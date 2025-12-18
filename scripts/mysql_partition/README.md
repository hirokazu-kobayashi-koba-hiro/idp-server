# MySQL パーティショニング設定

MySQLの統計テーブルおよびセキュリティイベントテーブルにパーティショニングを適用し、大量データの効率的な管理を実現します。

## スクリプト一覧

| ファイル名 | 種別 | 説明 |
|-----------|------|------|
| `setup-partitions.sql` | セットアップ | Stored ProcedureとEvent Schedulerの設定 |
| `migrate-to-partitions.sql` | マイグレーション | 既存テーブルをパーティションテーブルに変換 |
| `verify-partitions.sh` | 検証 | 統計テーブルのパーティション動作検証 |
| `verify-security-event-partitions.sh` | 検証 | セキュリティイベントテーブルのパーティション動作検証 |
| `cleanup-partitions.sh` | クリーンアップ | 検証環境の削除 |
| `partition-lifecycle-test.sh` | ライフサイクル | パーティション作成/削除のライフサイクル検証 |
| `event-scheduler-autorun-test.sh` | 自動実行 | Event Schedulerの自動実行検証 |
| `statistics-users-benchmark.sh` | ベンチマーク | パーティション vs 通常テーブルの性能比較 |

## 概要

### 統計テーブル

| テーブル | パーティション戦略 | 保持期間 | PostgreSQLと同等 |
|---------|------------------|---------|-----------------|
| statistics_daily_users | RANGE (日単位) | 90日 | ✅ |
| statistics_monthly_users | RANGE (月単位) | 13ヶ月 | ✅ |
| statistics_yearly_users | RANGE (月単位) | 60ヶ月 | ✅ |

### セキュリティイベントテーブル

| テーブル | パーティション戦略 | 保持期間 | PostgreSQLと同等 |
|---------|------------------|---------|-----------------|
| security_event | RANGE (日単位) | 90日 | ✅ |
| security_event_hook_results | RANGE (日単位) | 90日 | ✅ |

## PostgreSQL (pg_partman) との比較

| 項目 | PostgreSQL | MySQL |
|------|------------|-------|
| パーティション管理 | pg_partman拡張 | Event Scheduler + Stored Procedure |
| 自動パーティション作成 | pg_partman内蔵 | 自作Procedureで実現 |
| 保持期間管理 | pg_partman設定 | 自作Procedureで実現 |
| デフォルトパーティション | `DEFAULT`パーティション | `MAXVALUE`パーティション（p_future） |
| 実行履歴テーブル | pg_cron: `cron.job_run_details` | **なし**（`LAST_EXECUTED`のみ） |

## 前提条件

- MySQL 8.0以上
- Event Schedulerが有効

```sql
-- Event Scheduler有効化（my.cnfまたは起動時）
SET GLOBAL event_scheduler = ON;

-- 確認
SHOW VARIABLES LIKE 'event_scheduler';
```

## セットアップ

### 1. 新規インストール

新規環境では、DDLにパーティション定義が含まれているため追加作業は不要です。

### 2. 既存環境のマイグレーション

```bash
# バックアップを取ってからマイグレーション実行
mysql -u <user> -p <database> < scripts/mysql_partition/migrate-to-partitions.sql
```

### 3. パーティション管理設定

```bash
# Stored ProcedureとEventの設定
mysql -u <user> -p <database> < scripts/mysql_partition/setup-partitions.sql
```

### 4. 動作検証

```bash
# パーティション動作の検証
./scripts/mysql_partition/verify-partitions.sh

# ライフサイクルテスト
./scripts/mysql_partition/partition-lifecycle-test.sh

# Event Scheduler自動実行テスト
./scripts/mysql_partition/event-scheduler-autorun-test.sh

# パフォーマンスベンチマーク
./scripts/mysql_partition/statistics-users-benchmark.sh          # デフォルト: 10万行
./scripts/mysql_partition/statistics-users-benchmark.sh 500000   # 50万行
```

## 自動メンテナンス

Event Schedulerにより毎日AM 3:00に以下が実行されます：

1. **新規パーティション作成**: 今後3ヶ月分を事前作成
2. **古いパーティション削除**: 保持期間を超えたパーティションを削除

```sql
-- 手動でメンテナンス実行
CALL maintain_statistics_partitions();
```

## 運用コマンド

### パーティション状態確認

```sql
SELECT TABLE_NAME, PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'statistics%'
ORDER BY TABLE_NAME, PARTITION_NAME;
```

### Event確認

```sql
SHOW EVENTS;

-- 特定のEventの詳細
SHOW CREATE EVENT evt_maintain_statistics_partitions;
```

### 手動パーティション作成

```sql
-- 特定月のパーティションを作成
CALL create_daily_users_partition('2025-03-01');
CALL create_monthly_users_partition('2025-03-01');
CALL create_yearly_users_partition('2025-03-01');
```

### 手動パーティション削除

```sql
-- 保持期間を指定して削除
CALL drop_old_daily_users_partitions(90);    -- 90日以上前を削除
CALL drop_old_monthly_users_partitions(13);  -- 13ヶ月以上前を削除
CALL drop_old_yearly_users_partitions(60);   -- 60ヶ月以上前を削除
```

## パフォーマンス効果

### ベンチマーク結果（10,000行/テーブル）

#### statistics_daily_users (DAU)

| テスト | 通常(ms) | パーティション(ms) | 結果 |
|-------|---------|------------------|------|
| 特定日のDAU COUNT | 231 | 253 | 9% 低下 |
| 1ヶ月間のDAU推移 | 246 | 295 | 19% 低下 |
| 全期間ユニークユーザー | 235 | 245 | 4% 低下 |
| 特定ユーザー活動履歴 | 242 | 250 | 3% 低下 |

#### statistics_monthly_users (MAU)

| テスト | 通常(ms) | パーティション(ms) | 結果 |
|-------|---------|------------------|------|
| 特定月のMAU COUNT | 315 | 254 | **19% 改善** |
| 年間MAU推移 | 258 | 248 | **3% 改善** |
| 複数年のMAU比較 | 257 | 311 | 21% 低下 |
| 6ヶ月間ユニークユーザー | 259 | 245 | **5% 改善** |

#### statistics_yearly_users (YAU)

| テスト | 通常(ms) | パーティション(ms) | 結果 |
|-------|---------|------------------|------|
| 特定年のYAU COUNT | 251 | 252 | 同等 |
| 複数年のYAU推移 | 303 | 271 | **10% 改善** |
| 直近アクティブユーザー | 263 | 313 | 19% 低下 |
| 非アクティブユーザー | 258 | 261 | 1% 低下 |

#### 考察

小規模データ（1万行）では明確な差が出にくい理由：
- パーティションテーブルは複数パーティションの管理コストがある
- データが少ないとフルスキャンでも十分高速
- 小規模データはメモリキャッシュに収まりやすい

**パーティショニングの真価が発揮されるケース：**
- 100万行以上のデータ量
- 古いデータの定期削除（DROP PARTITIONで50〜500倍高速）
- 日付範囲を条件としたクエリ（パーティションプルーニング）

### パーティションプルーニング

パーティションキー（日付カラム）を条件に含むクエリで大幅な性能改善が期待できます：

```sql
-- パーティションプルーニングが効く
SELECT * FROM statistics_daily_users
WHERE stat_date >= '2025-01-01' AND stat_date < '2025-02-01';

-- パーティションプルーニングが効かない（全パーティションスキャン）
SELECT * FROM statistics_daily_users WHERE user_id = 'xxx';
```

### 削除性能

| 操作 | 100万行削除時間 |
|-----|---------------|
| DELETE文 | 2〜5秒 |
| DROP PARTITION | 0.01〜0.1秒 |

**DROP PARTITION**はDELETEの50〜500倍高速です。

## トラブルシューティング

### Event Schedulerが動作しない

```sql
-- 確認
SHOW VARIABLES LIKE 'event_scheduler';

-- 有効化
SET GLOBAL event_scheduler = ON;

-- my.cnf に永続化
[mysqld]
event_scheduler = ON
```

### パーティションが作成されない

```sql
-- エラーログ確認
SHOW WARNINGS;

-- 権限確認（ALTER TABLE権限が必要）
SHOW GRANTS FOR CURRENT_USER();
```

### データ挿入時にエラー

`p_future`パーティション（MAXVALUE）が存在すれば、対応するパーティションがなくてもデータは挿入されます。

```
ERROR 1526: Table has no partition for value ...
```

このエラーが出た場合は、`p_future`パーティションが存在するか確認してください。

## 関連ドキュメント

- [MySQL 8.0 Partitioning](https://dev.mysql.com/doc/refman/8.0/en/partitioning.html)
- [MySQL Event Scheduler](https://dev.mysql.com/doc/refman/8.0/en/event-scheduler.html)
- PostgreSQL版: `scripts/pg_partman/`
