---
name: spec-security-event
description: セキュリティイベント・フック（Security Events & Hooks）機能の開発・修正を行う際に使用。イベント発行、統計データ更新、フック連携（Slack/Datadog/Email/Webhook/SSF）の実装時に役立つ。
---

# セキュリティイベント機能 開発ガイド

## ドキュメント

詳細なドキュメントは以下を参照：
- `documentation/docs/content_03_concepts/06-security-extensions/concept-01-security-events.md` - セキュリティイベント概念
- `documentation/docs/content_03_concepts/07-operations/concept-03-tenant-statistics.md` - テナント統計概念
- `documentation/docs/content_06_developer-guide/03-application-plane/09-security-event.md` - 開発者ガイド
- `documentation/docs/content_06_developer-guide/05-configuration/security-event-hook.md` - セキュリティイベントフック設定
- `documentation/docs/content_05_how-to/phase-4-extensions/04-security-event-hooks.md` - フック設定

## 機能概要

セキュリティイベント機能は、ユーザー操作やシステムイベントを記録・通知する層。
- **イベントタイプ**: 認証、ユーザーライフサイクル、トークン、認可
- **フック**: Slack, Email, Webhook, SSF, Datadog
- **フィルタリング**: イベントタイプ別トリガー設定

## モジュール構成

```
libs/
├── idp-server-platform/               # コア定義
│   └── .../platform/security/
│       ├── SecurityEvent.java         # イベントモデル
│       ├── SecurityEventPublisher.java # イベント発行
│       ├── event/                     # イベント関連クラス
│       │   ├── SecurityEventType.java
│       │   ├── SecurityEventUser.java
│       │   └── DefaultSecurityEventType.java
│       ├── handler/
│       │   └── SecurityEventHandler.java  # メインハンドラー
│       ├── hook/                      # フックIF
│       └── repository/                # リポジトリIF
│
├── idp-server-springboot-adapter/     # Spring Boot統合
│   └── .../adapters/springboot/application/event/
│       └── SecurityEventPublisherService.java
│
├── idp-server-security-event-framework/  # SSF実装
│   └── .../security/event/hook/ssf/
│
├── idp-server-security-event-hooks/   # 各種フック実装
│   └── .../security/event/hooks/
│       ├── slack/
│       ├── datadog/
│       ├── email/
│       └── webhook/
│
├── idp-server-control-plane/          # 管理API
│   └── .../management/security/event/
│       └── SecurityEventManagementApi.java
│
└── idp-server-core-adapter/           # DB実装
    └── .../security/event/
        ├── command/
        └── query/
```

**注意**: 統一されたEventHookExecutorインターフェースではなく、各フックタイプごとに個別のExecutorクラスが存在します。

## テナント設定の前提条件（security_event_log_config）

セキュリティイベントの永続化・統計・フックを動作させるには、テナント設定の `security_event_log_config` で以下を有効にする必要がある（**デフォルトは全て `false`**）:

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
| `persistence_enabled` | `false` | `true` でイベントをDBに永続化（`security_event` テーブル） |
| `statistics_enabled` | `false` | `true` で統計データを更新（DAU/MAU/YAU、イベントカウント） |
| `format` | - | ログ出力形式（`structured_json` 推奨） |
| `include_event_detail` | `false` | イベント詳細（リクエストヘッダー等）を含めるか |
| `include_user_pii` | `false` | ユーザーPII（メール等）を含めるか |

**重要**: `persistence_enabled: false` の場合、Management API でセキュリティイベントを照会できない。`statistics_enabled: false` の場合、テナント統計API（`/statistics`）が常に空を返す。

実装: `SecurityEventLogConfiguration.java` → `SecurityEventHandler.handle()` 内で `config.isPersistenceEnabled()` / `config.isStatisticsEnabled()` をチェック。

## データライフサイクル

```
[1. 発生] 認証/認可イベント発生（Application Plane）
    ↓
[2. 発行] SecurityEvent発行（@Async、非同期）
    ↓
[3. 処理] SecurityEventHandler.handle()
    ├── DB保存（security_event テーブル）
    ├── 統計データ更新（statistics_events）
    ├── アクティブユーザー更新（DAU/MAU/YAU）
    ├── ログ出力
    └── フック実行（Slack/Datadog/Webhook/SSF）
    ↓  ※保存失敗時: SecurityEventRetryScheduler（60秒間隔、最大3回）
[4. 保持] アクティブパーティションに保存（日単位RANGE、90日間）
    ↓  ※PostgreSQL: pg_partman 毎日 02:00 UTC / MySQL: Event Scheduler 毎日 02:30 AM
[5. アーカイブ] 90日超過パーティションをDETACH → archiveスキーマに移動
    ↓  ※PostgreSQL: 毎日 03:00 UTC
[6. エクスポート] 外部ストレージへエクスポート（stub実装、S3/GCS対応可）
    ↓  ※エクスポート成功時のみ次のステップへ
[7. 削除] パーティションDROP
```

## 処理フロー（詳細）

ステップ3の `SecurityEventHandler.handle()` の詳細:

```
[SecurityEventHandler.handle()]
    ├── [1] イベントをDBに保存
    ├── [2] 統計データ更新（statistics_events）
    ├── [3] アクティブユーザー更新（DAU/MAU/YAU）
    ├── [4] ログ出力
    └── [5] フック実行（Slack/Datadog/Webhook/SSF）
```

## 統計データ更新

`SecurityEventHandler`は以下のリポジトリを使用：
- `StatisticsEventsCommandRepository` - イベント種別ごとのカウント
- `DailyActiveUserCommandRepository` - DAU
- `MonthlyActiveUserCommandRepository` - MAU
- `YearlyActiveUserCommandRepository` - YAU

### パフォーマンス改善（#1198）
- JSONBベースの更新 → 行ベースのupsertに変更
- `statistics_events`テーブルで直接upsert
- 10ms → 0.53ms（約19倍高速化）

### パフォーマンス改善（#1231）
- フックコンフィグ取得をAdapter層でキャッシュ（TTL 5分）
- 設定の登録/更新/削除時にキャッシュ自動無効化
- `TenantQueryDataSource`と同じパターンで実装
- 実装: `SecurityEventHookConfigurationQueryDataSource`, `SecurityEventHookConfigurationCommandDataSource`

## SecurityEventHook インターフェース

全フックは `SecurityEventHook` を実装する。

```java
public interface SecurityEventHook {
  SecurityEventHookType type();
  default boolean shouldExecute(
      Tenant tenant, SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {
    if (!hookConfiguration.hasEvents()) { return false; }
    return hookConfiguration.containsTrigger(securityEvent.type().value());
  }
  SecurityEventHookResult execute(
      Tenant tenant, SecurityEvent securityEvent,
      SecurityEventHookConfiguration configuration);
}
```

`shouldExecute()` でテナント・イベント種別・フック設定に基づくフィルタリングが可能。

## フック種別

| フック | 用途 | 実装クラス |
|--------|------|-----------|
| Slack | Slack通知 | `SlackSecurityEventHookExecutor` |
| Datadog | ログ転送 | - |
| Webhook | 汎用HTTP（RFC 8935準拠） | `WebHookSecurityEventExecutor` |
| SSF | Shared Signals Framework | `SsfHookExecutor` |
| Email | メール通知 | - |

### WebHook 実装パターン（RFC 8935）

`WebHookSecurityEventExecutor` は `hookConfiguration.getEvent(securityEvent.type())` で設定を取得し、`HttpRequestExecutor.execute()` に委譲する。成功/失敗は `SecurityEventHookResult.successWithContext()` / `failureWithContext()` で返却。

### Slack 実装パターン

`SlackSecurityEventHookExecutor` はテンプレートベースのメッセージ補間を使用。`NotificationTemplateInterpolator` がイベントデータをテンプレートに埋め込み、`{"text": "message"}` 形式で Slack Incoming Webhook に送信する。メッセージテンプレートはイベント種別ごとに設定可能。

### Retry メカニズム

全フックは `HttpRetryConfiguration` で exponential backoff を設定可能:
- `maxRetries`: 最大リトライ回数
- `retryableStatusCodes`: リトライ対象 HTTP ステータス (502, 503, 504)
- `backoffDelays`: バックオフ間隔リスト
- `idempotencyRequired`: Idempotency-Key ヘッダー自動付与

## フック設定

Management API エンドポイント:
```
POST /v1/management/tenants/{tenant-id}/security-event-hook-configurations
POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/security-event-hook-configurations
```

### Webhook設定例

```json
{
  "id": "uuid",
  "type": "WEBHOOK",
  "triggers": ["password_success", "password_failure"],
  "events": {
    "default": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://webhook.example.com/events",
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
  "execution_order": 100,
  "enabled": true,
  "store_execution_payload": true
}
```

### Slack設定例

```json
{
  "id": "uuid",
  "type": "SLACK",
  "triggers": ["password_failure", "user_self_delete"],
  "events": {
    "password_failure": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://hooks.slack.com/services/xxx/yyy/zzz",
          "method": "POST",
          "auth_type": "none"
        }
      }
    }
  },
  "enabled": true
}
```

### SSF設定例

```json
{
  "id": "uuid",
  "type": "SSF",
  "triggers": ["password_success", "password_failure"],
  "metadata": {
    "issuer": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}",
    "spec_version": "1_0",
    "jwks_uri": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/ssf/jwks",
    "jwks": "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"...\",\"y\":\"...\",\"d\":\"...\",\"use\":\"sig\",\"kid\":\"ssf-key-id\",\"alg\":\"ES256\"}]}"
  },
  "events": {
    "password_success": {
      "execution": {
        "function": "ssf",
        "details": {
          "security_event_type_identifier": "https://schemas.openid.net/secevent/risc/event-type/credential-compromise",
          "kid": "ssf-key-id",
          "url": "https://receiver.example.com/ssf/events"
        }
      }
    }
  },
  "enabled": true
}
```

**重要**: `metadata.jwks` にはSET署名用の**秘密鍵を含むJWK Set**をJSON文字列で指定。実装は `jwks_uri` からの動的取得を行わず、`jwks` フィールドから直接鍵を読み取る。`kid` は `events` 内の `details.kid` と一致させること。

### 主要フィールド

| フィールド | 説明 |
|-----------|------|
| `id` | Hook設定ID（UUID） |
| `type` | フックタイプ（`WEBHOOK`, `SLACK`, `SSF`, `Email`等） |
| `triggers` | トリガーとなるイベントタイプの配列 |
| `events` | イベントタイプごとの実行設定マップ（`default`で全トリガー共通設定） |
| `metadata.jwks` | **SSF必須** SET署名用の秘密鍵を含むJWK Set（JSON文字列） |
| `execution_order` | 実行順序（複数Hook時） |
| `enabled` | 有効/無効 |
| `store_execution_payload` | 実行ペイロードを保存するか |

## パーティショニング・アーカイブ（ライフサイクル ステップ4-7）

### パーティショニング（ステップ4: 保持）

`security_event` と `security_event_hook_results` は日単位の RANGE パーティショニングで管理。保持期間は90日。

| テーブル | パーティション | 保持期間 | 主キー |
|----------|:----------:|:-------:|--------|
| `security_event` | 日単位 | 90日 | `(id, created_at)` |
| `security_event_hook_results` | 日単位 | 90日 | `(id, created_at)` |

- **PostgreSQL**: pg_partman で自動管理（premake = 90日分先行作成）
- **MySQL**: Event Scheduler + ストアドプロシージャで自動管理

### アーカイブ・エクスポート・削除（ステップ5-7）

90日超過データは自動アーカイブ後に削除される。

**PostgreSQL:**
```
pg_partman retention (毎日 02:00 UTC)
  → [5] 90日超過パーティションを archive スキーマに DETACH
archive.process_archived_partitions() (毎日 03:00 UTC)
  → [6] 外部ストレージにエクスポート → [7] 成功時 DROP
```

**MySQL:**
```
Event Scheduler (毎日 02:30 AM)
  → [5] maintain_security_event_partitions()
  → [6] EXCHANGE PARTITION → archive テーブル → [7] DROP
```

外部ストレージへのエクスポートはスタブ実装（S3/GCS等に対応可能）。

**探索起点:**
- `libs/idp-server-database/postgresql/V0_9_21_1__add_event_partitioning.sql`
- `libs/idp-server-database/postgresql/V0_9_21_3__archive_support.sql`
- `libs/idp-server-database/postgresql/operation/setup-pg-cron-jobs.sql`
- `libs/idp-server-springboot-adapter/.../event/SecurityEventRetryScheduler.java`

## 通知アダプター

| アダプター | 用途 | 探索起点 |
|-----------|------|---------|
| `FcmNotifier` | FCM プッシュ通知（CIBA デバイス認証） | `libs/idp-server-notification-fcm-adapter/` |
| `ApnsNotifier` | APNS プッシュ通知（iOS） | `libs/idp-server-notification-apns-adapter/` |
| `AwsEmailSender` | AWS SES メール送信 | `libs/idp-server-email-aws-adapter/` |

## E2Eテスト

```
e2e/src/tests/
├── integration/ida/
│   └── integration-06-identity-verification-security-event.test.js
├── scenario/control_plane/organization/
│   └── organization_security_event_hook_management.test.js
└── usecase/
    ├── standard/standard-02-minimal-client-security-event.test.js
    ├── advance/advance-01-federation-security-event-user-name.test.js
    ├── ciba/ciba-04-security-event-device-ids.test.js
    └── mfa/mfa-02-security-event-logging.test.js
```

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-platform:compileJava
./gradlew :libs:idp-server-security-event-framework:compileJava
./gradlew :libs:idp-server-security-event-hooks:compileJava

# テスト
cd e2e && npm test -- scenario/control_plane/organization/organization_security_event_hook_management.test.js
cd e2e && npm test -- --grep "security.*event"
```

## トラブルシューティング

### フックが実行されない
- `enabled: true`か確認
- `triggers`配列にイベントタイプが含まれているか確認
- `events`マップに対応する実行設定があるか確認（`default`で全トリガー共通設定可能）

### Slack通知が届かない
- Webhook URLが正しいか確認
- 対応するExecutor（SlackSecurityEventHookExecutor）が登録されているか確認
