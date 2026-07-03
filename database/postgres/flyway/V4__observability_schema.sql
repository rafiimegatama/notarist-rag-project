-- ============================================================
-- NOTARIST RAG Platform — PostgreSQL Observability Schema
-- Flyway V4 — Phase 5: Observability + Ops tooling
-- ============================================================

-- -------------------------------------------------------
-- dead_letter_queue: add ops-replay and resolve tracking columns.
-- DlqReplayService reads/updates these; DeadLetterRepositoryImpl writes dlq_id.
-- -------------------------------------------------------
ALTER TABLE dead_letter_queue
    ADD COLUMN IF NOT EXISTS document_id      UUID,
    ADD COLUMN IF NOT EXISTS resolved_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolved_by      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS resolve_trace_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS resolve_reason   VARCHAR(100);

-- Efficient lookup for unresolved DLQ items (ops dashboard)
CREATE INDEX IF NOT EXISTS idx_dlq_unresolved
    ON dead_letter_queue (tenant_id, created_at DESC)
    WHERE resolved_at IS NULL;

-- -------------------------------------------------------
-- chunk_index: add embedding version tracking for reindex support.
-- ReindexTriggerService updates these to mark chunks needing re-embedding.
-- -------------------------------------------------------
ALTER TABLE chunk_index
    ADD COLUMN IF NOT EXISTS embedding_version VARCHAR(50),
    ADD COLUMN IF NOT EXISTS reindex_job_id    UUID;

CREATE INDEX IF NOT EXISTS idx_chunk_embedding_version
    ON chunk_index (embedding_version)
    WHERE embedding_version IS NOT NULL;

-- -------------------------------------------------------
-- pipeline_run: coordinator table for reindex and DLQ replay operations.
-- Written by ReindexTriggerService; read/updated by DlqReplayService.
-- run_type: REINDEX | REINDEX_COORDINATOR | REINDEX_GLOBAL
-- status:   PENDING | PROCESSING | COMPLETED | FAILED
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS pipeline_run (
    run_id              UUID          NOT NULL,
    document_id         UUID,
    tenant_id           UUID,
    run_type            VARCHAR(50)   NOT NULL,
    status              VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    triggered_by        VARCHAR(100),
    trigger_reason      TEXT,
    affected_chunk_count INTEGER,
    failure_stage       VARCHAR(50),
    failure_reason      TEXT,
    retry_count         INTEGER       NOT NULL DEFAULT 0,
    replayed_by         VARCHAR(100),
    replayed_at         TIMESTAMPTZ,
    replay_trace_id     VARCHAR(100),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_pipeline_run PRIMARY KEY (run_id)
);

CREATE INDEX IF NOT EXISTS idx_pipeline_run_document
    ON pipeline_run (document_id)
    WHERE document_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pipeline_run_tenant_status
    ON pipeline_run (tenant_id, status)
    WHERE tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pipeline_run_status
    ON pipeline_run (status, created_at DESC);
