# OpenID Connect Front-Channel Logout 1.0

Front-Channel Logout は、ブラウザのリダイレクトを利用して複数の RP を同時にログアウトするための仕様です。

---

## 第1部: 概要編

### Front-Channel Logout とは？

Front-Channel Logout は、ユーザーのブラウザ（フロントチャネル）を通じて、OP から各 RP にログアウトを通知する仕組みです。

```
Front-Channel Logout のフロー:

  ┌────────────┐     ログアウト      ┌────────────┐
  │  ユーザー    │ ───────────────► │     OP     │
  │  ブラウザ    │                   │            │
  │            │ ◄──────────────── │            │
  │            │  ログアウトページ    │            │
  └────────────┘                   └────────────┘
       │
       │  iframe で各 RP のログアウト URL を読み込み
       ▼
  ┌─────────────────────────────────────────────┐
  │              OP のログアウトページ              │
  │  ┌─────────────────────────────────────────┐ │
  │  │  <iframe src="https://rp1/logout">     │ │
  │  │  <iframe src="https://rp2/logout">     │ │
  │  │  <iframe src="https://rp3/logout">     │ │
  │  └─────────────────────────────────────────┘ │
  └─────────────────────────────────────────────┘
```

### Back-Channel との違い

| 観点 | Front-Channel | Back-Channel |
|------|---------------|--------------|
| 通信経路 | ブラウザ経由 | サーバー間直接 |
| 信頼性 | 低い（ブラウザ依存） | 高い |
| Cookie | 利用可能 | 利用不可 |
| 実装 | シンプル | 複雑 |
| スケール | RP 数に制限あり | 制限なし |

---

## 第2部: 詳細編

### クライアント登録

RP は登録時に Front-Channel Logout URI を指定します。

```json
{
  "client_id": "s6BhdRkqt3",
  "redirect_uris": ["https://rp.example.com/callback"],
  "frontchannel_logout_uri": "https://rp.example.com/logout/frontchannel",
  "frontchannel_logout_session_required": true
}
```

| メタデータ | 説明 |
|-----------|------|
| `frontchannel_logout_uri` | RP のログアウトエンドポイント |
| `frontchannel_logout_session_required` | sid を含めるか |

### OP のログアウトページ

ユーザーがログアウトすると、OP は各 RP の frontchannel_logout_uri を iframe で読み込むページを表示します。

```html
<!-- OP のログアウトページ -->
<!DOCTYPE html>
<html>
<head>
  <title>Logging Out...</title>
  <style>
    .logout-container { text-align: center; padding: 50px; }
    .logout-status { margin: 20px 0; }
    iframe { display: none; }
  </style>
</head>
<body>
  <div class="logout-container">
    <h1>ログアウト中...</h1>
    <div class="logout-status" id="status">
      各アプリケーションからログアウトしています
    </div>

    <!-- 各 RP の logout iframe -->
    <iframe id="rp1" src="https://rp1.example.com/logout/frontchannel?iss=https://op.example.com&sid=abc123"></iframe>
    <iframe id="rp2" src="https://rp2.example.com/logout/frontchannel?iss=https://op.example.com&sid=abc123"></iframe>
    <iframe id="rp3" src="https://rp3.example.com/logout/frontchannel?iss=https://op.example.com&sid=abc123"></iframe>
  </div>

  <script>
    var iframes = document.querySelectorAll('iframe');
    var loaded = 0;
    var total = iframes.length;

    iframes.forEach(function(iframe) {
      iframe.onload = function() {
        loaded++;
        if (loaded === total) {
          // すべての RP がログアウト完了
          document.getElementById('status').textContent = 'ログアウト完了';

          // post_logout_redirect_uri にリダイレクト
          setTimeout(function() {
            window.location.href = 'https://rp1.example.com/logout/callback';
          }, 1000);
        }
      };

      iframe.onerror = function() {
        loaded++;
        console.error('Logout failed for:', iframe.src);
      };
    });

    // タイムアウト
    setTimeout(function() {
      if (loaded < total) {
        document.getElementById('status').textContent =
          '一部のアプリケーションからのログアウトに失敗しました';
      }
    }, 5000);
  </script>
</body>
</html>
```

### RP のログアウトエンドポイント

```
GET https://rp.example.com/logout/frontchannel?
  iss=https://op.example.com
  &sid=abc123
```

| パラメータ | 説明 |
|-----------|------|
| `iss` | OP の識別子 |
| `sid` | セッション ID（オプション） |

### RP の実装

```java
@Controller
public class FrontChannelLogoutController {

    @GetMapping("/logout/frontchannel")
    public ResponseEntity<String> frontchannelLogout(
            @RequestParam("iss") String issuer,
            @RequestParam(value = "sid", required = false) String sessionId) {

        // issuer の検証
        if (!trustedIssuers.contains(issuer)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Untrusted issuer");
        }

        // セッションのログアウト
        if (sessionId != null) {
            // 特定のセッションをログアウト
            sessionService.invalidateByOPSessionId(sessionId);
        } else {
            // 現在のセッションをログアウト（Cookie ベース）
            sessionService.invalidateCurrentSession();
        }

        // 空の HTML を返す（iframe 内で表示）
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .body("<html><body>Logged out</body></html>");
    }
}
```

### JavaScript（RP 側）

```javascript
// RP のログアウトエンドポイント（SPA の場合）
class FrontChannelLogoutHandler {
  constructor(config) {
    this.trustedIssuers = config.trustedIssuers;
  }

  // ログアウトページをレンダリング
  handleLogout() {
    const params = new URLSearchParams(window.location.search);
    const issuer = params.get('iss');
    const sessionId = params.get('sid');

    // issuer の検証
    if (!this.trustedIssuers.includes(issuer)) {
      console.error('Untrusted issuer:', issuer);
      return;
    }

    // ローカルセッションをクリア
    this.clearLocalSession(sessionId);

    // 成功を示す HTML を表示
    document.body.innerHTML = '<p>Logged out</p>';
  }

  clearLocalSession(sessionId) {
    // トークンをクリア
    localStorage.removeItem('access_token');
    localStorage.removeItem('id_token');
    localStorage.removeItem('refresh_token');

    // セッション Cookie をクリア
    document.cookie = 'session=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';

    // セッション ID による特定のセッションクリア
    if (sessionId) {
      const storedSessionId = sessionStorage.getItem('op_session_id');
      if (storedSessionId === sessionId) {
        sessionStorage.clear();
      }
    } else {
      sessionStorage.clear();
    }
  }
}

// ログアウトページで実行
if (window.location.pathname === '/logout/frontchannel') {
  const handler = new FrontChannelLogoutHandler({
    trustedIssuers: ['https://op.example.com']
  });
  handler.handleLogout();
}
```

### OP の実装

```java
@Controller
public class OPLogoutController {

    private final ClientRepository clientRepository;
    private final SessionRepository sessionRepository;

    @GetMapping("/logout")
    public String logout(
            @RequestParam(value = "id_token_hint", required = false) String idTokenHint,
            @RequestParam(value = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
            @RequestParam(value = "state", required = false) String state,
            Model model) {

        // ID トークンからセッション情報を取得
        String sessionId = null;
        String clientId = null;
        if (idTokenHint != null) {
            JWT idToken = JWT.parse(idTokenHint);
            sessionId = idToken.getClaim("sid");
            clientId = idToken.getAudience().get(0);
        }

        // 現在のセッションで認証された RP を取得
        List<AuthenticatedClient> clients = sessionRepository
            .getAuthenticatedClients(sessionId);

        // 各 RP の frontchannel_logout_uri を収集
        List<String> logoutUris = new ArrayList<>();
        for (AuthenticatedClient client : clients) {
            ClientInfo clientInfo = clientRepository.findByClientId(client.getClientId());
            if (clientInfo.getFrontchannelLogoutUri() != null) {
                String uri = buildLogoutUri(clientInfo, sessionId);
                logoutUris.add(uri);
            }
        }

        // OP のセッションを終了
        sessionRepository.invalidate(sessionId);

        // モデルに設定
        model.addAttribute("logoutUris", logoutUris);
        model.addAttribute("postLogoutRedirectUri", postLogoutRedirectUri);
        model.addAttribute("state", state);

        return "logout"; // logout.html
    }

    private String buildLogoutUri(ClientInfo client, String sessionId) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(client.getFrontchannelLogoutUri())
            .queryParam("iss", issuer);

        if (client.isFrontchannelLogoutSessionRequired() && sessionId != null) {
            builder.queryParam("sid", sessionId);
        }

        return builder.build().toUriString();
    }
}
```

### ディスカバリーメタデータ

```json
{
  "issuer": "https://op.example.com",
  "frontchannel_logout_supported": true,
  "frontchannel_logout_session_supported": true
}
```

| メタデータ | 説明 |
|-----------|------|
| `frontchannel_logout_supported` | Front-Channel Logout をサポート |
| `frontchannel_logout_session_supported` | sid パラメータをサポート |

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| iss の検証 | 信頼された OP からのリクエストのみ処理 |
| HTTPS | ログアウト URI は HTTPS 必須 |
| Cache-Control | キャッシュを無効化 |
| Cookie 属性 | SameSite=None, Secure が必要 |
| タイムアウト | iframe の読み込みにタイムアウトを設定 |

### 制限事項

```
Front-Channel Logout の制限:

1. ブラウザの制限
   - サードパーティ Cookie のブロック
   - ITP（Safari）
   - Enhanced Tracking Prevention（Firefox）

2. iframe の制限
   - X-Frame-Options
   - Content-Security-Policy

3. 信頼性
   - ブラウザを閉じると実行されない
   - ネットワーク遅延の影響

推奨:
  - Back-Channel Logout と併用
  - 重要なセッションは Back-Channel を使用
```

---

## 参考リンク

- [OpenID Connect Front-Channel Logout 1.0](https://openid.net/specs/openid-connect-frontchannel-1_0.html)
- [OpenID Connect Back-Channel Logout 1.0](https://openid.net/specs/openid-connect-backchannel-1_0.html)
- [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html)
