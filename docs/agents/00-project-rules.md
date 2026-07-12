# 00 — Project Rules

Source of truth: `/CLAUDE.md`. This document expands those rules for agents (human or AI)
operating on the Notarist RAG Platform. If anything here conflicts with `/CLAUDE.md`, the
root file wins.

## Non-negotiable rules

1. **Jangan asumsi nama kolom** — never guess a column name. If a schema isn't visible in
   `database/oracle/liquibase/` or `database/postgres/flyway/`, stop and ask or inspect the
   DDL before writing SQL or a JPA entity against it.
2. **Jangan skip file** — when a task spans multiple files (a module, a migration set, a
   doc set), every file in scope gets touched. Partial coverage is treated as incomplete work.
3. **Jangan hallucinate mapping** — domain ↔ JPA ↔ DTO field mappings must be verified against
   the actual entity/record definitions, never inferred from naming convention alone.
4. **Oracle 19C compatibility** — all SQL against the `NOTARIST` / `NOTARIST_STG` / `NOTARIST_SEC`
   schemas must run on Oracle 19C (no 21c+-only syntax).
5. **Generated output location** — analysis/generation output goes to `/generated`
   (`/generated/docs`, `/generated/sql`, `/generated/backend`, `/generated/json`,
   `/generated/openapi`). Durable, hand-maintained project documentation (this doc set,
   `docs/architecture`, `docs/business`) lives under `/docs` instead — it's reference
   material, not disposable analysis output.
6. **No source modification without approval** — architectural or cross-module changes are
   proposed first (see [[15-workflow]]) and applied only after explicit sign-off. Bug fixes
   with a clear, narrow root cause are the exception.
7. **Explicit columns, always** — every SQL query names its columns.
8. **`SELECT *` is forbidden**, in application code and in ad-hoc analysis SQL alike.
9. **`MAX(TIME_PR)` requirement** — any query touching a versioned/time-sliced staging table
   must select the latest slice via `MAX(TIME_PR)`; this predates the legal-doc pivot (see
   [[01-system-overview]]) and still applies wherever `TIME_PR`-versioned staging tables are used.
10. **ANALYSIS_FIRST mode** — understand and state the current schema/code/contract before
    proposing a change. Don't generate code against an assumed shape.

## Data classification

Every field and document carries one of: `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`,
`STRICTLY_CONFIDENTIAL`. Classification drives: Qdrant payload filtering, Oracle VPD
predicates, and which roles can see a field unmasked (see [[10-security-agent]]).

## Output standard

Every generated file must be: markdown-normalized, RAG-friendly (clean headings, no dense
tables where prose reads better), chunk-friendly (self-contained sections), and
source-traceable (cite the file/migration/decision it came from).

## How agents use this file

Every agent role in this doc set ([[03-orchestrator]] through [[12-reporter-agent]]) operates
under these ten rules without exception. Role-specific docs add constraints on top of this
file; they never relax it.
