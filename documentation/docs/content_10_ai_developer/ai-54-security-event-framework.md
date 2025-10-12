# idp-server-security-event-framework

## モジュール概要

**情報源**: `libs/idp-server-security-event-framework/`
**確認日**: 2025-10-12

### 責務

Shared Signals Framework (SSF) によるセキュリティイベント配信。

**仕様**: [Shared Signals Framework](https://openid.net/specs/openid-sse-framework-1_0.html)

### 主要機能

- **Push Delivery**: Webhook配信
- **Pull Delivery**: Polling配信
- **Event Types**: `account-disabled`, `credential-change`, `session-revoked`等
- **Security Event Token (SET)**: JWT形式のイベント

## SSF Event Types

| Event Type | 説明 |
|-----------|------|
| `https://schemas.openid.net/secevent/risc/event-type/account-disabled` | アカウント無効化 |
| `https://schemas.openid.net/secevent/risc/event-type/credential-change` | 認証情報変更 |
| `https://schemas.openid.net/secevent/risc/event-type/session-revoked` | セッション無効化 |
| `https://schemas.openid.net/secevent/caep/event-type/token-claims-change` | トークンクレーム変更 |

## SSF Hook Executor - Push Delivery実装

**情報源**: [SsfHookExecutor.java:35-176](../../libs/idp-server-security-event-framework/src/main/java/org/idp/server/security/event/hook/ssf/SsfHookExecutor.java#L35-L176)

```java
/**
 * SSF Hook Executor（Push Delivery）
 * 確認方法: 実ファイルの35-176行目
 */
public class SsfHookExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.SSF.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    long startTime = System.currentTimeMillis();

    try {
      // 1. SSF設定取得
      SecurityEventConfig securityEventConfig = hookConfiguration.getEvent(securityEvent.type());
      SharedSignalFrameworkMetadataConfig metadataConfig =
          jsonConverter.read(hookConfiguration.metadata(), SharedSignalFrameworkMetadataConfig.class);
      SecurityEventExecutionConfig executionConfig = securityEventConfig.execution();
      SharedSignalFrameworkTransmissionConfig transmissionConfig =
          jsonConverter.read(executionConfig.details(), SharedSignalFrameworkTransmissionConfig.class);

      // 2. Security Event Token (SET) 生成
      SecurityEventTokenCreator securityEventTokenCreator =
          new SecurityEventTokenCreator(securityEvent, metadataConfig, transmissionConfig);
      SecurityEventToken securityEventToken = securityEventTokenCreator.create();

      // 3. SSF送信
      return send(
          hookConfiguration,
          securityEvent,
          transmissionConfig.url(),
          securityEventToken,
          transmissionConfig,
          startTime);

    } catch (Exception e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration,
          securityEvent,
          null,
          executionDurationMs,
          e.getClass().getSimpleName(),
          "SSF hook execution failed: " + e.getMessage());
    }
  }

  private SecurityEventHookResult send(
      SecurityEventHookConfiguration hookConfiguration,
      SecurityEvent securityEvent,
      String endpoint,
      SecurityEventToken securityEventToken,
      SharedSignalFrameworkTransmissionConfig transmissionConfig,
      long startTime) {

    // HTTPリクエスト構築
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(new URI(endpoint))
        .header("Content-Type", "application/secevent+jwt")
        .POST(HttpRequest.BodyPublishers.ofString(securityEventToken.value()))
        .build();

    // OAuth認証が設定されている場合は追加
    HttpRequestResult httpRequestResult =
        (transmissionConfig.oauthAuthorization() != null)
            ? httpRequestExecutor.executeWithOAuth(httpRequest, transmissionConfig.oauthAuthorization())
            : httpRequestExecutor.execute(httpRequest);

    long executionDurationMs = System.currentTimeMillis() - startTime;

    // レスポンスハンドリング
    if (httpRequestResult.isSuccess()) {
      return SecurityEventHookResult.successWithContext(
          hookConfiguration, securityEvent, executionDetails, executionDurationMs);
    } else {
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration, securityEvent, executionDetails, executionDurationMs,
          "SSF_ERROR", "SSF transmission failed");
    }
  }
}
```

## 重要ポイント

- ✅ `SecurityEventHook` インターフェース実装
- ✅ `SecurityEventTokenCreator`によるSET生成
- ✅ OAuth認証オプション対応
- ✅ `SecurityEventHookResult`による結果管理
- ✅ 実行時間計測（executionDurationMs）

**注意**: SSF Pull Deliveryの実装は現時点で未確認。実装が存在するか要調査。

## 関連ドキュメント

- [通知・イベント層統合ドキュメント](./ai-50-notification-security-event.md) - SSFを含む全セキュリティイベントモジュール
- [idp-server-security-event-hooks](./ai-55-security-event-hooks.md) - Webhook/Slack/Datadog連携
- [idp-server-platform](./ai-12-platform.md) - HttpRequestExecutor（リトライ機能）

---

**情報源**:
- `libs/idp-server-security-event-framework/`配下の実装コード
- [SsfHookExecutor.java](../../libs/idp-server-security-event-framework/src/main/java/org/idp/server/security/event/hook/ssf/SsfHookExecutor.java)
- [Shared Signals Framework](https://openid.net/specs/openid-sse-framework-1_0.html)

**最終更新**: 2025-10-12
