# Implementation Plan: Issue #800 - Authentication Step Definitions with 1st/2nd Factor Support

**Branch**: `feature/authentication-step-definitions-1st-2nd-factor`
**Issue**: #800 メアド認証によるアカウント作成・認証の挙動が不安定
**Created**: 2025-10-25
**Estimated Time**: 5-7 hours (2 days)

---

## 📋 Executive Summary

### Problem
- メールアドレス変更時に前のユーザーでログインされる
- 登録と認証の区別が不明確
- 1st factor/2nd factorの制御ができない

### Solution
1. **`AuthenticationStepDefinition` 拡張**: `requiresUser`, `allowRegistration`, `userIdentitySource` 追加
2. **`resolveUser()` 修正**: DB検索最優先 + Policy連携
3. **Keycloakパターン準拠**: 1st/2nd factor の明確な区別

### Expected Outcome
- ✅ Issue #800 完全解決
- ✅ 登録/認証をPolicyで制御可能
- ✅ 1st/2nd factorの明確な区別
- ✅ Keycloakパターン準拠

---

## 🎯 Implementation Phases

### Phase 1: AuthenticationStepDefinition Extension (30min)

#### Objective
1st/2nd factor制御の基盤を作る

#### Files to Modify
```
libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/policy/
└── AuthenticationStepDefinition.java
```

#### Implementation Details

**Add new fields**:
```java
public class AuthenticationStepDefinition implements JsonReadable {
  String method;
  int order;

  // ⭐ NEW: 1st/2nd factor control
  boolean requiresUser = true;           // default: 2nd factor
  boolean allowRegistration = false;     // default: no registration
  String userIdentitySource;             // "email", "phone_number", "username"
  String verificationSource;             // verification identifier
  String registrationMode = "allowed";   // "allowed", "required", "disabled"
```

**Add getter/has methods**:
```java
  public boolean requiresUser() {
    return requiresUser;
  }

  public boolean allowRegistration() {
    return allowRegistration;
  }

  public String userIdentitySource() {
    return userIdentitySource;
  }

  public boolean hasUserIdentitySource() {
    return userIdentitySource != null && !userIdentitySource.isEmpty();
  }

  public String verificationSource() {
    return verificationSource;
  }

  public boolean hasVerificationSource() {
    return verificationSource != null && !verificationSource.isEmpty();
  }

  public String registrationMode() {
    return registrationMode != null ? registrationMode : "allowed";
  }
```

**Update toMap()**:
```java
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("method", method);
    map.put("order", order);
    map.put("requires_user", requiresUser);
    map.put("allow_registration", allowRegistration);
    map.put("registration_mode", registrationMode());
    if (hasUserIdentitySource()) {
      map.put("user_identity_source", userIdentitySource);
    }
    if (hasVerificationSource()) {
      map.put("verification_source", verificationSource);
    }
    return map;
  }
```

#### Verification
```bash
./gradlew :libs:idp-server-core:compileJava
```

---

### Phase 2: EmailAuthenticationChallengeInteractor Fix (1-2 hours)

#### Objective
Issue #800修正 + 1st factor対応

#### Files to Modify
```
libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/email/
└── EmailAuthenticationChallengeInteractor.java
```

#### Implementation Details

**Step 2.1: Add getCurrentStepDefinition() helper**

Location: After `resolveEmail()` method

```java
/**
 * 現在のステップ定義を取得
 *
 * @param transaction 認証トランザクション
 * @param method 認証メソッド名
 * @return ステップ定義（見つからない場合はnull）
 */
private AuthenticationStepDefinition getCurrentStepDefinition(
    AuthenticationTransaction transaction,
    String method) {

  AuthenticationRequest request = transaction.request();
  if (!request.hasAuthenticationPolicy()) {
    return null;
  }

  AuthenticationPolicy policy = request.authenticationPolicy();
  if (!policy.hasStepDefinitions()) {
    return null;
  }

  return policy.stepDefinitions().stream()
      .filter(step -> method.equals(step.authenticationMethod()))
      .findFirst()
      .orElse(null);
}
```

**Step 2.2: Add extractIdentity() helper**

Location: After `getCurrentStepDefinition()` method

```java
/**
 * userIdentitySource から識別子を抽出
 *
 * @param user ユーザー
 * @param stepDefinition ステップ定義
 * @return 識別子（email, phone_number, username）
 */
private String extractIdentity(User user, AuthenticationStepDefinition stepDefinition) {
  if (stepDefinition == null || !stepDefinition.hasUserIdentitySource()) {
    return user.email();  // default
  }

  return switch (stepDefinition.userIdentitySource()) {
    case "email" -> user.email();
    case "phone_number" -> user.phoneNumber();
    case "username" -> user.preferredUsername();
    default -> user.email();
  };
}
```

**Step 2.3: Fix resolveUser() method**

Location: Line 169-193

**BEFORE**:
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

**AFTER**:
```java
/**
 * User識別（1st factor）
 *
 * <p>Issue #800修正:
 * <ul>
 *   <li>DB検索を最優先（入力識別子で検索）</li>
 *   <li>AuthenticationStepDefinition.allowRegistration を考慮</li>
 *   <li>userIdentitySource で識別子フィールドを特定</li>
 * </ul>
 *
 * @param tenant テナント
 * @param transaction 認証トランザクション
 * @param email 入力されたメールアドレス
 * @param providerId プロバイダーID
 * @param userQueryRepository ユーザークエリリポジトリ
 * @return 識別されたユーザー
 * @throws AuthenticationException ユーザーが見つからず、登録も許可されていない場合
 */
private User resolveUser(
    Tenant tenant,
    AuthenticationTransaction transaction,
    String email,
    String providerId,
    UserQueryRepository userQueryRepository) {

  // ⭐ Step定義を取得
  AuthenticationStepDefinition stepDefinition =
      getCurrentStepDefinition(transaction, method());

  // ⭐ 1. DB検索最優先（Issue #800修正）
  User existingUser = userQueryRepository.findByEmail(tenant, email, providerId);
  if (existingUser.exists()) {
    return existingUser;
  }

  // ⭐ 2. Transaction再利用（同じ識別子の場合のみ）
  if (transaction.hasUser()) {
    User transactionUser = transaction.user();

    // userIdentitySource でフィールド特定
    String transactionIdentity = extractIdentity(transactionUser, stepDefinition);

    if (email.equals(transactionIdentity)) {
      return transactionUser;  // 同じ識別子 → 再利用（Challenge再送信）
    }
    // 異なる識別子 → 前のUserを破棄して新規作成へ
  }

  // ⭐ 3. 新規ユーザー作成判断
  boolean allowRegistration = stepDefinition != null
      ? stepDefinition.allowRegistration()
      : false;  // デフォルト: 登録禁止

  if (!allowRegistration) {
    throw new AuthenticationException(
        "user_not_found",
        "User with email '" + email + "' not found. " +
        "Registration is not allowed for this authentication flow.");
  }

  // ✅ 新規ユーザー作成
  User user = new User();
  String id = UUID.randomUUID().toString();
  user.setSub(id);
  user.setEmail(email);

  return user;
}
```

#### Verification
```bash
./gradlew :libs:idp-server-authentication-interactors:compileJava
./gradlew spotlessApply
```

---

### Phase 3: SmsAuthenticationChallengeInteractor Fix (30min - 1 hour)

#### Objective
SMS認証も同様のパターンで修正

#### Files to Modify
```
libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/sms/
└── SmsAuthenticationChallengeInteractor.java
```

#### Implementation Details

**Apply same pattern as Email**:

1. Add `getCurrentStepDefinition()` helper
2. Add `extractIdentity()` helper
3. Fix `resolveUser()` method (Line 168-192)

**Key differences**:
- `findByEmail()` → `findByPhone()`
- `email` → `phoneNumber`
- `user.setEmail(email)` → `user.setPhoneNumber(phoneNumber)`

#### Verification
```bash
./gradlew :libs:idp-server-authentication-interactors:compileJava
./gradlew spotlessApply
```

---

### Phase 4: E2E Test Implementation (1-2 hours)

#### Objective
新機能・バグ修正の検証

#### Files to Create/Modify
```
e2e/src/tests/spec/authentication/
├── email-authentication-step-definitions.test.js  (NEW)
└── sms-authentication-step-definitions.test.js   (NEW)

config/examples/e2e/test-tenant/authentication-policy/
└── oauth.json  (UPDATE)
```

#### Test Scenarios

**Test 1: Issue #800 - Email address change**
```javascript
test('メアドAでChallenge後、メアドBに変更 → メアドBでログイン', async () => {
  // 1. メアドA入力
  await authHelper.inputEmail('a@example.com');
  await authHelper.submitEmailChallenge();

  // 2. ブラウザバック
  await authHelper.goBack();

  // 3. メアドB入力
  await authHelper.inputEmail('b@example.com');
  await authHelper.submitEmailChallenge();

  // 4. メアドB宛のOTP取得
  const otpB = await authHelper.getOtpFromEmail('b@example.com');
  await authHelper.inputOtp(otpB);
  await authHelper.submitOtp();

  // 5. トークン取得
  const userInfo = await authHelper.getUserInfo();

  // ✅ メアドBのユーザーでログイン
  expect(userInfo.email).toBe('b@example.com');
});
```

**Test 2: allowRegistration=true - New user creation**
```javascript
test('allowRegistration=true: 未登録メアドで新規登録可能', async () => {
  // Policy: allow_registration: true
  const user = await authHelper.authenticateWithEmail(
    'newuser@example.com',
    { acr_values: 'email-registration-allowed' }
  );

  expect(user.email).toBe('newuser@example.com');
  expect(user.sub).toBeDefined();
});
```

**Test 3: allowRegistration=false - Existing users only**
```javascript
test('allowRegistration=false: 未登録メアドでエラー', async () => {
  // Policy: allow_registration: false
  const result = await authHelper.authenticateWithEmail(
    'nonexistent@example.com',
    { acr_values: 'email-auth-only' }
  );

  expect(result.error).toBe('user_not_found');
  expect(result.error_description).toContain('not allowed');
});
```

**Test 4: userIdentitySource - Phone number**
```javascript
test('userIdentitySource=phone_number: 電話番号で識別', async () => {
  const user = await authHelper.authenticateWithSms('+81901234567');

  expect(user.phone_number).toBe('+81901234567');
  expect(user.sub).toBeDefined();
});
```

#### Verification
```bash
cd e2e
npm test -- email-authentication-step-definitions.test.js
npm test -- sms-authentication-step-definitions.test.js
```

---

### Phase 5: Documentation & Configuration Update (30min)

#### Objective
ドキュメント・サンプル設定の更新

#### Files to Update

**1. CLAUDE.md**

Add to Bug Fix section:
```markdown
## 🐛 Bug Fix履歴

### Issue #800: Email/SMS認証でのUser識別問題修正（2025-10-25）

**問題**: メアド/電話番号変更時に前のUserが引き継がれる

**根本原因**:
- `resolveUser()` で `transaction.hasUser()` を最優先判定
- 入力された識別子での検索より先に既存Userを参照

**修正内容**:
1. `AuthenticationStepDefinition` 拡張
   - `requiresUser`: 1st/2nd factor区別
   - `allowRegistration`: 登録制御
   - `userIdentitySource`: 識別子フィールド指定

2. `EmailAuthenticationChallengeInteractor.resolveUser()` 修正
   - DB検索最優先（Issue #800修正）
   - `allowRegistration` 参照で登録制御
   - `userIdentitySource` で識別子フィールド特定

3. `SmsAuthenticationChallengeInteractor.resolveUser()` 修正
   - Emailと同じパターン適用

**判定順序変更**:
```java
// Before: transaction.hasUser() 最優先（誤り）
if (transaction.hasUser()) { ... }
User existingUser = findByEmail(...);

// After: findByEmail() 最優先（正解）
User existingUser = findByEmail(...);  // ← DB検索最優先
if (transaction.hasUser() && sameIdentity) { ... }
```

**影響範囲**: Email/SMS認証のCHALLENGEステップのみ

**テスト**: E2Eテスト追加（メアド変更シナリオ + 登録制御）
```

**2. oauth.json**

Update with improved policy (use the version created earlier)

#### Verification
```bash
# Documentation check
cat CLAUDE.md | grep -A 20 "Issue #800"

# Configuration validation
cat config/examples/e2e/test-tenant/authentication-policy/oauth.json | jq .
```

---

## 🔄 Implementation Workflow

### Day 1 (3-4 hours)

```bash
# Phase 1: AuthenticationStepDefinition (30min)
vim libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/policy/AuthenticationStepDefinition.java
./gradlew :libs:idp-server-core:compileJava
git add -A
git commit -m "feat: extend AuthenticationStepDefinition with 1st/2nd factor support"

# Phase 2: EmailAuthenticationChallengeInteractor (1-2 hours)
vim libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/email/EmailAuthenticationChallengeInteractor.java
./gradlew spotlessApply
./gradlew :libs:idp-server-authentication-interactors:compileJava
git add -A
git commit -m "fix: resolve user by input identifier first in email authentication (Issue #800)"

# Phase 3: SmsAuthenticationChallengeInteractor (30min-1 hour)
vim libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/sms/SmsAuthenticationChallengeInteractor.java
./gradlew spotlessApply
./gradlew :libs:idp-server-authentication-interactors:compileJava
git add -A
git commit -m "fix: resolve user by input identifier first in sms authentication (Issue #800)"
```

### Day 2 (2-3 hours)

```bash
# Phase 4: E2E Tests (1-2 hours)
vim e2e/src/tests/spec/authentication/email-authentication-step-definitions.test.js
vim e2e/src/tests/spec/authentication/sms-authentication-step-definitions.test.js
cd e2e && npm test
git add -A
git commit -m "test: add E2E tests for step definitions and Issue #800 fix"

# Phase 5: Documentation (30min)
vim CLAUDE.md
vim config/examples/e2e/test-tenant/authentication-policy/oauth.json
git add -A
git commit -m "docs: update CLAUDE.md and oauth.json with step definitions"

# Build & Test
./gradlew build
cd e2e && npm test

# PR Creation
git push -u origin feature/authentication-step-definitions-1st-2nd-factor
gh pr create --title "feat: Add step_definitions with 1st/2nd factor support (Issue #800)" \
  --label "enhancement,bug" \
  --body "See IMPLEMENTATION_PLAN_ISSUE_800.md for details"
```

---

## ✅ Completion Checklist

### Before Merge
- [ ] All Java files compile without errors
- [ ] `./gradlew spotlessApply` executed
- [ ] All unit tests pass: `./gradlew test`
- [ ] All E2E tests pass: `cd e2e && npm test`
- [ ] CLAUDE.md updated with bug fix details
- [ ] oauth.json updated with step_definitions examples
- [ ] No regression in existing authentication flows

### Code Quality
- [ ] Javadoc added to new/modified public methods
- [ ] Error messages are user-friendly
- [ ] Null-safety checks in place
- [ ] No TODO comments in production code

### Testing
- [ ] Issue #800 scenario tested and verified
- [ ] allowRegistration=true/false tested
- [ ] userIdentitySource tested (email, phone_number)
- [ ] Existing authentication flows still work

---

## 🚨 Risk Mitigation

### High Risk
| Risk | Impact | Mitigation |
|------|--------|------------|
| Regression in existing auth flows | High | Run all E2E tests before merge |
| Null pointer in legacy policies | Medium | Add null-safety checks |
| Performance degradation | Low | Step definition lookup is O(n) but n is small |

### Rollback Plan
If critical issues found after merge:
1. Revert commits in reverse order
2. Run regression tests
3. Investigate root cause offline
4. Re-implement with fixes

---

## 📊 Success Metrics

- ✅ Issue #800 fully resolved (email change works correctly)
- ✅ 0 regressions in existing authentication flows
- ✅ E2E test coverage for new features
- ✅ Documentation updated
- ✅ Keycloak pattern compliance improved

---

**Ready to start Phase 1!** 🚀
