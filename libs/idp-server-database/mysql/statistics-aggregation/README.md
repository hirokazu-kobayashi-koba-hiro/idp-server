# 統計データ日次集計（手動適用 - MySQL版）

`security_event` テーブルからテナント統計を日次バッチで集計する。
Flyway管理外のため手動で適用する。

## ファイル一覧

| ファイル | 説明 |
|---------|------|
| `01_daily_statistics_aggregation.mysql.sql` | 集計ストアドプロシージャ + Event Scheduler |

## 適用手順

### 1. タイムゾーンテーブルの確認

`CONVERT_TZ` を使用するため、MySQLにタイムゾーンデータが必要。

```bash
mysql_tzinfo_to_sql /usr/share/zoneinfo | mysql -u root mysql
```

### 2. 集計プロシージャの登録

```bash
mysql -h <host> -u <user> -p <database> < 01_daily_statistics_aggregation.mysql.sql
```

Event Scheduler ジョブ `evt_daily_statistics_aggregation` も同時に登録される（毎日 04:00）。

### 3. 動作確認

```sql
-- 前日分を手動実行
CALL aggregate_daily_statistics(NULL);

-- 特定の日を指定
CALL aggregate_daily_statistics('2026-03-20');
```
