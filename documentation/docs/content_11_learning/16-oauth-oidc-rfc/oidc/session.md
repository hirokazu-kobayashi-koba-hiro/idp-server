# OpenID Connect Session Management 1.0

OpenID Connect Session Management は、RP が OP のセッション状態を監視し、同期するための仕様です。

---

## 第1部: 概要編

### Session Management とは？

Session Management は、RP（Relying Party）が OP（OpenID Provider）の**セッション状態を監視**し、ログアウトを検出する仕組みです。

```
セッション状態の監視:

  ┌────────────────────────────────────────────────────┐
  │                    Browser                         │
  │  ┌─────────────┐             ┌─────────────────┐  │
  │  │   RP Page   │             │  OP iframe      │  │
  │  │             │  postMessage│  (check_session) │  │
  │  │  ┌───────┐  │ ◄─────────► │                 │  │
  │  │  │ iframe│  │             │  セッション状態   │  │
  │  │  │(hidden)  │             │  の確認          │  │
  │  │  └───────┘  │             │                 │  │
  │  └─────────────┘             └─────────────────┘  │
  └────────────────────────────────────────────────────┘
```

### なぜ Session Management が必要なのか？

| シナリオ | 説明 |
|---------|------|
| OP でログアウト | RP でも自動的にログアウトしたい |
| セッション変更 | 別ユーザーでログインした場合を検出 |
| セッション切れ | OP のセッションが切れたことを検出 |
| シングルログアウト | すべての RP で同時にログアウト |

### 主要コンポーネント

| コンポーネント | 説明 |
|---------------|------|
| check_session_iframe | OP が提供する iframe エンドポイント |
| session_state | セッション状態を表すハッシュ値 |
| RP iframe | RP に埋め込まれた隠し iframe |

---

## 第2部: 詳細編

### session_state パラメータ

認可レスポンスに `session_state` が含まれます。

```
認可レスポンス:
  redirect_uri?
    code=SplxlOBeZQQYbYS6WxSbIA
    &state=af0ifjsldkj
    &session_state=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...

session_state の計算:
  session_state = hash(client_id + origin + opbs + salt) + "." + salt

  client_id: クライアント ID
  origin: RP のオリジン
  opbs: OP Browser State（Cookie など）
  salt: ランダム値
```

### OP の check_session_iframe

OP は `check_session_iframe` エンドポイントを提供します。

```html
<!-- OP の check_session_iframe -->
<!DOCTYPE html>
<html>
<head>
  <title>Check Session</title>
</head>
<body>
<script>
  window.addEventListener('message', function(e) {
    // オリジンの検証
    if (e.origin !== trustedOrigin) return;

    // メッセージのパース
    var parts = e.data.split(' ');
    var clientId = parts[0];
    var sessionState = parts[1];

    // セッション状態の計算
    var opbs = getOPBrowserState(); // Cookie から取得
    var expectedSessionState = computeSessionState(clientId, e.origin, opbs);

    // 比較と応答
    if (sessionState === expectedSessionState) {
      e.source.postMessage('unchanged', e.origin);
    } else {
      e.source.postMessage('changed', e.origin);
    }
  });

  function getOPBrowserState() {
    // OP のセッション Cookie を取得
    return document.cookie.match(/opbs=([^;]+)/)?.[1] || '';
  }

  function computeSessionState(clientId, origin, opbs) {
    var salt = generateSalt();
    var hash = sha256(clientId + ' ' + origin + ' ' + opbs + ' ' + salt);
    return hash + '.' + salt;
  }
</script>
</body>
</html>
```

### RP の実装

```html
<!-- RP のページ -->
<!DOCTYPE html>
<html>
<head>
  <title>RP Application</title>
</head>
<body>
  <!-- OP の check_session_iframe を埋め込み -->
  <iframe id="op-iframe"
          src="https://op.example.com/check_session"
          style="display:none;">
  </iframe>

<script>
  var clientId = 's6BhdRkqt3';
  var sessionState = 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...'; // 認可レスポンスから取得
  var opOrigin = 'https://op.example.com';
  var checkInterval = 5000; // 5秒ごとにチェック
  var timerID;

  function checkSession() {
    var opIframe = document.getElementById('op-iframe').contentWindow;
    var message = clientId + ' ' + sessionState;
    opIframe.postMessage(message, opOrigin);
  }

  window.addEventListener('message', function(e) {
    if (e.origin !== opOrigin) return;

    if (e.data === 'changed') {
      // セッションが変更された
      console.log('Session changed!');
      handleSessionChange();
    } else if (e.data === 'unchanged') {
      // セッションは有効
      console.log('Session unchanged');
    } else if (e.data === 'error') {
      // エラー
      console.log('Session check error');
    }
  });

  function handleSessionChange() {
    // タイマーを停止
    clearInterval(timerID);

    // オプション 1: サイレント再認証
    performSilentAuth();

    // オプション 2: ユーザーをログアウト
    // logout();

    // オプション 3: ユーザーに通知
    // showSessionExpiredDialog();
  }

  function performSilentAuth() {
    // prompt=none で認証を試行
    var authUrl = 'https://op.example.com/authorize?' +
      'response_type=code' +
      '&client_id=' + clientId +
      '&redirect_uri=' + encodeURIComponent(window.location.origin + '/callback') +
      '&scope=openid' +
      '&prompt=none';

    var iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    iframe.src = authUrl;
    document.body.appendChild(iframe);
  }

  // 定期的にセッションをチェック
  timerID = setInterval(checkSession, checkInterval);
</script>
</body>
</html>
```

### ディスカバリーメタデータ

```json
{
  "issuer": "https://op.example.com",
  "check_session_iframe": "https://op.example.com/check_session",
  "end_session_endpoint": "https://op.example.com/logout"
}
```

| メタデータ | 説明 |
|-----------|------|
| `check_session_iframe` | セッションチェック用 iframe の URL |
| `end_session_endpoint` | RP 起点ログアウトのエンドポイント |

### RP 起点ログアウト

ユーザーが RP でログアウトした場合、OP のセッションも終了します。

```
ログアウトリクエスト:
  GET https://op.example.com/logout?
    id_token_hint=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
    &post_logout_redirect_uri=https://rp.example.com/logout/callback
    &state=xyz
```

| パラメータ | 説明 |
|-----------|------|
| `id_token_hint` | ログアウトするセッションの ID トークン |
| `post_logout_redirect_uri` | ログアウト後のリダイレクト先 |
| `state` | CSRF 対策 |

### 実装例

#### JavaScript（RP 側）

```javascript
class SessionManager {
  constructor(config) {
    this.clientId = config.clientId;
    this.opOrigin = config.opOrigin;
    this.checkSessionIframe = config.checkSessionIframe;
    this.sessionState = null;
    this.checkInterval = config.checkInterval || 5000;
    this.timerID = null;
    this.iframe = null;

    this.onSessionChanged = config.onSessionChanged || this.defaultSessionChangeHandler;
  }

  // セッション監視を開始
  startMonitoring(sessionState) {
    this.sessionState = sessionState;

    // iframe を作成
    this.iframe = document.createElement('iframe');
    this.iframe.id = 'op-check-session-iframe';
    this.iframe.src = this.checkSessionIframe;
    this.iframe.style.display = 'none';
    document.body.appendChild(this.iframe);

    // メッセージリスナーを設定
    window.addEventListener('message', this.handleMessage.bind(this));

    // 定期チェックを開始
    this.timerID = setInterval(() => this.checkSession(), this.checkInterval);
  }

  // セッション監視を停止
  stopMonitoring() {
    if (this.timerID) {
      clearInterval(this.timerID);
      this.timerID = null;
    }

    if (this.iframe) {
      document.body.removeChild(this.iframe);
      this.iframe = null;
    }

    window.removeEventListener('message', this.handleMessage.bind(this));
  }

  // セッションをチェック
  checkSession() {
    if (!this.iframe || !this.sessionState) return;

    const message = `${this.clientId} ${this.sessionState}`;
    this.iframe.contentWindow.postMessage(message, this.opOrigin);
  }

  // メッセージを処理
  handleMessage(event) {
    if (event.origin !== this.opOrigin) return;

    switch (event.data) {
      case 'unchanged':
        console.log('Session is still valid');
        break;
      case 'changed':
        console.log('Session has changed');
        this.onSessionChanged();
        break;
      case 'error':
        console.error('Session check error');
        break;
    }
  }

  // デフォルトのセッション変更ハンドラー
  defaultSessionChangeHandler() {
    // サイレント再認証を試行
    this.performSilentAuth()
      .then(result => {
        if (result.success) {
          this.sessionState = result.sessionState;
        } else {
          // ログアウト
          this.logout();
        }
      });
  }

  // サイレント再認証
  async performSilentAuth() {
    return new Promise((resolve) => {
      const iframe = document.createElement('iframe');
      iframe.style.display = 'none';

      const state = generateRandomString();
      const authUrl = new URL(this.authorizationEndpoint);
      authUrl.searchParams.set('response_type', 'code');
      authUrl.searchParams.set('client_id', this.clientId);
      authUrl.searchParams.set('redirect_uri', this.silentRedirectUri);
      authUrl.searchParams.set('scope', 'openid');
      authUrl.searchParams.set('prompt', 'none');
      authUrl.searchParams.set('state', state);

      // タイムアウト
      const timeout = setTimeout(() => {
        document.body.removeChild(iframe);
        resolve({ success: false, error: 'timeout' });
      }, 10000);

      // コールバックを待つ
      window.addEventListener('message', function handler(event) {
        if (event.data.type === 'silent_auth_result') {
          clearTimeout(timeout);
          document.body.removeChild(iframe);
          window.removeEventListener('message', handler);
          resolve(event.data);
        }
      });

      iframe.src = authUrl.toString();
      document.body.appendChild(iframe);
    });
  }

  // ログアウト
  logout() {
    this.stopMonitoring();

    const logoutUrl = new URL(this.endSessionEndpoint);
    logoutUrl.searchParams.set('id_token_hint', this.idToken);
    logoutUrl.searchParams.set('post_logout_redirect_uri', this.postLogoutRedirectUri);
    logoutUrl.searchParams.set('state', generateRandomString());

    window.location.href = logoutUrl.toString();
  }
}

// 使用例
const sessionManager = new SessionManager({
  clientId: 's6BhdRkqt3',
  opOrigin: 'https://op.example.com',
  checkSessionIframe: 'https://op.example.com/check_session',
  checkInterval: 5000,
  onSessionChanged: () => {
    // カスタムハンドラー
    showSessionExpiredDialog();
  }
});

// 認可レスポンス後にセッション監視を開始
sessionManager.startMonitoring(sessionState);
```

#### Java（OP 側 - check_session_iframe）

```java
@Controller
public class CheckSessionController {

    @GetMapping("/check_session")
    public String checkSession() {
        return "check_session"; // check_session.html を返す
    }
}
```

```html
<!-- check_session.html -->
<!DOCTYPE html>
<html>
<head>
  <title>Check Session</title>
</head>
<body>
<script>
  (function() {
    var trustedOrigins = [
      'https://rp1.example.com',
      'https://rp2.example.com'
    ];

    window.addEventListener('message', function(e) {
      // オリジンの検証
      if (!trustedOrigins.includes(e.origin)) {
        return;
      }

      try {
        var parts = e.data.split(' ');
        if (parts.length !== 2) {
          e.source.postMessage('error', e.origin);
          return;
        }

        var clientId = parts[0];
        var receivedSessionState = parts[1];

        // OP Browser State を取得
        var opbs = getCookie('opbs') || '';

        // session_state を計算
        var stateParts = receivedSessionState.split('.');
        if (stateParts.length !== 2) {
          e.source.postMessage('error', e.origin);
          return;
        }

        var salt = stateParts[1];
        var expectedHash = sha256(clientId + ' ' + e.origin + ' ' + opbs + ' ' + salt);
        var expectedSessionState = expectedHash + '.' + salt;

        if (receivedSessionState === expectedSessionState) {
          e.source.postMessage('unchanged', e.origin);
        } else {
          e.source.postMessage('changed', e.origin);
        }
      } catch (err) {
        e.source.postMessage('error', e.origin);
      }
    });

    function getCookie(name) {
      var match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
      return match ? match[2] : null;
    }

    async function sha256(message) {
      var msgBuffer = new TextEncoder().encode(message);
      var hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
      var hashArray = Array.from(new Uint8Array(hashBuffer));
      return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    }
  })();
</script>
</body>
</html>
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| オリジン検証 | postMessage のオリジンを必ず検証 |
| HTTPS | すべての通信を暗号化 |
| Cookie 属性 | SameSite, Secure, HttpOnly |
| 信頼された RP のみ | check_session_iframe へのアクセスを制限 |
| チェック間隔 | 負荷を考慮して適切な間隔を設定 |

### Session Management の限界

```
限界:
  - Same-Site Cookie により動作しない場合がある
  - ITP（Intelligent Tracking Prevention）の影響
  - サードパーティ Cookie のブロック

代替手段:
  - Back-Channel Logout（推奨）
  - Front-Channel Logout
  - Refresh Token の短い有効期限
```

---

## 参考リンク

- [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html)
- [OpenID Connect RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html)
- [OpenID Connect Front-Channel Logout 1.0](https://openid.net/specs/openid-connect-frontchannel-1_0.html)
- [OpenID Connect Back-Channel Logout 1.0](https://openid.net/specs/openid-connect-backchannel-1_0.html)
