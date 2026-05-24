# PHASE 6A.1 — Recommended Fixes
**Project:** notarist-rag  
**Report Date:** 2026-05-24  
**Scope:** Actionable fix specifications for all violations identified in violation_report.md

---

## Priority Order

1. **P0 — Build Unblocking** (CRITICAL-001, CRITICAL-002) — must be done first, nothing else can run
2. **P1 — Compile Correctness** (CRITICAL-003, CRITICAL-004, HIGH-001, HIGH-002, HIGH-003) — before CI
3. **P2 — Architecture Safety** (HIGH-004, MEDIUM-001..005) — before integration testing
4. **P3 — Housekeeping** (LOW-001, LOW-002) — backlog

---

## P0 Fixes — Build Unblocking

---

### FIX-001: Create gradle/libs.versions.toml

**Fixes:** CRITICAL-001, RISK-B1  
**Location:** `generated/backend-skeleton/gradle/libs.versions.toml`

Create the file with all version catalog entries referenced across all module `build.gradle.kts` files:

```toml
[versions]
spring-boot = "3.2.5"
jjwt = "0.12.5"
ojdbc11 = "21.11.0.0"
postgresql = "42.7.3"
flyway = "10.11.0"
liquibase = "4.27.0"
minio = "8.5.9"
qdrant-client = "1.9.1"
reactor-core = "3.6.5"
jackson = "2.17.1"
micrometer = "1.12.5"
logback-contrib = "0.1.5"
okhttp = "4.12.0"
archunit = "1.3.0"
mockito = "5.11.0"
testcontainers = "1.19.7"
resilience4j = "2.2.0"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-webflux = { module = "org.springframework.boot:spring-boot-starter-webflux" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-jdbc = { module = "org.springframework.boot:spring-boot-starter-jdbc" }
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }

jjwt-api = { module = "io.jsonwebtoken:jjwt-api", version.ref = "jjwt" }
jjwt-impl = { module = "io.jsonwebtoken:jjwt-impl", version.ref = "jjwt" }
jjwt-jackson = { module = "io.jsonwebtoken:jjwt-jackson", version.ref = "jjwt" }

ojdbc11 = { module = "com.oracle.database.jdbc:ojdbc11", version.ref = "ojdbc11" }
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql" }

flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-postgresql = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }
liquibase-core = { module = "org.liquibase:liquibase-core", version.ref = "liquibase" }

minio = { module = "io.minio:minio", version.ref = "minio" }
qdrant-client = { module = "io.qdrant:client", version.ref = "qdrant-client" }

reactor-core = { module = "io.projectreactor:reactor-core", version.ref = "reactor-core" }

jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
jackson-datatype-jsr310 = { module = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310", version.ref = "jackson" }

micrometer-prometheus = { module = "io.micrometer:micrometer-registry-prometheus", version.ref = "micrometer" }
logback-json-classic = { module = "ch.qos.logback.contrib:logback-json-classic", version.ref = "logback-contrib" }
logback-jackson = { module = "ch.qos.logback.contrib:logback-jackson", version.ref = "logback-contrib" }

okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
resilience4j-spring-boot3 = { module = "io.github.resilience4j:resilience4j-spring-boot3", version.ref = "resilience4j" }

archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }
mockito-core = { module = "org.mockito:mockito-core", version.ref = "mockito" }
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
testcontainers-oracle-xe = { module = "org.testcontainers:oracle-xe", version.ref = "testcontainers" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.5" }
```

**Verification:** Run `./gradlew dependencies --configuration compileClasspath` on any module. Should not throw `Unresolved reference: libs`.

---

### FIX-002: Register All Modules in settings.gradle.kts

**Fixes:** CRITICAL-002, RISK-B2  
**Location:** `generated/backend-skeleton/settings.gradle.kts`

Replace current content with:

```kotlin
rootProject.name = "notarist-rag"

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

**Note:** Module source directories for `notarist-infra`, `notarist-runtime`, `notarist-observability` must be co-located or their `projectDir` must be configured. Either:
(a) Copy source from `phase5*` directories into `generated/backend-skeleton/notarist-{infra,runtime,observability}/src/`, or
(b) Set custom project directories:
```kotlin
project(":notarist-infra").projectDir = file("../backend-implementation/phase5-infra-ai/notarist-infra")
project(":notarist-runtime").projectDir = file("../backend-implementation/phase5b-ai-runtime/notarist-runtime")
project(":notarist-observability").projectDir = file("../backend-implementation/phase5c-observability/notarist-observability")
```

---

## P1 Fixes — Compile Correctness

---

### FIX-003: Create build.gradle.kts for notarist-infra

**Fixes:** HIGH-003  
**Location:** `generated/backend-skeleton/notarist-infra/build.gradle.kts`

```kotlin
dependencies {
    implementation(project(":notarist-core"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.minio)
    implementation(libs.qdrant.client)
    implementation(libs.micrometer.prometheus)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}
```

---

### FIX-004: Resolve notarist-runtime Cross-Module Import Violations

**Fixes:** CRITICAL-003, CRITICAL-004, RISK-C1, RISK-C2, RISK-C3  
**Location:** Multiple files in `notarist-runtime`

**Step 1 — Move shared types to notarist-core:**

Move or redefine these types to `notarist-core`:
- `OcrConfidencePolicy` → `com.notarist.core.domain.policy.OcrConfidencePolicy`
- `OcrReviewStatus` → `com.notarist.core.domain.valueobject.OcrReviewStatus`
- `QdrantVectorPayload` → `com.notarist.core.domain.valueobject.QdrantVectorPayload`

**Step 2 — Define OcrPort in notarist-core:**

```java
// notarist-core: com.notarist.core.port.OcrPort
package com.notarist.core.port;

public interface OcrPort {
    OcrResult process(OcrRequest request);
}
```

Remove `import com.notarist.ingest.application.port.out.OcrServicePort` from `PaddleOcrAdapter.java`.  
Replace with `import com.notarist.core.port.OcrPort`.

**Step 3 — Create build.gradle.kts for notarist-runtime:**

```kotlin
dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-infra"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.reactor.core)
    implementation(libs.okhttp)
    implementation(libs.micrometer.prometheus)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}
```

---

### FIX-005: Create build.gradle.kts for notarist-observability

**Fixes:** HIGH-003  
**Location:** `generated/backend-skeleton/notarist-observability/build.gradle.kts`

```kotlin
dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-audit"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.micrometer.prometheus)
    implementation(libs.resilience4j.spring.boot3)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}
```

---

### FIX-006: Resolve Dual Migration Tool Strategy

**Fixes:** HIGH-001, RISK-L2  
**Location:** `generated/backend-skeleton/notarist-web/build.gradle.kts`

**Decision: Flyway for PostgreSQL, Liquibase for Oracle**

Remove `liquibase-core` from `notarist-web` if Oracle DDL is managed outside the application (e.g., DBA-managed). If Liquibase is truly needed for Oracle:

```kotlin
// notarist-web/build.gradle.kts — explicit migration tool ownership
implementation(libs.liquibase.core)         // Oracle schema (BRANCHPERFAPPDB)
implementation(libs.flyway.core)            // PostgreSQL schema (RAG metadata, vector)
implementation(libs.flyway.postgresql)      // PostgreSQL dialect for Flyway
```

And in `application.yml`:
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration/postgresql
    datasource:
      url: ${POSTGRES_URL}
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/oracle-master.xml
    datasource:
      url: ${ORACLE_URL}
```

Each migration tool must target a different DataSource. Never point both at the same schema.

---

### FIX-007: Expand All Wildcard Imports

**Fixes:** HIGH-002  
**Scope:** All 36+ wildcard import occurrences

**Pattern — JPA Entities (replace `jakarta.persistence.*`):**
```java
// Before
import jakarta.persistence.*;

// After — explicit only what is used
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
```

**Pattern — Spring MVC Controllers (replace `org.springframework.web.bind.annotation.*`):**
```java
// Before
import org.springframework.web.bind.annotation.*;

// After
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
```

**Pattern — Spring HTTP (replace `org.springframework.http.*`):**
```java
// Before
import org.springframework.http.*;

// After
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
```

**Pattern — Internal domain models (replace `com.notarist.*.domain.model.*`):**
```java
// Before — in AssistantOrchestrator.java
import com.notarist.assistant.domain.model.*;

// After — explicit
import com.notarist.assistant.domain.model.Conversation;
import com.notarist.assistant.domain.model.AssistantRequest;
import com.notarist.assistant.domain.model.AssistantResponse;
```

**Pattern — java.util (replace `java.util.*`):**
```java
// Before
import java.util.*;

// After — list only what is used, e.g.:
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
```

---

### FIX-008: Update notarist-web to Include New Modules

**Fixes:** RISK-L1  
**Location:** `generated/backend-skeleton/notarist-web/build.gradle.kts`

Add the three new modules to the assembly:
```kotlin
dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-auth"))
    implementation(project(":notarist-document"))
    implementation(project(":notarist-ingest"))
    implementation(project(":notarist-search"))
    implementation(project(":notarist-assistant"))
    implementation(project(":notarist-regulation"))
    implementation(project(":notarist-audit"))
    implementation(project(":notarist-infra"))         // ADD
    implementation(project(":notarist-runtime"))       // ADD
    implementation(project(":notarist-observability")) // ADD
    // ... rest of deps unchanged
}
```

---

## P2 Fixes — Architecture Safety

---

### FIX-009: Add Gradle Toolchain Declaration

**Fixes:** MEDIUM-001, MEDIUM-002, RISK-LAT2  
**Location:** `generated/backend-skeleton/build.gradle.kts`

Replace:
```kotlin
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

With:
```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
```

If `--enable-preview` is not used by any Java source in the project, also remove:
```kotlin
// REMOVE if no preview features are used
tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
    options.release.set(17)
}
tasks.withType<Test> {
    jvmArgs("--enable-preview")
}
```

---

### FIX-010: Remove Hardcoded Jakarta Validation Version from notarist-core

**Fixes:** MEDIUM-003  
**Location:** `generated/backend-skeleton/notarist-core/build.gradle.kts`

```kotlin
// Before
implementation("jakarta.validation:jakarta.validation-api:3.0.2")

// After
implementation("jakarta.validation:jakarta.validation-api")
```

---

### FIX-011: Remove Redundant jackson-databind Declarations

**Fixes:** MEDIUM-004  
**Location:** `notarist-ingest`, `notarist-search`, `notarist-assistant`, `notarist-audit` build files

Remove:
```kotlin
implementation(libs.jackson.databind)
```

From modules that already declare `spring-boot-starter-web` (which transitively provides Jackson). Keep explicit `jackson-databind` only in `notarist-core`.

---

### FIX-012: Fail Fast on Missing Nexus URL

**Fixes:** MEDIUM-005  
**Location:** `generated/backend-skeleton/build.gradle.kts`

```kotlin
// Before
url = uri(System.getenv("NEXUS_URL") ?: "https://nexus.notarist.internal/repository/maven-public/")

// After
url = uri(
    System.getenv("NEXUS_URL")
        ?: if (System.getenv("CI") != null) {
            throw GradleException("NEXUS_URL must be set in CI environment")
        } else {
            "https://nexus.notarist.internal/repository/maven-public/"
        }
)
```

---

### FIX-013: Document or Remove PostgreSQL Driver in notarist-auth

**Fixes:** HIGH-004  
**Location:** `generated/backend-skeleton/notarist-auth/build.gradle.kts`

Either document with a comment why it's needed:
```kotlin
// Refresh token blacklist stored in PostgreSQL (separate from Oracle user entities)
implementation(libs.postgresql)
```

Or remove if unused and ensure ojdbc11 is explicitly declared:
```kotlin
// Oracle JDBC for User/Role JPA entities (UserJpaEntity, UserRoleJpaEntity)
implementation(libs.ojdbc11)
```

---

## Build Reproducibility Checklist (Post-Fix)

| Item | Fix Applied | Verification |
|---|---|---|
| `gradle/libs.versions.toml` exists | FIX-001 | `./gradlew dependencies` succeeds |
| All 12 modules in settings.gradle.kts | FIX-002 | `./gradlew projects` shows all 12 |
| notarist-infra has build.gradle.kts | FIX-003 | `./gradlew :notarist-infra:compileJava` |
| notarist-runtime cross-imports resolved | FIX-004 | `./gradlew :notarist-runtime:compileJava` |
| notarist-observability has build.gradle.kts | FIX-005 | `./gradlew :notarist-observability:compileJava` |
| Single migration tool per DataSource | FIX-006 | Startup: no dual-migration conflict |
| No wildcard imports | FIX-007 | `./gradlew check` + ArchUnit test |
| notarist-web includes all 3 new modules | FIX-008 | Full app JAR contains all classes |
| Toolchain declared | FIX-009 | `./gradlew -Pjava.toolchain.version=21 build` fails gracefully |
| No hardcoded Jakarta version | FIX-010 | `./gradlew dependencyInsight --dependency jakarta.validation` |
| No redundant jackson-databind | FIX-011 | `./gradlew dependencyInsight --dependency jackson-databind` |
| Nexus URL required in CI | FIX-012 | CI fails fast without NEXUS_URL |
| ojdbc11 documented in auth | FIX-013 | Code review |

---

## Estimated Fix Effort

| Priority | Fix Count | Estimated Effort |
|---|---|---|
| P0 (Build Unblocking) | 2 fixes | 2–3 hours |
| P1 (Compile Correctness) | 6 fixes | 4–6 hours |
| P2 (Architecture Safety) | 5 fixes | 2–3 hours |
| P3 (Housekeeping) | 2 fixes | 30 minutes |
| **Total** | **15 fixes** | **~12 hours** |
