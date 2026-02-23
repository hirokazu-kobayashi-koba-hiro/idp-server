# UserInfo実装ガイド

## このドキュメントの目的

**UserInfoエンドポイント**（ユーザー情報取得）の実装を理解することが目標です。

### 所要時間
⏱️ **約20分**

### 前提知識
- [03. Token Flow](./03-token-endpoint.md)
- OpenID Connect基礎知識

---

## UserInfoとは

**Access Tokenを使ってユーザー情報を取得するエンドポイント**

**OpenID Connect Core 1.0 Section 5.3準拠**

---

## アーキテクチャ全体像

### 30秒で理解する全体像

```
HTTPリクエスト（Access Token）
    ↓
Controller (UserinfoV1Api) - HTTP処理
    ↓
EntryService (UserinfoEntryService) - オーケストレーション
    ├─ Tenant取得
    ├─ UserinfoRequest作成
    ├─ UserinfoProtocol.request()（Delegate渡し）
    └─ イベント発行
    ↓
Core層 (UserinfoProtocol)
    ├─ Access Token検証（署名・期限・失効チェック）
    ├─ Subject抽出
    ├─ Delegate.findUser() 呼び出し
    ├─ Scope別Claims抽出
    └─ レスポンス生成
    ↓
UseCase層 (UserinfoDelegate.findUser())
    └─ UserQueryRepository.get()
    ↓
ユーザー情報返却
```

### 主要クラスの責務

| クラス | 層 | 役割 | 実装 |
|--------|---|------|------|
| **UserinfoV1Api** | Controller | HTTPエンドポイント | [UserinfoV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/userinfo/UserinfoV1Api.java) |
| **UserinfoEntryService** | UseCase | トランザクション・Delegate実装 | [UserinfoEntryService.java:62-114](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/UserinfoEntryService.java#L62-L114) |
| **UserinfoProtocol** | Core | Access Token検証・Claims抽出 | Core |
| **UserinfoDelegate** | Interface | Core層→UseCase層コールバック | Core |
| **OAuthToken** | Core | Access Token情報（subject/scope） | Core Domain |

### Delegateパターン

**重要**: Core層はRepositoryに直接依存しない設計

```
Core層 (UserinfoProtocol)
    ↓ Delegate経由
UseCase層 (UserinfoEntryService.findUser())
    ↓
Repository層 (UserQueryRepository)
```

**理由**: Hexagonal Architectureの原則（Core層の独立性維持）

---

## エンドポイント

```
GET /{tenant-id}/v1/userinfo
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**実装**:
- [UserinfoV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/userinfo/UserinfoV1Api.java)
- [UserinfoEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/UserinfoEntryService.java)

---

## フロー

```
1. [クライアント] Access Token取得済み
   ↓
2. [クライアント] UserInfoリクエスト
GET /{tenant-id}/v1/userinfo
Authorization: Bearer eyJ...
   ↓
3. [UserinfoEntryService] リクエスト受信
   ↓
4. [UserinfoProtocol] Access Token検証
   ↓
5. [UserQueryRepository] ユーザー情報取得
   ↓
6. [Scope検証] 返却可能なClaimsをフィルタ
   ↓
7. [レスポンス] ユーザー情報返却
{
  "sub": "user-12345",
  "name": "John Doe",
  "email": "john@example.com",
  "email_verified": true
}
```

---

## EntryService実装

**実装**: [UserinfoEntryService.java:62](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/UserinfoEntryService.java#L62)

```java
@Transaction(readOnly = true)  // ✅ 読み取り専用
public class UserinfoEntryService implements UserinfoApi, UserinfoDelegate {

  UserinfoProtocols userinfoProtocols;
  UserQueryRepository userQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  UserEventPublisher eventPublisher;

  @Override
  public UserinfoRequestResponse request(
      TenantIdentifier tenantIdentifier,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    // 1. Tenant取得
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // 2. UserinfoRequest作成
    UserinfoRequest userinfoRequest = new UserinfoRequest(tenant, authorizationHeader);
    userinfoRequest.setClientCert(clientCert);  // MTLS対応

    // 3. Core層に委譲
    UserinfoProtocol userinfoProtocol = userinfoProtocols.get(tenant.authorizationProvider());
    UserinfoRequestResponse result = userinfoProtocol.request(userinfoRequest, this);

    // 4. イベント発行（成功時）
    if (result.isOK()) {
      eventPublisher.publish(
          tenant,
          result.oAuthToken(),
          DefaultSecurityEventType.userinfo_success,
          requestAttributes);
    }

    return result;
  }

  // ✅ Delegate実装: Core層からのコールバック
  @Override
  public User findUser(Tenant tenant, Subject subject) {
    UserIdentifier userIdentifier = new UserIdentifier(subject.value());
    return userQueryRepository.get(tenant, userIdentifier);
  }
}
```

**ポイント**:
- ✅ `@Transaction(readOnly = true)`: 読み取り専用トランザクション
- ✅ `UserinfoDelegate`実装: Core層へのコールバック提供
- ✅ イベント発行: `userinfo_success`

---

## UserinfoDelegate パターン

### Core層からのコールバック

```java
public interface UserinfoDelegate {
  /**
   * Core層がユーザー情報を取得する際に呼び出す
   */
  User findUser(Tenant tenant, Subject subject);
}
```

**実装例（Core層）**:

**実装**: [UserinfoHandler.java:58-90](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/userinfo/handler/UserinfoHandler.java#L58-L90)

```java
public class UserinfoHandler {

  OAuthTokenQueryRepository oAuthTokenQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  UserinfoCustomIndividualClaimsCreators userinfoCustomIndividualClaimsCreators;

  public UserinfoRequestResponse handle(UserinfoRequest request, UserinfoDelegate delegate) {

    // 1. Validator: 入力形式チェック
    AccessTokenEntity accessTokenEntity = request.toAccessToken();
    Tenant tenant = request.tenant();

    UserinfoValidator validator = new UserinfoValidator(request);
    validator.validate();

    // 2. Access Token取得
    OAuthToken oAuthToken = oAuthTokenQueryRepository.find(tenant, accessTokenEntity);

    if (!oAuthToken.exists()) {
      throw new TokenInvalidException("not found token");
    }

    // 3. 設定取得
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, oAuthToken.requestedClientId());

    // 4. Delegate経由でユーザー取得
    User user = delegate.findUser(tenant, oAuthToken.subject());

    // 5. Verifier: ビジネスルール検証
    UserinfoVerifier verifier = new UserinfoVerifier(oAuthToken, request.toClientCert(), user);
    verifier.verify();

    // 6. Claims抽出（Scope別フィルタリング）
    UserinfoClaimsCreator claimsCreator =
        new UserinfoClaimsCreator(
            user,
            oAuthToken.authorizationGrant(),
            authorizationServerConfiguration,
            clientConfiguration,
            userinfoCustomIndividualClaimsCreators);
    Map<String, Object> claims = claimsCreator.createClaims();

    // 7. レスポンス生成
    UserinfoResponse userinfoResponse = new UserinfoResponse(user, claims);
    return new UserinfoRequestResponse(UserinfoRequestStatus.OK, oAuthToken, userinfoResponse);
  }
}
```

**処理の7ステップ**:
1. Validator: 入力形式チェック
2. Access Token取得（OAuthTokenQueryRepository）
3. 設定取得（AuthorizationServerConfiguration/ClientConfiguration）
4. **Delegate経由でユーザー取得** ← UseCase層への依存注入
5. Verifier: ビジネスルール検証（トークン有効性・MTLS等）
6. Claims抽出（UserinfoClaimsCreator）
7. レスポンス生成

**Delegateパターンの理由**: Core層はRepositoryに直接依存せず、UseCase層経由でデータ取得（Hexagonal Architecture原則）

---

## Scope別の返却Claims

| Scope | 返却されるClaims |
|-------|---------------|
| `openid` | `sub`（必須） |
| `profile` | `name`, `family_name`, `given_name`, `middle_name`, `nickname`, `picture`, `website`, `gender`, `birthdate`, `zoneinfo`, `locale`, `updated_at` |
| `email` | `email`, `email_verified` |
| `phone` | `phone_number`, `phone_number_verified` |
| `address` | `address` (JSON) |

**例**:
```
Access Token scope: openid profile email

UserInfoレスポンス:
{
  "sub": "user-12345",
  "name": "John Doe",
  "email": "john@example.com",
  "email_verified": true
}
```

---

## Access Token検証

### 検証項目

UserInfoエンドポイントでは、以下を検証します：

1. **JWT署名検証**: Access TokenのJWT署名が正当か
2. **有効期限チェック**: `exp`クレームが期限内か
3. **失効チェック**: トークンが失効（revoke）されていないか
4. **Audience検証**: トークンの用途が正しいか
5. **ユーザー存在チェック**: ユーザーが存在するか
6. **ユーザーステータスチェック**: ユーザーがアクティブな状態か（LOCKED, DISABLED, SUSPENDED, DEACTIVATED, DELETED_PENDING, DELETEDは拒否）

### 検証エラー

```bash
# 無効なトークン
GET /{tenant-id}/v1/userinfo
Authorization: Bearer invalid-token

→ HTTP 401 Unauthorized
{
  "error": "invalid_token",
  "error_description": "The access token is invalid"
}
```

```bash
# 期限切れトークン
GET /{tenant-id}/v1/userinfo
Authorization: Bearer eyJ...（期限切れ）

→ HTTP 401 Unauthorized
{
  "error": "invalid_token",
  "error_description": "The access token has expired"
}
```

```bash
# 失効済みトークン
GET /{tenant-id}/v1/userinfo
Authorization: Bearer eyJ...（失効済み）

→ HTTP 401 Unauthorized
{
  "error": "invalid_token",
  "error_description": "The access token has been revoked"
}
```

```bash
# ユーザーが見つからない
GET /{tenant-id}/v1/userinfo
Authorization: Bearer eyJ...

→ HTTP 401 Unauthorized
{
  "error": "invalid_token",
  "error_description": "not found user"
}
```

```bash
# ユーザーがアクティブでない（LOCKED, DISABLED等）
GET /{tenant-id}/v1/userinfo
Authorization: Bearer eyJ...

→ HTTP 401 Unauthorized
{
  "error": "invalid_token",
  "error_description": "user is not active (id: xxx, status: LOCKED)"
}
```

---

## Claims抽出ロジック

### Scope → Claims マッピング

**実装**: Core層でScopeに基づいてClaimsをフィルタリング

```
Access Token:
  - subject: "user-12345"
  - scopes: ["openid", "profile", "email"]

User（DB）:
  - sub: "user-12345"
  - name: "John Doe"
  - email: "john@example.com"
  - phone_number: "+81-90-1234-5678"  ← phoneスコープなし
  - address: {...}  ← addressスコープなし

↓ Scope別にフィルタリング

UserInfoレスポンス:
  {
    "sub": "user-12345",       ← openidスコープ
    "name": "John Doe",        ← profileスコープ
    "email": "john@example.com", ← emailスコープ
    "email_verified": true     ← emailスコープ
  }
  ※ phone_number, address は含まれない（スコープなし）
```

### 最小限のレスポンス

**`openid`スコープのみ**の場合：

```json
{
  "sub": "user-12345"
}
```

**`sub`は常に返却**されます（OpenID Connect仕様）。

---

## よくあるエラー

### エラー1: `invalid_token` - 無効なAccess Token

**原因**: 期限切れ・不正なトークン

**解決策**: 新しいAccess Tokenを取得

### エラー2: Claimsが返却されない

**原因**: Scopeが不足

**解決策**: トークン取得時に必要なScopeを指定

```javascript
// ✅ 正しい
scope: 'openid profile email'  // profile, emailスコープ追加
```

### エラー3: `invalid_token` - ユーザーがアクティブでない

**原因**: ユーザーのステータスがLOCKED, DISABLED, SUSPENDED, DEACTIVATED, DELETED_PENDING, DELETEDのいずれか

**解決策**: 管理者がユーザーステータスをアクティブな状態（INITIALIZED, FEDERATED, REGISTERED, IDENTITY_VERIFIED, IDENTITY_VERIFICATION_REQUIRED）に変更する

---

## 次のステップ

✅ UserInfoの実装を理解した！

### 📖 次に読むべきドキュメント

1. [06. CIBA Flow実装](./06-ciba-flow.md) - バックチャネル認証

### 🔗 詳細情報

- [OpenID Connect Core 1.0 Section 5.3](https://openid.net/specs/openid-connect-core-1_0.html#UserInfo)

---

**情報源**: [UserinfoEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/UserinfoEntryService.java)
**最終更新**: 2025-10-12
