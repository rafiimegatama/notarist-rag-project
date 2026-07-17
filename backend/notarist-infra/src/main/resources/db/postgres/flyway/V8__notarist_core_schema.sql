-- ============================================================
-- NOTARIST RAG Platform — PostgreSQL core schema
-- Flyway V8 — port of Oracle Liquibase V001/V002/V003
--
-- These four tables previously lived ONLY in Oracle (NOTARIST schema) and were
-- reached through JPA. Oracle is removed, so they move here, into the same
-- database as chunk_index / audit_trail / session_token / ingestion_queue.
--
-- Translation notes (Oracle -> PostgreSQL):
--   VARCHAR2(n)            -> VARCHAR(n)
--   NUMBER(1) 0|1 flag     -> BOOLEAN            (ACTIVE)
--   NUMBER(19)/(5)/(3)     -> BIGINT / INT       (no scale games needed)
--   BINARY_FLOAT           -> REAL               (OCR_CONFIDENCE)
--   CLOB                   -> TEXT               (STAGE_HISTORY)
--   TIMESTAMP + SYSTIMESTAMP -> TIMESTAMPTZ + NOW()
--   SEQUENCE + trigger-less PK -> GENERATED ALWAYS AS IDENTITY (USER_ROLE_MAP)
--   NOTARIST / NOTARIST_SEC schemas -> public (single Supabase schema)
--
-- Identifiers are lowercase: Hibernate's CamelCaseToUnderscoresNamingStrategy
-- lowercases the entity's @Table/@Column names, and unquoted PostgreSQL folds to
-- lowercase too, so "NOTARIST_USER" in the entity resolves to notarist_user here.
--
-- Row-level tenant isolation (the Oracle VPD replacement) is V9, deliberately a
-- separate migration so the security policy can be reviewed on its own.
--
-- NOT ported: the Oracle NOTARIST_SEC.AUDIT_TRAIL table. Audit already lives in
-- PostgreSQL (V7) and the Oracle copy was retired and receiving no writes.
-- ============================================================

-- -------------------------------------------------------
-- notarist_user: platform accounts (authentication identity)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS notarist_user (
    user_id       VARCHAR(36)  NOT NULL,
    tenant_id     VARCHAR(36)  NOT NULL,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,   -- BCrypt, cost=12
    full_name     VARCHAR(255) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notarist_user PRIMARY KEY (user_id),
    CONSTRAINT uq_notarist_user_username UNIQUE (username)
);

CREATE INDEX IF NOT EXISTS idx_notarist_user_tenant ON notarist_user (tenant_id);

-- -------------------------------------------------------
-- user_role_map: user -> role assignments
-- Oracle used USER_ROLE_MAP_SEQ with allocationSize=1, i.e. a DB round trip per
-- insert. IDENTITY gives the same semantics natively and removes the sequence.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_role_map (
    role_map_id BIGINT      GENERATED ALWAYS AS IDENTITY,
    user_id     VARCHAR(36) NOT NULL,
    role_code   VARCHAR(50) NOT NULL,  -- STAFF|NOTARIS|PPAT_OFFICER|PIMPINAN|ADMIN

    CONSTRAINT pk_user_role_map PRIMARY KEY (role_map_id),
    CONSTRAINT fk_role_map_user FOREIGN KEY (user_id)
        REFERENCES notarist_user (user_id),
    CONSTRAINT uq_user_role_map_unique UNIQUE (user_id, role_code)
);

CREATE INDEX IF NOT EXISTS idx_user_role_map_user ON user_role_map (user_id);

-- -------------------------------------------------------
-- dokumen_legal: legal document aggregate root (akta, regulasi, SOP)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS dokumen_legal (
    document_id          VARCHAR(36)  NOT NULL,
    document_title       VARCHAR(500) NOT NULL,
    document_type        VARCHAR(50)  NOT NULL,  -- AKTA|REGULASI|SOP
    jenis_akta           VARCHAR(50),            -- APHT|SKMHT|FIDUSIA|ROYA|AJB|… null for REGULASI/SOP
    nomor_akta           VARCHAR(100),           -- {nomor}/{bulan}/{tahun}
    tanggal_akta         DATE,
    classification_level VARCHAR(50)  NOT NULL,  -- PUBLIC|INTERNAL|CONFIDENTIAL|STRICTLY_CONFIDENTIAL
    status               VARCHAR(50)  NOT NULL,
    minio_object_key     VARCHAR(500) NOT NULL,
    checksum_sha256      VARCHAR(64)  NOT NULL,  -- SHA-256 hex, duplicate detection
    file_size_bytes      BIGINT,
    mime_type            VARCHAR(100),
    notaris_id           VARCHAR(36),
    tenant_id            VARCHAR(36)  NOT NULL,
    uploaded_by          VARCHAR(36)  NOT NULL,
    page_count           INT,
    version_number       INT          NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    indexed_at           TIMESTAMPTZ,

    CONSTRAINT pk_dokumen_legal PRIMARY KEY (document_id),
    CONSTRAINT fk_dokumen_uploaded_by FOREIGN KEY (uploaded_by)
        REFERENCES notarist_user (user_id),
    CONSTRAINT uq_dokumen_checksum_tenant UNIQUE (checksum_sha256, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_dokumen_legal_tenant      ON dokumen_legal (tenant_id);
CREATE INDEX IF NOT EXISTS idx_dokumen_legal_status      ON dokumen_legal (status, tenant_id);
CREATE INDEX IF NOT EXISTS idx_dokumen_legal_type        ON dokumen_legal (document_type, tenant_id);
CREATE INDEX IF NOT EXISTS idx_dokumen_legal_uploaded_by ON dokumen_legal (uploaded_by);

-- -------------------------------------------------------
-- ingestion_job: pipeline job state + DLQ/retry tracking
-- Distinct from ingestion_queue (V1/V2), which is the worker dispatch queue.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS ingestion_job (
    ingestion_id         VARCHAR(36)   NOT NULL,
    job_id               VARCHAR(36)   NOT NULL,
    document_id          VARCHAR(36)   NOT NULL,
    tenant_id            VARCHAR(36)   NOT NULL,
    uploaded_by          VARCHAR(36)   NOT NULL,
    document_type        VARCHAR(50)   NOT NULL,
    classification_level VARCHAR(50)   NOT NULL,
    original_filename    VARCHAR(500)  NOT NULL,
    checksum_sha256      VARCHAR(64)   NOT NULL,
    pipeline_status      VARCHAR(50)   NOT NULL DEFAULT 'UPLOADED',
    overall_status       VARCHAR(50)   NOT NULL DEFAULT 'PENDING',

    -- DLQ / retry tracking
    failure_stage        VARCHAR(50),
    retry_count          INT           NOT NULL DEFAULT 0,
    last_error_code      VARCHAR(100),
    last_error_hash      VARCHAR(64),
    next_retry_at        TIMESTAMPTZ,
    dead_letter_reason   VARCHAR(1000),

    -- OCR stage output (Liquibase V003)
    ocr_confidence       REAL,
    ocr_object_key       VARCHAR(1000),

    -- JSON array of StageRecord; TEXT, not CLOB/oid — see the @Lob note on the entity
    stage_history        TEXT          NOT NULL DEFAULT '[]',

    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    completed_at         TIMESTAMPTZ,

    CONSTRAINT pk_ingestion_job PRIMARY KEY (ingestion_id),
    CONSTRAINT uq_ingestion_job_job_id UNIQUE (job_id),
    CONSTRAINT chk_ingest_job_status CHECK (pipeline_status IN (
        'UPLOADED', 'OCR_PENDING', 'OCR_COMPLETED',
        'NER_PENDING', 'NER_COMPLETED',
        'CHUNK_PENDING', 'CHUNK_COMPLETED',
        'EMBED_PENDING', 'INDEX_PENDING',
        'COMPLETED', 'FAILED', 'DLQ'
    ))
);

CREATE INDEX IF NOT EXISTS idx_ingest_job_tenant_status   ON ingestion_job (tenant_id, pipeline_status);
CREATE INDEX IF NOT EXISTS idx_ingest_job_checksum_tenant ON ingestion_job (checksum_sha256, tenant_id);
CREATE INDEX IF NOT EXISTS idx_ingest_job_retry           ON ingestion_job (pipeline_status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_ingest_job_document        ON ingestion_job (document_id);
CREATE INDEX IF NOT EXISTS idx_ingest_job_uploaded_by     ON ingestion_job (uploaded_by);
