# RFC 8707: OAuth 2.0 リソースインジケーター

RFC 8707 は、クライアントがアクセスしたいリソースサーバーを明示的に指定するための拡張仕様です。

---

## 第1部: 概要編

### リソースインジケーターとは？

リソースインジケーター（Resource Indicator）は、トークンの**対象リソースサーバー**を明示的に指定するパラメータです。

```
従来:
  クライアント ──── scope=read write ────► 認可サーバー
                                              │
                                              ▼
                                    トークン発行
                                    （どのリソース向け？）

RFC 8707:
  クライアント ── resource=https://api.example.com ──► 認可サーバー
              ── scope=read write ──────────────────►
                                                         │
                                                         ▼
                                               api.example.com 向け
                                               トークン発行
```

### なぜリソースインジケーターが必要なのか？

| 課題 | 説明 |
|------|------|
| トークンの濫用 | あるリソース用のトークンが別のリソースで使われる |
| スコープの曖昧さ | `read` がどのリソースに対する read なのか不明 |
| マルチテナント | テナントごとに異なるリソースを区別できない |
| audience の不明確さ | トークンの正当な受け取り手が不明 |

```
問題シナリオ:
  Resource A 用のトークンが Resource B で使われる

  ┌──────────┐    token    ┌────────────┐
  │ クライアント │ ─────────► │ Resource A │  ← 正当
  │           │            └────────────┘
  │           │    token    ┌────────────┐
  │           │ ─────────► │ Resource B │  ← 不正使用
  └──────────┘            └────────────┘

解決策:
  トークンに audience を明示的に設定
  Resource B はトークンを拒否
```

### 基本的な使い方

```http
GET /authorize?
  response_type=code
  &client_id=s6BhdRkqt3
  &redirect_uri=https://client.example.com/callback
  &scope=read write
  &resource=https://api.example.com
  &resource=https://calendar.example.com
```

複数のリソースを指定可能です。

---

## 第2部: 詳細編

### resource パラメータ

| 特性 | 説明 |
|------|------|
| 形式 | 絶対 URI |
| 複数指定 | 可能（複数の `resource` パラメータ） |
| 使用場所 | 認可エンドポイント、トークンエンドポイント |
| フラグメント | 禁止 |

```
有効な値:
  ✅ https://api.example.com
  ✅ https://api.example.com/v1
  ✅ urn:example:resource

無効な値:
  ❌ https://api.example.com#fragment  (フラグメント付き)
  ❌ api.example.com  (相対 URI)
```

### 認可リクエスト

```http
GET /authorize?
  response_type=code
  &client_id=s6BhdRkqt3
  &redirect_uri=https://client.example.com/callback
  &scope=read write
  &resource=https://api.example.com
  &state=xyz
  &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
  &code_challenge_method=S256
```

認可サーバーは、指定されたリソースに対するアクセス許可を求めます。

### トークンリクエスト

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https://client.example.com/callback
&client_id=s6BhdRkqt3
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
&resource=https://api.example.com
```

### トークンのスコープとリソースの関係

```
resource と scope の組み合わせ:

認可リクエスト:
  resource=https://api.example.com
  resource=https://calendar.example.com
  scope=read write

トークンリクエスト（api.example.com 用）:
  resource=https://api.example.com
  → トークンは api.example.com 用の read write のみ

トークンリクエスト（calendar.example.com 用）:
  resource=https://calendar.example.com
  → トークンは calendar.example.com 用の read write のみ
```

### リソースごとのトークン発行

```
シナリオ: 2 つのリソースにアクセスしたい

1. 認可リクエスト（両方のリソースを指定）
   resource=https://api.example.com
   resource=https://files.example.com
   scope=read write

2. トークンリクエスト #1（API 用）
   resource=https://api.example.com
   → API 用のアクセストークンを取得

3. トークンリクエスト #2（Files 用）
   resource=https://files.example.com
   → Files 用のアクセストークンを取得

結果:
  ┌─────────────────────┐
  │ Token for API       │ ─► api.example.com
  │ aud: api.example.com│
  └─────────────────────┘

  ┌─────────────────────┐
  │ Token for Files     │ ─► files.example.com
  │ aud: files.example.com│
  └─────────────────────┘
```

### JWT アクセストークンの audience

resource パラメータで指定されたリソースは、JWT アクセストークンの `aud` クレームに設定されます。

```json
{
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "aud": "https://api.example.com",
  "scope": "read write",
  "exp": 1704153600,
  "iat": 1704150000
}
```

リソースサーバーは `aud` を検証し、自分宛てのトークンのみを受け入れます。

### 複数リソースと単一トークン

認可サーバーによっては、複数のリソースに対する単一のトークンを発行することもあります。

```json
{
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "aud": [
    "https://api.example.com",
    "https://files.example.com"
  ],
  "scope": "read write",
  "exp": 1704153600
}
```

ただし、セキュリティ上は**リソースごとに個別のトークン**を発行することを推奨します。

### リフレッシュトークン

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&refresh_token=tGzv3JOkF0XG5Qx2TlKWIA
&resource=https://api.example.com
```

リフレッシュ時にも `resource` を指定できます。

### 認可サーバーの処理

```
リソースインジケーターの処理フロー:

1. resource パラメータの検証
   ├── 絶対 URI か
   ├── フラグメントがないか
   └── 登録されたリソースか（ポリシーによる）

2. クライアントとリソースの関係
   └── クライアントは指定されたリソースにアクセス可能か

3. scope とリソースの関係
   └── 指定された scope は指定されたリソースで有効か

4. トークン発行
   ├── aud にリソースを設定
   └── scope はリソース固有にフィルタリング
```

### 実装例

#### Java（認可サーバー）

```java
@GetMapping("/authorize")
public ResponseEntity<Void> authorize(
        @RequestParam("response_type") String responseType,
        @RequestParam("client_id") String clientId,
        @RequestParam("redirect_uri") String redirectUri,
        @RequestParam("scope") String scope,
        @RequestParam(value = "resource", required = false) List<String> resources,
        @RequestParam("state") String state) {

    // resource パラメータの検証
    if (resources != null) {
        for (String resource : resources) {
            validateResourceIndicator(resource);
        }
    }

    // 認可コードにリソース情報を紐付け
    AuthorizationCode code = createAuthorizationCode(
        clientId, redirectUri, scope, resources, state
    );

    return ResponseEntity.status(302)
        .location(URI.create(redirectUri + "?code=" + code.getValue() + "&state=" + state))
        .build();
}

@PostMapping("/token")
public ResponseEntity<TokenResponse> token(
        @RequestParam("grant_type") String grantType,
        @RequestParam("code") String code,
        @RequestParam(value = "resource", required = false) String resource) {

    AuthorizationCode authCode = validateAuthorizationCode(code);

    // 認可時に指定されたリソースと一致するか確認
    if (resource != null) {
        if (!authCode.getResources().contains(resource)) {
            throw new InvalidTargetException("Resource not authorized");
        }
    }

    // リソース固有のトークンを発行
    String audience = resource != null ? resource : authCode.getResources().get(0);
    String scopeForResource = filterScopeForResource(authCode.getScope(), audience);

    String accessToken = generateAccessToken(
        authCode.getSubject(),
        audience,
        scopeForResource
    );

    return ResponseEntity.ok(TokenResponse.builder()
        .accessToken(accessToken)
        .tokenType("Bearer")
        .expiresIn(3600)
        .scope(scopeForResource)
        .build());
}

private void validateResourceIndicator(String resource) {
    try {
        URI uri = new URI(resource);
        if (!uri.isAbsolute()) {
            throw new InvalidTargetException("Resource must be an absolute URI");
        }
        if (uri.getFragment() != null) {
            throw new InvalidTargetException("Resource must not contain a fragment");
        }
    } catch (URISyntaxException e) {
        throw new InvalidTargetException("Invalid resource URI");
    }
}
```

#### JavaScript（クライアント側）

```javascript
class OAuth2Client {
  constructor(config) {
    this.authorizationEndpoint = config.authorizationEndpoint;
    this.tokenEndpoint = config.tokenEndpoint;
    this.clientId = config.clientId;
    this.redirectUri = config.redirectUri;
  }

  // 認可リクエスト URL を生成
  getAuthorizationUrl(options) {
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: this.clientId,
      redirect_uri: this.redirectUri,
      scope: options.scope,
      state: options.state,
      code_challenge: options.codeChallenge,
      code_challenge_method: 'S256'
    });

    // 複数のリソースを追加
    if (options.resources) {
      for (const resource of options.resources) {
        params.append('resource', resource);
      }
    }

    return `${this.authorizationEndpoint}?${params.toString()}`;
  }

  // 特定のリソース用のトークンを取得
  async getTokenForResource(code, codeVerifier, resource) {
    const params = new URLSearchParams({
      grant_type: 'authorization_code',
      code: code,
      redirect_uri: this.redirectUri,
      client_id: this.clientId,
      code_verifier: codeVerifier
    });

    if (resource) {
      params.append('resource', resource);
    }

    const response = await fetch(this.tokenEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: params
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(`Token request failed: ${error.error_description}`);
    }

    return response.json();
  }

  // 複数リソースのトークンを取得
  async getTokensForResources(code, codeVerifier, resources) {
    const tokens = {};

    for (const resource of resources) {
      tokens[resource] = await this.getTokenForResource(code, codeVerifier, resource);
    }

    return tokens;
  }
}

// 使用例
const client = new OAuth2Client({
  authorizationEndpoint: 'https://auth.example.com/authorize',
  tokenEndpoint: 'https://auth.example.com/token',
  clientId: 's6BhdRkqt3',
  redirectUri: 'https://client.example.com/callback'
});

// 認可リクエスト
const authUrl = client.getAuthorizationUrl({
  scope: 'read write',
  resources: [
    'https://api.example.com',
    'https://files.example.com'
  ],
  state: generateState(),
  codeChallenge: await generateCodeChallenge(codeVerifier)
});

// コールバック後、各リソース用のトークンを取得
const apiToken = await client.getTokenForResource(code, codeVerifier, 'https://api.example.com');
const filesToken = await client.getTokenForResource(code, codeVerifier, 'https://files.example.com');
```

### リソースサーバーの検証

```java
public void validateAccessToken(String token) {
    JWT jwt = JWT.parse(token);

    // audience の検証
    List<String> audience = jwt.getAudience();
    if (!audience.contains(MY_RESOURCE_IDENTIFIER)) {
        throw new InvalidTokenException("Token is not intended for this resource");
    }

    // その他の検証...
}
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| リソースごとのトークン | 複数リソースには個別のトークンを発行 |
| audience 検証 | リソースサーバーは必ず aud を検証 |
| スコープの分離 | リソースごとにスコープをフィルタリング |
| 許可されたリソース | クライアントがアクセス可能なリソースを制限 |
| トークン漏洩の影響範囲 | リソース固有のトークンで影響を限定 |

### RFC 8707 と RFC 8693 の関係

```
RFC 8707（Resource Indicators）:
  認可時にリソースを指定

RFC 8693（Token Exchange）:
  トークン交換時に audience を指定

組み合わせ:
  resource パラメータで取得したトークンを
  Token Exchange で別のリソース用に交換
```

---

## 参考リンク

- [RFC 8707 - Resource Indicators for OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc8707)
- [RFC 8693 - OAuth 2.0 Token Exchange](https://datatracker.ietf.org/doc/html/rfc8693)
- [RFC 6749 - The OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
