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

# Phase 4: Contact Verification & Change（セルフサービス 連絡先）

認証済みユーザーが自分の連絡先を確認・変更するフローです（`POST /{tenant-id}/v1/me/{channel}/...`）。

このユースケースは **ローカル生成モード**（`authentication-config-email.json` の
`execution.details.function: "no_action"`）で、idp-server がコードを生成・保持・照合します。
外部サービスにコード生成・検証を委譲するパターンの手順は `../mfa-sms/VERIFY.md` の Phase 4 を参照してください。

## 前提

- `public-tenant-template.json` の `scopes_supported` と `public-client-template.json` の `scope` に
  `email:change` が含まれていること
- `authentication-config-email.json` の `templates` に `email_verify` / `email_change` /
  `email_change_notice` が定義されていること（未定義でも動きますが、既定の「確認コード」文面に
  フォールバックします）
- テナントの `identity_unique_key_type` が `EMAIL_OR_EXTERNAL_USER_ID` であること
  → **email の変更はログイン識別子の移動**、phone の変更は属性のみ、に分類されます
- no-action モードでは実際のメールは送信されません。検証コードは Management API から取得します

## Step 13: email:change 付きトークンの取得

確認（`/verification`）は `openid` で足りますが、変更（`/change`）には `email:change` が要ります。
1 本のトークンで両方を試すため、スコープを広げて MFA を 1 回通します。

### リクエスト

**1. スコープを拡張して認可リクエスト**

```bash
CONTACT_SCOPE="openid profile email email:change"
COOKIE_JAR5=$(mktemp)
CONTACT_STATE="verify-contact-$(date +%s)"

CONTACT_REDIRECT=$(curl -s -c "${COOKIE_JAR5}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${CONTACT_SCOPE}" | sed 's/:/%3A/g; s/ /+/g')&state=${CONTACT_STATE}&prompt=login")

CONTACT_AUTH_ID=$(echo "${CONTACT_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${CONTACT_AUTH_ID}"
```

**2. Email OTP を通す（Step 6 〜 6b と同じ手順）**

```bash
curl -s -b "${COOKIE_JAR5}" -c "${COOKIE_JAR5}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${CONTACT_AUTH_ID}/email-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"${TEST_EMAIL}\"}" > /dev/null

CONTACT_TXN_ID=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-transactions?authorization_id=${CONTACT_AUTH_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq -r '.list[0].id')

CONTACT_OTP=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-interactions/${CONTACT_TXN_ID}/email-authentication-challenge" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq -r '.payload.verification_code')

curl -s -b "${COOKIE_JAR5}" -c "${COOKIE_JAR5}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${CONTACT_AUTH_ID}/email-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"verification_code\": \"${CONTACT_OTP}\"}" > /dev/null
```

**3. パスワード認証（Phase 3 でリセット済みのパスワードを使用）**

```bash
curl -s -b "${COOKIE_JAR5}" -c "${COOKIE_JAR5}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${CONTACT_AUTH_ID}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${TEST_EMAIL}\",
    \"password\": \"${RESET_PASSWORD}\"
  }" > /dev/null
```

**4. authorize -> token**

```bash
CONTACT_CODE=$(curl -s -b "${COOKIE_JAR5}" -c "${COOKIE_JAR5}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${CONTACT_AUTH_ID}/authorize" \
  -H "Content-Type: application/json" -d '{}' \
  | jq -r '.redirect_uri' | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')

CONTACT_TOKEN_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${CONTACT_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

CONTACT_TOKEN=$(echo "${CONTACT_TOKEN_RESPONSE}" | jq -r '.access_token')
echo "${CONTACT_TOKEN_RESPONSE}" | jq -r '.scope'
```

### 確認ポイント

- `access_token` が取得できること
- レスポンスの `scope` に `email:change` が含まれること
  （含まれない場合はテナントの `scopes_supported` とクライアントの `scope` を確認）

---

## Step 14: Email の確認（`openid` で足りること）

アカウントに登録済みのアドレスが到達可能であることを確認します。**宛先はアカウントの現在値に固定**され、
リクエストボディを取りません。

### リクエスト

**1. コードを要求**

```bash
VERIFY_CHALLENGE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/me/email/verification" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d '{"new_value": "attacker@example.com"}')

echo "${VERIFY_CHALLENGE}" | jq .
VERIFY_CHALLENGE_ID=$(echo "${VERIFY_CHALLENGE}" | jq -r '.id')
```

**2. Management API でチャレンジを取得**

```bash
USER_ID=$(curl -s "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" | jq -r '.sub')

curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/contact-verification-challenges?user_id=${USER_ID}&operation=email_verify" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.list[0]'

VERIFY_CODE=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/contact-verification-challenges/${VERIFY_CHALLENGE_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq -r '.verification_code')
echo "Verification Code: ${VERIFY_CODE}"
```

**3. 確定**

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/me/email/verification/${VERIFY_CHALLENGE_ID}/verify" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d "{\"verification_code\": \"${VERIFY_CODE}\"}" | jq .
```

### 確認ポイント

- 1 で HTTP 200 と `id` が返ること
- Management API の `target_value` が **`${TEST_EMAIL}`（現在値）** であること
  — ボディに入れた `attacker@example.com` は**無視される**
- `delivery` が `"internal"` であること（ローカル生成モード）
- `verification_code` が 6 桁の数字、`attempts` が `0`、`expired` が `false` であること
- 3 で HTTP 200 が返り、`user.email_verified` が `true` になること

---

## Step 15: Email の変更（ログイン識別子の移動）

`email:change` スコープが要ります。確定すると `email` が置き換わり、`identity_unique_key_type` が
`EMAIL_OR_EXTERNAL_USER_ID` なので **`preferred_username`（ログイン識別子）も追従** します。

### リクエスト

```bash
NEW_EMAIL="verify-changed-$(date +%s)@example.com"

CHANGE_CHALLENGE_ID=$(curl -s \
  -X POST "${TENANT_BASE}/v1/me/email/change" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d "{\"new_value\": \"${NEW_EMAIL}\"}" | jq -r '.id')

CHANGE_CODE=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/contact-verification-challenges/${CHANGE_CHALLENGE_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq -r '.verification_code')

curl -s \
  -X POST "${TENANT_BASE}/v1/me/email/change/${CHANGE_CHALLENGE_ID}/verify" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d "{\"verification_code\": \"${CHANGE_CODE}\"}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- レスポンスの `user.email` が `${NEW_EMAIL}` であること
- レスポンスの `user.preferred_username` も `${NEW_EMAIL}` に追従していること
- コードの宛先は**新しいアドレス**、変更通知（`email_change_notice`）の宛先は**旧アドレス**であること
  — no-action モードでは実際には送信されないため、送信内容はアプリケーションログで確認します
- 確定後に同じチャレンジ ID を再送すると HTTP 400 になること（使い捨て）

---

## Step 16: 新しいアドレスでログインできること

識別子が移動したことの確認です。

### リクエスト

```bash
COOKIE_JAR6=$(mktemp)
NEW_LOGIN_STATE="verify-newlogin-$(date +%s)"

NEW_LOGIN_AUTH_ID=$(curl -s -c "${COOKIE_JAR6}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+profile+email&state=${NEW_LOGIN_STATE}&prompt=login" \
  | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')

curl -s -b "${COOKIE_JAR6}" -c "${COOKIE_JAR6}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${NEW_LOGIN_AUTH_ID}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${NEW_EMAIL}\",
    \"password\": \"${RESET_PASSWORD}\"
  }" | jq .
```

### 確認ポイント

- 新しいアドレスで HTTP 200 が返ること
- 旧アドレス（`${TEST_EMAIL}`）では認証できないこと

---

## Step 17: 拒否されること（負の確認）

### リクエスト

```bash
# 1. スコープ不足（ACCESS_TOKEN2 は openid profile email のみ）
echo -n "insufficient scope: "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/email/change" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN2}" \
  -d '{"new_value": "someone-else@example.com"}'

# 2. 現在値と同じ値への変更
echo -n "same value: "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/email/change" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d "{\"new_value\": \"${NEW_EMAIL}\"}"

# 3. 不正な形式
echo -n "malformed: "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/email/change" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d '{"new_value": 12345}'

# 4. 存在しない（＝他人の）チャレンジ ID
echo -n "foreign challenge: "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/email/change/00000000-0000-0000-0000-000000000000/verify" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d '{"verification_code": "123456"}'

# 5. クールダウン中の再送（resend_cooldown_seconds: 60）
echo -n "1st send: "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/email/verification" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}"
echo -n "2nd send within cooldown: "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/email/verification" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}"

# 6. 本テナントには sms 認証設定が無いため、phone 系は到達しない
echo -n "phone (no sms config): "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/phone/verification" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}"
```

### 確認ポイント

| # | 期待 |
|---|------|
| 1 | `403` — `email:change` スコープが無い |
| 2 | `400` — 現在値と同じ（確認エンドポイントを使うべき） |
| 3 | `400` — 非文字列はスキーマ検証で弾かれ、**送信前**に拒否される |
| 4 | `404` — 他人のチャレンジは「拒否」ではなく「存在しない」 |
| 5 | 1 通目 `200` / 2 通目 `400` — 同一ユーザー × 同一操作の再送間隔 |
| 6 | `404` — `sms` 認証設定が未登録。電話番号の手順は `../mfa-sms/VERIFY.md` を参照 |

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

### Phase 4: Contact Verification & Change

| Step | 確認項目 | 結果 |
|------|---------|------|
| 13 | `email:change` を含むトークンが取得できる | |
| 14 | 確認は `openid` だけで開始でき、宛先が現在値に固定される（`new_value` が無視される） | |
| 14 | Management API で `delivery: internal` と `verification_code` が取得できる | |
| 14 | 確認の確定で `email_verified` が `true` になる | |
| 15 | Email 変更が確定し、`email` が新しい値になる | |
| 15 | `preferred_username` が新しい値に追従する（識別子の移動） | |
| 16 | 新しいアドレスでログインでき、旧アドレスではできない | |
| 17 | スコープ不足 `403` / 同一値 `400` / 不正形式 `400` / 他人のチャレンジ `404` | |
| 17 | クールダウン中の再送が `400` になる | |
