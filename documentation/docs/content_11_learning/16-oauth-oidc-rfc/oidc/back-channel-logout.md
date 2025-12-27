# OpenID Connect Back-Channel Logout 1.0

Back-Channel Logout は、サーバー間の直接通信（バックチャネル）を使用して RP にログアウトを通知する仕様です。

---

## 第1部: 概要編

### Back-Channel Logout とは？

Back-Channel Logout は、OP が各 RP のバックチャネルエンドポイントに**直接 HTTP リクエスト**を送信してログアウトを通知する仕組みです。

```
Back-Channel Logout のフロー:

  ┌────────────┐     ログアウト      ┌────────────┐
  │  ユーザー    │ ───────────────► │     OP     │
  └────────────┘                   │            │
                                   └────────────┘
                                         │
                    サーバー間直接通信     │
                 ┌────────────────────────┼────────────────────────┐
                 │                        │                        │
                 ▼                        ▼                        ▼
          ┌──────────┐             ┌──────────┐             ┌──────────┐
          │   RP 1   │             │   RP 2   │             │   RP 3   │
          │  Server  │             │  Server  │             │  Server  │
          └──────────┘             └──────────┘             └──────────┘
               │                        │                        │
               └── Logout Token ────────┴────── Logout Token ────┘
```

### Front-Channel との違い

| 観点 | Back-Channel | Front-Channel |
|------|--------------|---------------|
| 通信経路 | サーバー間直接 | ブラウザ経由 |
| 信頼性 | 高い | 低い |
| Cookie | 利用不可 | 利用可能 |
| ブラウザ依存 | なし | あり |
| ITP/Cookie制限 | 影響なし | 影響あり |

### Logout Token

OP は Logout Token（JWT）を RP に送信します。

```json
{
  "iss": "https://op.example.com",
  "sub": "user-123",
  "aud": "s6BhdRkqt3",
  "iat": 1704067200,
  "jti": "logout-token-12345",
  "events": {
    "http://schemas.openid.net/event/backchannel-logout": {}
  },
  "sid": "session-abc123"
}
```

---

## 第2部: 詳細編

### Logout Token の構造

| クレーム | 必須 | 説明 |
|---------|------|------|
| `iss` | ✅ | OP の識別子 |
| `sub` | △ | ログアウトするユーザーの識別子 |
| `aud` | ✅ | RP のクライアント ID |
| `iat` | ✅ | 発行時刻 |
| `jti` | ✅ | トークンの一意識別子（リプレイ防止） |
| `events` | ✅ | ログアウトイベント |
| `sid` | △ | セッション ID |

**注意**: `sub` または `sid` のどちらかは必須です。

### events クレーム

```json
{
  "events": {
    "http://schemas.openid.net/event/backchannel-logout": {}
  }
}
```

この形式は固定で、ログアウトイベントを示します。

### クライアント登録

```json
{
  "client_id": "s6BhdRkqt3",
  "redirect_uris": ["https://rp.example.com/callback"],
  "backchannel_logout_uri": "https://rp.example.com/logout/backchannel",
  "backchannel_logout_session_required": true
}
```

| メタデータ | 説明 |
|-----------|------|
| `backchannel_logout_uri` | RP のバックチャネルログアウトエンドポイント |
| `backchannel_logout_session_required` | sid を Logout Token に含めるか |

### OP から RP へのリクエスト

```http
POST /logout/backchannel HTTP/1.1
Host: rp.example.com
Content-Type: application/x-www-form-urlencoded

logout_token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

### RP のレスポンス

| ステータス | 説明 |
|-----------|------|
| 200 | 成功（ボディは空でも可） |
| 400 | Logout Token が無効 |
| 501 | Back-Channel Logout をサポートしていない |

### RP の検証手順

```
Logout Token の検証:

1. JWT の形式検証
   └── 正しい JWT か

2. 署名の検証
   └── OP の公開鍵で署名を検証

3. iss の検証
   └── 期待される OP か

4. aud の検証
   └── 自分（RP）が対象か

5. iat の検証
   └── 発行時刻が妥当か（未来すぎない、古すぎない）

6. events クレームの検証
   └── http://schemas.openid.net/event/backchannel-logout が存在

7. nonce がないことを確認
   └── Logout Token には nonce を含めてはならない

8. jti の検証（リプレイ防止）
   └── 過去に使用されていないか

9. sub または sid の確認
   └── どちらかが存在すること
```

### RP の実装

#### Java

```java
@RestController
public class BackChannelLogoutController {

    private final JWTVerifier jwtVerifier;
    private final SessionService sessionService;
    private final Set<String> usedJtis = ConcurrentHashMap.newKeySet();

    @PostMapping("/logout/backchannel")
    public ResponseEntity<Void> backchannelLogout(
            @RequestParam("logout_token") String logoutToken) {

        try {
            // Logout Token の検証
            JWT jwt = validateLogoutToken(logoutToken);

            // セッションの無効化
            invalidateSession(jwt);

            return ResponseEntity.ok().build();

        } catch (InvalidLogoutTokenException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private JWT validateLogoutToken(String token) throws InvalidLogoutTokenException {
        try {
            // JWT のパース
            SignedJWT jwt = SignedJWT.parse(token);

            // 署名の検証
            if (!jwtVerifier.verify(jwt)) {
                throw new InvalidLogoutTokenException("Invalid signature");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            // iss の検証
            if (!expectedIssuer.equals(claims.getIssuer())) {
                throw new InvalidLogoutTokenException("Invalid issuer");
            }

            // aud の検証
            if (!claims.getAudience().contains(clientId)) {
                throw new InvalidLogoutTokenException("Invalid audience");
            }

            // iat の検証
            Date iat = claims.getIssueTime();
            if (iat == null || iat.after(new Date())) {
                throw new InvalidLogoutTokenException("Invalid iat");
            }
            if (iat.before(new Date(System.currentTimeMillis() - 300000))) {
                throw new InvalidLogoutTokenException("Token too old");
            }

            // events クレームの検証
            Map<String, Object> events = claims.getJSONObjectClaim("events");
            if (events == null ||
                !events.containsKey("http://schemas.openid.net/event/backchannel-logout")) {
                throw new InvalidLogoutTokenException("Invalid events claim");
            }

            // nonce がないことを確認
            if (claims.getClaim("nonce") != null) {
                throw new InvalidLogoutTokenException("nonce must not be present");
            }

            // jti の検証（リプレイ防止）
            String jti = claims.getJWTID();
            if (jti == null || !usedJtis.add(jti)) {
                throw new InvalidLogoutTokenException("Duplicate jti");
            }

            // sub または sid の確認
            String sub = claims.getSubject();
            String sid = claims.getStringClaim("sid");
            if (sub == null && sid == null) {
                throw new InvalidLogoutTokenException("sub or sid required");
            }

            return jwt;

        } catch (ParseException e) {
            throw new InvalidLogoutTokenException("Invalid JWT format");
        }
    }

    private void invalidateSession(JWT jwt) {
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        String sub = claims.getSubject();
        String sid = claims.getStringClaim("sid");

        if (sid != null) {
            // セッション ID で無効化
            sessionService.invalidateByOPSessionId(sid);
        } else if (sub != null) {
            // ユーザーのすべてのセッションを無効化
            sessionService.invalidateBySubject(sub);
        }
    }
}
```

#### Node.js

```javascript
const express = require('express');
const jose = require('jose');

const app = express();
app.use(express.urlencoded({ extended: true }));

const usedJtis = new Set();

app.post('/logout/backchannel', async (req, res) => {
  const logoutToken = req.body.logout_token;

  try {
    // JWKS を取得
    const JWKS = jose.createRemoteJWKSet(new URL('https://op.example.com/.well-known/jwks.json'));

    // Logout Token の検証
    const { payload } = await jose.jwtVerify(logoutToken, JWKS, {
      issuer: 'https://op.example.com',
      audience: clientId
    });

    // events クレームの検証
    if (!payload.events?.['http://schemas.openid.net/event/backchannel-logout']) {
      return res.status(400).send('Invalid events claim');
    }

    // nonce がないことを確認
    if (payload.nonce) {
      return res.status(400).send('nonce must not be present');
    }

    // jti の検証（リプレイ防止）
    if (!payload.jti || usedJtis.has(payload.jti)) {
      return res.status(400).send('Duplicate or missing jti');
    }
    usedJtis.add(payload.jti);

    // sub または sid の確認
    if (!payload.sub && !payload.sid) {
      return res.status(400).send('sub or sid required');
    }

    // セッションの無効化
    if (payload.sid) {
      await sessionService.invalidateByOPSessionId(payload.sid);
    } else {
      await sessionService.invalidateBySubject(payload.sub);
    }

    res.status(200).send();

  } catch (error) {
    console.error('Logout token validation failed:', error);
    res.status(400).send('Invalid logout token');
  }
});
```

### OP の実装

```java
@Service
public class BackChannelLogoutService {

    private final ClientRepository clientRepository;
    private final SessionRepository sessionRepository;
    private final JWTSigner jwtSigner;
    private final RestTemplate restTemplate;

    public void logout(String sessionId) {
        // セッションで認証された RP を取得
        List<AuthenticatedClient> clients = sessionRepository
            .getAuthenticatedClients(sessionId);

        // セッション情報を取得
        SessionInfo session = sessionRepository.findById(sessionId);

        for (AuthenticatedClient client : clients) {
            ClientInfo clientInfo = clientRepository.findByClientId(client.getClientId());

            if (clientInfo.getBackchannelLogoutUri() != null) {
                try {
                    sendLogoutToken(clientInfo, session);
                } catch (Exception e) {
                    // ログを記録して続行
                    log.error("Failed to send logout token to {}: {}",
                        clientInfo.getClientId(), e.getMessage());
                }
            }
        }

        // OP のセッションを終了
        sessionRepository.invalidate(sessionId);
    }

    private void sendLogoutToken(ClientInfo client, SessionInfo session) {
        // Logout Token を生成
        String logoutToken = createLogoutToken(client, session);

        // RP に送信
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("logout_token", logoutToken);

        HttpEntity<MultiValueMap<String, String>> request =
            new HttpEntity<>(body, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(
            client.getBackchannelLogoutUri(),
            request,
            Void.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new LogoutFailedException(
                "Logout failed for " + client.getClientId()
            );
        }
    }

    private String createLogoutToken(ClientInfo client, SessionInfo session) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(client.getClientId())
            .issueTime(new Date())
            .jwtID(UUID.randomUUID().toString())
            .claim("events", Map.of(
                "http://schemas.openid.net/event/backchannel-logout", Map.of()
            ));

        // sub と sid を設定
        if (session.getSubject() != null) {
            builder.subject(session.getSubject());
        }

        if (client.isBackchannelLogoutSessionRequired() && session.getId() != null) {
            builder.claim("sid", session.getId());
        }

        JWTClaimsSet claims = builder.build();
        return jwtSigner.sign(claims);
    }
}
```

### ディスカバリーメタデータ

```json
{
  "issuer": "https://op.example.com",
  "backchannel_logout_supported": true,
  "backchannel_logout_session_supported": true
}
```

| メタデータ | 説明 |
|-----------|------|
| `backchannel_logout_supported` | Back-Channel Logout をサポート |
| `backchannel_logout_session_supported` | sid をサポート |

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| HTTPS | backchannel_logout_uri は HTTPS 必須 |
| 署名検証 | Logout Token の署名を必ず検証 |
| jti キャッシュ | リプレイ攻撃防止のため jti をキャッシュ |
| iat 検証 | 古すぎる・未来すぎるトークンを拒否 |
| タイムアウト | OP 側でタイムアウトを設定 |
| 非同期処理 | 多数の RP がある場合は非同期で処理 |

### エラーハンドリング

```
OP 側のエラーハンドリング:

1. RP が応答しない
   └── タイムアウト後、ログを記録して続行

2. RP が 4xx を返す
   └── ログを記録して続行

3. RP が 5xx を返す
   └── リトライを検討

4. ネットワークエラー
   └── リトライを検討

RP 側のエラーハンドリング:

1. 無効な Logout Token
   └── 400 を返す

2. 処理中にエラー
   └── 500 を返す（OP がリトライ可能）
```

### jti キャッシュの管理

```java
@Component
public class JtiCache {
    // TTL 付きキャッシュ（5分など）
    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .maximumSize(10000)
        .build();

    public boolean isUsed(String jti) {
        return cache.getIfPresent(jti) != null;
    }

    public void markAsUsed(String jti) {
        cache.put(jti, true);
    }
}
```

---

## 参考リンク

- [OpenID Connect Back-Channel Logout 1.0](https://openid.net/specs/openid-connect-backchannel-1_0.html)
- [OpenID Connect Front-Channel Logout 1.0](https://openid.net/specs/openid-connect-frontchannel-1_0.html)
- [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html)
