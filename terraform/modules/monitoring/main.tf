/**
 * Cloud Logging + Cloud Monitoring.
 *
 * The alerts here are chosen from how THIS system actually fails, not from a generic dashboard:
 *
 *   - uptime / 5xx / latency  — the ordinary ones.
 *   - instance floor breached — if min_instances drops to 0 the in-process @Scheduled jobs stop and
 *                               ingestion silently stalls. Nothing else would tell you.
 *   - secret access denied    — a missing secretAccessor binding or an empty secret version makes
 *                               every new revision fail to start. Distinct from a code bug and worth
 *                               its own signal.
 *   - RLS zero-rows           — the tenant policy is FAIL-CLOSED: a path that forgets to establish
 *                               an identity sees nothing rather than everything. That is the correct
 *                               behaviour, and it looks exactly like "the feature is broken". The
 *                               app logs a warning when it skips applying the identity; alert on it,
 *                               or you will debug it as a data-loss incident.
 *
 * Log-based metrics are created for the last two because there is no built-in metric for either.
 */

# ---------------------------------------------------------------------------
# Notification channels
# ---------------------------------------------------------------------------
resource "google_monitoring_notification_channel" "email" {
  for_each = toset(var.alert_email_addresses)

  project      = var.project_id
  display_name = "Notarist ${var.environment} — ${each.value}"
  type         = "email"

  labels = {
    email_address = each.value
  }
}

locals {
  channels = [for c in google_monitoring_notification_channel.email : c.id]

  # Alerts are pointless without somewhere to send them. Guard rather than silently create
  # alert policies that notify nobody.
  alerting_enabled = length(local.channels) > 0
}

# ---------------------------------------------------------------------------
# Uptime check — is the service answering from outside GCP at all?
# ---------------------------------------------------------------------------
resource "google_monitoring_uptime_check_config" "health" {
  count = var.enable_uptime_check ? 1 : 0

  project      = var.project_id
  display_name = "notarist-${var.environment}-health"
  timeout      = "10s"
  period       = "300s"

  http_check {
    path         = "/actuator/health"
    port         = 443
    use_ssl      = true
    validate_ssl = true
  }

  monitored_resource {
    type = "uptime_url"
    labels = {
      project_id = var.project_id
      host       = var.service_host
    }
  }
}

resource "google_monitoring_alert_policy" "uptime" {
  count = var.enable_uptime_check && local.alerting_enabled ? 1 : 0

  project      = var.project_id
  display_name = "Notarist ${var.environment} — service is DOWN"
  combiner     = "OR"
  severity     = "CRITICAL"

  conditions {
    display_name = "Uptime check failing"
    condition_threshold {
      filter = join(" AND ", [
        "metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\"",
        "resource.type=\"uptime_url\"",
        "metric.label.check_id=\"${google_monitoring_uptime_check_config.health[0].uptime_check_id}\"",
      ])
      comparison      = "COMPARISON_LT"
      threshold_value = 1
      duration        = "300s"

      aggregations {
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_NEXT_OLDER"
        cross_series_reducer = "REDUCE_COUNT_FALSE"
        group_by_fields      = ["resource.label.host"]
      }

      trigger {
        count = 1
      }
    }
  }

  notification_channels = local.channels

  documentation {
    content = "The Notarist ${var.environment} health endpoint is not responding. Check Cloud Run revision status, then Supabase reachability (a failed DB connection fails the health check)."
  }
}

# ---------------------------------------------------------------------------
# 5xx rate
# ---------------------------------------------------------------------------
resource "google_monitoring_alert_policy" "error_rate" {
  count = local.alerting_enabled ? 1 : 0

  project      = var.project_id
  display_name = "Notarist ${var.environment} — elevated 5xx rate"
  combiner     = "OR"
  severity     = "ERROR"

  conditions {
    display_name = "5xx responses above threshold"
    condition_threshold {
      filter = join(" AND ", [
        "metric.type=\"run.googleapis.com/request_count\"",
        "resource.type=\"cloud_run_revision\"",
        "resource.label.service_name=\"${var.service_name}\"",
        "metric.label.response_code_class=\"5xx\"",
      ])
      comparison      = "COMPARISON_GT"
      threshold_value = var.error_rate_threshold
      duration        = "300s"

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }

  notification_channels = local.channels
}

# ---------------------------------------------------------------------------
# Latency
# ---------------------------------------------------------------------------
resource "google_monitoring_alert_policy" "latency" {
  count = local.alerting_enabled ? 1 : 0

  project      = var.project_id
  display_name = "Notarist ${var.environment} — p95 latency degraded"
  combiner     = "OR"
  severity     = "WARNING"

  conditions {
    display_name = "p95 request latency high"
    condition_threshold {
      filter = join(" AND ", [
        "metric.type=\"run.googleapis.com/request_latencies\"",
        "resource.type=\"cloud_run_revision\"",
        "resource.label.service_name=\"${var.service_name}\"",
      ])
      comparison      = "COMPARISON_GT"
      threshold_value = var.latency_p95_threshold_ms
      duration        = "300s"

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_PERCENTILE_95"
      }
    }
  }

  notification_channels = local.channels

  documentation {
    content = "p95 latency above ${var.latency_p95_threshold_ms}ms. Usual suspects: Supabase connection saturation (max_instances * POSTGRES_POOL_MAX over the ceiling), Qdrant slowness, or an OCR/embedding sidecar timing out."
  }
}

# ---------------------------------------------------------------------------
# The instance floor. If this hits zero, ingestion has silently stopped.
# ---------------------------------------------------------------------------
resource "google_monitoring_alert_policy" "instance_floor" {
  count = var.expect_always_on_instance && local.alerting_enabled ? 1 : 0

  project      = var.project_id
  display_name = "Notarist ${var.environment} — no warm instance (SCHEDULERS ARE STOPPED)"
  combiner     = "OR"
  severity     = "CRITICAL"

  conditions {
    display_name = "Active instance count fell to zero"
    condition_threshold {
      filter = join(" AND ", [
        "metric.type=\"run.googleapis.com/container/instance_count\"",
        "resource.type=\"cloud_run_revision\"",
        "resource.label.service_name=\"${var.service_name}\"",
      ])
      comparison      = "COMPARISON_LT"
      threshold_value = 1
      duration        = "600s"

      aggregations {
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_MAX"
        cross_series_reducer = "REDUCE_SUM"
      }
    }
  }

  notification_channels = local.channels

  documentation {
    content = <<-EOT
      No Cloud Run instance has been running for 10 minutes.

      This is not just an availability question. The ingestion queue poller, the retry sweep and the
      token-deny-list cleanup are IN-PROCESS @Scheduled jobs. With no warm instance (or with
      cpu_idle = true) they do not execute, and uploads will be accepted and never processed — with
      no error raised anywhere.

      Check that min_instances >= 1 and cpu_idle = false on the current revision.
    EOT
  }
}

# ---------------------------------------------------------------------------
# Log-based metric + alert: secret access failures at startup.
# ---------------------------------------------------------------------------
resource "google_logging_metric" "secret_access_denied" {
  project = var.project_id
  name    = "notarist_${var.environment}_secret_access_denied"

  filter = join(" AND ", [
    "resource.type=\"cloud_run_revision\"",
    "resource.labels.service_name=\"${var.service_name}\"",
    "(textPayload:\"PERMISSION_DENIED\" OR textPayload:\"Secret Manager\" OR jsonPayload.message:\"secretmanager\")",
    "severity>=ERROR",
  ])

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
  }
}

resource "google_monitoring_alert_policy" "secret_access_denied" {
  count = local.alerting_enabled ? 1 : 0

  project      = var.project_id
  display_name = "Notarist ${var.environment} — Secret Manager access failing"
  combiner     = "OR"
  severity     = "CRITICAL"

  conditions {
    display_name = "Secret access errors in logs"
    condition_threshold {
      filter = join(" AND ", [
        "metric.type=\"logging.googleapis.com/user/${google_logging_metric.secret_access_denied.name}\"",
        "resource.type=\"cloud_run_revision\"",
      ])
      comparison      = "COMPARISON_GT"
      threshold_value = 0
      duration        = "60s"

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_DELTA"
      }
    }
  }

  notification_channels = local.channels

  documentation {
    content = "A revision cannot read a secret. Either the runtime SA lost roles/secretmanager.secretAccessor, or the secret has ZERO versions (Terraform creates the container; a human must add the value). Run `make secrets-check`."
  }
}

# ---------------------------------------------------------------------------
# Log-based metric + alert: RLS identity not applied.
#
# The appliers log "Tenant identity requested outside an active transaction — skipping" when they
# cannot establish the identity. The fail-closed policy then returns ZERO rows. Users experience
# that as "my documents disappeared", which is the single most misleading failure this system has.
# ---------------------------------------------------------------------------
resource "google_logging_metric" "rls_identity_skipped" {
  project = var.project_id
  name    = "notarist_${var.environment}_rls_identity_skipped"

  filter = join(" AND ", [
    "resource.type=\"cloud_run_revision\"",
    "resource.labels.service_name=\"${var.service_name}\"",
    "(textPayload:\"Tenant identity\" OR jsonPayload.message:\"Tenant identity\")",
    "severity>=WARNING",
  ])

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
  }
}

resource "google_monitoring_alert_policy" "rls_identity_skipped" {
  count = local.alerting_enabled ? 1 : 0

  project      = var.project_id
  display_name = "Notarist ${var.environment} — tenant identity not applied (queries returning no rows)"
  combiner     = "OR"
  severity     = "ERROR"

  conditions {
    display_name = "RLS identity skipped"
    condition_threshold {
      filter = join(" AND ", [
        "metric.type=\"logging.googleapis.com/user/${google_logging_metric.rls_identity_skipped.name}\"",
        "resource.type=\"cloud_run_revision\"",
      ])
      comparison      = "COMPARISON_GT"
      threshold_value = 0
      duration        = "300s"

      aggregations {
        alignment_period   = "300s"
        per_series_aligner = "ALIGN_DELTA"
      }
    }
  }

  notification_channels = local.channels

  documentation {
    content = <<-EOT
      The app failed to establish a PostgreSQL RLS tenant identity for one or more queries.

      The tenant policy is FAIL-CLOSED by design, so those queries returned ZERO ROWS rather than
      another tenant's data. Nothing has leaked. But users will report it as missing documents.

      Root cause is almost always a repository path that is not @Transactional — the identity is a
      transaction-local setting and does not survive outside one.
    EOT
  }
}

# ---------------------------------------------------------------------------
# Log sink — long-term retention of audit-relevant logs.
# The audit domain calls for 7-year retention; Cloud Logging's default bucket keeps 30 days.
# ---------------------------------------------------------------------------
resource "google_logging_project_sink" "audit_archive" {
  count = var.audit_log_bucket == null ? 0 : 1

  project     = var.project_id
  name        = "notarist-${var.environment}-audit-archive"
  destination = "storage.googleapis.com/${var.audit_log_bucket}"

  filter = join(" AND ", [
    "resource.type=\"cloud_run_revision\"",
    "resource.labels.service_name=\"${var.service_name}\"",
    "severity>=INFO",
  ])

  unique_writer_identity = true
}

# The sink's writer identity needs permission to write into the archive bucket, or the sink silently
# drops everything.
resource "google_storage_bucket_iam_member" "sink_writer" {
  count = var.audit_log_bucket == null ? 0 : 1

  bucket = var.audit_log_bucket
  role   = "roles/storage.objectCreator"
  member = google_logging_project_sink.audit_archive[0].writer_identity
}
