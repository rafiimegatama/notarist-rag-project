-- ============================================================
-- NOTARIST RAG Platform — PostgreSQL
-- Flyway V14 — apply FORCE ROW LEVEL SECURITY to every RLS table
-- ============================================================
--
-- WHY THIS MIGRATION EXISTS
--
-- V9 through V13 each ENABLE ROW LEVEL SECURITY and create tenant policies, and
-- each one documents — in a comment — that FORCE ROW LEVEL SECURITY is mandatory.
-- None of them ever issued the statement. V9 states the reason exactly:
--
--   "Flyway runs these migrations as the application role, which therefore OWNS
--    these tables, and a table owner BYPASSES plain ROW LEVEL SECURITY. Without
--    FORCE, every policy below would be silently inert for the exact role the
--    application connects as."
--
-- That is the live consequence: ENABLE alone does not constrain the table OWNER.
-- Flyway creates these tables, so the application role owns them, so every policy
-- added in V9–V13 is inert for the one role that matters. The failure is silent —
-- reads succeed, return rows, and cross every tenant boundary.
--
-- FORCE is idempotent and safe to apply to an already-forced table, so this runs
-- correctly whether or not a previous deployment applied V9–V13.
--
-- SCOPE: the exact set of tables that ENABLE ROW LEVEL SECURITY in V9–V13.
--   V9  notarist_user, dokumen_legal, ingestion_job
--   V10 notarial_case, case_timeline
--   V11 bundle, bundle_workflow, bundle_timeline
--   V12 ocr_review
--   V13 verification
-- No table gains RLS here that did not already have it; this migration only closes
-- the owner-bypass hole on tables whose policies already exist.
--
-- STILL NOT ENOUGH ON ITS OWN — the operational invariant from V9 is unchanged:
-- FORCE does not constrain a SUPERUSER or a role holding BYPASSRLS. Those ignore
-- RLS entirely, forced or not, and nothing in this file can detect it.
--
-- Supabase's default `postgres` role is documented only as having "admin
-- privileges"; whether it carries SUPERUSER or BYPASSRLS is NOT published, so it
-- is asserted here as UNKNOWN rather than guessed. Verify against the live database
-- with the role the application actually connects as:
--
--   SELECT current_user, rolsuper, rolbypassrls
--     FROM pg_roles WHERE rolname = current_user;
--
-- Both MUST be false. If either is true, every policy below is inert and the
-- isolation guarantee is void — connect as a dedicated non-superuser,
-- non-BYPASSRLS application role instead.
-- ============================================================

-- V9 — core tenant tables
ALTER TABLE notarist_user   FORCE ROW LEVEL SECURITY;
ALTER TABLE dokumen_legal   FORCE ROW LEVEL SECURITY;
ALTER TABLE ingestion_job   FORCE ROW LEVEL SECURITY;

-- V10 — case module
ALTER TABLE notarial_case   FORCE ROW LEVEL SECURITY;
ALTER TABLE case_timeline   FORCE ROW LEVEL SECURITY;

-- V11 — bundle module
ALTER TABLE bundle          FORCE ROW LEVEL SECURITY;
ALTER TABLE bundle_workflow FORCE ROW LEVEL SECURITY;
ALTER TABLE bundle_timeline FORCE ROW LEVEL SECURITY;

-- V12 — OCR review module
ALTER TABLE ocr_review      FORCE ROW LEVEL SECURITY;

-- V13 — verification module
ALTER TABLE verification    FORCE ROW LEVEL SECURITY;
