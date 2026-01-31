-- verify_statistics_events.sql
--
-- statistics_events 移行の検証スクリプト
--
-- 使用方法:
--   ./run_verify_statistics_events.sh

-- 1. テーブル状態確認
SELECT '=== Table Status ===' as section;
SELECT
    'statistics_events' as table_name,
    COUNT(*) as rows,
    COUNT(DISTINCT tenant_id) as tenants
FROM statistics_events;

-- 2. 合計値検証（MISMATCHがないことを確認）
SELECT '=== Validation Result ===' as section;
WITH original AS (
    SELECT
        sm.tenant_id,
        metrics.key as event_type,
        SUM((metrics.value)::bigint) as original_count
    FROM statistics_monthly sm
    CROSS JOIN LATERAL jsonb_each(sm.daily_metrics) as days(key, day_data)
    CROSS JOIN LATERAL jsonb_each_text(days.day_data) as metrics(key, value)
    WHERE metrics.value ~ '^\d+$'
    GROUP BY sm.tenant_id, metrics.key
),
migrated AS (
    SELECT tenant_id, event_type, SUM(count) as migrated_count
    FROM statistics_events
    GROUP BY tenant_id, event_type
)
SELECT
    COUNT(*) FILTER (WHERE o.original_count != m.migrated_count) as mismatch_count,
    COUNT(*) as total_count,
    CASE
        WHEN COUNT(*) FILTER (WHERE o.original_count != m.migrated_count) = 0 THEN 'OK - No mismatches'
        ELSE 'ERROR - Mismatches found'
    END as status
FROM original o
JOIN migrated m ON o.tenant_id = m.tenant_id AND o.event_type = m.event_type;

-- 3. MISMATCH詳細（問題がある場合のみ表示）
SELECT '=== Mismatch Details (if any) ===' as section;
WITH original AS (
    SELECT
        sm.tenant_id,
        metrics.key as event_type,
        SUM((metrics.value)::bigint) as original_count
    FROM statistics_monthly sm
    CROSS JOIN LATERAL jsonb_each(sm.daily_metrics) as days(key, day_data)
    CROSS JOIN LATERAL jsonb_each_text(days.day_data) as metrics(key, value)
    WHERE metrics.value ~ '^\d+$'
    GROUP BY sm.tenant_id, metrics.key
),
migrated AS (
    SELECT tenant_id, event_type, SUM(count) as migrated_count
    FROM statistics_events
    GROUP BY tenant_id, event_type
)
SELECT
    o.tenant_id::text,
    o.event_type,
    o.original_count,
    m.migrated_count,
    o.original_count - m.migrated_count as diff
FROM original o
JOIN migrated m ON o.tenant_id = m.tenant_id AND o.event_type = m.event_type
WHERE o.original_count != m.migrated_count
ORDER BY ABS(o.original_count - m.migrated_count) DESC
LIMIT 20;
