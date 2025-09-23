# 現在のエラーハンドリング実装分析

Identity Verification システムの各フェーズにおける現在のエラーハンドリング実装を分析します。

## フェーズ別エラーハンドリング実装状況

### 1. Request フェーズ ✅ **統一済み**

**実装場所**: `IdentityVerificationApplicationRequestValidator.java`

**エラーハンドリング方式**:
- JsonSchemaValidationResult を使用した構造化バリデーション
- `isValid()` + `errors()` のパターン

```java
JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(requestJson);
return new IdentityVerificationApplicationValidationResult(
    validationResult.isValid(), validationResult.errors());
```

**特徴**:
- ✅ 統一されたバリデーション形式
- ✅ 構造化されたエラー情報
- ✅ クライアント向けの詳細エラー

### 2. Pre-hook フェーズ ⚠️ **部分的統一**

#### 2.1 Verification処理

**実装場所**: `IdentityVerificationApplicationRequestVerifiers.java`

**エラーハンドリング方式**:
- `IdentityVerificationApplicationRequestVerifiedResult` 使用
- 未定義verifierの警告ログ + 処理継続

```java
if (verifier == null) {
  log.warn("IdentityVerification verifier is undefined. type: {}", verificationConfig.type());
  continue; // ← 警告のみで処理継続
}

if (verifyResult.isError()) {
  return verifyResult; // ← エラー時は即座にリターン
}
```

**問題点**:
- ⚠️ 未定義verifierを警告のみで処理継続
- ⚠️ エラー種別の分類が不十分

#### 2.2 Additional Parameters処理

**実装場所**: `HttpRequestParameterResolver.java`

**現在の状況**:
```java
// TODO handle error
public Map<String, Object> resolve(...) {
  // エラーハンドリングなし
  HttpRequestResult httpRequestResult = httpRequestExecutor.execute(configuration, baseParams);
  // ...
}
```

**問題点**:
- ❌ エラーハンドリング完全未実装
- ❌ ネットワークエラー、認証エラー等が未処理
- ❌ 設定エラーの検証なし

### 3. Execution フェーズ ✅ **統一済み**

**実装場所**: `IdentityVerificationApplicationHttpRequestExecutor.java`

**エラーハンドリング方式**:
- `IdentityVerificationErrorDetails` による統一エラー形式
- ステータス別分岐処理
- セキュリティ考慮（情報リーク防止）

```java
private IdentityVerificationExecutionStatus resolveStatus(HttpRequestResult httpRequestResult) {
  if (httpRequestResult.isClientError()) {
    return IdentityVerificationExecutionStatus.CLIENT_ERROR;
  }
  if (httpRequestResult.isServerError()) {
    return IdentityVerificationExecutionStatus.SERVER_ERROR;
  }
  return IdentityVerificationExecutionStatus.OK;
}

private Map<String, Object> createErrorResponse(HttpRequestResult httpRequestResult) {
  IdentityVerificationErrorDetails.Builder builder =
      IdentityVerificationErrorDetails.builder()
          .error(IdentityVerificationErrorDetails.ErrorTypes.EXECUTION_FAILED)
          .errorDescription("Identity verification execution failed")
          .addErrorDetail("execution_type", "http_request")
          .addErrorDetail("status_category", statusCategory);
  return builder.build().toMap();
}
```

**特徴**:
- ✅ 統一されたエラー形式
- ✅ セキュリティ考慮（内部情報の秘匿）
- ✅ 構造化されたエラー詳細
- ✅ ステータス別適切な処理

### 4. Post-hook フェーズ 🔍 **要調査**

**実装場所**: `IdentityVerificationPostHookConfig.java`

**現在の状況**:
```java
public class IdentityVerificationPostHookConfig implements JsonReadable {
  List<IdentityVerificationConfig> executions = new ArrayList<>();
  // executionsのみで、verifications/additional_parametersなし
}
```

**問題点**:
- ❌ ドキュメントとの不整合（verifications, additional_parameters未実装）
- 🔍 実際の実行処理が不明

### 5. Transition フェーズ 🔍 **要調査**

**実装場所**: `IdentityVerificationApplicationStatusEvaluator.java`

**エラーハンドリング方式**:
- 条件評価での例外処理は不明
- 条件未定義時の動作パターン

```java
if (!conditionConfig.exists()) {
  return ConditionTransitionResult.UNDEFINED; // ← 未定義時の処理
}
```

### 6. Store/Response フェーズ 🔍 **要調査**

**実装場所**:
- `IdentityVerificationApplication.java` (store処理)
- `IdentityVerificationDynamicResponseMapper.java` (response処理)

**現在の状況**: MappingRule使用時のエラーハンドリングが不明

## 問題の優先度

### 🔥 緊急対応が必要
1. **HttpRequestParameterResolver** - エラーハンドリング完全未実装

### ⚠️ 改善が必要
1. **Pre-hook verification** - 未定義verifierの処理方針
2. **Post-hook** - ドキュメントとの整合性

### 🔍 調査が必要
1. **Transition** - 条件評価エラー処理
2. **Store/Response** - MappingRuleエラー処理

## 統一化のメリット

1. **開発効率向上**: 一貫したエラーハンドリングパターン
2. **保守性向上**: 統一されたエラー形式とログ
3. **ユーザビリティ**: 予測可能なエラーレスポンス
4. **セキュリティ**: 統一されたセキュリティ対策

## 次のステップ

1. **緊急対応**: HttpRequestParameterResolverのエラーハンドリング実装
2. **戦略策定**: 統一エラーハンドリング戦略の定義
3. **段階的実装**: フェーズ別実装計画の策定