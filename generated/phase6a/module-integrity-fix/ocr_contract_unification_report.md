# PHASE 6A.2-FIX — OCR Contract Unification Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P0

---

## Problem Summary

Two incompatible OCR contracts existed simultaneously:

| Contract | Location | OcrConfig Fields | OcrResult Fields |
|---|---|---|---|
| `OcrServicePort` (skeleton) | `notarist-ingest.application.port.out` | `(int dpi, String language, boolean enhanceContrast)` | `(..., long processingMs)` |
| `OcrServicePort` (phase2 impl) | same package | `(String language, boolean enableHandwriting, boolean enableTables, float minConfidenceThreshold)` | `(..., long durationMs)` |
| `OcrPort` + `core.OcrConfig` | `notarist-core.port.ocr` | `(String language, boolean enableHandwriting, boolean enableTables, float minConfidenceThreshold)` | `(..., long durationMs)` |

**Finding:** The phase2 `OcrServicePort` was already field-identical to `core.OcrPort`. Only the skeleton version diverged (used `dpi`, `enhanceContrast`). The root cause was an out-of-sync skeleton definition.

---

## Migration Map

```
BEFORE                                  AFTER
──────────────────────────────────────  ──────────────────────────────────────
OcrServicePort (skeleton)               OcrServicePort (skeleton)
  record OcrConfig(dpi, lang, enh)   →  @Deprecated empty interface
  record OcrResult(..., processingMs)

OcrServicePort (phase2 impl)            OcrServicePort (phase2 impl)
  record OcrConfig(lang, hw, tbl, min) →  @Deprecated empty interface
  record OcrResult(..., durationMs)

OcrWorker                               OcrWorker
  injects OcrServicePort             →  injects OcrPort (core)
  uses OcrServicePort.OcrConfig.       uses OcrConfig.defaultIndonesia()
    defaultIndonesia()                 
  uses OcrServicePort.OcrResult       uses OcrResult

OcrServiceAdapter                       OcrServiceAdapter
  @Component                         →  no @Component (production bean is PaddleOcrAdapter)
  implements OcrServicePort           →  implements OcrPort
  returns OcrServicePort.OcrResult    →  returns core.OcrResult

OcrCompletedEvent (skeleton)            OcrCompletedEvent (skeleton)
  List<String> ocrWarnings           →  List<String> warnings
  long processingMs                  →  long durationMs
  (no compact constructor)            →  compact constructor with List.copyOf()
```

---

## Files Modified

| File | Change |
|---|---|
| `backend-skeleton/.../OcrServicePort.java` | Replaced with `@Deprecated` empty interface; removed nested records |
| `backend-implementation/phase2-ingest/.../OcrServicePort.java` | Replaced with `@Deprecated` empty interface; removed nested records |
| `backend-implementation/phase2-ingest/.../OcrWorker.java` | Injects `OcrPort`; uses `OcrConfig`/`OcrResult` from core |
| `backend-implementation/phase2-ingest/.../OcrServiceAdapter.java` | Implements `OcrPort`; `@Component` removed; no Spring dependency |
| `backend-skeleton/.../OcrCompletedEvent.java` | Renamed `ocrWarnings`→`warnings`, `processingMs`→`durationMs`; added compact constructor |
| `backend-skeleton/.../core/domain/ocr/OcrResult.java` | Added compact constructor with `List.copyOf()` |

---

## Post-Fix Contract Verification

| Rule | Status |
|---|---|
| Single OCR port interface: `core.OcrPort` | PASS |
| Single `OcrConfig` definition: `core.domain.ocr.OcrConfig` | PASS |
| Single `OcrResult` definition: `core.domain.ocr.OcrResult` | PASS |
| `PaddleOcrAdapter` implements `OcrPort` | PASS (from Phase 6A.1-FIX) |
| `OcrServiceAdapter` implements `OcrPort` | PASS (fixed this pass) |
| `OcrWorker` uses `OcrPort` | PASS (fixed this pass) |
| `notarist-runtime` does NOT depend on `notarist-ingest` | PASS |
| `notarist-ingest` does NOT depend on `notarist-runtime` | PASS |
| No circular dependencies introduced | PASS |

---

## Remaining Items

| Item | Classification | Action |
|---|---|---|
| `OcrServicePort` deprecated stubs still compile | LATENT | Delete in Phase 6A.4 cleanup after confirming no remaining imports |
| `OcrCompletedEvent` field rename (`ocrWarnings→warnings`) may break any existing callers | LATENT | Scan for callers of `.ocrWarnings()` and `.processingMs()` before Phase 6A.3 |
