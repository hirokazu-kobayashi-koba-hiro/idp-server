# RFC 8693: OAuth 2.0 トークン交換

RFC 8693 は、あるトークンを別のトークンに交換するためのプロトコルを定義した仕様です。委任（Delegation）や偽装（Impersonation）のシナリオで使用されます。

---

## 第1部: 概要編

### トークン交換とは？

トークン交換は、既存のトークンを使用して**別のトークン**を取得する仕組みです。

```
基本的なフロー:

  ┌──────────┐                              ┌──────────────┐
  │ クライアント │ ── subject_token ─────────► │  認可サーバー  │
  │           │    (交換したいトークン)        │              │
  │           │ ◄──────────────────────── │              │
  └──────────┘    新しいトークン              └──────────────┘
```

### なぜトークン交換が必要なのか？

| シナリオ | 説明 |
|---------|------|
| マイクロサービス | サービス間でトークンを伝播・変換 |
| 委任（Delegation） | 「〇〇の代理で」アクセスする |
| 偽装（Impersonation） | 「〇〇として」アクセスする |
| トークンのダウングレード | スコープを絞ったトークンを取得 |
| クロスドメイン | 異なるドメイン間でトークンを交換 |

### 委任 vs 偽装

```
委任（Delegation）:
  「User A の代理で Service B がアクセス」

  ┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐
  │ User A │ ─► │Service │ ─► │Service │ ─► │Resource│
  │        │    │   B    │    │   C    │    │        │
  └────────┘    └────────┘    └────────┘    └────────┘
                    │
                    └── Token には User A と Service B の両方が記載

  act (actor): Service B
  sub (subject): User A

偽装（Impersonation）:
  「Service B が User A になりすまして」アクセス

  ┌────────┐    ┌────────┐    ┌────────┐
  │Service │ ─► │Service │ ─► │Resource│
  │   B    │    │   C    │    │        │
  └────────┘    └────────┘    └────────┘
      │
      └── Token には User A のみ記載（Service B は見えない）

  sub: User A
  （act なし）
```

---

## 第2部: 詳細編

### トークン交換リクエスト

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
&subject_token_type=urn:ietf:params:oauth:token-type:access_token
&requested_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=https://api.example.com
&scope=read write
```

### リクエストパラメータ

| パラメータ | 必須 | 説明 |
|-----------|------|------|
| `grant_type` | ✅ | `urn:ietf:params:oauth:grant-type:token-exchange` |
| `subject_token` | ✅ | 交換したいトークン（主体を表す） |
| `subject_token_type` | ✅ | subject_token のタイプ |
| `actor_token` | △ | アクター（代理者）のトークン |
| `actor_token_type` | △ | actor_token のタイプ（actor_token がある場合必須） |
| `requested_token_type` | △ | 要求するトークンタイプ |
| `audience` | △ | トークンの対象者 |
| `scope` | △ | 要求するスコープ |
| `resource` | △ | リソースサーバー（RFC 8707） |

### トークンタイプ URI

| URI | 説明 |
|-----|------|
| `urn:ietf:params:oauth:token-type:access_token` | アクセストークン |
| `urn:ietf:params:oauth:token-type:refresh_token` | リフレッシュトークン |
| `urn:ietf:params:oauth:token-type:id_token` | ID トークン |
| `urn:ietf:params:oauth:token-type:saml1` | SAML 1.1 アサーション |
| `urn:ietf:params:oauth:token-type:saml2` | SAML 2.0 アサーション |
| `urn:ietf:params:oauth:token-type:jwt` | JWT |

### レスポンス

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read write"
}
```

| フィールド | 説明 |
|-----------|------|
| `access_token` | 発行されたトークン |
| `issued_token_type` | 発行されたトークンのタイプ |
| `token_type` | トークンの使用方法（通常 `Bearer`） |
| `expires_in` | 有効期限（秒） |
| `scope` | 付与されたスコープ |
| `refresh_token` | リフレッシュトークン（オプション） |

### ユースケース別の例

#### 1. マイクロサービス間のトークン伝播

```
シナリオ:
  User → API Gateway → Service A → Service B → Database

API Gateway が受け取ったトークンを Service A 用に交換:

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={user_access_token}
&subject_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=service-a
&scope=service-a:read
```

#### 2. 委任トークンの取得

```
シナリオ:
  Service B が User A の代理で Service C にアクセス

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={user_a_access_token}
&subject_token_type=urn:ietf:params:oauth:token-type:access_token
&actor_token={service_b_access_token}
&actor_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=service-c

発行されるトークン（JWT）:
{
  "sub": "user-a",
  "aud": "service-c",
  "act": {
    "sub": "service-b"
  }
}
```

#### 3. 偽装トークンの取得

```
シナリオ:
  Admin が User A として操作

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={user_a_id_token}
&subject_token_type=urn:ietf:params:oauth:token-type:id_token
&requested_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=target-resource

発行されるトークン:
{
  "sub": "user-a",
  "aud": "target-resource"
  // act クレームなし = 偽装
}
```

#### 4. スコープのダウングレード

```
シナリオ:
  広いスコープのトークンから、限定されたスコープのトークンを取得

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={broad_scope_token}
&subject_token_type=urn:ietf:params:oauth:token-type:access_token
&scope=read  // 元のトークンより狭いスコープ
```

#### 5. SAML → OAuth 変換

```
シナリオ:
  SAML アサーションを OAuth アクセストークンに変換

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={base64_encoded_saml_assertion}
&subject_token_type=urn:ietf:params:oauth:token-type:saml2
&requested_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=https://api.example.com
```

### 委任チェーン

複数の委任が連鎖する場合、`act` クレームがネストします。

```json
{
  "sub": "user-a",
  "act": {
    "sub": "service-b",
    "act": {
      "sub": "service-c"
    }
  }
}
```

```
意味:
  Service C が Service B を通じて User A の代理でアクセス

  User A → Service B → Service C → Resource
            (delegate)   (delegate)
```

### 認可サーバーの検証

```
トークン交換リクエストの検証フロー:

1. クライアント認証
   └── リクエスト元のクライアントを認証

2. subject_token の検証
   ├── 署名の検証
   ├── 有効期限の確認
   ├── 発行者の確認
   └── 主体の識別

3. actor_token の検証（あれば）
   ├── 署名の検証
   ├── 有効期限の確認
   └── アクターの識別

4. 交換の認可
   ├── クライアントは交換を許可されているか
   ├── 要求された audience は許可されているか
   ├── 要求された scope は許可されているか
   └── 委任/偽装のポリシーに適合するか

5. 新しいトークンの発行
   ├── sub: subject_token の主体
   ├── act: actor_token の主体（委任の場合）
   ├── aud: 要求された audience
   └── scope: 付与されたスコープ
```

### 実装例

#### Java（認可サーバー）

```java
@RestController
public class TokenExchangeController {

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> exchange(
            @RequestParam("grant_type") String grantType,
            @RequestParam("subject_token") String subjectToken,
            @RequestParam("subject_token_type") String subjectTokenType,
            @RequestParam(value = "actor_token", required = false) String actorToken,
            @RequestParam(value = "actor_token_type", required = false) String actorTokenType,
            @RequestParam(value = "requested_token_type", required = false) String requestedTokenType,
            @RequestParam(value = "audience", required = false) String audience,
            @RequestParam(value = "scope", required = false) String scope) {

        // grant_type の確認
        if (!"urn:ietf:params:oauth:grant-type:token-exchange".equals(grantType)) {
            throw new UnsupportedGrantTypeException();
        }

        // subject_token の検証
        TokenInfo subjectInfo = validateToken(subjectToken, subjectTokenType);

        // actor_token の検証（あれば）
        TokenInfo actorInfo = null;
        if (actorToken != null) {
            actorInfo = validateToken(actorToken, actorTokenType);
        }

        // 交換ポリシーの確認
        validateExchangePolicy(subjectInfo, actorInfo, audience, scope);

        // 新しいトークンの生成
        String newToken = generateToken(
            subjectInfo.getSubject(),
            actorInfo != null ? actorInfo.getSubject() : null,
            audience,
            scope
        );

        return ResponseEntity.ok(TokenResponse.builder()
            .accessToken(newToken)
            .issuedTokenType("urn:ietf:params:oauth:token-type:access_token")
            .tokenType("Bearer")
            .expiresIn(3600)
            .scope(scope)
            .build());
    }

    private String generateToken(String subject, String actor, String audience, String scope) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
            .subject(subject)
            .audience(audience)
            .issueTime(new Date())
            .expirationTime(new Date(System.currentTimeMillis() + 3600000));

        if (actor != null) {
            // 委任トークン
            Map<String, Object> actClaim = new HashMap<>();
            actClaim.put("sub", actor);
            builder.claim("act", actClaim);
        }

        if (scope != null) {
            builder.claim("scope", scope);
        }

        // 署名してトークンを返す
        return signToken(builder.build());
    }
}
```

#### JavaScript（クライアント側）

```javascript
async function exchangeToken(subjectToken, options = {}) {
  const params = new URLSearchParams({
    grant_type: 'urn:ietf:params:oauth:grant-type:token-exchange',
    subject_token: subjectToken,
    subject_token_type: options.subjectTokenType || 'urn:ietf:params:oauth:token-type:access_token'
  });

  if (options.actorToken) {
    params.append('actor_token', options.actorToken);
    params.append('actor_token_type', options.actorTokenType || 'urn:ietf:params:oauth:token-type:access_token');
  }

  if (options.audience) {
    params.append('audience', options.audience);
  }

  if (options.scope) {
    params.append('scope', options.scope);
  }

  if (options.requestedTokenType) {
    params.append('requested_token_type', options.requestedTokenType);
  }

  const response = await fetch('https://auth.example.com/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'Authorization': `Basic ${btoa(`${clientId}:${clientSecret}`)}`
    },
    body: params
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(`Token exchange failed: ${error.error_description}`);
  }

  return response.json();
}

// 使用例: マイクロサービス間のトークン伝播
const internalToken = await exchangeToken(userAccessToken, {
  audience: 'internal-service',
  scope: 'internal:read'
});

// 使用例: 委任トークン
const delegatedToken = await exchangeToken(userToken, {
  actorToken: serviceToken,
  audience: 'downstream-service'
});
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| クライアント認証 | 交換を許可されたクライアントのみ認証 |
| 交換ポリシー | どのトークンをどのように交換できるか制限 |
| 偽装の制限 | 偽装は特権クライアントのみに許可 |
| 委任チェーンの深さ | ネストの深さを制限 |
| スコープの制限 | 元のトークンより広いスコープを許可しない |
| 監査ログ | すべての交換を記録 |
| audience の検証 | 許可された audience のみ受け入れ |

### エラーコード

| エラー | 説明 |
|--------|------|
| `invalid_request` | リクエストが不正 |
| `invalid_client` | クライアント認証失敗 |
| `invalid_grant` | subject_token または actor_token が無効 |
| `unauthorized_client` | 交換が許可されていない |
| `unsupported_token_type` | サポートされていないトークンタイプ |
| `invalid_target` | audience または resource が無効 |

---

## 参考リンク

- [RFC 8693 - OAuth 2.0 Token Exchange](https://datatracker.ietf.org/doc/html/rfc8693)
- [RFC 8707 - Resource Indicators for OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc8707)
- [RFC 7519 - JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
