# OpenID Connect 動的クライアント登録 1.0

OpenID Connect Dynamic Client Registration は、RFC 7591 を拡張し、OIDC 固有のクライアントメタデータを追加した仕様です。

---

## 第1部: 概要編

### OIDC 動的登録とは？

OpenID Connect の動的クライアント登録は、RFC 7591 の OAuth 2.0 動的登録に**OIDC 固有のメタデータ**を追加した仕様です。

```
RFC 7591（OAuth 2.0）:
  redirect_uris
  token_endpoint_auth_method
  grant_types
  ...

OIDC Dynamic Registration:
  + userinfo_signed_response_alg
  + id_token_encrypted_response_alg
  + default_max_age
  + subject_type
  ...
```

### OIDC 固有のメタデータ

| カテゴリ | メタデータ例 |
|---------|-------------|
| ID トークン | 署名・暗号化アルゴリズム |
| UserInfo | 署名・暗号化アルゴリズム |
| セッション | 最大認証時間 |
| ログアウト | ログアウト URI |
| Subject | pairwise / public |

---

## 第2部: 詳細編

### 登録リクエスト

```http
POST /register HTTP/1.1
Host: auth.example.com
Content-Type: application/json
Authorization: Bearer initial_access_token

{
  "application_type": "web",
  "redirect_uris": [
    "https://client.example.com/callback"
  ],
  "client_name": "My OIDC Application",
  "logo_uri": "https://client.example.com/logo.png",
  "subject_type": "pairwise",
  "sector_identifier_uri": "https://client.example.com/sector",
  "token_endpoint_auth_method": "private_key_jwt",
  "jwks_uri": "https://client.example.com/.well-known/jwks.json",
  "userinfo_encrypted_response_alg": "RSA-OAEP-256",
  "userinfo_encrypted_response_enc": "A256GCM",
  "id_token_signed_response_alg": "ES256",
  "id_token_encrypted_response_alg": "RSA-OAEP-256",
  "id_token_encrypted_response_enc": "A256GCM",
  "default_max_age": 3600,
  "require_auth_time": true,
  "default_acr_values": ["urn:mace:incommon:iap:silver"],
  "initiate_login_uri": "https://client.example.com/login",
  "post_logout_redirect_uris": [
    "https://client.example.com/logout/callback"
  ],
  "backchannel_logout_uri": "https://client.example.com/logout/backchannel",
  "backchannel_logout_session_required": true
}
```

### OIDC 固有のクライアントメタデータ

#### アプリケーションタイプ

| メタデータ | 説明 |
|-----------|------|
| `application_type` | `web` または `native` |

```
web:
  - Web アプリケーション
  - redirect_uri は HTTPS 必須（localhost を除く）

native:
  - ネイティブアプリケーション（モバイル、デスクトップ）
  - redirect_uri はカスタムスキームまたは localhost 許可
```

#### Subject タイプ

| メタデータ | 説明 |
|-----------|------|
| `subject_type` | `public` または `pairwise` |
| `sector_identifier_uri` | pairwise の場合のセクター識別子 |

```
public:
  - すべてのクライアントで同じ sub 値
  - ユーザー追跡が可能

pairwise:
  - クライアントごとに異なる sub 値
  - プライバシー保護
  - sector_identifier_uri で同一セクターを識別
```

#### ID トークンの暗号化

| メタデータ | 説明 |
|-----------|------|
| `id_token_signed_response_alg` | 署名アルゴリズム（デフォルト: RS256） |
| `id_token_encrypted_response_alg` | 鍵暗号化アルゴリズム |
| `id_token_encrypted_response_enc` | コンテンツ暗号化アルゴリズム |

#### UserInfo の署名・暗号化

| メタデータ | 説明 |
|-----------|------|
| `userinfo_signed_response_alg` | 署名アルゴリズム |
| `userinfo_encrypted_response_alg` | 鍵暗号化アルゴリズム |
| `userinfo_encrypted_response_enc` | コンテンツ暗号化アルゴリズム |

#### Request Object の署名・暗号化

| メタデータ | 説明 |
|-----------|------|
| `request_object_signing_alg` | 署名アルゴリズム |
| `request_object_encryption_alg` | 鍵暗号化アルゴリズム |
| `request_object_encryption_enc` | コンテンツ暗号化アルゴリズム |

#### セッション管理

| メタデータ | 説明 |
|-----------|------|
| `default_max_age` | デフォルトの最大認証時間（秒） |
| `require_auth_time` | ID トークンに auth_time を必須にするか |
| `default_acr_values` | デフォルトの認証コンテキストクラス |

#### ログアウト

| メタデータ | 説明 |
|-----------|------|
| `post_logout_redirect_uris` | ログアウト後のリダイレクト先 |
| `frontchannel_logout_uri` | フロントチャネルログアウト URI |
| `frontchannel_logout_session_required` | セッション ID を含めるか |
| `backchannel_logout_uri` | バックチャネルログアウト URI |
| `backchannel_logout_session_required` | セッション ID を含めるか |

#### その他

| メタデータ | 説明 |
|-----------|------|
| `initiate_login_uri` | OP が RP にログインを要求する際の URI |
| `request_uris` | 事前登録された request_uri |

### 登録レスポンス

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "client_id": "s6BhdRkqt3",
  "client_secret": "cf136dc3c1fc93f31185e5885805d",
  "client_id_issued_at": 1704067200,
  "client_secret_expires_at": 0,
  "registration_access_token": "reg-token-abc123",
  "registration_client_uri": "https://auth.example.com/register/s6BhdRkqt3",

  "application_type": "web",
  "redirect_uris": ["https://client.example.com/callback"],
  "client_name": "My OIDC Application",
  "logo_uri": "https://client.example.com/logo.png",
  "subject_type": "pairwise",
  "sector_identifier_uri": "https://client.example.com/sector",
  "token_endpoint_auth_method": "private_key_jwt",
  "jwks_uri": "https://client.example.com/.well-known/jwks.json",
  "userinfo_encrypted_response_alg": "RSA-OAEP-256",
  "userinfo_encrypted_response_enc": "A256GCM",
  "id_token_signed_response_alg": "ES256",
  "id_token_encrypted_response_alg": "RSA-OAEP-256",
  "id_token_encrypted_response_enc": "A256GCM",
  "default_max_age": 3600,
  "require_auth_time": true,
  "default_acr_values": ["urn:mace:incommon:iap:silver"],
  "post_logout_redirect_uris": ["https://client.example.com/logout/callback"],
  "backchannel_logout_uri": "https://client.example.com/logout/backchannel",
  "backchannel_logout_session_required": true
}
```

### Sector Identifier URI

pairwise subject を使用する場合、複数のクライアントで同じ sub 値を共有するために使用。

```
Sector Identifier URI:
  https://client.example.com/sector

レスポンス（JSON 配列）:
  [
    "https://client.example.com/callback",
    "https://mobile.example.com/callback",
    "https://desktop.example.com/callback"
  ]

効果:
  これらの redirect_uri を持つクライアントは
  同じユーザーに対して同じ sub 値を受け取る
```

### 暗号化設定の例

#### ID トークンの暗号化

```json
{
  "id_token_signed_response_alg": "ES256",
  "id_token_encrypted_response_alg": "RSA-OAEP-256",
  "id_token_encrypted_response_enc": "A256GCM",
  "jwks_uri": "https://client.example.com/.well-known/jwks.json"
}
```

OP は以下の手順で ID トークンを生成：
1. ES256 で署名（JWS）
2. クライアントの公開鍵で暗号化（JWE）
3. Nested JWT として送信

#### UserInfo の暗号化

```json
{
  "userinfo_signed_response_alg": "RS256",
  "userinfo_encrypted_response_alg": "RSA-OAEP-256",
  "userinfo_encrypted_response_enc": "A256GCM"
}
```

### 実装例

#### Java（登録リクエスト）

```java
public class OIDCClientRegistration {

    public ClientRegistrationResponse register(
            String registrationEndpoint,
            String initialAccessToken,
            ClientRegistrationRequest request) throws Exception {

        // OIDC 固有のメタデータを設定
        request.setApplicationType("web");
        request.setSubjectType("pairwise");
        request.setIdTokenSignedResponseAlg("ES256");
        request.setIdTokenEncryptedResponseAlg("RSA-OAEP-256");
        request.setIdTokenEncryptedResponseEnc("A256GCM");
        request.setDefaultMaxAge(3600);
        request.setRequireAuthTime(true);

        // ログアウト設定
        request.setBackchannelLogoutUri(
            "https://client.example.com/logout/backchannel"
        );
        request.setBackchannelLogoutSessionRequired(true);

        // HTTP リクエスト
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(registrationEndpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + initialAccessToken)
            .POST(HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(request)
            ))
            .build();

        HttpResponse<String> response = httpClient.send(
            httpRequest, HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 201) {
            throw new RegistrationException("Registration failed: " + response.body());
        }

        return objectMapper.readValue(
            response.body(), ClientRegistrationResponse.class
        );
    }
}
```

#### JavaScript（登録リクエスト）

```javascript
async function registerOIDCClient(config) {
  const registrationRequest = {
    application_type: 'web',
    redirect_uris: config.redirectUris,
    client_name: config.clientName,
    logo_uri: config.logoUri,

    // OIDC 固有の設定
    subject_type: 'pairwise',
    id_token_signed_response_alg: 'ES256',
    id_token_encrypted_response_alg: 'RSA-OAEP-256',
    id_token_encrypted_response_enc: 'A256GCM',

    // 認証設定
    token_endpoint_auth_method: 'private_key_jwt',
    jwks_uri: config.jwksUri,

    // セッション設定
    default_max_age: 3600,
    require_auth_time: true,

    // ログアウト設定
    post_logout_redirect_uris: config.postLogoutRedirectUris,
    backchannel_logout_uri: config.backchannelLogoutUri,
    backchannel_logout_session_required: true
  };

  const response = await fetch(config.registrationEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${config.initialAccessToken}`
    },
    body: JSON.stringify(registrationRequest)
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(`Registration failed: ${error.error_description}`);
  }

  const clientInfo = await response.json();

  // 重要な情報を保存
  await saveClientCredentials({
    clientId: clientInfo.client_id,
    clientSecret: clientInfo.client_secret,
    registrationAccessToken: clientInfo.registration_access_token,
    registrationClientUri: clientInfo.registration_client_uri
  });

  return clientInfo;
}
```

### ネイティブアプリケーションの登録

```json
{
  "application_type": "native",
  "redirect_uris": [
    "com.example.myapp://callback",
    "http://localhost:8080/callback"
  ],
  "client_name": "My Native App",
  "token_endpoint_auth_method": "none",
  "grant_types": ["authorization_code", "refresh_token"],
  "response_types": ["code"]
}
```

ネイティブアプリは PKCE を使用し、`token_endpoint_auth_method` は `none`（パブリッククライアント）。

### ディスカバリーとの連携

```javascript
async function registerWithDiscovery(issuer, config) {
  // 1. OP のメタデータを取得
  const discoveryUrl = `${issuer}/.well-known/openid-configuration`;
  const metadataResponse = await fetch(discoveryUrl);
  const metadata = await metadataResponse.json();

  // 2. 登録エンドポイントを取得
  const registrationEndpoint = metadata.registration_endpoint;
  if (!registrationEndpoint) {
    throw new Error('Dynamic registration not supported');
  }

  // 3. サポートされている機能を確認
  const supportedAlgs = metadata.id_token_signing_alg_values_supported;
  const idTokenAlg = supportedAlgs.includes('ES256') ? 'ES256' : 'RS256';

  // 4. 登録
  const registrationRequest = {
    redirect_uris: config.redirectUris,
    client_name: config.clientName,
    id_token_signed_response_alg: idTokenAlg,
    token_endpoint_auth_method: determineAuthMethod(metadata)
  };

  return await registerClient(registrationEndpoint, registrationRequest);
}
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 登録エンドポイントの保護 | Initial Access Token を要求 |
| redirect_uri 検証 | application_type に応じて検証 |
| sector_identifier_uri | HTTPS 必須、内容を検証 |
| 暗号化設定 | クライアントの公開鍵を検証 |
| ログアウト URI | HTTPS 必須 |

---

## 参考リンク

- [OpenID Connect Dynamic Client Registration 1.0](https://openid.net/specs/openid-connect-registration-1_0.html)
- [RFC 7591 - OAuth 2.0 Dynamic Client Registration Protocol](https://datatracker.ietf.org/doc/html/rfc7591)
- [RFC 7592 - OAuth 2.0 Dynamic Client Registration Management Protocol](https://datatracker.ietf.org/doc/html/rfc7592)
