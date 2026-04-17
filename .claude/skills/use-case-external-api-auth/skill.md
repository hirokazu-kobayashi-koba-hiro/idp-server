---
name: use-case-external-api-auth
description: 外部API認証ユースケースの設定ガイド。外部API連携（認証委譲、リスク判定、OTP等）の interaction 設計、identity_match_field、MFA 2段階目、previous_interaction のヒアリングと設定JSONを提供。
---

# 外部API認証

任意の外部APIと連携した認証を、設定JSONだけで構築するユースケース。
`/external-api-authentication` エンドポイントで、リクエストボディの `interaction` フィールドにより複数の外部API処理を切り替える。

**ユースケース**:
- 既存の認証基盤（LDAP、RADIUS、レガシーシステム等）を OIDC レイヤーとして活用
- 認証フローにリスク分析API、不正検知サービス、外部OTPプロバイダーを組み込む
- MFA の2段階目に外部APIを利用（ユーザー一致検証あり）

## external-password-auth との違い

| 項目 | external-password-auth | external-api-auth（これ） |
|------|----------------------|--------------------------|
| エンドポイント | `/password-authentication` | `/external-api-authentication` |
| type | `password` | `external-api-authentication` |
| interaction | 1つ固定（`password-authentication`） | 複数定義可能（`interaction` フィールドで選択） |
| user_resolve | 必須 | interaction ごとに有無を選択 |
| Challenge-Response | 非対応 | `previous_interaction` で対応 |
| MFA 2段階目 | 対応 | 対応（`identity_match_field` による一致検証あり） |
| セキュリティイベント | `password_success/failure` | `external_api_{interaction}_{success/failure}`（動的） |

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | 連携する外部APIのURL | 実サービスURL / モック | 認証メソッド設定 `http_request.url` |
| 2 | 外部API認証方式 | `oauth2` / `hmac_sha256` / `none` | 認証メソッド設定 `http_request.auth_type` |
| 3 | interaction 設計 | 名前、数、各 interaction の役割 | 認証メソッド設定 `interactions` キー |
| 4 | user_resolve の有無 | interaction ごとに決定 | 認証メソッド設定 `user_resolve` |
| 5 | Challenge-Response の要否 | `previous_interaction` 使用有無 | 認証メソッド設定 `http_request_store` + `previous_interaction` |
| 6 | MFA 2段階目で使うか | yes / no | 認証ポリシー `step_definitions` |
| 7 | identity_match_field | JSONPath（`$.email`, `$.phone_number` 等）/ 未設定 | 認証メソッド設定 `user_resolve.identity_match_field` |
| 8 | セッション有効期限 | 秒数（デフォルト: 86400） | テナント `session_config.timeout_seconds` |
| 9 | トークン有効期限（AT） | 秒数（デフォルト: 3600） | 認可サーバー `extension.access_token_duration` |

## ヒアリング結果 -> 環境変数マッピング

### 外部APIサービス

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| 外部APIサービスURL | `EXTERNAL_API_URL` | `http://host.docker.internal:4000/auth/password` |
| 外部プロバイダー識別子 | `EXTERNAL_PROVIDER_ID` | `external-api` |
| 外部API認証方式 | `EXTERNAL_API_AUTH_TYPE` | `none` |

### セッション・トークン設定

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| セッション有効期限（秒） | `SESSION_TIMEOUT_SECONDS` | `86400` |
| AT有効期限（秒） | `ACCESS_TOKEN_DURATION` | `3600` |
| IDT有効期限（秒） | `ID_TOKEN_DURATION` | `3600` |
| RT有効期限（秒） | `REFRESH_TOKEN_DURATION` | `86400` |

---

## 設定対象と手順

### 1. 外部API認証設定（認証メソッド設定）-- 核心

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations`

#### パターンA: 単一 interaction（パスワード認証委譲）

```json
{
  "type": "external-api-authentication",
  "metadata": { "description": "External API authentication" },
  "interactions": {
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
          "url": "${EXTERNAL_API_URL}",
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
        "identity_match_field": "$.email",
        "user_mapping_rules": [
          { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
          { "from": "$.execution_http_request.response_body.email", "to": "email" },
          { "from": "$.execution_http_request.response_body.name", "to": "name" },
          { "static_value": "${EXTERNAL_PROVIDER_ID}", "to": "provider_id" }
        ]
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body.email", "to": "email" }
        ]
      }
    }
  }
}
```

#### パターンB: 複数 interaction（認証 + リスク判定）

```json
{
  "interactions": {
    "password_verify": { "...": "(パターンA と同じ)" },
    "risk_check": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://risk.example.com/assess",
          "method": "POST",
          "body_mapping_rules": [
            { "from": "$.request_body.device_fingerprint", "to": "fingerprint" }
          ]
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body.risk_score", "to": "risk_score" }
        ]
      }
    }
  }
}
```

#### パターンC: Challenge-Response（外部OTP）

```json
{
  "interactions": {
    "otp_send": {
      "execution": {
        "function": "http_request",
        "http_request": { "url": "https://otp.example.com/send", "method": "POST" },
        "http_request_store": {
          "key": "otp_send",
          "interaction_mapping_rules": [
            { "from": "$.response_body.transaction_id", "to": "transaction_id" }
          ]
        }
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
        "user_mapping_rules": [ "..." ]
      }
    }
  }
}
```

### 2. 認証ポリシー

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-policies`

#### 1st factor のみ

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "external_api_only",
      "priority": 1,
      "available_methods": ["external-api", "initial-registration"],
      "success_conditions": {
        "any_of": [
          [{ "path": "$.external-api-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
          [{ "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }]
        ]
      }
    }
  ]
}
```

#### MFA（password + external-api）

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
        { "method": "password", "order": 1, "requires_user": false, "user_identity_source": "username" },
        { "method": "external-api", "order": 2, "requires_user": true }
      ],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.external-api-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [{ "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }]
        ]
      }
    }
  ]
}
```

### 3. クレーム設定（claims_supported）

> **重要**: この設定が無いと UserInfo / ID Token が `sub` のみしか返さない。

`PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server` で設定。

---

## identity_match_field 設計ガイド

MFA 2段階目で `user_resolve` を使う場合、`identity_match_field` の設定が重要。

| パターン | identity_match_field | 説明 |
|---------|---------------------|------|
| メールで一致検証 | `$.email` | 1st factor のユーザーと email で比較 |
| 外部ユーザーIDで一致検証 | `$.external_user_id` | provider 間の ID で比較 |
| 電話番号で一致検証 | `$.phone_number` | 電話番号で比較 |
| カスタムプロパティで一致検証 | `$.custom_properties.member_id` | 任意のカスタムフィールドで比較 |
| 比較スキップ（リスク判定等） | 未設定 | hasUser チェックのみ |

**選び方**:
- 外部APIが返すユーザー情報に、1st factor のユーザーと共通するフィールドがあるか？
- あれば → そのフィールドの JSONPath を設定
- なければ → 未設定（外部APIのHTTPステータスのみで判定）

---

## セキュリティイベント

| ケース | イベント名 |
|--------|-----------|
| `password_verify` 成功 | `external_api_password_verify_success` |
| `password_verify` 失敗 | `external_api_password_verify_failure` |
| `risk_check` 成功 | `external_api_risk_check_success` |
| interaction 未指定 / 未登録 | `external_api_authentication_failure` |

---

## 設定確認チェックリスト

| # | 確認観点 | よくあるミス |
|---|---------|------------|
| 1 | `claims_supported` が設定済み | 未設定で UserInfo/ID Token が `sub` のみ |
| 2 | `EXTERNAL_API_URL` が idp-server コンテナから到達可能 | `localhost` を指定 → `host.docker.internal` を使う |
| 3 | `identity_unique_key_type` が `EMAIL_OR_EXTERNAL_USER_ID` | `EMAIL` のままだと外部ユーザーIDでの識別が機能しない |
| 4 | MFA 2段階目で `identity_match_field` が設定済み | 未設定だとフィールド比較がスキップされる |
| 5 | `provider_id` が `static_value` で固定値 | 外部APIの値を使うと攻撃者が制御可能 |
| 6 | Challenge-Response で `http_request_store.key` と `previous_interaction.key` が一致 | キー不一致でデータ受け渡しが機能しない |

---

## 関連ドキュメント

- `documentation/docs/content_02_quickstart/quickstart-14-external-api-authentication.md` - クイックスタート
- `documentation/docs/content_05_how-to/phase-4-extensions/06-external-api-authentication.md` - 設定ガイド（Step by Step）
- `documentation/docs/content_06_developer-guide/05-configuration/authn/external-api.md` - 設定リファレンス
- スキル `use-case-external-password-auth` - 外部パスワード認証委譲（単一 interaction）
- スキル `spec-external-integration` - 外部サービス連携の詳細（HTTP Request Executor, MappingRule）
- スキル `spec-authentication` - 認証ポリシー・MFA の詳細

$ARGUMENTS
