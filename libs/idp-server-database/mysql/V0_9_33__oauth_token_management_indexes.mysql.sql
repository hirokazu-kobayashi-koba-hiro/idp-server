-- Indexes for Token Management API
-- Supports filtering by user_id, client_id, and expired token filtering
-- with access_token_expires_at for the default "active only" filter

CREATE INDEX idx_oauth_token_tenant_user
    ON oauth_token (tenant_id, user_id);

CREATE INDEX idx_oauth_token_tenant_client
    ON oauth_token (tenant_id, client_id);

-- Composite index for the most common query pattern:
-- WHERE tenant_id = ? AND access_token_expires_at > now()
-- ORDER BY access_token_created_at DESC
CREATE INDEX idx_oauth_token_tenant_expires_created
    ON oauth_token (tenant_id, access_token_expires_at, access_token_created_at DESC);
