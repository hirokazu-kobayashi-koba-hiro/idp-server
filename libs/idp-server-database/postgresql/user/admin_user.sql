-- =============================================================================
-- Admin User Creation with RLS Bypass (idp_admin_user)
-- =============================================================================
-- Purpose: Cross-tenant operations, initial data population, system management API
-- Feature: BYPASSRLS privilege to ignore RLS constraints
--
-- Execution Requirements: Must be executed by PostgreSQL superuser
-- =============================================================================

-- Create admin user with RLS bypass privilege
-- Use environment variable for password: IDP_ADMIN_PASSWORD
CREATE USER idp_admin_user WITH
    PASSWORD 'idp_admin_user'
    BYPASSRLS;  -- RLS bypass privilege

-- Grant basic permissions
GRANT CONNECT ON DATABASE idpserver TO idp_admin_user;
GRANT USAGE ON SCHEMA public TO idp_admin_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO idp_admin_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO idp_admin_user;

-- Grant permissions for future tables
ALTER DEFAULT PRIVILEGES
    FOR ROLE idpserver
    IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO idp_admin_user;

ALTER DEFAULT PRIVILEGES
    FOR ROLE idpserver
    IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO idp_admin_user;

-- Connection limit is unlimited (-1) by default
-- Connection pooling (e.g., HikariCP) should manage connection limits at application level

-- Verification query
SELECT
    rolname,
    rolsuper,
    rolbypassrls,
    rolconnlimit
FROM pg_roles
WHERE rolname = 'idp_admin_user';