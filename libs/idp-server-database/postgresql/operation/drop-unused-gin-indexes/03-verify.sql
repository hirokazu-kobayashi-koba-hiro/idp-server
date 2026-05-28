-- ================================================
-- 03. Verify: confirm GIN indexes are gone
-- ================================================
--
-- 削除後に以下を確認:
--   1. 親 partitioned index が存在しないこと
--   2. 子 partition の GIN index がすべて消えていること
--   3. テーブル本体の他 index (B-tree) は無事なこと
--   4. テーブル+index 総サイズが減ったこと
--   5. pg_partman が次に作る新 partition に GIN が付かないことの確認方法
-- ================================================


-- ------------------------------------------------
-- 1) 親 partitioned index の不在確認
-- ------------------------------------------------
\echo '=== 1) Parent partitioned index status ==='

SELECT
    CASE
        WHEN EXISTS (
            SELECT 1 FROM pg_class
            WHERE relname = 'idx_events_detail_jsonb' AND relkind = 'I'
        ) THEN 'STILL EXISTS - check 02-drop-parent-retry.sh result'
        ELSE 'OK: parent index dropped'
    END AS parent_index_status;


-- ------------------------------------------------
-- 2) 子 partition の GIN index 残存確認
-- ------------------------------------------------
\echo ''
\echo '=== 2) Remaining child GIN indexes (should be 0) ==='

SELECT
    pc.relname AS partition_table,
    c.relname AS remaining_child_index,
    pg_size_pretty(pg_relation_size(c.oid)) AS size
FROM pg_class c
JOIN pg_index idx ON idx.indexrelid = c.oid
JOIN pg_class pc ON pc.oid = idx.indrelid
JOIN pg_am am ON am.oid = c.relam
WHERE pc.relname LIKE 'security_event_p%'
  AND am.amname = 'gin'
  AND c.relname LIKE '%detail%'
ORDER BY pc.relname;


-- ------------------------------------------------
-- 3) security_event の残存 index (B-tree 等は無事か)
-- ------------------------------------------------
-- ※ partitioned table の index は pg_class から直接引く
--   (pg_stat_user_indexes は子 partition 単位なので、
--   親に対する一覧を見るには pg_index/pg_class を join する)
\echo ''
\echo '=== 3) Surviving partitioned indexes on security_event (B-tree etc.) ==='

SELECT
    ic.relname AS parent_index,
    am.amname AS index_type,
    pg_size_pretty(
        COALESCE((
            SELECT SUM(pg_relation_size(child.oid))
            FROM pg_inherits inh
            JOIN pg_class child ON child.oid = inh.inhrelid
            WHERE inh.inhparent = ic.oid
        ), 0)
    ) AS aggregated_size
FROM pg_index pi
JOIN pg_class pc ON pc.oid = pi.indrelid
JOIN pg_class ic ON ic.oid = pi.indexrelid
JOIN pg_am am ON am.oid = ic.relam
WHERE pc.relname = 'security_event'
  AND pc.relkind = 'p'
ORDER BY ic.relname;


-- ------------------------------------------------
-- 4) テーブル+index 総サイズ (削除前と比較)
-- ------------------------------------------------
-- ※ partitioned table の pg_total_relation_size は 0 を返すので、
--   子 partition を合算して算出する
\echo ''
\echo '=== 4) Current total size of security_event (aggregated across partitions) ==='

WITH partitions AS (
    SELECT inh.inhrelid AS oid
    FROM pg_inherits inh
    WHERE inh.inhparent = 'security_event'::regclass
)
SELECT
    pg_size_pretty(SUM(pg_total_relation_size(oid))) AS total_size,
    pg_size_pretty(SUM(pg_relation_size(oid))) AS table_only_size,
    pg_size_pretty(SUM(pg_total_relation_size(oid)) - SUM(pg_relation_size(oid))) AS indexes_size,
    COUNT(*) AS partition_count
FROM partitions;


-- ------------------------------------------------
-- 5) pg_partman 設定確認 + 次回 maintenance での挙動の事前確認
-- ------------------------------------------------
\echo ''
\echo '=== 5) pg_partman maintenance preview ==='

-- 次にできる partition がどうなるかは、親 partitioned index が無いことで自動判定される。
-- 親 index 一覧をリストアップして、GIN が含まれていないことを確認:

SELECT
    pi.indexrelid::regclass AS parent_index,
    am.amname AS index_type
FROM pg_index pi
JOIN pg_class pc ON pc.oid = pi.indrelid
JOIN pg_class ic ON ic.oid = pi.indexrelid
JOIN pg_am am ON am.oid = ic.relam
WHERE pc.relname = 'security_event'
  AND pc.relkind = 'p'  -- partitioned table
ORDER BY parent_index;

\echo ''
\echo 'If no GIN index appears above, new partitions created by pg_partman will not get GIN.'


-- ------------------------------------------------
-- 6) (任意) 直近 1 日の security_event INSERT 数 (削除後の書き込みコスト目安)
-- ------------------------------------------------
\echo ''
\echo '=== 6) Recent INSERT count (last 24h, sample) ==='

SELECT
    COUNT(*) AS inserts_last_24h
FROM security_event
WHERE created_at > now() - interval '24 hours';
