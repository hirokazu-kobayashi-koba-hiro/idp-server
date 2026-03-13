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
    "callback_application_id_param": "application_id"
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
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "password",
            "token_endpoint": "{外部サービストークンエンドポイント}",
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
      }
    }
  },
  "result": {
    "verified_claims_mapping_rules": [
      { "static_value": "jp_aml", "to": "verification.trust_framework" },
      { "from": "$.application.application_details.last_name", "to": "claims.family_name" },
      { "from": "$.application.application_details.first_name", "to": "claims.given_name" },
      { "from": "$.application.application_details.birthdate", "to": "claims.birthdate" }
    ]
  }
}
```

**ヒアリング結果の反映先**:

| ヒアリング項目 | JSONキー | 値の例 |
|--------------|---------|-------|
| 申込タイプ名 | `type` | `investment-account-opening`, `kyc-basic` |
| 必須入力項目 | `processes.apply.request.schema.required` | `["last_name", "first_name", "birthdate"]` |
| 外部eKYCサービスURL | `processes.apply.execution.http_request.url` | `https://ekyc-vendor.example.com/api/verify` |
| 認証方式 | `processes.{process}.execution.http_request.oauth_authorization` または `.hmac_authentication` | OAuth 2.0 / HMAC / Basic |
| trust_framework | トップレベル `result.verified_claims_mapping_rules` 内 | `jp_aml`, `eidas` |
| verified_claimsに含める属性 | トップレベル `result.verified_claims_mapping_rules` | family_name, given_name, birthdate, address等 |

### 2. 身元確認申し込みスコープ設定

身元確認申し込みAPI（`/v1/me/identity-verification/applications`）へのアクセスには `identity_verification_application` スコープが必要。

**認可サーバーの `scopes_supported` とクライアントの `scope` に追加すること。**

```json
// 認可サーバー
"scopes_supported": ["openid", "profile", "email", "transfers", "identity_verification_application"]

// クライアント
"scope": "openid profile email transfers identity_verification_application"
```

**SecurityConfig での制御**（`SecurityConfig.java`）:

| エンドポイント | メソッド | 必要スコープ |
|--------------|---------|-------------|
| `/v1/me/identity-verification/applications/{type}/{process}` | POST | `identity_verification_application` |
| `/v1/me/identity-verification/applications/{type}/{id}/{process}` | POST | `identity_verification_application` |
| `/v1/me/identity-verification/applications` | GET | `identity_verification_application` |
| `/v1/me/identity-verification/applications/{type}/{id}` | DELETE | `identity_verification_application_delete` |
| `/v1/me/identity-verification/results` | GET | `identity_verification_result` |

### 3. 身元確認結果取得スコープ設定

身元確認結果API（`/v1/me/identity-verification/results`）へのアクセスには `identity_verification_result` スコープが必要。

**認可サーバーの `scopes_supported` とクライアントの `scope` に追加すること。**

```json
// 認可サーバー
"scopes_supported": ["openid", "profile", "email", "transfers", "identity_verification_application", "identity_verification_result"]

// クライアント
"scope": "openid profile email transfers identity_verification_application identity_verification_result"
```

### 4. 身元確認必須スコープ設定（認可サーバー拡張設定更新）

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

### 5. verified_claims有効化（認可サーバー設定更新）

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

```json
{
  "verified_claims_supported": true
}
```

### 6. クレーム設定（認可サーバー更新）

> **重要**: この設定が無いと UserInfo / ID Token が `sub` のみしか返さない。
> 詳細は `use-case-login` スキルの「クレーム設定」セクションを参照。

認可サーバーの `claims_supported` に返したいクレーム一覧を設定する。
標準的な設定は `config/templates/tenant-template.json` を参照。

## 設定確認チェックリスト

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 1 | `claims_supported` が設定済み | 認可サーバー | 未設定で UserInfo/ID Token が `sub` のみ |
| 2 | `ui_config.base_url` が認証UIのオリジン | テナント `ui_config` | APIサーバーURLを設定してしまう |
| 3 | `cors_config` に全フィールド設定 | テナント `cors_config` | `allow_origins` だけで `allow_headers`, `allow_methods`, `allow_credentials` が抜ける |
| 4 | `verified_claims_supported` = `true` | 認可サーバー | 未設定だと verified_claims が返らない |
| 5 | `scopes_supported` に `identity_verification_application` が含まれる | 認可サーバー | 未追加で身元確認申込APIにアクセス不可 |
| 6 | クライアントの `scope` に `identity_verification_application` が含まれる | クライアント設定 | 認可サーバーに追加してもクライアントに設定し忘れ |
| 7 | `scopes_supported` に `identity_verification_result` が含まれる | 認可サーバー | 未追加で身元確認結果取得APIにアクセス不可（403） |
| 8 | クライアントの `scope` に `identity_verification_result` が含まれる | クライアント設定 | 認可サーバーに追加してもクライアントに設定し忘れ |
| 9 | `required_identity_verification_scopes` が設定済み | 認可サーバー拡張設定 | 未設定だと身元確認が必須化されない |
| 10 | 身元確認テンプレートの外部サービスURL・認証情報が正しい | identity-verification-config | モックURLのまま本番で使用 |
| 11 | `verified_claims_mapping_rules` の `trust_framework` が設定済み | 身元確認テンプレート | 未設定だと verified_claims の verification 情報が不完全 |

### 動作確認時のprompt値

| テスト | prompt値 | 目的 |
|--------|---------|------|
| ユーザー登録 | `prompt=create` | Sign Up画面を直接表示 |
| 再認証 | `prompt=login` | 既存セッションを無視して再認証を強制 |

## 設定例ファイル参照

- テンプレート: `config/templates/use-cases/ekyc/`
- 信頼サービス（E2E参照）: `config/examples/e2e/test-tenant/identity/trust-service.json`
- eKYCテンプレート: `config/templates/use-cases/ekyc/identity-verification-config.json`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-4-extensions/identity-verification/01-guide.md`
- `documentation/docs/content_05_how-to/phase-4-extensions/identity-verification/02-application.md`
- `documentation/docs/content_05_how-to/phase-4-extensions/identity-verification/03-registration.md`
- `documentation/docs/content_02_quickstart/quickstart-07-ekyc.md`

$ARGUMENTS
