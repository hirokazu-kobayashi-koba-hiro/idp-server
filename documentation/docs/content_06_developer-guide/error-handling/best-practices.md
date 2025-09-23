# エラーハンドリング ベストプラクティス

Identity Verification システムでエラーハンドリングを実装する際のベストプラクティスガイドです。

## 基本原則

### 1. **Fail Fast vs Fail Safe**

適切な戦略選択:

```java
// ✅ Request phase: Fail Fast
public ValidationResult validateRequest(Request request) {
  if (!isValidFormat(request)) {
    // 不正リクエストは即座に拒否
    throw new RequestValidationException("Invalid request format");
  }
  return ValidationResult.success();
}

// ✅ Pre-hook phase: Fail Safe
public Map<String, Object> resolveAdditionalParams(Config config) {
  try {
    return externalService.fetch(config);
  } catch (Exception e) {
    log.warn("External service failed, using fallback", e);
    // フォールバック値で処理継続
    return createFallbackResponse();
  }
}
```

### 2. **適切な例外の使い分け**

```java
// ✅ チェック例外: 回復可能なエラー
public class ExternalServiceException extends Exception {
  public ExternalServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}

// ✅ 非チェック例外: プログラムエラー
public class ConfigurationException extends RuntimeException {
  public ConfigurationException(String message) {
    super(message);
  }
}
```

### 3. **セキュリティ重視の情報開示**

```java
// ❌ 内部情報の漏洩
catch (DatabaseException e) {
  return ErrorResponse.builder()
    .message("Database error: " + e.getMessage()) // ← 内部詳細が漏洩
    .stackTrace(e.getStackTrace())                // ← セキュリティリスク
    .build();
}

// ✅ 適切な情報秘匿
catch (DatabaseException e) {
  log.error("Database operation failed", e);     // ← 詳細はログのみ
  return ErrorResponse.builder()
    .message("Internal service error")           // ← 汎用メッセージ
    .errorCode("IVE-500-001")                   // ← トレース可能なコード
    .retryAfter(30)                             // ← 有用な情報のみ
    .build();
}
```

## コーディングパターン

### 1. **統一エラーハンドラーの使用**

```java
// ✅ 推奨パターン
public class HttpRequestParameterResolver implements AdditionalRequestParameterResolver {

  @Override
  public Map<String, Object> resolve(...) {
    return PreHookErrorHandler.handleResolverError("http_request", () -> {
      HttpRequestResult result = httpRequestExecutor.execute(configuration, baseParams);
      return createSuccessResponse(result);
    });
  }
}

// ❌ 非推奨パターン
public class HttpRequestParameterResolver implements AdditionalRequestParameterResolver {

  @Override
  public Map<String, Object> resolve(...) {
    try {
      // 個別のエラーハンドリング実装（統一性なし）
      HttpRequestResult result = httpRequestExecutor.execute(configuration, baseParams);
      return createSuccessResponse(result);
    } catch (Exception e) {
      // 各クラスで独自実装（保守性低下）
      log.error("Some error", e);
      return Collections.emptyMap();
    }
  }
}
```

### 2. **Builder パターンでのエラーレスポンス構築**

```java
// ✅ 推奨: 可読性の高いBuilder
public Map<String, Object> createErrorResponse(String errorType, String description, Exception cause) {
  return IdentityVerificationErrorResponse.builder()
      .error(true)
      .errorType(errorType)
      .errorDescription(description)
      .addDetail("phase", "pre_hook")
      .addDetail("component", "http_request_resolver")
      .addDetail("retryable", isRetryableError(cause))
      .addDetail("correlation_id", getCurrentCorrelationId())
      .fallbackData(createDefaultFallback())
      .build()
      .toMap();
}
```

### 3. **コンテキスト保持**

```java
// ✅ 推奨: MDCでコンテキスト管理
public class CorrelationIdHandler {
  private static final String CORRELATION_ID_KEY = "correlationId";

  public static void setCorrelationId(String correlationId) {
    MDC.put(CORRELATION_ID_KEY, correlationId);
  }

  public static String getCorrelationId() {
    return MDC.get(CORRELATION_ID_KEY);
  }

  public static void clearCorrelationId() {
    MDC.remove(CORRELATION_ID_KEY);
  }
}

// 使用例
public void processRequest(Request request) {
  String correlationId = generateCorrelationId();
  CorrelationIdHandler.setCorrelationId(correlationId);
  try {
    // 処理実行
  } finally {
    CorrelationIdHandler.clearCorrelationId();
  }
}
```

## ログ記録のベストプラクティス

### 1. **構造化ログの使用**

```java
// ✅ 推奨: 構造化ログ
public void logError(String phase, String component, Exception error) {
  log.error("Error occurred in identity verification",
      keyValue("phase", phase),
      keyValue("component", component),
      keyValue("error_type", classifyError(error).name()),
      keyValue("correlation_id", CorrelationIdHandler.getCorrelationId()),
      keyValue("retryable", isRetryableError(error)),
      error);
}

// ❌ 非推奨: 非構造化ログ
public void logError(String phase, String component, Exception error) {
  log.error("Error in " + phase + " phase, component " + component + ": " + error.getMessage(), error);
}
```

### 2. **ログレベルの適切な使い分け**

```java
public class ErrorLogger {

  public void logByErrorType(IdentityVerificationErrorType errorType, String message, Exception e) {
    switch (errorType) {
      case CONFIGURATION_ERROR, AUTHENTICATION_ERROR -> {
        // システム管理者の即座対応が必要
        log.error("Critical error requiring immediate attention: {}", message, e);
      }

      case EXTERNAL_SERVICE_ERROR, TIMEOUT_ERROR -> {
        // 自動復旧可能だが監視が必要
        log.warn("Recoverable error occurred: {}", message, e);
      }

      case REQUEST_VALIDATION_FAILED -> {
        // 想定内のクライアントエラー
        log.info("Client request validation failed: {}", message);
      }

      default -> {
        // 分類不明なエラー
        log.error("Unclassified error: {}", message, e);
      }
    }
  }
}
```

### 3. **機密情報の秘匿**

```java
// ✅ 推奨: 機密情報をマスク
public void logHttpRequest(HttpRequest request, HttpResponse response) {
  Map<String, Object> logData = Map.of(
      "method", request.getMethod(),
      "url", maskSensitiveUrl(request.getUrl()),
      "status_code", response.getStatusCode(),
      "headers", maskSensitiveHeaders(request.getHeaders()),
      "response_size", response.getBodySize()
  );

  log.info("HTTP request completed", logData);
}

private String maskSensitiveUrl(String url) {
  // API キーやトークンをマスク
  return url.replaceAll("(api_key|token)=[^&]*", "$1=***");
}
```

## テスト戦略

### 1. **エラーシナリオの網羅的テスト**

```java
@Test
class HttpRequestParameterResolverErrorHandlingTest {

  @Test
  void shouldHandleNetworkError() {
    // Given
    when(httpRequestExecutor.execute(any(), any()))
        .thenThrow(new ConnectTimeoutException("Connection timeout"));

    // When
    Map<String, Object> result = resolver.resolve(tenant, user, app, request, attrs, config);

    // Then
    assertThat(result)
        .containsEntry("error", true)
        .containsEntry("error_type", "TIMEOUT_ERROR")
        .containsKey("fallback_data");

    verify(logger).warn(contains("HTTP request failed"), any(Exception.class));
  }

  @Test
  void shouldHandleAuthenticationError() {
    // Given
    when(httpRequestExecutor.execute(any(), any()))
        .thenThrow(new AuthenticationException("Invalid credentials"));

    // When
    Map<String, Object> result = resolver.resolve(tenant, user, app, request, attrs, config);

    // Then
    assertThat(result)
        .containsEntry("error", true)
        .containsEntry("error_type", "AUTHENTICATION_ERROR")
        .containsEntry("retryable", false);

    verify(logger).error(contains("Authentication failed"), any(Exception.class));
  }
}
```

### 2. **モックを使った外部依存の分離**

```java
@TestConfiguration
public class ErrorHandlingTestConfiguration {

  @Bean
  @Primary
  public HttpRequestExecutor mockHttpRequestExecutor() {
    return Mockito.mock(HttpRequestExecutor.class);
  }

  @Bean
  @Primary
  public CircuitBreaker mockCircuitBreaker() {
    return CircuitBreaker.ofDefaults("test");
  }
}
```

### 3. **カオスエンジニアリング的アプローチ**

```java
@ActiveProfiles("chaos-testing")
@SpringBootTest
class ChaosErrorHandlingTest {

  @Test
  void shouldHandleRandomServiceFailures() {
    // ランダムに外部サービスを失敗させる
    ChaosMonkey.enableFor(ExternalService.class)
        .withFailureRate(0.3)
        .withLatency(Duration.ofSeconds(2));

    // 複数回実行して回復力を確認
    for (int i = 0; i < 100; i++) {
      Result result = identityVerificationService.process(createTestRequest());
      assertThat(result.isCompleted()).isTrue(); // 何らかの結果は返る
    }
  }
}
```

## パフォーマンス考慮事項

### 1. **エラーハンドリングの軽量化**

```java
// ✅ 推奨: 軽量なエラー分類
public IdentityVerificationErrorType classifyError(Exception e) {
  // instanceof チェックは高速
  if (e instanceof ConnectTimeoutException || e instanceof SocketTimeoutException) {
    return TIMEOUT_ERROR;
  }
  if (e instanceof AuthenticationException) {
    return AUTHENTICATION_ERROR;
  }
  // フォールバック
  return INTERNAL_ERROR;
}

// ❌ 非推奨: 重い処理
public IdentityVerificationErrorType classifyError(Exception e) {
  // スタックトレース解析は重い
  String stackTrace = ExceptionUtils.getStackTrace(e);
  if (stackTrace.contains("timeout")) {
    return TIMEOUT_ERROR;
  }
  // ...
}
```

### 2. **非同期エラー処理**

```java
// ✅ 推奨: 非ブロッキングエラー処理
@Async("errorHandlingExecutor")
public CompletableFuture<Void> processErrorAsync(ErrorEvent errorEvent) {
  try {
    // メトリクス更新
    errorMetrics.recordError(errorEvent);

    // アラート送信
    if (errorEvent.isCritical()) {
      alertService.sendAlert(errorEvent);
    }

    // 詳細分析
    errorAnalysisService.analyze(errorEvent);

  } catch (Exception e) {
    log.error("Error processing error event", e);
  }
  return CompletableFuture.completedFuture(null);
}
```

### 3. **メモリ効率的なエラー情報**

```java
// ✅ 推奨: 必要最小限の情報
public class LightweightErrorDetails {
  private final String errorType;
  private final String message;
  private final long timestamp;
  private final String correlationId;

  // 重いオブジェクトは避ける（Throwable等）
}

// ❌ 非推奨: メモリを大量消費
public class HeavyErrorDetails {
  private final Throwable fullException;  // スタックトレース全体
  private final String fullRequestBody;   // リクエスト全体
  private final Map<String, Object> allContext; // 全コンテキスト
}
```

## 運用監視

### 1. **エラー傾向の分析**

```java
@Component
public class ErrorTrendAnalyzer {

  @Scheduled(fixedRate = 300000) // 5分毎
  public void analyzeErrorTrends() {
    ErrorTrendReport report = errorMetrics.generateTrendReport(Duration.ofHours(1));

    if (report.hasAnomalies()) {
      log.warn("Error trend anomaly detected: {}", report.getAnomalies());
      alertService.sendTrendAlert(report);
    }
  }
}
```

### 2. **ヘルスチェックとの連携**

```java
@Component
public class IdentityVerificationHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    try {
      // 各フェーズの健全性チェック
      boolean preHookHealthy = checkPreHookHealth();
      boolean executionHealthy = checkExecutionHealth();

      if (preHookHealthy && executionHealthy) {
        return Health.up()
            .withDetail("pre_hook_error_rate", getPreHookErrorRate())
            .withDetail("execution_error_rate", getExecutionErrorRate())
            .build();
      } else {
        return Health.down()
            .withDetail("failing_phases", getFailingPhases())
            .build();
      }
    } catch (Exception e) {
      return Health.down()
          .withException(e)
          .build();
    }
  }
}
```

これらのベストプラクティスに従うことで、保守性が高く、運用しやすいエラーハンドリングシステムを構築できます。