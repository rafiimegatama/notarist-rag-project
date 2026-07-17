# 07 — Execution Strategies

| Field | Value |
|---|---|
| Status | IMPLEMENTED |
| Interface | `com.notarist.search.application.routing.AnswerStrategy` |
| Count | 9 strategies — 5 deterministic, 3 LLM-backed, 1 retrieval-only |

---

## 1. The contract

```java
public interface AnswerStrategy {
    String name();                                   // audit metadata
    boolean supports(ClassifiedQuery query);         // selection
    boolean usesLlm();                               // declared, then verified by the guard
    AnswerResult execute(ClassifiedQuery q, AnswerRequest r);
}
```

Plus, for LLM-backed strategies only:

```java
public interface StreamingAnswerStrategy extends AnswerStrategy {
    AnswerResult executeStreaming(ClassifiedQuery q, AnswerRequest r, AnswerTokenSink sink);
}
```

**The router chooses; strategies own their dependencies.** That is the whole design. A SQL strategy
holds a `LegalFactPort`; a RAG strategy holds a `RagPort`. The router holds neither and therefore
cannot answer anything itself — it can only delegate.

`usesLlm()` is a *declaration*, and declarations can be wrong. The guard verifies the claim against
what actually happened (`AnswerMetadata.llmInvoked`), so a strategy that lies is caught rather than
trusted.

---

## 2. The strategies

### Deterministic (5) — no `RagPort`, no possible LLM call

| Strategy | Order | Handles | Source |
|---|---|---|---|
| `StatisticsStrategy` | 10 | `STATISTICS` — "berapa akta bulan ini" | `SELECT COUNT(*)` |
| `AggregationStrategy` | 11 | `AGGREGATION` — "berapa akta per jenis" | `GROUP BY` |
| `ReminderStrategy` | 12 | `REMINDER` — "jatuh tempo minggu depan" | ⚠️ **not available yet** |
| `StructuredSearchStrategy` | 13 | `STATUS_LOOKUP`, `IDENTIFIER_LOOKUP` | `dokumen_legal` lookup |
| `SemanticSearchStrategy` | 19 | `SIMILARITY` **when the user asked to see documents** | hybrid retrieval, no synthesis |

### LLM-backed (3) — hold `RagPort`

| Strategy | Order | Handles | Engine |
|---|---|---|---|
| `HybridSearchStrategy` | 20 | `SIMILARITY` (explain/reason) | BM25 + vector + reranker + **LLM synthesis** |
| `ComparisonStrategy` | 21 | `COMPARISON` | RAG in COMPARE mode |
| `DocumentQaStrategy` | 30 | `SUMMARIZE`, `EXPLAIN`, `OPEN_QUESTION` | RAG — **the fallback** |

Order encodes precedence: deterministic strategies are ordered **ahead of** generative ones, so a tie
always resolves toward the engine that cannot invent.

Note `DocumentQaStrategy` is now the **fallback**, at the end. Before this sprint it was not the
fallback — it was the *only* path, which is precisely why "berapa akta bulan ini" landed in it.

---

## 3. Why `SemanticSearchStrategy` and `HybridSearchStrategy` are separate

Both answer "find similar clauses". They differ in what the user actually asked for:

| Query | Strategy | Why |
|---|---|---|
| "**tampilkan** klausul indemnity yang mirip" | `SemanticSearchStrategy` (no LLM) | The lawyer wants to **read the clause**. Paraphrasing a contractual clause is a good way to lose the word that mattered. |
| "klausul indemnity yang mirip dengan ini" | `HybridSearchStrategy` (LLM) | The user wants the system to **reason** across them — synthesis earns its place. |

The classifier detects an explicit listing verb (`tampilkan`, `daftar`, `cari`, `temukan`) and sets
`wantsList=true`. When someone asks to *see* documents, returning the documents is both cheaper and
more honest than returning a model's summary of them.

The specification's section C requires hybrid + synthesis for semantic queries, and
`HybridSearchStrategy` provides exactly that. `SemanticSearchStrategy` is the narrower, safer case.

---

## 4. Strategies that cannot answer yet — and say so

`ReminderStrategy` returns `AnswerResult.unsupported(...)` for **every** query. That is deliberate and
correct: statutory deadlines live on a Case/Deadline aggregate that does not exist.

```java
// ReminderStrategy — the entire execute() body
return AnswerResult.unsupported(NOT_AVAILABLE, name(), ms);
```

It would have been trivial to route these to the LLM instead and return something fluent. That would
have been the **worst possible outcome**: a model asked "which SKMHT expire next week?" will read some
deed text and produce a confident list of dates, and a missed SKMHT deadline voids the security
interest it was created to protect. A wrong answer here does not merely fail to help — it gets acted
upon.

When the Case schema lands, only this method body changes. The routing, the guard, the API contract and
the tests all stay as they are.

Similarly, `StructuredSearchStrategy` distinguishes two honest cases:

- **Document identifiable** → returns the real ingestion status, plus an explicit caveat that *legal*
  status (finalization/approval/delivery) is not yet tracked.
- **No identifier** (i.e. it is about a bundle/case/approval) → `unsupported`, stating the Case module
  does not exist.

---

## 5. `AnswerResult` — the uniform shape

Four factories, each stamping the correct audit metadata so a strategy cannot forget to:

| Factory | `llmInvoked` | `sqlInvoked` | Used by |
|---|---|---|---|
| `fromSql(...)` | `false` | `true` | Statistics, Aggregation, Structured |
| `unsupported(...)` | `false` | `true` | Reminder, Structured (case-scoped) |
| `fromRetrieval(...)` | `false` | `false` | Semantic |
| `fromRag(...)` | **`true`** | `false` | Hybrid, Comparison, DocumentQA |

The caller — the assistant — receives the same type regardless, and **cannot tell which engine ran**.
That is the point of Task 5: the assistant renders answers; it does not choose engines.

---

## 6. The SQL layer

`LegalFactRepositoryImpl` follows the conventions already in the codebase (`BM25SearchRepositoryImpl`):

- **explicit column lists — never `SELECT *`** (project rule)
- bound parameters only; the `GROUP BY` column is chosen from a **closed enum**, never interpolated
- an explicit `tenant_id` predicate **in addition to** Postgres RLS — defence in depth, since RLS
  currently covers only 3 of 13 tables
- time bounds evaluated against the **database clock** (`date_trunc('month', NOW())`), not the JVM's,
  so results are reproducible and immune to server timezone drift

Tables read: `dokumen_legal` (counts, breakdowns, status, nomor akta lookup). Nothing else exists yet.

---

## 7. Streaming

Only LLM strategies implement `StreamingAnswerStrategy` — a SQL `COUNT` has no tokens to stream, it has
a number.

For deterministic answers the router emits the finished answer as **a single token**:

```java
result = strategy.execute(classified, request);
if (result.answerText() != null) sink.onToken(result.answerText());
```

So the SSE transport behaves identically regardless of which engine answered, and the existing
streaming endpoint keeps working without change. Covered by
`AnswerRouterTest.factualAnswersStreamAsSingleToken()`.
