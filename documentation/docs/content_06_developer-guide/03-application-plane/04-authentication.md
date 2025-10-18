# 認証実装ガイド

## このドキュメントの目的

**認証インタラクション**（パスワード、SMS、FIDO2等の認証実行）の実装を理解することが目標です。

### 所要時間
⏱️ **約30分**

### 前提知識
- [02. Authorization Flow](./02-authorization-flow.md)

---

## 認証インタラクションとは

Authorization Code Flowの中で、**ユーザー本人確認**を行う処理。

```
Authorization Request
  ↓
[ログイン画面表示]
  ↓
【認証インタラクション】← このドキュメントの対象
  ├─ パスワード認証
  ├─ SMS OTP認証
  ├─ FIDO2認証
  └─ など
  ↓
Authorization Code発行
```

---

## アーキテクチャ全体像

### 30秒で理解する全体像

```
HTTPリクエスト
    ↓
Controller (AuthenticationV1Api) - HTTP処理
    ↓
EntryService (OAuthFlowEntryService.interact()) - オーケストレーション
    ├─ Tenant取得
    ├─ AuthorizationRequest取得
    ├─ OAuthSession取得
    ├─ AuthenticationTransaction取得（認証状態）
    ├─ AuthenticationInteractor選択（Plugin）
    ├─ 認証実行（interact）
    ├─ AuthenticationTransaction更新
    ├─ OAuthSession更新（成功時）
    ├─ ロック処理（失敗回数超過時）
    └─ イベント発行
    ↓
Core層 (AuthenticationInteractor) - 認証ロジック
    ├─ PasswordAuthenticationInteractor
    ├─ SmsAuthenticationInteractor
    ├─ EmailAuthenticationInteractor
    ├─ WebAuthnAuthenticationInteractor
    ├─ FidoUafAuthenticationInteractor
    └─ DeviceAuthenticationInteractor
    ↓
Repository - ユーザー検証・認証状態保存
```

### 主要クラスの責務

| クラス | 層 | 役割 | 実装 |
|--------|---|------|------|
| **AuthenticationV1Api** | Controller | HTTPエンドポイント | [AuthenticationV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/authentication/AuthenticationV1Api.java) |
| **OAuthFlowEntryService** | UseCase | トランザクション・オーケストレーション | [OAuthFlowEntryService.java:164-214](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L164-L214) |
| **AuthenticationInteractor** | Core | 認証ロジック（Plugin拡張可能） | `libs/idp-server-authentication-interactors/` |
| **AuthenticationTransaction** | Core | 認証状態管理（完了した認証・残り認証） | Core Domain |
| **OAuthSession** | Core | セッション管理（認証成功後の情報保持） | Core Domain |

### データフロー

```
┌─────────────────────────────────────────────────────┐
│ 1. AuthenticationTransaction（認証開始時作成）        │
├─────────────────────────────────────────────────────┤
│  - identifier: auth-req-12345                       │
│  - authenticationPolicy: { success_conditions: ... }│
│  - interactionResults: {}  ← 空                      │
│  - request.user: null                               │
└─────────────────────────────────────────────────────┘
    ↓ パスワード認証実行
┌─────────────────────────────────────────────────────┐
│ 2. AuthenticationTransaction（更新後）               │
├─────────────────────────────────────────────────────┤
│  - interactionResults: {                            │
│      "password": {                                  │
│        successCount: 1, failureCount: 0,           │
│        attemptCount: 1, method: "password"         │
│      }                                              │
│    }                                                │
│  - request.user: User(sub=user-12345)               │
│  - isSuccess(): false  ← まだSMS必要                 │
└─────────────────────────────────────────────────────┘
    ↓ SMS認証実行
┌─────────────────────────────────────────────────────┐
│ 3. AuthenticationTransaction（認証完了）             │
├─────────────────────────────────────────────────────┤
│  - interactionResults: {                            │
│      "password": {successCount: 1, ...},           │
│      "sms": {successCount: 1, ...}                 │
│    }                                                │
│  - request.user: User(sub=user-12345)               │
│  - isSuccess(): true  ← 認証完了！                   │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 4. OAuthSession（認証情報をセッションに保存）         │
├─────────────────────────────────────────────────────┤
│  - user: User(sub=user-12345)                       │
│  - authentication: Authentication(                  │
│      methods=["password", "sms"],                  │
│      acr="urn:mace:incommon:iap:silver"           │
│    )                                                │
│  - sessionKey: tenant-12345:client-abc              │
│  → Authorization Code発行で使用                      │
└─────────────────────────────────────────────────────┘
```

---

## AuthenticationTransaction（認証状態管理）

### 役割

認証フロー全体の状態を管理。以下の情報を保持：

- **identifier**: AuthenticationTransaction識別子
- **authorizationIdentifier**: 紐づくAuthorization Request ID
- **request**: 認証リクエスト（ユーザー情報含む）
- **authenticationPolicy**: 認証ポリシー（success/failure/lock条件）
- **interactionResults**: 各認証方式の試行結果（試行回数・成功回数・失敗回数）
- **attributes**: カスタム属性

### 主要メソッド

**実装**: [AuthenticationTransaction.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/AuthenticationTransaction.java)

```java
public class AuthenticationTransaction {

  AuthenticationTransactionIdentifier identifier;
  AuthorizationIdentifier authorizationIdentifier;
  AuthenticationRequest request;
  AuthenticationPolicy authenticationPolicy;
  AuthenticationInteractionResults interactionResults;  // 認証結果の集合
  AuthenticationTransactionAttributes attributes;

  /**
   * 認証完了チェック（success_conditions評価）
   */
  public boolean isSuccess() {
    if (hasAuthenticationPolicy()) {
      AuthenticationResultConditionConfig successConditions =
          authenticationPolicy.successConditions();
      return MfaConditionEvaluator.isSuccessSatisfied(
          successConditions, interactionResults);
    }
    return interactionResults.containsAnySuccess();
  }

  /**
   * 認証失敗チェック（failure_conditions評価）
   */
  public boolean isFailure() {
    if (hasAuthenticationPolicy()) {
      AuthenticationResultConditionConfig failureConditions =
          authenticationPolicy.failureConditions();
      return MfaConditionEvaluator.isFailureSatisfied(
          failureConditions, interactionResults);
    }
    return interactionResults.containsDenyInteraction();
  }

  /**
   * アカウントロックチェック（lock_conditions評価）
   */
  public boolean isLocked() {
    if (hasAuthenticationPolicy()) {
      AuthenticationResultConditionConfig lockConditions =
          authenticationPolicy.lockConditions();
      return MfaConditionEvaluator.isLockedSatisfied(
          lockConditions, interactionResults);
    }
    return false;
  }

  /**
   * 処理完了チェック
   */
  public boolean isComplete() {
    return isSuccess() || isFailure() || isLocked();
  }

  /**
   * 認証結果で更新
   */
  public AuthenticationTransaction updateWith(
      AuthenticationInteractionRequestResult result) {

    Map<String, AuthenticationInteractionResult> resultMap = interactionResults.toMap();

    // ユーザー情報を更新
    AuthenticationRequest updatedRequest = updateWithUser(result);

    // 既存の認証結果がある場合は更新、なければ追加
    if (interactionResults.contains(result.interactionTypeName())) {
      AuthenticationInteractionResult foundResult =
          interactionResults.get(result.interactionTypeName());
      AuthenticationInteractionResult updatedInteraction = foundResult.updateWith(result);
      resultMap.put(result.interactionTypeName(), updatedInteraction);
    } else {
      // 新しい認証結果を追加
      String operationType = result.operationType().name();
      String method = result.method();
      int successCount = result.isSuccess() ? 1 : 0;
      int failureCount = result.isSuccess() ? 0 : 1;
      LocalDateTime interactionTime = SystemDateTime.now();

      AuthenticationInteractionResult newResult =
          new AuthenticationInteractionResult(
              operationType, method, 1, successCount, failureCount, interactionTime);
      resultMap.put(result.interactionTypeName(), newResult);
    }

    AuthenticationInteractionResults updatedResults =
        new AuthenticationInteractionResults(resultMap);

    return new AuthenticationTransaction(
        identifier,
        authorizationIdentifier,
        updatedRequest,
        authenticationPolicy,
        updatedResults,
        attributes);
  }

  /**
   * 最終的なAuthentication生成（Authorization Code発行時に使用）
   */
  public Authentication authentication() {
    if (!isSuccess()) {
      return new Authentication();
    }

    LocalDateTime time = interactionResults.authenticationTime();
    List<String> methods = interactionResults.authenticationMethods();
    String acr = AcrResolver.resolve(authenticationPolicy.acrMappingRules(), methods);

    return new Authentication()
        .setTime(time)
        .addMethods(methods)
        .addAcr(acr);
  }
}
```

**重要な実装ポイント**:

1. **`interactionResults`で管理**: 各認証方式の試行回数・成功回数・失敗回数を記録
2. **条件評価による判定**: `MfaConditionEvaluator`が`success_conditions`等を評価
3. **イミュータブル**: `updateWith()`は新しいインスタンスを返す
4. **最終的な`Authentication`生成**: `authentication()`メソッドがACR等を含むオブジェクトを生成

### 状態遷移

```
[初期状態]
interactionResults: {}
isSuccess(): false
isComplete(): false
    ↓ パスワード認証成功
[パスワード認証完了]
interactionResults: {
  "password": {successCount: 1, failureCount: 0, attemptCount: 1}
}
isSuccess(): false（2要素認証ポリシーの場合）
isComplete(): false  ← まだ完了していない
    ↓ SMS認証成功
[認証完了]
interactionResults: {
  "password": {successCount: 1, failureCount: 0, attemptCount: 1},
  "sms": {successCount: 1, failureCount: 0, attemptCount: 1}
}
isSuccess(): true  ← success_conditions満たした
isComplete(): true
    ↓
Authorization Code発行可能
```

**判定メソッドの使い分け**:
- `isSuccess()`: 認証成功（`success_conditions`を満たす）
- `isFailure()`: 認証失敗（`failure_conditions`を満たす）
- `isLocked()`: アカウントロック（`lock_conditions`を満たす）
- `isComplete()`: 処理完了（success/failure/lockedのいずれか）

---

## エンドポイント

```
POST /{tenant-id}/v1/authentications/{authorization-request-id}/{interaction-type}
{
  "username": "user@example.com",
  "password": "secret"
}
```

**実装**: [AuthenticationV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/authentication/AuthenticationV1Api.java)

---

## 認証方式（Interaction Type）

| Type | 説明 | リクエストパラメータ |
|------|------|------------------|
| `password` | パスワード認証 | `username`, `password` |
| `sms` | SMS OTP認証 | `phone_number`, `otp_code` |
| `email` | Email OTP認証 | `email`, `otp_code` |
| `webauthn` | WebAuthn/FIDO2 | `credential`, `authenticator_data`, `signature` |
| `fido_uaf` | FIDO UAF | `uaf_response` |
| `device` | デバイス認証 | `device_id` |

**設定駆動**: テナントごとに有効な認証方式を設定可能

---

## 実装の全体フロー

```
1. [ユーザー] ログイン画面で認証情報入力
   ↓
2. [フロントエンド] 認証リクエスト送信
POST /{tenant-id}/v1/authentications/{auth-request-id}/password
{
  "username": "user@example.com",
  "password": "secret123"
}
   ↓
3. [AuthenticationV1Api] リクエスト受信
   ↓
4. [OAuthFlowEntryService.interact()] 呼び出し
   ↓
5. [OAuthProtocol] AuthenticationInteractor選択
   ↓
6. [PasswordAuthenticationInteractor] パスワード検証
   ↓
7. [AuthenticationTransaction] 更新（認証状態を記録）
   ↓
8. [レスポンス] 認証結果返却
{
  "status": "authenticated",
  "next_step": "authorize"  // または "additional_authentication"
}
```

---

## EntryService実装

**実装**: [OAuthFlowEntryService.java:164-214](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L164-L214)

### 実際の処理フロー（10ステップ）

```java
@Transaction
public class OAuthFlowEntryService implements OAuthFlowApi {

  OAuthProtocols oAuthProtocols;
  OAuthSessionDelegate oAuthSessionDelegate;
  AuthenticationInteractors authenticationInteractors;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;
  AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository;
  UserQueryRepository userQueryRepository;
  OAuthFlowEventPublisher eventPublisher;
  UserLifecycleEventPublisher userLifecycleEventPublisher;

  @Override
  public AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {

    // 1. Tenant取得
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // 2. AuthorizationRequest取得
    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    // 3. OAuthSession取得または初期化
    OAuthSession oAuthSession =
        oAuthSessionDelegate.findOrInitialize(authorizationRequest.sessionKey());

    // 4. AuthenticationTransaction取得
    AuthorizationIdentifier authorizationIdentifier =
        new AuthorizationIdentifier(authorizationRequestIdentifier.value());
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);

    // 5. AuthenticationInteractor選択（Plugin）
    AuthenticationInteractor interactor = authenticationInteractors.get(type);

    // 6. 認証実行
    AuthenticationInteractionRequestResult result =
        interactor.interact(  // ← authenticate()ではなくinteract()
            tenant,
            authenticationTransaction,
            type,
            request,
            requestAttributes,
            userQueryRepository);

    // 7. AuthenticationTransaction更新
    AuthenticationTransaction updatedTransaction =
        authenticationTransaction.updateWith(result);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

    // 8. OAuthSession更新（認証成功時）
    if (result.isSuccess()) {
      OAuthSession updated =
          oAuthSession.didAuthentication(result.user(), updatedTransaction.authentication());
      oAuthSessionDelegate.updateSession(updated);
    }

    // 9. ロック処理（失敗回数超過時）
    if (updatedTransaction.isLocked()) {
      UserLifecycleEvent userLifecycleEvent =
          new UserLifecycleEvent(tenant, updatedTransaction.user(), UserLifecycleType.LOCK);
      userLifecycleEventPublisher.publish(userLifecycleEvent);
    }

    // 10. イベント発行（Security Event）
    eventPublisher.publish(
        tenant, authorizationRequest, result.user(), result.eventType(), requestAttributes);

    return result;
  }
}
```

### レスポンス構造

**実装**: [PasswordAuthenticationInteractor.java:67-91](../../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/password/PasswordAuthenticationInteractor.java#L67-L91)

レスポンスは`AuthenticationInteractionRequestResult.response()`から取得されます（`Map<String, Object>`）。

#### 認証成功（単一認証の場合）

```json
{
  "user": {
    "sub": "user-12345",
    "email": "user@example.com",
    "name": "John Doe",
    "email_verified": true,
    "phone_number": "+81-90-1234-5678"
  }
}
```

**実装**: Interactorが`response.put("user", user.toMap())`で構築

**HTTP Status**: `200 OK` (`AuthenticationInteractionStatus.SUCCESS`)

---

#### 認証成功（追加認証必要な場合）

追加認証が必要な場合も同じレスポンス構造ですが、フロントエンドは`AuthenticationTransaction.isAuthenticated()`をチェックして次のステップを判定します。

```json
{
  "user": {
    "sub": "user-12345",
    "email": "user@example.com",
    "name": "John Doe"
  }
}
```

**判定ロジック（フロントエンド側）**:
```javascript
// AuthenticationTransactionを別途取得して判定
GET /{tenant-id}/v1/authentications/{auth-req-id}
→ {
  "is_authenticated": false,
  "completed_methods": ["password"],
  "authentication_policy": {
    "success_conditions": { ... }
  }
}

// is_authenticated=false → 追加認証が必要
```

---

#### 認証失敗

```json
{
  "error": "invalid_request",
  "error_description": "user is not found or invalid password"
}
```

**実装**: Interactorが`response.put("error", ...)`で構築

**HTTP Status**: `400 Bad Request` (`AuthenticationInteractionStatus.CLIENT_ERROR`)

**エラーコード種類**:
- `invalid_request`: ユーザーが見つからない、パスワード不正
- `invalid_otp`: OTPコードが不正
- `otp_expired`: OTPコードが期限切れ
- `device_not_found`: デバイスが見つからない

---

#### アカウントロック

アカウントロックは`AuthenticationTransaction.isLocked()`で判定され、`UserLifecycleEvent`が発行されます。

**レスポンス**（ロック後の認証試行時）:
```json
{
  "error": "account_locked",
  "error_description": "Account has been locked due to too many failed attempts"
}
```

**HTTP Status**: `403 Forbidden`

**ロック処理の流れ**:
1. 失敗回数が`lock_conditions`を満たす（例: 5回）
2. `AuthenticationTransaction.isLocked() = true`
3. `UserLifecycleEvent(type=LOCK)`発行
4. 次回の認証試行時に`account_locked`エラー

### ポイント

- ✅ **10ステップの詳細処理**: Tenant取得からイベント発行まで
- ✅ **OAuthSession管理**: 認証成功時にセッション更新
- ✅ **ロック処理**: 失敗回数超過時にUserLifecycleEvent発行
- ✅ **Security Event発行**: 全ての認証試行を記録
- ✅ **Plugin選択**: AuthenticationInteractorを動的に選択

---

## AuthenticationInteractor（Plugin）

### Password認証の例

**実装**: [PasswordAuthenticationInteractor.java](../../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/password/PasswordAuthenticationInteractor.java)

```java
public class PasswordAuthenticationInteractor implements AuthenticationInteractor {

  UserQueryRepository userQueryRepository;
  PasswordVerificationDelegation passwordVerificationDelegation;

  @Override
  public AuthenticationInteractionType type() {
    return AuthenticationInteractionType.PASSWORD;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(  // ← authenticate()ではない！
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    // 1. リクエストからusername/password取得
    String username = request.optValueAsString("username", "");
    String password = request.optValueAsString("password", "");

    // 2. ユーザー検索
    User user = userQueryRepository.findByEmail(tenant, username);

    if (user == null) {
      return AuthenticationInteractionRequestResult.failed("user_not_found");
    }

    // 3. パスワード検証
    boolean verified = passwordVerificationDelegation.verify(password, user.hashedPassword());

    if (!verified) {
      return AuthenticationInteractionRequestResult.failed("invalid_password");
    }

    // 4. 成功
    Authentication authentication = new Authentication(
        AuthenticationInteractionType.PASSWORD,
        SystemDateTime.now());

    return AuthenticationInteractionRequestResult.success(user, authentication);
  }
}
```

**ポイント**:
- ✅ `interact()`メソッド（`authenticate()`ではない）
- ✅ `AuthenticationInteractionRequestResult`を返却
- ✅ 成功時は`Authentication`オブジェクトを作成

---

### SMS OTP認証の例

**実装**: [SmsAuthenticationInteractor.java](../../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/sms/SmsAuthenticationInteractor.java)

```java
public class SmsAuthenticationInteractor implements AuthenticationInteractor {

  @Override
  public AuthenticationInteractionType type() {
    return AuthenticationInteractionType.SMS;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    // 1. OTPコード取得
    String otpCode = request.optValueAsString("otp_code", "");

    // 2. セッションから期待値取得
    String expectedOtp = transaction.smsOtpCode();

    // 3. OTP検証
    if (!otpCode.equals(expectedOtp)) {
      return AuthenticationInteractionRequestResult.failed("invalid_otp");
    }

    // 4. 有効期限チェック
    if (transaction.isSmsOtpExpired()) {
      return AuthenticationInteractionRequestResult.failed("otp_expired");
    }

    // 5. 成功
    User user = transaction.user();
    Authentication authentication = new Authentication(
        AuthenticationInteractionType.SMS,
        SystemDateTime.now());

    return AuthenticationInteractionRequestResult.success(user, authentication);
  }
}
```

**ポイント**:
- ✅ OTPコードは`AuthenticationTransaction`に保存されている
- ✅ 有効期限チェックも`AuthenticationTransaction`で実行
- ✅ パスワード認証後の追加認証として使用される

---

## 認証ポリシー（複数認証）

**実装**: [AuthenticationPolicy.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/policy/AuthenticationPolicy.java)

### AuthenticationPolicyの構造

認証ポリシーは**条件ベース**で定義されます（簡略化された`minimum_methods`ではありません）。

#### 実際のデータ構造

```json
{
  "priority": 1,
  "available_methods": ["password", "sms", "webauthn", "device"],
  "success_conditions": {
    "any_of": [
      [
        {"path": "$.methods", "type": "array", "operation": "contains", "value": "password"},
        {"path": "$.methods", "type": "array", "operation": "contains", "value": "sms"}
      ],
      [
        {"path": "$.methods", "type": "array", "operation": "contains", "value": "webauthn"}
      ]
    ]
  },
  "failure_conditions": {
    "any_of": [
      [
        {"path": "$.failure_count", "type": "number", "operation": "gte", "value": 3}
      ]
    ]
  },
  "lock_conditions": {
    "any_of": [
      [
        {"path": "$.failure_count", "type": "number", "operation": "gte", "value": 5}
      ]
    ]
  }
}
```

#### フィールド説明

| フィールド | 型 | 説明 |
|-----------|---|------|
| `priority` | number | ポリシーの優先順位（複数ポリシー時、低い値が優先） |
| `available_methods` | array | 使用可能な認証方式のリスト |
| `success_conditions` | object | 認証成功の条件（`any_of`で複数パターン） |
| `failure_conditions` | object | 認証失敗の条件 |
| `lock_conditions` | object | アカウントロックの条件 |

#### 条件（Condition）の構造

各条件は**JSONPath + 演算子**で定義：

```json
{
  "path": "$.methods",        // チェック対象（JSONPath）
  "type": "array",            // データ型（array/number/string等）
  "operation": "contains",    // 演算子
  "value": "password"         // 期待値
}
```

**サポートされる演算子**:
- `contains`: 配列に値を含む
- `eq`: 等しい
- `ne`: 等しくない
- `gte`: 以上
- `lte`: 以下
- `gt`: より大きい
- `lt`: より小さい

### 動作例

#### 例1: パスワード + SMS の2要素認証

```json
{
  "success_conditions": {
    "any_of": [
      [
        {"path": "$.methods", "operation": "contains", "value": "password"},
        {"path": "$.methods", "operation": "contains", "value": "sms"}
      ]
    ]
  }
}
```

**動作**:
1. パスワード認証のみ → `isAuthenticated() = false`（SMSが必要）
2. パスワード + SMS → `isAuthenticated() = true`（認証完了）

#### 例2: WebAuthn単独 OR パスワード+SMS（`any_of`）

```json
{
  "success_conditions": {
    "any_of": [
      [
        {"path": "$.methods", "operation": "contains", "value": "webauthn"}
      ],
      [
        {"path": "$.methods", "operation": "contains", "value": "password"},
        {"path": "$.methods", "operation": "contains", "value": "sms"}
      ]
    ]
  }
}
```

**動作**:
- WebAuthn単独 → 認証完了
- パスワード + SMS → 認証完了
- パスワードのみ → 追加認証必要

#### 例3: 失敗回数によるロック

```json
{
  "failure_conditions": {
    "any_of": [
      [{"path": "$.failure_count", "operation": "gte", "value": 3}]
    ]
  },
  "lock_conditions": {
    "any_of": [
      [{"path": "$.failure_count", "operation": "gte", "value": 5}]
    ]
  }
}
```

**動作**:
- 3回失敗 → 認証失敗ステータス
- 5回失敗 → アカウントロック（UserLifecycleEvent発行）

---

## E2Eテスト例

```javascript
describe('Authentication Interaction', () => {
  let tenantId = '18ffff8d-8d97-460f-a71b-33f2e8afd41e';
  let authReqId;

  beforeAll(async () => {
    // Authorization Request実行
    const authResponse = await axios.get(
      `http://localhost:8080/${tenantId}/v1/authorizations`,
      {
        params: {
          response_type: 'code',
          client_id: 'test-client',
          redirect_uri: 'https://client.example.com/callback',
          scope: 'openid'
        },
        maxRedirects: 0,
        validateStatus: (status) => status === 302
      }
    );

    const url = new URL(authResponse.headers.location, 'http://localhost');
    authReqId = url.searchParams.get('id');
  });

  test('should authenticate with password', async () => {
    const response = await axios.post(
      `http://localhost:8080/${tenantId}/v1/authentications/${authReqId}/password`,
      {
        username: 'user@example.com',
        password: 'password123'
      }
    );

    expect(response.status).toBe(200);
    expect(response.data.status).toBe('authenticated');
  });

  test('should require additional authentication for 2FA', async () => {
    // パスワード認証
    const passwordResponse = await axios.post(
      `http://localhost:8080/${tenantId}/v1/authentications/${authReqId}/password`,
      {
        username: 'user@example.com',
        password: 'password123'
      }
    );

    expect(passwordResponse.data.status).toBe('additional_authentication_required');
    expect(passwordResponse.data.next_methods).toContain('sms');

    // SMS OTP認証
    const smsResponse = await axios.post(
      `http://localhost:8080/${tenantId}/v1/authentications/${authReqId}/sms`,
      {
        otp_code: '123456'
      }
    );

    expect(smsResponse.data.status).toBe('authenticated');
  });
});
```

---

## よくあるエラー

### エラー1: `invalid_request` - 認証失敗

**実際のレスポンス**:
```json
{
  "error": "invalid_request",
  "error_description": "user is not found or invalid password"
}
```

**原因**: パスワード・OTPコード等が不正

**解決策**: 正しい認証情報を使用

**実装**: [PasswordAuthenticationInteractor.java](../../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/password/PasswordAuthenticationInteractor.java)

### エラー2: 追加認証必要

**実際のレスポンス**:
```json
{
  "status": "authenticated",
  "next_step": "otp",
  "message": "Additional authentication required"
}
```
HTTP Status: 200 OK

**重要**: 追加認証が必要な場合、エラーではなく**正常ステータス（200）**で次の認証ステップを返す

**原因**: 認証ポリシーで複数認証が必須

**解決策**: レスポンスの`next_step`に指定された認証方式を実行

---

## 次のステップ

✅ 認証インタラクションの実装を理解した！

### 📖 次に読むべきドキュメント

1. [05. UserInfo実装](./05-userinfo.md) - ユーザー情報取得
2. [06. CIBA Flow実装](./06-ciba-flow.md) - バックチャネル認証

### 🔗 詳細情報

- [AI開発者向け: 認証インタラクター](../../content_10_ai_developer/ai-41-authentication.md)
- [実装ガイド: Authentication Interactions](../04-implementation-guides/impl-06-authentication-interactor.md)

---

**情報源**: [OAuthFlowEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java)
**最終更新**: 2025-10-12
