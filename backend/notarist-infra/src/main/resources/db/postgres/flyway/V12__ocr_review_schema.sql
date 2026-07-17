-- ============================================================
-- NOTARIST RAG Platform — OCR Review module schema
-- Flyway V12
--
-- The OCR Review bounded context (notarist-review) exposes the HUMAN review process
-- that follows OCR extraction. It is NOT OCR inference — that already runs elsewhere.
-- This module persists one aggregate:
--
--   * OcrReview — a document's extracted fields plus the reviewer's decisions on them,
--                 moved only through its status state machine (PENDING → IN_PROGRESS →
--                 REVIEW_COMPLETED → VERIFIED). Its children:
--                   - ocr_review_field        : one extracted field + its review decision
--                   - ocr_authority_item      : read-only authority extraction (clause /
--                                               director timeline / current directors /
--                                               signing authority), confirmable/rejectable
--                   - ocr_review_field_audit  : append-only history of every field decision
--
-- Design notes, so the next reader does not have to reverse-engineer them:
--
--  1. Optimistic locking is a real `version` column (@Version on the JPA entity). Two
--     reviewers acting on the same document concurrently: one commits, the other gets an
--     OptimisticLockException and must reload. Every field decision also stamps the review
--     root (reviewer_id / reviewed_at / status), so the root version bumps and guards the
--     whole aggregate — a field write cannot silently interleave with another reviewer's.
--
--  2. NO foreign key from reviewer_id to notarist_user. Those columns reference identities
--     owned by the AUTH bounded context; a hard cross-context FK would couple the two
--     schemas' migration order and lifecycle. The boundary is kept by the application.
--     (Same call the Case context made in V10.)
--
--  3. Children carry NO tenant_id and get NO RLS policy of their own — exactly as
--     case_timeline_entry does in V10. They are tenant-scoped transitively: every read
--     path reaches them through ocr_review, which is itself policy-filtered.
--
--  4. ocr_review_field_audit is APPEND-ONLY. The UNIQUE (review_id, sequence) constraint
--     is the concurrency guard for appends: two writers racing to append the same sequence
--     — one wins, the other's insert is rejected. History is never updated or deleted.
--
--  5. Row-level tenant isolation reuses the V9 machinery verbatim (notarist_tenant_visible
--     + notarist_set_identity). Fail-closed: a session with no identity set sees no reviews.
--     FORCE ROW LEVEL SECURITY because Flyway runs as the table owner, which would
--     otherwise bypass the policy.
-- ============================================================

-- -------------------------------------------------------
-- ocr_review: the OcrReview aggregate root (one per document)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS ocr_review (
    review_id            VARCHAR(36)  NOT NULL,
    document_id          VARCHAR(36)  NOT NULL,   -- the reviewed document; one review per document
    tenant_id            VARCHAR(36)  NOT NULL,
    review_status        VARCHAR(50)  NOT NULL,   -- ReviewStatus enum
    reviewer_id          VARCHAR(36),             -- auth-context user id; null until first acted on
    reviewed_at          TIMESTAMPTZ,             -- timestamp of the most recent review action
    document_name        VARCHAR(500) NOT NULL,
    page_count           INT          NOT NULL,
    stamp_detected       BOOLEAN      NOT NULL DEFAULT FALSE,
    signature_detected   BOOLEAN      NOT NULL DEFAULT FALSE,
    overall_confidence   NUMERIC(5,4) NOT NULL DEFAULT 0,   -- 0.0000 .. 1.0000
    last_audit_sequence  INT          NOT NULL DEFAULT 0,    -- dense high-water mark for field-audit sequence
    version              INT          NOT NULL DEFAULT 0,    -- optimistic lock (@Version)
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ocr_review PRIMARY KEY (review_id),
    CONSTRAINT uq_ocr_review_document UNIQUE (document_id)
);

CREATE INDEX IF NOT EXISTS idx_ocr_review_tenant  ON ocr_review (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ocr_review_status  ON ocr_review (tenant_id, review_status);

-- -------------------------------------------------------
-- ocr_review_field: one extracted field and its review decision (aggregate child)
-- No tenant column — reached only through ocr_review, which is policy-filtered.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS ocr_review_field (
    field_id          VARCHAR(36)   NOT NULL,
    review_id         VARCHAR(36)   NOT NULL,
    field_name        VARCHAR(200)  NOT NULL,     -- machine name, e.g. NIK
    display_label     VARCHAR(200)  NOT NULL,     -- human label shown in the UI
    extracted_value   VARCHAR(4000),              -- original OCR output, never overwritten
    corrected_value   VARCHAR(4000),              -- reviewer's correction, if any
    confidence        NUMERIC(5,4)  NOT NULL,     -- 0.0000 .. 1.0000
    confidence_level  VARCHAR(20)   NOT NULL,     -- HIGH|MEDIUM|LOW (derived from confidence)
    decision          VARCHAR(30)   NOT NULL,     -- FieldDecision enum
    rejection_reason  VARCHAR(2000),              -- mandatory when decision = REJECTED
    reviewer_id       VARCHAR(36),                -- who last decided this field
    reviewed_at       TIMESTAMPTZ,
    bbox_page         INT           NOT NULL,     -- 1-based page number
    bbox_x            NUMERIC(9,6)  NOT NULL,     -- relative [0..1] coordinates
    bbox_y            NUMERIC(9,6)  NOT NULL,
    bbox_width        NUMERIC(9,6)  NOT NULL,
    bbox_height       NUMERIC(9,6)  NOT NULL,
    sort_order        INT           NOT NULL,
    version           INT           NOT NULL DEFAULT 0,

    CONSTRAINT pk_ocr_review_field PRIMARY KEY (field_id),
    CONSTRAINT fk_ocr_review_field_review FOREIGN KEY (review_id)
        REFERENCES ocr_review (review_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ocr_review_field_review ON ocr_review_field (review_id, sort_order);

-- -------------------------------------------------------
-- ocr_authority_item: read-only authority extraction, confirmable/rejectable (aggregate child)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS ocr_authority_item (
    authority_id     VARCHAR(36)   NOT NULL,
    review_id        VARCHAR(36)   NOT NULL,
    authority_type   VARCHAR(40)   NOT NULL,      -- AUTHORITY_CLAUSE|DIRECTOR_TIMELINE|CURRENT_DIRECTORS|SIGNING_AUTHORITY
    role_label       VARCHAR(200),                -- e.g. "Notaris", "Direksi Bank"
    person_name      VARCHAR(300),
    content          VARCHAR(4000),               -- clause text / directorship detail
    confidence       NUMERIC(5,4),
    decision         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING|CONFIRMED|REJECTED
    decided_at       TIMESTAMPTZ,
    sort_order       INT           NOT NULL,
    version          INT           NOT NULL DEFAULT 0,

    CONSTRAINT pk_ocr_authority_item PRIMARY KEY (authority_id),
    CONSTRAINT fk_ocr_authority_item_review FOREIGN KEY (review_id)
        REFERENCES ocr_review (review_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ocr_authority_item_review ON ocr_authority_item (review_id, sort_order);

-- -------------------------------------------------------
-- ocr_review_field_audit: append-only history of every field decision
-- The UNIQUE (review_id, sequence) constraint is the append concurrency guard.
-- Never updated, never deleted (except by the parent review's cascade).
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS ocr_review_field_audit (
    audit_id        VARCHAR(36)   NOT NULL,
    review_id       VARCHAR(36)   NOT NULL,
    field_id        VARCHAR(36)   NOT NULL,
    decision        VARCHAR(30)   NOT NULL,       -- FieldDecision applied
    previous_value  VARCHAR(4000),
    new_value       VARCHAR(4000),
    reason          VARCHAR(2000),                -- rejection reason, if any
    reviewer_id     VARCHAR(36)   NOT NULL,
    reviewer_role   VARCHAR(50)   NOT NULL,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    sequence        INT           NOT NULL,

    CONSTRAINT pk_ocr_review_field_audit PRIMARY KEY (audit_id),
    CONSTRAINT fk_ocr_field_audit_review FOREIGN KEY (review_id)
        REFERENCES ocr_review (review_id) ON DELETE CASCADE,
    CONSTRAINT uq_ocr_field_audit_sequence UNIQUE (review_id, sequence)
);

CREATE INDEX IF NOT EXISTS idx_ocr_field_audit_review ON ocr_review_field_audit (review_id, sequence);
CREATE INDEX IF NOT EXISTS idx_ocr_field_audit_field  ON ocr_review_field_audit (field_id, sequence);

-- -------------------------------------------------------
-- Row-level tenant isolation — reuses V9's notarist_tenant_visible() predicate.
-- Only the tenant-bearing root gets a policy; children are scoped through it.
-- -------------------------------------------------------
ALTER TABLE ocr_review ENABLE ROW LEVEL SECURITY;
ALTER TABLE ocr_review FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS ocr_review_tenant_policy ON ocr_review;
CREATE POLICY ocr_review_tenant_policy ON ocr_review
    FOR ALL
    USING      (notarist_tenant_visible(tenant_id))
    WITH CHECK (notarist_tenant_visible(tenant_id));
