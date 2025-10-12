# 04-authentication.md レビュー結果と修正案

## 📋 レビュー観点

1. ✅ 実装との一致
2. ✅ アーキテクチャや主要なクラスの説明
3. ✅ 人間が理解できるか

---

## ❌ 発見された問題点

### 問題1: メソッド名の不一致

**ドキュメント（誤り）**:
```java
AuthenticationResult authenticate(...)
```

**実装**:
```java
AuthenticationInteractionRequestResult interact(...)
```

**影響**: コードを読む際に混乱する

---

### 問題2: EntryServiceの処理が簡略化されすぎ

**ドキュメント**: 6ステップ（不完全）
**実装**: 以下が抜けている
- OAuthSession処理（didAuthentication）
- イベント発行（eventPublisher.publish）
- ロック処理（isLocked → UserLifecycleEvent発行）

---

### 問題3: アーキテクチャ全体像がない

以下が不明確：
- Controller → EntryService → Core層の流れ
- AuthenticationTransactionの役割（いつ作られる？いつ更新される？）
- Plugin システムの説明

---

### 問題4: レスポンス構造が曖昧

```json
// ドキュメント（曖昧）
{
  "status": "authenticated",
  "next_step": "authorize"
}
```

実際のレスポンス構造が不明

---

### 問題5: AuthenticationTransactionの説明が遅い

- 246行目で初めて詳細説明
- しかし64行目の全体フローで既に使われている
- 順序が逆

---

## ✅ 修正方針

### 修正1: アーキテクチャセクションを追加（冒頭）

```markdown
## アーキテクチャ全体像

### 30秒で理解する全体像

\`\`\`
HTTPリクエスト
    ↓
Controller (AuthenticationV1Api) - HTTP処理
    ↓
EntryService (OAuthFlowEntryService.interact()) - オーケストレーション
    ├─ AuthenticationTransaction取得（認証状態）
    ├─ AuthenticationInteractor選択（Plugin）
    ├─ 認証実行
    ├─ AuthenticationTransaction更新
    ├─ OAuthSession更新
    ├─ イベント発行
    └─ ロック処理（失敗時）
    ↓
Core層 (AuthenticationInteractor)
    ├─ PasswordAuthenticationInteractor
    ├─ SmsAuthenticationInteractor
    ├─ WebAuthnAuthenticationInteractor
    └─ ... (Plugin拡張可能)
    ↓
Repository - ユーザー検証・認証状態保存
\`\`\`

### 主要クラスの責務

| クラス | 層 | 役割 |
|--------|---|------|
| **AuthenticationV1Api** | Controller | HTTPエンドポイント |
| **OAuthFlowEntryService** | UseCase | トランザクション・オーケストレーション |
| **AuthenticationInteractor** | Core | 認証ロジック（Plugin） |
| **AuthenticationTransaction** | Core | 認証状態管理 |
| **OAuthSession** | Core | セッション管理 |
\`\`\`

---

### 修正2: AuthenticationTransactionを前半で説明

**Before**: 246行目で初登場
**After**: アーキテクチャセクション直後に配置

---

### 修正3: EntryService実装を実際の処理に合わせる

```java
@Override
public AuthenticationInteractionRequestResult interact(...) {

  // 1. Tenant取得
  Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

  // 2. AuthorizationRequest取得
  OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
  AuthorizationRequest authorizationRequest =
      oAuthProtocol.get(tenant, authorizationRequestIdentifier);

  // 3. OAuthSession取得
  OAuthSession oAuthSession =
      oAuthSessionDelegate.findOrInitialize(authorizationRequest.sessionKey());

  // 4. AuthenticationTransaction取得
  AuthenticationTransaction authenticationTransaction =
      authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);

  // 5. AuthenticationInteractor選択（Plugin）
  AuthenticationInteractor interactor = authenticationInteractors.get(type);

  // 6. 認証実行
  AuthenticationInteractionRequestResult result =
      interactor.interact(  // ← authenticate()ではない！
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

  // 8. OAuthSession更新（成功時）
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

  // 10. イベント発行
  eventPublisher.publish(
      tenant, authorizationRequest, result.user(), result.eventType(), requestAttributes);

  return result;
}
```

---

### 修正4: AuthenticationInteractorの実装を正確に

```java
// ❌ 間違い（ドキュメント）
public AuthenticationResult authenticate(...)

// ✅ 正しい（実装）
public AuthenticationInteractionRequestResult interact(
    Tenant tenant,
    AuthenticationTransaction transaction,
    AuthenticationInteractionType type,
    AuthenticationInteractionRequest request,
    RequestAttributes requestAttributes,
    UserQueryRepository userQueryRepository)
```

---

### 修正5: 実際のレスポンス構造を追加

```json
// 認証成功（完了）
{
  "status": "SUCCESS",
  "user": {
    "sub": "user-12345",
    "email": "user@example.com"
  },
  "authentication": {
    "methods": ["password"],
    "completed_at": "2025-10-13T10:00:00Z"
  }
}

// 追加認証必要（2FA）
{
  "status": "ADDITIONAL_AUTHENTICATION_REQUIRED",
  "completed_methods": ["password"],
  "remaining_methods": ["sms", "webauthn"],
  "message": "Additional authentication is required"
}

// 認証失敗
{
  "status": "FAILED",
  "error": "invalid_credentials",
  "message": "Invalid username or password",
  "remaining_attempts": 2
}
```

---

## 📊 修正の優先度

| 修正 | 優先度 | 理由 |
|------|-------|------|
| 修正1: アーキテクチャ全体像 | 🔴 High | 最初に全体像を理解する必要がある |
| 修正2: AuthenticationTransaction前出し | 🔴 High | 全体フローで既に使われている |
| 修正3: EntryService実装修正 | 🔴 High | 実装と不一致 |
| 修正4: メソッド名修正 | 🟡 Medium | コード例が混乱する |
| 修正5: レスポンス構造 | 🟡 Medium | テスト実装時に必要 |

---

## 🎯 修正後の構成案

```markdown
# 認証実装ガイド

## このドキュメントの目的

## 認証インタラクションとは

## アーキテクチャ全体像 ← NEW!
  ### 30秒で理解する全体像
  ### 主要クラスの責務
  ### 処理フロー図

## AuthenticationTransaction（認証状態管理） ← 移動!
  ### 役割
  ### 主要メソッド
  ### 状態遷移

## エンドポイント

## 認証方式（Interaction Type）

## EntryService実装 ← 修正!
  ### 実際の処理フロー（10ステップ）
  ### レスポンス構造 ← NEW!

## AuthenticationInteractor（Plugin） ← 修正!
  ### Password認証の例
  ### SMS OTP認証の例

## 認証ポリシー（複数認証）

## E2Eテスト例

## よくあるエラー

## 次のステップ
```

---

**作成日**: 2025-10-13
**レビュー対象**: documentation/docs/content_06_developer-guide/03-application-plane/04-authentication.md
**優先度**: High（開発者の初期学習に影響）
