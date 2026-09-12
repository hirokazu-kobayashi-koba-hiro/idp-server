# セルフサービス 連絡先の確認・変更

認証済みエンドユーザーが自分の**メールアドレス**または**電話番号**を、確認(検証)または変更するフロー。
対象の値へ確認コードを送り、本人到達性を検証してから確定する。

送信には既存の認証設定をそのまま流用する（Email は [Email認証](./authn/email.md) の
`email-authentication-challenge`、電話番号は [SMS認証](./authn/sms.md) の `sms-authentication-challenge`）。
テナントが追加で書く設定は、変更用スコープと（必要なら）変更ポリシーだけ。

:::tip このページは設定リファレンスです
- **なぜ**識別子移動と属性更新を分けるのか → [連絡先変更ポリシー](../../content_03_concepts/02-identity-management/concept-03-contact-change-policy.md)
- **フローとエンドポイント** → [セルフサービス 連絡先の確認・変更（プロトコル）](../../content_04_protocols/protocol-08-self-service-contact-change.md)
:::

---

## 2つのモード

流用する認証設定には形が2つあり、**どちらも動く**。どちらになるかは設定から判定するので、
モードを指定する設定項目は無い。

| | ローカル生成 | 外部委譲 |
|---|---|---|
| 判定 | `execution.details` に sender の記述がある | `execution.function` が `http_request` / `http_requests`、または sender の記述が無い |
| コード生成 | idp-server | 外部サービス |
| 送信 | idp-server（SMTP / HTTP API / AWS SES など） | 外部サービス |
| 照合 | idp-server（`verification_code` 列と比較） | 外部の検証API |
| idp-server が保持するもの | コードそのもの | 外部の識別子（`transaction_id` 等） |
| 参照する設定 | `details`（sender / settings / templates / retry / expire） | `http_request` / `http_requests` と `*_store` |

### 外部委譲で参照する設定

challenge 側（`{channel}-authentication-challenge`）:

| 項目 | 用途 |
|---|---|
| `execution.http_request` | 外部API設定（単発） |
| `execution.http_requests` | 外部API設定（チェーン。認証してから送るなど） |
| `execution.http_request_store` / `http_requests_store` | レスポンスから何を保持するか |

verify 側（`{channel}-authentication`）:

| 項目 | 用途 |
|---|---|
| `execution.http_request` / `http_requests` | 外部検証API設定 |
| `execution.previous_interaction` | challenge で保持した値の参照キー |

保持した値は検証リクエストのマッピングで `$.interaction.*` から読める。ログイン側と同じ記法。

チェーンは最初の失敗で打ち切り、各結果を `$.execution_http_requests` に積むので、後続の
リクエストと保存マッピングが前の結果を読める。**空のチェーンは失敗扱い**（外部に一度も
聞かずに成功を返さないため）。ログイン側にある条件付きスキップは、認証トランザクションが
無いため評価できず、対応していない。

### 外部委譲での制約

| 項目 | 挙動 |
|---|---|
| `expire_seconds` / `retry_count_limitation` / `resend_cooldown_seconds` | `details` 由来のため既定値（300 / 5 / 60）。実際の有効期限や試行上限は外部サービスが持つ |
| 変更通知（`notify_previous_value`） | **送られない**。文面の置き場が認証設定の `templates` しか無く、委譲設定はそれを持たないため。ログに残してスキップする |

通知の制約は連絡先変更に固有ではなく、テナント単位の通知設定が存在しないことに起因する。

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

### 再送クールダウン

同一ユーザー × 同一操作で、直前の送信から `resend_cooldown_seconds` 以内の再送は `400` で拒否します。
**未設定時は 60 秒**（0 = 無制限にはしません）。

変更側は宛先を呼び出し側が指定できるため、無制限だと SMS の課金と送信者レピュテーションが、
呼び出し側の都合で消費されます。受け取る側は関与を求めていない第三者です。

キーは「ユーザー + 操作」なので、Email 変更のクールダウン中でも電話番号の変更や Email の確認は通ります。
経過判定は DB 側の時刻で行うため、アプリ側の時計のズレで短縮されません。

---

## テナントポリシー: 何を要求するか

`identity_policy_config.contact_change_policy` で、**識別子を動かす変更**と**属性だけの更新**を
別々に規定できます。どちらになるかは `identity_unique_key_type` で決まります。

| `identity_unique_key_type` | email 変更 | phone 変更 |
|---|---|---|
| `EMAIL` / `EMAIL_OR_EXTERNAL_USER_ID` | `identifier_move` | `attribute_only` |
| `PHONE` / `PHONE_OR_EXTERNAL_USER_ID` | `attribute_only` | `identifier_move` |
| `USERNAME` 系 / `EXTERNAL_USER_ID` | `attribute_only` | `attribute_only` |

```json
"identity_policy_config": {
  "identity_unique_key_type": "EMAIL",
  "contact_change_policy": {
    "identifier_move": {
      "allowed": true,
      "authentication_conditions": {
        "any_of": [
          [ { "path": "$.amr", "operation": "contains", "value": "password" } ],
          [ { "path": "$.amr", "operation": "contains", "value": "fido-uaf" } ]
        ]
      },
      "max_auth_age_seconds": 300,
      "notify_previous_value": true,
      "identity_verified_behavior": "DENY"
    },
    "attribute_only": {
      "notify_previous_value": true,
      "identity_verified_behavior": "ALLOW"
    }
  }
}
```

### `authentication_conditions`

「本人が、最近、認証していること」をアクセストークンの認証情報に対する条件で表します。
**認証方式を名指ししません。** パスワードを持たない利用者（passkey のみ、外部 IdP 由来）を
締め出さないためです。

記法は `device_registration_conditions` と同じで、`any_of` はグループの OR、
グループ内は AND です。評価できるパス:

| path | 内容 |
|---|---|
| `$.amr` | 認証方式の配列。**このサーバ独自の値**で RFC 8176 の登録名ではありません（`password` / `email` / `sms` / `fido-uaf` / `fido2` / `external-api`） |
| `$.acr` | 認証コンテキストクラス |
| `$.auth_time` | 認証時刻（epoch 秒） |
| `$.auth_age` | 認証からの経過秒 |

`$.auth_time` を持たないトークンでは `auth_time` / `auth_age` の**キー自体が存在しません**。
その場合 `max_auth_age_seconds` は**満たされない**扱いです（欠落を「たった今」と解釈すると
規則が反転するため）。

未設定なら条件なし＝スコープだけが門番です。

### `identity_verified_behavior`

| 値 | 挙動 |
|---|---|
| `ALLOW` | 身元確認状態に関係なく許可 |
| `DENY` | `IDENTITY_VERIFIED` / `IDENTITY_VERIFICATION_REQUIRED` では拒否 |
| `DOWNGRADE_STATUS` | （未実装） |

`identifier_move` の既定は `DENY` です。身元確認結果ですら `preferred_username` を
動かさない設計（`IdentityVerificationUserUpdater`）と同じ線に揃えています。

### `notify_previous_value`

確定後、**置き換えられた側の値**へ「変更されました」を送ります（既定 `true`）。

確認コードは新しい値に届くので、フローの間、現在の持ち主には何も届きません。
これは変更を**防ぐ**層ではなく、**気づかせる**層です。

文面は送信元の認証設定の `templates` に置きます。キーは
`email_change_notice` / `phone_change_notice`。

```json
"email_change_notice": {
  "subject": "メールアドレスが変更されました",
  "body": "{CHANGED_AT} に {NEW_VALUE_MASKED} へ変更されました。心当たりが無い場合はサポートへご連絡ください。"
}
```

確認コード用のテンプレートとはプレースホルダが異なります。

| プレースホルダ | 使える場所 |
|---|---|
| `{VERIFICATION_CODE}` / `{EXPIRE_SECONDS}` | 確認コード（`email_change` / `email_verify` 等） |
| `{CHANGED_AT}` / `{NEW_VALUE_MASKED}` | 変更通知（`*_notice`） |

新しい値は部分マスクで引用します（`n***@example.com` / `*****5678`）。乗っ取り後は
その受信箱を第三者も読んでいる可能性があるため、本人が「自分のではない」と判別できる分だけを載せます。

送信失敗は**ログに残して握りつぶします**。変更は既に確定しており、通知の不達で成功を
失敗に変えるほうが悪いためです。

:::warning 外部委譲モードでは送られません
文面の置き場が認証設定の `templates` しか無く、委譲設定はそれを持たないためです。通知自体は
認証チャレンジではないので本来は送れるはずで、テナント単位の通知設定ができれば解消します。
:::

### 判定のタイミング

開始時（コード送信前）と確定時の両方で評価します。満たせない要求で開始できてしまうと、
拒否のたびに呼び出し側の選んだ宛先へメッセージが飛ぶためです。確定時にも見るのは、
チャレンジが未消費の間に身元確認状態も認証経過時間も動きうるからです。

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
   - 再送間隔を変えるなら `resend_cooldown_seconds`（未設定は 60 秒）
   - 変更通知を使うなら `templates` に `email_change_notice` / `phone_change_notice`
     （未定義ならデフォルト文面にフォールバックしますが、確認コード用の文面が出るので設定推奨）
2. **変更用スコープ**をテナントの `scopes_supported` とクライアントの `scope` に追加する。
   無いとトークンに載らず、変更エンドポイントが常に `403 insufficient_scope` になる
   (クライアント登録スコープの allow-list フィルタが効くため、fail closed)。

```json
"scopes_supported": ["openid", "profile", "email", "email:change", "phone:change"]
```

3. **変更の重さを決めるなら** `identity_policy_config.contact_change_policy`。
   未設定なら `identifier_move.identity_verified_behavior: DENY` と
   `notify_previous_value: true` だけが効きます

**認証ポリシー（`flow` 設定）の追加は不要。**

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

## 運用: 期限切れチャレンジの掃除

確定したチャレンジは削除されますが、**コードを要求して放置された行は残ります**（利用者がコードを持って
戻ってこないだけなので、消す契機が無い）。有効な宛先と使い捨てコードを保持したままなので、他の
有効期限付きテーブルと同じ一括削除の対象にしています。

```
POST /v1/admin/operations/delete-expired-data   { "max_deletion_number": 10000 }
```

レスポンスの内訳に `contact_verification_challenge` が並びます。

---

## 運用: 「コードが届かない」問い合わせ

発行済みチャレンジは管理APIで参照できる。

```
GET /v1/management/tenants/{tenant-id}/contact-verification-challenges?user_id=...&operation=email_change
GET /v1/management/tenants/{tenant-id}/contact-verification-challenges/{id}
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/contact-verification-challenges
GET /v1/management/organizations/{org-id}/tenants/{tenant-id}/contact-verification-challenges/{id}
```

権限は `idp:contact-verification-challenge:read`。

**まず `target_value`（実際の送信先）を見る。** ユーザーが言っているアドレス／番号と違っていれば、
それが原因。ここはモードによらず同じ。

その次に見るものはモードで変わり、`delivery` がどちらかを示す。

```json
// ローカル生成
{ "delivery": "internal", "target_value": "...", "verification_code": "123456", "attempts": 0, ... }

// 外部委譲
{ "delivery": "external", "target_value": "...", "external_reference": { "transaction_id": "ext-..." }, ... }
```

| `delivery` | コードの在処 |
|---|---|
| `internal` | `verification_code` に入っている |
| `external` | **idp-server は持っていない。** 外部サービスの記録を `external_reference` で引く |

`internal` で `verification_code` を返すのは意図的。チャレンジの消費には**本人のアクセストークン**が
必要なため、コードだけを入手したオペレーターが単独で確定させることはできない。

参照は監査ログに記録される。ただし監査ログには `verification_code` も `external_reference` も
残さない（監査ログは永続で、そこに使い捨ての秘密を残す理由が無いため）。
