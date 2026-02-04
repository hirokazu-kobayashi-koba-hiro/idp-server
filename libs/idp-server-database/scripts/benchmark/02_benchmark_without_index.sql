/*
 * ベンチマーク: 複合インデックスなし
 * Issue #1227: クエリパフォーマンス比較
 */

\timing on
\set benchmark_tenant_id '11111111-1111-1111-1111-111111111111'

-- ================================================
-- 準備: 複合インデックスが存在しないことを確認
-- ================================================
SELECT 'Current indexes on security_event' AS info;
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'security_event'
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
-- ベンチマーク 3: user_id 検索
-- ================================================
SELECT '=== Benchmark 3: user_id search ===' AS test;

-- user_idはランダムなので、存在するものを1つ取得
WITH sample_user AS (
    SELECT user_id FROM security_event
    WHERE tenant_id = :'benchmark_tenant_id'::uuid
    LIMIT 1
)
SELECT * FROM (
    EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
    SELECT se.*
    FROM security_event se, sample_user su
    WHERE se.tenant_id = :'benchmark_tenant_id'::uuid
      AND se.user_id = su.user_id
      AND se.created_at BETWEEN NOW() - interval '30 days' AND NOW()
    ORDER BY se.created_at DESC
    LIMIT 100
) AS explain_result;

-- ================================================
-- ベンチマーク 4: type 検索
-- ================================================
SELECT '=== Benchmark 4: type search ===' AS test;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM security_event
WHERE tenant_id = :'benchmark_tenant_id'::uuid
  AND type = 'login_success'
  AND created_at BETWEEN NOW() - interval '30 days' AND NOW()
ORDER BY created_at DESC
LIMIT 100;

-- ================================================
-- ベンチマーク 5: COUNT クエリ
-- ================================================
SELECT '=== Benchmark 5: COUNT query ===' AS test;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT COUNT(*)
FROM security_event
WHERE tenant_id = :'benchmark_tenant_id'::uuid
  AND external_user_id = 'heavy_user_001'
  AND created_at BETWEEN NOW() - interval '30 days' AND NOW();

-- ================================================
-- 結果サマリ
-- ================================================
SELECT '=== WITHOUT INDEX BENCHMARK COMPLETED ===' AS status;
SELECT 'Record the Execution Time values above, then run 03_create_composite_indexes.sql' AS next_step;
