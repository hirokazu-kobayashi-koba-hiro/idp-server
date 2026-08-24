# 配列クレームの要素単位同意 実験ガイド

エンドユーザーが「持っている口座のうち、この1つだけ渡す」を同意画面で選べるようにする機能（Issue #1816）を手元で確認するガイドです。

`denied_claims` はクレームを丸ごとしか落とせないため、複数の所有物を格納したカスタムプロパティは all-or-nothing でした。`claims:accounts` に同意すると全口座が出る、拒否すると1つも出ない。ここで確認するのは、その中間を選べるようにした `granted_claim_values` の挙動です。

> **前提**: `setup.sh` が正常に完了していること。
> `claims:*` スコープの基礎は EXPERIMENTS-authorization-server.md の Experiment 10（`custom_claims_scope_mapping`）を先に読むと理解が早いです。

---

## 共通準備

```bash
cd config/templates/use-cases/login-password-only
source helpers.sh --org claim-value-check
source claim-value-helpers.sh
get_admin_token
```

> `--org` は `setup.sh` 実行時の `ORGANIZATION_NAME` に合わせてください。
> 専用の組織を作る場合は `ORGANIZATION_NAME=claim-value-check ./setup.sh` から始めます。

`claim-value-helpers.sh` が定義する関数は5つです。中身はそのまま読める短い curl なので、生のリクエストを見たいときはファイルを開いてください。

| 関数 | 役割 |
|------|------|
| `setup_claim_value_tenant` | `claims:*` スコープ追加・カスタムクレーム有効化・同意画面のあるページに変更 |
| `register_claim_value_user` | テストユーザー登録 + `custom_properties` 付与 |
| `show_claim_values` | view-data の選択候補を認証前後で表示 |
| `consent_url` | ブラウザで開く認可リクエストを出力 |
| `try_selection` | 同意ボディを渡してアクセストークンの中身を表示 |

---

## Experiment 1: 選択候補を同意画面に出す

> **やりたいこと**: ユーザーが持つ配列プロパティを、同意画面の選択肢として出したい
>
> **使う設定**: `authorization_server.extension.custom_claims_scope_mapping` + `claims:*` スコープ
>
> **実装の仕組み**: `OAuthViewDataCreator` が、リクエストされた `claims:*` スコープに対応する
> カスタムプロパティのうち **配列のものだけ** を view-data の `claim_values` に載せる。
> スカラーは「選ぶ余地がない」ので対象外（丸ごとの拒否は `denied_scopes` の担当）。
> 認証トランザクションがユーザーを解決した後にしか出ないので、認証前の view-data に
> ユーザー属性は含まれない。

### 1. テナントを設定する

```bash
setup_claim_value_tenant
```

4つの変更をまとめて行います。

| 変更 | なぜ必要か |
|------|-----------|
| `scopes_supported` に `claims:accounts` / `claims:cards` / `claims:branch` | Discovery 表示用 |
| クライアントの `scope` にも同じ3つ | 実際のスコープフィルタはクライアント設定側（Experiment 4 参照） |
| `extension.custom_claims_scope_mapping = true` | これが無いと `claims:*` を足してもクレームがトークンに出ない |
| `ui_config.signin_page = "/auth/"` | 同意画面（`ConsentStep`）があるのは汎用サインイン画面 `/auth/` のみ |

> **テンプレートの穴が2つ**:
> `public-tenant-template.json` の `extension` に `custom_claims_scope_mapping` がありません（`onboarding-template.json` にはある）。
> また `signin_page` の既定は `/signin/` で、こちらは同意画面を持たないため、認証が終わるとそのまま完了してしまいます（`config/examples/*` はいずれも `/auth/`）。

### 2. テストユーザーを登録する

```bash
register_claim_value_user
```

サインアップフローで登録したあと、管理APIで `custom_properties` を付与します（登録スキーマには `custom_properties` の口がないため）。付けているのは3種類で、それぞれ扱いが違います。

| プロパティ | 型 | 期待される扱い |
|-----------|-----|--------------|
| `accounts` | 文字列の配列 | 要素ごとに選べる |
| `cards` | オブジェクトの配列 | 要素ごとに選べる |
| `branch` | スカラー | 選択対象外（候補に出ない） |

登録したメールアドレスとパスワードは `CLAIM_VALUE_EMAIL` / `CLAIM_VALUE_PASSWORD` に入り、以降の関数がそのまま使います。

### 3. 候補が出るか確認する

```bash
show_claim_values
```

### 4. 期待結果

| タイミング | `claim_values` | 理由 |
|-----------|---------------|------|
| 認証前 | `null` | ユーザーが未解決。認可IDを握った第三者に属性を渡さない |
| 認証後 | `accounts` と `cards` のみ | 配列だけが候補。`branch` はスカラーなので出ない |

---

## Experiment 2: 同意画面で選ぶ

> **やりたいこと**: 実際の画面で要素を選んで、トークンの中身が変わることを確認したい

### 1. 認可リクエストを開く

```bash
consent_url
```

出力された URL をブラウザで開き、Experiment 1 で登録したユーザー（`echo ${CLAIM_VALUE_EMAIL}`）でログインします。

> **遷移先について**: 302 の飛び先は `tenant.ui_config.base_url` + `signin_page` で組み立てられます。
> テンプレートの既定は `UI_BASE_URL=https://auth.local.test` なので、app-view コンテナの画面に着地します。
> 画面を変更したときは `docker compose up -d --build app-view` を忘れると古いビルドを見ることになります。
>
> `api.local.test/auth-views/...` は Spring Boot の jar に同梱された別ビルドです。
> そちらを更新するには `cd app-view && npm run build-and-copy` のあと jar とイメージの再ビルドが要ります。

### 2. 同意画面の見え方

```
 Permissions
   ☑ Your basic profile
   ☑ Your email address
   ☑ Accounts                                      ← claims:accounts
       ☑ acc-1  ☑ acc-2  ☑ acc-3
   ☑ Cards                                         ← claims:cards
       ☑ Id: card-1 · Brand: visa · Limit: 100000
       ☑ Id: card-2 · Brand: master · Limit: 50000
   ☑ Branch                                        ← スカラーなので子リストなし
```

- 値は対応するスコープ行の下に入れ子で並びます。同じ権限の粒度違いなので、別セクションには分けません
- 親（Accounts）のチェックを外すと、子はグレーアウトして無効化されます。消えないのは「なぜ選べないのか」を上の行が説明するためです
- オブジェクトの要素は、自身のスカラーフィールドを `フィールド: 値` で連ねてラベルにします

### 3. 発行されたトークンを見る

`acc-2` だけ残して「Continue」を押したあと、リダイレクト先の URL から `code` を取り出して交換します。

```bash
TOKEN_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=YOUR_CODE" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

decode_jwt_payload "$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')" \
  | jq '{accounts, cards, branch}'
```

---

## Experiment 3: granted_claim_values を API だけで試す

> **やりたいこと**: 画面を経由せず、同意ボディのパターンごとの挙動を並べて確認したい
>
> **変わるもの**: `/authorize` のリクエストボディの `granted_claim_values`

`complete_auth_flow` は空ボディ（`{}`）を送るため、選択を渡す場合は `try_selection` を使います。

```bash
try_selection "選択なし" '{}'

try_selection "acc-2 だけ" '{"granted_claim_values": {"accounts": ["acc-2"]}}'

try_selection "持っていない値を混ぜる" \
  '{"granted_claim_values": {"accounts": ["acc-2", "acc-999-not-owned"]}}'

try_selection "1つも選ばない" '{"granted_claim_values": {"accounts": []}}'

try_selection "オブジェクト要素を選ぶ" \
  '{"granted_claim_values": {"cards": [{"limit": 100000, "brand": "visa", "id": "card-1"}]}}'

try_selection "オブジェクトを id だけで指定" \
  '{"granted_claim_values": {"cards": [{"id": "card-1"}]}}'
```

### 期待結果

| 同意ボディ | `accounts` / `cards` | 理由 |
|-----------|---------------------|------|
| 選択なし | 全要素 | 既定は全リリース。触っていないクレームは絞らない |
| `["acc-2"]` | `["acc-2"]` | 選んだ要素だけ |
| `["acc-2", "acc-999-not-owned"]` | `["acc-2"]` | ユーザーが持つ値との積集合。**同意で値を注入できない** |
| `[]` | クレームごと消える | 丸ごと拒否と同じ結果。空配列という第三の答えは返さない（OIDC Core §5.3.2） |
| `[{limit, brand, id}]`（順不同） | 選んだオブジェクト1件 | オブジェクトの一致はフィールドの集合で判定。順序は無関係 |
| `[{"id": "card-1"}]` | クレームごと消える | 一致は**要素まるごと**。部分指定は何にも一致しない |

> **ポイント**: `granted_claim_values` は narrowing 専用です。持っていない値を名指ししても入りません。
> これがないと、同意ボディに任意の値を書くだけでトークンのクレームを詐称できてしまいます。

---

## 現時点の制約

| 制約 | 内容 |
|------|------|
| UserInfo は未対応 | `UserinfoHandler` はアクセストークンの `sub` からユーザーを都度ロードするため、グラントに載せた絞り込み結果が届きません。アクセストークンと ID Token は絞れていますが、UserInfo は全要素を返します（対応方針を検討中） |
| オブジェクトは全体一致 | 識別子フィールドだけを指定して選ぶことはできません。「どのフィールドが識別子か」を同意の仕様に持たせる拡張が必要です |

---

## 元に戻す

```bash
restore_auth_server
restore_client
restore_tenant
```

テナントごと消す場合は `./delete.sh`（`ORGANIZATION_NAME` を合わせて実行）を使います。

---

## まとめ

| # | やりたいこと | 使うもの | 確認できること |
|---|------------|---------|--------------|
| 1 | 選択候補を出したい | `claims:*` + `custom_claims_scope_mapping` | 配列だけが `claim_values` に出る／認証前は出ない |
| 2 | 画面で選びたい | 同意画面（app-view の `/auth/`） | スコープ配下の入れ子表示と、親を外したときの無効化 |
| 3 | 挙動を並べて見たい | `granted_claim_values` | 積集合・空選択・オブジェクト一致の4パターン |
