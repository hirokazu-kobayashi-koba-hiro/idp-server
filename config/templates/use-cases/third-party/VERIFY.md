# 動作確認ガイド - Third Party Integration

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。
このユースケースでは 2 つのパターンを検証します：

- **Phase 1**: Web Client による認可コードフロー（ユーザー登録 → ログイン → Token → UserInfo → Introspection）
- **Phase 2**: M2M Client による client_credentials フロー + Token Introspection

> **自動テスト**: `./verify.sh` を実行すると、以下の手順をすべて自動で検証できます。
> `./verify.sh --org my-organization` で組織名を指定できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること

## 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-third-party}"
cd config/templates/use-cases/third-party

source ../../../../.env

CONFIG_DIR="../../../generated/${ORGANIZATION_NAME}"
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/public-tenant.json")
WEB_CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/web-client.json")
WEB_CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/web-client.json")
WEB_REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/web-client.json")
M2M_CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/m2m-client.json")
M2M_CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/m2m-client.json")
M2M_SCOPE=$(jq -r '.scope' "${CONFIG_DIR}/m2m-client.json")
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Organization: ${ORGANIZATION_NAME}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Web Client:   ${WEB_CLIENT_ID}"
echo "M2M Client:   ${M2M_CLIENT_ID}"
```

---

# Phase 1: Authorization Code Flow (Web Client)

Web Client（Confidential）を使った認可コードフローを検証します。

## Step 1: Discovery Endpoint

### リクエスト

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `issuer` が `${TENANT_BASE}` と一致すること
- `grant_types_supported` に `authorization_code`, `client_credentials` が含まれていること
- `scopes_supported` に `api:read`, `api:write` が含まれていること

### 個別確認

```bash
# grant_types の確認
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '.grant_types_supported'
```

```bash
# カスタムスコープの確認
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '.scopes_supported'
```

---

## Step 2: Authorization Request

### リクエスト

```bash
STATE="verify-state-$(date +%s)"
SCOPE="api:read"
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

## Step 3: User Registration (initial-registration)

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

## Step 4: Authorize

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

- 認可コードが取得できること

---

## Step 5: Token Exchange

Web Client は `client_secret_basic`（`-u` オプション）で認証します。

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

- `access_token` が取得できること
- `refresh_token` が取得できること

---

## Step 6: Token Introspection (Web Client token)

Web Client が取得したアクセストークンを Introspection で検証します。

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/tokens/introspection" \
  -u "${WEB_CLIENT_ID}:${WEB_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "token=${ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- `active` が `true` であること
- `scope` に要求したスコープが含まれていること
- `client_id` が Web Client ID と一致すること

---

## Step 7: Refresh Token

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -u "${WEB_CLIENT_ID}:${WEB_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" | jq .
```

### 確認ポイント

- Refresh Token で新しい `access_token` が取得できること

---

# Phase 2: M2M (Client Credentials)

M2M Client で client_credentials グラントを検証します。

## Step 8: M2M Client Credentials Grant

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

- `access_token` が含まれていること
- `token_type` が `Bearer` であること

---

## Step 9: Token Introspection (M2M token)

M2M トークンを Introspection エンドポイントで検証します。

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/tokens/introspection" \
  -u "${M2M_CLIENT_ID}:${M2M_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "token=${M2M_ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- `active` が `true` であること
- `scope` に要求したスコープが含まれていること
- `client_id` が M2M クライアント ID と一致すること

---

## Step 10: UserInfo with M2M Token (Expected: 401)

M2M トークン（client_credentials）には subject（エンドユーザー）がないため、UserInfo エンドポイントは 401 invalid_token を返す必要があります。

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -H "Authorization: Bearer ${M2M_ACCESS_TOKEN}" \
  "${TENANT_BASE}/v1/userinfo" | jq .
```

### 確認ポイント

- HTTP 401 が返ること
- `error` が `invalid_token` であること
- 500 server_error が返らないこと（修正前の不具合動作）

---

## チェックリスト

### Phase 1: Authorization Code Flow (Web Client)

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | Discovery endpoint が HTTP 200 を返す | |
| 1 | authorization_code, client_credentials が grant_types に含まれている | |
| 1 | api:read, api:write が scopes_supported に含まれている | |
| 2 | Authorization request が HTTP 302 を返す | |
| 3 | User registration が成功する | |
| 4 | Authorization code が取得できる | |
| 5 | Token exchange で access_token, refresh_token が取得できる | |
| 6 | Token introspection で active: true が返る | |
| 7 | Refresh token で新しい access_token が取得できる | |

### Phase 2: M2M (Client Credentials)

| Step | 確認項目 | 結果 |
|------|---------|------|
| 8 | M2M client_credentials で access_token が取得できる | |
| 9 | Token introspection で active: true が返る | |
| 9 | scope が正しい | |
| 10 | M2M トークンで UserInfo が 401 invalid_token を返す | |
