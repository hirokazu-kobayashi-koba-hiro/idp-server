---
name: use-case-attribute-verification
description: 属性照合ユースケースの設定ガイド。ログインの途中で利用者を確認するステップ（身元確認済みか等のアカウントの状態の条件、生年月日・電話番号の下 4 桁等の入力の照合）のヒアリングと、認証設定・認証ポリシーの JSON を提供。既存のログイン構成に後付けする。
---

# 属性照合（ログインの途中での利用者の確認）

パスワードなどで利用者を確定したあとに、その利用者を確認するステップを足すユースケース。
単体でログインが成り立つものではなく、**既存のログイン構成（use-case-login / use-case-mfa 等）に後付けする**。

**ユースケース**:
- 身元確認済み（`IDENTITY_VERIFIED`）の利用者だけを通し、未確認ならその場で eKYC へ案内する
- 特定のロール・会員区分の利用者だけを通す
- 高リスクな操作の前に、生年月日や電話番号の下 4 桁を入力させる（知識ベースの確認）

**認証要素には数えない**（`OperationType.VERIFICATION`）。`amr` に入らず、照合だけで認証は完了しない。

開発者ガイド: `documentation/docs/content_06_developer-guide/05-configuration/authn/attribute-verification.md`

## なぜ認証ポリシーの条件だけでは足りないか

`success_conditions` に `$.user.status` を書いても、評価は最後の `authorize`。満たさない利用者がそれを知るのは全ステップのあとになる。
このステップを利用者の確定直後に置けば、その場でテナントが決めた `error` が返り、画面が案内を出し分けられる。

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | 何を確認するか | アカウントの状態（条件） / 入力の照合 / 両方 | 認証設定 `interactions` の数と種類 |
| 2 | 状態の条件 | status / roles / custom_properties 等。RP が渡した値（会員番号等）との一致 | `conditions`（認証ポリシーと同じ条件式）。RP の値は `$.request.custom_params.*` と `value_path` |
| 3 | 条件を満たさないときの `error` | 例: `identity_verification_required` | `error`（画面の案内の出し分けに使う） |
| 4 | 入力させる項目と表記のそろえ方 | 生年月日 / 電話番号（下 N 桁） / 郵便番号 / 氏名 / フリガナ / メール / カスタム属性 | `fields[].user_attribute`, `normalize`（または `functions`）, `suffix_length` |
| 5 | 試行回数と期間 | 既定 5 回 / 900 秒 | `max_attempts`, `lockout_seconds` |
| 6 | 入力の照合の失敗でアカウントをロックするか | yes（候補の少ない項目では前提） / no | 認証ポリシー `lock_conditions` |
| 7 | どのログインの後ろに置くか | 既存ポリシーの `step_definitions` | 認証ポリシー `step_definitions`, `success_conditions` |

## インタラクションの設計

確認は名前を付けたインタラクションに分け、**1 つの名前は条件か入力の照合のどちらか一方**（両方書くと設定エラー）。
両方やりたいときは 2 つの名前を作り、別々のステップとして並べる。

| 種類 | details | 入力 | 失敗の数え方 |
|---|---|---|---|
| 条件 | `conditions` + `error` | なし（画面は表示時に自動で送る） | 試行回数に数えない |
| 入力の照合 | `fields` + `max_attempts` + `lockout_seconds` | `fields[].input` のキー | 利用者単位（Redis）とトランザクション単位（DB） |

結果は `$.attribute-verification.interactions.<名前>.*` に名前ごとに記録される。

## 設定 JSON

### 認証設定（`type: attribute-verification`）

```json
{
  "id": "<UUID>",
  "type": "attribute-verification",
  "attributes": {},
  "metadata": {},
  "interactions": {
    "identity-verified": {
      "execution": {
        "details": {
          "conditions": {
            "any_of": [[ { "path": "$.user.status", "type": "string", "operation": "eq", "value": "IDENTITY_VERIFIED" } ]]
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

`user_attribute` に使えるもの: `birthdate` / `phone_number` / `email` / `name` / `given_name` / `family_name` / `address.postal_code` / `custom_properties.<key>`（スカラーのみ）。
`normalize`（両側に同じ変換をかけてから比べる。結果が空なら一致しない）:

| プリセット | 用途 | 一致する例 |
|---|---|---|
| `exact`（既定） | そのまま | |
| `nfkc` | 全角・半角だけそろえる | `ＹＡＭＡＤＡ` = `YAMADA` |
| `name` | 氏名（漢字・ローマ字）。空白除去・ダッシュ統一・小文字 | `山田 太郎` = `山田太郎`、`Smith‐Jones` = `smith - jones` |
| `kana` | フリガナ。`name` ＋ ひらがな→カタカナ | `やまだ` = `ﾔﾏﾀﾞ` = `ヤマダ` |
| `email` | メール。trim・小文字 | `Taro@Example.com` = `taro@example.com` |
| `digits` | 数字だけ（電話・郵便番号） | `〒１２３－４５６７` = `123-4567` |
| `date` | 日付（`/`・`.`・8 桁・年月日・全角） | `1990年4月1日` = `19900401` |

異体字・旧字体（`髙`・`邊` など）と和暦はそろわない。氏名ならフリガナ（`kana`）を推奨。
プリセットで足りないときは、マッピングと同じ書き方の `functions`（`normalize` / `trim` / `case` / `replace` / `regex_replace` / `substring` / `kana` / `date` のみ）を `normalize` の代わりに書ける。

### 認証ポリシー（既存ポリシーへの差分）

```json
{
  "available_methods": ["password", "attribute-verification"],
  "step_definitions": [
    { "method": "password", "order": 1, "requires_user": false },
    { "method": "attribute-verification", "interaction": "identity-verified", "order": 2, "requires_user": true },
    { "method": "attribute-verification", "interaction": "kba", "order": 3, "requires_user": true }
  ],
  "success_conditions": {
    "any_of": [[
      { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
      { "path": "$.attribute-verification.interactions.identity-verified.success_count", "type": "integer", "operation": "gte", "value": 1 },
      { "path": "$.attribute-verification.interactions.kba.success_count", "type": "integer", "operation": "gte", "value": 1 }
    ]]
  },
  "lock_conditions": {
    "any_of": [[
      { "path": "$.attribute-verification.interactions.kba.failure_count", "type": "integer", "operation": "gte", "value": 5 }
    ]]
  }
}
```

- `success_conditions` は**名前ごとの内訳**を並べる。合計の `$.attribute-verification.success_count` は片方の成功でも満たされる
- `lock_conditions` は**入力の照合の名前**で書く。合計の `failure_count` だと、条件で止まった利用者（未確認など）が呼ぶだけでロックされる

## 既存テナントへの追加手順

ORGANIZER テナントの管理者トークンで、組織レベル API を使う（パスは `use-case-setup` の表を参照）。

1. 認証設定を作成: `POST .../tenants/{tenant-id}/authentication-configurations`（上の JSON）
2. 認証ポリシーを更新: `PUT .../tenants/{tenant-id}/authentication-policies/{policy-id}`（既存ポリシーに上の差分を足したもの全体）
3. 状態の条件で `IDENTITY_VERIFIED` を使うなら、利用者がそのステータスになる経路を確認する。idp-server の身元確認（`use-case-ekyc`）は、結果設定の `user_status` が未指定なら承認時に `IDENTITY_VERIFIED` へ遷移させる（`KEEP` だと遷移しない）

## 認可画面

- app-view は対応済み。view-data の `authentication_step_hints["attribute-verification"].interactions.<名前>` に `kind` と `inputs` が入り、条件のステップは自動送信、入力の照合は入力欄を出す。`custom_properties.<key>` のラベルはキー名がそのまま出るので、利用者向けの名前が要るなら自作の画面にする
- 自作の画面の場合: リクエストボディに `interaction` を必ず入れる。ステップの完了は authentication-status の `interaction_results["attribute-verification"].interactions.<名前>.success_count` で判定する（方式単位では判定できない）

| `error` | 画面の案内 |
|---|---|
| 設定の `error`（例: `identity_verification_required`） | 身元確認などへの誘導 |
| `attribute_mismatch` | 入力が登録内容と一致しない |
| `too_many_attempts` | しばらく待ってから再試行 |
| `invalid_request` | 利用者が未確定、または `interaction` の指定漏れ |

## 動作確認

そのまま動く 3 ステップ構成（パスワード → `identity-verified` → `kba`、テストユーザー 2 人）:
`config/examples/attribute-verification/`（`setup.sh` → `verify.sh`、ブラウザ手順は `VERIFY.md`）

## 注意点

- `email` は多くのテナントでログイン ID そのもの。照合しても確認は強くならない（照合するなら `normalize: "email"`）
- `phone_number` は書式が違うと全桁では一致しない（`+8190…` と `090…`）。`suffix_length` で末尾を比べる
- Redis が使えないと利用者単位の上限が効かない。下 4 桁（1 万通り）など候補の少ない項目では `lock_conditions` を前提にする
- CIBA では `login_hint` で利用者が決まるため、最初のステップにも置ける（その場合も認証は完了しない）
- `error` は英小文字・数字・`_` のみ
- RP が渡した値との照合（`$.request.custom_params.<名前>` eq `value_path: $.user.custom_properties.<名前>`）は、信頼する出どころの値しか見えない（ポリシーの `custom_params_trusted_sources`、既定 `pushed` / `request_object`）。クエリだけで渡す RP には PAR か署名つきリクエストオブジェクトを使ってもらう。取り違え防止（利用者に悪意が無い前提）だけが目的なら、そのポリシーに限って `query` を足してもよい（利用者は書き換えられるので、本人の保証にはならない）
- 属性照合のステップ（または `$.request.*` / `$.attribute-verification.*` を見る条件）を含むポリシーでは SSO しない（view-data の `session_enabled` が false、`authorize-with-session` は 400、prompt=none は `login_required`）。SSO を効かせたいクライアントには、`conditions` で照合の無いポリシーを当てる
