# PHASE 6A.3 — Deployment Readiness Summary
**Project:** notarist-rag  
**Validation Date:** 2026-05-24  
**Phase:** 6A.3 — Extended Configuration & Contract Validation

---

## Deployment Readiness: NOT READY FOR STAGING OR PRODUCTION

**P0 blockers exist that prevent the application from starting correctly.**

---

## P0 Blockers — Startup Failures

These defects will cause the application to **fail to start** or **start in a silently broken configuration**.

| ID | Finding | Impact |
|---|---|---|
| BLK-01 | `MinioProperties` (`spring.minio.*`) not defined in `application.yaml` | `IllegalStateException` on startup: "spring.minio.endpoint is required" |
| BLK-02 | `PostgresProperties` / `SearchModuleConfig` (`spring.datasource.postgres.*`) not in YAML | `IllegalStateException` on startup: "spring.datasource.postgres.url required" |
| BLK-03 | `AuthModuleConfig` reads `notarist.datasource.postgres.url` — not in YAML | `@Value` injection failure — `UnsatisfiedDependencyException` |
| BLK-04 | `IngestModuleConfig` reads `notarist.postgres.url` — not in YAML | `@Value` injection failure |
| BLK-05 | Two `@Bean @Primary ObjectMapper` beans | `NoUniqueBeanDefinitionException` on startup |
| BLK-06 | `SecurityConfig.securityFilterChain()` does NOT wire `JwtAuthenticationFilter` | All authenticated endpoints return 401 for valid JWT tokens (auth is broken) |

---

## P1 Risks — Deployment-Breaking in Production

These will not prevent startup but will cause failures under real production conditions.

| ID | Finding | Impact |
|---|---|---|
| DEP-01 | Oracle DataSource has no HikariCP pool (raw `OracleDataSource`) | Connection exhaustion under load |
| DEP-02 | SSE emitter timeout (60s) ≤ Ollama inference timeout (60s) | SSE stream closes before DONE event is delivered |
| DEP-03 | 3 concurrent queue workers + 3 retries = all workers blocked during circuit-open window | No new ingestion processed during 30s circuit breaker recovery |
| DEP-04 | No profile-specific YAML files | Production uses same defaults as local dev — credential and logging risks |
| DEP-05 | Duplicate `postgresDataSource` + `postgresJdbcTemplate` beans (non-deterministic winner) | Correct HikariCP pool may not be the active one |
| DEP-06 | Qdrant API key empty default | Vector store unauthenticated in any environment where `QDRANT_API_KEY` not set |

---

## P0 Security Blockers

| ID | Finding | Impact |
|---|---|---|
| SEC-01 | MinIO credentials hardcoded `minioadmin` in `IngestModuleConfig.java` source | Source code contains credential literal |
| SEC-02 | PostgreSQL password hardcoded `notarist` in `IngestModuleConfig.java` source | Source code contains credential literal |
| SEC-03 | `JwtAuthenticationFilter` not wired in SecurityConfig | JWT authentication non-functional — all requests reach business logic unauthenticated |

---

## Complete Finding Registry

### All Findings by Priority

**P0 — Startup Blockers / Critical Security**

| ID | Report | Classification | Finding |
|---|---|---|---|
| BLK-01 | config_integrity | CRITICAL | MinIO: `spring.minio.*` not in YAML |
| BLK-02 | config_namespace | CRITICAL | PostgreSQL: `spring.datasource.postgres.*` not in YAML |
| BLK-03 | config_namespace | CRITICAL | Auth module: `notarist.datasource.postgres.*` not in YAML |
| BLK-04 | config_namespace | CRITICAL | Ingest module: `notarist.postgres.*` not in YAML |
| BLK-05 | config_integrity | CRITICAL | Duplicate `@Primary ObjectMapper` |
| OA-02 | openapi | CRITICAL | `AssistantRequest` vs `AssistantQueryRequest` mismatch |
| SEC-01 | secret | CRITICAL | MinIO credentials in source code |
| SEC-02 | secret | CRITICAL | PostgreSQL password in source code |
| SEC-03 | secret | CRITICAL | `JwtAuthenticationFilter` not wired in `SecurityConfig` |

**P1 — Deployment Risks / High Security**

| ID | Report | Classification | Finding |
|---|---|---|---|
| DEP-01 | config_integrity | DEPLOYMENT_RISK | Oracle DataSource missing HikariCP pool |
| DEP-02 | timeout | DEPLOYMENT_RISK | SSE timeout ≤ Ollama inference timeout |
| DEP-03 | timeout | DEPLOYMENT_RISK | Circuit breaker + retry timing incompatible |
| DEP-04 | profile | DEPLOYMENT_RISK | No profile-specific YAML files (local/dev/staging/prod) |
| DEP-05 | config_namespace | CRITICAL | Duplicate bean names non-deterministic |
| DEP-06 | secret | MEDIUM | Qdrant API key empty default |
| SEC-04 | secret | HIGH | Docker compose MinIO password default |
| SEC-05 | secret | HIGH | Docker compose PostgreSQL password default |
| SEC-06 | secret | HIGH | MinIO weak defaults in application.yaml |
| SEC-07 | secret | MEDIUM | Actuator metrics publicly accessible |
| TMO-01 | timeout | CONFIG_RISK | Ollama YAML timeout orphaned (not wired to code) |
| TMO-02 | timeout | CONFIG_RISK | OCR YAML timeout orphaned |
| OA-06 | openapi | LATENT_RISK | Correlation ID header case mismatch |
| OA-07 | openapi | CONFIG_RISK | SSE event schema incomplete (Design A active) |
| HCP-01 | profile | CONFIG_RISK | 3 different HikariCP connection timeouts for same DB |

**P2 — Latent Risks / Deferred**

| ID | Report | Classification | Finding |
|---|---|---|---|
| TMO-03 | timeout | LATENT | Embedding batch timeout tight on CPU |
| TMO-04 | timeout | LATENT | Scheduler shutdown vs OCR duration |
| NS-01 | config_namespace | CONFIG_RISK | 9 duplicate values: constants vs YAML |
| NS-02 | config_namespace | CONFIG_RISK | Circuit breaker thresholds not configurable |
| OA-01 | openapi | CONFIG_RISK | No static OpenAPI spec file |
| OA-03 | openapi | LATENT_RISK | SearchResponse skeleton vs impl drift |
| OA-04 | openapi | LATENT_RISK | IngestionJobStatusResponse duplication |
| OA-05 | openapi | LATENT_RISK | UploadUrlResponse duplication |
| OA-08 | openapi | LATENT_RISK | Enum fields as String in AssistantRequest |
| OA-09 | openapi | LATENT_RISK | ApiResponse class duplication |

---

## Finding Counts by Classification

| Classification | P0 | P1 | P2 | Total |
|---|---|---|---|---|
| CRITICAL | 7 | 1 | 0 | 8 |
| SECURITY_RISK | 2 | 5 | 0 | 7 |
| DEPLOYMENT_RISK | 1 | 3 | 0 | 4 |
| CONFIG_RISK | 0 | 3 | 4 | 7 |
| LATENT_RISK | 0 | 1 | 5 | 6 |
| **Total** | **10** | **13** | **9** | **32** |

---

## Positive Findings (Working Correctly)

| Area | Assessment |
|---|---|
| Bean collision from Phase 6A.2-FIX | RESOLVED — zero `@Component` duplicates |
| OCR contract unification | RESOLVED — single `core.OcrPort` |
| Enum persistence | RESOLVED — `@Enumerated(STRING)` on all JPA enum fields |
| Record immutability | RESOLVED — defensive copies in all 4 affected records |
| Model registry consistency | RESOLVED — `EmbeddingContract.EMBEDDING_MODEL` used |
| `IntegrationTimeouts` constant alignment | PASS — MinIO/Qdrant/OCR/Embedding constants consistent with `*Properties` defaults |
| No hardcoded SQL injection vectors | PASS — all JPQL uses typed parameters |
| Correlation ID generation | PASS — `CorrelationIdFilter` generates when absent |
| VPD context cleanup | PASS — `VpdContextHolder.clear()` in JWT filter finally block |
| MDC cleanup in filters | PASS — `MDC.remove()` in finally blocks |
| OkHttp streaming for Ollama | CORRECT — does not use RestTemplate (incompatible with NDJSON) |
| Circuit breaker metrics via Micrometer | PASS — gauge per integration |
| No infinite retry | PASS — all retry counts bounded |
| `APP_ENCRYPTION_KEY` no default | PASS — fails fast if not set |

---

## Remediation Priority Order

### Immediate (before any test deployment)

1. Fix property namespace fragmentation — align all `@ConfigurationProperties` prefixes with `application.yaml` namespaces
2. Wire `JwtAuthenticationFilter` in `SecurityConfig`
3. Remove duplicate `@Primary ObjectMapper` (keep `AiRuntimeConfig` version)
4. Remove hardcoded credential defaults from `IngestModuleConfig`
5. Remove duplicate bean definitions for `postgresDataSource` and `postgresJdbcTemplate`

### Before staging deployment

6. Create `application-staging.yaml` and `application-prod.yaml` with credential-free config
7. Fix Oracle DataSource to use HikariCP
8. Fix SSE emitter timeout to exceed Ollama inference timeout
9. Wire Ollama timeout from `application.yaml` into `OllamaRuntimeAdapter`
10. Remove weak MinIO/PostgreSQL defaults from `application.yaml`

### Before production deployment

11. Restrict actuator endpoints (remove `/actuator/metrics` from public access)
12. Reconcile `AssistantRequest` vs `AssistantQueryRequest` DTO mismatch
13. Standardize correlation ID header casing
14. Generate and commit static OpenAPI spec
15. Complete SSE Design B typed event records

---

## Phase 6A.3 Complete

**STOP — Awaiting approval before continuing to Phase 6A.4.**
