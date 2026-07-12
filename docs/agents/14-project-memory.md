# 14 — Project Memory

## What this is

Separate from `docs/` (team-facing, git-tracked reference) and from `/generated`
(disposable analysis output), there is a persistent, file-based memory store used across
conversations with the AI assistant working on this repo:
`~/.claude/projects/-home-extrasugar-notarist-rag-project/memory/`, indexed by `MEMORY.md`.

## Memory types in use on this project

- **`project`** — decisions, phase status, motivations that aren't derivable from code alone.
  Examples already in use: `project_direction.md` (the datamart→legal-doc pivot and why),
  `architecture_decisions.md` (the STEP 2/3 decision log), `db_choice_pending.md` (hybrid DB
  confirmation), `step8b_progress.md` (phase completion tracking).
- **`user`** — who's operating the project and how (role, expertise, preferences). Not yet
  heavily populated for this project beyond basic identity.
- **`feedback`** — corrections or confirmations about *how* to work on this repo, distinct
  from *what* the repo contains. Not yet populated — as agents receive direct correction
  ("don't do X", "yes, keep doing Y") on this project, it belongs here, not folded into a
  `project` memory.
- **`reference`** — pointers to external systems (issue trackers, dashboards). Not yet
  populated for this project.

## What does *not* belong in memory

Anything derivable by reading the repo: current module list, file structure, git history,
which migrations exist. Memory is for facts that would otherwise be lost — motivation,
history of a pivot, a phase-completion checkpoint — not a cache of things `grep`/`git log`
already answer authoritatively and more currently.

## Staleness discipline

Every memory file in this project carries a real timestamp and should be treated as a
point-in-time snapshot, not live state. Concretely for this repo: `step8b_progress.md` says
"Phase 1 done, Phase 2 next" as of 2026-05-24 — but the actual repository already has a
fully-built `notarist-ingest` module (workers, coordinator, adapters all present) well beyond
that snapshot. **Before acting on a progress memory, check the actual module contents** — see
[[20-roadmap]] for the reconciled, current state as of this doc set's writing.

## Linking convention

Memory files link to each other with `[[name]]` referencing another memory's `name:` slug.
This doc set (`docs/agents/`) uses the same `[[name]]` convention to link *documents* to each
other — the two systems are separate stores but share the citation style deliberately, so a
reader recognizes a cross-reference regardless of which store it's in.

## How agents should use it

1. Read relevant memory before starting work that depends on prior decisions.
2. Verify anything memory claims about current code/schema state against the actual
   file/DB — memory is context, not ground truth (see [[09-debug-agent]]'s
   environment-vs-defect discipline; the same "verify before asserting" applies here).
3. Update or correct a memory the moment it's found stale, rather than leaving a known-wrong
   entry for the next session to be misled by.
