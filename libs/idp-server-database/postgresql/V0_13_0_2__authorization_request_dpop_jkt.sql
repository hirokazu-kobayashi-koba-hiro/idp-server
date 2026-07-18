-- RFC 9449 Section 10: Authorization Code Binding to a DPoP Key
-- Add dpop_jkt column to authorization_request to bind the issued authorization
-- code to a specific DPoP key thumbprint (base64url-encoded SHA-256 JWK Thumbprint).
ALTER TABLE authorization_request ADD COLUMN IF NOT EXISTS dpop_jkt VARCHAR(255);
