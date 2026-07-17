# 06 — Answer Routing Architecture

| Field | Value |
|---|---|
| Status | **IMPLEMENTED** (backend only) |
| Date | 2026-07-14 |
| Build | `./gradlew clean build` → SUCCESS · 122 tests, 0 failures |
| Scope | `notarist-search`, `notarist-assistant`, `notarist-web` (ArchUnit). No frontend, infra, OCR, or runtime changes. |

---

## 1. The defect this fixes

`IntentClassifier` classified every query into one of five `SearchIntent` values. **That
classification was then never used.** `RetrievalPipeline` — the interface whose Javadoc promised
"implementations … selected based on `SearchQuery.intent`" — had **zero implementations** (verified by
grep, and it remains dead code today).

Consequently every question took the same path:

```
classify → normalize → BM25 + vector → RRF fusion → diversity → rerank → LLM
```

The language model was the **default execution engine for the entire product**. That meant:

| Question | What actually happened |
|---|---|
| "berapa akta bulan ini" | An LLM read some retrieved chunks and **estimated a count** |
| "apakah akta 125 sudah final" | An LLM **inferred a legal status** from document text |
| "SKMHT mana yang jatuh tempo minggu depan" | An LLM **invented deadlines** |

Each of these has an exact answer sitting in a database. In a notary office, a fabricated deed count
is embarrassing; a fabricated *legal status* or *missed statutory deadline* is a liability. An SKMHT
whose deadline is missed voids the security interest it exists to protect — and the user cannot tell
a hallucinated answer from a real one, so they act on it.

---

## 2. The new flow

```
question
   ↓
QueryClassifier          (deterministic regex — never a model)
   ↓
ClassifiedQuery          (category + subtype + extracted parameters)
   ↓
AnswerRouter             (selects an execution STRATEGY, not a provider)
   ↓
FactualQueryGuard        (pre-flight: may this strategy answer this category?)
   ↓
AnswerStrategy           (SQL … or RAG — strategies own their dependencies)
   ↓
FactualQueryGuard        (post-flight: did an LLM answer something it must not?)
   ↓
AnswerResult             (+ audit metadata: strategyUsed, llmInvoked, sqlInvoked, …)
```

### Component map

| Component | Package | Role |
|---|---|---|
| `QueryCategory` | `search.application.routing` | FACTUAL · STATUS · SEMANTIC · DOCUMENT_INTELLIGENCE · UNKNOWN. Carries `llmEligible`. |
| `QuerySubtype` | ″ | 10 subtypes; selects the strategy |
| `QueryClassifier` | ″ | Deterministic classification + parameter extraction |
| `AnswerRouter` | ″ | Chooses the strategy; runs the guard; never executes anything itself |
| `FactualQueryGuard` | ″ | Fail-closed LLM boundary |
| `AnswerStrategy` | ″ | The common interface (9 implementations) |
| `AnswerResult` | ″ | Uniform result + `AnswerMetadata` |
| `LegalFactPort` | `search.application.port.out` | Deterministic SQL facts |
| `RagPort` | ″ | LLM synthesis — **implemented in the assistant module** |
| `LegalFactRepositoryImpl` | `search.infrastructure.persistence.postgres` | JDBC; explicit columns; tenant-scoped |

---

## 3. Why the LLM cannot answer a fact — two independent mechanisms

**1. Positive (structural).** A factual question is routed to a SQL strategy, and the SQL strategies
**do not hold `RagPort`**. There is no code path from `StatisticsStrategy` to a language model. This is
enforced by an ArchUnit rule (`non-LLM strategies must not hold the RAG port`), so it cannot be
quietly undone.

**2. Negative (assertion).** `FactualQueryGuard` runs twice:

- **pre-flight** — refuses to execute an LLM-backed strategy for an LLM-forbidden category;
- **post-flight** — refuses to *return* a result whose metadata says `llmInvoked=true` for such a
  category, which catches a strategy that misreports `usesLlm()`.

The guard **fails the request** rather than degrading to an LLM answer.

Why both? The positive rule is only as strong as the wiring. If someone later makes an LLM strategy
claim `supports(STATISTICS)`, the structural rule silently breaks and nobody notices — the counts just
quietly start being hallucinated. The guard converts that silent corruption into a loud failure.
`AnswerRouterTest.guardRejectsLlmStrategyForFactualQuery()` simulates exactly that regression.

### The rule that must never be added

`FactualQueryGuard.mayFallBackToLlm()` returns `false`, always, and exists as a **named, documented
method** rather than as an absence. It is there because the most tempting future change to this code is
*"the SQL returned nothing, so let's just ask the model"*. That single line would reintroduce the entire
class of fabricated legal answer this sprint removes.

- An empty count **is** the answer: zero.
- A missing table **is** an answer: "not available yet."

Neither is an invitation to guess.

---

## 4. Honesty where the data does not exist yet

Case, Bundle, Approval and Deadline tables **do not exist** (they arrive in the Case sprint). So some
questions in the specification genuinely cannot be answered today:

| Question | Behaviour |
|---|---|
| "apakah bundle ABC sudah dikirim" | `StructuredSearchStrategy` → **unsupported**, states plainly that the Case module does not exist |
| "SKMHT jatuh tempo minggu depan" | `ReminderStrategy` → **unsupported** |
| "siapa yang menyetujui bundle ini" | `StructuredSearchStrategy` → **unsupported** |
| "apakah akta 125 sudah final" | Returns the **ingestion** status it really has, and says explicitly that *legal* finalization is not yet tracked |

That last row is the subtle one. The system knows a document's *pipeline* status (`UPLOADED → …
→ INDEXED`). A notary asking "is it final?" means the *legal* status — signed, approved, delivered.
Reporting `INDEXED` as though it answered "finalized" would be **a lie dressed as a fact**. So the
answer reports what it actually knows and names what it does not.

**Crucially, none of these fall back to the LLM.** "I cannot answer this yet" is a correct answer. A
fluent, confident, invented one is not.

---

## 5. Routing table

| Query | Category | Strategy | LLM? |
|---|---|---|---|
| "berapa akta bulan ini" | FACTUAL | `StatisticsStrategy` | ❌ |
| "berapa dokumen gagal OCR" | FACTUAL | `StatisticsStrategy` | ❌ |
| "berapa akta per jenis" | FACTUAL | `AggregationStrategy` | ❌ |
| "SKMHT jatuh tempo minggu depan" | FACTUAL | `ReminderStrategy` | ❌ |
| "apakah akta 125 sudah final" | STATUS | `StructuredSearchStrategy` | ❌ |
| "status bundle ABC" | STATUS | `StructuredSearchStrategy` | ❌ |
| "akta nomor 125/VII/2024" | STATUS | `StructuredSearchStrategy` | ❌ |
| "tampilkan klausul mirip" | SEMANTIC | `SemanticSearchStrategy` | ❌ (list) |
| "klausul indemnity yang mirip" | SEMANTIC | `HybridSearchStrategy` | ✅ |
| "bandingkan dua dokumen" | DOC_INTEL | `ComparisonStrategy` | ✅ |
| "ringkas dokumen ini" | DOC_INTEL | `DocumentQaStrategy` | ✅ |
| "jelaskan klausul ini" | DOC_INTEL | `DocumentQaStrategy` | ✅ |
| anything else | UNKNOWN | `DocumentQaStrategy` | ✅ grounded |

**Ties are resolved in favour of the engine that cannot make something up.** The classifier tests
factual and status patterns *before* semantic ones, so `"jelaskan status akta 125"` — which contains
both an explanatory verb and a status question — routes to **SQL**, not to the LLM. There is a test for
exactly this (`explanatoryVerbDoesNotDefeatStatus`).

---

## 6. Ambiguity policy

When a query carries both an identifier and an explanatory verb ("ceritakan tentang akta 125"), the
**identifier is resolved via SQL first**; the LLM may then explain the *verified record*. The fact
always comes from the database; the prose comes from the model. **Never the reverse.**

---

## 7. Observability (Task 7)

Every answer carries `AnswerResult.AnswerMetadata`:

| Field | Meaning |
|---|---|
| `strategyUsed` | which engine answered |
| `executionTimeMs` | strategy execution time |
| `llmInvoked` | **the audit-critical flag** — was a model used? |
| `sqlInvoked` | was the database used? |
| `documentsRetrieved` | retrieval breadth |
| `citationsCount` | citation count |

`llmInvoked` is what makes the "no LLM for facts" rule **verifiable after the fact** rather than merely
asserted. It is logged on every request and fed to the post-flight guard. If a factual answer ever
carries `llmInvoked=true`, that is a provable defect — not a matter of opinion.

Logged at INFO on every answer:

```
Answered strategy=StatisticsStrategy llmInvoked=false sqlInvoked=true docs=0 citations=0 ms=12
```
