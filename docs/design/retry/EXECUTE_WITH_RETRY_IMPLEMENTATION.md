# executeWithRetry() メソッド実装詳細

## 1. HttpRetryConfiguration クラス

```java
public class HttpRetryConfiguration {
  private final int maxRetries;
  private final Duration[] backoffDelays;
  private final Set<Integer> retryableStatusCodes;
  private final Set<Class<? extends Exception>> retryableExceptions;
  private final boolean idempotencyRequired;
  private final String strategy; // EXPONENTIAL_BACKOFF, FIXED_DELAY, etc.

  public HttpRetryConfiguration() {
    // デフォルト設定（Issue #248準拠）
    this.maxRetries = 3;
    this.backoffDelays = new Duration[]{
      Duration.ofSeconds(1),   // 1st retry
      Duration.ofSeconds(5),   // 2nd retry
      Duration.ofSeconds(30)   // 3rd retry
    };
    this.retryableStatusCodes = Set.of(500, 502, 503, 504, 408, 429);
    this.retryableExceptions = Set.of(
      IOException.class,
      ConnectException.class,
      SocketTimeoutException.class,
      HttpTimeoutException.class
    );
    this.idempotencyRequired = false;
    this.strategy = "EXPONENTIAL_BACKOFF";
  }

  public static HttpRetryConfiguration noRetry() {
    return new Builder().maxRetries(0).build();
  }

  public static HttpRetryConfiguration defaultRetry() {
    return new HttpRetryConfiguration();
  }

  // Builder pattern implementation...
}
```

## 2. HttpRequestExecutor への executeWithRetry() 追加

```java
public class HttpRequestExecutor {
  // 既存フィールド
  HttpClient httpClient;
  OAuthAuthorizationResolvers oAuthorizationResolvers;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestExecutor.class);

  // 新規追加
  private IdempotencyKeyManager idempotencyManager;

  // コンストラクタ更新
  public HttpRequestExecutor(
      HttpClient httpClient,
      OAuthAuthorizationResolvers oAuthAuthorizationResolvers,
      IdempotencyKeyManager idempotencyManager) {
    this.httpClient = httpClient;
    this.oAuthorizationResolvers = oAuthAuthorizationResolvers;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
    this.idempotencyManager = idempotencyManager;
  }

  // 1. 基本的なリトライメソッド
  public HttpRequestResult executeWithRetry(
      HttpRequest httpRequest,
      HttpRetryConfiguration retryConfig) {
    return executeWithRetry(httpRequest, null, retryConfig);
  }

  // 2. OAuth + リトライメソッド
  public HttpRequestResult executeWithRetry(
      HttpRequest httpRequest,
      OAuthAuthorizationConfiguration oAuthConfig,
      HttpRetryConfiguration retryConfig) {

    String correlationId = generateCorrelationId();
    String idempotencyKey = null;

    log.info("Starting HTTP request with retry: uri={}, maxRetries={}, correlationId={}",
      httpRequest.uri(), retryConfig.maxRetries(), correlationId);

    for (int attempt = 0; attempt <= retryConfig.maxRetries(); attempt++) {
      try {
        // Idempotency Key管理
        HttpRequest enhancedRequest = httpRequest;
        if (retryConfig.idempotencyRequired()) {
          if (idempotencyKey == null) {
            idempotencyKey = idempotencyManager.generateKey(httpRequest);
          }
          enhancedRequest = idempotencyManager.addIdempotencyKey(httpRequest, idempotencyKey);
        }

        // OAuth認証適用
        HttpRequestResult result = (oAuthConfig != null)
          ? executeWithOAuth(enhancedRequest, oAuthConfig)
          : execute(enhancedRequest);

        // 成功時の処理
        if (result.isSuccess()) {
          log.info("HTTP request succeeded: uri={}, attempt={}, statusCode={}, correlationId={}",
            httpRequest.uri(), attempt + 1, result.statusCode(), correlationId);
          return result;
        }

        // リトライ判定
        if (!isRetryableResult(result, retryConfig)) {
          log.warn("HTTP request failed with non-retryable error: uri={}, statusCode={}, attempt={}, correlationId={}",
            httpRequest.uri(), result.statusCode(), attempt + 1, correlationId);
          return result;
        }

        // 最大リトライ回数チェック
        if (attempt == retryConfig.maxRetries()) {
          log.error("HTTP request failed after max retries: uri={}, maxRetries={}, finalStatusCode={}, correlationId={}",
            httpRequest.uri(), retryConfig.maxRetries(), result.statusCode(), correlationId);
          return createMaxRetriesExceededResult(result, retryConfig);
        }

        // リトライ前の待機
        Duration delay = calculateBackoffDelay(attempt, retryConfig);
        log.warn("HTTP request failed, retrying: uri={}, attempt={}, statusCode={}, nextDelay={}, correlationId={}",
          httpRequest.uri(), attempt + 1, result.statusCode(), delay, correlationId);

        waitBeforeRetry(delay);

      } catch (Exception e) {
        // 例外のリトライ判定
        if (!isRetryableException(e, retryConfig)) {
          log.error("HTTP request failed with non-retryable exception: uri={}, attempt={}, correlationId={}",
            httpRequest.uri(), attempt + 1, correlationId, e);
          throw new HttpRequestExecutionException("Non-retryable error occurred", e);
        }

        // 最大リトライ回数チェック
        if (attempt == retryConfig.maxRetries()) {
          log.error("HTTP request failed with exception after max retries: uri={}, maxRetries={}, correlationId={}",
            httpRequest.uri(), retryConfig.maxRetries(), correlationId, e);
          throw new HttpRequestExecutionException("Max retries exceeded", e);
        }

        // リトライ前の待機
        Duration delay = calculateBackoffDelay(attempt, retryConfig);
        log.warn("HTTP request failed with exception, retrying: uri={}, attempt={}, nextDelay={}, correlationId={}",
          httpRequest.uri(), attempt + 1, delay, correlationId, e);

        waitBeforeRetry(delay);
      }
    }

    // このコードに到達することはないが、念のため
    throw new HttpRequestExecutionException("Unexpected retry loop termination");
  }

  // 3. リトライ判定ロジック
  private boolean isRetryableResult(HttpRequestResult result, HttpRetryConfiguration config) {
    // 4xx エラーは通常リトライしない（401, 403, 404 etc.）
    if (result.isClientError()) {
      // ただし 408 (Request Timeout) と 429 (Too Many Requests) はリトライ対象
      return config.retryableStatusCodes().contains(result.statusCode());
    }

    // 5xx エラーは通常リトライ対象
    if (result.isServerError()) {
      return config.retryableStatusCodes().contains(result.statusCode());
    }

    return false;
  }

  private boolean isRetryableException(Exception e, HttpRetryConfiguration config) {
    return config.retryableExceptions().stream()
      .anyMatch(retryableClass -> retryableClass.isAssignableFrom(e.getClass()));
  }

  // 4. バックオフ遅延計算
  private Duration calculateBackoffDelay(int attempt, HttpRetryConfiguration config) {
    if (attempt < config.backoffDelays().length) {
      return config.backoffDelays()[attempt];
    }

    // 設定された遅延を超える場合は最後の遅延を使用
    return config.backoffDelays()[config.backoffDelays().length - 1];
  }

  // 5. 実際の待機処理
  private void waitBeforeRetry(Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HttpRequestExecutionException("Retry wait interrupted", e);
    }
  }

  // 6. 最大リトライ回数超過時のレスポンス生成
  private HttpRequestResult createMaxRetriesExceededResult(
      HttpRequestResult lastResult,
      HttpRetryConfiguration config) {

    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("error", "max_retries_exceeded");
    errorBody.put("error_description",
      String.format("Request failed after %d retries", config.maxRetries()));
    errorBody.put("max_retries", config.maxRetries());
    errorBody.put("final_status_code", lastResult.statusCode());
    errorBody.put("retryable", false);

    return new HttpRequestResult(
      lastResult.statusCode(),
      lastResult.headers(),
      JsonNodeWrapper.fromObject(errorBody)
    );
  }

  // 7. 相関ID生成（トレーシング用）
  private String generateCorrelationId() {
    return "req_" + UUID.randomUUID().toString().substring(0, 8);
  }
}
```

## 3. IdempotencyKeyManager 実装

```java
@Component
public class IdempotencyKeyManager {
  private final LoggerWrapper log = LoggerWrapper.getLogger(IdempotencyKeyManager.class);

  public String generateKey(HttpRequest request) {
    // リクエストの内容に基づいたユニークキー生成
    String requestHash = createRequestHash(request);
    return "idem_" + requestHash + "_" + UUID.randomUUID().toString().substring(0, 8);
  }

  public HttpRequest addIdempotencyKey(HttpRequest request, String idempotencyKey) {
    // 既存のヘッダーをコピー
    HttpRequest.Builder builder = HttpRequest.newBuilder()
      .uri(request.uri())
      .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));

    // 既存ヘッダーをコピー
    request.headers().map().forEach((name, values) -> {
      for (String value : values) {
        builder.header(name, value);
      }
    });

    // Idempotency-Key ヘッダー追加
    builder.header("Idempotency-Key", idempotencyKey);

    log.debug("Added Idempotency-Key: {} to request: {}", idempotencyKey, request.uri());
    return builder.build();
  }

  private String createRequestHash(HttpRequest request) {
    // リクエストのURL、メソッド、ボディのハッシュを生成
    String content = request.method() + ":" + request.uri().toString();
    return Integer.toHexString(content.hashCode());
  }
}
```

## 4. 使用例: SsfHookExecutor での適用

```java
public class SsfHookExecutor implements SecurityEventHook {

  private SecurityEventHookResult send(
      String endpoint,
      SecurityEventToken securityEventToken,
      SharedSignalFrameworkTransmissionConfig transmissionConfig) {

    HttpRequest httpRequest = HttpRequest.newBuilder()
      .uri(URI.create(endpoint))
      .header("Content-Type", "application/secevent+jwt")
      .header("Accept", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(securityEventToken.value()))
      .build();

    // リトライ設定（設定ファイルから取得 or デフォルト）
    HttpRetryConfiguration retryConfig = transmissionConfig.retryConfiguration()
      .orElse(HttpRetryConfiguration.defaultRetry());

    try {
      HttpRequestResult result;
      if (transmissionConfig.oauthAuthorization() != null) {
        // OAuth + リトライ
        result = httpRequestExecutor.executeWithRetry(
          httpRequest,
          transmissionConfig.oauthAuthorization(),
          retryConfig
        );
      } else {
        // リトライのみ
        result = httpRequestExecutor.executeWithRetry(httpRequest, retryConfig);
      }

      return handleResponse(result, endpoint);

    } catch (HttpRequestExecutionException e) {
      log.error("SSF transmission failed completely: endpoint={}", endpoint, e);
      return SecurityEventHookResult.failure(type(), Map.of(
        "error", "transmission_failed",
        "error_description", e.getMessage(),
        "retryable", false
      ));
    }
  }
}
```

## 5. 設定例

### JSON設定ファイル
```json
{
  "url": "https://example.com/webhook",
  "oauth_authorization": { "..." },
  "retry_configuration": {
    "max_retries": 3,
    "backoff_delays": ["1s", "5s", "30s"],
    "retryable_status_codes": [408, 429, 500, 502, 503, 504],
    "retryable_exceptions": ["IOException", "ConnectException", "SocketTimeoutException"],
    "idempotency_required": true,
    "strategy": "EXPONENTIAL_BACKOFF"
  }
}
```

## 6. ログ出力例

```
2025-09-23 10:30:45.123 INFO  - Starting HTTP request with retry: uri=https://example.com/webhook, maxRetries=3, correlationId=req_abc12345
2025-09-23 10:30:45.500 WARN  - HTTP request failed, retrying: uri=https://example.com/webhook, attempt=1, statusCode=503, nextDelay=PT1S, correlationId=req_abc12345
2025-09-23 10:30:46.501 WARN  - HTTP request failed, retrying: uri=https://example.com/webhook, attempt=2, statusCode=502, nextDelay=PT5S, correlationId=req_abc12345
2025-09-23 10:30:51.502 INFO  - HTTP request succeeded: uri=https://example.com/webhook, attempt=3, statusCode=200, correlationId=req_abc12345
```

## 7. テスト実装例

```java
@Test
void testExecuteWithRetry_SuccessAfterRetries() {
  // Mock HttpClient の設定: 1回目503、2回目502、3回目200
  when(mockHttpClient.send(any(), any()))
    .thenReturn(createResponse(503))
    .thenReturn(createResponse(502))
    .thenReturn(createResponse(200));

  HttpRetryConfiguration config = HttpRetryConfiguration.defaultRetry();
  HttpRequest request = HttpRequest.newBuilder(URI.create("http://test")).build();

  HttpRequestResult result = executor.executeWithRetry(request, config);

  assertEquals(200, result.statusCode());
  verify(mockHttpClient, times(3)).send(any(), any());
}
```

この実装により、Issue #248の要件（最大3回リトライ、指数バックオフ、Idempotency対応）を満たしつつ、既存のHttpRequestExecutorアーキテクチャと統合されたリトライ機能が実現されます。