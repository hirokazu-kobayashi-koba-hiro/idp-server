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
MTLS_BASE_URL="${MTLS_BASE_URL:-https://mtls.api.local.test}"
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

PAR（Pushed Authorization Request）を使った認可コードフローを検証します。
クライアント認証は `tls_client_auth`（mTLS）を使用します。

> **Note**: FIDO2/WebAuthn はブラウザの WebAuthn API を使用するため、ブラウザ操作が必要です。

### FAPI プロファイルの適用ルール

要求するスコープによって FAPI プロファイルが自動的に切り替わり、セキュリティ要件が変わります。

| スコープ | FAPI プロファイル | PAR の要件 |
|---------|-----------------|-----------|
| `read`, `account` | **Baseline** | プレーン PAR（署名不要） |
| `write`, `transfers` | **Advance** | **署名付きリクエストオブジェクト（JWT）が必須** |
| 上記以外（`openid`, `profile`, `email` 等） | プロファイル適用なし | プレーン PAR |

この設定はテナントの `authorization_server.extension` で定義されています:

```json
{
  "fapi_baseline_scopes": ["read", "account"],
  "fapi_advance_scopes": ["write", "transfers"]
}
```

**Part A / Part B** では Baseline スコープのみ要求するため、プレーン PAR を使用します。
**Phase 6 Step 24** では `transfers`（Advance スコープ）を要求するため、署名付きリクエストオブジェクトが必要です。mock-server.js の `/jwt/sign` エンドポイントで JWT を生成します。

---

# Part A: ユーザー登録 + FIDO2 Passkey 登録

ユーザーを登録し、FIDO2 Passkey を紐づけます。

### Step 9: PAR リクエスト（ユーザー登録）

PAR エンドポイントに認可リクエストパラメータを事前登録します。
`tls_client_auth` クライアントを使用し、クライアント証明書で認証します。

```bash
# PKCE code_verifier 生成
CODE_VERIFIER=$(openssl rand -hex 32)
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
  --data-urlencode "scope=openid profile email account identity_verification_application identity_verification_result" \
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

### Step 16b: 送信者限定トークン（Certificate-Bound Token）の検証

トークンがクライアント証明書に紐づいており、別の証明書では使えないことを確認します。

#### Access Token の `cnf` クレーム確認

```bash
echo "${ACCESS_TOKEN}" | cut -d'.' -f2 | python3 -c "
import sys,base64,json
s=sys.stdin.read().strip()+'=='
d=json.loads(base64.urlsafe_b64decode(s))
print(json.dumps(d.get('cnf', 'NOT PRESENT'), indent=2))
"
```

- `cnf.x5t#S256` にクライアント証明書の SHA-256 thumbprint が含まれていること

#### テスト: 証明書なしでアクセス（非 mTLS エンドポイント経由）

```bash
curl -s -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

- `error: "invalid_token"` が返ること
- `error_description` に `"access token is sender constrained, but mtls client cert does not exists"` が含まれること

#### テスト: 別のクライアント証明書でアクセス

同じ CA で署名された別のクライアント証明書を生成し、トークンのバインディング不一致を確認します。

```bash
# 同じ開発用 CA で別の証明書を生成
CA_CERT="../../../../docker/nginx/ca.crt"
CA_KEY="../../../../docker/nginx/ca.key"
OTHER_CERT_DIR=$(mktemp -d)

openssl ecparam -genkey -name prime256v1 -noout -out "${OTHER_CERT_DIR}/other-key.pem" 2>/dev/null
openssl req -new -key "${OTHER_CERT_DIR}/other-key.pem" -out "${OTHER_CERT_DIR}/other.csr" \
  -subj "/CN=other-app/O=Other Org/C=JP" 2>/dev/null
openssl x509 -req -days 1 -in "${OTHER_CERT_DIR}/other.csr" \
  -CA "${CA_CERT}" -CAkey "${CA_KEY}" -CAcreateserial \
  -out "${OTHER_CERT_DIR}/other-cert.pem" 2>/dev/null

# 別の証明書で UserInfo にアクセス
curl -s \
  --cert "${OTHER_CERT_DIR}/other-cert.pem" --key "${OTHER_CERT_DIR}/other-key.pem" \
  -X GET "${MTLS_TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .

rm -rf "${OTHER_CERT_DIR}"
```

- `error: "invalid_token"` が返ること
- `error_description` に `"access token and mtls client cert is unmatch"` が含まれること

#### 確認ポイント

| テスト | 期待結果 | 理由 |
|--------|---------|------|
| 正しい証明書 | 200 OK | `cnf.x5t#S256` が一致 |
| 証明書なし | 401 `invalid_token` | 送信者限定トークンに証明書が必要 |
| 別の証明書（同じ CA） | 401 `invalid_token` | thumbprint が不一致 |

---

# Part B: FIDO2 認証（PAR + tls_client_auth）

登録済みの Passkey を使って FIDO2 認証を行います。

> **重要**: Part A で Passkey 登録が完了していることが前提です。
> ブラウザのセッション（Cookie）をクリアするか、シークレットウィンドウを使用してください。

### Step 17: PAR リクエスト（再認証）

`prompt=login` を指定して、既存セッションに関わらず再認証を強制します。

```bash
# PKCE code_verifier 生成
CODE_VERIFIER_2=$(openssl rand -hex 32)
CODE_CHALLENGE_2=$(echo -n "${CODE_VERIFIER_2}" | openssl dgst -sha256 -binary | openssl base64 -e | tr '+/' '-_' | tr -d '=')
NONCE_2=$(openssl rand -hex 16)

PAR_RESPONSE_2=$(curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X POST "${MTLS_TENANT_BASE}/v1/authorizations/push" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "response_type=code" \
  --data-urlencode "client_id=${TLS_CLIENT_ID}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "scope=openid profile email account identity_verification_application identity_verification_result" \
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

## Phase 6: eKYC（身元確認）

Part A で取得した `ACCESS_TOKEN` を使って、身元確認の申請→承認→verified_claims 取得を検証します。

> **前提**: Phase 5 Part A でトークンを取得済みであること。`identity_verification_application` および `identity_verification_result` スコープを含むトークンが必要です（Step 9 で含めています）。

### Step 20: eKYC Discovery 確認

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '{
  verified_claims_supported,
  trust_frameworks_supported,
  claims_in_verified_claims_supported,
  evidence_supported
}'
```

#### 確認ポイント

- `verified_claims_supported` が `true`
- `trust_frameworks_supported` に `jp_aml` が含まれること

### Step 21: 身元確認の申請（Apply）

```bash
IV_TYPE="authentication-assurance"

APPLY_RESPONSE=$(curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X POST "${MTLS_TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN_2}" \
  -d '{
    "last_name": "Tanaka",
    "first_name": "Taro",
    "birthdate": "1990-01-15",
    "email_address": "fapi-verify@example.com",
    "address": {
      "street_address": "1-1-1 Chiyoda",
      "locality": "Chiyoda-ku",
      "region": "Tokyo",
      "postal_code": "1000001",
      "country": "JP"
    }
  }')

echo "${APPLY_RESPONSE}" | jq .

APPLICATION_ID=$(echo "${APPLY_RESPONSE}" | jq -r '.id')
echo "Application ID: ${APPLICATION_ID}"
```

#### 確認ポイント

- HTTP 200 が返ること
- `id`（Application ID）が取得できること

### Step 22: 身元確認の承認（Evaluate Result）

申請を承認します（本番環境では外部 eKYC サービスの審査結果に基づく）。

```bash
EVALUATE_RESPONSE=$(curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X POST "${MTLS_TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/${APPLICATION_ID}/evaluate-result" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN_2}" \
  -d '{
    "approved": true,
    "rejected": false
  }')

echo "${EVALUATE_RESPONSE}" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること

### Step 23: 申請ステータス確認

```bash
curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X GET "${MTLS_TENANT_BASE}/v1/me/identity-verification/applications?id=${APPLICATION_ID}&type=${IV_TYPE}&status=approved" \
  -H "Authorization: Bearer ${ACCESS_TOKEN_2}" | jq .
```

#### 確認ポイント

- `list` に1件の申請が含まれていること
- `status` が `approved` であること

### Step 23b: 身元確認結果の取得（IV Results API）

承認された身元確認の `verified_claims`（trust_framework + クレーム）を確認します。

```bash
curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X GET "${MTLS_TENANT_BASE}/v1/me/identity-verification/results?type=${IV_TYPE}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN_2}" | jq .
```

#### 確認ポイント

- `list` に1件の結果が含まれていること
- `verified_claims.verification.trust_framework` が `jp_aml` であること
- `verified_claims.claims` に `given_name`, `family_name`, `birthdate` が含まれていること
- `verified_at` が存在すること

> この API は `identity_verification_result` スコープが必要です。Step 9 / Step 17 で含めています。

### Step 24: verified_claims 付きトークン再取得

身元確認が承認された状態で、`transfers` スコープ（`required_identity_verification_scopes` 対象）を含む新しいトークンを取得します。

> **注意**: `transfers` は FAPI Advance スコープのため、署名付きリクエストオブジェクト（JWT）が必要です。
> mock-server.js の `/jwt/sign` エンドポイントを使って JWT を生成します。
>
> ```bash
> # mock-server.js が起動していない場合
> node mock-server.js &
> ```

#### Step 24a: PKCE + 署名付きリクエストオブジェクト生成

```bash
CODE_VERIFIER_3=$(openssl rand -hex 32)
CODE_CHALLENGE_3=$(echo -n "${CODE_VERIFIER_3}" | openssl dgst -sha256 -binary | openssl base64 -e | tr '+/' '-_' | tr -d '=')
NONCE_3=$(openssl rand -hex 16)
STATE_3="verify-ekyc-$(date +%s)"
NOW=$(date +%s)

JWKS_PATH="$(pwd)/jwks.json"

SIGNED_REQUEST=$(curl -s -X POST http://localhost:4003/jwt/sign \
  -H "Content-Type: application/json" \
  -d "{
    \"jwks_path\": \"${JWKS_PATH}\",
    \"payload\": {
      \"iss\": \"${TLS_CLIENT_ID}\",
      \"sub\": \"${TLS_CLIENT_ID}\",
      \"aud\": \"${TENANT_BASE}\",
      \"jti\": \"par-${STATE_3}\",
      \"iat\": ${NOW},
      \"exp\": $((NOW + 300)),
      \"nbf\": ${NOW},
      \"response_type\": \"code\",
      \"response_mode\": \"jwt\",
      \"client_id\": \"${TLS_CLIENT_ID}\",
      \"redirect_uri\": \"${REDIRECT_URI}\",
      \"scope\": \"openid profile email transfers verified_claims:given_name verified_claims:family_name verified_claims:birthdate\",
      \"state\": \"${STATE_3}\",
      \"nonce\": \"${NONCE_3}\",
      \"code_challenge\": \"${CODE_CHALLENGE_3}\",
      \"code_challenge_method\": \"S256\"
    }
  }" | jq -r '.jwt')

echo "Signed Request: ${SIGNED_REQUEST:0:30}..."
```

#### Step 24b: PAR（署名付き）

```bash
PAR_RESPONSE_3=$(curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X POST "${MTLS_TENANT_BASE}/v1/authorizations/push" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "request=${SIGNED_REQUEST}" \
  --data-urlencode "client_id=${TLS_CLIENT_ID}")

echo "${PAR_RESPONSE_3}" | jq .
REQUEST_URI_3=$(echo "${PAR_RESPONSE_3}" | jq -r '.request_uri')
```

##### 確認ポイント

- `request_uri` が取得できること

#### Step 24c: 認可リクエスト（ブラウザ）

```bash
echo "ブラウザで以下のURLを開いて認証・同意してください:"
echo "${TENANT_BASE}/v1/authorizations?client_id=${TLS_CLIENT_ID}&request_uri=${REQUEST_URI_3}"
```

認証・同意後:

> **JARM レスポンス**: `response_mode=jwt` のため、コールバック URL のクエリパラメータに `response=<JWT>` が含まれます。
> JWT をデコードして `code` を取り出します。

```bash
CALLBACK_URL_3="<ブラウザのリダイレクト先 URL 全体>"

# JARM: response パラメータから JWT を取得してデコード
JARM_JWT=$(python3 -c "from urllib.parse import urlparse, parse_qs; print(parse_qs(urlparse('${CALLBACK_URL_3}').query)['response'][0])")
echo "JARM JWT: ${JARM_JWT:0:30}..."

# JWT ペイロードから code を取り出す
AUTHORIZATION_CODE_3=$(echo "${JARM_JWT}" | cut -d'.' -f2 | python3 -c "
import sys, base64, json
s = sys.stdin.read().strip() + '=='
print(json.loads(base64.urlsafe_b64decode(s))['code'])
")
echo "Authorization Code: ${AUTHORIZATION_CODE_3:0:20}..."

TOKEN_RESPONSE_3=$(curl -s \
  --cert "${CLIENT_CERT}" --key "${CLIENT_KEY}" \
  -X POST "${MTLS_TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE_3}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${TLS_CLIENT_ID}" \
  --data-urlencode "code_verifier=${CODE_VERIFIER_3}")

ACCESS_TOKEN_3=$(echo "${TOKEN_RESPONSE_3}" | jq -r '.access_token')
```

### Step 25: Access Token の verified_claims を確認

`verified_claims:*` スコープにより、JWT Access Token に `verified_claims` が含まれます。

```bash
# Access Token (JWT) をデコードして verified_claims を確認
echo "${ACCESS_TOKEN_3}" | cut -d'.' -f2 | python3 -c "
import sys, base64, json
s = sys.stdin.read().strip() + '=='
payload = json.loads(base64.urlsafe_b64decode(s))
print(json.dumps({
    'scope': payload.get('scope'),
    'verified_claims': payload.get('verified_claims', 'NOT PRESENT'),
    'cnf': payload.get('cnf')
}, indent=2))
"
```

#### 確認ポイント

- `verified_claims` に `given_name`, `family_name`, `birthdate` が含まれていること
- `scope` に `transfers`, `verified_claims:given_name` 等が含まれていること
- `cnf.x5t#S256` が含まれていること（送信者限定トークン）

> **Note**: `verified_claims` は現在 Access Token (JWT) のカスタムクレームとして返されます。
> UserInfo エンドポイントでの `verified_claims` 返却は Issue #1435 で対応予定です。

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
| 5 | 16b | AT に cnf.x5t#S256 が含まれる | |
| 5 | 16b | 証明書なしで 401 invalid_token が返る | |
| 5 | 16b | 別の証明書で 401 invalid_token（unmatch）が返る | |
| **Part B: FIDO2 認証** | | |
| 5 | 17 | PAR で再認証用の request_uri が取得できる | |
| 5 | 18 | Passkey（Touch ID 等）でパスワードなしログインが成功する | |
| 5 | 19 | Token Exchange + UserInfo で Part A と同じユーザー情報が返る | |
| **Phase 6: eKYC（身元確認）** | | | |
| 6 | 20 | verified_claims_supported が true | |
| 6 | 21 | 身元確認の申請（Apply）が成功する | |
| 6 | 22 | 身元確認の承認（Evaluate Result）が成功する | |
| 6 | 23 | 申請ステータスが approved になる | |
| 6 | 23b | IV Results API で verified_claims（jp_aml + クレーム）が返る | |
| 6 | 24 | transfers スコープでトークン再取得ができる | |
| 6 | 25 | Access Token (JWT) に verified_claims が含まれる | |
