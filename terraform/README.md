# Notarist — Google Cloud Infrastructure

Everything that runs in Google Cloud is defined here. Nothing is clicked in the console.

```
Cloud Build ──► Artifact Registry ──► Cloud Run ──► Supabase (PostgreSQL)
                                          │           Qdrant (vectors)
                                          ├────────► Cloud Storage (documents)
                                          └────────► Secret Manager (credentials)
                    Cloud Scheduler ──────┘
                    Cloud Logging + Monitoring
```

---

## Layout

```
terraform/
  bootstrap/            Run ONCE per project: creates the TF state bucket + enables APIs.
  modules/
    project_services/   API enablement
    service_account/    runtime / deployer / invoker identities + IAM
    artifact_registry/  container image repository
    storage/            document bucket (CONFIDENTIAL data — see the module header)
    secret_manager/     secret CONTAINERS and access bindings (never values)
    network/            optional VPC connector + Cloud NAT for a STATIC egress IP
    cloudrun/           the service itself
    scheduler/          heartbeat + future HTTP-triggered jobs
    monitoring/         uptime, 5xx, latency, and this system's specific failure modes
  environments/
    dev/ staging/ prod/ identical main.tf; all differences live in terraform.tfvars
cloudbuild/
  cloudbuild.yaml       build → test → push → plan → deploy → smoke test
```

The environment roots are byte-identical apart from `backend.tf`. That is deliberate: the *shape* of
the infrastructure must not drift between environments, so "what's different about prod?" is
answered by diffing two small tfvars files, not two large HCL trees.

---

## Required Google APIs

Enabled automatically by `modules/project_services` (so this table is documentation, not a checklist
you have to execute):

| API | Why |
|---|---|
| `run.googleapis.com` | The service. |
| `artifactregistry.googleapis.com` | Container images. |
| `secretmanager.googleapis.com` | DB password, encryption keys, JWT keypair, Qdrant key. |
| `storage.googleapis.com` | Document bucket **and** the Terraform state bucket. |
| `iam.googleapis.com` | Service accounts. |
| `iamcredentials.googleapis.com` | **signBlob** — without it, GCS V4 signed upload URLs fail. Easy to miss. |
| `cloudbuild.googleapis.com` | CI/CD. |
| `cloudscheduler.googleapis.com` | Heartbeat / cron. |
| `logging.googleapis.com` | Logs, log-based metrics, the audit sink. |
| `monitoring.googleapis.com` | Uptime checks, alert policies. |
| `cloudresourcemanager.googleapis.com`, `serviceusage.googleapis.com` | Terraform needs these to manage the project at all. |
| `compute.googleapis.com`, `vpcaccess.googleapis.com` | **Only** when `enable_vpc_egress = true`. |

---

## Deployment flow

### One-time, per project

```bash
# 1. State bucket + APIs. Local state; the only Terraform here that has any.
terraform -chdir=terraform/bootstrap init
terraform -chdir=terraform/bootstrap apply \
  -var project_id=notarist-prod-000000 \
  -var state_bucket_name=notarist-tfstate-prod-000000

# 2. Point the environment at that bucket.
#    Edit terraform/environments/prod/backend.tf, or pass it at init:
terraform -chdir=terraform/environments/prod init \
  -backend-config="bucket=notarist-tfstate-prod-000000"

# 3. Configure the environment.
cp terraform/environments/prod/terraform.tfvars.example \
   terraform/environments/prod/terraform.tfvars
$EDITOR terraform/environments/prod/terraform.tfvars

# 4. Create the infrastructure. Cloud Run will be created but will NOT become healthy yet —
#    the secrets are still empty. That is expected; step 5 fixes it.
terraform -chdir=terraform/environments/prod apply
```

### Secrets — the step that will bite you if you skip it

Terraform creates the secret **containers**. It never sets their **values**, because a value passed
through a Terraform variable is a value written into the state file in plaintext — `sensitive = true`
hides it from CLI output but does *not* remove it from state. So the values are written once, out of
band:

```bash
P=notarist-prod-000000
E=notarist-prod

printf '%s' "$SUPABASE_PASSWORD"  | gcloud secrets versions add $E-postgres-password   --data-file=- --project=$P
printf '%s' "$APP_ENCRYPTION_KEY" | gcloud secrets versions add $E-app-encryption-key  --data-file=- --project=$P
printf '%s' "$APP_ENCRYPTION_SALT"| gcloud secrets versions add $E-app-encryption-salt --data-file=- --project=$P
printf '%s' "$QDRANT_API_KEY"     | gcloud secrets versions add $E-qdrant-api-key      --data-file=- --project=$P

# The JWT keypair. These are mounted as FILES, because JwtService reads them from a path.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out notarist-private.pem
openssl rsa -in notarist-private.pem -pubout -out notarist-public.pem
gcloud secrets versions add $E-jwt-private-key --data-file=notarist-private.pem --project=$P
gcloud secrets versions add $E-jwt-public-key  --data-file=notarist-public.pem  --project=$P
shred -u notarist-private.pem   # do not leave it on a laptop
```

`terraform output secret_populate_commands` prints this list for the environment you are in.

**A Cloud Run revision that references a secret with zero versions will not start**, and the error
points at the revision, not at the empty secret. The `verify-secrets` step in `cloudbuild.yaml`
catches this before deploying and tells you which secret is empty.

Rotation is just another `versions add` — the service reads `latest` and picks it up on the next
revision. No Terraform run, no state churn.

### Every deploy

Cloud Build, triggered from a git push:

1. **Gradle build + tests.** Fails here → nothing is published. (The Dockerfile builds with `-x test`
   on purpose: a container build is the wrong place to discover a failing test.)
2. **Docker build**, cached from the previous environment image.
3. **verify-secrets** — every secret has an enabled version.
4. **terraform plan** — surfaces infrastructure drift on every deploy.
5. **terraform apply** — *gated behind `_AUTO_APPLY=true`*. A routine code push must not be able to
   mutate infrastructure as a side effect.
6. **Deploy** — sets only the image. Everything else about the service (env, secrets, scaling,
   mounts) is Terraform's, and the `cloudrun` module `ignore_changes` the image so the two never
   fight over the same field.
7. **Smoke test** — polls `/actuator/health`. A deploy that reports success but serves 503 is not a
   success.

---

## Secrets vs. configuration

| Value | Where | Why |
|---|---|---|
| `POSTGRES_PASSWORD`, `APP_ENCRYPTION_KEY`, `APP_ENCRYPTION_SALT`, `QDRANT_API_KEY` | Secret Manager → **env var** | Credentials. |
| JWT private + public PEM | Secret Manager → **volume mount** at `/etc/notarist/keys` | `JwtService` calls `Files.readString(Paths.get(...))`. It wants a file. Passing the PEM as an env var fails at startup — and the tempting "fix" is baking the key into the image, which is how private keys end up in a container registry. |
| `POSTGRES_URL`, `POSTGRES_USER`, `QDRANT_URL`, `GCS_BUCKET` | Plain env var | Connection topology, not credentials. |
| GCS credentials | **Nothing** | ADC via the runtime service account. There is no key file anywhere. |

---

## Things that will silently break, and the guardrails against them

**The app role must not be a superuser and must not hold `BYPASSRLS`.** Both bypass PostgreSQL
row-level security outright, which silently voids every cross-tenant isolation policy. Everything
keeps working; every tenant just sees every other tenant. Nothing in GCP can detect this — it is a
Supabase-side setting, and it is the single highest-consequence item on this page.

**`min_instances = 0` stops the ingestion pipeline.** The app drives ingestion from *in-process*
`@Scheduled` jobs, and Cloud Run gives an idle instance no CPU. The `cloudrun` module therefore
derives `min_instances >= 1` and `cpu_idle = false` from `run_in_process_schedulers` rather than
letting them be set independently, and `monitoring` alerts if the warm instance ever disappears.

**Supabase connection ceiling.** `max_instances × POSTGRES_POOL_MAX` (plus the separate ingest pool)
must stay under the Supabase limit. Prod is sized at 10 × 8 = 80. Raise either number and check the
plan's ceiling first, or use the transaction pooler on port 6543.

**RLS returning zero rows looks exactly like a bug.** The tenant policy is fail-closed by design. A
repository path that is not `@Transactional` cannot establish the identity, and its queries return
nothing rather than another tenant's data. Nothing has leaked — but users report it as missing
documents. There is a log-based metric and alert (`rls_identity_skipped`) specifically so this is
diagnosed in a minute instead of a day.

---

## Blockers — this will not serve traffic end-to-end yet

1. **The RAG sidecars are not deployed by this Terraform.** OCR, NER, reranker, embedding and Ollama
   are separate services, and none of them appears in the stated target architecture. Until they
   exist and `sidecar_urls` points at them, ingestion stalls at the OCR stage and RAG search cannot
   complete. The app will start and serve auth and document CRUD.

2. **The service cannot scale to zero.** Consequence of the in-process schedulers above. The fix is a
   Java change — expose the pipeline triggers as HTTP endpoints and drive them from Cloud Scheduler,
   which is already wired to do exactly that (`modules/scheduler`, `var.jobs`). Java was out of scope
   for this work.

3. **Supabase and Qdrant are provisioned outside Terraform.** They are not GCP resources. Their
   URLs, the DB role, and the RLS-safe role attributes are inputs here, not outputs. If they must
   allowlist a source IP, set `enable_vpc_egress = true` and give them `terraform output egress_ips`.

4. **The first `apply` creates a Cloud Run service that will not go healthy** until the secrets are
   populated and a real image is pushed. Expected; not a misconfiguration.

5. **Nothing here has been applied against a real GCP project.** `terraform validate` passes for all
   four roots against the real provider schema, which catches schema and type errors — it does not
   catch quota, IAM-propagation or org-policy problems. The first `apply` is still a first apply.

6. **`deploy/cloudrun/service.yaml` + `deploy.sh` still exist** from the earlier imperative approach
   and now overlap with this Terraform. They should be deleted so there is one way to deploy, but
   they were outside what I was asked to touch.
