# MFA (Password + SMS OTP) Use Case Template

パスワード + SMS OTP の二要素認証を設定するテンプレートセット。

> Claude Code を使用している場合は `/use-case-setup` でヒアリング付きの対話型セットアップが利用できます。

> **templates vs examples**: このテンプレート（`config/templates/`）はゼロからの完全セットアップ用です。Organization・テナント・クライアントを一括作成します。既存テナントへの追加設定には `config/examples/mfa-sms/` を使用してください。

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式 | パスワード + SMS OTP (二要素認証) |
| パスワードポリシー | 8文字以上、文字種制約なし |
| アカウントロック | 5回失敗で15分ロック |
| セッション有効期限 | 24時間 |
| ユーザー登録必須項目 | email, password, name |

> **外部API委譲（http_request）モード**: SMS送信機能はidp-serverに内蔵されていないため、デフォルトで外部APIに委譲する設定になっています。ローカル開発では付属のモックサーバー（`mock-server.js`、ポート4004）を使用します。本番環境では `SMS_SERVICE_CHALLENGE_URL` / `SMS_SERVICE_VERIFY_URL` を実際のSMS送信サービス（Twilio等）のURLに変更してください。

## ファイル構成

| ファイル | 用途 | API |
|---------|------|-----|
| `onboarding-template.json` | Organization + Organizer Tenant + Admin User + Client | `POST /v1/management/onboarding` |
| `public-tenant-template.json` | Public Tenant（パスワードポリシー + セッション設定） | `POST /v1/management/tenants` |
| `authentication-config-initial-registration.json` | ユーザー登録スキーマ | `POST /v1/management/tenants/{id}/authentication-configurations` |
| `authentication-config-sms.json` | SMS OTP 認証設定（外部API委譲モード） | `POST /v1/management/tenants/{id}/authentication-configurations` |
| `mock-server.js` | SMS送信サービスのモックサーバー（ポート4004） | - |
| `authentication-policy.json` | パスワード + SMS OTP MFA 認証ポリシー | `POST /v1/management/tenants/{id}/authentication-policies` |
| `public-client-template.json` | アプリケーションクライアント | `POST /v1/management/tenants/{id}/clients` |
| `setup.sh` | 上記を順番に実行するスクリプト | - |

## セットアップ手順

### 前提条件

- idp-serverが起動済み
- システム管理者テナントが存在（初期セットアップ済み）
- `.env` に管理者認証情報を設定済み
- **モックサーバー起動済み**（または外部SMS送信サービスのURL設定済み）

```bash
# モックサーバー起動（別ターミナル）
node config/templates/use-cases/mfa-sms/mock-server.js
```

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

# テナントIDを指定
PUBLIC_TENANT_ID="custom-tenant-id" ./setup.sh

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

#### 外部SMSサービス設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `SMS_SERVICE_CHALLENGE_URL` | `http://host.docker.internal:4004/sms/challenge` | SMS送信（チャレンジ）APIのURL |
| `SMS_SERVICE_VERIFY_URL` | `http://host.docker.internal:4004/sms/verify` | SMS検証APIのURL |

> デフォルトは付属のモックサーバーを使用します。本番環境ではTwilio等の実サービスURLに変更してください。

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
     → パスワードポリシー + セッション設定付きテナント作成

Step 5: 認証設定作成 - 初期登録（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → ユーザー登録スキーマ（email + password + name）

Step 6: 認証設定作成 - SMS OTP（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → SMS OTP 認証（外部API委譲モード + モックサーバー）

Step 7: 認証ポリシー作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → パスワード AND SMS OTP で認証成功とするMFAポリシー

Step 8: クライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → アプリケーション用OAuthクライアント
```
