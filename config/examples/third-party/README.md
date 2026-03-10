# Third Party Integration Example

サードパーティ連携（Web/モバイル/M2M クライアント）を構成するサンプル設定です。

> **examples vs templates**: この例（`config/examples/`）は固定値のサンプル構成で、そのまま実行できます。環境変数でカスタマイズしたい場合は `config/templates/use-cases/third-party/` を使用してください。

## ファイル構成

```
third-party/
├── README.md                                      # このファイル
├── onboarding-request.json                        # Onboarding API 用（Organization + Organizer Tenant + Admin User + Client）
├── public-tenant-request.json                     # Public Tenant（パスワードポリシー + セッション設定 + 認可サーバー + トークン戦略）
├── authentication-config-initial-registration.json # ユーザー登録スキーマ
├── authentication-policy-oauth.json               # パスワードのみ認証ポリシー
├── client-web-request.json                        # Web アプリケーションクライアント（Confidential）
├── client-mobile-request.json                     # モバイルアプリケーションクライアント（Public + PKCE）
├── client-m2m-request.json                        # M2M クライアント（Client Credentials）
├── setup.sh                                       # セットアップスクリプト
├── update.sh                                      # 更新スクリプト
└── delete.sh                                      # 削除スクリプト
```

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式 | パスワードのみ |
| パスワードポリシー | 8文字以上、文字種制約なし |
| アカウントロック | 5回失敗で15分ロック |
| セッション有効期限 | 24時間 |
| ユーザー登録必須項目 | email, password, name |
| アクセストークン有効期限 | 30分 |
| リフレッシュトークン有効期限 | 7日 |
| リフレッシュトークンローテーション | 有効（FIXED戦略） |
| カスタムスコープ | `api:read`, `api:write` |
| クライアント種別 | Web（Confidential）、Mobile（Public + PKCE）、M2M（Client Credentials） |

## リソースID一覧

| リソース | ID |
|---------|-----|
| Organization ID | `55555555-1111-5555-5555-555555555555` |
| Organizer Tenant ID | `55555555-2222-5555-5555-555555555555` |
| Admin User sub | `55555555-3333-5555-5555-555555555555` |
| Admin Client ID | `55555555-4444-5555-5555-555555555555` |
| Public Tenant ID | `55555555-5555-5555-5555-555555555555` |
| Auth Config ID | `55555555-6666-5555-5555-555555555555` |
| Web Client ID | `55555555-7777-5555-5555-555555555555` |
| Mobile Client ID | `55555555-8888-5555-5555-555555555555` |
| M2M Client ID | `55555555-9999-5555-5555-555555555555` |
| Issuer | `https://api.local.test/55555555-5555-5555-5555-555555555555` |

## セットアップ

### 1. 前提条件

- idp-server が起動していること
- `.env` ファイルに管理者認証情報が設定されていること

### 2. セットアップ実行

```bash
cd config/examples/third-party
chmod +x setup.sh update.sh delete.sh
./setup.sh
```

### 3. 設定更新

設定ファイルを編集後、以下のスクリプトで反映できます：

```bash
# テナント・認可サーバー・全クライアントを更新
./update.sh
```

更新対象:
- Public Tenant 設定（パスワードポリシー、セッション）
- 認可サーバー設定（JWKS、トークン戦略含む）
- Web アプリケーションクライアント設定
- Mobile アプリケーションクライアント設定
- M2M クライアント設定

### 4. 削除

```bash
# 全リソースを削除
./delete.sh
```

## Organization Admin

| 項目 | 値 |
|------|-----|
| Email | `org-admin@example.com` |
| Password | `OrgAdminPassword123!` |

## クライアント設定

### Admin Client (Organizer Tenant)

| 項目 | 値 |
|------|-----|
| Client ID | `55555555-4444-5555-5555-555555555555` |
| Client Alias | `third-party-admin-client` |
| Client Secret | `third-party-admin-secret-32ch!!` |
| Auth Method | `client_secret_post` |

### Web Client (Confidential)

| 項目 | 値 |
|------|-----|
| Client ID | `55555555-7777-5555-5555-555555555555` |
| Client Alias | `third-party-web-app` |
| Client Secret | `third-party-web-secret-32chars!` |
| Auth Method | `client_secret_basic` |
| Redirect URI | `http://localhost:3000/callback` |
| Grant Types | `authorization_code`, `refresh_token` |
| Scope | `openid profile email api:read` |
| Application Type | `web` |

### Mobile Client (Public + PKCE)

| 項目 | 値 |
|------|-----|
| Client ID | `55555555-8888-5555-5555-555555555555` |
| Client Alias | `third-party-mobile-app` |
| Auth Method | `none`（PKCE必須） |
| Redirect URI | `com.example.thirdparty://callback` |
| Grant Types | `authorization_code`, `refresh_token` |
| Scope | `openid profile email api:read` |
| Application Type | `native` |

### M2M Client (Client Credentials)

| 項目 | 値 |
|------|-----|
| Client ID | `55555555-9999-5555-5555-555555555555` |
| Client Alias | `third-party-m2m-service` |
| Client Secret | `third-party-m2m-secret-32chars!` |
| Auth Method | `client_secret_basic` |
| Grant Types | `client_credentials` |
| Scope | `api:read api:write` |
| Application Type | `web` |

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
     → パスワードポリシー + セッション設定 + トークン戦略付きテナント作成

Step 5: 認証設定作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → ユーザー登録スキーマ（email + password + name）

Step 6: 認証ポリシー作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → パスワードのみで認証成功とするポリシー

Step 7: Web クライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → Confidential クライアント（client_secret_basic）

Step 8: Mobile クライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → Public クライアント（PKCE必須、auth_method=none）

Step 9: M2M クライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → Client Credentials クライアント（api:read, api:write スコープ）
```

## テスト方法

### Web Client - Authorization Code Flow

```bash
# 1. ブラウザで認可エンドポイントを開く
open "https://api.local.test/55555555-5555-5555-5555-555555555555/v1/authorizations?response_type=code&client_id=55555555-7777-5555-5555-555555555555&redirect_uri=http://localhost:3000/callback&scope=openid%20profile%20email%20api%3Aread&state=test-state"

# 2. メールアドレス/パスワードで登録またはログイン

# 3. 認可コードをトークンに交換（Basic認証）
curl -X POST https://api.local.test/55555555-5555-5555-5555-555555555555/v1/tokens \
  -u "55555555-7777-5555-5555-555555555555:third-party-web-secret-32chars!" \
  -d "grant_type=authorization_code" \
  -d "code=YOUR_CODE" \
  -d "redirect_uri=http://localhost:3000/callback"
```

### Mobile Client - Authorization Code Flow + PKCE

```bash
# 1. code_verifier と code_challenge を生成
CODE_VERIFIER=$(openssl rand -base64 32 | tr -d '=/+' | head -c 43)
CODE_CHALLENGE=$(echo -n "${CODE_VERIFIER}" | openssl dgst -sha256 -binary | base64 | tr -d '=' | tr '/+' '_-')

# 2. ブラウザで認可エンドポイントを開く（PKCE パラメータ付き）
open "https://api.local.test/55555555-5555-5555-5555-555555555555/v1/authorizations?response_type=code&client_id=55555555-8888-5555-5555-555555555555&redirect_uri=com.example.thirdparty://callback&scope=openid%20profile%20email%20api%3Aread&state=test-state&code_challenge=${CODE_CHALLENGE}&code_challenge_method=S256"

# 3. メールアドレス/パスワードで登録またはログイン

# 4. 認可コードをトークンに交換（code_verifier付き）
curl -X POST https://api.local.test/55555555-5555-5555-5555-555555555555/v1/tokens \
  -d "grant_type=authorization_code" \
  -d "code=YOUR_CODE" \
  -d "redirect_uri=com.example.thirdparty://callback" \
  -d "client_id=55555555-8888-5555-5555-555555555555" \
  -d "code_verifier=${CODE_VERIFIER}"
```

### M2M Client - Client Credentials

```bash
# クライアントクレデンシャルでアクセストークンを取得
curl -X POST https://api.local.test/55555555-5555-5555-5555-555555555555/v1/tokens \
  -u "55555555-9999-5555-5555-555555555555:third-party-m2m-secret-32chars!" \
  -d "grant_type=client_credentials" \
  -d "scope=api:read api:write"
```

### OIDC Discovery

```
https://api.local.test/55555555-5555-5555-5555-555555555555/.well-known/openid-configuration
```
