# 13 — Agent Communication

## Principle

Agents don't communicate by re-explaining context to each other verbally — they communicate
through durable, checkable artifacts. Any handoff between the roles in this doc set should
point at a file, a contract, or a memory entry, not rely on the receiving agent trusting a
one-line summary.

## Handoff artifacts by role pair

| From → To | Artifact |
|---|---|
| [[04-planner-agent]] → [[05-architect-agent]] | STEP document (problem framing, entities, contracts affected) |
| [[05-architect-agent]] → [[06-backend-agent]] / [[07-frontend-agent]] | Frozen contract (port interface, DTO spec, OpenAPI shape) in `docs/architecture/` |
| [[06-backend-agent]] / [[07-frontend-agent]] → [[08-qa-agent]] | The diff itself + which gates in [[08-qa-agent]] were self-checked before handoff |
| [[08-qa-agent]] → [[09-debug-agent]] | Specific failing gate + reproduction, not "tests fail" |
| [[09-debug-agent]] → [[08-qa-agent]] | Root cause + fix location, so QA re-verifies the actual gate that failed |
| any role → [[10-security-agent]] | Explicit flag when a change touches auth, PII fields, or classification — security review is requested, not assumed skippable |
| [[08-qa-agent]] / [[10-security-agent]] → [[12-reporter-agent]] | Pass/fail per gate, sign-off status |
| [[12-reporter-agent]] → everyone (future sessions) | `/CLAUDE.md` change history, `docs/architecture/` entries, memory (see [[14-project-memory]]) |

## Format expectations

- **Cite, don't restate.** A handoff references `file:line` or a specific migration/contract
  name rather than paraphrasing it — paraphrase drifts from the source over time, a citation
  doesn't.
- **State confidence, not just conclusion.** If build verification was blocked by environment
  (see [[09-debug-agent]]) and correctness was instead confirmed by static read-through, say
  that explicitly rather than letting "compiles" and "verified by reading" collapse into the
  same claim.
- **Escalate ambiguity upward, don't guess and pass it on.** If a column name, module
  ownership, or contract shape is unclear, that's resolved (or explicitly surfaced) before
  handoff — an ambiguous handoff just relocates the problem instead of solving it
  (see [[00-project-rules]] rules 1 and 3).

## Cross-references as the wiring format

This doc set links roles to each other with `[[name]]` — treat that as the literal
communication graph: if a doc references a role it hands off to or receives from, that
relationship is real and should hold in practice, not just on paper.
