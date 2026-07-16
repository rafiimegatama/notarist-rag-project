variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "environment" {
  description = "Environment name (dev|staging|prod)."
  type        = string
}

variable "name_prefix" {
  description = "Prefix for service account IDs, e.g. \"notarist-dev\". Must keep the final account_id within 6-30 chars."
  type        = string
}

variable "create_deployer" {
  description = "Create the Cloud Build deployer service account."
  type        = bool
  default     = true
}

variable "create_invoker" {
  description = "Create the Cloud Scheduler invoker service account."
  type        = bool
  default     = true
}

variable "create_github_actions" {
  description = "Create the GitHub Actions service account and its Workload Identity Federation pool/provider. Requires github_repository."
  type        = bool
  default     = false
}

variable "github_repository" {
  description = "The repository allowed to federate in, as \"owner/repo\". This is the security boundary: without it, any GitHub repository could impersonate the service account."
  type        = string
  default     = null

  validation {
    # Fail at plan time rather than creating a provider that trusts all of GitHub.
    condition     = var.github_repository == null || can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "github_repository must be \"owner/repo\", e.g. \"notarist/notarist-rag-project\"."
  }
}

variable "github_allowed_ref" {
  description = "Optionally restrict federation to a single git ref, e.g. \"refs/heads/main\". Recommended for prod so a token minted on an arbitrary branch cannot deploy. null allows any ref in the repository."
  type        = string
  default     = null
}

variable "github_actions_can_deploy" {
  description = "Grant GitHub Actions roles/run.developer and actAs on the runtime SA. Leave false while the deploy job is disabled: a workflow that cannot deploy should not hold permission to."
  type        = bool
  default     = false
}
