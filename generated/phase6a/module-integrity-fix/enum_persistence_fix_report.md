# PHASE 6A.2-FIX — Enum Persistence Fix Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P1

---

## Problem Summary

JPA entity enum-valued fields were mapped as raw `String` columns without `@Enumerated(EnumType.STRING)`. This meant:
- No compile-time type safety between the Java field and the column value
- Repository query methods accepted `String` params instead of typed enums, allowing invalid values
- Mapper code required manual `.name()` / `.valueOf()` conversion boilerplate at every callsite
- Hibernate defaulted to `ORDINAL` strategy for any field declared as an enum type without explicit annotation (latent risk if field type ever changed)

---

## Affected Entities — Pre-Fix

| Entity | Field | Old Java Type | Column | Risk |
|---|---|---|---|---|
| `DocumentLegalJpaEntity` | `documentType` | `String` | `DOCUMENT_TYPE` | Invalid value accepted at compile time |
| `DocumentLegalJpaEntity` | `jenisAkta` | `String` | `JENIS_AKTA` | Invalid value accepted at compile time |
| `DocumentLegalJpaEntity` | `classificationLevel` | `String` | `CLASSIFICATION_LEVEL` | Invalid value accepted at compile time |
| `DocumentLegalJpaEntity` | `status` | `String` | `STATUS` | Invalid value accepted at compile time |
| `IngestionJobJpaEntity` | `documentType` | `String` | `DOCUMENT_TYPE` | Invalid value accepted at compile time |
| `IngestionJobJpaEntity` | `classificationLevel` | `String` | `CLASSIFICATION_LEVEL` | Invalid value accepted at compile time |
| `IngestionJobJpaEntity` | `pipelineStatus` | `String` | `PIPELINE_STATUS` | Invalid value accepted at compile time |
| `IngestionJobJpaEntity` | `overallStatus` | `String` | `OVERALL_STATUS` | Invalid value accepted at compile time |
| `UserRoleJpaEntity` | `roleCode` | `String` | `ROLE_CODE` | Invalid value accepted at compile time |

---

## Fix Applied: Typed Enum Fields with `@Enumerated(EnumType.STRING)`

### `DocumentLegalJpaEntity`

| Field | Old Type | New Type | Annotation Added |
|---|---|---|---|
| `documentType` | `String` | `JenisDokumen` | `@Enumerated(EnumType.STRING)` |
| `jenisAkta` | `String` | `JenisAkta` | `@Enumerated(EnumType.STRING)` |
| `classificationLevel` | `String` | `ClassificationLevel` | `@Enumerated(EnumType.STRING)` |
| `status` | `String` | `DocumentStatus` | `@Enumerated(EnumType.STRING)` |
| `checksumSha256` | — | — | Added `updatable = false` |

Constructor, all getters, and `setStatus()` updated to use enum types.

### `IngestionJobJpaEntity`

| Field | Old Type | New Type | Annotation Added |
|---|---|---|---|
| `documentType` | `String` | `JenisDokumen` | `@Enumerated(EnumType.STRING)` |
| `classificationLevel` | `String` | `ClassificationLevel` | `@Enumerated(EnumType.STRING)` |
| `pipelineStatus` | `String` | `PipelineStatus` | `@Enumerated(EnumType.STRING)` |
| `overallStatus` | `String` | `JobStatus` | `@Enumerated(EnumType.STRING)` |
| `checksumSha256` | — | — | Added `updatable = false` |

`failureStage` remains `String` (nullable, no enum — intentional).

### `UserRoleJpaEntity`

| Field | Old Type | New Type | Annotation Added |
|---|---|---|---|
| `roleCode` | `String` | `Role` | `@Enumerated(EnumType.STRING)` |

---

## Cascade Fixes (Repository + Mapper)

The entity field type changes required cascading updates to all callers:

### `IngestionJobJpaRepository`
- `findByPipelineStatusAndTenantId(String, ...)` → `findByPipelineStatusAndTenantId(PipelineStatus, ...)`
- JPQL `WHERE j.pipelineStatus = 'FAILED'` → `WHERE j.pipelineStatus = :failedStatus` with `@Param("failedStatus") PipelineStatus failedStatus`

### `IngestionJobRepositoryImpl`
- `toEntity()`: removed `.name()` on all enum fields — passes enums directly
- `toDomain()`: removed `.valueOf()` on all enum fields — reads enums directly from entity getters
- `findByStatusAndTenantId()`: passes `status` enum directly (was `status.name()`)
- `findFailedAndReadyForRetry()`: passes `PipelineStatus.FAILED` enum as first argument

### `DocumentLegalJpaRepository`
- `findByTenantIdWithFilters()` / `countByTenantIdWithFilters()`:
  - `:documentType` `String` → `JenisDokumen`
  - `:status` `String` → `DocumentStatus`
  - `:allowedLevels` `List<String>` → `List<ClassificationLevel>`
  - `:maxClearanceLevel` `Integer` → `ClassificationLevel` (IS NULL sentinel preserved)

### `DocumentLegalRepositoryImpl`
- `filter.documentType().name()` → `filter.documentType()` (pass enum directly)
- `filter.status().name()` → `filter.status()`
- `clearanceOrdinal` (Integer) replaced by `maxClearance` (ClassificationLevel)
- `computeAllowedLevels()` return type: `List<String>` → `List<ClassificationLevel>` (removed `Enum::name` mapping)
- `entity.setStatus(status.name())` → `entity.setStatus(status)`

### `DocumentLegalMapper`
- `toDomain()`: removed `JenisDokumen.valueOf(e.getDocumentType())` → `e.getDocumentType()`
- `toDomain()`: removed `JenisAkta.valueOf(e.getJenisAkta())` → `e.getJenisAkta()`
- `toDomain()`: removed `ClassificationLevel.valueOf(e.getClassificationLevel())` → `e.getClassificationLevel()`
- `toDomain()`: removed `DocumentStatus.valueOf(e.getStatus())` → `e.getStatus()`
- `toEntity()`: removed `.name()` from all enum fields — passes enums directly to constructor

---

## Files Modified

| File | Change |
|---|---|
| `phase1-auth-document/notarist-document/.../DocumentLegalJpaEntity.java` | 4 fields: String→enum + `@Enumerated(STRING)`; `updatable=false` on checksum |
| `phase1-auth-document/notarist-document/.../DocumentLegalJpaRepository.java` | Enum-typed query params; added imports |
| `phase1-auth-document/notarist-document/.../DocumentLegalRepositoryImpl.java` | Removed `.name()` conversions; `List<ClassificationLevel>` for allowedLevels |
| `phase1-auth-document/notarist-document/.../DocumentLegalMapper.java` | Removed `.name()`/`.valueOf()` in `toDomain()`/`toEntity()` |
| `phase1-auth-document/notarist-auth/.../UserRoleJpaEntity.java` | `roleCode`: String→Role + `@Enumerated(STRING)` |
| `phase2-ingest/notarist-ingest/.../IngestionJobJpaEntity.java` | 4 fields: String→enum + `@Enumerated(STRING)`; `updatable=false` on checksum |
| `phase2-ingest/notarist-ingest/.../IngestionJobJpaRepository.java` | Enum-typed params; JPQL parameterized `:failedStatus` |
| `phase2-ingest/notarist-ingest/.../IngestionJobRepositoryImpl.java` | Removed `.name()`/`.valueOf()`; enum args to queries |

---

## Post-Fix Enum Persistence Status

| Entity | Field | Enum Type | `@Enumerated(STRING)` | Status |
|---|---|---|---|---|
| `DocumentLegalJpaEntity` | `documentType` | `JenisDokumen` | YES | PASS |
| `DocumentLegalJpaEntity` | `jenisAkta` | `JenisAkta` | YES | PASS |
| `DocumentLegalJpaEntity` | `classificationLevel` | `ClassificationLevel` | YES | PASS |
| `DocumentLegalJpaEntity` | `status` | `DocumentStatus` | YES | PASS |
| `IngestionJobJpaEntity` | `documentType` | `JenisDokumen` | YES | PASS |
| `IngestionJobJpaEntity` | `classificationLevel` | `ClassificationLevel` | YES | PASS |
| `IngestionJobJpaEntity` | `pipelineStatus` | `PipelineStatus` | YES | PASS |
| `IngestionJobJpaEntity` | `overallStatus` | `JobStatus` | YES | PASS |
| `UserRoleJpaEntity` | `roleCode` | `Role` | YES | PASS |

**No ORDINAL risk. No invalid-string risk. No `.name()`/`.valueOf()` boilerplate remaining in JPA layer.**
