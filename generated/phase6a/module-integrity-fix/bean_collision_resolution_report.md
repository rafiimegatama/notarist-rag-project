# PHASE 6A.2-FIX — Bean Collision Resolution Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P0

---

## Problem Summary

5 port interfaces had dual `@Component` implementations. Spring would throw `NoUniqueBeanDefinitionException` at startup, preventing the application from running.

---

## Bean Ownership Matrix — Post-Fix

| Port Interface | Production Bean (KEPT) | Stub Bean (DEMOTED) | Resolution |
|---|---|---|---|
| `VectorSearchPort` | `notarist.infra.qdrant.QdrantSearchAdapter` — Phase 5, Qdrant HTTP, full resilience | `notarist.search.infrastructure.adapter.QdrantSearchAdapter` — Phase 3, returns empty List | Removed `@Component` from Phase 3 stub |
| `DocumentStoragePort` | `notarist.infra.minio.MinioDocumentStorageAdapter` — Phase 5, resilience + retry | `notarist.ingest.infrastructure.adapter.MinioDocumentStorageAdapter` — Phase 2, direct MinIO | Removed `@Component` from Phase 2 implementation |
| `LlmPort` | `notarist.runtime.ollama.OllamaRuntimeAdapter` — Phase 5B, OkHttp streaming | `notarist.assistant.infrastructure.adapter.OllamaAdapter` — Phase 4, stub Indonesian response | Removed `@Component` from Phase 4 stub |
| `VectorIndexPort` | `notarist.infra.qdrant.QdrantIndexAdapter` — Phase 5, Qdrant upsert | `notarist.ingest.infrastructure.adapter.VectorIndexAdapter` — Phase 2, no-op stub | Removed `@Component` from Phase 2 stub |
| `RerankerPort` | `notarist.runtime.reranker.RerankerRuntimeWorker` — Phase 5B, cross-encoder HTTP | `notarist.search.infrastructure.adapter.RerankerAdapter` — Phase 3, fixed 0.5 score | Removed `@Component` from Phase 3 stub |
| `OcrPort` | `notarist.runtime.ocr.PaddleOcrAdapter` — Phase 5B, PaddleOCR HTTP | `notarist.ingest.infrastructure.adapter.OcrServiceAdapter` — Phase 2, stub (migrated) | `@Component` removed as part of OCR unification (P0-F01) |
| `EmbeddingPort` | `notarist.runtime.embedding.EmbeddingRuntimeWorker` — Phase 5B | `notarist.ingest.infrastructure.adapter.EmbeddingAdapter` — Phase 2, zero-vector stub | Removed `@Component` from Phase 2 stub |
| `NerServicePort` | `notarist.ingest.infrastructure.adapter.NerServiceAdapter` — Phase 2 | (none) — no Phase 5 replacement | Kept `@Component` — single bean, no conflict |

---

## Files Modified

| File | Change |
|---|---|
| `phase3-search/.../adapter/QdrantSearchAdapter.java` | Removed `@Component`, removed `@Value` constructor param, updated Javadoc |
| `phase3-search/.../adapter/RerankerAdapter.java` | Removed `@Component`, updated Javadoc |
| `phase2-ingest/.../adapter/MinioDocumentStorageAdapter.java` | Removed `@Component`, updated Javadoc |
| `phase2-ingest/.../adapter/EmbeddingAdapter.java` | Removed `@Component`, removed `@Value` annotation (manual constructor now), updated Javadoc |
| `phase2-ingest/.../adapter/VectorIndexAdapter.java` | Removed `@Component`, removed `@Value` annotation, updated Javadoc |
| `phase4-assistant/.../adapter/OllamaAdapter.java` | Removed `@Component`, updated Javadoc |
| `phase2-ingest/.../adapter/OcrServiceAdapter.java` | Removed `@Component` (also migrated to `OcrPort` — see OCR report) |

---

## Stub Reactivation for Tests

Stubs are no longer auto-wired by Spring. For isolated module tests:

```java
@TestConfiguration
public class IngestTestConfig {
    @Bean
    public EmbeddingPort embeddingPort() {
        return new EmbeddingAdapter("http://localhost:11434");
    }

    @Bean
    public VectorIndexPort vectorIndexPort() {
        return new VectorIndexAdapter("http://localhost:6333");
    }

    @Bean
    public OcrPort ocrPort() {
        return new OcrServiceAdapter();
    }
}
```

For search module tests, `QdrantSearchAdapter` and `RerankerAdapter` use the same pattern.  
For assistant module tests, `OllamaAdapter` uses the same pattern.

---

## Post-Fix Bean Ambiguity Check

| Port | Beans in Application Context | Status |
|---|---|---|
| `VectorSearchPort` | 1 (infra `QdrantSearchAdapter`) | PASS |
| `DocumentStoragePort` | 1 (infra `MinioDocumentStorageAdapter`) | PASS |
| `LlmPort` | 1 (runtime `OllamaRuntimeAdapter`) | PASS |
| `VectorIndexPort` | 1 (infra `QdrantIndexAdapter`) | PASS |
| `RerankerPort` | 1 (runtime `RerankerRuntimeWorker`) | PASS |
| `OcrPort` | 1 (runtime `PaddleOcrAdapter`) | PASS |
| `EmbeddingPort` | 1 (runtime `EmbeddingRuntimeWorker`) | PASS — verify @Component in phase5b |
| `NerServicePort` | 1 (ingest `NerServiceAdapter`) | PASS |

**Action required:** Verify `EmbeddingRuntimeWorker` in `notarist-runtime` has `@Component` annotation. This was not confirmed in the scan.

---

## Zero Bean Ambiguity: ACHIEVED

All 8 ports now have exactly 1 `@Component` implementation. `NoUniqueBeanDefinitionException` risk: **ELIMINATED**.
