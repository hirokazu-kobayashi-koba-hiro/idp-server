---
name: use-case-enterprise
description: エンタープライズ（セキュリティイベントフック）ユースケースの設定ガイド。Webhook/SSFフック設定、セキュリティイベント永続化、テナント統計、mock-serverによる動作確認を提供。
---

# エンタープライズ（セキュリティイベントフック）

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | フックタイプ | Webhook / SSF / 両方 | フック設定JSON |
| 2 | トリガーイベント | password_success, password_failure, login_success, user_signup 等 | `triggers` 配列 |
| 3 | Webhook送信先URL | 外部エンドポイントURL | `events.default.execution.http_request.url` |
| 4 | Webhook認証 | none / oauth2 / hmac_sha256 | `auth_type`, `oauth_authorization` |
| 5 | SSF送信先URL | SSF ReceiverのURL | `events.{type}.execution.details.url` |
| 6 | SSF署名鍵 | EC P-256 / RS256 | `metadata.jwks`, `details.kid` |
| 7 | イベント永続化 | 有効 / 無効 | テナント `security_event_log_config.persistence_enabled` |
| 8 | 統計データ記録 | 有効 / 無効 | テナント `security_event_log_config.statistics_enabled` |

---

## テナント設定の前提条件（security_event_log_config）

セキュリティイベントの永続化・統計・フックを動作させるには、テナント設定で以下を有効にする（**デフォルトは全て `false`**）:

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
| `persistence_enabled` | `false` | `true` でイベントをDBに永続化。Management API でイベント照会に必須 |
| `statistics_enabled` | `false` | `true` で統計データ（DAU/MAU/YAU、イベントカウント）を記録。統計APIに必須 |

**重要**: フック自体はこれらの設定に関係なく実行されるが、イベント永続化と統計記録は明示的に有効化が必要。

> **注意**: テンプレートに含まれる秘密鍵（`jwks.json`, `security-event-hook-ssf.json` の `metadata.jwks`）はテスト専用です。本番環境では必ず別の鍵を生成して使用してください。

---

## テンプレートファイル

```
config/templates/use-cases/enterprise/
├── setup.sh                                  # 環境構築（オンボーディング〜クライアント作成）
├── delete.sh                                 # クリーンアップ
├── helpers.sh                                # 共通関数（認証フロー、フック管理、イベント照会）
├── verify.sh                                 # 自動検証（9ステップ）
├── mock-server.js                            # Webhook/SSF受信用モックサーバー（port 4005）
├── VERIFY.md                                 # 手動検証ガイド（6フェーズ18ステップ）
├── onboarding-template.json                  # オンボーディング設定
├── public-tenant-template.json               # テナント設定（statistics_enabled含む）
├── public-client-template.json               # クライアント設定
├── authentication-config-initial-registration.json  # 認証メソッド設定
├── authentication-policy.json                # 認証ポリシー
├── jwks.json                                 # テナント署名鍵
├── security-event-hook-webhook.json          # Webhookフック設定テンプレート
└── security-event-hook-ssf.json              # SSFフック設定テンプレート
```

---

## セットアップ手順

```bash
# 1. 環境構築
bash config/templates/use-cases/enterprise/setup.sh

# 2. mock-server起動（別ターミナル）
node config/templates/use-cases/enterprise/mock-server.js

# 3. 動作確認
cd config/templates/use-cases/enterprise
source helpers.sh
get_admin_token
source verify.sh

# 4. クリーンアップ
bash config/templates/use-cases/enterprise/delete.sh
```

---

## Webhook フック設定

```json
{
  "type": "WEBHOOK",
  "triggers": ["password_success", "password_failure", "login_success", "user_signup"],
  "events": {
    "default": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "${WEBHOOK_URL}",
          "method": "POST",
          "auth_type": "none",
          "body_mapping_rules": [
            { "from": "$.type", "to": "event_type" },
            { "from": "$.user.sub", "to": "user_id" },
            { "from": "$.user.preferred_username", "to": "username" }
          ]
        }
      }
    }
  },
  "enabled": true
}
```

### body_mapping_rules

- `from`: JsonPath構文（`$` プレフィックス必須）。`SecurityEvent.toMap()` のキーを参照
- `to`: 送信先リクエストボディのフィールド名

**利用可能なJsonPathキー**:
| JsonPath | 内容 |
|----------|------|
| `$.type` | イベントタイプ（`password_success` 等） |
| `$.user.sub` | ユーザーID |
| `$.user.preferred_username` | ユーザー名 |
| `$.user.email` | メールアドレス |
| `$.tenant.id` | テナントID |
| `$.client.id` | クライアントID |
| `$.ip_address` | IPアドレス |
| `$.created_at` | イベント発生日時 |

---

## SSF フック設定

```json
{
  "type": "SSF",
  "triggers": ["password_success", "password_failure"],
  "metadata": {
    "issuer": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}",
    "spec_version": "1_0",
    "jwks_uri": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/ssf/jwks",
    "jwks": "{\"keys\":[{...秘密鍵を含むJWK Set...}]}"
  },
  "events": {
    "password_success": {
      "execution": {
        "function": "ssf",
        "details": {
          "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/credential-compromise",
          "kid": "ssf-key-id",
          "url": "${SSF_RECEIVER_URL}"
        }
      }
    }
  },
  "enabled": true
}
```

### SSF設定の注意点

- **`metadata.jwks` 必須**: SET（Security Event Token）署名用の**秘密鍵を含むJWK Set**をJSON文字列で指定。実装は `jwks_uri` からの動的取得を行わず `jwks` から直接鍵を読み取る
- **`kid` の一致**: `metadata.jwks` 内の鍵の `kid` と `events.{type}.execution.details.kid` を一致させること
- **`oauth_authorization`**: SSF Receiver が認証を要求する場合に設定。`client_authentication_type` フィールド必須

---

## Management API（検証用）

### セキュリティイベント照会

```
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/security-events?limit=10
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/security-events?event_type=password_success
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/security-events/{event-id}
```

### フック実行結果照会

```
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/security-event-hooks?limit=10
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/security-event-hooks?hook_type=WEBHOOK
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/security-event-hooks?status=FAILURE
```

レスポンスフィールド: `security_event.type`（イベントタイプ）, `type`（フックタイプ）, `status`, `created_at`

### テナント統計

```
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/statistics?from=2026-03&to=2026-03
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/statistics/yearly/2026
```

---

## 環境変数

| 変数 | デフォルト | 説明 |
|------|-----------|------|
| `ORGANIZATION_NAME` | `enterprise` | 組織名 |
| `MOCK_API_URL` | `http://host.docker.internal:4005` | mock-server URL（Docker内から） |
| `MOCK_LOCAL_URL` | `http://localhost:4005` | mock-server URL（ホストから） |
| `TEST_EMAIL` | `test-enterprise@example.com` | テストユーザーメール |
| `TEST_PASSWORD` | `ChangeMe123` | テストユーザーパスワード |

---

## 検証で確認される項目

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | Webhook フック登録 | HTTP 201 |
| 2 | SSF フック登録 | HTTP 201 |
| 3 | フック一覧取得 | 登録した2件が含まれる |
| 4 | 認証フロー（登録→ログイン→失敗） | イベント発火 |
| 5 | Webhook イベント受信 | event_type, user_id が正確 |
| 6 | SSF イベント受信 | content_type: application/secevent+jwt |
| 7 | セキュリティイベント永続化 | DB に8件以上 |
| 8 | フック実行結果 | ALL SUCCESS |
| 9 | テナント統計 | 当月データ取得成功 |

$ARGUMENTS
