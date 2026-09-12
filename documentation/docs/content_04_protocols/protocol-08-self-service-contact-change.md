# セルフサービス 連絡先の確認・変更

## 概要

認証済みのエンドユーザーが、自分のメールアドレスまたは電話番号を**確認**または**変更**するための、idp-server 独自のフローです。

対象の値へワンタイムコードを送り、到達性を証明させてから確定します。標準仕様には対応するものがありません（OIDC は連絡先の変更手続きを定義していない）。

**認証フローの仕組みには乗っていません。** `AuthenticationTransaction` / `AuthenticationInteractor` を使わず、専用テーブル `contact_verification_challenge` に自前で状態を持ちます。理由は[後述](#なぜ認証インタラクションに乗せないか)。

---

## シーケンス

変更（`change`）の場合。確認（`verification`）は宛先が現在値に固定される点だけが違います。

```
クライアント                      idp-server              新しい値      旧い値
 │                                   │                      │            │
 ├─ POST /v1/me/email/change ───────▶│                      │            │
 │    Bearer <token>                 │                      │            │
 │    { new_value }                  │                      │            │
 │                                   │                      │            │
 │                         ┌─────────┴─────────┐            │            │
 │                         │ 1. スコープ検査     │            │            │
 │                         │ 2. ポリシー判定     │            │            │
 │                         │    (amr / auth_age │            │            │
 │                         │     / 身元確認状態) │            │            │
 │                         │ 3. new_value 検証  │            │            │
 │                         │ 4. 再送クールダウン │            │            │
 │                         └─────────┬─────────┘            │            │
 │                                   ├─── コード送信 ───────▶│            │
 │◀─ 200 { id } ─────────────────────┤                      │            │
 │                                   │   challenge を保存     │            │
 │                                   │                      │            │
 │  （利用者がコードを受け取る）          │                      │            │
 │                                   │                      │            │
 ├─ POST /v1/me/email/change/{id}/verify ──▶                │            │
 │    { verification_code }          │                      │            │
 │                                   │                      │            │
 │                         ┌─────────┴─────────┐            │            │
 │                         │ 1. スコープ検査     │            │            │
 │                         │ 2. ポリシー再判定   │            │            │
 │                         │ 3. 所有者つき取得   │            │            │
 │                         │    (FOR UPDATE)   │            │            │
 │                         │ 4. コード照合       │            │            │
 │                         │ 5. 一意性検査       │            │            │
 │                         │ 6. 部分更新        │            │            │
 │                         └─────────┬─────────┘            │            │
 │                                   ├─── 変更通知 ──────────────────────▶│
 │◀─ 200 ────────────────────────────┤                      │            │
```

ステップ 2 が開始・確定の**両方**にあるのは、チャレンジが未消費の間に身元確認状態もトークンの認証経過時間も動きうるためです。スコープを両方で見ているのと同じ理由です。

---

## エンドポイント体系

`{channel}` は `email` / `phone`。

| メソッド・パス | ボディ | スコープ | 送信先 |
|---|---|---|---|
| `POST /{tenant-id}/v1/me/{channel}/verification` | なし | `openid` | アカウントの現在値 |
| `POST /{tenant-id}/v1/me/{channel}/verification/{id}/verify` | `{verification_code}` | `openid` | — |
| `POST /{tenant-id}/v1/me/{channel}/change` | `{new_value}` | `{channel}:change` | リクエスト指定 |
| `POST /{tenant-id}/v1/me/{channel}/change/{id}/verify` | `{verification_code}` | `{channel}:change` | — |

### 2 系統に分かれている理由

確認と変更では、確定したときに動くものが違います。

| | 確認 | 変更 |
|---|---|---|
| 変わるもの | `*_verified` のみ | 値 + `*_verified`（+ 該当ポリシーなら `preferred_username`） |
| IdP 内部の権限 | 実質なし | **ログイン識別子の移動** |
| 必要スコープ | `openid` | `{channel}:change` |

1 本にまとめると、サーバが `new_value` と現在値を比較して初めて意図が分かるため、**コードを送る前に認可を判定できません**。「再確認に十分軽く、識別子移動に十分重い」スコープは存在しないので分けています。

確認側は**リクエストボディを受け取りません**（送っても無視）。宛先が呼び出し側の指定で動かないので、この半分は構造的に宛先を差し替えられません。

---

## なぜ認証インタラクションに乗せないか

認証インタラクションのエンドポイント

```
POST /{tenant-id}/v1/authorizations|authentications/{id}/{interaction-type}
```

は**設計上あえて無認証**です。まだ資格情報を持たない相手との交渉を駆動するためで、公開されている必要があります。

一方このフローは**すでに認証済みのユーザーによる属性変更**で、公開エンドポイントを持つ理由がありません。乗せると、その公開ドアから属性変更に到達できてしまいます。

自前のテーブルに状態を持つことで、以下が構造的に不要・不可能になります。

- 認証ポリシー（`flow` 設定）が不要
- 無認証エンドポイントからの駆動が不可能（そもそも生えない）
- 所有者チェックの書き忘れが不可能（SQL の述語にしている）

---

## チャレンジの扱い

### 所有権は述語

```sql
SELECT ... FROM contact_verification_challenge
WHERE id = ? AND tenant_id = ? AND user_id = ? FOR UPDATE
```

他人のチャレンジは「見つかって拒否」ではなく**存在しません**（`404`）。ID とコードの両方を知っていても駆動できません。`FOR UPDATE` により、同時に 2 つの確定が同じコードを消費することもありません。

### 操作種別は行に永続化する

`email_verify` / `email_change` / `phone_verify` / `phone_change` を発行時に確定し、必要スコープもチャネルもそこから導きます。URL でスコープを決めて行で挙動を決める、という食い違いが起きません。

電話のチャレンジを email エンドポイントへ投げても `404` です。

### コードは行に置くとは限らない

`contact_verification_challenge` は、ローカル生成なら `verification_code` を、委譲なら
`external_reference` を持ちます。どちらか一方だけが入ります。

管理APIは `delivery` でどちらかを示し、`internal` なら `verification_code`、`external` なら
`external_reference` を返します。監査ログにはどちらも残しません。

### 確定は部分更新

確定時に書くのは該当する列だけです。全カラムを書き戻すと、呼び出し側が保持する古いユーザー像で `status` 等を巻き戻してしまいます。

| 操作 | 書く列 |
|---|---|
| `*_verify` | `*_verified` |
| `*_change` | 値 + `*_verified` + `preferred_username` |

---

## コードを誰が持つか

流用する認証設定には形が2つあり、どちらでも動きます。**モードを指定する設定項目はありません**
（`execution` に sender の記述があるかで判定する）。

```
ローカル生成                              外部委譲
  idp がコードを生成                       外部サービスがコードを生成
  idp が送信                               外部サービスが送信
  idp が照合（verification_code 列）        外部の検証APIが照合
  idp が持つ: コード                        idp が持つ: transaction_id 等の識別子
```

委譲の場合、確定時の照合はこう流れます。

```
POST /v1/me/email/change/{id}/verify  { verification_code }
  │
  ├─ チャレンジ行から external_reference を読む
  │
  └─ 外部の検証API へ
        body: { verification_code, transaction_id }
              ↑ リクエスト由来        ↑ $.interaction.* から（保存済み）
        200 → 確定へ / それ以外 → 試行回数を加算して 400
```

外部API は単発（`http_request`）とチェーン（`http_requests`）の両方に対応します。チェーンは
最初の失敗で打ち切り、各結果を `$.execution_http_requests` に積むので、後続のリクエストと
保存マッピングが前の結果を読めます。

---

## テナントが決められること

変更の重さは `identity_policy_config.contact_change_policy` で規定します。**識別子を動かす変更**と**属性だけの更新**を別々に設定でき、どちらになるかは `identity_unique_key_type` が決めます。

要求する認証は**方式を名指しせず** `amr` / `acr` / `auth_age` に対する条件として書きます。パスワードを持たない利用者（passkey のみ、外部 IdP 由来）を締め出さないためです。

詳細は [セルフサービス 連絡先の確認・変更（設定）](../content_06_developer-guide/05-configuration/contact-verification.md)。

---

## 関連ドキュメント

- [ID（ユーザー）管理](../content_03_concepts/02-identity-management/concept-01-id-management.md) — IDポリシーと `preferred_username`
- [連絡先変更ポリシー](../content_03_concepts/02-identity-management/concept-03-contact-change-policy.md) — 識別子移動と属性更新を分ける理由
- [セルフサービス 連絡先の確認・変更（設定）](../content_06_developer-guide/05-configuration/contact-verification.md)
- [Email認証](../content_06_developer-guide/05-configuration/authn/email.md) / [SMS認証](../content_06_developer-guide/05-configuration/authn/sms.md) — 送信設定の流用元
