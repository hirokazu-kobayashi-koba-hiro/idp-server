# セルフサービス 連絡先の確認・変更

認証済みエンドユーザーが自分の**メールアドレス**または**電話番号**を、確認(検証)または変更するフロー。
対象の値へ確認コードを送り、本人到達性を検証してから確定する。

送信には既存の認証設定をそのまま流用する（Email は [Email認証](./authn/email.md) の
`email-authentication-challenge`、電話番号は [SMS認証](./authn/sms.md) の `sms-authentication-challenge`）。
sender / settings / templates / retry_count_limitation / expire_seconds はそこから読む。

---

## 認証フローの仕組みには乗せていない

このフローは `AuthenticationTransaction` / `AuthenticationInteractor` を**使わない**。専用テーブル
`contact_verification_challenge` に自前で状態を持つ。

理由は、認証インタラクションのエンドポイント
(`POST /{tenant-id}/v1/authorizations|authentications/{id}/{interaction-type}`)が**設計上あえて無認証**
だから。あれは「まだ資格情報を持たない相手」との交渉を駆動するためのもので、公開されている必要がある。
一方こちらは**すでに認証済みのユーザーによる属性変更**で、公開エンドポイントを持つ理由が無い。
乗せると、その公開ドアから属性変更に到達できてしまう。

その結果、以下が構造的に不要・不可能になっている:

- 認証ポリシー(`flow` 設定)が不要 — テナントに追加する設定は送信元の認証設定とスコープだけ
- 無認証エンドポイントからの駆動が不可能 — そもそも生えない
- 所有者チェックの書き忘れが不可能 — 後述のとおり SQL の述語

---

## 確認と変更が別エンドポイントである理由

| | 確認 | 変更 |
|---|---|---|
| 変わるもの | `*_verified` のみ | 値 + `*_verified`（+ 対応する identity policy なら `preferred_username`） |
| IdP 内部の権限 | 実質なし(クレーム値のみ) | **ログイン識別子の移動** |
| 必要スコープ | `openid` | **`email:change` / `phone:change`** |
| 送信先 | **現在値に固定**(リクエストから受け取らない) | リクエストの `new_value` |

1本にまとめると、**コードを送る前に認可を判定できない**(意図はサーバが `new_value` と現在値を
比較して初めて分かる)。「再確認に十分軽く、識別子移動に十分重い」スコープは存在しないため分割している。
`email:change` を `openid` で代替すると、通常の OIDC トークン全て — 第三者クライアントに発行したものを
含む — に識別子の乗っ取り能力を与えることになる。

---

## エンドポイント

`{channel}` は `email` または `phone`。`{channel}:change` はそれぞれ `email:change` / `phone:change`。

| メソッド・パス | ボディ | スコープ | 説明 |
|---|---|---|---|
| `POST /{tenant-id}/v1/me/{channel}/verification` | なし | `openid` | 現在値へコード送信し `{ "id": "<challenge>" }` を返す |
| `POST /{tenant-id}/v1/me/{channel}/verification/{id}/verify` | `{ "verification_code": "..." }` | `openid` | 検証 + `*_verified: true` |
| `POST /{tenant-id}/v1/me/{channel}/change` | `{ "new_value": "..." }` | `{channel}:change` | 新しい値へコード送信し `{ "id": "<challenge>" }` を返す |
| `POST /{tenant-id}/v1/me/{channel}/change/{id}/verify` | `{ "verification_code": "..." }` | `{channel}:change` | 検証 + 値の確定 |

- 確認側は `new_value` を**受け取らない**(送っても無視)。送信先が呼び出し側の指定で動かないので、この半分は宛先を差し替えられない
- 確認側は、アカウントにその channel の値が無ければ `400`
- 変更側で `new_value` に現在値を指定すると `400`(確認エンドポイントを使う)
- `new_value` は送信前に形式・型検証する。不正なら `400` で、コードは送られない
  - `email`: `local@domain.tld` 相当・最大255文字
  - `phone`: 先頭が `+` または数字・以降は数字と ` ()-` ・最大32文字。E.164 を推奨するが、
    国内表記も受け付ける（テナントごとの表記揺れを弾かないため、意図的に緩い）
  - 非文字列(数値・オブジェクト・配列・null・真偽値)はいずれも拒否
- スコープは**開始時と確定時の両方**で検証する

---

## 所有権と操作種別の扱い

チャレンジの取得は所有者を**述語**に含める:

```sql
WHERE id = ? AND tenant_id = ? AND user_id = ? FOR UPDATE
```

他人のチャレンジは「見つかって拒否」ではなく**存在しない**(`404`)。ID とコードの両方を知っていても駆動できない。

操作種別(`email_verify` / `email_change` / `phone_verify` / `phone_change`)は**チャレンジ行に永続化**され、
必要スコープも channel もそこから導く。URL でスコープを決めて行で挙動を決める、という食い違いが起きない。
変更チャレンジを確認エンドポイントへ投げても `404` になる。

確定時は該当列の**部分更新**を行う。全カラムを書き戻すと、呼び出し側が保持する古いユーザー像で
`status` 等を巻き戻してしまうため。

---

## 必要な設定

1. **送信元の認証設定**
   - Email: [Email認証](./authn/email.md) の `email` config。`templates` に `email_verify` /
     `email_change` を追加推奨(未定義ならデフォルト文面にフォールバック)
   - 電話番号: [SMS認証](./authn/sms.md) の `sms` config。`templates` に `phone_verify` /
     `phone_change` を追加推奨(同上)
2. **変更用スコープ**をテナントの `scopes_supported` とクライアントの `scope` に追加する。
   無いとトークンに載らず、変更エンドポイントが常に `403 insufficient_scope` になる
   (クライアント登録スコープの allow-list フィルタが効くため、fail closed)。

```json
"scopes_supported": ["openid", "profile", "email", "email:change", "phone:change"]
```

**認証ポリシーの追加は不要。**

---

## identity policy による一意性の挙動(変更時のみ)

一意性は identity policy 由来の `preferred_username` に対して働く。確認側は現在値のままなので無関係。

| `identity_unique_key_type` | 挙動 |
|---|---|
| `EMAIL` | メール = ログイン識別子。**既存ユーザーと同じメールへの変更は拒否**(`400`)。変更後は新メールでログイン(旧メールは不可) |
| `PHONE` | 電話番号 = ログイン識別子。上と同様 |
| その他 | その channel の値は属性のみ。**重複する値への変更も許可** |

---

## 監査

操作ごとに別種別のイベントを発行する(いずれもログイン時の `email_verification_*` とは別):

| 操作 | イベント |
|---|---|
| Email 確認 | `email_verify_request_success` / `_failure`、`email_verify_success` / `_failure` |
| Email 変更 | `email_change_request_success` / `_failure`、`email_change_success` / `_failure` |
| 電話番号 確認 | `phone_verify_request_success` / `_failure`、`phone_verify_success` / `_failure` |
| 電話番号 変更 | `phone_change_request_success` / `_failure`、`phone_change_success` / `_failure` |

永続化して照会するにはテナントに `security_event_log_config.persistence_enabled: true` が必要。

---

## 運用: 「コードが届かない」問い合わせ

発行済みチャレンジは管理APIで参照できる。

```
GET /v1/management/tenants/{tenant-id}/contact-verification-challenges?user_id=...&operation=email_change
GET /v1/management/tenants/{tenant-id}/contact-verification-challenges/{id}
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/contact-verification-challenges
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/contact-verification-challenges/{id}
```

権限は `idp:contact-verification-challenge:read`。返るのは `target_value`(実際の送信先)、
`verification_code`、`attempts`、`expires_at`、`expired`。

まず `target_value` を見る。ユーザーが言っているアドレス／番号と違っていれば、それが原因。

`verification_code` を返すのは意図的。チャレンジの消費には**本人のアクセストークン**が必要なため、
コードだけを入手したオペレーターが単独で確定させることはできない。参照は監査ログに記録される。
