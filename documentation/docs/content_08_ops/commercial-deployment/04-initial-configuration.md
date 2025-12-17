# 初期設定 - 管理テナント・ユーザー作成

idp-server の初回デプロイ後、管理テナント・管理者ユーザー・管理クライアントを作成する手順を説明します。

---

## ステップ1: 前提条件の確認

以下を確認してください：

```bash
# 1. ヘルスチェック
curl ${AUTHORIZATION_SERVER_URL}/actuator/health
# → {"status":"UP"} が返ること

# 2. データベース接続確認
psql -h <DB_HOST> -U idp_app_user -d idpserver -c "SELECT 1;"
# → 接続成功すること

# 3. Redis接続確認
redis-cli -h <REDIS_HOST> ping
# → PONG が返ること
```

---

## ステップ2: 認証情報の準備

管理API認証に必要な情報を準備します：

```bash
# API Key/Secret を準備（UUID形式推奨）
export IDP_SERVER_API_KEY="your-api-key"
export IDP_SERVER_API_SECRET="your-api-secret"

# サーバーURLを設定
export AUTHORIZATION_SERVER_URL="https://idp.example.com"
```

**Note**: API Key/Secretの生成方法は [環境変数設定](./02-environment-variables.md#api認証キーシークレット設定) を参照してください。

---

## ステップ3: 設定ファイルの確認

初期化用のJSONファイルが準備されていることを確認します。

**前提**: 設定ファイルは事前に準備・レビュー済みであること

### 3-1. 設定ファイルの取得

```bash
# 例: S3から取得
aws s3 cp s3://your-config-bucket/production/initial.json ./initial.json

# 例: 設定管理リポジトリから取得
git clone <config-repo>
cp config-repo/production/initial.json ./initial.json
```

### 3-2. 内容の確認

```bash
# JSON構文チェック
cat initial.json | jq .
# → エラーが出ないこと

# 主要項目の確認
cat initial.json | jq '{
  org_id: .organization.id,
  tenant_id: .tenant.id,
  tenant_domain: .tenant.domain,
  user_email: .user.email
}'
```

**Note**: 設定ファイルの作成方法は [How-to: 初期テナント・ユーザー作成](../../content_05_how-to/phase-1-setup/how-to-01-create-initial-tenant-and-user.md) を参照してください。

---

## ステップ4: 初期化API実行

設定ファイルを使って初期化APIを実行します：

```bash
curl -X POST "${AUTHORIZATION_SERVER_URL}/v1/admin/initialization" \
  -u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
  -H "Content-Type: application/json" \
  --data @initial.json | jq
```

**期待されるレスポンス（200 OK）**:

```json
{
  "organization": {
    "id": "org-...",
    "name": "MyCompany"
  },
  "tenant": {
    "id": "tenant-...",
    "name": "main-tenant"
  },
  "user": {
    "sub": "user-...",
    "email": "admin@example.com"
  },
  "client": {
    "client_id": "client-..."
  }
}
```

---

## ステップ5: 検証

### 5-1. OAuthトークン取得テスト

```bash
# initial.jsonから値を取得して環境変数に設定
export TENANT_ID=$(cat initial.json | jq -r '.tenant.id')
export ADMIN_EMAIL=$(cat initial.json | jq -r '.user.email')
export ADMIN_PASSWORD=$(cat initial.json | jq -r '.user.raw_password')
export CLIENT_ID=$(cat initial.json | jq -r '.client.client_id')
export CLIENT_SECRET=$(cat initial.json | jq -r '.client.client_secret')

# テナント固有のトークンエンドポイント
export TOKEN_ENDPOINT="${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/tokens"

# パスワード認証でトークン取得
curl -X POST "${TOKEN_ENDPOINT}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=${ADMIN_EMAIL}" \
  -d "password=${ADMIN_PASSWORD}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "scope=openid profile email management" | jq
```

**期待結果**: `access_token` が返される

### 5-2. 管理API呼び出しテスト

```bash
# 取得したトークンを設定
export ACCESS_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# テナント一覧を取得
curl -X GET "${AUTHORIZATION_SERVER_URL}/v1/admin/tenants" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq
```

**期待結果**: 作成したテナントが含まれるリストが返される

---

## トラブルシューティング

### エラー: `401 Unauthorized`

**原因**: API Key/Secret が間違っている

**確認**:
```bash
echo -n "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" | base64
```

---

### エラー: `curl: (7) Failed to connect`

**原因**: アプリケーションが起動していない

**確認**:
```bash
curl -v ${AUTHORIZATION_SERVER_URL}/actuator/health
```

---

### エラー: `400 Bad Request`

**原因**: JSONが不正

**確認**:
```bash
cat initial.json | jq .
```

---

### エラー: `409 Conflict`

**原因**: 同じIDのリソースが既に存在

**対処**: 別のUUIDを使用するか、データベースをリセット（開発環境のみ）

---

## 関連ドキュメント

- [環境変数設定](./02-environment-variables.md)
- [データベース設定](./03-database.md)
- [運用ガイダンス](./05-operational-guidance.md)

### 詳細情報
- [How-to: 初期テナント・ユーザー作成](../../content_05_how-to/phase-1-setup/how-to-01-create-initial-tenant-and-user.md) - 各フィールドの詳細説明
- [Control Plane API - 初期化](../../content_06_developer-guide/02-control-plane/03-system-level-api.md) - API仕様の詳細

---

## 参考: 開発環境用スクリプト

ローカル開発環境では、以下のスクリプトで自動化できます：

```bash
./init-generate-env.sh      # .env生成
./init-admin-tenant-config.sh  # initial.json生成
./setup.sh                   # 初期化API実行
```

**Note**: 本番環境では、これらのスクリプトを直接使用せず、設定管理システム（Terraform、Ansible等）や Secrets Manager を使用してください。
