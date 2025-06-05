# FIDO-UAF認証

このドキュメントは、`fido-uaf-authentication` 方式による生体認証・パスワードレス認証処理の `概要`・`設定`・`利用方法`について説明します。

---

## 概要

FIDO-UAF（Universal Authentication Framework）は、指紋・顔認証などの生体認証を活用し、パスワードレスな本人認証を可能にする仕組みです。

FIDO-UAF認証は以下の複数のインタラクションを連続的に実行することで成立します：

登録時：

* `fido-uaf-registration-challenge`: 生体認証登録のチャレンジを生成
* `fido-uaf-registration`: 登録応答の受信とデバイス登録

認証時：

* `fido-uaf-authentication-challenge`: 認証のチャレンジを生成
* `fido-uaf-authentication`: 認証応答を受信し検証

---

## 設定

設定には `fido-uaf` をキーとする `AuthenticationConfiguration` をテナントごとに登録する必要があります。

### 外部FIDOサーバー

#### 設定項目（テンプレート定義）

| 項目                    | 内容                    |
|-----------------------|-----------------------|
| `type`                | `external-authn` 固定   |
| `device_id_param`     | 外部サービスのデバイスIDのパラメータ名  |
| `oauth_authorization` | 外部APIへの認証情報（OAuth2設定） |
| `executions`          | 外部サービスAPI定義           |

外部FIDOサーバーのAPI定義を6つ設定することができます。

1. facets FIDO-UAFのメタデータ取得API用の設定
2. registration-challenge 登録チャレンジ用のAPI設定
3. registration 登録用のAPI設定
4. authentication-challenge 認証チャレンジ用のAPI設定
5. authentication 認証用のAPI設定
6. delete-key 鍵の削除用のAPI設定

#### API定義項目

外部サービスのAPI仕様に応じて設定を行います。

| 項目                  | 説明                                                   |
|---------------------|------------------------------------------------------|
| `url`               | 外部サービスAPIのURL。POSTで呼び出される。                           |
| `method`            | HTTPメソッド（通常は `"POST"`）                               |
| `headers`           | API呼び出し時のHTTPヘッダ（`Content-Type`, `Authorization` など） |
| `dynamic_body_keys` | APIリクエストbodyに含める動的キー。ユーザー入力などから取得                    |
| `static_body`       | APIリクエストbodyに含める固定キー（例：`{"service_code": "001"}`）    |

---

## 利用方法

### 事前準備

* テナントに `type = "fido-uaf"` の設定（`AuthenticationConfiguration`）を登録する

### 認証時の流れ

1. クライアントが `/v1/authorizations/{id}/fido-uaf-authentication-challenge` にリクエストし、チャレンジを取得
2. FIDO対応の認証器（デバイス）で認証を実行し、認証応答（signed assertion）を
   `/v1/authorizations/{id}/fido-uaf-authentication` に送信
3. サーバー側で応答を検証し、成功時に `Authentication` オブジェクトを生成して返却

---

## 備考

* FIDO-UAFはユーザーにとってパスワード入力不要なUXを実現しながら、高いセキュリティを提供します
