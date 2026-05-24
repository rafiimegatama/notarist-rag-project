# PHASE 6A.2-FIX — Immutability Restoration Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P1

---

## Problem Summary

4 domain records accepted mutable `List` / `Map` collections in their canonical constructors. Any caller retaining a reference to the original collection could mutate record state after construction, breaking the record immutability contract.

---

## Affected Records

| Record | Field | Type | Risk |
|---|---|---|---|
| `core.domain.ocr.OcrResult` | `warnings` | `List<String>` | Caller-held reference mutates OCR result warnings |
| `ingest.domain.event.OcrCompletedEvent` | `ocrWarnings` (now `warnings`) | `List<String>` | Event payload mutable after publish |
| `ingest.domain.event.NerCompletedEvent` | `entitiesExtracted` | `Map<String, Integer>` | NER entity counts mutable after event publish |
| `assistant.api.response.SseCompleteEvent` | `warnings` | `List<String>` | SSE response warnings mutable after construction |

---

## Fix: Compact Constructors with Defensive Copy

All four records received a compact constructor that replaces the field with an unmodifiable copy on construction. `null`-safe: a `null` argument is normalized to an empty immutable collection.

### `core.domain.ocr.OcrResult`

```java
public record OcrResult(String ocrObjectKey, int pageCount, int extractedTextLength,
                        float confidenceAvg, List<String> warnings, long durationMs) {
    public OcrResult {
        warnings = List.copyOf(warnings != null ? warnings : List.of());
    }
}
```

### `ingest.domain.event.OcrCompletedEvent`

Field rename applied in the same pass (see OCR Contract Unification report).

```java
public record OcrCompletedEvent(
        String ingestionId, String documentId, String tenantId,
        String ocrObjectKey, int pageCount, float confidenceAvg,
        List<String> warnings, long durationMs,
        String correlationId, Instant occurredAt) implements DomainEvent {
    public OcrCompletedEvent {
        warnings = List.copyOf(warnings != null ? warnings : List.of());
    }
    @Override public String eventType()    { return "ocr.completed"; }
    @Override public String publishedBy()  { return "notarist-ingest"; }
}
```

### `ingest.domain.event.NerCompletedEvent`

```java
public record NerCompletedEvent(
        String ingestionId, String documentId, String tenantId,
        Map<String, Integer> entitiesExtracted,
        String correlationId, Instant occurredAt) implements DomainEvent {
    public NerCompletedEvent {
        entitiesExtracted = Map.copyOf(entitiesExtracted != null ? entitiesExtracted : Map.of());
    }
    @Override public String eventType()    { return "ner.completed"; }
    @Override public String publishedBy()  { return "notarist-ingest"; }
}
```

### `assistant.api.response.SseCompleteEvent`

```java
public record SseCompleteEvent(
        String sessionId, String queryId,
        List<String> warnings, Instant completedAt) {
    public SseCompleteEvent {
        warnings = List.copyOf(warnings != null ? warnings : List.of());
    }
}
```

---

## Files Modified

| File | Change |
|---|---|
| `backend-skeleton/notarist-core/.../ocr/OcrResult.java` | Added compact constructor: `List.copyOf()` on `warnings` |
| `backend-skeleton/notarist-ingest/.../event/OcrCompletedEvent.java` | Added compact constructor: `List.copyOf()` on `warnings`; field renamed `ocrWarnings`→`warnings`, `processingMs`→`durationMs` |
| `backend-skeleton/notarist-ingest/.../event/NerCompletedEvent.java` | Added compact constructor: `Map.copyOf()` on `entitiesExtracted` |
| `backend-skeleton/notarist-assistant/.../response/SseCompleteEvent.java` | Added compact constructor: `List.copyOf()` on `warnings` |

---

## Post-Fix Immutability Status

| Record | Collection Field | Defensive Copy | Null-Safe | Status |
|---|---|---|---|---|
| `OcrResult` | `warnings` | `List.copyOf()` | yes | PASS |
| `OcrCompletedEvent` | `warnings` | `List.copyOf()` | yes | PASS |
| `NerCompletedEvent` | `entitiesExtracted` | `Map.copyOf()` | yes | PASS |
| `SseCompleteEvent` | `warnings` | `List.copyOf()` | yes | PASS |

---

## Latent Items Not Fixed This Pass

| Item | Reason Deferred |
|---|---|
| `SseChunkEvent` and other SSE record variants | No mutable collection fields found in scan — no action required |
| `OcrCompletedEvent` callers of `.ocrWarnings()` / `.processingMs()` | Must be scanned and updated before Phase 6A.3 (see architecture drift report) |
