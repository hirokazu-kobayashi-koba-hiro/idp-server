# Email認証

このドキュメントは、`email-authentication` 方式による認証処理の `概要`・`設定`・`利用方法` について説明します。

---

## 概要

Email認証は、ユーザーのメールアドレスにワンタイムコードまたは認証リンクを送信し、それを使って本人確認を行う認証方式です。

`idp-server` ではEmail認証機能を2つのパターンで提供します：

1. 外部Email認証サービスを利用したTOTP
2. idp-serverで認証コード生成・検証 + 外部Email送信サービスを利用したTOTP

Email認証は以下の複数のインタラクションを連続的に実行することで成立します：

* `email-authentication-challenge`: 認証用コードまたはリンクの送信
* `email-authentication`: ワンタイムコードやリンクパラメータの検証

---

## 設定

設定には `email` をキーとする `AuthenticationConfiguration` をテナントごとに登録する必要があります。

### 外部Email認証サービスを利用したTOTP

#### 設定項目（テンプレート定義）

| 項目                        | 内容                       |
|---------------------------|--------------------------|
| `type`                    | `external-authn` 固定      |
| `transaction_id_param`    | 外部サービスのトランザクションIDのパラメータ名 |
| `verification_code_param` | 外部サービスの検証コードのパラメータ名      |
| `oauth_authorization`     | 外部APIへの認証情報（OAuth2設定）    |
| `executions`              | 外部サービスAPI定義              |

#### API定義項目

| 項目                  | 説明                                                   |
|---------------------|------------------------------------------------------|
| `url`               | 外部サービスAPIのURL。POSTで呼び出される。                           |
| `method`            | HTTPメソッド（通常は "POST"）                                 |
| `headers`           | API呼び出し時のHTTPヘッダ（`Content-Type`, `Authorization` など） |
| `dynamic_body_keys` | APIリクエストbodyに含める動的キー。ユーザー入力などから取得                    |
| `static_body`       | APIリクエストbodyに含める固定キー（例：`{"service_code": "001"}`）    |

### idp-serverで認証コード生成・検証 + 外部Email送信サービスを利用したTOTP (予定)

#### Email認証設定

| 項目                       | 説明               |
|--------------------------|------------------|
| `retry_count_limitation` | リトライ上限回数         |
| `expire_seconds`         | ワンタイムコードの有効期限（秒） |
| `length`                 | 生成されるコードの桁数      |
| `templates`              | メッセージテンプレート群     |

#### 外部サービス設定

| 項目                    | 内容                       |
|-----------------------|--------------------------|
| `type`                | `external-sms-sender` 固定 |
| `oauth_authorization` | 外部APIへの認証情報（OAuth2設定）    |
| `executions`          | 外部サービスAPI定義              |

### idp-serverで認証コード生成・検証 + SMTP

#### Email認証設定

| 項目                       | 説明               |
|--------------------------|------------------|
| `retry_count_limitation` | リトライ上限回数         |
| `expire_seconds`         | ワンタイムコードの有効期限（秒） |
| `length`                 | 生成されるコードの桁数      |
| `templates`              | メッセージテンプレート群     |

#### SMTP設定

| 項目                | 型       | 説明                                |
|-------------------|---------|-----------------------------------|
| `host`            | string  | SMTPサーバーのホスト名（例：`smtp.gmail.com`） |
| `port`            | integer | SMTPポート番号（通常は `587` または `465`）    |
| `username`        | string  | 認証に使用するメールアドレス（送信元）               |
| `password`        | string  | SMTPサーバーに接続するためのパスワードまたはアプリパスワード  |
| `auth`            | boolean | SMTP認証を使用するかどうか（`true`で有効）        |
| `starttls.enable` | boolean | STARTTLSを有効にするか（`true`で通信が暗号化される） |


### idp-serverで認証コード生成・検証 + AWS SES を利用したTOTP (予定)

TODO

## 利用方法

### 事前準備

* テナントに `type = "email"` の設定（`AuthenticationConfiguration`）を登録する

### 認証時の流れ

1. `prompt=login` もしくは `acr_values` によってEmail認証が要求される
2. `/v1/authorizations/{id}/email-authentication-challenge` にリクエストし、認証コードまたはリンクをメールで送信
3. ユーザーが受信したメールに含まれるコードやリンクを用いて `/v1/authorizations/{id}/email-authentication` にリクエスト

---

## 備考

* 認証コード／リンクの形式や送信手段はテンプレートにより柔軟にカスタマイズ可能です。
* 複数のEmail送信方法（SMTP, API等）を `EmailAuthenticationExecutors` によって拡張可能です。
