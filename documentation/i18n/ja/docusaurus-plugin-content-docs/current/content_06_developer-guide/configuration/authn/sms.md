# SMS認証

このドキュメントは、`sms-authentication` 方式による認証処理の `概要`・`設定`・`利用方法` について説明します。

---

## 概要

SMS認証は、ユーザーの電話番号にワンタイムコード（OTP）を送信し、それを使って本人確認を行う多要素認証方式の一つです。

`idp-server` ではSMS認証機能を2つのパターンで提供します。

1. 外部SMS認証サービスを利用したTOTP
2. idp-serverで認証コード生成・検証 + 外部SMS送信サービスを利用したTOTP

SMS認証は以下の複数のインタラクションを連続的に実行することで成立します：

* `sms-authentication-challenge`: 認証用ワンタイムコードの送信
* `sms-authentication`: ワンタイムコードの検証

## 設定

設定には `sms` をキーとする `AuthenticationConfiguration` をテナントごとに登録する必要があります。

### 外部SMS認証サービスを利用したTOTP

#### 設定項目（テンプレート定義）

| 項目                        | 内容                       |
|---------------------------|--------------------------|
| `type`                    | `external-authn` 固定      |
| `transaction_id_param`    | 外部サービスのトランザクションIDのパラメータ名 |
| `verification_code_param` | 外部サービスの検証コードのパラメータ名      |
| `oauth_authorization`     | 外部APIへの認証情報（OAuth2設定）    |
| `executions`              | 外部サービスAPI定義              |

外部SMS認証サービスのAPI定義を2つ設定することができます。

1. challenge 認証コードをSMSで送信するための設定
2. verify 認証コードを検証するための設定

#### API定義項目

外部サービスのAPI仕様に応じて設定を行います。

| 項目                  | 説明                                                   |
|---------------------|------------------------------------------------------|
| `url`               | 外部サービスAPIのURL。POSTで呼び出される。                           |
| `method`            | HTTPメソッド（通常は `"POST"`）                               |
| `headers`           | API呼び出し時のHTTPヘッダ（`Content-Type`, `Authorization` など） |
| `dynamic_body_keys` | APIリクエストbodyに含める動的キー。ユーザー入力などから取得                    |
| `static_body`       | APIリクエストbodyに含める固定キー（例：`{"service_code": "001"}`）    |

### idp-serverで認証コード生成・検証 + 外部SMS送信サービスを利用したTOTP (予定)


#### SMS認証設定

| 項目         | 説明                    |
|------------|-----------------------|
| `type`     | 実行タイプ（executor名）      |
| `provider` | 使用するSMSプロバイダ（Twilio等） |
| `ttl`      | ワンタイムコードの有効期限（秒）      |
| `length`   | 生成されるコードの桁数           |

#### 外部サービス設定

| 項目                        | 内容                       |
|---------------------------|--------------------------|
| `type`                    | `external-sms-sender` 固定 |
| `oauth_authorization`     | 外部APIへの認証情報（OAuth2設定）    |
| `executions`              | 外部サービスAPI定義              |

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

- テナントに `type = "sms"` の設定（`AuthenticationConfiguration`）を登録する

### 認証時

1. 認可フロー中に `prompt=login` または `acr_values` によってSMS認証が要求される
2. `/v1/authorizations/{id}/sms-authentication-challenge` によって認証コードがSMS送信
3. SMS送信後に、`/v1/authorizations/{id}/sms-authentication` 、で認証コードを検証する

---


## 備考

* この処理は `sms-authentication-challenge` によって事前に送信されたワンタイムコードの確認に使用されます。
* 成功時は、SMS認証が完了した `Authentication` 情報が返却され、認可処理が進行します。
* 複数のSMS認証方式を `SmsAuthenticationExecutors` によって拡張可能です。
