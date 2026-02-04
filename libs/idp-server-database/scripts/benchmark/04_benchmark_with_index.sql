/*
 * ベンチマーク: 複合インデックスあり
 * Issue #1227: クエリパフォーマンス比較
 */

\timing on
\set benchmark_tenant_id '11111111-1111-1111-1111-111111111111'

-- ================================================
-- 準備: 複合インデックスが存在することを確認
-- ================================================
SELECT 'Current indexes on security_event' AS info;
SELECT indexname
FROM pg_indexes
WHERE tablename = 'security_event'
  AND indexname LIKE 'idx_events_tenant_%_created_at'
ORDER BY indexname;

-- ================================================
-- ベンチマーク 1: external_user_id 検索（ボトルネックケース）
-- ================================================
SELECT '=== Benchmark 1: external_user_id search (heavy_user_001) ===' AS test;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM security_event
WHERE tenant_id = :'benchmark_tenant_id'::uuid
  AND external_user_id = 'heavy_user_001'
  AND created_at BETWEEN NOW() - interval '30 days' AND NOW()
ORDER BY created_at DESC
LIMIT 100;

-- ================================================
-- ベンチマーク 2: client_id 検索
-- ================================================
SELECT '=== Benchmark 2: client_id search ===' AS test;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM security_event
WHERE tenant_id = :'benchmark_tenant_id'::uuid
  AND client_id = 'client-001'
  AND created_at BETWEEN NOW() - interval '30 days' AND NOW()
ORDER BY created_at DESC
LIMIT 100;

-- ================================================
-- ベンチマーク 3: type 検索
-- ================================================
SELECT '=== Benchmark 3: type search ===' AS test;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM security_event
WHERE tenant_id = :'benchmark_tenant_id'::uuid
  AND type = 'login_success'
  AND created_at BETWEEN NOW() - interval '30 days' AND NOW()
ORDER BY created_at DESC
LIMIT 100;

-- ================================================
-- ベンチマーク 4: COUNT クエリ
-- ================================================
SELECT '=== Benchmark 4: COUNT query ===' AS test;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT COUNT(*)
FROM security_event
WHERE tenant_id = :'benchmark_tenant_id'::uuid
  AND external_user_id = 'heavy_user_001'
  AND created_at BETWEEN NOW() - interval '30 days' AND NOW();

-- ================================================
-- 結果サマリ
-- ================================================
SELECT '=== WITH INDEX BENCHMARK COMPLETED ===' AS status;
SELECT 'Compare the Execution Time values with the previous benchmark.' AS summary;
SELECT 'Expected improvement: 100-500x faster for filtered queries.' AS expected;
SELECT 'Run 99_cleanup.sql to remove test data when done.' AS next_step;
