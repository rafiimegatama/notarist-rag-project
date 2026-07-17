variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "region" {
  description = "Default region."
  type        = string
  default     = "asia-southeast2" # Jakarta — closest to the notarial user base.
}

variable "state_bucket_name" {
  description = "Globally-unique name for the Terraform state bucket."
  type        = string
}

variable "state_bucket_location" {
  description = "Location for the state and audit-log buckets."
  type        = string
  default     = "ASIA-SOUTHEAST2"
}

variable "enable_network_apis" {
  description = "Enable compute + vpcaccess APIs (needed only if any environment uses a VPC connector)."
  type        = bool
  default     = true
}

variable "create_audit_log_bucket" {
  description = "Create the long-term log archive bucket."
  type        = bool
  default     = true
}

variable "audit_log_bucket_name" {
  description = "Globally-unique name for the audit log archive bucket."
  type        = string
  default     = null
}

variable "audit_log_retention_seconds" {
  description = "WORM retention for archived logs. Default is 7 years, matching the audit domain."
  type        = string
  default     = "220752000s"
}
