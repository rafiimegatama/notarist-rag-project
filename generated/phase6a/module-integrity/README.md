# PHASE 6A.2 — Module Integrity Validation
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Status:** COMPLETE — Validation only. No fixes applied.

---

## Report Index

| File | Scope | Critical Findings |
|---|---|---|
| `module_integrity_report.md` | Module structure, port completeness, event architecture, SSE overview | 1 BLOCKER, 5 INTEGRITY_RISK |
| `dto_event_consistency_report.md` | DTO schema, event field completeness, SSE contract analysis, DTO duplication | 4 INTEGRITY_RISK, 1 RUNTIME_RISK |
| `enum_persistence_audit.md` | All enums, JPA persistence mapping, ordinal risk, duplication | 5 INTEGRITY_RISK |
| `record_immutability_audit.md` | Records with mutable fields, compact constructor validation, sealed interface gap | 3 RUNTIME_RISK, 2 LATENT_RISK |
| `adapter_port_integrity_report.md` | Dual @Component blocker, port completeness, OcrServicePort/OcrPort mismatch | 5 BLOCKER, 2 INTEGRITY_RISK |
| `persistence_alignment_report.md` | JPA entities, repositories, Oracle/PostgreSQL routing, JPQL safety | 4 INTEGRITY_RISK, 2 RUNTIME_RISK |
| `architecture_drift_report.md` | Cross-cutting drift, hexagonal compliance, shadow modules, technical debt | 1 BLOCKER, 6 INTEGRITY_RISK |

---

## Consolidated Risk Register

### BLOCKER (must fix before Spring context starts)

| ID | Source Report | Description |
|---|---|---|
| API-B1 | adapter_port | `VectorSearchPort`: Phase3 stub + Phase5 real both @Component |
| API-B2 | adapter_port | `DocumentStoragePort`: Phase2 stub + Phase5 real both @Component |
| API-B3 | adapter_port | `LlmPort`: Phase4 stub + Phase5B real both @Component |
| API-B4 | adapter_port | `VectorIndexPort`: Phase2 stub + Phase5 real both @Component |
| API-B5 | adapter_port | `RerankerPort`: Phase3 stub + Phase5B real both @Component |

**Fix for all 5:** Remove `@Component` from Phase 2/3/4 stub adapter classes.

---

### INTEGRITY_RISK (fix before integration testing)

| ID | Source Report | Description | Priority |
|---|---|---|---|
| AD-I1 | arch_drift | `OcrServicePort.OcrConfig` vs `core.OcrConfig` — different fields, not interchangeable | P0 |
| AD-I2 | arch_drift | SSE dual design — Design A (monolithic SseEvent) vs Design B (5 typed records) | P1 |
| EPA-I1 | enum_persistence | `DocumentLegalJpaEntity` — 4 String fields should be enum-typed with @Enumerated(STRING) | P1 |
| EPA-I2 | enum_persistence | `IngestionJobJpaEntity` — 4 String fields should be enum-typed with @Enumerated(STRING) | P1 |
| EPA-I3 | enum_persistence | `UserRoleJpaEntity.roleCode` — should be `Role` type with @Enumerated(STRING) | P1 |
| EPA-I4 | enum_persistence | `OcrReviewStatus` infra copy not yet deleted — lingering after 6A.1-FIX | P2 |
| EPA-I5 | enum_persistence | `ClassificationLevel` phase1 copy diverges from skeleton | P2 |
| DEC-I1 | dto_event | `OcrCompletedEvent.ocrWarnings/processingMs` diverge from `OcrResult.warnings/durationMs` | P1 |
| DEC-I2 | dto_event | CitationDto duplication: 4 different types across 2 modules | P2 |
| DEC-I3 | dto_event | `streamMode` and `modelId` as raw String in AiResponseGeneratedEvent | P2 |
| PA-I1 | persistence | JPQL `findByTenantIdWithFilters` passes raw String enum params | P2 |
| PA-I2 | persistence | Hardcoded `'FAILED'` literal in IngestionJobJpaRepository JPQL | P1 |

---

### RUNTIME_RISK (fix before system test)

| ID | Source Report | Description |
|---|---|---|
| RIA-R1 | record_immutability | `OcrResult.warnings` — mutable List, defensive copy missing |
| RIA-R2 | record_immutability | `OcrCompletedEvent.ocrWarnings` — mutable List |
| RIA-R3 | record_immutability | `NerCompletedEvent.entitiesExtracted` — mutable Map |
| PA-R1 | persistence | UUID entity IDs: no null/format guard in entity constructors |
| PA-R2 | persistence | Dual DataSource: @Primary not declared — JPA may route to wrong DB |
| PA-L3 | persistence | Flyway may default to Oracle datasource URL |

---

### LATENT_RISK (Phase 6A.4 scope)

| ID | Source Report | Description |
|---|---|---|
| AD-L1 | arch_drift | Shadow `notarist-core` module (phase1-auth-document/notarist-core) not on classpath |
| AD-L2 | arch_drift | Duplicate FQN class names in skeleton + phase impl source roots |
| AD-L3 | arch_drift | 30+ wildcard imports across all modules |
| MI-P2-1 | module_integrity | `eventVersion()` not explicitly overridden per event record |
| DEC-L3 | dto_event | Raw UUID fields in events instead of typed value objects |
| RIA-L1 | record_immutability | `SseCompleteEvent.warnings` — mutable List |
| RIA-L2 | record_immutability | `OcrConfig` range validation missing |
| RIA-L4 | record_immutability | No `SseEventEnvelope` sealed interface unifying SSE types |
| PA-L1 | persistence | No `@Version` optimistic locking on IngestionJobJpaEntity |

---

## Phase 6A.2 — Recommended Fix Sequence

Before proceeding to Phase 6A.3, the following fixes should be applied in order:

```
STEP 1 [P0 — Startup blocker]:
  Remove @Component from all Phase 2/3/4 stub adapters (5 classes)
  → Files: OcrServiceAdapter, EmbeddingAdapter, VectorIndexAdapter, NerServiceAdapter,
           QdrantSearchAdapter (phase3), RerankerAdapter, MinioDocumentStorageAdapter (phase2),
           OllamaAdapter (phase4)

STEP 2 [P0 — Integration contract blocker]:
  Resolve OcrServicePort vs OcrPort type mismatch
  → Option: migrate ingest pipeline to use core.OcrPort and core.OcrConfig
  → Delete OcrServicePort after migration

STEP 3 [P1 — Persistence integrity]:
  Add @Enumerated(EnumType.STRING) with typed enum fields to all 4 entities
  → DocumentLegalJpaEntity: documentType→JenisDokumen, jenisAkta→JenisAkta,
                             classificationLevel→ClassificationLevel, status→DocumentStatus
  → IngestionJobJpaEntity: pipelineStatus→PipelineStatus, overallStatus→JobStatus,
                            classificationLevel→ClassificationLevel, failureStage→PipelineStage
  → UserRoleJpaEntity: roleCode→Role

STEP 4 [P1 — OcrCompletedEvent naming]:
  Rename OcrCompletedEvent.ocrWarnings→warnings, processingMs→durationMs

STEP 5 [P1 — JPQL hardcoded literal]:
  Replace 'FAILED' literal in IngestionJobJpaRepository with PipelineStatus.FAILED.name()

STEP 6 [P1 — Mutable collections]:
  Add List.copyOf() / Map.copyOf() compact constructors to:
  OcrResult, OcrCompletedEvent, NerCompletedEvent

STEP 7 [P1 — SSE contract]:
  Decide canonical SSE design; add SseEventEnvelope sealed interface if Design B adopted
```

---

## Next Phase

**PHASE 6A.3 — Configuration & Contract Validation**

Scope:
- `application.yml` / `application.properties` completeness
- Oracle + PostgreSQL datasource dual-bean configuration
- Flyway + Liquibase exclusive ownership verification
- JNDI/connection pool settings
- Security filter chain configuration
- Actuator endpoints exposure configuration
- `notarist-regulation` module scan (deferred from 6A.2)

**STOP — Wait for approval before proceeding to Phase 6A.3.**
