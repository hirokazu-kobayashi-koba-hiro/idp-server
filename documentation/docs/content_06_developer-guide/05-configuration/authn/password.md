# パスワード認証

このドキュメントは、パスワードによる認証処理の`概要`・`設定`・`利用方法`及び`内部ロジック`について説明します。

この方式では、ユーザーID（通常はメールアドレス）とパスワードを用いた、一般的な認証フローを実装しています。

---

## 概要
パスワード認証は、ユーザーがID（一般にメールアドレス）とパスワードを入力してログインする、もっとも基本的で広く使われている認証方式です。

この方式は、認可リクエストに対応したログイン画面から実行され、ユーザーのクレデンシャルを検証することで認証を完了します。

本実装では、以下の特徴を備えています：
* ユーザー入力のパスワードと事前に登録されたハッシュ化されたパスワードを比較します。
* 成功時にはpwd認証手段およびACR値を付加した認証オブジェクトを返却
* 失敗時にはセキュリティイベント（例：password_failure）をトリガー

テナントは `password-authentication` の登録を行うことでこの機能を利用することが可能です。


## 設定

パスワード認証は設定不要で使用できます。

テナント作成時に自動的に有効化され、以下の機能が利用可能になります：

* ユーザーID（メールアドレス等）とパスワードによる認証
* ハッシュ化されたパスワードの安全な照合
* 認証成功時の`pwd`認証手段とACR値の付与

### 前提条件

* ユーザーがテナントに登録されていること
* ユーザーのパスワードが適切にハッシュ化されて保存されていること

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
