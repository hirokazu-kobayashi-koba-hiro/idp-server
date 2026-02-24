# Passwordless (FIDO2/WebAuthn) Use Case Template

FIDO2/WebAuthn/Passkey によるパスワードレス認証を設定するテンプレートセット。

> Claude Code を使用している場合は `/use-case-setup` でヒアリング付きの対話型セットアップが利用できます。

> **templates vs examples**: このテンプレート（`config/templates/`）はゼロからの完全セットアップ用です。Organization・テナント・クライアントを一括作成します。既存テナントへの追加設定には `config/examples/passwordless-fido2/` を使用してください。

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式 | FIDO2/WebAuthn OR パスワード (移行期パターン) |
| パスワードポリシー | 8文字以上、文字種制約なし |
| アカウントロック | 5回失敗で15分ロック |
| セッション有効期限 | 24時間 |
| ユーザー登録必須項目 | email, password, name |
| 最大デバイス数 | 5 |
| デバイスシークレット有効期限 | 24時間 |

## ファイル構成

| ファイル | 用途 | API |
|---------|------|-----|
| `onboarding-template.json` | Organization + Organizer Tenant + Admin User + Client | `POST /v1/management/onboarding` |
| `public-tenant-template.json` | Public Tenant（パスワードポリシー + セッション設定 + デバイスルール） | `POST /v1/management/tenants` |
| `authentication-config-initial-registration.json` | ユーザー登録スキーマ | `POST /v1/management/tenants/{id}/authentication-configurations` |
| `authentication-config-fido2-template.json` | FIDO2/WebAuthn認証設定 | `POST /v1/management/tenants/{id}/authentication-configurations` |
| `authentication-policy.json` | FIDO2 or パスワード認証ポリシー | `POST /v1/management/tenants/{id}/authentication-policies` |
| `authentication-policy-fido2-registration.json` | FIDO2デバイス登録ポリシー | `POST /v1/management/tenants/{id}/authentication-policies` |
| `public-client-template.json` | アプリケーションクライアント | `POST /v1/management/tenants/{id}/clients` |
| `setup.sh` | 上記を順番に実行するスクリプト | - |

## セットアップ手順

### 前提条件

- idp-serverが起動済み
- システム管理者テナントが存在（初期セットアップ済み）
- `.env` に管理者認証情報を設定済み

### 自動セットアップ

```bash
# .env を設定（プロジェクトルートに配置）
# AUTHORIZATION_SERVER_URL=http://localhost:8080
# ADMIN_TENANT_ID=<system-admin-tenant-id>
# ADMIN_USER_EMAIL=<admin-email>
# ADMIN_USER_PASSWORD=<admin-password>
# ADMIN_CLIENT_ID=<admin-client-id>
# ADMIN_CLIENT_SECRET=<admin-client-secret>

# JWKS を生成して配置（初回のみ）
# cp path/to/your/jwks.json ./jwks.json

# セットアップ実行
./setup.sh

# ドライラン（実際には作成しない）
./setup.sh --dry-run
```

### 環境変数でカスタマイズ

```bash
# 組織名を指定
ORGANIZATION_NAME="my-company" ./setup.sh

# FIDO2設定をカスタマイズ
FIDO2_RP_ID="myapp.example.com" \
FIDO2_RP_NAME="My App" \
FIDO2_ALLOWED_ORIGIN="https://myapp.example.com" \
MAX_DEVICES=3 \
./setup.sh

# クライアント設定をカスタマイズ
CLIENT_NAME="My Web App" \
REDIRECT_URI="https://myapp.example.com/callback" \
./setup.sh

# パスワードポリシーとトークン有効期限をカスタマイズ
PASSWORD_MIN_LENGTH=12 \
PASSWORD_REQUIRE_UPPERCASE=true \
PASSWORD_REQUIRE_NUMBER=true \
PASSWORD_MAX_ATTEMPTS=3 \
SESSION_TIMEOUT_SECONDS=3600 \
ACCESS_TOKEN_DURATION=1800 \
REFRESH_TOKEN_DURATION=604800 \
./setup.sh
```

### カスタマイズ可能な環境変数

#### FIDO2/WebAuthn 設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `FIDO2_RP_ID` | `local.dev` | Relying Party ID |
| `FIDO2_RP_NAME` | `Local Dev IDP` | Relying Party 表示名 |
| `FIDO2_ALLOWED_ORIGIN` | `${AUTHORIZATION_SERVER_URL}` | WebAuthn 許可オリジン |
| `MAX_DEVICES` | `5` | ユーザーあたりの最大デバイス数 |
| `DEVICE_SECRET_EXPIRES_IN_SECONDS` | `86400` | デバイスシークレット有効期限 |

#### セッション設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `SESSION_TIMEOUT_SECONDS` | `86400` | セッション有効期限（秒） |

#### パスワードポリシー

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `PASSWORD_MIN_LENGTH` | `8` | 最小文字数 |
| `PASSWORD_MAX_LENGTH` | `72` | 最大文字数 |
| `PASSWORD_REQUIRE_UPPERCASE` | `false` | 大文字必須 |
| `PASSWORD_REQUIRE_LOWERCASE` | `false` | 小文字必須 |
| `PASSWORD_REQUIRE_NUMBER` | `false` | 数字必須 |
| `PASSWORD_REQUIRE_SPECIAL_CHAR` | `false` | 特殊文字必須 |
| `PASSWORD_MAX_HISTORY` | `0` | パスワード履歴保存数 |
| `PASSWORD_MAX_ATTEMPTS` | `5` | ロックまでの失敗回数 |
| `PASSWORD_LOCKOUT_DURATION_SECONDS` | `900` | ロック期間（秒） |

#### トークン有効期限

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `ACCESS_TOKEN_DURATION` | `3600` | アクセストークン有効期限（秒） |
| `ID_TOKEN_DURATION` | `3600` | IDトークン有効期限（秒） |
| `REFRESH_TOKEN_DURATION` | `86400` | リフレッシュトークン有効期限（秒） |

#### ユーザー登録

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `REGISTRATION_REQUIRED_FIELDS` | `email,password,name` | 登録時の必須項目（カンマ区切り） |

## セットアップフロー

```
Step 1: システム管理者トークン取得
  └─ POST /{admin-tenant}/v1/tokens (password grant)

Step 2: オンボーディング（システムレベルAPI）
  └─ POST /v1/management/onboarding
     → Organization + Organizer Tenant + Admin User + Client 作成

Step 3: ORGANIZER管理者トークン取得
  └─ POST /{organizer-tenant}/v1/tokens (password grant)
     → 以降は組織レベルAPIを使用

Step 4: パブリックテナント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants
     → パスワードポリシー + セッション設定 + デバイスルール付きテナント作成

Step 5: 認証設定作成 - initial-registration（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → ユーザー登録スキーマ（email + password + name）

Step 6: 認証設定作成 - FIDO2（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → FIDO2/WebAuthn認証設定（RP ID, RP Name, Allowed Origins）

Step 7: 認証ポリシー作成 - oauth（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → FIDO2 or パスワードで認証成功とするポリシー

Step 8: 認証ポリシー作成 - fido2-registration（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → FIDO2デバイス登録ポリシー

Step 9: クライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → アプリケーション用OAuthクライアント
```
