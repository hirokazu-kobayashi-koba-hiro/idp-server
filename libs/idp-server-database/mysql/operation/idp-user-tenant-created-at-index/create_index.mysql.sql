-- =====================================================
-- idp_user (tenant_id, created_at DESC) index 追加 (本番運用向け / MySQL)
--
-- ALGORITHM=INPLACE, LOCK=NONE で書き込みブロックなしの online build。
-- 想定実行時間: 200 万行で 5-30 分 (テーブルサイズ・I/O 次第)。
-- =====================================================

CREATE INDEX idx_idp_user_tenant_created_at
    ON idp_user (tenant_id, created_at DESC)
    ALGORITHM=INPLACE LOCK=NONE;
