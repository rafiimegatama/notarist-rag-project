variable "billing_account_id" {
  description = <<-EOT
    Billing account ID (e.g. "01ABCD-2345EF-6789GH"), WITHOUT the "billingAccounts/" prefix.
    Null or empty disables the module entirely — an environment that must not manage billing creates
    nothing rather than failing to plan.
  EOT
  type        = string
  default     = null
}

variable "project_number" {
  description = <<-EOT
    Project NUMBER, not project ID. google_billing_budget's budget_filter.projects wants
    "projects/<number>"; passing the human-readable ID silently matches nothing, so the budget
    covers the whole billing account instead of this project — an alert that never fires for the
    reason you think it will.
  EOT
  type        = string
}

variable "name_prefix" {
  description = "Resource name prefix, e.g. notarist-prod."
  type        = string
}

variable "monthly_amount" {
  description = "Monthly budget, in whole currency units (no decimals — google_billing_budget takes units as a string integer)."
  type        = number

  validation {
    condition     = var.monthly_amount > 0
    error_message = "monthly_amount must be greater than zero — a zero budget alerts at every threshold immediately."
  }
}

variable "currency_code" {
  description = "ISO 4217 code. MUST match the billing account's currency; a mismatch is rejected at apply."
  type        = string
  default     = "USD"
}

variable "notification_channel_ids" {
  description = "Monitoring notification channel IDs to notify. Pass module.monitoring.notification_channel_ids so budget alerts reach the same humans as every other alert, without minting a second set of channels."
  type        = list(string)
  default     = []
}

variable "notify_billing_admins" {
  description = "Also email the billing account's Billing Administrators/Users via the default IAM recipients. On by default: the ops rotation and the person who pays the invoice are often different people."
  type        = bool
  default     = true
}
