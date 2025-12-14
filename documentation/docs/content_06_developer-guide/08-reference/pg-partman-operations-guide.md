# pg_partman + pg_cron パーティション運用ガイド

## 1. 概要

本ドキュメントでは、pg_partmanとpg_cronを使用したPostgreSQLパーティションの自動運用について説明します。

### 1.1 pg_partman とは

pg_partmanは、PostgreSQLのパーティション管理を自動化する拡張機能です。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         pg_partman の役割                                    │
│                                                                             │
│  ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐       │
│  │  パーティション  │     │  パーティション  │     │  保持ポリシー   │       │
│  │  自動作成       │     │  自動削除       │     │  管理          │       │
│  │  (premake)      │     │  (retention)    │     │                │       │
│  └─────────────────┘     └─────────────────┘     └─────────────────┘       │
│                                                                             │
│  手動でのCREATE TABLE / DROP TABLEが不要になる                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 pg_cron とは

pg_cronは、PostgreSQL内でジョブをスケジュール実行するための拡張機能です。

```sql
-- cron式でSQLを定期実行
SELECT cron.schedule('job-name', '0 * * * *', 'SELECT my_function();');
```

### 1.3 なぜこの組み合わせか

| 方式 | メリット | デメリット | AWS RDS対応 |
|:---|:---|:---|:---|
| 手動パーティション管理 | シンプル | 運用負荷高い | ✅ |
| pg_partman + pg_partman_bgw | 設定のみで自動化 | - | ❌ |
| **pg_partman + pg_cron** | 柔軟なスケジュール | - | **✅** |
| 外部スケジューラー（Lambda等） | AWS連携容易 | 複雑 | ✅ |

**AWS RDSではpg_partman_bgwが使用できないため、pg_cronとの組み合わせが推奨されます。**

---

## 2. 環境構成

### 2.1 ローカル開発環境（Docker）

```yaml
# docker-compose.yaml
postgres-primary:
  command: [
    "postgres",
    "-c", "shared_preload_libraries=pg_stat_statements,pg_cron",
    "-c", "cron.database_name=idpserver"
  ]
```

### 2.2 AWS RDS

```
# パラメータグループ設定
shared_preload_libraries = 'pg_cron'
cron.database_name = 'idpserver'
```

**注意**: パラメータグループ変更後はDBインスタンスの再起動が必要です。

---

## 3. セットアップ手順

### 3.1 拡張機能の有効化

```sql
-- pg_cronの有効化
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- pg_partmanの有効化
CREATE SCHEMA IF NOT EXISTS partman;
CREATE EXTENSION IF NOT EXISTS pg_partman WITH SCHEMA partman;
```

### 3.2 パーティションテーブルの作成

```sql
-- 例: 統計ユーザーテーブル（日別パーティション）
CREATE TABLE statistics_daily_users (
    tenant_id UUID NOT NULL,
    stat_date DATE NOT NULL,
    user_id UUID NOT NULL,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, stat_date, user_id)
) PARTITION BY RANGE (stat_date);

CREATE INDEX idx_daily_users_tenant_date
    ON statistics_daily_users(tenant_id, stat_date);
```

### 3.3 pg_partman設定

```sql
-- パーティション管理を設定
SELECT partman.create_parent(
    p_parent_table => 'public.statistics_daily_users',
    p_control => 'stat_date',           -- パーティションキー
    p_type => 'range',                  -- RANGE パーティション
    p_interval => '1 month',            -- 月別パーティション
    p_premake => 3,                     -- 3ヶ月先まで事前作成
    p_start_partition => '2024-01-01'   -- 開始日
);

-- 保持ポリシー設定
UPDATE partman.part_config
SET infinite_time_partitions = true,   -- 無限に新規作成
    retention = '6 months',             -- 6ヶ月保持
    retention_keep_table = false,       -- 古いパーティションを削除
    retention_keep_index = false        -- インデックスも削除
WHERE parent_table = 'public.statistics_daily_users';
```

### 3.4 pg_cronでメンテナンスジョブを設定

```sql
-- 毎時メンテナンス実行（推奨）
SELECT cron.schedule(
    'partman-maintenance',
    '0 * * * *',  -- 毎時0分
    $$CALL partman.run_maintenance_proc()$$
);

-- または毎日AM2時に実行
SELECT cron.schedule(
    'partman-daily-maintenance',
    '0 2 * * *',  -- 毎日AM2:00
    $$CALL partman.run_maintenance_proc()$$
);
```

---

## 4. 推奨設定

### 4.1 統計データテーブルの設定

| テーブル | パーティション間隔 | premake | 保持期間 |
|:---|:---|:---|:---|
| `statistics_daily_users` | 月別 | 3 | 6ヶ月 |
| `statistics_monthly_users` | 年別 | 2 | 3年 |
| `statistics_yearly_users` | 年別 | 2 | 5年 |

### 4.2 設定例

```sql
-- DAU（日次アクティブユーザー）テーブル
SELECT partman.create_parent(
    p_parent_table => 'public.statistics_daily_users',
    p_control => 'stat_date',
    p_type => 'range',
    p_interval => '1 month',
    p_premake => 3,
    p_start_partition => '2024-01-01'
);

UPDATE partman.part_config
SET retention = '6 months',
    retention_keep_table = false
WHERE parent_table = 'public.statistics_daily_users';

-- MAU（月次アクティブユーザー）テーブル
SELECT partman.create_parent(
    p_parent_table => 'public.statistics_monthly_users',
    p_control => 'stat_month',
    p_type => 'range',
    p_interval => '1 year',
    p_premake => 2,
    p_start_partition => '2024-01-01'
);

UPDATE partman.part_config
SET retention = '3 years',
    retention_keep_table = false
WHERE parent_table = 'public.statistics_monthly_users';

-- YAU（年次アクティブユーザー）テーブル
SELECT partman.create_parent(
    p_parent_table => 'public.statistics_yearly_users',
    p_control => 'stat_year',
    p_type => 'range',
    p_interval => '1 year',
    p_premake => 2,
    p_start_partition => '2024-01-01'
);

UPDATE partman.part_config
SET retention = '5 years',
    retention_keep_table = false
WHERE parent_table = 'public.statistics_yearly_users';
```

---

## 5. 運用監視

### 5.1 pg_partman設定確認

```sql
-- 設定一覧
SELECT
    parent_table,
    partition_interval,
    premake,
    retention,
    infinite_time_partitions as infinite
FROM partman.part_config
ORDER BY parent_table;
```

### 5.2 パーティション一覧確認

```sql
-- 特定テーブルのパーティション一覧
SELECT
    c.relname as partition,
    pg_size_pretty(pg_relation_size(c.oid)) as size
FROM pg_inherits i
JOIN pg_class c ON i.inhrelid = c.oid
JOIN pg_class p ON i.inhparent = p.oid
WHERE p.relname = 'statistics_daily_users'
ORDER BY c.relname;
```

### 5.3 pg_cronジョブ確認

```sql
-- 登録されているジョブ一覧
SELECT jobid, jobname, schedule, command, active
FROM cron.job
ORDER BY jobid;

-- ジョブ実行履歴
SELECT
    jobid,
    status,
    return_message,
    start_time,
    end_time
FROM cron.job_run_details
ORDER BY start_time DESC
LIMIT 10;
```

### 5.4 DEFAULTパーティション監視

```sql
-- DEFAULTパーティションにデータが溜まっていないか確認
SELECT
    p.relname as parent_table,
    c.relname as default_partition,
    (SELECT COUNT(*) FROM ONLY c.relname::regclass) as row_count
FROM pg_inherits i
JOIN pg_class c ON i.inhrelid = c.oid
JOIN pg_class p ON i.inhparent = p.oid
WHERE c.relname LIKE '%_default';
```

**注意**: DEFAULTパーティションにデータが存在する場合、パーティション作成が追いついていない可能性があります。

---

## 6. トラブルシューティング

### 6.1 パーティションが作成されない

**症状**: 新しいパーティションが自動作成されない

**確認手順**:
```sql
-- pg_cronジョブが有効か確認
SELECT jobid, jobname, active FROM cron.job
WHERE jobname LIKE '%partman%';

-- ジョブ実行履歴を確認
SELECT * FROM cron.job_run_details
WHERE jobid = (SELECT jobid FROM cron.job WHERE jobname = 'partman-maintenance')
ORDER BY start_time DESC
LIMIT 5;
```

**対処法**:
```sql
-- 手動でメンテナンス実行
CALL partman.run_maintenance_proc();

-- ジョブを再スケジュール
SELECT cron.unschedule('partman-maintenance');
SELECT cron.schedule('partman-maintenance', '0 * * * *',
    $$CALL partman.run_maintenance_proc()$$);
```

### 6.2 古いパーティションが削除されない

**症状**: retention設定を超えたパーティションが残っている

**確認手順**:
```sql
-- retention設定を確認
SELECT parent_table, retention, retention_keep_table
FROM partman.part_config;
```

**対処法**:
```sql
-- retention_keep_tableがtrueになっていないか確認
UPDATE partman.part_config
SET retention_keep_table = false
WHERE parent_table = 'public.your_table';

-- メンテナンス再実行
CALL partman.run_maintenance_proc();
```

### 6.3 DEFAULTパーティションにデータが溜まる（重要）

**症状**: DEFAULTパーティションに想定外のデータが存在

**原因**: premake設定が不足、またはデータの日付が想定範囲外

**⚠️ 重大な問題**: DEFAULTパーティションにデータが存在する状態で、そのデータの日付範囲に該当する新しいパーティションを作成しようとすると、pg_partmanのバージョンによっては**制約違反でエラー**になる可能性があります。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  DEFAULTパーティション問題の流れ                                             │
│                                                                             │
│  1. premake不足で将来日付のパーティションが存在しない                         │
│     例: premake=1 → 今日+1日後のパーティションしか存在しない                  │
│                                                                             │
│  2. 将来日付のデータがDEFAULTパーティションに格納される                       │
│     例: 5日後のデータ → DEFAULTパーティションに格納                          │
│                                                                             │
│  3. run_maintenance_proc()が新パーティション作成を試みる                     │
│                                                                             │
│  4. バージョンにより動作が異なる:                                            │
│     - 古いバージョン: DEFAULTパーティションのデータと競合 → エラー発生        │
│     - 新しいバージョン: エラーは発生しないが、データは移動されない            │
│                                                                             │
│  5. いずれの場合も、DEFAULTパーティションのデータは手動対処が必要             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 検証結果（実際の動作確認）

以下の検証を `default-partition-problem-test.sh` で実施しました：

```
検証環境: pg_partman 5.x

Phase 1: テストテーブル作成（premake=1）
  → 初期パーティション: default, p20251209, p20251210

Phase 2: 将来日付データ挿入（5〜7日後）
  → DEFAULTパーティションに4件格納
     event_date | count
     -----------+-------
     2025-12-14 |     2
     2025-12-15 |     1
     2025-12-16 |     1

Phase 3: check_default()で監視
  → DEFAULTにデータがあるテーブルを検出

Phase 4: partition_data_time()で再配置
  → 4件が適切なパーティションに移動
  → 新パーティション p20251214, p20251215, p20251216 が自動作成
  → DEFAULTパーティションが空に

Phase 5: メンテナンス再実行
  → premake=10の設定により、12/26までのパーティションが作成
```

**対処法**:

```sql
-- Step 1: DEFAULTパーティションのデータ範囲を確認
SELECT MIN(stat_date), MAX(stat_date), COUNT(*)
FROM statistics_daily_users_default;

-- Step 2: partition_data_time()でデータを適切なパーティションに再配置
-- この関数は必要なパーティションも同時に作成する
-- ⚠️ 注意: 大量データの場合、長時間のロック獲得が発生
SELECT partman.partition_data_time(
    p_parent_table := 'public.statistics_daily_users',
    p_batch_count := 100  -- バッチサイズを指定（移動するレコード数）
);

-- 戻り値: 移動したレコード数
-- 例: partition_data_time
--     ---------------------
--                        4

-- Step 3: DEFAULTパーティションが空になったことを確認
SELECT COUNT(*) FROM statistics_daily_users_default;
-- 結果: 0

-- Step 4: premakeを増やして再発防止
UPDATE partman.part_config
SET premake = 6  -- 6期間先まで事前作成
WHERE parent_table = 'public.statistics_daily_users';

-- Step 5: メンテナンス再実行
CALL partman.run_maintenance_proc();
```

**予防策（check_default関数）**:

```sql
-- DEFAULTパーティションにデータがある全テーブルをチェック
-- 引数なしで、pg_partman管理下の全テーブルを対象
SELECT * FROM partman.check_default();

-- 結果例:
--            default_table                  | count
-- -----------------------------------------+-------
--  public.statistics_daily_users_default   |     4
--  public.statistics_monthly_users_default |     0
-- (データがあるテーブルのみ表示される)

-- 監視クエリ例（アラート用）
SELECT COUNT(*) as tables_with_default_data
FROM partman.check_default()
WHERE count > 0;
-- 結果が0より大きい場合、対処が必要
```

**重要**: `check_default()`は引数なしで、pg_partman管理下の全テーブルのDEFAULTパーティションをチェックします。日次または週次の監視ジョブに組み込むことを推奨します。

### 6.4 run_maintenance_proc()がエラーで失敗

**症状**: pg_cronジョブが失敗ステータスになる

**確認手順**:
```sql
-- ジョブ実行履歴でエラーメッセージを確認
SELECT
    jobid,
    status,
    return_message,
    start_time,
    end_time
FROM cron.job_run_details
WHERE status = 'failed'
ORDER BY start_time DESC
LIMIT 10;
```

**主な原因と対処**:

| エラー内容 | 原因 | 対処法 |
|:---|:---|:---|
| 制約違反 | DEFAULTパーティションにデータ存在 | `partition_data_time()`でデータ再配置 |
| 権限エラー | 実行ユーザーの権限不足 | `GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA partman` |
| ロック待ち | 他トランザクションがロック保持 | 低負荷時間帯に再実行 |

---

## 7. 検証スクリプト

### 7.1 利用可能なスクリプト

```bash
scripts/pg_partman/
├── setup-pg_partman.sh                  # pg_partman拡張セットアップ
├── statistics-users-pg_partman.sh       # 統計テーブルのパーティション設定
├── verify-pg_partman.sh                 # 動作検証
├── partition-lifecycle-test.sh          # ライフサイクル検証
├── pgcron-autorun-test.sh               # pg_cron自動実行検証
├── default-partition-problem-test.sh    # DEFAULTパーティション問題検証
├── cleanup-pg_partman.sh                # クリーンアップ
└── README.md                            # 詳細説明
```

### 7.2 基本的な検証手順

```bash
# 1. pg_partmanセットアップ
./scripts/pg_partman/setup-pg_partman.sh

# 2. 統計テーブルのパーティション設定
./scripts/pg_partman/statistics-users-pg_partman.sh

# 3. 動作検証
./scripts/pg_partman/verify-pg_partman.sh

# 4. pg_cron自動実行検証
./scripts/pg_partman/pgcron-autorun-test.sh

# 5. クリーンアップ（必要に応じて）
./scripts/pg_partman/cleanup-pg_partman.sh
```

### 7.3 DEFAULTパーティション問題の検証

DEFAULTパーティション問題の発生と対処法を検証するスクリプト：

```bash
./scripts/pg_partman/default-partition-problem-test.sh
```

**検証内容**:

| Phase | 内容 | 確認ポイント |
|:---|:---|:---|
| 1 | テストテーブル作成（premake=1） | 最小限のパーティションで開始 |
| 2 | 将来日付データ挿入 | DEFAULTパーティションに格納される |
| 3 | check_default()で監視 | DEFAULTにデータがあるテーブルを検出 |
| 4 | メンテナンス実行 | バージョンによる動作差異を確認 |
| 5 | partition_data_time()で再配置 | データが適切なパーティションに移動 |
| 6 | 再度メンテナンス実行 | 正常にパーティションが作成される |

**実行例**:
```
Phase 2: 将来日付データ挿入
  → DEFAULTパーティションに4件格納

Phase 3: check_default()で監視
  → 結果:
           default_table           | count
  ---------------------------------+-------
   public.default_test_table_default |     4

Phase 5: partition_data_time()で再配置
  → partition_data_time
    ---------------------
                       4
  → DEFAULTパーティションが空に
```

### 7.4 ライフサイクル検証

パーティションの作成・削除ライフサイクルを検証するスクリプト：

```bash
./scripts/pg_partman/partition-lifecycle-test.sh
```

**検証内容**:
- premake設定による将来パーティションの事前作成
- retention設定による古いパーティションの自動削除
- データ分散の確認

---

## 8. AWS RDS固有の注意事項

### 8.1 pg_partman_bgwは使用不可

AWS RDSでは`pg_partman_bgw`（Background Worker）は`shared_preload_libraries`に追加できません。
代わりに`pg_cron`を使用してください。

```
ローカル環境:
  pg_partman_bgw（Background Worker）← 使用可能

AWS RDS:
  pg_cron + run_maintenance_proc() ← こちらを使用
```

### 8.2 パラメータグループの設定

```
# 必須設定
shared_preload_libraries = 'pg_cron'
cron.database_name = 'your_database_name'

# オプション設定
cron.log_run = on
cron.log_statement = on
```

### 8.3 権限設定

```sql
-- RDSマスターユーザーで実行
GRANT USAGE ON SCHEMA cron TO your_app_user;
GRANT USAGE ON SCHEMA partman TO your_app_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA partman TO your_app_user;
```

---

## 9. ベストプラクティス

### 9.1 メンテナンス実行タイミング

| スケジュール | ユースケース |
|:---|:---|
| 毎時（`0 * * * *`） | 日別パーティション、高頻度データ |
| 毎日AM2時（`0 2 * * *`） | 月別パーティション、標準的な運用 |
| 週1回（`0 2 * * 0`） | 年別パーティション、低頻度データ |

### 9.2 premake設定の目安

| パーティション間隔 | 推奨premake |
|:---|:---|
| 日別 | 7〜14（1〜2週間先） |
| 月別 | 2〜3（2〜3ヶ月先） |
| 年別 | 1〜2（1〜2年先） |

### 9.3 監視アラート設定

以下の状況でアラートを設定することを推奨します：

- DEFAULTパーティションにデータが存在
- pg_cronジョブの失敗
- パーティション数の急激な増加/減少

### 9.4 パーティションプルーニングの最適化

パーティションプルーニングを有効活用することで、クエリパフォーマンスが大幅に向上します。

```sql
-- PostgreSQL 14以降はデフォルトで有効
SHOW enable_partition_pruning;  -- 'on'であることを確認

-- 複数パーティションをJOINする場合に有効化推奨
SET enable_partitionwise_join = on;

-- 集約クエリ（SUM, COUNT等）のパフォーマンス向上
SET enable_partitionwise_aggregate = on;
```

**Aurora PostgreSQL パラメータグループ設定**:
```
enable_partition_pruning = 1          # デフォルト有効（v14+）
enable_partitionwise_join = 1         # 複数パーティションJOIN時に推奨
enable_partitionwise_aggregate = 1    # 集約クエリ時に推奨
```

### 9.5 メンテナンス実行の冪等性

`run_maintenance_proc()`は**冪等**です。複数回実行しても問題ありません。

- 実行時間: 通常数秒で完了
- ロック: 短時間のみ
- 安全性: 既に存在するパーティションは再作成されない

```sql
-- 安心して手動実行可能
CALL partman.run_maintenance_proc();
```

### 9.6 外部スケジューラーの選択肢

pg_cron以外のメンテナンス実行方式の比較：

| 方式 | 特徴 | 推奨ユースケース |
|:---|:---|:---|
| pg_cron | DB内で完結、シンプル | 標準的な運用 |
| EventBridge + Lambda | AWS連携、詳細な監視 | 複雑な監視要件 |
| Kubernetes CronJob | コンテナ環境、アラート連携 | EKS環境 |

**Kubernetes CronJobの例**（参考）:
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: partman-maintenance
spec:
  schedule: "0 2 * * 0"  # 週1回
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: psql
            image: postgres:15
            command:
            - psql
            - -h
            - $(DB_HOST)
            - -U
            - $(DB_USER)
            - -d
            - $(DB_NAME)
            - -c
            - "CALL partman.run_maintenance_proc();"
          restartPolicy: OnFailure
```

---

## 10. 運用チェックリスト

### 10.1 日次チェック項目

```sql
-- 1. pg_cronジョブ実行履歴確認
SELECT status, COUNT(*)
FROM cron.job_run_details
WHERE start_time > NOW() - INTERVAL '24 hours'
GROUP BY status;

-- 2. DEFAULTパーティションにデータがないか確認
SELECT table_name, row_count
FROM (
    SELECT
        c.relname as table_name,
        (SELECT COUNT(*) FROM pg_class WHERE relname = c.relname) as row_count
    FROM pg_class c
    WHERE c.relname LIKE '%_default'
) t
WHERE row_count > 0;
```

### 10.2 週次チェック項目

```sql
-- 1. パーティション数の確認
SELECT
    parent.relname as parent_table,
    COUNT(*) as partition_count
FROM pg_inherits i
JOIN pg_class parent ON i.inhparent = parent.oid
JOIN pg_class child ON i.inhrelid = child.oid
WHERE parent.relname IN ('statistics_daily_users', 'statistics_monthly_users', 'statistics_yearly_users')
GROUP BY parent.relname;

-- 2. pg_partman設定の確認
SELECT parent_table, premake, retention, infinite_time_partitions
FROM partman.part_config;

-- 3. パーティションサイズの確認
SELECT
    parent.relname as parent_table,
    pg_size_pretty(SUM(pg_relation_size(child.oid))) as total_size
FROM pg_inherits i
JOIN pg_class parent ON i.inhparent = parent.oid
JOIN pg_class child ON i.inhrelid = child.oid
GROUP BY parent.relname;
```

---

## 11. 関連ドキュメント

- [データベースパーティショニングガイド](database-partitioning-guide.md) - パーティショニングの基本概念
- [テナント統計管理](../../content_03_concepts/concept-23-tenant-statistics.md) - 統計データの設計

## 12. 参考資料

- [pg_partman GitHub](https://github.com/pgpartman/pg_partman)
- [pg_cron GitHub](https://github.com/citusdata/pg_cron)
- [AWS RDS PostgreSQL Partitions](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL_Partitions.html)
