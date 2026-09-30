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
| 認証したブラウザの識別 | 認証ステップの応答で渡す使い捨ての値 `auth_proof` |
| OP セッションの保存 | 最後に idp-server を**トップレベル遷移**で経由させ、そこで保存する（`/complete`） |

---

## 仕組み

XHR はページがその場に居たまま裏で呼ぶので、アドレスバーは動きません。トップレベル遷移はタブ自体が移動します。idp-server の Cookie がファーストパーティとして扱われるのは、アドレスバーが idp-server を指しているときだけです。

| # | アドレスバー | やっていること |
|---|---|---|
| 1 | RP | ログインボタンで認可リクエストへ遷移させる（今までどおり） |
| 2 | idp-server `/v1/authorizations` | `IDP_AUTH_SESSION` を保存し、認可画面へ 302 |
| 3 | 認可画面 | 認証と `authorize` を XHR で呼ぶ。認証ステップの応答で `auth_proof` ① を受け取り、`authorize` に付ける。`authorize` は code を返さず、`/complete` 用の `auth_proof` ② を返す |
| 4 | idp-server `/v1/authorizations/{id}/complete?auth_proof=②` | 認可画面が遷移させる。`IDP_AUTH_SESSION` と ② を照合し、OP セッションの Cookie を保存して RP へ 302 |
| 5 | RP | 今までどおりのコールバック（code と state） |

**RP の実装は変わりません。** 変わるのは認可画面と idp-server です。

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

画面の配信先をクライアントごとに変えたい場合は、[バリアント](./02-authorization-view-canary.md) で別サイトの画面を宣言し、RP が認可リクエストにバリアント名を付けます。

### あわせて確認する設定

| 設定 | 必要なこと |
|---|---|
| `cors_config.allow_origins` | 認可画面のオリジンを含める。画面は view-data などの API を XHR で呼ぶため |
| `session_config.cookie_same_site` | `Lax`（既定）または `None`。`Strict` は使えない。認可画面から `/complete` への遷移はクロスサイトなので、`Strict` の `IDP_AUTH_SESSION` は送られず、`/complete` が拒否される |

---

## 自作の認可画面でやること

idp-server 付属の認可画面（app-view）は対応済みです。自作の画面は次の 4 点に対応してください。

1. **応答の `auth_proof` を保持する。** 認証ステップ（パスワード、OTP、FIDO2、サインアップなど）とフェデレーションのコールバックの応答に含まれます。どのステップで出るかは認証ポリシー次第なので、応答を横断して拾い、認可リクエスト ID ごとに最新のものを持ちます。同意画面のリロードで失われないよう、`sessionStorage` に置くことを推奨します（認可画面自身のオリジンなのでファーストパーティです）。
2. **`authorize` のボディに載せる。**
   ```json
   POST /{tenant-id}/v1/authorizations/{id}/authorize
   { "auth_proof": "<1 で保持した値>" }
   ```
   使い捨てなので、同じ値は再送できません。
3. **`authorize` の応答で分岐する。** 別サイト認可画面モードでは `redirect_uri` が返らず、`auth_proof` だけが返ります。`redirect_uri` の有無だけで分岐していると、何も起きなくなります。
   - `auth_proof` がある：`/{tenant-id}/v1/authorizations/{id}/complete?auth_proof=<値>` へ**トップレベル遷移**する（`window.location.href` など。fetch では Cookie が保存されません）
   - `redirect_uri` がある：従来どおりそこへ遷移する
4. **`authorize-with-session`（既存の OP セッションで続ける）も同じ分岐にする。** 応答に `auth_proof` が返るので、3 と同じく `/complete` へ遷移します。

ブラウザでの入力が一度も無いフロー（`login_hint` を指定し、デバイスのプッシュ承認だけで認証する構成など）では、認証ステップで `auth_proof` は出ません。このときは `auth_proof` を付けずに `authorize` を呼べば、応答で `/complete` 用の `auth_proof` が返ります。

---

## 切り替え方

切り替えは、**画面を先に、設定を後に**行います。

1. idp-server を更新する（宣言しない限り挙動は変わりません）
2. 認可画面を更新する（サーバーが `auth_proof` を返さない間は、従来どおり `redirect_uri` へ遷移します）
3. テナントまたはクライアントで別サイト認可画面モードを有効にする
4. 認証ポリシーの `auth_session_binding_required` を `true` に戻す（別サイト構成を動かすために `false` にしていた場合）

使用中のクライアントの設定を切り替えると、その瞬間に認証の途中だった利用者は一度だけ失敗します（切り替え前に認証を終えていると `auth_proof` が発行されていないため、`authorize` が 400 を返す）。次のどちらかで切り替えてください。

- **別クライアントに乗り換える**：`cross_site_authorization_view: true` の新しいクライアントを作り、RP が `client_id` を切り替える。途中のフローは古いクライアントのまま最後まで進む。古いクライアントで発行したリフレッシュトークンは使えなくなり、同意はクライアントごとなので同意画面が再び出ます
- **メンテナンスで止めて切り替える**

戻すときは設定を戻すだけです（4 で戻した `auth_session_binding_required` も、あわせて `false` に戻します）。

4 を忘れないでください。別サイト認可画面モードでは、認証ステップと `authorize` は `IDP_AUTH_SESSION` を見ないので、`false` にしておく必要はありません。一方で `/complete` は `false` を尊重して照合を行わないため、デバイスだけで認証するフローと既存の OP セッションで続けるフローでは、ブラウザの束縛が無くなります（`/complete` はこの場合に警告をログに出します）。

---

## 注意点

**`/complete` の照合に失敗すると、テナントのエラー画面へ遷移します。** RP には戻りません。遷移先は `ui_config.base_url` の `/error/` で、`error=invalid_request` と `error_description` が付きます。認可リクエストを始めたのと別のブラウザで開いた、同じ URL を二度開いた、などが原因です。

**`authorize` は 1 回しか呼べません。** 一度 `/complete` 用の `auth_proof` を返した認可に、もう一度 `authorize`（または `authorize-with-session`）を呼ぶと 400 になります。最初に受け取った値で `/complete` へ遷移してください。

**認証ステップは、認可リクエストを始めたブラウザに束縛されません。** 認証ステップは XHR なので `IDP_AUTH_SESSION` が届かず、始めたブラウザの照合は `/complete` だけで行います。認可リクエスト ID が第三者に知られた場合の保護は、同一サイト構成より弱くなります。

**`auth_session_binding_required: false` の認証ポリシーでは、`/complete` の `IDP_AUTH_SESSION` の照合も行われません。** デバイスだけで認証するフローと既存の OP セッションで続けるフローでは、この照合が唯一のブラウザの束縛です。別サイト認可画面モードでは `false` にする必要はないので、`true` にしてください（切り替え方の 4）。

**`auth_proof` は `/complete` の URL（クエリ文字列）に載ります。** 使い捨てで、`IDP_AUTH_SESSION` の照合もあるため単体では使えませんが、アクセスログやプロキシのログに残ります。ログのマスク対象に `auth_proof` を加えることを推奨します。

**deny（キャンセル）はブラウザに束縛されません。** 認証前のキャンセルにも使うため `auth_proof` を求められず、`IDP_AUTH_SESSION` も XHR には届きません。認可リクエスト ID を知っていれば取り消せます（code や OP セッションは出ません）。

認証ステップと deny の束縛は [#1913](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1913) で扱います。

**キャッシュ（Redis）が止まっていてもログインは成立します。** `auth_proof` は認証トランザクション（DB）に保存されるためです。ただし OP セッション自体は Redis に保存されるため、停止中は ID Token に `sid` が付かず、SSO とバックチャネルログアウトが効きません。これは同一サイト構成でも同じです。

---

## 関連ドキュメント

- [認可画面のカナリアリリース](./02-authorization-view-canary.md) - バリアントで画面の配信先を出し分ける
- [テナント設定](../../content_06_developer-guide/05-configuration/tenant.md) - `ui_config` のフィールドリファレンス
- [クライアント設定](../../content_06_developer-guide/05-configuration/client.md) - `extension` のフィールドリファレンス
- [Authorization Code Flow 実装ガイド](../../content_06_developer-guide/03-application-plane/02-authorization-flow.md) - 別サイト構成の実装
