# OIDF 適合性テスト

OpenID Foundation の conformance suite をローカルで動かし、idp-server に対して適合性テストを
実行するためのハーネス。`certification.openid.net` も ngrok も使わない。

関連: [Issue #1842](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1842)

![OIDF 適合性テストの実行環境](./architecture.svg)

suite は PAR / token / userinfo を自分で叩けるが、**認可エンドポイントだけはブラウザが要る**。
そこが内蔵の HtmlUnit で描画できないため、その 1 工程だけをホスト常駐の Playwright ドライバが
肩代わりする（[driver/README.md](./driver/README.md)）。

## 対応状況

| スイート | 規格 | プラン | モジュール | 状態 |
|---|---|---|---|---|
| [fapi1-advanced](./fapi1-advanced/) | FAPI 1.0 Advanced Final | `fapi1-advanced-final-test-plan` | 68 | ✅ 実行できる |
| [fapi-ciba](./fapi-ciba/) | FAPI-CIBA ID1 | `fapi-ciba-id1-test-plan` | 70 | ❌ デバイス承認の自動化が未実装 |
| [fapi2](./fapi2/) | FAPI 2.0 Security Profile Final | `fapi2-security-profile-final-test-plan` | 72 | ❌ テナント設定が存在しない |
| [oidcc](./oidcc/) | OpenID Connect Core | `oidcc-test-plan` ほか | 55 / 38 | ❌ 設定とプランの対応づけが未決 |

**`run.sh` があるディレクトリが実行できるスイート。** 未対応のものは README だけを置いてあり、
そこに「何が足りないか」を書いている。

## ディレクトリ

```
oidc-conformance-suite/
├── README.md              このファイル
├── architecture.svg       環境全体図
├── docker-compose.yaml    suite スタック（全スイート共通）
├── lib/runner.sh          ランナーコンテナ起動（全スイート共通）
├── driver/                ブラウザ操作の常駐プロセス
├── fapi1-advanced/        ← run.sh あり
├── fapi-ciba/             README のみ
├── fapi2/                 README のみ
├── oidcc/                 README のみ
└── results/               テスト結果（gitignore）
```

テスト設定 JSON はここには置かない。クライアント ID や証明書がテナント設定と対になるため、
`config/examples/<setup>/oidc-test/` に置かれている。このディレクトリはそれらを実行する
仕組みだけを持つ。

## 共通の前提

| | 用途 |
|---|---|
| Docker | idp-server / suite / テストランナーのすべて |
| Node.js | ブラウザ操作ドライバ（Playwright） |
| conformance-suite のクローン | ランナー `scripts/run-test-plan.py` を使うため |

suite 本体はビルド不要（OIDF の公開イメージを使う）が、ランナースクリプトはイメージに含まれない
ためクローンだけは要る。

```bash
git clone https://gitlab.com/openid/conformance-suite.git
export CONFORMANCE_SUITE_DIR="$PWD/conformance-suite"
```

## 共通の手順

```bash
# 1. idp-server
docker compose up -d

# 2. suite スタック
docker compose -f oidc-conformance-suite/docker-compose.yaml up -d
#    起動後 https://localhost:8443/ が開けば OK（自己署名なので警告は出る）
```

ここから先はスイートごとに違う（対象テナント、ドライバの要否、承認手順）。各ディレクトリの
README を参照。

## トラブルシュート

**suite が idp-server に到達できない**

`docker-compose.yaml` の `extra_hosts` で `api.local.test` / `mtls.api.local.test` を
`host-gateway` に向けている。idp-server 側がホストの 443 を公開していることが前提。

```bash
docker exec conformance-server curl -sk -o /dev/null -w '%{http_code}\n' \
  https://api.local.test/c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8/.well-known/openid-configuration
```

**`localhost.emobix.co.uk` が解決できない**

公開 DNS で 127.0.0.1 に解決される前提のホスト名だが、解決できない環境がある。ドライバの API
接続は `SUITE=https://localhost:8443` で回避でき、ブラウザ側は Chromium の
`--host-resolver-rules` で固定済み。suite コンテナ内では network alias で解決される。

**redirect_uri が一致しない**

クライアント設定に登録済みの `redirect_uris` は
`https://localhost.emobix.co.uk:8443/test/a/{alias}/callback`。suite の `BASE_URL` を変えた場合は
クライアント設定側も合わせる。

## 今後

CI で回すには [#1694](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1694)
（`docker-compose.ci.yml`）が前提になる。既知の失敗を `--expected-failures-file` に登録すれば、
新規 failure だけでビルドを落とせる。
