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

## Step 9b: 身元確認の申請 (ongoing-verification/apply) — 外部eKYCサービス連携

外部eKYCサービス（mock-server）と連携する `ongoing-verification` タイプで身元確認を申請します。

> **前提**: mock-server が起動していること (`node mock-server.js`)

```bash
IV_TYPE_ONGOING="ongoing-verification"

APPLY_RESPONSE_ONGOING=$(curl -s \
  -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE_ONGOING}/apply" \
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

echo "${APPLY_RESPONSE_ONGOING}" | jq .

APPLICATION_ID_ONGOING=$(echo "${APPLY_RESPONSE_ONGOING}" | jq -r '.id')
echo "Application ID (ongoing): ${APPLICATION_ID_ONGOING}"
```

### 確認ポイント

- HTTP 200 が返ること
- `id` (Application ID) が取得できること
- mock-serverのログに eKYC 申請リクエストが記録されていること

---

## Step 9c: 身元確認の承認 (ongoing-verification/callback-result)

外部eKYCサービスからのコールバック結果を登録して申請を承認します。

```bash
CALLBACK_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE_ONGOING}/${APPLICATION_ID_ONGOING}/callback-result" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{"verification_result": "approved"}')

echo "${CALLBACK_RESPONSE}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること

---

## Step 9d: 申請ステータス確認 (ongoing-verification)

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/me/identity-verification/applications?id=${APPLICATION_ID_ONGOING}&type=${IV_TYPE_ONGOING}&status=approved" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `status` が `approved` であること
- `type` が `ongoing-verification` であること
- `application_details.external_application_id` が存在すること（外部サービスから返された値）

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

## Phase 2b: verified_claims ライフサイクル確認

verified_claims のマージ挙動を確認します。

---

## Step 12b: verified_claims の確認（管理API）

ongoing-verification 承認前後で verified_claims がどう変わるか確認します。

```bash
# 管理トークンで現在の verified_claims を確認
curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/users?email=${TEST_EMAIL}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.list[0].verified_claims'
```

### 確認ポイント（authentication-assurance 承認後）

- `verification.trust_framework` が `eidas` であること
- `claims.given_name`, `claims.family_name`, `claims.birthdate` が存在すること
- `claims.external_application_id` が存在すること（外部サービスから取得した値）

---

## Step 12c: ongoing-verification 承認後の verified_claims マージ確認

Step 9b〜9c で ongoing-verification を申請・承認した後、verified_claims がどう更新されたか確認します。

```bash
curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/users?email=${TEST_EMAIL}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.list[0].verified_claims'
```

### 確認ポイント（ongoing-verification 承認後）

- `verification.trust_framework` が `jp_aml` に**上書き**されていること（authentication-assurance の `eidas` から変更）
- `claims.external_application_id` が**新しい値に上書き**されていること
- `claims.given_name`, `claims.family_name`, `claims.birthdate` はそのまま保持されていること

> **マージ挙動**: `mergeVerifiedClaims(putAll)` により、同じキーは上書き、存在しないキーは保持されます。マッピングルールにないキーの既存値は消えません。

---

## Step 12d: 空マッピングルールの承認確認（既存値が保持されること）

`verified_claims_mapping_rules` が空 `[]` のテンプレートで承認した場合、既存の verified_claims が保持されることを確認します。

> **前提**: 管理APIで `verified_claims_mapping_rules: []` のテンプレート（例: `empty-result-test`）を事前に登録しておく

```bash
# 空マッピングのテンプレートで申込み + 承認
APPLY_EMPTY=$(curl -s \
  -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/empty-result-test/apply" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{"name": "Test"}')

APPLICATION_ID_EMPTY=$(echo "${APPLY_EMPTY}" | jq -r '.id')

curl -s \
  -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/empty-result-test/${APPLICATION_ID_EMPTY}/evaluate-result" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{"approved": true}'

# verified_claims が変わっていないことを確認
curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/users?email=${TEST_EMAIL}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.list[0].verified_claims'
```

### 確認ポイント

- verified_claims が承認前と**同一**であること
- 空の `putAll({})` は既存値を変更しないことの確認

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
        'trust_framework': 'jp_aml'
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
  - `verified_claims.verification.trust_framework` が `"jp_aml"` であること（ongoing-verification で申請した場合）
  - `verified_claims.claims.given_name` が `"Taro"` であること
  - `verified_claims.claims.family_name` が `"Tanaka"` であること
  - `verified_claims.claims.birthdate` が `"1990-01-15"` であること

---

## Step 17: UserInfo

UserInfo エンドポイントで基本クレームが返ることを確認します。

> **注意**: UserInfo の `verified_claims` は **scope ベース selective**（`verified_claims:*` スコープ + `access_token_selective_verified_claims`）で返る（[Phase 4](#phase-4-scope-ベース-selective-verified_claims-1514) 参照）。本 Step の `claims` パラメータは `id_token` 向けの要求なので、ここでの UserInfo には `verified_claims` は含まれない。

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

## Phase 4: scope ベース selective verified_claims (#1514)

`claims` パラメータを使わず、`verified_claims:*` スコープで Access Token / UserInfo に verified_claims を**選択的**に含める方式を確認します。`access_token_selective_verified_claims: true` が前提。Phase 1 で登録し Phase 2 で身元確認を完了したユーザー（`${TEST_EMAIL}`）で確認します。

---

## Step 18: scope ベースで認可 〜 トークン取得

`verified_claims:given_name verified_claims:family_name` のみ要求します（`verified_claims:verification:*` は要求しない）。

```bash
STATE3="verify-scope-vc-$(date +%s)"
SCOPE3="openid verified_claims:given_name verified_claims:family_name"
COOKIE_JAR3=$(mktemp)

AUTH_REDIRECT3=$(curl -s -c "${COOKIE_JAR3}" -o /dev/null -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE3}" | tr ' ' '+')&state=${STATE3}")
AUTHORIZATION_ID3=$(echo "${AUTH_REDIRECT3}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')

curl -s -b "${COOKIE_JAR3}" -c "${COOKIE_JAR3}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID3}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"${TEST_EMAIL}\", \"password\": \"${TEST_PASSWORD}\"}" > /dev/null

AUTHORIZE_RESPONSE3=$(curl -s -b "${COOKIE_JAR3}" -c "${COOKIE_JAR3}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID3}/authorize" \
  -H "Content-Type: application/json" -d '{}')
AUTHORIZATION_CODE3=$(echo "${AUTHORIZE_RESPONSE3}" | jq -r '.redirect_uri' | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')

TOKEN_RESPONSE3=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE3}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

ACCESS_TOKEN3=$(echo "${TOKEN_RESPONSE3}" | jq -r '.access_token')
echo "${TOKEN_RESPONSE3}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `access_token` が取得できること

---

## Step 19: Access Token デコード (selective verified_claims)

```bash
echo "${ACCESS_TOKEN3}" | cut -d'.' -f2 | python3 -c "
import sys, base64, json
p = sys.stdin.read().strip()
p += '=' * (4 - len(p) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(p)), indent=2, ensure_ascii=False))
"
```

### 確認ポイント

- `verified_claims` がネスト構造（`verification` + `claims`）で含まれること
- `verified_claims.claims.given_name` / `family_name` が含まれること
- `verified_claims.claims.birthdate` は**含まれない**こと（要求していない＝データ最小化）
- `verified_claims.verification.trust_framework` が**含まれる**こと（`verified_claims:verification:trust_framework` を要求していなくても、`verification` の必須要素なので常時返る。`verification: {}` は非準拠のため出さない）
- `verified_claims.verification.evidence` は**含まれない**こと（要求していない＝オプトイン）

---

## Step 20: UserInfo (selective verified_claims)

```bash
curl -s -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN3}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `verified_claims` が Access Token と同じネスト構造で返ること（UserInfo も #1514 で対応）
- `verification.trust_framework` が常時含まれ、未要求の `claims.birthdate` / `verification.evidence` は含まれないこと

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
| 9b | ongoing-verification 申請が成功し Application ID が取得できる | |
| 9c | ongoing-verification コールバック承認が成功する | |
| 9d | ongoing-verification ステータスが approved、external_application_id が存在する | |
| 10 | 申請の承認 (evaluate-result) が成功する | |
| 11 | 申請ステータスが approved になっている | |
| 12 | 身元確認結果が取得でき verified_at が存在する | |

### Phase 2b: verified_claims ライフサイクル

| Step | 確認項目 | 結果 |
|------|---------|------|
| 12b | authentication-assurance 承認後に verified_claims に external_application_id が存在する | |
| 12c | ongoing-verification 承認後に trust_framework が jp_aml に上書きされている | |
| 12c | ongoing-verification 承認後に external_application_id が新しい値に上書きされている | |
| 12c | ongoing-verification 承認後に given_name, family_name, birthdate が保持されている | |
| 12d | 空マッピングルールの承認後に verified_claims が変更されていない | |

### Phase 3: Verified Claims

| Step | 確認項目 | 結果 |
|------|---------|------|
| 13 | claims パラメータ付き認可リクエストが成功する | |
| 14 | 既存ユーザーでログインが成功する | |
| 15 | Token exchange で transfers スコープが含まれる | |
| 16 | ID Token に verified_claims が含まれる | |
| 16 | verified_claims.verification.trust_framework が jp_aml | |
| 16 | verified_claims.claims に given_name, family_name, birthdate が含まれる | |
| 17 | UserInfo で sub, email, name が返る | |

### Phase 4: scope ベース selective verified_claims (#1514)

| Step | 確認項目 | 結果 |
|------|---------|------|
| 18 | verified_claims:* スコープで access_token が取得できる | |
| 19 | Access Token に verified_claims がネスト構造で含まれる | |
| 19 | claims に要求した given_name, family_name のみ含まれる（birthdate は含まれない） | |
| 19 | verification.trust_framework が常時含まれる（未要求でも） | |
| 19 | verification.evidence が含まれない（未要求＝オプトイン） | |
| 20 | UserInfo でも同じネスト構造の verified_claims が返る | |

---

## 次のステップ: 設定のカスタマイズ

基本動作確認が完了したら、設定値を変更して挙動の違いを確認できます。

| ガイド | 内容 |
|--------|------|
| [VERIFY-CONFIG-CHANGES.md](./VERIFY-CONFIG-CHANGES.md) | mock↔http_request切替、リトライ、エラー処理、condition、additional_parameters等 |
| [VERIFY-CONFIG-CHANGES-ADVANCED.md](./VERIFY-CONFIG-CHANGES-ADVANCED.md) | マッピング関数リファレンス（trim/case/replace/switch/if/uuid4/now/split/map/join等） |
