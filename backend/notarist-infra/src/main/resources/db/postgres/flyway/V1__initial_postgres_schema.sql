-- ============================================================
-- NOTARIST RAG Platform — PostgreSQL Initial Schema
-- Flyway V1 — Phase 1: Auth + Document
-- ============================================================

-- -------------------------------------------------------
-- session_token: Auth session storage (refresh tokens)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS session_token (
    session_id         UUID         NOT NULL,
    user_id            UUID         NOT NULL,
    tenant_id          UUID         NOT NULL,
    refresh_token_hash VARCHAR(64)  NOT NULL,  -- SHA-256 hex, single-use
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMPTZ  NOT NULL,
    invalidated        BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_session_token PRIMARY KEY (session_id)
);

-- Lookup by refresh token hash (rotation on use)
CREATE UNIQUE INDEX idx_session_token_hash
    ON session_token (refresh_token_hash)
    WHERE invalidated = FALSE;

-- Cleanup queries by user
CREATE INDEX idx_session_token_user
    ON session_token (user_id);

-- Expired session cleanup
CREATE INDEX idx_session_token_expires
    ON session_token (expires_at)
    WHERE invalidated = FALSE;

-- -------------------------------------------------------
-- token_deny_list: Revoked JWT JTI tracking
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS token_deny_list (
    jti        VARCHAR(36)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_token_deny_list PRIMARY KEY (jti)
);

CREATE INDEX idx_token_deny_list_expires
    ON token_deny_list (expires_at);

-- -------------------------------------------------------
-- document_chunk: Chunked text for vector indexing
-- Populated by ingest pipeline (Phase 2)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS document_chunk (
    chunk_id        UUID         NOT NULL,
    document_id     UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    chunk_index     INTEGER      NOT NULL,
    chunk_text      TEXT         NOT NULL,  -- PII-redacted before storage
    token_count     INTEGER      NOT NULL,
    chunk_strategy  VARCHAR(50)  NOT NULL,  -- AKTA|REGULASI|SOP
    page_number     INTEGER,
    section_title   VARCHAR(500),
    pasal_ref       VARCHAR(100),
    qdrant_vector_id UUID,
    bm25_vector     TSVECTOR,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_document_chunk PRIMARY KEY (chunk_id),
    CONSTRAINT uq_document_chunk_idx UNIQUE (document_id, chunk_index)
);

-- GIN index for BM25 full-text search
CREATE INDEX idx_document_chunk_bm25
    ON document_chunk USING GIN (bm25_vector);

-- Lookup by document
CREATE INDEX idx_document_chunk_document
    ON document_chunk (document_id);

-- Tenant-scoped search
CREATE INDEX idx_document_chunk_tenant
    ON document_chunk (tenant_id);

-- -------------------------------------------------------
-- ingestion_queue: PostgreSQL SKIP LOCKED processing queue
-- Populated and consumed by ingest pipeline (Phase 2)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS ingestion_queue (
    job_id          UUID         NOT NULL,
    document_id     UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    pipeline_stage  VARCHAR(100) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    payload         JSONB        NOT NULL DEFAULT '{}',
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    max_attempts    INTEGER      NOT NULL DEFAULT 3,
    locked_by       VARCHAR(100),
    locked_at       TIMESTAMPTZ,
    scheduled_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    error_detail    TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ingestion_queue PRIMARY KEY (job_id)
);

-- SKIP LOCKED dequeue pattern: pending jobs ordered by scheduled_at
CREATE INDEX idx_ingestion_queue_dequeue
    ON ingestion_queue (pipeline_stage, scheduled_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_ingestion_queue_document
    ON ingestion_queue (document_id);

CREATE INDEX idx_ingestion_queue_tenant_status
    ON ingestion_queue (tenant_id, status);

-- GIN index for JSONB payload queries
CREATE INDEX idx_ingestion_queue_payload
    ON ingestion_queue USING GIN (payload);
