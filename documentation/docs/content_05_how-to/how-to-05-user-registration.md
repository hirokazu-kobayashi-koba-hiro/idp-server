# ユーザー登録と初回認証

## このドキュメントの目的

**最もシンプルなユーザー登録**を実行し、認証フローで動作確認することが目標です。

具体的には、**ユーザーを作成し、最小限の認証設定でOAuth/OIDC認証フローを実行**します。

### 学べること

✅ **ユーザー管理の基礎**
- ユーザーとは何か
- ユーザー登録の必須項目
- ユーザー情報の確認方法

✅ **実践的な知識**
- ユーザー作成の実行手順
- 簡易認証設定（パスワード認証 + 認証ポリシー）
- 認証フローの動作確認（Authorization Code Flow）

### 所要時間
⏱️ **約10分**

### このドキュメントの位置づけ

**Phase 1**: 最小構成で動作確認（Step 4/5）

**前提ドキュメント**:
- [how-to-01: 組織初期化](./how-to-02-organization-initialization.md) - 組織作成済み
- [how-to-02: テナント作成](./how-to-03-tenant-setup.md) - テナント作成済み
- [how-to-03: クライアント登録](./how-to-04-client-registration.md) - クライアント登録済み

**次のドキュメント**:
- [how-to-05: 認証ポリシー基礎編](./how-to-07-authentication-policy-basic.md) - より柔軟な認証設定

### 前提条件
- [how-to-03](./how-to-04-client-registration.md)でクライアント登録完了
- 組織管理者トークンを取得済み
- 組織ID・テナントID・クライアントIDを確認済み

---

## ユーザーとは

**User（ユーザー）**は、idp-serverで認証・認可される**個人またはエンティティ**です。

ユーザーは必ず1つのテナントに所属し、以下の情報を持ちます：
- **sub**: ユーザーの一意識別子（UUID）
- **email**: メールアドレス（ログインID）
- **name**: 表示名
- **password**: 認証用パスワード（ハッシュ化保存）

---

## このドキュメントで行うこと

1. **最初のユーザーを作成**（最小パラメータ）
2. **ユーザー情報を確認**（取得API）
3. **簡易認証設定**（パスワード認証 + 認証ポリシー）
4. **認証フローで動作確認**（Authorization Code Flow → Token取得）

### 環境変数の準備

**前提**: [how-to-03](./how-to-04-client-registration.md)で設定した環境変数を使用します。

```bash
# 環境変数の確認
echo "Organization ID: $ORGANIZATION_ID"
echo "Public Tenant ID: $PUBLIC_TENANT_ID"
echo "Web Client ID: $WEB_CLIENT_ID"
echo "Admin Token: ${ORG_ADMIN_TOKEN:0:50}..."
```

まだ設定していない場合は、how-to-03を参照してください。

---

## Step 1: 最初のユーザーを作成

### 最小設定でユーザー作成

```bash
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/users" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" \
  -d '{
    "email": "test-user@example.com",
    "provider_id": "idp-server",
    "raw_password": "Test1234",
    "name": "Test User",
    "email_verified": true
  }' | jq .
```

### パラメータ説明

| 項目 | 型 | 必須 | 説明 |
|-----|---|------|------|
| `email` | string | ✅ | メールアドレス（ログインID） |
| `password` | string | ✅ | パスワード（最低8文字、大文字・小文字・数字推奨） |
| `name` | string | ❌ | 表示名 |
| `email_verified` | boolean | ❌ | メール検証済みフラグ（デフォルト: `false`） |

**重要**: `email_verified: true`を指定することで、メール検証をスキップして即座にログイン可能になります。

### 期待されるレスポンス

```json
{
  "dry_run": false,
  "result": {
    "sub": "550e8400-e29b-41d4-a716-446655440001",
    "email": "test-user@example.com",
    "name": "Test User",
    "email_verified": true
  }
}
```

### 確認ポイント
- ✅ `sub`が発行されている（ユーザーの一意識別子）
- ✅ `email`が正しく登録されている
- ✅ `email_verified`が`true`になっている

### 環境変数に保存

```bash
# 後の手順で使用するため、subを保存
export TEST_USER_SUB="550e8400-e29b-41d4-a716-446655440001"
export TEST_USER_EMAIL="test-user@example.com"
export TEST_USER_PASSWORD="Test1234"
```

---

## Step 2: ユーザー情報を確認

### ユーザー取得API

```bash
curl -X GET "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/users/${TEST_USER_SUB}" \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" | jq .
```

### 期待されるレスポンス

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440001",
  "email": "test-user@example.com",
  "name": "Test User",
  "email_verified": true,
  "created_at": "2025-01-15T10:30:00Z",
  "updated_at": "2025-01-15T10:30:00Z"
}
```

**注意**: `password`フィールドはレスポンスに含まれません（セキュリティのため）。

### ユーザー一覧取得

```bash
curl -X GET "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/users" \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" | jq .
```

---

## Step 3: 認証フローで動作確認

### 3.1 Authorization Request

```bash
curl -v "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${WEB_CLIENT_ID}&\
redirect_uri=http://localhost:3000/callback&\
scope=openid+profile+email&\
state=random-state-123&\
nonce=random-nonce-456"
```

**レスポンス**: ログイン画面にリダイレクトされます。

```
< HTTP/1.1 302 Found
< Location: http://localhost:8080/{tenant-id}/login?auth_transaction_id=auth-tx-uuid
```

### 3.2 認証トランザクションIDを取得

リダイレクト先URLから`auth_transaction_id`を抽出します。

```bash
# 例: auth-tx-uuid-here
export AUTH_TRANSACTION_ID="auth-tx-uuid-here"
```

### 3.3 パスワード認証実行

```bash
curl -X POST "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/authentications/${AUTH_TRANSACTION_ID}" \
  -H 'Content-Type: application/json' \
  -d "{
    \"username\": \"${TEST_USER_EMAIL}\",
    \"password\": \"${TEST_USER_PASSWORD}\"
  }" | jq .
```

**成功レスポンス**:
```json
{
  "status": "SUCCESS",
  "redirect_uri": "http://localhost:3000/callback?code=auth-code-abc123&state=random-state-123"
}
```

### 3.4 Authorization Codeを抽出

```bash
# redirect_uriからcodeパラメータを抽出
export AUTH_CODE="auth-code-abc123"
```
http://localhost:3000/callback?code=DTPRqKCRc7MPsK3EQ2wkINbMQWI&iss=https%3A%2F%2Fapp.example.com%2F4c0da82e-ae80-4c37-bd0b-09f194cf64db&state=random-state-123
### 3.5 トークン取得

```bash
curl -X POST "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/tokens" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -u "${WEB_CLIENT_ID}:${WEB_CLIENT_SECRET}" \
  -d "grant_type=authorization_code" \
  -d "code=${AUTH_CODE}" \
  -d "redirect_uri=http://localhost:3000/callback" | jq .
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

### 確認ポイント
- ✅ `access_token`が発行されている
- ✅ `id_token`が発行されている（OIDCフロー）
- ✅ `refresh_token`が発行されている
- ✅ `expires_in`が3600秒（1時間）

---

## よくあるエラー

### エラー1: ユーザーが存在しない

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
# ユーザー一覧を確認
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/users" \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" | jq .

# 正しいメールアドレスを使用
```

---

### エラー2: パスワード要件違反

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

### エラー3: 認証方式が許可されていない

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
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-policies" \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" | jq .

# available_methods に "password" が含まれているか確認
# 含まれていない場合は、Step 3.2を実行
```

---

### エラー4: redirect_uri不一致

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "redirect_uri does not match registered URIs"
}
```

**原因**: Authorization Requestの`redirect_uri`がクライアント登録時の`redirect_uris`に含まれていない

**解決策**:
```bash
# クライアントのredirect_urisを確認
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients/${WEB_CLIENT_ID}" \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" | jq .redirect_uris

# redirect_urisに追加（必要に応じて）
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients/${WEB_CLIENT_ID}" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" \
  -d '{
    "redirect_uris": [
      "https://app.example.com/callback",
      "http://localhost:3000/callback"
    ]
  }' | jq .
```

---

## API Reference

### ユーザー登録API

```http
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/users
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "Password123",
  "name": "User Name",
  "email_verified": true
}
```

#### パラメータ詳細

| 項目 | 型 | 必須 | 説明 |
|-----|---|------|------|
| `email` | string | ✅ | メールアドレス（一意） |
| `password` | string | ✅ | パスワード（認証設定の要件を満たす必要あり） |
| `name` | string | ❌ | 表示名 |
| `given_name` | string | ❌ | 名 |
| `family_name` | string | ❌ | 姓 |
| `middle_name` | string | ❌ | ミドルネーム |
| `nickname` | string | ❌ | ニックネーム |
| `preferred_username` | string | ❌ | 希望ユーザー名 |
| `profile` | string (URL) | ❌ | プロフィールページURL |
| `picture` | string (URL) | ❌ | プロフィール画像URL |
| `website` | string (URL) | ❌ | ウェブサイトURL |
| `email_verified` | boolean | ❌ | メール検証済みフラグ（デフォルト: `false`） |
| `gender` | string | ❌ | 性別 |
| `birthdate` | string (YYYY-MM-DD) | ❌ | 生年月日 |
| `zoneinfo` | string | ❌ | タイムゾーン（例: `Asia/Tokyo`） |
| `locale` | string | ❌ | ロケール（例: `ja-JP`） |
| `phone_number` | string | ❌ | 電話番号（E.164形式推奨） |
| `phone_number_verified` | boolean | ❌ | 電話番号検証済みフラグ |
| `address` | object | ❌ | 住所情報 |

#### レスポンス

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "dry_run": false,
  "result": {
    "sub": "550e8400-e29b-41d4-a716-446655440001",
    "email": "user@example.com",
    "name": "User Name",
    "email_verified": true,
    "created_at": "2025-01-15T10:30:00Z",
    "updated_at": "2025-01-15T10:30:00Z"
  }
}
```

**注意**: `password`はレスポンスに含まれません（セキュリティのため）。

---

### ユーザー取得API

```http
GET /v1/management/organizations/{organization-id}/tenants/{tenant-id}/users/{sub}
Authorization: Bearer {access_token}
```

#### レスポンス

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440001",
  "email": "user@example.com",
  "name": "User Name",
  "email_verified": true,
  "created_at": "2025-01-15T10:30:00Z",
  "updated_at": "2025-01-15T10:30:00Z"
}
```

---

### ユーザー一覧取得API

```http
GET /v1/management/organizations/{organization-id}/tenants/{tenant-id}/users
Authorization: Bearer {access_token}
```

#### レスポンス

```json
{
  "users": [
    {
      "sub": "550e8400-e29b-41d4-a716-446655440001",
      "email": "user@example.com",
      "name": "User Name",
      "email_verified": true
    }
  ],
  "total": 1
}
```

---

## セキュリティのベストプラクティス

### ✅ 推奨

1. **パスワード要件の厳格化**
   ```json
   {
     "min_length": 12,              // 最低12文字
     "require_uppercase": true,     // 大文字必須
     "require_lowercase": true,     // 小文字必須
     "require_number": true,        // 数字必須
     "require_special_character": true  // 特殊文字必須
   }
   ```

2. **メール検証フロー**
   ```json
   {
     "email_verified": false  // 開発環境以外ではfalseを推奨
   }
   ```
   本番環境では、ユーザーにメール検証リンクを送信し、`email_verified: true`は検証後に設定します。

3. **パスワードハッシュ**
   idp-serverは自動的に**bcrypt**でパスワードをハッシュ化します。
   - ✅ 平文パスワードは保存されない
   - ✅ bcryptのソルト付きハッシュ
   - ✅ データベース漏洩時も安全

4. **最小権限の原則**
   ユーザーには必要最小限のスコープのみを許可します。

### ❌ 避けるべき設定

1. **パスワード要件が緩すぎる**
   ```json
   {
     "min_length": 4,               // ❌ 短すぎる
     "require_uppercase": false,    // ❌ 要件が緩すぎる
     "require_lowercase": false,
     "require_number": false
   }
   ```

2. **本番環境でemail_verified: true**
   ```json
   {
     "email_verified": true  // ❌ 本番環境では避けるべき
   }
   ```

---

## 次のステップ

✅ ユーザー登録と認証フローができました！

### より高度な認証設定
- [How-to: 認証ポリシー基礎編](./how-to-07-authentication-policy-basic.md) - より柔軟な認証設定
- [How-to: MFA設定](./how-to-08-mfa-setup.md) - SMS OTP、TOTP、FIDO2

### 外部IdP連携
- [How-to: Federation設定](./how-to-11-federation-setup.md) - Google、Azure AD連携

### トークン管理
- [How-to: トークン戦略](./how-to-09-token-strategy.md) - アクセストークン・リフレッシュトークンの管理

---

## 関連ドキュメント

- [Concept: 認証ポリシー](../content_03_concepts/concept-05-authentication-policy.md) - 認証ポリシーの詳細
- [Developer Guide: User Management](../content_06_developer-guide/05-configuration/user.md) - ユーザー管理の詳細設定
- [Developer Guide: Authentication実装](../content_06_developer-guide/03-application-plane/04-authentication.md) - 開発者向け実装ガイド
- [API Reference](../content_07_reference/api-reference.md) - Management API仕様

---

**最終更新**: 2025-10-14
**難易度**: ⭐☆☆☆☆（初級）
**対象**: 初めてユーザー登録を行う管理者・開発者
