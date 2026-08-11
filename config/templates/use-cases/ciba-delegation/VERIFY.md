# 動作確認ガイド - CIBA 委譲

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。

> **自動テスト**: `./verify.sh` を実行すると、Phase 1（ユーザー登録 + FIDO-UAF デバイス登録）と Phase 2（CIBA フロー）の全手順を自動で検証できます。
> `./verify.sh --org my-organization` で組織名を指定できます。

## 前提条件

- `setup.sh` が正常に完了していること
- Mockoon FIDO-UAF モックサーバーが起動していること（`docker compose up -d mockoon`）
- `curl`, `jq`, `python3`, `openssl` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること

## 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-ciba-delegation}"
cd config/templates/use-cases/ciba-delegation

source ../../../../.env

CONFIG_DIR="../../../generated/${ORGANIZATION_NAME}"
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/public-tenant.json")
CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/public-client.json")
CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/public-client.json")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/public-client.json")
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Organization: ${ORGANIZATION_NAME}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Client ID:    ${CLIENT_ID}"
echo "Redirect URI: ${REDIRECT_URI}"
```

---

## Phase 1: ユーザー登録 + FIDO-UAF デバイス登録

### Step 1: Discovery Endpoint

OpenID Connect の Discovery エンドポイントが正しく応答するか確認します。

#### リクエスト

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること
- `issuer` が `${TENANT_BASE}` と一致すること
- `backchannel_authentication_endpoint` が含まれていること
- `grant_types_supported` に `urn:openid:params:grant-type:ciba` が含まれていること
- `grant_types_supported` に `urn:ietf:params:oauth:grant-type:jwt-bearer` が含まれていること
- `backchannel_token_delivery_modes_supported` に `poll` が含まれていること

---

### Step 2: Authorization Request

認可リクエストを送信し、ログイン画面へのリダイレクトを確認します。

#### リクエスト

```bash
STATE="verify-state-$(date +%s)"
SCOPE="openid profile email claims:authentication_devices"
COOKIE_JAR=$(mktemp)

AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE}")

AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTHORIZATION_ID}"
```

#### 確認ポイント

- HTTP 302 リダイレクトが返ること
- Authorization ID が取得できること

---

### Step 3: User Registration (initial-registration)

テストユーザーを新規登録します。

#### リクエスト

```bash
TEST_EMAIL="verify-$(date +%s)@example.com"
TEST_PASSWORD="VerifyPass123"
TEST_NAME="Verify User"

REGISTRATION=$(curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/initial-registration" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${TEST_EMAIL}\",
    \"password\": \"${TEST_PASSWORD}\",
    \"name\": \"${TEST_NAME}\"
  }")

echo "${REGISTRATION}" | jq .

# Step 11 と Step 19 の device_secret_jwt で使います
USER_SUB=$(echo "${REGISTRATION}" | jq -r '.user.sub')
echo "User Sub: ${USER_SUB}"
```

#### 確認ポイント

- HTTP 200 が返ること
- ユーザーが正常に登録されたこと
- `user.sub` が取得できること

---

### Step 4: FIDO-UAF Registration Challenge

FIDO-UAF 登録チャレンジを取得します。

#### リクエスト

```bash
FIDO_CHALLENGE=$(curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/fido-uaf-registration-challenge" \
  -H "Content-Type: application/json" \
  -d '{"app_name": "CIBA Test App", "platform": "iOS", "os": "iOS 18.0", "model": "iPhone15"}')

echo "${FIDO_CHALLENGE}" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること
- チャレンジデータが含まれていること

---

### Step 5: FIDO-UAF Registration Complete

FIDO-UAF 登録を完了し、device_secret を取得します。

#### リクエスト

```bash
FIDO_REG=$(curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/fido-uaf-registration" \
  -H "Content-Type: application/json" \
  -d '{"uafResponse": [{"assertionScheme": "UAFV1TLV", "assertion": "mock_assertion_data"}]}')

echo "${FIDO_REG}" | jq .

DEVICE_ID=$(echo "${FIDO_REG}" | jq -r '.device_id')
DEVICE_SECRET=$(echo "${FIDO_REG}" | jq -r '.device_secret')
DEVICE_SECRET_JWT_ISSUER=$(echo "${FIDO_REG}" | jq -r '.device_secret_jwt_issuer')

echo "Device ID: ${DEVICE_ID}"
echo "Device Secret: ${DEVICE_SECRET:0:10}..."
echo "JWT Issuer: ${DEVICE_SECRET_JWT_ISSUER}"
```

#### 確認ポイント

- HTTP 200 が返ること
- `device_id` が含まれていること
- `device_secret` が含まれていること
- `device_secret_algorithm` が `HS256` であること
- `device_secret_jwt_issuer` が含まれていること

---

### Step 6: Authorize (consent grant)

同意を許可し、認可コードを取得します。

#### リクエスト

```bash
AUTHORIZE_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')

echo "Authorization Code: ${AUTHORIZATION_CODE}"
```

#### 確認ポイント

- HTTP 200 が返ること
- `redirect_uri` に `code` パラメータが含まれていること

---

### Step 7: Token Exchange

認可コードをトークンに交換します。これによりユーザーとデバイスがDBに永続化されます。

#### リクエスト

```bash
TOKEN_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

echo "${TOKEN_RESPONSE}" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること
- `access_token` が含まれていること
- `id_token` が含まれていること

---

## Phase 2: CIBA Flow

### Step 8: UserInfo Verification (authentication_devices)

トークン交換後、UserInfo エンドポイントで `authentication_devices` が含まれることを確認します。

#### リクエスト

```bash
ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')

curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること
- `authentication_devices` 配列が含まれ、1件以上のデバイスが登録されていること
- デバイスの `id` が Step 5 で取得した `device_id` と一致すること
- `credential_payload`（device_secret）が UserInfo に露出していないこと（セキュリティ）

---

### Step 9: Backchannel Authentication Request

CIBAバックチャネル認証リクエストを送信します。`login_hint` にデバイスIDを指定します。

#### リクエスト

```bash
CIBA_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/backchannel/authentications" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "scope=openid profile email" \
  --data-urlencode "login_hint=device:${DEVICE_ID},idp:idp-server" \
  --data-urlencode "binding_message=CIBA-VERIFY" \
  --data-urlencode "user_code=${TEST_PASSWORD}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

echo "${CIBA_RESPONSE}" | jq .

AUTH_REQ_ID=$(echo "${CIBA_RESPONSE}" | jq -r '.auth_req_id')
echo "Auth Request ID: ${AUTH_REQ_ID}"
```

#### 確認ポイント

- HTTP 200 が返ること
- `auth_req_id` が含まれていること
- `expires_in` が設定値と一致すること
- `interval` が設定値と一致すること

---

### Step 10: CIBA Token Polling（認証前）

認証が完了する前にトークンエンドポイントをポーリングし、`authorization_pending` エラーが返ることを確認します。
これにより poll モードが正しく動作していることを検証します。

#### リクエスト

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=urn:openid:params:grant-type:ciba" \
  --data-urlencode "auth_req_id=${AUTH_REQ_ID}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" | jq .
```

#### 確認ポイント

- `error` が `authorization_pending` であること
- デバイス認証が完了するまでこのエラーが返り続けること
- `interval` 秒（デフォルト: 5秒）以上の間隔でポーリングすること（`slow_down` エラー回避）

---

### Step 11: Get Authentication Transaction (device_secret_jwt)

device_secret で HS256 JWT を作成し、デバイス認証トランザクションを取得します。

#### リクエスト

```bash
# HS256 JWT を作成
base64url_encode() {
  openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

ISSUER="${TENANT_BASE}"
NOW=$(date +%s)
EXP=$((NOW + 3600))
JTI="jti-$(date +%s)-${RANDOM}"

JWT_PAYLOAD=$(jq -n \
  --arg iss "${DEVICE_SECRET_JWT_ISSUER}" \
  --arg sub "${USER_SUB}" \
  --arg aud "${ISSUER}" \
  --arg jti "${JTI}" \
  --argjson exp "${EXP}" \
  --argjson iat "${NOW}" \
  '{iss: $iss, sub: $sub, aud: $aud, jti: $jti, exp: $exp, iat: $iat}')

HEADER='{"alg":"HS256","typ":"JWT"}'
HEADER_B64=$(echo -n "${HEADER}" | base64url_encode)
PAYLOAD_B64=$(echo -n "${JWT_PAYLOAD}" | base64url_encode)
UNSIGNED="${HEADER_B64}.${PAYLOAD_B64}"
SIGNATURE=$(echo -n "${UNSIGNED}" | openssl dgst -sha256 -hmac "${DEVICE_SECRET}" -binary | base64url_encode)
DEVICE_JWT="${UNSIGNED}.${SIGNATURE}"
echo $DEVICE_JWT
```

```shell
# デバイス認証トランザクション取得
TRANSACTION_RESPONSE=$(curl -s -X GET \
  "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications?attributes.auth_req_id=${AUTH_REQ_ID}" \
  -H "Authorization: Bearer ${DEVICE_JWT}")

echo "${TRANSACTION_RESPONSE}" | jq .
TRANSACTION_ID=$(echo "${TRANSACTION_RESPONSE}" | jq -r '.list[0].id')
echo "Transaction ID: ${TRANSACTION_ID}"
```

#### 確認ポイント

- HTTP 200 が返ること（JWT認証成功）
- 認証トランザクションが取得できること
- `context` にスコープ情報が含まれていること

---

### Step 12: FIDO-UAF Authentication Challenge

デバイス側で FIDO-UAF 認証チャレンジを取得します。

#### リクエスト

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/fido-uaf-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d "{\"device_id\": \"${DEVICE_ID}\"}" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること

---

### Step 13: FIDO-UAF Authentication Complete

FIDO-UAF 認証を完了します。

#### リクエスト

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/fido-uaf-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"device_id\": \"${DEVICE_ID}\", \"uafResponse\": [{\"assertionScheme\": \"UAFV1TLV\", \"assertion\": \"mock_assertion_data\"}]}" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること

---

### Step 14: CIBA Token（認証後）

CIBAトークンエンドポイントでトークンを取得します。

#### リクエスト

```bash
CIBA_TOKEN_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=urn:openid:params:grant-type:ciba" \
  --data-urlencode "auth_req_id=${AUTH_REQ_ID}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

echo "${CIBA_TOKEN_RESPONSE}" | jq .

CIBA_ACCESS_TOKEN=$(echo "${CIBA_TOKEN_RESPONSE}" | jq -r '.access_token')
echo "CIBA Access Token: ${CIBA_ACCESS_TOKEN:0:20}..."
```

#### 確認ポイント

- 認証前: `authorization_pending` エラーが返ること
- 認証後: HTTP 200 で `access_token` が取得できること
- `id_token` が含まれていること

---

### Step 15: UserInfo Verification

CIBAトークンで UserInfo エンドポイントを呼び出します。

#### リクエスト

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${CIBA_ACCESS_TOKEN}" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること
- `sub` が含まれていること
- `email` が登録時のメールアドレスと一致すること

---

## Phase 3: パスワード認証 + CIBA 委譲

**Phase 1 を Step 7 まで完了していることが前提です。** 承認する人が居ないと委譲は完了しません。

ユーザーが永続化されるのは **Step 6 の authorize** の時点です。初期登録（Step 3）や FIDO-UAF 登録（Step 5）を終えただけでは、まだユーザーは保存されていません。Step 6 を飛ばすと `ciba_start` が `unknown_user_id` を返します。

Phase 1 の Step 3 で設定した `TEST_EMAIL` / `TEST_PASSWORD` をそのまま使います。

### この構成について

**委譲は、ユーザーを特定してから使います。** このテンプレートはパスワード認証を1要素目に置き、CIBA 委譲を2要素目にしています。

委譲だけで認証を完結させる設定（`user_resolve` が1要素目としてユーザーを解決する形）は、このテンプレートでは提供しません。委譲先の userinfo から解決したユーザーが `provider_id` 違いの**別レコードとして新規作成される**ためです。委譲先が自分自身であるこのテンプレートでは、同一人物・同一メールアドレスのユーザーが2件できてしまいます。

2要素目として使う場合は `identity_match_field` による照合が働き、既存ユーザーとの一致を検証するだけで新規作成は行いません。

### Step 16: 認可リクエスト

```bash
COOKIE_JAR=$(mktemp)

AUTH_LOCATION=$(curl -s -c "${COOKIE_JAR}" -o /dev/null -w '%{redirect_url}' \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=delegation&acr_values=urn:idp:acr:password-ciba")

AUTH_ID=$(echo "${AUTH_LOCATION}" | sed -n 's/.*[?&]id=\([^&]*\).*/\1/p')
echo "Authorization ID: ${AUTH_ID}"

curl -s -b "${COOKIE_JAR}" "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/view-data" \
  | jq '.authentication_policy | {description, priority}'
```

期待結果:

```json
{ "description": "password_and_ciba_delegation", "priority": 210 }
```

`acr_values` を付けないと既定ポリシー（パスワード / 初期登録）になり、委譲は行われません。

### Step 17: パスワード認証（1要素目・ユーザーの特定）

```bash
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" -X POST \
  "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${TEST_EMAIL}\",\"password\":\"${TEST_PASSWORD}\"}" | jq '.user.sub'
```

ここで返る `sub` が Phase 1 の `USER_SUB` と一致します。以降の委譲は、この人物であることの確認に使われます。

### Step 18: CIBA 開始（委譲）

```bash
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" -X POST \
  "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"interaction\":\"ciba_start\",\"login_hint\":\"email:${TEST_EMAIL},idp:idp-server\"}" | jq '.'
```

期待結果（`external_status_code` が 200）。`auth_req_id` はレスポンスに含まれず、サーバー側に保持されます。

### Step 19: ポーリング（承認前）

```bash
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" -X POST \
  "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{"interaction":"ciba_poll"}' | jq '.'
```

期待結果（HTTP 400。CIBA 側のエラーがそのまま透過される）:

```json
{ "external_status_code": 400, "error": "authorization_pending" }
```

サインイン画面はこの `error` を見て、承認待ちなのか本当の失敗なのかを判断します。

### Step 20: デバイスで承認

**Phase 1 の Step 5 で登録したデバイス**（`DEVICE_ID` / `DEVICE_SECRET` / `DEVICE_SECRET_JWT_ISSUER`）で承認します。`ciba_start` が作ったトランザクションは `login_hint` で指したユーザーのデバイス宛なので、別のデバイスでは `list` が空になります。

#### 20-1. device_secret_jwt を作成

```bash
base64url_encode() {
  openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

NOW=$(date +%s)
JWT_PAYLOAD=$(jq -n \
  --arg iss "${DEVICE_SECRET_JWT_ISSUER}" \
  --arg sub "${USER_SUB}" \
  --arg aud "${TENANT_BASE}" \
  --arg jti "jti-$(date +%s)-${RANDOM}" \
  --argjson exp "$((NOW + 3600))" \
  --argjson iat "${NOW}" \
  '{iss: $iss, sub: $sub, aud: $aud, jti: $jti, exp: $exp, iat: $iat}')

HEADER_B64=$(echo -n '{"alg":"HS256","typ":"JWT"}' | base64url_encode)
PAYLOAD_B64=$(echo -n "${JWT_PAYLOAD}" | base64url_encode)
UNSIGNED="${HEADER_B64}.${PAYLOAD_B64}"
SIGNATURE=$(echo -n "${UNSIGNED}" | openssl dgst -sha256 -hmac "${DEVICE_SECRET}" -binary | base64url_encode)
DEVICE_JWT="${UNSIGNED}.${SIGNATURE}"
```

#### 20-2. 認証トランザクションを取得

Phase 2 では `attributes.auth_req_id` で絞りましたが、**委譲では `auth_req_id` がサーバー側に保持され手元に無い**ため、`flow=ciba` で絞ります。

```bash
TRANSACTION_RESPONSE=$(curl -s -X GET \
  "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications?flow=ciba" \
  -H "Authorization: Bearer ${DEVICE_JWT}")

echo "${TRANSACTION_RESPONSE}" | jq .
TRANSACTION_ID=$(echo "${TRANSACTION_RESPONSE}" | jq -r '.list[0].id')
echo "Transaction ID: ${TRANSACTION_ID}"
```

`list` が空の場合は、承認しようとしているデバイスが `login_hint` で指したユーザーのものか確認してください。

#### 20-3. FIDO-UAF 認証

```bash
curl -s -X POST \
  "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/fido-uaf-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d "{\"device_id\": \"${DEVICE_ID}\"}" | jq .

curl -s -X POST \
  "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/fido-uaf-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"device_id\": \"${DEVICE_ID}\", \"uafResponse\": [{\"assertionScheme\": \"UAFV1TLV\", \"assertion\": \"mock_assertion_data\"}]}" | jq .
```

#### 確認ポイント

- 20-2 が HTTP 200 で、`list` に1件以上あること
- 20-3 の2つのリクエストがどちらも HTTP 200 を返すこと

> Phase 1 を `verify.sh` で済ませた場合に限り、`./ciba-device-auth.sh` で 20-1〜20-3 を代替できます。同スクリプトは `config/generated/${ORGANIZATION_NAME}/device-credentials.json` を読みますが、このファイルを書くのは `verify.sh` だけです。手順書どおり手で登録した場合は前回のデバイスを承認しようとして空振りします。

### Step 21: ポーリング（承認後）

```bash
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" -X POST \
  "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{"interaction":"ciba_poll"}' | jq '.'
```

期待結果（`external_status_code` が 200）。アクセストークンはサーバー側に保持され、レスポンスには含まれません。

### Step 22: ユーザー照合

```bash
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" -X POST \
  "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{"interaction":"userinfo"}' | jq '.'
```

#### 確認ポイント

- `user.sub` が **Step 17 と一致すること**

`identity_match_field` により、デバイスで承認した人がパスワードを入力した人と同一かが検証されます。異なるユーザーで承認した場合は `user_identity_mismatch` で失敗します。

### Step 23: 認証状態の確認

```bash
curl -s -b "${COOKIE_JAR}" "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/authentication-status" | jq '.'
```

期待結果:

```json
{
  "status": "success",
  "interaction_results": {
    "password-authentication": { "success_count": 1 },
    "external-api-authentication": { "success_count": 3, "failure_count": 1 }
  }
}
```

`failure_count` が 1 なのは、Step 19 の承認前ポーリングが非2xx だったためです。**この用途のポリシーに `failure_conditions` を置くとここでロックします。**

### Step 24: 認可コードの取得

```bash
AUTHORIZE_RESPONSE=$(curl -s -b "${COOKIE_JAR}" -X POST \
  "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/authorize" \
  -H "Content-Type: application/json" -d '{}')

echo "${AUTHORIZE_RESPONSE}" | jq '.redirect_uri'
AUTHORIZATION_CODE=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri' | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
echo "Authorization Code: ${AUTHORIZATION_CODE}"
```

### Step 25: トークン交換

```bash
TOKEN_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=${AUTHORIZATION_CODE}" \
  -d "redirect_uri=${REDIRECT_URI}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}")

echo "${TOKEN_RESPONSE}" | jq .
ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
```

#### 確認ポイント

- `access_token` と `id_token` が返ること

### Step 26: UserInfo

```bash
curl -s "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

#### 確認ポイント

- `email` が `TEST_EMAIL` と一致すること
- **`sub` が Phase 1 の `USER_SUB` と一致すること**

パスワード認証でユーザーを特定してから委譲しているため、委譲を通しても subject は変わりません。

---

## チェックリスト

### 設定前提（setup.sh 実行前に確認）

| # | 確認項目 | 結果 |
|---|---------|------|
| 1 | `custom_claims_scope_mapping: true` が `authorization_server.extension` に設定済み | |
| 2 | `claims_supported` に `authentication_devices` が含まれている | |
| 3 | `scopes_supported` に `claims:authentication_devices` が含まれている | |

> `custom_claims_scope_mapping` が未設定だと `claims:*` スコープが機能せず、UserInfo/ID Token にカスタムクレーム（`authentication_devices` 等）が含まれません。

### Phase 1: ユーザー登録 + FIDO-UAF デバイス登録

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | Discovery endpoint が HTTP 200 を返す | |
| 1 | backchannel_authentication_endpoint が含まれている | |
| 1 | grant_types_supported に ciba が含まれている | |
| 1 | grant_types_supported に jwt-bearer が含まれている | |
| 1 | backchannel_token_delivery_modes_supported に poll が含まれている | |
| 2 | Authorization request が HTTP 302 を返す | |
| 3 | User registration が成功する | |
| 4 | FIDO-UAF registration challenge が成功する | |
| 5 | FIDO-UAF registration で device_id, device_secret が取得できる | |
| 6 | Authorize で認可コードが取得できる（**この時点でユーザーが永続化される**） | |
| 7 | Token exchange で access_token が取得できる | |
| 8 | UserInfo に authentication_devices が含まれる | |
| 8 | device_id が Step 5 と一致する | |
| 8 | credential_payload が露出していない（セキュリティ） | |

### Phase 2: CIBA Flow

| Step | 確認項目 | 結果 |
|------|---------|------|
| 9 | Backchannel auth request で auth_req_id が取得できる | |
| 10 | 認証前ポーリングで authorization_pending が返る | |
| 11 | device_secret_jwt で認証トランザクションが取得できる | |
| 12 | FIDO-UAF authentication challenge が成功する | |
| 13 | FIDO-UAF authentication が成功する | |
| 14 | CIBA token（認証後）で access_token が取得できる | |
| 15 | UserInfo で sub, email が返る | |

### Phase 3: パスワード認証 + CIBA 委譲

| Step | 確認項目 | 結果 |
|------|---------|------|
| 16 | 適用ポリシーが `password_and_ciba_delegation` である | |
| 17 | パスワード認証が成功し、sub が Phase 1 の USER_SUB と一致する | |
| 18 | ciba_start が external_status_code 200 を返す | |
| 18 | レスポンスに auth_req_id が含まれていない | |
| 19 | 承認前ポーリングで error=authorization_pending が透過される | |
| 20-2 | 認証トランザクションが取得できる（list が1件以上） | |
| 20-3 | FIDO-UAF challenge / authentication がどちらも 200 | |
| 21 | 承認後ポーリングが external_status_code 200 を返す | |
| 21 | レスポンスに access_token が含まれていない | |
| 22 | userinfo の user.sub が Step 17 と一致する（同一ユーザー照合） | |
| 23 | authentication-status が success になる | |
| 23 | external-api-authentication の success_count が 3、failure_count が 1 以上 | |
| 24 | authorize で認可コードが取得できる | |
| 25 | トークン交換で access_token / id_token が取得できる | |
| 26 | UserInfo の sub が Phase 1 の USER_SUB と一致する | |
