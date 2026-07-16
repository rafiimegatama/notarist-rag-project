-- ============================================================
-- NOTARIST RAG Platform — Bundle bounded context
-- Flyway V11
--
-- A Bundle is the working collection of legal documents belonging to a Case. A Case
-- may own several. The bundle is modelled by two coordinated aggregates that share a
-- bundle_id:
--   * Bundle          — document COMPOSITION (OPEN/COMPLETE/LOCKED + its documents).
--   * BundleWorkflow   — the process LIFECYCLE (OPEN → … → DELIVERED → LOCKED, 10 states).
-- Splitting them keeps "are all documents here?" and "where is this in the office
-- process?" from collapsing into one overloaded status column.
--
-- Reuses the V9 RLS machinery verbatim (notarist_tenant_visible + notarist_set_identity).
-- Fail-closed, FORCE ROW LEVEL SECURITY (Flyway runs as the table owner, which would
-- otherwise bypass the policy). Child tables carry no tenant_id and get no policy of
-- their own — they are reached only through a policy-filtered parent (same as V9/V10).
--
-- Optimistic locking is a real `version` column (@Version) on each aggregate table.
-- ============================================================

-- -------------------------------------------------------
-- bundle: the Bundle composition aggregate
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS bundle (
    bundle_id               VARCHAR(36) NOT NULL,
    case_id                 VARCHAR(36) NOT NULL,
    tenant_id               VARCHAR(36) NOT NULL,
    bundle_type             VARCHAR(50) NOT NULL,   -- IDENTITY|LAND_CERTIFICATE|SUPPORTING|DRAFT_OUTPUT
    expected_document_count INT         NOT NULL DEFAULT 0,
    assembly_status         VARCHAR(50) NOT NULL DEFAULT 'OPEN',   -- OPEN|COMPLETE|LOCKED
    version                 INT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_bundle PRIMARY KEY (bundle_id)
);

CREATE INDEX IF NOT EXISTS idx_bundle_tenant ON bundle (tenant_id);
CREATE INDEX IF NOT EXISTS idx_bundle_case   ON bundle (tenant_id, case_id);

-- -------------------------------------------------------
-- bundle_document: the DocumentRefs a bundle holds (ids only — the Document aggregate
-- is referenced, never owned). No tenant column; reached only through bundle.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS bundle_document (
    bundle_id      VARCHAR(36)  NOT NULL,
    document_id    VARCHAR(36)  NOT NULL,
    role_in_bundle VARCHAR(100),

    CONSTRAINT pk_bundle_document PRIMARY KEY (bundle_id, document_id),
    CONSTRAINT fk_bundle_document_bundle FOREIGN KEY (bundle_id)
        REFERENCES bundle (bundle_id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- bundle_workflow: the BundleWorkflow process aggregate (one per bundle)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS bundle_workflow (
    bundle_id   VARCHAR(36) NOT NULL,
    case_id     VARCHAR(36) NOT NULL,
    tenant_id   VARCHAR(36) NOT NULL,
    status      VARCHAR(50) NOT NULL DEFAULT 'OPEN',   -- 10-state BundleWorkflowStatus
    version     INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_bundle_workflow PRIMARY KEY (bundle_id),
    CONSTRAINT fk_bundle_workflow_bundle FOREIGN KEY (bundle_id)
        REFERENCES bundle (bundle_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bundle_workflow_tenant ON bundle_workflow (tenant_id);
CREATE INDEX IF NOT EXISTS idx_bundle_workflow_status ON bundle_workflow (tenant_id, status);

-- -------------------------------------------------------
-- bundle_timeline / bundle_timeline_entry: the bundle's append-only story
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS bundle_timeline (
    timeline_id VARCHAR(36) NOT NULL,
    bundle_id   VARCHAR(36) NOT NULL,
    tenant_id   VARCHAR(36) NOT NULL,
    status      VARCHAR(50) NOT NULL,   -- ACTIVE|SEALED
    version     INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_bundle_timeline PRIMARY KEY (timeline_id),
    CONSTRAINT uq_bundle_timeline_bundle UNIQUE (bundle_id)
);

CREATE INDEX IF NOT EXISTS idx_bundle_timeline_tenant ON bundle_timeline (tenant_id);

CREATE TABLE IF NOT EXISTS bundle_timeline_entry (
    entry_id       VARCHAR(36)   NOT NULL,
    timeline_id    VARCHAR(36)   NOT NULL,
    entry_type     VARCHAR(50)   NOT NULL,
    description    VARCHAR(2000) NOT NULL,
    actor_user_id  VARCHAR(36)   NOT NULL,
    actor_role     VARCHAR(50)   NOT NULL,
    occurred_at    TIMESTAMPTZ   NOT NULL,
    sequence       INT           NOT NULL,

    CONSTRAINT pk_bundle_timeline_entry PRIMARY KEY (entry_id),
    CONSTRAINT fk_bundle_timeline_entry_timeline FOREIGN KEY (timeline_id)
        REFERENCES bundle_timeline (timeline_id) ON DELETE CASCADE,
    CONSTRAINT uq_bundle_timeline_entry_sequence UNIQUE (timeline_id, sequence)
);

CREATE INDEX IF NOT EXISTS idx_bundle_timeline_entry_timeline
    ON bundle_timeline_entry (timeline_id, sequence);

-- -------------------------------------------------------
-- Row-level tenant isolation on the three tenant-bearing tables.
-- -------------------------------------------------------
ALTER TABLE bundle ENABLE ROW LEVEL SECURITY;
ALTER TABLE bundle FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS bundle_tenant_policy ON bundle;
CREATE POLICY bundle_tenant_policy ON bundle
    FOR ALL USING (notarist_tenant_visible(tenant_id)) WITH CHECK (notarist_tenant_visible(tenant_id));

ALTER TABLE bundle_workflow ENABLE ROW LEVEL SECURITY;
ALTER TABLE bundle_workflow FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS bundle_workflow_tenant_policy ON bundle_workflow;
CREATE POLICY bundle_workflow_tenant_policy ON bundle_workflow
    FOR ALL USING (notarist_tenant_visible(tenant_id)) WITH CHECK (notarist_tenant_visible(tenant_id));

ALTER TABLE bundle_timeline ENABLE ROW LEVEL SECURITY;
ALTER TABLE bundle_timeline FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS bundle_timeline_tenant_policy ON bundle_timeline;
CREATE POLICY bundle_timeline_tenant_policy ON bundle_timeline
    FOR ALL USING (notarist_tenant_visible(tenant_id)) WITH CHECK (notarist_tenant_visible(tenant_id));
