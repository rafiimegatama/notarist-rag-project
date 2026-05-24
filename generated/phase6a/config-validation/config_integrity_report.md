# PHASE 6A.3 — Config Integrity Report
**Project:** notarist-rag  
**Validation Date:** 2026-05-24  
**Phase:** 6A.3 — Extended Configuration & Contract Validation

---

## Executive Summary

7 critical configuration defects found across property namespace, bean wiring, and timeout alignment. 2 items are P0 (startup blockers). 5 are P1 (deployment risks). No feature changes required — all fixes are configuration and wiring changes.

---

## Finding 1 — PROPERTY NAMESPACE FRAGMENTATION (P0 — CRITICAL)

### MinIO: 3 Incompatible Namespaces

| Consumer | Namespace Used | Source |
|---|---|---|
| `MinioProperties` (`@ConfigurationProperties`) | `spring.minio.*` | `MinioClientConfig.java` |
| `IngestModuleConfig` (`@Value`) | `notarist.minio.*` | `IngestModuleConfig.java:22-24` |
| `application.yaml` definition | `notarist.storage.minio.*` | `application.yaml:58-68` |

**Impact:** `MinioProperties` will fail to bind at startup — `spring.minio.*` does not exist in `application.yaml`. The phase5 `MinioClientConfig` will throw `IllegalStateException("spring.minio.endpoint is required")` on startup.

### PostgreSQL: 4 Incompatible Namespaces

| Consumer | Namespace Used | Source |
|---|---|---|
| `PostgresProperties` (`@ConfigurationProperties`) | `spring.datasource.postgres.*` | `PostgresConnectionConfig.java` |
| `SearchModuleConfig` | `spring.datasource.postgres.*` | `SearchModuleConfig.java:26` |
| `IngestModuleConfig` (`@Value`) | `notarist.postgres.*` | `IngestModuleConfig.java:33-36` |
| `AuthModuleConfig` (`@Value`) | `notarist.datasource.postgres.*` | `AuthModuleConfig.java:25-28` |
| `application.yaml` definition | `notarist.database.postgres.*` | `application.yaml:47-51` |

**Impact:** None of the `@Value`-based injections in `IngestModuleConfig` or `AuthModuleConfig` will resolve — `notarist.postgres.*` and `notarist.datasource.postgres.*` are not defined in `application.yaml`. These beans will fail with `@Value` injection errors at startup.

### Qdrant: 2 Incompatible Namespaces

| Consumer | Namespace Used | Source |
|---|---|---|
| `QdrantProperties` (`@ConfigurationProperties`) | `spring.qdrant.*` | `QdrantClientConfig.java` |
| `application.yaml` definition | `notarist.storage.qdrant.*` | `application.yaml:55-57` |

**Impact:** `QdrantProperties` will use all defaults (localhost:6333, no API key) regardless of what is set in `application.yaml`.

### Remediation

Define a **single canonical namespace** for each integration and update all `@ConfigurationProperties` prefixes to match:

```
notarist.database.postgres.*    → canonical PostgreSQL namespace
notarist.storage.minio.*        → canonical MinIO namespace
notarist.storage.qdrant.*       → canonical Qdrant namespace
```

Update all `@ConfigurationProperties(prefix = ...)` and `@Value("${...}")` references to the canonical namespace.

---

## Finding 2 — DUPLICATE BEAN NAMES ACROSS MODULES (P0 — CRITICAL)

### `postgresDataSource` registered in 3 modules

| Module | Class | Bean Name |
|---|---|---|
| `notarist-auth` (Phase 1) | `AuthModuleConfig` | `@Bean("postgresDataSource")` |
| `notarist-search` (Phase 3) | `SearchModuleConfig` | `@Bean("postgresDataSource")` |
| `notarist-infra` (Phase 5) | `PostgresConnectionConfig` | `@Bean("postgresDataSource")` |

### `postgresJdbcTemplate` registered in 4 modules

| Module | Class | Bean Name |
|---|---|---|
| `notarist-auth` (Phase 1) | `AuthModuleConfig` | `@Bean postgresJdbcTemplate` |
| `notarist-ingest` (Phase 2) | `IngestModuleConfig` | `@Bean("postgresJdbcTemplate")` |
| `notarist-search` (Phase 3) | `SearchModuleConfig` | `@Bean("postgresJdbcTemplate")` |
| `notarist-infra` (Phase 5) | `PostgresConnectionConfig` | `@Bean("postgresJdbcTemplate")` |

**Impact:** Spring's default behavior for duplicate bean names is "last one wins." The winning bean depends on component scan order — non-deterministic across JVM runs. In Phase 5+, the correct infra bean should win, but there is no guarantee.

### Remediation

- Phase 5 `PostgresConnectionConfig` is the authoritative PostgreSQL configuration.
- Remove `@Bean("postgresDataSource")` and `@Bean("postgresJdbcTemplate")` from `AuthModuleConfig`, `IngestModuleConfig`, and `SearchModuleConfig`.
- These modules should inject `@Qualifier("postgresDataSource")` and `@Qualifier("postgresJdbcTemplate")` rather than declaring new beans.

---

## Finding 3 — DUPLICATE `@Primary` ObjectMapper (P0 — CRITICAL)

| Module | Class | Bean Declaration |
|---|---|---|
| `notarist-assistant` (Phase 4) | `AssistantModuleConfig` | `@Bean @Primary ObjectMapper objectMapper()` |
| `notarist-runtime` (Phase 5B) | `AiRuntimeConfig` | `@Bean @Primary ObjectMapper aiRuntimeObjectMapper()` |

**Impact:** Two `@Primary` ObjectMapper beans → `NoUniqueBeanDefinitionException` at startup when any component injects `ObjectMapper` without a qualifier.

### Remediation

- `AiRuntimeConfig.aiRuntimeObjectMapper()` should be the single `@Primary` (Phase 5B is authoritative).
- Remove `@Primary` from `AssistantModuleConfig.objectMapper()`. Since both are configured identically (JavaTimeModule, no timestamp), this is safe.

---

## Finding 4 — ORACLE DATASOURCE SKELETON INCOMPLETE (P1 — DEPLOYMENT_RISK)

`DataSourceConfig` (skeleton, `notarist-web`) declares `@Bean @Primary DataSource oracleDataSource()` using raw `OracleDataSource` without HikariCP pooling:

```java
// DataSourceConfig.java:30-36
OracleDataSource ds = new OracleDataSource();
ds.setURL(oracleUrl);
ds.setUser(oracleUsername);
ds.setPassword(oraclePassword);
// TODO (STEP 8B): configure HikariCP pool wrapper, VPD context injection
return ds;
```

**Impact:** No connection pool for Oracle. Every JPA operation opens a new physical connection. Under load, Oracle will reach max connections and reject new requests.

### Remediation

Replace with HikariCP wrapping `OracleDataSource`, configured via `notarist.database.oracle.*` properties:
```java
HikariConfig config = new HikariConfig();
config.setDataSourceClassName("oracle.jdbc.pool.OracleDataSource");
config.addDataSourceProperty("URL", oracleUrl);
config.setUsername(oracleUsername);
config.setPassword(oraclePassword);
config.setMaximumPoolSize(oraclePoolMax);
config.setMinimumIdle(oraclePoolMin);
```

---

## Finding 5 — OLLAMA TIMEOUT: YAML vs CODE MISMATCH (P1 — CONFIG_RISK)

| Location | Value | Source |
|---|---|---|
| `application.yaml` | `ollama.timeout-ms: 120000` (120s) | `application.yaml:108` |
| `OllamaRuntimeAdapter` | `OLLAMA_INFERENCE_TIMEOUT_MS = 60_000` (60s) | `OllamaRuntimeAdapter.java:42` |
| `IntegrationTimeouts` | `OLLAMA_INFERENCE_MS = 60_000` (60s) | `IntegrationTimeouts.java:36` |

**Impact:** The YAML value `${OLLAMA_TIMEOUT_MS:120000}` is **never read** by `OllamaRuntimeAdapter`. The actual timeout is the hardcoded 60s in the class. The YAML property is an orphan — setting it has no effect. Operators will believe they can tune this via environment variable, but cannot.

### Remediation

Wire `${notarist.sidecar.ollama.timeout-ms:60000}` into `OllamaRuntimeAdapter` via `@Value` and use it in `OkHttpClient.Builder().readTimeout()`.

---

## Finding 6 — OCR YAML TIMEOUT ORPHANED (P1 — CONFIG_RISK)

| Location | Value | Source |
|---|---|---|
| `application.yaml` | `ocr.timeout-ms: 120000` (120s) | `application.yaml:99` |
| `PaddleOcrAdapter` | `30_000L` hardcoded | `PaddleOcrAdapter.java:78` |
| `IntegrationTimeouts` | `OCR_PAGE_TIMEOUT_MS = 30_000` (30s) | `IntegrationTimeouts.java:39` |

**Impact:** Same pattern as Ollama. The YAML `${OCR_TIMEOUT_MS:120000}` is not read by `PaddleOcrAdapter`. Hardcoded 30s from `IntegrationTimeouts` is used. The YAML value is an orphan.

The 30s per-page timeout in `IntegrationTimeouts` is intentional and correct (per the Javadoc rationale). The YAML 120s appears to be a legacy holdover from when timeout was total-document rather than per-page.

### Remediation

Remove `notarist.sidecar.ocr.timeout-ms` from `application.yaml` or document it clearly as unused. `PaddleOcrAdapter` should reference `IntegrationTimeouts.OCR_PAGE_TIMEOUT_MS` (which it does indirectly).

---

## Finding 7 — SSE EMITTER TIMEOUT vs OLLAMA TIMEOUT (P1 — DEPLOYMENT_RISK)

| Component | Timeout | Source |
|---|---|---|
| `SseEmitter` in `AssistantController` | 60,000 ms (60s) | `AssistantController.java:81` |
| `OllamaRuntimeAdapter` OkHttp read timeout | 60,000 ms (60s) | `OllamaRuntimeAdapter.java:42` |

**Impact:** Both timeouts are 60s. If Ollama takes exactly 60s, the SSE connection expires BEFORE the inference completes and the final tokens/DONE event are sent. Under production load (7B Indonesian legal model, long context), Ollama inference can easily exceed 60s on CPU-only deployment.

### Remediation

SSE emitter timeout should be `> Ollama inference timeout`:
```java
// SseEmitter should outlive inference
new SseEmitter(90_000L)   // if Ollama timeout = 60s
// or
new SseEmitter(OLLAMA_INFERENCE_TIMEOUT_MS + 30_000L)
```

---

## Summary Table

| # | Finding | Classification | Priority | Status |
|---|---|---|---|---|
| 1 | Property namespace fragmentation (MinIO/Postgres/Qdrant) | CRITICAL | P0 | NOT FIXED |
| 2 | Duplicate bean names (`postgresDataSource`, `postgresJdbcTemplate`) | CRITICAL | P0 | NOT FIXED |
| 3 | Duplicate `@Primary` ObjectMapper | CRITICAL | P0 | NOT FIXED |
| 4 | Oracle DataSource has no HikariCP pool | DEPLOYMENT_RISK | P1 | NOT FIXED |
| 5 | Ollama YAML timeout orphaned (60s code vs 120s YAML) | CONFIG_RISK | P1 | NOT FIXED |
| 6 | OCR YAML timeout orphaned (30s code vs 120s YAML) | CONFIG_RISK | P1 | NOT FIXED |
| 7 | SSE emitter timeout ≤ Ollama inference timeout | DEPLOYMENT_RISK | P1 | NOT FIXED |
