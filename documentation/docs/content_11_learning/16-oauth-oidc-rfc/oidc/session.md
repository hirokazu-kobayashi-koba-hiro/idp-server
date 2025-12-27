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
