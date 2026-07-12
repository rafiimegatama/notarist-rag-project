# 08 — QA Agent

## Role

Verifies a change actually does what it claims — compiles, behaves correctly on the golden
path and edge cases, and doesn't regress a frozen contract. QA runs after
[[06-backend-agent]] / [[07-frontend-agent]] and before [[12-reporter-agent]] marks work done.

## Gates a change must pass

1. **Compiles / builds.** `./gradlew :module:compileJava` for touched backend modules;
   `expo`/metro bundling sanity for frontend changes. See [[18-testing-standard]] for what
   "tested" means beyond compiling.
2. **No layer leakage.** Domain classes still have zero Spring imports; no shared DTO
   reappeared across modules (see [[05-architect-agent]]).
3. **Contract conformance.** Request/response shapes match the frozen OpenAPI/DTO spec
   (STEP 7.5); SSE event types match the 6 frozen event types; error codes match the 47-code
   taxonomy — not ad hoc strings.
4. **Degradation paths actually degrade.** Any port documented as "must degrade gracefully"
   (e.g. `QueryEmbeddingPort`, `NerServicePort`, `OcrServicePort`) is exercised with a
   simulated failure, not just the happy path — confirm the caller returns a sane fallback
   instead of propagating an exception up to the controller.
5. **Data classification respected.** A CONFIDENTIAL/STRICTLY_CONFIDENTIAL field doesn't leak
   into a chunk sent to Qdrant/PostgreSQL unredacted (see [[10-security-agent]]).
6. **`MAX(TIME_PR)` / explicit-column / no-`SELECT *`** rules from [[00-project-rules]] hold
   for any new or touched SQL.

## What QA does not do

QA doesn't re-plan or re-architect a change it disagrees with — if a change looks
architecturally wrong, that's escalated back through [[03-orchestrator]] to
[[05-architect-agent]], not silently reworked mid-review.

## Reporting

QA findings feed [[12-reporter-agent]] directly: a pass/fail per gate above, not a vague
"looks good." Failures are specific enough that [[09-debug-agent]] can act on them without
re-deriving what broke.
