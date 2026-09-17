# 動作確認ガイド - MFA (Password + SMS OTP)

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
ORGANIZATION_NAME="${ORGANIZATION_NAME:-mfa-sms}"
cd config/templates/use-cases/mfa-sms

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

# Phase 2: MFA Login (SMS OTP + Password)

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

## Step 6: SMS OTP Challenge (1st factor)

SMS OTP チャレンジを送信します（no-action モード：ローカル環境用）。

### リクエスト

```bash
curl -s -b "${COOKIE_JAR2}" -c "${COOKIE_JAR2}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/sms-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d "{
    \"phone_number\": \"+819012345678\",
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
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-interactions/${TRANSACTION_ID}/sms-authentication-challenge" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")

VERIFICATION_CODE=$(echo "${INTERACTION_RESPONSE}" | jq -r '.payload.verification_code')
echo "Verification Code: ${VERIFICATION_CODE}"
```

### 確認ポイント

- 管理者トークンが取得できること
- Transaction ID が取得できること
- 検証コードが取得できること（6桁の数字）

---

## Step 7: SMS OTP Verification (1st factor complete)

取得した検証コードで SMS OTP を検証します。

### リクエスト

```bash
curl -s -b "${COOKIE_JAR2}" -c "${COOKIE_JAR2}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/sms-authentication" \
  -H "Content-Type: application/json" \
  -d "{
    \"verification_code\": \"${VERIFICATION_CODE}\"
  }" | jq .
```

### 確認ポイント

- HTTP 200 または 201 が返ること
- SMS OTP 検証が成功すること

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

- `amr` に `"sms"` と `"password"` の両方が含まれていること（MFA が正しく実行された証拠）
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

# Phase 3: Contact Verification & Change（セルフサービス 連絡先）

認証済みユーザーが自分の電話番号を確認・変更するフローです（`POST /{tenant-id}/v1/me/phone/...`）。

このユースケースは **外部委譲モード**（`authentication-config-sms.json` の
`execution.function: "http_request"`）で、OTP の生成・送信・照合をすべて外部サービスが行います。
idp-server はコードを持たず、突き合わせ用の識別子だけを保持します。
ローカル生成モードの手順は `../mfa-email/VERIFY.md` の Phase 4 を参照してください。

## 前提

- `mock-server.js`（ポート 4004）が起動していること
- `public-tenant-template.json` の `scopes_supported` と `public-client-template.json` の `scope` に
  `phone:change` が含まれていること
- テナントの `identity_unique_key_type` が `PHONE_OR_EXTERNAL_USER_ID` であること
  → **電話番号の変更はログイン識別子の移動**に分類されます

> **変更通知は送られません。** 通知の文面の置き場が認証設定の `templates` しか無く、委譲設定は
> それを持たないためです（→ #1879）。ログに `Contact change notice skipped` が出ます。

## Step 10: phone:change 付きトークンの取得

スコープを広げて MFA を 1 回通します。

### リクエスト

**1. スコープを拡張して認可リクエスト**

```bash
CONTACT_SCOPE="openid profile email phone:change"
COOKIE_JAR3=$(mktemp)
CONTACT_STATE="verify-contact-$(date +%s)"

CONTACT_AUTH_ID=$(curl -s -c "${COOKIE_JAR3}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${CONTACT_SCOPE}" | sed 's/:/%3A/g; s/ /+/g')&state=${CONTACT_STATE}&prompt=login" \
  | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${CONTACT_AUTH_ID}"
```

**2. SMS OTP とパスワードを通す（Step 6 〜 8 と同じ手順）**

```bash
curl -s -b "${COOKIE_JAR3}" -c "${COOKIE_JAR3}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${CONTACT_AUTH_ID}/sms-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d '{"phone_number": "+819012345678", "template": "authentication"}' > /dev/null

CONTACT_TXN_ID=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-transactions?authorization_id=${CONTACT_AUTH_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq -r '.list[0].id')

CONTACT_OTP=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-interactions/${CONTACT_TXN_ID}/sms-authentication-challenge" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq -r '.payload.verification_code')

curl -s -b "${COOKIE_JAR3}" -c "${COOKIE_JAR3}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${CONTACT_AUTH_ID}/sms-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"verification_code\": \"${CONTACT_OTP}\"}" > /dev/null

curl -s -b "${COOKIE_JAR3}" -c "${COOKIE_JAR3}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${CONTACT_AUTH_ID}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"${TEST_EMAIL}\", \"password\": \"${TEST_PASSWORD}\"}" > /dev/null
```

**3. authorize -> token**

```bash
CONTACT_CODE=$(curl -s -b "${COOKIE_JAR3}" -c "${COOKIE_JAR3}" \
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

- レスポンスの `scope` に `phone:change` が含まれること

---

## Step 11: 電話番号を持たないアカウントの確認は拒否される

Step 3 の登録では電話番号を設定していないため、**確認するものがありません**。

### リクエスト

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/me/phone/verification" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" | jq .
```

### 確認ポイント

- HTTP 400 と `the account has no phone to verify.` が返ること
- **送信は行われない**こと（mock-server のコンソールに何も出ない）

---

## Step 12: 電話番号の変更（外部サービスがコードを持つ）

`phone:change` スコープが要ります。外部サービスが OTP を生成・送信し、照合もそちらが行います。

### リクエスト

**1. 変更を開始**

```bash
NEW_PHONE_NUMBER="+819011112222"

CHANGE_CHALLENGE_ID=$(curl -s \
  -X POST "${TENANT_BASE}/v1/me/phone/change" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d "{\"new_value\": \"${NEW_PHONE_NUMBER}\"}" | jq -r '.id')
echo "Challenge ID: ${CHANGE_CHALLENGE_ID}"
```

**2. Management API でチャレンジを見る**

```bash
curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/contact-verification-challenges/${CHANGE_CHALLENGE_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq .

CHANGE_CODE=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/contact-verification-challenges/${CHANGE_CHALLENGE_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq -r '.external_reference.verification_code')
echo "Verification Code: ${CHANGE_CODE}"
```

**3. 誤ったコードが外部サービスに拒否されることを確認**

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/me/phone/change/${CHANGE_CHALLENGE_ID}/verify" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d '{"verification_code": "000000"}' | jq .
```

**4. 正しいコードで確定**

```bash
curl -s \
  -X POST "${TENANT_BASE}/v1/me/phone/change/${CHANGE_CHALLENGE_ID}/verify" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d "{\"verification_code\": \"${CHANGE_CODE}\"}" | jq .
```

### 確認ポイント

- 1 で HTTP 200 と `id` が返ること
- mock-server のコンソールに `OTP generated: ...` が出ること
  — 送信ボディの項目名は **`phone_number`**（`sms-authentication-challenge` の
  `request.schema` と同じ名前）。`phone_number is required` の 400 が返る場合はここがずれています
- 2 の Management API が `"delivery": "external"` を返し、`verification_code` ではなく
  `external_reference` を返すこと（idp-server はコードを持たないため）
- 3 が HTTP 400 になること。**ローカル比較ではなく外部サービスが拒否**しています
- 4 が HTTP 200 になり、`user.phone_number` が `${NEW_PHONE_NUMBER}`、
  `user.preferred_username` も同じ値に追従すること（識別子の移動）

---

## Step 13: 設定した番号の確認と、拒否されること

### リクエスト

```bash
# 1. 今度は確認できる（openid だけで足りる）
echo -n "verification after set: "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/phone/verification" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}"

# 2. スコープ不足（ACCESS_TOKEN2 は openid profile email のみ）
echo -n "insufficient scope: "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/phone/change" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN2}" \
  -d '{"new_value": "+819033334444"}'

# 3. 現在値と同じ値への変更
echo -n "same value: "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/phone/change" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d "{\"new_value\": \"${NEW_PHONE_NUMBER}\"}"

# 4. 送信先に渡せない形式
echo -n "malformed: "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/phone/change" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}" \
  -d '{"new_value": "+81 90 <script>"}'

# 5. 本テナントには email 認証設定が無いため、email 系は到達しない
echo -n "email (no email config): "
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "${TENANT_BASE}/v1/me/email/verification" \
  -H "Authorization: Bearer ${CONTACT_TOKEN}"
```

### 確認ポイント

| # | 期待 |
|---|------|
| 1 | `200` — アカウントに番号があるので確認できる。宛先は現在値に固定 |
| 2 | `403` — `phone:change` スコープが無い |
| 3 | `400` — 現在値と同じ（確認エンドポイントを使うべき） |
| 4 | `400` — スキーマ検証で**送信前**に拒否される |
| 5 | `404` — `email` 認証設定が未登録。メールの手順は `../mfa-email/VERIFY.md` を参照 |

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
| 6 | SMS OTP challenge が成功する | |
| 6b | Management API で検証コードが取得できる | |
| 7 | SMS OTP verification が成功する | |
| 8 | Password authentication が成功する | |
| 9 | MFA flow で token が取得できる | |
| 9b | ID Token の amr に sms と password が含まれる | |
| 9c | UserInfo で sub が返る | |
| 9d | Refresh token が動作する | |

### Phase 3: Contact Verification & Change

| Step | 確認項目 | 結果 |
|------|---------|------|
| 10 | `phone:change` を含むトークンが取得できる | |
| 11 | 電話番号を持たないアカウントの確認が `400` かつ送信ゼロ | |
| 12 | 委譲モードで変更を開始でき、mock-server が `phone_number` を受け取る | |
| 12 | Management API が `delivery: external` と `external_reference` を返す | |
| 12 | 誤ったコードが外部サービスに `400` で拒否される | |
| 12 | 確定で `phone_number` と `preferred_username` が新しい値になる | |
| 13 | 設定後は確認が `200` になる | |
| 13 | スコープ不足 `403` / 同一値 `400` / 不正形式 `400` / email 系 `404` | |
