# フェデレーション（外部IdP連携）実装ガイド

## このドキュメントの目的

**外部アイデンティティプロバイダ（IdP）**との連携を理解することが目標です。

### 所要時間
⏱️ **約30分**

### 前提知識
- [04. Authentication実装](./04-authentication.md)
- OpenID Connect基礎知識

---

## フェデレーションとは

Authorization Code Flowの認証フェーズで、**外部IdP（Google/Azure AD等）**を使ってユーザー認証を行う仕組み。

```
Authorization Request
  ↓
[ログイン画面表示]
  ↓
【フェデレーション】← このドキュメントの対象
  ├─ Google でログイン
  ├─ Azure AD でログイン
  └─ など
  ↓
Authorization Code発行
```

**対応プロトコル**:
- ✅ **OpenID Connect (OIDC)** - 実装済み
- 🔜 **SAML 2.0** - 対応予定

**用途**:
- エンタープライズSSO（Google Workspace、Azure AD等）
- BYOIdP（マルチテナントSaaSでのIdP持ち込み）

---

## アーキテクチャ全体像

### 30秒で理解する全体像

```
HTTPリクエスト
    ↓
Controller (OAuthV1Api) - HTTP処理
    ↓
EntryService (OAuthFlowEntryService) - オーケストレーション
    ├─ FederationInteractor選択（Plugin）
    ├─ SsoSession作成
    ├─ 外部IdPへリダイレクト
    ↓
外部IdP（Google/Azure AD等）
    ├─ ユーザー認証
    └─ Authorization Code発行
    ↓
Callback
    ↓
EntryService (OAuthFlowEntryService.callbackFederation())
    ├─ SsoSession取得（state検証）
    ├─ Token Request（外部IdPへ）
    ├─ ID Token検証
    ├─ UserInfo取得
    ├─ User作成/更新
    └─ AuthenticationTransaction更新
    ↓
Authorization Code発行（通常フロー）
```

### 主要クラスの責務

| クラス | 層 | 役割 | 実装 |
|--------|---|------|------|
| **OAuthV1Api** | Controller | HTTPエンドポイント | [OAuthV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/oauth/OAuthV1Api.java) |
| **OAuthFlowEntryService** | UseCase | トランザクション・オーケストレーション | [OAuthFlowEntryService.java:216-246](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L216-L246) |
| **FederationInteractor** | Core | フェデレーション処理（Plugin） | Extension Core |
| **OidcSsoExecutor** | Core | プロバイダー別処理（Google/Azure AD等） | Extension Core |
| **SsoSession** | Core | state/nonce/code_verifier保持 | Extension Core |

---

## エンドポイント

### Federation Request

外部IdPへのリダイレクトを開始：

```
POST /{tenant-id}/v1/authorizations/{auth-req-id}/federations/{type}/{provider}

# 例: Googleでログイン
POST /{tenant-id}/v1/authorizations/abc-123/federations/oidc/google
```

**レスポンス**:
```
HTTP/1.1 302 Found
Location: https://accounts.google.com/o/oauth2/v2/auth?
  client_id=xxx.apps.googleusercontent.com&
  redirect_uri=http://localhost:8080/{tenant-id}/v1/authorizations/federations/oidc/callback&
  response_type=code&
  scope=openid+profile+email&
  state=uuid&
  nonce=uuid&
  code_challenge=xxx&
  code_challenge_method=S256
```

**実装**: [OAuthFlowEntryService.java:216-246](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L216-L246)

---

### Federation Callback

外部IdPからのコールバック受信：

```
POST /{tenant-id}/v1/authorizations/federations/{type}/callback

# 例: Googleからのコールバック
POST /{tenant-id}/v1/authorizations/federations/oidc/callback
Content-Type: application/x-www-form-urlencoded

code=abc123&state=session-uuid
```

**パラメータ（リクエストボディ）**:
- `code`: 認可コード（外部IdPが発行）
- `state`: Session識別子（改ざん検証用）

**実装**: [OAuthFlowEntryService.java:248-281](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L248-L281)

---

## フェデレーション処理フロー

### Phase 1: Federation Request

```
POST /{tenant-id}/v1/authorizations/{auth-req-id}/federations/oidc/google
    ↓
OAuthFlowEntryService.requestFederation()
    ↓
┌─────────────────────────────────────────────────────┐
│ 1. FederationInteractor選択（Plugin）                │
├─────────────────────────────────────────────────────┤
│  FederationInteractors.get(FederationType.OIDC)    │
│    → OidcFederationInteractor                      │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 2. フェデレーション設定取得                             │
├─────────────────────────────────────────────────────┤
│  FederationConfigurationQueryRepository.get()      │
│    → OidcSsoConfiguration                          │
│       - client_id                                  │
│       - authorization_endpoint                     │
│       - scopes等                                    │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 3. SsoSession作成                                   │
├─────────────────────────────────────────────────────┤
│  SsoSessionの役割: Callback時のセキュリティ検証        │
│                                                     │
│  保存される情報:                                      │
│  ┌─────────────────────────────────────────────┐   │
│  │ state: UUID                                 │   │
│  │  用途: CSRF攻撃防止                          │   │
│  │  検証: Callback時にstateパラメータと一致確認  │   │
│  ├─────────────────────────────────────────────┤   │
│  │ nonce: UUID                                 │   │
│  │  用途: リプレイ攻撃防止                       │   │
│  │  検証: ID Tokenのnonceクレームと一致確認     │   │
│  ├─────────────────────────────────────────────┤   │
│  │ code_verifier: ランダム文字列（43-128文字）   │   │
│  │  用途: PKCE（コード横取り防止）              │   │
│  │  使用: Token Request時に外部IdPへ送信       │   │
│  ├─────────────────────────────────────────────┤   │
│  │ authorizationRequestIdentifier              │   │
│  │  用途: 元の認可リクエスト識別                 │   │
│  │  使用: AuthenticationTransaction取得        │   │
│  ├─────────────────────────────────────────────┤   │
│  │ ssoProvider: GOOGLE                         │   │
│  │  用途: プロバイダー識別                       │   │
│  │  使用: Callback時のルーティング              │   │
│  └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 4. SsoSession保存（DB: federation_sso_session）      │
├─────────────────────────────────────────────────────┤
│  SsoSessionCommandRepository.register()            │
│    → Callback時の検証に使用                          │
│                                                     │
│  保存場所:                                           │
│  - PostgreSQL/MySQL（federation_sso_sessionテーブル）│
│  - JSONB形式でpayload保存                           │
│  - テナント分離（RLS適用）                           │
│  - 一時データ（フェデレーション完了後は削除）          │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 5. 外部IdPへのリダイレクトURL生成                       │
├─────────────────────────────────────────────────────┤
│  https://accounts.google.com/o/oauth2/v2/auth?     │
│    client_id=xxx&                                  │
│    redirect_uri=http://localhost/{tenant}/callback&│
│    state={state}&                                  │
│    nonce={nonce}&                                  │
│    code_challenge=SHA256(code_verifier)&  ← PKCE  │
│    code_challenge_method=S256&                     │
│    scope=openid+profile+email                     │
│                                                     │
│  PKCE使用理由（RFC 7636）:                           │
│  - 外部IdPからのAuthorization Codeもブラウザ経由    │
│  - ブラウザ = Code横取りリスク                       │
│  - code_verifier検証でCode横取り防止                │
└─────────────────────────────────────────────────────┘
    ↓
→ レスポンス: 302 Redirect
```

**セキュリティ検証の仕組み**:
- **state**: CSRF攻撃防止（Callback時にstateパラメータと一致確認）
- **nonce**: リプレイ攻撃防止（ID Tokenのnonceクレームと一致確認）
- **code_verifier**: PKCE（Token Request時に外部IdPへ送信、code_challengeと照合）

---

### Phase 2: 外部IdPでの認証

```
ユーザー → Googleログイン画面
    ↓
Google アカウントで認証
    ↓
Google が Authorization Code 発行
    ↓
302 Redirect: http://localhost/{tenant}/v1/authorizations/federations/oidc/callback?code=abc123&state={state}
```

---

### Phase 3: Federation Callback

```
POST /{tenant-id}/v1/authorizations/federations/oidc/callback
# body: code=abc123&state={state}
    ↓
OAuthFlowEntryService.callbackFederation()
    ↓
┌─────────────────────────────────────────────────────┐
│ 1. SsoSession取得（state検証）                        │
├─────────────────────────────────────────────────────┤
│  SsoSessionQueryRepository.get(state)              │
│  - state一致確認（改ざん検出）                         │
│  - 存在しない → invalid_request エラー                │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 2. Token Request（外部IdPへ）                        │
├─────────────────────────────────────────────────────┤
│  POST https://oauth2.googleapis.com/token          │
│    grant_type=authorization_code&                  │
│    code=abc123&                                    │
│    client_id=xxx&                                  │
│    client_secret=yyy&                              │
│    code_verifier={verifier}  ← PKCE                │
│                                                     │
│  レスポンス:                                          │
│  {                                                  │
│    "access_token": "ya29.xxx",                     │
│    "id_token": "eyJhbGci...",                      │
│    "expires_in": 3600                              │
│  }                                                  │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 3. ID Token検証                                     │
├─────────────────────────────────────────────────────┤
│  - 署名検証（JWKSから公開鍵取得）                       │
│  - iss検証（https://accounts.google.com）           │
│  - aud検証（client_id一致）                          │
│  - exp検証（有効期限内）                              │
│  - nonce検証（SsoSessionのnonceと一致）              │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 4. UserInfo取得（外部IdPへ）                         │
├─────────────────────────────────────────────────────┤
│  GET https://openidconnect.googleapis.com/v1/userinfo│
│  Authorization: Bearer ya29.xxx                    │
│                                                     │
│  レスポンス:                                          │
│  {                                                  │
│    "sub": "google-user-12345",                     │
│    "email": "user@gmail.com",                      │
│    "name": "John Doe",                             │
│    "picture": "https://..."                        │
│  }                                                  │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 5. User作成/更新                                     │
├─────────────────────────────────────────────────────┤
│  既存ユーザー検索:                                     │
│    external_user_id = "google-user-12345"          │
│    provider_id = "google"                          │
│                                                     │
│  存在する → ユーザー情報更新（email/name等）            │
│  存在しない → 新規ユーザー作成                          │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 6. SsoCredentials保存                               │
├─────────────────────────────────────────────────────┤
│  - user_id                                         │
│  - sso_provider: "google"                          │
│  - access_token: "ya29.xxx"  ← SSO継続用            │
│  - refresh_token                                   │
│  - id_token                                        │
│  → 次回ログイン時にSSO可能                            │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ 7. AuthenticationTransaction更新                    │
├─────────────────────────────────────────────────────┤
│  interactionResults: {                             │
│    "oidc-google": {successCount: 1, ...}           │
│  }                                                  │
│  isSuccess(): true  ← 認証完了                       │
└─────────────────────────────────────────────────────┘
    ↓
Authorization Code発行可能
```

---

## サポートされるプロバイダー

| SsoProvider | 説明 | エンドポイント |
|------------|------|-------------|
| **GOOGLE** | Google Workspace / Gmail | accounts.google.com |
| **AZURE_AD** | Microsoft Azure AD / Entra ID | login.microsoftonline.com |
| **GENERIC_OIDC** | 標準OIDC準拠IdP | カスタム設定 |

**拡張可能**: 新しいSsoProviderをPluginとして追加可能

**詳細**: [実装ガイド: Federation Provider実装](../04-implementation-guides/impl-08-federation-provider.md)

---

## ユースケース

| ユースケース | 例 | 利点 |
|-----------|---|------|
| **エンタープライズSSO** | Google Workspace、Azure AD | 既存アカウントでログイン可能 |
| **国家デジタルID** | マイナンバーカード | 高信頼性認証 |
| **学術フェデレーション** | 学認（GakuNin）、eduGAIN | 学術機関間SSO |
| **BYOIdP** | マルチテナントSaaSでのIdP持ち込み | テナントごとに異なるIdP |

---

## フェデレーション設定

**Management APIで事前設定が必要**:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "oidc_federation",
  "sso_provider": "google",
  "client_id": "xxx.apps.googleusercontent.com",
  "client_secret": "GOCSPX-xxx",
  "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
  "token_endpoint": "https://oauth2.googleapis.com/token",
  "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo",
  "jwks_uri": "https://www.googleapis.com/oauth2/v3/certs",
  "issuer": "https://accounts.google.com",
  "scopes": ["openid", "profile", "email"],
  "user_info_mapping": {
    "sub": "sub",
    "email": "email",
    "name": "name",
    "picture": "picture"
  }
}
```

**設定API**:
```
POST /v1/management/tenants/{tenant-id}/federation-configurations
```

---

## User同定戦略

### 外部IdPのsubとの紐付け

フェデレーションでは、外部IdPの`sub`（Subject Identifier）でユーザーを識別：

#### 初回ログイン（新規ユーザー作成）

```
外部IdP（Google）からのID Token:
{
  "sub": "google-user-12345",
  "email": "john@gmail.com",
  "name": "John Doe"
}
    ↓
idp-serverでユーザー検索:
  WHERE external_user_id = "google-user-12345"
    AND provider_id = "google"
    ↓
  結果: 見つからない（初回ログイン）
    ↓
新規ユーザー作成:
{
  "id": "user-uuid-abc",  ← idp-server内部ID
  "external_user_id": "google-user-12345",  ← Googleのsub
  "provider_id": "google",
  "email": "john@gmail.com",
  "name": "John Doe",
  "email_verified": true  ← Googleで検証済み
}
```

#### 2回目以降のログイン（既存ユーザー）

```
外部IdP（Google）からのID Token:
{
  "sub": "google-user-12345",  ← 同じsub
  "email": "john@gmail.com"
}
    ↓
idp-serverでユーザー検索:
  WHERE external_user_id = "google-user-12345"
    AND provider_id = "google"
    ↓
  結果: 見つかった（user-uuid-abc）
    ↓
既存ユーザー情報更新:
{
  "id": "user-uuid-abc",  ← 同じID
  "email": "john@gmail.com",  ← 最新情報に更新
  "name": "John Doe"
}
```

**検索に provider_id も必要な理由**:
複数のIdPで同じsubが存在する可能性があるため、`external_user_id` と `provider_id` の組み合わせで一意に識別

---

## レスポンス

### Federation Request成功

```
HTTP/1.1 302 Found
Location: https://accounts.google.com/o/oauth2/v2/auth?...
```

ブラウザが外部IdPにリダイレクト

---

### Federation Callback成功

外部IdP認証成功後、idp-serverの認証フローに戻る：

```
HTTP/1.1 302 Found
Location: /{tenant-id}/v1/authorizations/{auth-req-id}/authorize
```

この後、通常のAuthorization Code発行フローに続く

---

### エラー

#### 1. 設定なし

```json
{
  "error": "invalid_request",
  "error_description": "Federation configuration not found for provider: google"
}
```

**HTTP Status**: `400 Bad Request`

**対処**: Management APIで設定を登録

---

#### 2. state検証失敗

```json
{
  "error": "invalid_request",
  "error_description": "Invalid state parameter"
}
```

**HTTP Status**: `400 Bad Request`

**原因**: state改ざん、またはSession期限切れ（5分）

---

#### 3. ID Token検証失敗

```json
{
  "error": "invalid_request",
  "error_description": "ID token verification failed: invalid signature"
}
```

**HTTP Status**: `400 Bad Request`

**原因**: 署名不正、iss不一致、aud不一致、nonce不一致

---

## SSO（シングルサインオン）

一度外部IdPで認証すると、SsoCredentialsが保存され、次回ログイン時にSSO可能：

```
初回ログイン:
  外部IdP認証 → User作成 → SsoCredentials保存

2回目以降:
  SsoCredentials存在 → 外部IdPのAccess Tokenで自動認証
  （パスワード入力不要）
```

**SsoCredentialsの有効期限**: 外部IdPのAccess Token有効期限に依存

---

## E2Eテスト例

**参考**: `e2e/src/tests/scenario/application/scenario-federation-oidc.test.js`

```javascript
const { v4: uuidv4 } = require('uuid');
const axios = require('axios');

describe('OIDC Federation Flow', () => {
  let tenantId = '18ffff8d-8d97-460f-a71b-33f2e8afd41e';
  let clientId = 'test-client';
  let redirectUri = 'http://localhost:3000/callback';

  beforeAll(async () => {
    // Management APIでフェデレーション設定登録
    await axios.post(
      `http://localhost:8080/v1/management/tenants/${tenantId}/federation-configurations`,
      {
        id: uuidv4(),
        type: 'oidc_federation',
        sso_provider: 'google',
        client_id: 'xxx.apps.googleusercontent.com',
        client_secret: 'GOCSPX-xxx',
        authorization_endpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
        token_endpoint: 'https://oauth2.googleapis.com/token',
        userinfo_endpoint: 'https://openidconnect.googleapis.com/v1/userinfo',
        jwks_uri: 'https://www.googleapis.com/oauth2/v3/certs',
        issuer: 'https://accounts.google.com',
        scopes: ['openid', 'profile', 'email']
      },
      {
        headers: { Authorization: `Bearer ${adminToken}` }
      }
    );
  });

  test('should redirect to external IdP', async () => {
    // 1. Authorization Request
    const authResponse = await axios.get(
      `http://localhost:8080/${tenantId}/v1/authorizations`,
      {
        params: {
          response_type: 'code',
          client_id: clientId,
          redirect_uri: redirectUri,
          scope: 'openid'
        },
        maxRedirects: 0,
        validateStatus: (status) => status === 302
      }
    );

    const authReqId = extractAuthReqId(authResponse.headers.location);

    // 2. Federation Request
    const federationResponse = await axios.post(
      `http://localhost:8080/${tenantId}/v1/authorizations/${authReqId}/federations/oidc/google`,
      {},
      {
        maxRedirects: 0,
        validateStatus: (status) => status === 302
      }
    );

    // Googleへのリダイレクト確認
    expect(federationResponse.status).toBe(302);
    expect(federationResponse.headers.location).toContain('accounts.google.com/o/oauth2/v2/auth');
    expect(federationResponse.headers.location).toContain('state=');
    expect(federationResponse.headers.location).toContain('nonce=');
    expect(federationResponse.headers.location).toContain('code_challenge=');
  });

  test('should handle federation callback', async () => {
    // テスト環境では、外部IdPの代わりにモックを使用
    // 実際のGoogle認証は手動テストで実施

    const mockState = 'test-state-uuid';
    const mockCode = 'mock-google-code';

    // 3. Federation Callback（モック）
    const callbackResponse = await axios.post(
      `http://localhost:8080/${tenantId}/v1/authorizations/federations/oidc/callback`,
      `code=${mockCode}&state=${mockState}`,
      {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        maxRedirects: 0,
        validateStatus: (status) => status === 302
      }
    );

    // Authorization Code発行フローへリダイレクト確認
    expect(callbackResponse.status).toBe(302);
    expect(callbackResponse.headers.location).toContain('/authorizations/');
    expect(callbackResponse.headers.location).toContain('/authorize');
  });
});
```

**注意**: 実際のGoogle認証はモック環境では困難なため、テストではCallback部分のみを検証。実際の外部IdP連携は手動テストで確認。

---

## 次のステップ

✅ フェデレーションの実装を理解した！

### 📖 次に読むべきドキュメント

1. [実装ガイド: Federation Provider実装](../04-implementation-guides/impl-08-federation-provider.md) - 新しいSsoProvider追加方法

### 🔗 詳細情報

- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)

---

**情報源**: [OAuthFlowEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java)
**最終更新**: 2025-10-13
