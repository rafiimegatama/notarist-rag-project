/**
 * BOOTSTRAP — run this ONCE per project, before any environment.
 *
 * Chicken-and-egg: the environments keep their state in a GCS bucket, but a GCS bucket has to be
 * created by something. This root solves that. It is the ONLY Terraform in the repo that uses LOCAL
 * state, and that local state file is worth almost nothing — everything here is trivially
 * recreatable and `prevent_destroy` guards the one thing that is not (the bucket).
 *
 *   terraform -chdir=terraform/bootstrap init
 *   terraform -chdir=terraform/bootstrap apply -var project_id=<proj>
 *
 * It creates:
 *   - the Terraform state bucket (versioned, so a corrupted apply can be rolled back)
 *   - the API enablement for the project
 *
 * Then each environment `init`s against this bucket with its own prefix.
 */

terraform {
  required_version = ">= 1.5"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
  # Local state, deliberately — this root is what creates the remote backend.
}

provider "google" {
  project = var.project_id
  region  = var.region
}

module "project_services" {
  source = "../modules/project_services"

  project_id          = var.project_id
  enable_network_apis = var.enable_network_apis
}

resource "google_storage_bucket" "tfstate" {
  project  = var.project_id
  name     = var.state_bucket_name
  location = var.state_bucket_location

  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  # Terraform state contains a full inventory of the infrastructure — resource names, IAM members,
  # bucket names, service URLs. It is not a secret store, but it is a map of the estate. Never let
  # this bucket become public, and never allow force_destroy.
  force_destroy = false

  # Non-negotiable for state: an apply that corrupts or truncates state is recoverable ONLY if the
  # previous generation still exists.
  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      num_newer_versions = 20
      with_state         = "ARCHIVED"
    }
    action {
      type = "Delete"
    }
  }

  depends_on = [module.project_services]

  lifecycle {
    prevent_destroy = true
  }
}

# Optional: a separate bucket for long-term log archival (the 7-year audit retention the domain
# calls for; Cloud Logging's own buckets default to 30 days).
resource "google_storage_bucket" "audit_logs" {
  count = var.create_audit_log_bucket ? 1 : 0

  project  = var.project_id
  name     = var.audit_log_bucket_name
  location = var.state_bucket_location

  storage_class               = "NEARLINE"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false

  retention_policy {
    retention_period = var.audit_log_retention_seconds
    # Left UNLOCKED. Locking is irreversible by anyone including project owners — do it as a
    # deliberate compliance step, not as a side effect of a first bootstrap.
    is_locked = false
  }

  depends_on = [module.project_services]

  lifecycle {
    prevent_destroy = true
  }
}
