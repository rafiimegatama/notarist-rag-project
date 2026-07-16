# 10 — Backward Compatibility

| Field | Value |
|---|---|
| Status | **No API, DTO, endpoint, or schema change.** |
| Frontend impact | **None.** Claude 2's React Native app needs no change. |
| Build | `./gradlew clean build` → SUCCESS · 122 tests, 0 failures |

---

## 1. What did NOT change

### Endpoints — all identical

| Endpoint | Status |
|---|---|
| `POST /api/v1/search` | ✅ **unchanged** — still the hybrid retrieval pipeline |
| `POST /api/v1/assistant/ask` | ✅ same request, same response shape |
| `POST /api/v1/assistant/ask/stream` | ✅ SSE contract unchanged |
| `GET /api/v1/assistant/history/{sessionId}` | ✅ untouched |
| `GET /api/v1/documents`, `/documents/{id}` | ✅ untouched |
| `POST /api/v1/ingest`, `/{jobId}/confirm`, `/{id}/status` | ✅ untouched |
| `POST /api/v1/auth/*` | ✅ untouched |
| `/ops/*` | ✅ untouched |

### DTOs — all identical

`AssistantResponse`, `CitationDto`, `SearchResponse`, `CitationResponse`, `AssistantCommand`,
`SearchQuery` — **no field added, removed, renamed or retyped**. The mobile app destructures
`answerText`, `citations` and `confidence`; all three are present, with the same types and semantics.

### Database — no migration

No new table, no new column, **no Flyway migration**. `LegalFactRepositoryImpl` only *reads*
`dokumen_legal`, which already exists.

### Untouched by explicit constraint

Frontend · OCR runtime · AI Runtime registry · provider registry · Terraform · GCP · Supabase infra ·
Docker · Cloud Run · authentication · JWT · upload pipeline · embedding runtime · reranker runtime.
The Case and Bundle aggregates are **not implemented** — that remains the next sprint.

---

## 2. What DID change — behaviour, not contract

This is the one thing to be clear about, because it is a real change to a shipped endpoint.

**`POST /assistant/ask` now answers factual questions correctly.**

| Question | Before | After |
|---|---|---|
| "berapa akta bulan ini" | An LLM **estimated** a number from retrieved text | `SELECT COUNT(*)` — **exact** |
| "apakah akta 125 sudah final" | An LLM **inferred** a legal status | SQL lookup + explicit statement that legal status is not yet tracked |
| "SKMHT jatuh tempo minggu depan" | An LLM **invented** deadlines | "not available yet — Case module not implemented" |
| "ringkas dokumen ini" | RAG | RAG (unchanged) |

**The JSON shape is byte-identical.** The client cannot break. But the *content* of some answers
changes — they become true. Two consequences worth stating plainly:

1. **Some answers are now refusals.** "Which SKMHT expire next week?" previously returned a confident,
   fluent, fabricated list. It now returns "this is not available yet". That is a **downgrade in
   apparent capability and an upgrade in actual correctness** — and it is the entire point. A missed
   SKMHT deadline voids the security interest; a user acting on invented dates is worse off than a user
   who knows the system cannot tell them.

2. **Factual answers now say where they came from.** The `confidenceSection` for a SQL-backed answer
   reads *"Pasti — jawaban dihitung langsung dari basis data (bukan dari AI)."* rather than
   *"Tinggi (100%) — didukung dokumen"*. Describing a `COUNT(*)` as "strongly grounded in documents"
   would misstate its provenance.

The frontend renders `confidenceSection` as an opaque string, so this needs no client change.

---

## 3. Internal refactor — no external surface

| Class | Change |
|---|---|
| `AssistantOrchestrator` | Rewritten. Now depends **only** on `AnswerRouter` (+ citation formatting, memory, audit, metrics). It no longer holds `LlmPort` or `SearchPort`. |
| `RagAnswerService` | **New.** The RAG pipeline, extracted verbatim from the orchestrator. Now the only place in the codebase that invokes a language model. |
| `RagAnswerAdapter` | **New.** Implements the search module's `RagPort`. |

The RAG pipeline's ordering guarantees are **unchanged and still mandatory**: retrieve → budget →
**cite before generating** → evaluate grounding → short-circuit if insufficient under STRICT → prompt →
LLM → detect unsupported claims → hallucination guard.

### The dependency inversion that avoids a cycle

`RagPort` is **declared in `notarist-search`** (the consumer) and **implemented in `notarist-assistant`**
(the provider). So:

- the assistant depends on the router ✅
- the router never depends on the assistant ✅ — **no cycle** (rule 10 verifies this)
- every LLM concern stays behind the port, reachable only by strategies permitted to use it

No new Gradle module was needed. `notarist-assistant` already depended on `notarist-search`.

---

## 4. Streaming compatibility

The SSE endpoint is unchanged. LLM-backed strategies stream real tokens. Deterministic strategies have
no tokens to stream — a `COUNT` is a number, not a sequence — so the router emits the finished answer as
**a single token**. The transport therefore behaves identically regardless of which engine answered, and
`onStart` / `onToken` / cancellation all work exactly as before.

Cancellation now routes `AssistantUseCase.cancelStream(traceId)` → `AnswerRouter.cancelStream` →
`RagPort.cancel`, reaching the same `LlmPort.cancelStream` as before. Same behaviour, one more hop, and
the orchestrator no longer needs to know that an LLM exists.

---

## 5. Verification performed

| Check | Result |
|---|---|
| `./gradlew clean build` | ✅ SUCCESS |
| Full test suite | ✅ **122 tests, 0 failures, 0 errors** (was 45 → +77) |
| ArchUnit (10 rules) | ✅ all pass |
| Compile: all 12 modules | ✅ |
| DTO/endpoint diff | ✅ none |
| Flyway migrations added | ✅ none |
| Frontend files touched | ✅ none |

**Not verified:** no live end-to-end run against a running Postgres + Ollama stack. The SQL in
`LegalFactRepositoryImpl` is unit-tested at the routing layer with a mocked port, but the queries
themselves have **not been executed against a real database**. That is the honest gap in this sprint —
see the technical-debt list.
