output "runtime_email" {
  description = "Email of the Cloud Run runtime service account."
  value       = google_service_account.runtime.email
}

output "runtime_id" {
  description = "Fully-qualified ID of the runtime service account."
  value       = google_service_account.runtime.name
}

output "deployer_email" {
  description = "Email of the Cloud Build deployer service account (null when not created)."
  value       = var.create_deployer ? google_service_account.deployer[0].email : null
}

output "invoker_email" {
  description = "Email of the Cloud Scheduler invoker service account (null when not created)."
  value       = var.create_invoker ? google_service_account.invoker[0].email : null
}

output "github_actions_email" {
  description = "Email of the GitHub Actions service account (null when not created). Set as the GHA_SERVICE_ACCOUNT repository variable."
  value       = var.create_github_actions ? google_service_account.github_actions[0].email : null
}

output "github_actions_provider" {
  description = "Full resource name of the Workload Identity provider (null when not created). Set as the GHA_WIF_PROVIDER repository variable; google-github-actions/auth needs it."
  value       = var.create_github_actions ? google_iam_workload_identity_pool_provider.github[0].name : null
}
