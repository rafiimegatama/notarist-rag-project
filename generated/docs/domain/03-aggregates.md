# 03 — Aggregates

| Field | Value |
|---|---|
| Status | DESIGN ONLY |
| Rule | An aggregate is a **transaction + consistency boundary**, not a folder of related data. |
| ⚠️ Superseded | **Bundle is now an aggregate ROOT, not an entity of Case** — see §0.1. Implemented in `notarist-case`. |

---

## 0.1 Correction: Bundle was promoted to an aggregate root

This document originally modelled `Bundle` as an **entity inside the Case aggregate**. The Domain
Foundation sprint promoted it to its own **aggregate root**. That is a real change to the consistency
model, not a naming choice, so it is recorded here rather than quietly diverging from the code.

**What changes:**

| | Original design | As implemented |
|---|---|---|
| Bundle loaded via | `Case` | `BundleRepository` |
| Case → Bundle | composition (owned) | **reference by `BundleId`** |
| Case + Bundle consistency | strong (one transaction) | **eventually consistent** |
| Locking a bundle | locks the Case row | locks only the Bundle |

**Why it is defensible.** A case with twenty documents across four bundles would otherwise force every
document attachment to load, lock and re-save the whole Case. Splitting the root means attaching a KTP
contends only on its own bundle. The cost is that "case state" and "bundle completeness" are no longer
guaranteed consistent in the same instant — which is acceptable, because nothing in the workflow makes
a decision from both at once: the Case asks *"are all bundles locked?"* only at a human gate, where a
few milliseconds of lag is invisible.

**What it costs.** The rule "a bundle cannot be opened on a terminal case" can no longer be enforced
by the Bundle itself (it cannot see the Case's state). It is enforced in `BundleFactory`, which takes
the `Case` object precisely so it can refuse. That is a factory doing real work, not ceremony — and it
is the one invariant that got weaker in the trade.

---

---

## 0. Aggregate inventory

| Aggregate | Root? | Owning context | Transaction boundary |
|---|---|---|---|
| `Case` | ✅ root | Case Management | one Case per transaction |
| `Bundle` | entity of `Case` | Case Management | inside Case's transaction |
| `Workflow` | entity of `Case` | Case Management | inside Case's transaction |
| `DocumentLegal` | ✅ root **(existing — stays root)** | Document Intelligence | one Document |
| `Verification` | ✅ root | Document Intelligence | one Verification |
| `Draft` | ✅ root | Document Generation | one Draft |
| `Template` | ✅ root | Document Generation | one Template |
| `QcChecklist` | ✅ root | Quality Control | one Checklist |
| `Approval` | ✅ root | Approval | one Approval |
| `Reminder` | ✅ root | Reminder | one Reminder |
| `Repertorium` | ✅ root | Case Management | **serialized** — see §9 |
| `Notification` | ✅ root | Notification | one Notification |
| `Person`, `Collateral` | ✅ roots | Administration | one each |
| `Timeline` | ❌ **not an aggregate** | Audit | read-model projection |

**The golden rule applied throughout:** *one transaction modifies exactly one aggregate.* Anything
crossing an aggregate boundary happens by **domain event**, eventually consistent.

---

## 1. Case

- **Root:** `Case`
- **Entities:** `Bundle`, `Workflow`, `Exception`
- **Value objects:** `CaseId`, `CaseNumber`, `CaseType`, `CaseState`, `Deadline`, `TenantId`,
  `PartyRole` (Borrower/Guarantor/Director + `PersonId`)
- **Factory:** `Case.open(caseType, client, tenant, actor)` → emits `CaseCreated`, state
  `CASE_CREATED`. **The constructor is private.** A Case cannot be born in any other state.
- **Repository:** `CaseRepository` — `save`, `findById`, `findByTenantAndState`,
  `findByAssignedNotaris`
- **Consistency boundary:** the Case + its bundles + its workflow log. Loading a Case loads its
  bundles (small, bounded — a case has ~1–5 bundles).
- **Transaction boundary:** one Case. **Never** two cases, and **never** a Case + a Document.

### Invariants
1. `state` may only change via `transitionTo()`, which consults `CaseStateMachine`. **There is no
   public setter.** *(This deliberately does not repeat the existing `DocumentLegal` mistake, where
   `transitionStatus()` is an unguarded assignment with a `// TODO: enforce` and the rules live in a
   bypassable static helper.)*
2. Every transition appends exactly one `Workflow` entry — atomically, same transaction.
3. `ROLLBACK` and `CANCEL` transitions **require a non-blank reason**. Enforced in the aggregate.
4. Terminal states (`ARCHIVED`, `CANCELLED`) accept no outbound transition.
5. A Case cannot reach `FINALIZED` without an `APPROVED` `NOTARY_SIGNATURE` approval. The Case does
   not *check* this — it only transitions on receipt of `ApprovalGranted`. Authority is Approval's
   invariant, not Case's.
6. A Case cannot leave `UPLOADING` while any bundle is `OPEN` with unmet `expectedDocumentCount`.
7. `tenantId` is immutable after creation.

### Lifecycle
`CASE_CREATED → UPLOADING → OCR_RUNNING → FIELD_EXTRACTION → WAITING_VERIFICATION → VERIFIED →
GENERATING_DRAFT → WAITING_QC → QC_APPROVED → WAITING_NOTARY_APPROVAL → FINALIZED → DELIVERED →
ARCHIVED`, with retry/rollback edges (see `07-state-machines.md`).

### Why Documents are NOT inside this aggregate
Three reasons, all grounded in the existing code:
1. **Concurrency.** Five worker types mutate document state independently. If Documents were inside
   Case, every OCR completion would lock the whole Case — serialising a 20-document case's pipeline.
2. **Documents pre-date cases.** Thousands of existing rows have no case. An aggregate that *requires*
   a parent cannot represent data we already have.
3. **Different lifecycles.** Machine vs human. The project rule is explicit: *never merge the two.*

⇒ **Case references Documents by identity, through `Bundle`.**

---

## 2. Bundle *(entity within Case)*

- **Root:** none — accessed only through `Case`
- **Value objects:** `BundleId`, `BundleType`, `BundleStatus`, `DocumentRef` (`DocumentId` +
  `roleInBundle`)
- **Factory:** `Case.openBundle(type, expectedCount)` → `BundleCreated`
- **Repository:** none of its own. *(A `BundleRepository` for read-side queries is acceptable, but
  writes go through `Case`.)*
- **Consistency boundary:** within `Case`.

### Invariants
1. A Bundle holds `DocumentId` **references**, never `DocumentLegal` objects.
2. `LOCKED` is **irreversible**. No unlock operation exists on the aggregate. A correction requires a
   *new* bundle — because a notary signed on the basis of these exact documents, and swapping one
   afterwards would break the evidentiary chain.
3. A Bundle can only be `LOCKED` when `COMPLETE`.
4. `isComplete()` ⇔ `documents.size() >= expectedDocumentCount`.
5. `ingestionProgress()` **reads** referenced documents' `DocumentStatus` via `DocumentStatusPort`.
   **Read-only. A Bundle never writes document state.**

### Lifecycle
`OPEN → COMPLETE → LOCKED` (terminal)

---

## 3. DocumentLegal ✅ EXISTS — stays a root

- **Root:** `DocumentLegal` *(unchanged)*
- **Value objects:** `DocumentId` ✅, `JenisDokumen` ✅, `JenisAkta` ✅, `NomorAkta` ✅,
  `ClassificationLevel` ✅, `DocumentChecksum` ✅
- **Repository:** `DocumentLegalRepository` ✅
- **Transaction boundary:** one Document. Its ingestion pipeline is driven by `IngestionJob`
  (a **separate root**, already).

### Invariants (⚠️ currently UNENFORCED — existing debt)
1. Immutable after `INDEXED`.
2. Status transitions follow `DocumentStatusMachine`.

> **Known defect, not introduced by this design:** `DocumentLegal.transitionStatus()` and
> `markIndexed()` carry `// TODO (STEP 8B): enforce…` and perform **unguarded assignment**. The rules
> live only in the static `DocumentStatusMachine`, which any caller can bypass. **New aggregates must
> enforce invariants internally.** Fixing `DocumentLegal` is logged as debt, out of scope here.

### Change in this program
**Two nullable references only.** A Document *may* point at an originating case. Legacy documents keep
`NULL` forever. `case_id` must **never** become `NOT NULL`.

---

## 4. Verification 🆕

- **Root:** `Verification`
- **Entities:** `ExtractedField`
- **Value objects:** `VerificationId`, `FieldName`, `FieldValue`, `Confidence`, `VerificationStatus`,
  `SourceSpan` (page + bounding box — *where in the scan this came from*)
- **Factory:** `Verification.forDocument(documentId, extractedFields)` — created on
  `ExtractionCompleted`
- **Repository:** `VerificationRepository`
- **Transaction boundary:** one Verification (one document's field set).

### Invariants
1. A field's **verified value is stored separately from the extracted value.** Correcting a field
   **never overwrites** what the machine read — both are retained forever. This is what lets the
   office prove "the OCR said X, a human corrected it to Y, on this date."
2. A human **must** verify every field whose confidence is below the `ACCEPTED` threshold.
   ✅ **Reuses the existing `OcrConfidencePolicy`** (`≥0.80 ACCEPTED`, `[0.40,0.80)
   LOW_CONFIDENCE_REVIEW`, `<0.40 REJECTED`) — thresholds are **not** re-invented.
3. `REJECTED` OCR (< 0.40) **cannot be verified at all** — the document must be re-scanned. A human
   is not permitted to "confirm" text the machine could not read; that would be manufacturing
   evidence.
4. Verification is complete ⇔ every required field is `VERIFIED`.
5. `verifiedBy` must be a human. **A machine can never be the verifier.** This is the liability
   boundary of the entire system.

### Lifecycle
`PENDING → IN_REVIEW → VERIFIED | REJECTED`

---

## 5. Draft 🆕

- **Root:** `Draft`
- **Value objects:** `DraftId`, `DraftVersion`, `TemplateRef` (id + **version**), `FactBinding`,
  `DraftStatus`
- **Factory:** `Draft.generate(templateVersion, verifiedFacts, caseId)` → `DraftGenerated`
- **Repository:** `DraftRepository`
- **Transaction boundary:** one Draft.

### Invariants
1. **Every fact is bound from a verified `ExtractedField`.** A Draft cannot be constructed from
   unverified data — the factory rejects it. **The LLM composes prose; it never supplies a fact.**
2. A Draft is **immutable once generated**. Rejection does not edit it — it produces a **new version**.
   The rejected version is retained (a notary must be able to show what changed and why).
3. A Draft records the **exact template version and clause versions** used. Published templates are
   immutable, so any historical deed remains reproducible.
4. A Draft has **no legal force**. Only a `FINALIZED` Case produces a Minuta.

### Lifecycle
`GENERATING → GENERATED → (QC) → APPROVED | REJECTED → (regenerate ⇒ new version)`

---

## 6. Template 🆕

- **Root:** `Template`
- **Entities:** `TemplateSection`
- **Value objects:** `TemplateId`, `TemplateVersion`, `Slot` (a named fact placeholder),
  `ClauseRef` (id + version), `TemplateStatus`
- **Factory:** `Template.draft(jenisAkta, author)`; `template.publish()` → `TemplatePublished`
- **Repository:** `TemplateRepository`

### Invariants
1. A **`PUBLISHED` template version is immutable, forever.** Editing creates a new version. This is
   not a convenience — a deed executed in 2024 must remain explicable by the template that produced it.
2. Only a **Notary** may author or publish a template. Templating *is* legal drafting.
3. `RETIRED` templates cannot start new drafts, but existing drafts that reference them stay valid.
4. Every `Slot` must map to a known `FieldName` — a template with an unbindable slot cannot publish.

### Lifecycle
`DRAFT_TEMPLATE → PUBLISHED → RETIRED` (versions accumulate; none is ever deleted)

---

## 7. QcChecklist 🆕

- **Root:** `QcChecklist`
- **Value objects:** `QcChecklistId`, `QcItem` (code, severity, passed, message), `QcSeverity`
  (`BLOCKING` | `WARNING`), `QcResult`, `RulesetVersion`
- **Factory:** `QcChecklist.evaluate(draft, verifiedFacts, ruleSetVersion)` — pure function
- **Repository:** `QcChecklistRepository`

### Invariants
1. **Deterministic.** Same draft + same facts + same ruleset ⇒ same verdict. **No LLM, no network, no
   clock.** A probabilistic QC gate manufactures false confidence and is worse than none.
2. `result = FAILED` ⇔ **any** `BLOCKING` item failed. Warnings never block.
3. Immutable once evaluated. Re-running QC creates a **new** checklist; it never mutates the old one.
4. `rulesetVersion` is recorded. A regulator can ask "which rules applied on this date."

### Lifecycle
`PENDING → PASSED | FAILED` (terminal; supersede by re-evaluating)

---

## 8. Approval 🆕

- **Root:** `Approval`
- **Value objects:** `ApprovalId`, `ApprovalType` (`QC_SIGNOFF` | `NOTARY_SIGNATURE`),
  `ApprovalDecision`, `RequiredRole`, `RejectionReason`
- **Factory:** `Approval.request(caseId, type, requiredRole)` → `ApprovalRequested`
- **Repository:** `ApprovalRepository` — `findPendingForRole(role, tenant)` ← **the hot query**
- **Transaction boundary:** one Approval. *(Deliberately its own root: the primary query is
  cross-case — "what awaits my signature?" — and loading every Case to answer that is absurd.)*

### Invariants
1. Assigned to a **role**; resolved by a **person**. The decider's role must satisfy `requiredRole`.
   ✅ Checked against **existing JWT claims** — no auth change.
2. `REJECTED` **requires** a non-blank reason. Enforced in the aggregate *and* as a DB check.
3. A decided Approval is **immutable**. Changing one's mind means raising a **new** approval —
   because the first decision, and its reversal, are both legally significant facts.
4. **Four-eyes:** for `QC_SIGNOFF`, `decidedBy` must differ from the draft's author. *(Pending
   decision D3 — see roadmap.)*
5. `NOTARY_SIGNATURE` may only be decided by `NOTARIS`. Not `ADMIN`. **Not even `PIMPINAN`.** Notarial
   authority is personal and statutory — it is not an org-chart permission and cannot be delegated
   upward.

### Lifecycle
`PENDING → APPROVED | REJECTED` (terminal)

---

## 9. Repertorium 🆕 — the strictly-serialized aggregate

- **Root:** `Repertorium` (one per notary, per year)
- **Entities:** `RepertoriumEntry`
- **Value objects:** `RepertoriumId`, `SequenceNumber`, `NomorAkta` ✅
- **Factory:** `Repertorium.forYear(notarisId, year)`
- **Repository:** `RepertoriumRepository`
- **Transaction boundary:** **one Repertorium — serialized.**

### Invariants
1. Entries are **gapless and sequential**. A missing number is a regulatory finding.
2. **Append-only.** No deletion, no renumbering, no reuse of a number — ever.
3. A number is allocated **exactly once**, at `FINALIZED`.

> **This is the one place in the system requiring pessimistic locking / a serialized sequence.**
> Optimistic concurrency is not acceptable: two cases finalizing simultaneously must not receive the
> same akta number, and a rolled-back transaction must not burn a number (a Postgres `SEQUENCE` would
> leave gaps on rollback — which is exactly what the law forbids). This deserves explicit design
> attention at implementation time; it is the subtlest correctness requirement in the domain.

---

## 10. Reminder 🆕

- **Root:** `Reminder`
- **Value objects:** `ReminderId`, `ReminderType`, `FiresOnState`, `DueAt`, `ReminderStatus`
- **Factory:** `Reminder.scheduleFor(caseId, state, dueAt, targetRole)`
- **Repository:** `ReminderRepository` — `findDue(now)` ← mirrors the existing `ingestion_queue`
  dequeue pattern

### Invariants
1. Fires **only** on human-gate states (`WAITING_VERIFICATION`, `WAITING_QC`,
   `WAITING_NOTARY_APPROVAL`). A case in `OCR_RUNNING` needs a worker, not a nag.
2. **Auto-cancelled** when the Case leaves `firesOnState`. This prevents the classic bug of reminding
   a notary to sign what they already signed.
3. Never fires on a terminal Case.

### Lifecycle
`SCHEDULED → SENT | CANCELLED` (terminal)

---

## 11. Notification 🆕

- **Root:** `Notification`
- **Value objects:** `NotificationId`, `Channel`, `NotificationType`, `ReadState`
- **Repository:** `NotificationRepository`
- **Invariants:** delivery is **at-least-once**; the consumer deduplicates on `idempotencyKey`.
  Read-state is per recipient.
- **Lifecycle:** `PENDING → SENT → READ` | `FAILED`
- ⚠️ Its API shape is already dictated by the **shipped mobile UI** (list + unread count + mark-read).
  Match that contract; do not design a different one.

---

## 12. Timeline — ❌ NOT an aggregate

A **read-model projection** over `audit_trail` (`subject_type='CASE' AND subject_id=:caseId ORDER BY
created_at`). It has no root, no invariants, no repository — only a query port.

Creating a `timeline` table would duplicate every audit row, violating the project's *never duplicate
data* rule. `audit_trail` is already polymorphic (`subject_type`/`subject_id`/`detail_json`), so this
costs **zero DDL** — only new vocabulary values.

---

## 13. Consistency map — what is eventually consistent

| Crossing | Mechanism | Consistency |
|---|---|---|
| Document pipeline → Case state | `DocumentIngestionCompleted` event | **Eventual** (seconds) |
| Verification → Draft | `VerificationCompleted` event | **Eventual** |
| Draft → QC | `DraftGenerated` event | **Eventual** |
| QC → Approval | `QCCompleted` event | **Eventual** |
| Approval → Case `FINALIZED` | `ApprovalGranted` event | **Eventual** |
| Case transition → Workflow entry | same transaction | **Strong** |
| Case transition → audit entry | listener | **Eventual** |
| `FINALIZED` → Repertorium number | **serialized allocation** | **Strong** ⚠️ |

**Everything is eventually consistent except two things:** a Case's own state + its workflow log
(same transaction), and repertorium numbering (serialized). Those two are strongly consistent because
the law requires it. Everywhere else, a few seconds of lag is not merely acceptable — it is what keeps
the ingestion pipeline from having to take a lock on a business case.
