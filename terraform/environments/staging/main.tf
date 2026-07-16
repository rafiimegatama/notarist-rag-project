/**
 * Notarist platform — environment root.
 *
 * Identical across dev/staging/prod by design: the SHAPE of the infrastructure should not vary by
 * environment, only its parameters. Everything that differs lives in terraform.tfvars, so "what is
 * different about prod?" is answered by diffing two small files rather than two large ones.
 *
 * Dependency order (Terraform infers most of it from references; the comments say why):
 *   project_services  →  everything          (an API must be on before its resources exist)
 *   service_account   →  storage/secrets/AR  (bindings need the member to exist)
 *   secret_manager    →  cloudrun            (a revision with an unresolvable secret will not start)
 *   cloudrun          →  scheduler/monitoring(they target the service URL)
 */

terraform {
  required_version = ">= 1.5"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

locals {
  name_prefix = "notarist-${var.environment}"

  labels = {
    app         = "notarist"
    environment = var.environment
    managed_by  = "terraform"
    component   = "rag-backend"
  }

  # Secret CONTAINERS. Values are never set by Terraform — see modules/secret_manager/main.tf.
  #
  # Only genuine CREDENTIALS belong here. Connection topology (POSTGRES_URL, QDRANT_URL, GCS_BUCKET,
  # OLLAMA_BASE_URL) is passed as a plain env var instead: a URL is not a credential, and routing it
  # through Secret Manager would add cost, an extra IAM dependency, and — because a secret with zero
  # versions blocks a revision from starting — a startup failure mode, in exchange for no
  # confidentiality gain. See the "Secrets vs. configuration" table in terraform/README.md.
  secrets = {
    "postgres-password"   = "Supabase PostgreSQL password for the application role"
    "app-encryption-key"  = "AES-256 key for S1/S2 field-level encryption (APP_ENCRYPTION_KEY)"
    "app-encryption-salt" = "Salt for field-level encryption (APP_ENCRYPTION_SALT)"
    "qdrant-api-key"      = "Qdrant Cloud API key"
    "jwt-private-key"     = "RSA private key PEM for signing JWTs — mounted as a FILE, not an env var"
    "jwt-public-key"      = "RSA public key PEM for verifying JWTs — mounted as a FILE"
    "openrouter-api-key"  = "OpenRouter API key for hosted LLM inference (OPENROUTER_API_KEY)"
    "gemini-api-key"      = "Google Gemini API key for hosted LLM inference (GEMINI_API_KEY)"
  }
}

# ---------------------------------------------------------------------------
module "project_services" {
  source = "../../modules/project_services"

  project_id                 = var.project_id
  enable_network_apis        = var.enable_vpc_egress
  enable_github_actions_apis = var.enable_github_actions
  # Only when this environment actually manages a budget — see module.budget.
  enable_billing_apis = var.billing_account_id != null && var.billing_account_id != ""
}

# ---------------------------------------------------------------------------
module "service_account" {
  source = "../../modules/service_account"

  project_id  = var.project_id
  environment = var.environment
  name_prefix = local.name_prefix

  # Keyless GitHub Actions identity. github_repository is the security boundary — see
  # modules/service_account/github_actions.tf.
  create_github_actions     = var.enable_github_actions
  github_repository         = var.github_repository
  github_allowed_ref        = var.github_allowed_ref
  github_actions_can_deploy = var.github_actions_can_deploy

  depends_on = [module.project_services]
}

# ---------------------------------------------------------------------------
module "artifact_registry" {
  source = "../../modules/artifact_registry"

  project_id    = var.project_id
  region        = var.region
  environment   = var.environment
  repository_id = var.artifact_repository_id

  immutable_tags  = var.immutable_image_tags
  cleanup_dry_run = var.artifact_cleanup_dry_run

  reader_members = ["serviceAccount:${module.service_account.runtime_email}"]
  writer_members = compact([
    module.service_account.deployer_email == null ? "" : "serviceAccount:${module.service_account.deployer_email}",
  ])

  labels     = local.labels
  depends_on = [module.project_services]
}

# ---------------------------------------------------------------------------
module "storage" {
  source = "../../modules/storage"

  project_id  = var.project_id
  bucket_name = var.documents_bucket_name
  location    = var.bucket_location

  versioning_enabled = true
  force_destroy      = var.bucket_force_destroy
  retention_seconds  = var.document_retention_seconds
  retention_locked   = var.document_retention_locked
  cors_origins       = var.cors_origins

  # The app reads, writes and deletes objects; it must not be able to reconfigure the bucket.
  object_admin_members = ["serviceAccount:${module.service_account.runtime_email}"]

  labels     = local.labels
  depends_on = [module.project_services]
}

# ---------------------------------------------------------------------------
# OCR / chunk artefacts derived from the documents bucket.
#
# Confidential — this is the *content* of notarial documents in text form, so it inherits the same
# strict defaults (public access prevention, UBLA) from the module. But unlike the originals it is
# REGENERABLE: re-run the pipeline and you get it back. Hence no versioning (an overwrite costs a
# re-run, not an original) and no WORM retention (the 7-year obligation attaches to the source
# document, not to a derived cache of it).
module "storage_ocr_output" {
  source = "../../modules/storage"

  project_id  = var.project_id
  bucket_name = var.ocr_output_bucket_name
  location    = var.bucket_location

  versioning_enabled = false
  force_destroy      = var.bucket_force_destroy
  retention_seconds  = null

  object_admin_members = ["serviceAccount:${module.service_account.runtime_email}"]

  labels     = merge(local.labels, { data_class = "confidential-derived" })
  depends_on = [module.project_services]
}

# ---------------------------------------------------------------------------
# Documents the app produces from templates (draft akta, exports).
#
# Versioned: a regenerated draft must not silently destroy the previous one a notary may have already
# acted on. No WORM lock — these are drafts; retention attaches once a document is finalised into the
# documents bucket. CORS is on because the client downloads these directly via a V4 signed URL.
module "storage_generated_documents" {
  source = "../../modules/storage"

  project_id  = var.project_id
  bucket_name = var.generated_documents_bucket_name
  location    = var.bucket_location

  versioning_enabled = true
  force_destroy      = var.bucket_force_destroy
  retention_seconds  = null
  cors_origins       = var.cors_origins

  object_admin_members = ["serviceAccount:${module.service_account.runtime_email}"]

  labels     = merge(local.labels, { data_class = "confidential" })
  depends_on = [module.project_services]
}

# ---------------------------------------------------------------------------
# Static application assets: document templates, letterheads, fonts.
#
# Two deliberate differences from every other bucket here:
#
#   - The runtime SA gets objectVIEWER, not objectAdmin. The app READS templates; it has no reason to
#     rewrite them. Assets are a deployed artefact — CI or an operator publishes them. This means a
#     compromised container cannot tamper with the template that every future akta is generated from.
#   - Tiering is OFF. These objects are small, hot and read on nearly every generation. Ageing them
#     into COLDLINE would add per-read retrieval charges to the hottest path in the bucket, which is
#     the opposite of the saving tiering exists to produce.
module "storage_application_assets" {
  source = "../../modules/storage"

  project_id  = var.project_id
  bucket_name = var.application_assets_bucket_name
  location    = var.bucket_location

  versioning_enabled     = true
  force_destroy          = var.bucket_force_destroy
  retention_seconds      = null
  enable_storage_tiering = false
  cors_origins           = var.cors_origins

  object_viewer_members = ["serviceAccount:${module.service_account.runtime_email}"]

  labels     = merge(local.labels, { data_class = "internal" })
  depends_on = [module.project_services]
}

# ---------------------------------------------------------------------------
module "secret_manager" {
  source = "../../modules/secret_manager"

  project_id  = var.project_id
  name_prefix = local.name_prefix
  secrets     = local.secrets

  accessor_members  = ["serviceAccount:${module.service_account.runtime_email}"]
  replica_locations = var.secret_replica_locations

  labels     = local.labels
  depends_on = [module.project_services]
}

# ---------------------------------------------------------------------------
module "network" {
  source = "../../modules/network"

  enabled     = var.enable_vpc_egress
  project_id  = var.project_id
  region      = var.region
  environment = var.environment
  name_prefix = local.name_prefix

  depends_on = [module.project_services]
}

# ---------------------------------------------------------------------------
module "cloudrun" {
  source = "../../modules/cloudrun"

  project_id   = var.project_id
  region       = var.region
  service_name = "${local.name_prefix}-api"

  # First-create only. Cloud Build owns the image thereafter (ignore_changes in the module).
  image                 = var.image
  service_account_email = module.service_account.runtime_email
  secret_ids            = module.secret_manager.secret_ids

  # The pipeline runs on in-process timers, so the instance must keep CPU when idle.
  run_in_process_schedulers = var.run_in_process_schedulers

  min_instances = var.min_instances
  max_instances = var.max_instances
  cpu           = var.cpu
  memory        = var.memory
  concurrency   = var.concurrency

  ingress         = var.ingress
  invoker_members = var.invoker_members

  vpc_connector_id = module.network.connector_id
  vpc_egress       = var.vpc_egress

  custom_domain       = var.custom_domain
  deletion_protection = var.deletion_protection

  env_vars = merge(
    {
      SPRING_PROFILES_ACTIVE = var.environment
      LOG_LEVEL              = var.log_level

      # ---- Supabase. The password is a SECRET (see module.secret_manager); the URL and user are
      # ---- connection topology, not credentials, and belong in plain config.
      POSTGRES_URL      = var.postgres_url
      POSTGRES_USER     = var.postgres_user
      POSTGRES_POOL_MAX = tostring(var.postgres_pool_max)

      # ---- Google Cloud Storage. No keys: auth is ADC via the runtime SA.
      GCS_BUCKET             = module.storage.bucket_name
      GOOGLE_CLOUD_PROJECT   = var.project_id
      GCS_AUTO_CREATE_BUCKET = "false" # Terraform owns the bucket; the app must not create one.

      # Signs V4 upload URLs by impersonating ITSELF through the IAM signBlob API, so no private key
      # is ever mounted or baked. Requires the self-tokenCreator binding in the service_account module.
      GCS_SIGNING_SERVICE_ACCOUNT = module.service_account.runtime_email

      # ---- Qdrant (API key is a secret).
      QDRANT_URL        = var.qdrant_url
      QDRANT_COLLECTION = var.qdrant_collection

      # ---- JWT. These are PATHS into the Secret Manager volume mounts, not key material.
      JWT_ISSUER                    = var.jwt_issuer
      JWT_PRIVATE_KEY_PATH          = "/etc/notarist/keys/private/notarist-private.pem"
      JWT_PUBLIC_KEY_PATH           = "/etc/notarist/keys/public/notarist-public.pem"
      JWT_ACCESS_TOKEN_TTL_SECONDS  = tostring(var.jwt_access_ttl_seconds)
      JWT_REFRESH_TOKEN_TTL_SECONDS = tostring(var.jwt_refresh_ttl_seconds)
    },
    # ---- Sidecars (OCR, NER, reranker, embedding, Ollama). These are NOT provisioned by this
    # ---- Terraform — they are separate services and nothing in the target architecture deploys
    # ---- them. Until they exist, these point nowhere and the RAG pipeline cannot complete.
    # ---- See terraform/README "Blockers".
    var.sidecar_urls,
  )

  labels = local.labels

  depends_on = [module.secret_manager, module.artifact_registry]
}

# ---------------------------------------------------------------------------
module "scheduler" {
  source = "../../modules/scheduler"

  project_id  = var.project_id
  region      = var.region
  name_prefix = local.name_prefix

  service_name = module.cloudrun.service_name
  service_url  = module.cloudrun.service_url

  invoker_service_account_email = module.service_account.invoker_email

  enable_heartbeat   = var.enable_heartbeat
  heartbeat_schedule = var.heartbeat_schedule
  jobs               = var.scheduler_jobs

  depends_on = [module.cloudrun]
}

# The scheduler's OIDC token is worthless unless the invoker SA may actually invoke the service.
resource "google_cloud_run_v2_service_iam_member" "scheduler_invoker" {
  project  = var.project_id
  location = var.region
  name     = module.cloudrun.service_name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${module.service_account.invoker_email}"
}

# ---------------------------------------------------------------------------
module "monitoring" {
  source = "../../modules/monitoring"

  project_id  = var.project_id
  environment = var.environment

  service_name = module.cloudrun.service_name
  service_host = replace(module.cloudrun.service_url, "https://", "")

  alert_email_addresses = var.alert_email_addresses
  enable_uptime_check   = var.enable_uptime_check

  # Alert if the warm instance disappears — that silently stops the ingestion pipeline.
  expect_always_on_instance = var.run_in_process_schedulers

  error_rate_threshold     = var.error_rate_threshold
  latency_p95_threshold_ms = var.latency_p95_threshold_ms
  audit_log_bucket         = var.audit_log_bucket

  depends_on = [module.cloudrun]
}

# ---------------------------------------------------------------------------
# Log retention + exclusions (Sprint TF2, Task 8).
#
# Separate from module.monitoring on purpose: that module owns ALERTING (what should page someone),
# this one owns RETENTION and INGESTION (what is kept, for how long, and what is never charged for).
# They both touch Cloud Logging; they answer different questions and are not duplicates.
module "logging" {
  source = "../../modules/logging"

  project_id  = var.project_id
  name_prefix = local.name_prefix

  configure_default_bucket = var.configure_default_log_bucket
  default_retention_days   = var.log_retention_days

  create_audit_bucket    = var.create_audit_log_bucket
  audit_retention_days   = var.audit_log_retention_days
  audit_retention_locked = var.audit_log_retention_locked

  exclude_health_check_logs = var.exclude_health_check_logs
  exclude_static_asset_logs = var.exclude_static_asset_logs

  depends_on = [module.project_services]
}

# ---------------------------------------------------------------------------
# Billing budget (Sprint TF2, Task 9).
#
# Creates NOTHING unless billing_account_id is set, because the budget is created against the BILLING
# ACCOUNT and the project-level deployer usually has no permission there. An environment that cannot
# manage billing plans cleanly instead of failing.
#
# Reuses the monitoring module's notification channels rather than minting a second set of email
# channels for the same people (Task 1: never duplicate).
module "budget" {
  source = "../../modules/budget"

  billing_account_id = var.billing_account_id
  project_number     = var.project_number
  name_prefix        = local.name_prefix

  monthly_amount = var.monthly_budget_amount
  currency_code  = var.budget_currency_code

  notification_channel_ids = module.monitoring.notification_channel_ids
  notify_billing_admins    = var.budget_notify_billing_admins

  depends_on = [module.project_services]
}
