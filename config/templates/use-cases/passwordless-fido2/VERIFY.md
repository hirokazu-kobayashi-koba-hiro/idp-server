# 動作確認ガイド - Passwordless (FIDO2/WebAuthn)

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。

> **自動テスト**: `./verify.sh` を実行すると、パスワードフォールバックのフロー検証を自動で実行できます。
> `./verify.sh --org my-organization` で組織名を指定できます。
> FIDO2/WebAuthn 認証はブラウザ操作が必要なため、CLI では完全な検証ができません。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること

## 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-passwordless-fido2}"
cd config/templates/use-cases/passwordless-fido2

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

---

## Step 2: Authorization Request

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

## Step 3: User Registration (initial-registration, password fallback)

テストユーザーをパスワードフォールバックで登録します。

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

## Step 4: Authorize (consent grant)

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

## Step 5: Token Exchange

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
```

### 確認ポイント

- HTTP 200 が返ること
- `access_token`, `id_token` が含まれていること

---

## Step 6: UserInfo Endpoint

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

## Step 7: Token Refresh

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

## ブラウザでの FIDO2 テスト

上記の CLI テストではパスワードフォールバックのみ検証しています。
FIDO2/WebAuthn 認証を手動でテストするには、以下の URL をブラウザで開いてください:

```
${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=test
```

> FIDO2 認証にはセキュリティキーまたはプラットフォーム認証器（Touch ID 等）が必要です。

---

## チェックリスト

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | Discovery endpoint が HTTP 200 を返す | |
| 1 | issuer が正しい | |
| 2 | Authorization request が HTTP 302 を返す | |
| 3 | User registration が成功する | |
| 4 | Authorize で認可コードが取得できる | |
| 5 | Token exchange で access_token が取得できる | |
| 6 | UserInfo で sub が返る | |
| 7 | Refresh token で新しい access_token が取得できる | |
| - | (手動) ブラウザで FIDO2 認証が動作する | |
