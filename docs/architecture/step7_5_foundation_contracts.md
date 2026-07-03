# STEP 7.5 — FOUNDATION CONTRACT GENERATION
# NOTARIST RAG PLATFORM

**Version:** v1.0  
**Date:** 2026-05-24  
**Status:** CONTRACT FREEZE — Awaiting approval before STEP 8 implementation  
**Mode:** ANALYSIS_FIRST | CONTRACT-FIRST | OPENAPI-FIRST | CITATION-FIRST | AUDIT-FIRST  
**Classification:** INTERNAL  
**Predecessor:** STEP 7 — Backend Implementation Architecture (approved)

---

## SUMMARY

STEP 7.5 membekukan seluruh contract, boundary, dan naming convention  
sebelum implementation code digenerate pada STEP 8.

**Scope freeze ini mencakup 20 area:**

| # | Area | Status |
|---|---|---|
| 1 | OpenAPI Specification Structure | FROZEN |
| 2 | API Naming Convention | FROZEN |
| 3 | DTO Contract Specification | FROZEN |
| 4 | Event JSON Schema | FROZEN |
| 5 | Queue Payload Contract | FROZEN |
| 6 | Gradle Multi-Module Structure | FROZEN |
| 7 | Dependency Graph | FROZEN |
| 8 | Shared Library Policy | FROZEN |
| 9 | Package Naming Convention | FROZEN |
| 10 | Error Code Taxonomy | FROZEN |
| 11 | Standard Response Contract | FROZEN |
| 12 | SSE Event Contract | FROZEN |
| 13 | Environment Variable Contract | FROZEN |
| 14 | Config Namespace Strategy | FROZEN |
| 15 | Correlation-ID Propagation Contract | FROZEN |
| 16 | Audit Log Schema | FROZEN |
| 17 | AI Response Contract | FROZEN |
| 18 | Citation Response Contract | FROZEN |
| 19 | Pagination & Filtering Contract | FROZEN |
| 20 | Versioning Strategy | FROZEN |

**Architecture ground truth (dari STEP 7):**
- Modular monolith, 8 modules: core, auth, document, ingest, search, assistant, regulation, audit
- Oracle 19C (3 schemas) + PostgreSQL + Qdrant + MinIO
- PostgreSQL SKIP LOCKED queue (5 pipeline stages)
- JWT RS256 + Oracle VPD + Qdrant payload filter
- BGE-M3 1024-dim embeddings
- BM25 TOP-20 + Qdrant TOP-20 → RRF(k=60) → Reranker → TOP-5
- Citation-first: response tidak valid tanpa source chunk
- SSE streaming sebagai default AI response mode

---

## TABLE OF CONTENTS

1. [OpenAPI Specification Structure](#1-openapi-specification-structure)
2. [API Naming Convention](#2-api-naming-convention)
3. [DTO Contract Specification](#3-dto-contract-specification)
4. [Event JSON Schema](#4-event-json-schema)
5. [Queue Payload Contract](#5-queue-payload-contract)
6. [Gradle Multi-Module Structure](#6-gradle-multi-module-structure)
7. [Dependency Graph](#7-dependency-graph)
8. [Shared Library Policy](#8-shared-library-policy)
9. [Package Naming Convention](#9-package-naming-convention)
10. [Error Code Taxonomy](#10-error-code-taxonomy)
11. [Standard Response Contract](#11-standard-response-contract)
12. [SSE Event Contract](#12-sse-event-contract)
13. [Environment Variable Contract](#13-environment-variable-contract)
14. [Config Namespace Strategy](#14-config-namespace-strategy)
15. [Correlation-ID Propagation Contract](#15-correlation-id-propagation-contract)
16. [Audit Log Schema](#16-audit-log-schema)
17. [AI Response Contract](#17-ai-response-contract)
18. [Citation Response Contract](#18-citation-response-contract)
19. [Pagination & Filtering Contract](#19-pagination--filtering-contract)
20. [Versioning Strategy](#20-versioning-strategy)

---

## 1. OPENAPI SPECIFICATION STRUCTURE

### 1.1 File Layout

```
/generated/openapi/
├── notarist-api.yaml                    # Master spec — imports all module specs
├── components/
│   ├── schemas/
│   │   ├── common.yaml                  # ApiResponse, ApiMeta, ApiError, Page
│   │   ├── document.yaml                # DocumentLegalResponse, DocumentSummary
│   │   ├── ingest.yaml                  # IngestionJobResponse, UploadUrlResponse
│   │   ├── search.yaml                  # SearchRequest, SearchResponse, RetrievalResult
│   │   ├── assistant.yaml               # AssistantRequest, AssistantResponse, Citation
│   │   ├── regulation.yaml              # RegulasiResponse, PasalResponse
│   │   ├── auth.yaml                    # LoginRequest, TokenResponse, RefreshRequest
│   │   └── audit.yaml                   # AuditTrailResponse, AuditEntryResponse
│   ├── parameters/
│   │   ├── common-params.yaml           # page, size, sort, correlationId header
│   │   └── path-params.yaml             # documentId, jobId, sessionId
│   ├── responses/
│   │   ├── 400.yaml                     # Bad Request standard response
│   │   ├── 401.yaml                     # Unauthorized
│   │   ├── 403.yaml                     # Forbidden
│   │   ├── 404.yaml                     # Not Found
│   │   ├── 409.yaml                     # Conflict (duplicate, lock)
│   │   ├── 422.yaml                     # Unprocessable Entity (validation)
│   │   └── 500.yaml                     # Internal Server Error
│   └── securitySchemes/
│       └── bearerAuth.yaml              # JWT RS256 bearer scheme
└── paths/
    ├── auth.yaml                        # /api/v1/auth paths
    ├── documents.yaml                   # /api/v1/documents paths
    ├── ingest.yaml                      # /api/v1/ingest paths
    ├── search.yaml                      # /api/v1/search paths
    ├── assistant.yaml                   # /api/v1/assistant paths
    ├── regulations.yaml                 # /api/v1/regulations paths
    ├── audit.yaml                       # /api/v1/audit paths
    └── admin.yaml                       # /api/v1/admin paths
```

### 1.2 Master Spec Header

```yaml
# notarist-api.yaml
openapi: "3.1.0"
info:
  title: NOTARIST RAG Platform API
  version: "1.0.0"
  description: |
    Internal API untuk NOTARIST RAG Platform.
    Sistem knowledge retrieval berbasis AI untuk dokumen hukum notaris dan PPAT.
  contact:
    name: NOTARIST Platform Team
  license:
    name: INTERNAL — Not for public distribution

servers:
  - url: http://localhost:8080
    description: Local development
  - url: https://notarist-dev.internal
    description: Dev environment
  - url: https://notarist-staging.internal
    description: Staging environment

tags:
  - name: auth
    description: Authentication & session management
  - name: documents
    description: Document lifecycle & metadata
  - name: ingest
    description: Document upload & ingestion pipeline
  - name: search
    description: Hybrid retrieval & semantic search
  - name: assistant
    description: AI assistant & RAG response (SSE)
  - name: regulations
    description: Regulasi & SOP hierarchy navigation
  - name: audit
    description: Audit trail & compliance log
  - name: admin
    description: Admin operations (ADMIN role only)

security:
  - bearerAuth: []
```

### 1.3 OpenAPI-First Workflow Rule

```
WAJIB: Spec ditulis DULU sebelum controller dibuat.
Urutan:
  1. Tulis schema di components/schemas/
  2. Tulis path di paths/
  3. Generate controller interface via openapi-generator-gradle-plugin
  4. Controller implements generated interface
  5. TIDAK BOLEH edit generated controller interface — edit spec lalu re-generate
```

### 1.4 Tag-to-Module Mapping

| OpenAPI Tag | Spring Module | Base Path |
|---|---|---|
| `auth` | notarist-auth | `/api/v1/auth` |
| `documents` | notarist-document | `/api/v1/documents` |
| `ingest` | notarist-ingest | `/api/v1/ingest` |
| `search` | notarist-search | `/api/v1/search` |
| `assistant` | notarist-assistant | `/api/v1/assistant` |
| `regulations` | notarist-regulation | `/api/v1/regulations` |
| `audit` | notarist-audit | `/api/v1/audit` |
| `admin` | notarist-web | `/api/v1/admin` |

---

## 2. API NAMING CONVENTION

### 2.1 URL Pattern Rules

| Rule | Pattern | Example |
|---|---|---|
| Base prefix | `/api/v{N}/` | `/api/v1/` |
| Resource collection | plural kebab-case | `/api/v1/documents` |
| Resource instance | `/{resourceId}` | `/api/v1/documents/{documentId}` |
| Sub-resource | `/{id}/sub-resource` | `/api/v1/documents/{documentId}/chunks` |
| Action (non-CRUD) | `/{id}/action` verb | `/api/v1/ingest/jobs/{jobId}/retry` |
| Async job status | `/{id}/status` | `/api/v1/ingest/jobs/{jobId}/status` |
| Upload URL | noun-first | `/api/v1/ingest/upload-url` |
| Stream endpoint | `/stream` suffix | `/api/v1/assistant/stream` |
| Admin operations | `/admin/` prefix | `/api/v1/admin/queue/status` |

### 2.2 HTTP Method Semantics

| Method | Semantic | Idempotent | Body |
|---|---|---|---|
| `GET` | Read resource / list | Yes | No |
| `POST` | Create or execute action | No | Yes |
| `PUT` | Full replacement (versioned docs) | Yes | Yes |
| `PATCH` | Partial update | No | Yes |
| `DELETE` | Soft delete only (legal docs never hard delete) | Yes | No |

### 2.3 Complete Endpoint Inventory

#### Auth — `/api/v1/auth`

| Method | Path | Description | Roles |
|---|---|---|---|
| POST | `/login` | Authenticate user | Public |
| POST | `/refresh` | Refresh access token | Public (with refresh token) |
| POST | `/logout` | Invalidate session | Authenticated |
| GET | `/me` | Current user info | Authenticated |

#### Documents — `/api/v1/documents`

| Method | Path | Description | Roles |
|---|---|---|---|
| GET | `/` | List documents (paginated) | STAFF+ |
| GET | `/{documentId}` | Get document detail | STAFF+ |
| GET | `/{documentId}/chunks` | List document chunks | STAFF+ |
| GET | `/{documentId}/entities` | List NER entities | NOTARIS+ |
| GET | `/{documentId}/download-url` | Get signed download URL | STAFF+ |
| PATCH | `/{documentId}/metadata` | Update metadata (pre-index only) | NOTARIS+ |

#### Ingest — `/api/v1/ingest`

| Method | Path | Description | Roles |
|---|---|---|---|
| POST | `/upload-url` | Request signed upload URL | STAFF+ |
| POST | `/jobs/{jobId}/confirm` | Confirm upload completed | STAFF+ |
| GET | `/jobs/{jobId}/status` | Poll ingestion status | STAFF+ |
| POST | `/jobs/{jobId}/retry` | Retry failed job (specific stage) | ADMIN |
| GET | `/jobs` | List all ingestion jobs | NOTARIS+ |

#### Search — `/api/v1/search`

| Method | Path | Description | Roles |
|---|---|---|---|
| POST | `/hybrid` | Hybrid BM25 + semantic search | STAFF+ |
| POST | `/semantic` | Semantic-only search | STAFF+ |
| POST | `/regulation` | Regulation hierarchy search | STAFF+ |
| POST | `/sop` | SOP step-based search | STAFF+ |

#### Assistant — `/api/v1/assistant`

| Method | Path | Description | Roles |
|---|---|---|---|
| POST | `/sessions` | Create assistant session | STAFF+ |
| GET | `/sessions/{sessionId}` | Get session history | STAFF+ |
| POST | `/sessions/{sessionId}/query` | Submit query (non-streaming) | STAFF+ |
| GET | `/sessions/{sessionId}/stream` | SSE streaming query | STAFF+ |
| DELETE | `/sessions/{sessionId}` | End session | STAFF+ |

#### Regulations — `/api/v1/regulations`

| Method | Path | Description | Roles |
|---|---|---|---|
| GET | `/` | List all regulasi | STAFF+ |
| GET | `/{regulasiId}` | Get regulasi detail | STAFF+ |
| GET | `/{regulasiId}/bab` | List BAB | STAFF+ |
| GET | `/{regulasiId}/bab/{babId}/pasal` | List Pasal in BAB | STAFF+ |
| GET | `/pasal/{pasalId}` | Get specific Pasal | STAFF+ |
| GET | `/pasal/{pasalId}/citations` | Citation usages of Pasal | NOTARIS+ |

#### Audit — `/api/v1/audit`

| Method | Path | Description | Roles |
|---|---|---|---|
| GET | `/trail` | List audit entries (paginated) | PIMPINAN, ADMIN |
| GET | `/trail/{auditId}` | Get audit entry detail | PIMPINAN, ADMIN |
| GET | `/trail/document/{documentId}` | Audit by document | PIMPINAN, ADMIN |
| GET | `/trail/user/{userId}` | Audit by user | ADMIN |

#### Admin — `/api/v1/admin`

| Method | Path | Description | Roles |
|---|---|---|---|
| GET | `/queue/status` | Queue depth per stage | ADMIN |
| GET | `/queue/dlq` | Dead letter queue entries | ADMIN |
| POST | `/queue/dlq/{dlqId}/replay` | Replay DLQ entry | ADMIN |
| POST | `/ingestion/jobs/{jobId}/replay` | Force replay specific job | ADMIN |
| GET | `/health/sidecars` | Sidecar health status | ADMIN |

### 2.4 JSON Field Naming Convention

| Context | Convention | Example |
|---|---|---|
| JSON request/response fields | `camelCase` | `documentId`, `createdAt`, `nomorAkta` |
| URL path parameters | `camelCase` | `{documentId}`, `{sessionId}` |
| URL query parameters | `camelCase` | `?pageSize=20&sortBy=createdAt` |
| OpenAPI schema names | `PascalCase` | `DocumentLegalResponse`, `SearchRequest` |
| Event type strings | `dot.separated.lower` | `document.uploaded`, `ocr.completed` |
| Error codes | `SCREAMING_SNAKE_CASE` | `DOCUMENT_NOT_FOUND`, `OCR_FAILURE` |
| Enum values (JSON) | `SCREAMING_SNAKE_CASE` | `"STRICTLY_CONFIDENTIAL"`, `"OCR_QUEUE"` |
| Database columns | `UPPER_SNAKE_CASE` | `DOCUMENT_ID`, `CREATED_AT` |
| Config properties | `lower.dot.notation` | `notarist.security.jwt.access-token-ttl-minutes` |
| Environment variables | `SCREAMING_SNAKE_CASE` | `ORACLE_PASSWORD`, `QDRANT_API_KEY` |

### 2.5 Timestamp Convention

| Rule | Value |
|---|---|
| Format | ISO-8601 with UTC offset: `2026-05-24T10:30:00.000Z` |
| Timezone | Always UTC in API response |
| Storage | Oracle: `TIMESTAMP WITH TIME ZONE`, PostgreSQL: `TIMESTAMPTZ` |
| Field suffix | Always `At` for timestamps: `createdAt`, `completedAt`, `indexedAt` |

### 2.6 ID Convention

| Type | Format | Example |
|---|---|---|
| All resource IDs (API) | UUID v4, lowercase hyphenated | `"3fa85f64-5717-4562-b3fc-2c963f66afa6"` |
| Oracle PK | `VARCHAR2(36)` UUID | Same format |
| PostgreSQL PK | `UUID` type | Same format |
| Queue job IDs | UUID v4 | Same format |
| Correlation ID | UUID v4 | Same format |

---

## 3. DTO CONTRACT SPECIFICATION

### 3.1 DTO Naming Convention

| DTO Type | Suffix | Example |
|---|---|---|
| Create request | `CreateRequest` | `InitiateIngestionRequest` |
| Update request | `UpdateRequest` | `UpdateDocumentMetadataRequest` |
| Query request | `Request` | `SearchRequest`, `LoginRequest` |
| List response | `Response` (plural context) | `DocumentListResponse` |
| Detail response | `Response` | `DocumentLegalResponse` |
| Summary (in lists) | `Summary` | `DocumentSummary` |
| Embedded nested | `Detail` | `CitationDetail`, `ChunkDetail` |

### 3.2 Auth DTOs

```
LoginRequest {
  username:    String  [NotNull, Size(3..50)]
  password:    String  [NotNull, Size(8..100)]
}

TokenResponse {
  accessToken:   String   — JWT RS256
  refreshToken:  String   — Opaque UUID
  tokenType:     String   — Always "Bearer"
  expiresIn:     Integer  — Seconds until access token expiry (900)
  userId:        UUID
  roles:         List<String>
  tenantId:      UUID
}

RefreshTokenRequest {
  refreshToken:  String  [NotNull]
}

UserProfileResponse {
  userId:        UUID
  username:      String
  fullName:      String
  email:         String  [masked if S2-classified]
  roles:         List<String>
  tenantId:      UUID
  lastLoginAt:   String  [ISO-8601]
}
```

### 3.3 Document DTOs

```
DocumentSummary {
  documentId:          UUID
  documentTitle:       String
  documentType:        String  [AKTA | REGULASI | SOP]
  jenisAkta:           String  [nullable — APHT | SKMHT | FIDUSIA | ROYA | AJB | ...]
  nomorAkta:           String  [nullable]
  classificationLevel: String  [PUBLIC | INTERNAL | CONFIDENTIAL | STRICTLY_CONFIDENTIAL]
  status:              String  [UPLOADED | OCR_QUEUE | INDEXED | FAILED]
  indexedAt:           String  [ISO-8601, nullable]
  createdAt:           String  [ISO-8601]
  notarisId:           UUID    [nullable]
}

DocumentLegalResponse {
  documentId:          UUID
  documentTitle:       String
  documentType:        String
  jenisAkta:           String  [nullable]
  nomorAkta:           String  [nullable]
  tanggalAkta:         String  [date only: YYYY-MM-DD, nullable]
  classificationLevel: String
  status:              String
  pageCount:           Integer [nullable]
  fileSizeBytes:       Long    [nullable]
  mimeType:            String
  minioObjectKey:      String  [for internal reference only]
  entities:            EntitySummary [nullable — requires NOTARIS+ role]
  notaris:             NotarisSummary [nullable]
  createdAt:           String
  indexedAt:           String  [nullable]
  versionNumber:       Integer
}

EntitySummary {
  personCount:     Integer
  notarisCount:    Integer
  nikCount:        Integer  [always 0 in response — S1 encrypted, not exposed]
  nomorAktaCount:  Integer
  alamatCount:     Integer  [masked for STAFF role]
  tanggalCount:    Integer
}

NotarisSummary {
  notarisId:   UUID
  namaNotaris: String
  nomorSk:     String
  kotaWilayah: String
}

ChunkDetail {
  chunkId:          UUID
  documentId:       UUID
  chunkIndex:       Integer
  chunkText:        String  [PII redacted]
  tokenCount:       Integer
  chunkStrategy:    String  [KLAUSUL_BASED | HIERARCHY_BASED | STEP_BASED]
  metadata: {
    pageNumber:     Integer [nullable]
    sectionTitle:   String  [nullable]
    pasalRef:       String  [nullable, for REGULASI only]
  }
}
```

### 3.4 Ingest DTOs

```
InitiateIngestionRequest {
  originalFilename:    String  [NotNull, Size(1..255)]
  documentType:        String  [NotNull, Enum(AKTA|REGULASI|SOP)]
  jenisAkta:           String  [nullable, Enum(...)]
  mimeType:            String  [NotNull, Pattern("application/pdf")]
  fileSizeBytes:       Long    [NotNull, Min(1), Max(52428800)]  — 50MB max
  classificationLevel: String  [NotNull, Enum(PUBLIC|INTERNAL|CONFIDENTIAL|STRICTLY_CONFIDENTIAL)]
  checksumSha256:      String  [NotNull, Size(64,64)]
}

UploadUrlResponse {
  jobId:         UUID
  signedUrl:     String   — pre-signed MinIO PUT URL (TTL 15 minutes)
  objectKey:     String   — MinIO object path
  expiresAt:     String   — ISO-8601 expiry timestamp
  uploadHeaders: Map<String, String>  — required headers for MinIO PUT
}

ConfirmUploadRequest {
  checksumSha256:  String  [NotNull, Size(64,64)]  — re-verify on confirm
}

IngestionJobStatusResponse {
  jobId:           UUID
  documentId:      UUID
  currentStage:    String  [UPLOAD_CONFIRMED|OCR_QUEUE|OCR_PROCESSING|NER_QUEUE|...]
  status:          String  [PENDING|PROCESSING|COMPLETED|FAILED|DLQ]
  progress: {
    completedStages:  Integer
    totalStages:      Integer  — always 5
    percentComplete:  Integer  — 0..100
  }
  stageHistory:    List<StageRecord>
  failureReason:   String  [nullable]
  createdAt:       String
  updatedAt:       String
  completedAt:     String  [nullable]
}

StageRecord {
  stage:        String
  status:       String
  startedAt:    String  [nullable]
  completedAt:  String  [nullable]
  durationMs:   Long    [nullable]
  attemptCount: Integer
}
```

### 3.5 Search DTOs

```
HybridSearchRequest {
  queryText:    String  [NotNull, Size(1..500)]
  intent:       String  [Default(HYBRID), Enum(HYBRID|SEMANTIC|KEYWORD|REGULATION|SOP)]
  filters: {
    documentType:        String  [nullable, Enum]
    jenisAkta:           String  [nullable, Enum]
    classificationLevel: String  [nullable, Enum]
    notarisId:           UUID    [nullable]
    dateFrom:            String  [nullable, YYYY-MM-DD]
    dateTo:              String  [nullable, YYYY-MM-DD]
  }
  topK:         Integer  [Default(5), Min(1), Max(20)]
  includeScores: Boolean [Default(false)]
}

SearchResponse {
  queryText:     String
  intent:        String
  results:       List<RetrievalResultResponse>
  totalCandidates: Integer  — before reranking
  retrievalMs:   Long
  rerankMs:      Long
}

RetrievalResultResponse {
  chunkId:          UUID
  documentId:       UUID
  documentTitle:    String
  documentType:     String
  jenisAkta:        String  [nullable]
  nomorAkta:        String  [nullable]
  chunkText:        String  [PII redacted, field-masked by role]
  chunkIndex:       Integer
  rank:             Integer  — final rank (1-based)
  scores: {
    bm25:           Float    [nullable if includeScores=false]
    cosine:         Float    [nullable if includeScores=false]
    rrfScore:       Float    [nullable if includeScores=false]
    rerankerScore:  Float    [nullable if includeScores=false]
  }
  metadata: {
    tanggalAkta:    String  [nullable]
    notarisId:      UUID    [nullable]
    sectionTitle:   String  [nullable]
    pasalRef:       String  [nullable]
    pageNumber:     Integer [nullable]
  }
  sourceObjectKey:  String  — MinIO reference for full document
}
```

### 3.6 Assistant DTOs

```
CreateSessionRequest {
  sessionTitle:  String  [nullable, Size(0..100)]
  context: {
    documentIds:   List<UUID>  [nullable — pre-filter context to specific docs]
    jenisAkta:     String      [nullable — pre-filter by akta type]
  }
}

SessionResponse {
  sessionId:   UUID
  userId:      UUID
  title:       String
  createdAt:   String
  lastActiveAt: String
  turnCount:   Integer
  context:     SessionContext
}

AssistantQueryRequest {
  queryText:   String   [NotNull, Size(1..1000)]
  stream:      Boolean  [Default(true)]
  topK:        Integer  [Default(5), Min(1), Max(10)]
  searchIntent: String  [Default(HYBRID)]
}

AssistantResponse {
  — see Section 17 (AI Response Contract)
}
```

### 3.7 Regulation DTOs

```
RegulasiSummary {
  regulasiId:     UUID
  nomorRegulasi:  String
  judulRegulasi:  String
  jenisRegulasi:  String  [UU | PP | PERMEN | PERDA | SK | SE]
  tanggalBerlaku: String  [YYYY-MM-DD]
  status:         String  [BERLAKU | DICABUT | DIUBAH]
  babCount:       Integer
  pasalCount:     Integer
}

PasalResponse {
  pasalId:          UUID
  regulasiId:       UUID
  babId:            UUID
  nomorBab:         String
  judulBab:         String
  nomorPasal:       String
  judulPasal:       String  [nullable]
  isiPasal:         String  [full text — masked if STRICTLY_CONFIDENTIAL and role < NOTARIS]
  ayatList:         List<AyatDetail>
  amendmentHistory: List<AmendmentRecord>
  citationCount:    Integer
}

AyatDetail {
  nomorAyat:  String
  isiAyat:    String
}

AmendmentRecord {
  amendedBy:    String  — nomor regulasi yang mengubah
  amendedAt:    String  [YYYY-MM-DD]
  changeType:   String  [CHANGED | DELETED | ADDED]
  description:  String
}
```

---

## 4. EVENT JSON SCHEMA

All events conform to the base `DomainEvent` schema.

### 4.1 Base Event Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://notarist.internal/schemas/event/base",
  "title": "DomainEvent",
  "type": "object",
  "required": ["eventId", "eventType", "eventVersion", "timestamp", "correlationId", "traceId", "payload"],
  "properties": {
    "eventId":       { "type": "string", "format": "uuid" },
    "eventType":     { "type": "string", "pattern": "^[a-z]+\\.[a-z.]+$" },
    "eventVersion":  { "type": "string", "enum": ["1.0"] },
    "timestamp":     { "type": "string", "format": "date-time" },
    "correlationId": { "type": "string", "format": "uuid" },
    "traceId":       { "type": "string", "format": "uuid" },
    "publishedBy":   { "type": "string" },
    "payload":       { "type": "object" }
  }
}
```

### 4.2 `document.uploaded` Schema

```json
{
  "$id": "https://notarist.internal/schemas/event/document.uploaded",
  "allOf": [{ "$ref": "base" }],
  "properties": {
    "eventType": { "const": "document.uploaded" },
    "payload": {
      "type": "object",
      "required": ["jobId","documentId","objectKey","originalFilename","documentType",
                   "mimeType","checksumSha256","fileSizeBytes","uploadedBy","classificationLevel","tenantId"],
      "properties": {
        "jobId":               { "type": "string", "format": "uuid" },
        "documentId":          { "type": "string", "format": "uuid" },
        "objectKey":           { "type": "string", "pattern": "^notarist-raw/.+" },
        "originalFilename":    { "type": "string", "maxLength": 255 },
        "documentType":        { "type": "string", "enum": ["AKTA","REGULASI","SOP"] },
        "jenisAkta":           { "type": ["string","null"],
                                 "enum": ["APHT","SKMHT","FIDUSIA","ROYA","AJB","APJB",
                                          "APH","WASIAT","WAARMERKING","LEGALISASI","KUASA",null] },
        "mimeType":            { "type": "string", "const": "application/pdf" },
        "checksumSha256":      { "type": "string", "pattern": "^[a-f0-9]{64}$" },
        "fileSizeBytes":       { "type": "integer", "minimum": 1, "maximum": 52428800 },
        "uploadedBy":          { "type": "string", "format": "uuid" },
        "classificationLevel": { "type": "string",
                                 "enum": ["PUBLIC","INTERNAL","CONFIDENTIAL","STRICTLY_CONFIDENTIAL"] },
        "tenantId":            { "type": "string", "format": "uuid" }
      }
    }
  }
}
```

### 4.3 `ocr.completed` Schema

```json
{
  "$id": "https://notarist.internal/schemas/event/ocr.completed",
  "allOf": [{ "$ref": "base" }],
  "properties": {
    "eventType": { "const": "ocr.completed" },
    "payload": {
      "required": ["jobId","documentId","ocrObjectKey","pageCount","ocrEngine","processingMs"],
      "properties": {
        "jobId":               { "type": "string", "format": "uuid" },
        "documentId":          { "type": "string", "format": "uuid" },
        "ocrObjectKey":        { "type": "string", "pattern": "^notarist-ocr/.+" },
        "pageCount":           { "type": "integer", "minimum": 1 },
        "extractedTextLength": { "type": "integer", "minimum": 0 },
        "ocrEngine":           { "type": "string", "const": "PaddleOCR-v4" },
        "confidenceAvg":       { "type": "number", "minimum": 0.0, "maximum": 1.0 },
        "processingMs":        { "type": "integer", "minimum": 0 },
        "ocrWarnings":         { "type": "array", "items": { "type": "string" } }
      }
    }
  }
}
```

### 4.4 `ner.completed` Schema

```json
{
  "$id": "https://notarist.internal/schemas/event/ner.completed",
  "properties": {
    "eventType": { "const": "ner.completed" },
    "payload": {
      "required": ["jobId","documentId","nerObjectKey","processingMs"],
      "properties": {
        "jobId":          { "type": "string", "format": "uuid" },
        "documentId":     { "type": "string", "format": "uuid" },
        "nerObjectKey":   { "type": "string", "pattern": "^notarist-processed/.+" },
        "entitiesExtracted": {
          "type": "object",
          "properties": {
            "PERSON":       { "type": "integer" },
            "NOTARIS":      { "type": "integer" },
            "PPAT":         { "type": "integer" },
            "NOMOR_AKTA":   { "type": "integer" },
            "NIK":          { "type": "integer" },
            "NPWP":         { "type": "integer" },
            "TANGGAL":      { "type": "integer" },
            "ALAMAT":       { "type": "integer" },
            "ORGANISASI":   { "type": "integer" }
          }
        },
        "nerEngine":     { "type": "string", "enum": ["RULE_BASED","INDOBERT","HYBRID"] },
        "piiRedacted":   { "type": "boolean" },
        "processingMs":  { "type": "integer" }
      }
    }
  }
}
```

### 4.5 `chunking.completed` Schema

```json
{
  "$id": "https://notarist.internal/schemas/event/chunking.completed",
  "properties": {
    "eventType": { "const": "chunking.completed" },
    "payload": {
      "required": ["jobId","documentId","totalChunks","chunkStrategy","chunkObjectKey"],
      "properties": {
        "jobId":            { "type": "string", "format": "uuid" },
        "documentId":       { "type": "string", "format": "uuid" },
        "totalChunks":      { "type": "integer", "minimum": 1 },
        "chunkStrategy":    { "type": "string",
                              "enum": ["KLAUSUL_BASED","HIERARCHY_BASED","STEP_BASED"] },
        "avgTokensPerChunk":{ "type": "integer" },
        "minTokens":        { "type": "integer" },
        "maxTokens":        { "type": "integer" },
        "overlapPercent":   { "type": "integer", "minimum": 0, "maximum": 50 },
        "chunkObjectKey":   { "type": "string", "pattern": "^notarist-chunk/.+" },
        "processingMs":     { "type": "integer" }
      }
    }
  }
}
```

### 4.6 `embedding.completed` Schema

```json
{
  "$id": "https://notarist.internal/schemas/event/embedding.completed",
  "properties": {
    "eventType": { "const": "embedding.completed" },
    "payload": {
      "required": ["jobId","documentId","embeddingModel","embeddingDimension","totalVectors"],
      "properties": {
        "jobId":              { "type": "string", "format": "uuid" },
        "documentId":         { "type": "string", "format": "uuid" },
        "embeddingModel":     { "type": "string", "const": "bge-m3" },
        "embeddingDimension": { "type": "integer", "const": 1024 },
        "totalVectors":       { "type": "integer", "minimum": 1 },
        "processingMs":       { "type": "integer" }
      }
    }
  }
}
```

### 4.7 `indexing.completed` Schema

```json
{
  "$id": "https://notarist.internal/schemas/event/indexing.completed",
  "properties": {
    "eventType": { "const": "indexing.completed" },
    "payload": {
      "required": ["jobId","documentId","qdrantCollection","vectorsIndexed","postgresBm25Updated"],
      "properties": {
        "jobId":               { "type": "string", "format": "uuid" },
        "documentId":          { "type": "string", "format": "uuid" },
        "qdrantCollection":    { "type": "string", "const": "notarist_chunks" },
        "vectorsIndexed":      { "type": "integer", "minimum": 1 },
        "postgresBm25Updated": { "type": "boolean" },
        "oracleStatusUpdated": { "type": "boolean" },
        "processingDurationMs":{ "type": "integer" }
      }
    }
  }
}
```

### 4.8 `ai.response.generated` Schema

```json
{
  "$id": "https://notarist.internal/schemas/event/ai.response.generated",
  "properties": {
    "eventType": { "const": "ai.response.generated" },
    "payload": {
      "required": ["sessionId","userId","tenantId","citationCount","tokensInput","tokensOutput","modelId","groundingScore"],
      "properties": {
        "sessionId":            { "type": "string", "format": "uuid" },
        "userId":               { "type": "string", "format": "uuid" },
        "tenantId":             { "type": "string", "format": "uuid" },
        "queryHash":            { "type": "string", "pattern": "^[a-f0-9]{64}$" },
        "citationCount":        { "type": "integer", "minimum": 0 },
        "tokensInput":          { "type": "integer", "minimum": 0 },
        "tokensOutput":         { "type": "integer", "minimum": 0 },
        "modelId":              { "type": "string" },
        "groundingScore":       { "type": "number", "minimum": 0.0, "maximum": 1.0 },
        "hallucinationFlagRaised": { "type": "boolean" },
        "streamMode":           { "type": "string", "enum": ["SSE","BATCH"] },
        "streamDurationMs":     { "type": "integer" },
        "ttftMs":               { "type": "integer",
                                  "description": "Time to first token (ms)" }
      }
    }
  }
}
```

### 4.9 `citation.created` Schema

```json
{
  "$id": "https://notarist.internal/schemas/event/citation.created",
  "properties": {
    "eventType": { "const": "citation.created" },
    "payload": {
      "required": ["citationId","sessionId","chunkId","documentId","userId","verified"],
      "properties": {
        "citationId":    { "type": "string", "format": "uuid" },
        "sessionId":     { "type": "string", "format": "uuid" },
        "chunkId":       { "type": "string", "format": "uuid" },
        "documentId":    { "type": "string", "format": "uuid" },
        "userId":        { "type": "string", "format": "uuid" },
        "tenantId":      { "type": "string", "format": "uuid" },
        "confidence":    { "type": "number", "minimum": 0.0, "maximum": 1.0 },
        "verified":      { "type": "boolean" },
        "citationIndex": { "type": "integer", "minimum": 1 }
      }
    }
  }
}
```

### 4.10 `security.access.denied` Schema

```json
{
  "$id": "https://notarist.internal/schemas/event/security.access.denied",
  "properties": {
    "eventType": { "const": "security.access.denied" },
    "payload": {
      "required": ["userId","resource","action","reason","ipAddress"],
      "properties": {
        "userId":       { "type": ["string","null"], "format": "uuid" },
        "tenantId":     { "type": ["string","null"], "format": "uuid" },
        "resource":     { "type": "string" },
        "action":       { "type": "string" },
        "reason":       { "type": "string",
                          "enum": ["INVALID_JWT","EXPIRED_JWT","INSUFFICIENT_ROLE",
                                   "VPD_VIOLATION","CLASSIFICATION_CLEARANCE","SESSION_INVALIDATED"] },
        "ipAddress":    { "type": "string" },
        "userAgent":    { "type": "string" },
        "endpoint":     { "type": "string" }
      }
    }
  }
}
```

---

## 5. QUEUE PAYLOAD CONTRACT

**Queue table:** `NOTARIST_STG.INGESTION_QUEUE`  
**Payload column type:** `JSONB` (PostgreSQL)

### 5.1 Stage Transition Table

| Stage (Current) | Triggered By | Worker | Next Stage |
|---|---|---|---|
| `UPLOAD_CONFIRMED` | Client confirm API | — | `OCR_QUEUE` |
| `OCR_QUEUE` | enqueue from UPLOAD_CONFIRMED | OcrWorker | `NER_QUEUE` |
| `NER_QUEUE` | ocr.completed event | NerWorker | `CHUNKING_QUEUE` |
| `CHUNKING_QUEUE` | ner.completed event | ChunkingWorker | `EMBEDDING_QUEUE` |
| `EMBEDDING_QUEUE` | chunking.completed event | EmbeddingWorker | `INDEXING_QUEUE` |
| `INDEXING_QUEUE` | embedding.completed event | IndexingWorker | `COMPLETED` |

### 5.2 Queue Payload Schema per Stage

#### `UPLOAD_CONFIRMED` payload

```json
{
  "jobId":               "uuid",
  "documentId":          "uuid",
  "tenantId":            "uuid",
  "uploadedBy":          "uuid",
  "objectKey":           "notarist-raw/<tenantId>/<documentId>/<filename>",
  "checksumSha256":      "64-char hex",
  "fileSizeBytes":       9876543,
  "documentType":        "AKTA",
  "jenisAkta":           "APHT",
  "classificationLevel": "CONFIDENTIAL",
  "correlationId":       "uuid",
  "traceId":             "uuid"
}
```

#### `OCR_QUEUE` payload (adds OCR config)

```json
{
  "jobId":           "uuid",
  "documentId":      "uuid",
  "tenantId":        "uuid",
  "objectKey":       "notarist-raw/...",
  "ocrConfig": {
    "dpi":           300,
    "language":      "id",
    "enhanceContrast": true
  },
  "correlationId":   "uuid",
  "traceId":         "uuid",
  "attemptNumber":   1
}
```

#### `NER_QUEUE` payload

```json
{
  "jobId":          "uuid",
  "documentId":     "uuid",
  "tenantId":       "uuid",
  "ocrObjectKey":   "notarist-ocr/<documentId>/result.json",
  "documentType":   "AKTA",
  "nerConfig": {
    "engine":       "HYBRID",
    "enablePiiRedaction": true,
    "ruleBasedFirst": true
  },
  "correlationId":  "uuid",
  "traceId":        "uuid",
  "attemptNumber":  1
}
```

#### `CHUNKING_QUEUE` payload

```json
{
  "jobId":         "uuid",
  "documentId":    "uuid",
  "tenantId":      "uuid",
  "nerObjectKey":  "notarist-processed/<documentId>/ner.json",
  "documentType":  "AKTA",
  "chunkingConfig": {
    "strategy":    "KLAUSUL_BASED",
    "maxTokens":   800,
    "minTokens":   400,
    "overlapPercent": 15
  },
  "correlationId": "uuid",
  "traceId":       "uuid"
}
```

#### `EMBEDDING_QUEUE` payload

```json
{
  "jobId":          "uuid",
  "documentId":     "uuid",
  "tenantId":       "uuid",
  "chunkObjectKey": "notarist-chunk/<documentId>/chunks.jsonl",
  "totalChunks":    47,
  "embeddingConfig": {
    "model":        "bge-m3",
    "dimension":    1024,
    "batchSize":    16
  },
  "correlationId":  "uuid",
  "traceId":        "uuid"
}
```

#### `INDEXING_QUEUE` payload

```json
{
  "jobId":          "uuid",
  "documentId":     "uuid",
  "tenantId":       "uuid",
  "chunkObjectKey": "notarist-chunk/<documentId>/chunks.jsonl",
  "totalChunks":    47,
  "indexingConfig": {
    "qdrantCollection": "notarist_chunks",
    "upsertMode":       "REPLACE_EXISTING",
    "updatePostgresBm25": true,
    "updateOracleStatus": true
  },
  "correlationId":  "uuid",
  "traceId":        "uuid"
}
```

### 5.3 Queue Status Enum

```
PENDING        — waiting to be processed
PROCESSING     — locked by a worker
COMPLETED      — stage finished successfully
FAILED         — attempt failed, will retry
DLQ            — moved to dead letter queue (max attempts exceeded)
```

---

## 6. GRADLE MULTI-MODULE STRUCTURE

### 6.1 `settings.gradle.kts`

```kotlin
rootProject.name = "notarist-rag"

include(
    "notarist-core",
    "notarist-auth",
    "notarist-document",
    "notarist-ingest",
    "notarist-search",
    "notarist-assistant",
    "notarist-regulation",
    "notarist-audit",
    "notarist-web"
)
```

### 6.2 Root `build.gradle.kts`

```kotlin
plugins {
    java
    id("org.springframework.boot") version libs.versions.springBoot apply false
    id("io.spring.dependency-management") version libs.versions.springDependencyManagement apply false
    id("org.openapi.generator") version libs.versions.openapiGenerator apply false
}

allprojects {
    group = "com.notarist"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    repositories {
        maven {
            url = uri("https://nexus.notarist.internal/repository/maven-public/")
            credentials {
                username = System.getenv("NEXUS_USER")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
        mavenCentral()
    }
}
```

### 6.3 Version Catalog — `gradle/libs.versions.toml`

```toml
[versions]
springBoot                 = "3.2.5"
springDependencyManagement = "1.1.5"
java                       = "17"
openapiGenerator           = "7.5.0"
ojdbc11                    = "23.3.0.23.09"
postgresql                 = "42.7.3"
qdrantClient               = "1.9.1"
minioClient                = "8.5.10"
jjwt                       = "0.12.5"
liquibase                  = "4.27.0"
flyway                     = "10.12.0"
mapstruct                  = "1.5.5.Final"
jackson                    = "2.17.1"
logbackJson                = "0.1.5"
opentelemetry              = "2.4.0"
micrometer                 = "1.12.5"
mockito                    = "5.11.0"
testcontainers             = "1.19.8"
archunit                   = "1.3.0"

[libraries]
# Spring
spring-boot-starter-web          = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-security     = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-data-jpa     = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-jdbc         = { module = "org.springframework.boot:spring-boot-starter-jdbc" }
spring-boot-starter-webflux      = { module = "org.springframework.boot:spring-boot-starter-webflux" }
spring-boot-starter-actuator     = { module = "org.springframework.boot:spring-boot-starter-actuator" }
spring-boot-starter-validation   = { module = "org.springframework.boot:spring-boot-starter-validation" }
spring-boot-starter-test         = { module = "org.springframework.boot:spring-boot-starter-test" }

# Database
ojdbc11                   = { module = "com.oracle.database.jdbc:ojdbc11", version.ref = "ojdbc11" }
postgresql                = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
liquibase-core            = { module = "org.liquibase:liquibase-core", version.ref = "liquibase" }
flyway-core               = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }

# Storage & Vector
qdrant-client             = { module = "io.qdrant:client", version.ref = "qdrantClient" }
minio                     = { module = "io.minio:minio", version.ref = "minioClient" }

# Security
jjwt-api                  = { module = "io.jsonwebtoken:jjwt-api", version.ref = "jjwt" }
jjwt-impl                 = { module = "io.jsonwebtoken:jjwt-impl", version.ref = "jjwt" }
jjwt-jackson              = { module = "io.jsonwebtoken:jjwt-jackson", version.ref = "jjwt" }

# Observability
micrometer-registry-prometheus = { module = "io.micrometer:micrometer-registry-prometheus" }
opentelemetry-sdk         = { module = "io.opentelemetry:opentelemetry-sdk", version.ref = "opentelemetry" }

# Utilities
mapstruct                 = { module = "org.mapstruct:mapstruct", version.ref = "mapstruct" }
mapstruct-processor       = { module = "org.mapstruct:mapstruct-processor", version.ref = "mapstruct" }
logback-json              = { module = "ch.qos.logback.contrib:logback-json-classic", version.ref = "logbackJson" }
jackson-databind          = { module = "com.fasterxml.jackson.core:jackson-databind" }

# Testing
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
testcontainers-oracle     = { module = "org.testcontainers:oracle-xe", version.ref = "testcontainers" }
archunit                  = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }

[plugins]
spring-boot               = { id = "org.springframework.boot", version.ref = "springBoot" }
spring-dependency-mgmt    = { id = "io.spring.dependency-management", version.ref = "springDependencyManagement" }
openapi-generator         = { id = "org.openapi.generator", version.ref = "openapiGenerator" }
```

### 6.4 Module `build.gradle.kts` Pattern

```kotlin
// Example: notarist-ingest/build.gradle.kts
dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-document"))
    implementation(project(":notarist-audit"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.postgresql)
    implementation(libs.minio)
    implementation(libs.flyway.core)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit)
}
```

### 6.5 `notarist-web/build.gradle.kts` (Entry Point)

```kotlin
plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-auth"))
    implementation(project(":notarist-document"))
    implementation(project(":notarist-ingest"))
    implementation(project(":notarist-search"))
    implementation(project(":notarist-assistant"))
    implementation(project(":notarist-regulation"))
    implementation(project(":notarist-audit"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.ojdbc11)
    implementation(libs.liquibase.core)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.logback.json)
}
```

---

## 7. DEPENDENCY GRAPH

### 7.1 Module Dependency Matrix

`✓` = allowed dependency | `✗` = forbidden | `—` = self

|  | core | auth | document | ingest | search | assistant | regulation | audit | web |
|---|---|---|---|---|---|---|---|---|---|
| **core** | — | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| **auth** | ✓ | — | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ |
| **document** | ✓ | ✗ | — | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| **ingest** | ✓ | ✗ | ✓ | — | ✗ | ✗ | ✗ | ✓ | ✗ |
| **search** | ✓ | ✗ | ✓ | ✗ | — | ✗ | ✗ | ✗ | ✗ |
| **assistant** | ✓ | ✗ | ✓ | ✗ | ✓ | — | ✗ | ✓ | ✗ |
| **regulation** | ✓ | ✗ | ✓ | ✗ | ✗ | ✗ | — | ✗ | ✗ |
| **audit** | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | — | ✗ |
| **web** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |

### 7.2 Dependency Justifications

| Dependency | Reason |
|---|---|
| `ingest → document` | IngestionJob creates and updates `DocumentLegal` status |
| `ingest → audit` | Every pipeline stage emits audit entry |
| `search → document` | Search results include document metadata |
| `assistant → search` | RAG pipeline delegates retrieval to search module |
| `assistant → document` | Citation validation resolves back to document |
| `assistant → audit` | Every AI response is audit-logged |
| `regulation → document` | RegulasiMaster is a specialized DocumentLegal |
| `auth → audit` | Auth failures and session events are audit-logged |
| `web → all` | Entry point — wires all module beans |

### 7.3 Forbidden Dependency Rationale

| Forbidden | Reason |
|---|---|
| `search → ingest` | Search must not trigger ingestion — pipeline is one-way |
| `document → any` | Document module is a pure domain — no upstream deps |
| `core → any` | Core is the shared kernel — zero outbound deps |
| `audit → any (except core)` | Audit is append-only — must not create circular flows |
| Any module → `web` | Web is the composition root — nothing depends on it |

---

## 8. SHARED LIBRARY POLICY

### 8.1 What Goes in `notarist-core`

| Category | Items | Reason |
|---|---|---|
| Strong-typed IDs | `DocumentId`, `ChunkId`, `PersonId`, `JobId`, `SessionId` | Used across ≥ 3 modules |
| Legal domain enums | `JenisDokumen`, `JenisAkta`, `ClassificationLevel`, `PipelineStage`, `JobStatus` | Domain vocabulary — shared |
| Value objects | `NomorAkta`, `NomorNIK` (encrypted), `NomorNPWP`, `CorrelationId`, `TraceId` | Cross-cutting primitives |
| Base event | `DomainEvent` interface | All modules produce events |
| Use-case interfaces | `UseCase<C,R>`, `CommandUseCase<C>` | Generic contract pattern |
| API response types | `ApiResponse<T>`, `ApiMeta`, `ApiError`, `PageResponse<T>` | All API modules return same envelope |
| Exception types | `DocumentNotFoundException`, `UnauthorizedAccessException`, `ValidationException` | Shared error vocabulary |
| Constants | `RRF_K = 60`, `EMBEDDING_DIM = 1024`, `MAX_CHUNK_TOKENS` | Configuration constants |
| Utilities | `SensitiveFieldRedactor`, `TextNormalizer`, `ChecksumUtil` | Zero Spring, pure Java |

### 8.2 What Does NOT Go in `notarist-core`

| Item | Reason |
|---|---|
| Spring beans (`@Component`) | Core has zero Spring dependency |
| Database entities | Module-specific concern |
| HTTP clients | Infrastructure concern |
| Queue adapter | Module-specific (ingest owns the queue) |
| JWT handling | Auth module responsibility |
| Business logic for specific domains | Belongs in the owning module's domain layer |
| DTO classes for specific modules | Module-private concern |

### 8.3 Shared Type Immutability Rule

All types in `notarist-core` are **immutable**:
- `record` classes (Java 17) preferred over `class`
- No setters
- Value objects: validated at construction, fail-fast
- Enums: closed set — new values require STEP 7.5 contract update + approval

---

## 9. PACKAGE NAMING CONVENTION

### 9.1 Root Package Pattern

```
com.notarist.<module>.<layer>.<sub-package>
```

### 9.2 Full Qualified Package Inventory

```
com.notarist.core
  .domain.valueobject          — DocumentId, ChunkId, NomorAkta, CorrelationId, ...
  .domain.event                — DomainEvent (interface)
  .domain.exception            — DocumentNotFoundException, UnauthorizedAccessException, ...
  .application.usecase         — UseCase<C,R>, CommandUseCase<C>
  .api.response                — ApiResponse<T>, ApiMeta, ApiError, PageResponse<T>
  .util                        — SensitiveFieldRedactor, ChecksumUtil, TextNormalizer

com.notarist.auth
  .api.rest                    — AuthController
  .api.request                 — LoginRequest, RefreshTokenRequest
  .api.response                — TokenResponse, UserProfileResponse
  .application.command         — AuthenticateCommand, RefreshTokenCommand, LogoutCommand
  .application.handler.command — AuthenticateCommandHandler, RefreshTokenCommandHandler
  .application.port.in         — AuthenticateUserUseCase, RefreshTokenUseCase, InvalidateSessionUseCase
  .application.port.out        — UserRepository, SessionTokenRepository, TokenDenyListPort
  .domain.model                — User, Session, RefreshToken, Role
  .domain.service              — TokenGenerationService, PasswordVerificationService
  .domain.exception            — InvalidCredentialsException, SessionExpiredException
  .infrastructure.persistence.oracle — OracleUserRepository
  .infrastructure.persistence.postgres — PostgresSessionTokenRepository
  .infrastructure.security     — JwtTokenProvider, VpdContextSetter, JwtAuthenticationFilter
  .config                      — SecurityConfig, JwtConfig

com.notarist.document
  .api.rest                    — DocumentController
  .api.request                 — UpdateDocumentMetadataRequest
  .api.response                — DocumentLegalResponse, DocumentSummary, ChunkDetail
  .application.query           — GetDocumentQuery, ListDocumentsQuery, GetChunksQuery
  .application.handler.query   — GetDocumentQueryHandler, ListDocumentsQueryHandler
  .application.port.in         — GetDocumentUseCase, ListDocumentsUseCase, GetChunksUseCase
  .application.port.out        — DocumentLegalRepository, ChunkRepository
  .domain.model                — DocumentLegal, DocumentChunk, DocumentVersion, NotarisSummary
  .domain.exception            — DocumentNotFoundException, DocumentAlreadyIndexedException
  .infrastructure.persistence.oracle — OracleDocumentRepository
  .infrastructure.persistence.postgres — PostgresChunkRepository
  .infrastructure.persistence.mapper — DocumentLegalMapper, ChunkMapper
  .config                      — DocumentModuleConfig

com.notarist.ingest
  .api.rest                    — IngestController
  .api.request                 — InitiateIngestionRequest, ConfirmUploadRequest, RetryJobRequest
  .api.response                — UploadUrlResponse, IngestionJobStatusResponse
  .application.command         — InitiateIngestionCommand, ConfirmUploadCommand, RetryIngestionCommand
  .application.handler.command — InitiateIngestionCommandHandler, ConfirmUploadCommandHandler
  .application.port.in         — InitiateIngestionUseCase, GetIngestionStatusUseCase, RetryIngestionUseCase
  .application.port.out        — IngestionJobRepository, DocumentStoragePort, OcrServicePort,
                                  NerServicePort, ChunkingPort, EmbeddingPort, VectorIndexPort, IngestionQueuePort
  .application.service         — IngestionPipelineOrchestrator, ChecksumVerificationService
  .application.policy          — DuplicateDetectionPolicy, ClassificationEnforcementPolicy
  .domain.model                — IngestionJob, ProcessingLock, DocumentChecksum, IngestionAuditEntry
  .domain.event                — DocumentUploadedEvent, OcrCompletedEvent, NerCompletedEvent,
                                  ChunkingCompletedEvent, EmbeddingCompletedEvent, IndexingCompletedEvent
  .domain.service              — ChunkingStrategySelector, PipelineStageTransitionService
  .infrastructure.persistence.postgres — PostgresIngestionJobRepository, PostgresIngestionQueueAdapter
  .infrastructure.minio        — MinioDocumentStorageAdapter
  .infrastructure.sidecar.ocr  — PaddleOcrAdapter
  .infrastructure.sidecar.ner  — IndoBertNerAdapter
  .infrastructure.sidecar.embedding — OllamaEmbeddingAdapter
  .infrastructure.qdrant       — QdrantVectorIndexAdapter
  .infrastructure.queue        — IngestionQueueConsumer, IngestionQueuePublisher,
                                  OcrWorker, NerWorker, ChunkingWorker, EmbeddingWorker, IndexingWorker
  .config                      — IngestModuleConfig, QueueConfig, SidecarConfig

com.notarist.search
  .api.rest                    — SearchController
  .api.request                 — HybridSearchRequest, SemanticSearchRequest, RegulationSearchRequest
  .api.response                — SearchResponse, RetrievalResultResponse
  .application.query           — HybridSearchQuery, SemanticSearchQuery
  .application.handler.query   — HybridSearchQueryHandler, SemanticSearchQueryHandler
  .application.port.in         — HybridSearchUseCase, SemanticSearchUseCase
  .application.port.out        — KeywordRetrievalPort, SemanticRetrievalPort, RerankerPort, ContextAssemblyPort
  .application.pipeline        — RetrievalPipeline (interface), HybridRetrievalPipeline,
                                  RegulationRetrievalPipeline, SopRetrievalPipeline
  .domain.model                — SearchQuery, RetrievalResult, SearchIntent, RankingScore, SearchSession
  .domain.service              — RrfFusionService, SearchPolicyService, FieldMaskingService
  .infrastructure.postgres     — PostgresBm25RetrievalAdapter
  .infrastructure.qdrant       — QdrantSemanticRetrievalAdapter
  .infrastructure.sidecar.reranker — BgeRerankerAdapter
  .infrastructure.assembly     — ChunkContextAssembler
  .config                      — SearchModuleConfig

com.notarist.assistant
  .api.rest                    — AssistantController
  .api.request                 — CreateSessionRequest, AssistantQueryRequest
  .api.response                — SessionResponse, AssistantResponse
  .application.command         — CreateSessionCommand, SubmitQueryCommand
  .application.handler.command — CreateSessionCommandHandler, SubmitQueryCommandHandler
  .application.port.in         — StreamAssistantUseCase, NonStreamAssistantUseCase, CreateSessionUseCase
  .application.port.out        — LlmPort, SearchPort, CitationRepositoryPort, ResponseAuditPort, SessionRepository
  .application.pipeline        — RagPipeline (interface), CitationFirstRagPipeline
  .domain.model                — AssistantConversation, AssistantQuery, AssistantResponse,
                                  Citation, SourceChunk, GroundingMetadata, AssistantToken
  .domain.service              — CitationValidatorService, HallucinationGuardService, PromptBuilderService
  .domain.exception            — NoSourceFoundException, CitationValidationException, HallucinationDetectedException
  .infrastructure.sidecar.llm  — OllamaLlmAdapter
  .infrastructure.persistence.postgres — PostgresCitationRepository, PostgresSessionRepository
  .config                      — AssistantModuleConfig

com.notarist.regulation
  .api.rest                    — RegulationController
  .api.request                 — (read-only — no create request in this module)
  .api.response                — RegulasiSummary, RegulasiResponse, PasalResponse, BabResponse
  .application.query           — ListRegulasiQuery, GetRegulasiQuery, GetPasalQuery
  .application.handler.query   — ListRegulasiQueryHandler, GetRegulasiQueryHandler, GetPasalQueryHandler
  .application.port.in         — ListRegulasiUseCase, GetPasalUseCase, GetRegulasiCitationsUseCase
  .application.port.out        — RegulasiRepository, PasalRepository, CitationLookupPort
  .domain.model                — RegulasiMaster, RegulasiBab, RegulasiPasal, RegulasiAyat, RegulasiCitation
  .infrastructure.persistence.oracle — OracleRegulasiRepository, OraclePasalRepository
  .config                      — RegulationModuleConfig

com.notarist.audit
  .api.rest                    — AuditController
  .api.response                — AuditTrailResponse, AuditEntryResponse
  .application.query           — ListAuditTrailQuery, GetAuditEntryQuery
  .application.handler.query   — AuditQueryHandler
  .application.handler.event   — DomainEventAuditHandler  — consumes all domain events
  .application.port.in         — GetAuditTrailUseCase, RecordAuditEventUseCase
  .application.port.out        — AuditTrailRepository
  .domain.model                — AuditEntry, AuditEventType, AuditSubject
  .infrastructure.persistence.oracle — OracleAuditTrailRepository
  .config                      — AuditModuleConfig

com.notarist.web
  .config                      — DataSourceConfig, SecurityConfig, OpenApiConfig, ObservabilityConfig,
                                  CorrelationIdFilter, TraceIdFilter, VpdContextFilter
  .NotaristApplication         — main() entry point
```

---

## 10. ERROR CODE TAXONOMY

### 10.1 Error Code Format

```
<DOMAIN>_<SPECIFIC_REASON>
```

All error codes: `SCREAMING_SNAKE_CASE`.

### 10.2 HTTP Status → Error Code Mapping

| HTTP Status | Meaning | Error Code Prefix |
|---|---|---|
| 400 | Bad Request — validation failure | `VALIDATION_*` |
| 401 | Unauthorized — auth required | `AUTH_*` |
| 403 | Forbidden — insufficient permission | `ACCESS_*` |
| 404 | Not Found | `*_NOT_FOUND` |
| 409 | Conflict — duplicate or lock | `*_CONFLICT`, `*_DUPLICATE` |
| 422 | Unprocessable — semantic validation | `*_INVALID_*` |
| 429 | Too Many Requests | `RATE_LIMIT_EXCEEDED` |
| 500 | Internal Server Error | `SYSTEM_*` |
| 503 | Service Unavailable — sidecar down | `SIDECAR_*` |

### 10.3 Full Error Code Registry

#### AUTH Domain (401 / 403)

| Code | HTTP | Description |
|---|---|---|
| `AUTH_INVALID_CREDENTIALS` | 401 | Username or password incorrect |
| `AUTH_TOKEN_EXPIRED` | 401 | JWT access token expired |
| `AUTH_TOKEN_INVALID` | 401 | JWT malformed or signature invalid |
| `AUTH_TOKEN_MISSING` | 401 | No Authorization header present |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | Refresh token not found or expired |
| `AUTH_REFRESH_TOKEN_USED` | 401 | Refresh token already consumed (rotation violation) |
| `AUTH_SESSION_INVALIDATED` | 401 | Session was explicitly invalidated |
| `ACCESS_INSUFFICIENT_ROLE` | 403 | User role does not permit this action |
| `ACCESS_CLASSIFICATION_CLEARANCE` | 403 | Document classification exceeds user clearance |
| `ACCESS_VPD_VIOLATION` | 403 | Oracle VPD blocked row-level access |
| `ACCESS_TENANT_MISMATCH` | 403 | Cross-tenant access attempt |

#### DOCUMENT Domain (404 / 409)

| Code | HTTP | Description |
|---|---|---|
| `DOCUMENT_NOT_FOUND` | 404 | DocumentId does not exist |
| `DOCUMENT_CHUNK_NOT_FOUND` | 404 | ChunkId does not exist |
| `DOCUMENT_ALREADY_INDEXED` | 409 | Cannot modify document after indexing |
| `DOCUMENT_TYPE_MISMATCH` | 422 | JenisAkta incompatible with documentType |

#### INGEST Domain (409 / 422 / 503)

| Code | HTTP | Description |
|---|---|---|
| `INGEST_JOB_NOT_FOUND` | 404 | IngestionJobId does not exist |
| `INGEST_DUPLICATE_CHECKSUM` | 409 | Document with same SHA-256 already ingested |
| `INGEST_LOCK_CONFLICT` | 409 | Document currently being processed by another worker |
| `INGEST_UPLOAD_URL_EXPIRED` | 422 | Signed upload URL has expired |
| `INGEST_CHECKSUM_MISMATCH` | 422 | Provided checksum does not match uploaded file |
| `INGEST_FILE_TOO_LARGE` | 422 | File exceeds 50MB limit |
| `INGEST_MIME_TYPE_UNSUPPORTED` | 422 | Only application/pdf supported |
| `INGEST_JOB_NOT_RETRYABLE` | 422 | Job is COMPLETED or in non-retryable state |
| `SIDECAR_OCR_UNAVAILABLE` | 503 | PaddleOCR service not responding |
| `SIDECAR_NER_UNAVAILABLE` | 503 | IndoBERT NER service not responding |
| `SIDECAR_EMBEDDING_UNAVAILABLE` | 503 | Ollama/bge-m3 service not responding |
| `SIDECAR_RERANKER_UNAVAILABLE` | 503 | Reranker service not responding |
| `SIDECAR_LLM_UNAVAILABLE` | 503 | Ollama LLM service not responding |

#### SEARCH Domain (422)

| Code | HTTP | Description |
|---|---|---|
| `SEARCH_QUERY_EMPTY` | 422 | Query text is blank |
| `SEARCH_QUERY_TOO_LONG` | 422 | Query exceeds 500 characters |
| `SEARCH_INTENT_INVALID` | 422 | Unknown search intent value |
| `SEARCH_NO_RESULTS` | 200 | Valid but zero results — returned as 200 with empty list |
| `VECTOR_INDEX_UNAVAILABLE` | 503 | Qdrant not reachable |

#### ASSISTANT Domain (422 / 500)

| Code | HTTP | Description |
|---|---|---|
| `ASSISTANT_SESSION_NOT_FOUND` | 404 | SessionId does not exist |
| `ASSISTANT_SESSION_EXPIRED` | 422 | Session exceeded max inactivity |
| `ASSISTANT_NO_SOURCE_FOUND` | 422 | Zero relevant chunks retrieved — response aborted |
| `ASSISTANT_CITATION_INVALID` | 500 | Citation references non-existent chunk (internal error) |
| `ASSISTANT_HALLUCINATION_DETECTED` | 200 | Response generated but grounding score below threshold — warning appended |
| `ASSISTANT_QUERY_TOO_LONG` | 422 | Query exceeds 1000 characters |

#### REGULATION Domain (404)

| Code | HTTP | Description |
|---|---|---|
| `REGULATION_NOT_FOUND` | 404 | RegulasiId does not exist |
| `PASAL_NOT_FOUND` | 404 | PasalId does not exist |
| `BAB_NOT_FOUND` | 404 | BabId does not exist |

#### VALIDATION Domain (400)

| Code | HTTP | Description |
|---|---|---|
| `VALIDATION_FIELD_REQUIRED` | 400 | Required field missing |
| `VALIDATION_FIELD_INVALID_FORMAT` | 400 | Field fails format constraint |
| `VALIDATION_FIELD_SIZE_EXCEEDED` | 400 | Field exceeds max length |
| `VALIDATION_ENUM_INVALID` | 400 | Value not in allowed enum |

#### SYSTEM Domain (500)

| Code | HTTP | Description |
|---|---|---|
| `SYSTEM_INTERNAL_ERROR` | 500 | Unexpected exception — logged with correlationId |
| `SYSTEM_DATABASE_ERROR` | 500 | Oracle or PostgreSQL connectivity failure |
| `SYSTEM_VECTOR_STORE_ERROR` | 500 | Qdrant write failure |
| `SYSTEM_STORAGE_ERROR` | 500 | MinIO read/write failure |

---

## 11. STANDARD RESPONSE CONTRACT

### 11.1 Success Response

```json
{
  "meta": {
    "requestId":    "uuid",
    "timestamp":    "2026-05-24T10:30:00.000Z",
    "apiVersion":   "v1",
    "processingMs": 142
  },
  "data":  { },
  "error": null
}
```

### 11.2 Error Response

```json
{
  "meta": {
    "requestId":  "uuid",
    "timestamp":  "2026-05-24T10:30:00.000Z",
    "apiVersion": "v1"
  },
  "data": null,
  "error": {
    "code":    "DOCUMENT_NOT_FOUND",
    "message": "Document dengan ID tersebut tidak ditemukan.",
    "details": [
      {
        "field":  "documentId",
        "issue":  "Value '3fa85f64-...' not found in system"
      }
    ]
  }
}
```

### 11.3 Async Job Accepted (202)

```json
{
  "meta": {
    "requestId":  "uuid",
    "timestamp":  "2026-05-24T10:30:00.000Z",
    "apiVersion": "v1"
  },
  "data": {
    "jobId":       "uuid",
    "status":      "OCR_QUEUE",
    "statusUrl":   "/api/v1/ingest/jobs/{jobId}/status",
    "estimatedCompletionMs": 30000
  },
  "error": null
}
```

### 11.4 JSON Schema — `ApiResponse<T>`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://notarist.internal/schemas/api/response",
  "title": "ApiResponse",
  "type": "object",
  "required": ["meta"],
  "properties": {
    "meta": {
      "type": "object",
      "required": ["requestId", "timestamp", "apiVersion"],
      "properties": {
        "requestId":    { "type": "string", "format": "uuid" },
        "timestamp":    { "type": "string", "format": "date-time" },
        "apiVersion":   { "type": "string", "enum": ["v1"] },
        "processingMs": { "type": "integer", "minimum": 0 }
      }
    },
    "data":  { },
    "error": {
      "oneOf": [
        { "type": "null" },
        {
          "type": "object",
          "required": ["code", "message"],
          "properties": {
            "code":    { "type": "string", "pattern": "^[A-Z_]+$" },
            "message": { "type": "string" },
            "details": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["field", "issue"],
                "properties": {
                  "field": { "type": "string" },
                  "issue": { "type": "string" }
                }
              }
            }
          }
        }
      ]
    }
  }
}
```

### 11.5 Invariants

| Rule | Enforcement |
|---|---|
| `meta` always present | Required in all responses — success and error |
| `data` null on error | Mutually exclusive with `error` |
| `error` null on success | Never both non-null simultaneously |
| `requestId` = `correlationId` | Same UUID as `X-Correlation-ID` request header |
| HTTP 2xx → `error: null` | Controller layer enforces this |
| HTTP 4xx/5xx → `data: null` | `@ControllerAdvice` enforces this |

---

## 12. SSE EVENT CONTRACT

**Content-Type:** `text/event-stream`  
**Endpoint:** `GET /api/v1/assistant/sessions/{sessionId}/stream`  
**Auth:** JWT Bearer in Authorization header (SSE does not support cookies)

### 12.1 SSE Event Types

```
event: token         — LLM output token (streaming)
event: citation      — Citation reference encountered
event: warning       — Non-fatal warning (e.g., hallucination flag)
event: complete      — Stream finished successfully
event: error         — Stream failed — terminal event
event: heartbeat     — Keep-alive ping (every 15s if LLM is slow)
```

### 12.2 SSE Payload Schemas

#### `event: token`

```
event: token
id: {sequenceNumber}
data: {"token":"Berdasarkan","index":0,"sessionId":"uuid"}
```

```json
{
  "token":     "string — one or more characters",
  "index":     "integer — sequential 0-based",
  "sessionId": "uuid"
}
```

#### `event: citation`

```
event: citation
id: {sequenceNumber}
data: {"citationId":"uuid","citationIndex":1,"chunkId":"uuid","documentId":"uuid","documentTitle":"...","excerpt":"...","confidence":0.92,"verified":true}
```

```json
{
  "citationId":    "uuid",
  "citationIndex": "integer — 1-based, matches [CITATION-N] in text",
  "chunkId":       "uuid",
  "documentId":    "uuid",
  "documentTitle": "string",
  "nomorAkta":     "string — nullable",
  "excerpt":       "string — the relevant chunk excerpt",
  "confidence":    "float 0.0-1.0",
  "verified":      "boolean"
}
```

#### `event: warning`

```
event: warning
data: {"warningCode":"HALLUCINATION_DETECTED","message":"...","severity":"MEDIUM"}
```

```json
{
  "warningCode": "HALLUCINATION_DETECTED | LOW_GROUNDING | CITATION_UNVERIFIED | PARTIAL_SOURCE",
  "message":     "string",
  "severity":    "LOW | MEDIUM | HIGH"
}
```

#### `event: complete`

```
event: complete
data: {"sessionId":"uuid","totalTokens":387,"citationCount":3,"groundingScore":0.87,"durationMs":4200,"hallucinationFlagRaised":false}
```

```json
{
  "sessionId":             "uuid",
  "totalTokens":           "integer",
  "citationCount":         "integer",
  "groundingScore":        "float 0.0-1.0",
  "durationMs":            "integer",
  "ttftMs":                "integer — time to first token",
  "hallucinationFlagRaised": "boolean",
  "warnings":              "List<string> — warning codes if any"
}
```

#### `event: error`

```
event: error
data: {"code":"ASSISTANT_NO_SOURCE_FOUND","message":"Tidak ditemukan dokumen relevan untuk pertanyaan ini.","fatal":true}
```

```json
{
  "code":    "string — error code from taxonomy",
  "message": "string",
  "fatal":   "boolean — if true, stream ends"
}
```

#### `event: heartbeat`

```
event: heartbeat
data: {"timestamp":"2026-05-24T10:30:15.000Z"}
```

### 12.3 SSE Stream Lifecycle

```
CLIENT connects to /stream
  → 200 OK, Content-Type: text/event-stream
  → Server sends: event: citation (pre-assembled before LLM)
  → Server sends: event: token (repeated until response complete)
  → Server sends: event: warning (if any)
  → Server sends: event: complete
  → Stream closed by server

On error:
  → Server sends: event: error (fatal: true)
  → Stream closed by server

Client disconnect: server detects and cancels LLM stream
```

### 12.4 SSE Client Requirements (Frontend)

- Use `EventSource` polyfill with custom Authorization header
- Handle each `event:` type explicitly (not just `message`)
- On `event: error` with `fatal: true` → show error UI, do not reconnect
- On disconnect: retry with exponential backoff (max 3 retries)
- Reconnect uses `Last-Event-ID` header to resume (server supports idempotent resume)

---

## 13. ENVIRONMENT VARIABLE CONTRACT

### 13.1 Complete Environment Variable Registry

All variables listed with: name, type, required, default (local), secret flag.

#### Database

| Variable | Type | Required | Local Default | Secret |
|---|---|---|---|---|
| `ORACLE_URL` | String | Yes | `jdbc:oracle:thin:@localhost:1521/NOTARISTDB` | No |
| `ORACLE_USER` | String | Yes | `notarist_app` | No |
| `ORACLE_PASSWORD` | String | Yes | _(set in local config)_ | **Yes** |
| `ORACLE_POOL_MAX` | Integer | No | `10` | No |
| `ORACLE_POOL_MIN` | Integer | No | `2` | No |
| `POSTGRES_URL` | String | Yes | `jdbc:postgresql://localhost:5432/notarist` | No |
| `POSTGRES_USER` | String | Yes | `notarist_app` | No |
| `POSTGRES_PASSWORD` | String | Yes | _(set in local config)_ | **Yes** |
| `POSTGRES_POOL_MAX` | Integer | No | `10` | No |

#### Vector & Object Storage

| Variable | Type | Required | Local Default | Secret |
|---|---|---|---|---|
| `QDRANT_URL` | String | Yes | `http://localhost:6333` | No |
| `QDRANT_API_KEY` | String | No | _(empty for local)_ | **Yes** |
| `QDRANT_COLLECTION` | String | No | `notarist_chunks` | No |
| `MINIO_ENDPOINT` | String | Yes | `http://localhost:9000` | No |
| `MINIO_ACCESS_KEY` | String | Yes | `minioadmin` | **Yes** |
| `MINIO_SECRET_KEY` | String | Yes | `minioadmin` | **Yes** |
| `MINIO_BUCKET_RAW` | String | No | `notarist-raw` | No |
| `MINIO_BUCKET_OCR` | String | No | `notarist-ocr` | No |
| `MINIO_BUCKET_PROCESSED` | String | No | `notarist-processed` | No |
| `MINIO_BUCKET_CHUNK` | String | No | `notarist-chunk` | No |
| `MINIO_BUCKET_EXPORT` | String | No | `notarist-export` | No |
| `MINIO_UPLOAD_URL_TTL_MINUTES` | Integer | No | `15` | No |

#### Security

| Variable | Type | Required | Local Default | Secret |
|---|---|---|---|---|
| `JWT_PRIVATE_KEY_PATH` | String | Yes | `file:./keys/notarist-private.pem` | **Yes** |
| `JWT_PUBLIC_KEY_PATH` | String | Yes | `file:./keys/notarist-public.pem` | No |
| `JWT_ISSUER` | String | No | `notarist-rag` | No |
| `JWT_ACCESS_TOKEN_TTL_MINUTES` | Integer | No | `15` | No |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | Integer | No | `7` | No |
| `APP_ENCRYPTION_KEY` | String | Yes | _(never default in any env)_ | **Yes** |
| `APP_ENCRYPTION_SALT` | String | Yes | _(never default in any env)_ | **Yes** |

#### Sidecar Services

| Variable | Type | Required | Local Default | Secret |
|---|---|---|---|---|
| `OCR_BASE_URL` | String | Yes | `http://localhost:8081` | No |
| `OCR_TIMEOUT_MS` | Integer | No | `120000` | No |
| `NER_BASE_URL` | String | Yes | `http://localhost:8082` | No |
| `NER_TIMEOUT_MS` | Integer | No | `30000` | No |
| `RERANKER_BASE_URL` | String | Yes | `http://localhost:8083` | No |
| `RERANKER_TIMEOUT_MS` | Integer | No | `15000` | No |
| `OLLAMA_BASE_URL` | String | Yes | `http://localhost:11434` | No |
| `OLLAMA_MODEL` | String | No | `notarist-llm-7b` | No |
| `OLLAMA_TIMEOUT_MS` | Integer | No | `120000` | No |

#### Ingestion Queue

| Variable | Type | Required | Local Default | Secret |
|---|---|---|---|---|
| `QUEUE_POLL_INTERVAL_MS` | Integer | No | `2000` | No |
| `QUEUE_MAX_CONCURRENT_WORKERS` | Integer | No | `3` | No |
| `QUEUE_LOCK_TIMEOUT_MS` | Integer | No | `300000` | No |
| `QUEUE_MAX_RETRY_ATTEMPTS` | Integer | No | `3` | No |

#### Application

| Variable | Type | Required | Local Default | Secret |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | String | Yes | `local` | No |
| `SERVER_PORT` | Integer | No | `8080` | No |
| `APP_NAME` | String | No | `notarist-rag` | No |
| `LOG_LEVEL` | String | No | `INFO` | No |
| `LOG_FORMAT` | String | No | `JSON` | No |
| `NEXUS_USER` | String | Yes (CI) | — | No |
| `NEXUS_PASSWORD` | String | Yes (CI) | — | **Yes** |

#### Observability

| Variable | Type | Required | Local Default | Secret |
|---|---|---|---|---|
| `OTEL_EXPORTER_ENDPOINT` | String | No | _(empty — OTel disabled locally)_ | No |
| `OTEL_SERVICE_NAME` | String | No | `notarist-rag` | No |
| `PROMETHEUS_ENABLED` | Boolean | No | `true` | No |

### 13.2 Secret Management Rule

```
LOCAL:    .env file (gitignored) or application-local.yaml (gitignored)
DEV:      GitLab CI/CD environment variables (masked)
STAGING:  GitLab CI/CD environment variables (masked)
PROD:     External secrets manager (e.g., HashiCorp Vault) — injected at runtime
```

**FORBIDDEN:** Commit any variable marked `Secret: Yes` to git. CI pipeline must fail if secrets detected in code.

---

## 14. CONFIG NAMESPACE STRATEGY

### 14.1 YAML Namespace Hierarchy

```
notarist:
  ├── security:
  │   ├── jwt:
  │   │   ├── issuer
  │   │   ├── access-token-ttl-minutes
  │   │   ├── refresh-token-ttl-days
  │   │   ├── private-key-path          ← from JWT_PRIVATE_KEY_PATH
  │   │   └── public-key-path           ← from JWT_PUBLIC_KEY_PATH
  │   └── encryption:
  │       ├── key                       ← from APP_ENCRYPTION_KEY
  │       └── salt                      ← from APP_ENCRYPTION_SALT
  │
  ├── database:
  │   ├── oracle:
  │   │   ├── url                       ← from ORACLE_URL
  │   │   ├── username                  ← from ORACLE_USER
  │   │   ├── password                  ← from ORACLE_PASSWORD
  │   │   ├── pool-max
  │   │   └── pool-min
  │   └── postgres:
  │       ├── url                       ← from POSTGRES_URL
  │       ├── username
  │       ├── password
  │       └── pool-max
  │
  ├── storage:
  │   ├── qdrant:
  │   │   ├── url
  │   │   ├── api-key
  │   │   └── collection
  │   └── minio:
  │       ├── endpoint
  │       ├── access-key
  │       ├── secret-key
  │       ├── upload-url-ttl-minutes
  │       └── buckets:
  │           ├── raw
  │           ├── ocr
  │           ├── processed
  │           ├── chunk
  │           └── export
  │
  ├── ingestion:
  │   └── queue:
  │       ├── poll-interval-ms
  │       ├── max-concurrent-workers
  │       ├── lock-timeout-ms
  │       └── max-retry-attempts
  │
  ├── search:
  │   ├── qdrant:
  │   │   └── top-k-semantic
  │   ├── bm25:
  │   │   └── top-k-keyword
  │   ├── rrf:
  │   │   └── k
  │   └── reranker:
  │       └── top-k-final
  │
  ├── ai:
  │   ├── ollama:
  │   │   ├── model
  │   │   ├── stream
  │   │   └── max-tokens
  │   └── hallucination-guard:
  │       ├── enabled
  │       └── min-citation-count
  │
  ├── sidecar:
  │   ├── ocr:
  │   │   ├── base-url
  │   │   └── timeout-ms
  │   ├── ner:
  │   │   ├── base-url
  │   │   └── timeout-ms
  │   ├── reranker:
  │   │   ├── base-url
  │   │   └── timeout-ms
  │   └── ollama:
  │       ├── base-url
  │       └── timeout-ms
  │
  └── observability:
      ├── correlation-id-header         — "X-Correlation-ID"
      ├── trace-id-header               — "X-Trace-ID"
      └── metrics:
          └── enabled
```

### 14.2 Namespace Rules

| Rule | Enforcement |
|---|---|
| All properties under `notarist.*` | No bare Spring property overrides |
| Secrets always `${ENV_VAR}` reference | No hardcoded credentials in YAML |
| Profile-specific files override defaults | `application-{profile}.yaml` adds/overrides |
| `notarist-web` owns all DataSource config | Module configs use injected beans |

---

## 15. CORRELATION-ID PROPAGATION CONTRACT

### 15.1 HTTP Header Definitions

| Header | Type | Description |
|---|---|---|
| `X-Correlation-ID` | UUID v4 | Tracks one business operation end-to-end |
| `X-Trace-ID` | UUID v4 | Distributed trace ID for OTel span linking |
| `X-Request-ID` | UUID v4 | Unique per HTTP request (may differ from correlation) |

### 15.2 Generation Rules

| Scenario | Behavior |
|---|---|
| Client sends `X-Correlation-ID` | Use the provided value as-is |
| Client does NOT send `X-Correlation-ID` | Server generates new UUID v4 |
| Same business operation, multiple requests | Client MUST resend same `X-Correlation-ID` |
| Upload confirmation to status polling | Client uses same `correlationId` from upload URL response |
| Ingestion pipeline stages | `correlationId` propagated through all queue payloads |
| Sidecar calls (OCR, NER, Reranker, LLM) | `X-Correlation-ID` and `X-Trace-ID` forwarded in HTTP headers |

### 15.3 Propagation Path

```
[Client Request]
    X-Correlation-ID: abc-123
    X-Trace-ID: xyz-456
          │
          ▼
[CorrelationIdFilter — notarist-web]
    → Extract or generate correlationId
    → Extract or generate traceId
    → Store in MDC: correlationId, traceId
    → Store in SecurityContext-equivalent holder
          │
          ▼
[Spring Controller]
    → Pass correlationId to Command/Query object
          │
          ▼
[Application Layer]
    → Pass correlationId to all port method calls
          │
          ▼
[Infrastructure Adapters]
    → Add to all outbound HTTP calls:
        X-Correlation-ID: abc-123
        X-Trace-ID: xyz-456
    → Add to all queue payloads: correlationId field
    → Add to all audit events: correlationId field
          │
          ▼
[Response to Client]
    → Add to response headers:
        X-Correlation-ID: abc-123
        X-Trace-ID: xyz-456
```

### 15.4 Logging MDC Keys

| MDC Key | Value |
|---|---|
| `correlationId` | X-Correlation-ID value |
| `traceId` | X-Trace-ID value |
| `userId` | Authenticated user ID (if present) |
| `tenantId` | Tenant ID (if present) |
| `requestPath` | HTTP request path |
| `module` | Current module name |

### 15.5 Error Response Requirement

All error responses MUST include `meta.requestId` equal to `X-Correlation-ID`.  
This allows operators to correlate client-reported errors to server logs using a single ID.

---

## 16. AUDIT LOG SCHEMA

### 16.1 Oracle Table — `NOTARIST_SEC.AUDIT_TRAIL`

```sql
-- Conceptual DDL (implementation in Liquibase migration)
NOTARIST_SEC.AUDIT_TRAIL (
    AUDIT_ID          VARCHAR2(36)      PRIMARY KEY,   -- UUID
    CORRELATION_ID    VARCHAR2(36)      NOT NULL,
    TRACE_ID          VARCHAR2(36),
    EVENT_TYPE        VARCHAR2(100)     NOT NULL,      -- See Section 16.2
    EVENT_CATEGORY    VARCHAR2(30)      NOT NULL,      -- DOCUMENT|AUTH|INGEST|SEARCH|ASSISTANT|SECURITY
    SUBJECT_TYPE      VARCHAR2(30)      NOT NULL,      -- DOCUMENT|USER|SESSION|JOB|CITATION
    SUBJECT_ID        VARCHAR2(36)      NOT NULL,      -- ID of the subject
    ACTOR_USER_ID     VARCHAR2(36),                    -- Who performed the action
    ACTOR_ROLE        VARCHAR2(30),                    -- Role at time of action
    TENANT_ID         VARCHAR2(36)      NOT NULL,
    ACTION            VARCHAR2(100)     NOT NULL,      -- Human-readable action
    OUTCOME           VARCHAR2(20)      NOT NULL,      -- SUCCESS|FAILURE|PARTIAL
    DETAIL_JSON       CLOB,                            -- Additional context (JSON)
    IP_ADDRESS        VARCHAR2(45),
    USER_AGENT        VARCHAR2(500),
    CREATED_AT        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT audit_trail_pk PRIMARY KEY (AUDIT_ID)
) TABLESPACE NOTARIST_SEC_DATA;

-- Append-only enforcement (no UPDATE/DELETE via app user)
-- Oracle role: NOTARIST_AUDIT_ROLE → INSERT only on AUDIT_TRAIL
```

### 16.2 Audit Event Type Registry

| Event Type | Category | Subject | Trigger |
|---|---|---|---|
| `DOCUMENT_VIEWED` | DOCUMENT | DOCUMENT | Document detail fetched |
| `DOCUMENT_CHUNK_VIEWED` | DOCUMENT | DOCUMENT | Chunk list fetched |
| `DOCUMENT_DOWNLOAD_URL_GENERATED` | DOCUMENT | DOCUMENT | Signed download URL requested |
| `DOCUMENT_METADATA_UPDATED` | DOCUMENT | DOCUMENT | Metadata PATCH |
| `INGEST_JOB_INITIATED` | INGEST | JOB | Upload URL requested |
| `INGEST_UPLOAD_CONFIRMED` | INGEST | JOB | Upload confirmed |
| `INGEST_STAGE_COMPLETED` | INGEST | JOB | Each pipeline stage done |
| `INGEST_STAGE_FAILED` | INGEST | JOB | Stage failure |
| `INGEST_JOB_DLQ` | INGEST | JOB | Moved to dead letter |
| `SEARCH_HYBRID_EXECUTED` | SEARCH | SESSION | Hybrid search performed |
| `SEARCH_SEMANTIC_EXECUTED` | SEARCH | SESSION | Semantic search performed |
| `AI_SESSION_CREATED` | ASSISTANT | SESSION | Assistant session started |
| `AI_QUERY_SUBMITTED` | ASSISTANT | SESSION | User submitted query |
| `AI_RESPONSE_GENERATED` | ASSISTANT | SESSION | LLM response assembled |
| `AI_CITATION_CREATED` | ASSISTANT | CITATION | Citation validated |
| `AI_HALLUCINATION_FLAGGED` | ASSISTANT | SESSION | Guard triggered |
| `AUTH_LOGIN_SUCCESS` | AUTH | USER | Successful login |
| `AUTH_LOGIN_FAILURE` | AUTH | USER | Failed login attempt |
| `AUTH_TOKEN_REFRESH` | AUTH | SESSION | Refresh token used |
| `AUTH_LOGOUT` | AUTH | SESSION | Session invalidated |
| `SECURITY_ACCESS_DENIED` | SECURITY | USER | Authz failure |
| `SECURITY_VPD_BLOCKED` | SECURITY | USER | VPD row filter triggered |
| `SENSITIVE_FIELD_ACCESSED` | SECURITY | DOCUMENT | S2-masked field accessed |

### 16.3 `DETAIL_JSON` Schema

```json
{
  "additionalContext": "object — event-specific fields",
  "examples": {
    "DOCUMENT_VIEWED": {
      "classificationLevel": "CONFIDENTIAL",
      "documentType": "AKTA",
      "jenisAkta": "APHT"
    },
    "AI_RESPONSE_GENERATED": {
      "tokensInput": 1240,
      "tokensOutput": 387,
      "citationCount": 3,
      "groundingScore": 0.87,
      "hallucinationFlagRaised": false
    },
    "SECURITY_ACCESS_DENIED": {
      "reason": "INSUFFICIENT_ROLE",
      "requiredRole": "NOTARIS",
      "userRole": "STAFF",
      "resource": "/api/v1/documents/{id}/entities"
    }
  }
}
```

### 16.4 Audit Write Policy

| Rule | Enforcement |
|---|---|
| Append-only | Oracle role grants INSERT only — no UPDATE/DELETE |
| Cannot be skipped | Audit write is part of application service transaction |
| Audit failure = operation failure | If audit write fails → rollback the business operation |
| Audit records never soft-deleted | Legal requirement — permanent retention |
| Retention period | Minimum 7 years (legal document domain) |
| Audit of audit reads | Reading AUDIT_TRAIL itself is logged at INFO level |

---

## 17. AI RESPONSE CONTRACT

### 17.1 Non-Streaming Response Schema (Full)

`POST /api/v1/assistant/sessions/{sessionId}/query`

```json
{
  "meta": {
    "requestId":    "uuid",
    "timestamp":    "2026-05-24T10:30:05.000Z",
    "apiVersion":   "v1",
    "processingMs": 5400
  },
  "data": {
    "sessionId":     "uuid",
    "queryId":       "uuid",
    "queryText":     "Apa syarat pendaftaran APHT?",
    "responseText":  "Berdasarkan ketentuan yang berlaku [CITATION-1], syarat pendaftaran APHT meliputi... [CITATION-2]",
    "citations":     [ { "see": "Section 18" } ],
    "grounding": {
      "groundingScore":         0.87,
      "citationCoverage":       "FULL",
      "hallucinationFlagRaised": false,
      "verifiedCitationCount":  3,
      "unverifiedCitationCount": 0,
      "warningMessages":        []
    },
    "meta": {
      "model":         "notarist-llm-7b",
      "tokensInput":   1240,
      "tokensOutput":  387,
      "streamMode":    "BATCH",
      "latencyMs":     5400,
      "ttftMs":        null,
      "searchIntent":  "HYBRID",
      "topKUsed":      5
    }
  },
  "error": null
}
```

### 17.2 Response Text Format Rules

| Rule | Detail |
|---|---|
| Citation markers | `[CITATION-N]` where N is 1-based integer |
| No response without citation | If no citations → error `ASSISTANT_NO_SOURCE_FOUND` |
| Citation marker position | Placed immediately after the claim it supports |
| Multiple citations | Multiple markers allowed: `[CITATION-1][CITATION-2]` |
| Language | Response in Bahasa Indonesia (matches query language) |
| Max response length | 2048 tokens (configurable per intent) |
| Hallucination suffix | If flag raised, append: `⚠️ Perhatian: Sebagian jawaban mungkin memerlukan verifikasi lebih lanjut.` |

### 17.3 Grounding Score Thresholds

| Score Range | Interpretation | Action |
|---|---|---|
| 0.80 - 1.00 | High grounding | No warning |
| 0.60 - 0.79 | Medium grounding | Low grounding warning |
| 0.00 - 0.59 | Low grounding | Hallucination flag raised — warning appended |

### 17.4 `citationCoverage` Enum

| Value | Meaning |
|---|---|
| `FULL` | All claims in response have at least one citation |
| `PARTIAL` | Some claims uncited — warning raised |
| `NONE` | No citations in response — blocked (should not reach client) |

---

## 18. CITATION RESPONSE CONTRACT

### 18.1 Citation Object Schema (full)

```json
{
  "citationId":     "uuid",
  "citationIndex":  1,
  "chunkId":        "uuid",
  "documentId":     "uuid",
  "documentTitle":  "Akta APHT No. 45/V/2024",
  "documentType":   "AKTA",
  "jenisAkta":      "APHT",
  "nomorAkta":      "45/V/2024",
  "tanggalAkta":    "2024-05-15",
  "notarisName":    "Sari Dewi, S.H., M.Kn.",
  "chunkIndex":     12,
  "excerpt":        "...pemegang hak tanggungan berhak untuk menjual objek hak tanggungan...",
  "pageNumber":     4,
  "sectionTitle":   "Klausul 3 — Hak Tanggungan",
  "confidence":     0.92,
  "verified":       true,
  "verificationMethod": "QDRANT_CHUNK_LOOKUP",
  "sourceObjectKey": "notarist-chunk/<documentId>/chunks.jsonl"
}
```

### 18.2 Citation Validation Flow

```
1. RetrievalResult returned from search pipeline
   → chunkId and documentId known
2. Citation assembled (pre-LLM)
   → CitationValidatorService.validate(chunkId)
   → Verify chunkId exists in Qdrant (QDRANT_CHUNK_LOOKUP)
   → Verify documentId exists in Oracle
3. Citation object constructed with verified=true
4. If Qdrant lookup fails → verified=false (logged as warning)
5. Post-LLM: CitationValidatorService.verifyAll(citations, responseText)
   → Check [CITATION-N] markers match assembled citation list
   → Check no orphaned markers (N in text but no citation object)
6. Any orphaned marker → HALLUCINATION flag
```

### 18.3 Citation Immutability Rule

Once a Citation is created and persisted to PostgreSQL `CITATION_RECORD` table:
- `chunkId`, `documentId`, `citationIndex`, `verified` are immutable
- `confidence` may be updated post-reranking (one-time)
- Citations are linked to a `sessionId` and `queryId`
- Citation records retained for legal auditability (same 7-year policy as AUDIT_TRAIL)

---

## 19. PAGINATION & FILTERING CONTRACT

### 19.1 Pagination Request Parameters

All list endpoints support:

| Parameter | Type | Default | Max | Description |
|---|---|---|---|---|
| `page` | Integer | `0` | — | 0-based page index |
| `size` | Integer | `20` | `100` | Items per page |
| `sort` | String | `createdAt,desc` | — | `{field},{asc\|desc}` |

Multiple sort fields: `?sort=classificationLevel,asc&sort=createdAt,desc`

### 19.2 Pagination Response Object

```json
{
  "items": [],
  "page": {
    "number":        0,
    "size":          20,
    "totalElements": 150,
    "totalPages":    8,
    "hasNext":       true,
    "hasPrevious":   false,
    "isFirst":       true,
    "isLast":        false
  }
}
```

Wrapped in standard `ApiResponse<PageResponse<T>>`.

### 19.3 Filter Parameters by Endpoint

#### `GET /api/v1/documents`

| Filter Param | Type | Description |
|---|---|---|
| `documentType` | Enum | `AKTA\|REGULASI\|SOP` |
| `jenisAkta` | Enum | `APHT\|SKMHT\|...` |
| `status` | Enum | `UPLOADED\|INDEXED\|FAILED` |
| `classificationLevel` | Enum | Classification filter |
| `notarisId` | UUID | Filter by notaris |
| `dateFrom` | YYYY-MM-DD | Created or indexed from |
| `dateTo` | YYYY-MM-DD | Created or indexed to |
| `query` | String | Full-text search on title |

#### `GET /api/v1/ingest/jobs`

| Filter Param | Type | Description |
|---|---|---|
| `status` | Enum | `PENDING\|PROCESSING\|COMPLETED\|FAILED\|DLQ` |
| `stage` | Enum | Current pipeline stage |
| `dateFrom` | YYYY-MM-DD | Job created from |
| `dateTo` | YYYY-MM-DD | Job created to |

#### `GET /api/v1/audit/trail`

| Filter Param | Type | Description |
|---|---|---|
| `eventCategory` | Enum | `DOCUMENT\|AUTH\|INGEST\|SEARCH\|ASSISTANT\|SECURITY` |
| `eventType` | String | Specific audit event type |
| `actorUserId` | UUID | Filter by actor |
| `subjectId` | UUID | Filter by subject (document, session, etc.) |
| `outcome` | Enum | `SUCCESS\|FAILURE\|PARTIAL` |
| `dateFrom` | ISO-8601 | Audit entry from |
| `dateTo` | ISO-8601 | Audit entry to |

### 19.4 Sorting Allowlist

Sorting is only permitted on explicitly allowlisted fields to prevent SQL injection via sort parameter.

| Endpoint | Sortable Fields |
|---|---|
| `/documents` | `createdAt`, `indexedAt`, `documentType`, `classificationLevel` |
| `/ingest/jobs` | `createdAt`, `updatedAt`, `status` |
| `/regulations` | `nomorRegulasi`, `tanggalBerlaku`, `status` |
| `/audit/trail` | `createdAt`, `eventCategory`, `outcome` |

### 19.5 Filter Validation Rules

| Rule | Detail |
|---|---|
| Unknown filter params | Silently ignored (no error) |
| Invalid enum values | Return `VALIDATION_ENUM_INVALID` error |
| Date format violation | Return `VALIDATION_FIELD_INVALID_FORMAT` error |
| `dateFrom > dateTo` | Return `VALIDATION_FIELD_INVALID_FORMAT` error |
| `size > 100` | Clamped to 100 without error |
| Negative page | Return `VALIDATION_FIELD_INVALID_FORMAT` error |

---

## 20. VERSIONING STRATEGY

### 20.1 API Version Strategy

| Rule | Detail |
|---|---|
| URL-based versioning | `/api/v1/`, `/api/v2/` |
| Current version | `v1` |
| Breaking change triggers new version | Adding required field, removing field, changing type, removing endpoint |
| Non-breaking changes (v1 only) | Adding optional field, new optional query param, new endpoint |
| Deprecation grace period | Minimum 2 full sprint cycles (4 weeks) before v_old removal |
| Deprecation signal | `Deprecation: true` response header + `Sunset: {date}` header |
| Parallel version support | Max 2 versions active simultaneously |

### 20.2 Schema Version Strategy (Events)

All domain events carry `eventVersion` field.

| Rule | Detail |
|---|---|
| Current version | `"1.0"` for all events |
| Breaking event change | Increment to `"2.0"` — consumers must handle both versions during transition |
| Non-breaking event change | Increment to `"1.1"` — additive only (new optional fields) |
| Version in event schema file | File named `document.uploaded.v1.json`, `document.uploaded.v2.json` |
| Backward compatibility window | Old event version supported for 30 days after new version published |

### 20.3 Database Schema Version Strategy

| Database | Tool | Strategy |
|---|---|---|
| Oracle | Liquibase | Sequential changelogs — never modify applied changeset |
| PostgreSQL | Flyway | Sequential `V{N}__description.sql` — never modify applied migration |
| Qdrant collection | Manual + config | Collection name versioned: `notarist_chunks_v1` — new collection created for breaking schema change |

**Oracle Liquibase naming:**
```
db/oracle/changelog/
  db.changelog-master.yaml
  changes/
    oracle-0001-create-notarist-schema.yaml
    oracle-0002-create-notarist-stg-schema.yaml
    oracle-0003-create-notarist-sec-schema.yaml
    oracle-0004-create-dokumen-legal-table.yaml
    ...
```

**PostgreSQL Flyway naming:**
```
db/postgres/migration/
  V0001__create_ingestion_queue.sql
  V0002__create_dlq_table.sql
  V0003__create_chunk_bm25_table.sql
  V0004__create_session_token_table.sql
  V0005__create_citation_record_table.sql
  ...
```

### 20.4 DTO Version Strategy

- DTOs do not carry version numbers — they are versioned via API URL
- When API v2 is created, v2 DTOs live in separate `api.v2.request/response` packages
- v1 DTOs remain unchanged for the deprecation window

### 20.5 Config Version Strategy

| Concern | Strategy |
|---|---|
| `notarist.*` properties | Additive-only within same Spring profile |
| Renamed property | Add new property, deprecated old via `@DeprecatedConfigurationProperty` |
| Removed property | Remove only after all environments confirmed migrated |
| `libs.versions.toml` | Version bumps require explicit PR review |

---

## OPENAPI STRUCTURE (Summary)

```
/generated/openapi/
├── notarist-api.yaml              ← Master spec (imports all)
├── components/
│   ├── schemas/                   ← 8 schema files (one per domain)
│   ├── parameters/                ← Shared params (pagination, path vars, headers)
│   ├── responses/                 ← Shared error responses (400-500)
│   └── securitySchemes/           ← JWT RS256 bearer
└── paths/                         ← 8 path files (one per module)
```

---

## MODULE DEPENDENCY (Summary)

```
core ← (all modules depend on core)
auth ← core, audit
document ← core
ingest ← core, document, audit
search ← core, document
assistant ← core, document, search, audit
regulation ← core, document
audit ← core
web ← ALL (composition root)
```

---

## ERROR TAXONOMY (Summary)

| Prefix | HTTP | Domain |
|---|---|---|
| `AUTH_*` | 401 | Authentication |
| `ACCESS_*` | 403 | Authorization |
| `*_NOT_FOUND` | 404 | Resource missing |
| `*_CONFLICT` / `*_DUPLICATE` | 409 | State conflict |
| `VALIDATION_*` | 400 | Input validation |
| `*_INVALID_*` | 422 | Semantic validation |
| `SIDECAR_*_UNAVAILABLE` | 503 | Sidecar down |
| `SYSTEM_*` | 500 | Internal error |

Total error codes defined: **47**

---

## RESPONSE CONTRACT (Summary)

| Contract | Standard |
|---|---|
| Envelope | `{ meta, data, error }` — always |
| Timestamp | ISO-8601 UTC in all fields |
| IDs | UUID v4 lowercase hyphenated |
| Error | `{ code, message, details[] }` |
| Pagination | `{ items, page: { number, size, totalElements, ... } }` |
| SSE events | 6 types: token, citation, warning, complete, error, heartbeat |
| AI response | Citation-first, grounding score, hallucination flag mandatory |

---

## CONFIG CONTRACT (Summary)

| Scope | Rule |
|---|---|
| Namespace | All under `notarist.*` |
| Secrets | Always `${ENV_VAR}` reference — never hardcoded |
| Profiles | local / dev / staging / production |
| DB migration | Liquibase (Oracle) + Flyway (PostgreSQL) — sequential, immutable |
| Env vars | 48 variables defined, 12 marked Secret |

---

## RECOMMENDATION

**Sebelum STEP 8 dimulai, konfirmasi hal berikut:**

1. **Error code taxonomy** — 47 error codes: apakah ada domain yang belum tercakup?
2. **DTO field coverage** — Apakah ada field bisnis penting yang belum terdaftar di Section 3?
3. **SSE streaming** — Apakah frontend sudah siap handle 6 SSE event types (token, citation, warning, complete, error, heartbeat)?
4. **Audit retention policy** — 7 tahun sudah sesuai regulasi notaris Indonesia?
5. **STRICTLY_CONFIDENTIAL handling** — Apakah rule "STAFF tidak bisa lihat STRICTLY_CONFIDENTIAL pasal" sudah cukup atau perlu granularity lebih?
6. **Queue worker concurrency** — Default `max-concurrent-workers=3` untuk initial: sudah sesuai kapasitas server target?
7. **Chunk size policy** — AKTA: 400-800 token (15% overlap), REGULASI: 300-600 (0%), SOP: 200-500 (10%): sudah final?
8. **Version catalog** — `libs.versions.toml` library versions: perlu verifikasi compatibility sebelum implementation?

---

*STEP 7.5 COMPLETE — Foundation contracts frozen.*  
*Tidak ada implementation code yang digenerate.*  
*Tunggu approval sebelum lanjut ke STEP 8 — Implementation Generation.*
