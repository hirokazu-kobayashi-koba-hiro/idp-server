---
name: use-case-external-password-auth
description: 外部パスワード認証委譲ユースケースの設定ガイド。外部認証サービスURL、マッピングルール、ブルートフォース防止、セッション・トークン有効期限のヒアリングと環境変数マッピングを提供。
---

# 外部パスワード認証委譲

パスワード認証を外部サービス（HTTP API）に委譲するユースケース。
idp-server 内蔵のパスワード検証を使わず、`authentication-configurations` の `execution.function = "http_request"` を使って外部 API にユーザー名/パスワードを転送し、レスポンスからユーザー情報をマッピングする。

**ユースケース**: 既存の認証基盤（社内認証 API 等）を持つ組織が、idp-server を OIDC レイヤーとして導入するケース。idp-server は HTTP API で外部サービスと連携するため、外部サービスが HTTP エンドポイントを提供している必要がある。

## テンプレート実行

**テンプレート**: `config/templates/use-cases/external-password-auth/`

```bash
# 基本実行
bash config/templates/use-cases/external-password-auth/setup.sh

# カスタマイズ例
ORGANIZATION_NAME="my-company" \
EXTERNAL_AUTH_URL="https://auth.internal.example.com/api/authenticate" \
EXTERNAL_PROVIDER_ID="ldap-wrapper" \
SESSION_TIMEOUT_SECONDS=3600 \
ACCESS_TOKEN_DURATION=1800 \
REFRESH_TOKEN_DURATION=604800 \
bash config/templates/use-cases/external-password-auth/setup.sh

# ドライラン（実際には作成しない）
bash config/templates/use-cases/external-password-auth/setup.sh --dry-run
```

## 細かい設定 Q&A（逆引き）

**「やりたいこと -> 設定」の対応表**: [qa.md](./qa.md)

ユーザーが具体的にやりたいことを言った場合は、qa.md を参照して該当するQ&Aの設定キー+値を提示すること。

## 設定変更 x 挙動確認（ハンズオン）

**「設定を変えて → 挙動が変わることを体験する」実験ガイド**:

- `config/templates/use-cases/external-password-auth/EXPERIMENTS.md` - `http_request`（単数）の基本実験
- `config/templates/use-cases/external-password-auth/EXPERIMENTS-http-requests.md` - `http_requests`（複数形）の複数API チェーン実験

設定の効果を手元で確認したい場合はこれらのガイドを案内すること。

**自動検証スクリプト**: `config/templates/use-cases/external-password-auth/verify.sh`

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | 外部認証サービスURL | 実サービスURL / モック（localhost:4001） | 認証メソッド設定 `http_request.url` |
| 2 | 外部プロバイダーID | 任意の識別子（例: `ldap-wrapper`, `legacy-auth`） | 認証メソッド設定 `user_mapping_rules[].static_value` |
| 3 | アカウントロック条件 | 失敗回数（デフォルト: 5回） | 認証ポリシー `failure_conditions` / `lock_conditions` |
| 4 | セッション有効期限 | 秒数（デフォルト: 86400 = 24時間） | テナント `session_config.timeout_seconds` |
| 5 | トークン有効期限（AT） | 秒数（デフォルト: 3600 = 1時間） | 認可サーバー `extension.access_token_duration` |
| 6 | トークン有効期限（IDT） | 秒数（デフォルト: 3600 = 1時間） | 認可サーバー `extension.id_token_duration` |
| 7 | トークン有効期限（RT） | 秒数（デフォルト: 86400 = 24時間） | 認可サーバー `extension.refresh_token_duration` |

## ヒアリング結果 -> 環境変数マッピング

### 外部認証サービス

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| 外部認証サービスURL | `EXTERNAL_AUTH_URL` | `http://host.docker.internal:4001/auth/password` |
| 外部プロバイダー識別子 | `EXTERNAL_PROVIDER_ID` | `external-auth` |

### セッション設定

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| セッション有効期限（秒） | `SESSION_TIMEOUT_SECONDS` | `86400` |

### トークン有効期限

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| AT有効期限（秒） | `ACCESS_TOKEN_DURATION` | `3600` |
| IDT有効期限（秒） | `ID_TOKEN_DURATION` | `3600` |
| RT有効期限（秒） | `REFRESH_TOKEN_DURATION` | `86400` |

---

## 設定対象と手順

### 1. 外部パスワード認証設定（認証メソッド設定） -- 核心

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations`

この設定が外部パスワード認証委譲の核心。`execution.function = "http_request"` で外部サービスにリクエストを転送し、`user_mapping_rules` でレスポンスをユーザー属性にマッピングする。

```json
{
  "type": "password",
  "metadata": {
    "description": "External password authentication via HTTP request"
  },
  "interactions": {
    "password-authentication": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://auth.example.com/api/authenticate",
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
          { "static_value": "external-auth", "to": "provider_id" }
        ]
      },
      "response": {
        "body_mapping_rules": [
          {
            "from": "$.execution_http_request.response_body.user_id",
            "to": "user_id",
            "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.user_id" }
          },
          {
            "from": "$.execution_http_request.response_body.email",
            "to": "email",
            "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.email" }
          },
          {
            "from": "$.execution_http_request.response_body.error",
            "to": "error",
            "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error" }
          },
          {
            "from": "$.execution_http_request.response_body.error_description",
            "to": "error_description",
            "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_description" }
          }
        ]
      }
    }
  }
}
```

**外部 API の契約**:

| 項目 | 仕様 |
|------|------|
| リクエスト | `POST` `{"username": "...", "password": "..."}` |
| 成功 (HTTP 200) | `{"user_id": "...", "email": "...", "name": "..."}` |
| 失敗 (HTTP 401) | `{"error": "invalid_credentials", "error_description": "..."}` |

**マッピングルール**:

| 外部レスポンス | idp-server 属性 | 説明 |
|---------------|----------------|------|
| `user_id` | `external_user_id` | 外部サービスのユーザーID |
| `email` | `email` | メールアドレス |
| `name` | `name` | 表示名 |
| (static) | `provider_id` | 外部サービス識別子 |

### 1b. 複数API チェーン設定（http_requests）

複数の外部APIを順番に呼び出し、結果を統合するパターン。例: 認証API → ユーザー詳細API。

| | `http_request`（単数） | `http_requests`（複数形） |
|---|---|---|
| リクエスト数 | 1つ | 複数（順番に実行） |
| 設定キー | `execution.http_request` (object) | `execution.http_requests` (array) |
| 結果パス | `$.execution_http_request.response_body.*` | `$.execution_http_requests[0].response_body.*` |
| チェーン | 不可 | 前のレスポンスを次のリクエストで使える |
| エラー時 | 即座に返却 | 失敗した時点で中断、それまでの結果を保持 |

**複数API呼び出し時のエラーハンドリング**:

成功フィールドとエラーフィールドが共存しないよう、`allOf` + `missing` 複合条件を使う:

```json
{
  "from": "$.execution_http_requests[0].response_body.user_id",
  "to": "user_id",
  "condition": {
    "operation": "allOf",
    "value": [
      { "operation": "exists", "path": "$.execution_http_requests[0].response_body.user_id" },
      { "operation": "missing", "path": "$.execution_http_requests[1].response_body.error" }
    ]
  }
}
```

- `exists` のみだと、API 2 が失敗してもAPI 1 の成功フィールドが返ってしまう
- `allOf` で「自身が存在 AND 後続APIのエラーが missing」を両方満たす場合のみ出力

**functions（ヘッダー動的生成）**:

```json
"header_mapping_rules": [
  {
    "from": "$.unused",
    "to": "x-request-id",
    "functions": [
      { "name": "random_string", "args": { "length": 8 } },
      { "name": "format", "args": { "template": "trace-{{value}}" } }
    ]
  }
]
```

使える functions: `format`（テンプレート変換）、`random_string`（ランダム文字列）、`now`（現在時刻）、`exists`（boolean化）。`from: "$.unused"` は入力不要な functions 用の特殊パス。

詳細と実験手順は `EXPERIMENTS-http-requests.md` を参照。

### 2. 認証ポリシー（パスワードのみ、failure/lock_conditions あり）

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-policies`

login-password-only との主な差分:
- `available_methods` は `["password"]` のみ（`initial-registration` なし = ユーザー自己登録なし）
- `failure_conditions` / `lock_conditions` を設定（5回失敗でアカウントロック）

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "external_password_only",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password"],
      "success_conditions": {
        "any_of": [
          [
            {
              "path": "$.password-authentication.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ]
        ]
      },
      "failure_conditions": {
        "any_of": [
          [
            {
              "path": "$.password-authentication.failure_count",
              "type": "integer",
              "operation": "gte",
              "value": 5
            }
          ]
        ]
      },
      "lock_conditions": {
        "any_of": [
          [
            {
              "path": "$.password-authentication.failure_count",
              "type": "integer",
              "operation": "gte",
              "value": 5
            }
          ]
        ]
      }
    }
  ]
}
```

### 3. クレーム設定（claims_supported）

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

> **重要**: この設定が無いと UserInfo / ID Token が `sub` のみしか返さない。

テンプレートでは `public-tenant-template.json` に以下が含まれている:

```json
{
  "claims_supported": [
    "sub", "iss", "auth_time", "acr",
    "name", "given_name", "family_name", "nickname", "preferred_username", "middle_name",
    "profile", "picture", "website",
    "email", "email_verified",
    "gender", "birthdate", "zoneinfo", "locale", "updated_at",
    "address", "phone_number", "phone_number_verified"
  ],
  "claims_parameter_supported": true
}
```

---

## login-password-only との差分

| 項目 | login-password-only | external-password-auth |
|------|--------------------|-----------------------|
| パスワード検証 | idp-server 内蔵 | 外部サービス（http_request） |
| パスワードポリシー | テナントに設定 | なし（外部サービスが管理） |
| ユーザー登録 | initial-registration | なし（外部サービスが管理） |
| ユーザー識別 | EMAIL | EMAIL_OR_EXTERNAL_USER_ID |
| 認証ポリシー | password + initial-registration | password のみ |
| ブルートフォース防止 | なし | failure_conditions + lock_conditions |

---

## 設定確認チェックリスト

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 1 | `claims_supported` が設定済み | 認可サーバー | 未設定で UserInfo/ID Token が `sub` のみ返却 |
| 2 | `EXTERNAL_AUTH_URL` が idp-server コンテナから到達可能 | 認証メソッド設定 | `localhost` を指定（Docker 内から到達不可）→ `host.docker.internal` を使う |
| 3 | `EXTERNAL_PROVIDER_ID` が一意 | 認証メソッド設定 | 複数テナントで同じ provider_id を使うと衝突の可能性 |
| 4 | 外部サービスのレスポンスに `user_id` フィールドがある | 外部サービス | フィールド名の不一致で `external_user_id` が `null` になる |
| 5 | `identity_unique_key_type` が `EMAIL_OR_EXTERNAL_USER_ID` | テナント `identity_policy_config` | `EMAIL` のままだと外部ユーザーIDでの識別が機能しない |
| 6 | `failure_conditions` / `lock_conditions` が設定済み | 認証ポリシー | 未設定だと認証失敗でアカウントロックされない |
| 7 | `ui_config.base_url` が認証UIのオリジン | テナント `ui_config` | APIサーバーURLを設定してしまう |
| 8 | パスワードポリシーが未設定であること | テナント `identity_policy_config` | 外部サービス管理なのに idp-server 側にもポリシーを設定してしまう |

---

## 設定例ファイル参照

- テンプレート: `config/templates/use-cases/external-password-auth/`
- 認証メソッド設定: `config/templates/use-cases/external-password-auth/authentication-config-password-template.json`
- 認証ポリシー: `config/templates/use-cases/external-password-auth/authentication-policy.json`
- テナント設定: `config/templates/use-cases/external-password-auth/public-tenant-template.json`
- モックサーバー: `config/templates/use-cases/external-password-auth/mock-server.js`

## 関連ドキュメント

- `config/templates/use-cases/external-password-auth/README.md` - テンプレートの詳細説明
- `documentation/docs/content_05_how-to/phase-1-foundation/07-authentication-policy.md` - 認証ポリシー設定
- スキル `spec-external-integration` - 外部サービス連携の詳細（HTTP Request Executor, MappingRule）
- スキル `use-case-login` - login-password-only との共通設定（セッション、トークン等）

$ARGUMENTS
