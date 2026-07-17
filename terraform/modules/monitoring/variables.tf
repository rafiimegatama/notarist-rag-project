variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "environment" {
  description = "Environment name (dev|staging|prod)."
  type        = string
}

variable "service_name" {
  description = "Cloud Run service name to monitor."
  type        = string
}

variable "service_host" {
  description = "Hostname of the service (no scheme), e.g. notarist-prod-xxxx-uc.a.run.app."
  type        = string
}

variable "alert_email_addresses" {
  description = "Emails to notify. EMPTY = no alert policies are created at all, because an alert that notifies nobody is worse than none (it looks like coverage)."
  type        = list(string)
  default     = []
}

variable "enable_uptime_check" {
  description = "Create an external uptime check. Requires the service to be publicly reachable."
  type        = bool
  default     = true
}

variable "expect_always_on_instance" {
  description = "True when this environment pins a warm instance for the in-process schedulers. Enables the 'no warm instance' alert."
  type        = bool
  default     = true
}

variable "error_rate_threshold" {
  description = "5xx requests per second that trips the error alert."
  type        = number
  default     = 0.5
}

variable "latency_p95_threshold_ms" {
  description = "p95 latency (ms) that trips the latency alert. RAG search is not fast; do not set this at API-CRUD levels."
  type        = number
  default     = 5000
}

variable "audit_log_bucket" {
  description = "GCS bucket for long-term log archival (7-year audit retention). Null = no sink."
  type        = string
  default     = null
}
