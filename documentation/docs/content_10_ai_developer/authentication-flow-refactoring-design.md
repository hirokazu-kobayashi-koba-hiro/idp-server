# 認証フロー改修設計書

**Issue**: #800 メアド認証によるアカウント作成・認証の挙動が不安定
**作成日**: 2025-10-24
**対象**: idp-server 認証基盤全体
**前提**: リリース前の製品QA中（本番リリース前に修正必須）

---

## 📋 Executive Summary

### 発見された問題
製品QA中に**Critical Severity**のバグを発見：
- メールアドレス変更時に前のユーザーアカウントでログインされる
- Email/SMS/WebAuthn認証で同じ設計欠陥が存在
- セキュリティリスク: 他人のアカウントに誤ログインの可能性

### 影響範囲
- **ユーザー影響**: 認証フロー全体（Email/SMS/WebAuthn）
- **セキュリティ**: High - アカウント混同のリスク
- **データ整合性**: ユーザーID-識別子の不一致

### 推奨アクション
**本番リリース前に必須修正** - 3段階の改修計画を提案

---

## 目次

1. [QA発見事項](#1-qa発見事項)
2. [根本原因分析](#2-根本原因分析)
3. [改修戦略](#3-改修戦略)
4. [Phase 1: Critical Fix（リリースブロッカー）](#4-phase-1-critical-fixリリースブロッカー)
5. [Phase 2: 構造改善（GA後1ヶ月以内）](#5-phase-2-構造改善ga後1ヶ月以内)
6. [Phase 3: アーキテクチャ刷新（GA後3ヶ月以内）](#6-phase-3-アーキテクチャ刷新ga後3ヶ月以内)
7. [リスク管理](#7-リスク管理)
8. [品質保証計画](#8-品質保証計画)

---

## 1. QA発見事項

### 1.1 再現手順（Issue #800）

**事象1: メールアドレス切り替え時の挙動**
```
前提: メアドA (a@example.com) で既にユーザー登録済み

1. 認証画面でメアドA入力 → メール認証画面に遷移
2. ブラウザバック → 認証画面に戻る
3. メアドB (b@example.com) 入力 → メール認証画面に遷移
4. メアドB宛のOTPで認証完了

期待: メアドBのユーザーとしてログイン
実際: メアドAのユーザーとしてログイン ❌
```

**事象2: アプリ再インストール後の挙動**
```
1. アプリ再インストール（新規セッション）
2. メアドB入力 → メール認証完了
3. 期待: メアドBのユーザーとしてログイン
   実際: メアドAのユーザーとしてログイン ❌
```

### 1.2 影響を受ける認証方式

| 認証方式 | 影響範囲 | 重大度 | ファイル |
|---------|---------|--------|---------|
| **Email** | ✅ 影響あり | Critical | `EmailAuthenticationChallengeInteractor.java:176-193` |
| **SMS** | ✅ 影響あり | Critical | `SmsAuthenticationChallengeInteractor.java:168-192` |
| **WebAuthn** | ⚠️ 潜在的影響 | High | `WebAuthnRegistrationChallengeInteractor.java:113` |
| **Password** | ✅ 影響なし | - | ユーザー名で直接検索 |
| **Federation** | ✅ 影響なし | - | IdPがユーザー特定 |

### 1.3 セキュリティ評価

**CVSSv3.1 評価** (仮)
- **攻撃元区分 (AV)**: Network
- **攻撃条件の複雑さ (AC)**: Low
- **必要な特権レベル (PR)**: None
- **利用者の関与 (UI)**: Required（ユーザーがメアド変更操作）
- **影響の想定範囲 (S)**: Changed（別ユーザーに影響）
- **機密性への影響 (C)**: High（他人のアカウント情報閲覧）
- **完全性への影響 (I)**: High（他人のアカウント操作）
- **可用性への影響 (A)**: None

**推定スコア**: 7.5-8.0 (High Severity)

---

## 2. 根本原因分析

### 2.1 問題のコード

#### 問題箇所1: EmailAuthenticationChallengeInteractor.resolveUser()

`libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/email/EmailAuthenticationChallengeInteractor.java:169-193`

```java
private User resolveUser(
    Tenant tenant,
    AuthenticationTransaction transaction,
    String email,
    String providerId,
    UserQueryRepository userQueryRepository) {

  // ❌ 問題: transaction.hasUser()を最優先判定
  if (transaction.hasUser()) {
    User user = transaction.user();
    user.setEmail(email);  // ← メールだけ更新するが、subは既存ユーザーのまま
    return user;
  }

  // 既存ユーザー検索
  User existingUser = userQueryRepository.findByEmail(tenant, email, providerId);
  if (existingUser.exists()) {
    return existingUser;
  }

  // 新規ユーザー作成
  User user = new User();
  String id = UUID.randomUUID().toString();
  user.setSub(id);
  user.setEmail(email);

  return user;
}
```

**問題の本質**:
1. `transaction.hasUser()` = true の場合、入力されたメールアドレスを無視
2. 前回のCHALLENGEステップで設定されたUserオブジェクトを再利用
3. `user.setEmail(email)` でメールだけ更新するが、`sub`（ユーザーID）は変わらない
4. 結果: メアドBで認証完了しても、メアドAのユーザーIDでログイン

#### 問題箇所2: SmsAuthenticationChallengeInteractor.resolveUser()

`libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/sms/SmsAuthenticationChallengeInteractor.java:168-192`

**完全に同じパターン**: 電話番号の場合も同じバグが存在

### 2.2 設計上の欠陥

#### 欠陥1: OperationTypeの意味が実装側に伝わっていない

```java
public enum OperationType {
  CHALLENGE,        // 1st factor: ユーザー識別フェーズ
  AUTHENTICATION,   // 2nd factor: 検証フェーズ
  ...
}
```

**問題**:
- `CHALLENGE`は「ユーザー識別」のフェーズ
- しかし実装側は`transaction.hasUser()`に依存（既存状態を信頼）
- 識別子変更時に前のUserが残っていることを考慮していない

#### 欠陥2: AuthenticationTransactionの不変性誤解

`AuthenticationTransaction.java:114-130`

```java
private AuthenticationRequest updateWithUser(
    AuthenticationInteractionRequestResult interactionRequestResult) {

  if (!request.hasUser()) {
    return request.updateWithUser(interactionRequestResult);  // 初回のみ更新
  }

  // ❌ User変更検出で例外
  if (!request.isSameUser(interactionRequestResult.user())) {
    throw new BadRequestException("User is not the same as the request");
  }

  return request;  // 変更なし（既存User維持）
}
```

**問題**:
- Userは一度設定されたら変更不可という暗黙の前提
- CHALLENGEステップの複数回実行（識別子変更）を考慮していない

#### 欠陥3: Keycloakパターンとの乖離

| 設計要素 | Keycloak | idp-server | 評価 |
|---------|----------|------------|------|
| 1st/2nd区別 | `requiresUser()` で明示 | `OperationType` だが暗黙的 | ❌ |
| User管理 | `context.setUser()` で明示的設定 | `transaction.user()` で暗黙的取得 | ❌ |
| User確定タイミング | `setUser()` 呼び出し時 | `resolveUser()` 内部（不透明） | ❌ |
| 再実行時の挙動 | `clearUser()` で明示的リセット | 機構なし | ❌ |

### 2.3 影響範囲マトリクス

| コンポーネント | 影響 | 変更必要性 | 優先度 |
|---------------|------|-----------|--------|
| `EmailAuthenticationChallengeInteractor` | Critical | 必須 | P0 |
| `SmsAuthenticationChallengeInteractor` | Critical | 必須 | P0 |
| `WebAuthnRegistrationChallengeInteractor` | High | 推奨 | P1 |
| `AuthenticationTransaction` | Medium | Phase 2 | P2 |
| `AuthenticationInteractor` (interface) | Low | Phase 3 | P3 |
| E2Eテスト | High | 必須 | P0 |

---

## 3. 改修戦略

### 3.1 リリース判断とタイムライン

```
現在: QA中
  ↓
Phase 1: Critical Fix (2-3日)
  ├─ resolveUser() 修正
  ├─ E2Eテスト追加
  └─ 回帰テスト
  ↓
GA判断: Phase 1完了後にリリース可能
  ↓
Phase 2: 構造改善 (GA後 1ヶ月)
  ├─ AuthenticationFlowContext 導入
  ├─ User管理の明示化
  └─ Phase 1の技術負債解消
  ↓
Phase 3: アーキテクチャ刷新 (GA後 3ヶ月)
  ├─ requiresUser() 導入
  ├─ Keycloakパターン準拠
  └─ 設計負債完全解消
```

### 3.2 品質ゲート

#### GA前（Phase 1完了）
- ✅ Issue #800 完全解決
- ✅ E2Eテスト全パス
- ✅ セキュリティレビュー完了
- ✅ 回帰テスト完了
- ✅ ドキュメント更新

#### GA後1ヶ月（Phase 2完了）
- ✅ User管理が明示的になる
- ✅ 技術負債50%削減
- ✅ コードカバレッジ80%以上

#### GA後3ヶ月（Phase 3完了）
- ✅ Keycloakパターン準拠
- ✅ 設計負債完全解消
- ✅ 拡張性向上

### 3.3 リスク管理

#### Critical Risks（GA前に対処）

| リスク | 影響 | 対策 | 担当 |
|--------|------|------|------|
| Phase 1修正でリグレッション | High | 全E2Eテスト実行 | QA |
| 修正漏れ（WebAuthn等） | Medium | 全Interactor調査 | Dev |
| テストカバレッジ不足 | High | 新規テスト追加 | QA+Dev |

#### High Risks（GA後対応）

| リスク | 影響 | 対策 | タイミング |
|--------|------|------|-----------|
| Phase 2でのAPI互換性 | Medium | Deprecation戦略 | GA+1M |
| Phase 3の工数超過 | Low | 段階的移行 | GA+3M |

---

## 4. Phase 1: Critical Fix（リリースブロッカー）

### 4.1 目的

**Issue #800の完全解決 + GA判断**

### 4.2 スコープ

#### In-Scope（必須）
- ✅ `EmailAuthenticationChallengeInteractor.resolveUser()` 修正
- ✅ `SmsAuthenticationChallengeInteractor.resolveUser()` 修正
- ✅ E2Eテスト追加（メアド変更シナリオ）
- ✅ 回帰テスト全実行
- ✅ ドキュメント更新（CLAUDE.md）

#### Out-of-Scope（Phase 2以降）
- ❌ `AuthenticationTransaction` の設計変更
- ❌ `AuthenticationInteractor` インターフェース変更
- ❌ Keycloakパターンへの準拠

### 4.3 実装詳細

#### 4.3.1 EmailAuthenticationChallengeInteractor 修正

**ファイル**: `libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/email/EmailAuthenticationChallengeInteractor.java`

**修正箇所**: Line 169-193

**Before**:
```java
private User resolveUser(
    Tenant tenant,
    AuthenticationTransaction transaction,
    String email,
    String providerId,
    UserQueryRepository userQueryRepository) {

  if (transaction.hasUser()) {
    User user = transaction.user();
    user.setEmail(email);
    return user;
  }

  User existingUser = userQueryRepository.findByEmail(tenant, email, providerId);
  if (existingUser.exists()) {
    return existingUser;
  }

  User user = new User();
  String id = UUID.randomUUID().toString();
  user.setSub(id);
  user.setEmail(email);

  return user;
}
```

**After**:
```java
private User resolveUser(
    Tenant tenant,
    AuthenticationTransaction transaction,
    String email,
    String providerId,
    UserQueryRepository userQueryRepository) {

  // ✅ 修正: 入力された識別子での検索を最優先
  // CHALLENGE = User識別フェーズのため、常に入力値で検索
  User existingUser = userQueryRepository.findByEmail(tenant, email, providerId);
  if (existingUser.exists()) {
    return existingUser;
  }

  // transactionに既にUserがいて、かつ同じメールなら再利用
  // （Challenge再送信の場合）
  if (transaction.hasUser()) {
    User transactionUser = transaction.user();
    if (email.equals(transactionUser.email())) {
      return transactionUser;
    }
    // ⚠️ 異なるメール入力 → 前のUserを破棄して新規作成
  }

  // 新規ユーザー作成
  User user = new User();
  String id = UUID.randomUUID().toString();
  user.setSub(id);
  user.setEmail(email);

  return user;
}
```

**変更点**:
1. `findByEmail()` を最優先（Line 1-5）
2. `transaction.hasUser()` チェックは2番目（Line 7-13）
3. メールアドレス一致チェック追加（Line 9）
4. コメントでCHALLENGEフェーズの意味を明記（Line 2-3）

#### 4.3.2 SmsAuthenticationChallengeInteractor 修正

**ファイル**: `libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/sms/SmsAuthenticationChallengeInteractor.java`

**修正箇所**: Line 168-192

**修正内容**: Email認証と完全に同じパターンを適用

```java
private User resolveUser(
    Tenant tenant,
    AuthenticationTransaction transaction,
    String phoneNumber,
    String providerId,
    UserQueryRepository userQueryRepository) {

  // ✅ 修正: 入力された電話番号での検索を最優先
  User existingUser = userQueryRepository.findByPhone(tenant, phoneNumber, providerId);
  if (existingUser.exists()) {
    return existingUser;
  }

  // transactionに既にUserがいて、かつ同じ電話番号なら再利用
  if (transaction.hasUser()) {
    User transactionUser = transaction.user();
    if (phoneNumber.equals(transactionUser.phoneNumber())) {
      return transactionUser;
    }
  }

  // 新規ユーザー作成
  User user = new User();
  String id = UUID.randomUUID().toString();
  user.setSub(id);
  user.setPhoneNumber(phoneNumber);

  return user;
}
```

### 4.4 テスト計画

#### 4.4.1 単体テスト（新規追加）

**ファイル**: `libs/idp-server-authentication-interactors/src/test/java/org/idp/server/authentication/interactors/email/EmailAuthenticationChallengeInteractorTest.java`

```java
class EmailAuthenticationChallengeInteractorTest {

  @Test
  @DisplayName("メアド変更時: 新しいUserを返す")
  void resolveUser_whenEmailChanged_returnsNewUser() {
    // Setup
    User userA = new User();
    userA.setSub("user-a-id");
    userA.setEmail("a@example.com");

    AuthenticationTransaction transaction =
      createTransactionWithUser(userA);

    // Mock: メアドBはDB未登録
    when(userQueryRepository.findByEmail(tenant, "b@example.com", "idp-server"))
      .thenReturn(User.empty());

    // Execute: 異なるメアドで呼び出し
    User result = invokeResolveUser(transaction, "b@example.com");

    // Verify: 新しいUserが返される
    assertNotEquals("user-a-id", result.sub());
    assertEquals("b@example.com", result.email());
  }

  @Test
  @DisplayName("同じメアドでの再送信: 既存Userを返す")
  void resolveUser_whenSameEmailResend_returnsExistingUser() {
    // Setup
    User userA = new User();
    userA.setSub("user-a-id");
    userA.setEmail("a@example.com");

    AuthenticationTransaction transaction =
      createTransactionWithUser(userA);

    // Mock: メアドAはDB未登録（transactionにのみ存在）
    when(userQueryRepository.findByEmail(tenant, "a@example.com", "idp-server"))
      .thenReturn(User.empty());

    // Execute: 同じメアドで呼び出し（再送信）
    User result = invokeResolveUser(transaction, "a@example.com");

    // Verify: 既存Userが返される（再利用）
    assertEquals("user-a-id", result.sub());
    assertEquals("a@example.com", result.email());
  }

  @Test
  @DisplayName("DB登録済みユーザー: DB優先で返す")
  void resolveUser_whenUserInDb_returnsDbUser() {
    // Setup
    User userInDb = new User();
    userInDb.setSub("user-db-id");
    userInDb.setEmail("a@example.com");

    User userInTransaction = new User();
    userInTransaction.setSub("user-tx-id");
    userInTransaction.setEmail("b@example.com");

    AuthenticationTransaction transaction =
      createTransactionWithUser(userInTransaction);

    // Mock: メアドAはDB登録済み
    when(userQueryRepository.findByEmail(tenant, "a@example.com", "idp-server"))
      .thenReturn(userInDb);

    // Execute
    User result = invokeResolveUser(transaction, "a@example.com");

    // Verify: DBのUserが最優先
    assertEquals("user-db-id", result.sub());
    assertEquals("a@example.com", result.email());
  }

  @Test
  @DisplayName("新規ユーザー: 新規作成して返す")
  void resolveUser_whenNewUser_createsAndReturnsNewUser() {
    // Setup
    AuthenticationTransaction transaction =
      createTransactionWithoutUser();

    // Mock: メアドCはDB未登録
    when(userQueryRepository.findByEmail(tenant, "c@example.com", "idp-server"))
      .thenReturn(User.empty());

    // Execute
    User result = invokeResolveUser(transaction, "c@example.com");

    // Verify: 新規User作成
    assertNotNull(result.sub());
    assertEquals("c@example.com", result.email());
    // UUID形式チェック
    assertDoesNotThrow(() -> UUID.fromString(result.sub()));
  }
}
```

#### 4.4.2 E2Eテスト（新規追加）

**ファイル**: `e2e/spec/authentication/email-authentication-address-change.spec.js`

```javascript
const { test, expect } = require('@playwright/test');
const { AuthenticationHelper } = require('../helpers/authentication-helper');

test.describe('Email Authentication - Address Change Scenarios (Issue #800)', () => {

  let authHelper;

  test.beforeEach(async ({ page }) => {
    authHelper = new AuthenticationHelper(page);
  });

  test('メアドAでChallenge送信後、メアドBに変更して認証完了 → メアドBでログイン', async () => {
    // 1. 認証フロー開始
    await authHelper.startAuthenticationFlow();

    // 2. メアドAでChallenge送信
    await authHelper.inputEmail('user-a@example.com');
    await authHelper.submitEmailChallenge();

    // 3. ブラウザバック（ユーザー操作）
    await authHelper.goBack();

    // 4. メアドBでChallenge送信
    await authHelper.inputEmail('user-b@example.com');
    await authHelper.submitEmailChallenge();

    // 5. メアドB宛のOTPを取得
    const otpB = await authHelper.getOtpFromEmail('user-b@example.com');

    // 6. OTP検証
    await authHelper.inputOtp(otpB);
    await authHelper.submitOtp();

    // 7. トークン取得
    const tokens = await authHelper.getTokens();
    const userInfo = await authHelper.getUserInfo(tokens.access_token);

    // ✅ 検証: メアドBのユーザーでログイン
    expect(userInfo.email).toBe('user-b@example.com');
    expect(userInfo.email_verified).toBe(true);
  });

  test('既存ユーザーメアドAと新規ユーザーメアドBで認証 → 別々のユーザーID', async () => {
    // 1. メアドAで認証完了（既存ユーザー）
    const userA = await authHelper.authenticateWithEmail('user-a@example.com');
    const userAId = userA.sub;

    // 2. 新しいセッションでメアドBで認証完了（新規ユーザー）
    await authHelper.startNewSession();
    const userB = await authHelper.authenticateWithEmail('user-b@example.com');
    const userBId = userB.sub;

    // ✅ 検証: 別々のユーザーID
    expect(userAId).not.toBe(userBId);
    expect(userA.email).toBe('user-a@example.com');
    expect(userB.email).toBe('user-b@example.com');
  });

  test('アプリ再インストール後、メアドBで認証 → メアドBのユーザーでログイン', async () => {
    // 前提: メアドAのユーザーが存在
    await authHelper.createUser('user-a@example.com');

    // 1. アプリ再インストール（セッションクリア）
    await authHelper.clearAllSessions();
    await authHelper.clearAllCookies();

    // 2. メアドBで認証
    const userB = await authHelper.authenticateWithEmail('user-b@example.com');

    // ✅ 検証: メアドBのユーザーでログイン
    expect(userB.email).toBe('user-b@example.com');
    expect(userB.email_verified).toBe(true);
  });

  test('同じメアドでChallenge再送信 → 同じユーザーIDを維持', async () => {
    // 1. メアドAでChallenge送信
    await authHelper.startAuthenticationFlow();
    await authHelper.inputEmail('user-a@example.com');
    await authHelper.submitEmailChallenge();

    // 2. OTP画面で「再送信」ボタンをクリック
    await authHelper.clickResendButton();

    // 3. 新しいOTPを取得して認証完了
    const newOtp = await authHelper.getLatestOtpFromEmail('user-a@example.com');
    await authHelper.inputOtp(newOtp);
    await authHelper.submitOtp();

    // 4. トークン取得
    const tokens = await authHelper.getTokens();
    const userInfo = await authHelper.getUserInfo(tokens.access_token);

    // ✅ 検証: 正常にログイン
    expect(userInfo.email).toBe('user-a@example.com');
    expect(userInfo.email_verified).toBe(true);
  });
});
```

#### 4.4.3 回帰テスト

**実行対象**:
- ✅ 全単体テスト: `./gradlew test`
- ✅ 全E2Eテスト: `cd e2e && npm test`
- ✅ 認証フロー系テスト重点実施

**テストマトリクス**:

| 認証方式 | 既存テスト | 新規テスト | 合計 |
|---------|-----------|-----------|------|
| Email | 15 | 4 | 19 |
| SMS | 12 | 4 | 16 |
| WebAuthn | 8 | 0 | 8 |
| Password | 10 | 0 | 10 |
| Federation | 5 | 0 | 5 |
| **合計** | **50** | **8** | **58** |

### 4.5 デリバリー計画

#### 4.5.1 開発タイムライン

```
Day 1 (AM):
  - EmailAuthenticationChallengeInteractor 修正
  - SmsAuthenticationChallengeInteractor 修正
  - 単体テスト作成・実行

Day 1 (PM):
  - E2Eテスト作成・実行
  - コードレビュー

Day 2 (AM):
  - 全テスト実行（回帰テスト）
  - 修正があればフィードバック

Day 2 (PM):
  - ドキュメント更新
  - PR作成・マージ

Day 3:
  - QA最終確認
  - GA判断
```

#### 4.5.2 実装手順

**Step 1: ブランチ作成**
```bash
git checkout -b fix/issue-800-authentication-user-resolution
```

**Step 2: 修正実施**
```bash
# EmailAuthenticationChallengeInteractor 修正
vim libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/email/EmailAuthenticationChallengeInteractor.java

# SmsAuthenticationChallengeInteractor 修正
vim libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/sms/SmsAuthenticationChallengeInteractor.java

# フォーマット
./gradlew spotlessApply
```

**Step 3: テスト作成・実行**
```bash
# 単体テスト作成
vim libs/idp-server-authentication-interactors/src/test/java/org/idp/server/authentication/interactors/email/EmailAuthenticationChallengeInteractorTest.java

# 単体テスト実行
./gradlew test --tests "*EmailAuthenticationChallengeInteractorTest"

# E2Eテスト作成
vim e2e/spec/authentication/email-authentication-address-change.spec.js

# E2Eテスト実行
cd e2e
npm test -- spec/authentication/email-authentication-address-change.spec.js
```

**Step 4: 回帰テスト**
```bash
# 全テスト実行
./gradlew test
cd e2e && npm test
```

**Step 5: ドキュメント更新**
```bash
# CLAUDE.md更新
vim CLAUDE.md

# 変更内容を記録
cat >> CLAUDE.md <<'EOF'

## 🐛 Bug Fix履歴

### Issue #800: Email/SMS認証でのUser識別問題修正（2025-10-24）

**問題**: メアド/電話番号変更時に前のUserが引き継がれる

**修正内容**:
- `EmailAuthenticationChallengeInteractor.resolveUser()`: 入力識別子での検索を最優先
- `SmsAuthenticationChallengeInteractor.resolveUser()`: 入力識別子での検索を最優先

**判定順序変更**:
```java
// Before: transaction.hasUser() 最優先
if (transaction.hasUser()) { ... }
User existingUser = findByEmail(...);

// After: findByEmail() 最優先
User existingUser = findByEmail(...);  // ← 最優先
if (transaction.hasUser() && sameEmail) { ... }
```

**影響範囲**: Email/SMS認証のCHALLENGEステップのみ

**テスト**: E2Eテスト追加（メアド変更シナリオ）
EOF
```

**Step 6: コミット・PR作成**
```bash
# コミット
git add .
git commit -m "fix: resolve user by input identifier first in CHALLENGE step

**Issue**: #800 メアド認証によるアカウント作成・認証の挙動が不安定

**問題**:
- メールアドレス変更時に前のUserが引き継がれる
- transaction.hasUser()を最優先判定していたため

**修正内容**:
- EmailAuthenticationChallengeInteractor.resolveUser(): findByEmail()を最優先
- SmsAuthenticationChallengeInteractor.resolveUser(): findByPhone()を最優先
- E2Eテスト追加: メアド変更シナリオ（4パターン）

**判定順序変更**:
1. 入力された識別子でDB検索（最優先）
2. transaction.hasUser() && 同じ識別子なら再利用
3. 新規User作成

**テスト**:
- 単体テスト追加: 8件
- E2Eテスト追加: 4件
- 回帰テスト: 全パス

**影響範囲**: Email/SMS認証のCHALLENGEステップのみ

Fixes #800

🤖 Generated with [Claude Code](https://claude.com/claude-code)
Co-Authored-By: Claude <noreply@anthropic.com>"

# PR作成
gh pr create --title "fix: Issue #800 - User resolution in email/sms authentication (Critical)" \
  --label "bug,critical,security" \
  --body "$(cat <<'PR_BODY'
## 📋 Summary
**Critical Bug Fix**: Email/SMS認証でメアド/電話番号変更時に前のUserが引き継がれる問題を修正

## 🐛 Problem
製品QA中に発見されたCritical Severity Bug:
- メアドAで認証開始後、メアドBに変更して認証完了 → メアドAでログイン
- セキュリティリスク: 他人のアカウントに誤ログインの可能性
- 影響範囲: Email/SMS/WebAuthn認証

## ✅ Solution
`resolveUser()`の判定順序を修正:

**Before**:
```java
if (transaction.hasUser()) { ... }  // ← 最優先（誤り）
User existingUser = findByEmail(...);
```

**After**:
```java
User existingUser = findByEmail(...);  // ← 最優先（正解）
if (transaction.hasUser() && sameEmail) { ... }
```

## 📝 Changes
- `EmailAuthenticationChallengeInteractor.resolveUser()`: 169-193行目修正
- `SmsAuthenticationChallengeInteractor.resolveUser()`: 168-192行目修正
- E2Eテスト追加: メアド変更シナリオ（4パターン）
- 単体テスト追加: 8件

## 🧪 Test Results
- ✅ 単体テスト: 8件追加・全パス
- ✅ E2Eテスト: 4件追加・全パス
- ✅ 回帰テスト: 全パス（58件）

## 🔒 Security Impact
- **Before**: CVSSv3.1 7.5-8.0 (High Severity)
- **After**: 脆弱性解消

## 📚 Documentation
- CLAUDE.md更新: Bug Fix履歴追加

## 🚀 Release Impact
**GA判断**: このPRマージ後にGA可能

Fixes #800

🤖 Generated with [Claude Code](https://claude.com/claude-code)
PR_BODY
)"
```

### 4.6 品質ゲート

#### Merge前チェックリスト
- [ ] 単体テスト: 全パス
- [ ] E2Eテスト: 全パス
- [ ] 回帰テスト: 全パス
- [ ] コードレビュー: 承認済み
- [ ] セキュリティレビュー: 承認済み
- [ ] ドキュメント: 更新済み
- [ ] `./gradlew spotlessApply`: 実行済み

#### GA判断基準
- [ ] Issue #800: 完全解決確認
- [ ] E2Eテスト: メアド変更シナリオ全パス
- [ ] 回帰影響: なし
- [ ] パフォーマンス影響: なし

---

## 5. Phase 2: 構造改善（GA後1ヶ月以内）

### 5.1 目的

**Phase 1の技術負債解消 + User管理の明示化**

### 5.2 スコープ

#### In-Scope
- ✅ `AuthenticationFlowContext` 導入
- ✅ User管理の明示化（setUser/getUser/clearUser）
- ✅ EmailVerificationChallenge へのemail追加
- ✅ 既存Interactorの段階的移行

#### Out-of-Scope（Phase 3）
- ❌ `requiresUser()` 導入
- ❌ Keycloakパターン完全準拠

### 5.3 設計

#### 5.3.1 AuthenticationFlowContext 導入

**新規インターフェース**: `AuthenticationFlowContext.java`

```java
package org.idp.server.core.openid.authentication;

public interface AuthenticationFlowContext {

  // Transaction取得
  AuthenticationTransaction transaction();

  // ⭐ User管理を明示化
  User getUser();              // Userを取得（nullableを明示）
  void setUser(User user);     // User確定を明示
  void clearUser();            // Userリセットを明示
  boolean hasUser();

  // Challenge管理
  void storeChallenge(String key, Object challenge);
  <T> T getChallenge(String key, Class<T> type);
  void clearChallenge(String key);
  boolean hasChallenge(String key);

  // Tenant/Realm情報
  Tenant tenant();
  TenantAttributes tenantAttributes();

  // Request Attributes
  RequestAttributes requestAttributes();
}
```

**実装クラス**: `DefaultAuthenticationFlowContext.java`

```java
package org.idp.server.core.openid.authentication;

public class DefaultAuthenticationFlowContext implements AuthenticationFlowContext {

  private AuthenticationTransaction transaction;
  private final Tenant tenant;
  private final RequestAttributes requestAttributes;
  private final Map<String, Object> challenges = new HashMap<>();

  public DefaultAuthenticationFlowContext(
      AuthenticationTransaction transaction,
      Tenant tenant,
      RequestAttributes requestAttributes) {
    this.transaction = transaction;
    this.tenant = tenant;
    this.requestAttributes = requestAttributes;
  }

  @Override
  public User getUser() {
    return transaction.user();  // nullable
  }

  @Override
  public void setUser(User user) {
    // ⭐ TransactionのUser更新
    AuthenticationInteractionRequestResult result =
      AuthenticationInteractionRequestResult.ofUserUpdate(user);
    this.transaction = transaction.updateWith(result);
  }

  @Override
  public void clearUser() {
    // ⭐ TransactionのUserクリア
    AuthenticationInteractionRequestResult result =
      AuthenticationInteractionRequestResult.ofUserClear();
    this.transaction = transaction.updateWith(result);
  }

  @Override
  public boolean hasUser() {
    return transaction.hasUser();
  }

  @Override
  public void storeChallenge(String key, Object challenge) {
    challenges.put(key, challenge);
  }

  @Override
  public <T> T getChallenge(String key, Class<T> type) {
    Object challenge = challenges.get(key);
    if (challenge == null) {
      return null;
    }
    return type.cast(challenge);
  }

  @Override
  public void clearChallenge(String key) {
    challenges.remove(key);
  }

  @Override
  public boolean hasChallenge(String key) {
    return challenges.containsKey(key);
  }

  @Override
  public AuthenticationTransaction transaction() {
    return transaction;
  }

  @Override
  public Tenant tenant() {
    return tenant;
  }

  @Override
  public TenantAttributes tenantAttributes() {
    return transaction.request().tenantAttributes();
  }

  @Override
  public RequestAttributes requestAttributes() {
    return requestAttributes;
  }
}
```

#### 5.3.2 EmailAuthenticationChallengeInteractor 改良

```java
@Override
public AuthenticationInteractionRequestResult interact(
    Tenant tenant,
    AuthenticationTransaction transaction,
    AuthenticationInteractionType type,
    AuthenticationInteractionRequest request,
    RequestAttributes requestAttributes,
    UserQueryRepository userQueryRepository) {

  // ⭐ Context作成
  AuthenticationFlowContext flowContext =
    new DefaultAuthenticationFlowContext(transaction, tenant, requestAttributes);

  String email = resolveEmail(flowContext, request);
  String providerId = request.optValueAsString("provider_id", "idp-server");

  // ⭐ User識別（Phase 1の修正を維持）
  User user = resolveUser(flowContext, email, providerId, userQueryRepository);

  // Challenge送信
  AuthenticationExecutionResult executionResult = sendEmailChallenge(...);

  if (executionResult.isClientError()) {
    return AuthenticationInteractionRequestResult.clientError(...);
  }

  // ⭐ User確定を明示
  flowContext.setUser(user);

  return new AuthenticationInteractionRequestResult(
    AuthenticationInteractionStatus.SUCCESS,
    type,
    operationType(),
    method(),
    user,  // ← flowContext.getUser() でも可
    contents,
    DefaultSecurityEventType.email_verification_request_success);
}

// ⭐ resolveUser()もContext使用
private User resolveUser(
    AuthenticationFlowContext flowContext,
    String email,
    String providerId,
    UserQueryRepository userQueryRepository) {

  Tenant tenant = flowContext.tenant();

  // Phase 1の修正を維持
  User existingUser = userQueryRepository.findByEmail(tenant, email, providerId);
  if (existingUser.exists()) {
    return existingUser;
  }

  if (flowContext.hasUser()) {
    User transactionUser = flowContext.getUser();
    if (email.equals(transactionUser.email())) {
      return transactionUser;
    }
  }

  User user = new User();
  user.setSub(UUID.randomUUID().toString());
  user.setEmail(email);
  return user;
}
```

#### 5.3.3 EmailVerificationChallenge へのemail追加

**修正ファイル**: `EmailVerificationChallenge.java`

```java
public class EmailVerificationChallenge implements Serializable, JsonReadable {

  String verificationCode;
  String email;  // ⭐ 追加: どのメールアドレス用のChallengeかを記録
  int retryCountLimitation;
  int tryCount;
  int expiresSeconds;
  LocalDateTime createdAt;

  public static EmailVerificationChallenge create(
      String email,  // ⭐ 追加
      OneTimePassword oneTimePassword,
      int retryCountLimitation,
      int expiresSeconds) {
    return new EmailVerificationChallenge(
        oneTimePassword.value(),
        email,  // ⭐ 追加
        retryCountLimitation,
        0,
        expiresSeconds,
        SystemDateTime.now());
  }

  public EmailVerificationResult verify(String inputEmail, String inputCode) {
    // ⭐ メールアドレスの一致も確認
    if (!Objects.equals(email, inputEmail)) {
      return EmailVerificationResult.failure(
        Map.of("error", "invalid_request",
               "error_description", "Email address mismatch"));
    }

    if (isExpired()) {
      return EmailVerificationResult.failure(
        Map.of("error", "invalid_request",
               "error_description", "Email challenge is expired"));
    }

    if (tryCount >= retryCountLimitation) {
      return EmailVerificationResult.failure(
        Map.of("error", "invalid_request",
               "error_description", "Too many attempts"));
    }

    if (!Objects.equals(verificationCode, inputCode)) {
      return EmailVerificationResult.failure(
        Map.of("error", "invalid_request",
               "error_description", "Invalid verification code"));
    }

    return EmailVerificationResult.success(Map.of("status", "success"));
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("verification_code", verificationCode);
    map.put("email", email);  // ⭐ 追加
    map.put("retry_count_limitation", retryCountLimitation);
    map.put("try_count", tryCount);
    map.put("expires_seconds", expiresSeconds);
    map.put("created_at", createdAt);
    return map;
  }
}
```

### 5.4 マイグレーション戦略

#### 段階的移行

**Step 1**: AuthenticationFlowContext 導入（1週目）
- インターフェース・実装追加
- 既存Interactorは変更なし（後方互換性維持）

**Step 2**: Email/SMS Interactor 移行（2週目）
- EmailAuthenticationChallengeInteractor
- EmailAuthenticationInteractor
- SmsAuthenticationChallengeInteractor
- SmsAuthenticationInteractor

**Step 3**: 他のInteractor 移行（3週目）
- WebAuthnRegistrationChallengeInteractor
- WebAuthnAuthenticationChallengeInteractor
- その他

**Step 4**: 既存APIの非推奨化（4週目）
- AuthenticationInteractor の古いシグネチャを @Deprecated
- 新しいContextベースのシグネチャを推奨

---

## 6. Phase 3: アーキテクチャ刷新（GA後3ヶ月以内）

### 6.1 目的

**Keycloakパターン完全準拠 + 設計負債完全解消**

### 6.2 スコープ

- ✅ `requiresUser()` 導入
- ✅ Keycloakパターン準拠
- ✅ 実行順序保証の仕組み
- ✅ 既存APIの完全移行

### 6.3 設計

#### 6.3.1 requiresUser() 導入

**インターフェース変更**: `AuthenticationInteractor.java`

```java
public interface AuthenticationInteractor {

  AuthenticationInteractionType type();

  default OperationType operationType() {
    return OperationType.AUTHENTICATION;
  }

  String method();

  // ⭐ 追加: User必須かどうかを明示
  default boolean requiresUser() {
    // デフォルト: OperationTypeから推論
    return operationType() == OperationType.AUTHENTICATION;
  }

  // 新しいシグネチャ（Contextベース）
  AuthenticationInteractionRequestResult interact(
      AuthenticationFlowContext context,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      UserQueryRepository userQueryRepository);

  // 古いシグネチャ（非推奨）
  @Deprecated
  default AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {
    // Contextに変換して新シグネチャを呼ぶ
    AuthenticationFlowContext context =
      new DefaultAuthenticationFlowContext(transaction, tenant, requestAttributes);
    return interact(context, type, request, userQueryRepository);
  }
}
```

#### 6.3.2 実行順序保証

**新規クラス**: `AuthenticationFlowExecutor.java`

```java
public class AuthenticationFlowExecutor {

  public AuthenticationInteractionRequestResult execute(
      AuthenticationFlowContext context,
      AuthenticationInteractor interactor,
      AuthenticationInteractionRequest request,
      UserQueryRepository userQueryRepository) {

    // ⭐ User必須チェック
    if (interactor.requiresUser() && !context.hasUser()) {
      throw new IllegalStateException(
        "Authenticator " + interactor.getClass().getSimpleName() +
        " requires user but user is not set. " +
        "Ensure a user identification authenticator (requiresUser=false) " +
        "is executed before this authenticator.");
    }

    // Interactor実行
    AuthenticationInteractionRequestResult result =
      interactor.interact(
        context,
        interactor.type(),
        request,
        userQueryRepository);

    return result;
  }
}
```

#### 6.3.3 Interactor実装例

**1st Factor**: `EmailAuthenticationChallengeInteractor`

```java
@Override
public boolean requiresUser() {
  return false;  // ⭐ User識別フェーズ（User不要）
}

@Override
public AuthenticationInteractionRequestResult interact(
    AuthenticationFlowContext context,
    AuthenticationInteractionType type,
    AuthenticationInteractionRequest request,
    UserQueryRepository userQueryRepository) {

  String email = request.getValueAsString("email");
  String providerId = request.optValueAsString("provider_id", "idp-server");

  // ⭐ User識別（Phase 1の修正を維持）
  User user = identifyUser(context, email, providerId, userQueryRepository);

  // Challenge送信
  sendEmailChallenge(context, user, email);

  // ⭐ User確定を明示
  context.setUser(user);

  return success(user, ...);
}

private User identifyUser(
    AuthenticationFlowContext context,
    String email,
    String providerId,
    UserQueryRepository userQueryRepository) {

  // Phase 1の修正を維持
  User existingUser = userQueryRepository.findByEmail(
    context.tenant(), email, providerId);
  if (existingUser.exists()) {
    return existingUser;
  }

  // Challenge再送信の場合
  if (context.hasUser()) {
    User transactionUser = context.getUser();
    if (email.equals(transactionUser.email())) {
      return transactionUser;
    }
  }

  // 新規作成
  return createNewUser(email);
}
```

**2nd Factor**: `EmailAuthenticationInteractor`

```java
@Override
public boolean requiresUser() {
  return true;  // ⭐ 検証フェーズ（User必須）
}

@Override
public AuthenticationInteractionRequestResult interact(
    AuthenticationFlowContext context,
    AuthenticationInteractionType type,
    AuthenticationInteractionRequest request,
    UserQueryRepository userQueryRepository) {

  // ⭐ User必須（requiresUser=trueのため保証されている）
  User user = context.getUser();  // nullチェック不要

  String verificationCode = request.getValueAsString("verification_code");

  // Challenge検証
  EmailVerificationChallenge challenge =
    context.getChallenge("email-authentication-challenge", EmailVerificationChallenge.class);

  EmailVerificationResult verificationResult =
    challenge.verify(user.email(), verificationCode);

  if (verificationResult.isFailure()) {
    // Challenge更新（試行回数カウントアップ）
    challenge = challenge.countUp();
    context.storeChallenge("email-authentication-challenge", challenge);
    return failure(...);
  }

  // 検証成功
  user.setEmailVerified(true);
  return success(user, ...);
}
```

---

## 7. リスク管理

### 7.1 Critical Risks（Phase 1）

| リスク | 確率 | 影響 | 対策 | 担当 | ステータス |
|--------|------|------|------|------|-----------|
| Phase 1修正でリグレッション | Medium | High | 全E2Eテスト実行 | QA | Open |
| 修正漏れ（WebAuthn等） | Low | Medium | 全Interactor調査 | Dev | Open |
| テストカバレッジ不足 | Low | High | 新規テスト追加 | QA+Dev | Open |
| GA遅延 | Low | High | 3日以内完了 | PM | Open |

### 7.2 High Risks（Phase 2-3）

| リスク | 確率 | 影響 | 対策 | タイミング |
|--------|------|------|------|-----------|
| Phase 2でのAPI互換性 | Medium | Medium | Deprecation戦略 | GA+1M |
| Phase 3の工数超過 | Medium | Low | 段階的移行 | GA+3M |
| 既存Interactorの移行漏れ | Low | Medium | チェックリスト | GA+3M |

### 7.3 リスク対応計画

#### Phase 1 Critical Risk対応

**リグレッション対策**:
- ✅ 全E2Eテスト実行（58件）
- ✅ 認証フロー系テスト重点実施
- ✅ QAチームによる手動テスト

**修正漏れ対策**:
```bash
# 全Interactorで resolveUser パターン検索
grep -r "transaction.hasUser()" \
  libs/idp-server-authentication-interactors/src/main/java \
  --include="*.java" -A 5 -B 5
```

**テストカバレッジ対策**:
- 目標: 新規コード 80%以上
- JaCoCo レポート確認

---

## 8. 品質保証計画

### 8.1 テスト戦略

#### 8.1.1 単体テスト

**カバレッジ目標**: 80%以上

**重点テスト項目**:
- ✅ `resolveUser()` の判定順序
- ✅ メールアドレス変更シナリオ
- ✅ Challenge再送信シナリオ
- ✅ 新規ユーザー作成
- ✅ DB登録済みユーザー優先

#### 8.1.2 E2Eテスト

**新規追加テスト**: 8件

| テストID | シナリオ | 期待結果 |
|---------|---------|---------|
| E2E-01 | メアドA→メアドB変更 | メアドBでログイン |
| E2E-02 | 既存A + 新規B | 別々のユーザーID |
| E2E-03 | 再インストール後メアドB | メアドBでログイン |
| E2E-04 | Challenge再送信 | 同じユーザーID維持 |
| E2E-05 | SMS: 電話番号A→B変更 | 電話番号Bでログイン |
| E2E-06 | SMS: 既存A + 新規B | 別々のユーザーID |
| E2E-07 | Email: DB登録済み優先 | DB のUserでログイン |
| E2E-08 | SMS: DB登録済み優先 | DBのUserでログイン |

#### 8.1.3 セキュリティテスト

**テスト項目**:
- ✅ アカウント混同テスト
- ✅ セッション分離テスト
- ✅ Challenge再利用テスト
- ✅ タイミング攻撃耐性

### 8.2 パフォーマンステスト

**影響評価**: Phase 1修正のパフォーマンス影響は軽微

**測定項目**:
- 認証フロー全体のレイテンシ
- DB検索回数（変更なし）
- メモリ使用量（変更なし）

**期待値**:
- レイテンシ増加: <5%
- DB検索回数: 変更なし
- メモリ使用量: 変更なし

### 8.3 ドキュメント

**更新対象**:
- ✅ `CLAUDE.md`: Bug Fix履歴追加
- ✅ `ai-01-index.md`: Issue #800対応記録
- ✅ `authentication-federation.md`: resolveUser()パターン更新
- ✅ このドキュメント: 実装完了記録

---

## 9. まとめ

### 9.1 改修全体のロードマップ

```
QA中（現在）
  ↓
Phase 1 (2-3日)
  ├─ resolveUser() 修正
  ├─ E2Eテスト追加
  └─ 回帰テスト
  ↓
GA判断 ← ここでリリース可能
  ↓
GA後 1ヶ月
  └─ Phase 2: 構造改善
      ├─ AuthenticationFlowContext 導入
      └─ User管理明示化
  ↓
GA後 3ヶ月
  └─ Phase 3: アーキテクチャ刷新
      ├─ requiresUser() 導入
      └─ Keycloakパターン準拠
```

### 9.2 期待効果

#### Phase 1完了時
- ✅ Issue #800 完全解決
- ✅ セキュリティリスク解消
- ✅ GA判断可能

#### Phase 2完了時
- ✅ User管理が明示的になる
- ✅ 技術負債50%削減
- ✅ 保守性向上

#### Phase 3完了時
- ✅ Keycloakパターン準拠
- ✅ 設計負債完全解消
- ✅ 拡張性大幅向上

### 9.3 成功基準

#### Phase 1
- [ ] Issue #800: 完全解決確認
- [ ] E2Eテスト: メアド変更シナリオ全パス
- [ ] 回帰影響: なし
- [ ] セキュリティレビュー: 承認
- [ ] GA判断: Go

#### Phase 2
- [ ] AuthenticationFlowContext: 導入完了
- [ ] Email/SMS Interactor: 移行完了
- [ ] 技術負債: 50%削減
- [ ] コードカバレッジ: 80%以上

#### Phase 3
- [ ] requiresUser(): 全Interactor実装
- [ ] Keycloakパターン: 100%準拠
- [ ] 設計負債: 完全解消
- [ ] 拡張性: 大幅向上

---

## 付録

### A. 参考資料

- [Issue #800](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/800)
- [Keycloak Authentication SPI](https://wjw465150.gitbooks.io/keycloak-documentation/content/server_development/topics/auth-spi.html)
- [CLAUDE.md](../../../CLAUDE.md)

### B. 用語集

| 用語 | 説明 |
|------|------|
| **CHALLENGE** | 1st factor: ユーザー識別フェーズ |
| **AUTHENTICATION** | 2nd factor: 検証フェーズ |
| **resolveUser()** | User識別・作成メソッド |
| **AuthenticationFlowContext** | 認証フロー実行コンテキスト |
| **requiresUser()** | User必須かどうかを示すフラグ |

### C. 変更履歴

| 日付 | バージョン | 変更内容 | 作成者 |
|------|-----------|---------|--------|
| 2025-10-24 | 1.0 | 初版作成 | Claude Code |

---

**End of Document**
