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

### JavaScript（RP 側）

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
