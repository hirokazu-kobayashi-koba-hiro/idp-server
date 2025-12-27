# FAPI 2.0 Security Profile

FAPI 2.0 Security Profile は、FAPI 1.0 の経験を活かして再設計された次世代のセキュリティプロファイルです。

---

## 第1部: 概要編

### FAPI 2.0 とは？

FAPI 2.0 は、FAPI 1.0 を簡素化しつつ、より強力なセキュリティを提供する新しいプロファイルです。

```
FAPI 2.0 の特徴:

1. 簡素化
   - Hybrid Flow 廃止
   - 認可コードフローのみ
   - 複雑なオプションを削減

2. 強化
   - PAR 必須
   - Sender-Constrained Tokens 必須
   - JARM または iss パラメータ

3. 新機能
   - RFC 9396 RAR サポート
   - Grant Management サポート
```

### FAPI 1.0 vs FAPI 2.0

| 項目 | FAPI 1.0 | FAPI 2.0 |
|------|----------|----------|
| レスポンスタイプ | code, code id_token | code のみ |
| PAR | 推奨 | 必須 |
| Request Object | 必須（Advanced） | PAR で送信 |
| レスポンス保護 | JARM または Hybrid | JARM または RFC 9207 iss |
| トークンバインディング | mTLS | mTLS または DPoP |
| Grant Management | なし | サポート |

---

## 第2部: 詳細編

### 認可リクエスト

FAPI 2.0 では、認可リクエストは必ず PAR 経由で行います。

```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

response_type=code
&client_id=s6BhdRkqt3
&redirect_uri=https://client.example.com/callback
&scope=openid accounts
&state=af0ifjsldkj
&nonce=n-0S6_WzA2Mj
&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
&code_challenge_method=S256
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

### クライアント認証

```
許可されるクライアント認証:

✅ private_key_jwt
  - 非対称鍵 JWT（PS256, ES256）

✅ tls_client_auth
  - PKI ベースの mTLS

✅ self_signed_tls_client_auth
  - 自己署名証明書による mTLS

❌ client_secret_* 系は禁止
```

### 認可レスポンス

FAPI 2.0 では、以下のいずれかで認可レスポンスを保護します。

#### 方法 1: JARM

```
response_mode=jwt を使用:

GET /callback?
  response=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...

JWT ペイロード:
{
  "iss": "https://auth.example.com",
  "aud": "s6BhdRkqt3",
  "exp": 1704153600,
  "code": "SplxlOBeZQQYbYS6WxSbIA",
  "state": "af0ifjsldkj",
  "iss": "https://auth.example.com"
}
```

#### 方法 2: RFC 9207 iss パラメータ

```
iss パラメータを含む通常のレスポンス:

GET /callback?
  code=SplxlOBeZQQYbYS6WxSbIA
  &state=af0ifjsldkj
  &iss=https://auth.example.com

クライアントは iss を検証して Mix-Up 攻撃を防止
```

### Sender-Constrained Access Tokens

FAPI 2.0 では、アクセストークンのバインディングが必須です。

#### DPoP

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
DPoP: eyJhbGciOiJFUzI1NiIsImp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6Ii4uLiIsInkiOiIuLi4ifSwidHlwIjoiZHBvcCtqd3QifQ...

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&client_id=s6BhdRkqt3
&code_verifier=...
```

レスポンス:
```json
{
  "access_token": "eyJhbGciOiJQUzI1NiIs...",
  "token_type": "DPoP",
  "expires_in": 3600
}
```

アクセストークン:
```json
{
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "aud": "https://api.example.com",
  "exp": 1704153600,
  "cnf": {
    "jkt": "0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I"
  }
}
```

#### mTLS

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
(TLS クライアント証明書で接続)

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&client_id=s6BhdRkqt3
&code_verifier=...
```

アクセストークン:
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

### Rich Authorization Requests（RAR）

FAPI 2.0 では RFC 9396 の RAR がサポートされます。

```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/json

{
  "response_type": "code",
  "client_id": "s6BhdRkqt3",
  "redirect_uri": "https://client.example.com/callback",
  "scope": "openid",
  "authorization_details": [
    {
      "type": "payment_initiation",
      "instructedAmount": {
        "amount": "100.00",
        "currency": "EUR"
      },
      "creditorAccount": {
        "iban": "DE89370400440532013000"
      }
    }
  ],
  ...
}
```

### Grant Management

ユーザーが付与した認可を管理する機能。

```
Grant Management の操作:

1. 認可の照会
   GET /grants/{grant_id}

2. 認可の一覧
   GET /grants

3. 認可の取消
   DELETE /grants/{grant_id}
```

### 実装例

#### Java（クライアント側）

```java
public class FAPI2Client {

    private final String clientId;
    private final String parEndpoint;
    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final PrivateKey privateKey;
    private final ECPublicKey dpopPublicKey;
    private final ECPrivateKey dpopPrivateKey;

    public String startAuthorization(AuthorizationRequest request) throws Exception {
        // PKCE
        String codeVerifier = PKCEUtil.generateCodeVerifier();
        String codeChallenge = PKCEUtil.calculateS256(codeVerifier);

        String state = generateSecureRandom();
        String nonce = generateSecureRandom();

        // セッションに保存
        session.setAttribute("code_verifier", codeVerifier);
        session.setAttribute("state", state);
        session.setAttribute("nonce", nonce);

        // クライアントアサーション
        String clientAssertion = buildClientAssertion();

        // PAR リクエスト
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("response_type", "code");
        params.add("client_id", clientId);
        params.add("redirect_uri", request.getRedirectUri());
        params.add("scope", request.getScope());
        params.add("state", state);
        params.add("nonce", nonce);
        params.add("code_challenge", codeChallenge);
        params.add("code_challenge_method", "S256");
        params.add("client_assertion_type",
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        params.add("client_assertion", clientAssertion);

        // RAR がある場合
        if (request.getAuthorizationDetails() != null) {
            params.add("authorization_details",
                objectMapper.writeValueAsString(request.getAuthorizationDetails()));
        }

        ResponseEntity<PARResponse> parResponse = restTemplate.postForEntity(
            parEndpoint,
            new HttpEntity<>(params, headers),
            PARResponse.class
        );

        // 認可 URL を構築
        return UriComponentsBuilder.fromUriString(authorizationEndpoint)
            .queryParam("client_id", clientId)
            .queryParam("request_uri", parResponse.getBody().getRequestUri())
            .build()
            .toUriString();
    }

    public TokenResponse exchangeCode(String code, String codeVerifier) throws Exception {
        // DPoP Proof を生成
        String dpopProof = buildDPoPProof("POST", tokenEndpoint);

        // クライアントアサーション
        String clientAssertion = buildClientAssertion();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("DPoP", dpopProof);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("client_id", clientId);
        body.add("code_verifier", codeVerifier);
        body.add("client_assertion_type",
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        body.add("client_assertion", clientAssertion);

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
            tokenEndpoint,
            new HttpEntity<>(body, headers),
            TokenResponse.class
        );

        return response.getBody();
    }

    private String buildDPoPProof(String method, String uri) throws Exception {
        JWK jwk = new ECKey.Builder(Curve.P_256, dpopPublicKey).build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("dpop+jwt"))
            .jwk(jwk)
            .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .claim("htm", method)
            .claim("htu", uri)
            .issueTime(new Date())
            .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(dpopPrivateKey));

        return jwt.serialize();
    }
}
```

### 移行ガイド

```
FAPI 1.0 から FAPI 2.0 への移行:

1. response_type の変更
   code id_token → code

2. PAR の導入
   - すべての認可リクエストを PAR 経由に
   - Request Object は不要（PAR で代替）

3. レスポンス保護の更新
   - Hybrid Flow の廃止
   - JARM または iss パラメータ

4. DPoP の追加（オプション）
   - mTLS に加えて DPoP もサポート
   - クライアントの選択肢を増やす

5. Grant Management の導入
   - ユーザー向けの認可管理 UI
```

### セキュリティチェックリスト

| 項目 | 要件 |
|------|------|
| PAR | ✅ 必須 |
| PKCE S256 | ✅ 必須 |
| private_key_jwt または mTLS | ✅ 必須 |
| JARM または iss | ✅ 必須 |
| DPoP または mTLS | ✅ 必須 |
| PS256 または ES256 | ✅ 必須 |
| nonce | ✅ 必須 |
| state | ✅ 必須 |

---

## 参考リンク

- [FAPI 2.0 Security Profile](https://openid.bitbucket.io/fapi/fapi-2_0-security-profile.html)
- [FAPI 2.0 Message Signing](https://openid.bitbucket.io/fapi/fapi-2_0-message-signing.html)
- [RFC 9449 - DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
- [RFC 9396 - RAR](https://datatracker.ietf.org/doc/html/rfc9396)
- [Grant Management for OAuth 2.0](https://openid.bitbucket.io/fapi/fapi-grant-management.html)
