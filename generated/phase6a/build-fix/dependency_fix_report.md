# PHASE 6A.1-FIX — Dependency Fix Report
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** All dependency-level fixes applied in this pass

---

## P0 Fixes Applied

### [P0-F01] Version Catalog — Added Missing Entries

**File:** `generated/backend-skeleton/gradle/libs.versions.toml`  
**Status:** APPLIED

Pre-existing catalog was structurally correct but missing two library entries required by Phase 5 modules:

| Entry Added | Version | Required By |
|---|---|---|
| `okhttp = "4.12.0"` | 4.12.0 | `notarist-runtime` — `OllamaRuntimeAdapter` uses OkHttp for Ollama streaming |
| `resilience4j = "2.2.0"` | 2.2.0 | `notarist-observability` — `ResilienceConfig` uses Resilience4j |
| `okhttp = { module = "com.squareup.okhttp3:okhttp" }` | catalog entry | notarist-runtime |
| `resilience4j-spring-boot3 = { module = "io.github.resilience4j:resilience4j-spring-boot3" }` | catalog entry | notarist-observability |

All existing `libs.*` references in the 9 original skeleton modules were validated as present in the catalog. No missing references in existing modules.

---

### [P0-F02] settings.gradle.kts — Three Modules Registered

**File:** `generated/backend-skeleton/settings.gradle.kts`  
**Status:** APPLIED

Added to `include()` block:
```
"notarist-infra",
"notarist-runtime",
"notarist-observability",
```

Added `projectDir` overrides:
```kotlin
project(":notarist-infra").projectDir =
    file("../backend-implementation/phase5-infra-ai/notarist-infra")
project(":notarist-runtime").projectDir =
    file("../backend-implementation/phase5b-ai-runtime/notarist-runtime")
project(":notarist-observability").projectDir =
    file("../backend-implementation/phase5c-observability/notarist-observability")
```

Gradle will now find all 12 modules and resolve their build files from the correct directories.

---

## P1 Fixes Applied

### [P1-F01] notarist-infra/build.gradle.kts — Created

**File:** `generated/backend-implementation/phase5-infra-ai/notarist-infra/build.gradle.kts`  
**Status:** CREATED

```kotlin
dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-ingest"))   // VectorIndexPort, DocumentStoragePort
    implementation(project(":notarist-search"))   // VectorSearchPort

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.minio)
    implementation(libs.qdrant.client)
    implementation(libs.micrometer.prometheus)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}
```

**Architecture note:** `notarist-infra` depending on `notarist-ingest` and `notarist-search` is CORRECT hexagonal architecture. Infrastructure adapters implement ports defined in the domain/application modules. No circular dependency — ingest and search do NOT depend on infra.

---

### [P1-F02] notarist-runtime/build.gradle.kts — Created

**File:** `generated/backend-implementation/phase5b-ai-runtime/notarist-runtime/build.gradle.kts`  
**Status:** CREATED

```kotlin
dependencies {
    implementation(project(":notarist-core"))       // OcrPort, EmbeddingContract, OcrResult, OcrConfig
    implementation(project(":notarist-assistant"))  // LlmPort (OllamaRuntimeAdapter implements it)
    implementation(project(":notarist-search"))     // RerankerPort (RerankerRuntimeWorker implements it)
    // NO notarist-infra dependency (infra types replaced by core contracts — see P2 fixes)
    // NO notarist-ingest dependency (OcrServicePort replaced by OcrPort in core — see P2 fixes)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.reactor.core)
    implementation(libs.okhttp)
    implementation(libs.micrometer.prometheus)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}
```

**Architecture note:**
- `OllamaRuntimeAdapter implements LlmPort` — LlmPort from assistant. Runtime depends on assistant for the port contract. No circular dependency — assistant does NOT depend on runtime.
- `RerankerRuntimeWorker implements RerankerPort` — RerankerPort from search. Same pattern.
- `PaddleOcrAdapter implements OcrPort` — OcrPort from core. No ingest dependency needed.

---

### [P1-F03] notarist-observability/build.gradle.kts — Created

**File:** `generated/backend-implementation/phase5c-observability/notarist-observability/build.gradle.kts`  
**Status:** CREATED

```kotlin
dependencies {
    implementation(project(":notarist-core"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.micrometer.prometheus)
    implementation(libs.jackson.databind)
    implementation(libs.resilience4j.spring.boot3)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}
```

**Architecture note:** Observability is self-contained. Source scan confirmed zero cross-module imports to other notarist modules. Depends only on core + Spring + Micrometer + Resilience4j.

---

### [P1-F04] notarist-web/build.gradle.kts — Updated

**File:** `generated/backend-skeleton/notarist-web/build.gradle.kts`  
**Status:** APPLIED

Added three new module dependencies to the application assembly:
```kotlin
implementation(project(":notarist-infra"))
implementation(project(":notarist-runtime"))
implementation(project(":notarist-observability"))
```

All 65 new Java classes from Phase 5/5B/5C are now on the application classpath.

---

## Final Dependency Graph (Post-Fix)

```
notarist-web (assembly, 12 modules)
├── notarist-core          (leaf — zero project deps)
├── notarist-audit         → core
├── notarist-auth          → core, audit
├── notarist-document      → core
├── notarist-ingest        → core, document, audit
├── notarist-search        → core, document
├── notarist-regulation    → core, document
├── notarist-assistant     → core, document, search, audit
├── notarist-infra         → core, ingest, search
├── notarist-runtime       → core, assistant, search
├── notarist-observability → core
└── notarist-web           → all above
```

**Cycle check:** No circular dependencies. Each module only depends on modules with lower ordinal in the graph above. `notarist-core` is the leaf with no dependencies.

---

## Remaining Dependency Warnings (Not Blocking Compile)

| Warning | Module | Action |
|---|---|---|
| Dual migration tools (Liquibase + Flyway) in notarist-web | notarist-web | Document intentionality or remove one. Not a compile blocker. |
| Hardcoded Jakarta Validation version in notarist-core | notarist-core | Remove hardcode, let BOM manage. Low priority. |
| Redundant jackson-databind in web/ingest/search | Multiple | Cleanup. Not a compile blocker. |
| `ojdbc11` not explicitly declared in notarist-auth | notarist-auth | Clarify intent. Transitive via notarist-audit. |
