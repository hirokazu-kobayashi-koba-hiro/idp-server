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

### 認証したデバイスの照合（post_hook）

`fido-uaf-authentication` の `post_hook.verifications` を設定すると、FIDO サーバーでの検証が成功したあとに、その結果を idp-server でも照合します。照合に失敗したときは認証を失敗にします。

FIDO サーバーがチャレンジのデバイス以外の鍵による応答を受け付ける場合は、FIDO サーバーが返す「実際に認証したデバイスの ID」と、チャレンジで使った `device_id` を照合してください。

```json
"fido-uaf-authentication": {
  "execution": { ... },
  "post_hook": {
    "verifications": [
      {
        "type": "match",
        "details": {
          "rules": [
            {
              "path": "$.execution_http_request.response_body.<認証したデバイスの ID の項目>",
              "operation": "eq",
              "target_path": "$.interaction.device_id"
            }
          ]
        }
      }
    ]
  },
  "response": { ... }
}
```

| 項目 | 説明 |
|------|------|
| `type` | 検証の種類。現在は `match` のみ |
| `details.rules[].path` | 照合する値の JSONPath |
| `details.rules[].target_path` | 比較対象の値の JSONPath |
| `details.rules[].operation` | 比較の演算子（省略時は `eq`）。`eq` / `ne` / `in` / `nin` / `contains` / `regex` など、認証ポリシーの条件と同じ演算子を使える |

JSONPath で参照できるのは次の値です。

| パス | 内容 |
|------|------|
| `$.execution_http_request` | FIDO サーバーへのリクエストの結果（`response_body` など）。`execution.function` が `http_requests` のときは `$.execution_http_requests` |
| `$.interaction` | チャレンジで保存したデータ。ユーザーの特定に使う `device_id` は `$.interaction.device_id`。`execution.previous_interaction` の設定によらない |
| `$.request_body` | クライアントからのリクエストボディ |
| `$.request_attributes` | リクエスト属性 |

- 設定した検証は毎回必ず実行します。条件付きでスキップする設定はありません。
- 次のときは照合失敗として扱います。
  - どちらかのパスに値が無い
  - 未知の `type` / `operation`
  - `rules` が空、または形が不正
  - JSONPath が不正
- 値が無いと照合失敗になるため、`exists` は常に成立し、`missing` は常に失敗します。この 2 つは使わないでください。
- `[*]` などで配列を返すパスは、該当が無いと空の配列（値あり）になります。`ne` / `nin` と組み合わせるときは注意してください。
- `$.request_body` と `$.request_attributes` はクライアントが決める値です。照合の両側をクライアントの値にしないでください。`regex` では `target_path` の値をパターンとして使うので、`target_path` にもクライアントの値を使わないでください。

照合に失敗したときは、次のエラーを返します。

```json
{
  "error": "invalid_request",
  "error_description": "FIDO-UAF authentication result did not pass the verification."
}
```

`post_hook` を設定しないときは照合しません。

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

「認証済み」として扱うのは、次のどれかです。

* 同じ認可リクエストで、認証（パスワード、Email、SMS、新規登録など）が成功している
* CIBA のリクエスト
* `/me` の API（アクセストークンのユーザー）

`login_hint` でユーザーを指定しただけでは認証済みになりません。また、デバイスの登録・削除の成功は認証として数えません（`amr` にも入らず、`amr` で決まる `acr` や `level_of_authentication_scopes` も満たしません）。

次の成功も認証として数えます。これらで本人を確かめていない場合は、`device_registration_conditions` で登録の条件を絞ってください。

* 外部 API 認証（`external-api-authentication`）。外部の処理を始めるだけの interaction なども含む
* ナンバーマッチング（`authentication-device-number-matching`）。コードと `device_id` が一致するかを見るもので、デバイスの持ち主であることの確認（鍵による所持の確認や生体認証）はしない

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