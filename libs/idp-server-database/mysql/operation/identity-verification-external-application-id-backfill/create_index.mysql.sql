-- =====================================================
-- external_application_id index 追加 (本番運用向け / MySQL)
--
-- ALGORITHM=INPLACE, LOCK=NONE で書き込みブロックなしの online build。
-- 想定実行時間: 100万行で 5-30 分 (テーブルサイズ・I/O 次第)。
-- =====================================================

CREATE UNIQUE INDEX idx_verification_external_application_id
    ON identity_verification_application (tenant_id, external_application_id)
    ALGORITHM=INPLACE LOCK=NONE;
