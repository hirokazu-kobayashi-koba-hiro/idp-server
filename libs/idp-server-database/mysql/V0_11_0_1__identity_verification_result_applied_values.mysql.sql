-- #1607: record, at approval time, the user attributes this verification result actually applied —
-- user_claims / custom_properties / user_status (and future additions such as credential) — collapsed
-- into a single JSON column so each result row is self-describing for audit, without adding a column
-- per attribute kind. See the postgresql counterpart.
--
-- Shape (top-level keys): { "user_claims": {...}, "custom_properties": {...}, "user_status": "..." }
--
-- Additive + nullable: existing rows keep this column NULL (backward compatible). ADD COLUMN is
-- INSTANT (metadata-only) on MySQL 8.0.12+. No index (audit/replay record, not a query predicate).
ALTER TABLE identity_verification_result
    ADD COLUMN applied_user_claims JSON;
