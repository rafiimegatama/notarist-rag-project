# PHASE 6A.2 — DTO & Event Consistency Report
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** DTO schema consistency, event record field completeness, SSE contract alignment, cross-module DTO duplication

---

## Executive Summary

| Severity | Count | Finding |
|---|---|---|
| BLOCKER | 0 | — |
| INTEGRITY_RISK | 4 | SSE dual design, SseEvent.eventType as String, CitationDto duplication, event fields use raw UUIDs not value objects |
| RUNTIME_RISK | 1 | `OcrCompletedEvent` fields diverge from `OcrResult` record fields |
| LATENT_RISK | 3 | skeleton/impl record duplication, DTO prefix inconsistency, response field naming inconsistency |

---

## 1. Domain Event Records — Field Completeness Audit

### 1.1 DocumentUploadedEvent

```java
record DocumentUploadedEvent(
    UUID eventId, Instant timestamp, CorrelationId correlationId, TraceId traceId,
    UUID jobId, UUID documentId, UUID tenantId, UUID uploadedBy,
    String objectKey, String originalFilename,
    JenisDokumen documentType, JenisAkta jenisAkta,
    String mimeType, String checksumSha256, Long fileSizeBytes,
    ClassificationLevel classificationLevel
) implements DomainEvent
```

**Assessment: PASS — complete for audit and replay.**  
Fields carry typed value objects (`JenisDokumen`, `JenisAkta`, `ClassificationLevel`). UUID fields (`jobId`, `documentId`, `tenantId`, `uploadedBy`) are raw `UUID` instead of `JobId`, `DocumentId` — minor type-safety gap, not a breaking issue.

**LATENT_RISK:** `uploadedBy` is raw `UUID`. Should be `PersonId` or `UserId` value object for consistency with `DocumentId`, `JobId` pattern.

---

### 1.2 OcrCompletedEvent vs OcrResult — Divergence

**OcrCompletedEvent fields:**
```java
UUID jobId, UUID documentId, String ocrObjectKey,
int pageCount, int extractedTextLength, float confidenceAvg,
List<String> ocrWarnings, long processingMs
```

**OcrResult (core) fields:**
```java
String ocrObjectKey, int pageCount, int extractedTextLength,
float confidenceAvg, List<String> warnings, long durationMs
```

**RUNTIME_RISK — Naming divergence:**
| OcrCompletedEvent | OcrResult | Conflict |
|---|---|---|
| `ocrWarnings` | `warnings` | Different field names |
| `processingMs` | `durationMs` | Different field names |

When `PaddleOcrAdapter.extractText()` returns an `OcrResult`, the ingest pipeline that converts `OcrResult` → `OcrCompletedEvent` must manually map `ocrResult.warnings()` → `event.ocrWarnings` and `ocrResult.durationMs()` → `event.processingMs`. This mapping is implicit and error-prone.

**Remediation:** Standardize naming. Either rename `OcrResult` fields to match the event, or rename event fields to match `OcrResult`. Recommended: align to `OcrResult` as canonical (`warnings`, `durationMs`).

---

### 1.3 EmbeddingCompletedEvent

```java
record EmbeddingCompletedEvent(
    UUID eventId, Instant timestamp, CorrelationId correlationId, TraceId traceId,
    UUID jobId, UUID documentId,
    int totalVectors, long processingMs
) implements DomainEvent
```

**Assessment:** Minimal but sufficient. References `NotaristConstants.EMBEDDING_DIMENSION` via `embeddingDimension()` method (not a constructor field — uses constant). This is correct design.

**LATENT_RISK:** `embeddingModel()` hardcodes `"bge-m3"` string literal in the method body instead of using `EmbeddingContract.EMBEDDING_MODEL`. Should reference `EmbeddingContract`.

---

### 1.4 AiResponseGeneratedEvent

```java
record AiResponseGeneratedEvent(
    UUID eventId, Instant timestamp, CorrelationId correlationId, TraceId traceId,
    SessionId sessionId, UUID userId, UUID tenantId, String queryHash,
    int citationCount, int tokensInput, int tokensOutput,
    String modelId, float groundingScore, boolean hallucinationFlagRaised,
    String streamMode, long streamDurationMs, long ttftMs
) implements DomainEvent
```

**Assessment: PASS for audit completeness.** All required observability fields present.

**INTEGRITY_RISK — `streamMode` is raw String.** Expected values: `"SSE"`, `"BATCH"`. Should be an enum `StreamMode` in core or assistant. No compile-time enforcement.

**INTEGRITY_RISK — `modelId` is raw String.** Should reference `EmbeddingContract.EMBEDDING_MODEL` or use `ModelProvider` enum from runtime. Loose coupling between AI model identity and event contract.

---

## 2. SSE Contract — Dual Design Analysis

### 2.1 Design A: Monolithic SseEvent (Phase 4 implementation)

**Location:** `notarist-assistant.api.response.SseEvent` (phase4 impl)

```java
public record SseEvent(
    String eventType, String data, String traceId, int sequence, long timestampMs
)
```

- `eventType` is `String` — any value can be passed
- 7 factory methods provide named constructors
- `data` is opaque `String` — citation and confidence data are serialized JSON strings

**Problems:**
1. `eventType` has no compile-time constraint — passing unknown event type is silently accepted
2. `data` field is String — JSON-in-JSON anti-pattern; citation payloads lose type safety
3. No schema version on SSE events — client deserialization will break silently on changes

### 2.2 Design B: Typed SSE Records (Skeleton)

**Location:** `notarist-assistant.api.response.{SseTokenEvent, SseCitationEvent, SseCompleteEvent, SseWarningEvent, SseErrorEvent}`

- Each record is typed with specific fields
- `SseWarningEvent` has nested `WarningCode` enum
- `SseCitationEvent` carries structured citation fields (no JSON string)

**Problems:**
1. No common interface — no `SseEventEnvelope` interface exists to unify the 5 records
2. Cannot be handled polymorphically without instanceof checks
3. Spring SseEmitter must serialize each differently

### 2.3 Conflict Assessment

**INTEGRITY_RISK:** Both designs are present and both are valid Java — no compile error. However:
- Phase 5 `OllamaRuntimeAdapter` (checked earlier) streams responses. What type does it return?
- The `ResponseStreamer` in assistant sends events via `SseEmitter`. Which type does it serialize?
- A client receiving SSE events will get inconsistent `event:` and `data:` fields depending on which design the code path follows.

**Remediation (Phase 6A.2 Fix):**  
Adopt Design B (typed records) as canonical. Add a `SseEvent` marker interface to unify them:
```java
// In notarist-core or notarist-assistant
public sealed interface SseEventEnvelope permits 
    SseTokenEvent, SseCitationEvent, SseCompleteEvent, SseWarningEvent, SseErrorEvent {}
```
Remove the monolithic `SseEvent` record from Phase 4 impl. Migration must be done before Phase 5 streaming works.

---

## 3. DTO Duplication Analysis

### 3.1 SearchResponse Duplication

| Location | Class | Fields |
|---|---|---|
| `notarist-search` skeleton | `SearchResponse.java` | record — needs scan |
| `notarist-search` phase3 impl | `SearchResponse.java` (different path?) | record — needs scan |

Two `SearchResponse` records exist in different packages in the same module. Gradle compiles both — the one on the classpath wins at runtime. This is a **LATENT_RISK**.

### 3.2 CitationDto vs CitationEntry vs CitationResponse

| Class | Module | Location |
|---|---|---|
| `CitationDto` | notarist-assistant (phase4 impl) | `api.response.CitationDto` |
| `CitationEntry` | notarist-search (phase3 impl) | `domain.model.CitationEntry` |
| `CitationResponse` | notarist-search (phase3 impl) | `api.response.CitationResponse` |
| `SseCitationEvent` | notarist-assistant (skeleton) | `api.response.SseCitationEvent` |

Four different citation DTO representations exist across 2 modules. No shared citation contract in `notarist-core`.

**INTEGRITY_RISK:** Each phase maps citations differently. Client code cannot rely on a consistent citation schema.

**Remediation:** Define canonical `CitationDto` in `notarist-core.api.response` (or `notarist-search.domain.model`). All cross-module citation references use the same type.

### 3.3 IngestionStatusResponse Duplication

| Location | Class |
|---|---|
| `notarist-ingest` phase2 impl | `api.response.IngestionStatusResponse` |
| `notarist-ingest` skeleton | `api.response.IngestionJobStatusResponse` |

Two different class names for the same conceptual response. The skeleton and phase2 impl are separate source trees — both compile. At runtime, whichever is on classpath wins. Name divergence will cause confusion.

**Remediation:** Standardize to `IngestionJobStatusResponse` (skeleton is authoritative). Remove phase2 impl duplicate.

---

## 4. UploadUrlResponse Duplication

| Location | Fields |
|---|---|
| `notarist-ingest` phase2 impl | `record UploadUrlResponse(...)` |
| `notarist-ingest` skeleton | `record UploadUrlResponse(...)` |

Two identical-named classes in different source roots of the same module. Verify they have the same fields — if not, this is a schema split.

---

## 5. InitiateIngestionCommand Duplication

| Location |
|---|
| `notarist-ingest` phase2 impl: `application.command.InitiateIngestionCommand` |
| `notarist-ingest` skeleton: `application.command.InitiateIngestionCommand` |

Same fully-qualified class name in two source trees under the same Gradle module. If both are on `srcDirs`, this is a **compile error** (duplicate class). If only one is active, the other is dead code.

**Action required:** Verify `notarist-ingest/build.gradle.kts` `srcDirs` configuration. Only one source root should provide this class.

---

## 6. Remediation Summary

| ID | Severity | Issue | Remediation |
|---|---|---|---|
| DEC-I1 | INTEGRITY_RISK | `OcrCompletedEvent` vs `OcrResult` field name mismatch | Align `ocrWarnings→warnings`, `processingMs→durationMs` |
| DEC-I2 | INTEGRITY_RISK | SSE dual design (SseEvent vs 5 typed records) | Adopt Design B; add `SseEventEnvelope` sealed interface; delete monolithic `SseEvent` |
| DEC-I3 | INTEGRITY_RISK | 4 citation DTO types across 2 modules | Canonicalize citation type in `notarist-core` or `notarist-search.domain.model` |
| DEC-I4 | INTEGRITY_RISK | `streamMode` and `modelId` as raw String in AiResponseGeneratedEvent | Define `StreamMode` enum; reference `EmbeddingContract.EMBEDDING_MODEL` |
| DEC-R1 | RUNTIME_RISK | `EmbeddingCompletedEvent.embeddingModel()` hardcodes `"bge-m3"` | Use `EmbeddingContract.EMBEDDING_MODEL` |
| DEC-L1 | LATENT_RISK | `IngestionStatusResponse` vs `IngestionJobStatusResponse` | Standardize to skeleton name |
| DEC-L2 | LATENT_RISK | `InitiateIngestionCommand` duplicated in two source roots | Verify srcDirs; remove duplicate |
| DEC-L3 | LATENT_RISK | Raw UUID fields in events instead of value objects | Replace `UUID uploadedBy` → `PersonId`, etc. |
