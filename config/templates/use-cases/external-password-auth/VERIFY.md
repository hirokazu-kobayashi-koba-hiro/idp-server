# 動作確認ガイド - External Password Auth

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。

> **自動テスト**: `./verify.sh` を実行すると、以下の手順をすべて自動で検証できます。
> `./verify.sh --org my-organization` で組織名を指定できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること
- 外部認証サービス（またはモックサーバー）が起動していること

## 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-external-password-auth}"
cd config/templates/use-cases/external-password-auth

source ../../../../.env

CONFIG_DIR="../../../generated/${ORGANIZATION_NAME}"
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/public-tenant.json")
CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/public-client.json")
CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/public-client.json")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/public-client.json")
EXTERNAL_AUTH_URL=$(jq -r '.interactions["password-authentication"].execution.http_request.url' "${CONFIG_DIR}/authentication-config-password.json")
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

echo "Server:        ${AUTHORIZATION_SERVER_URL}"
echo "Organization:  ${ORGANIZATION_NAME}"
echo "Tenant ID:     ${PUBLIC_TENANT_ID}"
echo "Client ID:     ${CLIENT_ID}"
echo "Redirect URI:  ${REDIRECT_URI}"
echo "External Auth: ${EXTERNAL_AUTH_URL}"
```

---

## Step 1: External Auth Service Connectivity

idp-server が接続する外部認証サービスに直接リクエストを送り、疎通と API 契約を確認します。

idp-server 設定では `host.docker.internal` を使いますが、ホストから直接確認する際は `localhost` に読み替えます。

### リクエスト

```bash
# host.docker.internal → localhost に変換してホストから直接確認
EXTERNAL_AUTH_URL_LOCAL=$(echo "${EXTERNAL_AUTH_URL}" | sed 's|host\.docker\.internal|localhost|')

curl -s -X POST "${EXTERNAL_AUTH_URL_LOCAL}" \
  -H "Content-Type: application/json" \
  -d '{"username": "test@example.com", "password": "test"}' | jq .
```

### 確認ポイント

- 接続できること（タイムアウトしない）
- HTTP 200 の場合: `user_id` と `email` がレスポンスに含まれること
- HTTP 401 の場合: サービスは到達可能（認証拒否は正常動作）

### レスポンス例（成功）

```json
{
  "user_id": "ext-user-test-example-com",
  "email": "test@example.com",
  "name": "External User"
}
```

### トラブルシューティング

接続できない場合、モックサーバーを起動してください:

```bash
node mock-server.js
# → Mock auth server running on http://localhost:4001
```

---

## Step 2: Discovery Endpoint

OpenID Connect の Discovery エンドポイントが正しく応答するか確認します。

### リクエスト

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `issuer` が `${TENANT_BASE}` と一致すること
- `authorization_endpoint`, `token_endpoint`, `userinfo_endpoint` が含まれていること

---

## Step 3: Authorization Request

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

## Step 4: Password Authentication (via External Service)

外部サービス経由でパスワード認証を実行します。

### リクエスト

```bash
TEST_USERNAME="test@example.com"
TEST_PASSWORD="ExternalPass123!"

curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"${TEST_USERNAME}\", \"password\": \"${TEST_PASSWORD}\"}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- 外部サービスがリクエストを受信し、ユーザー情報を返していること
- idp-server がレスポンスからユーザー情報をマッピングしていること

### トラブルシューティング

- HTTP 500: 外部認証サービスが起動していない可能性があります
- HTTP 401/403: 外部サービスが認証を拒否しました

---

## Step 5: Password Authentication Error (invalid credentials)

不正なパスワードで認証が拒否されることを確認します。

### リクエスト

```bash
# Step 3 と同様に新しい認可セッションを作成
STATE_ERR="verify-state-err-$(date +%s)"
COOKIE_JAR_ERR=$(mktemp)

AUTH_REDIRECT_ERR=$(curl -s -c "${COOKIE_JAR_ERR}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE_ERR}")

AUTHORIZATION_ID_ERR=$(echo "${AUTH_REDIRECT_ERR}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')

# 不正なパスワードで認証
curl -s -b "${COOKIE_JAR_ERR}" -c "${COOKIE_JAR_ERR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID_ERR}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"test@example.com\", \"password\": \"invalid\"}" | jq .
```

### 確認ポイント

- **HTTP 401** が返ること（外部サービスの認証拒否が idp-server 経由でクライアントに伝播される）
- レスポンスボディに `user_id: null`, `email: null` が含まれること
- Step 4 の正常認証フローに影響しないこと（別セッション）

### レスポンス例（認証失敗）

```
HTTP/1.1 401

{
  "error": "invalid_credentials",
  "error_description": "Invalid username or password"
}
```

---

## Step 6: Authorize (consent grant)

Step 4 のセッションに戻り、同意を許可して認可コードを取得します。

### リクエスト

```bash
AUTHORIZE_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
RETURNED_STATE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]state=\([^&#]*\).*/\1/p')

echo "Authorization Code: ${AUTHORIZATION_CODE}"
echo "State matches: $([ "${RETURNED_STATE}" = "${STATE}" ] && echo "yes" || echo "no")"
```

### 確認ポイント

- HTTP 200 が返ること
- `redirect_uri` に `code` パラメータが含まれていること
- `state` パラメータが送信時の値と一致すること

---

## Step 7: Token Exchange

認可コードをトークンに交換します。

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

echo "Access Token:  ${ACCESS_TOKEN:0:20}..."
echo "Refresh Token: ${REFRESH_TOKEN:0:20}..."
```

### 確認ポイント

- HTTP 200 が返ること
- `access_token` が含まれていること
- `id_token` が含まれていること
- `token_type` が `Bearer` であること

---

## Step 8: UserInfo Endpoint

アクセストークンを使って UserInfo エンドポイントを呼び出します。

### リクエスト

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `sub` が含まれていること（トークンが有効であることの確認）
- `email` が外部サービスからマッピングされた値と一致すること
- `name` が外部サービスからマッピングされた値と一致すること

### レスポンス例

```json
{
  "sub": "user-uuid",
  "email": "test@example.com",
  "name": "External User"
}
```

---

## Step 9: Token Refresh

リフレッシュトークンを使って新しいアクセストークンを取得します。

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

## チェックリスト

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | 外部認証サービスに接続できる | |
| 1 | レスポンスに user_id, email が含まれる | |
| 2 | Discovery endpoint が HTTP 200 を返す | |
| 2 | issuer が正しい | |
| 3 | Authorization request が HTTP 302 を返す | |
| 3 | Authorization ID が取得できる | |
| 4 | 外部サービス経由のパスワード認証が成功する | |
| 5 | 不正パスワードで認証が拒否される | |
| 6 | Authorize で認可コードが取得できる | |
| 6 | state が一致する | |
| 7 | Token exchange で access_token が取得できる | |
| 7 | id_token が含まれている | |
| 8 | UserInfo で sub が返る | |
| 8 | email, name が外部サービスの値と一致する | |
| 9 | Refresh token で新しい access_token が取得できる | |
