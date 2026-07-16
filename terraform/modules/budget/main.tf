/**
 * Billing budget + threshold alerts (Sprint TF2, Task 9).
 *
 * New module: nothing in this Terraform created a `google_billing_budget`, so the project had no
 * spend ceiling and no warning before one. For a platform whose costs scale with LLM inference
 * (OpenRouter/Gemini per-token) and Cloud Run instance-hours, the failure mode is not a slow drift —
 * it is a retry loop or a runaway ingestion job turning a $40 month into a $4,000 one, discovered on
 * the invoice.
 *
 * ---------------------------------------------------------------------------------------------
 * WHAT A BUDGET IS AND IS NOT
 *
 * This ALERTS. It does not cap. Google has no "stop spending" switch — the documented way to enforce
 * a hard cap is a budget Pub/Sub topic wired to a function that disables project billing, which
 * takes the whole platform down (including the database path) and is a decision for a human, not a
 * default. So this module notifies and stops there, and the README says so rather than implying a
 * ceiling that does not exist.
 *
 * BILLING ACCOUNT SCOPE: `google_billing_budget` is created against the BILLING ACCOUNT, not the
 * project. The credential running Terraform therefore needs `roles/billing.costsManager` (or
 * `billing.budgets.create`) ON THE BILLING ACCOUNT — a permission the project-level deployer does
 * NOT have by default. That is the single most likely reason a first `terraform apply` of this
 * module fails; see the sprint report's "required credentials".
 *
 * The module is gated on `billing_account_id` being non-null so that an environment which cannot
 * (or should not) manage billing simply creates nothing, rather than failing to plan.
 */

locals {
  enabled = var.billing_account_id != null && var.billing_account_id != ""

  # 50 / 75 / 90 / 100 as specified, plus a FORECASTED 100% rung.
  #
  # The forecast rung is the one that is actually actionable: CURRENT_SPEND at 100% tells you the
  # money is already gone, which is a receipt, not an alert. FORECASTED_SPEND at 100% fires when
  # Google's projection says this month WILL breach — typically days earlier, while a runaway job can
  # still be stopped. Both are kept: the forecast can be wrong in either direction, and the actual is
  # the ground truth.
  actual_thresholds = [0.5, 0.75, 0.9, 1.0]
}

resource "google_billing_budget" "this" {
  count = local.enabled ? 1 : 0

  billing_account = var.billing_account_id
  display_name    = "${var.name_prefix} — monthly budget"

  budget_filter {
    projects = ["projects/${var.project_number}"]

    # Scope to this project's spend only. Without this the budget covers the entire billing account,
    # so a shared account would alert on other projects' costs and this project's own overrun could
    # hide inside the noise.
    calendar_period = "MONTH"

    # Credits (free tier, committed-use discounts, promotions) are subtracted before comparing to the
    # amount. INCLUDE_ALL_CREDITS means the budget tracks what will actually be INVOICED, which is
    # the number a finance owner recognises.
    credit_types_treatment = "INCLUDE_ALL_CREDITS"
  }

  amount {
    specified_amount {
      currency_code = var.currency_code
      units         = tostring(var.monthly_amount)
    }
  }

  dynamic "threshold_rules" {
    for_each = local.actual_thresholds
    content {
      threshold_percent = threshold_rules.value
      spend_basis       = "CURRENT_SPEND"
    }
  }

  # The early-warning rung. See the note in locals.
  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "FORECASTED_SPEND"
  }

  all_updates_rule {
    # REUSES the notification channels the monitoring module already created, rather than minting a
    # second set of email channels for the same humans. Task 1: never duplicate resources.
    monitoring_notification_channels = var.notification_channel_ids

    # Also email whoever administers the billing account. Belt and braces: the monitoring channels
    # are project-scoped and an ops rotation may not include the person who pays the bill.
    disable_default_iam_recipients = !var.notify_billing_admins

    # Only meaningful with a pubsub_topic, which this module deliberately does not create — see the
    # header on why an automated billing kill-switch is not a default.
    schema_version = "1.0"
  }
}
