# 動作確認ガイド - Passwordless (FIDO2/WebAuthn)

setup.sh で構築した環境が正しく動作するかを確認するためのガイドです。

> **Note**: FIDO2/WebAuthn はブラウザの WebAuthn API を使用するため、ブラウザ操作が必要です。
> CLI のみで検証するパスワードフォールバックのテストは `./verify.sh` を実行してください。

## 前提条件

- `setup.sh` が正常に完了していること
- ブラウザが WebAuthn に対応していること（Chrome, Safari, Firefox 等）
- FIDO2 認証器が利用可能であること（Touch ID, Windows Hello, セキュリティキー等）
- `curl`, `jq` がインストール済みであること

## 変数設定

```bash
cd config/examples/passwordless-fido2

source ../../../.env

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' public-tenant-request.json)
CLIENT_ID=$(jq -r '.client_id' client-request.json)
CLIENT_SECRET=$(jq -r '.client_secret' client-request.json)
REDIRECT_URI=$(jq -r '.redirect_uris[0]' client-request.json)
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Client ID:    ${CLIENT_ID}"
echo "Redirect URI: ${REDIRECT_URI}"
```

---

## Step 1: Discovery Endpoint

OpenID Connect の Discovery エンドポイントが正しく応答するか確認します。

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

### 確認ポイント

- `issuer` が `${TENANT_BASE}` と一致すること
- `grant_types_supported` に `authorization_code` が含まれること

---

# Part A: FIDO2 Passkey 登録

ユーザーを登録し、FIDO2 Passkey を紐づけます。

## Step 2: 認可リクエスト（ユーザー登録）

`prompt=create` を指定して、ユーザー登録画面を直接表示します。

```bash
echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=verify-fido2-reg&prompt=create"
```

### 確認ポイント

- FIDO2 ログイン画面（`/signin/fido2/`）にリダイレクトされること

## Step 3: ユーザー登録（Sign Up 画面）

初回のため、ログイン画面から Sign Up 画面に遷移してユーザーを登録します。

1. ログイン画面で **「Sign Up」** リンクをクリック
2. 以下の情報を入力:
   - **Email**: `fido2-test@example.com`
   - **Password**: `TestPass123`
   - **Name**: `FIDO2 Test User`
3. **「Register」** ボタンをクリック

### 確認ポイント

- ユーザー登録が成功すること
- 同意画面、またはFIDO2 Passkey 登録のプロンプトに遷移すること

## Step 4: FIDO2 Passkey 登録（ブラウザダイアログ）

ユーザー登録後、FIDO2 Passkey の登録プロンプトが表示されます。

1. ブラウザの WebAuthn ダイアログが表示される
2. 認証器で Passkey を作成:
   - **Touch ID**: 指紋を読み取る
   - **Windows Hello**: PIN または顔認証
   - **セキュリティキー**: キーをタッチ
3. 登録完了を確認

> この Step は認証ポリシーの設定により、同意画面の後に表示される場合もあります。
> Passkey 登録がスキップされた場合でも、パスワード認証で同意画面に進めます。

### 確認ポイント

- ブラウザの WebAuthn ダイアログが表示されること
- Passkey の登録が成功すること

## Step 5: 同意画面（Consent）

同意画面で認可を付与します。

1. 要求されているスコープ（openid, profile, email）を確認
2. **「Approve」** ボタンをクリック

### 確認ポイント

- リダイレクト URI に `code` パラメータ付きでリダイレクトされること
- URL から認可コードをコピーしておく

## Step 6: Token Exchange

リダイレクト先 URL の `code` パラメータを使ってトークンを取得します。

```bash
# リダイレクト先 URL 全体をコピーして設定
# 例: http://localhost:3000/callback/?code=XXXX&state=verify-fido2-reg
CALLBACK_URL="<ブラウザのリダイレクト先 URL 全体>"

# URL から code を抽出
AUTHORIZATION_CODE=$(python3 -c "from urllib.parse import urlparse, parse_qs; print(parse_qs(urlparse('${CALLBACK_URL}').query)['code'][0])")
echo "Authorization Code: ${AUTHORIZATION_CODE}"

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
ID_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')
REFRESH_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.refresh_token')
```

### 確認ポイント

- `access_token`, `id_token`, `refresh_token` が含まれていること

## Step 7: ID Token 確認

```bash
echo "${ID_TOKEN}" | cut -d'.' -f2 | python3 -c "import sys,base64,json; print(json.dumps(json.loads(base64.urlsafe_b64decode(sys.stdin.read().strip()+'==')),indent=2))"
```

### 確認ポイント

- `sub` が存在すること
- `iss` が `${TENANT_BASE}` と一致すること
- `email` が登録したメールアドレスと一致すること

## Step 8: UserInfo Endpoint

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

### 確認ポイント

- `sub`, `email`, `name` が含まれていること

---

# Part B: FIDO2 認証

登録済みの Passkey を使って FIDO2 認証を行います。

> **重要**: Part A で Passkey 登録が完了していることが前提です。
> ブラウザのセッション（Cookie）をクリアするか、シークレットウィンドウを使用してください。

## Step 9: 認可リクエスト（再認証）

`prompt=login` を指定して、既存セッションに関わらず再認証を強制します。

```bash
echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=verify-fido2-auth&prompt=login"
```

### 確認ポイント

- FIDO2 ログイン画面（`/signin/fido2/`）にリダイレクトされること

## Step 10: FIDO2 認証（Passkey ログイン）

FIDO2 ログイン画面で Passkey を使って認証します。

1. **「Sign in with Passkey」** ボタンをクリック（またはパスキーのプロンプトが自動表示）
2. 認証器で認証:
   - **Touch ID**: 指紋を読み取る
   - **Windows Hello**: PIN または顔認証
   - **セキュリティキー**: キーをタッチ
3. 認証成功 → 同意画面に遷移

### 確認ポイント

- ブラウザの WebAuthn ダイアログが表示されること
- パスワード入力なしで認証が成功すること

## Step 11: 同意 + Token Exchange

1. 同意画面で **「Approve」** をクリック
2. リダイレクト先 URL 全体をコピー

```bash
# リダイレクト先 URL 全体をコピーして設定
CALLBACK_URL_2="<ブラウザのリダイレクト先 URL 全体>"

# URL から code を抽出
AUTHORIZATION_CODE_2=$(python3 -c "from urllib.parse import urlparse, parse_qs; print(parse_qs(urlparse('${CALLBACK_URL_2}').query)['code'][0])")
echo "Authorization Code: ${AUTHORIZATION_CODE_2}"

TOKEN_RESPONSE_2=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE_2}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

echo "${TOKEN_RESPONSE_2}" | jq .

ACCESS_TOKEN_2=$(echo "${TOKEN_RESPONSE_2}" | jq -r '.access_token')
```

### 確認ポイント

- `access_token`, `id_token` が含まれていること
- パスワードを一度も入力せずにトークンが取得できたこと

## Step 12: UserInfo（FIDO2 認証セッション）

```bash
curl -s \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN_2}" | jq .
```

### 確認ポイント

- Part A と同じ `sub`, `email`, `name` が返ること（同一ユーザー）

---

## チェックリスト

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | Discovery endpoint が正しい issuer を返す | |
| **Part A: FIDO2 Passkey 登録** | | |
| 2 | 認可リクエストで FIDO2 ログイン画面にリダイレクトされる | |
| 3 | Sign Up でユーザー登録が成功する | |
| 4 | FIDO2 Passkey 登録が成功する | |
| 5 | 同意画面で認可コードが取得できる | |
| 6 | Token Exchange で access_token, id_token が取得できる | |
| 7 | ID Token に sub, email が含まれる | |
| 8 | UserInfo で sub, email, name が返る | |
| **Part B: FIDO2 認証** | | |
| 9 | 新しいセッションで FIDO2 ログイン画面が表示される | |
| 10 | Passkey（Touch ID 等）でパスワードなしログインが成功する | |
| 11 | Token Exchange で access_token, id_token が取得できる | |
| 12 | UserInfo で Part A と同じユーザー情報が返る | |
