# セキュリティイベントフック設定ガイド

## このドキュメントの目的

**セキュリティイベント**（ログイン成功、失敗、本人確認完了等）を**外部サービスに通知**する設定ができるようになることが目標です。

### 所要時間
⏱️ **約20分**

### 前提条件
- 管理者トークンを取得済み
- 通知先サービス（Email、Slack、Webhook等）の準備
- 組織ID（organization-id）を取得済み

### Management API URL

**組織レベルAPI**（このドキュメントでの表記）:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/security-event-hooks
```

**注意**: システムレベルAPIとの違い
- **組織レベル**: `POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/security-event-hooks` ← このドキュメント
- **システムレベル**: `POST /v1/management/tenants/{tenant-id}/security-event-hooks` ← 管理者のみ

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
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hooks" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "Email",
    "enabled": true,
    "events": [
      "login_success",
      "login_failure",
      "password_changed"
    ],
    "email_configuration": {
      "to": "security-team@example.com",
      "subject": "Security Event: {event_type}",
      "body_template": "Event: {event_type}\nUser: {user_email}\nTime: {timestamp}\nIP: {ip_address}"
    }
  }'
```

**設定内容**:
- `type: "Email"` - Email通知
- `events` - 通知するイベント一覧
- `email_configuration` - メール設定（宛先、件名、本文）

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
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hooks" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "Webhook",
    "enabled": true,
    "events": [
      "login_success",
      "login_failure",
      "account_locked",
      "identity_verification_application_approved"
    ],
    "webhook_configuration": {
      "url": "https://your-webhook-endpoint.example.com/security-events",
      "method": "POST",
      "headers": {
        "Authorization": "Bearer your-api-key",
        "Content-Type": "application/json"
      },
      "retry_configuration": {
        "max_retries": 3,
        "retryable_status_codes": [502, 503, 504],
        "backoff_delays": ["PT1S", "PT2S", "PT4S"]
      }
    }
  }'
```

**設定内容**:
- `url` - Webhook エンドポイント
- `method` - HTTPメソッド（POST推奨）
- `headers` - 認証ヘッダー等
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

| イベント名 | 発生タイミング | 重要度 |
|-----------|-------------|-------|
| `login_success` | ログイン成功 | 🟢 低 |
| `login_failure` | ログイン失敗 | 🟡 中 |
| `authentication_success` | 認証成功 | 🟢 低 |
| `authentication_failure` | 認証失敗 | 🟡 中 |
| `mfa_success` | MFA成功 | 🟢 低 |
| `mfa_failure` | MFA失敗 | 🟡 中 |

### アカウント関連イベント

| イベント名 | 発生タイミング | 重要度 |
|-----------|-------------|-------|
| `account_locked` | アカウントロック | 🔴 高 |
| `account_unlocked` | アカウントロック解除 | 🟡 中 |
| `password_changed` | パスワード変更 | 🟡 中 |
| `user_created` | ユーザー作成 | 🟢 低 |
| `user_deleted` | ユーザー削除 | 🔴 高 |

### 本人確認関連イベント

| イベント名 | 発生タイミング | 重要度 |
|-----------|-------------|-------|
| `identity_verification_application_approved` | 本人確認申請承認 | 🟡 中 |
| `identity_verification_application_rejected` | 本人確認申請却下 | 🟡 中 |
| `identity_verification_application_cancelled` | 本人確認申請キャンセル | 🟢 低 |

### トークン関連イベント

| イベント名 | 発生タイミング | 重要度 |
|-----------|-------------|-------|
| `token_issued` | トークン発行 | 🟢 低 |
| `token_refreshed` | トークン更新 | 🟢 低 |
| `token_revoked` | トークン失効 | 🟡 中 |

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
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hooks" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "SSF",
    "enabled": true,
    "events": [
      "account_locked",
      "password_changed",
      "token_revoked"
    ],
    "ssf_configuration": {
      "transmission_endpoint": "https://receiver.example.com/ssf/events",
      "oauth_authorization": {
        "token_endpoint": "https://receiver.example.com/oauth/token",
        "client_id": "your-client-id",
        "client_secret": "your-client-secret"
      }
    }
  }'
```

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
# Email通知（ユーザー向け）
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hooks" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "Email",
    "enabled": true,
    "events": ["login_success", "password_changed"],
    "email_configuration": {
      "to": "{user_email}",
      "subject": "セキュリティ通知: {event_type}",
      "body_template": "あなたのアカウントで以下のイベントが発生しました:\n\nイベント: {event_type}\n日時: {timestamp}\nIP: {ip_address}"
    }
  }'

# Webhook通知（セキュリティチーム向け）
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hooks" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "Webhook",
    "enabled": true,
    "events": ["login_failure", "account_locked", "suspicious_activity"],
    "webhook_configuration": {
      "url": "https://slack.example.com/webhooks/security-alerts",
      "method": "POST"
    }
  }'

# SSF通知（外部システム連携）
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hooks" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "SSF",
    "enabled": true,
    "events": ["account_locked", "token_revoked"],
    "ssf_configuration": {
      "transmission_endpoint": "https://external-system.example.com/ssf/events"
    }
  }'
```

**効果**:
- ✅ ログイン成功 → ユーザーにEmail通知
- ✅ ログイン失敗 → Slackでセキュリティチームに通知
- ✅ アカウントロック → ユーザーにEmail、Slackに通知、外部システムにSSF送信

---

## 実例: エンタープライズ向けセキュリティイベントフック

### Email通知設定（外部通知システム連携）

**シナリオ**: 外部通知システムAPIを使用したメール送信

```json
{
  "type": "Email",
  "enabled": true,
  "events": [
    "identity_verification_application_approved",
    "strongauth_pin_registration_success"
  ],
  "email_configuration": {
    "function": "email",
    "http_request": {
      "url": "${NOTIFICATION_SYSTEM_URL}/api/v1/emails/send",
      "method": "POST",
      "body_mapping_rules": [
        {
          "from": "$.user.email",
          "to": "to"
        },
        {
          "static_value": "本人確認申請が承認されました",
          "to": "subject"
        },
        {
          "from": "$.event.details",
          "to": "body"
        }
      ]
    }
  }
}
```

**特徴**:
- 外部通知システム連携（`${NOTIFICATION_SYSTEM_URL}`）
- body_mapping_rulesでメール内容をカスタマイズ
- 本人確認承認、PIN登録成功時に通知

---

### SSF通知設定（RISC準拠）

**シナリオ**: Shared Signals Frameworkによる標準化されたセキュリティイベント送信

```json
{
  "type": "SSF",
  "enabled": true,
  "events": [
    "login_success",
    "login_failure",
    "password_changed",
    "account_locked",
    "token_issued",
    "token_revoked"
  ],
  "ssf_configuration": {
    "function": "ssf",
    "transmission_endpoint": "${EXTERNAL_SSF_RECEIVER_URL}/ssf/events",
    "oauth_authorization": {
      "token_endpoint": "${EXTERNAL_SSF_RECEIVER_URL}/oauth/token",
      "client_id": "${SSF_CLIENT_ID}",
      "client_secret": "${SSF_CLIENT_SECRET}"
    }
  }
}
```

**特徴**:
- 外部SSF Receiver連携
- OAuth 2.0認証
- 幅広いイベントを送信（ログイン、パスワード変更、ロック等）

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
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-event-hooks" \
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
   高重要度（account_locked等）:
     → Email（ユーザー）
     → Slack（セキュリティチーム）
     → SSF（外部システム）

   中重要度（password_changed等）:
     → Email（ユーザー）

   低重要度（login_success等）:
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
- [How-to: Identity Verification](./how-to-16-identity-verification-application.md) - 本人確認申請
- [Concept: Security Events](../content_03_concepts/concept-11-security-events.md)

---

## 関連ドキュメント

- [Concept: Audit & Compliance](../content_03_concepts/concept-13-audit-compliance.md) - 監査ログ
- [Implementation Guide: HTTP Request Executor](../content_06_developer-guide/04-implementation-guides/impl-16-http-request-executor.md) - リトライ機構
- [API Reference](../content_07_reference/api-reference.md) - Management API仕様

---

**最終更新**: 2025-10-13
**難易度**: ⭐⭐⭐☆☆（中級）
**対象**: セキュリティイベント通知を設定する管理者・開発者
**習得スキル**: イベント選択、Email/Webhook/SSF設定、リトライ設定、冪等性保証
