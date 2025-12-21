# PostgreSQL パーティショニングガイド

このドキュメントでは、PostgreSQLのテーブルパーティショニングの設計と運用を解説します。

---

## 目次

1. [パーティショニングの概要](#1-パーティショニングの概要)
2. [パーティショニング方式](#2-パーティショニング方式)
3. [宣言的パーティショニング](#3-宣言的パーティショニング)
4. [パーティション管理](#4-パーティション管理)
5. [パフォーマンス最適化](#5-パフォーマンス最適化)
6. [運用とメンテナンス](#6-運用とメンテナンス)
7. [実践パターン](#7-実践パターン)

---

## 1. パーティショニングの概要

### 1.1 パーティショニングとは

```
┌──────────────────────────────────────────────────────────────┐
│                  パーティショニングとは                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  大きなテーブルを複数の小さな部分（パーティション）に        │
│  分割する技術                                                │
│                                                              │
│  【非パーティションテーブル】                                │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                    orders (10億行)                      │ │
│  │  全データが1つのテーブルに格納                          │ │
│  └────────────────────────────────────────────────────────┘ │
│                          ↓                                   │
│  【パーティションテーブル】                                  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    orders (親テーブル)                 │  │
│  └───────────────────────────────────────────────────────┘  │
│            │              │              │                   │
│            ▼              ▼              ▼                   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐        │
│  │ orders_2023  │ │ orders_2024  │ │ orders_2025  │ ...    │
│  │  (3億行)     │ │  (4億行)     │ │  (3億行)     │        │
│  └──────────────┘ └──────────────┘ └──────────────┘        │
│                                                              │
│  アプリケーションからは1つのテーブルに見える                │
│  内部的には複数のパーティションに分散                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 パーティショニングのメリット

```
┌──────────────────────────────────────────────────────────────┐
│                パーティショニングのメリット                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【パフォーマンス向上】                                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ パーティションプルーニング                            │   │
│  │ - クエリ条件に該当しないパーティションをスキップ      │   │
│  │ - 例: WHERE created_at >= '2024-01-01'               │   │
│  │   → 2023年以前のパーティションは読まない             │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【メンテナンス効率化】                                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ - VACUUM/ANALYZEがパーティション単位で実行可能        │   │
│  │ - 古いデータのDROPが高速 (DELETE不要)                 │   │
│  │ - インデックス再構築がパーティション単位              │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【データ管理】                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ - 古いデータのアーカイブが容易                        │   │
│  │ - パーティション単位でテーブルスペースを分離可能      │   │
│  │ - データ保持ポリシーの実装が容易                      │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 1.3 パーティショニングが有効なケース

```
┌──────────────────────────────────────────────────────────────┐
│             パーティショニングが有効なケース                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【推奨】                                                    │
│  ✅ テーブルサイズが数十GB以上                              │
│  ✅ 時系列データ (ログ、イベント、トランザクション)          │
│  ✅ 定期的に古いデータを削除/アーカイブする                  │
│  ✅ 特定の条件でのクエリが大部分                            │
│  ✅ テナント別のデータ分離                                   │
│                                                              │
│  【非推奨】                                                  │
│  ❌ テーブルサイズが小さい (数GB以下)                       │
│  ❌ パーティションキーでのフィルタがほとんどない             │
│  ❌ 全パーティションを常にスキャンするクエリが多い           │
│  ❌ トランザクションが複数パーティションにまたがる           │
│                                                              │
│  【目安】                                                    │
│  - 数千万行以上で検討開始                                    │
│  - 1億行以上で強く推奨                                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. パーティショニング方式

### 2.1 方式の比較

```
┌──────────────────────────────────────────────────────────────┐
│                   パーティショニング方式                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【RANGE パーティショニング】                                │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 値の範囲で分割                                        │   │
│  │ 用途: 日付、ID範囲など                                │   │
│  │                                                       │   │
│  │ orders_2023: created_at >= '2023-01-01' AND < '2024-01-01'│
│  │ orders_2024: created_at >= '2024-01-01' AND < '2025-01-01'│
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【LIST パーティショニング】                                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 特定の値のリストで分割                                │   │
│  │ 用途: カテゴリ、リージョン、テナントなど              │   │
│  │                                                       │   │
│  │ orders_jp: country = 'JP'                            │   │
│  │ orders_us: country = 'US'                            │   │
│  │ orders_eu: country IN ('DE', 'FR', 'GB')             │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【HASH パーティショニング】                                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ハッシュ値で均等に分割                                │   │
│  │ 用途: 均等分散が必要な場合                            │   │
│  │                                                       │   │
│  │ orders_0: hash(user_id) % 4 = 0                      │   │
│  │ orders_1: hash(user_id) % 4 = 1                      │   │
│  │ orders_2: hash(user_id) % 4 = 2                      │   │
│  │ orders_3: hash(user_id) % 4 = 3                      │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 複合パーティショニング

```
┌──────────────────────────────────────────────────────────────┐
│                   複合パーティショニング                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  複数のキーで階層的にパーティショニング                      │
│                                                              │
│                      orders                                  │
│                         │                                    │
│         ┌───────────────┼───────────────┐                   │
│         │               │               │                    │
│         ▼               ▼               ▼                    │
│  orders_2024_q1   orders_2024_q2   orders_2024_q3           │
│  (RANGE by date)                                             │
│         │                                                    │
│    ┌────┴────┐                                              │
│    ▼         ▼                                               │
│ orders_    orders_                                           │
│ 2024_q1_jp 2024_q1_us                                        │
│ (LIST by country)                                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. 宣言的パーティショニング

### 3.1 RANGEパーティション作成

```sql
-- 親テーブルの作成
CREATE TABLE orders (
    id BIGSERIAL,
    user_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    total_amount NUMERIC(10, 2),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY RANGE (order_date);

-- 月次パーティションの作成
CREATE TABLE orders_2024_01 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE orders_2024_02 PARTITION OF orders
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

CREATE TABLE orders_2024_03 PARTITION OF orders
    FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');

-- デフォルトパーティション (範囲外のデータ用)
CREATE TABLE orders_default PARTITION OF orders DEFAULT;

-- インデックスの作成 (親テーブルに作成すると全パーティションに適用)
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_created_at ON orders (created_at);
```

### 3.2 LISTパーティション作成

```sql
-- 親テーブルの作成
CREATE TABLE customers (
    id BIGSERIAL,
    name VARCHAR(100) NOT NULL,
    region VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY LIST (region);

-- リージョン別パーティション
CREATE TABLE customers_asia PARTITION OF customers
    FOR VALUES IN ('JP', 'KR', 'CN', 'TW', 'SG');

CREATE TABLE customers_americas PARTITION OF customers
    FOR VALUES IN ('US', 'CA', 'BR', 'MX');

CREATE TABLE customers_europe PARTITION OF customers
    FOR VALUES IN ('GB', 'DE', 'FR', 'IT', 'ES');

CREATE TABLE customers_default PARTITION OF customers DEFAULT;
```

### 3.3 HASHパーティション作成

```sql
-- 親テーブルの作成
CREATE TABLE user_activities (
    id BIGSERIAL,
    user_id BIGINT NOT NULL,
    activity_type VARCHAR(50),
    activity_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY HASH (user_id);

-- 4つのハッシュパーティション
CREATE TABLE user_activities_0 PARTITION OF user_activities
    FOR VALUES WITH (MODULUS 4, REMAINDER 0);

CREATE TABLE user_activities_1 PARTITION OF user_activities
    FOR VALUES WITH (MODULUS 4, REMAINDER 1);

CREATE TABLE user_activities_2 PARTITION OF user_activities
    FOR VALUES WITH (MODULUS 4, REMAINDER 2);

CREATE TABLE user_activities_3 PARTITION OF user_activities
    FOR VALUES WITH (MODULUS 4, REMAINDER 3);
```

### 3.4 複合パーティション作成

```sql
-- 親テーブル (年月でRANGE)
CREATE TABLE events (
    id BIGSERIAL,
    event_type VARCHAR(50) NOT NULL,
    event_date DATE NOT NULL,
    data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY RANGE (event_date);

-- 月次パーティション (さらにイベントタイプでLIST)
CREATE TABLE events_2024_01 PARTITION OF events
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01')
    PARTITION BY LIST (event_type);

-- サブパーティション
CREATE TABLE events_2024_01_click PARTITION OF events_2024_01
    FOR VALUES IN ('click', 'impression');

CREATE TABLE events_2024_01_conversion PARTITION OF events_2024_01
    FOR VALUES IN ('purchase', 'signup');

CREATE TABLE events_2024_01_default PARTITION OF events_2024_01 DEFAULT;
```

### 3.5 パーティションキーの制約

```
┌──────────────────────────────────────────────────────────────┐
│                 パーティションキーの制約                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【主キー・ユニーク制約】                                    │
│  パーティションキーを含む必要がある                          │
│                                                              │
│  ❌ 不可                                                     │
│  CREATE TABLE orders (...) PARTITION BY RANGE (order_date); │
│  ALTER TABLE orders ADD PRIMARY KEY (id);                   │
│                                                              │
│  ✅ 可能                                                     │
│  ALTER TABLE orders ADD PRIMARY KEY (id, order_date);       │
│                                                              │
│  【外部キー】                                                │
│  - 親テーブルへの外部キーは可能                              │
│  - パーティションテーブルを参照する外部キーは不可            │
│    (PostgreSQL 11以降で制限緩和)                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. パーティション管理

### 4.1 パーティションの追加

```sql
-- 新しいパーティションを追加
CREATE TABLE orders_2024_04 PARTITION OF orders
    FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');

-- テーブルスペースを指定して追加
CREATE TABLE orders_2024_05 PARTITION OF orders
    FOR VALUES FROM ('2024-05-01') TO ('2024-06-01')
    TABLESPACE fast_storage;
```

### 4.2 パーティションのデタッチ

```sql
-- パーティションをデタッチ (データは保持)
ALTER TABLE orders DETACH PARTITION orders_2023_01;

-- デタッチしたテーブルは通常のテーブルとして操作可能
SELECT count(*) FROM orders_2023_01;

-- アーカイブ用テーブルスペースに移動
ALTER TABLE orders_2023_01 SET TABLESPACE archive_storage;

-- または削除
DROP TABLE orders_2023_01;

-- CONCURRENTLY オプション (PostgreSQL 14+)
-- 長時間ロックを回避
ALTER TABLE orders DETACH PARTITION orders_2023_01 CONCURRENTLY;
```

### 4.3 既存テーブルのアタッチ

```sql
-- 既存テーブルをパーティションとしてアタッチ
-- テーブルのデータがパーティション条件を満たす必要がある

-- 1. 制約を追加 (アタッチ時の検証を高速化)
ALTER TABLE orders_2024_06
    ADD CONSTRAINT orders_2024_06_check
    CHECK (order_date >= '2024-06-01' AND order_date < '2024-07-01');

-- 2. アタッチ
ALTER TABLE orders ATTACH PARTITION orders_2024_06
    FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
```

### 4.4 パーティションの確認

```sql
-- パーティション一覧
SELECT
    parent.relname AS parent_table,
    child.relname AS partition_name,
    pg_get_expr(child.relpartbound, child.oid) AS partition_bound
FROM pg_inherits
JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
JOIN pg_class child ON pg_inherits.inhrelid = child.oid
WHERE parent.relname = 'orders'
ORDER BY child.relname;

-- パーティションサイズ
SELECT
    child.relname AS partition_name,
    pg_size_pretty(pg_relation_size(child.oid)) AS size,
    pg_stat_user_tables.n_live_tup AS row_count
FROM pg_inherits
JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
JOIN pg_class child ON pg_inherits.inhrelid = child.oid
LEFT JOIN pg_stat_user_tables ON child.relname = pg_stat_user_tables.relname
WHERE parent.relname = 'orders'
ORDER BY child.relname;

-- パーティション情報 (詳細)
SELECT * FROM pg_partitioned_table WHERE partrelid = 'orders'::regclass;
```

---

## 5. パフォーマンス最適化

### 5.1 パーティションプルーニング

```sql
-- パーティションプルーニングの確認
SET enable_partition_pruning = on;  -- デフォルトでon

-- 実行計画で確認
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM orders WHERE order_date = '2024-03-15';

-- 結果例:
-- Append
--   ->  Seq Scan on orders_2024_03 orders_1
--         Filter: (order_date = '2024-03-15'::date)
--
-- orders_2024_03 パーティションのみスキャン

-- 複数パーティションにまたがる場合
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM orders
WHERE order_date BETWEEN '2024-02-15' AND '2024-03-15';

-- 結果例:
-- Append
--   ->  Seq Scan on orders_2024_02 orders_1
--   ->  Seq Scan on orders_2024_03 orders_2
```

### 5.2 パーティションワイズジョイン

```sql
-- パーティションワイズジョインの有効化
SET enable_partitionwise_join = on;  -- デフォルトでoff

-- 同じパーティションキーを持つテーブル同士の結合が効率化
EXPLAIN (ANALYZE, COSTS OFF)
SELECT o.*, oi.*
FROM orders o
JOIN order_items oi ON o.id = oi.order_id AND o.order_date = oi.order_date
WHERE o.order_date = '2024-03-15';
```

### 5.3 パーティションワイズアグリゲート

```sql
-- パーティションワイズアグリゲートの有効化
SET enable_partitionwise_aggregate = on;  -- デフォルトでoff

-- パーティション単位で集計してからマージ
EXPLAIN (ANALYZE, COSTS OFF)
SELECT order_date, count(*), sum(total_amount)
FROM orders
WHERE order_date >= '2024-01-01'
GROUP BY order_date;
```

### 5.4 インデックス戦略

```sql
-- グローバルインデックス (親テーブルに作成)
-- 全パーティションに自動的に作成される
CREATE INDEX idx_orders_user_id ON orders (user_id);

-- ローカルインデックス (特定パーティションにのみ)
CREATE INDEX idx_orders_2024_03_status ON orders_2024_03 (status);

-- パーティションキーを含むインデックス
-- プルーニングがより効果的に
CREATE INDEX idx_orders_date_user ON orders (order_date, user_id);

-- インデックスの確認
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexname::regclass)) AS size
FROM pg_indexes
WHERE tablename LIKE 'orders%';
```

---

## 6. 運用とメンテナンス

### 6.1 パーティション自動作成スクリプト

```sql
-- 月次パーティション自動作成関数
CREATE OR REPLACE FUNCTION create_monthly_partition(
    parent_table TEXT,
    target_date DATE
) RETURNS VOID AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    start_date := date_trunc('month', target_date);
    end_date := start_date + interval '1 month';
    partition_name := parent_table || '_' || to_char(start_date, 'YYYY_MM');

    -- パーティションが存在しない場合のみ作成
    IF NOT EXISTS (
        SELECT 1 FROM pg_class WHERE relname = partition_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
            partition_name, parent_table, start_date, end_date
        );
        RAISE NOTICE 'Created partition: %', partition_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- 使用例: 今月と来月のパーティションを作成
SELECT create_monthly_partition('orders', CURRENT_DATE);
SELECT create_monthly_partition('orders', CURRENT_DATE + interval '1 month');
```

### 6.2 古いパーティションの削除スクリプト

```sql
-- 古いパーティション削除関数
CREATE OR REPLACE FUNCTION drop_old_partitions(
    parent_table TEXT,
    retention_months INT
) RETURNS VOID AS $$
DECLARE
    partition_record RECORD;
    cutoff_date DATE;
BEGIN
    cutoff_date := date_trunc('month', CURRENT_DATE - (retention_months || ' months')::interval);

    FOR partition_record IN
        SELECT child.relname AS partition_name
        FROM pg_inherits
        JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
        JOIN pg_class child ON pg_inherits.inhrelid = child.oid
        WHERE parent.relname = parent_table
          AND child.relname ~ '_\d{4}_\d{2}$'
          AND to_date(substring(child.relname from '_(\d{4}_\d{2})$'), 'YYYY_MM') < cutoff_date
    LOOP
        EXECUTE format('ALTER TABLE %I DETACH PARTITION %I', parent_table, partition_record.partition_name);
        EXECUTE format('DROP TABLE %I', partition_record.partition_name);
        RAISE NOTICE 'Dropped partition: %', partition_record.partition_name;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- 使用例: 12ヶ月より古いパーティションを削除
SELECT drop_old_partitions('orders', 12);
```

### 6.3 定期メンテナンスジョブ

```bash
#!/bin/bash
# partition_maintenance.sh

PGHOST="${PGHOST:-localhost}"
PGUSER="${PGUSER:-postgres}"
PGDATABASE="${PGDATABASE:-mydb}"
LOG_FILE="/var/log/pg_partition/maintenance_$(date +%Y%m%d).log"

exec > >(tee -a $LOG_FILE) 2>&1

echo "=== Partition Maintenance Started: $(date) ==="

# 新しいパーティションを作成 (2ヶ月先まで)
psql -h $PGHOST -U $PGUSER -d $PGDATABASE << 'EOF'
SELECT create_monthly_partition('orders', CURRENT_DATE);
SELECT create_monthly_partition('orders', CURRENT_DATE + interval '1 month');
SELECT create_monthly_partition('orders', CURRENT_DATE + interval '2 months');
EOF

# 古いパーティションを削除 (12ヶ月より古い)
psql -h $PGHOST -U $PGUSER -d $PGDATABASE << 'EOF'
SELECT drop_old_partitions('orders', 12);
EOF

# パーティション統計情報の更新
psql -h $PGHOST -U $PGUSER -d $PGDATABASE -c "ANALYZE orders;"

# パーティション一覧の確認
psql -h $PGHOST -U $PGUSER -d $PGDATABASE << 'EOF'
SELECT
    child.relname AS partition_name,
    pg_size_pretty(pg_relation_size(child.oid)) AS size
FROM pg_inherits
JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
JOIN pg_class child ON pg_inherits.inhrelid = child.oid
WHERE parent.relname = 'orders'
ORDER BY child.relname;
EOF

echo "=== Partition Maintenance Completed: $(date) ==="
```

### 6.4 cronスケジュール

```bash
# /etc/cron.d/pg_partition

# 毎日午前2時にパーティションメンテナンス
0 2 * * * postgres /opt/scripts/partition_maintenance.sh
```

---

## 7. 実践パターン

### 7.1 時系列データ (ログ、イベント)

```sql
-- 日次パーティション (大量ログ向け)
CREATE TABLE access_logs (
    id BIGSERIAL,
    user_id BIGINT,
    path VARCHAR(500),
    method VARCHAR(10),
    status_code INT,
    response_time_ms INT,
    created_at TIMESTAMP NOT NULL
) PARTITION BY RANGE (created_at);

-- 日次パーティション自動作成関数
CREATE OR REPLACE FUNCTION create_daily_partition(
    parent_table TEXT,
    target_date DATE
) RETURNS VOID AS $$
DECLARE
    partition_name TEXT;
BEGIN
    partition_name := parent_table || '_' || to_char(target_date, 'YYYYMMDD');

    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = partition_name) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
            partition_name, parent_table,
            target_date, target_date + interval '1 day'
        );
    END IF;
END;
$$ LANGUAGE plpgsql;

-- INSERTトリガーでパーティションを自動作成
CREATE OR REPLACE FUNCTION ensure_partition_exists()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM create_daily_partition('access_logs', NEW.created_at::date);
    RETURN NEW;
EXCEPTION WHEN duplicate_table THEN
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 注意: トリガーは性能に影響するため、
-- 事前にパーティションを作成しておく方が推奨
```

### 7.2 マルチテナント

```sql
-- テナントIDでLISTパーティション
CREATE TABLE tenant_data (
    id BIGSERIAL,
    tenant_id VARCHAR(50) NOT NULL,
    data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY LIST (tenant_id);

-- テナントごとにパーティション作成
CREATE TABLE tenant_data_acme PARTITION OF tenant_data
    FOR VALUES IN ('acme');

CREATE TABLE tenant_data_globex PARTITION OF tenant_data
    FOR VALUES IN ('globex');

-- 新規テナント追加時
CREATE TABLE tenant_data_newcorp PARTITION OF tenant_data
    FOR VALUES IN ('newcorp');

-- テナント削除時 (データも一緒に削除)
ALTER TABLE tenant_data DETACH PARTITION tenant_data_oldcorp;
DROP TABLE tenant_data_oldcorp;
```

### 7.3 地理データ

```sql
-- リージョンでLISTパーティション + 日付でRANGE
CREATE TABLE geo_events (
    id BIGSERIAL,
    region VARCHAR(20) NOT NULL,
    event_date DATE NOT NULL,
    location POINT,
    data JSONB
) PARTITION BY LIST (region);

-- リージョンパーティション
CREATE TABLE geo_events_apac PARTITION OF geo_events
    FOR VALUES IN ('JP', 'KR', 'CN', 'AU', 'SG')
    PARTITION BY RANGE (event_date);

CREATE TABLE geo_events_emea PARTITION OF geo_events
    FOR VALUES IN ('GB', 'DE', 'FR', 'AE')
    PARTITION BY RANGE (event_date);

-- サブパーティション (月次)
CREATE TABLE geo_events_apac_2024_01 PARTITION OF geo_events_apac
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

### 7.4 既存テーブルのパーティション化

```sql
-- 既存テーブルをパーティションテーブルに変換

-- 1. 新しいパーティションテーブルを作成
CREATE TABLE orders_new (
    LIKE orders INCLUDING ALL
) PARTITION BY RANGE (order_date);

-- 2. パーティションを作成
CREATE TABLE orders_new_2024_01 PARTITION OF orders_new
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
-- ... 他のパーティションも作成

-- 3. データを移行 (バッチで)
INSERT INTO orders_new SELECT * FROM orders WHERE order_date >= '2024-01-01' AND order_date < '2024-02-01';

-- 4. テーブルを入れ替え (短時間ロック)
BEGIN;
ALTER TABLE orders RENAME TO orders_old;
ALTER TABLE orders_new RENAME TO orders;
COMMIT;

-- 5. 旧テーブルを削除
DROP TABLE orders_old;
```

### 7.5 パーティション監視クエリ

```sql
-- パーティション別サイズとレコード数
SELECT
    parent.relname AS parent_table,
    child.relname AS partition_name,
    pg_size_pretty(pg_relation_size(child.oid)) AS size,
    pg_stat_user_tables.n_live_tup AS rows,
    pg_stat_user_tables.n_dead_tup AS dead_rows,
    pg_stat_user_tables.last_vacuum,
    pg_stat_user_tables.last_analyze
FROM pg_inherits
JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
JOIN pg_class child ON pg_inherits.inhrelid = child.oid
LEFT JOIN pg_stat_user_tables ON child.relname = pg_stat_user_tables.relname
WHERE parent.relname = 'orders'
ORDER BY child.relname;

-- 偏りの検出
WITH partition_stats AS (
    SELECT
        child.relname AS partition_name,
        pg_relation_size(child.oid) AS size_bytes
    FROM pg_inherits
    JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
    JOIN pg_class child ON pg_inherits.inhrelid = child.oid
    WHERE parent.relname = 'orders'
)
SELECT
    partition_name,
    pg_size_pretty(size_bytes) AS size,
    ROUND(100.0 * size_bytes / SUM(size_bytes) OVER (), 2) AS size_percent
FROM partition_stats
ORDER BY size_bytes DESC;
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - テーブルパーティショニング](https://www.postgresql.org/docs/current/ddl-partitioning.html)
- [PostgreSQL公式ドキュメント - パーティション管理](https://www.postgresql.org/docs/current/sql-altertable.html)
- [pg_partman - 自動パーティション管理拡張](https://github.com/pgpartman/pg_partman)
- [PostgreSQL Wiki - Table partitioning](https://wiki.postgresql.org/wiki/Table_partitioning)
