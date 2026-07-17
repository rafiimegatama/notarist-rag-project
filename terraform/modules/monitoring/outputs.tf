output "notification_channel_ids" {
  description = "Notification channel IDs created."
  value       = local.channels
}

output "alerting_enabled" {
  description = "False when no alert emails were supplied — in which case NO alert policies exist."
  value       = local.alerting_enabled
}

output "uptime_check_id" {
  description = "Uptime check ID, or null when disabled."
  value       = var.enable_uptime_check ? google_monitoring_uptime_check_config.health[0].uptime_check_id : null
}

output "log_metrics" {
  description = "Log-based metrics created for failure modes with no built-in metric."
  value = [
    google_logging_metric.secret_access_denied.name,
    google_logging_metric.rls_identity_skipped.name,
  ]
}
