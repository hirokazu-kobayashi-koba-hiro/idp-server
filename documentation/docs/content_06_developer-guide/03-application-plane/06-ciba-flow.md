# CIBA Flow実装ガイド

## このドキュメントの目的

**CIBA（Client Initiated Backchannel Authentication）**の実装を理解することが目標です。

### 所要時間
⏱️ **約30分**

### 前提知識
- [02. Authorization Flow](./02-authorization-flow.md)
- [03. Token Flow](./03-token-endpoint.md)
- CIBA仕様基礎知識

---

## CIBAとは

**バックチャネル認証** - ユーザーがログインデバイスとは別のデバイス（スマホ等）で認証を承認する方式。

**OpenID Connect CIBA Core 1.0準拠**

---

## 通常のAuthorization Code Flowとの違い

| 項目 | Authorization Code Flow | CIBA |
|------|------------------------|------|
| **認証デバイス** | ブラウザ（同じデバイス） | スマホ等（別デバイス） |
| **リダイレクト** | あり（ブラウザリダイレクト） | なし |
| **ユーザー操作** | ブラウザでログイン | スマホでプッシュ通知承認 |
| **ポーリング** | なし | あり（またはPing/Push） |

---

## アーキテクチャ全体像

### 30秒で理解する全体像

```
HTTPリクエスト
    ↓
Controller (CibaV1Api) - HTTP処理
    ↓
EntryService (CibaFlowEntryService) - オーケストレーション
    ├─ Tenant取得
    ├─ CibaRequest作成
    ├─ CibaProtocol.request()（UserHintResolver使用）
    ├─ AuthenticationTransaction作成
    ├─ デフォルト認証実行（プッシュ通知）
    ├─ イベント発行
    └─ auth_req_id返却
    ↓
Core層 (CibaProtocol)
    ├─ Validator: 入力形式チェック
    ├─ UserHintResolver: login_hint → User解決
    ├─ Verifier: ビジネスルール検証
    └─ BackchannelAuthenticationRequest生成
    ↓
プッシュ通知 → ユーザー承認 → トークン発行（Token Flow）
```

### 主要クラスの責務

| クラス | 層 | 役割 | 実装 |
|--------|---|------|------|
| **CibaV1Api** | Controller | HTTPエンドポイント | [CibaV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/ciba/CibaV1Api.java) |
| **CibaFlowEntryService** | UseCase | トランザクション・オーケストレーション | [CibaFlowEntryService.java:86-142](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/CibaFlowEntryService.java#L86-L142) |
| **CibaProtocol** | Core | CIBA仕様準拠処理 | Extension Core |
| **UserHintResolver** | Core | login_hint → User解決（Plugin） | Extension Core |
| **BackchannelAuthenticationRequest** | Core | CIBA認証リクエスト（5分TTL） | Extension Core |
| **AuthenticationTransaction** | Core | 認証状態管理 | Core Domain |

### CIBAの特徴

**Authorization Code Flowとの違い**:
- ❌ **リダイレクトなし**: ユーザーは別デバイスで承認
- ✅ **auth_req_id**: Authorization Codeの代わり
- ✅ **ポーリング**: クライアントが定期的にトークンリクエスト
- ✅ **プッシュ通知**: FCM/APNS/SMSでユーザーに通知
- ✅ **非同期**: 認証リクエストとトークン取得が分離

---

## CIBAフロー

```
1. [クライアント] CIBA認証リクエスト
POST /{tenant-id}/v1/backchannel/authentications
{
  "login_hint": "user@example.com",
  "binding_message": "Code: 1234",
  "client_notification_token": "xxx"  // Push mode
}
   ↓
2. [idp-server] auth_req_id返却
{
  "auth_req_id": "auth-req-abc123",
  "expires_in": 300,
  "interval": 5  // Poll mode
}
   ↓
3. [idp-server] ユーザーにプッシュ通知送信
FCM/APNS → [ユーザーのスマホ]
「Code: 1234でログイン承認しますか？」
   ↓
4. [ユーザー] スマホで承認
   ↓
5. [idp-server] 認証完了を記録
   ↓
6. [クライアント] トークンリクエスト（ポーリング）
POST /{tenant-id}/v1/tokens
{
  "grant_type": "urn:openid:params:grant-type:ciba",
  "auth_req_id": "auth-req-abc123",
  "client_id": "xxx",
  "client_secret": "yyy"
}
   ↓
7. [idp-server] Access Token + ID Token発行
{
  "access_token": "eyJ...",
  "id_token": "eyJ...",
  "token_type": "Bearer"
}
```

---

## エンドポイント

```
# CIBA認証リクエスト
POST /{tenant-id}/v1/backchannel/authentications

# トークン取得（ポーリング）
POST /{tenant-id}/v1/tokens
{
  "grant_type": "urn:openid:params:grant-type:ciba",
  "auth_req_id": "xxx"
}
```

**実装**:
- [CibaV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/ciba/CibaV1Api.java)
- [CibaFlowEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/CibaFlowEntryService.java)

---

## EntryService実装

**実装**: [CibaFlowEntryService.java:86-142](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/CibaFlowEntryService.java#L86-L142)

### CIBA認証リクエスト処理（10ステップ）

```java
@Transaction
public class CibaFlowEntryService implements CibaFlowApi {

  CibaProtocols cibaProtocols;
  UserHintResolvers userHintResolvers;
  AuthenticationInteractors authenticationInteractors;
  CibaRequestAdditionalVerifiers additionalVerifiers;
  TenantQueryRepository tenantQueryRepository;
  AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository;
  AuthenticationPolicyConfigurationQueryRepository authenticationPolicyConfigurationQueryRepository;
  CibaFlowEventPublisher eventPublisher;

  @Override
  public CibaRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    // 1. Tenant取得
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // 2. CibaRequest作成
    CibaRequest cibaRequest = new CibaRequest(tenant, authorizationHeader, params);
    cibaRequest.setClientCert(clientCert);

    // 3. CibaProtocol取得
    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProvider());

    // 4. Core層に委譲（UserHintResolver + AdditionalVerifiers使用）
    CibaIssueResponse issueResponse =
        cibaProtocol.request(cibaRequest, userHintResolvers, additionalVerifiers);

    // 5. エラー時は即座に返却
    if (!issueResponse.isOK()) {
      return issueResponse.toErrorResponse();
    }

    // 6. イベント発行（CIBA認証リクエスト成功）
    eventPublisher.publish(
        tenant,
        issueResponse.request(),
        issueResponse.user(),
        DefaultSecurityEventType.backchannel_authentication_request_success.toEventType(),
        requestAttributes);

    // 7. AuthenticationPolicyConfiguration取得
    AuthenticationPolicyConfiguration authenticationPolicyConfiguration =
        authenticationPolicyConfigurationQueryRepository.find(
            tenant, StandardAuthFlow.CIBA.toAuthFlow());

    // 8. AuthenticationTransaction作成
    AuthenticationTransaction authenticationTransaction =
        CibaAuthenticationTransactionCreator.create(
            tenant, issueResponse, authenticationPolicyConfiguration);

    // 9. デフォルト認証実行（プッシュ通知送信）
    AuthenticationInteractionType authenticationInteractionType =
        issueResponse.defaultCibaAuthenticationInteractionType();
    AuthenticationInteractor authenticationInteractor =
        authenticationInteractors.get(authenticationInteractionType);

    AuthenticationInteractionRequestResult interactionRequestResult =
        authenticationInteractor.interact(
            tenant,
            authenticationTransaction,
            authenticationInteractionType,
            new AuthenticationInteractionRequest(Map.of()),
            requestAttributes,
            userQueryRepository);

    // 10. AuthenticationTransaction保存 + イベント発行
    AuthenticationTransaction updatedTransaction =
        authenticationTransaction.updateWith(interactionRequestResult);
    authenticationTransactionCommandRepository.register(tenant, updatedTransaction);

    eventPublisher.publish(
        tenant,
        issueResponse.request(),
        issueResponse.user(),
        interactionRequestResult.eventType(),
        requestAttributes);

    return issueResponse.toResponse();
  }
}
```

### ポイント

- ✅ **UserHintResolvers**: login_hint（email/phone/sub）からUserを解決（Plugin拡張可能）
- ✅ **CibaRequestAdditionalVerifiers**: CIBA固有の検証（パスワード必須等）
- ✅ **デフォルト認証実行**: CIBA認証リクエスト時点でプッシュ通知を送信
- ✅ **AuthenticationTransaction**: 認証状態を管理（ユーザー承認待ち）
- ✅ **イベント発行**: 2回（リクエスト成功・プッシュ通知送信）

---

## UserHintResolvers（login_hint解決）

**実装**: [UserHintResolvers.java](../../../../libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/user/UserHintResolvers.java)

### login_hintの形式

CIBAでは`login_hint`パラメータでユーザーを特定します。複数の形式をサポート：

| プレフィックス | 形式 | 例 | 検索方法 |
|--------------|------|---|---------|
| `sub:` | idp-server内部のユーザーID | `sub:user-uuid-12345` | UserIdentifierで直接取得 |
| `email:` | メールアドレス | `email:user@example.com` | emailで検索 |
| `phone:` | 電話番号 | `phone:+81-90-1234-5678` | phone_numberで検索 |
| `ex-sub:` | 外部IdPのsub | `ex-sub:google-user-12345:google` | external_user_id + provider_idで検索 |
| `device:` | デバイスID | `device:device-uuid-67890` | authentication_device.idで検索 |

### LoginHintResolverの実装

**実装**: [LoginHintResolver.java:27-76](../../../../libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/user/LoginHintResolver.java#L27-L76)

```java
public class LoginHintResolver implements UserHintResolver {

  @Override
  public User resolve(
      Tenant tenant,
      UserHint userHint,
      UserHintRelatedParams userHintRelatedParams,
      UserQueryRepository userQueryRepository) {

    String loginHint = userHint.value();

    // プレフィックスマッチャー（5種類）
    List<LoginHintMatcher> matchers = List.of(
        // 1. sub:user-uuid
        new PrefixMatcher("sub:", hints -> {
          UserIdentifier userIdentifier = new UserIdentifier(hints.getLeft());
          return userQueryRepository.get(tenant, userIdentifier);
        }),

        // 2. ex-sub:external-sub:provider-id
        new PrefixMatcher("ex-sub:", hints ->
            userQueryRepository.findByExternalIdpSubject(
                tenant, hints.getLeft(), hints.getRight())),

        // 3. device:device-id:provider-id
        new PrefixMatcher("device:", hints ->
            userQueryRepository.findByDeviceId(
                tenant,
                new AuthenticationDeviceIdentifier(hints.getLeft()),
                hints.getRight())),

        // 4. phone:+81-90-1234-5678:provider-id
        new PrefixMatcher("phone:", hints ->
            userQueryRepository.findByPhone(tenant, hints.getLeft(), hints.getRight())),

        // 5. email:user@example.com:provider-id
        new PrefixMatcher("email:", hints ->
            userQueryRepository.findByEmail(tenant, hints.getLeft(), hints.getRight()))
    );

    // 最初にマッチしたResolverでUser解決
    return matchers.stream()
        .filter(matcher -> matcher.matches(loginHint))
        .findFirst()
        .map(matcher -> {
          Pairs<String, String> hints = matcher.extractHints(loginHint);
          return matcher.resolve(hints);
        })
        .orElse(User.notFound());
  }
}
```

### 使用例

#### 1. emailでユーザー特定

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/backchannel/authentications" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'client:secret' | base64)" \
  -d "login_hint=email:user@example.com&binding_message=Code: 1234&scope=openid"
```

**処理**:
```
login_hint="email:user@example.com"
  ↓
PrefixMatcher("email:").matches() → true
  ↓
extractHints() → ("user@example.com", "")
  ↓
userQueryRepository.findByEmail(tenant, "user@example.com", "")
  ↓
User取得
```

---

#### 2. phone番号でユーザー特定

```bash
curl -X POST "..." \
  -d "login_hint=phone:+81-90-1234-5678&binding_message=Code: 1234&scope=openid"
```

---

#### 3. デバイスIDでユーザー特定

スマホアプリのデバイスIDを使用：

```bash
curl -X POST "..." \
  -d "login_hint=device:device-uuid-67890&binding_message=Code: 1234&scope=openid"
```

---

#### 4. 外部IdPのsubでユーザー特定

Google等の外部IdPで既に認証済みのユーザー：

```bash
curl -X POST "..." \
  -d "login_hint=ex-sub:google-user-12345:google&binding_message=Code: 1234&scope=openid"
```

---

### エラー

**ユーザーが見つからない場合**:

```json
{
  "error": "invalid_request",
  "error_description": "User not found for login_hint: email:unknown@example.com"
}
```

**HTTP Status**: `400 Bad Request`

---

## CIBA Mode

CIBAは3つのモードをサポート：

| Mode | 説明 | トークン取得方法 |
|------|------|---------------|
| **Poll** | ポーリング | クライアントが定期的にトークンリクエスト |
| **Ping** | Ping通知 | idp-serverがclient_notification_endpointに通知 |
| **Push** | プッシュ | idp-serverがclient_notification_endpointにトークン送信 |

### Poll Mode（最も一般的）

```
1. CIBA Request
   ↓ レスポンス
{
  "auth_req_id": "xxx",
  "expires_in": 300,
  "interval": 5  // 5秒ごとにポーリング
}
   ↓
2. クライアントがポーリング（5秒ごと）
POST /{tenant-id}/v1/tokens
{
  "grant_type": "urn:openid:params:grant-type:ciba",
  "auth_req_id": "xxx"
}
   ↓ ユーザー未承認時
{
  "error": "authorization_pending"
}
   ↓ ユーザー承認完了後
{
  "access_token": "eyJ...",
  "id_token": "eyJ..."
}
```

---

## クライアント認証

CIBA認証リクエストでは**クライアント認証が必須**です。

**詳細**: [10. Client Authentication実装](./10-client-authentication.md) - 7つの認証方式の完全ガイド

### サポートされる認証方式

| 認証方式 | 送信方法 | セキュリティレベル |
|---------|---------|----------------|
| **client_secret_basic** | Basic認証ヘッダー | ⭐⭐ |
| **client_secret_post** | POSTボディ | ⭐ |
| **client_secret_jwt** | JWT署名（共有鍵） | ⭐⭐⭐ |
| **private_key_jwt** | JWT署名（秘密鍵） | ⭐⭐⭐⭐ |
| **tls_client_auth** | クライアント証明書（MTLS） | ⭐⭐⭐⭐⭐ |

### client_secret_basic の例

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/backchannel/authentications" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'my-client:my-secret' | base64)" \
  -d "login_hint=user@example.com&binding_message=Code: 1234&scope=openid profile"
```

### client_secret_post の例

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/backchannel/authentications" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "login_hint=user@example.com&binding_message=Code: 1234&scope=openid profile&client_id=my-client&client_secret=my-secret"
```

---

## トークンリクエスト（CIBA Grant Type）

**実装**: [CibaGrantService.java](../../../../libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/grant/CibaGrantService.java)

### リクエスト

```bash
POST /{tenant-id}/v1/tokens
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(client_id:client_secret)

grant_type=urn:openid:params:grant-type:ciba&auth_req_id=8d67dc78-7faa-4d41-aabd-67707b374255
```

### 処理フロー

```
CibaGrantService.create()
    ↓
┌─────────────────────────────────────────────────────┐
│ 1. Validator（入力形式チェック）                       │
├─────────────────────────────────────────────────────┤
│  - auth_req_id必須チェック                            │
│  - grant_type検証                                    │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 2. BackchannelAuthenticationRequest取得              │
├─────────────────────────────────────────────────────┤
│  - auth_req_idでBackchannelAuthenticationRequest検索 │
│  - 存在しない → invalid_grant エラー                  │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 3. Verifier（ビジネスルール検証）                      │
├─────────────────────────────────────────────────────┤
│  - 有効期限チェック（5分以内）                         │
│  - クライアント一致チェック                            │
│  - ユーザー承認済みチェック                            │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 4. 承認状態チェック                                   │
├─────────────────────────────────────────────────────┤
│  - 承認待ち → authorization_pending                  │
│  - ユーザー拒否 → access_denied                       │
│  - 承認完了 → トークン発行へ                          │
└─────────────────────────────────────────────────────┘
    ↓ 承認完了の場合
┌─────────────────────────────────────────────────────┐
│ 5. トークン生成                                       │
├─────────────────────────────────────────────────────┤
│  - Access Token生成（JWT、デフォルト1時間有効）        │
│  - Refresh Token生成（設定による）                     │
│  - ID Token生成（nonce/at_hash/c_hash含む）          │
│  - BackchannelAuthenticationRequest削除（ワンタイム）  │
└─────────────────────────────────────────────────────┘
    ↓
OAuthToken（Access/Refresh/ID Token）
```

### レスポンスパターン

#### 1. 承認待ち（authorization_pending）

ユーザーがまだスマホで承認していない状態：

```json
{
  "error": "authorization_pending",
  "error_description": "The authorization request is still pending"
}
```

**HTTP Status**: `400 Bad Request`

**対処**: `interval`秒待機してリトライ（デフォルト5秒）

---

#### 2. 承認完了（トークン発行）

ユーザーがスマホで承認した後：

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "scope": "openid profile email"
}
```

**HTTP Status**: `200 OK`

**重要**: auth_req_idは削除される（ワンタイム使用）

---

#### 3. ユーザー拒否（access_denied）

ユーザーがスマホで拒否ボタンを押した場合：

```json
{
  "error": "access_denied",
  "error_description": "The resource owner denied the request"
}
```

**HTTP Status**: `400 Bad Request`

**対処**: 新しいCIBA認証リクエストを実行

---

#### 4. タイムアウト（expired_token）

auth_req_idの有効期限（5分）が切れた場合：

```json
{
  "error": "expired_token",
  "error_description": "The auth_req_id has expired"
}
```

**HTTP Status**: `400 Bad Request`

**対処**: 新しいCIBA認証リクエストを実行

---

## ポーリング仕様（OpenID Connect CIBA準拠）

### interval（ポーリング間隔）

CIBA認証リクエストのレスポンスで返される`interval`を厳守：

```json
{
  "auth_req_id": "xxx",
  "expires_in": 300,
  "interval": 5  // この秒数ごとにポーリング
}
```

**RFC仕様**:
- クライアントは**最低でも`interval`秒**待機してからリトライ
- interval未満でリクエスト → `slow_down`エラー
- `slow_down`受信時 → intervalを5秒延長

**例**: interval=5の場合
```
0秒: CIBA認証リクエスト
5秒: 1回目のトークンリクエスト → authorization_pending
10秒: 2回目のトークンリクエスト → authorization_pending
15秒: 3回目のトークンリクエスト → トークン発行成功
```

---

### expires_in（有効期限）

auth_req_idは`expires_in`秒間のみ有効：

```json
{
  "auth_req_id": "xxx",
  "expires_in": 300  // 300秒 = 5分間有効
}
```

**動作**:
- 5分以内にユーザー承認 → トークン発行可能
- 5分経過後 → `expired_token`エラー
- トークン発行後 → auth_req_id削除（再利用不可）

---

## auth_req_id のライフサイクル

### 作成 → 使用 → 削除

```
1. CIBA認証リクエスト
   → BackchannelAuthenticationRequest作成
   → auth_req_id生成（UUID）
   → Redis + DB保存（5分TTL）

2. ユーザー承認待ち
   → ポーリング: authorization_pending
   → AuthenticationTransaction更新（承認状態）

3. トークンリクエスト（承認完了後）
   → BackchannelAuthenticationRequest取得
   → トークン生成
   → BackchannelAuthenticationRequest削除  ← ワンタイム

4. 再度トークンリクエスト
   → BackchannelAuthenticationRequest不存在
   → invalid_grant エラー
```

**重要**: Authorization Codeと同じく、**使用後即削除**される（`used`フラグではない）

---

## BackchannelAuthenticationRequest vs AuthenticationTransaction

2つの異なるオブジェクトが使われます：

| オブジェクト | 役割 | 作成タイミング | 削除タイミング |
|-------------|------|--------------|--------------|
| **BackchannelAuthenticationRequest** | CIBA認証リクエスト情報 | CIBA認証リクエスト時 | トークン発行後 |
| **AuthenticationTransaction** | 認証状態管理 | CIBA認証リクエスト時 | 認証完了 or 失敗 or ロック時 |

### BackchannelAuthenticationRequest

**保存される情報**:
```java
- auth_req_id: UUID
- client_id: クライアントID
- login_hint: ユーザーヒント
- binding_message: "Code: 1234"
- scope: "openid profile email"
- expiresAt: 現在時刻+5分
```

**用途**: トークンリクエスト時の検証（クライアント一致・有効期限等）

### AuthenticationTransaction

**保存される情報**:
```java
- identifier: auth-req-12345
- authorizationIdentifier: auth-req-12345
- authenticationPolicy: 認証ポリシー
- interactionResults: {"push_notification": {successCount: 0, attemptCount: 1}}
- request.user: User(sub=user-12345)
```

**用途**: ユーザー承認状態の管理（承認待ち・承認完了・拒否）

### 使い分け

```
CIBA認証リクエスト:
  BackchannelAuthenticationRequest作成（auth_req_id発行）
  AuthenticationTransaction作成（認証状態管理）
  ↓
ユーザー承認:
  AuthenticationTransaction更新（interactionResults更新）
  ↓
トークンリクエスト:
  BackchannelAuthenticationRequest検証（有効期限・クライアント）
  AuthenticationTransaction確認（承認済みか）
  トークン発行
  BackchannelAuthenticationRequest削除
  AuthenticationTransaction削除
```

---

## チェックリスト

CIBA実装時の確認項目：

### EntryService（UseCase層）
- [ ] CibaRequest作成
- [ ] UserHintResolvers使用（login_hint解決）
- [ ] AuthenticationTransaction作成
- [ ] プッシュ通知送信

### Core層（CibaProtocol）
- [ ] login_hint検証
- [ ] binding_message検証
- [ ] BackchannelAuthenticationRequest生成

### E2Eテスト
- [ ] Poll modeテスト
- [ ] ユーザー承認テスト
- [ ] ユーザー拒否テスト

---

## よくあるエラー

### エラー1: `authorization_pending` - 認証待ち

**原因**: ユーザーがまだ承認していない

**解決策**: ポーリング継続（interval秒待機）

### エラー2: `access_denied` - ユーザーが拒否

**原因**: ユーザーがスマホで拒否ボタンを押した

**解決策**: 新しいCIBA認証リクエストを実行

---

## 次のステップ

✅ CIBA Flowの実装を理解した！

### 📖 次に読むべきドキュメント

1. [07. Identity Verification実装](./07-identity-verification.md) - 身元確認申込み

### 🔗 詳細情報

- [AI開発者向け: CIBA Extension](../../content_10_ai_developer/ai-31-extension-ciba.md)
- [OpenID Connect CIBA Core 1.0](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)

---

**情報源**: [CibaFlowEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/CibaFlowEntryService.java)
**最終更新**: 2025-10-12
