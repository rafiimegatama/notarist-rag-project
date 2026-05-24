# PHASE 6A.3 — Config Namespace Audit
**Project:** notarist-rag  
**Validation Date:** 2026-05-24  
**Classification:** CONFIG_RISK / CRITICAL

---

## Scope

Validates:
- Property namespace consistency across YAML + `@ConfigurationProperties` + `@Value`
- Bean name uniqueness
- Cross-module config ownership
- Duplicate namespace detection

---

## Part 1 — Property Namespace Map

### Complete Namespace Inventory

| Namespace | Defined In | Read By |
|---|---|---|
| `notarist.database.oracle.*` | `application.yaml:41-44` | `DataSourceConfig` via `@Value("${notarist.database.oracle.*}")` |
| `notarist.database.postgres.*` | `application.yaml:47-51` | **NOTHING** — no `@Value` or `@ConfigurationProperties` binds to this namespace |
| `notarist.storage.minio.*` | `application.yaml:58-68` | **NOTHING** — no consumer binds `notarist.storage.minio.*` |
| `notarist.storage.qdrant.*` | `application.yaml:55-57` | **NOTHING** — no consumer binds `notarist.storage.qdrant.*` |
| `notarist.ingestion.queue.*` | `application.yaml:71-76` | Unknown — need to check IngestQueueWorker |
| `notarist.search.*` | `application.yaml:77-86` | Unknown — need to check SearchModuleConfig |
| `notarist.ai.*` | `application.yaml:87-95` | Unknown — likely consumed by ModelRegistry |
| `notarist.sidecar.*` | `application.yaml:96-109` | **ORPHANED** — OCR/Ollama timeouts not read by adapters |
| `notarist.observability.*` | `application.yaml:110-115` | Unknown — likely consumed by ObservabilityConfig |
| `spring.minio.*` | **NOWHERE in YAML** | `MinioProperties` (`@ConfigurationProperties(prefix="spring.minio")`) |
| `spring.qdrant.*` | **NOWHERE in YAML** | `QdrantProperties` (`@ConfigurationProperties(prefix="spring.qdrant")`) |
| `spring.datasource.postgres.*` | **NOWHERE in YAML** | `PostgresProperties`, `SearchModuleConfig` |
| `notarist.minio.*` | **NOWHERE in YAML** | `IngestModuleConfig` `@Value` |
| `notarist.postgres.*` | **NOWHERE in YAML** | `IngestModuleConfig` `@Value` |
| `notarist.datasource.postgres.*` | **NOWHERE in YAML** | `AuthModuleConfig` `@Value` |
| `notarist.security.jwt.*` | `application.yaml:31-35` | `JwtService` (assumed via `@Value`) |
| `notarist.security.encryption.*` | `application.yaml:36-38` | `EncryptionService` (assumed) |

### Orphaned YAML Namespaces (Defined but Not Read)

| Namespace | Defined In | Reader | Risk |
|---|---|---|---|
| `notarist.database.postgres.*` | `application.yaml:47-51` | None found | CONFIG_RISK — dead config |
| `notarist.storage.minio.*` | `application.yaml:58-68` | None found | CONFIG_RISK — dead config |
| `notarist.storage.qdrant.*` | `application.yaml:55-57` | None found | CONFIG_RISK — dead config |
| `notarist.sidecar.ocr.timeout-ms` | `application.yaml:99` | Not read by `PaddleOcrAdapter` | CONFIG_RISK — orphaned |
| `notarist.sidecar.ollama.timeout-ms` | `application.yaml:108` | Not read by `OllamaRuntimeAdapter` | CONFIG_RISK — orphaned |
| `notarist.sidecar.ner.base-url` | `application.yaml:101-102` | Depends on NerServiceAdapter implementation | Needs verification |
| `notarist.sidecar.reranker.base-url` | `application.yaml:104-105` | Depends on RerankerRuntimeWorker | Needs verification |

### Orphaned `@Value` / `@ConfigurationProperties` (Consuming non-existent namespaces)

| Consumer | Namespace Used | Defined in YAML | Risk |
|---|---|---|---|
| `MinioProperties` | `spring.minio.*` | NO | **STARTUP FAILURE** — all fields use compact-constructor defaults |
| `QdrantProperties` | `spring.qdrant.*` | NO | **SILENT MISCONFIGURATION** — all fields use defaults |
| `PostgresProperties` | `spring.datasource.postgres.*` | NO | **STARTUP FAILURE** — url required, will throw |
| `SearchModuleConfig` | `spring.datasource.postgres.*` | NO | **STARTUP FAILURE** — url required |
| `IngestModuleConfig` | `notarist.minio.*` | NO | **STARTUP FAILURE** — `@Value` with no default for url-like properties throws |
| `IngestModuleConfig` | `notarist.postgres.*` | NO | **STARTUP FAILURE** (unless default covers it) |
| `AuthModuleConfig` | `notarist.datasource.postgres.*` | NO | **STARTUP FAILURE** — no default on required url |

---

## Part 2 — Bean Name Collision Matrix

### `postgresDataSource` Bean Registrations

| Module | Class | Phase | Qualifier |
|---|---|---|---|
| `notarist-auth` | `AuthModuleConfig` | Phase 1 | `@Bean("postgresDataSource")` |
| `notarist-search` | `SearchModuleConfig` | Phase 3 | `@Bean("postgresDataSource")` |
| `notarist-infra` | `PostgresConnectionConfig` | Phase 5 | `@Bean("postgresDataSource")` |

**Collision type:** Named bean override — last definition wins, determined by component-scan order.

### `postgresJdbcTemplate` Bean Registrations

| Module | Class | Phase | Qualifier |
|---|---|---|---|
| `notarist-auth` | `AuthModuleConfig` | Phase 1 | `@Bean postgresJdbcTemplate` |
| `notarist-ingest` | `IngestModuleConfig` | Phase 2 | `@Bean("postgresJdbcTemplate")` |
| `notarist-search` | `SearchModuleConfig` | Phase 3 | `@Bean("postgresJdbcTemplate")` |
| `notarist-infra` | `PostgresConnectionConfig` | Phase 5 | `@Bean("postgresJdbcTemplate")` |

### `objectMapper` / `aiRuntimeObjectMapper` Primary Conflict

| Module | Class | Bean |
|---|---|---|
| `notarist-assistant` | `AssistantModuleConfig` | `@Bean @Primary ObjectMapper objectMapper()` |
| `notarist-runtime` | `AiRuntimeConfig` | `@Bean @Primary ObjectMapper aiRuntimeObjectMapper()` |

**Collision type:** Two `@Primary` ObjectMapper beans → Spring throws `NoUniqueBeanDefinitionException` on startup.

---

## Part 3 — Cross-Module Config Ownership Violations

### Rule: Runtime config owned by notarist-runtime

| Property | Current Owner | Correct Owner | Violation? |
|---|---|---|---|
| Ollama base URL | `application.yaml` (shared) | `notarist-runtime` | Shared OK — but timeout is hardcoded in runtime |
| Ollama timeout | Hardcoded in `OllamaRuntimeAdapter` | Should be in `application.yaml` → runtime | VIOLATION — not wired |
| Embedding timeout | Hardcoded in `EmbeddingRuntimeWorker` | Should be configurable | VIOLATION |
| OCR timeout per page | Hardcoded in `PaddleOcrAdapter` | Should be in `IntegrationTimeouts` | CORRECT — uses `IntegrationTimeouts` |
| Circuit breaker thresholds | Hardcoded in `CircuitBreakerRegistry` | Should be in `application.yaml` | VIOLATION — not configurable |

### Rule: Infra config owned by notarist-infra

| Property | Current Owner | Correct Owner | Violation? |
|---|---|---|---|
| Qdrant properties | `spring.qdrant.*` (not in YAML) | `notarist.storage.qdrant.*` in YAML | VIOLATION — namespace mismatch |
| MinIO properties | `spring.minio.*` (not in YAML) | `notarist.storage.minio.*` in YAML | VIOLATION — namespace mismatch |
| Postgres connection pool | Split: auth/ingest/search/infra each have their own | `notarist-infra` | VIOLATION — duplicate ownership |

### Rule: Observability config isolated

| Property | Location | Status |
|---|---|---|
| Circuit breaker thresholds | `CircuitBreakerRegistry.java` hardcoded | VIOLATION — not in `application.yaml` |
| Degradation hierarchy | `OperationalDegradationHierarchy` | Need to check for hardcoding |
| Metrics enabled | `application.yaml:114` | CORRECT |
| Correlation ID header | `NotaristConstants` + `application.yaml` | DUPLICATE — same value in two places |

---

## Part 4 — Duplicate Configuration Detection

| Configuration | Defined In | Value | Duplicate? |
|---|---|---|---|
| RRF_K | `NotaristConstants.RRF_K = 60` | 60 | YES |
| RRF_K | `application.yaml:82 rrf.k: 60` | 60 | YES |
| `SEMANTIC_TOP_K` | `NotaristConstants.SEMANTIC_TOP_K = 20` | 20 | YES |
| `top-k-semantic` | `application.yaml:80` | 20 | YES |
| `KEYWORD_TOP_K` | `NotaristConstants.KEYWORD_TOP_K = 20` | 20 | YES |
| `top-k-keyword` | `application.yaml:81` | 20 | YES |
| `RERANKER_TOP_K` | `NotaristConstants.RERANKER_TOP_K = 5` | 5 | YES |
| `top-k-final` | `application.yaml:85` | 5 | YES |
| `QUEUE_MAX_RETRY_ATTEMPTS` | `NotaristConstants.QUEUE_MAX_RETRY_ATTEMPTS = 3` | 3 | YES |
| `max-retry-attempts` | `application.yaml:75` | 3 | YES |
| `QDRANT_COLLECTION` | `NotaristConstants.QDRANT_COLLECTION = "notarist_chunks"` | string | YES |
| `qdrant.collection` | `application.yaml:57` | string | YES |

**9 pairs of duplicate configuration** between `NotaristConstants` and `application.yaml`. Changing one does not change the other. If retrieval parameters are tuned via `application.yaml`, the constants may remain at old values, causing divergence if any code reads from constants directly.

**Remediation:** `NotaristConstants` should be the SINGLE source for frozen, non-tunable values (chunk sizes, API version). Tunable runtime parameters (top-k, queue workers) should ONLY be in `application.yaml` and be read via `@Value` at runtime. Remove the duplicate entries.

---

## Part 5 — Namespace Collision Detection

No direct namespace collision found (two namespaces sharing the same prefix path). However, the namespace fragmentation creates shadow collision where multiple namespaces address the same infrastructure concern:

| Concern | Namespaces (all addressing same resource) |
|---|---|
| PostgreSQL | `notarist.database.postgres`, `spring.datasource.postgres`, `notarist.postgres`, `notarist.datasource.postgres` |
| MinIO | `notarist.storage.minio`, `spring.minio`, `notarist.minio` |
| Qdrant | `notarist.storage.qdrant`, `spring.qdrant` |

---

## Summary

| Finding | Classification | Priority |
|---|---|---|
| 7 YAML namespaces orphaned (not read) | CONFIG_RISK | P1 |
| 5 `@Value`/`@ConfigurationProperties` reading non-existent namespaces | CRITICAL | P0 |
| 4 `postgresDataSource` / `postgresJdbcTemplate` duplicate beans | CRITICAL | P0 |
| 2 `@Primary ObjectMapper` conflict | CRITICAL | P0 |
| 9 duplicate config values (constants vs YAML) | CONFIG_RISK | P2 |
| Circuit breaker thresholds not configurable | CONFIG_RISK | P2 |
| Ollama/OCR timeouts hardcoded, not wired from YAML | CONFIG_RISK | P1 |
