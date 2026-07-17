output "service_url" {
  description = "Public HTTPS URL of the Notarist API."
  value       = module.cloudrun.service_url
}

output "service_name" {
  description = "Cloud Run service name."
  value       = module.cloudrun.service_name
}

output "image_base" {
  description = "Artifact Registry base path. Cloud Build pushes to <image_base>/notarist-backend:<tag>."
  value       = module.artifact_registry.image_base
}

output "documents_bucket" {
  description = "GCS bucket holding legal documents (the app's GCS_BUCKET)."
  value       = module.storage.bucket_name
}

output "ocr_output_bucket" {
  description = "GCS bucket holding OCR/chunk artefacts derived from the document bucket."
  value       = module.storage_ocr_output.bucket_name
}

output "generated_documents_bucket" {
  description = "GCS bucket holding documents the app generates from templates."
  value       = module.storage_generated_documents.bucket_name
}

output "application_assets_bucket" {
  description = "GCS bucket holding static assets (templates, letterheads, fonts). Runtime has READ-only access."
  value       = module.storage_application_assets.bucket_name
}

output "runtime_service_account" {
  description = "Identity the app runs as."
  value       = module.service_account.runtime_email
}

output "deployer_service_account" {
  description = "Identity Cloud Build runs as."
  value       = module.service_account.deployer_email
}

# The two values .github/workflows/*.yml needs. They are NOT secrets — a WIF provider name and an SA
# email are useless without a token GitHub will only mint for this repository — so set them as
# repository *variables* (vars.GHA_WIF_PROVIDER / vars.GHA_SERVICE_ACCOUNT), not secrets.
output "github_actions_service_account" {
  description = "GitHub Actions identity. Set as the GHA_SERVICE_ACCOUNT repository variable. Null unless enable_github_actions."
  value       = module.service_account.github_actions_email
}

output "github_actions_wif_provider" {
  description = "Workload Identity provider resource name. Set as the GHA_WIF_PROVIDER repository variable. Null unless enable_github_actions."
  value       = module.service_account.github_actions_provider
}

output "secret_ids" {
  description = "Secret Manager secret IDs consumed by the service."
  value       = module.secret_manager.secret_ids
}

output "secret_populate_commands" {
  description = "Run these ONCE to write the secret values. Terraform never sets them — a value in a variable is a value in the state file."
  value       = module.secret_manager.populate_commands
}

output "egress_ips" {
  description = "Static egress IPs to allowlist in Supabase and Qdrant. Empty unless enable_vpc_egress = true."
  value       = module.network.egress_ips
}

output "scales_to_zero" {
  description = "False when a warm instance is pinned for the in-process schedulers (and therefore always billing)."
  value       = module.cloudrun.scales_to_zero
}

output "alerting_enabled" {
  description = "False when no alert emails were configured — meaning NO alert policies exist."
  value       = module.monitoring.alerting_enabled
}
