# External Token認証

このドキュメントは、`external-token` 方式による外部トークンを使った認証処理の `概要`・`設定`・`利用方法` について説明します。

---

## 概要

External Token認証は、**外部のアイデンティティプロバイダー（IdP）が発行したアクセストークン**を使って、ユーザー認証とユーザー情報取得を行う方式です。

### 主な用途

- 他のIdP（Google、Azure AD、独自IdP等）で認証済みのユーザーを連携
- 既存システムのアクセストークンを使った認証
- API-to-API認証での利用

### 処理フロー

1. クライアントが外部IdPからアクセストークンを取得
2. そのアクセストークンをidp-serverに送信
3. idp-serverが外部APIにトークンを送信してユーザー情報を取得
4. 取得したユーザー情報でidp-server内のユーザーと紐付け
5. 認証成功

---

## 設定

External Token認証を使用するには、テナントに `type = "external-token"` の認証設定を登録する必要があります。

### 基本構造

```json
{
  "id": "UUID",
  "type": "external-token",
  "attributes": {
    "service_name": "external-service-name"
  },
  "metadata": {},
  "interactions": {
    "external-token": {
      "request": {
        "schema": {
          "type": "object",
          "properties": {
            "access_token": { "type": "string" }
          }
        }
      },
      "execution": {
        "function": "http_requests",
        "http_requests": [
          { /* ユーザー概要取得API */ },
          { /* ユーザー詳細取得API */ }
        ]
      },
      "user_resolve": {
        "user_mapping_rules": [ /* ユーザー情報マッピング */ ]
      }
    }
  }
}
```

---

## Request Schema

External Token認証で受け付けるリクエストの構造：

```json
{
  "request": {
    "schema": {
      "type": "object",
      "properties": {
        "access_token": {
          "type": "string",
          "description": "外部IdPが発行したアクセストークン"
        }
      },
      "required": ["access_token"]
    }
  }
}
```

---

## Execution: 複数HTTPリクエスト

**function**: `http_requests`

複数の外部APIを連続して呼び出し、ユーザー情報を取得します。

### HTTP Requests 設定

```json
{
  "execution": {
    "function": "http_requests",
    "http_requests": [
      {
        "url": "https://external-service.com/user/overview",
        "method": "POST",
        "header_mapping_rules": [
          {
            "from": "$.request_body.access_token",
            "to": "x-token",
            "functions": [
              { "name": "format", "args": { "template": "Bearer {{value}}" } }
            ]
          },
          {
            "from": "$.unused",
            "to": "x-request-id",
            "functions": [
              { "name": "random_string", "args": { "length": 6 } },
              { "name": "format", "args": { "template": "trace-id-{{value}}" } }
            ]
          }
        ],
        "body_mapping_rules": [
          { "from": "$.request_body", "to": "*" }
        ]
      },
      {
        "url": "https://external-service.com/user/details",
        "method": "POST",
        "header_mapping_rules": [
          { "static_value": "your-client-id", "to": "x-client-id" }
        ],
        "body_mapping_rules": [
          {
            "from": "$.execution_http_requests[0].response_body.id",
            "to": "user_id"
          }
        ]
      }
    ]
  }
}
```

### 主要項目

| 項目 | 説明 |
|------|------|
| `url` | 外部APIのエンドポイント |
| `method` | HTTPメソッド（POST/GET等） |
| `header_mapping_rules` | HTTPヘッダーのマッピングルール |
| `body_mapping_rules` | リクエストボディのマッピングルール |

### Mapping Functions

**header_mapping_rules**と**body_mapping_rules**でデータ変換を行います：

#### ヘッダーマッピング例

```json
{
  "from": "$.request_body.access_token",
  "to": "x-token",
  "functions": [
    { "name": "format", "args": { "template": "Bearer {{value}}" } }
  ]
}
```

- `from`: 参照元（JSONPath）
- `to`: 設定先ヘッダー名
- `functions`: 適用する関数リスト

#### 前のリクエスト結果の参照

```json
{
  "from": "$.execution_http_requests[0].response_body.id",
  "to": "user_id"
}
```

2番目のリクエストで、1番目のレスポンスを参照できます。

---

## User Resolve: ユーザー情報マッピング

外部APIから取得した情報を、idp-serverのUserモデルにマッピングします。

```json
{
  "user_resolve": {
    "user_mapping_rules": [
      { "static_value": "external-provider", "to": "provider_id" },
      { "from": "$.execution_http_requests[0].response_body.id", "to": "external_user_id" },
      { "from": "$.execution_http_requests[0].response_body.email", "to": "email" },
      { "from": "$.execution_http_requests[1].response_body.birthdate", "to": "birthdate" },
      { "from": "$.execution_http_requests[1].response_body.phone_number", "to": "phone_number" },
      {
        "from": "$.execution_http_requests[1].response_body.role",
        "to": "custom_properties.role",
        "functions": [{ "name": "exists", "args": {} }]
      }
    ]
  }
}
```

### User Mapping Rules

| 項目 | 説明 |
|------|------|
| `from` | 参照元（JSONPath）。`$.execution_http_requests[N].response_body.*`で前のHTTPレスポンスを参照 |
| `to` | マッピング先（User model のフィールド） |
| `static_value` | 固定値の設定 |
| `functions` | 適用する関数リスト（exists, format等） |

### マッピング先フィールド

| フィールド | 説明 |
|-----------|------|
| `provider_id` | プロバイダーID（外部IdP識別） |
| `external_user_id` | 外部システムのユーザーID |
| `email` | メールアドレス |
| `name` | 表示名 |
| `birthdate` | 生年月日 |
| `phone_number` | 電話番号 |
| `custom_properties.*` | カスタムプロパティ |

---

## 完全な設定例

**情報源**: `config/examples/e2e/test-tenant/authentication-config/external-token/mocky.json`

```json
{
  "id": "5ee62e7d-e1f0-4f38-b714-0c5d6a28d8f1",
  "type": "external-token",
  "attributes": {
    "service_name": "mock"
  },
  "metadata": {},
  "interactions": {
    "external-token": {
      "request": {
        "schema": {
          "type": "object",
          "properties": {
            "access_token": { "type": "string" }
          }
        }
      },
      "execution": {
        "function": "http_requests",
        "http_requests": [
          {
            "url": "http://host.docker.internal:4000/user/overview",
            "method": "POST",
            "header_mapping_rules": [
              {
                "from": "$.request_body.access_token",
                "to": "x-token",
                "convertType": "string",
                "functions": [
                  { "name": "format", "args": { "template": "Bearer {{value}}" } }
                ]
              },
              {
                "from": "$.unused",
                "to": "x-request-id",
                "functions": [
                  { "name": "random_string", "args": { "length": 6 } },
                  { "name": "format", "args": { "template": "trace-id-{{value}}" } }
                ]
              },
              {
                "from": "$.unused",
                "to": "issued_at",
                "functions": [
                  { "name": "now", "args": { "zone": "Asia/Tokyo", "pattern": "yyyy-MM-dd HH:mm:ss" } }
                ]
              }
            ],
            "body_mapping_rules": [
              { "from": "$.request_body", "to": "*" }
            ]
          },
          {
            "url": "http://host.docker.internal:4000/user/details",
            "method": "POST",
            "header_mapping_rules": [
              { "static_value": "test-client-id", "to": "x-client-id" },
              {
                "from": "$.unused",
                "to": "x-request-id",
                "functions": [
                  { "name": "randomString", "args": { "length": 6 } },
                  { "name": "format", "args": { "template": "trace-id-{{value}}" } }
                ]
              }
            ],
            "body_mapping_rules": [
              { "from": "$.execution_http_requests[0].response_body.id", "to": "user_id" }
            ]
          }
        ]
      },
      "user_resolve": {
        "user_mapping_rules": [
          { "static_value": "mock-provider", "to": "provider_id" },
          { "from": "$.execution_http_requests[0].response_body.id", "to": "external_user_id" },
          { "from": "$.execution_http_requests[0].response_body.email", "to": "email" },
          { "from": "$.execution_http_requests[1].response_body.birthdate", "to": "birthdate" },
          { "from": "$.execution_http_requests[1].response_body.zoneinfo", "to": "zoneinfo" },
          { "from": "$.execution_http_requests[1].response_body.locale", "to": "locale" },
          { "from": "$.execution_http_requests[1].response_body.phone_number", "to": "phone_number" },
          {
            "from": "$.execution_http_requests[1].response_body.role",
            "to": "custom_properties.role",
            "functions": [{ "name": "exists", "args": {} }]
          }
        ]
      }
    }
  }
}
```

---

## 利用方法

### 事前準備

1. テナントに `type = "external-token"` の認証設定を登録する（上記設定例を参照）
2. 外部IdPのAPIエンドポイントとレスポンス構造を確認

### 認証フロー

1. クライアントが外部IdPで認証し、アクセストークンを取得
2. 認可リクエストに対応して、以下のエンドポイントにPOSTする：

```http
POST /v1/authorizations/{id}/external-token
Content-Type: application/json
```

**リクエストボディ例**:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

3. idp-serverが設定に従って外部APIを呼び出し、ユーザー情報を取得
4. ユーザー情報をマッピングして、idp-server内のユーザーと紐付け
5. 認証成功

---

## 内部ロジック

**実装**: `ExternalTokenAuthenticationInteractor.java`

1. **設定取得**
   - `AuthenticationConfigurationQueryRepository.get(tenant, "external-token")`
   - テナントごとのexternal-token設定を取得

2. **HTTP Requests実行**
   - 設定された複数のHTTPリクエストを順次実行
   - 前のリクエストのレスポンスを次のリクエストで参照可能
   - Mapping Functionsでヘッダー・ボディを動的生成

3. **User Resolve**
   - `user_mapping_rules`に従ってユーザー情報をマッピング
   - `provider_id`と`external_user_id`でユーザーを識別
   - 存在しないユーザーは新規作成（設定により制御可能）

4. **認証成功処理**
   - `User`と`Authentication`オブジェクトを返却
   - セキュリティイベント発行

---

## Mapping Functions活用例

### 動的ヘッダー生成

```json
{
  "from": "$.request_body.access_token",
  "to": "x-token",
  "functions": [
    { "name": "format", "args": { "template": "Bearer {{value}}" } }
  ]
}
```

アクセストークンを`Bearer {token}`形式に変換してヘッダーに設定

### リクエストID生成

```json
{
  "from": "$.unused",
  "to": "x-request-id",
  "functions": [
    { "name": "random_string", "args": { "length": 6 } },
    { "name": "format", "args": { "template": "trace-id-{{value}}" } }
  ]
}
```

ランダムな6文字を生成し、`trace-id-ABC123`形式のリクエストIDを作成

### タイムスタンプ生成

```json
{
  "from": "$.unused",
  "to": "issued_at",
  "functions": [
    { "name": "now", "args": { "zone": "Asia/Tokyo", "pattern": "yyyy-MM-dd HH:mm:ss" } }
  ]
}
```

現在時刻を指定フォーマットで生成

### 前のレスポンス参照

```json
{
  "from": "$.execution_http_requests[0].response_body.id",
  "to": "user_id"
}
```

1番目のHTTPリクエストのレスポンスから`id`を抽出

---

## 備考

- **provider_id**: 外部IdPごとに異なる値を設定（ユーザーの一意識別に使用）
- **複数API呼び出し**: `http_requests`配列で順次実行
- **Mapping Functions**: 19個の関数を組み合わせて柔軟なデータ変換が可能
- **セキュリティ**: アクセストークンの検証は外部APIに委譲

---

## 関連ドキュメント

- [Mapping Functions 開発ガイド](../04-implementation-guides/impl-20-mapping-functions.md) - 19個の変換関数の詳細
- [HTTP Request Executor](../04-implementation-guides/impl-16-http-request-executor.md) - HTTPリクエスト実行の詳細
- [External Integration](../04-implementation-guides/impl-17-external-integration.md) - 外部サービス連携パターン

---

**情報源**:
- `config/examples/e2e/test-tenant/authentication-config/external-token/mocky.json`
- `libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/external_token/ExternalTokenAuthenticationInteractor.java`

**最終更新**: 2025-12-08
**作成者**: Claude Code（AI開発支援）
