# 動作確認ガイド - MFA (Password + Email OTP)

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。
このユースケースでは 2 つのフェーズ（ユーザー登録 + MFA ログイン）を検証します。

> **自動テスト**: `./verify.sh` を実行すると、以下の手順をすべて自動で検証できます。
> `./verify.sh --org my-organization` で組織名を指定できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること

## 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-mfa-email}"
cd config/templates/use-cases/mfa-email

source ../../../../.env

CONFIG_DIR="../../../generated/${ORGANIZATION_NAME}"
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/public-tenant.json")
CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/public-client.json")
CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/public-client.json")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/public-client.json")
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

# Management API 用（Phase 2 で検証コード取得に使用）
ORG_ID=$(jq -r '.organization.id' "${CONFIG_DIR}/onboarding.json")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/onboarding.json")
ORG_ADMIN_EMAIL=$(jq -r '.user.email' "${CONFIG_DIR}/onboarding.json")
ORG_ADMIN_PASSWORD=$(jq -r '.user.raw_password' "${CONFIG_DIR}/onboarding.json")
ORG_CLIENT_ID=$(jq -r '.client.client_id' "${CONFIG_DIR}/onboarding.json")
ORG_CLIENT_SECRET=$(jq -r '.client.client_secret' "${CONFIG_DIR}/onboarding.json")

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Organization: ${ORGANIZATION_NAME}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Client ID:    ${CLIENT_ID}"
echo "Redirect URI: ${REDIRECT_URI}"
```

> **注意**: Management API 用の変数は、generated ディレクトリ内のファイル名が環境によって異なる場合があります。
> `ls ${CONFIG_DIR}/` で実際のファイル一覧を確認し、適宜ファイル名を調整してください。

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

## Step 4: Registration Flow (authorize -> token)

登録フローを完了させます。

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

```bash
TOKEN_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
echo "Access Token: ${ACCESS_TOKEN:0:20}..."
```

### 確認ポイント

- 認可コードが取得できること
- トークン交換で `access_token` が取得できること

---

## Step 4b: UserInfo 確認

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

### 確認ポイント

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

**1. 管理者トークンを取得**

```bash
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
echo "Org Access Token: ${ORG_ACCESS_TOKEN:0:20}..."
```

**2. Authentication Transaction を取得**

```bash
TRANSACTION_RESPONSE=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-transactions?authorization_id=${AUTHORIZATION_ID2}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")

TRANSACTION_ID=$(echo "${TRANSACTION_RESPONSE}" | jq -r '.list[0].id')
echo "Transaction ID: ${TRANSACTION_ID}"
```

**3. 検証コードを取得**

```bash
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

## Step 9: MFA Flow Complete (authorize -> token)

MFA 認証後のフローを完了させます。

### リクエスト

```bash
AUTHORIZE_RESPONSE2=$(curl -s \
  -b "${COOKIE_JAR2}" -c "${COOKIE_JAR2}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI2=$(echo "${AUTHORIZE_RESPONSE2}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE2=$(echo "${AUTHZ_REDIRECT_URI2}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
echo "Authorization Code: ${AUTHORIZATION_CODE2}"
```

```bash
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
ID_TOKEN2=$(echo "${TOKEN_RESPONSE2}" | jq -r '.id_token')
REFRESH_TOKEN2=$(echo "${TOKEN_RESPONSE2}" | jq -r '.refresh_token')
```

### 確認ポイント

- 認可コードが取得できること
- トークン交換で `access_token` が取得できること

---

## Step 9b: ID Token デコード（amr 確認）

MFA が正しく実行されたことを ID Token の `amr` (Authentication Methods References) クレームで確認します。

```bash
echo "${ID_TOKEN2}" | cut -d'.' -f2 | python3 -c "
import sys, base64, json
p = sys.stdin.read().strip()
p += '=' * (4 - len(p) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(p)), indent=2, ensure_ascii=False))
"
```

### 確認ポイント

- `amr` に `"email"` と `"password"` の両方が含まれていること（MFA が正しく実行された証拠）
- `sub` が存在すること

---

## Step 9c: UserInfo 確認

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN2}" | jq .
```

### 確認ポイント

- UserInfo で `sub` が返ること

---

## Step 9d: Refresh Token

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN2}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" | jq .
```

### 確認ポイント

- Refresh Token で新しい `access_token` が取得できること

---

# Phase 3: Password Change & Reset

> **verify.sh との対応**: Phase 3 は verify.sh の Step 8（パスワード変更）、Step 9（パスワードリセット）、Step 10（リセット後ログイン確認）に対応しています。

## Step 10: Password Change（ユーザー自身によるパスワード変更）

認証済みユーザーが自身のパスワードを変更します。

### リクエスト

```bash
NEW_PASSWORD="NewVerifyPass456"

curl -s \
  -X POST "${TENANT_BASE}/v1/me/password/change" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN2}" \
  -d "{
    \"current_password\": \"${TEST_PASSWORD}\",
    \"new_password\": \"${NEW_PASSWORD}\"
  }" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `"message": "Password changed successfully."` が返ること

---

## Step 11: Password Reset（メール認証によるパスワードリセット）

パスワードを忘れた場合のリセットフローです。`password:reset` スコープで認可リクエストを行い、メール認証のみで認証を完了した後、新パスワードを設定します。

### 前提

- テナントの `scopes_supported` に `password:reset` が含まれていること
- クライアントの `scope` に `password:reset` が含まれていること
- 認証ポリシーに `password:reset` スコープ用のメール認証のみポリシー（`password_reset_email_only`）が設定されていること

### リクエスト

**1. password:reset スコープで認可リクエスト**

```bash
COOKIE_JAR3=$(mktemp)
RESET_STATE="verify-reset-$(date +%s)"

RESET_AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR3}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+password%3Areset&state=${RESET_STATE}&prompt=login")

RESET_AUTH_ID=$(echo "${RESET_AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${RESET_AUTH_ID}"
```

**2. Email OTP チャレンジ**

```bash
curl -s -b "${COOKIE_JAR3}" -c "${COOKIE_JAR3}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${RESET_AUTH_ID}/email-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${TEST_EMAIL}\",
    \"template\": \"authentication\"
  }" | jq .
```

**3. Management API で検証コードを取得**

```bash
RESET_TRANSACTION_ID=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-transactions?authorization_id=${RESET_AUTH_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq -r '.list[0].id')

RESET_VERIFICATION_CODE=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-interactions/${RESET_TRANSACTION_ID}/email-authentication-challenge" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq -r '.payload.verification_code')

echo "Verification Code: ${RESET_VERIFICATION_CODE}"
```

**4. Email OTP 検証**

```bash
curl -s -b "${COOKIE_JAR3}" -c "${COOKIE_JAR3}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${RESET_AUTH_ID}/email-authentication" \
  -H "Content-Type: application/json" \
  -d "{
    \"verification_code\": \"${RESET_VERIFICATION_CODE}\"
  }" | jq .
```

**5. 認可 → トークン取得**

```bash
RESET_AUTHORIZE_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR3}" -c "${COOKIE_JAR3}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${RESET_AUTH_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

RESET_CODE=$(echo "${RESET_AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri' | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')

RESET_TOKEN_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${RESET_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

RESET_ACCESS_TOKEN=$(echo "${RESET_TOKEN_RESPONSE}" | jq -r '.access_token')
RESET_SCOPE=$(echo "${RESET_TOKEN_RESPONSE}" | jq -r '.scope')
echo "Scope: ${RESET_SCOPE}"
```

**6. パスワードリセット**

```bash
RESET_PASSWORD="ResetVerifyPass789"

curl -s \
  -X POST "${TENANT_BASE}/v1/me/password/reset" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${RESET_ACCESS_TOKEN}" \
  -d "{
    \"new_password\": \"${RESET_PASSWORD}\"
  }" | jq .
```

### 確認ポイント

- トークンの `scope` に `password:reset` が含まれていること
- HTTP 200 が返り、パスワードリセットが成功すること
- リセット後のパスワードでログインできること

---

## Step 12: Verify Login with Reset Password

リセット後のパスワードでログインできることを確認します。

### リクエスト

```bash
COOKIE_JAR4=$(mktemp)
LOGIN_STATE="verify-login-$(date +%s)"

LOGIN_REDIRECT=$(curl -s -c "${COOKIE_JAR4}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+profile+email&state=${LOGIN_STATE}&prompt=login")

LOGIN_AUTH_ID=$(echo "${LOGIN_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')

curl -s -b "${COOKIE_JAR4}" -c "${COOKIE_JAR4}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${LOGIN_AUTH_ID}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${TEST_EMAIL}\",
    \"password\": \"${RESET_PASSWORD}\"
  }" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- リセット前のパスワードではログインできないこと

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
| 9b | ID Token の amr に email と password が含まれる | |
| 9c | UserInfo で sub が返る | |
| 9d | Refresh token が動作する | |

### Phase 3: Password Change & Reset

| Step | 確認項目 | 結果 |
|------|---------|------|
| 10 | Password change が成功する | |
| 11 | password:reset スコープでメール認証のみで認可できる | |
| 11 | password:reset スコープ付きトークンが取得できる | |
| 11 | Password reset が成功する | |
| 12 | リセット後のパスワードでログインできる | |
