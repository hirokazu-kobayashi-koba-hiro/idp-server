# redirect_uri 検証の仕様比較

redirect_uri（リダイレクト URI）の検証方法は、OAuth 2.0 の歴史とともに厳格化されてきました。このドキュメントでは、各仕様における検証ルールの違いを比較し、セキュリティと利便性のバランスを考察します。

---

## 第1部: 概要

### なぜ redirect_uri の検証が重要なのか？

redirect_uri は認可コードやトークンの送信先です。検証が甘いと、攻撃者が認可コードを自分のサーバーに送らせることができます。

```
正常なフロー:
  ユーザー → 認可サーバー → https://legitimate-app.com/callback?code=xxx

攻撃（オープンリダイレクタ）:
  ユーザー → 認可サーバー → https://attacker.com/steal?code=xxx
                            ↑ 認可コードが攻撃者に渡る
```

### 検証の厳格さスペクトラム

```
緩い                                                              厳格
 │                                                                  │
 ▼                                                                  ▼
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│ 登録なし  │  │ 部分一致  │  │ 完全一致  │  │ 完全一致  │  │ 完全一致  │
│ (非推奨)  │  │ (RFC6749) │  │  (OIDC)  │  │ + 制約   │  │ + 制約   │
│          │  │          │  │          │  │ (BCP)    │  │ (FAPI)   │
└──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘

利便性: 高 ◄─────────────────────────────────────────────────► 低
安全性: 低 ◄─────────────────────────────────────────────────► 高
```

---

## RFC 3986: URI 比較の基礎

redirect_uri の検証を理解するには、まず URI の仕様である RFC 3986 を理解する必要があります。

### URI の構造

```
  https://user:pass@example.com:8080/path/to/resource?query=value#fragment
  └─┬─┘   └───┬───┘ └────┬────┘└─┬┘└──────┬────────┘└─────┬─────┘└───┬───┘
 scheme   userinfo     host   port      path           query      fragment
          └──────────┬──────────┘
                 authority
```

| 要素 | 説明 | 大文字小文字 |
|------|------|-------------|
| scheme | プロトコル（http, https） | 区別なし |
| userinfo | ユーザー情報（非推奨） | 区別あり |
| host | ホスト名または IP | 区別なし |
| port | ポート番号 | - |
| path | リソースパス | 区別あり |
| query | クエリパラメータ | 区別あり |
| fragment | フラグメント識別子 | 区別あり |

### URI の比較方法（RFC 3986 Section 6）

RFC 3986 では URI を比較する 4 つの方法を定義しています。

#### 1. Simple String Comparison（単純文字列比較）

> Two URIs are equivalent if they are **identical character-by-character**.

**最も厳格な方法**。文字単位で完全に一致するかを確認。

```
比較対象: https://example.com/callback

✅ 一致: https://example.com/callback
❌ 不一致: https://EXAMPLE.COM/callback     ← 大文字
❌ 不一致: https://example.com:443/callback ← デフォルトポート明示
❌ 不一致: https://example.com/callback/    ← 末尾スラッシュ
❌ 不一致: https://example.com/Callback     ← パスの大文字小文字
```

**OAuth 2.0 Security BCP（RFC 9700）はこの方法を要求。**

#### 2. Syntax-Based Normalization（構文ベース正規化）

正規化してから比較。以下の変換を行う：

| 正規化 | 例 |
|--------|-----|
| スキームを小文字化 | `HTTPS:` → `https:` |
| ホストを小文字化 | `Example.COM` → `example.com` |
| パーセントエンコードを大文字化 | `%2f` → `%2F` |
| 不要なパーセントエンコードを解除 | `%41` → `A` |
| 空パスを `/` に | `https://example.com` → `https://example.com/` |
| デフォルトポートを削除 | `:443` → (削除) |

```
正規化前: HTTPS://Example.COM:443/Path
正規化後: https://example.com/Path

正規化前: https://example.com
正規化後: https://example.com/
```

**この方法を使うと、以下が同一視される：**

```
https://example.com/callback
https://EXAMPLE.COM/callback      ← ホストは正規化で同一
https://example.com:443/callback  ← デフォルトポートは正規化で削除
```

#### 3. Scheme-Based Normalization（スキームベース正規化）

スキーム固有のルールを適用。例えば HTTP では：

- 空パスを `/` に変換
- デフォルトポート（80/443）を削除

#### 4. Protocol-Based Normalization（プロトコルベース正規化）

実際にリソースにアクセスして等価性を判断。リダイレクト先が同じなら同一視するなど。

**セキュリティ上の理由から、OAuth では使用しない。**

### なぜ Simple String Comparison が推奨されるか？

```
┌─────────────────────────────────────────────────────────────────────────┐
│  正規化を行うと...                                                       │
│                                                                         │
│  登録:   https://app.example.com/callback                               │
│                                                                         │
│  リクエスト: HTTPS://APP.EXAMPLE.COM/callback                           │
│                      ↓ 正規化                                           │
│              https://app.example.com/callback                           │
│                      ↓                                                  │
│              ✅ 一致（正規化後）                                         │
│                                                                         │
│  これは一見便利だが...                                                   │
│                                                                         │
│  攻撃者が細工した URI:                                                   │
│    https://app.example.com%2F..%2F..%2Fattacker.com/callback            │
│                      ↓ 正規化（パーセントデコード）                       │
│    https://app.example.com/../../attacker.com/callback                  │
│                      ↓ パス正規化                                        │
│    https://attacker.com/callback   ← 攻撃者のサーバーに！                │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

正規化は**予期しない変換**を引き起こす可能性があり、セキュリティリスクになる。

### 各仕様での URI 比較方法

| 仕様 | 比較方法 | 根拠 |
|------|----------|------|
| RFC 6749 | 明記なし | 実装依存 |
| OIDC Core | 明記なし（「identical」とだけ記載） | 解釈の余地あり |
| RFC 8252 | Simple String Comparison を推奨 | Section 8.4 |
| RFC 9700 | Simple String Comparison を**必須** | Section 4.1.3 |
| FAPI 1.0/2.0 | exact string matching | プロファイルで規定 |

### 実装上の注意点

#### パーセントエンコードの扱い

同じ文字列でもエンコード方法が異なると不一致になる：

```
登録:     https://example.com/callback?state=a b
リクエスト: https://example.com/callback?state=a%20b

Simple String Comparison では ❌ 不一致
```

**対策**: 登録時にパーセントエンコード済みの形式で保存する。

#### 末尾スラッシュ

```
登録:     https://example.com/callback
リクエスト: https://example.com/callback/

Simple String Comparison では ❌ 不一致
```

**対策**: 登録時に末尾スラッシュの有無を統一するポリシーを決める。

#### IPv6 アドレス

```
登録:     http://[::1]/callback
リクエスト: http://[0:0:0:0:0:0:0:1]/callback

Simple String Comparison では ❌ 不一致（同じアドレスでも）
```

**対策**: IPv6 は正規化形式（`[::1]`）で登録することを推奨。

---

## 第2部: 仕様別の検証ルール

### RFC 3986（URI 仕様, 2005年）

redirect_uri 検証の**基礎となる URI 構造と比較方法**を定義。OAuth 関連仕様はすべてこの RFC を参照している。

#### URI の構造

```
  https://user:pass@www.example.com:8080/path/to/resource?query=value#fragment
  └─┬─┘   └───┬──┘ └──────┬───────┘└─┬┘└───────┬───────┘└─────┬────┘└───┬───┘
 scheme  userinfo       host      port      path           query     fragment

  └──────────────────┬──────────────────┘
                 authority
```

| 構成要素 | 説明 | 大文字小文字 | redirect_uri での扱い |
|----------|------|-------------|----------------------|
| scheme | プロトコル | 区別しない | 必須（本番は https） |
| userinfo | ユーザー情報 | 区別する | 通常使用しない |
| host | ホスト名 | 区別しない | 必須 |
| port | ポート番号 | - | 省略時はデフォルト |
| path | リソースパス | **区別する** | 完全一致が求められる |
| query | クエリパラメータ | **区別する** | 仕様により扱いが異なる |
| fragment | フラグメント | **区別する** | **禁止**（OAuth/OIDC 共通） |

#### URI 比較の 3 つの方式（Section 6）

##### 6.2.1 Simple String Comparison（単純文字列比較）

**バイト単位での完全一致**。最も厳格で安全。

```
比較対象: https://example.com/callback

✅ 一致:   https://example.com/callback
❌ 不一致: https://EXAMPLE.COM/callback     ← 大文字
❌ 不一致: https://example.com:443/callback ← ポート明示
❌ 不一致: https://example.com/callback/    ← 末尾スラッシュ
❌ 不一致: https://example.com/Callback     ← パスの大文字
```

**RFC 9700（Security BCP）はこの方式を要求。**

##### 6.2.2 Syntax-Based Normalization（構文ベース正規化）

URI 構文ルールに基づいて正規化してから比較。

| 正規化ルール | 変換例 |
|-------------|--------|
| スキーム小文字化 | `HTTPS:` → `https:` |
| ホスト小文字化 | `EXAMPLE.COM` → `example.com` |
| パーセントエンコード大文字化 | `%2f` → `%2F` |
| 不要なエンコード解除 | `%41` → `A`（非予約文字） |
| 空パス正規化 | `https://example.com` → `https://example.com/` |
| デフォルトポート削除 | `:443` → (削除) |

##### 6.2.3 Scheme-Based Normalization（スキームベース正規化）

スキーム固有のルールを適用。HTTP では末尾スラッシュや `/../` の解決など。

#### 3 方式の比較

| URI ペア | Simple String | Syntax-Based | Scheme-Based |
|----------|---------------|--------------|--------------|
| `http://example.com` vs `HTTP://example.com` | ❌ | ✅ | ✅ |
| `http://example.com` vs `http://example.com/` | ❌ | ❌ | ✅ |
| `http://example.com:80` vs `http://example.com` | ❌ | ✅ | ✅ |
| `http://example.com/~a` vs `http://example.com/%7Ea` | ❌ | ✅ | ✅ |

#### なぜ Simple String Comparison が推奨されるか？

```
正規化を使うと攻撃の余地が生まれる:

登録: https://app.example.com/callback

攻撃者が細工した URI:
  https://app.example.com%2F..%2F..%2Fattacker.com/callback
              ↓ パーセントデコード
  https://app.example.com/../../attacker.com/callback
              ↓ パス正規化
  https://attacker.com/callback   ← 攻撃者のサーバーに！
```

正規化ロジックの実装差異やバグがセキュリティホールになる。

---

### RFC 6749（OAuth 2.0 Core, 2012年）

OAuth 2.0 の基本仕様。**曖昧な表現が多く、実装者の解釈に委ねられる**部分が多い。

#### 登録要件

> The authorization server MUST require the following clients to register their redirection endpoint:
> - Public clients.
> - Confidential clients utilizing the implicit grant type.

| クライアントタイプ | 登録 |
|-------------------|------|
| Public クライアント | 必須 |
| Confidential + Implicit | 必須 |
| Confidential + Authorization Code | 推奨（SHOULD） |

#### 検証方法

> If multiple redirection URIs have been registered, if only part of the redirection URI has been registered, or if no redirection URI has been registered, the client MUST include a redirection URI with the authorization request using the "redirect_uri" request parameter.

**解釈の余地がある表現**:
- 「part of the redirection URI」が何を意味するか不明確
- 前方一致？ドメインのみ？パスのプレフィックス？

#### クエリパラメータ

> The redirection endpoint URI MAY include an "application/x-www-form-urlencoded" formatted query component, which MUST be retained when adding additional response parameters.

クエリパラメータの**追加は許可**されている。

```
登録: https://app.example.com/callback?client=abc
許可: https://app.example.com/callback?client=abc&code=xxx&state=yyy
                                                  ↑ 追加される
```

#### RFC 6749 の問題点

| 問題 | 説明 |
|------|------|
| 部分一致の許容 | パス以下を自由に指定できると攻撃に悪用される可能性 |
| ワイルドカードの解釈 | 仕様に明記なし、実装依存 |
| フラグメントの扱い | 登録時は禁止だが、検証時の扱いが曖昧 |

---

### OpenID Connect Core 1.0（2014年）

OAuth 2.0 より**明確に厳格化**された。

#### 検証方法

> The Redirection URI MUST NOT use the fragment component.
> 
> The Authorization Server MUST require the use of TLS.
>
> The Redirection URI **MUST be an absolute URI** as defined by [RFC3986] Section 4.3.
>
> The Redirection URI **MAY use an application/x-www-form-urlencoded formatted query component**.

そして重要な一文：

> The Authorization Server **MUST verify** that the value of the redirect_uri parameter is **identical** to the Redirection URI that was pre-registered.

#### 完全一致（Exact Match）

OIDC では**完全一致**が要求される。

```
登録: https://app.example.com/callback

✅ 許可: https://app.example.com/callback
❌ 拒否: https://app.example.com/callback/
❌ 拒否: https://app.example.com/callback?extra=param
❌ 拒否: https://app.example.com/callback#fragment
❌ 拒否: https://APP.example.com/callback  ← スキームとホストは大文字小文字区別なし
                                            だがパスは区別される
```

#### 例外: 複数 URI の登録

複数の redirect_uri を登録している場合、リクエストで指定された URI が登録済みリストの**いずれかと完全一致**すればよい。

```
登録済み:
  - https://app.example.com/callback
  - https://app.example.com/oauth/callback
  - https://staging.example.com/callback

リクエスト: redirect_uri=https://app.example.com/callback
→ ✅ 1番目と完全一致
```

---

### RFC 8252（OAuth 2.0 for Native Apps, 2017年）

ネイティブアプリ（モバイル・デスクトップ）向けの特殊なルール。

#### 3つの redirect_uri パターン

| パターン | 形式 | 例 |
|----------|------|-----|
| Private-Use URI Scheme | `com.example.app:/callback` | カスタムスキーム |
| Claimed HTTPS Scheme | `https://app.example.com/.well-known/...` | Universal Links / App Links |
| Loopback Interface | `http://127.0.0.1:{port}/callback` | ローカルサーバー |

#### Loopback の特殊ルール

**ポート番号は動的に変わることを許容**する必要がある。

> The authorization server MUST allow any port to be specified at the time of the request for loopback IP redirect URIs, to accommodate clients that obtain an available ephemeral port from the operating system at the time of the request.

```
登録: http://127.0.0.1/callback  または  http://[::1]/callback

リクエスト時:
  ✅ http://127.0.0.1:51234/callback  ← ポート番号は検証しない
  ✅ http://127.0.0.1:8080/callback
  ✅ http://[::1]:3000/callback
  
  ❌ http://localhost:8080/callback   ← localhost は NG（DNS リバインディング攻撃のリスク）
```

#### Private-Use URI Scheme の推奨事項

> To avoid clashing with schemes that have a defined meaning, schemes using a domain name based on a domain the app developer controls is recommended.

```
推奨:   com.example.myapp:/callback
非推奨: myapp:/callback  ← 他アプリと衝突の可能性
```

---

### RFC 9700（OAuth 2.0 Security BCP, 2024年）

セキュリティベストプラクティス。**最も厳格な推奨事項**を提示。

#### 完全一致の強制

> Authorization servers MUST compare URIs using **exact string matching** as per Section 6.2.1 of [RFC3986] (Simple String Comparison).

Simple String Comparison = バイト単位での完全一致。

#### パターンマッチングの禁止

> Authorization servers **SHOULD NOT** allow clients to register redirect URI patterns.

ワイルドカードやプレフィックスマッチングは禁止。

```
❌ https://app.example.com/callback/*
❌ https://*.example.com/callback
❌ https://app.example.com/callback?*
```

#### なぜパターンマッチングが危険か？

> Such patterns could inadvertently match malicious URIs that are controlled by attackers.

例：

```
登録: https://app.example.com/callback/*

攻撃者が悪用:
  https://app.example.com/callback/../../../attacker/steal
  https://app.example.com/callback/../../.well-known/webfinger?...
```

パストラバーサルやオープンリダイレクタの脆弱性と組み合わせて攻撃される。

#### localhost の禁止

> Clients **SHOULD NOT** use `localhost` as the hostname in loopback redirect URIs.

理由：DNS リバインディング攻撃のリスク。

```
❌ http://localhost:8080/callback
✅ http://127.0.0.1:8080/callback
✅ http://[::1]:8080/callback
```

---

### FAPI 1.0 Baseline / Advanced（2021年）

金融グレード。**最も厳格**。

#### Baseline Profile

> The authorization server shall require redirect URIs to be pre-registered.
> The authorization server shall require the redirect_uri in the authorization request.
> The authorization server shall require **exact matching** of redirect URIs.

#### Advanced Profile

追加で以下を要求：

> The authorization server shall require that the value of redirect_uri is **exactly equal** to one of the pre-registered redirect URIs.

さらに **JAR（JWT-Secured Authorization Request）** または **PAR（Pushed Authorization Requests）** を要求：

> The client shall send the authorization request as a signed JWT (request object).

JAR: 認可リクエストパラメータを署名付き JWT にして送信（フロントチャネル経由だが改ざん検知可能）
PAR: 認可リクエストパラメータをバックチャネルで事前送信

#### FAPI での追加制約

| 制約 | 理由 |
|------|------|
| HTTPS 必須（localhost 除く） | 通信の盗聴防止 |
| localhost 非推奨 | DNS リバインディング対策 |
| フラグメント禁止 | 仕様上の制約 |
| 動的パラメータ禁止 | 予測可能性の確保 |

---

### FAPI 2.0 Security Profile（2023年）

FAPI 1.0 をさらに簡素化・強化。

> The authorization server shall compare the redirect_uri parameter value with the pre-registered redirect URIs using **exact string matching**.

PAR が**必須**となり、redirect_uri を含む認可リクエストパラメータはバックチャネルで送信される。

```
従来（FAPI 1.0 without PAR）:
  ブラウザ → 認可サーバー（フロントチャネル）
  redirect_uri が URL に露出

FAPI 2.0 + PAR:
  クライアント → 認可サーバー（バックチャネル）で認可リクエストパラメータを送信
  ブラウザ → 認可サーバー（フロントチャネル）は request_uri のみ
  
  ※ 認可レスポンス（認可コード）はフロントチャネルで返る
```

---

## 第3部: 比較表

### 検証方法の比較

| 仕様 | 一致方式 | クエリパラメータ | ポート | ワイルドカード |
|------|----------|-----------------|--------|---------------|
| RFC 6749 | 部分一致可 | 追加許可 | 固定 | 実装依存 |
| OIDC Core | 完全一致 | 登録済みのみ | 固定 | 禁止 |
| RFC 8252 | 完全一致 | 固定 | Loopback は動的許可 | 禁止 |
| RFC 9700 | 完全一致 | 固定 | Loopback は動的許可 | 禁止 |
| FAPI 1.0 | 完全一致 | 固定 | 固定 | 禁止 |
| FAPI 2.0 | 完全一致 | 固定 | 固定 | 禁止 |

### 許容される URI の例

| 登録 URI | リクエスト URI | RFC6749 | OIDC | FAPI |
|----------|---------------|---------|------|------|
| `https://app.example.com/cb` | `https://app.example.com/cb` | ✅ | ✅ | ✅ |
| `https://app.example.com/cb` | `https://app.example.com/cb?foo=bar` | ✅ | ❌ | ❌ |
| `https://app.example.com/cb` | `https://app.example.com/cb/` | ⚠️ | ❌ | ❌ |
| `https://app.example.com/` | `https://app.example.com/cb` | ⚠️ | ❌ | ❌ |
| `http://127.0.0.1/cb` | `http://127.0.0.1:8080/cb` | ❌ | ❌ | ❌ |
| `http://127.0.0.1/cb` (Native) | `http://127.0.0.1:8080/cb` | ✅* | ✅* | N/A |

*RFC 8252 に従う場合

### セキュリティと利便性のトレードオフ

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│  利便性                                                                  │
│    ▲                                                                    │
│    │                                                                    │
│    │  ┌─────────┐                                                       │
│    │  │ RFC6749 │ ← 複数パターン登録可、開発が楽                          │
│    │  └─────────┘                                                       │
│    │       │                                                            │
│    │       ▼                                                            │
│    │  ┌─────────┐                                                       │
│    │  │  OIDC   │ ← 完全一致だが複数 URI 登録可                          │
│    │  └─────────┘                                                       │
│    │       │                                                            │
│    │       ▼                                                            │
│    │  ┌─────────┐                                                       │
│    │  │RFC 9700 │ ← パターンマッチ禁止、localhost 非推奨                  │
│    │  └─────────┘                                                       │
│    │       │                                                            │
│    │       ▼                                                            │
│    │  ┌─────────┐                                                       │
│    │  │  FAPI   │ ← 1.0: JAR or PAR、2.0: PAR 必須                       │
│    │  └─────────┘   認可リクエストのパラメータをバックチャネル化           │
│    │                                                                    │
│    └────────────────────────────────────────────────────────────►       │
│                                                         セキュリティ     │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 第4部: 実装上の推奨事項

### 認可サーバー実装者向け

| 推奨レベル | 内容 |
|-----------|------|
| 必須 | redirect_uri の事前登録を必須にする |
| 必須 | 完全一致（exact string match）で検証する |
| 必須 | フラグメント（#）を含む URI を拒否する |
| 強く推奨 | パターンマッチング（ワイルドカード）を許可しない |
| 強く推奨 | HTTP スキームは Loopback IP のみ許可 |
| 推奨 | PAR をサポートして redirect_uri をバックチャネル化 |

### クライアント開発者向け

| 推奨レベル | 内容 |
|-----------|------|
| 必須 | 本番環境では HTTPS を使用 |
| 必須 | redirect_uri は固定値を使用（動的生成しない） |
| 強く推奨 | 開発環境でも `127.0.0.1` を使用（`localhost` ではなく） |
| 推奨 | 環境ごとに別々の redirect_uri を登録（dev, staging, prod） |

### 検証の実装例

```java
public class RedirectUriValidator {
    
    private final Set<String> registeredUris;
    private final boolean allowLoopbackPort;
    
    public boolean validate(String requestUri) {
        // 1. null/空チェック
        if (requestUri == null || requestUri.isEmpty()) {
            return false;
        }
        
        // 2. フラグメントチェック
        if (requestUri.contains("#")) {
            return false;
        }
        
        // 3. Loopback IP の場合はポート番号を無視して比較
        if (allowLoopbackPort && isLoopbackUri(requestUri)) {
            return registeredUris.stream()
                .filter(this::isLoopbackUri)
                .anyMatch(registered -> matchIgnoringPort(registered, requestUri));
        }
        
        // 4. 完全一致で比較
        return registeredUris.contains(requestUri);
    }
    
    private boolean isLoopbackUri(String uri) {
        return uri.startsWith("http://127.0.0.1") 
            || uri.startsWith("http://[::1]");
    }
    
    private boolean matchIgnoringPort(String registered, String request) {
        URI regUri = URI.create(registered);
        URI reqUri = URI.create(request);
        
        return regUri.getScheme().equals(reqUri.getScheme())
            && regUri.getHost().equals(reqUri.getHost())
            && regUri.getPath().equals(reqUri.getPath());
        // ポート番号は比較しない
    }
}
```

---

## まとめ

redirect_uri の検証ルールは、OAuth 2.0 の進化とともに厳格化されてきました。

| 時期 | 仕様 | 特徴 |
|------|------|------|
| 2012年 | RFC 6749 | 曖昧、実装依存 |
| 2014年 | OIDC Core | 完全一致を明記 |
| 2017年 | RFC 8252 | ネイティブアプリ向けルール追加 |
| 2021年 | FAPI 1.0 | 金融グレードの厳格化 |
| 2023年 | FAPI 2.0 | PAR 必須化 |
| 2024年 | RFC 9700 | BCP として完全一致を推奨 |

**新規実装では RFC 9700 / FAPI 2.0 レベルの厳格さを採用することを推奨します。**

---

## 参考リンク

- [RFC 3986 - Uniform Resource Identifier (URI): Generic Syntax](https://datatracker.ietf.org/doc/html/rfc3986)
- [RFC 6749 - OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [RFC 8252 - OAuth 2.0 for Native Apps](https://datatracker.ietf.org/doc/html/rfc8252)
- [RFC 9700 - OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/rfc9700)
- [FAPI 1.0 Security Profile](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-2_0-security-profile.html)
- [OAuth 2.0 Redirect URI Validation (oauth.net)](https://oauth.net/2/redirect-uri/)
