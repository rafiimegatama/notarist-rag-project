# 05 — CI/CD

## Two pipelines exist, and that is a decision with a cost

| | Cloud Build | GitHub Actions |
|---|---|---|
| File | `cloudbuild/cloudbuild.yaml` | `.github/workflows/` |
| Identity | `notarist-<env>-deploy` | `notarist-<env>-gha` (keyless) |
| `terraform apply` | **Yes**, gated on `_AUTO_APPLY=true` | **No** |
| Deploy | Yes | Disabled by default |
| Status | Pre-existing | Added this sprint |

The target architecture specifies GitHub → GitHub Actions → Artifact Registry → Cloud Run. The repo
already implemented CI/CD in Cloud Build. The decision was to **add GitHub Actions and keep Cloud
Build**.

**The risk this carries, stated plainly:** two pipelines that can both deploy will eventually
disagree about what is running. If GitHub Actions becomes the real deploy path, retire the Cloud
Build trigger rather than leaving both armed. The same argument already applies to
`deploy/cloudrun/deploy.sh`, which predates both and overlaps with the Terraform.

The two pipelines deliberately share their ordering and their secret-verification logic, so they
cannot disagree about what "green" means.

## `.github/workflows/terraform.yml`

Two tiers, split by whether credentials are needed:

- **`static`** — `fmt`, `tflint`, `validate` across all four roots. No credentials, no variables.
  Runs on every PR, including from forks, from day one. `validate` uses `-backend=false` because a
  static check must not need a state bucket.
- **`plan`** — matrix over dev/staging/prod. Needs WIF and inputs. **Skips** rather than fails when
  `vars.GHA_WIF_PROVIDER` is unset, so an unbootstrapped repo still gets green PRs. Posts the plan
  as a PR comment, truncated at 60k chars (GitHub rejects comments over 65,536).

There is no apply job. See [03-module-overview.md](03-module-overview.md).

## `.github/workflows/deploy.yml`

`build` → `push` → `verify-secrets` → `deploy`.

- **`build`** runs the real Gradle build **with tests, before anything is published**. The Dockerfile
  builds with `-x test` on purpose — a container build is the wrong place to discover a failing test.
  So an image in the registry always corresponds to a commit whose tests passed. Same ordering as
  Cloud Build, deliberately.
- **`push`** tags with both the immutable commit SHA (what gets deployed, what a rollback names) and
  a moving environment tag (used only as a layer cache source).
- **`verify-secrets`** fails loudly if a secret the service mounts has zero versions. A revision
  referencing an empty secret fails to start with an error pointing at the *revision*, not at the
  secret. This turns a confusing rollout failure into a clear message.
- **`deploy`** sets **only the image**. Everything else is Terraform's.

### The deploy job is disabled by default — two switches

1. `vars.ENABLE_CLOUD_RUN_DEPLOY == 'true'` — a repository variable.
2. `github_actions_can_deploy = true` — Terraform.

(1) without (2) means the job runs and fails on permissions, because the GitHub Actions SA does not
hold `run.developer` until Terraform grants it. **This is intended, not an oversight.** The decision
about whether CI may deploy is an infrastructure decision reviewed as Terraform, not a repository
setting one person can flip in a web UI. Flipping (1) alone cannot escalate anything.

The `deploy` job also attaches a GitHub Environment, so prod can require human approval.

## Repository configuration

Settings → Secrets and variables → Actions → **Variables** (not secrets — a WIF provider name and a
project ID are useless without a token GitHub only mints for this repository):

| Variable | From |
|---|---|
| `GHA_WIF_PROVIDER` | `terraform output github_actions_wif_provider` |
| `GHA_SERVICE_ACCOUNT` | `terraform output github_actions_service_account` |
| `GCP_PROJECT_ID_{DEV,STAGING,PROD}` | the project IDs |
| `TF_STATE_BUCKET_{DEV,STAGING,PROD}` | `terraform output` from bootstrap |
| `TF_IMAGE_{…}`, `TF_POSTGRES_URL_{…}`, `TF_QDRANT_URL_{…}` | tfvars values |
| `TF_BUCKET_{DOCUMENTS,OCR_OUTPUT,GENERATED,ASSETS}_{…}` | bucket names |
| `ENABLE_CLOUD_RUN_DEPLOY` | `'true'` to arm deploys |
| `GCP_REGION`, `AR_REPOSITORY` | optional; default `asia-southeast2`, `notarist` |

`terraform.tfvars` is gitignored (it carries project-identifying detail), which is why CI supplies
inputs through `TF_VAR_*`. **None of these are credentials** — every actual secret lives in Secret
Manager and is referenced by name.

## Known-unverified

Neither workflow has ever executed. The YAML parses and every path it references
(`backend/Dockerfile`, `backend/gradlew`) exists, but:

- **The `vars[format('…_{0}', matrix.environment)]` lookups are unproven.** GitHub normalises
  variable names to uppercase and documents `vars` lookups as case-insensitive, so
  `GCP_PROJECT_ID_dev` should resolve `GCP_PROJECT_ID_DEV`. If the first run resolves these to empty
  strings, that is why — uppercase the suffix via a step output.
- Action versions are pinned to major tags (`@v4`), not commit SHAs. SHA-pinning is the
  supply-chain-safe choice; tags were used for legibility. Worth revisiting before this pipeline
  holds deploy authority.
- The first `plan` will be the first real exercise of the WIF trust chain.
