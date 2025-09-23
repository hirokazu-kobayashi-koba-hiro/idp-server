# executeWithRetry() と設定ベースメソッドの関係

## 現在のメソッド階層

```java
public class HttpRequestExecutor {

  // 1. 設定ベースメソッド（既存）
  public HttpRequestResult execute(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {
    // 動的マッピング・OAuth・HMAC認証・HttpRequest構築
    HttpRequest httpRequest = buildHttpRequest(configuration, httpRequestBaseParams);
    return execute(httpRequest); // 最終的にこれを呼ぶ
  }

  // 2. 直接HttpRequestメソッド（既存）
  public HttpRequestResult execute(HttpRequest httpRequest) {
    // HttpClientで実際のHTTP実行
  }

  // 3. OAuth対応直接メソッド（Issue #479で追加）
  public HttpRequestResult executeWithOAuth(
      HttpRequest httpRequest, OAuthAuthorizationConfiguration oAuthConfig) {
    // OAuth認証ヘッダー追加 + execute(httpRequest)
  }

  // 4. リトライ対応メソッド（Issue #248で追加予定）
  public HttpRequestResult executeWithRetry(
      HttpRequest httpRequest, HttpRetryConfiguration retryConfig) {
    // リトライロジック + execute(httpRequest)
  }

  // 5. OAuth + リトライメソッド（Issue #248で追加予定）
  public HttpRequestResult executeWithRetry(
      HttpRequest httpRequest,
      OAuthAuthorizationConfiguration oAuthConfig,
      HttpRetryConfiguration retryConfig) {
    // リトライロジック + executeWithOAuth(httpRequest, oAuthConfig)
  }
}
```

## 設定ベースメソッドへのリトライ統合

### Option 1: HttpRequestExecutionConfigInterface拡張（推奨）

```java
// インターフェース拡張
public interface HttpRequestExecutionConfigInterface {
  // 既存メソッド...
  HttpRequestUrl httpRequestUrl();
  HttpMethod httpMethod();
  OAuthAuthorizationConfiguration oauthAuthorization();

  // 新規追加
  HttpRetryConfiguration retryConfiguration();
  default boolean hasRetryConfiguration() {
    return retryConfiguration() != null;
  }
}

// HttpRequestExecutor の設定ベースメソッド更新
public HttpRequestResult execute(
    HttpRequestExecutionConfigInterface configuration,
    HttpRequestBaseParams httpRequestBaseParams) {

  // 既存のHttpRequest構築ロジック
  HttpRequest httpRequest = buildHttpRequest(configuration, httpRequestBaseParams);

  // リトライ設定に応じた実行方法選択
  if (configuration.hasRetryConfiguration()) {
    HttpRetryConfiguration retryConfig = configuration.retryConfiguration();

    if (configuration.hasOAuthAuthorization()) {
      // OAuth + リトライ
      return executeWithRetry(httpRequest, configuration.oauthAuthorization(), retryConfig);
    } else {
      // リトライのみ
      return executeWithRetry(httpRequest, retryConfig);
    }
  } else {
    // 従来通りの実行
    if (configuration.hasOAuthAuthorization()) {
      return executeWithOAuth(httpRequest, configuration.oauthAuthorization());
    } else {
      return execute(httpRequest);
    }
  }
}
```

### Option 2: 設定ベース専用リトライメソッド

```java
// 設定ベース専用のリトライメソッド追加
public HttpRequestResult executeWithRetry(
    HttpRequestExecutionConfigInterface configuration,
    HttpRequestBaseParams httpRequestBaseParams,
    HttpRetryConfiguration retryConfig) {

  // HttpRequest構築
  HttpRequest httpRequest = buildHttpRequest(configuration, httpRequestBaseParams);

  // OAuth設定とリトライ設定の組み合わせ実行
  if (configuration.hasOAuthAuthorization()) {
    return executeWithRetry(httpRequest, configuration.oauthAuthorization(), retryConfig);
  } else {
    return executeWithRetry(httpRequest, retryConfig);
  }
}
```

## 既存実装への影響

### AdditionalParameterHttpRequestConfig （Identity Verification）

```java
// 現在の使用パターン
public class HttpRequestParameterResolver {
  public AdditionalParameterResolveResult resolve(...) {
    AdditionalParameterHttpRequestConfig configuration = // 設定から取得
    HttpRequestBaseParams baseParams = // パラメータ構築

    // 現在: リトライなし
    HttpRequestResult result = httpRequestExecutor.execute(configuration, baseParams);

    // 今後: リトライ対応
    HttpRequestResult result;
    if (configuration.hasRetryConfiguration()) {
      // 設定にリトライ設定がある場合
      result = httpRequestExecutor.execute(configuration, baseParams); // 自動的にリトライ適用
    } else {
      // デフォルトリトライ設定を適用
      HttpRetryConfiguration defaultRetry = HttpRetryConfiguration.defaultRetry();
      result = httpRequestExecutor.executeWithRetry(configuration, baseParams, defaultRetry);
    }
  }
}
```

### SecurityEventHttpRequestConfig （WebHook）

```java
// 現在
public class WebHookSecurityEventExecutor {
  public SecurityEventHookResult execute(...) {
    SecurityEventHttpRequestConfig configuration = // 設定
    HttpRequestBaseParams baseParams = // パラメータ

    HttpRequestResult result = httpRequestExecutor.execute(configuration, baseParams);
    // ...
  }
}

// 今後: 設定ファイルでリトライ制御
{
  "webhook_config": {
    "url": "https://example.com/webhook",
    "oauth_authorization": { "..." },
    "retry_configuration": {
      "max_retries": 3,
      "backoff_delays": ["1s", "5s", "30s"]
    }
  }
}
```

## 実装の段階的移行

### Phase 1: インターフェース拡張
```java
public interface HttpRequestExecutionConfigInterface {
  // 既存メソッド...

  // 新規追加（デフォルト実装でnull返却）
  default HttpRetryConfiguration retryConfiguration() {
    return null;
  }

  default boolean hasRetryConfiguration() {
    return retryConfiguration() != null;
  }
}
```

### Phase 2: 設定ベースメソッド内でのリトライ自動適用
```java
public HttpRequestResult execute(
    HttpRequestExecutionConfigInterface configuration,
    HttpRequestBaseParams httpRequestBaseParams) {

  HttpRequest httpRequest = buildHttpRequest(configuration, httpRequestBaseParams);

  // リトライ設定の自動適用
  HttpRetryConfiguration retryConfig = configuration.retryConfiguration();
  if (retryConfig == null) {
    retryConfig = HttpRetryConfiguration.noRetry(); // リトライなし
  }

  // 統一されたリトライ実行
  if (configuration.hasOAuthAuthorization()) {
    return executeWithRetry(httpRequest, configuration.oauthAuthorization(), retryConfig);
  } else {
    return executeWithRetry(httpRequest, retryConfig);
  }
}
```

### Phase 3: 各設定クラスでのリトライ設定実装

```java
// AdditionalParameterHttpRequestConfig
public class AdditionalParameterHttpRequestConfig implements HttpRequestExecutionConfigInterface {
  // 既存フィールド...
  private HttpRetryConfiguration retryConfiguration;

  @Override
  public HttpRetryConfiguration retryConfiguration() {
    return retryConfiguration;
  }
}

// SecurityEventHttpRequestConfig
public class SecurityEventHttpRequestConfig implements HttpRequestExecutionConfigInterface {
  // 既存フィールド...
  private HttpRetryConfiguration retryConfiguration;

  @Override
  public HttpRetryConfiguration retryConfiguration() {
    return retryConfiguration;
  }
}
```

## 利点

### 1. 後方互換性維持
- 既存の `execute(configuration, baseParams)` 呼び出しはそのまま動作
- リトライ設定がない場合は従来通りの動作

### 2. 設定統一
- 全てのHTTP通信で統一されたリトライ設定
- JSON設定ファイルでのリトライ制御

### 3. 段階的移行
- インターフェース拡張 → 自動適用 → 個別設定実装の順で段階的に実装可能

## 設定例

```json
{
  "identity_verification": {
    "pre_hook": {
      "http_request": {
        "url": "https://api.external.com/verify",
        "oauth_authorization": { "..." },
        "retry_configuration": {
          "max_retries": 2,
          "backoff_delays": ["1s", "3s"],
          "idempotency_required": true
        }
      }
    }
  },
  "security_event_hooks": {
    "webhook": {
      "url": "https://webhook.example.com/events",
      "retry_configuration": {
        "max_retries": 3,
        "backoff_delays": ["1s", "5s", "30s"],
        "idempotency_required": false
      }
    }
  }
}
```

この統合により、既存の設定ベースメソッドを使用しているすべてのコンポーネントで、設定によるリトライ制御が可能になります。