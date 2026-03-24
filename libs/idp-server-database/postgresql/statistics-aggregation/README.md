# 統計データ日次集計（手動適用）

`security_event` テーブルから統計データを日次バッチで集計する関数。
Flyway管理外のため手動で適用する。

## 背景

アプリ側（`SecurityEventHandler`）ではセキュリティイベント発生のたびに `statistics_events` テーブルを `UPSERT`（`INSERT ... ON CONFLICT DO UPDATE`）で更新していた。
高負荷時にはフック処理（Slack/Webhook等のHTTP I/O）と統計更新が同一トランザクションで実行されるため、UPDATEのロック保持時間が長くなり、他のイベントの統計更新がブロックされる問題があった。

この関数は、統計集計をDB側の日次バッチに移行することで、アプリ側のロック競合を解消する。

## 全体像

```
security_event テーブル（ソース）
  │
  │  aggregate_daily_statistics('2026-03-24')
  │  毎日 04:00 UTC に pg_cron で自動実行
  │
  ├──→ Step 1: statistics_events
  │      イベント種別ごとのカウント集計
  │      (login_success: 500, login_failure: 20, ...)
  │
  ├──→ Step 2: statistics_daily_users
  │      日次アクティブユーザー（DAU）のユーザーID記録
  │
  ├──→ Step 3: statistics_monthly_users
  │      月次アクティブユーザー（MAU）のユーザーID記録
  │
  ├──→ Step 4: statistics_yearly_users
  │      年次アクティブユーザー（YAU）のユーザーID記録
  │      ※テナントの会計年度（fiscal_year_start_month）を考慮
  │
  └──→ Step 5: statistics_events に DAU/MAU/YAU カウントを追記
         Step 2-4 のユニークユーザー数を集計
         (dau: 155, mau: 1200, yau: 5000)
```

## 各ステップの詳細

### 準備: テナントTZマッピング構築（02版のみ）

テナントテーブルから一意なタイムゾーンを抽出し、対象日のUTC時間範囲を事前計算する。

```
tenant.attributes->>'timezone' = 'Asia/Tokyo' の場合:
  対象日 2026-03-24 のローカル 00:00〜23:59 は
  UTC では 2026-03-23 15:00 〜 2026-03-24 15:00

→ このUTC範囲でフィルタすれば、行ごとの AT TIME ZONE 変換が不要
```

01版は行ごとに `AT TIME ZONE` で変換するため、大量データで遅くなる。

### Step 1: イベントカウント集計 → `statistics_events`

`security_event` から対象日のイベントを**テナントのローカル日付**で絞り込み、イベント種別ごとにカウントする。

```sql
INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
SELECT tenant_id, '2026-03-24', type, COUNT(*)
FROM security_event ...
GROUP BY tenant_id, type
ON CONFLICT (tenant_id, stat_date, event_type)
DO UPDATE SET count = EXCLUDED.count;  -- 絶対値上書き（冪等）
```

- `inspect_token_success` を含む全イベントタイプをカウントする（バッチ集計のためフィルタ不要）
- アプリ側（`SecurityEventHandler`）ではリアルタイムUPSERTのロック競合軽減のため除外していたが、バッチでは1日分を `COUNT(*)` するだけなのでコスト差なし

### Step 2: DAU → `statistics_daily_users`

対象日にアクティブだったユーザーのIDを記録する。

```sql
INSERT INTO statistics_daily_users (tenant_id, stat_date, user_id, ...)
SELECT tenant_id, '2026-03-24', user_id, ...
FROM security_event
WHERE type IN ('login_success', 'issue_token_success',
               'refresh_token_success', 'inspect_token_success')
  AND user_id IS NOT NULL
GROUP BY tenant_id, user_id
ON CONFLICT DO UPDATE SET last_used_at = GREATEST(...);
```

### Step 3: MAU → `statistics_monthly_users`

Step 2 と同じ抽出条件で、`stat_month`（月初日）にグループ化して記録する。
月をまたいで実行しても `ON CONFLICT DO NOTHING` 的に既存レコードは `last_used_at` のみ更新。

### Step 4: YAU → `statistics_yearly_users`

テナントの**会計年度**（`fiscal_year_start_month`）を考慮した `stat_year` を計算する。

```
FiscalYearCalculator.java と同一ロジック:

  candidateStart = 対象日の年 + 会計年度開始月 + 1日
  if 対象日 < candidateStart → candidateStart - 1年

例（fiscal_year_start_month = 4、日本企業）:
  2026-02-15 → candidate=2026-04-01 → 2月<4月 → stat_year = 2025-04-01（FY2025）
  2026-05-10 → candidate=2026-04-01 → 5月≥4月 → stat_year = 2026-04-01（FY2026）
```

### Step 5: DAU/MAU/YAU カウント → `statistics_events`

Step 2-4 で記録したユーザーテーブルから `COUNT(DISTINCT user_id)` を集計し、`statistics_events` に `dau` / `mau` / `yau` として書き込む。

YAU カウントでは Step 4 と同じ会計年度計算で `stat_year` を決定し、対応する年度のユニークユーザー数を集計する。

## ファイル一覧

| ファイル | 説明 |
|---------|------|
| `01_daily_statistics_aggregation.sql` | 集計関数（行単位TZ変換版） |
| `02_daily_statistics_aggregation_optimized.sql` | 集計関数（事前計算版、推奨） |
| `99_generate_test_data.sql` | ベンチマーク用テストデータ生成 |

**注意**: 01 と 02 は**排他**（同じ関数名 `aggregate_daily_statistics` を `CREATE OR REPLACE` する）。どちらか一方のみ適用する。Docker環境では02が自動適用される。

### 01 と 02 の違い

| | 01（行単位TZ変換） | 02（事前計算、推奨） |
|---|---|---|
| TZ変換 | 全行に `AT TIME ZONE` を適用 | テナントTZグループごとにUTC範囲を事前計算 |
| パーティション | 定数窓で3パーティションに限定 | 同上 + tempテーブルJOINで精密フィルタ |
| 性能（5M件） | 14.1秒 | 6.5秒 |
| 適用場面 | シンプルさ優先 | 大規模データ |

## 適用手順

### 1. 集計関数の登録

```bash
# 最適化版（推奨）
PGPASSWORD=<password> psql -h <host> -U idpserver -d idpserver \
  -f 02_daily_statistics_aggregation_optimized.sql
```

### 2. pg_cronジョブの登録

Docker環境では `statistics-setup` コンテナが自動で登録する。
手動の場合:

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

## 設計ポイント

### テナントタイムゾーン対応

`security_event.created_at` はUTCで保存されている。統計の日付は各テナントのローカル日付で計算する必要がある。

```
イベント: 2026-03-24 23:00 UTC

テナントA (Asia/Tokyo, UTC+9):  → 2026-03-25 08:00 JST → 3月25日の統計
テナントB (UTC):                → 2026-03-24 23:00 UTC → 3月24日の統計
```

02版では、テナントごとにTZ変換するのではなく、TZグループごとにUTC範囲を事前計算することで大幅に高速化。

### パーティションプルーニング

`security_event` テーブルは `created_at` で日次パーティション分割されている。
02版では、定数の広い窓（`[target_date - 14h, target_date + 36h)`）をWHERE句に含めることで、PostgreSQLが不要パーティションをスキップできる。

```sql
-- この定数条件でパーティションプルーニングが効く
WHERE ev.created_at >= v_prune_start AND ev.created_at < v_prune_end
-- この動的条件でテナントTZに基づく精密フィルタ
  AND ev.created_at >= tr.utc_start AND ev.created_at < tr.utc_end
```

### 冪等性

全てのINSERTで `ON CONFLICT ... DO UPDATE SET count = EXCLUDED.count`（絶対値上書き）を使用。
加算（`count + EXCLUDED.count`）ではないため、何度実行しても同じ結果になる。

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
