-- ================================================
-- 99. Rollback: Recreate GIN index (emergency)
-- ================================================
--
-- 緊急時に GIN index を再作成する。
--
-- 警告:
--   ・本来このスクリプトは使うべきでない
--     (削除した理由 = 元々誰も使ってないため)
--   ・もし「やっぱり JSONB 検索が必要」になったら、まず
--     SQL を `detail ->> ?` から `detail @> ?::jsonb` に書き換える
--     ことを検討すべき (Issue #1550 と同じパターン)
--
-- 推奨 opclass:
--   ・jsonb_ops      : ?, ?|, ?&, @> をサポート、サイズ大
--   ・jsonb_path_ops : @> のみサポート、サイズ小 (推奨)
--
-- 注意:
--   ・CREATE INDEX CONCURRENTLY は partitioned table 親に対して使えない
--   ・親 index を作るには ACCESS EXCLUSIVE が必要 (短時間)
--   ・子 partition のサイズ次第で再構築に時間かかる (数分〜数十分)
--   ・再構築中もアプリの読み書きは継続可能 (新規 partition は ATTACH 時に index 作成)
--
-- 実行ユーザー: db_owner
-- ================================================

-- ※ 通常は推奨 opclass の jsonb_path_ops で再作成
-- ※ 元の定義 (jsonb_ops) に厳密に戻したい場合は WITH (gin_pending_list_limit = ...) の有無も確認

SET lock_timeout = '5s';

-- ON 親 partitioned table; 子 partition には自動継承される
CREATE INDEX idx_events_detail_jsonb
    ON security_event USING GIN (detail jsonb_path_ops);
-- ↑ 元の定義に戻したい場合は jsonb_path_ops を削除 (= デフォルト jsonb_ops)
-- CREATE INDEX idx_events_detail_jsonb ON security_event USING GIN (detail);


-- 再作成確認
\echo ''
\echo '=== Verification ==='

SELECT
    indexrelname,
    pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
WHERE indexrelname = 'idx_events_detail_jsonb';

SELECT
    pc.relname AS partition_table,
    c.relname AS child_index,
    pg_size_pretty(pg_relation_size(c.oid)) AS size
FROM pg_inherits i
JOIN pg_class c ON c.oid = i.inhrelid
JOIN pg_class p ON p.oid = i.inhparent
JOIN pg_index idx ON idx.indexrelid = c.oid
JOIN pg_class pc ON pc.oid = idx.indrelid
WHERE p.relname = 'idx_events_detail_jsonb'
ORDER BY pc.relname;
