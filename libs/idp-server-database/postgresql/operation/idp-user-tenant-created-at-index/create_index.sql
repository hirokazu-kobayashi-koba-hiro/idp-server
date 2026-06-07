/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- idp_user (tenant_id, created_at DESC) index 追加 (本番運用向け)
--
-- CREATE INDEX CONCURRENTLY: 書き込みブロックなしで index を構築する。
-- 想定実行時間: 200 万行で 5-30 分 (テーブルサイズ・I/O 次第)。
-- トランザクション内で実行不可なので psql から直接流す。
--
-- 進行状況の監視 (別 session):
--   SELECT phase, blocks_done, blocks_total,
--          round(100.0 * blocks_done / NULLIF(blocks_total, 0), 1) AS pct
--   FROM pg_stat_progress_create_index;
-- =====================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_idp_user_tenant_created_at
    ON idp_user (tenant_id, created_at DESC);

-- 完了後: INVALID な index が残っていないことを確認
SELECT i.indexrelid::regclass AS index_name, i.indisvalid
FROM pg_index i
WHERE i.indrelid = 'idp_user'::regclass
  AND NOT i.indisvalid;
