# 性能テスト実行ガイド

本ドキュメントでは、性能テストの実行手順を step-by-step で説明する。

---

## 前提条件

- Docker / Docker Compose がインストールされていること
- k6 がインストールされていること（`brew install k6`）
- Python 3 がインストールされていること
- PostgreSQL クライアント（psql）がインストールされていること

---

## 実行順序の概要

```
Phase 1: テストデータファイル生成（TSV/JSON）
    ↓
Phase 2: Docker環境起動
    ↓
Phase 3: シングルテナントデータ投入（100万件）
    ↓
Phase 4: テナント・クライアント確認
    ↓
Phase 1.5: マルチテナントデータ準備（必要な場合）
    ↓
Phase 5: 環境変数設定
    ↓
Phase 6-7: テスト実行
```

:::note マルチテナントテストを行う場合
Phase 1.5 は Phase 2（Docker起動）の後に実行してください。
既存テナント情報をDBから取得する必要があるためです。
:::

---

## Phase 1: テストデータ準備

### Step 1.1: ユーザーデータ生成（100万件）

既存データがない場合、またはデータを再生成する場合：

```bash
python3 ./performance-test/data/generate_users_100k.py
```

生成されるファイル：
- `performance-test/data/generated_users_100k.tsv` (約550MB, 100万件)
- `performance-test/data/generated_user_devices_100k.tsv` (約155MB, 100万件)

:::note
実行には数分かかります。進捗は1000件ごとに表示されます。
:::

### Step 1.2: テスト用JSONデータ生成

k6スクリプトで使用するテストユーザーデータを生成：

```bash
chmod +x ./performance-test/data/test-user.sh
./performance-test/data/test-user.sh all
```

生成されるファイル（各500件）：
| ファイル | login_hint パターン | 使用ユーザー範囲 |
|---------|-------------------|----------------|
| performance-test-user-device.json | device:{deviceId} | 1-500 |
| performance-test-user-sub.json | sub:{subject} | 501-1000 |
| performance-test-user-email.json | email:{email} | 1001-1500 |
| performance-test-user-phone.json | phone:{phone} | 1501-2000 |
| performance-test-user-ex-sub.json | ex-sub:{externalSubject} | 2001-2500 |

---

## Phase 1.5: マルチテナント・スケーラビリティテスト用データ準備

マルチテナントテストやスケーラビリティテストを行う場合は、追加のデータ準備が必要。

### Step 1.5.1: テナント登録

マルチテナントテスト用に複数のテナント（組織）を登録する。
オンボーディングAPIを使用して、各テナントに組織・認可サーバー・クライアント・ユーザーを一括作成する。

:::note 前提条件
- Docker環境が起動済みであること
- `.env` ファイルに管理者情報（`ADMIN_TENANT_ID`, `ADMIN_USER_EMAIL`, `ADMIN_USER_PASSWORD`, `ADMIN_CLIENT_ID`, `ADMIN_CLIENT_SECRET`）が設定されていること
- 先に Phase 2 (Docker環境起動) を実行すること
:::

```bash
# 5テナントを登録
./performance-test/data/register-tenants.sh -n 5

# ドライラン（実際には登録しない）
./performance-test/data/register-tenants.sh -n 5 -d true
```

パラメータ説明：
| パラメータ | 説明 |
|----------|------|
| -n | 登録するテナント数（必須） |
| -d | ドライラン（true/false、デフォルト: false） |
| -b | ベースURL（デフォルト: .envの`AUTHORIZATION_SERVER_URL`または`http://localhost:8080`） |

:::tip .envからの自動読み込み
管理者認証情報は `.env` ファイルから自動的に読み込まれます。手動での指定は不要です。
:::

生成されるファイル：
- `performance-test/data/performance-test-tenant.json` - テナント情報（各テナントの組織ID、テナントID、クライアント情報、ユーザー情報）

確認：
```bash
cat ./performance-test/data/performance-test-tenant.json
```

### Step 1.5.2: マルチテナント用ユーザーデータ生成

各テナントにユーザーデータを生成：

```bash
# デフォルト: 各テナント10万ユーザー
python3 ./performance-test/data/generate_multi_tenant_users.py

# ユーザー数を指定する場合
python3 ./performance-test/data/generate_multi_tenant_users.py --users-per-tenant 50000
```

生成されるファイル：
- `generated_multi_tenant_users.tsv` - 全テナントのユーザーデータ
- `generated_multi_tenant_devices.tsv` - 全テナントの認証デバイスデータ
- `performance-test-multi-tenant-users.json` - k6テスト用データ（各テナント500ユーザー）

### Step 1.5.3: マルチテナント用データ投入

```bash
# ユーザーデータ投入
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "\COPY idp_user (
  id, tenant_id, provider_id, external_user_id, name, email,
  email_verified, phone_number, phone_number_verified,
  preferred_username, status, authentication_devices
) FROM './performance-test/data/generated_multi_tenant_users.tsv'
WITH (FORMAT csv, HEADER false, DELIMITER E'\t')"

# 認証デバイスデータ投入
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "\COPY idp_user_authentication_devices (
  id, tenant_id, user_id, os, model, platform, locale,
  app_name, priority, available_methods,
  notification_token, notification_channel
) FROM './performance-test/data/generated_multi_tenant_devices.tsv'
WITH (FORMAT csv, HEADER false, DELIMITER E'\t')"
```

### スケーラビリティテスト用データパターン

| テストパターン | テナント数 | ユーザー数/テナント | 合計ユーザー | 用途 |
|--------------|----------|-------------------|-------------|------|
| シングルテナント | 1 | 100万 | 100万 | 基本性能測定 |
| マルチテナント（小） | 5 | 10万 | 50万 | マルチテナント性能 |
| マルチテナント（中） | 5 | 20万 | 100万 | スケーラビリティ評価 |
| データスケール | 1 | 10万/100万 | 可変 | データ量影響評価 |

---

## Phase 2: Docker環境起動

### Step 2.1: 性能テスト用構成で起動

```bash
# プロジェクトルートで実行
docker compose -f docker-compose.performance.yml up -d
```

または標準構成で起動：

```bash
docker compose up -d
```

### Step 2.2: 起動確認

```bash
# コンテナ状態確認
docker compose ps

# ヘルスチェック
curl http://localhost:8080/health
```

### Step 2.3: pg_stat_statements 有効化

```bash
docker exec -it <postgresql-container> psql -U idpserver -d idpserver -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
```

---

## Phase 3: データ投入

### Step 3.1: ユーザーデータ投入

```bash
# PostgreSQLコンテナに接続してデータ投入
docker exec -i <postgresql-container> psql -U idpserver -d idpserver -c "\COPY idp_user (
  id,
  tenant_id,
  provider_id,
  external_user_id,
  name,
  email,
  email_verified,
  phone_number,
  phone_number_verified,
  preferred_username,
  status,
  authentication_devices
) FROM STDIN WITH (FORMAT csv, HEADER false, DELIMITER E'\t')" < ./performance-test/data/generated_users_100k.tsv
```

または、ホストから直接：

```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "\COPY idp_user (
  id,
  tenant_id,
  provider_id,
  external_user_id,
  name,
  email,
  email_verified,
  phone_number,
  phone_number_verified,
  preferred_username,
  status,
  authentication_devices
) FROM './performance-test/data/generated_users_100k.tsv' WITH (FORMAT csv, HEADER false, DELIMITER E'\t')"
```

### Step 3.2: 認証デバイスデータ投入

```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "\COPY idp_user_authentication_devices (
  id,
  tenant_id,
  user_id,
  os,
  model,
  platform,
  locale,
  app_name,
  priority,
  available_methods,
  notification_token,
  notification_channel
) FROM './performance-test/data/generated_user_devices_100k.tsv' WITH (FORMAT csv, HEADER false, DELIMITER E'\t')"
```

### Step 3.3: 投入確認

```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "
SELECT
  (SELECT COUNT(*) FROM idp_user) as users,
  (SELECT COUNT(*) FROM idp_user_authentication_devices) as devices;
"
```

期待結果：
```
  users   | devices
----------+---------
 1000000  | 1000000
```

---

## Phase 4: テナント・クライアント登録

### Step 4.1: シングルテナント用テナント登録

シングルテナントテスト用のテナントが未登録の場合、E2Eテストを実行して登録する。

```bash
cd e2e && npm test
```

これにより、テスト用テナント `67e7eae6-62b0-4500-9eff-87459f63fc66` とクライアント `clientSecretPost` が登録される。

### Step 4.2: 登録確認

```bash
# テナント一覧を確認
docker exec postgres-primary psql -U idpserver -d idpserver -c "
SELECT tenant_id, COUNT(*) as client_count
FROM client_configuration
GROUP BY tenant_id
ORDER BY tenant_id
LIMIT 10;
"

# テスト用テナントのクライアント確認
docker exec postgres-primary psql -U idpserver -d idpserver -c "
SELECT id_alias, tenant_id
FROM client_configuration
WHERE id_alias = 'clientSecretPost'
LIMIT 5;
"
```

---

## Phase 5: 環境変数設定

```bash
export BASE_URL=http://localhost:8080
export TENANT_ID=67e7eae6-62b0-4500-9eff-87459f63fc66
export CLIENT_ID=clientSecretPost
export CLIENT_SECRET=clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890
export REDIRECT_URI=https://www.certification.openid.net/test/a/idp_oidc_basic/callback
```

確認：

```bash
echo "BASE_URL: $BASE_URL"
echo "TENANT_ID: $TENANT_ID"
```

---

## Phase 6: ストレステスト実行

### 結果ディレクトリ作成

```bash
mkdir -p performance-test/result/stress
mkdir -p performance-test/result/load
```

### Step 6.1: 認可リクエスト

```bash
k6 run --summary-export=./performance-test/result/stress/scenario-1-authorization-request.json \
  ./performance-test/stress/scenario-1-authorization-request.js
```

### Step 6.2: BC Request

```bash
k6 run --summary-export=./performance-test/result/stress/scenario-2-bc.json \
  ./performance-test/stress/scenario-2-bc.js
```

### Step 6.3: CIBA Flow（全パターン）

```bash
# device パターン
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-device.json \
  ./performance-test/stress/scenario-3-ciba-device.js

# sub パターン
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-sub.json \
  ./performance-test/stress/scenario-3-ciba-sub.js

# email パターン
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-email.json \
  ./performance-test/stress/scenario-3-ciba-email.js

# phone パターン
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-phone.json \
  ./performance-test/stress/scenario-3-ciba-phone.js

# ex-sub パターン
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-ex-sub.json \
  ./performance-test/stress/scenario-3-ciba-ex-sub.js
```

### Step 6.4: Token（各Grant Type）

```bash
# Password Grant
k6 run --summary-export=./performance-test/result/stress/scenario-4-token-password.json \
  ./performance-test/stress/scenario-4-token-password.js

# Client Credentials Grant
k6 run --summary-export=./performance-test/result/stress/scenario-5-token-client-credentials.json \
  ./performance-test/stress/scenario-5-token-client-credentials.js
```

### Step 6.5: その他

```bash
# JWKS
k6 run --summary-export=./performance-test/result/stress/scenario-6-jwks.json \
  ./performance-test/stress/scenario-6-jwks.js

# Token Introspection
k6 run --summary-export=./performance-test/result/stress/scenario-7-token-introspection.json \
  ./performance-test/stress/scenario-7-token-introspection.js

# Authentication Device
k6 run --summary-export=./performance-test/result/stress/scenario-8-authentication-device.json \
  ./performance-test/stress/scenario-8-authentication-device.js

# Identity Verification
k6 run --summary-export=./performance-test/result/stress/scenario-9-identity-verification-application.json \
  ./performance-test/stress/scenario-9-identity-verification-application.js
```

---

## Phase 7: ロードテスト実行

### Step 7.1: シングルテナントCIBA

```bash
k6 run ./performance-test/load/scenario-1-ciba-login.js
```

### Step 7.2: マルチテナントCIBA

```bash
k6 run ./performance-test/load/scenario-2-multi-ciba-login.js
```

### Step 7.3: ピーク負荷

```bash
k6 run ./performance-test/load/scenario-3-peak-login.js
```

### Step 7.4: フェデレーション

```bash
k6 run ./performance-test/load/scenario-4-federation.js
```

---

## Phase 8: 結果分析

### Step 8.1: pg_stat_statements確認

```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "
SELECT
  query,
  calls,
  round(total_exec_time::numeric, 2) as total_time_ms,
  round(mean_exec_time::numeric, 2) as mean_time_ms,
  rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
"
```

### Step 8.2: 統計リセット（次回テスト前）

```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "SELECT pg_stat_statements_reset();"
```

### Step 8.3: リソース監視

```bash
# テスト中に別ターミナルで実行
docker stats $(docker compose ps -q idp-server)
```

---

## Phase 9: クリーンアップ

### テストデータ削除

```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "
DELETE FROM idp_user_authentication_devices WHERE tenant_id = '67e7eae6-62b0-4500-9eff-87459f63fc66';
DELETE FROM idp_user WHERE tenant_id = '67e7eae6-62b0-4500-9eff-87459f63fc66';
"
```

### Docker環境停止

```bash
docker compose down
```

---

## 一括実行スクリプト

全ストレステストを順次実行する場合：

```bash
#!/bin/bash
set -e

RESULT_DIR="./performance-test/result/stress"
STRESS_DIR="./performance-test/stress"

mkdir -p $RESULT_DIR

scenarios=(
  "scenario-1-authorization-request"
  "scenario-2-bc"
  "scenario-3-ciba-device"
  "scenario-3-ciba-sub"
  "scenario-3-ciba-email"
  "scenario-3-ciba-phone"
  "scenario-3-ciba-ex-sub"
  "scenario-4-token-password"
  "scenario-5-token-client-credentials"
  "scenario-6-jwks"
  "scenario-7-token-introspection"
  "scenario-8-authentication-device"
  "scenario-9-identity-verification-application"
)

for scenario in "${scenarios[@]}"; do
  echo "=========================================="
  echo "Running: $scenario"
  echo "=========================================="
  k6 run --summary-export="${RESULT_DIR}/${scenario}.json" "${STRESS_DIR}/${scenario}.js"
  echo ""
  sleep 5  # クールダウン
done

echo "All stress tests completed!"
```

---

## トラブルシューティング

### データ投入エラー

```
ERROR: duplicate key value violates unique constraint
```

→ 既存データを削除してから再投入：
```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "TRUNCATE idp_user CASCADE;"
```

### k6接続エラー

```
ERRO[0001] Request Failed error="Get http://localhost:8080/..."
```

→ 環境変数とサーバー起動を確認：
```bash
echo $BASE_URL
curl $BASE_URL/health
```

### メモリ不足

```
java.lang.OutOfMemoryError: Java heap space
```

→ JVMヒープサイズを増加：
```yaml
environment:
  JAVA_TOOL_OPTIONS: "-Xms1g -Xmx4g"
```

---

## 関連ドキュメント

- [テスト環境](./01-test-environment.md)
- [ストレステスト結果](./02-stress-test-results.md)
- [チューニングガイド](./05-tuning-guide.md)
