# 動作確認ガイド - MFA (Password + Email OTP)

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。
このユースケースでは 2 つのフェーズ（ユーザー登録 + MFA ログイン）を検証します。

> **自動テスト**: `./verify.sh` を実行すると、以下の手順をすべて自動で検証できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること

## 変数設定

```bash
cd config/examples/mfa-email

source ../../../.env

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' public-tenant-request.json)
CLIENT_ID=$(jq -r '.client_id' client-request.json)
CLIENT_SECRET=$(jq -r '.client_secret' client-request.json)
REDIRECT_URI=$(jq -r '.redirect_uris[0]' client-request.json)
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

# Management API 用（Phase 2 で検証コード取得に使用）
ORG_ID=$(jq -r '.organization.id' onboarding-request.json)
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' onboarding-request.json)
ORG_ADMIN_EMAIL=$(jq -r '.user.email' onboarding-request.json)
ORG_ADMIN_PASSWORD=$(jq -r '.user.raw_password' onboarding-request.json)
ORG_CLIENT_ID=$(jq -r '.client.client_id' onboarding-request.json)
ORG_CLIENT_SECRET=$(jq -r '.client.client_secret' onboarding-request.json)

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Client ID:    ${CLIENT_ID}"
echo "Redirect URI: ${REDIRECT_URI}"
```

---

# Phase 1: User Registration (initial-registration)

まず新規ユーザーを登録し、基本フローが動作することを確認します。

## Step 1: Discovery Endpoint

### リクエスト

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `issuer` が `${TENANT_BASE}` と一致すること

---

## Step 2: Authorization Request (for registration)

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

## Step 4: Registration Flow (authorize -> token -> userinfo)

登録フローを完了させます。

### リクエスト

```bash
# Authorize
AUTHORIZE_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
echo "Authorization Code: ${AUTHORIZATION_CODE}"

# Token Exchange
TOKEN_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

REG_ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
echo "Access Token: ${REG_ACCESS_TOKEN:0:20}..."

# UserInfo
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${REG_ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- 認可コードが取得できること
- トークン交換で `access_token` が取得できること
- UserInfo で `sub` が返ること

---

# Phase 2: MFA Login (Email OTP + Password)

登録済みユーザーで MFA ログインを検証します。新しいセッションで開始します。

## Step 5: Authorization Request (for MFA login)

### リクエスト

```bash
# 新しい Cookie Jar でフレッシュセッション
COOKIE_JAR2=$(mktemp)
STATE2="verify-mfa-$(date +%s)"

AUTH_REDIRECT2=$(curl -s -c "${COOKIE_JAR2}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE2}")

AUTHORIZATION_ID2=$(echo "${AUTH_REDIRECT2}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTHORIZATION_ID2}"
```

### 確認ポイント

- HTTP 302 リダイレクトが返ること
- Authorization ID が取得できること

---

## Step 6: Email OTP Challenge (1st factor)

Email OTP チャレンジを送信します（no-action モード：ローカル環境用）。

### リクエスト

```bash
curl -s -b "${COOKIE_JAR2}" -c "${COOKIE_JAR2}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/email-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${TEST_EMAIL}\",
    \"template\": \"authentication\"
  }" | jq .
```

### 確認ポイント

- HTTP 200 または 201 が返ること

---

## Step 6b: Management API で検証コードを取得

ローカル環境では no-action モードのため、Management API から検証コードを取得します。

### リクエスト

```bash
# 1. 管理者トークンを取得
ORG_TOKEN_RESPONSE=$(curl -s -X POST \
  "${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ORG_ADMIN_EMAIL}" \
  --data-urlencode "password=${ORG_ADMIN_PASSWORD}" \
  --data-urlencode "client_id=${ORG_CLIENT_ID}" \
  --data-urlencode "client_secret=${ORG_CLIENT_SECRET}" \
  --data-urlencode "scope=openid profile email management")

ORG_ACCESS_TOKEN=$(echo "${ORG_TOKEN_RESPONSE}" | jq -r '.access_token')

# 2. Authentication Transaction を取得
TRANSACTION_RESPONSE=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-transactions?authorization_id=${AUTHORIZATION_ID2}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")

TRANSACTION_ID=$(echo "${TRANSACTION_RESPONSE}" | jq -r '.list[0].id')
echo "Transaction ID: ${TRANSACTION_ID}"

# 3. 検証コードを取得
INTERACTION_RESPONSE=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-interactions/${TRANSACTION_ID}/email-authentication-challenge" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")

VERIFICATION_CODE=$(echo "${INTERACTION_RESPONSE}" | jq -r '.payload.verification_code')
echo "Verification Code: ${VERIFICATION_CODE}"
```

### 確認ポイント

- 管理者トークンが取得できること
- Transaction ID が取得できること
- 検証コードが取得できること（6桁の数字）

---

## Step 7: Email OTP Verification (1st factor complete)

取得した検証コードで Email OTP を検証します。

### リクエスト

```bash
curl -s -b "${COOKIE_JAR2}" -c "${COOKIE_JAR2}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/email-authentication" \
  -H "Content-Type: application/json" \
  -d "{
    \"verification_code\": \"${VERIFICATION_CODE}\"
  }" | jq .
```

### 確認ポイント

- HTTP 200 または 201 が返ること
- Email OTP 検証が成功すること

---

## Step 8: Password Authentication (2nd factor)

パスワードで第 2 要素の認証を行います。

### リクエスト

```bash
curl -s -b "${COOKIE_JAR2}" -c "${COOKIE_JAR2}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${TEST_EMAIL}\",
    \"password\": \"${TEST_PASSWORD}\"
  }" | jq .
```

### 確認ポイント

- HTTP 200 または 201 が返ること
- パスワード認証が成功すること

---

## Step 9: MFA Flow Complete (authorize -> token -> userinfo)

MFA 認証後のフローを完了させます。

### リクエスト

```bash
# Authorize
AUTHORIZE_RESPONSE2=$(curl -s \
  -b "${COOKIE_JAR2}" -c "${COOKIE_JAR2}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI2=$(echo "${AUTHORIZE_RESPONSE2}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE2=$(echo "${AUTHZ_REDIRECT_URI2}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
echo "Authorization Code: ${AUTHORIZATION_CODE2}"

# Token Exchange
TOKEN_RESPONSE2=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE2}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

echo "${TOKEN_RESPONSE2}" | jq .

ACCESS_TOKEN2=$(echo "${TOKEN_RESPONSE2}" | jq -r '.access_token')
REFRESH_TOKEN2=$(echo "${TOKEN_RESPONSE2}" | jq -r '.refresh_token')

# UserInfo
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN2}" | jq .

# Refresh Token
curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN2}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" | jq .
```

### 確認ポイント

- 認可コードが取得できること
- トークン交換で `access_token` が取得できること
- UserInfo で `sub` が返ること
- Refresh Token で新しい `access_token` が取得できること

---

## チェックリスト

### Phase 1: User Registration

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | Discovery endpoint が HTTP 200 を返す | |
| 1 | issuer が正しい | |
| 2 | Authorization request が HTTP 302 を返す | |
| 3 | User registration が成功する | |
| 4 | Registration flow で token が取得できる | |
| 4 | UserInfo で sub が返る | |

### Phase 2: MFA Login

| Step | 確認項目 | 結果 |
|------|---------|------|
| 5 | MFA authorization request が HTTP 302 を返す | |
| 6 | Email OTP challenge が成功する | |
| 6b | Management API で検証コードが取得できる | |
| 7 | Email OTP verification が成功する | |
| 8 | Password authentication が成功する | |
| 9 | MFA flow で token が取得できる | |
| 9 | UserInfo で sub が返る | |
| 9 | Refresh token が動作する | |
