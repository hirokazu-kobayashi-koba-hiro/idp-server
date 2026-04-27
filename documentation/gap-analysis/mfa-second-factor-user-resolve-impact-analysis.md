# MFA 2段目 user_resolve 修正の影響分析

## 修正概要

Issue #1497: MFA 2段目の PasswordAuthenticationInteractor で `user_resolve`（`userMappingRules`）が実行されず、`custom_properties` が反映されない問題。

### 変更ファイル

| ファイル | 変更内容 |
|---------|---------|
| `PasswordAuthenticationInteractor.java` | 2段目で `allowRegistration` + `userMappingRules` 設定時に外部認証結果をマージ |
| `AuthenticationTransaction.java` | 同一ユーザーでも `updateWithUser` で最新の user データを反映 |

---

## 修正箇所1: PasswordAuthenticationInteractor.resolveUser()

### Before

```java
if (stepDefinition != null && stepDefinition.requiresUser()) {
    if (!transaction.hasUser()) {
        return User.notFound();
    }
    return transaction.user();  // ← 常に早期リターン。userMappingRules に到達しない
}
```

### After

```java
if (stepDefinition != null && stepDefinition.requiresUser()) {
    if (!transaction.hasUser()) {
        return User.notFound();
    }

    // 2nd factor with user_resolve: apply userMappingRules to merge external data
    if (stepDefinition.allowRegistration()
        && configuration.exists()
        && executionResult.isSuccess()) {
        AuthenticationInteractionConfig interactionConfig =
            configuration.getAuthenticationConfig("password-authentication");
        if (!interactionConfig.userResolve().userMappingRules().isEmpty()) {
            User resolved = resolveUserFromExternalAuth(...);
            return transaction.user().updateWith(resolved);
            // ↑ 既存ユーザーをベースに、resolved の非空フィールドで上書きした新Userを返す
        }
    }

    return transaction.user();
}
```

### 実行条件（3つすべて満たす場合のみ発火）

1. `stepDefinition.allowRegistration()` = true（認証ポリシーで明示的に設定）
2. `configuration.exists()` = true（password-authentication の設定が存在）
3. `executionResult.isSuccess()` = true（外部認証サービスが成功応答を返した）
4. `userMappingRules` が空でない（user_resolve にマッピングルールが設定されている）

通常の MFA フロー（外部認証なし）では条件2-4が満たされないため、**既存の動作に変更なし**。

---

## 修正箇所2: AuthenticationTransaction.updateWithUser()

### Before

```java
// Authentication success - add user to transaction
if (!request.hasUser()) {
    return request.updateWithUser(interactionRequestResult);  // 1段目: ユーザーをセット
}

if (!request.isSameUser(interactionRequestResult.user())) {
    throw new BadRequestException("User is not the same as the request");
}

return request;  // ← 同一ユーザーなら何もしない。2段目のマージ結果が消失
```

### After

```java
if (!request.hasUser()) {
    return request.updateWithUser(interactionRequestResult);  // 1段目: ユーザーをセット
}

if (!request.isSameUser(interactionRequestResult.user())) {
    throw new BadRequestException("User is not the same as the request");
}

// Same user: update with latest user data
return request.updateWithUser(interactionRequestResult);  // ← 最新ユーザーで更新
```

### この変更が必要な理由

`PasswordAuthenticationInteractor` が `updateWith(resolved)` で新しい User を返しても、`AuthenticationTransaction` の旧コードでは `return request`（変更前の user を保持したまま）で、DB 永続化時にマージ結果が反映されなかった。

---

## 影響分析: 全認証フロー

### updateWithUser の呼び出しパス

```
各 Interactor.interact()
  → resolveUser()  ← 認証済みユーザーを返す
  → AuthenticationInteractionRequestResult に格納
  ↓
OAuthFlowEntryService / CibaFlowEntryService / UserOperationEntryService
  → AuthenticationTransaction.updateWith(result)
    → updateWithUser(result)  ← ★変更箇所
  → authenticationTransactionCommandRepository.update()  ← DB永続化
```

### 各 Interactor の2段目の動作

| Interactor | 2段目の動作 | 返す User | 影響 |
|-----------|------------|----------|------|
| **Email** (allowRegistration=true) | `transaction.user()` に `setEmail()` して返す | 同一オブジェクト（ミュータブル変更） | なし: 同じ参照なので updateWithUser しても結果同じ |
| **Email** (allowRegistration=false) | `transaction.user()` そのまま返す | 同一オブジェクト | なし: 同じ user で updateWithUser → 同じ結果 |
| **SMS** (allowRegistration=true) | `transaction.user()` に `setPhoneNumber()` して返す | 同一オブジェクト（ミュータブル変更） | なし: Email と同じ |
| **SMS** (allowRegistration=false) | `transaction.user()` そのまま返す | 同一オブジェクト | なし |
| **Password** (user_resolve なし) | `transaction.user()` そのまま返す | 同一オブジェクト | なし |
| **Password** (user_resolve あり) | `transaction.user().updateWith(resolved)` | **新しい User（マージ済み）** | **修正対象: 正しく反映されるようになる** |
| **FIDO2 / WebAuthn** | `transaction.user()` そのまま返す | 同一オブジェクト | なし |
| **FIDO-UAF** | `transaction.user()` そのまま返す | 同一オブジェクト | なし |

### 1段目の動作

1段目は `!request.hasUser()` パスで `updateWithUser` が呼ばれる。この分岐は変更していないため**影響なし**。

### CIBAフローの動作

`CibaFlowEntryService` も `authenticationTransaction.updateWith(result)` を呼ぶが、CIBA は通常1段で完了し、MFA 2段目のパスには入らない。仮に入っても上記と同じ分析が適用される。

---

## User.updateWith() の安全性

```java
public User updateWith(User patchUser) {
    return new User(
        this.sub,                 // immutable: 既存値保持
        this.providerId,          // immutable: 既存値保持
        this.externalUserId,      // immutable: 既存値保持
        ...
        patchUser.hasName() ? patchUser.name() : this.name,  // 非空なら上書き
        ...
        this.hashedPassword,      // immutable: 既存値保持
        this.verifiedClaims,      // immutable: 既存値保持
        patchUser.hasCustomProperties() ? patchUser.customPropertiesValue() : this.customProperties,
        ...
    );
}
```

- `sub`, `providerId`, `externalUserId` → immutable（認証識別子は変更不可）
- `hashedPassword`, `verifiedClaims`, `credentials` → immutable（セキュリティ上重要なフィールドは変更不可）
- `name`, `email`, `custom_properties`, `roles` 等 → resolved に値がある場合のみ上書き

---

## テスト確認

- [x] 全ユニットテスト通過（`./gradlew test` BUILD SUCCESSFUL）
- [ ] E2E テスト: MFA（email → password）フローで custom_properties がマージされること
- [ ] E2E テスト: 通常の password 認証（1段目）に影響がないこと
- [ ] E2E テスト: email/SMS の MFA フローに影響がないこと
