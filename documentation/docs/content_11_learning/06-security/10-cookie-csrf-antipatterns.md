# Cookie と CSRF のアンチパターン集

BFF / SPA / IdP 実装で繰り返し踏まれるパターンをまとめたもの。
各項目は「症状 → なぜダメか → 正しくは」の形式。

## 前提：なぜ CSRF が起きるのか

Cookie は *ambient authority*（環境権限）であり、保持者が明示的に提示しなくても自動で効く。
これは仕様上の性質であってバグではない。CSRF 対策とは、この性質を後付けで打ち消す作業である。

### 元々の設計：発信元を一切見なかった

Cookie の送信可否は、本来「送信先が Domain / Path 条件に合致するか」だけで決まっていた。
リクエストがどのページから発生したかは判定に入らない。

```
発信元: https://evil.com           ← 判定に使われない（SameSite 以前）
送信先: https://bff.example.com/api/transfer
        → Domain / Path が合致するので Cookie を添付
```

サーバ側から見ると、正規の画面から来たリクエストと**完全に見分けがつかない**。
Cookie は正しく、セッションも有効だからである。

### 現在：発信元も見るようになった（ただし別の単位で）

SameSite の既定化（2020年前後）以降、ブラウザは発信元も考慮する。

- SameSite 判定のために発信元の **site** を見る
- `Origin` ヘッダを付与する
- `Sec-Fetch-Site` を付与する

### 判定単位が3つに分かれているのが混乱の元

同じ「クロスサイトかどうか」に見えて、仕組みごとに**判定単位が異なる**。

| 仕組み | 判定単位 | `a.example.com` と `b.example.com` |
|---|---|---|
| SOP / CORS / `Origin` ヘッダ | **オリジン**（`scheme://host:port`） | **別物** |
| SameSite | **site**（schemeful eTLD+1） | **同じ** |
| Cookie のスコープ | **Domain 属性 + Path**（ポートは無視） | Domain 指定次第 |

Cookie の送信先マッチングはオリジン単位ではなく、Cookie 独自の Domain / Path ルールで決まる。
ポートを区別しない点に注意（F-1 参照）。

**B-1 のサブドメイン穴はこの単位のズレが原因**である。
CORS はオリジンで判定するので `evil.example.com` を別物として扱えるが、
SameSite は site で判定するため同一サイト扱いになり素通しする。

### 自動送信される認証情報はすべて対象

| 認証方式 | 送信のトリガー | CSRF |
|---|---|---|
| Cookie | ブラウザが自動 | **起きる** |
| HTTP Basic 認証 | ブラウザが自動 | **起きる** |
| クライアント証明書 (mTLS) | ブラウザが自動 | **起きる** |
| `Authorization` ヘッダ | JS が明示的に付ける | 起きない |

---

## A. 認識レベルのアンチパターン

### A-1. 「SPA だから CSRF は関係ない」

**症状**
Bearer トークン時代の感覚のまま Cookie ベースへ移行し、CSRF 対策を入れ忘れる。

**なぜダメか**
`Authorization` ヘッダ方式で CSRF が起きなかったのは、トークンが同一オリジンの JS からしか読めず、
攻撃者のオリジンから付けようがなかったため（構造的な防御）。
Cookie に移した瞬間にこの構造は消える。

**正しくは**
保存先を Cookie に変更する判断と、CSRF 対策を入れる判断はセットで行う。片方だけの移行はしない。

---

### A-2. 「HttpOnly にしたから安全」

**症状**
XSS 対策として HttpOnly Cookie を導入し、それで対策完了とみなす。

**なぜダメか**
HttpOnly が防ぐのは「JS からの読み取り」だけ。CSRF は読み取りを必要としない攻撃なので一切効かない。
XSS と CSRF は別軸の脅威であり、どちらか片方を選ぶ構造になっている。

```
        Cookie      Bearer(Web Storage)
XSS       強い            弱い
CSRF      弱い            強い
```

**正しくは**
二本立てで対策する。

- XSS 側 → HttpOnly Cookie ＋ CSP ＋ 依存関係の監査
- CSRF 側 → SameSite ＋ Origin 検証（＋必要なら CSRF トークン）

---

### A-3. 「CORS があるから守られている」

**症状**
CORS を設定してあるので、クロスオリジンからのリクエストは弾かれると考える。

**なぜダメか**
CORS は制限を**緩める**仕組みであって、守る仕組みではない。
また同一オリジンポリシー（SOP）が制限するのは**レスポンスの読み取り**であり、**リクエストの送信**ではない。

```
evil.com → bff.example.com へ POST
  送信:     通る（Cookie 付き）
  読み取り:  SOP でブロック
```

とくに `<form>` 送信は CORS の管轄外。
`application/x-www-form-urlencoded` / `multipart/form-data` / `text/plain` の POST は
「単純リクエスト」扱いでプリフライトが飛ばない。

なお CORS の判定単位は**オリジン**であり、SameSite の **site** とは異なる（前提セクション参照）。
「CORS で弾けるから SameSite でも弾ける」という推論は成り立たない。

**正しくは**
CORS を CSRF 対策として数えない。サーバ側で Origin を検証する。

---

### A-4. 「攻撃者にレスポンスは読めないから実害はない」

**症状**
CSRF はレスポンスが読めないので情報漏洩にはならない、と軽視する。

**なぜダメか**
CSRF は「撃てるが結果は見えない」攻撃。危険なのは**状態変更**であって読み取りではない。

- メールアドレス変更 ＋ パスワードリセット → **アカウント乗っ取りに直結**
- 2FA 無効化
- 送金、購入、退会
- 権限付与、API キー発行

**正しくは**
状態変更を伴うすべてのエンドポイントを保護対象とみなす。

---

## B. SameSite まわりのアンチパターン

### B-1. SameSite=Lax を万能だと思う

**症状**
`SameSite=Lax` を設定したので CSRF 対策は完了、と判断する。

**なぜダメか**
Lax には明確な穴が4つある。

| ケース | Lax で防げるか |
|---|---|
| クロスサイト POST | ○ |
| **クロスサイト GET で状態変更** | **✗** |
| **サブドメイン間**（`evil.example.com` → `api.example.com`） | **✗** |
| 古いブラウザ / SameSite 未対応 UA | ✗ |

とくにサブドメインの穴が重要。SameSite は **site 単位（schemeful eTLD+1）** であってオリジン単位ではない。
CORS がオリジン単位で判定するのと**単位がズレている**ため、
CORS では別物として扱える `evil.example.com` が、SameSite では同一サイト扱いになる。
ユーザー投稿を置いている CDN サブドメインに XSS が1つあれば、SameSite は貫通する。

**正しくは**
Lax は基礎防御と位置づけ、Origin 検証を必ず併用する。

---

### B-2. GET に副作用を持たせる

**症状**

```
GET /api/items/123/delete
GET /api/logout
GET /api/settings?theme=dark   （保存される）
```

**なぜダメか**
`SameSite=Lax` はトップレベル GET 遷移では Cookie を送る。つまり GET は素通しになる。
さらに JS すら不要で成立する。

```html
<img src="https://bff.example.com/api/items/123/delete">
```

画像として壊れていても、**リクエストは既に届いている**。
`<link>` `<iframe>` `<video>` `<script>`、CSS の `background-image` ——
外部リソースを読み込む仕組みはすべて攻撃経路になる。

**正しくは**
GET は安全（safe）かつ冪等に保つ。状態変更は POST / PUT / PATCH / DELETE に寄せる。
これは設計レベルの前提であり、ここが崩れていると他の対策が効かなくなる。

---

### B-3. form_post 対応で SameSite=None にして、そのまま放置

**症状**
OIDC の `response_mode=form_post` を導入したところ「state が見つからない」エラーが多発。
調べて `SameSite=None; Secure` に変更したら直ったので、そのまま本番へ。

**なぜダメか**
`form_post` は認可サーバからのクロスサイト **POST** なので Lax では Cookie が飛ばない。
これ自体は正しい対処だが、**`None` にした時点で CSRF 防御が完全に消える**。

| response_mode | 戻り方 | 必要な SameSite |
|---|---|---|
| `query` / `fragment` | トップレベル GET 遷移 | Lax でOK |
| `form_post` | クロスサイト POST | **`None; Secure` 必須** |

**正しくは**
以下のいずれかを取る。

1. `None` にする Cookie を**コールバック処理専用に限定**する（Path を絞り、TTL を数分にする）。
   アプリ本体のセッション Cookie は Lax のまま分離する。
2. `response_mode=query` で足りるなら、そもそも `form_post` を使わない。
3. `None` を使うなら Origin 検証と CSRF トークンを必ず併用する。

---

### B-4. Domain 属性を無警戒に広げる

**症状**

```
Set-Cookie: session=...; Domain=example.com
```

「サブドメインでも使いたいから」で安易に指定する。

**なぜダメか**
Cookie が全サブドメインに送られるようになる。
サブドメインが1つでも侵害されれば（XSS、サブドメインテイクオーバー、放置された古いサービス）、
セッション Cookie がそこへ流れる。B-1 のサブドメイン穴と組み合わさると被害が拡大する。

**正しくは**
Domain を指定せずホスト限定にする。必要な場合のみ最小範囲で指定する。
併せて `__Host-` プレフィックスを使う。

```
Set-Cookie: __Host-session=...; Secure; Path=/; SameSite=Lax
```

`__Host-` を付けると Domain 指定が禁止され、Path が `/` 強制、Secure 必須になる。
サブドメインからの Cookie 上書き（Cookie tossing）を防げる。

---

## C. トークン保管のアンチパターン

### C-1. アクセストークン / リフレッシュトークンを Web Storage に置く

**症状**
`localStorage` にトークンを保存。指摘されて `sessionStorage` に変更し、対策完了とする。

**なぜダメか**
XSS に対しては**ほぼ変わらない**。どちらも同じ JS コンテキストから同期的に読める。

```js
sessionStorage.getItem('token')  // 同じ1行で読める
```

`sessionStorage` が優れているのは XSS 以外の部分（タブを閉じたら消える、タブ単位で隔離、
永続しない）であり、窃取の可否は変わらない。差は**攻撃の持続時間だけ**。

とくに**リフレッシュトークン**は長期の再発行権そのものなので、Web Storage に置いてはいけない。

**正しくは**

| 対策 | XSS 耐性 |
|---|---|
| localStorage | ✗ |
| sessionStorage | ✗（持続時間が短いだけ） |
| JS メモリ変数 | △（窃取窓が狭いだけ） |
| **HttpOnly Cookie** | ○ |
| **BFF（トークンをブラウザに出さない）** | ◎ |
| DPoP / mTLS（送信者制約） | ◎（盗まれても他所で使えない） |

優先順位は以下。保存先の議論は最後でよい。

1. XSS そのものを潰す（CSP、フレームワークのエスケープ、依存関係の監査）
2. リフレッシュトークンをブラウザから出す
3. アクセストークンを短命にする
4. 保存先を選ぶ

---

### C-2. トークンを Cookie の中身に直接詰めて返す

**症状**
BFF をステートレスにしたいので、アクセストークン / リフレッシュトークンを
そのまま HttpOnly Cookie の値として返す。

**なぜダメか**
`sessionStorage` より確実にマシだが、BFF セッション方式より明確に劣る。

| | sessionStorage | Cookie に直接格納 | BFF セッション |
|---|---|---|---|
| XSS で窃取 | ✗ | ○ | ○ |
| Cookie 窃取マルウェア | ✗ | ✗ | ○（ID のみ） |
| **即時失効** | ✗ | **✗** | ○ |
| サイズ制限 | 問題なし | **4KB の壁** | 問題なし |
| CSRF 対策 | 不要 | **必要** | **必要** |

具体的な問題:

- **失効できない**。「このセッションを今すぐ切る」ができず、exp まで有効のまま
- **4KB 制限**。RFC 9068 準拠の JWT に claim を盛ると超える。分割し始めると壊れる
- **ローテーションが辛い**。同時リクエストで競合し、古い refresh token の使用が
  再利用検知に引っかかって全セッション破棄、という事故が起きる
- **[端末侵害（infostealer）](./07-session-security.md#4-端末侵害によるセッション窃取infostealer)に耐性がない**。ディスク上の Cookie ストアを直接抜かれる

**正しくは**
BFF がセッションストアにトークンを保持し、ブラウザにはセッション ID だけを返す。
Redis を1つ立てられるなら、こちらにする。

---

### C-3. code_verifier をブラウザに出す

**症状**
SPA で PKCE を実装し、`code_verifier` を `sessionStorage` に保存する。

**なぜダメか**
XSS で読める。また外部ブラウザ / Custom Tabs 経由で戻ると `sessionStorage` が読めず、
認可フローそのものが壊れる。

**正しくは**
BFF 構成なら、`state` をキーにしたサーバ側セッションに格納する。

```
session[state] = { verifier, nonce, return_to, created_at }
```

ブラウザには紐付けキー（＝ state と Cookie）だけを渡す。

---

### C-4. セッションを切ればトークンも止まると思う

**症状**
ログアウトでサーバ側セッションを破棄し、それで全部無効になったとみなす。

**なぜダメか**
セッションとアクセストークンは**別々に期限を持つ**。BFF ではこの2つが同居する。

```
   ログアウト
      ↓
   ┌──────────────────┐
   │ セッション        │ ← 即座に無効化できる
   └──────────────────┘
   ┌──────────────────────────────────┐
   │ アクセストークン                   │ ← 有効期限まで生きる
   └──────────────────────────────────┘
                                    ↑ ここが残る
```

リソースサーバが JWT を自前検証している場合、認可サーバに問い合わせないため
**トークンが無効化されたことに気づけない**。

**正しくは**
以下のいずれか、または組み合わせ。

| 方法 | 効果 | コスト |
|---|---|---|
| アクセストークンを短命にする | ズレの幅を小さくする | 更新回数が増える |
| ログアウト時に失効させる（RFC 7009） | ズレを無くす | リソースサーバが introspection を見る必要がある |
| 識別子型トークン + introspection | 常に最新の状態を見る | 呼び出しごとに問い合わせ |

**JWT の自己完結性と即時失効は両立しない。** どちらを取るかの判断になる。

---

## D. 検証実装のアンチパターン

### D-1. state を「値の一致」だけで検証する

**症状**
リクエストに載っていた state を、そのままレスポンスの state と比較して終わり。

**なぜダメか**
state 検証の本質は「**このブラウザが開始した認可か**」の確認であって、値の一致ではない。
値の実体をクライアント側だけに持たせると、攻撃者が自分で用意した state を送り込める。

**正しくは**
state の実体はサーバ側に置き、以下を実施する。

1. state が存在する（無ければ即エラー。`error` パラメータ時も検証してから返す）
2. **定数時間比較**で照合
3. 照合成功したら**即削除**（単回使用）
4. TTL 超過（5〜10分程度）で破棄
5. 紐づく `code_verifier` を取り出して token リクエストへ
6. `nonce` は ID Token 側で別途検証

state / PKCE / nonce は役割が異なるので、兼用させない。

- **state**：CSRF 防止 ＋ アプリ状態の復元
- **PKCE**：認可コード横取り防止
- **nonce**：ID Token のリプレイ防止

---

### D-2. Origin / Referer を検証していない

**症状**
SameSite だけに依存し、サーバ側で発信元を確認していない。

**なぜダメか**
B-1 のサブドメイン穴、B-3 の `None` 設定、古い UA ——
SameSite が効かないケースが現実に存在する。

**正しくは**
`Origin` ヘッダを allowlist と照合する。実装コストが低く効果が高い。

- `Origin` は**ブラウザが強制的に付けるので JS から偽装できない**
- POST には必ず付く
- 無い場合は `Referer` にフォールバック。どちらも無ければ拒否

---

### D-3. CSRF トークンをセッションに紐づけていない

**症状**
CSRF トークンを発行しているが、セッションと無関係な値、または全ユーザー共通の固定値。

**なぜダメか**
攻撃者が自分のアカウントで正規のトークンを取得し、それを攻撃ページに埋め込めば通ってしまう。

**正しくは**
synchronizer token pattern（セッションに紐づけてサーバ側で照合）か、
double submit cookie を使う。後者を使う場合は `__Host-` プレフィックスを併用し、
サブドメインからの Cookie 上書きを防ぐ。

:::warning CSRF トークンは XSS の保険にならない
CSRF トークンは「攻撃者はページの中身を読めない」という前提に立っている。
XSS が成立した時点でその前提が崩れるため、攻撃者は正規のフォームからトークンを読み、
正規の手順でリクエストを組み立てられる。

XSS 対策の代替として CSRF トークンを数えないこと。
:::

---

### D-4. 単一キーで state を保存し、複数タブで壊れる

**症状**

```js
session["oauth_state"] = state   // 後勝ちで上書きされる
```

2つのタブから認可を開始すると、先に開始したタブが壊れる。

**正しくは**
セッション内に state をキーとした辞書を持つ。

```js
session.oauth[state] = { verifier, nonce, return_to }
```

上限を 3〜5 件程度に設定し、古いものから破棄する。

---

## E. リダイレクト復帰まわりのアンチパターン

### E-1. コールバック後に URL を掃除しない

**症状**
`?code=...&state=...` が付いたまま画面を表示し、そのまま放置する。

**なぜダメか**

- リロードで code が再送され `invalid_grant` になる
- ブラウザ履歴に code が残る
- 同一オリジン遷移では `Referrer-Policy: strict-origin-when-cross-origin` でも
  **フル URL が送られる**ため、サイト内リンクから code が漏れる
- Analytics のページビュー送信に URL ごと乗る

**正しくは**
token 交換の直後に `history.replaceState` で query を除去してから遷移する。
併せてコールバックページに `Referrer-Policy: no-referrer` を明示する。

---

### E-2. sessionStorage だけで復帰状態を持つ

**症状**
認可リダイレクトからの復帰情報を `sessionStorage` のみに保存する。

**なぜダメか**
`sessionStorage` は**タブ単位**。以下のケースで読めなくなる。

- iOS の SFSafariViewController / Android の Custom Tabs 経由
- 認可画面が別タブで開いた場合
- ユーザーが URL を手動で別タブに貼り直した場合

「Cookie の session（ブラウザを閉じるまで・全タブ共有）」と
「sessionStorage の session（そのタブが生きている間だけ）」は粒度が異なる。

**正しくは**
サーバセッション（Cookie）を正とし、`sessionStorage` は UI 復元の補助に留める二段構え。

---

### E-3. UI 状態の永続化に機密情報を巻き込む

**症状**
Zustand persist や TanStack Query persister をデフォルト設定のまま導入し、
ストア全体を `localStorage` に保存する。

**なぜダメか**
API レスポンスや個人情報がそのまま平文で残る。トークンがストアに含まれていれば一緒に漏れる。

**正しくは**
保存対象を明示的に絞る。

```js
persist(store, {
  name: 'draft',
  storage: createJSONStorage(() => sessionStorage),
  partialize: (s) => ({ formDraft: s.formDraft }),  // トークンは含めない
})
```

TanStack Query なら `shouldDehydrateQuery` で個人情報を含むクエリを除外する。
決済情報・個人情報は退避対象にしない。

---

## F. 運用・開発環境のアンチパターン

### F-1. ポートでセッションが分離されると思い込む

**症状**
`localhost:3000` と `localhost:8080` で別プロジェクトを開発し、セッションが混ざる。

**なぜダメか**
**Cookie はポートを区別しない**。ホスト名が同じなら共有される。

**正しくは**
開発時は `app.localhost` / `api.localhost` のようにホスト名を分けるか、
ブラウザプロファイルを分ける。

---

### F-2. タブを分ければ別アカウントで検証できると思う

**症状**
マルチテナントの検証で、タブを分けてテナント A / B に同時ログインしようとする。

**なぜダメか**
Cookie は**ブラウザプロファイル単位**で共有される。タブ単位ではない。

| 単位 | Cookie | sessionStorage | localStorage |
|---|---|---|---|
| タブ | 共有 | **隔離** | 共有 |
| ウィンドウ | 共有 | 隔離 | 共有 |
| プロファイル | 共有（この単位） | – | 共有 |
| 別プロファイル | 隔離 | 隔離 | 隔離 |
| シークレット | 隔離 | 隔離 | 隔離 |

**正しくは**
プロファイルを分けるか、シークレットウィンドウ / ゲストプロファイルを使う。

---

## チェックリスト

Cookie ベース認証を導入する際の最低ライン。

- [ ] `SameSite=Lax`（可能なら `Strict`）を設定した
- [ ] `Secure` を設定した
- [ ] `HttpOnly` を設定した
- [ ] `__Host-` プレフィックスを付けた（Domain を広げていない）
- [ ] サーバ側で `Origin` を allowlist 照合している
- [ ] GET に副作用のあるエンドポイントが存在しない
- [ ] `SameSite=None` にした Cookie がある場合、用途と Path を限定し CSRF トークンを併用している
- [ ] リフレッシュトークンがブラウザの Web Storage に存在しない
- [ ] state が単回使用・TTL 付き・定数時間比較で検証されている
- [ ] state が複数タブに対応している（単一キー上書きになっていない）
- [ ] コールバック後に URL から code / state を除去している
- [ ] 状態変更エンドポイントが網羅的に保護されている（追加時の漏れを防ぐ仕組みがある）
- [ ] CSP を設定している（XSS 側の対策が別途ある）
- [ ] ログアウト時のアクセストークンの扱いを決めてある（C-4）

---

## 関連ドキュメント

| ドキュメント | 内容 |
|---|---|
| [Webセッションの基礎](../19-session-management/01-web-session-basics.md) | Cookie の仕組み、オリジン、サーバーサイドセッション |
| [セッションセキュリティ](./07-session-security.md) | セッション攻撃、Cookie 属性、ライフサイクル |
| [トークン保存のセキュリティ](./05-token-storage-security.md) | 保存場所ごとの性質 |
| [OAuth セキュリティ脅威](./04-oauth-security-threats.md) | CSRF を含む攻撃手法の全体像 |
| [RFC 10017](../16-oauth-oidc-rfc/security/rfc10017-browser-based-apps.md) | ブラウザアプリのアーキテクチャ選択 |

---

## 参考

- [RFC 6749 — The OAuth 2.0 Authorization Framework](https://www.rfc-editor.org/rfc/rfc6749.html)
- [RFC 9068 — JWT Profile for OAuth 2.0 Access Tokens](https://www.rfc-editor.org/rfc/rfc9068.html)
- [RFC 8707 — Resource Indicators for OAuth 2.0](https://www.rfc-editor.org/rfc/rfc8707.html)
- [RFC 9700 — OAuth 2.0 Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700.html)
- [RFC 10017 — OAuth 2.0 for Browser-Based Applications](https://www.rfc-editor.org/rfc/rfc10017.html)（BFF 推奨の根拠）
- [draft-ietf-httpbis-rfc6265bis — Cookies: HTTP State Management Mechanism](https://datatracker.ietf.org/doc/draft-ietf-httpbis-rfc6265bis/)（SameSite / `__Host-`。2026-08 時点で未発行）
- [OWASP Cross-Site Request Forgery Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

---

**最終更新**: 2026-08-28
**対象**: フロントエンド開発者、バックエンド開発者、セキュリティエンジニア
