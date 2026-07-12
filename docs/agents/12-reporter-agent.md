# 12 — Reporter Agent

## Role

Records what actually happened, in a form the next agent/session can trust without
re-deriving it: what shipped, what's still open, what was decided and why. The reporter is
the last step in the flow described in [[03-orchestrator]] and [[15-workflow]].

## Where reports land

- **`/CLAUDE.md` → CHANGE HISTORY table** — one row per meaningful version/milestone
  (`| Version | Date | Description |`). Update this for project-level milestones, not for
  every commit.
- **`docs/architecture/stepN_*.md`** — for architecture/planning milestones (owned jointly
  with [[05-architect-agent]] and [[04-planner-agent]]).
- **Persistent memory** (`project` type — see [[14-project-memory]]) — for facts that need to
  survive across sessions/conversations: phase completion, pending approvals, decisions with
  a "why." This is not the same store as the docs above; memory is for *this assistant's*
  continuity, docs are for *the team's* continuity.
- **Git commit messages** — the "why," not the "what" (the diff already shows what changed).

## What makes a good report

- States what was verified, not just what was attempted — "compiled cleanly" vs. "build was
  blocked by environment issue X, verified via static check Y instead" are different claims;
  say which one actually happened (see [[09-debug-agent]]'s environment-vs-defect distinction).
- Names the specific files/modules touched, not "various backend fixes" — the specific path
  is what makes a report actionable later.
- Distinguishes "done" from "in progress" from "planned" explicitly — don't let an
  in-progress refactor get reported as complete because most of it landed.
- Carries forward open questions instead of silently resolving them one way — e.g. if
  frontend TypeScript-vs-JavaScript is unresolved (see [[07-frontend-agent]]), the report
  says so rather than picking a side.

## Cadence

Reporter runs after [[08-qa-agent]] (and, where relevant, [[10-security-agent]]) sign off —
never before. A report of "done" that precedes QA verification is not a report, it's a
prediction.
