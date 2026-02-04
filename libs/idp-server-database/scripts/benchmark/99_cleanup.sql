/*
 * テストデータ クリーンアップ
 * Issue #1227: ベンチマーク後の後処理
 */

\timing on
\set benchmark_tenant_id '11111111-1111-1111-1111-111111111111'

-- ================================================
-- 削除前の確認
-- ================================================
SELECT 'Before cleanup' AS status;
SELECT COUNT(*) AS benchmark_data_count
FROM security_event
WHERE tenant_id = :'benchmark_tenant_id'::uuid;

-- ================================================
-- テストデータ削除
-- ================================================
SELECT '=== Deleting benchmark test data ===' AS status;

-- バッチ削除（大量データなので分割）
DO $$
DECLARE
    deleted_count INT;
    total_deleted INT := 0;
    batch_size INT := 100000;
    benchmark_tenant_id UUID := '11111111-1111-1111-1111-111111111111';
BEGIN
    LOOP
        DELETE FROM security_event
        WHERE id IN (
            SELECT id FROM security_event
            WHERE tenant_id = benchmark_tenant_id
            LIMIT batch_size
        );

        GET DIAGNOSTICS deleted_count = ROW_COUNT;
        total_deleted := total_deleted + deleted_count;

        IF deleted_count > 0 THEN
            RAISE NOTICE 'Deleted % rows (total: %)', deleted_count, total_deleted;
        END IF;

        EXIT WHEN deleted_count = 0;
    END LOOP;

    RAISE NOTICE 'Total deleted: % rows', total_deleted;
END $$;

-- ================================================
-- インデックスは残す（本番マイグレーションで追加するため）
-- ================================================
SELECT 'Composite indexes preserved (will be added via migration V0_9_29)' AS note;

-- ================================================
-- 統計情報更新
-- ================================================
VACUUM ANALYZE security_event;

-- ================================================
-- 削除後の確認
-- ================================================
SELECT 'After cleanup' AS status;
SELECT COUNT(*) AS remaining_count
FROM security_event
WHERE tenant_id = :'benchmark_tenant_id'::uuid;

SELECT '=== Cleanup completed ===' AS status;
