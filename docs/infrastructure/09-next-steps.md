# 09 — Next Steps

Ordered by what blocks what. Items 1–4 are prerequisites for anything running at all.

## Blocking

### 1. Decide the project layout

The Terraform assumes **one project per environment**. The only project that exists is
`project-bead1995-1df2-4fdd-8ec` — a default "My First Project" with a generated name, an
organization parent, and billing already attached.

Three real projects (`notarist-dev-…`, `notarist-staging-…`, `notarist-prod-…`) are the right shape:
per-project IAM, per-project quota, and a `destroy` that cannot reach across environments. Using the
existing project for all three would collapse those boundaries, and the WIF pool IDs would still be
distinct (`name_prefix` includes the environment) but nothing else would be.

**Nobody should do this without deciding it deliberately.** It is the one choice that is expensive
to reverse.

### 2. Get Application Default Credentials, then run a plan

```bash
gcloud auth application-default login
```

`gcloud auth login` authenticates the CLI. Terraform's provider needs ADC. That is why no `plan` has
ever run here, and why `validate` passing is weaker evidence than it looks: it checks configuration
against the provider schema, not against a real project's quota, org policy, or IAM.

### 3. Bootstrap the state bucket

[07-deployment-guide.md](07-deployment-guide.md). Replace the `-000000` placeholders — GCS bucket
names are a single global namespace.

### 4. Populate the secrets before the first apply

[06-secret-management.md](06-secret-management.md). Skipping this creates a Cloud Run revision that
will not start, with an error naming the revision rather than the empty secret.

## Known gaps — infrastructure is ready, something else is not

### 5. The RAG sidecars are not deployed by this Terraform

OCR, NER, reranker, embedding and Ollama are separate services. None appears in the target
architecture and none is provisioned here. Until they exist and `sidecar_urls` points at them,
**ingestion stalls at the OCR stage and RAG search cannot complete.** The app will start and serve
auth and document CRUD.

Either they get their own Cloud Run services (and a module), or they are external and only need
URLs. That is an architecture decision nobody has made yet.

### 6. The service cannot scale to zero — and it is ~95% of the bill

`run_in_process_schedulers = true` forces a warm instance in every environment. **~$384/month at
zero traffic**, see [08-cost-estimation.md](08-cost-estimation.md).

The fix is a **Java change**: expose the pipeline triggers as HTTP endpoints and drive them from
Cloud Scheduler. `modules/scheduler` (`var.jobs`) and the `invoker` service account are already
wired to do exactly this — the infrastructure side is done and waiting. Java was out of scope for
this sprint.

This is the highest-value single change available: ~$110/month immediately, more if prod's
`min_instances` can then drop.

### 7. `openrouter-api-key` and `gemini-api-key` exist but are not wired

Containers only. The `cloudrun` module selects secrets by name, so they are created and not mounted
— which is why adding them was safe. Consuming them needs two changes together:

1. The backend reading `OPENROUTER_API_KEY` / `GEMINI_API_KEY`.
2. A line each in `modules/cloudrun/main.tf` mapping the secret into the env.

Then add them to `verify-secrets` in **both** pipelines and populate the values, or the first deploy
after wiring will fail to start.

### 8. Three of the four buckets have no consumer

`ocr-output`, `generated-documents` and `application-assets` are created, secured and labelled, but
only `documents` is passed to the service (`GCS_BUCKET`). The `terraform graph` shows `cloudrun`
depending on `storage` alone.

The backend needs to know these bucket names before they do anything. That is a backend + `cloudrun`
env change, deliberately not made here.

Note `application-assets` grants the runtime **read-only** access — publishing templates is an
operator/CI job by design ([03-module-overview.md](03-module-overview.md)).

## Should be cleaned up

### 9. Three deploy paths, and two of them overlap

`deploy/cloudrun/deploy.sh` + `service.yaml` predate the Terraform and overlap with it. Cloud Build
and GitHub Actions can both deploy. **Pipelines that can both deploy will eventually disagree about
what is running.**

Pick one. If GitHub Actions wins, retire the Cloud Build trigger and delete `deploy/cloudrun/`. The
existing `terraform/README.md` already flagged the `deploy.sh` overlap; it was left alone because
deleting another engineer's work needs a decision, not an assumption.

### 10. SHA-pin the GitHub Actions

Actions are pinned to major tags (`@v4`), which are mutable — a compromised tag runs in a workflow
holding a token to your cloud project. SHA-pinning removes that. Worth doing before the deploy job
is armed. ([05-ci-cd.md](05-ci-cd.md))

### 11. Verify the CI variable lookups on first run

`vars[format('GCP_PROJECT_ID_{0}', matrix.environment)]` builds an uppercase-suffixed name from a
lowercase matrix value. GitHub documents `vars` lookups as case-insensitive, so this should work —
but it has never run. If the first `plan` fails with empty inputs, that is the cause.

### 12. Consider `github_allowed_ref` for prod

`enable_github_actions` + `github_repository` scope federation to this repository. Prod should also
set `github_allowed_ref = "refs/heads/main"`, so a token minted by a workflow on an arbitrary branch
cannot touch prod.

## Deliberately not done

| | Why |
|---|---|
| Standalone `logging` / `iam` modules | Would make the design worse — [03-module-overview.md](03-module-overview.md) |
| Renaming modules to kebab-case | Churns every `source` path in three roots for zero functional gain |
| `SUPABASE_URL` / `QDRANT_URL` / `GCS_BUCKET` / `OLLAMA_URL` as secrets | A URL is not a credential — [06-secret-management.md](06-secret-management.md) |
| Cloud SQL | Supabase is the production PostgreSQL |
| Deleting `deploy/cloudrun/` | Not this sprint's call — see item 9 |
