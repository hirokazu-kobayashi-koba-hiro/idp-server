# Authorization Code Flow実装ガイド

## このドキュメントの目的

**Authorization Code Flow**（OAuth 2.0で最も一般的な認可フロー）の実装を理解することが目標です。

### 所要時間
⏱️ **約45分**

### 前提知識
- [01. Application Plane概要](./01-overview.md)
- OAuth 2.0基礎知識（[RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749)）

---

## Authorization Code Flowとは

**3つのステップ**でアクセストークンを取得する認可フロー。

```
1. Authorization Request（認可リクエスト）
   ↓
2. User Authentication（ユーザー認証）
   ↓
3. Token Request（トークンリクエスト）
```

**RFC 6749 Section 4.1準拠**

### なぜこのフローが必要なのか？

**セキュリティ上の理由**:
- ✅ クライアントシークレットをブラウザに露出しない
- ✅ アクセストークンがURLに含まれない（ブラウザ履歴に残らない）
- ✅ 認可コードは1回限り使用可能（リプレイ攻撃防止）

**使用場面**:
- Webアプリケーション（サーバーサイドで動作）
- SPAアプリケーション（PKCE併用）
- モバイルアプリ（PKCE必須）

---

## 実装アーキテクチャ全体像

### 30秒で理解する全体像

```
HTTPリクエスト
    ↓
Controller (OAuthV1Api) - HTTP処理
    ↓
EntryService (OAuthFlowEntryService) - トランザクション管理
    ↓
Core層 (OAuthProtocol) - OAuth仕様準拠ロジック
    ↓
Repository - データ永続化
```

### 主要クラスの責務

| クラス | 役割 | 主な処理 |
|--------|------|---------|
| **OAuthV1Api** | HTTPエンドポイント | パラメータ受け取り、レスポンス返却 |
| **OAuthFlowEntryService** | オーケストレーション | トランザクション、イベント発行 |
| **OAuthProtocol** | OAuth仕様実装 | 検証、AuthorizationRequest作成、Code発行 |
| **OAuthRequestHandler** | リクエスト処理 | Validator、Verifier実行 |
| **OAuthAuthorizeHandler** | Code発行処理 | 認証確認、Authorization Code生成 |

### 主要ドメインオブジェクト

| オブジェクト | 説明 | 保存場所 | 有効期限 |
|-------------|------|---------|---------|
| **AuthorizationRequest** | 認可リクエスト情報 | DB | 認証完了後削除 |
| **AuthenticationTransaction** | 認証トランザクション | DB | 認証完了後削除 |
| **AuthorizationCodeGrant** | 認可コード | DB | 10分（使用後即削除） |
| **OAuthSession** | セッション情報（SSO用） | Redis | max_age設定による |

---

### Core層の詳細アーキテクチャ

Phase 1（Authorization Request）のCore層処理：

```
OAuthProtocol.request()
    ↓
┌─────────────────────────────────────────────────────┐
│ OAuthRequestHandler                                 │
├─────────────────────────────────────────────────────┤
│  1. OAuthRequestValidator（入力形式チェック）         │
│     - client_id必須                                 │
│     - パラメータ重複禁止                              │
│                                                     │
│  2. 設定取得                                         │
│     - AuthorizationServerConfiguration取得          │
│     - ClientConfiguration取得                       │
│                                                     │
│  3. OAuthRequestContext作成                         │
│     - パラメータ + 設定を統合                         │
│                                                     │
│  4. OAuthRequestVerifier（ビジネスルール検証）        │
│     ├─ Base Verifier（OAuth2/OIDC）                 │
│     │   - response_type検証                         │
│     │   - redirect_uri検証                          │
│     │   - scope検証                                 │
│     │   - nonce必須チェック（OIDC）                  │
│     │                                               │
│     └─ Extension Verifiers（Plugin）                │
│         - RequestObjectVerifier（JWT署名検証）       │
│         - AuthorizationDetailsVerifier              │
│         - JarmVerifier                              │
│                                                     │
│  5. AuthorizationRequest生成                        │
│     - AuthorizationRequestIdentifier（UUID）        │
│     - response_type, client_id, scope等             │
│                                                     │
│  6. AuthorizationRequest保存（DB）                   │
│     - AuthorizationRequestRepository.register()    │
└─────────────────────────────────────────────────────┘
```

**実装**: [OAuthRequestHandler.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/handler/OAuthRequestHandler.java)

---

Phase 3（Authorization Approve）のCore層処理：

```
OAuthProtocol.authorize()
    ↓
┌─────────────────────────────────────────────────────┐
│ OAuthAuthorizeHandler                               │
├─────────────────────────────────────────────────────┤
│  1. OAuthAuthorizeRequestValidator                  │
│     - AuthorizationRequestIdentifier必須            │
│     - User存在確認                                   │
│     - Authentication存在確認                         │
│                                                     │
│  2. AuthorizationRequest取得                        │
│     - AuthorizationRequestRepository.get()          │
│                                                     │
│  3. 設定取得                                         │
│     - AuthorizationServerConfiguration取得          │
│     - ClientConfiguration取得                       │
│                                                     │
│  4. OAuthAuthorizeContext作成                       │
│     - AuthorizationRequest + User + Authentication  │
│                                                     │
│  5. AuthorizationResponseCreator選択（Plugin）       │
│     - response_type="code" → CodeResponseCreator    │
│     - response_type="token" → TokenResponseCreator  │
│                                                     │
│  6. AuthorizationResponse生成                       │
│     - Authorization Code生成（UUID）                │
│     - redirect_uri + "?code=xxx&state=yyy"          │
│                                                     │
│  7. AuthorizationCodeGrant保存                      │
│     - code, expiresAt（10分）                       │
│     - authorizationGrant（user, scope等）           │
│     - DB保存                                        │
│                                                     │
│  8. OAuthSession保存                                │
│     - user, authentication                          │
│     - sessionKey: tenant-id:client-id               │
│     - SSO用（次回の自動ログイン）                     │
└─────────────────────────────────────────────────────┘
```

**実装**: [OAuthAuthorizeHandler.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/handler/OAuthAuthorizeHandler.java)

---

## 3つのフェーズの処理フロー

### Phase 1: Authorization Request（認可リクエスト）

**目的**: ユーザーをログイン画面にリダイレクト

```
GET /{tenant-id}/v1/authorizations?response_type=code&client_id=xxx&redirect_uri=...
    ↓
OAuthFlowEntryService.request()
    ├─ Tenant取得
    ├─ OAuthProtocol.request() 呼び出し
    │   ├─ パラメータ検証（Validator）
    │   ├─ クライアント検証（Verifier）
    │   ├─ AuthorizationRequest生成
    │   └─ DB保存
    │
    └─ AuthenticationTransaction作成
        └─ 認証ポリシー設定（MFA必須？等）

→ レスポンス: ログイン画面URL
```

**保存されるデータ**:
- `AuthorizationRequest`: client_id, redirect_uri, scope等
- `AuthenticationTransaction`: 認証状態管理（status: PENDING）

---

### View Data API（認可画面データ取得）

**目的**: 認可画面（SPA）がクライアント情報・スコープ情報等を取得し、UIを構築する

Phase 1完了後、ログイン画面にリダイレクトされたSPAが認可画面を描画するために呼び出します。

```
GET /{tenant-id}/v1/authorizations/{authReqId}/view-data
    ↓
OAuthFlowEntryService.getViewData()
    ├─ AuthorizationRequest取得
    ├─ ClientConfiguration取得
    ├─ OPSession取得（SSO判定用）
    └─ OAuthViewDataCreator.create()
        ├─ クライアント情報抽出（client_id, client_name, logo_uri等）
        ├─ スコープ一覧
        ├─ セッション有効判定
        ├─ 利用可能フェデレーション一覧
        ├─ カスタムパラメータ
        └─ client_custom_properties（設定時のみ）

→ レスポンス: 認可画面描画用データ（JSON）
```

**レスポンス例**:
```json
{
  "client_id": "client123",
  "client_name": "サンプルアプリケーション",
  "client_uri": "https://client.example.com",
  "logo_uri": "https://client.example.com/logo.png",
  "contacts": ["support@client.example.com"],
  "tos_uri": "https://client.example.com/terms",
  "policy_uri": "https://client.example.com/privacy",
  "scopes": ["openid", "profile", "email"],
  "session_enabled": false,
  "available_federations": [],
  "custom_params": {},
  "client_custom_properties": {
    "app_label": "my-custom-app",
    "feature_flags": { "dark_mode": true }
  }
}
```

**主要フィールド**:

| フィールド | 説明 |
|-----------|------|
| `client_id` / `client_name` | クライアント識別情報 |
| `logo_uri` / `tos_uri` / `policy_uri` | 認可画面に表示するクライアントメタデータ |
| `scopes` | 要求されているスコープ一覧 |
| `session_enabled` | 既存セッションで認証スキップ可能か（SSO判定） |
| `available_federations` | 利用可能な外部IdP連携（Google、Azure AD等） |
| `custom_params` | 認可リクエストのカスタムパラメータ |
| `client_custom_properties` | クライアントのextension.custom_propertiesに設定した任意データ（未設定時は含まれない） |

`session_enabled`が`true`の場合、SPAは再認証をスキップして直接`/authorize`を呼び出すことができます。

**実装**: [OAuthViewDataCreator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/view/OAuthViewDataCreator.java)

---

### Phase 2: User Authentication（ユーザー認証）

**目的**: ユーザー本人確認

```
POST /{tenant-id}/v1/authorizations/{authReqId}/password
{
  "username": "user@example.com",
  "password": "secret"
}
    ↓
OAuthFlowEntryService.interact()
    ├─ AuthorizationRequest取得
    ├─ AuthenticationTransaction取得
    ├─ AuthenticationInteractor選択
    │   └─ PasswordAuthenticationInteractor等
    │
    ├─ AuthenticationInteractor.interact()
    │   ├─ パスワード検証
    │   └─ AuthenticationResult生成
    │
    ├─ AuthenticationTransaction更新
    │   └─ status: AUTHENTICATED
    │   └─ subject: userId
    │
    └─ OAuthSession更新
        └─ 認証情報保存

→ レスポンス: 認証完了
```

**詳細**: [04-authentication.md](./04-authentication.md)

**実装**: [OAuthFlowEntryService.java:164](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L164)

---

### Phase 3: Authorization Approve（認可承認）

**目的**: Authorization Code発行とユーザー登録

```
POST /{tenant-id}/v1/authorizations/{authReqId}/authorize
    ↓
OAuthFlowEntryService.authorize()
    ├─ Tenant取得
    ├─ OAuthProtocol取得
    ├─ AuthorizationRequest取得
    ├─ AuthenticationTransaction取得
    │   ├─ user取得（既にAuthenticationTransactionに含まれる）
    │   ├─ deniedScopes取得
    │   └─ isSuccess()判定 → authentication or null
    │
    ├─ OAuthAuthorizeRequest作成
    │
    └─ OAuthProtocol.authorize() 呼び出し
        └─ OAuthAuthorizeHandler.handle()
            ├─ Validation（AuthorizationRequestIdentifier・User・Authentication）
            ├─ AuthorizationRequest取得（DB）
            ├─ ClientConfiguration・AuthorizationServerConfiguration取得
            ├─ OAuthAuthorizeContext作成
            ├─ AuthorizationResponseCreator選択
            │   ├─ response_type=code → CodeResponseCreator
            │   ├─ response_type=token → TokenResponseCreator
            │   └─ response_type=id_token token → HybridResponseCreator
            │
            ├─ AuthorizationResponse生成
            │   ├─ Authorization Code生成（UUID）
            │   └─ redirect_uri + "?code=abc123&state=xyz"
            │
            ├─ hasAuthorizationCode() → AuthorizationCodeGrant保存（DB）
            │   - code: "abc123..."
            │   - expiresAt: 現在時刻+10分
            │   - authorizationRequestIdentifier
            │   - authorizationGrant（user, scope, clientId等）
            │
            ├─ hasAccessToken() → OAuthToken保存（DB）
            │   ※ Implicit/Hybrid Flowの場合
            │
            └─ OAuthSession作成・登録（SSO用）
                - user, authentication
                - sessionKey: tenant-id:client-id

    ├─ (authorize成功の場合)
    │   ├─ userRegistrator.registerOrUpdate() - ユーザー登録/更新
    │   ├─ 招待完了イベント発行（invitation_idがあれば）
    │   ├─ authenticationTransaction削除（DB）
    │   └─ oauth_authorizeイベント発行
    │
    └─ (authorize失敗の場合)
        └─ authorize_failureイベント発行

→ レスポンス: 302 Redirect
```

**実装**:
- [OAuthFlowEntryService.java:283-346](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L283-L346)
- [OAuthAuthorizeHandler.java:77-133](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/handler/OAuthAuthorizeHandler.java#L77-L133)

**保存されるデータ**:
- `AuthorizationCodeGrant`: code, expiresAt, authorizationRequestIdentifier, authorizationGrant
  - **注意**: `used`フィールドは存在しない（使用後即削除する設計）

---

## リクエストパラメータとバリデーション

### 必須パラメータ

| パラメータ | 必須 | 説明 | 例 |
|-----------|------|------|---|
| `response_type` | ✅ | レスポンスタイプ（`code`固定） | `code` |
| `client_id` | ✅ | クライアントID | `my-web-app` |
| `redirect_uri` | 条件付き | リダイレクトURI（複数登録時は必須） | `https://app.example.com/callback` |
| `scope` | ✅ | アクセススコープ | `openid profile email` |
| `state` | 推奨 | CSRF対策用ランダム値 | `random-xyz-123` |
| `nonce` | 推奨 | リプレイ攻撃対策（OIDC） | `nonce-abc-456` |

**RFC 6749 Section 4.1.1準拠**

---

### バリデーションアーキテクチャ

**実装**: [OAuthRequestHandler.java:115-139](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/handler/OAuthRequestHandler.java#L115-L139)

```
OAuthRequest（パラメータ）
    ↓
┌──────────────────────────────────────┐
│ OAuthRequestValidator                │ ステップ1: 入力形式チェック
│  - client_id必須チェック               │
│  - パラメータ重複禁止                   │
└──────────────────────────────────────┘
    ↓ OK
設定取得
    ├─ AuthorizationServerConfiguration （サーバー設定）
    └─ ClientConfiguration              （クライアント設定）
    ↓
OAuthRequestContext作成
    ↓
┌──────────────────────────────────────┐
│ OAuthRequestVerifier                 │ ステップ2: ビジネスルール検証
│  ├─ Base Verifier選択                │
│  │   ├─ OAuth2RequestVerifier        │ ← OAuth 2.0用
│  │   └─ OidcRequestVerifier           │ ← OIDC用
│  │                                    │
│  └─ Extension Verifiers（順次実行）    │
│      ├─ RequestObjectVerifier        │ ← Request Object (JWT)
│      ├─ AuthorizationDetailsVerifier │ ← Authorization Details
│      └─ JarmVerifier                 │ ← JARM
└──────────────────────────────────────┘
    ↓ OK
AuthorizationRequest保存
```

---

### Validator: 入力形式チェック

**実装**: [OAuthRequestValidator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/validator/OAuthRequestValidator.java)

**検証内容**:
1. **client_id必須チェック**
2. **パラメータ重複禁止**（RFC 6749 Section 3.1）

```java
// client_id必須
if (!oAuthRequestParameters.hasClientId()) {
  throw new OAuthBadRequestException(
    "invalid_request",
    "authorization request must contains client_id"
  );
}

// 同じパラメータが複数回指定されていないか
List<String> duplicateKeys = oAuthRequestParameters.multiValueKeys();
if (!duplicateKeys.isEmpty()) {
  throw new OAuthBadRequestException(
    "invalid_request",
    "authorization request must not contains duplicate value"
  );
}
```

**例外**: `OAuthBadRequestException` → **エラーページ表示**（リダイレクトしない）

---

### Verifier: プラグインによる段階的検証

**実装**: [OAuthRequestVerifier.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/verifier/OAuthRequestVerifier.java)

**プラグインアーキテクチャ**:
```
OAuthRequestVerifier.verify(context)
    ↓
┌─────────────────────────────────────────────┐
│ 1. Base Verifier選択（プロファイル別）           │
├─────────────────────────────────────────────┤
│  AuthorizationProfile.OAUTH2                │
│    → OAuth2RequestVerifier                  │
│       ├─ redirect_uri検証                   │
│       └─ OAuthRequestBaseVerifier           │
│           ├─ response_type検証              │
│           ├─ サーバー対応チェック             │
│           ├─ クライアント対応チェック          │
│           └─ scope検証                      │
│                                             │
│  AuthorizationProfile.OIDC                  │
│    → OidcRequestVerifier                    │
│       ├─ nonce必須チェック                   │
│       └─ OidcRequestBaseVerifier            │
│           └─ OIDC固有の検証                 │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 2. Extension Verifiers（該当時のみ実行）       │
├─────────────────────────────────────────────┤
│  RequestObjectVerifier                      │
│    条件: request または request_uri 存在     │
│    検証: JWT署名検証、パラメータ整合性        │
│                                             │
│  AuthorizationDetailsVerifier               │
│    条件: authorization_details パラメータ存在 │
│    検証: JSON形式、必須フィールド             │
│                                             │
│  JarmVerifier                               │
│    条件: response_mode=jwt                  │
│    検証: JARM設定の妥当性                    │
└─────────────────────────────────────────────┘
```

**プラグインローダー**:
- `AuthorizationRequestVerifierPluginLoader.load()` - Base Verifier拡張
- `AuthorizationRequestExtensionVerifierPluginLoader.load()` - Extension Verifier拡張

---

### OAuth2RequestVerifier の検証詳細

**実装**: [OAuth2RequestVerifier.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/verifier/OAuth2RequestVerifier.java)

```
OAuth2RequestVerifier.verify(context)
    ↓
┌────────────────────────────────────────────┐
│ redirect_uri検証（RFC 6749 Section 3.1.2） │
├────────────────────────────────────────────┤
│  redirect_uri指定あり？                     │
│    YES                                     │
│    ├─ フラグメント（#）含む？               │
│    │   YES → エラー                         │
│    │                                        │
│    └─ 登録URIと完全一致？                   │
│        NO → エラー                          │
│                                            │
│    NO                                      │
│    └─ 登録URIが複数？                       │
│        YES → エラー（redirect_uri必須）      │
└────────────────────────────────────────────┘
    ↓
┌────────────────────────────────────────────┐
│ OAuthRequestBaseVerifier（共通検証）        │
├────────────────────────────────────────────┤
│  1. response_type必須？                     │
│  2. response_typeが既知の値？               │
│  3. サーバーがサポート？                     │
│  4. クライアントが使用可能？                 │
│  5. scopeが有効？                           │
└────────────────────────────────────────────┘
```

**検証ロジックのポイント**:
- redirect_uri検証が**最優先**（RFC 6749 Section 3.1.2.4準拠）
- redirect_uri不正 → `OAuthBadRequestException` → **リダイレクトしない**
- その他のエラー → `OAuthRedirectableBadRequestException` → **エラーをリダイレクト**

---

### Validator vs Verifier の違い（実装ベース）

| 観点 | OAuthRequestValidator | OAuthRequestVerifier                                       |
|------|---------------------|------------------------------------------------------------|
| **実行タイミング** | リクエスト受信直後 | Context作成後                                                 |
| **必要な情報** | リクエストパラメータのみ | Tenant設定・クライアント設定                                          |
| **検証内容** | 形式チェック（client_id存在、重複なし） | ビジネスルール（redirect_uri一致、scope有効性）                           |
| **例外型** | `OAuthBadRequestException` | `OAuthRedirectableBadRequestException`                     |
| **エラー処理** | エラーページ表示 | リダイレクトURIの検討でエラーの場合はエラーページ<br/>リダイレクトURIの検証後はリダイレクトにエラーを返却 |

**RFC 6749 Section 3.1.2.4の実装**:
> redirect_uriが無効な場合は、**リダイレクトしてはいけない**（セキュリティ理由）

---

## SSO（シングルサインオン）可能タイミング

### SSO（prompt=none）が利用可能になるタイミング

**重要**: idp-serverでは、SSOは**認可承認時点**（Phase 3完了時）から利用可能になります。

```
Phase 1: Authorization Request
    ↓
Phase 2: User Authentication
    ↓
Phase 3: Authorization Approve ← この時点でSSO可能になる
    ↓
Phase 4: Token Request（SSOにはこのステップは不要）
```

### 技術的な仕組み

**AuthorizationGranted登録タイミング**:
- `AuthorizationGranted`（認可同意記録）は**認可承認時**（`OAuthAuthorizeHandler`）に登録される
- これにより、Token Request前でも`prompt=none`によるSSO認可が可能

```
認可承認完了
    ↓
AuthorizationGranted登録（DB）← SSO可能に
    ↓
Token Request（省略可能）
    ↓
別のクライアントから prompt=none でSSO認可可能
```

### SSO利用条件

`prompt=none`でSSO認可が成功するための条件:
1. ✅ 同一ユーザーで過去に認可完了済み（AuthorizationGranted存在）
2. ✅ 有効なOAuthSession存在（セッション期限内）
3. ✅ 要求スコープが既認可スコープに含まれる

### 他IdPとの比較

| IdP | SSO可能タイミング |
|-----|-----------------|
| **idp-server** | 認可承認時（Token Request前） |
| **Keycloak** | 認可承認時（Token Request前） |
| **Auth0** | 認可承認時（Token Request前） |

**設計理由**: Token Request前にSSOを可能にすることで、ユーザー体験を向上させる。ユーザーが認可を完了した時点で、別のクライアントからもスムーズにログインできる。

**実装**: [OAuthAuthorizeHandler.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/handler/OAuthAuthorizeHandler.java)

---

## データのライフサイクル

### Authorization Code の一生

```
1. 発行 (authorize時)
   └─ DB保存、expiresAt=現在時刻+10分（デフォルト）
      - 設定: AuthorizationServerConfiguration.authorizationCodeValidDuration()
      - デフォルト値: 600秒（10分）

2. 使用 (Token Request時)
   ├─ 存在確認 ✅
   ├─ 有効期限チェック ✅
   ├─ クライアント一致チェック ✅
   ├─ redirect_uri一致チェック ✅
   └─ Access Token発行

3. 削除
   └─ Token Request成功後に即座に削除（使用後即削除）

⚠️ 重要:
- 1回限り使用可能（使用後即削除）
- usedフィールドは存在しない（削除による実装）
- 10分以内に使用しないと期限切れ→invalid_grant
```

**実装**: [AuthorizationServerExtensionConfiguration.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/AuthorizationServerExtensionConfiguration.java) - `authorizationCodeValidDuration = 600`（秒）

### AuthorizationRequest の一生

```
1. 作成 (request時)
   └─ PostgreSQL/MySQLの`authorization_request`テーブルに保存
      - expires_at: 現在時刻 + 30分（デフォルト、設定変更可能）
      - 設定: AuthorizationServerConfiguration.oauthAuthorizationRequestExpiresIn()

2. 認証中
   └─ AuthenticationTransactionで状態管理
      - status: PENDING → AUTHENTICATED

3. Token Request
   └─ Authorization Code交換時に即座に削除
      - AuthorizationCodeGrantRepository.delete()
      - AuthorizationRequestRepository.delete()
      - 実装: AuthorizationCodeGrantService.java:200-201

4. 期限切れデータのクリーンアップ
   └─ 手動クリーンアップAPIで削除
      POST /v1/admin/operations/cleanup
      {"max_deletion_number": 10000}

      ⚠️ 注意: 自動削除機能は未実装
      期限切れデータは手動でクリーンアップする必要あり
```

**実装**:
- 保存: [AuthorizationRequestDataSource.java:36-38](../../../../libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource/oidc/request/AuthorizationRequestDataSource.java#L36-L38)
- 削除: [AuthorizationCodeGrantService.java:200-201](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/AuthorizationCodeGrantService.java#L200-L201)
- 有効期限設定: [AuthorizationServerExtensionConfiguration.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/AuthorizationServerExtensionConfiguration.java) - `oauthAuthorizationRequestExpiresIn = 1800`（秒）

---

## 実際に動かしてみる

### 前提条件

1. **テナント作成済み**
```bash
TENANT_ID="18ffff8d-8d97-460f-a71b-33f2e8afd41e"
```

2. **クライアント登録済み**
```bash
CLIENT_ID="my-web-app"
CLIENT_SECRET="secret-12345"
REDIRECT_URI="http://localhost:3000/callback"
```

3. **ユーザー登録済み**
```bash
USERNAME="test@example.com"
PASSWORD="password123"
```

### Step-by-Step実行

#### 1. Authorization Request（ブラウザで実行）

```
http://localhost:8080/${TENANT_ID}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=random-state-xyz
```

**期待される動作**:
- ログイン画面にリダイレクトされる
- URLに`authorization_request_id`が含まれる

**実際のURL例**:
```
http://localhost:8080/signin/index.html?id=abc-123-def&tenant_id=18ffff8d-8d97-460f-a71b-33f2e8afd41e
```

#### 2. User Authentication（curlで実行）

```bash
AUTH_REQUEST_ID="abc-123-def"  # 上記URLから取得

curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_REQUEST_ID}/password" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test@example.com",
    "password": "password123"
  }'
```

**成功レスポンス**:
```json
{
  "status": "authenticated",
  "next_step": "authorize"
}
```

#### 3. Authorization Approve（curlで実行）

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_REQUEST_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}' \
  -i  # ヘッダーも表示
```

**成功レスポンス**:
```
HTTP/1.1 302 Found
Location: http://localhost:3000/callback?code=eyJhbGc...&state=random-state-xyz
```

#### 4. Authorization Code抽出

```bash
# Locationヘッダーから codeパラメータを抽出
CODE="eyJhbGc..."
```

#### 5. Token Request

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=${CODE}&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}&redirect_uri=${REDIRECT_URI}"
```

**成功レスポンス**:
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

### デバッグのヒント

**AuthorizationRequestの確認**:
```bash
# データベースで直接確認（開発環境のみ）
docker exec -it postgres psql -U idp_user -d idp_db -c \
  "SELECT id, client_id, redirect_uri, scope, created_at FROM authorization_request WHERE tenant_id='${TENANT_ID}' ORDER BY created_at DESC LIMIT 5;"
```

**ログ確認**:
```bash
# idp-serverのログを監視
docker logs -f idp-server | grep -E "OAuthFlowEntryService|AuthorizationRequest"
```

---

## PAR（Pushed Authorization Request）

**RFC 9126準拠の拡張機能** - セキュリティ強化版Authorization Request

### 通常のAuthorization Requestとの違い

| 項目 | 通常のGET | PAR（POST） |
|------|----------|-----------|
| **送信方法** | URLパラメータ | POSTボディ |
| **エンドポイント** | `GET /v1/authorizations` | `POST /v1/authorizations/push` |
| **セキュリティ** | パラメータがURL露出 | バックチャネルで安全 |
| **サイズ制限** | URLサイズ制限あり | 制限なし |
| **FAPI要件** | - | FAPI 2.0で必須 |

### PAR使用フロー

```
Step 1: PAR実行
POST /{tenant-id}/v1/authorizations/push
{
  "response_type": "code",
  "client_id": "xxx",
  "redirect_uri": "https://...",
  "scope": "openid profile"
}
    ↓
レスポンス:
{
  "request_uri": "urn:ietf:params:oauth:request_uri:abc123",
  "expires_in": 90
}

Step 2: request_uriで認可リクエスト
GET /{tenant-id}/v1/authorizations?client_id=xxx&request_uri=urn:ietf:params:oauth:request_uri:abc123
    ↓
（通常のフローと同じ）
```

---

## E2Eテスト例

**参考ファイル**: `e2e/src/tests/scenario/application/scenario-02-sso-oidc.test.js`

以下は教育用の簡略化した例です。実際のテストコードはより複雑な設定を含みます。

```javascript
const { v4: uuidv4 } = require('uuid');
const axios = require('axios');

describe('Authorization Code Flow', () => {
  let tenantId = '18ffff8d-8d97-460f-a71b-33f2e8afd41e';
  let clientId = 'test-client';
  let redirectUri = 'https://client.example.com/callback';

  test('should complete authorization code flow', async () => {
    // 1. Authorization Request
    const authResponse = await axios.get(
      `http://localhost:8080/${tenantId}/v1/authorizations`,
      {
        params: {
          response_type: 'code',
          client_id: clientId,
          redirect_uri: redirectUri,
          scope: 'openid profile email',
          state: 'random-state'
        },
        maxRedirects: 0,
        validateStatus: (status) => status === 302
      }
    );

    // ログイン画面URLにリダイレクト
    expect(authResponse.status).toBe(302);
    expect(authResponse.headers.location).toContain('/signin/index.html');

    // authorization_request_id抽出
    const authReqId = new URL(authResponse.headers.location, 'http://localhost').searchParams.get('id');

    // 2. User Authentication（別テストで実施）
    await axios.post(
      `http://localhost:8080/${tenantId}/v1/authentications/${authReqId}`,
      {
        type: 'password',
        username: 'user@example.com',
        password: 'password123'
      }
    );

    // 3. Authorization Approve
    const approveResponse = await axios.post(
      `http://localhost:8080/${tenantId}/v1/authorizations/${authReqId}/approve`,
      {},
      {
        maxRedirects: 0,
        validateStatus: (status) => status === 302
      }
    );

    // クライアントにリダイレクト（Authorization Code付き）
    expect(approveResponse.status).toBe(302);
    const redirectUrl = new URL(approveResponse.headers.location);
    expect(redirectUrl.origin + redirectUrl.pathname).toBe(redirectUri);

    const code = redirectUrl.searchParams.get('code');
    expect(code).toBeTruthy();

    // 4. Token Request（次のガイド参照）
    const tokenResponse = await axios.post(
      `http://localhost:8080/${tenantId}/v1/tokens`,
      new URLSearchParams({
        grant_type: 'authorization_code',
        code: code,
        client_id: clientId,
        client_secret: 'client-secret',
        redirect_uri: redirectUri
      }),
      {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      }
    );

    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty('access_token');
    expect(tokenResponse.data).toHaveProperty('id_token');
    expect(tokenResponse.data).toHaveProperty('token_type', 'Bearer');
  });
});
```

---

## よくあるエラーと対処法

### エラー1: `invalid_request` - redirect_uri不一致

**実際のエラー**:
```
GET /{tenant-id}/v1/authorizations?response_type=code&client_id=my-app&redirect_uri=https://wrong-domain.com/callback
    ↓
302 Redirect to エラー画面
または
https://registered-uri.com/callback?error=invalid_request&error_description=redirect_uri+does+not+match+registered+URIs
```

**原因**: クライアント登録時の`redirect_uris`と一致しない

**解決策**:
```bash
# 1. 登録済みredirect_uriを確認
curl -X GET "http://localhost:8080/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.redirect_uris'

# 出力例:
# ["https://app.example.com/callback", "http://localhost:3000/callback"]

# 2. 完全一致するURIを使用（パス、ポート、プロトコルすべて一致必須）
redirect_uri=https://app.example.com/callback  # ✅
redirect_uri=https://app.example.com/callback/ # ❌ 末尾スラッシュ違い
redirect_uri=http://app.example.com/callback   # ❌ httpとhttpsの違い
```

---

### エラー2: `unsupported_response_type` - 未サポートのresponse_type

**実際のエラー**:
```json
{
  "error": "unsupported_response_type",
  "error_description": "authorization server is unsupported response_type (token)"
}
```

**原因**:
- `response_type=token`（Implicit Flow）はセキュリティ理由で無効化されている
- または、クライアントタイプが許可していない

**解決策**:
```bash
# Authorization Code Flowを使用
response_type=code  # ✅ 推奨

# サーバー設定でサポートされているresponse_typeを確認
curl -X GET "http://localhost:8080/${TENANT_ID}/.well-known/openid-configuration" \
  | jq '.response_types_supported'

# 出力例:
# ["code", "code id_token", "code token", "code id_token token"]
```

---

### エラー3: `invalid_scope` - 無効なscope

**実際のエラー**:
```
302 Redirect to:
https://app.example.com/callback?error=invalid_scope&error_description=authorization+request+does+not+contains+valid+scope
```

**原因**:
- scopeパラメータが空
- サポートされていないscopeを指定

**解決策**:
```bash
# 1. 最低限必須のscope
scope=openid  # ✅ OIDCの場合は必須

# 2. 追加情報が必要な場合
scope=openid profile email  # ✅ スペース区切り

# 3. カスタムscopeの確認（テナント設定依存）
curl -X GET "http://localhost:8080/${TENANT_ID}/.well-known/openid-configuration" \
  | jq '.scopes_supported'
```

---

### エラー4: 認証未完了で`/authorize`実行

**実際のエラー**:
```json
{
  "error": "authentication_required",
  "error_description": "User authentication is not completed"
}
```

**原因**: Phase 2（認証）をスキップしてPhase 3（Code発行）を実行

**正しい順序**:
```bash
# ❌ 間違い
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_REQUEST_ID}/authorize"
# → エラー: authentication_required

# ✅ 正しい
# Step 1: 認証実行
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_REQUEST_ID}/password" \
  -d '{"username": "user@example.com", "password": "secret"}'

# Step 2: 認証完了後にauthorize実行
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_REQUEST_ID}/authorize"
```

---

### エラー5: Authorization Code期限切れ

**実際のエラー（Token Request時）**:
```json
{
  "error": "invalid_grant",
  "error_description": "authorization code is expired"
}
```

**原因**: Authorization Code発行から10分以上経過

**解決策**:
```bash
# Authorization Code発行後、すぐにToken Request実行
# 有効期限: 10分（デフォルト）

# タイムアウトした場合は、最初からやり直し
# → 再度Authorization Requestから実行
```

---

### エラー6: Authorization Code再利用

**実際のエラー**:
```json
{
  "error": "invalid_grant",
  "error_description": "not found authorization code"
}
```

**原因**: 同じAuthorization Codeで2回Token Request実行

**動作**:
```
1回目のToken Request: ✅ 成功（認可コード削除）
2回目のToken Request: ❌ エラー（invalid_grant）
```

**対処**:
```bash
# Authorization Codeは1回限り使用可能
# 再度必要な場合は、最初からAuthorization Flowを実行
```

---

## 次のステップ

✅ Authorization Code Flowの実装を理解した！

### 📖 次に読むべきドキュメント

1. [03. Token Flow実装](./03-token-endpoint.md) - トークン発行・検証
2. [04. Authentication実装](./04-authentication.md) - ユーザー認証

### 🔗 詳細情報

- [AI開発者向け: Core - OAuth](../../content_10_ai_developer/ai-11-core.md#oauth---oauth-20コア)
- [RFC 6749 Section 4.1](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1) - Authorization Code Grant

---

**情報源**: [OAuthFlowEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java)
**最終更新**: 2025-10-12
