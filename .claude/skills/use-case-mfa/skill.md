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

## OTP認証の2層アーキテクチャ

Email/SMS OTP認証は **OTP管理**（生成・検証・有効期限・リトライ制限）と **メッセージ送信** の2層で構成される。

### 層1: execution.function — OTP管理の責務

| execution.function | OTP管理 | メッセージ送信 | ユースケース |
|---|---|---|---|
| `email_authentication_challenge` / `sms_authentication_challenge` | **idp-server内部** | `details.function`で選択 | 推奨（OTP管理が堅牢） |
| `http_request` | **外部サービス丸投げ** | 外部サービス丸投げ | 外部認証サービスが全責務を持つ場合 |

`email_authentication_challenge` / `sms_authentication_challenge` を使う場合、`expire_seconds` や `retry_count_limitation` は **idp-server内部で強制** される。
`http_request` を使う場合、これらの設定はidp-serverでは効かず、外部サービス側の責務になる。

### 層2: details.function — メッセージ送信方法

`execution.function` が `email_authentication_challenge` / `sms_authentication_challenge` の場合のみ有効。

| details.function | 送信方法 | 用途 |
|---|---|---|
| `no_action` | 送信しない（ログのみ） | ローカル開発・テスト |
| `http_request` | 外部API委譲（SendGrid/Twilio等） | **本番推奨** |
| ※Email限定: SMTP | idp-server内蔵SMTP送信 | Email のみ（`metadata.settings.smtp` で設定） |

### 3パターンまとめ

**Email認証**:

| # | execution.function | details.function | OTP管理 | メール送信 |
|---|---|---|---|---|
| 1 | `email_authentication_challenge` | `no_action` | 内部 | 送信しない |
| 2 | `email_authentication_challenge` | `http_request` | 内部 | 外部API委譲 |
| 3 | `http_request` | — | 外部 | 外部 |

**SMS認証**:

| # | execution.function | details.function | OTP管理 | SMS送信 |
|---|---|---|---|---|
| 1 | `sms_authentication_challenge` | `no_action` | 内部 | 送信しない |
| 2 | `sms_authentication_challenge` | `http_request` | 内部 | 外部API委譲（[#1394](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1394)） |
| 3 | `http_request` | — | 外部 | 外部 |

### 設定構造（内部OTP管理 + 外部API送信の場合）

```json
{
  "interactions": {
    "email-authentication-challenge": {
      "execution": {
        "function": "email_authentication_challenge",
        "details": {
          "function": "http_request",
          "sender": "noreply@example.com",
          "sender_config": {
            "http_request": {
              "url": "https://api.sendgrid.com/v3/mail/send",
              "method": "POST",
              "header_mapping_rules": [
                { "static_value": "application/json", "to": "Content-Type" },
                { "static_value": "Bearer {API_KEY}", "to": "Authorization" }
              ],
              "body_mapping_rules": [
                { "from": "$.request_body", "to": "*" }
              ]
            }
          },
          "templates": {
            "authentication": {
              "subject": "Your verification code",
              "body": "Code: {VERIFICATION_CODE}\nExpires in {EXPIRE_SECONDS}s."
            }
          },
          "retry_count_limitation": 5,
          "expire_seconds": 300
        }
      },
      "response": {
        "body_mapping_rules": [
          { "static_value": "sent", "to": "status", "condition": { "operation": "missing", "path": "$.error" } },
          { "from": "$.error", "to": "error", "condition": { "operation": "exists", "path": "$.error" } }
        ]
      }
    },
    "email-authentication": {
      "execution": {
        "function": "email_authentication"
      },
      "response": {
        "body_mapping_rules": [
          { "static_value": "verified", "to": "status", "condition": { "operation": "missing", "path": "$.error" } },
          { "from": "$.error", "to": "error", "condition": { "operation": "exists", "path": "$.error" } }
        ]
      }
    }
  }
}
```

**ポイント**:
- `email-authentication-challenge` の `details` 内に `sender_config.http_request` でHTTP送信先を設定
- `email-authentication`（検証）は `execution.function = "email_authentication"` のみ（内部検証、外部APIコール不要）
- `retry_count_limitation` と `expire_seconds` は `details` 内に設定し、idp-server内部で強制される

### 実装クラス

| コンポーネント | クラス |
|---|---|
| OTP生成・検証（Email） | `EmailChallengeAuthenticationExecutor`, `EmailAuthenticationExecutor` |
| OTP生成・検証（SMS） | `SmsChallengeAuthenticationExecutor`, `SmsAuthenticationExecutor` |
| メール送信（no_action） | `NoActionEmailSender` |
| メール送信（HTTP） | `HttpRequestEmailSender` |
| SMS送信（no_action） | `NoActionSmsSender` |
| SMS送信（HTTP） | `HttpRequestSmsSender`（[#1394](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1394)） |
| 送信者選択 | `EmailSenders.get(String function)` / `SmsSenders.get(SmsSenderType)` |
| 設定読み込み | `EmailAuthenticationConfiguration`, `EmailSenderConfiguration` |

---

## SMS と Email の送信方式の違い（簡易比較）

| 項目 | SMS | Email |
|------|-----|-------|
| idp-server 内蔵送信 | なし | あり（SMTP） |
| 外部API委譲 | `http_request`（Twilio等） | `http_request`（SendGrid等） |
| ローカル開発 | `no_action`（OTP生成のみ、送信なし） | `no_action`（OTP生成のみ、送信なし） |

- **SMS**: 送信機能を内蔵していないため、本番では必ず外部SMS送信サービスへの `http_request` 委譲が必要
- **Email**: SMTP設定（`metadata.settings.smtp`）を行えば idp-server 単体でメール送信可能。外部API委譲も選択可

## 設定対象と手順

### 1. SMS認証メソッド設定

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations`

> **パターン選択**: 下記は外部丸投げ（`execution.function = "http_request"`）パターン。
> 内部OTP管理 + 外部SMS送信パターンは「OTP認証の2層アーキテクチャ」セクションを参照。

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
          "auth_type": "oauth2",
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
          { "from": "$.execution_http_request.response_body.status", "to": "status" },
          { "from": "$.execution_http_request.response_body.message", "to": "message", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.message" } },
          { "from": "$.execution_http_request.response_body.error", "to": "error", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error" } },
          { "from": "$.execution_http_request.response_body.error_description", "to": "error_description", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_description" } }
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
          "auth_type": "oauth2",
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
            { "from": "$.request_body", "to": "*" },
            { "from": "$.interaction.transaction_id", "to": "transaction_id" }
          ]
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body.status", "to": "status" },
          { "from": "$.execution_http_request.response_body.message", "to": "message", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.message" } },
          { "from": "$.execution_http_request.response_body.error", "to": "error", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error" } },
          { "from": "$.execution_http_request.response_body.error_description", "to": "error_description", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_description" } }
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

> **パターン選択**: 下記は外部丸投げ（`execution.function = "http_request"`）パターン。
> 内部OTP管理 + 外部メール送信パターンは「OTP認証の2層アーキテクチャ」セクションを参照。
> 内部OTP管理を使う場合、`expire_seconds` と `retry_count_limitation` はidp-server内部で強制される。

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
          "auth_type": "oauth2",
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

### 4. クレーム設定（認可サーバー更新）

> **重要**: この設定が無いと UserInfo / ID Token が `sub` のみしか返さない。
> 詳細は `use-case-login` スキルの「クレーム設定」セクションを参照。

認可サーバーの `claims_supported` に返したいクレーム一覧を設定する。
標準的な設定は `config/templates/tenant-template.json` を参照。

## 内部/外部で異なる response.body_mapping_rules

> 詳細は `spec-external-integration` スキルの「認証設定の内部/外部で異なるマッピングパス」を参照。

| 項目 | 内部ビルトイン (`email_authentication_challenge` 等) | 外部 HTTP (`http_request`) |
|------|------|------|
| response のマッピングパス | `$` (executor contents 直接) | `$.execution_http_request.response_body` |
| 送信ボディで前のインタラクション参照 | N/A | `$.interaction.xxx` |
| ワイルドカード `"to": "*"` | 内部データ漏洩リスク（`verification_code` 等） | 外部レスポンス丸ごと漏洩リスク |
| 推奨 | `static_value` + 条件付きエラーマッピング | 必要フィールドだけ明示的にマッピング |

## step_definitions リファレンス

認証ポリシーの `step_definitions` でステップの実行順序とユーザー特定方法を定義する。

| フィールド | 説明 |
|-----------|------|
| `method` | 認証メソッド名（`password`, `email`, `sms`, `fido2` 等） |
| `order` | 実行順序（1が最初） |
| `requires_user` | `true`: 前のステップで既にユーザーが特定されていることが前提。`false`: このステップ自身がユーザーを特定する |
| `user_identity_source` | `requires_user: false` の場合に必要。どのフィールドでユーザーを特定するか（`"email"`, `"username"` 等） |

> **重要**: 最初のステップ（order=1）には必ず `requires_user: false` + `user_identity_source` を設定すること。
> `requires_user: true` を最初のステップに設定すると、まだユーザーが特定されていないため `user_not_found` エラーになる。

**例: Email → Password（デフォルト）**:
```json
"step_definitions": [
  { "method": "email", "order": 1, "requires_user": false, "user_identity_source": "email" },
  { "method": "password", "order": 2, "requires_user": true }
]
```

**例: Password → Email（順序反転）**:
```json
"step_definitions": [
  { "method": "password", "order": 1, "requires_user": false, "user_identity_source": "username" },
  { "method": "email", "order": 2, "requires_user": true }
]
```

## conditions と success_conditions の違い

> **注意**: `conditions` と `success_conditions` は構造が異なる。混同しないこと。

### conditions（ポリシー適用条件）

どのリクエストにこのポリシーを適用するかを決める。**専用フィールド**で指定する:

```json
// スコープ条件: transfers スコープを要求した場合のみ適用
"conditions": { "scopes": ["transfers"] }

// 無条件（全リクエストに適用）
"conditions": {}
```

**NG**: `conditions` に `any_of` + JSONPath を使うのは誤り。それは `success_conditions` の構造。

> **重要: priority とconditions の関係**
> - `conditions.scopes` が空 `[]` のポリシーは**全リクエストにマッチ**する
> - マッチした全ポリシーの中から **priority が最も高いもの**が選択される
> - そのため、スコープ条件付きポリシーは、デフォルトポリシーよりも **priority を高く** 設定すること
> - 例: `mfa_for_transfers` (priority: 10) > `password_only` (priority: 1)

### success_conditions（認証成功条件）

認証が成功したと判定する条件。**`any_of` + JSONPath** で指定する:

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

## 設定確認チェックリスト

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 1 | `claims_supported` が設定済み | 認可サーバー | 未設定で UserInfo/ID Token が `sub` のみ |
| 2 | `ui_config.base_url` が認証UIのオリジン | テナント `ui_config` | APIサーバーURLを設定してしまう |
| 3 | `cors_config` に全フィールド設定 | テナント `cors_config` | `allow_origins` だけで `allow_headers`, `allow_methods`, `allow_credentials` が抜ける |
| 4 | email/SMS認証設定が存在する | authentication-config | 未作成で `Authentication Configuration Not Found` エラー |
| 5 | 認証ポリシーの `success_conditions` がAND条件 | 認証ポリシー | `[[条件1, 条件2]]`（AND）ではなく `[[条件1], [条件2]]`（OR）にしてしまう |
| 6 | `failure_conditions` / `lock_conditions` 設定済み | 認証ポリシー | 未設定だと認証失敗でアカウントロックされない |
| 7 | no-actionモードの場合、Management APIで検証コード取得可能 | 動作確認手順 | 管理者トークンのスコープに `management` が含まれていない |
| 8 | 最初のステップに `requires_user: false` + `user_identity_source` | `step_definitions` | 最初のステップに `requires_user: true` を設定して `user_not_found` エラー |

### MFA の amr 確認

MFA が正しく実行されたかは ID Token の `amr` クレームで確認できる:

```bash
echo "${ID_TOKEN}" | cut -d'.' -f2 | python3 -c "import sys,base64,json; print(json.dumps(json.loads(base64.urlsafe_b64decode(sys.stdin.read().strip()+'==')),indent=2))"
```

- `amr` に `["email", "password"]` のように両方の認証方式が含まれていればMFA成功

### 動作確認時のprompt値

| テスト | prompt値 | 目的 |
|--------|---------|------|
| ユーザー登録 | `prompt=create` | Sign Up画面を直接表示 |
| MFA再認証 | `prompt=login` | 既存セッションを無視してMFA認証を再実行 |

## 設定例ファイル参照

- テンプレート: `config/templates/use-cases/mfa-email/`
- SMS認証: `config/examples/e2e/.../authentication-config/sms/external.json`
- メール認証: `config/examples/e2e/.../authentication-config/email/smtp.json`
- 認証ポリシー: `config/examples/e2e/.../authentication-policy/oauth.json`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-2-security/01-mfa-setup.md`
- `documentation/docs/content_05_how-to/phase-1-foundation/07-authentication-policy.md`
- `documentation/docs/content_05_how-to/phase-2-security/03-authentication-policy-advanced.md` — `conditions`（`scopes`, `client_ids`）、`failure_conditions`、`lock_conditions`、`step_definitions` の詳細
- `documentation/docs/content_06_developer-guide/05-configuration/authentication-policy.md` — 設定リファレンス
- `documentation/docs/content_02_quickstart/quickstart-05-mfa.md`

$ARGUMENTS
