output "default_bucket_id" {
  description = "The _Default log bucket, when this module configures it; null otherwise."
  value       = var.configure_default_bucket ? google_logging_project_bucket_config.default[0].id : null
}

output "audit_bucket_id" {
  description = "The audit log bucket, or null when not created."
  value       = var.create_audit_bucket ? google_logging_project_bucket_config.audit[0].id : null
}

output "audit_sink_writer_identity" {
  description = <<-EOT
    The sink's writer service account. Nothing needs to grant it anything for a LOG-BUCKET destination
    (Logging writes to its own buckets), which is why no IAM binding accompanies it here — unlike the
    GCS sink in modules/monitoring, which does need an explicit objectCreator grant.
  EOT
  value       = var.create_audit_bucket ? google_logging_project_sink.audit_to_bucket[0].writer_identity : null
}

output "exclusions" {
  description = "Names of active log exclusions — i.e. what this project has deliberately chosen not to see."
  value = compact([
    var.exclude_health_check_logs ? google_logging_project_exclusion.health_checks[0].name : "",
    var.exclude_static_asset_logs ? google_logging_project_exclusion.static_asset_requests[0].name : "",
  ])
}
