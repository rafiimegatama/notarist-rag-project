#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Build and deploy the Notarist RAG backend to Cloud Run.
#
# Prereqs (once):
#   gcloud auth login && gcloud config set project "$PROJECT_ID"
#   gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
#       cloudbuild.googleapis.com iamcredentials.googleapis.com storage.googleapis.com
#   # Artifact Registry repo:
#   gcloud artifacts repositories create notarist --repository-format=docker --location="$REGION"
#   # GCS bucket + object access for the runtime SA:
#   gsutil mb -l "$REGION" "gs://$GCS_BUCKET"
#   gcloud storage buckets add-iam-policy-binding "gs://$GCS_BUCKET" \
#       --member="serviceAccount:$RUNTIME_SA" --role=roles/storage.objectAdmin
#   # V4 signed URLs from Cloud Run without a key: the runtime SA signs blobs as itself.
#   gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
#       --member="serviceAccount:$RUNTIME_SA" --role=roles/iam.serviceAccountTokenCreator
#
# Secrets (create in Secret Manager, then reference below):
#   POSTGRES_PASSWORD, APP_ENCRYPTION_KEY, APP_ENCRYPTION_SALT
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PROJECT_ID="${PROJECT_ID:?set PROJECT_ID}"
REGION="${REGION:-us-central1}"
SERVICE="${SERVICE:-notarist-backend}"
GCS_BUCKET="${GCS_BUCKET:?set GCS_BUCKET}"
RUNTIME_SA="${RUNTIME_SA:?set RUNTIME_SA (e.g. notarist-run@$PROJECT_ID.iam.gserviceaccount.com)}"
POSTGRES_URL="${POSTGRES_URL:?set POSTGRES_URL (Supabase JDBC URL, sslmode=require)}"
POSTGRES_USER="${POSTGRES_USER:?set POSTGRES_USER}"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/notarist/${SERVICE}:$(date +%Y%m%d-%H%M%S)"

# Build the image from the backend/ context (Cloud Build; no local Docker needed).
gcloud builds submit backend --tag "$IMAGE" --project "$PROJECT_ID"

# Deploy. CPU is kept ALWAYS ALLOCATED and min-instances=1 because the ingestion queue poller
# (@Scheduled in IngestionQueueScheduler) must keep running between HTTP requests — with the
# default request-scoped CPU it would freeze whenever the service is idle.
gcloud run deploy "$SERVICE" \
  --image "$IMAGE" \
  --project "$PROJECT_ID" \
  --region "$REGION" \
  --service-account "$RUNTIME_SA" \
  --no-cpu-throttling \
  --cpu 1 --memory 1Gi \
  --min-instances 1 --max-instances 5 \
  --concurrency 40 \
  --timeout 300 \
  --port 8080 \
  --allow-unauthenticated \
  --set-env-vars "SPRING_PROFILES_ACTIVE=prod" \
  --set-env-vars "GOOGLE_CLOUD_PROJECT=${PROJECT_ID}" \
  --set-env-vars "GCS_BUCKET=${GCS_BUCKET}" \
  --set-env-vars "GCS_SIGNING_SERVICE_ACCOUNT=${RUNTIME_SA}" \
  --set-env-vars "GCS_AUTO_CREATE_BUCKET=false" \
  --set-env-vars "^@^POSTGRES_URL=${POSTGRES_URL}" \
  --set-env-vars "POSTGRES_USER=${POSTGRES_USER}" \
  --set-env-vars "JWT_PRIVATE_KEY_PATH=/secrets/jwt/private.pem" \
  --set-env-vars "JWT_PUBLIC_KEY_PATH=/secrets/jwt/public.pem" \
  --set-secrets "POSTGRES_PASSWORD=POSTGRES_PASSWORD:latest" \
  --set-secrets "APP_ENCRYPTION_KEY=APP_ENCRYPTION_KEY:latest" \
  --set-secrets "APP_ENCRYPTION_SALT=APP_ENCRYPTION_SALT:latest" \
  --set-secrets "/secrets/jwt/private.pem=JWT_PRIVATE_KEY:latest" \
  --set-secrets "/secrets/jwt/public.pem=JWT_PUBLIC_KEY:latest"

echo "Deployed ${SERVICE} → $(gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT_ID" --format='value(status.url)')"
