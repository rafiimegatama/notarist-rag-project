output "budget_name" {
  description = "Budget resource name, or null when disabled (no billing_account_id supplied)."
  value       = length(google_billing_budget.this) > 0 ? google_billing_budget.this[0].name : null
}

output "enabled" {
  description = "False when no billing_account_id was supplied — in which case NO budget exists and there is no spend alerting."
  value       = local.enabled
}
