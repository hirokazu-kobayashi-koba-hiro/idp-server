# FAPI 1.0 Advanced Profile

FAPI 1.0 Advanced Profile は、決済や高額取引など高リスクな金融 API 向けのセキュリティプロファイルです。

---

## 第1部: 概要編

### Advanced Profile とは？

Advanced Profile は、Baseline Profile の要件に加えて、より強力なセキュリティ対策を要求するプロファイルです。

```
FAPI 1.0 Advanced の追加要件:

Baseline:
  ✓ PKCE
  ✓ state
  ✓ nonce
  ✓ 機密クライアント推奨

Advanced（追加）:
  + private_key_jwt または mTLS 必須
  + request object 必須
  + s_hash / c_hash 検証
  + JARM または Hybrid Flow + ID Token 検証
  + Sender-Constrained Access Tokens
```

### 用途

| 用途 | プロファイル |
|------|-------------|
| 残高照会 | Baseline |
| 取引履歴 | Baseline |
| 送金・決済 | Advanced |
| 口座開設 | Advanced |
| 個人情報変更 | Advanced |

---

## 第2部: 詳細編

### クライアント認証（必須）

Advanced Profile では、以下のクライアント認証方式のみ許可されます。

```
許可される認証方式:

1. private_key_jwt
   - 非対称鍵 JWT で認証
   - 秘密鍵はクライアントのみが保持

2. tls_client_auth
   - CA が発行した証明書で認証
   - 証明書のサブジェクト DN で識別

3. self_signed_tls_client_auth
   - 自己署名証明書で認証
   - 事前に公開鍵を登録

禁止される認証方式:
  ❌ client_secret_basic
  ❌ client_secret_post
  ❌ client_secret_jwt
  ❌ none
```

### Request Object（必須）

認可リクエストのパラメータを署名付き JWT（Request Object）で送信することが必須です。

```
Request Object の構造:

{
  "iss": "s6BhdRkqt3",           // client_id
  "aud": "https://auth.example.com",
  "response_type": "code",
  "client_id": "s6BhdRkqt3",
  "redirect_uri": "https://client.example.com/callback",
  "scope": "openid accounts",
  "state": "af0ifjsldkj",
  "nonce": "n-0S6_WzA2Mj",
  "code_challenge": "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
  "code_challenge_method": "S256",
  "exp": 1704153600,
  "iat": 1704150000,
  "nbf": 1704150000
}
```

#### 送信方法

**方法 1: request パラメータ**
```http
GET /authorize?
  client_id=s6BhdRkqt3
  &request=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...
```

**方法 2: request_uri パラメータ（PAR 推奨）**
```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

request=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...
&client_id=s6BhdRkqt3
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJQUzI1NiIs...
```

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "request_uri": "urn:ietf:params:oauth:request_uri:6esc_11ACC5bwc014ltc14eY22c",
  "expires_in": 90
}
```

```http
GET /authorize?
  client_id=s6BhdRkqt3
  &request_uri=urn:ietf:params:oauth:request_uri:6esc_11ACC5bwc014ltc14eY22c
```

### 認可レスポンスの保護

Advanced Profile では、認可レスポンスを保護する必要があります。

#### 方法 1: JARM（JWT Secured Authorization Response Mode）

```
認可レスポンスが JWT として返される:

GET https://client.example.com/callback?
  response=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9.
    eyJpc3MiOiJodHRwczovL2F1dGguZXhhbXBsZS5jb20iLCJhdWQiOiJzNkJoZFJrcXQzIiwiZXhwIjoxNzA0MTUzNjAwLCJjb2RlIjoiU3BseGxPQmVaUVFZYllTNld4U2JJQSIsInN0YXRlIjoiYWYwaWZqc2xka2oifQ.
    signature

JWT のペイロード:
{
  "iss": "https://auth.example.com",
  "aud": "s6BhdRkqt3",
  "exp": 1704153600,
  "code": "SplxlOBeZQQYbYS6WxSbIA",
  "state": "af0ifjsldkj"
}
```

#### 方法 2: Hybrid Flow + ID Token 検証

```
response_type=code id_token を使用:

GET https://client.example.com/callback#
  code=SplxlOBeZQQYbYS6WxSbIA
  &id_token=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...
  &state=af0ifjsldkj

ID Token で code を検証:
{
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "aud": "s6BhdRkqt3",
  "c_hash": "LDktKdoQak3Pk0cnXxCltA",  ← code のハッシュ
  "s_hash": "abc123...",                ← state のハッシュ
  "nonce": "n-0S6_WzA2Mj",
  "exp": 1704153600
}

c_hash の計算:
  1. code を ASCII オクテットとして取得
  2. SHA-256 でハッシュ
  3. 左半分（128 ビット）を Base64URL エンコード
```

### Sender-Constrained Access Tokens

アクセストークンをクライアントにバインドすることが必須です。

#### mTLS Certificate-Bound Tokens

```json
{
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "aud": "https://api.example.com",
  "exp": 1704153600,
  "cnf": {
    "x5t#S256": "bwcK0esc3ACC3DB2Y5_lESsXE8o9ltc14eY22c"
  }
}
```

リソースサーバーは、mTLS 接続のクライアント証明書のハッシュと `cnf.x5t#S256` を比較します。

### 署名アルゴリズム

```
Advanced Profile で許可されるアルゴリズム:

ID トークン:
  ✅ PS256, PS384, PS512
  ✅ ES256, ES384, ES512
  ❌ RS256, RS384, RS512（禁止）

Request Object:
  ✅ PS256, PS384, PS512
  ✅ ES256, ES384, ES512

クライアントアサーション:
  ✅ PS256, PS384, PS512
  ✅ ES256, ES384, ES512
```

### 完全な実装例

#### Java（認可サーバー）

```java
@Service
public class FAPIAdvancedAuthorizationService {

    private final JWTVerifier jwtVerifier;
    private final ClientRepository clientRepository;

    public AuthorizationResponse authorize(HttpServletRequest request) {
        // 1. Request Object の取得と検証
        String requestObject = getRequestObject(request);
        JWT requestJwt = validateRequestObject(requestObject);

        // 2. クライアントの検証
        String clientId = requestJwt.getClaim("client_id");
        ClientInfo client = clientRepository.findByClientId(clientId);

        // 3. クライアント認証方式の確認
        if (!isAdvancedAuthMethod(client.getTokenEndpointAuthMethod())) {
            throw new InvalidClientException(
                "Only private_key_jwt or mTLS is allowed"
            );
        }

        // 4. 署名アルゴリズムの確認
        JWSAlgorithm alg = requestJwt.getHeader().getAlgorithm();
        if (!isAllowedAlgorithm(alg)) {
            throw new InvalidRequestException(
                "Algorithm " + alg + " is not allowed"
            );
        }

        // 5. PKCE の確認
        String codeChallenge = requestJwt.getClaim("code_challenge");
        String codeChallengeMethod = requestJwt.getClaim("code_challenge_method");
        if (codeChallenge == null || !"S256".equals(codeChallengeMethod)) {
            throw new InvalidRequestException("PKCE with S256 is required");
        }

        // 6. 認可処理...
        return processAuthorization(requestJwt, client);
    }

    private boolean isAdvancedAuthMethod(String method) {
        return "private_key_jwt".equals(method) ||
               "tls_client_auth".equals(method) ||
               "self_signed_tls_client_auth".equals(method);
    }

    private boolean isAllowedAlgorithm(JWSAlgorithm alg) {
        return JWSAlgorithm.PS256.equals(alg) ||
               JWSAlgorithm.PS384.equals(alg) ||
               JWSAlgorithm.PS512.equals(alg) ||
               JWSAlgorithm.ES256.equals(alg) ||
               JWSAlgorithm.ES384.equals(alg) ||
               JWSAlgorithm.ES512.equals(alg);
    }
}
```

#### JavaScript（クライアント側）

```javascript
class FAPIAdvancedClient {
  constructor(config) {
    this.clientId = config.clientId;
    this.parEndpoint = config.parEndpoint;
    this.authorizationEndpoint = config.authorizationEndpoint;
    this.tokenEndpoint = config.tokenEndpoint;
    this.redirectUri = config.redirectUri;
    this.privateKey = config.privateKey;
    this.keyId = config.keyId;
  }

  async startAuthorization(scope) {
    // PKCE
    const codeVerifier = this.generateCodeVerifier();
    const codeChallenge = await this.calculateCodeChallenge(codeVerifier);

    const state = this.generateSecureRandom();
    const nonce = this.generateSecureRandom();

    // セッションに保存
    sessionStorage.setItem('code_verifier', codeVerifier);
    sessionStorage.setItem('state', state);
    sessionStorage.setItem('nonce', nonce);

    // Request Object を構築
    const requestObject = await this.buildRequestObject({
      response_type: 'code',
      client_id: this.clientId,
      redirect_uri: this.redirectUri,
      scope: scope,
      state: state,
      nonce: nonce,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256'
    });

    // PAR でリクエストを送信
    const clientAssertion = await this.buildClientAssertion();

    const parResponse = await fetch(this.parEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: new URLSearchParams({
        request: requestObject,
        client_id: this.clientId,
        client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer',
        client_assertion: clientAssertion
      })
    });

    const { request_uri } = await parResponse.json();

    // 認可エンドポイントにリダイレクト
    const authUrl = new URL(this.authorizationEndpoint);
    authUrl.searchParams.set('client_id', this.clientId);
    authUrl.searchParams.set('request_uri', request_uri);

    window.location.href = authUrl.toString();
  }

  async buildRequestObject(params) {
    const now = Math.floor(Date.now() / 1000);

    const header = {
      alg: 'PS256',
      typ: 'JWT',
      kid: this.keyId
    };

    const payload = {
      iss: this.clientId,
      aud: this.authorizationEndpoint,
      iat: now,
      exp: now + 300,
      nbf: now,
      ...params
    };

    return await this.signJWT(header, payload, this.privateKey);
  }

  async handleJARMCallback(jarmResponse) {
    // JARM レスポンスを検証
    const payload = await this.verifyJWT(jarmResponse);

    // state の検証
    const savedState = sessionStorage.getItem('state');
    if (payload.state !== savedState) {
      throw new Error('State mismatch');
    }

    // エラーチェック
    if (payload.error) {
      throw new Error(`Authorization error: ${payload.error_description || payload.error}`);
    }

    // トークン交換
    return await this.exchangeCode(payload.code);
  }
}
```

### セキュリティチェックリスト

| 項目 | Baseline | Advanced |
|------|----------|----------|
| TLS 1.2+ | ✅ | ✅ |
| PKCE S256 | ✅ | ✅ |
| state | ✅ | ✅ |
| nonce | ✅ | ✅ |
| private_key_jwt/mTLS | 推奨 | ✅ 必須 |
| Request Object | 任意 | ✅ 必須 |
| JARM または Hybrid+ID Token | 任意 | ✅ 必須 |
| PS256/ES256 | 推奨 | ✅ 必須 |
| Certificate-Bound Tokens | 任意 | ✅ 必須 |

---

## 参考リンク

- [FAPI 1.0 Advanced Profile](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [FAPI 1.0 Baseline Profile](https://openid.net/specs/openid-financial-api-part-1-1_0.html)
- [JWT Secured Authorization Response Mode (JARM)](https://openid.net/specs/openid-financial-api-jarm-01.html)
- [RFC 9126 - Pushed Authorization Requests](https://datatracker.ietf.org/doc/html/rfc9126)
