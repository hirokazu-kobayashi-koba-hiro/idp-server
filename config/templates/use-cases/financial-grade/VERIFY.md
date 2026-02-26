# 動作確認ガイド - Financial-Grade (FAPI Advanced + CIBA)

setup.sh で構築した環境が正しく動作するかを確認するためのガイドです。

> **自動テスト**: `./verify.sh` を実行すると、FAPI 設定の検証を自動で行えます。
> `./verify.sh --org my-organization` で組織名を指定できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `openssl` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること
- `./generate-certs.sh` でクライアント証明書を生成済みであること（Phase 5 以降で使用）
- ブラウザが WebAuthn に対応していること（Phase 5 以降で使用：Chrome, Safari, Firefox 等）
- FIDO2 認証器が利用可能であること（Touch ID, Windows Hello, セキュリティキー等）

## 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-financial-grade}"
cd config/templates/use-cases/financial-grade

source ../../../../.env

CONFIG_DIR="../../../generated/${ORGANIZATION_NAME}"
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/financial-tenant.json")
TLS_CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/tls-client-auth-client.json")
PKJ_CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/private-key-jwt-client.json")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/tls-client-auth-client.json")
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

# mTLS エンドポイント（Phase 5 以降で使用）
MTLS_BASE_URL="${MTLS_BASE_URL:-https://mtls.api.local.dev}"
MTLS_TENANT_BASE="${MTLS_BASE_URL}/${PUBLIC_TENANT_ID}"

# クライアント証明書（Phase 5 以降で使用）
CLIENT_CERT="${CONFIG_DIR}/certs/client-cert.pem"
CLIENT_KEY="${CONFIG_DIR}/certs/client-key.pem"

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "mTLS Server:  ${MTLS_BASE_URL}"
echo "Organization: ${ORGANIZATION_NAME}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "TLS Client:   ${TLS_CLIENT_ID}"
echo "PKJ Client:   ${PKJ_CLIENT_ID}"
echo "Redirect URI: ${REDIRECT_URI}"
```

---

## Phase 1: FAPI Configuration Verification

### Step 1: Discovery Endpoint

OpenID Connect の Discovery エンドポイントが FAPI 準拠の設定を返すか確認します。

#### リクエスト

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること
- `issuer` が `${TENANT_BASE}` と一致すること
- `tls_client_certificate_bound_access_tokens` が `true` であること
- `require_signed_request_object` が `true` であること
- `pushed_authorization_request_endpoint` が設定されていること
- `backchannel_authentication_endpoint` が設定されていること

### Step 2: mTLS Configuration

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '{
  tls_client_certificate_bound_access_tokens,
  mtls_endpoint_aliases
}'
```

#### 確認ポイント

- `tls_client_certificate_bound_access_tokens` が `true`
- `mtls_endpoint_aliases` に `token_endpoint`, `userinfo_endpoint` 等が含まれること

### Step 3: Signed Request Object

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '{
  require_signed_request_object,
  request_object_signing_alg_values_supported
}'
```

#### 確認ポイント

- `require_signed_request_object` が `true`
- `request_object_signing_alg_values_supported` に `ES256` が含まれること

### Step 4: PAR (Pushed Authorization Request)

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '{
  pushed_authorization_request_endpoint
}'
```

#### 確認ポイント

- `pushed_authorization_request_endpoint` が設定されていること

### Step 5: JARM (JWT Secured Authorization Response Mode)

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '{
  response_modes_supported,
  authorization_signing_alg_values_supported
}'
```

#### 確認ポイント

- `response_modes_supported` に `jwt`, `query.jwt`, `fragment.jwt` が含まれること
- `authorization_signing_alg_values_supported` に `ES256` が含まれること

---

## Phase 2: Client Authentication

### Step 6: Token Endpoint Auth Methods

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '{
  token_endpoint_auth_methods_supported,
  token_endpoint_auth_signing_alg_values_supported
}'
```

#### 確認ポイント

- `token_endpoint_auth_methods_supported` に `private_key_jwt` が含まれること
- `token_endpoint_auth_methods_supported` に `tls_client_auth` が含まれること
- `token_endpoint_auth_signing_alg_values_supported` に `ES256` が含まれること

---

## Phase 3: CIBA Configuration

### Step 7: CIBA Endpoints

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '{
  backchannel_authentication_endpoint,
  backchannel_token_delivery_modes_supported,
  backchannel_authentication_request_signing_alg_values_supported,
  backchannel_user_code_parameter_supported,
  grant_types_supported
}'
```

#### 確認ポイント

- `backchannel_authentication_endpoint` が設定されていること
- `backchannel_token_delivery_modes_supported` に `poll` が含まれること
- `grant_types_supported` に `urn:openid:params:grant-type:ciba` が含まれること

---

## Phase 4: FAPI Scopes

### Step 8: Scopes

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '{
  scopes_supported
}'
```

#### 確認ポイント

- `account`, `transfers`, `read`, `write` スコープが含まれること

---

## Phase 5: 認可コードフロー（PAR + FIDO2 + tls_client_auth）

PAR（Pushed Authorization Request）を使った FAPI Advanced 認可コードフローを検証します。
PAR を使用するため、署名付きリクエストオブジェクト（JWT）は不要です。
クライアント認証は `tls_client_auth`（mTLS）を使用します。

> **Note**: FIDO2/WebAuthn はブラウザの WebAuthn API を使用するため、ブラウザ操作が必要です。

---

# Part A: ユーザー登録 + FIDO2 Passkey 登録

ユーザーを登録し、FIDO2 Passkey を紐づけます。

### Step 9: PAR リクエスト（ユーザー登録）

PAR エンドポイントに認可リクエストパラメータを事前登録します。
`tls_client_auth` クライアントを使用し、クライアント証明書で認証します。

```bash
# PKCE code_verifier 生成
CODE_VERIFIER=$(openssl rand -base64 32 | tr -d '=+/' | cut -c1-43)
CODE_CHALLENGE=$(echo -n "${CODE_VERIFIER}" | openssl dgst -sha256 -binary | openssl base64 -e | tr '+/' '-_' | tr -d '=')
NONCE=$(openssl rand -hex 16)

echo "Code Verifier:  ${CODE_VERIFIER}"
echo "Code Challenge: ${CODE_CHALLENGE}"
echo "Nonce:          ${NONCE}"

PAR_RESPONSE=$(curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X POST "${MTLS_TENANT_BASE}/v1/authorizations/push" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "response_type=code" \
  --data-urlencode "client_id=${TLS_CLIENT_ID}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "scope=openid profile email account" \
  --data-urlencode "state=verify-fapi-reg" \
  --data-urlencode "nonce=${NONCE}" \
  --data-urlencode "code_challenge=${CODE_CHALLENGE}" \
  --data-urlencode "code_challenge_method=S256" \
  --data-urlencode "prompt=create")

echo "${PAR_RESPONSE}" | jq .

REQUEST_URI=$(echo "${PAR_RESPONSE}" | jq -r '.request_uri')
echo "Request URI: ${REQUEST_URI}"
```

#### 確認ポイント

- HTTP 201 が返ること
- `request_uri` が `urn:ietf:params:oauth:request_uri:` で始まること
- `expires_in` が設定されていること

### Step 10: 認可リクエスト（ブラウザ）

PAR で取得した `request_uri` を使って認可リクエストを送信します。
以下の URL をブラウザで開きます。

```bash
echo "${TENANT_BASE}/v1/authorizations?client_id=${TLS_CLIENT_ID}&request_uri=${REQUEST_URI}"
```

#### 確認ポイント

- FIDO2 ログイン画面（`/signin/fido2/`）にリダイレクトされること

### Step 11: ユーザー登録（Sign Up 画面）

初回のため、ログイン画面から Sign Up 画面に遷移してユーザーを登録します。

1. ログイン画面で **「Sign Up」** リンクをクリック
2. 以下の情報を入力:
   - **Email**: `fapi-verify@example.com`
   - **Password**: `FapiVerifySecure123!`
   - **Name**: `FAPI Verify User`
3. **「Register」** ボタンをクリック

#### 確認ポイント

- ユーザー登録が成功すること
- FIDO2 Passkey 登録のプロンプトに遷移すること

### Step 12: FIDO2 Passkey 登録（ブラウザダイアログ）

ユーザー登録後、FIDO2 Passkey の登録プロンプトが表示されます。

1. ブラウザの WebAuthn ダイアログが表示される
2. 認証器で Passkey を作成:
   - **Touch ID**: 指紋を読み取る
   - **Windows Hello**: PIN または顔認証
   - **セキュリティキー**: キーをタッチ
3. 登録完了を確認

#### 確認ポイント

- ブラウザの WebAuthn ダイアログが表示されること
- Passkey の登録が成功すること

### Step 13: 同意画面（Consent）

1. 要求されているスコープ（openid, profile, email, account）を確認
2. **「Approve」** ボタンをクリック

#### 確認ポイント

- リダイレクト URI に `code` パラメータ付きでリダイレクトされること
- URL から認可コードをコピーしておく

### Step 14: Token Exchange（tls_client_auth）

リダイレクト先 URL 全体をコピーして認可コードを抽出し、トークンを取得します。
`tls_client_auth` のため `client_secret` は不要で、クライアント証明書で認証します。

```bash
# リダイレクト先 URL 全体をコピーして設定
# 例: https://localhost:8443/callback?code=XXXX&state=verify-fapi-reg
CALLBACK_URL="<ブラウザのリダイレクト先 URL 全体>"

# URL から code を抽出
AUTHORIZATION_CODE=$(python3 -c "from urllib.parse import urlparse, parse_qs; print(parse_qs(urlparse('${CALLBACK_URL}').query)['code'][0])")
echo "Authorization Code: ${AUTHORIZATION_CODE}"

TOKEN_RESPONSE=$(curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X POST "${MTLS_TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${TLS_CLIENT_ID}" \
  --data-urlencode "code_verifier=${CODE_VERIFIER}")

echo "${TOKEN_RESPONSE}" | jq .

ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
ID_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')
REFRESH_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.refresh_token')
```

#### 確認ポイント

- `access_token`, `id_token`, `refresh_token` が含まれていること
- `client_secret` を使用していないこと（証明書のみで認証）

### Step 15: ID Token 確認

```bash
echo "${ID_TOKEN}" | cut -d'.' -f2 | python3 -c "import sys,base64,json; print(json.dumps(json.loads(base64.urlsafe_b64decode(sys.stdin.read().strip()+'==')),indent=2))"
```

#### 確認ポイント

- `sub` が存在すること
- `iss` が `${TENANT_BASE}` と一致すること
- `email` が登録したメールアドレスと一致すること

### Step 16: UserInfo

```bash
curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X GET "${MTLS_TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

#### 確認ポイント

- `sub`, `email`, `name` が含まれていること

---

# Part B: FIDO2 認証（PAR + tls_client_auth）

登録済みの Passkey を使って FIDO2 認証を行います。

> **重要**: Part A で Passkey 登録が完了していることが前提です。
> ブラウザのセッション（Cookie）をクリアするか、シークレットウィンドウを使用してください。

### Step 17: PAR リクエスト（再認証）

`prompt=login` を指定して、既存セッションに関わらず再認証を強制します。

```bash
# PKCE code_verifier 生成
CODE_VERIFIER_2=$(openssl rand -base64 32 | tr -d '=+/' | cut -c1-43)
CODE_CHALLENGE_2=$(echo -n "${CODE_VERIFIER_2}" | openssl dgst -sha256 -binary | openssl base64 -e | tr '+/' '-_' | tr -d '=')
NONCE_2=$(openssl rand -hex 16)

PAR_RESPONSE_2=$(curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X POST "${MTLS_TENANT_BASE}/v1/authorizations/push" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "response_type=code" \
  --data-urlencode "client_id=${TLS_CLIENT_ID}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "scope=openid profile email account" \
  --data-urlencode "state=verify-fapi-auth" \
  --data-urlencode "nonce=${NONCE_2}" \
  --data-urlencode "code_challenge=${CODE_CHALLENGE_2}" \
  --data-urlencode "code_challenge_method=S256" \
  --data-urlencode "prompt=login")

echo "${PAR_RESPONSE_2}" | jq .

REQUEST_URI_2=$(echo "${PAR_RESPONSE_2}" | jq -r '.request_uri')
echo "Request URI: ${REQUEST_URI_2}"
```

### Step 18: 認可リクエスト + FIDO2 認証（ブラウザ）

以下の URL をブラウザで開きます。

```bash
echo "${TENANT_BASE}/v1/authorizations?client_id=${TLS_CLIENT_ID}&request_uri=${REQUEST_URI_2}"
```

1. FIDO2 ログイン画面で **「Sign in with Passkey」** をクリック
2. 認証器で認証:
   - **Touch ID**: 指紋を読み取る
   - **Windows Hello**: PIN または顔認証
   - **セキュリティキー**: キーをタッチ
3. 認証成功 → 同意画面 → **「Approve」** をクリック

#### 確認ポイント

- パスワード入力なしで認証が成功すること
- リダイレクト URI に `code` パラメータ付きでリダイレクトされること

### Step 19: Token Exchange + UserInfo

```bash
# リダイレクト先 URL 全体をコピーして設定
CALLBACK_URL_2="<ブラウザのリダイレクト先 URL 全体>"

# URL から code を抽出
AUTHORIZATION_CODE_2=$(python3 -c "from urllib.parse import urlparse, parse_qs; print(parse_qs(urlparse('${CALLBACK_URL_2}').query)['code'][0])")

TOKEN_RESPONSE_2=$(curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X POST "${MTLS_TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE_2}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${TLS_CLIENT_ID}" \
  --data-urlencode "code_verifier=${CODE_VERIFIER_2}")

echo "${TOKEN_RESPONSE_2}" | jq .

ACCESS_TOKEN_2=$(echo "${TOKEN_RESPONSE_2}" | jq -r '.access_token')

curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X GET "${MTLS_TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN_2}" | jq .
```

#### 確認ポイント

- `access_token`, `id_token` が含まれていること
- パスワードを一度も入力せずにトークンが取得できたこと
- UserInfo で Part A と同じ `sub`, `email`, `name` が返ること（同一ユーザー）

---

## チェックリスト

| Phase | Step | 確認項目 | 結果 |
|-------|------|---------|------|
| 1 | 1 | Discovery endpoint が HTTP 200 を返す | |
| 1 | 1 | issuer が正しい | |
| 1 | 2 | mTLS が有効 (tls_client_certificate_bound_access_tokens: true) | |
| 1 | 2 | mTLS エンドポイントエイリアスが設定されている | |
| 1 | 3 | 署名付きリクエストが必須 (require_signed_request_object: true) | |
| 1 | 4 | PAR エンドポイントが設定されている | |
| 1 | 5 | JARM レスポンスモード (jwt) がサポートされている | |
| 2 | 6 | private_key_jwt がサポートされている | |
| 2 | 6 | tls_client_auth がサポートされている | |
| 3 | 7 | CIBA エンドポイントが設定されている | |
| 3 | 7 | CIBA grant type がサポートされている | |
| 4 | 8 | account スコープがサポートされている | |
| 4 | 8 | transfers スコープがサポートされている | |
| 4 | 8 | read スコープがサポートされている | |
| 4 | 8 | write スコープがサポートされている | |
| **Part A: ユーザー登録 + FIDO2 Passkey 登録** | | |
| 5 | 9 | PAR で request_uri が取得できる | |
| 5 | 10 | 認可リクエストで FIDO2 ログイン画面にリダイレクトされる | |
| 5 | 11 | Sign Up でユーザー登録が成功する | |
| 5 | 12 | FIDO2 Passkey 登録が成功する | |
| 5 | 13 | 同意画面で認可コードが取得できる | |
| 5 | 14 | tls_client_auth で Token Exchange が成功する（client_secret 不要） | |
| 5 | 15 | ID Token に sub, email が含まれる | |
| 5 | 16 | UserInfo で sub, email, name が返る | |
| **Part B: FIDO2 認証** | | |
| 5 | 17 | PAR で再認証用の request_uri が取得できる | |
| 5 | 18 | Passkey（Touch ID 等）でパスワードなしログインが成功する | |
| 5 | 19 | Token Exchange + UserInfo で Part A と同じユーザー情報が返る | |
