variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "region" {
  description = "Cloud Scheduler region."
  type        = string
}

variable "name_prefix" {
  description = "Prefix for job names."
  type        = string
}

variable "service_name" {
  description = "Cloud Run service being targeted (for job descriptions)."
  type        = string
}

variable "service_url" {
  description = "Base HTTPS URL of the Cloud Run service."
  type        = string
}

variable "invoker_service_account_email" {
  description = "SA whose OIDC token authenticates the scheduler to Cloud Run. Needs roles/run.invoker."
  type        = string
}

variable "time_zone" {
  description = "IANA time zone for cron expressions."
  type        = string
  default     = "Asia/Jakarta"
}

variable "enable_heartbeat" {
  description = "Create the /actuator/health heartbeat job."
  type        = bool
  default     = true
}

variable "heartbeat_schedule" {
  description = "Cron for the heartbeat."
  type        = string
  default     = "*/5 * * * *"
}

variable "jobs" {
  description = <<-EOT
    Additional HTTP-triggered jobs, keyed by short name. Each: { path, schedule, description,
    http_method?, body?, retry_count?, attempt_deadline?, paused? }.

    Empty by default and it must stay that way until the app EXPOSES pipeline triggers as HTTP
    endpoints — today the pipeline runs on in-process timers that Cloud Scheduler cannot reach.
  EOT
  type        = any
  default     = {}
}
