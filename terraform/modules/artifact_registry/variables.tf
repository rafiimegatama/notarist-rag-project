variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "region" {
  description = "Region for the repository. Keep it the same as Cloud Run to avoid cross-region pull latency and egress cost."
  type        = string
}

variable "environment" {
  description = "Environment name (dev|staging|prod)."
  type        = string
}

variable "repository_id" {
  description = "Artifact Registry repository ID."
  type        = string
  default     = "notarist"
}

variable "immutable_tags" {
  description = "Forbid re-pointing an existing tag at a new digest. True in prod: it makes a deployed tag a permanent, auditable reference."
  type        = bool
  default     = false
}

variable "keep_recent_versions" {
  description = "How many recent image versions to always keep, so rollback targets survive cleanup."
  type        = number
  default     = 10
}

variable "untagged_retention" {
  description = "Age after which untagged images are deleted, e.g. \"604800s\" (7d)."
  type        = string
  default     = "604800s"
}

variable "cleanup_dry_run" {
  description = "Log what cleanup WOULD delete instead of deleting. Start true; only disable after reviewing the logs."
  type        = bool
  default     = true
}

variable "reader_members" {
  description = "IAM members granted pull access (the Cloud Run runtime SA)."
  type        = list(string)
  default     = []
}

variable "writer_members" {
  description = "IAM members granted push access (the Cloud Build deployer SA)."
  type        = list(string)
  default     = []
}

variable "labels" {
  description = "Resource labels."
  type        = map(string)
  default     = {}
}
