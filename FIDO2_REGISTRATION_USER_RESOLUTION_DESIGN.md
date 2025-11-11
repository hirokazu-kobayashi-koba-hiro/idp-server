# FIDO2 Registration User Resolution 設計書

**作成日**: 2025-11-09
**対象**: FIDO2 Registration Challenge/Registration
**目的**: Userなし状態からのFIDO2登録を可能にする

---

## 📋 現状分析

### 現在の実装の問題点

#### 1. Fido2RegistrationChallengeInteractor (Line 138)
```java
return new AuthenticationInteractionRequestResult(
    ...,
    transaction.user(),  // ← Userがいない場合、nullまたは空User
    contents,
    ...
);
```

**問題**:
- `transaction.hasUser() == false` の場合、`transaction.user()`は空のUserを返す
- 次のRegistrationステップで`baseUser = transaction.user()`がnullまたは空になる

#### 2. resolveUsernameFromRequest() (Line 168-204)
```java
private Map<String, Object> resolveUsernameFromRequest(...) {
  Map<String, Object> requestMap = new HashMap<>(request.toMap());

  // Strategy 1: transaction.hasUser()最優先
  if (transaction.hasUser()) {
    User user = transaction.user();
    String username = resolveUsernameFromUser(user, identityPolicy);
    requestMap.put("username", username);
    return requestMap;
  }

  // Strategy 2: リクエストから取得
  if (requestMap.containsKey("username")) {
    // usernameをそのまま使用
  }

  return requestMap;
}
```

**問題**:
- `transaction.hasUser() == false` かつ `request`に`username`がない場合
  → `extractUserInfo()`がエラー（Line 128: `request.getValueAsString("username")`）

#### 3. WebAuthn4jRegistrationChallengeExecutor.extractUserInfo() (Line 127-139)
```java
private WebAuthn4jUser extractUserInfo(AuthenticationExecutionRequest request) {
  String username = request.getValueAsString("username");  // ← usernameがないと例外
  String displayName = request.optValueAsString("displayName", username);

  String userId = Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(username.getBytes(StandardCharsets.UTF_8));

  return new WebAuthn4jUser(userId, username, displayName);
}
```

**問題**:
- `username`が必須だが、Userなし状態では提供されない

#### 4. Fido2RegistrationInteractor (Line 127)
```java
User baseUser = transaction.user();  // ← Userがいない場合、空User
```

**問題**:
- `baseUser`が空の場合、`baseUser.sub()`がnullでユーザー作成ができない

---

## 🎯 設計要件

### 必須要件

1. **Userなし状態からのFIDO2登録**
   - リクエストに`username`を含めることで、新規Userを作成
   - Email/SMS認証なしで直接FIDO2登録可能

2. **既存Userへの追加登録（現在の挙動維持）**
   - Email/SMS認証後 → FIDO2登録
   - `transaction.user()`が存在する場合、そのUserに紐づける

3. **Issue #800パターンの回避**
   - `transaction.hasUser()`最優先ではなく、入力識別子を優先
   - Email/SMS認証と同じresolveUser()パターン

### ユースケース

#### Use Case 1: パスワードレス新規登録（Userなし）
```
1. ユーザーがアプリ起動（初回）
2. FIDO2 Registration Challengeをリクエスト
   POST /auth/interactions
   {
     "type": "fido2-registration-challenge",
     "username": "alice@example.com"  // ← リクエストに含める
   }

3. Challenge生成
   → User解決: findByPreferredUsername("alice@example.com")
   → 存在しない → 新規User作成（仮）
   → transaction.user = User(sub=新規UUID, preferredUsername="alice@example.com")

4. FIDO2 Registration
   POST /auth/interactions
   {
     "type": "fido2-registration",
     "id": "...",
     "response": { ... }
   }

   → baseUser = transaction.user()  // 新規作成されたUser
   → credential.username = "alice@example.com"
   → DB保存: User + Credential

5. 認証完了 → UserA でログイン
```

#### Use Case 2: 既存Userへの追加（Email認証後）
```
1. Email CHALLENGE: alice@example.com
   → transaction.user = UserA

2. Email AUTHENTICATION: OTP検証
   → transaction.user = UserA（継続）

3. FIDO2 Registration Challenge
   → resolveUser(): transaction.hasUser() && 同じusername
   → UserAを再利用

4. FIDO2 Registration
   → baseUser = UserA
   → UserAにcredential追加

5. 認証完了 → UserA でログイン
```

#### Use Case 3: Email変更後のFIDO2登録（Issue #800類似）
```
1. Email CHALLENGE: alice@example.com
   → transaction.user = UserA

2. ブラウザバック → Email CHALLENGE: bob@example.com
   → resolveUser(): findByEmail("bob@example.com")最優先
   → transaction.user = UserB（更新）

3. FIDO2 Registration Challenge
   → resolveUser(): findByPreferredUsername("bob@example.com")最優先
   → transaction.user = UserB（正しい）

4. FIDO2 Registration
   → baseUser = UserB
   → UserBにcredential追加 ✅
```

---

## 🏗️ 設計案

### Option 1: Email/SMSと同じresolveUser()パターン（推奨）

#### 実装: Fido2RegistrationChallengeInteractor

```java
@Override
public AuthenticationInteractionRequestResult interact(
    Tenant tenant,
    AuthenticationTransaction transaction,
    AuthenticationInteractionType type,
    AuthenticationInteractionRequest request,
    RequestAttributes requestAttributes,
    UserQueryRepository userQueryRepository) {

  AuthenticationConfiguration configuration = configurationRepository.get(tenant, "fido2");
  AuthenticationInteractionConfig authenticationInteractionConfig =
      configuration.getAuthenticationConfig("fido2-registration-challenge");
  AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();

  AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

  // ✅ Step 1: username解決（transaction.user()またはrequest）
  Map<String, Object> requestMap = resolveUsernameFromRequest(tenant, transaction, request);
  String username = extractUsername(requestMap);

  // ✅ Step 2: User解決または作成（Email/SMSと同じパターン）
  User user = resolveUser(tenant, transaction, username, userQueryRepository);

  // Challenge生成
  AuthenticationExecutionRequest authenticationExecutionRequest =
      new AuthenticationExecutionRequest(requestMap);
  AuthenticationExecutionResult executionResult =
      executor.execute(tenant, transaction.identifier(), authenticationExecutionRequest, requestAttributes, execution);

  // ... エラー処理 ...

  // ✅ 解決されたUserを返す
  return new AuthenticationInteractionRequestResult(
      AuthenticationInteractionStatus.SUCCESS,
      type,
      operationType(),
      method(),
      user,  // ← 解決されたUser
      contents,
      DefaultSecurityEventType.fido2_registration_challenge_success);
}

/**
 * Resolves or creates User based on username.
 *
 * <p>Resolution strategy (same as Email/SMS authentication):
 *
 * <ol>
 *   <li>Search by preferredUsername (highest priority)
 *   <li>If transaction.hasUser() && same username: reuse existing User
 *   <li>Create new User with generated UUID
 * </ol>
 *
 * @param tenant the tenant
 * @param transaction the authentication transaction
 * @param username the username (preferredUsername)
 * @param userQueryRepository the user query repository
 * @return the resolved or created User
 */
private User resolveUser(
    Tenant tenant,
    AuthenticationTransaction transaction,
    String username,
    UserQueryRepository userQueryRepository) {

  // ✅ Strategy 1: Search by preferredUsername (highest priority)
  User existingUser = userQueryRepository.findByPreferredUsernameNoProvider(tenant, username);
  if (existingUser.exists()) {
    log.debug("FIDO2 registration: found existing user by preferredUsername: {}", username);
    return existingUser;
  }

  // ✅ Strategy 2: Reuse transaction.user() if same username
  if (transaction.hasUser()) {
    User transactionUser = transaction.user();
    TenantIdentityPolicy identityPolicy = tenant.identityPolicyConfig();
    String transactionUsername = resolveUsernameFromUser(transactionUser, identityPolicy);

    if (username.equals(transactionUsername)) {
      log.debug("FIDO2 registration: reusing transaction user with same username: {}", username);
      return transactionUser;
    }
    // ⚠️ Different username → discard transaction.user(), create new User
  }

  // ✅ Strategy 3: Create new User
  User user = new User();
  String id = UUID.randomUUID().toString();
  user.setSub(id);
  user.setPreferredUsername(username);

  log.debug("FIDO2 registration: created new user with sub: {}, preferredUsername: {}", id, username);
  return user;
}

/**
 * Extracts username from request map.
 *
 * @param requestMap the request map
 * @return the username
 * @throws IllegalArgumentException if username is not found
 */
private String extractUsername(Map<String, Object> requestMap) {
  if (!requestMap.containsKey("username")) {
    throw new IllegalArgumentException(
        "FIDO2 registration requires 'username' in request or transaction.user()");
  }
  return requestMap.get("username").toString();
}
```

#### 実装: resolveUsernameFromRequest()修正

```java
private Map<String, Object> resolveUsernameFromRequest(
    Tenant tenant,
    AuthenticationTransaction transaction,
    AuthenticationInteractionRequest request) {

  Map<String, Object> requestMap = new HashMap<>(request.toMap());

  // ✅ Strategy 1: Use username from request (highest priority)
  if (requestMap.containsKey("username")) {
    log.debug("FIDO2 registration: using username from request: {}", requestMap.get("username"));
    return requestMap;
  }

  // ✅ Strategy 2: Resolve from transaction.user() (fallback)
  if (transaction.hasUser()) {
    User user = transaction.user();
    TenantIdentityPolicy identityPolicy = tenant.identityPolicyConfig();
    String username = resolveUsernameFromUser(user, identityPolicy);

    if (username != null && !username.isEmpty()) {
      log.debug("FIDO2 registration: resolved username from transaction.user(): {}", username);
      requestMap.put("username", username);

      if (user.name() != null && !user.name().isEmpty()) {
        requestMap.put("displayName", user.name());
      }
    }
  }

  return requestMap;
}
```

**変更点**:
- ✅ **Strategy順序逆転**: `request.username`を最優先（Issue #800対策）
- ✅ **transaction.user()はフォールバック**: 既存Userへの追加登録時のみ使用

---

### Option 2: User作成を遅延させる（非推奨）

#### 概要
- Challenge時はUserを作成せず、Registrationで作成

#### 問題点
- ❌ `transaction.user()`がnullのまま
- ❌ Registrationステップで複雑なロジックが必要
- ❌ Email/SMSパターンと整合性が取れない

**結論**: Option 1を採用

---

## 🔄 フロー比較

### Before（現在の実装）

```
[Userなし状態]
1. FIDO2 Reg Challenge
   ↓
   transaction.hasUser() = false
   ↓
   request.username がない
   ↓
   ❌ extractUserInfo() エラー（usernameがない）

[Email認証後]
1. Email CHALLENGE → transaction.user = UserA
2. FIDO2 Reg Challenge
   ↓
   transaction.hasUser() = true（最優先）
   ↓
   username = UserAのemail
   ↓
   ✅ Challenge生成
```

### After（提案設計）

```
[Userなし状態]
1. FIDO2 Reg Challenge + username in request
   ↓
   resolveUsernameFromRequest():
     request.username = "alice@example.com"（最優先）
   ↓
   resolveUser():
     findByPreferredUsername("alice@example.com")
     → 存在しない
     → 新規User作成
   ↓
   transaction.user = User(sub=新規UUID, preferredUsername="alice@example.com")
   ↓
   ✅ Challenge生成

2. FIDO2 Registration
   ↓
   baseUser = transaction.user()  // 新規作成されたUser
   ↓
   ✅ Credential保存

[Email認証後]
1. Email CHALLENGE → transaction.user = UserA
2. FIDO2 Reg Challenge
   ↓
   resolveUsernameFromRequest():
     request.username がない
     → transaction.user()から解決（フォールバック）
     → username = UserAのemail
   ↓
   resolveUser():
     findByPreferredUsername(UserAのemail)
     → UserA存在
     → UserAを返す
   ↓
   transaction.user = UserA（継続）
   ↓
   ✅ Challenge生成
```

---

## 🧪 テストシナリオ

### Test 1: Userなし状態からのFIDO2登録

```javascript
test('FIDO2 registration without existing user', async () => {
  // 1. FIDO2 Registration Challenge（Userなし）
  const challengeResponse = await authHelper.requestFido2RegistrationChallenge({
    username: 'alice@example.com'
  });

  expect(challengeResponse.status).toBe('success');
  expect(challengeResponse.challenge).toBeDefined();

  // 2. FIDO2 Registration
  const credential = await authHelper.createFido2Credential(challengeResponse);
  const registrationResponse = await authHelper.completeFido2Registration(credential);

  expect(registrationResponse.status).toBe('success');

  // 3. 検証: 新規Userが作成されている
  const tokens = await authHelper.getTokens();
  const userInfo = await authHelper.getUserInfo(tokens.access_token);

  expect(userInfo.preferred_username).toBe('alice@example.com');
  expect(userInfo.sub).toBeDefined();  // 新規UUID
});
```

### Test 2: Email認証後のFIDO2追加登録

```javascript
test('FIDO2 registration after email authentication', async () => {
  // 1. Email認証
  const userA = await authHelper.authenticateWithEmail('alice@example.com');
  const userAId = userA.sub;

  // 2. FIDO2 Registration Challenge（usernameなし）
  const challengeResponse = await authHelper.requestFido2RegistrationChallenge();

  // 3. FIDO2 Registration
  const credential = await authHelper.createFido2Credential(challengeResponse);
  await authHelper.completeFido2Registration(credential);

  // 4. 検証: 同じUserに紐づいている
  const tokens = await authHelper.getTokens();
  const userInfo = await authHelper.getUserInfo(tokens.access_token);

  expect(userInfo.sub).toBe(userAId);  // 同じUser
  expect(userInfo.preferred_username).toBe('alice@example.com');
});
```

### Test 3: Email変更後のFIDO2登録（Issue #800類似）

```javascript
test('FIDO2 registration after email change', async () => {
  // 1. Email CHALLENGE: alice@example.com
  await authHelper.startAuthenticationFlow();
  await authHelper.inputEmail('alice@example.com');
  await authHelper.submitEmailChallenge();

  // 2. ブラウザバック → Email CHALLENGE: bob@example.com
  await authHelper.goBack();
  await authHelper.inputEmail('bob@example.com');
  await authHelper.submitEmailChallenge();

  // 3. FIDO2 Registration Challenge（usernameはtransactionから）
  const challengeResponse = await authHelper.requestFido2RegistrationChallenge();

  // 4. FIDO2 Registration
  const credential = await authHelper.createFido2Credential(challengeResponse);
  await authHelper.completeFido2Registration(credential);

  // 5. Email AUTHENTICATION: bob@example.com
  const otpB = await authHelper.getOtpFromEmail('bob@example.com');
  await authHelper.inputOtp(otpB);
  await authHelper.submitOtp();

  // 6. 検証: UserBでログイン
  const tokens = await authHelper.getTokens();
  const userInfo = await authHelper.getUserInfo(tokens.access_token);

  expect(userInfo.email).toBe('bob@example.com');

  // 7. クレデンシャル確認: UserBに紐づいている
  const credentials = await authHelper.getUserCredentials(tokens.access_token);
  expect(credentials.length).toBe(1);
  expect(credentials[0].username).toBe('bob@example.com');  // ← 正しい
});
```

---

## 📝 実装チェックリスト

### Phase 1: Fido2RegistrationChallengeInteractor修正

- [ ] `resolveUser()` メソッド追加（Email/SMSパターン）
- [ ] `resolveUsernameFromRequest()` 修正（request最優先）
- [ ] `extractUsername()` メソッド追加（username抽出）
- [ ] `interact()` メソッド修正（resolveUser()呼び出し）
- [ ] Javadoc追加（resolveUser()の動作説明）

### Phase 2: テスト追加

- [ ] 単体テスト: resolveUser()の3パターン検証
- [ ] E2Eテスト: Userなし状態からのFIDO2登録
- [ ] E2Eテスト: Email認証後のFIDO2追加登録
- [ ] E2Eテスト: Email変更後のFIDO2登録（Issue #800類似）

### Phase 3: ドキュメント更新

- [ ] CLAUDE.md: FIDO2登録パターン追記
- [ ] FIDO2_SECURITY_ANALYSIS.md: resolveUser()パターン追記

---

## 🎯 まとめ

### 採用設計: Option 1

**理由**:
1. ✅ Email/SMSと同じresolveUser()パターン → 一貫性
2. ✅ Issue #800対策（入力識別子最優先）
3. ✅ Userなし状態からの登録が可能
4. ✅ 既存の挙動を維持（後方互換性）

### 実装優先度

**P0 (必須)**:
- Fido2RegistrationChallengeInteractor修正
- resolveUser()実装

**P1 (推奨)**:
- E2Eテスト追加
- ドキュメント更新

**P2 (オプション)**:
- AuthenticationFlowContext導入（Issue #800 Phase 2）
