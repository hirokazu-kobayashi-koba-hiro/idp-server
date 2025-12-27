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

---

## 参考リンク

- [OpenID Connect Back-Channel Logout 1.0](https://openid.net/specs/openid-connect-backchannel-1_0.html)
- [OpenID Connect Front-Channel Logout 1.0](https://openid.net/specs/openid-connect-frontchannel-1_0.html)
- [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html)
