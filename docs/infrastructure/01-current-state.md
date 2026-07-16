# 01 — Current State

Verified against the live project on **2026-07-15**. Everything below was checked with `gcloud` or
`terraform`, not inferred from the configuration.

## The project

| | |
|---|---|
| Account | `tbs13dd@gmail.com` |
| Project ID | `project-bead1995-1df2-4fdd-8ec` ("My First Project") |
| Project number | `826434106527` |
| Organization | `617058961201` |
| Lifecycle | `ACTIVE` |
| Billing | **Enabled** — `012E10-1433D0-147C03` (not modified) |
| Caller IAM | `roles/owner`, plus `bigquery.dataEditor`, `pubsub.publisher`, `pubsub.subscriber` |

This is a default "My First Project", not a purpose-made Notarist project. The Terraform is written
for **one project per environment** (`notarist-dev-…`, `notarist-staging-…`, `notarist-prod-…` in
the tfvars examples). See [09-next-steps.md](09-next-steps.md).

The `environment` tag warning from `gcloud` is informational and does not block anything.

## APIs

Enabled during this sprint (they were off):

`run` · `cloudbuild` · `artifactregistry` · `secretmanager` · `iam` · `cloudscheduler` ·
`cloudresourcemanager`

`iamcredentials` came on as a dependency of `iam` — which matters, because it is what backs the
**signBlob** call the app uses to mint V4 signed upload URLs. Without it uploads fail in a way that
looks like a bucket permission error.

Already on: `storage`, `logging`, `monitoring`, `serviceusage`, `cloudtrace`, `pubsub`, plus the
BigQuery/Dataplex set that ships with a default project.

Still off, deliberately:

| API | Why |
|---|---|
| `compute`, `vpcaccess` | Only needed when `enable_vpc_egress = true`. Terraform enables them on demand. |
| `sts` | Only needed for GitHub Actions Workload Identity. Terraform enables it when `enable_github_actions = true`. |

These were enabled with `gcloud`, which is a bootstrap exception to the no-click-ops rule: you
cannot `terraform apply` against an API that is off. `modules/project_services` declares all of them
with `disable_on_destroy = false`, so state matches reality and a `destroy` of one environment
cannot turn an API off underneath another.

## Existing resources

**None.** Zero GCS buckets, no Cloud Run services, no Artifact Registry repositories, no service
accounts beyond the defaults. Nothing has ever been applied. The `terraform-state` bucket does not
exist, so `bootstrap/` is a genuine create rather than an adopt.

## The Terraform, before this sprint

Already present and in good condition — roughly 4,500 lines across a bootstrap root, nine modules
and three environment roots. It was **not** scaffolded from scratch in this sprint; it was audited
and extended. It already passed `terraform fmt -check -recursive` and `terraform validate` on all
four roots before any change was made.

Audit findings, and what happened to each:

| Finding | Action |
|---|---|
| No `.github/` — CI/CD was Cloud Build only | Added two workflows ([05-ci-cd.md](05-ci-cd.md)) |
| No GitHub Actions identity | Added, keyless via Workload Identity Federation |
| One bucket (`documents`); four were specified | Added `ocr-output`, `generated-documents`, `application-assets` |
| `OPENROUTER_API_KEY` / `GEMINI_API_KEY` missing | Added as containers |
| 18 TFLint warnings — modules pinned no provider version | Added `versions.tf` to all nine modules; TFLint now clean |
| `sts` API absent, required by WIF | Added to `project_services`, gated |
| No standalone `logging` / `iam` module | **Left as-is on purpose** — see [03-module-overview.md](03-module-overview.md) |
| Modules named `snake_case`, spec said `kebab-case` | **Left as-is** — churns every `source` path in three roots for no functional gain |

## How far this was verified

| Check | Result |
|---|---|
| `terraform fmt -check -recursive` | Clean |
| `terraform validate` × 4 roots | All "The configuration is valid." |
| `tflint --recursive` | 0 issues (was 18) |
| `terraform graph` | Generated — [03-module-overview.md](03-module-overview.md) |
| Workflow YAML parses | Both files |
| `terraform plan` | **Never run** |
| `terraform apply` | **Never run** |

**`plan` was not run, and this is the honest limit of the work.** The container has no Application
Default Credentials (`gcloud auth login` authenticates the CLI, not the Terraform provider), and
`terraform plan` requires both ADC and a state bucket that does not exist yet. `validate` checks the
configuration against the real provider schema — it catches type and schema errors. It does not
catch quota, IAM propagation, org policy, or a bucket name someone else already owns. **The first
apply is still a first apply.**

To get further, run `gcloud auth application-default login` and then the bootstrap in
[07-deployment-guide.md](07-deployment-guide.md).
