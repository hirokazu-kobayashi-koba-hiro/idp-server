# Security Test Suite - Issue #801

## 概要

Issue #801のセキュリティ監査用E2Eテストスイート。Issue #800で発見された「状態の不正な引き継ぎ」脆弱性パターン（認証識別子の切り替え攻撃）を検証します。

## 実装済みテスト

### S1: 認証識別子の切り替え攻撃 (Email/SMS)
**ファイル**: `identifier_switching_attack.test.js`

**重大度**: Critical
**CVE**: CWE-287 (Improper Authentication)
**関連**: Issue #800修正の検証

### S9: Redirect URI切り替え攻撃 (OAuth 2.0)
**ファイル**: `redirect_uri_switching_attack.test.js`

**重大度**: Critical
**CVE**: CWE-601 (URL Redirection to Untrusted Site)
**関連**: RFC 6749 Section 4.1.3 準拠確認

#### 攻撃シナリオ

**Token Endpoint Redirect URI Mismatch**:
1. 正規のredirect_uriで認可リクエスト
2. 認可コード取得
3. 攻撃者のredirect_uriでトークンリクエスト
4. **期待**: invalid_grant エラー ✅
5. **脆弱**: トークン発行 → 認可コード漏洩 ❌

**Unregistered Redirect URI**:
1. 登録されていないredirect_uriで認可リクエスト
2. **期待**: invalid_request エラー ✅
3. **脆弱**: 認可成功 → フィッシング攻撃 ❌

**Substring Matching Attack**:
1. 登録URI: `https://example.com/callback`
2. 攻撃URI: `https://example.com/callback.evil.com`
3. **期待**: invalid_request エラー（完全一致検証） ✅
4. **脆弱**: 認可成功（部分一致検証） ❌

#### テスト内容

##### 1. Token Endpoint Redirect URI Validation
```javascript
it("Should reject token request when redirect_uri does not match authorization request")
```

**検証フロー**:
1. 正規のredirect_uriで認可リクエスト → 認可コード取得
2. 異なるredirect_uriでトークンリクエスト
3. トークンエンドポイントのレスポンスを検証
4. **PASS**: `status === 400 && error === "invalid_grant"`
5. **FAIL**: `status === 200 && access_token` → RFC 6749 Section 4.1.3違反

**RFC 6749 Section 4.1.3**:
> "REQUIRED, if the 'redirect_uri' parameter was included in the
> authorization request as described in Section 4.1.1, and their
> values MUST be identical."

##### 2. Redirect URI Omission Attack
```javascript
it("Should reject token request when redirect_uri is missing but was present in authorization")
```

**検証フロー**:
1. redirect_uri付きで認可リクエスト
2. redirect_uri省略でトークンリクエスト
3. **PASS**: `status === 400 && error === "invalid_grant"`
4. **FAIL**: トークン発行成功

##### 3. Unregistered Redirect URI Validation
```javascript
it("Should reject authorization request with unregistered redirect_uri")
```

**検証フロー**:
1. 登録されていないredirect_uriで認可リクエスト
2. **PASS**: `error === "invalid_request" || "unauthorized_client"`
3. **FAIL**: 認可成功 → フィッシング攻撃が可能

**RFC 6749 Section 3.1.2.3**:
> "The authorization server MUST require the following clients to
> register their redirection endpoint"

##### 4. Exact Match Validation
```javascript
it("Should validate exact match of redirect_uri (no substring matching)")
```

**検証フロー**:
1. 登録URI + ".evil.com" で認可リクエスト
2. **PASS**: エラー返却（完全一致検証）
3. **FAIL**: 認可成功（部分一致検証）

#### 高度な検証（合計21テストケース）

##### 5-12. URI Normalization and Strict Matching
- HTTP vs HTTPS スキーム違い検証
- デフォルトポート省略 vs 明示（:443）
- クエリパラメータ追加検証
- フラグメント（#）付きURI検証
- 末尾スラッシュ有無検証
- ホスト名Case違い検証（WWW.EXAMPLE.COM vs www.example.com）
- 非標準ポート違い検証
- 完全一致ポジティブテスト

**RFC 3986 vs OAuth 2.0 厳密モード**:
```
RFC 3986 正規化: https://example.com == https://example.com:443
OAuth 2.0 厳密: https://example.com != https://example.com:443 ✅ (idp-server)
```

##### 13-16. Multiple Registered Redirect URIs
```javascript
it("Should validate redirect_uri when client has multiple registered URIs")
it("Should not allow cross-contamination between registered redirect_uris")
it("Should bind authorization code to specific redirect_uri")
it("Should allow authorization code reuse with same redirect_uri")
```

**検証内容**:
- 複数登録URI（HTTPS + HTTP）の個別検証
- 登録URI間のクロスコンタミネーション防止
- 認可コードの特定redirect_uriへのバインディング
- 同じredirect_uriでのトークン取得成功

**重要な発見**:
```
認可: redirect_uri A でコード取得
トークン: redirect_uri B でリクエスト（A, B両方とも登録済み）
→ 400/401エラー ✅ （登録済みでも異なるURIは拒否）
```

##### 17-19. URL Encoding and Special Characters
- URL-encoded文字の扱い検証
- パストラバーサル攻撃（../）防止
- Localhost variants検証

##### 20-21. Authorization Code Security
```javascript
it("Should reject second token request with same authorization code")
```

**RFC 6749 Section 10.5 検証**:
> "The authorization server MUST ensure that authorization codes
> cannot be used more than once."

**検証フロー**:
1. 認可コード取得
2. 1回目のトークンリクエスト → 成功 ✅
3. 2回目のトークンリクエスト（同じコード） → invalid_grant ✅

**テスト結果**:
```
First token request:           SUCCESS
Second token request (reuse):  REJECTED
Error Code:                    invalid_grant
```

---

#### 攻撃シナリオ

**Email認証パターン**:
1. 被害者メールアドレスAで認証開始
2. チャレンジ送信後、攻撃者メールアドレスBに変更
3. 検証コードBで認証
4. **期待** (Issue #800修正後): メールアドレスBとしてログイン ✅
5. **脆弱** (Issue #800修正前): メールアドレスAとしてログイン ❌

**SMS認証パターン (2FA)**:
1. Email認証 (1st factor) で新規ユーザー作成
2. 被害者の電話番号でSMS Challenge送信
3. 攻撃者の電話番号に切り替え (識別子切り替え攻撃)
4. 攻撃者の検証コードで認証完了
5. **期待**: 攻撃者の電話番号としてログイン ✅
6. **脆弱**: 被害者の電話番号としてログイン ❌

#### テスト内容

##### 1. Email Authentication Identifier Switching
```javascript
it("Should authenticate as the FINAL email, not the INITIAL email (Issue #800 fix verification)")
```

**検証フロー**:
1. 被害者メールで Challenge送信
2. 攻撃者メールに切り替え
3. 攻撃者の検証コードで認証
4. ID Tokenの`email`クレームを確認
5. **PASS**: `email === attackerEmail`
6. **FAIL**: `email === victimEmail` → Issue #800脆弱性存在

**修正内容確認**:
- `EmailAuthenticationResolver.resolveUser()`
- データベース検索を優先（Transaction内ユーザー再利用より先）

##### 2. SMS Authentication Identifier Switching (2FA)
```javascript
it("Should authenticate as the FINAL phone number, not the INITIAL phone number")
```

**検証フロー**:
1. Email認証 (1st factor) 完了
2. 被害者の電話番号でSMS Challenge送信
3. 攻撃者の電話番号に切り替え
4. 攻撃者の検証コードで認証
5. ID Tokenの`phone_number`クレームを確認
6. **PASS**: `phone_number === attackerPhone`
7. **FAIL**: `phone_number === victimPhone` → 脆弱性存在

**重要**: SMS認証は2nd factor (`requires_user=true`) なので、先にEmail認証が必要

##### 3. Multiple Identifier Switches
```javascript
it("Should handle multiple email switches and use the FINAL email")
```

**検証フロー**:
1. email1 → email2 → email3 と複数回切り替え
2. email3の検証コードで認証完了
3. ID Tokenの`email`クレームが email3 と一致することを確認

---

## テスト実行方法

### 個別実行
```bash
cd e2e
npm test -- identifier_switching_attack.test.js
```

### 詳細ログ付き実行
```bash
npm test -- identifier_switching_attack.test.js --verbose
```

---

## テスト結果の読み方

### 期待される結果

Issue #800の修正が正しく動作している場合、すべてのテストが**PASS**します。

#### ✅ 成功パターン (修正後)
```
[Verification] ID Token email claim: attacker@example.com
[Verification] Expected (attacker): attacker@example.com
[Verification] NOT expected (victim): victim@example.com

✅ PASS: Authenticated as ATTACKER (final email)
   → Issue #800 fix is working correctly
   → Database search is prioritized over transaction user
```

#### ❌ 失敗パターン (修正前)
```
[Verification] ID Token email claim: victim@example.com

❌❌❌ CRITICAL FAIL: Authenticated as VICTIM (initial email)!
   → Issue #800 vulnerability exists
   → Identifier switching attack succeeded

CRITICAL VULNERABILITY: User authenticated as initial identifier, not final
```

### ログの見方

各テストは詳細なコンソールログを出力します:

```javascript
[Test] Victim email: victim@example.com
[Test] Attacker email: attacker@example.com

[Step 1] Sending challenge to VICTIM: victim@example.com
[Step 1] Victim challenge: 200

[Step 2] SWITCHING to ATTACKER: attacker@example.com
[Step 2] Attacker challenge: 200

[Step 3] Got verification code for attacker: 123456
[Step 4] Verification: 200

[Verification] ID Token email claim: attacker@example.com ✅
```

---

## 実装パターン

### Issue #800 修正内容

#### 修正前 (脆弱)
```java
// EmailAuthenticationResolver.java
public IdpUser resolveUser(...) {
  // ❌ Transaction内ユーザーを優先
  if (transaction.hasAuthenticatedUser()) {
    return transaction.authenticatedUser(); // 被害者のユーザー
  }

  // データベース検索（後回し）
  return userRepository.findByEmail(email); // 攻撃者のユーザー
}
```

#### 修正後 (正しい)
```java
// EmailAuthenticationResolver.java
public IdpUser resolveUser(...) {
  // ✅ データベース検索を優先
  IdpUser user = userRepository.findByEmail(email); // 攻撃者のユーザー
  if (user != null) {
    return user;
  }

  // Transaction内ユーザー（フォールバック）
  if (transaction.hasAuthenticatedUser()) {
    return transaction.authenticatedUser();
  }

  return null;
}
```

### テストパターン

```javascript
describe("Issue #801 - S1: Authentication Identifier Switching Attack", () => {
  describe("Critical: Email Authentication Identifier Switching", () => {
    it("Should authenticate as the FINAL email", async () => {
      // 1. 攻撃シナリオ準備
      const victimEmail = faker.internet.email();
      const attackerEmail = faker.internet.email();

      // 2. 識別子切り替え実行
      const interaction = async (id) => {
        // Victim challenge
        await postAuthentication({...victimEmail...});

        // Switch to attacker
        await postAuthentication({...attackerEmail...});

        // Verify with attacker's code
        await postAuthentication({verification_code});
      };

      // 3. 認証完了
      const { authorizationResponse } = await requestAuthorizations({
        user: { email: attackerEmail },
        interaction,
      });

      // 4. ID Token検証
      const payload = decodeIdToken(tokenResponse.data.id_token);

      // 5. 最終識別子確認
      if (payload.email === victimEmail) {
        fail("CRITICAL: Authenticated as initial identifier");
      } else if (payload.email === attackerEmail) {
        expect(payload.email).toBe(attackerEmail); // ✅
      }
    });
  });
});
```

---

## 既存テスト (参考)

### multi_tenant_isolation.test.js
マルチテナント分離のテスト

### session_fixation_password_auth.test.js
Session Fixation攻撃のテスト (Issue #736)
- パスワード認証後のSession ID再生成確認

---

## 今後のテスト計画

### Session関連テスト (別途検討)

以下のテストは、Cookie Jar自動管理の制約により、別の実装戦略が必要です:

#### S16: Session検証欠如によるTransaction ID切り替え攻撃
- **課題**: `http/index.js`のCookie Jar自動管理により、明示的なSession切り替えが困難
- **対策案**: `axios`直接使用 (session_fixation_password_auth.test.js パターン)

#### S15: Redis障害時のSession-Transaction紐付け喪失攻撃
- **課題**: Docker操作が他テストに影響
- **対策案**: 統合テストまたは専用テスト環境で実施

#### S12: Transaction ID再利用攻撃
- **課題**: Transaction無効化タイミングの検証
- **対策案**: 単体テストで補完

#### S13: Transaction ID並行利用攻撃
- **課題**: Race Conditionの再現性
- **対策案**: 負荷テストツール使用

---

## 関連情報

### 関連Issue
- [#800](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/800) メアド認証によるアカウント作成・認証の挙動が不安定
- [#801](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/801) 類似脆弱性の体系的確認

### コードベース参照
- `EmailAuthenticationResolver.java` - resolveUser()判定順序
- `SmsAuthenticationResolver.java` - resolveUser()判定順序
- `OAuthFlowEntryService.java` - 認証フローオーケストレーション

### 参考実装
- Keycloak SessionCodeChecks - session_code + client_id + tab_id検証
- OWASP Testing Guide - Authentication Testing

---

## 作成情報

- **作成日**: 2025-10-26
- **ブランチ**: `feature/issue-801-security-audit-e2e-tests`
- **作成者**: Claude Code
- **Issue**: [#801](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/801)
- **焦点**: 認証識別子の切り替え攻撃 (Issue #800修正検証)
