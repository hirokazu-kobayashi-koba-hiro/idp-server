-- draft-ietf-oauth-attestation-based-client-auth Section 10.3: refresh tokens issued to a
-- Client Instance are bound to that instance, not only to the client.
--
-- A separate column from jwk_thumbprint on purpose. That one is the DPoP binding (RFC 9449) and
-- decides token_type, so a DPoP value there means the client sends DPoP proofs. An attestation
-- client sends a Client Attestation PoP instead, and its token stays Bearer.
--
-- Nullable and unindexed: only rows issued through attest_jwt_client_auth carry a value, and the
-- lookup is by primary key on the refresh path, never by this column.
ALTER TABLE oauth_token ADD COLUMN client_instance_thumbprint VARCHAR(64);

-- The registered Client Instance the token was issued to (registered_instance_key only). A refresh
-- has to come from that instance, not merely from one holding the same key: a deleted instance
-- frees its key, and a new registration of it must not inherit the old refresh tokens. Revoking or
-- deleting the instance deletes its tokens by this column; the index is V0_14_0_6.
ALTER TABLE oauth_token ADD COLUMN client_instance_id CHAR(36);
