# PHASE 6A.1 — Dependency Matrix
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** Multi-module Gradle dependency graph — declared + transitive

---

## 1. Registered Modules (settings.gradle.kts)

| Module | Registered | Has build.gradle.kts |
|---|---|---|
| notarist-core | YES | YES |
| notarist-auth | YES | YES |
| notarist-document | YES | YES |
| notarist-ingest | YES | YES |
| notarist-search | YES | YES |
| notarist-assistant | YES | YES |
| notarist-regulation | YES | YES |
| notarist-audit | YES | YES |
| notarist-web | YES | YES |
| **notarist-infra** | **NO** | YES (phase5-infra-ai) |
| **notarist-runtime** | **NO** | NOT PRESENT |
| **notarist-observability** | **NO** | NOT PRESENT |

> **CRITICAL:** `notarist-infra`, `notarist-runtime`, `notarist-observability` are generated with Java source but NOT registered in `settings.gradle.kts`. They are orphan modules — unreachable from the Gradle build.

---

## 2. Declared Inter-Module Dependency Matrix

```
Depends On →    core  auth  document  ingest  search  assistant  regulation  audit  web
notarist-core    —     —      —         —       —        —           —          —     —
notarist-auth    ✓     —      —         —       —        —           —          ✓     —
notarist-document ✓    —      —         —       —        —           —          —     —
notarist-ingest  ✓     —      ✓         —       —        —           —          ✓     —
notarist-search  ✓     —      ✓         —       —        —           —          —     —
notarist-assistant ✓   —      ✓         —       ✓        —           —          ✓     —
notarist-regulation ✓  —      ✓         —       —        —           —          —     —
notarist-audit   ✓     —      —         —       —        —           —          —     —
notarist-web     ✓     ✓      ✓         ✓       ✓        ✓           ✓          ✓     —
```

Legend: ✓ = declared `project(":module")` dependency, — = no dependency

---

## 3. Unregistered Module Source Dependencies (Phase 5 artifacts)

| Source Module | Import Found In | Target Package | Declaration Status |
|---|---|---|---|
| notarist-runtime | PaddleOcrAdapter.java | `com.notarist.ingest.application.port.out.OcrServicePort` | **MISSING — cross-module import without Gradle dep** |
| notarist-runtime | PaddleOcrAdapter.java | `com.notarist.infra.ocr.OcrConfidencePolicy` | **MISSING — cross-module import without Gradle dep** |
| notarist-runtime | PaddleOcrAdapter.java | `com.notarist.infra.ocr.OcrReviewStatus` | **MISSING — cross-module import without Gradle dep** |
| notarist-runtime | EmbeddingRuntimeWorker.java | `com.notarist.infra.qdrant.QdrantVectorPayload` | **MISSING — cross-module import without Gradle dep** |

---

## 4. External Dependency Inventory per Module

### notarist-core
| Dependency | Version Source | Type |
|---|---|---|
| `jakarta.validation:jakarta.validation-api` | `3.0.2` (hardcoded) | implementation |
| `com.fasterxml.jackson.core:jackson-databind` | `libs.jackson.databind` | implementation |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | `libs.jackson.datatype.jsr310` | implementation |
| `org.springframework.boot:spring-boot-starter-test` | BOM | testImplementation |

### notarist-auth
| Dependency | Version Source | Type |
|---|---|---|
| `spring-boot-starter-web` | BOM | implementation |
| `spring-boot-starter-security` | BOM | implementation |
| `spring-boot-starter-data-jpa` | BOM | implementation |
| `spring-boot-starter-validation` | BOM | implementation |
| `jjwt-api` | libs catalog | implementation |
| `jjwt-impl` | libs catalog | implementation |
| `jjwt-jackson` | libs catalog | implementation |
| `postgresql` | libs catalog | implementation |
| `spring-boot-starter-test` | BOM | testImplementation |
| `mockito-core` | libs catalog | testImplementation |
| `archunit-junit5` | libs catalog | testImplementation |

> NOTE: `postgresql` driver in `notarist-auth` — auth entities use Oracle via `ojdbc11` inherited from core BOM. PostgreSQL driver presence here needs justification (session token storage? JWT refresh store?).

### notarist-document
| Dependency | Version Source | Type |
|---|---|---|
| `spring-boot-starter-web` | BOM | implementation |
| `spring-boot-starter-data-jpa` | BOM | implementation |
| `spring-boot-starter-jdbc` | BOM | implementation |
| `spring-boot-starter-validation` | BOM | implementation |
| `ojdbc11` | libs catalog | implementation |
| `postgresql` | libs catalog | implementation |

> NOTE: Both `ojdbc11` and `postgresql` declared — document module spans Oracle (akta, legal) and Postgres (RAG metadata). Dual-DB dependency is intentional but must be clearly scoped.

### notarist-ingest
| Dependency | Version Source | Type |
|---|---|---|
| `spring-boot-starter-web` | BOM | implementation |
| `spring-boot-starter-jdbc` | BOM | implementation |
| `spring-boot-starter-validation` | BOM | implementation |
| `postgresql` | libs catalog | implementation |
| `flyway-core` | libs catalog | implementation |
| `flyway-postgresql` | libs catalog | implementation |
| `minio` | libs catalog | implementation |
| `jackson-databind` | libs catalog | implementation |

### notarist-search
| Dependency | Version Source | Type |
|---|---|---|
| `spring-boot-starter-web` | BOM | implementation |
| `spring-boot-starter-jdbc` | BOM | implementation |
| `spring-boot-starter-validation` | BOM | implementation |
| `postgresql` | libs catalog | implementation |
| `qdrant-client` | libs catalog | implementation |
| `jackson-databind` | libs catalog | implementation |

### notarist-assistant
| Dependency | Version Source | Type |
|---|---|---|
| `spring-boot-starter-web` | BOM | implementation |
| `spring-boot-starter-webflux` | BOM | implementation |
| `spring-boot-starter-jdbc` | BOM | implementation |
| `spring-boot-starter-validation` | BOM | implementation |
| `postgresql` | libs catalog | implementation |
| `reactor-core` | libs catalog | implementation |
| `jackson-databind` | libs catalog | implementation |

### notarist-audit
| Dependency | Version Source | Type |
|---|---|---|
| `spring-boot-starter-web` | BOM | implementation |
| `spring-boot-starter-data-jpa` | BOM | implementation |
| `spring-boot-starter-validation` | BOM | implementation |
| `ojdbc11` | libs catalog | implementation |
| `jackson-databind` | libs catalog | implementation |

### notarist-regulation
| Dependency | Version Source | Type |
|---|---|---|
| `spring-boot-starter-web` | BOM | implementation |
| `spring-boot-starter-data-jpa` | BOM | implementation |
| `spring-boot-starter-validation` | BOM | implementation |
| `ojdbc11` | libs catalog | implementation |

### notarist-web (Application Assembly)
| Dependency | Version Source | Type |
|---|---|---|
| All 8 sub-modules | project() | implementation |
| `spring-boot-starter-web` | BOM | implementation |
| `spring-boot-starter-webflux` | BOM | implementation |
| `spring-boot-starter-security` | BOM | implementation |
| `spring-boot-starter-data-jpa` | BOM | implementation |
| `spring-boot-starter-actuator` | BOM | implementation |
| `spring-boot-starter-validation` | BOM | implementation |
| `ojdbc11` | libs catalog | implementation |
| `postgresql` | libs catalog | implementation |
| `liquibase-core` | libs catalog | implementation |
| `flyway-core` | libs catalog | implementation |
| `flyway-postgresql` | libs catalog | implementation |
| `micrometer-prometheus` | libs catalog | implementation |
| `logback-json-classic` | libs catalog | implementation |
| `logback-jackson` | libs catalog | implementation |
| `jackson-datatype-jsr310` | libs catalog | implementation |

> **WARNING:** Both `liquibase-core` AND `flyway-core`/`flyway-postgresql` declared in `notarist-web`. Dual migration strategy. See Violation Report.

---

## 5. Version Catalog Status

| Catalog File | Location | Status |
|---|---|---|
| `gradle/libs.versions.toml` | Expected: `generated/backend-skeleton/gradle/libs.versions.toml` | **NOT FOUND — CRITICAL** |

All `libs.*` references in module build files resolve against a Version Catalog that does not exist. Build will fail at configuration phase with `Could not resolve symbol 'libs'`.

---

## 6. BOM Version Consistency

| BOM | Version | Applied Via |
|---|---|---|
| `spring-boot-dependencies` | `3.2.5` | root `build.gradle.kts` → `dependencyManagement` |
| Jakarta Validation API | `3.0.2` | hardcoded in `notarist-core` |
| Spring Boot Plugin | `3.2.5` | root `build.gradle.kts` |
| Dependency Management Plugin | `1.1.5` | root `build.gradle.kts` |

> Spring Boot 3.2.5 bundles Jakarta Validation API `3.0.2`. The explicit hardcoded version in `notarist-core` is consistent but creates a hidden duplicate. Should be removed and sourced from BOM.

---

## 7. Dependency Flow Summary

```
notarist-web (assembly)
├── notarist-auth → notarist-core, notarist-audit
├── notarist-document → notarist-core
├── notarist-ingest → notarist-core, notarist-document, notarist-audit
├── notarist-search → notarist-core, notarist-document
├── notarist-assistant → notarist-core, notarist-document, notarist-search, notarist-audit
├── notarist-regulation → notarist-core, notarist-document
├── notarist-audit → notarist-core
└── notarist-core (leaf — no module deps)

ORPHAN (not in build):
├── notarist-infra [phase5-infra-ai]
├── notarist-runtime [phase5b-ai-runtime] → imports notarist-infra, notarist-ingest
└── notarist-observability [phase5c-observability]
```
