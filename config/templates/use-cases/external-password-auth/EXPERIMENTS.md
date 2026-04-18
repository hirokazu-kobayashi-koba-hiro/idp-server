# 設定変更 × 挙動確認 実験ガイド

設定を1つ変えて → 挙動がどう変わるかを手元で確認するためのガイドです。
「この設定って何に効くの？」を体感で理解できます。

> **前提**: `setup.sh` が正常に完了し、モックサーバー（`mock-server.js`）が起動していること。

---

## 共通準備

`helpers.sh` を source すると、変数・関数がすべて使えるようになります。

```bash
cd config/templates/use-cases/external-password-auth
source helpers.sh                  # デフォルト組織名
# source helpers.sh --org my-org   # 組織名を指定する場合

get_admin_token   # 設定変更に必要な管理トークンを取得
```

> **重要**: テナント更新 API（PUT）は**フル置換**です。
> 送らなかったフィールドは空のデフォルトにリセットされます。
> `helpers.sh` の `update_tenant` / `update_auth_server` / `update_auth_config` を使えば、ベースのJSON から変えたいフィールドだけ上書きして送れます。

> **トラブルシューティング**: 設定変更が効かない場合、まずAPIレスポンスを確認してください。
> `update_tenant` / `update_auth_server` のレスポンスにエラーが含まれている場合、
> 管理トークンの期限切れ（`get_admin_token` を再実行）や、リクエストの不備が原因です。
> レスポンス全体を見るには `| jq .` を付けてください。

### モックサーバーの起動

```bash
# 別ターミナルで起動（依存パッケージ不要）
node mock-server.js
# → Mock auth server running on http://localhost:4001
```

**モックサーバーの認証ルール**:
- `password` が `"invalid"` → 401（認証失敗）
- `username` / `password` が空 → 401（認証失敗）
- それ以外 → 200（認証成功）

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
| `update_auth_config` | 認証メソッド設定を部分変更 | `update_auth_config '.interactions[...].execution.http_request.url = "..."'` |
| `restore_auth_config` | 認証メソッド設定を元に戻す | `restore_auth_config` |
| `update_client` | クライアント設定を部分変更 | `update_client '.scope = "openid profile"'` |
| `restore_client` | クライアント設定を元に戻す | `restore_client` |
| `start_auth_flow` | 認可リクエスト開始 | `start_auth_flow` / `start_auth_flow openid` |
| `password_login` | パスワード認証（外部サービス経由） | `password_login "a@b.com" "Pass123"` |
| `complete_auth_flow` | 認可→トークン取得 | `complete_auth_flow` |
| `get_userinfo` | UserInfo 取得 | `get_userinfo` / `get_userinfo "${OTHER_TOKEN}"` |

---

## Experiment 1: 外部認証サービスでログインする（基本動作確認）

> **やりたいこと**: 外部パスワード認証委譲が正しく動作するか確認したい
>
> **確認する設定**: `authentication-configurations`（http_request executor）

### 1. 認証成功を確認

```bash
start_auth_flow

echo "--- 正しいパスワードでログイン（モックサーバーは invalid 以外を受け入れる） ---"
password_login "user1@example.com" "correct-password" | jq .
```

### 2. 認可→トークン取得→UserInfo

```bash
complete_auth_flow

echo "--- UserInfo ---"
get_userinfo | jq .
```

### 3. 認証失敗を確認

```bash
start_auth_flow

echo "--- 間違ったパスワード（invalid）でログイン → 拒否されるはず ---"
password_login "user1@example.com" "invalid" | jq .
```

### 4. 期待結果

| パスワード | 結果 | 理由 |
|-----------|------|------|
| `correct-password` | 成功 | モックサーバーが 200 を返す |
| `invalid` | 拒否 | モックサーバーが 401 を返す |

> UserInfo には `sub`, `email`, `name` 等が含まれるはずです。
> `email` は外部サービスから返された値がマッピングされます。

---

## Experiment 2: アカウントロックを体験する

> **やりたいこと**: N回パスワードを間違えたらロックされることを確認したい
>
> **変わる設定**: 認証ポリシー `failure_conditions` / `lock_conditions`
>
> **login-password-only との違い**: このテンプレートはデフォルトで failure/lock_conditions が有効（5回）。この実験では閾値を3回に変更してロックを素早く体験する。

> **前提**: `password_login` 関数はリクエストボディに `provider_id` を含めて送信します。
> これにより認証失敗時でも `tryResolveUserForLogging` が正しい `provider_id` で
> ユーザーを検索し、ロックイベントが発火します。

### 1. 設定変更：ロック閾値を 3回に

```bash
update_auth_policy '{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "external_password_lock_3",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password"],
      "success_conditions": {
        "any_of": [
          [{"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}]
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

### 2. ユーザーを事前作成する（成功ログイン）

> **重要**: 外部パスワード認証では、ユーザーは初回の認証成功＋認可コード発行時に DB に登録されます。
> ユーザーが DB に存在しない状態で失敗を繰り返しても、ロック対象のユーザーがいないため
> ロックが意味をなしません。先にユーザーを作成しておく必要があります。

```bash
LOCK_EMAIL="locktest-$(date +%s)@example.com"

echo "--- ユーザー事前作成: 正しいパスワードでログイン＋認可コード取得 ---"
start_auth_flow
password_login "${LOCK_EMAIL}" "correct-password" > /dev/null
complete_auth_flow > /dev/null
echo "ユーザー作成完了: ${LOCK_EMAIL}"
```

### 3. 挙動確認：新しい認可フローで3回間違える

```bash
start_auth_flow

echo "--- 1回目: 間違ったパスワード ---"
password_login "${LOCK_EMAIL}" "invalid" | jq .

echo "--- 2回目: 間違ったパスワード ---"
password_login "${LOCK_EMAIL}" "invalid" | jq .

echo "--- 3回目: 間違ったパスワード → ここでロック ---"
password_login "${LOCK_EMAIL}" "invalid" | jq .
```

### 4. ロック確認：正しいパスワードでも認可コードが発行されない

```bash
echo "--- 2秒待機（非同期ロック処理の完了を待つ） ---"
sleep 2

echo "--- 4回目: 正しいパスワード → ロック中 ---"
start_auth_flow
password_login "${LOCK_EMAIL}" "correct-password" | jq .

echo "--- 認可コード取得 → isSuccess()=false のため失敗するはず ---"
complete_auth_flow | jq .
```

### 5. 期待結果

| ステップ | パスワード | 結果 | 理由 |
|---------|-----------|------|------|
| 事前作成 | `correct-password` | 成功 | ユーザーを DB に登録 |
| 1回目 | `invalid` | 認証失敗 | モックサーバーが 401 |
| 2回目 | `invalid` | 認証失敗 | モックサーバーが 401 |
| 3回目 | `invalid` | ロック発動 | `failure_count >= 3` で `lock_conditions` に該当、ユーザーステータスが LOCKED に変更 |
| 4回目 | `correct-password` | 外部サービスは成功 | モックサーバーが 200 を返すが `isSuccess()` = false |
| 認可コード取得 | — | **失敗** | `isLocked()` = true → 認可コード発行されない |

> **確認ポイント**:
> - 3回目の失敗時点で `user_lock` イベントが発火し、ユーザーステータスが LOCKED に変更される
> - 4回目のレスポンスに `"status": "LOCKED"` が含まれることで、ロック状態を確認できる
> - 認可コード取得は二重に防御される:
>   - `AuthenticationTransaction.isSuccess()` が `isLocked()` をガードし false を返す
>   - `OAuthAuthorizeHandler` がユーザーステータスをチェックし `User status is not active, cannot authorize. (LOCKED)` で拒否
> - セキュリティイベントには `password_failure` × 3 + `user_lock` × 1 が記録される

### 6. 元に戻す

```bash
restore_auth_policy
```

---

## Experiment 3: 外部認証サービスの URL を変更する

> **やりたいこと**: 外部認証サービスの接続先を切り替えたい
>
> **変わる設定**: `authentication-configurations` の `execution.http_request.url`
>
> **外部パスワード認証固有**: login-password-only には存在しない、このテンプレート特有の実験。

### 1. 設定変更：存在しないURLに変更

```bash
update_auth_config '
  .interactions["password-authentication"].execution.http_request.url = "http://host.docker.internal:9999/auth/not-exist"
' | jq '.interactions["password-authentication"].execution.http_request.url // .'
```

### 2. 挙動確認：認証を試みる → 外部サービスに接続できず失敗

```bash
start_auth_flow

echo "--- 存在しないURLに認証リクエスト → エラーになるはず ---"
password_login "test@example.com" "correct-password" | jq .
```

### 3. 設定変更：正しいURLに戻す

```bash
restore_auth_config

echo "--- 元のURLに戻して認証 → 成功するはず ---"
start_auth_flow
password_login "test@example.com" "correct-password" | jq .
```

### 4. 期待結果

| URL | 結果 | 理由 |
|-----|------|------|
| `http://host.docker.internal:9999/auth/not-exist` | エラー | 外部サービスに接続不可 |
| `http://host.docker.internal:4001/auth/password`（元の設定） | 成功 | モックサーバーが応答 |

---

## Experiment 4: マッピングルールを変更して UserInfo の内容を変える

> **やりたいこと**: 外部サービスのレスポンスから別のフィールドをマッピングしたい
>
> **変わる設定**: `authentication-configurations` の `user_mapping_rules`
>
> **外部パスワード認証固有**: 外部レスポンスと idp-server ユーザー属性の対応を自由に変更できる。

### 1. ベースラインを確認

```bash
start_auth_flow
password_login "mapping-$(date +%s)@example.com" "correct-password" > /dev/null
complete_auth_flow > /dev/null

echo "--- ベースライン: UserInfo ---"
get_userinfo | jq .
```

> `name` が `"External User"`（モックサーバーのデフォルト）のはずです。

### 2. 設定変更：name マッピングを email に上書き

```bash
update_auth_config '
  .interactions["password-authentication"].user_resolve.user_mapping_rules = [
    { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
    { "from": "$.execution_http_request.response_body.email", "to": "email" },
    { "from": "$.execution_http_request.response_body.email", "to": "name" },
    { "static_value": "external-auth", "to": "provider_id" }
  ]
' | jq '.interactions["password-authentication"].user_resolve.user_mapping_rules // .'
```

### 3. 挙動確認：新しいユーザーでログイン

```bash
MAPPING_EMAIL="mapped-$(date +%s)@example.com"
start_auth_flow
password_login "${MAPPING_EMAIL}" "correct-password" > /dev/null
complete_auth_flow > /dev/null

echo "--- マッピング変更後: UserInfo ---"
get_userinfo | jq '{name, email}'
```

### 4. 期待結果

| タイミング | `name` の値 | 理由 |
|-----------|------------|------|
| 変更前 | `External User` | `response_body.name` からマッピング |
| 変更後 | `mapped-xxxx@example.com` | `response_body.email` を `name` にマッピング |

### 5. 元に戻す

```bash
restore_auth_config
```

---

## Experiment 5: claims_supported を変えて UserInfo の返却値を確認する

> **やりたいこと**: UserInfo で返るクレームを制御したい
>
> **変わる設定**: `authorization_server.claims_supported`

### 1. まずベースラインを確認

```bash
start_auth_flow
password_login "claims-$(date +%s)@example.com" "correct-password" > /dev/null
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
password_login "claims2-$(date +%s)@example.com" "correct-password" > /dev/null
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

### 1. 設定変更：AT を 10秒に

```bash
update_auth_server '.extension.access_token_duration = 10' \
  | jq '.result.extension.access_token_duration // .'
```

### 2. 挙動確認

```bash
start_auth_flow
password_login "token-$(date +%s)@example.com" "correct-password" > /dev/null
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

## Experiment 7: セッション有効期限を変えて再認証を体験する

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
start_auth_flow
password_login "session-$(date +%s)@example.com" "correct-password" > /dev/null
complete_auth_flow > /dev/null

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

### 4. 元に戻す

```bash
restore_tenant
```

---

## Experiment 8: プロバイダーIDを変更して複数外部サービスの識別を確認する

> **やりたいこと**: 外部サービスの provider_id を変えると、ユーザーがどう識別されるか確認したい
>
> **変わる設定**: `authentication-configurations` の `user_mapping_rules` (static_value: provider_id)
>
> **外部パスワード認証固有**: provider_id によって外部サービスの区別ができる。

### 1. provider_id = "external-auth" でログイン（デフォルト）

```bash
PROVIDER_EMAIL="provider-$(date +%s)@example.com"
start_auth_flow
password_login "${PROVIDER_EMAIL}" "correct-password" > /dev/null
complete_auth_flow > /dev/null

echo "--- provider_id=external-auth（デフォルト）---"
get_userinfo | jq .
```

### 2. 設定変更：provider_id を変更

```bash
update_auth_config '
  .interactions["password-authentication"].user_resolve.user_mapping_rules[-1].static_value = "ldap-wrapper"
' | jq '.interactions["password-authentication"].user_resolve.user_mapping_rules // .'
```

### 3. 挙動確認：同じメールアドレスでログイン → 別ユーザーとして扱われる

```bash
start_auth_flow
password_login "${PROVIDER_EMAIL}" "correct-password" > /dev/null
complete_auth_flow > /dev/null

echo "--- provider_id=ldap-wrapper ---"
get_userinfo | jq .
```

### 4. 期待結果

| provider_id | 結果 | 理由 |
|-------------|------|------|
| `external-auth` | ユーザーA として認証 | `sub` は最初のログインで作成されたユーザー |
| `ldap-wrapper` | 別のユーザーとして新規作成 | `provider_id` が異なるため同一ユーザーと見なされない |

> `identity_unique_key_type = "EMAIL_OR_EXTERNAL_USER_ID"` のため、
> `external_user_id` + `provider_id` の組み合わせでユーザーが識別されます。

### 5. 元に戻す

```bash
restore_auth_config
```

---

## Experiment 9: リクエストのボディフィールド名を変更する

> **やりたいこと**: 外部サービスが期待するフィールド名が `username` / `password` でない場合に対応したい
>
> **変わる設定**: `authentication-configurations` の `execution.http_request.body_mapping_rules`
>
> **外部パスワード認証固有**: 外部サービスの API 契約に合わせてリクエストボディのフィールド名を自由に変更できる。

### 1. ベースラインを確認（デフォルト: `username` / `password`）

```bash
echo "--- 現在の body_mapping_rules ---"
cat "${CONFIG_DIR}/authentication-config-password.json" | \
  jq '.interactions["password-authentication"].execution.http_request.body_mapping_rules'

start_auth_flow
echo "--- デフォルトのフィールド名で認証（成功するはず） ---"
password_login "body-test@example.com" "correct-password" | jq .
```

### 2. 設定変更：`username` → `user` に変更

```bash
update_auth_config '
  .interactions["password-authentication"].execution.http_request.body_mapping_rules = [
    { "from": "$.request_body.username", "to": "user" },
    { "from": "$.request_body.password", "to": "password" }
  ]
' | jq '.interactions["password-authentication"].execution.http_request.body_mapping_rules // .'
```

> idp-server は外部サービスに `{"user": "...", "password": "..."}` を送るようになります。
> モックサーバーは `{"username": "...", "password": "..."}` を期待しているため、
> `username` が `undefined` → 認証失敗になります。

### 3. 挙動確認：認証を試みる → フィールド名不一致で失敗

```bash
start_auth_flow
echo "--- フィールド名変更後に認証 → 失敗するはず ---"
password_login "body-test@example.com" "correct-password" | jq .
```

### 4. 期待結果

| `body_mapping_rules` の `to` | 外部サービスに送るJSON | 結果 | 理由 |
|------|------|------|------|
| `"username"` (デフォルト) | `{"username": "...", "password": "..."}` | 成功 | モックが期待するフィールド名と一致 |
| `"user"` (変更後) | `{"user": "...", "password": "..."}` | 失敗 | モックは `username` を期待しているが `user` が来た |

> **ポイント**: 外部サービスの API 仕様に合わせて `body_mapping_rules` の `to` を正確に設定する必要がある。
> フィールド名の不一致は認証失敗の原因になるため、外部サービスの API ドキュメントを必ず確認すること。

### 5. 元に戻す

```bash
restore_auth_config
```

---

## Experiment 10: カスタムヘッダーを外部サービスに送信する

> **やりたいこと**: 外部サービスが API キーや認証トークンをヘッダーで要求する場合に対応したい
>
> **変わる設定**: `authentication-configurations` の `execution.http_request.header_mapping_rules`
>
> **外部パスワード認証固有**: 外部サービスへのリクエストにカスタムヘッダーを追加できる。API キー認証やトレーサビリティ用のヘッダーを設定する場面で使う。

### 1. ベースラインを確認

```bash
echo "--- 現在の header_mapping_rules ---"
cat "${CONFIG_DIR}/authentication-config-password.json" | \
  jq '.interactions["password-authentication"].execution.http_request.header_mapping_rules'
```

> デフォルトは `Content-Type: application/json` のみ。

### 2. 設定変更：API キーヘッダーを追加

```bash
update_auth_config '
  .interactions["password-authentication"].execution.http_request.header_mapping_rules = [
    { "static_value": "application/json", "to": "Content-Type" },
    { "static_value": "my-secret-api-key-123", "to": "X-Api-Key" },
    { "static_value": "idp-server", "to": "X-Request-Source" }
  ]
' | jq '.interactions["password-authentication"].execution.http_request.header_mapping_rules // .'
```

### 3. 挙動確認：認証を試みる

```bash
start_auth_flow
echo "--- カスタムヘッダー追加後に認証（モックはヘッダーを検証しないので成功するはず） ---"
password_login "header-test@example.com" "correct-password" | jq .
```

### 4. 設定変更：Content-Type を削除してみる

```bash
update_auth_config '
  .interactions["password-authentication"].execution.http_request.header_mapping_rules = [
    { "static_value": "my-secret-api-key-123", "to": "X-Api-Key" }
  ]
' | jq '.interactions["password-authentication"].execution.http_request.header_mapping_rules // .'

start_auth_flow
echo "--- Content-Type なしで認証 → モックはJSONパース可能なので成功する場合もある ---"
password_login "header-test2@example.com" "correct-password" | jq .
```

### 5. 期待結果

| header_mapping_rules | 結果 | 理由 |
|-----|------|------|
| `Content-Type` + `X-Api-Key` + `X-Request-Source` | 成功 | モックはヘッダーを検証しない |
| `X-Api-Key` のみ（`Content-Type` なし） | 成功 / 失敗 | 外部サービスの実装による |

> **ポイント**: 実際の外部サービスでは `Content-Type` が必須の場合が多い。
> また `Authorization: Bearer <token>` や `X-Api-Key` でリクエストを認証するケースでは、
> `static_value` に秘密鍵やトークンを直接設定する。
>
> **`from` でリクエストヘッダーを転送**: `{ "from": "$.request_headers.X-Forwarded-For", "to": "X-Forwarded-For" }` のように、
> 元のリクエストのヘッダーを外部サービスに転送することも可能。

### 6. 元に戻す

```bash
restore_auth_config
```

---

## Experiment 11: レスポンスの条件付きマッピングを確認する

> **やりたいこと**: 外部サービスのレスポンスから、条件に応じてフィールドを返却したい / 条件の効果を確認したい
>
> **変わる設定**: `authentication-configurations` の `response.body_mapping_rules` の `condition`
>
> **外部パスワード認証固有**: `condition` を使うと、外部サービスのレスポンスに特定のフィールドが存在する場合のみマッピングできる。成功時は `user_id` / `email`、失敗時は `error` / `error_description` を返すパターン。

### 1. ベースラインを確認（条件付きマッピング有効）

```bash
echo "--- 現在の response.body_mapping_rules ---"
cat "${CONFIG_DIR}/authentication-config-password.json" | \
  jq '.interactions["password-authentication"].response.body_mapping_rules'

echo ""
echo "--- 認証成功時のレスポンス（user_id, email あり / error なし） ---"
start_auth_flow
password_login "condition-$(date +%s)@example.com" "correct-password" | jq .

echo ""
echo "--- 認証失敗時のレスポンス（error, error_description あり / user_id なし） ---"
start_auth_flow
password_login "condition-fail@example.com" "invalid" | jq .
```

### 2. 設定変更：condition を全て削除

```bash
update_auth_config '
  .interactions["password-authentication"].response.body_mapping_rules = [
    { "from": "$.execution_http_request.response_body.user_id", "to": "user_id" },
    { "from": "$.execution_http_request.response_body.email", "to": "email" },
    { "from": "$.execution_http_request.response_body.error", "to": "error" },
    { "from": "$.execution_http_request.response_body.error_description", "to": "error_description" }
  ]
' | jq '.interactions["password-authentication"].response.body_mapping_rules // .'
```

### 3. 挙動確認：成功/失敗時のレスポンスを比較

```bash
echo "--- condition 削除後: 認証成功時のレスポンス ---"
start_auth_flow
password_login "nocond-$(date +%s)@example.com" "correct-password" | jq .

echo ""
echo "--- condition 削除後: 認証失敗時のレスポンス ---"
start_auth_flow
password_login "nocond-fail@example.com" "invalid" | jq .
```

### 4. 期待結果

| condition | 認証成功時のレスポンス | 認証失敗時のレスポンス |
|-----------|---------------------|---------------------|
| あり（デフォルト） | `user_id`, `email` のみ（error は condition 不成立で省略） | `error`, `error_description` のみ（user_id は condition 不成立で省略） |
| なし（削除後） | `user_id`, `email`, `error`(null), `error_description`(null) 全て含まれる | `error`, `error_description`, `user_id`(null), `email`(null) 全て含まれる |

> **ポイント**: `condition` を使うことで、成功/失敗のレスポンスを綺麗に分離できる。
> `condition` なしだと、存在しないフィールドも null として含まれるため、
> クライアント側で null チェックが必要になる。

### 5. 元に戻す

```bash
restore_auth_config
```

---

## Experiment 12: ユーザー識別方式を変更する（identity_unique_key_type）

> **やりたいこと**: ユーザーの一意識別キーを `EMAIL_OR_EXTERNAL_USER_ID` から `EMAIL` に変えたらどうなるか確認したい
>
> **変わる設定**: テナント `identity_policy_config.identity_unique_key_type`
>
> **外部パスワード認証固有**: `EMAIL_OR_EXTERNAL_USER_ID` は `external_user_id` + `provider_id` でユーザーを識別する（外部サービス前提の設定）。`EMAIL` に変えるとメールアドレスのみで識別する。

### 1. ベースラインを確認（EMAIL_OR_EXTERNAL_USER_ID）

```bash
echo "--- 現在の identity_unique_key_type ---"
echo "${TENANT_JSON}" | jq '.identity_policy_config.identity_unique_key_type'
```

### 2. provider_id が異なると別ユーザーになることを確認

```bash
ID_EMAIL="idtype-$(date +%s)@example.com"

echo "--- provider_id=external-auth でログイン ---"
start_auth_flow
password_login "${ID_EMAIL}" "correct-password" > /dev/null
complete_auth_flow > /dev/null
FIRST_SUB=$(get_userinfo | jq -r '.sub')
echo "sub: ${FIRST_SUB}"

echo ""
echo "--- provider_id を ldap-wrapper に変更 ---"
update_auth_config '
  .interactions["password-authentication"].user_resolve.user_mapping_rules[-1].static_value = "ldap-wrapper"
' > /dev/null

echo "--- 同じメールで再ログイン ---"
start_auth_flow
password_login "${ID_EMAIL}" "correct-password" > /dev/null
complete_auth_flow > /dev/null
SECOND_SUB=$(get_userinfo | jq -r '.sub')
echo "sub: ${SECOND_SUB}"

echo ""
echo "--- 比較: EMAIL_OR_EXTERNAL_USER_ID モード ---"
echo "同じメール / 異なる provider_id → sub が異なる: ${FIRST_SUB} vs ${SECOND_SUB}"

restore_auth_config > /dev/null
```

### 3. 設定変更：identity_unique_key_type を EMAIL に変更

```bash
update_tenant '.identity_policy_config.identity_unique_key_type = "EMAIL"' \
  | jq '.result.identity_policy_config.identity_unique_key_type // .'
```

### 4. 挙動確認：provider_id が異なっても同一ユーザーになる

```bash
ID_EMAIL2="idtype2-$(date +%s)@example.com"

echo "--- provider_id=external-auth でログイン ---"
start_auth_flow
password_login "${ID_EMAIL2}" "correct-password" > /dev/null
complete_auth_flow > /dev/null
THIRD_SUB=$(get_userinfo | jq -r '.sub')
echo "sub: ${THIRD_SUB}"

echo ""
echo "--- provider_id を ldap-wrapper に変更 ---"
update_auth_config '
  .interactions["password-authentication"].user_resolve.user_mapping_rules[-1].static_value = "ldap-wrapper"
' > /dev/null

echo "--- 同じメールで再ログイン ---"
start_auth_flow
password_login "${ID_EMAIL2}" "correct-password" > /dev/null
complete_auth_flow > /dev/null
FOURTH_SUB=$(get_userinfo | jq -r '.sub')
echo "sub: ${FOURTH_SUB}"

echo ""
echo "--- 比較: EMAIL モード ---"
echo "同じメール / 異なる provider_id → sub が同じ: ${THIRD_SUB} vs ${FOURTH_SUB}"
```

### 5. 期待結果

| identity_unique_key_type | 同一メール + 異なる provider_id | 理由 |
|--------------------------|-------------------------------|------|
| `EMAIL_OR_EXTERNAL_USER_ID` (デフォルト) | **別ユーザー** (`sub` が異なる) | `external_user_id` + `provider_id` で識別するため |
| `EMAIL` | **同一ユーザー** (`sub` が同じ) | メールアドレスのみで識別するため |

> **ポイント**: 外部認証委譲では通常 `EMAIL_OR_EXTERNAL_USER_ID` を使う。
> - **複数の外部サービスを併用する場合**: `EMAIL_OR_EXTERNAL_USER_ID` が必須（provider_id で区別）
> - **外部サービスが1つだけの場合**: `EMAIL` でも運用可能（シンプル）
> - **移行時の注意**: 途中で変更すると既存ユーザーの識別に影響が出る可能性がある

### 6. 元に戻す

```bash
restore_auth_config > /dev/null
restore_tenant
```

---

## 実験一覧

| # | やりたいこと | 変わる設定 | 確認できること |
|---|------------|-----------|--------------|
| 1 | 外部認証の基本動作 | （設定変更なし） | 認証成功/失敗の基本フロー |
| 2 | アカウントロックしたい | `failure_conditions` / `lock_conditions` | N回失敗でロックされる |
| 3 | 外部サービスURLを変えたい | `http_request.url` | 接続先切り替え、接続エラー |
| 4 | マッピングを変えたい | `user_mapping_rules` | UserInfo に異なる値が入る |
| 5 | UserInfo の返却値を制御したい | `claims_supported` | クレームが消える/増える |
| 6 | AT 有効期限を短くしたい | `access_token_duration` | 期限切れで 401 → RT で復活 |
| 7 | セッションを短くしたい | `session_config.timeout_seconds` | 期限切れで再認証を要求 |
| 8 | プロバイダーIDを変えたい | `user_mapping_rules` (provider_id) | 同一メールでも別ユーザーになる |
| 9 | リクエストのフィールド名を変えたい | `body_mapping_rules` | 外部APIの契約に合わせた送信 |
| 10 | カスタムヘッダーを送りたい | `header_mapping_rules` | API キー、トレーサビリティヘッダー |
| 11 | レスポンスの条件マッピングを確認 | `response.body_mapping_rules.condition` | 成功/失敗の返却値を分離 |
| 12 | ユーザー識別方式を変えたい | `identity_unique_key_type` | EMAIL vs EXTERNAL_USER_ID の違い |
