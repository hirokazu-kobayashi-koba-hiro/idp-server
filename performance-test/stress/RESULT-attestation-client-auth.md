# Attestation-Based Client Authentication 性能検証

Date: 2026-09-15
Target: `attest_jwt_client_auth`（draft-ietf-oauth-attestation-based-client-auth-10）／ refs #1521
Tool: k6 v1.4.1

client_secret による認証との差分を測り、増えたコストの内訳を切り分けた記録。

---

## 環境

* idp-server 2 インスタンス（load-balancer 経由）
    * `cpus: 2.0`, `memory: 2g`
    * `JAVA_TOOL_OPTIONS: -Xms512m -Xmx2g -XX:MaxGCPauseMillis=100`
* PostgreSQL primary + replica、Redis
* テストデータ: 1 テナント / 200 ユーザー / インスタンス 20〜50

`pg_stat_statements` は未導入のため、DB 単体のコストは trust source の差分で推定している。

---

## 1. client_secret との比較

認証方式**以外**を揃えたシナリオ同士の比較。5 VU / 20s、2 ラウンド。

### トークンエンドポイント

`scenario-5-token-client-credentials` ↔ `scenario-15-token-attest-jwt-client-auth`

| | median (r1 / r2) | p95 | 成功率 |
|---|---|---|---|
| client_secret_post | 5.52 / 5.50 ms | 9.32 / 9.55 ms | 100% |
| attest_jwt_client_auth | 7.02 / 7.11 ms | 11.65 / 12.02 ms | 100% |
| **差分** | **+1.6ms** | +2.4ms | — |

### CIBA BC Request

`scenario-2-bc` ↔ `scenario-16-ciba-attest-jwt-client-auth`

| | median (r1 / r2) | p95 | TPS | 成功率 |
|---|---|---|---|---|
| client_secret_post | 5.42 / 5.36 ms | 8.94 / 9.27 ms | 841 / 835 | 100% |
| attest_jwt_client_auth | 6.67 / 6.68 ms | 11.38 / 11.24 ms | 672 / 673 | 100% |
| **差分** | **+1.3ms** | +2.2ms | -20% | — |

**クライアント認証のコストはエンドポイントに依らずほぼ一定**（+1.3〜1.6ms）。
CIBA で TPS が -20% なのは、母数のリクエストが 6ms 前後と軽いため。

高並列（50 VU / 30s）ではトークン INSERT 側が先に飽和し、TPS 差は -0.8% に収まった。

| 50 VU | TPS | median | p95 | 成功率 |
|---|---|---|---|---|
| client_secret_post | 760.2 | 26.56ms | 188.73ms | 99.76% |
| attest_jwt_client_auth | 754.3 | 36.53ms | 223.83ms | 100% |

---

## 2. 内訳の切り分け（署名アルゴリズム × trust source）

`scenario-17-token-attest-matrix`、5 VU / 20s、median。

| alg | registered_instance_key | attester_jwks | trust source 差 |
|-----|------------------------|---------------|----------------|
| PS256 (RSA-2048) | 4.12ms | 3.94ms | -0.18 |
| RS256 (RSA-2048) | 4.16ms | 4.19ms | +0.03 |
| ES256 (P-256) | 6.67ms | 6.10ms | -0.57 |
| ES384 (P-384) | 8.71ms | 8.65ms | -0.06 |
| ES512 (P-521) | 11.78ms | 12.20ms | +0.42 |
| client_secret_post（参考） | 5.38ms | — | — |

`attester_jwks` は Attester の公開鍵がクライアント設定にあるため `client_instance` を引かない。
2 つの trust source の差がそのまま DB 鍵解決のコストになる。

### 分かったこと

**効くのは alg であって DB ではない。**
trust source の差は全て ±0.6ms 以内（別途 ES256 のみで 2 ラウンド取った際は +0.16ms）。
`client_instance` は PK 完全一致の 1 クエリなので誤差レベル。
一方 alg は RS256 → ES512 で **+7.6ms**。鍵解決のキャッシュより alg 選定のほうが効く。

**RSA の検証は ECDSA より速い。**
署名は RSA が遅いが検証は逆で、公開指数 65537 のべき乗 1 回で済む。
ECDSA は点のスカラー倍算が必要なため、RS256 は ES256 より 2.5ms 速い。

**ECDSA は曲線サイズに急峻。**
ES256 6.67 → ES384 8.71（+2.0）→ ES512 11.78（+3.1）。P-521 は P-256 の約 1.8 倍。

**EdDSA は未対応。**
`invalid_client_attestation / unsupported key type` で 401。JOSE 層が OKP 鍵に対応していない。
Ed25519 の検証は ECDSA より速いため、対応すれば選択肢になりうる。
記録として `generate-attestation-matrix.js` の生成対象には含めてある。

---

## 3. 計測上の注意

### median を読む

1 秒級の外れ値が散発的に出る。認証方式やエンドポイントとは相関せず、当たった run に乗る。

| 出た run | max |
|---|---|
| token / client_secret_post | 1.70s, 2.98s, 1.02s |
| token / attest (ES256) | 541ms |
| token / attest (RS256, PS256) | 1.08s, 944ms |
| CIBA（全 run） | 出ず（max 44〜70ms） |

当初これを client_secret 固有と見たが、RSA の run でも同様に出たため取り下げた。
チェックポイントか GC と考えられる。**avg と TPS はこの裾に引きずられるため、median で比較する。**

### baseline との絶対差には誤差を見込む

`scenario-5` の median はセッション中 3.75〜5.52ms とブレた。
同一ハーネス内（scenario-17 の alg 同士）の相対比較は安定しているが、
baseline との差は ±1.5ms 程度の誤差を見込んで読む。
RSA が baseline より速く見えているのはこの範囲に収まる。

### 飽和させない

120 VU ではこの環境が飽和し、p95 が閾値（500ms）付近で揺れて判定が安定しない。
認証方式のコストを測る目的では 5 VU 程度の低並列で median を取る。

---

## 4. 示唆

* 本番の瞬間 TPS（130 規模）では +1.3ms は問題にならない。トークンエンドポイントが
  CPU 律速になったときに初めて効いてくる。
* 削るなら **Client Attestation JWT の検証結果キャッシュ**。draft §9.2 が
  Client Attestation JWT の使い回しを想定しており（`abca-01-attester-jwks.test.js` が
  そのケースをカバー）、実装は毎リクエストでフル検証している。
  その `exp` までを TTL にキャッシュすれば、毎回必要なのは PoP の 1 回だけになる。
* 鍵解決のキャッシュは効果がない（実測 ±0.6ms 以内）。

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
node ./performance-test/scripts/generate-attestation-matrix.js

# 1. client_secret との比較
VU_COUNT=5 DURATION=20s k6 run ./performance-test/stress/scenario-5-token-client-credentials.js
VU_COUNT=5 DURATION=20s k6 run ./performance-test/stress/scenario-15-token-attest-jwt-client-auth.js
VU_COUNT=5 DURATION=20s k6 run ./performance-test/stress/scenario-2-bc.js
VU_COUNT=5 DURATION=20s k6 run ./performance-test/stress/scenario-16-ciba-attest-jwt-client-auth.js

# 2. 内訳の切り分け（実行前に --resign すること）
node ./performance-test/scripts/generate-attestation-matrix.js --resign
ALG=RS256 TRUST_SOURCE=attester_jwks VU_COUNT=5 DURATION=20s \
  k6 run ./performance-test/stress/scenario-17-token-attest-matrix.js
```

事前署名した JWT は 5 分で期限切れになる（PoP JWT の `iat` 許容窓が ±5 分）。
シナリオ側が 240 秒超のプールを検出して起動時に停止するので、その場合は `--resign` する。
