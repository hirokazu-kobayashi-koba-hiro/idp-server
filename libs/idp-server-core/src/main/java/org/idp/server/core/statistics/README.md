# Tenant Statistics - DB保存確認方法

## 概要
リアルタイム統計機能は、SecurityEventを受け取ってテナントごとの日次統計をDBに保存します。

## アーキテクチャ

```
SecurityEvent発行
  ↓
StatisticsEventListener (@Async)
  ↓
TenantStatisticsUpdateService
  ↓
TenantStatisticsDataCommandRepository
  ↓
PostgresqlExecutor / MysqlExecutor
  ↓
tenant_statistics_data テーブル
```

## DB保存確認方法

### 1. アプリケーション起動
```bash
# PostgreSQLを使用する場合
docker-compose up -d postgresql
./gradlew bootRun

# MySQLを使用する場合
docker-compose up -d mysql
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/idp ./gradlew bootRun
```

### 2. ログで確認
アプリケーションログに以下のようなメッセージが出力されます：

```
[INFO] TenantStatisticsUpdateService - Incrementing metric: tenant=xxx-xxx, date=2025-01-23, metric=login_success_count
[INFO] TenantStatisticsUpdateService - Adding daily active user: tenant=xxx-xxx, date=2025-01-23, user=yyy-yyy
```

### 3. DBで直接確認

#### PostgreSQLの場合
```sql
-- Docker コンテナに接続
docker exec -it idp-postgresql psql -U idp_app_user -d idp

-- 統計データを確認
SELECT
  id,
  tenant_id,
  stat_date,
  metrics,
  created_at
FROM tenant_statistics_data
ORDER BY stat_date DESC, created_at DESC
LIMIT 10;

-- 特定の日付の統計を確認
SELECT
  tenant_id,
  stat_date,
  metrics->>'login_success_count' as login_count,
  metrics->>'login_failure_count' as failure_count,
  jsonb_array_length(metrics->'daily_active_users') as dau
FROM tenant_statistics_data
WHERE stat_date = CURRENT_DATE;

-- メトリクスの詳細を確認
SELECT
  tenant_id,
  stat_date,
  jsonb_pretty(metrics) as metrics_detail
FROM tenant_statistics_data
WHERE stat_date = CURRENT_DATE;
```

#### MySQLの場合
```sql
-- Docker コンテナに接続
docker exec -it idp-mysql mysql -u idp_app_user -p idp

-- 統計データを確認
SELECT
  id,
  tenant_id,
  stat_date,
  metrics,
  created_at
FROM tenant_statistics_data
ORDER BY stat_date DESC, created_at DESC
LIMIT 10;

-- 特定の日付の統計を確認
SELECT
  tenant_id,
  stat_date,
  JSON_EXTRACT(metrics, '$.login_success_count') as login_count,
  JSON_EXTRACT(metrics, '$.login_failure_count') as failure_count,
  JSON_LENGTH(metrics, '$.daily_active_users') as dau
FROM tenant_statistics_data
WHERE stat_date = CURDATE();
```

## 保存されるデータ構造

### テーブル: tenant_statistics_data

| カラム | 型 | 説明 |
|--------|-----|------|
| id | UUID | 統計データID |
| tenant_id | UUID | テナントID |
| stat_date | DATE | 統計日付 |
| metrics | JSONB/JSON | メトリクスデータ |
| created_at | TIMESTAMP | 作成日時 |

### metricsフィールドの構造

```json
{
  "login_success_count": 150,
  "login_failure_count": 5,
  "tokens_issued": 120,
  "new_users": 3,
  "daily_active_users": [
    "user-uuid-1",
    "user-uuid-2",
    "user-uuid-3"
  ]
}
```

## 対応イベントタイプ

| SecurityEventType | メトリクス | 説明 |
|-------------------|------------|------|
| login_success | login_success_count + daily_active_users | ログイン成功 + DAU追跡 |
| password_failure | login_failure_count | ログイン失敗 |
| issue_token_success | tokens_issued | トークン発行 |
| user_signup | new_users | 新規ユーザー登録 |
| user_create | new_users | ユーザー作成 |

## テスト方法

### 単体テスト
```bash
./gradlew :libs:idp-server-use-cases:test --tests TenantStatisticsUpdateServiceTest
```

### 動作確認（手動）

1. アプリケーションを起動
2. ログインを実行
3. 上記のSQLでDBを確認
4. metricsカラムにデータが保存されていることを確認

## トラブルシューティング

### データが保存されない場合

1. **ログを確認**
   ```bash
   # TenantStatisticsUpdateServiceのログがあるか確認
   grep "Incrementing metric" logs/application.log
   grep "Adding daily active user" logs/application.log
   ```

2. **StatisticsEventListenerが動作しているか確認**
   ```bash
   # StatisticsEventListenerのログがあるか確認
   grep "StatisticsEventListener" logs/application.log
   ```

3. **テーブルが作成されているか確認**
   ```sql
   -- PostgreSQL
   \d tenant_statistics_data

   -- MySQL
   DESCRIBE tenant_statistics_data;
   ```

4. **権限を確認**
   ```sql
   -- PostgreSQL
   SELECT grantee, privilege_type
   FROM information_schema.role_table_grants
   WHERE table_name='tenant_statistics_data';
   ```

## パフォーマンス考慮事項

- `@Async`により非同期処理されるため、メインリクエストをブロックしません
- UPSERT（ON CONFLICT）により同じ日付のデータは更新されます
- `tenant_id, stat_date`の複合ユニークインデックスにより高速検索が可能です

## 関連ドキュメント

- DDL: `libs/idp-server-database/postgresql/V1_0_0__init_lib.sql`
- テスト: `libs/idp-server-use-cases/src/test/java/org/idp/server/usecases/statistics/`
