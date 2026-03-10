# 動作確認ガイド - Login (Social)

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。

> **自動テスト**: `./verify.sh` を実行すると、パスワードベースのフロー検証を自動で実行できます。
> `./verify.sh --org my-organization` で組織名を指定できます。
> ソーシャルログイン（Google）はブラウザでの操作が必要なため、自動テストでは検証できません。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること

## 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-login-social}"
cd config/templates/use-cases/login-social

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

テストユーザーを新規登録します（パスワードベースの検証用）。

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

## Google ソーシャルログイン検証

上記の CLI テスト（Step 1〜7）はパスワードベースの認証フローの検証です。
Google ソーシャルログインを検証するには、以下の手順を実施してください。

### 前提条件（Google Cloud Console）

1. [Google Cloud Console](https://console.cloud.google.com/) で OAuth 2.0 クライアントを作成済みであること
2. **承認済みのリダイレクト URI** に以下を追加済みであること:

```bash
# federation-config.json から実際のリダイレクト URI を確認
jq -r '.payload.redirect_uri' "${CONFIG_DIR}/federation-config.json"
# 例: https://api.local.test/{PUBLIC_TENANT_ID}/v1/authorizations/federations/oidc/callback
```

3. `.env` に `GOOGLE_CLIENT_ID` と `GOOGLE_CLIENT_SECRET` が設定されていること
   - `your-google-client-id` のままだとテスト不可

### Step 8: Federation 設定の確認

setup.sh で作成された federation 設定が正しいことを確認します。

```bash
# Federation Config の内容を確認
echo "Federation ID: $(jq -r '.id' "${CONFIG_DIR}/federation-config.json")"
echo "SSO Provider:  $(jq -r '.sso_provider' "${CONFIG_DIR}/federation-config.json")"
echo "Enabled:       $(jq -r '.enabled' "${CONFIG_DIR}/federation-config.json")"
echo "Client ID:     $(jq -r '.payload.client_id' "${CONFIG_DIR}/federation-config.json")"
echo "Redirect URI:  $(jq -r '.payload.redirect_uri' "${CONFIG_DIR}/federation-config.json")"
```

#### 確認ポイント

- `enabled` が `true` であること
- `client_id` が `your-google-client-id` ではなく、実際の Google Client ID であること
- `redirect_uri` のドメインが `AUTHORIZATION_SERVER_URL` と一致していること

### Step 9: クライアントの Federation 設定確認

クライアントに `available_federations` が設定されていることを確認します。

```bash
jq '.available_federations' "${CONFIG_DIR}/public-client.json"
# => ["70afd9e7-c28f-4e26-87ad-f9837255c1d1"] のように Federation ID が含まれること
```

#### 確認ポイント

- `available_federations` 配列に Federation Config の ID が含まれていること

### Step 10: 認証ポリシーの確認

認証ポリシーの `success_conditions` に Google フェデレーション認証（`$.oidc-google.success_count`）が含まれていることを確認します。

> **重要**: この設定が無いと、Google 認証が成功しても authorize ステップで `authentication is required` エラーが返ります。

```bash
# 認証ポリシーの success_conditions を確認
jq '.policies[].success_conditions.any_of[] | select(.[].path == "$.oidc-google.success_count")' "${CONFIG_DIR}/authentication-policy.json"
```

#### 確認ポイント

- `$.oidc-google.success_count` の条件が `any_of` に含まれていること
- 含まれていない場合は、`authentication-policy.json` に以下を追加して認証ポリシーを更新する:

```json
[
  {
    "path": "$.oidc-google.success_count",
    "type": "integer",
    "operation": "gte",
    "value": 1
  }
]
```

### Step 11: 認可リクエスト開始（Google 用）

新しい認可セッションを開始します（Step 2 と同じ手順）。

```bash
STATE="verify-google-$(date +%s)"
SCOPE="openid profile email"
COOKIE_JAR=$(mktemp)

AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE}")

AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTHORIZATION_ID}"
```

### Step 12: Federation フロー開始（API）

Google ボタンのクリックに相当する API コールです。
Federation の type (`oidc`) と sso_provider (`google`) を指定して POST します。

```bash
FEDERATION_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/federations/oidc/google")

GOOGLE_AUTH_URL=$(echo "${FEDERATION_RESPONSE}" | jq -r '.redirect_uri')
echo "Google Auth URL: ${GOOGLE_AUTH_URL}"

# 後で callback API に渡すため、state パラメータを抽出
FEDERATION_STATE=$(python3 -c "from urllib.parse import urlparse, parse_qs; print(parse_qs(urlparse('${GOOGLE_AUTH_URL}').query)['state'][0])")
echo "Federation State: ${FEDERATION_STATE}"
```

#### 確認ポイント

- HTTP 200 が返ること
- `redirect_uri` に Google の認可エンドポイント (`https://accounts.google.com/o/oauth2/v2/auth`) が含まれること
- URL パラメータに `client_id`, `redirect_uri`, `state`, `scope` が含まれていること

### Step 13: Google 認証（ブラウザ）

上記で取得した `GOOGLE_AUTH_URL` をブラウザで開き、Google アカウントで認証します。

```bash
# URL をブラウザで開く（macOS の場合）
open "${GOOGLE_AUTH_URL}"
```

1. **Google ログイン画面** で Google アカウントを選択・認証
2. **Google 同意画面** でアクセスを許可
3. Google がコールバック URL にリダイレクト → ブラウザの **アドレスバー** または **DevTools > Network タブ** でリダイレクト先 URL をコピー

```bash
# リダイレクト先 URL 全体をコピーして設定
# 例: https://...sso-callback?state=eyJ...&code=4%2F0Afr...&scope=...
SSO_CALLBACK_URL="<ブラウザのリダイレクト先 URL 全体>"

# URL から code と state を抽出
GOOGLE_AUTH_CODE=$(python3 -c "from urllib.parse import urlparse, parse_qs; print(parse_qs(urlparse('${SSO_CALLBACK_URL}').query)['code'][0])")
FEDERATION_STATE=$(python3 -c "from urllib.parse import urlparse, parse_qs; print(parse_qs(urlparse('${SSO_CALLBACK_URL}').query)['state'][0])")

echo "Code:  ${GOOGLE_AUTH_CODE}"
echo "State: ${FEDERATION_STATE}"
```

### Step 14: Federation Callback API 実行

Google から受け取った認可コード (`code`) と `state` を使って、フェデレーションコールバック API を実行します。
サーバーは Google に対してトークン交換・UserInfo 取得を行い、ユーザーアカウントを作成/リンクします。

```bash
CALLBACK_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/federations/oidc/callback" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "code=${GOOGLE_AUTH_CODE}" \
  --data-urlencode "state=${FEDERATION_STATE}")

CALLBACK_HTTP=$(echo "${CALLBACK_RESPONSE}" | tail -n1)
CALLBACK_BODY=$(echo "${CALLBACK_RESPONSE}" | sed '$d')

echo "HTTP: ${CALLBACK_HTTP}"
echo "${CALLBACK_BODY}" | jq . 2>/dev/null || echo "${CALLBACK_BODY}"
```

#### 確認ポイント

- HTTP 200 が返ること
- サーバーが Google とのトークン交換に成功し、ユーザーが作成/リンクされること

### Step 15: Authorize → トークン取得

コールバック処理完了後、認可フローを継続してトークンを取得します。

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

echo "${TOKEN_RESPONSE}" | jq .
ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
```

#### 確認ポイント

- トークン交換が成功すること（`access_token`, `id_token` が返る）

### Step 16: UserInfo 確認

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

#### 確認ポイント
- UserInfo のレスポンスに Google アカウントの情報が含まれること:
  - `email`: Google アカウントのメールアドレス
  - `name`: Google アカウントの表示名
  - `email_verified`: `true`（Google 認証済み）

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
| 8 | Federation 設定が正しい（enabled, client_id, redirect_uri） | |
| 9 | クライアントに available_federations が設定されている | |
| 10 | 認証ポリシーに `$.oidc-google.success_count` が含まれている | |
| 11 | Google 用の認可リクエストが開始できる | |
| 12 | Federation API が Google 認可 URL を返す | |
| 13 | (手動) ブラウザで Google 認証し code を取得できる | |
| 14 | Callback API が正常に処理される（HTTP 200） | |
| 15 | Google ログインでトークン取得が成功する | |
| 16 | Google ログインで email, name が UserInfo に含まれる | |
