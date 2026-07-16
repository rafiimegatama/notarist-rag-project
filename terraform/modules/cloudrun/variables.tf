variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "region" {
  description = "Cloud Run region."
  type        = string
}

variable "service_name" {
  description = "Cloud Run service name."
  type        = string
}

variable "image" {
  description = <<-EOT
    Container image to deploy, e.g. <region>-docker.pkg.dev/<proj>/notarist/notarist-backend:<tag>.
    Terraform only sets this on FIRST create — subsequent image changes are owned by Cloud Build
    (see the ignore_changes block in main.tf).
  EOT
  type        = string
}

variable "service_account_email" {
  description = "Runtime service account the container runs as."
  type        = string
}

variable "secret_ids" {
  description = <<-EOT
    Map of short name => Secret Manager secret ID. Must contain:
      postgres-password, app-encryption-key, app-encryption-salt, qdrant-api-key,
      jwt-private-key, jwt-public-key
  EOT
  type        = map(string)

  validation {
    condition = length(setsubtract(
      ["postgres-password", "app-encryption-key", "app-encryption-salt", "qdrant-api-key", "jwt-private-key", "jwt-public-key"],
      keys(var.secret_ids)
    )) == 0
    error_message = "secret_ids is missing one or more required secrets. The service cannot start without all six."
  }
}

variable "env_vars" {
  description = "Plain (non-secret) environment variables. Anything sensitive belongs in secret_ids."
  type        = map(string)
  default     = {}
}

variable "run_in_process_schedulers" {
  description = <<-EOT
    True when this service runs the app's in-process @Scheduled jobs (ingestion queue poller,
    retry sweep, token-deny-list cleanup). Forces min_instances >= 1 and cpu_idle = false, because
    Cloud Run gives an idle instance no CPU and the jobs would silently never fire.
    Set false ONLY if the schedulers are disabled in this environment.
  EOT
  type        = bool
  default     = true
}

variable "min_instances" {
  description = "Minimum instances. Raised to at least 1 automatically when run_in_process_schedulers is true."
  type        = number
  default     = 0
}

variable "max_instances" {
  description = "Maximum instances. Cap this against the Supabase connection ceiling: max_instances * POSTGRES_POOL_MAX must stay under it."
  type        = number
  default     = 10
}

variable "concurrency" {
  description = "Max concurrent requests per instance."
  type        = number
  default     = 80
}

variable "cpu" {
  description = "CPU limit, e.g. \"1\" or \"2\"."
  type        = string
  default     = "1"
}

variable "memory" {
  description = "Memory limit. The JVM is sized to 75% of this (MaxRAMPercentage in the Dockerfile); below 512Mi a Spring Boot app with JPA will OOM."
  type        = string
  default     = "1Gi"
}

variable "container_port" {
  description = "Port the container listens on."
  type        = number
  default     = 8080
}

variable "request_timeout" {
  description = "Request timeout. Must exceed the slowest synchronous path — OCR_TIMEOUT_MS is 120s, so do not set this below that."
  type        = string
  default     = "300s"
}

variable "startup_initial_delay" {
  description = "Seconds to wait before the first startup probe. A JVM + Spring context + Flyway needs real time."
  type        = number
  default     = 20
}

variable "startup_failure_threshold" {
  description = "Startup probe failures tolerated before the instance is killed. initial_delay + threshold*period is the total boot budget."
  type        = number
  default     = 12
}

variable "ingress" {
  description = "INGRESS_TRAFFIC_ALL | INGRESS_TRAFFIC_INTERNAL_ONLY | INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER."
  type        = string
  default     = "INGRESS_TRAFFIC_ALL"
}

variable "invoker_members" {
  description = <<-EOT
    Members granted roles/run.invoker. ["allUsers"] makes the API PUBLIC — correct only because the
    app enforces its own JWT auth. Never add allUsers to a service whose auth assumes a private network.
  EOT
  type        = list(string)
  default     = []
}

variable "vpc_connector_id" {
  description = "VPC Access connector ID for static-IP egress. Null = default Cloud Run egress."
  type        = string
  default     = null
}

variable "vpc_egress" {
  description = "PRIVATE_RANGES_ONLY | ALL_TRAFFIC. ALL_TRAFFIC is required if Supabase/Qdrant must see the static NAT IP, since those are public endpoints."
  type        = string
  default     = "PRIVATE_RANGES_ONLY"
}

variable "jwt_key_mount_path" {
  description = "Directory the JWT PEM secrets are mounted under. JWT_PRIVATE_KEY_PATH / JWT_PUBLIC_KEY_PATH must point inside it."
  type        = string
  default     = "/etc/notarist/keys"
}

variable "custom_domain" {
  description = "Custom domain to map, e.g. api.notarist.id. Null = use the run.app URL."
  type        = string
  default     = null
}

variable "deletion_protection" {
  description = "Block `terraform destroy` of the service. True in prod."
  type        = bool
  default     = false
}

variable "labels" {
  description = "Resource labels."
  type        = map(string)
  default     = {}
}
