# 11 — Domain Foundation (Aggregate Roots)

| Field | Value |
|---|---|
| Status | **IMPLEMENTED** — `notarist-case` (new module) |
| Build | `./gradlew clean build` → SUCCESS · **183 tests, 0 failures** (was 122) |
| Scope | Aggregate roots only. No business features, no persistence, no API, no migrations. |
| Untouched | frontend · OCR runtime · AI runtime · Terraform · GCP · Supabase · auth · upload pipeline · answer router |

---

## 1. What was built

A new Gradle module, `notarist-case` (Java package `com.notarist.kase` — `case` is a reserved word),
containing **five aggregate roots** and nothing else.

```
notarist-case/
└── domain/
    ├── model/           Case · Bundle · Approval · Timeline · Repertorium
    │                    + AggregateRoot (base) · TimelineEntry · RepertoriumEntry
    ├── state/           CaseState(17) · BundleStatus · ApprovalDecision · TimelineStatus
    │                    + 4 state machines · TransitionKind
    ├── event/           11 domain events (extending the EXISTING core DomainEvent)
    ├── valueobject/     7 IDs · Actor · Role · CaseNumber · CaseType · BundleType
    │                    · ApprovalType · DocumentRef · TransitionReason
    ├── factory/         Case · Bundle · Approval · Timeline · Repertorium
    ├── specification/   Specification<T> + Case/Bundle/Approval rule sets
    └── exception/       IllegalTransition · InvariantViolation · Authority
└── application/
    ├── port/out/        5 repository ports + DomainEventPublisher  (interfaces ONLY)
    ├── port/in/         HandleIngestionOutcomeUseCase + DocumentIngestionOutcome
    └── listener/        IngestionOutcomeHandler  (the OCR subscription seam)
```

**There is no `infrastructure/` package.** A repository implementation needs tables, and this sprint
ships no migrations. The domain is complete and fully tested without one — which is the point of
having a domain layer at all.

---

## 2. The central claim, and how it is defended

> **An illegal state is unreachable — not "rejected by validation", but unreachable.**

That rests on one property: **there are no public setters, anywhere.** The only door into an
aggregate's state is `transition()`, which consults its state machine and throws.

A design document cannot keep that true. A failing build can:

```java
noMethods().that().areDeclaredInClassesThat().resideInAPackage("..kase.domain.model..")
           .should().haveNameMatching("set[A-Z].*")
```

plus a companion rule that every non-static field in an aggregate is `private` — because a
package-private field is a setter with extra steps.

This is deliberately **not** the pattern the existing `DocumentLegal` uses, where
`transitionStatus()` is an unguarded field assignment carrying `// TODO (STEP 8B): enforce state
machine transitions`, and the rules live in a static helper any caller may simply not call. Rules
*beside* an aggregate are advice. Rules *inside* it are invariants.

Every aggregate exposes exactly the three operations the brief required:

| Operation | Meaning |
|---|---|
| `transition(...)` | the only mutator; validates against the state machine and the actor's authority |
| `validate()` | asserts every invariant; run after **every** mutation |
| `domainEvents()` / `pullDomainEvents()` | events accumulate here and are drained exactly once |

---

## 3. The five aggregates

### Case — 17 states, table-driven

Each edge carries three things the domain actually needs: is it legal, what *kind* of move is it
(FORWARD / RETRY / ROLLBACK / CANCEL), and **which roles may take it**.

What the table makes impossible:

| Attempt | Result |
|---|---|
| Skip QC (`WAITING_QC → WAITING_NOTARY_APPROVAL`) | `IllegalTransitionException` — the notary must never be the first to see an error |
| Jump to `FINALIZED` from `VERIFIED` | rejected — only a notary's approval creates a deed |
| `QC_FAILED → QC_APPROVED` | rejected — a failed QC is regenerated, never "approved" |
| Cancel a `FINALIZED` case | rejected — **a signed deed cannot be un-signed**; it is corrected by a *new* deed |
| ADMIN or PIMPINAN finalizing | `AuthorityException` — notarial authority is statutory and personal, and cannot be escalated upward |
| SYSTEM verifying | `AuthorityException` — verification is the liability boundary; a machine cannot assume it |
| Rollback with no reason | `InvariantViolationException` — a regulator will ask *why* |

`QC_FAILED` is the hinge, and it deliberately offers **two** rollback edges — back to *drafting* (the
draft was wrong) or back to *verification* (the source facts were wrong). The machine refuses to
choose between them, because that distinction requires human judgement and getting it wrong is the
likeliest error in the whole workflow.

`OCR_RUNNING` is **observed, never driven**: the Case learns ingestion finished by receiving an event.

### Bundle — holds IDs, and `LOCKED` is forever

Holds `DocumentRef` (an ID plus the part it plays), **never `DocumentLegal` objects**. The Document
aggregate is reused, not duplicated and not owned — enforced by an ArchUnit rule that forbids any
class in `..kase..` from depending on `DocumentLegal`.

There is **no `unlock()`** — not a guarded one, not an admin one. The notary signed on the basis of
those exact documents; swapping one afterwards would silently invalidate the evidentiary chain.
Attaching is **idempotent**, because at-least-once event delivery makes duplicate attachment normal
rather than exceptional.

### Approval — where legal responsibility attaches

Two rules live here and nowhere else, because a controller check is bypassed the moment a second
caller (an event listener, a batch job, a new endpoint) reaches the same use case:

- **Authority.** `NOTARY_SIGNATURE` may be decided only by `NOTARIS` or `PPAT_OFFICER`. Not ADMIN.
  *Not even PIMPINAN.* An administrator who can sign deeds is a fraud vector, not a convenience.
- **Four eyes.** You may not approve your own work.

A decision is **never reversed**: changing your mind means raising a *new* approval, because the
original decision and its reversal are **both** legally significant facts.

### Timeline — append-only, then sealed

No update, no delete, no correction. An entry that was wrong is followed by another entry saying so;
the original stays. Sequence numbers are dense, and the invariant rejects a gap — which makes removal
*detectable* rather than silent. Sealed when the case closes: the story can no longer grow a chapter.

### Repertorium — the subtlest object in the system

Three statutory properties, none of them preferences:

1. **Gapless.** Numbers run 1, 2, 3 … A missing number is a regulatory finding. **This is why a
   database `SEQUENCE` is not acceptable** — sequences leave gaps on rollback, which is exactly what
   the law forbids. The next number is derived from the entries themselves.
2. **Append-only.** No deletion, no renumbering, no reuse — there is no method that removes an entry.
3. **Allocate-once per case.** `allocate()` is **idempotent on `caseId`**: asking twice returns the
   *same* number rather than burning a second one.

That third property is what makes the "allocate, then transition to FINALIZED" ordering safe. If the
transition fails after allocation and the operation is retried, the case gets its **original** number
back — the first is not stranded as a gap, and no second number is minted for the same deed. The
repository port documents that its implementation **must** take a pessimistic lock (`SELECT … FOR
UPDATE`); this is the one place in the system that genuinely requires serialization.

---

## 4. The OCR subscription — a boundary that holds in both directions

The brief: *"OCR must publish events only. Case subscribes."*

`notarist-case` has **no dependency on `notarist-ingest`, and `notarist-ingest` has none on
`notarist-case`.** Both directions are enforced by ArchUnit (and those rules are no longer vacuous —
the module now exists, so they check real classes).

The Case subscribes through an **inbound port shaped in its own language**:

```java
public record DocumentIngestionOutcome(DocumentId documentId, CaseId caseId,
                                       BundleId bundleId, boolean succeeded) {}
```

The composition root translates the pipeline's event into this. The pipeline simply echoes back the
`caseId` it was handed in its existing job payload — it never resolves one, and never learns what a
Case is.

`IngestionOutcomeHandler` calls `aCase.transition(...)`. It does **not** touch a field, and it does
not count documents on the pipeline's behalf. It is idempotent: a redelivered completion for a case
that has already moved on is ignored, not replayed.

> ⚠️ **Honestly: this is not wired yet.** Completing the loop requires `notarist-ingest` to echo the
> `caseId` back on its completion event — a one-line change to a module this sprint was explicitly
> forbidden to touch (*"DO NOT modify Upload Pipeline"*). The handler is written now, and tested,
> **so the boundary exists before anyone is tempted to have a worker reach into a Case directly.**

---

## 5. Architecture rules — 10 new, 10 existing, all green

New, in `notarist-case`:

| Rule | Protects |
|---|---|
| aggregates expose **no public setters** | ⭐ the central claim: illegal states are unreachable |
| aggregate fields are **private** | a non-private field is a setter with extra steps |
| only the aggregate may `raise()` its own events | an event is a statement about what *it* did |
| application services must not drive the state machines | the aggregate decides legality, exactly once |
| the domain is **framework-free** (no Spring, no JPA) | a JPA annotation makes the schema a domain concern |
| the domain must not depend on the application layer | dependencies point inward |
| `..kase..` must not depend on ingest/document/search/assistant/auth | contexts talk by event, not by import |
| no cycles between domain packages | layering must be real |
| no aggregate references another aggregate **root** | identity only — otherwise two transaction boundaries fuse |
| nothing in `..kase..` depends on `DocumentLegal` | the Document aggregate is reused, never owned |

**Two of these caught real defects during the sprint** — which is the argument for writing them:

1. **A genuine package cycle:** `model → event → model`. The events imported `ApprovalType`, which sat
   in `model`, while `model` imported the events. `ApprovalType` is a pure enum with no identity, so it
   belonged in `valueobject` all along. Moved; cycle gone.
2. **My own rule was too strict:** the first version forbade the application layer from touching
   `..domain.state..` at all — but a service must be able to *name* a target state to pass to
   `transition()`. Narrowed to: a service may not depend on a `*StateMachine`. It may **request** a
   transition; it may never **decide** whether one is legal.

---

## 6. Constraints honoured

| Constraint | Status |
|---|---|
| No migrations | ✅ none written; no `infrastructure/` package exists |
| No DTO / API / controller changes | ✅ none — the module has no `api/` package |
| No frontend changes | ✅ |
| Document aggregate reused, not duplicated | ✅ enforced by ArchUnit |
| Bundle references Document **IDs only** | ✅ `DocumentRef` |
| Upload pipeline / OCR runtime untouched | ✅ zero files changed in `notarist-ingest` |
| Existing search router untouched | ✅ |
| No aggregate cycles | ✅ ArchUnit |
| Illegal transitions impossible | ✅ 61 tests |

Only three files outside the new module were touched, all build wiring:
`settings.gradle.kts`, `notarist-web/build.gradle.kts`, and `notarist-case/build.gradle.kts`.

---

## 7. Technical debt & what is deliberately missing

| # | Item | Why |
|---|---|---|
| 1 | **No persistence.** Repository ports have no implementations. | Needs tables; no migrations this sprint. The domain is fully testable without them. |
| 2 | **Ingestion subscription not wired.** | Needs `notarist-ingest` to echo `caseId` — forbidden this sprint. |
| 3 | `Repertorium` month is taken from the JVM clock (`LocalDate.now()`). | Should come from the DB clock, like the answer router's SQL, so numbering is reproducible across timezones. Real, minor, and worth fixing before persistence lands. |
| 4 | No `Workflow` aggregate. | The original design had one; `Timeline` + `CaseTransitioned` events now cover it. Rather than build both, the redundancy is called out — Workflow would duplicate the Timeline. |
| 5 | `Role` is duplicated (case domain vs `notarist-auth`). | Deliberate: the domain must not depend on the auth module. The values are identical and map 1:1 from existing JWT claims — but they can drift, and nothing yet stops them. |
| 6 | PPAT signing authority unresolved. | `ApprovalType` currently lets a `PPAT_OFFICER` sign any deed. Whether that is lawful for a *non*-PPAT deed is a question for a lawyer, not an engineer. |
| 7 | `DocumentLegal`'s own invariants remain unenforced `TODO`s. | Pre-existing; out of scope. The new aggregates deliberately do not copy that pattern. |
