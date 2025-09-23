# Issue #248 リトライ機能実装設計

## 概要
HttpRequestExecutorにリトライ機能を統合し、外部サービス呼び出しの信頼性を向上させる。

## アーキテクチャ設計

### 1. リトライ設定クラス
```java
public class HttpRetryConfiguration {
  private int maxRetries = 3;
  private Duration[] backoffDelays = {
    Duration.ofSeconds(1),   // 1st retry
    Duration.ofSeconds(5),   // 2nd retry
    Duration.ofSeconds(30)   // 3rd retry
  };
  private Set<Integer> retryableStatusCodes = Set.of(500, 502, 503, 504, 408);
  private Set<Class<? extends Exception>> retryableExceptions =
    Set.of(IOException.class, ConnectException.class, SocketTimeoutException.class);
  private boolean idempotencyRequired = false;
}
```

### 2. HttpRequestExecutor拡張
```java
public class HttpRequestExecutor {
  // 既存フィールド +
  private HttpRetryConfiguration retryConfig;
  private IdempotencyKeyManager idempotencyManager;

  // 新しいリトライ対応メソッド
  public HttpRequestResult executeWithRetry(
      HttpRequest request,
      HttpRetryConfiguration config) {
    return executeWithRetry(request, null, config);
  }

  public HttpRequestResult executeWithRetry(
      HttpRequest request,
      OAuthAuthorizationConfiguration oAuthConfig,
      HttpRetryConfiguration retryConfig) {

    for (int attempt = 0; attempt <= retryConfig.maxRetries(); attempt++) {
      try {
        // Idempotency-Key追加
        HttpRequest enhancedRequest = addIdempotencyKeyIfNeeded(request, retryConfig);

        // OAuth認証適用
        HttpRequestResult result = (oAuthConfig != null)
          ? executeWithOAuth(enhancedRequest, oAuthConfig)
          : execute(enhancedRequest);

        // 成功またはリトライ不要エラーの場合は即座に返却
        if (result.isSuccess() || !isRetryable(result, retryConfig)) {
          return result;
        }

        // リトライ前の待機
        if (attempt < retryConfig.maxRetries()) {
          waitBeforeRetry(attempt, retryConfig);
        }

      } catch (Exception e) {
        if (!isRetryable(e, retryConfig) || attempt == retryConfig.maxRetries()) {
          throw e;
        }
        waitBeforeRetry(attempt, retryConfig);
      }
    }

    // 最大リトライ回数超過時のフォールバック
    return createMaxRetriesExceededResult();
  }
}
```

### 3. Idempotency Key管理
```java
@Component
public class IdempotencyKeyManager {
  private final Cache<String, String> idempotencyCache;

  public String generateKey(HttpRequest request) {
    return "idem_" + UUID.randomUUID().toString();
  }

  public HttpRequest addIdempotencyKey(HttpRequest request, String key) {
    return HttpRequest.newBuilder(request.uri())
      .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
      .headers(getAllHeaders(request))
      .header("Idempotency-Key", key)
      .build();
  }
}
```

### 4. Circuit Breaker統合
```java
public class CircuitBreakerHttpExecutor {
  private final CircuitBreaker circuitBreaker;
  private final HttpRequestExecutor executor;

  public HttpRequestResult executeWithCircuitBreaker(
      HttpRequest request,
      OAuthAuthorizationConfiguration oAuthConfig,
      HttpRetryConfiguration retryConfig) {

    return circuitBreaker.executeSupplier(() ->
      executor.executeWithRetry(request, oAuthConfig, retryConfig)
    );
  }
}
```

## 設定統合パターン

### HttpRequestExecutionConfig拡張
```java
public interface HttpRequestExecutionConfigInterface {
  // 既存メソッド...

  // 新規追加
  HttpRetryConfiguration retryConfiguration();
  boolean circuitBreakerEnabled();
}
```

### SsfHookExecutor適用例
```java
public class SsfHookExecutor implements SecurityEventHook {

  private SecurityEventHookResult send(
      String endpoint,
      SecurityEventToken securityEventToken,
      SharedSignalFrameworkTransmissionConfig transmissionConfig) {

    HttpRequest httpRequest = buildRequest(endpoint, securityEventToken);

    // リトライ設定を取得（設定ファイルから）
    HttpRetryConfiguration retryConfig = transmissionConfig.retryConfiguration()
      .orElse(HttpRetryConfiguration.defaultConfig());

    try {
      HttpRequestResult result;
      if (transmissionConfig.oauthAuthorization() != null) {
        result = httpRequestExecutor.executeWithRetry(
          httpRequest,
          transmissionConfig.oauthAuthorization(),
          retryConfig
        );
      } else {
        result = httpRequestExecutor.executeWithRetry(httpRequest, retryConfig);
      }

      return handleResponse(result);

    } catch (MaxRetriesExceededException e) {
      log.error("SSF transmission failed after {} retries: endpoint={}",
        retryConfig.maxRetries(), endpoint, e);
      return SecurityEventHookResult.failure(type(),
        Map.of("error", "max_retries_exceeded", "retries", retryConfig.maxRetries()));
    }
  }
}
```

## 段階的実装計画

### Phase 1: 基本リトライ機能 (1週間)
- [ ] HttpRetryConfigurationクラス作成
- [ ] HttpRequestExecutor.executeWithRetry()実装
- [ ] 指数バックオフ戦略実装
- [ ] 基本的なリトライ判定ロジック

### Phase 2: Idempotency対応 (1週間)
- [ ] IdempotencyKeyManagerクラス作成
- [ ] HttpRequestへのIdempotency-Key自動付与
- [ ] 重複リクエスト検知・防止機能

### Phase 3: Circuit Breaker統合 (1週間)
- [ ] CircuitBreakerHttpExecutor実装
- [ ] 外部サービス障害検知・自動復旧
- [ ] メトリクス・監視機能追加

### Phase 4: 既存コンポーネント適用 (1週間)
- [ ] SsfHookExecutorリトライ対応
- [ ] WebHookSecurityEventExecutorリトライ対応
- [ ] SsoCredentialsParameterResolverリトライ対応
- [ ] その他のHTTP通信コンポーネント

## 設定例

### SSF Transmission設定
```json
{
  "url": "https://example.com/ssf/receiver",
  "oauth_authorization": { "..." },
  "retry_configuration": {
    "max_retries": 3,
    "backoff_delays": ["1s", "5s", "30s"],
    "retryable_status_codes": [500, 502, 503, 504],
    "idempotency_required": true,
    "circuit_breaker_enabled": true
  }
}
```

### Identity Verification Pre-hook設定
```json
{
  "url": "https://api.external.com/verify",
  "retry_configuration": {
    "max_retries": 2,
    "backoff_delays": ["1s", "3s"],
    "retryable_status_codes": [408, 500, 502, 503],
    "idempotency_required": false
  }
}
```

## メトリクス・監視

### リトライメトリクス
```java
@Component
public class RetryMetrics {
  private final Counter retryCounter;
  private final Timer retryDelayTimer;
  private final Gauge circuitBreakerState;

  public void recordRetry(String component, int attempt, String reason) {
    retryCounter.increment(
      Tag.of("component", component),
      Tag.of("attempt", String.valueOf(attempt)),
      Tag.of("reason", reason)
    );
  }
}
```

### ログ出力例
```json
{
  "timestamp": "2025-09-23T10:30:45.123Z",
  "level": "WARN",
  "message": "HTTP request retry",
  "component": "ssf_hook_executor",
  "endpoint": "https://example.com/ssf/receiver",
  "attempt": 2,
  "max_retries": 3,
  "backoff_delay": "5s",
  "error_type": "SERVER_ERROR",
  "status_code": 503,
  "idempotency_key": "idem_abc123..."
}
```

## 期待される効果

1. **信頼性向上**: 一時的な外部サービス障害への自動対応
2. **運用負荷軽減**: 手動リトライ作業の削減
3. **ユーザー体験向上**: より安定したサービス提供
4. **監視強化**: リトライ状況の可視化
5. **設定統一**: 全コンポーネントで統一されたリトライ戦略

この設計により、Issue #248の要件を満たしつつ、既存のHttpRequestExecutorアーキテクチャと統合された、スケーラブルなリトライ機能を実現できます。