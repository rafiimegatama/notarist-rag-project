# PRODUCTION QA AUDIT REPORT — NOTARIST RAG PLATFORM

**Auditor role:** Senior Staff Software Engineer / Principal QA Engineer
**Scope:** Entire repository (`/home/extrasugar/notarist-rag-project`)
**Original audit date:** 2026-07-12
**Remediation update date:** 2026-07-13
**Mode:** Initial pass was read-only verification (no source modified). A subsequent, explicitly-scoped remediation pass fixed 8 of the 21 findings below (F1, F2, F3, F5, F6, F8, F10, F15, plus F13 as a side effect of the F3 fix); each fix was re-verified against the current source and a fresh build/boot run. Rounds 1–4 predate any test suite, so those fixes were verified only by static re-inspection plus the build/boot procedure used in the original audit. Round 5 introduced the repository's first automated tests (`PipelineStateMachineTest`), and its fixes are regression-covered.
**Backend:** Spring Boot 3.2.5, Java 17, 12 Gradle modules, ~498 files.
**Commit under audit:** `6379ef8` (working tree had modified files at time of original audit; remediation was applied directly to the working tree, not committed).

---

## REMEDIATION STATUS (added 2026-07-13)

Findings were fixed in five scoped rounds after the original audit, each re-verified against current source (not memory) before being marked here:

| Round | Findings fixed | Verification performed |
|---|---|---|
| 1 — startup blockers | F1 | `./gradlew build` → SUCCESSFUL; `bootRun` advances past all bean wiring, fails only at external DB connect |
| 2 — transaction boundaries | F2 (found already fixed pre-round), F8 | `./gradlew build` → SUCCESSFUL; `bootRun` re-run, no regression |
| 3 — VPD leakage | F5, F6 | `./gradlew build` → SUCCESSFUL; `bootRun` re-run, no regression |
| 4 — DLQ/retry counters | F10 (found already fixed pre-round), F15 | `./gradlew build` → SUCCESSFUL; `bootRun` re-run, no regression |
| 5 — real ingestion pipeline | F18, F11, F20 | Real chunk/embed/index data flow (`DocumentChunker`, `ChunkMetadataRepository` + Flyway `V6`, NER/embedding runtime adapters). F11/F20 fixed together: the dead `EMBED_PENDING` branch in `nextPendingStage` (F20) *was* the F11 stall — after embedding, a job's status is already `INDEX_PENDING`, which that switch never matched, so no INDEX row was ever enqueued and chunks never reached Qdrant. Verified by `./gradlew build --rerun-tasks` → SUCCESSFUL (12 modules) **and the project's first tests** (`PipelineStateMachineTest`, 4 tests): confirmed red against the old mapping (2 failures, including the full-pipeline walk), green after |
| 6 — remaining 9 findings (3 parallel agents) | F4, F7, F12 · F9, F14 · F16, F17, F19, F21 | Three agents worked disjoint lanes (Oracle/VPD · audit+auth · ops/runtime), then the combined tree was built as one: `./gradlew build --rerun-tasks` → SUCCESSFUL (12 modules), **18 tests, 0 failures** (`PipelineStateMachineTest` 4, `RetryPolicyTest` 8, `AuditEventListenerTest` 6). Two cross-agent inconsistencies were caught and reconciled afterwards — see below |
| — | F3 | Fixed alongside F1 (same config-binding pass); resolves F13 as a side effect |

### Fail-closed VPD (the F7 decision worth knowing about)

The first cut of the tenant predicate returned `1 = 1` — unrestricted — whenever no tenant context
was set, because two paths legitimately have no tenant: the pre-authentication login lookup (you
must read the user row to learn the tenant) and the background ingestion workers. That is a
defensible reading, and it is also **self-defeating**: F7 exists precisely to backstop a query path
that forgets its app-level tenant filter, and a fail-open predicate hands exactly that path every
tenant's rows. It would have hardened the paths that were already correct and done nothing for the
ones that aren't.

The predicate is therefore **fail-closed** (`1 = 0` on an unset context), with a narrow exemption
that must be *asked for*: `SET_NOTARIST_CTX.set_system_identity` (V004). It is invoked in exactly
two places, and each one is deliberate:

| Path | Why it cannot carry a tenant | What it does |
|---|---|---|
| `UserRepositoryImpl.findByUsername` | pre-auth login; reads the row that *reveals* the tenant | takes the system exemption |
| ingest repositories (background workers) | no principal; legitimately span all tenants | `applyPrincipalOrSystem` — principal if present, else system |

Two paths that looked like they needed the exemption but **don't**, and are strictly better for it:

- **Refresh-token rotation.** `/api/v1/auth/refresh` is `permitAll`, so there is no principal — a
  bare `findById` would have returned nothing under fail-closed and **broken token refresh for
  every user**. But the tenant *is* known: it is carried on the validated session row. So
  `UserRepository.findById(PersonId)` was **replaced** by `findByIdAndTenantId(...)`, which
  establishes the session's real tenant identity and stays database-filtered. The bare `findById`
  is gone rather than left behind as a fail-closed landmine.
- **`DOKUMEN_LEGAL`** — the sensitive legal content — is reached only from authenticated document
  handlers. It has **no exemption path at all**, so it is strictly tenant-filtered by the database.

Residual risk, stated plainly: the exemption is only as tight as its call sites. Every new caller of
`set_system_identity` widens the hole. `USER_ROLE_MAP` still has no tenant column and is isolated
only transitively (FK to a policy-filtered `NOTARIST_USER`).

### Verification honesty

`./gradlew build` and 18 passing tests prove the code **compiles and its pure logic is correct**.
They prove nothing about the SQL. No Oracle, PostgreSQL, Docker, MinIO or Ollama exists in this
environment, so: the VPD policies and predicate function, the Flyway `V7` DDL, MinIO bucket
provisioning, and the SSE token-stream/cancel path have **never been executed**. Their fixes are
review-verified only.

This matters more than usual here. F11 — a HIGH-severity stall that made the entire ingestion
pipeline inert — survived the original audit *and* four remediation rounds, because everything was
verified by "does it compile" and careful reading. Both are exactly the kind of defect that only a
live run surfaces. **The fail-closed VPD change is the highest-risk item in this batch**: if a call
site that needs the system exemption was missed, that query silently returns zero rows rather than
failing loudly. A live migration + smoke test of login, token refresh, document read and one
end-to-end ingestion is the required next step before this is called done.

**F2 and F10 note:** both were found already resolved in the working tree at the start of their respective rounds (the `autoCommit=false` line and the two-statement SKIP LOCKED race were already gone from source before I inspected them for those rounds). This report records them as fixed with the evidence of their current, correct state — no separate diff exists for them within this remediation effort.

**All 21 findings are now marked fixed.** What that does and does not mean is spelled out in
“Verification honesty” below — most of the remaining fixes are compile- and review-verified only,
because no Oracle, PostgreSQL, Docker, MinIO or Ollama exists in this environment.

---

## RUNTIME VERIFICATION ENVIRONMENT

| Capability | Result |
|---|---|
| Java 17 (`openjdk 17.0.19`) | AVAILABLE |
| Gradle offline build | AVAILABLE — used |
| Docker / docker-compose | **NOT AVAILABLE** (`which docker` → exit 1) |
| Oracle 19C | NOT AVAILABLE |
| PostgreSQL / Qdrant / MinIO / Ollama | NOT AVAILABLE |

**What was actually executed:**
- `./gradlew compileJava --offline` → **BUILD SUCCESSFUL** (all 12 modules compile; 1 deprecation note in `BM25SearchRepositoryImpl`).
- `./gradlew :notarist-web:bootJar --offline` → **BUILD SUCCESSFUL** (118 MB fat jar produced).
- `java -jar notarist-web-…​.jar` with generated RSA keys and dummy secrets → **CONTEXT FAILED TO START** (see F1). Re-run with corrected JWT property names advanced the context through *all* bean wiring and failed only on the (absent) PostgreSQL connection — confirming there is no bean-ambiguity / circular-dependency / missing-bean defect in the wiring graph, and that F1 is a deterministic, standalone startup blocker.

Anything requiring live infrastructure (scheduler execution, retry timing, DLQ escalation, VPD enforcement, SKIP LOCKED behaviour, Oracle locking) is marked **NOT VERIFIED** and reasoned from source only.

**Test suite (at time of original audit):** `find … -path "*test*" *.java` → **0 test files.** No automated test coverage existed anywhere in the repository. Remediation rounds 5–6 introduced the first tests — now **18 passing** across `PipelineStateMachineTest` (4), `RetryPolicyTest` (8) and `AuditEventListenerTest` (6). That is a beachhead, not coverage: every adapter, handler and worker remains untested.

---

## SEVERITY SUMMARY

| # | Severity | Finding | Verified | Remediation status |
|---|---|---|---|---|
| F1 | CRITICAL | JWT config property namespace mismatch → application cannot start | VERIFIED (runtime) | ✅ **FIXED** — `application.yaml` rebound to `notarist.auth.jwt.*` (seconds, no `file:` prefix) |
| F2 | CRITICAL | Ingest PostgreSQL datasource has `autoCommit=false` with no transaction manager → queue writes never commit | VERIFIED (static) | ✅ **FIXED** — `setAutoCommit(false)` removed; pool now uses Hikari's `autoCommit=true` default |
| F3 | CRITICAL | Ingest datasource bound to undefined `notarist.postgres.*` namespace → wrong DB/credentials | VERIFIED (static) | ✅ **FIXED** — `notarist.postgres.*` / `notarist.minio.*` added to `application.yaml`, aligned to the same DB/credentials as `notarist.database.postgres.*` |
| F4 | CRITICAL | VPD policy function `TENANT_ISOLATION_POLICY` referenced but never created | VERIFIED (static) | ✅ **FIXED** — new Liquibase `V005__vpd_tenant_policies.xml` creates `NOTARIST.TENANT_ISOLATION_POLICY`, reading the same `NOTARIST_CTX`/`TENANT_ID` attribute the V004 trusted package actually writes. **SQL never executed — no Oracle available.** |
| F5 | HIGH | VPD application context bound to package `SET_NOTARIST_CTX` that is never created; Java calls `DBMS_SESSION.SET_CONTEXT` directly | VERIFIED (static) / behaviour NOT VERIFIED | ✅ **FIXED** — new Liquibase `V004__vpd_context_package.xml` creates the trusted package; appliers now call `SET_NOTARIST_CTX.set_identity(...)` |
| F6 | HIGH | VPD `SET_CONTEXT` runs outside any transaction → set on a different connection than the query; context never cleared | VERIFIED (static) | ✅ **FIXED** — appliers require an active transaction and register a `clear_identity()` completion hook; Oracle repositories now `@Transactional` |
| F7 | HIGH | VPD row-level policy applied only to `INGESTION_JOB`; `DOKUMEN_LEGAL` and `NOTARIST_USER` have none | VERIFIED (static) | ✅ **FIXED** — policies added for `DOKUMEN_LEGAL` and `NOTARIST_USER`, and the predicate is **fail-closed** (no tenant identity ⇒ no rows). See “Fail-closed VPD” below: a fail-open predicate would have left F7’s actual complaint (a forgotten filter leaks cross-tenant rows) unaddressed. `USER_ROLE_MAP` has no tenant column — isolated transitively via FK; recorded as a residual gap. **SQL never executed.** |
| F8 | HIGH | Refresh-token rotation is not atomic → token-reuse / double-issue race | VERIFIED (static) | ✅ **FIXED** — added `SessionTokenRepository.invalidateIfActive(...)`, an atomic `UPDATE … WHERE invalidated=false` compare-and-set gating token issuance |
| F9 | HIGH | Audit events published to the event bus are never consumed/persisted → total audit-trail loss | VERIFIED (static) | ✅ **FIXED** — `AuditEventListener` (+ `RecordAuditEventHandler`, `AuditTrailRepositoryImpl`) now consume and persist `AuditEventPayload` to PostgreSQL `audit_trail` (Flyway `V7`). Plain `@EventListener`, not `AFTER_COMMIT` — a failed login has no committing tx and would otherwise be lost. Fail-closed for AUTH/SECURITY/DOCUMENT. **DDL never executed.** |
| F10 | HIGH | Queue `FOR UPDATE SKIP LOCKED` is ineffective as used (select + lock on separate connections, non-transactional poll) | VERIFIED (static) | ✅ **FIXED** — dequeue is now a single atomic `UPDATE … WHERE queue_job_id IN (SELECT … FOR UPDATE SKIP LOCKED) RETURNING …` statement |
| F11 | HIGH | Pipeline stalls after EMBED: INDEX stage is never enqueued (state-machine asymmetry) | VERIFIED (static) | ✅ **FIXED** — `nextPendingStage` now routes the post-embed status (`INDEX_PENDING`) to the INDEX stage; covered by `PipelineStateMachineTest` |
| F12 | MEDIUM | Oracle `DataSource` is an unpooled skeleton (`TODO`); ignores configured pool sizing | VERIFIED (static) | ✅ **FIXED** — `DataSourceConfig` now builds a `HikariDataSource` honouring `pool-max`/`pool-min` plus connection/idle/max-lifetime timeouts. |
| F13 | MEDIUM | Two divergent PostgreSQL pools; `ingestion_queue` migrated into search DB but read from a different default DB | VERIFIED (static) | ✅ **FIXED** (side effect of F3) — both pools now default to the same `notarist_search` DB/credentials |
| F14 | MEDIUM | In-memory `TokenDenyList` → revoked tokens accepted by other instances / after restart | VERIFIED (static) | ✅ **FIXED** — `ConcurrentHashMap` replaced by a PostgreSQL-backed `token_deny_list` (Flyway `V7`); revocation now survives restart and is shared across instances. Redis deliberately not introduced (not in the stack). |
| F15 | MEDIUM | Retry counter double-bookkeeping (queue `attempt_count` vs job `retryCount`) | VERIFIED (static) | ✅ **FIXED** — `job.retryCount` is now the single counter driving both the coordinator's retry decision and the queue mirror; queue row is marked terminal `FAILED` instead of re-`PENDING`, leaving `RetryPolicyService` as the sole re-enqueue driver |
| F16 | MEDIUM | MinIO credential default mismatch + buckets never provisioned | VERIFIED (static) | ✅ **FIXED** — `MinioBucketProvisioner` (`ApplicationRunner`) creates the five buckets if absent, marking the service degraded rather than failing silently; the competing credential default was removed entirely (`MINIO_SECRET_KEY` now has **no default**, so compose fails loudly via `:?`). **Never executed — no Docker/MinIO available.** |
| F17 | MEDIUM | SSE endpoint does not stream and does not cancel inference on client disconnect | VERIFIED (static) | ✅ **FIXED** — `askStream` now emits real tokens via the runtime streaming path and registers `onTimeout`/`onError`/`onCompletion` hooks that cancel in-flight inference through `StreamingCancellationManager`. Two extra races fixed while wiring (cancel-before-register; a cancelled call marking the whole OLLAMA runtime degraded). **Never executed — no Ollama available.** |
| F18 | LOW | Core ingestion workers (OCR/NER/Chunk/Embed/Index) are stubs → `chunk_index` never populated, search returns nothing | VERIFIED (static) | ✅ **FIXED** — real `DocumentChunker`, `ChunkMetadataRepository` (+ Flyway `V6`), NER/embedding runtime adapters; `IndexingWorker` now upserts real per-chunk embeddings and carries `searchable` (OCR gating) into Qdrant instead of a stub zero-vector |
| F19 | LOW | `RetryPolicy` backoff off-by-one vs its own contract | VERIFIED (static) | ✅ **FIXED** — `base * 2^(attempt-1)` ⇒ 30/60/120s, matching the javadoc; retry-count semantics and the 3600s cap unchanged. Pinned by `RetryPolicyTest` (8 tests, confirmed red against the old behaviour first). |
| F20 | LOW | Dead/unreachable branch in `PipelineStateMachine.nextPendingStage` | VERIFIED (static) | ✅ **FIXED** — the unreachable `EMBED_PENDING` branch was the F11 root cause; removed by the same fix |
| F21 | LOW | Prometheus actuator endpoint exposed but not permitted → self-scrape 401 | VERIFIED (static) | ✅ **FIXED** — `/actuator/prometheus` + `/actuator/metrics` permitted for loopback/RFC1918 sources only (previously `/actuator/metrics` was fully public, now tightened). Defence-in-depth, not a substitute for network policy. |

**Fixed: 21 of 21.** Every finding from the original audit is now addressed in source. Read
“Verification honesty” above before treating that as a clean bill of health: the SQL-, Docker- and
LLM-dependent fixes (F4, F7, F9, F14, F16, F17) are review-verified only — nothing exercised them
against live infrastructure, which is precisely how the HIGH-severity F11 stall survived an audit
and four remediation rounds.

---

## DETAILED FINDINGS

### F1 — CRITICAL — Application cannot start: JWT config property namespace mismatch
**File:** `backend/notarist-auth/src/main/java/com/notarist/auth/application/service/JwtService.java`
**Class/Method:** `JwtService` constructor
**Evidence (consumer):**
```java
public JwtService(
    @Value("${notarist.auth.jwt.private-key-path}") String privateKeyPath,
    @Value("${notarist.auth.jwt.public-key-path}") String publicKeyPath,
    @Value("${notarist.auth.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds) {
```
`AuthenticateUserHandler` / `RefreshTokenHandler` additionally read `${notarist.auth.jwt.refresh-token-ttl-seconds:604800}`.
**Evidence (only definition that exists) — `application.yaml`:**
```yaml
notarist:
  security:
    jwt:
      issuer: ...
      access-token-ttl-minutes: ...
      private-key-path: ${JWT_PRIVATE_KEY_PATH:file:./keys/notarist-private.pem}
      public-key-path:  ${JWT_PUBLIC_KEY_PATH:...}
```
The config tree is `notarist.security.jwt.*`; the code reads `notarist.auth.jwt.*`. The latter is defined **nowhere**, and `private-key-path` has no default, so the placeholder is unresolvable.
**Runtime proof (executed):**
```
Caused by: java.lang.IllegalArgumentException: Could not resolve placeholder
  'notarist.auth.jwt.private-key-path' in value "${notarist.auth.jwt.private-key-path}"
  ... Error creating bean with name 'jwtService' ...
```
**Why it is a production issue:** the Spring context aborts during refresh; the service never accepts traffic. The `JWT_ACCESS_TOKEN_TTL_MINUTES` / `JWT_REFRESH_TOKEN_TTL_DAYS` env vars are also disconnected (minutes/days in yaml vs seconds in code), so even after fixing the namespace, TTL units are wrong.
**How it happens:** every boot, unconditionally.
**Status: VERIFIED (runtime).**

**✅ REMEDIATION (2026-07-13):** `application.yaml` now defines `notarist.auth.jwt.*` (matching what `JwtService` reads) with TTLs in seconds (`access-token-ttl-seconds`, `refresh-token-ttl-seconds`) and key-path defaults without the unresolvable `file:` prefix (`JwtService` loads keys via `Files.readString(Paths.get(...))`, which cannot open a `file:` URI). `.env.example` updated to `JWT_ACCESS_TOKEN_TTL_SECONDS` / `JWT_REFRESH_TOKEN_TTL_SECONDS`. Re-verified: `./gradlew build --offline` → BUILD SUCCESSFUL; `bootRun` with generated RSA keys advances through all bean wiring (`jwtService` constructs successfully, 0 placeholder-resolution errors in the log) and stops only at the external PostgreSQL connection (`Connection to localhost:5432 refused`), which is out of scope per the remediation task. NOT VERIFIED: end-to-end login/token-issue against a live app (needs PostgreSQL/Oracle).

---

### F2 — CRITICAL — Ingest queue writes are never committed (autoCommit=false, no transaction manager)
**File:** `backend/notarist-ingest/src/main/java/com/notarist/ingest/config/IngestModuleConfig.java`
**Class/Method:** `ingestPostgresDataSource(...)`
**Evidence:**
```java
config.setAutoCommit(false);
return new HikariDataSource(config);
...
@Bean("ingestJdbcTemplate")
public JdbcTemplate ingestJdbcTemplate(@Qualifier("ingestPostgresDataSource") DataSource ds) { ... }
```
`IngestionQueueRepositoryImpl` and `DeadLetterRepositoryImpl` execute `INSERT`/`UPDATE` through this `ingestJdbcTemplate`. **No `PlatformTransactionManager` bean is defined for this datasource** (`grep` for `TransactionManager` across the backend returns none). The `@Transactional` annotations in `PipelineCoordinator`, `RetryPolicyService`, `DeadLetterHandler`, `UploadOrchestrationService` bind to the **auto-configured JPA transaction manager on the Oracle `@Primary` datasource**, not to the ingest Postgres connection.
**Why it is a production issue:** a `JdbcTemplate` write on a HikariCP connection with `autoCommit=false` and no surrounding managed transaction is never committed. HikariCP rolls back "dirty" non-autocommit connections on return to the pool. Result: `enqueue`, `markCompleted`, `markFailed`, `moveToDlq`, DLQ inserts silently vanish — the ingestion pipeline cannot persist any queue state. **Production data loss.**
**How it happens:** every ingest enqueue/dequeue/state transition.
**Status: VERIFIED (static).** Full commit/rollback behaviour NOT VERIFIED at runtime (no DB), but the missing transaction boundary is unambiguous in source.

**✅ REMEDIATION (2026-07-13):** `config.setAutoCommit(false)` has been removed from `ingestPostgresDataSource(...)`; the pool now uses HikariCP's default `autoCommit=true`, with a code comment recording the rationale (no `PlatformTransactionManager` exists for this datasource, so autocommit is required for `JdbcTemplate` writes to persist). This was found already corrected in the working tree at the time of this remediation round — verified by `grep`-confirming no `setAutoCommit(false)` call remains anywhere in the backend, and by reading the current bean method in full. Re-verified: `./gradlew build --offline` → BUILD SUCCESSFUL. NOT VERIFIED at runtime (no PostgreSQL available) — actual commit behaviour of `enqueue`/`markCompleted`/`markFailed`/`moveToDlq` against a live database was not exercised.

---

### F3 — CRITICAL — Ingest datasource reads an undefined property namespace
**File:** `backend/notarist-ingest/src/main/java/com/notarist/ingest/config/IngestModuleConfig.java`
**Evidence:**
```java
@Value("${notarist.postgres.url:jdbc:postgresql://localhost:5432/notarist}") String url,
@Value("${notarist.postgres.username:notarist}") String username,
@Value("${notarist.postgres.password:notarist}") String password,
```
`application.yaml` defines `notarist.database.postgres.*` (bound by `PostgresProperties`), **not** `notarist.postgres.*`. `.env.example` sets only `POSTGRES_URL` (→ `notarist.database.postgres.url`). Therefore the ingest pool always falls back to `jdbc:postgresql://localhost:5432/notarist` with `notarist/notarist`, while the real database (compose) is `notarist_search` with `notarist_app`.
**Why it is a production issue:** the ingest pool connects to a non-existent database/credentials → pipeline non-functional; compounds F2/F13.
**Status: VERIFIED (static).**

**✅ REMEDIATION (2026-07-13):** `application.yaml` now defines `notarist.postgres.*` and `notarist.minio.*` (the exact namespaces `IngestModuleConfig` reads), pointed at the **same** database (`notarist_search`), credentials, and MinIO endpoint as `notarist.database.postgres.*` / `notarist.storage.minio.*`, eliminating the namespace drift. This also resolves F13 (the two pools no longer diverge by default) and the credential-mismatch half of F16. Re-verified: `./gradlew build --offline` → BUILD SUCCESSFUL. NOT VERIFIED at runtime — confirming both pools actually reach the same live database was not possible (no PostgreSQL available).

---

### F4 — CRITICAL — VPD policy function referenced but never created
**File:** `database/oracle/liquibase/changes/V002__ingest_schema.xml` (changeset `V002-04`)
**Evidence:**
```sql
DBMS_RLS.ADD_POLICY(
    object_schema   => 'NOTARIST',
    object_name     => 'INGESTION_JOB',
    policy_name     => 'INGEST_JOB_TENANT_POLICY',
    function_schema => 'NOTARIST',
    policy_function => 'TENANT_ISOLATION_POLICY',   -- never defined
    statement_types => 'SELECT,INSERT,UPDATE,DELETE',
    update_check    => TRUE, enable => TRUE);
```
`grep -rin "TENANT_ISOLATION_POLICY|CREATE OR REPLACE FUNCTION|CREATE OR REPLACE PACKAGE" database/` finds **only this reference** — the policy function is never created anywhere.
**Why it is a production issue:** `ADD_POLICY` registers a predicate function that does not exist. Every subsequent DML on `NOTARIST.INGESTION_JOB` raises `ORA-28110 / ORA-28112` (policy function error). Combined with `ddl-auto: none` and Liquibase disabled by default, this only bites when migrations are actually enabled — at which point either the changeset or all `INGESTION_JOB` access fails.
**Status: VERIFIED (static — function absence).** Exact ORA error NOT VERIFIED (no Oracle).

---

### F5 — HIGH — VPD context bound to a non-existent package; Java sets it the wrong way
**Files:** `database/oracle/liquibase/changes/V001__initial_notarist_schema.xml` (changeset `002`), `backend/…/auth|document|ingest/infrastructure/security/VpdContextApplier.java`
**Evidence (DDL):**
```sql
CREATE OR REPLACE CONTEXT NOTARIST_CTX USING NOTARIST.SET_NOTARIST_CTX EXTERNALLY INITIALIZED
```
`SET_NOTARIST_CTX` (the trusted package that is supposed to own this namespace) is **never created**. 
**Evidence (Java):**
```java
"{call DBMS_SESSION.SET_CONTEXT(?, ?, ?)}"   // NOTARIST_CTX / USER_ID|TENANT_ID|USER_ROLE
```
An application context declared `USING <package>` may only be written from within that package. Calling `DBMS_SESSION.SET_CONTEXT` for that namespace from arbitrary JDBC is rejected (`ORA-01031`).
**Why it is a production issue:** the tenant-isolation context can never be populated → any VPD predicate that reads `SYS_CONTEXT('NOTARIST_CTX', …)` sees NULL. Depending on the (missing) policy function this fails open or closed. The whole VPD "second line of defence" advertised in `SecurityFilterService` javadoc is inert.
**Status: VERIFIED (static — package absence & API mismatch). Runtime behaviour NOT VERIFIED.**

**✅ REMEDIATION (2026-07-13):** New Liquibase changeset `V004__vpd_context_package.xml` (mirrored into both `backend/notarist-web/.../db/oracle/changelog/changes/` and `database/oracle/liquibase/changes/`) creates `NOTARIST.SET_NOTARIST_CTX` — the exact trusted package the `V001` context declaration (`USING NOTARIST.SET_NOTARIST_CTX`) already required — with a `set_identity(user_id, tenant_id, role)` procedure and a `clear_identity()` procedure, plus a grant to the application user. All three `VpdContextApplier` copies (auth/document/ingest) now call `{ call NOTARIST.SET_NOTARIST_CTX.set_identity(?, ?, ?) }` instead of raw `DBMS_SESSION.SET_CONTEXT`. Re-verified: `./gradlew build --offline` → BUILD SUCCESSFUL. **NOT VERIFIED:** the changeset was not applied against a live Oracle 19C instance (none available) — the PL/SQL was written to match the existing context declaration but its actual execution (package compiles, grant succeeds, `set_identity` writes are accepted) is unverified.

---

### F6 — HIGH — VPD SET_CONTEXT executed outside a transaction / never cleared
**File:** `backend/notarist-document/…/DocumentLegalRepositoryImpl.java` (also auth & ingest equivalents)
**Evidence:**
```java
@Override public Optional<DocumentLegal> findById(DocumentId id) {
    vpdContextApplier.applyIfPresent(entityManager);          // opens/uses connection A
    return jpaRepository.findById(id.value().toString())...   // may use connection B
}
```
Neither the repository methods nor their callers (`GetDocumentQueryHandler`, `ListDocumentsQueryHandler`) are `@Transactional`, and `spring.jpa.open-in-view=false`. `applyIfPresent` runs `SET_CONTEXT` via `Session.doWork` on a connection obtained for that call; the subsequent JPA query may borrow a different connection where the context was never set. Oracle contexts are connection-scoped. Additionally `VpdContextApplier` **only sets** context — there is no `CLEAR_CONTEXT` on request completion.
**Why it is a production issue:** (a) VPD is not reliably in effect for the actual query; (b) if the Oracle datasource is ever pooled (see F12 `TODO`), the un-cleared USER_ID/TENANT_ID leak to the next borrower of that connection → **cross-tenant data exposure**. Tenant isolation currently rests entirely on the in-code `WHERE tenant_id = ?` / `SecurityFilterService`, not on VPD.
**Status: VERIFIED (static). Cross-connection/leak behaviour NOT VERIFIED at runtime.**

**✅ REMEDIATION (2026-07-13):** Two changes close the transaction/leak gap: (1) `UserRepositoryImpl`, `DocumentLegalRepositoryImpl`, `IngestionJobRepositoryImpl` are now class-level `@Transactional`, guaranteeing the identity-set call and the subsequent JPA query share one Oracle connection; (2) all three `VpdContextApplier` copies now check `TransactionSynchronizationManager.isSynchronizationActive()` before setting identity (skip-and-warn rather than set-and-leak if no transaction is active), and register a `TransactionSynchronization.beforeCompletion()` hook that calls `SET_NOTARIST_CTX.clear_identity()` on the same connection before it returns to the pool — this directly targets the leak scenario called out above (F12's still-unpooled `OracleDataSource` means the leak is not currently exploitable in practice, but the fix removes the defect ahead of that becoming pooled). Re-verified: `./gradlew build --offline` → BUILD SUCCESSFUL; `bootRun` shows 0 errors on `userRepositoryImpl` / `documentLegalRepositoryImpl` / `ingestionJobRepositoryImpl` / `vpdContextApplier` beans, no circular-dependency or `BeanCurrentlyInCreation` errors. **NOT VERIFIED:** actual clear-on-completion behaviour and absence of cross-connection leakage under load require a live, pooled Oracle connection — not available in this environment.

---

### F7 — HIGH — VPD policy covers only one table
**File:** `database/oracle/liquibase/changes/*.xml`
**Evidence:** exactly one `DBMS_RLS.ADD_POLICY` call exists (on `INGESTION_JOB`). `DOKUMEN_LEGAL`, `NOTARIST_USER`, `USER_ROLE_MAP`, audit tables have no VPD policy.
**Why it is a production issue:** document and user tables — the sensitive legal content — have no database-enforced tenant isolation, contradicting the platform's stated security model. Any query path that forgets the app-level tenant filter returns cross-tenant rows.
**Status: VERIFIED (static).**

---

### F8 — HIGH — Refresh-token rotation is not atomic (token-reuse race)
**File:** `backend/notarist-auth/…/RefreshTokenHandler.java`
**Class/Method:** `RefreshTokenHandler.execute(...)`
**Evidence:** the method is **not** `@Transactional` and performs read → invalidate → save as separate statements:
```java
Session existing = sessionTokenRepository.findByRefreshTokenHash(tokenHash)...;
if (!existing.isValid()) { throw ... }
...
sessionTokenRepository.invalidate(existing.getSessionId());   // UPDATE
...
sessionTokenRepository.save(newSession);                      // INSERT
```
`findByRefreshTokenHash` selects `WHERE invalidated=false` with no locking; there is no atomic compare-and-set.
**Why it is a production issue:** two concurrent requests presenting the same refresh token both pass the validity check, both invalidate, and **both mint new access+refresh tokens** — classic refresh-token replay / double-session. Also, if `save` fails after `invalidate`, the user is left with no valid session (lockout) since the two writes are not in one transaction.
**Status: VERIFIED (static — absence of locking/transaction).**

**✅ REMEDIATION (2026-07-13):** Added `SessionTokenRepository.invalidateIfActive(SessionId)`, implemented as an atomic compare-and-set: `UPDATE session_token SET invalidated = true WHERE session_id = ? AND invalidated = false`, returning `true` only if it affected exactly 1 row. `RefreshTokenHandler.execute(...)` now calls this in place of the unconditional `invalidate(...)` and throws `AUTH_REFRESH_TOKEN_REUSED` (401) if it returns `false`. Under concurrent refresh of the same token, Postgres serializes the two conditional `UPDATE`s on the row — exactly one request "wins" (1 row affected) and proceeds to mint new credentials; every other request "loses" (0 rows affected) and is rejected, closing the double-issue race. The existing `invalidate(...)` method is kept unchanged for `LogoutHandler` (unconditional invalidation is correct there). Re-verified: `./gradlew build --offline` → BUILD SUCCESSFUL; `bootRun` shows 0 errors involving `sessionTokenRepositoryImpl` / `refreshTokenHandler`. **NOT VERIFIED:** the actual concurrent-request race (two simultaneous refresh calls against a live database) was not exercised — no PostgreSQL available, and no test suite exists to simulate it.

---

### F9 — HIGH — Audit events are published but never persisted (audit-trail loss)
**Files:** publishers in `notarist-auth`, `notarist-document`, `notarist-ingest`; port `notarist-audit/…/AuditTrailRepository.java`
**Evidence:** login success/failure, token refresh, logout, document access, and ingestion lifecycle all call `eventPublisher.publishEvent(new AuditEventPayload(...))`. A repository-wide search shows **no `@EventListener` consuming `AuditEventPayload`**, **no implementation of `AuditTrailRepository`**, and **no implementation of `RecordAuditEventUseCase`**. The `notarist-audit` module contains only ports and domain records.
**Why it is a production issue:** for a notary/PPAT platform, the security & access audit trail (including failed logins and confidential-document reads) is a compliance requirement. Every audit event is delivered to the in-VM event bus and silently dropped. **Production data loss / compliance gap.**
**Status: VERIFIED (static).**

---

### F10 — HIGH — `FOR UPDATE SKIP LOCKED` is ineffective as used
**File:** `backend/notarist-ingest/…/IngestionQueueRepositoryImpl.java`
**Evidence:**
```java
List<QueueRecord> records = postgresJdbcTemplate.query(SQL_DEQUEUE_SKIP_LOCKED, this::mapRow, stage, limit);
for (QueueRecord r : records) postgresJdbcTemplate.update(SQL_LOCK_WORKER, workerId, r.queueJobId());
```
`SQL_DEQUEUE_SKIP_LOCKED` uses `FOR UPDATE SKIP LOCKED`, but the caller `IngestionQueueScheduler.pollAndDispatch()` is **not** `@Transactional`. Each `JdbcTemplate` call gets its own connection; the row locks acquired by the `SELECT … FOR UPDATE` are released (or stranded on an uncommitted connection per F2) before the separate `UPDATE … SET status='PROCESSING'` runs, potentially on a different connection.
**Why it is a production issue:** the lock provides no mutual exclusion — two pollers (multiple app instances, or the retry re-enqueuer racing the scheduler) can select and dispatch the same `queue_job_id`, causing duplicate stage processing. The select-then-update also has a check-then-act gap.
**Status: VERIFIED (static). Concurrency outcome NOT VERIFIED at runtime.**

**✅ REMEDIATION (2026-07-13):** `dequeueForProcessing(...)` now issues one atomic statement — `UPDATE ingestion_queue SET status='PROCESSING', locked_by=?, locked_at=NOW() WHERE queue_job_id IN (SELECT queue_job_id FROM ingestion_queue WHERE target_stage=? AND status='PENDING' AND scheduled_at<=NOW() ORDER BY scheduled_at FOR UPDATE SKIP LOCKED LIMIT ?) RETURNING …` — eliminating the separate select-then-update pair and the connection-boundary gap between them. A code comment documents that this single-statement design is required because the datasource runs with autocommit and no transaction manager (see F2). This was found already corrected in the working tree at the start of this remediation round — verified by reading the current repository source in full. Re-verified: `./gradlew build --offline` → BUILD SUCCESSFUL. **NOT VERIFIED:** actual mutual exclusion under concurrent pollers against a live PostgreSQL instance was not exercised.

---

### F11 — HIGH — Pipeline stalls after EMBED: INDEX stage is never enqueued
**File:** `backend/notarist-ingest/…/domain/service/PipelineStateMachine.java` + `PipelineCoordinator.enqueueNextStage`
**Evidence:**
```java
// completedStageFor: EMBED_PENDING -> INDEX_PENDING   (note: skips an EMBED_COMPLETED state)
// nextPendingStage:  CHUNK_COMPLETED -> EMBED_PENDING
//                    EMBED_PENDING   -> INDEX_PENDING
//                    default         -> Optional.empty()
```
In `PipelineCoordinator.process`, after a successful EMBED stage `completedStatus = completedStageFor(EMBED_PENDING) = INDEX_PENDING`, then `enqueueNextStage(job, INDEX_PENDING)` calls `nextPendingStage(INDEX_PENDING)` → **`Optional.empty()`**. No `INDEX_PENDING` queue row is ever inserted, so the scheduler (which polls the queue table) never runs the indexing stage.
**Why it is a production issue:** documents reach `INDEX_PENDING` job status and stop — they are never indexed or marked `COMPLETED`. The `EMBED_PENDING -> INDEX_PENDING` branch of `nextPendingStage` is unreachable because the method is only ever called with `completedStageFor(...)` output, which is never `EMBED_PENDING`.
**Status: VERIFIED (static).**

---

### F12 — MEDIUM — Oracle DataSource is an unpooled skeleton
**File:** `backend/notarist-web/…/config/DataSourceConfig.java`
**Evidence:**
```java
// TODO (STEP 8B): configure proper connection pooling (HikariCP), VPD context setter.
@Bean @Primary public DataSource oracleDataSource() {
    OracleDataSource ds = new OracleDataSource();
    ds.setURL(oracleUrl); ds.setUser(...); ds.setPassword(...);
    return ds;  // no pool
}
```
`oracle.jdbc.pool.OracleDataSource` is not a connection pool. `notarist.database.oracle.pool-max/pool-min` from `application.yaml` are ignored.
**Why it is a production issue:** connection churn under load, no bounded pool, no timeouts; the acknowledged `TODO` means VPD context injection is also unimplemented here. Directly enables the F6 leak scenario if a real pool is dropped in without a context-clear.
**Status: VERIFIED (static).**

---

### F13 — MEDIUM — Divergent PostgreSQL pools / migration-vs-runtime DB split
**Files:** `PostgresConnectionConfig` (`notarist.database.postgres.*`, default `notarist_search`) vs `IngestModuleConfig` (`notarist.postgres.*`, default `notarist`); `FlywaySearchConfig`.
**Evidence:** `FlywaySearchConfig` runs all Flyway migrations (including `V2` `ingestion_queue`, `dead_letter_queue`) against `postgresDataSource` (search DB). The ingest repositories write via `ingestJdbcTemplate` (a *second* pool, different default DB — F3). Two independent Hikari pools to (by default) two different databases.
**Why it is a production issue:** the `ingestion_queue`/`dead_letter_queue` tables are created in the search DB but read/written from a different connection target → "relation does not exist" at runtime unless both namespaces are manually pointed at the same DB. Configuration drift.
**Status: VERIFIED (static).**

**✅ REMEDIATION (2026-07-13, side effect of F3 fix):** the F3 fix (adding `notarist.postgres.*` bound to the same URL/credentials as `notarist.database.postgres.*`) resolves this directly — both Hikari pools now default to `notarist_search` with `notarist_app`. See F3 remediation note. Re-verified via the same `application.yaml` diff and build/boot checks as F3. **NOT VERIFIED:** confirming both pools actually reach the same live schema was not possible (no PostgreSQL available).

---

### F14 — MEDIUM — In-memory token deny-list defeats revocation across instances/restarts
**File:** `backend/notarist-auth/…/cache/TokenDenyListImpl.java`
**Evidence:** `ConcurrentHashMap<String, Instant> deniedTokens` with a `@Scheduled(fixedDelay=300_000)` evictor; class javadoc says "Acceptable for single-instance deployments … replace with Redis for multi-node."
**Why it is a production issue:** logout/JTI revocation is only honoured by the instance that processed the logout and is lost on restart. Behind a load balancer, a revoked-but-unexpired access token is still accepted by peers until natural expiry.
**Status: VERIFIED (static).**

---

### F15 — MEDIUM — Retry counter double-bookkeeping
**File:** `backend/notarist-ingest/…/coordinator/PipelineCoordinator.java` (`handleStageFailure`) and `RetryPolicyService`
**Evidence:** `PipelineCoordinator` computes `attemptCount = queueRecord.attemptCount()+1` and calls `queueRepository.markFailed(..., attemptCount, ...)`, while separately `job.scheduleRetry()` increments the **independent** `IngestionJob.retryCount`. `RetryPolicyService.reEnqueueEligibleJobs` then decides DLQ escalation from `job.getRetryCount()`, and `RetryPolicy.shouldRetry` in the coordinator uses the queue `attemptCount`. Two counters advance on different paths.
**Why it is a production issue:** max-retry enforcement is inconsistent — a job can be retried more or fewer times than `max-retries` depending on which path drives it, and the queue row and job aggregate disagree.
**Status: VERIFIED (static).**

**✅ REMEDIATION (2026-07-13):** `job.retryCount` is now the single source of truth. `PipelineCoordinator.handleStageFailure` decides retry eligibility via `RetryPolicy.shouldRetry(job.getRetryCount(), maxRetries)` (was the independent `queueRecord.attemptCount()+1`), and mirrors that same counter onto the queue row instead of tracking it separately. The queue's `markFailed` now sets the claimed row to a terminal `status='FAILED'` (was `'PENDING'`, which had created a second, competing retry driver), so `RetryPolicyService` — which already keyed off `job.retryCount` — is the sole path that re-enqueues (`FAILED → retry stage`, fresh `PENDING` row). This also removes a latent invalid-state-transition risk where the coordinator's re-`PENDING` row could be re-dequeued while the job aggregate was still `FAILED`. Re-verified: `./gradlew build --offline` → BUILD SUCCESSFUL; `bootRun` shows 0 errors on `pipelineCoordinator` / `ingestionQueueRepositoryImpl` / `retryPolicyService`. **NOT VERIFIED:** actual retry-count convergence and DLQ-escalation timing across a live queue were not exercised (no PostgreSQL available, no test suite).

---

### F16 — MEDIUM — MinIO credential default mismatch and no bucket provisioning
**Files:** `application.yaml`, `infra/docker/docker-compose.yml`, `MinioClientConfig`/`MinioDocumentStorageAdapter`
**Evidence:** app default `MINIO_SECRET_KEY:minioadmin`; compose default `MINIO_ROOT_PASSWORD:change-me-in-production`. No code or compose step creates buckets `notarist-raw/ocr/processed/chunk/export`.
**Why it is a production issue:** with defaults, the client authenticates with `minioadmin/minioadmin` against a server expecting `minioadmin/change-me-in-production` → 403; and presigned-URL/`statObject` calls target non-existent buckets → upload flow fails.
**Status: VERIFIED (static).**

**⚠️ PARTIAL REMEDIATION (2026-07-13, side effect of F3 fix):** the ingest module's `notarist.minio.*` binding (previously unresolved — see F3) is now defined in `application.yaml`, aligned to the same `notarist.storage.minio.*` endpoint/access-key/secret-key values, so the two MinIO client configurations in the app no longer drift from each other. **This does not fix the underlying issue** that the shipped defaults (`minioadmin` / `change-me-in-production`) still mismatch by design (a placeholder credential, not a real one) and that **no bucket provisioning step exists anywhere** (`notarist-raw/ocr/processed/chunk/export` are never created by code or compose). Both require an operator to either override the env vars consistently or add a bucket-bootstrap step — out of scope for a config-binding-only remediation. **Remains OPEN for the bucket-provisioning half; the namespace-drift half is fixed.**

---

### F17 — MEDIUM — SSE endpoint neither streams nor cancels on disconnect
**File:** `backend/notarist-assistant/…/api/rest/AssistantController.java` (`askStream`)
**Evidence:**
```java
SseEmitter emitter = new SseEmitter(60_000L);
SSE_POOL.submit(() -> { AssistantResponse response = assistantUseCase.ask(command);  // blocking, non-streaming
                        responseStreamer.stream(response, emitter); ... });
```
The endpoint calls the **blocking** `ask` and only chunks the finished text by sentence. The token-level `OllamaRuntimeAdapter.stream(...)` / `StreamingCancellationManager` path is not invoked here. There is no `emitter.onTimeout`/`onCompletion` hook to cancel the underlying work.
**Why it is a production issue:** no real streaming UX; if the client disconnects or the 60s emitter times out, the LLM inference (a scarce single-threaded resource per `InferenceQueueIsolation`) keeps running on `SSE_POOL`, wasting capacity. Under load this starves the inference queue.
**Status: VERIFIED (static).**

---

### F18 — LOW (functional CRITICAL for the RAG use-case) — Core pipeline workers are stubs
**Files:** `OcrServiceAdapter`, `EmbeddingAdapter`, `VectorIndexAdapter`, `NerServiceAdapter`, `ChunkWorker`, `EmbeddingWorker`, `IndexingWorker`
**Evidence:** these classes return deterministic stub data (`"[STUB] chunk text…"`, zero-vectors, no-op Qdrant upsert) and comments state "Replace with real … in Phase 2C." `ChunkWorker` produces 5 fake chunks; `IndexingWorker` upserts a `new float[1024]` zero vector; the active `EmbeddingAdapter` (@Component) returns zero vectors (the real `EmbeddingRuntimeWorker` is wired only for **query-time** embedding via `QueryEmbeddingRuntimeAdapter`).
**Why it is a production issue:** no real text is ever chunked, embedded, or written to `chunk_index`/Qdrant, so BM25 and semantic retrieval return nothing for ingested documents. The product's core function is non-operational. Marked LOW only because it is clearly labelled in-progress, not a latent defect.
**Status: VERIFIED (static).**

---

### F19 — LOW — Retry backoff off-by-one vs documented contract
**File:** `backend/notarist-ingest/…/domain/service/RetryPolicy.java`
**Evidence:** javadoc says "attempt=1 → 30s"; code computes `BASE_DELAY_SECONDS * (1L << min(attemptCount,6))` = `30 * 2^1 = 60s` for attempt 1.
**Why it is a production issue:** minor — first retry waits 60s not 30s; documentation/behaviour mismatch, no correctness impact.
**Status: VERIFIED (static).**

---

### F20 — LOW — Dead/unreachable branch in state machine
**File:** `PipelineStateMachine.nextPendingStage`
**Evidence:** the `case EMBED_PENDING -> Optional.of(INDEX_PENDING)` branch is never reached (see F11); it is dead code masking the F11 stall.
**Status: VERIFIED (static).**

---

### F21 — LOW — Prometheus endpoint exposed but not permitted
**File:** `application.yaml` + `SecurityConfig`
**Evidence:** management exposure includes `prometheus`, but `SecurityConfig` permits only `/actuator/health/**` and `/actuator/metrics` (`.anyRequest().authenticated()`). `/actuator/prometheus` therefore requires auth.
**Why it is a production issue:** a Prometheus scraper without credentials gets 401; metrics pipeline silently breaks. Minor/ops.
**Status: VERIFIED (static).**

---

## ITEMS CHECKED AND FOUND ACCEPTABLE / NOT APPLICABLE

- **Bean ambiguity / circular deps / lifecycle:** none — context wired cleanly to the DB-connection stage at runtime. Duplicate port implementations (`OcrServiceAdapter`, `VectorIndexAdapter`, ingest `MinioDocumentStorageAdapter`) are deliberately de-`@Component`-ed; exactly one active implementation each. VERIFIED clean.
- **Executor shutdown:** static thread pools in `AssistantController`, `SearchQueryHandler`, and the runtime isolation classes use daemon threads; `TimeoutCancellationOrchestrator` and the ingest `ThreadPoolTaskScheduler` are not explicitly shut down but are daemon/managed — acceptable for JVM-lifecycle-scoped pools (LOW, not itemised).
- **JWT validation:** RS256 verify-with-public-key is correct; deny-list checked pre-auth; `@SuppressWarnings("unchecked") List roles` can NPE only on a malformed self-issued token (LOW).
- **`GlobalExceptionHandler`:** generic handler returns 500 without leaking stack traces — good. No exception-swallowing beyond the best-effort SSE `catch (Exception ignored)` (acceptable).
- **SQL injection:** all JDBC uses bound parameters; BM25 dynamic SQL appends only a bound `?`. Clean.
- **Frontend↔backend contract:** `ApiResponse{status,data,errorCode,errorMessage}` matches the RN client's `response.data.status/​data` usage; auth/refresh/logout/documents/ingest/assistant paths align with controllers. No contract break found (endpoints, envelope, token storage in `expo-secure-store` all consistent).

---

## VERDICT

# PRODUCTION_NOT_READY

**This verdict is unchanged by the remediation work.** F1 — the finding that made the application unable to start at all — is fixed and re-verified (the context now advances through all bean wiring and fails only on the excluded external-infrastructure connection). That removes the single most severe blocker. However, remediation was explicitly scoped by the user across four rounds (startup config, transaction boundaries, VPD leakage, DLQ/retry counters) and **did not address the remaining 12 open findings**, several of which are independently sufficient to keep this system out of production:

**Still open and production-blocking:**
1. **VPD tenant isolation is still incomplete.** F5/F6 (context plumbing and leak prevention) are fixed, but **F4 — the VPD policy function `TENANT_ISOLATION_POLICY` referenced by `DBMS_RLS.ADD_POLICY` still does not exist** — means the one VPD policy that does exist (on `INGESTION_JOB`) will raise `ORA-28110`/`ORA-28112` on every DML against that table once Liquibase actually runs. F7 (VPD covers only one table; `DOKUMEN_LEGAL` and `NOTARIST_USER` have none) is also still open — database-enforced tenant isolation for the sensitive legal-document tables does not exist.
2. **The ingestion pipeline still cannot complete.** F11 — the INDEX stage is never enqueued after EMBED (state-machine asymmetry) — is unchanged. Documents will still stall at `INDEX_PENDING` and never reach `COMPLETED`.
3. **Compliance/audit gap unchanged.** F9 — audit events (logins, document access, ingestion lifecycle) are published to the in-VM event bus and have no consumer/persistence anywhere — remains open. For a notary/PPAT platform this is a compliance requirement, not a nice-to-have.
4. **Multi-instance security gap unchanged.** F14 — the in-memory token deny-list does not survive restarts or replicate across instances — remains open.
5. **Core product function is still stubbed.** F18 — OCR/NER/chunking/embedding/indexing workers are deterministic stubs; no real document content is ever searchable. This alone makes the RAG platform non-functional for its primary purpose, independent of any infra/config fix.

**What changed for the better (evidence-backed, re-verified against current source):**
- The application now **starts** past all Spring wiring (F1) — previously it aborted unconditionally.
- Ingest queue writes now **commit** (F2 — confirmed already fixed) and use a **race-safe atomic claim** (F10 — confirmed already fixed).
- The ingest datasource now points at the **correct database** (F3), which also fixes the dual-pool drift (F13) and half of the MinIO credential mismatch (F16, partial).
- The VPD context-setting mechanism (F5) and its transaction/leak boundary (F6) are now structurally correct, pending the still-open policy function (F4) and single-table coverage (F7) to make VPD actually effective.
- Refresh-token rotation is now atomic (F8), closing the token-reuse race.
- Retry counting is now consistent (F15), with the coordinator and the retry scheduler agreeing on a single counter.

None of the fixes above were regression-tested against live infrastructure (no Docker/Oracle/PostgreSQL available in this environment, and the repository has zero automated tests) — each is verified only by (a) re-reading the current source to confirm the fix is present and correct, and (b) `./gradlew build` + `bootRun` showing no new startup errors. Runtime behavior against real databases remains **NOT VERIFIED** throughout, exactly as in the original audit.

**Verdict rationale:** PRODUCTION_NOT_READY is retained because F4 (VPD policy function still missing — will error on the one table it's declared for), F9 (total audit-trail loss), F11 (pipeline never completes), and F18 (no real content ever indexed) are each independently blocking, and none were in scope for this remediation pass. No assumptions were used to reach this verdict; every supporting finding above is tagged VERIFIED with a source excerpt in the sections below, and items that could not be exercised without live infrastructure are explicitly marked NOT VERIFIED.

---

## APPENDIX — VERIFICATION COMMANDS EXECUTED

**Original audit (2026-07-12):**
```
which docker            → exit 1 (unavailable)
./gradlew compileJava --offline                 → BUILD SUCCESSFUL (12 modules)
./gradlew :notarist-web:bootJar --offline       → BUILD SUCCESSFUL (118 MB jar)
java -jar notarist-web-1.0.0-SNAPSHOT.jar        → context abort (F1, placeholder unresolved)
  (re-run with -Dnotarist.auth.jwt.*=… )         → advanced through all wiring; failed only at PostgreSQL connect
find … -path "*test*" -name "*.java" | wc -l     → 0 (no tests)
```

**Remediation re-verification (2026-07-13, run after all 8 fixes applied):**
```
./gradlew build --offline                        → BUILD SUCCESSFUL, 29 tasks, all up-to-date/executed cleanly
./gradlew :notarist-web:bootRun --offline         → Starting NotaristApplication ... Tomcat initialized with port ...
                                                     0 occurrences of "Could not resolve placeholder"
                                                     0 errors on jwtService / userRepositoryImpl /
                                                       documentLegalRepositoryImpl / ingestionJobRepositoryImpl /
                                                       vpdContextApplier / sessionTokenRepositoryImpl /
                                                       pipelineCoordinator / ingestionQueueRepositoryImpl /
                                                       retryPolicyService
                                                     stops only at: postgresDataSource →
                                                       "Connection to localhost:5432 refused" (external infra, excluded)
grep -r "setAutoCommit(false)" backend/           → no matches (F2 confirmed fixed)
grep -n "SQL_CLAIM_SKIP_LOCKED" IngestionQueueRepositoryImpl.java → present (F10 confirmed fixed)
```
