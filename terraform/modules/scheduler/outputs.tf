output "heartbeat_job_name" {
  description = "Name of the heartbeat job, or null when disabled."
  value       = var.enable_heartbeat ? google_cloud_scheduler_job.heartbeat[0].name : null
}

output "job_names" {
  description = "Names of the custom scheduler jobs."
  value       = [for j in google_cloud_scheduler_job.custom : j.name]
}
