# RFC 7009 - OAuth 2.0 Token Revocation

RFC 7009 は、クライアントが不要になったアクセストークンやリフレッシュトークンを認可サーバーに対して無効化（失効）するための仕様です。

---

## 第1部: 概要編

### Token Revocation とは？

Token Revocation は、クライアントがトークンを認可サーバーに返却し、無効化する仕組みです。

```
なぜ Revocation が必要か？

1. ログアウト時
   ユーザーがログアウト → トークンを無効化
   → 盗まれても使えなくなる

2. アプリのアンインストール
   アプリ削除 → トークンを無効化
   → 不要なアクセス権を削除

3. セキュリティインシデント
   トークン漏洩の疑い → 即座に無効化
   → 被害を最小化

┌────────────┐                    ┌────────────┐
│   Client   │                    │     AS     │
│            │                    │            │
│  Token を  │   POST /revoke     │  Token を  │
│  無効化    │ ─────────────────► │  無効化    │
│  したい    │   token=xxx        │            │
│            │ ◄───────────────── │            │
│            │   200 OK           │            │
└────────────┘                    └────────────┘
```

### Introspection との違い

| 機能 | Token Revocation (RFC 7009) | Token Introspection (RFC 7662) |
|------|----------------------------|-------------------------------|
| 目的 | トークンを無効化 | トークンの状態を確認 |
| 操作 | 書き込み（状態変更） | 読み取り（状態確認） |
| 主な利用者 | クライアント | リソースサーバー |
| エンドポイント | `/revoke` | `/introspect` |

### ユースケース

| シナリオ | 説明 |
|---------|------|
| ユーザーログアウト | セッション終了時にトークンを無効化 |
| 権限変更 | ユーザーの権限が変わった時に古いトークンを無効化 |
| デバイス紛失 | ユーザーがデバイスを紛失した場合に無効化 |
| アプリ連携解除 | サードパーティアプリの連携を解除 |
| セキュリティ対応 | 不正アクセスの疑いがある場合に即座に無効化 |

---

## 第2部: 詳細編

### Revocation エンドポイント

認可サーバーは `/revoke` エンドポイントを提供します。

#### リクエスト

```http
POST /revoke HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

token=45ghiukldjahdnhzdauz
&token_type_hint=refresh_token
```

#### パラメータ

| パラメータ | 必須 | 説明 |
|-----------|------|------|
| `token` | ✅ | 無効化するトークン |
| `token_type_hint` | △ | トークンの種類のヒント |

#### token_type_hint の値

| 値 | 説明 |
|----|------|
| `access_token` | アクセストークン |
| `refresh_token` | リフレッシュトークン |

**注意**: `token_type_hint` はあくまでヒントです。認可サーバーは実際のトークンの種類を確認する必要があります。

### レスポンス

#### 成功

```http
HTTP/1.1 200 OK
```

Revocation は常に 200 OK を返します。これはセキュリティ上の理由です：
- トークンが存在しない場合でも 200 を返す
- トークンがすでに無効な場合でも 200 を返す
- クライアントに不要な情報を与えない

#### エラー

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "invalid_request",
  "error_description": "Missing required parameter: token"
}
```

| エラー | 説明 |
|--------|------|
| `invalid_request` | リクエストが不正 |
| `invalid_client` | クライアント認証に失敗 |
| `unsupported_token_type` | トークンタイプがサポートされていない |

### クライアント認証

Revocation エンドポイントへのリクエストには、クライアント認証が必要です。

#### client_secret_basic

```http
POST /revoke HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

token=45ghiukldjahdnhzdauz
```

#### client_secret_post

```http
POST /revoke HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

token=45ghiukldjahdnhzdauz
&client_id=s6BhdRkqt3
&client_secret=gX1fBat3bV
```

#### private_key_jwt

```http
POST /revoke HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

token=45ghiukldjahdnhzdauz
&client_id=s6BhdRkqt3
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJSUzI1NiIs...
```

### Revocation の動作

#### リフレッシュトークンの Revocation

リフレッシュトークンを無効化すると、関連するアクセストークンも無効化されます（SHOULD）。

```
リフレッシュトークン無効化の影響:

  ┌─────────────────────────────────────────┐
  │          Refresh Token                  │
  │  (revoke されたトークン)                 │
  └─────────────────────────────────────────┘
                    │
                    │ 関連付け
                    ▼
  ┌─────────────────────────────────────────┐
  │         Access Token(s)                 │
  │  (これらも無効化される - SHOULD)         │
  └─────────────────────────────────────────┘
```

#### アクセストークンの Revocation

アクセストークンのみを無効化した場合、リフレッシュトークンは有効なままです（実装依存）。

```
アクセストークン無効化の影響:

  ┌─────────────────────────────────────────┐
  │          Access Token                   │
  │  (revoke されたトークン)                 │
  └─────────────────────────────────────────┘
                    ↑
                    │ 再発行可能
  ┌─────────────────────────────────────────┐
  │         Refresh Token                   │
  │  (有効なまま - 実装依存)                 │
  └─────────────────────────────────────────┘
```

### ディスカバリーメタデータ

```json
{
  "issuer": "https://auth.example.com",
  "revocation_endpoint": "https://auth.example.com/revoke",
  "revocation_endpoint_auth_methods_supported": [
    "client_secret_basic",
    "client_secret_post",
    "private_key_jwt"
  ],
  "revocation_endpoint_auth_signing_alg_values_supported": [
    "RS256",
    "PS256",
    "ES256"
  ]
}
```

### 実装例

#### Java（認可サーバー側）

```java
@RestController
@RequestMapping("/revoke")
public class RevocationController {

    private final TokenRepository tokenRepository;
    private final ClientAuthenticator clientAuthenticator;

    @PostMapping
    public ResponseEntity<Void> revoke(
            HttpServletRequest request,
            @RequestParam("token") String token,
            @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint) {

        // クライアント認証
        ClientCredentials credentials = clientAuthenticator.authenticate(request);
        if (credentials == null) {
            return ResponseEntity.status(401)
                .header("WWW-Authenticate", "Basic")
                .build();
        }

        // トークンの検索と無効化
        try {
            revokeToken(token, tokenTypeHint, credentials.getClientId());
        } catch (Exception e) {
            // エラーがあっても 200 を返す（セキュリティ上の理由）
            log.warn("Token revocation error", e);
        }

        // 常に 200 OK を返す
        return ResponseEntity.ok().build();
    }

    private void revokeToken(String token, String tokenTypeHint, String clientId) {
        // token_type_hint に基づいて検索を最適化
        if ("refresh_token".equals(tokenTypeHint)) {
            // リフレッシュトークンから検索
            RefreshToken refreshToken = tokenRepository.findRefreshToken(token);
            if (refreshToken != null && refreshToken.getClientId().equals(clientId)) {
                // リフレッシュトークンを無効化
                tokenRepository.revokeRefreshToken(refreshToken.getId());
                // 関連するアクセストークンも無効化
                tokenRepository.revokeAccessTokensByRefreshToken(refreshToken.getId());
                return;
            }
        }

        if ("access_token".equals(tokenTypeHint)) {
            // アクセストークンから検索
            AccessToken accessToken = tokenRepository.findAccessToken(token);
            if (accessToken != null && accessToken.getClientId().equals(clientId)) {
                tokenRepository.revokeAccessToken(accessToken.getId());
                return;
            }
        }

        // ヒントがない場合、両方を検索
        RefreshToken refreshToken = tokenRepository.findRefreshToken(token);
        if (refreshToken != null && refreshToken.getClientId().equals(clientId)) {
            tokenRepository.revokeRefreshToken(refreshToken.getId());
            tokenRepository.revokeAccessTokensByRefreshToken(refreshToken.getId());
            return;
        }

        AccessToken accessToken = tokenRepository.findAccessToken(token);
        if (accessToken != null && accessToken.getClientId().equals(clientId)) {
            tokenRepository.revokeAccessToken(accessToken.getId());
        }
    }
}

@Service
public class TokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public void revokeRefreshToken(String tokenId) {
        jdbcTemplate.update(
            "UPDATE refresh_tokens SET revoked = true, revoked_at = ? WHERE id = ?",
            Instant.now(),
            tokenId
        );
    }

    public void revokeAccessToken(String tokenId) {
        jdbcTemplate.update(
            "UPDATE access_tokens SET revoked = true, revoked_at = ? WHERE id = ?",
            Instant.now(),
            tokenId
        );
    }

    public void revokeAccessTokensByRefreshToken(String refreshTokenId) {
        jdbcTemplate.update(
            "UPDATE access_tokens SET revoked = true, revoked_at = ? WHERE refresh_token_id = ?",
            Instant.now(),
            refreshTokenId
        );
    }
}
```

#### JavaScript（クライアント側）

```javascript
class TokenRevocationClient {
  constructor(config) {
    this.revocationEndpoint = config.revocationEndpoint;
    this.clientId = config.clientId;
    this.clientSecret = config.clientSecret;
  }

  async revokeToken(token, tokenTypeHint = null) {
    const body = new URLSearchParams({
      token: token
    });

    if (tokenTypeHint) {
      body.append('token_type_hint', tokenTypeHint);
    }

    // クライアント認証
    const auth = btoa(`${this.clientId}:${this.clientSecret}`);

    const response = await fetch(this.revocationEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': `Basic ${auth}`
      },
      body: body
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(`Revocation failed: ${error.error_description || error.error}`);
    }

    return true;
  }

  async logout(accessToken, refreshToken) {
    const results = [];

    // リフレッシュトークンを先に無効化（関連するアクセストークンも無効化される）
    if (refreshToken) {
      try {
        await this.revokeToken(refreshToken, 'refresh_token');
        results.push({ token: 'refresh_token', success: true });
      } catch (e) {
        results.push({ token: 'refresh_token', success: false, error: e.message });
      }
    }

    // アクセストークンも明示的に無効化（念のため）
    if (accessToken) {
      try {
        await this.revokeToken(accessToken, 'access_token');
        results.push({ token: 'access_token', success: true });
      } catch (e) {
        results.push({ token: 'access_token', success: false, error: e.message });
      }
    }

    // ローカルストレージからトークンを削除
    this.clearLocalTokens();

    return results;
  }

  clearLocalTokens() {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    sessionStorage.removeItem('access_token');
    sessionStorage.removeItem('refresh_token');
  }
}

// 使用例
const revocationClient = new TokenRevocationClient({
  revocationEndpoint: 'https://auth.example.com/revoke',
  clientId: 's6BhdRkqt3',
  clientSecret: 'gX1fBat3bV'
});

// ログアウト時
async function handleLogout() {
  const accessToken = localStorage.getItem('access_token');
  const refreshToken = localStorage.getItem('refresh_token');

  await revocationClient.logout(accessToken, refreshToken);

  // ログイン画面にリダイレクト
  window.location.href = '/login';
}
```

### JWT アクセストークンの Revocation

JWT（自己完結型トークン）の場合、Revocation は追加の考慮が必要です。

```
JWT の Revocation の課題:

JWT は自己完結型
  → リソースサーバーは AS に問い合わせずに検証可能
  → Revocation を即座に反映できない

解決策:

1. 短い有効期限
   exp を短く設定（5分など）
   → 自然に失効するまで待つ

2. Revocation リスト
   無効化された JWT の jti をリストで管理
   → リソースサーバーがリストを確認

3. Introspection との併用
   リソースサーバーが /introspect で確認
   → リアルタイムに状態を確認

4. Token Binding (DPoP/mTLS)
   トークンを特定のクライアントにバインド
   → 盗まれても使用不可
```

#### Revocation リストの実装例

```java
@Service
public class JWTRevocationService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String REVOCATION_KEY_PREFIX = "revoked:jwt:";

    public void revokeJWT(String jti, Instant expiresAt) {
        String key = REVOCATION_KEY_PREFIX + jti;
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();

        if (ttlSeconds > 0) {
            // JWT の有効期限まで revocation 情報を保持
            redisTemplate.opsForValue().set(key, "revoked", ttlSeconds, TimeUnit.SECONDS);
        }
    }

    public boolean isRevoked(String jti) {
        String key = REVOCATION_KEY_PREFIX + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| クライアント認証 | 必須：不正な Revocation を防止 |
| HTTPS | 必須：トークン漏洩を防止 |
| レスポンス | 常に 200 OK：トークンの存在を推測させない |
| 所有者検証 | クライアントが所有するトークンのみ無効化可能 |
| 関連トークン | リフレッシュトークン無効化時はアクセストークンも無効化 |
| ログ | Revocation イベントを監査ログに記録 |

---

## 参考リンク

- [RFC 7009 - OAuth 2.0 Token Revocation](https://datatracker.ietf.org/doc/html/rfc7009)
- [RFC 7662 - OAuth 2.0 Token Introspection](https://datatracker.ietf.org/doc/html/rfc7662)
- [RFC 6749 - OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
