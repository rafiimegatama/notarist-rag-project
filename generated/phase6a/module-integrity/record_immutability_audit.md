# PHASE 6A.2 — Record Immutability Audit
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** All Java records — mutable collection exposure, compact constructor validation, Jackson compatibility, sealed interface usage

---

## Executive Summary

| Severity | Count | Finding |
|---|---|---|
| INTEGRITY_RISK | 3 | Mutable `List`/`Map` fields in records expose internal state |
| RUNTIME_RISK | 1 | Records with mutable collections used as event payloads — modification after creation possible |
| LATENT_RISK | 2 | Compact constructor validation missing in value object records, Jackson serialization compatibility |
| PASS | 8 | Value object records have null checks, DomainEvent records are structurally correct, no `@JsonDeserialize` needed for Spring Boot 3 |

---

## 1. Records with Mutable Collection Fields

### 1.1 OcrResult — Mutable List

**Location:** `notarist-core.domain.ocr.OcrResult`

```java
public record OcrResult(
    String ocrObjectKey, int pageCount, int extractedTextLength,
    float confidenceAvg, List<String> warnings, long durationMs
)
```

**Problem:** `List<String> warnings` is a mutable `java.util.List`. The record accessor `warnings()` returns a reference to the original list — callers can mutate it:
```java
OcrResult result = adapter.extractText(key, config);
result.warnings().add("injected warning");  // compiles and mutates
```

**Severity:** RUNTIME_RISK — domain model state can be corrupted post-creation.

**Remediation:**
```java
public record OcrResult(
    String ocrObjectKey, int pageCount, int extractedTextLength,
    float confidenceAvg, List<String> warnings, long durationMs
) {
    public OcrResult {
        warnings = List.copyOf(warnings != null ? warnings : List.of());
    }
}
```
`List.copyOf()` in the compact constructor creates an immutable defensive copy.

---

### 1.2 OcrCompletedEvent (Skeleton) — Mutable List

**Location:** `notarist-ingest.domain.event.OcrCompletedEvent`

```java
public record OcrCompletedEvent(
    ..., List<String> ocrWarnings, long processingMs
) implements DomainEvent
```

Same risk as `OcrResult`. Domain events are intended to be immutable value snapshots for audit replay. A mutable list in an event breaks the immutability guarantee.

**Severity:** RUNTIME_RISK — event audit log can be mutated in-memory after firing.

**Remediation:** Same pattern — compact constructor with `List.copyOf()`.

---

### 1.3 NerCompletedEvent — Mutable Map

**Location:** `notarist-ingest.domain.event.NerCompletedEvent`

```java
public record NerCompletedEvent(
    ..., Map<String, Integer> entitiesExtracted, ...
) implements DomainEvent
```

`Map<String, Integer>` is mutable. `entitiesExtracted()` returns the original map reference.

**Severity:** RUNTIME_RISK — NER entity counts can be modified after event is fired.

**Remediation:**
```java
public NerCompletedEvent {
    entitiesExtracted = Map.copyOf(entitiesExtracted != null ? entitiesExtracted : Map.of());
}
```

---

### 1.4 SseCompleteEvent — Mutable List

**Location:** `notarist-assistant.api.response.SseCompleteEvent` (skeleton)

```java
public record SseCompleteEvent(
    UUID sessionId, int totalTokens, int citationCount, float groundingScore,
    long durationMs, long ttftMs, boolean hallucinationFlagRaised,
    List<String> warnings
)
```

API response records sent over the wire don't have the same mutability risk as domain events, but following consistent immutability discipline prevents future bugs when these records are reused.

**Severity:** LATENT_RISK.

**Remediation:** Same compact constructor pattern with `List.copyOf()`.

---

## 2. Value Object Records — Compact Constructor Validation

### Records WITH Null Checks (PASS)

| Record | Location | Null Check Present |
|---|---|---|
| `ChunkId` | `notarist-core.domain.valueobject` | YES — `if (value == null) throw` |
| `DocumentId` | `notarist-core.domain.valueobject` | YES — compact constructor verified |
| `CorrelationId` | `notarist-core.domain.valueobject` | YES |
| `TraceId` | `notarist-core.domain.valueobject` | YES |
| `JobId` | `notarist-core.domain.valueobject` | YES |
| `SessionId` | `notarist-core.domain.valueobject` | YES |

### Records to Verify (NOT YET SCANNED)

| Record | Location | Status |
|---|---|---|
| `NomorAkta` | `notarist-core.domain.valueobject` | VERIFY null check and format validation |
| `NomorNIK` | `notarist-core.domain.valueobject` | VERIFY — NIK is 16 digits; length check needed |
| `NomorNPWP` | `notarist-core.domain.valueobject` | VERIFY — NPWP format check needed |
| `PersonId` | `notarist-core.domain.valueobject` | VERIFY |

**Recommendation:** NomorNIK and NomorNPWP are high-sensitivity PII identifiers. Compact constructors should validate format (not just null) to prevent garbage data from propagating into the system.

---

## 3. OcrConfig — Factory Method Validation

**Location:** `notarist-core.domain.ocr.OcrConfig`

```java
public record OcrConfig(
    String language, boolean enableHandwriting,
    boolean enableTables, float minConfidenceThreshold
) {
    public static OcrConfig defaultIndonesia() {
        return new OcrConfig("id", false, false, 0.4f);
    }
}
```

**Assessment: PASS** — no mutable fields. All primitives and String (immutable).

**LATENT_RISK:** `language` is a raw String. If PaddleOCR only supports specific language codes (`"id"`, `"en"`), this should be validated in a compact constructor:
```java
public OcrConfig {
    Objects.requireNonNull(language, "language");
    if (minConfidenceThreshold < 0.0f || minConfidenceThreshold > 1.0f)
        throw new IllegalArgumentException("minConfidenceThreshold must be 0.0-1.0, got: " + minConfidenceThreshold);
}
```

---

## 4. Domain Events — Structural Immutability Assessment

### Assessment Criteria
- Record fields must be immutable primitives, immutable value types (Instant, UUID, String), value objects (CorrelationId, TraceId), or defensively copied collections
- `implements DomainEvent` must be present

| Event | Mutable Fields | DomainEvent | Verdict |
|---|---|---|---|
| `DocumentUploadedEvent` | none — all primitives, String, UUID, value objects | YES | PASS |
| `OcrCompletedEvent` (skeleton) | `List<String> ocrWarnings` | YES | FAIL — needs defensive copy |
| `NerCompletedEvent` | `Map<String, Integer> entitiesExtracted` | YES | FAIL — needs defensive copy |
| `EmbeddingCompletedEvent` | none | YES | PASS |
| `ChunkingCompletedEvent` | needs scan | YES (assumed) | UNVERIFIED |
| `IndexingCompletedEvent` | needs scan | YES (assumed) | UNVERIFIED |
| `AiResponseGeneratedEvent` | none | YES | PASS |
| `CitationCreatedEvent` | needs scan | YES (assumed) | UNVERIFIED |

---

## 5. Sealed Interface Usage

### Current Usage: NONE FOUND

A `sealed interface` is used in `SseWarningEvent.WarningCode` enum-like structure, but no sealed interfaces for polymorphic record hierarchies are in use.

**Opportunity Identified (not required but recommended for Phase 6A.4):**

The SSE event types (Design B) could use a sealed interface:
```java
public sealed interface SseEventEnvelope 
    permits SseTokenEvent, SseCitationEvent, SseCompleteEvent, SseWarningEvent, SseErrorEvent {}
```

This would enable exhaustive pattern matching at the controller/serializer level:
```java
return switch (event) {
    case SseTokenEvent t -> ...
    case SseCitationEvent c -> ...
    // exhaustive — compiler enforces all cases
};
```

This is a **LATENT_RISK recommendation**, not a blocker.

---

## 6. Jackson Serialization Compatibility

### Records + Jackson in Spring Boot 3.2.5

Spring Boot 3.2.5 uses Jackson 2.17.x which has native support for Java records (`jackson-module-parameter-names` is auto-configured). Records serialize and deserialize correctly without `@JsonDeserialize` annotations **as long as:**

1. The record has a single canonical constructor (auto-satisfied)
2. Parameter names are preserved in bytecode (`-parameters` javac flag)
3. Spring Boot 3 enables `-parameters` by default via `spring-boot-maven-plugin` / `spring-boot-gradle-plugin`

**LATENT_RISK — `--enable-preview` flag interaction:**  
The project uses `--enable-preview` in some modules (flagged in RISK-LAT1 from 6A.1). Preview mode can affect bytecode in unexpected ways. Parameter names should be verified after compilation.

**LATENT_RISK — API response records + `@JsonSerialize`:**  
`ApiResponse<T>` is a generic record. Jackson handles generic records in Spring Boot 3 correctly when `ObjectMapper` has `FAIL_ON_EMPTY_BEANS = false` and `registerKotlinModule` is not required. Verify `ObjectMapper` configuration in `notarist-web`.

---

## 7. Remediation Summary

| ID | Severity | Record | Issue | Action |
|---|---|---|---|---|
| RIA-R1 | RUNTIME_RISK | `OcrResult` | `List<String> warnings` mutable | Add compact constructor with `List.copyOf()` |
| RIA-R2 | RUNTIME_RISK | `OcrCompletedEvent` | `List<String> ocrWarnings` mutable | Add compact constructor with `List.copyOf()` |
| RIA-R3 | RUNTIME_RISK | `NerCompletedEvent` | `Map<String,Integer> entitiesExtracted` mutable | Add compact constructor with `Map.copyOf()` |
| RIA-L1 | LATENT_RISK | `SseCompleteEvent` | `List<String> warnings` mutable | Add compact constructor with `List.copyOf()` |
| RIA-L2 | LATENT_RISK | `OcrConfig` | `minConfidenceThreshold` range unchecked | Add compact constructor range validation |
| RIA-L3 | LATENT_RISK | `NomorNIK`, `NomorNPWP` | No format validation | Add compact constructor format checks |
| RIA-L4 | LATENT_RISK | SSE event types | No sealed interface unifying them | Add `SseEventEnvelope` sealed interface (Phase 6A.4) |
