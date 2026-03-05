---
name: use-case-login
description: ログイン（パスワードのみ）ユースケースの設定ガイド。パスワードポリシー、セッション設定、ユーザー登録スキーマ、トークン有効期限のヒアリングと環境変数マッピングを提供。
---

# ログイン（パスワードのみ）

## テンプレート実行

**テンプレート**: `config/templates/use-cases/login-password-only/`

```bash
# 基本実行
bash config/templates/use-cases/login-password-only/setup.sh

# カスタマイズ例
ORGANIZATION_NAME="acme-corp" \
NEW_ADMIN_EMAIL="admin@acme.com" \
NEW_ADMIN_PASSWORD="SecurePass123!" \
PASSWORD_MIN_LENGTH=12 \
PASSWORD_REQUIRE_UPPERCASE=true \
PASSWORD_REQUIRE_NUMBER=true \
PASSWORD_MAX_ATTEMPTS=3 \
SESSION_TIMEOUT_SECONDS=3600 \
ACCESS_TOKEN_DURATION=1800 \
CLIENT_NAME="ACME Web App" \
REDIRECT_URI="https://app.acme.com/callback" \
bash config/templates/use-cases/login-password-only/setup.sh

# ドライラン（実際には作成しない）
bash config/templates/use-cases/login-password-only/setup.sh --dry-run
```

## 細かい設定 Q&A（逆引き）

**「やりたいこと → 設定」の対応表**: [qa.md](./qa.md)

ユーザーが具体的にやりたいことを言った場合は、qa.md を参照して該当するQ&Aの設定キー＋値を提示すること。

## 設定変更 × 挙動確認（ハンズオン）

**「設定を変えて → 挙動が変わることを体験する」実験ガイド**: `config/templates/use-cases/login-password-only/EXPERIMENTS.md`

設定の効果を手元で確認したい場合はこのガイドを案内すること。

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | 認証方式 | パスワードのみ / 外部IdP連携 / 両方併用 | 認証ポリシー, フェデレーション設定 |
| 2 | パスワードポリシー | 最小文字数, 必須文字種 | テナント `identity_policy_config.password_policy` |
| 3 | アカウントロック | 失敗回数, ロック期間 | テナント `identity_policy_config.password_policy` |
| 4 | セッション管理 | 有効期限, cookie設定 | テナント `session_config` |
| 5 | トークン有効期限 | AT, IDT, RT | 認可サーバー `extension` |
| 6 | ユーザー登録スキーマ | 必須項目（email, name, phone等） | 認証メソッド設定（initial-registration） |
| 7 | 返すクレーム | 標準OIDC / カスタム追加 | 認可サーバー claims_supported |

## ヒアリング結果 → 環境変数マッピング

### セッション設定

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| セッション有効期限（秒） | `SESSION_TIMEOUT_SECONDS` | `86400` |

### パスワードポリシー

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| パスワード最小文字数 | `PASSWORD_MIN_LENGTH` | `8` |
| パスワード最大文字数 | `PASSWORD_MAX_LENGTH` | `72` |
| 大文字必須 | `PASSWORD_REQUIRE_UPPERCASE` | `false` |
| 小文字必須 | `PASSWORD_REQUIRE_LOWERCASE` | `false` |
| 数字必須 | `PASSWORD_REQUIRE_NUMBER` | `false` |
| 特殊文字必須 | `PASSWORD_REQUIRE_SPECIAL_CHAR` | `false` |
| パスワード履歴保存数 | `PASSWORD_MAX_HISTORY` | `0` |
| ロックまでの失敗回数 | `PASSWORD_MAX_ATTEMPTS` | `5` |
| ロック期間（秒） | `PASSWORD_LOCKOUT_DURATION_SECONDS` | `900` |

### トークン有効期限

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| AT有効期限（秒） | `ACCESS_TOKEN_DURATION` | `3600` |
| IDT有効期限（秒） | `ID_TOKEN_DURATION` | `3600` |
| RT有効期限（秒） | `REFRESH_TOKEN_DURATION` | `86400` |

### ユーザー登録

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| 登録必須項目 | `REGISTRATION_REQUIRED_FIELDS` | `email,password,name` |

---

## 設定対象と手順

### 1. パスワードポリシー設定（テナント更新）

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}`

```json
{
  "identity_policy_config": {
    "identity_unique_key_type": "EMAIL_OR_EXTERNAL_USER_ID",
    "password_policy": {
      "min_length": 8,
      "max_length": 72,
      "require_uppercase": false,
      "require_lowercase": false,
      "require_number": false,
      "require_special_char": false,
      "max_history": 0,
      "max_attempts": 5,
      "lockout_duration_seconds": 900
    }
  }
}
```

**ヒアリング結果の反映先**:

| ヒアリング項目 | JSONキー | 値の例 |
|--------------|---------|-------|
| 最小文字数 | `min_length` | `8`, `12` |
| 大文字必須 | `require_uppercase` | `true`/`false` |
| 小文字必須 | `require_lowercase` | `true`/`false` |
| 数字必須 | `require_number` | `true`/`false` |
| 特殊文字必須 | `require_special_char` | `true`/`false` |
| ロック条件（失敗回数） | `max_attempts` | `5`, `10` |
| ロック期間（秒） | `lockout_duration_seconds` | `900`（15分）, `3600`（1時間） |
| パスワード履歴保存数 | `max_history` | `0`（なし）, `5` |

### 2. セッション設定（テナント更新）

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}`

```json
{
  "session_config": {
    "cookie_name": "session",
    "cookie_same_site": "Lax",
    "use_secure_cookie": true,
    "use_http_only_cookie": true,
    "cookie_path": "/",
    "timeout_seconds": 3600,
    "switch_policy": "SWITCH_ALLOWED"
  }
}
```

**ヒアリング結果の反映先**:

| ヒアリング項目 | JSONキー | 値の例 |
|--------------|---------|-------|
| セッション有効期限（秒） | `timeout_seconds` | `1800`, `3600`, `86400` |
| セッション切り替え | `switch_policy` | `STRICT` / `SWITCH_ALLOWED` / `MULTI_SESSION` |
| cookieドメイン | `cookie_domain` | `example.com` |

### 3. ユーザー登録設定（認証メソッド設定）

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations`

```json
{
  "id": "{uuid}",
  "type": "initial-registration",
  "interactions": {
    "initial-registration": {
      "request": {
        "schema": {
          "type": "object",
          "required": ["email", "password", "name"],
          "properties": {
            "email": {
              "type": "string",
              "format": "email",
              "maxLength": 255
            },
            "password": {
              "type": "string",
              "pattern": "^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()]).+$",
              "minLength": 8,
              "maxLength": 64
            },
            "name": { "type": "string", "maxLength": 255 },
            "given_name": { "type": "string", "maxLength": 255 },
            "family_name": { "type": "string", "maxLength": 255 },
            "phone_number": { "type": "string", "maxLength": 20 }
          }
        }
      }
    }
  }
}
```

**ヒアリング結果の反映先**:

| ヒアリング項目 | JSONキー | 値の例 |
|--------------|---------|-------|
| 必須項目 | `required` 配列 | `["email", "password"]`, `["email", "password", "name"]` |
| パスワード正規表現 | `password.pattern` | `^(?=.*[A-Z])(?=.*\\d).+$` |
| 電話番号も取得 | `properties` に `phone_number` 追加 | `{ "type": "string" }` |

### 4. 認証ポリシー設定（パスワードのみ）

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-policies`

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "password_only",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password", "initial-registration"],
      "success_conditions": {
        "any_of": [
          [
            {
              "path": "$.password-authentication.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ],
          [
            {
              "path": "$.initial-registration.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ]
        ]
      }
    }
  ]
}
```

### 5. クレーム設定（認可サーバー更新）

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

> **重要**: この設定が無いと UserInfo / ID Token が `sub` のみしか返さない。

```json
{
  "claims_supported": [
    "sub", "iss", "auth_time", "acr",
    "name", "given_name", "family_name", "nickname", "preferred_username", "middle_name",
    "profile", "picture", "website",
    "email", "email_verified",
    "gender", "birthdate", "zoneinfo", "locale", "updated_at",
    "address", "phone_number", "phone_number_verified"
  ]
}
```

**スコープとクレームの対応**:

| スコープ | 返されるクレーム |
|---------|-----------------|
| `profile` | name, given_name, family_name, middle_name, nickname, preferred_username, profile, picture, website, gender, birthdate, zoneinfo, locale, updated_at |
| `email` | email, email_verified |
| `phone` | phone_number, phone_number_verified |
| `address` | address |

### 6. フェデレーション設定（ソーシャルログイン）

> ヒアリング項目 #1 で「外部IdP連携」または「両方併用」を選択した場合に設定する。

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/federation-configurations`

**Google連携例**:
```json
{
  "type": "oidc",
  "sso_provider": "google",
  "enabled": true,
  "payload": {
    "type": "standard",
    "provider": "standard",
    "issuer": "https://accounts.google.com",
    "issuer_name": "google",
    "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
    "token_endpoint": "https://oauth2.googleapis.com/token",
    "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo",
    "jwks_uri": "https://www.googleapis.com/oauth2/v3/certs",
    "client_id": "{Google Client ID}",
    "client_secret": "{Google Client Secret}",
    "redirect_uri": "https://{domain}/{tenant-id}/v1/authorizations/federations/oidc/callback",
    "scopes_supported": ["openid", "profile", "email"],
    "userinfo_mapping_rules": [
      { "from": "$.http_request.response_body.sub", "to": "external_user_id" },
      { "from": "$.http_request.response_body.email", "to": "email" },
      { "from": "$.http_request.response_body.name", "to": "name" },
      { "from": "$.http_request.response_body.picture", "to": "picture" }
    ]
  }
}
```

**Azure AD連携例**:
```json
{
  "type": "oidc",
  "sso_provider": "azure_ad",
  "enabled": true,
  "payload": {
    "type": "standard",
    "provider": "standard",
    "issuer": "https://login.microsoftonline.com/{azure-tenant-id}/v2.0",
    "issuer_name": "azure_ad",
    "authorization_endpoint": "https://login.microsoftonline.com/{azure-tenant-id}/oauth2/v2.0/authorize",
    "token_endpoint": "https://login.microsoftonline.com/{azure-tenant-id}/oauth2/v2.0/token",
    "userinfo_endpoint": "https://graph.microsoft.com/oidc/userinfo",
    "client_id": "{Azure Client ID}",
    "client_secret": "{Azure Client Secret}",
    "redirect_uri": "https://{domain}/{tenant-id}/v1/authorizations/federations/oidc/callback",
    "scopes_supported": ["openid", "profile", "email"],
    "userinfo_mapping_rules": [
      { "from": "$.http_request.response_body.sub", "to": "external_user_id" },
      { "from": "$.http_request.response_body.email", "to": "email" },
      { "from": "$.http_request.response_body.name", "to": "name" }
    ]
  }
}
```

### 7. クライアントでフェデレーション有効化

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/clients/{client-id}`

```json
{
  "extension": {
    "available_federations": [
      {
        "id": "{federation-config-id}",
        "type": "oidc",
        "sso_provider": "google"
      }
    ]
  }
}
```

## 設定確認チェックリスト

### パスワードログイン

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 1 | `claims_supported` が設定済み | 認可サーバー | 未設定で UserInfo/ID Token が `sub` のみ |
| 2 | `ui_config.base_url` が認証UIのオリジン | テナント `ui_config` | APIサーバーURLを設定してしまう |
| 3 | `cors_config` に全フィールド設定 | テナント `cors_config` | `allow_origins` だけで `allow_headers`, `allow_methods`, `allow_credentials` が抜ける |
| 4 | `failure_conditions` / `lock_conditions` 設定済み | 認証ポリシー | 未設定だと認証失敗でアカウントロックされない |
| 5 | `registration_required_fields` が要件と一致 | setup.sh 環境変数 | `email,password` のみで `name` が抜ける等 |

### ソーシャルログイン（追加チェック）

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 6 | フェデレーション設定の `client_id` が実際の値 | federation-config | `your-google-client-id` のまま |
| 7 | フェデレーション設定の `redirect_uri` のドメインが `AUTHORIZATION_SERVER_URL` と一致 | federation-config | ドメイン不一致でコールバック失敗 |
| 8 | クライアントに `available_federations` が設定済み | クライアント設定 | 未設定だとソーシャルログインボタンが表示されない |
| 9 | 認証ポリシーの `success_conditions` に `$.oidc-{provider}.success_count` が含まれる | 認証ポリシー | 未設定だとソーシャル認証成功後に `authentication is required` エラー |
| 10 | Google Cloud Console の「承認済みリダイレクトURI」に federation の `redirect_uri` が登録済み | Google Cloud Console | 未登録で `redirect_uri_mismatch` エラー |

### 動作確認時のprompt値

| テスト | prompt値 | 目的 |
|--------|---------|------|
| ユーザー登録 | `prompt=create` | Sign Up画面を直接表示 |
| 再ログイン | `prompt=login` | 既存セッションを無視して再認証を強制 |

## 設定例ファイル参照

- テンプレート: `config/templates/use-cases/login-password-only/`, `config/templates/use-cases/login-social/`
- テナント設定: `config/examples/e2e/tenant-1e68932e-ed4a-43e7-b412-460665e42df3/tenant.json`
- ユーザー登録: `config/examples/e2e/.../authentication-config/initial-registration/standard.json`
- 認証ポリシー: `config/examples/e2e/.../authentication-policy/oauth.json`
- フェデレーション: `config/examples/e2e/.../federation/oidc/google.json`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-1-foundation/05-user-registration.md`
- `documentation/docs/content_05_how-to/phase-1-foundation/07-authentication-policy.md`
- `documentation/docs/content_05_how-to/phase-3-advanced/01-federation-setup.md`
- `documentation/docs/content_02_quickstart/quickstart-04-login.md`

$ARGUMENTS
