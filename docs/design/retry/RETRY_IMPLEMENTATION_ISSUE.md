**Title:** HttpRequestExecutorリトライ機能実装（Issue #248対応）

**Body:**

## 概要
Issue #248で検討されているリトライ機能をHttpRequestExecutorに統合実装します。外部サービス呼び出しの信頼性向上と、一時的な障害への自動対応を実現します。

## 背景
Issue #248の調査により、以下の要件が明確になりました：
- **最大3回リトライ**（指数バックオフ: 1秒 → 5秒 → 30秒）
- **Idempotency-Key管理**による重複防止
- **Circuit Breaker パターン**による障害保護
- **設定ベース制御**による柔軟なリトライ戦略

現在のHttpRequestExecutorアーキテクチャ（Issue #479でOAuth対応済み）を基盤として、シームレスなリトライ機能を追加します。

## 実装計画

### Phase 1: 基本リトライ機能 (1週間)
- [ ] `HttpRetryConfiguration`クラス作成
  ```java
  public class HttpRetryConfiguration {
    private int maxRetries = 3;
    private Duration[] backoffDelays = {Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30)};
    private Set<Integer> retryableStatusCodes = Set.of(500, 502, 503, 504, 408, 429);
    private boolean idempotencyRequired = false;
  }
  ```

- [ ] `HttpRequestExecutor.executeWithRetry()`メソッド実装
  ```java
  public HttpRequestResult executeWithRetry(HttpRequest request, HttpRetryConfiguration config)
  public HttpRequestResult executeWithRetry(HttpRequest request, OAuthAuthorizationConfiguration oauth, HttpRetryConfiguration config)
  ```

- [ ] 指数バックオフ戦略実装
- [ ] リトライ判定ロジック（5xx、タイムアウト等）
- [ ] 詳細ログ出力（相関ID、リトライ状況）

### Phase 2: Idempotency対応 (1週間)
- [ ] `IdempotencyKeyManager`クラス作成
- [ ] HttpRequestへのIdempotency-Key自動付与機能
- [ ] 重複リクエスト検知・防止機能

### Phase 3: 設定ベース統合 (1週間)
- [ ] `HttpRequestExecutionConfigInterface`拡張
  ```java
  public interface HttpRequestExecutionConfigInterface {
    // 既存メソッド...
    HttpRetryConfiguration retryConfiguration();
    default boolean hasRetryConfiguration() { return retryConfiguration() != null; }
  }
  ```

- [ ] 設定ベースメソッドでの自動リトライ適用
- [ ] 既存設定クラスへのリトライ設定追加
  - `AdditionalParameterHttpRequestConfig`
  - `SecurityEventHttpRequestConfig`
  - `IdentityVerificationHttpRequestConfig`

### Phase 4: Circuit Breaker統合 (1週間)
- [ ] `CircuitBreakerHttpExecutor`実装
- [ ] 外部サービス障害検知・自動復旧
- [ ] メトリクス・監視機能追加

## 実装アーキテクチャ

### リトライメソッド階層
```java
// 既存アーキテクチャ
execute(HttpRequestExecutionConfigInterface, HttpRequestBaseParams) // 設定ベース
  ↓
execute(HttpRequest) // 直接実行
executeWithOAuth(HttpRequest, OAuthAuthorizationConfiguration) // OAuth対応

// 新規追加
executeWithRetry(HttpRequest, HttpRetryConfiguration) // リトライ
executeWithRetry(HttpRequest, OAuthAuthorizationConfiguration, HttpRetryConfiguration) // OAuth + リトライ
```

### 設定統合パターン
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
          "retryable_status_codes": [408, 500, 502, 503],
          "idempotency_required": true
        }
      }
    }
  }
}
```

## 適用対象コンポーネント

### 即座適用
- [ ] `SsfHookExecutor` - SSF transmission
- [ ] `WebHookSecurityEventExecutor` - Webhook送信
- [ ] `HttpRequestParameterResolver` - 外部API呼び出し
- [ ] `SsoCredentialsParameterResolver` - トークンリフレッシュ

### 将来適用
- [ ] `StandardOidcExecutor` - OIDC endpoint呼び出し
- [ ] `BackChannelAuthenticationExecutor` - CIBA endpoint
- [ ] その他の外部HTTP通信コンポーネント

## 期待される効果

### 信頼性向上
- 一時的な外部サービス障害への自動対応
- ネットワーク不安定時の自動復旧
- タイムアウト・接続エラーからの回復

### 運用負荷軽減
- 手動リトライ作業の削減
- 障害通知の削減（自動復旧による）
- 運用監視の効率化

### ユーザー体験向上
- より安定したサービス提供
- レスポンス時間の改善（適切なリトライによる）
- エラー発生率の低下

## テスト戦略

### 単体テスト
- [ ] リトライロジックの各シナリオ
- [ ] バックオフ遅延の正確性
- [ ] Idempotency-Key生成・管理
- [ ] リトライ判定ロジック

### 統合テスト
- [ ] 外部サービス障害シミュレーション
- [ ] タイムアウト・ネットワークエラーテスト
- [ ] OAuth認証とリトライの組み合わせ
- [ ] 設定ベースメソッドでのリトライ動作

### E2Eテスト
- [ ] 実際の外部サービス連携でのリトライ確認
- [ ] パフォーマンス影響測定
- [ ] ログ・メトリクス出力確認

## モニタリング・メトリクス

### リトライメトリクス
```java
@Component
public class RetryMetrics {
  private final Counter retryCounter;
  private final Timer retryDelayTimer;
  private final Gauge circuitBreakerState;

  public void recordRetry(String component, int attempt, String reason);
  public void recordMaxRetriesExceeded(String component, String endpoint);
}
```

### ログ出力例
```json
{
  "timestamp": "2025-09-23T10:30:45.123Z",
  "level": "WARN",
  "message": "HTTP request retry",
  "component": "ssf_hook_executor",
  "endpoint": "https://example.com/webhook",
  "attempt": 2,
  "max_retries": 3,
  "backoff_delay": "5s",
  "error_type": "SERVER_ERROR",
  "status_code": 503,
  "correlation_id": "req_abc12345",
  "idempotency_key": "idem_def67890"
}
```

## 関連Issue・PR
- Issue #248 - リトライ機能設計（元Issue）
- Issue #479 - OAuth認証サポート（✅ 完了済み）
- Issue #489 - HttpClient段階的移行（進行中）

## 実装見積もり
- **Phase 1**: 1週間 - 基本リトライ機能
- **Phase 2**: 1週間 - Idempotency対応
- **Phase 3**: 1週間 - 設定統合
- **Phase 4**: 1週間 - Circuit Breaker統合
- **合計**: 4週間

## 実装時の注意点
- 既存API互換性の維持
- パフォーマンス影響の最小化
- リトライ設定は任意（null許可）でフォールバック対応
- 段階的リリースによるリスク軽減
- 包括的なテストカバレッジ確保

---

**Labels:** enhancement, reliability, http, retry, circuit-breaker
**Assignees:** @hirokazu-kobayashi-koba-hiro
**Projects:** Platform Enhancement
**Milestone:** Reliability Improvements