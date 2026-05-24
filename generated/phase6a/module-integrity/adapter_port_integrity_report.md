# PHASE 6A.2 — Adapter/Port Integrity Report
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** All port interfaces — implementation completeness, duplicate adapter beans, @Primary/@Qualifier usage, stub vs real adapter coexistence

---

## Executive Summary

| Severity | Count | Finding |
|---|---|---|
| BLOCKER | 5 | 5 port interfaces have dual `@Component` implementations — Spring context will fail at startup |
| INTEGRITY_RISK | 2 | OcrServicePort / OcrPort contract ambiguity; EmbeddingPort impl unverified |
| LATENT_RISK | 3 | `KeywordRetrievalPort`, `SemanticRetrievalPort`, `IngestionQueuePort` have no confirmed implementation |
| PASS | 4 | OcrPort (single impl), AssistantAuditPort, TokenDenyListPort, PaddleOcrAdapter boundary |

---

## 1. Dual-Bean Problem — Root Cause Analysis

### How This Happened

The project was built in phases:
- **Phase 2/3/4**: Stub adapters were created in domain modules (`notarist-ingest`, `notarist-search`, `notarist-assistant`) to satisfy ports for end-to-end testing
- **Phase 5/5B**: Real adapters were created in `notarist-infra` and `notarist-runtime` to replace stubs
- Both stub and real adapters kept `@Component` annotation
- When `notarist-web` assembly includes all modules, Spring finds BOTH implementations for each port

### Spring Boot behavior

When Spring Boot sees two `@Component` beans implementing the same interface and no `@Primary` or `@Qualifier` is used, it throws:
```
NoUniqueBeanDefinitionException: No qualifying bean of type 
'com.notarist.search.application.port.out.VectorSearchPort' available: 
expected single matching bean but found 2: 
[qdrantSearchAdapter, com.notarist.infra.qdrant.QdrantSearchAdapter]
```

This is a **startup blocker** — the application will not start.

---

## 2. Dual-Bean Inventory

### 2.1 VectorSearchPort

**Port:** `com.notarist.search.application.port.out.VectorSearchPort`

| Class | Package | Phase | @Component | Type |
|---|---|---|---|---|
| `QdrantSearchAdapter` | `notarist-search.infrastructure.adapter` | Phase 3 | YES | STUB — returns empty List |
| `QdrantSearchAdapter` | `notarist-infra.qdrant` | Phase 5 | YES | REAL — full Qdrant HTTP integration |

**Conflict:** Both implement `VectorSearchPort`. Both are `@Component`. Spring will fail.

**Remediation:**
```java
// Phase 3 stub — remove @Component, add @Profile("stub") if needed for isolated testing
// Phase 5 real — keep @Component, optionally add @Primary for explicit priority
```

---

### 2.2 DocumentStoragePort

**Port:** `com.notarist.ingest.application.port.out.DocumentStoragePort`

| Class | Package | Phase | @Component | Type |
|---|---|---|---|---|
| `MinioDocumentStorageAdapter` | `notarist-ingest.infrastructure.adapter` | Phase 2 | YES | REAL — full MinIO integration |
| `MinioDocumentStorageAdapter` | `notarist-infra.minio` | Phase 5 | YES | REAL (with resilience) |

**Note:** Unlike other pairs, BOTH are real implementations. Phase 5 adds `DegradedModeRegistry` and `NotaristRetryPolicy`. Phase 2 is the simpler direct implementation.

**Conflict:** Both implement `DocumentStoragePort`. Both are `@Component`. Spring will fail.

**Remediation:** Phase 5 `infra.MinioDocumentStorageAdapter` is authoritative (has resilience). Remove `@Component` from Phase 2 version. Add `@Component` + `@Primary` to Phase 5 version.

---

### 2.3 LlmPort

**Port:** `com.notarist.assistant.application.port.out.LlmPort`

| Class | Package | Phase | @Component | Type |
|---|---|---|---|---|
| `OllamaAdapter` | `notarist-assistant.infrastructure.adapter` | Phase 4 | YES | STUB — deterministic Indonesian response |
| `OllamaRuntimeAdapter` | `notarist-runtime.ollama` | Phase 5B | YES | REAL — OkHttp streaming to Ollama |

**Note:** `LlmPort` is also defined in two places:
- `notarist-assistant.application.port.out.LlmPort` (phase4 impl)
- `notarist-assistant.application.port.out.LlmPort` (skeleton)

If both are on `srcDirs`, this is a compile error. Verify `notarist-assistant` build configuration.

**Remediation:** Remove `@Component` from `OllamaAdapter` (Phase 4 stub). Phase 5B `OllamaRuntimeAdapter` is the implementation.

---

### 2.4 VectorIndexPort

**Port:** `com.notarist.ingest.application.port.out.VectorIndexPort`

| Class | Package | Phase | @Component | Type |
|---|---|---|---|---|
| `VectorIndexAdapter` | `notarist-ingest.infrastructure.adapter` | Phase 2 | YES | STUB |
| `QdrantIndexAdapter` | `notarist-infra.qdrant` | Phase 5 | YES | REAL |

**Remediation:** Remove `@Component` from Phase 2 stub.

---

### 2.5 RerankerPort

**Port:** `com.notarist.search.application.port.out.RerankerPort`

| Class | Package | Phase | @Component | Type |
|---|---|---|---|---|
| `RerankerAdapter` | `notarist-search.infrastructure.adapter` | Phase 3 | YES | STUB |
| `RerankerRuntimeWorker` | `notarist-runtime` | Phase 5B | YES (assumed) | REAL |

**Action Required:** Verify `RerankerRuntimeWorker` has `@Component`. If yes, remove from Phase 3 stub.

---

### 2.6 EmbeddingPort

**Port:** `com.notarist.ingest.application.port.out.EmbeddingPort`

| Class | Package | Phase | @Component | Type |
|---|---|---|---|---|
| `EmbeddingAdapter` | `notarist-ingest.infrastructure.adapter` | Phase 2 | YES | STUB |
| `EmbeddingRuntimeWorker` | `notarist-runtime.embedding` | Phase 5B | ? | REAL |

**Action Required:** Verify `EmbeddingRuntimeWorker` has `@Component`. If it does, remove from Phase 2 stub.

---

## 3. OcrServicePort vs OcrPort — Contract Ambiguity

### Current State

```
OcrServicePort (ingest.application.port.out)
  └── OcrServiceAdapter (@Component, Phase 2 stub)
  
OcrPort (core.port.ocr) [NEW — Phase 6A.1-FIX]
  └── PaddleOcrAdapter (@Component, Phase 5B real impl)
```

### Problem

The ingest pipeline (`UploadOrchestrationService`, `OcrServiceAdapter`) was built against `OcrServicePort` with nested `OcrResult`/`OcrConfig` record types. The new `PaddleOcrAdapter` implements `OcrPort` with `OcrResult`/`OcrConfig` from `notarist-core`.

These are **different types** with the same names. When the ingest pipeline calls:
```java
OcrServicePort.OcrResult result = ocrServicePort.performOcr(jobId, objectKey, config);
```
...and the real implementation is `PaddleOcrAdapter` which returns `core.OcrResult`, there is a **type mismatch**.

**INTEGRITY_RISK — Integration contract is broken.** The ingest pipeline cannot use `PaddleOcrAdapter` without migrating `OcrServicePort` to use `OcrPort` and `core.OcrResult`/`OcrConfig`.

**This is a Phase 6A.2 scope item.** The fix is: migrate `OcrServicePort` to extend or delegate to `OcrPort`, OR replace `OcrServicePort` with `OcrPort` in the ingest application layer.

**Recommended remediation:**
1. Replace `OcrServicePort` in ingest with `OcrPort` from core
2. Update `OcrServiceAdapter` to implement `OcrPort` (stub impl)
3. The ingest pipeline uses `OcrPort` instead of `OcrServicePort`
4. `PaddleOcrAdapter` implements `OcrPort` — same type, no conflict
5. Delete `OcrServicePort`

---

## 4. Unimplemented Ports

### 4.1 KeywordRetrievalPort

**Location:** `notarist-search.application.port.out.KeywordRetrievalPort`  
**Implementation found:** `BM25SearchRepositoryImpl` in `notarist-search.infrastructure.persistence.postgres`

**Action:** Verify `BM25SearchRepositoryImpl` implements `KeywordRetrievalPort`. If not, check if it implements a `KeywordSearchRepository` interface that was renamed.

### 4.2 SemanticRetrievalPort

**Location:** `notarist-search.application.port.out.SemanticRetrievalPort`  
**Implementation:** `QdrantSearchAdapter` (skeleton) implements `VectorSearchPort` — does it also implement `SemanticRetrievalPort`? Verify.

### 4.3 IngestionQueuePort

**Location:** `notarist-ingest.application.port.out.IngestionQueuePort`  
**Implementation:** `IngestionQueueRepositoryImpl` in `notarist-ingest.infrastructure.persistence.postgres` — verify it implements `IngestionQueuePort`.

### 4.4 TokenDenyListPort

**Location:** `notarist-auth.application.port.out.TokenDenyListPort`  
**Implementation:** `SessionTokenRepositoryImpl` in `notarist-auth.infrastructure.persistence.postgres` — verify it implements `TokenDenyListPort`.

---

## 5. NerServicePort — Verify Implementation

| Class | Package | Phase | @Component |
|---|---|---|---|
| `NerServiceAdapter` | `notarist-ingest.infrastructure.adapter` | Phase 2 | YES |

`NerServicePort` appears to have only one implementation. Verify it is NOT also in Phase 5.

---

## 6. Recommended Fix Plan for Dual-Bean Problem

### Option A — @Profile("stub") (Recommended for testing parity)
```java
// Phase 2/3/4 stub adapters:
@Component
@Profile("stub")  // only active when spring.profiles.active=stub
public class QdrantSearchAdapter implements VectorSearchPort { ... }

// Phase 5 real adapters:
@Component
@Primary  // wins when both profiles are active
public class com.notarist.infra.qdrant.QdrantSearchAdapter implements VectorSearchPort { ... }
```

**Pros:** Stubs remain runnable for isolated testing  
**Cons:** Requires profile configuration in test contexts

### Option B — Remove @Component from stubs (Recommended for production)
```java
// Phase 2/3/4 stub adapters:
// @Component  ← REMOVED
public class QdrantSearchAdapter implements VectorSearchPort { ... }
```

**Pros:** Simple, no ambiguity, no profile overhead  
**Cons:** Stubs cannot be auto-wired in tests without explicit @Bean declarations

**Recommendation:** Use Option B for all 5 dual-bean pairs. Stubs can be manually configured in `@TestConfiguration` if needed for integration tests.

---

## 7. Remediation Summary

| ID | Severity | Port | Conflicting Classes | Action |
|---|---|---|---|---|
| API-B1 | BLOCKER | `VectorSearchPort` | Phase3 `QdrantSearchAdapter` + Phase5 `QdrantSearchAdapter` | Remove `@Component` from Phase 3 |
| API-B2 | BLOCKER | `DocumentStoragePort` | Phase2 `MinioDocumentStorageAdapter` + Phase5 `MinioDocumentStorageAdapter` | Remove `@Component` from Phase 2 |
| API-B3 | BLOCKER | `LlmPort` | Phase4 `OllamaAdapter` + Phase5B `OllamaRuntimeAdapter` | Remove `@Component` from Phase 4 |
| API-B4 | BLOCKER | `VectorIndexPort` | Phase2 `VectorIndexAdapter` + Phase5 `QdrantIndexAdapter` | Remove `@Component` from Phase 2 |
| API-B5 | BLOCKER | `RerankerPort` | Phase3 `RerankerAdapter` + Phase5B `RerankerRuntimeWorker` | Remove `@Component` from Phase 3 |
| API-I1 | INTEGRITY_RISK | `OcrServicePort`/`OcrPort` | Ingest uses `OcrServicePort`; runtime implements `OcrPort` | Migrate ingest to use `OcrPort`; delete `OcrServicePort` |
| API-I2 | INTEGRITY_RISK | `EmbeddingPort` | Phase2 stub + Phase5B real | Verify Phase5B has `@Component`; remove from Phase 2 |
| API-L1 | LATENT_RISK | `KeywordRetrievalPort` | `BM25SearchRepositoryImpl` — verify port match | Scan and verify |
| API-L2 | LATENT_RISK | `SemanticRetrievalPort` | No confirmed impl | Scan and verify |
| API-L3 | LATENT_RISK | `IngestionQueuePort` | `IngestionQueueRepositoryImpl` — verify port match | Scan and verify |
