/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- external_application_id index 追加 (本番運用向け)
--
-- CREATE INDEX CONCURRENTLY: 書き込みブロックなしで index を構築する。
-- 想定実行時間: 100万行で 5-30 分 (テーブルサイズ・I/O 次第)。
-- トランザクション内で実行不可なので psql から直接流す。
--
-- 進行状況の監視 (別 session):
--   SELECT phase, blocks_done, blocks_total,
--          round(100.0 * blocks_done / NULLIF(blocks_total, 0), 1) AS pct
--   FROM pg_stat_progress_create_index;
-- =====================================================

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_verification_external_application_id
    ON identity_verification_application (tenant_id, external_application_id);

-- 完了後: INVALID な index が残っていないことを確認 (見つかったら DROP CONCURRENTLY して再実行)
SELECT i.indexrelid::regclass AS index_name, i.indisvalid
FROM pg_index i
WHERE i.indrelid = 'identity_verification_application'::regclass
  AND NOT i.indisvalid;
