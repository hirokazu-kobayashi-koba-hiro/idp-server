# 認可画面を別サイトに置く

## このドキュメントの目的

**認可画面（サインイン・同意の画面）を idp-server と別のサイトに置いても、Safari を含むブラウザでログインと SSO が成立する**ようにすることが目標です。

「別サイト」は登録可能ドメイン（eTLD+1）が違うことを指します。`auth.example.com` と `api.example.com` は同一サイトなので対象外です。`auth.example-login.com` と `api.example.com` のような組み合わせが対象です。

### 所要時間
⏱️ **約20分**（自作の認可画面の改修は含みません）

### 前提条件
- 管理者トークンを取得済み
- 組織ID（organization-id）とテナントID（tenant-id）を取得済み
- 認可画面を別サイトで配信済み（`ui_config.base_url`、または [バリアント](./02-authorization-view-canary.md) の `base_url` がそのサイトを指している）

---

## なぜ設定が要るのか

認可画面は、認証や同意の API を XHR で呼びます。画面が別サイトにあると、これらの呼び出しはブラウザにとってサードパーティになり、Safari は idp-server の Cookie を**送らず、保存もしません**。その結果、次の 2 つが成立しなくなります。

- **ブラウザの束縛**（`IDP_AUTH_SESSION`）：認可リクエストを始めたブラウザかを確かめる Cookie が届かない
- **OP セッション**（`IDP_IDENTITY`）：ログインしても Cookie が保存されず、SSO が効かない

別サイト認可画面モードでは、Cookie に頼っていたところを次のように置き換えます。

| 置き換えるもの | 置き換え先 |
|---|---|
| 認可画面からの呼び出しが、始めたブラウザからか | 認可リクエストの応答で認可画面に渡す値 `view_binding`。認可画面は呼び出しのたびに `x-view-binding` ヘッダーで送る |
| 認証したブラウザの識別 | 認証ステップの応答で渡す使い捨ての値 `auth_proof` |
| OP セッションの保存 | 最後に idp-server を**トップレベル遷移**で経由させ、そこで保存する（`/complete`） |

---

## 仕組み

XHR はページがその場に居たまま裏で呼ぶので、アドレスバーは動きません。トップレベル遷移はタブ自体が移動します。idp-server の Cookie がファーストパーティとして扱われるのは、アドレスバーが idp-server を指しているときだけです。

| # | アドレスバー | やっていること |
|---|---|---|
| 1 | RP | ログインボタンで認可リクエストへ遷移させる（今までどおり） |
| 2 | idp-server `/v1/authorizations` | `IDP_AUTH_SESSION` を保存し、認可画面へ 302。認可画面の URL の fragment に `view_binding` を付ける |
| 3 | 認可画面 | 認証と `authorize` を XHR で呼ぶ。呼び出しには `x-view-binding` ヘッダーで `view_binding` を付ける。認証ステップの応答で `auth_proof` ① を受け取り、`authorize` に付ける。`authorize` は code を返さず、`/complete` 用の `auth_proof` ② を返す |
| 4 | idp-server `/v1/authorizations/{id}/complete?auth_proof=②` | 認可画面が遷移させる。`IDP_AUTH_SESSION` と ② を照合し、OP セッションの Cookie を保存して RP へ 302 |
| 5 | RP | 今までどおりのコールバック（code と state） |

**RP の実装は変わりません。** 変わるのは認可画面と idp-server です。

`view_binding` は、認可リクエストのたびに作り直す値で、DB にはハッシュだけを置きます。URL の fragment はサーバーに送られないので、認可リクエストを始めたブラウザの認可画面だけが受け取ります。認可画面からの呼び出しは、この値で始めたブラウザのものだと確かめます。値が無い・違う呼び出しは 401 です。

`/complete` では 2 つを照合します。`auth_proof` ② は「認証したブラウザか」、`IDP_AUTH_SESSION` は「認可リクエストを始めたブラウザか」を確かめます。どちらかが欠けると、攻撃者が始めた認可を被害者に完了させる、またはその逆が通ってしまうため、両方が必要です。

---

## 設定

### テナント全体を切り替える

`ui_config` に `cross_site` を追加します。テナント設定の更新は全置換なので、GET したボディに追記して PUT してください。

```json
"ui_config": {
  "base_url": "https://auth.example-login.com",
  "signin_page": "/signin",
  "signup_page": "/signup",
  "cross_site": true
}
```

### クライアントごとに切り替える

クライアントの `extension` に `cross_site_authorization_view` を追加します。指定があればテナントの `ui_config.cross_site` より優先されます。一部の RP で試す、または対応の遅れた RP だけ従来の経路に残す、といった移行に使います。

```json
"extension": {
  "cross_site_authorization_view": true
}
```

| テナント `cross_site` | クライアント `cross_site_authorization_view` | 動き |
|---|---|---|
| `false`（既定） | 指定なし | 従来の経路 |
| `false` | `true` | このクライアントだけ別サイト認可画面モード |
| `true` | 指定なし | 別サイト認可画面モード |
| `true` | `false` | このクライアントだけ従来の経路 |

画面の配信先をクライアントごとに変えたい場合は、[バリアント](./02-authorization-view-canary.md) で別サイトの画面を宣言し、RP が認可リクエストにバリアント名を付けます。バリアントが切り替えるのは画面の配信先だけで、別サイト認可画面モードは有効になりません。別サイトの画面に振り分けるクライアントには、`cross_site_authorization_view: true` もあわせて設定してください。

### あわせて確認する設定

| 設定 | 必要なこと |
|---|---|
| `cors_config.allow_origins` | 認可画面のオリジンを含める。画面は view-data などの API を XHR で呼ぶため |
| idp-server の前段（API Gateway / CDN / WAF） | `x-view-binding` ヘッダーを通す。前段が CORS のプリフライトに自分で応答する場合は `Access-Control-Allow-Headers` にも含める。ヘッダーを許可リストで転送する構成で漏れると、認可画面からの呼び出しが 401（ヘッダーが落ちる）か、プリフライトで止まる。有効にする前に済ませる |
| `session_config.cookie_same_site` | `Lax`（既定）または `None`。`Strict` は使えない。認可画面から `/complete` への遷移はクロスサイトなので、`Strict` の `IDP_AUTH_SESSION` は送られず、`/complete` が拒否される |

---

## 自作の認可画面でやること

idp-server 付属の認可画面（app-view）は対応済みです。自作の画面は次の 5 点に対応してください。

1. **URL の fragment の `view_binding` を、idp-server の呼び出しに付ける。** 認可画面は `https://<認可画面>/signin?id=<認可リクエスト ID>&tenant_id=…#view_binding=<値>` のように開かれます。値を認可リクエスト ID ごとに `sessionStorage` に置き、その認可リクエストについての idp-server の呼び出し（view-data、authentication-status、認証ステップ、フェデレーション、`authorize`、`authorize-with-session`、`deny`）すべてに `x-view-binding: <値>` ヘッダーを付けます。フェデレーションのコールバック（`/v1/authorizations/federations/{type}/callback`）は URL に ID を含まないので、直近に受け取った値を付けます。値を受け取ったら、アドレスバーからは消すことを推奨します（`history.replaceState`）。
2. **応答の `auth_proof` を保持する。** 認証ステップ（パスワード、OTP、FIDO2、サインアップなど）とフェデレーションのコールバックの応答に含まれます。どのステップで出るかは認証ポリシー次第なので、応答を横断して拾い、認可リクエスト ID ごとに最新のものを持ちます。同意画面のリロードで失われないよう、`sessionStorage` に置くことを推奨します（認可画面自身のオリジンなのでファーストパーティです）。
3. **`authorize` のボディに載せる。**
   ```json
   POST /{tenant-id}/v1/authorizations/{id}/authorize
   { "auth_proof": "<2 で保持した値>" }
   ```
   使い捨てなので、同じ値は再送できません。
4. **`authorize` の応答で分岐する。** 別サイト認可画面モードでは `redirect_uri` が返らず、`auth_proof` だけが返ります。`redirect_uri` の有無だけで分岐していると、何も起きなくなります。
   - `auth_proof` がある：`/{tenant-id}/v1/authorizations/{id}/complete?auth_proof=<値>` へ**トップレベル遷移**する（`window.location.href` など。fetch では Cookie が保存されません）
   - `redirect_uri` がある：従来どおりそこへ遷移する
5. **`authorize-with-session`（既存の OP セッションで続ける）も同じ分岐にする。** 応答に `auth_proof` が返るので、4 と同じく `/complete` へ遷移します。この認可でサインインが済んだあとは `authorize-with-session` は使えません（400）。`auth_proof` 付きの `authorize` を呼びます。

ブラウザでの入力が一度も無いフロー（`login_hint` を指定し、デバイスのプッシュ承認だけで認証する構成など）では、認証ステップで `auth_proof` は出ません。このときは `auth_proof` を付けずに `authorize` を呼べば、応答で `/complete` 用の `auth_proof` が返ります。

---

## 切り替え方

切り替えは、**画面を先に、設定を後に**行います。

1. idp-server を更新する（宣言しない限り挙動は変わりません）
2. 認可画面を更新する（サーバーが `auth_proof` を返さない間は、従来どおり `redirect_uri` へ遷移します）
3. テナントまたはクライアントで別サイト認可画面モードを有効にする
4. 認証ポリシーの `auth_session_binding_required` を `true` に戻す（別サイト構成を動かすために `false` にしていた場合）

使用中のクライアントの設定を切り替えると、その瞬間に認証の途中だった利用者は一度だけ失敗します（切り替え前に始めた認可には `view_binding` も `auth_proof` も発行されていないため、認可画面からの呼び出しが 401 や 400 になる）。次のどちらかで切り替えてください。

- **別クライアントに乗り換える**：`cross_site_authorization_view: true` の新しいクライアントを作り、RP が `client_id` を切り替える。途中のフローは古いクライアントのまま最後まで進む。古いクライアントで発行したリフレッシュトークンは使えなくなり、同意はクライアントごとなので同意画面が再び出ます
- **メンテナンスで止めて切り替える**

戻すときは設定を戻すだけです（4 で戻した `auth_session_binding_required` も、あわせて `false` に戻します）。

4 を忘れないでください。別サイト認可画面モードでは、認可画面からの呼び出しは `IDP_AUTH_SESSION` の代わりに `view_binding` で照合するので、`false` にしておく必要はありません。`false` のままだと、`view_binding` と `/complete` の `IDP_AUTH_SESSION` の照合がどちらも行われず、ブラウザの束縛が無くなります（`/complete` はこの場合に警告をログに出します）。

---

## 注意点

**`/complete` の照合に失敗すると、テナントのエラー画面へ遷移します。** RP には戻りません。遷移先は `ui_config.base_url` の `/error/` で、`error=invalid_request` と `error_description` が付きます。認可リクエストを始めたのと別のブラウザで開いた、同じ URL を二度開いた、などが原因です。

**`authorize` は 1 回しか呼べません。** 一度 `/complete` 用の `auth_proof` を返した認可に、もう一度 `authorize`（または `authorize-with-session`）を呼ぶと 400 になります。最初に受け取った値で `/complete` へ遷移してください。

**認可画面からの呼び出しは、`view_binding` で始めたブラウザに束縛されます。** 同一サイト構成で `IDP_AUTH_SESSION` が担っていた照合です。認可リクエスト ID を知っていても、値を持たない呼び出しは 401 になります。PAR で同じ認可リクエストを開き直した場合は、最後に開いたブラウザの値だけが有効です。

**ブラウザでの入力が無いフローでは、承認した人が認可を始めた本人かどうかを、デバイス側の確認に任せます。** `login_hint` を指定してデバイスのプッシュ承認だけで認証する構成などでは、`authorize` は `auth_proof` を求めず、束縛は始めたブラウザへのもの（`view_binding` と `/complete` の `IDP_AUTH_SESSION`）だけになります。同一サイト構成でも同じで、idp-server はこのフローでデバイス側の確認を強制しません。この構成を使う場合は、認証ポリシーに [ナンバーマッチング](../../content_06_developer-guide/05-configuration/authn/number-matching.md) を組み込み、認可画面に表示したコードをデバイスで入力させることを推奨します。

**`auth_session_binding_required: false` の認証ポリシーでは、`view_binding` と `/complete` の `IDP_AUTH_SESSION` の照合も行われません。**別サイト認可画面モードでは `false` にする必要はないので、`true` にしてください（切り替え方の 4）。

**`auth_proof` は `/complete` の URL（クエリ文字列）に載ります。** 使い捨てで、`IDP_AUTH_SESSION` の照合もあるため単体では使えませんが、アクセスログやプロキシのログに残ります。ログのマスク対象に `auth_proof` を加えることを推奨します。

**キャッシュ（Redis）が止まっていてもログインは成立します。** `auth_proof` は認証トランザクション（DB）に保存されるためです。ただし OP セッション自体は Redis に保存されるため、停止中は ID Token に `sid` が付かず、SSO とバックチャネルログアウトが効きません。これは同一サイト構成でも同じです。

---

## 関連ドキュメント

- [認可画面のカナリアリリース](./02-authorization-view-canary.md) - バリアントで画面の配信先を出し分ける
- [テナント設定](../../content_06_developer-guide/05-configuration/tenant.md) - `ui_config` のフィールドリファレンス
- [クライアント設定](../../content_06_developer-guide/05-configuration/client.md) - `extension` のフィールドリファレンス
- [Authorization Code Flow 実装ガイド](../../content_06_developer-guide/03-application-plane/02-authorization-flow.md) - 別サイト構成の実装
