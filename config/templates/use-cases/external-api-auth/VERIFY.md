# 動作確認ガイド - External API Auth

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。

> **自動テスト**: `./verify.sh` を実行すると、以下の手順をすべて自動で検証できます。
> `./verify.sh --org my-organization` で組織名を指定できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること
- Mockoon が起動していること（`docker compose up mockoon`）

## 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-external-api-auth}"
cd config/templates/use-cases/external-api-auth

source ../../../../.env

CONFIG_DIR="../../../generated/${ORGANIZATION_NAME}"
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/public-tenant.json")
CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/public-client.json")
CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/public-client.json")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/public-client.json")
EXTERNAL_API_URL=$(jq -r '.interactions.password_verify.execution.http_request.url' "${CONFIG_DIR}/authentication-config-external-api.json")
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

echo "Server:        ${AUTHORIZATION_SERVER_URL}"
echo "Tenant ID:     ${PUBLIC_TENANT_ID}"
echo "Client ID:     ${CLIENT_ID}"
echo "External API:  ${EXTERNAL_API_URL}"
```

---

## Step 1: Mock Server Connectivity

```bash
EXTERNAL_API_URL_LOCAL=$(echo "${EXTERNAL_API_URL}" | sed 's|host\.docker\.internal|localhost|')

curl -s -X POST "${EXTERNAL_API_URL_LOCAL}" \
  -H "Content-Type: application/json" \
  -d '{"username": "test@example.com", "password": "test"}' | jq .
```

### 確認ポイント

- HTTP 200: `user_id`, `email`, `phone_number`, `member_id` がレスポンスに含まれること
- 接続できない場合: `docker compose up mockoon` で Mockoon を起動

---

## Step 2: Discovery Endpoint

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '{issuer, authorization_endpoint, token_endpoint}'
```

### 確認ポイント

- `issuer` が `${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}` と一致すること

---

## Step 3: User Registration

```bash
# 認可リクエストを開始してクッキーを取得
COOKIE_JAR=$(mktemp)
AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+profile+email&state=test")

AUTH_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTH_ID}"

# ユーザー登録
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/initial-registration" \
  -H "Content-Type: application/json" \
  -d '{"email": "extapi-user@example.com", "password": "ExternalPass123!", "name": "Test User"}' | jq .

# 登録フロー完了
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/authorize" \
  -H "Content-Type: application/json" -d '{}' > /dev/null
```

### 確認ポイント

- HTTP 200: `user.sub` が返ること
- HTTP 400 (`conflict`): 既にユーザーが存在する場合は正常

---

## Step 4: Authorization Request

```bash
STATE="test-$(date +%s)"
AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+profile+email&state=${STATE}&prompt=login")

AUTH_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTH_ID}"
```

### 確認ポイント

- HTTP 302 リダイレクト
- `id` パラメータが含まれること

---

## Step 5: External API Authentication

```bash
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{"interaction": "password_verify", "username": "extapi-user@example.com", "password": "ExternalPass123!"}' | jq .
```

### 確認ポイント

- HTTP 200
- レスポンスに `interaction: "password_verify"` が含まれること
- レスポンスに `user` または `email` が含まれること

### 認証失敗テスト

```bash
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{"interaction": "password_verify", "username": "test@example.com", "password": "invalid"}' | jq .
```

- HTTP 401: mock サーバーが `password=invalid` で 401 を返す

### 未定義 interaction テスト

```bash
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{"interaction": "nonexistent"}' | jq .
```

- HTTP 400: `error: "invalid_request"`

---

## Step 6: Authorize

```bash
AUTHORIZE_BODY=$(curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/authorize" \
  -H "Content-Type: application/json" -d '{}')

echo "${AUTHORIZE_BODY}" | jq .

CODE=$(echo "${AUTHORIZE_BODY}" | jq -r '.redirect_uri' | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
echo "Authorization Code: ${CODE}"
```

### 確認ポイント

- HTTP 200
- `redirect_uri` に `code` パラメータが含まれること

---

## Step 7: Token Exchange

```bash
TOKEN_BODY=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

echo "${TOKEN_BODY}" | jq '{token_type, expires_in}'

ACCESS_TOKEN=$(echo "${TOKEN_BODY}" | jq -r '.access_token')
ID_TOKEN=$(echo "${TOKEN_BODY}" | jq -r '.id_token')
REFRESH_TOKEN=$(echo "${TOKEN_BODY}" | jq -r '.refresh_token')
```

### ID Token の amr 確認

```bash
# base64url デコード
ID_TOKEN_B64=$(echo "${ID_TOKEN}" | cut -d'.' -f2 | tr '_-' '/+')
PAD=$((4 - ${#ID_TOKEN_B64} % 4))
[ "${PAD}" -lt 4 ] && ID_TOKEN_B64="${ID_TOKEN_B64}$(printf '%0.s=' $(seq 1 ${PAD}))"
echo "${ID_TOKEN_B64}" | base64 -d 2>/dev/null | jq '{sub, iss, amr, exp}'
```

### 確認ポイント

- `token_type: "Bearer"`
- `access_token`, `id_token`, `refresh_token` が発行されていること
- **`amr` に `"external-api"` が含まれること**

---

## Step 8: UserInfo

```bash
curl -s -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- HTTP 200
- `sub`, `email`, `name` が含まれること

---

## Step 9: Refresh Token

```bash
curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" | jq '{token_type, expires_in}'
```

### 確認ポイント

- HTTP 200
- 新しい `access_token` が発行されること

---

## サーバーログでセキュリティイベントを確認

```bash
docker compose logs idp-server-1 | grep "external_api_"
```

以下のようなイベントが出力されていることを確認:

```
event_type: external_api_password_verify_success
event_type: external_api_password_verify_failure  (認証失敗テストの場合)
event_type: external_api_authentication_failure   (未定義 interaction の場合)
```
