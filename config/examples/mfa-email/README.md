# MFA (Password + Email OTP) Example

パスワード + Email OTP の二要素認証を構成するサンプル設定です。

> **examples vs templates**: この例（`config/examples/`）は固定値のサンプル構成で、そのまま実行できます。環境変数でカスタマイズしたい場合は `config/templates/use-cases/mfa-email/` を使用してください。

## ファイル構成

```
mfa-email/
├── README.md                                      # このファイル
├── onboarding-request.json                        # Onboarding API 用（Organization + Organizer Tenant + Admin User + Client）
├── public-tenant-request.json                     # Public Tenant（パスワードポリシー + セッション設定 + 認可サーバー）
├── authentication-config-initial-registration.json # ユーザー登録スキーマ
├── authentication-config-email.json               # Email OTP 認証設定（no-action モード）
├── authentication-policy-oauth.json               # パスワード + Email OTP MFA 認証ポリシー
├── client-request.json                            # アプリケーションクライアント
├── setup.sh                                       # セットアップスクリプト
├── update.sh                                      # 更新スクリプト
└── delete.sh                                      # 削除スクリプト
```

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式 | パスワード + Email OTP (二要素認証) |
| Email OTP モード | no-action（ローカル開発用、実際のメール送信なし） |
| パスワードポリシー | 8文字以上、文字種制約なし |
| アカウントロック | 5回失敗で15分ロック |
| セッション有効期限 | 24時間 |
| ユーザー登録必須項目 | email, password, name |
| OTP 有効期限 | 300秒（5分） |
| OTP リトライ上限 | 5回 |

> **no-action モードについて**: ローカル開発では実際のメールは送信されません。検証コードはサーバーログに出力されます。

## セットアップ

### 1. 前提条件

- idp-server が起動していること
- `.env` ファイルに管理者認証情報が設定されていること

### 2. セットアップ実行

```bash
cd config/examples/mfa-email
chmod +x setup.sh update.sh delete.sh
./setup.sh
```

### 3. セットアップ結果

| リソース | 値 |
|---------|-----|
| Organization ID | `22222222-1111-2222-2222-222222222222` |
| Organizer Tenant ID | `22222222-2222-2222-2222-222222222222` |
| Public Tenant ID | `22222222-3333-2222-2222-222222222222` |
| Issuer | `https://api.local.test/22222222-3333-2222-2222-222222222222` |

### 4. 設定更新

設定ファイルを編集後、以下のスクリプトで反映できます：

```bash
# テナント・認可サーバー・クライアントを更新
./update.sh
```

更新対象:
- Public Tenant 設定（パスワードポリシー、セッション）
- 認可サーバー設定（JWKS含む）
- アプリケーションクライアント設定

### 5. 削除

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
| Client ID | `22222222-5555-2222-2222-222222222222` |
| Client Alias | `mfa-email-admin-client` |
| Client Secret | `mfa-email-admin-secret-32chars!` |
| Auth Method | `client_secret_post` |

### Application Client (Public Tenant)

| 項目 | 値 |
|------|-----|
| Client ID | `22222222-6666-2222-2222-222222222222` |
| Client Alias | `mfa-email-webapp` |
| Client Secret | `mfa-email-webapp-secret-32chars!` |
| Auth Method | `client_secret_post` |
| Redirect URI | `http://localhost:3000/callback` |

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

Step 5: 認証設定作成 - ユーザー登録（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → ユーザー登録スキーマ（email + password + name）

Step 6: 認証設定作成 - Email OTP（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → Email OTP 認証設定（no-action モード）

Step 7: 認証ポリシー作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → パスワード AND Email OTP で認証成功とするMFAポリシー

Step 8: クライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → アプリケーション用OAuthクライアント
```

## テスト方法

### Authorization Code Flow

```bash
# 1. ブラウザで認可エンドポイントを開く
open "https://api.local.test/22222222-3333-2222-2222-222222222222/v1/authorizations?response_type=code&client_id=22222222-6666-2222-2222-222222222222&redirect_uri=http://localhost:3000/callback&scope=openid%20profile%20email&state=test-state"

# 2. メールアドレス/パスワードで登録またはログイン
#    ログイン時は Email OTP の入力も求められます
#    no-action モードでは検証コードはサーバーログに出力されます

# 3. 認可コードをトークンに交換
curl -X POST https://api.local.test/22222222-3333-2222-2222-222222222222/v1/tokens \
  -d "grant_type=authorization_code" \
  -d "code=YOUR_CODE" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "client_id=22222222-6666-2222-2222-222222222222" \
  -d "client_secret=mfa-email-webapp-secret-32chars!"
```

### OIDC Discovery

```
https://api.local.test/22222222-3333-2222-2222-222222222222/.well-known/openid-configuration
```
