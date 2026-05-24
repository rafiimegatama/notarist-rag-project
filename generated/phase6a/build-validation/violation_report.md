# PHASE 6A.1 — Violation Report
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** Dependency violations, boundary violations, build violations, import violations

---

## Severity Legend

| Level | Meaning |
|---|---|
| CRITICAL | Build fails or runtime ClassNotFoundException guaranteed |
| HIGH | Compile error or architectural boundary broken |
| MEDIUM | Build smell, hidden risk, non-reproducible behavior |
| LOW | Style enforcement, catalog hygiene |

---

## CRITICAL Violations

---

### [CRITICAL-001] Version Catalog Missing

**Type:** Build Configuration  
**Severity:** CRITICAL  
**Affects:** ALL MODULES

**Description:**  
All module `build.gradle.kts` files reference `libs.*` symbols (e.g., `libs.spring.boot.starter.web`, `libs.ojdbc11`, `libs.qdrant.client`, etc.) but the Gradle Version Catalog file `gradle/libs.versions.toml` does not exist anywhere in the project.

**Impact:**  
Gradle configuration phase fails immediately with:
```
FAILURE: Build failed with an exception.
* What went wrong:
Could not resolve symbol 'libs' in build file
```
No module can be compiled. No test can run. Build is 100% blocked.

**Files Affected:**  
`notarist-assistant/build.gradle.kts`, `notarist-audit/build.gradle.kts`, `notarist-auth/build.gradle.kts`, `notarist-document/build.gradle.kts`, `notarist-ingest/build.gradle.kts`, `notarist-regulation/build.gradle.kts`, `notarist-search/build.gradle.kts`, `notarist-web/build.gradle.kts`

**Required Fix:**  
Create `gradle/libs.versions.toml` with all required version catalog entries. See `recommended_fixes.md`.

---

### [CRITICAL-002] Three Modules Not Registered in settings.gradle.kts

**Type:** Build Configuration  
**Severity:** CRITICAL  
**Affects:** notarist-infra, notarist-runtime, notarist-observability

**Description:**  
Phases 5, 5B, and 5C generated three substantial Java modules:
- `notarist-infra` — 23 Java files (Qdrant, MinIO, PostgreSQL, OCR infra adapters)
- `notarist-runtime` — 22 Java files (Ollama, embedding, reranker, OCR runtime workers)
- `notarist-observability` — 20 Java files (health, metrics, tracing, circuit breaker)

None are included in `settings.gradle.kts`. Gradle does not know they exist.

**Impact:**  
- `./gradlew build` ignores all 65 Java files across these 3 modules
- `notarist-runtime` imports `com.notarist.infra.*` and `com.notarist.ingest.*` — these cross-module imports cannot resolve without Gradle project references
- If manually added later without fixing cross-imports, classpath resolution fails

**Required Fix:**  
Add to `settings.gradle.kts`:
```kotlin
include(
    "notarist-core",
    "notarist-auth",
    "notarist-document",
    "notarist-ingest",
    "notarist-search",
    "notarist-assistant",
    "notarist-regulation",
    "notarist-audit",
    "notarist-infra",
    "notarist-runtime",
    "notarist-observability",
    "notarist-web"
)
```
Then add `build.gradle.kts` for each new module with correct dependencies.

---

### [CRITICAL-003] notarist-runtime Imports notarist-ingest Without Declaration

**Type:** Architectural Boundary Violation + Missing Dependency  
**Severity:** CRITICAL  
**Affects:** notarist-runtime → notarist-ingest

**Description:**  
`PaddleOcrAdapter.java` in `notarist-runtime` contains:
```java
import com.notarist.ingest.application.port.out.OcrServicePort;
```
There is no `implementation(project(":notarist-ingest"))` in any runtime build file.

**Impact:**  
- ClassNotFoundException at runtime for `OcrServicePort`
- Violates the rule: "assistant tidak import ingest internals" — extended here as runtime must not couple to ingest application layer
- `OcrServicePort` belongs to ingest's application layer; runtime should define its own port interface

**Required Fix:**  
Define `OcrPort` in `notarist-core` or `notarist-runtime` domain. Remove import of ingest application port. Apply Dependency Inversion — runtime implements a port defined in core, ingest implements the same port independently.

---

### [CRITICAL-004] notarist-runtime Imports notarist-infra Without Declaration

**Type:** Cross-Module Import Without Dependency  
**Severity:** CRITICAL  
**Affects:** notarist-runtime → notarist-infra

**Description:**  
Multiple files in `notarist-runtime` import from `com.notarist.infra`:
- `PaddleOcrAdapter.java` → `com.notarist.infra.ocr.OcrConfidencePolicy`
- `PaddleOcrAdapter.java` → `com.notarist.infra.ocr.OcrReviewStatus`
- `EmbeddingRuntimeWorker.java` → `com.notarist.infra.qdrant.QdrantVectorPayload`

**Impact:**  
ClassNotFoundException for all three types at runtime. `notarist-infra` itself is not in the build, compounding the issue.

**Required Fix:**  
Either:
(a) Move `OcrConfidencePolicy`, `OcrReviewStatus`, `QdrantVectorPayload` to `notarist-core` as shared value objects, or  
(b) Declare `notarist-infra` dependency in `notarist-runtime`'s build.gradle.kts and register both in settings. Option (a) is preferred to avoid tight coupling.

---

## HIGH Violations

---

### [HIGH-001] Dual Migration Tool Strategy in notarist-web

**Type:** Configuration Conflict  
**Severity:** HIGH  
**Affects:** notarist-web

**Description:**  
`notarist-web/build.gradle.kts` declares:
```kotlin
implementation(libs.liquibase.core)
implementation(libs.flyway.core)
implementation(libs.flyway.postgresql)
```
Both Liquibase and Flyway are migration tools. Having both active will cause:
- `FlywayAutoConfiguration` and `LiquibaseAutoConfiguration` both attempting schema management on startup
- Schema migration race condition or double-execution
- Spring Boot cannot determine migration order when both are present

**Additionally:** `notarist-ingest` already uses `flyway-core` + `flyway-postgresql` for Postgres schema. Having Liquibase in `notarist-web` creates a dual strategy.

**Required Fix:**  
Pick ONE migration tool. Based on existing usage in `notarist-ingest`, standardize on **Flyway** for PostgreSQL schemas. Use **Liquibase** only if Oracle schema migration is needed (Oracle DDL compatibility is better in Liquibase). Remove one or separate by database: Flyway → Postgres, Liquibase → Oracle.

---

### [HIGH-002] Wildcard Imports in Production Source Files

**Type:** Import Discipline  
**Severity:** HIGH  
**Affects:** 20+ files across multiple modules

**Description:**  
The following production source files contain wildcard (`.*`) imports in violation of the global rule "no wildcard import":

| File | Wildcard Import |
|---|---|
| `IngestionJobRepositoryImpl.java` | `com.notarist.core.domain.valueobject.*` |
| `IngestionJobRepositoryImpl.java` | `com.notarist.ingest.domain.model.*` |
| `IngestionJobRepositoryImpl.java` | `java.util.*` |
| `IngestionJob.java` | `com.notarist.core.domain.valueobject.*` |
| `UploadOrchestrationService.java` | `com.notarist.core.domain.valueobject.*` |
| `DocumentLegalMapper.java` | `com.notarist.core.domain.valueobject.*` |
| `MinioDocumentStorageAdapter.java` (ingest) | `io.minio.*` |
| `MinioDocumentStorageAdapter.java` (infra) | `io.minio.*` |
| `QdrantSearchAdapter.java` | `org.springframework.http.*` |
| `QdrantSearchAdapter.java` | `java.util.*` |
| `QdrantIndexAdapter.java` | `org.springframework.http.*` |
| `QdrantIndexAdapter.java` | `java.util.*` |
| `QdrantFilterBuilder.java` | `java.util.*` |
| `OperationalMetricsRegistry.java` | `io.micrometer.core.instrument.*` |
| `RerankerRuntimeWorker.java` | `org.springframework.http.*` |
| `RerankerQueueIsolation.java` | `java.util.concurrent.*` |
| `InferenceQueueIsolation.java` | `java.util.concurrent.*` |
| `EmbeddingRuntimeWorker.java` | `org.springframework.http.*` |
| `OllamaRuntimeAdapter.java` | `okhttp3.*` |
| `OcrRuntimeIsolation.java` | `java.util.concurrent.*` |
| `PaddleOcrAdapter.java` | `org.springframework.http.*` |
| `VectorConsistencyChecker.java` | `org.springframework.http.*` |
| `VectorConsistencyChecker.java` | `java.util.*` |
| `SearchQueryHandler.java` | `com.notarist.search.application.pipeline.*` |
| `ConversationMemoryService.java` | `java.util.*` |
| `AssistantOrchestrator.java` | `com.notarist.assistant.application.pipeline.*` |
| `AssistantOrchestrator.java` | `com.notarist.assistant.domain.model.*` |
| `AssistantContextBudgetManager.java` | `java.util.*` |
| `UserRoleJpaEntity.java` | `jakarta.persistence.*` |
| `UserJpaEntity.java` | `jakarta.persistence.*` |
| `IngestionJobJpaEntity.java` | `jakarta.persistence.*` |
| `DocumentLegalJpaEntity.java` | `jakarta.persistence.*` |
| `DocumentController.java` | `org.springframework.web.bind.annotation.*` |
| `AuthController.java` | `org.springframework.web.bind.annotation.*` |
| `AssistantController.java` | `org.springframework.web.bind.annotation.*` |
| `OperationalHealthEndpoint.java` | `org.springframework.web.bind.annotation.*` |
| `RetrievalFusionService.java` | `java.util.*` |

**Required Fix:**  
Expand all wildcard imports to explicit named imports. For JPA entities `jakarta.persistence.*` → individual: `@Entity`, `@Table`, `@Column`, `@Id`, `@GeneratedValue`, etc.

---

### [HIGH-003] Unresolved Module Build Files for notarist-infra, notarist-runtime, notarist-observability

**Type:** Missing Build Artifact  
**Severity:** HIGH

**Description:**  
`notarist-infra` has Java source under `phase5-infra-ai/notarist-infra/` but **has no `build.gradle.kts`** found in the standard skeleton location `generated/backend-skeleton/notarist-infra/`.

`notarist-runtime` and `notarist-observability` similarly lack skeleton-level `build.gradle.kts` files.

Without build files, even after registration in `settings.gradle.kts`, Gradle has no dependency declarations and cannot resolve the classpath for these modules.

**Required Fix:**  
Create `build.gradle.kts` for each of the three missing modules in `generated/backend-skeleton/`. See `recommended_fixes.md` for content templates.

---

### [HIGH-004] notarist-auth Declares PostgreSQL Driver Without Clear Justification

**Type:** Dependency Clarity / Potential Hidden Dependency  
**Severity:** HIGH  
**Affects:** notarist-auth

**Description:**  
`notarist-auth/build.gradle.kts` declares `implementation(libs.postgresql)` but auth module uses `spring-boot-starter-data-jpa` with Oracle (`ojdbc11` is absent from auth build — inherited transitively via notarist-audit which does declare `ojdbc11`). Auth entities (`UserJpaEntity`, `UserRoleJpaEntity`) are Oracle entities.

The PostgreSQL driver in auth suggests either:
- JWT refresh token store on PostgreSQL (valid but undocumented)
- Accidental carry-over from another module template
- Session store dependency not documented in architecture

**Required Fix:**  
Document clearly in auth's `build.gradle.kts` why PostgreSQL driver is needed, or remove if unused. If refresh token store is on PostgreSQL, a comment is mandatory.

---

## MEDIUM Violations

---

### [MEDIUM-001] --enable-preview Flag in Root Build

**Type:** Build Reproducibility  
**Severity:** MEDIUM

**Description:**  
Root `build.gradle.kts`:
```kotlin
tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
    options.release.set(17)
}
tasks.withType<Test> {
    jvmArgs("--enable-preview")
}
```
`--enable-preview` enables JDK preview features that are JDK minor version specific. A build on JDK 17.0.9 may produce different behavior than JDK 17.0.12. This violates build reproducibility.

**Required Fix:**  
If no preview features are actually used, remove `--enable-preview` from all compilation tasks. If used, document which features and pin the JDK minor version in the Gradle toolchain configuration.

---

### [MEDIUM-002] Missing Gradle Toolchain Declaration

**Type:** Build Reproducibility  
**Severity:** MEDIUM

**Description:**  
`sourceCompatibility` and `targetCompatibility` are set to Java 17, but there is no `java.toolchain` declaration. Developers with JDK 21 or JDK 11 on path will get unexpected results — Gradle does not automatically download or use JDK 17.

**Required Fix:**  
```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
```
Remove `sourceCompatibility`/`targetCompatibility` once toolchain is declared.

---

### [MEDIUM-003] Hardcoded Jakarta Validation Version in notarist-core

**Type:** Dependency Version Conflict Risk  
**Severity:** MEDIUM  
**Affects:** notarist-core

**Description:**  
```kotlin
implementation("jakarta.validation:jakarta.validation-api:3.0.2")
```
Spring Boot 3.2.5 BOM already manages `jakarta.validation-api` at the correct version. Hardcoding `3.0.2` bypasses BOM management and may conflict if Spring Boot's version diverges in a future BOM update.

**Required Fix:**  
Remove the version specifier, let BOM manage:
```kotlin
implementation("jakarta.validation:jakarta.validation-api")
```

---

### [MEDIUM-004] Duplicate jackson-databind Declarations

**Type:** Dependency Duplication  
**Severity:** MEDIUM  
**Affects:** notarist-ingest, notarist-search, notarist-assistant

**Description:**  
`jackson-databind` is declared explicitly in multiple modules while `spring-boot-starter-web` already pulls it transitively via `jackson-core` + `jackson-databind` from the Spring Boot BOM.

Modules with explicit redundant declaration:
- `notarist-ingest` — has `spring-boot-starter-web` + `libs.jackson.databind`
- `notarist-search` — has `spring-boot-starter-web` + `libs.jackson.databind`
- `notarist-assistant` — has `spring-boot-starter-web` + `libs.jackson.databind`
- `notarist-audit` — has `spring-boot-starter-web` + `libs.jackson.databind`

**Required Fix:**  
Remove explicit `jackson-databind` declarations from modules that already have `spring-boot-starter-web`. Keep explicit declaration only in `notarist-core` (which has no Spring Web starter).

---

### [MEDIUM-005] Nexus Credential Fallback to Public Maven Central

**Type:** Build Security / Repository Priority  
**Severity:** MEDIUM

**Description:**  
Root build:
```kotlin
repositories {
    maven { name = "NotaristNexus"; url = uri(System.getenv("NEXUS_URL") ?: "https://nexus.notarist.internal/...") }
    mavenCentral()
}
```
If `NEXUS_URL` is not set, build falls back to `https://nexus.notarist.internal/` which is not publicly resolvable, then falls back to Maven Central. In CI without Nexus env vars, builds will silently pull from Maven Central — dependency pinning is lost.

**Required Fix:**  
Fail fast if NEXUS_URL is not set in CI:
```kotlin
url = uri(System.getenv("NEXUS_URL") 
    ?: throw GradleException("NEXUS_URL environment variable is required"))
```
Or maintain an explicit `mavenCentral()` fallback only for dev, controlled by a Gradle property.

---

## LOW Violations

---

### [LOW-001] notarist-web Redundant Spring Dependencies

**Type:** Dependency Redundancy  
**Severity:** LOW

**Description:**  
`notarist-web` declares `spring-boot-starter-web`, `spring-boot-starter-webflux`, `spring-boot-starter-security`, etc. at the assembly level. Since all sub-modules already pull these transitively, the explicit declarations in the assembly are redundant. However, since `notarist-web` is the application entry point, this is acceptable for clarity but should be documented.

---

### [LOW-002] Mixed spring-boot-starter-jdbc and spring-boot-starter-data-jpa in Same Module

**Type:** Dependency Style  
**Severity:** LOW  
**Affects:** notarist-document

**Description:**  
`notarist-document` declares both `spring-boot-starter-data-jpa` (for JPA entities) and `spring-boot-starter-jdbc` (for `JdbcTemplate`). Both are valid but their co-existence should be intentional: JPA for Oracle transactional entities, JDBC for Postgres RAG metadata reads. This should be documented.

---

## Summary Table

| ID | Severity | Category | Module(s) Affected | Fix Priority |
|---|---|---|---|---|
| CRITICAL-001 | CRITICAL | Build Config | ALL | Immediate |
| CRITICAL-002 | CRITICAL | Build Config | infra/runtime/observability | Immediate |
| CRITICAL-003 | CRITICAL | Arch Boundary | notarist-runtime | Immediate |
| CRITICAL-004 | CRITICAL | Cross-Module Import | notarist-runtime | Immediate |
| HIGH-001 | HIGH | Config Conflict | notarist-web | Before CI |
| HIGH-002 | HIGH | Import Discipline | 20+ files | Before CI |
| HIGH-003 | HIGH | Missing Artifact | infra/runtime/observability | Before CI |
| HIGH-004 | HIGH | Hidden Dependency | notarist-auth | Before CI |
| MEDIUM-001 | MEDIUM | Reproducibility | ALL | Sprint |
| MEDIUM-002 | MEDIUM | Reproducibility | ALL | Sprint |
| MEDIUM-003 | MEDIUM | Version Conflict | notarist-core | Sprint |
| MEDIUM-004 | MEDIUM | Duplication | ingest/search/assistant/audit | Sprint |
| MEDIUM-005 | MEDIUM | Build Security | ALL | Sprint |
| LOW-001 | LOW | Redundancy | notarist-web | Backlog |
| LOW-002 | LOW | Style | notarist-document | Backlog |
