# PHASE 6A.2 — Enum Persistence Audit
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** All enum types — persistence mapping, cross-module duplication, ordinal drift risk, DB column alignment

---

## CRITICAL PREAMBLE

> Enum ordinal drift is one of the most silent data-corruption vectors in JPA applications.  
> If an enum field is stored as ORDINAL (the JPA default) and a new constant is inserted  
> anywhere except the END of the enum, all existing rows are silently misread.  
> This audit covers every enum in the project and its persistence mapping.

---

## 1. Enum Inventory — All Modules

| Enum | Module | Location | Ordinal Risk | Used in JPA Entity? | @Enumerated present? |
|---|---|---|---|---|---|
| `ClassificationLevel` | notarist-core (skeleton) | `domain.valueobject` | YES — carries `int level` field | NO (stored as String in entities) | N/A |
| `ClassificationLevel` | notarist-core (phase1) | `domain.valueobject` | YES | NO | N/A |
| `JenisDokumen` | notarist-core (skeleton) | `domain.valueobject` | YES | NO (stored as String in entities) | N/A |
| `JenisAkta` | notarist-core (skeleton) | `domain.valueobject` | YES | NO (stored as String in entities) | N/A |
| `OcrReviewStatus` | notarist-core (skeleton) | `domain.ocr` | YES | NO | N/A |
| `OcrReviewStatus` | notarist-infra (phase5) | `infra.ocr` | YES | NO | N/A — deprecated |
| `DocumentStatus` | notarist-document (skeleton) | `domain.model` | YES | **NO — stored as `String status` in DocumentLegalJpaEntity** | MISSING |
| `JobStatus` | notarist-ingest (skeleton) | `domain.model` | YES | **NO — stored as `String overallStatus` in IngestionJobJpaEntity** | MISSING |
| `JobStatus` | notarist-ingest (phase2) | `domain.model` | YES — same constants, adds `isTerminal()` | — | — |
| `PipelineStatus` | notarist-ingest (phase2/skeleton) | `domain.model` | YES | **NO — stored as `String pipelineStatus` in IngestionJobJpaEntity** | MISSING |
| `PipelineStage` | notarist-ingest (skeleton) | `domain.model` | YES | NO — not in entity | N/A |
| `Role` | notarist-auth (skeleton) | `domain.model` | YES | **NO — stored as `String roleCode` in UserRoleJpaEntity** | MISSING |
| `AuditEventType` | notarist-audit (skeleton) | `domain.model` | YES | Not scanned — verify | UNKNOWN |
| `AuditOutcome` | notarist-audit (skeleton) | `domain.model` | YES | Not scanned — verify | UNKNOWN |
| `SearchIntent` | notarist-search (skeleton+phase3) | `domain.model` | YES | NO — not in any entity | N/A |
| `RetrievalReason` | notarist-search (phase3) | `domain.model` | YES | NO | N/A |
| `AnswerConfidence` | notarist-assistant (phase4) | `domain.model` | YES | NO | N/A |
| `AssistantSafetyMode` | notarist-assistant (phase4) | `domain.model` | YES | NO | N/A |
| `GroundingCoverage` | notarist-assistant (skeleton) | `domain.model` | YES | NO | N/A |
| `ModelProvider` | notarist-runtime (phase5b) | `model` | YES | NO | N/A |

---

## 2. JPA Entity — Enum Field Mapping Analysis

### 2.1 DocumentLegalJpaEntity — All Enum-Valued Fields Stored as String

**Entity:** `notarist-document.infrastructure.persistence.oracle.DocumentLegalJpaEntity`  
**Table:** `DOKUMEN_LEGAL` (schema: NOTARIST)

| Field Name | Java Type in Entity | Domain Enum | Column DDL Type | Risk |
|---|---|---|---|---|
| `documentType` | `String` | `JenisDokumen` | VARCHAR2(50) | INTEGRITY_RISK |
| `jenisAkta` | `String` | `JenisAkta` | VARCHAR2(50) | INTEGRITY_RISK |
| `classificationLevel` | `String` | `ClassificationLevel` | VARCHAR2(50) | INTEGRITY_RISK |
| `status` | `String` | `DocumentStatus` | VARCHAR2(50) | INTEGRITY_RISK |

**Finding:** All 4 fields are stored as raw `String`, not as enum types. There is NO `@Enumerated` annotation anywhere because the Java field type is `String`, not an enum.

**Consequence:**
- No compile-time safety — any `String` value can be stored
- No JPA-level constraint enforcement
- Manual `.name()` / `.valueOf()` calls in repository implementation are implicit and untested
- Schema drift: if `DocumentStatus` gains a new constant, no migration needed (String) but also no constraint prevents invalid values

**Severity:** INTEGRITY_RISK (not BLOCKER — no ORDINAL risk since fields are not enum-typed)

**Remediation:** Convert fields to enum types with `@Enumerated(EnumType.STRING)`:
```java
// BEFORE
@Column(name = "STATUS", length = 50)
private String status;

// AFTER
@Enumerated(EnumType.STRING)
@Column(name = "STATUS", length = 50)
private DocumentStatus status;
```

---

### 2.2 IngestionJobJpaEntity — All Enum-Valued Fields Stored as String

**Entity:** `notarist-ingest.infrastructure.persistence.oracle.IngestionJobJpaEntity`  
**Table:** `INGESTION_JOB` (schema: NOTARIST)

| Field Name | Java Type in Entity | Domain Enum | Column DDL Type | Risk |
|---|---|---|---|---|
| `pipelineStatus` | `String` | `PipelineStatus` | VARCHAR2(50) | INTEGRITY_RISK |
| `overallStatus` | `String` | `JobStatus` | VARCHAR2(50) | INTEGRITY_RISK |
| `classificationLevel` | `String` | `ClassificationLevel` | VARCHAR2(50) | INTEGRITY_RISK |
| `failureStage` | `String` | `PipelineStage` (nullable) | VARCHAR2(50) | INTEGRITY_RISK |

Same pattern as above — raw String storage. Adds complication: `setters` accept raw String, allowing callers to pass arbitrary values:
```java
// This compiles and persists silently — no validation
entity.setPipelineStatus("UNKNOWN_STATUS_NOT_IN_ENUM");
```

**Remediation:** Same pattern — convert to typed enum fields with `@Enumerated(EnumType.STRING)`.

---

### 2.3 UserRoleJpaEntity — Role Stored as String

**Entity:** `notarist-auth.infrastructure.persistence.oracle.UserRoleJpaEntity`  
**Table:** `USER_ROLE_MAP` (schema: NOTARIST)

| Field Name | Java Type | Domain Enum | Risk |
|---|---|---|---|
| `roleCode` | `String` | `Role` | INTEGRITY_RISK |

**Additional risk:** `Role` enum carries `ClassificationLevel defaultClearance` — this field is the authoritative source for access control decisions. If `roleCode` is stored/loaded incorrectly, access control is compromised.

**Remediation:**
```java
@Enumerated(EnumType.STRING)
@Column(name = "ROLE_CODE", length = 50, nullable = false)
private Role roleCode;
```

---

## 3. Enum Duplication Analysis

### 3.1 ClassificationLevel — Two Non-Identical Versions

| Location | Constants | Has `int level` field | Has `isAtLeast()` method |
|---|---|---|---|
| `notarist-core` skeleton (authoritative) | PUBLIC(0), INTERNAL(1), CONFIDENTIAL(2), STRICTLY_CONFIDENTIAL(3) | YES | YES |
| `notarist-core` phase1 (legacy) | PUBLIC, INTERNAL, CONFIDENTIAL, STRICTLY_CONFIDENTIAL | NO | NO |

**BLOCKER-level concern for legacy usage:** If any code compiles against the phase1 copy and calls `isAtLeast()`, it will fail to compile. If any code stores the `int level` field, it doesn't exist in the phase1 version.

However, since only the skeleton core is on the Gradle classpath, this is a developer-confusion risk, not a current compile blocker. **The phase1 copy must be archived or deleted.**

### 3.2 OcrReviewStatus — Duplicate Across Core and Infra

| Location | Constants |
|---|---|
| `notarist-core.domain.ocr.OcrReviewStatus` (NEW — Phase 6A.1-FIX) | ACCEPTED, LOW_CONFIDENCE_REVIEW, REJECTED |
| `notarist-infra.ocr.OcrReviewStatus` (deprecated wrapper) | ACCEPTED, LOW_CONFIDENCE_REVIEW, REJECTED |

Both have identical constants. The infra version has a migration comment added in Phase 6A.1-FIX. No code should import the infra version. Verified: `PaddleOcrAdapter` no longer imports it.

**LATENT_RISK:** If any other file in notarist-infra imports `com.notarist.infra.ocr.OcrReviewStatus`, it will compile (infra is on classpath) but creates an implicit dependency on a deprecated type.

**Remediation:** Scan all files in notarist-infra for imports of `com.notarist.infra.ocr.OcrReviewStatus`; replace with `com.notarist.core.domain.ocr.OcrReviewStatus`. Then delete infra version.

### 3.3 JobStatus — Additive Duplication (Safe)

| Location | Constants | Extra method |
|---|---|---|
| `notarist-ingest` skeleton | PENDING, PROCESSING, COMPLETED, FAILED, DLQ | none |
| `notarist-ingest` phase2 | PENDING, PROCESSING, COMPLETED, FAILED, DLQ | `isTerminal()` |

Same constants, same ordinals. Phase2 version adds `isTerminal()`. If both are in `srcDirs`, compile error (duplicate class). If only one is active, the other is dead code. This needs `srcDirs` verification.

### 3.4 SearchIntent — Potential Duplication

| Location |
|---|
| `notarist-search` skeleton: `com.notarist.search.domain.model.SearchIntent` |
| `notarist-search` phase3: `com.notarist.search.domain.model.SearchIntent` |

Same fully-qualified name in different source roots of the same module. Same risk as `JobStatus` — verify srcDirs.

---

## 4. Enum Ordinal Risk Summary

Since ALL JPA entities store enum-valued fields as raw `String` (not typed enum fields), there is currently **NO `@Enumerated(EnumType.ORDINAL)` risk** — ordinal storage requires the Java field to be of enum type, which it is not.

**However, this creates the inverse risk:** the database has no JPA-level type constraint. Any migration that inadvertently changes column values to integers (ordinals) would silently corrupt data with no compile-time protection.

**The recommended fix is still to add `@Enumerated(EnumType.STRING)` with typed enum fields**, because:
1. It provides compile-time safety
2. It prevents accidental ordinal storage in the future
3. It documents intent — a reader can see what valid values are
4. JPA will throw `IllegalArgumentException` if an invalid enum string comes from the DB — fail-fast is safer than silent `null`

---

## 5. Remediation Priority

| ID | Severity | Entity | Fields | Action |
|---|---|---|---|---|
| EPA-I1 | INTEGRITY_RISK | `DocumentLegalJpaEntity` | `status`, `documentType`, `jenisAkta`, `classificationLevel` | Add enum types + `@Enumerated(EnumType.STRING)` |
| EPA-I2 | INTEGRITY_RISK | `IngestionJobJpaEntity` | `pipelineStatus`, `overallStatus`, `classificationLevel`, `failureStage` | Add enum types + `@Enumerated(EnumType.STRING)` |
| EPA-I3 | INTEGRITY_RISK | `UserRoleJpaEntity` | `roleCode` | Change to `Role` type + `@Enumerated(EnumType.STRING)` |
| EPA-I4 | INTEGRITY_RISK | `OcrReviewStatus` infra copy | — | Scan infra for remaining imports; delete infra copy |
| EPA-I5 | INTEGRITY_RISK | `ClassificationLevel` phase1 copy | — | Archive or delete phase1 copy |
| EPA-L1 | LATENT_RISK | `JobStatus` / `SearchIntent` duplicates | — | Verify srcDirs; remove duplicate from source roots |
