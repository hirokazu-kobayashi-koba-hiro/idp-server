# Security Event Hook設定ガイド

## このドキュメントの目的

Security Event Hook（セキュリティイベント通知）の設定方法を理解します。

### 所要時間
⏱️ **約15分**

---

## Security Event Hookとは

**Security Event Hook**はセキュリティイベント（認証成功/失敗、トークン発行等）を外部サービスに通知する機能です。

**通知先**:
- Webhook（汎用HTTP通知）
- Email
- SSF（Shared Signals Framework）
- チャットサービス（Webhook経由）
- SIEM（Security Information and Event Management）

---

## 設定ファイル構造

### security-event-hook/webhook.json

```json
{
  "id": "uuid",
  "type": "webhook",
  "execution_order": 1,
  "triggers": [
    "password_failure",
    "user_locked",
    "oauth_authorize",
    "token_request_success"
  ],
  "attributes": {
    "label": "Webhook通知",
    "description": "セキュリティイベントをWebhookで通知"
  },
  "endpoint": "${WEBHOOK_URL}/events",
  "auth_type": "bearer",
  "auth_token": "${WEBHOOK_AUTH_TOKEN}",
  "enabled": true
}
```

---

### security-event-hook/ssf.json（SSF連携）

```json
{
  "id": "uuid",
  "type": "SSF",
  "execution_order": 1,
  "triggers": [
    "identity_verification_application_approved",
    "fido_uaf_deregistration_success",
    "user_delete"
  ],
  "attributes": {
    "label": "Security Event Framework",
    "spec": "https://openid.net/specs/openid-sharedsignals-framework-1_0.html"
  },
  "metadata": {
    "issuer": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}",
    "spec_version": "1_0",
    "jwks_uri": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/ssf/jwks",
    "delivery_methods_supported": [
      "urn:ietf:rfc:8935"
    ]
  },
  "events": {
    "identity_verification_application_approved": {
      "execution": {
        "type": "ssf",
        "details": {
          "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/account-enabled",
          "kid": "ssf-key-id",
          "url": "${SSF_RECEIVER_URL}/events/receive",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "client_credentials",
            "token_endpoint": "${AUTH_URL}/token",
            "client_id": "${CLIENT_ID}",
            "client_secret": "${CLIENT_SECRET}"
          }
        }
      }
    }
  },
  "enabled": true
}
```

---

#### SSF Eventsセクションの詳細

**構造**: イベントタイプごとに異なるSSF設定を定義できます。

```
events: {
  "{event-type-1}": { 個別設定 },
  "{event-type-2}": { 個別設定 },
  ...
}
```

**イベントタイプ別設定例**:

```json
{
  "events": {
    "user_delete": {
      "execution": {
        "type": "ssf",
        "details": {
          "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/account-disabled",
          "kid": "ssf-key-id",
          "url": "${SSF_RECEIVER_URL_A}/user-events",
          "security_event_token_additional_payload_mapping_rules": [
            {
              "from": "$.user.sub",
              "to": "ex_sub"
            }
          ]
        }
      }
    },
    "identity_verification_application_approved": {
      "execution": {
        "type": "ssf",
        "details": {
          "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/account-enabled",
          "kid": "ssf-key-id",
          "url": "${SSF_RECEIVER_URL_B}/verification-events",
          "auth_type": "oauth2",
          "oauth_authorization": {
            "type": "client_credentials",
            "token_endpoint": "${AUTH_URL}/token",
            "client_id": "${CLIENT_ID}",
            "client_secret": "${CLIENT_SECRET}",
            "cache_enabled": true,
            "cache_ttl_seconds": 3600
          }
        }
      }
    }
  }
}
```

**重要なポイント**:
1. イベントタイプごとに**異なるURL**を設定可能
2. イベントタイプごとに**異なる認証設定**を指定可能
3. `security_event_token_additional_payload_mapping_rules`でペイロードカスタマイズ

---

#### OAuth認証のキャッシュ設定

SSFでもOAuth 2.0認証のキャッシュを利用できます：

```json
{
  "oauth_authorization": {
    "type": "client_credentials",
    "token_endpoint": "${AUTH_URL}/token",
    "client_id": "${CLIENT_ID}",
    "client_secret": "${CLIENT_SECRET}",
    "cache_enabled": true,
    "cache_ttl_seconds": 3600,
    "cache_buffer_seconds": 10
  }
}
```

**キャッシュ設定**:
| フィールド | 説明 | デフォルト |
|-----------|------|----------|
| `cache_enabled` | キャッシュ有効化 | `false` |
| `cache_ttl_seconds` | キャッシュ有効期限（秒） | - |
| `cache_buffer_seconds` | 期限切れN秒前に再取得 | `0` |

**メリット**:
- Token Endpoint呼び出し回数を削減
- SSF送信のレイテンシを低減
- 大量のイベント発生時のパフォーマンス向上

---

## 主要なフィールド

### 基本情報

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `id` | ✅ | Hook設定ID（UUID） |
| `type` | ✅ | Hookタイプ | `webhook` / `SSF` / `email` |
| `execution_order` | ❌ | 実行順序（複数Hook時） | デフォルト: 1 |
| `triggers` | ✅ | トリガーとなるイベントタイプ |
| `enabled` | ✅ | 有効/無効 | `true` / `false` |

---

### Triggers（イベントタイプ）

通知対象のイベントを指定：

```json
{
  "triggers": [
    "password_success",
    "password_failure",
    "user_locked",
    "oauth_authorize",
    "token_request_success"
  ]
}
```

**主要なイベントタイプ**:
- `password_success` / `password_failure` - パスワード認証
- `oauth_authorize` - Authorization Code発行
- `token_request_success` - トークン発行
- `user_locked` - アカウントロック
- `user_delete` - ユーザー削除

**完全なリスト**: [DefaultSecurityEventType.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/event/DefaultSecurityEventType.java)

---

### Webhook設定

汎用HTTP通知：

```json
{
  "type": "webhook",
  "endpoint": "https://webhook.example.com/events",
  "auth_type": "bearer",
  "auth_token": "${WEBHOOK_AUTH_TOKEN}"
}
```

**認証タイプ**:
- `bearer` - Bearer Token
- `oauth2` - OAuth 2.0 Client Credentials
- `none` - 認証なし

---

### SSF設定

Shared Signals Framework準拠の通知。

**重要**: SSFでは`events`セクションで**イベントタイプごとに個別設定**が可能。

```json
{
  "type": "SSF",
  "metadata": {
    "issuer": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}",
    "spec_version": "1_0",
    "jwks_uri": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/ssf/jwks"
  },
  "events": {
    "user_delete": {
      "execution": {
        "type": "ssf",
        "details": {
          "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/account-disabled",
          "kid": "ssf-key-id",
          "url": "${SSF_RECEIVER_URL}/events/receive"
        }
      }
    }
  }
}
```

**詳細**: [Security Event Hooks実装ガイド](../04-implementation-guides/impl-15-security-event-hooks.md)

#### SSF eventsセクション構造

`events`セクションでは、イベントタイプごとに個別の実行設定を定義できます。

**フィールド説明**:

| フィールド | 説明 |
|-----------|------|
| `events` | イベント名をキーとした実行設定のマップ |
| `events.{event_name}.execution.type` | `"ssf"` 固定 |
| `events.{event_name}.execution.details.security_event_type_identifier` | SSFイベントタイプURI（RISC準拠） |
| `events.{event_name}.execution.details.kid` | Security Event Token（SET）の署名鍵ID |
| `events.{event_name}.execution.details.url` | SET送信先URL |
| `events.{event_name}.execution.details.oauth_authorization` | OAuth 2.0認証設定（オプション） |
| `metadata.spec_version` | SSF仕様バージョン（`"1_0"`） |
| `metadata.jwks_uri` | SSF用のJWKS URI |
| `metadata.stream_configuration` | SSFストリーム設定 |
| `metadata.stream_configuration.aud` | 受信側のクライアントID一覧 |

**完全な設定例**:
```json
{
  "type": "SSF",
  "triggers": ["identity_verification_approved", "user_locked"],
  "metadata": {
    "issuer": "https://idp.example.com/tenant-id",
    "spec_version": "1_0",
    "jwks_uri": "https://idp.example.com/tenant-id/v1/ssf/jwks",
    "jwks": { "keys": [...] },
    "delivery_methods_supported": ["https://schemas.openid.net/secevent/risc/delivery-method/push"],
    "stream_configuration": {
      "aud": ["client-id-1", "client-id-2"]
    }
  },
  "events": {
    "identity_verification_approved": {
      "execution": {
        "type": "ssf",
        "details": {
          "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/account-enabled",
          "kid": "ssf-signing-key",
          "url": "https://receiver.example.com/events",
          "oauth_authorization": {
            "type": "client_credentials",
            "token_endpoint": "https://receiver.example.com/oauth/token",
            "client_id": "idp-client",
            "client_secret": "secret",
            "cache_enabled": true
          }
        }
      }
    },
    "user_locked": {
      "execution": {
        "type": "ssf",
        "details": {
          "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/account-disabled",
          "kid": "ssf-signing-key",
          "url": "https://receiver.example.com/events"
        }
      }
    }
  },
  "security_event_token_additional_payload_mapping_rules": [
    {
      "from": "$.event.user.sub",
      "to": "reason"
    }
  ]
}
```

**動作フロー**:
1. `triggers`で指定されたイベントが発生
2. 対応する`events.{event_name}`の実行設定を取得
3. Security Event Token（SET）を生成
4. `kid`で指定された鍵で署名
5. `url`で指定されたエンドポイントへHTTP POST送信

**参照仕様**:
- [OpenID Shared Signals Framework 1.0](https://openid.net/specs/openid-sharedsignals-framework-1_0.html)
- [OpenID RISC Event Types](https://openid.net/specs/openid-risc-event-types-1_0.html)

---

## Management APIで登録

### API エンドポイント

**組織レベルAPI**:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/security-event-hook-configurations
```

### Security Event Hook登録

```bash
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/security-event-hook-configurations
Content-Type: application/json

{
  "id": "uuid",
  "type": "webhook",
  "triggers": ["password_failure", "user_locked"],
  "endpoint": "https://webhook.example.com/events",
  "auth_type": "bearer",
  "auth_token": "secret-token",
  "enabled": true
}
```

---

## リトライ設定

Hook送信失敗時の自動リトライ：

```json
{
  "retry_configuration": {
    "max_retries": 3,
    "retryable_status_codes": [502, 503, 504],
    "backoff_delays": ["PT1S", "PT2S", "PT4S"]
  }
}
```

**動作**:
- 1回目失敗 → 1秒後にリトライ
- 2回目失敗 → 2秒後にリトライ
- 3回目失敗 → 4秒後にリトライ
- 最終失敗 → `security_event_hook_results`テーブルに記録

---

## よくある設定ミス

### ミス1: イベントタイプの誤り

**問題**: イベントが通知されない

**原因**: `triggers`に存在しないイベントタイプを指定

**解決策**: `DefaultSecurityEventType`から正しいイベントタイプを選択

### ミス2: 認証エラー

**エラー**: Hook送信が401 Unauthorizedで失敗

**原因**: `auth_token`が無効

**解決策**: 有効なトークンを設定

---

## 次のステップ

✅ Security Event Hook設定を理解した！

### 次に読むべきドキュメント

1. [Security Event Hooks実装ガイド](../04-implementation-guides/impl-15-security-event-hooks.md)
2. [Events実装ガイド](../03-application-plane/09-events.md)

---

**最終更新**: 2025-10-13

---

## 📊 初学者向けドキュメント品質レビュー

**レビュー日**: 2025-01-15
**レビュー対象**: 初学者（idp-server開発経験なし、Java/Spring Boot基礎知識あり）

### ✅ 良い点

1. **目的・所要時間の明示**: 冒頭で目的と所要時間（15分）が明確
2. **具体的なJSON例**: コピペ可能な完全な設定例が豊富
3. **段階的説明**: WebhookとSSFで難易度を分けて説明
4. **エラー対処**: よくある設定ミスのセクションが実用的
5. **次のステップ**: 関連ドキュメントへの明確な誘導
6. **表形式の整理**: フィールド説明が表で見やすく整理

### ⚠️ 改善推奨事項

- [ ] **アーキテクチャ図の追加**（重要度: 高）
  - Security Event Hookがどこで動作するかの全体像図
  - イベント発生 → Hook処理 → 外部通知の流れ図

- [ ] **用語説明の補強**（重要度: 高）
  - 「SSF（Shared Signals Framework）」の簡潔な説明
  - 「RISC（Risk Incident Sharing and Coordination）」への言及
  - 「kid（Key ID）」の役割説明

- [ ] **前提知識の明記**（重要度: 中）
  - Security Event Hooksを理解するために必要な前提知識
  - OAuth 2.0、JWT、Webhookの基礎知識が必要であることを明示

- [ ] **実際の動作確認手順**（重要度: 高）
  - 設定後にイベントが正しく送信されるかテストする方法
  - 例: パスワード失敗を意図的に発生させて通知確認

- [ ] **デフォルト値の明記**（重要度: 中）
  - `execution_order`のデフォルト値は記載あり
  - `retry_configuration`が省略された場合の動作を明記

- [ ] **環境変数の説明**（重要度: 中）
  - `${WEBHOOK_URL}`などの環境変数の設定方法
  - どこで定義するのか（.env, docker-compose.yml等）

- [ ] **複数Hook実行順序の詳細**（重要度: 低）
  - `execution_order`が同じ場合の動作
  - 並列実行 vs 直列実行の説明

### 💡 追加推奨コンテンツ

1. **簡単な例から始める**: 最初に最もシンプルなWebhook例（認証なし）から開始
2. **トラブルシューティングの拡充**:
   - Hook送信履歴の確認方法
   - `security_event_hook_results`テーブルの確認SQL例
3. **セキュリティベストプラクティス**:
   - auth_tokenの安全な管理方法
   - HTTPS必須の明記
4. **パフォーマンス考慮事項**:
   - 大量イベント発生時の影響
   - 非同期処理の説明
5. **実用例の追加**:
   - SIEMツール連携の具体例
   - チャットサービス通知の実装例

### 📈 総合評価

- **理解しやすさ**: ⭐⭐⭐⭐☆ (4/5) - 構造は良いが図があればさらに理解しやすい
- **実用性**: ⭐⭐⭐⭐⭐ (5/5) - コピペ可能な例が豊富で即座に使える
- **完全性**: ⭐⭐⭐⭐☆ (4/5) - 主要な内容は網羅、細かい動作仕様が一部不明
- **初学者適合度**: ⭐⭐⭐⭐☆ (4/5) - 専門用語の説明強化で5点に

### 🎯 推奨される学習パス

**このドキュメントの位置づけ**: 中級（基本的な設定理解後）

**推奨順序**:
1. [Events実装ガイド](../03-application-plane/09-events.md) - Security Eventの基礎理解
2. **このドキュメント** - Security Event Hook設定
3. [Security Event Hooks実装ガイド](../04-implementation-guides/impl-15-security-event-hooks.md) - 実装の詳細
4. [HTTP Request Executor](../04-implementation-guides/impl-16-http-request-executor.md) - リトライ機構の理解

### 📝 具体的改善案（優先度順）

#### 1. アーキテクチャ図の追加（最優先）

```
┌─────────────────┐
│ Application     │
│ (認証処理等)    │
└────────┬────────┘
         │ イベント発生
         ▼
┌─────────────────┐
│ Security Event  │
│ Hook System     │
├─────────────────┤
│ - Event検出     │
│ - Hook実行判定  │
│ - リトライ制御  │
└────────┬────────┘
         │ HTTP POST
         ▼
┌─────────────────┐
│ 外部サービス    │
│ - Webhook       │
│ - SSF Receiver  │
│ - SIEM          │
└─────────────────┘
```

#### 2. 動作確認チェックリスト

```markdown
## 設定確認チェックリスト

- [ ] Security Event Hook設定をManagement APIで登録完了
- [ ] `enabled: true`が設定されている
- [ ] `triggers`に対象イベントが含まれている
- [ ] エンドポイントURLが正しく設定されている
- [ ] 認証情報が正しく設定されている
- [ ] テストイベントを発生させて通知確認
- [ ] `security_event_hook_results`で送信履歴確認
```

#### 3. 最小構成の例（最初に提示）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "webhook",
  "triggers": ["password_failure"],
  "endpoint": "https://webhook.site/unique-url",
  "auth_type": "none",
  "enabled": true
}
```

**説明**: webhook.siteで即座にテスト可能な最小構成
