/**
 * Log retention + exclusions (Sprint TF2, Task 8).
 *
 * New module, scoped deliberately narrowly. What already exists elsewhere and is NOT duplicated here:
 *
 *   modules/monitoring  owns google_logging_project_sink (audit logs -> GCS) and the two
 *                       google_logging_metric definitions. Those are ALERTING concerns and they stay
 *                       there. Task 1 says never duplicate resources, and a second sink over the same
 *                       filter would double-write every matching line — and double the storage bill.
 *
 * What was genuinely missing, and is what this module adds:
 *
 *   1. RETENTION. The project's _Default log bucket keeps 30 days, full stop, because nothing ever
 *      configured it. For a notarial platform that is the wrong default in both directions: 30 days
 *      is too SHORT for an audit trail that may be asked about years later, and — for chatty
 *      request logs — 30 days of everything is money spent on noise.
 *
 *   2. EXCLUSIONS. Every log line ingested past the free tier is billed. Health checks run every
 *      60s from Cloud Scheduler AND from the uptime check; that is ~86k lines/month of "200 OK" that
 *      nobody will ever read, charged at ingestion. Excluding them is the single highest-leverage
 *      logging change available here.
 *
 * ---------------------------------------------------------------------------------------------
 * WHY _Default AND NOT A NEW BUCKET
 *
 * Cloud Logging routes everything into the _Default bucket unless a sink says otherwise. Creating a
 * fresh bucket does not move logs into it — it only exists alongside _Default, and you then pay to
 * store logs twice while _Default keeps its 30-day default anyway. Configuring _Default in place is
 * what actually changes retention for the logs the platform is really producing. `google_logging_
 * project_bucket_config` adopts the existing bucket rather than creating a second one.
 *
 * A SEPARATE, LONGER-LIVED bucket IS created for security/audit logs, because those have a genuinely
 * different retention obligation from application chatter and must not be aged out with it.
 */

# ---------------------------------------------------------------------------------------------
# _Default: the bucket every log lands in unless routed elsewhere.
#
# NOT created — ADOPTED. This resource configures the bucket Google already made. Terraform will not
# delete _Default on destroy (the API refuses), which is why there is no prevent_destroy here: the
# platform's guarantee comes from the API, not from us.
resource "google_logging_project_bucket_config" "default" {
  count = var.configure_default_bucket ? 1 : 0

  project        = var.project_id
  location       = var.log_bucket_location
  bucket_id      = "_Default"
  retention_days = var.default_retention_days

  # Deliberately NOT locked. A locked bucket cannot have its retention shortened OR be deleted for
  # the lifetime of the retention period — on _Default, which receives every log line in the project,
  # that is an irreversible commitment to a bill. Locking belongs on the audit bucket below, where
  # tamper-resistance is the actual requirement.
  locked = false

  description = "Default log bucket. Retention set by Terraform (Sprint TF2); Google's out-of-the-box default is 30 days."
}

# ---------------------------------------------------------------------------------------------
# Security / audit logs: a longer-lived home, separate from application chatter.
#
# Locked when var.audit_retention_locked is set. Locking makes retention tamper-evident — an operator
# who can otherwise delete evidence of their own actions cannot shorten this. That is the property an
# audit trail is FOR, so it is offered here even though it is refused on _Default above.
resource "google_logging_project_bucket_config" "audit" {
  count = var.create_audit_bucket ? 1 : 0

  project        = var.project_id
  location       = var.log_bucket_location
  bucket_id      = "${var.name_prefix}-audit"
  retention_days = var.audit_retention_days
  locked         = var.audit_retention_locked

  description = "Admin Activity / Data Access / IAM audit logs. Retention is independent of application logs because the obligation is."
}

# Route audit logs into the bucket above. Without this the bucket exists and stays empty — a sink is
# what puts anything in it.
resource "google_logging_project_sink" "audit_to_bucket" {
  count = var.create_audit_bucket ? 1 : 0

  project     = var.project_id
  name        = "${var.name_prefix}-audit-to-bucket"
  destination = "logging.googleapis.com/${google_logging_project_bucket_config.audit[0].id}"

  # Admin Activity (who changed infrastructure) + Data Access (who read what) + the platform's own
  # security-relevant application logs.
  #
  # Distinct from modules/monitoring's sink, which exports a SUBSET to GCS for long-term archival.
  # Two sinks over overlapping filters is intentional and is not duplication: one feeds queryable
  # short-to-medium term storage, the other feeds cheap immutable archive. They have different
  # retention, different cost profiles and different readers.
  filter = join(" OR ", [
    "logName:\"cloudaudit.googleapis.com%2Factivity\"",
    "logName:\"cloudaudit.googleapis.com%2Fdata_access\"",
    "logName:\"cloudaudit.googleapis.com%2Fsystem_event\"",
  ])

  # _Default keeps its own copy: excluding here would mean the ONLY copy of an audit line lives in a
  # bucket whose retention may later be locked, and a locked bucket cannot be pruned if it turns out
  # to be capturing something it should not.
  unique_writer_identity = true
}

# ---------------------------------------------------------------------------------------------
# EXCLUSIONS — applied to _Default's own sink, which is what "stop ingesting this" means.
#
# Each exclusion is a deliberate decision to be BLIND to something in exchange for money. So each one
# below is narrow, and none of them can hide a failure:
#
#   * health checks       — matched on the 2xx status AND the health path. A FAILING health check
#                           still logs, which is the only time anyone would look.
#   * static assets       — 2xx only, same reasoning.
#
// What is deliberately NOT excluded, despite being high-volume and tempting:
//   * anything >= 400            — the errors are the point
//   * audit logs                 — cannot legally be dropped, and _Default is a copy
//   * Cloud Run startup/shutdown — the record of a revision failing to boot
resource "google_logging_project_exclusion" "health_checks" {
  count = var.exclude_health_check_logs ? 1 : 0

  project     = var.project_id
  name        = "${var.name_prefix}-exclude-healthcheck-2xx"
  description = "Successful health probes only. ~86k lines/month of '200 OK' from the scheduler heartbeat + uptime check. A failing probe (non-2xx) is NOT excluded."

  filter = join(" AND ", [
    "resource.type=\"cloud_run_revision\"",
    "httpRequest.status>=200",
    "httpRequest.status<300",
    "(httpRequest.requestUrl:\"/actuator/health\" OR httpRequest.requestUrl:\"/ops/health\" OR httpRequest.requestUrl:\"/health\")",
  ])
}

resource "google_logging_project_exclusion" "static_asset_requests" {
  count = var.exclude_static_asset_logs ? 1 : 0

  project     = var.project_id
  name        = "${var.name_prefix}-exclude-static-2xx"
  description = "Successful static asset fetches. Non-2xx still logged."

  filter = join(" AND ", [
    "resource.type=\"cloud_run_revision\"",
    "httpRequest.status>=200",
    "httpRequest.status<300",
    "(httpRequest.requestUrl:\"/favicon.ico\" OR httpRequest.requestUrl:\"/static/\" OR httpRequest.requestUrl:\"/assets/\")",
  ])
}
