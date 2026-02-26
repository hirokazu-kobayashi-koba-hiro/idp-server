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

## 処理フロー

```
[認証/認可イベント発生]
    ↓
[SecurityEvent発行] (@Async)
    ↓
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

```json
{
  "hook_id": "uuid",
  "name": "Login Success Notification",
  "enabled": true,
  "hook_type": "slack",
  "trigger": {
    "event_types": [
      "PASSWORD_AUTH_SUCCESS",
      "FIDO2_AUTH_SUCCESS"
    ]
  },
  "config": {
    "webhook_url": "https://hooks.slack.com/services/xxx/yyy/zzz",
    "channel": "#security-alerts"
  }
}
```

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
- `trigger.event_types`にイベントタイプが含まれているか確認

### Slack通知が届かない
- Webhook URLが正しいか確認
- 対応するExecutor（SlackSecurityEventHookExecutor）が登録されているか確認
