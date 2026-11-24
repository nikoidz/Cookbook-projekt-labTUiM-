\set ON_ERROR_STOP on

BEGIN;
CREATE ROLE cookbook_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
SELECT format('GRANT CONNECT ON DATABASE %I TO cookbook_app', current_database()) \gexec
GRANT USAGE ON SCHEMA public TO cookbook_app;
GRANT SELECT, INSERT ON public.users TO cookbook_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.recipes TO cookbook_app;
GRANT SELECT, INSERT, DELETE ON public.user_sessions TO cookbook_app;
GRANT USAGE ON SEQUENCE public.users_id_seq, public.recipes_id_seq TO cookbook_app;
COMMIT;
