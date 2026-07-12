# 06 — Backend Agent

## Stack

Spring Boot 3, Java 17, Gradle multi-module (Kotlin DSL). Oracle JDBC pulled from a private
Nexus/Artifactory (not Maven Central — `ojdbc11.jar` is uploaded internally). Liquibase for
Oracle migrations (`database/oracle/liquibase/`), Flyway for PostgreSQL
(`database/postgres/flyway/`, currently V1 through V5).

## Module layout (every business module)

```
api/request, api/response, api/rest        — DTOs (records) + controllers
application/command, application/query     — command/query records
application/handler/command, .../query     — one handler per command/query
application/service, application/coordinator, application/worker  — orchestration, pipeline stages
application/port/in, application/port/out  — interfaces only
domain/model, domain/event, domain/exception, domain/service — zero Spring dependency
infrastructure/persistence/oracle, .../postgres — JPA entities (Oracle) / JdbcTemplate (Postgres)
infrastructure/adapter, .../security, .../metrics, .../event
config/                                     — module Spring config, usually thin
```

Reference implementations: `notarist-auth` (JWT, refresh rotation, session handling) and
`notarist-ingest` (full 5-stage async pipeline with coordinator + per-stage workers) are the
most complete examples to imitate for a new module.

## Conventions to follow

- **Records for DTOs and value objects.** No Lombok observed in this codebase — explicit
  constructors, explicit getters where needed (see `QueryEmbeddingPort`, `IngestionJob`).
- **Constructor injection only**, no field injection.
- **VpdContextApplier is duplicated per module**, not shared. This is deliberate — it avoids
  a cross-module dependency (e.g. `document` depending on `auth`) purely to reuse five lines
  of `DBMS_SESSION.SET_CONTEXT` plumbing. Copy it, don't import it.
- **Oracle vs PostgreSQL access pattern differs by data kind**: legal/master data via Spring
  Data JPA + a `RepositoryImpl` that applies VPD context before every query; session/queue/chunk
  data via `JdbcTemplate`, no JPA, no dual `EntityManagerFactory`.
- **Cross-module events** go through `AuditEventPayload` (in `core`) + Spring
  `ApplicationEventPublisher` — never a direct method call into another module's internals.
- **Ports degrade, they don't propagate blindly.** A port whose failure shouldn't sink an
  entire use case (e.g. `QueryEmbeddingPort` failing shouldn't fail search) is caught at the
  call site and degraded explicitly — see `SemanticRetriever.embedQuery()` for the pattern:
  catch, log a `warn` with the correlating ID, return a sentinel the caller already handles.
- **Javadoc only where the *why* isn't obvious** — see `QueryEmbeddingPort`'s Javadoc
  explaining the inversion pattern and degrade-on-failure contract. Don't restate the method
  signature in prose.

## Before starting implementation work

1. Confirm the module boundary and port ownership with [[05-architect-agent]]'s standing
   decisions — don't assume a new class belongs in `infra` just because it talks to an
   external service; if it encodes a business rule, it belongs in `core` or the module's
   own `domain/`.
2. Confirm column names against the actual migration file — never against a memory of the
   schema (rule 1, [[00-project-rules]]).
3. If touching a frozen contract (STEP 7.5), route through [[05-architect-agent]] first.

## Known environment quirk

Local Gradle builds in this sandbox can fail with a `build/tmp/.../stash-dir` permission
error if a previous session left `build/` directories owned by a different sandbox user.
Fix: delete the stale `*/build` directories (they're disposable, gitignored build output) —
if that's blocked by ownership too, this is an environment issue, not a code defect; verify
correctness by reading the actual wiring instead of insisting on a local compile.
