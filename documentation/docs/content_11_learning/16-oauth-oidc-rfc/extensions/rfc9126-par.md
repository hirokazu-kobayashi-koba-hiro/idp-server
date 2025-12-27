# RFC 9126: PAR（Pushed Authorization Requests）

RFC 9126 は、認可リクエストのパラメータをバックチャネルで事前送信するための仕様です。このドキュメントでは、PAR の仕組みと実装方法を解説します。

---

## 第1部: 概要編

### PAR とは何か？

PAR（Pushed Authorization Requests）は、認可リクエストのパラメータを**フロントチャネル（ブラウザ）ではなくバックチャネル（サーバー間通信）で送信**する仕組みです。

従来の認可リクエストでは、すべてのパラメータが URL に含まれていました。PAR では、パラメータを事前に認可サーバーに送信し、代わりに短い参照 URI を受け取ります。

### なぜ PAR が必要なのか？

従来の認可リクエストには以下の問題があります。

#### 問題 1: URL 長の制限

```
従来のリクエスト:
GET /authorize?
  response_type=code
  &client_id=xxx
  &redirect_uri=https://...
  &scope=openid profile email address phone
  &state=abc
  &nonce=xyz
  &code_challenge=...
  &code_challenge_method=S256
  &claims={"userinfo":{"given_name":{"essential":true},...}}
  &request=eyJhbGciOiJSUzI1NiIsInR5cCI6...（長大な JWT）

→ URL が数千文字になり、ブラウザやサーバーの制限に引っかかる
```

#### 問題 2: パラメータの露出

```
ブラウザの URL バーに機密情報が表示される:
- scope（要求する権限）
- claims（要求するユーザー情報）
- state（CSRF トークン）

→ ショルダーハッキングや履歴からの漏洩リスク
```

#### 問題 3: パラメータの改ざん

```
攻撃者がブラウザ上でパラメータを改ざん:
- redirect_uri を変更
- scope を拡大
- state を削除

→ JAR（署名付きリクエスト）で防げるが、URL 長問題は残る
```

### PAR の解決策

```
┌──────────┐                              ┌──────────────┐
│ クライアント│                              │  認可サーバー  │
└────┬─────┘                              └──────┬───────┘
     │                                           │
     │  (1) POST /par                            │
     │  認可パラメータをすべて送信               │
     │ ─────────────────────────────────────────►│
     │                                           │
     │  (2) 201 Created                          │
     │  request_uri=urn:ietf:params:oauth:...    │
     │ ◄─────────────────────────────────────────│
     │                                           │
     │                                           │
     │  (3) ブラウザをリダイレクト                │
     │  GET /authorize?                          │
     │      client_id=xxx                        │
     │      &request_uri=urn:ietf:params:...     │
     │ ─────────────────────────────────────────►│
     │                                           │
     │  (4) 通常の認可フロー継続                  │
     │                                           │
```

**メリット:**
- URL が短くなる（request_uri のみ）
- パラメータがブラウザに露出しない
- バックチャネルでクライアント認証が行われる

---

## 第2部: 詳細編

### PAR エンドポイント

#### リクエスト

```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

response_type=code
&client_id=s6BhdRkqt3
&redirect_uri=https%3A%2F%2Fclient.example.org%2Fcallback
&scope=openid%20profile
&state=af0ifjsldkj
&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
&code_challenge_method=S256
```

通常の認可リクエストと同じパラメータを POST で送信。**クライアント認証が必須**。

#### レスポンス

```http
HTTP/1.1 201 Created
Content-Type: application/json
Cache-Control: no-cache, no-store

{
  "request_uri": "urn:ietf:params:oauth:request_uri:bwc4JK-ESC0w8acc191e-Y1LTC2",
  "expires_in": 60
}
```

| フィールド | 説明 |
|-----------|------|
| `request_uri` | 認可リクエストへの参照 URI |
| `expires_in` | 有効期限（秒）。通常は短い（60秒など） |

### 認可リクエスト

PAR で取得した `request_uri` を使って認可エンドポイントにリダイレクト。

```http
GET /authorize?
  client_id=s6BhdRkqt3
  &request_uri=urn%3Aietf%3Aparams%3Aoauth%3Arequest_uri%3Abwc4JK-ESC0w8acc191e-Y1LTC2
HTTP/1.1
Host: auth.example.com
```

**注意**: `client_id` は必須。`request_uri` 内の `client_id` と一致する必要がある。

### request_uri の要件

| 要件 | 説明 |
|------|------|
| 形式 | `urn:ietf:params:oauth:request_uri:` で始まる |
| 一意性 | 推測困難な十分なエントロピーを持つ |
| 有効期限 | 短い（通常 60〜600 秒） |
| 一回限り | 使用後は無効化（推奨） |

### エラーレスポンス

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "invalid_request",
  "error_description": "The redirect_uri is not registered"
}
```

| エラーコード | 説明 |
|--------------|------|
| `invalid_request` | リクエストパラメータが不正 |
| `invalid_client` | クライアント認証失敗 |
| `unauthorized_client` | クライアントに PAR の権限がない |
| `access_denied` | リクエストが拒否された |

### JAR との組み合わせ

PAR と JAR（JWT-Secured Authorization Request）は組み合わせ可能。

```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

request=eyJhbGciOiJSUzI1NiIsInR5cCI6Im9hdXRoLWF1dGh6LXJlcStqd3QifQ...
```

この場合、認可パラメータは署名付き JWT（request object）として送信される。

**二重の保護:**
- PAR: パラメータがフロントチャネルに露出しない
- JAR: パラメータの改ざんを検知可能

### 実装例

#### クライアント側（Java）

```java
public class PARClient {
    
    private final WebClient webClient;
    private final String parEndpoint;
    private final String clientId;
    private final String clientSecret;
    
    public PARResponse pushAuthorizationRequest(AuthorizationRequest authzRequest) {
        String credentials = Base64.getEncoder()
            .encodeToString((clientId + ":" + clientSecret).getBytes());
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("response_type", "code");
        params.add("client_id", clientId);
        params.add("redirect_uri", authzRequest.getRedirectUri());
        params.add("scope", authzRequest.getScope());
        params.add("state", authzRequest.getState());
        params.add("code_challenge", authzRequest.getCodeChallenge());
        params.add("code_challenge_method", "S256");
        
        return webClient.post()
            .uri(parEndpoint)
            .header("Authorization", "Basic " + credentials)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(params))
            .retrieve()
            .bodyToMono(PARResponse.class)
            .block();
    }
    
    public String buildAuthorizationUrl(String requestUri) {
        return UriComponentsBuilder
            .fromHttpUrl(authorizationEndpoint)
            .queryParam("client_id", clientId)
            .queryParam("request_uri", requestUri)
            .build()
            .toUriString();
    }
}

@Data
public class PARResponse {
    @JsonProperty("request_uri")
    private String requestUri;
    
    @JsonProperty("expires_in")
    private int expiresIn;
}
```

#### 認可サーバー側

```java
@RestController
public class PAREndpoint {
    
    private final PARRequestStore requestStore;
    private final ClientAuthenticator clientAuthenticator;
    
    @PostMapping("/par")
    public ResponseEntity<PARResponse> pushAuthorizationRequest(
            @RequestHeader("Authorization") String authorization,
            @RequestParam Map<String, String> params) {
        
        // 1. クライアント認証
        Client client = clientAuthenticator.authenticate(authorization);
        if (client == null) {
            return ResponseEntity.status(401)
                .body(new ErrorResponse("invalid_client"));
        }
        
        // 2. パラメータ検証
        String clientId = params.get("client_id");
        if (!client.getClientId().equals(clientId)) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("invalid_request", 
                    "client_id mismatch"));
        }
        
        // 3. redirect_uri 検証
        String redirectUri = params.get("redirect_uri");
        if (!client.getRegisteredRedirectUris().contains(redirectUri)) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("invalid_request",
                    "redirect_uri not registered"));
        }
        
        // 4. request_uri 生成・保存
        String requestUri = generateRequestUri();
        int expiresIn = 60;
        
        requestStore.save(requestUri, params, 
            Instant.now().plusSeconds(expiresIn));
        
        // 5. レスポンス
        return ResponseEntity.status(201)
            .body(new PARResponse(requestUri, expiresIn));
    }
    
    private String generateRequestUri() {
        String random = UUID.randomUUID().toString();
        return "urn:ietf:params:oauth:request_uri:" + random;
    }
}
```

### FAPI における PAR

| プロファイル | PAR 要件 |
|-------------|---------|
| FAPI 1.0 Baseline | 任意 |
| FAPI 1.0 Advanced | JAR または PAR が必要 |
| FAPI 2.0 | **PAR 必須** |

FAPI 2.0 では PAR が必須となり、すべての認可リクエストパラメータがバックチャネル経由で送信される。

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| クライアント認証 | PAR エンドポイントでは必須 |
| request_uri の有効期限 | 短く設定（60秒推奨） |
| request_uri の一回限り使用 | 使用後は即座に無効化 |
| HTTPS | 必須 |
| request_uri の推測困難性 | 十分なエントロピー（128ビット以上） |

### PAR vs JAR

| 観点 | PAR | JAR |
|------|-----|-----|
| パラメータの露出 | ❌ 露出しない | ⚠️ 署名付きだが URL に含まれる |
| URL 長 | ✅ 短い | ❌ JWT が長大になりうる |
| クライアント認証 | ✅ 必須 | ❌ 任意 |
| 改ざん検知 | ⚠️ 保存時点のみ | ✅ 署名で検証可能 |
| 組み合わせ | PAR + JAR で最強 | |

---

## 参考リンク

- [RFC 9126 - OAuth 2.0 Pushed Authorization Requests](https://datatracker.ietf.org/doc/html/rfc9126)
- [RFC 9101 - JWT-Secured Authorization Request (JAR)](https://datatracker.ietf.org/doc/html/rfc9101)
- [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-2_0-security-profile.html)
