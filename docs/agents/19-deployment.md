# 19 — Deployment

## Topology

```
Spring Boot app (notarist-web, single deployable, modular monolith)
    ├── Oracle 19C           — external, enterprise instance (NOTARIST / NOTARIST_STG / NOTARIST_SEC)
    ├── PostgreSQL           — containerized (infra/docker/docker-compose.yml)
    ├── Qdrant               — containerized
    ├── MinIO                — containerized, 6 buckets, signed-URL direct upload
    ├── Ollama (local LLM)   — containerized, :11434
    ├── PaddleOCR sidecar    — :8081 (CPU-only initially, GPU-ready compose path)
    ├── IndoBERT NER sidecar — :8082
    └── Reranker sidecar     — :8083
```

## Environment / profiles

4 profiles: `local`, `dev`, `staging`, `production`, selected via `SPRING_PROFILES_ACTIVE`
(defaults to `local` in `application.yaml`). All secrets externalized — never hardcoded.
48 env vars registered in total (STEP 7.5 contract), 12 of them secrets — see
[[11-devops-agent]] for the sync-with-`.env.example` responsibility.

## Migrations on deploy

- Oracle: Liquibase changesets from `database/oracle/liquibase/`, applied in order,
  never edited retroactively.
- PostgreSQL: Flyway migrations from `database/postgres/flyway/` (V1–V5 currently),
  sequential and additive.
- A deploy that needs a schema change ships the migration *with* the code change in the same
  reviewable unit — not as a follow-up.

## Build & artifact

Gradle multi-module build (`backend/settings.gradle.kts`, 12 modules). Oracle JDBC driver
pulled from a private Nexus/Artifactory at build time — both CI and local dev need access to
that internal repo; it is not on Maven Central.

## CI/CD

GitLab CI, self-hosted CE — pipeline YAML native to GitLab, with its own built-in container
registry and runner (STEP 6 decision). Pipeline changes go through the same approval gate as
other shared-infrastructure changes (see [[00-project-rules]] rule 6).

## Storage

MinIO uploads bypass the backend entirely via signed URL — the backend issues the signed URL
but the file bytes never transit through `notarist-web`. Buckets: `notarist-raw`,
`notarist-ocr`, `notarist-processed`, `notarist-chunk`, `notarist-export`, `notarist-backup`.

## Observability on deploy

`notarist-observability` module exposes health aggregation and an operational dashboard;
every external sidecar (Ollama, PaddleOCR, IndoBERT, reranker, MinIO, Qdrant, Postgres) needs
a health indicator wired in before it's considered production-ready (see [[11-devops-agent]]).
`DegradedModeRegistry` (in `notarist-infra`) tracks per-service degraded state so the app can
keep serving partial functionality (e.g. keyword-only search) rather than hard-failing when
one sidecar is down.

## Before any production-affecting deploy action

Treat it as a hard-to-reverse, shared-system action per the parent system's execution-care
policy — confirm explicitly before running anything that touches a shared environment,
rather than assuming local dev conventions carry over unchanged.
