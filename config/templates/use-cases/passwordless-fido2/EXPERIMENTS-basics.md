# 設定変更 × 挙動確認 実験ガイド（Passwordless FIDO2）

設定を1つ変えて → 挙動がどう変わるかを手元で確認するためのガイドです。
「この設定って何に効くの？」を体感で理解できます。

> **前提**: `setup.sh` が正常に完了していること。

> **FIDO2 と CLI の使い分け**: FIDO2/WebAuthn はブラウザの WebAuthn API を使うため、
> FIDO2 認証そのものの検証はブラウザ操作が必要です。
> ただし、このテンプレートはパスワードフォールバックを含む構成なので、
> 多くの実験は **CLI（パスワード認証）で挙動を確認** できます。

---

## 共通準備

`helpers.sh` を source すると、変数・関数がすべて使えるようになります。

```bash
cd config/templates/use-cases/passwordless-fido2
source helpers.sh                  # デフォルト組織名
# source helpers.sh --org my-org   # 組織名を指定する場合

get_admin_token   # 設定変更に必要な管理トークンを取得
```

> **重要**: テナント更新 API（PUT）は**フル置換**です。
> 送らなかったフィールドは空のデフォルトにリセットされます。
> `helpers.sh` の `update_tenant` / `update_auth_server` を使えば、ベースのJSON から変えたいフィールドだけ上書きして送れます。

> **トラブルシューティング**: 設定変更が効かない場合、まずAPIレスポンスを確認してください。
> `update_tenant` / `update_auth_server` のレスポンスにエラーが含まれている場合、
> 管理トークンの期限切れ（`get_admin_token` を再実行）や、リクエストの不備が原因です。
> レスポンス全体を見るには `| jq .` を付けてください。

### helpers.sh で使える関数

| 関数 | 用途 | 使用例 |
|------|------|--------|
| `get_admin_token` | 管理トークン取得 | `get_admin_token` |
| `update_tenant` | テナント設定を部分変更 | `update_tenant '.identity_policy_config.authentication_device_rule.max_devices = 1'` |
| `restore_tenant` | テナント設定を元に戻す | `restore_tenant` |
| `update_auth_server` | 認可サーバー設定を部分変更 | `update_auth_server '.extension.access_token_duration = 10'` |
| `restore_auth_server` | 認可サーバー設定を元に戻す | `restore_auth_server` |
| `modify_auth_policy` | 認証ポリシーを jq フィルタで部分変更 | `modify_auth_policy '.policies[0].available_methods = ["fido2"]'` |
| `restore_auth_policy` | 認証ポリシーを元に戻す | `restore_auth_policy` |
| `modify_fido2_reg_policy` | FIDO2 登録ポリシーを jq フィルタで部分変更 | `modify_fido2_reg_policy '.policies[0]...'` |
| `restore_fido2_reg_policy` | FIDO2 登録ポリシーを元に戻す | `restore_fido2_reg_policy` |
| `update_client` | クライアント設定を部分変更 | `update_client '.scope = "openid profile"'` |
| `restore_client` | クライアント設定を元に戻す | `restore_client` |
| `start_auth_flow` | 認可リクエスト開始 | `start_auth_flow` / `start_auth_flow openid` |
| `register_user` | ユーザー登録 | `register_user "a@b.com" "Pass123" "Name"` |
| `password_login` | パスワード認証 | `password_login "a@b.com" "Pass123"` |
| `email_challenge` | email 認証チャレンジ | `email_challenge "a@b.com"` |
| `email_verify` | email 認証コード検証 | `email_verify "123456"` |
| `complete_auth_flow` | 認可 → トークン取得 | `complete_auth_flow` |
| `get_view_data` | 認可フローの view-data 取得 | `get_view_data \| jq .` |
| `get_userinfo` | UserInfo 取得 | `get_userinfo` / `get_userinfo "${OTHER_TOKEN}"` |

---

## Experiment 1: パスワードフォールバックを無効化してFIDO2必須にする

> **やりたいこと**: パスワード認証を禁止して、FIDO2 のみの純粋なパスワードレス環境にしたい
>
> **変わる設定**: 認証ポリシー `available_methods` から `password` を除外
>
> **検証方法**: CLI（view-data レスポンスの `available_methods` の変化を確認）
>
> **背景**: `available_methods` はサーバー側で認証リクエストを拒否するものではなく、
> フロントエンド UI に「どの認証方式を表示するか」を伝える UI ヒントです。
> view-data API（`GET /v1/authorizations/{id}/view-data`）のレスポンスに
> `authentication_policy.available_methods` として含まれます。

### 1. ベースライン：変更前の view-data を確認

```bash
start_auth_flow

echo "--- 変更前: view-data の available_methods ---"
get_view_data | jq '.authentication_policy.available_methods'
```

> `["fido2", "password", "email", "initial-registration"]` が返るはずです。

### 2. 設定変更：available_methods から password を外す

```bash
modify_auth_policy '
  .policies[0].available_methods = ["fido2", "email", "initial-registration"]
' | jq '.result.policies[0].available_methods // .'
```

### 3. 挙動確認：変更後の view-data を確認

```bash
start_auth_flow

echo "--- 変更後: view-data の available_methods ---"
get_view_data | jq '.authentication_policy.available_methods'

echo ""
echo "--- 変更後: view-data のポリシー全体 ---"
get_view_data | jq '.authentication_policy | {description, available_methods, step_definitions}'
```

### 4. 期待結果

| タイミング | `available_methods` | UI への影響 |
|-----------|---------------------|-----------|
| 変更前 | `["fido2", "password", "email", "initial-registration"]` | パスワード入力フォームが表示される |
| 変更後 | `["fido2", "email", "initial-registration"]` | パスワード入力フォームが**消える**。FIDO2 のみ表示 |

> **注意**: `available_methods` は UI ヒントです。
> サーバー側では `password-authentication` API を直接呼べば認証自体は実行されます。
> パスワード認証を完全に禁止するには、`success_conditions` からパスワードのパスも外す必要があります。

### 5. 元に戻す

```bash
restore_auth_policy
```

---

## Experiment 2: デバイス登録上限を変更する

> **やりたいこと**: ユーザーが登録できる FIDO2 デバイスの数を制限したい
>
> **変わる設定**: `identity_policy_config.authentication_device_rule.max_devices`
>
> **検証方法**: ブラウザ（FIDO2 デバイス登録）

### 1. 設定変更：max_devices を 1 に

```bash
update_tenant '.identity_policy_config.authentication_device_rule.max_devices = 1' \
  | jq '.result.identity_policy_config.authentication_device_rule // .'
```

### 2. 1台目のデバイス登録（成功）

1. ブラウザで認可リクエストを開く（`prompt=create` でユーザー登録から）:

```bash
echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=exp-device-limit&prompt=create"
```

2. ユーザーを登録し、email 認証 → **1台目の FIDO2 デバイスを登録** → **成功する**
3. **登録したメールアドレスを控えておく**（次のステップで使う）

### 3. 2台目のデバイス登録（拒否）

1. 新しいブラウザウィンドウ（またはシークレットウィンドウ）で認可リクエストを開く（`prompt=login` で再認証）:

```bash
echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=exp-device-limit-2&prompt=login"
```

2. Step 2 で登録したメールアドレスを入力 → email 認証を完了する
3. email 認証成功により `device_registration_conditions` が満たされ、FIDO2 デバイス登録画面が表示される
4. **2台目の FIDO2 デバイスを登録** しようとする → **拒否される**（`max_devices = 1` を超過）

> **Note**: `prompt=login` は既存セッションを無視して再認証を強制します。
> email 認証成功後に `device_registration_conditions` が満たされるため、
> 認証 UI がデバイス追加登録のオプションを表示します。

### 4. 期待結果

| 操作 | 結果 | 理由 |
|------|------|------|
| 1台目のデバイス登録 | 成功 | `max_devices = 1` の上限内 |
| 2台目のデバイス登録 | 拒否 | `max_devices = 1` を超過 |

### 5. 元に戻す

```bash
restore_tenant
```

---

## Experiment 3: device_registration_conditions を外す（セキュリティ体験）

> **やりたいこと**: デバイス登録時の MFA 要件を外すとどうなるか体験したい
>
> **変わる設定**: 認証ポリシー `device_registration_conditions` を空にする
>
> **検証方法**: ブラウザ（FIDO2 デバイス登録）
>
> **セキュリティ警告**: この実験は MFA なしでデバイス登録が可能になる脆弱性を
> 意図的に作り出します。体験後は必ず元に戻してください。

### 1. 設定変更：device_registration_conditions を空にする

```bash
modify_auth_policy '
  .policies[0].device_registration_conditions = {}
' | jq '.result.policies[0] | {description, device_registration_conditions} // .'
```

### 2. 挙動確認（ブラウザ）

1. ブラウザで認可リクエストを開き、新しいユーザーを登録:

```bash
echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=exp-no-mfa&prompt=create"
```

2. ユーザー登録後、**email 認証なし**で FIDO2 デバイス登録のプロンプトが表示される

### 3. 期待結果

| 設定 | デバイス登録時 | セキュリティリスク |
|------|-------------|-----------------|
| `device_registration_conditions` あり（デフォルト） | email 認証成功が必須 | MFA で本人確認済み |
| `device_registration_conditions` なし（この実験） | 条件なしで即座に登録可能 | 攻撃者がセッションを奪取した場合、不正なデバイスを登録できる |

### 4. 元に戻す

```bash
restore_auth_policy
```

---

## Experiment 4: パスワードフォールバック時のアカウントロック

> **やりたいこと**: パスワードフォールバック利用時に、3回間違えたらロックしたい
>
> **変わる設定**: `password_policy.max_attempts` + 認証ポリシー `failure_conditions`
>
> **検証方法**: CLI（パスワード認証で検証）

### 1. まずテストユーザーを作成

```bash
LOCK_TEST_EMAIL="locktest-$(date +%s)@example.com"
start_auth_flow
register_user "${LOCK_TEST_EMAIL}" "CorrectPass1" "Lock Test User"
complete_auth_flow > /dev/null
echo "テストユーザー: ${LOCK_TEST_EMAIL}"
```

### 2. 設定変更：max_attempts を 3 に

```bash
# テナントのパスワードポリシーを更新
update_tenant '
  .identity_policy_config.password_policy.max_attempts = 3 |
  .identity_policy_config.password_policy.lockout_duration_seconds = 60
' | jq '.result.identity_policy_config.password_policy // .'

# 認証ポリシーの failure/lock 閾値も 3 に合わせる
modify_auth_policy '
  .policies[0].failure_conditions.any_of[1][0].value = 3 |
  .policies[0].lock_conditions.any_of[1][0].value = 3
' | jq '.result.policies[0] | {failure_conditions, lock_conditions} // .'
```

### 3. 挙動確認：わざと3回間違える

```bash
start_auth_flow

echo "--- 1回目: 間違ったパスワード ---"
password_login "${LOCK_TEST_EMAIL}" "WrongPass1" | jq .

echo "--- 2回目: 間違ったパスワード ---"
password_login "${LOCK_TEST_EMAIL}" "WrongPass2" | jq .

echo "--- 3回目: 間違ったパスワード → ここでロック ---"
password_login "${LOCK_TEST_EMAIL}" "WrongPass3" | jq .

echo "--- 4回目: 正しいパスワード → ロック中なので拒否されるはず ---"
password_login "${LOCK_TEST_EMAIL}" "CorrectPass1" | jq .
```

### 4. 期待結果

| 回数 | パスワード | 結果 | 理由 |
|------|-----------|------|------|
| 1回目 | `WrongPass1` | 認証失敗 | パスワード不一致 |
| 2回目 | `WrongPass2` | 認証失敗 | パスワード不一致 |
| 3回目 | `WrongPass3` | ロック発動 | `failure_count >= 3` で `lock_conditions` に該当 |
| 4回目 | `CorrectPass1` | 拒否 | 正しいパスワードでもロック中（60秒間） |

> 60秒待ってから再度正しいパスワードでログインすると成功するはずです。

### 5. 元に戻す

```bash
restore_tenant
restore_auth_policy
```

---

## Experiment 5: claims_supported を変えて UserInfo の返却値を確認する

> **やりたいこと**: UserInfo で返るクレームを制御したい
>
> **変わる設定**: `authorization_server.claims_supported`
>
> **検証方法**: CLI（パスワード認証でトークン取得後に UserInfo 確認）

### 1. まずベースラインを確認

```bash
start_auth_flow
register_user "claims-$(date +%s)@example.com" "TestPass123" "Claims User"
complete_auth_flow

echo "--- ベースライン: UserInfo ---"
get_userinfo | jq .
```

> `sub`, `email`, `name` 等が返ってくるはずです。

### 2. 設定変更：claims_supported を sub のみに

```bash
update_auth_server '.claims_supported = ["sub", "iss", "auth_time", "acr"]' \
  | jq '.result.claims_supported // .'
```

### 3. 挙動確認：新しいトークンで UserInfo を取得

```bash
start_auth_flow
register_user "claims2-$(date +%s)@example.com" "TestPass123" "Claims User 2"
complete_auth_flow

echo "--- claims_supported 制限後: UserInfo ---"
get_userinfo | jq .
```

### 4. 期待結果

| タイミング | claims_supported | UserInfo の内容 |
|-----------|------------------|----------------|
| 変更前 | 全クレーム | `sub`, `email`, `name` 等 |
| 変更後 | `["sub", "iss", "auth_time", "acr"]` | `sub` のみ（email, name は消える） |

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 6: トークン有効期限を短くして期限切れを体験する

> **やりたいこと**: アクセストークンの有効期限を短くしたい → 期限切れ後にどうなるか見たい
>
> **変わる設定**: `authorization_server.extension.access_token_duration`
>
> **検証方法**: CLI（パスワード認証でトークン取得後に検証）

### 1. 設定変更：AT を 10秒に

```bash
update_auth_server '.extension.access_token_duration = 10' \
  | jq '.result.extension.access_token_duration // .'
```

### 2. 挙動確認

```bash
start_auth_flow
register_user "token-$(date +%s)@example.com" "TestPass123" "Token User"
complete_auth_flow

echo "--- 即座に UserInfo（成功するはず） ---"
get_userinfo | jq .

echo ""
echo "--- 15秒待機... ---"
sleep 15

echo "--- 期限切れ後に UserInfo（401 になるはず） ---"
curl -s -w "\nHTTP %{http_code}" -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  "${TENANT_BASE}/v1/userinfo"

echo ""
echo "--- リフレッシュトークンで再取得 ---"
REFRESH_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

NEW_ACCESS_TOKEN=$(echo "${REFRESH_RESPONSE}" | jq -r '.access_token')
NEW_EXPIRES_IN=$(echo "${REFRESH_RESPONSE}" | jq -r '.expires_in')
echo "New AT expires_in: ${NEW_EXPIRES_IN}s"

echo "--- 新しい AT で UserInfo（成功するはず） ---"
get_userinfo "${NEW_ACCESS_TOKEN}" | jq .
```

### 3. 期待結果

| タイミング | 結果 | 理由 |
|-----------|------|------|
| 即座に UserInfo | 200 OK | AT 有効期間内 |
| 15秒後に UserInfo | 401 Unauthorized | AT 期限切れ（10秒） |
| RT で再取得 → UserInfo | 200 OK | 新しい AT が有効 |
| 新 AT の `expires_in` | `10` | 設定した有効期限が反映されている |

### 4. 元に戻す

```bash
restore_auth_server
```

---

## 実験一覧

| # | やりたいこと | 変わる設定 | 確認できること | 検証方法 |
|---|------------|-----------|--------------|---------|
| 1 | パスワードを禁止してFIDO2必須にしたい | `available_methods` から `password` 除外 | view-data の `available_methods` から `password` が消える | CLI |
| 2 | デバイス登録数を制限したい | `authentication_device_rule.max_devices` | 上限超過で登録拒否される | ブラウザ |
| 3 | MFA なしでデバイス登録するとどうなるか | `device_registration_conditions` 削除 | MFA なしで登録可能（脆弱性体験） | ブラウザ |
| 4 | パスワードフォールバックをロックしたい | `max_attempts` + `failure_conditions` | N回失敗でロックされる | CLI |
| 5 | UserInfo の返却値を制御したい | `claims_supported` | クレームが消える/増える | CLI |
| 6 | AT 有効期限を短くしたい | `access_token_duration` | 期限切れで 401 → RT で復活 | CLI |
