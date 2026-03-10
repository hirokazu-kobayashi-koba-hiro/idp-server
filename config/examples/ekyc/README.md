# eKYC (Identity Verification) Example

身元確認（eKYC）プロセスを構成するサンプル設定です。

> **examples vs templates**: この例（`config/examples/`）は固定値のサンプル構成で、そのまま実行できます。環境変数でカスタマイズしたい場合は `config/templates/use-cases/ekyc/` を使用してください。

## ファイル構成

```
ekyc/
├── README.md                                      # このファイル
├── onboarding-request.json                        # Onboarding API 用（Organization + Organizer Tenant + Admin User + Client）
├── public-tenant-request.json                     # Public Tenant（パスワードポリシー + セッション設定 + 認可サーバー + verified_claims）
├── authentication-config-initial-registration.json # ユーザー登録スキーマ
├── authentication-policy-oauth.json               # パスワードのみ認証ポリシー
├── identity-verification-config.json              # 身元確認設定（authentication-assurance）
├── client-request.json                            # アプリケーションクライアント
├── setup.sh                                       # セットアップスクリプト
├── update.sh                                      # 更新スクリプト
└── delete.sh                                      # 削除スクリプト
```

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式 | パスワードのみ + 身元確認プロセス |
| パスワードポリシー | 8文字以上、文字種制約なし |
| アカウントロック | 5回失敗で15分ロック |
| セッション有効期限 | 24時間 |
| ユーザー登録必須項目 | email, password, name |
| verified_claims_supported | true |
| 身元確認トリガー | `transfers` スコープ要求時 |
| 身元確認モード | no_action / mock（ローカル開発用） |

## 主な特徴

- **verified_claims_supported**: 認可サーバーが OpenID Connect for Identity Assurance (eKYC) をサポート
- **required_identity_verification_scopes**: `transfers` スコープを要求すると身元確認プロセスが発動
- **no_action / mock モード**: ローカル開発環境では外部 eKYC サービスへの接続なしで動作確認可能
- **eKYC フロー**: apply（申請） → evaluate-result（審査結果評価） → approved/rejected

## セットアップ

### 1. 前提条件

- idp-server が起動していること
- `.env` ファイルに管理者認証情報が設定されていること

### 2. セットアップ実行

```bash
cd config/examples/ekyc
chmod +x setup.sh update.sh delete.sh
./setup.sh
```

### 3. セットアップ結果

| リソース | 値 |
|---------|-----|
| Organization ID | `44444444-1111-4444-4444-444444444444` |
| Organizer Tenant ID | `44444444-2222-4444-4444-444444444444` |
| Public Tenant ID | `44444444-3333-4444-4444-444444444444` |
| Issuer | `https://api.local.test/44444444-3333-4444-4444-444444444444` |

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
| Client ID | `44444444-5555-4444-4444-444444444444` |
| Client Alias | `ekyc-admin-client` |
| Client Secret | `ekyc-admin-secret-32characters!!` |
| Auth Method | `client_secret_post` |

### Application Client (Public Tenant)

| 項目 | 値 |
|------|-----|
| Client ID | `44444444-6666-4444-4444-444444444444` |
| Client Alias | `ekyc-webapp` |
| Client Secret | `ekyc-webapp-secret-32characters!` |
| Auth Method | `client_secret_post` |
| Redirect URI | `http://localhost:3000/callback` |
| Scope | `openid profile email transfers` |

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
     → パスワードポリシー + セッション設定 + verified_claims 付きテナント作成

Step 5: 認証設定作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → ユーザー登録スキーマ（email + password + name）

Step 6: 認証ポリシー作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → パスワードのみで認証成功とするポリシー

Step 7: クライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → アプリケーション用OAuthクライアント

Step 8: 身元確認設定作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/identity-verification-configurations
     → eKYC プロセス設定（apply + evaluate-result）
```

## テスト方法

### Authorization Code Flow（身元確認あり）

身元確認を発動させるには、`transfers` スコープを含めて認可リクエストを送信します。

```bash
# 1. ブラウザで認可エンドポイントを開く（transfers スコープ付き）
open "https://api.local.test/44444444-3333-4444-4444-444444444444/v1/authorizations?response_type=code&client_id=44444444-6666-4444-4444-444444444444&redirect_uri=http://localhost:3000/callback&scope=openid%20profile%20email%20transfers&state=test-state"

# 2. メールアドレス/パスワードで登録またはログイン

# 3. 身元確認プロセスが発動（transfers スコープにより）

# 4. 認可コードをトークンに交換
curl -X POST https://api.local.test/44444444-3333-4444-4444-444444444444/v1/tokens \
  -d "grant_type=authorization_code" \
  -d "code=YOUR_CODE" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "client_id=44444444-6666-4444-4444-444444444444" \
  -d "client_secret=ekyc-webapp-secret-32characters!"
```

### eKYC フロー

```
1. apply（申請）
   ユーザーが身元情報（氏名、生年月日、メールアドレス等）を送信
   → no_action モードでは即座に受理

2. evaluate-result（審査結果評価）
   審査結果を評価し、approved（承認）または rejected（否認）に遷移
   → approved: { "approved": true }
   → rejected: { "rejected": true }
```

### OIDC Discovery

```shell
curl https://api.local.test/44444444-3333-4444-4444-444444444444/.well-known/openid-configuration | jq
```
