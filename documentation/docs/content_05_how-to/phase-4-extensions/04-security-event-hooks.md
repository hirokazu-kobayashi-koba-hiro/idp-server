# セキュリティイベントフック設定ガイド

## このドキュメントの目的

**セキュリティイベント**（ログイン成功、失敗、本人確認完了等）を**外部サービスに通知**する設定ができるようになることが目標です。

### 所要時間
⏱️ **約20分**

### 前提条件
- 管理者トークンを取得済み
- 通知先サービス（Email、Slack、Webhook等）の準備
- 組織ID（organization-id）を取得済み
- テナントの `security_event_log_config` で必要な機能が有効化されていること（下記参照）

### テナント設定（security_event_log_config）

セキュリティイベントフックを動作させるには、テナント設定で以下を有効にしてください（**デフォルトは全て `false`**）:

```json
{
  "tenant": {
    "security_event_log_config": {
      "persistence_enabled": true,
      "statistics_enabled": true,
      "format": "structured_json",
      "include_event_detail": true
    }
  }
}
```

| フィールド | デフォルト | 説明 |
|-----------|:---------:|------|
| `persistence_enabled` | `false` | `true` でイベントをDBに永続化。Management API でイベント照会するために必須 |
| `statistics_enabled` | `false` | `true` で統計データ（DAU/MAU/YAU、イベントカウント）を記録。統計APIを使用する場合に必須 |

> **注意**: これらの設定が `false` のままだと、フック自体は実行されますが、イベントの永続化や統計データは記録されません。

### Management API URL

**組織レベルAPI**（このドキュメントでの表記）:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/security-event-hook-configurations
```

**注意**: システムレベルAPIとの違い
- **組織レベル**: `POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/security-event-hook-configurations` ← このドキュメント
- **システムレベル**: `POST /v1/management/tenants/{tenant-id}/security-event-hook-configurations` ← 管理者のみ

通常の運用では組織レベルAPIを使用してください。

---

## セキュリティイベントフックとは

**重要なセキュリティイベント**が発生した時に、**自動的に外部サービスに通知**する仕組みです。

```
セキュリティイベント発生
  ↓
idp-server: イベント検出
  ├─ ログイン成功
  ├─ ログイン失敗（不正アクセス試行）
  ├─ 本人確認完了
  ├─ アカウントロック
  └─ パスワード変更
  ↓
セキュリティイベントフック
  ├─ Email送信
  ├─ Slack通知
  ├─ Webhook呼び出し
  └─ SSF（Shared Signals Framework）送信
```

**用途**:
- ✅ セキュリティ監視（不正アクセス検出）
- ✅ ユーザー通知（ログイン通知等）
- ✅ 外部システム連携（SIEM、ログ分析等）

---

## Level 1: Email通知の設定（10分）

### Step 1: Email通知フックを作成

```bash
IDP_SERVER_URL=http://localhost:8080

curl -X POST "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hook-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "id": "'$(uuidgen)'",
    "type": "EMAIL",
    "enabled": true,
    "triggers": [
      "login_success",
      "password_failure",
      "password_change_success"
    ],
    "events": {
      "default": {
        "execution": {
          "function": "http_request",
          "http_request": {
            "url": "https://notification-api.example.com/emails/send",
            "method": "POST",
            "auth_type": "none",
            "body_mapping_rules": [
              { "static_value": "security-team@example.com", "to": "to" },
              { "from": "$.type", "to": "subject" },
              { "from": "$.user.sub", "to": "user_id" }
            ]
          }
        }
      }
    }
  }'
```

**設定内容**:
- `type: "EMAIL"` - Email通知
- `triggers` - 通知するイベント一覧
- `events` - 実行設定（外部Email API呼び出し）

---

### Step 2: 動作確認

ユーザーがログインすると、自動的にEmailが送信されます:

```
件名: Security Event: login_success

本文:
Event: login_success
User: user@example.com
Time: 2025-10-13T10:00:00Z
IP: 192.168.1.100
```

---

## Level 2: Webhook通知の設定（10分）

### Webhookフックを作成

```bash
curl -X POST "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hook-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "id": "'$(uuidgen)'",
    "type": "WEBHOOK",
    "enabled": true,
    "triggers": [
      "password_success",
      "password_failure",
      "identity_verification_application_approved"
    ],
    "events": {
      "default": {
        "execution": {
          "function": "http_request",
          "http_request": {
            "url": "https://your-webhook-endpoint.example.com/security-events",
            "method": "POST",
            "auth_type": "oauth2",
            "oauth_authorization": {
              "type": "client_credentials",
              "token_endpoint": "https://auth.example.com/oauth/token",
              "client_authentication_type": "client_secret_post",
              "client_id": "your-client-id",
              "client_secret": "your-client-secret"
            },
            "body_mapping_rules": [
              { "from": "$.type", "to": "event_type" },
              { "from": "$.user.sub", "to": "user_id" }
            ],
            "retry_configuration": {
              "max_retries": 3,
              "retryable_status_codes": [502, 503, 504],
              "backoff_delays": ["PT1S", "PT2S", "PT4S"]
            }
          }
        }
      }
    }
  }'
```

**設定内容**:
- `events.default.execution.http_request.url` - Webhook エンドポイント
- `auth_type` - 認証タイプ（`oauth2`, `hmac_sha256`, `none`）
- `body_mapping_rules` - リクエストボディのマッピング
- `retry_configuration` - リトライ設定

---

### Webhookで受信するデータ

```json
{
  "event_type": "login_success",
  "timestamp": "2025-10-13T10:00:00Z",
  "tenant_id": "tenant-uuid",
  "user": {
    "sub": "user-12345",
    "email": "user@example.com"
  },
  "client_id": "my-app",
  "ip_address": "192.168.1.100",
  "user_agent": "Mozilla/5.0..."
}
```

---

## 利用可能なイベント一覧

### 認証関連イベント

| イベント名 | 発生タイミング |
|-----------|-------------|
| `password_success` | パスワード認証成功 |
| `password_failure` | パスワード認証失敗 |
| `sms_verification_success` | SMS認証成功 |
| `sms_verification_failure` | SMS認証失敗 |
| `email_verification_success` | メール認証成功 |
| `fido2_authentication_success` | FIDO2認証成功 |
| `fido2_authentication_failure` | FIDO2認証失敗 |
| `login_success` | セッション作成 |
| `logout` | セッション終了 |

### ユーザーライフサイクルイベント

| イベント名 | 発生タイミング |
|-----------|-------------|
| `user_signup` | ユーザー自己登録 |
| `user_self_delete` | ユーザー自己削除 |
| `user_create` | 管理者によるユーザー作成 |
| `user_delete` | 管理者によるユーザー削除 |
| `user_lock` | アカウントロック |
| `password_change_success` | パスワード変更成功 |
| `password_reset_success` | パスワードリセット成功 |

### 本人確認関連イベント

| イベント名 | 発生タイミング |
|-----------|-------------|
| `identity_verification_application_approved` | 本人確認申請承認 |
| `identity_verification_application_rejected` | 本人確認申請却下 |
| `identity_verification_application_cancelled` | 本人確認申請キャンセル |

### トークン・認可関連イベント

| イベント名 | 発生タイミング |
|-----------|-------------|
| `oauth_authorize` | Authorization Code発行 |
| `issue_token_success` | アクセストークン発行成功 |
| `issue_token_failure` | アクセストークン発行失敗 |
| `refresh_token_success` | トークン更新成功 |
| `revoke_token_success` | トークン失効 |

**完全なリスト**: [DefaultSecurityEventType.java](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/event/DefaultSecurityEventType.java)

---

## Level 3: SSF（Shared Signals Framework）

### SSFとは

**標準化されたセキュリティイベント通知プロトコル**（RFC 8935）

**用途**:
- 複数サービス間でセキュリティイベントを共有
- アカウント侵害の早期検出
- 協調的なセキュリティ対策

### SSF設定

```bash
curl -X POST "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hook-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "id": "'$(uuidgen)'",
    "type": "SSF",
    "enabled": true,
    "triggers": [
      "user_lock",
      "password_change_success",
      "revoke_token_success"
    ],
    "events": {
      "user_lock": {
        "execution": {
          "function": "ssf",
          "details": {
            "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/account-disabled",
            "kid": "ssf-key-id",
            "url": "https://receiver.example.com/ssf/events",
            "oauth_authorization": {
              "type": "client_credentials",
              "token_endpoint": "https://receiver.example.com/oauth/token",
              "client_authentication_type": "client_secret_post",
              "client_id": "your-client-id",
              "client_secret": "your-client-secret"
            }
          }
        }
      }
    },
    "metadata": {
      "issuer": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}",
      "spec_version": "1_0",
      "jwks_uri": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/ssf/jwks",
      "jwks": "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"...\",\"y\":\"...\",\"d\":\"...\",\"use\":\"sig\",\"kid\":\"ssf-key-id\",\"alg\":\"ES256\"}]}"
    }
  }'
```

> **重要**: `metadata.jwks` にはSET（Security Event Token）署名用の**秘密鍵を含むJWK Set**をJSON文字列で指定します。`jwks_uri` だけでは署名鍵を取得できないため、`jwks` フィールドは必須です。`kid` は `events` 内の各 `details.kid` と一致させてください。

**送信されるSSFトークン例**:
```json
{
  "iss": "https://idp.example.com",
  "iat": 1697000000,
  "jti": "unique-event-id",
  "aud": "https://receiver.example.com",
  "events": {
    "https://schemas.openid.net/secevent/risc/event-type/account-disabled": {
      "subject": {
        "sub": "user-12345"
      },
      "reason": "account locked due to failed login attempts"
    }
  }
}
```

**詳細**: [Developer Guide: Events](../content_06_developer-guide/03-application-plane/09-events.md)

---

## Level 4: 複数フックの組み合わせ

### Email + Webhook + SSF

```bash
# Webhook通知（セキュリティチーム向けSlack通知）
curl -X POST "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hook-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "id": "'$(uuidgen)'",
    "type": "WEBHOOK",
    "enabled": true,
    "triggers": ["password_failure", "user_lock"],
    "events": {
      "default": {
        "execution": {
          "function": "http_request",
          "http_request": {
            "url": "https://hooks.slack.com/services/xxx/yyy/zzz",
            "method": "POST",
            "auth_type": "none",
            "body_mapping_rules": [
              { "from": "$.type", "to": "event_type" },
              { "from": "$.user.sub", "to": "user_id" }
            ]
          }
        }
      }
    },
    "execution_order": 1
  }'

# SSF通知（外部システム連携）
curl -X POST "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hook-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "id": "'$(uuidgen)'",
    "type": "SSF",
    "enabled": true,
    "triggers": ["user_lock", "revoke_token_success"],
    "events": {
      "user_lock": {
        "execution": {
          "function": "ssf",
          "details": {
            "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/account-disabled",
            "kid": "ssf-key-id",
            "url": "https://external-system.example.com/ssf/events"
          }
        }
      }
    },
    "metadata": {
      "issuer": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}",
      "spec_version": "1_0",
      "jwks_uri": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/ssf/jwks",
      "jwks": "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"...\",\"y\":\"...\",\"d\":\"...\",\"use\":\"sig\",\"kid\":\"ssf-key-id\",\"alg\":\"ES256\"}]}"
    },
    "execution_order": 2
  }'
```

**効果**:
- ✅ パスワード認証失敗 → Slackでセキュリティチームに通知
- ✅ アカウントロック → Slack通知 + 外部システムにSSF送信

---

## 実例: エンタープライズ向けセキュリティイベントフック

### Email通知設定（外部通知システム連携）

**シナリオ**: 外部通知システムAPIを使用したメール送信

```json
{
  "id": "uuid",
  "type": "EMAIL",
  "enabled": true,
  "triggers": [
    "identity_verification_application_approved"
  ],
  "events": {
    "default": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "${NOTIFICATION_SYSTEM_URL}/api/v1/emails/send",
          "method": "POST",
          "auth_type": "none",
          "body_mapping_rules": [
            { "from": "$.user.email", "to": "to" },
            { "static_value": "本人確認申請が承認されました", "to": "subject" },
            { "from": "$.type", "to": "event_type" }
          ]
        }
      }
    }
  }
}
```

**特徴**:
- 外部通知システム連携（`${NOTIFICATION_SYSTEM_URL}`）
- body_mapping_rulesでメール内容をカスタマイズ
- 本人確認承認時に通知

---

### SSF通知設定（RISC準拠）

**シナリオ**: Shared Signals Frameworkによる標準化されたセキュリティイベント送信

```json
{
  "id": "uuid",
  "type": "SSF",
  "enabled": true,
  "triggers": [
    "password_success",
    "password_failure",
    "password_change_success",
    "user_lock",
    "issue_token_success",
    "revoke_token_success"
  ],
  "events": {
    "user_lock": {
      "execution": {
        "function": "ssf",
        "details": {
          "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/account-disabled",
          "kid": "ssf-key-id",
          "url": "${EXTERNAL_SSF_RECEIVER_URL}/ssf/events",
          "oauth_authorization": {
            "type": "client_credentials",
            "token_endpoint": "${EXTERNAL_SSF_RECEIVER_URL}/oauth/token",
            "client_authentication_type": "client_secret_post",
            "client_id": "${SSF_CLIENT_ID}",
            "client_secret": "${SSF_CLIENT_SECRET}"
          }
        }
      }
    }
  },
  "metadata": {
    "issuer": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}",
    "spec_version": "1_0",
    "jwks_uri": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/ssf/jwks",
    "jwks": "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"...\",\"y\":\"...\",\"d\":\"...\",\"use\":\"sig\",\"kid\":\"ssf-key-id\",\"alg\":\"ES256\"}]}"
  }
}
```

**特徴**:
- 外部SSF Receiver連携
- OAuth 2.0認証
- 幅広いイベントを送信（パスワード認証、変更、ロック等）
- `metadata.jwks` に署名用秘密鍵が必須

---

## よくあるエラー

### エラー1: 通知送信失敗

**エラーログ**:
```
Security Event Hook execution failed: Connection refused
```

**原因**: 通知先エンドポイントにアクセスできない

**解決策**:
```bash
# エンドポイントを確認
curl -X POST "https://your-webhook-endpoint.example.com/test" \
  -H "Content-Type: application/json" \
  -d '{"test": "message"}'

# URLが正しいか確認
# ファイアウォール設定を確認
```

---

### エラー2: リトライ上限到達

**エラーログ**:
```
Security Event Hook failed after 3 retries
```

**原因**: 通知先サービスが一時的にダウン、リトライ上限に到達

**解決策**:
```json
// リトライ設定を調整
"retry_configuration": {
  "max_retries": 5,  // 3回 → 5回に増やす
  "retryable_status_codes": [502, 503, 504, 429],  // 429 (Too Many Requests) も追加
  "backoff_delays": ["PT1S", "PT2S", "PT4S", "PT8S", "PT16S"]  // 指数バックオフ
}
```

---

### エラー3: イベントが送信されない

**原因**: `enabled: false` または該当イベントが`events`に含まれていない

**解決策**:
```bash
# フック設定を確認
curl "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hook-configurations" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.[] | {type, enabled, events}'

# enabled: true を確認
# eventsに該当イベントが含まれているか確認
```

---

## セキュリティイベントフックのベストプラクティス

### ✅ 推奨

1. **重要度に応じた通知先**
   ```
   高重要度（user_lock, user_delete等）:
     → Webhook（セキュリティチーム）
     → SSF（外部システム）

   中重要度（password_change_success, password_failure等）:
     → Webhook通知

   低重要度（login_success, issue_token_success等）:
     → ログのみ（通知なし、または集計後に通知）
   ```

2. **リトライ設定必須**
   ```json
   "retry_configuration": {
     "max_retries": 3,
     "idempotency_required": true  // 重要！
   }
   ```

3. **通知内容の最小化**
   ```json
   // ✅ 必要最小限
   "body_template": "Event: {event_type}\nUser: {user_id}\nTime: {timestamp}"

   // ❌ 過剰な情報（PII漏洩リスク）
   "body_template": "Event: {event_type}\nUser: {user_email}\nPassword: {password}"
   ```

---

### ❌ 避けるべき設定

1. **リトライ設定なし**
   ```json
   // ❌ 一時的なネットワークエラーで通知失敗
   "webhook_configuration": {
     "url": "...",
     // retry_configuration なし
   }
   ```

2. **冪等性保証なし**
   ```json
   // ❌ リトライ時に重複送信リスク
   "retry_configuration": {
     "max_retries": 3,
     "idempotency_required": false  // 危険
   }
   ```

3. **全イベントを通知**
   ```json
   // ❌ ノイズが多すぎる
   "events": [
     "login_success",
     "token_issued",
     "userinfo_success",
     // ... 全50種類
   ]
   ```

---

## 開発環境とテスト

### Mockoonでテスト

開発環境では、Mockoonで通知エンドポイントをモック：

```json
// Mockoon設定
{
  "name": "Security Event Webhook",
  "method": "POST",
  "endpoint": "security-events",
  "responses": [
    {
      "statusCode": 200,
      "body": "{\"status\": \"received\"}"
    }
  ]
}
```

### ログで確認

```bash
# idp-serverのログを確認
tail -f logs/idp-server.log | grep "SecurityEventHook"

# 出力例
SecurityEventHook executed: type=Email, event=login_success, status=SUCCESS
SecurityEventHook executed: type=Webhook, event=login_failure, status=RETRY (attempt 1/3)
```

---

## 次のステップ

✅ セキュリティイベントフックを設定できました！

### より高度な設定
- [Developer Guide: Events実装](../content_06_developer-guide/03-application-plane/09-events.md)
- [Implementation Guide: Security Event Hooks](../content_06_developer-guide/04-implementation-guides/impl-15-security-event-hooks.md)

### 関連機能
- [Identity Verification](./identity-verification/02-application.md) - 本人確認申請
- [Concept: Security Events](../content_03_concepts/06-security-extensions/concept-01-security-events.md)

---

## 関連ドキュメント

- [Concept: Audit & Compliance](../content_03_concepts/07-operations/concept-01-audit-compliance.md) - 監査ログ
- [Implementation Guide: HTTP Request Executor](../content_06_developer-guide/04-implementation-guides/impl-16-http-request-executor.md) - リトライ機構
- [API Reference](../content_07_reference/api-reference.md) - Management API仕様

---

**最終更新**: 2025-10-13
**難易度**: ⭐⭐⭐☆☆（中級）
**対象**: セキュリティイベント通知を設定する管理者・開発者
**習得スキル**: イベント選択、Email/Webhook/SSF設定、リトライ設定、冪等性保証
