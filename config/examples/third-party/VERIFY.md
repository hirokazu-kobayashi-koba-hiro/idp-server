# 動作確認ガイド - Third Party Integration

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。
このユースケースでは M2M（client_credentials）と Web Client（authorization_code）の両方を検証します。

> **自動テスト**: `./verify.sh` を実行すると、以下の手順をすべて自動で検証できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること

## 変数設定

```bash
cd config/examples/third-party

source ../../../.env

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' public-tenant-request.json)
WEB_CLIENT_ID=$(jq -r '.client_id' client-web-request.json)
WEB_CLIENT_SECRET=$(jq -r '.client_secret' client-web-request.json)
WEB_REDIRECT_URI=$(jq -r '.redirect_uris[0]' client-web-request.json)
M2M_CLIENT_ID=$(jq -r '.client_id' client-m2m-request.json)
M2M_CLIENT_SECRET=$(jq -r '.client_secret' client-m2m-request.json)
M2M_SCOPE=$(jq -r '.scope' client-m2m-request.json)
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Web Client:   ${WEB_CLIENT_ID}"
echo "M2M Client:   ${M2M_CLIENT_ID}"
```

---

## Step 1: Discovery Endpoint (Extended)

Discovery エンドポイントで client_credentials グラントタイプとカスタムスコープを確認します。

### リクエスト

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `issuer` が `${TENANT_BASE}` と一致すること
- `grant_types_supported` に `client_credentials` が含まれていること
- `scopes_supported` に `api:read`, `api:write` が含まれていること

### 個別確認

```bash
# client_credentials グラントタイプの確認
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '.grant_types_supported | map(select(. == "client_credentials"))'

# カスタムスコープの確認
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '.scopes_supported | map(select(. == "api:read" or . == "api:write"))'
```

---

## Step 2: M2M Client Credentials Grant

M2M クライアントで client_credentials グラントを実行します。

### リクエスト

```bash
M2M_TOKEN_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -u "${M2M_CLIENT_ID}:${M2M_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=${M2M_SCOPE}")

echo "${M2M_TOKEN_RESPONSE}" | jq .

M2M_ACCESS_TOKEN=$(echo "${M2M_TOKEN_RESPONSE}" | jq -r '.access_token')
```

### 確認ポイント

- HTTP 200 が返ること
- `access_token` が含まれていること
- `token_type` が `Bearer` であること

### レスポンス例

```json
{
  "access_token": "eyJhbGci...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

---

## Step 3: Token Introspection (M2M token)

M2M トークンを Introspection エンドポイントで検証します。

### リクエスト

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/tokens/introspection" \
  -u "${M2M_CLIENT_ID}:${M2M_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "token=${M2M_ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `active` が `true` であること
- `scope` に要求したスコープが含まれていること
- `client_id` が M2M クライアント ID と一致すること

### レスポンス例

```json
{
  "active": true,
  "scope": "api:read api:write",
  "client_id": "m2m-client-id",
  "token_type": "Bearer",
  "exp": 1234567890
}
```

---

## Step 4: Web Client Authorization Request

Web クライアントで認可コードフローを開始します。

### リクエスト

```bash
STATE="verify-state-$(date +%s)"
SCOPE="openid profile email api:read"
COOKIE_JAR=$(mktemp)

AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${WEB_CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${WEB_REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE}")

AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTHORIZATION_ID}"
```

### 確認ポイント

- HTTP 302 リダイレクトが返ること
- Authorization ID が取得できること

---

## Step 5: User Registration (initial-registration)

### リクエスト

```bash
TEST_EMAIL="verify-$(date +%s)@example.com"
TEST_PASSWORD="VerifyPass123"
TEST_NAME="Verify User"

curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/initial-registration" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${TEST_EMAIL}\",
    \"password\": \"${TEST_PASSWORD}\",
    \"name\": \"${TEST_NAME}\"
  }" | jq .
```

### 確認ポイント

- HTTP 200 または 201 が返ること

---

## Step 6: Authorize (consent grant)

### リクエスト

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

### 確認ポイント

- HTTP 200 が返ること
- `redirect_uri` に `code` パラメータが含まれていること

---

## Step 7: Token Exchange (Web Client uses client_secret_basic)

Web クライアントは `client_secret_basic`（HTTP Basic 認証）でトークン交換を行います。

### リクエスト

```bash
TOKEN_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -u "${WEB_CLIENT_ID}:${WEB_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${WEB_REDIRECT_URI}")

echo "${TOKEN_RESPONSE}" | jq .

ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
REFRESH_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.refresh_token')
```

### 確認ポイント

- HTTP 200 が返ること
- `access_token`, `id_token` が含まれていること

> **注意**: M2M クライアント（Step 2）と異なり、Web クライアントは `-u` フラグで Basic 認証を使用しています。

---

## Step 8: UserInfo Endpoint

### リクエスト

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `sub`, `email`, `name` が含まれていること

---

## Step 9: Token Refresh (Web Client)

### リクエスト

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -u "${WEB_CLIENT_ID}:${WEB_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- 新しい `access_token` が含まれていること

---

## チェックリスト

### M2M (Machine-to-Machine)

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | Discovery endpoint が HTTP 200 を返す | |
| 1 | client_credentials が grant_types_supported に含まれている | |
| 1 | api:read, api:write が scopes_supported に含まれている | |
| 2 | M2M client_credentials で access_token が取得できる | |
| 3 | Token introspection で active: true が返る | |
| 3 | scope が正しい | |

### Web Client (Authorization Code Flow)

| Step | 確認項目 | 結果 |
|------|---------|------|
| 4 | Authorization request が HTTP 302 を返す | |
| 5 | User registration が成功する | |
| 6 | Authorize で認可コードが取得できる | |
| 7 | Token exchange で access_token が取得できる（Basic 認証） | |
| 8 | UserInfo で sub が返る | |
| 9 | Refresh token で新しい access_token が取得できる | |
