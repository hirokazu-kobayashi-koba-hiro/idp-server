# 動作確認ガイド - MFA (Password + FIDO-UAF Device Authentication)

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。

> **自動テスト**: `./verify.sh` を実行すると、基本フロー（初期登録パス）を自動検証できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `config/generated/mfa-fido-uaf/` に生成ファイルが存在すること
- idp-server が起動中

## 事前準備

### 1. モックサーバーの起動

```bash
# 別ターミナルで起動（ポート4005）
node config/templates/use-cases/mfa-fido-uaf/mock-server.js
```

### 2. helpers.sh の読み込み

```bash
cd config/templates/use-cases/mfa-fido-uaf
source helpers.sh
get_admin_token
```

これにより以下の変数・関数が使えるようになります:

| 変数 | 内容 |
|------|------|
| `$TENANT_BASE` | テナントベースURL |
| `$CLIENT_ID` | クライアントID |
| `$REDIRECT_URI` | リダイレクトURI |

| 関数 | 用途 |
|------|------|
| `start_auth_flow` | 認可リクエスト開始 |
| `start_auth_flow_with_login_hint "sub:{id}"` | login_hint 付き認可リクエスト |
| `register_user [email] [password] [name]` | ユーザー登録 |
| `password_login email password` | パスワード認証 |
| `email_challenge email` | メール OTP 送信 |
| `get_email_verification_code` | Management API で検証コード取得 |
| `email_verify [code]` | メール OTP 検証 |
| `fido_uaf_reg_challenge` | FIDO-UAF 登録チャレンジ |
| `fido_uaf_reg` | FIDO-UAF 登録完了 |
| `complete_auth_flow` | 認可 → トークン交換 |
| `get_device_auth_transactions` | デバイスの認証トランザクション取得 |
| `fido_uaf_auth_challenge` | FIDO-UAF 認証チャレンジ（デバイス側） |
| `fido_uaf_auth` | FIDO-UAF 認証（デバイス側） |
| `get_view_data` | view-data 取得 |
| `get_auth_status` | authentication-status 取得 |
| `get_userinfo` | UserInfo 取得 |
| `show_amr` | ID Token の amr 表示 |

## Phase 1: ユーザー登録 + パスワード認証

```bash
# 認可リクエスト開始
start_auth_flow

# ユーザー登録
register_user

# 認証ステータス確認
get_auth_status | jq '.status'
# 期待: "success"

# 認可 → トークン取得
complete_auth_flow

# UserInfo 確認
get_userinfo | jq '{sub, email, name}'

# amr 確認
show_amr
# 期待: amr: ["initial-registration"]
# 期待: acr: urn:idp:acr:initial
```

## Phase 2: login_hint 付き認可フロー

```bash
# login_hint 付き認可リクエスト
start_auth_flow_with_login_hint "sub:${USER_SUB}"

# view-data で login_hint が返ることを確認
get_view_data | jq '.login_hint'
# 期待: "sub:{USER_SUB}"

# 認証ステータス（認証前）
get_auth_status | jq '.status'
# 期待: "in_progress"

# パスワード認証
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"

# 認証ステータス（認証後）
get_auth_status | jq '{status, authentication_methods}'
# 期待: status: "success", authentication_methods: ["password"]

# 認可 → トークン取得
complete_auth_flow
show_amr
# 期待: amr: ["password"]
# 期待: acr: urn:idp:acr:pwd
```

## Phase 3: デバイス登録条件の検証

パスワードのみではデバイス登録を許可せず、MFA（パスワード + メール認証）完了後のみ許可されることを確認します。

### Step 1: パスワードのみで FIDO-UAF 登録 → 拒否

```bash
# 新しい認可フロー開始
start_auth_flow

# パスワード認証
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"

# FIDO-UAF 登録チャレンジ → forbidden 期待
fido_uaf_reg_challenge
# 期待: ← 400 forbidden: Current authentication level does not meet device registration requirements.
```

### Step 2: メール認証（MFA）完了後に FIDO-UAF 登録 → 許可

```bash
# メール OTP 送信
email_challenge "${TEST_EMAIL}"
# 期待: ← 200 Email OTP sent

# Management API で検証コード取得
get_email_verification_code
# 期待: Verification Code: 123456

# メール OTP 検証
email_verify
# 期待: ← 200 Email verified

# FIDO-UAF 登録チャレンジ → 成功
fido_uaf_reg_challenge
# 期待: ← 200 FIDO-UAF registration challenge received

# FIDO-UAF 登録完了
fido_uaf_reg
# 期待: ← 200 FIDO-UAF registered (device_id: ...)

# 認可 → トークン取得
complete_auth_flow
show_amr
```

## Phase 4: 認可コードフローで FIDO-UAF 認証

Phase 3 でデバイス登録したユーザーで、login_hint + acr_values 付き認可コードフロー + FIDO-UAF 認証を実行します。

```bash
# login_hint + acr_values 付き認可リクエスト
start_auth_flow_with_login_hint "sub:${USER_SUB}" "openid+profile+email" "urn:idp:acr:device"

# view-data で login_hint 確認
get_view_data | jq '.login_hint'
# 期待: "sub:{USER_SUB}"

# 認証ステータス（認証前）
get_auth_status | jq '.status'
# 期待: "in_progress"

# 認証トランザクション取得（デバイス側）
get_device_auth_transactions
# 期待: Transaction ID: {UUID}

# FIDO-UAF 認証チャレンジ（デバイス側）
fido_uaf_auth_challenge
# 期待: ← 200 FIDO-UAF authentication challenge received

# FIDO-UAF 認証（デバイス側 - 生体認証シミュレーション）
fido_uaf_auth
# 期待: ← 200 FIDO-UAF authentication success

# 認証ステータス（認証後）
get_auth_status | jq '{status, authentication_methods}'
# 期待: status: "success", authentication_methods: ["fido-uaf"]

# 認可 → トークン取得
complete_auth_flow

# ID Token の amr 確認
show_amr
# 期待: amr: ["fido-uaf"]
# 期待: acr: urn:idp:acr:device

# UserInfo 確認
get_userinfo | jq '{sub, email, name}'
```

## Phase 5: スコープフィルタリングの検証（level_of_authentication_scopes）

認証レベルに応じてスコープがフィルタリングされることを確認します。

`level_of_authentication_scopes` 設定:
- `transfers` スコープ: FIDO-UAF 認証が必要
- `account` スコープ: パスワード + メール認証（MFA）が必要

### Step 1: パスワードのみで transfers スコープをリクエスト → フィルタリング

```bash
# transfers スコープを含む認可リクエスト
start_auth_flow "openid+profile+email+transfers+account"

# パスワード認証（1要素のみ）
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"

# 認可 → トークン取得
complete_auth_flow

# トークンのスコープ確認
echo "${TOKEN_RESPONSE}" | jq '.scope'
# 期待: transfers と account が除外されている（パスワードのみでは認証レベル不足）

show_amr
# 期待: acr: urn:idp:acr:pwd
```

### Step 2: パスワード + メール認証（MFA）で account スコープ取得

```bash
# transfers + account スコープを含む認可リクエスト
start_auth_flow "openid+profile+email+transfers+account"

# パスワード認証
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"

# メール認証
email_challenge "${TEST_EMAIL}"
get_email_verification_code
email_verify

# 認可 → トークン取得
complete_auth_flow

# トークンのスコープ確認
echo "${TOKEN_RESPONSE}" | jq '.scope'
# 期待: account が含まれる、transfers は除外（FIDO-UAF 未実施）

show_amr
# 期待: amr: ["password", "email"]
# 期待: acr: urn:idp:acr:mfa
```

### Step 3: FIDO-UAF 認証で transfers スコープ取得

```bash
# transfers + account スコープ + acr_values 付き認可リクエスト
# acr_values=urn:idp:acr:device を指定して device_fido_uaf_authentication ポリシーを選択
start_auth_flow_with_login_hint "sub:${USER_SUB}" "openid+profile+email+transfers+account" "urn:idp:acr:device"

# デバイス FIDO-UAF 認証
get_device_auth_transactions
fido_uaf_auth_challenge
fido_uaf_auth

# 認可 → トークン取得
complete_auth_flow

# トークンのスコープ確認
echo "${TOKEN_RESPONSE}" | jq '.scope'
# 期待: transfers が含まれる（FIDO-UAF 認証済み）

show_amr
# 期待: amr: ["fido-uaf"]
# 期待: acr: urn:idp:acr:device
```
