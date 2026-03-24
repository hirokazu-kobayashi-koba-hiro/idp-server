# 統計データ日次集計（手動適用 - MySQL版）

`security_event` テーブルから統計データを日次バッチで集計する。
Flyway管理外のため手動で適用する。

処理内容・設計方針の詳細は [PostgreSQL版 README](../../../postgresql/statistics-aggregation/README.md) を参照。
本ドキュメントではMySQL固有の事項のみ記載する。

## ファイル一覧

| ファイル | 説明 |
|---------|------|
| `01_daily_statistics_aggregation.mysql.sql` | 集計ストアドプロシージャ + Event Scheduler |

## PostgreSQL版との違い

| 項目 | PostgreSQL | MySQL |
|------|-----------|-------|
| 関数形式 | `FUNCTION`（`RETURNS TABLE`で各ステップの結果を返却） | `PROCEDURE`（`CALL`で実行） |
| TZ変換 | `AT TIME ZONE` | `CONVERT_TZ` |
| 会計年度計算 | `MAKE_DATE` | `STR_TO_DATE` + `CONCAT` |
| 定期実行 | pg_cron | Event Scheduler |
| 冪等性 | `ON CONFLICT ... DO UPDATE` | `ON DUPLICATE KEY UPDATE` |
| 最適化版（02） | あり（事前計算版） | なし（01のみ） |

## 前提条件

### Event Scheduler の有効化

MySQL の Event Scheduler はデフォルトで**無効**。有効化が必要。

```sql
-- 現在の状態確認
SHOW VARIABLES LIKE 'event_scheduler';

-- 有効化（実行中のセッションから）
SET GLOBAL event_scheduler = ON;
```

永続化するには `my.cnf` に追記:
```ini
[mysqld]
event_scheduler = ON
```

### タイムゾーンテーブル

`CONVERT_TZ` を使用するため、MySQLにタイムゾーンデータが必要。

```bash
mysql_tzinfo_to_sql /usr/share/zoneinfo | mysql -u root mysql
```

## 適用手順

### 1. 集計プロシージャの登録

```bash
mysql -h <host> -u <user> -p <database> < 01_daily_statistics_aggregation.mysql.sql
```

Event Scheduler ジョブ `evt_daily_statistics_aggregation` も同時に登録される（毎日 04:00）。

### 2. 動作確認

```sql
-- 前日分を手動実行
CALL aggregate_daily_statistics(NULL);

-- 特定の日を指定
CALL aggregate_daily_statistics('2026-03-20');
```
