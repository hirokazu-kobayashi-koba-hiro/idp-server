# Email認証

このドキュメントは、`email-authentication` 方式による認証処理の `概要`・`設定`・`利用方法` について説明します。

---

## 概要

Email認証は、ユーザーのメールアドレスにワンタイムコード（OTP）を送信し、それを使って本人確認を行う認証方式です。

`idp-server` ではEmail認証機能を以下のパターンで提供します：

1. **外部Email認証サービスを利用**: 外部APIサービスでコード生成・送信・検証を行う
2. **SMTP送信**: idp-serverでコード生成・検証し、SMTPでメール送信
3. **HTTP API送信**: idp-serverでコード生成・検証し、HTTP APIでメール送信
4. **テスト用（no-action）**: 実際のメール送信を行わないテスト設定

Email認証は以下の2つのインタラクションを連続的に実行することで成立します：

* `email-authentication-challenge`: 認証用ワンタイムコードの生成・送信
* `email-authentication`: ワンタイムコードの検証

### テンプレート機能

メールテンプレートでは以下のプレースホルダーが利用可能です：

- `{VERIFICATION_CODE}`: 生成されたワンタイムコード（6桁数字）
- `{EXPIRE_SECONDS}`: 有効期限（秒数）

これらのプレースホルダーは、SMTP送信・HTTP API送信・テスト用設定のすべてで共通して使用できます。

---

## 設定

Email認証を使用するには、テナントに `type = "email"` の認証設定を登録する必要があります。

### 基本構造

すべての認証設定は、統一されたinteractions形式を使用します。Email認証では**2つのinteraction**を定義します：

1. **email-authentication-challenge**: ワンタイムコード生成・送信
2. **email-authentication**: ワンタイムコード検証

```json
{
  "id": "UUID",
  "type": "email",
  "attributes": {},
  "metadata": {
    "type": "external",
    "description": "Email authentication",
    "transaction_id_param": "transaction_id",
    "verification_code_param": "verification_code"
  },
  "interactions": {
    "email-authentication-challenge": {
      "request": { "schema": {...} },
      "execution": { "function": "email_authentication_challenge", "details": {...} },
      "response": { "body_mapping_rules": [...] }
    },
    "email-authentication": {
      "request": { "schema": {...} },
      "execution": { "function": "email_authentication" },
      "response": { "body_mapping_rules": [...] }
    }
  }
}
```

### Interaction 1: email-authentication-challenge

ワンタイムコードを生成し、メール送信するinteractionです。

#### Request Schema

```json
{
  "request": {
    "schema": {
      "type": "object",
      "properties": {
        "email": { "type": "string" },
        "template": { "type": "string" }
      }
    }
  }
}
```

#### Execution

**function**: `email_authentication_challenge`

**details設定**:

```json
{
  "execution": {
    "function": "email_authentication_challenge",
    "details": {
      "function": "no_action",
      "sender": "test@gmail.com",
      "templates": {
        "registration": {
          "subject": "[ID Verification] Your signup email confirmation code",
          "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds."
        },
        "authentication": {
          "subject": "[ID Verification] Your login email confirmation code",
          "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds."
        }
      },
      "retry_count_limitation": 5,
      "expire_seconds": 300
    }
  }
}
```

**details.function**:
- `no_action`: テスト用（実際のメール送信なし）
- `smtp`: SMTP経由でメール送信
- `http_request`: HTTP API経由でメール送信

#### Response

```json
{
  "response": {
    "body_mapping_rules": [
      { "from": "$.response_body", "to": "*" }
    ]
  }
}
```

---

### Interaction 2: email-authentication

送信されたワンタイムコードを検証するinteractionです。

#### Request Schema

```json
{
  "request": {
    "schema": {
      "type": "object",
      "properties": {
        "verification_code": { "type": "string" }
      }
    }
  }
}
```

#### Execution

**function**: `email_authentication`

ワンタイムコードの検証を行います。

#### Response

```json
{
  "response": {
    "body_mapping_rules": [
      { "from": "$.response_body", "to": "*" }
    ]
  }
}
```

---

### 完全な設定例（no-action）

**情報源**: `config/examples/e2e/test-tenant/authentication-config/email/no-action.json`

```json
{
  "id": "6f5b0255-8faf-42cf-9f24-4f702386993f",
  "type": "email",
  "attributes": {},
  "metadata": {
    "type": "external",
    "description": "mocky",
    "transaction_id_param": "transaction_id",
    "verification_code_param": "verification_code"
  },
  "interactions": {
    "email-authentication-challenge": {
      "request": {
        "schema": {
          "type": "object",
          "properties": {
            "email": { "type": "string" },
            "template": { "type": "string" }
          }
        }
      },
      "pre_hook": {},
      "execution": {
        "function": "email_authentication_challenge",
        "details": {
          "function": "no_action",
          "sender": "test@gmail.com",
          "templates": {
            "registration": {
              "subject": "[ID Verification] Your signup email confirmation code",
              "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\nIf you did not request this, please contact your administrator.\n\n– IDP Support"
            },
            "authentication": {
              "subject": "[ID Verification] Your login email confirmation code",
              "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\nIf you did not request this, please contact your administrator.\n\n– IDP Support"
            }
          },
          "retry_count_limitation": 5,
          "expire_seconds": 300
        }
      },
      "post_hook": {},
      "response": {
        "body_mapping_rules": [
          { "from": "$.response_body", "to": "*" }
        ]
      }
    },
    "email-authentication": {
      "request": {
        "schema": {
          "type": "object",
          "properties": {
            "verification_code": { "type": "string" }
          }
        }
      },
      "pre_hook": {},
      "execution": {
        "function": "email_authentication"
      },
      "post_hook": {},
      "response": {
        "body_mapping_rules": [
          { "from": "$.response_body", "to": "*" }
        ]
      }
    }
  }
}
```

---

## 設定パターン詳細

### パターン1: テスト用（no-action）

実際のメール送信を行わず、ワンタイムコードのみ生成するテスト用設定です。

**execution.details.function**: `"no_action"`

上記の完全な設定例を参照してください。

---

### パターン2: SMTP送信

idp-serverでワンタイムコードを生成・検証し、SMTPでメール送信する設定です。

#### execution.details設定

```json
{
  "execution": {
    "function": "email_authentication_challenge",
    "details": {
      "function": "smtp",
      "sender": "noreply@example.com",
      "smtp_config": {
        "host": "smtp.gmail.com",
        "port": 587,
        "username": "your-email@gmail.com",
        "password": "your-app-password",
        "auth": true,
        "starttls_enable": true
      },
      "templates": {
        "registration": {
          "subject": "[ID Verification] Your signup email confirmation code",
          "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds."
        },
        "authentication": {
          "subject": "[ID Verification] Your login email confirmation code",
          "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds."
        }
      },
      "retry_count_limitation": 5,
      "expire_seconds": 300
    }
  }
}
```

---

### パターン3: HTTP API送信

idp-serverでワンタイムコードを生成・検証し、HTTP APIでメール送信する設定です。

**情報源**: `config/examples/e2e/test-tenant/authentication-config/email/external.json`

#### execution.details設定

```json
{
  "execution": {
    "function": "email_authentication_challenge",
    "details": {
      "function": "http_request",
      "sender": "noreply@example.com",
      "http_request_config": {
        "url": "https://api.sendgrid.com/v3/mail/send",
        "method": "POST",
        "auth_type": "bearer",
        "bearer_token": "your-api-key",
        "header_mapping_rules": [...],
        "body_mapping_rules": [...]
      },
      "templates": {
        "registration": { "subject": "...", "body": "..." },
        "authentication": { "subject": "...", "body": "..." }
      },
      "retry_count_limitation": 5,
      "expire_seconds": 300
    }
  }
}
```

---

## 旧設定方式（廃止予定）

以下の設定方式は旧バージョンで使用されていた構造です。新規実装ではinteractions形式を使用してください。

### 2. SMTP送信

idp-serverでワンタイムコードを生成・検証し、SMTPでメール送信する設定です。

#### 基本構造

```json
{
  "id": "設定ID",
  "type": "email",
  "payload": {
    "type": "smtp",
    "sender": "送信元メールアドレス",
    "settings": {
      "smtp": { /* SMTP設定 */ }
    },
    "templates": { /* メールテンプレート */ },
    "retry_count_limitation": 5,
    "expire_seconds": 300
  }
}
```

#### SMTP設定

| 項目                | 型       | 説明                                     |
|-------------------|---------|----------------------------------------|
| `host`            | string  | SMTPサーバーのホスト名（例：`smtp.gmail.com`）      |
| `port`            | integer | SMTPポート番号（通常は `587` または `465`）         |
| `username`        | string  | SMTP認証用ユーザー名                          |
| `password`        | string  | SMTP認証用パスワード                          |
| `auth`            | boolean | SMTP認証を使用するかどうか                       |
| `starttls.enable` | boolean | STARTTLSを有効にするか（`true`で通信が暗号化される）    |

#### Email認証設定

| 項目                       | 説明                            |
|--------------------------|-------------------------------|
| `sender`                 | 送信元メールアドレス                    |
| `retry_count_limitation` | 検証リトライ上限回数（デフォルト：5）           |
| `expire_seconds`         | ワンタイムコードの有効期限（秒、デフォルト：300）    |
| `templates`              | メールテンプレート（`registration`、`authentication`） |

#### テンプレート設定

各テンプレートには `subject`（件名）と `body`（本文）を設定：

```json
"templates": {
  "registration": {
    "subject": "[ID Verification] Your signup confirmation code",
    "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds."
  },
  "authentication": {
    "subject": "[ID Verification] Your login confirmation code",
    "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds."
  }
}
```

### 3. HTTP API送信

idp-serverでワンタイムコードを生成・検証し、HTTP APIでメール送信する設定です。SendGrid、Mailgun等のメール配信サービスAPIと連携できます。

#### 基本構造

```json
{
  "id": "設定ID",
  "type": "email",
  "payload": {
    "type": "http_request",
    "function": "http_request",
    "sender": "送信元メールアドレス",
    "sender_config": {
      "http_request": { /* HTTP API設定 */ }
    },
    "templates": {
      "registration": {
        "subject": "[ID Verification] Your signup confirmation code",
        "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds."
      },
      "authentication": {
        "subject": "[ID Verification] Your login confirmation code", 
        "body": "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds."
      }
    },
    "retry_count_limitation": 5,
    "expire_seconds": 300
  }
}
```

**重要**: HTTP API送信でも、idp-server側でテンプレート処理（プレースホルダー置換）を行うため、`templates`設定が必須です。

#### HTTP API設定

HTTP APIへのリクエストマッピングを `sender_config.http_request` で設定します：

```json
"sender_config": {
  "http_request": {
    "url": "https://api.sendgrid.v3/mail/send",
    "method": "POST",
    "headers": {
      "Authorization": "Bearer ${API_KEY}",
      "Content-Type": "application/json"
    },
    "body_mapping_rules": [
      {
        "from": "$.request_body.from",
        "to": "from.email"
      },
      {
        "from": "$.request_body.to",
        "to": "personalizations[0].to[0].email"
      },
      {
        "from": "$.request_body.subject", 
        "to": "subject"
      },
      {
        "from": "$.request_body.body",
        "to": "content[0].value"
      },
      {
        "static_value": "text/plain",
        "to": "content[0].type"
      }
    ]
  }
}
```

**送信データフロー**:
1. idp-serverが `templates` でメール内容を生成（プレースホルダー置換）
2. `EmailSendingRequest` が作成され、以下のデータが `request_body` として利用可能：
   ```json
   {
     "from": "送信元メールアドレス",
     "to": "宛先メールアドレス", 
     "subject": "置換済み件名",
     "body": "置換済み本文"
   }
   ```
3. `body_mapping_rules` により外部API形式にマッピング

#### 利用可能なメール配信サービス例

| サービス | API URL | 認証方式 |
|----------|---------|----------|
| SendGrid | `https://api.sendgrid.v3/mail/send` | Bearer Token |
| Mailgun | `https://api.mailgun.net/v3/{domain}/messages` | Basic Auth |
| Amazon SES | `https://email.{region}.amazonaws.com/` | AWS Signature |
| Postmark | `https://api.postmarkapp.com/email` | X-Postmark-Server-Token |

### 4. テスト用（no-action）

実際のメール送信を行わない、開発・テスト用の設定です。

```json
{
  "id": "設定ID",
  "type": "email",
  "interactions": {
    "email-authentication-challenge": {
      "execution": {
        "function": "email_authentication_challenge",
        "details": {
          "function": "no_action",
          "sender": "test@example.com",
          "templates": { /* テンプレート設定 */ },
          "retry_count_limitation": 5,
          "expire_seconds": 300
        }
      }
    },
    "email-authentication": {
      "execution": {
        "function": "email_authentication"
      }
    }
  }
}
```

## 利用方法

### 事前準備

1. テナントに `type = "email"` の設定（`AuthenticationConfiguration`）を登録する
2. 認証ポリシーでEmail認証を有効化する

### 認証フロー

Email認証は以下の2段階で実行されます：

#### 1. チャレンジ送信（email-authentication-challenge）

```http
POST /v1/authorizations/{authorization_id}/email-authentication-challenge
Content-Type: application/json

{
  "email": "user@example.com",
  "template": "authentication"
}
```

**レスポンス例：**
```json
{
  "status": "challenge_sent",
  "message": "Verification code has been sent to your email"
}
```

#### 2. コード検証（email-authentication）

```http
POST /v1/authorizations/{authorization_id}/email-authentication
Content-Type: application/json

{
  "verification_code": "123456"
}
```

**レスポンス例：**
```json
{
  "status": "success",
  "user": {
    "sub": "user-id",
    "email_verified": true
  }
}
```

### エラーパターン

#### コード期限切れ
```json
{
  "error": "invalid_request",
  "error_description": "email challenge is expired"
}
```

#### 試行回数上限
```json
{
  "error": "invalid_request", 
  "error_description": "email challenge is reached limited to 5 attempts"
}
```

#### コード不一致
```json
{
  "error": "invalid_request",
  "error_description": "Invalid verification code"
}
```

---

## 実装詳細

### 主要クラス

| クラス名 | 役割 |
|---------|-----|
| `EmailAuthenticationInteractor` | Email認証のメインロジック（`libs/idp-server-authentication-interactors`) |
| `EmailAuthenticationExecutor` | ワンタイムコード検証処理 |
| `EmailVerificationChallenge` | チャレンジ情報の管理（コード、有効期限、試行回数） |
| `EmailAuthenticationConfiguration` | Email認証設定の管理 |

### 処理の流れ

1. **チャレンジ送信時**：
   - ワンタイムコード生成（6桁数字）
   - 有効期限・試行回数制限の設定
   - テンプレートに基づくメール送信

2. **コード検証時**：
   - 有効期限チェック
   - 試行回数チェック  
   - コード照合
   - ユーザーの `email_verified` フラグを `true` に更新

### 設定例参照

実際の設定例は以下のディレクトリを参照してください：

- `config-sample/*/authentication-config/email/smtp.json` - SMTP設定
- `config-sample/*/authentication-config/email/external.json` - 外部API設定（http_request使用）
- `config-sample/*/authentication-config/email/no-action.json` - テスト設定

### 送信方式の比較

| 送信方式 | 設定の複雑さ | 拡張性 | 推奨用途 |
|---------|------------|--------|----------|
| **外部API（http_request）** | ★★★ | ★★★ | 外部サービス完全委譲、高度なカスタマイズ |
| **SMTP** | ★★ | ★★ | 既存SMTPサーバー活用、シンプル構成 |
| **HTTP API** | ★★ | ★★★ | クラウドメール配信サービス活用 |
| **no-action** | ★ | ★ | 開発・テスト環境 |
