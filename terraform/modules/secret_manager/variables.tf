variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "name_prefix" {
  description = "Prefix for secret IDs, e.g. \"notarist-prod\"."
  type        = string
}

variable "secrets" {
  description = <<-EOT
    Secret containers to create, keyed by short name. The value is a human description only — never
    a secret value (see the module header for why). The final secret ID is "<name_prefix>-<key>".
  EOT
  type        = map(string)
}

variable "accessor_members" {
  description = "Members granted roles/secretmanager.secretAccessor on every secret (the Cloud Run runtime SA)."
  type        = list(string)
  default     = []
}

variable "replica_locations" {
  description = "Regions to pin secret replication to for data residency. Null = automatic (Google-chosen) replication."
  type        = list(string)
  default     = null
}

variable "labels" {
  description = "Resource labels."
  type        = map(string)
  default     = {}
}
