variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "bucket_name" {
  description = "Globally-unique bucket name. GCS bucket names are global, so include the project or environment."
  type        = string
}

variable "location" {
  description = "Bucket location, e.g. ASIA-SOUTHEAST2. Data residency for notarial documents may be legally constrained — choose deliberately."
  type        = string
}

variable "storage_class" {
  description = "Default storage class."
  type        = string
  default     = "STANDARD"
}

variable "versioning_enabled" {
  description = "Keep noncurrent object versions."
  type        = bool
  default     = true
}

variable "force_destroy" {
  description = "Allow `terraform destroy` to delete a NON-EMPTY bucket. Keep false anywhere real documents live."
  type        = bool
  default     = false
}

variable "kms_key_name" {
  description = "CMEK key for encryption at rest. Null = Google-managed keys."
  type        = string
  default     = null
}

variable "retention_seconds" {
  description = "WORM retention period in seconds, e.g. \"220752000s\" for 7 years. Null = no retention policy."
  type        = string
  default     = null
}

variable "retention_locked" {
  description = "LOCK the retention policy. Irreversible — nobody, including project owners, can shorten or remove it afterwards."
  type        = bool
  default     = false
}

variable "noncurrent_versions_to_keep" {
  description = "How many noncurrent versions to retain before deleting the oldest."
  type        = number
  default     = 3
}

variable "enable_storage_tiering" {
  description = "Age objects down to NEARLINE then COLDLINE."
  type        = bool
  default     = true
}

variable "nearline_after_days" {
  description = "Age (days) at which live objects move to NEARLINE."
  type        = number
  default     = 90
}

variable "coldline_after_days" {
  description = "Age (days) at which live objects move to COLDLINE."
  type        = number
  default     = 365
}

variable "cors_origins" {
  description = "Origins allowed to PUT to V4 signed upload URLs. Empty = no CORS (server-side uploads only). Never use [\"*\"] for a confidential bucket."
  type        = list(string)
  default     = []
}

variable "access_log_bucket" {
  description = "Bucket to write GCS access logs to. Null = no access logging."
  type        = string
  default     = null
}

variable "object_admin_members" {
  description = "Members granted roles/storage.objectAdmin (the Cloud Run runtime SA)."
  type        = list(string)
  default     = []
}

variable "object_viewer_members" {
  description = "Members granted read-only object access."
  type        = list(string)
  default     = []
}

variable "labels" {
  description = "Resource labels."
  type        = map(string)
  default     = {}
}
