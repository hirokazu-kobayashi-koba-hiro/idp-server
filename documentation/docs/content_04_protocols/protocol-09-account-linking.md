# 外部IdPアカウント連携

## 概要

認証済みのユーザーが、自分のアカウントに**外部IdPのアカウントを追加で紐付ける**ための idp-server 独自のプロトコル拡張です。紐付けの過程で外部IdPが発行したアクセストークンとリフレッシュトークンを暗号化して保管し、後から外部APIの呼び出しに使えるようにします。

既存の Federation（外部IdPでログインする機能）とは目的が異なります。

| | Federation | アカウント連携 |
|---|---|---|
| 目的 | 外部IdPで**ログインする** | 外部IdPのトークンを**保管する** |
| 起点 | 未認証の認可リクエスト | 認証済みユーザーの操作 |
| 紐付け数 | 1ユーザー1件 | provider ごとに複数可（`account_alias` で識別） |
| トークン | 平文 JSONB | アプリ層で AES-GCM 暗号化 |

両者は独立した系統です。連携を追加しても Federation の動作は変わりません。

---

## シーケンス

```
RPアプリ                       idp-server                    外部IdP
 │                                │                              │
 ├─ POST /v1/me/linked-external-accounts/link/{provider} ───────▶│
 │   Authorization: Bearer         │  Bearer から user を確定      │
 │                                │  redirect_uri を allow-list 照合
 │◀─ 201 { start_url, state }      │  連携セッションを保存 (pending)
 │                                │                              │
 ├─ location.href = start_url ────▶                              │
 │                                │  操作者 == セッションの user か
 │                                │  を照合 (authorized)          │
 │◀─ 302 外部IdPの認可URL ──────────┤                              │
 ├────────────────────────────────┼─────────────────────────────▶│
 │                                │        ユーザーがログイン・同意 │
 │                                │◀─ 302 /v1/linking/callback ───┤
 │                                │                              │
 │                                ├─ token 交換 (code_verifier) ─▶│
 │                                │◀─ access_token / refresh_token│
 │                                ├─ userinfo ──────────────────▶│
 │                                │  暗号化してセッションに預ける   │
 │                                │  (parked)                    │
 │◀─ 302 return_to?linking=done&state=                            │
 │                                │                              │
 ├─ POST /v1/me/linked-external-accounts/complete ──────────────▶│
 │   Authorization: Bearer { state }  Bearer と セッションの user  │
 │                                │  を照合して確定 (consumed)     │
 │◀─ 201 { alias, provider, ... }  │                              │
```

認可コードは idp-server の中で交換されて完結し、**RPには渡りません**。

---

## エンドポイント体系

### RPが呼ぶもの（Bearer 必須）

| エンドポイント | 用途 |
|---|---|
| `POST /{tenant-id}/v1/me/linked-external-accounts/link/{provider}` | 連携の開始。`{ redirect_uri, scope }` を受け取り `{ start_url, state, expires_in }` を返す |
| `POST /{tenant-id}/v1/me/linked-external-accounts/complete` | 連携の確定。`{ state }` を受け取る |
| `GET /{tenant-id}/v1/me/linked-external-accounts` | 連携一覧 |

`{provider}` には連携先の設定名（`sso_provider`）を指定します。

`redirect_uri` は連携完了後にブラウザを戻す先で、開始時にクライアント設定の allow-list と完全一致で照合されます。

### ブラウザ遷移（Bearer なし）

| エンドポイント | 認証 | 用途 |
|---|---|---|
| `GET /{tenant-id}/v1/linking/start?state=` | ブラウザセッション | 操作者を検証し、外部IdPの認可URLへ 302 |
| `GET /{tenant-id}/v1/linking/callback/{provider}?code&state` | `state` | 認可コードを交換し、`redirect_uri` へ 302 |

この2本が `/me` の外にあるのは、どちらもブラウザのトップレベル遷移で Bearer を運べないためです。`/me` は `ProtectedResourceApiFilter` によって Bearer 前提で保護されている名前空間なので、そこに未認証エンドポイントを混ぜると、実装者が「フィルタが通っているはず」と誤認する余地が生まれます。

一覧のレスポンスにトークンは含まれません。返るのは `alias` / `provider` / `federated_username` / `scope` / 各有効期限 / `created_at` / `updated_at` です。

---

## 確定を分ける理由

外部IdPからのコールバックはブラウザのリダイレクトで届くため、`Authorization` ヘッダを持ちません。つまりコールバック自身には「これが誰の連携か」を確かめる手段がありません。

そこでコールバックでは連携を確定させず、交換したトークンを暗号化して**連携セッションに預けるだけ**にします。確定するのは、Bearer を持つ `complete` だけです。

```
pending ──[start] 操作者を照合──▶ authorized ──[callback] コード交換──▶ parked ──[complete] Bearer 照合──▶ consumed
```

各遷移は条件付き UPDATE による単回消費です。コールバックでは**コードを交換する前に**状態を取りにいき、競合に負けた側が認可コードに触れないようにしています。認可コードは単回のため、二重に使うと外部IdP側で認可ごと失効させられる場合があります。

### 同一性の検証は3箇所に要る

連携の乗っ取りには向きが3つあり、止まる場所がそれぞれ違います。

| # | 向き | 結果 | 止まる場所 |
|---|---|---|---|
| 1 | 攻撃者が**被害者の `state`** を再生する | 被害者のアカウントに攻撃者の外部アカウントが付く | `complete`（Bearer と照合） |
| 2 | 攻撃者が**自分の `start_url`** を被害者に踏ませる | 攻撃者のアカウントに被害者の外部アカウントが付く | `/linking/start`（操作者と照合） |
| 3 | 攻撃者が**自分で `/linking/start` を通り、外部IdPの認可URLだけ**を被害者に渡す | 同上 | **コールバック（ブラウザ束縛と照合）** |

1 は `state` を握っていても Bearer が一致しないため `complete` で落ちます。

2 は `complete` では落ちません。`state` を握っているのは攻撃者なので、攻撃者自身の Bearer で確定できてしまいます。`/linking/start` で操作者を照合することで、被害者が外部IdPに到達する前に止めます。

3 が厄介です。**攻撃者は `/linking/start` を自分で通過できます**（攻撃者こそが束縛されたユーザーなので、操作者照合は正常に通ります）。そこで返る `302` の `Location` には、外部IdPの認可URLが `state` も `code_challenge` も込みで完成した形で載っています。それだけを被害者に渡せば、被害者は `/linking/start` に一切触れずに外部IdPへ行き、コールバックだけが被害者のブラウザで発火します。**操作者照合は被害者に対して一度も評価されません。**

これを止めるのがブラウザ束縛です。`/linking/start` は乱数の秘密値を発行し、そのハッシュだけをセッションに保存して、値そのものは `IDP_LINK_BINDING` Cookie でブラウザに渡します。コールバックはこの Cookie を要求し、**認可コードを交換する前に**照合します。被害者のブラウザはこの Cookie を持たないため、被害者の認可コードは交換されません。

:::info Cookie の `SameSite` はテナント設定に従いません
コールバックは外部IdPからのクロスサイトのトップレベル GET です。`SameSite=Lax` はこの遷移をちょうど許可しますが、`Strict` だと Cookie が送られず連携が必ず失敗します。そのため `IDP_LINK_BINDING` は `Lax` 固定で、テナントの `cookie_same_site` には従いません。
:::

`/linking/start` は idp-server のブラウザセッションから操作者を解決します。セッションが無い場合は 401 を返します。

### RP 側の前提: トップレベル遷移で開くこと

RP と idp-server が別ドメインでも連携は成立します。Cookie が要る2箇所はどちらもトップレベル遷移で、`SameSite=Lax` はまさにその経路を許可するためです。`SameSite` が制限するのは Cookie の送信であって保存ではないので、クロスサイトの遷移先で `Set-Cookie` を受け取ること自体は妨げられません。

| 局面 | サイト関係 | 種類 | 結果 |
|---|---|---|---|
| `/linking/start` で発行 | RP → idp-server | トップレベル遷移 | 保存される（遷移先が first-party になる）|
| コールバックで送信 | 外部IdP → idp-server | トップレベル GET | 送られる |

:::warning iframe の中では成立しません
iframe に埋め込むと third-party 文脈になり、Safari の ITP と Chrome のサードパーティ Cookie 制限によって**保存の時点で**落ちます。`location.href` などで**トップレベル遷移**させてください。別ウィンドウ（ポップアップ）は独立したトップレベル閲覧コンテキストなので問題ありません。
:::

外部IdPが `response_mode=form_post` で返す場合も成立しません。クロスサイトの POST には `Lax` の Cookie が送られないためです。コールバックは GET のみを受け付けます。

---

## 設定

### 連携先IdP

`federation-configurations`（`type: oidc`）に登録します。`redirect_uri` には連携用コールバックを指定します。

```json
{
  "type": "oidc",
  "sso_provider": "example-idp",
  "enabled": true,
  "payload": {
    "provider": "standard",
    "issuer_name": "example-idp",
    "authorization_endpoint": "https://idp.example.com/authorize",
    "token_endpoint": "https://idp.example.com/token",
    "userinfo_endpoint": "https://idp.example.com/userinfo",
    "client_id": "...",
    "client_secret": "...",
    "client_authentication_type": "client_secret_post",
    "redirect_uri": "https://api.example.com/{tenant-id}/v1/linking/callback/example-idp",
    "scopes_supported": ["openid", "email", "offline_access"],
    "userinfo_mapping_rules": [
      { "from": "$.http_request.response_body.sub", "to": "external_user_id" },
      { "from": "$.http_request.response_body.email", "to": "email" }
    ],
    "refresh_token_expires_in": 86400
  }
}
```

`external_user_id` へのマッピングは必須です。この値が外部アカウントの識別子として `UNIQUE (tenant_id, provider, federated_user_id)` に使われます。

`scopes_supported` は `link` のリクエストで `scope` を省略したときの既定値です。

`duplicate_link_policy` は、同じ外部アカウントが既にテナント内の別ユーザーに紐付いている場合の挙動です。

| 値 | 挙動 |
|---|---|
| `reject`（既定） | 409 を返す |
| `allow` | 複数ユーザーがそれぞれ自分の連携として保持できる |

**データベース側では一意制約をかけていません。** 保管している外部アカウント識別子は識別子であって identity ではなく（この値で認証する経路はありません）、一律に禁止すると共用の法人アカウントのような正当なケースを塞ぐうえ、先に連携した側が本来の持ち主を締め出せてしまうためです。ログイン用の Federation は `idp_user` 側に一意制約を持っており、そちらは「1つの外部 identity が2人に解決されない」ことを担保する必要があるので事情が異なります。

:::warning ログイン用の設定とは分けてください
連携はAPIアクセス用のスコープ（例: `drive.readonly`）を要求し、コールバック先も異なります。ログイン用の設定を共用すると、ログインの同意画面にAPIスコープが現れます。
:::

### クライアント

連携完了後の戻り先を `extension.linking_return_uris` に登録します。

```json
{
  "extension": {
    "linking_return_uris": ["https://rp.example.com/linking/callback"]
  }
}
```

認可の `redirect_uris` とは分けています。連携後の戻り先は通常アプリの設定画面であり、認可コードを受け取るURLではありません。流用すると、本来コールバックでないURLに認可コードを飛ばせるようになります。

### 暗号化

トークンは `ENCRYPTION_KEY` から導出した鍵で AES-GCM 暗号化して保管します（`oauth_token` と同じ方式）。`encryption_key_id` 列は将来の鍵ローテーションに備えたもので、現在は常に `default` です。

---

## エラー

レスポンスは `{ "error": "invalid_request", "error_description": "..." }` の形です。

| ステータス | 発生条件 |
|---|---|
| 400 | `redirect_uri` が未指定、または allow-list に無い / 外部IdP側でトークン交換・userinfo に失敗 |
| 401 | `/linking/start` にブラウザセッションが無い |
| 403 | 操作者が連携セッションのユーザーと一致しない / コールバックがブラウザ束縛を提示できない |
| 404 | `state` に対応する連携セッションが無い |
| 409 | 連携セッションが期限切れ、または期待する状態でない / 同じ外部アカウントが既に他のユーザーに紐付いている（`duplicate_link_policy: reject` のとき） |

409 のうち重複連携は、誰に紐付いているかを返しません。返すと外部アカウントの所有者を列挙する手がかりになるためです。

同じユーザーが同じ外部アカウントをもう一度連携した場合はエラーになりません。既存の連携が更新され、`account_alias` は保たれます。

---

## 未対応の機能

| 項目 | 状態 |
|---|---|
| 連携の解除 | 未実装 |
| 保管トークンの取得API | 未実装 |
| リフレッシュ | 未実装 |
| 確定されなかった連携（`parked`）の削除 | 未実装。期限切れ後も暗号化トークンが残ります |
| ID Token の検証 | **行いません。** ユーザーの識別は userinfo エンドポイントのレスポンスに依存します |
| step-up 認証 | 未実装。`/linking/start` はブラウザセッションが無ければ 401 を返すだけです |
| ネイティブアプリ | 上記のため未対応。アプリ内ブラウザに idp-server のセッションが無いと `/linking/start` で 401 になります |
| 外部IdPの `response_mode=form_post` | 未対応。コールバックは GET のみです |
| MySQL | DDL はありますが、永続化実装は PostgreSQL のみです |

---

## 関連ドキュメント

- [認可コードフロー](./protocol-01-authorization-code-flow.md) - 連携先IdPとのやり取りのベース
- [クライアント認証](./protocol-06-client-authentication.md) - 連携先IdPへのクライアント認証方式
