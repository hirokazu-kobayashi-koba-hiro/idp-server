-- #1607: record, at approval time, the user attributes this verification result actually applied —
-- user_claims / custom_properties / user_status (and future additions such as credential) — collapsed
-- into a single JSONB column so each result row is self-describing for audit, without adding a column
-- per attribute kind.
--
-- Shape (top-level keys): { "user_claims": {...}, "custom_properties": {...}, "user_status": "..." }
--
-- Additive + nullable: existing rows keep this column NULL (backward compatible). ADD COLUMN
-- (nullable, no default) is catalog-only on PostgreSQL 11+ — momentary lock, no table rewrite.
-- No index on purpose: these values are recorded for audit/replay, not filtered by predicate, so we
-- avoid the write-blocking index build on an already-populated table.
ALTER TABLE identity_verification_result
    ADD COLUMN applied_user_claims JSONB;
