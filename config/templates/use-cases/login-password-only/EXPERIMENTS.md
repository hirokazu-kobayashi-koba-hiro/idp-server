# 設定変更 × 挙動確認 実験ガイド

設定を1つ変えて → 挙動がどう変わるかを手元で確認するためのガイドです。
「この設定って何に効くの？」を体感で理解できます。

> **前提**: `setup.sh` が正常に完了していること。

---

## 共通準備

`helpers.sh` を source すると、変数・関数がすべて使えるようになります。

```bash
cd config/templates/use-cases/login-password-only
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
| `update_tenant` | テナント設定を部分変更 | `update_tenant '.session_config.timeout_seconds = 15'` |
| `restore_tenant` | テナント設定を元に戻す | `restore_tenant` |
| `update_auth_server` | 認可サーバー設定を部分変更 | `update_auth_server '.extension.access_token_duration = 10'` |
| `restore_auth_server` | 認可サーバー設定を元に戻す | `restore_auth_server` |
| `update_auth_policy` | 認証ポリシーを更新 | `update_auth_policy '{"flow":"oauth",...}'` |
| `restore_auth_policy` | 認証ポリシーを元に戻す | `restore_auth_policy` |
| `start_auth_flow` | 認可リクエスト開始 | `start_auth_flow` / `start_auth_flow openid` |
| `register_user` | ユーザー登録 | `register_user "a@b.com" "Pass123" "Name"` |
| `password_login` | パスワード認証 | `password_login "a@b.com" "Pass123"` |
| `complete_auth_flow` | 認可→トークン取得 | `complete_auth_flow` |
| `get_userinfo` | UserInfo 取得 | `get_userinfo` / `get_userinfo "${OTHER_TOKEN}"` |

---

## Experiment 1: パスワードポリシーを強化する

> **やりたいこと**: パスワードに大文字と数字を必須にしたい
>
> **変わる設定**: `identity_policy_config.password_policy`

### 1. 設定変更

```bash
update_tenant '
  .identity_policy_config.password_policy.min_length = 12 |
  .identity_policy_config.password_policy.require_uppercase = true |
  .identity_policy_config.password_policy.require_lowercase = true |
  .identity_policy_config.password_policy.require_number = true
' | jq '.result.identity_policy_config.password_policy // .'
```

### 2. 挙動確認：弱いパスワードで登録 → 拒否される

```bash
start_auth_flow

# NG: 小文字のみ・8文字 → 拒否されるはず
echo "--- 弱いパスワードで登録 ---"
register_user "weak-$(date +%s)@example.com" "password" | jq .

# NG: 大文字あるが数字なし → 拒否されるはず
echo "--- 数字なしで登録 ---"
register_user "nonum-$(date +%s)@example.com" "WeakPassword" | jq .

# OK: 大文字 + 小文字 + 数字 + 12文字以上 → 成功するはず
echo "--- 強いパスワードで登録 ---"
register_user "strong-$(date +%s)@example.com" "StrongPass123" | jq .
```

### 3. 期待結果

| パスワード | 結果 | 理由 |
|-----------|------|------|
| `password` | 拒否 | 小文字のみ、8文字（12文字未満） |
| `WeakPassword` | 拒否 | 数字なし |
| `StrongPass123` | 成功 | 大文字 + 小文字 + 数字 + 13文字 |

### 4. 元に戻す

```bash
restore_tenant
```

---

## Experiment 2: アカウントロックを体験する

> **やりたいこと**: 3回パスワードを間違えたらロックしたい
>
> **変わる設定**: `password_policy.max_attempts` + 認証ポリシー `failure_conditions`

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

# 認証ポリシーに failure_conditions / lock_conditions を追加
update_auth_policy '{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "password_with_lock",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password", "initial-registration"],
      "success_conditions": {
        "any_of": [
          [{"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}],
          [{"path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1}]
        ]
      },
      "failure_conditions": {
        "any_of": [
          [{"path": "$.password-authentication.failure_count", "type": "integer", "operation": "gte", "value": 3}]
        ]
      },
      "lock_conditions": {
        "any_of": [
          [{"path": "$.password-authentication.failure_count", "type": "integer", "operation": "gte", "value": 3}]
        ]
      }
    }
  ]
}' | jq '{id, flow, enabled} // .'
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

## Experiment 3: claims_supported を変えて UserInfo の返却値を確認する

> **やりたいこと**: UserInfo で返るクレームを制御したい
>
> **変わる設定**: `authorization_server.claims_supported`

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

## Experiment 4: トークン有効期限を短くして期限切れを体験する

> **やりたいこと**: アクセストークンの有効期限を短くしたい → 期限切れ後にどうなるか見たい
>
> **変わる設定**: `authorization_server.extension.access_token_duration`

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

## Experiment 5: セッション有効期限を変えて再認証を体験する

> **やりたいこと**: セッション有効期限を短くしたい → 期限切れ後にどうなるか見たい
>
> **変わる設定**: `session_config.timeout_seconds`
>
> **重要**: セッションの `expiresAt` はセッション作成時に確定します。
> そのため、**設定変更前に作られたセッションは影響を受けません**。
> この実験では **設定変更後に新しいセッションを作って** 挙動を確認します。

### 1. 設定変更：セッションを 15秒に

```bash
update_tenant '.session_config.timeout_seconds = 15' \
  | jq '.result.session_config // .'
```

### 2. 挙動確認：設定変更後に新しいセッションを作成

```bash
# 設定変更後に新しいユーザー＋新しいセッションを作成
TEST_EMAIL="session-$(date +%s)@example.com"
start_auth_flow
register_user "${TEST_EMAIL}" "TestPass123" "Session User"
complete_auth_flow

# この時点のセッションは timeout=15秒 で作成されている
try_prompt_none "即座（セッション有効）"

echo ""; echo "20秒待機..."; sleep 20
try_prompt_none "20秒後（セッション切れ）"
```

### 3. 期待結果

```
--- 即座（セッション有効） ---
  Result: session valid (code issued)

--- 20秒後（セッション切れ） ---
  Result: login_required
  # または
  Result: redirect to login (session expired)
```

> **うまくいかない場合**: テナント設定のキャッシュがRedis上に残っている可能性があります。
> 設定変更後、数秒待ってから新しいセッションを作成してみてください。

### 4. 元に戻す

```bash
restore_tenant
```

---

## Experiment 6: スコープによるクレーム出し分けを確認する

> **やりたいこと**: スコープを変えると UserInfo の返却値がどう変わるか見たい
>
> **変わる設定**: 認可リクエストの `scope` パラメータ（設定変更ではなくリクエスト側の違い）

### 1. scope=openid のみ

```bash
start_auth_flow "openid"   # scope を引数で指定
register_user "scope1-$(date +%s)@example.com" "TestPass123" "Scope User" > /dev/null
complete_auth_flow > /dev/null

echo "--- scope=openid のみ ---"
get_userinfo | jq .
```

### 2. scope=openid profile email

```bash
start_auth_flow "openid+profile+email"
register_user "scope2-$(date +%s)@example.com" "TestPass123" "Scope User 2" > /dev/null
complete_auth_flow > /dev/null

echo "--- scope=openid profile email ---"
get_userinfo | jq .
```

### 3. 期待結果

| scope | UserInfo に含まれるクレーム |
|-------|--------------------------|
| `openid` | `sub` のみ |
| `openid profile` | `sub` + `name`, `given_name`, `family_name` 等 |
| `openid profile email` | 上記 + `email`, `email_verified` |

---

## 実験一覧

| # | やりたいこと | 変わる設定 | 確認できること |
|---|------------|-----------|--------------|
| 1 | パスワードを強化したい | `password_policy.require_*` | 弱いパスワードが拒否される |
| 2 | アカウントロックしたい | `max_attempts` + `failure_conditions` | N回失敗でロックされる |
| 3 | UserInfo の返却値を制御したい | `claims_supported` | クレームが消える/増える |
| 4 | AT 有効期限を短くしたい | `access_token_duration` | 期限切れで 401 → RT で復活 |
| 5 | セッションを短くしたい | `session_config.timeout_seconds` | 期限切れで再認証を要求 |
| 6 | スコープで出し分けたい | 認可リクエストの `scope` | scope による UserInfo の違い |
