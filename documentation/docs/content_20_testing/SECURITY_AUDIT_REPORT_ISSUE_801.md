# セキュリティ監査レポート - Issue #801

**作成日**: 2025-12-08
**対象**: idp-server 認証基盤全体
**監査範囲**: Issue #800 で発見された「状態の不正な引き継ぎ」脆弱性パターンの体系的確認
**ステータス**: Phase 1 (GA前Critical確認) 進行中

---

## 📋 Executive Summary

### 監査目的

Issue #800で発見された「状態の不正な引き継ぎ」という脆弱性パターンが、認証フロー全体・API設計・セッション管理に存在しないかを体系的に確認し、E2Eテストでセキュリティを担保する。

### 主要な発見

| 重大度 | シナリオ | E2Eテスト | バックエンド実装 | リスク評価 |
|--------|---------|-----------|----------------|-----------|
| **Critical** | S1: 認証識別子の切り替え攻撃 | ✅ **実装済み** | ✅ **修正済み** (Issue #800) | 🟢 **低** |
| **Critical** | S3: テナント境界越え攻撃 | ✅ **実装済み** | ✅ **保護済み** | 🟢 **低** |
| **Critical** | S7: セッション混同攻撃 | ⚠️ **部分的** | ✅ **保護済み** (Session再生成) | 🟡 **中** |
| **Critical** | **S15: Redis障害時Session紐付け喪失** | ❌ **未実装** | ⚠️ **設計判断** (可用性優先) | 🔴 **高** |
| **Critical** | **S16: Session検証欠如** | ❌ **未実装** | ⚠️ **検証不明** | 🔴 **高** |
| **Critical** | S9: Redirect URI切り替え攻撃 | ✅ **実装済み** | ✅ **保護済み** (RFC 6749準拠) | 🟢 **低** |
| **High** | S11: Transaction ID切り替え攻撃 | ❌ **未実装** | 🔍 **要確認** | 🟡 **中** |

### 推奨アクション

**GA前に必須対応**:
1. **S15**: Redis障害時のエラーハンドリング検証 - 可用性とセキュリティのトレードオフ判断
2. **S16**: Session-Transaction バインディング検証の実装確認
3. **S9**: Redirect URI検証ロジックのコードレビュー

**GA後1週間以内**:
4. **S11**: Transaction ID検証ロジックのE2Eテスト実装
5. **S7**: Session Fixation攻撃の完全なE2Eテストカバレッジ

---

## 🔍 詳細分析

### 1. 実装済み・保護済みシナリオ

#### ✅ S1: 認証識別子の切り替え攻撃 (Critical)

**脆弱性パターン**:
```
1. 被害者の識別子で認証開始 (email: victim@example.com)
2. 攻撃者の識別子に切り替え (email: attacker@example.com)
3. 攻撃者の検証コードで認証完了
4. [修正前] 被害者としてログイン ❌
5. [修正後] 攻撃者としてログイン ✅
```

**E2Eテスト**: `e2e/src/tests/security/identifier_switching_attack.test.js`
- Email認証での識別子切り替え ✅
- SMS認証での識別子切り替え (2FA) ✅
- 複数回の識別子切り替え ✅

**バックエンド修正** (Issue #800):
```java
// EmailAuthenticationChallengeInteractor.resolveUser()
// 修正前: transaction.hasUser()を最優先（脆弱）
// 修正後: userQueryRepository.findByEmail()を最優先（安全）
```

**リスク**: 🟢 **低** - 修正済み・E2Eテストで検証済み

---

#### ✅ S3: テナント境界越え攻撃 (Critical)

**脆弱性パターン**:
```
1. Tenant AでアクセストークンA取得
2. Tenant BのエンドポイントにトークンAでアクセス
3. [脆弱] Tenant Bのリソースにアクセス可能 ❌
4. [保護] 401 Unauthorized ✅
```

**E2Eテスト**: `e2e/src/tests/security/multi_tenant_isolation.test.js`
- Userinfo Endpointのテナント分離 ✅
- Token Introspectionのテナント分離 ✅
- Resource Owner Endpointのテナント分離 ✅

**バックエンド保護**: Row Level Security (RLS) + API層でのテナント検証

**リスク**: 🟢 **低** - 多層防御で保護済み

---

#### ⚠️ S7: セッション混同攻撃 (Critical)

**脆弱性パターン**:
```
1. 攻撃者がセッションID取得 (未認証)
2. 被害者がそのセッションIDで認証
3. [脆弱] セッションIDが再生成されない ❌
4. [保護] 認証後にセッションID再生成 ✅
```

**E2Eテスト**: `e2e/src/tests/security/session_fixation_password_auth.test.js`
- パスワード認証後のSession ID再生成確認 ✅
- **不足**: Email/SMS/WebAuthn認証でのSession ID再生成確認 ❌

**バックエンド保護**: Spring Securityのセッション再生成機能

**リスク**: 🟡 **中** - パスワード認証は保護済みだが、他の認証方式は未検証

**推奨**: Email/SMS/WebAuthn認証でのSession Fixationテスト追加（GA後1週間以内）

---

### 2. Critical未実装シナリオ（GA前対応必須）

#### 🔴 S15: Redis障害時のSession-Transaction紐付け喪失攻撃 (Critical)

**脆弱性パターン**:
```
前提: Redis障害中

1. 被害者AがTransaction ID-A で認証開始
2. 攻撃者がID-Aを盗聴/推測
3. 攻撃者が自分のSessionでID-Aを使用
4. SafeRedisSessionRepository.findById()
   → Redis例外キャッチ
   → null返却（エラー無視）
5. OAuthSessionService.findOrInitialize()
   → 新規セッション初期化
   → Session-Transaction紐付けチェック不可
6. [脆弱] 攻撃者が被害者Aとして認証完了 ❌
```

**E2Eテスト**: ❌ **未実装**
- 課題: Docker操作が他テストに影響
- 対策案: 統合テストまたは専用テスト環境で実施

**バックエンド実装確認**:

**app/src/main/java/org/idp/server/SafeRedisSessionRepository.java:108-117**:
```java
@Override
public RedisSession findById(String id) {
  try {
    return super.findById(id);
  } catch (Exception e) {
    logger.error("Failed to load session: {}", e.getMessage());
    return null;  // ⚠️ エラー無視 - 意図的な設計判断
  }
}
```

**Javadoc記載**:
> Use Cases:
> - High-availability identity providers where **session loss is acceptable** during Redis downtime.
> - **Graceful degradation strategy** for distributed authentication systems.

**libs/idp-server-springboot-adapter/.../OAuthSessionService.java:41-48**:
```java
public OAuthSession findOrInitialize(OAuthSessionKey oAuthSessionKey) {
  OAuthSession oAuthSession = httpSessionRepository.find(oAuthSessionKey);
  if (oAuthSession.exists()) {
    return oAuthSession;
  }

  return OAuthSession.init(oAuthSessionKey);  // ⚠️ Redis障害時に新規セッション初期化
}
```

**分析**:
- これは**意図的な設計判断**（可用性優先）
- しかし、セキュリティリスクが存在
- **トレードオフ**: 可用性 vs セキュリティ

**リスク**: 🔴 **高** - Redis障害時に認証バイパスの可能性

**推奨アクション** (GA前):

**オプション1: セキュリティ優先** (Keycloakパターン)
```java
@Override
public RedisSession findById(String id) {
  try {
    return super.findById(id);
  } catch (Exception e) {
    logger.error("Failed to load session (Redis disconnected): {}", e.getMessage());

    // セキュリティクリティカルな操作は停止
    throw new SessionStorageUnavailableException(
      "Session storage is temporarily unavailable. Please try again later.", e);
  }
}
```
- **メリット**: セキュリティ確保
- **デメリット**: Redis障害時にサービス停止（503 Service Unavailable）

**オプション2: 可用性優先** (現在の実装を維持)
- **メリット**: Redis障害時もサービス継続
- **デメリット**: セキュリティリスク受容
- **必須**: 明確な設計判断のドキュメント化 + リスク受容

**オプション3: ハイブリッド**
```java
// 認証関連操作のみエラーを返す
if (isAuthenticationRelatedOperation(id)) {
  throw new SessionStorageUnavailableException(...);
}
return null;  // その他の操作は継続
```

**判断基準**:
- **GA前に必ず判断**: セキュリティ vs 可用性のトレードオフ
- **推奨**: オプション1（セキュリティ優先）または オプション3（ハイブリッド）

---

#### ✅ S9: Redirect URI切り替え攻撃 (Critical)

**脆弱性パターン**:
```
1. 正規のredirect_uriで認可リクエスト → 認可コード取得
2. 攻撃者のredirect_uriでトークンリクエスト
3. [脆弱] トークン発行 → 認可コード漏洩 ❌
4. [保護] invalid_request エラー ✅
```

**E2Eテスト**: `e2e/src/tests/security/redirect_uri_switching_attack.test.js`

**基本検証** (5テスト):
- Token endpoint redirect_uri mismatch検証 ✅
- Redirect URI省略攻撃検証 ✅
- 未登録redirect_uri検証 ✅
- Substring matching攻撃検証 ✅
- Path case-sensitive検証 ✅

**URI正規化と厳密一致** (8テスト):
- HTTP vs HTTPS スキーム違い ✅
- デフォルトポート省略 vs 明示 (`:443`) ✅
- クエリパラメータ追加 ✅
- フラグメント (`#`) 付きURI ✅
- 末尾スラッシュ有無 ✅
- ホスト名Case違い (`WWW` vs `www`) ✅
- 非標準ポート違い ✅
- 完全一致ポジティブテスト ✅

**複数登録URI** (4テスト):
- 複数URI個別検証 ✅
- 登録URI間クロスコンタミネーション防止 ✅
- 認可コードの特定URI紐付け ✅
- 同一URIでの正常トークン取得 ✅

**特殊文字・エンコーディング** (3テスト):
- URL-encoded文字 ✅
- パストラバーサル攻撃 (`../`) ✅
- Localhost variants ✅

**認可コードセキュリティ** (1テスト):
- 認可コード再利用防止 ✅

**テスト結果**: ✅ **全21テストがパス**
```
Test Suites: 1 passed
Tests:       21 passed
Time:        3.228 s
```

**バックエンド保護**: RFC 6749 Section 4.1.3準拠

**検証内容**: **21種類の包括的テスト**
```
基本検証 (5テスト):
- redirect_uri不一致 → invalid_request
- redirect_uri省略 → invalid_request
- 未登録redirect_uri → invalid_request
- Substring攻撃 → invalid_request（完全一致）
- Path case-sensitive → invalid_request

URI正規化と厳密一致 (8テスト):
- HTTP vs HTTPS → invalid_request（スキーム違い検出）
- :443明示 vs 省略 → invalid_request（厳密モード）
- クエリパラメータ → invalid_request（完全一致）
- フラグメント → invalid_request
- 末尾スラッシュ → invalid_request
- ホスト名Case → invalid_request（厳密モード）
- 非標準ポート → invalid_request
- 完全一致 → 200 OK ✅

複数登録URI (4テスト):
- 複数URI個別検証 → 200 OK ✅
- URI間クロスコンタミネーション → 400/401（防止）
- 認可コード特定URI紐付け → 400/401（強制）
- 同一URIトークン取得 → 200 OK ✅

特殊文字・エンコーディング (3テスト):
- URL-encoded → invalid_request
- パストラバーサル → invalid_request
- Localhost variants → 実装依存

認可コードセキュリティ (1テスト):
- 認可コード再利用 → invalid_grant（防止）✅
```

**RFC 6749 準拠確認**:
- ✅ Section 4.1.3: Token endpointでのredirect_uri一致確認
- ✅ Section 3.1.2.3: Redirect URI登録必須
- ✅ 完全一致検証（部分一致禁止）
- ✅ Case-sensitive検証

**リスク**: 🟢 **低** - RFC 6749準拠で実装済み・E2Eテストで検証済み

---

#### 🔴 S16: Session検証欠如によるTransaction ID切り替え攻撃 (Critical)

**脆弱性パターン**:
```
1. 被害者AがTransaction ID-A で認証開始
2. 攻撃者がID-Aを入手（盗聴/推測）
3. 攻撃者が自分のSessionでID-Aを使用
   POST /{ID-A}/email-authentication
   Cookie: 攻撃者のSession
4. OAuthFlowEntryService.interact()
   → Session取得（攻撃者のSession）
   → Transaction取得（ID-A）
   → Session-Transaction紐付けチェックなし？ ⚠️
5. [脆弱] 攻撃者が被害者Aとして認証完了 ❌
```

**E2Eテスト**: ❌ **未実装**
- 課題: Cookie Jar自動管理により、明示的なSession切り替えが困難
- 対策案: axios直接使用（session_fixation_password_auth.test.jsパターン）

**バックエンド実装確認**:

**OAuthFlowEntryService.java:168-221**:
```java
public AuthenticationInteractionRequestResult interact(
    TenantIdentifier tenantIdentifier,
    AuthorizationRequestIdentifier authorizationRequestIdentifier,
    AuthenticationInteractionType type,
    AuthenticationInteractionRequest request,
    RequestAttributes requestAttributes) {

  Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

  OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
  AuthorizationRequest authorizationRequest =
      oAuthProtocol.get(tenant, authorizationRequestIdentifier);  // Line 178-179

  // ⚠️ 要確認: authorizationRequest.sessionKey() と現在のSessionIDの比較が見えない

  AuthenticationInteractor authenticationInteractor = authenticationInteractors.get(type);
  AuthorizationIdentifier authorizationIdentifier =
      new AuthorizationIdentifier(authorizationRequestIdentifier.value());
  AuthenticationTransaction authenticationTransaction =
      authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);  // Line 184-185

  // ⚠️ 要確認: Transaction-Session紐付け検証がない？

  AuthenticationInteractionRequestResult result =
      authenticationInteractor.interact(
          tenant,
          authenticationTransaction,
          type,
          request,
          requestAttributes,
          userQueryRepository);

  // ... 以下、認証処理
}
```

**要調査**:
1. `authorizationRequest.sessionKey()` がどのように生成されるか
2. `requestAttributes` に現在のSessionIDが含まれているか
3. Session-Transactionバインディングがどこで検証されるか

**Keycloakパターン** (参考):
```java
// Keycloakは session_code + client_id + tab_id の3つ組で厳密検証
SessionCodeChecks checks = new SessionCodeChecks(
  realm, sessionManager, request,
  session_code, client_id, tab_id);

if (!checks.verify()) {
  return error("Invalid Code");
}
```

**リスク**: 🔴 **高** - Session検証が不明確

**推奨アクション** (GA前):
1. **コードレビュー**: Session-Transaction バインディング検証ロジックの確認
2. **E2Eテスト実装**: Transaction ID切り替え攻撃シナリオ
3. **必要に応じて修正**: Session検証の追加

**E2Eテスト実装案**:
```javascript
// e2e/src/tests/security/transaction_id_switching_attack.test.js
it("should block cross-session transaction ID usage", async () => {
  // 1. 被害者の認証開始 → Transaction ID-A取得
  const victimAuthResponse = await axios.get(authorizationEndpoint, {
    params: { client_id, ... },
    maxRedirects: 0,
    validateStatus: () => true,
  });
  const victimSessionId = getSessionId(victimAuthResponse);
  const victimAuthId = extractAuthId(victimAuthResponse.headers.location);

  // 2. 攻撃者が別Sessionを取得
  const attackerAuthResponse = await axios.get(authorizationEndpoint, {
    params: { client_id, ... },
    maxRedirects: 0,
    validateStatus: () => true,
  });
  const attackerSessionId = getSessionId(attackerAuthResponse);

  // 3. 攻撃者が被害者のTransaction IDを使用
  const attackResponse = await axios.post(
    `/${victimAuthId}/email-authentication`,
    { verification_code: "123456" },
    {
      headers: { Cookie: `SESSION=${attackerSessionId}` },  // 攻撃者のSession
      validateStatus: () => true,
    }
  );

  // 4. 期待: 403 Forbidden（Session不一致）
  // 5. 危険: 200 OK（攻撃成功）
  expect(attackResponse.status).toBe(403);
  console.log("✅ Session-Transaction binding verified");
});
```

---

### 3. その他の未実装Criticalシナリオ

#### 🟡 S9: Redirect URI切り替え攻撃 (Critical)

**脆弱性パターン**:
```
1. 正規のredirect_uriで認証開始
2. 途中で攻撃者のredirect_uriに切り替え
3. [脆弱] 攻撃者のredirect_uriにcode送信 ❌
4. [保護] redirect_uri検証でエラー ✅
```

**E2Eテスト**: ❌ **未実装**

**バックエンド実装**: 🔍 **要確認**
- OAuth 2.0仕様では、redirect_uriは厳密に検証される必要がある
- トークンエンドポイントで、認可コード取得時のredirect_uriと一致確認

**推奨アクション** (GA前):
1. **コードレビュー**: redirect_uri検証ロジックの確認
2. **RFC 6749準拠確認**: Section 4.1.3 (Authorization Code Grant)

---

#### 🟡 S11: Authentication Transaction ID 切り替え攻撃 (Critical/High)

**脆弱性パターン**:
```
1. 被害者AがTransaction ID-A で認証開始
2. 攻撃者BがTransaction ID-B で認証開始
3. 攻撃者がID-Aを盗聴/推測
4. 攻撃者がID-AでInteraction実行
5. [脆弱] 被害者Aとしてログイン ❌
6. [保護] 403 Forbidden（Session不一致） ✅
```

**E2Eテスト**: ❌ **未実装**（S16と重複）

**リスク**: 🟡 **中** - S16で対処可能

---

## 📊 セキュリティテストカバレッジ

### E2Eテスト実装状況

| シナリオ | ファイル | テスト数 | カバレッジ |
|---------|---------|---------|-----------|
| S1: 識別子切り替え | `identifier_switching_attack.test.js` | 3 | 100% |
| S3: テナント境界越え | `multi_tenant_isolation.test.js` | 3 | 100% |
| S7: セッション混同 | `session_fixation_password_auth.test.js` | 1 | 33% (パスワードのみ) |
| S9: Redirect URI切り替え | `redirect_uri_switching_attack.test.js` | **21** | **100%+** (包括的) |
| S15: Redis障害 | - | 0 | 0% |
| S16: Session検証欠如 | - | 0 | 0% |
| S11: Transaction ID | - | 0 | 0% |

**合計**: **28件のE2Eテスト実装済み**（S9: 5件 → 21件に拡張）、3件未実装

**S9の包括的カバレッジ**:
- 基本検証: 5テスト
- URI正規化: 8テスト
- 複数URI: 4テスト
- 特殊文字: 3テスト
- コードセキュリティ: 1テスト

---

## 🎯 GA前アクションアイテム

### Phase 1: Critical確認（GA前必須）

#### 1. S15: Redis障害時エラーハンドリング - 設計判断

**タスク**: セキュリティ vs 可用性のトレードオフ判断

**選択肢**:
- [ ] **オプション1**: セキュリティ優先（Keycloakパターン） - Redis障害時は503返却
- [ ] **オプション2**: 可用性優先（現状維持） - リスク受容を明確にドキュメント化
- [ ] **オプション3**: ハイブリッド - 認証操作のみエラー返却

**担当**: アーキテクト + セキュリティチーム
**期限**: GA前
**成果物**: 設計判断書 + 実装（必要に応じて）

---

#### 2. S16: Session-Transaction バインディング検証 - コードレビュー

**タスク**: バックエンド実装の詳細確認

**調査項目**:
- [ ] `AuthorizationRequest.sessionKey()` の生成ロジック
- [ ] `RequestAttributes` に含まれる現在のSessionID
- [ ] Session-Transaction紐付け検証の有無
- [ ] Transaction ID生成のランダム性（UUID v4確認）

**担当**: 開発チーム
**期限**: GA前
**成果物**: コードレビューレポート + E2Eテスト実装（必要に応じて）

---

#### 3. S9: Redirect URI検証 - コードレビュー

**タスク**: OAuth 2.0仕様準拠確認

**調査項目**:
- [ ] 認可エンドポイントでのredirect_uri検証
- [ ] トークンエンドポイントでのredirect_uri一致確認
- [ ] RFC 6749 Section 4.1.3準拠確認

**担当**: 開発チーム
**期限**: GA前
**成果物**: コードレビューレポート

---

### Phase 2: E2Eテスト実装（GA後1週間以内）

#### 4. S16/S11: Transaction ID切り替え攻撃テスト実装

**ファイル**: `e2e/src/tests/security/transaction_id_switching_attack.test.js`

**テストケース**:
1. 他人のTransaction IDでInteraction実行 → 403 Forbidden
2. Transaction ID再利用（認証完了後） → 404/410
3. 並行利用（Race Condition） → 409 Conflict

**担当**: QA + 開発チーム
**期限**: GA後1週間
**成果物**: E2Eテスト実装 + 実行レポート

---

#### 5. S7: Session Fixation完全テスト実装

**ファイル**: `e2e/src/tests/security/session_fixation_all_auth_methods.test.js`

**テストケース**:
1. Email認証後のSession ID再生成確認
2. SMS認証後のSession ID再生成確認
3. WebAuthn認証後のSession ID再生成確認

**担当**: QA + 開発チーム
**期限**: GA後1週間
**成果物**: E2Eテスト実装 + 実行レポート

---

## 🔗 参考資料

### 関連Issue
- [#800](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/800) メアド認証によるアカウント作成・認証の挙動が不安定
- [#801](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/801) 類似脆弱性の体系的確認
- [#736](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/736) Session Fixation
- [#734](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/734) マルチテナント分離

### コードベース参照
- `app/src/main/java/org/idp/server/SafeRedisSessionRepository.java`
- `libs/idp-server-use-cases/.../OAuthFlowEntryService.java`
- `libs/idp-server-springboot-adapter/.../OAuthSessionService.java`

### 外部参考
- [Keycloak SessionCodeChecks](https://github.com/keycloak/keycloak/blob/main/services/src/main/java/org/keycloak/services/resources/SessionCodeChecks.java)
- [OWASP Testing Guide - Authentication Testing](https://owasp.org/www-project-web-security-testing-guide/latest/4-Web_Application_Security_Testing/04-Authentication_Testing/)
- [RFC 6749 - OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)

---

## 📝 作成情報

- **作成者**: Claude Code
- **作成日**: 2025-12-08
- **監査対象**: idp-server v0.9.0+
- **監査範囲**: Issue #801 Critical脆弱性シナリオ
