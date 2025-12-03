# 統計データメンテナンスガイド

統計テーブル（`statistics_monthly`, `statistics_daily_users`, `statistics_monthly_users`）の運用・保守手順。

---

## 目次

1. [データ保持ポリシー](#データ保持ポリシー)
2. [pg_cron セットアップ](#pg_cron-セットアップ)
3. [クリーンアップジョブ設定](#クリーンアップジョブ設定)
4. [パーティショニング移行](#パーティショニング移行)（将来対応）
5. [pg_cron動作確認テスト](#pg_cron動作確認テスト)
6. [トラブルシューティング](#トラブルシューティング)
7. [監視クエリ](#監視クエリ)

---

## データ保持ポリシー

### 推奨設定

| テーブル | 保持期間 | 理由 |
|----------|----------|------|
| `statistics_monthly` | 24ヶ月 | 年次比較に必要 |
| `statistics_daily_users` | 2日 | リアルタイム重複チェック用のみ |
| `statistics_monthly_users` | 2ヶ月 | 当月・前月のMAU追跡用のみ |

### データ量見積もり

```
前提: 100テナント × 10,000 DAU × 50,000 MAU

statistics_daily_users:   100 × 10,000 × 2日 = 200万行
statistics_monthly_users: 100 × 50,000 × 2月 = 1,000万行
statistics_monthly:       100 × 24月 = 2,400行
```

---

## pg_cron セットアップ

### Docker環境（推奨）

idp-serverのDocker環境ではpg_cronがプリインストールされています。

```bash
# 拡張機能の有効化とクリーンアップジョブ登録
docker exec -i postgres-primary psql -U idpserver -d idpserver \
  < docker/postgresql/primary/setup-pg-cron.sql
```

これにより以下が自動設定されます：
- pg_cron拡張機能の作成
- 3つのクリーンアップジョブ登録（daily_users, monthly_users, statistics）

### 手動セットアップ（マネージドDB等）

AWS RDS、Azure Database等ではpg_cronが事前設定済みの場合があります。

#### 1. 拡張機能インストール確認

```sql
-- pg_cronが利用可能か確認
SELECT * FROM pg_available_extensions WHERE name = 'pg_cron';
```

#### 2. postgresql.conf 設定（必要な場合）

```conf
# postgresql.conf に追加
shared_preload_libraries = 'pg_cron'
cron.database_name = 'idpserver'
```

#### 3. PostgreSQL再起動（必要な場合）

```bash
# systemd環境
sudo systemctl restart postgresql
```

#### 4. 拡張機能の有効化

```sql
-- superuserで実行
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- 権限付与（アプリケーションユーザーにジョブ作成権限を与える場合）
GRANT USAGE ON SCHEMA cron TO idpserver;
```

#### 5. インストール確認

```sql
-- cron.job テーブルが存在することを確認
SELECT * FROM cron.job;
```

---

## クリーンアップジョブ設定

### クリーンアップ関数（既存）

DDL `V0_10_0__statistics.sql` に以下の関数が定義済み：

```sql
-- 古い月次統計を削除
cleanup_old_statistics(retention_months INTEGER)

-- 古い日次ユーザーを削除
cleanup_old_daily_users(retention_days INTEGER)

-- 古い月次ユーザーを削除
cleanup_old_monthly_users(retention_months INTEGER)
```

### ジョブ登録

```sql
-- 1. 日次クリーンアップ（毎日 AM 3:00 JST = 18:00 UTC）
SELECT cron.schedule(
    'cleanup-daily-users',
    '0 18 * * *',
    $$SELECT cleanup_old_daily_users(2)$$
);

-- 2. 月次クリーンアップ（毎月1日 AM 4:00 JST = 19:00 UTC）
SELECT cron.schedule(
    'cleanup-monthly-users',
    '0 19 1 * *',
    $$SELECT cleanup_old_monthly_users(2)$$
);

-- 3. 年次クリーンアップ（毎月1日 AM 4:30 JST）
SELECT cron.schedule(
    'cleanup-old-statistics',
    '30 19 1 * *',
    $$SELECT cleanup_old_statistics(24)$$
);
```

### ジョブ確認

```sql
-- 登録済みジョブ一覧
SELECT jobid, schedule, command, nodename, active
FROM cron.job;

-- 実行履歴
SELECT * FROM cron.job_run_details
ORDER BY start_time DESC
LIMIT 20;
```

### ジョブ管理

```sql
-- ジョブ無効化
UPDATE cron.job SET active = false WHERE jobname = 'cleanup-daily-users';

-- ジョブ有効化
UPDATE cron.job SET active = true WHERE jobname = 'cleanup-daily-users';

-- ジョブ削除
SELECT cron.unschedule('cleanup-daily-users');

-- 即座に実行（テスト用）
SELECT cleanup_old_daily_users(2);
```

---

## パーティショニング移行

### 移行判断基準

以下の場合にパーティショニングを検討：

- [ ] `statistics_daily_users` が **1億行超**
- [ ] DELETE処理が **10分以上**かかる
- [ ] クエリ性能が **許容範囲外**

### Step 1: パーティションテーブル作成

```sql
-- 1. 新パーティションテーブル作成
CREATE TABLE statistics_daily_users_new (
    tenant_id UUID NOT NULL,
    stat_date DATE NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, stat_date, user_id)
) PARTITION BY RANGE (stat_date);

-- 2. RLS設定
ALTER TABLE statistics_daily_users_new ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON statistics_daily_users_new
    USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE statistics_daily_users_new FORCE ROW LEVEL SECURITY;

-- 3. 初期パーティション作成（過去3ヶ月 + 来月）
DO $$
DECLARE
    m DATE;
BEGIN
    FOR m IN SELECT generate_series(
        DATE_TRUNC('month', CURRENT_DATE - INTERVAL '3 months'),
        DATE_TRUNC('month', CURRENT_DATE + INTERVAL '1 month'),
        '1 month'::INTERVAL
    )::DATE
    LOOP
        EXECUTE format(
            'CREATE TABLE statistics_daily_users_%s
             PARTITION OF statistics_daily_users_new
             FOR VALUES FROM (%L) TO (%L)',
            TO_CHAR(m, 'YYYY_MM'),
            m,
            m + INTERVAL '1 month'
        );
    END LOOP;
END $$;
```

### Step 2: データ移行

```sql
-- メンテナンスウィンドウ中に実行

-- 1. データ移行
INSERT INTO statistics_daily_users_new
SELECT * FROM statistics_daily_users;

-- 2. テーブル入れ替え
BEGIN;
ALTER TABLE statistics_daily_users RENAME TO statistics_daily_users_old;
ALTER TABLE statistics_daily_users_new RENAME TO statistics_daily_users;
COMMIT;

-- 3. 旧テーブル削除（動作確認後）
-- DROP TABLE statistics_daily_users_old;
```

### Step 3: パーティション管理関数

```sql
-- パーティション自動作成（来月分）
CREATE OR REPLACE FUNCTION create_daily_users_partition()
RETURNS void AS $$
DECLARE
    partition_date DATE;
    partition_name TEXT;
BEGIN
    partition_date := DATE_TRUNC('month', CURRENT_DATE + INTERVAL '1 month');
    partition_name := 'statistics_daily_users_' || TO_CHAR(partition_date, 'YYYY_MM');

    -- 既存チェック
    IF NOT EXISTS (
        SELECT 1 FROM pg_tables WHERE tablename = partition_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF statistics_daily_users
             FOR VALUES FROM (%L) TO (%L)',
            partition_name,
            partition_date,
            partition_date + INTERVAL '1 month'
        );
        RAISE NOTICE 'Created partition: %', partition_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- 古いパーティション削除
CREATE OR REPLACE FUNCTION drop_old_daily_users_partitions(retention_months INTEGER)
RETURNS INTEGER AS $$
DECLARE
    partition_record RECORD;
    dropped_count INTEGER := 0;
    cutoff_month TEXT;
BEGIN
    cutoff_month := TO_CHAR(
        CURRENT_DATE - (retention_months || ' months')::INTERVAL,
        'YYYY_MM'
    );

    FOR partition_record IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
          AND tablename ~ '^statistics_daily_users_[0-9]{4}_[0-9]{2}$'
          AND tablename < 'statistics_daily_users_' || cutoff_month
    LOOP
        EXECUTE 'DROP TABLE ' || quote_ident(partition_record.tablename);
        RAISE NOTICE 'Dropped partition: %', partition_record.tablename;
        dropped_count := dropped_count + 1;
    END LOOP;

    RETURN dropped_count;
END;
$$ LANGUAGE plpgsql;
```

### Step 4: pg_cronジョブ更新

```sql
-- 既存ジョブ削除
SELECT cron.unschedule('cleanup-daily-users');

-- パーティション管理ジョブ登録
SELECT cron.schedule(
    'create-daily-partition',
    '0 0 25 * *',  -- 毎月25日に来月分作成
    $$SELECT create_daily_users_partition()$$
);

SELECT cron.schedule(
    'drop-old-daily-partitions',
    '0 3 1 * *',  -- 毎月1日に古いパーティション削除
    $$SELECT drop_old_daily_users_partitions(2)$$
);
```

---

## pg_cron動作確認テスト

pg_cronが正常に動作しているか確認するためのテスト手順。

### Step 1: テスト用テーブルとジョブを作成

```sql
-- テスト用テーブル作成
CREATE TABLE IF NOT EXISTS cron_test_log (
    id SERIAL PRIMARY KEY,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 毎分実行するテストジョブを登録
SELECT cron.schedule('test-job', '* * * * *', $$INSERT INTO cron_test_log DEFAULT VALUES$$);

-- 登録確認
SELECT jobid, jobname, schedule, command FROM cron.job WHERE jobname = 'test-job';
```

### Step 2: 1分以上待機後、実行結果を確認

```sql
-- 実行結果確認
SELECT * FROM cron_test_log ORDER BY id DESC LIMIT 5;

-- ジョブ実行履歴
SELECT jobid, runid, status, return_message, start_time, end_time
FROM cron.job_run_details
WHERE jobid = (SELECT jobid FROM cron.job WHERE jobname = 'test-job')
ORDER BY start_time DESC LIMIT 5;
```

**期待結果**:
- `cron_test_log` に行が追加される
- `cron.job_run_details` の status が `succeeded`

### Step 3: テスト後のクリーンアップ

```bash
# Docker環境
docker exec postgres-primary psql -U idpserver -d idpserver -c "
SELECT cron.unschedule('test-job');
DROP TABLE IF EXISTS cron_test_log;
"
```

---

## トラブルシューティング

### pg_cronが動作しない

```sql
-- ログ確認
SELECT * FROM cron.job_run_details
WHERE status = 'failed'
ORDER BY start_time DESC;

-- PostgreSQLログも確認
-- /var/log/postgresql/postgresql-XX-main.log
```

### クリーンアップが遅い

```sql
-- 削除対象行数を事前確認
SELECT COUNT(*) FROM statistics_daily_users
WHERE stat_date < CURRENT_DATE - INTERVAL '2 days';

-- バッチ削除（大量データ時）
DO $$
DECLARE
    deleted INTEGER;
BEGIN
    LOOP
        DELETE FROM statistics_daily_users
        WHERE ctid IN (
            SELECT ctid FROM statistics_daily_users
            WHERE stat_date < CURRENT_DATE - INTERVAL '2 days'
            LIMIT 10000
        );
        GET DIAGNOSTICS deleted = ROW_COUNT;
        EXIT WHEN deleted = 0;
        COMMIT;
        PERFORM pg_sleep(0.1);  -- 負荷軽減
    END LOOP;
END $$;
```

### パーティション作成忘れ

```sql
-- エラー: no partition for value "2025-03-01"
-- 対応: 手動でパーティション作成
SELECT create_daily_users_partition();
```

---

## 監視クエリ

```sql
-- テーブルサイズ確認
SELECT
    relname as table_name,
    pg_size_pretty(pg_total_relation_size(relid)) as total_size,
    pg_size_pretty(pg_relation_size(relid)) as data_size,
    n_live_tup as row_count
FROM pg_stat_user_tables
WHERE relname LIKE 'statistics%'
ORDER BY pg_total_relation_size(relid) DESC;

-- 日次データ量推移
SELECT
    stat_date,
    COUNT(*) as user_count,
    COUNT(DISTINCT tenant_id) as tenant_count
FROM statistics_daily_users
GROUP BY stat_date
ORDER BY stat_date DESC
LIMIT 7;
```
