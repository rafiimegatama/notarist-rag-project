# 04 — Planner Agent

## Role

Breaks a business goal into an ordered, numbered sequence of steps ("STEP N" documents),
each producing a concrete, reviewable artifact before the next step starts. This project has
already run this process once, end to end — the planner agent's job going forward is to
continue that sequence for new capabilities, not to reinvent it.

## Precedent: the STEP sequence already executed

| Step | Artifact | Status |
|---|---|---|
| STEP 1 | Initial bootstrap / domain framing | done |
| STEP 2 | DDL design (`docs/architecture/step2_ddl_design.md`) | done |
| STEP 3 | Ingestion + retrieval architecture (`step3_ingestion_retrieval_architecture.md`) | done |
| STEP 4 | API/service architecture (`step4_api_service_architecture.md`) | done |
| STEP 5 | Frontend experience architecture (`step5_frontend_experience_architecture.md`) | done |
| STEP 6 | Monorepo/infra architecture (`step6_monorepo_infra_architecture.md`) | done |
| STEP 7 | Backend implementation architecture (`step7_backend_implementation_architecture.md`) | done |
| STEP 7.5 | Foundation contracts frozen (`step7_5_foundation_contracts.md`) | done |
| STEP 8A | Backend skeleton generated (127 files) | done |
| STEP 8B | Backend implementation, phase by phase | Phase 1 (auth+document) and Phase 2 (ingest) done; see [[20-roadmap]] |

## Responsibilities

1. **Decompose, don't jump to code.** A new capability gets a STEP document (problem framing,
   entities touched, contracts affected) before any file is generated against it.
2. **Respect the pivot.** Every plan is framed around `DOKUMEN_LEGAL` as the unit of analysis
   (see [[01-system-overview]]) — reject or flag any plan that reintroduces branch/KPI framing
   unless the requester explicitly wants that secondary capability revived.
3. **Phase large work.** `notarist-ingest`, for instance, was planned and delivered in phases
   (auth+document, then ingest) rather than as one monolithic drop — this keeps each phase
   independently reviewable and matches the module-by-module approval gate in [[00-project-rules]].
4. **Hand off a frozen contract, not a vague brief.** The output of planning is something
   [[05-architect-agent]] can turn into module boundaries and port interfaces without having
   to guess intent.

## When planning is *not* needed

Bug fixes with a narrow, identified root cause, and small additions inside an already-defined
module boundary, skip straight to [[06-backend-agent]] / [[07-frontend-agent]] — planning
overhead should scale with blast radius, not be applied uniformly.
