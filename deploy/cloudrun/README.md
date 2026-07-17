# Notarist RAG — Cloud Run Deployment

Target architecture: **Cloud Run** (compute) + **Google Cloud Storage** (documents) + **Supabase**
(PostgreSQL). No MinIO, no localhost dependencies at runtime.

## Contents
- `Dockerfile` (`backend/Dockerfile`) — multi-stage, non-root JRE image, binds `$PORT`.
- `deploy.sh` — build (Cloud Build) + `gcloud run deploy` with the correct flags.
- `service.yaml` — declarative Knative equivalent for GitOps.

---

## 1. One-time GCP setup

```bash
export PROJECT_ID=your-project
export REGION=us-central1
export GCS_BUCKET=notarist-documents          # globally unique
export RUNTIME_SA=notarist-run@$PROJECT_ID.iam.gserviceaccount.com

gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
    cloudbuild.googleapis.com iamcredentials.googleapis.com storage.googleapis.com

gcloud iam service-accounts create notarist-run --display-name "Notarist Cloud Run"
gcloud artifacts repositories create notarist --repository-format=docker --location=$REGION

# GCS bucket + object access for the runtime SA
gsutil mb -l $REGION gs://$GCS_BUCKET
gcloud storage buckets add-iam-policy-binding gs://$GCS_BUCKET \
    --member="serviceAccount:$RUNTIME_SA" --role=roles/storage.objectAdmin

# Let the runtime SA sign V4 upload URLs as itself (no private key on the instance)
gcloud iam service-accounts add-iam-policy-binding $RUNTIME_SA \
    --member="serviceAccount:$RUNTIME_SA" --role=roles/iam.serviceAccountTokenCreator

# Secrets
for s in POSTGRES_PASSWORD APP_ENCRYPTION_KEY APP_ENCRYPTION_SALT JWT_PRIVATE_KEY JWT_PUBLIC_KEY; do
  gcloud secrets create $s --replication-policy=automatic 2>/dev/null || true
done
# ...then add versions (e.g. gcloud secrets versions add POSTGRES_PASSWORD --data-file=-)
gcloud secrets add-iam-policy-binding POSTGRES_PASSWORD \
    --member="serviceAccount:$RUNTIME_SA" --role=roles/secretmanager.secretAccessor
# (repeat the binding for each secret)
```

## 2. Deploy

```bash
PROJECT_ID=$PROJECT_ID REGION=$REGION GCS_BUCKET=$GCS_BUCKET RUNTIME_SA=$RUNTIME_SA \
POSTGRES_URL='jdbc:postgresql://db.<ref>.supabase.co:5432/postgres?sslmode=require' \
POSTGRES_USER=postgres \
  ./deploy/cloudrun/deploy.sh
```

---

## 3. Environment variables

### Storage — Google Cloud Storage
| Variable | Required | Default | Purpose |
|---|---|---|---|
| `GCS_BUCKET` | **yes** | — | Single bucket for all pipeline stages (keys are stage-prefixed). |
| `GOOGLE_CLOUD_PROJECT` | on Cloud Run: auto | *(from ADC)* | GCP project; resolved from the metadata server on Cloud Run. |
| `GCS_SIGNING_SERVICE_ACCOUNT` | recommended on Cloud Run | *(unset)* | SA whose identity signs V4 upload URLs via IAM `signBlob`. Set to the runtime SA. |
| `GCS_CREDENTIALS_PATH` | local/CI only | *(unset → ADC)* | Path to a SA key file for local signing. **Leave empty on Cloud Run.** |
| `GCS_LOCATION` | no | `US` | Region for auto-created buckets only. |
| `GCS_AUTO_CREATE_BUCKET` | no | `false` | Verify-or-create bucket at startup. Keep `false` in prod. |
| `INGEST_SIGNED_URL_TTL_SECONDS` | no | `3600` | Lifetime of signed upload URLs. |

> Authentication is **Application Default Credentials** — there is no endpoint, access key, or
> secret key. On Cloud Run that is the attached runtime service account (Workload Identity).

### Database — Supabase PostgreSQL
| Variable | Required | Default | Purpose |
|---|---|---|---|
| `POSTGRES_URL` | **yes** | `jdbc:postgresql://localhost:5432/notarist` | Supabase JDBC URL; append `?sslmode=require`. |
| `POSTGRES_USER` | **yes** | `notarist_app` | DB user. |
| `POSTGRES_PASSWORD` | **yes** | — | From Secret Manager. |
| `POSTGRES_POOL_MAX` | no | `10` | Keep `pool-max × max-instances` under Supabase's connection ceiling, or use the Supabase transaction pooler. |
| `FLYWAY_ENABLED` / `LIQUIBASE_ENABLED` | no | `false` | Enable migrations for a fresh DB (see app config notes). |

### Runtime / Cloud Run
| Variable | Required | Default | Purpose |
|---|---|---|---|
| `PORT` | injected by Cloud Run | `8080` | Listen port; `server.port` honours it. |
| `SHUTDOWN_TIMEOUT` | no | `25s` | Graceful-shutdown drain window (< Cloud Run SIGKILL grace). |
| `SPRING_PROFILES_ACTIVE` | no | `local` | Set `prod` on Cloud Run. |
| `SERVER_PORT` | no | `8080` | Only used off Cloud Run (PORT wins). |

### Security / JWT
| Variable | Required | Purpose |
|---|---|---|
| `APP_ENCRYPTION_KEY`, `APP_ENCRYPTION_SALT` | **yes** | App-layer encryption (Secret Manager). |
| `JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY_PATH` | **yes** | Mount PEM keys via Secret Manager volumes (e.g. `/secrets/jwt/*.pem`). |
| `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL_SECONDS`, `JWT_REFRESH_TOKEN_TTL_SECONDS` | no | JWT config. |

### AI sidecars (only if enabled — not part of the core Cloud Run target)
`OCR_BASE_URL`, `NER_BASE_URL`, `RERANKER_BASE_URL`, `EMBEDDING_BASE_URL`, `OLLAMA_BASE_URL`,
`QDRANT_URL` — all default to `localhost` and **must** be pointed at real reachable services (or
their features disabled) before those pipeline stages work on Cloud Run. See "Runtime blockers".

---

## 4. Cloud Run runtime checklist

| Area | Status | Notes |
|---|---|---|
| **Port binding** | ✅ | `server.port=${PORT:${SERVER_PORT:8080}}` — honours Cloud Run's `$PORT`. |
| **Filesystem** | ✅ | No app writes to local disk. Container FS is read-only-friendly; only `/tmp` (tmpfs) is writable if ever needed. |
| **Temp files** | ✅ | No `createTempFile`/`new File(...)` writes in the pipeline — documents stream through GCS. |
| **Streaming** | ✅ | Uploads bypass the app via signed PUT; reads use `ReadChannel` streaming (no full-object buffering). |
| **Graceful shutdown** | ✅ | `server.shutdown=graceful` + lifecycle timeout; scheduler waits for in-flight tasks (`awaitTermination=30s`). |
| **Thread pools** | ✅ | Ingest scheduler pool is bounded (`ingest-sched-*`, size 4) and drains on shutdown. |
| **Scheduler** | ⚠️ | `@Scheduled` ingestion poller requires **CPU always allocated** (`--no-cpu-throttling`) and **min-instances ≥ 1**, else polling stalls when idle. Set by `deploy.sh` / `service.yaml`. |
| **Statelessness** | ✅ | No in-memory session state; JWT stateless; queue state in Postgres (`SKIP LOCKED`). |
| **Health probes** | ✅ | `/actuator/health` used for startup + liveness probes. |
