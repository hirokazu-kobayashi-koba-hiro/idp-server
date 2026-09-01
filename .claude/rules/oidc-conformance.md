---
paths:
  - "oidc-conformance-suite/**"
---

# OIDF 適合性テストのルール

実行の全体像と手順は `oidc-conformance-suite/README.md`。ここは**踏みやすい落とし穴**だけを書く。

## 前提の確認順序（これを飛ばすと原因究明に時間を溶かす）

上から順に見る。上が崩れていると下は必ず失敗する。

```bash
# 1. idp-server とテナント
curl -sk https://api.local.test/c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8/.well-known/openid-configuration \
  | jq '{issuer, pushed_authorization_request_endpoint, response_modes_supported}'
#    → 404 なら config/examples/financial-grade/setup.sh を流す（テナントは消えることがある）

# 2. suite スタック
curl -sk -o /dev/null -w '%{http_code}\n' https://localhost:8443/api/runner/available
#    → 200 でなければ docker compose -f oidc-conformance-suite/docker-compose.yaml up -d

# 3. ドライバが 1 つだけ動いているか（後述。最重要）
pgrep -f "[d]river.mjs"

# 4. suite から idp-server へ到達できるか
docker exec conformance-server curl -sk -o /dev/null -w '%{http_code}\n' \
  https://api.local.test/c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8/.well-known/openid-configuration
```

## ドライバは全スイート共用。1 プロセスだけ（必須）

**複数起動すると URL を取り合い、原因の分かりにくい失敗になる。** 実際に踏んだ症状:

- 振る舞いテーブルを持たない古いプロセスが URL を拾い、`user-rejects` で Cancel が押されない
- 2 プロセスが同じ passkey ファイルを奪い合い、署名カウンタが壊れて
  `Failed to verify authentication data`

テストを流す前に必ず `pgrep -f "[d]river.mjs"` で確認する。`kill` (SIGTERM) が効かないことが
あるので、止まらなければ `kill -INT`。

**スイートごとにドライバを立てる必要はない。** サインイン画面の URL に `tenant_id` が載っており、
`flow.mjs` の `TENANTS` からテナント別の設定（管理者・ユーザー・passkey ファイル）を引く。
FAPI 1.0 を流した後そのまま FAPI 2.0 を流せる。新しいテナントを足すときは `TENANTS` に追加する。
起動ログにさばけるテナントが出るので、そこに無い URL を拾うと既定テナントの管理者で
検証コードを取りに行って失敗する。

## alias が違うプランは並列に走る

`run.sh` に複数のプランを並べると、**alias が同じものは直列、違うものは並列**に実行される
（キューが alias 単位）。並列で困るのは共有状態を持つとき。

実際に踏んだ症状: ドライバがセッション用のブラウザコンテキストを 1 本しか持っておらず、
別プランのテストに枠を奪われて 2 回目の認可が `login_required` になった。セッションは
テスト ID をキーに持つこと（`driver.mjs` の `liveSessions`）。

逆に **alias が同じプランを並べるのは危険**。同時に 1 テストしか掴めないので、片方が
alias 衝突で落ちる。設定ファイルの alias が重複していないか確認する。

```bash
jq -r .alias config/examples/*/oidc-test/**/*.json | sort | uniq -d
```

## テスト設定の scope が profile を起動する（最も誤読しやすい）

idp-server はリクエストされた scope でプロファイルを決める
（`AuthorizationServerExtensionConfiguration.isFapi20()` / `isFapiAdvance()` / `isFapiBaseline()`）。
**テスト設定 JSON の `scope` にテナントの `fapi20_scopes` / `fapi_advance_scopes` の値が
入っていないと、そのプロファイルの検証が 1 つも走らない。**

このとき落ちるのは PKCE 必須・client assertion の `aud` 制限・sender-constrain 必須といった
「プロファイル固有の要件」だけなので、**個々の実装バグに見えて実際は設定ミス**という誤読をする。
FAPI 系のテストで複数の要件がまとめて素通りしていたら、まず scope の対応を疑う。

```bash
# テナント側
jq '.authorization_server.extension | {fapi20_scopes, fapi_advance_scopes, fapi_baseline_scopes}' <テナント設定>
# テスト設定側
jq '.client.scope' <テスト設定>
```

## テスト実行も同時に走らせない

**実行中に別の `run.sh` を叩くと、走っているテストが alias 衝突で INTERRUPTED になる。**
設定 JSON の `alias` は同時に 1 つのテストしか掴めない。

```
TEST-RUNNER | Stopping test due to alias conflict - before this test finished,
              you have started another test using the same alias.
```

蹴られた側は条件エラーが 0 でも INTERRUPTED になるため、**テスト内容の問題と誤読しやすい**。
INTERRUPTED を見たらまずログ末尾で alias conflict かどうかを確認する。

途中で実行を止めた場合も alias を掴んだままのテストが残る。この状態で流すと **1 個目が
alias 衝突で INTERRUPTED → ランナーが次のモジュールへ進む → それも INTERRUPTED** と連鎖し、
全モジュールが 1〜2 秒で終わる。テスト内容とは無関係なので、suite を作り直してから流す。

```bash
docker restart conformance-server
curl -sk https://localhost:8443/api/runner/running   # [] になっていること
```

## passkey ファイルとユーザーは常にセットで扱う

passkey はテナントごとに `driver/passkey-<label>.json`（label は `TENANTS` の値）に保存され、
サーバに登録済みの資格情報と 1:1 で対応している。片方だけ変えると壊れる。

| 操作 | 必要な対応 |
|---|---|
| `TENANTS` の `email` を変える | その passkey ファイルを**消す**（新規ユーザーとして登録し直し） |
| passkey ファイルを消す | `email` も**変える**（後述。サーバ側の資格情報を消すだけでは直らない） |
| テナントを作り直す（`setup.sh` 再実行） | passkey ファイルを**消す**（ユーザーごと消えている） |

### 「サーバの資格情報だけ消して登録し直す」はできない

画面は資格情報の有無ではなく **`user.status`** で登録/認証を切り替える
（`app-view/src/auth/stepHelpers.ts` の `shouldFido2Authenticate`）。

```ts
return !isInitialUser(userStatus);   // 初期ユーザー以外は常に「認証」
```

既存ユーザーの資格情報を DB から消しても、画面は「Use passkey」を出し続け、サーバは空の
`allowCredentials` を返し、ブラウザは `Passkey sign-in was cancelled` になる。
**復旧するには email を変えて新規ユーザーにする。**

実際に踏んだ症状: passkey ファイルの持ち主（`conformance-driver4@example.com`）と
`TENANTS` の `email`（`conformance-driver@example.com`）が食い違い、FAPI 1.0 の
ほぼ全モジュールが 30 秒タイムアウト → 240 秒で UNKNOWN。1 時間で 19 モジュールしか進まなかった。
サーバのログは `authentication challenge generated successfully` までしか出ず、
`retrieving credential` が無い（＝ブラウザが assertion を返していない）ことで切り分けられる。

## 署名カウンタを巻き戻さない

idp-server は WebAuthn §6.1.1 のクローン検知を実装している
（`WebAuthn4jAuthenticationExecutor:117`、`newSignCount <= 保存値` で拒否）。

仮想オーセンティケータはブラウザコンテキストごとに空なので毎回鍵を注入するが、
**カウンタも一緒に巻き戻すと 2 回目の認証が必ず失敗する**。`flow.mjs` の `savePasskey()` は
成功・失敗にかかわらず呼ぶこと（`driver.mjs` は `finally` で呼んでいる）。

## テストごとの振る舞いは `flow.mjs` の BEHAVIORS に集約する

すべてのテストが「ログインして同意」で通るわけではない。個別対応を `driver.mjs` に
散らかさず、テーブルに追加する。

```js
// flow.mjs
{ match: /user-rejects-authentication/, consent: "deny" }              // Cancel を押す
{ match: /par-ensure-reused-request-uri-.../, firstVisit: "abandon" }  // 1 回目は離脱
{ match: /oidcc-(prompt-login|max-age-1)$/, session: "reuse", screenshot: "second-login" }
```

判別材料はテスト名しかない（`/api/info/{id}` の `testName`）。**先に一致したものが採用される**
ので、具体的なパターンを先に置く（`max-age-1` を `max-age-10000` より先に、かつ末尾を固定する）。

| 軸 | 既定 | 変える理由 |
|---|---|---|
| `consent` | `allow` | 同意を拒否させるテスト |
| `firstVisit` | `complete` | 1 回目はログインせず離脱させるテスト |
| `session` | `fresh` | 既存セッションでの挙動を見るテスト。既定を `fresh` にしているのは、あるテストのセッションが次に漏れると独立性が壊れるため |
| `screenshot` | エラーページのみ | 2 回目のログイン画面の提出が要るテスト |

## REVIEW はスクリーンショット提出待ち。失敗ではない

「その画面が表示されること」を目視確認するテストは callback を返さない。suite は
REVIEW のログエントリを作り `upload` に提出先 ID を入れて待つ。埋めないと **240 秒で
タイムアウト**する。`driver.mjs` の `fulfillReview()` が自動提出している。

| 何を確認するテストか | 例 |
|---|---|
| エラーページが出ること | `par-attempt-reuse-request_uri` など |
| 2 回目に再認証を求められること | `oidcc-prompt-login` / `oidcc-max-age-1` |

後者は **検証がすべて成功していても提出しないと UNKNOWN で終わる**。条件エラーが 0 なのに
UNKNOWN になっていたら、テスト側が `createPlaceholder()` で待っていないか確認する。

結果が `REVIEW` で終わるのは**正常**。`FAILED` と混同しないこと。

## `--rerun` のプラン番号は位置依存

`--rerun 1:44` の 1 と 44 はコマンドラインでの並び順であって、規格やテスト名ではない。
**プラン構成を変えると番号がずれる。** 恒久的な参照にはテスト名を使い、番号は都度確認する。

## clone とイメージのバージョン差に注意

ランナー `run-test-plan.py` は `CONFORMANCE_SUITE_DIR` のクローンから、サーバは Docker
イメージから来る。**両者は別々に更新される**ため、API が食い違うと黙って壊れる。
挙動がおかしいときはまず両方の日付を確認する。

```bash
docker inspect registry.gitlab.com/openid/conformance-suite:latest --format '{{.Created}}'
git -C "$CONFORMANCE_SUITE_DIR" log -1 --format='%h %ad' --date=short
```

## テスト設定 JSON はここに置かない

クライアント ID や証明書がテナント設定と対になるため、`config/examples/<setup>/oidc-test/` に
置く（5 つの setup が既にこの構成）。`oidc-conformance-suite/` は実行する仕組みだけを持つ。
