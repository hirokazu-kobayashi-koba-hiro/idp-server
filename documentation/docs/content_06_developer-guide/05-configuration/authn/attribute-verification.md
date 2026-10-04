# 属性照合

認証の途中で、確定した利用者を確認する `attribute-verification` の `概要`・`設定`・`利用方法` について説明します。

確認は名前を付けたインタラクションごとに設定し、認証ポリシーで別々のステップとして並べます。
1 つのインタラクションは、次のどちらか一方です。

| 確認 | 内容 | 例 |
|---|---|---|
| 条件（`conditions`） | アカウントの状態を、認証ポリシーと同じ条件式で確認する。入力は不要 | 身元確認済み（`IDENTITY_VERIFIED`）か |
| 入力の照合（`fields`） | 利用者が入力した値を、登録済みの属性と照合する | 生年月日、電話番号の下 4 桁 |

---

## 概要

**本人確認済みの利用者に対する追加の確認です。利用者を特定する手段でも、認証要素でもありません。**

- 前の段（パスワードなど）で利用者が確定してからでないと呼べません
- 結果は `OperationType.VERIFICATION` として記録されます。認証の成否（ポリシーが無いテナントの既定判定）には数えられず、ID Token の `amr` にも入りません
- 入力の照合の応答は「一致した」か「一致しなかった」かだけです。どの項目が違ったか、その属性が登録されているかは返しません

**条件を途中で確認する理由**：認証ポリシーの `success_conditions` は、最後の `authorize` で評価されます。
`$.user.status` で身元確認済みを求めても、満たさない利用者がそれを知るのは全ステップのあとです。
利用者を確定したステップの直後に条件のステップを置けば、その時点でテナントが決めた `error`
（例：`identity_verification_required`）が返り、認可画面は身元確認の画面へ案内するなどの分岐ができます。

生年月日などは他人も知りうる値です。単独で認証を完了させる構成にはしないでください。

CIBA では `login_hint` で利用者が決まるため、前の段の成功が無くても呼べます（最初のステップに置けます）。
この場合も認証要素には数えられないため、照合だけで CIBA の認証は完了しません。

---

## 設定

### 認証設定

`interactions` のキーがインタラクションの名前です。

```json
{
  "id": "0199a1f0-5c2d-7e3f-8a4b-1c2d3e4f5a6b",
  "type": "attribute-verification",
  "interactions": {
    "identity-verified": {
      "execution": {
        "details": {
          "conditions": {
            "any_of": [[
              { "path": "$.user.status", "type": "string", "operation": "eq", "value": "IDENTITY_VERIFIED" }
            ]]
          },
          "error": "identity_verification_required"
        }
      }
    },
    "kba": {
      "execution": {
        "details": {
          "fields": [
            { "input": "birthdate", "user_attribute": "birthdate", "normalize": "date" },
            { "input": "phone_last4", "user_attribute": "phone_number", "normalize": "digits", "suffix_length": 4 }
          ],
          "max_attempts": 5,
          "lockout_seconds": 900
        }
      }
    }
  }
}
```

**条件（`conditions`）**

| 項目 | 内容 | 既定値 |
|---|---|---|
| `conditions` | 確認する条件。書き方と参照できるパス（`$.user.*`、`$.request.custom_params.*`、各ステップの結果）、`value_path` は認証ポリシーの `success_conditions` と同じ | 必須 |
| `error` | 条件を満たさないときに返す `error`。英小文字・数字・`_` のみ | `attribute_condition_not_satisfied` |

入力が無く推測ではないので、条件を満たさなかった回数は試行回数として数えません。

**RP が渡した値との照合**：RP が認可リクエストで渡した値（例：会員番号）が、ログインした利用者のものかを確かめられます。
`$.request.custom_params.*` に見えるのは、認証ポリシーの `custom_params_trusted_sources` で信頼する出どころの値だけです（既定は PAR とリクエストオブジェクト。[認証ポリシー](../authentication-policy.md#request-custom-params)）。

```json
"member-match": {
  "execution": {
    "details": {
      "conditions": {
        "any_of": [[
          { "path": "$.request.custom_params.member_no", "type": "string", "operation": "eq",
            "value_path": "$.user.custom_properties.member_no" }
        ]]
      },
      "error": "member_mismatch"
    }
  }
}
```

属性照合のステップを含むポリシーでは SSO（セッションの再利用）をせず、毎回サインインからやり直します。照合は「その認可リクエストでの確認」で、セッションでは代わりにならないためです（[認証ポリシー](../authentication-policy.md#request-custom-params)）。

**入力の照合（`fields`）**

| 項目 | 内容 | 既定値 |
|---|---|---|
| `fields[].input` | 入力値を読むリクエストボディのキー。値は文字列で送る | 必須 |
| `fields[].user_attribute` | 照合する属性（下表） | 必須 |
| `fields[].normalize` | 比較前の正規化（下表） | `exact` |
| `fields[].functions` | `normalize` の代わりに、比較前にかける function の並び（下記）。`normalize` との同時指定は不可 | なし |
| `fields[].suffix_length` | 正規化のあと、末尾 N 文字だけを比較する。0 は全体 | `0` |
| `max_attempts` | 利用者ごとの試行回数の上限 | `5` |
| `lockout_seconds` | 上限に達したあと拒否する期間（最初の試行から数える） | `900` |

すべての項目が一致したときだけ成功です。入力が文字列でない（数値で送った等）リクエストは
`invalid_request` で拒否し、試行回数には数えません。

**1 つのインタラクションに `conditions` と `fields` の両方は書けません。** 結果はインタラクションの名前ごとに
`$.attribute-verification.interactions.<名前>.*` として記録されます。1 つの名前の失敗が「推測の失敗」か
「条件を満たさない」のどちらか一方になるので、アカウントロックの条件を迷わず書けます。

読めない設定（両方の指定、未知の演算子、空の条件グループ、読めない項目など）は全体を無効として扱い、
500（`server_error`）になります。黙って読み飛ばすと、確認が弱くなるためです。

**照合できる属性**

| `user_attribute` | 内容 |
|---|---|
| `birthdate` | 生年月日 |
| `phone_number` | 電話番号 |
| `email` | メールアドレス |
| `name` / `given_name` / `family_name` | 氏名 |
| `address.postal_code` | 郵便番号 |
| `custom_properties.<key>` | カスタム属性（文字列・数値・真偽値のみ） |

`verified_claims` やパスワードなどは照合できません。

- `email` は、多くのテナントで前の段のログイン ID そのものです。前の段で入力済みの値を照合しても、確認は強くなりません。
  照合する場合は `normalize: "email"` を使います（大文字・小文字をそろえます）
- `phone_number` は、登録値と入力の書式が違うと全桁では一致しません（例：登録値が `+819012345678`、入力が `09012345678`）。
  `suffix_length` で末尾だけを比べるか、登録値の書式に合わせて入力させてください
- `custom_properties.<key>` は、app-view では `<key>` がそのまま入力欄のラベルになります。
  利用者に見せる名前が別にあるなら、自作の画面でラベルを用意してください

**正規化**

両方の値に同じ正規化をかけてから比較します。正規化の結果が空になる値（日付として読めない、数字が 1 つも無いなど）は、
値なしとして扱い、何とも一致しません。

| `normalize` | 内容 | 一致する例 |
|---|---|---|
| `exact` | そのまま | |
| `nfkc` | NFKC で全角・半角をそろえ、前後の空白を除く | `ＹＡＭＡＤＡ` = `YAMADA` |
| `name` | NFKC → 空白をすべて除く → ダッシュ類（U+2010–2015、U+2212）を `-` に → 小文字 | `Smith‐Jones` = `smith - jones`、`山田 太郎` = `山田太郎` |
| `kana` | `name` と同じ → ひらがなをカタカナに | `やまだ たろう` = `ﾔﾏﾀﾞﾀﾛｳ` = `ヤマダ タロウ` |
| `email` | NFKC → 前後の空白を除く → 小文字 | `Taro@Example.com` = `taro@example.com` |
| `digits` | NFKC → 数字以外を除く | `〒１２３－４５６７` = `123-4567` |
| `date` | 日付として読み `yyyy-MM-dd` に（`-` / `/` / `.` / 8 桁 / `年月日`、全角も） | `1990年4月1日` = `1990/4/1` = `19900401` |

一致しないもの：
- 異体字・旧字体（`髙橋` と `高橋`、`渡邊` と `渡辺`）。氏名で照合するならフリガナ（`kana`）を推奨します
- 和暦（`H2.4.1`）。画面では日付の入力欄を使ってください
- 長音符 `ー` は、カナの名前で使う文字なのでダッシュとしてそろえません（`ジョーンズ` と `ジョンズ` は一致しない）

**function を直接書く**

プリセットで足りないときは、マッピングと同じ書き方の function を並べられます。

```json
{ "input": "member_no", "user_attribute": "custom_properties.member_no",
  "functions": [
    { "name": "normalize" },
    { "name": "regex_replace", "args": { "pattern": "^M-", "replacement": "" } }
  ] }
```

使える function は、入力だけから毎回同じ結果を返す変換に限ります：`normalize` / `trim` / `case` / `replace` / `regex_replace` / `substring` / `kana` / `date`。
`exists` や `random_string` のように比較を壊すもの、知らない名前、引数の誤り（例：`kana` の `to: "katakan"`）は、設定エラー（500）になります。
各 function は [マッピング関数](../../04-implementation-guides/advanced/mapping-functions.md) を参照してください。

**表記をそろえることについて**：マッピング関数の `normalize` は「どの利用者かを決める比較には使わない」としています。
属性照合は、確定済みの 1 人の利用者について、入力が登録値と同じかを確かめるだけで、どの利用者かを決める比較ではありません。
表記をそろえても広がるのはその利用者が入力できる書き方だけで、別の利用者の値と一致するようにはなりません。

### 総当たり対策（入力の照合）

試行回数は、インタラクションごとに 2 か所で数えます。

| 単位 | 数え方 | 上限に達したとき |
|---|---|---|
| 利用者 | キャッシュ（Redis）。認可リクエストをやり直しても減らない。一致すると 0 に戻る | `lockout_seconds` の間、正しい値でも `too_many_attempts` |
| 認証トランザクション | トランザクションに記録された、そのインタラクションの失敗回数（DB） | そのトランザクションでは `too_many_attempts` |

**キャッシュが使えないときは、利用者単位の上限が効きません。** 残るのはトランザクション単位の上限だけで、
前の段を通せる者は、認可リクエストを作り直すたびに `max_attempts` 回ずつ試せます。
電話番号の下 4 桁（1 万通り）のように候補の少ない項目を使う場合は、認証ポリシーの `lock_conditions` に
`$.attribute-verification.interactions.<入力の照合の名前>.failure_count` を書き、失敗が続いたらアカウントをロックする構成を前提にしてください。
`lock_conditions` / `failure_conditions` に合計の `$.attribute-verification.failure_count` を使うと、条件のステップで止まった利用者
（身元確認が済んでいないなど）が呼ぶだけで、アカウントがロックされたり、認可リクエストが失敗したりします。内訳の名前で書いてください。
`lock_conditions` によるロックは利用者のステータスとして DB に記録されるため、キャッシュに依存しません。

### 認証ポリシー

前の段で利用者を確定させ、そのあとにインタラクションを 1 つずつステップとして置きます（`step_definitions[].interaction`）。

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "password_then_attribute_verification",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password", "attribute-verification"],
      "step_definitions": [
        { "method": "password", "order": 1, "requires_user": false, "user_identity_source": "username" },
        { "method": "attribute-verification", "interaction": "identity-verified", "order": 2, "requires_user": true },
        { "method": "attribute-verification", "interaction": "kba", "order": 3, "requires_user": true }
      ],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.attribute-verification.interactions.identity-verified.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.attribute-verification.interactions.kba.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      },
      "lock_conditions": {
        "any_of": [
          [
            { "path": "$.attribute-verification.interactions.kba.failure_count", "type": "integer", "operation": "gte", "value": 5 }
          ]
        ]
      }
    }
  ]
}
```

`success_conditions` には、インタラクションごとの内訳を並べます。合計の `$.attribute-verification.success_count` は、
どちらか片方の成功でも満たされてしまいます。

---

## 利用方法

```http
POST /{tenant-id}/v1/authorizations/{id}/attribute-verification
```

リクエストボディの `interaction` で、実行するインタラクションを指定します。

```json
{ "interaction": "identity-verified" }
```

```json
{
  "interaction": "kba",
  "birthdate": "2000/01/05",
  "phone_last4": "5678"
}
```

| 状況 | HTTP | `error` |
|---|---|---|
| 成功 | 200 | - |
| 前の段で利用者が確定していない | 400 | `invalid_request` |
| `interaction` が無い、または設定に無い名前 | 400 | `invalid_request` |
| 入力が文字列でない | 400 | `invalid_request`（試行回数に数えない） |
| 条件を満たさない | 400 | 設定の `error`（既定 `attribute_condition_not_satisfied`） |
| 一致しない（属性が未登録の場合を含む） | 400 | `attribute_mismatch` |
| 試行回数の上限に達した | 400 | `too_many_attempts` |
| 設定が無い・読めない | 500 | `server_error` |

セキュリティイベントは `attribute_verification_success` / `attribute_verification_failure` です。

---

## 関連ドキュメント

- [認証ポリシー](../authentication-policy.md)
- [パスワード](./password.md)
