# RFC 9207: OAuth 2.0 認可サーバー発行者識別

RFC 9207 は、認可レスポンスに発行者（Issuer）識別子を含めることで、Mix-Up 攻撃を防止するための仕様です。

---

## 第1部: 概要編

### 発行者識別とは？

RFC 9207 は、認可レスポンスに `iss` パラメータを追加して、どの認可サーバーがレスポンスを発行したかを明示します。

```
従来:
  認可サーバー ──► redirect_uri?code=xxx&state=yyy ──► クライアント
                        │
                        └── どの認可サーバーからのレスポンス？

RFC 9207:
  認可サーバー ──► redirect_uri?code=xxx&state=yyy&iss=https://as.example.com ──► クライアント
                        │
                        └── iss で認可サーバーを識別
```

### Mix-Up 攻撃とは？

Mix-Up 攻撃は、複数の認可サーバーを使用するクライアントを標的にした攻撃です。

```
攻撃シナリオ:

1. クライアントが複数の認可サーバー（AS-1, AS-2）をサポート
2. ユーザーが AS-1 で認可を開始
3. 攻撃者が AS-2 の認可レスポンスを AS-1 のものとして偽装
4. クライアントが AS-2 の認可コードを AS-1 に送信
5. AS-1 がエラーを返す、または攻撃者がトークンを取得

  ┌──────────┐    ┌─────────────┐    ┌──────────┐
  │  ユーザー  │    │   攻撃者     │    │ AS-1     │
  │          │    │             │    │          │
  │          │ ◄──┤ AS-2 の     │    │          │
  │          │    │ code を     │    │          │
  │          │    │ AS-1 として  │    │          │
  └──────────┘    │ 送信        │    └──────────┘
                  └─────────────┘
```

### iss パラメータによる防止

```
正当なレスポンス（AS-1）:
  redirect_uri?code=abc&state=xyz&iss=https://as1.example.com
                                       │
                                       └── AS-1 の識別子

攻撃レスポンス（AS-2 の code を AS-1 として偽装）:
  redirect_uri?code=def&state=xyz&iss=https://as2.example.com
                                       │
                                       └── AS-2 の識別子

クライアントの検証:
  1. state から期待される issuer を取得: https://as1.example.com
  2. レスポンスの iss と比較
  3. 一致しなければ拒否 → 攻撃防止
```

---

## 第2部: 詳細編

### iss パラメータ

| 特性 | 説明 |
|------|------|
| 形式 | 認可サーバーの Issuer 識別子（URL） |
| 必須 | 認可サーバーがサポートする場合は必須 |
| 場所 | 認可レスポンス |
| 対象 | 成功レスポンス・エラーレスポンスの両方 |

```
Issuer 識別子の要件:
  - HTTPS スキーム
  - ポート番号はデフォルト（443）または明示的に指定
  - パスはオプション
  - クエリ・フラグメントは禁止

例:
  ✅ https://auth.example.com
  ✅ https://auth.example.com:8443
  ✅ https://auth.example.com/tenant/123
  ❌ https://auth.example.com?query=value
  ❌ https://auth.example.com#fragment
```

### 認可レスポンス

#### 成功レスポンス

```
HTTP/1.1 302 Found
Location: https://client.example.com/callback?
  code=SplxlOBeZQQYbYS6WxSbIA
  &state=af0ifjsldkj
  &iss=https%3A%2F%2Fauth.example.com
```

| パラメータ | 説明 |
|-----------|------|
| `code` | 認可コード |
| `state` | クライアントが送信した state |
| `iss` | 認可サーバーの Issuer 識別子 |

#### エラーレスポンス

```
HTTP/1.1 302 Found
Location: https://client.example.com/callback?
  error=access_denied
  &error_description=The+user+denied+the+request
  &state=af0ifjsldkj
  &iss=https%3A%2F%2Fauth.example.com
```

エラーレスポンスにも `iss` を含めることが推奨されます。

### ディスカバリーによるサポート表明

認可サーバーは、メタデータで `iss` のサポートを表明します。

```json
{
  "issuer": "https://auth.example.com",
  "authorization_endpoint": "https://auth.example.com/authorize",
  "token_endpoint": "https://auth.example.com/token",
  "authorization_response_iss_parameter_supported": true
}
```

| メタデータ | 説明 |
|-----------|------|
| `authorization_response_iss_parameter_supported` | `iss` パラメータをサポートする場合 `true` |

### クライアントの検証手順

```
認可レスポンスの検証フロー:

1. state の検証
   └── 自分が生成した state か

2. state から期待される issuer を取得
   └── 認可リクエスト時に保存しておく

3. iss パラメータの検証
   ├── iss が存在するか
   ├── iss が期待される issuer と一致するか
   └── 一致しなければエラー

4. 以降の通常の処理
   └── トークンリクエストなど
```

### クライアントの実装

```javascript
class OAuth2Client {
  constructor(config) {
    // 複数の認可サーバーをサポート
    this.authServers = config.authServers;  // { 'server1': {...}, 'server2': {...} }
  }

  // 認可リクエストを開始
  startAuthorization(serverId) {
    const server = this.authServers[serverId];
    const state = generateRandomState();

    // state と期待される issuer を保存
    sessionStorage.setItem('oauth_state', state);
    sessionStorage.setItem('expected_issuer', server.issuer);

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: server.clientId,
      redirect_uri: this.redirectUri,
      state: state,
      scope: 'openid profile',
      code_challenge: this.codeChallenge,
      code_challenge_method: 'S256'
    });

    window.location.href = `${server.authorizationEndpoint}?${params}`;
  }

  // コールバックを処理
  handleCallback(params) {
    // state の検証
    const savedState = sessionStorage.getItem('oauth_state');
    if (params.state !== savedState) {
      throw new Error('State mismatch');
    }

    // iss の検証（RFC 9207）
    const expectedIssuer = sessionStorage.getItem('expected_issuer');
    if (params.iss) {
      if (params.iss !== expectedIssuer) {
        throw new Error(`Issuer mismatch: expected ${expectedIssuer}, got ${params.iss}`);
      }
    } else {
      // iss がない場合は警告（または拒否）
      console.warn('Authorization response does not contain iss parameter');
    }

    // エラーチェック
    if (params.error) {
      throw new Error(`Authorization error: ${params.error_description || params.error}`);
    }

    // 正常な場合はトークンリクエストを実行
    return this.exchangeCodeForToken(params.code, expectedIssuer);
  }

  async exchangeCodeForToken(code, issuer) {
    const server = Object.values(this.authServers).find(s => s.issuer === issuer);

    const response = await fetch(server.tokenEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        code: code,
        redirect_uri: this.redirectUri,
        client_id: server.clientId,
        code_verifier: this.codeVerifier
      })
    });

    return response.json();
  }
}

// 使用例
const client = new OAuth2Client({
  authServers: {
    google: {
      issuer: 'https://accounts.google.com',
      authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
      tokenEndpoint: 'https://oauth2.googleapis.com/token',
      clientId: 'google-client-id'
    },
    github: {
      issuer: 'https://github.com',
      authorizationEndpoint: 'https://github.com/login/oauth/authorize',
      tokenEndpoint: 'https://github.com/login/oauth/access_token',
      clientId: 'github-client-id'
    }
  }
});

// Google で認可を開始
client.startAuthorization('google');

// コールバック処理
const params = new URLSearchParams(window.location.search);
await client.handleCallback({
  code: params.get('code'),
  state: params.get('state'),
  iss: params.get('iss'),
  error: params.get('error'),
  error_description: params.get('error_description')
});
```

### Java（認可サーバー）

```java
@GetMapping("/authorize")
public ResponseEntity<Void> authorize(
        @RequestParam("response_type") String responseType,
        @RequestParam("client_id") String clientId,
        @RequestParam("redirect_uri") String redirectUri,
        @RequestParam("state") String state,
        @RequestParam("scope") String scope) {

    // 認可処理...

    // 認可コードを生成
    String code = generateAuthorizationCode();

    // リダイレクト URL を構築（iss を含める）
    String issuer = "https://auth.example.com";
    String redirectUrl = UriComponentsBuilder.fromUriString(redirectUri)
        .queryParam("code", code)
        .queryParam("state", state)
        .queryParam("iss", issuer)  // RFC 9207
        .build()
        .toUriString();

    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(redirectUrl))
        .build();
}

@GetMapping("/authorize/error")
public ResponseEntity<Void> authorizeError(
        @RequestParam("redirect_uri") String redirectUri,
        @RequestParam("state") String state,
        @RequestParam("error") String error,
        @RequestParam(value = "error_description", required = false) String errorDescription) {

    // エラーレスポンスにも iss を含める
    String issuer = "https://auth.example.com";
    String redirectUrl = UriComponentsBuilder.fromUriString(redirectUri)
        .queryParam("error", error)
        .queryParam("error_description", errorDescription)
        .queryParam("state", state)
        .queryParam("iss", issuer)  // RFC 9207
        .build()
        .toUriString();

    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(redirectUrl))
        .build();
}
```

### 暗黙的フローとの関係

```
暗黙的フロー（Implicit Flow）:
  response_type=token の場合も iss を含める

  redirect_uri#
    access_token=...
    &token_type=Bearer
    &state=xyz
    &iss=https://auth.example.com

ただし、暗黙的フローは非推奨（RFC 9700 BCP）
```

### OIDC との関係

OpenID Connect では、ID トークンに `iss` クレームが含まれます。RFC 9207 はこれを補完し、認可レスポンス自体にも `iss` を含めます。

```
OIDC:
  ID トークンの iss クレーム
  → トークンレスポンスで取得（トークンエンドポイント後）

RFC 9207:
  認可レスポンスの iss パラメータ
  → 認可レスポンスで即座に検証可能
  → Mix-Up 攻撃をより早い段階で防止
```

### JARM との関係

JWT Secured Authorization Response Mode（JARM）を使用する場合、JWT 自体に `iss` クレームが含まれます。

```
JARM:
  認可レスポンス自体が JWT
  JWT の iss クレームで発行者を識別

RFC 9207:
  通常のクエリパラメータとして iss を追加
  JARM を使用しない場合の解決策
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 必須検証 | クライアントは iss を必ず検証 |
| 厳格な一致 | 文字列の完全一致で比較 |
| state との連携 | state から期待される issuer を取得 |
| エラーレスポンス | エラーにも iss を含める |
| ディスカバリー | メタデータでサポートを確認 |

### 移行戦略

```
既存システムへの導入:

1. 認可サーバー側
   └── 認可レスポンスに iss を追加
   └── メタデータを更新

2. クライアント側
   └── iss の検証ロジックを追加
   └── iss がない場合のフォールバック（移行期間中）

3. 完全移行
   └── iss がないレスポンスを拒否
```

---

## 参考リンク

- [RFC 9207 - OAuth 2.0 Authorization Server Issuer Identification](https://datatracker.ietf.org/doc/html/rfc9207)
- [OAuth 2.0 Mix-Up Mitigation](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-mix-up-mitigation)
- [RFC 8414 - OAuth 2.0 Authorization Server Metadata](https://datatracker.ietf.org/doc/html/rfc8414)
