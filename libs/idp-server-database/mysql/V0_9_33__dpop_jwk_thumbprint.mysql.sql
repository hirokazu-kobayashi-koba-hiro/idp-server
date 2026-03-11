-- RFC 9449: DPoP (Demonstrating Proof of Possession) support
-- Add jwk_thumbprint column to oauth_token for DPoP-bound access tokens
ALTER TABLE oauth_token ADD COLUMN jwk_thumbprint TEXT;
