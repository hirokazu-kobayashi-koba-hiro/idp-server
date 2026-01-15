# Identity Verification設定ガイド

## このドキュメントの目的

Identity Verification（身元確認/eKYC）の設定方法を理解します。

### 所要時間
⏱️ **約20分**

---

## Identity Verificationとは

**Identity Verification**はeKYC（electronic Know Your Customer）や本人確認プロセスを管理する機能です。

**ユースケース**:
- 顔認証による本人確認
- 身分証明書の検証
- 口座情報による本人確認
- VIPステータス確認

---

## 設定ファイル構造

### identity-verification/face-verification.json

```json
{
  "id": "ed5c1717-98eb-4415-898d-6d4584810b5e",
  "type": "face-verification",
  "attributes": {
    "label": "顔認証",
    "provider": "external-provider"
  },
  "processes": {
    "start": {
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "${VERIFICATION_API_URL}/verify/start",
          "method": "POST",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "client_credentials",
            "token_endpoint": "${AUTH_URL}/token",
            "client_id": "${CLIENT_ID}",
            "client_secret": "${CLIENT_SECRET}"
          }
        }
      },
      "store": {
        "application_details_mapping_rules": [
          {
            "from": "$.response_body.session_id",
            "to": "verification_session.id"
          }
        ]
      },
      "response": {
        "body_mapping_rules": [
          {
            "from": "$.response_body.session_id",
            "to": "session_id"
          }
        ]
      }
    },
    "check-status": {
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "${VERIFICATION_API_URL}/verify/status",
          "method": "POST"
        }
      },
      "transition": {
        "approved": {
          "any_of": [
            [
              {
                "path": "$.response_body.status",
                "type": "string",
                "operation": "eq",
                "value": "verified"
              }
            ]
          ]
        },
        "rejected": {
          "any_of": [
            [
              {
                "path": "$.response_body.status",
                "type": "string",
                "operation": "eq",
                "value": "failed"
              }
            ]
          ]
        }
      }
    }
  }
}
```

---

## 主要なフィールド

### 基本情報

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `id` | ✅ | 設定ID（UUID） |
| `type` | ✅ | 確認タイプ（任意の文字列） |
| `attributes` | ❌ | 属性情報 |
| `processes` | ✅ | プロセス定義 |

---

### Processesセクション

各プロセス（start, check-status, cancel等）を定義：

```json
{
  "processes": {
    "start": {...},
    "check-status": {...},
    "cancel": {...}
  }
}
```

**動的API生成**:
```
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/start
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/check-status
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/cancel
```

---

### Processオブジェクト

各プロセスは7つのフェーズで構成：

| フェーズ | 説明 | 必須 |
|---------|------|------|
| `request` | リクエストスキーマ定義 | ❌ |
| `pre_hook` | 実行前処理 | ❌ |
| `execution` | メイン処理（外部API呼び出し等） | ✅ |
| `post_hook` | 実行後処理 | ❌ |
| `transition` | ステータス遷移条件 | ❌ |
| `store` | 結果保存 | ❌ |
| `response` | レスポンスマッピング | ❌ |

---

## プロセス依存関係とシーケンス制御

複数のプロセスを順序立てて実行する必要がある場合、`dependencies`フィールドで実行順序を制御できます。

### dependencies フィールド

各プロセスに`dependencies`を設定することで、前提となるプロセスの完了を必須化し、リトライポリシーを制御できます。

**設定項目**:

| フィールド | 型 | 説明 | 必須 |
|---------|---|------|-----|
| `required_processes` | string[] | このプロセスを実行する前に完了が必要なプロセス名のリスト | ❌ |
| `allow_retry` | boolean | プロセスの再実行を許可するか (`true`: 許可, `false`: 不可) | ✅ |

### 設定例

```json
{
  "processes": {
    "apply": {
      "dependencies": {
        "required_processes": [],
        "allow_retry": false
      },
      "pre_hook": {
        "verifications": [
          {
            "type": "process_sequence"
          }
        ]
      },
      "execution": {
        "type": "no_action"
      }
    },
    "crm-registration": {
      "dependencies": {
        "required_processes": ["apply"],
        "allow_retry": false
      },
      "pre_hook": {
        "verifications": [
          {
            "type": "process_sequence"
          }
        ]
      },
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "${CRM_API_URL}/register",
          "method": "POST"
        }
      }
    },
    "request-ekyc": {
      "dependencies": {
        "required_processes": ["crm-registration"],
        "allow_retry": true
      },
      "pre_hook": {
        "verifications": [
          {
            "type": "process_sequence"
          }
        ]
      },
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "${EKYC_API_URL}/request",
          "method": "POST"
        }
      }
    }
  }
}
```

### 動作の仕組み

1. **依存関係チェック**: `required_processes`に指定されたプロセスがすべて正常完了している場合のみ実行可能
2. **リトライ制御**: `allow_retry: false`のプロセスは一度だけ実行可能。再実行しようとするとエラー
3. **process_sequence検証**: `pre_hook.verifications`に`process_sequence`タイプを追加することで依存関係を強制

### 実行順序の例

**証券口座開設の3段階プロセス**:

```bash
# 1. apply（基本情報入力）- 依存なし、リトライ不可
POST /{tenant-id}/v1/me/identity-verification/applications/account-opening/apply
→ 成功 (application_id: "abc-123" を取得)

# 2. crm-registration（CRM登録）- apply完了が必須、リトライ不可
POST /{tenant-id}/v1/me/identity-verification/applications/account-opening/abc-123/crm-registration
→ 成功

# 3. request-ekyc（eKYC実施）- crm-registration完了が必須、リトライ可
POST /{tenant-id}/v1/me/identity-verification/applications/account-opening/abc-123/request-ekyc
→ 成功

# 4. request-ekycの再実行（allow_retry: true のため成功）
POST /{tenant-id}/v1/me/identity-verification/applications/account-opening/abc-123/request-ekyc
→ 成功
```

### エラーケース

**依存関係違反**:

```bash
# applyを実行せずにcrm-registrationを実行
POST /{tenant-id}/v1/me/identity-verification/applications/account-opening/crm-registration
```

**エラーレスポンス**:
```json
{
  "error": "pre_hook_validation_failed",
  "error_messages": [
    "Process 'crm-registration' requires completion of: apply"
  ]
}
```

**リトライ禁止違反**:

```bash
# apply実行後、再度applyを実行
POST /{tenant-id}/v1/me/identity-verification/applications/account-opening/abc-123/apply
```

**エラーレスポンス**:
```json
{
  "error": "pre_hook_validation_failed",
  "error_messages": [
    "Process 'apply' does not allow retry and has already been executed"
  ]
}
```

### ユースケース

| シナリオ | 設定 |
|---------|------|
| **線形フロー** | apply → ekyc → callback という順序を強制 |
| **ワンタイム処理** | 基本情報入力は一度だけ実行 (`allow_retry: false`) |
| **リトライ可能処理** | 本人確認書類の撮影失敗時に再実行を許可 (`allow_retry: true`) |
| **複数依存** | プロセスDが「プロセスA」「プロセスB」「プロセスC」すべての完了を必要とする |

### 注意事項

1. **循環依存の禁止**: プロセスA → プロセスB → プロセスA のような循環依存は設定しない
2. **process_sequence検証必須**: 依存関係を強制するには`pre_hook.verifications`に`process_sequence`を追加
3. **リトライポリシー設計**: ビジネス要件に応じて`allow_retry`を適切に設定
4. **エラーハンドリング**: クライアント側で依存関係エラーを適切に処理

**参考**: [身元確認申込みガイド - プロセス依存関係とシーケンス制御](../../content_05_how-to/phase-4-extensions/identity-verification/02-application.md#プロセス依存関係とシーケンス制御)

---

### Request Schema

リクエストボディのバリデーション（JSONSchema）：

```json
{
  "request": {
    "schema": {
      "type": "object",
      "required": ["user_id", "document_type"],
      "properties": {
        "user_id": {
          "type": "string",
          "description": "ユーザーID"
        },
        "document_type": {
          "type": "string",
          "enum": ["passport", "drivers_license"],
          "description": "身分証明書タイプ"
        }
      }
    }
  }
}
```

**動作**: APIリクエスト受信時にJSONSchemaで検証。不正な場合は400エラー。

---

### Pre Hook（実行前処理）

**用途**: メイン処理（execution）の前にビジネスロジック検証や追加データ取得を実行

Pre Hookは2つのコンポーネントで構成されます：
1. **Verifications**: ビジネスロジック検証（プロセス依存関係、ユーザークレーム検証など）
2. **Additional Parameters**: 追加データ取得（外部API呼び出しなど）

実行順序は常に `Verifications → Additional Parameters` です。

---

#### Verifications（検証処理）

実行前に各種ビジネスロジック検証を行います。

**検証タイプ一覧**:

| type | 概要 | 必須パラメータ |
|------|------|--------------|
| `process_sequence` | プロセス依存関係とリトライ制御の検証 | なし |
| `user_claim` | リクエスト内容とユーザークレームの一致確認 | `details.verification_parameters` |
| `application_limitation` | 申込み可能数チェック（予定） | - |
| `duplicate_application` | 重複申請チェック（予定） | - |

##### process_sequence 検証

**用途**: プロセスの実行順序を強制、リトライを制御

```json
{
  "pre_hook": {
    "verifications": [
      {
        "type": "process_sequence"
      }
    ]
  },
  "dependencies": {
    "required_processes": ["apply"],
    "allow_retry": false
  }
}
```

**動作**:
- `required_processes`に指定されたプロセスがすべて完了しているかチェック
- `allow_retry: false`の場合、既に実行済みのプロセスの再実行を拒否

**エラー例**:
```json
{
  "error": "pre_hook_validation_failed",
  "error_messages": [
    "Process 'crm-registration' requires completion of: apply"
  ]
}
```

**参考**: [プロセス依存関係とシーケンス制御](#プロセス依存関係とシーケンス制御)

---

##### user_claim 検証

**用途**: リクエストデータとユーザー属性の一致を検証

```json
{
  "pre_hook": {
    "verifications": [
      {
        "type": "user_claim",
        "details": {
          "verification_parameters": [
            {
              "request_json_path": "$.mobile_phone_number",
              "user_claim_json_path": "phone_number"
            },
            {
              "request_json_path": "$.email",
              "user_claim_json_path": "email"
            }
          ]
        }
      }
    ]
  }
}
```

**パラメータ説明**:

| フィールド | 説明 |
|----------|------|
| `request_json_path` | リクエストから値を取得するJSONPath（例: `$.mobile_phone_number`） |
| `user_claim_json_path` | ユーザークレームから値を取得するキー（例: `phone_number`） |

**動作**:
- リクエストの`mobile_phone_number`とユーザーの`phone_number`を比較
- 一致しない場合はエラー

**エラー例**:
```json
{
  "error": "pre_hook_validation_failed",
  "error_messages": [
    "User claim verification failed: mobile_phone_number mismatch"
  ]
}
```

**ユースケース**:
- 口座開設時に登録済みメールアドレスとの一致を確認
- 電話番号認証済みユーザーのみ申込み可能にする

---

##### 複数検証の組み合わせ

複数の検証を設定順に実行できます：

```json
{
  "pre_hook": {
    "verifications": [
      {
        "type": "process_sequence"
      },
      {
        "type": "user_claim",
        "details": {
          "verification_parameters": [
            {
              "request_json_path": "$.email",
              "user_claim_json_path": "email"
            }
          ]
        }
      }
    ]
  }
}
```

**実行順序**: process_sequence検証 → user_claim検証

**いずれかが失敗した場合**: 処理は中断され、400エラーを返却

---

##### 条件付き実行（Conditional Execution）

Verificationsコンポーネントに`condition`フィールドを追加することで、実行を動的に制御できます。

**メリット**:
- パフォーマンス最適化（不要な検証をスキップ）
- リスクベースの認証制御
- 柔軟なビジネスロジック実装

**条件演算子一覧**:

| 演算子 | 説明 | 例 |
|-------|------|---|
| `eq` | 等しい | `{"operation": "eq", "path": "$.user.role", "value": "admin"}` |
| `ne` | 等しくない | `{"operation": "ne", "path": "$.user.status", "value": "suspended"}` |
| `gt` | より大きい | `{"operation": "gt", "path": "$.request_body.amount", "value": 1000}` |
| `gte` | 以上 | `{"operation": "gte", "path": "$.request_body.amount", "value": 100000}` |
| `lt` | より小さい | `{"operation": "lt", "path": "$.risk_score", "value": 50}` |
| `lte` | 以下 | `{"operation": "lte", "path": "$.retry_count", "value": 3}` |
| `in` | 含まれる | `{"operation": "in", "path": "$.user.country", "value": ["US", "EU", "JP"]}` |
| `nin` | 含まれない | `{"operation": "nin", "path": "$.user.status", "value": ["banned"]}` |
| `exists` | 存在する | `{"operation": "exists", "path": "$.user.verified"}` |
| `missing` | 存在しない | `{"operation": "missing", "path": "$.user.temp_flag"}` |
| `contains` | 文字列を含む | `{"operation": "contains", "path": "$.user.email", "value": "@company.com"}` |
| `regex` | 正規表現 | `{"operation": "regex", "path": "$.user.phone", "value": "^\\+81"}` |

**複合演算子**:

| 演算子 | 説明 | 使用例 |
|---------|------------|---------|
| `allOf` | すべての条件を満たす（AND） | `{"operation": "allOf", "value": [cond1, cond2]}` |
| `anyOf` | いずれかの条件を満たす（OR） | `{"operation": "anyOf", "value": [cond1, cond2]}` |

**例1: 高額取引時のみ追加検証**

```json
{
  "pre_hook": {
    "verifications": [
      {
        "type": "user_claim",
        "details": {
          "verification_parameters": [
            {
              "request_json_path": "$.identity_document_number",
              "user_claim_json_path": "id_number"
            }
          ]
        },
        "condition": {
          "operation": "gte",
          "path": "$.request_body.amount",
          "value": 100000
        }
      }
    ]
  }
}
```

**動作**: リクエストの`amount`が100,000以上の場合のみ、本人確認書類番号の検証を実行

**例2: 管理者のみ実行**

```json
{
  "pre_hook": {
    "verifications": [
      {
        "type": "enhanced_verification",
        "condition": {
          "operation": "eq",
          "path": "$.user.role",
          "value": "admin"
        }
      }
    ]
  }
}
```

**例3: 複合条件（Premium会員かつ18歳以上）**

```json
{
  "pre_hook": {
    "verifications": [
      {
        "type": "premium_verification",
        "condition": {
          "operation": "allOf",
          "value": [
            {
              "operation": "eq",
              "path": "$.user.tier",
              "value": "premium"
            },
            {
              "operation": "gte",
              "path": "$.user.age",
              "value": 18
            }
          ]
        }
      }
    ]
  }
}
```

**例4: 地域ベースの条件**

```json
{
  "pre_hook": {
    "verifications": [
      {
        "type": "geo_compliance_check",
        "condition": {
          "operation": "in",
          "path": "$.user.country",
          "value": ["US", "CA", "GB"]
        }
      }
    ]
  }
}
```

**コンテキストデータ**:

条件評価で利用可能なデータ：

```json
{
  "user": {
    "sub": "ユーザーID",
    "role": "admin",
    "tier": "premium",
    "age": 25,
    "country": "JP"
  },
  "application": {
    "id": "申込みID",
    "type": "申込み種別",
    "status": "申込みステータス"
  },
  "request_body": {
    "amount": 50000
  },
  "request_attributes": {
    "ip": "クライアントIP"
  }
}
```

**参考**: [身元確認申込みガイド - 条件付き実行](../../content_05_how-to/phase-4-extensions/identity-verification/02-application.md#条件付き実行機能-conditional-execution)

---

#### Additional Parameters

```json
{
  "pre_hook": {
    "additional_parameters": [
      {
        "type": "http_request",
        "details": {
          "url": "${EXTERNAL_API_URL}/get-user-info",
          "method": "POST",
          "note": "ユーザー情報を事前取得",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "client_credentials",
            "token_endpoint": "${AUTH_URL}/token",
            "client_id": "${CLIENT_ID}",
            "client_secret": "${CLIENT_SECRET}",
            "cache_enabled": true,
            "cache_ttl_seconds": 3600
          },
          "body_mapping_rules": [
            {
              "from": "$.user.external_user_id",
              "to": "user_id"
            }
          ]
        }
      }
    ]
  }
}
```

**重要なポイント**:
1. **実行順序**: pre_hook → execution → post_hook
2. **結果の保存**: `$.pre_hook_additional_parameters[0]`に保存される
3. **後続での参照**: executionやstoreで結果を参照可能

---

#### Pre Hookの結果を参照する例

```json
{
  "pre_hook": {
    "additional_parameters": [
      {
        "type": "http_request",
        "details": {
          "url": "${EXTERNAL_API_URL}/lookup",
          "method": "GET"
        }
      }
    ]
  },
  "execution": {
    "type": "http_request",
    "http_request": {
      "url": "${VERIFICATION_API_URL}/verify",
      "method": "POST",
      "body_mapping_rules": [
        {
          "from": "$.pre_hook_additional_parameters[0].response_body.verification_id",
          "to": "verification_id",
          "note": "Pre Hookの結果を使用"
        }
      ]
    }
  }
}
```

**JSONPath**:
- `$.pre_hook_additional_parameters[0]` - 1番目のPre Hook結果
- `$.pre_hook_additional_parameters[0].response_body` - レスポンスボディ
- `$.pre_hook_additional_parameters[0].response_headers` - レスポンスヘッダー

---

### Execution

外部サービスとの連携方法を定義：

```json
{
  "execution": {
    "type": "http_request",
    "http_request": {
      "url": "${VERIFICATION_API_URL}/verify",
      "method": "POST",
      "auth_type": "oauth2",
      "oauth_authorization": {
        "type": "client_credentials",
        "token_endpoint": "${AUTH_URL}/token",
        "client_id": "${CLIENT_ID}",
        "client_secret": "${CLIENT_SECRET}"
      },
      "body_mapping_rules": [
        {
          "from": "$.request_body.user_id",
          "to": "user_id"
        }
      ]
    }
  }
}
```

---

### Store（結果保存）

プロセスの実行結果をIdentity Verification Applicationに保存：

```json
{
  "store": {
    "application_details_mapping_rules": [
      {
        "from": "$.response_body.session_id",
        "to": "verification_session.id"
      },
      {
        "from": "$.response_body.url",
        "to": "verification_session.url"
      },
      {
        "from": "$.pre_hook_additional_parameters[0].response_body.user_status",
        "to": "user_info.status"
      }
    ]
  }
}
```

**用途**:
- 後続のプロセスで参照するデータを保存
- Identity Verification Application詳細として保存
- `$.application.processes.{process-name}`で参照可能

**参照例**（後続のcheck-statusプロセスで）:
```json
{
  "body_mapping_rules": [
    {
      "from": "$.application.processes.start.verification_session.id",
      "to": "session_id",
      "note": "startプロセスで保存したsession_idを使用"
    }
  ]
}
```

---

### Transition（ステータス遷移）

プロセス実行結果に基づいてステータスを遷移：

```json
{
  "transition": {
    "approved": {
      "any_of": [
        [
          {
            "path": "$.response_body.status",
            "type": "string",
            "operation": "eq",
            "value": "verified"
          }
        ]
      ]
    },
    "rejected": {
      "any_of": [
        [
          {
            "path": "$.response_body.status",
            "operation": "eq",
            "value": "failed"
          }
        ]
      ]
    }
  }
}
```

**ステータス**:
- `approved` - 確認成功
- `rejected` - 確認失敗
- `canceled` - キャンセル
- `pending` - 処理中（デフォルト）

---

### Callbackプロセス

外部サービスからの非同期コールバックを受け取るプロセスです。

#### 基本設定

```json
{
  "processes": {
    "callback-result": {
      "type": "callback",
      "request": {
        "basic_auth": {
          "username": "external_service",
          "password": "${CALLBACK_PASSWORD}"
        },
        "schema": {
          "type": "object",
          "required": ["application_id", "status"],
          "properties": {
            "application_id": { "type": "string" },
            "status": { "type": "string" },
            "verification": { "type": "object" },
            "claims": { "type": "object" }
          }
        }
      },
      "transition": {
        "approved": {
          "any_of": [[
            {
              "path": "$.request_body.status",
              "type": "string",
              "operation": "eq",
              "value": "approved"
            }
          ]]
        },
        "rejected": {
          "any_of": [[
            {
              "path": "$.request_body.status",
              "type": "string",
              "operation": "eq",
              "value": "rejected"
            }
          ]]
        }
      }
    }
  }
}
```

#### Callback API エンドポイント

**パターン1**: application_id をパスパラメータで特定

```
POST /{tenant-id}/internal/v1/identity-verification/callback/{type}/{application-id}/{process}
```

**例**:
```bash
POST /tenant-123/internal/v1/identity-verification/callback/investment-account-opening/abc-456/callback-result
Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=
Content-Type: application/json

{
  "status": "approved",
  "verification": { ... },
  "claims": { ... }
}
```

**パターン2**: application_id をボディから特定

```
POST /{tenant-id}/internal/v1/identity-verification/callback/{type}/{process}
```

**例**:
```bash
POST /tenant-123/internal/v1/identity-verification/callback/investment-account-opening/callback-result
Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=
Content-Type: application/json

{
  "application_id": "ext-app-456",
  "status": "approved",
  "verification": { ... },
  "claims": { ... }
}
```

#### Common設定との連携

`common.callback_application_id_param`で申請ID識別パラメータ名を指定します：

```json
{
  "common": {
    "callback_application_id_param": "application_id"
  },
  "processes": {
    "callback-result": {
      "type": "callback",
      "request": {
        "schema": {
          "type": "object",
          "required": ["application_id"],
          "properties": {
            "application_id": { "type": "string" }
          }
        }
      }
    }
  }
}
```

**動作**: リクエストボディの`application_id`フィールドで申請を特定（パターン2の場合）

#### Basic認証

コールバックAPIにはBasic認証を設定できます：

```json
{
  "request": {
    "basic_auth": {
      "username": "kyc_callback_user",
      "password": "secure_password_123"
    }
  }
}
```

**セキュリティ**:
- パスワードは環境変数から取得することを推奨（例: `${CALLBACK_PASSWORD}`）
- HTTPS通信必須
- IPホワイトリストとの併用を推奨

#### コールバック処理の流れ

```
外部サービス
  ↓
  POST /callback/{type}/{application-id}/{process}
  + Basic認証
  ↓
idp-server
  ↓ Request Schema検証
  ↓ Pre Hook（必要に応じて）
  ↓ Execution（通常はno_action）
  ↓ Transition（ステータス遷移判定）
  ↓ Store（結果保存）
  ↓ verified_claims生成（approved時）
  ↓
200 OK
```

#### ユースケース

| シナリオ | 説明 |
|---------|------|
| **審査結果通知** | eKYC審査完了後、外部サービスから結果を受信 |
| **ステータス更新** | 申込み処理の各段階で外部サービスから進捗を受信 |
| **verified_claims登録** | 本人確認完了後、検証済みクレームを自動登録 |

#### 注意事項

1. **認証必須**: Basic認証またはHMAC認証を必ず設定
2. **スキーマ検証**: 不正なリクエストを防ぐためスキーマ定義を厳密に
3. **冪等性**: 同じコールバックが複数回呼ばれても安全な設計にする
4. **タイムアウト**: 外部サービス側でリトライロジックを実装

---

### Result（結果設定）

#### verified_claims_mapping_rules

OIDC4IDA（OpenID Connect for Identity Assurance）準拠のverified_claims生成ルールです。

**設定例**:
```json
{
  "result": {
    "verified_claims_mapping_rules": [
      {
        "static_value": "jp_aml",
        "to": "verification.trust_framework"
      },
      {
        "from": "$.response_body.user.family_name",
        "to": "claims.family_name"
      },
      {
        "from": "$.response_body.user.given_name",
        "to": "claims.given_name"
      },
      {
        "from": "$.response_body.user.birthdate",
        "to": "claims.birthdate"
      }
    ]
  }
}
```

**フィールド構造**:
- `verification.*`: 身元確認のメタデータ
  - `trust_framework`: 信頼フレームワーク（例: `jp_aml`）
  - `evidence`: 証拠情報（例: 本人確認書類の種類）
- `claims.*`: 検証済みユーザー属性
  - `family_name`: 姓
  - `given_name`: 名
  - `birthdate`: 生年月日
  - `address`: 住所
  - など

**動作**:
1. eKYC等の外部サービスから取得した情報を`verified_claims`形式にマッピング
2. OIDCトークンに含めて発行
3. クライアントアプリケーションが信頼できる身元確認済み情報として利用可能

**使用シーン**:
- eKYC連携（本人確認書類のスキャン等）
- 身元確認済みIDの発行
- Verified Credentialsの生成

**参照仕様**:
- [OpenID Connect for Identity Assurance 1.0](https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html)

---

### Common（共通設定）

複数プロセスに共通する設定項目です。

**設定例**:
```json
{
  "common": {
    "external_service": "kyc-provider",
    "callback_application_id_param": "application_id"
  }
}
```

**フィールド説明**:
- `external_service`: 外部サービスの識別名（ログ・監査用）
- `callback_application_id_param`: コールバック時に使用する申請ID識別パラメータ名

**使用シーン**:
- 複数プロセスで共通する外部サービス名の定義
- コールバック処理での申請ID識別

---

## 実践例：証券口座開設フロー

複数プロセスを連携させた実際の申込みフローの完全な実装例です。

### フロー概要

1. **apply**: 基本情報入力（依存なし、リトライ不可）
2. **crm-registration**: CRM登録（applyが必須、リトライ不可）
3. **request-ekyc**: eKYC実施（crm-registrationが必須、リトライ可）
4. **callback-result**: 審査結果受信（コールバック）

### シーケンス図

```
ユーザー → apply → CRM登録 → eKYC → 外部審査 → callback → verified_claims反映
```

### 完全な設定

```json
{
  "id": "666bae10-bc0d-41ce-92b4-53359b2f8439",
  "type": "investment-account-opening",
  "common": {
    "external_service": "kyc-provider",
    "callback_application_id_param": "application_id"
  },
  "processes": {
    "apply": {
      "request": {
        "schema": {
          "type": "object",
          "required": ["family_name", "given_name", "email", "mobile_phone_number"],
          "properties": {
            "family_name": { "type": "string", "maxLength": 255 },
            "given_name": { "type": "string", "maxLength": 255 },
            "email": {
              "type": "string",
              "pattern": "^[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}$"
            },
            "mobile_phone_number": {
              "type": "string",
              "pattern": "^[0-9]{10,11}$"
            }
          }
        }
      },
      "pre_hook": {
        "verifications": [
          {
            "type": "process_sequence"
          },
          {
            "type": "user_claim",
            "details": {
              "verification_parameters": [
                {
                  "request_json_path": "$.mobile_phone_number",
                  "user_claim_json_path": "phone_number"
                },
                {
                  "request_json_path": "$.email",
                  "user_claim_json_path": "email"
                }
              ]
            }
          }
        ]
      },
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "${KYC_API_URL}/apply",
          "method": "POST",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "client_credentials",
            "token_endpoint": "${AUTH_URL}/token",
            "client_id": "${CLIENT_ID}",
            "client_secret": "${CLIENT_SECRET}"
          },
          "body_mapping_rules": [
            { "from": "$.request_body", "to": "*" }
          ]
        }
      },
      "dependencies": {
        "required_processes": [],
        "allow_retry": false
      },
      "transition": {
        "applied": {
          "any_of": [[
            {
              "path": "$.response_body.application_id",
              "type": "string",
              "operation": "exists"
            }
          ]]
        }
      },
      "store": {
        "application_details_mapping_rules": [
          { "from": "$.request_body", "to": "*" },
          { "from": "$.response_body.application_id", "to": "external_application_id" }
        ]
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.response_body", "to": "*" }
        ]
      }
    },
    "crm-registration": {
      "request": {
        "schema": {
          "type": "object",
          "required": ["crm_id"],
          "properties": {
            "crm_id": { "type": "string" }
          }
        }
      },
      "pre_hook": {
        "verifications": [
          { "type": "process_sequence" }
        ]
      },
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "${CRM_API_URL}/register",
          "method": "POST",
          "auth_type": "hmac_sha256",
          "hmac_authentication": {
            "api_key": "${CRM_API_KEY}",
            "secret": "${CRM_SECRET}",
            "signature_format": "HmacSHA256={signature}",
            "signing_fields": ["method", "path", "timestamp", "body"]
          },
          "body_mapping_rules": [
            {
              "from": "$.application.processes.apply.external_application_id",
              "to": "application_id"
            },
            { "from": "$.request_body", "to": "*" }
          ]
        }
      },
      "dependencies": {
        "required_processes": ["apply"],
        "allow_retry": false
      },
      "store": {
        "application_details_mapping_rules": [
          { "from": "$.request_body", "to": "crm_data" }
        ]
      }
    },
    "request-ekyc": {
      "request": {
        "schema": {
          "type": "object",
          "required": ["trust_framework", "evidence_document_type"],
          "properties": {
            "trust_framework": { "type": "string" },
            "evidence_document_type": { "type": "string" }
          }
        }
      },
      "pre_hook": {
        "verifications": [
          { "type": "process_sequence" }
        ]
      },
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "${KYC_API_URL}/{{external_application_id}}/request-ekyc",
          "method": "POST",
          "path_mapping_rules": [
            {
              "from": "$.application.processes.apply.external_application_id",
              "to": "external_application_id"
            }
          ],
          "body_mapping_rules": [
            { "from": "$.request_body", "to": "*" }
          ]
        }
      },
      "dependencies": {
        "required_processes": ["crm-registration"],
        "allow_retry": true
      },
      "store": {
        "application_details_mapping_rules": [
          { "from": "$.request_body", "to": "ekyc_data" }
        ]
      }
    },
    "callback-result": {
      "type": "callback",
      "request": {
        "basic_auth": {
          "username": "kyc_callback_user",
          "password": "${CALLBACK_PASSWORD}"
        },
        "schema": {
          "type": "object",
          "required": ["application_id", "status", "verification", "claims"],
          "properties": {
            "application_id": { "type": "string" },
            "status": { "type": "string" },
            "verification": { "type": "object" },
            "claims": { "type": "object" }
          }
        }
      },
      "transition": {
        "approved": {
          "any_of": [[
            {
              "path": "$.request_body.status",
              "type": "string",
              "operation": "eq",
              "value": "approved"
            }
          ]]
        },
        "rejected": {
          "any_of": [[
            {
              "path": "$.request_body.status",
              "type": "string",
              "operation": "eq",
              "value": "rejected"
            }
          ]]
        }
      }
    }
  },
  "result": {
    "verified_claims_mapping_rules": [
      { "static_value": "jp_aml", "to": "verification.trust_framework" },
      { "from": "$.request_body.claims.family_name", "to": "claims.family_name" },
      { "from": "$.request_body.claims.given_name", "to": "claims.given_name" },
      { "from": "$.request_body.claims.birthdate", "to": "claims.birthdate" },
      { "from": "$.request_body.claims.email", "to": "claims.email" },
      { "from": "$.request_body.verification.evidence[0].type", "to": "verification.evidence.0.type" },
      { "from": "$.request_body.verification.evidence[0].time", "to": "verification.evidence.0.time" }
    ],
    "source_details_mapping_rules": [
      { "from": "$.application.application_details", "to": "*" }
    ]
  }
}
```

### API実行シーケンス

#### 1. apply実行（基本情報入力）

```bash
POST /{tenant-id}/v1/me/identity-verification/applications/investment-account-opening/apply
Authorization: Bearer {user-access-token}
Content-Type: application/json

{
  "family_name": "山田",
  "given_name": "太郎",
  "email": "taro.yamada@example.com",
  "mobile_phone_number": "09012345678"
}

# Response
{
  "id": "abc-123",
  "application_id": "ext-app-456",
  "status": "applied"
}
```

#### 2. crm-registration実行（CRM登録）

```bash
POST /{tenant-id}/v1/me/identity-verification/applications/investment-account-opening/abc-123/crm-registration
Authorization: Bearer {user-access-token}
Content-Type: application/json

{
  "crm_id": "CRM-789"
}

# Response
{
  "id": "abc-123",
  "status": "applied"
}
```

#### 3. request-ekyc実行（eKYC開始）

```bash
POST /{tenant-id}/v1/me/identity-verification/applications/investment-account-opening/abc-123/request-ekyc
Authorization: Bearer {user-access-token}
Content-Type: application/json

{
  "trust_framework": "jp_aml",
  "evidence_document_type": "idcard"
}

# Response
{
  "id": "abc-123",
  "ekyc_session_url": "https://ekyc.example.com/session/xyz"
}
```

#### 4. 外部サービスからコールバック（審査完了）

```bash
POST /{tenant-id}/internal/v1/identity-verification/callback/investment-account-opening/abc-123/callback-result
Authorization: Basic {base64(username:password)}
Content-Type: application/json

{
  "application_id": "ext-app-456",
  "status": "approved",
  "verification": {
    "evidence": [
      {
        "type": "id_document",
        "time": "2025-01-15T10:00:00Z"
      }
    ]
  },
  "claims": {
    "family_name": "山田",
    "given_name": "太郎",
    "birthdate": "1990-01-01",
    "email": "taro.yamada@example.com"
  }
}

# Response
{
  "status": "approved"
}
```

### ポイント解説

1. **プロセス依存関係**:
   - `crm-registration`は`apply`完了が必須
   - `request-ekyc`は`crm-registration`完了が必須
   - `process_sequence`検証で強制

2. **リトライ制御**:
   - `apply`, `crm-registration`はワンタイム（`allow_retry: false`）
   - `request-ekyc`は失敗時に再実行可能（`allow_retry: true`）

3. **ユーザークレーム検証**:
   - `apply`時にリクエストのメールアドレスと電話番号がユーザー属性と一致するか検証

4. **プロセス間データ共有**:
   - `apply`で取得した`external_application_id`を`crm-registration`と`request-ekyc`で参照

5. **コールバック認証**:
   - Basic認証で外部サービスからのコールバックを保護

6. **verified_claims生成**:
   - `callback-result`のステータスが`approved`の場合、verified_claimsを自動生成

---

## Management APIで登録

### API エンドポイント

**組織レベルAPI**:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/identity-verification-configurations
```

### Identity Verification設定登録

```bash
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/identity-verification-configurations
Content-Type: application/json

{
  "id": "uuid",
  "type": "face-verification",
  "processes": {
    "start": {
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "${VERIFICATION_API_URL}/verify/start",
          "method": "POST"
        }
      }
    }
  }
}
```

---

## よくある問題と解決策

### 問題1: スコープ未定義

**エラー**:
```json
{
  "error": "invalid_scope",
  "error_description": "scope 'identity_verification_application' is not supported"
}
```

**原因**: Tenant設定の`scopes_supported`に未定義

**解決策**: `scopes_supported`に`identity_verification_application`を追加

---

### 問題2: プロセスシーケンスエラー

**エラー**:
```json
{
  "error": "pre_hook_validation_failed",
  "error_messages": [
    "Process 'crm-registration' requires completion of: apply"
  ]
}
```

**原因**: 依存プロセスが完了していない

**解決策**:
1. 依存プロセス（この例では`apply`）を先に実行
2. `dependencies.required_processes`の設定を確認
3. プロセス実行順序を見直す

**デバッグ方法**:
```bash
# 申込み一覧を取得してプロセス実行状況を確認
GET /{tenant-id}/v1/me/identity-verification/applications/{type}/{application-id}
```

---

### 問題3: リトライ禁止エラー

**エラー**:
```json
{
  "error": "pre_hook_validation_failed",
  "error_messages": [
    "Process 'apply' does not allow retry and has already been executed"
  ]
}
```

**原因**: `allow_retry: false`のプロセスを再実行しようとした

**解決策**:
1. 再実行が必要な場合は`allow_retry: true`に設定変更
2. 新しいapplicationを作成して最初からやり直す
3. ビジネス要件を見直してリトライポリシーを再検討

---

### 問題4: user_claim検証失敗

**エラー**:
```json
{
  "error": "pre_hook_validation_failed",
  "error_messages": [
    "User claim verification failed: email mismatch"
  ]
}
```

**原因**: リクエストデータとユーザー属性が一致しない

**解決策**:
1. UserInfo APIでユーザー属性を確認
   ```bash
   GET /{tenant-id}/v1/me/userinfo
   Authorization: Bearer {access-token}
   ```
2. リクエストデータを修正
3. `verification_parameters`の`request_json_path`と`user_claim_json_path`を確認

---

### 問題5: transition条件が満たされない

**問題**: ステータスが`approved`に遷移しない

**原因**:
- JSONPathが間違っている
- 条件値が外部APIレスポンスと一致しない
- `type`フィールドの型が間違っている

**解決策**:
1. 外部APIのレスポンスをログで確認
2. JSONPathをテスト
   ```json
   {
     "transition": {
       "approved": {
         "any_of": [[
           {
             "path": "$.response_body.status",
             "type": "string",
             "operation": "eq",
             "value": "success"
           }
         ]]
       }
     }
   }
   ```
3. `response_body`の構造を確認
4. 条件のデバッグには`store`で値を保存して確認

---

### 問題6: Callback認証失敗

**エラー**:
```
401 Unauthorized
```

**原因**: Basic認証のcredentialsが間違っている

**解決策**:
1. `basic_auth`の設定を確認
2. Base64エンコーディングを確認
   ```bash
   echo -n "username:password" | base64
   ```
3. 外部サービス側の設定と一致しているか確認

---

### 問題7: JSONPath参照エラー

**問題**: `$.application.processes.apply.external_application_id`が参照できない

**原因**:
- `apply`プロセスで`store`していない
- プロセス名が間違っている
- JSONPathの構造が間違っている

**解決策**:
1. `apply`プロセスの`store`設定を確認
   ```json
   {
     "store": {
       "application_details_mapping_rules": [
         {
           "from": "$.response_body.application_id",
           "to": "external_application_id"
         }
       ]
     }
   }
   ```
2. 申込み詳細APIで保存されているデータを確認
   ```bash
   GET /{tenant-id}/v1/me/identity-verification/applications/{type}/{application-id}
   ```

---

### 問題8: 外部API連携失敗

**エラー**: Execution phase failed

**原因**:
- OAuth2トークン取得失敗
- HMAC署名が間違っている
- URLパラメータのマッピングミス
- タイムアウト

**解決策**:
1. **OAuth2の場合**:
   - `token_endpoint`が正しいか確認
   - `client_id`と`client_secret`を確認
   - スコープが正しいか確認

2. **HMAC認証の場合**:
   - `api_key`と`secret`を確認
   - `signing_fields`の順序を確認
   - タイムスタンプの生成を確認

3. **URLマッピングの場合**:
   - `path_mapping_rules`の`from`パスを確認
   - テンプレート変数（例: `{{external_application_id}}`）が正しく置換されているか確認

4. **タイムアウトの場合**:
   - 外部APIのレスポンス時間を確認
   - ネットワーク接続を確認

---

### 問題9: verified_claims生成失敗

**問題**: `approved`になってもverified_claimsが生成されない

**原因**:
- `result.verified_claims_mapping_rules`が未定義
- コールバックデータの構造が想定と異なる

**解決策**:
1. `result`セクションの設定を確認
2. コールバックデータの構造を確認
3. マッピングルールのJSONPathを修正
   ```json
   {
     "result": {
       "verified_claims_mapping_rules": [
         { "from": "$.request_body.claims.family_name", "to": "claims.family_name" }
       ]
     }
   }
   ```

---

### デバッグのベストプラクティス

1. **ログの確認**:
   - アプリケーションログでエラー詳細を確認
   - 外部APIのリクエスト/レスポンスをログに出力

2. **段階的なテスト**:
   - 最初は最小構成で動作確認
   - Pre Hook、Post Hook、Transitionを段階的に追加

3. **Storeの活用**:
   - デバッグ用に中間データを`store`に保存
   - 申込み詳細APIで保存データを確認

4. **外部ツールの活用**:
   - JSONPath評価: https://jsonpath.com/
   - Base64エンコード/デコード
   - JWT デコーダー

---

## 次のステップ

✅ Identity Verification設定を理解した！

### 次に読むべきドキュメント

1. [Identity Verification実装ガイド](../03-application-plane/07-identity-verification.md)
2. [HttpRequestExecutor実装ガイド](../04-implementation-guides/impl-16-http-request-executor.md)

---

**最終更新**: 2025-10-13

---

## 📊 初学者向けドキュメント品質レビュー

**レビュー日**: 2025-01-15
**レビュー対象**: 初学者（idp-server開発経験なし、Java/Spring Boot基礎知識あり）

### ✅ 良い点

1. **7フェーズ構造の明示**: Process構造を表形式で明確に説明
2. **Pre/Post Hookの説明**: 実行順序と結果参照方法が詳細
3. **Store機能の説明**: プロセス間でのデータ受け渡し方法が明確
4. **Transition条件**: ステータス遷移の仕組みが具体的
5. **JSONPath活用**: pre_hook結果の参照方法が詳細
6. **動的API生成**: typeとprocess名でAPIが生成される仕組みを説明

### ⚠️ 改善推奨事項

- [ ] **Identity Verificationの概念説明**（重要度: 高）
  - eKYC/本人確認の業務的な意味
  - 7フェーズ処理の全体像図
  - なぜこのような複雑な構造が必要か

- [ ] **最小構成の例**（重要度: 高）
  - 最もシンプルなstart processのみの例
  - Hooksやtransition不使用の基本例

- [ ] **7フェーズの流れ図**（重要度: 高）
  - request → pre_hook → execution → post_hook → transition → store → response
  - 各フェーズでのデータフロー

- [ ] **動作確認手順**（重要度: 高）
  - Identity Verification APIの実行テスト方法
  - ステータス遷移の確認方法

- [ ] **前提知識の明記**（重要度: 中）
  - JSONPath、JSONSchema、Mapping Functions
  - HttpRequestExecutorの理解が前提

- [ ] **実践的なシナリオ**（重要度: 中）
  - 「顔認証のみ」シンプル例
  - 「顔認証 + 身分証確認」複合例
  - 「銀行口座確認」の完全例

- [ ] **エラーハンドリング**（重要度: 中）
  - 外部API失敗時の動作
  - リトライ設定の説明

### 💡 追加推奨コンテンツ

1. **Identity Verification全体フロー図**:
   ```
   申込み開始(start) → 外部サービス実行 →
   ステータス確認(check-status) → 承認/却下 →
   Claims反映
   ```

2. **7フェーズの詳細図**:
   ```
   [Request] → [Pre Hook] → [Execution] →
   [Post Hook] → [Transition] → [Store] → [Response]
   ```

3. **processesとAPIの対応表**:
   ```
   | process名 | 生成されるAPI | 用途 |
   |-----------|--------------|------|
   | start | POST .../start | 確認開始 |
   | check-status | POST .../check-status | 状態確認 |
   | cancel | POST .../cancel | キャンセル |
   ```

4. **Store機能の活用パターン**:
   - startで保存したsession_idをcheck-statusで参照
   - 複数processでのデータ共有

5. **トラブルシューティング**:
   - transition条件が満たされない場合
   - store mapping失敗時の確認

### 📈 総合評価

- **理解しやすさ**: ⭐⭐⭐☆☆ (3/5) - 7フェーズ構造が複雑
- **実用性**: ⭐⭐⭐⭐⭐ (5/5) - Pre/Post Hookの詳細な例が実用的
- **完全性**: ⭐⭐⭐⭐⭐ (5/5) - 全7フェーズを網羅
- **初学者適合度**: ⭐⭐☆☆☆ (2/5) - 高度な機能で初学者には難しい

### 🎯 推奨される学習パス

**このドキュメントの位置づけ**: 上級（基本機能習得後）

**推奨順序**:
1. [HttpRequestExecutor](../04-implementation-guides/impl-16-http-request-executor.md) - HTTP通信基礎
2. [Mapping Functions](../04-implementation-guides/impl-20-mapping-functions.md) - マッピング基礎
3. [外部サービス連携](../04-implementation-guides/impl-17-external-integration.md) - 統合パターン
4. **このドキュメント** - Identity Verification設定
5. [Identity Verification実装ガイド](../03-application-plane/07-identity-verification.md) - 実装詳細

### 📝 具体的改善案（優先度順）

#### 1. 7フェーズ処理の全体図（最優先）

```markdown
## 7フェーズ処理の仕組み

Identity Verificationの各processは、以下の7フェーズで処理されます：

\`\`\`
┌────────────────────────────────────────────────────┐
│ Phase 1: Request                                   │
│  - JSONSchemaでリクエスト検証                      │
│  - 不正な場合は400エラー                           │
└─────────────┬──────────────────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────┐
│ Phase 2: Pre Hook                                  │
│  - 外部API呼び出し（additional_parameters）        │
│  - 結果を$.pre_hook_additional_parameters[0]に保存 │
└─────────────┬──────────────────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────┐
│ Phase 3: Execution                                 │
│  - メイン処理実行（http_request）                  │
│  - Pre Hook結果を参照可能                          │
└─────────────┬──────────────────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────┐
│ Phase 4: Post Hook                                 │
│  - 実行後の追加処理                                │
│  - Execution結果を参照可能                         │
└─────────────┬──────────────────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────┐
│ Phase 5: Transition                                │
│  - レスポンスに基づいてステータス判定              │
│  - approved/rejected/canceledに遷移                │
└─────────────┬──────────────────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────┐
│ Phase 6: Store                                     │
│  - application_detailsに結果保存                   │
│  - 後続processで$.application.processes...で参照   │
└─────────────┬──────────────────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────┐
│ Phase 7: Response                                  │
│  - クライアントへのレスポンス生成                  │
│  - body_mapping_rulesでマッピング                  │
└────────────────────────────────────────────────────┘
\`\`\`
```

#### 2. 最小構成から段階的に

```markdown
## 段階的な設定例

### ステップ1: 最小構成（startのみ）

**シナリオ**: 外部APIを1回だけ呼び出す

\`\`\`json
{
  "id": "simple-verification",
  "type": "face-check",
  "processes": {
    "start": {
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "https://verify-api.example.com/check",
          "method": "POST",
          "body_mapping_rules": [
            {"from": "$.request_body.user_id", "to": "user_id"}
          ]
        }
      },
      "response": {
        "body_mapping_rules": [
          {"from": "$.response_body.result", "to": "verification_result"}
        ]
      }
    }
  }
}
\`\`\`

**使用されるフェーズ**: Execution + Response のみ

### ステップ2: ステータス遷移追加

**シナリオ**: レスポンスに基づいてapproved/rejectedを判定

（既存のtransition例を参照）

### ステップ3: Pre Hook追加

**シナリオ**: メイン処理前にユーザー情報を事前取得

（既存のpre_hook例を参照）
```
