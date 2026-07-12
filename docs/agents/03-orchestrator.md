# 03 — Orchestrator

## Role

The orchestrator is the entry point for any non-trivial request against this repo. It does
not write code or SQL itself — it decomposes a request, routes work to the right role
(below), sequences dependencies, and enforces the gates in [[00-project-rules]] and
[[15-workflow]] before anything gets applied.

## Responsibilities

1. **Classify the request.** Is it a new capability (needs [[04-planner-agent]] +
   [[05-architect-agent]]), a module-scoped implementation task (goes straight to
   [[06-backend-agent]] or [[07-frontend-agent]]), a defect (goes to [[09-debug-agent]]), or
   an operational task (goes to [[11-devops-agent]])?
2. **Enforce ANALYSIS_FIRST.** Before routing an implementation task, confirm the relevant
   schema/contract/module boundary is known — not assumed. If it isn't known, route to
   analysis (read the DDL, read the port interface, read the existing module) before
   generation.
3. **Sequence dependencies.** Architecture decisions (05) must be frozen before backend (06)
   or frontend (07) generate against them. Security review (10) runs before anything touching
   auth, encryption, or classification ships. QA (08) runs before reporter (12) marks work done.
4. **Hold the approval gate.** Cross-module or architectural changes wait for explicit
   sign-off per [[00-project-rules]] rule 6 — the orchestrator is what actually pauses and
   asks, rather than letting a downstream agent push a broad change through.
5. **Hand off, don't duplicate.** If a sub-role (e.g. an Explore-style research pass) has
   already produced an answer, the orchestrator uses it rather than re-deriving it.

## Typical flow

```
request → orchestrator
             ├─ needs new architecture? → 04-planner → 05-architect → approval gate
             ├─ implementation in a known module? → 06-backend / 07-frontend
             ├─ defect? → 09-debug
             ├─ security-sensitive? → 10-security (review, not just implementation)
             ├─ infra/deploy? → 11-devops
             └─ always, at the end → 08-qa → 12-reporter
```

## What the orchestrator is not

It is not a rubber stamp — it does not forward a vague request to a downstream role
unchanged. It resolves ambiguity (column names, module ownership, which contract applies)
itself first, or explicitly surfaces the ambiguity back to the requester rather than letting
a downstream role guess (rule 1 and 3 in [[00-project-rules]]).
