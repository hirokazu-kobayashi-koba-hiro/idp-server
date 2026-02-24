# Login (Social) Example

パスワード + Google ソーシャルログインを構成するサンプル設定です。

> **examples vs templates**: この例（`config/examples/`）は固定値のサンプル構成で、そのまま実行できます。環境変数でカスタマイズしたい場合は `config/templates/use-cases/login-social/` を使用してください。

## ファイル構成

```
login-social/
├── README.md                                      # このファイル
├── onboarding-request.json                        # Onboarding API 用（Organization + Organizer Tenant + Admin User + Client）
├── public-tenant-request.json                     # Public Tenant（パスワードポリシー + セッション設定 + 認可サーバー）
├── authentication-config-initial-registration.json # ユーザー登録スキーマ
├── authentication-policy-oauth.json               # パスワードのみ認証ポリシー
├── federation-config-google.json                  # Google OIDC フェデレーション設定
├── client-request.json                            # アプリケーションクライアント
├── setup.sh                                       # セットアップスクリプト
├── update.sh                                      # 更新スクリプト
└── delete.sh                                      # 削除スクリプト
```

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式 | パスワード + Google ソーシャルログイン |
| パスワードポリシー | 8文字以上、文字種制約なし |
| アカウントロック | 5回失敗で15分ロック |
| セッション有効期限 | 24時間 |
| ユーザー登録必須項目 | email, password, name |
| フェデレーション | Google OIDC |
| ID一意キー | EMAIL_OR_EXTERNAL_USER_ID |

## セットアップ

### 1. 前提条件

- idp-server が起動していること
- `.env` ファイルに管理者認証情報が設定されていること
- Google Cloud Console で OAuth 2.0 クライアントID/シークレットを取得済みであること

### 2. Google OAuth 設定

`federation-config-google.json` の以下のフィールドを実際の値に置き換えてください：

```json
{
  "client_id": "your-google-client-id",
  "client_secret": "your-google-client-secret"
}
```

Google Cloud Console での設定：
- 承認済みリダイレクト URI: `https://api.local.dev/11111111-3333-1111-1111-111111111111/v1/authorizations/federations/oidc/callback`

### 3. セットアップ実行

```bash
cd config/examples/login-social
chmod +x setup.sh update.sh delete.sh
./setup.sh
```

### 4. セットアップ結果

| リソース | 値 |
|---------|-----|
| Organization ID | `11111111-1111-1111-1111-111111111111` |
| Organizer Tenant ID | `11111111-2222-1111-1111-111111111111` |
| Public Tenant ID | `11111111-3333-1111-1111-111111111111` |
| Federation Config ID | `11111111-8888-1111-1111-111111111111` |
| Issuer | `https://api.local.dev/11111111-3333-1111-1111-111111111111` |

### 5. 設定更新

設定ファイルを編集後、以下のスクリプトで反映できます：

```bash
# テナント・認可サーバー・クライアント・フェデレーション設定を更新
./update.sh
```

更新対象:
- Public Tenant 設定（パスワードポリシー、セッション）
- 認可サーバー設定（JWKS含む）
- アプリケーションクライアント設定（フェデレーション設定含む）
- フェデレーション設定（Google OIDC）

### 6. 削除

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
| Client ID | `11111111-5555-1111-1111-111111111111` |
| Client Alias | `login-social-admin-client` |
| Client Secret | `login-social-admin-secret-32ch!` |
| Auth Method | `client_secret_post` |

### Application Client (Public Tenant)

| 項目 | 値 |
|------|-----|
| Client ID | `11111111-6666-1111-1111-111111111111` |
| Client Alias | `login-social-webapp` |
| Client Secret | `login-social-webapp-secret-32ch!` |
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

Step 5: 認証設定作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → ユーザー登録スキーマ（email + password + name）

Step 6: 認証ポリシー作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → パスワードのみで認証成功とするポリシー

Step 7: クライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → アプリケーション用OAuthクライアント

Step 8: フェデレーション設定作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/federation-configurations
     → Google OIDC フェデレーション設定

Step 9: クライアント更新（組織レベルAPI）
  └─ PUT /v1/management/organizations/{org-id}/tenants/{id}/clients/{client-id}
     → available_federations にフェデレーション設定IDを追加
```

## テスト方法

### Authorization Code Flow

```bash
# 1. ブラウザで認可エンドポイントを開く
open "https://api.local.dev/11111111-3333-1111-1111-111111111111/v1/authorizations?response_type=code&client_id=11111111-6666-1111-1111-111111111111&redirect_uri=http://localhost:3000/callback&scope=openid%20profile%20email&state=test-state"

# 2. メールアドレス/パスワードで登録またはログイン、または Google ログインを使用

# 3. 認可コードをトークンに交換
curl -X POST https://api.local.dev/11111111-3333-1111-1111-111111111111/v1/tokens \
  -d "grant_type=authorization_code" \
  -d "code=YOUR_CODE" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "client_id=11111111-6666-1111-1111-111111111111" \
  -d "client_secret=login-social-webapp-secret-32ch!"
```

### OIDC Discovery

```
https://api.local.dev/11111111-3333-1111-1111-111111111111/.well-known/openid-configuration
```
