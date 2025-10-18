# idp-server-security-event-hooks

## モジュール概要

**情報源**: `libs/idp-server-security-event-hooks/`
**確認日**: 2025-10-12

### 責務

セキュリティイベントフック（Webhook/Slack/Datadog連携）。

### 主要機能

- **Webhook**: 汎用Webhook配信
- **Slack**: Slack通知
- **Datadog**: Datadogイベント送信
- **Retry Mechanism**: 失敗時のリトライ

## SecurityEventHook インターフェース

**情報源**: [SecurityEventHook.java:23](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/hook/SecurityEventHook.java#L23)

```java
/**
 * セキュリティイベントフック（Plugin）
 * 確認方法: 実ファイルの23-41行目
 */
public interface SecurityEventHook {

  // ✅ フックタイプ
  SecurityEventHookType type();

  // ✅ 実行判定（defaultメソッド）
  default boolean shouldExecute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    if (!hookConfiguration.hasEvents()) {
      return false;
    }

    return hookConfiguration.containsTrigger(securityEvent.type().value());
  }

  // ✅ フック実行（Tenant第一引数）
  SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration configuration);
}
```

## WebHookSecurityEventExecutor - Webhook実装

**情報源**: [WebHookSecurityEventExecutor.java:35](../../../libs/idp-server-security-event-hooks/src/main/java/org/idp/server/security/event/hooks/webhook/WebHookSecurityEventExecutor.java#L35)

```java
/**
 * Webhook実装（RFC 8935準拠）
 * 確認方法: 実ファイルの35-80行目
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8935">RFC 8935 - Push-Based SET Delivery</a>
 */
public class WebHookSecurityEventExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.WEBHOOK.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    long startTime = System.currentTimeMillis();

    try {
      // 1. 設定取得
      WebHookConfiguration configuration =
          jsonConverter.read(hookConfiguration, WebHookConfiguration.class);

      // 2. イベントタイプ別の設定取得
      HttpRequestUrl httpRequestUrl = configuration.httpRequestUrl(securityEvent.type());
      HttpMethod httpMethod = configuration.httpMethod(securityEvent.type());
      HttpRequestStaticHeaders httpRequestStaticHeaders =
          configuration.httpRequestHeaders(securityEvent.type());
      HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys =
          configuration.httpRequestDynamicBodyKeys(securityEvent.type());
      HttpRequestStaticBody httpRequestStaticBody =
          configuration.httpRequestStaticBody(securityEvent.type());

      // 3. リクエストボディ生成（動的マッピング）
      HttpRequestBodyCreator requestBodyCreator =
          new HttpRequestBodyCreator(
              new HttpRequestBaseParams(securityEvent.toMap()),
              httpRequestDynamicBodyKeys,
              httpRequestStaticBody);
      Map<String, Object> requestBody = requestBodyCreator.create();

      // 4. HTTPリクエスト実行
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(new URI(httpRequestUrl.value()))
          .method(httpMethod.value(), /* body */)
          .headers(httpRequestStaticHeaders.toArray())
          .build();

      HttpRequestResult result = httpRequestExecutor.execute(httpRequest);

      long executionDurationMs = System.currentTimeMillis() - startTime;

      if (result.isSuccess()) {
        return SecurityEventHookResult.success(securityEvent, result, executionDurationMs);
      } else {
        return SecurityEventHookResult.failure(securityEvent, result, executionDurationMs);
      }

    } catch (Exception e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      return SecurityEventHookResult.error(securityEvent, e, executionDurationMs);
    }
  }
}
```

**重要ポイント**:
- ✅ **Tenant第一引数**: マルチテナント分離
- ✅ **実行時間計測**: `executionDurationMs`でパフォーマンス監視
- ✅ **動的設定**: イベントタイプ別にURL/メソッド/ヘッダー/ボディを設定可能
- ✅ **例外ハンドリング**: 成功/失敗/エラーを明確に区別

## SlackSecurityEventHookExecutor - Slack実装

**情報源**: `libs/idp-server-security-event-hooks/src/main/java/org/idp/server/security/event/hooks/slack/`

```java
public class SlackSecurityEventHookExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.SLACK.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    long startTime = System.currentTimeMillis();

    // 1. Slack Webhook URL取得
    SlackSecurityEventHookConfiguration config =
        jsonConverter.read(hookConfiguration, SlackSecurityEventHookConfiguration.class);

    // 2. Slackメッセージ構築
    String slackPayload = buildSlackMessage(securityEvent);

    // 3. Webhook送信
    HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(config.webhookUrl()))
        .POST(HttpRequest.BodyPublishers.ofString(slackPayload))
        .header("Content-Type", "application/json")
        .build();

    HttpRequestResult result = httpRequestExecutor.execute(request);

    long executionDurationMs = System.currentTimeMillis() - startTime;

    return result.isSuccess()
        ? SecurityEventHookResult.success(securityEvent, result, executionDurationMs)
        : SecurityEventHookResult.failure(securityEvent, result, executionDurationMs);
  }

  private String buildSlackMessage(SecurityEvent event) {
    return String.format("""
        {
          "text": "Security Event: %s",
          "blocks": [
            {
              "type": "header",
              "text": {
                "type": "plain_text",
                "text": "Security Event"
              }
            },
            {
              "type": "section",
              "fields": [
                {"type": "mrkdwn", "text": "*Type:*\\n%s"},
                {"type": "mrkdwn", "text": "*User:*\\n%s"},
                {"type": "mrkdwn", "text": "*Time:*\\n%s"}
              ]
            }
          ]
        }
        """,
        event.type(),
        event.type().value(),
        event.userSub(),
        event.timestamp()
    );
  }
}
```

## Retry Mechanism

**情報源**: [HttpRequestExecutor.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/http/HttpRequestExecutor.java)（platform層で提供）

**重要**: 専用のRetryExecutorクラスは存在しない。`HttpRequestExecutor.executeWithRetry()`を使用。

```java
/**
 * リトライ機構（Issue #398）
 * platform層のHttpRequestExecutorが提供
 */

// SSF Hook内でHttpRequestExecutor.executeWithRetry()を使用
public class SsfHookExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;

  private SecurityEventHookResult send(...) {
    // HTTPリクエスト構築
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(new URI(endpoint))
        .header("Content-Type", "application/secevent+jwt")
        .POST(HttpRequest.BodyPublishers.ofString(securityEventToken.value()))
        .build();

    // ✅ リトライ設定（設定ファイルから取得）
    HttpRetryConfiguration retryConfig = HttpRetryConfiguration.builder()
        .maxRetries(3)
        .retryableStatusCodes(List.of(502, 503, 504))
        .backoffDelays(List.of(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4)
        ))
        .idempotencyRequired(true)
        .build();

    // ✅ HttpRequestExecutor.executeWithRetry()でリトライ実行
    HttpRequestResult httpRequestResult =
        (transmissionConfig.oauthAuthorization() != null)
            ? httpRequestExecutor.executeWithRetry(
                httpRequest,
                transmissionConfig.oauthAuthorization(),
                retryConfig)
            : httpRequestExecutor.executeWithRetry(httpRequest, retryConfig);

    // レスポンスハンドリング
    long executionDurationMs = System.currentTimeMillis() - startTime;
    return httpRequestResult.isSuccess()
        ? SecurityEventHookResult.successWithContext(...)
        : SecurityEventHookResult.failureWithContext(...);
  }
}
```

**重要ポイント**:
- ✅ platform層の`HttpRequestExecutor`がリトライ機能を提供
- ✅ `HttpRetryConfiguration`でリトライ設定を定義
- ✅ Exponential Backoff自動実行
- ✅ Idempotency-Keyヘッダー自動付与（`idempotencyRequired: true`）
- ❌ 専用の`SecurityEventHookRetryExecutor`クラスは存在しない

## 関連ドキュメント

- [通知・イベント層統合ドキュメント](./ai-50-notification-security-event.md) - フックを含む全セキュリティイベントモジュール
- [idp-server-security-event-framework](./ai-54-security-event-framework.md) - Shared Signals Framework
- [idp-server-platform](./ai-12-platform.md) - HttpRequestExecutor（リトライ機能）

---

**情報源**:
- `libs/idp-server-security-event-hooks/`配下の実装コード
- [WebHookSecurityEventExecutor.java](../../../libs/idp-server-security-event-hooks/src/main/java/org/idp/server/security/event/hooks/webhook/WebHookSecurityEventExecutor.java)
- Issue #398（Security Event Hook Retry Mechanism）
- platform.md「HTTP クライアント - リトライ機能付き実行」

**最終更新**: 2025-10-12
