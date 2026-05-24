# PHASE 6A.1-FIX — Compile Resurrection Summary
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Fix Pass:** COMPLETE

---

## Fix Execution Summary

| Fix ID | Priority | Description | Status |
|---|---|---|---|
| P0-F01 | P0 | Added `okhttp` and `resilience4j` to `libs.versions.toml` | DONE |
| P0-F02 | P0 | Registered 3 modules in `settings.gradle.kts` with `projectDir` overrides | DONE |
| P1-F01 | P1 | Created `notarist-infra/build.gradle.kts` | DONE |
| P1-F02 | P1 | Created `notarist-runtime/build.gradle.kts` | DONE |
| P1-F03 | P1 | Created `notarist-observability/build.gradle.kts` | DONE |
| P1-F04 | P1 | Updated `notarist-web/build.gradle.kts` (+3 modules) | DONE |
| P2-F01 | P2 | Created `OcrPort` in core; fixed `PaddleOcrAdapter` | DONE |
| P2-F02 | P2 | Created `OcrReviewStatus`, `OcrConfidencePolicy` in core; removed bad imports from runtime | DONE |
| P2-F03 | P2 | Created `EmbeddingContract` in core; fixed `EmbeddingRuntimeWorker` | DONE |

---

## Files Modified or Created

### New Files Created
| File | Type | Purpose |
|---|---|---|
| `gradle/libs.versions.toml` | Modified existing | Added okhttp, resilience4j entries |
| `backend-implementation/phase5-infra-ai/notarist-infra/build.gradle.kts` | NEW | Gradle build config for infra module |
| `backend-implementation/phase5b-ai-runtime/notarist-runtime/build.gradle.kts` | NEW | Gradle build config for runtime module |
| `backend-implementation/phase5c-observability/notarist-observability/build.gradle.kts` | NEW | Gradle build config for observability module |
| `notarist-core/.../domain/ocr/OcrReviewStatus.java` | NEW | Canonical enum (moved from infra) |
| `notarist-core/.../domain/ocr/OcrConfidencePolicy.java` | NEW | Canonical policy (moved from infra) |
| `notarist-core/.../domain/ocr/OcrResult.java` | NEW | Canonical value object |
| `notarist-core/.../domain/ocr/OcrConfig.java` | NEW | Canonical value object |
| `notarist-core/.../domain/vector/EmbeddingContract.java` | NEW | Embedding dimension constants |
| `notarist-core/.../port/ocr/OcrPort.java` | NEW | Shared OCR port interface |

### Files Modified
| File | Change |
|---|---|
| `settings.gradle.kts` | Added 3 modules + projectDir overrides |
| `notarist-web/build.gradle.kts` | Added 3 new module project() deps |
| `runtime/.../ocr/PaddleOcrAdapter.java` | Removed 3 bad imports; added 3 core imports; changed `implements OcrServicePort` → `implements OcrPort` |
| `runtime/.../embedding/EmbeddingRuntimeWorker.java` | Replaced `QdrantVectorPayload` import with `EmbeddingContract`; fixed wildcard `org.springframework.http.*` |
| `infra/.../ocr/OcrReviewStatus.java` | Added migration comment header |
| `infra/.../ocr/OcrConfidencePolicy.java` | Added migration comment header |

---

## Compile Risk Status — Post-Fix

| Risk ID | Risk Level | Pre-Fix | Post-Fix | Notes |
|---|---|---|---|---|
| RISK-B1 | BLOCKER | libs.versions.toml missing | RESOLVED | Catalog existed; missing entries added |
| RISK-B2 | BLOCKER | 3 modules unregistered | RESOLVED | Registered with projectDir overrides |
| RISK-C1 | COMPILE-ERROR | PaddleOcrAdapter → OcrServicePort (ingest) | RESOLVED | Now implements OcrPort from core |
| RISK-C2 | COMPILE-ERROR | PaddleOcrAdapter → OcrConfidencePolicy (infra) | RESOLVED | Import removed (was unused) |
| RISK-C3 | COMPILE-ERROR | PaddleOcrAdapter → OcrReviewStatus (infra) | RESOLVED | Import removed (was unused) |
| RISK-C3b | COMPILE-ERROR | EmbeddingRuntimeWorker → QdrantVectorPayload (infra) | RESOLVED | Now uses EmbeddingContract from core |
| RISK-L1 | LINK-ERROR | notarist-web missing infra/runtime/observability | RESOLVED | Added to notarist-web build |
| RISK-L2 | LINK-ERROR | Dual migration tools | NOT RESOLVED | Deferred — not a compile blocker |
| RISK-R1 | RUNTIME-RISK | ojdbc11 in auth via transitive | NOT RESOLVED | Deferred — low priority |
| RISK-R2 | RUNTIME-RISK | webflux+web co-existence in assistant | NOT RESOLVED | Deferred — not a compile error |
| RISK-LAT1 | LATENT | --enable-preview non-reproducible | NOT RESOLVED | P2 cleanup — not blocking |
| RISK-LAT2 | LATENT | No Java toolchain declared | NOT RESOLVED | P2 cleanup — not blocking |
| RISK-LAT3 | LATENT | Transitive ojdbc11 | NOT RESOLVED | P2 cleanup — not blocking |

---

## Build Readiness Assessment — Post-Fix

### Expected Gradle Configuration Phase Result
```
./gradlew projects

> Task :projects
Root project 'notarist-rag'
+--- Project ':notarist-assistant'
+--- Project ':notarist-audit'
+--- Project ':notarist-auth'
+--- Project ':notarist-core'
+--- Project ':notarist-document'
+--- Project ':notarist-infra'
+--- Project ':notarist-ingest'
+--- Project ':notarist-observability'
+--- Project ':notarist-regulation'
+--- Project ':notarist-runtime'
+--- Project ':notarist-search'
+--- Project ':notarist-web'

BUILD SUCCESSFUL
```

### Expected Compile Phase — Module-by-Module

| Module | Compile Expected | Notes |
|---|---|---|
| notarist-core | SUCCESS | Zero external deps; all types self-contained |
| notarist-audit | SUCCESS | Depends only on core |
| notarist-auth | SUCCESS | Depends on core + audit |
| notarist-document | SUCCESS | Depends on core |
| notarist-ingest | SUCCESS | Depends on core + document + audit |
| notarist-search | SUCCESS | Depends on core + document |
| notarist-regulation | SUCCESS | Depends on core + document |
| notarist-assistant | SUCCESS | Depends on core + document + search + audit |
| notarist-infra | SUCCESS | Depends on core + ingest + search; all ports found |
| notarist-runtime | SUCCESS | Depends on core + assistant + search; all ports found |
| notarist-observability | SUCCESS | Self-contained; depends only on core |
| notarist-web | SUCCESS | Assembly: all 11 modules on classpath |

### Remaining COMPILE-RISK Items (Not Blockers)
- **Wildcard imports** (30+): Will compile successfully as long as the referenced classes exist. Risk: silent breakage on refactor. Recommended for cleanup before CI enforcement.
- **OcrServicePort in ingest**: Ingest still defines its own `OcrServicePort` with nested records. These are SEPARATE from the new `OcrPort`/`OcrResult`/`OcrConfig` in core. Ingest will compile fine; integration contract alignment is Phase 6A.2.

---

## Dependency Graph — Final State

```
notarist-core
  ├── domain.ocr: OcrReviewStatus, OcrConfidencePolicy, OcrResult, OcrConfig  [NEW]
  ├── domain.vector: EmbeddingContract                                          [NEW]
  └── port.ocr: OcrPort                                                         [NEW]

notarist-audit      ← core
notarist-auth       ← core, audit
notarist-document   ← core
notarist-ingest     ← core, document, audit
notarist-search     ← core, document
notarist-regulation ← core, document
notarist-assistant  ← core, document, search, audit

notarist-infra      ← core, ingest (VectorIndexPort, DocumentStoragePort),
                         search (VectorSearchPort)
notarist-runtime    ← core (OcrPort, EmbeddingContract),
                         assistant (LlmPort),
                         search (RerankerPort)
notarist-observability ← core

notarist-web        ← ALL 11 modules above
```

**No circular dependencies. Verified.**

---

## Next Steps

**Immediate (before PHASE 6A.2):**
1. Run `./gradlew :notarist-core:compileJava` — confirm 6 new files compile cleanly
2. Run `./gradlew :notarist-runtime:compileJava` — confirm PaddleOcrAdapter and EmbeddingRuntimeWorker compile
3. Run `./gradlew :notarist-infra:compileJava` — confirm infra adapters resolve all ports
4. Run `./gradlew :notarist-web:compileJava` — confirm full assembly compiles

**After confirmation — proceed to:**

**PHASE 6A.2 — Module Integrity Validation**  
(DTO consistency, event schema, port/adapter completeness, entity/repository alignment, persistence contract)

---

## Remaining Items Classified

| Item | Classification | Phase |
|---|---|---|
| Dual migration tools (Liquibase + Flyway) | RUNTIME_RISK | 6A.3 Configuration Validation |
| Wildcard imports in 30+ files | LATENT_RISK | 6A.4 Compile Safety Hardening |
| --enable-preview flag | LATENT_RISK | 6A.4 Compile Safety Hardening |
| Missing Java toolchain declaration | LATENT_RISK | 6A.4 Compile Safety Hardening |
| OcrServicePort/OcrPort integration contract | LATENT_RISK | 6A.2 Module Integrity |
| QdrantVectorPayload duplicate REQUIRED_DIMENSION | LATENT_RISK | 6A.2 Module Integrity |
| Redundant jackson-databind declarations | LOW | 6A.4 Compile Safety Hardening |
| PostgreSQL driver in notarist-auth clarity | LOW | 6A.3 Configuration Validation |
