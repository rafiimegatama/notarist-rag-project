-- Phase 3: Search module PostgreSQL schema
-- chunk_index: populated by ingest pipeline, queried by BM25 keyword retriever

CREATE TABLE IF NOT EXISTS chunk_index (
    chunk_id            VARCHAR(36)   PRIMARY KEY,
    ingestion_id        VARCHAR(36)   NOT NULL,
    document_id         VARCHAR(36)   NOT NULL,
    tenant_id           UUID          NOT NULL,
    document_type       VARCHAR(50)   NOT NULL,
    classification_level VARCHAR(50)  NOT NULL,
    chunk_index         INT           NOT NULL,
    section_title       TEXT,
    page_number         INT,
    chunk_text          TEXT          NOT NULL,
    search_vector       TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', chunk_text)) STORED,
    source_object_key   TEXT          NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_chunk_document_type    CHECK (document_type IN ('AKTA', 'REGULASI', 'SOP')),
    CONSTRAINT chk_chunk_classification   CHECK (classification_level IN (
        'PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'STRICTLY_CONFIDENTIAL'))
);

CREATE INDEX IF NOT EXISTS idx_chunk_tenant          ON chunk_index (tenant_id);
CREATE INDEX IF NOT EXISTS idx_chunk_document        ON chunk_index (document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_type            ON chunk_index (document_type);
CREATE INDEX IF NOT EXISTS idx_chunk_tenant_type     ON chunk_index (tenant_id, document_type);
CREATE INDEX IF NOT EXISTS idx_chunk_classification  ON chunk_index (classification_level);
CREATE INDEX IF NOT EXISTS idx_chunk_search_vector   ON chunk_index USING GIN (search_vector);

-- search_query_log: audit trail for every search invocation
CREATE TABLE IF NOT EXISTS search_query_log (
    query_id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID          NOT NULL,
    user_id               UUID          NOT NULL,
    raw_query             TEXT          NOT NULL,
    normalized_query      TEXT          NOT NULL,
    intent                VARCHAR(50)   NOT NULL,
    grounding_level       VARCHAR(20)   NOT NULL,
    retrieved_chunk_count INT           NOT NULL DEFAULT 0,
    processing_time_ms    BIGINT        NOT NULL DEFAULT 0,
    correlation_id        VARCHAR(100),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_search_log_tenant ON search_query_log (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_search_log_intent ON search_query_log (intent, created_at DESC);
