# Token Exchange実装ガイド

## このドキュメントの目的

**Token Exchange（RFC 8693）** の実装を理解することが目標です。外部IdPが発行したトークンをidp-server発行のアクセストークンに交換する仕組みを扱います。

### 所要時間
⏱️ **約30分**

### 前提知識
- [03. Token Endpoint](./03-token-endpoint.md)
- [08. Federation](./08-federation.md)
- OAuth 2.0基礎知識

---

## Token Exchangeとは

外部IdPが発行したトークン（JWT / opaque）を、idp-serverのアクセストークンに交換する仕組み。IDサービス移行やサードパーティ連携で使用。

**RFC準拠**: OAuth 2.0 Token Exchange (RFC 8693)

**Grant Type**: `urn:ietf:params:oauth:grant-type:token-exchange`

**対応プロトコル**:
- ✅ **JWT署名検証** - JWKS による外部IdP署名検証
- ✅ **Token Introspection** - 外部IdP introspection endpoint によるopaque token検証
- ✅ **JIT Provisioning** - ユーザー自動作成・属性同期
- 🔜 **Delegation** - actor_token 対応（未実装）
- 🔜 **SAML** - SAML 1.1/2.0 アサーション（未対応）

---

## アーキテクチャ全体像

### 30秒で理解する全体像

```
HTTPリクエスト (POST /{tenant-id}/v1/tokens)
    ↓
Controller (TokenV1Api) - HTTP処理
    ↓
EntryService (TokenEntryService) - トランザクション管理
    ↓
Core層 (TokenRequestHandler)
    ├─ Validator: grant_type, subject_token, subject_token_type 検証
    ├─ クライアント認証
    ├─ TokenExchangeGrantService 選択（SPI）
    │    ├─ SubjectTokenVerifier: 検証方式に応じてJWT/Introspectionで検証
    │    ├─ resolveUser: ユーザー検索 or JIT Provisioning
    │    └─ issueToken: アクセストークン + リフレッシュトークン発行
    └─ issued_token_type 付きレスポンス生成
    ↓
Repository - トークン永続化
```

### 主要クラスの責務

| クラス | 層 | 役割 | 実装 |
|--------|---|------|------|
| **TokenV1Api** | Controller | HTTPエンドポイント | [TokenV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/token/TokenV1Api.java) |
| **TokenRequestHandler** | Core | トークンリクエスト処理 | [TokenRequestHandler.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/handler/token/TokenRequestHandler.java) |
| **TokenExchangeGrantService** | Core | Token Exchange処理 | [TokenExchangeGrantService.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/TokenExchangeGrantService.java) |
| **SubjectTokenVerifier** | Core | subject_token検証（JWT/Introspection） | [SubjectTokenVerifier.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/verifier/SubjectTokenVerifier.java) |
| **FederationJwtVerifier** | Core | 外部IdP JWT署名検証 | [FederationJwtVerifier.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/FederationJwtVerifier.java) |
| **ExternalTokenIntrospector** | Core | 外部IdP introspection呼び出し | [ExternalTokenIntrospector.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/ExternalTokenIntrospector.java) |
| **TokenExchangeGrantValidator** | Core | リクエストパラメータ検証 | [TokenExchangeGrantValidator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/validator/TokenExchangeGrantValidator.java) |
| **TokenExchangeGrantVerifier** | Core | JWTクレーム検証（iss, sub, aud, exp） | [TokenExchangeGrantVerifier.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/verifier/TokenExchangeGrantVerifier.java) |

### 主要ドメインオブジェクト

| オブジェクト | 説明 |
|-------------|------|
| **SubjectToken** | subject_tokenパラメータの値オブジェクト（JWTパース対応） |
| **SubjectTokenType** | subject_token_type の enum（access_token / id_token / jwt） |
| **SubjectTokenVerificationResult** | 検証結果（federation, subject, claims） |
| **IssuedTokenType** | レスポンス用 issued_token_type |
| **AvailableFederation** | 外部IdP Federation設定（JWKS, introspection, JIT等） |

---

## 対応状況一覧

| # | カテゴリ | ケース | 状況 | 備考 |
|---|---|---|---|---|
| A | subject_token 検証 | JWT署名検証（JWKS） | ✅ | `subject_token_type`: jwt / id_token / access_token |
| B | subject_token 検証 | Opaque token（Introspection） | ✅ | `token_exchange_token_verification_method: "introspection"` |
| C | subject_token 検証 | SAML 1.1 / 2.0 | 🔜 | |
| D | ユーザー検索 | ユーザーIDで完全一致 | ✅ | `subject_claim_mapping: "sub"` |
| E | ユーザー検索 | emailで検索 | ✅ | `subject_claim_mapping: "email"` |
| F | ユーザー検索 | 外部IdPサブジェクトマッピング | ✅ | `subject_claim_mapping` 未設定（推奨） |
| G | ユーザー管理 | JIT Provisioning | ✅ | `jit_provisioning_enabled: true` |
| H | ユーザー管理 | Claim Sync | ✅ | JIT有効 + mapping rules設定時 |
| I | 交換パターン | Impersonation | ✅ | subject_token のみ |
| J | 交換パターン | Delegation（actor_token） | 🔜 | |

---

## 処理フロー詳細

### JWT subject_token の検証フロー

```
1. subject_token を JWT としてパース
2. issuer クレームから AvailableFederation を特定
3. federation の JWKS / jwks_uri で署名検証
4. JWT クレーム検証（iss, sub, aud, exp, nbf, iat）
5. ユーザー解決（検索 or JIT Provisioning）
6. アクセストークン + リフレッシュトークン発行
```

### Opaque token の検証フロー（Introspection）

```
1. token_exchange_token_verification_method = "introspection" の federation を特定
2. 外部IdP の introspection endpoint にリクエスト
   （client_secret_basic or client_secret_post）
3. レスポンスの active=true を確認
4. sub クレームを取得
5. ユーザー解決（検索 or JIT Provisioning）
6. アクセストークン + リフレッシュトークン発行
```

### JIT Provisioning フロー

```
ユーザー検索（findUser）
  │
  ├─ [見つかった + JIT有効 + mappingRulesあり]
  │    → UserInfoMapper でクレーム→User変換 → 属性同期
  │
  ├─ [見つかった + JIT無効]
  │    → そのまま使用
  │
  ├─ [見つからない + JIT有効]
  │    → UserInfoMapper でクレーム→User変換
  │    → 新規UUID + status=FEDERATED で登録
  │
  └─ [見つからない + JIT無効]
       → invalid_grant エラー
```

SSO Federation（`OidcFederationInteractor.resolveUser`）と同じパターン。`UserInfoMapper` + `UserRegistrator.registerOrUpdate()` を再利用。

---

## リクエスト/レスポンス仕様

### リクエストパラメータ

| パラメータ | 必須 | 説明 | 対応状況 |
|---|---|---|---|
| `grant_type` | REQUIRED | `urn:ietf:params:oauth:grant-type:token-exchange` | ✅ |
| `subject_token` | REQUIRED | 交換対象のセキュリティトークン | ✅ |
| `subject_token_type` | REQUIRED | subject_token のタイプ識別子 | ✅ |
| `scope` | OPTIONAL | 要求するスコープ | ✅ |
| `requested_token_type` | OPTIONAL | 要求する出力トークンタイプ | パラメータ受付のみ |
| `resource` | OPTIONAL | ターゲットリソースURI | パラメータ受付のみ |
| `audience` | OPTIONAL | ターゲットサービス名 | パラメータ受付のみ |
| `actor_token` | OPTIONAL | アクタートークン | 🔜 |
| `actor_token_type` | CONDITIONAL | actor_token のタイプ識別子 | 🔜 |

### レスポンスパラメータ

| パラメータ | 必須 | 説明 |
|---|---|---|
| `access_token` | REQUIRED | 発行されたトークン |
| `issued_token_type` | REQUIRED | `urn:ietf:params:oauth:token-type:access_token` |
| `token_type` | REQUIRED | `Bearer` |
| `expires_in` | RECOMMENDED | 有効期限（秒） |
| `scope` | CONDITIONAL | 発行されたスコープ |
| `refresh_token` | OPTIONAL | リフレッシュトークン |

### リクエスト例

```bash
curl -X POST https://api.example.com/{tenant-id}/v1/tokens \
  -d "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
  -d "subject_token=eyJhbGciOiJFUzI1NiIs..." \
  -d "subject_token_type=urn:ietf:params:oauth:token-type:access_token" \
  -d "scope=openid profile email" \
  -d "client_id=my-client" \
  -d "client_secret=my-secret"
```

### レスポンス例

```json
{
  "access_token": "eyJhbGciOiJFUzI1NiIs...",
  "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "openid profile email",
  "refresh_token": "dGhpcyBpcyBhIHJlZnJl..."
}
```

---

## 設定リファレンス

### Authorization Server

`grant_types_supported` に `urn:ietf:params:oauth:grant-type:token-exchange` を追加。

### Client

`grant_types` に `urn:ietf:params:oauth:grant-type:token-exchange` を追加。

### Federation（available_federations）

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `id` | string | Yes | Federation ID |
| `issuer` | string | Yes | 外部IdPの issuer |
| `type` | string | Yes | `"oidc"` 等 |
| `provider_id` | string | No | プロバイダID（未設定時は issuer がfallback） |
| `token_exchange_grant_enabled` | boolean | Yes | token exchange の有効/無効 |
| `token_exchange_token_verification_method` | string | No | `"jwt"`（デフォルト）または `"introspection"` |
| `subject_claim_mapping` | string | No | ユーザー検索方法（下表参照） |
| `jit_provisioning_enabled` | boolean | No | JIT Provisioning の有効/無効（デフォルト: false） |
| `userinfo_mapping_rules` | array | No | 外部クレーム→ユーザー属性のマッピングルール |
| `jwks` | string | No | 外部IdPの公開鍵（JWT検証用） |
| `jwks_uri` | string | No | 外部IdPのJWKSエンドポイント |
| `introspection_endpoint` | string | No | 外部IdPのイントロスペクションエンドポイント |
| `introspection_auth_method` | string | No | `"client_secret_basic"`（デフォルト）または `"client_secret_post"` |
| `introspection_client_id` | string | No | イントロスペクション用クライアントID |
| `introspection_client_secret` | string | No | イントロスペクション用クライアントシークレット |

**検証方式ごとの必須設定:**
- `"jwt"`: `jwks` または `jwks_uri` が必要
- `"introspection"`: `introspection_endpoint` + `introspection_client_id` + `introspection_client_secret` が必要
- `jit_provisioning_enabled: true`: `userinfo_mapping_rules` が必要

### subject_claim_mapping の動作

| 値 | 検索方法 | 用途 |
|---|---|---|
| `"sub"` | `findById` — idp-server のユーザーIDと完全一致 | 旧IdPと同じID体系の場合 |
| `"email"` | `findByEmail` — email + providerId で検索 | emailベースのマッチング |
| 未設定/その他 | `findByExternalIdpSubject` — 外部IdP sub + providerId で検索 | 異なるID体系の移行（推奨） |

---

## Federation設定例

### JWT検証 + JIT Provisioning

```json
{
  "id": "legacy-idp",
  "issuer": "https://old-idp.example.com",
  "type": "oidc",
  "token_exchange_grant_enabled": true,
  "token_exchange_token_verification_method": "jwt",
  "jit_provisioning_enabled": true,
  "jwks": "{\"keys\":[...]}",
  "userinfo_mapping_rules": [
    { "from": "$.sub", "to": "external_user_id" },
    { "from": "$.email", "to": "email" },
    { "from": "$.preferred_username", "to": "preferred_username" },
    { "from": "$.name", "to": "name" }
  ]
}
```

### Introspection検証 + JIT Provisioning

```json
{
  "id": "legacy-idp",
  "issuer": "https://old-idp.example.com",
  "type": "oidc",
  "token_exchange_grant_enabled": true,
  "token_exchange_token_verification_method": "introspection",
  "jit_provisioning_enabled": true,
  "introspection_endpoint": "https://old-idp.example.com/introspect",
  "introspection_auth_method": "client_secret_basic",
  "introspection_client_id": "idp-server-client",
  "introspection_client_secret": "secret123",
  "userinfo_mapping_rules": [
    { "from": "$.sub", "to": "external_user_id" },
    { "from": "$.sub", "to": "preferred_username" }
  ]
}
```

### JWT検証のみ（ユーザー事前登録済み）

```json
{
  "id": "legacy-idp",
  "issuer": "https://old-idp.example.com",
  "type": "oidc",
  "token_exchange_grant_enabled": true,
  "subject_claim_mapping": "sub",
  "jwks": "{\"keys\":[...]}"
}
```

---

## エラーレスポンス

| error | 原因 |
|---|---|
| `unsupported_grant_type` | サーバーが token exchange をサポートしていない |
| `unauthorized_client` | クライアントが token exchange を許可されていない |
| `invalid_request` | `subject_token` または `subject_token_type` が未指定 |
| `invalid_grant` | issuer が信頼されていない / 署名検証失敗 / JWT期限切れ / ユーザー未発見 / introspection で active=false |
| `server_error` | 予期しないエラー |

---

## 制約事項

### actor_token 未対応

RFC 8693 の Delegation パターン（`actor_token` / `actor_token_type`）は未実装。Impersonation パターンのみサポート。

### SAML トークン未対応

`urn:ietf:params:oauth:token-type:saml1` / `saml2` は未対応。
