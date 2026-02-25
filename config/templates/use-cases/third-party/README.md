# Third Party Integration Template

サードパーティ連携（Web/モバイル/M2M）テンプレート。

3種類のクライアント（Web Confidential、Mobile Public+PKCE、M2M client_credentials）を登録し、トークン戦略設定とカスタムAPIスコープを構成します。

> Claude Code を使用している場合は `/use-case-setup` でヒアリング付きの対話型セットアップが利用できます。

> **templates vs examples**: このテンプレート（`config/templates/`）はゼロからの完全セットアップ用です。Organization・テナント・クライアントを一括作成します。既存テナントへの追加設定には `config/examples/third-party/` を使用してください。

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式 | パスワードのみ |
| パスワードポリシー | 8文字以上、文字種制約なし |
| アカウントロック | 5回失敗で15分ロック |
| セッション有効期限 | 24時間 |
| トークン戦略 | AT=1800s, IDT=3600s, RT=604800s, ローテーション有効 |
| カスタムスコープ | api:read, api:write |
| クライアント種別 | Web（Confidential）、Mobile（Public+PKCE）、M2M（client_credentials） |

## ファイル構成

| ファイル | 用途 | API |
|---------|------|-----|
| `onboarding-template.json` | Organization + Organizer Tenant + Admin User + Client | `POST /v1/management/onboarding` |
| `public-tenant-template.json` | Public Tenant（トークン戦略 + カスタムスコープ） | `POST /v1/management/tenants` |
| `authentication-config-initial-registration.json` | ユーザー登録スキーマ | `POST /v1/management/tenants/{id}/authentication-configurations` |
| `authentication-policy.json` | パスワードのみ認証ポリシー | `POST /v1/management/tenants/{id}/authentication-policies` |
| `web-client-template.json` | Webアプリケーションクライアント（Confidential） | `POST /v1/management/tenants/{id}/clients` |
| `mobile-client-template.json` | モバイルアプリケーションクライアント（Public + PKCE） | `POST /v1/management/tenants/{id}/clients` |
| `m2m-client-template.json` | M2Mクライアント（client_credentials） | `POST /v1/management/tenants/{id}/clients` |
| `setup.sh` | 上記を順番に実行するスクリプト | - |
| `verify.sh` | セットアップ後の動作確認スクリプト | - |

## クライアント種別

### Web Client（Confidential）
- `token_endpoint_auth_method`: `client_secret_basic`
- `application_type`: `web`
- `grant_types`: `authorization_code`, `refresh_token`
- `scope`: `openid profile email api:read`

### Mobile Client（Public + PKCE）
- `token_endpoint_auth_method`: `none`（PKCE必須）
- `application_type`: `native`
- `grant_types`: `authorization_code`, `refresh_token`
- `scope`: `openid profile email api:read`

### M2M Client（Client Credentials）
- `token_endpoint_auth_method`: `client_secret_basic`
- `application_type`: `web`
- `grant_types`: `client_credentials`
- `scope`: `api:read api:write`（カスタマイズ可能）

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

# 動作確認
./verify.sh
./verify.sh --org my-organization
```

### 環境変数でカスタマイズ

```bash
# 組織名を指定
ORGANIZATION_NAME="my-company" ./setup.sh

# トークン戦略をカスタマイズ
ACCESS_TOKEN_DURATION=900 \
REFRESH_TOKEN_DURATION=2592000 \
ROTATE_REFRESH_TOKEN=true \
REFRESH_TOKEN_STRATEGY="SLIDING" \
./setup.sh

# 全クライアント設定をカスタマイズ
CLIENT_NAME="My Web App" \
REDIRECT_URI="https://myapp.example.com/callback" \
MOBILE_CLIENT_NAME="My iOS App" \
MOBILE_REDIRECT_URI="com.myapp.ios://callback" \
M2M_CLIENT_NAME="Backend Service" \
M2M_SCOPE="api:read api:write api:admin" \
./setup.sh
```

### カスタマイズ可能な環境変数

#### 基本設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `ORGANIZATION_NAME` | `third-party` | 組織名 |
| `COOKIE_NAME` | `SESSION` | セッションCookie名 |
| `NEW_ADMIN_EMAIL` | `admin@example.com` | 管理者メールアドレス |
| `NEW_ADMIN_PASSWORD` | `ChangeMe123` | 管理者パスワード |

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

#### トークン戦略

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `ACCESS_TOKEN_DURATION` | `1800` | アクセストークン有効期限（秒） |
| `ID_TOKEN_DURATION` | `3600` | IDトークン有効期限（秒） |
| `REFRESH_TOKEN_DURATION` | `604800` | リフレッシュトークン有効期限（秒） |
| `ROTATE_REFRESH_TOKEN` | `true` | リフレッシュトークンローテーション |
| `REFRESH_TOKEN_STRATEGY` | `FIXED` | リフレッシュトークン戦略（FIXED / SLIDING） |

#### ユーザー登録

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `REGISTRATION_REQUIRED_FIELDS` | `email,password,name` | 登録時の必須項目（カンマ区切り） |

#### Web Client設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `CLIENT_ID` | (自動生成) | WebクライアントID |
| `CLIENT_ALIAS` | `web-app` | Webクライアントエイリアス |
| `CLIENT_SECRET_VALUE` | (自動生成) | Webクライアントシークレット |
| `CLIENT_NAME` | `Web Application` | Webクライアント名 |
| `REDIRECT_URI` | `http://localhost:3000/callback/` | リダイレクトURI |

#### Mobile Client設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `MOBILE_CLIENT_ID` | (自動生成) | モバイルクライアントID |
| `MOBILE_CLIENT_ALIAS` | `mobile-app` | モバイルクライアントエイリアス |
| `MOBILE_CLIENT_NAME` | `Mobile Application` | モバイルクライアント名 |
| `MOBILE_REDIRECT_URI` | `com.example.app://callback` | モバイルリダイレクトURI |

#### M2M Client設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `M2M_CLIENT_ID` | (自動生成) | M2MクライアントID |
| `M2M_CLIENT_ALIAS` | `m2m-service` | M2Mクライアントエイリアス |
| `M2M_CLIENT_SECRET` | (自動生成) | M2Mクライアントシークレット |
| `M2M_CLIENT_NAME` | `M2M Service` | M2Mクライアント名 |
| `M2M_SCOPE` | `api:read api:write` | M2Mクライアントスコープ |

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
     → トークン戦略 + カスタムスコープ付きテナント作成

Step 5: 認証設定作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → ユーザー登録スキーマ（email + password + name）

Step 6: 認証ポリシー作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → パスワードのみで認証成功とするポリシー

Step 7: Webクライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → Confidentialクライアント（client_secret_basic）

Step 8: Mobileクライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → Publicクライアント（PKCE必須、auth_method=none）

Step 9: M2Mクライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → client_credentialsクライアント
```

## テスト方法

### Web Client（Authorization Code Flow）

ブラウザで以下のURLを開く:

```
{BASE_URL}/{PUBLIC_TENANT_ID}/v1/authorizations?response_type=code&client_id={CLIENT_ID}&redirect_uri={REDIRECT_URI}&scope=openid%20profile%20email%20api%3Aread&state=test-state
```

### Mobile Client（Authorization Code Flow + PKCE）

PKCEパラメータ付きで認可リクエスト:

```bash
# code_verifier を生成
CODE_VERIFIER=$(openssl rand -base64 32 | tr -d '=+/' | head -c 43)
CODE_CHALLENGE=$(echo -n "${CODE_VERIFIER}" | openssl dgst -sha256 -binary | openssl base64 | tr -d '=' | tr '+/' '-_')

# 認可リクエスト
# {BASE_URL}/{PUBLIC_TENANT_ID}/v1/authorizations?response_type=code&client_id={MOBILE_CLIENT_ID}&redirect_uri={MOBILE_REDIRECT_URI}&scope=openid%20profile%20email%20api%3Aread&state=test-state&code_challenge=${CODE_CHALLENGE}&code_challenge_method=S256

# トークン交換
curl -X POST {BASE_URL}/{PUBLIC_TENANT_ID}/v1/tokens \
  -d "grant_type=authorization_code" \
  -d "code=YOUR_CODE" \
  -d "redirect_uri={MOBILE_REDIRECT_URI}" \
  -d "client_id={MOBILE_CLIENT_ID}" \
  -d "code_verifier=${CODE_VERIFIER}"
```

### M2M Client（Client Credentials）

```bash
curl -X POST {BASE_URL}/{PUBLIC_TENANT_ID}/v1/tokens \
  -u "{M2M_CLIENT_ID}:{M2M_CLIENT_SECRET}" \
  -d "grant_type=client_credentials" \
  -d "scope=api:read api:write"
```
