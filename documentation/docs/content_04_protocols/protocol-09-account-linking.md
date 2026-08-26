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

### 同一性の検証は2箇所に要る

連携の乗っ取りには向きが2つあり、止まる場所が違います。

| 向き | 結果 | 止まる場所 |
|---|---|---|
| 攻撃者が**被害者の `state`** を再生する | 被害者のアカウントに攻撃者の外部アカウントが付く | `complete`（Bearer と照合） |
| 攻撃者が**自分の `state`** を被害者に踏ませる | **攻撃者のアカウントに被害者の外部アカウントが付く** | `/linking/start` のみ |

2つ目は `complete` では止まりません。`state` を握っているのは攻撃者なので、攻撃者自身の Bearer で確定でき、照合が通ってしまいます。**`/linking/start` で「いま操作している人」と「連携開始時に束縛したユーザー」が一致するかを確認することが、この向きに対する唯一の防壁**です。

`/linking/start` は現在、idp-server のブラウザセッションから操作者を解決します。セッションが無い場合は 401 を返します。

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
| 403 | 操作者が連携セッションのユーザーと一致しない |
| 404 | `state` に対応する連携セッションが無い |
| 409 | 連携セッションが期限切れ、または期待する状態でない / 同じ外部アカウントが既に他のユーザーに紐付いている |

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
| MySQL | DDL はありますが、永続化実装は PostgreSQL のみです |

---

## 関連ドキュメント

- [認可コードフロー](./protocol-01-authorization-code-flow.md) - 連携先IdPとのやり取りのベース
- [クライアント認証](./protocol-06-client-authentication.md) - 連携先IdPへのクライアント認証方式
