# 05 — Application Services

| Field | Value |
|---|---|
| Status | DESIGN ONLY |
| Rule | Application services **orchestrate**; they hold **no business rules**. Rules live in aggregates and domain services. |

---

## 0. What an application service may and may not do

| May | May NOT |
|---|---|
| Load an aggregate, call **one** method on it, save it | Decide *whether* a transition is legal (that's the aggregate) |
| Open/close the transaction | Reach into another aggregate's internals |
| Publish events | Contain an `if` on business state |
| Enforce authorization (coarse) | Modify **two** aggregates in one transaction |
| Map DTO ↔ domain | Call another application service (⇒ hidden coupling) |

> **The transaction rule, restated:** one transaction modifies **one aggregate**. Anything crossing an
> aggregate boundary is an **event**, not a second `save()`. Every service below is designed to that
> rule, and where it is tempting to break it, that is called out explicitly.

---

## 1. CaseApplicationService

- **Context:** Case Management
- **Responsibilities:** Case creation; state transitions; party & collateral association; exception
  raising/escalation; deadline registration; delivery; archival.
- **Dependencies:** `CaseRepository`, `CaseStateMachine` (domain service), `EventPublisher`
- **Ports used:** *out* → `DocumentStatusPort` (read-only), `RepertoriumPort`
- **Transaction scope:** **one `Case`.** The workflow entry is appended in the *same* transaction (it
  is inside the aggregate). Audit and downstream reactions are **outside** it, via events.

| Operation | Notes |
|---|---|
| `createCase` | factory → `CaseCreated` |
| `transitionCase` | delegates the *decision* to `Case.transitionTo()`; the service never validates the transition itself |
| `attachDocument` | writes a `DocumentId` **reference**; never loads or mutates the Document |
| `raiseException` / `escalate` | |
| `deliverBundle` | `FINALIZED → DELIVERED` |
| `archiveCase` | `DELIVERED → ARCHIVED` |

⚠️ **The trap:** `transitionCase(FINALIZED)` must allocate a repertorium number. That is a **second
aggregate**. It is therefore *not* done in the same transaction — see §12.

---

## 2. BundleApplicationService

- **Context:** Case Management
- **Responsibilities:** Bundle creation, document attachment/removal, completeness checks, locking.
- **Dependencies:** `CaseRepository` (bundles are **entities of Case** — loaded through it)
- **Ports used:** `DocumentStatusPort` (to report ingestion progress)
- **Transaction scope:** **one `Case`** (its parent). There is no independent bundle transaction.

| Operation | Notes |
|---|---|
| `openBundle` | |
| `attachDocument` / `detachDocument` | rejected once `LOCKED` |
| `getProgress` | reads `DocumentStatus` of referenced docs — **read-only, never writes** |
| `lockBundle` | **irreversible**; only when `COMPLETE` |

---

## 3. DocumentVerificationService

- **Context:** Document Intelligence
- **Responsibilities:** Present extracted fields for human review; record verified/corrected values;
  determine when verification is complete.
- **Dependencies:** `VerificationRepository`, ✅ `OcrConfidencePolicy` (**existing — reuse the
  0.80/0.40 thresholds; do not re-invent**)
- **Ports used:** *out* → `EventPublisher`
- **Transaction scope:** one `Verification`.

| Operation | Notes |
|---|---|
| `openVerification` | on `ExtractionCompleted` |
| `confirmField` / `correctField` | **the corrected value never overwrites the extracted value** — both are retained forever |
| `completeVerification` | → `VerificationCompleted` |
| `rejectDocument` | OCR below `REJECTED` threshold ⇒ re-scan required; **a human may not "confirm" text the machine could not read** |

**The one rule that matters here:** the verifier is always a **human**. This service must never expose
an auto-verify path. It is the liability boundary of the whole platform.

---

## 4. DocumentGenerationService

- **Context:** Document Generation
- **Responsibilities:** Bind verified facts into a template version; render a draft; version drafts;
  regenerate after rejection.
- **Dependencies:** `TemplateRepository`, `DraftRepository`
- **Ports used:** *out* → ✅ `LlmPort` (**the existing `RegistryLlmPort` in `notarist-runtime` — do not
  create a second LLM abstraction**), ✅ `DocumentStoragePort` (persist the rendered deed),
  `VerifiedFactPort`
- **Transaction scope:** one `Draft`.

| Operation | Notes |
|---|---|
| `generateDraft` | facts **injected** from verified extraction; the LLM composes **prose only** |
| `regenerate` | produces a **new version**; the rejected one is retained |
| `publishTemplate` / `versionClause` | **Notary only** |

> **Invariant enforced at the factory, not here:** a Draft cannot be constructed from unverified data.
> The service cannot bypass it even by accident.
>
> **Runtime respect:** the LLM call goes through the existing registry, which already owns timeouts,
> cancellation, queue isolation and degradation. This service adds **no retry loop of its own** —
> stacking one on top of the runtime's is retry amplification.

---

## 5. QCService

- **Context:** Quality Control
- **Responsibilities:** Evaluate a draft against a versioned ruleset; produce an immutable verdict.
- **Dependencies:** `QcRuleSetRepository`, `QcChecklistRepository`
- **Ports used:** `VerifiedFactPort`, `DraftContentPort`
- **Transaction scope:** one `QcChecklist`.
- **External dependencies: NONE.** No AI, no network, no clock.

| Operation | Notes |
|---|---|
| `runQC` | pure evaluation → `QCCompleted(PASSED\|FAILED)` |
| `rerunQC` | creates a **new** checklist; never mutates the previous verdict |
| `publishRuleSet` | Notary only; versioned |

**Determinism is the product here.** Same draft + same facts + same ruleset ⇒ same verdict. If this
service ever calls an LLM, the QC gate stops being a gate.

---

## 6. ApprovalService

- **Context:** Approval
- **Responsibilities:** Raise approval requests against a role; record decisions; enforce authority
  and four-eyes.
- **Dependencies:** `ApprovalRepository`
- **Ports used:** `RoleAuthorityPort` (✅ reads **existing** JWT claims — no auth change)
- **Transaction scope:** one `Approval`.

| Operation | Who |
|---|---|
| `requestApproval` | system, on `QCCompleted(PASSED)` |
| `decide(APPROVED\|REJECTED)` | **only** the holder of `requiredRole` |
| `listPendingForMe` | the hot cross-case query |

**`NOTARY_SIGNATURE` may be decided only by `NOTARIS` — not `ADMIN`, not `PIMPINAN`.** Notarial
authority is statutory and personal; it is not an org-chart permission and cannot be escalated upward.
This is enforced in the aggregate, not merely checked here.

---

## 7. ReminderService

- **Context:** Reminder
- **Responsibilities:** Schedule reminders on human-gate states; cancel on state exit; escalate on
  repeated non-response; track deadlines.
- **Dependencies:** `ReminderRepository`
- **Ports used:** `NotificationPort`, `CaseStatePort` (**re-check the gate is still open before
  firing**)
- **Transaction scope:** one `Reminder`.
- **Reuse:** the dequeue loop mirrors the existing `IngestionQueueScheduler` (`WHERE status='SCHEDULED'
  AND due_at <= now()` + partial index). **Do not invent a new scheduling mechanism.**

The re-check via `CaseStatePort` before firing is what stops the system nagging a notary to sign
something already signed — the single most credibility-destroying bug a reminder system can have.

---

## 8. SearchService ✅ EXISTS — extended

- **Context:** Knowledge Search
- **Current responsibilities (keep):** classify → normalize → parallel BM25 + vector retrieval →
  security filter **per source, before merge** → RRF fusion → diversity → rerank → context assembly.
- **New responsibilities:** honour the `AnswerRouter`; support an optional `caseId` scope filter.
- **Ports used:** ✅ `VectorSearchPort`, `KeywordSearchRepository`, `RerankerPort`,
  `QueryEmbeddingPort`, 🆕 `CaseFactPort`
- **Transaction scope:** **none** (read-only).

> **The defect this fixes:** `SearchIntent` is classified today and then **never used** —
> `RetrievalPipeline` (whose Javadoc promises intent-based strategy selection) has **zero
> implementations**. Every query, including *"is akta 42 signed?"*, currently goes through the LLM.

---

## 9. AssistantService ✅ EXISTS — extended

- **Context:** Knowledge Search
- **Current responsibilities (keep):** RAG orchestration, grounding evaluation, hallucination guard,
  citation injection, conversation memory, SSE streaming.
- **New responsibility:** **refuse to answer factual/status/numeric questions from the LLM.** Route
  them to `CaseFactPort` (deterministic SQL) instead.
- **Ports used:** ✅ `SearchPort`, `LlmPort`, 🆕 `CaseFactPort`
- **Transaction scope:** none.

**The hard rule, structurally enforced:**
1. **Router (positive):** a SQL route never constructs an `LlmRequest` — the LLM port is not on that
   code path at all.
2. **Guard (negative):** a `FactualQueryGuard` at the LLM boundary refuses `NUMERIC_LOOKUP`,
   `STATUS_LOOKUP` and `AGGREGATION` intents and **fails closed**.

Grounding and hallucination guards remain — but they are a *quality net* for semantic answers, **not**
a substitute for never routing a legal-status question to a language model in the first place.

⚠️ **Backward compatibility:** the response envelope (`answerText`, `citations`, `confidence`) must not
change — the shipped mobile app destructures it. The *answer* changes (it becomes correct); the
*shape* must not.

---

## 10. TimelineService

- **Context:** Audit (read-side)
- **Responsibilities:** Project a Case's audit entries into a human-readable chronology.
- **Dependencies:** ✅ `AuditTrailRepository` (**existing**)
- **Ports used:** none
- **Transaction scope:** **none — read-only. It writes nothing, ever.**

Query: `SELECT … FROM audit_trail WHERE subject_type='CASE' AND subject_id=:caseId ORDER BY created_at`.

**No `timeline` table exists or will exist.** `audit_trail` is already polymorphic, so this costs zero
DDL — only new `subject_type` vocabulary.

---

## 11. AuditService ✅ EXISTS — vocabulary only

- **Context:** Audit
- **Responsibilities:** Append immutable entries. **Never update, never delete.**
- **Dependencies:** ✅ `AuditTrailRepository`, `RecordAuditEventHandler`, `AuditEventListener`
- **Transaction scope:** its own (a failed audit write must **not** roll back the business
  transaction — but it **must** alert; a silently-lost audit entry is a compliance hole).
- **Change:** `subject_type` gains `CASE|BUNDLE|APPROVAL|DRAFT|QC`; `event_category` gains `CASE`.
  Both are `VARCHAR` ⇒ **zero DDL**.

---

## 12. The transaction problem: `FINALIZED` + repertorium

The single hardest orchestration in the system, and the one most likely to be got wrong.

Approving a notary signature must:
1. transition the `Case` to `FINALIZED`, **and**
2. allocate a gapless `RepertoriumEntry`.

These are **two aggregates**. Doing both in one transaction violates the aggregate rule; doing them in
two transactions risks a Case finalized with no akta number (or a number burned by a rollback — and a
**gap in the repertorium is a regulatory finding**).

**Resolution — allocate first, then transition:**

```
1. ApprovalGranted(NOTARY_SIGNATURE) received
2. TX-A: Repertorium.allocate(caseId)   [serialized]  → RepertoriumNumberAllocated
         └─ idempotent on caseId: re-allocating for the same case returns the SAME number
3. TX-B: Case.transitionTo(FINALIZED, nomorAkta)      → CaseTransitioned
```

If TX-B fails, the number is already allocated to that case and is **not** reused. Retrying step 3
finds the existing allocation and completes. The number is never burned, never duplicated, and never
gapped. **Order matters: allocate before transition.** The reverse (transition, then allocate) can
strand a `FINALIZED` case with no number, which is unrecoverable without manual intervention.

> This is why `Repertorium` is its own aggregate root with **serialized** allocation and **no
> automatic retry** (see `04-domain-events.md` §4) — a retry loop around statutory number allocation is
> precisely how you manufacture the gap the law forbids.

---

## 13. Service → port matrix

| Service | Writes | Reads (ports) | Publishes |
|---|---|---|---|
| CaseApplicationService | `Case` | `DocumentStatusPort`, `RepertoriumPort` | Case events |
| BundleApplicationService | `Case` (bundles) | `DocumentStatusPort` | Bundle events |
| DocumentVerificationService | `Verification` | — | `VerificationCompleted` |
| DocumentGenerationService | `Draft`, `Template` | `LlmPort` ✅, `VerifiedFactPort`, `DocumentStoragePort` ✅ | Draft events |
| QCService | `QcChecklist` | `VerifiedFactPort`, `DraftContentPort` | QC events |
| ApprovalService | `Approval` | `RoleAuthorityPort` ✅ | Approval events |
| ReminderService | `Reminder` | `CaseStatePort`, `NotificationPort` | Reminder events |
| SearchService ✅ | — | vector/keyword/rerank ✅, `CaseFactPort` | `SearchExecuted` |
| AssistantService ✅ | — | `SearchPort` ✅, `LlmPort` ✅, `CaseFactPort` | `AiResponseGenerated` ✅ |
| TimelineService | — | `AuditTrailRepository` ✅ | — |
| AuditService ✅ | `audit_trail` | — | — |

**No service appears in another service's dependency column.** Services do not call services —
they publish events. That is the only way to keep the contexts from silently fusing back into a
monolith.
