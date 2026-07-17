output "state_bucket" {
  description = "Terraform state bucket. Put this in each environment's backend.tf."
  value       = google_storage_bucket.tfstate.name
}

output "audit_log_bucket" {
  description = "Audit log archive bucket, or null when not created."
  value       = var.create_audit_log_bucket ? google_storage_bucket.audit_logs[0].name : null
}

output "enabled_apis" {
  description = "Google APIs enabled on the project."
  value       = module.project_services.enabled_apis
}

output "next_step" {
  description = "What to do after bootstrap."
  value       = <<-EOT
    State bucket ready: ${google_storage_bucket.tfstate.name}

    Next:
      1. Set this bucket in terraform/environments/<env>/backend.tf
      2. terraform -chdir=terraform/environments/dev init
      3. Populate the Secret Manager values (see terraform/README.md "Secrets") BEFORE the first
         apply of the Cloud Run service — a revision that references a secret with no versions will
         not start.
  EOT
}
