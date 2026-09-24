/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- oauth_token (tenant_id, client_instance_id) 部分 index 追加 (本番運用向け)
--
-- CREATE INDEX CONCURRENTLY: 書き込みブロックなしで index を構築する。
-- 部分 index でも構築時は oauth_token を全件走査するため、所要時間は
-- テーブルサイズ次第。トランザクション内で実行不可なので psql から直接流す。
--
-- 進行状況の監視 (別 session):
--   SELECT phase, blocks_done, blocks_total,
--          round(100.0 * blocks_done / NULLIF(blocks_total, 0), 1) AS pct
--   FROM pg_stat_progress_create_index;
-- =====================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_oauth_token_client_instance
    ON oauth_token (tenant_id, client_instance_id)
    WHERE client_instance_id IS NOT NULL;

-- 完了後: INVALID な index が残っていないことを確認
SELECT i.indexrelid::regclass AS index_name, i.indisvalid
FROM pg_index i
WHERE i.indrelid = 'oauth_token'::regclass
  AND NOT i.indisvalid;
