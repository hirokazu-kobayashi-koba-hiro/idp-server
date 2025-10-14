# Application Plane Track（認証フロー実装者向け）

## 🎯 このトラックの目標

**認証・認可フロー（Application Plane）の実装**ができるようになる。

- Authorization Flow実装
- Token Endpoint実装（Grant Type追加）
- 認証インタラクター実装（新しい認証方式追加）
- Federation実装（外部IdP連携）

**所要期間**: 2-4週間

**前提**: [初級ラーニングパス](./01-beginner.md)完了

---

## 📅 学習スケジュール

### Week 1: OAuth/OIDCフロー理解

#### Day 1-3: Authorization Flow実装
- [ ] **所要時間**: 12時間
- [ ] [Authorization Flow実装ガイド](../03-application-plane/02-authorization-flow.md)を読む
- [ ] Authorization Code Flowの全体フローを理解

**実践課題**:
```bash
# 実際にAuthorization Code Flowを実行して理解する

# 1. Authorization Request
curl "http://localhost:8080/${TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=test-client&\
redirect_uri=https://app.example.com/callback&\
scope=openid+profile+email&\
state=random-state&\
nonce=random-nonce"

# 2. 認証（省略）

# 3. Token Request
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Authorization: Basic $(echo -n 'client-id:client-secret' | base64)" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}"

# 4. UserInfo取得
curl "http://localhost:8080/${TENANT_ID}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

**チェックポイント**:
- [ ] Authorization Code Flowの8ステップを説明できる
- [ ] Authorization Request → Authorization Code → Access Tokenの流れを理解
- [ ] PKCE（Proof Key for Code Exchange）の役割を説明できる

---

#### Day 4-5: Token Endpoint実装
- [ ] **所要時間**: 8時間
- [ ] [Token Endpoint実装ガイド](../03-application-plane/03-token-endpoint.md)を読む
- [ ] Grant Type別の処理を理解

**実践課題**:
```java
// Grant Type別の処理フローを図解する
1. Authorization Code Grant（最も重要）
   - Authorization Code検証
   - PKCE検証
   - Access/Refresh/ID Token生成

2. Client Credentials Grant
   - クライアント認証のみ
   - ユーザーコンテキストなし

3. Refresh Token Grant
   - 既存トークン検証
   - 新しいAccess Token発行
```

**チェックポイント**:
- [ ] 4種類のGrant Typeの違いを説明できる
- [ ] クライアント認証7方式を説明できる
- [ ] Token Introspection/Revocationの用途を理解

---

### Week 2: 認証インタラクター実装

#### Day 6-8: 認証インタラクター理解
- [ ] **所要時間**: 10時間
- [ ] [Authentication実装ガイド](../03-application-plane/04-authentication.md)を読む
- [ ] [AuthenticationInteractor実装ガイド](../04-implementation-guides/impl-06-authentication-interactor.md)を読む

**実践課題**:
```java
// 既存の認証インタラクターを読んで理解する
1. PasswordAuthenticationInteractor
   - パスワード検証
   - ハッシュ比較

2. SmsAuthenticationInteractor
   - OTP生成
   - SMS送信
   - OTP検証

3. FidoUafAuthenticationInteractor
   - Challenge生成
   - 署名検証
```

**チェックポイント**:
- [ ] AuthenticationInteractorインターフェースを説明できる
- [ ] `type()`メソッドの役割を理解
- [ ] Plugin機構での動的選択を理解

---

#### Day 9-10: 新しい認証方式追加
- [ ] **所要時間**: 8時間
- [ ] 新しい認証インタラクターを実装

**実践課題**:
```java
// Passkey認証を実装する
public class PasskeyAuthenticationInteractor implements AuthenticationInteractor {

    @Override
    public String type() {
        return "passkey";
    }

    @Override
    public AuthenticationResult authenticate(
        AuthenticationTransaction transaction,
        AuthenticationRequest request,
        AuthenticationConfiguration configuration) {

        // 1. Passkey Challenge検証
        String challenge = transaction.optValueAsString("challenge", "");
        String response = request.optValueAsString("response", "");

        // 2. WebAuthn検証
        boolean isValid = verifyPasskeyResponse(challenge, response);

        if (!isValid) {
            return AuthenticationResult.failed("Invalid passkey response");
        }

        // 3. ユーザー特定
        User user = userRepository.findByPasskeyCredential(tenant, credentialId);

        return AuthenticationResult.success(user);
    }
}
```

**チェックリスト**:
- [ ] AuthenticationInteractor実装
- [ ] `type()`メソッドで一意な識別子返却
- [ ] Plugin自動ロード確認
- [ ] E2Eテスト作成

**チェックポイント**:
- [ ] 新しい認証方式を追加できる
- [ ] Pluginパターンを実装できる

---

### Week 3: Grant Type実装

#### Day 11-13: 新しいGrant Type追加
- [ ] **所要時間**: 12時間
- [ ] カスタムGrant Typeを実装

**実践課題**:
```java
// Token Exchange Grant (RFC 8693) を実装する
public class TokenExchangeGrantService implements OAuthTokenCreationService {

    @Override
    public GrantType supportedGrantType() {
        return new GrantType("urn:ietf:params:oauth:grant-type:token-exchange");
    }

    @Override
    public OAuthToken create(
        TokenRequestContext context,
        ClientCredentials clientCredentials) {

        // 1. Validator
        TokenExchangeGrantValidator validator = new TokenExchangeGrantValidator(context);
        validator.validate();

        // 2. Subject Token検証
        String subjectToken = context.parameters().getFirst("subject_token");
        OAuthToken originalToken = oAuthTokenQueryRepository.find(
            context.tenant(),
            new AccessTokenEntity(subjectToken)
        );

        // 3. Verifier
        TokenExchangeGrantVerifier verifier = new TokenExchangeGrantVerifier(originalToken);
        verifier.verify();

        // 4. 新しいAccess Token生成
        AccessToken newAccessToken = accessTokenCreator.exchange(originalToken, context);

        // 5. OAuthToken保存
        OAuthToken oAuthToken = new OAuthTokenBuilder(...)
            .add(newAccessToken)
            .build();

        oAuthTokenCommandRepository.register(context.tenant(), oAuthToken);
        return oAuthToken;
    }
}
```

**チェックリスト**:
- [ ] `OAuthTokenCreationService`実装
- [ ] `supportedGrantType()`で一意なGrant Type返却
- [ ] Plugin自動ロード確認
- [ ] E2Eテスト作成

**チェックポイント**:
- [ ] 新しいGrant Typeを追加できる
- [ ] RFC仕様を実装に落とし込める

---

### Week 4: Federation実装

#### Day 14-17: 外部IdP連携実装
- [ ] **所要時間**: 16時間
- [ ] [Federation実装ガイド](../03-application-plane/08-federation.md)を読む
- [ ] [Federation Provider実装ガイド](../04-implementation-guides/impl-08-federation-provider.md)を読む
- [ ] 新しいSsoProvider追加

**実践課題**:
```java
// Azure AD連携を実装する
public class AzureAdSsoExecutor implements SsoExecutor {

    @Override
    public SsoProvider supportedProvider() {
        return SsoProvider.AZURE_AD;
    }

    @Override
    public SsoRedirectResponse redirect(
        FederationConfiguration config,
        AuthenticationTransaction transaction) {

        // 1. SsoSession作成
        SsoSession session = createSsoSession(transaction, config);

        // 2. Azure ADへのリダイレクトURL生成
        String authorizationUrl = String.format(
            "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize?" +
            "client_id=%s&" +
            "redirect_uri=%s&" +
            "state=%s&" +
            "nonce=%s&" +
            "response_type=code&" +
            "scope=openid+profile+email",
            config.getTenantId(),
            config.getClientId(),
            config.getCallbackUrl(),
            session.state(),
            session.nonce()
        );

        return new SsoRedirectResponse(authorizationUrl, session);
    }

    @Override
    public SsoCallbackResult callback(
        SsoSession session,
        Map<String, String> params,
        FederationConfiguration config) {

        // 1. state検証
        // 2. Azure ADへToken Request
        // 3. ID Token検証
        // 4. UserInfo取得
        // 5. User作成/更新

        return new SsoCallbackResult(user, idToken);
    }
}
```

**チェックリスト**:
- [ ] SsoExecutor実装
- [ ] state/nonce検証実装
- [ ] 外部IdPへのToken Request実装
- [ ] ID Token検証実装
- [ ] 属性マッピング実装
- [ ] E2Eテスト作成

**チェックポイント**:
- [ ] 新しい外部IdP連携を追加できる
- [ ] OpenID Connect Discovery対応を実装できる
- [ ] セキュリティ（state/nonce）を正しく実装できる

---

#### Day 18-20: CIBA実装理解
- [ ] **所要時間**: 12時間
- [ ] [CIBA Flow実装ガイド](../03-application-plane/06-ciba-flow.md)を読む
- [ ] バックチャネル認証の仕組みを理解

**実践課題**:
```bash
# CIBA Flowを実行して理解する

# 1. Backchannel Authentication Request
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/backchannel/authentications" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'client-id:client-secret' | base64)" \
  -d "login_hint=user@example.com&binding_message=Code:1234"

# レスポンス: { "auth_req_id": "...", "expires_in": 300 }

# 2. ユーザーが認証デバイスで承認（別フロー）

# 3. Token Request（ポーリングまたはPush通知後）
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Authorization: Basic $(echo -n 'client-id:client-secret' | base64)" \
  -d "grant_type=urn:openid:params:grant-type:ciba&auth_req_id=${AUTH_REQ_ID}"
```

**チェックポイント**:
- [ ] CIBAの3つのモード（poll/ping/push）を説明できる
- [ ] 通常フローとの違いを説明できる

---

## 📚 必読ドキュメント

| 優先度 | ドキュメント | 所要時間 |
|-------|------------|---------|
| 🔴 必須 | [Application Plane概要](../03-application-plane/01-overview.md) | 10分 |
| 🔴 必須 | [Authorization Flow実装](../03-application-plane/02-authorization-flow.md) | 45分 |
| 🔴 必須 | [Token Endpoint実装](../03-application-plane/03-token-endpoint.md) | 30分 |
| 🔴 必須 | [Authentication実装](../03-application-plane/04-authentication.md) | 30分 |
| 🔴 必須 | [Federation実装](../03-application-plane/08-federation.md) | 30分 |
| 🟡 推奨 | [AuthenticationInteractor実装ガイド](../04-implementation-guides/impl-06-authentication-interactor.md) | 30分 |
| 🟡 推奨 | [AI開発者向け: Core詳細](../../content_10_ai_developer/ai-11-core.md) | 90分 |

---

## ✅ 完了判定基準

以下をすべて達成したらApplication Plane Trackクリア：

### 知識面
- [ ] Authorization Code Flowの8ステップを説明できる
- [ ] 4種類のGrant Typeの違いを説明できる
- [ ] クライアント認証7方式を説明できる
- [ ] PKCE（Proof Key for Code Exchange）の仕組みを説明できる
- [ ] state/nonceの役割を説明できる

### 実践面
- [ ] 新しいGrant Typeを実装・マージした
- [ ] 新しい認証インタラクターを実装・マージした
- [ ] 外部IdP連携（SsoExecutor）を実装・マージした
- [ ] E2Eテストを作成し、全件パスした
- [ ] レビューコメントが10件以下

### OAuth/OIDC仕様理解
- [ ] RFC 6749（OAuth 2.0）の主要セクションを理解
- [ ] OpenID Connect Core 1.0の主要フローを理解
- [ ] JWT（RFC 7519）の構造を理解

---

## 🚀 次のステップ

Application Plane Track完了後の選択肢：

### Control Planeも学ぶ
管理API実装も習得したい場合：
- [Control Plane Track](./02-control-plane-track.md)

### Full Stack開発者へ
両方を統合した高度な実装を学ぶ：
- [Full Stack Track](./04-full-stack-track.md)

### 専門性を深める
Application Plane専門家として：
- 新しいOAuth拡張仕様対応（RAR、DPoP等）
- セキュリティ強化（FAPI対応）
- 新しい認証方式設計

---

## 💡 Application Plane実装のヒント

### よくあるミス

#### 1. Authorization Code再利用
```bash
# ❌ 間違い: Authorization Codeは1回しか使えない
curl -X POST ".../v1/tokens" -d "grant_type=authorization_code&code=${CODE}..."
# → 成功

curl -X POST ".../v1/tokens" -d "grant_type=authorization_code&code=${CODE}..."
# → invalid_grant エラー（Authorization Codeは使用後即削除）
```

**理由**: セキュリティ（リプレイ攻撃防止）

#### 2. redirect_uri不一致
```bash
# ❌ 間違い: Authorization RequestとToken Requestでredirect_uriが異なる

# Authorization Request
redirect_uri=https://app.example.com/callback

# Token Request
redirect_uri=https://app.example.com/callback/  # 末尾スラッシュ → エラー
```

**理由**: RFC 6749で完全一致が必須

#### 3. PKCE未使用（Public Client）
```bash
# ❌ 危険: SPAやMobileアプリでclient_secret使用
client_secret=xxx  # 漏洩リスク

# ✅ 安全: PKCE使用（client_secret不要）
code_verifier=random-string
code_challenge=SHA256(code_verifier)
code_challenge_method=S256
```

**理由**: Public ClientではPKCE必須

---

### Handler-Service-Repositoryパターン

Application Planeでも同じパターンを使用：

```
Handler (プロトコル処理)
  ├─ OAuthAuthorizeHandler
  ├─ TokenRequestHandler
  └─ UserinfoHandler
    ↓
Service (ビジネスロジック)
  ├─ AuthorizationCodeGrantService
  ├─ ClientCredentialsGrantService
  └─ RefreshTokenGrantService
    ↓
Repository (データアクセス)
  ├─ AuthorizationCodeGrantRepository
  ├─ OAuthTokenQueryRepository
  └─ UserQueryRepository
```

---

### Delegateパターン

Core層からUseCase層へのコールバック：

```java
// Core層
public interface UserinfoDelegate {
    User findUser(Tenant tenant, Subject subject);
}

// UseCase層が実装
public class UserinfoEntryService implements UserinfoApi, UserinfoDelegate {
    @Override
    public User findUser(Tenant tenant, Subject subject) {
        return userQueryRepository.get(tenant, new UserIdentifier(subject.value()));
    }
}
```

**理由**: Core層はRepositoryに直接依存しない（Hexagonal Architecture原則）

---

### セキュリティ考慮事項

#### 1. Token検証の順序
```java
// ✅ 正しい順序
1. JWT署名検証
2. 有効期限チェック（exp）
3. 失効チェック（Revocation）
4. Audience検証（aud）
5. Issuer検証（iss）
```

#### 2. state/nonce検証
```java
// ✅ 必須
// Authorization Request時に生成
String state = generateRandomState();
String nonce = generateRandomNonce();

// Callback時に検証
if (!receivedState.equals(session.state())) {
    throw new InvalidStateException();
}
```

**理由**: CSRF攻撃・リプレイ攻撃防止

---

## 🔗 関連リソース

- [AI開発者向け: Core - OAuth](../../content_10_ai_developer/ai-11-core.md#oauth---認可ドメイン)
- [AI開発者向け: Core - Token](../../content_10_ai_developer/ai-11-core.md#token---トークンドメイン)
- [AI開発者向け: Core - Authentication](../../content_10_ai_developer/ai-11-core.md#authentication---認証ドメイン)
- [AI開発者向け: Extensions - CIBA](../../content_10_ai_developer/ai-14-extensions.md#ciba-extension)
- [開発者ガイドTOC](../DEVELOPER_GUIDE_TOC.md)

---

**最終更新**: 2025-10-13
**対象**: 認証フロー実装者（2-4週間）
**習得スキル**: Authorization Flow、Token Endpoint、認証インタラクター、Grant Type、Federation
