# 07 — Deployment Guide

Nothing has been applied. This is the path from an empty project to a running service.

## 0. Prerequisites

```bash
gcloud auth login
gcloud auth application-default login    # Terraform's provider needs ADC, NOT the CLI login
```

**These are two different credentials.** `gcloud auth login` authenticates the CLI; the Terraform
Google provider reads Application Default Credentials. Having the first without the second is why
`terraform plan` could not be run during the infrastructure sprint.

Decide your project layout first. The Terraform assumes **one project per environment**
(`notarist-dev-…`, `notarist-staging-…`, `notarist-prod-…`). The current project
`project-bead1995-1df2-4fdd-8ec` is a default "My First Project" and is not one of them — see
[09-next-steps.md](09-next-steps.md).

## 1. Bootstrap — once per project

Creates the state bucket and enables the APIs. Local state, because it is creating the bucket the
other roots will store their state in.

```bash
terraform -chdir=terraform/bootstrap init
terraform -chdir=terraform/bootstrap apply \
  -var project_id=notarist-prod-000000 \
  -var state_bucket_name=notarist-tfstate-prod-000000
```

Bucket names are one **global** namespace shared with every Google Cloud customer. `-000000` in the
examples is a placeholder — replace it with the project number or the apply fails on a name someone
else already owns.

Bootstrap's local state file is not precious (it manages one bucket and some API enablement, and
re-running is a no-op), but do not delete the bucket it created. Everything else's state is in it.

## 2. Configure the environment

```bash
cp terraform/environments/prod/terraform.tfvars.example \
   terraform/environments/prod/terraform.tfvars
$EDITOR terraform/environments/prod/terraform.tfvars
```

At minimum: `project_id`, `image`, `postgres_url`, `qdrant_url`, and all four bucket names.

`image` on a first create must point at something that starts and serves `/actuator/health`, or the
initial apply creates a service that never becomes Ready. The pipeline owns the image afterwards.

## 3. Populate secrets — BEFORE the first apply

See [06-secret-management.md](06-secret-management.md). Skipping this produces a Cloud Run revision
that will not start, with an error naming the revision rather than the empty secret.

## 4. Apply

```bash
terraform -chdir=terraform/environments/prod init \
  -backend-config="bucket=notarist-tfstate-prod-000000"
terraform -chdir=terraform/environments/prod plan     # read this
terraform -chdir=terraform/environments/prod apply
```

**Read the plan.** This is the first apply against a real project; `validate` passing does not mean
`apply` will. Expect to discover at least one of: a taken bucket name, an org policy, a quota, or
IAM propagation lag (bindings can take a minute to become effective, and a retry usually fixes it).

## 5. Wire up CI

```bash
terraform -chdir=terraform/environments/prod output github_actions_wif_provider
terraform -chdir=terraform/environments/prod output github_actions_service_account
```

Set them as repository **variables** with the rest of the table in [05-ci-cd.md](05-ci-cd.md).
Requires `enable_github_actions = true` and `github_repository = "owner/repo"` in tfvars.

## 6. Deploy

Push to `main`. Cloud Build builds, tests, pushes and deploys. GitHub Actions builds, tests, pushes
and — unless both switches in [05-ci-cd.md](05-ci-cd.md) are on — stops before deploying.

## Rollback

```bash
gcloud run services update-traffic notarist-prod-api \
  --to-revisions=<previous-revision>=100 --region=asia-southeast2
```

Traffic-shift, not redeploy: instant, and it does not depend on the build that broke you. Then fix
forward. Deploying an older image tag also works but takes minutes and re-runs the same pipeline.

## What "working" will not mean yet

The service will start and serve auth and document CRUD. It will **not** complete ingestion or RAG
search, because the OCR/NER/reranker/embedding/Ollama sidecars are not deployed by this Terraform and
`sidecar_urls` has nothing in it. Ingestion stalls at the OCR stage. This is a known gap, not a
misconfiguration.

## Things that will silently break

**The Supabase app role must not be a superuser and must not hold `BYPASSRLS`.** Both bypass
row-level security outright, silently voiding every cross-tenant isolation policy. Everything keeps
working; every tenant just sees every other tenant. **Nothing in GCP can detect this** — it is a
Supabase-side setting, and it is the highest-consequence item in this documentation set.

**`max_instances × POSTGRES_POOL_MAX` must stay under the Supabase connection ceiling.** Prod is
sized at 10 × 8 = 80. Raise either and check the plan's limit first, or move to the transaction
pooler on port 6543.

**RLS returning zero rows looks exactly like a bug.** The tenant policy is fail-closed by design. A
repository path that is not `@Transactional` cannot establish identity, so its queries return nothing
rather than another tenant's data. Nothing has leaked — but users report it as missing documents.
The `rls_identity_skipped` log-based metric and alert exist to make that a one-minute diagnosis.
