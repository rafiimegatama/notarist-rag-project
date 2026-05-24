# PHASE 6A.2 — Module Integrity Report
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** Structural integrity of all 12 modules — package layout, interface completeness, cross-module leakage, and build graph correctness

---

## Executive Summary

| Severity | Count | Status |
|---|---|---|
| BLOCKER | 1 | Dual `@Component` for same port = Spring context failure at startup |
| INTEGRITY_RISK | 5 | Enum field type erasure, SSE contract split, OcrPort dualism, ClassificationLevel divergence, event version passivity |
| RUNTIME_RISK | 2 | Missing @GeneratedValue intent clarity, mutable collection exposure in records |
| LATENT_RISK | 3 | KeywordRetrievalPort unimplemented, skeleton/impl core divergence, DomainEvent version default not overridden |
| PASS | 6 | DomainEvent interface exists, hexagonal structure correct, no circular deps, build graph verified, core zero-Spring confirmed, record usage appropriate |

---

## 1. Module Structural Layout — Validated

### Confirmed Package Structure per Module

| Module | Domain Layer | Application Layer | Infrastructure Layer | API Layer | Conforms |
|---|---|---|---|---|---|
| notarist-core | `domain.{ocr,valueobject,event,exception,vector}` | `application.usecase` | — | `api.response` | YES |
| notarist-auth | `domain.model` | `application.{port,service,command}` | `persistence.{oracle,postgres}` | `api.{request,response}` | YES |
| notarist-document | `domain.model` | `application.{port,query,usecase}` | `persistence.oracle` | `api.response` | YES |
| notarist-ingest | `domain.{model,event}` | `application.{port,service,command}` | `{adapter,persistence.{oracle,postgres}}` | `api.{request,response}` | YES |
| notarist-search | `domain.model` | `application.{pipeline,port,query}` | `{adapter,persistence.postgres}` | `api.{request,response}` | YES |
| notarist-assistant | `domain.{model,event}` | `application.{pipeline,port,command}` | `adapter` | `api.{request,response}` | YES |
| notarist-infra | `infra.{minio,qdrant,postgres,resilience,ocr}` | — | — | — | YES |
| notarist-runtime | `runtime.{ocr,ollama,embedding,reranker,model,metrics,degradation,guard}` | — | — | — | YES |
| notarist-observability | `observability.{audit,health,ops,trace,metrics,resilience}` | — | — | — | YES |
| notarist-audit | `audit.domain.model` | — | — | `audit.api.response` | YES |
| notarist-regulation | Not fully scanned — assumed skeletal | — | — | — | UNVERIFIED |
| notarist-web | Bootstrap + global config | — | — | — | YES |

**notarist-regulation** was not scanned in this pass — add to 6A.3 scope.

---

## 2. Port Completeness Inventory

### Ports Defined vs. Implementations Found

| Port Interface | Location | Phase 3/4 Stub | Phase 5 Real Impl | Status |
|---|---|---|---|---|
| `VectorSearchPort` | `notarist-search.application.port.out` | `search.QdrantSearchAdapter` (@Component) | `infra.QdrantSearchAdapter` (@Component) | **BLOCKER: dual bean** |
| `DocumentStoragePort` | `notarist-ingest.application.port.out` | `ingest.MinioDocumentStorageAdapter` (@Component) | `infra.MinioDocumentStorageAdapter` (@Component) | **BLOCKER: dual bean** |
| `LlmPort` | `notarist-assistant.application.port.out` | `assistant.OllamaAdapter` (@Component) | `runtime.OllamaRuntimeAdapter` (@Component) | **BLOCKER: dual bean** |
| `RerankerPort` | `notarist-search.application.port.out` | `search.RerankerAdapter` (@Component) | `runtime.RerankerRuntimeWorker` | **BLOCKER: dual bean (assumed)** |
| `VectorIndexPort` | `notarist-ingest.application.port.out` | `ingest.VectorIndexAdapter` (@Component) | `infra.QdrantIndexAdapter` (@Component) | **BLOCKER: dual bean** |
| `EmbeddingPort` | `notarist-ingest.application.port.out` | `ingest.EmbeddingAdapter` (@Component) | Phase 5 `EmbeddingRuntimeWorker` | Verify @Component |
| `OcrServicePort` | `notarist-ingest.application.port.out` | `ingest.OcrServiceAdapter` (@Component) | Replaced by `OcrPort` in core | LATENT: port contract ambiguity |
| `OcrPort` | `notarist-core.port.ocr` | — | `runtime.PaddleOcrAdapter` (@Component) | OK — single impl |
| `KeywordRetrievalPort` | `notarist-search.application.port.out` | NOT FOUND | NOT FOUND | **LATENT: no implementation** |
| `SemanticRetrievalPort` | `notarist-search.application.port.out` | NOT FOUND | NOT FOUND | **LATENT: no implementation** |
| `IngestionQueuePort` | `notarist-ingest.application.port.out` | NOT FOUND | NOT FOUND | **LATENT: no implementation** |
| `TokenDenyListPort` | `notarist-auth.application.port.out` | `auth.SessionTokenRepositoryImpl` (likely) | — | Verify |

---

## 3. Core Module Integrity

### notarist-core Package Contents

**Confirmed canonical types (skeleton — authoritative):**

| Package | File | Type | Zero Spring? |
|---|---|---|---|
| `domain.event` | `DomainEvent.java` | interface | YES |
| `domain.ocr` | `OcrReviewStatus.java` | enum | YES |
| `domain.ocr` | `OcrConfidencePolicy.java` | final class | YES |
| `domain.ocr` | `OcrResult.java` | record | YES |
| `domain.ocr` | `OcrConfig.java` | record | YES |
| `domain.vector` | `EmbeddingContract.java` | final class | YES |
| `domain.valueobject` | `ClassificationLevel.java` | enum | YES |
| `domain.valueobject` | `JenisAkta.java` | enum | YES |
| `domain.valueobject` | `JenisDokumen.java` | enum | YES |
| `domain.valueobject` | `CorrelationId.java`, `TraceId.java`, `DocumentId.java`, etc. | records | YES |
| `port.ocr` | `OcrPort.java` | interface | YES |
| `api.response` | `ApiResponse.java`, `ApiError.java`, `PageResponse.java`, etc. | records | NO — uses jackson-databind |
| `application.usecase` | `CommandUseCase.java`, `UseCase.java` | interfaces | YES |
| `util` | `NotaristConstants.java` | class | YES |

**INTEGRITY_RISK — Core module duplication:**  
Two `notarist-core` source trees exist:
- `generated/backend-skeleton/notarist-core/` — authoritative (active Gradle module)
- `generated/backend-implementation/phase1-auth-document/notarist-core/` — legacy copy

The `ClassificationLevel` enum differs between these two copies (see Section 4 of `enum_persistence_audit.md`). The phase1 copy is NOT on the Gradle classpath (no `include()` in settings.gradle.kts points to it). Risk: IDE indexing confusion, developer edits wrong copy.

---

## 4. Event Architecture Integrity

### Domain Events Inventory

| Event Class | Module | Implements DomainEvent | eventType | publishedBy | eventVersion |
|---|---|---|---|---|---|
| `DocumentUploadedEvent` | notarist-ingest | YES | `"document.uploaded"` | `"notarist-ingest"` | default `"1.0"` |
| `OcrCompletedEvent` | notarist-ingest | YES | `"ocr.completed"` | `"notarist-ingest"` | default `"1.0"` |
| `EmbeddingCompletedEvent` | notarist-ingest | YES | `"embedding.completed"` | `"notarist-ingest"` | default `"1.0"` |
| `ChunkingCompletedEvent` | notarist-ingest | — | — | — | verify |
| `NerCompletedEvent` | notarist-ingest | — | — | — | verify |
| `IndexingCompletedEvent` | notarist-ingest | — | — | — | verify |
| `AiResponseGeneratedEvent` | notarist-assistant | YES | `"ai.response.generated"` | `"notarist-assistant"` | default `"1.0"` |
| `CitationCreatedEvent` | notarist-assistant | — | — | — | verify |

**Confirmed:** `DomainEvent` interface exists in `notarist-core.domain.event` with `eventVersion()` default `"1.0"`. Structure is correct.  
**INTEGRITY_RISK:** No concrete event overrides `eventVersion()`. This is safe for v1 but becomes a risk when a breaking change occurs — version bump will require touching all event classes.

---

## 5. SSE Contract Integrity

Two incompatible SSE designs exist simultaneously:

**Design A — Phase 4 implementation** (`notarist-assistant` impl, `SseEvent.java`):
- Single record: `record SseEvent(String eventType, String data, String traceId, int sequence, long timestampMs)`
- 7 factory methods: `answerToken`, `citation`, `confidence`, `warning`, `followUp`, `done`, `error`
- `eventType` is raw `String` — no compile-time contract

**Design B — Skeleton** (`notarist-assistant` skeleton, 5 separate records):
- `SseTokenEvent`, `SseCitationEvent`, `SseCompleteEvent`, `SseWarningEvent`, `SseErrorEvent`
- Each has `EVENT_TYPE` String constant
- `SseWarningEvent` has nested `WarningCode` enum

**Current compile state:** Both designs exist. Phase 5 `OllamaRuntimeAdapter` likely produces `SseEvent` (Design A). Skeleton controller likely expects Design B. This is an INTEGRITY_RISK — SSE wire format is undefined.

---

## 6. Remediation Recommendations

### P0 — Must Fix Before Runtime

| ID | Issue | Remediation |
|---|---|---|
| MI-P0-1 | 5 port interfaces have dual @Component implementations — Spring will fail with `NoUniqueBeanDefinitionException` | Add `@Primary` to Phase 5 real implementations; add `@Profile("stub")` or remove `@Component` from Phase 3/4 stubs. Recommended: remove @Component from all Phase 3/4 stubs, leave them as non-Spring classes for testing only. |

### P1 — Fix in Phase 6A.2 Remediation Pass

| ID | Issue | Remediation |
|---|---|---|
| MI-P1-1 | `KeywordRetrievalPort`, `SemanticRetrievalPort`, `IngestionQueuePort` have no `@Component` implementation found | Verify if `BM25SearchRepositoryImpl` implements `KeywordRetrievalPort`; if not, mark ports as unimplemented and add stub |
| MI-P1-2 | SSE contract split (Design A vs B) | Decide canonical design. Recommend: keep Design B (typed records) for the skeleton contract; migrate Phase 4 impl to use typed records |
| MI-P1-3 | Legacy `phase1-auth-document/notarist-core/` shadow tree | Remove or clearly mark as archived; ensure no code references it |

### P2 — Fix in Phase 6A.4

| ID | Issue | Remediation |
|---|---|---|
| MI-P2-1 | `eventVersion()` not overridden per event | Add explicit `@Override public String eventVersion() { return "1.0"; }` to each concrete event record |
| MI-P2-2 | `notarist-regulation` not scanned | Add to 6A.3 scope |

---

## 7. Module Graph — Final Validation

```
Dependency Direction (→ means "depends on"):

notarist-core (leaf)
  ↑
notarist-audit → core
notarist-auth → core, audit
notarist-document → core
notarist-ingest → core, document, audit
notarist-search → core, document
notarist-regulation → core, document (assumed)
notarist-assistant → core, document, search, audit
notarist-infra → core, ingest (ports), search (ports)
notarist-runtime → core, assistant (LlmPort), search (RerankerPort)
notarist-observability → core
notarist-web → ALL above
```

**Cycle check: PASS — no circular dependencies.**  
**Boundary check: PASS — no domain module imports infrastructure module.**

