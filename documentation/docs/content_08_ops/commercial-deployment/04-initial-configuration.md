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

**Note**: 設定ファイルの作成方法は [How-to: サーバーセットアップ](../../content_05_how-to/phase-1-foundation/01-server-setup.md) および [How-to: 組織初期化](../../content_05_how-to/phase-1-foundation/02-organization-initialization.md) を参照してください。

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

## ステップ5: システム設定（Trusted Proxy・SSRF保護）

初期化完了後、System Configuration API でセキュリティ関連のシステム設定を行います。

**前提**: ステップ4で取得した管理者トークンを使用します。

```bash
# ステップ4の初期化結果から管理者トークンを取得
export TENANT_ID=$(cat initial.json | jq -r '.tenant.id')
export ADMIN_EMAIL=$(cat initial.json | jq -r '.user.email')
export ADMIN_PASSWORD=$(cat initial.json | jq -r '.user.raw_password')
export CLIENT_ID=$(cat initial.json | jq -r '.client.client_id')
export CLIENT_SECRET=$(cat initial.json | jq -r '.client.client_secret')

export ACCESS_TOKEN=$(curl -s -X POST "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=${ADMIN_EMAIL}" \
  -d "password=${ADMIN_PASSWORD}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "scope=openid management" | jq -r '.access_token')
```

### 5-1. Trusted Proxy設定

ロードバランサーやリバースプロキシの背後で運用する場合、`X-Forwarded-For` ヘッダーからクライアントIPを正しく解決するために Trusted Proxy を設定します。

**未設定の場合**: クライアントIPが全てLB/プロキシのIPとなり、監査ログやレートリミットが正しく機能しません。

```bash
curl -X PUT "${AUTHORIZATION_SERVER_URL}/v1/management/system-configurations" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "trusted_proxies": {
      "enabled": true,
      "addresses": [
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16"
      ]
    }
  }' | jq
```

**設定項目:**

| フィールド | 説明 | デフォルト | 本番推奨 |
|-----------|------|----------|---------|
| `enabled` | Trusted Proxy機能の有効化 | `false` | `true` |
| `addresses` | 信頼するプロキシのIP/CIDR一覧 | `[]` | VPCのCIDR範囲 |

**`addresses` の設定例:**

| 環境 | 推奨設定 |
|------|---------|
| AWS ALB (VPC: 10.0.0.0/16) | `["10.0.0.0/16"]` |
| GCP Cloud Load Balancing | `["35.191.0.0/16", "130.211.0.0/22"]` |
| Kubernetes (Pod CIDR) | `["10.244.0.0/16"]`（クラスタ設定に依存） |

### 5-2. SSRF保護設定

外部HTTPリクエスト（Webhook、フェデレーション連携等）時に、プライベートIPへのアクセスをブロックします。

**未設定の場合**: Security Event HookやフェデレーションのコールバックURLにプライベートIPを指定されると、内部ネットワークへのアクセスが可能になります。

```bash
curl -X PUT "${AUTHORIZATION_SERVER_URL}/v1/management/system-configurations" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "trusted_proxies": {
      "enabled": true,
      "addresses": [
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16"
      ]
    },
    "ssrf_protection": {
      "enabled": true,
      "bypass_hosts": [],
      "allowed_hosts": []
    }
  }' | jq
```

**Note**: System Configuration API は全設定を一括更新するため、Trusted Proxy設定も含めて送信します。

**設定項目:**

| フィールド | 説明 | デフォルト | 本番推奨 |
|-----------|------|----------|---------|
| `enabled` | SSRF保護の有効化 | `false` | `true` |
| `bypass_hosts` | 保護を迂回するホスト名一覧 | `[]` | `[]`（空推奨） |
| `allowed_hosts` | 許可する外部ホスト名一覧（指定時はallowlist方式） | `[]` | 連携先のみ指定 |

**動作モード:**

| `allowed_hosts` | 動作 |
|----------------|------|
| `[]`（空） | プライベートIPのみブロック（blocklist方式） |
| ホスト名指定あり | 指定されたホストのみ許可（allowlist方式、より安全） |

**ブロック対象:** RFC1918プライベートIP（`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`）、ループバック（`127.0.0.0/8`）、クラウドメタデータ（`169.254.169.254`）、IPv6プライベート範囲

**allowlist方式の設定例（推奨）:**
```json
{
  "ssrf_protection": {
    "enabled": true,
    "bypass_hosts": [],
    "allowed_hosts": [
      "accounts.google.com",
      "login.microsoftonline.com",
      "hooks.slack.com"
    ]
  }
}
```

### 5-3. 設定確認

```bash
curl -X GET "${AUTHORIZATION_SERVER_URL}/v1/management/system-configurations" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq
```

**期待結果**: `trusted_proxies.enabled: true` と `ssrf_protection.enabled: true` が返される

**Note**: 設定は5分間キャッシュされます。設定更新時はキャッシュが自動的に無効化されますが、複数インスタンス環境では全インスタンスへの反映に最大5分かかります。

---

## ステップ6: 検証

### 6-1. OAuthトークン取得テスト

```bash
# ステップ5で設定済みの環境変数を使用（未設定の場合は再設定）
# export TENANT_ID, ADMIN_EMAIL, ADMIN_PASSWORD, CLIENT_ID, CLIENT_SECRET

# パスワード認証でトークン取得（profile, email スコープ付き）
curl -X POST "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=${ADMIN_EMAIL}" \
  -d "password=${ADMIN_PASSWORD}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "scope=openid profile email management" | jq
```

**期待結果**: `access_token` が返される

### 6-2. 管理API呼び出しテスト

```bash
# ステップ5で取得済みの ACCESS_TOKEN を使用（期限切れの場合は6-1で再取得）

# initial.jsonから組織IDを取得
export ORGANIZATION_ID=$(cat initial.json | jq -r '.organization.id')

# テナント一覧を取得
curl -X GET "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants" \
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
- [How-to: サーバーセットアップ](../../content_05_how-to/phase-1-foundation/01-server-setup.md) - サーバー初期設定の詳細
- [How-to: 組織初期化](../../content_05_how-to/phase-1-foundation/02-organization-initialization.md) - 組織・テナント・ユーザー作成の詳細
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
