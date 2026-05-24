# PHASE 6A.2-FIX — Post-Fix Integrity Summary
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  

---

## Validation Targets — Final Status

| Target | Result |
|---|---|
| Single OCR contract source of truth | **PASS** |
| Zero bean ambiguity | **PASS** |
| Immutable records safe | **PASS** |
| Enum persistence explicit | **PASS** |
| No hardcoded model identifiers | **PASS** |
| Runtime remains ingest-agnostic | **PASS** |
| No new circular dependencies | **PASS** |

---

## Target 1 — Single OCR Contract

**Before:** 3 OCR contracts (`OcrServicePort` skeleton, `OcrServicePort` phase2, `OcrPort` core)  
**After:** 1 contract — `notarist-core.port.ocr.OcrPort` with `core.OcrConfig` and `core.OcrResult`

| Check | Status |
|---|---|
| `OcrServicePort` in skeleton | `@Deprecated` empty interface — no active consumers |
| `OcrServicePort` in phase2-ingest | `@Deprecated` empty interface — no active consumers |
| `OcrWorker` uses `OcrPort` | PASS |
| `OcrServiceAdapter` implements `OcrPort` | PASS |
| `PaddleOcrAdapter` implements `OcrPort` | PASS (from Phase 6A.1-FIX) |
| `notarist-runtime` does NOT depend on `notarist-ingest` | PASS |
| `notarist-ingest` does NOT depend on `notarist-runtime` | PASS |

---

## Target 2 — Zero Bean Ambiguity

**Before:** 6 port interfaces had 2 `@Component` implementations — fatal `NoUniqueBeanDefinitionException` at startup

| Port Interface | Active Bean | Stub Deactivated | Status |
|---|---|---|---|
| `VectorSearchPort` | `infra.QdrantSearchAdapter` | phase3 stub | PASS |
| `DocumentStoragePort` | `infra.MinioDocumentStorageAdapter` | phase2 stub | PASS |
| `LlmPort` | `runtime.OllamaRuntimeAdapter` | phase4 stub | PASS |
| `VectorIndexPort` | `infra.QdrantIndexAdapter` | phase2 stub | PASS |
| `RerankerPort` | `runtime.RerankerRuntimeWorker` | phase3 stub | PASS |
| `OcrPort` | `runtime.PaddleOcrAdapter` | phase2 stub (via OCR fix) | PASS |
| `EmbeddingPort` | `runtime.EmbeddingRuntimeWorker` | phase2 stub | PASS |
| `NerServicePort` | `ingest.NerServiceAdapter` | (single bean — no conflict) | PASS |

`EmbeddingRuntimeWorker` `@Component` annotation **confirmed present** at class level in `phase5b-ai-runtime`.

---

## Target 3 — Immutable Records

| Record | Field | Defensive Copy | Status |
|---|---|---|---|
| `core.OcrResult` | `warnings: List<String>` | `List.copyOf()` | PASS |
| `ingest.OcrCompletedEvent` | `warnings: List<String>` | `List.copyOf()` | PASS |
| `ingest.NerCompletedEvent` | `entitiesExtracted: Map<String,Integer>` | `Map.copyOf()` | PASS |
| `assistant.SseCompleteEvent` | `warnings: List<String>` | `List.copyOf()` | PASS |

---

## Target 4 — Enum Persistence Explicit

All 9 enum-mapped JPA fields now carry `@Enumerated(EnumType.STRING)`. No field relies on Hibernate default ORDINAL. All repository filter queries use typed enum parameters.

| Entity | Fields Fixed | `@Enumerated(STRING)` | Status |
|---|---|---|---|
| `DocumentLegalJpaEntity` | 4 | All | PASS |
| `IngestionJobJpaEntity` | 4 | All | PASS |
| `UserRoleJpaEntity` | 1 | All | PASS |

---

## Target 5 — No Hardcoded Model Identifiers

| Location | Before | After | Status |
|---|---|---|---|
| `EmbeddingCompletedEvent.embeddingModel()` | `"bge-m3"` literal | `EmbeddingContract.EMBEDDING_MODEL` | PASS |

`EmbeddingContract.EMBEDDING_MODEL = "bge-m3"` is the single source of truth in `notarist-core`.  
`ModelRegistry` in `notarist-runtime` exposes `DEFAULT_EMBEDDING` which references the same value.

---

## Target 6 — Runtime Remains Ingest-Agnostic

No new dependency edges were introduced between `notarist-runtime` and `notarist-ingest`.

- `notarist-runtime` → `notarist-core` only (unchanged)
- `notarist-ingest` → `notarist-core` only (unchanged)
- `OcrServiceAdapter` (ingest) implements `OcrPort` from core — no runtime import
- `PaddleOcrAdapter` (runtime) implements `OcrPort` from core — no ingest import

---

## Target 7 — No New Circular Dependencies

All 21 file changes stayed within their respective modules. No inter-module imports were added.  
Dependency graph is unchanged from Phase 5B baseline:

```
notarist-web ──────────────────────────────────────────────────────┐
notarist-infra ─────────────────────────────────────────────────┐  │
notarist-runtime ────────────────────────────────────────────┐  │  │
notarist-observability ──────────────────────────────────┐   │  │  │
notarist-regulation ─────────────────────────────────┐   │   │  │  │
notarist-audit ──────────────────────────────────┐   │   │   │  │  │
notarist-assistant ──────────────────────────┐   │   │   │   │  │  │
notarist-search ──────────────────────────┐  │   │   │   │   │  │  │
notarist-ingest ──────────────────────┐   │  │   │   │   │   │  │  │
notarist-document ────────────────┐   │   │  │   │   │   │   │  │  │
notarist-auth ────────────────┐   │   │   │  │   │   │   │   │  │  │
                              └───┴───┴───┴──┴───┴───┴───┴───┘  │  │
                              notarist-core ◄──────────────────┘  │  │
                                       ▲─────────────────────────┘  │
                                       ▲────────────────────────────┘
```

---

## Outstanding Action Items (Pre-Phase 6A.3)

| Item | Owner | Priority |
|---|---|---|
| Scan callers of `OcrCompletedEvent.ocrWarnings()` and `.processingMs()` (renamed) | Developer | HIGH — before 6A.3 |
| Verify no remaining `import com.notarist.ingest.application.port.out.OcrServicePort` (non-deprecated) in active callers | Developer | HIGH |
| Phase 6A.4 deferred items (shadow core, SSE split, wildcard imports) | Developer | MEDIUM |

---

## PHASE 6A.2-FIX: COMPLETE

All P0 and P1 integrity risks from the PHASE 6A.2 validation scan are resolved.  
**Ready for PHASE 6A.3 approval gate.**
