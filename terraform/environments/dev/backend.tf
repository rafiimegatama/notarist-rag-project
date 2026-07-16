/**
 * Remote state — GCS, created by terraform/bootstrap.
 *
 * The bucket is versioned, so a corrupted or truncated apply can be rolled back to the previous
 * generation. GCS backends take a lock on the state object automatically, so two concurrent applies
 * (a human and Cloud Build, say) cannot interleave and corrupt it.
 *
 * The bucket name cannot be a variable — Terraform resolves the backend before variables exist.
 * Either edit it here, or pass it at init:
 *
 *   terraform init -backend-config="bucket=notarist-tfstate-<project>"
 */
terraform {
  backend "gcs" {
    bucket = "notarist-tfstate-CHANGE-ME"
    prefix = "env/dev"
  }
}
