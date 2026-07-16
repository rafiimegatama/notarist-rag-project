-- ============================================================
-- NOTARIST RAG Platform — Verification module schema
-- Flyway V13
--
-- The Verification bounded context (notarist-verification) exposes the HUMAN verification
-- stage that follows OCR Review and precedes bundle approval. It is NOT OCR and NOT QC.
-- It persists one aggregate:
--
--   * Verification — a bundle's confirmation checklist, moved only through its status state
--                    machine (PENDING → UNDER_VERIFICATION → VERIFIED / FAILED, with return).
--                    Its children:
--                      - verification_checklist_item : one check (automatic or manual) + decision
--                      - verification_item_audit     : append-only history of every decision
--
-- Design notes:
--
--  1. Optimistic locking is a real `version` column (@Version on the JPA entity). Two verifiers
--     acting on the same bundle concurrently: one commits, the other gets an OptimisticLock
--     exception and must reload. Every checklist decision stamps the verification root
--     (reviewer_id / reviewed_at / status when it opens), so the root version bumps and guards
--     the whole aggregate.
--
--  2. NO foreign key from reviewer_id / bundle_id to other contexts' tables. Those identities are
--     owned by the AUTH and CASE contexts; a hard cross-context FK would couple migration order
--     and lifecycle. The boundary is kept by the application. (Same call V10/V12 made.)
--
--  3. Children carry NO tenant_id and get NO RLS policy of their own — they are tenant-scoped
--     transitively through verification, which is itself policy-filtered.
--
--  4. verification_item_audit is APPEND-ONLY. The UNIQUE (verification_id, sequence) constraint
--     is the append concurrency guard. History is never updated or deleted.
--
--  5. Row-level tenant isolation reuses the V9 machinery verbatim (notarist_tenant_visible +
--     notarist_set_identity). Fail-closed. FORCE ROW LEVEL SECURITY because Flyway runs as the
--     table owner, which would otherwise bypass the policy.
-- ============================================================

-- -------------------------------------------------------
-- verification: the Verification aggregate root (one per bundle)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS verification (
    verification_id      VARCHAR(36)  NOT NULL,
    bundle_id            VARCHAR(36)  NOT NULL,   -- the verified bundle; one verification per bundle
    tenant_id            VARCHAR(36)  NOT NULL,
    status               VARCHAR(40)  NOT NULL,   -- VerificationStatus enum
    reviewer_id          VARCHAR(36),             -- auth-context user id; null until first acted on
    reviewed_at          TIMESTAMPTZ,
    last_audit_sequence  INT          NOT NULL DEFAULT 0,   -- dense high-water mark for item-audit sequence
    version              INT          NOT NULL DEFAULT 0,    -- optimistic lock (@Version)
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_verification PRIMARY KEY (verification_id),
    CONSTRAINT uq_verification_bundle UNIQUE (bundle_id)
);

CREATE INDEX IF NOT EXISTS idx_verification_tenant ON verification (tenant_id);
CREATE INDEX IF NOT EXISTS idx_verification_status ON verification (tenant_id, status);

-- -------------------------------------------------------
-- verification_checklist_item: one check and its decision (aggregate child)
-- No tenant column — reached only through verification, which is policy-filtered.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS verification_checklist_item (
    item_id          VARCHAR(36)   NOT NULL,
    verification_id  VARCHAR(36)   NOT NULL,
    category         VARCHAR(40)   NOT NULL,   -- ChecklistCategory enum
    title            VARCHAR(500)  NOT NULL,
    mandatory        BOOLEAN       NOT NULL DEFAULT TRUE,
    check_type       VARCHAR(20)   NOT NULL,   -- AUTOMATIC|MANUAL
    status           VARCHAR(20)   NOT NULL,   -- PENDING|COMPLETED
    decision         VARCHAR(30),              -- PASS|FAIL|NOT_APPLICABLE|MANUAL_REQUIRED (null while PENDING)
    reviewer_id      VARCHAR(36),
    reviewed_at      TIMESTAMPTZ,
    comment          VARCHAR(2000),            -- mandatory when decision = FAIL
    sort_order       INT           NOT NULL,
    version          INT           NOT NULL DEFAULT 0,

    CONSTRAINT pk_verification_checklist_item PRIMARY KEY (item_id),
    CONSTRAINT fk_verification_item_verification FOREIGN KEY (verification_id)
        REFERENCES verification (verification_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_verification_item_verification
    ON verification_checklist_item (verification_id, sort_order);

-- -------------------------------------------------------
-- verification_item_audit: append-only history of every checklist decision
-- The UNIQUE (verification_id, sequence) constraint is the append concurrency guard.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS verification_item_audit (
    audit_id           VARCHAR(36)   NOT NULL,
    verification_id    VARCHAR(36)   NOT NULL,
    item_id            VARCHAR(36)   NOT NULL,
    decision           VARCHAR(30)   NOT NULL,   -- Decision applied
    previous_decision  VARCHAR(30),              -- the decision before this one, if any
    comment            VARCHAR(2000),
    reviewer_id        VARCHAR(36)   NOT NULL,
    reviewer_role      VARCHAR(50)   NOT NULL,
    occurred_at        TIMESTAMPTZ   NOT NULL,
    sequence           INT           NOT NULL,

    CONSTRAINT pk_verification_item_audit PRIMARY KEY (audit_id),
    CONSTRAINT fk_verification_item_audit_verification FOREIGN KEY (verification_id)
        REFERENCES verification (verification_id) ON DELETE CASCADE,
    CONSTRAINT uq_verification_item_audit_sequence UNIQUE (verification_id, sequence)
);

CREATE INDEX IF NOT EXISTS idx_verification_item_audit_verification
    ON verification_item_audit (verification_id, sequence);
CREATE INDEX IF NOT EXISTS idx_verification_item_audit_item
    ON verification_item_audit (item_id, sequence);

-- -------------------------------------------------------
-- Row-level tenant isolation — reuses V9's notarist_tenant_visible() predicate.
-- Only the tenant-bearing root gets a policy; children are scoped through it.
-- -------------------------------------------------------
ALTER TABLE verification ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS verification_tenant_policy ON verification;
CREATE POLICY verification_tenant_policy ON verification
    FOR ALL
    USING      (notarist_tenant_visible(tenant_id))
    WITH CHECK (notarist_tenant_visible(tenant_id));
