variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "name_prefix" {
  description = "Resource name prefix, e.g. notarist-prod."
  type        = string
}

variable "log_bucket_location" {
  description = <<-EOT
    Log bucket location. NOT freely choosable: _Default is created by Google in "global" and its
    location is IMMUTABLE, so configuring it with anything else fails at apply. Leave "global" unless
    you know the project's _Default was regionalised at creation.
  EOT
  type        = string
  default     = "global"
}

variable "configure_default_bucket" {
  description = "Adopt and configure the project's existing _Default log bucket. Off in dev, where the 30-day default is fine and adopting a shared bucket in a sandbox project is more surprise than value."
  type        = bool
  default     = true
}

variable "default_retention_days" {
  description = <<-EOT
    Retention for application logs in _Default. Google's default is 30.

    Anything up to 30 days is free; beyond that every day is billed per GiB stored. 30 -> 90 roughly
    triples the storage line for logs. Choose from how far back an incident review actually reaches,
    not from a round number.
  EOT
  type        = number
  default     = 30

  validation {
    condition     = var.default_retention_days >= 1 && var.default_retention_days <= 3650
    error_message = "default_retention_days must be between 1 and 3650."
  }
}

variable "create_audit_bucket" {
  description = "Create a separate, longer-lived bucket for audit logs. Off in dev."
  type        = bool
  default     = true
}

variable "audit_retention_days" {
  description = <<-EOT
    Retention for the audit bucket.

    400 days (13 months) is the default because it covers a full year plus a quarter of overlap, which
    is what "show me the same month last year" actually requires. It is NOT a legal determination:
    Indonesian notarial record-keeping obligations attach to the DOCUMENTS (see the documents bucket's
    WORM retention), not to cloud audit logs. Set this from your own compliance advice.
  EOT
  type        = number
  default     = 400

  validation {
    condition     = var.audit_retention_days >= 30 && var.audit_retention_days <= 3650
    error_message = "audit_retention_days must be between 30 and 3650."
  }
}

variable "audit_retention_locked" {
  description = <<-EOT
    Lock the audit bucket's retention.

    IRREVERSIBLE. Once locked, retention cannot be shortened and the bucket cannot be deleted until
    every entry ages out — a `terraform destroy` will FAIL for up to audit_retention_days. That is
    the point (an operator must not be able to erase evidence of their own actions), but it means
    enabling this in prod commits you to the bill for that period. Off by default: a lock should be a
    conscious act, not something inherited from a module default.
  EOT
  type        = bool
  default     = false
}

variable "exclude_health_check_logs" {
  description = "Stop ingesting SUCCESSFUL health probes. Failing probes are always kept."
  type        = bool
  default     = true
}

variable "exclude_static_asset_logs" {
  description = "Stop ingesting SUCCESSFUL static asset fetches. Non-2xx always kept."
  type        = bool
  default     = true
}
