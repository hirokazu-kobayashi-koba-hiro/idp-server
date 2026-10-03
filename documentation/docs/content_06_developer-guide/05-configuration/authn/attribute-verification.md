# 属性照合

利用者が入力した値（生年月日、電話番号の下 4 桁など）を、アカウントに登録されている属性と照合する
`attribute-verification` の `概要`・`設定`・`利用方法` について説明します。

---

## 概要

**本人確認済みの利用者に対する追加の確認です。利用者を特定する手段でも、認証要素でもありません。**

- 前の段（パスワードなど）で利用者が確定してからでないと呼べません
- 結果は `OperationType.VERIFICATION` として記録されます。認証の成否（ポリシーが無いテナントの既定判定）には数えられず、ID Token の `amr` にも入りません
- 応答は「一致した」か「一致しなかった」かだけです。どの項目が違ったか、その属性が登録されているかは返しません

生年月日などは他人も知りうる値です。単独で認証を完了させる構成にはしないでください。

CIBA では `login_hint` で利用者が決まるため、前の段の成功が無くても呼べます（最初のステップに置けます）。
この場合も認証要素には数えられないため、照合だけで CIBA の認証は完了しません。

---

## 設定

### 認証設定

```json
{
  "id": "0199a1f0-5c2d-7e3f-8a4b-1c2d3e4f5a6b",
  "type": "attribute-verification",
  "interactions": {
    "attribute-verification": {
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

| 項目 | 内容 | 既定値 |
|---|---|---|
| `fields[].input` | 入力値を読むリクエストボディのキー | 必須 |
| `fields[].user_attribute` | 照合する属性（下表） | 必須 |
| `fields[].normalize` | 比較前の正規化（下表） | `exact` |
| `fields[].suffix_length` | 正規化のあと、末尾 N 文字だけを比較する。0 は全体 | `0` |
| `max_attempts` | 利用者ごとの試行回数の上限 | `5` |
| `lockout_seconds` | 上限に達したあと拒否する期間（最初の試行から数える） | `900` |

すべての項目が一致したときだけ成功です。読めない項目が 1 つでもある設定は全体を無効として扱い、
照合は 500（`server_error`）になります。項目を黙って読み飛ばすと、確認が弱くなるためです。

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

- `email` は、多くのテナントで前の段のログイン ID そのものです。前の段で入力済みの値を照合しても、確認は強くなりません
- `phone_number` は、登録値と入力の書式が違うと全桁では一致しません（例：登録値が `+819012345678`、入力が `09012345678`）。
  `suffix_length` で末尾だけを比べるか、登録値の書式に合わせて入力させてください

**正規化**

両方の値に同じ正規化をかけてから比較します。正規化できない値（日付として読めないなど）は一致しません。

| `normalize` | 内容 | 例 |
|---|---|---|
| `exact` | そのまま | |
| `nfkc` | Unicode NFKC で全角・半角をそろえ、前後の空白を除く | `ＡＢＣ` → `ABC` |
| `digits` | `nfkc` のあと数字だけを残す | `090-1234-5678` → `09012345678` |
| `date` | `nfkc` のあと日付として読み、`yyyy-MM-dd` にそろえる。`yyyy-MM-dd` / `yyyy/M/d` / `yyyyMMdd` を受け付ける | `2000/1/5` → `2000-01-05` |

### 総当たり対策

試行回数は 2 か所で数えます。

| 単位 | 数え方 | 上限に達したとき |
|---|---|---|
| 利用者 | キャッシュ（Redis）。認可リクエストをやり直しても減らない。一致すると 0 に戻る | `lockout_seconds` の間、正しい値でも `too_many_attempts` |
| 認証トランザクション | トランザクションに記録された失敗回数（DB） | そのトランザクションでは `too_many_attempts` |

**キャッシュが使えないときは、利用者単位の上限が効きません。** 残るのはトランザクション単位の上限だけで、
前の段を通せる者は、認可リクエストを作り直すたびに `max_attempts` 回ずつ試せます。
電話番号の下 4 桁（1 万通り）のように候補の少ない項目を使う場合は、認証ポリシーの `lock_conditions` に
`$.attribute-verification.failure_count` を書き、失敗が続いたらアカウントをロックする構成を前提にしてください。
`lock_conditions` によるロックは利用者のステータスとして DB に記録されるため、キャッシュに依存しません。

### 認証ポリシー

前の段で利用者を確定させ、そのあとに属性照合を置きます。

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "password_and_attribute_verification",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password", "attribute-verification"],
      "step_definitions": [
        { "method": "password", "order": 1, "requires_user": false, "user_identity_source": "username" },
        { "method": "attribute-verification", "order": 2, "requires_user": true }
      ],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.attribute-verification.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

---

## 利用方法

```http
POST /{tenant-id}/v1/authorizations/{id}/attribute-verification
```

```json
{
  "birthdate": "2000/01/05",
  "phone_last4": "5678"
}
```

| 状況 | HTTP | `error` |
|---|---|---|
| 一致 | 200 | - |
| 前の段で利用者が確定していない | 400 | `invalid_request` |
| 一致しない（属性が未登録の場合を含む） | 400 | `attribute_mismatch` |
| 試行回数の上限に達した | 400 | `too_many_attempts` |
| 設定が無い・読めない | 500 | `server_error` |

セキュリティイベントは `attribute_verification_success` / `attribute_verification_failure` です。

---

## 関連ドキュメント

- [認証ポリシー](../authentication-policy.md)
- [パスワード](./password.md)
