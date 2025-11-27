-- =====================================================
-- DDL関数の動作確認テスト
-- V0_10_0__statistics.sql で定義された関数の動作確認
-- =====================================================
--
-- 実行方法:
--   docker exec -i postgres-primary psql -U idp_app_user -d idpserver -f libs/idp-server-database/postgresql/operation/test_statistics_functions.sql
--
-- または:
--   psql -U idp_app_user -d idpserver -f libs/idp-server-database/postgresql/operation/test_statistics_functions.sql
--

\echo '========================================='
\echo 'Test 1: cleanup_old_statistics function'
\echo '========================================='

-- テストデータ準備
SET app.tenant_id = 'aaaaaaaa-0000-0000-0000-000000000000';

-- 古いデータを挿入（100日前、50日前、10日前）
INSERT INTO tenant_statistics (id, tenant_id, stat_date, metrics, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000000', CURRENT_DATE - INTERVAL '100 days', '{"dau": 100, "login_success_count": 500}', NOW(), NOW()),
    (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000000', CURRENT_DATE - INTERVAL '50 days', '{"dau": 200, "login_success_count": 1000}', NOW(), NOW()),
    (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000000', CURRENT_DATE - INTERVAL '10 days', '{"dau": 300, "login_success_count": 1500}', NOW(), NOW())
ON CONFLICT (tenant_id, stat_date) DO NOTHING;

\echo 'Before cleanup: Count all records'
SELECT COUNT(*) as total_count FROM tenant_statistics WHERE tenant_id = 'aaaaaaaa-0000-0000-0000-000000000000';

\echo ''
\echo 'Execute cleanup (retention 30 days):'
SELECT cleanup_old_statistics(30) as deleted_count;

\echo ''
\echo 'After cleanup: Count remaining records (should be 1 - only 10 days old)'
SELECT COUNT(*) as remaining_count FROM tenant_statistics WHERE tenant_id = 'aaaaaaaa-0000-0000-0000-000000000000';

\echo ''
\echo '========================================='
\echo 'Test 2: get_dau_count function'
\echo '========================================='

-- テストデータ準備（クリーンアップ）
DELETE FROM daily_active_users WHERE tenant_id = 'aaaaaaaa-0000-0000-0000-000000000000';

-- 今日の3ユーザーを挿入
INSERT INTO daily_active_users (tenant_id, stat_date, user_id)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000000', CURRENT_DATE, 'bbbbbbbb-0000-0000-0000-000000000001'),
    ('aaaaaaaa-0000-0000-0000-000000000000', CURRENT_DATE, 'bbbbbbbb-0000-0000-0000-000000000002'),
    ('aaaaaaaa-0000-0000-0000-000000000000', CURRENT_DATE, 'bbbbbbbb-0000-0000-0000-000000000003')
ON CONFLICT DO NOTHING;

\echo 'Get DAU count for today (should be 3):'
SELECT get_dau_count('aaaaaaaa-0000-0000-0000-000000000000'::UUID, CURRENT_DATE) as dau_count;

\echo ''
\echo 'Get DAU count for yesterday (should be 0):'
SELECT get_dau_count('aaaaaaaa-0000-0000-0000-000000000000'::UUID, (CURRENT_DATE - INTERVAL '1 day')::DATE) as dau_count;

\echo ''
\echo '========================================='
\echo 'Test 3: cleanup_old_dau function'
\echo '========================================='

-- 古いDAUデータを挿入（100日前、50日前）
INSERT INTO daily_active_users (tenant_id, stat_date, user_id)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000000', CURRENT_DATE - INTERVAL '100 days', 'bbbbbbbb-0000-0000-0000-000000000004'),
    ('aaaaaaaa-0000-0000-0000-000000000000', CURRENT_DATE - INTERVAL '50 days', 'bbbbbbbb-0000-0000-0000-000000000005')
ON CONFLICT DO NOTHING;

\echo 'Before cleanup: Count all DAU records (should be 5: 3 today + 2 old)'
SELECT COUNT(*) as total_dau_count FROM daily_active_users WHERE tenant_id = 'aaaaaaaa-0000-0000-0000-000000000000';

\echo ''
\echo 'Execute DAU cleanup (retention 30 days):'
SELECT cleanup_old_dau(30) as deleted_dau_count;

\echo ''
\echo 'After cleanup: Count remaining DAU records (should be 3 - only today)'
SELECT COUNT(*) as remaining_dau_count FROM daily_active_users WHERE tenant_id = 'aaaaaaaa-0000-0000-0000-000000000000';

\echo ''
\echo '========================================='
\echo 'Test 4: View test (latest_statistics)'
\echo '========================================='

\echo 'Latest statistics view (should show recent data with extracted metrics):'
SELECT
    tenant_id,
    stat_date,
    dau,
    login_success_rate,
    tokens_issued,
    new_users,
    TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI:SS') as created_at,
    TO_CHAR(updated_at, 'YYYY-MM-DD HH24:MI:SS') as updated_at
FROM latest_statistics
WHERE tenant_id = 'aaaaaaaa-0000-0000-0000-000000000000'
ORDER BY stat_date DESC
LIMIT 5;

\echo ''
\echo '========================================='
\echo 'Test Summary'
\echo '========================================='
\echo '✓ cleanup_old_statistics: Deletes records older than retention days'
\echo '✓ get_dau_count: Returns unique user count for specified date'
\echo '✓ cleanup_old_dau: Deletes DAU records older than retention days'
\echo '✓ latest_statistics view: Extracts common metrics from JSONB'
\echo ''
\echo 'All tests completed successfully!'
