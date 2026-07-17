/**
 * Identities for the platform.
 *
 *   runtime   — the identity Cloud Run runs the Notarist container as. Everything the app touches
 *               (GCS, Secret Manager, Logging, Monitoring) is authorised through this, via
 *               Application Default Credentials. No key files anywhere.
 *   deployer  — the identity Cloud Build runs as: pushes images, applies Terraform, deploys.
 *   invoker   — the identity Cloud Scheduler uses to call the service. Separate from the others so
 *               a scheduler token cannot do anything except invoke.
 *
 * Least privilege: project-level roles are limited to telemetry (logging/monitoring/trace), which
 * have no data-plane reach. Access to the bucket and to each secret is granted as a
 * RESOURCE-level binding in the storage / secret_manager modules, not with project-wide
 * roles/storage.admin or roles/secretmanager.admin.
 */

resource "google_service_account" "runtime" {
  project      = var.project_id
  account_id   = "${var.name_prefix}-run"
  display_name = "Notarist Cloud Run runtime (${var.environment})"
  description  = "Identity for the Notarist backend on Cloud Run. Reads secrets, reads/writes the document bucket, signs GCS upload URLs."
}

resource "google_service_account" "deployer" {
  count = var.create_deployer ? 1 : 0

  project      = var.project_id
  account_id   = "${var.name_prefix}-deploy"
  display_name = "Notarist Cloud Build deployer (${var.environment})"
  description  = "Identity for Cloud Build: builds and pushes images, applies Terraform, deploys Cloud Run."
}

resource "google_service_account" "invoker" {
  count = var.create_invoker ? 1 : 0

  project      = var.project_id
  account_id   = "${var.name_prefix}-invoker"
  display_name = "Notarist Cloud Scheduler invoker (${var.environment})"
  description  = "Identity Cloud Scheduler uses to invoke Cloud Run. Can invoke; nothing else."
}

# ---------------------------------------------------------------------------
# Runtime: telemetry only at the project level.
# ---------------------------------------------------------------------------
locals {
  runtime_project_roles = [
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/cloudtrace.agent",
  ]
}

resource "google_project_iam_member" "runtime" {
  for_each = toset(local.runtime_project_roles)

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

# ---------------------------------------------------------------------------
# Runtime: permission to sign as ITSELF.
#
# This is the non-obvious one. The app mints V4 signed upload URLs for GCS without holding a
# private key: it calls the IAM signBlob API as its own identity (see GCS_SIGNING_SERVICE_ACCOUNT /
# GcsProperties.signing-service-account). That requires the runtime SA to hold
# roles/iam.serviceAccountTokenCreator ON ITSELF.
#
# Without this binding the app starts fine and only fails later, at the moment a user tries to
# upload — a signed-URL 403 that looks like a bucket permission problem and is not.
# ---------------------------------------------------------------------------
resource "google_service_account_iam_member" "runtime_self_sign" {
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.runtime.email}"
}

# ---------------------------------------------------------------------------
# Deployer: build, push, deploy.
# ---------------------------------------------------------------------------
locals {
  deployer_project_roles = var.create_deployer ? [
    "roles/artifactregistry.writer", # push images
    "roles/run.developer",           # create/update the Cloud Run service
    "roles/logging.logWriter",       # write build logs
    "roles/storage.admin",           # read/write the Terraform state bucket
    "roles/secretmanager.admin",     # Terraform manages secret CONTAINERS (not values)
    "roles/iam.serviceAccountAdmin", # Terraform manages the service accounts
    "roles/resourcemanager.projectIamAdmin",
  ] : []
}

resource "google_project_iam_member" "deployer" {
  for_each = toset(local.deployer_project_roles)

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.deployer[0].email}"
}

# Cloud Build must be able to "act as" the runtime SA in order to deploy a service that RUNS as it.
# Without this, `gcloud run deploy --service-account=<runtime>` fails with a permission error that
# names the deployer, not the runtime — a confusing failure worth pre-empting.
resource "google_service_account_iam_member" "deployer_acts_as_runtime" {
  count = var.create_deployer ? 1 : 0

  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deployer[0].email}"
}
