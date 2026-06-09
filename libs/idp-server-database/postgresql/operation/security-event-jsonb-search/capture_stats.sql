/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- pg_stat_statements から security_event 関連クエリを抽出
--
-- 使い方:
--   1. pg_stat_statements_reset()              <- リセット
--   2. e2e perf test 実行                       <- 計測対象
--   3. psql -f capture_stats.sql                <- これ
--
-- 列の意味:
--   calls     : 呼び出し回数
--   total_ms  : 累計実行時間
--   mean_ms   : 1 回あたり実行時間 (lock 待ちも含む)
--   read_ms   : ディスク読み込み時間 (track_io_timing 有効時)
--   shared_hit: 共有バッファヒット (block 数)
--   shared_read: ディスク読み込み block 数 (キャッシュミス)
--   rows      : 返った行数の累計
-- =====================================================

\set ON_ERROR_STOP on

\echo '=== security_event 検索 / カウント クエリ統計 ==='
SELECT
    calls,
    total_exec_time::numeric(10,1) AS total_ms,
    mean_exec_time::numeric(10,2)  AS mean_ms,
    blk_read_time::numeric(10,1)   AS read_ms,
    shared_blks_hit                AS shared_hit,
    shared_blks_read               AS shared_read,
    rows,
    substring(translate(query, E'\n\r\t', '   ') from 1 for 100) AS q
FROM pg_stat_statements
WHERE query ILIKE '%FROM security_event%'
  AND query NOT ILIKE 'INSERT%'
  AND query NOT ILIKE 'CREATE%'
ORDER BY total_exec_time DESC
LIMIT 20;

\echo ''
\echo '=== detail JSONB アクセス (->> / @> / ? 演算子) を含むクエリのみ ==='
SELECT
    calls,
    total_exec_time::numeric(10,1) AS total_ms,
    mean_exec_time::numeric(10,2)  AS mean_ms,
    shared_blks_hit                AS shared_hit,
    shared_blks_read               AS shared_read,
    rows,
    substring(translate(query, E'\n\r\t', '   ') from 1 for 200) AS q
FROM pg_stat_statements
WHERE query ILIKE '%security_event%'
  AND (query ILIKE '%detail %->>%' OR query ILIKE '%detail %@>%' OR query ILIKE '%detail ?%')
ORDER BY total_exec_time DESC
LIMIT 10;

\echo ''
\echo '=== 参考: pg_stat_statements リセットしたい場合 ==='
\echo '  psql -c "SELECT pg_stat_statements_reset();"'
