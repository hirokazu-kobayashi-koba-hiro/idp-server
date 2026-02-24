---
name: use-case-ekyc
description: 身元確認/eKYCユースケースの設定ガイド。外部eKYCサービス連携、身元確認テンプレート、verified_claims設定、必須スコープ設定のヒアリングと設定JSONを提供。
---

# 身元確認/eKYC

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | 実施方法 | idp-server経由で申込み / 結果のみ登録 | 身元確認テンプレート |
| 2 | 申込みプロセス | ステップ数と順序 | テンプレート `processes` |
| 3 | 外部eKYCサービス | APIエンドポイント, 認証方式 | テンプレート `execution` |
| 4 | verified_claims | 保存する情報, trust_framework | テンプレート `result.verified_claims_mapping_rules` |
| 5 | 身元確認必須スコープ | どのスコープで必須化するか | 認可サーバー拡張設定 |

---

## 設定対象と手順

### 1. 身元確認テンプレート設定

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/identity-verification-configurations`

```json
{
  "id": "{uuid}",
  "type": "{申込タイプ名}",
  "enabled": true,
  "common": {
    "external_service": "{サービス名}",
    "callback_application_id_param": "application_id",
    "oauth_authorization": {
      "type": "password",
      "token_endpoint": "{外部サービストークンエンドポイント}",
      "client_id": "{client_id}",
      "username": "{username}",
      "password": "{password}",
      "scope": "application"
    }
  },
  "processes": {
    "apply": {
      "request": {
        "schema": {
          "type": "object",
          "required": ["last_name", "first_name", "birthdate"],
          "properties": {
            "last_name": { "type": "string", "maxLength": 100 },
            "first_name": { "type": "string", "maxLength": 100 },
            "birthdate": { "type": "string", "format": "date" },
            "email_address": { "type": "string", "format": "email" },
            "address": { "type": "string", "maxLength": 500 }
          }
        }
      },
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "{外部eKYCサービスURL}",
          "method": "POST",
          "header_mapping_rules": [
            { "static_value": "application/json", "to": "Content-Type" }
          ],
          "body_mapping_rules": [
            { "from": "$.request_body", "to": "*" }
          ]
        },
        "http_request_store": {
          "key": "apply",
          "interaction_mapping_rules": [
            { "from": "$.response_body.application_id", "to": "external_application_id" }
          ]
        }
      },
      "store": {
        "mapping_rules": [
          { "from": "$.request_body", "to": "*" },
          { "from": "$.additional_parameters.external_application_id", "to": "external_application_id" }
        ]
      }
    },
    "callback-result": {
      "required_processes": ["apply"],
      "allow_retry": false,
      "execution": {
        "function": "no_op"
      },
      "transition": {
        "rules": [
          {
            "to": "approved",
            "conditions": [
              {
                "path": "$.request_body.verification_result",
                "type": "string",
                "operation": "eq",
                "value": "approved"
              }
            ]
          },
          {
            "to": "rejected",
            "conditions": [
              {
                "path": "$.request_body.verification_result",
                "type": "string",
                "operation": "eq",
                "value": "rejected"
              }
            ]
          }
        ]
      },
      "result": {
        "verified_claims_mapping_rules": [
          { "from": "$.application.application_details.last_name", "to": "verified_claims.claims.family_name" },
          { "from": "$.application.application_details.first_name", "to": "verified_claims.claims.given_name" },
          { "from": "$.application.application_details.birthdate", "to": "verified_claims.claims.birthdate" },
          { "static_value": "jp_aml", "to": "verified_claims.trust_framework" }
        ]
      }
    }
  }
}
```

**ヒアリング結果の反映先**:

| ヒアリング項目 | JSONキー | 値の例 |
|--------------|---------|-------|
| 申込タイプ名 | `type` | `investment-account-opening`, `kyc-basic` |
| 必須入力項目 | `processes.apply.request.schema.required` | `["last_name", "first_name", "birthdate"]` |
| 外部eKYCサービスURL | `processes.apply.execution.http_request.url` | `https://ekyc-vendor.example.com/api/verify` |
| 認証方式 | `common.oauth_authorization` または `common.hmac_authorization` | OAuth 2.0 / HMAC / Basic |
| trust_framework | `result.verified_claims_mapping_rules` 内 | `jp_aml`, `eidas` |
| verified_claimsに含める属性 | `result.verified_claims_mapping_rules` | family_name, given_name, birthdate, address等 |

### 2. 身元確認必須スコープ設定（認可サーバー拡張設定更新）

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

```json
{
  "extension": {
    "required_identity_verification_scopes": ["transfers", "account"],
    "access_token_verified_claims": true,
    "access_token_selective_verified_claims": true
  }
}
```

**ヒアリング結果の反映先**:

| ヒアリング項目 | JSONキー | 値の例 |
|--------------|---------|-------|
| 必須スコープ | `required_identity_verification_scopes` | `["transfers"]` |
| ATにverified_claims含有 | `access_token_verified_claims` | `true` |

### 3. verified_claims有効化（認可サーバー設定更新）

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

```json
{
  "verified_claims_supported": true
}
```

## 設定例ファイル参照

- 投資口座開設: `config/examples/e2e/.../identity/investment-account-opening.json`
- 継続的顧客確認: `config/examples/e2e/.../identity/continuous-customer-due-diligence.json`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-4-extensions/identity-verification/01-guide.md`
- `documentation/docs/content_05_how-to/phase-4-extensions/identity-verification/02-application.md`
- `documentation/docs/content_05_how-to/phase-4-extensions/identity-verification/03-registration.md`
- `documentation/docs/content_02_quickstart/quickstart-07-ekyc.md`

$ARGUMENTS
