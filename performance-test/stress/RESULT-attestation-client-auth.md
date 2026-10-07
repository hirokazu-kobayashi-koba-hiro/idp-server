# Attestation-Based Client Authentication 性能検証

Date: 2026-10-07（初回 2026-09-15）
Target: `attest_jwt_client_auth`（draft-ietf-oauth-attestation-based-client-auth-10）／ refs #1521, #1949
Tool: k6 v1.4.1

client_secret による認証との差分を測り、増えたコストの内訳を切り分けた記録。

2026-10-07 に、PoP JWT の `jti` のリプレイ検出（#1893 / #1941）が入った状態で測り直した。
PoP JWT はリクエストごとに `jti` を変えて事前署名し、1 リクエストに 1 つずつ使う。
サーバは受け付けた PoP JWT ごとに Redis へ `SET NX EX` を 1 回行う（Redis を外した状態では測っていない）。

---

## 環境

* idp-server 2 インスタンス（load-balancer 経由）
    * `cpus: 2.0`, `memory: 2g`
    * `JAVA_TOOL_OPTIONS: -Xms512m -Xmx2g -XX:MaxGCPauseMillis=100`
* PostgreSQL primary + replica、Redis
* テストデータ: 1 テナント / 200 ユーザー / インスタンス 20〜100
* テナントの `client_attestation_pop_acceptable_window_seconds`: 600

`pg_stat_statements` は未導入のため、DB 単体のコストは trust source の差分で推定している。

---

## 1. client_secret との比較

認証方式**以外**を揃えたシナリオ同士の比較。5 VU / 20s、2 ラウンド。全 run で成功率 100%。

### トークンエンドポイント

`scenario-5-token-client-credentials` ↔ `scenario-15-token-attest-jwt-client-auth`

| | median (r1 / r2) | p95 (r1 / r2) | TPS (r1 / r2) |
|---|---|---|---|
| client_secret_post | 3.48 / 3.35 ms | 5.48 / 5.05 ms | 1313 / 1368 |
| attest_jwt_client_auth | 4.81 / 4.57 ms | 27.06 / 17.89 ms | 675 / 808 |
| **差分** | **+1.3ms** | — | — |

### CIBA BC Request

`scenario-2-bc` ↔ `scenario-16-ciba-attest-jwt-client-auth`

| | median (r1 / r2) | p95 (r1 / r2) | TPS (r1 / r2) |
|---|---|---|---|
| client_secret_post | 3.36 / 3.55 ms | 5.40 / 6.21 ms | 1306 / 1239 |
| attest_jwt_client_auth | 4.50 / 4.50 ms | 13.62 / 13.48 ms | 872 / 873 |
| **差分** | **+1.0ms** | — | — |

**クライアント認証のコストはエンドポイントに依らずほぼ一定**（median で +1.0〜1.3ms）。
2026-09-15（リプレイ検出なし）の +1.3〜1.6ms から増えていない。

p95 は attest のほうが大きく開く。後述のとおり ES256 の署名検証に連動しており、
リプレイ検出（Redis）ではない（RSA の run は p95 も client_secret と同程度）。

### 高並列（50 VU / 30s）

| 50 VU | TPS | median | p95 | 成功率 |
|---|---|---|---|---|
| client_secret_post | 1005.3 | 43.83ms | 108.61ms | 100% |
| attest_jwt_client_auth | 683.0 | 75.06ms | 157.27ms | 100% |

TPS 差は -32%。2026-09-15 は -0.8%（760.2 / 754.3）で、トークン INSERT 側が先に飽和していた。
今回は client_secret 側の上限が 1005 TPS まで上がり、attest 側（683 TPS）は前回とほぼ同じ水準で頭打ちになった。
原因は切り分けていないが、2. の結果から、ES256 の署名検証が CPU を使う分が表に出たものと見ている。

---

## 2. 内訳の切り分け（署名アルゴリズム × trust source）

`scenario-17-token-attest-matrix`、5 VU / 20s。

| alg | registered_instance_key（median / p95） | attester_jwks（median / p95） | trust source 差（median） |
|-----|------------------------|---------------|----------------|
| PS256 (RSA-2048) | 3.92 / 6.74ms | 3.80 / 6.31ms | -0.12 |
| RS256 (RSA-2048) | 3.96 / 6.70ms | 3.81 / 6.50ms | -0.15 |
| ES256 (P-256) | 4.53 / 20.89ms | 4.47 / 19.77ms | -0.06 |
| ES384 (P-384) | 6.62 / 47.52ms | 6.44 / 47.44ms | -0.18 |
| ES512 (P-521) | 10.34 / 61.42ms | 10.48 / 61.53ms | +0.14 |
| client_secret_post（参考、1. の値） | 3.35〜3.48 / 5.05〜5.48ms | — | — |

`attester_jwks` は Attester の公開鍵がクライアント設定にあるため `client_instance` を引かない。
2 つの trust source の差がそのまま DB 鍵解決のコストになる。

### 分かったこと

**効くのは alg であって DB でもリプレイ検出でもない。**
trust source の差は全て ±0.2ms 以内。`client_instance` は PK 完全一致の 1 クエリなので誤差レベル。
RS256 / PS256 は、署名検証 2 回と Redis の `SET NX EX` 1 回を含めても、client_secret との差が median で +0.6ms 以内に収まる。
一方 alg は RS256 → ES512 で **+6.4ms**。

**RSA の検証は ECDSA より速い。**
署名は RSA が遅いが検証は逆で、公開指数 65537 のべき乗 1 回で済む。
ECDSA は点のスカラー倍算が必要なため、ES256 は RS256 より median で 0.6ms 遅く、p95 は 3 倍になる。

**ECDSA は曲線サイズに急峻。**
ES256 4.53 → ES384 6.62（+2.1）→ ES512 10.34（+3.7）。P-521 は P-256 の約 2.3 倍。
p95 も 20 → 47 → 61ms と伸び、TPS は 810 → 423 → 235 まで落ちる。

**EdDSA は未対応。**
`invalid_client_attestation / unsupported key type` で 401（全リクエスト）。JOSE 層が OKP 鍵に対応していない。
Ed25519 の検証は ECDSA より速いため、対応すれば選択肢になりうる。
記録として `generate-attestation-matrix.js` の生成対象には含めてある。

---

## 3. 計測上の注意

### median を読む

外れ値が散発的に出る（今回の max は 31〜389ms。2026-09-15 は 1 秒級も出ていた）。
認証方式やエンドポイントとは相関せず、当たった run に乗る。
**avg と TPS はこの裾に引きずられるため、median で比較する。**

### baseline との絶対差には誤差を見込む

`scenario-5` の median は日によって大きく変わる（2026-09-15 は 3.75〜5.52ms、今回は 3.35〜3.48ms）。
同一ハーネス内（scenario-17 の alg 同士）の相対比較は安定しているが、
baseline との差や、日をまたいだ比較は ±1.5ms 程度の誤差を見込んで読む。

### PoP JWT は使い切り

PoP JWT はリクエストの数だけ事前署名し、1 回のテストで使い切る。
次の実行の前には必ず `--resign` する（しないと全リクエストが `jti` のリプレイとして 401 になる）。
署名から 600 秒（テナントの窓）を過ぎるとやはり 401 になるので、シナリオは始める前に経過時間を確かめて止まる。

### 飽和させない

120 VU ではこの環境が飽和し、p95 が閾値（500ms）付近で揺れて判定が安定しない。
認証方式のコストを測る目的では 5 VU 程度の低並列で median を取る。

---

## 4. 示唆

* 瞬間 TPS が数百規模までなら +1.3ms は問題にならない。トークンエンドポイントが
  CPU 律速になったときに初めて効いてくる（50 VU で TPS -32%）。
* リプレイ検出（Redis の `SET NX EX`）のコストは計測上見えない。RSA の run で、
  署名検証 2 回を含めて client_secret との差が +0.6ms 以内に収まっている。
* 削るなら **Client Attestation JWT の検証結果キャッシュ**。draft §9.2 が
  Client Attestation JWT の使い回しを想定しており（`abca-01-attester-jwks.test.js` が
  そのケースをカバー）、実装は毎リクエストでフル検証している。
  その `exp` までを TTL にキャッシュすれば、毎回必要なのは PoP の 1 回だけになる。
  ECDSA の鍵を使うクライアントでは、p95 にも効くはず。
* 鍵解決のキャッシュは効果がない（実測 ±0.2ms 以内）。

---

## 再現手順

```bash
./performance-test/scripts/register-tenants.sh -n 1
python3 ./performance-test/scripts/generate_users.py \
  --tenants-file ./performance-test/data/performance-test-tenant.json --users 200
./performance-test/scripts/import_users.sh multi_tenant_1x0k -y
cp ./performance-test/data/multi_tenant_1x0k_test_users.json \
   ./performance-test/data/performance-test-multi-tenant-users.json

export NODE_EXTRA_CA_CERTS="$(mkcert -CAROOT)/rootCA.pem"
node ./performance-test/scripts/generate-client-instances.js
node ./performance-test/scripts/generate-attestation-matrix.js --only registered_instance_key/ES256

# 1. client_secret との比較（attest の各 run の前に --resign する）
VU_COUNT=5 DURATION=20s k6 run ./performance-test/stress/scenario-5-token-client-credentials.js
node ./performance-test/scripts/generate-client-instances.js --resign
VU_COUNT=5 DURATION=20s k6 run ./performance-test/stress/scenario-15-token-attest-jwt-client-auth.js
VU_COUNT=5 DURATION=20s k6 run ./performance-test/stress/scenario-2-bc.js
node ./performance-test/scripts/generate-client-instances.js --resign
VU_COUNT=5 DURATION=20s k6 run ./performance-test/stress/scenario-16-ciba-attest-jwt-client-auth.js

# 2. 内訳の切り分け（組み合わせごとに、流す組み合わせだけ --resign する）
node ./performance-test/scripts/generate-attestation-matrix.js --resign --only attester_jwks/RS256
ALG=RS256 TRUST_SOURCE=attester_jwks VU_COUNT=5 DURATION=20s \
  k6 run ./performance-test/stress/scenario-17-token-attest-matrix.js
```

PoP JWT の `iat` は、テナントの `client_attestation_pop_acceptable_window_seconds`（性能テスト用のテナントは 600 秒）の
内側でしか受け付けられない。シナリオは「署名からの経過 + `DURATION`」が窓から 30 秒を引いた値を超えるときは
起動時に止まるので、その場合は `--resign` する。
