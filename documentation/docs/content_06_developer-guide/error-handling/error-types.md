# エラー分類体系

Identity Verification システムで発生するエラーの分類と対応方針を定義します。

## エラー分類一覧

### 1. Configuration Errors（設定エラー）

| エラータイプ | 説明 | 対応方針 | 復旧可能性 |
|-------------|------|---------|-----------|
| `CONFIGURATION_ERROR` | 設定ファイルの不正 | FAIL_FAST | ❌ 手動修正必要 |
| `MISSING_RESOLVER` | 必要なResolver未定義 | LOG_WARN + SKIP | ⚠️ 機能制限で継続 |
| `INVALID_SCHEMA` | JSONスキーマ不正 | FAIL_FAST | ❌ 手動修正必要 |

**例**:
```java
// 未定義resolverの検出
if (resolver == null) {
  log.warn("Resolver undefined: {}", resolverType);
  // 処理をスキップしてエラーレスポンス返却
  return createMissingResolverResponse(resolverType);
}
```

### 2. Validation Errors（バリデーションエラー）

| エラータイプ | 説明 | 対応方針 | 復旧可能性 |
|-------------|------|---------|-----------|
| `REQUEST_VALIDATION_FAILED` | リクエスト形式エラー | FAIL_FAST | ❌ クライアント修正必要 |
| `VERIFICATION_FAILED` | 事前検証失敗 | CONDITIONAL | ⚠️ 条件により継続 |
| `CONDITION_EVALUATION_FAILED` | 条件評価エラー | DEFAULT_VALUE | ✅ デフォルト動作 |

**例**:
```java
// リクエストバリデーション
JsonSchemaValidationResult result = validator.validate(request);
if (!result.isValid()) {
  return ValidationErrorResponse.builder()
    .errorType(REQUEST_VALIDATION_FAILED)
    .validationErrors(result.errors())
    .build();
}
```

### 3. External Service Errors（外部サービスエラー）

| エラータイプ | 説明 | 対応方針 | 復旧可能性 |
|-------------|------|---------|-----------|
| `EXTERNAL_SERVICE_ERROR` | 外部サービス障害 | RETRY + FALLBACK | ✅ 自動復旧可能 |
| `NETWORK_ERROR` | ネットワーク接続エラー | RETRY + TIMEOUT | ✅ 自動復旧可能 |
| `AUTHENTICATION_ERROR` | 認証失敗 | NO_RETRY + ALERT | ❌ 設定確認必要 |
| `TIMEOUT_ERROR` | タイムアウト | RETRY + FALLBACK | ✅ 自動復旧可能 |

**例**:
```java
// 外部サービスエラーハンドリング
try {
  HttpRequestResult result = httpClient.execute(request);
  return result;
} catch (ConnectTimeoutException e) {
  return handleRetryableError(TIMEOUT_ERROR, e, 3);
} catch (AuthenticationException e) {
  return handleNonRetryableError(AUTHENTICATION_ERROR, e);
}
```

### 4. System Errors（システムエラー）

| エラータイプ | 説明 | 対応方針 | 復旧可能性 |
|-------------|------|---------|-----------|
| `INTERNAL_ERROR` | 内部処理エラー | LOG_ERROR + FALLBACK | ⚠️ 調査必要 |
| `MAPPING_ERROR` | データマッピングエラー | PARTIAL_DATA | ⚠️ 部分的に継続 |
| `STORAGE_ERROR` | ストレージエラー | RETRY + ALERT | ✅ 自動復旧可能 |

## エラー重要度レベル

### Critical (緊急) 🔴
- システム全体に影響
- 即座の対応が必要
- 自動アラート発報

**該当エラー**:
- `CONFIGURATION_ERROR`
- `AUTHENTICATION_ERROR`
- 外部サービス完全停止

### High (高) 🟡
- 機能の一部に影響
- 24時間以内の対応
- 監視強化

**該当エラー**:
- `EXTERNAL_SERVICE_ERROR`
- `STORAGE_ERROR`
- 高頻度の`TIMEOUT_ERROR`

### Medium (中) 🟢
- 限定的な影響
- 通常の運用対応
- 定期レビュー

**該当エラー**:
- `MISSING_RESOLVER`
- `VERIFICATION_FAILED`
- 散発的なネットワークエラー

### Low (低) ⚪
- 機能に影響なし
- 情報として記録
- 週次レビュー

**該当エラー**:
- `REQUEST_VALIDATION_FAILED`（不正リクエスト）
- 想定内の条件分岐

## フェーズ別エラー対応マトリックス

| エラータイプ → | request | pre_hook | execution | post_hook | transition | store | response |
|---------------|---------|----------|-----------|-----------|------------|-------|----------|
| **Configuration** | FAIL | SKIP | FAIL | SKIP | DEFAULT | PARTIAL | MINIMAL |
| **Validation** | FAIL | WARN | FAIL | SKIP | DEFAULT | PARTIAL | MINIMAL |
| **External Service** | FAIL | FALLBACK | FAIL_DETAIL | SKIP | DEFAULT | PARTIAL | MINIMAL |
| **System** | FAIL | FALLBACK | FAIL_DETAIL | SKIP | DEFAULT | PARTIAL | MINIMAL |

**凡例**:
- **FAIL**: 処理停止・エラーレスポンス
- **SKIP**: ログ記録・処理継続
- **FALLBACK**: フォールバック値使用
- **DEFAULT**: デフォルト動作実行
- **PARTIAL**: 部分データで継続
- **MINIMAL**: 最小限レスポンス

## リトライ戦略

### 指数バックオフ

```java
public class RetryStrategy {
  private static final int MAX_RETRIES = 3;
  private static final long BASE_DELAY = 1000; // 1秒

  public <T> T executeWithRetry(Supplier<T> operation) {
    Exception lastException = null;

    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        return operation.get();
      } catch (RetryableException e) {
        lastException = e;
        if (attempt < MAX_RETRIES) {
          long delay = BASE_DELAY * (1L << attempt); // 1s, 2s, 4s
          sleep(delay);
        }
      }
    }
    throw new MaxRetriesExceededException(lastException);
  }
}
```

### リトライ対象判定

```java
public boolean isRetryableError(IdentityVerificationErrorType errorType) {
  return switch (errorType) {
    case EXTERNAL_SERVICE_ERROR,
         NETWORK_ERROR,
         TIMEOUT_ERROR,
         STORAGE_ERROR -> true;

    case AUTHENTICATION_ERROR,
         CONFIGURATION_ERROR,
         REQUEST_VALIDATION_FAILED -> false;

    default -> false;
  };
}
```

## サーキットブレーカー

外部サービス呼び出しの保護:

```java
@Component
public class ExternalServiceCircuitBreaker {
  private final CircuitBreaker circuitBreaker;

  public ExternalServiceCircuitBreaker() {
    this.circuitBreaker = CircuitBreaker.ofDefaults("external-service");
    circuitBreaker.getEventPublisher()
        .onStateTransition(event ->
            log.info("Circuit breaker state: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
  }

  public <T> T executeWithCircuitBreaker(Supplier<T> operation) {
    return circuitBreaker.executeSupplier(operation);
  }
}
```

## メトリクス定義

### エラー発生率

```java
// エラー種別ごとの発生数
Counter.builder("identity_verification_errors_total")
    .tag("phase", phase)
    .tag("error_type", errorType.name())
    .tag("severity", severity.name())
    .register(meterRegistry);

// 成功率
Gauge.builder("identity_verification_success_rate")
    .tag("phase", phase)
    .register(meterRegistry, this, obj -> calculateSuccessRate());
```

### レスポンス時間

```java
// フェーズ別処理時間
Timer.builder("identity_verification_phase_duration")
    .tag("phase", phase)
    .tag("status", success ? "success" : "error")
    .register(meterRegistry);
```

## アラート設定

### Critical アラート

```yaml
alerts:
  - name: IdentityVerificationHighErrorRate
    condition: rate(identity_verification_errors_total[5m]) > 0.1
    severity: critical
    description: "Identity verification error rate exceeded 10%"

  - name: ExternalServiceDown
    condition: up{job="external-identity-service"} == 0
    severity: critical
    description: "External identity service is down"
```

### Warning アラート

```yaml
alerts:
  - name: IdentityVerificationSlowResponse
    condition: histogram_quantile(0.95, identity_verification_phase_duration) > 30
    severity: warning
    description: "95th percentile response time > 30s"
```

この分類体系により、エラーの性質に応じた適切な対応と監視が可能になります。