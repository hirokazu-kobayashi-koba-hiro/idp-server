# 動作確認ガイド - Attestation-Based Client Authentication

`setup.sh` で作った環境で、2つの信頼モデルそれぞれの認証を実際に通します。

## 前提条件

- `setup.sh` 実行済み
- `jq` / `curl` / Node.js 18 以上

## 変数設定

```bash
# リポジトリ内のどこから実行しても動きます
cd "$(git rev-parse --show-toplevel)"
set -a; source .env; set +a

OUT=config/generated/attestation-based-client-auth
TPL=config/templates/use-cases/attestation-based-client-auth

TENANT_ID=$(jq -r '.tenant.id' $OUT/public-tenant.json)
ISSUER="${AUTHORIZATION_SERVER_URL}/${TENANT_ID}"
ATTESTER_CLIENT=$(jq -r '.client_id' $OUT/attester-jwks-client.json)
SELF_SIGNED_CLIENT=$(jq -r '.client_id' $OUT/self-signed-client.json)
```

---

# Phase 0: 設定の確認

```bash
$TPL/verify.sh
```

Discovery のメタデータ、Challenge エンドポイント、クライアント設定、attestation 無しの拒否をまとめて確認します。全部 ✅ になってから先に進んでください。

---

# Phase 1: attester_jwks でトークンを取得する

Client Attester がアプリを検証して Client Attestation JWT を発行するモデルです。ここでは `attester-keys.json` の秘密鍵が Attester の役を務めます。

## Step 1: 2つの JWT を生成

```bash
eval "$(node $TPL/mint-attestation.mjs \
  --mode attester \
  --client-id "$ATTESTER_CLIENT" \
  --issuer "$ISSUER" \
  --out-dir "$OUT")"
```

`eval` しているのは、このコマンドが環境変数を設定する形で出力するためです。

```
export OAUTH_CLIENT_ATTESTATION=...      # Client Attestation JWT
export OAUTH_CLIENT_ATTESTATION_POP=...  # Client Attestation PoP JWT
```

中身を見たいときは `eval` を外して `--format json` を付けてください。

このコマンドが触る鍵は2つあります。

| 鍵 | 置き場所 | 役割 |
|----|---------|------|
| Attester の秘密鍵 | `$OUT/attester-keys.json`（`setup.sh` が生成） | Client Attestation JWT に署名する。**無いとエラーで止まります** |
| Client Instance Key | `$OUT/instance-key.json` | **端末内の鍵の代役**。公開鍵が CAJ の `cnf.jwk` に入り、秘密鍵が PoP に署名する |

Client Instance Key は初回に生成して保存し、2回目以降は同じ鍵を使います。JWT のほうは `jti` と `iat` が変わるので毎回別物になります。

このモードでは Client Instance Key をどこにも登録しません。Attester が保証するからです。Phase 2 では**同じファイルの鍵を登録**します。

## Step 2: 送る JWT を確認する

送信前に中身を見ておくと、失敗したときの切り分けが早くなります。

```bash
node $TPL/mint-attestation.mjs --decode "$OAUTH_CLIENT_ATTESTATION"
node $TPL/mint-attestation.mjs --decode "$OAUTH_CLIENT_ATTESTATION_POP"
```

```
header:
{ "typ": "oauth-client-attestation+jwt", "alg": "ES256", "kid": "attester-1" }
payload:
{ "iss": "attester.example.com", "sub": "<client_id>", "iat": ..., "exp": ...,
  "cnf": { "jwk": { "kty": "EC", "crv": "P-256", "x": "...", "y": "..." } } }
iat is 0s ago
exp in 300s
```

`--decode` は**署名を検証せず**に中身を出すだけです。自前のクライアント実装が作った JWT を渡せば、リファレンスと項目ごとに突き合わせられます。`iat` / `exp` は現在時刻からの相対でも表示されるので、端末の時計ズレもここで気づけます。

### 見るべきところ

- CAJ の `cnf.jwk` と PoP の署名鍵が対になっているか（対になっていないと `invalid_client_attestation`）
- `typ` が2つとも専用の値になっているか
- `aud` が issuer で、トークンエンドポイントの URL になっていないか
- `iat is Ns ago` が ±5 分に収まっているか

## Step 3: トークンリクエスト

```bash
curl -sk -X POST "${ISSUER}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "OAuth-Client-Attestation: ${OAUTH_CLIENT_ATTESTATION}" \
  -H "OAuth-Client-Attestation-PoP: ${OAUTH_CLIENT_ATTESTATION_POP}" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=account" \
  --data-urlencode "client_id=${ATTESTER_CLIENT}" | jq '.'
```

```json
{
  "access_token": "eyJhbGciOiJFUzI1NiIsInR5cCI6ImF0K2p3dCIsImtpZCI6...",
  "scope": "account",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### 確認ポイント

- `access_token` が返る
- `client_secret` を一切送っていない
- `scope` がクライアント設定で絞られた結果になっている

失敗した場合は `error` と `error_description` が返ります。**`error_description` はどの検証で落ちたかを名指しする**ので、切り分けはここを最初に見てください。

```json
{
  "error": "use_fresh_attestation",
  "error_description": "Client authentication failed: method=attest_jwt_client_auth, client_id=..., reason=client attestation jwt is expired"
}
```

---

# Phase 2: registered_instance_key でトークンを取得する

Attester を持たず、アプリが自分で署名するモデルです。認可サーバーは**事前登録した鍵**を信頼します。

## Step 1: インスタンス鍵を登録

```bash
ADMIN_TOKEN=$(./config/scripts/get-access-token.sh \
  -u "$ADMIN_USER_EMAIL" -p "$ADMIN_USER_PASSWORD" -t "$ADMIN_TENANT_ID" \
  -e "$AUTHORIZATION_SERVER_URL" -c "$ADMIN_CLIENT_ID" -s "$ADMIN_CLIENT_SECRET")

INSTANCE_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
INSTANCE_JWK=$(node $TPL/mint-attestation.mjs --print-jwk --out-dir "$OUT")

curl -sk -X POST \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${SELF_SIGNED_CLIENT}/instances" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"${INSTANCE_ID}\",\"instance_key\":${INSTANCE_JWK}}" -w "\n%{http_code}\n"
```

`201` が返れば登録完了です。

> 管理API からの登録は `client_instance_registration_policy` に縛られません。アプリ自身が登録する経路（`POST /{tenant}/v1/client-instances`）はポリシーの対象で、`require_authentication_device` では登録済みの認証デバイスが必要になります。その経路を実際に通す手順は [EXPERIMENTS.md](./EXPERIMENTS.md) の Experiment 6 にあります。

## Step 2: 自己署名で2つの JWT を生成

```bash
eval "$(node $TPL/mint-attestation.mjs \
  --mode self-signed \
  --client-id "$SELF_SIGNED_CLIENT" \
  --issuer "$ISSUER" \
  --instance-id "$INSTANCE_ID" \
  --out-dir "$OUT")"
```

## Step 3: トークンリクエスト

```bash
curl -sk -X POST "${ISSUER}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "OAuth-Client-Attestation: ${OAUTH_CLIENT_ATTESTATION}" \
  -H "OAuth-Client-Attestation-PoP: ${OAUTH_CLIENT_ATTESTATION_POP}" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=account" \
  --data-urlencode "client_id=${SELF_SIGNED_CLIENT}" | jq '.'
```

---

# Phase 3: Challenge を載せる

## Step 1: Challenge を取得

```bash
ATTESTATION_CHALLENGE=$(curl -sk -X POST "${ISSUER}/v1/client-attestation/challenges" | jq -r '.attestation_challenge')
echo "$ATTESTATION_CHALLENGE"
```

## Step 2: challenge クレーム付きで JWT を生成

```bash
eval "$(node $TPL/mint-attestation.mjs \
  --mode attester \
  --client-id "$ATTESTER_CLIENT" \
  --issuer "$ISSUER" \
  --challenge "$ATTESTATION_CHALLENGE" \
  --out-dir "$OUT")"

node $TPL/mint-attestation.mjs --decode "$OAUTH_CLIENT_ATTESTATION_POP"
```

`--challenge` を渡すと PoP JWT の payload に `challenge` クレームが入ります。デコードで確認できます。

```
payload:
{ "aud": "...", "jti": "...", "iat": ..., "challenge": "..." }
```

Client Attestation JWT のほうは変わりません。Challenge は PoP だけに載ります。

## Step 3: トークンリクエスト

```bash
curl -sk -X POST "${ISSUER}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "OAuth-Client-Attestation: ${OAUTH_CLIENT_ATTESTATION}" \
  -H "OAuth-Client-Attestation-PoP: ${OAUTH_CLIENT_ATTESTATION_POP}" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=account" \
  --data-urlencode "client_id=${ATTESTER_CLIENT}" | jq '.'
```

## Step 4: 同じ Challenge をもう一度使う

```bash
# PoP だけ作り直す。ATTESTATION_CHALLENGE は Step 1 のまま
eval "$(node $TPL/mint-attestation.mjs \
  --mode attester --client-id "$ATTESTER_CLIENT" --issuer "$ISSUER" \
  --challenge "$ATTESTATION_CHALLENGE" --out-dir "$OUT")"

curl -sk -X POST "${ISSUER}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "OAuth-Client-Attestation: ${OAUTH_CLIENT_ATTESTATION}" \
  -H "OAuth-Client-Attestation-PoP: ${OAUTH_CLIENT_ATTESTATION_POP}" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=account" \
  --data-urlencode "client_id=${ATTESTER_CLIENT}" | jq '.'
```

2回目も `access_token` が返ります。**Challenge は単回消費ではありません** — 有効期間（既定 300 秒）のあいだ再利用できます。CIBA のポーリングのように短時間に何度もリクエストする経路のためで、1回ごとに取り直すと往復が倍になってしまうからです。

一方で PoP JWT のほうは毎回作り直します。`jti` と `iat` が変わります。

強制した場合の挙動は [EXPERIMENTS.md](./EXPERIMENTS.md) の Experiment 1 で確認できます。

---

# 2つの JWT の中身

`mint-attestation.mjs` が組み立てている内容です。自前で実装する場合はここを写してください。

## Client Attestation JWT

```json
{ "typ": "oauth-client-attestation+jwt", "alg": "ES256", "kid": "..." }
.
{ "sub": "<client_id>", "iat": ..., "exp": ..., "cnf": { "jwk": { "kty": "EC", "crv": "P-256", "x": "...", "y": "..." } } }
```

| 項目 | 要件 |
|------|------|
| `typ` | 固定。JWT ライブラリの既定（`JWT`）のままにすると弾かれます |
| `alg` | `client_attestation_signing_alg_values_supported` の範囲。`none` と HS* は拒否 |
| `kid` | **`registered_instance_key` では必須**。instance_id を入れます。サーバーはこれで登録済みの鍵を引くので、無いと必ず 401 になります。`attester_jwks` では JWKS 内の鍵選択に使います |
| `sub` | 認証する `client_id` |
| `exp` | 必須。自己署名では `exp - iat` が 24 時間以内であることも要求されます |
| `cnf.jwk` | Client Instance Key の**公開鍵**。`d` を含めると拒否されます。自己署名では登録済みの鍵と一致している必要があります |

## Client Attestation PoP JWT

```json
{ "typ": "oauth-client-attestation-pop+jwt", "alg": "ES256" }
.
{ "aud": "<issuer>", "jti": "<一意>", "iat": ..., "challenge": "<任意>" }
```

| 項目 | 要件 |
|------|------|
| 署名鍵 | Attestation JWT の `cnf.jwk` に対応する秘密鍵 |
| `aud` | 認可サーバーの issuer identifier。**トークンエンドポイントの URL ではありません** |
| `jti` | 必須。リクエストごとに一意 |
| `iat` | 必須。現在時刻から ±5 分以内 |
| `challenge` | サーバーが強制している場合は必須 |

`iss` と `exp` は PoP JWT には**定義されていません**。入れても無視されます。

> PoP はリクエストごとに作り直してください。ただし現在の実装は `jti` の**存在**しか見ておらず、同一 PoP の再送を検出する仕組み（seen-values ストア）はまだありません。鮮度は `iat` の時間窓だけが担保しています。

---

# トラブルシューティング

| 症状 | 原因 |
|------|------|
| `invalid_client_attestation` / 401 | いずれかの JWT の検証失敗。`typ` の付け忘れ、`kid` 無し（自己署名）、`cnf.jwk` と署名鍵の不一致が多い |
| `use_fresh_attestation` | Client Attestation JWT の `exp` 切れ。作り直して再送 |
| `use_attestation_challenge` | Challenge が必須なのに無い、または期限切れ。レスポンスヘッダ `OAuth-Client-Attestation-Challenge` に新しい値が入っているので、それで作り直します |
| `invalid_client`（専用コードでない） | ヘッダそのものが無い、または同じヘッダを複数送っている |
