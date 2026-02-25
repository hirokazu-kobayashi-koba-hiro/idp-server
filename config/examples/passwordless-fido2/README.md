# Passwordless (FIDO2/WebAuthn) Example

FIDO2/WebAuthn/Passkey によるパスワードレス認証を構成するサンプル設定です。

> **examples vs templates**: この例（`config/examples/`）は固定値のサンプル構成で、そのまま実行できます。環境変数でカスタマイズしたい場合は `config/templates/use-cases/passwordless-fido2/` を使用してください。

## ファイル構成

```
passwordless-fido2/
├── README.md                                        # このファイル
├── onboarding-request.json                          # Onboarding API 用（Organization + Organizer Tenant + Admin User + Client）
├── public-tenant-request.json                       # Public Tenant（パスワードポリシー + セッション設定 + デバイスルール + 認可サーバー）
├── authentication-config-initial-registration.json  # ユーザー登録スキーマ
├── authentication-config-fido2.json                 # FIDO2/WebAuthn 認証設定（webauthn4j）
├── authentication-policy-oauth.json                 # FIDO2 OR パスワード認証ポリシー（oauth フロー）
├── authentication-policy-fido2-registration.json    # FIDO2 デバイス登録ポリシー（fido2-registration フロー）
├── client-request.json                              # アプリケーションクライアント
├── setup.sh                                         # セットアップスクリプト
├── update.sh                                        # 更新スクリプト
└── delete.sh                                        # 削除スクリプト
```

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式 | FIDO2/WebAuthn OR パスワード（移行期パターン） |
| FIDO2 RP ID | `local.dev` |
| Authenticator | platform（端末内蔵認証器） |
| Resident Key | required（Discoverable Credential） |
| User Verification | required |
| パスワードポリシー | 8文字以上、文字種制約なし |
| アカウントロック | 5回失敗で15分ロック |
| セッション有効期限 | 24時間 |
| ユーザー登録必須項目 | email, password, name |

### デバイスルール（authentication_device_rule）

| 項目 | 設定値 | 説明 |
|------|--------|------|
| max_devices | 5 | 1ユーザーあたり最大5デバイス登録可能 |
| device_secret_algorithm | HS256 | デバイスシークレットの署名アルゴリズム |
| device_secret_expires_in_seconds | 86400 | デバイスシークレットの有効期限（24時間） |

### FIDO2 に関する注意

- FIDO2/WebAuthn は **HTTPS** および **Secure Context** が必須です（開発時は `localhost` で可）
- ブラウザが WebAuthn API をサポートしている必要があります
- `authenticator_attachment: platform` は端末内蔵の認証器（Touch ID, Windows Hello 等）を使用します
- `require_resident_key: true` により Discoverable Credential（Passkey）として登録されます

## セットアップ

### 1. 前提条件

- idp-server が起動していること
- `.env` ファイルに管理者認証情報が設定されていること

### 2. セットアップ実行

```bash
cd config/examples/passwordless-fido2
chmod +x setup.sh update.sh delete.sh
./setup.sh
```

### 3. セットアップ結果

| リソース | 値 |
|---------|-----|
| Organization ID | `33333333-1111-3333-3333-333333333333` |
| Organizer Tenant ID | `33333333-2222-3333-3333-333333333333` |
| Public Tenant ID | `33333333-3333-3333-3333-333333333333` |
| Issuer | `https://api.local.dev/33333333-3333-3333-3333-333333333333` |

### 4. 設定更新

設定ファイルを編集後、以下のスクリプトで反映できます：

```bash
# テナント・認可サーバー・クライアントを更新
./update.sh
```

更新対象:
- Public Tenant 設定（パスワードポリシー、セッション、デバイスルール）
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
| Client ID | `33333333-5555-3333-3333-333333333333` |
| Client Alias | `fido2-admin-client` |
| Client Secret | `fido2-admin-secret-32characters!` |
| Auth Method | `client_secret_post` |

### Application Client (Public Tenant)

| 項目 | 値 |
|------|-----|
| Client ID | `33333333-6666-3333-3333-333333333333` |
| Client Alias | `fido2-webapp` |
| Client Secret | `fido2-webapp-secret-32characters!` |
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
     → パスワードポリシー + セッション設定 + デバイスルール付きテナント作成

Step 5: 認証設定作成 - initial-registration（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → ユーザー登録スキーマ（email + password + name）

Step 6: 認証設定作成 - fido2（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → FIDO2/WebAuthn 認証設定（webauthn4j）

Step 7: 認証ポリシー作成 - oauth フロー（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → FIDO2 OR パスワード OR 初回登録で認証成功とするポリシー

Step 8: 認証ポリシー作成 - fido2-registration フロー（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → FIDO2 デバイス登録成功を必要とするポリシー

Step 9: クライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → アプリケーション用OAuthクライアント
```

## テスト方法

### Authorization Code Flow

```bash
# 1. ブラウザで認可エンドポイントを開く
open "https://api.local.dev/33333333-3333-3333-3333-333333333333/v1/authorizations?response_type=code&client_id=33333333-6666-3333-3333-333333333333&redirect_uri=http://localhost:3000/callback&scope=openid%20profile%20email&state=test-state"

# 2. メールアドレス/パスワードで登録またはログイン
#    → パスワード認証後、FIDO2 デバイス登録を促される

# 3. 認可コードをトークンに交換
curl -X POST https://api.local.dev/33333333-3333-3333-3333-333333333333/v1/tokens \
  -d "grant_type=authorization_code" \
  -d "code=YOUR_CODE" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "client_id=33333333-6666-3333-3333-333333333333" \
  -d "client_secret=fido2-webapp-secret-32characters!"
```

> **Note**: FIDO2 認証のテストには WebAuthn API をサポートするブラウザが必要です（Chrome, Safari, Firefox, Edge）。自動テストには仮想認証器（Virtual Authenticator）の設定が必要です。

### OIDC Discovery

```
https://api.local.dev/33333333-3333-3333-3333-333333333333/.well-known/openid-configuration
```
