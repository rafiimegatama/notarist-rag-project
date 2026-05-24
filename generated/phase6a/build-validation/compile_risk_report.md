# PHASE 6A.1 — Compile Risk Report
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** Compile-time failure risks, symbol resolution failures, classpath integrity

---

## Risk Classification

| Level | Definition |
|---|---|
| BLOCKER | Compilation cannot start or terminates immediately |
| COMPILE-ERROR | Specific file(s) will fail to compile — `javac` error |
| LINK-ERROR | Compiles individually but fails during assembly / classpath resolution |
| RUNTIME-RISK | Compiles successfully but fails at runtime with class/method not found |
| LATENT | No immediate failure, but creates fragile condition that breaks under change |

---

## BLOCKER Risks

---

### [RISK-B1] Gradle Version Catalog Not Found

**Risk Level:** BLOCKER  
**Confidence:** 100%  
**Affects:** All 9 registered modules

**Failure Mode:**
```
FAILURE: Build failed with an exception.
Build file 'notarist-assistant/build.gradle.kts': line N:
  Unresolved reference: libs
```

All build files use `libs.spring.boot.starter.web`, `libs.ojdbc11`, `libs.qdrant.client`, `libs.jjwt.api`, `libs.minio`, `libs.flyway.core`, `libs.archunit.junit5`, etc.

Without `gradle/libs.versions.toml`, every build script fails at the Gradle configuration phase. No compilation task is reachable.

**Blocking Path:**
```
gradlew build
  → configure all projects
    → resolve libs.* symbols
      → FAIL — VersionCatalog not found
```

**Resolution Priority:** IMMEDIATE before any other task

---

### [RISK-B2] Three Modules Unreachable from Build Graph

**Risk Level:** BLOCKER  
**Confidence:** 100%  
**Affects:** notarist-infra, notarist-runtime, notarist-observability

**Failure Mode:**  
Not a Gradle error per se — Gradle simply ignores modules not in `settings.gradle.kts`. However:
- `notarist-runtime` source imports `com.notarist.infra.*` and `com.notarist.ingest.*` — these will never resolve
- If an attempt is made to compile the `phase5*` directories independently (e.g., `javac` directly), all cross-module symbols fail
- Any CI task that attempts to verify these phases fails silently or with unresolvable symbols

**Resolution Priority:** IMMEDIATE — add to settings + create build.gradle.kts for all three

---

## COMPILE-ERROR Risks

---

### [RISK-C1] PaddleOcrAdapter Cannot Resolve OcrServicePort

**Risk Level:** COMPILE-ERROR  
**Confidence:** 100%  
**File:** `phase5b-ai-runtime/notarist-runtime/.../ocr/PaddleOcrAdapter.java`

**Failure Mode:**
```
error: cannot find symbol
  import com.notarist.ingest.application.port.out.OcrServicePort;
                                                  ^
```

`OcrServicePort` is in `notarist-ingest`'s application layer. Even if `notarist-runtime` is added to the build, it has no declared dependency on `notarist-ingest`.

**Root Cause:** Architectural layering violation — runtime adapter layer imports application port from a sibling domain module.

---

### [RISK-C2] PaddleOcrAdapter Cannot Resolve OcrConfidencePolicy, OcrReviewStatus

**Risk Level:** COMPILE-ERROR  
**Confidence:** 100%  
**File:** `phase5b-ai-runtime/notarist-runtime/.../ocr/PaddleOcrAdapter.java`

**Failure Mode:**
```
error: cannot find symbol
  import com.notarist.infra.ocr.OcrConfidencePolicy;
error: cannot find symbol
  import com.notarist.infra.ocr.OcrReviewStatus;
```

`notarist-infra` is not a registered Gradle module. Even if added, `notarist-runtime` has no declared dependency on it.

---

### [RISK-C3] EmbeddingRuntimeWorker Cannot Resolve QdrantVectorPayload

**Risk Level:** COMPILE-ERROR  
**Confidence:** 100%  
**File:** `phase5b-ai-runtime/notarist-runtime/.../embedding/EmbeddingRuntimeWorker.java`

**Failure Mode:**
```
error: cannot find symbol
  import com.notarist.infra.qdrant.QdrantVectorPayload;
```

Same root cause as RISK-C2. `notarist-infra` not in build graph, no dependency declared.

---

### [RISK-C4] Wildcard Imports Risk Hidden Resolution Failures

**Risk Level:** COMPILE-ERROR (latent — depends on actual class names in packages)  
**Confidence:** HIGH  
**Affects:** 20+ files (see violation_report.md HIGH-002)

**Failure Mode:**  
Wildcard imports like `import com.notarist.core.domain.valueobject.*` will succeed only if the package exists and the class names referenced within the file are actually present in that package. If any class was renamed, removed, or moved, the wildcard import hides the error until a specific class reference fails:
```
error: cannot find symbol
  DocumentId id = ...
          ^
  (symbol: class DocumentId, location: package com.notarist.core.domain.valueobject)
```
With explicit imports, this failure is caught immediately at the import line and is far easier to diagnose.

**Specific High-Risk Files:**
- `IngestionJobRepositoryImpl.java` — `import com.notarist.ingest.domain.model.*` covers 5+ domain classes. Any refactoring of domain model classes silently breaks this.
- `AssistantOrchestrator.java` — `import com.notarist.assistant.domain.model.*` covers 3+ model classes.
- `SearchQueryHandler.java` — `import com.notarist.search.application.pipeline.*` covers pipeline service references.

---

## LINK-ERROR Risks

---

### [RISK-L1] notarist-web Assembly Missing infra/runtime/observability

**Risk Level:** LINK-ERROR  
**Confidence:** 100%  
**Affects:** Final application JAR

**Failure Mode:**  
`notarist-web` does not declare dependencies on `notarist-infra`, `notarist-runtime`, or `notarist-observability`. Even if these modules are added to the build and compile individually, the application assembly does not include them on the classpath. At runtime:
```
java.lang.NoClassDefFoundError: com/notarist/infra/qdrant/QdrantSearchAdapter
```
The application starts but any code path touching Qdrant, MinIO, Ollama, or observability fails.

---

### [RISK-L2] Dual Migration Tool Spring Boot Autoconfiguration Conflict

**Risk Level:** LINK-ERROR / RUNTIME-RISK  
**Confidence:** HIGH  
**Affects:** notarist-web application startup

**Failure Mode:**  
Spring Boot 3.2.5 autoconfigures both `FlywayAutoConfiguration` and `LiquibaseAutoConfiguration` when both are on the classpath with valid configuration. Startup sequence:
1. `LiquibaseAutoConfiguration` runs first (alphabetical)
2. `FlywayAutoConfiguration` runs second
3. If both target the same DataSource and schema, second migration sees already-applied changesets or conflicts

Unless explicitly disabled:
```properties
spring.flyway.enabled=false
# OR
spring.liquibase.enabled=false
```
Both will attempt to run. This is a deployment-time risk, not a compile-time error.

---

## RUNTIME-RISK

---

### [RISK-R1] Missing ojdbc11 in notarist-auth

**Risk Level:** RUNTIME-RISK  
**Confidence:** MEDIUM  
**Affects:** notarist-auth JPA entities

**Failure Mode:**  
`notarist-auth` uses `spring-boot-starter-data-jpa` and defines `UserJpaEntity`, `UserRoleJpaEntity` with JPA annotations targeting Oracle. However, `ojdbc11` is not declared in `notarist-auth/build.gradle.kts`. It is only available transitively through `notarist-audit` (which is a declared dependency).

If `notarist-auth` is ever used without `notarist-audit` (e.g., in unit test isolation), Oracle JDBC driver is absent:
```
java.sql.SQLException: No suitable driver found for jdbc:oracle:thin:@...
```

**Required Fix:**  
Either declare `ojdbc11` explicitly in `notarist-auth`, or document that auth always requires audit on classpath (fragile — prefer explicit).

---

### [RISK-R2] notarist-assistant webflux + reactor-core Without Full Reactive Configuration

**Risk Level:** RUNTIME-RISK  
**Confidence:** MEDIUM  
**Affects:** notarist-assistant SSE streaming

**Failure Mode:**  
`notarist-assistant` declares both `spring-boot-starter-web` (servlet/blocking) and `spring-boot-starter-webflux` (reactive). Spring Boot with both on classpath defaults to servlet unless `spring.main.web-application-type=reactive` is set. If streaming SSE uses `Flux<T>` but runs on a servlet container, backpressure and cancellation may not propagate correctly.

**Required Fix:**  
Verify `application.yml` explicitly sets `spring.main.web-application-type=reactive` for assistant module, or document the hybrid intent (Flux returned from servlet endpoint — supported but limited).

---

## LATENT Risks

---

### [RISK-LAT1] --enable-preview Makes Build Non-Reproducible

**Risk Level:** LATENT  
**Confidence:** HIGH

Preview features in JDK 17 include sealed classes, pattern matching finalization, and record patterns. If any preview feature is used, the bytecode is marked as preview-only and may not run on a different JDK 17 minor version. This creates invisible CI/prod divergence.

---

### [RISK-LAT2] Missing Gradle Toolchain Allows JDK Version Drift

**Risk Level:** LATENT  
**Confidence:** HIGH

Without `java.toolchain.languageVersion = JavaLanguageVersion.of(17)`, any developer or CI runner with a different JDK on PATH compiles with that JDK. A JDK 21 compiler with `--release 17` produces different bytecode than JDK 17 for certain constructs.

---

### [RISK-LAT3] Transitive Dependency on ojdbc11 via notarist-audit

**Risk Level:** LATENT  
**Confidence:** MEDIUM

Modules `notarist-auth`, `notarist-document` (via `spring-boot-starter-data-jpa`) depend on Oracle JDBC driver transitively through declared module dependencies. If `notarist-audit` is ever excluded or refactored to not declare `ojdbc11`, these modules silently lose Oracle connectivity.

---

## Compile Risk Summary

| Risk ID | Level | Confidence | Impact | Priority |
|---|---|---|---|---|
| RISK-B1 | BLOCKER | 100% | Build entirely dead | P0 |
| RISK-B2 | BLOCKER | 100% | 65 Java files unreachable | P0 |
| RISK-C1 | COMPILE-ERROR | 100% | PaddleOcrAdapter fails | P1 |
| RISK-C2 | COMPILE-ERROR | 100% | PaddleOcrAdapter fails | P1 |
| RISK-C3 | COMPILE-ERROR | 100% | EmbeddingRuntimeWorker fails | P1 |
| RISK-C4 | COMPILE-ERROR | HIGH | 20+ files fragile | P1 |
| RISK-L1 | LINK-ERROR | 100% | Application incomplete at runtime | P1 |
| RISK-L2 | LINK-ERROR | HIGH | Startup failure dual migration | P1 |
| RISK-R1 | RUNTIME-RISK | MEDIUM | Auth Oracle driver missing | P2 |
| RISK-R2 | RUNTIME-RISK | MEDIUM | SSE streaming misconfigured | P2 |
| RISK-LAT1 | LATENT | HIGH | Non-reproducible build | P2 |
| RISK-LAT2 | LATENT | HIGH | JDK drift | P2 |
| RISK-LAT3 | LATENT | MEDIUM | Transitive Oracle JDBC fragility | P3 |

---

## Compile Risk Verdict

**Current Build State: DEAD**

The project cannot compile in its current state. Two BLOCKER-level issues prevent any Gradle task from executing:
1. Missing `gradle/libs.versions.toml`
2. Three modules not registered in `settings.gradle.kts`

Resolving both BLOCKERs exposes three COMPILE-ERROR risks in `notarist-runtime` that must then be fixed before the runtime module can be compiled.

After all BLOCKER and COMPILE-ERROR risks are resolved, estimated remaining build correctness: **HIGH** — the core 9 modules (core through web) have clean dependency graphs.
