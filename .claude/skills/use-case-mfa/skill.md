---
name: use-case-mfa
description: MFA（多要素認証）ユースケースの設定ガイド。SMS/メール認証メソッド設定、MFA認証ポリシー（AND/OR/スコープ別）のヒアリングと設定JSONを提供。
---

# MFA（多要素認証）

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | 第2要素 | SMS / メール / 選択式 | 認証メソッド設定 |
| 2 | MFA適用範囲 | 全アプリ必須 / 特定アプリのみ / 特定スコープのみ | 認証ポリシー `conditions`, `level_of_authentication_scopes` |
| 3 | コード有効期限 | 3分 / 5分 / 10分 | 認証メソッド設定 `metadata.expire_seconds` |
| 4 | 試行回数上限 | 3回 / 5回 | 認証メソッド設定 `metadata.retry_count_limitation` |
| 5 | SMS/メール送信サービス | 外部API連携（Twilio等） / モック | 認証メソッド設定 `interactions` |

---

## 設定対象と手順

### 1. SMS認証メソッド設定

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations`

```json
{
  "id": "{uuid}",
  "type": "sms",
  "metadata": {
    "type": "external",
    "description": "SMS authentication for MFA",
    "transaction_id_param": "transaction_id",
    "verification_code_param": "verification_code",
    "retry_count_limitation": 5,
    "expire_seconds": 300
  },
  "interactions": {
    "sms-authentication-challenge": {
      "request": {
        "schema": {
          "type": "object",
          "properties": {
            "phone_number": { "type": "string" },
            "template": { "type": "string" }
          }
        }
      },
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "{SMS送信サービスURL}",
          "method": "POST",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "{認証トークンエンドポイント}",
            "client_id": "{client_id}",
            "username": "{username}",
            "password": "{password}",
            "scope": "application"
          },
          "header_mapping_rules": [
            { "static_value": "application/json", "to": "Content-Type" }
          ],
          "body_mapping_rules": [
            { "from": "$.request_body", "to": "*" }
          ]
        },
        "http_request_store": {
          "key": "sms-authentication-challenge",
          "interaction_mapping_rules": [
            { "from": "$.response_body.transaction_id", "to": "transaction_id" }
          ]
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body", "to": "*" }
        ]
      }
    },
    "sms-authentication": {
      "request": {
        "schema": {
          "type": "object",
          "properties": {
            "verification_code": { "type": "string" }
          }
        }
      },
      "execution": {
        "function": "http_request",
        "previous_interaction": {
          "key": "sms-authentication-challenge"
        },
        "http_request": {
          "url": "{SMS検証サービスURL}",
          "method": "POST",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "{認証トークンエンドポイント}",
            "client_id": "{client_id}",
            "username": "{username}",
            "password": "{password}",
            "scope": "application"
          },
          "header_mapping_rules": [
            { "static_value": "application/json", "to": "Content-Type" }
          ],
          "body_mapping_rules": [
            { "from": "$.request_body", "to": "*" }
          ]
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

**ヒアリング結果の反映先**:

| ヒアリング項目 | JSONキー | 値の例 |
|--------------|---------|-------|
| コード有効期限（秒） | `metadata.expire_seconds` | `180`, `300`, `600` |
| 試行回数上限 | `metadata.retry_count_limitation` | `3`, `5` |
| SMS送信サービスURL | `interactions.sms-authentication-challenge.execution.http_request.url` | Twilio API等 |
| SMS検証サービスURL | `interactions.sms-authentication.execution.http_request.url` | Twilio API等 |

### 2. メール認証メソッド設定

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations`

```json
{
  "id": "{uuid}",
  "type": "email",
  "metadata": {
    "type": "external",
    "sender": "{送信元メールアドレス}",
    "retry_count_limitation": 5,
    "expire_seconds": 300,
    "settings": {
      "smtp": {
        "host": "{SMTPホスト}",
        "port": 587,
        "username": "{SMTPユーザー}",
        "password": "{SMTPパスワード}",
        "auth": true,
        "starttls": { "enable": true }
      }
    }
  },
  "interactions": {
    "email-authentication-challenge": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "{メール送信サービスURL}",
          "method": "POST",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "{認証トークンエンドポイント}"
          }
        }
      }
    }
  }
}
```

### 3. 認証ポリシー更新（MFA対応）

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-policies`

**パターンA: パスワード + SMS（AND必須）**:
```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "password + sms mfa",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password", "sms", "initial-registration"],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

**パターンB: パスワード + (SMS or メール)（選択式MFA）**:
```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "password + (sms or email)",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password", "sms", "email", "initial-registration"],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

**パターンC: 特定スコープでのみMFA必須**:
```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "mfa_for_transfers_scope",
      "priority": 10,
      "conditions": { "scopes": ["transfers"] },
      "available_methods": ["password", "sms"],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    },
    {
      "description": "password_only_default",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password"],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

## success_conditions 構造リファレンス

| 要件 | any_of構造 | 意味 |
|------|-----------|------|
| AND（MFA必須） | `[[条件1, 条件2]]` | 両方成功が必要 |
| OR（選択式） | `[[条件1], [条件2]]` | いずれか1つでOK |
| AND+OR（選択式MFA） | `[[条件1, 条件2], [条件1, 条件3]]` | (1+2) OR (1+3) |

## 利用可能なJSONPath

```
$.password-authentication.success_count
$.sms-authentication.success_count
$.email-authentication.success_count
$.fido2-authentication.success_count
$.fido-uaf-authentication.success_count
$.initial-registration.success_count
$.password-authentication.failure_count
```

## 設定例ファイル参照

- SMS認証: `config/examples/e2e/.../authentication-config/sms/external.json`
- メール認証: `config/examples/e2e/.../authentication-config/email/smtp.json`
- 認証ポリシー: `config/examples/e2e/.../authentication-policy/oauth.json`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-2-security/01-mfa-setup.md`
- `documentation/docs/content_05_how-to/phase-1-foundation/07-authentication-policy.md`
- `documentation/docs/content_02_quickstart/quickstart-05-mfa.md`

$ARGUMENTS
