# パスワード認証（password-authentication）

このドキュメントは、`password-authentication` 方式による認証処理の概要、利用方法、および設定について説明します。
この方式では、ユーザーID（通常はメールアドレス）とパスワードを用いた、一般的な認証フローを実装しています。

---

## 利用方法

1. テナントに `type = "password-authentication"` の設定を登録する
2. 認可リクエストにてログイン画面を表示（例：`prompt=login`）
3. ログインフォームで入力された `username` と `password` を使って、以下のエンドポイントにPOSTする：

```
POST /authorizations/{id}/password-authentication
```

リクエストボディの例：

```json
{
  "username": "user@example.com",
  "password": "P@ssw0rd!"
}
```

---

## 内部ロジック

1. **ユーザー検索**
   `userQueryRepository.findByEmail(...)` により、テナント + ユーザーID + provider\_id でユーザー情報を検索。

2. **パスワード検証**
   `passwordVerificationDelegation.verify(password, user.hashedPassword())` によって、入力されたパスワードをハッシュと比較。

3. **認証失敗時の応答**
   ユーザーが存在しない、またはパスワードが不一致の場合は `CLIENT_ERROR` を返し、`password_failure` イベントを発行。

4. **認証成功処理**
   成功時には以下の情報で `Authentication` オブジェクトを構成：

    * `time`: 現在時刻（SystemDateTime）
    * `methods`: `["pwd"]`
    * `acr_values`: `["urn:mace:incommon:iap:silver"]`

5. **成功レスポンス**
   `user` と `authentication` を含むレスポンスを生成し、`password_success` イベントを発行。

---

## スキーマ設定

この方式はユーザー属性定義スキーマを直接使わず、`username`（= email等）と `password` の検証に特化しています。
ただし、事前に `User` 情報が正しく登録されている必要があります。

---

## 備考

* `provider_id` は省略時 `"idp-server"` が使用されます。
* 認証手段（methods）とACR値（acr\_values）は固定で `pwd` / `urn:mace:incommon:iap:silver` を使用しています。
* セキュリティイベント（成功・失敗）はそれぞれ `password_success`, `password_failure` としてログに記録されます。
