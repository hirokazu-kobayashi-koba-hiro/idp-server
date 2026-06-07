-- =====================================================
-- Issue #1460: idp_user に (tenant_id, created_at DESC) index 追加 (MySQL)
--
-- See postgresql/V0_10_0_4__idp_user_tenant_created_at_index.sql for context.
--
-- MySQL 8.0+ では CREATE INDEX のデフォルトが ALGORITHM=INPLACE で
-- 書き込みブロックなしの online build となるため、Flyway 適用でもほぼ
-- 影響なし。確実性を求める場合は事前に runbook の create_index.mysql.sql
-- (ALGORITHM=INPLACE LOCK=NONE 明示) を実行しておくこと。
-- `CREATE INDEX IF NOT EXISTS` なので本ファイルは no-op となり安全。
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_idp_user_tenant_created_at
    ON idp_user (tenant_id, created_at DESC);
