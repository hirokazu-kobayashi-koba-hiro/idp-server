# 初期設定・ユーザー・ロール管理

idp-server の商用デプロイメント後の初期設定、管理者ユーザー作成、ロール・権限設定、テナント管理について説明します。

**情報源**: `/setup.sh`, `/config-sample/local/admin-tenant/initial.json`, `/.env`
**確認日**: 2025-10-03

---

## 🚀 初期セットアップ手順

### 1. 環境変数設定

初期化スクリプトを実行して `.env` ファイルを生成します。

```bash
./init.sh
```

**生成される内容**:
- API Key/Secret/Encryption Key（自動生成）
- 管理者ユーザー情報（自動生成）
- データベースパスワード（自動生成）

**生成後の編集**:

`.env` ファイルを開き、本番環境用に以下を修正：

```bash
# サーバー設定（修正必須）
IDP_SERVER_DOMAIN=https://your-domain.com/
ENV=production

# BASE_URL（修正必須）
BASE_URL=https://your-domain.com

# 管理者ユーザー（任意で変更）
ADMIN_USERNAME=admin
ADMIN_EMAIL=admin@your-domain.com

# その他の値（API Key, Secret, Password等）は自動生成された値をそのまま使用
```

**重要**: 自動生成された値は安全に保管してください。

### 2. 環境変数確認

```bash
set -a; [ -f .env ] && source .env; set +a

echo "ENV: $ENV"
echo "IDP_SERVER_DOMAIN: $IDP_SERVER_DOMAIN"
echo "API_KEY: ${IDP_SERVER_API_KEY:0:8}..."
```

### 3. アプリケーション起動確認

```bash
# ヘルスチェック
curl -v ${IDP_SERVER_DOMAIN}actuator/health
```

**期待結果**:
```json
{
  "status": "UP"
}
```

**注意**: 管理API認証の確認は次の初期化実行時に行われます。

---

## 👨‍💼 管理テナント・組織初期化

### setup.sh による初期化

**スクリプト実行**:

```bash
./setup.sh
```

**setup.sh の動作**:
```bash
#!/bin/zsh
# .env を読み込み
set -a; [ -f .env ] && source .env; set +a

# 管理テナント初期化API呼び出し
curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/initialization" \
  -u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
  -H "Content-Type:application/json" \
  --data @./config-sample/"${ENV}"/admin-tenant/initial.json | jq
```

**処理内容**:
1. `.env` から環境変数を読み込み
2. `ENV` 環境変数に基づいて `config-sample/${ENV}/admin-tenant/initial.json` を使用
3. `/v1/admin/initialization` エンドポイントに初期化リクエスト送信
4. **組織**、**テナント**、**認可サーバー**、**管理者ユーザー**、**管理クライアント**を一括作成

### initial.json 設定構造

**情報源**: `/config-sample/local/admin-tenant/initial.json:1-310`

初期化JSONファイルには以下の設定が含まれます:

```json
{
  "organization": {
    "id": "組織UUID",
    "name": "組織名",
    "description": "組織説明"
  },
  "tenant": {
    "id": "テナントUUID",
    "name": "テナント名",
    "domain": "https://your-domain.com",
    "authorization_provider": "idp-server",
    "database_type": "postgresql",
    "attributes": {
      "cookie_name": "ADMIN_TENANT_IDP_SERVER_SESSION",
      "use_secure_cookie": true,
      "allow_origins": ["https://admin.your-domain.com"],
      "security_event_log_format": "structured_json",
      "security_event_log_persistence_enabled": true
    }
  },
  "authorization_server": {
    "issuer": "https://your-domain.com/{tenant-id}",
    "authorization_endpoint": "https://your-domain.com/{tenant-id}/v1/authorizations",
    "token_endpoint": "https://your-domain.com/{tenant-id}/v1/tokens",
    "grant_types_supported": [
      "authorization_code",
      "refresh_token",
      "password",
      "client_credentials",
      "urn:openid:params:grant-type:ciba"
    ],
    "scopes_supported": [
      "openid", "profile", "email", "management"
    ],
    "extension": {
      "access_token_duration": 3600,
      "id_token_duration": 3600
    }
  },
  "user": {
    "sub": "ユーザーUUID",
    "provider_id": "idp-server",
    "name": "admin",
    "email": "admin@your-domain.com",
    "email_verified": true,
    "raw_password": "SecurePassword123!",
    "role": "Administrator"
  },
  "client": {
    "client_id": "クライアントUUID",
    "client_secret": "クライアントシークレット",
    "redirect_uris": ["https://admin.your-domain.com/callback"],
    "grant_types": ["authorization_code", "refresh_token"],
    "scope": "openid profile email management",
    "client_name": "Admin Client",
    "token_endpoint_auth_method": "client_secret_post"
  }
}
```

### 環境別設定

**設定ファイルの配置**:
```
config-sample/
├── local/
│   └── admin-tenant/
│       └── initial.json  # ローカル開発環境用
├── develop/
│   └── admin-tenant/
│       └── initial.json  # 開発環境用
└── production/
    └── admin-tenant/
        └── initial.json  # 本番環境用（作成が必要）
```

**商用環境用設定作成**:

```bash
# 本番環境用ディレクトリ作成
mkdir -p config-sample/production/admin-tenant

# テンプレートをコピー
cp config-sample/local/admin-tenant/initial.json \
   config-sample/production/admin-tenant/initial.json

# 本番環境用に編集
vim config-sample/production/admin-tenant/initial.json
```

**必須修正項目**:
1. **UUID生成**: `id`, `sub`, `client_id` を新規UUID（`uuidgen | tr A-Z a-z`）に変更
2. **ドメイン**: `domain`, `issuer`, エンドポイントURLを本番ドメインに変更
3. **シークレット**: `client_secret`, `raw_password` を安全な値に変更
4. **CORS設定**: `allow_origins` を本番フロントエンドURLに変更
5. **Cookie設定**: `use_secure_cookie` を `true` に設定
6. **JWKS**: 本番用キーペアを生成・設定（開発用キーの使用禁止）

---

## 🔍 初期化完了確認

初期化完了後、確認スクリプトを実行して動作確認します。

```bash
./setup-confirmation.sh
```

**確認内容**:
1. 環境変数の読み込み確認（ENV、BASE_URL）
2. OAuth パスワード認証でアクセストークン取得
3. 管理API呼び出し
   - テナント一覧の取得（管理テナントが存在するか）
   - ユーザー一覧の取得（管理者ユーザーが存在するか）
4. レスポンスタイムの確認

**成功の条件**:
- アクセストークンが取得できる
- テナント一覧に `initial.json` で設定したテナントが含まれる
- ユーザー一覧に `initial.json` で設定したユーザーが含まれる
- レスポンスタイムが妥当（< 1秒）

---

## 📋 初期設定チェックリスト

### 環境変数設定
- [ ] `.env` ファイル作成・権限設定（600）
- [ ] `IDP_SERVER_API_KEY`, `IDP_SERVER_API_SECRET` 設定
- [ ] `ENCRYPTION_KEY` 生成・設定（32バイト Base64）
- [ ] `ENV` 設定（production/develop/local）

### 設定ファイル準備
- [ ] `config-sample/${ENV}/admin-tenant/initial.json` 作成
- [ ] UUID生成・設定（organization, tenant, user, client）
- [ ] ドメイン・エンドポイントURL設定
- [ ] シークレット・パスワード設定
- [ ] JWKS キーペア生成・設定

### 初期化実行
- [ ] `./setup.sh` 実行成功
- [ ] 組織作成確認（`/v1/admin/organizations` で確認）
- [ ] テナント作成確認（`/v1/admin/tenants` で確認）
- [ ] 管理者ユーザー作成確認
- [ ] 管理クライアント作成確認

### 動作確認
- [ ] ヘルスチェック成功（DB, Redis）
- [ ] 管理API認証成功
- [ ] パスワード認証テスト成功
- [ ] クライアント認証テスト成功

### セキュリティ確認
- [ ] 本番環境で開発用JWKS使用していない
- [ ] `use_secure_cookie=true` 設定（HTTPS環境）
- [ ] CORS `allow_origins` が適切に設定
- [ ] パスワード・シークレットが安全な値

---

## 🔗 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [環境変数設定](./02-environment-variables.md)
- [データベース設定](./03-database.md)
- [検証・テストチェックリスト](./05-verification-checklist.md)
- [運用ガイダンス](./06-operational-guidance.md)
