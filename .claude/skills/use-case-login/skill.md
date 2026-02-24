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

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | 認証方式 | パスワードのみ / 外部IdP連携 / 両方併用 | 認証ポリシー, フェデレーション設定 |
| 2 | パスワードポリシー | 最小文字数, 必須文字種 | テナント `identity_policy_config.password_policy` |
| 3 | アカウントロック | 失敗回数, ロック期間 | テナント `identity_policy_config.password_policy` |
| 4 | セッション管理 | 有効期限, cookie設定 | テナント `session_config` |
| 5 | トークン有効期限 | AT, IDT, RT | 認可サーバー `extension` |
| 6 | ユーザー登録スキーマ | 必須項目（email, name, phone等） | 認証メソッド設定（initial-registration） |

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

## 設定例ファイル参照

- テナント設定: `config/examples/e2e/tenant-1e68932e-ed4a-43e7-b412-460665e42df3/tenant.json`
- ユーザー登録: `config/examples/e2e/.../authentication-config/initial-registration/standard.json`
- 認証ポリシー: `config/examples/e2e/.../authentication-policy/oauth.json`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-1-foundation/05-user-registration.md`
- `documentation/docs/content_05_how-to/phase-1-foundation/07-authentication-policy.md`
- `documentation/docs/content_02_quickstart/quickstart-04-login.md`

$ARGUMENTS
