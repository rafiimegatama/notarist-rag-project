/**
 * GitHub Actions identity — keyless, via Workload Identity Federation.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THERE IS NO SERVICE ACCOUNT KEY HERE
 *
 * The obvious way to let GitHub Actions touch GCP is `gcloud iam service-accounts keys create` and
 * paste the JSON into a repository secret. That key is a permanent, non-expiring credential to a
 * cloud identity, sitting in a system where anyone with repo admin can read it, it appears in
 * `terraform state` if Terraform mints it, and it does not rotate. Key leakage is the single most
 * common route into a GCP project.
 *
 * Workload Identity Federation replaces it: GitHub signs a short-lived OIDC token describing the
 * workflow run, and GCP exchanges that for a ~1 hour access token. Nothing long-lived exists, so
 * there is nothing to leak or rotate.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE LINE THAT MATTERS MOST: attribute_condition
 *
 * A WIF provider with no attribute_condition trusts EVERY token GitHub issues — that is, every
 * workflow in every public repository on GitHub. Anyone could impersonate this service account from
 * a repo they created a minute earlier. The condition below pins the exchange to this repository
 * specifically, and google_iam_workload_identity_pool_provider will refuse to apply without one when
 * the issuer is GitHub. Do not relax it to make a fork's CI pass.
 */

locals {
  gha_enabled = var.create_github_actions ? 1 : 0

  # Pin the exchange to this repository. Optionally pin it to a single branch too, which is what you
  # want for prod: a token minted by a workflow on some throwaway branch should not be able to deploy.
  gha_attribute_condition = join(" && ", compact([
    "assertion.repository == '${var.github_repository}'",
    var.github_allowed_ref == null ? null : "assertion.ref == '${var.github_allowed_ref}'",
  ]))
}

resource "google_service_account" "github_actions" {
  count = local.gha_enabled

  project      = var.project_id
  account_id   = "${var.name_prefix}-gha"
  display_name = "Notarist GitHub Actions (${var.environment})"
  description  = "Identity GitHub Actions federates into via OIDC. Builds and pushes images, plans Terraform. Holds no key."

  # A variable validation block cannot read another variable before Terraform 1.9, and these modules
  # support >= 1.5 — so the "enabled but unscoped" case is caught here instead. Stop the apply rather
  # than build a provider whose attribute_condition compares against an empty string.
  lifecycle {
    precondition {
      condition     = var.github_repository != null
      error_message = "create_github_actions = true requires github_repository (\"owner/repo\"). Without it the Workload Identity provider would not be scoped to a repository."
    }
  }
}

resource "google_iam_workload_identity_pool" "github" {
  count = local.gha_enabled

  project                   = var.project_id
  workload_identity_pool_id = "${var.name_prefix}-gh-pool"
  display_name              = "GitHub Actions (${var.environment})"
  description               = "Federates GitHub Actions OIDC tokens for ${var.github_repository}."
}

resource "google_iam_workload_identity_pool_provider" "github" {
  count = local.gha_enabled

  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github[0].workload_identity_pool_id
  workload_identity_pool_provider_id = "${var.name_prefix}-gh-provider"
  display_name                       = "GitHub OIDC"

  # Only these claims are readable in IAM conditions and bindings. Mapping the minimum keeps the
  # trust surface small and legible.
  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  attribute_condition = local.gha_attribute_condition

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# The binding that actually lets the workflow become the service account. Scoped by
# attribute.repository, so a token from any other repo fails here even if it somehow reached the
# provider.
resource "google_service_account_iam_member" "github_actions_wif" {
  count = local.gha_enabled

  service_account_id = google_service_account.github_actions[0].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github[0].name}/attribute.repository/${var.github_repository}"
}

# ---------------------------------------------------------------------------
# Roles: build, push, and PLAN. Deliberately not apply.
#
# Terraform APPLY authority stays with the Cloud Build deployer. GitHub Actions can see a plan and
# publish an image, but a workflow file — which any repo write can change — cannot rewrite the
# project's IAM or delete a bucket. That is why roles/secretmanager.admin,
# roles/iam.serviceAccountAdmin and roles/resourcemanager.projectIamAdmin appear on the deployer
# above and NOT here.
#
# roles/viewer is what makes `terraform plan` work: a plan must read every resource it manages. It is
# broad in reach but read-only, and notably does NOT include secretmanager.versions.access — a plan
# can see that a secret exists without being able to read its value.
# ---------------------------------------------------------------------------
locals {
  github_actions_project_roles = var.create_github_actions ? concat([
    "roles/viewer",                  # terraform plan: read the world, change nothing
    "roles/artifactregistry.writer", # push images
    "roles/logging.logWriter",
    ],
    # Only when the deploy job is switched on. Left off, a leaked token cannot ship a revision.
    var.github_actions_can_deploy ? ["roles/run.developer"] : []
  ) : []
}

resource "google_project_iam_member" "github_actions" {
  for_each = toset(local.github_actions_project_roles)

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.github_actions[0].email}"
}

# Deploying a service that RUNS as the runtime SA requires permission to act as it. Gated with the
# deploy capability for the same reason as run.developer.
resource "google_service_account_iam_member" "github_actions_acts_as_runtime" {
  count = var.create_github_actions && var.github_actions_can_deploy ? 1 : 0

  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.github_actions[0].email}"
}
