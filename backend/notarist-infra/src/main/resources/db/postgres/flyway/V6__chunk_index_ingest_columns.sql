-- ============================================================
-- NOTARIST RAG Platform — PostgreSQL
-- Flyway V6 — chunk_index becomes the durable handoff between
--             CHUNK → EMBED → INDEX pipeline stages
-- ============================================================

-- ChunkWorker persists real chunk rows here (replacing in-memory-only
-- ChunkMetadata that previously died at process() return).
-- EmbeddingWorker reads rows WHERE embedding IS NULL, writes vectors back.
-- IndexingWorker reads rows WHERE embedding IS NOT NULL and upserts to Qdrant.
-- BM25 keyword search additionally gates on searchable = TRUE, mirroring
-- the is_searchable payload filter already enforced on the Qdrant side.

ALTER TABLE chunk_index
    ADD COLUMN IF NOT EXISTS token_count     INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS start_offset    INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS end_offset      INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS chunk_strategy  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS overlap_tokens  INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pasal_ref       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ocr_confidence  REAL         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS review_status   VARCHAR(30)  NOT NULL DEFAULT 'LOW_CONFIDENCE_REVIEW',
    ADD COLUMN IF NOT EXISTS searchable      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS embedding       REAL[],
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(100),
    ADD COLUMN IF NOT EXISTS embedded_at     TIMESTAMPTZ;

-- Stage handoff lookups: all pipeline reads are per-ingestion
CREATE INDEX IF NOT EXISTS idx_chunk_ingestion
    ON chunk_index (ingestion_id);

-- EmbeddingWorker idempotent re-run: only unembedded chunks per ingestion
CREATE INDEX IF NOT EXISTS idx_chunk_ingestion_unembedded
    ON chunk_index (ingestion_id)
    WHERE embedding IS NULL;

-- BM25 search gate: searchable chunks only
CREATE INDEX IF NOT EXISTS idx_chunk_searchable
    ON chunk_index (tenant_id)
    WHERE searchable = TRUE;
