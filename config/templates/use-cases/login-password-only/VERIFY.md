# 動作確認ガイド - Login (Password Only)

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。

> **自動テスト**: `./verify.sh` を実行すると、以下の手順をすべて自動で検証できます。
> `./verify.sh --org my-organization` で組織名を指定できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること

## 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-login-password-only}"
cd config/templates/use-cases/login-password-only

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

## Step 1: Discovery Endpoint

OpenID Connect の Discovery エンドポイントが正しく応答するか確認します。

### リクエスト

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `issuer` が `${TENANT_BASE}` と一致すること
- `authorization_endpoint`, `token_endpoint`, `userinfo_endpoint` が含まれていること

### レスポンス例

```json
{
  "issuer": "https://idp.example.com/tenant-id",
  "authorization_endpoint": "https://idp.example.com/tenant-id/v1/authorizations",
  "token_endpoint": "https://idp.example.com/tenant-id/v1/tokens",
  "userinfo_endpoint": "https://idp.example.com/tenant-id/v1/userinfo",
  "grant_types_supported": ["authorization_code", "refresh_token"],
  "scopes_supported": ["openid", "profile", "email"]
}
```

---

## Step 2: Authorization Request

認可リクエストを送信し、ログイン画面へのリダイレクトを確認します。

### リクエスト

```bash
STATE="verify-state-$(date +%s)"
SCOPE="openid profile email"
COOKIE_JAR=$(mktemp)

AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE}")

AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTHORIZATION_ID}"
```

### 確認ポイント

- HTTP 302 リダイレクトが返ること
- Authorization ID が取得できること

---

## Step 3: User Registration (initial-registration)

テストユーザーを新規登録します。

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
- ユーザーが正常に登録されたこと

---

## Step 4: Authorize (consent grant)

同意を許可し、認可コードを取得します。

### リクエスト

```bash
AUTHORIZE_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
RETURNED_STATE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]state=\([^&#]*\).*/\1/p')

echo "Authorization Code: ${AUTHORIZATION_CODE}"
echo "State matches: $([ "${RETURNED_STATE}" = "${STATE}" ] && echo "yes" || echo "no")"
```

### 確認ポイント

- HTTP 200 が返ること
- `redirect_uri` に `code` パラメータが含まれていること
- `state` パラメータが送信時の値と一致すること

---

## Step 5: Token Exchange

認可コードをトークンに交換します。

### リクエスト

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

ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
REFRESH_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.refresh_token')

echo "Access Token:  ${ACCESS_TOKEN:0:20}..."
echo "Refresh Token: ${REFRESH_TOKEN:0:20}..."
```

### 確認ポイント

- HTTP 200 が返ること
- `access_token` が含まれていること
- `id_token` が含まれていること
- `token_type` が `Bearer` であること

### レスポンス例

```json
{
  "access_token": "eyJhbGci...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "id_token": "eyJhbGci...",
  "refresh_token": "eyJhbGci..."
}
```

---

## Step 6: UserInfo Endpoint

アクセストークンを使って UserInfo エンドポイントを呼び出します。

### リクエスト

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `sub` が含まれていること（トークンが有効であることの確認）
- `email` が登録時のメールアドレスと一致すること
- `name` が登録時の名前と一致すること

### レスポンス例

```json
{
  "sub": "user-uuid",
  "email": "verify-1234567890@example.com",
  "name": "Verify User"
}
```

---

## Step 7: Token Refresh

リフレッシュトークンを使って新しいアクセストークンを取得します。

### リクエスト

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- 新しい `access_token` が含まれていること

---

## チェックリスト

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | Discovery endpoint が HTTP 200 を返す | |
| 1 | issuer が正しい | |
| 2 | Authorization request が HTTP 302 を返す | |
| 2 | Authorization ID が取得できる | |
| 3 | User registration が成功する | |
| 4 | Authorize で認可コードが取得できる | |
| 4 | state が一致する | |
| 5 | Token exchange で access_token が取得できる | |
| 5 | id_token が含まれている | |
| 6 | UserInfo で sub が返る | |
| 6 | email, name が正しい | |
| 7 | Refresh token で新しい access_token が取得できる | |
