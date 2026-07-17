# 01 — Ubiquitous Language

| Field | Value |
|---|---|
| Status | DESIGN ONLY — no code, no migrations |
| Scope | Notary Office Operating System (Case-centric) |
| Date | 2026-07-14 |
| Language rule | Indonesian legal terms are **kept in Indonesian**. Translating *Minuta* to "original copy" loses legal meaning and is forbidden. |

---

## 0. How to read this

Every term carries: **Definition** (what it is), **Business meaning** (why the office cares),
**Owner** (the bounded context that is the source of truth), **Who may modify**, **Lifecycle**.

"Owner" is a *context*, not a person. Exactly one context owns each term. Any other context that
needs it holds a **reference by identity**, never a copy. This is the rule that prevents the same
concept existing twice with two truths.

> **Terms already implemented in code are marked ✅ REUSE — they must not be redefined.**
> Terms marked 🆕 NEW do not exist yet. Terms marked ⚠️ CONFLICT clash with something that exists.

---

## 1. Core workflow terms

### Case ⚠️ CONFLICT (name only)
- **Definition:** One unit of notarial work, from client instruction to delivered deed. The root
  business object of the entire system.
- **Business meaning:** What the office bills for, staffs, and is accountable for. A notary does not
  think "I processed a document"; they think "I closed the Budi land sale."
- **Owner:** Case Management
- **May modify:** Staff (create, attach documents), Notary (approve/finalize), Admin (cancel/override)
- **Lifecycle:** `CASE_CREATED → … → ARCHIVED` (see `07-state-machines.md`)
- ⚠️ `CASE` is a reserved SQL keyword; the table is `notaris_case`, the Java package `kase`.

### Bundle 🆕
- **Definition:** A named group of documents within a Case that serves one purpose (identity papers,
  land certificates, the generated output).
- **Business meaning:** How a notary's desk actually works — papers arrive in folders, not one at a
  time. A bundle knows how many documents it *expects*, so "3 of 5 received" is answerable.
- **Owner:** Case Management
- **May modify:** Staff (add/remove documents while `OPEN`); nobody once `LOCKED`
- **Lifecycle:** `OPEN → COMPLETE → LOCKED` (locking is irreversible — see Bundle Locked)

### Legal Document (`DocumentLegal`) ✅ REUSE
- **Definition:** One uploaded or generated file with legal significance, with its own machine
  lifecycle (OCR → NER → chunk → embed → index).
- **Business meaning:** The physical artefact. **Exists independently of any Case** — a notary browses
  "all my aktas" without thinking about cases, and thousands of documents already exist with no case.
- **Owner:** Document Intelligence
- **May modify:** the ingestion pipeline (machine); no human edits content after `INDEXED`
- **Lifecycle:** `UPLOADED → … → INDEXED` (existing `DocumentStatus`, **unchanged**)
- **Rule:** *Documents remain independent aggregates. Case orchestrates; it never absorbs them.*

### Workflow ✅ (concept) / 🆕 (for Case)
- **Definition:** The ordered record of *why* a Case moved from one state to the next.
- **Business meaning:** Answers "who sent this back to drafting, and on what grounds?" — a question a
  notary must be able to answer to a regulator.
- **Owner:** Case Management
- **May modify:** nobody directly — append-only, written as a side effect of a valid transition
- **Lifecycle:** immutable once appended

### Timeline 🆕 (projection, not a store)
- **Definition:** The human-readable chronological view of everything that happened to a Case.
- **Business meaning:** What staff open first thing in the morning.
- **Owner:** Audit (as a **read-model projection over `audit_trail`**)
- **May modify:** nobody — derived
- **Lifecycle:** none (query-time projection)
- **Rule:** Timeline is **not a table**. Building one would duplicate every audit row.

---

## 2. Verification & extraction

### OCR Confidence ✅ REUSE
- **Definition:** A float `[0.0, 1.0]` scoring how reliably text was read from a scan.
- **Business meaning:** The office's tolerance for machine error. A misread NIK digit on a land deed
  is a legal defect, not a typo.
- **Owner:** Document Intelligence (`OcrConfidencePolicy`)
- **May modify:** nobody — produced by the OCR provider
- **Thresholds (already in code — do not re-invent):** `≥ 0.80 ACCEPTED` · `[0.40, 0.80)
  LOW_CONFIDENCE_REVIEW` · `< 0.40 REJECTED`

### Extraction 🆕 (partially exists as NER)
- **Definition:** Pulling structured fields (NIK, NPWP, nomor akta, names, dates, amounts) out of
  OCR'd text.
- **Business meaning:** Turns a picture of a KTP into data a draft can be built from.
- **Owner:** Document Intelligence
- **May modify:** the NER pipeline (machine); a human corrects via **Verification**, never in place
- **Lifecycle:** produced once per document; superseded by a human-verified value

### Verification 🆕 (seam exists)
- **Definition:** A human confirming that extracted field values match the source document.
- **Business meaning:** **The liability boundary.** After verification, the office asserts the data is
  true. Everything downstream (draft, QC, signature) trusts it.
- **Owner:** Document Intelligence
- **May modify:** Staff, Notary. **Never a machine.**
- **Lifecycle:** `PENDING → VERIFIED | REJECTED`
- **Reuse:** `OcrReviewStatus.LOW_CONFIDENCE_REVIEW` **is already** the "needs a human" signal. The
  Verification context consumes it rather than inventing a parallel flag.

### Review 🆕
- **Definition:** The act of a person inspecting machine output before it is trusted. The umbrella
  term; Verification (fields) and QC (drafts) are its two concrete forms.
- **Owner:** — (language only; not an aggregate)

### Exception 🆕
- **Definition:** Any condition that stops a Case advancing automatically and requires a human:
  OCR rejected, a field unextractable, a QC blocking failure, a missing document.
- **Business meaning:** The office's work queue. **An exception is not an error** — it is normal
  notarial work (a client brought a blurry photocopy).
- **Owner:** Case Management
- **May modify:** Staff (resolve), Notary/Admin (override)
- **Lifecycle:** `RAISED → RESOLVED | ESCALATED`

### Escalation 🆕
- **Definition:** Handing an unresolved Exception to higher authority (Junior → Senior → Notary).
- **Business meaning:** Junior staff must not silently guess on a legal ambiguity.
- **Owner:** Case Management
- **May modify:** anyone may raise; only the escalation target may resolve
- **Lifecycle:** `RAISED → ACKNOWLEDGED → RESOLVED`

---

## 3. Drafting

### Template 🆕 (no code exists)
- **Definition:** A versioned, parameterised skeleton of a deed, with slots for verified facts and
  selectable Legal Clauses.
- **Business meaning:** The office's accumulated legal craft. Templates are the product.
- **Owner:** Document Generation
- **May modify:** **Notary only** (a template is legal drafting). Admin may publish/retire.
- **Lifecycle:** `DRAFT_TEMPLATE → PUBLISHED → RETIRED` (published versions are **immutable** — a deed
  drafted last year must be reproducible from the template that made it)

### Legal Clause 🆕
- **Definition:** A reusable, individually versioned paragraph of legal text (e.g. a default clause).
- **Business meaning:** Clauses change when the law changes. When one does, the office must know every
  deed that used the old one.
- **Owner:** Document Generation
- **May modify:** Notary
- **Lifecycle:** versioned, immutable once used

### Draft 🆕
- **Definition:** A generated, not-yet-approved deed produced from a Template + verified facts.
- **Business meaning:** The output under review. It has **no legal force**.
- **Owner:** Document Generation
- **May modify:** regenerate (new version); **never edited into legal force** — it must pass QC and
  Approval
- **Lifecycle:** `GENERATING → GENERATED → REJECTED → (regenerate) → APPROVED`
- **Hard rule:** every *fact* in a draft (NIK, names, nomor, dates, amounts) is **injected from
  verified extraction**, never generated by an LLM. The LLM may compose prose only.

### Minuta ✅ (domain term, not yet modelled)
- **Definition:** The **original, signed deed retained by the notary**, held in the office's
  protocol. In Indonesian law the minuta never leaves the notary's custody.
- **Business meaning:** The legal original. Losing it is a career-ending event.
- **Owner:** Case Management (as the finalized artefact of a Case)
- **May modify:** **nobody, ever.** Immutable and permanently retained after signing.
- **Lifecycle:** created at `FINALIZED` → retained indefinitely (never deleted, never archived away)

### Salinan
- **Definition:** The **certified copy** of the minuta issued to the parties.
- **Business meaning:** What the client actually receives. Multiple salinan may be issued over time.
- **Owner:** Case Management / Delivery
- **May modify:** Notary issues; nobody edits
- **Lifecycle:** `ISSUED → DELIVERED` (a Case may issue many, over years)
- **Rule:** Minuta and Salinan are **distinct documents**, not two states of one. Conflating them is a
  legal error.

### Repertorium
- **Definition:** The notary's official chronological register of every deed executed, with its
  number and date.
- **Business meaning:** **Statutory.** It is inspected by the authorities. The numbering is sequential
  and gapless.
- **Owner:** Case Management
- **May modify:** append-only, at `FINALIZED`. **No deletions, no renumbering, ever** — a gap is a
  regulatory finding.
- **Lifecycle:** immutable append-only ledger, per notary, per year
- ⚠️ This makes `nomor_akta` allocation a **strictly serialized** operation — see `03-aggregates.md`.

---

## 4. Parties

### Person 🆕 (`PersonId` VO ✅ exists)
- **Definition:** A natural or legal person involved in a Case.
- **Business meaning:** The same human recurs across cases for decades. Identity must be stable.
- **Owner:** Administration (PERSON_MASTER)
- **May modify:** Staff (create/correct), Notary (verify identity)
- **Lifecycle:** long-lived, independent of any Case
- ✅ `PersonId` already exists in `notarist-core`. Reuse.

The following are **Roles a Person plays in a Case** — they are *not* separate entities. Modelling
"Borrower" as its own table would duplicate Person and immediately drift.

| Term | Definition | Business meaning |
|---|---|---|
| **Borrower** | Person receiving credit, granting security over an asset | The party whose asset is at risk |
| **Guarantor** | Person who assumes the obligation if the Borrower defaults | Must appear and sign in person |
| **Director** | Natural person with authority to bind a legal entity | Their **Authority** must be proven, not assumed |
| **Bank Partner** | The lending institution ordering the work | The office's repeat client; usually the one paying |

### Authority (*Kewenangan*) 🆕
- **Definition:** Proof that a person may legally bind another party (board resolution, articles,
  power of attorney).
- **Business meaning:** **The single most common source of a void deed.** If a Director signs without
  authority, the deed can be annulled.
- **Owner:** Case Management (verified fact on the Case)
- **May modify:** Staff records, **Notary verifies** — the notary's personal legal responsibility
- **Lifecycle:** `CLAIMED → VERIFIED | REJECTED`, with an expiry date

### Power of Attorney (*Kuasa*) ✅ (`JenisAkta.KUASA` exists)
- **Definition:** An instrument by which one person authorises another to act for them.
- **Business meaning:** Both an input (a party appears by proxy) and an output (the office drafts one).
- **Owner:** Document Intelligence (as input) / Document Generation (as output)
- **Lifecycle:** has a validity period; **may be revoked** — a deed signed under a revoked kuasa is void
- ✅ Already a `JenisAkta` value. Reuse.

### Collateral (*Agunan/Jaminan*) 🆕
- **Definition:** The asset securing an obligation — land (with a certificate), a building, a vehicle,
  a receivable.
- **Business meaning:** The subject matter of APHT/SKMHT/Fidusia work. Its identifiers (certificate
  number, location, value) are the facts a deed is built from.
- **Owner:** Case Management
- **May modify:** Staff (record), Notary (verify against the certificate)
- **Lifecycle:** referenced across many cases (the same land is mortgaged, released, re-mortgaged)

---

## 5. Control

### Quality Control (QC) 🆕
- **Definition:** Deterministic, rule-based validation of a Draft against verified facts and legal
  requirements, before it reaches the notary.
- **Business meaning:** Protects the notary's signature. The notary should never be the first person
  to notice that the NIK is wrong.
- **Owner:** Quality Control
- **May modify:** the ruleset — **Notary only**; results are machine-produced and immutable
- **Lifecycle:** `PENDING → PASSED | FAILED`
- **Rule:** QC is **rules, not an LLM**. "Does the NIK in the draft equal the verified NIK?" is a
  string comparison, and must never be a probabilistic judgement.

### QC Checklist / QC Item 🆕
- **Definition:** The versioned set of rules applied, and each rule's outcome.
- **Business meaning:** A `BLOCKING` failure stops the deed. A `WARNING` annotates it.
- **Owner:** Quality Control
- **Lifecycle:** immutable once evaluated; carries `ruleset_version` (a deed QC'd last year was judged
  by last year's rules and must remain explicable)

### Approval 🆕
- **Definition:** A decision by an authorised human to let a Case advance past a gate.
- **Business meaning:** **Where legal responsibility attaches.** The notary's approval *is* the
  professional act.
- **Owner:** Approval
- **May modify:** only the holder of the required role; the decision is then immutable
- **Lifecycle:** `PENDING → APPROVED | REJECTED`
- **Rule:** an Approval is assigned to a **role**, resolved by a **person**.

### Reminder 🆕
- **Definition:** A scheduled nudge that a Case is waiting on a human.
- **Business meaning:** Cases die in waiting states. Deadlines in notarial work are statutory (an
  SKMHT expires; a mortgage registration has a filing window).
- **Owner:** Reminder
- **May modify:** system schedules/cancels; Staff may snooze
- **Lifecycle:** `SCHEDULED → SENT | CANCELLED` — auto-cancelled when the Case leaves the state it
  fires on (never remind a notary to sign what is already signed)

### Deadline 🆕
- **Definition:** A date by which an action must legally occur.
- **Business meaning:** Distinct from a Reminder. **A deadline is a fact of law; a reminder is a
  message about it.** Missing an SKMHT deadline voids the security interest.
- **Owner:** Case Management
- **May modify:** Notary (only where law permits extension)
- **Lifecycle:** `ACTIVE → MET | MISSED` (a missed deadline is a permanent, auditable fact)

### Audit ✅ REUSE (`audit_trail`)
- **Definition:** The immutable, append-only record of who did what, when, to which subject.
- **Business meaning:** Regulatory defence. Answers "prove nobody tampered with this."
- **Owner:** Audit
- **May modify:** **nobody.** Append-only by construction.
- **Lifecycle:** written once, retained per policy
- ✅ Already polymorphic (`subject_type` / `subject_id` / `detail_json`). Case/Bundle/Approval subjects
  need **no schema change** — only new vocabulary.

### Bundle Delivery 🆕
- **Definition:** Handing the finished output (salinan, registration receipts) to the client or bank.
- **Business meaning:** The billable completion event. The bank does not pay until delivery.
- **Owner:** Case Management
- **May modify:** Staff (dispatch), recipient (acknowledge)
- **Lifecycle:** `PENDING → DISPATCHED → ACKNOWLEDGED`

### Bundle Locked 🆕
- **Definition:** A Bundle sealed against further change once its contents are relied upon.
- **Business meaning:** Once the notary signs based on a set of documents, that set is frozen —
  swapping a KTP afterwards would invalidate the audit chain.
- **Owner:** Case Management
- **Lifecycle:** **irreversible.** No unlock. A correction requires a *new* bundle version, recorded
  as such.

---

## 6. Terms deliberately NOT introduced

| Rejected term | Why | Use instead |
|---|---|---|
| `DocumentOwner`, `CaseDocument` | Would duplicate `DocumentLegal` | `bundle_document` link |
| `TimelineEntry` (stored) | Duplicates every audit row | `audit_trail` projection |
| `CaseStatus` | Collides with `DocumentStatus`/`PipelineStatus` (3 status enums already exist) | `CaseState` |
| `User` | `notarist_user` + `Role` already exist | ✅ reuse |
| `SearchIndex` | `chunk_index` exists | ✅ reuse |

---

## 7. The naming rule that matters most

**Case state and Document state are never the same word with the same meaning.**

`CASE.state = OCR_RUNNING` means *"at least one document in this case's bundle has not finished the
pipeline."* It is a **derived summary**. It does not drive OCR; it observes it. The Case never writes
`DocumentStatus`, and the pipeline never writes `CaseState`.

Three status vocabularies already exist in the codebase (`DocumentStatus` 14 values, `PipelineStatus`
12, legacy `PipelineStage`). `CaseState` is the fourth and **last**. It lives at a different altitude
and must never be merged with them.
