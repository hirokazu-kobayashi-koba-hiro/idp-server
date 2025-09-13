-- =============================================================================
-- User Role Verification Query
-- =============================================================================
-- Purpose: Verify PostgreSQL user creation and role configuration
-- Usage: psql -h your-db-host -U postgres -d idpserver -f select-user-role.sql
-- =============================================================================

-- User creation and role privilege verification
SELECT
    rolname,
    rolsuper,
    rolbypassrls,
    rolconnlimit
FROM pg_roles
WHERE rolname IN ('idp_admin_user', 'idp_app_user')
ORDER BY rolname;

-- Expected result:
--     rolname     | rolsuper | rolbypassrls | rolconnlimit
-- ----------------+----------+--------------+--------------
--  idp_admin_user | f        | t            |           25
--  idp_app_user   | f        | f            |           -1
-- (2 rows)
--
-- Verification points:
-- - idp_admin_user: rolbypassrls=t (RLS BYPASS privilege)
-- - idp_app_user: rolbypassrls=f (subject to RLS)
-- - Both users: rolsuper=f (not superuser)