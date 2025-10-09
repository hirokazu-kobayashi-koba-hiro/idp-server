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

設定には `type = "fido-uaf"` の `AuthenticationConfiguration` をテナントごとに登録する必要があります。

FIDO-UAF認証は `interactions` ベースの設定構造を持ち、以下の6つのインタラクションを定義します。

### トップレベル設定項目

| 項目            | 内容                           |
|---------------|------------------------------|
| `id`          | 設定ID（UUID形式）                  |
| `type`        | `fido-uaf` 固定                 |
| `attributes`  | 外部サービス設定（type, service_name） |
| `interactions` | インタラクション定義（6つのFIDO操作）         |

### attributes設定項目

| 項目                | 内容                              |
|-------------------|-----------------------------------|
| `type`            | `external` 固定（外部FIDOサーバー利用）      |
| `service_name`    | サービス識別名（例：`mocky`）               |

---

## インタラクション概要

FIDO-UAF認証では以下の6つのインタラクションを設定します。すべて `http_request` executorを使用して外部FIDOサーバーと連携します。

### フロー別分類

| フェーズ | インタラクション | 用途 | 呼び出し元 |
|---------|----------------|------|-----------|
| **初期設定** | `fido-uaf-facets` | Trusted Facetsメタデータ取得 | クライアントアプリ |
| **デバイス登録** | `fido-uaf-registration-challenge` | 登録チャレンジ生成 | 管理API |
| | `fido-uaf-registration` | デバイス登録・検証 | 管理API |
| **認証** | `fido-uaf-authentication-challenge` | 認証チャレンジ生成 | 認可フロー |
| | `fido-uaf-authentication` | 認証応答検証 | 認可フロー |
| **登録解除** | `fido-uaf-deregistration` | デバイス登録削除 | 管理API |

---

## OAuth2認証設定

各インタラクションの`oauth_authorization`で外部FIDOサーバーへの認証を設定します。

| 項目                      | 説明                                |
|-------------------------|-------------------------------------|
| `type`                  | `password` (Resource Owner Password Credentials) |
| `token_endpoint`        | トークンエンドポイントURL                     |
| `client_id`             | クライアントID                           |
| `username`              | ユーザー名                              |
| `password`              | パスワード                              |
| `scope`                 | スコープ                               |
| `cache_enabled`         | トークンキャッシュ有効化（`true`/`false`）       |
| `cache_ttl_seconds`     | キャッシュ有効期限（秒）                       |
| `cache_buffer_seconds`  | キャッシュ更新バッファ時間（秒）                   |

**キャッシング**: 外部FIDO APIへの認証トークン取得はコストが高いため、`cache_enabled: true`でトークンをキャッシュすることを推奨します。

---

## 設定例

```json
{
  "id": "dd290cfa-e1f4-4a5f-88bf-e4958519ceac",
  "type": "fido-uaf",
  "attributes": {
    "type": "external",
    "service_name": "mocky"
  },
  "interactions": {
    "fido-uaf-facets": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "http://localhost:4000/fido-uaf/facets",
          "method": "GET",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "http://localhost:4000/token",
            "client_id": "your-client-id",
            "username": "username",
            "password": "password",
            "scope": "application",
            "cache_buffer_seconds": 10,
            "cache_ttl_seconds": 1800,
            "cache_enabled": true
          },
          "header_mapping_rules": [
            {
              "static_value": "application/json",
              "to": "Content-Type"
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
    "fido-uaf-registration-challenge": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "http://localhost:4000/fido-uaf/registration-challenge",
          "method": "POST",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "http://localhost:4000/token",
            "client_id": "your-client-id",
            "username": "username",
            "password": "password",
            "scope": "application",
            "cache_buffer_seconds": 10,
            "cache_ttl_seconds": 1800,
            "cache_enabled": true
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
    "fido-uaf-registration": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "http://localhost:4000/fido-uaf/registration",
          "method": "POST",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "http://localhost:4000/token",
            "client_id": "your-client-id",
            "username": "username",
            "password": "password",
            "scope": "application",
            "cache_buffer_seconds": 10,
            "cache_ttl_seconds": 1800,
            "cache_enabled": true
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
    "fido-uaf-authentication-challenge": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "http://localhost:4000/fido-uaf/authentication-challenge",
          "method": "POST",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "http://localhost:4000/token",
            "client_id": "your-client-id",
            "username": "username",
            "password": "password",
            "scope": "application",
            "cache_buffer_seconds": 10,
            "cache_ttl_seconds": 1800,
            "cache_enabled": true
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
    "fido-uaf-authentication": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "http://localhost:4000/fido-uaf/authentication",
          "method": "POST",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "http://localhost:4000/token",
            "client_id": "your-client-id",
            "username": "username",
            "password": "password",
            "scope": "application",
            "cache_buffer_seconds": 10,
            "cache_ttl_seconds": 1800,
            "cache_enabled": true
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
    "fido-uaf-deregistration": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "http://localhost:4000/fido-uaf/delete-key",
          "method": "POST",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "http://localhost:4000/token",
            "client_id": "your-client-id",
            "username": "username",
            "password": "password",
            "scope": "application",
            "cache_buffer_seconds": 10,
            "cache_ttl_seconds": 1800,
            "cache_enabled": true
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

## 利用方法

### 全体フロー

FIDO-UAF認証は以下の4つのフェーズで構成されます：

1. **Facetsメタデータ取得**: クライアントアプリが信頼できるファセット情報を取得
2. **デバイス登録**: 管理APIでユーザーの生体認証デバイスを登録
3. **認証**: 認可フロー中で登録済みデバイスによる認証
4. **デバイス登録解除**: 管理APIでデバイスの登録を削除

---

### 1. Facetsメタデータ取得

クライアントアプリケーションがFIDO-UAFの信頼できるファセット（Trusted Facets）情報を取得します。

```http
GET /{tenant-id}/fido-uaf/facets
```

**レスポンス**: FIDO-UAF仕様に準拠したTrusted Facets List

---

### 2. デバイス登録フロー（管理API）

ユーザーが自分の生体認証デバイスを登録します。管理API経由で実行します。

#### Step 1: 登録チャレンジ取得

```http
POST /v1/me/authentication-devices/fido-uaf/registration-challenge
Authorization: Bearer {access_token}
```

**レスポンス**: FIDO-UAF登録チャレンジ（Registration Request）

#### Step 2: デバイス登録

クライアントアプリが生体認証デバイスで署名した登録応答をサーバーに送信します。

```http
POST /v1/me/authentication-devices/fido-uaf/registration
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "uafResponse": "eyJ...（FIDO-UAF Registration Response）"
}
```

**レスポンス**: 登録成功時のデバイス情報

---

### 3. 認証フロー（認可フロー中）

認可リクエスト中にFIDO-UAF認証を実行します。

#### Step 1: 認証チャレンジ取得

```http
POST /v1/authorizations/{authorization_id}/fido-uaf-authentication-challenge
```

**レスポンス**: FIDO-UAF認証チャレンジ（Authentication Request）

#### Step 2: 認証

クライアントアプリが生体認証デバイスで署名した認証応答をサーバーに送信します。

```http
POST /v1/authorizations/{authorization_id}/fido-uaf-authentication
Content-Type: application/json

{
  "uafResponse": "eyJ...（FIDO-UAF Authentication Response）"
}
```

**レスポンス**: 認証成功時の認証情報

---

### 4. デバイス登録解除フロー（管理API）

登録済みデバイスを削除します。

```http
POST /v1/me/authentication-devices/fido-uaf/deregistration
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "device_id": "device-uuid"
}
```

**レスポンス**: 削除成功確認

---

## 備考

* FIDO-UAFはユーザーにとってパスワード入力不要なUXを実現しながら、高いセキュリティを提供します

## 参考
* プロトコル
  * https://fidoalliance.org/specs/fido-uaf-v1.1-ps-20170202/fido-uaf-protocol-v1.1-ps-20170202.html
* Facets仕様
  * https://fidoalliance.org/specs/fido-uaf-v1.2-rd-20171128/fido-appid-and-facets-v1.2-rd-20171128.html