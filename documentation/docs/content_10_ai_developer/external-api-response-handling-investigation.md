# 外部APIレスポンスハンドリング改善調査

**調査日**: 2025-01-30
**関連Issue**: #544, #716
**調査者**: Claude Code

## 概要

外部API連携における2つの問題を統合的に検討：

1. **Issue #544**: HTTPステータスコードのマッピング方針（429, 503などの情報喪失）
2. **Issue #716**: HTTP 200 + Body内エラーパターンの未対応

両Issueは「外部APIレスポンスハンドリング」という同じ領域の異なる側面を扱っており、統合的な解決が望ましい。

## 現状分析

### 実装フロー

```
外部API Response
  ↓ (例: status=429, Retry-After: 60, body={...})
HttpRequestExecutor (platform層)
  ↓ statusCode=429をそのまま保持 ✅
HttpRequestResult (statusCode=429, body={...})
  ↓
IdentityVerificationApplicationHttpRequestExecutor.resolveStatus()
  ↓ 🔴 問題箇所：ここで CLIENT_ERROR に丸める
IdentityVerificationExecutionStatus.CLIENT_ERROR
  ↓
最終レスポンス: 400相当のエラー（429情報喪失）
```

### 問題の所在

**ファイル**: `libs/idp-server-core-extension-ida/src/main/java/org/idp/server/core/extension/identity/verification/application/execution/executor/IdentityVerificationApplicationHttpRequestExecutor.java`

```java
private IdentityVerificationExecutionStatus resolveStatus(HttpRequestResult httpRequestResult) {
  if (httpRequestResult.isClientError()) {  // 400-499
    return IdentityVerificationExecutionStatus.CLIENT_ERROR;  // 🔴 429も400も同じ
  }

  if (httpRequestResult.isServerError()) {  // 500-599
    return IdentityVerificationExecutionStatus.SERVER_ERROR;  // 🔴 503も500も同じ
  }

  return IdentityVerificationExecutionStatus.OK;  // 🔴 HTTP 200でもBody内エラーあり得る
}
```

### 調査済み事実

#### ✅ 良い点

1. **HttpRequestExecutor自体は正しい実装**
   - ステータスコードをそのまま保持（`httpResponse.statusCode()`）
   - Retry-Afterヘッダー対応済み（lines 802-857）
   - ネットワーク例外を適切なステータスコードにマッピング

2. **詳細情報は保存されている**
   ```java
   .addErrorDetail("status_code", httpRequestResult.statusCode());
   ```
   - 実際のステータスコードはerror_details内に記録
   - しかし、最終的なHTTP応答では判定ロジックがstatusのみを見る

#### ❌ 問題点

| 問題 | 詳細 | 影響 |
|------|------|------|
| **ステータスコード丸め** | 429/503/504などが3値に集約される | Rate Limit/Retry情報喪失 |
| **HTTP 200エラー未検出** | Body内の`{"status":"error"}`を見ていない | 成功と誤認識 |
| **Enumが貧弱** | OK/CLIENT_ERROR/SERVER_ERROR の3値のみ | 詳細な制御不可 |

## 問題の詳細

### Issue #544: ステータスコード丸め問題

#### 失われる情報

| 実際のコード | 丸められた結果 | 失われる情報 | ユースケース |
|------------|--------------|------------|------------|
| 429 Too Many Requests | `CLIENT_ERROR` | Rate Limit情報 | リトライ間隔調整 |
| 503 Service Unavailable | `SERVER_ERROR` | サービス停止情報 | フェイルオーバー判断 |
| 504 Gateway Timeout | `SERVER_ERROR` | タイムアウト情報 | タイムアウト値調整 |

#### Retry-Afterヘッダーの扱い

HttpRequestExecutorは既に対応済み：
```java
private Duration parseRetryAfterHeader(HttpRequestResult result) {
  // RFC 7231準拠のRetry-After解析
  // delay-seconds形式とHTTP-date形式に対応
}
```

しかし、上位層でステータスコードが丸められるため、429であることが判別できず、Retry-After情報を活用できない。

### Issue #716: HTTP 200エラーパターン

#### 典型的なエラーパターン

**パターン1: status フィールド**
```json
HTTP 200 OK
{
  "status": "error",
  "error_code": "VERIFICATION_FAILED",
  "message": "Document validation failed"
}
```

**パターン2: success フィールド**
```json
HTTP 200 OK
{
  "success": false,
  "reason": "insufficient_data",
  "details": {
    "missing_fields": ["address", "date_of_birth"]
  }
}
```

**パターン3: error オブジェクト存在**
```json
HTTP 200 OK
{
  "error": {
    "type": "validation_error",
    "message": "Invalid document format"
  }
}
```

#### 実際の外部API例

- **Stripe API**: `{"success": true/false}` パターン
- **AWS API**: `{"error": {...}}` パターン
- **Twilio API**: HTTP 200 + エラーコードパターン

## 検討した解決パターン

### パターンA: Enumを詳細化

```java
public enum IdentityVerificationExecutionStatus {
  OK,
  CLIENT_ERROR,
  BAD_REQUEST,         // 400
  UNAUTHORIZED,        // 401
  FORBIDDEN,           // 403
  NOT_FOUND,           // 404
  RATE_LIMITED,        // 429
  SERVER_ERROR,
  SERVICE_UNAVAILABLE, // 503
  GATEWAY_TIMEOUT;     // 504
}
```

**メリット**:
- ステータスコードの詳細な表現が可能
- 各コードに応じた適切な処理が可能

**デメリット**:
- Enumが肥大化（HTTPステータスコード全体をカバーすると数十個）
- 既存コードへの影響が大きい
- 新しいコードが追加されるたびにEnumを拡張する必要がある

**評価**: ❌ 推奨しない（保守性が低い）

### パターンB: 元のステータスコードを保持（推奨）

```java
public class IdentityVerificationExecutionResult {
  IdentityVerificationExecutionStatus status;  // OK/CLIENT_ERROR/SERVER_ERROR（カテゴリ）
  int statusCode;  // ← 追加：元のHTTPステータスコードを保持
  Map<String, Object> result;

  // 判定メソッド追加
  public boolean isRateLimited() {
    return statusCode == 429;
  }

  public boolean isServiceUnavailable() {
    return statusCode == 503;
  }

  public boolean isGatewayTimeout() {
    return statusCode == 504;
  }
}
```

**メリット**:
- 既存の`status`フィールドとの互換性維持
- 詳細なステータスコード情報を保持
- 必要に応じて判定メソッドを追加可能
- Enumの肥大化を防ぐ

**デメリット**:
- statusとstatusCodeの二重管理（概念の重複）
- error_details内にも`status_code`があり三重管理になる

**評価**: ⭐ 推奨（既存コード影響が最小限）

### パターンC: ResponseSuccessCriteriaの導入（Issue #716対応）

設定により、HTTP 200レスポンスのBody内容でエラー判定を行う機能を追加。

#### 設定例

```json
{
  "execution": {
    "type": "http_request",
    "http_request": {
      "url": "https://external-api.com/verify",
      "method": "POST",
      "response_success_criteria": {
        "field_checks": [
          {"field": "status", "operator": "equals", "value": "success"},
          {"field": "success", "operator": "equals", "value": true},
          {"field": "error", "operator": "not_exists"}
        ],
        "match_mode": "any"
      }
    }
  }
}
```

#### 実装すべきクラス

**1. ResponseSuccessCriteria**
```java
// File: libs/idp-server-platform/src/main/java/org/idp/server/platform/http/ResponseSuccessCriteria.java
public class ResponseSuccessCriteria {
  List<FieldCheck> fieldChecks;
  MatchMode matchMode;  // ANY (OR) or ALL (AND)

  public boolean evaluate(JsonNodeWrapper responseBody) {
    if (fieldChecks == null || fieldChecks.isEmpty()) {
      return true;  // 設定なし = デフォルト動作
    }

    if (matchMode == MatchMode.ALL) {
      return fieldChecks.stream().allMatch(check -> check.evaluate(responseBody));
    } else {
      return fieldChecks.stream().anyMatch(check -> check.evaluate(responseBody));
    }
  }
}
```

**2. FieldCheck**
```java
public class FieldCheck {
  String field;          // JSONPath形式: "status" or "result.status"
  Operator operator;     // EQUALS, NOT_EQUALS, EXISTS, NOT_EXISTS, IN, REGEX
  Object value;          // 期待値（operatorによってはnull可）

  public boolean evaluate(JsonNodeWrapper responseBody) {
    Object actualValue = responseBody.getValue(field);  // JSONPath評価

    switch (operator) {
      case EQUALS:
        return Objects.equals(actualValue, value);
      case NOT_EQUALS:
        return !Objects.equals(actualValue, value);
      case EXISTS:
        return actualValue != null;
      case NOT_EXISTS:
        return actualValue == null;
      case IN:
        return ((List<?>) value).contains(actualValue);
      case REGEX:
        return actualValue != null &&
               actualValue.toString().matches(value.toString());
      default:
        return false;
    }
  }
}

public enum Operator {
  EQUALS, NOT_EQUALS, EXISTS, NOT_EXISTS, IN, REGEX
}
```

**3. HttpRequestResult拡張**
```java
public class HttpRequestResult {
  int statusCode;
  Map<String, List<String>> headers;
  JsonNodeWrapper body;
  ResponseSuccessCriteria successCriteria;  // ← 追加

  public boolean isSuccess() {
    // HTTP statusベースの判定
    if (!isHttpSuccess()) {
      return false;
    }

    // ResponseSuccessCriteriaによるBody判定
    if (successCriteria != null) {
      return successCriteria.evaluate(body);
    }

    return true;  // デフォルト動作
  }
}
```

**メリット**:
- 外部APIごとの独自エラーパターンに対応可能
- 設定ベースで柔軟に判定ロジックを変更可能
- 下位互換性維持（設定なし = 現在の動作）

**デメリット**:
- 設定が複雑化する
- JSONPath評価のパフォーマンス影響

**評価**: ⭐ 推奨（柔軟性が高い）

## 🎯 重要な発見：既存の再利用可能コンポーネント

### 身元確認ステータス遷移機能からの知見

**重要**: パターンCの`ResponseSuccessCriteria`実装に必要なコンポーネントは既にコードベースに存在している。

身元確認アプリケーションのステータス遷移機能（`IdentityVerificationApplicationStatusEvaluator`）が同様の条件評価を実装しており、そのコンポーネントを再利用できる。

### 既存コンポーネント一覧

#### 1. ConditionOperationEvaluator（条件演算子評価）

**ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/condition/ConditionOperationEvaluator.java`

**提供する機能**:
```java
public static boolean evaluate(
    Object target, ConditionOperation conditionOperation, Object expected) {
  return switch (conditionOperation) {
    case EQ -> Objects.equals(target, expected);              // equals
    case NE -> !Objects.equals(target, expected);             // not_equals
    case GT -> compareNumbers(target, expected) > 0;          // greater_than
    case GTE -> compareNumbers(target, expected) >= 0;        // greater_than_or_equal
    case LT -> compareNumbers(target, expected) < 0;          // less_than
    case LTE -> compareNumbers(target, expected) <= 0;        // less_than_or_equal
    case IN -> containsInList(target, expected);              // in
    case NIN -> !containsInList(target, expected);            // not_in
    case EXISTS -> target != null;                            // exists
    case MISSING -> target == null;                           // not_exists
    case CONTAINS -> containsString(target, expected);        // contains
    case REGEX -> matchRegex(target, expected);               // regex
    default -> false;
  };
}
```

**特徴**:
- **12種類の演算子**: EQ, NE, GT, GTE, LT, LTE, IN, NIN, EXISTS, MISSING, CONTAINS, REGEX
- **型安全**: 数値比較、文字列比較、リスト検索を適切に処理
- **セキュリティ**: Regex長制限（ReDoS対策）、LRUキャッシュ、スレッドセーフ
- **パフォーマンス**: Pattern事前コンパイル、キャッシング

#### 2. ConditionOperation Enum（演算子定義）

**ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/condition/ConditionOperation.java`

**提供する演算子**:
```java
public enum ConditionOperation {
  EQ,       // 等しい
  NE,       // 等しくない
  GT,       // より大きい
  GTE,      // 以上
  LT,       // より小さい
  LTE,      // 以下
  IN,       // リストに含まれる
  NIN,      // リストに含まれない
  EXISTS,   // 存在する
  MISSING,  // 存在しない
  CONTAINS, // 文字列を含む
  REGEX;    // 正規表現マッチ
}
```

#### 3. JsonPathWrapper（JSONPath評価）

**ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/json/path/JsonPathWrapper.java`

**提供する機能**:
```java
public Object readRaw(String path) {
  try {
    return JsonPath.read(document, path);
  } catch (PathNotFoundException e) {
    log.warn(e.getMessage());
    return null;  // パスが存在しない場合はnull
  }
}
```

**特徴**:
- **Jayway JSONPath統合**: 業界標準JSONPathライブラリ
- **エラーハンドリング**: PathNotFoundExceptionを適切に処理
- **型保持**: Object型で返却、呼び出し側で型判定

#### 4. IdentityVerificationApplicationStatusEvaluator（実装パターン参照）

**ファイル**: `libs/idp-server-core-extension-ida/src/main/java/org/idp/server/core/extension/identity/verification/application/status/IdentityVerificationApplicationStatusEvaluator.java`

**参考実装**（lines 124-136）:
```java
static boolean isAllSatisfied(
    List<IdentityVerificationCondition> resultConditions,
    JsonPathWrapper jsonPathWrapper) {
  for (IdentityVerificationCondition resultCondition : resultConditions) {
    // JSONPathで値を取得
    Object actualValue = jsonPathWrapper.readRaw(resultCondition.path());

    // ConditionOperationEvaluatorで評価
    if (!ConditionOperationEvaluator.evaluate(
        actualValue,
        resultCondition.operation(),
        resultCondition.value())) {
      return false;
    }
  }
  return true;
}
```

### 実装への影響

#### パターンCの簡略化

**当初の見積もり**: `ResponseSuccessCriteria`, `FieldCheck`, `Operator`を新規実装 → 約100行

**実際の必要実装**: 既存コンポーネントを組み合わせるラッパーのみ → 約30行

**簡略化された実装案**:
```java
// File: libs/idp-server-platform/src/main/java/org/idp/server/platform/http/ResponseSuccessCriteria.java
public class ResponseSuccessCriteria {
  List<ResponseCondition> conditions;
  ConditionMatchMode matchMode;  // ALL (AND) or ANY (OR)

  public boolean evaluate(JsonPathWrapper responseBody) {
    if (conditions == null || conditions.isEmpty()) {
      return true;
    }

    return matchMode == ConditionMatchMode.ALL
        ? conditions.stream().allMatch(c -> evaluateCondition(c, responseBody))
        : conditions.stream().anyMatch(c -> evaluateCondition(c, responseBody));
  }

  private boolean evaluateCondition(ResponseCondition condition, JsonPathWrapper json) {
    Object actualValue = json.readRaw(condition.path());
    return ConditionOperationEvaluator.evaluate(
        actualValue, condition.operation(), condition.value());
  }
}

public record ResponseCondition(
    String path,                  // JSONPath（例: "$.status"）
    ConditionOperation operation, // 既存のConditionOperation enum使用
    Object value                  // 期待値
) {}
```

### メリット

1. **実装量の大幅削減**: 100行 → 30行（70%削減）
2. **品質保証**: 既存コンポーネントは本番稼働実績あり
3. **一貫性**: 身元確認ステータス遷移と同じパターン
4. **保守性**: 演算子追加時はConditionOperationEvaluator一箇所のみ
5. **セキュリティ**: ReDoS対策、Patternキャッシュなど既実装

### 設定例の更新

**改善前**（独自Operator定義）:
```json
{
  "field_checks": [
    {"field": "status", "operator": "equals", "value": "success"}
  ]
}
```

**改善後**（既存ConditionOperation使用）:
```json
{
  "conditions": [
    {"path": "$.status", "operation": "eq", "value": "success"}
  ],
  "match_mode": "any"
}
```

**利用可能な演算子**:
- `eq` / `ne`: 等価比較
- `gt` / `gte` / `lt` / `lte`: 数値比較
- `in` / `nin`: リスト検索
- `exists` / `missing`: 存在チェック
- `contains`: 文字列検索
- `regex`: 正規表現マッチ

### 実装優先度の変更

この発見により、フェーズ2の実装難易度が大幅に低下。

**当初の見積もり**: 中程度の実装規模（新規クラス3つ、100+行）
**修正後の見積もり**: 小規模の実装（ラッパークラス1つ、30行）

## 推奨する実装アプローチ

### フェーズ1: ステータスコード保持（Issue #544対応）

1. `IdentityVerificationExecutionResult`に`statusCode`フィールド追加
2. `resolveStatus()`を修正してstatusCodeを保持
3. Handlerでの判定ロジックを詳細化（429, 503などを区別）

**影響範囲**: 小（内部実装のみ、API変更なし）

### フェーズ2: ResponseSuccessCriteria導入（Issue #716対応）

**既存コンポーネント活用により大幅に簡略化**

1. `ResponseSuccessCriteria`クラス実装（約30行）
   - `ConditionOperationEvaluator`を活用した条件評価
   - `JsonPathWrapper`によるJSONPath評価
   - `IdentityVerificationApplicationStatusEvaluator`と同じパターン
2. `ResponseCondition` recordクラス実装（3フィールドのみ）
3. `IdentityVerificationHttpRequestConfig`に設定項目追加
4. E2Eテスト追加（身元確認ステータス遷移テストを参考）

**影響範囲**: 小（既存コンポーネント再利用、新機能追加、既存動作は変更なし）
**実装難易度**: 低（既存パターンの踏襲）

### フェーズ3: ドキュメント整備

1. ステータスコードマッピング方針の文書化
2. ResponseSuccessCriteria設定ガイド作成
3. 外部API連携ベストプラクティス文書化

## ベストプラクティス調査結果

### 1. AWS API Gateway

#### 429 (Too Many Requests)
- **2種類の429**: クォータベース（Limit Exceeded Exception）vs 一時的スロットリング
- **クォータベース**: リトライ無効、クォータ増加 or 次の期間まで待機
- **同時リクエスト制限**: Lambda同時実行数制限（1000）超過時に発生、スロット解放後リトライ可能
- **推奨対策**: キャッシング、リクエスト監視・カウント、重複リクエスト削減

#### 503 (Service Unavailable)
- **30秒タイムアウト制限**: バックエンドが30秒以内に応答できない場合503返却
- **推奨対策**:
  - 指数バックオフ + リトライ
  - 非同期パターン（202 Accepted + 後続取得）
  - CloudWatch/X-Rayで監視・診断

#### ステータスコード保持
AWS API Gatewayはバックエンドのステータスコードをそのまま返却（プロキシ統合モード）

### 2. Google Cloud API Gateway

#### リトライ戦略
- **クライアント側責任**: Gateway自体に自動リトライ機能なし
- **推奨パターン**: 指数バックオフ + ジッター（truncated exponential backoff with jitter）
- **リトライ対象**: 408, 429, 5xx

#### タイムアウト設定
- **デフォルト**: 15秒
- **最大値**: 600秒（API Gateway）
- **設定方法**: `x-google-backend`拡張の`deadline`パラメータ

#### エラー判別
- **ログ確認**: `jsonPayload.responseDetails`が"via_upstream"ならバックエンドエラー

### 3. Stripe API

#### HTTPステータスコード戦略
- **2xx**: 成功
- **4xx**: クライアントエラー（パラメータ不足、課金失敗等）
- **5xx**: サーバーエラー（稀）
- **重要**: **HTTP 200 + エラーパターンは使用しない**

#### クライアントライブラリ
- 自動的に非200レスポンスを例外に変換
- 失敗課金、無効パラメータ、認証エラー、ネットワーク障害を例外として提供

#### Webhook
- **必須**: 2xxレスポンスを即座に返却
- **タイムアウト**: 遅延すると失敗マーク、複数日後に自動無効化

### 4. RFC 6749 (OAuth 2.0標準)

#### エラーレスポンスフォーマット（Section 5.2）

**HTTPステータス**: 400 Bad Request（`invalid_client`は401可）

**必須パラメータ**:
```json
{
  "error": "invalid_request",              // 必須: エラーコード
  "error_description": "詳細説明（任意）",  // 任意: 人間可読説明
  "error_uri": "https://..."                // 任意: 詳細情報URL
}
```

**Content-Type**: `application/json;charset=UTF-8`

**エラーコード例**:
- `invalid_request`: 必須パラメータ欠如、不正形式
- `invalid_client`: クライアント認証失敗
- `invalid_grant`: 認可グラント無効
- `unauthorized_client`: クライアント未認可
- `unsupported_grant_type`: グラントタイプ非サポート

### 5. GitHub API

#### Rate Limiting (429 or 403)
- **レスポンス**: 403 Forbidden または 429 Too Many Requests
- **ヘッダー**: `x-ratelimit-remaining: 0`
- **セカンダリレート制限**: 同様に403/429返却

#### 推奨リトライ戦略
1. **retry-afterヘッダー存在**: 指定秒数待機
2. **x-ratelimit-remaining: 0**: `x-ratelimit-reset`（UTC epoch秒）まで待機
3. **その他**: 最低1分待機
4. **継続失敗**: 指数的増加待機

#### 特殊ケース
- **githubusercontent.com**: APIと別レート制限（認証ユーザーでも）
- **CI/CD注意**: githubusercontent.comからのファイル取得で予期しない429

### 6. Twilio API

#### HTTP 200 + エラーコードパターン
- **Verify SNA**: HTTP 200 OKだが、レスポンス内`error_code`が非0の場合はエラー
- **Studio API v1**: 既存アクティブ実行時に200 OK + 既存実行返却（v2は409 Conflict）
- **一般的には**: 適切なHTTPステータスコード使用（400, 404, 500等）

#### エラーハンドリング
- HTTPステータスコードと詳細エラーメッセージの組み合わせ
- エラーコード辞書とドキュメントリンク提供

### 7. Kong Gateway

#### Rate Limiting (429)
- **レスポンス**: HTTP 429 + "API rate limit exceeded"
- **3.12+新機能**: リクエストスロットリング（遅延+リトライ）
  - 制限超過時、即座拒否せず遅延・リトライ
  - 最大リトライ超過 or 待機室満杯時に429返却

#### リトライポリシー
- **MeshRetry**: 429を含む特定ステータスコードでリトライ可能
- **Rate-Limited Back-Off**: `retry-after`や`x-ratelimit-reset`ヘッダー値使用

#### クライアント指導
- レート制限ヘッダーでタイムウィンドウ経過後リトライ推奨

## ベストプラクティスまとめ

### 1. HTTPステータスコード方針

| 原則 | 推奨アプローチ |
|------|--------------|
| **基本方針** | 適切なHTTPステータスコード使用（Stripe, GitHub, OAuth 2.0） |
| **例外ケース** | 特定API（Twilio Verify SNA）やレガシーAPI（Studio v1）のみHTTP 200 + error code |
| **429 Rate Limit** | 必ず429返却、retry-afterヘッダー推奨 |
| **503 Unavailable** | 一時的障害、指数バックオフ必須 |

### 2. リトライ戦略

| 要素 | 推奨実装 |
|------|----------|
| **基本戦略** | 指数バックオフ + ジッター（全サービス共通） |
| **リトライ対象** | 408, 429, 5xx（transient errors） |
| **リトライ禁止** | 4xx（429除く）- クライアント起因エラー |
| **ヘッダー優先度** | 1. `retry-after` > 2. `x-ratelimit-reset` > 3. デフォルト待機 |
| **実装場所** | クライアント側責任（Gateway自体には実装しない） |

### 3. HTTP 200 + エラーパターン対応

| 判定 | 実装要否 |
|------|---------|
| **主要サービス** | Stripe, GitHub, OAuth 2.0は不使用 → **優先度低** |
| **特殊ケース** | Twilio等の特定API → **設定可能な機能として実装** |
| **推奨実装** | `ResponseSuccessCriteria`（任意設定、デフォルト無効） |

### 4. ステータスコード保持の重要性

- **AWS API Gateway**: バックエンドコードそのまま返却
- **Kong Gateway**: 詳細なレート制限ヘッダー提供
- **GitHub API**: 複数ヘッダーで詳細情報提供
- **結論**: **元のステータスコードとヘッダー情報を失わないことが重要**

## 決定事項：ステータスコードマッピング方針

### 方針決定の根拠

ベストプラクティス調査結果から以下の知見を得た：

1. **主要サービスは適切なHTTPステータスコード使用**（Stripe, GitHub, OAuth 2.0）
2. **HTTP 200 + エラーパターンは例外的**（Twilio Verify SNA等の特定API）
3. **ステータスコード保持が業界標準**（AWS API Gateway等）
4. **リトライにはヘッダー情報が必須**（retry-after, x-ratelimit-reset）

### Issue #544: HTTPステータスコード情報喪失問題

#### ✅ 採用方針: パターンB（ステータスコード保持）

**決定内容**:
```java
public class IdentityVerificationExecutionResult {
  IdentityVerificationExecutionStatus status;  // OK/CLIENT_ERROR/SERVER_ERROR（カテゴリ）
  int statusCode;  // ← 追加：元のHTTPステータスコード
  Map<String, Object> result;

  // 判定メソッド追加
  public boolean isRateLimited() { return statusCode == 429; }
  public boolean isServiceUnavailable() { return statusCode == 503; }
  public boolean isGatewayTimeout() { return statusCode == 504; }
}
```

**採用理由**:
- ✅ 既存の`status`フィールドとの互換性維持（破壊的変更なし）
- ✅ 詳細なステータスコード情報を保持
- ✅ 必要に応じて判定メソッドを追加可能（拡張性）
- ✅ Enumの肥大化を防ぐ（保守性）
- ✅ AWS API Gateway等の業界標準に準拠

**却下した代替案**:
- ❌ パターンA（Enum詳細化）: HTTPステータスコード全体（数十個）をEnumで管理 → 保守性低

**実装優先度**: **高**（情報喪失は重大な問題）

**影響範囲**: 小（内部実装のみ、API変更なし）

### Issue #716: HTTP 200 + Body内エラーパターン未対応

#### ✅ 採用方針: パターンC（ResponseSuccessCriteria導入、任意設定）

**決定内容**:
```java
// 既存コンポーネント再利用による簡略実装
public class ResponseSuccessCriteria {
  List<ResponseCondition> conditions;
  ConditionMatchMode matchMode;  // ALL (AND) or ANY (OR)

  public boolean evaluate(JsonPathWrapper responseBody) {
    if (conditions == null || conditions.isEmpty()) {
      return true;  // 設定なし = デフォルト動作（HTTP statusのみ判定）
    }

    return matchMode == ConditionMatchMode.ALL
        ? conditions.stream().allMatch(c -> evaluateCondition(c, responseBody))
        : conditions.stream().anyMatch(c -> evaluateCondition(c, responseBody));
  }

  private boolean evaluateCondition(ResponseCondition condition, JsonPathWrapper json) {
    Object actualValue = json.readRaw(condition.path());
    return ConditionOperationEvaluator.evaluate(
        actualValue, condition.operation(), condition.value());
  }
}

public record ResponseCondition(
    String path,                  // JSONPath（例: "$.status"）
    ConditionOperation operation, // 既存enum使用（EQ, NE, EXISTS等12種類）
    Object value                  // 期待値
) {}
```

**設定例**:
```json
{
  "execution": {
    "type": "http_request",
    "http_request": {
      "url": "https://external-api.com/verify",
      "method": "POST",
      "response_success_criteria": {
        "conditions": [
          {"path": "$.status", "operation": "eq", "value": "success"},
          {"path": "$.error", "operation": "missing"}
        ],
        "match_mode": "all"
      }
    }
  }
}
```

**採用理由**:
- ✅ 主要サービス（Stripe, GitHub, OAuth 2.0）は不使用 → 優先度中
- ✅ 特殊ケース（Twilio Verify SNA等）に対応可能
- ✅ 既存コンポーネント再利用で実装量30行（70%削減）
- ✅ 設定ベースで柔軟に対応（外部APIごとの独自パターン）
- ✅ 下位互換性維持（設定なし = 現在の動作）
- ✅ 身元確認ステータス遷移と同じパターン（一貫性）

**既存コンポーネント活用**:
- `ConditionOperationEvaluator` - 12種類の演算子評価（本番稼働実績あり）
- `ConditionOperation` - 演算子enum定義
- `JsonPathWrapper` - JSONPath評価
- `IdentityVerificationApplicationStatusEvaluator` - 実装パターン参照

**実装優先度**: **中**（特殊ケース対応、任意設定）

**影響範囲**: 小（新機能追加、既存動作は変更なし）

### 実装順序の決定

**フェーズ1優先**: Issue #544（ステータスコード保持）
- 理由: 情報喪失は重大、影響範囲小、実装容易
- 期間: 1〜2日

**フェーズ2**: Issue #716（ResponseSuccessCriteria）
- 理由: 特殊ケース対応、既存コンポーネント活用で低リスク
- 期間: 2〜3日

**フェーズ3**: ドキュメント整備
- 期間: 1日

### 却下した選択肢

| 選択肢 | 却下理由 |
|-------|---------|
| **何もしない** | 情報喪失による実運用への悪影響（Rate Limit対応不可等） |
| **Issue #716のみ実装** | ステータスコード保持がより重要（業界標準） |
| **パターンA（Enum詳細化）** | 保守性低下、拡張時の影響範囲大 |
| **完全新規実装** | 既存コンポーネント（ConditionOperationEvaluator等）を活用すべき |

## 次のステップ

## 実装計画

### フェーズ1: ステータスコード保持実装（Issue #544）

**期間**: 1〜2日
**優先度**: 高
**担当者**: TBD

#### 1.1 コア実装（1日目）

**タスク1**: `IdentityVerificationExecutionResult`拡張
- **ファイル**: `libs/idp-server-core-extension-ida/src/main/java/.../IdentityVerificationExecutionResult.java`
- **変更内容**:
  ```java
  public class IdentityVerificationExecutionResult {
    IdentityVerificationExecutionStatus status;
    int statusCode;  // ← 追加
    Map<String, Object> result;

    // 判定メソッド追加
    public boolean isRateLimited() { return statusCode == 429; }
    public boolean isServiceUnavailable() { return statusCode == 503; }
    public boolean isGatewayTimeout() { return statusCode == 504; }
  }
  ```

**タスク2**: `IdentityVerificationApplicationHttpRequestExecutor`修正
- **ファイル**: `libs/idp-server-core-extension-ida/src/main/java/.../IdentityVerificationApplicationHttpRequestExecutor.java`
- **変更内容** (lines 67-77付近):
  ```java
  // 修正前
  private IdentityVerificationExecutionStatus resolveStatus(HttpRequestResult httpRequestResult) {
    if (httpRequestResult.isClientError()) {
      return IdentityVerificationExecutionStatus.CLIENT_ERROR;
    }
    // ...
  }

  // 修正後
  private IdentityVerificationExecutionResult createExecutionResult(HttpRequestResult httpRequestResult) {
    IdentityVerificationExecutionStatus status = resolveStatus(httpRequestResult);
    int statusCode = httpRequestResult.statusCode();  // ← 保持
    return new IdentityVerificationExecutionResult(status, statusCode, ...);
  }
  ```

**タスク3**: Handler層修正
- **ファイル**: `libs/idp-server-core-extension-ida/src/main/java/.../IdentityVerificationApplicationHandler.java`
- **変更内容**: `statusCode`を参照した詳細判定
  ```java
  if (result.isRateLimited()) {
    // Retry-After情報を含むエラーレスポンス
  } else if (result.isServiceUnavailable()) {
    // 503固有のエラーハンドリング
  }
  ```

#### 1.2 テスト実装（1日目〜2日目）

**単体テスト**:
- `IdentityVerificationExecutionResultTest.java`
  - `isRateLimited()`が429で`true`
  - `isServiceUnavailable()`が503で`true`
  - `isGatewayTimeout()`が504で`true`

**E2Eテスト**:
- `scenario-05-identity_verification-application.test.js`
  - 外部API 429レスポンス → statusCode保持確認
  - 外部API 503レスポンス → statusCode保持確認

#### 1.3 フォーマット・ビルド（2日目）

```bash
./gradlew spotlessApply
./gradlew build && ./gradlew test
cd e2e && npm test
```

### フェーズ2: ResponseSuccessCriteria実装（Issue #716）

**期間**: 2〜3日
**優先度**: 中
**担当者**: TBD

#### 2.1 コア実装（1日目）

**タスク1**: `ResponseSuccessCriteria`クラス実装
- **ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/http/ResponseSuccessCriteria.java` (新規)
- **実装内容**: 約30行
  ```java
  public class ResponseSuccessCriteria {
    List<ResponseCondition> conditions;
    ConditionMatchMode matchMode;

    public boolean evaluate(JsonPathWrapper responseBody) {
      if (conditions == null || conditions.isEmpty()) {
        return true;
      }
      return matchMode == ConditionMatchMode.ALL
          ? conditions.stream().allMatch(c -> evaluateCondition(c, responseBody))
          : conditions.stream().anyMatch(c -> evaluateCondition(c, responseBody));
    }

    private boolean evaluateCondition(ResponseCondition condition, JsonPathWrapper json) {
      Object actualValue = json.readRaw(condition.path());
      return ConditionOperationEvaluator.evaluate(
          actualValue, condition.operation(), condition.value());
    }
  }
  ```

**タスク2**: `ResponseCondition` record実装
- **ファイル**: 同上
- **実装内容**: 3行
  ```java
  public record ResponseCondition(
      String path,
      ConditionOperation operation,
      Object value
  ) {}
  ```

**タスク3**: `ConditionMatchMode` enum実装
- **ファイル**: 同上
- **実装内容**: 2行
  ```java
  public enum ConditionMatchMode { ALL, ANY }
  ```

#### 2.2 設定統合（1日目〜2日目）

**タスク4**: `IdentityVerificationHttpRequestConfig`拡張
- **ファイル**: `libs/idp-server-core-extension-ida/src/main/java/.../IdentityVerificationHttpRequestConfig.java`
- **変更内容**:
  ```java
  ResponseSuccessCriteria responseSuccessCriteria;  // ← 追加

  public ResponseSuccessCriteria responseSuccessCriteria() {
    return responseSuccessCriteria;
  }
  ```

**タスク5**: `HttpRequestExecutor`修正
- **ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/http/HttpRequestExecutor.java`
- **変更内容**: `ResponseSuccessCriteria`をリクエスト時に渡す
  ```java
  HttpRequestResult execute(HttpRequest request, ResponseSuccessCriteria criteria) {
    // ...
    return new HttpRequestResult(statusCode, headers, body, criteria);
  }
  ```

**タスク6**: `HttpRequestResult`拡張
- **ファイル**: 同上
- **変更内容**:
  ```java
  ResponseSuccessCriteria successCriteria;

  public boolean isSuccess() {
    if (!isHttpSuccess()) {
      return false;
    }
    if (successCriteria != null) {
      return successCriteria.evaluate(new JsonPathWrapper(body));
    }
    return true;
  }
  ```

#### 2.3 設定例・テスト（2日目〜3日目）

**設定例追加**:
- **ファイル**: `config/examples/e2e/test-tenant/identity/investment-account-opening.json`
- **追加内容**:
  ```json
  "execution": {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4000/apply",
      "method": "POST",
      "response_success_criteria": {
        "conditions": [
          {"path": "$.status", "operation": "eq", "value": "success"}
        ],
        "match_mode": "all"
      }
    }
  }
  ```

**E2Eテスト**:
- `scenario-05-identity_verification-application.test.js`
  - HTTP 200 + `{"status": "error"}` → エラー判定
  - HTTP 200 + `{"status": "success"}` → 成功判定
  - 設定なし → 既存動作（HTTP statusのみ）

#### 2.4 フォーマット・ビルド（3日目）

```bash
./gradlew spotlessApply
./gradlew build && ./gradlew test
cd e2e && npm test
```

### フェーズ3: ドキュメント整備（1日）

**期間**: 1日
**優先度**: 中
**担当者**: TBD

#### 3.1 ユーザー向けドキュメント

**タスク1**: How-toガイド更新
- **ファイル**: `documentation/docs/content_05_how-to/how-to-16-identity-verification-application.md`
- **追加内容**:
  - ステータスコード保持の説明
  - `ResponseSuccessCriteria`設定ガイド
  - 12種類の演算子説明（EQ, NE, EXISTS等）
  - 設定例（Twilio Verify SNAパターン）

#### 3.2 OpenAPI仕様更新

**タスク2**: レスポンススキーマ更新
- **ファイル**: `documentation/openapi/swagger-resource-owner-ja.yaml`
- **追加内容**:
  - `statusCode`フィールド説明
  - `response_success_criteria`設定スキーマ

#### 3.3 AI開発者向けドキュメント

**タスク3**: 本調査ドキュメント最終化
- **ファイル**: `documentation/docs/content_10_ai_developer/external-api-response-handling-investigation.md`
- **追加内容**:
  - 実装完了記録
  - 実際の実装時の気づき
  - パフォーマンス測定結果

### パフォーマンス評価計画

**測定項目**:
1. **ResponseSuccessCriteria評価時間**
   - 対象: 単一条件、複数条件（AND/OR）
   - 目標: 1ms以下

2. **JSONPath評価のオーバーヘッド**
   - 対象: 単純パス（`$.status`）、ネストパス（`$.result.data.status`）
   - 目標: 0.5ms以下

3. **Regex評価のキャッシュ効果**
   - 対象: 同一パターンの繰り返し評価
   - 目標: 2回目以降は初回の10%以下

**測定方法**:
- JMHベンチマーク
- E2Eテストでのレスポンス時間測定

**合格基準**:
- 全体レスポンス時間への影響: 5%以内

## 参考資料

### HTTPステータスコード標準

- **RFC 7231**: HTTP/1.1 Semantics and Content
  - Section 6: Response Status Codes
  - Section 7.1.3: Retry-After

### エラーハンドリングパターン

- **REST API Design Best Practices** (Microsoft)
- **Google API Design Guide**: Error Handling
- **AWS API Gateway**: Error Response

### 既存実装参照

**Platform層（再利用可能コンポーネント）**:
- `ConditionOperationEvaluator.java` - 12種類の条件演算子評価（EQ, NE, GT, etc.）
- `ConditionOperation.java` - 演算子Enum定義
- `JsonPathWrapper.java` - JSONPath評価ラッパー
- `HttpRequestExecutor.java:469-483` - 例外のステータスコードマッピング
- `HttpRequestExecutor.java:802-857` - Retry-Afterヘッダー解析

**IDA Extension層（実装パターン参照）**:
- `IdentityVerificationApplicationStatusEvaluator.java:124-136` - 条件評価パターン
- `IdentityVerificationApplicationHttpRequestExecutor.java:67-77` - 現状の問題箇所

## 変更履歴

- 2025-01-30: 初版作成（Issue #544, #716の統合調査）
- 2025-01-30: 既存再利用可能コンポーネント発見を追記
  - `ConditionOperationEvaluator` - 12種類の演算子評価
  - `JsonPathWrapper` - JSONPath評価
  - `IdentityVerificationApplicationStatusEvaluator` - 実装パターン参照
  - フェーズ2実装難易度を「中」→「低」、実装量を100行→30行に修正
- 2025-01-30: ベストプラクティス調査完了を追記
  - AWS API Gateway、Google Cloud API Gateway、Kong Gateway調査
  - Stripe API、GitHub API、Twilio API調査
  - RFC 6749 (OAuth 2.0)標準調査
  - ベストプラクティスまとめ4項目追加
  - 次のステップを調査結果に基づき更新
- 2025-01-30: ステータスコードマッピング方針決定を追記
  - Issue #544: パターンB（ステータスコード保持）採用決定
  - Issue #716: パターンC（ResponseSuccessCriteria、任意設定）採用決定
  - 実装順序決定（フェーズ1: #544、フェーズ2: #716、フェーズ3: ドキュメント）
  - 却下した選択肢と理由を明記
- 2025-01-30: 実装計画策定完了
  - フェーズ1（1〜2日）: ステータスコード保持実装、詳細タスク6項目
  - フェーズ2（2〜3日）: ResponseSuccessCriteria実装、詳細タスク6項目
  - フェーズ3（1日）: ドキュメント整備、タスク3項目
  - パフォーマンス評価計画（測定項目3項目、合格基準明記）
  - 総実装期間見積もり: 4〜6日
