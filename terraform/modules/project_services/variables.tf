variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "enable_network_apis" {
  description = "Also enable compute + vpcaccess, needed only when routing Cloud Run egress through a VPC connector."
  type        = bool
  default     = false
}

variable "enable_github_actions_apis" {
  description = "Also enable sts, needed only when GitHub Actions federates in via Workload Identity."
  type        = bool
  default     = false
}

variable "enable_billing_apis" {
  description = "Also enable billingbudgets + cloudbilling, needed only when this environment manages a budget (modules/budget). Off by default: enabling a billing API the deployer has no permission to call adds nothing."
  type        = bool
  default     = false
}
