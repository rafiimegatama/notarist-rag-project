# 16 — Repository Structure

```
notarist-rag-project/
├── CLAUDE.md                     — root project rules (source of truth, see [[00-project-rules]])
├── backend/                      — Spring Boot 3 / Java 17, Gradle multi-module
│   ├── settings.gradle.kts       — 12-module include list
│   ├── build.gradle.kts          — root build config
│   ├── notarist-core/            — value objects, exceptions, domain policy, constants
│   ├── notarist-auth/            — JWT, sessions, login/refresh/logout
│   ├── notarist-document/        — legal document CRUD/query
│   ├── notarist-ingest/          — 5-stage async ingestion pipeline
│   ├── notarist-search/          — hybrid retrieval (BM25 + Qdrant + RRF + rerank)
│   ├── notarist-assistant/       — AI assistant, SSE streaming, citation assembly
│   ├── notarist-regulation/      — REGULASI_MASTER hierarchy (BAB/Pasal/Ayat)
│   ├── notarist-audit/           — audit trail module
│   ├── notarist-infra/           — MinIO/Qdrant/Postgres adapters, resilience/degraded-mode
│   ├── notarist-runtime/         — ML/LLM-facing adapters (embedding, OCR, Ollama)
│   ├── notarist-observability/   — health aggregation, operational dashboard, metrics
│   └── notarist-web/             — application entrypoint, security config, OpenAPI, web config
├── frontend/
│   └── NotaristApp/               — React Native (Expo)
│       └── src/{api,contexts,screens,navigation,components,hooks,utils}/
├── database/
│   ├── oracle/liquibase/         — Oracle 19C migrations (db.changelog-master.yaml + changes/)
│   └── postgres/flyway/          — PostgreSQL migrations (V1..V5)
├── infra/
│   └── docker/                   — docker-compose.yml (minio, postgres, qdrant, ollama), .env.example
├── docs/
│   ├── architecture/             — frozen STEP 2–7.5 architecture decisions
│   ├── business/                 — glossary, semantic dictionary, project overview, requirements
│   ├── build/                    — dependency-matrix.md
│   └── agents/                   — this doc set (00–20): the agent operating framework
└── generated/                     — disposable analysis output (docs, sql, backend, json, openapi)
    ├── sql/                       — oracle_transactional_schema.sql, postgres_rag_schema.sql
    ├── openapi/, json/, logs/
```

## Where new things go

- A new architecture decision → `docs/architecture/stepN_*.md`, then reflected into
  `docs/agents/05-architect-agent.md` if it changes a *standing* decision.
- A new business term → `docs/business/business_glossary.md` /
  `semantic_dictionary.md`.
- Ad hoc analysis/SQL generation that isn't meant to be a permanent record → `/generated/*`
  (see [[00-project-rules]] rule 5).
- A new backend capability → the module whose boundary it falls inside (see
  [[05-architect-agent]]'s module-ownership decisions); a genuinely new module needs
  architect sign-off and an entry in `backend/settings.gradle.kts`.

## Module internal layout

See [[06-backend-agent]] for the `api/application/domain/infrastructure/config` layout
shared by every business module.
