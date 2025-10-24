# OAuthFlowEntryService.java 確認状況レポート

**ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java`
**総行数**: 431行
**確認日**: 2025-10-25
**関連Issue**: #800, #801

---

## 📋 メソッド一覧

| メソッド | 行数 | 確認状況 | 脆弱性リスク | 優先度 |
|---------|------|---------|-------------|--------|
| `interact()` | 164-214 | ✅ 詳細確認済み | **Critical** | P0 |
| `authorizeWithSession()` | 348-? | ⚠️ 一部確認 | High | P1 |
| `authorize()` | 283-? | ❌ 未確認 | High | P1 |
| `callbackFederation()` | 248-? | ❌ 未確認 | Medium | P2 |
| `requestFederation()` | 216-? | ❌ 未確認 | Low | P3 |
| `push()` | 102-? | ❌ 未確認 | Low | P3 |
| `request()` | 117-? | ❌ 未確認 | Low | P3 |
| `getViewData()` | 141-? | ❌ 未確認 | Low | P3 |
| `deny()` | 384-? | ❌ 未確認 | Low | P3 |
| `logout()` | 419-? | ❌ 未確認 | Low | P3 |

---

## ✅ 確認済みメソッド

### interact() - Line 164-214

**目的**: 認証Interaction実行（email-authentication, password-authentication等）

**確認済み内容**:

```java
public AuthenticationInteractionRequestResult interact(
    TenantIdentifier tenantIdentifier,
    AuthorizationRequestIdentifier authorizationRequestIdentifier,
    AuthenticationInteractionType type,
    AuthenticationInteractionRequest request,
    RequestAttributes requestAttributes) {

  Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

  // Line 173-175: Authorization Request取得
  OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
  AuthorizationRequest authorizationRequest =
      oAuthProtocol.get(tenant, authorizationRequestIdentifier);

  // Line 177-178: Session取得
  OAuthSession oAuthSession =
      oAuthSessionDelegate.findOrInitialize(authorizationRequest.sessionKey());

  // Line 180-184: Authentication Transaction取得
  AuthenticationInteractor authenticationInteractor = authenticationInteractors.get(type);
  AuthorizationIdentifier authorizationIdentifier =
      new AuthorizationIdentifier(authorizationRequestIdentifier.value());
  AuthenticationTransaction authenticationTransaction =
      authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);

  // Line 186-193: Interactor実行
  AuthenticationInteractionRequestResult result =
      authenticationInteractor.interact(
          tenant,
          authenticationTransaction,
          type,
          request,
          requestAttributes,
          userQueryRepository);

  // Line 195-196: Transaction更新
  AuthenticationTransaction updatedTransaction = authenticationTransaction.updateWith(result);
  authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

  // Line 198-202: 成功時のSession更新
  if (result.isSuccess()) {
    OAuthSession updated =
        oAuthSession.didAuthentication(result.user(), updatedTransaction.authentication());
    oAuthSessionDelegate.updateSession(updated);
  }

  // Line 204-208: アカウントロック処理
  if (updatedTransaction.isLocked()) {
    UserLifecycleEvent userLifecycleEvent =
        new UserLifecycleEvent(tenant, updatedTransaction.user(), UserLifecycleType.LOCK);
    userLifecycleEventPublisher.publish(userLifecycleEvent);
  }

  // Line 210-211: イベント発行
  eventPublisher.publish(
      tenant, authorizationRequest, result.user(), result.eventType(), requestAttributes);

  return result;
}
```

**発見された問題**:

#### 🚨 S16: Session-Transaction バインディング検証欠如

**問題箇所**: Line 177-184

```java
// Session取得
OAuthSession oAuthSession =
    oAuthSessionDelegate.findOrInitialize(authorizationRequest.sessionKey());

// Transaction取得
AuthenticationTransaction authenticationTransaction =
    authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);

// ❌ 問題: この2つの紐付け検証がない
```

**脆弱性**:
- `authorizationRequest.sessionKey()` からSessionKey取得
- しかし、**現在のHTTP SessionとSessionKeyの一致確認がない**
- 攻撃者が他人のTransaction IDを使って、自分のSessionで認証実行可能

**修正案**:
```java
// ✅ 追加すべき検証
String currentSessionId = requestAttributes.sessionId();

if (!oAuthSession.sessionKey().value().equals(currentSessionId)) {
  throw new UnauthorizedException("Session mismatch");
}

// または
if (!authorizationRequest.belongsToCurrentSession(currentSessionId)) {
  throw new UnauthorizedException("Authorization request belongs to different session");
}
```

---

### authorizeWithSession() - Line 348-?

**目的**: セッションを使った認証完了

**確認済み内容** (一部):

```java
// Line 354-357: Authorization Request取得とSession取得
OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
AuthorizationRequest authorizationRequest =
    oAuthProtocol.get(tenant, authorizationRequestIdentifier);
OAuthSession session = oAuthSessionDelegate.find(authorizationRequest.sessionKey());
```

**確認項目**:
- ⚠️ Session検証ロジックがあるか？（未確認）
- ⚠️ Session nullチェックがあるか？（未確認）
- ⚠️ Session-Authorization Request紐付け確認があるか？（未確認）

**要追加確認**: Line 348-383 の全体

---

## ❌ 未確認メソッド（優先度順）

### P1: Critical確認が必要

#### authorize() - Line 283-?

**目的**: 認証完了処理（最終ステップ）

**確認必要事項**:
- Session-Transaction バインディング検証
- Transaction完了後の無効化
- Authorization Code生成時のSession紐付け
- Redirect URI検証

**脆弱性リスク**: High - 認証完了の最終ゲートなので重要

---

#### callbackFederation() - Line 248-?

**目的**: Federation（外部IdP）からのCallback処理

**確認必要事項**:
- State検証（CSRF対策）
- Federation SessionとOAuthSessionの紐付け
- IdP切り替え攻撃の防止

**脆弱性リスク**: Medium-High - 外部IdPからの入力処理

---

### P2: High確認が必要

#### requestFederation() - Line 216-?

**目的**: Federation認証のリクエスト開始

**確認必要事項**:
- Session取得（Line 235-236で確認済み）
- IdP選択の検証

**脆弱性リスク**: Medium

---

### P3: 確認推奨

#### push() - Line 102-?

**目的**: PAR (Pushed Authorization Request) 処理

**確認必要事項**:
- リクエストパラメータの検証
- request_uri生成と紐付け

**脆弱性リスク**: Low-Medium

---

#### request() - Line 117-?

**目的**: Authorization Request処理（OAuth開始）

**確認必要事項**:
- パラメータ検証
- Transaction生成ロジック

**脆弱性リスク**: Low-Medium

---

#### getViewData() - Line 141-?

**目的**: 認可画面用のデータ取得

**確認必要事項**:
- データ取得の認可チェック

**脆弱性リスク**: Low

---

#### deny() - Line 384-?

**目的**: 認可拒否処理

**確認必要事項**:
- Session検証
- Transaction無効化

**脆弱性リスク**: Low

---

#### logout() - Line 419-?

**目的**: ログアウト処理

**確認必要事項**:
- Session無効化
- Token無効化

**脆弱性リスク**: Low

---

## 🔍 詳細確認が必要な箇所

### 1. Session取得パターンの統一性確認

**確認済み**:
- `interact()`: Line 177-178
- `requestFederation()`: Line 235-236
- `authorizeWithSession()`: Line 357
- `deny()`: Line 398

**確認必要**:
- 各メソッドでSession検証ロジックが統一されているか？
- Session nullチェックがあるか？
- Session-AuthorizationRequest紐付け確認があるか？

### 2. Transaction生成・更新・無効化のパターン

**確認済み**:
- `interact()`: Line 183-184 (取得), Line 195-196 (更新)

**確認必要**:
- Transaction生成はどこで？（`request()`メソッド？）
- Transaction完了後の無効化は？（`authorize()`メソッド？）
- Transaction有効期限チェックは？

### 3. Authorization Code生成時のセキュリティ

**確認必要**:
- `authorize()` メソッド内
- Code生成時のSession紐付け
- Code検証時の紐付け確認
- PKCE検証

---

## 📊 確認進捗

| カテゴリ | 確認済み | 未確認 | 合計 | 進捗率 |
|---------|---------|--------|------|--------|
| **Critical** | 1 | 2 | 3 | 33% |
| **High** | 0 | 1 | 1 | 0% |
| **Medium/Low** | 0 | 6 | 6 | 0% |
| **合計** | 1 | 9 | 10 | 10% |

---

## 🎯 次のアクションアイテム

### Phase 1: GA前必須

- [ ] `authorize()` メソッドの詳細確認（Line 283-347）
  - Authorization Code生成
  - Session-Transaction検証
  - Transaction無効化

- [ ] `authorizeWithSession()` メソッドの詳細確認（Line 348-383）
  - Session検証ロジック
  - nullチェック

- [ ] `callbackFederation()` メソッドの詳細確認（Line 248-282）
  - State検証
  - Session紐付け

### Phase 2: GA後

- [ ] `requestFederation()` 確認（Line 216-247）
- [ ] `push()` 確認（Line 102-116）
- [ ] `request()` 確認（Line 117-140）
- [ ] その他のメソッド確認

---

## 📝 確認メモ

### interact() メソッドで発見した問題点

1. **Session-Transaction バインディング検証なし**
   - `authorizationRequest.sessionKey()` からSessionKey取得
   - 現在のHTTP SessionとSessionKeyの一致確認がない

2. **Redis障害時の挙動**
   - `findOrInitialize()` がRedis障害時に新規セッション初期化
   - Session-Transaction紐付けが失われる

3. **エラーハンドリング**
   - Session取得失敗時のエラーハンドリングが不明確

### 今後確認すべき観点

1. **全メソッドで共通**:
   - Session取得時の検証パターン
   - Session nullチェック
   - Session-リソース（Transaction/AuthorizationRequest）の紐付け確認

2. **認証完了系メソッド**:
   - Transaction/AuthorizationRequest無効化
   - Authorization Code生成時のSession紐付け
   - Token発行時の検証

3. **Federation系メソッド**:
   - State検証（CSRF対策）
   - IdP検証
   - Session紐付け

---

**作成日**: 2025-10-25
**作成者**: Claude Code (Security Audit)
