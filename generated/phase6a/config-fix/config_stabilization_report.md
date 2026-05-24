# PHASE 6A.3-FIX — Config Stabilization Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P0 → P2

---

## Fix Summary

| Priority | Area | P0 Items | P1 Items | P2 Items |
|---|---|---|---|---|
| P0 | Namespace unification | 5 namespace fragmentations resolved | — | — |
| P0 | ObjectMapper ownership | 1 duplicate @Primary removed | — | — |
| P0 | Security filter activation | JWT filter wired in chain | — | — |
| P0 | Credential literals | 2 hardcoded defaults removed from source | — | — |
| P0 | Duplicate bean names | `postgresDataSource` + `postgresJdbcTemplate` deduplicated | — | — |
| P1 | DTO contract | AssistantRequest vs AssistantQueryRequest mismatch fixed | — | — |
| P1 | SSE timeout | Emitter 60s → 90s (> Ollama 60s) | — | — |
| P1 | Ollama timeout wiring | YAML property now wired to OllamaRuntimeAdapter | — | — |
| P1 | Secret hygiene | YAML MinIO defaults removed; .env.example created | — | — |
| P2 | Profile strategy | 4 profile files created | — | — |
| P2 | DataSource hardening | Oracle DataSource upgraded to HikariCP | — | — |

---

## Complete File Change Log

### P0 — Namespace / Bean / ObjectMapper / Security

| File | Change |
|---|---|
| `backend-skeleton/.../application.yaml` | Full rewrite — canonical `notarist.*` namespace throughout |
| `notarist-infra/.../MinioProperties.java` | prefix: `spring.minio` → `notarist.infra.minio` |
| `notarist-infra/.../QdrantProperties.java` | prefix: `spring.qdrant` → `notarist.infra.qdrant` |
| `notarist-infra/.../PostgresProperties.java` | prefix: `spring.datasource.postgres` → `notarist.infra.datasource.postgres` |
| `notarist-web/.../DataSourceConfig.java` | @Value: `notarist.database.oracle` → `notarist.infra.datasource.oracle`; added HikariCP |
| `phase2-ingest/.../IngestModuleConfig.java` | @Value: `notarist.minio` → `notarist.infra.minio`; removed duplicate beans; removed credential defaults |
| `phase1-auth/.../AuthModuleConfig.java` | Removed `postgresDataSource` + `postgresJdbcTemplate` beans; namespace removed |
| `phase3-search/.../SearchModuleConfig.java` | Removed `postgresDataSource` + `postgresJdbcTemplate` beans |
| `phase4-assistant/.../AssistantModuleConfig.java` | Removed `@Primary ObjectMapper`; replaced with `Jackson2ObjectMapperBuilderCustomizer` |
| `notarist-web/.../SecurityConfig.java` | Wired `jwtAuthenticationFilter` via `.addFilterBefore()`; removed `permitAll` on `/actuator/metrics` |

### P1 — SSE / Timeout / DTO / Secrets

| File | Change |
|---|---|
| `phase4-assistant/.../AssistantController.java` | SseEmitter `60_000L` → `90_000L`; `X-Correlation-Id` → `X-Correlation-ID` (both endpoints) |
| `phase5b-runtime/.../OllamaRuntimeAdapter.java` | `@Value("${notarist.runtime.ollama.inference-timeout-ms:60000}")`; `@PostConstruct initHttpClient()` |
| `backend-skeleton/.../AssistantQueryRequest.java` | Deprecated (`forRemoval = true`) |
| `backend-skeleton/.../AssistantRequest.java` | Created — canonical DTO matching phase4 implementation; validation annotations |
| `generated/.env.example` | Created — all env vars with requirement classification |

### P2 — Profiles / DataSource

| File | Change |
|---|---|
| `backend-skeleton/.../application-local.yaml` | Created — local dev defaults; MinIO docker-compose credentials; debug logging |
| `backend-skeleton/.../application-dev.yaml` | Created — dev server overrides; debug logging; no credentials |
| `backend-skeleton/.../application-staging.yaml` | Created — production-like; restricted actuator; increased pools |
| `backend-skeleton/.../application-prod.yaml` | Created — hardened; liveness/readiness only; no credential fallbacks |

---

## Total Files Changed / Created: 18

| Category | Files |
|---|---|
| Modified (existing files) | 10 |
| Created (new files) | 8 |

---

## Remaining Items (Not Fixed — Deferred to Phase 6A.4)

| Item | Classification | Reason Deferred |
|---|---|---|
| Docker compose MinIO/PostgreSQL default passwords | HIGH | Docker files not in code-change scope; document in README |
| Qdrant API key empty default validation | MEDIUM | Needs warning in `QdrantProperties` if non-local profile |
| `notarist.runtime.ocr.*` consumers (ModelRegistry wiring) | VERIFY | ModelRegistry reads from `application.yaml` — verify namespace alignment |
| `notarist.security.*` consumers (JwtService) | VERIFY | JwtService reads JWT properties — verify namespace alignment |
| Static OpenAPI spec generation | P2 | Phase 6A.4 task |
| SSE event schema (Design A vs Design B) | P2 | Phase 6A.4 task |
| Wildcard imports (30+ files) | P2 | Phase 6A.4 task |
| `OcrCompletedEvent` caller scan (renamed fields) | VERIFY | Pre-6A.4 action |
