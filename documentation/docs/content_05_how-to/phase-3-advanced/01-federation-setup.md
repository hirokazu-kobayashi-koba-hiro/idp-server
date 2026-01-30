# 外部IdP連携（Federation）の設定

## このドキュメントの目的

**Google でログイン**のような外部IdP（Identity Provider）連携を設定することが目標です。

### 所要時間
⏱️ **約20分**

### 前提条件
- 管理者トークンを取得済み
- 外部IdP（Google、Azure AD等）でOAuthクライアント登録済み
- 組織ID（organization-id）を取得済み

### Management API URL

**組織レベルAPI**（このドキュメントでの表記）:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/federation-configurations
```

**注意**: システムレベルAPIとの違い
- **組織レベル**: `POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/federation-configurations` ← このドキュメント
- **システムレベル**: `POST /v1/management/tenants/{tenant-id}/federation-configurations` ← 管理者のみ

通常の運用では組織レベルAPIを使用してください。

---

## Federationとは

ユーザーが**既存のアカウント**（Google、Azure AD等）でログインできるようにする仕組みです。

```
ユーザー
  ↓ 「Googleでログイン」をクリック
Googleの認証画面
  ↓ Google認証成功
idp-server
  ↓ Googleからユーザー情報取得
  ↓ idp-serverのユーザーとして認証完了
Authorization Code発行
```

**メリット**:
- ✅ ユーザーは新しいパスワードを覚える必要なし
- ✅ 企業SSO（Google Workspace、Azure AD）と統合
- ✅ セキュリティ向上（外部IdPのMFAを利用）

### 設定要素の関係（概要）

Federation認証には3つの設定が連携します：

```
┌──────────────────┐    ┌──────────────────────┐    ┌──────────────────┐
│     Client       │    │ Authentication       │    │   Federation     │
│                  │    │ Policy               │    │   Configuration  │
├──────────────────┤    ├──────────────────────┤    ├──────────────────┤
│ extension:       │    │ available_methods:   │    │ sso_provider:    │
│  available_      │───▶│  - "oidc-google" ◀───┼────│   "google"       │
│  federations:    │    │                      │    │                  │
│   sso_provider:  │    │ success_conditions:  │    │ payload:         │
│    "google"      │    │  oidc-google >= 1    │    │  issuer, ...     │
└──────────────────┘    └──────────────────────┘    └──────────────────┘
```

**命名規則**: `oidc-{sso_provider}` でAuthentication PolicyとFederation Configurationが紐づく

📖 **詳細**: [Federation設定ガイド](../../content_06_developer-guide/05-configuration/federation.md)

---

## 例: Google連携を設定

### Step 1: Googleでクライアント登録

Google Cloud Consoleで事前準備：

1. **プロジェクト作成**
2. **OAuth 2.0クライアント作成**
   - アプリケーションタイプ: ウェブアプリケーション
   - 承認済みのリダイレクトURI: `http://localhost:8080/{tenant-id}/v1/authorizations/federations/oidc/callback`

3. **クライアントID・シークレット取得**
   - クライアントID: `123456789-abcdefg.apps.googleusercontent.com`
   - クライアントシークレット: `GOCSPX-xxxxxxxxxxxxx`

---

### Step 2: idp-serverでFederation設定

```bash
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/federation-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "oidc",
    "sso_provider": "google",
    "enabled": true,
    "payload": {
      "type": "standard",
      "provider": "standard",
      "issuer": "https://accounts.google.com",
      "issuer_name": "google",
      "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
      "token_endpoint": "https://oauth2.googleapis.com/token",
      "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo",
      "client_id": "123456789-abcdefg.apps.googleusercontent.com",
      "client_secret": "GOCSPX-xxxxxxxxxxxxx",
      "redirect_uri": "http://localhost:8080/${TENANT_ID}/v1/authorizations/federations/oidc/callback",
      "scopes_supported": ["openid", "profile", "email"],
      "userinfo_mapping_rules": [
        {"from": "$.http_request.response_body.sub", "to": "external_user_id"},
        {"from": "$.http_request.response_body.email", "to": "email"},
        {"from": "$.http_request.response_body.name", "to": "name"},
        {"from": "$.http_request.response_body.picture", "to": "picture"}
      ]
    }
  }'
```

**重要なフィールド**:
- `payload.type`: プロトコル種別（`standard`=標準OIDC）
- `payload.provider`: Executorタイプ（`standard`/`oauth-extension`/`facebook`）
- `payload.issuer_name`: IdP識別名（ユーザーの`external_idp_issuer`に設定される）
- `payload.redirect_uri`: コールバックURL（外部IdPに登録するURL）
- `payload.userinfo_mapping_rules`: UserInfo→idp-serverユーザーへのマッピング（詳細は[userinfo_mapping_rulesの詳細](#userinfo_mapping_rules-の詳細)を参照）

**レスポンス**:
```json
{
  "dry_run": false,
  "result": {
    "id": "770e8400-e29b-41d4-a716-446655440002",
    "type": "oidc",
    "sso_provider": "google",
    "enabled": true,
    ...
  }
}
```

**設定内容**:
- `sso_provider: "google"` - Google連携を識別
- `payload.issuer` - Googleの発行者URL
- `payload.issuer_name` - IdP識別名
- `payload.provider` - Executorタイプ（`standard`=標準OIDCフロー）
- `payload.scopes_supported` - Googleから取得する情報（openid, profile, email）
- `payload.userinfo_mapping_rules` - Googleのユーザー情報 → idp-serverのユーザー属性マッピング

---

### Step 3: 認証ポリシーでFederationを許可

```bash
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies/oauth" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "available_methods": ["password", "oidc-google"],
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
            "path": "$.oidc-google.success_count",
            "type": "integer",
            "operation": "gte",
            "value": 1
          }
        ]
      ]
    }
  }'
```

**設定内容**:
- `available_methods` に `"oidc-google"` を追加
- `success_conditions.any_of` - パスワード認証**または**Google認証のいずれかが1回以上成功すればOK

---

### Step 4: クライアントでFederationを有効化

Step 2で作成したフェデレーション設定のIDを使用して、クライアントにFederationを設定します。

```bash
# FEDERATION_ID は Step 2 のレスポンスで取得した id を使用
FEDERATION_ID="770e8400-e29b-41d4-a716-446655440002"

curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "extension": {
      "available_federations": [
        {
          "id": "'${FEDERATION_ID}'",
          "type": "oidc",
          "sso_provider": "google"
        }
      ]
    }
  }'
```

**設定内容**:
- `extension.available_federations` にフェデレーション設定を追加
- `id`: Step 2で作成したフェデレーション設定のID
- `type`: プロトコルタイプ（oidc）
- `sso_provider`: IdP識別子（google）

---

## Step 5: 動作確認

### 5.1 Authorization Request（通常通り）

```bash
curl -v "http://localhost:8080/${TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${CLIENT_ID}&\
redirect_uri=${REDIRECT_URI}&\
scope=openid+profile+email&\
state=random-state"
```

**レスポンス**: ログイン画面にリダイレクト
```
表示される選択肢:
- パスワードでログイン
- Googleでログイン  ← NEW!
```

### 5.2 Googleでログイン

ユーザーが「Googleでログイン」をクリック:

```bash
# フロントエンドから実行
POST /{tenant-id}/v1/authentications/${AUTH_TRANSACTION_ID}/federations/oidc/google
```

**レスポンス**:
```json
{
  "redirect_uri": "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&state=...&nonce=..."
}
```

### 5.3 Googleの認証画面

ユーザーはGoogleの認証画面にリダイレクト:
```
1. Googleアカウントでログイン
2. idp-serverへのアクセス許可
3. Callback URLにリダイレクト
```

### 5.4 Callback処理（自動）

idp-serverが自動処理:
```
1. Googleから認証コード受け取り
2. GoogleへToken Request
3. GoogleのID Token検証
4. UserInfo取得
5. idp-serverのユーザー作成/更新
6. Authorization Code発行
```

### 5.5 トークン取得（通常通り）

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "${CLIENT_ID}:${CLIENT_SECRET}" \
  -d "grant_type=authorization_code&code=abc123&redirect_uri=${REDIRECT_URI}"
```

**成功！**

---

## 他の外部IdP設定例

### Azure AD連携

```json
{
  "type": "oidc",
  "sso_provider": "azure_ad",
  "enabled": true,
  "payload": {
    "type": "standard",
    "provider": "standard",
    "issuer": "https://login.microsoftonline.com/{azure-tenant-id}/v2.0",
    "issuer_name": "azure_ad",
    "authorization_endpoint": "https://login.microsoftonline.com/{azure-tenant-id}/oauth2/v2.0/authorize",
    "token_endpoint": "https://login.microsoftonline.com/{azure-tenant-id}/oauth2/v2.0/token",
    "userinfo_endpoint": "https://graph.microsoft.com/oidc/userinfo",
    "client_id": "your-client-id",
    "client_secret": "your-client-secret",
    "redirect_uri": "http://localhost:8080/${TENANT_ID}/v1/authorizations/federations/oidc/callback",
    "scopes_supported": ["openid", "profile", "email"],
    "userinfo_mapping_rules": [
      {"from": "$.http_request.response_body.sub", "to": "external_user_id"},
      {"from": "$.http_request.response_body.email", "to": "email"},
      {"from": "$.http_request.response_body.name", "to": "name"},
      {"from": "$.http_request.response_body.preferred_username", "to": "preferred_username"}
    ]
  }
}
```

### カスタムOIDCプロバイダー

```json
{
  "type": "oidc",
  "sso_provider": "custom-idp",
  "enabled": true,
  "payload": {
    "type": "standard",
    "provider": "standard",
    "issuer": "https://your-idp.example.com",
    "issuer_name": "custom-idp",
    "authorization_endpoint": "https://your-idp.example.com/oauth2/authorize",
    "token_endpoint": "https://your-idp.example.com/oauth2/token",
    "userinfo_endpoint": "https://your-idp.example.com/oauth2/userinfo",
    "client_id": "your-client-id",
    "client_secret": "your-client-secret",
    "redirect_uri": "http://localhost:8080/${TENANT_ID}/v1/authorizations/federations/oidc/callback",
    "scopes_supported": ["openid", "profile", "email"],
    "userinfo_mapping_rules": [
      {"from": "$.http_request.response_body.sub", "to": "external_user_id"},
      {"from": "$.http_request.response_body.email", "to": "email"},
      {"from": "$.http_request.response_body.name", "to": "name"},
      {"from": "$.http_request.response_body.custom_claims.department", "to": "custom_properties.department"},
      {"from": "$.http_request.response_body.custom_claims.employee_id", "to": "custom_properties.employee_id"}
    ]
  }
}
```

---

## よくあるエラー

### エラー1: リダイレクトURI不一致

**エラー**:
```
Googleのエラー: redirect_uri_mismatch
```

**原因**: Google Cloud Consoleで登録したリダイレクトURIと、実際のリダイレクトURIが一致しない

**解決策**:
```bash
# Google Cloud Consoleで以下を登録
http://localhost:8080/{tenant-id}/v1/authorizations/federations/oidc/callback

# 注意: {tenant-id} は実際のテナントIDに置き換える
http://localhost:8080/18ffff8d-8d97-460f-a71b-33f2e8afd41e/v1/authorizations/federations/oidc/callback
```

---

### エラー2: クライアントシークレット間違い

**エラー**:
```json
{
  "error": "invalid_client",
  "error_description": "Client authentication failed"
}
```

**原因**: `client_id`または`client_secret`が間違っている

**解決策**: Google Cloud Consoleで正しい値を確認

---

### エラー3: ユーザー属性マッピング失敗

**エラー**:
```json
{
  "error": "user_mapping_failed",
  "error_description": "Required attribute 'email' not found"
}
```

**原因**: `user_mapping_rules`で指定した属性がGoogleのレスポンスに含まれていない

**解決策**:
```bash
# Googleから取得できる属性を確認
curl "https://openidconnect.googleapis.com/v1/userinfo" \
  -H "Authorization: Bearer ${GOOGLE_ACCESS_TOKEN}"

# レスポンス例
{
  "sub": "1234567890",
  "name": "John Doe",
  "email": "john@example.com",
  "picture": "https://..."
}

# user_mapping_rulesを実際の属性に合わせる
```

---

## セキュリティ考慮事項

### state検証（CSRF防止）

idp-serverは自動的に`state`パラメータを検証します。

```
Authorization Request時:
  state=random-xyz123 を生成・保存

Callback時:
  受け取ったstate == 保存したstate → OK
  不一致 → CSRF攻撃の可能性 → エラー
```

### nonce検証（リプレイ攻撃防止）

ID Tokenの`nonce`を検証:

```
Authorization Request時:
  nonce=random-abc456 を生成・保存

ID Token検証時:
  ID Token.nonce == 保存したnonce → OK
  不一致 → リプレイ攻撃の可能性 → エラー
```

---

## 高度な設定

### Executorタイプ（payload.provider）

外部IdPとの通信方法を指定します：

| provider | 説明 | 用途 |
|----------|------|------|
| `standard` | 標準OIDCフロー | Google, Azure AD等のOIDC準拠IdP |
| `oauth-extension` | OAuth拡張フロー | カスタムUserInfo取得が必要な場合 |
| `Facebook` | Facebook専用フロー | Facebook Login（大文字始まり） |

**通常は`standard`を使用**してください。`oauth-extension`は`userinfo_execution`でカスタムHTTPリクエストが必要な場合に使用します。

⚠️ **注意**: 無効なprovider値を指定すると**404エラー**が返されます。値は**大文字小文字を区別**します。

```json
{
  "error": "invalid_request",
  "error_description": "No OidcSsoExecutor found for provider xxx"
}
```

### userinfo_mapping_rules の詳細

`userinfo_mapping_rules` は JSONPath を使用して外部IdPのユーザー情報をidp-serverのユーザー属性にマッピングします。

#### provider別のJSONPath形式

| provider | JSONPath形式 | 説明 |
|----------|-------------|------|
| `standard` | `$.http_request.response_body.{field}` | 標準OIDCプロバイダー |
| `oauth-extension` (単一リクエスト) | `$.userinfo_execution_http_request.response_body.{field}` | カスタム単一HTTPリクエスト |
| `oauth-extension` (複数リクエスト) | `$.userinfo_execution_http_requests[index].response_body.{field}` | カスタム複数HTTPリクエスト |

#### standard プロバイダーの例

Google, Azure AD, カスタムOIDCなど標準的なプロバイダーの場合:

```json
{
  "payload": {
    "provider": "standard",
    "userinfo_mapping_rules": [
      {"from": "$.http_request.response_body.sub", "to": "external_user_id"},
      {"from": "$.http_request.response_body.email", "to": "email"},
      {"from": "$.http_request.response_body.name", "to": "name"},
      {"from": "$.http_request.response_body.picture", "to": "picture"},
      {"from": "$.http_request.response_body.custom_claims.department", "to": "custom_properties.department"}
    ]
  }
}
```

#### oauth-extension プロバイダーの例

カスタムHTTPリクエストでユーザー情報を取得する場合:

**単一リクエスト（http_request）**:
```json
{
  "payload": {
    "provider": "oauth-extension",
    "userinfo_execution": {
      "function": "http_request",
      "http_request": {
        "url": "https://api.example.com/user/profile",
        "method": "GET",
        "header_mapping_rules": [
          {
            "from": "$.request_body.access_token",
            "to": "Authorization",
            "functions": [{"name": "format", "args": {"template": "Bearer {{value}}"}}]
          }
        ]
      }
    },
    "userinfo_mapping_rules": [
      {"from": "$.userinfo_execution_http_request.response_body.user_id", "to": "external_user_id"},
      {"from": "$.userinfo_execution_http_request.response_body.mail", "to": "email"},
      {"from": "$.userinfo_execution_http_request.response_body.display_name", "to": "name"}
    ]
  }
}
```

**ポイント**:
- `$.request_body.access_token`で事前に取得したアクセストークンを参照
- `format`関数で`Bearer `プレフィックスを追加してAuthorizationヘッダーに設定

**複数リクエスト（http_requests）**:
```json
{
  "payload": {
    "provider": "oauth-extension",
    "userinfo_execution": {
      "function": "http_requests",
      "http_requests": [
        {
          "url": "https://api.example.com/user/overview",
          "method": "POST",
          "header_mapping_rules": [
            {
              "from": "$.request_body.access_token",
              "to": "Authorization",
              "functions": [{"name": "format", "args": {"template": "Bearer {{value}}"}}]
            }
          ]
        },
        {
          "url": "https://api.example.com/user/details",
          "method": "POST",
          "header_mapping_rules": [
            {
              "from": "$.request_body.access_token",
              "to": "Authorization",
              "functions": [{"name": "format", "args": {"template": "Bearer {{value}}"}}]
            }
          ]
        }
      ]
    },
    "userinfo_mapping_rules": [
      {"from": "$.userinfo_execution_http_requests[0].response_body.id", "to": "external_user_id"},
      {"from": "$.userinfo_execution_http_requests[0].response_body.email", "to": "email"},
      {"from": "$.userinfo_execution_http_requests[1].response_body.birthdate", "to": "birthdate"},
      {"from": "$.userinfo_execution_http_requests[1].response_body.phone_number", "to": "phone_number"},
      {"from": "$.userinfo_execution_http_requests[1].response_body.role", "to": "custom_properties.role"}
    ]
  }
}
```

#### 静的値のマッピング

`from`の代わりに`static_value`を使用して固定値を設定できます:
```json
{"static_value": "my-provider", "to": "provider_id"}
```

#### 重要な`to`フィールド

| フィールド | 説明 | 必須 |
|-----------|------|------|
| `external_user_id` | 外部IdPでのユーザーID | **必須** |
| `email` | メールアドレス | - |
| `name` | 表示名 | - |
| `picture` | プロフィール画像URL | - |
| `preferred_username` | ユーザー名 | - |
| `birthdate` | 生年月日 | - |
| `phone_number` | 電話番号 | - |
| `custom_properties.{key}` | カスタム属性 | - |

**詳細**: [Federation実装ガイド](../../content_06_developer-guide/03-application-plane/08-federation.md)、[Federation設定ガイド](../../content_06_developer-guide/05-configuration/federation.md)

---

## 次のステップ

✅ 外部IdP連携を設定できました！

### さらに高度な認証
- [CIBA Flow](./how-to-12-ciba-flow-fido-uaf.md) - バックチャネル認証
- [Identity Verification](../phase-4-extensions/identity-verification/02-application.md) - 身元確認申込み

### 他の外部IdP
- **Azure AD**: エンタープライズSSO
- **GitHub**: 開発者向けログイン
- **カスタムOIDCプロバイダー**: 自社IdP連携

---

## トラブルシューティング

### Discovery自動設定

OpenID Connect Discoveryに対応しているIdPの場合、自動設定が便利です：

```bash
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/federation-configurations/discover" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "issuer": "https://accounts.google.com",
    "client_id": "123456789-abcdefg.apps.googleusercontent.com",
    "client_secret": "GOCSPX-xxxxxxxxxxxxx"
  }'
```

**自動設定される項目**:
- `authorization_endpoint`
- `token_endpoint`
- `userinfo_endpoint`
- `jwks_uri`
- `scopes_supported`

---

## 関連ドキュメント

- [Concept: ID管理](../content_03_concepts/02-identity-management/concept-01-id-management.md) - Federation概念
- [Developer Guide: Federation実装](../content_06_developer-guide/03-application-plane/08-federation.md) - 開発者向け
- [組織初期化](../phase-1-foundation/02-organization-initialization.md) - テナント・ユーザー作成

---

**最終更新**: 2025-10-13
**難易度**: ⭐⭐⭐☆☆（中級）
**対象**: 外部IdP連携を初めて設定する管理者・開発者
