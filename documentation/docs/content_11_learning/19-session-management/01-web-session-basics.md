# Webセッションの基礎

## このドキュメントの目的

**Webセッション**の仕組みを理解し、IdPのセッション管理を学ぶ土台を作ることが目標です。

### 所要時間
約30分

### 学べること
- HTTPのステートレス性とセッションの必要性
- Cookieの仕組みと属性
- Same-Origin / Cross-Origin と Same-Site / Cross-Site の違い
- SameSite属性によるCookie送信制御
- Servletコンテナの役割
- セッションのセキュリティ

---

## HTTPはステートレス

### ステートレスとは

```
【HTTPの特徴】

リクエスト1          リクエスト2          リクエスト3
    │                    │                    │
    ▼                    ▼                    ▼
┌────────┐          ┌────────┐          ┌────────┐
│ GET /  │          │ GET /  │          │ GET /  │
│ cart   │          │ cart   │          │ cart   │
└────────┘          └────────┘          └────────┘
    │                    │                    │
    ▼                    ▼                    ▼
┌────────────────────────────────────────────────────┐
│                    Webサーバー                      │
│                                                    │
│  「誰？」           「誰？」           「誰？」      │
│                                                    │
│  → 各リクエストは独立、前回の情報を覚えていない      │
└────────────────────────────────────────────────────┘
```

**ステートレス（Stateless）**とは:
- サーバーは前回のリクエストを覚えていない
- 各リクエストは独立して処理される
- クライアントの「状態」を保持しない

### なぜセッションが必要か

```
【問題】ログイン状態をどう維持する？

1. ユーザーがログイン
   POST /login { username: "alice", password: "***" }
   → 認証成功

2. 次のリクエスト
   GET /my-account
   → サーバー「誰？ログインしてないよ」

→ 毎回パスワードを送る？ → 非現実的＆危険
```

**解決策**: セッション（Session）
- クライアントを識別するための仕組み
- ログイン状態を維持
- ショッピングカートの中身を保持

---

## Cookieの仕組み

### Cookieとは

**Cookie**: サーバーがブラウザに保存を指示する小さなデータ

```
【Cookie の流れ】

1. サーバーがCookieを設定
   ┌─────────────────────────────────────────────┐
   │ HTTP/1.1 200 OK                             │
   │ Set-Cookie: session_id=abc123; Path=/       │
   └─────────────────────────────────────────────┘
                    │
                    ▼
            ┌───────────────┐
            │   ブラウザ     │
            │               │
            │ Cookies:      │
            │ ・session_id  │
            └───────────────┘

2. ブラウザが自動的にCookieを送信
   ┌─────────────────────────────────────────────┐
   │ GET /dashboard HTTP/1.1                     │
   │ Cookie: session_id=abc123                   │
   └─────────────────────────────────────────────┘
```

**ポイント**:
- サーバーが`Set-Cookie`ヘッダーで設定
- ブラウザが自動的に保存し、次回から自動送信
- ユーザーは意識せずにセッションが維持される

### Cookie属性

| 属性 | 説明 | 例 |
|:----|:----|:---|
| **Name=Value** | Cookieの名前と値 | `session_id=abc123` |
| **Domain** | 送信先ドメイン | `Domain=example.com` |
| **Path** | 送信先パス | `Path=/app` |
| **Expires/Max-Age** | 有効期限 | `Max-Age=3600` |
| **Secure** | HTTPS時のみ送信 | `Secure` |
| **HttpOnly** | JavaScriptからアクセス不可 | `HttpOnly` |
| **SameSite** | クロスサイトリクエスト制御 | `SameSite=Lax` |

### セキュリティ属性

```
【セッションCookieの推奨設定】

Set-Cookie: SESSION=abc123;
            Path=/;
            Secure;        ← HTTPS時のみ送信（盗聴防止）
            HttpOnly;      ← XSS対策（JSからアクセス不可）
            SameSite=Lax;  ← CSRF対策
            Max-Age=1800   ← 30分で期限切れ
```

**HttpOnly**: XSS（クロスサイトスクリプティング）対策
- 悪意のあるJavaScriptからCookieを盗めなくする

**Secure**: 盗聴防止
- HTTPS通信時のみCookieを送信

**SameSite**: CSRF（クロスサイトリクエストフォージェリ）対策

| 値 | 動作 | 用途 |
|:---|:----|:----|
| Strict | 他サイトからは送信しない | 高セキュリティ |
| Lax | GETナビゲーションのみ送信 | 推奨（バランス） |
| None | 常に送信（Secure必須） | サードパーティCookie |

---

## オリジンとCookieの送信

### オリジン（Origin）とは

**オリジン** = スキーム + ホスト + ポート

```
【オリジンの構成要素】

https://idp.example.com:443/authorize
  │          │            │
  │          │            └── ポート（443）
  │          └── ホスト（idp.example.com）
  └── スキーム（https）

→ オリジン: https://idp.example.com:443
```

### Same-Origin vs Cross-Origin

| 比較元 | 比較先 | 判定 | 理由 |
|:------|:------|:-----|:----|
| `https://idp.example.com` | `https://idp.example.com/path` | **Same-Origin** | 全て一致 |
| `https://idp.example.com` | `https://app.example.com` | **Cross-Origin** | ホストが異なる |
| `https://idp.example.com` | `http://idp.example.com` | **Cross-Origin** | スキームが異なる |
| `https://idp.example.com` | `https://idp.example.com:8443` | **Cross-Origin** | ポートが異なる |

### サイト（Site）とは

**サイト** = eTLD+1（有効トップレベルドメイン + 1レベル）

```
【eTLD+1 の例】

idp.example.com
    └────┬────┘
     eTLD+1 = example.com

app.example.com
    └────┬────┘
     eTLD+1 = example.com

→ 両方とも同じサイト（example.com）
```

**eTLD（有効トップレベルドメイン）**:
- `.com`, `.org`, `.net` など
- `.co.jp`, `.com.au` など（2レベルのTLD）
- [Public Suffix List](https://publicsuffix.org/) で管理

### Same-Site vs Cross-Site

```
【Same-Site の判定】

┌─────────────────────────────────────────────────────────────────┐
│                      example.com (サイト)                        │
│                                                                 │
│   ┌─────────────────┐           ┌─────────────────┐            │
│   │ idp.example.com │ ←─────→  │ app.example.com │            │
│   │                 │ Same-Site │                 │            │
│   │    (IdP)        │           │    (RP)         │            │
│   └─────────────────┘           └─────────────────┘            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

【Cross-Site の判定】

┌─────────────────────┐           ┌─────────────────────┐
│   example.com       │           │   another.com       │
│                     │           │                     │
│  ┌───────────────┐  │           │  ┌───────────────┐  │
│  │idp.example.com│  │ ←───────→ │  │app.another.com│  │
│  │               │  │Cross-Site │  │               │  │
│  │    (IdP)      │  │           │  │    (RP)       │  │
│  └───────────────┘  │           │  └───────────────┘  │
│                     │           │                     │
└─────────────────────┘           └─────────────────────┘
```

| 比較 | Same-Origin | Same-Site |
|:----|:------------|:----------|
| 基準 | スキーム + ホスト + ポート | eTLD+1 |
| `idp.example.com` vs `app.example.com` | Cross-Origin | **Same-Site** |
| `example.com` vs `another.com` | Cross-Origin | **Cross-Site** |

### SameSite属性とCookie送信

SameSite属性は**Cross-Site**リクエスト時のCookie送信を制御します。

#### Same-Site リクエストの場合

```
【Same-Site リクエスト】

example.com 内のサブドメイン間
┌─────────────────────────────────────────────────────────────────┐
│                      example.com (サイト)                        │
│                                                                 │
│   ┌─────────────────┐           ┌─────────────────┐            │
│   │ idp.example.com │ ←─────→  │ app.example.com │            │
│   │                 │           │                 │            │
│   │  Cookie:        │  リクエスト│  リンク/POST/   │            │
│   │  SameSite=???   │           │  fetch 全て     │            │
│   └─────────────────┘           └─────────────────┘            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

| SameSite値 | GETナビゲーション | POST送信 | fetch/XHR |
|:-----------|:-----------------|:---------|:----------|
| Strict     | ✅ 送信する       | ✅        | ✅         |
| Lax        | ✅ 送信する       | ✅        | ✅         |
| None       | ✅ 送信する       | ✅        | ✅         |

→ Same-Site内では SameSite属性に関係なく全て送信される
```

#### Cross-Site リクエストの場合

```
【Cross-Site リクエスト】

サイトA (example.com)                    サイトB (another.com)
┌─────────────────────┐                 ┌─────────────────────┐
│                     │                 │                     │
│  Cookie:            │   リクエスト    │  リンククリック      │
│  SESSION=abc123     │ ←───────────── │  <a href="...">     │
│  SameSite=???       │                 │                     │
│                     │                 │                     │
└─────────────────────┘                 └─────────────────────┘

| SameSite値 | GETナビゲーション | POST送信 | fetch/XHR |
|:-----------|:-----------------|:---------|:----------|
| Strict     | ❌ 送信しない     | ❌        | ❌         |
| Lax        | ✅ 送信する       | ❌        | ❌         |
| None       | ✅ 送信する       | ✅        | ✅         |

→ SameSite属性が効くのは Cross-Site の場合のみ
```

**GETナビゲーション**: リンククリック、リダイレクト、アドレスバー入力
**POST送信**: フォーム送信
**fetch/XHR**: JavaScript からの非同期リクエスト

### IdP/RPシナリオ別のCookie挙動

#### シナリオ1: 同一オリジン

```
【同一オリジン構成】

https://example.com/idp  ← IdP
https://example.com/app  ← RP

→ Same-Origin、Same-Site
→ SameSite=Strict でも全て送信される
```

| 設定 | Cookie送信 |
|:----|:----------|
| SameSite=Strict | ✅ |
| SameSite=Lax | ✅ |
| SameSite=None | ✅ |

#### シナリオ2: サブドメイン（Same-Site）

```
【サブドメイン構成】

https://idp.example.com  ← IdP
https://app.example.com  ← RP

→ Cross-Origin だが Same-Site
```

| リクエスト種別 | SameSite=Strict | SameSite=Lax | SameSite=None |
|:-------------|:----------------|:-------------|:--------------|
| リンククリック（GET） | ❌ | ✅ | ✅ |
| フォーム送信（POST） | ❌ | ❌ | ✅ |
| fetch/XHR | ❌ | ❌ | ✅ |

**ポイント**: SameSite=Lax でリンククリック時にCookie送信される（OIDCリダイレクトフローで重要）

#### シナリオ3: 別ドメイン（Cross-Site）

```
【別ドメイン構成】

https://idp.example.com   ← IdP
https://app.another.com   ← RP

→ Cross-Origin かつ Cross-Site
```

| リクエスト種別 | SameSite=Strict | SameSite=Lax | SameSite=None |
|:-------------|:----------------|:-------------|:--------------|
| リンククリック（GET） | ❌ | ✅ | ✅ |
| フォーム送信（POST） | ❌ | ❌ | ✅ |
| fetch/XHR | ❌ | ❌ | ✅ |

**注意**: SameSite=None を使う場合は **Secure属性が必須**

### OIDCフローでの影響

```
【認可コードフロー】

1. RP → IdP へリダイレクト
   GET https://idp.example.com/authorize?...

   ← IdPのCookieが送信されるか？

   SameSite=Lax: ✅ 送信される（トップレベルナビゲーション）
   SameSite=Strict: ❌ 送信されない

2. IdP での認証（IdPドメイン内）
   POST https://idp.example.com/password-authentication

   ← IdPのCookieが送信されるか？

   SameSite=Lax: ✅ 送信される（Same-Site）
   SameSite=Strict: ✅ 送信される（Same-Site）

3. IdP → RP へリダイレクト
   302 Redirect to https://app.another.com/callback?code=...

   ← RPドメインへの遷移（IdPのCookieは関係なし）
```

**推奨設定**:
- IdPのセッションCookie: `SameSite=Lax`
- 理由: Cross-Site からのリダイレクトでもCookieが送信され、SSOが機能する

### Domain属性による共有

```
【Domain属性の効果】

Set-Cookie: SESSION=abc123; Domain=example.com; Path=/

→ 以下のすべてでCookieが送信される:
   - example.com
   - idp.example.com
   - app.example.com
   - sub.app.example.com
```

**注意**: Domain属性を指定すると、サブドメイン間でCookieが共有される

| 設定 | idp.example.com | app.example.com |
|:----|:----------------|:----------------|
| Domain指定なし | ✅ | ❌ |
| Domain=example.com | ✅ | ✅ |

### まとめ: Cookie送信の判定フロー

```
【Cookie送信判定】

リクエスト発生
    │
    ▼
Domain属性チェック ─── 不一致 ──→ ❌ 送信しない
    │
    │ 一致
    ▼
Path属性チェック ─── 不一致 ──→ ❌ 送信しない
    │
    │ 一致
    ▼
Secure属性チェック ─── HTTPなのにSecure ──→ ❌ 送信しない
    │
    │ OK
    ▼
SameSite属性チェック
    │
    ├─ Same-Site リクエスト ──→ ✅ 送信する
    │
    └─ Cross-Site リクエスト
         │
         ├─ Strict ──→ ❌ 送信しない
         ├─ Lax + GETナビゲーション ──→ ✅ 送信する
         ├─ Lax + その他 ──→ ❌ 送信しない
         └─ None + Secure ──→ ✅ 送信する
```

---

## サーバーサイドセッション

### セッションの保存場所

```
【セッションデータの保存場所】

┌───────────────┐      Cookie         ┌────────────────────────────┐
│               │ ←────────────────── │                            │
│   ブラウザ     │   SESSION=abc123   │         サーバー            │
│               │ ──────────────────→ │                            │
└───────────────┘                     │  ┌────────────────────────┐│
                                      │  │ セッションストレージ    ││
                                      │  │                        ││
                                      │  │ abc123:                ││
                                      │  │   user_id: "user-001"  ││
                                      │  │   login_time: ...      ││
                                      │  │   cart: [...]          ││
                                      │  └────────────────────────┘│
                                      └────────────────────────────┘
```

**ポイント**:
- ブラウザには**セッションID**のみ保存
- 実際のデータはサーバー側に保存
- セッションIDがデータを紐付けるキー

### 保存場所の選択肢

| 保存場所 | 特徴 | 用途 |
|:--------|:----|:----|
| **JVMメモリ** | 高速、サーバー再起動で消失 | 開発環境 |
| **ファイル** | 永続化可能、I/O遅い | 小規模サイト |
| **RDB** | 永続化、検索可能 | トランザクション重視 |
| **Redis** | 高速、TTL、クラスタ対応 | 本番環境（推奨） |

---

## Servletコンテナの役割

### Servletコンテナとは

**Servletコンテナ**: Javaで書かれたWebアプリケーションの実行環境

```
【Webアプリケーションの実行環境】

┌─────────────────────────────────────────────────────────────────┐
│                    Servletコンテナ（Tomcat等）                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   HTTPリクエスト受信                     │   │
│  │                         │                               │   │
│  │                         ▼                               │   │
│  │  ┌─────────────────────────────────────────────────┐   │   │
│  │  │ リクエスト/レスポンスオブジェクト生成             │   │   │
│  │  │                                                 │   │   │
│  │  │ ・リクエストパラメータ解析                       │   │   │
│  │  │ ・Cookie解析                                    │   │   │
│  │  │ ・HttpSession管理  ← ここでセッションを管理      │   │   │
│  │  └─────────────────────────────────────────────────┘   │   │
│  │                         │                               │   │
│  │                         ▼                               │   │
│  │  ┌─────────────────────────────────────────────────┐   │   │
│  │  │              アプリケーション                    │   │   │
│  │  │                                                 │   │   │
│  │  │ セッションを利用したビジネスロジック             │   │   │
│  │  └─────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  セッションストレージ                    │   │
│  │                     （JVMメモリ）                        │   │
│  │                                                         │   │
│  │  abc123: { user: "alice", cart: [...] }                │   │
│  │  def456: { user: "bob", preferences: {...} }           │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Servletコンテナの役割**:
- HTTPリクエスト/レスポンスの処理
- Servletのライフサイクル管理
- **セッション管理**（HttpSession）
- スレッドプール管理

**代表的なServletコンテナ**:
- Apache Tomcat
- Jetty
- Undertow（Spring Boot内蔵）

### HttpSessionの仕組み

```
【HttpSessionの動作】

1. 初回リクエスト（セッションなし）
   ┌──────────────────────────────────────────────────────────────┐
   │ GET /cart HTTP/1.1                                           │
   │ (Cookieなし)                                                 │
   │                                                              │
   │ Servletコンテナの処理:                                        │
   │   ① 新しいセッションID生成（例: abc123）                      │
   │   ② JVMメモリにセッション領域確保                             │
   │   ③ レスポンスにSet-Cookie追加                                │
   │                                                              │
   │ HTTP/1.1 200 OK                                              │
   │ Set-Cookie: JSESSIONID=abc123; Path=/; HttpOnly              │
   └──────────────────────────────────────────────────────────────┘

2. 2回目以降のリクエスト
   ┌──────────────────────────────────────────────────────────────┐
   │ GET /cart HTTP/1.1                                           │
   │ Cookie: JSESSIONID=abc123                                    │
   │                                                              │
   │ Servletコンテナの処理:                                        │
   │   ① CookieからセッションID取得                                │
   │   ② JVMメモリから該当セッション検索                           │
   │   ③ アプリケーションにセッションを渡す                        │
   └──────────────────────────────────────────────────────────────┘
```

**ポイント**:
- セッション管理は**Servletコンテナの責務**
- アプリケーションは**HttpSession API**を使うだけ
- セッションデータは**JVMメモリ**に保存（デフォルト）
- Cookie名は通常`JSESSIONID`

---

## セッションのセキュリティ

### セッション固定攻撃

**攻撃シナリオ**:

```
1. 攻撃者が自分のセッションIDを取得
   攻撃者 → サーバー: GET /
   サーバー → 攻撃者: Set-Cookie: SESSION=evil123

2. 攻撃者がそのセッションIDを被害者に使わせる
   攻撃者 → 被害者: 「このリンクをクリック」
   https://example.com/?SESSION=evil123

3. 被害者がログイン
   被害者 → サーバー: POST /login (Cookie: SESSION=evil123)
   サーバー: セッションevil123にログイン状態を紐付け

4. 攻撃者がセッションを乗っ取る
   攻撃者 → サーバー: GET /my-account (Cookie: SESSION=evil123)
   サーバー: 「被害者としてログイン済みですね」
   → 攻撃者が被害者のアカウントにアクセス
```

### 対策: セッションID再生成

```
【対策後の流れ】

攻撃者のセッションID: evil123
                │
被害者がログイン    │
                ▼
┌─────────────────────────────────────────────────┐
│ ログイン成功時にセッションID再生成               │
│                                                 │
│ 古いID: evil123 → 新しいID: new456             │
│                                                 │
│ 攻撃者の evil123 は無効に                       │
└─────────────────────────────────────────────────┘
                │
                ▼
攻撃者: evil123でアクセス → 無効なセッション
被害者: new456でアクセス → 正常にログイン状態
```

**必須対策**:
- ログイン成功時にセッションIDを再生成する
- これにより攻撃者の知っているセッションIDが無効化される

---

## Cookieのパスによる分離

### マルチテナントでの分離

```
【テナントごとにCookieを分離】

Browser Cookie Storage:
│
├── Path: /tenant-a/
│   └── SESSION = "session-for-tenant-a"
│
├── Path: /tenant-b/
│   └── SESSION = "session-for-tenant-b"
│
└── Path: /tenant-c/
    └── SESSION = "session-for-tenant-c"

→ /tenant-a/* へのリクエスト
   Cookie: SESSION=session-for-tenant-a のみ送信

→ /tenant-b/* へのリクエスト
   Cookie: SESSION=session-for-tenant-b のみ送信
```

**ポイント**:
- Cookie の `Path` 属性でテナント分離可能
- 同じブラウザで複数テナントに独立してログイン可能

---

## セッションのライフサイクル

### 状態遷移

```
【セッションの一生】

1. 作成
   初回アクセス時
   → セッションID生成
   → Set-Cookie送信

2. 利用中
   ├─ 属性の読み書き
   ├─ 最終アクセス時刻更新
   └─ タイムアウト時間リセット

3. ログイン時
   → セッションID再生成（セキュリティ）
   → ユーザー情報をセッションに保存

4. ログアウト時
   → セッション無効化
   → セッションデータ削除
   → Cookie削除

5. タイムアウト
   → 一定時間アクセスなし
   → サーバーがセッション削除
   → 次回アクセス時は新規セッション
```

---

## まとめ

### 重要ポイント

| 項目 | 説明 |
|:----|:----|
| **Cookie** | ブラウザに保存、リクエスト時に自動送信 |
| **セッションID** | サーバー側データとの紐付けキー |
| **Servletコンテナ** | HttpSessionを管理する実行環境 |
| **HttpOnly** | XSS対策（必須） |
| **Secure** | HTTPS時のみ送信（必須） |
| **SameSite** | CSRF対策（推奨: Lax） |
| **セッションID再生成** | ログイン時の必須対策 |

### 次のステップ

次に読むべきドキュメント:
- [02. 分散セッション管理](./02-distributed-session.md) - 複数サーバーでのセッション共有

---

## 関連仕様

- [RFC 6265 - HTTP State Management Mechanism](https://datatracker.ietf.org/doc/html/rfc6265) - Cookie仕様
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
