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

**用途**: メイン処理（execution）の前に追加のAPIを呼び出して、その結果を利用

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

## よくある設定ミス

### ミス1: スコープ未定義

**エラー**:
```json
{
  "error": "invalid_scope",
  "error_description": "scope 'identity_verification_application' is not supported"
}
```

**原因**: Tenant設定の`scopes_supported`に未定義

**解決策**: `scopes_supported`に`identity_verification_application`を追加

### ミス2: transition条件の誤り

**問題**: ステータスが遷移しない

**原因**: JSONPathや条件値が間違っている

**解決策**: 外部APIのレスポンスを確認してパスを修正

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
