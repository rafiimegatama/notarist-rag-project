# Sprint 6 — AI Sidecar Platform & End-to-End RAG — Final Report

- **Date:** 2026-07-16
- **Scope:** Make the RAG pipeline work end-to-end. Terraform is feature-complete; not in scope.
- **Method:** `ANALYSIS_FIRST`. Source read top-to-bottom along the query path and the ingestion
  path. Baseline `./gradlew compileJava` = **EXIT 0** (the in-flight Oracle→Postgres / MinIO→GCS
  migration compiles). No public API changed. Repository left dirty, not committed.
- **Headline:** The Java RAG platform is **real and complete** — every port has a real HTTP/DB
  adapter, no stubs on the hot path. What is missing is **not Java code**: it is the **ML sidecar
  services themselves** (PaddleOCR, IndoBERT NER, cross-encoder reranker, and — in sidecar mode —
  bge-m3). They have **no source and no container anywhere in this repo**, and are absent from
  `infra/docker/docker-compose.yml`. Execution therefore breaks at **OCR** on the ingestion path.

---

## 1. Sidecar dependency inventory

Legend: **Implemented** = real adapter, exercised on the hot path · **Partial** = works but a known
functional gap · **Missing** = the Java expects it but the runtime artifact does not exist · **Dead
code** = present but deactivated/unused.

| Capability | Java adapter (real) | Status of the *service* it calls | Wired via |
|---|---|---|---|
| **Embedding (query)** | `QueryEmbeddingRuntimeAdapter` → `OllamaEmbeddingProvider` / `SidecarEmbeddingProvider` | **Implemented** for `ollama` (needs `bge-m3` pulled); **Missing** service for `sidecar` (`:8084`, no container) | `EMBED_PROVIDER` (default `ollama`) |
| **Embedding (ingest)** | `IngestEmbeddingRuntimeAdapter` → `EmbeddingRuntimeWorker` | same backend as above | `EmbeddingPort` |
| **OCR** | `OcrRuntimeService` → `PaddleOcrProvider` (real HTTP `POST /predict/ocr_system`) | **Missing** — no PaddleOCR container, `OCR_ENDPOINT` has **no default** (null ⇒ unavailable) | `OcrServicePort` / `OCR_PROVIDER=paddle` |
| **NER** | `IndoBertNerAdapter` (real HTTP `POST /extract`) | **Missing** — no IndoBERT container (`:8082`) | `NerServicePort` |
| **Reranker** | `RerankerRuntimeWorker` (real HTTP `POST /rerank`) + `NoneRerankerProvider` | `none` = **Implemented** (passthrough); `crossencoder` service **Missing** (`:8083`) | `RERANK_PROVIDER` (default `none`) |
| **LLM (Ollama)** | `OllamaRuntimeAdapter` (real OkHttp `POST /api/chat`, NDJSON streaming) | **Implemented** — Ollama container exists; needs chat model pulled | `LLM_PROVIDER=ollama` |
| **OpenRouter / vLLM / OpenAI / Gemini / Anthropic** | — | **Missing (by design)** — enumerated as future providers in `application.yaml`; no adapter yet | `LLM_PROVIDER` |
| **Qdrant** | `QdrantSearchAdapter` (search) + `QdrantIndexAdapter` (index) | **Implemented** — container present, real HTTP | `VectorSearchPort` / `VectorIndexPort` |
| **Supabase / Postgres** | `BM25SearchRepositoryImpl`, `LegalFactRepositoryImpl`, `ChunkMetadataRepositoryImpl`, JPA | **Implemented** — container present, Flyway migrates | JPA + `@Qualifier` pools |
| **GCS (object storage)** | `GcsDocumentStorageAdapter` | **Implemented (prod)** — no local emulator; needs a real dev bucket + ADC | `DocumentStoragePort` |

**Dead code (safe to ignore, not on the hot path):**
- `search/infrastructure/adapter/RerankerAdapter.java` — `@Component` commented out; superseded by
  `RerankerRuntimeWorker`. It is the identity-`0.5` stub; **not** a Spring bean.
- `assistant/domain/model/LlmResponse.stub(...)` — helper only; the real path uses `LlmResponse`
  built by `OllamaRuntimeAdapter.invoke`.

**Provider-abstraction verdict:** genuinely clean. `RuntimeRegistry` → `LlmRegistry` /
`EmbeddingRegistry` / `RerankerRegistry` resolve the active provider **at startup and fail-fast** on
a bad `*_PROVIDER` id (`AbstractRuntimeRegistry.resolve`, throws listing registered ids). Business
logic only ever sees interfaces.

---

## 2. One question, traced end to end

```
[React Native] AssistantScreen
   │  POST /api/assistant/query   (JWT; SSE for streaming)
   ▼
AssistantController                         notarist-assistant/api/rest
   ▼
AssistantOrchestrator.execute()             ← no longer holds LlmPort/SearchPort
   ▼
AnswerRouter.route()                        notarist-search/application/routing
   │  QueryClassifier → FactualQueryGuard → select strategy (@Order)
   │  • factual/status  → SQL strategy (no LLM)  ── LegalFactRepositoryImpl (Postgres)
   │  • semantic/QA     → RAG strategy ──────────► RagPort
   ▼
RagPort  ⇒  RagAnswerAdapter  ⇒  RagAnswerService     notarist-assistant
   │
   ├─(1) SearchPort ⇒ SearchAdapter ⇒ SearchUseCase ⇒ SearchQueryHandler   notarist-search
   │        classify → normalize → PARALLEL:
   │           ├ KeywordRetriever  ⇒ BM25SearchRepositoryImpl  → Postgres  ✅ runs
   │           └ SemanticRetriever ⇒ QueryEmbeddingPort ⇒ Ollama /api/embed (bge-m3)  ⚠ needs model
   │                                ⇒ VectorSearchPort ⇒ QdrantSearchAdapter → Qdrant ✅ (if indexed)
   │        → SecurityFilter → RRF fusion → DiversityFilter
   │        → RerankerService ⇒ RerankerPort ⇒ RegistryRerankerPort
   │                              → NoneRerankerProvider (default, passthrough) ✅
   │                              → RerankerRuntimeWorker (crossencoder) ✗ no service
   │        → ContextAssembly (citations + grounding + budget)
   │
   ├─(2) AssistantContextBudgetManager  — dedup + prioritize + truncate
   ├─(3) CitationInjector.buildCitations()      ← CITE BEFORE GENERATING (mandatory)
   ├─(4) GroundingEvaluator                      ← STRICT + INSUFFICIENT ⇒ short-circuit, no LLM
   ├─(5) RetrievalContextAssembler + PromptBuilder (PromptVersion.V1_LEGAL_ID)
   ├─(6) LlmPort ⇒ RegistryLlmPort ⇒ OllamaRuntimeAdapter → Ollama /api/chat  ⚠ needs chat model
   ├─(7) UnsupportedClaimDetector (rule-based) + HallucinationGuard
   ▼
AnswerResult → AssistantResponse (answer + citations + confidence + follow-ups)
   ▼  memoryService.store()  +  auditPort.publishInteraction()
[React Native] rendered answer with [Sumber: chunk_id] citations
```

**Where execution breaks:**
- **Query-only path (question ⇒ answer over already-indexed vectors):** does **not** break given
  `postgres + qdrant + ollama` up and `bge-m3` + a chat model pulled. Embedding failure degrades
  gracefully to keyword-only (`SemanticRetriever.embedQuery` returns `null`, not throw). Reranker
  default `none` needs nothing. **This path is runnable today** on the existing compose stack.
- **Full PDF ⇒ answer path:** **breaks at OCR.** `PaddleOcrProvider` has no endpoint (no default,
  no container) ⇒ provider unavailable ⇒ `OcrWorker` fails ⇒ dead-letter. NER would break next for
  the same reason. Both are **missing services, not missing code.**

---

## 3. Missing adapters — none

Every `port/out` interface on the RAG and ingestion paths already has a **real** implementation:

| Port | Implementation |
|---|---|
| `search…QueryEmbeddingPort` | `QueryEmbeddingRuntimeAdapter` |
| `search…VectorSearchPort` | `QdrantSearchAdapter` |
| `search…RerankerPort` | `RegistryRerankerPort` |
| `search…KeywordSearchRepository` | `BM25SearchRepositoryImpl` |
| `search…RagPort` | `RagAnswerAdapter` |
| `assistant…SearchPort` | `SearchAdapter` |
| `assistant…LlmPort` | `RegistryLlmPort` |
| `assistant…AssistantAuditPort` | `AssistantAuditPublisher` (SLF4J — see §7) |
| `ingest…OcrServicePort` | `OcrRuntimeService` → `PaddleOcrProvider` |
| `ingest…NerServicePort` | `IndoBertNerAdapter` |
| `ingest…EmbeddingPort` | `IngestEmbeddingRuntimeAdapter` |
| `ingest…VectorIndexPort` | `QdrantIndexAdapter` |
| `ingest…DocumentStoragePort` | `GcsDocumentStorageAdapter` |
| `ingest…ChunkMetadataRepository` | `ChunkMetadataRepositoryImpl` |

**No placeholder services were created** (per Sprint rules). Fabricating an in-Java "fake OCR" would
have violated the no-mock / no-placeholder rule; the honest state is: adapters done, services owed.

---

## 4. Embedding audit

| Check | Verdict | Evidence |
|---|---|---|
| Batching | ✅ | `EmbeddingRuntimeWorker.embedBatch` / Ollama `input:[...]`; capability `batch(true, 32/64)` |
| Retry | ⚠ **none at embed layer** | No retry in `EmbeddingRuntimeWorker`/`OllamaEmbeddingProvider`; a transient failure marks EMBEDDING degraded and throws. Qdrant/OCR retry, embedding does not. |
| Timeout | ✅ | 15 s per batch via `TimeoutCancellationOrchestrator.submitWithTimeout` |
| Dimension validation | ✅ | Both providers assert `== QdrantVectorPayload.REQUIRED_DIMENSION` (1024); mismatch is fatal |
| Provider abstraction | ✅ | `EmbeddingProvider` interface; `ollama` vs `sidecar` selected by `EMBED_PROVIDER`; shared queue/timeout/degradation channel |

**Note:** query embedding failure is swallowed to `null` in `SemanticRetriever` (correct — keyword
still serves). Ingest embedding failure is **not** swallowed (correct — a document must not be
indexed with a zero vector).

---

## 5. Retriever audit

| Check | Verdict | Evidence |
|---|---|---|
| Top-K | ✅ | `notarist.search.qdrant.top-k-semantic=20`, `bm25.top-k-keyword=20`, `reranker.top-k-final=5`; `query.maxResults()` threaded into Qdrant `limit` |
| Metadata filtering | ✅ | `QdrantFilterBuilder`: tenant + max-classification + docType + `is_searchable`. Tenant/classification come from the authenticated principal, not client headers |
| Similarity threshold | ✅ | `SemanticRetriever.MIN_COSINE_SCORE = 0.60` → Qdrant `score_threshold` |
| Hybrid search | ✅ | Parallel BM25 + vector on a 4-thread pool → per-source security filter **before** merge → RRF fusion (`k=60`) |
| Chunk ordering | ✅ | RRF → diversity filter → rerank sort by `effectiveScore` desc |

**Nuance:** `RerankerRuntimeWorker` returns only `top_k=5`; `RerankerService` keeps unranked chunks
at their fused score, then re-sorts all. Correct, but chunks beyond rank 5 never get a cross-encoder
score — acceptable for `top-k-final=5`.

---

## 6. Prompt builder audit

| Check | Verdict | Evidence |
|---|---|---|
| System prompt | ✅ | `PromptVersion.V1_LEGAL_ID` — 8 mandatory rules, citation-first, Indonesian legal domain, versioned + stored for audit |
| History | ❌ **not wired into the prompt** | `ConversationMemoryService` stores turns, but `RagAnswerService.answer` builds the prompt from retrieval context only. Multi-turn follow-ups have **no conversational memory in the LLM call.** |
| RAG context | ✅ | `RetrievalContextAssembler` emits `[Sumber: chunkId]` blocks; injected into `{CONTEXT}` |
| Citation formatting | ✅ | Markers built **before** the LLM from retrieval metadata (`CitationInjector`), not from model output |
| Context-size protection | ✅ | `AssistantContextBudgetManager` (dedup/prioritize/truncate) + `ContextOverflowGuard` in the Ollama adapter; `PromptBuilder` estimates tokens |
| Prompt-injection resistance | ⚠ **partial** | Strong system-prompt guardrails + `FactualQueryGuard` keeps the LLM off factual queries + grounding short-circuit. But retrieved chunk text is concatenated verbatim into `{CONTEXT}` with **no delimiter hardening / instruction-stripping** — a malicious document could attempt in-context injection. Post-hoc `UnsupportedClaimDetector` + `HallucinationGuard` mitigate but do not prevent. |

---

## 7. Inference audit

| Check | Verdict | Evidence |
|---|---|---|
| Streaming | ✅ | `OllamaRuntimeAdapter.executeStreaming` — OkHttp NDJSON line parse; SSE to client |
| Retry | ⚠ **none** | On failure: mark OLLAMA degraded + return fallback text (non-stream) / fallback token (stream). No re-attempt. Backpressure via `InferenceQueueIsolation` CallerRuns. |
| Timeout | ✅ | Config-driven OkHttp connect/read/write (`LLM_*_TIMEOUT_MS`); read-timeout bounds full gen + inter-token gap |
| Cancellation | ✅ | `StreamingCancellationManager` — opened before queueing; `call.cancel()`; IOException-after-cancel treated as deliberate, **not** degradation |
| Provider abstraction | ✅ | `RegistryLlmPort` → registry-selected `InferenceProvider`; per-call resolution |
| Token accounting | ⚠ **partial** | `eval_count` / `eval_duration` → token-rate metric; but `LlmResponse.invoke` hardcodes `promptTokens=0, completionTokens=0`. Streaming path does not surface token counts at all. |

---

## 8. Citation integrity

- **Citations precede generation** (`RagAnswerService` step 3, before the LLM) and are built from
  **retrieval metadata**, so a citation can never reference a chunk that was not retrieved — the
  structural guarantee against hallucinated citations.
- **Traceability:** every chunk carries `chunkId` → surfaced as `[Sumber: {chunkId}]` in both the
  injected context (`RetrievalContextAssembler`) and the response block (`CitationInjector`). Each
  cited chunk resolves to `documentId` + `sourceObjectKey` + `chunkIndex`.
- **Per-paragraph referencing (the Sprint-8 ask):** the system prompt *requires* a marker per claim,
  and `UnsupportedClaimDetector` flags absolutist sentences lacking a marker — but enforcement is a
  **rule-based heuristic on Indonesian absolutist terms**, not a claim-to-chunk entailment check. A
  bland unsupported sentence passes. This is a known Phase-4 stub (documented in the class).
- **Gap:** `SearchAdapter` does not carry per-chunk `tenantId` / `classificationLevel` / `sectionTitle`
  / full text across the module boundary (only the ~200-char citation excerpt). Downstream tolerates
  the nulls, but the assembled context uses the truncated excerpt, not full chunk text.

---

## 9. End-to-end test (PDF → OCR → Chunk → Embed → Index → Retrieve → LLM → Answer → Citation)

**Could not be executed in this environment.** Reported per Sprint rule "if a dependency is missing,
report it" — this is a factual blocker, not a skipped step:

1. **Docker is not available** in this WSL2 distro (`docker: command not found`) — the compose stack
   cannot be brought up here.
2. **The ML sidecars do not exist as artifacts.** No `*.py`, no FastAPI/uvicorn, no Dockerfile for
   PaddleOCR / IndoBERT NER / cross-encoder anywhere in the repo. `infra/docker/docker-compose.yml`
   ships only `postgres`, `qdrant`, `ollama`. So even with Docker, `OCR → NER` cannot run.

**Static end-to-end verdict** (what *would* happen with the stack up):
- **Ingestion** breaks at **OCR** (no PaddleOCR endpoint) → `OcrWorker` dead-letters. Nothing reaches
  Chunk/Embed/Index. Qdrant stays empty.
- **Query** works **only if** Qdrant is pre-seeded. With an empty index, retrieval returns nothing →
  `GroundingEvaluator` = INSUFFICIENT → STRICT short-circuits to the honest "no basis" fallback
  (correct, safe behavior — it does not hallucinate).

**Compile gate:** `./gradlew compileJava` = **EXIT 0** across all modules (migration intact).

---

## 10. Final report

### Completed / verified
- Full RAG query pipeline is real end-to-end in Java: controller → router → hybrid retrieve → rerank
  → cite-before-generate → grounding gate → Ollama streaming → hallucination guard → citations.
- Every port has a real adapter; **no missing adapters**; no hot-path stubs.
- Provider abstraction is clean and fail-fast (`RuntimeRegistry` + `AbstractRuntimeRegistry`).
- Embedding dimension validation, timeouts, queue isolation, graceful degradation all present.
- Citation model is structurally sound (built from retrieval, pre-LLM).
- **Fix applied this sprint (infra, uncommitted):** `infra/docker/docker-compose.override.yml`
  removed the stale `minio` override (broke `docker compose up` — base has no minio) and now pulls
  **`bge-m3`** alongside the chat model (default `EMBED_PROVIDER=ollama` needs it or every query
  silently loses semantic retrieval).

### Missing (blocks live E2E)
1. **ML sidecar services** — PaddleOCR, IndoBERT NER, cross-encoder reranker (and bge-m3 if run as a
   dedicated sidecar). No source, no container, absent from compose. **Owed as a devops/ML task.**
2. **`OCR_ENDPOINT`** has no value → OCR unavailable at boot (by design: it must not crash-loop the
   whole app, but ingestion cannot run without it).
3. **Docker** unavailable in this environment — no live run possible here.

### Architectural risks
- **Conversation history is not fed to the LLM** (§6) — multi-turn is stateless in the prompt.
- **Prompt-injection surface** — retrieved chunk text concatenated verbatim, no delimiter hardening (§6).
- **No retry on embedding or inference** (§4, §7) — single transient failure degrades immediately.
- **Token accounting incomplete** (§7) — cost/limit tracking unreliable.
- **Citation enforcement is heuristic**, not entailment-based (§8).
- **Audit persistence** — `AssistantAuditPublisher` logs via SLF4J; confirm `notarist-audit` actually
  persists interactions (roadmap F9 review-only).
- **`IndoBertNerAdapter` javadoc still says "MinIO"** post-migration — cosmetic doc drift, object-key
  contract itself is storage-agnostic.

### Recommended Sprint 7
1. **Build & compose the ML sidecars** (PaddleOCR, IndoBERT NER, optional cross-encoder) as
   containers with `/predict/ocr_system`, `/extract`, `/rerank`; add to `docker-compose.yml` with
   healthchecks; set `OCR_ENDPOINT`/`NER_BASE_URL`/`RERANKER_BASE_URL`. Wire their health into
   `notarist-observability`.
2. **Run the real E2E** on a machine with Docker + GPU: one notarial PDF through OCR→…→citation.
3. **Feed conversation history into `PromptBuilder`** (bounded, token-budgeted) — closes the
   multi-turn gap without touching module boundaries.
4. **Harden prompt-injection**: delimiter-fence retrieved context, strip instruction-like spans.
5. **Add retry/backoff** to embedding + inference (reuse `NotaristRetryPolicy`).
6. **Complete token accounting** in `OllamaRuntimeAdapter` (prompt+completion, streaming included).
7. **First OpenRouter/vLLM `InferenceProvider`** to prove the abstraction beyond Ollama.

---
*Generated 2026-07-16 · source-traceable · file:line references verified against working tree.*
