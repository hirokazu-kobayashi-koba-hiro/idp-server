/*
 * 複合インデックス作成
 * Issue #1227: security_event テーブルのクエリパフォーマンス改善
 */

\timing on

-- ================================================
-- 複合インデックス作成
-- ================================================
SELECT '=== Creating composite indexes ===' AS status;

-- 外部ユーザーID検索用
CREATE INDEX IF NOT EXISTS idx_events_tenant_external_user_created_at
    ON security_event (tenant_id, external_user_id, created_at DESC);

-- クライアントID検索用
CREATE INDEX IF NOT EXISTS idx_events_tenant_client_created_at
    ON security_event (tenant_id, client_id, created_at DESC);

-- ユーザーID検索用
CREATE INDEX IF NOT EXISTS idx_events_tenant_user_created_at
    ON security_event (tenant_id, user_id, created_at DESC);

-- イベントタイプ検索用
CREATE INDEX IF NOT EXISTS idx_events_tenant_type_created_at
    ON security_event (tenant_id, type, created_at DESC);

-- ================================================
-- 統計情報更新
-- ================================================
ANALYZE security_event;

-- ================================================
-- インデックス確認
-- ================================================
SELECT 'Indexes after creation' AS info;
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'security_event'
ORDER BY indexname;

-- インデックスサイズ確認
SELECT 'Index sizes' AS info;
SELECT
    indexrelname AS index_name,
    pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
WHERE relname = 'security_event'
ORDER BY pg_relation_size(indexrelid) DESC;

SELECT '=== Composite indexes created. Run 04_benchmark_with_index.sql ===' AS next_step;
