/**
 * Cloud Scheduler.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * BE HONEST ABOUT WHAT THIS DOES AND DOES NOT DO.
 *
 * The Notarist app drives its ingestion pipeline from IN-PROCESS @Scheduled jobs. Cloud Scheduler
 * CANNOT trigger those — they are timers inside the JVM, not HTTP endpoints. There is nothing to
 * POST to. What actually keeps them running is the Cloud Run module pinning min_instances >= 1 with
 * cpu_idle = false.
 *
 * So the jobs defined here are deliberately modest:
 *
 *   heartbeat — GETs /actuator/health on a schedule. It does NOT drive the pipeline. It gives us
 *               (a) an external liveness signal that alerts can hang off, and (b) a steady trickle
 *               of requests, which is a cheap backstop if someone later sets min_instances to 0 and
 *               unknowingly parks the schedulers.
 *
 *   custom    — anything you add via var.jobs, for the day the pipeline triggers ARE exposed as
 *               endpoints. That is the change that would let this service scale to zero; see
 *               terraform/README "Blockers".
 *
 * Jobs authenticate with an OIDC token from a dedicated invoker SA, so the endpoint does not have
 * to be public for the scheduler to reach it.
 * ─────────────────────────────────────────────────────────────────────────────
 */

resource "google_cloud_scheduler_job" "heartbeat" {
  count = var.enable_heartbeat ? 1 : 0

  project     = var.project_id
  region      = var.region
  name        = "${var.name_prefix}-heartbeat"
  description = "Health probe for ${var.service_name}. Does NOT drive the ingestion pipeline — see module header."
  schedule    = var.heartbeat_schedule
  time_zone   = var.time_zone

  attempt_deadline = "30s"

  retry_config {
    retry_count          = 1
    min_backoff_duration = "5s"
  }

  http_target {
    http_method = "GET"
    uri         = "${var.service_url}/actuator/health"

    oidc_token {
      service_account_email = var.invoker_service_account_email
      audience              = var.service_url
    }
  }
}

resource "google_cloud_scheduler_job" "custom" {
  for_each = var.jobs

  project     = var.project_id
  region      = var.region
  name        = "${var.name_prefix}-${each.key}"
  description = each.value.description
  schedule    = each.value.schedule
  time_zone   = var.time_zone
  paused      = try(each.value.paused, false)

  attempt_deadline = try(each.value.attempt_deadline, "180s")

  retry_config {
    retry_count          = try(each.value.retry_count, 3)
    min_backoff_duration = "10s"
    max_backoff_duration = "600s"
    max_doublings        = 3
  }

  http_target {
    http_method = try(each.value.http_method, "POST")
    uri         = "${var.service_url}${each.value.path}"
    body        = try(each.value.body, null) == null ? null : base64encode(each.value.body)

    headers = try(each.value.body, null) == null ? {} : {
      "Content-Type" = "application/json"
    }

    oidc_token {
      service_account_email = var.invoker_service_account_email
      audience              = var.service_url
    }
  }
}
