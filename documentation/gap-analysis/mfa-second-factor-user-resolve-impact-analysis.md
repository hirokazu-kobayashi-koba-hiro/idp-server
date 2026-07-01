# MFA 2段目 user_resolve 修正の影響分析

## 修正概要

Issue #1497: MFA 2段目の PasswordAuthenticationInteractor で `user_resolve`（`userMappingRules`）が実行されず、外部認証由来の `custom_properties` 等が反映されない問題。

2段目は、1段目で確立した認証済みユーザー（`transaction.user()`）を **identity の正**とし、外部認証結果から得た**非識別の属性のみ**をマージする（enrichment）。**identity の再解決は行わない**。

### 変更ファイル

| ファイル | 変更内容 |
|---------|---------|
| `PasswordAuthenticationInteractor.java` | 2段目で `userMappingRules` 設定時に、allowlist で絞った非識別属性のみを認証済みユーザーにマージ |
| `AuthenticationTransaction.java` | 同一ユーザーでも `updateWithUser` で最新の user データ（enrichment 結果）を反映 |

---

## セキュリティ設計: identity すり替えの防止（CWE-287）

### 何が危険か

2段目の外部認証は、**提出された資格情報（＝入力）に影響される**。もし2段目で `updateWith` に identity フィールドを含む User を渡すと、1段目で確立した identity（`email` / `preferred_username` / `status` / `roles`）が2段目の入力由来の値に**上書き**されうる。`AuthenticationTransaction.isSameUser()` は `sub` のみを比較し、`updateWith` は `sub` を1段目に固定するため、**sub 一致ガードは素通り**し、属性だけがすり替わる（identity 汚染）。

### 対策: allowlist enrichment

`IdentityVerificationUserUpdater`（IDA）の設計を踏襲し、2段目では以下を徹底する。

1. **identity を再解決しない**。`transaction.user()` が唯一の identity 源。`resolveUserFromExternalAuth()`（DB 検索 + JIT sub 採番）は1段目専用で、2段目からは呼ばない。
2. **allowlist（`SECOND_FACTOR_ENRICHABLE_CLAIMS`）でマージ対象を絞る**。許可されるのは記述的なプロフィールクレーム（`name`, `given_name`, `family_name`, `middle_name`, `nickname`, `profile`, `picture`, `website`, `gender`, `birthdate`, `zoneinfo`, `locale`, `address`）と `custom_properties` のみ。
3. **識別子・lifecycle・権限は不可侵**。`sub` / `preferred_username` / `email` / `phone_number`（いずれもテナントの一意キー・`user_identity_source` になりうる）、`status`、`roles` / `permissions` / `authentication_devices` はマージ対象外。allowlist 外を狙ったマッピングルールは **drop し WARN ログ**を出す。

> IDA の `PATCHABLE_STANDARD_CLAIMS` より**さらに厳格**（`email` / `phone_number` を除外）。IDA は信頼できる検証結果を既知ユーザーに適用するのに対し、2段目のソースは提出資格情報に影響されるため。

---

## 修正箇所1: PasswordAuthenticationInteractor.resolveUser()

### Before（脆弱: identity を再解決し全フィールドを上書き）

```java
if (stepDefinition.allowRegistration() && configuration.exists() && executionResult.isSuccess()) {
    if (!interactionConfig.userResolve().userMappingRules().isEmpty()) {
        // 提出入力だけから identity を再解決（1段目 user を無視）
        User resolved = resolveUserFromExternalAuth(...);
        // email / preferred_username / status / roles まで上書きされる
        return transaction.user().updateWith(resolved);
    }
}
return transaction.user();
```

### After（enrichment のみ・allowlist）

```java
if (configuration.exists() && executionResult.isSuccess()) {
    List<MappingRule> userMappingRules = interactionConfig.userResolve().userMappingRules();
    if (!userMappingRules.isEmpty()) {
        // identity は再解決しない。allowlist で絞った非識別属性のみの patch を作る
        User enrichmentPatch =
            buildSecondFactorEnrichmentPatch(request, executionResult, userMappingRules);
        return transaction.user().updateWith(enrichmentPatch);
    }
}
return transaction.user();
```

`buildSecondFactorEnrichmentPatch()` は、マッピング結果を `SECOND_FACTOR_ENRICHABLE_CLAIMS` + `custom_properties` で `retain` し、それ以外を drop（WARN）した User を返す。`sub` を持たないため、`updateWith` は1段目 identity を保持する。

### 実行条件

1. 2段目（`requiresUser()` = true）かつ `transaction.hasUser()` = true
2. `configuration.exists()` = true（password-authentication の設定が存在）
3. `executionResult.isSuccess()` = true（外部認証サービスが成功応答を返した）
4. `userMappingRules` が空でない

`user_resolve` を設定しない通常の MFA フローでは条件4が満たされないため、**既存の動作に変更なし**（`transaction.user()` をそのまま返す）。

> 注: 旧実装が要求していた `allowRegistration()` 条件は削除した。enrichment は認証済みユーザーへの属性付与であり、新規登録可否とは無関係のため。

---

## 修正箇所2: AuthenticationTransaction.updateWithUser()

### Before

```java
if (!request.isSameUser(interactionRequestResult.user())) {
    throw new BadRequestException("User is not the same as the request");
}
return request;  // ← 同一ユーザーなら何もしない。2段目の enrichment 結果が消失
```

### After

```java
if (!request.isSameUser(interactionRequestResult.user())) {
    throw new BadRequestException("User is not the same as the request");
}
// Same user: update with latest user data (2nd factor enrichment adds custom_properties)
return request.updateWithUser(interactionRequestResult);
```

### この変更の安全性

enrichment 結果を DB へ反映するために必要。interactor 側が allowlist で非識別属性に限定しているため、この伝播は identity を変えない。`isSameUser()` は `sub` 比較で、interactor は `sub` を変更しないため同一性は保たれる。

---

## User.updateWith() の性質（重要な注意）

`User.updateWith(patchUser)` は `sub` / `providerId` / `externalUserId` / `hashedPassword` / `verifiedClaims` / `permissions` / `credentials` を immutable に保つが、**`email` / `email_verified` / `preferred_username` / `status` / `roles` / `custom_properties` / `authentication_devices` は `patchUser` が値を持てば上書きする**。

つまり `updateWith` 自体は identity フィールドを保護しない。2段目の安全性は、**呼び出し側（`buildSecondFactorEnrichmentPatch`）が patch にこれらを含めない**ことで担保される。将来 `updateWith` を別経路から呼ぶ際も、patch の出所が信頼できるかを都度確認すること。

---

## 影響分析: 各 Interactor の2段目

| Interactor | 2段目の動作 | 返す User | 影響 |
|-----------|------------|----------|------|
| **Email** (allowRegistration=true) | `transaction.user()` に `setEmail()` して返す | 同一オブジェクト | なし |
| **SMS** (allowRegistration=true) | `transaction.user()` に `setPhoneNumber()` して返す | 同一オブジェクト | なし |
| **Password** (user_resolve なし) | `transaction.user()` そのまま返す | 同一オブジェクト | なし |
| **Password** (user_resolve あり) | `transaction.user().updateWith(enrichmentPatch)` | **新 User（非識別属性のみマージ）** | **修正対象: identity を保ちつつ属性を反映** |
| **FIDO2 / FIDO-UAF** | `transaction.user()` そのまま返す | 同一オブジェクト | なし |

`updateWithUser` の1段目パス（`!request.hasUser()`）は変更していないため影響なし。CIBA は通常1段で完了し、仮に2段目に入っても同分析が適用される。

---

## テスト確認

- [x] ユニット: `PasswordAuthenticationInteractorSecondFactorEnrichmentTest` — allowlist が identifiers/email/status/roles を drop し、`name`/`custom_properties` のみ通すこと。session user に適用しても identity（sub/email/preferred_username）が保たれること
- [x] 全モジュール + core ユニットテスト通過
- [x] E2E（`security/mfa_second_factor_identity_preservation.test.js`）: 2段目で注入した identity フィールドが無視され、1段目 identity が保持されること + `custom_properties` は反映されること
- [x] E2E 回帰（`scenario-12-mfa-second-factor-user-resolve.test.js`）: custom_properties が引き続きマージされること
