-- ================================================
-- 00. Pre-check: Inspect state before dropping unused GIN indexes
-- ================================================
--
-- 削除前に以下を確認:
--   1. 対象 GIN index が本当に未使用か (idx_scan = 0)
--   2. 親 partitioned index の子インデックス一覧
--   3. 対象テーブル全体のサイズ + GIN index のサイズ
--   4. アクティブな長時間トランザクションがいないか
--   5. pg_partman の設定確認
--
-- 想定実行ユーザー: db_owner (もしくは superuser)
-- 影響: 読み取りのみ、何も変更しない
-- ================================================


-- ------------------------------------------------
-- 1) 親 partitioned index の存在確認 + 子の使用統計合計
-- ------------------------------------------------
-- ※ 親 partitioned index は pg_stat_user_indexes に直接出ない
--   (PostgreSQL は partition 単位でしか stats を取らない)。
--   子の idx_scan 合計が 0 なら未使用とみなせる。
\echo '=== 1) Parent partitioned index status + aggregated child usage ==='

SELECT
    n.nspname AS schema_name,
    c.relname AS parent_index,
    c.relkind AS relkind,  -- 'I' = partitioned index, 'i' = regular index
    pg_size_pretty(
        COALESCE((
            SELECT SUM(pg_relation_size(child.oid))
            FROM pg_inherits inh
            JOIN pg_class child ON child.oid = inh.inhrelid
            WHERE inh.inhparent = c.oid
        ), 0)
    ) AS aggregated_size,
    COALESCE((
        SELECT SUM(s.idx_scan)
        FROM pg_inherits inh
        JOIN pg_stat_user_indexes s ON s.indexrelid = inh.inhrelid
        WHERE inh.inhparent = c.oid
    ), 0) AS aggregated_idx_scan
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relname = 'idx_events_detail_jsonb';


-- ------------------------------------------------
-- 2) 子 partition の GIN index 一覧 + サイズ
-- ------------------------------------------------
\echo ''
\echo '=== 2) Child GIN indexes (per partition) ==='

SELECT
    pc.relname AS partition_table,
    c.relname AS child_index,
    pg_size_pretty(pg_relation_size(c.oid)) AS size,
    s.idx_scan,
    CASE
        WHEN pc.relname = 'security_event_p' || to_char(CURRENT_DATE, 'YYYYMMDD')
            THEN 'TODAY'
        WHEN pc.relname = 'security_event_default'
            THEN 'DEFAULT'
        WHEN pc.relname > 'security_event_p' || to_char(CURRENT_DATE, 'YYYYMMDD')
            THEN 'FUTURE'
        ELSE 'PAST'
    END AS partition_class
    -- すべて親 DROP のカスケードで一括削除される (個別 DROP は不可)
FROM pg_inherits i
JOIN pg_class c ON c.oid = i.inhrelid
JOIN pg_class p ON p.oid = i.inhparent
JOIN pg_index idx ON idx.indexrelid = c.oid
JOIN pg_class pc ON pc.oid = idx.indrelid
LEFT JOIN pg_stat_user_indexes s ON s.indexrelid = c.oid
WHERE p.relname = 'idx_events_detail_jsonb'
ORDER BY pc.relname;


-- ------------------------------------------------
-- 3) GIN index 総サイズ + テーブル全体のサイズ
-- ------------------------------------------------
\echo ''
\echo '=== 3) Total sizes ==='

WITH child_gin AS (
    SELECT c.oid AS idx_oid
    FROM pg_inherits i
    JOIN pg_class c ON c.oid = i.inhrelid
    JOIN pg_class p ON p.oid = i.inhparent
    WHERE p.relname = 'idx_events_detail_jsonb'
)
SELECT
    pg_size_pretty(SUM(pg_relation_size(idx_oid))) AS total_gin_size,
    COUNT(*) AS gin_index_count
FROM child_gin;

SELECT
    pg_size_pretty(pg_total_relation_size('security_event')) AS security_event_total_size;


-- ------------------------------------------------
-- 4) アクティブな長時間トランザクション
-- ------------------------------------------------
\echo ''
\echo '=== 4) Long-running transactions on security_event ==='

SELECT
    pid,
    state,
    wait_event_type,
    wait_event,
    now() - xact_start AS xact_duration,
    LEFT(query, 100) AS query_preview
FROM pg_stat_activity
WHERE state = 'active'
  AND xact_start IS NOT NULL
  AND (query ILIKE '%security_event%' OR query ILIKE '%idx_events_detail_jsonb%')
  AND pid != pg_backend_pid()
ORDER BY xact_start;


-- ------------------------------------------------
-- 5) pg_partman 設定 (親 GIN を消した後、新 partition に GIN が作られないことを確認するための事前情報)
-- ------------------------------------------------
\echo ''
\echo '=== 5) pg_partman config for security_event ==='

SELECT
    parent_table,
    partition_interval,
    premake,
    retention,
    retention_keep_table,
    retention_keep_index
FROM partman.part_config
WHERE parent_table = 'public.security_event';


-- ------------------------------------------------
-- 6) コード側で detail カラムへの GIN 演算子使用が無いことを念のため確認
-- ------------------------------------------------
\echo ''
\echo '=== 6) Reminder: verify code side ==='
\echo 'Run on host shell to confirm no GIN operators used:'
\echo '  grep -rn "detail @>\|detail ?\|detail ?|\|detail ?&" libs/'
\echo ''
\echo 'Current SQL pattern uses "detail ->> ? = ?" which does NOT use GIN.'
