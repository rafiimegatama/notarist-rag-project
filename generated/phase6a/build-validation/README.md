# PHASE 6A.1 — Build & Dependency Validation
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Phase:** 6A.1 — Build & Dependency Validation  
**Status:** COMPLETE — Pending Fix Execution

---

## Reports

| File | Contents |
|---|---|
| [dependency_matrix.md](dependency_matrix.md) | Full inter-module dependency graph, external library inventory, BOM consistency, orphan module map |
| [violation_report.md](violation_report.md) | All violations categorized by severity: 4 CRITICAL, 4 HIGH, 5 MEDIUM, 2 LOW |
| [compile_risk_report.md](compile_risk_report.md) | Compile-time failure analysis: 2 BLOCKERs, 4 COMPILE-ERRORs, 2 LINK-ERRORs, 2 RUNTIME-RISKs, 3 LATENT |
| [recommended_fixes.md](recommended_fixes.md) | 15 actionable fixes with exact code, ordered by priority P0→P3 |

---

## Executive Summary

### Build State: DEAD

The project cannot compile in its current form due to two BLOCKER-level issues.

### Critical Findings

| # | Finding | Severity | Fix |
|---|---|---|---|
| 1 | `gradle/libs.versions.toml` does not exist | CRITICAL | FIX-001 |
| 2 | 3 modules (infra/runtime/observability) not in `settings.gradle.kts` | CRITICAL | FIX-002 |
| 3 | `notarist-runtime` imports `notarist-ingest` application layer without Gradle dep | CRITICAL | FIX-004 |
| 4 | `notarist-runtime` imports `notarist-infra` types without Gradle dep | CRITICAL | FIX-004 |
| 5 | `notarist-web` has both Liquibase and Flyway active | HIGH | FIX-006 |
| 6 | 36+ wildcard imports across 20+ production files | HIGH | FIX-007 |
| 7 | 3 new modules have no `build.gradle.kts` | HIGH | FIX-003/005 |

### Architecture Boundary Validation Results

| Rule | Status | Finding |
|---|---|---|
| notarist-core imports no other module | PASS | Core has zero project() deps |
| assistant does not import ingest internals | PASS | No such import found in assistant |
| search does not import assistant | PASS | No such import found in search |
| observability is not a god-module | PASS | Observability depends only on core + audit |
| runtime does not import web layer | PASS | No web layer imports in runtime |
| domain layer zero Spring dependency | PASS | notarist-core has no Spring imports |
| **notarist-runtime does not import ingest** | **FAIL** | PaddleOcrAdapter imports OcrServicePort from ingest |
| **notarist-runtime does not import infra internals undeclared** | **FAIL** | 3 infra types imported without dependency |

### Module Count

| Category | Count | Status |
|---|---|---|
| Registered in settings | 9 | Functional |
| Generated but unregistered | 3 | Orphan — must be added |
| Total Java files scanned | ~220 | — |
| Wildcard import violations | 36 | Must fix before CI |

---

## Next Step

Resolve P0 fixes (FIX-001, FIX-002) to unblock build.  
Then resolve P1 fixes before submitting to CI.

**PHASE 6A.1 complete. Awaiting approval for PHASE 6A.2 — Module Integrity Validation.**
