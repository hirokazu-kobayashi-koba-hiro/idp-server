# 動作確認ガイド - eKYC (Identity Verification)

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。

> **自動テスト**: `./verify.sh` を実行すると、以下の手順をすべて自動で検証できます。
> `./verify.sh --org my-organization` で組織名を指定できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること

## 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-ekyc}"
cd config/templates/use-cases/ekyc

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

## Phase 1: Basic Authentication Flow

---

## Step 1: Discovery Endpoint + verified_claims_supported 確認

OpenID Connect の Discovery エンドポイントと eKYC 固有の設定を確認します。

### リクエスト

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `issuer` が `${TENANT_BASE}` と一致すること
- **`verified_claims_supported` が `true` であること**（eKYC 固有）
- `claims_parameter_supported` が `true` であること

### verified_claims_supported の個別確認

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '{verified_claims_supported, claims_parameter_supported}'
```

> 両方 `true` が返れば OK です。

---

## Step 2: Authorization Request

> **スコープ**: `identity_verification_application` は身元確認申し込みAPI (`/v1/me/identity-verification/applications`) へのアクセスに必要なスコープです。
> このスコープが無いと Phase 2 の身元確認 API 呼び出しが 403 で失敗します。

```bash
STATE="verify-state-$(date +%s)"
SCOPE="openid profile email identity_verification_application"
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

```bash
REFRESH_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

echo "${REFRESH_RESPONSE}" | jq .

ACCESS_TOKEN=$(echo "${REFRESH_RESPONSE}" | jq -r '.access_token')
```

### 確認ポイント

- HTTP 200 が返ること
- 新しい `access_token` が含まれていること

---

## Phase 2: eKYC Identity Verification

ここからは eKYC 固有の機能を検証します。Phase 1 で取得した `ACCESS_TOKEN` を使います。

---

## Step 8: 身元確認設定の確認 (管理API)

setup.sh で登録した identity verification configuration が正しく存在するか管理APIで確認します。

```bash
# 管理者トークンの取得
ORG_ID=$(jq -r '.organization.id' "${CONFIG_DIR}/onboarding.json")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/onboarding.json")
ORG_CLIENT_ID=$(jq -r '.client.client_id' "${CONFIG_DIR}/onboarding.json")
ORG_CLIENT_SECRET=$(jq -r '.client.client_secret' "${CONFIG_DIR}/onboarding.json")
ADMIN_EMAIL=$(jq -r '.user.email' "${CONFIG_DIR}/onboarding.json")
ADMIN_PASSWORD=$(jq -r '.user.raw_password' "${CONFIG_DIR}/onboarding.json")

ORG_TOKEN_RESPONSE=$(curl -s -X POST \
  "${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ADMIN_EMAIL}" \
  --data-urlencode "password=${ADMIN_PASSWORD}" \
  --data-urlencode "client_id=${ORG_CLIENT_ID}" \
  --data-urlencode "client_secret=${ORG_CLIENT_SECRET}" \
  --data-urlencode "scope=openid profile email management")

ORG_ACCESS_TOKEN=$(echo "${ORG_TOKEN_RESPONSE}" | jq -r '.access_token')

# 身元確認設定の取得
IV_CONFIG_ID=$(jq -r '.id' "${CONFIG_DIR}/identity-verification-config.json")

curl -s \
  -X GET "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/identity-verification-configurations/${IV_CONFIG_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `type` が `"authentication-assurance"` であること
- `processes` に `apply` と `evaluate-result` が含まれていること
- `result.verified_claims_mapping_rules` が設定されていること

---

## Step 9: 身元確認の申請 (Apply)

ユーザーの個人情報を提出して身元確認を申請します。

```bash
IV_TYPE="authentication-assurance"

APPLY_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d "{
    \"last_name\": \"Tanaka\",
    \"first_name\": \"Taro\",
    \"birthdate\": \"1990-01-15\",
    \"email_address\": \"${TEST_EMAIL}\",
    \"address\": {
      \"street_address\": \"1-1-1 Chiyoda\",
      \"locality\": \"Chiyoda-ku\",
      \"region\": \"Tokyo\",
      \"postal_code\": \"1000001\",
      \"country\": \"JP\"
    }
  }")

echo "${APPLY_RESPONSE}" | jq .

APPLICATION_ID=$(echo "${APPLY_RESPONSE}" | jq -r '.id')
echo "Application ID: ${APPLICATION_ID}"
```

### 確認ポイント

- HTTP 200 が返ること
- `id` (Application ID) が取得できること

---

## Step 10: 身元確認の承認 (Evaluate Result)

申請を承認します（本番環境では外部 eKYC サービスの審査結果に基づく）。

```bash
EVALUATE_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/${APPLICATION_ID}/evaluate-result" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{
    "approved": true,
    "rejected": false
  }')

echo "${EVALUATE_RESPONSE}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること

---

## Step 11: 申請ステータス確認

承認後、申請ステータスが `approved` になっていることを確認します。

```bash
APPLICATIONS_RESPONSE=$(curl -s \
  -X GET "${TENANT_BASE}/v1/me/identity-verification/applications?id=${APPLICATION_ID}&type=${IV_TYPE}&status=approved" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

echo "${APPLICATIONS_RESPONSE}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `list` に1件の申請が含まれていること
- `status` が `approved` であること
- `type` が `authentication-assurance` であること

---

## Step 12: 身元確認結果の取得

承認された身元確認結果を取得します。

```bash
RESULTS_RESPONSE=$(curl -s \
  -X GET "${TENANT_BASE}/v1/me/identity-verification/results?application_id=${APPLICATION_ID}&type=${IV_TYPE}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

echo "${RESULTS_RESPONSE}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `list` に1件の結果が含まれていること
- `type` が `authentication-assurance` であること
- `verified_at` が存在すること

---

## Phase 3: Verified Claims (claims parameter)

身元確認完了後、`claims` パラメータを使って認可リクエストに `verified_claims` を要求し、ID Token / UserInfo で verified claims が返ることを検証します。

---

## Step 13: Authorization Request with claims parameter

`transfers` スコープと `claims` パラメータを含む認可リクエストを送信します。

```bash
STATE2="verify-ekyc-$(date +%s)"
SCOPE2="openid profile email transfers"
COOKIE_JAR2=$(mktemp)

CLAIMS_PARAM=$(python3 -c "
import urllib.parse, json
claims = {
  'id_token': {
    'verified_claims': {
      'verification': {
        'trust_framework': 'eidas'
      },
      'claims': {
        'given_name': 'Taro',
        'family_name': 'Tanaka',
        'birthdate': '1990-01-15'
      }
    }
  }
}
print(urllib.parse.quote(json.dumps(claims)))
")

AUTH_REDIRECT2=$(curl -s -c "${COOKIE_JAR2}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE2}" | tr ' ' '+')&state=${STATE2}&claims=${CLAIMS_PARAM}")

AUTHORIZATION_ID2=$(echo "${AUTH_REDIRECT2}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTHORIZATION_ID2}"
```

### 確認ポイント

- HTTP 302 リダイレクトが返ること
- Authorization ID が取得できること
- `claims` パラメータがエラーにならないこと

---

## Step 14: Login (既存ユーザー)

Phase 1 で登録したユーザーでログインします。

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

- HTTP 200 が返ること

---

## Step 15: Authorize + Token Exchange

```bash
AUTHORIZE_RESPONSE2=$(curl -s \
  -b "${COOKIE_JAR2}" -c "${COOKIE_JAR2}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI2=$(echo "${AUTHORIZE_RESPONSE2}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE2=$(echo "${AUTHZ_REDIRECT_URI2}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')

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
```

### 確認ポイント

- HTTP 200 が返ること
- `access_token`, `id_token` が含まれていること
- `scope` に `transfers` が含まれていること

---

## Step 16: ID Token デコード (verified_claims 確認)

ID Token をデコードして `verified_claims` が含まれているか確認します。

```bash
echo "${ID_TOKEN2}" | cut -d'.' -f2 | python3 -c "
import sys, base64, json
p = sys.stdin.read().strip()
p += '=' * (4 - len(p) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(p)), indent=2, ensure_ascii=False))
"
```

### 確認ポイント

- `iss`, `sub`, `aud` が含まれていること
- `verified_claims` が含まれていること
  - `verified_claims.verification.trust_framework` が `"eidas"` であること
  - `verified_claims.claims.given_name` が `"Taro"` であること
  - `verified_claims.claims.family_name` が `"Tanaka"` であること
  - `verified_claims.claims.birthdate` が `"1990-01-15"` であること

---

## Step 17: UserInfo

UserInfo エンドポイントで基本クレームが返ることを確認します。

> **注意**: 現時点では UserInfo エンドポイントは `verified_claims` に未対応です。
> `verified_claims` は ID Token でのみ返されます。

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN2}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `sub` が含まれていること
- `email`, `name` が含まれていること

---

## チェックリスト

### Phase 1: Basic Authentication Flow

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | Discovery endpoint が HTTP 200 を返す | |
| 1 | issuer が正しい | |
| 1 | verified_claims_supported が true | |
| 1 | claims_parameter_supported が true | |
| 2 | Authorization request が HTTP 302 を返す | |
| 3 | User registration が成功する | |
| 4 | Authorize で認可コードが取得できる | |
| 5 | Token exchange で access_token が取得できる | |
| 6 | UserInfo で sub, email, name が返る | |
| 7 | Refresh token で新しい access_token が取得できる | |

### Phase 2: eKYC Identity Verification

| Step | 確認項目 | 結果 |
|------|---------|------|
| 8 | 身元確認設定が管理APIで取得できる | |
| 9 | 身元確認申請が成功し Application ID が取得できる | |
| 10 | 申請の承認 (evaluate-result) が成功する | |
| 11 | 申請ステータスが approved になっている | |
| 12 | 身元確認結果が取得でき verified_at が存在する | |

### Phase 3: Verified Claims

| Step | 確認項目 | 結果 |
|------|---------|------|
| 13 | claims パラメータ付き認可リクエストが成功する | |
| 14 | 既存ユーザーでログインが成功する | |
| 15 | Token exchange で transfers スコープが含まれる | |
| 16 | ID Token に verified_claims が含まれる | |
| 16 | verified_claims.verification.trust_framework が eidas | |
| 16 | verified_claims.claims に given_name, family_name, birthdate が含まれる | |
| 17 | UserInfo で sub, email, name が返る | |
