-- migrate_statistics_events.sql
--
-- statistics_monthly.daily_metrics から statistics_events への移行スクリプト
--
-- 使用方法:
--   psql -f migrate_statistics_events.sql
--
-- 注意:
--   - このスクリプトは1回だけ実行してください
--   - 加算モードのため、複数回実行すると値が重複します
--   - アプリ稼働中に実行可能（ON CONFLICT で既存キーは加算）

-- 移行前の状態確認
SELECT 'Before migration' as phase;
SELECT 'statistics_monthly' as table_name, COUNT(*) as rows FROM statistics_monthly
UNION ALL
SELECT 'statistics_events', COUNT(*) FROM statistics_events;

-- データ移行実行
INSERT INTO statistics_events (tenant_id, stat_date, event_type, count, created_at, updated_at)
SELECT
    sm.tenant_id,
    days.key::date as stat_date,
    metrics.key as event_type,
    (metrics.value)::bigint as count,
    NOW() as created_at,
    NOW() as updated_at
FROM statistics_monthly sm
CROSS JOIN LATERAL jsonb_each(sm.daily_metrics) as days(key, day_data)
CROSS JOIN LATERAL jsonb_each_text(days.day_data) as metrics(key, value)
WHERE sm.daily_metrics IS NOT NULL
  AND sm.daily_metrics != '{}'::jsonb
  AND days.key ~ '^\d{4}-\d{2}-\d{2}$'  -- フル日付形式（YYYY-MM-DD）
  AND metrics.value IS NOT NULL
  AND metrics.value != 'null'
  AND metrics.value ~ '^\d+$'  -- 数値のみ
  AND (metrics.value)::bigint > 0
ON CONFLICT (tenant_id, stat_date, event_type)
DO UPDATE SET
    count = statistics_events.count + EXCLUDED.count,
    updated_at = NOW();

-- 移行後の状態確認
SELECT 'After migration' as phase;
SELECT 'statistics_monthly' as table_name, COUNT(*) as rows FROM statistics_monthly
UNION ALL
SELECT 'statistics_events', COUNT(*) FROM statistics_events;
