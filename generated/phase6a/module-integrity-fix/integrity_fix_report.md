# PHASE 6A.2-FIX — Master Integrity Fix Log
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P0 → P2

---

## Fix Summary

| Priority | Area | Finding | Resolution | Status |
|---|---|---|---|---|
| P0 | OCR Contract Unification | `OcrServicePort` existed in 2 locations with incompatible field definitions; `OcrWorker` and `OcrServiceAdapter` used the wrong contract | Deprecated both `OcrServicePort` stubs; migrated `OcrWorker` and `OcrServiceAdapter` to `core.OcrPort` | DONE |
| P0 | Spring Bean Collision | 6 port interfaces had 2 `@Component` implementations — `NoUniqueBeanDefinitionException` on startup | Removed `@Component` from all 6 Phase 2/3/4 stub adapters; wrote Javadoc directing to production bean | DONE |
| P1 | Record Immutability | 4 records accepted mutable `List`/`Map` in constructor | Added compact constructors with `List.copyOf()` / `Map.copyOf()` | DONE |
| P1 | Enum Persistence | 9 JPA entity enum fields stored as raw `String` — no type safety, ORDINAL fallback risk | Added `@Enumerated(EnumType.STRING)` to all 9 fields across 3 entities | DONE |
| P1 | Mapper/Repository Cascade | `.name()`/`.valueOf()` boilerplate in 4 mapper/repository files after entity enum changes | Removed all `.name()`/`.valueOf()` conversions; enum types flow end-to-end | DONE |
| P1 | Model Registry Consistency | `EmbeddingCompletedEvent.embeddingModel()` hardcoded `"bge-m3"` string | Replaced with `EmbeddingContract.EMBEDDING_MODEL` constant | DONE |
| P1 | JPQL Parameterization | `IngestionJobJpaRepository` used hardcoded `'FAILED'` string literal in JPQL | Replaced with `:failedStatus` typed parameter (`PipelineStatus.FAILED`) | DONE |
| P2 | Architecture Drift | Shadow core module, SSE design split, wildcard imports, duplicate class names | Documented in architecture drift report; deferred to Phase 6A.4 | DEFERRED |

---

## Complete File Change Log

### P0 — OCR Contract

| File | Change |
|---|---|
| `backend-skeleton/notarist-ingest/.../OcrServicePort.java` | Replaced with `@Deprecated` empty interface |
| `phase2-ingest/notarist-ingest/.../OcrServicePort.java` | Replaced with `@Deprecated` empty interface |
| `phase2-ingest/notarist-ingest/.../OcrWorker.java` | Injects `OcrPort`; uses `OcrConfig`/`OcrResult` from core |
| `phase2-ingest/notarist-ingest/.../OcrServiceAdapter.java` | Implements `OcrPort`; `@Component` removed; no-arg constructor; test-only |
| `backend-skeleton/notarist-core/.../ocr/OcrResult.java` | Added compact constructor with `List.copyOf()` |
| `backend-skeleton/notarist-ingest/.../event/OcrCompletedEvent.java` | Renamed fields `ocrWarnings`→`warnings`, `processingMs`→`durationMs`; added compact constructor |

### P0 — Bean Collision (6 stub adapters deactivated)

| File | Change |
|---|---|
| `phase3-search/.../adapter/QdrantSearchAdapter.java` | Removed `@Component`; updated Javadoc |
| `phase3-search/.../adapter/RerankerAdapter.java` | Removed `@Component`; updated Javadoc |
| `phase2-ingest/.../adapter/MinioDocumentStorageAdapter.java` | Removed `@Component`; updated Javadoc |
| `phase2-ingest/.../adapter/EmbeddingAdapter.java` | Removed `@Component`; constructor takes `String ollamaUrl` directly |
| `phase2-ingest/.../adapter/VectorIndexAdapter.java` | Removed `@Component`; constructor takes `String qdrantUrl` directly |
| `phase4-assistant/.../adapter/OllamaAdapter.java` | Removed `@Component`; updated Javadoc |

### P1 — Immutability

| File | Change |
|---|---|
| `backend-skeleton/notarist-core/.../ocr/OcrResult.java` | Compact constructor: `List.copyOf()` on `warnings` |
| `backend-skeleton/notarist-ingest/.../event/OcrCompletedEvent.java` | Compact constructor: `List.copyOf()` on `warnings` |
| `backend-skeleton/notarist-ingest/.../event/NerCompletedEvent.java` | Compact constructor: `Map.copyOf()` on `entitiesExtracted` |
| `backend-skeleton/notarist-assistant/.../response/SseCompleteEvent.java` | Compact constructor: `List.copyOf()` on `warnings` |

### P1 — Enum Persistence (entities)

| File | Change |
|---|---|
| `phase1-auth-document/notarist-document/.../DocumentLegalJpaEntity.java` | 4 fields → typed enums + `@Enumerated(STRING)`; checksum `updatable=false` |
| `phase2-ingest/notarist-ingest/.../IngestionJobJpaEntity.java` | 4 fields → typed enums + `@Enumerated(STRING)`; checksum `updatable=false` |
| `phase1-auth-document/notarist-auth/.../UserRoleJpaEntity.java` | `roleCode` → `Role` + `@Enumerated(STRING)` |

### P1 — Enum Persistence (cascade: repositories + mapper)

| File | Change |
|---|---|
| `phase2-ingest/notarist-ingest/.../IngestionJobJpaRepository.java` | Enum-typed params; JPQL `:failedStatus` parameterized |
| `phase2-ingest/notarist-ingest/.../IngestionJobRepositoryImpl.java` | Removed `.name()`/`.valueOf()`; enum args to queries |
| `phase1-auth-document/notarist-document/.../DocumentLegalJpaRepository.java` | Enum-typed params; `List<ClassificationLevel>` for allowedLevels |
| `phase1-auth-document/notarist-document/.../DocumentLegalRepositoryImpl.java` | Removed `.name()` conversions; enum args to queries |
| `phase1-auth-document/notarist-document/.../DocumentLegalMapper.java` | Removed `.name()`/`.valueOf()` in `toDomain()`/`toEntity()` |

### P1 — Model Registry

| File | Change |
|---|---|
| `backend-skeleton/notarist-ingest/.../event/EmbeddingCompletedEvent.java` | Hardcoded `"bge-m3"` → `EmbeddingContract.EMBEDDING_MODEL` |

---

## Total Files Changed: 21

| Priority | Files |
|---|---|
| P0 (OCR + Bean) | 12 |
| P1 (Immutability + Enum + Model Registry) | 9 |
| P2 | 0 (deferred) |

---

## Deferred Items

See `architecture_drift_cleanup_report.md` for all P2 items deferred to Phase 6A.4.

**Action required before Phase 6A.3:**
- Scan callers of `OcrCompletedEvent.ocrWarnings()` and `.processingMs()` — these accessor names no longer exist after the field rename.
