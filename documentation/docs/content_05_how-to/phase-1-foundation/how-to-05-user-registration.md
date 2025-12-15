# ユーザー登録と初回認証

## このドキュメントの目的

**ユーザー登録（セルフサインアップ）を実装し、初回認証でトークンを取得する**ことが目標です。

具体的には、**initial-registration API**を使用して、ユーザーが自分でアカウントを作成し、即座にログインできるフローを構築します。

### 学べること

✅ **ユーザー登録の基礎**
- セルフサインアップ（initial-registration）の仕組み
- 登録時のパスワードポリシー検証
- 登録完了後の自動認証フロー

✅ **実践的な知識**
- Authorization Request → initial-registration → Token取得の一連の流れ
- カスタム登録スキーマの設定方法
- トラブルシューティング

### 所要時間
⏱️ **約10分**

### このドキュメントの位置づけ

**Phase 1**: 最小構成で動作確認（Step 4/5）

**前提ドキュメント**:
- [how-to-01: 組織初期化](./how-to-02-organization-initialization.md) - 組織作成済み
- [how-to-02: テナント作成](./how-to-03-tenant-setup.md) - テナント作成済み
- [how-to-03: クライアント登録](./how-to-04-client-registration.md) - クライアント登録済み

**次のドキュメント**:
- [how-to-06: 認証ポリシー基礎編](./how-to-07-authentication-policy-basic.md) - より柔軟な認証設定

### 前提条件
- [how-to-03](./how-to-04-client-registration.md)でクライアント登録完了
- 組織ID・テナントID・クライアントIDを確認済み
- OAuth 2.0 / OIDC の基本的なフロー理解

---

## ユーザー登録の2つの方法

idp-serverでは、サービスの性質に応じて2つの登録方法を提供しています：

### 方法1: initial-registration（セルフサインアップ）
**ユーザー自身が認証画面で登録**

Auth0/Keycloakと同じように、ログイン画面に「新規登録」を用意し、ユーザーが自分で情報を入力して登録します。

**向いているサービス**:
- ✅ 一般向けWebサービス（ECサイト、SNS、ブログ等）
- ✅ モバイルアプリ
- ✅ 不特定多数のユーザーが利用するSaaS

**このドキュメントで扱う方法です。**

### 方法2: Management API
**管理者が事前にユーザー作成**

管理画面やスクリプトから、管理者がユーザーを事前に作成します。

**向いているサービス**:
- ✅ 社内システム（従業員管理）
- ✅ 招待制サービス
- ✅ B2B SaaS（取引先企業のユーザー一括登録）

**別ドキュメントで扱います**: Management APIによるユーザー管理（今後追加予定）

---

## このドキュメントで行うこと

### 🧭 全体の流れ

1. Authorization Request（認可リクエスト）
2. initial-registration API でユーザー登録
3. Authorization Code 取得
4. Token Request でアクセストークン・IDトークン取得

---

## initial-registration の仕組み

### 認証フロー内でのユーザー登録

initial-registrationは、OAuth認可フロー中にユーザー登録を行います：

```
[ユーザー] → Authorization Request
           ↓
[idp-server] → ログイン画面にリダイレクト
           ↓
[ユーザー] → 新規登録フォーム入力
           ↓
[idp-server] ← initial-registration API
           ↓ ユーザー作成 + パスワードポリシー検証
           ↓ 認証成功（OperationType.AUTHENTICATION）
           ↓
[idp-server] → Authorization Code発行
           ↓
[ユーザー] → Token Request
           ↓
[idp-server] → Access Token + ID Token発行
```

### デフォルト登録スキーマ

テナントにinitial-registration設定がない場合、以下のフィールドで動作します：

**必須項目**:
- `email`: メールアドレス（ログインID）
- `password`: パスワード（テナントのパスワードポリシーで検証）

**任意項目**:
- `name`: 氏名
- `given_name`: 名
- `family_name`: 姓
- `phone_number`: 電話番号

**カスタマイズ可能**: Authentication Configuration APIで登録フィールドを追加・変更できます（後述）

---

## 環境変数の準備

**前提**: [how-to-03](./how-to-04-client-registration.md)で設定した環境変数を使用します。

```bash
# 環境変数の確認
echo "Organization ID: $ORGANIZATION_ID"
echo "Public Tenant ID: $PUBLIC_TENANT_ID"
echo "Web Client ID: $WEB_CLIENT_ID"
echo "Web Client Secret: ${WEB_CLIENT_SECRET:0:20}..."
```

まだ設定していない場合は、how-to-03を参照してください。

---

## Step 1: Authorization Request

### 認可リクエスト実行

```bash
curl -v "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${WEB_CLIENT_ID}&\
redirect_uri=http://localhost:3000/callback&\
scope=openid+profile+email&\
state=random-state-123&\
nonce=random-nonce-456"
```

### レスポンス

```
< HTTP/1.1 302 Found
< Location: http://localhost:8080/auth-views/signin/index.html?tenant_id={tenant-id}&id={auth-id}
```

### 認証トランザクションIDを取得

リダイレクト先URLから `id` パラメータを抽出します。

```bash
# 例: Location: http://localhost:8080/auth-views/signin/index.html?tenant_id=xxx&id=da1af51a-274e-481a-8146-78d5bd205671
export AUTH_ID="da1af51a-274e-481a-8146-78d5bd205671"
```

---

## Step 2: initial-registration でユーザー登録

### ユーザー登録API実行

```bash
curl -X POST "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/authorizations/${AUTH_ID}/initial-registration" \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "newuser@example.com",
    "password": "SecurePass123!",
    "name": "New User",
    "given_name": "New",
    "family_name": "User"
  }' | jq .
```

### パラメータ説明

| 項目 | 型 | 必須 | 説明 |
|-----|---|------|------|
| `email` | string | ✅ | メールアドレス（ログインID、一意） |
| `password` | string | ✅ | パスワード（テナントのパスワードポリシーで検証） |
| `name` | string | ❌ | 氏名 |
| `given_name` | string | ❌ | 名 |
| `family_name` | string | ❌ | 姓 |
| `phone_number` | string | ❌ | 電話番号 |

**重要**: `password` はテナントのパスワードポリシーに準拠する必要があります（デフォルト: 最低8文字）。

### 成功レスポンス

```json
{
  "user": {
    "sub": "1a6e5377-18be-4322-a00e-9ecd7c24335c",
    "email": "newuser@example.com",
    "name": "New User",
    "provider_id": "idp-server",
    "status": "INITIALIZED"
  }
}
```

### 確認ポイント
- ✅ `sub` が発行されている（ユーザーの一意識別子）
- ✅ `email` が正しく登録されている
- ✅ `status` が `INITIALIZED` になっている

### 環境変数に保存

```bash
export NEW_USER_SUB="1a6e5377-18be-4322-a00e-9ecd7c24335c"
export NEW_USER_EMAIL="newuser@example.com"
```

---

## Step 3: Authorization Code 取得

initial-registration が成功すると、ユーザーは自動的に認証済み状態になります。次に authorize エンドポイントを呼び出します。

### Authorize実行

```bash
curl -X POST "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/authorizations/${AUTH_ID}/authorize" \
  -H 'Content-Type: application/json' \
  -d '{}' | jq .
```

### 成功レスポンス

```json
{
  "redirect_uri": "http://localhost:3000/callback?code=wsGIhymxIijie8v6xOQhOSo-YAM&iss=http://localhost:8080/{tenant-id}&state=random-state-123"
}
```

### Authorization Code を抽出

```bash
# redirect_uri から code パラメータを抽出
export AUTH_CODE="wsGIhymxIijie8v6xOQhOSo-YAM"
```

---

## Step 4: トークン取得

### Token Request実行

```bash
curl -X POST "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/tokens" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "grant_type=authorization_code" \
  -d "code=${AUTH_CODE}" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "client_id=${WEB_CLIENT_ID}" \
  -d "client_secret=${WEB_CLIENT_SECRET}" | jq .
```

### 成功レスポンス

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6ImF0K2p3dCJ9...",
  "refresh_token": "YdFkVQXEKDIzZvUhwQMqsKh8LIBBZlnAwow1inqKdjE",
  "scope": "openid profile email",
  "id_token": "eyJraWQiOiJiZDlkZGVmMC0wODQ0LTRlMmItYTNiOS1lZjlkNzhiMDlmNGMiLCJhbGciOiJSUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### ID Token の確認

ID Token をデコードして、登録したユーザー情報を確認できます：

```bash
# ID Token の payload 部分をデコード（jwt.io や jq を使用）
echo $ID_TOKEN | cut -d'.' -f2 | base64 -d | jq .
```

```json
{
  "sub": "1a6e5377-18be-4322-a00e-9ecd7c24335c",
  "aud": "1883e958-b8a8-44a9-9ece-be73db6080f8",
  "auth_time": 1763195688,
  "amr": ["initial-registration"],
  "iss": "http://localhost:8080/{tenant-id}",
  "exp": 1763199288,
  "iat": 1763195688
}
```

### 確認ポイント
- ✅ `access_token` が発行されている
- ✅ `id_token` が発行されている（OIDCフロー）
- ✅ `refresh_token` が発行されている
- ✅ `amr` に `initial-registration` が含まれている（登録経由で認証）

---

## よくあるエラー

### エラー1: パスワードポリシー違反

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "invalid request.",
  "error_messages": ["password minLength is 8"]
}
```

**原因**: パスワードがテナントのパスワードポリシーを満たしていない

**解決策**:
```bash
# ❌ 間違い: 8文字未満
"password": "Test1"

# ❌ 間違い: 複雑性要件がある場合
"password": "12345678"

# ✅ 正しい: デフォルト（最低8文字）
"password": "SecurePass123!"
```

**テナントのパスワードポリシー確認**:
```bash
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}" \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" | jq '.identity_policy.password_policy'
```

---

### エラー2: メールアドレス重複

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "user is conflict with username and password"
}
```

**原因**: 同じメールアドレスのユーザーが既に存在

**解決策**:
```bash
# 別のメールアドレスを使用
"email": "newuser2@example.com"
```

---

### エラー3: 認証トランザクションIDが無効

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "not found oauth request"
}
```

**原因**:
- Authorization Request が実行されていない
- 認証トランザクションIDが間違っている
- トランザクションが有効期限切れ

**解決策**:
```bash
# Step 1 から再実行
# Authorization Request → id パラメータ抽出 → initial-registration
```

---

### エラー4: redirect_uri 不一致

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "redirect_uri does not match registered URIs"
}
```

**原因**: Authorization Request の `redirect_uri` がクライアント登録時の `redirect_uris` に含まれていない

**解決策**:
```bash
# クライアントの redirect_uris を確認
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients/${WEB_CLIENT_ID}" \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" | jq .redirect_uris

# redirect_uris に追加（必要に応じて）
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

### initial-registration API

```http
POST /{tenant-id}/v1/authorizations/{id}/initial-registration
Content-Type: application/json

{
  "email": "newuser@example.com",
  "password": "SecurePass123!",
  "name": "New User",
  "given_name": "New",
  "family_name": "User",
  "phone_number": "+81-90-1234-5678"
}
```

#### パラメータ詳細

| 項目 | 型 | 必須 | 説明 |
|-----|---|------|------|
| `email` | string | ✅ | メールアドレス（一意、ログインID） |
| `password` | string | ✅ | パスワード（テナントのパスワードポリシーで検証） |
| `name` | string | ❌ | 氏名 |
| `given_name` | string | ❌ | 名 |
| `family_name` | string | ❌ | 姓 |
| `phone_number` | string | ❌ | 電話番号 |

**注意**: カスタム登録スキーマを設定している場合、そのスキーマに従う必要があります。

#### レスポンス

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "user": {
    "sub": "1a6e5377-18be-4322-a00e-9ecd7c24335c",
    "email": "newuser@example.com",
    "name": "New User",
    "provider_id": "idp-server",
    "status": "REGISTERED"
  }
}
```

**注意**: `password` はレスポンスに含まれません（セキュリティのため）。

---

## カスタム登録スキーマの設定（オプション）

デフォルトスキーマではなく、独自の登録フィールドを定義したい場合は、Authentication Configuration API を使用します。

### 例: 会社名・部署名を必須にする

```bash
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-configurations" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" \
  -d '{
    "id": "'"$(uuidgen | tr '[:upper:]' '[:lower:]')"'",
    "type": "initial-registration",
    "enabled": true,
    "interactions": {
      "initial-registration": {
        "request": {
          "request_schema": {
            "type": "object",
            "required": ["email", "password", "name", "company", "department"],
            "properties": {
              "email": {
                "type": "string",
                "format": "email",
                "maxLength": 255
              },
              "password": {
                "type": "string",
                "minLength": 8
              },
              "name": {
                "type": "string",
                "maxLength": 255
              },
              "company": {
                "type": "string",
                "maxLength": 255
              },
              "department": {
                "type": "string",
                "maxLength": 255
              }
            }
          }
        }
      }
    }
  }' | jq .
```

カスタムスキーマを設定後は、`company` と `department` が必須になります：

```bash
curl -X POST "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/authorizations/${AUTH_ID}/initial-registration" \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "employee@example.com",
    "password": "SecurePass123!",
    "name": "Employee Name",
    "company": "Example Corp",
    "department": "Engineering"
  }' | jq .
```

---

## セキュリティのベストプラクティス

### ✅ 推奨

1. **パスワードポリシーの設定**

   テナント作成時に適切なパスワードポリシーを設定：
   ```json
   {
     "identity_policy": {
       "password_policy": {
         "min_length": 12,
         "max_length": 72
       }
     }
   }
   ```

   詳細は [Concept: Password Policy](../content_03_concepts/02-identity-management/concept-02-password-policy.md) を参照。

2. **メール検証フロー**

   本番環境では、登録後にメール検証を実施：
   - 登録直後は `email_verified: false`
   - 検証リンクをメール送信
   - ユーザーがリンクをクリック後、`email_verified: true` に更新

3. **Bot対策**

   - reCAPTCHA や hCaptcha の導入
   - レート制限の設定

4. **登録フィールドの最小化**

   初回登録時は必要最小限のフィールドのみ要求し、プロフィール編集で追加情報を収集。

### ❌ 避けるべき設定

1. **パスワード要件が緩すぎる**
   ```json
   {
     "min_length": 4  // ❌ 短すぎる
   }
   ```

2. **本番環境でメール検証なし**

   悪意のあるユーザーが他人のメールアドレスで登録可能になります。

3. **過度な情報収集**

   初回登録で住所・クレジットカード等を要求すると、登録率が低下します。

---

## 次のステップ

✅ セルフサービスユーザー登録ができました！

### より高度な認証設定
- [How-to: 認証ポリシー基礎編](./how-to-07-authentication-policy-basic.md) - より柔軟な認証設定
- [How-to: MFA設定](./how-to-08-mfa-setup.md) - SMS OTP、TOTP、FIDO2

### 外部IdP連携
- [How-to: Federation設定](./how-to-11-federation-setup.md) - Google、Azure AD連携

### パスワード管理
- [How-to: パスワード管理](./how-to-06-password-management.md) - パスワード変更・リセット
- [Concept: Password Policy](../content_03_concepts/02-identity-management/concept-02-password-policy.md) - パスワードポリシー詳細

---

## 関連ドキュメント

- [Concept: 認証ポリシー](../content_03_concepts/03-authentication-authorization/concept-01-authentication-policy.md) - 認証ポリシーの詳細
- [Concept: Password Policy](../content_03_concepts/02-identity-management/concept-02-password-policy.md) - パスワードポリシー詳細
- [Developer Guide: Authentication実装](../content_06_developer-guide/03-application-plane/04-authentication.md) - 開発者向け実装ガイド
- [API Reference](../content_07_reference/api-reference.md) - Management API仕様

---

**最終更新**: 2025-01-15
**難易度**: ⭐☆☆☆☆（初級）
**対象**: 初めてユーザー登録機能を実装する管理者・開発者
