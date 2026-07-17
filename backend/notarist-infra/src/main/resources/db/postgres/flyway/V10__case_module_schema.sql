-- ============================================================
-- NOTARIST RAG Platform — Case module schema (first vertical slice)
-- Flyway V10
--
-- The Case bounded context (notarist-case) persists two aggregates:
--   * Case     — a unit of notarial work, moved only through its state machine.
--   * Timeline — the append-only story of that case (one per case).
--
-- Design notes, so the next reader does not have to reverse-engineer them:
--
--  1. Table is `notarial_case`, not `case`. CASE is a reserved SQL keyword; naming
--     the table `case` would force quoting at every reference. `notarial_case`
--     folds to lowercase like every other unquoted identifier here (see V8).
--
--  2. Optimistic locking is a real `version` column (@Version on the JPA entity),
--     not the hand-maintained version_number that dokumen_legal carries. Two
--     concurrent status changes on the same case: one commits, the other gets an
--     OptimisticLockException and must reload. This is the concurrency guard the
--     Case aggregate's single-writer transition() relies on at the database level.
--
--  3. NO foreign key from created_by / assigned_notaris_id to notarist_user.
--     Those columns reference identities owned by the AUTH bounded context. A hard
--     cross-context FK would couple the two schemas' migration order and lifecycle;
--     the boundary is kept by the application, not by the database. (dokumen_legal
--     made the opposite call for uploaded_by; the Case context is deliberately
--     decoupled from the auth tables.)
--
--  4. Timeline entries and bundle refs carry NO tenant_id and get NO RLS policy of
--     their own — exactly as user_role_map does in V9. They are tenant-scoped
--     transitively: every read path reaches them through a parent row
--     (notarial_case / case_timeline) that is itself policy-filtered.
--
--  5. Row-level tenant isolation reuses the V9 machinery verbatim
--     (notarist_tenant_visible + notarist_set_identity). Fail-closed: a session
--     with no identity set sees no cases. FORCE ROW LEVEL SECURITY because Flyway
--     runs as the table owner, which would otherwise bypass the policy.
-- ============================================================

-- -------------------------------------------------------
-- notarial_case: the Case aggregate root
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS notarial_case (
    case_id              VARCHAR(36)  NOT NULL,
    case_number          VARCHAR(100) NOT NULL,   -- {nomor}/{bulan}/{tahun}, tenant-unique
    case_type            VARCHAR(50)  NOT NULL,    -- APHT|SKMHT|AJB|FIDUSIA|ROYA|WASIAT|KUASA|PENDIRIAN_PT|LAINNYA
    tenant_id            VARCHAR(36)  NOT NULL,
    created_by           VARCHAR(36)  NOT NULL,    -- auth-context user id; no cross-context FK (see header)
    assigned_notaris_id  VARCHAR(36),              -- auth-context user id; nullable until assigned
    state                VARCHAR(50)  NOT NULL,    -- CaseState enum
    nomor_akta           VARCHAR(100),             -- allocated from the Repertorium at FINALIZED, never before
    version              INT          NOT NULL DEFAULT 0,   -- optimistic lock (@Version)
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    closed_at            TIMESTAMPTZ,              -- set when the case reaches a terminal state

    CONSTRAINT pk_notarial_case PRIMARY KEY (case_id),
    CONSTRAINT uq_notarial_case_number_tenant UNIQUE (tenant_id, case_number)
);

CREATE INDEX IF NOT EXISTS idx_notarial_case_tenant        ON notarial_case (tenant_id);
CREATE INDEX IF NOT EXISTS idx_notarial_case_state         ON notarial_case (tenant_id, state);
CREATE INDEX IF NOT EXISTS idx_notarial_case_notaris       ON notarial_case (tenant_id, assigned_notaris_id);
CREATE INDEX IF NOT EXISTS idx_notarial_case_created_by    ON notarial_case (tenant_id, created_by);
CREATE INDEX IF NOT EXISTS idx_notarial_case_type          ON notarial_case (tenant_id, case_type);
CREATE INDEX IF NOT EXISTS idx_notarial_case_created_at    ON notarial_case (tenant_id, created_at);

-- -------------------------------------------------------
-- case_bundle_ref: the Case's referenced bundle ids (aggregate holds ids only)
-- No tenant column — reached only through notarial_case, which is policy-filtered.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS case_bundle_ref (
    case_id    VARCHAR(36) NOT NULL,
    bundle_id  VARCHAR(36) NOT NULL,

    CONSTRAINT pk_case_bundle_ref PRIMARY KEY (case_id, bundle_id),
    CONSTRAINT fk_case_bundle_ref_case FOREIGN KEY (case_id)
        REFERENCES notarial_case (case_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_case_bundle_ref_case ON case_bundle_ref (case_id);

-- -------------------------------------------------------
-- case_timeline: the Timeline aggregate root (one per case)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS case_timeline (
    timeline_id  VARCHAR(36)  NOT NULL,
    case_id      VARCHAR(36)  NOT NULL,
    tenant_id    VARCHAR(36)  NOT NULL,
    status       VARCHAR(50)  NOT NULL,    -- ACTIVE|SEALED
    version      INT          NOT NULL DEFAULT 0,   -- optimistic lock (@Version)
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_case_timeline PRIMARY KEY (timeline_id),
    CONSTRAINT uq_case_timeline_case UNIQUE (case_id)
);

CREATE INDEX IF NOT EXISTS idx_case_timeline_tenant ON case_timeline (tenant_id);

-- -------------------------------------------------------
-- case_timeline_entry: one immutable line in a case's story
-- Append-only. The UNIQUE (timeline_id, sequence) constraint is the concurrency
-- guard for appends: two workers racing to append the same sequence — one wins,
-- the other's insert is rejected, which is exactly the aggregate's dense-sequence
-- invariant enforced at the database.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS case_timeline_entry (
    entry_id       VARCHAR(36)   NOT NULL,
    timeline_id    VARCHAR(36)   NOT NULL,
    entry_type     VARCHAR(50)   NOT NULL,   -- TimelineEntryType enum
    description    VARCHAR(2000) NOT NULL,
    actor_user_id  VARCHAR(36)   NOT NULL,   -- SYSTEM actor uses the all-zero UUID, never null
    actor_role     VARCHAR(50)   NOT NULL,   -- STAFF|NOTARIS|PPAT_OFFICER|PIMPINAN|ADMIN|SYSTEM
    occurred_at    TIMESTAMPTZ   NOT NULL,
    sequence       INT           NOT NULL,

    CONSTRAINT pk_case_timeline_entry PRIMARY KEY (entry_id),
    CONSTRAINT fk_timeline_entry_timeline FOREIGN KEY (timeline_id)
        REFERENCES case_timeline (timeline_id) ON DELETE CASCADE,
    CONSTRAINT uq_timeline_entry_sequence UNIQUE (timeline_id, sequence)
);

CREATE INDEX IF NOT EXISTS idx_timeline_entry_timeline ON case_timeline_entry (timeline_id, sequence);

-- -------------------------------------------------------
-- Row-level tenant isolation — reuses V9's notarist_tenant_visible() predicate.
-- Only the two tenant-bearing tables get a policy; children are scoped through them.
-- -------------------------------------------------------
ALTER TABLE notarial_case ENABLE ROW LEVEL SECURITY;
ALTER TABLE notarial_case FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS notarial_case_tenant_policy ON notarial_case;
CREATE POLICY notarial_case_tenant_policy ON notarial_case
    FOR ALL
    USING      (notarist_tenant_visible(tenant_id))
    WITH CHECK (notarist_tenant_visible(tenant_id));

ALTER TABLE case_timeline ENABLE ROW LEVEL SECURITY;
ALTER TABLE case_timeline FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS case_timeline_tenant_policy ON case_timeline;
CREATE POLICY case_timeline_tenant_policy ON case_timeline
    FOR ALL
    USING      (notarist_tenant_visible(tenant_id))
    WITH CHECK (notarist_tenant_visible(tenant_id));
