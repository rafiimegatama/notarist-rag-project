# 11 — DevOps Agent

## Scope

Local/dev infra (`infra/docker/`), CI/CD, migrations, environment/profile management,
external sidecar operations.

## Local infra (`infra/docker/docker-compose.yml`)

Containerized: `minio`, `postgres`, `qdrant`, `ollama` (plus their named volumes and a shared
`notarist-net` network). `docker-compose.override.yml` and `.env.example` hold
environment-specific overrides. Notably **not** containerized here: Oracle 19C (enterprise
instance, external), and the ML sidecars (PaddleOCR `:8081`, IndoBERT NER `:8082`, reranker
`:8083`) — confirm with [[05-architect-agent]]/[[02-architecture]] before assuming they
belong in this compose file versus a separate deployment.

## Migrations

- **Oracle**: Liquibase, `database/oracle/liquibase/db.changelog-master.yaml` +
  `changes/` — changesets are additive; a migration already applied is never edited, only
  superseded by a new one.
- **PostgreSQL**: Flyway, `database/postgres/flyway/`, currently `V1` (initial) through `V5`
  (`pipeline_run_lifecycle_columns`) — sequential, numbered, never renumbered.

## Environment / profiles

`SPRING_PROFILES_ACTIVE` (default `local`) selects the active profile in
`backend/notarist-web/src/main/resources/application.yaml`. Per STEP 6: 4 profiles overall
(local/dev/staging/production), all secrets externalized — never hardcoded into
`application.yaml` or committed. Per STEP 7.5: 48 env vars registered, 12 of them secrets.

## Build & dependency management

Gradle multi-module (Kotlin DSL), `backend/settings.gradle.kts` lists all 12 modules.
Oracle JDBC (`ojdbc11.jar`) comes from a private Nexus/Artifactory — both local dev and CI
runners pull from there, not Maven Central.

## CI/CD

GitLab CI, self-hosted Community Edition — native pipeline YAML, built-in container registry
and runner (per STEP 6 decision). A pipeline change is itself a change to shared
infrastructure and needs the same sign-off as any other cross-cutting change
(see [[00-project-rules]] rule 6).

## OCR hardware path

CPU-only initially; the Docker Compose setup is GPU-ready so the OCR sidecar can move to
NVIDIA GPU when ingestion volume grows — don't assume GPU is provisioned by default.

## Responsibilities checklist

1. Keep `infra/docker/.env.example` in sync with the actual 48-var registry — a var used in
   code but missing from `.env.example` is a devops defect.
2. Never apply a destructive infra action (drop a volume, force-recreate a container with a
   named volume, `docker compose down -v`) without explicit confirmation — these are
   hard-to-reverse actions per the parent system's execution-care policy.
3. A new external sidecar dependency gets a health check wired into
   `notarist-observability` (see [[02-architecture]]) before it's considered "deployed."
