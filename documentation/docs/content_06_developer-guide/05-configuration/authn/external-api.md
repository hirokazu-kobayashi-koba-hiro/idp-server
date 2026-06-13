# External API認証

このドキュメントは、`external-api-authentication` 方式による外部API連携認証の `概要`・`設定`・`利用方法` について説明します。

---

## 概要

External API認証は、**リクエストボディの `interaction` フィールドで処理を動的にルーティング**し、設定ベースで任意の外部APIと連携する汎用認証方式です。

### external-token との違い

| 項目 | external-token | external-api-authentication |
|------|---------------|---------------------------|
| エンドポイント | `/external-token` | `/external-api-authentication` |
| interaction | 1つ固定 | 複数定義可能（`interaction` フィールドで選択） |
| 用途 | 外部トークンによる認証 | 任意の外部API連携（認証・リスク判定・OTP等） |
| user_resolve | 必須 | interaction ごとに有無を選択 |
| MFA 2段階目 | 非対応 | 対応（ユーザー一致検証あり） |

### 主な用途

- 外部認証サービスへの委譲（LDAP、RADIUS、レガシーシステム等）
- リスクベース認証（外部リスク判定APIの結果で追加認証を要求）
- 外部OTPサービス連携（Challenge-Response パターン）
- MFA の2段階目としての外部本人確認
- CRM / 会員基盤連携（外部会員DBでの認証 + 自動プロビジョニング）

### 処理フロー

```
1. クライアントが POST /external-api-authentication に interaction を指定して送信
2. idp-server が interactions[interaction] の設定を取得
3. JSON Schema バリデーション（設定がある場合）
4. 設定に従って外部APIを呼び出し
5. レスポンスマッピング
6. ユーザー解決（user_resolve 設定がある場合のみ）
7. 認証結果を返却
```

---

## 設定

External API認証を使用するには、テナントに `type = "external-api-authentication"` の認証設定を登録する必要があります。

### 基本構造

```json
{
  "id": "UUID",
  "type": "external-api-authentication",
  "attributes": {},
  "metadata": {
    "description": "外部API認証の説明"
  },
  "interactions": {
    "interaction名": {
      "request": { "schema": { /* JSON Schema */ } },
      "execution": { "function": "http_request", "http_request": { /* HTTP設定 */ } },
      "user_resolve": { "user_mapping_rules": [ /* ユーザーマッピング */ ] },
      "response": { "body_mapping_rules": [ /* レスポンスマッピング */ ] }
    }
  }
}
```

`interactions` の各キーが interaction 名になり、リクエストボディの `interaction` フィールドで選択されます。

---

## Interaction の種類

### 1. ユーザー認証型（user_resolve あり）

外部APIでユーザーを認証し、レスポンスからユーザー情報を解決します。

```json
{
  "password_verify": {
    "request": {
      "schema": {
        "type": "object",
        "required": ["interaction", "username", "password"],
        "properties": {
          "interaction": { "type": "string" },
          "username": { "type": "string", "minLength": 1 },
          "password": { "type": "string", "minLength": 1 }
        }
      }
    },
    "execution": {
      "function": "http_request",
      "http_request": {
        "url": "https://auth.example.com/verify",
        "method": "POST",
        "auth_type": "oauth2",
        "oauth_authorization": {
          "type": "client_credentials",
          "token_endpoint": "https://auth.example.com/token",
          "client_id": "idp-client",
          "client_secret": "secret",
          "scope": "authentication"
        },
        "body_mapping_rules": [
          { "from": "$.request_body.username", "to": "username" },
          { "from": "$.request_body.password", "to": "password" }
        ]
      }
    },
    "user_resolve": {
      "identity_match_field": "$.email",
      "user_mapping_rules": [
        { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
        { "from": "$.execution_http_request.response_body.email", "to": "email" },
        { "from": "$.execution_http_request.response_body.name", "to": "name" },
        { "static_value": "external-auth-provider", "to": "provider_id" }
      ]
    },
    "response": {
      "body_mapping_rules": [
        { "from": "$.execution_http_request.response_body.user_id", "to": "user_id" },
        { "from": "$.execution_http_request.response_body.email", "to": "email" }
      ]
    }
  }
}
```

> **MFA 2段階目で使う場合**: `identity_match_field` を設定して、1段階目のユーザーとの一致検証を有効にしてください。

### 2. 補助判定型（user_resolve なし）

外部APIの結果だけを返します。リスク判定やステータスチェック等に使用します。

```json
{
  "risk_check": {
    "execution": {
      "function": "http_request",
      "http_request": {
        "url": "https://risk.example.com/assess",
        "method": "POST",
        "body_mapping_rules": [
          { "from": "$.request_body.session_context", "to": "context" }
        ]
      }
    },
    "response": {
      "body_mapping_rules": [
        { "from": "$.execution_http_request.response_body.risk_score", "to": "risk_score" },
        { "from": "$.execution_http_request.response_body.risk_level", "to": "risk_level" }
      ]
    }
  }
}
```

### 3. Challenge-Response 型（previous_interaction）

2つの interaction を組み合わせて、Challenge → Verify のフローを実現します。

```json
{
  "otp_send": {
    "execution": {
      "function": "http_request",
      "http_request": {
        "url": "https://otp.example.com/send",
        "method": "POST",
        "body_mapping_rules": [
          { "from": "$.request_body.phone_number", "to": "phone" }
        ]
      },
      "http_request_store": {
        "key": "otp_send",
        "interaction_mapping_rules": [
          { "from": "$.response_body.transaction_id", "to": "transaction_id" }
        ]
      }
    },
    "response": {
      "body_mapping_rules": [
        { "from": "$.execution_http_request.response_body.transaction_id", "to": "transaction_id" }
      ]
    }
  },
  "otp_verify": {
    "execution": {
      "function": "http_request",
      "previous_interaction": { "key": "otp_send" },
      "http_request": {
        "url": "https://otp.example.com/verify",
        "method": "POST",
        "body_mapping_rules": [
          { "from": "$.interaction.transaction_id", "to": "transaction_id" },
          { "from": "$.request_body.code", "to": "verification_code" }
        ]
      }
    },
    "user_resolve": {
      "user_mapping_rules": [
        { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
        { "from": "$.execution_http_request.response_body.email", "to": "email" },
        { "static_value": "otp-provider", "to": "provider_id" }
      ]
    }
  }
}
```

**ポイント**:
- `http_request_store`: 1つ目の interaction のレスポンスを保存
- `previous_interaction`: 2つ目の interaction から保存データを参照
- `$.interaction.*`: 保存されたデータへのアクセスパス

---

## 利用方法

### エンドポイント

```http
POST /{tenantId}/v1/authorizations/{authorizationId}/external-api-authentication
Content-Type: application/json
```

### リクエスト例

```json
{
  "interaction": "password_verify",
  "username": "user@example.com",
  "password": "secret"
}
```

`interaction` フィールドで、設定の `interactions` キーを指定します。

### Challenge-Response の場合

```
// Step 1: Challenge
POST /external-api-authentication
{ "interaction": "otp_send", "phone_number": "+819012345678" }
→ { "transaction_id": "abc-123" }

// Step 2: Verify
POST /external-api-authentication
{ "interaction": "otp_verify", "code": "123456" }
→ { "user": { "sub": "...", "email": "..." } }
```

---

## MFA での利用（2段階目）

認証ポリシーで `external-api` を2段階目に設定できます。

### 認証ポリシー設定

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "password + external API verification",
      "priority": 1,
      "available_methods": ["password", "external-api", "initial-registration"],
      "step_definitions": [
        {
          "method": "password",
          "order": 1,
          "requires_user": false,
          "user_identity_source": "username"
        },
        {
          "method": "external-api",
          "order": 2,
          "requires_user": true
        }
      ],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.external-api-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

### セキュリティ: ユーザー一致検証（identity_match_field）

2段階目（`requires_user: true`）では以下のセキュリティチェックが行われます:

1. **1段階目未完了チェック**: トランザクションに認証済みユーザーがいなければ `400 user_not_found`
2. **ユーザー一致検証**: `identity_match_field` で指定した JSONPath のフィールドで、1段階目のユーザーと外部APIが返したユーザーを比較。不一致なら `400 user_identity_mismatch`
3. **一致する場合のみ**: 1段階目のユーザーをそのまま返す（外部APIのユーザー情報では上書きしない）

#### identity_match_field の設定

`user_resolve` 内に JSONPath 式で比較フィールドを指定します:

```json
{
  "user_resolve": {
    "identity_match_field": "$.email",
    "user_mapping_rules": [
      { "from": "$.execution_http_request.response_body.email", "to": "email" },
      { "..." : "..." }
    ]
  }
}
```

| identity_match_field | 比較対象 | ユースケース |
|---------------------|---------|------------|
| `$.email` | メールアドレス | パスワード認証委譲の2段階目 |
| `$.external_user_id` | 外部ユーザーID | 外部システム連携の2段階目 |
| `$.phone_number` | 電話番号 | SMS検証の2段階目 |
| `$.custom_properties.member_id` | カスタムプロパティ | 会員基盤連携の2段階目 |
| 未設定 | 比較スキップ | リスク分析API等（ユーザー識別不要） |

**未設定の場合**: `identity_match_field` を設定しないと、1段階目のユーザーの存在チェック（`hasUser`）のみが行われ、フィールド比較はスキップされます。リスク判定APIなど、ユーザー識別を返さない外部APIを2段階目に使う場合に適しています。

#### user_resolve なしの2段階目

`user_resolve` 自体を設定しない場合でも、`requires_user: true` のとき:
- 1段階目のユーザーの存在チェックは実行される（スキップ攻撃防止）
- 外部APIの結果だけを返し、1段階目のユーザーをそのまま引き継ぐ

---

## セキュリティイベント

interaction ごとに動的なセキュリティイベントが発行されます。

| ケース | イベント名 |
|--------|-----------|
| `password_verify` 成功 | `external_api_password_verify_success` |
| `password_verify` 失敗 | `external_api_password_verify_failure` |
| `risk_check` 成功 | `external_api_risk_check_success` |
| interaction 未指定 / 未登録 | `external_api_authentication_failure` |

形式: `external_api_{interaction名}_{success|failure}`

レスポンスボディにも `interaction` フィールドが含まれるため、ログやWebhookでの識別が可能です。

---

## エラーレスポンス

| エラー | ステータス | 説明 |
|--------|----------|------|
| `invalid_request` | 400 | `interaction` フィールド未指定 |
| `invalid_request` | 400 | 未登録の `interaction` 名 |
| `invalid_request` | 400 | JSON Schema バリデーション失敗 |
| `user_not_found` | 400 | 2段階目で1段階目の認証済みユーザーが不在 |
| `user_identity_mismatch` | 400 | 2段階目で外部APIのユーザーと1段階目のユーザーが不一致 |
| (外部APIのステータス) | 透過 | 外部APIが返した 401, 429, 500 等がそのまま返る |

---

## 外部API認証方式

外部APIへのリクエストで使用できる認証方式:

| auth_type | 説明 |
|-----------|------|
| `oauth2` | OAuth 2.0 Bearer Token（client_credentials / password フロー） |
| `hmac_sha256` | HMAC SHA-256 署名 |
| `none` | 認証なし |

---

## ID Token の amr クレーム

External API認証が成功すると、ID Token の `amr`（Authentication Methods References）クレームに `external-api` が含まれます。

MFA の場合は両方の認証方式が含まれます:

```json
{
  "amr": ["password", "external-api"]
}
```

---

## 完全な設定例

### パスワード認証委譲 + リスク判定

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "type": "external-api-authentication",
  "attributes": {},
  "metadata": {
    "description": "External password auth + risk assessment"
  },
  "interactions": {
    "password_verify": {
      "request": {
        "schema": {
          "type": "object",
          "required": ["interaction", "username", "password"],
          "properties": {
            "interaction": { "type": "string" },
            "username": { "type": "string", "minLength": 1, "maxLength": 256 },
            "password": { "type": "string", "minLength": 1, "maxLength": 128 }
          }
        }
      },
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://auth.example.com/verify",
          "method": "POST",
          "header_mapping_rules": [
            { "static_value": "application/json", "to": "Content-Type" }
          ],
          "body_mapping_rules": [
            { "from": "$.request_body.username", "to": "username" },
            { "from": "$.request_body.password", "to": "password" }
          ]
        }
      },
      "user_resolve": {
        "user_mapping_rules": [
          { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
          { "from": "$.execution_http_request.response_body.email", "to": "email" },
          { "from": "$.execution_http_request.response_body.name", "to": "name" },
          { "static_value": "auth-service", "to": "provider_id" }
        ]
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body.user_id", "to": "user_id" },
          { "from": "$.execution_http_request.response_body.email", "to": "email" }
        ]
      }
    },
    "risk_check": {
      "request": {
        "schema": {
          "type": "object",
          "required": ["interaction"],
          "properties": {
            "interaction": { "type": "string" },
            "device_fingerprint": { "type": "string" },
            "ip_address": { "type": "string" }
          }
        }
      },
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://risk.example.com/assess",
          "method": "POST",
          "body_mapping_rules": [
            { "from": "$.request_body.device_fingerprint", "to": "fingerprint" },
            { "from": "$.request_body.ip_address", "to": "ip" }
          ]
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body.risk_score", "to": "risk_score" },
          { "from": "$.execution_http_request.response_body.risk_level", "to": "risk_level" }
        ]
      }
    }
  }
}
```

---

## 関連ドキュメント

- [External Token認証](./external-token.md) - 外部トークンによる認証（単一 interaction）
- [認証ポリシー設定](../authentication-policy.md) - MFA・ステップアップ認証の設定
- [Mapping Functions 開発ガイド](../04-implementation-guides/impl-20-mapping-functions.md) - マッピング関数の詳細
- [HTTP Request Executor](../04-implementation-guides/impl-16-http-request-executor.md) - HTTPリクエスト実行の詳細

---

**情報源**:
- `libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/external_api/ExternalApiAuthenticationInteractor.java`
- `e2e/src/tests/usecase/advance/advance-13-external-api-authentication.test.js`
- `e2e/src/tests/security/external-api-authentication-2nd-factor-bypass.test.js`

**最終更新**: 2026-03-24
**作成者**: Claude Code（AI開発支援）
