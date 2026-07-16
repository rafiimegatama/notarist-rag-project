output "service_name" {
  description = "Cloud Run service name."
  value       = google_cloud_run_v2_service.this.name
}

output "service_url" {
  description = "HTTPS URL of the service."
  value       = google_cloud_run_v2_service.this.uri
}

output "service_id" {
  description = "Fully-qualified service ID."
  value       = google_cloud_run_v2_service.this.id
}

output "latest_ready_revision" {
  description = "Most recent revision that reached Ready."
  value       = google_cloud_run_v2_service.this.latest_ready_revision
}

output "effective_min_instances" {
  description = "Min instances actually applied — forced to >=1 when the in-process schedulers need always-on CPU."
  value       = local.min_instances
}

output "scales_to_zero" {
  description = "False when the service is pinned warm for the schedulers (and therefore always billing)."
  value       = local.min_instances == 0
}
