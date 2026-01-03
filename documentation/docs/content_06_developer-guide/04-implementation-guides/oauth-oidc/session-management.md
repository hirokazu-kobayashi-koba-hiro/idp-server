# OIDC Session Management

## 📍 このドキュメントの位置づけ

**対象読者**: OIDC Session Managementの実装詳細を理解したい開発者

**このドキュメントで学べること**:
- OIDC Session Managementの仕組み
- OPSession / ClientSession の設計パターン
- IDP_IDENTITY / IDP_SESSION Cookieの役割
- SSO（シングルサインオン）の実装
- RP-Initiated Logout / Back-Channel Logout の実装

**前提知識**:
- OAuth 2.0 / OpenID Connect の基礎知識
- 認可コードフローの理解

---

## 🏗️ セッション管理アーキテクチャ

idp-serverのセッション管理は、Keycloakのアーキテクチャを参考に設計されています。

### セッションの種類

```
┌─────────────────────────────────────────────────────────────────┐
│                        Browser Session                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    OPSession                             │   │
│  │  - ブラウザとOPの間のセッション（SSO用）                    │   │
│  │  - sub, authTime, acr, amr を保持                        │   │
│  │  - 複数のClientSessionを持つ                             │   │
│  └─────────────────────────────────────────────────────────┘   │
│           │                    │                    │           │
│           ▼                    ▼                    ▼           │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐     │
│  │ClientSession│      │ClientSession│      │ClientSession│     │
│  │  Client A   │      │  Client B   │      │  Client C   │     │
│  │  sid: xxx   │      │  sid: yyy   │      │  sid: zzz   │     │
│  └─────────────┘      └─────────────┘      └─────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### セッションとCookieの関係

| Cookie名 | 内容 | HttpOnly | 目的 |
|----------|------|----------|------|
| `IDP_IDENTITY` | opSessionId | Yes | SSO識別用（サーバー側で使用） |
| `IDP_SESSION` | SHA256(opSessionId) | No | Session Management iframe用 |

**参考実装**: [SessionCookieService.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/session/SessionCookieService.java)

### Cookieのパススコープ（テナント分離）

Keycloakと同様に、Cookieのパスでテナント（Realm）を分離できます。

```
Browser Cookie Storage:
├── /tenant-a/
│   ├── IDP_IDENTITY = "session-id-for-tenant-a"
│   └── IDP_SESSION = "hash-a..."
│
└── /tenant-b/
    ├── IDP_IDENTITY = "session-id-for-tenant-b"
    └── IDP_SESSION = "hash-b..."
```

これにより、同一ブラウザで複数テナントに独立してログインできます。

---

## 📋 コアクラス

### OPSession

**ブラウザとOP間のセッション**を表すクラスです。ユーザーがログインすると作成され、ログアウトまで維持されます。

```java
public class OPSession {
  private final OPSessionIdentifier id;      // セッションID（UUID）
  private final String sub;                   // ユーザー識別子
  private final Instant authTime;             // 認証時刻
  private final String acr;                   // 認証コンテキストクラス
  private final Set<String> amr;              // 認証方式
  private final Instant createdAt;
  private final Instant expiresAt;

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }
}
```

**参考実装**: [OPSession.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/session/OPSession.java)

### ClientSession

**OPSessionと特定のRPの間のセッション**を表すクラスです。認可が完了すると作成されます。

```java
public class ClientSession {
  private final ClientSessionIdentifier sid;  // ID Token の sid クレームに含まれる
  private final OPSessionIdentifier opSessionId;
  private final String clientId;
  private final Set<String> scopes;
  private final String nonce;
  private final Instant createdAt;
}
```

**参考実装**: [ClientSession.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/session/ClientSession.java)

### OIDCSessionHandler

**セッション管理操作を調整**するクラスです。

```java
public class OIDCSessionHandler {

  // 認証成功時にOPSessionを作成
  public OPSession onAuthenticationSuccess(
      Tenant tenant, User user, Authentication authentication);

  // 認可時にClientSessionを作成（sidを返す）
  public ClientSessionIdentifier onAuthorize(
      Tenant tenant, OPSession opSession, String clientId,
      Set<String> scopes, String nonce);

  // セッションCookieを設定
  public void registerSessionCookies(
      Tenant tenant, OPSession opSession, SessionCookieDelegate delegate);

  // CookieからOPSessionを取得
  public Optional<OPSession> getOPSessionFromCookie(
      Tenant tenant, SessionCookieDelegate delegate);

  // セッションハッシュを計算（IDP_SESSION cookie用）
  public String computeSessionHash(String opSessionId);

  // セッション有効性を検証
  public boolean isSessionValid(OPSession opSession, Long maxAge);

  // OPSessionの終了
  public ClientSessions terminateOPSession(
      Tenant tenant, OPSessionIdentifier opSessionId, TerminationReason reason);
}
```

**参考実装**: [OIDCSessionHandler.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/session/OIDCSessionHandler.java)

---

## 🔄 セッションのライフサイクル

### 1. セッション作成（認証成功時）

```
┌──────────┐     ┌──────────────┐     ┌─────────────────────┐
│  User    │────▶│ 認証成功      │────▶│ OPSession作成        │
└──────────┘     └──────────────┘     └─────────────────────┘
                                              │
                                              ▼
                                      ┌─────────────────────┐
                                      │ Cookie設定           │
                                      │ - IDP_IDENTITY      │
                                      │ - IDP_SESSION       │
                                      └─────────────────────┘
```

**実装箇所**: `OAuthFlowEntryService.authenticate()`

```java
if (updatedTransaction.isSuccess()) {
  Authentication authentication = updatedTransaction.authentication();
  OPSession opSession = oidcSessionHandler.onAuthenticationSuccess(
      tenant, updatedTransaction.user(), authentication);

  // Cookie設定（OIDCSessionHandlerに委譲）
  oidcSessionHandler.registerSessionCookies(tenant, opSession, sessionCookieDelegate);
}
```

### 2. ClientSession作成（認可時）

```
┌──────────┐     ┌──────────────┐     ┌─────────────────────┐
│ 認可承認  │────▶│ Cookie読取    │────▶│ OPSession取得        │
└──────────┘     └──────────────┘     └─────────────────────┘
                                              │
                                              ▼
                                      ┌─────────────────────┐
                                      │ ClientSession作成    │
                                      │ → sid生成           │
                                      └─────────────────────┘
                                              │
                                              ▼
                                      ┌─────────────────────┐
                                      │ ID Token に sid含む  │
                                      └─────────────────────┘
```

**実装箇所**: `OAuthFlowEntryService.authorize()`

```java
oidcSessionHandler
    .getOPSessionFromCookie(tenant, sessionCookieDelegate)
    .ifPresent(opSession -> {
      ClientSessionIdentifier sid = oidcSessionHandler
          .onAuthorize(tenant, opSession, clientId, scopes, nonce);
      oAuthAuthorizeRequest.setCustomProperties(Map.of("sid", sid.value()));
    });
```

### 3. SSO（セッション再利用）

既存のセッションを使用して、再認証なしで認可を行います。

```
┌──────────┐     ┌──────────────┐     ┌─────────────────────┐
│ 認可要求  │────▶│ Cookie読取    │────▶│ OPSession取得        │
└──────────┘     └──────────────┘     └─────────────────────┘
                                              │
                                              ▼
                                      ┌─────────────────────┐
                                      │ セッション検証        │
                                      │ - 有効期限           │
                                      │ - max_age           │
                                      └─────────────────────┘
                                              │
                                              ▼
                                      ┌─────────────────────┐
                                      │ 認証スキップ          │
                                      │ → 直接認可           │
                                      └─────────────────────┘
```

**実装箇所**: `OAuthFlowEntryService.authorizeWithSession()`

```java
// OPSessionをCookieから取得（OIDCSessionHandlerに委譲）
Optional<OPSession> opSessionOpt = oidcSessionHandler
    .getOPSessionFromCookie(tenant, sessionCookieDelegate);

if (opSessionOpt.isEmpty()) {
  return new OAuthAuthorizeResponse(
      OAuthAuthorizeStatus.BAD_REQUEST, "invalid_request", "session not found");
}

OPSession opSession = opSessionOpt.get();

// max_ageによる検証
Long maxAge = authorizationRequest.maxAge().exists()
    ? authorizationRequest.maxAge().toLongValue()
    : null;

if (!oidcSessionHandler.isSessionValid(opSession, maxAge)) {
  return new OAuthAuthorizeResponse(
      OAuthAuthorizeStatus.BAD_REQUEST, "invalid_request", "session expired");
}

// セッションからAuthenticationを復元
LocalDateTime authTime = LocalDateTime.ofInstant(opSession.authTime(), ZoneOffset.UTC);
Authentication authentication = new Authentication()
    .setTime(authTime)
    .addAcr(opSession.acr())
    .addMethods(opSession.amr());
```

---

## 🚪 ログアウト

### RP-Initiated Logout

RPからログアウトを開始するフローです。

```
┌──────────┐     ┌──────────────┐     ┌─────────────────────┐
│  RP      │────▶│ /logout      │────▶│ id_token_hint解析    │
└──────────┘     └──────────────┘     └─────────────────────┘
                                              │
                                              ▼
                                      ┌─────────────────────┐
                                      │ ClientSession → sid │
                                      │ sid → OPSession     │
                                      └─────────────────────┘
                                              │
                                              ▼
                                      ┌─────────────────────┐
                                      │ 全ClientSessionに    │
                                      │ ログアウト通知        │
                                      └─────────────────────┘
                                              │
                                              ▼
                                      ┌─────────────────────┐
                                      │ Cookie削除           │
                                      │ セッション無効化      │
                                      └─────────────────────┘
```

**実装箇所**: `OAuthFlowEntryService.logout()`

```java
public OAuthLogoutResponse logout(...) {
  OAuthLogoutResponse response = oAuthProtocol.logout(oAuthLogoutRequest);

  if (response.isOk() && response.hasContext()) {
    // セッションログアウト実行
    response = executeSessionLogout(tenant, response);

    // Cookie削除
    if (sessionCookieDelegate != null) {
      sessionCookieDelegate.clearSessionCookies();
    }
  }

  return response;
}
```

### Back-Channel Logout

OPからRPへバックチャネルでログアウトを通知します。

```
┌──────────┐     ┌──────────────────┐     ┌─────────────────────┐
│ Logout   │────▶│ LogoutOrchestrator│────▶│ 各ClientSessionの    │
│ 開始     │     │                  │     │ RPを取得             │
└──────────┘     └──────────────────┘     └─────────────────────┘
                                                   │
                        ┌──────────────────────────┼──────────────────────────┐
                        ▼                          ▼                          ▼
                 ┌─────────────┐           ┌─────────────┐           ┌─────────────┐
                 │ Client A    │           │ Client B    │           │ Client C    │
                 │ POST logout │           │ POST logout │           │ POST logout │
                 │ token       │           │ token       │           │ token       │
                 └─────────────┘           └─────────────┘           └─────────────┘
```

#### BackChannelLogoutService インターフェース

```java
public interface BackChannelLogoutService {

  // Logout Tokenをエンコード（型安全: JWKSをStringで受け取る）
  String encodeLogoutToken(
      LogoutToken token,
      String signingAlgorithm,
      String signingKeyJwks);  // Object → String に変更

  // Logout Tokenを検証
  LogoutTokenValidationResult validateLogoutToken(
      String token,
      String expectedIssuer,
      String expectedAudience,
      String publicKeyJwks);  // Object → String に変更

  // RPへログアウト通知を送信
  BackChannelLogoutResult sendLogoutToken(String logoutUri, String logoutToken);
}
```

**型安全性の改善**: 以前は`Object`型だったJWKSパラメータを`String`型に変更し、型安全性を向上させました。

#### HttpClient依存性注入

`DefaultBackChannelLogoutService`はHttpClientを明示的に受け取ります（DIフレンドリー）：

```java
public class DefaultBackChannelLogoutService implements BackChannelLogoutService {

  private final HttpClient httpClient;

  public DefaultBackChannelLogoutService(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  // デフォルトHttpClientを作成するファクトリメソッド
  public static HttpClient createDefaultHttpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }
}
```

**参考実装**:
- [LogoutOrchestrator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/session/logout/LogoutOrchestrator.java)
- [BackChannelLogoutService.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/session/logout/BackChannelLogoutService.java)
- [DefaultBackChannelLogoutService.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/session/logout/DefaultBackChannelLogoutService.java)

---

## 🍪 Cookie管理

### SessionCookieDelegate インターフェース

Cookieの読み書きを抽象化するインターフェースです。

```java
public interface SessionCookieDelegate {

  // セッションCookieを設定
  void setSessionCookies(String identityToken, String sessionHash, long maxAgeSeconds);

  // IDP_IDENTITY Cookieを取得
  Optional<String> getIdentityToken();

  // IDP_SESSION Cookieを取得
  Optional<String> getSessionHash();

  // セッションCookieを削除
  void clearSessionCookies();
}
```

**参考実装**: [SessionCookieDelegate.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/session/SessionCookieDelegate.java)

### Spring Boot実装

```java
@Service
public class SessionCookieService implements SessionCookieDelegate {

  public static final String IDENTITY_COOKIE_NAME = "IDP_IDENTITY";
  public static final String SESSION_COOKIE_NAME = "IDP_SESSION";

  @Override
  public void setSessionCookies(String identityToken, String sessionHash, long maxAgeSeconds) {
    // IDP_IDENTITY cookie (HttpOnly)
    Cookie identityCookie = new Cookie(IDENTITY_COOKIE_NAME, identityToken);
    identityCookie.setHttpOnly(true);
    identityCookie.setSecure(true);
    identityCookie.setPath("/");

    // IDP_SESSION cookie (NOT HttpOnly - for session management iframe)
    Cookie sessionCookie = new Cookie(SESSION_COOKIE_NAME, sessionHash);
    sessionCookie.setHttpOnly(false);  // JavaScript からアクセス可能
    sessionCookie.setSecure(true);
    sessionCookie.setPath("/");

    // SameSite=Lax を設定
    addCookieWithSameSite(identityCookie, "Lax");
    addCookieWithSameSite(sessionCookie, "Lax");
  }
}
```

**参考実装**: [SessionCookieService.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/session/SessionCookieService.java)

### セッションハッシュ計算

`IDP_SESSION` Cookieには、セッションIDのSHA-256ハッシュを格納します。

```java
public class SessionHashCalculator {

  public static String sha256UrlEncodedHash(String input) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
  }

  public static boolean verifySessionHash(String opSessionId, String providedHash) {
    if (opSessionId == null || providedHash == null) {
      return false;
    }
    String expectedHash = sha256UrlEncodedHash(opSessionId);
    return expectedHash.equals(providedHash);
  }
}
```

**参考実装**: [SessionHashCalculator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/session/SessionHashCalculator.java)

---

## 🗄️ セッションストレージ

### OPSessionRepository

すべてのメソッドで**Tenantを第一引数**として受け取ります（マルチテナント対応）。

```java
public interface OPSessionRepository {
  void save(Tenant tenant, OPSession session);
  Optional<OPSession> findById(Tenant tenant, OPSessionIdentifier id);
  void updateLastAccessedAt(Tenant tenant, OPSession session);
  void delete(Tenant tenant, OPSessionIdentifier id);
}
```

### ClientSessionRepository

```java
public interface ClientSessionRepository {
  void save(Tenant tenant, ClientSession session);
  Optional<ClientSession> findBySid(Tenant tenant, ClientSessionIdentifier sid);
  ClientSessions findByOpSessionId(Tenant tenant, OPSessionIdentifier opSessionId);
  ClientSessions findByTenantAndSub(TenantIdentifier tenantId, String sub);
  ClientSessions findByTenantClientAndSub(TenantIdentifier tenantId, String clientId, String sub);
  void deleteBySid(Tenant tenant, ClientSessionIdentifier sid);
  void deleteByOpSessionId(Tenant tenant, OPSessionIdentifier opSessionId);
}
```

### Redis インデックス構造

Redisストレージでは、以下のインデックスにより効率的な検索が可能です：

```
# OPSession
op_session:{tenantId}:{opSessionId}              # メインデータ
idx:tenant:{tenantId}:sub:{sub}                   # ユーザーベース検索

# ClientSession
client_session:{tenantId}:{sid}                   # メインデータ
idx:tenant:{tenantId}:op_session:{opSessionId}   # OPSession別検索
idx:tenant:{tenantId}:sub:{sub}                   # ユーザーベース検索
idx:tenant:{tenantId}:client:{clientId}:sub:{sub} # クライアント×ユーザー検索
```

**ユーザーベース検索**: 管理APIからユーザーのセッションを検索・管理できます。
- `findByTenantAndSub()`: 特定ユーザーの全セッション取得
- `findByTenantClientAndSub()`: 特定クライアント×ユーザーのセッション取得

### ストレージの選択

| ストレージ | 特徴 | ユースケース |
|-----------|------|-------------|
| Redis | 高速、TTL対応、インデックス検索 | 本番環境推奨 |
| Database | 永続化、監査 | 厳格なコンプライアンス要件 |
| In-Memory | シンプル | 開発・テスト |

---

## 🔒 セキュリティ考慮事項

### 1. セッションハイジャック対策

- **HttpOnly Cookie**: `IDP_IDENTITY` はJavaScriptからアクセス不可
- **Secure Cookie**: HTTPS接続のみで送信
- **SameSite=Lax**: CSRF攻撃を軽減

### 2. セッション固定攻撃対策

認証成功時にセッションIDを再生成します。

```java
// OAuthSessionDataSource.java
private void regenerateSessionId() {
  String newSessionId = httpServletRequest.changeSessionId();
  log.info("Session ID regenerated: {} -> {}", oldSessionId, newSessionId);
}
```

### 3. max_age パラメータの検証

認可リクエストの `max_age` パラメータに基づいて、セッションの有効性を検証します。

```java
public boolean isSessionValid(OPSession opSession, Long maxAge) {
  if (opSession == null || opSession.isExpired()) {
    return false;
  }

  if (maxAge != null && maxAge > 0) {
    Instant authTime = opSession.authTime();
    Instant maxAuthTime = authTime.plusSeconds(maxAge);
    if (Instant.now().isAfter(maxAuthTime)) {
      return false;  // 再認証が必要
    }
  }

  return true;
}
```

### 4. prompt=login の処理

`prompt=login` が指定された場合は、既存セッションを無視して再認証を要求します。

---

## 🎯 将来の拡張機能

idp-serverでは、Keycloakの高度なセッション管理機能を参考に、以下の機能を将来的に実装予定です。

### Step-up Authentication（認証レベル管理）

ユーザーが既にログインしていても、より高い認証レベルが要求された場合に追加認証を求める機能。

```
認可リクエスト: acr_values=gold
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. 現在の認証レベルを確認（LoA_MAP）                          │
│    例: {"bronze": 1234567890}                               │
└─────────────────────────────────────────────────────────────┘
        │
        ▼ gold > bronze
┌─────────────────────────────────────────────────────────────┐
│ 2. Step-up認証を要求（MFA等）                                │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. LoA_MAP更新                                               │
│    {"bronze": 1234567890, "gold": 1234567900}               │
└─────────────────────────────────────────────────────────────┘
```

### セッション数制限

ユーザーあたりのセッション数を制限する機能。

| モード | 説明 |
|-------|------|
| `DENY_NEW_SESSION` | 上限超過時に新規ログインを拒否 |
| `TERMINATE_OLDEST_SESSION` | 最も古いセッションをログアウトして新規ログインを許可 |

### デバイストラッキング

セッションにデバイス情報を紐付けて管理する機能。

| 項目 | 説明 |
|-----|------|
| `device` | デバイス種別（Desktop, Mobile等） |
| `browser` | ブラウザ名・バージョン |
| `os` | OS名・バージョン |
| `ipAddress` | IPアドレス |

### DPoP（Demonstrating Proof of Possession）

トークンをクライアントの秘密鍵に紐付け、トークン盗難時の悪用を防止。

```
Client                              OP
  │ 1. DPoP proof生成（秘密鍵で署名） │
  │ 2. Token Request + DPoP header   │
  ├─────────────────────────────────>│
  │                                  │ 3. DPoP検証
  │ 4. Access Token (cnf: {jkt})     │
  │<─────────────────────────────────┤
```

---

## 📚 関連仕様

- [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html)
- [OpenID Connect RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html)
- [OpenID Connect Back-Channel Logout 1.0](https://openid.net/specs/openid-connect-backchannel-1_0.html)
- [OpenID Connect Front-Channel Logout 1.0](https://openid.net/specs/openid-connect-frontchannel-1_0.html)
- [RFC 9449 - OAuth 2.0 Demonstrating Proof of Possession (DPoP)](https://datatracker.ietf.org/doc/html/rfc9449)

---

## 🔗 関連ファイル

| ファイル | 説明 |
|---------|------|
| `OPSession.java` | OP-Browser間セッション |
| `ClientSession.java` | OP-RP間セッション |
| `OIDCSessionHandler.java` | セッション管理調整 |
| `OIDCSessionService.java` | セッションCRUD操作 |
| `OPSessionRepository.java` | OPセッションリポジトリインターフェース |
| `ClientSessionRepository.java` | ClientセッションリポジトリIF |
| `SessionCookieDelegate.java` | Cookie操作インターフェース |
| `SessionCookieService.java` | Spring Boot Cookie実装 |
| `SessionHashCalculator.java` | セッションハッシュ計算 |
| `BackChannelLogoutService.java` | バックチャネルログアウトIF |
| `DefaultBackChannelLogoutService.java` | バックチャネルログアウト実装 |
| `IdentityCookieToken.java` | Identity Cookie JWT |
| `LogoutOrchestrator.java` | ログアウト調整 |
| `OAuthFlowEntryService.java` | OAuth/OIDCフロー統合 |
