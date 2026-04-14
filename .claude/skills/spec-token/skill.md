---
name: spec-token
description: トークン管理（Token Management）機能の開発・修正を行う際に使用。Access Token, Refresh Token, ID Token, Introspection, Revocation実装時に役立つ。
---

# トークン管理（Token Management）開発ガイド

## ドキュメント

- `documentation/docs/content_06_developer-guide/03-application-plane/03-token-endpoint.md` - トークンエンドポイント実装ガイド
- `documentation/docs/content_03_concepts/04-tokens-claims/concept-02-token-management.md` - トークン管理概念
- `documentation/docs/content_05_how-to/phase-2-security/02-token-strategy.md` - トークン有効期限パターン（4パターン、クライアントレベルオーバーライド）
- `documentation/docs/content_07_reference/transaction-expiration-settings.md` - トランザクションデータの有効期限設定リファレンス
- `documentation/docs/content_06_developer-guide/05-configuration/client.md` - Client設定ガイド（Extension設定のトークン関連項目）

## 機能概要

トークン管理は、Access Token/Refresh Token/ID Tokenの発行・検証・取消を行う層。
- **トークン発行**: Authorization Code, Refresh Token, Client Credentials各グラント対応
- **Token Introspection（RFC 7662）**: トークン検証
- **Token Revocation（RFC 7009）**: トークン取消
- **ID Token発行**: OpenID Connect対応

## モジュール構成

```
libs/
├── idp-server-core/                         # トークンコア
│   └── .../token/
│       ├── handler/token/
│       │   └── TokenRequestHandler.java    # トークンリクエスト処理
│       ├── service/
│       │   ├── OAuthTokenCreationServices.java
│       │   ├── AuthorizationCodeGrantService.java
│       │   ├── RefreshTokenGrantService.java
│       │   └── ClientCredentialsGrantService.java
│       ├── OAuthToken.java                 # トークン表現
│       ├── repository/
│       │   ├── OAuthTokenCommandRepository.java
│       │   └── OAuthTokenQueryRepository.java
│       └── handler/
│           ├── tokenintrospection/
│           │   └── TokenIntrospectionHandler.java
│           └── tokenrevocation/
│               └── TokenRevocationHandler.java
│
├── idp-server-core-adapter/                 # アダプター（キャッシュ含む）
│   └── .../datasource/token/
│       ├── OAuthTokenCacheKeyBuilder.java   # キャッシュキー生成（共有）
│       ├── OAuthTokenCacheStoreResolver.java # TOKEN_CACHE_ENABLED制御
│       ├── command/
│       │   └── OAuthTokenCommandDataSource.java  # 削除時キャッシュ連動
│       └── query/
│           └── OAuthTokenQueryDataSource.java    # Introspection時キャッシュ
│
└── idp-server-control-plane/               # 管理API
    └── .../management/token/
        └── TokenConfigManagementApi.java
```

## トークン発行

`idp-server-core/token/handler/token/TokenRequestHandler.java` 内の実際のメソッドシグネチャ:

```java
public class TokenRequestHandler {

    OAuthTokenCreationServices oAuthTokenCreationServices;

    public TokenRequestResponse handle(
        TokenRequest tokenRequest,
        PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate,
        TokenUserFindingDelegate tokenUserFindingDelegate
    ) {
        // Grant typeに応じた処理はOAuthTokenCreationServicesが管理
        // AuthorizationCodeGrantService
        // RefreshTokenGrantService
        // ClientCredentialsGrantService
        // ...
    }
}
```

**注意**: Grant type別の処理は、OAuthTokenCreationServices経由で各Serviceに委譲されます。

## Grant type別サービス

| サービス | 役割 |
|---------|------|
| `AuthorizationCodeGrantService` | Authorization Code Grant処理 |
| `RefreshTokenGrantService` | Refresh Token Grant処理 |
| `ClientCredentialsGrantService` | Client Credentials Grant処理 |
| `JwtBearerGrantService` | JWT Bearer Grant処理（RFC 7523） |

## JWT Bearer Grant（RFC 7523）

JWT Bearer Grantは、JWTアサーションを使用してアクセストークンを直接取得するグラントタイプです。

### 概要

- **Grant Type**: `urn:ietf:params:oauth:grant-type:jwt-bearer`
- **用途**: 外部IdPトークン交換、デバイス認証によるトークン取得
- **仕様**: [RFC 7523](https://datatracker.ietf.org/doc/html/rfc7523)

### サポートするフェデレーションタイプ

| タイプ | 説明 | 署名検証 |
|-------|------|----------|
| `device` | デバイスシークレットによる認証 | HMAC（HS256/HS384/HS512） |
| 外部IdP | 外部OIDCプロバイダーのトークン | RSA/EC（jwks_uri取得） |

### クライアント設定

```json
{
  "grant_types": ["urn:ietf:params:oauth:grant-type:jwt-bearer"],
  "extension": {
    "available_federations": [
      {
        "issuer": "device",
        "type": "device",
        "jwt_bearer_grant_enabled": true
      },
      {
        "issuer": "https://accounts.google.com",
        "provider_id": "google",
        "type": "oidc",
        "jwks_uri": "https://www.googleapis.com/oauth2/v3/certs",
        "jwt_bearer_grant_enabled": true
      }
    ]
  }
}
```

### トークンリクエスト

```
POST /v1/tokens
Content-Type: application/x-www-form-urlencoded
Authorization: Basic <base64(client_id:client_secret)>

grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
&assertion=<JWT>
&scope=openid profile
```

### JWT Assertion構造（デバイスタイプ）

```json
// Header
{
  "alg": "HS256",
  "typ": "JWT"
}
// Payload
{
  "iss": "device:{deviceId}",
  "sub": "{deviceId}",
  "aud": "https://idp.example.com/{tenantId}",
  "jti": "unique-token-id",
  "iat": 1234567890,
  "exp": 1234571490
}
```

### ユーザー解決ロジック（subject_claim_mapping）

JWTの`sub`クレームからユーザーを解決する方法を設定できます。

| subject_claim_mapping | 動作 | デフォルト |
|----------------------|------|-----------:|
| `device_id` | `sub`をデバイスIDとして扱い、デバイス所有者を検索 | デバイスフェデレーション |
| `sub` | `sub`をidp-serverのユーザーIDとして直接検索 | - |
| `email` | `sub`をメールアドレスとして検索 | - |
| (default) | `sub`を外部IdPのユーザー識別子として検索 | 外部IdPフェデレーション |

**外部IdPフェデレーションの動作**:
- JWTの`sub`は**外部IdPでのユーザー識別子**（idp-serverのユーザーIDではない）
- `findByExternalIdpSubject(tenant, subject, providerId)`で検索
- `providerId`はフェデレーション設定の`provider_id`から取得（未設定の場合は`issuer`にフォールバック）
- 事前に外部IdP連携（Federation）でユーザーが紐付けられている必要あり

**provider_id設定**:
```json
{
  "issuer": "https://accounts.google.com",
  "provider_id": "google",  // ユーザー検索時に使用（DDL制約対応）
  "type": "oidc"
}
```
- `provider_id`を明示的に設定することで、長いissuer URLではなく短い識別子でユーザー検索が可能
- DDLの`VARCHAR(255)`制約に対応
- 未設定の場合は`issuer`がそのまま使用される（後方互換性）

**セキュリティ上の利点（device_id方式）**:
- クライアントがユーザーIDを知る必要がない
- デバイスシークレット漏洩時も、そのデバイス所有者としてのみ認証可能
- 任意ユーザーへのなりすまし不可

### 関連ファイル

| ファイル | 役割 |
|---------|------|
| `JwtBearerGrantService.java` | JWT Bearer Grant処理メイン |
| `JwtBearerGrantValidator.java` | リクエスト検証 |
| `JwtBearerGrantVerifier.java` | JWTクレーム検証 |
| `JwtBearerUserFinder.java` | ユーザー解決ロジック |
| `JwtBearerUserFindingDelegate.java` | ユーザー検索インターフェース |

## Token Introspection（RFC 7662）

`idp-server-core/token/introspection/` 内:

トークンのメタデータを検証し、active/inactiveを返却します。

### Introspectionキャッシュ

`TOKEN_CACHE_ENABLED=true`（環境変数）かつRedis有効時、Introspection結果をRedisにキャッシュします。

- **パターン**: Cache-Aside（初回Introspection時にキャッシュ格納）
- **キー**: `oauth_token:at:{tenant_id}:{hmac(access_token)}`（`OAuthTokenCacheKeyBuilder`で生成）
- **TTL**: 60秒固定
- **デフォルト**: OFF（opt-in）。`OAuthTokenCacheStoreResolver`が`TOKEN_CACHE_ENABLED`を参照し、OFFの場合は`NoOperationCacheStore`を使用

### キャッシュ削除の連鎖

トークンが削除される全パターンでキャッシュも連動して削除されます:

| 削除トリガー | 処理 |
|-------------|------|
| **Token Revocation** | DB DELETE + キャッシュ削除 |
| **認可グラント削除**（管理API） | GrantRevocationService → deleteByUserAndClient → SELECT hashed tokens → キャッシュ削除 → DB DELETE |
| **ログアウト等の一括失効** | deleteByUserAndClient → SELECT hashed tokens → キャッシュ削除 → DB DELETE |
| **TTL経過** | キャッシュ自動削除（60秒） |

## Token Revocation（RFC 7009）

`idp-server-core/token/revocation/` 内:

トークンを無効化します。OAuthTokenCommandRepository.delete()を使用します。キャッシュが有効な場合、DB削除と同時にキャッシュも削除されます。

## Access Token タイプ（opaque vs JWT）

`authorization_server.extension.access_token_type` で制御（認可サーバーレベルの設定。クライアント単位のオーバーライドは不可）。

| タイプ | 形式 | 検証方法 | Revocation反映 |
|-------|------|---------|--------------|
| opaque（デフォルト） | ランダム文字列 | Introspectionエンドポイント必須 | 即時（DB削除） |
| JWT | `header.payload.signature` | JWKS署名検証でローカル検証可能 | 次回Introspection時（JWTは自己完結型のため即時反映されない） |

**使い分けの指針**:
- **opaque**: トークン情報を隠蔽したい場合、Revocationを即時反映したい場合
- **JWT**: リソースサーバーがIdPへの問い合わせなしにトークンを検証したい場合（パフォーマンス優先）

JWTの場合、ペイロードに `iss`, `sub`, `scope`, `client_id`, `exp` 等が含まれる。JWKSエンドポイント（`/v1/jwks`）で公開鍵を取得して署名検証する。Introspectionも引き続き動作する。

### カスタムクレームの扱いの違い

`AccessTokenCustomClaimsCreator` で追加されたカスタムクレーム（`claims:*` スコープマッピング、verified_claims等）は、ATタイプによって格納先が異なる。

| タイプ | ATの値に含まれるか | DBに保存 | Introspectionで返却 | セキュリティ |
|-------|-------------------|---------|-------------------|------------|
| opaque | **含まれない** | ✅ `AccessTokenCustomClaims` として保存 | ✅ | クレームが露出しない |
| JWT | **JWTペイロードに平文で含まれる** | ✅ | ✅ | ATを持つ者がクレームを読める |

**実装**: `AccessTokenCreator.issueAccessToken()` で `AccessTokenPayloadBuilder` にカスタムクレームを追加。opaque型の場合は `AccessTokenEntity` がランダム文字列になるためペイロードはATの値に含まれないが、`AccessTokenCustomClaims` としてDBに保存される。

**セキュリティ上の注意**: 機密情報（外部IdPトークン等）をカスタムクレームに含める場合、JWT ATでは平文で露出するため、opaque ATを使用するかJWEで暗号化する必要がある。

## リフレッシュトークン戦略

`RefreshTokenCreatable` が以下の4パターンで動作する。

| strategy | rotation | トークン値 | 有効期限 |
|----------|----------|-----------|---------|
| EXTENDS | true | **新しい** | **延長**（now + duration） |
| EXTENDS | false | 同じ | **延長**（now + duration） |
| FIXED | true | **新しい** | 同じ（初回発行時のまま） |
| FIXED | false | 同じ | 同じ（初回発行時のまま） |

### 設定

```json
{
  "extension": {
    "refresh_token_duration": 604800,
    "refresh_token_strategy": "FIXED",
    "rotate_refresh_token": true
  }
}
```

### ローテーション動作

- `rotate_refresh_token: true`: リフレッシュ時に新しいRTを発行し、旧RTを無効化する。旧RTでのリフレッシュはエラー
- `rotate_refresh_token: false`: 同じRTを繰り返し使える

### FIXED vs EXTENDS

- **FIXED**: RTの有効期限は最初の発行時点から固定。例: RT期限60秒で発行→50秒後にリフレッシュ→残り10秒で期限切れ
- **EXTENDS（SLIDING）**: リフレッシュするたびに有効期限が `now + duration` にリセットされる。アクティブなユーザーはログアウトされない

## トークン有効期限設定

認可サーバー設定（`authorization_server.extension`）でデフォルト値を設定し、クライアント設定で個別にオーバーライド可能。

| 設定 | デフォルト | 説明 |
|------|----------|------|
| `access_token_duration` | 3600 | AT有効期限（秒） |
| `id_token_duration` | 3600 | ID Token有効期限（秒）。`IdTokenCreator`が`now + duration`で`exp`を計算 |
| `refresh_token_duration` | 604800 | RT有効期限（秒） |
| `authorization_code_valid_duration` | 600 | 認可コード有効期限（秒）。RFC 6749推奨は最大10分 |
| `oauth_authorization_request_expires_in` | 1800 | 認可リクエスト（認証中コンテキスト）の有効期限。AUTH_SESSION cookieのmaxAgeと同じ値が使われる |

## id_token_strict_mode

`authorization_server.extension.id_token_strict_mode` で ID Token に含めるクレームを制御する。

| 条件 | ID Token | UserInfo |
|------|----------|----------|
| `false`（デフォルト） | scopeベースのクレームを含む（name, email等） | 含む |
| `true`, claims未指定 | `sub`, `iss`, `aud`, `exp`, `iat` のみ | 含む |
| `true`, `claims`パラメータで`essential: true` | 指定されたクレームを含む | 含む |
| `true`, `claims`パラメータで`voluntary`（essentialなし） | 含まない | 含む |

**`claims:*` カスタムスコープへの影響**: `id_token_strict_mode` はID Tokenのみに影響し、UserInfoには影響しない。`claims:*` カスタムクレームもUserInfoからは通常通り返却される。

## scopes_supported と claims_supported の動作の違い

| 設定 | 影響範囲 | 動作 |
|------|---------|------|
| `scopes_supported` | Discovery（`.well-known/openid-configuration`）の表示**のみ** | 実際のスコープ処理には影響しない。スコープのフィルタリングはクライアント設定の`scope`で行う |
| `claims_supported` | **Grant作成時のフィルタリング** | `GrantIdTokenClaims`/`GrantUserinfoClaims`の作成時に`claims_supported`に含まれないクレームを除外する。UserInfo/ID Tokenの両方に影響 |

**カスタムスコープとUserInfoの関係**: `api:read`等のリソースアクセス用スコープはUserInfoのクレームに影響しない。UserInfoで返るクレームは`profile`, `email`等のOIDC標準スコープで制御される。

## E2Eテスト

```
e2e/src/tests/
├── spec/
│   ├── rfc6749_token_endpoint_*.test.js     # OAuth 2.0 Token Endpoint
│   ├── rfc7009_token_revocation_*.test.js   # Token Revocation
│   ├── rfc7662_token_introspection_*.test.js # Token Introspection
│   ├── rfc7523_jwt_bearer_grant_*.test.js   # JWT Bearer Grant
│   └── oidc_core_*.test.js                  # OIDC関連トークンテスト
│
├── usecase/device-credential/
│   └── device-credential-04-device-secret-issuance.test.js  # デバイスシークレット+JWT Bearer
│
└── scenario/application/
    └── (トークン関連シナリオテスト)
```

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-core:compileJava

# テスト
cd e2e && npm test -- spec/rfc6749_token_endpoint_*.test.js
cd e2e && npm test -- spec/rfc7009_token_revocation_*.test.js
cd e2e && npm test -- spec/rfc7662_token_introspection_*.test.js
cd e2e && npm test -- spec/rfc7523_jwt_bearer_grant_*.test.js
cd e2e && npm test -- usecase/device-credential/device-credential-04-device-secret-issuance.test.js
```

## トラブルシューティング

### トークン発行失敗
- Grant typeが正しいか確認
- Authorization Code/Refresh Tokenが有効か確認
- 対応するGrantServiceが登録されているか確認

### Introspectionでinactive
- トークンが有効期限内か確認
- OAuthTokenが正しく保存されているか確認

### Introspectionキャッシュが効かない
- `TOKEN_CACHE_ENABLED=true`が環境変数に設定されているか確認
- `CACHE_ENABLE=true`（Redis自体）が有効か確認
- `OAuthTokenCacheStoreResolver`が`NoOperationCacheStore`を返していないか確認

### トークン失効後もIntrospectionでactiveが返る
- キャッシュTTL（60秒）以内の場合、古いキャッシュが返される可能性がある
- 通常はRevocation/削除時にキャッシュも連動削除されるため発生しない
- 直接DBを操作した場合のみ不整合が起きうる（最大60秒で自動解消）

### Revocationが失敗
- クライアント認証が成功しているか確認
- OAuthTokenCommandRepository.delete()が正しく呼ばれているか確認

### JWT Bearer Grant失敗

#### invalid_grant: Device federation not configured
- クライアント設定の`available_federations`に`type: "device"`が含まれているか確認
- `jwt_bearer_grant_enabled: true`が設定されているか確認

#### invalid_grant: Invalid JWT signature
- デバイスシークレットが正しいか確認
- JWTの`alg`がデバイス設定と一致しているか確認（HS256/HS384/HS512）
- 手動JWT生成の場合、署名エンコードが正しいか確認（Base64URL）

#### invalid_grant: User not found
- JWTの`sub`クレームにデバイスIDが設定されているか確認
- `subject_claim_mapping`の設定を確認（デフォルト: `device_id`）
- デバイスが正しくユーザーに紐付いているか確認

#### invalid_client
- クライアント認証方式が正しいか確認（client_secret_basic vs client_secret_post）
- AuthorizationヘッダーのBasic認証が正しくエンコードされているか確認
