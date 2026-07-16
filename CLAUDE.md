# PROJECT CONTEXT — NOTARIST RAG PLATFORM

> **Status of this document.** Rewritten 2026-07-16 (Sprint 6.5) against the code as it actually
> exists after Sprint TF2 and Sprint 6. Every statement below is traceable to a file in this
> repository; the file is cited inline. Anything that could not be proven from the repository is
> marked **UNKNOWN** rather than guessed.
>
> The previous version of this file described Oracle 19c, `BRANCHPERF*` schemas, a staging datamart
> and local-only inference. None of that exists in the code any more. See
> [Migration summary](#migration-summary-old--new).

---

## 1. Project Overview

### Purpose

Notarist RAG is a knowledge-retrieval system for Indonesian notary (Notaris) and PPAT offices. A
user uploads a legal document; the system extracts its text, redacts PII, chunks and embeds it, and
then answers natural-language questions about the corpus **with citations attached to every answer**.

The defining product constraint is **grounding**. This is not a chatbot with a document search bolted
on: the pipeline assembles citations *before* the model is called, evaluates grounding *before* the
model is called, and refuses to call the model at all when grounding is insufficient in STRICT mode
(`RagAnswerService.answer()`, step 5). An answer the system cannot attribute to a retrieved chunk is
treated as a defect, not a feature.

### Business domain

Notaris, PPAT, akta, sertifikat, fidusia, roya, APHT, SKMHT. Documents carry a classification level —
`PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `STRICTLY_CONFIDENTIAL` (`ClassificationLevel`) — which is
enforced *in SQL* during retrieval (`BM25SearchRepositoryImpl`: "Classification level ordinal enforced
in SQL — no post-filter drift"), not filtered afterwards in Java.

### Overall architecture

A Spring Boot modular monolith, hexagonal (ports and adapters), built as **one deployable**
(`notarist-web` produces the only `bootJar`; `backend/Dockerfile` builds only `:notarist-web:bootJar`).
The 15 Gradle modules are architectural boundaries, not separate services.

The AI capabilities are abstracted behind a provider registry, so swapping an inference backend is an
environment-variable change and a restart rather than a code change
(`application.yaml` → `notarist.runtime.*`; `RuntimeRegistry`, `LlmRegistry`, `EmbeddingRegistry`,
`RerankerRegistry`, `OcrProviderRegistry`).

### Current production target

**Google Cloud Run**, one container, image from Artifact Registry, built by Cloud Build, with all
infrastructure declared in Terraform (`terraform/`, `cloudbuild/cloudbuild.yaml`). PostgreSQL is
Supabase and Qdrant is Qdrant Cloud — both provisioned *outside* Terraform and consumed as URLs
(`terraform/README.md`, "Blockers" #3).

**The production target is not currently reachable end to end.** The RAG sidecars are not deployed by
anything in this repository, so ingestion stalls at OCR and RAG search cannot complete. This is stated
by the infrastructure itself, not inferred: see `terraform/README.md` "Blockers" #1 and the
`sidecar_urls` variable description in `terraform/environments/prod/variables.tf:257`. The app does
start and serve auth and document CRUD without them. See [§6](#6-infrastructure) and
[§10](#10-current-status).

### Technology stack (summary — details in [§3](#3-technology-stack))

Spring Boot 3.2.5 on Java 17, Gradle 8.8 · PostgreSQL (Supabase) · Qdrant · Google Cloud Storage ·
Ollama · Terraform on Cloud Run · JWT auth · PostgreSQL RLS for tenant isolation.

---

## 2. Current Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│ FRONTEND — React Native 0.86 / Expo ~57 (frontend/NotaristApp)           │
│ 4-screen slice. JavaScript, not TypeScript (unresolved — see §10).       │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ HTTPS · JWT Bearer · SSE for streaming
┌────────────────────────────────▼─────────────────────────────────────────┐
│ BACKEND — Spring Boot 3.2.5 modular monolith, ONE Cloud Run container    │
│                                                                          │
│  API LAYER  (@RestController — the endpoints in §2.1 are the only ones)  │
│      AuthController · DocumentController · IngestionController           │
│      SearchController · AssistantController · CaseController             │
│      BundleController · VerificationController · OcrReviewController     │
│      DashboardController · ReminderController · OperationalHealthEndpoint│
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────────────────┐
│ APPLICATION LAYER — orchestrators, handlers, pipelines                   │
│      AssistantOrchestrator → AnswerRouter → AnswerStrategy               │
│      RagAnswerService   (the ONLY place that invokes an LLM)             │
│      PipelineCoordinator (drives 5-stage ingestion via @Scheduled)       │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────────────────┐
│ PORTS (out)  — interfaces owned by the application layer                 │
│      LlmPort · SearchPort · RagPort · VectorSearchPort                   │
│      KeywordSearchRepository · QueryEmbeddingPort · RerankerPort         │
│      DocumentStoragePort · OcrServicePort · NerServicePort               │
│      LegalFactPort · AssistantAuditPort                                  │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────────────────┐
│ ADAPTERS — infrastructure implementations of the ports                   │
│      RagAnswerAdapter · SearchAdapter · RerankerAdapter                  │
│      QueryEmbeddingRuntimeAdapter · IngestEmbeddingRuntimeAdapter        │
│      BM25SearchRepositoryImpl (Postgres) · QdrantIndexAdapter            │
│      IndoBertNerAdapter · GCS storage adapter                            │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────────────────┐
│ PROVIDERS — RuntimeRegistry selects ONE per capability, by env var       │
│      LlmRegistry        ← LLM_PROVIDER     → ollama                      │
│      EmbeddingRegistry  ← EMBED_PROVIDER   → ollama | sidecar            │
│      RerankerRegistry   ← RERANK_PROVIDER  → none | crossencoder         │
│      OcrProviderRegistry← OCR_PROVIDER     → paddle                      │
│      (an unknown id fails STARTUP and lists the registered ids)          │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ HTTP
┌────────────────────────────────▼─────────────────────────────────────────┐
│ SIDECARS — external HTTP services. NOT built, composed or deployed by    │
│            this repository. See §5 — this is the production blocker.     │
│      OCR :8081 · NER :8082 · Reranker :8083 · Embedding :8084            │
│      Ollama :11434  (Ollama alone is in docker-compose.yml)              │
└──────────┬──────────────────────────────────────┬────────────────────────┘
           │                                      │
┌──────────▼─────────────────┐      ┌─────────────▼────────────────────────┐
│ VECTOR DB — Qdrant         │      │ LLM — Ollama (only impl. provider)   │
│ dense semantic retrieval   │      │ OllamaRuntimeAdapter                 │
│ collection notarist_chunks │      │ streaming + cancellation             │
└──────────┬─────────────────┘      └─────────────┬────────────────────────┘
           │                                      │
           │  ┌───────────────────────────────────▼──────────────────┐
           └─►│ RESPONSE — citations + grounding score + warnings,   │
              │ SSE token stream or single JSON body.                │
              │ Guarded: UnsupportedClaimDetector → HallucinationGuard│
              │ → downgrade to fallback message if ungrounded.        │
              └──────────────────────────────────────────────────────┘

SIDE STORES (not in the request path above):
  PostgreSQL (Supabase) — system of record + BM25 keyword index (chunk_index) + audit trail.
                          Tenant isolation by RLS. Flyway V1–V13, run at startup.
  Google Cloud Storage  — 4 buckets: documents (WORM), ocr-output, generated-docs, assets.
```

### 2.1 The complete REST surface

These are **all** the mapped endpoints in the repository. Never cite one that is not on this list
(rule: *never fabricate endpoints*). Base path `/api/v1` via `NotaristConstants.API_BASE_PATH`.

| Method | Path | Controller |
|---|---|---|
| POST | `/api/v1/auth/login`, `/auth/refresh`, `/auth/logout` | `AuthController` |
| GET | `/api/v1/documents`, `/documents/{documentId}` | `DocumentController` |
| POST | `/api/v1/ingest`, `/ingest/{jobId}/confirm` | `IngestionController` |
| GET | `/api/v1/ingest/{ingestionId}/status` | `IngestionController` |
| POST | `/api/v1/search` | `SearchController` |
| POST | `/api/v1/assistant/ask` | `AssistantController` |
| POST | `/api/v1/assistant/ask/stream` (`text/event-stream`) | `AssistantController` |
| GET | `/api/v1/assistant/history/{sessionId}` | `AssistantController` |
| POST/GET | `/api/v1/cases`, GET `/cases/{caseId}`, `/cases/{caseId}/timeline` | `CaseController` |
| PATCH | `/api/v1/cases/{caseId}/status` | `CaseController` |
| GET | `/api/v1/cases/statistics`, `/cases/{caseId}/activities` | `CaseInsightsController` |
| POST/GET | `/api/v1/cases/{caseId}/bundles` | `BundleController` |
| GET | `/api/v1/bundles/{bundleId}`, `/bundles/{bundleId}/timeline` | `BundleController` |
| PATCH | `/api/v1/bundles/{bundleId}/status` | `BundleController` |
| GET | `/api/v1/bundles/{bundleId}/verification`, `/verification/summary` | `VerificationController` |
| POST | `/api/v1/bundles/{bundleId}/verification/checklist/{itemId}` | `VerificationController` |
| PATCH | `/api/v1/bundles/{bundleId}/verification/status` | `VerificationController` |
| GET | `/api/v1/documents/{documentId}/ocr`, `/ocr/summary` | `OcrReviewController` |
| PUT | `/api/v1/documents/{documentId}/ocr/fields/{fieldId}` | `OcrReviewController` |
| PATCH | `/api/v1/documents/{documentId}/ocr/status` | `OcrReviewController` |
| GET | `/api/v1/dashboard/summary` | `DashboardController` |
| GET | `/api/v1/reminders` | `ReminderController` |
| GET | `/ops/health`, `/ops/health/live`, `/ops/health/ready` | `OperationalHealthEndpoint` |
| POST | `/ops/replay/queue`, `/ops/replay/dlq`, `/ops/reindex` | `OperationalHealthEndpoint` |
| GET | `/ops/consistency/vectors`, `/ops/consistency/migrations` | `OperationalHealthEndpoint` |
| GET | `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` | Spring Actuator |

---

## 3. Technology Stack

Versions are from `backend/gradle/libs.versions.toml`, `backend/build.gradle.kts` and
`backend/gradle/wrapper/gradle-wrapper.properties`. **There is no Oracle anywhere in this stack.**

### Backend

| Component | Value | Evidence |
|---|---|---|
| Framework | Spring Boot **3.2.5** | `libs.versions.toml: springBoot` |
| Language | Java **17** (source/target) | `build.gradle.kts:15-16` |
| Build tool | **Gradle 8.8**, Kotlin DSL, version catalog | `gradle-wrapper.properties` |
| Modules | **15**, single deployable | `settings.gradle.kts` |
| Runtime image | `eclipse-temurin:17-jre`, non-root | `backend/Dockerfile` |
| Web | spring-boot-starter-web (MVC) + webflux + reactor 3.6.5 | `libs.versions.toml` |
| Streaming | `SseEmitter` (MVC), **not** WebFlux end-to-end | `AssistantController:94` |
| Mapping | MapStruct 1.5.5 | `libs.versions.toml` |
| API docs | springdoc-openapi 2.5.0 | `libs.versions.toml` |
| HTTP clients | OkHttp 4.12, `RestTemplate` (`aiRuntimeRestTemplate`) | `IndoBertNerAdapter` |

### Database

| Component | Value | Evidence |
|---|---|---|
| Engine | **PostgreSQL** — driver 42.7.3, `PostgreSQLDialect` | `application.yaml:25` |
| Production host | **Supabase** | `terraform/environments/prod/main.tf:274` |
| Pooling | HikariCP, `@Primary` pool built by `PostgresConnectionConfig` | `application.yaml:66-78` |
| Migrations | **Flyway 10.12.0**, V1–V13, `classpath:db/postgres/flyway` | `FlywaySearchConfig:31` |
| Migration ownership | Programmatic; Spring's Flyway auto-config **disabled** so a second Flyway can't open against the same DB | `application.yaml:20-21` |
| Keyword index | `chunk_index` + `search_query_log` (BM25 via `ts_rank`/`plainto_tsquery`, `simple` dictionary) | `BM25SearchRepositoryImpl` |

**One database, one migrator.** There is no longer a Liquibase/Oracle half to keep in step
(`application.yaml:16-19`).

### Vector DB

| Component | Value | Evidence |
|---|---|---|
| Engine | **Qdrant**, client 1.9.1, image `qdrant/qdrant:v1.8.4` locally | `libs.versions.toml`, `docker-compose.yml:46` |
| Collection | `notarist_chunks` (`QDRANT_COLLECTION`) | `application.yaml:93` |
| Ports | 6333 HTTP / 6334 gRPC | `docker-compose.yml` |
| Production | **Qdrant Cloud, provisioned outside Terraform**; API key is a Secret Manager secret | `terraform/README.md` Blockers #3 |

### Object Storage

| Component | Value | Evidence |
|---|---|---|
| Engine | **Google Cloud Storage**, `google-cloud-storage` 2.43.2 | `libs.versions.toml` |
| Auth | **ADC via the Cloud Run runtime SA. No key file anywhere.** | `terraform/README.md` "Secrets vs. configuration" |
| Signed URLs | V4, signed by the runtime SA impersonating **itself** via IAM `signBlob` — no private key mounted | `main.tf:285-287`; requires `iamcredentials.googleapis.com` |
| Buckets | documents (versioned + WORM retention), ocr-output (regenerable, no versioning), generated-documents (versioned), application-assets (**objectViewer only** — a compromised container cannot tamper with templates) | `terraform/environments/prod/main.tf:111-208` |
| **MinIO** | **REMOVED.** Adapters deleted; not in compose. | See [§10](#10-current-status) for the one dead reference left |

### Infrastructure

| Component | Value | Evidence |
|---|---|---|
| IaC | **Terraform** >= 1.5, google provider ~> 6.0 (6.50.0 locked) | `environments/prod/main.tf:15-24` |
| Compute | **Cloud Run** v2, one service `notarist-{env}-api` | `modules/cloudrun` |
| Images | **Artifact Registry**, optional immutable tags + cleanup policy | `modules/artifact_registry` |
| CI/CD | **Cloud Build**: build → test → docker → verify-secrets → plan → apply (gated) → deploy → smoke | `cloudbuild/cloudbuild.yaml`, `terraform/README.md` |
| Environments | dev / staging / prod — **byte-identical `main.tf`**, differences only in tfvars | `terraform/README.md` |
| Monitoring | uptime, 5xx rate, p95 latency, always-on-instance alert | `modules/monitoring` |
| Logging | retention + audit bucket (lockable) + cost exclusions | `modules/logging` |
| Budget | billing budget; **creates nothing unless `billing_account_id` is set** | `modules/budget` |
| Secrets | **Secret Manager containers only — Terraform never writes values** | `modules/secret_manager` |
| Network | **optional** VPC connector + Cloud NAT for static egress IP | `modules/network` |
| Scheduler | heartbeat only — see [§6](#6-infrastructure) | `modules/scheduler` |

### Authentication & Security

| Component | Value | Evidence |
|---|---|---|
| AuthN | **JWT**, RS256, jjwt 0.12.5 | `libs.versions.toml`, `JwtService` |
| Key material | RSA PEM **files** mounted from Secret Manager at `/etc/notarist/keys` — `JwtService` calls `Files.readString(Paths.get(...))` | `terraform/README.md` |
| TTLs | access 900s, refresh 604800s | `application.yaml:59-60` |
| Filter | `JwtAuthenticationFilter`, wired into the chain, Boot auto-registration disabled to prevent double execution | `SecurityConfig` |
| Tenant isolation | **PostgreSQL RLS** (`V9__tenant_isolation_rls.sql`), applied by `RlsContextApplier` per module, identity from `VpdContextHolder` | migrations + `notarist-*/infrastructure/security/` |
| Logout | token deny-list | `V7__audit_trail_and_token_deny_list.sql` |
| Field encryption | AES-256 key + salt, secrets provisioned — **but see the dead-config note in [§7](#7-environment-variables)** | `main.tf:50-51` |

> **The highest-consequence security invariant.** The Supabase app role must not be a superuser and
> must not hold `BYPASSRLS`. Either one silently voids every cross-tenant policy — everything keeps
> working and every tenant sees every other tenant. **Nothing in GCP can detect this**; it is a
> Supabase-side setting (`terraform/README.md`). Conversely, RLS returning zero rows is fail-closed
> *by design* and looks exactly like a bug; the `rls_identity_skipped` log metric exists to tell the
> two apart.

---

## 4. AI Architecture

### The RAG pipeline, as implemented

`RagAnswerService.answer()` is **the only place in the codebase that invokes a language model**. It is
reachable exclusively through `RagPort`, which only LLM-eligible strategies hold. Its ordering
guarantees are mandatory and enforced in that order:

```
1. Retrieval            SearchPort.search(...)
2. Context budget       AssistantContextBudgetManager — dedup + prioritize + truncate
3. CITATIONS            CitationInjector.buildCitations(...)      ← BEFORE the LLM
4. Grounding eval       GroundingEvaluator.evaluate(...)          ← BEFORE the LLM
5. SHORT-CIRCUIT        INSUFFICIENT + STRICT  →  never call the model, return fallback
6. Context assembly     RetrievalContextAssembler.assemble(...)
7. Prompt               PromptBuilder.build(..., PromptVersion.V1_LEGAL_ID, ...)
8. LLM                  LlmPort.invoke(...)  or  LlmPort.stream(...)
9. Claim detection      UnsupportedClaimDetector.detect(answer, citations)
10. Guard               HallucinationGuard.guard(...) → downgrade to fallback if ungrounded
```

Retrieval itself (`notarist-search`) runs: `QueryNormalizer` → `IntentClassifier` /
`QueryClassifier` → `SemanticRetriever` (Qdrant) **+** `KeywordRetriever` (Postgres BM25) →
`RetrievalFusionService` (RRF) → `RerankerService` → `DiversityFilterService` →
`SecurityFilterService` → `ContextBudgetManager` → `CitationResolver` → `GroundingValidator`.
`AnswerRouter` dispatches to an `AnswerStrategy` (`HybridSearchStrategy`, `SemanticSearchStrategy`,
`StructuredSearchStrategy`, `DocumentQaStrategy`, `AggregationStrategy`, `ComparisonStrategy`,
`StatisticsStrategy`, `ReminderStrategy`) — only some of which are LLM-eligible.

| Capability | Status | Where |
|---|---|---|
| Embedding | **Implemented** — two providers, query + ingest paths | `OllamaEmbeddingProvider`, `SidecarEmbeddingProvider`, `QueryEmbeddingRuntimeAdapter`, `IngestEmbeddingRuntimeAdapter` |
| Retriever | **Implemented** | `SemanticRetriever`, `KeywordRetriever` |
| Hybrid search | **Implemented** — dense + sparse fused | `HybridSearchStrategy`, `RetrievalFusionService` |
| BM25 | **Implemented** — Postgres `ts_rank` + `plainto_tsquery`, `simple` dictionary (no Indonesian stemmer) | `BM25SearchRepositoryImpl` |
| Vector search | **Implemented** — real Qdrant HTTP client | `QdrantIndexAdapter`, `VectorSearchPort` |
| RRF fusion | **Implemented** | `RetrievalFusionService` |
| Context budget | **Implemented** — twice, at two layers | `ContextBudgetManager` (search), `AssistantContextBudgetManager` (assistant) |
| Prompt builder | **Implemented** — versioned (`V1_LEGAL_ID`), carries chunk IDs for cross-referencing | `PromptBuilder`, `AssembledPrompt` |
| Hallucination guard | **Implemented** — downgrades to a fallback message; metric counts downgrades | `HallucinationGuard`, `AssistantMetricsRegistry` |
| Unsupported claim detector | **Implemented** | `UnsupportedClaimDetector` |
| Grounding evaluator | **Implemented** — pre-LLM; also `GroundingValidator` in search | `GroundingEvaluator` |
| Citation injector | **Implemented** — citation-first, `[CITATION-N]` markers | `CitationInjector` |
| Streaming | **Implemented** — SSE, token/citation/complete/warning/error events, cancellable | `AssistantController:93`, `ResponseStreamer`, `StreamingCancellationManager` |
| Provider abstraction | **Implemented** — registry per capability, startup-validated | `RuntimeRegistry` + 4 registries |
| Token accounting | **Partial** — token *budgeting* and overflow guarding exist (`ContextOverflowGuard`, budget managers, `LlmResponse` token fields). No cost accounting, no per-tenant token quota or aggregation. | see files listed |
| Conversation memory | **Implemented** | `ConversationMemoryService` |
| Follow-up suggestions | **Implemented** | `FollowUpSuggestionService` |
| Safety modes | **Implemented** — `STRICT` / `BALANCED` | `AssistantSafetyMode` |

### Providers — exact status

Selection keys are deliberately symmetrical: `LLM_PROVIDER`, `EMBED_PROVIDER`, `RERANK_PROVIDER`,
`OCR_PROVIDER`. **An unknown id fails startup and lists the ids that ARE registered** — it must never
boot green and then dead-letter every document (`application.yaml:182-185`).

| Capability | Provider id | Status |
|---|---|---|
| LLM | `ollama` | **Implemented** — `OllamaRuntimeAdapter`, the only `InferenceProvider` impl. Streaming + cancellation + queue isolation. |
| LLM | `vllm`, `openai`, `gemini`, `anthropic`, `openrouter`, `tensorrt` | **Not implemented** — named as future options in a comment only (`application.yaml:154`). No classes exist. |
| Embedding | `ollama` | **Implemented** — `OllamaEmbeddingProvider` (bge-m3 served by Ollama). **Default.** |
| Embedding | `sidecar` | **Implemented** — `SidecarEmbeddingProvider` → `EmbeddingRuntimeWorker`, dedicated bge-m3 HTTP service on :8084. |
| Reranker | `none` | **Implemented** — `NoneRerankerProvider`, keeps retrieval order. **Default.** |
| Reranker | `crossencoder` | **Implemented in code** — `RerankerRuntimeWorker` (bge-reranker-v2-m3 over HTTP :8083). Off by default; requires a sidecar that nothing deploys. |
| OCR | `paddle` | **Implemented** — `PaddleOcrProvider`, `POST {endpoint}/predict/ocr_system`. **Default.** |
| OCR | `surya`, `gemini`, `mistral`, `azure`, `google-vision` | **Not implemented** — commented-out config stubs (`application.yaml:234-248`). "An endpoint here with no matching provider class is inert." |
| NER | `indobert` | **Implemented** — `IndoBertNerAdapter`, `POST {endpoint}/extract` on :8082. Not registry-selected; a direct `NerServicePort` `@Component`. |

**Dead configuration — present in config, read by nothing.** Verified by grepping every
`@Value` key and `@ConfigurationProperties` prefix in `backend/`:

| Config | Problem |
|---|---|
| `notarist.ai.ollama.{model,stream,max-tokens}` | **No reader.** Also contradicts the live key: it defaults to `notarist-llm-7b` while `notarist.runtime.llm.model` defaults to `qwen3:14b`. Two different "models", one of which does nothing. |
| `notarist.ai.hallucination-guard.{enabled,min-citation-count}` | **No reader.** The guard is always on and its thresholds are in code — this config cannot disable or tune it. Dangerously misleading. |
| `notarist.search.{qdrant.top-k-semantic, bm25.top-k-keyword, rrf.k, reranker.top-k-final}` | **No reader.** Retrieval tuning knobs that tune nothing. |
| `notarist.ingestion.queue.*` | **Namespace drift.** Code reads `notarist.ingest.*`; the yaml declares `notarist.ingestion.*`. The declared block is inert and the code silently uses `@Value` defaults. |
| `notarist.security.encryption.{key,salt}` | **No reader**, though Terraform provisions both secrets and Cloud Run injects them. Field-level encryption is **UNKNOWN / likely not wired**. |
| `notarist.observability.*` | **No reader.** |
| `notarist.sidecar.*.timeout-ms` | **No reader** — only `base-url` is read (`ModelRegistry`). Per-sidecar timeouts are hardcoded (e.g. `IndoBertNerAdapter.NER_TIMEOUT_MS = 30_000`). |
| Terraform secrets `openrouter-api-key`, `gemini-api-key` | Provisioned in `main.tf:55-56` for providers that **do not exist in code**. |

*Only bound prefixes:* `notarist.database.postgres`, `notarist.ocr`, `notarist.storage.gcs`,
`notarist.storage.qdrant` (`@ConfigurationProperties`), plus `@Value` keys under
`notarist.auth.jwt.*`, `notarist.postgres.*`, `notarist.runtime.*`, `notarist.sidecar.*.base-url`,
`notarist.ingest.*`, `notarist.storage.gcs.location`.

---

## 5. Sidecar Architecture

**The headline fact: not one sidecar is built, composed or deployed by this repository, except
Ollama.** There is exactly one `Dockerfile` (`backend/Dockerfile`, the app itself). `docker-compose.yml`
contains only `postgres`, `qdrant` and `ollama`. Terraform states it does not provision them and that
"until they exist … ingestion stalls at the OCR stage and RAG search cannot complete"
(`terraform/README.md` Blockers #1).

The Java clients are real and finished. **What is missing is the services on the other end.**

| Sidecar | Endpoint | Port | Provider / runtime | Health | Client implemented? | In compose? | Mandatory? |
|---|---|---|---|---|---|---|---|
| **OCR** | `POST /predict/ocr_system` | 8081 | PaddleOCR (`paddle`) | `OcrHealthIndicator`; startup probe `OCR_PROBE_ON_STARTUP=true` | **Yes** — `PaddleOcrProvider` | **No** | **Yes** — ingestion stalls at stage 1 without it. Note `fail-fast-on-unhealthy-provider` defaults **false** on purpose: a slow OCR sidecar must not crash-loop an app that can still serve auth, documents and search. |
| **NER** | `POST /extract` | 8082 | IndoBERT | via `RuntimeDegradationManager` | **Yes** — `IndoBertNerAdapter` | **No** | **Yes** — `NerWorker` refuses to advance to chunking when `piiRedacted` is false. PII redaction gates the pipeline. |
| **Embedding** | HTTP | 8084 | bge-m3 | `AiRuntimeHealthIndicator` | **Yes** — `SidecarEmbeddingProvider` | **No** | **No** — only when `EMBED_PROVIDER=sidecar`. Default `ollama` uses the Ollama container instead. |
| **Reranker** | HTTP | 8083 | bge-reranker-v2-m3 | `AiRuntimeHealthIndicator` | **Yes** — `RerankerRuntimeWorker` | **No** | **No** — default `RERANK_PROVIDER=none`. |
| **LLM (Ollama)** | `/api/generate`-style | 11434 | Ollama `0.3.12` | `curl /` healthcheck | **Yes** — `OllamaRuntimeAdapter` | **YES** — the only one | **Yes** — no LLM, no answers. Model pulled via `ollama pull llama3.2:3b-instruct-q8_0`. |

Endpoint URLs come from `notarist.sidecar.*.base-url` via `ModelRegistry`. In production they arrive
as the raw `sidecar_urls` env-var map (`terraform/environments/prod/variables.tf:257`), deliberately
untyped so they can be wired without a Terraform change once the services exist.

Two deliberate design choices worth preserving:
- **The OCR endpoint has no localhost default** (`application.yaml:226-231`): "a default that works on
  a laptop and dies silently in a container is a worse failure than a clear startup error." The other
  sidecars *do* default to localhost — an inconsistency, not a rule.
- **Retry is applied once, centrally**, in `OcrRuntimeService`; providers must not retry internally or
  policies multiply (3 × 3 = 9 attempts against an already-overloaded engine). Backoff is exponential
  with jitter, because without jitter a batch retries in lockstep and re-saturates the engine forever.

**UNKNOWN:** whether the sidecars exist anywhere outside this repository (a separate repo, a manual
deployment, a vendor service). The roadmap has been asking this since 2026-07-12
(`docs/agents/20-roadmap.md`, "Known gaps") and it is still unanswered. **This is the single most
important open question in the project.**

---

## 6. Infrastructure — Sprint TF2

Everything in Google Cloud is declared in `terraform/`. Nothing is clicked in the console.

```
Cloud Build ──► Artifact Registry ──► Cloud Run ──► Supabase (PostgreSQL)
                                          │           Qdrant (vectors)
                                          ├────────► Cloud Storage (4 buckets)
                                          └────────► Secret Manager
                    Cloud Scheduler ──────┘
                    Cloud Logging + Monitoring + Budget
```

- **Cloud Run** — one v2 service per environment. `min_instances >= 1` and `cpu_idle = false` are
  *derived* from `run_in_process_schedulers` rather than settable independently, because the pipeline
  runs on in-process `@Scheduled` timers and Cloud Run gives an idle instance no CPU. Cloud Build
  owns the image after first create (`ignore_changes` on `image`) so Terraform and CI never fight.
- **Artifact Registry** — reader binding for the runtime SA, writer for the deployer; optional
  immutable tags and a cleanup policy with a dry-run switch.
- **Logging** — separate from monitoring *on purpose*: monitoring owns alerting (what should page
  someone), logging owns retention and ingestion (what is kept, how long, what is never charged for).
  Includes a lockable audit bucket and health-check/static-asset exclusions.
- **Monitoring** — uptime check, 5xx rate, p95 latency, and this system's specific failure modes:
  an alert if the always-on instance disappears (that silently stops ingestion), and the
  `rls_identity_skipped` log metric.
- **Budget** — creates nothing unless `billing_account_id` is set, because a budget is created against
  the *billing account* and a project-level deployer usually has no permission there. Reuses
  monitoring's notification channels rather than minting a second set for the same people.
- **IAM** — three identities: runtime, deployer, invoker. Least privilege is real, not nominal: the
  app is `objectAdmin` on document buckets but only `objectViewer` on application-assets; the runtime
  SA holds `serviceAccountTokenCreator` **on itself** purely to sign V4 URLs. GitHub Actions uses
  keyless Workload Identity Federation with the repository as the security boundary.
- **Secret Manager** — containers only. Terraform never sets values, because a value passed through a
  Terraform variable is written into state in plaintext (`sensitive = true` hides it from CLI output;
  it does **not** remove it from state). Values are added out of band via `gcloud secrets versions add`.
  A revision referencing a zero-version secret **will not start**, and the error points at the
  revision, not the empty secret — `verify-secrets` in Cloud Build catches this first.
- **Only credentials are secrets.** `POSTGRES_URL`, `QDRANT_URL`, `GCS_BUCKET`, `OLLAMA_BASE_URL` are
  connection *topology* and travel as plain env vars: a URL is not a credential, and routing it through
  Secret Manager would add cost, an IAM dependency and a startup failure mode for no confidentiality gain.

### Scheduler limitations — stated plainly

Cloud Scheduler **cannot** drive the ingestion pipeline. The pipeline runs on in-process `@Scheduled`
timers inside the JVM; there is no HTTP endpoint to POST to. What keeps them running is
`min_instances >= 1` with `cpu_idle = false`. The heartbeat job GETs `/actuator/health` — it provides
an external liveness signal and a trickle of requests as a backstop if someone parks the schedulers by
setting `min_instances = 0`. `var.jobs` exists for the day the triggers *are* exposed as endpoints
(`modules/scheduler/main.tf`).

**Consequence: the service cannot scale to zero.** The fix is a Java change, not a Terraform one.

### Disaster Recovery

**Not implemented.** There is no backup, restore, PITR or DR resource in `terraform/` — grep returns
nothing. RTO/RPO targets exist **only as a design document**
(`docs/architecture/step6_monorepo_infra_architecture.md:1256-1279`) and are not realised anywhere.

What *does* exist is partial data protection, which is not DR: bucket versioning, WORM retention on
the documents bucket (7-year obligation), and a lockable audit log bucket. Supabase and Qdrant backup
policy is **UNKNOWN** — they are outside Terraform.

### Production readiness — honest assessment

**Not production ready.** From `terraform/README.md` "Blockers", verbatim in substance:

1. **RAG sidecars are not deployed by this Terraform.** Ingestion stalls at OCR; RAG search cannot
   complete. Auth and document CRUD do work.
2. **The service cannot scale to zero** (in-process schedulers).
3. **Supabase and Qdrant are provisioned outside Terraform** — their URLs and RLS-safe role attributes
   are inputs here, not outputs.
4. **The first `apply` creates a Cloud Run service that will not go healthy** until secrets are
   populated and a real image is pushed. Expected, not a misconfiguration.
5. **Nothing here has been applied against a real GCP project.** `terraform validate` passes for all
   four roots against the real provider schema — which catches schema and type errors, not quota,
   IAM-propagation or org-policy problems. The first `apply` is still a first apply.
6. **`deploy/cloudrun/service.yaml` + `deploy.sh` still exist** from the earlier imperative approach and
   overlap with this Terraform. There should be one way to deploy.

---

## 7. Environment Variables

Only variables with a real reader are listed. Names are from `application.yaml` and
`terraform/environments/prod/main.tf`.

### Required — production

| Variable | Purpose |
|---|---|
| `POSTGRES_URL` | Supabase JDBC URL. Local default exists; **must** be set in prod. |
| `POSTGRES_USER` | App role. Must **not** be superuser or hold `BYPASSRLS`. |
| `POSTGRES_PASSWORD` | Secret Manager → env. **No default — startup fails without it.** |
| `GCS_BUCKET` | **No default, deliberately** — bucket names are globally unique, so a shipped default would collide or silently write to someone else's bucket. |
| `QDRANT_URL` | Qdrant Cloud endpoint. |
| `QDRANT_API_KEY` | Secret Manager → env. |
| `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` | **Filesystem paths**, not key material — Secret Manager *volume mounts* at `/etc/notarist/keys/…`. Passing the PEM as an env var fails at startup. |
| `OCR_ENDPOINT` (or `OCR_BASE_URL`) | **No localhost default.** Required for ingestion. |

### Required — Cloud Run specific

| Variable | Purpose |
|---|---|
| `PORT` | Injected by Cloud Run. `server.port` honours `PORT` → `SERVER_PORT` → 8080. Binding anything else fails the startup probe. |
| `SPRING_PROFILES_ACTIVE` | Set to the environment name by Terraform. Local default `local`. |
| `GCS_SIGNING_SERVICE_ACCOUNT` | Runtime SA — signs V4 URLs via IAM `signBlob` with no private key on disk. |
| `GOOGLE_CLOUD_PROJECT` | Optional on Cloud Run (resolved from metadata); set when ADC carries no default project. |

### Optional — tuning (all have working defaults)

| Variable | Default |
|---|---|
| `LLM_PROVIDER` / `LLM_MODEL` | `ollama` / `qwen3:14b` |
| `EMBED_PROVIDER` / `EMBED_MODEL` | `ollama` / `bge-m3` |
| `RERANK_PROVIDER` / `RERANK_MODEL` | `none` / `bge-reranker-v2-m3` |
| `OCR_PROVIDER` | `paddle` |
| `LLM_CONNECT_TIMEOUT_MS` / `LLM_READ_TIMEOUT_MS` / `LLM_WRITE_TIMEOUT_MS` | 5000 / 120000 / 10000 |
| `OCR_TIMEOUT` | 120000 (wall-clock for one document, across all retries) |
| `OCR_RETRY_MAX_ATTEMPTS` / `_INITIAL_BACKOFF_MS` / `_MAX_BACKOFF_MS` / `_MULTIPLIER` / `OCR_RETRY_JITTER` | 3 / 1000 / 15000 / 2.0 / 0.2 |
| `OCR_BATCH_ENABLED` / `OCR_BATCH_SIZE` | true / `0` = **derive from detected hardware** (CUDA + VRAM via `GpuAwarenessConfig`), never from a GPU model name |
| `OCR_PROBE_ON_STARTUP` / `OCR_FAIL_FAST` | true / **false** |
| `POSTGRES_POOL_MAX` | 10 (prod tfvars: 8) |
| `POSTGRES_CONNECTION_TIMEOUT_MS` / `_IDLE_TIMEOUT_MS` / `_MAX_LIFETIME_MS` | 30000 / 600000 / 1800000 |
| `QDRANT_COLLECTION` | `notarist_chunks` |
| `GCS_AUTO_CREATE_BUCKET` | false — **keep false in prod**; Terraform owns the bucket |
| `GCS_LOCATION` / `GCS_CREDENTIALS_PATH` | `US` / unset (local/CI signing only) |
| `JWT_ISSUER` / `JWT_ACCESS_TOKEN_TTL_SECONDS` / `JWT_REFRESH_TOKEN_TTL_SECONDS` | `notarist-rag` / 900 / 604800 |
| `SHUTDOWN_TIMEOUT` | 25s — must stay under Cloud Run's SIGTERM→SIGKILL grace |
| `LOG_LEVEL` / `PROMETHEUS_ENABLED` | INFO / true |
| `NER_BASE_URL` / `RERANKER_BASE_URL` / `EMBEDDING_BASE_URL` / `OLLAMA_BASE_URL` | localhost :8082 / :8083 / :8084 / :11434 |
| `notarist.ingest.*` (`@Value` defaults only, absent from yaml) | `chunk.max-text-bytes`, `embedding.batch-size`, `max-retries`, `scheduler.{concurrency-per-stage,thread-pool-size,worker-id}`, `signed-url-ttl-seconds` |

### Development-only

`POSTGRES_PORT`, `POSTGRES_DB`, `QDRANT_HTTP_PORT`, `QDRANT_GRPC_PORT`, `OLLAMA_PORT`
(`docker-compose.yml`). Local secrets belong in `.env.local`, never in `docker-compose.override.yml`.

### Obsolete — removed, do not reintroduce

Every Oracle variable (`ORACLE_*`, TNS/JDBC-thin URLs, `BRANCHPERF*` schema names) and every MinIO
variable (`MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`). No reader exists
for any of them.

### Declared but inert

`APP_ENCRYPTION_KEY` and `APP_ENCRYPTION_SALT` are provisioned as secrets and injected by Cloud Run,
but **nothing reads `notarist.security.encryption.*`**. Do not assume field-level encryption is active.
See the dead-configuration table in [§4](#4-ai-architecture).

---

## 8. Development Workflow

```
Developer
   │
   ├─► docker compose up            (infra/docker/docker-compose.yml)
   │      postgres:5432 · qdrant:6333/6334 · ollama:11434
   │      NOTE: no OCR/NER/reranker/embedding sidecar exists here.
   │
   ├─► gcloud auth application-default login    ← object storage is REAL GCS
   │      export GCS_BUCKET=<a real dev bucket>
   │
   ├─► ./gradlew :notarist-web:bootRun          (Spring Boot :8080)
   │      Flyway V1–V13 runs at startup against Postgres
   │
   ├─► Qdrant   ← collection notarist_chunks
   ├─► Ollama   ← docker exec notarist-ollama ollama pull llama3.2:3b-instruct-q8_0
   │
   └─► git push  ─►  Cloud Build  ─►  Terraform  ─►  Cloud Run
```

### Local development

1. `docker compose up` from `infra/docker/`. You get Postgres, Qdrant and Ollama — **and nothing
   else**. OCR and NER have no local container, so the ingestion pipeline cannot complete locally
   unless you point `OCR_ENDPOINT` / `NER_BASE_URL` at services you run yourself.
2. **Object storage is real GCS, not a local container.** Authenticate with
   `gcloud auth application-default login` and set `GCS_BUCKET`. A `fake-gcs-server` emulator can
   cover offline object I/O but **does not support V4 signed-URL verification**, so the upload flow
   must be exercised against a real dev bucket (`docker-compose.yml:9-14`).
3. Set `POSTGRES_PASSWORD` and `GCS_BUCKET` — neither has a default and both fail loudly.
4. `./gradlew :notarist-web:bootRun`. Flyway migrates on startup; `ddl-auto: none` — schema comes from
   migrations, never from Hibernate.
5. Build everything: `./gradlew build`. Tests: JUnit 5 (see the coverage gap in [§10](#10-current-status)).

### Production deployment

Push to git; Cloud Build does the rest (`cloudbuild/cloudbuild.yaml`):

1. Gradle build + tests — fails here, nothing is published. (The Dockerfile builds with `-x test` on
   purpose: a container build is the wrong place to discover a failing test.)
2. Docker build, cached from the previous environment image.
3. `verify-secrets` — every secret has an enabled version.
4. `terraform plan` — surfaces drift on every deploy.
5. `terraform apply` — **gated behind `_AUTO_APPLY=true`**. A routine code push must not mutate
   infrastructure as a side effect.
6. Deploy — sets **only the image**. Env, secrets, scaling and mounts are Terraform's.
7. Smoke test — polls `/actuator/health`. A deploy that reports success but serves 503 is not a success.

One-time per project: `terraform/bootstrap` (state bucket + APIs), then populate secrets out of band —
`terraform output secret_populate_commands` prints the exact list.

---

## 9. Repository Structure

Architecture, not a directory tree. Every module follows the same hexagonal shape:
`api/` (REST) → `application/` (handlers, ports, pipelines) → `domain/` (pure, Spring-free) →
`infrastructure/` (adapters). **The domain layer is pure and Spring-free — the cheapest place to add
tests.**

| Module | Responsibility |
|---|---|
| `notarist-core` | Shared kernel: value objects (`NomorNIK`, `NomorNPWP`, `JenisDokumen`, `ClassificationLevel`), `VpdContextHolder` (authenticated tenant/user identity), domain policies (`OcrConfidencePolicy`), `NotaristConstants`. Depended on by everything; depends on nothing. |
| `notarist-auth` | JWT issue/refresh/logout, `JwtService` (RS256 from PEM files), `JwtAuthenticationFilter`, `SecurityConfig`, users/roles, token deny-list, `RlsContextApplier`. |
| `notarist-document` | Document metadata, retrieval, `DocumentStatusMachine`, audit event publishing. |
| `notarist-ingest` | **The 5-stage ingestion pipeline.** `PipelineCoordinator` drives `OcrWorker` → `NerWorker` → `ChunkWorker` → `EmbeddingWorker` → `IndexingWorker` via in-process `@Scheduled` timers, governed by `PipelineStateMachine` (`PipelineStage`: `*_QUEUE` / `*_PROCESSING` pairs, 1–5). Retry policy, dead-letter handling, duplicate detection, `DocumentChunker`. Owns `OcrServicePort`, `NerServicePort`, `DocumentStoragePort`. |
| `notarist-runtime` | **The AI runtime.** Provider registries (LLM/embedding/reranker/OCR), provider implementations, `ModelRegistry` (sidecar endpoint resolution), degradation manager, timeout/cancellation orchestrator, queue isolation per capability, GPU-aware batch sizing, `ContextOverflowGuard`, runtime metrics. |
| `notarist-search` | **Retrieval.** Hybrid dense+sparse: `SemanticRetriever` (Qdrant) + `KeywordRetriever` (Postgres BM25) → RRF fusion → rerank → diversity → security filter → context budget → citations → grounding validation. `AnswerRouter` + 8 `AnswerStrategy` implementations. Owns `RagPort` — the only route to an LLM. |
| `notarist-assistant` | **Conversational RAG.** `AssistantOrchestrator`, `RagAnswerService` (the only LLM invoker), citation-first pipeline, hallucination guard, unsupported-claim detection, grounding evaluation, versioned prompts, SSE streaming, conversation memory, follow-ups. |
| `notarist-case` | **Largest module (145 files).** Cases, bundles, workflow, timeline, activities, reminders, dashboard. |
| `notarist-review` | Human-in-the-loop OCR review: field correction, review status, summaries. The escape hatch for low-confidence OCR. |
| `notarist-verification` | Bundle verification checklists and status. |
| `notarist-regulation` | **Stub — one file** (`RegulasiMaster`). Regulation retrieval is named across the architecture docs but not built. |
| `notarist-audit` | Audit trail persistence and event listening. |
| `notarist-infra` | Cross-cutting infrastructure: `PostgresConnectionConfig` (the `@Primary` pool), `FlywaySearchConfig` (programmatic migration), `NotaristMigrationRunner`, GCS storage adapter, degraded-mode registry, integration timeouts. |
| `notarist-observability` | Health endpoints (`/ops/*`), operational replay/reindex/consistency checks, correlation-ID propagation, metrics. |
| `notarist-web` | **The only deployable.** Spring Boot entry point, the single `application.yaml`, the only `bootJar`. |

Non-module directories:

| Path | Contents |
|---|---|
| `terraform/` | `bootstrap/` (once per project) · `modules/` (10 modules) · `environments/{dev,staging,prod}` (byte-identical `main.tf`; all difference in tfvars) |
| `cloudbuild/` | `cloudbuild.yaml` — the CI/CD pipeline |
| `infra/docker/` | Local compose stack (postgres, qdrant, ollama) |
| `frontend/NotaristApp/` | React Native 0.86 / Expo ~57 — 4-screen slice |
| `docs/` | `agents/` (operating framework) · `architecture/` (STEP 2–7.5 design) · `business/` · `infrastructure/` (Sprint TF2 write-up). **Design intent — not always current state.** |
| `generated/` | Disposable analysis output — `docs/`, `sql/`, `json/`, `openapi/`, `logs/`, `sprint6/` |
| `deploy/` | **Legacy imperative deploy.** Overlaps Terraform; should be deleted. |
| `database/` | **Stale duplicate.** See [§10](#10-current-status). |

---

## 10. Current Status

### Completed

- **Oracle → PostgreSQL migration.** No Oracle in the stack. One database, one migrator, Flyway V1–V13.
  `VpdContextApplier` → `RlsContextApplier`; Oracle VPD → PostgreSQL RLS.
- **MinIO → Google Cloud Storage.** Adapters deleted; ADC auth, no key file anywhere; V4 signed URLs
  via IAM `signBlob`; 4 purpose-separated buckets.
- **Sprint TF2 — the full Terraform estate.** 10 modules, 3 environments, bootstrap, CI/CD, monitoring,
  logging, budget, least-privilege IAM, keyless GitHub Actions. `terraform validate` passes on all four
  roots against the real provider schema.
- **Sprint 6 — the RAG pipeline carries real data.** Real per-chunk flow `ChunkWorker → EmbeddingWorker
  → IndexingWorker → Qdrant`; real embeddings, not stub zero-vectors; OCR gating reaches the index via
  `ChunkPayload.searchable`. The F11/F20 keystone stall is fixed.
- **Provider abstraction.** Four registries, startup-validated, env-swappable.
- **Grounding machinery.** Citation-first ordering, pre-LLM grounding evaluation, STRICT short-circuit,
  claim detection, hallucination guard.
- **Build.** All 15 modules compile; zero-defect build.
- **Security.** JWT filter chain wired, cross-tenant header trust removed (identity from the
  authenticated principal, never a client header), RLS migration in place.

### Partial

- **Token accounting** — budgeting and overflow guarding, but no cost accounting or per-tenant quotas.
- **Reranking** — `crossencoder` implemented but off by default and sidecar-less.
- **Frontend** — 4 screens (Home, Login, Documents, Assistant) against a 15-screen + 5-modal design.
  React Query / Zustand / Bottom-Tab+Modal navigation: **not confirmed wired**.
- **Testing** — JUnit 5 is declared for all subprojects, but coverage is essentially one class deep
  (`PipelineStateMachineTest`, 4 tests). That test earned its keep — it was confirmed **red** against the
  old mapping and green after, so it is a real regression guard. `DocumentStatusMachine`, `RetryPolicy`,
  `OcrConfidencePolicy` and every worker, adapter and handler have no test.
- **Observability** — metrics and health exist; sidecar health wiring into `notarist-observability` is
  **UNKNOWN**.

### Missing

- **The sidecars themselves.** OCR, NER, reranker, embedding: clients finished, services nonexistent.
- **Disaster recovery.** Design doc only; nothing implemented.
- **Field-level encryption.** Secrets provisioned, config declared, **no reader**.
- **`notarist-regulation`.** One file.
- **Scale-to-zero.** Blocked by in-process schedulers; needs HTTP-exposed pipeline triggers.

### Known blockers

1. **No sidecars deployed** → ingestion stalls at OCR; RAG search cannot complete. Auth and document
   CRUD work. **Top priority.**
2. **Nothing has ever run against live infrastructure.** No document has been ingested end to end. The
   audit's runtime findings stop at "context boots, fails only on external DB connect." Every
   SQL/Docker/LLM-dependent fix is **review-verified only, never executed**.
3. **No Terraform `apply` against a real GCP project.** `validate` catches schema errors, not quota,
   IAM propagation or org policy.
4. **Supabase RLS role attributes are outside Terraform** and unverifiable from here. If the app role is
   superuser or has `BYPASSRLS`, every tenant sees every other tenant, silently.

### Technical debt

| Debt | Detail |
|---|---|
| **Dead configuration** | `notarist.ai.*`, `notarist.search.*`, `notarist.observability.*`, `notarist.security.encryption.*`, `notarist.sidecar.*.timeout-ms` — all read by nothing. The hallucination-guard block is the most dangerous: it looks like a safety switch and is not. |
| **Config namespace drift** | yaml `notarist.ingestion.*` vs. code `notarist.ingest.*`. |
| **Contradictory model config** | `notarist.ai.ollama.model: notarist-llm-7b` (dead) vs. `notarist.runtime.llm.model: qwen3:14b` (live). |
| **Orphan interface** | `RagPipeline` is declared, never implemented, never injected. `RagAnswerService` does the work. |
| **Stale `database/`** | `database/postgres/flyway/` holds V1–V7 only; the live set is V1–V13 at `notarist-infra/src/main/resources/db/postgres/flyway/`. **Two divergent copies of the migration history.** `database/oracle/liquibase/` is pure legacy. |
| **Dead compose service** | `docker-compose.override.yml` still configures `minio`, which no longer exists in the base compose. |
| **Duplicate deploy path** | `deploy/cloudrun/*` overlaps Terraform. |
| **Stale code comments** | `IndoBertNerAdapter`'s javadoc still describes fetching from MinIO. |
| **Provisioned-but-unused secrets** | Terraform creates `openrouter-api-key` / `gemini-api-key` for providers that do not exist. |
| **Stale docs** | `docs/agents/*` and `docs/architecture/*` still reference Oracle/VPD/MinIO and predate TF2. Design intent, not current state. `docs/agents/00-project-rules.md` still lists the two rules deleted below. |

---

## 11. Next Sprint — recommended priorities

Justified by the repository, in order:

1. **Resolve the sidecar question — it blocks everything.** First answer the UNKNOWN in [§5](#5-sidecar-architecture):
   do PaddleOCR / IndoBERT / reranker / embedding services exist anywhere? If not, this is the sprint:
   containerise them, add them to `docker-compose.yml`, and give them a Terraform module (or a documented
   external deployment) that populates `sidecar_urls`. Nothing downstream can be validated until an OCR
   endpoint answers. *(`terraform/README.md` Blockers #1; roadmap has carried this open since 2026-07-12.)*
2. **Ingest one real document end to end.** The data flow is real and has never executed. This single
   action would validate more than any amount of further reading — every review-verified-only finding
   (RLS policies, audit persistence, pool sizing) is something only a live run exposes.
3. **Delete the dead configuration, or wire it.** Each block is a trap for the next engineer. Prioritise
   `notarist.ai.hallucination-guard` (looks like a safety switch, isn't) and the
   `notarist.ingestion`/`notarist.ingest` drift. Decide whether field-level encryption is real: either
   wire `notarist.security.encryption.*` or delete it *and* its two secrets.
4. **Collapse the duplicate migration history.** Delete `database/postgres/flyway/` (V1–V7, stale) and
   `database/oracle/liquibase/` (legacy). Two divergent copies is how a wrong one gets applied.
5. **Extend the domain test suite.** `DocumentStatusMachine`, `RetryPolicy`, `OcrConfidencePolicy` next.
   The domain is Spring-free, so tests are cheap. `PipelineStateMachineTest` already paid for itself by
   pinning F11 — a bug that survived a full audit *and* a remediation round precisely because nothing
   executed the pipeline.
6. **First real `terraform apply` against a dev project.** Blockers #4/#5 resolve only by doing it.
7. **Decide scale-to-zero.** Expose the pipeline triggers as HTTP endpoints and drive them from
   Cloud Scheduler — `modules/scheduler` and `var.jobs` are already wired for exactly this. Removes the
   `min_instances >= 1` cost floor.
8. **Delete `deploy/cloudrun/*`.** One way to deploy.
9. **Resolve the frontend TypeScript decision** before more screens are added — the design says
   TypeScript, the scaffold is `.js`. Needs an explicit decision, not a silent default.
10. **Refresh `docs/agents/` and `docs/architecture/`** to post-TF2 reality, or mark them explicitly as
    historical design intent.
11. **Decide the fate of KPI/dashboard framing.** `DashboardController` exists; the datamart it was
    designed around does not. Either scope it to `DOKUMEN_LEGAL` or drop it explicitly.

---

## GLOBAL RULES

These govern all work in this repository. They are the rules that survived the rewrite because they
still earn their place.

1. **ANALYSIS_FIRST.** State the current schema/code/contract *before* proposing a change. Never
   generate code against an assumed shape.
2. **Evidence-based changes only.** Every claim about this system must be traceable to a file. If it
   cannot be proven from the repository, say **UNKNOWN**. Do not fill gaps with plausible narrative —
   this document exists because the last one did.
3. **Never fabricate endpoints.** The complete REST surface is [§2.1](#21-the-complete-rest-surface).
   If it is not there, it does not exist.
4. **Never invent APIs.** Verify a method, port or provider exists before calling it. A config block
   naming a provider is not a provider — `application.yaml` names six LLM backends and one exists.
5. **Jangan asumsi nama kolom.** Never guess a column name. Read the migration in
   `backend/notarist-infra/src/main/resources/db/postgres/flyway/` — the live set, **not** the stale
   copy under `database/`.
6. **Jangan hallucinate mapping.** Domain ↔ JPA ↔ DTO mappings are verified against the actual
   entity/record definitions, never inferred from naming convention.
7. **Jangan skip file.** When a task spans a module or a migration set, every file in scope gets
   touched. Partial coverage is incomplete work.
8. **Explicit columns, always. `SELECT *` is forbidden** — in application code and ad-hoc SQL alike.
9. **No source modification without approval.** Architectural or cross-module changes are proposed
   first. Narrow, clear-root-cause bug fixes are the exception.
10. **Keep the repository dirty. Never commit automatically.** Changes are left in the working tree for
    human review. Committing is an explicit human decision, never a side effect of finishing a task.
11. **Generated output goes to `/generated`** (`docs`, `sql`, `backend`, `json`, `openapi`). Durable
    documentation lives in `/docs`. Disposable analysis never lands in `/docs`.
12. **Output standard.** Generated files are markdown-normalized, RAG-friendly, chunk-friendly and
    source-traceable — cite the file, migration or decision each claim came from.

### Rules deleted in this rewrite

- ~~"Semua SQL Oracle 19C compatible"~~ — there is no Oracle. All SQL is PostgreSQL.
- ~~"Semua query wajib gunakan MAX(TIME_PR)"~~ — `TIME_PR` appears in **zero** `.java`, `.sql` or `.xml`
  files. It belonged to the `BRANCHPERF` staging datamart, which no longer exists.

---

## Migration summary: Old → New

Every major architectural correction made by this rewrite.

| # | Old CLAUDE.md claimed | Current reality | Evidence |
|---|---|---|---|
| 1 | **Oracle 19C** database | **PostgreSQL** (Supabase in prod), `PostgreSQLDialect`, driver 42.7.3 | `application.yaml:25`, `libs.versions.toml` |
| 2 | Schemas **`BRANCHPERFSTAGINGDB`** / **`BRANCHPERFAPPDB`** | No such schema. Flyway V1–V13 on one database. `BRANCHPERF` appears in **zero** code/SQL files. | migrations; grep |
| 3 | **Liquibase/Oracle** migrations | **Flyway 10.12.0**, programmatic, Boot auto-config disabled. `database/oracle/liquibase/` is dead legacy. | `FlywaySearchConfig`, `application.yaml:16-21` |
| 4 | Oracle **VPD** tenant isolation | **PostgreSQL RLS** (`V9__tenant_isolation_rls.sql`). `VpdContextApplier` → `RlsContextApplier`. | migrations, git status |
| 5 | **"Enterprise Local RAG System"** — local-only inference | **Cloud Run** is the production target. Inference is provider-abstracted and remote-capable. Ollama is one provider, not the architecture. | `terraform/`, `notarist.runtime.*` |
| 6 | **"staging datamart"**, **"dashboard KPI"** as primary goals | Pivoted to legal-document intelligence. No datamart. `DashboardController` survives; its datamart does not. | `settings.gradle.kts`, `docs/agents/01-system-overview.md` |
| 7 | Object storage: **MinIO** (implied by stack) | **Google Cloud Storage**, ADC auth, no key file, V4 signed URLs via IAM `signBlob`. MinIO adapters deleted. | `Dockerfile`, `main.tf:280-287` |
| 8 | RAG stack: "**local LLM**", "reranker", "bge-m3" as flat facts | Provider **registries** with per-capability status. Reranker defaults to **`none`**. Six LLM backends named in config; **one** implemented. | `RuntimeRegistry`, [§4](#4-ai-architecture) |
| 9 | No infrastructure section | **Sprint TF2**: 10 Terraform modules, 3 environments, Cloud Build CI/CD, monitoring, logging, budget, Secret Manager, least-privilege IAM. | `terraform/` |
| 10 | No sidecar section | **Five sidecars, four undeployed** — the project's top blocker, previously undocumented in this file. | [§5](#5-sidecar-architecture), `terraform/README.md` |
| 11 | No environment variables | Documented, split required/optional/dev/prod, **with dead variables called out**. | [§7](#7-environment-variables) |
| 12 | No status section; implied a working system | Honest status: **not production ready**, never run live, never applied to real GCP. | [§10](#10-current-status) |
| 13 | Rule: "Semua SQL Oracle 19C compatible" | Deleted — no Oracle. | — |
| 14 | Rule: "Semua query wajib gunakan `MAX(TIME_PR)`" | Deleted — `TIME_PR` exists nowhere. | grep |
| 15 | "Backend: Spring Boot 3, Java 17" (vague) | Spring Boot **3.2.5**, Java **17**, Gradle **8.8**, **15** modules, one deployable. | `libs.versions.toml`, `settings.gradle.kts` |
| 16 | Frontend: "React Native" | React Native **0.86** / Expo **~57**, **JavaScript** not TypeScript, 4-screen slice. | `package.json` |

### Assumptions made in this rewrite

1. **"Sprint TF2" = the Terraform estate in `terraform/`**, identified by the explicit
   `Sprint TF2, Task 8` / `Task 9` markers in `environments/prod/main.tf:365,390`. **"Sprint 6" = the
   ingestion-pipeline data-flow work** evidenced by `generated/sprint6/rag-pipeline-trace.md` and commit
   `a5287ed`. Neither sprint has a definition document in the repository; the mapping is inferred from
   those artefacts.
2. **The working tree is treated as current reality**, not `HEAD`. The Oracle→Postgres and MinIO→GCS
   migrations are **uncommitted** (see `git status`). Reading only committed code would describe a system
   that no longer matches the files on disk. Left dirty, per rule 10.
3. **"Implemented" means the class exists, is wired and is reachable** — *not* that it has been executed
   against live infrastructure. By that stricter standard almost nothing is verified; [§10](#10-current-status)
   states this plainly rather than letting the [§4](#4-ai-architecture) table imply otherwise.
4. **Provider status is judged by grepping for implementing classes**, not by config presence. A commented
   config block is "not implemented"; a registered `@Component` with an `id()` is "implemented."
5. **`docs/` is design intent, not current state.** Where `docs/` and code disagree, code wins. This is why
   `docs/architecture/step6`'s RTO/RPO targets are reported as "design only" rather than as a DR capability.

---

## CHANGE HISTORY

| Version | Date | Description |
|---|---|---|
| v1 | 2026-05-23 | Initial project bootstrap (Oracle 19c / BRANCHPERF / KPI datamart framing) |
| **v2** | **2026-07-16** | **Full rewrite — Sprint 6.5 architecture reality sync.** Oracle → PostgreSQL, MinIO → GCS, local-only → Cloud Run. Added architecture, AI, sidecar, infrastructure, environment, workflow, structure, status and next-sprint sections. Documented the sidecar blocker and the dead-configuration set. Documentation-only: no application code, Terraform or infrastructure was modified. |
