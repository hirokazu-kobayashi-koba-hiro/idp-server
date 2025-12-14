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

---

## 例: Google連携を設定

### Step 1: Googleでクライアント登録

Google Cloud Consoleで事前準備：

1. **プロジェクト作成**
2. **OAuth 2.0クライアント作成**
   - アプリケーションタイプ: ウェブアプリケーション
   - 承認済みのリダイレクトURI: `http://localhost:8080/{tenant-id}/v1/authentications/federations/oidc/google/callback`

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
    "client_id": "123456789-abcdefg.apps.googleusercontent.com",
    "client_secret": "GOCSPX-xxxxxxxxxxxxx",
    "issuer": "https://accounts.google.com",
    "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
    "token_endpoint": "https://oauth2.googleapis.com/token",
    "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo",
    "scopes": ["openid", "profile", "email"],
    "user_mapping_rules": {
      "sub": "$.sub",
      "email": "$.email",
      "name": "$.name",
      "picture": "$.picture"
    }
  }'
```

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
- `issuer` - Googleの発行者URL
- `scopes` - Googleから取得する情報（openid, profile, email）
- `user_mapping_rules` - Googleのユーザー情報 → idp-serverのユーザー属性マッピング

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
      "type": "any",
      "authentication_methods": ["password", "oidc-google"]
    }
  }'
```

**設定内容**:
- `available_methods` に `"oidc-google"` を追加
- `type: "any"` - パスワード**または**Google認証でOK

---

### Step 4: クライアントでFederationを有効化

```bash
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "available_federations": ["oidc-google"]
  }'
```

**設定内容**:
- このクライアントでGoogle連携を利用可能にする

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
  "client_id": "your-client-id",
  "client_secret": "your-client-secret",
  "issuer": "https://login.microsoftonline.com/{tenant-id}/v2.0",
  "authorization_endpoint": "https://login.microsoftonline.com/{tenant-id}/oauth2/v2.0/authorize",
  "token_endpoint": "https://login.microsoftonline.com/{tenant-id}/oauth2/v2.0/token",
  "userinfo_endpoint": "https://graph.microsoft.com/oidc/userinfo",
  "scopes": ["openid", "profile", "email"],
  "user_mapping_rules": {
    "sub": "$.sub",
    "email": "$.email",
    "name": "$.name"
  }
}
```

### カスタムOIDCプロバイダー

```json
{
  "type": "oidc",
  "sso_provider": "custom-idp",
  "enabled": true,
  "client_id": "your-client-id",
  "client_secret": "your-client-secret",
  "issuer": "https://your-idp.example.com",
  "authorization_endpoint": "https://your-idp.example.com/oauth2/authorize",
  "token_endpoint": "https://your-idp.example.com/oauth2/token",
  "userinfo_endpoint": "https://your-idp.example.com/oauth2/userinfo",
  "scopes": ["openid", "profile", "email"],
  "user_mapping_rules": {
    "sub": "$.sub",
    "email": "$.email",
    "name": "$.name",
    "department": "$.custom_claims.department"
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
http://localhost:8080/{tenant-id}/v1/authentications/federations/oidc/google/callback

# 注意: {tenant-id} は実際のテナントIDに置き換える
http://localhost:8080/18ffff8d-8d97-460f-a71b-33f2e8afd41e/v1/authentications/federations/oidc/google/callback
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

## ユーザー統合戦略

### パターン1: メールアドレスで統合（デフォルト）

```json
{
  "user_mapping_rules": {
    "email": "$.email"
  },
  "user_integration_strategy": "email_match"
}
```

**動作**:
```
Googleからemail取得: john@example.com
  ↓
idp-serverで同じemailのユーザーを検索
  ↓
存在する → 既存ユーザーと統合（更新）
存在しない → 新規ユーザー作成
```

### パターン2: 外部IDで統合

```json
{
  "user_mapping_rules": {
    "sub": "$.sub",
    "email": "$.email"
  },
  "user_integration_strategy": "external_id_match",
  "external_id_attribute": "sub"
}
```

**動作**:
```
Googleからsub取得: google-1234567890
  ↓
idp-serverでexternal_id="google-1234567890"のユーザーを検索
  ↓
存在する → 既存ユーザーと統合
存在しない → 新規ユーザー作成（external_id="google-1234567890"）
```

---

## 高度な設定

### カスタム属性マッピング

```json
{
  "user_mapping_rules": {
    "sub": "$.sub",
    "email": "$.email",
    "name": "$.name",
    "picture": "$.picture",
    "department": "$.custom_claims.department",
    "employee_id": "$.custom_claims.employee_id"
  }
}
```

**JSONPath**で柔軟にマッピング:
- `$.email` - ルート直下の`email`
- `$.custom_claims.department` - ネストした属性

### 複数HTTPリクエスト（高度）

標準的なOIDC IdPでは不要ですが、一部のIdPでは複数API呼び出しが必要な場合があります。

**詳細**: [Federation実装ガイド](../content_06_developer-guide/03-application-plane/08-federation.md)、[Federation設定ガイド](../content_06_developer-guide/05-configuration/federation.md)

---

## 次のステップ

✅ 外部IdP連携を設定できました！

### さらに高度な認証
- [How-to: CIBA Flow](./how-to-12-ciba-flow-fido-uaf.md) - バックチャネル認証
- [How-to: Identity Verification](./how-to-16-identity-verification-application.md) - 身元確認申込み

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

- [Concept: ID管理](../content_03_concepts/02-identity-management/concept-04-id-management.md) - Federation概念
- [Developer Guide: Federation実装](../content_06_developer-guide/03-application-plane/08-federation.md) - 開発者向け
- [How-to: 組織初期化](./how-to-02-organization-initialization.md) - テナント・ユーザー作成

---

**最終更新**: 2025-10-13
**難易度**: ⭐⭐⭐☆☆（中級）
**対象**: 外部IdP連携を初めて設定する管理者・開発者
