# 02 — Architecture

## Pattern

Hexagonal Architecture + DDD + CQRS-lite, as a **modular monolith** — one deployable
(`notarist-web`), 12 Gradle modules, no microservice split. Every module (except `core`,
`infra`, `runtime`, `observability`, `web`) follows the same internal layout:

```
api/            request/response DTOs, REST controllers
application/    command/query, handler, service, port (in/out interfaces), coordinator, worker
domain/         model, event, exception, service — zero Spring dependency, enforced
infrastructure/ persistence (oracle/postgres), adapter, security, metrics, event publishing
config/         module wiring only
```

## The 12 modules

`notarist-core`, `notarist-auth`, `notarist-document`, `notarist-ingest`, `notarist-search`,
`notarist-assistant`, `notarist-regulation`, `notarist-audit`, `notarist-infra`,
`notarist-runtime`, `notarist-observability`, `notarist-web` (see `backend/settings.gradle.kts`).

- **core**: value objects (records), exceptions, `DomainEvent`, `UseCase`/`CommandUseCase`,
  `ApiResponse<T>`/`ApiMeta`/`ApiError`, `NotaristConstants`, cross-cutting domain policy
  (e.g. `domain/policy/OcrConfidencePolicy` — domain policy lives in `core`, never in `infra`).
- **infra**: concrete adapters for MinIO, Qdrant, Postgres, resilience/degraded-mode registry.
- **runtime**: real ML/LLM-facing adapters (embedding, OCR, Ollama) that implement ports owned
  by consuming modules — e.g. `QueryEmbeddingRuntimeAdapter` in `runtime` implements
  `QueryEmbeddingPort` owned by `search`. This inversion (interface in the consumer, impl in
  runtime) is the standing pattern; mirror it for new ML-backed capabilities.
- **web**: `NotaristApplication`, security config, OpenAPI, global exception handling,
  correlation-ID propagation, `application.yaml` (profile-driven: `SPRING_PROFILES_ACTIVE`,
  default `local`).

No shared DTOs cross module boundaries — cross-module communication is via domain events
(Spring `ApplicationEventPublisher`) or explicit port interfaces only.

## Data layer — hybrid, 3 systems, no mixed concerns

| Layer | System | Holds |
|---|---|---|
| Transactional | Oracle 19C (`NOTARIST`, `NOTARIST_STG`, `NOTARIST_SEC` schemas) | legal master data, transactional metadata, audit trail, user access |
| Retrieval | PostgreSQL (`notarist_rag` / `rag` schema) | OCR metadata, chunk metadata, semantic metadata, AI interaction logs, JSONB payloads, search cache, ingestion queue (PostgreSQL `SKIP LOCKED`, not RabbitMQ) |
| Vector | Qdrant (`notarist_legal_docs` collection) | embeddings (bge-m3, 1024-dim, cosine), payload filtering |

Cross-layer joins happen at the application level via `doc_id` (UUID). OCR text and chunk
text are **never** stored in Oracle. Legal master data is **never** stored in PostgreSQL.

## Ingestion pipeline

5 async stages driven by `PipelineCoordinator` + `StageWorker` subclasses in
`notarist-ingest`: OCR → chunk → NER → embed → index, each with its own worker
(`OcrWorker`, `ChunkWorker`, `NerWorker`, `EmbeddingWorker`, `IndexingWorker`), a
`PipelineStateMachine` domain service, dead-letter handling (`DeadLetterHandler`,
`DeadLetterRepository`), and retry policy (`RetryPolicyService`/`RetryPolicy`). External
sidecars: PaddleOCR (`:8081`), IndoBERT NER (`:8082`), Ollama (`:11434`), reranker (`:8083`).

## Retrieval

BM25/tsvector keyword TOP-20 (Postgres) + Qdrant semantic TOP-20 → Reciprocal Rank Fusion
(k=60) → TOP-30 merged → reranker → TOP-5 final (configurable per intent). Query-time
embedding goes through `QueryEmbeddingPort` and must degrade gracefully to zero semantic
hits on failure rather than fail the whole search — keyword retrieval always still runs
(see `SemanticRetriever` in `notarist-search`).

## AI response

Citation-first: citations are assembled **before** the LLM prompt, not extracted after.
Streaming SSE token-by-token is the default response mode; non-streaming is the fallback.
A response without a source reference is treated as invalid.

## Security

JWT RS256 with refresh rotation + Oracle VPD (`DBMS_SESSION.SET_CONTEXT`, applied per-module
via a duplicated `VpdContextApplier` — see [[06-backend-agent]]) + Qdrant payload filtering.
Full detail in [[10-security-agent]].

## Storage

MinIO, 6 buckets: `notarist-raw`, `notarist-ocr`, `notarist-processed`, `notarist-chunk`,
`notarist-export`, `notarist-backup`. Uploads go via signed URL directly to MinIO, bypassing
the backend.

Full decision log: `docs/architecture/step7_backend_implementation_architecture.md` and
`docs/architecture/step7_5_foundation_contracts.md`.
