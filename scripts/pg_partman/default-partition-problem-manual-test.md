# DEFAULTパーティション問題 手動検証手順

IntelliJ DBコンソールから実行するSQL集です。

```sql
-- ============================================================================
-- DEFAULTパーティション問題 手動検証
-- ============================================================================
-- 各Phaseを順番に実行してください。
-- ============================================================================

-- ============================================================================
-- Phase 1: テストテーブル作成（premake=1 で少なく設定）
-- ============================================================================

-- 既存テーブル削除
DROP TABLE IF EXISTS default_test_table CASCADE;

-- テストテーブル作成（日別パーティション）
CREATE TABLE default_test_table (
    id UUID DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid,
    event_date DATE NOT NULL,
    data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, event_date, id)
) PARTITION BY RANGE (event_date);

COMMENT ON TABLE default_test_table IS 'DEFAULT partition problem test table';

-- 既存のpg_partman設定を削除（エラーが出ても無視してOK）
DELETE FROM partman.part_config WHERE parent_table = 'public.default_test_table';

-- pg_partman設定（premake=1 で意図的に少なく設定）
SELECT partman.create_parent(
    p_parent_table => 'public.default_test_table',
    p_control => 'event_date',
    p_type => 'range',
    p_interval => '1 day',
    p_premake => 1,
    p_start_partition => CURRENT_DATE::text
);

-- 初期パーティション状態を確認
-- 期待結果: default, 今日, 明日 の3パーティションが作成される
SELECT c.relname as partition
FROM pg_inherits i
JOIN pg_class c ON i.inhrelid = c.oid
JOIN pg_class p ON i.inhparent = p.oid
WHERE p.relname = 'default_test_table'
ORDER BY c.relname;

-- ============================================================================
-- Phase 2: 将来日付のデータを挿入（DEFAULTに格納される）
-- ============================================================================

-- 5〜7日後のデータを挿入（パーティションが存在しないためDEFAULTへ）
INSERT INTO default_test_table (event_date, data)
VALUES
    (CURRENT_DATE + INTERVAL '5 days', 'future data 1'),
    (CURRENT_DATE + INTERVAL '5 days', 'future data 2'),
    (CURRENT_DATE + INTERVAL '6 days', 'future data 3'),
    (CURRENT_DATE + INTERVAL '7 days', 'future data 4');

-- DEFAULTパーティションの確認
-- 期待結果: 5〜7日後の日付に4件のデータが格納される
SELECT event_date, COUNT(*) as count
FROM default_test_table_default
GROUP BY event_date
ORDER BY event_date;

-- DEFAULTパーティションのデータ件数
-- 期待結果: 4件
SELECT COUNT(*) as default_count FROM default_test_table_default;

-- ============================================================================
-- Phase 3: check_default()でDEFAULTパーティションを監視
-- ============================================================================

-- pg_partman管理下の全テーブルのDEFAULTパーティションをチェック
-- ※引数なしで実行
-- 期待結果:
--            default_table           | count
-- -----------------------------------+-------
--  public.default_test_table_default |     4
SELECT * FROM partman.check_default();

-- DEFAULTパーティションの直接確認
SELECT
    'default_test_table' as parent_table,
    COUNT(*) as default_count
FROM default_test_table_default;

-- ============================================================================
-- Phase 4: premakeを増やしてメンテナンス実行
-- ============================================================================

-- premakeを1から10に変更
UPDATE partman.part_config
SET premake = 10
WHERE parent_table = 'public.default_test_table';

-- 設定変更を確認
SELECT parent_table, premake, retention
FROM partman.part_config
WHERE parent_table = 'public.default_test_table';

-- メンテナンス実行（エラーが発生する可能性あり）
-- pg_partmanのバージョンによって動作が異なる
-- 古いバージョン: DEFAULTパーティションのデータと競合してエラー発生
-- 新しいバージョン: エラーは発生しないが、DEFAULTのデータは移動されない
CALL partman.run_maintenance_proc();

-- 現在のパーティション状態を確認
SELECT c.relname as partition
FROM pg_inherits i
JOIN pg_class c ON i.inhrelid = c.oid
JOIN pg_class p ON i.inhparent = p.oid
WHERE p.relname = 'default_test_table'
ORDER BY c.relname;

-- ============================================================================
-- Phase 5: partition_data_time()でデータを再配置
-- ============================================================================

-- 再配置前のDEFAULTパーティションを確認
SELECT event_date, COUNT(*) as count
FROM default_test_table_default
GROUP BY event_date
ORDER BY event_date;

-- partition_data_time()でDEFAULTパーティションのデータを適切なパーティションに移動
-- この関数は必要なパーティションも同時に作成する
-- 期待結果: 移動したレコード数（4）が返される
--  partition_data_time
-- ---------------------
--                    4
SELECT partman.partition_data_time(
    p_parent_table := 'public.default_test_table',
    p_batch_count := 100
);

-- 再配置後のDEFAULTパーティションを確認（空になっているはず）
-- 期待結果: 0件
SELECT COUNT(*) as default_count FROM default_test_table_default;

-- 再配置後のパーティション状態を確認
-- 新しいパーティション（5〜7日後）が作成されているはず
SELECT c.relname as partition
FROM pg_inherits i
JOIN pg_class c ON i.inhrelid = c.oid
JOIN pg_class p ON i.inhparent = p.oid
WHERE p.relname = 'default_test_table'
ORDER BY c.relname;

-- 各パーティションのデータ件数を確認
-- 期待結果: default_count=0, total_count=4
SELECT
    (SELECT COUNT(*) FROM default_test_table_default) as default_count,
    (SELECT COUNT(*) FROM default_test_table) as total_count;

-- ============================================================================
-- Phase 6: 再度メンテナンス実行（正常動作を確認）
-- ============================================================================

-- メンテナンス再実行
CALL partman.run_maintenance_proc();

-- 最終パーティション状態を確認
-- premake=10の設定により、10日後までのパーティションが作成される
SELECT c.relname as partition
FROM pg_inherits i
JOIN pg_class c ON i.inhrelid = c.oid
JOIN pg_class p ON i.inhparent = p.oid
WHERE p.relname = 'default_test_table'
ORDER BY c.relname;

-- ============================================================================
-- クリーンアップ
-- ============================================================================

-- pg_partman設定削除
DELETE FROM partman.part_config WHERE parent_table = 'public.default_test_table';

-- テストテーブル削除
DROP TABLE IF EXISTS default_test_table CASCADE;

-- ============================================================================
-- 補足: 監視用クエリ（本番運用向け）
-- ============================================================================

-- DEFAULTパーティションにデータがあるテーブルを検出
-- アラート用: DEFAULTにデータがあるテーブル数を取得
SELECT COUNT(*) as tables_with_default_data
FROM partman.check_default()
WHERE count > 0;

-- pg_partman設定一覧
SELECT
    parent_table,
    partition_interval,
    premake,
    retention,
    infinite_time_partitions as infinite
FROM partman.part_config
ORDER BY parent_table;

-- pg_cronジョブ実行履歴
SELECT
    jobid,
    status,
    return_message,
    start_time,
    end_time
FROM cron.job_run_details
ORDER BY start_time DESC
LIMIT 10;

-- ============================================================================
-- 検証サマリー
-- ============================================================================
-- | Phase | 確認ポイント                    | 期待結果                      |
-- |-------|--------------------------------|------------------------------|
-- | 1     | premake=1でパーティション作成    | default + 2パーティション      |
-- | 2     | 将来日付データ挿入              | DEFAULTに4件格納              |
-- | 3     | check_default()で監視           | データありテーブルを検出        |
-- | 4     | メンテナンス実行                | バージョンにより動作異なる      |
-- | 5     | partition_data_time()実行       | 4件移動、DEFAULTが空に         |
-- | 6     | 再メンテナンス                  | 正常にパーティション作成        |
-- ============================================================================
-- 重要な教訓
-- ============================================================================
-- 予防: premakeを十分に設定（日別なら7〜14）
-- 監視: check_default()を定期実行
-- 対処: partition_data_time()でデータ再配置
-- 注意: 大量データの再配置は長時間ロックが発生する可能性
-- ============================================================================
```
