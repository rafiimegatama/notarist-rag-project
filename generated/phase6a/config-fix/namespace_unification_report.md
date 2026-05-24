# PHASE 6A.3-FIX — Namespace Unification Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P0

---

## Problem

5 incompatible property namespaces were in use simultaneously. `@ConfigurationProperties` prefixes did not match `application.yaml` key paths. Environment variables could not reliably configure the application.

---

## Canonical Namespace Strategy

```
notarist:
  security:      JWT, encryption
  infra:
    datasource:
      oracle:    Oracle 19C (primary JPA datasource)
      postgres:  PostgreSQL (BM25 search, session tokens)
    minio:       Object storage
    qdrant:      Vector store
  runtime:
    ocr:         PaddleOCR sidecar
    embedding:   bge-m3 embedding sidecar
    reranker:    Cross-encoder reranker sidecar
    ner:         NER sidecar
    ollama:      Ollama LLM
  ingest:
    queue:       Pipeline queue configuration
    scheduler:   Ingest scheduler thread pool
  search:        Retrieval parameters (top-k, RRF-k, etc.)
  assistant:     Hallucination guard, LLM parameters
  observability: Headers, metrics flags
```

`spring.datasource.*` is reserved for Spring's JPA auto-configuration of the primary (Oracle) datasource when Hibernate/Liquibase auto-config is used. It is NOT used for custom application properties.

---

## Migration Map

| Old Namespace | Owner | New Namespace | Fixed In |
|---|---|---|---|
| `spring.minio.*` | `MinioProperties` | `notarist.infra.minio.*` | `MinioProperties.java` |
| `spring.qdrant.*` | `QdrantProperties` | `notarist.infra.qdrant.*` | `QdrantProperties.java` |
| `spring.datasource.postgres.*` | `PostgresProperties`, `SearchModuleConfig` | `notarist.infra.datasource.postgres.*` | `PostgresProperties.java` |
| `notarist.minio.*` | `IngestModuleConfig` | `notarist.infra.minio.*` | `IngestModuleConfig.java` |
| `notarist.postgres.*` | `IngestModuleConfig` | `notarist.infra.datasource.postgres.*` | `IngestModuleConfig.java` |
| `notarist.datasource.postgres.*` | `AuthModuleConfig` | Removed (bean removed) | `AuthModuleConfig.java` |
| `notarist.database.oracle.*` | `DataSourceConfig` | `notarist.infra.datasource.oracle.*` | `DataSourceConfig.java` |
| `notarist.database.postgres.*` | `application.yaml` only | Renamed to `notarist.infra.datasource.postgres.*` | `application.yaml` |
| `notarist.storage.minio.*` | `application.yaml` only | Renamed to `notarist.infra.minio.*` | `application.yaml` |
| `notarist.storage.qdrant.*` | `application.yaml` only | Renamed to `notarist.infra.qdrant.*` | `application.yaml` |
| `notarist.sidecar.*` | `application.yaml` only | Renamed to `notarist.runtime.*` | `application.yaml` |
| `notarist.ai.*` | `application.yaml` only | Merged into `notarist.runtime.ollama.*` and `notarist.assistant.*` | `application.yaml` |

---

## Orphaned Properties Resolved

| Property | Was Orphaned Because | Resolution |
|---|---|---|
| `notarist.sidecar.ollama.timeout-ms` | Not read by `OllamaRuntimeAdapter` | Renamed to `notarist.runtime.ollama.inference-timeout-ms`; wired via `@Value` in `OllamaRuntimeAdapter` |
| `notarist.sidecar.ocr.timeout-ms` (120s) | Not read by `PaddleOcrAdapter` (uses hardcoded 30s) | Renamed to `notarist.runtime.ocr.timeout-ms` with correct 30s default; documenting per-page intent |
| `notarist.ai.ollama.model` | Not confirmed to be wired | Preserved as `notarist.runtime.ollama.model` |
| `notarist.ai.ollama.stream` | Not confirmed to be wired | Preserved as `notarist.runtime.ollama.stream` |
| `notarist.database.postgres.*` | Not read by any `@ConfigurationProperties` | Merged into `notarist.infra.datasource.postgres.*` |

---

## Files Modified

| File | Change |
|---|---|
| `backend-skeleton/notarist-web/.../application.yaml` | Full rewrite: canonical `notarist.*` namespace throughout |
| `notarist-infra/.../MinioProperties.java` | `@ConfigurationProperties(prefix = "notarist.infra.minio")` |
| `notarist-infra/.../QdrantProperties.java` | `@ConfigurationProperties(prefix = "notarist.infra.qdrant")` |
| `notarist-infra/.../PostgresProperties.java` | `@ConfigurationProperties(prefix = "notarist.infra.datasource.postgres")` |
| `notarist-web/.../DataSourceConfig.java` | `@Value` updated to `notarist.infra.datasource.oracle.*` |
| `phase2-ingest/.../IngestModuleConfig.java` | `@Value` updated to `notarist.infra.minio.*` and `notarist.infra.datasource.postgres.*` |
| `phase5b-runtime/.../OllamaRuntimeAdapter.java` | `@Value("${notarist.runtime.ollama.inference-timeout-ms:60000}")` wired |

---

## Post-Fix Namespace Consistency

| Namespace | In YAML | In Code | Status |
|---|---|---|---|
| `notarist.infra.minio.*` | YES | `MinioProperties`, `IngestModuleConfig` | PASS |
| `notarist.infra.qdrant.*` | YES | `QdrantProperties` | PASS |
| `notarist.infra.datasource.oracle.*` | YES | `DataSourceConfig` | PASS |
| `notarist.infra.datasource.postgres.*` | YES | `PostgresProperties` | PASS |
| `notarist.runtime.ollama.*` | YES | `OllamaRuntimeAdapter` | PASS |
| `notarist.runtime.ocr.*` | YES | (consumed by ModelRegistry — verify) | VERIFY |
| `notarist.ingest.queue.*` | YES | (consumed by QueueWorker — verify) | VERIFY |
| `notarist.security.*` | YES | (consumed by JwtService — verify) | VERIFY |
| No `spring.minio.*` or `spring.qdrant.*` | Removed | No orphaned consumers | PASS |
