# 統一エラーハンドリング戦略

Identity Verification システム全体で一貫したエラーハンドリングを実現するための統一戦略を定義します。

## 設計原則

### 1. **フェーズ特性に応じた戦略**
各フェーズの役割と重要性に応じて、適切なエラーハンドリング戦略を適用

### 2. **セキュリティ重視**
内部システム情報の漏洩防止と適切なエラー情報の提供

### 3. **復旧可能性**
可能な限り処理を継続し、適切なフォールバック値を提供

### 4. **観測可能性**
適切なログ記録とメトリクス収集

## エラー分類体系

```java
public enum IdentityVerificationErrorType {
  // Configuration errors - 設定エラー
  CONFIGURATION_ERROR,      // 設定ファイルの問題
  MISSING_RESOLVER,         // 必要なResolverが未定義
  INVALID_SCHEMA,          // JSONスキーマの問題

  // Validation errors - バリデーションエラー
  REQUEST_VALIDATION_FAILED, // リクエスト形式エラー
  VERIFICATION_FAILED,       // 事前検証失敗
  CONDITION_EVALUATION_FAILED, // 条件評価エラー

  // External service errors - 外部サービスエラー
  EXTERNAL_SERVICE_ERROR,    // 外部サービス呼び出し失敗
  NETWORK_ERROR,            // ネットワーク関連エラー
  AUTHENTICATION_ERROR,     // 認証エラー
  TIMEOUT_ERROR,           // タイムアウト

  // System errors - システムエラー
  INTERNAL_ERROR,          // 内部処理エラー
  MAPPING_ERROR,           // データマッピングエラー
  STORAGE_ERROR           // ストレージエラー
}
```

## フェーズ別エラー戦略

| フェーズ | 戦略 | 理由 | 実装方針 |
|---------|------|------|---------|
| **request** | **FAIL_FAST** | リクエスト不正は即座に拒否すべき | 既存実装維持 |
| **pre_hook** | **RESILIENT** | 事前処理失敗でも本処理は継続すべき | エラー時フォールバック値使用 |
| **execution** | **FAIL_WITH_DETAILS** | メイン処理の失敗は詳細報告が必要 | 既存実装維持・拡張 |
| **post_hook** | **BEST_EFFORT** | 事後処理失敗でも結果は返すべき | ログ記録 + 処理継続 |
| **transition** | **CONDITIONAL** | 条件によって継続/停止を判断 | 条件評価エラー時はデフォルト動作 |
| **store** | **FALLBACK** | 保存失敗時は最低限の情報を保持 | 簡易フォーマットでフォールバック |
| **response** | **GRACEFUL** | レスポンス生成失敗でも何らかの応答 | 最小限レスポンス生成 |

## 統一エラーレスポンス構造

### 基本構造
```json
{
  "error": true,
  "error_type": "EXTERNAL_SERVICE_ERROR",
  "error_description": "External verification service is temporarily unavailable",
  "error_details": {
    "phase": "pre_hook",
    "component": "http_request_resolver",
    "timestamp": "2025-09-23T10:30:00Z",
    "correlation_id": "req-12345",
    "retryable": true,
    "retry_after": 30
  },
  "fallback_data": {
    "response_status": 0,
    "response_headers": {},
    "response_body": {}
  }
}
```

### セキュリティ考慮事項
- **内部情報の秘匿**: スタックトレースや内部パスは含めない
- **適切な情報提供**: トラブルシューティングに必要な最小限の情報
- **ログとの分離**: 詳細情報はログに記録、レスポンスは簡潔に

## 実装パターン

### 1. **統一エラーハンドラー**

```java
public class IdentityVerificationErrorHandler {

  public static <T> ErrorHandlingResult<T> handlePhaseError(
      String phase, String component, Supplier<T> operation, T fallback) {
    try {
      T result = operation.get();
      return ErrorHandlingResult.success(result);
    } catch (Exception e) {
      return handleException(phase, component, e, fallback);
    }
  }

  private static <T> ErrorHandlingResult<T> handleException(
      String phase, String component, Exception e, T fallback) {

    IdentityVerificationErrorType errorType = classifyError(e);

    // ログ記録
    if (isRetryableError(errorType)) {
      log.warn("Retryable error in {} phase, component {}: {}",
               phase, component, e.getMessage());
    } else {
      log.error("Non-retryable error in {} phase, component {}",
                phase, component, e);
    }

    // エラーレスポンス生成
    ErrorDetails errorDetails = ErrorDetails.builder()
        .errorType(errorType)
        .phase(phase)
        .component(component)
        .retryable(isRetryableError(errorType))
        .correlationId(getCurrentCorrelationId())
        .build();

    return ErrorHandlingResult.error(errorDetails, fallback);
  }
}
```

### 2. **フェーズ固有ハンドラー**

```java
// Pre-hook専用
public class PreHookErrorHandler extends IdentityVerificationErrorHandler {
  public static Map<String, Object> handleResolverError(
      String resolverType, Supplier<Map<String, Object>> operation) {

    Map<String, Object> fallback = createDefaultResponse();

    return handlePhaseError("pre_hook", resolverType, operation, fallback)
        .getValueOrFallback();
  }

  private static Map<String, Object> createDefaultResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("error", true);
    response.put("error_type", "RESOLVER_FAILED");
    response.put("response_status", 0);
    response.put("response_headers", Collections.emptyMap());
    response.put("response_body", Collections.emptyMap());
    return response;
  }
}
```

### 3. **設定ベースエラー制御**

```java
@Configuration
public class ErrorHandlingConfiguration {

  @Value("${identity.verification.error.pre-hook.strategy:RESILIENT}")
  private ErrorStrategy preHookStrategy;

  @Value("${identity.verification.error.pre-hook.retry-count:3}")
  private int preHookRetryCount;

  @Value("${identity.verification.error.execution.security-sanitize:true}")
  private boolean executionSecuritySanitize;
}
```

## メトリクスとモニタリング

### 1. **エラーメトリクス**
```java
// エラー発生数
Counter.builder("identity_verification_errors")
    .tag("phase", phase)
    .tag("component", component)
    .tag("error_type", errorType.name())
    .register(meterRegistry);

// エラー率
Timer.Sample.start(meterRegistry)
    .stop(Timer.builder("identity_verification_operation")
        .tag("phase", phase)
        .tag("status", success ? "success" : "error")
        .register(meterRegistry));
```

### 2. **アラート条件**
- Pre-hook エラー率 > 10%
- Execution エラー率 > 5%
- 外部サービスエラー > 3回/分

## ログ戦略

### 1. **構造化ログ**
```json
{
  "timestamp": "2025-09-23T10:30:00Z",
  "level": "ERROR",
  "logger": "IdentityVerificationErrorHandler",
  "message": "External service error in pre_hook phase",
  "phase": "pre_hook",
  "component": "http_request_resolver",
  "error_type": "EXTERNAL_SERVICE_ERROR",
  "correlation_id": "req-12345",
  "retryable": true,
  "exception": "HttpRequestException: Connection timeout"
}
```

### 2. **ログレベル指針**
- **ERROR**: 業務継続に影響するエラー
- **WARN**: 処理継続可能だが注意が必要
- **INFO**: 正常な処理フロー
- **DEBUG**: 詳細なトラブルシューティング情報

## 段階的移行計画

### Phase 1: 緊急対応 (即座実装)
- HttpRequestParameterResolverのエラーハンドリング実装
- 基本的なフォールバック機能

### Phase 2: 統一基盤 (1-2週間)
- 統一エラーハンドラーライブラリ作成
- 既存実装の統一形式への移行

### Phase 3: 高度化 (1ヶ月)
- 設定ベースエラー制御
- メトリクスとモニタリング強化
- 自動復旧機能

## テスト戦略

### 1. **エラーシナリオテスト**
- ネットワーク障害シミュレーション
- 外部サービス異常レスポンス
- 設定エラーケース

### 2. **復旧テスト**
- フォールバック動作確認
- リトライ機能検証
- グレースフル停止テスト

この統一戦略により、Identity Verification システム全体で一貫性のあるエラーハンドリングを実現し、システムの信頼性とユーザビリティを向上させます。