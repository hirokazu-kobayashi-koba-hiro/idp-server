# OpenID Connect Discovery

## 📍 このドキュメントの位置づけ

**対象読者**: OpenID Connect Discovery の実装詳細を理解したい開発者

**このドキュメントで学べること**:
- OpenID Connect Discovery の仕組み
- `.well-known/openid-configuration` エンドポイントの実装
- JWKS (JSON Web Key Set) エンドポイントの実装
- メタデータ生成の実装詳細
- AuthorizationServerConfiguration からのメタデータ抽出

**前提知識**:
- [basic-15: OIDC Discovery & Dynamic Registration](../../content_03_concepts/basic/basic-15-oidc-discovery-dynamic-registration-standard.md)の理解
- [basic-12: OpenID Connect詳解](../../content_03_concepts/basic/basic-12-openid-connect-detail.md)の理解
- OAuth 2.0 / OIDC の基礎知識

---

## 🏗️ OpenID Connect Discovery とは

**OpenID Connect Discovery** は、認可サーバーのメタデータ（設定情報）をクライアントが自動的に取得できる仕組みです。

### なぜDiscoveryが必要か

**Discoveryなしの場合の問題点**:
```
1. 開発者がドキュメントを読んでエンドポイントURLを手動設定
   - authorization_endpoint: https://idp.example.com/authorize
   - token_endpoint: https://idp.example.com/token
   - userinfo_endpoint: https://idp.example.com/userinfo
   - jwks_uri: https://idp.example.com/.well-known/jwks.json

2. サーバー側でURLが変更されると、すべてのクライアントで設定変更が必要
3. サポートされている機能（スコープ、アルゴリズム等）の確認が困難
```

**Discoveryありの場合**:
```
1. クライアントは .well-known/openid-configuration にアクセス
2. すべてのエンドポイントURL、サポート機能を自動取得
3. サーバー側の変更に自動追従
```

### 2つのエンドポイント

| エンドポイント | URL | 用途 |
|-------------|-----|------|
| **Server Configuration** | `/.well-known/openid-configuration` | 認可サーバーのメタデータ |
| **JWKS** | `/.well-known/jwks.json` | 公開鍵セット（署名検証用） |

---

## 📋 実装アーキテクチャ

### DiscoveryHandler

Discovery関連のリクエストを処理するハンドラーです。

```java
public class DiscoveryHandler {

  AuthorizationServerConfigurationQueryRepository
      authorizationServerConfigurationQueryRepository;

  /**
   * .well-known/openid-configuration レスポンス生成
   */
  public ServerConfigurationRequestResponse getConfiguration(Tenant tenant) {
    // 1. AuthorizationServerConfiguration 取得
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    // 2. レスポンス生成
    ServerConfigurationResponseCreator serverConfigurationResponseCreator =
        new ServerConfigurationResponseCreator(authorizationServerConfiguration);
    Map<String, Object> content = serverConfigurationResponseCreator.create();

    return new ServerConfigurationRequestResponse(
        ServerConfigurationRequestStatus.OK, content);
  }

  /**
   * .well-known/jwks.json レスポンス生成
   */
  public JwksRequestResponse getJwks(Tenant tenant) {
    // 1. AuthorizationServerConfiguration 取得
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    // 2. JWKS レスポンス生成
    JwksResponseCreator jwksResponseCreator =
        new JwksResponseCreator(authorizationServerConfiguration);
    Map<String, Object> content = jwksResponseCreator.create();

    return new JwksRequestResponse(JwksRequestStatus.OK, content);
  }
}
```

**参考実装**: [DiscoveryHandler.java:30](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/discovery/handler/DiscoveryHandler.java#L30)

---

## 🔧 Server Configuration 実装

### ServerConfigurationResponseCreator

`.well-known/openid-configuration` のレスポンスを生成します。

```java
public class ServerConfigurationResponseCreator {
  AuthorizationServerConfiguration authorizationServerConfiguration;

  public Map<String, Object> create() {
    Map<String, Object> map = new HashMap<>();

    // 1. 必須フィールド
    map.put("issuer", authorizationServerConfiguration.issuer());
    map.put("authorization_endpoint",
        authorizationServerConfiguration.authorizationEndpoint());
    map.put("jwks_uri", authorizationServerConfiguration.jwksUri());
    map.put("response_types_supported",
        authorizationServerConfiguration.responseTypesSupported());
    map.put("subject_types_supported",
        authorizationServerConfiguration.subjectTypesSupported());
    map.put("id_token_signing_alg_values_supported",
        authorizationServerConfiguration.idTokenSigningAlgValuesSupported());

    // 2. オプションフィールド（存在する場合のみ追加）
    if (authorizationServerConfiguration.hasTokenEndpoint()) {
      map.put("token_endpoint",
          authorizationServerConfiguration.tokenEndpoint());
    }

    if (authorizationServerConfiguration.hasUserinfoEndpoint()) {
      map.put("userinfo_endpoint",
          authorizationServerConfiguration.userinfoEndpoint());
    }

    if (authorizationServerConfiguration.hasRegistrationEndpoint()) {
      map.put("registration_endpoint",
          authorizationServerConfiguration.registrationEndpoint());
    }

    if (authorizationServerConfiguration.hasScopesSupported()) {
      map.put("scopes_supported",
          authorizationServerConfiguration.scopesSupported());
    }

    if (authorizationServerConfiguration.hasResponseModesSupported()) {
      map.put("response_modes_supported",
          authorizationServerConfiguration.responseModesSupported());
    }

    if (authorizationServerConfiguration.hasGrantTypesSupported()) {
      map.put("grant_types_supported",
          authorizationServerConfiguration.grantTypesSupported());
    }

    if (authorizationServerConfiguration.hasAcrValuesSupported()) {
      map.put("acr_values_supported",
          authorizationServerConfiguration.acrValuesSupported());
    }

    // 3. 暗号化関連（IDトークン）
    if (authorizationServerConfiguration.hasIdTokenEncryptionAlgValuesSupported()) {
      map.put("id_token_encryption_alg_values_supported",
          authorizationServerConfiguration.idTokenEncryptionAlgValuesSupported());
    }

    if (authorizationServerConfiguration.hasIdTokenEncryptionEncValuesSupported()) {
      map.put("id_token_encryption_enc_values_supported",
          authorizationServerConfiguration.idTokenEncryptionEncValuesSupported());
    }

    // 4. 暗号化関連（Userinfo）
    if (authorizationServerConfiguration.hasUserinfoSigningAlgValuesSupported()) {
      map.put("userinfo_signing_alg_values_supported",
          authorizationServerConfiguration.userinfoSigningAlgValuesSupported());
    }

    if (authorizationServerConfiguration.hasUserinfoEncryptionAlgValuesSupported()) {
      map.put("userinfo_encryption_alg_values_supported",
          authorizationServerConfiguration.userinfoEncryptionAlgValuesSupported());
    }

    // 5. Request Object 関連
    if (authorizationServerConfiguration.hasRequestObjectSigningAlgValuesSupported()) {
      map.put("request_object_signing_alg_values_supported",
          authorizationServerConfiguration.requestObjectSigningAlgValuesSupported());
    }

    if (authorizationServerConfiguration.hasRequestObjectEncryptionAlgValuesSupported()) {
      map.put("request_object_encryption_alg_values_supported",
          authorizationServerConfiguration.requestObjectEncryptionAlgValuesSupported());
    }

    // 6. トークンエンドポイント認証
    if (authorizationServerConfiguration.hasTokenEndpointAuthMethodsSupported()) {
      map.put("token_endpoint_auth_methods_supported",
          authorizationServerConfiguration.tokenEndpointAuthMethodsSupported());
    }

    if (authorizationServerConfiguration.hasTokenEndpointAuthSigningAlgValuesSupported()) {
      map.put("token_endpoint_auth_signing_alg_values_supported",
          authorizationServerConfiguration.tokenEndpointAuthSigningAlgValuesSupported());
    }

    // 7. Claims 関連
    if (authorizationServerConfiguration.hasClaimsSupported()) {
      map.put("claims_supported",
          authorizationServerConfiguration.claimsSupported());
    }

    map.put("claims_parameter_supported",
        authorizationServerConfiguration.claimsParameterSupported());
    map.put("request_parameter_supported",
        authorizationServerConfiguration.requestParameterSupported());
    map.put("request_uri_parameter_supported",
        authorizationServerConfiguration.requestUriParameterSupported());
    map.put("require_request_uri_registration",
        authorizationServerConfiguration.requireRequestUriRegistration());

    // 8. mTLS 関連
    map.put("tls_client_certificate_bound_access_tokens",
        authorizationServerConfiguration.isTlsClientCertificateBoundAccessTokens());

    if (authorizationServerConfiguration.hasMtlsEndpointAliases()) {
      map.put("mtls_endpoint_aliases",
          authorizationServerConfiguration.mtlsEndpointAliases());
    }

    // 9. Introspection / Revocation
    if (authorizationServerConfiguration.hasIntrospectionEndpoint()) {
      map.put("introspection_endpoint",
          authorizationServerConfiguration.introspectionEndpoint());
    }

    if (authorizationServerConfiguration.hasRevocationEndpoint()) {
      map.put("revocation_endpoint",
          authorizationServerConfiguration.revocationEndpoint());
    }

    // 10. Authorization Details (RAR - RFC 9396)
    if (!authorizationServerConfiguration.authorizationDetailsTypesSupported().isEmpty()) {
      map.put("authorization_details_types_supported",
          authorizationServerConfiguration.authorizationDetailsTypesSupported());
    }

    // 11. CIBA (Backchannel Authentication)
    if (authorizationServerConfiguration.hasBackchannelTokenDeliveryModesSupported()) {
      map.put("backchannel_token_delivery_modes_supported",
          authorizationServerConfiguration.backchannelTokenDeliveryModesSupported());
    }

    if (authorizationServerConfiguration.hasBackchannelAuthenticationEndpoint()) {
      map.put("backchannel_authentication_endpoint",
          authorizationServerConfiguration.backchannelAuthenticationEndpoint());
    }

    // 12. Identity Assurance (IDA)
    map.put("verified_claims_supported",
        authorizationServerConfiguration.verifiedClaimsSupported());

    if (authorizationServerConfiguration.verifiedClaimsSupported()) {
      map.put("trust_frameworks_supported",
          authorizationServerConfiguration.trustFrameworksSupported());
      map.put("evidence_supported",
          authorizationServerConfiguration.evidenceSupported());
      map.put("id_documents_supported",
          authorizationServerConfiguration.idDocumentsSupported());
      map.put("id_documents_verification_methods_supported",
          authorizationServerConfiguration.idDocumentsVerificationMethodsSupported());
      map.put("claims_in_verified_claims_supported",
          authorizationServerConfiguration.claimsInVerifiedClaimsSupported());
    }

    return map;
  }
}
```

**参考実装**: [ServerConfigurationResponseCreator.java:23](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/discovery/ServerConfigurationResponseCreator.java#L23)

### レスポンス例

```json
{
  "issuer": "https://idp.example.com",
  "authorization_endpoint": "https://idp.example.com/authorize",
  "token_endpoint": "https://idp.example.com/token",
  "userinfo_endpoint": "https://idp.example.com/userinfo",
  "jwks_uri": "https://idp.example.com/.well-known/jwks.json",
  "registration_endpoint": "https://idp.example.com/register",
  "scopes_supported": ["openid", "profile", "email", "phone", "address", "offline_access"],
  "response_types_supported": ["code", "id_token", "token id_token", "code id_token", "code token", "code token id_token"],
  "response_modes_supported": ["query", "fragment", "form_post", "jwt"],
  "grant_types_supported": ["authorization_code", "implicit", "refresh_token", "client_credentials"],
  "acr_values_supported": ["password", "fido-uaf", "webauthn"],
  "subject_types_supported": ["public", "pairwise"],
  "id_token_signing_alg_values_supported": ["RS256", "ES256", "PS256"],
  "id_token_encryption_alg_values_supported": ["RSA-OAEP", "RSA-OAEP-256"],
  "id_token_encryption_enc_values_supported": ["A128CBC-HS256", "A256CBC-HS512"],
  "userinfo_signing_alg_values_supported": ["RS256", "ES256"],
  "request_object_signing_alg_values_supported": ["RS256", "ES256", "PS256"],
  "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post", "client_secret_jwt", "private_key_jwt", "tls_client_auth"],
  "token_endpoint_auth_signing_alg_values_supported": ["RS256", "ES256", "PS256"],
  "claims_supported": ["sub", "name", "given_name", "family_name", "email", "email_verified", "phone_number"],
  "claims_parameter_supported": true,
  "request_parameter_supported": true,
  "request_uri_parameter_supported": true,
  "require_request_uri_registration": false,
  "tls_client_certificate_bound_access_tokens": true,
  "introspection_endpoint": "https://idp.example.com/introspect",
  "revocation_endpoint": "https://idp.example.com/revoke",
  "backchannel_token_delivery_modes_supported": ["poll", "ping"],
  "backchannel_authentication_endpoint": "https://idp.example.com/bc-authorize",
  "verified_claims_supported": true,
  "trust_frameworks_supported": ["eidas", "jp_moj"],
  "evidence_supported": ["id_document", "qes"],
  "id_documents_supported": ["idcard", "passport", "driving_permit"],
  "claims_in_verified_claims_supported": ["given_name", "family_name", "birthdate"]
}
```

---

## 🔑 JWKS (JSON Web Key Set) 実装

### JwksResponseCreator

JWKS エンドポイントのレスポンスを生成します。

```java
public class JwksResponseCreator {

  AuthorizationServerConfiguration authorizationServerConfiguration;

  public JwksResponseCreator(
      AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public Map<String, Object> create() {
    try {
      // JWKS から公開鍵のみを抽出
      String jwks = authorizationServerConfiguration.jwks();
      return JwkParser.parsePublicKeys(jwks);
    } catch (JsonWebKeyInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
```

**参考実装**: [JwksResponseCreator.java:24](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/discovery/JwksResponseCreator.java#L24)

### JwkParser.parsePublicKeys

秘密鍵を除外して、公開鍵のみをJWKSとして返します。

```java
public class JwkParser {

  public static Map<String, Object> parsePublicKeys(String value)
      throws JsonWebKeyInvalidException {

    if (value == null || value.trim().isEmpty()) {
      throw new JsonWebKeyInvalidException("JWKS value is null or empty.");
    }

    try {
      // 1. JWKS をパース
      JWKSet jwkSet = JWKSet.parse(value);

      // 2. 公開鍵のみを抽出
      JWKSet publicJWKSet = jwkSet.toPublicJWKSet();

      // 3. JSON Object に変換
      return publicJWKSet.toJSONObject();
    } catch (ParseException e) {
      throw new JsonWebKeyInvalidException(e.getMessage(), e);
    }
  }
}
```

**参考実装**: [JwkParser.java:26](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JwkParser.java#L26)

**重要なポイント**:
- ✅ **公開鍵のみ**を返す（`toPublicJWKSet()`）
- ❌ 秘密鍵（`d`, `p`, `q` 等）は除外される
- ✅ クライアントはこの公開鍵でIDトークンの署名を検証

### JWKSレスポンス例

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "2025-12-07-rsa-key",
      "alg": "RS256",
      "n": "xGOr-H7A5I3YvA...",
      "e": "AQAB"
    },
    {
      "kty": "EC",
      "use": "sig",
      "kid": "2025-12-07-ec-key",
      "crv": "P-256",
      "alg": "ES256",
      "x": "WKn-ZIGevcwGI...",
      "y": "Pnlqj4F1cOEqe..."
    }
  ]
}
```

**フィールド説明**:
- `kty`: Key Type（RSA, EC, OKP等）
- `use`: 用途（sig=署名、enc=暗号化）
- `kid`: Key ID（鍵の識別子）
- `alg`: アルゴリズム（RS256, ES256等）
- `n`, `e`: RSA公開鍵パラメータ
- `x`, `y`: 楕円曲線公開鍵パラメータ

---

## 📊 メタデータ項目一覧

### 必須項目

| フィールド | 説明 | 例 |
|----------|------|-----|
| `issuer` | トークン発行者URL | "https://idp.example.com" |
| `authorization_endpoint` | 認可エンドポイント | "https://idp.example.com/authorize" |
| `jwks_uri` | JWKS URL | "https://idp.example.com/.well-known/jwks.json" |
| `response_types_supported` | サポートするresponse_type | ["code", "id_token", "code id_token"] |
| `subject_types_supported` | サポートするsubject type | ["public", "pairwise"] |
| `id_token_signing_alg_values_supported` | IDトークン署名アルゴリズム | ["RS256", "ES256"] |

### 推奨項目

| フィールド | 説明 | 例 |
|----------|------|-----|
| `token_endpoint` | トークンエンドポイント | "https://idp.example.com/token" |
| `userinfo_endpoint` | Userinfoエンドポイント | "https://idp.example.com/userinfo" |
| `scopes_supported` | サポートするスコープ | ["openid", "profile", "email"] |
| `response_modes_supported` | サポートするresponse_mode | ["query", "fragment", "form_post", "jwt"] |
| `grant_types_supported` | サポートするgrant_type | ["authorization_code", "refresh_token"] |
| `token_endpoint_auth_methods_supported` | サポートするクライアント認証方式 | ["client_secret_basic", "private_key_jwt", "tls_client_auth"] |
| `claims_supported` | サポートするクレーム | ["sub", "name", "email"] |

### オプション項目

| フィールド | 説明 | 用途 |
|----------|------|------|
| `registration_endpoint` | 動的クライアント登録エンドポイント | Dynamic Registration |
| `introspection_endpoint` | トークンイントロスペクションエンドポイント | トークン検証 |
| `revocation_endpoint` | トークン失効エンドポイント | トークン無効化 |
| `acr_values_supported` | サポートする認証コンテキストクラス | ["password", "fido-uaf", "webauthn"] |
| `request_parameter_supported` | Request Objectサポート | FAPI |
| `request_uri_parameter_supported` | Request URI サポート | FAPI/PAR |
| `tls_client_certificate_bound_access_tokens` | mTLS トークンバインディング | FAPI Advance |
| `mtls_endpoint_aliases` | mTLS エンドポイントエイリアス | FAPI |
| `backchannel_authentication_endpoint` | CIBA認証エンドポイント | CIBA |
| `backchannel_token_delivery_modes_supported` | CIBAデリバリーモード | CIBA |
| `verified_claims_supported` | verified_claims サポート | IDA |
| `trust_frameworks_supported` | サポートするトラストフレームワーク | IDA |

---

## 🧪 テスト実装例

### Discovery エンドポイントテスト

```java
@Test
void testDiscoveryConfiguration() {
  // 1. テナント作成
  TenantIdentifier tenantIdentifier = new TenantIdentifier("tenant-123");
  Tenant tenant = tenantRepository.get(tenantIdentifier);

  // 2. Discovery Handler呼び出し
  DiscoveryHandler handler = new DiscoveryHandler(
      authorizationServerConfigurationQueryRepository);

  ServerConfigurationRequestResponse response = handler.getConfiguration(tenant);

  // 3. 検証
  assertEquals(ServerConfigurationRequestStatus.OK, response.status());

  Map<String, Object> content = response.content();
  assertNotNull(content.get("issuer"));
  assertNotNull(content.get("authorization_endpoint"));
  assertNotNull(content.get("token_endpoint"));
  assertNotNull(content.get("jwks_uri"));
  assertNotNull(content.get("response_types_supported"));
  assertNotNull(content.get("subject_types_supported"));
  assertNotNull(content.get("id_token_signing_alg_values_supported"));
}
```

### JWKS エンドポイントテスト

```java
@Test
void testJwksEndpoint() {
  // 1. テナント作成
  Tenant tenant = tenantRepository.get(new TenantIdentifier("tenant-456"));

  // 2. JWKS取得
  DiscoveryHandler handler = new DiscoveryHandler(
      authorizationServerConfigurationQueryRepository);

  JwksRequestResponse response = handler.getJwks(tenant);

  // 3. 検証
  assertEquals(JwksRequestStatus.OK, response.status());

  Map<String, Object> content = response.content();
  assertNotNull(content.get("keys"));

  List<Map<String, Object>> keys = (List<Map<String, Object>>) content.get("keys");
  assertFalse(keys.isEmpty());

  // 4. 公開鍵のみ含まれることを確認
  for (Map<String, Object> key : keys) {
    assertNotNull(key.get("kty"));  // Key Type
    assertNotNull(key.get("kid"));  // Key ID
    assertNull(key.get("d"));       // 秘密鍵パラメータは除外されている
  }
}
```

### メタデータ項目の検証

```java
@Test
void testFapiMetadata() {
  Tenant tenant = tenantRepository.get(new TenantIdentifier("fapi-tenant"));

  DiscoveryHandler handler = new DiscoveryHandler(
      authorizationServerConfigurationQueryRepository);

  ServerConfigurationRequestResponse response = handler.getConfiguration(tenant);
  Map<String, Object> metadata = response.content();

  // FAPI関連項目の検証
  assertTrue((Boolean) metadata.get("request_parameter_supported"));
  assertTrue((Boolean) metadata.get("request_uri_parameter_supported"));
  assertTrue((Boolean) metadata.get("tls_client_certificate_bound_access_tokens"));

  List<String> tokenAuthMethods =
      (List<String>) metadata.get("token_endpoint_auth_methods_supported");
  assertTrue(tokenAuthMethods.contains("private_key_jwt"));
  assertTrue(tokenAuthMethods.contains("tls_client_auth"));
}
```

---

## 📋 実装チェックリスト

Discovery機能を実装・設定する際のチェックリスト:

### Server Configuration

- [ ] **必須メタデータ**:
  - [ ] `issuer` - Issuer URL設定
  - [ ] `authorization_endpoint` - 認可エンドポイントURL
  - [ ] `jwks_uri` - JWKS URL
  - [ ] `response_types_supported` - サポートするレスポンスタイプ
  - [ ] `subject_types_supported` - Subject タイプ（public/pairwise）
  - [ ] `id_token_signing_alg_values_supported` - IDトークン署名アルゴリズム

- [ ] **推奨メタデータ**:
  - [ ] `token_endpoint` - トークンエンドポイントURL
  - [ ] `userinfo_endpoint` - UserinfoエンドポイントURL
  - [ ] `scopes_supported` - サポートするスコープ一覧
  - [ ] `grant_types_supported` - サポートするグラントタイプ
  - [ ] `token_endpoint_auth_methods_supported` - クライアント認証方式

- [ ] **拡張メタデータ（機能に応じて）**:
  - [ ] FAPI: `request_parameter_supported`, `tls_client_certificate_bound_access_tokens`
  - [ ] CIBA: `backchannel_authentication_endpoint`, `backchannel_token_delivery_modes_supported`
  - [ ] IDA: `verified_claims_supported`, `trust_frameworks_supported`

### JWKS

- [ ] **JWKS設定**:
  - [ ] AuthorizationServerConfiguration に JWKS を設定
  - [ ] 秘密鍵を含む完全なJWKS（内部保存用）

- [ ] **公開鍵抽出**:
  - [ ] `JwkParser.parsePublicKeys()` で秘密鍵を除外
  - [ ] 公開鍵のみをクライアントに返す

- [ ] **鍵ローテーション**:
  - [ ] 複数の鍵をJWKSに含める（古い鍵 + 新しい鍵）
  - [ ] 移行期間中は両方の鍵で検証可能にする

---

## 🚨 よくある間違い

### 1. 秘密鍵の公開

```java
// ❌ 誤り: 秘密鍵を含むJWKSをそのまま返す
public Map<String, Object> create() {
  String jwks = authorizationServerConfiguration.jwks();
  return JWKSet.parse(jwks).toJSONObject();  // 秘密鍵も含まれる！
}

// ✅ 正しい: 公開鍵のみ抽出
public Map<String, Object> create() {
  String jwks = authorizationServerConfiguration.jwks();
  return JwkParser.parsePublicKeys(jwks);  // 公開鍵のみ
}
```

### 2. オプション項目のnull追加

```java
// ❌ 誤り: 存在しない項目をnullで追加
map.put("registration_endpoint", null);

// ✅ 正しい: 存在する場合のみ追加
if (authorizationServerConfiguration.hasRegistrationEndpoint()) {
  map.put("registration_endpoint",
      authorizationServerConfiguration.registrationEndpoint());
}
```

### 3. issuer の不一致

```json
// ❌ 誤り: issuer と実際のトークン issuer が異なる
{
  "issuer": "https://idp.example.com",
  ...
}

// IDトークン:
{
  "iss": "https://auth.example.com",  // 異なる！
  ...
}

// ✅ 正しい: issuer とトークンの iss が一致
```

### 4. JWKS URLの設定忘れ

```java
// ❌ 誤り: jwks_uri が設定されていない
// クライアントは公開鍵を取得できず、署名検証ができない

// ✅ 正しい: jwks_uri を適切に設定
map.put("jwks_uri", "https://idp.example.com/.well-known/jwks.json");
```

---

## 🔗 関連ドキュメント

**概念・基礎**:
- [basic-15: OIDC Discovery & Dynamic Registration](../../content_03_concepts/basic/basic-15-oidc-discovery-dynamic-registration-standard.md) - Discovery概念
- [basic-12: OpenID Connect詳解](../../content_03_concepts/basic/basic-12-openid-connect-detail.md) - OIDC基礎

**実装詳細**:
- [impl-22: FAPI実装ガイド](./impl-22-fapi-implementation.md) - FAPI関連メタデータ
- [03-application-plane/06-ciba-flow.md](../03-application-plane/06-ciba-flow.md) - CIBA関連メタデータ

**参考実装クラス**:
- [DiscoveryHandler.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/discovery/handler/DiscoveryHandler.java)
- [ServerConfigurationResponseCreator.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/discovery/ServerConfigurationResponseCreator.java)
- [JwksResponseCreator.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/discovery/JwksResponseCreator.java)
- [JwkParser.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JwkParser.java)
- [OidcMetaDataApi.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/discovery/OidcMetaDataApi.java)

**RFC/仕様**:
- [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html)
- [RFC 8414 - OAuth 2.0 Authorization Server Metadata](https://datatracker.ietf.org/doc/html/rfc8414)
- [RFC 7517 - JSON Web Key (JWK)](https://datatracker.ietf.org/doc/html/rfc7517)

---

**最終更新**: 2025-12-07
**難易度**: ⭐⭐ (初級〜中級)
