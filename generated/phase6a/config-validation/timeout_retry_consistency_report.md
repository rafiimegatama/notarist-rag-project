# PHASE 6A.3 — Timeout & Retry Consistency Report
**Project:** notarist-rag  
**Validation Date:** 2026-05-24  
**Classification:** CONFIG_RISK / DEPLOYMENT_RISK

---

## Timeout Inventory

### Declared Timeouts — All Sources

| Integration | Source | Value | Type |
|---|---|---|---|
| MinIO connect | `IntegrationTimeouts.MINIO_CONNECT_MS` | 5,000 ms | Connection |
| MinIO read | `IntegrationTimeouts.MINIO_READ_MS` | 30,000 ms | Read |
| MinIO write | `IntegrationTimeouts.MINIO_WRITE_MS` | 120,000 ms | Write |
| MinIO connect | `MinioProperties` default | 5,000 ms | Connection |
| MinIO read | `MinioProperties` default | 30,000 ms | Read |
| MinIO write | `MinioProperties` default | 120,000 ms | Write |
| Qdrant connect | `IntegrationTimeouts.QDRANT_CONNECT_MS` | 3,000 ms | Connection |
| Qdrant search | `IntegrationTimeouts.QDRANT_SEARCH_MS` | 5,000 ms | Read |
| Qdrant upsert | `IntegrationTimeouts.QDRANT_UPSERT_MS` | 10,000 ms | Write |
| Qdrant connect | `QdrantProperties` default | 3,000 ms | Connection |
| Qdrant search | `QdrantProperties` default | 5,000 ms | Read |
| Qdrant upsert | `QdrantProperties` default | 10,000 ms | Write |
| PostgreSQL query | `IntegrationTimeouts.POSTGRES_QUERY_S` | 30 s | Query |
| PostgreSQL query | `postgresJdbcTemplate.setQueryTimeout(30)` | 30 s | Query |
| Ollama inference | `OllamaRuntimeAdapter.OLLAMA_INFERENCE_TIMEOUT_MS` | 60,000 ms | HTTP read |
| Ollama connect | `OllamaRuntimeAdapter` OkHttp | 5,000 ms | Connection |
| Ollama stream first-token | `IntegrationTimeouts.OLLAMA_STREAM_FIRST_MS` | 10,000 ms | Declared but unused |
| Ollama inference | `application.yaml` `ollama.timeout-ms` | 120,000 ms | **ORPHANED** |
| OCR per-page | `PaddleOcrAdapter` hardcoded | 30,000 ms | `submitWithTimeout` |
| OCR per-page | `IntegrationTimeouts.OCR_PAGE_TIMEOUT_MS` | 30,000 ms | Per-page |
| OCR total | `application.yaml` `ocr.timeout-ms` | 120,000 ms | **ORPHANED** |
| Embedding batch | `EmbeddingRuntimeWorker.EMBEDDING_TIMEOUT_MS` | 15,000 ms | `submitWithTimeout` |
| Embedding batch | `IntegrationTimeouts.EMBEDDING_BATCH_TIMEOUT_MS` | 15,000 ms | Per-batch |
| SSE emitter | `AssistantController` hardcoded | 60,000 ms | SseEmitter TTL |
| Auth REST Template | `AiRuntimeConfig.aiRuntimeRestTemplate` | connect:5s, read:30s | HTTP |
| Observability REST | `ResilienceConfig.observabilityRestTemplate` | connect:5s, read:10s | HTTP |
| Ingest scheduler shutdown | `IngestModuleConfig.ingestSchedulerTaskScheduler` | 30 s | Graceful shutdown |
| Auth Postgres pool connect | `AuthModuleConfig` hardcoded | 20,000 ms | Pool connection |
| Infra Postgres pool connect | `PostgresConnectionConfig` via props | 5,000 ms | Pool connection |

---

## Consistency Checks

### CHECK 1 — Ollama: SSE Emitter vs Inference Timeout

**FAIL**

| Component | Timeout |
|---|---|
| `SseEmitter` (`AssistantController`) | 60,000 ms |
| `OllamaRuntimeAdapter` OkHttp read | 60,000 ms |

The SSE connection and Ollama inference timeout are EQUAL. If Ollama takes exactly the full timeout, the SSE emitter expires simultaneously with the HTTP read. In practice, for any inference approaching the timeout limit, the SSE DONE event will not be delivered because the emitter closes at the same moment.

**Rule violated:** SSE timeout MUST be > inference timeout to allow DONE/ERROR events to be flushed.

**Remediation:** Set `SseEmitter` to `90_000L` (90s) while keeping Ollama at 60s, providing 30s of headroom for event delivery.

---

### CHECK 2 — Ollama: YAML vs Code Timeout

**FAIL — ORPHANED CONFIG**

`application.yaml` declares `notarist.sidecar.ollama.timeout-ms: ${OLLAMA_TIMEOUT_MS:120000}` = 120s.  
`OllamaRuntimeAdapter` hardcodes `OLLAMA_INFERENCE_TIMEOUT_MS = 60_000` = 60s.

The YAML value is never injected into `OllamaRuntimeAdapter`. Setting `OLLAMA_TIMEOUT_MS=180000` in the environment has no effect. This is a configuration transparency failure — operators cannot tune Ollama timeout through documented configuration.

---

### CHECK 3 — OCR: YAML vs Code Timeout

**FAIL — ORPHANED CONFIG (different intent)**

`application.yaml` declares `notarist.sidecar.ocr.timeout-ms: ${OCR_TIMEOUT_MS:120000}` = 120s.  
`PaddleOcrAdapter` hardcodes `30_000L` from `IntegrationTimeouts.OCR_PAGE_TIMEOUT_MS` = 30s per page.

The OCR YAML timeout appears to be a legacy "total document" timeout concept, while the code implements a per-page timeout. The architectural decision (per-page vs per-document) is correct and intentional, but the YAML property is misleading.

---

### CHECK 4 — `AiRuntimeConfig` RestTemplate Read Timeout vs Consumers

**PASS (with caveat)**

`AiRuntimeConfig.aiRuntimeRestTemplate`: `readTimeout(30s)`.

Consumers of this RestTemplate:
- `PaddleOcrAdapter` — uses `TimeoutCancellationOrchestrator(30s)` as outer timeout → RestTemplate 30s is consistent
- `EmbeddingRuntimeWorker` — uses `TimeoutCancellationOrchestrator(15s)` as outer timeout → RestTemplate 30s is a safe outer bound

The inner `TimeoutCancellationOrchestrator` timeout fires first (15s or 30s). The RestTemplate read timeout (30s) serves as a safety net. For embedding the inner timeout (15s) is shorter than the RestTemplate timeout (30s) — this is correct.

**Caveat:** `OllamaRuntimeAdapter` uses OkHttp directly (NOT this RestTemplate) — correct, as NDJSON streaming requires direct HTTP.

---

### CHECK 5 — Circuit Breaker Cooldown vs Retry Timing

**LATENT_RISK**

Circuit breaker parameters (hardcoded in `CircuitBreakerRegistry`):
- `OPEN_THRESHOLD = 3` consecutive failures → OPEN
- `HALF_OPEN_AFTER_MS = 30_000` (30s cooldown)
- `PROBE_SUCCESSES = 2`

Queue retry configuration (`application.yaml`):
- `max-retry-attempts: ${QUEUE_MAX_RETRY_ATTEMPTS:3}`
- `poll-interval-ms: ${QUEUE_POLL_INTERVAL_MS:2000}` (2s)

**Scenario:** Qdrant circuit opens after 3 failures. Retry #1 (2s) → OPEN. Retry #2 (4s) → OPEN. Retry #3 (6s) → OPEN. After 30s cooldown → HALF_OPEN. But the job has already exhausted its 3 retries and moved to FAILED/DLQ.

With a 30s circuit breaker cooldown and 2s poll interval, a job will exhaust all retries before the circuit re-closes. This means no retried job will ever succeed when the circuit breaker is involved — it will always exhaust retries during the OPEN window.

**Remediation:** Either:
- Extend `max-retry-attempts` to ≥ `(HALF_OPEN_AFTER_MS / poll_interval) + 2` = 17+ retries; OR
- Add exponential backoff to retries so that later retries occur after the circuit re-closes; OR
- Separate circuit-breaker-induced failures from genuine failures in retry counting.

---

### CHECK 6 — Embedding Timeout vs Batch Size

**PASS**

`EmbeddingRuntimeWorker.EMBEDDING_TIMEOUT_MS = 15_000` (15s) per batch.  
The bge-m3 model on CPU processes ~100 tokens/s. A typical notarist document chunk (800 tokens) = ~8s. A batch of 2-3 chunks = 16-24s → may EXCEED the 15s timeout on CPU.

This is a latent risk rather than a definite failure (GPU deployment would be fine). The batch timeout should be configurable based on hardware profile.

---

### CHECK 7 — Ingest Scheduler Shutdown vs OCR Duration

**LATENT_RISK**

`ingestSchedulerTaskScheduler.awaitTerminationSeconds = 30`.  
`IntegrationTimeouts.OCR_PAGE_TIMEOUT_MS = 30,000` (30s per page).

A multi-page document with 3+ pages could be mid-OCR when shutdown signal arrives. The scheduler will wait 30s, which covers exactly 1 page. If OCR is on page 2+, the job will be interrupted mid-processing and left in an inconsistent state.

---

## Retry Configuration Audit

| Retry Setting | Value | Source |
|---|---|---|
| Queue max retries | `${QUEUE_MAX_RETRY_ATTEMPTS:3}` | `application.yaml:75` |
| MinIO max retries | 3 (default in `MinioProperties`) | Code default |
| Qdrant max retries | 3 (default in `QdrantProperties`) | Code default |
| Auth HikariCP connection timeout | 20s | `AuthModuleConfig.java:35` |
| Infra HikariCP connection timeout | 5s | `PostgresConnectionConfig` via props |
| No infinite retry detected | — | PASS |

**No infinite retry found** — all retry counts are bounded.

---

## Summary Table

| Check | Validation | Result | Priority |
|---|---|---|---|
| SSE timeout > inference timeout | Emitter 60s = OkHttp 60s | **FAIL** | P1 |
| Ollama YAML timeout wired to code | YAML 120s not wired | **FAIL** | P1 |
| OCR YAML timeout intent | YAML 120s orphaned | **FAIL** | P2 |
| RestTemplate timeout vs consumers | Consistent (30s ≥ all inner timeouts) | PASS | — |
| Circuit breaker vs retry timing | 3 retries exhausted before 30s cooldown | **LATENT** | P1 |
| Embedding batch timeout on CPU | 15s may be tight for 3+ chunks | **LATENT** | P2 |
| Scheduler shutdown vs OCR duration | 30s = 1 page coverage only | **LATENT** | P2 |
| No infinite retry | All retries bounded | PASS | — |
| Retry timeout < total request timeout | All sub-timeouts < HTTP timeout | PASS | — |
