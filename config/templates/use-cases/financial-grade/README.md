# Financial-Grade (FAPI Advanced + CIBA) Use Case Template

FAPI Advanced + CIBA 準拠の金融グレード認可サーバーを構築するテンプレートセット。mTLS、署名付きリクエスト、PAR、JARM、CIBA を組み合わせた最高セキュリティレベルの構成。

> Claude Code を使用している場合は `/use-case-setup` でヒアリング付きの対話型セットアップが利用できます。

> **templates vs examples**: このテンプレート（`config/templates/`）はゼロからの完全セットアップ用です。Organization・テナント・クライアントを一括作成します。既存テナントへの追加設定には `config/examples/financial-grade/` を使用してください。

## 設定内容

| 項目 | 設定値 |
|------|--------|
| mTLS | 有効（証明書バウンドアクセストークン） |
| 署名付きリクエスト | 必須（ES256） |
| PAR | 有効（有効期限 60秒） |
| JARM | 有効（ES256） |
| CIBA | 有効（poll モード、有効期限 120秒） |
| トークン有効期限 | AT=300秒、IDT=300秒、RT=2592000秒 |
| FAPI Baseline スコープ | read, account |
| FAPI Advance スコープ | write, transfers |
| 認証方式 | FIDO2 + Email（高セキュリティ）、Email + SMS（標準） |
| クライアント認証 | tls_client_auth, private_key_jwt |

## ファイル構成

| ファイル | 用途 | API |
|---------|------|-----|
| `onboarding-template.json` | Organization + Organizer Tenant + Admin User + Client | `POST /v1/management/onboarding` |
| `financial-tenant-template.json` | Financial Tenant（FAPI設定 + CIBA設定） | `POST /v1/management/tenants` |
| `authentication-config-initial-registration.json` | ユーザー登録スキーマ | `POST .../authentication-configurations` |
| `authentication-config-fido2.json` | FIDO2 (WebAuthn4J) 認証設定 | `POST .../authentication-configurations` |
| `authentication-policy-oauth.json` | OAuth 認証ポリシー（3段階） | `POST .../authentication-policies` |
| `authentication-policy-ciba.json` | CIBA 認証ポリシー | `POST .../authentication-policies` |
| `tls-client-auth-client-template.json` | mTLS クライアント | `POST .../clients` |
| `private-key-jwt-client-template.json` | JWT クライアント | `POST .../clients` |
| `financial-user-template.json` | テストユーザー（認証デバイス付き） | `POST .../users` |
| `jwks.json` | サーバー署名鍵 | - |
| `setup.sh` | 上記を順番に実行するスクリプト | - |
| `verify.sh` | FAPI 設定検証スクリプト | - |
| `update.sh` | 全リソース更新スクリプト | - |
| `delete.sh` | 全リソース削除スクリプト | - |
| `ciba-device-auth.sh` | CIBA デバイス認証スクリプト | - |
| `generate-certs.sh` | クライアント証明書生成スクリプト | - |

## セットアップ手順

### 前提条件

- idp-server が起動済み
- システム管理者テナントが存在（初期セットアップ済み）
- `.env` に管理者認証情報を設定済み
- `openssl` がインストール済み（証明書生成用）

### 自動セットアップ

```bash
# .env を設定（プロジェクトルートに配置）
# AUTHORIZATION_SERVER_URL=https://api.local.dev
# ADMIN_TENANT_ID=<system-admin-tenant-id>
# ADMIN_USER_EMAIL=<admin-email>
# ADMIN_USER_PASSWORD=<admin-password>
# ADMIN_CLIENT_ID=<admin-client-id>
# ADMIN_CLIENT_SECRET=<admin-client-secret>

# JWKS を生成して配置（初回のみ）
# cp path/to/your/jwks.json ./jwks.json

# クライアント証明書を生成（初回のみ）
./generate-certs.sh

# セットアップ実行
./setup.sh

# ドライラン（実際には作成しない）
./setup.sh --dry-run
```

### 環境変数でカスタマイズ

```bash
# 組織名を指定
ORGANIZATION_NAME="my-bank" ./setup.sh

# mTLS URL を指定
MTLS_BASE_URL="https://mtls.mybank.example.com" ./setup.sh

# トークン有効期限をカスタマイズ
ACCESS_TOKEN_DURATION=600 \
REFRESH_TOKEN_DURATION=86400 \
./setup.sh

# CIBA設定をカスタマイズ
CIBA_REQUEST_EXPIRES_IN=180 \
CIBA_POLLING_INTERVAL=3 \
./setup.sh

# クライアント設定をカスタマイズ
TLS_CLIENT_ALIAS="my-tls-client" \
TLS_CLIENT_NAME="My TLS Client" \
PKJ_CLIENT_ALIAS="my-pkj-client" \
PKJ_CLIENT_NAME="My PKJ Client" \
CERT_SUBJECT_DN="CN=my-app,O=My Bank,C=JP" \
REDIRECT_URI="https://myapp.example.com/callback" \
./setup.sh
```

### カスタマイズ可能な環境変数

#### 基本設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `ORGANIZATION_NAME` | `financial-grade` | 組織名 |
| `MTLS_BASE_URL` | `https://mtls.api.local.dev` | mTLS エンドポイント URL |
| `UI_BASE_URL` | `${AUTHORIZATION_SERVER_URL}` | UI ベース URL |
| `COOKIE_NAME` | `FAPI_SESSION` | セッション Cookie 名 |
| `SIGNING_ALGORITHM` | `ES256` | 署名アルゴリズム |

#### トークン有効期限

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `ACCESS_TOKEN_DURATION` | `300` | アクセストークン有効期限（秒） |
| `ID_TOKEN_DURATION` | `300` | ID トークン有効期限（秒） |
| `REFRESH_TOKEN_DURATION` | `2592000` | リフレッシュトークン有効期限（秒） |
| `PAR_EXPIRES_IN` | `60` | PAR 有効期限（秒） |

#### CIBA 設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `CIBA_REQUEST_EXPIRES_IN` | `120` | CIBA リクエスト有効期限（秒） |
| `CIBA_POLLING_INTERVAL` | `5` | ポーリング間隔（秒） |
| `CIBA_USER_CODE_REQUIRED` | `false` | ユーザーコード必須 |

#### FIDO2 設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `FIDO2_RP_ID` | `local.dev` | WebAuthn RP ID |

#### クライアント設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `TLS_CLIENT_ALIAS` | `fapi-tls-client` | mTLS クライアントエイリアス |
| `TLS_CLIENT_NAME` | `FAPI TLS Client Auth Client` | mTLS クライアント名 |
| `PKJ_CLIENT_ALIAS` | `fapi-pkj-client` | JWT クライアントエイリアス |
| `PKJ_CLIENT_NAME` | `FAPI Private Key JWT Client` | JWT クライアント名 |
| `CERT_SUBJECT_DN` | `CN=financial-app,O=Financial Institution,C=JP` | 証明書 Subject DN |
| `REDIRECT_URI` | `https://localhost:8443/callback` | リダイレクト URI |

#### テストユーザー設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `FINANCIAL_USER_EMAIL` | `fapi-test@example.com` | テストユーザーメール |
| `FINANCIAL_USER_PASSWORD` | `FapiTestSecure123!` | テストユーザーパスワード |
| `FINANCIAL_USER_PHONE` | `+81-90-1234-5678` | テストユーザー電話番号 |

## セットアップフロー

```
Step 1: システム管理者トークン取得
  +-- POST /{admin-tenant}/v1/tokens (password grant)

Step 2: オンボーディング（システムレベルAPI）
  +-- POST /v1/management/onboarding
     -> Organization + Organizer Tenant + Admin User + Client 作成

Step 3: ORGANIZER管理者トークン取得
  +-- POST /{organizer-tenant}/v1/tokens (password grant)
     -> 以降は組織レベルAPIを使用

Step 4: 金融テナント作成（組織レベルAPI）
  +-- POST /v1/management/organizations/{org-id}/tenants
     -> FAPI + CIBA 設定付きテナント作成

Step 5a: 認証設定作成 - initial-registration（組織レベルAPI）
  +-- POST .../tenants/{id}/authentication-configurations
     -> ユーザー登録スキーマ

Step 5b: 認証設定作成 - FIDO2（組織レベルAPI）
  +-- POST .../tenants/{id}/authentication-configurations
     -> WebAuthn4J FIDO2 認証

Step 6a: 認証ポリシー作成 - OAuth（組織レベルAPI）
  +-- POST .../tenants/{id}/authentication-policies
     -> 3段階セキュリティポリシー

Step 6b: 認証ポリシー作成 - CIBA（組織レベルAPI）
  +-- POST .../tenants/{id}/authentication-policies
     -> CIBA フロー用ポリシー

Step 7a: クライアント作成 - tls_client_auth（組織レベルAPI）
  +-- POST .../tenants/{id}/clients
     -> mTLS クライアント認証

Step 7b: クライアント作成 - private_key_jwt（組織レベルAPI）
  +-- POST .../tenants/{id}/clients
     -> JWT クライアント認証

Step 8: テストユーザー作成（組織レベルAPI）
  +-- POST .../tenants/{id}/users
     -> 認証デバイス付きユーザー（CIBA用）
```
