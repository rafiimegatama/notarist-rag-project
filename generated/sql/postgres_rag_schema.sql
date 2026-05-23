-- ============================================================
-- POSTGRESQL RAG SCHEMA
-- NOTARIST RAG PLATFORM
-- Layer: Retrieval / Semantic / AI Interaction
-- Database: notarist_rag
-- Schema: rag
-- Version: v1.0 | Date: 2026-05-23
-- ============================================================
-- Notes:
--   - doc_id (VARCHAR 36) = FK reference ke Oracle DOC_MASTER.DOC_ID
--     (tidak di-enforce di DB level karena cross-database)
--   - user_id (VARCHAR 36) = FK reference ke Oracle USER_MASTER.USER_ID
--   - TIMESTAMPTZ selalu digunakan (timezone-aware)
--   - JSONB digunakan untuk payload fleksibel
-- ============================================================


-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";    -- UUID generation
CREATE EXTENSION IF NOT EXISTS "pg_trgm";      -- Fuzzy text matching

-- Schema
CREATE SCHEMA IF NOT EXISTS rag;

-- ============================================================
-- SECTION 1: OCR LAYER
-- ============================================================

CREATE TABLE rag.ocr_result (
    ocr_id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    doc_id                  VARCHAR(36)     NOT NULL,
    page_number             INTEGER         NOT NULL,
    raw_text                TEXT,
    cleaned_text            TEXT,
    confidence_score        FLOAT           CHECK (confidence_score BETWEEN 0.0 AND 1.0),
    ocr_engine              VARCHAR(50),
    ocr_engine_version      VARCHAR(20),
    processing_time_ms      INTEGER,
    has_table               BOOLEAN         DEFAULT FALSE,
    has_image               BOOLEAN         DEFAULT FALSE,
    has_handwriting         BOOLEAN         DEFAULT FALSE,
    language_detected       VARCHAR(10)     DEFAULT 'id',
    page_width_px           INTEGER,
    page_height_px          INTEGER,
    word_count              INTEGER,
    char_count              INTEGER,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ocr_doc_page UNIQUE (doc_id, page_number)
);

COMMENT ON TABLE  rag.ocr_result                    IS 'Output OCR per halaman dokumen';
COMMENT ON COLUMN rag.ocr_result.doc_id             IS 'Referensi ke NOTARIST.DOC_MASTER.DOC_ID di Oracle';
COMMENT ON COLUMN rag.ocr_result.raw_text           IS 'Teks mentah langsung dari OCR engine';
COMMENT ON COLUMN rag.ocr_result.cleaned_text       IS 'Teks setelah post-processing dan normalisasi';
COMMENT ON COLUMN rag.ocr_result.confidence_score   IS 'Rata-rata confidence score per halaman (0.0 - 1.0)';

CREATE INDEX idx_ocr_doc_id         ON rag.ocr_result (doc_id);
CREATE INDEX idx_ocr_confidence     ON rag.ocr_result (confidence_score);


-- ============================================================
-- SECTION 2: NER / ENTITY EXTRACTION
-- ============================================================

CREATE TABLE rag.legal_entity_extract (
    extract_id          UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    ocr_id              UUID            NOT NULL REFERENCES rag.ocr_result (ocr_id) ON DELETE CASCADE,
    doc_id              VARCHAR(36)     NOT NULL,
    entity_type         VARCHAR(50)     NOT NULL,
    entity_value        TEXT            NOT NULL,
    entity_value_norm   TEXT,
    confidence_score    FLOAT           CHECK (confidence_score BETWEEN 0.0 AND 1.0),
    page_number         INTEGER         NOT NULL,
    char_start          INTEGER,
    char_end            INTEGER,
    bounding_box        JSONB,
    is_verified         BOOLEAN         DEFAULT FALSE,
    verified_by         VARCHAR(36),
    verified_at         TIMESTAMPTZ,
    mapped_to_oracle    BOOLEAN         DEFAULT FALSE,
    oracle_record_id    VARCHAR(36),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_entity_type CHECK (entity_type IN (
        'NOMOR_AKTA', 'NAMA_PIHAK', 'TANGGAL_AKTA',
        'NOMOR_SERTIFIKAT', 'NIK', 'NPWP',
        'NILAI_TRANSAKSI', 'NAMA_NOTARIS', 'NAMA_PPAT',
        'LOKASI', 'NOMOR_SK', 'JENIS_HAK', 'LAIN'
    ))
);

COMMENT ON TABLE  rag.legal_entity_extract                      IS 'Entitas hukum hasil NER dari teks OCR';
COMMENT ON COLUMN rag.legal_entity_extract.entity_value_norm    IS 'Nilai yang dinormalisasi (tanggal → ISO 8601, dsb)';
COMMENT ON COLUMN rag.legal_entity_extract.bounding_box         IS '{"x": int, "y": int, "width": int, "height": int}';
COMMENT ON COLUMN rag.legal_entity_extract.mapped_to_oracle     IS 'TRUE jika sudah di-map ke record Oracle (AKTA_MASTER, dll)';
COMMENT ON COLUMN rag.legal_entity_extract.oracle_record_id     IS 'ID record Oracle yang bersesuaian jika mapped_to_oracle = TRUE';

CREATE INDEX idx_ner_doc_id         ON rag.legal_entity_extract (doc_id);
CREATE INDEX idx_ner_entity_type    ON rag.legal_entity_extract (entity_type);
CREATE INDEX idx_ner_verified       ON rag.legal_entity_extract (is_verified);
CREATE INDEX idx_ner_value_trgm     ON rag.legal_entity_extract USING GIN (entity_value gin_trgm_ops);


-- ============================================================
-- SECTION 3: CHUNKING
-- ============================================================

CREATE TABLE rag.doc_chunk (
    chunk_id            UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    doc_id              VARCHAR(36)     NOT NULL,
    chunk_index         INTEGER         NOT NULL,
    page_number_start   INTEGER,
    page_number_end     INTEGER,
    chunk_text          TEXT            NOT NULL,
    chunk_text_tsv      TSVECTOR        GENERATED ALWAYS AS (
                            to_tsvector('simple', COALESCE(chunk_text, ''))
                        ) STORED,
    char_start          INTEGER,
    char_end            INTEGER,
    chunk_type          VARCHAR(30)     NOT NULL DEFAULT 'PARAGRAPH',
    token_count         INTEGER,
    has_overlap_prev    BOOLEAN         DEFAULT FALSE,
    has_overlap_next    BOOLEAN         DEFAULT FALSE,
    is_indexed          BOOLEAN         DEFAULT FALSE,
    embedding_status    VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uq_chunk_doc_index   UNIQUE (doc_id, chunk_index),
    CONSTRAINT chk_chunk_type       CHECK (chunk_type IN (
        'PARAGRAPH', 'CLAUSE', 'ARTICLE', 'HEADER',
        'TABLE', 'FOOTER', 'SIGNATURE_BLOCK', 'LAIN'
    )),
    CONSTRAINT chk_embedding_status CHECK (embedding_status IN (
        'PENDING', 'INDEXED', 'FAILED', 'OUTDATED'
    ))
);

COMMENT ON TABLE  rag.doc_chunk                         IS 'Potongan teks untuk RAG indexing';
COMMENT ON COLUMN rag.doc_chunk.chunk_index             IS 'Urutan chunk dalam dokumen (0-based)';
COMMENT ON COLUMN rag.doc_chunk.chunk_text_tsv          IS 'GENERATED column untuk full-text search PostgreSQL';
COMMENT ON COLUMN rag.doc_chunk.has_overlap_prev        IS 'TRUE jika chunk ini overlap dengan chunk sebelumnya';
COMMENT ON COLUMN rag.doc_chunk.chunk_type              IS 'Tipe struktural: PARAGRAPH|CLAUSE|ARTICLE|HEADER|TABLE|dll';

CREATE INDEX idx_chunk_doc_id       ON rag.doc_chunk (doc_id);
CREATE INDEX idx_chunk_tsv          ON rag.doc_chunk USING GIN (chunk_text_tsv);
CREATE INDEX idx_chunk_status       ON rag.doc_chunk (embedding_status);
CREATE INDEX idx_chunk_indexed      ON rag.doc_chunk (is_indexed);
CREATE INDEX idx_chunk_text_trgm    ON rag.doc_chunk USING GIN (chunk_text gin_trgm_ops);


-- ============================================================
-- SECTION 4: EMBEDDING METADATA
-- ============================================================

CREATE TABLE rag.embedding_metadata (
    embedding_id        UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    chunk_id            UUID            NOT NULL REFERENCES rag.doc_chunk (chunk_id) ON DELETE CASCADE,
    doc_id              VARCHAR(36)     NOT NULL,
    qdrant_point_id     VARCHAR(36)     NOT NULL,
    qdrant_collection   VARCHAR(100)    NOT NULL DEFAULT 'notarist_legal_docs',
    model_name          VARCHAR(100)    NOT NULL DEFAULT 'bge-m3',
    model_version       VARCHAR(50),
    vector_dimension    INTEGER         NOT NULL DEFAULT 1024,
    indexing_status     VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    indexed_at          TIMESTAMPTZ,
    index_error         TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uq_qdrant_point_id   UNIQUE (qdrant_point_id),
    CONSTRAINT uq_embedding_chunk   UNIQUE (chunk_id),
    CONSTRAINT chk_indexing_status  CHECK (indexing_status IN (
        'PENDING', 'INDEXED', 'FAILED', 'DELETED'
    ))
);

COMMENT ON TABLE  rag.embedding_metadata                        IS 'Metadata rekaman embedding di Qdrant, satu baris per chunk';
COMMENT ON COLUMN rag.embedding_metadata.qdrant_point_id        IS 'ID point di Qdrant collection, UUID format';
COMMENT ON COLUMN rag.embedding_metadata.qdrant_collection      IS 'Nama collection Qdrant, default: notarist_legal_docs';
COMMENT ON COLUMN rag.embedding_metadata.vector_dimension       IS 'Dimensi vektor, default 1024 (bge-m3)';

CREATE INDEX idx_emb_doc_id         ON rag.embedding_metadata (doc_id);
CREATE INDEX idx_emb_qdrant_id      ON rag.embedding_metadata (qdrant_point_id);
CREATE INDEX idx_emb_status         ON rag.embedding_metadata (indexing_status);


-- ============================================================
-- SECTION 5: SEMANTIC METADATA (per-document summary)
-- ============================================================

CREATE TABLE rag.semantic_metadata (
    meta_id             UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    doc_id              VARCHAR(36)     NOT NULL,
    jenis_dokumen       VARCHAR(30),
    jenis_akta          VARCHAR(30),
    klasifikasi         VARCHAR(30),
    extracted_entities  JSONB,
    auto_tags           JSONB,
    legal_categories    JSONB,
    summary_text        TEXT,
    key_clauses         JSONB,
    ocr_quality_avg     FLOAT           CHECK (ocr_quality_avg BETWEEN 0.0 AND 1.0),
    embedding_quality   FLOAT           CHECK (embedding_quality BETWEEN 0.0 AND 1.0),
    total_chunks        INTEGER,
    total_pages         INTEGER,
    is_searchable       BOOLEAN         DEFAULT FALSE,
    last_updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_semantic_doc_id UNIQUE (doc_id)
);

COMMENT ON TABLE  rag.semantic_metadata                     IS 'Ringkasan semantik per dokumen, diperbarui setiap pipeline selesai';
COMMENT ON COLUMN rag.semantic_metadata.extracted_entities  IS 'Ringkasan NER: {"nomor_akta": "...", "pihak": [...]}';
COMMENT ON COLUMN rag.semantic_metadata.auto_tags           IS 'Tags dari AI: [{"tag": "APHT", "score": 0.95}]';
COMMENT ON COLUMN rag.semantic_metadata.key_clauses         IS 'Klausul penting: [{"judul": "...", "teks": "..."}]';
COMMENT ON COLUMN rag.semantic_metadata.is_searchable       IS 'TRUE jika embedding sudah selesai dan dokumen bisa dicari';

CREATE INDEX idx_semeta_doc_id          ON rag.semantic_metadata (doc_id);
CREATE INDEX idx_semeta_jenis_dokumen   ON rag.semantic_metadata (jenis_dokumen);
CREATE INDEX idx_semeta_searchable      ON rag.semantic_metadata (is_searchable);


-- ============================================================
-- SECTION 6: PIPELINE PROCESSING LOG
-- ============================================================

CREATE TABLE rag.doc_processing_log (
    log_id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    doc_id              VARCHAR(36)     NOT NULL,
    pipeline_stage      VARCHAR(30)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    progress_pct        INTEGER         DEFAULT 0 CHECK (progress_pct BETWEEN 0 AND 100),
    error_message       TEXT,
    error_detail        JSONB,
    processing_time_ms  INTEGER,
    worker_id           VARCHAR(100),
    retry_count         INTEGER         DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT chk_pipeline_stage   CHECK (pipeline_stage IN (
        'UPLOAD', 'OCR', 'NER', 'CHUNKING', 'EMBEDDING', 'INDEXING', 'COMPLETE'
    )),
    CONSTRAINT chk_proc_status      CHECK (status IN (
        'STARTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'RETRYING', 'SKIPPED'
    ))
);

COMMENT ON TABLE  rag.doc_processing_log                IS 'Log setiap tahap pipeline per dokumen';
COMMENT ON COLUMN rag.doc_processing_log.pipeline_stage IS 'UPLOAD|OCR|NER|CHUNKING|EMBEDDING|INDEXING|COMPLETE';
COMMENT ON COLUMN rag.doc_processing_log.error_detail   IS 'Stack trace atau detail error dalam JSONB';
COMMENT ON COLUMN rag.doc_processing_log.retry_count    IS 'Berapa kali tahap ini sudah di-retry';

CREATE INDEX idx_proclog_doc_id         ON rag.doc_processing_log (doc_id);
CREATE INDEX idx_proclog_stage_status   ON rag.doc_processing_log (pipeline_stage, status);
CREATE INDEX idx_proclog_created        ON rag.doc_processing_log (created_at);


-- ============================================================
-- SECTION 7: SEARCH & AI INTERACTION
-- ============================================================

CREATE TABLE rag.search_session (
    session_id          UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             VARCHAR(36)     NOT NULL,
    session_start       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    session_end         TIMESTAMPTZ,
    total_queries       INTEGER         DEFAULT 0,
    ip_address          VARCHAR(50),
    device_info         JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  rag.search_session                IS 'Sesi pencarian satu pengguna';
COMMENT ON COLUMN rag.search_session.device_info    IS '{"platform": "mobile", "os": "android", "app_version": "1.0"}';

CREATE INDEX idx_session_user_id    ON rag.search_session (user_id);
CREATE INDEX idx_session_start      ON rag.search_session (session_start);


CREATE TABLE rag.ai_query (
    query_id            UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id          UUID            REFERENCES rag.search_session (session_id),
    user_id             VARCHAR(36)     NOT NULL,
    query_text          TEXT            NOT NULL,
    query_text_norm     TEXT,
    intent_type         VARCHAR(50),
    filter_applied      JSONB,
    top_k_requested     INTEGER         DEFAULT 5,
    search_mode         VARCHAR(20)     NOT NULL DEFAULT 'HYBRID',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_intent_type  CHECK (intent_type IN (
        'SEARCH_DOCUMENT', 'ASK_LEGAL', 'EXPLAIN_TERM',
        'RELATED_DOCS', 'SUMMARIZE', 'UNKNOWN'
    )),
    CONSTRAINT chk_search_mode  CHECK (search_mode IN ('SEMANTIC', 'KEYWORD', 'HYBRID'))
);

COMMENT ON TABLE  rag.ai_query                      IS 'Setiap query yang dikirim ke RAG pipeline';
COMMENT ON COLUMN rag.ai_query.query_text_norm      IS 'Query setelah normalisasi: lowercase, expand abbr legal';
COMMENT ON COLUMN rag.ai_query.filter_applied       IS '{"jenis_dokumen": "AKTA", "jenis_akta": "APHT"}';
COMMENT ON COLUMN rag.ai_query.search_mode          IS 'SEMANTIC|KEYWORD|HYBRID';

CREATE INDEX idx_query_session_id   ON rag.ai_query (session_id);
CREATE INDEX idx_query_user_id      ON rag.ai_query (user_id);
CREATE INDEX idx_query_intent       ON rag.ai_query (intent_type);
CREATE INDEX idx_query_created      ON rag.ai_query (created_at);


CREATE TABLE rag.retrieval_result (
    result_id           UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    query_id            UUID            NOT NULL REFERENCES rag.ai_query (query_id) ON DELETE CASCADE,
    chunk_id            UUID            NOT NULL REFERENCES rag.doc_chunk (chunk_id),
    doc_id              VARCHAR(36)     NOT NULL,
    rank_position       INTEGER         NOT NULL,
    semantic_score      FLOAT,
    keyword_score       FLOAT,
    rerank_score        FLOAT,
    final_score         FLOAT,
    was_used_in_context BOOLEAN         DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  rag.retrieval_result                          IS 'Hasil retrieval per query: chunk apa yang ditemukan dan scorenya';
COMMENT ON COLUMN rag.retrieval_result.semantic_score           IS 'Cosine similarity dari Qdrant (0.0 - 1.0)';
COMMENT ON COLUMN rag.retrieval_result.keyword_score            IS 'BM25 score dari PostgreSQL full-text search';
COMMENT ON COLUMN rag.retrieval_result.rerank_score             IS 'Score dari cross-encoder reranker';
COMMENT ON COLUMN rag.retrieval_result.final_score              IS 'RRF fusion score — score akhir untuk ranking';
COMMENT ON COLUMN rag.retrieval_result.was_used_in_context      IS 'TRUE jika chunk ini masuk ke prompt LLM';

CREATE INDEX idx_retrieval_query_id     ON rag.retrieval_result (query_id);
CREATE INDEX idx_retrieval_chunk_id     ON rag.retrieval_result (chunk_id);


CREATE TABLE rag.ai_response (
    response_id         UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    query_id            UUID            NOT NULL REFERENCES rag.ai_query (query_id),
    response_text       TEXT            NOT NULL,
    model_name          VARCHAR(100),
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    total_tokens        INTEGER GENERATED ALWAYS AS (
                            COALESCE(prompt_tokens, 0) + COALESCE(completion_tokens, 0)
                        ) STORED,
    latency_ms          INTEGER,
    confidence_level    VARCHAR(20),
    is_truncated        BOOLEAN         DEFAULT FALSE,
    has_hallucination_flag BOOLEAN      DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_confidence   CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'))
);

COMMENT ON TABLE  rag.ai_response                               IS 'Respons yang dihasilkan LLM beserta metadata generation';
COMMENT ON COLUMN rag.ai_response.total_tokens                  IS 'GENERATED: prompt_tokens + completion_tokens';
COMMENT ON COLUMN rag.ai_response.has_hallucination_flag        IS 'Flag manual jika staf menandai respons tidak akurat';

CREATE INDEX idx_response_query_id  ON rag.ai_response (query_id);
CREATE INDEX idx_response_created   ON rag.ai_response (created_at);


CREATE TABLE rag.citation (
    citation_id         UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    response_id         UUID            NOT NULL REFERENCES rag.ai_response (response_id) ON DELETE CASCADE,
    chunk_id            UUID            NOT NULL REFERENCES rag.doc_chunk (chunk_id),
    doc_id              VARCHAR(36)     NOT NULL,
    citation_text       TEXT,
    page_number         INTEGER,
    relevance_score     FLOAT,
    citation_order      INTEGER         NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  rag.citation                      IS 'Referensi chunk yang digunakan dalam satu respons AI';
COMMENT ON COLUMN rag.citation.citation_text        IS 'Snippet teks yang dikutip dari chunk (untuk tampilan)';
COMMENT ON COLUMN rag.citation.citation_order       IS 'Urutan tampil citation dalam respons (1-based)';

CREATE INDEX idx_citation_response_id   ON rag.citation (response_id);
CREATE INDEX idx_citation_chunk_id      ON rag.citation (chunk_id);


-- ============================================================
-- SECTION 8: SEARCH CACHE
-- ============================================================

CREATE TABLE rag.search_cache (
    cache_id            UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    query_hash          VARCHAR(64)     NOT NULL,
    query_text          TEXT            NOT NULL,
    filter_hash         VARCHAR(64),
    result_payload      JSONB           NOT NULL,
    hit_count           INTEGER         DEFAULT 0,
    last_hit_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ     NOT NULL,
    CONSTRAINT uq_cache_hash UNIQUE (query_hash, filter_hash)
);

COMMENT ON TABLE  rag.search_cache                  IS 'Cache hasil pencarian untuk query yang identik';
COMMENT ON COLUMN rag.search_cache.query_hash       IS 'SHA-256 dari normalized query text';
COMMENT ON COLUMN rag.search_cache.filter_hash      IS 'SHA-256 dari filter_applied payload';
COMMENT ON COLUMN rag.search_cache.result_payload   IS 'Serialized search results: [{chunk_id, score, snippet}]';

CREATE INDEX idx_cache_hash         ON rag.search_cache (query_hash, filter_hash);
CREATE INDEX idx_cache_expires      ON rag.search_cache (expires_at);


-- ============================================================
-- SECTION 9: AI INTERACTION AUDIT (PostgreSQL side)
-- ============================================================

CREATE TABLE rag.ai_interaction_audit (
    audit_id            UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             VARCHAR(36)     NOT NULL,
    session_id          UUID            REFERENCES rag.search_session (session_id),
    query_id            UUID            REFERENCES rag.ai_query (query_id),
    response_id         UUID            REFERENCES rag.ai_response (response_id),
    action_type         VARCHAR(50)     NOT NULL,
    doc_ids_accessed    JSONB,
    klasifikasi_accessed JSONB,
    ip_address          VARCHAR(50),
    user_agent          TEXT,
    risk_level          VARCHAR(20)     NOT NULL DEFAULT 'LOW',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_risk_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

COMMENT ON TABLE  rag.ai_interaction_audit                      IS 'Audit trail interaksi AI/RAG, terpisah dari Oracle AUDIT_TRAIL';
COMMENT ON COLUMN rag.ai_interaction_audit.doc_ids_accessed     IS 'Array doc_id yang terekspos ke user dalam sesi ini';
COMMENT ON COLUMN rag.ai_interaction_audit.klasifikasi_accessed IS 'Level klasifikasi tertinggi dokumen yang diakses';
COMMENT ON COLUMN rag.ai_interaction_audit.risk_level           IS 'LOW|MEDIUM|HIGH|CRITICAL — dinilai berdasarkan klasifikasi';

CREATE INDEX idx_ai_audit_user_id   ON rag.ai_interaction_audit (user_id);
CREATE INDEX idx_ai_audit_created   ON rag.ai_interaction_audit (created_at);
CREATE INDEX idx_ai_audit_risk      ON rag.ai_interaction_audit (risk_level);


-- ============================================================
-- SECTION 10: VIEWS
-- ============================================================

-- View: status indexing per dokumen
CREATE VIEW rag.v_doc_indexing_status AS
SELECT
    pl.doc_id,
    MAX(CASE WHEN pl.pipeline_stage = 'OCR'       THEN pl.status END) AS ocr_status,
    MAX(CASE WHEN pl.pipeline_stage = 'NER'       THEN pl.status END) AS ner_status,
    MAX(CASE WHEN pl.pipeline_stage = 'CHUNKING'  THEN pl.status END) AS chunking_status,
    MAX(CASE WHEN pl.pipeline_stage = 'EMBEDDING' THEN pl.status END) AS embedding_status,
    MAX(CASE WHEN pl.pipeline_stage = 'INDEXING'  THEN pl.status END) AS indexing_status,
    COUNT(DISTINCT dc.chunk_id)                                        AS total_chunks,
    COUNT(DISTINCT CASE WHEN dc.is_indexed THEN dc.chunk_id END)      AS indexed_chunks,
    sm.is_searchable,
    sm.ocr_quality_avg,
    sm.last_updated_at
FROM rag.doc_processing_log pl
LEFT JOIN rag.doc_chunk dc          ON dc.doc_id = pl.doc_id
LEFT JOIN rag.semantic_metadata sm  ON sm.doc_id = pl.doc_id
GROUP BY pl.doc_id, sm.is_searchable, sm.ocr_quality_avg, sm.last_updated_at;

COMMENT ON VIEW rag.v_doc_indexing_status IS 'Status indexing pipeline per dokumen, untuk monitoring';


-- View: retrieval analytics ringkasan
CREATE VIEW rag.v_retrieval_analytics AS
SELECT
    q.query_id,
    q.user_id,
    q.intent_type,
    q.search_mode,
    q.created_at                        AS query_time,
    r.response_id,
    r.model_name,
    r.total_tokens,
    r.latency_ms,
    r.confidence_level,
    COUNT(DISTINCT rr.chunk_id)         AS chunks_retrieved,
    COUNT(DISTINCT c.citation_id)       AS citations_used,
    COUNT(DISTINCT rr.doc_id)           AS unique_docs_retrieved
FROM rag.ai_query q
LEFT JOIN rag.ai_response r         ON r.query_id = q.query_id
LEFT JOIN rag.retrieval_result rr   ON rr.query_id = q.query_id
LEFT JOIN rag.citation c            ON c.response_id = r.response_id
GROUP BY
    q.query_id, q.user_id, q.intent_type, q.search_mode, q.created_at,
    r.response_id, r.model_name, r.total_tokens, r.latency_ms, r.confidence_level;

COMMENT ON VIEW rag.v_retrieval_analytics IS 'Analitik retrieval: setiap query dengan ringkasan hasil dan performa';


-- ============================================================
-- END OF POSTGRESQL RAG SCHEMA
-- Tables:  10 tables
-- Views:    2 views
-- Schema:  rag
-- Database: notarist_rag
-- ============================================================
