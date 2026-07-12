# 17 — Coding Standard

## Java / backend

- **Records for DTOs and value objects.** No Lombok in this codebase — write explicit
  constructors and accessors when a plain record isn't enough.
- **Constructor injection only.** No field injection (`@Autowired` on a field), no setter
  injection.
- **Domain layer has zero Spring dependency.** Not a guideline — a `domain/` class importing
  `org.springframework.*` is a defect. Ports (`application/port/in`, `application/port/out`)
  are plain interfaces; adapters implementing them live in `infrastructure/` or another module
  entirely (see the `QueryEmbeddingPort`/`QueryEmbeddingRuntimeAdapter` pattern in
  [[06-backend-agent]]).
- **Package layout is fixed per module**: `api → application → domain → infrastructure →
  config`, each with the sub-packages listed in [[06-backend-agent]]. A new class goes in the
  existing sub-package that matches its concern; don't invent a new top-level package inside
  a module without an architecture decision.
- **No shared DTOs across modules.** If two modules need the same shape, that's a signal for
  a domain event or a port, not a shared class in `core` used as a DTO.
- **Explicit error handling only at real boundaries.** A port whose failure is recoverable
  (e.g. an ML sidecar timing out) is caught at the call site and degraded explicitly, with a
  `log.warn` including the correlating trace/query ID — not swallowed silently, not
  rethrown to fail an otherwise-independent code path (see `SemanticRetriever` in
  [[02-architecture]]).
- **Javadoc only for non-obvious *why*.** Good examples already in the codebase:
  `QueryEmbeddingPort`'s Javadoc (explains the inversion pattern and degrade-on-failure
  contract), `QueryEmbeddingRuntimeAdapter`'s Javadoc (explains which concrete worker it
  delegates to and why). Don't add Javadoc that just restates the method signature.
- **VpdContextApplier is intentionally duplicated per module** — this is a deliberate
  boundary decision (see [[05-architect-agent]]), not missed reuse. Don't "fix" it by
  extracting a shared utility that creates a new cross-module dependency.

## SQL

- Explicit columns always, `SELECT *` forbidden, Oracle 19C-compatible syntax only, and
  `MAX(TIME_PR)` for any versioned staging table read (see [[00-project-rules]]).
- Oracle: JPA entities + Spring Data JPA repositories for master/legal data, with VPD context
  applied before every query. PostgreSQL: `JdbcTemplate` for session/queue/chunk data — no
  JPA, no second `EntityManagerFactory`.

## Frontend (React Native)

- One `src/api/*.js` file per backend module surface — mirrors backend module boundaries
  (see [[07-frontend-agent]]).
- Auth/token lifecycle centralized in `AuthContext`, not duplicated per screen.

## General

- Follow the parent system's defaults: no comments explaining *what* code does (names should
  do that), comments only for non-obvious *why*; no speculative abstraction ahead of an
  actual second use case; no error handling for scenarios that structurally can't happen.
