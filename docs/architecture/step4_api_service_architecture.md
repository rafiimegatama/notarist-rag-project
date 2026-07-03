# STEP 4 — API & SERVICE ARCHITECTURE DESIGN
# NOTARIST RAG PLATFORM — SPRING BOOT 3 / JAVA 17

**Version:** v1.0
**Date:** 2026-05-23
**Mode:** ANALYSIS_FIRST
**Status:** DRAFT — Pending Approval
**Scope:** Module architecture, API domain, service flow, event flow, security model

---

## SUMMARY

STEP 4 mendefinisikan seluruh arsitektur layanan dan API untuk NOTARIST RAG Platform.

**Stack:**
| Component | Technology |
|---|---|
| Backend framework | Spring Boot 3.x / Java 17 |
| API style | RESTful JSON (OpenAPI 3.1) |
| Architecture pattern | **Modular Monolith** (bukan microservices) |
| DB access: Oracle | Spring Data JPA + Hibernate |
| DB access: PostgreSQL | Spring JDBC Template / Spring Data JPA |
| Vector DB | Qdrant REST client (custom) |
| Streaming | Server-Sent Events (SSE) via Spring WebFlux |
| Async processing | Spring Integration + PostgreSQL queue (SKIP LOCKED) |
| Auth | Spring Security + JWT (RS256) |
| LLM | Ollama REST client (local model) |
| OCR | PaddleOCR REST service (sidecar) |
| NER | Rule engine (Java) + IndoBERT REST service (Python sidecar) |
| Object storage | MinIO (S3-compatible) |

**Architectural Rationale — Modular Monolith:**
Dipilih bukan microservices karena:
- Tim kecil, single deployment lebih mudah dikelola
- Shared domain model (akta, client, dokumen) menghindari cross-service call overhead
- Transaksional konsistency lebih mudah (Oracle XA tidak diperlukan)
- Pipeline ingestion lebih efisien dalam satu process
- Dapat dipisah menjadi microservices di masa depan jika skala membutuhkan

---

## MODULE ARCHITECTURE

### Top-Level Module Structure

```
notarist-rag-platform/
│
├── notarist-core/              Module 0 — Shared domain & utilities
├── notarist-auth/              Module 1 — Auth, JWT, RBAC
├── notarist-document/          Module 2 — Document master, versioning, relations
├── notarist-ingest/            Module 3 — Upload, staging, OCR, NER pipeline
├── notarist-search/            Module 4 — Hybrid search, RRF, reranking
├── notarist-assistant/         Module 5 — RAG orchestration, LLM, streaming
├── notarist-regulation/        Module 6 — Regulation hierarchy, pasal navigation
├── notarist-audit/             Module 7 — Audit trail, access log
└── notarist-web/               Module 8 — REST controllers, main app, config
```

### Module 0 — notarist-core

```
TANGGUNG JAWAB:
  Shared domain models, value objects, enums, exceptions, constants,
  utility classes. Tidak memiliki dependencies ke modul lain.

KEY PACKAGES:
  domain/
    model/          DocId, AktaId, ChunkId (strongly-typed ID value objects)
    enum/           JenisDokumen, JenisAkta, KlasifikasiKerahasiaan,
                    StatusDokumen, PipelineStage
    exception/      DocumentNotFoundException, UnauthorizedAccessException,
                    OcrFailureException, EmbeddingException,
                    VectorSyncException
  config/
    Constants       CHUNK_OVERLAP_RATIO, MAX_CHUNK_TOKENS, RRF_K
  util/
    SensitiveFieldRedactor   → redact NIK/amount dari text
    TextNormalizer           → lowercase, expand abbreviasi legal
    ChunkSizeCalculator      → token count estimation
    DocumentIdGenerator      → UUID generator
```

### Module 1 — notarist-auth

```
TANGGUNG JAWAB:
  JWT authentication, role-based authorization, user session management.
  Oracle VPD policy configuration (design, bukan implementasi VPD itu sendiri).

KEY PACKAGES:
  auth/
    service/
      JwtTokenService         → issue, validate, refresh RS256 JWT
      UserAuthService         → login, logout, current user
      RbacService             → role check, permission evaluation
    filter/
      JwtAuthFilter           → per-request JWT validation
      RbacFilter              → role enforcement per endpoint
      AccessControlFilter     → inject klasifikasi filter ke downstream

SECURITY CONTEXT PER REQUEST:
  NotaristPrincipal {
    userId: String
    username: String
    role: UserRole         STAFF | NOTARIS | PPAT_OFFICER | PIMPINAN | ADMIN
    allowedKlasifikasi: Set<String>   → pre-computed dari role
    sessionId: UUID
  }
  Stored in SecurityContextHolder, accessible ke seluruh request chain.
```

### Module 2 — notarist-document

```
TANGGUNG JAWAB:
  CRUD dokumen master, versioning, tagging, document relationships.
  Akses ke Oracle NOTARIST schema.

KEY SERVICES:
  DocumentService        → create, update, findById, findAll (dengan filter)
  DocumentVersionService → version history, compare versions
  DocumentTagService     → add/remove/list tags
  DocumentRelationService→ create/query DOC_RELATIONSHIP
  AktaService            → AKTA_MASTER CRUD, pihak & sertifikat mapping
  ClientService          → CLIENT_MASTER CRUD
  PersonService          → PERSON_MASTER, NOTARIS, PPAT management
  SertifikatService      → SERTIFIKAT_MASTER CRUD

KEY REPOSITORIES (JPA, Oracle):
  DocMasterRepository
  AktaMasterRepository
  ClientMasterRepository
  PersonMasterRepository
  NotarisMasterRepository
  PpatMasterRepository
  SertifikatMasterRepository
  DocRelationshipRepository
  DocVersionRepository
  TagMasterRepository
  DocTagMapRepository
```

### Module 3 — notarist-ingest

```
TANGGUNG JAWAB:
  Upload pipeline, OCR orchestration, NER pipeline (rule + IndoBERT),
  metadata extraction, staging management.

KEY SERVICES:
  DocumentUploadService      → file validation, staging, promosi ke DOC_MASTER
  OcrOrchestrationService    → trigger OCR, quality gate, store ocr_result
  NerOrchestrationService    → rule extraction + IndoBERT + conflict resolver
  SensitiveFieldRedactorService → redact sebelum store ke chunk_text
  MetadataMappingService     → map extracted entities ke Oracle master
  PipelineStateService       → manage DOC_MASTER.STATUS transitions
  ManualReviewQueueService   → flag docs untuk human review

NER PIPELINE (lihat STEP 3 decision):
  NerPipelineOrchestrator
  ├── RuleBasedExtractor     (Java — regex/pattern engine)
  │     Ekstrak: nomor akta, sertifikat, tanggal, NIK, NPWP,
  │             nomor APHT/SKMHT/fidusia, kode wilayah, nomor surat
  │     Output: EntityCandidate{value, type, confidence, source=RULE, regexVersion}
  │
  ├── IndoBertNerClient      (HTTP client ke Python NER sidecar)
  │     Ekstrak: nama pihak, notaris, PPAT, alamat, organisasi,
  │             wilayah, jabatan, instansi, legal entity
  │     Output: EntityCandidate{value, type, confidence, source=INDOBERT}
  │
  ├── ConflictResolver       → merge results, resolve overlapping entities
  │     Priority: RULE > INDOBERT untuk field deterministic
  │     INDOBERT wins untuk nama/alamat
  │
  ├── ValidationLayer        → validasi format (NIK 16 digit, tanggal valid, dll)
  │     Mark low-confidence as review_required=TRUE
  │
  └── LlmFallbackExtractor   → HANYA jika confidence < threshold
        Kirim paragraph ke LLM dengan prompt ekstraksi
        Untuk: ambiguity resolution, relation inference

KEY REPOSITORIES (JPA/JDBC, PostgreSQL):
  OcrResultRepository
  LegalEntityExtractRepository
  DocChunkRepository
  DocProcessingLogRepository
  StgDocIncomingRepository     (Oracle NOTARIST_STG)
```

### Module 4 — notarist-search

```
TANGGUNG JAWAB:
  Hybrid search orchestration: Qdrant semantic + PostgreSQL BM25 + RRF fusion,
  reranking, access filter injection.

KEY SERVICES:
  HybridSearchService         → orchestrate full hybrid search pipeline
  QdrantSearchClient          → semantic search via Qdrant REST API
  PostgresFullTextSearchService → BM25 via PostgreSQL tsvector
  RrfFusionService            → Reciprocal Rank Fusion merge
  DiversityEnforcementService → max N chunks per doc
  RerankerClient              → HTTP client ke reranker sidecar
  AccessFilterBuilder         → build Qdrant filter + PostgreSQL WHERE
  SearchCacheService          → cache hit/miss, TTL management

SEARCH REQUEST MODEL:
  SearchRequest {
    queryText: String
    searchMode: HYBRID | SEMANTIC | KEYWORD
    jenisDokumen: String?      (optional filter)
    jenisAkta: String?         (optional filter)
    dateFrom: LocalDate?
    dateTo: LocalDate?
    topK: Integer              default 5
    includeRelated: Boolean    expand via DOC_RELATIONSHIP?
  }

SEARCH RESPONSE MODEL:
  SearchResponse {
    query: String
    totalFound: Integer
    searchMode: String
    results: [SearchResultItem] {
      chunkId: UUID
      docId: String
      nomorAkta: String?
      jenisDokumen: String
      jenisAkta: String?
      pageNumber: Integer
      snippet: String             masked version for display
      semanticScore: Float?
      keywordScore: Float?
      rerankScore: Float?
      finalScore: Float
      sourceFilename: String
      tanggalDokumen: LocalDate?
      klasifikasi: String
    }
    executionTimeMs: Long
  }
```

### Module 5 — notarist-assistant

```
TANGGUNG JAWAB:
  RAG orchestration: intent classification, context assembly, LLM integration,
  streaming response, citation extraction, conversation management.

KEY SERVICES:
  AssistantService             → main RAG orchestration entry point
  IntentClassifierService      → classify query intent
  QueryPreprocessorService     → normalize, expand abbreviasi legal
  ContextAssemblerService      → build prompt dari retrieved chunks
  LlmClient                   → HTTP client ke Ollama REST API
  StreamingLlmClient          → SSE/streaming variant
  CitationExtractorService    → parse [SUMBER-X] dari response
  ConversationService         → multi-turn conversation management
  FollowUpSuggestionService   → generate follow-up query suggestions
  HallucinationFlagService    → optional flag dari user feedback

INTENT TYPES:
  SEARCH_DOCUMENT   → Structured metadata query (tidak perlu LLM)
  ASK_LEGAL         → Full RAG pipeline
  EXPLAIN_TERM      → Mini RAG (top-1 chunk) atau LLM direct
  RELATED_DOCS      → DOC_RELATIONSHIP traversal + optional RAG
  SUMMARIZE         → Full RAG N=10

LLM PROVIDER ABSTRACTION:
  LlmProvider (interface)
  ├── OllamaProvider     (local LLM via Ollama)
  └── [extensible untuk provider lain tanpa mengubah service layer]
```

### Module 6 — notarist-regulation

```
TANGGUNG JAWAB:
  Regulation hierarchy management, pasal navigation, amendment tracking,
  cross-regulation citation graph.

KEY SERVICES:
  RegulasiService              → CRUD REGULASI_MASTER
  RegulasiHierarchyService    → navigate BAB/Pasal/Ayat tree
  RegulasiPasalService        → pasal detail, citation retrieval
  AmendmentService             → track perubahan, compare versions
  CrossRegulationService      → relasi antar regulasi

KEY ENTITIES (Oracle):
  REGULASI_MASTER
  REGULASI_BAB                 (level 1 hierarchy)
  REGULASI_PASAL               (level 2 — primary citation unit)
  REGULASI_AYAT                (level 3 — sub-pasal)
  REGULASI_CITATION            (cross-regulation references)

HIERARCHY MODEL:
  REGULASI_MASTER
  └── REGULASI_BAB (BAB_ID, REGULASI_ID, NOMOR, JUDUL)
       └── REGULASI_PASAL (PASAL_ID, BAB_ID, NOMOR, KONTEN)
            └── REGULASI_AYAT (AYAT_ID, PASAL_ID, NOMOR, KONTEN)

CHUNK MAPPING:
  Setiap REGULASI_PASAL.CHUNK_ID_REF → rag.doc_chunk
  Setiap REGULASI_AYAT.CHUNK_ID_REF → rag.doc_chunk (opsional, granular)
  Ini memungkinkan: AI response → citation → pasal/ayat → navigasi UI
```

### Module 7 — notarist-audit

```
TANGGUNG JAWAB:
  Audit trail management, access logging, security event tracking.
  Akses ke Oracle NOTARIST_SEC schema + PostgreSQL rag.ai_interaction_audit.

KEY SERVICES:
  AuditTrailService        → record DDL/DML changes ke master data
  DocumentAccessLogService → record setiap akses dokumen fisik
  AiInteractionAuditService→ record RAG queries, docs exposed
  SensitiveFieldAccessLog  → record akses ke field sensitif (clear view)
  AuditQueryService        → query audit records untuk reporting

KEY DESIGN:
  Semua audit write bersifat FIRE AND FORGET (async) — tidak boleh
  memblokir main request thread.
  Audit loss acceptable (audit tidak boleh cause transaction failure).
  Tapi audit gaps harus di-alert untuk compliance.
```

### Module 8 — notarist-web

```
TANGGUNG JAWAB:
  REST controllers, OpenAPI config, error handling, CORS, main app entry.
  Tidak memiliki business logic — hanya routing ke service layer.

CONTROLLERS:
  AuthController           → /api/v1/auth/**
  UserController           → /api/v1/users/**
  DocumentController       → /api/v1/documents/**
  IngestController         → /api/v1/ingest/**
  SearchController         → /api/v1/search/**
  AssistantController      → /api/v1/assistant/**
  RegulationController     → /api/v1/regulations/**
  ChunkController          → /api/v1/chunks/**
  CitationController       → /api/v1/citations/**
  AuditController          → /api/v1/audit/**
  AdminController          → /api/v1/admin/**

CROSS-CUTTING CONCERNS (via Spring AOP):
  RequestLoggingAspect     → log setiap request (method, path, user, latency)
  SensitiveMaskingAspect   → mask response fields berdasarkan role
  AuditLoggingAspect       → trigger audit write untuk aksi sensitif
  RateLimitAspect          → rate limiting per user/endpoint
```

### External Services (Sidecar Architecture)

```
┌─────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APP (JVM)                        │
│                                                                 │
│  notarist-web (controllers)                                     │
│       ↓                                                         │
│  [module services]                                              │
│       ↓                                                         │
│  [HTTP clients ke sidecar]                                      │
└───────────────────┬─────────────────────────────────────────────┘
                    │ HTTP / localhost
        ┌───────────┼───────────────────────────────┐
        │           │                               │
        ▼           ▼                               ▼
[PaddleOCR     [IndoBERT NER     [Ollama           [Reranker
 sidecar       Python service]   local LLM]        sidecar]
 :8081]        :8082]            :11434]            :8083]
```

---

## API DOMAIN

**Base URL:** `/api/v1/`
**Content-Type:** `application/json`
**Auth:** `Authorization: Bearer <JWT>`
**Standard Response Envelope:**

```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "timestamp": "2026-05-23T10:00:00Z",
    "requestId": "uuid",
    "executionTimeMs": 120
  },
  "errors": []
}
```

---

### DOMAIN 1 — Authentication & User

```
POST   /auth/login
       Body: {username, password}
       Response: {accessToken, refreshToken, expiresIn, role}

POST   /auth/refresh
       Body: {refreshToken}
       Response: {accessToken, expiresIn}

POST   /auth/logout
       Header: Bearer token
       Action: invalidate session

GET    /users/me
       Response: {userId, username, fullName, email, role, lastLogin}

GET    /users                       [ADMIN only]
       Params: role, status, page, size
       Response: paginated user list

POST   /users                       [ADMIN only]
       Body: {username, email, fullName, role}

PUT    /users/{id}/role             [ADMIN only]
       Body: {role}

PUT    /users/{id}/status           [ADMIN only]
       Body: {status: ACTIVE|SUSPENDED}

GET    /users/{id}/activity         [PIMPINAN, ADMIN]
       Response: recent access log summary
```

---

### DOMAIN 2 — Document Management

```
GET    /documents
       Params: jenisDokumen, jenisAkta, klasifikasi, status,
               notaryId, clientName, nomorAkta, dateFrom, dateTo,
               page, size, sort
       Response: paginated document list (filtered by user role)

GET    /documents/{docId}
       Response: full document detail (with masking per role)

POST   /documents/upload
       Content-Type: multipart/form-data
       Body: file, jenisDokumen, klasifikasi, keterangan
       Response: {docId, docCode, status: PENDING_OCR, estimatedProcessingMin}

PUT    /documents/{docId}/metadata
       Body: {docTitle, klasifikasi, keterangan}
       Response: updated document

DELETE /documents/{docId}           [PIMPINAN, ADMIN]
       Action: soft delete (STATUS → DELETED)
       Requires: reason in body

GET    /documents/{docId}/status
       Response: pipeline status per stage
       {
         "upload": "COMPLETED",
         "ocr": "COMPLETED",
         "ner": "IN_PROGRESS",
         "chunking": "PENDING",
         "embedding": "PENDING",
         "isSearchable": false,
         "ocrQuality": 0.87,
         "totalChunks": 0,
         "indexedChunks": 0
       }

GET    /documents/{docId}/relationships
       Response: related documents with relation type
       [{docId, docTitle, nomorAkta, relationType, direction}]

POST   /documents/{docId}/relationships
       Body: {docIdTo, relationType}   [NOTARIS+]
       Response: created relationship

GET    /documents/{docId}/versions
       Response: version history [{versionId, versionNumber, changeType, changedAt}]

GET    /documents/{docId}/chunks
       Params: page, size, chunkType
       Response: paginated chunk list for document

GET    /documents/{docId}/akta
       Response: AKTA_MASTER detail + pihak list + sertifikat list
```

---

### DOMAIN 3 — Ingestion & OCR

```
POST   /ingest/retry/{docId}
       Action: retry stuck pipeline from last failed stage
       Response: {stage, newStatus}   [ADMIN]

POST   /ingest/ocr/retry/{docId}
       Action: re-run OCR specifically
       Response: {queuePosition, estimatedMinutes}   [ADMIN]

GET    /ingest/queue
       Params: stage, status, page, size   [ADMIN]
       Response: documents in pipeline queue

GET    /ingest/review-queue
       Response: docs flagged for manual review (low OCR confidence / low NER confidence)
       [{docId, docTitle, flag_reason, ocr_confidence}]   [NOTARIS, PIMPINAN, ADMIN]

POST   /ingest/review/{docId}/approve
       Body: {corrections: [{entityType, entityValue}]}   [NOTARIS+]
       Action: mark review as approved, continue pipeline

POST   /ingest/review/{docId}/reject
       Body: {reason}   [NOTARIS+]
       Action: mark doc as DELETED or request re-scan

GET    /ingest/stats
       Response: pipeline statistics (docs per stage, avg processing time)   [ADMIN]
```

---

### DOMAIN 4 — Search

```
POST   /search
       Body: {
         queryText: String,
         searchMode: "HYBRID" | "SEMANTIC" | "KEYWORD",
         filters: {
           jenisDokumen?: String,
           jenisAkta?: String,
           dateFrom?: String,
           dateTo?: String
         },
         topK?: Integer    (default 5, max 20)
       }
       Response: SearchResponse (lihat Module 4 model di atas)

GET    /search/suggest
       Params: q (min 3 chars), jenisDokumen
       Response: [{suggestion, type}]
       (autocomplete dari: nomor akta, nama client, jenis dokumen populer)

GET    /search/history
       Params: page, size
       Response: recent queries oleh current user + top results per query
```

---

### DOMAIN 5 — AI Assistant

```
POST   /assistant/ask
       Body: {
         queryText: String,
         conversationId?: UUID,    (null = new conversation)
         filters?: {jenisDokumen, jenisAkta, dateFrom, dateTo},
         topK?: Integer,
         includeRelated?: Boolean  (expand via DOC_RELATIONSHIP)
       }
       Response: {
         responseId: UUID,
         conversationId: UUID,
         answer: String,
         intent: String,
         confidence: "HIGH"|"MEDIUM"|"LOW",
         sources: [CitationItem],
         followUpSuggestions: [String],
         executionTimeMs: Long
       }

POST   /assistant/ask/stream
       Body: same as /ask
       Response: text/event-stream (SSE)
       Events:
         event: token         data: "Berdasarkan..."   (LLM token stream)
         event: sources       data: [{...citation...}]
         event: done          data: {responseId, executionTimeMs}
         event: error         data: {errorCode, message}

GET    /assistant/conversations
       Params: page, size
       Response: list of conversation sessions for current user

GET    /assistant/conversations/{conversationId}
       Response: full conversation history
       {messages: [{role, content, timestamp, sources?}]}

DELETE /assistant/conversations/{conversationId}
       Action: delete conversation history

POST   /assistant/conversations/{conversationId}/follow-up
       Body: {queryText}
       Response: same as /ask (with conversationId maintained)

POST   /assistant/feedback/{responseId}
       Body: {rating: 1-5, comment?, isHallucinated: Boolean}
       Action: store user feedback, optionally flag for review
```

---

### DOMAIN 6 — Regulation

```
GET    /regulations
       Params: jenisRegulasi, lembagaPenerbit, tahun, statusRegulasi,
               page, size, sort
       Response: paginated regulation list

GET    /regulations/{regulasiId}
       Response: regulation detail
       {regulasiId, jenisRegulasi, nomorRegulasi, tahun, judul,
        singkatan, lembagaPenerbit, tanggalBerlaku, statusRegulasi,
        parentRegulasiId?, amendmentType?}

GET    /regulations/{regulasiId}/structure
       Response: full hierarchy tree
       {
         regulasiId: "...",
         judul: "...",
         bab: [
           {
             babId: "...",
             nomor: "I",
             judul: "KETENTUAN UMUM",
             pasal: [
               {
                 pasalId: "...",
                 nomor: "1",
                 konten: "Dalam Undang-Undang ini yang dimaksud...",
                 ayat: [
                   {ayatId: "...", nomor: "(1)", konten: "..."}
                 ],
                 chunkIdRef: "uuid"
               }
             ]
           }
         ]
       }

GET    /regulations/{regulasiId}/bab
       Response: list BAB (tanpa konten pasal)

GET    /regulations/bab/{babId}/pasal
       Response: list pasal dalam BAB dengan konten

GET    /regulations/pasal/{pasalId}
       Response: pasal detail + ayat list + chunk reference
       {pasalId, nomor, judul?, konten, ayat: [...], chunkIdRef, babRef}

GET    /regulations/ayat/{ayatId}
       Response: ayat detail
       {ayatId, nomor, konten, huruf: [...], pasalRef}

GET    /regulations/{regulasiId}/amendments
       Response: amendment history chain
       [{regulasiId, judul, amendmentType, tanggalBerlaku, direction}]

GET    /regulations/compare
       Params: regulasiIdA, regulasiIdB
       Response: diff summary antara dua versi/regulasi
       (untuk manual compare — bukan automated diff)

POST   /regulations/{regulasiId}/citations
       Body: {citingRegulasiId, citingPasalId, targetPasalId, citationType}
       Action: create cross-regulation citation link   [ADMIN]

GET    /regulations/pasal/{pasalId}/cited-by
       Response: list dokumen/regulasi yang mengutip pasal ini

GET    /regulations/search
       Params: q, jenisRegulasi, tahun
       Response: pasal-level search results (semantic atau keyword)
```

---

### DOMAIN 7 — Chunks & Embeddings

```
GET    /chunks
       Params: docId, chunkType, isIndexed, page, size
       Response: paginated chunk list (for developers/admin)

GET    /chunks/{chunkId}
       Response: chunk detail
       {chunkId, docId, chunkText (redacted), chunkType, pageNumber,
        tokenCount, embeddingStatus, qdrantPointId, ocrConfidence}

GET    /chunks/{chunkId}/similar
       Params: topK (default 5), jenisDokumen?, klasifikasi?
       Response: similar chunks via Qdrant k-NN
       [{chunkId, docId, snippet, similarityScore}]

GET    /chunks/{chunkId}/context
       Response: chunk N-1, N, N+1 untuk menampilkan context sekitar chunk ini
       {prevChunk?, currentChunk, nextChunk?}

GET    /chunks/search
       Params: q, docId?, chunkType?, page, size
       Response: full-text search dalam chunk content
```

---

### DOMAIN 8 — Citations

```
GET    /citations/response/{responseId}
       Response: citations untuk satu AI response
       [{citationId, order, docId, nomorAkta, pageNumber, snippet, score}]

GET    /citations/response/{responseId}/trace
       Response: full traceability chain
       {
         response: {responseId, answerText, model, tokens},
         query: {queryId, queryText, intent, userId},
         citations: [
           {
             citationId,
             docId,
             docTitle,
             nomorAkta,
             pageNumber,
             snippet,
             chunkId,
             ocrPage: {confidence, rawTextAvailable},
             sourceFile: {originalFilename, storagePath}
           }
         ]
       }

GET    /citations/document/{docId}
       Response: semua AI responses yang mengutip dokumen ini
       Useful untuk: "dokumen ini sudah pernah digunakan AI untuk menjawab apa?"

GET    /citations/pasal/{pasalId}
       Response: AI responses yang mengutip pasal ini (untuk regulasi)
```

---

### DOMAIN 9 — Audit

```
GET    /audit/documents/{docId}
       Params: action, dateFrom, dateTo, page, size   [PIMPINAN, ADMIN]
       Response: audit trail untuk satu dokumen

GET    /audit/users/{userId}
       Params: action, dateFrom, dateTo, page, size   [PIMPINAN, ADMIN]
       Response: aktivitas satu user

GET    /audit/ai-sessions
       Params: userId, dateFrom, dateTo, riskLevel, page, size   [PIMPINAN, ADMIN]
       Response: AI interaction audit log

GET    /audit/ai-sessions/{sessionId}
       Response: detail satu sesi: query → chunks retrieved → docs accessed → response

GET    /audit/sensitive-access
       Params: userId, fieldName, dateFrom, dateTo   [ADMIN]
       Response: log akses ke field sensitif dalam clear view
```

---

### DOMAIN 10 — Admin

```
GET    /admin/pipeline/health
       Response: overall pipeline health
       {
         ocrQueueDepth: 12,
         nerQueueDepth: 5,
         embeddingQueueDepth: 24,
         manualReviewQueueDepth: 3,
         avgOcrConfidence: 0.86,
         failedDocsLast24h: 2,
         qdrantVsPostgresSyncRate: 0.998
       }

POST   /admin/reindex/{docId}
       Body: {reindexType: "FULL"|"METADATA_ONLY"}
       Response: {jobId, estimatedMinutes}   [ADMIN]

POST   /admin/reindex/bulk
       Body: {modelVersion?, jenisDokumen?, forceAll: Boolean}
       Response: {jobId, docsQueued}   [ADMIN]

GET    /admin/jobs/{jobId}
       Response: background job status {status, progress, error?}

GET    /admin/qdrant/sync-check
       Response: consistency check result {pgChunks, qdrantPoints, discrepancy}

POST   /admin/qdrant/sync-repair
       Action: resync discrepant chunks   [ADMIN]

GET    /admin/llm/health
       Response: Ollama service health + model loaded

GET    /admin/ocr/health
       Response: PaddleOCR sidecar health

GET    /admin/ner/health
       Response: IndoBERT sidecar health
```

---

## SERVICE FLOW

### SF-01: Document Upload → Active Flow

```
[CLIENT] POST /documents/upload
              ↓
[AuthFilter] validate JWT, extract NotaristPrincipal
              ↓
[RateLimitAspect] check upload rate limit per user
              ↓
[IngestController.upload()]
              ↓
[DocumentUploadService.initiateUpload()]
  1. Validate file type (whitelist)
  2. Validate file size (max 100MB)
  3. Calculate SHA-256 checksum
  4. Check duplicate → if DOC_MASTER exists with same checksum → 409 Conflict
  5. Save file ke MinIO object storage
  6. Insert NOTARIST_STG.STG_DOC_INCOMING (STATUS: PENDING)
  7. Malware scan (async, via event)
              ↓
[UploadValidationEvent published]
              ↓ (async listener)
[ValidationListener]
  8. Wait for malware scan result
  9. If clean → promote to NOTARIST.DOC_MASTER (STATUS: PENDING_OCR)
                 + DOC_VERSION (VERSION: 1, INITIAL)
                 + STG_DOC_INCOMING.STATUS = PROMOTED
                 + AuditTrail: INSERT event
 10. If infected → STG_DOC_INCOMING.STATUS = REJECTED
                   + AuditTrail: UPLOAD_REJECTED
              ↓
[OcrQueuedEvent published]
              ↓ (async OCR worker picks up)
[OcrWorker]  → Pipeline-02 (lihat STEP 3)
              ↓
... [rest of pipeline, each stage via events]
              ↓
[DocActiveEvent] → DOC_MASTER.STATUS = ACTIVE → doc searchable

[CLIENT] mendapat response langsung:
  {docId, docCode, status: "PENDING_OCR", estimatedProcessingMin: 5}
  Lalu poll GET /documents/{docId}/status untuk progress
```

### SF-02: AI Assistant Ask Flow (Non-Streaming)

```
[CLIENT] POST /assistant/ask
  Body: {queryText: "Apa syarat APHT?", topK: 5}
              ↓
[AssistantController.ask()]
  Extract NotaristPrincipal dari SecurityContext
              ↓
[AssistantService.processQuery()]
  1. Get/create search_session
  2. Insert ai_query record
  3. Preprocess query (normalize, expand abbr)
  4. Classify intent → ASK_LEGAL
  5. Build access filter dari role (klasifikasi allowed set)
              ↓
  6. Call HybridSearchService.search(preprocessedQuery, filter, topK=20)
     ├── [parallel] QdrantSearchClient.search() → 20 semantic results
     └── [parallel] PostgresFullTextSearchService.search() → 20 keyword results
              ↓
  7. RrfFusionService.fuse(semanticResults, keywordResults) → 30 merged
  8. DiversityEnforcementService.enforce(merged) → max 3 per doc
  9. RerankerClient.rerank(query, candidates) → 5 final
 10. Store retrieval_result records
              ↓
 11. ContextAssemblerService.assemble(top5Chunks)
     → formatted context string dengan [SUMBER-1]...[SUMBER-5] labels
              ↓
 12. LlmClient.complete(systemPrompt + context + userQuery)
     → raw LLM response text
              ↓
 13. CitationExtractorService.extract(responseText, chunkMapping)
     → List<Citation>
              ↓
 14. Store ai_response + citation records
              ↓
 15. AiInteractionAuditService.log(user, query, docsAccessed)
 16. DocumentAccessLogService.logBatch(user, docIds, SYSTEM_ACCESS)
              ↓
 17. Assemble final response (with field masking per role)
              ↓
[CLIENT] receives response {answer, sources, confidence, followUpSuggestions}
```

### SF-03: AI Assistant Ask Flow (Streaming)

```
[CLIENT] POST /assistant/ask/stream
         Accept: text/event-stream
              ↓
[AssistantController.askStream()]
  Returns: SseEmitter (Spring)
              ↓
[AssistantService.processQueryStream()]
  Steps 1-11 sama dengan SF-02 (retrieval)
              ↓
 12. StreamingLlmClient.streamComplete()
     → returns Flux<String> (reactive token stream)
              ↓
 13. Per token:
     emitter.send(SseEmitter.event()
                   .name("token")
                   .data(token))
              ↓
 14. Saat stream selesai:
     a. CitationExtractorService.extract(fullResponse)
     b. Store ai_response + citation
     c. Emit: event: sources   data: [citationJson]
     d. Emit: event: done      data: {responseId, executionTimeMs}
              ↓
 15. Audit logging (async, tidak block stream)
              ↓
 16. emitter.complete()
```

### SF-04: Regulation Hierarchy Navigation

```
[CLIENT] GET /regulations/{regulasiId}/structure
              ↓
[RegulationController.getStructure()]
              ↓
[RegulasiHierarchyService.buildTree(regulasiId)]
  1. Fetch REGULASI_MASTER
  2. Fetch all REGULASI_BAB WHERE REGULASI_ID = X ORDER BY URUTAN
  3. Per BAB: fetch all REGULASI_PASAL ORDER BY URUTAN
  4. Per PASAL: fetch all REGULASI_AYAT ORDER BY URUTAN
  5. Assemble nested tree structure
  6. Enrich each pasal: chunkIdRef → fetch snippet dari rag.doc_chunk
              ↓
[Response] nested hierarchy JSON
  (Tree bisa di-lazy-load per level jika terlalu besar)
```

### SF-05: Pasal-Level Citation Trace

```
[CLIENT] GET /citations/response/{responseId}/trace
              ↓
[CitationController.trace()]
              ↓
[CitationExtractorService.buildTrace(responseId)]
  1. Fetch ai_response
  2. Fetch ai_query (via response.queryId)
  3. Fetch all citations for this response
  4. Per citation:
     a. Fetch doc_chunk (chunkId)
     b. Fetch ocr_result (doc_id + page_number)
     c. Fetch DOC_MASTER (doc_id) — Oracle
     d. If doc is REGULASI: find REGULASI_PASAL WHERE CHUNK_ID_REF = chunkId
        → attach {pasalId, nomor, babRef} ke citation
  5. Assemble full trace chain
              ↓
[Response] full provenance: answer → citation → chunk → ocr_page → source_file
           + pasal reference jika applicable
```

---

## EVENT FLOW

### Event-Driven Processing Architecture

PostgreSQL digunakan sebagai message queue via **SKIP LOCKED** pattern —
tidak memerlukan external queue (Redis/RabbitMQ) untuk deployment yang lebih
sederhana. Dapat diganti dengan RabbitMQ jika volume meningkat.

```
QUEUE TABLE: notarist_rag.rag.pipeline_queue
  queue_id        UUID PK
  doc_id          VARCHAR(36)
  stage           VARCHAR(30)   OCR | NER | CHUNKING | EMBEDDING | INDEXING
  priority        INTEGER       1=HIGH, 2=NORMAL, 3=LOW
  status          VARCHAR(20)   PENDING | PROCESSING | DONE | FAILED
  retry_count     INTEGER
  payload         JSONB
  scheduled_at    TIMESTAMPTZ
  picked_at       TIMESTAMPTZ
  completed_at    TIMESTAMPTZ

WORKER POLLING (Spring Scheduled, per worker):
  SELECT * FROM rag.pipeline_queue
  WHERE stage = ? AND status = 'PENDING'
  AND scheduled_at <= NOW()
  ORDER BY priority ASC, scheduled_at ASC
  LIMIT 1
  FOR UPDATE SKIP LOCKED
  → prevents multiple workers picking same job
```

### Event Flow Diagram

```
[UPLOAD COMPLETE]
     │
     │ publishes: UploadValidatedEvent{docId}
     ▼
[OCR_WORKER]         pipeline_queue stage=OCR
     │
     │ publishes: OcrCompleteEvent{docId, avgConfidence}
     ▼
[NER_WORKER]         pipeline_queue stage=NER
     │
     │ publishes: NerCompleteEvent{docId, entitiesExtracted}
     ▼
[CLASSIFICATION_WORKER] pipeline_queue stage=CLASSIFICATION
     │
     │ publishes: ClassificationCompleteEvent{docId, jenisDokumen, klasifikasi}
     ▼
[CHUNKING_WORKER]    pipeline_queue stage=CHUNKING
     │
     │ publishes: ChunkingCompleteEvent{docId, chunkCount}
     ▼
[EMBEDDING_WORKER]   pipeline_queue stage=EMBEDDING
     │
     │ publishes: EmbeddingCompleteEvent{docId, indexedChunks}
     ▼
[INDEXING_COMPLETE]
     │ DOC_MASTER.STATUS = ACTIVE
     │ semantic_metadata.is_searchable = TRUE
     │ publishes: DocActiveEvent{docId}
     ▼
[NOTIFICATION_SERVICE]
     │ Notif ke uploader: "Dokumen {docTitle} sudah aktif dan dapat dicari"
     └── via Push Notification atau In-App notification

FAILURE PATH:
     [ANY_WORKER] → failure after 3 retries
     │ publishes: PipelineFailureEvent{docId, stage, error}
     ▼
     [FAILURE_HANDLER]
     │ DOC_MASTER tetap di stage sebelumnya
     │ pipeline_queue status = FAILED
     │ INSERT manual_review_queue
     └── Alert admin via email/Slack (configurable)
```

### Worker Pool Configuration

```
WORKER TYPE          | MIN THREADS | MAX THREADS | QUEUE POLL (sec)
---------------------|-------------|-------------|------------------
OCR Worker           | 2           | 4           | 5
NER Worker           | 2           | 4           | 5
Classification Worker| 1           | 2           | 10
Chunking Worker      | 2           | 4           | 5
Embedding Worker     | 1           | 2           | 10  (GPU-bound)
Sync Check Worker    | 1           | 1           | 3600 (hourly)
Cache Cleanup Worker | 1           | 1           | 86400 (daily)
```

---

## RETRIEVAL ORCHESTRATION FLOW

```
REQUEST: POST /assistant/ask
     │
     ├── [AUTH] JWT validation → NotaristPrincipal
     ├── [RATE LIMIT] 60 req/min per user
     │
     ▼
[INTENT CLASSIFICATION]
     Rules-based:
     ├── "cari akta", "temukan dokumen" → SEARCH_DOCUMENT
     ├── "apa itu", "jelaskan", "definisi" → EXPLAIN_TERM
     ├── "dokumen terkait", "ada hubungannya" → RELATED_DOCS
     ├── "ringkaskan", "summarikan" → SUMMARIZE
     └── default → ASK_LEGAL
     │
     ▼
[QUERY PREPROCESSING]
     ├── Lowercase
     ├── Expand legal abbr:
     │     "HT" → "Hak Tanggungan"
     │     "APHT" → "Akta Pemberian Hak Tanggungan"
     │     "KPR" → "Kredit Pemilikan Rumah"
     ├── Spell normalization
     └── Store normalized query in ai_query.query_text_norm
     │
     ▼
[ACCESS FILTER BUILD]
     STAFF:         {klasifikasi: [PUBLIC, INTERNAL]}
     NOTARIS:       {klasifikasi: [PUBLIC, INTERNAL, CONFIDENTIAL]}
     PIMPINAN/ADMIN:{klasifikasi: [all]}
     + user-provided filters (jenisDokumen, jenisAkta, dateRange)
     │
     ▼
[PARALLEL HYBRID SEARCH]
     ┌──────────────────────────────────────────────────────┐
     │                   PARALLEL EXECUTION                 │
     ├──────────────────────────┬───────────────────────────┤
     │ SEMANTIC (Qdrant)        │ KEYWORD (PostgreSQL)      │
     │                          │                           │
     │ embed(queryNorm)         │ ts_query(queryNorm)       │
     │ → float[1024]            │                           │
     │                          │                           │
     │ qdrant.search(           │ SELECT chunk_id,          │
     │   vector=queryVec,       │   ts_rank_cd(chunk_text_  │
     │   filter=accessFilter,   │   tsv, query) AS score    │
     │   limit=20               │ FROM rag.doc_chunk        │
     │ )                        │ WHERE chunk_text_tsv      │
     │                          │   @@ query                │
     │ Result: [{chunkId,score}]│ AND is_indexed=TRUE       │
     │                          │ LIMIT 20                  │
     │                          │                           │
     │                          │ Result: [{chunkId,rank}]  │
     └──────────────┬───────────┴──────────────┬────────────┘
                    │                           │
                    └──────────────┬────────────┘
                                   ▼
                           [RRF FUSION]
                    score(chunk) = Σ 1/(60 + rank_i)
                    Merge, deduplicate → 30 candidates
                                   │
                                   ▼
                    [DIVERSITY: max 3 per doc_id]
                                   │
                                   ▼
                    [RELATIONSHIP EXPANSION] (if includeRelated)
                    Query DOC_RELATIONSHIP untuk top docs
                    Pull additional chunks from related docs
                    Add to candidate pool with lower weight
                                   │
                                   ▼
                    [RERANKING: cross-encoder]
                    POST http://reranker:8083/rerank
                    Body: {query, candidates: [{id, text}]}
                    → scored list, pick top-N
                                   │
                                   ▼
                    [STORE retrieval_result]
                    (semantic_score, keyword_score, rerank_score, final_score)
                                   │
                                   ▼
                    [CONTEXT ASSEMBLY]
                    Max 4096 tokens
                    Format: [SUMBER-1]\n{chunk_text}\n\n[SUMBER-2]...
                                   │
                                   ▼
                    [LLM CALL]
                    POST http://ollama:11434/api/chat
                    {model, messages: [{system}, {user}]}
                    → response text
                                   │
                                   ▼
                    [CITATION EXTRACTION]
                    Parse [SUMBER-X] references
                    Map → chunk metadata
                                   │
                                   ▼
                    [RESPONSE ASSEMBLY + MASKING]
                    Apply role-based field masking
                                   │
                                   ▼
                    [ASYNC AUDIT LOGGING]
                    (tidak block response)
```

---

## STREAMING RESPONSE ARCHITECTURE

```
CLIENT ←────────────── SSE Stream ─────────────── SERVER

timeline:
t=0ms    [token]  "Berdasar"
t=5ms    [token]  "kan"
t=10ms   [token]  " APHT"
t=15ms   [token]  " Nomor"
...
t=2000ms [token]  "..." (LLM finishes)
t=2010ms [sources] [{citationJson}]
t=2015ms [done]    {responseId, executionTimeMs: 2015}

SPRING SSE IMPLEMENTATION DESIGN:
  AssistantController menggunakan SseEmitter
  AssistantService.processQueryStream() mengembalikan Flux<StreamEvent>
  StreamEvent types: TokenEvent | SourcesEvent | DoneEvent | ErrorEvent

  Timeout: 60 seconds (LLM timeout)
  Reconnect: client responsibility (dengan Last-Event-ID)
  Buffer: none (real-time streaming)

SSE EVENT FORMAT:
  id: {incrementing counter}
  event: token
  data: {"text": "Berdasarkan"}

  id: {n}
  event: sources
  data: [{"docId":"...","nomor_akta":"...","page":3,"snippet":"..."}]

  id: {n+1}
  event: done
  data: {"responseId":"uuid","executionTimeMs":2015}

ERROR EVENT:
  event: error
  data: {"errorCode":"LLM_TIMEOUT","message":"LLM tidak merespons dalam 30 detik"}
```

---

## AI CONVERSATION ARCHITECTURE

```
CONVERSATION MODEL:
  search_session.session_id = conversation_id

  Conversation state stored in PostgreSQL:
  - ai_query records (ordered by created_at)
  - ai_response records
  - Max 20 turns per conversation
  - Auto-summarize jika melebihi 20 turns (summary stored in session.device_info)

MULTI-TURN CONTEXT MANAGEMENT:
  Turn 1: User asks → full RAG pipeline
  Turn 2: Follow-up → include previous exchange in context
          Context: [previous Q&A summary] + [new RAG results]
  Turn N: If context too long (> 4096 tokens):
          Summarize earlier turns → compressed conversation history

FOLLOW-UP QUERY HANDLING:
  Detect coreference: "itu", "tersebut", "di sana", "mereka"
  Resolve coreference dari previous turn context
  Contoh: "Siapa yang menandatangani?" (after seeing APHT)
  → Resolved to: "Siapa yang menandatangani APHT No. 45/2024?"

CONVERSATION ISOLATION:
  Setiap conversation_id memiliki filter context sendiri
  User tidak bisa mengakses conversation lain
  Enforced di query level: WHERE user_id = {currentUserId}
```

---

## ASYNC INGESTION ARCHITECTURE

```
ASYNC PIPELINE DESIGN:

  Upload → Response (immediate) → Background pipeline → Doc becomes searchable

  Upload Response Time Target: < 2 seconds
  Full Pipeline Time Target:
    Small doc (< 10 halaman):   2-5 menit
    Medium doc (10-50 halaman): 5-15 menit
    Large doc (50+ halaman):    15-60 menit

PROGRESS POLLING:
  Client polls: GET /documents/{docId}/status setiap 5 detik
  Server returns: {stage, status, progress_pct, ocrQuality}
  Alternatively: SSE endpoint for real-time progress (future)

  GET /documents/{docId}/status/stream   (optional, future feature)
  → SSE stream dengan pipeline progress events

QUEUE PRIORITY:
  CRITICAL (priority 1): docs flagged untuk urgent processing
  HIGH     (priority 2): recent uploads (< 1 jam)
  NORMAL   (priority 3): regular uploads
  LOW      (priority 4): re-indexing jobs, bulk operations
```

---

## SECURITY MODEL

### JWT Token Design

```
TOKEN PAYLOAD:
  {
    "sub": "userId",           user identifier
    "username": "annisa.m",
    "role": "NOTARIS",
    "allowedKlasifikasi": ["PUBLIC","INTERNAL","CONFIDENTIAL"],
    "sessionId": "uuid",
    "iat": 1716460800,
    "exp": 1716464400         1 jam access token
  }

TOKEN TYPES:
  Access Token:  15 menit - 1 jam (configurable)
  Refresh Token: 7 hari (stored in DB, can be revoked)

RS256 ALGORITHM:
  Private key: Spring Boot app (sign only)
  Public key: distributed ke semua services yang perlu verify
```

### RBAC Enforcement Layers

```
LAYER 1 — Spring Security (endpoint level)
  @PreAuthorize("hasRole('NOTARIS') or hasRole('PIMPINAN')")
  Applied at Controller method level

LAYER 2 — Service layer (data level)
  RbacService.assertCanAccess(docId, currentUser)
  Checks: DOC_MASTER.KLASIFIKASI vs user's allowedKlasifikasi

LAYER 3 — Oracle VPD (database level)
  VPD Policy function attached to DOC_MASTER, AKTA_MASTER
  Filters rows server-side before data reaches application
  Even if application bug bypasses Layer 1-2, Oracle VPD ensures data security

LAYER 4 — Qdrant filter (vector level)
  AccessFilterBuilder.build(currentUser) → mandatory Qdrant filter
  Injected by RetrievalOrchestrationService
  Cannot be overridden by user request parameters

LAYER 5 — Response masking (presentation level)
  SensitiveMaskingAspect applied via @Around on Controller methods
  Masks fields based on UserRole dari NotaristPrincipal
```

### API Security Headers

```
Required request headers:
  Authorization: Bearer <JWT>

Required response headers:
  X-Request-Id: <UUID>          untuk tracing
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Strict-Transport-Security: max-age=31536000

Rate limiting headers (response):
  X-RateLimit-Limit: 60
  X-RateLimit-Remaining: 45
  X-RateLimit-Reset: 1716464400
```

---

## API VERSIONING STRATEGY

```
STRATEGY: URL-based versioning (primary) + Header-based (secondary)

URL:    /api/v1/documents
Header: API-Version: 1  (optional, for clients that prefer header-based)

VERSION LIFECYCLE:
  v1 — current (production)
  v2 — future (when breaking changes needed)
  Max 2 active versions simultaneously
  Deprecation notice: 6 months before version sunset
  Sunset header: Sunset: Sat, 31 Dec 2027 00:00:00 GMT

VERSIONING RULES:
  Breaking change → new major version required:
    - Remove field from response
    - Change field type
    - Change endpoint path
    - Change auth mechanism

  Non-breaking → same version, backward-compatible:
    - Add optional field to response
    - Add optional query parameter
    - Add new endpoint
    - Performance improvements

RESPONSE INCLUDES API VERSION:
  "meta": {
    "apiVersion": "1.0",
    "deprecationWarning": null
  }
```

---

## RECOMMENDATION

### REC-01 — Mulai dari Core Pipeline API, Bukan Full API Surface
Prioritas implementasi endpoint:

```
Wave 1 (MVP):
  POST /documents/upload
  GET  /documents/{id}/status
  POST /search
  POST /assistant/ask
  GET  /citations/response/{id}

Wave 2 (Feature Complete):
  GET  /regulations/{id}/structure
  GET  /regulations/pasal/{id}
  POST /assistant/ask/stream
  GET  /chunks/{id}/similar
  Audit endpoints

Wave 3 (Admin & Enhancement):
  POST /admin/reindex
  POST /ingest/review/{id}/approve
  GET  /assistant/conversations
  GET  /admin/pipeline/health
```

### REC-02 — Ambiguity: Modular Monolith vs Service Boundary
Saat ini didesain sebagai modular monolith. Jika di kemudian hari perlu
dipisah, batas modul yang sudah ada memudahkan ekstraksi ke microservice.
**Batas yang paling likely dipisah pertama:** `notarist-ingest` (OCR/NER
intensive, butuh dedicated resource) dan `notarist-assistant` (LLM inference,
butuh GPU isolation).

### REC-03 — PostgreSQL as Queue vs External Queue
SKIP LOCKED di PostgreSQL cukup untuk skala awal. Jika:
- Volume upload > 100 dokumen/hari → pertimbangkan RabbitMQ
- Latency pipeline kritis → pertimbangkan Redis Streams
Saat ini PostgreSQL queue lebih sederhana untuk deployment dan monitoring.

### REC-04 — IndoBERT Sidecar via REST
Python sidecar (IndoBERT NER) berkomunikasi via HTTP REST ke Spring Boot.
Ini memungkinkan:
- Model update tanpa restart Spring Boot
- Isolated resource management (Python process memory)
- Potensi scale-out IndoBERT secara independen

### REC-05 — Regulation Hierarchy Performance
`GET /regulations/{id}/structure` bisa sangat berat untuk UU panjang.
Rekomendasi:
- Implement lazy loading: GET /regulations/{id}/bab (dulu), lalu expand per BAB
- Cache structure di Redis/in-memory setelah pertama kali dibangun
- Pagination untuk pasal per BAB

### REC-06 — Ambiguity: Object Storage (MinIO vs Filesystem)
STEP 4 mengasumsikan MinIO (S3-compatible). Jika deployment bersifat
fully on-premise tanpa MinIO, dapat diganti dengan:
- Local filesystem mount (dengan backup strategy)
- NAS/SAN share
Konfirmasi storage strategy sebelum STEP 5 (implementasi).

---

## STATUS

```
STEP 1 — ANALYZE NEW DOMAIN         ✅ COMPLETE
STEP 2 — DDL DESIGN                 ✅ COMPLETE
STEP 3 — INGESTION & RETRIEVAL ARCH ✅ COMPLETE
STEP 4 — API & SERVICE ARCH         ✅ COMPLETE (pending approval)
STEP 5 — FRONTEND SCREENS           ⏸ WAITING APPROVAL
STEP 6 — BACKEND IMPLEMENTATION     ⏸ WAITING APPROVAL
STEP 7 — RAG PIPELINE IMPLEMENTATION⏸ WAITING APPROVAL
```

**2 Ambiguity untuk konfirmasi sebelum STEP 5 (Frontend Screens):**

1. **Object Storage** → MinIO? Local filesystem? NAS? Ini menentukan upload UX di mobile.
2. **Streaming di Mobile** → React Native mendukung SSE via EventSource polyfill.
   Apakah AI response di mobile perlu streaming (token-by-token) atau cukup non-streaming
   (tunggu response penuh, lalu tampilkan)?

---

*Generated by: NOTARIST RAG PLATFORM — ANALYSIS_FIRST mode*
*File: /generated/docs/step4_api_service_architecture.md*
*Date: 2026-05-23*
