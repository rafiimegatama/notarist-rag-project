# STEP 7 — BACKEND IMPLEMENTATION ARCHITECTURE SPECIFICATION
# NOTARIST RAG PLATFORM

**Version:** v1.0  
**Date:** 2026-05-24  
**Status:** ARCHITECTURE SPECIFICATION ONLY — Awaiting approval before implementation  
**Classification:** INTERNAL

---

## TABLE OF CONTENTS

1. [Module Structure](#1-module-structure)
2. [Package Structure](#2-package-structure)
3. [Domain Model Boundary](#3-domain-model-boundary)
4. [Interface Contract](#4-interface-contract)
5. [Event Contract](#5-event-contract)
6. [Ingestion Orchestration](#6-ingestion-orchestration)
7. [Retrieval Orchestration](#7-retrieval-orchestration)
8. [AI Orchestration](#8-ai-orchestration)
9. [Security Architecture](#9-security-architecture)
10. [Observability Architecture](#10-observability-architecture)
11. [Deployment Interaction](#11-deployment-interaction)
12. [Queue Architecture](#12-queue-architecture)
13. [Configuration Strategy](#13-configuration-strategy)
14. [API Contract Strategy](#14-api-contract-strategy)
15. [DTO Synchronization Strategy](#15-dto-synchronization-strategy)

---

## GLOBAL TECHNOLOGY CONSTRAINTS

| Constraint | Value |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| Build | Gradle Kotlin DSL |
| Architecture Pattern | Hexagonal Architecture (Ports & Adapters) |
| Domain Pattern | Domain-Driven Design (DDD) |
| CQRS Mode | CQRS-lite (Command/Query separated at use-case level) |
| Messaging | Async event-driven (PostgreSQL SKIP LOCKED queue) |
| API Design | OpenAPI-first |
| AI Response | Citation-first, Streaming-first (SSE) |
| Deployment | Modular Monolith (single deployable, 8 modules) |

---

## 1. MODULE STRUCTURE

### 1.1 Top-Level Module Layout

```
notarist-rag/
├── notarist-core/                   # Shared kernel — value objects, base types, utilities
├── notarist-auth/                   # Authentication & authorization
├── notarist-document/               # Document lifecycle management
├── notarist-ingest/                 # Ingestion pipeline orchestration
├── notarist-search/                 # Hybrid retrieval orchestration
├── notarist-assistant/              # AI assistant & RAG response
├── notarist-regulation/             # Regulasi & SOP document handling
├── notarist-audit/                  # Audit trail & compliance logging
├── notarist-web/                    # Spring Boot entry point — wires all modules
└── build.gradle.kts                 # Root build file
```

### 1.2 Deployment Topology

All 8 modules compile into **one Spring Boot JAR** (modular monolith).  
Sidecar services run as separate processes:

| Sidecar | Port | Responsibility |
|---|---|---|
| PaddleOCR | :8081 | OCR text extraction |
| IndoBERT NER | :8082 | Named entity recognition |
| Reranker (BGE) | :8083 | Cross-encoder reranking |
| Ollama | :11434 | Local LLM inference |

### 1.3 Internal Module Dependency Rules

```
notarist-web
  └── depends on → all modules (wiring only)

notarist-ingest
  └── depends on → notarist-core, notarist-document, notarist-audit

notarist-search
  └── depends on → notarist-core, notarist-document

notarist-assistant
  └── depends on → notarist-core, notarist-search, notarist-document, notarist-audit

notarist-regulation
  └── depends on → notarist-core, notarist-document

notarist-auth
  └── depends on → notarist-core, notarist-audit

notarist-audit
  └── depends on → notarist-core

notarist-core
  └── depends on → nothing (shared kernel, zero Spring dependency in domain layer)
```

**Forbidden cross-module calls:**
- `notarist-search` MUST NOT call `notarist-ingest` directly
- `notarist-assistant` MUST NOT call sidecar adapters directly (goes through `notarist-search`)
- Domain layers MUST NOT import from infrastructure packages

---

## 2. PACKAGE STRUCTURE

### 2.1 Universal Package Pattern (per module)

Every module follows the same internal package layout:

```
com.notarist.<module>/
├── api/
│   ├── rest/                        # REST controllers (thin, no business logic)
│   ├── request/                     # Inbound DTO (request body, path params)
│   ├── response/                    # Outbound DTO (API response bodies)
│   └── openapi/                     # OpenAPI annotations & config
├── application/
│   ├── command/                     # Command objects (write operations)
│   ├── query/                       # Query objects (read operations)
│   ├── handler/
│   │   ├── command/                 # Command handlers (orchestration)
│   │   └── query/                   # Query handlers (orchestration)
│   ├── service/                     # Application service (transaction boundary)
│   ├── port/
│   │   ├── in/                      # Driving ports (use-case interfaces)
│   │   └── out/                     # Driven ports (repository/adapter interfaces)
│   └── policy/                      # Business policy enforcement
├── domain/
│   ├── model/                       # Aggregate roots, entities, value objects
│   ├── event/                       # Domain events
│   ├── repository/                  # Repository interfaces (port definitions)
│   ├── service/                     # Domain services (pure domain logic)
│   └── exception/                   # Domain-specific exceptions
├── infrastructure/
│   ├── persistence/
│   │   ├── oracle/                  # Oracle adapter (NOTARIST schema)
│   │   ├── postgres/                # PostgreSQL adapter (BM25, metadata)
│   │   └── mapper/                  # ORM ↔ Domain model mappers
│   ├── qdrant/                      # Qdrant vector store adapter
│   ├── minio/                       # MinIO object storage adapter
│   ├── queue/                       # PostgreSQL queue adapter (SKIP LOCKED)
│   ├── sidecar/
│   │   ├── ocr/                     # PaddleOCR HTTP client adapter
│   │   ├── ner/                     # IndoBERT NER HTTP client adapter
│   │   ├── reranker/                # Reranker HTTP client adapter
│   │   └── llm/                     # Ollama HTTP client adapter
│   ├── cache/                       # Redis adapter (optional phase 2)
│   └── security/                    # JWT, VPD context propagation
└── config/
    ├── DataSourceConfig.java        # Oracle + PostgreSQL datasource beans
    ├── QdrantConfig.java
    ├── MinioConfig.java
    ├── QueueConfig.java
    ├── SecurityConfig.java
    ├── OpenApiConfig.java
    └── ObservabilityConfig.java
```

### 2.2 Module-Specific Package Highlights

#### `notarist-core` — Shared Kernel

```
com.notarist.core/
├── domain/
│   ├── valueobject/
│   │   ├── DocumentId              # Strong-typed document identifier
│   │   ├── ChunkId                 # Strong-typed chunk identifier
│   │   ├── PersonId                # Strong-typed person identifier
│   │   ├── CorrelationId           # Request correlation tracking
│   │   ├── TraceId                 # Distributed trace identifier
│   │   ├── NomorAkta               # Value object: nomor akta format
│   │   ├── NomorNIK                # Value object: NIK (encrypted at rest)
│   │   ├── NomorNPWP               # Value object: NPWP (encrypted at rest)
│   │   └── ClassificationLevel     # Enum: PUBLIC/INTERNAL/CONFIDENTIAL/STRICTLY_CONFIDENTIAL
│   └── event/
│       └── DomainEvent             # Base interface for all domain events
├── application/
│   └── usecase/
│       ├── UseCase<C, R>           # Generic use-case interface
│       └── CommandUseCase<C>       # Command-only use-case interface
└── api/
    └── response/
        ├── ApiResponse<T>          # Standard envelope: meta + data + error
        ├── ApiMeta                 # meta: requestId, timestamp, version
        ├── ApiError                # error: code, message, details[]
        └── PageResponse<T>         # Paginated response wrapper
```

#### `notarist-ingest` — Ingestion Pipeline

```
com.notarist.ingest/
├── domain/
│   ├── model/
│   │   ├── IngestionJob            # Aggregate root: represents one upload job
│   │   ├── JobStatus               # Enum: PENDING/OCR_QUEUE/OCR_PROCESSING/...
│   │   ├── ProcessingLock          # Value object: idempotency lock
│   │   ├── DocumentChecksum        # Value object: SHA-256 checksum
│   │   └── IngestionAuditEntry     # Domain entity: per-stage audit record
│   └── event/
│       ├── DocumentUploadedEvent
│       ├── OcrCompletedEvent
│       ├── NerCompletedEvent
│       ├── ChunkingCompletedEvent
│       ├── EmbeddingCompletedEvent
│       └── IndexingCompletedEvent
├── application/
│   ├── command/
│   │   ├── InitiateIngestionCommand
│   │   ├── ProcessOcrResultCommand
│   │   ├── ProcessNerResultCommand
│   │   ├── ProcessChunkingCommand
│   │   ├── ProcessEmbeddingCommand
│   │   └── FinalizeIndexingCommand
│   └── port/
│       ├── in/
│       │   ├── InitiateIngestionUseCase
│       │   ├── GetIngestionStatusUseCase
│       │   └── RetryIngestionUseCase
│       └── out/
│           ├── IngestionJobRepository
│           ├── DocumentStoragePort      # → MinIO adapter
│           ├── OcrServicePort           # → PaddleOCR sidecar
│           ├── NerServicePort           # → IndoBERT NER sidecar
│           ├── ChunkingPort             # → internal chunking service
│           ├── EmbeddingPort            # → Ollama / bge-m3 sidecar
│           └── VectorIndexPort          # → Qdrant adapter
└── infrastructure/
    └── queue/
        ├── IngestionQueueConsumer       # Polls PostgreSQL queue (SKIP LOCKED)
        └── IngestionQueuePublisher      # Publishes jobs to queue table
```

#### `notarist-search` — Hybrid Retrieval

```
com.notarist.search/
├── domain/
│   ├── model/
│   │   ├── SearchQuery             # Value object: query + filters + intent
│   │   ├── RetrievalResult         # Value object: ranked chunk with metadata
│   │   ├── SearchIntent            # Enum: SEMANTIC/KEYWORD/HYBRID/REGULATION
│   │   └── RankingScore            # Value object: BM25 + cosine + RRF fusion score
│   └── service/
│       ├── RrfFusionService        # RRF(k=60) domain logic (no Spring)
│       └── SearchPolicyService     # Access filter policy enforcement
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── HybridSearchUseCase
│   │   │   └── SemanticSearchUseCase
│   │   └── out/
│   │       ├── KeywordRetrievalPort        # → PostgreSQL BM25 adapter
│   │       ├── SemanticRetrievalPort       # → Qdrant adapter
│   │       ├── RerankerPort                # → Reranker sidecar adapter
│   │       └── ContextAssemblyPort         # → chunk assembler
│   └── pipeline/
│       ├── RetrievalPipeline           # Interface: execute(SearchQuery) → RetrievalResult[]
│       ├── HybridRetrievalPipeline     # Implementation: keyword + semantic + rerank
│       └── RegulationRetrievalPipeline # Implementation: hierarchy-aware regulation search
└── infrastructure/
    ├── postgres/
    │   └── BM25RetrievalAdapter        # tsvector + ts_rank query
    └── qdrant/
        └── SemanticRetrievalAdapter    # Qdrant cosine search, TOP-20
```

#### `notarist-assistant` — AI RAG Response

```
com.notarist.assistant/
├── domain/
│   ├── model/
│   │   ├── AssistantQuery          # Value object: user question + context
│   │   ├── AssistantResponse       # Aggregate: streaming response + citations
│   │   ├── Citation                # Value object: chunk ref + doc ref + confidence
│   │   ├── SourceChunk             # Value object: grounded source text
│   │   └── GroundingMetadata       # Value object: confidence + verification status
│   └── service/
│       ├── CitationValidatorService # Verifies citation traceability (no Spring)
│       └── HallucinationGuardService # Detects ungrounded claims (no Spring)
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── StreamAssistantUseCase      # SSE streaming response
│   │   │   └── NonStreamAssistantUseCase   # Batch response fallback
│   │   └── out/
│   │       ├── LlmPort                     # → Ollama adapter
│   │       ├── SearchPort                  # → notarist-search pipeline
│   │       ├── CitationRepositoryPort      # → PostgreSQL citation store
│   │       └── ResponseAuditPort           # → audit trail
│   └── pipeline/
│       ├── RagPipeline             # Interface: execute(query) → ResponseStream
│       └── CitationFirstRagPipeline # Citation assembled BEFORE LLM prompt
└── infrastructure/
    └── sidecar/
        └── OllamaLlmAdapter        # HTTP SSE streaming to Ollama :11434
```

---

## 3. DOMAIN MODEL BOUNDARY

### 3.1 Domain Layer Rules (ENFORCED)

| Rule | Enforcement |
|---|---|
| No Spring annotations in domain | `@Component`, `@Service`, `@Repository` forbidden in `domain/` package |
| No JPA/JDBC in domain | No `@Entity`, no `EntityManager`, no JDBC in domain layer |
| No HTTP in domain | No `RestTemplate`, `WebClient`, `HttpClient` in domain layer |
| No Qdrant client in domain | Qdrant access via port interface only |
| No DTO exposure | Domain model never returns DTO — mapper lives in infrastructure |

### 3.2 Aggregate Root Inventory

| Aggregate Root | Module | Key Invariants |
|---|---|---|
| `IngestionJob` | notarist-ingest | Status transition is sequential; checksum unique per tenant |
| `DocumentLegal` | notarist-document | Immutable after indexing; version increment on amendment |
| `SearchSession` | notarist-search | Session-scoped; access filter enforced at construction |
| `AssistantConversation` | notarist-assistant | Citation required before response commit; max 20 turns |
| `NotarisMaster` | notarist-document | Officer role is time-bounded (lisensi aktif/nonaktif) |
| `RegulasiMaster` | notarist-regulation | Hierarchy locked; amendment creates new version |
| `AuditTrail` | notarist-audit | Append-only; no update/delete allowed |

### 3.3 Value Object Inventory

| Value Object | Module | Description |
|---|---|---|
| `DocumentId` | core | UUID-based, typed |
| `NomorAkta` | core | Format: `<nomor>/<bulan>/<tahun>` |
| `NomorNIK` | core | 16-digit, always encrypted in memory via `EncryptedString` |
| `NomorNPWP` | core | 15-digit, always encrypted |
| `ClassificationLevel` | core | PUBLIC/INTERNAL/CONFIDENTIAL/STRICTLY_CONFIDENTIAL |
| `ChunkId` | core | UUID + document context |
| `CorrelationId` | core | UUID, propagated across all layers |
| `EmbeddingVector` | notarist-ingest | float[1024], immutable |
| `SearchQuery` | notarist-search | query text + intent + filters + top-k |
| `Citation` | notarist-assistant | chunkId + documentId + excerpt + confidence |

### 3.4 Domain Event Inventory

See Section 5 (Event Contract).

---

## 4. INTERFACE CONTRACT

### 4.1 Driving Port Contracts (Use-Case Interfaces)

All use-case interfaces live in `application/port/in/`. Controllers depend only on these interfaces.

#### `notarist-ingest`

```
interface InitiateIngestionUseCase {
    fun initiate(command: InitiateIngestionCommand): IngestionJobId
}

interface GetIngestionStatusUseCase {
    fun getStatus(jobId: IngestionJobId): IngestionJobStatus
}

interface RetryIngestionUseCase {
    fun retry(jobId: IngestionJobId, stage: PipelineStage): Unit
}
```

#### `notarist-search`

```
interface HybridSearchUseCase {
    fun search(query: SearchQuery): SearchResultPage
}

interface SemanticSearchUseCase {
    fun search(query: SearchQuery): List<RetrievalResult>
}
```

#### `notarist-assistant`

```
interface StreamAssistantUseCase {
    fun stream(query: AssistantQuery): Flow<AssistantToken>
}

interface NonStreamAssistantUseCase {
    fun answer(query: AssistantQuery): AssistantResponse
}
```

#### `notarist-auth`

```
interface AuthenticateUserUseCase {
    fun authenticate(command: AuthenticateCommand): TokenPair
}

interface RefreshTokenUseCase {
    fun refresh(command: RefreshTokenCommand): TokenPair
}

interface InvalidateSessionUseCase {
    fun invalidate(userId: UserId): Unit
}
```

#### `notarist-document`

```
interface GetDocumentUseCase {
    fun getById(id: DocumentId): DocumentLegalDetail
}

interface ListDocumentsUseCase {
    fun list(filter: DocumentFilter, page: PageRequest): Page<DocumentLegalSummary>
}
```

### 4.2 Driven Port Contracts (Adapter Interfaces)

All driven port interfaces live in `application/port/out/`. Infrastructure adapters implement these.

#### Storage Ports

```
interface DocumentStoragePort {
    fun generateUploadUrl(metadata: UploadMetadata): SignedUploadUrl
    fun verifyChecksum(objectKey: ObjectKey, expectedChecksum: Checksum): Boolean
    fun moveToProcessed(objectKey: ObjectKey): Unit
}

interface VectorIndexPort {
    fun upsertChunks(chunks: List<IndexableChunk>): Unit
    fun deleteByDocumentId(documentId: DocumentId): Unit
    fun searchSimilar(embedding: EmbeddingVector, filter: VectorFilter, topK: Int): List<VectorHit>
}
```

#### Sidecar Ports

```
interface OcrServicePort {
    fun extractText(objectKey: ObjectKey): OcrResult
}

interface NerServicePort {
    fun extractEntities(text: String, documentType: DocumentType): NerResult
}

interface RerankerPort {
    fun rerank(query: String, candidates: List<RetrievalResult>, topK: Int): List<RankedResult>
}

interface LlmPort {
    fun streamCompletion(prompt: LlmPrompt): Flow<LlmToken>
    fun complete(prompt: LlmPrompt): LlmResponse
}
```

#### Retrieval Ports

```
interface KeywordRetrievalPort {
    fun searchBM25(query: String, filter: DocumentFilter, topK: Int): List<KeywordHit>
}

interface SemanticRetrievalPort {
    fun searchCosine(embedding: EmbeddingVector, filter: VectorFilter, topK: Int): List<SemanticHit>
}
```

#### Queue Ports

```
interface IngestionQueuePort {
    fun enqueue(job: IngestionJob, stage: PipelineStage): Unit
    fun dequeueNext(stage: PipelineStage): IngestionJob?
    fun markCompleted(jobId: IngestionJobId, stage: PipelineStage): Unit
    fun markFailed(jobId: IngestionJobId, stage: PipelineStage, reason: String): Unit
    fun moveToDeadLetter(jobId: IngestionJobId, stage: PipelineStage): Unit
}
```

### 4.3 Sidecar HTTP Contract (Internal)

All sidecar calls go through adapter implementations. Contracts:

| Sidecar | Contract Type | Timeout | Retry |
|---|---|---|---|
| PaddleOCR | REST POST `/ocr/extract` | 120s | 3x exponential |
| IndoBERT NER | REST POST `/ner/extract` | 30s | 3x exponential |
| Reranker | REST POST `/rerank` | 15s | 2x linear |
| Ollama | SSE GET `/api/generate` | 120s (stream) | 1x (no retry on stream) |

---

## 5. EVENT CONTRACT

### 5.1 Event Taxonomy

All events implement `DomainEvent` base interface.

| Event Name | Publisher | Consumers | Trigger |
|---|---|---|---|
| `document.uploaded` | notarist-ingest | IngestionQueueConsumer | MinIO upload confirmed |
| `ocr.completed` | IngestionQueueConsumer | NerWorker | OCR text extracted |
| `ner.completed` | IngestionQueueConsumer | ChunkingWorker | NER entities extracted |
| `chunking.completed` | IngestionQueueConsumer | EmbeddingWorker | Document chunked |
| `embedding.completed` | IngestionQueueConsumer | IndexingWorker | Embeddings generated |
| `indexing.completed` | IngestionQueueConsumer | AuditModule, DocumentModule | Qdrant indexed |
| `ai.response.generated` | notarist-assistant | AuditModule | LLM response assembled |
| `citation.created` | notarist-assistant | AuditModule | Citation validated |
| `security.access.denied` | notarist-auth | AuditModule | Auth/authz failure |
| `document.access.logged` | notarist-document | AuditModule | Sensitive doc accessed |

### 5.2 Event Payload Schema

#### `document.uploaded`

```json
{
  "eventId": "uuid",
  "eventType": "document.uploaded",
  "timestamp": "ISO-8601",
  "correlationId": "uuid",
  "traceId": "uuid",
  "payload": {
    "jobId": "uuid",
    "documentId": "uuid",
    "objectKey": "notarist-raw/<tenantId>/<documentId>/<filename>",
    "originalFilename": "string",
    "documentType": "AKTA | REGULASI | SOP",
    "mimeType": "application/pdf",
    "checksumSha256": "hex-string",
    "fileSizeBytes": "long",
    "uploadedBy": "userId",
    "classificationLevel": "CONFIDENTIAL"
  }
}
```

#### `ocr.completed`

```json
{
  "eventId": "uuid",
  "eventType": "ocr.completed",
  "timestamp": "ISO-8601",
  "correlationId": "uuid",
  "payload": {
    "jobId": "uuid",
    "documentId": "uuid",
    "ocrObjectKey": "notarist-ocr/<documentId>/result.json",
    "pageCount": "int",
    "extractedTextLength": "int",
    "ocrEngine": "PaddleOCR-v4",
    "confidenceAvg": "float",
    "processingMs": "long"
  }
}
```

#### `ner.completed`

```json
{
  "eventId": "uuid",
  "eventType": "ner.completed",
  "timestamp": "ISO-8601",
  "correlationId": "uuid",
  "payload": {
    "jobId": "uuid",
    "documentId": "uuid",
    "entitiesExtracted": {
      "PERSON": "int",
      "NOTARIS": "int",
      "PPAT": "int",
      "NOMOR_AKTA": "int",
      "NIK": "int",
      "TANGGAL": "int",
      "ALAMAT": "int"
    },
    "nerObjectKey": "notarist-processed/<documentId>/ner.json",
    "processingMs": "long"
  }
}
```

#### `chunking.completed`

```json
{
  "eventId": "uuid",
  "eventType": "chunking.completed",
  "timestamp": "ISO-8601",
  "correlationId": "uuid",
  "payload": {
    "jobId": "uuid",
    "documentId": "uuid",
    "totalChunks": "int",
    "chunkStrategy": "KLAUSUL_BASED | HIERARCHY_BASED | STEP_BASED",
    "avgTokensPerChunk": "int",
    "chunkObjectKey": "notarist-chunk/<documentId>/chunks.jsonl",
    "processingMs": "long"
  }
}
```

#### `embedding.completed`

```json
{
  "eventId": "uuid",
  "eventType": "embedding.completed",
  "timestamp": "ISO-8601",
  "correlationId": "uuid",
  "payload": {
    "jobId": "uuid",
    "documentId": "uuid",
    "embeddingModel": "bge-m3",
    "embeddingDimension": 1024,
    "totalVectors": "int",
    "processingMs": "long"
  }
}
```

#### `indexing.completed`

```json
{
  "eventId": "uuid",
  "eventType": "indexing.completed",
  "timestamp": "ISO-8601",
  "correlationId": "uuid",
  "payload": {
    "jobId": "uuid",
    "documentId": "uuid",
    "qdrantCollection": "notarist_chunks",
    "vectorsIndexed": "int",
    "postgresBm25Updated": true,
    "processingDurationMs": "long"
  }
}
```

#### `ai.response.generated`

```json
{
  "eventId": "uuid",
  "eventType": "ai.response.generated",
  "timestamp": "ISO-8601",
  "correlationId": "uuid",
  "payload": {
    "sessionId": "uuid",
    "userId": "uuid",
    "queryHash": "sha256",
    "citationCount": "int",
    "tokensInput": "int",
    "tokensOutput": "int",
    "modelId": "string",
    "groundingScore": "float",
    "hallucinationFlagRaised": false,
    "streamDurationMs": "long"
  }
}
```

### 5.3 Event Retry Policy

| Event | Max Attempts | Backoff | Dead Letter After |
|---|---|---|---|
| `document.uploaded` | 5 | Exponential (1s, 2s, 4s, 8s, 16s) | 5th failure |
| `ocr.completed` | 3 | Exponential (5s, 15s, 45s) | 3rd failure |
| `ner.completed` | 3 | Linear (10s, 10s, 10s) | 3rd failure |
| `chunking.completed` | 3 | Linear (5s, 5s, 5s) | 3rd failure |
| `embedding.completed` | 3 | Exponential (10s, 30s, 90s) | 3rd failure |
| `indexing.completed` | 3 | Exponential (5s, 15s, 45s) | 3rd failure |

### 5.4 Event Replay Strategy

- Dead-letter records stored in `NOTARIST_STG.INGESTION_DLQ`
- Replay triggered manually via Admin API: `POST /api/v1/admin/ingestion/replay/{jobId}`
- Replay uses idempotency key: `documentId + stage + attemptNumber`
- Replay creates new job row; original row preserved for audit

---

## 6. INGESTION ORCHESTRATION

### 6.1 Full Pipeline Flow

```
[Client / Frontend]
        │
        │  1. Request signed upload URL
        ▼
[notarist-ingest API]
        │  POST /api/v1/ingest/upload-url
        │  → returns: { signedUrl, objectKey, jobId }
        │
        │  2. Client uploads directly to MinIO
        ▼
[MinIO: notarist-raw bucket]
        │
        │  3. Client confirms upload
        ▼
[notarist-ingest API]
        │  POST /api/v1/ingest/confirm/{jobId}
        │  → verify checksum
        │  → duplicate detection
        │  → acquire processing lock
        │  → emit: document.uploaded → queue
        │
        ▼
[INGESTION_QUEUE table — PostgreSQL SKIP LOCKED]
        │
        │  Stage: OCR_QUEUE
        ▼
[OcrWorker — Scheduled poller]
        │  → acquire lock
        │  → call PaddleOCR :8081
        │  → save OcrResult to notarist-ocr bucket
        │  → emit: ocr.completed → queue
        │
        │  Stage: NER_QUEUE
        ▼
[NerWorker — Scheduled poller]
        │  → call IndoBERT NER :8082
        │  → PII redaction before storage
        │  → save NerResult to notarist-processed bucket
        │  → emit: ner.completed → queue
        │
        │  Stage: CHUNKING_QUEUE
        ▼
[ChunkingWorker — Scheduled poller]
        │  → select strategy by document type:
        │    AKTA       → klausul-based (400-800 tokens, 15% overlap)
        │    REGULASI   → hierarchy-based (300-600 tokens, 0% overlap)
        │    SOP        → step-based (200-500 tokens, 10% overlap)
        │  → save chunks to notarist-chunk bucket
        │  → emit: chunking.completed → queue
        │
        │  Stage: EMBEDDING_QUEUE
        ▼
[EmbeddingWorker — Scheduled poller]
        │  → call bge-m3 via Ollama :11434
        │  → generate 1024-dim float[] per chunk
        │  → emit: embedding.completed → queue
        │
        │  Stage: INDEXING_QUEUE
        ▼
[IndexingWorker — Scheduled poller]
        │  → upsert vectors to Qdrant (notarist_chunks collection)
        │  → upsert tsvector to PostgreSQL (CHUNK_BM25 table)
        │  → update DOKUMEN_LEGAL.STATUS = INDEXED in Oracle
        │  → emit: indexing.completed
        │
        ▼
[notarist-audit]
        │  → write AUDIT_TRAIL record
        │  → notify document module: status update
```

### 6.2 Idempotency Strategy

| Mechanism | Implementation |
|---|---|
| Duplicate detection | SHA-256 checksum lookup in `NOTARIST_STG.DOCUMENT_CHECKSUM_LOG` |
| Processing lock | `INGESTION_LOCK` table row with `FOR UPDATE SKIP LOCKED` |
| Stage completion marker | `INGESTION_JOB_STAGE.COMPLETED_AT NOT NULL` check |
| Vector upsert | Qdrant `upsert` with `documentId + chunkIndex` as point ID |
| BM25 upsert | PostgreSQL `INSERT ... ON CONFLICT DO UPDATE` |

### 6.3 Partial Failure Recovery

| Failure Point | Recovery Strategy |
|---|---|
| OCR timeout | Retry up to 3x; move to DLQ on 4th failure |
| NER failure | Fallback to rule-based NER; mark as PARTIAL_NER |
| Chunking failure | Alert admin; job paused, manual review required |
| Embedding failure | Retry 3x; if Ollama down, exponential backoff |
| Indexing partial | Qdrant indexed but PostgreSQL failed → replay indexing stage only |

---

## 7. RETRIEVAL ORCHESTRATION

### 7.1 Hybrid Retrieval Pipeline

```
[Search Request]
        │
        │  SearchQuery {
        │    queryText: string,
        │    intent: HYBRID | SEMANTIC | KEYWORD | REGULATION,
        │    filters: { documentType, classificationLevel, dateRange, notarisId },
        │    topK: int (default 5),
        │    userId: uuid
        │  }
        │
        ▼
[RetrievalPipeline Interface]
        │
        ├──── Parallel Retrieval ─────────────────────────────┐
        │                                                      │
        ▼                                                      ▼
[KeywordRetrievalPort]                          [SemanticRetrievalPort]
 PostgreSQL BM25                                 Qdrant cosine similarity
 tsvector + ts_rank                              bge-m3 1024-dim
 TOP-20 results                                  TOP-20 results
 + access filter (SQL WHERE)                     + payload filter (Qdrant filter)
        │                                                      │
        └─────────────── Merge via RRF ───────────────────────┘
                                │
                                │  RRF(k=60)
                                │  → TOP-30 merged candidates
                                │
                                ▼
                    [RerankerPort]
                     BGE reranker :8083
                     Cross-encoder scoring
                     → TOP-5 final results (configurable)
                                │
                                ▼
                    [ContextAssemblyPort]
                     Assembles chunk text + metadata
                     Applies field masking per role
                     Returns: List<RetrievalResult>
```

### 7.2 Retrieval Pipeline Variants

| Pipeline | Use Case | Strategy |
|---|---|---|
| `HybridRetrievalPipeline` | General search | BM25 + Semantic + RRF + Reranker |
| `RegulationRetrievalPipeline` | Regulasi/Pasal search | Hierarchy-aware + Semantic only + Citation assembly |
| `SopRetrievalPipeline` | SOP workflow search | Step-based retrieval + sequential ordering |

### 7.3 Access Control in Retrieval

- **PostgreSQL BM25:** SQL WHERE clause enforces `classification_level` and `tenant_id`
- **Qdrant semantic:** Payload filter enforces `classification_level` and `accessible_roles`
- **Field masking:** `ContextAssemblyPort` applies S2-level masking (ALAMAT, NO_TELP, EMAIL) based on caller's role before returning chunks
- **Regulation access:** STRICTLY_CONFIDENTIAL pasal not returned to STAFF role

### 7.4 Retrieval Result Contract

```json
{
  "chunkId": "uuid",
  "documentId": "uuid",
  "documentTitle": "string",
  "documentType": "AKTA | REGULASI | SOP",
  "chunkIndex": "int",
  "chunkText": "string (masked if required)",
  "metadata": {
    "nomorAkta": "string",
    "tanggalAkta": "date",
    "notarisId": "uuid",
    "classificationLevel": "CONFIDENTIAL"
  },
  "scores": {
    "bm25": "float",
    "cosine": "float",
    "rrfScore": "float",
    "rerankerScore": "float"
  },
  "sourceObjectKey": "notarist-chunk/<documentId>/chunks.jsonl"
}
```

---

## 8. AI ORCHESTRATION

### 8.1 Citation-First RAG Pipeline

```
[AssistantQuery]
        │
        ▼
[CitationFirstRagPipeline]
        │
        │  Step 1: Retrieval
        │  → call HybridSearchUseCase
        │  → retrieve TOP-5 ranked chunks
        │
        │  Step 2: Citation Assembly (BEFORE LLM prompt)
        │  → validate each chunk has: chunkId, documentId, excerpt
        │  → build citation list
        │  → if citation count = 0 → ABORT with "no_source" response
        │
        │  Step 3: Prompt Construction
        │  → system prompt: citation-enforcing instruction
        │  → context: assembled chunk texts with [CITATION-N] markers
        │  → user query appended
        │
        │  Step 4: LLM Streaming (SSE)
        │  → call LlmPort.streamCompletion()
        │  → stream tokens via Server-Sent Events
        │  → each token emitted to client immediately
        │
        │  Step 5: Post-Response Validation
        │  → HallucinationGuardService.check(response, citations)
        │  → CitationValidatorService.verify(citations, chunks)
        │  → if hallucination flag → append warning to response
        │
        │  Step 6: Audit
        │  → emit: ai.response.generated
        │  → emit: citation.created per citation
        │  → log: token count, grounding score, latency
        │
        ▼
[SSE Stream to Client]
```

### 8.2 Hallucination Guard Layer

| Check | Method | Action on Fail |
|---|---|---|
| Citation presence | Response contains `[CITATION-N]` markers | Append `[WARNING: response ungrounded]` |
| Citation traceable | Citation chunkId exists in Qdrant | Flag citation as unverified |
| Factual grounding | Response claims verifiable in chunk text | Log hallucination event; reduce confidence score |
| Source coverage | At least 1 chunk supports main claim | Flag response as low-confidence |

### 8.3 Response Grounding Metadata

Every `AssistantResponse` includes:

```json
{
  "response": {
    "text": "...[CITATION-1]...[CITATION-2]...",
    "citations": [
      {
        "citationId": "uuid",
        "chunkId": "uuid",
        "documentId": "uuid",
        "documentTitle": "Akta APHT No. 123/V/2024",
        "excerpt": "...klausul jaminan...",
        "confidence": 0.92,
        "verified": true
      }
    ],
    "grounding": {
      "groundingScore": 0.87,
      "hallucinationFlagRaised": false,
      "citationCoverage": "FULL",
      "warningMessages": []
    },
    "meta": {
      "model": "notarist-llm-7b",
      "tokensInput": 1240,
      "tokensOutput": 387,
      "latencyMs": 4200,
      "streamMode": "SSE"
    }
  }
}
```

### 8.4 Non-Streaming Fallback

- Triggered if client does not accept `text/event-stream`
- Collects all tokens in buffer, returns assembled response
- Same citation validation and audit applies
- Endpoint: `POST /api/v1/assistant/query` (non-stream)
- Stream endpoint: `GET /api/v1/assistant/stream?queryId={id}` (SSE)

---

## 9. SECURITY ARCHITECTURE

### 9.1 Authentication Flow

```
[Client]
    │
    │  POST /api/v1/auth/login
    │  { username, password }
    │
    ▼
[notarist-auth — AuthenticateUserUseCase]
    │  → verify credentials vs Oracle NOTARIST.NOTARIST_USER
    │  → load roles from NOTARIST.USER_ROLE_MAP
    │  → generate JWT RS256 (access token: 15min)
    │  → generate refresh token (opaque UUID: 7 days)
    │  → store refresh token hash in PostgreSQL SESSION_TOKEN table
    │  → return: { accessToken, refreshToken, expiresIn }
```

### 9.2 JWT Contract

**Header:** RS256  
**Claims:**

```json
{
  "sub": "userId",
  "roles": ["NOTARIS", "STAFF"],
  "tenantId": "uuid",
  "classificationClearance": "CONFIDENTIAL",
  "sessionId": "uuid",
  "correlationId": "uuid",
  "iat": "epoch",
  "exp": "epoch"
}
```

### 9.3 Multi-Layer RBAC

| Layer | Mechanism | Scope |
|---|---|---|
| Spring Security | JWT role check | API endpoint access |
| Oracle VPD | Row-level security policy | SQL result filtering |
| Qdrant filter | Payload metadata filter | Vector search result filtering |
| Field masking | `ContextAssemblyPort` | PII field masking in chunk text |
| Audit logging | `notarist-audit` module | All access recorded |

### 9.4 Oracle VPD Context Propagation

Every Oracle connection must set VPD context before query execution:

```
DBMS_SESSION.SET_CONTEXT('NOTARIST_CTX', 'USER_ID', :userId);
DBMS_SESSION.SET_CONTEXT('NOTARIST_CTX', 'TENANT_ID', :tenantId);
DBMS_SESSION.SET_CONTEXT('NOTARIST_CTX', 'ROLE', :primaryRole);
DBMS_SESSION.SET_CONTEXT('NOTARIST_CTX', 'CLEARANCE', :classificationClearance);
```

VPD policy enforces:
- NOTARIST.DOKUMEN_LEGAL → filter by TENANT_ID
- NOTARIST_SEC.AUDIT_TRAIL → filter by TENANT_ID (read-only for PIMPINAN)
- NOTARIST.NOTARIS_MASTER → filter by TENANT_ID

### 9.5 Refresh Token Rotation

```
POST /api/v1/auth/refresh
→ validate refresh token against SESSION_TOKEN table
→ invalidate old refresh token (single-use)
→ issue new access token + new refresh token
→ update SESSION_TOKEN record
```

### 9.6 Session Invalidation

```
POST /api/v1/auth/logout
→ delete SESSION_TOKEN record for current session
→ add JWT jti to Redis deny-list (TTL = remaining access token validity)
→ emit: security.session.invalidated event
```

### 9.7 Encryption Classification

| Data | Classification | Encryption |
|---|---|---|
| NIK | S1 | Oracle TDE + AES-256 app-level |
| NPWP | S1 | Oracle TDE + AES-256 app-level |
| NILAI_TRANSAKSI | S1 | Oracle TDE + AES-256 app-level |
| NOMOR_REKENING | S1 | Oracle TDE + AES-256 app-level |
| ALAMAT | S2 | App-level masking per role |
| NO_TELP | S2 | App-level masking per role |
| EMAIL | S2 | App-level masking per role |
| Chunk text (Qdrant/PostgreSQL) | S2 | PII redacted before storage |

### 9.8 Request Correlation & Security Event Logging

- Every request receives `X-Correlation-ID` header (generated if absent)
- Every request receives `X-Trace-ID` header (propagated to all sidecar calls)
- Security events logged to `NOTARIST_SEC.SECURITY_EVENT_LOG`:
  - Auth failure
  - Access denied
  - Sensitive field accessed
  - Hallucination flag raised
  - Session invalidated

---

## 10. OBSERVABILITY ARCHITECTURE

### 10.1 Structured Logging Contract

All log entries use JSON format with mandatory fields:

```json
{
  "timestamp": "ISO-8601",
  "level": "INFO | WARN | ERROR",
  "service": "notarist-rag",
  "module": "notarist-ingest",
  "correlationId": "uuid",
  "traceId": "uuid",
  "userId": "uuid (if authenticated)",
  "event": "event-slug",
  "durationMs": "long (if applicable)",
  "message": "human-readable"
}
```

### 10.2 OpenTelemetry-Ready Design

- All adapters and use-case handlers accept `CorrelationId` and `TraceId` as explicit parameters
- Infrastructure adapters propagate trace headers to sidecar HTTP calls
- No direct OTel SDK dependency in domain layer — tracing context passed as value objects
- OTel SDK imported only in `notarist-web` config and infrastructure adapters
- Spans defined for: OCR call, NER call, Qdrant search, BM25 search, LLM completion

### 10.3 Metrics Registry

| Metric | Type | Labels | Description |
|---|---|---|---|
| `ingestion.job.total` | Counter | stage, status | Jobs processed per stage |
| `ingestion.job.duration_ms` | Histogram | stage | Processing time per stage |
| `ocr.pages.processed` | Counter | — | Total OCR pages |
| `ocr.confidence.avg` | Gauge | — | Average OCR confidence |
| `ner.entities.extracted` | Counter | entity_type | Total entities extracted |
| `retrieval.bm25.duration_ms` | Histogram | — | BM25 query latency |
| `retrieval.qdrant.duration_ms` | Histogram | — | Qdrant query latency |
| `retrieval.reranker.duration_ms` | Histogram | — | Reranker latency |
| `retrieval.results.count` | Histogram | pipeline | Results per query |
| `ai.tokens.input` | Counter | model | LLM input tokens |
| `ai.tokens.output` | Counter | model | LLM output tokens |
| `ai.latency.ttft_ms` | Histogram | model | Time to first token |
| `ai.latency.total_ms` | Histogram | model | Total completion time |
| `ai.hallucination.flags` | Counter | — | Hallucination flags raised |
| `ai.citation.count` | Histogram | — | Citations per response |
| `security.auth.failure` | Counter | reason | Auth failures |
| `api.request.duration_ms` | Histogram | path, method, status | API latency |

### 10.4 Health Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Overall health (Spring Boot Actuator) |
| `GET /actuator/health/oracle` | Oracle DB connectivity |
| `GET /actuator/health/postgres` | PostgreSQL connectivity |
| `GET /actuator/health/qdrant` | Qdrant connectivity |
| `GET /actuator/health/minio` | MinIO connectivity |
| `GET /actuator/health/ocr` | PaddleOCR sidecar |
| `GET /actuator/health/ner` | IndoBERT NER sidecar |
| `GET /actuator/health/llm` | Ollama connectivity |
| `GET /actuator/metrics` | Prometheus-format metrics |

---

## 11. DEPLOYMENT INTERACTION

### 11.1 Process Topology

```
┌─────────────────────────────────────────────────────────────┐
│  Docker Compose (local/dev/staging)                         │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  notarist-app  :8080                                 │  │
│  │  (Spring Boot — all 8 modules)                       │  │
│  └──────────────────────────────────────────────────────┘  │
│           │           │           │           │             │
│           ▼           ▼           ▼           ▼             │
│  ┌──────────┐  ┌──────────┐  ┌────────┐  ┌──────────┐     │
│  │ Oracle   │  │ PostgreSQL│  │ Qdrant │  │  MinIO   │     │
│  │ 19C:1521 │  │ :5432     │  │ :6333  │  │  :9000   │     │
│  └──────────┘  └──────────┘  └────────┘  └──────────┘     │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌────────┐  ┌──────────┐     │
│  │PaddleOCR │  │IndoBERT  │  │Reranker│  │  Ollama  │     │
│  │  :8081   │  │  :8082   │  │  :8083 │  │  :11434  │     │
│  └──────────┘  └──────────┘  └────────┘  └──────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### 11.2 Service Communication Matrix

| From | To | Protocol | Auth |
|---|---|---|---|
| notarist-app | Oracle 19C | JDBC (ojdbc11) | DB user + VPD |
| notarist-app | PostgreSQL | JDBC (pg driver) | DB user |
| notarist-app | Qdrant | HTTP REST | API key |
| notarist-app | MinIO | S3 SDK | AccessKey + SecretKey |
| notarist-app | PaddleOCR | HTTP REST | Internal (no auth on LAN) |
| notarist-app | IndoBERT NER | HTTP REST | Internal |
| notarist-app | Reranker | HTTP REST | Internal |
| notarist-app | Ollama | HTTP + SSE | Internal |
| Frontend (RN) | notarist-app | HTTPS + SSE | JWT Bearer |
| Frontend (RN) | MinIO | HTTPS (signed URL) | Pre-signed URL |

### 11.3 Data Flow Responsibility Matrix

| Responsibility | Owner |
|---|---|
| Upload URL generation | `notarist-ingest` API |
| Direct file upload | Client → MinIO (bypass backend) |
| Upload confirmation & checksum | `notarist-ingest` API |
| Ingestion pipeline execution | `notarist-ingest` workers (scheduled) |
| OCR, NER execution | Sidecar processes (PaddleOCR, IndoBERT) |
| Vector storage | `notarist-ingest` IndexingWorker → Qdrant |
| BM25 index update | `notarist-ingest` IndexingWorker → PostgreSQL |
| Oracle status update | `notarist-ingest` IndexingWorker → Oracle |
| Retrieval execution | `notarist-search` pipeline |
| LLM prompt + streaming | `notarist-assistant` pipeline |
| Audit recording | `notarist-audit` module (event-driven) |

---

## 12. QUEUE ARCHITECTURE

### 12.1 Queue Implementation

**Technology:** PostgreSQL `SKIP LOCKED` queue (no RabbitMQ for initial phase)

**Queue Table:** `NOTARIST_STG.INGESTION_QUEUE`

```sql
-- Conceptual schema (DDL to be generated separately)
INGESTION_QUEUE (
    QUEUE_ID        UUID PRIMARY KEY,
    JOB_ID          UUID NOT NULL,
    DOCUMENT_ID     UUID NOT NULL,
    STAGE           VARCHAR(30) NOT NULL,     -- OCR/NER/CHUNKING/EMBEDDING/INDEXING
    STATUS          VARCHAR(20) NOT NULL,     -- PENDING/PROCESSING/COMPLETED/FAILED/DLQ
    PAYLOAD         JSONB NOT NULL,
    ATTEMPT_COUNT   INTEGER DEFAULT 0,
    NEXT_ATTEMPT_AT TIMESTAMP,
    LOCKED_BY       VARCHAR(50),              -- worker instance ID
    LOCKED_AT       TIMESTAMP,
    COMPLETED_AT    TIMESTAMP,
    FAILED_REASON   TEXT,
    CREATED_AT      TIMESTAMP NOT NULL,
    UPDATED_AT      TIMESTAMP NOT NULL
)
```

### 12.2 Worker Polling Strategy

```
-- Poll pattern (conceptual — implemented in IngestionQueueConsumer)
SELECT ... FROM INGESTION_QUEUE
WHERE STAGE = :stage
  AND STATUS = 'PENDING'
  AND (NEXT_ATTEMPT_AT IS NULL OR NEXT_ATTEMPT_AT <= NOW())
ORDER BY CREATED_AT ASC
LIMIT 1
FOR UPDATE SKIP LOCKED
```

### 12.3 Queue Stage Definitions

| Stage Name | Trigger | Worker | Output |
|---|---|---|---|
| `UPLOAD_CONFIRMED` | Client confirmation | — | Enqueue OCR stage |
| `OCR_QUEUE` | UPLOAD_CONFIRMED | OcrWorker | OcrResult → MinIO |
| `NER_QUEUE` | ocr.completed | NerWorker | NerResult → MinIO |
| `CHUNKING_QUEUE` | ner.completed | ChunkingWorker | Chunks → MinIO |
| `EMBEDDING_QUEUE` | chunking.completed | EmbeddingWorker | Embeddings in memory |
| `INDEXING_QUEUE` | embedding.completed | IndexingWorker | Qdrant + PostgreSQL |

### 12.4 Dead Letter Queue

**Table:** `NOTARIST_STG.INGESTION_DLQ`

| Column | Description |
|---|---|
| `DLQ_ID` | UUID |
| `JOB_ID` | Reference to original job |
| `STAGE` | Stage that failed |
| `FAILED_AT` | Timestamp |
| `ATTEMPT_COUNT` | How many times retried |
| `LAST_ERROR` | Error message + stack trace fragment |
| `PAYLOAD_SNAPSHOT` | JSONB payload at time of failure |
| `RESOLVED` | Boolean (manually marked) |
| `RESOLVED_BY` | Admin user ID |
| `RESOLVED_AT` | Timestamp |

### 12.5 Queue Monitoring

- Metric: `queue.depth` per stage (Gauge)
- Metric: `queue.dlq.count` (Counter)
- Alert: DLQ count > 0 → operator notification
- Alert: queue depth > 100 for any stage → scale warning

---

## 13. CONFIGURATION STRATEGY

### 13.1 Profile Strategy

| Profile | Activation | Purpose |
|---|---|---|
| `local` | default | Developer laptop, Docker Compose local |
| `dev` | `SPRING_PROFILES_ACTIVE=dev` | Shared dev server |
| `staging` | `SPRING_PROFILES_ACTIVE=staging` | Pre-prod validation |
| `production` | `SPRING_PROFILES_ACTIVE=production` | Live environment |

### 13.2 Configuration File Layout

```
notarist-web/src/main/resources/
├── application.yaml                 # Shared defaults (non-sensitive)
├── application-local.yaml           # Local overrides (in .gitignore)
├── application-dev.yaml             # Dev environment config
├── application-staging.yaml         # Staging config
└── application-production.yaml      # Production config (no secrets — externalized)
```

### 13.3 Secret Externalization Strategy

| Secret | Local | Dev/Staging/Prod |
|---|---|---|
| Oracle password | `application-local.yaml` (gitignored) | Environment variable: `ORACLE_PASSWORD` |
| PostgreSQL password | `application-local.yaml` | `POSTGRES_PASSWORD` |
| Qdrant API key | `application-local.yaml` | `QDRANT_API_KEY` |
| MinIO secret key | `application-local.yaml` | `MINIO_SECRET_KEY` |
| JWT RS256 private key | Local keystore | External secrets manager / K8s secret |
| JWT RS256 public key | Local keystore | External secrets manager / K8s secret |
| Oracle encryption key (S1) | Local keystore | HSM / secrets manager |

### 13.4 Configuration Property Groups

```yaml
# application.yaml (structure only — not implementation)

notarist:
  security:
    jwt:
      issuer: notarist-rag
      access-token-ttl-minutes: 15
      refresh-token-ttl-days: 7
      private-key-path: ${JWT_PRIVATE_KEY_PATH}
      public-key-path: ${JWT_PUBLIC_KEY_PATH}

  ingestion:
    queue:
      poll-interval-ms: 2000
      max-concurrent-workers: 3
      lock-timeout-ms: 300000

  search:
    qdrant:
      collection: notarist_chunks
      top-k-semantic: 20
    bm25:
      top-k-keyword: 20
    rrf:
      k: 60
    reranker:
      top-k-final: 5

  ai:
    ollama:
      model: ${OLLAMA_MODEL:notarist-llm-7b}
      stream: true
      max-tokens: 2048
    hallucination-guard:
      enabled: true
      min-citation-count: 1

  observability:
    correlation-id-header: X-Correlation-ID
    trace-id-header: X-Trace-ID

  sidecar:
    ocr:
      base-url: ${OCR_BASE_URL:http://localhost:8081}
      timeout-ms: 120000
    ner:
      base-url: ${NER_BASE_URL:http://localhost:8082}
      timeout-ms: 30000
    reranker:
      base-url: ${RERANKER_BASE_URL:http://localhost:8083}
      timeout-ms: 15000
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
```

### 13.5 Database Migration Strategy

| Database | Tool | Location |
|---|---|---|
| Oracle 19C | Liquibase | `notarist-web/src/main/resources/db/oracle/changelog/` |
| PostgreSQL | Flyway | `notarist-web/src/main/resources/db/postgres/migration/` |

Migration naming:
- Oracle Liquibase: `db.changelog-master.yaml` → `oracle-001-create-notarist-schema.yaml`
- PostgreSQL Flyway: `V001__create_queue_tables.sql`, `V002__create_bm25_tables.sql`

---

## 14. API CONTRACT STRATEGY

### 14.1 API Versioning

All endpoints versioned under `/api/v1/`.  
Breaking changes → new version: `/api/v2/`.  
Old version deprecation: minimum 2 sprint grace period.

### 14.2 Standard Response Envelope

All API responses use:

```json
{
  "meta": {
    "requestId": "uuid",
    "timestamp": "ISO-8601",
    "apiVersion": "v1",
    "processingMs": "long"
  },
  "data": { },
  "error": null
}
```

Error response:

```json
{
  "meta": {
    "requestId": "uuid",
    "timestamp": "ISO-8601"
  },
  "data": null,
  "error": {
    "code": "ERROR_CODE_SLUG",
    "message": "Human-readable message",
    "details": [
      { "field": "fieldName", "issue": "description" }
    ]
  }
}
```

### 14.3 Pagination Contract

```json
{
  "meta": { "requestId": "..." },
  "data": {
    "items": [],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 150,
      "totalPages": 8,
      "hasNext": true,
      "hasPrevious": false
    }
  }
}
```

Query params: `?page=0&size=20&sort=createdAt,desc`

### 14.4 Async Job Response Pattern

For long-running operations (ingestion):

```
POST /api/v1/ingest/confirm/{jobId}
→ 202 Accepted
{
  "meta": { "requestId": "..." },
  "data": {
    "jobId": "uuid",
    "status": "OCR_QUEUE",
    "statusUrl": "/api/v1/ingest/status/{jobId}",
    "estimatedCompletionMs": 30000
  }
}
```

Status polling:

```
GET /api/v1/ingest/status/{jobId}
→ 200 OK
{
  "data": {
    "jobId": "uuid",
    "currentStage": "EMBEDDING_QUEUE",
    "progress": { "completed": 3, "total": 5 },
    "status": "PROCESSING"
  }
}
```

### 14.5 SSE Streaming Contract

```
GET /api/v1/assistant/stream
Headers:
  Authorization: Bearer <accessToken>
  X-Query-Id: <queryId>
  Accept: text/event-stream

Response: text/event-stream
event: token
data: {"token": "Berdasarkan", "index": 0}

event: token
data: {"token": " akta", "index": 1}

event: citation
data: {"citationId": "uuid", "chunkId": "uuid", "documentId": "uuid", "excerpt": "..."}

event: complete
data: {"totalTokens": 387, "citations": [...], "grounding": {...}}

event: error
data: {"code": "NO_SOURCE_FOUND", "message": "Tidak ditemukan dokumen relevan"}
```

### 14.6 OpenAPI-First Workflow

1. Write OpenAPI 3.1 YAML spec first (`/generated/openapi/`)
2. Use `openapi-generator-gradle-plugin` to generate:
   - Controller interfaces (implemented by REST controllers)
   - Request/response DTOs
3. Controllers implement generated interface — no manual DTO creation
4. Spec is source of truth; implementation must not deviate

### 14.7 API Module Inventory

| Module | Base Path | Key Endpoints |
|---|---|---|
| notarist-auth | `/api/v1/auth` | `POST /login`, `POST /refresh`, `POST /logout` |
| notarist-document | `/api/v1/documents` | `GET /{id}`, `GET /` (list), `GET /{id}/chunks` |
| notarist-ingest | `/api/v1/ingest` | `POST /upload-url`, `POST /confirm/{jobId}`, `GET /status/{jobId}` |
| notarist-search | `/api/v1/search` | `POST /hybrid`, `POST /semantic`, `POST /regulation` |
| notarist-assistant | `/api/v1/assistant` | `POST /query`, `GET /stream`, `GET /sessions/{id}` |
| notarist-regulation | `/api/v1/regulations` | `GET /`, `GET /{id}`, `GET /{id}/pasal` |
| notarist-audit | `/api/v1/audit` | `GET /trail` (PIMPINAN/ADMIN only) |
| notarist-web | `/api/v1/admin` | `POST /ingestion/replay/{jobId}`, `GET /queue/status` |

---

## 15. DTO SYNCHRONIZATION STRATEGY

### 15.1 DTO Layer Separation

| DTO Type | Location | Purpose |
|---|---|---|
| Request DTO | `api/request/` | Inbound from client — validated with Bean Validation |
| Response DTO | `api/response/` | Outbound to client — never expose domain internals |
| Command | `application/command/` | Drives use-case execution — internal to module |
| Query | `application/query/` | Parameterizes read operations — internal |
| Domain Model | `domain/model/` | Pure business objects — never crosses module boundary |
| Persistence Model | `infrastructure/persistence/` | ORM entities — never exposed to API layer |
| Sidecar Request/Response | `infrastructure/sidecar/` | HTTP contracts to sidecar services |

### 15.2 DTO Flow Rules

```
[Client Request]
    │
    ▼  (JSON deserialization)
[Request DTO]  ← Bean Validation (@NotNull, @Size, etc.)
    │
    ▼  (Controller maps to Command)
[Command / Query]  ← No validation annotations
    │
    ▼  (Command Handler calls Use Case)
[Use Case Interface]
    │
    ▼  (Application Service executes)
[Domain Model]  ← Pure business objects, no framework annotations
    │
    ▼  (Persistence layer maps from domain)
[Persistence Model]  ← @Entity, @Table, etc. — JDBC / JPA
    │
    ▼  (Query layer maps from persistence)
[Domain Model returned to application]
    │
    ▼  (Controller maps domain to response)
[Response DTO]  ← Safe public contract
    │
    ▼  (JSON serialization)
[Client Response]
```

### 15.3 Mapper Strategy

- **Domain ↔ Persistence:** `infrastructure/persistence/mapper/` — one mapper per aggregate
- **Domain ↔ API DTO:** `api/` controller has dedicated mapper class (no MapStruct for complex domain logic — manual mapping preferred)
- **Domain ↔ Sidecar:** `infrastructure/sidecar/` adapter owns sidecar request/response mapping

### 15.4 No Shared DTO Policy

- Cross-module data exchange: MUST go through domain events or explicit port interfaces
- Module A MUST NOT import Module B's DTO class directly
- Shared data types (IDs, enums, pagination) live in `notarist-core`
- If two modules need the same response shape, each defines its own — no shared DTO between modules

### 15.5 Validation Boundary

| Layer | Validation Type |
|---|---|
| `api/request/` | Bean Validation (JSR-380): format, presence, size |
| `application/command/` | Business rule validation (domain): consistency, invariants |
| `domain/model/` | Domain invariants enforced via constructor — no invalid state |
| `infrastructure/` | No validation — trust domain integrity |

---

## APPENDIX A — MODULE BUILD DEPENDENCY GRAPH

```
notarist-web
  ├── notarist-auth
  ├── notarist-document
  ├── notarist-ingest
  ├── notarist-search
  ├── notarist-assistant
  ├── notarist-regulation
  └── notarist-audit
        └── all above depend on notarist-core
```

---

## APPENDIX B — FORBIDDEN PATTERNS (ENFORCED)

| Pattern | Forbidden Because |
|---|---|
| `@Service` in `domain/` package | Domain must not depend on Spring |
| Direct `EntityManager` in controller | Fat controller anti-pattern |
| `SELECT *` in any query | Explicit column selection mandatory |
| Synchronous OCR call in request thread | OCR is async-only via queue |
| Response without citation | Citation-first is non-negotiable |
| Shared mutable DTO across modules | No shared DTO policy |
| God-service class > 300 lines | Single responsibility enforced |
| Direct Qdrant client in domain layer | External dependency via adapter only |
| Storing raw PII in PostgreSQL or Qdrant | PII must be redacted before non-Oracle storage |
| JWT secret key in application.yaml | Secrets must be externalized |

---

## APPENDIX C — STEP 7 DECISION LOG

| Decision | Rationale |
|---|---|
| Modular monolith (not microservices) | Single team, single deployable, lower ops overhead for initial phase |
| PostgreSQL SKIP LOCKED queue (not RabbitMQ) | Fewer infrastructure components; same guarantee for current scale |
| Citation-first before LLM prompt | Prevents hallucination at generation time, not just detection time |
| HallucinationGuard as domain service | Must run without Spring context; testable in isolation |
| Hexagonal ports at module boundary | Allows sidecar swap (e.g., OCR engine change) without domain changes |
| VPD context set per-connection | Oracle VPD requires session context; connection pool must handle this |
| Refresh token as opaque UUID (not JWT) | Enables revocation without deny-list; JWT refresh tokens can't be invalidated |

---

*STEP 7 COMPLETE — Architecture specification only.*  
*No implementation code generated.*  
*Awaiting approval before proceeding to STEP 8 (implementation generation).*
