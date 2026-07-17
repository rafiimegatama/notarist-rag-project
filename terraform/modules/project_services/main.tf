/**
 * Enables the Google APIs the Notarist platform depends on.
 *
 * Every other module assumes these are on. Enabling an already-enabled API is a no-op, so this is
 * safe to re-apply.
 *
 * disable_on_destroy = false is deliberate: a `terraform destroy` of one environment must never
 * turn an API off at the PROJECT level, which would break every other environment sharing the
 * project. Turning an API off is an explicit, manual decision.
 */

locals {
  required_apis = [
    "run.googleapis.com",              # Cloud Run — the service itself
    "artifactregistry.googleapis.com", # Artifact Registry — container images
    "secretmanager.googleapis.com",    # Secret Manager — DB password, JWT keys, API keys
    "storage.googleapis.com",          # Cloud Storage — document bucket + TF remote state
    "iam.googleapis.com",              # Service accounts
    "iamcredentials.googleapis.com",   # signBlob — REQUIRED for GCS V4 signed upload URLs
    "cloudbuild.googleapis.com",       # Cloud Build — CI/CD
    "cloudscheduler.googleapis.com",   # Cloud Scheduler — heartbeat / cron triggers
    "logging.googleapis.com",          # Cloud Logging
    "monitoring.googleapis.com",       # Cloud Monitoring — uptime checks, alerts
    "cloudresourcemanager.googleapis.com",
    "serviceusage.googleapis.com",
  ]

  # Budget alerting (Sprint TF2, Task 9). Gated rather than always-on: the API is only useful when an
  # environment actually manages a billing budget, and enabling a billing API on a project whose
  # deployer has no billing-account permission adds a service the project can never call.
  billing_apis = [
    "billingbudgets.googleapis.com", # google_billing_budget — spend thresholds
    "cloudbilling.googleapis.com",   # resolves the billing account the budget attaches to
  ]

  # Only needed when the service egresses through a VPC (static outbound IP for Supabase/Qdrant
  # allowlisting). Enabling these on a project that will never use a connector is wasteful noise.
  network_apis = [
    "compute.googleapis.com",
    "vpcaccess.googleapis.com",
  ]

  # Only needed when GitHub Actions federates in via Workload Identity. sts is what performs the
  # OIDC-token-for-access-token exchange; without it the auth step fails with a permission error that
  # points at the service account rather than at the missing API.
  github_actions_apis = [
    "sts.googleapis.com",
  ]

  apis = concat(
    local.required_apis,
    var.enable_network_apis ? local.network_apis : [],
    var.enable_github_actions_apis ? local.github_actions_apis : [],
    var.enable_billing_apis ? local.billing_apis : [],
  )
}

resource "google_project_service" "this" {
  for_each = toset(local.apis)

  project = var.project_id
  service = each.value

  disable_on_destroy         = false
  disable_dependent_services = false
}
