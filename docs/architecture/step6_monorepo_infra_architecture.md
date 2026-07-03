# STEP 6 — IMPLEMENTATION PLANNING & MONOREPO ARCHITECTURE
# NOTARIST RAG PLATFORM — INFRASTRUCTURE & DEPLOYMENT

**Version:** v1.0
**Date:** 2026-05-23
**Mode:** ANALYSIS_FIRST
**Status:** DRAFT — Pending Approval
**Scope:** Monorepo structure, deployment architecture, dev workflow, infrastructure, observability

---

## SUMMARY

STEP 6 mendefinisikan arsitektur implementasi fisik — bagaimana kode diorganisir,
bagaimana service di-deploy, dan bagaimana developer bekerja sehari-hari.

**Confirmed UX Decisions dari STEP 5 yang berlaku:**

| Decision | Value |
|---|---|
| Regulation Tree | Lazy loading per-BAB (accordion) |
| UI Theme | Light-only professional legal (v1), theme-token-based untuk future dark mode |

**Deployment Philosophy:**
- **Local-first**: setiap developer dapat menjalankan seluruh stack di laptop
- **On-premise friendly**: tidak bergantung pada cloud provider
- **Docker Compose** sebagai deployment unit (bukan Kubernetes untuk initial)
- **Single Oracle 19C** server (external, tidak di-containerize)
- **Monorepo** untuk semua komponen (backend, mobile, sidecars, contracts, infra)

**Build Tools per Layer:**
| Layer | Build Tool |
|---|---|
| Backend (Spring Boot) | Maven (multi-module) |
| Mobile (React Native) | npm / Metro Bundler |
| Python Sidecars | Poetry (per sidecar) |
| Infrastructure | Docker Compose + Makefile |
| API Contracts | openapi-generator-cli |

---

## MONOREPO STRUCTURE

### Root Directory Layout

```
notarist-rag/                              ← MONOREPO ROOT
│
├── Makefile                               ← Top-level orchestration commands
├── .env.example                           ← Template env vars (committed to git)
├── .env.local                             ← Local dev secrets (gitignored)
├── .gitignore
├── README.md
│
├── backend/                               ← Spring Boot 3 / Java 17
├── mobile/                                ← React Native / TypeScript
├── services/                              ← Python sidecars (OCR, NER, Reranker)
├── contracts/                             ← Shared API contracts & schemas
├── infra/                                 ← Infrastructure configs & compose files
├── scripts/                               ← Developer utility scripts
├── generated/                             ← Architecture docs (STEP 1-6 outputs)
│   ├── docs/
│   └── sql/
└── context/                               ← Project context documents (read-only)
```

### Makefile — Top-Level Commands

```makefile
CONCEPT (bukan implementasi):

make dev-up           → docker-compose local stack up (Postgres, Qdrant, MinIO, sidecars)
make dev-down         → stop all local containers
make dev-logs         → tail logs all services
make build-backend    → mvn clean package -DskipTests
make build-mobile     → npm install + prebuild check
make build-sidecars   → build all Python service Docker images
make test-backend     → mvn test
make test-mobile      → jest
make generate-api     → regenerate OpenAPI clients (backend stubs + mobile TS types)
make seed-data        → run seed scripts (test data)
make check-health     → curl health endpoints for all services
make db-migrate       → run Flyway (PostgreSQL) + Liquibase (Oracle)
make clean            → remove build artifacts
```

---

### Backend Structure (Maven Multi-Module)

```
backend/
│
├── pom.xml                                ← Parent POM
│   (packaging: pom, manages versions, shared deps, plugins)
│
├── notarist-core/                         MODULE 0 — Shared domain
│   ├── pom.xml
│   └── src/main/java/id/notarist/core/
│       ├── domain/
│       │   ├── model/
│       │   │   ├── DocId.java             (record — strongly typed ID)
│       │   │   ├── AktaId.java
│       │   │   ├── ChunkId.java
│       │   │   └── QdrantPointId.java
│       │   └── enums/
│       │       ├── JenisDokumen.java
│       │       ├── JenisAkta.java
│       │       ├── KlasifikasiKerahasiaan.java
│       │       ├── StatusDokumen.java
│       │       ├── PipelineStage.java
│       │       └── UserRole.java
│       ├── exception/
│       │   ├── DocumentNotFoundException.java
│       │   ├── UnauthorizedAccessException.java
│       │   ├── OcrFailureException.java
│       │   ├── EmbeddingException.java
│       │   └── VectorSyncException.java
│       ├── config/
│       │   └── Constants.java             (CHUNK_OVERLAP, RRF_K, MAX_TOKENS, etc.)
│       └── util/
│           ├── SensitiveFieldRedactor.java
│           ├── TextNormalizer.java
│           ├── LegalAbbreviationExpander.java
│           └── DocumentIdGenerator.java
│
├── notarist-auth/                         MODULE 1 — Auth & RBAC
│   ├── pom.xml
│   └── src/main/java/id/notarist/auth/
│       ├── config/
│       │   ├── SecurityConfig.java
│       │   └── JwtConfig.java
│       ├── domain/
│       │   └── NotaristPrincipal.java     (security context record)
│       ├── service/
│       │   ├── JwtTokenService.java
│       │   ├── UserAuthService.java
│       │   └── RbacService.java
│       └── filter/
│           ├── JwtAuthFilter.java
│           ├── RbacFilter.java
│           └── AccessControlFilter.java
│
├── notarist-document/                     MODULE 2 — Document domain
│   ├── pom.xml
│   └── src/main/java/id/notarist/document/
│       ├── entity/                        JPA entities (Oracle NOTARIST schema)
│       │   ├── DocMasterEntity.java
│       │   ├── AktaMasterEntity.java
│       │   ├── ClientMasterEntity.java
│       │   ├── PersonMasterEntity.java
│       │   ├── NotarisMasterEntity.java
│       │   ├── PpatMasterEntity.java
│       │   ├── SertifikatMasterEntity.java
│       │   └── DocRelationshipEntity.java
│       ├── repository/
│       │   ├── DocMasterRepository.java
│       │   ├── AktaMasterRepository.java
│       │   └── ... (per entity)
│       ├── service/
│       │   ├── DocumentService.java
│       │   ├── AktaService.java
│       │   ├── ClientService.java
│       │   ├── PersonService.java
│       │   ├── DocumentVersionService.java
│       │   ├── DocumentTagService.java
│       │   └── DocumentRelationService.java
│       └── mapper/
│           ├── DocumentMapper.java
│           └── AktaMapper.java
│
├── notarist-ingest/                       MODULE 3 — Ingestion pipeline
│   ├── pom.xml
│   └── src/main/java/id/notarist/ingest/
│       ├── service/
│       │   ├── DocumentUploadService.java
│       │   ├── OcrOrchestrationService.java
│       │   ├── NerOrchestrationService.java
│       │   ├── RuleBasedExtractor.java
│       │   ├── IndoBertNerClient.java      (HTTP client)
│       │   ├── LlmFallbackExtractor.java
│       │   ├── NerConflictResolver.java
│       │   ├── MetadataMappingService.java
│       │   ├── PipelineStateService.java
│       │   └── SensitiveFieldRedactorService.java
│       ├── worker/
│       │   ├── OcrWorker.java
│       │   ├── NerWorker.java
│       │   ├── ChunkingWorker.java
│       │   └── EmbeddingWorker.java
│       ├── queue/
│       │   └── PipelineQueueService.java  (PostgreSQL SKIP LOCKED)
│       └── client/
│           ├── PaddleOcrClient.java
│           └── MinioStorageClient.java
│
├── notarist-search/                       MODULE 4 — Hybrid search
│   ├── pom.xml
│   └── src/main/java/id/notarist/search/
│       ├── service/
│       │   ├── HybridSearchService.java
│       │   ├── RrfFusionService.java
│       │   ├── DiversityEnforcementService.java
│       │   ├── AccessFilterBuilder.java
│       │   └── SearchCacheService.java
│       └── client/
│           ├── QdrantSearchClient.java
│           ├── PostgresFullTextSearchService.java
│           └── RerankerClient.java
│
├── notarist-assistant/                    MODULE 5 — RAG & LLM
│   ├── pom.xml
│   └── src/main/java/id/notarist/assistant/
│       ├── service/
│       │   ├── AssistantService.java
│       │   ├── IntentClassifierService.java
│       │   ├── QueryPreprocessorService.java
│       │   ├── ContextAssemblerService.java
│       │   ├── CitationExtractorService.java
│       │   ├── ConversationService.java
│       │   └── FollowUpSuggestionService.java
│       └── client/
│           ├── LlmClient.java             (non-streaming)
│           └── StreamingLlmClient.java    (SSE/flux)
│
├── notarist-regulation/                   MODULE 6 — Regulation hierarchy
│   ├── pom.xml
│   └── src/main/java/id/notarist/regulation/
│       ├── entity/
│       │   ├── RegulasiMasterEntity.java
│       │   ├── RegulasiBabEntity.java
│       │   ├── RegulasiPasalEntity.java
│       │   ├── RegulasiAyatEntity.java
│       │   └── RegulasiCitationEntity.java
│       ├── service/
│       │   ├── RegulasiService.java
│       │   ├── RegulasiHierarchyService.java
│       │   ├── RegulasiPasalService.java
│       │   └── AmendmentService.java
│       └── repository/
│           ├── RegulasiMasterRepository.java
│           ├── RegulasiBabRepository.java
│           └── RegulasiPasalRepository.java
│
├── notarist-audit/                        MODULE 7 — Audit & security log
│   ├── pom.xml
│   └── src/main/java/id/notarist/audit/
│       ├── service/
│       │   ├── AuditTrailService.java
│       │   ├── DocumentAccessLogService.java
│       │   └── AiInteractionAuditService.java
│       └── aspect/
│           └── AuditLoggingAspect.java    (AOP)
│
└── notarist-web/                          MODULE 8 — Web layer (entry point)
    ├── pom.xml
    └── src/main/java/id/notarist/web/
        ├── NotaristApplication.java       (main entry)
        ├── controller/
        │   ├── AuthController.java
        │   ├── UserController.java
        │   ├── DocumentController.java
        │   ├── IngestController.java
        │   ├── SearchController.java
        │   ├── AssistantController.java
        │   ├── RegulationController.java
        │   ├── ChunkController.java
        │   ├── CitationController.java
        │   ├── AuditController.java
        │   └── AdminController.java
        ├── dto/
        │   ├── request/                   Request body DTOs
        │   └── response/                  Response DTOs
        ├── config/
        │   ├── WebMvcConfig.java
        │   ├── OpenApiConfig.java
        │   ├── CorsConfig.java
        │   └── AsyncConfig.java           (worker thread pool config)
        └── aspect/
            ├── RequestLoggingAspect.java
            ├── SensitiveMaskingAspect.java
            └── RateLimitAspect.java

    src/main/resources/
        ├── application.yml                (shared defaults)
        ├── application-local.yml
        ├── application-staging.yml
        └── application-production.yml
```

---

### Mobile Structure (React Native)

```
mobile/
│
├── package.json
├── tsconfig.json                          (strict mode)
├── babel.config.js
├── metro.config.js
├── .eslintrc.js
├── .prettierrc
│
├── android/                               ← Android native
├── ios/                                   ← iOS native
│
└── src/
    ├── api/
    │   ├── client.ts                      Axios instance + interceptors
    │   ├── sseClient.ts                   Custom EventSource (fetch-based)
    │   ├── queryClient.ts                 TanStack Query global config
    │   └── endpoints/
    │       ├── auth.api.ts
    │       ├── document.api.ts
    │       ├── ingest.api.ts
    │       ├── search.api.ts
    │       ├── assistant.api.ts
    │       ├── regulation.api.ts
    │       ├── citation.api.ts
    │       ├── chunk.api.ts
    │       ├── audit.api.ts
    │       └── admin.api.ts
    │
    ├── components/
    │   ├── common/
    │   │   ├── Button.tsx
    │   │   ├── Input.tsx
    │   │   ├── Badge.tsx
    │   │   ├── Card.tsx
    │   │   ├── Skeleton.tsx               (loading placeholder)
    │   │   ├── ErrorBoundary.tsx
    │   │   └── OfflineBanner.tsx
    │   ├── document/
    │   │   ├── DocumentCard.tsx
    │   │   ├── StatusBadge.tsx
    │   │   ├── ClassificationBadge.tsx
    │   │   └── PipelineProgress.tsx
    │   ├── search/
    │   │   ├── SearchBar.tsx
    │   │   ├── FilterPanel.tsx
    │   │   └── ResultCard.tsx
    │   ├── assistant/
    │   │   ├── ChatBubble.tsx
    │   │   ├── CitationCard.tsx
    │   │   ├── TypingIndicator.tsx
    │   │   └── StreamTokenRenderer.tsx    (incremental text)
    │   ├── regulation/
    │   │   ├── BabAccordion.tsx           (lazy loading accordion)
    │   │   ├── PasalItem.tsx
    │   │   ├── AyatItem.tsx
    │   │   └── AmendmentBadge.tsx
    │   ├── graph/
    │   │   └── RelationshipGraph.tsx      (react-native-svg based)
    │   └── upload/
    │       ├── FilePicker.tsx
    │       └── UploadProgressBar.tsx
    │
    ├── hooks/
    │   ├── useSearch.ts
    │   ├── useAssistant.ts
    │   ├── useDocumentUpload.ts
    │   ├── useRegulationTree.ts           (lazy load accordion state)
    │   ├── useOffline.ts
    │   ├── usePermissions.ts
    │   └── usePipelineStatus.ts           (polling)
    │
    ├── navigation/
    │   ├── RootNavigator.tsx
    │   ├── AuthNavigator.tsx
    │   ├── MainNavigator.tsx              (Bottom Tab)
    │   ├── DocumentNavigator.tsx
    │   ├── AssistantNavigator.tsx
    │   └── types.ts                       (navigation param types)
    │
    ├── screens/
    │   ├── auth/
    │   │   └── LoginScreen.tsx
    │   ├── dashboard/
    │   │   └── DashboardScreen.tsx
    │   ├── search/
    │   │   ├── SearchScreen.tsx
    │   │   └── SearchResultScreen.tsx
    │   ├── assistant/
    │   │   ├── ConversationListScreen.tsx
    │   │   └── AssistantChatScreen.tsx
    │   ├── document/
    │   │   ├── DocumentListScreen.tsx
    │   │   ├── DocumentDetailScreen.tsx
    │   │   ├── DocumentUploadScreen.tsx
    │   │   ├── OcrReviewScreen.tsx
    │   │   ├── ChunkExplorerScreen.tsx
    │   │   └── RelationshipGraphScreen.tsx
    │   ├── profile/
    │   │   ├── ProfileScreen.tsx
    │   │   ├── AuditScreen.tsx
    │   │   └── AdminScreen.tsx
    │   └── modals/
    │       ├── RegulationExplorerModal.tsx
    │       ├── CitationFullViewModal.tsx
    │       ├── DocumentViewerModal.tsx
    │       ├── PasalDetailModal.tsx
    │       └── AuditDetailModal.tsx
    │
    ├── store/
    │   ├── auth.store.ts
    │   ├── ui.store.ts
    │   ├── offline.store.ts
    │   └── conversation.store.ts
    │
    ├── theme/
    │   ├── colors.ts                      (semantic color tokens)
    │   ├── spacing.ts                     (spacing tokens)
    │   ├── typography.ts                  (font tokens)
    │   └── index.ts                       (export all tokens)
    │   NOTE: Theme-token-based — dark mode siap ditambahkan v2
    │         tanpa rewrite besar.
    │
    ├── types/
    │   ├── api.types.ts                   (generated from OpenAPI)
    │   ├── domain.types.ts
    │   └── navigation.types.ts
    │
    └── utils/
        ├── fieldMasker.ts
        ├── legalAbbreviations.ts
        ├── dateFormatter.ts
        └── tokenCounter.ts
```

---

### Python Sidecars Structure

```
services/
│
├── ocr-service/                           PaddleOCR REST API
│   ├── pyproject.toml                     (Poetry)
│   ├── Dockerfile
│   └── src/
│       ├── main.py                        FastAPI app
│       ├── ocr_engine.py                  PaddleOCR wrapper
│       ├── preprocessor.py                Image preprocessing
│       ├── postprocessor.py               Text cleanup + redaction
│       └── health.py                      Health endpoint
│
├── ner-service/                           IndoBERT NER REST API
│   ├── pyproject.toml
│   ├── Dockerfile
│   ├── models/                            Downloaded IndoBERT model weights
│   └── src/
│       ├── main.py                        FastAPI app
│       ├── ner_engine.py                  IndoBERT inference
│       ├── rule_extractor.py              Regex/rule patterns
│       └── entity_normalizer.py           Normalize extracted entities
│
└── reranker-service/                      Cross-encoder Reranker REST API
    ├── pyproject.toml
    ├── Dockerfile
    ├── models/                            Downloaded reranker model weights
    └── src/
        ├── main.py                        FastAPI app
        └── reranker_engine.py             Cross-encoder inference
```

---

### Contracts Structure

```
contracts/
│
├── openapi/
│   ├── notarist-api.yaml                  ← MASTER OpenAPI 3.1 spec
│   ├── components/
│   │   ├── schemas/                       Reusable schema components
│   │   │   ├── DocumentResponse.yaml
│   │   │   ├── SearchRequest.yaml
│   │   │   ├── AssistantAskRequest.yaml
│   │   │   ├── CitationItem.yaml
│   │   │   ├── RegulationStructure.yaml
│   │   │   └── ...
│   │   ├── parameters/                    Common query params
│   │   └── responses/                    Standard error responses
│   └── paths/                             Endpoint definitions
│       ├── auth.yaml
│       ├── documents.yaml
│       ├── search.yaml
│       ├── assistant.yaml
│       ├── regulations.yaml
│       └── audit.yaml
│
└── events/
    ├── UploadValidatedEvent.v1.schema.json
    ├── OcrCompleteEvent.v1.schema.json
    ├── NerCompleteEvent.v1.schema.json
    ├── ChunkingCompleteEvent.v1.schema.json
    ├── EmbeddingCompleteEvent.v1.schema.json
    ├── DocActiveEvent.v1.schema.json
    └── PipelineFailureEvent.v1.schema.json
```

---

### Infrastructure Structure

```
infra/
│
├── docker/
│   ├── docker-compose.local.yml           ← Local development (all services)
│   ├── docker-compose.staging.yml         ← Staging deployment
│   ├── docker-compose.production.yml      ← Production deployment
│   └── docker-compose.override.yml        ← Local overrides (gitignored)
│
├── minio/
│   ├── bucket-policies/
│   │   ├── notarist-raw-policy.json
│   │   ├── notarist-processed-policy.json
│   │   └── notarist-export-policy.json
│   └── lifecycle-policies/
│       ├── export-lifecycle.json          (auto-delete export after 30 days)
│       └── backup-retention.json
│
├── postgres/
│   ├── init/
│   │   ├── 01_create_schema.sql           (CREATE SCHEMA rag;)
│   │   └── 02_install_extensions.sql      (uuid-ossp, pg_trgm)
│   └── migrations/                        Flyway migration files
│       ├── V1__initial_rag_schema.sql
│       ├── V2__add_pipeline_queue.sql
│       └── V3__add_views.sql
│
├── oracle/
│   └── migrations/                        Liquibase changesets
│       ├── changelog-master.xml
│       ├── v1_notarist_schema.xml
│       ├── v1_notarist_stg_schema.xml
│       └── v1_notarist_sec_schema.xml
│
├── qdrant/
│   └── collection-config/
│       └── notarist_legal_docs.json       Collection creation config
│
├── nginx/
│   ├── nginx.conf                         Main nginx config
│   └── conf.d/
│       ├── notarist-api.conf              API routing
│       └── minio.conf                     MinIO proxy config
│
└── scripts/
    ├── create-minio-buckets.sh            Create all 6 buckets
    ├── create-qdrant-collection.sh        Create Qdrant collection
    └── init-oracle-schema.sh              Run Liquibase on Oracle
```

---

## DEPLOYMENT ARCHITECTURE

### Service Map

```
┌─────────────────────────────────────────────────────────────────────┐
│                         DOCKER COMPOSE STACK                        │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ NGINX (reverse proxy — port 80/443)                         │   │
│  │ Route:  /api/v1/*       → notarist-api:8080                 │   │
│  │         /minio/*        → minio:9001 (console)              │   │
│  └─────────────────────────────┬───────────────────────────────┘   │
│                                │                                     │
│  ┌─────────────────────────────▼───────────────────────────────┐   │
│  │ NOTARIST-API (Spring Boot — port 8080)                      │   │
│  │ Connects to:                                                │   │
│  │  • Oracle 19C (EXTERNAL — not in compose)                   │   │
│  │  • PostgreSQL :5432                                         │   │
│  │  • Qdrant :6333                                             │   │
│  │  • MinIO :9000                                              │   │
│  │  • ocr-service :8081                                        │   │
│  │  • ner-service :8082                                        │   │
│  │  • reranker-service :8083                                   │   │
│  │  • Ollama :11434                                            │   │
│  └──────────────────────────────────────────────────────────── ┘   │
│                                                                     │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐   │
│  │ ocr-service  │ │ ner-service  │ │ reranker-service         │   │
│  │ :8081        │ │ :8082        │ │ :8083                    │   │
│  │ PaddleOCR    │ │ IndoBERT NER │ │ Cross-encoder            │   │
│  │ + Tesseract  │ │ + Rules      │ │                          │   │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘   │
│                                                                     │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐               │
│  │ PostgreSQL   │ │ Qdrant       │ │ MinIO        │               │
│  │ :5432        │ │ :6333 :6334  │ │ :9000 :9001  │               │
│  │ notarist_rag │ │ legal_docs   │ │ 6 buckets    │               │
│  └──────────────┘ └──────────────┘ └──────────────┘               │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │ Ollama (local LLM) — :11434                                  │  │
│  │ Loaded model: llama3 / mistral / dll (configurable)          │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ JDBC (ojdbc11)
                              ▼
               ┌─────────────────────────────┐
               │ ORACLE 19C (EXTERNAL)       │
               │ Schemas: NOTARIST           │
               │          NOTARIST_STG       │
               │          NOTARIST_SEC       │
               └─────────────────────────────┘
```

### Port Map

```
SERVICE               PORT(S)   PROTOCOL   NOTES
─────────────────────────────────────────────────────────
Nginx (public)        80, 443   HTTP/HTTPS External entry point
notarist-api          8080      HTTP       Internal only (via Nginx)
ocr-service           8081      HTTP       Internal only
ner-service           8082      HTTP       Internal only
reranker-service      8083      HTTP       Internal only
Ollama                11434     HTTP       Internal only
PostgreSQL            5432      TCP        Internal only
Qdrant REST           6333      HTTP       Internal only
Qdrant gRPC           6334      gRPC       Internal only (optional)
MinIO API             9000      HTTP       Internal (+ signed URL external)
MinIO Console         9001      HTTP       Internal admin only
Oracle 19C            1521      TCP        External (existing server)
```

### Docker Compose Service Definitions (Concept)

```yaml
CONCEPT (bukan implementasi YAML):

services:

  nginx:
    image: nginx:stable-alpine
    ports: [80:80, 443:443]
    volumes: [nginx config, SSL certs]
    depends_on: [notarist-api]

  notarist-api:
    image: notarist/api:${VERSION}
    build: ./backend
    env_from: .env
    volumes: [temp processing dir]
    depends_on: [postgres, qdrant, minio]
    healthcheck: GET /actuator/health

  ocr-service:
    image: notarist/ocr:${VERSION}
    build: ./services/ocr-service
    env: [OCR_ENGINE=paddleocr, CONFIDENCE_THRESHOLD=0.70]
    volumes: [temp file cache]
    resources: [CPU 2 cores, RAM 4GB]

  ner-service:
    image: notarist/ner:${VERSION}
    build: ./services/ner-service
    volumes: [model weights mount — NOT in image for size]
    resources: [CPU 2 cores, RAM 4GB]

  reranker-service:
    image: notarist/reranker:${VERSION}
    build: ./services/reranker-service
    volumes: [model weights mount]
    resources: [CPU 2 cores, RAM 2GB]

  ollama:
    image: ollama/ollama:latest
    volumes: [ollama models]
    resources: [CPU 4 cores, RAM 8GB, optional GPU]

  postgres:
    image: postgres:16-alpine
    volumes: [data persistent volume]
    env: [POSTGRES_DB=notarist_rag, POSTGRES_USER, POSTGRES_PASSWORD]
    healthcheck: pg_isready

  qdrant:
    image: qdrant/qdrant:latest
    volumes: [data persistent volume, snapshot volume]
    env: [QDRANT_API_KEY]

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    volumes: [data persistent volume]
    env: [MINIO_ROOT_USER, MINIO_ROOT_PASSWORD]
    healthcheck: curl /minio/health/ready
```

### MinIO Bucket Architecture

```
BUCKET: notarist-raw
  Purpose:   Original uploaded files — immutable
  Policy:    Write: notarist-api only. Read: notarist-api only (signed URL)
  Encryption: SSE-S3 (AES-256)
  Versioning: ENABLED (preserve original always)
  Lifecycle:  NONE (permanent — legal document retention)
  Path structure: /{doc_id}/{original_filename}

BUCKET: notarist-ocr
  Purpose:   OCR intermediate artifacts (per-page JSON, confidence data)
  Policy:    Write: ocr-service + notarist-api. Read: notarist-api
  Lifecycle:  Delete after 90 days (intermediate, rebuilding possible)
  Path: /{doc_id}/page_{n}/ocr_result.json

BUCKET: notarist-processed
  Purpose:   Normalized/searchable PDF, cleaned text file
  Policy:    Write: notarist-api. Read: notarist-api (signed URL for app)
  Lifecycle:  Same as notarist-raw (retain with document)
  Path: /{doc_id}/processed.pdf  |  /{doc_id}/cleaned_text.txt

BUCKET: notarist-chunk
  Purpose:   Chunk artifacts (serialized chunk data for re-indexing)
  Policy:    Write/Read: notarist-api only
  Lifecycle:  Delete after document deleted/archived
  Path: /{doc_id}/chunks.jsonl

BUCKET: notarist-export
  Purpose:   AI-generated export packages, citation PDFs
  Policy:    Write: notarist-api. Read: signed URL (user download)
  Lifecycle:  Auto-delete after 30 days
  Path: /{user_id}/{export_id}/export.pdf

BUCKET: notarist-backup
  Purpose:   Periodic full backup
  Policy:    Write: backup job only. Read: admin only
  Versioning: ENABLED
  Lifecycle:  Delete after 365 days
  Path: /{YYYY-MM-DD}/{component}/{filename}
```

---

## DEV WORKFLOW

### Initial Setup (First Time)

```
SETUP SEQUENCE:

1. PREREQUISITES (developer machine):
   ○ Docker Desktop (or Docker Engine + Compose)
   ○ Java 17 (Amazon Corretto atau Eclipse Temurin)
   ○ Maven 3.9+
   ○ Node.js 20 LTS
   ○ Python 3.11
   ○ Poetry
   ○ Android Studio (untuk Android emulator)
   ○ ojdbc11.jar (dari Oracle website, placed ke local Maven repo)

2. CLONE & CONFIGURE:
   git clone git@{host}:notarist/notarist-rag.git
   cd notarist-rag
   cp .env.example .env.local
   # Edit .env.local dengan:
   # - Oracle DB credentials (dev instance)
   # - JWT private/public key (generate RSA keypair)
   # - MinIO credentials
   # - Ollama model name

3. START INFRASTRUCTURE:
   make dev-up
   # Starts: PostgreSQL, Qdrant, MinIO, sidecars, Ollama
   # Oracle: connects to existing dev Oracle server

4. DATABASE MIGRATION:
   make db-migrate
   # Runs:
   # - Flyway migrations on PostgreSQL (creates rag schema + tables)
   # - Liquibase changesets on Oracle (creates NOTARIST, STG, SEC schemas)

5. INITIALIZE INFRASTRUCTURE:
   make create-minio-buckets
   make create-qdrant-collection

6. SEED TEST DATA (optional):
   make seed-data
   # Inserts: sample users, sample documents, sample regulasi

7. START BACKEND:
   make build-backend
   # atau:
   cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local

8. START MOBILE (new terminal):
   cd mobile && npm install
   npx react-native start     # Metro bundler
   # (separate terminal)
   npx react-native run-android   # atau run-ios

9. VERIFY:
   make check-health
   # Checks: API, OCR, NER, Reranker, Ollama, Postgres, Qdrant, MinIO
```

### Daily Dev Workflow

```
EVERYDAY STARTUP:
  make dev-up                     (start infra if not running)
  cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
  cd mobile && npx react-native start

MAKING API CHANGES:
  1. Edit OpenAPI spec: contracts/openapi/notarist-api.yaml
  2. make generate-api
     → Regenerates: backend controller interface stubs
     → Regenerates: mobile TypeScript API types
  3. Implement backend changes (notarist-web controllers, service layer)
  4. Test dengan: curl atau Postman (import generated OpenAPI spec)
  5. Mobile types sudah sync otomatis

DATABASE MIGRATION:
  PostgreSQL: add new file di infra/postgres/migrations/V{N}__description.sql
              make db-migrate (Flyway auto-applies pending)
  Oracle:     add changeset di infra/oracle/migrations/
              make db-migrate (Liquibase auto-applies pending)

ADDING NEW SIDECAR ENDPOINT:
  1. Edit sidecar service code (Python)
  2. Rebuild: docker-compose build ocr-service (atau ner-service)
  3. Restart: docker-compose restart ocr-service
  4. Update Java HTTP client class jika ada perubahan contract

MODEL UPDATE (Ollama):
  ollama pull {new-model-name}
  Update: .env.local → OLLAMA_MODEL={new-model-name}
  Restart: backend (config reload)

QDRANT COLLECTION RESET (jika perlu):
  make reset-qdrant-collection
  make db-migrate                 (reset embedding_metadata status)
  make dev-reindex-all            (trigger bulk re-indexing)
```

---

## OPENAPI GENERATION FLOW

### API-First Workflow

```
SOURCE OF TRUTH: contracts/openapi/notarist-api.yaml

GENERATION TARGETS:

┌─────────────────────────────────────────────────────────────┐
│         contracts/openapi/notarist-api.yaml                 │
│                   (SINGLE SOURCE)                           │
└──────────────────────────┬──────────────────────────────────┘
                           │
              ┌────────────┼────────────┐
              │                         │
              ▼                         ▼
┌─────────────────────────┐  ┌────────────────────────────┐
│ BACKEND GENERATION      │  │ MOBILE GENERATION          │
│                         │  │                            │
│ Tool: openapi-generator │  │ Tool: openapi-generator    │
│       Maven plugin      │  │       npm (openapi-ts)     │
│                         │  │                            │
│ Output:                 │  │ Output:                    │
│  Controller interfaces  │  │  mobile/src/types/         │
│  (Spring annotations)   │  │  api.types.ts              │
│  Request/Response DTOs  │  │  (TypeScript interfaces)   │
│  Model classes          │  │                            │
│                         │  │ Developer edits:           │
│ Developer edits:        │  │  api/endpoints/*.api.ts    │
│  service layer only     │  │  (Axios calls using types) │
│  (controller generated) │  │                            │
└─────────────────────────┘  └────────────────────────────┘

WORKFLOW:
  1. API Designer edits: contracts/openapi/notarist-api.yaml
  2. Run: make generate-api
  3. Generated stubs appear in backend + mobile
  4. Backend developer implements service logic (not controller)
  5. Mobile developer implements UI (not API types)
  6. If API changes again → repeat step 2 → only changed parts regenerate

VALIDATION:
  CI step: validate OpenAPI spec before generation (openapi-lint)
  CI step: generate and check no compile errors (backend)
  CI step: generate and check no TS type errors (mobile)
```

### Event Schema Synchronization

```
EVENT SCHEMAS: contracts/events/*.v1.schema.json

USAGE:
  Spring Boot: Jackson deserialization validates against schema
  Python sidecars: jsonschema library validates pipeline events

VERSIONING STRATEGY:
  Event name format: {EventName}.{version}.schema.json
  New version → new file (OldVersion.v1, NewVersion.v2)
  Consumer supports N and N+1 version simultaneously (during transition)
  After transition complete → remove N version support

EXAMPLE: OcrCompleteEvent schema:
  {
    "eventType": "OcrCompleteEvent",
    "version": "1",
    "properties": {
      "docId": string (UUID),
      "avgConfidence": number (0.0-1.0),
      "pageCount": integer,
      "hasLowConfidencePages": boolean,
      "requiresManualReview": boolean,
      "processingTimeMs": integer,
      "workerId": string
    }
  }
```

---

## INFRASTRUCTURE FLOW

### Database Migration Flow (Flyway + Liquibase)

```
POSTGRESQL — Flyway:
  Migration files: infra/postgres/migrations/
  Naming: V{N}__{description}.sql
  Run: embedded in Spring Boot startup (auto-run) atau make db-migrate
  Table: flyway_schema_history (dalam schema rag)
  Strategy: always forward, no rollback
  Lock: pessimistic lock prevents concurrent migration

ORACLE — Liquibase:
  Changeset files: infra/oracle/migrations/changelog-master.xml
  Includes: v1_notarist_schema.xml, v1_stg.xml, v1_sec.xml
  Run: separate step (make db-migrate-oracle), tidak auto-run saat startup
  Reason: Oracle perlu credential yang lebih restricted untuk DDL
  Strategy: always forward
  Lock: Liquibase internal lock table

MIGRATION DISCIPLINE:
  ✓ Setiap schema change wajib ada migration file
  ✓ Tidak ada manual DDL langsung ke DB (kecuali emergency dengan approval)
  ✓ Migration file di-commit bersamaan dengan code yang membutuhkan schema baru
  ✓ Migration file tidak pernah diedit setelah di-commit
  ✓ Jika butuh change → tambah migration baru (tidak edit lama)
```

### Qdrant Data Flow

```
COLLECTION LIFECYCLE:

  CREATION (one-time, via script):
    notarist_legal_docs collection dengan config:
    - vector size: 1024
    - distance: Cosine
    - HNSW: m=16, ef_construct=100
    - Payload indexes: jenis_dokumen, jenis_akta, klasifikasi, doc_id,
                       tanggal_dokumen, is_searchable, is_active

  INGESTION (per document):
    notarist-api → PUT /collections/notarist_legal_docs/points
    Payload: per embedding_metadata design (STEP 3)

  RETRIEVAL (per query):
    notarist-api → POST /collections/notarist_legal_docs/points/search
    With: vector + filter (access control) + limit

  SNAPSHOT (daily backup):
    POST /collections/notarist_legal_docs/snapshots
    Stores: infra/qdrant/snapshots/ atau MinIO notarist-backup bucket

  RECOVERY:
    DELETE collection → recreate → restore from snapshot
    Atau: re-index dari PostgreSQL (slower, tapi selalu bisa)

QDRANT MEMORY ESTIMATION:
  Vectors: 1024 float32 = 4096 bytes per vector
  1000 chunks → ~4 MB
  10,000 chunks → ~40 MB
  100,000 chunks → ~400 MB
  HNSW overhead: ~2x base size
  100,000 chunks total → ~800 MB RAM
  Conservative allocation: 2 GB RAM untuk Qdrant service
```

### Oracle Integration Strategy

```
ORACLE 19C — EXTERNAL (tidak di-containerize):

JDBC CONNECTION CONFIG:
  URL:    jdbc:oracle:thin:@{host}:{port}/{service_name}
  Driver: ojdbc11 (Oracle 19c compatible)
  Pool:   HikariCP

  Dev Oracle:
    Max pool size: 10 connections per schema
    Connection timeout: 30s
    Idle timeout: 600s

  Prod Oracle:
    Max pool size: 20 connections per schema
    Connection timeout: 30s
    Validation query: SELECT 1 FROM DUAL

MULTI-SCHEMA CONNECTION:
  Spring Boot dikonfigurasi dengan SATU Oracle connection
  Schema switching via: ALTER SESSION SET CURRENT_SCHEMA = {schema}
  Atau: fully qualified table names: NOTARIST.DOC_MASTER
  Rekomendasi: fully qualified names (lebih explicit, audit-friendly)

ORACLE DRIVER MANAGEMENT:
  ojdbc11.jar TIDAK tersedia di Maven Central (license restriction)
  Setup lokal:
    mvn install:install-file -Dfile=ojdbc11.jar -DgroupId=com.oracle.database.jdbc
        -DartifactId=ojdbc11 -Dversion=19.x -Dpackaging=jar
  Atau: gunakan Oracle Maven repository (memerlukan account Oracle)
  Untuk CI: simpan jar di private Maven repository (Nexus/Artifactory)

ORACLE VPD SETUP:
  VPD policy functions di-create via Liquibase changeset
  Attached ke: DOC_MASTER, AKTA_MASTER
  Context variable: SYS_CONTEXT('NOTARIST_CTX', 'USER_ROLE')
  Set saat login: DBMS_SESSION.SET_CONTEXT('NOTARIST_CTX', 'USER_ROLE', {role})
  Spring Boot calls context setup proc setelah mendapat connection dari pool
```

---

## CI/CD ARCHITECTURE

### Pipeline Stages

```
TRIGGER: Push to branch atau Merge Request

┌──────────────────────────────────────────────────────────────┐
│ STAGE 1: CODE QUALITY                                        │
│  ├── Lint OpenAPI spec (spectral atau redocly)              │
│  ├── Java: checkstyle + spotbugs                            │
│  ├── TypeScript: eslint + tsc --noEmit                      │
│  └── Python: flake8 + mypy                                  │
└──────────────────────────────────────────────────────────────┘
                    │ pass
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ STAGE 2: GENERATE & VALIDATE CONTRACTS                       │
│  ├── generate-api (OpenAPI → backend stubs + mobile types)  │
│  ├── Verify backend compiles with generated stubs           │
│  └── Verify mobile types compile (tsc)                      │
└──────────────────────────────────────────────────────────────┘
                    │ pass
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ STAGE 3: UNIT TESTS                                          │
│  ├── Java: mvn test (JUnit 5)                               │
│  ├── TypeScript: jest                                       │
│  └── Python: pytest                                         │
└──────────────────────────────────────────────────────────────┘
                    │ pass
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ STAGE 4: INTEGRATION TESTS (only on main branch + staging)  │
│  ├── Start: testcontainers (Postgres, Qdrant, MinIO)        │
│  ├── Run Flyway migrations                                  │
│  ├── Run Spring Boot integration tests (@SpringBootTest)    │
│  └── Verify critical pipelines (upload → OCR stub → chunk) │
└──────────────────────────────────────────────────────────────┘
                    │ pass
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ STAGE 5: BUILD DOCKER IMAGES                                 │
│  ├── Build: notarist/api:${GIT_SHA}                         │
│  ├── Build: notarist/ocr:${GIT_SHA}                         │
│  ├── Build: notarist/ner:${GIT_SHA}                         │
│  └── Build: notarist/reranker:${GIT_SHA}                    │
└──────────────────────────────────────────────────────────────┘
                    │ pass
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ STAGE 6: PUSH TO REGISTRY                                    │
│  Push all images to private Docker registry                  │
│  Tag: ${GIT_SHA} + (if main branch) latest                  │
└──────────────────────────────────────────────────────────────┘
                    │ pass (main branch only)
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ STAGE 7: DEPLOY TO STAGING (auto, main branch)              │
│  ├── SSH ke staging server                                  │
│  ├── docker-compose pull (latest images)                    │
│  ├── docker-compose up -d (zero-downtime tidak guaranteed)  │
│  ├── Run: make db-migrate (Flyway + Liquibase)              │
│  └── Run: make check-health (semua services healthy)        │
└──────────────────────────────────────────────────────────────┘
                    │ pass + MANUAL APPROVAL GATE
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ STAGE 8: DEPLOY TO PRODUCTION (manual trigger)              │
│  Same as staging deployment                                  │
│  Additional: notify Slack/email setelah deploy              │
└──────────────────────────────────────────────────────────────┘

BRANCH STRATEGY:
  main       → deploys to staging (auto) + production (manual)
  feature/*  → runs Stage 1-5 only
  hotfix/*   → runs Stage 1-5, dapat deploy production langsung (dengan approval)

PRIVATE DOCKER REGISTRY:
  Options: Gitea Container Registry, Harbor, atau simple Docker registry server
  Accessible dari staging + production servers via internal network
```

---

## LOGGING & OBSERVABILITY

### Log Strategy

```
APPLICATION LOGGING (Spring Boot + Logback):

  FORMAT: Structured JSON (tidak plain text — lebih mudah diparse)
  FIELDS PER LOG ENTRY:
    timestamp, level, logger, message,
    requestId, userId, docId (if applicable),
    executionTimeMs (if applicable),
    exception (if applicable)

  LOG LEVELS:
    ERROR → unexpected failures, exception traces
    WARN  → OCR low confidence, retry attempts, rate limit approached
    INFO  → pipeline stage transitions, API requests (summarized)
    DEBUG → detailed pipeline steps (local only, tidak untuk production)
    TRACE → raw request/response (never in production)

  SENSITIVE DATA IN LOGS:
    ✗ NEVER log: NIK, NPWP, NILAI_TRANSAKSI, password, JWT token
    ✓ Log: doc_id, user_id, chunk_id, event type, duration

LOG DESTINATIONS:
  Local dev:    stdout (readable in docker logs)
  Staging/Prod: stdout → collected by Promtail → Loki

SIDECAR LOGS (Python):
  Same structured JSON format (Python logging + structlog)
  Same Promtail collection
```

### Observability Stack

```
STACK: Prometheus + Grafana + Loki  (all on-premise, lightweight)

METRICS (Prometheus scrape dari Spring Boot Actuator):

  BUSINESS METRICS (custom):
    notarist_documents_uploaded_total         (counter)
    notarist_documents_active_total           (gauge)
    notarist_documents_pending_review_total   (gauge)
    notarist_pipeline_stage_duration_seconds  (histogram, per stage)
    notarist_ocr_confidence_score             (histogram)
    notarist_rag_query_duration_seconds       (histogram)
    notarist_rag_retrieval_score              (histogram, semantic+rerank)
    notarist_llm_tokens_total                 (counter, prompt+completion)
    notarist_qdrant_sync_discrepancy_rate     (gauge)

  INFRASTRUCTURE METRICS (built-in):
    JVM: heap, GC, threads
    HikariCP: active/idle connections, wait time
    HTTP: request rate, latency, error rate
    Qdrant: built-in metrics endpoint
    PostgreSQL: pg_stat via postgres-exporter sidecar

GRAFANA DASHBOARDS (to be created):
  1. Pipeline Health Dashboard
     - Queue depths per stage
     - Processing duration per stage (P50, P95)
     - Error rate per stage
     - OCR confidence distribution
  2. RAG Quality Dashboard
     - Query latency (P50, P95, P99)
     - Retrieval score distribution
     - LLM token usage
     - Cache hit rate
  3. Infrastructure Dashboard
     - Qdrant memory + vector count
     - PostgreSQL connections + query time
     - MinIO storage usage per bucket
     - JVM health

ALERTING (Grafana Alerting):
  ├── Pipeline error rate > 5% → alert admin
  ├── OCR average confidence < 0.70 → alert admin
  ├── Qdrant sync discrepancy > 2% → alert admin
  ├── LLM service unhealthy > 5 min → alert admin
  ├── Postgres connection pool > 90% → alert admin
  └── MinIO storage > 80% capacity → alert admin
```

---

## BACKUP & RECOVERY STRATEGY

### Backup Schedule

```
COMPONENT       | METHOD                          | FREQUENCY    | RETENTION
────────────────|─────────────────────────────────|──────────────|──────────
Oracle 19C      | RMAN full + incremental         | Daily full   | 30 hari
                | (managed by DBA team)           | Hourly incr  | 7 hari
────────────────|─────────────────────────────────|──────────────|──────────
PostgreSQL      | pg_dump (schema rag)            | Daily 01:00  | 30 hari
                | WAL archiving                   | Continuous   | 7 hari
                | → enables PITR (point-in-time)  |              |
────────────────|─────────────────────────────────|──────────────|──────────
MinIO           | mc mirror ke notarist-backup    | Daily 02:00  | 365 hari
                | (replication dalam bucket)      |              |
────────────────|─────────────────────────────────|──────────────|──────────
Qdrant          | POST /snapshots API             | Daily 03:00  | 7 hari
                | → stored ke notarist-backup     |              |
────────────────|─────────────────────────────────|──────────────|──────────
Config & Code   | Git (all configs in repo)       | Per commit   | Indefinite
────────────────|─────────────────────────────────|──────────────|──────────
Docker images   | Private registry               | Per CI build | 30 hari
────────────────|─────────────────────────────────|──────────────|──────────
Model weights   | Stored di server, versioned    | Per update   | All vers.
(IndoBERT,      | Reference di .env              |              |
 Reranker)      |                                |              |

CATATAN: Backup Oracle adalah tanggung jawab DBA team (existing procedure).
         Notarist RAG team hanya berkoordinasi untuk restorasi.
```

### Recovery Procedures (Design, bukan implementasi)

```
SCENARIO 1: PostgreSQL corruption / data loss
  Recovery:
  1. Stop notarist-api (tidak ada write ke PG)
  2. Restore pg_dump terbaru: pg_restore ke notarist_rag
  3. Atau: PITR via WAL ke titik waktu tertentu
  4. Verify: count rows vs backup timestamp
  5. Start notarist-api
  RTO: < 2 jam | RPO: < 24 jam (atau < 1 jam dengan WAL PITR)

SCENARIO 2: Qdrant data loss / collection corruption
  Recovery (Option A — fast):
  1. Restore Qdrant snapshot (harian)
  2. Re-index dokumen yang diupload setelah snapshot timestamp
  3. RTO: < 1 jam | RPO: < 24 jam

  Recovery (Option B — tanpa snapshot):
  1. Qdrant collection kosong
  2. Set embedding_metadata.indexing_status = 'OUTDATED' semua
  3. Trigger full re-indexing dari PostgreSQL (doc_chunk tabel)
  4. RTO: hours (tergantung volume) | RPO: 0 jam (data di PostgreSQL aman)

SCENARIO 3: MinIO data loss
  Recovery:
  1. Restore dari notarist-backup bucket (via mc mirror reverse)
  2. Atau: upload ulang dari notarist-raw (dokumen asli tetap ada)
  RTO: < 4 jam | RPO: < 24 jam

SCENARIO 4: Spring Boot app crash / restart
  Recovery: docker-compose restart notarist-api
  Stateless app: no data loss, workers resume from pipeline_queue
  RTO: < 5 menit
```

---

## ENVIRONMENT STRATEGY

### Environment Variables (Concept)

```
.env.example (template di git — NO SECRETS):

# SPRING
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8080

# ORACLE (EXTERNAL)
ORACLE_HOST=localhost
ORACLE_PORT=1521
ORACLE_SERVICE=XEPDB1
ORACLE_SCHEMA=NOTARIST
ORACLE_USER=notarist_app
ORACLE_PASSWORD=          ← empty in example, filled in .env.local

# POSTGRESQL
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=notarist_rag
POSTGRES_USER=rag_app
POSTGRES_PASSWORD=

# QDRANT
QDRANT_HOST=qdrant
QDRANT_PORT=6333
QDRANT_API_KEY=
QDRANT_COLLECTION=notarist_legal_docs

# MINIO
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=
MINIO_BUCKET_RAW=notarist-raw
MINIO_BUCKET_PROCESSED=notarist-processed
MINIO_BUCKET_EXPORT=notarist-export

# JWT (RS256)
JWT_PRIVATE_KEY_PATH=/etc/secrets/jwt-private.pem
JWT_PUBLIC_KEY_PATH=/etc/secrets/jwt-public.pem
JWT_ACCESS_TOKEN_EXPIRY_MINUTES=60
JWT_REFRESH_TOKEN_EXPIRY_DAYS=7

# OCR SIDECAR
OCR_SERVICE_URL=http://ocr-service:8081
OCR_CONFIDENCE_THRESHOLD=0.70

# NER SIDECAR
NER_SERVICE_URL=http://ner-service:8082

# RERANKER SIDECAR
RERANKER_SERVICE_URL=http://reranker-service:8083

# OLLAMA LLM
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=llama3:8b
OLLAMA_TIMEOUT_SECONDS=60

# PIPELINE
PIPELINE_BATCH_SIZE_EMBEDDING=32
PIPELINE_OCR_WORKERS=2
PIPELINE_NER_WORKERS=2
PIPELINE_EMBEDDING_WORKERS=1

# RATE LIMITING
RATE_LIMIT_UPLOADS_PER_MIN=5
RATE_LIMIT_QUERIES_PER_MIN=60

ENVIRONMENT-SPECIFIC OVERRIDES:
  .env.local:       relaxed limits, debug=true, verbose logs
  .env.staging:     production-like, limited resources
  .env.production:  full security, resource limits, no debug

Spring Boot Profile-Specific YAML:
  application-local.yml:
    logging.level.id.notarist: DEBUG
    spring.jpa.show-sql: true
  application-production.yml:
    logging.level.id.notarist: INFO
    spring.jpa.show-sql: false
```

---

## SECURITY MODEL

### Secret Management

```
DEVELOPMENT:
  Secrets in .env.local (gitignored)
  JWT keys generated locally (openssl genrsa)
  Dev Oracle: shared dev credentials (tidak production credentials)

STAGING / PRODUCTION:
  Secrets NOT in .env files
  Deployment: environment variables di-inject saat container start
  Method: docker-compose secrets atau environment inject via CI/CD

  Options (on-premise):
  1. HashiCorp Vault (dedicated secret management)
  2. Docker secrets (built-in compose support)
  3. CI/CD masked variables + environment inject (simpler, for small teams)

  Recommendation: CI/CD masked variables (Option 3) untuk initial deployment.
                  Upgrade ke Vault jika tim/security requirements bertambah.

JWT KEY MANAGEMENT:
  RSA-2048 keypair per environment
  Private key: server-side only, never in git
  Public key: dapat di-share ke services yang perlu verify
  Rotation: annual atau saat ada compromise
  Storage: file di server (path di .env), atau secret manager
```

### Network Security

```
NETWORK ISOLATION (Docker Compose networks):

  public-network:    nginx ↔ internet
  api-network:       nginx ↔ notarist-api
  internal-network:  notarist-api ↔ sidecars, postgres, qdrant, minio, ollama

  Sidecars (ocr, ner, reranker, ollama):
    TIDAK accessible dari public-network
    HANYA accessible dari api-network
    Tidak ada authentication antara api dan sidecar
    (auth via network isolation — internal trusted network)

HTTPS SETUP:
  Nginx terminates TLS (SSL certificate)
  Internal services: HTTP (within Docker network)
  MinIO public access via Nginx proxy (signed URL tidak perlu backend proxy)

CORS CONFIG:
  Allowed origins: [mobile app origin, admin web origin]
  Allowed methods: GET, POST, PUT, DELETE
  Allowed headers: Authorization, Content-Type, X-Request-Id
  No wildcard (*) in production
```

---

## RECOMMENDATION

### REC-01 — Mulai Implementasi dari Backend Core + Infrastructure

```
RECOMMENDED IMPLEMENTATION ORDER:

WEEK 1-2: Infrastructure Foundation
  ○ docker-compose.local.yml (Postgres, Qdrant, MinIO)
  ○ Flyway migrations (PostgreSQL rag schema)
  ○ Liquibase changesets (Oracle schemas)
  ○ MinIO bucket creation scripts
  ○ Qdrant collection creation

WEEK 3-4: Backend Core
  ○ notarist-core module (domain, enums, exceptions)
  ○ notarist-auth module (JWT, RBAC)
  ○ Spring Boot app skeleton (notarist-web)
  ○ Basic document CRUD (notarist-document)

WEEK 5-6: Ingestion Pipeline
  ○ Document upload + MinIO integration
  ○ OCR sidecar (PaddleOCR FastAPI)
  ○ OCR integration + processing log
  ○ Rule-based NER
  ○ Pipeline queue (SKIP LOCKED)

WEEK 7-8: RAG Core
  ○ IndoBERT NER sidecar
  ○ Chunking service
  ○ Embedding pipeline + Qdrant integration
  ○ notarist-search (hybrid search + RRF)
  ○ Reranker sidecar

WEEK 9-10: AI Assistant
  ○ Ollama integration
  ○ Context assembly
  ○ SSE streaming
  ○ Citation extraction
  ○ notarist-regulation module

WEEK 11-12: Mobile MVP
  ○ React Native project setup
  ○ Auth + navigation
  ○ Document list + upload
  ○ Search screen
  ○ AI assistant screen (streaming)

WEEK 13+: Polish & Production
  ○ OCR review screen
  ○ Regulation explorer (lazy loading accordion)
  ○ Observability stack (Prometheus + Grafana + Loki)
  ○ Backup scripts
  ○ CI/CD pipeline
  ○ Performance testing
```

### REC-02 — Ambiguity: Oracle JDBC Driver Distribution
ojdbc11.jar tidak tersedia di Maven Central. Perlu satu dari:
1. **Oracle Maven repo** (butuh Oracle account) — paling bersih untuk CI/CD
2. **Private Nexus/Artifactory** — jika ada internal Maven repo
3. **Manual install ke local Maven repo** — hanya untuk developer lokal

Konfirmasi approach sebelum CI/CD di-setup.

### REC-03 — Model Weights Distribution
IndoBERT dan Reranker model weights (~1-4 GB per model) tidak bisa di dalam
Docker image (terlalu besar). Strategi:
- Store model weights di server (persistent volume)
- Docker container mount volume tersebut
- Dockerfile hanya berisi inference code, bukan weights
- CI/CD: model weights tidak di-push ke registry (hanya code)

### REC-04 — OCR GPU vs CPU
PaddleOCR bisa jalan di CPU (lambat) atau GPU (cepat).
- CPU: cocok untuk volume rendah (< 50 halaman/hari), latency ~5-10 detik/halaman
- GPU: diperlukan untuk volume tinggi, latency ~0.5-1 detik/halaman
Konfirmasi apakah server production memiliki GPU (NVIDIA) sebelum OCR service di-design untuk GPU.

### REC-05 — Ambiguity: CI/CD Tool
Belum ada konfirmasi tool CI/CD yang digunakan:
1. **GitLab CI** — jika menggunakan GitLab untuk source control
2. **GitHub Actions (self-hosted runner)** — jika menggunakan GitHub
3. **Jenkins** — jika existing Jenkins di organisasi
4. **Gitea + Forgejo Actions** — jika lightweight self-hosted preference

Pemilihan tool menentukan format pipeline file (`.gitlab-ci.yml`, `.github/workflows/*.yml`, `Jenkinsfile`).

---

## STATUS

```
STEP 1 — ANALYZE NEW DOMAIN         ✅ COMPLETE
STEP 2 — DDL DESIGN                 ✅ COMPLETE
STEP 3 — INGESTION & RETRIEVAL ARCH ✅ COMPLETE
STEP 4 — API & SERVICE ARCH         ✅ COMPLETE
STEP 5 — FRONTEND EXPERIENCE ARCH   ✅ COMPLETE
STEP 6 — MONOREPO & INFRA ARCH      ✅ COMPLETE (pending approval)
STEP 7 — BACKEND IMPLEMENTATION     ⏸ WAITING APPROVAL
STEP 8 — RAG PIPELINE IMPLEMENTATION⏸ WAITING APPROVAL
STEP 9 — FRONTEND IMPLEMENTATION    ⏸ WAITING APPROVAL
```

**3 Ambiguity untuk konfirmasi sebelum implementasi dimulai:**

1. **Oracle JDBC** → Oracle Maven repo? Private Nexus? Manual install?
2. **OCR Hardware** → CPU-only? atau GPU tersedia di production server?
3. **CI/CD Tool** → GitLab CI? GitHub Actions? Jenkins? Gitea?

---

## GENERATED DOCS SUMMARY

```
/generated/docs/
├── rag_architecture_analysis.md       23K  STEP 1 — Domain analysis
├── step2_ddl_design.md                19K  STEP 2 — DDL design (Oracle + PG + Qdrant)
├── step3_ingestion_retrieval_*.md     58K  STEP 3 — 16 pipeline flows
├── step4_api_service_architecture.md  50K  STEP 4 — API & service design
├── step5_frontend_experience_*.md     61K  STEP 5 — Screen & UX architecture
└── step6_monorepo_infra_*.md          57K  STEP 6 — Infra & deployment (this file)

/generated/sql/
├── oracle_transactional_schema.sql    28K  Oracle 19C DDL (15 tables)
└── postgres_rag_schema.sql            25K  PostgreSQL DDL (10 tables, 2 views)

TOTAL ARCHITECTURE DOCUMENTATION:  ~321K  |  ~9,000+ lines
```

---

*Generated by: NOTARIST RAG PLATFORM — ANALYSIS_FIRST mode*
*File: /generated/docs/step6_monorepo_infra_architecture.md*
*Date: 2026-05-23*
