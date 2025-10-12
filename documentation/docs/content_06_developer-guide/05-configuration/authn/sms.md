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

設定には `type = "sms"` の `AuthenticationConfiguration` をテナントごとに登録する必要があります。

SMS認証は `interactions` ベースの設定構造を持ち、以下の2つのインタラクションを定義します：

* `sms-authentication-challenge`: SMS送信処理
* `sms-authentication`: コード検証処理

### トップレベル設定項目

| 項目            | 内容                           |
|---------------|------------------------------|
| `id`          | 設定ID（UUID形式）                  |
| `type`        | `sms` 固定                      |
| `metadata`    | メタデータ（型、パラメータ名など）            |
| `interactions` | インタラクション定義（challenge/verify） |

### メタデータ設定項目

| 項目                        | 内容                                           |
|---------------------------|----------------------------------------------|
| `type`                    | `external`: 外部SMS認証サービス / `internal`: 内部OTP生成 |
| `verification_code_param` | 検証コードのパラメータ名（デフォルト: `verification_code`）      |
| `transaction_id_param`    | トランザクションIDのパラメータ名（外部サービス利用時）                |

---

## 設定パターン

### パターン1: 外部SMS認証サービスを利用したTOTP

外部のSMS認証APIサービスを利用し、OTP生成・検証を外部に委譲するパターンです。

#### challenge インタラクション設定

| 項目                            | 説明                                      |
|-------------------------------|-----------------------------------------|
| `execution.function`          | `http_request` 固定                       |
| `execution.http_request`      | 外部API設定（URL、メソッド、認証、マッピングルール）          |
| `execution.http_request_store` | 外部APIレスポンスの保存設定（`transaction_id`などを保存） |
| `response.body_mapping_rules` | レスポンスマッピング                              |

#### verify インタラクション設定

| 項目                                  | 説明                                   |
|-------------------------------------|--------------------------------------|
| `execution.function`                | `http_request` 固定                    |
| `execution.previous_interaction`    | challenge時に保存したデータの参照キー             |
| `execution.http_request`            | 外部検証API設定                            |
| `execution.http_request.body_mapping_rules` | `verification_code` と `transaction_id` のマッピング |
| `response.body_mapping_rules`       | レスポンスマッピング                           |

#### 設定例

```json
{
  "id": "0f12803e-37b6-437e-8ca9-5822bd852b74",
  "type": "sms",
  "metadata": {
    "type": "external",
    "verification_code_param": "verification_code"
  },
  "interactions": {
    "sms-authentication-challenge": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://external-sms-service.com/challenge",
          "method": "POST",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "https://external-sms-service.com/token",
            "client_id": "your-client-id",
            "username": "username",
            "password": "password"
          },
          "header_mapping_rules": [
            {
              "static_value": "application/json",
              "to": "Content-Type"
            }
          ],
          "body_mapping_rules": [
            {
              "from": "$.request_body",
              "to": "*"
            }
          ]
        },
        "http_request_store": {
          "key": "sms-authentication-challenge",
          "interaction_mapping_rules": [
            {
              "from": "$.response_body.transaction_id",
              "to": "transaction_id"
            }
          ]
        }
      },
      "response": {
        "body_mapping_rules": [
          {
            "from": "$.execution_http_request.response_body",
            "to": "*"
          }
        ]
      }
    },
    "sms-authentication": {
      "execution": {
        "function": "http_request",
        "previous_interaction": {
          "key": "sms-authentication-challenge"
        },
        "http_request": {
          "url": "https://external-sms-service.com/verify",
          "method": "POST",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "https://external-sms-service.com/token",
            "client_id": "your-client-id",
            "username": "username",
            "password": "password"
          },
          "body_mapping_rules": [
            {
              "from": "$.request_body.verification_code",
              "to": "verification_code"
            },
            {
              "from": "$.interaction.transaction_id",
              "to": "transaction_id"
            }
          ]
        }
      },
      "response": {
        "body_mapping_rules": [
          {
            "from": "$.execution_http_request.response_body",
            "to": "*"
          }
        ]
      }
    }
  }
}
```

---

### パターン2: idp-server内部でOTP生成・検証

idp-server内部でワンタイムパスワードを生成・検証し、外部SMSサービスは送信のみを担当するパターンです。

#### challenge インタラクション設定

| 項目                             | 説明                                      |
|--------------------------------|-----------------------------------------|
| `execution.function`           | `sms_authentication_challenge` 固定       |
| `execution.details.sender_type` | SMSサービスタイプ（`twilio`, `no_action`など）    |
| `execution.details.templates`  | テンプレート定義（`{VERIFICATION_CODE}`プレースホルダー使用） |
| `execution.details.retry_count_limitation` | 検証リトライ上限回数（デフォルト: 5）                    |
| `execution.details.expire_seconds` | OTP有効期限（秒）（デフォルト: 300）                  |

#### テンプレート設定項目

| 項目       | 内容                              |
|----------|-------------------------------------|
| `subject` | SMSタイトル（サービスによっては使用されない場合あり）       |
| `body`   | SMS本文。`{VERIFICATION_CODE}` と `{EXPIRE_SECONDS}` のプレースホルダーが使用可能 |

#### verify インタラクション設定

| 項目                       | 説明                           |
|--------------------------|------------------------------|
| `execution.function`     | `sms_authentication` 固定      |
| `execution.details`      | 検証設定（リトライ上限、有効期限）            |

#### 設定例

```json
{
  "id": "0f12803e-37b6-437e-8ca9-5822bd852b74",
  "type": "sms",
  "metadata": {
    "type": "internal",
    "verification_code_param": "verification_code"
  },
  "interactions": {
    "sms-authentication-challenge": {
      "execution": {
        "function": "sms_authentication_challenge",
        "details": {
          "sender_type": "no_action",
          "templates": {
            "registration": {
              "subject": "[ID Verification] Your signup SMS confirmation code",
              "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\n– IDP Support"
            },
            "authentication": {
              "subject": "[ID Verification] Your login SMS confirmation code",
              "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\n– IDP Support"
            }
          },
          "retry_count_limitation": 5,
          "expire_seconds": 300
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.response_body", "to": "*" }
        ]
      }
    },
    "sms-authentication": {
      "execution": {
        "function": "sms_authentication",
        "details": {
          "retry_count_limitation": 5,
          "expire_seconds": 300
        }
      }
    }
  }
}
```

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
