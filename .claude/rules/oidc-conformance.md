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

## ドライバは 1 プロセスだけ（必須）

**複数起動すると URL を取り合い、原因の分かりにくい失敗になる。** 実際に踏んだ症状:

- 振る舞いテーブルを持たない古いプロセスが URL を拾い、`user-rejects` で Cancel が押されない
- 2 プロセスが `passkey.json` を奪い合い、署名カウンタが壊れて
  `Failed to verify authentication data`

テストを流す前に必ず `pgrep -f "[d]river.mjs"` で確認する。`kill` (SIGTERM) が効かないことが
あるので、止まらなければ `kill -INT`。

## passkey.json とユーザーは常にセットで扱う

`driver/passkey.json` はサーバに登録済みの資格情報と 1:1 で対応している。片方だけ変えると壊れる。

| 操作 | 必要な対応 |
|---|---|
| `DRIVER_EMAIL` を変える | `passkey.json` を**消す**（新規ユーザーとして登録し直し） |
| `passkey.json` を消す | `DRIVER_EMAIL` も**変える**（サーバ側に残った鍵と食い違う） |
| テナントを作り直す（`setup.sh` 再実行） | `passkey.json` を**消す**（ユーザーごと消えている） |

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
{ match: /user-rejects-authentication/, consent: "deny" }        // Cancel を押す
{ match: /par-ensure-reused-request-uri-.../, firstVisit: "abandon" }  // 1 回目は離脱
```

判別材料はテスト名しかない（`/api/info/{id}` の `testName`）。

## REVIEW はスクリーンショット提出待ち。失敗ではない

「エラーページが表示されること」を目視確認するテストは callback を返さない。suite は
REVIEW のログエントリを作り `upload` に提出先 ID を入れて待つ。埋めないと **240 秒で
タイムアウト**する。`driver.mjs` の `fulfillReview()` が自動提出している。

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
