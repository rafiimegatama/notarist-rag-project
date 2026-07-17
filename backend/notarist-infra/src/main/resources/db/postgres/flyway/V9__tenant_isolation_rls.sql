-- ============================================================
-- NOTARIST RAG Platform — row-level tenant isolation
-- Flyway V9 — port of Oracle Liquibase V001/V004/V005 (VPD)
--
-- WHAT THIS REPLACES
--   Oracle                                  PostgreSQL
--   ------                                  ----------
--   CREATE CONTEXT NOTARIST_CTX             session GUCs under the "notarist." prefix
--     USING NOTARIST.SET_NOTARIST_CTX
--   PACKAGE SET_NOTARIST_CTX                notarist_set_identity() / _system_identity() / _clear_identity()
--     DBMS_SESSION.SET_CONTEXT              set_config(..., is_local => true)
--   SYS_CONTEXT('NOTARIST_CTX','TENANT_ID') current_setting('notarist.tenant_id', true)
--   DBMS_RLS.ADD_POLICY(...)                CREATE POLICY ... ON ... USING/WITH CHECK
--   update_check => TRUE                    WITH CHECK clause
--
-- THE SECURITY CONTRACT IS PRESERVED EXACTLY — FAIL CLOSED.
-- A session that has established NO identity sees NO rows. In Oracle the predicate
-- function returned '1 = 0' for that case; here the equivalent falls out of SQL's
-- own null semantics: with the GUC unset, nullif(...) is NULL, and
-- "tenant_id = NULL" is NULL — never true — so no row passes. A query path that
-- simply forgets to set its tenant gets nothing, which is the entire point of the
-- backstop: it must never be possible to see every tenant's rows by omission.
--
-- The ONLY escape is notarist_set_system_identity(), which a caller must ask for
-- ON PURPOSE. It exists for the two paths that legitimately have no tenant:
--   1. the pre-authentication login lookup — you must read the user row to learn
--      the tenant, so a fail-closed policy alone makes login unsatisfiable; and
--   2. background ingestion workers, which run outside any HTTP request and
--      legitimately process jobs across every tenant.
-- Those two call sites are the whole exemption surface. Every new caller of
-- notarist_set_system_identity() widens a hole in the isolation guarantee.
--
-- is_local => true is a genuine improvement over the Oracle original: the setting
-- is scoped to the current TRANSACTION and PostgreSQL discards it at commit or
-- rollback. Oracle needed an explicit clear_identity() on the same connection or a
-- pooled connection kept the previous borrower's tenant. Here that leak is closed
-- by the database, not by remembering to call a cleanup hook. The appliers still
-- require an active transaction (outside one, a local set_config would be a no-op
-- and the policy would then correctly show the caller nothing).
--
-- FORCE ROW LEVEL SECURITY is required, not optional: Flyway runs these migrations
-- as the application role, which therefore OWNS these tables, and a table owner
-- BYPASSES plain ROW LEVEL SECURITY. Without FORCE, every policy below would be
-- silently inert for the exact role the application connects as.
--
-- OPERATIONAL CONSEQUENCES — read before seeding data:
--   * The app must NOT connect as a PostgreSQL SUPERUSER or as a role with the
--     BYPASSRLS attribute. Superusers and BYPASSRLS roles ignore RLS entirely,
--     FORCE or not, and the isolation guarantee evaporates. On Supabase, connect
--     as a dedicated non-superuser application role.
--   * Seeding/backfilling these tables by hand is now subject to the policies. Do
--     it inside a transaction that first calls notarist_set_system_identity(), or
--     as a role that owns the table with RLS temporarily disabled.
--
-- user_role_map carries NO tenant column (role_map_id, user_id, role_code), so no
-- direct-column policy is possible and none is added — exactly as in Oracle. It is
-- tenant-scoped transitively: every read path reaches it through a notarist_user
-- row that is itself policy-filtered.
--
-- SCOPE: these three tables only. That is precisely the set Oracle VPD covered.
-- The pipeline tables (chunk_index, ingestion_queue, dead_letter_queue, …) were
-- never under VPD and are not brought under RLS here; extending isolation to them
-- is a real improvement but a separate, testable change, not something to smuggle
-- into a database migration.
-- ============================================================

-- -------------------------------------------------------
-- Session identity — the SET_NOTARIST_CTX package, in PostgreSQL
-- -------------------------------------------------------

-- Establishes the caller's identity for the current transaction.
CREATE OR REPLACE FUNCTION notarist_set_identity(
    p_user_id   TEXT,
    p_tenant_id TEXT,
    p_role      TEXT
) RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM set_config('notarist.user_id',   COALESCE(p_user_id,   ''), TRUE);
    PERFORM set_config('notarist.tenant_id', COALESCE(p_tenant_id, ''), TRUE);
    PERFORM set_config('notarist.user_role', COALESCE(p_role,      ''), TRUE);
    -- A transaction that previously claimed the system exemption must not keep it
    -- once a real tenant identity is put on it.
    PERFORM set_config('notarist.system_session', '', TRUE);
END;
$$;

-- Marks the transaction as a trusted system session, exempt from tenant filtering.
-- See the header: this is the login lookup and the ingestion workers, and nothing else.
CREATE OR REPLACE FUNCTION notarist_set_system_identity() RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    -- Drop any inherited identity first, so a system session can never run with a
    -- stale tenant still attached.
    PERFORM set_config('notarist.user_id',   '', TRUE);
    PERFORM set_config('notarist.tenant_id', '', TRUE);
    PERFORM set_config('notarist.user_role', '', TRUE);
    PERFORM set_config('notarist.system_session', 'Y', TRUE);
END;
$$;

-- Clears the identity. PostgreSQL already discards a transaction-local setting at
-- commit/rollback, so this is a belt-and-braces hook for the appliers rather than
-- the load-bearing cleanup that Oracle's clear_identity() had to be.
CREATE OR REPLACE FUNCTION notarist_clear_identity() RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM set_config('notarist.user_id',   '', TRUE);
    PERFORM set_config('notarist.tenant_id', '', TRUE);
    PERFORM set_config('notarist.user_role', '', TRUE);
    PERFORM set_config('notarist.system_session', '', TRUE);
END;
$$;

-- The predicate, once, so the three policies below cannot drift apart.
-- STABLE (not IMMUTABLE): it reads session state, so the planner may cache it
-- within a statement but must not fold it across statements.
CREATE OR REPLACE FUNCTION notarist_tenant_visible(p_tenant_id VARCHAR)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT
        -- Trusted system session: asked for explicitly. Unrestricted.
        COALESCE(current_setting('notarist.system_session', TRUE), '') = 'Y'
        -- Otherwise the row must match the caller's tenant. With no tenant set,
        -- nullif() yields NULL, the comparison is NULL, and the row is invisible.
        OR p_tenant_id = NULLIF(current_setting('notarist.tenant_id', TRUE), '');
$$;

-- -------------------------------------------------------
-- Policies
-- -------------------------------------------------------

-- notarist_user — the login lookup reaches this table with no tenant yet, and opts
-- into the system exemption explicitly (UserRepositoryImpl.findByUsername).
ALTER TABLE notarist_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE notarist_user FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS notarist_user_tenant_policy ON notarist_user;
CREATE POLICY notarist_user_tenant_policy ON notarist_user
    FOR ALL
    USING      (notarist_tenant_visible(tenant_id))
    WITH CHECK (notarist_tenant_visible(tenant_id));

-- dokumen_legal — the sensitive legal content. No exemption path reaches it: the
-- document repository only ever applies the principal's identity.
ALTER TABLE dokumen_legal ENABLE ROW LEVEL SECURITY;
ALTER TABLE dokumen_legal FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS dokumen_legal_tenant_policy ON dokumen_legal;
CREATE POLICY dokumen_legal_tenant_policy ON dokumen_legal
    FOR ALL
    USING      (notarist_tenant_visible(tenant_id))
    WITH CHECK (notarist_tenant_visible(tenant_id));

-- ingestion_job — reached from an authenticated upload (principal's tenant) and
-- from the background pipeline workers (system exemption).
ALTER TABLE ingestion_job ENABLE ROW LEVEL SECURITY;
ALTER TABLE ingestion_job FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS ingest_job_tenant_policy ON ingestion_job;
CREATE POLICY ingest_job_tenant_policy ON ingestion_job
    FOR ALL
    USING      (notarist_tenant_visible(tenant_id))
    WITH CHECK (notarist_tenant_visible(tenant_id));
