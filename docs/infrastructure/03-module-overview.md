# 03 — Module Overview

Nine modules, instantiated twelve times per environment (`storage` four times).

| Module | Instances | Creates |
|---|---|---|
| `project_services` | 1 | API enablement |
| `service_account` | 1 | runtime / deployer / invoker / github-actions + WIF + IAM |
| `artifact_registry` | 1 | Docker repository + reader/writer bindings |
| `storage` | 4 | documents, ocr-output, generated-documents, application-assets |
| `secret_manager` | 1 | 8 secret containers + accessor bindings |
| `network` | 1 | VPC + connector + Cloud NAT + static egress IP (off by default) |
| `cloudrun` | 1 | the service, env, secret mounts, scaling, invoker IAM |
| `scheduler` | 1 | heartbeat + HTTP-triggered jobs |
| `monitoring` | 1 | uptime check, alert policies, log sink, dashboard |

---

## `project_services`

Enables twelve APIs unconditionally, plus two conditional sets: `compute`/`vpcaccess` when
`enable_network_apis`, and `sts` when `enable_github_actions_apis` (added this sprint — WIF token
exchange fails without it).

`disable_on_destroy = false` throughout. A `terraform destroy` of one environment must never turn an
API off at the *project* level, which would break every other environment sharing it.

## `service_account`

Four identities, each with a reason to exist separately:

- **runtime** — what Cloud Run runs the container as. Project-level roles are telemetry only
  (`logging.logWriter`, `monitoring.metricWriter`, `cloudtrace.agent`); everything with data-plane
  reach is granted as a *resource-level* binding in the module that owns the resource.
- **deployer** — Cloud Build. Holds `terraform apply` authority.
- **invoker** — Cloud Scheduler. Can invoke the service and nothing else.
- **github_actions** — added this sprint. Keyless, see below.

The non-obvious binding: the runtime SA holds `roles/iam.serviceAccountTokenCreator` **on itself**.
The app mints V4 signed upload URLs by calling signBlob as its own identity rather than holding a
private key. Without this the app starts fine and only fails when a user tries to upload — a
signed-URL 403 that looks like a bucket problem and is not.

### The GitHub Actions identity (`github_actions.tf`)

No service account key exists. GitHub signs a short-lived OIDC token describing the workflow run and
GCP exchanges it for a ~1 hour access token. A JSON key would be a permanent credential sitting in a
repository secret, readable by anyone with repo admin, that never rotates.

**`attribute_condition` is the security boundary.** A WIF provider without one trusts *every* token
GitHub issues — every workflow in every public repo on GitHub. The condition pins the exchange to
this repository, and optionally to a single ref (`github_allowed_ref`, recommended for prod so a
token minted on a throwaway branch cannot deploy). A `precondition` fails the apply if
`create_github_actions = true` without `github_repository`, rather than building a provider that
compares against an empty string. (`precondition` rather than variable `validation` because
cross-variable validation needs Terraform ≥ 1.9 and these modules support ≥ 1.5.)

Its roles stop at `roles/viewer` + `artifactregistry.writer` + `logging.logWriter`, with
`run.developer` and actAs added only when `github_actions_can_deploy = true`. **`terraform apply`
authority is deliberately withheld** — a workflow file is editable by anyone with repo write, so an
apply from GitHub Actions would mean repo write equals project admin. `roles/viewer` is what makes
`plan` work; it is broad in reach but read-only and does **not** include
`secretmanager.versions.access`, so a plan can see that a secret exists without reading its value.

## `storage`

Instantiated four times. Module-wide, non-negotiable defaults:

- `public_access_prevention = "enforced"` — the bucket *cannot* be made public, even by a later
  careless IAM change. Not the GCS default, and the single most valuable line in the module.
- `uniform_bucket_level_access` — no per-object ACLs, so "who can read this?" has one auditable
  answer instead of one per object.

Per-instance choices, each following from what the bucket holds:

| Bucket | Versioning | Retention | Tiering | Runtime access |
|---|---|---|---|---|
| `documents` | yes | optional WORM (7y in prod) | yes | objectAdmin |
| `ocr_output` | **no** | none | yes | objectAdmin |
| `generated_documents` | yes | none | yes | objectAdmin |
| `application_assets` | yes | none | **no** | **objectViewer** |

- `ocr_output` is not versioned because it is regenerable: re-run the pipeline and you get it back.
  An overwrite costs a re-run, not an original. WORM retention attaches to the source document, not
  to a derived cache of it.
- `application_assets` gets **read-only** runtime access. The app reads templates; it has no reason
  to rewrite them. They are a deployed artefact, published by CI or an operator. This means a
  compromised container cannot tamper with the template every future akta is generated from.
- `application_assets` disables tiering. The objects are small, hot, and read on nearly every
  generation; ageing them to COLDLINE would add retrieval charges to the hottest path in the bucket
  — the opposite of what tiering is for.

The runtime gets `objectAdmin`, never `storage.admin`. `storage.admin` would let the runtime change
the bucket's IAM and retention policy — i.e. disable its own guardrails.

## `secret_manager`

Creates containers and accessor bindings. **Never values.** See
[06-secret-management.md](06-secret-management.md). `prevent_destroy = true` on every secret:
deleting one is unrecoverable and takes the app down, so removal must be deliberate rather than a
side effect of a refactor.

## `cloudrun`

The one derived behaviour worth knowing: `run_in_process_schedulers = true` forces
`min_instances >= 1` and `cpu_idle = false`. The app drives ingestion from in-process `@Scheduled`
jobs and Cloud Run gives an idle instance no CPU — so `min_instances = 0` would stop the pipeline
silently. The module *derives* both rather than exposing them as independent knobs, so they cannot
drift into a configuration that looks fine and does nothing.

This is also the platform's single largest cost driver — see [08-cost-estimation.md](08-cost-estimation.md).

The module `ignore_changes` the image, so Terraform and the deploy pipeline never fight over the
same field. Terraform owns everything about the service *except* which image is running.

## `monitoring`

Uptime check, alert policies, log-based metrics, log sink, dashboard. Includes an alert for
`rls_identity_skipped` — a fail-closed row-level-security path that returns zero rows and looks
exactly like a bug rather than like a security control working. Also alerts if the warm instance
disappears.

`alerting_enabled` is an output, and it is `false` when no alert emails are configured — meaning
**no alert policies exist at all**. Dev sets `alert_email_addresses = []`.

---

## Why there is no `logging` module and no `iam` module

Both were specified. Both were deliberately not built.

**Logging** lives in `monitoring`, because a log-based metric, the alert policy that reads it, and
the sink that exports it are one concern. Splitting them across two modules to satisfy a naming
checklist would mean a cross-module reference for every metric and a strictly worse dependency graph.

**IAM** is distributed on purpose. Each module grants its own resource-level bindings: `storage`
grants bucket access, `secret_manager` grants secret access, `artifact_registry` grants pull/push.
That *is* the least-privilege design. A central `iam` module would either duplicate those bindings
or pull them up to project-level roles — which is precisely the anti-pattern the current tree avoids
by never granting `roles/storage.admin` or `roles/secretmanager.admin` to the runtime.

Building either would have satisfied the spec and made the infrastructure worse.
