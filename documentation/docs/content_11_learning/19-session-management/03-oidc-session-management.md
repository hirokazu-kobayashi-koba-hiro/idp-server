# OIDC Session Management

## このドキュメントの目的

**OIDC Session Management**の仕様を理解し、IdPがなぜ特殊なセッション管理を必要とするかを学ぶことが目標です。

### 所要時間
約40分

### 学べること
- OIDC Session Managementの目的
- OPSession と ClientSession の関係
- 複数のログアウト方式
- セッション検索の要件

---

## なぜ通常のセッション管理では不十分か

### 一般的なWebアプリのセッション

```
【通常のセッション管理】

Browser ──── SESSION=abc123 ────→ Webアプリ
                                   │
                                   ▼
                              セッションデータ
                              ├── user: alice
                              ├── cart: [...]
                              └── preferences

・1ブラウザ = 1セッション
・セッションIDでのみアクセス
・シンプルで十分
```

### IdP（Identity Provider）のセッション

```
【IdPのセッション管理】

                    ┌───────────────────────────────┐
                    │            IdP                 │
                    │                               │
Browser ────────────│──→ OPSession                  │
                    │       │ sub: alice            │
                    │       │ authTime: 10:00       │
                    │       │                       │
                    │       ├── ClientSession(App A)│
                    │       │    sid: sid-001       │
                    │       │                       │
                    │       ├── ClientSession(App B)│
                    │       │    sid: sid-002       │
                    │       │                       │
                    │       └── ClientSession(App C)│
                    │            sid: sid-003       │
                    │                               │
                    └───────────────────────────────┘

・1ブラウザ = 1 OPSession
・1 OPSession = N ClientSession
・階層的な構造
```

**違い**:
- 複数のアプリケーション（RP）のセッションを管理
- ユーザーがどのアプリにログインしているか追跡
- ログアウト時に全アプリに通知が必要

---

## OIDC Session Managementとは

### 仕様群

OIDC Session Managementは複数の仕様で構成されています。

| 仕様 | 目的 |
|:----|:----|
| **Session Management** | iframeでセッション状態を監視 |
| **RP-Initiated Logout** | RPからのログアウト開始 |
| **Front-Channel Logout** | ブラウザ経由でログアウト通知 |
| **Back-Channel Logout** | サーバー間でログアウト通知 |

### 基本概念

```
【OPSession と ClientSession】

┌─────────────────────────────────────────────────────────────────┐
│                         OPSession                                │
│                                                                 │
│  ブラウザとIdP（OP）間のセッション                               │
│                                                                 │
│  属性:                                                          │
│  ├── id: セッションID                                           │
│  ├── sub: ユーザー識別子（誰がログインしているか）               │
│  ├── authTime: 認証時刻（いつログインしたか）                    │
│  ├── acr: 認証強度（どの強度で認証したか）                       │
│  └── amr: 認証方式（どの方法で認証したか）                       │
│                                                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │ClientSession│  │ClientSession│  │ClientSession│             │
│  │             │  │             │  │             │             │
│  │ App A       │  │ App B       │  │ App C       │             │
│  │ sid: xxx    │  │ sid: yyy    │  │ sid: zzz    │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
│                                                                 │
│  ClientSession: OPSessionとRP間のセッション                     │
│  ├── sid: ID Tokenに含まれるセッションID                        │
│  ├── clientId: どのアプリか                                     │
│  └── scopes: どの権限が許可されたか                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## シングルサインオン（SSO）

### SSOの仕組み

```
【SSOの流れ】

1. ユーザーがApp Aにアクセス
   ┌────────────────────────────────────────────────────────────┐
   │ Browser → App A: アクセス                                  │
   │ App A → Browser: IdPへリダイレクト                         │
   │ Browser → IdP: 認可リクエスト                              │
   │                                                            │
   │ IdP: ログインフォーム表示                                   │
   │ User: ID/パスワード入力                                    │
   │                                                            │
   │ IdP:                                                       │
   │   ① OPSession作成                                          │
   │   ② ClientSession(App A)作成                               │
   │   ③ IDP_IDENTITYクッキー発行                               │
   │                                                            │
   │ Browser ← IdP: 認可コード + Cookie                         │
   │ Browser → App A: 認可コード                                │
   │ App A: ID Token取得（sid: xxx）                            │
   └────────────────────────────────────────────────────────────┘

2. ユーザーがApp Bにアクセス
   ┌────────────────────────────────────────────────────────────┐
   │ Browser → App B: アクセス                                  │
   │ App B → Browser: IdPへリダイレクト                         │
   │ Browser → IdP: 認可リクエスト + IDP_IDENTITY Cookie        │
   │                                                            │
   │ IdP:                                                       │
   │   ① IDP_IDENTITYからOPSession取得                          │
   │   ② 有効なセッションあり → ログイン画面スキップ             │
   │   ③ ClientSession(App B)作成                               │
   │                                                            │
   │ Browser ← IdP: 認可コード                                  │
   │ App B: ID Token取得（sid: yyy）                            │
   └────────────────────────────────────────────────────────────┘

→ 2回目以降はログイン画面なしでアクセス可能
```

### prompt パラメータ

認可リクエストで `prompt` パラメータを使ってセッションの扱いを制御できます。

| 値 | 動作 |
|:---|:----|
| **none** | セッションがあれば自動認可、なければエラー |
| **login** | 既存セッションがあっても再認証を要求 |
| **consent** | 既存セッションがあっても同意画面を表示 |
| **select_account** | アカウント選択画面を表示 |

```
【prompt=none の流れ】

Browser → IdP: GET /authorize?...&prompt=none

IdP:
├── OPSession あり → 自動的に認可、ClientSession作成
│
└── OPSession なし → エラー: login_required
```

**ポイント**:
- `prompt=none` はサイレント認証（ユーザー操作なし）
- 有効なOPSessionが必要
- SPAのセッション確認によく使われる

---

## ログアウト

### RP-Initiated Logout

**RPからログアウトを開始する方式**

```
【RP-Initiated Logout の流れ】

1. ユーザーがApp Aでログアウトボタンをクリック

2. App AがIdPにリダイレクト
   GET /logout?
       id_token_hint=<ID Token>&
       post_logout_redirect_uri=https://app-a.com/logout-callback&
       state=abc123

3. IdPがセッションを終了
   ├── OPSession削除
   ├── 全ClientSession削除
   └── IDP_IDENTITY Cookie削除

4. IdPが指定URLにリダイレクト
   → https://app-a.com/logout-callback?state=abc123
```

**ポイント**:
- `id_token_hint` でどのセッションかを特定
- `post_logout_redirect_uri` で戻り先を指定
- ブラウザリダイレクトベース

### Back-Channel Logout

**サーバー間通信でログアウトを通知する方式**

```
【Back-Channel Logout の流れ】

1. ユーザーがログアウト（IdPまたは任意のRP）

2. IdPが全ClientSessionを検索
   「このユーザーのClientSessionをすべて取得」

   ※ここでユーザーIDでの検索が必要
   ※HttpSessionでは不可能 → 直接Redisアクセスが必要

3. IdPが各RPにLogout Tokenを送信
   ┌────────────────────────────────────────────────────────────┐
   │ POST https://app-a.com/backchannel-logout                  │
   │ Content-Type: application/x-www-form-urlencoded            │
   │                                                            │
   │ logout_token=eyJhbGciOiJSUzI1NiIsInR5cCI6Imp3dCJ9...       │
   │                                                            │
   │ Logout Token (JWT):                                        │
   │ {                                                          │
   │   "iss": "https://idp.example.com",                        │
   │   "sub": "user-123",                                       │
   │   "aud": "app-a",                                          │
   │   "iat": 1704067200,                                       │
   │   "sid": "xxx",  ← どのClientSessionか特定                 │
   │   "events": {                                              │
   │     "http://schemas.openid.net/event/backchannel-logout": {}│
   │   }                                                        │
   │ }                                                          │
   └────────────────────────────────────────────────────────────┘

4. 各RPがローカルセッションを終了
   App A: sidがxxxのセッションを削除
   App B: sidがyyyのセッションを削除
   App C: sidがzzzのセッションを削除
```

**ポイント**:
- ブラウザを経由しない（サーバー間通信）
- ユーザーがオフラインでも通知可能
- 信頼性が高い
- **ユーザーIDでのセッション検索が必須**

### Front-Channel Logout

**ブラウザ経由でログアウトを通知する方式**

```
【Front-Channel Logout の流れ】

1. ユーザーがIdPでログアウト

2. IdPがログアウト確認ページを表示
   ┌────────────────────────────────────────────────────────────┐
   │ <html>                                                     │
   │   <body>                                                   │
   │     <p>ログアウト中...</p>                                  │
   │                                                            │
   │     <!-- 各RPのlogout URLをiframeで読み込み -->            │
   │     <iframe src="https://app-a.com/logout?sid=xxx"/>       │
   │     <iframe src="https://app-b.com/logout?sid=yyy"/>       │
   │     <iframe src="https://app-c.com/logout?sid=zzz"/>       │
   │   </body>                                                  │
   │ </html>                                                    │
   └────────────────────────────────────────────────────────────┘

3. 各RPがiframe内でログアウト処理
   ・ローカルセッション削除
   ・Cookie削除
```

**ポイント**:
- ブラウザ経由（iframe）
- Cookie削除などブラウザ側の処理が可能
- 3rdパーティCookieブロックの影響を受ける
- すべてのiframeの読み込み完了を待つ必要がある

### ログアウト方式の比較

| 方式 | 通信経路 | 信頼性 | 制約 |
|:----|:--------|:------|:----|
| **RP-Initiated** | ブラウザ → IdP | 高 | ブラウザ操作必要 |
| **Back-Channel** | IdP → RP（サーバー間） | 最高 | RPがエンドポイント必要 |
| **Front-Channel** | IdP → RP（iframe経由） | 中 | 3rdパーティCookie制限 |

---

## セッション検索の要件

### なぜ複合検索が必要か

```
【ログアウト時に必要な検索】

1. RP-Initiated Logout
   id_token_hint からsubとsidを取得
   → sidでClientSessionを検索
   → ClientSessionからOPSessionを取得

2. Back-Channel Logout
   OPSession終了時
   → sub（ユーザーID）で全ClientSessionを検索
   → 各RPにLogout Token送信

3. Front-Channel Logout
   OPSession終了時
   → opSessionIdで全ClientSessionを検索
   → 各RPのlogout URLをiframeで表示
```

### 必要なインデックス

| 検索パターン | 用途 |
|:------------|:----|
| opSessionId → OPSession | セッションデータ取得 |
| sub → 全OPSession | ユーザーの全セッション取得 |
| sid → ClientSession | 特定のClientSession取得 |
| opSessionId → 全ClientSession | ログアウト通知 |
| clientId + sub → ClientSession | 特定アプリのセッション確認 |

```
【Redisインデックス設計例】

# メインデータ
op_session:{tenantId}:{opSessionId}
client_session:{tenantId}:{sid}

# インデックス
idx:tenant:{tenantId}:sub:{sub}                    → [opSessionId, ...]
idx:tenant:{tenantId}:op_session:{opSessionId}     → [sid, ...]
idx:tenant:{tenantId}:client:{clientId}:sub:{sub}  → [sid, ...]
```

---

## Session Management iframe

### セッション状態の監視

**RPがIdPのセッション状態をリアルタイムで監視する仕組み**

```
【Session Management iframe】

┌─────────────────────────────────────────────────────────────────┐
│                      RP (App A)                                  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    RPページ                              │   │
│  │                                                         │   │
│  │  ┌───────────────────────────────────────────────────┐ │   │
│  │  │           OP iframe (hidden)                      │ │   │
│  │  │  src: https://idp.example.com/check_session       │ │   │
│  │  │                                                   │ │   │
│  │  │  定期的にpostMessage:                             │ │   │
│  │  │  "client_id session_state"                        │ │   │
│  │  │                                                   │ │   │
│  │  │  → Cookieからセッション状態を計算                  │ │   │
│  │  │  → "unchanged" / "changed" / "error" を返す       │ │   │
│  │  └───────────────────────────────────────────────────┘ │   │
│  │                                                         │   │
│  │  JavaScriptがpostMessageを監視:                         │   │
│  │  ・unchanged → 何もしない                               │   │
│  │  ・changed → prompt=noneで再認証 or ログアウト処理     │   │
│  │  ・error → エラー処理                                   │   │
│  │                                                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2つのCookieの役割

| Cookie | HttpOnly | 用途 |
|:-------|:---------|:----|
| **IDP_IDENTITY** | Yes | サーバー側でセッション識別 |
| **IDP_SESSION** | No | iframe(JavaScript)でセッション状態確認 |

```
【なぜ2つ必要か】

IDP_IDENTITY (HttpOnly=Yes)
├── JavaScriptからアクセス不可
├── サーバー側でのみ使用
└── セキュリティのため必須

IDP_SESSION (HttpOnly=No)
├── JavaScriptからアクセス可能
├── Session Management iframeで使用
├── セッション状態のハッシュ値
└── 実際のセッションIDは含まない（安全）
```

---

## まとめ

### 重要ポイント

| 項目 | 説明 |
|:----|:----|
| **OPSession** | ブラウザとIdP間のセッション（SSO用） |
| **ClientSession** | OPSessionと各RP間のセッション |
| **sid** | ClientSessionの識別子（ID Tokenに含まれる） |
| **Back-Channel Logout** | サーバー間通信、ユーザーID検索が必要 |
| **複合検索** | sub, sid, clientIdでの検索が必要 |

### IdPでの検索要件

```
【必要な検索パターン】

┌──────────────────────────────────────────────┐
│ セッションID → セッションデータ              │ ← 通常のセッション
├──────────────────────────────────────────────┤
│ ユーザーID → 全セッション                    │ ← Back-Channel Logout
│ OPSession → 全ClientSession                 │ ← ログアウト通知
│ sid → ClientSession                        │ ← RP-Initiated Logout
│ clientId + sub → ClientSession             │ ← 再認可確認
└──────────────────────────────────────────────┘

→ HttpSession（セッションIDのみ）では不十分
→ 直接Redisアクセスでインデックス必要
```

### 次のステップ

次に読むべきドキュメント:
- [04. IdPセッション設計パターン](../../../../secret/04-idp-session-patterns.md) - Keycloakとidp-serverの比較

---

## 関連仕様

- [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html)
- [OpenID Connect RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html)
- [OpenID Connect Back-Channel Logout 1.0](https://openid.net/specs/openid-connect-backchannel-1_0.html)
- [OpenID Connect Front-Channel Logout 1.0](https://openid.net/specs/openid-connect-frontchannel-1_0.html)
