-- Runs once, as the container's admin (superuser) role, before the app connects.
--
-- The integration test must exercise row-level security the way production does. PostgreSQL RLS is
-- IGNORED for superusers and for roles with BYPASSRLS — FORCE or not. The Testcontainers default user
-- is a superuser, so connecting as it would make every RLS assertion pass vacuously. We create a
-- dedicated NOSUPERUSER / NOBYPASSRLS application role, run Flyway as it (so it OWNS the tables and
-- FORCE ROW LEVEL SECURITY bites), and point the app at it.

CREATE ROLE notarist_app LOGIN PASSWORD 'notarist_app' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;

GRANT ALL ON SCHEMA public TO notarist_app;
