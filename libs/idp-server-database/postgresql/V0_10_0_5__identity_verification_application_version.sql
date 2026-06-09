-- Add optimistic-lock version column to identity_verification_application.
--
-- Used by the read/external-call/write split (Phase 3 of #1573) so the write
-- transaction can detect concurrent updates that happened while the external
-- HTTP call was in flight.

ALTER TABLE identity_verification_application
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
