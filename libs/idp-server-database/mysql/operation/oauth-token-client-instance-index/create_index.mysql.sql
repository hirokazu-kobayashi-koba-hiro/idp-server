-- =====================================================
-- oauth_token (tenant_id, client_instance_id) index 追加 (本番運用向け / MySQL)
--
-- ALGORITHM=INPLACE, LOCK=NONE で書き込みブロックなしの online build。
-- 所要時間は oauth_token のサイズ次第。
-- =====================================================

CREATE INDEX idx_oauth_token_client_instance
    ON oauth_token (tenant_id, client_instance_id)
    ALGORITHM=INPLACE LOCK=NONE;
