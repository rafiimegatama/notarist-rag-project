# PHASE 6A.1-FIX — Architecture Boundary Fix Report
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** All cross-module boundary violations resolved in this pass

---

## P2 Fixes Applied

---

### [P2-F01] OcrPort extracted to notarist-core

**Problem:**  
`PaddleOcrAdapter` in `notarist-runtime` implemented `OcrServicePort` from `notarist-ingest`:
```java
// BEFORE
import com.notarist.ingest.application.port.out.OcrServicePort;
public class PaddleOcrAdapter implements OcrServicePort {
```
This created a `runtime → ingest` dependency, violating the rule that **runtime must be ingestion-agnostic**.

**Fix:**  
Created canonical `OcrPort` in `notarist-core`:

```
generated/backend-skeleton/notarist-core/src/main/java/
  com/notarist/core/
    port/ocr/OcrPort.java             ← NEW shared port interface
    domain/ocr/OcrResult.java         ← NEW shared value object
    domain/ocr/OcrConfig.java         ← NEW shared value object
```

**Files created:**
- `notarist-core/.../port/ocr/OcrPort.java` — interface with `extractText(String, OcrConfig): OcrResult`
- `notarist-core/.../domain/ocr/OcrResult.java` — record (ocrObjectKey, pageCount, extractedTextLength, confidenceAvg, warnings, durationMs)
- `notarist-core/.../domain/ocr/OcrConfig.java` — record (language, enableHandwriting, enableTables, minConfidenceThreshold)

**PaddleOcrAdapter updated:**
```java
// AFTER
import com.notarist.core.port.ocr.OcrPort;
import com.notarist.core.domain.ocr.OcrConfig;
import com.notarist.core.domain.ocr.OcrResult;
public class PaddleOcrAdapter implements OcrPort {
```

**Dependency chain after fix:**
```
notarist-runtime → notarist-core (OcrPort, OcrResult, OcrConfig)
notarist-ingest  → notarist-core (same shared types)
```
No runtime → ingest dependency. Both implement/consume from the shared kernel.

---

### [P2-F02] OcrConfidencePolicy and OcrReviewStatus moved to notarist-core

**Problem:**  
`PaddleOcrAdapter` imported domain policy types from `notarist-infra`:
```java
// BEFORE (unused imports, but present — violation of boundary)
import com.notarist.infra.ocr.OcrConfidencePolicy;
import com.notarist.infra.ocr.OcrReviewStatus;
```
`OcrConfidencePolicy` and `OcrReviewStatus` are pure domain policy with zero infrastructure dependencies. They were incorrectly placed in `notarist-infra`.

**Fix:**  
Created canonical versions in `notarist-core`:

```
generated/backend-skeleton/notarist-core/src/main/java/
  com/notarist/core/
    domain/ocr/OcrReviewStatus.java        ← NEW canonical enum
    domain/ocr/OcrConfidencePolicy.java    ← NEW canonical policy class
```

**Infra stubs updated:**  
Existing files in `notarist-infra/src/main/java/com/notarist/infra/ocr/` annotated with migration comment directing to core. Retained as deprecated wrappers for backward compatibility until cleanup pass.

**PaddleOcrAdapter:**  
The imports of `OcrConfidencePolicy` and `OcrReviewStatus` were unused (mentioned in Javadoc only). Both removed from `PaddleOcrAdapter` imports — no functional change.

**Canonical locations post-fix:**

| Type | Old Location | New Location |
|---|---|---|
| `OcrReviewStatus` | `com.notarist.infra.ocr` | `com.notarist.core.domain.ocr` |
| `OcrConfidencePolicy` | `com.notarist.infra.ocr` | `com.notarist.core.domain.ocr` |
| `OcrResult` | nested in `com.notarist.ingest.application.port.out.OcrServicePort` | `com.notarist.core.domain.ocr` |
| `OcrConfig` | nested in `com.notarist.ingest.application.port.out.OcrServicePort` | `com.notarist.core.domain.ocr` |
| `OcrPort` | `com.notarist.ingest.application.port.out.OcrServicePort` (interface) | `com.notarist.core.port.ocr` |

---

### [P2-F03] EmbeddingContract extracted to notarist-core

**Problem:**  
`EmbeddingRuntimeWorker` in `notarist-runtime` imported `QdrantVectorPayload` from `notarist-infra` solely to access the `REQUIRED_DIMENSION = 1024` constant:
```java
// BEFORE
import com.notarist.infra.qdrant.QdrantVectorPayload;
// ...
if (dimension != QdrantVectorPayload.REQUIRED_DIMENSION) {
```
This created a `runtime → infra` dependency for a single constant — a structural violation.

**Fix:**  
Created `EmbeddingContract` in `notarist-core`:

```
generated/backend-skeleton/notarist-core/src/main/java/
  com/notarist/core/domain/vector/EmbeddingContract.java  ← NEW
```

Content:
```java
public final class EmbeddingContract {
    public static final int    REQUIRED_DIMENSION = 1024;
    public static final String EMBEDDING_MODEL    = "bge-m3";
    public static final String EMBEDDING_VERSION  = "1.0.0";
    private EmbeddingContract() {}
}
```

**EmbeddingRuntimeWorker updated:**
```java
// AFTER
import com.notarist.core.domain.vector.EmbeddingContract;
// ...
if (dimension != EmbeddingContract.REQUIRED_DIMENSION) {
```

**QdrantVectorPayload** retains its own `REQUIRED_DIMENSION = 1024` constant in `notarist-infra`. In a future cleanup, `QdrantVectorPayload` should reference `EmbeddingContract.REQUIRED_DIMENSION` to avoid duplication. Not blocking.

---

## Boundary Validation — Post-Fix

| Rule | Pre-Fix | Post-Fix | Notes |
|---|---|---|---|
| notarist-core imports no other module | PASS | PASS | Unchanged |
| assistant does not import ingest internals | PASS | PASS | Unchanged |
| search does not import assistant | PASS | PASS | Unchanged |
| observability is not a god-module | PASS | PASS | Self-contained |
| **runtime does not import ingest** | **FAIL** | **PASS** | PaddleOcrAdapter fixed |
| **runtime does not import infra internals** | **FAIL** | **PASS** | EmbeddingRuntimeWorker fixed |
| domain layer zero Spring dependency | PASS | PASS | New core types have zero Spring dependency |
| No new circular dependencies introduced | — | PASS | Full cycle check performed |

---

## Core Module Growth Summary

New types added to `notarist-core` in this fix pass:

| Package | File | Type | Purpose |
|---|---|---|---|
| `domain.ocr` | `OcrReviewStatus.java` | enum | OCR confidence classification |
| `domain.ocr` | `OcrConfidencePolicy.java` | final class | OCR threshold evaluation |
| `domain.ocr` | `OcrResult.java` | record | OCR extraction output |
| `domain.ocr` | `OcrConfig.java` | record | OCR extraction configuration |
| `domain.vector` | `EmbeddingContract.java` | final class | Embedding dimension constant |
| `port.ocr` | `OcrPort.java` | interface | Shared OCR output port |

All 6 new types are:
- Zero Spring dependency (no `@Component`, no `@Bean`, no Spring imports)
- Compile-safe (no external library imports)
- Testable without Spring context
- Consistent with existing `notarist-core` design patterns

---

## Remaining Boundary Notes (Not Resolved in This Pass)

| Issue | Severity | Reason Deferred |
|---|---|---|
| `notarist-ingest.OcrServicePort` still defines its own `OcrResult`/`OcrConfig` nested records | LATENT | Ingest module consistency audit is Phase 6A.2 scope |
| `QdrantVectorPayload` has duplicate `REQUIRED_DIMENSION` constant vs `EmbeddingContract` | LOW | QdrantVectorPayload infra-internal; cleanup in next refactor |
| 30+ wildcard imports remain across all modules | HIGH | Import expansion is a compile-safety task; deferred to post-build-fix cleanup |
| `OcrServicePort` in notarist-ingest still exists alongside new `OcrPort` in core | LATENT | Integration point audit is Phase 6A.2 scope |
