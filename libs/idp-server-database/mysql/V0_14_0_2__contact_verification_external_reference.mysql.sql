-- Issue #1416: support tenants that delegate code generation and verification to an external
-- service (authentication config with execution.function = "http_request").
--
-- In that mode idp-server never sees the code: the external service issues it, delivers it and
-- decides whether a submitted one is right. What idp-server keeps is the reference that identifies
-- the exchange, so verification can ask about it and support can join the two records.
ALTER TABLE contact_verification_challenge
    MODIFY COLUMN verification_code VARCHAR(16) NULL;

ALTER TABLE contact_verification_challenge
    ADD COLUMN external_reference JSON;
