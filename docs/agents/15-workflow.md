# 15 — Workflow

## End-to-end flow

```
1. ANALYSIS_FIRST     — read the actual schema/code/contract; state it back before proposing
2. Proposal           — planner (04) frames the step; architect (05) shapes module/contract
3. Approval gate       — explicit sign-off for anything architectural or cross-module
4. Generation          — backend (06) / frontend (07) implement against the frozen contract
5. Verification        — QA (08), security review (10) where applicable
6. Debug loop          — if verification fails, debug (09) round-trips to QA, not straight to "done"
7. Report              — reporter (12) records outcome in docs/memory/CLAUDE.md history
```

This mirrors how the project actually got built: STEP 1 (bootstrap) → STEP 2 (DDL) → STEP 3
(ingestion/retrieval architecture) → STEP 4 (API architecture) → STEP 5 (frontend
architecture) → STEP 6 (infra architecture) → STEP 7 (backend architecture) → STEP 7.5
(contracts frozen) → STEP 8A (skeleton) → STEP 8B (phased implementation). See
[[04-planner-agent]] for the full table.

## Approval gates (rule 6, [[00-project-rules]])

A gate applies when a change is: cross-module, touches a frozen contract (STEP 7.5),
changes the data classification model, or changes the architecture decision log. A gate does
**not** apply to: a narrow bug fix with an identified root cause, or an addition entirely
inside one module's already-defined boundary.

## Scaling process to blast radius

Not every task runs the full seven steps. A one-line config fix skips straight to
generation + verification. A new capability spanning multiple modules runs the full flow.
[[03-orchestrator]] makes this call at intake — see that doc's "classify the request" step.

## Phased delivery, not big-bang

Large modules are delivered in phases that are each independently reviewable — `notarist-auth`
+ `notarist-document` as Phase 1, `notarist-ingest` as Phase 2, rather than one 200-file drop.
Each phase still runs the full flow above; phases aren't a way to skip verification, they're
a way to keep each verification pass tractable.

## Reconciling plan vs. actual state

Because STEP documents and memory are point-in-time, always check actual repo state
(module contents, `git log`) before trusting a plan's "next step" — see [[14-project-memory]]
staleness discipline and [[20-roadmap]] for the current reconciled status.
