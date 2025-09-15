-- Organization-Level Row Level Security Policies
-- This file demonstrates PostgreSQL RLS policies for organization-aware multi-tenant access

-- =============================================================================
-- RLS Configuration Variables
-- =============================================================================
-- app.tenant_id: Target tenant for single-tenant operations
-- app.organization_id: Organization boundary for organization-scoped operations
-- app.target_tenant_id: Target tenant for cross-tenant admin operations
-- app.admin_tenant_id: Admin tenant performing cross-tenant operations
-- app.access_mode: Access mode determining which RLS policies apply
--   - 'single_tenant': Standard single tenant isolation
--   - 'organization_scoped': Organization + tenant isolation
--   - 'organization_admin': Organization-wide admin access

-- =============================================================================
-- Example Table: tenants
-- =============================================================================
CREATE TABLE tenants (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    organization_id VARCHAR(255), -- NULL for standalone tenants
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Enable RLS on tenants table
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;

-- Policy 1: Single Tenant Access
-- Standard tenant-based isolation (backward compatibility)
CREATE POLICY tenant_single_access ON tenants
    FOR ALL
    TO PUBLIC
    USING (
        current_setting('app.access_mode', true) = 'single_tenant'
        AND id = current_setting('app.tenant_id', true)
    );

-- Policy 2: Organization Scoped Access
-- Access within organization boundaries
CREATE POLICY tenant_organization_scoped ON tenants
    FOR ALL
    TO PUBLIC
    USING (
        current_setting('app.access_mode', true) = 'organization_scoped'
        AND organization_id = current_setting('app.organization_id', true)
        AND id = current_setting('app.tenant_id', true)
    );

-- Policy 3: Organization Admin Access
-- Cross-tenant access for organization administrators
CREATE POLICY tenant_organization_admin ON tenants
    FOR ALL
    TO PUBLIC
    USING (
        current_setting('app.access_mode', true) = 'organization_admin'
        AND organization_id = current_setting('app.organization_id', true)
        AND (
            -- Admin can access their own tenant
            id = current_setting('app.admin_tenant_id', true)
            -- Admin can access target tenant within same organization
            OR id = current_setting('app.target_tenant_id', true)
        )
    );

-- =============================================================================
-- Example Table: users
-- =============================================================================
CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255), -- Derived from tenant
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- Enable RLS on users table
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

-- Policy 1: Single Tenant User Access
CREATE POLICY user_single_access ON users
    FOR ALL
    TO PUBLIC
    USING (
        current_setting('app.access_mode', true) = 'single_tenant'
        AND tenant_id = current_setting('app.tenant_id', true)
    );

-- Policy 2: Organization Scoped User Access
CREATE POLICY user_organization_scoped ON users
    FOR ALL
    TO PUBLIC
    USING (
        current_setting('app.access_mode', true) = 'organization_scoped'
        AND organization_id = current_setting('app.organization_id', true)
        AND tenant_id = current_setting('app.tenant_id', true)
    );

-- Policy 3: Organization Admin User Access
CREATE POLICY user_organization_admin ON users
    FOR ALL
    TO PUBLIC
    USING (
        current_setting('app.access_mode', true) = 'organization_admin'
        AND organization_id = current_setting('app.organization_id', true)
        AND (
            -- Admin can access users from their tenant
            tenant_id = current_setting('app.admin_tenant_id', true)
            -- Admin can access users from target tenant
            OR tenant_id = current_setting('app.target_tenant_id', true)
        )
    );

-- =============================================================================
-- Example Table: clients (OAuth clients)
-- =============================================================================
CREATE TABLE clients (
    id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255), -- Derived from tenant
    client_name VARCHAR(255) NOT NULL,
    client_secret VARCHAR(255),
    redirect_uris TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- Enable RLS on clients table
ALTER TABLE clients ENABLE ROW LEVEL SECURITY;

-- Apply same RLS pattern as users table
CREATE POLICY client_single_access ON clients
    FOR ALL TO PUBLIC
    USING (
        current_setting('app.access_mode', true) = 'single_tenant'
        AND tenant_id = current_setting('app.tenant_id', true)
    );

CREATE POLICY client_organization_scoped ON clients
    FOR ALL TO PUBLIC
    USING (
        current_setting('app.access_mode', true) = 'organization_scoped'
        AND organization_id = current_setting('app.organization_id', true)
        AND tenant_id = current_setting('app.tenant_id', true)
    );

CREATE POLICY client_organization_admin ON clients
    FOR ALL TO PUBLIC
    USING (
        current_setting('app.access_mode', true) = 'organization_admin'
        AND organization_id = current_setting('app.organization_id', true)
        AND (
            tenant_id = current_setting('app.admin_tenant_id', true)
            OR tenant_id = current_setting('app.target_tenant_id', true)
        )
    );

-- =============================================================================
-- Helper Functions
-- =============================================================================

-- Function to validate organization-tenant relationship
CREATE OR REPLACE FUNCTION validate_organization_tenant_relationship(
    p_organization_id VARCHAR(255),
    p_tenant_id VARCHAR(255)
) RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM tenants
        WHERE id = p_tenant_id
        AND organization_id = p_organization_id
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to get tenant's organization
CREATE OR REPLACE FUNCTION get_tenant_organization(p_tenant_id VARCHAR(255))
RETURNS VARCHAR(255) AS $$
    SELECT organization_id FROM tenants WHERE id = p_tenant_id;
$$ LANGUAGE sql SECURITY DEFINER;

-- =============================================================================
-- Usage Examples
-- =============================================================================

-- Example 1: Single tenant operation
-- SET app.tenant_id = 'tenant-123';
-- SET app.access_mode = 'single_tenant';
-- SELECT * FROM users; -- Returns only users from tenant-123

-- Example 2: Organization scoped operation
-- SET app.organization_id = 'org-456';
-- SET app.tenant_id = 'tenant-123';
-- SET app.access_mode = 'organization_scoped';
-- SELECT * FROM users; -- Returns users from tenant-123 within org-456

-- Example 3: Cross-tenant admin operation
-- SET app.organization_id = 'org-456';
-- SET app.admin_tenant_id = 'admin-tenant-789';
-- SET app.target_tenant_id = 'tenant-123';
-- SET app.access_mode = 'organization_admin';
-- SELECT * FROM users; -- Returns users from both admin-tenant-789 and tenant-123 within org-456

-- =============================================================================
-- Migration Strategy
-- =============================================================================

-- Step 1: Add organization_id columns to existing tables
-- ALTER TABLE users ADD COLUMN organization_id VARCHAR(255);
-- ALTER TABLE clients ADD COLUMN organization_id VARCHAR(255);

-- Step 2: Populate organization_id from tenant relationships
-- UPDATE users SET organization_id = get_tenant_organization(tenant_id);
-- UPDATE clients SET organization_id = get_tenant_organization(tenant_id);

-- Step 3: Create indexes for performance
-- CREATE INDEX idx_users_organization_tenant ON users(organization_id, tenant_id);
-- CREATE INDEX idx_clients_organization_tenant ON clients(organization_id, tenant_id);

-- Step 4: Enable RLS policies gradually
-- - Start with single_tenant policies (existing behavior)
-- - Add organization_scoped policies for new features
-- - Add organization_admin policies for admin operations