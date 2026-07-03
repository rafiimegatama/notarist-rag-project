# NOTARIST — Module Dependency Matrix

**Phase 6A.1 — Build & Dependency Validation**
Generated: 2026-05-24

---

## Module Inventory

| Module ID | Package Root | Phase | Location |
|---|---|---|---|
| `notarist-core` | `com.notarist.core` | 1 | `phase1-auth-document/notarist-core` |
| `notarist-auth` | `com.notarist.auth` | 1 | `phase1-auth-document/notarist-auth` |
| `notarist-document` | `com.notarist.document` | 1 | `phase1-auth-document/notarist-document` |
| `notarist-ingest` | `com.notarist.ingest` | 2 | `phase2-ingest/notarist-ingest` |
| `notarist-search` | `com.notarist.search` | 3 | `phase3-search/notarist-search` |
| `notarist-assistant` | `com.notarist.assistant` | 4 | `phase4-assistant/notarist-assistant` |
| `notarist-infra` | `com.notarist.infra` | 5A | `phase5-infra-ai/notarist-infra` |
| `notarist-runtime` | `com.notarist.runtime` | 5B | `phase5b-ai-runtime/notarist-runtime` |
| `notarist-observability` | `com.notarist.observability` | 5C | `phase5c-observability/notarist-observability` |

---

## Dependency Matrix (compile scope)

`→` = depends on  `✓` = allowed  `✗` = violation  `—` = no dependency

| From ↓ / To → | core | auth | document | ingest | search | assistant | infra | runtime | observability |
|---|---|---|---|---|---|---|---|---|---|
| **core** | — | — | — | — | — | — | — | — | — |
| **auth** | ✓ | — | — | — | — | — | — | — | — |
| **document** | ✓ | ✓ | — | — | — | — | — | — | — |
| **ingest** | ✓ | — | — | — | — | — | — | — | — |
| **search** | ✓ | — | — | — | — | — | — | — | — |
| **assistant** | ✓ | — | — | — | ✓ | — | — | — | — |
| **infra** | ✓ | — | — | ✓ | ✓ | — | — | — | — |
| **runtime** | ✓ | — | — | ✓ | ✓ | ✓ | ✓ | — | — |
| **observability** | — | — | — | — | — | — | — | — | — |

---

## Dependency Direction (acyclic validation)

```
notarist-core
  ↑
  ├── notarist-auth
  ├── notarist-document
  ├── notarist-ingest
  ├── notarist-search
  │     ↑
  │     └── notarist-assistant
  │           ↑
  │     notarist-infra (→ ingest, → search)
  │           ↑
  │     notarist-runtime (→ ingest, → search, → assistant, → infra)
  │
  └── notarist-observability (SELF-CONTAINED — no cross-module imports)
```

**Circular dependency check: NONE FOUND** ✓

---

## Cross-Module Import Evidence

### `notarist-infra` imports from:
| Imported Package | Type | File |
|---|---|---|
| `com.notarist.core.domain.valueobject.DocumentId` | Value Object | `MinioDocumentStorageAdapter.java`, `QdrantIndexAdapter.java` |
| `com.notarist.core.domain.valueobject.JobId` | Value Object | `MinioDocumentStorageAdapter.java` |
| `com.notarist.core.domain.valueobject.ClassificationLevel` | Enum | `QdrantFilterBuilder.java`, `QdrantSearchAdapter.java` |
| `com.notarist.ingest.application.port.out.DocumentStoragePort` | Port | `MinioDocumentStorageAdapter.java` |
| `com.notarist.ingest.application.port.out.VectorIndexPort` | Port | `QdrantIndexAdapter.java` |
| `com.notarist.search.application.port.out.VectorSearchPort` | Port | `QdrantSearchAdapter.java` |

### `notarist-runtime` imports from:
| Imported Package | Type | File |
|---|---|---|
| `com.notarist.core.domain.valueobject.*` | Value Objects | Multiple |
| `com.notarist.ingest.application.port.out.OcrServicePort` | Port | `PaddleOcrAdapter.java` |
| `com.notarist.search.application.port.out.RerankerPort` | Port | `RerankerRuntimeWorker.java` |
| `com.notarist.assistant.application.port.out.LlmPort` | Port | `OllamaRuntimeAdapter.java` |
| `com.notarist.infra.ocr.OcrConfidencePolicy` | Infra type | `PaddleOcrAdapter.java` |
| `com.notarist.infra.ocr.OcrReviewStatus` | Infra type | `PaddleOcrAdapter.java` |
| `com.notarist.infra.qdrant.QdrantVectorPayload` | Infra type | `EmbeddingRuntimeWorker.java` |

### `notarist-observability` imports from:
| Imported Package | Type | Status |
|---|---|---|
| (none — self-contained) | — | ✓ CLEAN |

---

## Dependency Chain Depth

| Module | Max dependency depth from core |
|---|---|
| notarist-core | 0 |
| notarist-auth / notarist-document / notarist-ingest / notarist-search | 1 |
| notarist-assistant | 2 |
| notarist-infra | 2 |
| notarist-runtime | 3 |
| notarist-observability | 0 (self-contained) |
