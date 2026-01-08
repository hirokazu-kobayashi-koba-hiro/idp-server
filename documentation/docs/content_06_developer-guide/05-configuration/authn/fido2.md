# FIDO2/WebAuthn認証

このドキュメントは、`fido2` 方式によるパスワードレス認証・多要素認証処理の `概要`・`設定`・`利用方法`
について説明します。

---

## 概要

FIDO2/WebAuthn（Web
Authentication）は、FIDO2の仕様に基づいたWeb標準の認証方式です。ユーザーが所持する公開鍵ベースの認証器（セキュリティキー、デバイス内蔵の生体認証など）を使って、安全かつパスワードレスな認証を実現します。

FIDO2認証は以下の複数のインタラクションを連続的に実行することで成立します：

登録時：

* `fido2-registration-challenge`: 登録チャレンジ生成
* `fido2-registration`: クライアントからの登録応答を受け取り検証・登録

認証時：

* `fido2-authentication-challenge`: 認証チャレンジ生成
* `fido2-authentication`: クライアントからの認証応答を検証

---

## 設定

設定には `fido2` をキーとする `AuthenticationConfiguration` をテナントごとに登録する必要があります。

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

### Webauthn4j

| 項目                           | 内容                                                 |
|------------------------------|----------------------------------------------------|
| `rp_id`                      | Relying Partyの識別子。通常はドメイン名（例：`example.com`）        |
| `origin`                     | WebAuthnクライアントのオリジン（例：`https://app.example.com`）   |
| `rp_name`                    | Relying Partyの表示名。ユーザー端末のUIに表示される                  |
| `token_binding_id`           | トークンバインディングのID（省略可、ほとんど使われない）                      |
| `require_resident_key`       | 認証器に鍵の保存（発見可能クレデンシャル）を要求するか                        |
| `attestation_preference`     | アテステーションの要求度合い。`none`, `indirect`, `direct` のいずれか  |
| `user_presence_required`     | タッチなどのユーザー操作（Presence）を要求するか                       |
| `authenticator_attachment`   | 認証器の種別。`platform`（端末内蔵）か `cross-platform`（USBキーなど） |
| `user_verification_required` | PINや生体認証によるユーザー検証を必須にするか                           |

---

## 利用方法

### 事前準備

- テナントに `type = "fido2"` の設定（`AuthenticationConfiguration`）を登録する

### 認証時の流れ

1. クライアントが `/v1/authorizations/{id}/fido2-authentication-challenge`
   にリクエストし、チャレンジ（PublicKeyCredentialRequestOptions）を取得
2. ブラウザのWebAuthn APIを使ってユーザーが認証器で認証を実行し、認証応答（Credential）を取得
3. クライアントが `/v1/authorizations/{id}/fido2-authentication` に応答を送信
4. サーバー側で公開鍵検証などを行い、成功時に `Authentication` を返却

---

## デバイス登録の制約

### 事前認証の必須要件

FIDO2デバイスの登録（`fido2-registration-challenge` および `fido2-registration`）には、**事前にユーザー認証が完了している**必要があります。

未認証状態でFIDO2デバイス登録を試みると、以下のエラーが返されます：

```json
{
  "error": "unauthorized",
  "error_description": "User must be authenticated before registering a FIDO2 device."
}
```

この制約により、FIDO2デバイスのみでユーザー登録を完結することはできません。ユーザーは最初にパスワード認証やEmail認証など、他の認証方式で認証を行う必要があります。

### device_registration_conditions ポリシー

認証ポリシーの `device_registration_conditions` を設定することで、デバイス登録に必要な認証レベル（ACR）を強制できます。

例えば、以下の設定ではEmail認証または既存のFIDO2デバイスでの認証を完了しないとデバイス登録ができません：

```json
{
  "device_registration_conditions": {
    "any_of": [
      [{ "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
      [{ "path": "$.fido2-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }]
    ]
  }
}
```

ポリシーを満たさない場合、以下のエラーが返されます：

```json
{
  "error": "forbidden",
  "error_description": "Current authentication level does not meet device registration requirements. Please complete required authentication steps (e.g., MFA or existing device authentication)."
}
```

---

## 備考

* WebAuthnはプラットフォーム認証器（デバイス内蔵）・ローミング認証器（セキュリティキー）に対応しています。
* クライアントとのやりとりはJSONとCBOR/ArrayBufferが混在するため、エンコード／デコード処理が必要です。
* FIDO2対応のブラウザ・デバイスが必要です。

---
