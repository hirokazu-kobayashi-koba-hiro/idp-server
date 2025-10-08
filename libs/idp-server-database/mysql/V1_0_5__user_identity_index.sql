-- Add unique constraint on (tenant_id, preferred_username) for tenant-scoped user identity
-- The value stored in preferred_username changes according to policy:
--   - USERNAME policy: preferred_username = normalized username
--   - EMAIL policy: preferred_username = normalized email
--   - PHONE policy: preferred_username = normalized phone
--   - EXTERNAL_USER_ID policy: preferred_username = normalized external_user_id

-- Change preferred_username to NOT NULL
ALTER TABLE idp_user MODIFY COLUMN preferred_username VARCHAR(255) NOT NULL;

-- Ensure uniqueness of preferred_username within tenant
CREATE UNIQUE INDEX idx_idp_user_tenant_preferred_username ON idp_user(tenant_id, preferred_username);

ALTER TABLE idp_user MODIFY COLUMN preferred_username VARCHAR(255) NOT NULL
  COMMENT 'Tenant-scoped unique user identifier. Stores normalized username/email/phone/external_user_id based on tenant unique key policy.';
