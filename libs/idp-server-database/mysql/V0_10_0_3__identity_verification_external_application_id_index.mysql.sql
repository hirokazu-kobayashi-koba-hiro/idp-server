-- =====================================================
-- external_application_id index 追加 (MySQL)
--
-- MySQL 8.0+ では CREATE INDEX のデフォルトが ALGORITHM=INPLACE で
-- 書き込みブロックなしの online build となるため、Flyway 適用でもほぼ
-- 影響なし。確実性を求める場合は事前に runbook の create_index.mysql.sql
-- (ALGORITHM=INPLACE LOCK=NONE 明示) を実行しておくこと。
-- `CREATE INDEX IF NOT EXISTS` なので本ファイルは no-op となり安全。
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_verification_external_application_id
    ON identity_verification_application (tenant_id, external_application_id);
