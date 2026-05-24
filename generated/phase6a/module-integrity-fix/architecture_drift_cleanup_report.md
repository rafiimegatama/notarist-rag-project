# PHASE 6A.2-FIX — Architecture Drift Cleanup Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P2

---

## Scope

This report covers P2-class architecture drift: items that do not cause runtime failures today but erode maintainability and will cause confusion or latent bugs if left unaddressed. None of these were fixed in PHASE 6A.2-FIX (P0/P1 work took priority). All are tracked here for resolution in PHASE 6A.4.

---

## Item 1 — Shadow `notarist-core` Module

**Risk:** MEDIUM — confusing, not dangerous

### Finding

A second `notarist-core` source tree exists at:
```
backend-implementation/phase1-auth-document/notarist-core/
```
This is a copy from Phase 1 that is **not listed** in `settings.gradle.kts` and therefore never on the compile classpath. It diverges from the canonical skeleton core over time and creates confusion for any developer exploring the source tree.

### Action (Phase 6A.4)

- Add a `README.md` to that directory: "ARCHIVED — This is a Phase 1 snapshot of notarist-core. The live module is at backend-skeleton/notarist-core/. Do not modify this copy."
- Optionally rename the directory to `notarist-core-phase1-archive/` to make intent explicit.
- Do NOT delete until Phase 6A.4 confirms no imports reference this path.

---

## Item 2 — SSE Contract Split (Design A vs Design B)

**Risk:** MEDIUM — duplicate implementation paths

### Finding

Two incompatible SSE designs exist in `notarist-assistant`:

| Design | Location | Shape |
|---|---|---|
| Design A | `assistant.api.sse.SseEventEmitter` | Monolithic: single `SseEvent` with `type` string + `Object data` |
| Design B | `assistant.api.response.Sse*Event` records | Typed: `SseChunkEvent`, `SseCompleteEvent`, `SseErrorEvent` as separate records |

Both exist as live code. Design B (`Sse*Event` records) is more type-safe and the direction Phase 5B moved toward. Design A is the skeleton baseline.

### Action (Phase 6A.4)

- Choose Design B as the canonical contract.
- Update `SseEventEmitter` to use the typed `Sse*Event` records.
- Delete the monolithic `SseEvent` class.
- Update all callers (primarily `AssistantStreamingUseCase`).

---

## Item 3 — Wildcard Imports (30+ files)

**Risk:** LOW — build smell, prevents precise dependency analysis

### Finding

30+ Java source files use wildcard imports (`import com.notarist.core.domain.valueobject.*` etc.). Notable clusters:

| Package | Wildcard Users |
|---|---|
| `com.notarist.core.domain.valueobject.*` | DocumentLegal*, IngestionJob*, Auth* mappers |
| `com.notarist.ingest.domain.model.*` | Worker classes |
| `jakarta.persistence.*` | All JPA entities |
| `java.util.*` | Repository implementations |

Wildcard imports mask which types are actually used, making module boundary analysis error-prone and IDE navigation slower.

### Action (Phase 6A.4)

- Expand all wildcard imports to explicit imports.
- Run compiler with `-Xlint:imports` to surface unused imports in the same pass.
- This is a pure mechanical change — no logic changes.

---

## Item 4 — Duplicate Command / Request Class FQNs

**Risk:** LOW — compilation works, but confusing naming

### Finding

Several command and request classes exist with identical simple names in different modules:

| Simple Name | Module A | Module B |
|---|---|---|
| `IngestDocumentCommand` | `notarist-ingest.application.port.in` | `notarist-web.api.request` (as DTO) |
| `SearchDocumentRequest` | `notarist-search.application.port.in` | `notarist-web.api.request` |

These are structurally distinct (the web layer DTOs are thin wrappers, the port-in interfaces are use-case commands), but sharing simple names across modules causes confusion and risks accidental cross-module import.

### Action (Phase 6A.4)

- Rename web-layer DTOs consistently: `IngestDocumentRequest`, `SearchDocumentRequest` (web layer always uses `*Request` suffix, use-case layer uses `*Command`).
- This is already partially followed — enforce it uniformly.

---

## Item 5 — Hardcoded JPQL String Literals (Resolved in P1)

The only hardcoded JPQL string literal found in the scan was `WHERE j.pipelineStatus = 'FAILED'` in `IngestionJobJpaRepository`. This was resolved in the P1 Enum Persistence Fix — replaced with `:failedStatus` parameter of type `PipelineStatus.FAILED`.

**Status: RESOLVED in PHASE 6A.2-FIX P1.**

---

## Item 6 — `OcrCompletedEvent` Field Rename Callers

The field rename `ocrWarnings`→`warnings` and `processingMs`→`durationMs` applied in this pass may break any existing callers accessing the old accessor names (`.ocrWarnings()`, `.processingMs()`).

### Action (before Phase 6A.3)

Scan for all callers of the old accessor names:
```
grep -r "\.ocrWarnings()" src/
grep -r "\.processingMs()" src/
```
Update each caller to `.warnings()` / `.durationMs()`.

---

## Item 7 — `Collectors.toList()` Legacy Usage

Several repository implementations use `Collectors.toList()` (mutable result) instead of the Java 16+ `.toList()` (unmodifiable result). `IngestionJobRepositoryImpl` and `DocumentLegalRepositoryImpl` were updated to `.toList()` as part of this pass. Others may remain.

### Action (Phase 6A.4)

Replace remaining `stream().collect(Collectors.toList())` with `.stream().toList()` across all repository and mapper classes.

---

## Summary Table

| Item | Risk | Status | Target Phase |
|---|---|---|---|
| Shadow `notarist-core` module | MEDIUM | DEFERRED | 6A.4 |
| SSE Design A vs Design B split | MEDIUM | DEFERRED | 6A.4 |
| Wildcard imports (30+ files) | LOW | DEFERRED | 6A.4 |
| Duplicate command/request names | LOW | DEFERRED | 6A.4 |
| Hardcoded JPQL `'FAILED'` literal | LOW | **RESOLVED** | 6A.2-FIX |
| `OcrCompletedEvent` caller scan | MEDIUM | ACTION REQUIRED | Before 6A.3 |
| `Collectors.toList()` legacy | LOW | PARTIAL | 6A.4 |
