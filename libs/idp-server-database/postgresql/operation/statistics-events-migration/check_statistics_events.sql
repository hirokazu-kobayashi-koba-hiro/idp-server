-- check_statistics_events.sql
--
-- statistics_events 移行前の事前確認スクリプト
--
-- 使用方法:
--   psql -f check_statistics_events.sql

-- 1. テーブル存在確認
SELECT '=== Table Existence ===' as section;
SELECT
    'statistics_events' as table_name,
    EXISTS (
        SELECT FROM information_schema.tables
        WHERE table_schema = 'public'
        AND table_name = 'statistics_events'
    ) as exists;

-- 2. 現在のテーブル状態確認
SELECT '=== Current Table Status ===' as section;
SELECT 'statistics_monthly' as table_name, COUNT(*) as rows FROM statistics_monthly
UNION ALL
SELECT 'statistics_events', COUNT(*) FROM statistics_events;

-- 3. 移行対象（daily_metricsにデータがあるレコード）
SELECT '=== Migration Target ===' as section;
SELECT COUNT(*) as records_to_migrate
FROM statistics_monthly
WHERE daily_metrics IS NOT NULL
  AND daily_metrics != '{}'::jsonb;

-- 4. テナント別の移行対象件数
SELECT '=== Migration Target by Tenant ===' as section;
SELECT
    tenant_id::text,
    COUNT(*) as monthly_records,
    SUM(jsonb_array_length(jsonb_path_query_array(daily_metrics, '$.keyvalue()'))) as estimated_event_rows
FROM statistics_monthly
WHERE daily_metrics IS NOT NULL
  AND daily_metrics != '{}'::jsonb
GROUP BY tenant_id
ORDER BY estimated_event_rows DESC;
