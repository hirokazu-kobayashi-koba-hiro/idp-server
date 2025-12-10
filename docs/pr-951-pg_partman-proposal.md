# PR #951 pg_partman への置き換え提案

## 現状の課題

PR #951 では自前の関数でパーティション管理を実装していますが、以下の課題があります：

### 1. DEFAULTパーティション問題への対処がない

```
問題の流れ:
1. 何らかの理由でパーティション作成が遅延
2. 将来日付のデータがDEFAULTパーティションに格納
3. その後のパーティション作成で制約違反エラーの可能性
4. 対処方法が提供されていない
```

### 2. security_event_hook_results のパーティション管理が不完全

- V0_9_1 でパーティションテーブル化の記述があるが、V0_9_2 の管理関数では `security_event` のみ
- `drop_old_daily_partitions()` で `security_event_hook_results` が削除されない

### 3. 保持期間変更時の柔軟性

- 90日→180日に変更する場合、関数の修正とFlywayマイグレーションが必要
- pg_partman なら `UPDATE partman.part_config SET retention = '180 days'` のみ

---

## pg_partman への置き換え提案

### 変更概要

| 項目 | 現在のPR | 提案 |
|:---|:---|:---|
| パーティション管理 | 自前関数 | pg_partman |
| スケジューリング | pg_cron（自前関数呼び出し） | pg_cron（run_maintenance_proc呼び出し） |
| DEFAULTパーティション監視 | なし | check_default()で監視 |
| 設定変更 | Flywayマイグレーション必要 | part_configテーブル更新のみ |

### 実装案

#### V0_9_1: パーティションテーブル作成（変更なし）

既存のPRの実装をそのまま使用します。

```sql
-- 既存のPR #951 の V0_9_1 をそのまま使用
-- TEMP TABLEパターンでの移行は適切
```

#### V0_9_2: pg_partman による管理（置き換え）

```sql
-- ================================================
-- pg_partman によるパーティション管理
-- ================================================

-- 拡張機能の有効化
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE SCHEMA IF NOT EXISTS partman;
CREATE EXTENSION IF NOT EXISTS pg_partman WITH SCHEMA partman;

-- ================================================
-- security_event のpg_partman設定
-- ================================================

SELECT partman.create_parent(
    p_parent_table => 'public.security_event',
    p_control => 'created_at',
    p_type => 'range',
    p_interval => '1 day',
    p_premake => 90,
    p_start_partition => CURRENT_DATE::text
);

UPDATE partman.part_config
SET infinite_time_partitions = true,
    retention = '90 days',
    retention_keep_table = false,
    retention_keep_index = false
WHERE parent_table = 'public.security_event';

-- ================================================
-- security_event_hook_results のpg_partman設定
-- ================================================

SELECT partman.create_parent(
    p_parent_table => 'public.security_event_hook_results',
    p_control => 'created_at',
    p_type => 'range',
    p_interval => '1 day',
    p_premake => 90,
    p_start_partition => CURRENT_DATE::text
);

UPDATE partman.part_config
SET infinite_time_partitions = true,
    retention = '90 days',
    retention_keep_table = false,
    retention_keep_index = false
WHERE parent_table = 'public.security_event_hook_results';

-- ================================================
-- pg_cronでメンテナンスジョブをスケジュール
-- ================================================

-- 毎日午前2時（UTC）にメンテナンス実行
-- パーティション作成・削除を一括で実行
SELECT cron.schedule(
    'partman-maintenance',
    '0 2 * * *',
    $$CALL partman.run_maintenance_proc()$$
);

-- ================================================
-- コメント
-- ================================================

COMMENT ON EXTENSION pg_partman IS 'Automatic partition management for security_event and security_event_hook_results tables with 90-day retention';
```

---

## メリット

### 1. DEFAULTパーティション問題への対処

```sql
-- 監視
SELECT * FROM partman.check_default();

-- 問題発生時の対処
SELECT partman.partition_data_time(
    p_parent_table := 'public.security_event',
    p_batch_count := 1000
);
```

### 2. 保持期間変更が容易

```sql
-- 90日→180日に変更（マイグレーション不要）
UPDATE partman.part_config
SET retention = '180 days',
    premake = 180
WHERE parent_table = 'public.security_event';

-- 次回のrun_maintenance_proc()で反映
```

### 3. 一元管理

```sql
-- 全パーティション設定を一覧表示
SELECT
    parent_table,
    partition_interval,
    premake,
    retention
FROM partman.part_config;
```

### 4. security_event_hook_results も自動管理

- `run_maintenance_proc()` で両テーブルのパーティション作成・削除が一括実行
- 個別の関数管理が不要

---

## 運用監視

### 日次チェック

```sql
-- 1. pg_cronジョブ実行履歴
SELECT status, COUNT(*)
FROM cron.job_run_details
WHERE start_time > NOW() - INTERVAL '24 hours'
GROUP BY status;

-- 2. DEFAULTパーティションにデータがないか確認
SELECT * FROM partman.check_default();
```

### pg_partman設定確認

```sql
SELECT
    parent_table,
    partition_interval,
    premake,
    retention,
    infinite_time_partitions
FROM partman.part_config
ORDER BY parent_table;
```

---

## Docker/インフラ変更

### Dockerfile

```dockerfile
FROM postgres:15

# Install pg_cron and pg_partman extensions
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        postgresql-15-cron \
        postgresql-15-partman && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
```

### docker-compose.yaml

```yaml
postgres-primary:
  command: [
    "postgres",
    "-c", "shared_preload_libraries=pg_stat_statements,pg_cron",
    "-c", "cron.database_name=idpserver"
  ]
```

---

## マイグレーション戦略

### 新規環境

1. V0_9_1: パーティションテーブル作成（既存PRのまま）
2. V0_9_2: pg_partman設定（上記の新実装）

### 既存環境（PR #951 適用済み）

既にPR #951 が適用されている環境向けのマイグレーション：

```sql
-- V0_9_3: pg_partman移行

-- 1. 既存のpg_cronジョブを削除
SELECT cron.unschedule('create-next-day-partitions');
SELECT cron.unschedule('drop-old-daily-partitions');

-- 2. 既存の関数を削除
DROP FUNCTION IF EXISTS create_next_day_partitions();
DROP FUNCTION IF EXISTS drop_old_daily_partitions();

-- 3. pg_partman設定（上記のV0_9_2と同じ）
-- ...
```

---

## 参考資料

- [pg_partman GitHub](https://github.com/pgpartman/pg_partman)
- [pg-partman-operations-guide.md](../documentation/docs/content_06_developer-guide/08-reference/pg-partman-operations-guide.md)
- [DEFAULTパーティション問題の検証結果](../scripts/pg_partman/default-partition-problem-manual-test.md)
