# 設定変更 × 挙動確認 実験ガイド - Attestation-Based Client Authentication

`setup.sh` で作った環境の設定を変えて、挙動がどう変わるかを確かめます。JWT の中身と組み立て方は [VERIFY.md](./VERIFY.md) を参照してください。

## 共通準備

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

ADMIN_TOKEN=$(./config/scripts/get-access-token.sh \
  -u "$ADMIN_USER_EMAIL" -p "$ADMIN_USER_PASSWORD" -t "$ADMIN_TENANT_ID" \
  -e "$AUTHORIZATION_SERVER_URL" -c "$ADMIN_CLIENT_ID" -s "$ADMIN_CLIENT_SECRET")

AS_URL="${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authorization-server"
CLIENTS_URL="${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients"
```

設定変更は「GET → `jq` で書き換え → PUT」で行います。管理APIの更新は**全置換**なので、GET が返した内容をそのまま土台にしてください。

トークン取得は毎回この形です。

```bash
request_token() {  # $1 = client_id
  curl -sk -X POST "${ISSUER}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -H "OAuth-Client-Attestation: ${OAUTH_CLIENT_ATTESTATION}" \
    -H "OAuth-Client-Attestation-PoP: ${OAUTH_CLIENT_ATTESTATION_POP}" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "scope=account" \
    --data-urlencode "client_id=$1"
}
```

---

## Experiment 1: Challenge を必須にする

`setup.sh` の既定は「エンドポイントは公開するが強制しない」（移行期の姿）です。強制に切り替えます。

### 1. 設定変更

```bash
curl -sk "$AS_URL" -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.extension.client_attestation_challenge_required = true' > /tmp/as.json

curl -sk -X PUT "$AS_URL" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" -d @/tmp/as.json \
  | jq '{diff}'
```

```json
{
  "diff": {
    "extension.client_attestation_challenge_required": { "before": false, "after": true }
  }
}
```

管理APIの更新は全置換ですが、レスポンスの `diff` には**変わった項目だけ**が before/after で出ます。意図した1項目だけが動いたことをここで確認できます。

### 2. 挙動確認：Challenge 無しで送る

```bash
eval "$(node $TPL/mint-attestation.mjs --mode attester \
  --client-id "$ATTESTER_CLIENT" --issuer "$ISSUER" --out-dir "$OUT")"

curl -sk -D /tmp/h.txt -X POST "${ISSUER}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "OAuth-Client-Attestation: ${OAUTH_CLIENT_ATTESTATION}" \
  -H "OAuth-Client-Attestation-PoP: ${OAUTH_CLIENT_ATTESTATION_POP}" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=account" \
  --data-urlencode "client_id=${ATTESTER_CLIENT}" | jq '.'

grep -i "oauth-client-attestation-challenge" /tmp/h.txt
```

### 3. 期待結果

```json
{
  "error": "use_attestation_challenge",
  "error_description": "Client authentication failed: method=attest_jwt_client_auth, client_id=..., reason=client attestation pop jwt must contain challenge claim"
}
```

```
OAuth-Client-Attestation-Challenge: 7ttaSN9pXpWZvQgJpkAIkgkWw5rMaBPjV_AW-SuFAPA
```

ステータスは `400 Bad Request` です（`invalid_client` 系の 401 ではありません）。拒否されたレスポンス自体が**次に使う Challenge を運んで**きます（draft-11 Section 6.1 は認可サーバーに 400 とこの同梱を必須にしています）。Challenge エンドポイントを別途叩く必要はありません。

### 4. 受け取った Challenge で再送

```bash
HANDED_BACK_CHALLENGE=$(grep -i "oauth-client-attestation-challenge" /tmp/h.txt | tr -d '\r' | awk '{print $2}')

eval "$(node $TPL/mint-attestation.mjs --mode attester \
  --client-id "$ATTESTER_CLIENT" --issuer "$ISSUER" --challenge "$HANDED_BACK_CHALLENGE" --out-dir "$OUT")"

request_token "$ATTESTER_CLIENT" | jq '.'
```

→ `access_token` が返ります。

### 5. 元に戻す

```bash
curl -sk "$AS_URL" -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.extension.client_attestation_challenge_required = false' > /tmp/as.json
curl -sk -X PUT "$AS_URL" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" -d @/tmp/as.json \
  | jq '{diff}'
```

---

## Experiment 2: 登録ポリシーを外すと登録が止まる

アプリからのインスタンス登録は、`client_instance_registration_policy` が `user_bound` のクライアントにしか許されません。設定を外すと、弱いほうにフォールバックせず**チャレンジの発行から拒否**されます。

### 1. ベースライン：チャレンジを要求する

```bash
curl -sk -X POST "${ISSUER}/v1/client-instances/challenges" \
  -H "Content-Type: application/json" \
  -d "{\"client_id\":\"${SELF_SIGNED_CLIENT}\"}" | jq '.'
```

→ `challenge` と `instance_id` が返ります。`instance_id` はサーバーが決めた値で、リクエストには含めません。

### 2. 設定変更：ポリシーを外す

```bash
curl -sk "${CLIENTS_URL}/${SELF_SIGNED_CLIENT}" -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq 'del(.extension.client_instance_registration_policy)' > /tmp/c.json

curl -sk -X PUT "${CLIENTS_URL}/${SELF_SIGNED_CLIENT}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" -d @/tmp/c.json \
  | jq '{diff}'
```

### 3. 挙動確認

手順1と同じリクエストを送ります。

→ `{ "error": "invalid_request" }`

### 4. 元に戻す

```bash
curl -sk "${CLIENTS_URL}/${SELF_SIGNED_CLIENT}" -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.extension.client_instance_registration_policy = "user_bound"' > /tmp/c.json
curl -sk -X PUT "${CLIENTS_URL}/${SELF_SIGNED_CLIENT}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" -d @/tmp/c.json \
  | jq '{diff}'
```

> ポリシーは**チャレンジ発行の時点**で判定されます。登録リクエストで弾かれるのではありません。
> 拒否の理由はレスポンスに出ません（どのクライアントが登録に参加しているかを外から探れないようにするため）。理由はセキュリティイベント `client_instance_registration_failure` に残ります。

---

## Experiment 3: trust_source を取り違えるとどうなるか

`client_attestation_trust_source` は「Client Attestation JWT を誰が署名するか」を決めます。クライアントの実装と食い違うとどうなるかを見ます。

### 1. 設定変更：Attester 方式のクライアントを自己署名方式に変える

```bash
curl -sk "${CLIENTS_URL}/${ATTESTER_CLIENT}" -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.extension.client_attestation_trust_source = "registered_instance_key"' > /tmp/a.json

curl -sk -X PUT "${CLIENTS_URL}/${ATTESTER_CLIENT}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" -d @/tmp/a.json \
  | jq '{diff}'
```

### 2. 挙動確認：Attester が署名した JWT を送る

```bash
eval "$(node $TPL/mint-attestation.mjs --mode attester \
  --client-id "$ATTESTER_CLIENT" --issuer "$ISSUER" --out-dir "$OUT")"

request_token "$ATTESTER_CLIENT" | jq '.'
```

### 3. 期待結果

```json
{ "error": "invalid_client_attestation" }
```

サーバーは登録済み Client Instance を `kid` で探しにいきますが、Attester の `kid` に該当するインスタンスは存在しないため、信頼できる鍵が見つからず拒否されます。**設定と実装は必ずセットで変える**必要があります。

### 4. 元に戻す

```bash
curl -sk -X PUT "${CLIENTS_URL}/${ATTESTER_CLIENT}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" \
  -d @"${OUT}/attester-jwks-client.json" | jq '{diff}'

eval "$(node $TPL/mint-attestation.mjs --mode attester \
  --client-id "$ATTESTER_CLIENT" --issuer "$ISSUER" --out-dir "$OUT")"
request_token "$ATTESTER_CLIENT" | jq '.'
```

`setup.sh` が保存した完全な JSON を投げ直しています。GET→PUT でも戻せますが、生成物から復元するほうが確実です。

---

## Experiment 4: alg を絞って拒否を見る

### 1. 設定変更：PoP の alg から ES256 を外す

```bash
curl -sk "$AS_URL" -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.client_attestation_pop_signing_alg_values_supported = ["RS256"]' > /tmp/as.json
curl -sk -X PUT "$AS_URL" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" -d @/tmp/as.json \
  | jq '{diff}'
```

### 2. 挙動確認

```bash
eval "$(node $TPL/mint-attestation.mjs --mode attester \
  --client-id "$ATTESTER_CLIENT" --issuer "$ISSUER" --out-dir "$OUT")"
request_token "$ATTESTER_CLIENT" | jq '.'
```

→ `{ "error": "invalid_client_attestation" }`

`mint-attestation.mjs` は ES256 で署名するため、許可リストから外れて拒否されます。Discovery にも変更が反映されるので、クライアントは事前に対応 alg を知ることができます。

```bash
curl -sk "${ISSUER}/.well-known/openid-configuration" \
  | jq '.client_attestation_pop_signing_alg_values_supported'
```

### 3. 元に戻す

```bash
curl -sk "$AS_URL" -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.client_attestation_pop_signing_alg_values_supported = ["ES256","RS256"]' > /tmp/as.json
curl -sk -X PUT "$AS_URL" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" -d @/tmp/as.json \
  | jq '{diff}'
```

---

## Experiment 5: Client Attestation JWT を期限切れにする

CAJ は `exp` まで使い回せます（draft-11 Section 10.2）。期限が切れたときにクライアントが何を受け取るかを見ます。

### 1. 挙動確認：60 秒前に切れた CAJ を送る

```bash
eval "$(node $TPL/mint-attestation.mjs --mode attester \
  --client-id "$ATTESTER_CLIENT" --issuer "$ISSUER" --exp-offset -60 --out-dir "$OUT")"

request_token "$ATTESTER_CLIENT" | jq '.'
```

### 2. 期待結果

```json
{ "error": "use_fresh_attestation" }
```

`invalid_client_attestation` ではありません。「JWT が壊れている」のではなく「**取り直せば通る**」という区別が付くように、専用のコードが返ります。クライアントは Attester に新しい CAJ を要求してリトライします。

### 3. 取り直して再送

```bash
eval "$(node $TPL/mint-attestation.mjs --mode attester \
  --client-id "$ATTESTER_CLIENT" --issuer "$ISSUER" --out-dir "$OUT")"
request_token "$ATTESTER_CLIENT" | jq '.'
```

→ `access_token` が返ります。

設定を戻す必要はありません。JWT の中身を変えただけです。

---

## Experiment 6: アプリ自身にインスタンスを登録させる

[VERIFY.md](./VERIFY.md) の Phase 2 は管理API から鍵を登録しました。ここでは**アプリがログインして自分で登録する**経路を通します。実アプリのインストール時の流れです。

登録は **ID トークン**（誰か）と**プラットフォーム証明**（どの端末・鍵か）で認証され、インスタンスはログインした利用者に束縛されます。ID トークンの `nonce` に `request_hash` を入れることで、ID トークンがこの鍵の登録にしか使えないようにします。

```
1. チャレンジを取得
2. 端末で鍵を作り、request_hash を計算
3. ハイブリッドフロー（response_type=code id_token, nonce=request_hash）でログイン → code + id_token
4. id_token と鍵と証明で登録
5. 手順3の code を、登録した鍵で交換
```

### 0. 設定変更：テスト用の App Attest のルートを信頼させる

プラットフォーム証明は実機でしか作れないため、`mint-app-attest.mjs` で Apple App Attest と同じ形の証明を作ります。違いは、チェーンの先が Apple のルートではなく、ここで生成したルートになることだけです。そのルートをクライアントに信頼させます。サーバー側の検証は実機の証明と同じです。

```bash
APP_ID="TEAMID1234.com.example.selfattested"
TEST_ROOT=$(node $TPL/mint-app-attest.mjs --init-authority --out-dir "$OUT")

curl -sk "${CLIENTS_URL}/${SELF_SIGNED_CLIENT}" -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq --arg app "$APP_ID" --arg root "$TEST_ROOT" \
      '.extension.client_instance_platform_config = {ios_app_attest: {app_ids: [$app], environment: "production", override_root_certificates: [$root]}}' \
  > /tmp/s.json

curl -sk -X PUT "${CLIENTS_URL}/${SELF_SIGNED_CLIENT}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" -d @/tmp/s.json \
  | jq '{diff}'
```

`override_root_certificates` を設定すると、サーバーのログに WARN が出ます。そのルートの持ち主を信頼することになるためで、本番のクライアントには設定しません。

### 1. チャレンジを取得

```bash
CH_RESP=$(curl -sk -X POST "${ISSUER}/v1/client-instances/challenges" \
  -H "Content-Type: application/json" -d "{\"client_id\":\"${SELF_SIGNED_CLIENT}\"}")

REGISTRATION_CHALLENGE=$(echo "$CH_RESP" | jq -r '.challenge')
INSTANCE_ID=$(echo "$CH_RESP" | jq -r '.instance_id')
```

`instance_id` はサーバーが決めます。リクエストで指定するものではありません。

> ここで返る `challenge` は**登録用**で、`/v1/client-attestation/challenges` が返す `attestation_challenge` とは**別物**です。登録用は単回消費、attestation 用は有効期間内なら再利用可。変数名を分けているのはそのためです。

### 2. 端末で鍵を作り、request_hash を計算

```bash
rm -f "$OUT/instance-key.json"        # 新しい端末を模す
INSTANCE_JWK=$(node $TPL/mint-attestation.mjs --print-jwk --out-dir "$OUT")
REQUEST_HASH=$(node $TPL/mint-attestation.mjs --request-hash --challenge "$REGISTRATION_CHALLENGE" --out-dir "$OUT")
```

`request_hash` は `SHA-256(challenge のバイト列 || RFC 7638 の canonical JWK)` です。チャレンジと**登録しようとしている鍵**の両方に縛られます。

### 3. ハイブリッドフローでログインする

`nonce` に `request_hash` を指定します。クライアント認証は要りません（まだ認証に使う鍵が登録されていないため）。

```bash
REDIRECT_URI=$(jq -r '.redirect_uris[0]' $OUT/self-signed-client.json)
COOKIE_JAR=$(mktemp)

AUTH_REDIRECT=$(curl -sk -c "$COOKIE_JAR" -o /dev/null -w "%{redirect_url}" -G "${ISSUER}/v1/authorizations" \
  --data-urlencode "response_type=code id_token" \
  --data-urlencode "client_id=${SELF_SIGNED_CLIENT}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "scope=openid account" \
  --data-urlencode "state=experiment-6" \
  --data-urlencode "nonce=${REQUEST_HASH}")
AUTHORIZATION_ID=$(echo "$AUTH_REDIRECT" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')

# 利用者を作ってログインする
curl -sk -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -X POST "${ISSUER}/v1/authorizations/${AUTHORIZATION_ID}/initial-registration" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"experiment6-$(date +%s)@example.com\",\"password\":\"VerifyPass123\",\"name\":\"Experiment 6\"}" | jq '.user.sub'

AUTHZ_REDIRECT=$(curl -sk -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -X POST "${ISSUER}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" -d '{}' | jq -r '.redirect_uri')

ID_TOKEN=$(echo "$AUTHZ_REDIRECT" | sed -n 's/.*[#&]id_token=\([^&]*\).*/\1/p')
AUTHORIZATION_CODE=$(echo "$AUTHZ_REDIRECT" | sed -n 's/.*[#&]code=\([^&]*\).*/\1/p')
```

ハイブリッドフローの応答はフラグメント（`#`）で返ります。`ID_TOKEN` をデコードすると、`nonce` が `REQUEST_HASH` と同じ値になっています。

### 4. ID トークンと鍵と証明で登録

```bash
PLATFORM_EVIDENCE=$(node $TPL/mint-app-attest.mjs --evidence \
  --challenge "$REGISTRATION_CHALLENGE" --app-id "$APP_ID" --out-dir "$OUT")

curl -sk -X POST "${ISSUER}/v1/client-instances" \
  -H "Content-Type: application/json" \
  -d "{\"challenge\":\"${REGISTRATION_CHALLENGE}\",
       \"id_token\":\"${ID_TOKEN}\",
       \"client_instance_public_key\":${INSTANCE_JWK},
       \"platform_evidence\":${PLATFORM_EVIDENCE}}" -w "\n%{http_code}\n"
```

→ `201`

証明に埋め込まれるのはチャレンジ（`clientDataHash` = チャレンジのバイト列の SHA-256）で、`request_hash` ではありません。`request_hash` は ID トークンの `nonce` にだけ使います。

試しに、手順2で作った鍵とは**別の鍵**を `client_instance_public_key` にして送ると `400` になります。ID トークンの `nonce` が元の鍵を指しているためです。漏れた ID トークンを別の鍵と組み合わせても登録できない、ということです。

### 5. 手順3のコードを、登録した鍵で交換する

```bash
eval "$(node $TPL/mint-attestation.mjs --mode self-signed \
  --client-id "$SELF_SIGNED_CLIENT" --issuer "$ISSUER" \
  --instance-id "$INSTANCE_ID" --out-dir "$OUT")"

curl -sk -X POST "${ISSUER}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "OAuth-Client-Attestation: ${OAUTH_CLIENT_ATTESTATION}" \
  -H "OAuth-Client-Attestation-PoP: ${OAUTH_CLIENT_ATTESTATION_POP}" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${SELF_SIGNED_CLIENT}" | jq '.'
```

→ `access_token` と `id_token` が返ります。

ログイン1回で、登録からトークン取得までが完結しました。登録したインスタンスはログインした利用者に束縛されています。

```bash
curl -sk "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/client-instances?client_id=${SELF_SIGNED_CLIENT}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | jq '.list[] | {id, user_id, status}'
```

登録したインスタンスは管理API から削除できます。

---

## 最後に

実験で設定を触った後は、`verify.sh` で元の状態に戻っているか確認してください。

```bash
$TPL/verify.sh
```
