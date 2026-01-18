-- Migration: Permission Namespace and Wildcard Support
-- Version: 0.9.27
-- Description: Adds 'idp:' namespace prefix to control plane permissions and
--              introduces wildcard permission (idp:*) for administrator role.
--
-- This migration:
-- 1. Updates existing permission names to use 'idp:' prefix
-- 2. Adds wildcard permission 'idp:*' for each tenant
-- 3. Updates administrator role to use wildcard permission instead of individual permissions
--
-- Backward compatibility is maintained at the application level through PermissionMatcher.normalize()

-- Step 1: Update existing permission names to use 'idp:' prefix
-- Only update permissions that don't already have a namespace prefix
UPDATE permission
SET name = 'idp:' || name,
    updated_at = NOW()
WHERE name NOT LIKE '%:%'
   OR (name LIKE '%:%' AND name NOT LIKE 'idp:%' AND name NOT LIKE 'custom:%');

-- Handle legacy admin_user permissions (convert underscore to hyphen)
UPDATE permission
SET name = REPLACE(name, 'idp:admin_user:', 'idp:admin-user:'),
    updated_at = NOW()
WHERE name LIKE 'idp:admin_user:%';

-- Step 2: Add wildcard permission 'idp:*' for each tenant that doesn't have it
INSERT INTO permission (id, tenant_id, name, description, created_at, updated_at)
SELECT
    gen_random_uuid(),
    t.id,
    'idp:*',
    'All control plane permissions',
    NOW(),
    NOW()
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM permission p
    WHERE p.tenant_id = t.id AND p.name = 'idp:*'
);

-- Step 3: Update administrator role to use wildcard permission
-- First, find the wildcard permission ID and administrator role ID for each tenant
-- Then, add the wildcard permission to the administrator role if not already present
INSERT INTO role_permission (id, tenant_id, role_id, permission_id, created_at)
SELECT
    gen_random_uuid(),
    r.tenant_id,
    r.id,
    p.id,
    NOW()
FROM role r
JOIN permission p ON r.tenant_id = p.tenant_id AND p.name = 'idp:*'
WHERE r.name = 'administrator'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Step 4: Optionally remove individual permissions from administrator role
-- (keeping them won't hurt since wildcard covers everything, but cleanup is cleaner)
-- Uncomment below if you want to remove individual permissions:
--
-- DELETE FROM role_permission
-- WHERE role_id IN (SELECT id FROM role WHERE name = 'administrator')
--   AND permission_id IN (SELECT id FROM permission WHERE name != 'idp:*');
