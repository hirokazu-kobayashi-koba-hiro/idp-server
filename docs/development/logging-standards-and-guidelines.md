# ログ標準・ガイドライン

## 概要

本ドキュメントは、idp-serverプロジェクトにおけるログ標準を定義し、全モジュールで一貫性のある、高性能で保守しやすいログ実装を確保することを目的としています。

**重要**: 実装コード内のログメッセージは **英語** で記述し、運用時の国際的対応とログ解析ツールとの互換性を確保します。

## ログレベル戦略

### TRACE
- **用途**: メソッドの入出力、非常に詳細な実行フロー
  - **使用場面**: パフォーマンスデバッグ、詳細なリクエストトレース
  - **実装例**: パラメータ付きメソッド入出力
```java
log.trace("Method started: operation={}, tenant={}", operation, tenantId);
log.trace("Method completed: operation={}, result={}", operation, result);
```

### DEBUG
- **用途**: ビジネスロジックフロー、中間状態、開発デバッグ
  - **使用場面**: アルゴリズムステップ、状態変化、条件分岐
  - **実装例**: ビジネスロジックチェックポイント
```java
log.debug("User authentication status: verified={}, mfa_required={}",
    isVerified, mfaRequired);
```

### INFO
- **用途**: ビジネスイベント、成功操作、監査証跡
  - **使用場面**: ユーザーアクション、システム状態変化、成功トランザクション
  - **実装例**: ビジネスイベント
```java
log.info("User login successful: user={}, method={}", userId, authMethod);
log.info("Token issued: client={}, scopes={}", clientId, scopes);
```

### WARN
- **用途**: 回復可能エラー、異常な状況、非推奨機能の使用
  - **使用場面**: フォールバック機構、リトライ、設定問題
  - **実装例**: 回復可能な問題
```java
log.warn("External service unavailable, using cached data: service={}", serviceName);
log.warn("Deprecated configuration detected: parameter={}", paramName);
```

### ERROR
- **用途**: 回復不可能エラー、システム障害、セキュリティ違反
  - **使用場面**: 例外、セキュリティ侵害、データ破損
  - **実装例**: 重大エラー
```java
log.error("Authentication failed: user={}, reason={}, attempts={}",
    userId, reason, attemptCount, exception);
```

## 構造化ログパターン

### 1. パラメータ化ログ
**文字列結合ではなく、必ずパラメータ化ログを使用する**

❌ **非推奨:**
```java
log.debug("READ start: " + target.getClass().getName() + ": " + method.getName());
log.error("rollback transaction: " + method.getName() + ", cause: " + e);
```

✅ **推奨:**
```java
log.debug("READ start: class={}, method={}", target.getClass().getName(), method.getName());
log.error("トランザクションロールバック失敗: method={}, error={}", method.getName(), e.getMessage(), e);
```

### 2. 一貫したキー・バリュー形式
コードベース全体で一貫したキー名を使用:

```java
// 標準パターン
log.info("操作完了: operation={}, tenant={}, duration={}ms",
    operation, tenantId, duration);

log.error("バリデーション失敗: field={}, value={}, constraint={}",
    fieldName, fieldValue, constraintType);
```

### 3. リッチエラーコンテキスト
デバッグ用の包括的コンテキストを含める:

```java
log.error("メール通知失敗: recipient={}, template={}, tenant={}, smtp_host={}, error={}",
    recipient, templateId, tenantId, smtpHost, e.getMessage(), e);
```

## パフォーマンスガイドライン

### 1. 高コスト操作の保護
```java
if (log.isDebugEnabled()) {
    log.debug("複雑な状態: {}", expensiveToStringOperation());
}
```

### 2. 不要なオブジェクト生成の回避
❌ **非推奨:**
```java
log.debug("User data: " + user.toDetailedString()); // 常に文字列生成
```

✅ **推奨:**
```java
log.debug("User data: {}", user); // ログ有効時のみtoString()を使用
```

### 3. 遅延評価
高コストな計算にはサプライヤを使用:
```java
log.debug("複雑な計算結果: {}", () -> expensiveCalculation());
```

## エラーログのベストプラクティス

### 1. 例外ログ
```java
// スタックトレース用に例外を最後のパラメータに含める
log.error("データベース操作失敗: query={}, params={}, error={}",
    query, params, e.getMessage(), e);
```

### 2. セキュリティ機密情報
**機密データは絶対にログ出力しない:**
```java
// ❌ パスワード、トークン、個人情報をログ出力しない
log.debug("Authentication: password={}", password);

// ✅ 安全な識別子をログ出力
log.debug("認証試行: user_id={}, method={}", userId, authMethod);
```

### 3. コンテキスト付きエラー情報
```java
// デバッグ用にビジネスコンテキストを含める
log.error("決済処理失敗: transaction_id={}, amount={}, currency={}, " +
    "payment_method={}, merchant_id={}, error={}",
    transactionId, amount, currency, paymentMethod, merchantId, e.getMessage(), e);
```

## モジュール固有の標準

### 1. TenantAwareEntryServiceProxy
**冗長なデバッグログを意味のあるトレースログに置換:**

❌ **現状:**
```java
log.debug("READ start: " + target.getClass().getName() + ": " + method.getName() + " ...");
log.debug("READ end: " + target.getClass().getName() + ": " + method.getName() + " ...");
```

✅ **改善後:**
```java
log.trace("トランザクション開始: operation={}, service={}, method={}",
    operationType, target.getClass().getSimpleName(), method.getName());
// 長時間実行操作のみ完了ログを出力
if (duration > 1000) {
    log.debug("長時間トランザクション完了: operation={}, duration={}ms",
        operationType, duration);
}
```

### 2. 認証・認可
```java
// 認証成功
log.info("認証成功: user={}, method={}, client={}",
    userId, authMethod, clientId);

// 認証失敗
log.warn("認証失敗: user={}, method={}, reason={}, client={}, ip={}",
    userId, authMethod, failureReason, clientId, remoteAddr);
```

### 3. フェデレーション・外部連携
```java
// 外部API呼び出し
log.debug("外部API要求: provider={}, endpoint={}, method={}",
    providerName, endpoint, httpMethod);

log.info("外部APIレスポンス: provider={}, status={}, duration={}ms",
    providerName, responseStatus, duration);

// フェデレーションイベント
log.info("フェデレーション認証: provider={}, user={}, tenant={}",
    providerName, externalUserId, tenantId);
```

### 4. セキュリティイベント
```java
// セキュリティ違反
log.error("セキュリティ違反検出: type={}, user={}, ip={}, details={}",
    violationType, userId, remoteAddr, details);

// 監査イベント
log.info("管理者操作: action={}, admin={}, target={}, details={}",
    actionType, adminUserId, targetResource, details);
```

## ツール・ユーティリティ

### 1. 構造化ログビルダー（将来の機能拡張）
```java
// 複雑なログ用のユーティリティ候補
LogBuilder.info()
    .event("user_login")
    .user(userId)
    .tenant(tenantId)
    .client(clientId)
    .duration(duration)
    .log("ユーザー認証完了");
```

### 2. パフォーマンス監視
```java
// 標準化されたパフォーマンスログ
try (var timer = PerformanceLogger.start("database_query")) {
    // データベース操作
}
// しきい値を超えた場合自動的にログ出力
```

## 移行ガイドライン

### フェーズ1: 高影響エリア
1. **TenantAwareEntryServiceProxy** - デバッグノイズ削減
   2. **認証フロー** - セキュリティログの標準化
   3. **エラーハンドラー** - リッチコンテキスト追加

### フェーズ2: 体系的クリーンアップ
1. **文字列結合** → パラメータ化ログ
   2. **不一致フォーマット** → 構造化パターン
   3. **不足コンテキスト** → 拡張エラーログ

### フェーズ3: 高度機能
1. **パフォーマンス監視**
   2. **ビジネスメトリクス**
   3. **アラート統合**

## 監視統合

### 1. ログベースメトリクス
```java
// メトリクス対応ログ
log.info("api_request_completed: endpoint={}, status={}, duration={}, tenant={}",
    endpoint, statusCode, duration, tenantId);
```

### 2. アラートパターン
```java
// アラート起動エラー
log.error("ALERT: Critical system failure: component={}, error={}",
    componentName, e.getMessage(), e);
```

### 3. ビジネスインテリジェンス
```java
// ビジネスイベント追跡
log.info("business_event: type={}, tenant={}, user={}, details={}",
    eventType, tenantId, userId, eventDetails);
```

## コードレビューガイドライン

### ログ文のチェックリスト
- [ ] 適切なログレベルを使用
  - [ ] パラメータ化ログを使用
  - [ ] 関連するコンテキストを含む
  - [ ] 機密情報を回避
  - [ ] パフォーマンス最適化（高コスト操作の保護）
  - [ ] プロジェクト標準との一貫したフォーマット

### 注意すべき一般的な問題
- ログ文での文字列結合
  - エラーコンテキストの不足
  - 機密データのログ出力
  - 一貫性のないキー命名
  - 本番コードパスでの過剰なデバッグログ

## ユースケース別実装例

### ユーザー認証フロー
```java
// 開始
log.debug("認証開始: method={}, client={}", authMethod, clientId);

// 進行
log.debug("ユーザー検証: user={}, verification_method={}", userId, verificationMethod);

// 成功
log.info("認証成功: user={}, method={}, client={}, duration={}ms",
    userId, authMethod, clientId, duration);

// 失敗
log.warn("認証失敗: user={}, method={}, reason={}, client={}, ip={}, attempts={}",
    userId, authMethod, failureReason, clientId, remoteAddr, attemptCount);
```

### トークン管理
```java
// トークン発行
log.info("トークン発行: type={}, client={}, user={}, scopes={}, expires_in={}",
    tokenType, clientId, userId, scopes, expiresIn);

// トークン検証
log.debug("トークン検証: type={}, client={}, remaining_ttl={}",
    tokenType, clientId, remainingTtl);

// トークン無効化
log.info("トークン無効化: type={}, client={}, user={}, reason={}",
    tokenType, clientId, userId, revocationReason);
```

### データベース操作
```java
// クエリ実行
log.debug("データベースクエリ: operation={}, table={}, conditions={}",
    operation, tableName, conditions);

// パフォーマンス監視
if (duration > 1000) {
    log.warn("低速データベースクエリ: operation={}, duration={}ms, table={}",
        operation, duration, tableName);
}

// エラー
log.error("データベース操作失敗: operation={}, table={}, error={}",
    operation, tableName, e.getMessage(), e);
```

## 現状分析・重要な改善課題

### 🚨 Critical Missing Logs Analysis (2025年1月調査結果)

プロジェクト全体のログ調査を実施した結果、以下の **重要なコンポーネントでログが完全に不足** していることが判明しました：

#### 1. 🔐 認証・認可フロー (影響度: CRITICAL)

**ClientAuthenticationHandler**
- 問題: クライアント認証処理において成功/失敗ログが一切なし
  - 影響: 認証問題のトラブルシューティングが不可能
  - 必要ログ例:
```java
log.info("Client authentication successful: method={}, client={}", authMethod, clientId);
log.warn("Client authentication failed: method={}, client={}, reason={}", authMethod, clientId, reason);
```

**OAuthHandler**
- 問題: OAuth認可処理の開始/完了ログなし
  - 影響: 認可フロー問題の根本原因分析困難
  - 必要ログ例:
```java
log.trace("OAuth authorization started: client={}, response_type={}", clientId, responseType);
log.info("OAuth authorization completed: client={}, user={}, scopes={}", clientId, userId, scopes);
```

**OAuthAuthorizeHandler**
- 問題: 認可コード発行・リダイレクト処理ログなし
  - 影響: 認可コードに関する問題追跡不可能
  - 必要ログ例:
```java
log.info("Authorization code issued: client={}, user={}, expires_in={}", clientId, userId, expiresIn);
log.trace("Authorization redirect: client={}, redirect_uri={}", clientId, redirectUri);
```

#### 2. 🎟️ トークン管理 (影響度: CRITICAL)

**TokenRequestHandler**
- 問題: トークン発行処理の包括的ログ不足
  - 影響: トークン関連問題のデバッグ困難
  - 必要ログ例:
```java
log.trace("Token request started: grant_type={}, client={}", grantType, clientId);
log.info("Access token issued: client={}, user={}, scopes={}, expires_in={}",
    clientId, userId, scopes, expiresIn);
log.info("Refresh token issued: client={}, user={}, expires_in={}",
    clientId, userId, refreshExpiresIn);
log.warn("Token request failed: grant_type={}, client={}, error={}",
    grantType, clientId, error);
```

**UserinfoHandler**
- 問題: ユーザー情報エンドポイントアクセスログなし
  - 影響: ユーザー情報取得問題の調査困難
  - 必要ログ例:
```java
log.trace("Userinfo request started: client={}", clientId);
log.info("Userinfo response sent: user={}, claims_count={}", userId, claimsCount);
log.warn("Userinfo request failed: reason={}, token_valid={}", reason, tokenValid);
```

#### 3. 🗄️ データベース操作 (影響度: HIGH)

**全QueryDataSourceクラス** (AuthenticationConfigurationQueryDataSource、ClientConfigurationQueryDataSource等)
- 問題: データベースアクセスエラー・パフォーマンスログなし
  - 影響: データベース関連問題の特定・最適化困難
  - 必要ログ例:
```java
log.trace("Database query started: operation={}, table={}", operation, table);
log.warn("Slow database query: operation={}, duration={}ms, table={}",
    operation, duration, table);
log.error("Database operation failed: operation={}, table={}, error={}",
    operation, table, e.getMessage(), e);
```

#### 4. ⚡ パフォーマンス・ヘルスモニタリング (影響度: MEDIUM)

- API応答時間追跡なし
  - 外部サービス呼び出し時間計測なし
  - システムリソース監視ログなし
  - 必要ログ例:
```java
log.info("API request completed: endpoint={}, method={}, status={}, duration={}ms",
    endpoint, method, status, duration);
log.warn("External service slow response: service={}, endpoint={}, duration={}ms",
    service, endpoint, duration);
```

#### 5. 🔍 ビジネスインテリジェンス・監査 (影響度: HIGH)

- ユーザーアクション詳細追跡なし
  - API使用統計・パターン分析データなし
  - コンプライアンス監査証跡不足
  - 必要ログ例:
```java
log.info("User action: action={}, user={}, resource={}, result={}",
    action, userId, resource, result);
log.info("API usage: endpoint={}, client={}, user={}, timestamp={}",
    endpoint, clientId, userId, timestamp);
```

### 📋 優先度付き改善ロードマップ

#### 🔥 P0 - 即座対応必要
1. **ClientAuthenticationHandler** 認証成功/失敗ログ実装
   2. **TokenRequestHandler** トークン発行包括ログ実装
   3. **UserinfoHandler** ユーザー情報アクセスログ実装

#### ⚠️ P1 - 1週間以内
4. **OAuthHandler** 認可フロー追跡ログ実装
   5. **Database DataSources** エラーハンドリング・パフォーマンスログ実装

#### 📊 P2 - 2週間以内
6. **Performance監視** レスポンス時間・リソース計測ログ実装
   7. **Business Intelligence** ユーザーアクション・統計ログ実装

### 💼 本番運用への影響

現在の状態では以下が **著しく困難**：
- 認証失敗の根本原因分析
  - トークン発行問題のデバッグ
  - パフォーマンス問題の特定
  - セキュリティインシデント調査
  - ユーザーサポート効率的対応

これらのログ実装により、**運用性・保守性・セキュリティが大幅に向上** することが期待されます。

## プロダクションログ分析による具体的改善課題

### 🔍 E2E環境ログ分析結果 (2025年1月)

実際のE2E環境ログを分析した結果、以下の**重要なコンテキスト不足問題**が判明しました：

#### 1. 🚨 Client Authentication Error - コンテキスト不足

**現状エラーログ:**
```json
{
  "message": "client authentication type is client_secret_post, but request does not contains client_secret_post",
  "logger_name": "org.idp.server.core.openid.token.handler.token.TokenRequestErrorHandler"
}
```

**問題:** どのクライアントで認証失敗したか不明

**発生箇所:** `ClientSecretPostAuthenticator.java:78`
```java
throw new ClientUnAuthorizedException(
    "client authentication type is client_secret_post, but request does not contains client_secret_post");
```

**改善案:**
```java
// 現在
throw new ClientUnAuthorizedException(
    "client authentication type is client_secret_post, but request does not contains client_secret_post");

// 改善後
throw new ClientUnAuthorizedException(
    "client authentication type is client_secret_post, but request does not contains client_secret_post, client_id=" +
    context.requestedClientId().value());
```

#### 2. 🚨 SecurityEventHook Executor Error - 識別子不明

**現状エラーログ:**
```json
{
  "message": "Transaction rollback: operation=WRITE, service=SecurityEventEntryService, method=handle, error=No executor registered for type 8fae9e9e-7ddd-4e95-82f9-f38b001a4dce",
  "logger_name": "org.idp.server.platform.proxy.TenantAwareEntryServiceProxy"
}
```

**問題:** `8fae9e9e-7ddd-4e95-82f9-f38b001a4dce` が何の識別子か不明

**発生箇所:** `SecurityEventHooks.java:35`
```java
throw new UnSupportedException("No executor registered for type " + type.name());
```

**改善案:**
```java
// 現在
throw new UnSupportedException("No executor registered for type " + type.name());

// 改善後
throw new UnSupportedException(
    "No SecurityEventHook executor registered: hook_type=" + type.name() +
    ", available_types=" + values.keySet().stream().map(SecurityEventHookType::name).collect(Collectors.joining(",")));
```

#### 3. 📊 Business Success Log - Business Context不足

**現状成功ログ:**
```json
{
  "message": "Execute AccessTokenCustomClaimsCreators : org.idp.server.core.openid.token.plugin.ScopeMappingCustomClaimsCreator",
  "logger_name": "org.idp.server.core.openid.token.plugin.AccessTokenCustomClaimsCreators"
}
```

**問題:** どのクライアント/ユーザーのトークン発行か不明

**改善案:**
```java
log.info("Access token custom claims executed: plugin={}, client={}, user={}, scopes={}",
    plugin.getClass().getSimpleName(), clientId, userId, scopes);
```

### 📋 系統的問題パターンと対策

#### Pattern 1: Client認証エラー系
**問題ファイル群:**
- `ClientSecretPostAuthenticator.java`
  - `ClientSecretBasicAuthenticator.java`
  - `ClientSecretJwtAuthenticator.java`
  - `PrivateKeyJwtAuthenticator.java`

**共通問題:** client_id情報の欠如
**統一対策:** 例外メッセージにclient_id追加

#### Pattern 2: Configuration NotFound系
**問題ファイル群:**
- `ClientConfigurationQueryDataSource.java`
  - `AuthenticationConfigurationQueryDataSource.java`
  - `FederationConfigurationQueryDataSource.java`

**共通問題:** tenant_id, 要求パラメータ詳細の欠如
**統一対策:** 例外コンストラクタでコンテキスト情報強化

#### Pattern 3: Executor/Hook Registration系
**問題ファイル群:**
- `SecurityEventHooks.java`
  - `ClientAuthenticators.java`
  - `OAuthTokenCreationServices.java`

**共通問題:** 利用可能オプション情報の欠如
**統一対策:** エラーメッセージに利用可能タイプ一覧追加

### 🎯 優先度付き改善プラン

#### 🔥 P0 - 即座対応 (セキュリティ・本番障害)
1. **Client Authentication Error** - client_id情報追加
   2. **SecurityEventHook Error** - hook_type詳細とavailable_types追加

#### ⚠️ P1 - 1週間以内 (運用効率)
3. **Configuration NotFound Error** - tenant, parameter詳細追加
   4. **Token Success Log** - client_id, user_id, scopes詳細追加

#### 📊 P2 - 2週間以内 (分析・最適化)
5. **Executor Registration Error** - available options一覧追加
   6. **Performance Log** - 処理時間・リソース使用量追加

---

本ドキュメントは、idp-serverプロジェクト全体で一貫性があり、保守可能で効率的なログ実装の基盤を提供します。