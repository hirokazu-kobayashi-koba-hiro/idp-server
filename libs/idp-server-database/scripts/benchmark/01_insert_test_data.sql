/*
 * security_event テストデータ投入スクリプト
 * Issue #1227: 複合インデックスベンチマーク用
 *
 * データ量: 1000万件（10万件 × 100バッチ）
 * 所要時間: 約10-30分
 */

-- ================================================
-- 設定
-- ================================================
\set VERBOSITY verbose
\timing on

-- ベンチマーク用テナントID（ランダム生成）
-- 既存テナントを使う場合はここを変更
\set benchmark_tenant_id '''11111111-1111-1111-1111-111111111111'''

-- ================================================
-- 投入前の状態確認
-- ================================================
SELECT 'Before insert' AS status, COUNT(*) AS total_rows FROM security_event;

-- ================================================
-- インデックスを一時的に削除（高速化）
-- ※ 既存インデックスは残す（単一カラムインデックス）
-- ================================================
DROP INDEX IF EXISTS idx_events_tenant_external_user_created_at;
DROP INDEX IF EXISTS idx_events_tenant_client_created_at;
DROP INDEX IF EXISTS idx_events_tenant_user_created_at;
DROP INDEX IF EXISTS idx_events_tenant_type_created_at;

-- ================================================
-- バッチ投入
-- ================================================
DO $$
DECLARE
    batch_size INT := 100000;      -- 10万件/バッチ
    total_batches INT := 100;      -- 100バッチ = 1000万件
    i INT;
    benchmark_tenant_id UUID := '11111111-1111-1111-1111-111111111111';
    start_time TIMESTAMP;
    batch_start TIMESTAMP;
BEGIN
    start_time := clock_timestamp();
    RAISE NOTICE '=== Starting test data insertion ===';
    RAISE NOTICE 'Target: % rows (% batches x % rows)', total_batches * batch_size, total_batches, batch_size;
    RAISE NOTICE 'Tenant ID: %', benchmark_tenant_id;
    RAISE NOTICE '';

    FOR i IN 1..total_batches LOOP
        batch_start := clock_timestamp();

        INSERT INTO security_event (
            id, type, description, tenant_id, tenant_name,
            client_id, client_name, user_id, user_name, external_user_id,
            ip_address, user_agent, detail, created_at
        )
        SELECT
            gen_random_uuid(),
            -- イベントタイプを分散
            (ARRAY[
                'login_success', 'login_failure', 'logout',
                'token_issued', 'token_refreshed', 'token_revoked',
                'password_changed', 'mfa_enabled', 'consent_granted'
            ])[1 + (random() * 8)::int],
            'Benchmark test event',
            benchmark_tenant_id,
            'benchmark-tenant',
            -- クライアントIDを分散（100種類）
            'client-' || lpad((random() * 99)::int::text, 3, '0'),
            'Benchmark Client',
            gen_random_uuid(),
            'user-' || lpad((random() * 999)::int::text, 4, '0'),
            -- external_user_id: 10%を特定ユーザーに集中（ボトルネック再現）
            CASE
                WHEN random() < 0.10 THEN 'heavy_user_001'
                WHEN random() < 0.15 THEN 'heavy_user_002'
                WHEN random() < 0.20 THEN 'heavy_user_003'
                ELSE 'user-' || lpad((random() * 9999)::int::text, 5, '0')
            END,
            ('192.168.' || (random() * 255)::int || '.' || (random() * 255)::int)::inet,
            'Mozilla/5.0 (Benchmark Test) AppleWebKit/537.36',
            jsonb_build_object(
                'batch', i,
                'benchmark', true,
                'source', 'issue-1227-test'
            ),
            -- 過去60日間にランダム分散（パーティション跨ぎ）
            NOW() - (random() * interval '60 days')
        FROM generate_series(1, batch_size);

        -- 進捗表示（10バッチごと）
        IF i % 10 = 0 THEN
            RAISE NOTICE 'Batch %/% completed (% rows) - batch time: %s, total elapsed: %s',
                i, total_batches, i * batch_size,
                round(extract(epoch from clock_timestamp() - batch_start)::numeric, 2),
                round(extract(epoch from clock_timestamp() - start_time)::numeric, 2);
        END IF;
    END LOOP;

    RAISE NOTICE '';
    RAISE NOTICE '=== Insertion completed ===';
    RAISE NOTICE 'Total time: %s seconds', round(extract(epoch from clock_timestamp() - start_time)::numeric, 2);
END $$;

-- ================================================
-- 投入後の状態確認
-- ================================================
SELECT 'After insert' AS status, COUNT(*) AS total_rows FROM security_event;

-- データ分布確認
SELECT 'Data distribution by external_user_id' AS info;
SELECT
    external_user_id,
    COUNT(*) AS count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS percentage
FROM security_event
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'::uuid
GROUP BY external_user_id
ORDER BY count DESC
LIMIT 10;

-- ================================================
-- 統計情報更新
-- ================================================
ANALYZE security_event;

RAISE NOTICE 'Test data insertion completed. Run 02_benchmark_without_index.sql next.';
