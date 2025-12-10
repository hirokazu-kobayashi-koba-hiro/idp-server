-- ================================================
-- PostgreSQL Extensions Initialization
-- ================================================
-- This script initializes pg_cron and pg_partman extensions.
-- Must be run as superuser (e.g., postgres user).
--
-- Prerequisites:
--   - PostgreSQL 14+
--   - pg_cron and pg_partman extensions installed
--   - shared_preload_libraries includes: pg_cron, pg_partman_bgw
--
-- Usage:
--   psql -U postgres -d <database> -f 02-init-extensions.sql
--
-- For custom DB_OWNER_USER:
--   psql -U postgres -d <database> -v db_owner_user=myuser -f 02-init-extensions.sql
-- ================================================

-- Default DB_OWNER_USER if not provided
\if :{?db_owner_user}
\else
  \set db_owner_user 'idp'
\endif

\echo 'Initializing extensions for DB_OWNER_USER:' :db_owner_user

-- ================================================
-- 1. Create pg_cron extension
-- ================================================
-- Note: pg_cron must be in shared_preload_libraries (postgresql.conf)
-- shared_preload_libraries = 'pg_cron,pg_partman_bgw'

CREATE EXTENSION IF NOT EXISTS pg_cron;

\echo 'pg_cron extension created'

-- ================================================
-- 2. Create pg_partman extension
-- ================================================

CREATE SCHEMA IF NOT EXISTS partman;
CREATE EXTENSION IF NOT EXISTS pg_partman WITH SCHEMA partman;

\echo 'pg_partman extension created in partman schema'

-- ================================================
-- 3. Grant permissions to application owner user
-- ================================================

-- partman schema permissions
GRANT USAGE, CREATE ON SCHEMA partman TO :db_owner_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA partman TO :db_owner_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA partman TO :db_owner_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA partman TO :db_owner_user;

\echo 'Granted partman schema permissions to' :db_owner_user

-- cron schema permissions
GRANT USAGE ON SCHEMA cron TO :db_owner_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA cron TO :db_owner_user;

\echo 'Granted cron schema permissions to' :db_owner_user

-- ================================================
-- 4. Verification
-- ================================================

\echo ''
\echo '=== Installed Extensions ==='
SELECT extname, extversion FROM pg_extension WHERE extname IN ('pg_cron', 'pg_partman');

\echo ''
\echo '=== Schema Permissions ==='
SELECT
    nspname AS schema,
    pg_catalog.array_agg(privilege_type) AS privileges
FROM information_schema.role_usage_grants
JOIN pg_namespace ON nspname = object_schema
WHERE grantee = :'db_owner_user'
  AND object_schema IN ('partman', 'cron')
GROUP BY nspname;

\echo ''
\echo 'Extensions initialization completed successfully'
