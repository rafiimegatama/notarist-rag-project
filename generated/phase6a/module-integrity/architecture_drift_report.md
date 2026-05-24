# PHASE 6A.2 — Architecture Drift Report
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** Cross-cutting architecture drift — hexagonal rule violations, layer leakage, shared contract inconsistency, module boundary erosion, technical debt accumulation

---

## Executive Summary

| Severity | Count | Finding |
|---|---|---|
| BLOCKER | 1 | Dual @Component beans will prevent Spring context from starting |
| INTEGRITY_RISK | 6 | OcrServicePort/OcrPort dualism; SSE contract split; ClassificationLevel divergence; OcrConfig type mismatch; raw String enum fields; StreamMode not typed |
| RUNTIME_RISK | 2 | Dual DataSource not @Primary; `@Lob` Oracle misconfiguration risk |
| LATENT_RISK | 5 | Shadow core module; wildcard imports (30+); EmbeddingCompletedEvent hardcoded string; enable-preview flag; duplicate class names across source roots |
| ARCHITECTURAL DEBT | 3 | No aggregate root pattern, no repository interface in domain layer for some modules, JPQL leaking infrastructure concerns |
| PASS | 7 | No circular dependencies; hexagonal structure correct; DomainEvent interface properly defined; core zero-Spring verified; OCR boundary fixed; value objects validated; Oracle/Postgres split clean |

---

## 1. Hexagonal Architecture Compliance — Drift Points

### Rule: Domain must not import infrastructure
**Status: PASS**  
No `@Entity`, Spring Data, or infrastructure annotation found in any `domain.*` package.

### Rule: Application layer must not import infrastructure
**Status: PASS (verified for ingest, auth, document, search, assistant)**  
Application services (`*Service.java`, `*UseCase.java`) use port interfaces only.

### Rule: Core module has zero Spring dependency
**Status: PASS**  
`notarist-core` build depends on `jakarta.validation`, `jackson-databind` — no `spring-*` compile dependency. New OCR/vector types added in Phase 6A.1-FIX are Spring-free.

### Rule: Port interfaces defined in domain/application, not infrastructure
**Status: PASS**  
All ports (`VectorSearchPort`, `DocumentStoragePort`, `LlmPort`, `RerankerPort`, `VectorIndexPort`, `OcrPort`) are defined in application or core packages, not infrastructure.

### Rule: Adapters implement ports, never the other direction
**Status: PASS** (with one exception below)

**Exception — OcrServicePort contract mismatch:**  
`OcrServicePort` (ingest application layer) defines its own nested `OcrConfig` and `OcrResult` types. `OcrPort` (core) defines canonical versions. `PaddleOcrAdapter` implements `OcrPort`, not `OcrServicePort`. The ingest application uses `OcrServicePort`. These two ports cannot be substituted for each other — they have different method signatures at the type level:

```
OcrServicePort.extractText(String, OcrServicePort.OcrConfig): OcrServicePort.OcrResult
OcrPort.extractText(String, core.OcrConfig): core.OcrResult
```

`OcrServicePort.OcrConfig`: `(int dpi, String language, boolean enhanceContrast)`  
`core.OcrConfig`: `(String language, boolean enableHandwriting, boolean enableTables, float minConfidenceThreshold)`

**Different fields. This is a contract split, not just a naming issue.**

The ingest pipeline will not work with `PaddleOcrAdapter` until one of these is resolved:
1. Migrate `OcrServicePort` to use `core.OcrConfig` / `core.OcrResult` (breaking)
2. Keep `OcrServicePort` for ingest-internal use; add an adapter/mapper in ingest that translates between the two types
3. Delete `OcrServicePort` entirely; update ingest pipeline to use `OcrPort`

**This is the highest-priority integration contract fix in the project.**

---

## 2. Module Boundary Drift — Cross-Phase Contamination

### 2.1 Shadow Core Module

The project has two copies of `notarist-core`:
```
generated/backend-skeleton/notarist-core/           ← authoritative (on Gradle classpath)
generated/backend-implementation/phase1-auth-document/notarist-core/  ← legacy (NOT on classpath)
```

The phase1 copy contains older versions of:
- `ClassificationLevel` — same constants, no `int level` field, no `isAtLeast()` method
- `ApiResponse`, `ApiMeta` — may differ from skeleton versions
- `AuditEventPayload` — exists in phase1 only, not in skeleton

**INTEGRITY_RISK:** Developers editing the wrong copy will produce changes that compile in isolation but don't affect the running application. This has happened before — the phase1 `ClassificationLevel` diverged from the skeleton version.

**Action:** Archive `generated/backend-implementation/phase1-auth-document/notarist-core/` by renaming to `notarist-core-ARCHIVED` or adding a `README` explaining it is not on the Gradle classpath.

### 2.2 Duplicate Source Classes — Same Fully-Qualified Name

Files that appear in both skeleton and phase impl source roots with the same FQN:

| FQN | Present In |
|---|---|
| `com.notarist.ingest.application.command.InitiateIngestionCommand` | skeleton + phase2 |
| `com.notarist.ingest.api.response.UploadUrlResponse` | skeleton + phase2 |
| `com.notarist.search.domain.model.SearchIntent` | skeleton + phase3 |
| `com.notarist.ingest.domain.model.JobStatus` | skeleton + phase2 |

If the skeleton and phase-impl source roots are BOTH on `srcDirs` in the build, this is a compile error: "duplicate class". If only one source root is active, the other class is dead code — but IDE tooling will see both and show false navigation targets.

**Verify per module: which source root is active in `build.gradle.kts` `sourceSets.main.java.srcDirs`.**

---

## 3. Port Contract Drift — Canonical vs Ingest-Internal

| Port | Defined In | Canonical? | Used By |
|---|---|---|---|
| `OcrPort` | `notarist-core.port.ocr` | YES — Phase 6A.1 canonical | `PaddleOcrAdapter` (runtime) |
| `OcrServicePort` | `notarist-ingest.application.port.out` | NO — should be deleted | `OcrServiceAdapter` (ingest stub), ingest pipeline |
| `EmbeddingPort` | `notarist-ingest.application.port.out` | INGEST-INTERNAL | `EmbeddingAdapter` stub, ingest pipeline |
| `EmbeddingPort` (core) | — | NOT YET CREATED | — |

`EmbeddingPort` is currently ingest-internal only, which is acceptable as long as `EmbeddingRuntimeWorker` in `notarist-runtime` is accessed via a port in `notarist-search` or `notarist-assistant` rather than directly by ingest. Verify the embedding call chain.

---

## 4. SSE Contract Drift — Two Parallel Design Choices

The project has two parallel SSE event designs that drifted during Phase 3→4 development:

**Timeline:**
- Phase 3/4 built `SseEvent` (Design A) — single envelope, String eventType
- Phase 5 skeleton defines 5 typed records (Design B) — typed SSE events per concern
- Neither design was reconciled before Phase 5B implementation

**Impact:**
- Phase 5B `OllamaRuntimeAdapter` streams tokens to `ResponseStreamer`
- `ResponseStreamer` serializes to SSE — which design does it use?
- The client-side contract (frontend React Native) needs ONE stable format

This drift will manifest as a runtime wire format inconsistency when Phase 5B streaming is tested.

---

## 5. Wildcard Imports — 30+ Files

From Phase 6A.1 build scan, 30+ files use wildcard imports (`import com.notarist.infra.*`, `import org.springframework.http.*`). This is not a compile blocker but:

1. Increases compile time (javac must resolve all wildcards)
2. Prevents easy detection of unused imports
3. Hides implicit dependencies — a file importing `com.notarist.infra.*` may be using 1 or 10 classes from infra
4. Makes architecture rule enforcement (`ArchUnit`) harder

**Phase 6A.4 scope item.** Not blocking Phase 6A.2.

---

## 6. EmbeddingCompletedEvent — String Constant Drift

```java
public String embeddingModel() { return "bge-m3"; }
```

This hardcodes `"bge-m3"` instead of `EmbeddingContract.EMBEDDING_MODEL`. When the embedding model is upgraded, this event will still report `"bge-m3"` in the audit log even after migration to a new model. Audit log replay will produce incorrect model attribution.

**Remediation:** `return EmbeddingContract.EMBEDDING_MODEL;`

---

## 7. Architectural Technical Debt Inventory

### 7.1 No Aggregate Root Pattern

None of the JPA entities implement an aggregate root pattern. `UserJpaEntity` directly exposes `roles` as a `List<UserRoleJpaEntity>` (mutable). A proper aggregate root would:
- Hide the internal collection
- Expose domain-meaningful methods: `addRole(Role)`, `removeRole(Role)`, `hasRole(Role)`
- Prevent direct access to `UserRoleJpaEntity`

This is not a compile blocker — it's an OOP/DDD discipline issue. Relevant for future iterations.

### 7.2 Repository Interface vs Spring Data Interface Confusion

`UserJpaRepository extends JpaRepository<UserJpaEntity, String>` is a Spring Data interface, NOT a domain repository port. The `UserRepositoryImpl` is a `@Component` that wraps this Spring Data interface and implements the domain port.

This is architecturally correct (Spring Data in infrastructure, domain port in application layer). No violation.

### 7.3 JPQL Leaking Infrastructure

`DocumentLegalJpaRepository` uses JPQL with Oracle-specific considerations (VPD filter via `allowedLevels` list). The `maxClearanceLevel: Integer` parameter is a database-level concept (ordinal) mixed into the repository method signature.

This couples the application's filtering logic to Oracle VPD implementation details. This is a design smell but not a current blocker.

---

## 8. Architecture Drift Risk Heatmap

```
┌─────────────────────────────────────────────────────────────────────────┐
│  RISK LEVEL    │  AREA                          │  PHASE TO FIX        │
├─────────────────────────────────────────────────────────────────────────┤
│  BLOCKER       │  Dual @Component stubs+real     │  6A.2 Fix Pass       │
│  INTEGRITY     │  OcrServicePort/OcrPort mismatch│  6A.2 Fix Pass       │
│  INTEGRITY     │  SSE contract split             │  6A.2 Fix Pass       │
│  INTEGRITY     │  String-typed enum fields        │  6A.2 Fix Pass       │
│  INTEGRITY     │  ClassificationLevel divergence  │  6A.2 Fix Pass       │
│  RUNTIME       │  @Primary DataSource missing    │  6A.3 Config         │
│  RUNTIME       │  Flyway default to Oracle risk  │  6A.3 Config         │
│  LATENT        │  Shadow core module             │  6A.4 Cleanup        │
│  LATENT        │  Wildcard imports 30+ files     │  6A.4 Cleanup        │
│  LATENT        │  Duplicate class FQNs           │  6A.4 Cleanup        │
│  LATENT        │  eventVersion default no override│  6A.4 Cleanup        │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Critical Path to Production Readiness

### Ordered by unblocking dependency:

```
1. [BLOCKER]   Remove @Component from all Phase 3/4 stubs
               → Unblocks: Spring context startup
               → Required before: any other testing

2. [INTEGRITY] Resolve OcrServicePort vs OcrPort contract
               → Unblocks: ingest → OCR pipeline end-to-end
               → Required before: Phase 5B integration test

3. [INTEGRITY] Resolve SSE contract (Design A vs B)
               → Unblocks: Phase 5B streaming test
               → Required before: frontend integration

4. [INTEGRITY] Add @Enumerated(EnumType.STRING) to entity fields
               → Unblocks: type-safe persistence
               → Required before: performance/load testing

5. [RUNTIME]   Add @Primary to Oracle DataSource
               → Unblocks: correct JPA routing
               → Required before: first system test

6. [RUNTIME]   Configure Flyway to use PostgreSQL URL explicitly
               → Unblocks: clean migration on startup
               → Required before: first system test
```

---

## 10. Remediation Summary

| ID | Severity | Area | Finding | Remediation |
|---|---|---|---|---|
| AD-B1 | BLOCKER | 5 dual-bean pairs | @Component on stubs AND real implementations | Remove @Component from all Phase 2/3/4 stubs |
| AD-I1 | INTEGRITY_RISK | OcrServicePort/OcrPort | Different OcrConfig types — not interchangeable | Migrate ingest to use core.OcrPort and core.OcrConfig |
| AD-I2 | INTEGRITY_RISK | SSE contract | Two parallel designs (Design A + Design B) | Adopt Design B; add SseEventEnvelope sealed interface |
| AD-I3 | INTEGRITY_RISK | Enum entity fields | 7 enum-valued fields stored as raw String | Add @Enumerated(EnumType.STRING) with typed fields |
| AD-I4 | INTEGRITY_RISK | ClassificationLevel | Two versions with different capabilities | Archive phase1 copy; use skeleton copy exclusively |
| AD-I5 | INTEGRITY_RISK | StreamMode | Raw String in AiResponseGeneratedEvent | Add StreamMode enum to assistant or core |
| AD-I6 | INTEGRITY_RISK | EmbeddingCompletedEvent | Hardcoded "bge-m3" string | Use EmbeddingContract.EMBEDDING_MODEL |
| AD-R1 | RUNTIME_RISK | DataSource | No @Primary on Oracle DataSource | Explicit @Primary on Oracle DataSource bean |
| AD-R2 | RUNTIME_RISK | Flyway | May default to Oracle datasource | Set spring.flyway.url=PostgreSQL explicitly |
| AD-L1 | LATENT_RISK | Shadow core | Phase1 core copy not on classpath, confusing | Archive or delete phase1 core |
| AD-L2 | LATENT_RISK | Duplicate FQNs | Same class names in skeleton + impl | Verify srcDirs; remove or consolidate |
| AD-L3 | LATENT_RISK | Wildcard imports | 30+ files with wildcard | Expand to explicit imports (Phase 6A.4) |
