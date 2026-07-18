-- Seed a demo login for local end-to-end testing (dev Supabase database).
-- Creates user demo.notaris / NotaristDemo123! in the same tenant as notaris.dev
-- (391f5b15-…) so the cases created in Sprint 7 are visible after login.
--
-- Run (from infra/docker, so .env.hybrid supplies the password):
--   docker run --rm -e PGPASSWORD="$(grep '^POSTGRES_PASSWORD=' .env.hybrid | cut -d= -f2-)" \
--     -v "$(git rev-parse --show-toplevel)/generated/sql/seed-demo-user.sql:/seed.sql:ro" \
--     postgres:16-alpine psql \
--     "host=aws-1-ap-northeast-1.pooler.supabase.com port=5432 dbname=postgres user=notarist_app.pibhkvcblvpgiudieujw sslmode=require" \
--     -v ON_ERROR_STOP=1 -f /seed.sql
--
-- RLS note: notarist_user is FORCE RLS; the system-session exemption below is the
-- same one UserRepositoryImpl.findByUsername uses for the login lookup.

SELECT set_config('notarist.system_session', 'Y', false);

DO $$
DECLARE
  v_hash text;
BEGIN
  -- BCrypt cost 12, matching PasswordVerifier's BCryptPasswordEncoder(12).
  -- pgcrypto lives in the extensions schema on Supabase; fall back if unqualified fails.
  BEGIN
    v_hash := crypt('NotaristDemo123!', gen_salt('bf', 12));
  EXCEPTION WHEN undefined_function THEN
    v_hash := extensions.crypt('NotaristDemo123!', extensions.gen_salt('bf', 12));
  END;

  INSERT INTO notarist_user (user_id, tenant_id, username, password_hash, full_name, active)
  VALUES ('d3a0de10-0000-4000-8000-000000000001',
          '391f5b15-4af2-448f-abec-76bc0d43ffd9',
          'demo.notaris', v_hash, 'Demo Notaris', TRUE)
  ON CONFLICT (user_id) DO UPDATE
      SET password_hash = EXCLUDED.password_hash, active = TRUE;
END $$;

INSERT INTO user_role_map (user_id, role_code)
VALUES ('d3a0de10-0000-4000-8000-000000000001', 'NOTARIS')
ON CONFLICT (user_id, role_code) DO NOTHING;

SELECT username, full_name, active FROM notarist_user WHERE username = 'demo.notaris';
