-- ============================================================
-- NOTARIST RAG Platform — PostgreSQL Ingest Schema
-- Flyway V2 — Phase 2: Ingestion Pipeline
-- ============================================================

-- -------------------------------------------------------
-- Rebuild ingestion_queue with proper Phase 2 schema.
-- V1 created a placeholder; V2 replaces it with the
-- queue_job_id-keyed schema required by IngestQueueRepository.
-- -------------------------------------------------------
DROP TABLE IF EXISTS ingestion_queue;

CREATE TABLE ingestion_queue (
    queue_job_id    UUID         NOT NULL,          -- PK: unique queue entry id
    ingestion_id    UUID         NOT NULL,          -- FK to ingestion job aggregate
    job_id          UUID         NOT NULL,          -- FK to ingestion job (jobId)
    tenant_id       UUID         NOT NULL,
    target_stage    VARCHAR(50)  NOT NULL,          -- PipelineStatus enum value
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',  -- PENDING|PROCESSING|COMPLETED|DLQ
    payload         JSONB        NOT NULL DEFAULT '{}',
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    locked_by       VARCHAR(100),                  -- worker id that locked this row
    locked_at       TIMESTAMPTZ,
    scheduled_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    next_retry_at   TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_detail    TEXT,
    dlq_reason      VARCHAR(1000),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ingestion_queue PRIMARY KEY (queue_job_id)
);

-- SKIP LOCKED dequeue index: pending entries ordered by scheduled_at per stage
CREATE INDEX idx_ingestion_queue_dequeue
    ON ingestion_queue (target_stage, scheduled_at)
    WHERE status = 'PENDING';

-- Retry scheduling lookup
CREATE INDEX idx_ingestion_queue_retry
    ON ingestion_queue (next_retry_at)
    WHERE status = 'PENDING' AND next_retry_at IS NOT NULL;

-- Lineage lookup
CREATE INDEX idx_ingestion_queue_ingestion_id
    ON ingestion_queue (ingestion_id);

-- Tenant scope
CREATE INDEX idx_ingestion_queue_tenant_status
    ON ingestion_queue (tenant_id, status);

-- JSONB payload queries
CREATE INDEX idx_ingestion_queue_payload
    ON ingestion_queue USING GIN (payload);

-- -------------------------------------------------------
-- dead_letter_queue: Append-only DLQ for failed ingestion jobs.
-- Written by DeadLetterRepositoryImpl — no updates, only INSERTs.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS dead_letter_queue (
    dlq_id              UUID         NOT NULL,
    ingestion_id        UUID         NOT NULL,
    job_id              UUID         NOT NULL,
    tenant_id           UUID         NOT NULL,
    failure_stage       VARCHAR(50)  NOT NULL,     -- PipelineStatus at point of failure
    retry_count         INTEGER      NOT NULL DEFAULT 0,
    last_error_code     VARCHAR(100),
    last_error_hash     VARCHAR(64),
    next_retry_at       TIMESTAMPTZ,
    dead_letter_reason  TEXT         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dead_letter_queue PRIMARY KEY (dlq_id)
);

-- Tenant-scoped DLQ queries (admin/ops dashboards)
CREATE INDEX idx_dlq_tenant
    ON dead_letter_queue (tenant_id, created_at DESC);

-- Job lineage lookup
CREATE INDEX idx_dlq_ingestion_id
    ON dead_letter_queue (ingestion_id);

-- -------------------------------------------------------
-- document_chunk: Ensure ingestion_id column for lineage.
-- (table created in V1; add ingestion_id for Phase 2 traceability)
-- -------------------------------------------------------
ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS ingestion_id UUID;

CREATE INDEX IF NOT EXISTS idx_document_chunk_ingestion
    ON document_chunk (ingestion_id);
