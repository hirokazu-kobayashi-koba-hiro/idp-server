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

FIDO-UAF認証を使用するには、テナントに `type = "fido-uaf"` の認証設定を登録する必要があります。

### 基本構造

すべての認証設定は、統一されたinteractions形式を使用します。FIDO-UAF認証では以下のinteractionsを定義します：

1. **fido-uaf-facets**: FIDO-UAFメタデータ取得
2. **fido-uaf-registration-challenge**: 登録チャレンジ生成
3. **fido-uaf-registration**: 登録応答検証
4. **fido-uaf-authentication-challenge**: 認証チャレンジ生成
5. **fido-uaf-authentication**: 認証応答検証
6. **fido-uaf-deregistration**: 認証器削除

```json
{
  "id": "UUID",
  "type": "fido-uaf",
  "attributes": {
    "type": "external",
    "service_name": "external-fido-service",
    "device_id_param": "user_id"
  },
  "metadata": {},
  "interactions": {
    "fido-uaf-facets": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://fido-service.example.com/facets",
          "method": "GET",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "https://fido-service.example.com/token",
            "client_id": "your-client-id",
            "username": "username",
            "password": "password",
            "scope": "application",
            "cache_buffer_seconds": 10,
            "cache_ttl_seconds": 1800,
            "cache_enabled": true
          }
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body", "to": "*" }
        ]
      }
    }
  }
}
```

**情報源**: `config/examples/e2e/test-tenant/authentication-config/fido-uaf/external.json`

### 設定項目

| フィールド | 説明 |
|-----------|------|
| `id` | 設定ID（UUID） |
| `type` | `"fido-uaf"` 固定 |
| `attributes.type` | `"external"` - 外部FIDO-UAFサービス連携 |
| `attributes.service_name` | 外部サービス識別名 |
| `attributes.device_id_param` | デバイスIDパラメータ名 |
| `interactions` | 各interactionの定義 |

### HTTP Request 設定

各interactionの`execution.http_request`で外部APIへのリクエストを設定：

| 項目 | 説明 |
|------|------|
| `url` | 外部FIDOサービスのAPIエンドポイント |
| `method` | HTTPメソッド（GET/POST） |
| `auth_type` | 認証タイプ（`oauth2`, `bearer`, `none`等） |
| `oauth_authorization` | OAuth2認証設定 |
| `header_mapping_rules` | HTTPヘッダーマッピング |
| `body_mapping_rules` | リクエストボディマッピング |

### OAuth2認証設定

外部FIDOサービスへの認証を設定：

| 項目 | 説明 |
|------|------|
| `type` | Grant Type（`password`, `client_credentials`等） |
| `token_endpoint` | トークンエンドポイントURL |
| `client_id` | クライアントID |
| `username` | ユーザー名（passwordグラントの場合） |
| `password` | パスワード（passwordグラントの場合） |
| `scope` | 要求スコープ |
| `cache_enabled` | トークンキャッシュ有効化 |
| `cache_ttl_seconds` | キャッシュTTL（秒） |
| `cache_buffer_seconds` | キャッシュ更新バッファ（秒） |

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

## デバイス登録の制約

### 事前認証の必須要件

FIDO-UAFデバイスの登録（`fido-uaf-registration-challenge` および `fido-uaf-registration`）には、**事前にユーザー認証が完了している**必要があります。

未認証状態でFIDO-UAFデバイス登録を試みると、以下のエラーが返されます：

```json
{
  "error": "unauthorized",
  "error_description": "User must be authenticated before registering a FIDO-UAF device."
}
```

この制約により、FIDO-UAFデバイスのみでユーザー登録を完結することはできません。ユーザーは最初にパスワード認証やEmail認証など、他の認証方式で認証を行う必要があります。

### device_registration_conditions ポリシー

認証ポリシーの `device_registration_conditions` を設定することで、デバイス登録に必要な認証レベル（ACR）を強制できます。

例えば、以下の設定ではEmail認証または既存のFIDO-UAFデバイスでの認証を完了しないとデバイス登録ができません：

```json
{
  "device_registration_conditions": {
    "any_of": [
      [{ "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
      [{ "path": "$.fido-uaf-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }]
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

### デバイス削除時の制約

FIDO-UAFデバイスの削除（`fido-uaf-deregistration`）にも同様の制約が適用されます：

1. **事前認証が必須**: 認証済みユーザーのみがデバイスを削除できます
2. **ACRポリシーの適用**: `device_registration_conditions` が設定されている場合、削除操作にも同じポリシーが適用されます

---

## 備考

* FIDO-UAFはユーザーにとってパスワード入力不要なUXを実現しながら、高いセキュリティを提供します

## 参考
* プロトコル
  * https://fidoalliance.org/specs/fido-uaf-v1.1-ps-20170202/fido-uaf-protocol-v1.1-ps-20170202.html
* Facets仕様
  * https://fidoalliance.org/specs/fido-uaf-v1.2-rd-20171128/fido-appid-and-facets-v1.2-rd-20171128.html