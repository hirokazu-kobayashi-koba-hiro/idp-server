# 統計データ日次集計（手動適用）

`security_event` テーブルからテナント統計を日次バッチで集計する。
Flyway管理外のため手動で適用する。

## ファイル一覧

| ファイル | 説明 |
|---------|------|
| `01_daily_statistics_aggregation.sql` | 集計関数（行単位TZ変換版） |
| `02_daily_statistics_aggregation_optimized.sql` | 集計関数（事前計算版、推奨） |
| `99_generate_test_data.sql` | ベンチマーク用テストデータ生成 |

## 適用手順

### 1. 集計関数の登録

```bash
# 最適化版（推奨）
PGPASSWORD=<password> psql -h <host> -U idpserver -d idpserver \
  -f 02_daily_statistics_aggregation_optimized.sql
```

### 2. pg_cronジョブの登録

`operation/setup-pg-cron-jobs.sql` に `daily-statistics-aggregation` ジョブが含まれている。

```bash
# postgresデータベースで実行（pg_cronはpostgresに存在）
PGPASSWORD=<password> psql -h <host> -U idp -d postgres \
  -f ../operation/setup-pg-cron-jobs.sql
```

スケジュール: 毎日 04:00 UTC（partman 02:00、archive 03:00 の後）

### 3. 動作確認

```sql
-- 前日分を手動実行
SELECT * FROM aggregate_daily_statistics();

-- 特定の日を指定
SELECT * FROM aggregate_daily_statistics('2026-03-20'::date);

-- 冪等性: 何度実行しても同じ結果
SELECT * FROM aggregate_daily_statistics('2026-03-20'::date);
```

## ベンチマーク

### テストデータ生成

```bash
# 100K件（デフォルト）
PGPASSWORD=<password> psql -h <host> -U idpserver -d idpserver \
  -f 99_generate_test_data.sql

# 500万件（特定日付）
PGPASSWORD=<password> psql -h <host> -U idpserver -d idpserver \
  -v target_count=5000000 \
  -v target_date="'2026-03-20'" \
  -f 99_generate_test_data.sql
```

### ベンチマーク実行

```sql
SELECT * FROM aggregate_daily_statistics('2026-03-20'::date);
```

### テストデータ削除

```sql
DELETE FROM security_event WHERE description = 'benchmark-test-data';
```

### 実測結果（Docker / PostgreSQL 15）

| データ量 | 行単位TZ変換 (01) | 事前計算 (02) | 改善率 |
|:---:|:---:|:---:|:---:|
| 6K | 18ms | 12ms | 1.5x |
| 100K | 540ms | 510ms | 1.1x |
| 1M | 3.1秒 | 1.4秒 | 2.2x |
| 5M | 14.1秒 | 6.5秒 | 2.2x |
| 20M（推定） | ~56秒 | ~26秒 | 2.2x |

## 設計ポイント

- **テナントTZ対応**: `tenant.attributes->>'timezone'` から各テナントのタイムゾーンを取得
- **会計年度対応**: `tenant.attributes->>'fiscal_year_start_month'` でYAUの年度開始を計算
- **パーティションプルーニング**: 定数の時間窓で不要パーティションをスキップ
- **冪等性**: `ON CONFLICT ... SET count = EXCLUDED.count` で絶対値上書き
