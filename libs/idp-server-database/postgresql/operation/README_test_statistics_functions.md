# Statistics Functions Test

V0_10_0__statistics.sql で定義されたPostgreSQL関数の動作確認テストです。

## テスト対象の関数

### 1. cleanup_old_statistics(retention_days INTEGER)
指定した保持期間より古い統計データを削除する関数

**使用例:**
```sql
-- 365日より古いデータを削除
SELECT cleanup_old_statistics(365);
```

### 2. get_dau_count(p_tenant_id UUID, p_stat_date DATE)
指定したテナント・日付のDAU（Daily Active Users）をカウントする関数

**使用例:**
```sql
-- 特定テナントの今日のDAUを取得
SELECT get_dau_count('aaaaaaaa-0000-0000-0000-000000000000'::UUID, CURRENT_DATE);
```

### 3. cleanup_old_dau(retention_days INTEGER)
指定した保持期間より古いDAUデータを削除する関数

**使用例:**
```sql
-- 90日より古いDAUデータを削除
SELECT cleanup_old_dau(90);
```

### 4. latest_statistics ビュー
過去30日間の統計データから主要メトリクスを抽出するビュー

**使用例:**
```sql
-- 最新30日間の統計を確認
SELECT * FROM latest_statistics WHERE tenant_id = 'your-tenant-id';
```

## 実行方法

### 方法1: シェルスクリプトで実行（推奨）
```bash
cd /Users/hirokazu.kobayashi/work/idp-server
./libs/idp-server-database/postgresql/operation/run_test_statistics_functions.sh
```

### 方法2: Dockerから直接実行
```bash
cd /Users/hirokazu.kobayashi/work/idp-server
docker exec -i postgres-primary psql -U idp_app_user -d idpserver -f libs/idp-server-database/postgresql/operation/test_statistics_functions.sql
```

### 方法3: ローカルpsqlで実行
```bash
cd /Users/hirokazu.kobayashi/work/idp-server
psql -U idp_app_user -d idpserver -f libs/idp-server-database/postgresql/operation/test_statistics_functions.sql
```

## テスト内容

### Test 1: cleanup_old_statistics
- 100日前、50日前、10日前のテストデータを挿入
- 30日より古いデータを削除（100日前と50日前が削除対象）
- 10日前のデータは保持されることを確認

**期待結果:** 2件削除、1件残存

### Test 2: get_dau_count
- 今日の日付で3ユーザーを挿入
- 今日のDAUカウントが3であることを確認
- 昨日のDAUカウントが0であることを確認

**期待結果:** 今日=3、昨日=0

### Test 3: cleanup_old_dau
- 100日前、50日前のDAUデータを挿入
- 30日より古いDAUデータを削除
- 今日のDAUデータは保持されることを確認

**期待結果:** 2件削除、3件残存（今日の分）

### Test 4: latest_statistics ビュー
- JSONB形式のmetricsからdau、login_success_rate等を抽出
- 過去30日間のデータが正しく表示されることを確認

**期待結果:** JOSNBからメトリクスが正しく抽出される

## テストデータのクリーンアップ

テスト実行後、必要に応じてテストデータを削除してください：

```sql
SET app.tenant_id = 'aaaaaaaa-0000-0000-0000-000000000000';

-- テスト用の統計データを削除
DELETE FROM tenant_statistics
WHERE tenant_id = 'aaaaaaaa-0000-0000-0000-000000000000';

-- テスト用のDAUデータを削除
DELETE FROM daily_active_users
WHERE tenant_id = 'aaaaaaaa-0000-0000-0000-000000000000';
```

## 注意事項

- テストはテナントID `aaaaaaaa-0000-0000-0000-000000000000` を使用します
- 本番環境では実行しないでください
- テスト実行前に `app.tenant_id` が設定されます（RLS対応）
- テストデータは `ON CONFLICT DO NOTHING` で重複挿入を回避しています
