/**
 * Secret Manager — secret CONTAINERS and access bindings. NOT secret VALUES.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY TERRAFORM DOES NOT SET THE VALUES
 *
 * Terraform state is a plaintext record of everything Terraform manages. Put a password in a
 * `google_secret_manager_secret_version.secret_data` and you have not protected it — you have
 * copied it into the state file (and into every plan output, and every CI log that echoes a plan).
 * Marking the variable `sensitive = true` hides it from CLI output; it does NOT remove it from
 * state. "Never hardcode secrets" has to mean "the value never enters the IaC pipeline at all".
 *
 * So: Terraform creates the empty secret and grants the runtime SA permission to read it. A human
 * (or a secure CI step) writes the value ONCE, out of band:
 *
 *   printf '%s' "$PGPASSWORD" | gcloud secrets versions add notarist-prod-postgres-password \
 *       --data-file=- --project=<project>
 *
 * Rotation is then just another `versions add` — the app reads :latest and picks it up on the next
 * revision, with no Terraform run and no state churn.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ORDERING — READ THIS BEFORE THE FIRST DEPLOY
 *
 * A Cloud Run revision that references a secret with ZERO versions fails to start, with an error
 * that points at the revision rather than at the empty secret. Populate every secret below BEFORE
 * the first `terraform apply` of the cloudrun module. `make secrets-check` (see terraform/README)
 * verifies this.
 */

resource "google_secret_manager_secret" "this" {
  for_each = var.secrets

  project   = var.project_id
  secret_id = "${var.name_prefix}-${each.key}"

  labels = merge(var.labels, {
    # Lets you find "which secrets does the app actually consume?" without reading Terraform.
    consumed_by = "cloud-run"
  })

  replication {
    dynamic "auto" {
      for_each = var.replica_locations == null ? [1] : []
      content {}
    }

    # Pin replication to specific regions when data residency is a legal constraint — plausible for
    # Indonesian notarial data.
    dynamic "user_managed" {
      for_each = var.replica_locations == null ? [] : [1]
      content {
        dynamic "replicas" {
          for_each = var.replica_locations
          content {
            location = replicas.value
          }
        }
      }
    }
  }

  # Deleting a secret is not recoverable and takes the app down. Require an explicit, deliberate
  # removal from `var.secrets` plus a state surgery, rather than letting a refactor drop it.
  lifecycle {
    prevent_destroy = true
  }
}

# The runtime SA may READ secret values. It may not create, update or delete them — a compromised
# container can therefore use the DB password but cannot rotate it out from under you or exfiltrate
# a secret it was never granted.
resource "google_secret_manager_secret_iam_member" "accessor" {
  for_each = {
    for pair in setproduct(keys(var.secrets), var.accessor_members) :
    "${pair[0]}:${pair[1]}" => { secret = pair[0], member = pair[1] }
  }

  project   = var.project_id
  secret_id = google_secret_manager_secret.this[each.value.secret].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = each.value.member
}
