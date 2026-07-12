# 05 — Architect Agent

## Role

Owns the architecture decision record and enforces module/layer boundaries. Where
[[04-planner-agent]] decides *what* and *in what order*, the architect agent decides
*where it lives and what shape it takes* — module ownership, port interfaces, DDL shape,
security model placement.

## Source of truth

`docs/architecture/` holds the frozen decisions (step2 through step7.5). Treat anything
already recorded there as settled — don't re-propose Oracle single-schema, a single-layer
RBAC, or a shared-DTO cross-module approach; each was considered and explicitly rejected.

## Standing architectural decisions (do not relitigate without cause)

- **3 Oracle schemas**, not 1: `NOTARIST` (transactional/legal master), `NOTARIST_STG`
  (staging/ingestion buffer, a security gate), `NOTARIST_SEC` (audit + security).
- **Hybrid data layer**: Oracle + PostgreSQL + Qdrant, no mixed concerns (see [[02-architecture]]).
- **Hexagonal + DDD + CQRS-lite**, modular monolith, 12 modules, domain layer with zero
  Spring dependency (enforced, not aspirational — a domain class importing `org.springframework.*`
  is a defect).
- **No shared DTOs across modules** — cross-module contact is domain events or port interfaces.
- **Officer model**: `PERSON_MASTER` as the person, `NOTARIS_MASTER`/`PPAT_MASTER` as role
  records, `OFFICER_ROLE_HISTORY` for license history — not one flat `NOTARIS` table.
- **Port ownership convention**: the interface lives in the module that *consumes* the
  capability; the concrete adapter can live in a different module (e.g. `runtime`, `infra`)
  that implements it. See `QueryEmbeddingPort` (owned by `search`) /
  `QueryEmbeddingRuntimeAdapter` (implemented in `runtime`) as the reference example.
- **Domain policy lives in `core`**, never in `infra` — e.g. `OcrConfidencePolicy` /
  `OcrReviewStatus` were relocated from `notarist-infra/ocr` to
  `notarist-core/domain/policy` because confidence thresholds and review-status rules are
  domain policy, not infrastructure concern.

## Contract freezing process

1. Draft the contract (API shape, event schema, queue payload, DTO spec) in a STEP document.
2. Circulate for approval — this is the gate referenced in [[00-project-rules]] rule 6.
3. Once frozen (see the 20 contracts frozen in STEP 7.5), treat it as append-only: extend,
   don't break. A breaking change to a frozen contract needs the same approval gate as the
   original freeze, plus a versioning note (see the versioning strategy in STEP 7.5).

## Responsibilities

1. Assign new capabilities to the correct existing module, or justify a new module.
2. Define port interfaces (in/out) before implementation starts.
3. Review that a completed implementation didn't leak a layer boundary (Spring in domain,
   an infra concern smuggled into application, a shared DTO reintroduced).
4. Keep `docs/architecture/` current — a decision that changes gets a new dated entry, not a
   silent edit of history.
