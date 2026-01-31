# statistics_events テーブル移行ガイド

`statistics_monthly.daily_metrics` (JSONB) から `statistics_events` (正規化テーブル) への移行手順。

---

## 目次

1. [概要](#概要)
2. [全体フロー](#全体フロー)
3. [現在のアプリ実装状況](#現在のアプリ実装状況)
4. [移行前の確認](#移行前の確認)
5. [Phase 1: バックアップ](#phase-1-バックアップ)
6. [Phase 2: データ移行 (PostgreSQL)](#phase-2-データ移行-postgresql)
7. [Phase 3: データ移行 (MySQL)](#phase-3-データ移行-mysql)
8. [Phase 4: 検証](#phase-4-検証)
9. [Phase 5: クリーンアップ](#phase-5-クリーンアップ)
10. [ロールバック](#ロールバック)
11. [チェックリスト](#チェックリスト)

---

## 概要

### 背景

Issue #1198 で、統計データの書き込みパフォーマンス改善のため `statistics_events` テーブルを導入しました。

### 問題点

`statistics_monthly.daily_metrics` (JSONB) への更新は以下の問題がありました：

- **行全体のロック**: JSONBの一部更新でも行全体がロックされる
- **同時書き込み競合**: 高トラフィック時にデッドロック発生
- **インデックス非効率**: JSONB内のキーへの検索が遅い

### 解決策

```
【移行前】
statistics_monthly.daily_metrics (JSONB)
{
  "2025-01-01": {"dau": 100, "login_success": 1500, "login_failure": 20},
  "2025-01-02": {"dau": 120, "login_success": 1600, "login_failure": 15},
  ...
}

【移行後】
statistics_events (Row-based)
| tenant_id | stat_date  | event_type    | count |
|-----------|------------|---------------|-------|
| xxx       | 2025-01-01 | dau           | 100   |
| xxx       | 2025-01-01 | login_success | 1500  |
| xxx       | 2025-01-01 | login_failure | 20    |
```

### 対象テーブル

| テーブル | 役割 | 移行後の状態 |
|----------|------|-------------|
| `statistics_events` | 新: イベント別日次統計 | 主テーブル |
| `statistics_monthly` | 旧: JSONB月次統計 | `daily_metrics` 廃止予定 |

---

## 全体フロー

```
┌─────────────────────────────────────────────────────────────────┐
│ Step 1: Flyway実行                                               │
│ ─────────────────────────────────────────────────────────────── │
│ V0_9_28__statistics_events.sql で新テーブル作成                  │
│ ※ アプリリリース前に実行                                         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 2: アプリリリース                                           │
│ ─────────────────────────────────────────────────────────────── │
│ 新コードをデプロイ                                               │
│ ※ SecurityEventHandler が statistics_events に書き込み開始      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 3: 新規データは statistics_events に書き込み                │
│ ─────────────────────────────────────────────────────────────── │
│ アプリ稼働開始、新規イベントは新テーブルへ                       │
│ ※ 旧テーブル (statistics_monthly.daily_metrics) には書き込まない│
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 4: データ移行（過去データ）                                 │
│ ─────────────────────────────────────────────────────────────── │
│ statistics_monthly.daily_metrics → statistics_events            │
│ ※ ON CONFLICT で既存キーは加算（アプリ稼働中に実行可能）        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 5: 検証・クリーンアップ                                     │
│ ─────────────────────────────────────────────────────────────── │
│ データ整合性確認後、daily_metrics をクリア                       │
└─────────────────────────────────────────────────────────────────┘
```

### 重要なポイント

| ポイント | 説明 |
|---------|------|
| **アプリ稼働中に移行可能** | Step 4 はアプリを停止せずに実行できる |
| **キー重複時は加算** | `ON CONFLICT DO UPDATE SET count = count + EXCLUDED.count` |
| **移行は1回だけ実行** | 加算モードのため、複数回実行すると値が重複する |
| **デュアルライト不要** | アプリは最初から新テーブルのみに書き込み |

### 当日データの扱い

リリース当日は、旧アプリと新アプリの両方がデータを書き込む可能性があります。

```
0時 ─────── 15時(リリース) ─────── 24時
    │              │                │
    └──旧アプリ────┘                │
    statistics_monthly              │
    に書き込み                      │
                   │                │
                   └────新アプリ────┘
                   statistics_events
                   に書き込み
```

加算モードにより、両方のデータが正しく合算されます。

---

## 現在のアプリ実装状況

**重要**: 現在のアプリケーションは既に `statistics_events` テーブルに完全移行済みです。

| 操作 | 対象テーブル | 実装クラス |
|------|-------------|-----------|
| 書き込み | `statistics_events` | `SecurityEventHandler` → `StatisticsEventsCommandRepository` |
| 読み込み | `statistics_events` | `TenantStatisticsQueryRepository` → `PostgresqlExecutor` |

### 移行が必要なもの

- **過去データのみ**: `statistics_monthly.daily_metrics` に残っている過去の統計データ

### 移行が不要なもの

- **アプリケーション設定変更**: 既に完了
- **デュアルライト**: 不要（アプリは既に `statistics_events` のみに書き込み）
- **読み込み切り替え**: 不要（アプリは既に `statistics_events` から読み込み）

---

## 移行前の確認

### 前提条件

- [ ] `V0_9_28__statistics_events.sql` マイグレーション適用済み
- [ ] `statistics_events` テーブルが存在する

### 事前確認

```bash
psql -f check_statistics_events.sql
```

確認内容:
- テーブル存在確認
- 現在のテーブル状態
- 移行対象レコード数
- テナント別の移行対象件数

---

## Phase 1: バックアップ

### PostgreSQL

```bash
# フルバックアップ（推奨）
pg_dump -h localhost -U postgres -d idpserver \
  -t statistics_monthly \
  -t statistics_events \
  -F c -f statistics_backup_$(date +%Y%m%d_%H%M%S).dump
```

### Docker環境

```bash
docker exec postgres-primary pg_dump -U idpserver -d idpserver \
  -t statistics_monthly \
  -t statistics_events \
  -F c > statistics_backup_$(date +%Y%m%d).dump
```

---

## Phase 2: データ移行 (PostgreSQL)

### 2.1 psqlで実行

#### 事前準備: 接続情報の設定

```bash
# 方法1: 環境変数で設定
export PGHOST=your-db-host.example.com
export PGPORT=5432
export PGDATABASE=idpserver
export PGUSER=idpserver
export PGPASSWORD=yourpassword  # または pgpass を使用

# 方法2: pgpass ファイルで設定（推奨）
# ~/.pgpass に以下の形式で記載
# hostname:port:database:username:password
echo "your-db-host.example.com:5432:idpserver:idpserver:yourpassword" >> ~/.pgpass
chmod 600 ~/.pgpass
```

#### 移行実行

```bash
cd libs/idp-server-database/postgresql/operation/

# 移行実行
psql -f migrate_statistics_events.sql

# 検証実行
psql -f verify_statistics_events.sql
```

#### SQLファイル

| ファイル | 説明 |
|---------|------|
| `check_statistics_events.sql` | 事前確認SQL |
| `migrate_statistics_events.sql` | 移行SQL |
| `verify_statistics_events.sql` | 検証SQL |

### 2.2 移行SQLの詳細

アプリケーションを停止せずに移行できます。`ON CONFLICT` により競合を安全に処理します。

```sql
-- アプリ稼働中に実行可能
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
    count = statistics_events.count + EXCLUDED.count,  -- 既存値に加算
    updated_at = NOW();
```

**ポイント**:
- `ON CONFLICT DO UPDATE SET count = statistics_events.count + EXCLUDED.count`
- アプリからの新規書き込みと過去データの移行が競合しても、値が正しく加算される

### 2.3 メンテナンス時間中の移行（データ整合性重視）

アプリを停止して移行する場合は、上書きモードを使用します。

```sql
BEGIN;

-- アプリ停止中に実行
TRUNCATE TABLE statistics_events;

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
  AND days.key ~ '^\d{4}-\d{2}-\d{2}$'
  AND metrics.value ~ '^\d+$'
  AND (metrics.value)::bigint > 0;

COMMIT;
```

### 2.4 大規模データの場合（バッチ処理）

```sql
-- 月単位でバッチ処理
DO $$
DECLARE
    target_month DATE;
    migrated_count INTEGER;
BEGIN
    FOR target_month IN
        SELECT DISTINCT stat_month
        FROM statistics_monthly
        WHERE daily_metrics IS NOT NULL
          AND daily_metrics != '{}'::jsonb
        ORDER BY stat_month
    LOOP
        INSERT INTO statistics_events (tenant_id, stat_date, event_type, count, created_at, updated_at)
        SELECT
            sm.tenant_id,
            days.key::date,
            metrics.key,
            (metrics.value)::bigint,
            NOW(),
            NOW()
        FROM statistics_monthly sm
        CROSS JOIN LATERAL jsonb_each(sm.daily_metrics) as days(key, day_data)
        CROSS JOIN LATERAL jsonb_each_text(days.day_data) as metrics(key, value)
        WHERE sm.stat_month = target_month
          AND days.key ~ '^\d{4}-\d{2}-\d{2}$'
          AND metrics.value ~ '^\d+$'
          AND (metrics.value)::bigint > 0
        ON CONFLICT (tenant_id, stat_date, event_type)
        DO UPDATE SET
            count = statistics_events.count + EXCLUDED.count,
            updated_at = NOW();

        GET DIAGNOSTICS migrated_count = ROW_COUNT;
        RAISE NOTICE 'Migrated %: % rows', target_month, migrated_count;

        -- 負荷軽減のため少し待機
        PERFORM pg_sleep(0.5);
    END LOOP;
END $$;
```

---

## Phase 3: データ移行 (MySQL)

### 3.1 移行スクリプト

```sql
-- MySQLではストアドプロシージャを使用

DELIMITER //

CREATE PROCEDURE migrate_statistics_events()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_tenant_id CHAR(36);
    DECLARE v_daily_metrics JSON;
    DECLARE v_day_key VARCHAR(10);
    DECLARE v_day_data JSON;
    DECLARE v_metric_key VARCHAR(255);
    DECLARE v_metric_value BIGINT;
    DECLARE v_day_keys JSON;
    DECLARE v_metric_keys JSON;
    DECLARE i INT;
    DECLARE j INT;

    DECLARE cur CURSOR FOR
        SELECT tenant_id, daily_metrics
        FROM statistics_monthly
        WHERE daily_metrics IS NOT NULL
          AND daily_metrics != '{}';

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_tenant_id, v_daily_metrics;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET v_day_keys = JSON_KEYS(v_daily_metrics);
        SET i = 0;

        WHILE i < JSON_LENGTH(v_day_keys) DO
            SET v_day_key = JSON_UNQUOTE(JSON_EXTRACT(v_day_keys, CONCAT('$[', i, ']')));
            SET v_day_data = JSON_EXTRACT(v_daily_metrics, CONCAT('$."', v_day_key, '"'));
            SET v_metric_keys = JSON_KEYS(v_day_data);
            SET j = 0;

            WHILE j < JSON_LENGTH(v_metric_keys) DO
                SET v_metric_key = JSON_UNQUOTE(JSON_EXTRACT(v_metric_keys, CONCAT('$[', j, ']')));
                SET v_metric_value = COALESCE(
                    CAST(JSON_EXTRACT(v_day_data, CONCAT('$."', v_metric_key, '"')) AS UNSIGNED),
                    0
                );

                IF v_metric_value > 0 THEN
                    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count, created_at, updated_at)
                    VALUES (
                        v_tenant_id,
                        STR_TO_DATE(v_day_key, '%Y-%m-%d'),
                        v_metric_key,
                        v_metric_value,
                        NOW(),
                        NOW()
                    )
                    ON DUPLICATE KEY UPDATE
                        count = count + VALUES(count),
                        updated_at = NOW();
                END IF;

                SET j = j + 1;
            END WHILE;

            SET i = i + 1;
        END WHILE;
    END LOOP;

    CLOSE cur;
END //

DELIMITER ;

-- 実行
CALL migrate_statistics_events();

-- クリーンアップ
DROP PROCEDURE IF EXISTS migrate_statistics_events;
```

---

## Phase 4: 検証

### 4.1 件数確認

```sql
-- 移行後のデータ件数
SELECT
    'statistics_events' as table_name,
    COUNT(*) as rows,
    COUNT(DISTINCT tenant_id) as tenants
FROM statistics_events;
```

### 4.2 合計値検証

```sql
-- イベントタイプ別の合計比較（MISMATCHがないことを確認）
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
    COUNT(*) as total_count
FROM original o
JOIN migrated m ON o.tenant_id = m.tenant_id AND o.event_type = m.event_type;
```

**期待結果**: `mismatch_count = 0`

### 4.3 MISMATCH詳細確認（問題がある場合）

```sql
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
ORDER BY ABS(o.original_count - m.migrated_count) DESC;
```

---

## Phase 5: クリーンアップ

### 5.1 daily_metrics クリア（移行完了後）

```sql
-- 注意: 検証完了後に実行
UPDATE statistics_monthly
SET daily_metrics = '{}'::jsonb,
    updated_at = NOW()
WHERE daily_metrics IS NOT NULL
  AND daily_metrics != '{}'::jsonb;
```

### 5.2 カラム削除（将来のマイグレーション）

```sql
-- V0_9_30 以降で実施予定
-- ALTER TABLE statistics_monthly DROP COLUMN daily_metrics;
```

---

## ロールバック

### データリストア（必要な場合）

```bash
# PostgreSQL
pg_restore -h localhost -U postgres -d idpserver \
  -t statistics_monthly \
  --data-only \
  --clean \
  statistics_backup_YYYYMMDD.dump
```

### statistics_events クリア（必要な場合）

```sql
TRUNCATE TABLE statistics_events;
```

---

## チェックリスト

| # | Step | 項目 | 担当 | 完了 |
|---|------|------|------|------|
| 1 | Step 1 | Flyway実行（V0_9_28 マイグレーション適用） | DBA | [ ] |
| 2 | Step 2 | アプリリリース | Dev | [ ] |
| 3 | Step 3 | 新規データが statistics_events に書き込まれることを確認 | Dev | [ ] |
| 4 | Step 4 | バックアップ取得 | DBA | [ ] |
| 5 | Step 4 | データ移行実行（過去データ） | DBA | [ ] |
| 6 | Step 5 | 合計値検証（MISMATCH = 0） | DBA | [ ] |
| 7 | Step 5 | daily_metrics クリーンアップ | DBA | [ ] |

---

## 関連ドキュメント

- [統計データメンテナンスガイド](./statistics-maintenance.md)
- [過去データ集計スクリプト](./aggregate_historical_statistics.sql)
- マイグレーション: `V0_9_28__statistics_events.sql`
