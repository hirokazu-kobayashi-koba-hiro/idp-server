# 初期設定・ユーザー・ロール管理

idp-server の商用デプロイメント後の初期設定、管理者ユーザー作成、ロール・権限設定、テナント管理について説明します。

---

## 🚀 初期セットアップ手順

### 1. 環境変数設定確認

```bash
# 必須環境変数の確認
echo "IDP_SERVER_API_KEY: $IDP_SERVER_API_KEY"
echo "IDP_SERVER_API_SECRET: $IDP_SERVER_API_SECRET"
echo "ENCRYPTION_KEY: $ENCRYPTION_KEY"
echo "SERVER_URL: $SERVER_URL"

# データベース接続確認
echo "DB_WRITER_URL: $DB_WRITER_URL"
echo "DB_READER_URL: $DB_READER_URL"
```

### 2. ヘルスチェック・動作確認

```bash
# アプリケーションヘルスチェック
curl -v $SERVER_URL/actuator/health

# データベース接続確認
curl -v $SERVER_URL/actuator/health/db

# Redis 接続確認
curl -v $SERVER_URL/actuator/health/redis
```

### 3. 管理API認証確認

```bash
# API 認証テスト
curl -X GET "$SERVER_URL/v1/admin/health" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json"
```

---

## 👨‍💼 管理テナント・管理者ユーザー作成

### 管理テナント初期化

```bash
# 管理テナント初期化スクリプト実行
curl -X POST "$SERVER_URL/v1/admin/initialization" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "tenant": {
      "identifier": "admin-tenant",
      "name": "管理テナント",
      "description": "システム管理用テナント",
      "status": "active",
      "attributes": {
        "tenant_type": "admin",
        "timezone": "Asia/Tokyo",
        "locale": "ja-JP"
      }
    },
    "admin_user": {
      "username": "system_admin",
      "email": "admin@your-domain.com",
      "given_name": "System",
      "family_name": "Administrator",
      "password": "GENERATE_SECURE_PASSWORD",
      "roles": ["system_admin", "tenant_admin"]
    },
    "client": {
      "client_id": "admin-console",
      "client_name": "管理コンソール",
      "client_secret": "GENERATE_CLIENT_SECRET",
      "grant_types": ["authorization_code", "refresh_token"],
      "response_types": ["code"],
      "scopes": ["openid", "profile", "email", "admin"],
      "redirect_uris": ["https://admin.your-domain.com/callback"]
    }
  }' | jq
```

### 管理テナント設定ファイル使用

既存の設定ファイルを使用する場合:

```bash
# config-sample の管理テナント設定を使用
curl -X POST "$SERVER_URL/v1/admin/initialization" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data @./config-sample/local/admin-tenant/initial.json | jq
```

---

## 🏢 テナント管理

### 新規テナント作成

```bash
# 企業テナント作成例
TENANT_ID=$(uuidgen | tr A-Z a-z)

curl -X POST "$SERVER_URL/v1/admin/tenants" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data "{
    \"identifier\": \"$TENANT_ID\",
    \"name\": \"株式会社サンプル\",
    \"description\": \"サンプル企業のテナント\",
    \"status\": \"active\",
    \"attributes\": {
      \"company_name\": \"株式会社サンプル\",
      \"industry\": \"technology\",
      \"timezone\": \"Asia/Tokyo\",
      \"locale\": \"ja-JP\",
      \"branding_enabled\": \"true\",
      \"custom_domain\": \"login.sample-corp.com\"
    },
    \"configuration\": {
      \"security_policy\": {
        \"password_policy\": \"strong\",
        \"mfa_required\": true,
        \"session_timeout\": 3600
      },
      \"audit_logging\": {
        \"enabled\": true,
        \"retention_days\": 90
      }
    }
  }" | jq
```

### テナント設定更新

```bash
# テナント属性更新
curl -X PUT "$SERVER_URL/v1/admin/tenants/$TENANT_ID" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "attributes": {
      "branding_logo_url": "https://cdn.sample-corp.com/logo.png",
      "branding_primary_color": "#1976d2",
      "branding_secondary_color": "#424242",
      "custom_terms_url": "https://sample-corp.com/terms",
      "custom_privacy_url": "https://sample-corp.com/privacy"
    }
  }' | jq
```

---

## 👥 ユーザー管理

### 管理者ユーザー作成

```bash
# テナント管理者作成
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/users" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "username": "tenant_admin",
    "email": "admin@sample-corp.com",
    "given_name": "田中",
    "family_name": "太郎",
    "password": "SecurePassword123!",
    "email_verified": true,
    "status": "active",
    "roles": ["tenant_admin", "user_manager"],
    "attributes": {
      "department": "IT部",
      "employee_id": "EMP001",
      "hire_date": "2024-01-15"
    }
  }' | jq
```

### 一般ユーザー作成

```bash
# 一般ユーザー作成
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/users" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "username": "john.doe",
    "email": "john.doe@sample-corp.com",
    "given_name": "John",
    "family_name": "Doe",
    "password": "TempPassword123!",
    "email_verified": false,
    "status": "active",
    "roles": ["user"],
    "attributes": {
      "department": "営業部",
      "employee_id": "EMP002",
      "manager": "tenant_admin"
    },
    "password_change_required": true
  }' | jq
```

### ユーザー一括インポート

```bash
# CSV ファイルからユーザー一括作成
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/users/bulk-import" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@users.csv" \
  -F "options={\"send_welcome_email\":true,\"password_change_required\":true}" | jq
```

---

## 🛡️ ロール・権限管理

### 標準ロール定義

#### システム管理者ロール
```bash
# システム管理者ロール作成
curl -X POST "$SERVER_URL/v1/admin/roles" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "name": "system_admin",
    "display_name": "システム管理者",
    "description": "システム全体の管理権限",
    "permissions": [
      "system:read",
      "system:write",
      "tenant:create",
      "tenant:read",
      "tenant:update",
      "tenant:delete",
      "user:create",
      "user:read",
      "user:update",
      "user:delete",
      "client:create",
      "client:read",
      "client:update",
      "client:delete",
      "audit:read"
    ],
    "scope": "system"
  }' | jq
```

#### テナント管理者ロール
```bash
# テナント管理者ロール作成
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/roles" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "name": "tenant_admin",
    "display_name": "テナント管理者",
    "description": "テナント内の管理権限",
    "permissions": [
      "tenant:read",
      "tenant:update",
      "user:create",
      "user:read",
      "user:update",
      "user:delete",
      "client:create",
      "client:read",
      "client:update",
      "client:delete",
      "audit:read"
    ],
    "scope": "tenant"
  }' | jq
```

#### ユーザー管理者ロール
```bash
# ユーザー管理者ロール作成
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/roles" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "name": "user_manager",
    "display_name": "ユーザー管理者",
    "description": "ユーザー管理権限",
    "permissions": [
      "user:create",
      "user:read",
      "user:update",
      "user:password_reset",
      "user:mfa_reset",
      "audit:read"
    ],
    "scope": "tenant"
  }' | jq
```

#### 一般ユーザーロール
```bash
# 一般ユーザーロール作成
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/roles" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "name": "user",
    "display_name": "一般ユーザー",
    "description": "基本ユーザー権限",
    "permissions": [
      "profile:read",
      "profile:update",
      "mfa:manage",
      "session:manage"
    ],
    "scope": "user"
  }' | jq
```

### カスタムロール作成

```bash
# 監査担当者ロール
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/roles" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "name": "auditor",
    "display_name": "監査担当者",
    "description": "監査・ログ閲覧権限",
    "permissions": [
      "audit:read",
      "log:read",
      "report:generate"
    ],
    "scope": "tenant"
  }' | jq
```

---

## 🔧 クライアント・アプリケーション設定

### Webアプリケーション クライアント

```bash
# SPA (Single Page Application) クライアント
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/clients" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "client_id": "webapp-spa",
    "client_name": "企業Webアプリケーション",
    "client_type": "public",
    "grant_types": ["authorization_code", "refresh_token"],
    "response_types": ["code"],
    "scopes": ["openid", "profile", "email"],
    "redirect_uris": [
      "https://app.sample-corp.com/callback",
      "https://app.sample-corp.com/silent-renew"
    ],
    "post_logout_redirect_uris": [
      "https://app.sample-corp.com/logout"
    ],
    "require_pkce": true,
    "token_endpoint_auth_method": "none",
    "id_token_signed_response_alg": "RS256",
    "userinfo_signed_response_alg": "RS256"
  }' | jq
```

### モバイルアプリケーション クライアント

```bash
# モバイルアプリ クライアント
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/clients" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "client_id": "mobile-app",
    "client_name": "企業モバイルアプリ",
    "client_type": "public",
    "grant_types": ["authorization_code", "refresh_token"],
    "response_types": ["code"],
    "scopes": ["openid", "profile", "email", "offline_access"],
    "redirect_uris": [
      "com.sample-corp.mobile://callback"
    ],
    "require_pkce": true,
    "token_endpoint_auth_method": "none",
    "refresh_token_rotation": true,
    "refresh_token_expiration": 2592000
  }' | jq
```

### APIクライアント (Machine-to-Machine)

```bash
# M2M クライアント
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/clients" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "client_id": "api-service",
    "client_name": "APIサービス",
    "client_type": "confidential",
    "client_secret": "GENERATE_SECURE_SECRET",
    "grant_types": ["client_credentials"],
    "scopes": ["api:read", "api:write"],
    "token_endpoint_auth_method": "client_secret_basic",
    "access_token_lifetime": 3600
  }' | jq
```

---

## 🔐 認証・セキュリティ設定

### 認証ポリシー設定

```bash
# パスワードポリシー設定
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/authentication-policies" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "name": "password_policy",
    "type": "password",
    "configuration": {
      "min_length": 12,
      "require_uppercase": true,
      "require_lowercase": true,
      "require_numbers": true,
      "require_special_chars": true,
      "forbidden_passwords": ["password", "123456", "qwerty"],
      "max_failed_attempts": 5,
      "lockout_duration": 900,
      "password_history": 5,
      "password_expiration_days": 90
    }
  }' | jq

# MFA ポリシー設定
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/authentication-policies" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "name": "mfa_policy",
    "type": "mfa",
    "configuration": {
      "required": true,
      "allowed_methods": ["webauthn", "totp", "sms"],
      "backup_codes_enabled": true,
      "remember_device_days": 30,
      "grace_period_days": 7
    }
  }' | jq
```

### WebAuthn/FIDO2 設定

```bash
# WebAuthn 設定
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/authentication-config" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "type": "webauthn",
    "configuration": {
      "rp_id": "sample-corp.com",
      "rp_name": "株式会社サンプル",
      "require_resident_key": false,
      "user_verification": "preferred",
      "attestation": "none",
      "timeout": 60000,
      "algorithms": ["ES256", "RS256"]
    }
  }' | jq
```

---

## 📧 通知・連携設定

### メール設定 (AWS SES)

```bash
# メール設定
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/authentication-config" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "type": "email",
    "configuration": {
      "provider": "aws_ses",
      "aws_region": "us-east-1",
      "from_email": "noreply@sample-corp.com",
      "from_name": "株式会社サンプル",
      "templates": {
        "welcome": "welcome_template_id",
        "password_reset": "password_reset_template_id",
        "email_verification": "email_verification_template_id"
      }
    }
  }' | jq
```

### Slack 通知設定

```bash
# Slack 通知設定
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/security-event-hooks" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "name": "slack_notifications",
    "type": "slack",
    "configuration": {
      "webhook_url": "https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK",
      "channel": "#security-alerts",
      "events": [
        "user_login_failed",
        "user_locked",
        "admin_action",
        "security_policy_violation"
      ],
      "enabled": true
    }
  }' | jq
```

---

## 🔍 設定検証・テスト

### 認証テスト

```bash
# パスワード認証テスト
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=john.doe" \
  -d "password=TempPassword123!" \
  -d "client_id=webapp-spa" \
  -d "scope=openid profile email"

# クライアント認証テスト
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'api-service:CLIENT_SECRET' | base64)" \
  -d "grant_type=client_credentials" \
  -d "scope=api:read"
```

### 管理機能テスト

```bash
# ユーザー一覧取得テスト
curl -X GET "$SERVER_URL/v1/admin/tenants/$TENANT_ID/users" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json"

# 監査ログ取得テスト
curl -X GET "$SERVER_URL/v1/admin/tenants/$TENANT_ID/audit-logs?limit=10" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json"
```

---

## 📋 初期設定チェックリスト

### 基本設定
- [ ] 管理テナント初期化完了
- [ ] システム管理者ユーザー作成完了
- [ ] 管理コンソールクライアント設定完了
- [ ] API 認証・接続確認完了

### テナント・ユーザー管理
- [ ] 本番テナント作成完了
- [ ] テナント管理者作成完了
- [ ] 基本ロール定義完了
- [ ] ユーザーインポート手順確認

### クライアント・アプリケーション
- [ ] Webアプリケーション クライアント設定
- [ ] モバイルアプリケーション クライアント設定
- [ ] API クライアント設定
- [ ] PKCE・セキュリティ設定確認

### 認証・セキュリティ
- [ ] パスワードポリシー設定
- [ ] MFA ポリシー設定
- [ ] WebAuthn/FIDO2 設定
- [ ] セッション・ロックアウト設定

### 通知・連携
- [ ] メール送信設定・テスト
- [ ] Slack 通知設定・テスト
- [ ] Webhook 連携設定
- [ ] 監査ログ設定確認

### 動作確認
- [ ] 認証フロー動作確認
- [ ] 管理機能動作確認
- [ ] エラーハンドリング確認
- [ ] パフォーマンス確認

---

## 🔗 関連ドキュメント

- [前提条件](./01-prerequisites.md)
- [環境変数・セキュリティパラメータ](./02-environment-variables.md)
- [AWS インフラ構築](./03-aws-infrastructure.md)
- [検証・テストチェックリスト](./05-verification-checklist.md)
- [運用ガイダンス](./06-operational-guidance.md)