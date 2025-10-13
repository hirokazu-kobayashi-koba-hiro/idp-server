# パスワード認証の設定

## このドキュメントの目的

**最もシンプルなパスワード認証**を設定し、動作確認することが目標です。

### 所要時間
⏱️ **約10分**

### 前提条件
- idp-serverが起動している
- 管理者トークンを取得済み
- テナントが作成済み
- 組織ID（organization-id）を取得済み

### Management API URL

**組織レベルAPI**（このドキュメントでの表記）:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-configurations
```

**注意**: システムレベルAPIとの違い
- **組織レベル**: `POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-configurations` ← このドキュメント
- **システムレベル**: `POST /v1/management/tenants/{tenant-id}/authentication-configurations` ← 管理者のみ

通常の運用では組織レベルAPIを使用してください。

---

## パスワード認証とは

ユーザー名（メールアドレス等）とパスワードで認証する、**最も基本的な認証方式**です。

```
ユーザー
  ↓ ユーザー名・パスワード入力
idp-server
  ↓ パスワードハッシュ照合
認証成功 → Authorization Code発行
```

---

## Step 1: パスワード認証設定を作成

### Management APIで設定

```bash
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "password",
    "enabled": true,
    "min_length": 8,
    "max_length": 128,
    "require_uppercase": true,
    "require_lowercase": true,
    "require_number": true,
    "require_special_character": false
  }'
```

### レスポンス

```json
{
  "dry_run": false,
  "result": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "type": "password",
    "enabled": true,
    "min_length": 8,
    "max_length": 128,
    "require_uppercase": true,
    "require_lowercase": true,
    "require_number": true,
    "require_special_character": false
  }
}
```

**設定内容**:
- ✅ 最低8文字
- ✅ 大文字必須
- ✅ 小文字必須
- ✅ 数字必須
- ❌ 特殊文字不要

---

## Step 2: 設定を確認

```bash
curl -X GET "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-configurations/password" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

**レスポンス**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "password",
  "enabled": true,
  ...
}
```

---

## Step 3: ユーザーを作成

パスワード認証にはユーザーが必要です。

```bash
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/users" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "email": "test-user@example.com",
    "password": "Test1234",
    "name": "Test User",
    "email_verified": true
  }'
```

**レスポンス**:
```json
{
  "dry_run": false,
  "result": {
    "sub": "user-uuid-here",
    "email": "test-user@example.com",
    "name": "Test User",
    "email_verified": true
  }
}
```

**重要**: パスワードは設定した要件（大文字・小文字・数字）を満たす必要があります。

---

## Step 4: 認証ポリシーを設定

パスワード認証を有効化するには、認証ポリシーで許可する必要があります。

```bash
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "available_methods": ["password"],
    "success_conditions": {
      "type": "any",
      "authentication_methods": ["password"]
    }
  }'
```

**設定内容**:
- `flow: "oauth"` - OAuth/OIDC認証フローで使用
- `available_methods: ["password"]` - パスワード認証を許可
- `success_conditions` - パスワード認証が成功すれば認証完了

---

## Step 5: 動作確認

### 5.1 Authorization Request

```bash
# ブラウザでアクセス（または curl -L でリダイレクトを追跡）
curl -v "http://localhost:8080/${TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${CLIENT_ID}&\
redirect_uri=${REDIRECT_URI}&\
scope=openid+profile+email&\
state=random-state&\
nonce=random-nonce"
```

**レスポンス**: ログイン画面にリダイレクト

### 5.2 パスワード認証実行

```bash
# 認証トランザクションIDを取得後
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authentications/${AUTH_TRANSACTION_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test-user@example.com",
    "password": "Test1234"
  }'
```

**成功レスポンス**:
```json
{
  "status": "SUCCESS",
  "redirect_uri": "https://app.example.com/callback?code=abc123&state=random-state"
}
```

### 5.3 トークン取得

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n '${CLIENT_ID}:${CLIENT_SECRET}' | base64)" \
  -d "grant_type=authorization_code&code=abc123&redirect_uri=${REDIRECT_URI}"
```

**成功レスポンス**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "scope": "openid profile email"
}
```

---

## よくあるエラー

### エラー1: パスワード要件違反

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "Password does not meet requirements"
}
```

**原因**: パスワードが設定要件を満たしていない

**解決策**:
```bash
# ❌ 間違い: 数字なし
"password": "TestPassword"

# ❌ 間違い: 大文字なし
"password": "test1234"

# ✅ 正しい: 大文字・小文字・数字あり
"password": "Test1234"
```

---

### エラー2: 認証方式が許可されていない

**エラー**:
```json
{
  "error": "authentication_method_not_allowed",
  "error_description": "password authentication is not allowed"
}
```

**原因**: 認証ポリシーで`password`が`available_methods`に含まれていない

**解決策**:
```bash
# 認証ポリシーを確認
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

# available_methods に "password" が含まれているか確認
# 含まれていない場合は、認証ポリシーを更新
```

---

### エラー3: ユーザーが存在しない

**エラー**:
```json
{
  "status": "FAILURE",
  "error": "user_not_found"
}
```

**原因**: ユーザーが作成されていない、またはメールアドレスが間違っている

**解決策**:
```bash
# ユーザーを作成（Step 3参照）
# または、正しいメールアドレスを使用
```

---

## 設定のカスタマイズ

### パスワード要件を緩和

```bash
# より緩い要件（開発環境用）
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-configurations/password" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "password",
    "enabled": true,
    "min_length": 6,
    "require_uppercase": false,
    "require_lowercase": false,
    "require_number": false,
    "require_special_character": false
  }'
```

### パスワード要件を厳格化

```bash
# より厳しい要件（本番環境用）
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-configurations/password" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "password",
    "enabled": true,
    "min_length": 12,
    "max_length": 128,
    "require_uppercase": true,
    "require_lowercase": true,
    "require_number": true,
    "require_special_character": true
  }'
```

---

## セキュリティのベストプラクティス

### ✅ 推奨設定

```json
{
  "min_length": 12,              // 最低12文字
  "require_uppercase": true,     // 大文字必須
  "require_lowercase": true,     // 小文字必須
  "require_number": true,        // 数字必須
  "require_special_character": true  // 特殊文字必須
}
```

### ❌ 避けるべき設定

```json
{
  "min_length": 4,               // ❌ 短すぎる
  "require_uppercase": false,    // ❌ 要件が緩すぎる
  "require_lowercase": false,
  "require_number": false,
  "require_special_character": false
}
```

### パスワードハッシュ

idp-serverは自動的に**bcrypt**でパスワードをハッシュ化します。

- ✅ 平文パスワードは保存されない
- ✅ bcryptのソルト付きハッシュ
- ✅ データベース漏洩時も安全

---

## 次のステップ

パスワード認証を設定できました！次は：

### MFA（多要素認証）を追加
- [How-to: MFA設定](./how-to-09-mfa-setup.md) - SMS OTP、TOTP、FIDO2

### 外部IdP連携
- [How-to: Federation設定](./how-to-12-federation-setup.md) - Google、Azure AD連携

### より複雑な認証フロー
- [How-to: CIBA Flow](./how-to-04-ciba-flow-fido-uaf.md) - バックチャネル認証
- [How-to: Identity Verification](./how-to-07-identity-verification-application.md) - 身元確認

---

## 関連ドキュメント

- [Concept: 認証ポリシー](../content_03_concepts/concept-05-authentication-policy.md) - 認証ポリシーの詳細
- [Developer Guide: Authentication実装](../content_06_developer-guide/03-application-plane/04-authentication.md) - 開発者向け実装ガイド
- [API Reference](../content_07_reference/api-reference.md) - Management API仕様

---

**最終更新**: 2025-10-13
**難易度**: ⭐☆☆☆☆（初級）
**対象**: 初めて認証設定を行う管理者・開発者
