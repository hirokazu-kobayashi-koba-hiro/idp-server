# パスワード管理

## このドキュメントの目的

**ユーザー自身によるパスワード変更・パスワードリセット機能を実装する**ことが目標です。

具体的には、`/me/password/change` と `/me/password/reset` APIを使用して、ログイン済みユーザーが自分のパスワードを管理できるフローを構築します。

### 学べること

✅ **パスワード変更**
- 現在のパスワードを検証して新しいパスワードに変更
- パスワードポリシーによる検証
- セキュリティイベントとの連携

✅ **パスワードリセット**
- パスワードを忘れた場合のリセットフロー
- `password:reset` スコープによる認可
- 代替認証方式（メール検証等）との組み合わせ

### 所要時間
⏱️ **約10分**

### このドキュメントの位置づけ

**Phase 1**: 最小構成で動作確認（Step 5/5）

**前提ドキュメント**:
- [ユーザー登録](./05-user-registration.md) - ユーザー登録済み

**次のドキュメント**:
- [認証ポリシー基礎編](./07-authentication-policy.md) - より柔軟な認証設定
- [MFA設定](../phase-2-security/01-mfa-setup.md) - 多要素認証

### 前提条件
- [how-to-05](./05-user-registration.md)でユーザー登録完了
- アクセストークン取得済み
- OAuth 2.0 / OIDC の基本的なフロー理解

---

## パスワード管理の2つの方法

idp-serverでは、2つのパスワード管理APIを提供しています：

### 方法1: パスワード変更（Password Change）

**現在のパスワードを知っている場合**

ユーザーが定期的にパスワードを変更したい場合や、セキュリティ強化のためにパスワードを更新する場合に使用します。

**ユースケース**:
- ✅ 定期的なパスワード更新
- ✅ セキュリティ強化のための変更
- ✅ パスワード漏洩の疑いがある場合の予防的変更

**必要な情報**:
- 現在のパスワード（`current_password`）
- 新しいパスワード（`new_password`）

### 方法2: パスワードリセット（Password Reset）

**現在のパスワードを忘れた場合**

ユーザーがパスワードを忘れてログインできない場合に、代替認証方式（メール検証等）で本人確認後、新しいパスワードを設定します。

**ユースケース**:
- ✅ パスワード忘れ
- ✅ アカウント復旧

**必要な情報**:
- 新しいパスワード（`new_password`）のみ
- ※ 事前に `password:reset` スコープで認証済みであること

---

## パスワード変更の実装

### フロー概要

```
[ユーザー] ログイン済み（アクセストークン取得済み）
     ↓
[ユーザー] パスワード変更リクエスト
     ↓ POST /me/password/change
     ↓ current_password + new_password
[idp-server]
     ↓ 1. 現在のパスワード検証
     ↓ 2. 新パスワードのポリシー検証
     ↓ 3. パスワード更新
     ↓
[レスポンス] 成功 / エラー
```

### API実行

```bash
curl -X POST "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/me/password/change" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "current_password": "OldPassword123!",
    "new_password": "NewSecurePass456!"
  }' | jq .
```

### パラメータ

| 項目 | 型 | 必須 | 説明 |
|-----|---|------|------|
| `current_password` | string | ✅ | 現在のパスワード |
| `new_password` | string | ✅ | 新しいパスワード（テナントのパスワードポリシーで検証） |

### 成功レスポンス

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "message": "Password changed successfully."
}
```

### エラーレスポンス

#### 現在のパスワードが間違っている場合

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "invalid_current_password",
  "error_description": "Current password is incorrect."
}
```

#### 新しいパスワードがポリシー違反の場合

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "invalid_new_password",
  "error_description": "password minLength is 8"
}
```

#### 必須パラメータが不足している場合

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "invalid_request",
  "error_description": "Current password is required."
}
```

---

## パスワードリセットの実装

### フロー概要

パスワードリセットは、ユーザーがパスワードを忘れた場合に使用します。通常、以下のフローで実行されます：

```
[ユーザー] パスワードを忘れた
     ↓
[ユーザー] パスワードリセット開始
     ↓ Authorization Request (scope=password:reset)
[idp-server]
     ↓ メール検証等の代替認証
     ↓ アクセストークン発行（password:reset スコープ付き）
     ↓
[ユーザー] 新パスワード設定
     ↓ POST /me/password/reset
     ↓ new_password のみ
[idp-server]
     ↓ 1. スコープ検証（password:reset）
     ↓ 2. 新パスワードのポリシー検証
     ↓ 3. パスワード更新
     ↓
[レスポンス] 成功 / エラー
```

### Step 1: パスワードリセット用トークン取得

パスワードリセットには、`password:reset` スコープを含むアクセストークンが必要です。

```bash
# Authorization Request（パスワードリセット用）
curl -v "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${WEB_CLIENT_ID}&\
redirect_uri=http://localhost:3000/callback&\
scope=openid+password:reset&\
state=random-state-123"
```

**重要**: このリクエストでは、パスワード認証以外の代替認証方式（メールOTP等）でユーザーを認証する必要があります。

### Step 2: パスワードリセットAPI実行

```bash
curl -X POST "http://localhost:8080/${PUBLIC_TENANT_ID}/v1/me/password/reset" \
  -H "Authorization: Bearer ${RESET_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "new_password": "NewSecurePass456!"
  }' | jq .
```

### パラメータ

| 項目 | 型 | 必須 | 説明 |
|-----|---|------|------|
| `new_password` | string | ✅ | 新しいパスワード（テナントのパスワードポリシーで検証） |

**注意**: `current_password` は不要です。`password:reset` スコープで認証済みであることが、本人確認の代わりになります。

### 成功レスポンス

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "message": "Password changed successfully."
}
```

### エラーレスポンス

#### スコープ不足の場合

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "error": "insufficient_scope",
  "error_description": "The request requires higher privileges than provided by the access token."
}
```

---

## パスワードポリシーとの連携

パスワード変更・リセット時には、テナントに設定されたパスワードポリシーで検証されます。

### パスワードポリシーの確認

```bash
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}" \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" | jq '.identity_policy.password_policy'
```

### レスポンス例

```json
{
  "min_length": 8,
  "max_length": 72
}
```

### ポリシー違反時のエラー

新しいパスワードがポリシーを満たさない場合、具体的なエラーメッセージが返されます：

```json
{
  "error": "invalid_new_password",
  "error_description": "password minLength is 8"
}
```

詳細は [Concept: Password Policy](../content_03_concepts/02-identity-management/concept-02-password-policy.md) を参照してください。

---

## よくあるエラー

### エラー1: アクセストークンなし/無効

**エラー**:
```http
HTTP/1.1 401 Unauthorized
```

**原因**: `Authorization` ヘッダーがないか、トークンが無効/期限切れ

**解決策**:
```bash
# トークンを再取得
# Authorization Code フローでアクセストークンを取得
```

### エラー2: 現在のパスワードが間違っている（パスワード変更時）

**エラー**:
```json
{
  "error": "invalid_current_password",
  "error_description": "Current password is incorrect."
}
```

**原因**: 入力した現在のパスワードが間違っている

**解決策**: 正しいパスワードを入力するか、パスワードリセットフローを使用

### エラー3: パスワードポリシー違反

**エラー**:
```json
{
  "error": "invalid_new_password",
  "error_description": "password minLength is 8"
}
```

**原因**: 新しいパスワードがテナントのパスワードポリシーを満たしていない

**解決策**: ポリシーに準拠したパスワードを設定
```bash
# ❌ 間違い: 8文字未満
"new_password": "Pass1"

# ✅ 正しい: 8文字以上
"new_password": "SecurePass123!"
```

### エラー4: password:reset スコープ不足（パスワードリセット時）

**エラー**:
```json
{
  "error": "insufficient_scope",
  "error_description": "The request requires higher privileges than provided by the access token."
}
```

**原因**: アクセストークンに `password:reset` スコープが含まれていない

**解決策**: `scope=password:reset` を含むAuthorization Requestで再認証

---

## API Reference

### パスワード変更 API

```http
POST /{tenant-id}/v1/me/password/change
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "current_password": "OldPassword123!",
  "new_password": "NewSecurePass456!"
}
```

#### 必要なスコープ

特別なスコープは不要（通常のアクセストークンで実行可能）

#### レスポンス

| ステータス | 説明 |
|-----------|------|
| 200 OK | パスワード変更成功 |
| 400 Bad Request | パラメータ不正、ポリシー違反、現在のパスワード不一致 |
| 401 Unauthorized | 認証エラー |

### パスワードリセット API

```http
POST /{tenant-id}/v1/me/password/reset
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "new_password": "NewSecurePass456!"
}
```

#### 必要なスコープ

`password:reset` スコープが必須

#### レスポンス

| ステータス | 説明 |
|-----------|------|
| 200 OK | パスワードリセット成功 |
| 400 Bad Request | パラメータ不正、ポリシー違反 |
| 401 Unauthorized | 認証エラー |
| 403 Forbidden | スコープ不足 |

---

## セキュリティのベストプラクティス

### ✅ 推奨

1. **パスワードポリシーの適切な設定**

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

2. **パスワード変更後のセッション管理**

   パスワード変更後、他のセッションを無効化することを検討：
   - 不正アクセスの疑いがある場合に有効
   - 全デバイスからの再ログインを強制

3. **パスワードリセットの代替認証**

   パスワードリセットフローでは、信頼性の高い代替認証を使用：
   - メールOTP
   - SMS OTP
   - 秘密の質問（推奨度低）

4. **レート制限**

   パスワード変更・リセットAPIにはレート制限を設定：
   - ブルートフォース攻撃の防止
   - アカウント列挙攻撃の防止

### ❌ 避けるべき設定

1. **弱いパスワードポリシー**
   ```json
   {
     "min_length": 4  // ❌ 短すぎる
   }
   ```

2. **パスワードリセット時の不十分な本人確認**

   メール検証なしでパスワードリセットを許可すると、アカウント乗っ取りのリスクがあります。

3. **パスワード履歴チェックなし**（将来の機能）

   過去に使用したパスワードの再利用を許可すると、セキュリティが低下します。

---

## 次のステップ

✅ パスワード管理機能を理解しました！

### 認証機能の拡張
- [How-to: 認証ポリシー基礎編](./07-authentication-policy.md) - より柔軟な認証設定
- [How-to: MFA設定](../phase-2-security/01-mfa-setup.md) - SMS OTP、TOTP、FIDO2

### セキュリティ強化
- [How-to: セキュリティイベントHooks](../phase-4-extensions/04-security-event-hooks.md) - パスワード変更時の通知

---

## 関連ドキュメント

- [Concept: Password Policy](../content_03_concepts/02-identity-management/concept-02-password-policy.md) - パスワードポリシー詳細
- [Concept: 認証ポリシー](../content_03_concepts/03-authentication-authorization/concept-01-authentication-policy.md) - 認証ポリシーの詳細
- [How-to: ユーザー登録](./05-user-registration.md) - ユーザー登録フロー
- [API Reference](../content_07_reference/api-reference.md) - API仕様

---

**最終更新**: 2025-12-15
**難易度**: ⭐⭐☆☆☆（初級〜中級）
**対象**: パスワード管理機能を実装する管理者・開発者
