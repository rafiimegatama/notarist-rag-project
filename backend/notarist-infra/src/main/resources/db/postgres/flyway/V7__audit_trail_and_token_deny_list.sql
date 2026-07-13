-- ============================================================
-- NOTARIST RAG Platform — PostgreSQL
-- Flyway V7 — F9: durable audit trail   (audit_trail)
--             F14: durable JWT deny-list (token_deny_list)
-- ============================================================

-- -------------------------------------------------------
-- audit_trail — append-only security & access audit trail.
--
-- Written by notarist-audit AuditTrailRepositoryImpl, fed by AuditEventListener,
-- which consumes the AuditEventPayload events that auth/document/ingest publish.
-- Before V7 there was no consumer and no table: every login, logout, token refresh,
-- document access and ingestion transition was published to the in-VM event bus
-- and dropped (F9).
--
-- PostgreSQL rather than Oracle NOTARIST_SEC.AUDIT_TRAIL because the most
-- compliance-critical events (AUTH_LOGIN_FAILURE, SECURITY_ACCESS_DENIED) occur
-- with no established VPD identity and would be filtered/rejected by Oracle's
-- row-level policies, and because the Oracle DataSource is still unpooled (F12).
--
-- Append-only: INSERT only. No UPDATE or DELETE path exists in the adapter.
-- Retention: 7 years (legal document domain requirement) — enforced operationally,
-- not by this schema; nothing here deletes rows.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_trail (
    audit_id        UUID          NOT NULL,
    correlation_id  VARCHAR(100),                        -- CorrelationId VO, propagated from the request
    trace_id        VARCHAR(100),                        -- TraceId VO (not carried on AuditEventPayload yet)
    event_type      VARCHAR(64)   NOT NULL,              -- AuditEventType enum name, exactly as emitted
    event_category  VARCHAR(32)   NOT NULL,              -- DOCUMENT|INGEST|SEARCH|ASSISTANT|AUTH|SECURITY|UNKNOWN
    subject_type    VARCHAR(64),                         -- USER|SESSION|DOCUMENT|INGESTION_JOB
    subject_id      VARCHAR(200),                        -- id of the subject, or 'ALL' for list operations
    actor_user_id   UUID,                                -- NULL for system/background actors
    actor_role      VARCHAR(64),
    tenant_id       UUID,
    action          VARCHAR(100),
    outcome         VARCHAR(16)   NOT NULL,              -- AuditOutcome: SUCCESS|FAILURE|PARTIAL
    detail_json     JSONB         NOT NULL DEFAULT '{}', -- event-specific payload; carries sourceEventType when UNMAPPED
    ip_address      VARCHAR(45),                         -- IPv6-capable length
    user_agent      VARCHAR(512),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_trail PRIMARY KEY (audit_id)
);

-- Primary console query: an auditor scans one tenant's trail, newest first.
CREATE INDEX IF NOT EXISTS idx_audit_trail_tenant_created
    ON audit_trail (tenant_id, created_at DESC);

-- "What did this user do?" — actor-scoped investigation.
CREATE INDEX IF NOT EXISTS idx_audit_trail_actor
    ON audit_trail (actor_user_id, created_at DESC);

-- "Who touched this document/session?" — subject-scoped investigation.
CREATE INDEX IF NOT EXISTS idx_audit_trail_subject
    ON audit_trail (subject_id, created_at DESC);

-- Category/type filtering (AuditFilter.eventCategory / eventType).
CREATE INDEX IF NOT EXISTS idx_audit_trail_category_type
    ON audit_trail (event_category, event_type, created_at DESC);

-- Failed-access sweep: brute-force / denied-access detection reads only the failures,
-- which are a small fraction of the table.
CREATE INDEX IF NOT EXISTS idx_audit_trail_failures
    ON audit_trail (created_at DESC)
    WHERE outcome = 'FAILURE';

-- detail_json containment queries (e.g. detail_json @> '{"ingestionId":"..."}').
CREATE INDEX IF NOT EXISTS idx_audit_trail_detail
    ON audit_trail USING GIN (detail_json);

COMMENT ON TABLE  audit_trail            IS 'Append-only security & access audit trail. INSERT only — no UPDATE/DELETE. 7-year retention.';
COMMENT ON COLUMN audit_trail.event_type IS 'AuditEventType enum name, stored exactly as the publisher emitted it; UNMAPPED preserves the raw string in detail_json.sourceEventType.';
COMMENT ON COLUMN audit_trail.outcome    IS 'SUCCESS|FAILURE|PARTIAL. FAILURE covers failed logins and denied access — the compliance-critical rows.';


-- -------------------------------------------------------
-- token_deny_list — durable, cluster-wide JWT revocation (F14).
--
-- Written by auth TokenDenyListRepositoryImpl on logout; read by JwtAuthenticationFilter
-- on every authenticated request. Replaces a per-instance ConcurrentHashMap under which a
-- revoked token stayed valid on every peer instance, and across a restart, until its
-- natural expiry.
--
-- PostgreSQL rather than Redis: Redis is not part of the deployed stack
-- (infra/docker/docker-compose.yml = MinIO + PostgreSQL + Qdrant + Ollama) and adding it
-- would introduce a new production failure domain for a table this small.
--
-- Rows are transient by design: an entry is only useful until the token would have expired
-- anyway, after which JWT exp validation rejects it unaided. evictExpiredEntries() purges
-- them every 5 minutes.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS token_deny_list (
    jti         VARCHAR(100) NOT NULL,               -- JWT ID claim of the revoked access token
    expires_at  TIMESTAMPTZ  NOT NULL,               -- natural expiry of that token; row is dead weight after this
    revoked_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_token_deny_list PRIMARY KEY (jti)
);

-- Purge sweep (DELETE ... WHERE expires_at <= NOW()) every 5 minutes.
CREATE INDEX IF NOT EXISTS idx_token_deny_list_expires
    ON token_deny_list (expires_at);

COMMENT ON TABLE  token_deny_list            IS 'Durable cluster-wide JWT deny-list. One row per logout, purged once the token would have expired on its own.';
COMMENT ON COLUMN token_deny_list.expires_at IS 'Remaining access-token validity at revocation time. isDenied() gates on expires_at > NOW(), so a not-yet-purged expired row correctly reads as not denied.';
