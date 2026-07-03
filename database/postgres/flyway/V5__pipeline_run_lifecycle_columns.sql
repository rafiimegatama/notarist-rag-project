-- ============================================================
-- NOTARIST RAG Platform — PostgreSQL
-- Flyway V5 — pipeline_run lifecycle timestamps
-- ============================================================

-- pipeline_run was created in V4 without started_at / completed_at.
-- QueueReplayService and SnapshotReadinessChecker both need these to
-- detect stuck pipelines and measure snapshot freshness.
-- Status values in use: PENDING | IN_PROGRESS | COMPLETED | FAILED

ALTER TABLE pipeline_run
    ADD COLUMN IF NOT EXISTS started_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

-- Stuck-pipeline detection: IN_PROGRESS entries older than N minutes
CREATE INDEX IF NOT EXISTS idx_pipeline_run_in_progress
    ON pipeline_run (started_at)
    WHERE status = 'IN_PROGRESS';

-- Freshness check: most recent completed run
CREATE INDEX IF NOT EXISTS idx_pipeline_run_completed
    ON pipeline_run (completed_at DESC)
    WHERE status = 'COMPLETED';
