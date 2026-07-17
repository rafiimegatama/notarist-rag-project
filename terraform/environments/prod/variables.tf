# ---- Identity -------------------------------------------------------------
variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "environment" {
  description = "Environment name."
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging or prod."
  }
}

variable "region" {
  description = "Region for Cloud Run, Artifact Registry, Scheduler and the VPC connector. They must all match."
  type        = string
  default     = "asia-southeast2"
}

# ---- Image ----------------------------------------------------------------
variable "image" {
  description = "Container image for the FIRST create only; Cloud Build owns it after that."
  type        = string
}

variable "artifact_repository_id" {
  description = "Artifact Registry repository ID."
  type        = string
  default     = "notarist"
}

variable "immutable_image_tags" {
  description = "Forbid re-pointing an existing image tag. True in prod."
  type        = bool
  default     = false
}

variable "artifact_cleanup_dry_run" {
  description = "Keep image cleanup policies in dry-run (log only)."
  type        = bool
  default     = true
}

# ---- Cloud Run ------------------------------------------------------------
variable "run_in_process_schedulers" {
  description = "This service runs the app's in-process @Scheduled jobs. Forces a warm instance with always-on CPU."
  type        = bool
  default     = true
}

variable "min_instances" {
  description = "Minimum instances (raised to >=1 automatically when run_in_process_schedulers)."
  type        = number
  default     = 0
}

variable "max_instances" {
  description = "Maximum instances. max_instances * postgres_pool_max must stay under the Supabase connection ceiling."
  type        = number
  default     = 4
}

variable "cpu" {
  description = "CPU limit."
  type        = string
  default     = "1"
}

variable "memory" {
  description = "Memory limit."
  type        = string
  default     = "1Gi"
}

variable "concurrency" {
  description = "Max concurrent requests per instance."
  type        = number
  default     = 80
}

variable "ingress" {
  description = "Cloud Run ingress setting."
  type        = string
  default     = "INGRESS_TRAFFIC_ALL"
}

variable "invoker_members" {
  description = "Who may invoke the service. [\"allUsers\"] = public API (the app enforces its own JWT auth)."
  type        = list(string)
  default     = []
}

variable "custom_domain" {
  description = "Custom domain to map. Null = the run.app URL."
  type        = string
  default     = null
}

variable "deletion_protection" {
  description = "Block terraform destroy of the Cloud Run service."
  type        = bool
  default     = false
}

# ---- Network --------------------------------------------------------------
variable "enable_vpc_egress" {
  description = "Route egress through a VPC connector + Cloud NAT for a STATIC outbound IP. Needed only if Supabase/Qdrant allowlist source IPs. Costs money (connector VMs run 24/7)."
  type        = bool
  default     = false
}

variable "vpc_egress" {
  description = "PRIVATE_RANGES_ONLY | ALL_TRAFFIC. Must be ALL_TRAFFIC for Supabase/Qdrant (public endpoints) to see the static NAT IP."
  type        = string
  default     = "PRIVATE_RANGES_ONLY"
}

# ---- Supabase -------------------------------------------------------------
variable "postgres_url" {
  description = "JDBC URL for Supabase, e.g. jdbc:postgresql://db.<ref>.supabase.co:5432/postgres?sslmode=require. NOT a secret — the password is."
  type        = string
}

variable "postgres_user" {
  description = "Application DB role. Must NOT be a superuser and must NOT hold BYPASSRLS, or the tenant-isolation RLS policies are silently void."
  type        = string
  default     = "notarist_app"
}

variable "postgres_pool_max" {
  description = "HikariCP max pool size PER INSTANCE."
  type        = number
  default     = 10
}

# ---- Qdrant ---------------------------------------------------------------
variable "qdrant_url" {
  description = "Qdrant endpoint URL."
  type        = string
}

variable "qdrant_collection" {
  description = "Qdrant collection name."
  type        = string
  default     = "notarist_chunks"
}

# ---- CI/CD ----------------------------------------------------------------
variable "enable_github_actions" {
  description = "Create the keyless GitHub Actions identity (service account + Workload Identity pool/provider) and enable the sts API."
  type        = bool
  default     = false
}

variable "github_repository" {
  description = "Repository allowed to federate in, as \"owner/repo\". Required when enable_github_actions is true."
  type        = string
  default     = null
}

variable "github_allowed_ref" {
  description = "Restrict federation to one git ref, e.g. \"refs/heads/main\". Recommended in prod. null allows any ref in the repository."
  type        = string
  default     = null
}

variable "github_actions_can_deploy" {
  description = "Grant GitHub Actions permission to deploy Cloud Run. Keep false while the workflow's deploy job is disabled — an identity should not hold a permission its workflow never uses."
  type        = bool
  default     = false
}

# ---- Storage --------------------------------------------------------------
# GCS bucket names are a single GLOBAL namespace shared with every other Google Cloud customer, so
# these have no defaults on purpose: a default would either collide or quietly hand you someone
# else's naming problem at apply time. Convention is notarist-<env>-<purpose>-<project-suffix>.
variable "documents_bucket_name" {
  description = "Globally-unique name for the document bucket."
  type        = string
}

variable "ocr_output_bucket_name" {
  description = "Globally-unique name for the OCR/chunk artefact bucket. Regenerable output derived from the document bucket."
  type        = string
}

variable "generated_documents_bucket_name" {
  description = "Globally-unique name for the bucket holding documents the app generates from templates."
  type        = string
}

variable "application_assets_bucket_name" {
  description = "Globally-unique name for the static asset bucket (document templates, letterheads, fonts)."
  type        = string
}

variable "bucket_location" {
  description = "Document bucket location. Data residency for notarial documents may be legally constrained."
  type        = string
  default     = "ASIA-SOUTHEAST2"
}

variable "bucket_force_destroy" {
  description = "Allow terraform destroy to delete a non-empty document bucket. NEVER true outside dev."
  type        = bool
  default     = false
}

variable "document_retention_seconds" {
  description = "WORM retention on documents, e.g. \"220752000s\" (7y). Null = none."
  type        = string
  default     = null
}

variable "document_retention_locked" {
  description = "Lock the retention policy. IRREVERSIBLE."
  type        = bool
  default     = false
}

variable "cors_origins" {
  description = "Origins allowed to PUT to V4 signed upload URLs (the frontend). Empty = no browser uploads."
  type        = list(string)
  default     = []
}

# ---- Secrets --------------------------------------------------------------
variable "secret_replica_locations" {
  description = "Pin secret replication for data residency. Null = automatic."
  type        = list(string)
  default     = null
}

# ---- JWT ------------------------------------------------------------------
variable "jwt_issuer" {
  description = "JWT issuer claim."
  type        = string
  default     = "notarist-rag"
}

variable "jwt_access_ttl_seconds" {
  description = "Access token TTL."
  type        = number
  default     = 900
}

variable "jwt_refresh_ttl_seconds" {
  description = "Refresh token TTL."
  type        = number
  default     = 604800
}

# ---- Sidecars -------------------------------------------------------------
variable "sidecar_urls" {
  description = <<-EOT
    Base URLs and timeouts for the OCR / NER / reranker / embedding / Ollama sidecars.

    NOT provisioned by this Terraform — they are separate services outside the stated target
    architecture. Until they are deployed and these point at them, ingestion and RAG search cannot
    complete. Passed as a raw env-var map so they can be wired without a Terraform change.
  EOT
  type        = map(string)
  default     = {}
}

# ---- Observability --------------------------------------------------------
variable "log_level" {
  description = "Root log level."
  type        = string
  default     = "INFO"
}

variable "alert_email_addresses" {
  description = "Emails for alerts. EMPTY = no alert policies are created at all."
  type        = list(string)
  default     = []
}

variable "enable_uptime_check" {
  description = "Create an external uptime check."
  type        = bool
  default     = true
}

variable "error_rate_threshold" {
  description = "5xx/sec that trips the error alert."
  type        = number
  default     = 0.5
}

variable "latency_p95_threshold_ms" {
  description = "p95 latency (ms) that trips the latency alert."
  type        = number
  default     = 5000
}

variable "audit_log_bucket" {
  description = "Bucket for long-term log archival (from bootstrap). Null = no sink."
  type        = string
  default     = null
}

# ---- Scheduler ------------------------------------------------------------
variable "enable_heartbeat" {
  description = "Create the /actuator/health heartbeat job."
  type        = bool
  default     = true
}

variable "heartbeat_schedule" {
  description = "Cron for the heartbeat."
  type        = string
  default     = "*/5 * * * *"
}

variable "scheduler_jobs" {
  description = "Extra HTTP-triggered jobs. Must stay empty until the app exposes pipeline triggers as endpoints."
  type        = any
  default     = {}
}

# ---------------------------------------------------------------------------
# Logging (Sprint TF2, Task 8)
# ---------------------------------------------------------------------------

variable "configure_default_log_bucket" {
  description = "Adopt and configure the project's _Default log bucket (retention). See modules/logging for why _Default is configured in place rather than replaced."
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "Application log retention in _Default. 30 days is Google's default AND the free-tier boundary; every day beyond 30 is billed per GiB."
  type        = number
  default     = 30
}

variable "create_audit_log_bucket" {
  description = "Create a separate, longer-lived bucket for Admin Activity / Data Access audit logs."
  type        = bool
  default     = true
}

variable "audit_log_retention_days" {
  description = "Audit log retention. 400 = 13 months (a full year plus overlap). NOT a legal determination — see modules/logging/variables.tf."
  type        = number
  default     = 400
}

variable "audit_log_retention_locked" {
  description = "Lock audit retention. IRREVERSIBLE: blocks terraform destroy of that bucket for the full retention period. Off by default — a lock should be a conscious act."
  type        = bool
  default     = false
}

variable "exclude_health_check_logs" {
  description = "Stop ingesting SUCCESSFUL health probes (~86k lines/month). Failing probes are always kept."
  type        = bool
  default     = true
}

variable "exclude_static_asset_logs" {
  description = "Stop ingesting SUCCESSFUL static asset fetches. Non-2xx always kept."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------
# Budget (Sprint TF2, Task 9)
# ---------------------------------------------------------------------------

variable "billing_account_id" {
  description = "Billing account ID WITHOUT the 'billingAccounts/' prefix, e.g. 01ABCD-2345EF-6789GH. Null/empty disables budget alerting entirely (no budget resource is created). Requires roles/billing.costsManager ON THE BILLING ACCOUNT for the credential running Terraform."
  type        = string
  default     = null
}

variable "project_number" {
  description = "Project NUMBER (not ID) — budget_filter.projects requires projects/<number>. Passing the ID silently scopes the budget to the whole billing account. `gcloud projects describe <id> --format='value(projectNumber)'`."
  type        = string
  default     = ""
}

variable "monthly_budget_amount" {
  description = "Monthly budget in whole currency units. Alerts fire at 50/75/90/100% of actual spend, plus a forecasted-100% early warning."
  type        = number
  default     = 100
}

variable "budget_currency_code" {
  description = "ISO 4217 code. MUST match the billing account's own currency or apply is rejected."
  type        = string
  default     = "USD"
}

variable "budget_notify_billing_admins" {
  description = "Also email the billing account's admins, in addition to the monitoring notification channels."
  type        = bool
  default     = true
}
