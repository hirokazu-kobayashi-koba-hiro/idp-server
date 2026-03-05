# 設定変更 × 挙動確認 実験ガイド

設定を1つ変えて → 挙動がどう変わるかを手元で確認するためのガイドです。
サードパーティ連携テンプレート固有のトークン戦略・Introspection・M2M設定を体感できます。

> **前提**: `setup.sh` が正常に完了していること。

---

## 共通準備

`helpers.sh` を source すると、変数・関数がすべて使えるようになります。

```bash
cd config/templates/use-cases/third-party
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
| `update_web_client` | Web Client 設定を部分変更 | `update_web_client '.scope = "openid api:read api:write"'` |
| `restore_web_client` | Web Client 設定を元に戻す | `restore_web_client` |
| `start_auth_flow` | 認可リクエスト開始 | `start_auth_flow` / `start_auth_flow "openid+api:read"` |
| `register_user` | ユーザー登録 | `register_user "a@b.com" "Pass123" "Name"` |
| `password_login` | パスワード認証 | `password_login "a@b.com" "Pass123"` |
| `complete_auth_flow` | 認可→トークン取得（Web Client） | `complete_auth_flow` |
| `get_userinfo` | UserInfo 取得 | `get_userinfo` / `get_userinfo "${OTHER_TOKEN}"` |
| `get_view_data` | ViewData 取得（同意画面用データ） | `get_view_data \| jq .` |
| `try_prompt_none` | prompt=none でセッション確認 | `try_prompt_none "ラベル"` |
| `m2m_token` | M2M トークン取得 | `m2m_token` / `m2m_token "api:read"` |
| `introspect_token` | Token Introspection | `introspect_token` / `introspect_token "${TOKEN}"` |
| `revoke_token` | Token Revocation | `revoke_token "${ACCESS_TOKEN}"` |
| `refresh` | リフレッシュトークンで AT 再取得 | `refresh` / `refresh "${OLD_RT}"` |

---

## Experiment 1: AT 有効期限を短くして Introspection で確認する

> **やりたいこと**: AT 有効期限を短くし、期限切れ前後で Introspection の結果がどう変わるか見たい
>
> **変わる設定**: `authorization_server.extension.access_token_duration`
>
> **このテンプレートの特徴**: AT は opaque トークン。JWT と違いクライアント側でデコードできないため、Introspection で有効性を確認する。

### 1. 設定変更：AT を 10秒に

```bash
update_auth_server '.extension.access_token_duration = 10' \
  | jq '.result.extension.access_token_duration // .'
```

### 2. 挙動確認

```bash
start_auth_flow
register_user "at-$(date +%s)@example.com" "TestPass123" "AT User"
complete_auth_flow

echo "--- 即座に Introspection（active: true のはず） ---"
introspect_token | jq '{active, scope, exp}'

echo ""
echo "--- 15秒待機... ---"
sleep 15

echo "--- 期限切れ後に Introspection（active: false のはず） ---"
introspect_token | jq '{active}'

echo ""
echo "--- リフレッシュトークンで AT 再取得 ---"
refresh
echo "新しい AT の expires_in を確認（10 のはず）"

echo "--- 新しい AT で Introspection ---"
introspect_token | jq '{active, scope, exp}'
```

### 3. 期待結果

| タイミング | Introspection 結果 | 理由 |
|-----------|-------------------|------|
| 即座 | `active: true` | AT 有効期間内 |
| 15秒後 | `active: false` | AT 期限切れ（10秒） |
| RT で再取得後 | `active: true` | 新しい AT が有効 |

### 4. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 2: リフレッシュトークンローテーションの挙動を確認する

> **やりたいこと**: RT ローテーション有効時、リフレッシュすると古い RT が無効化されることを確認したい
>
> **変わる設定**: なし（デフォルトで `rotate_refresh_token = true`）

### 1. トークン取得

```bash
start_auth_flow
register_user "rt-rot-$(date +%s)@example.com" "TestPass123" "RT Rotation User"
complete_auth_flow

echo "--- 初回の Refresh Token ---"
echo "RT: ${REFRESH_TOKEN:0:20}..."
OLD_RT="${REFRESH_TOKEN}"
```

### 2. リフレッシュ実行（RT がローテーションされる）

```bash
echo "--- リフレッシュ実行 ---"
refresh
echo "新しい RT: ${REFRESH_TOKEN:0:20}..."

echo ""
echo "--- 古い RT を使ってリフレッシュ（失敗するはず） ---"
RETRY_RESPONSE=$(curl -s \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -u "${CLIENT_ID}:${CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${OLD_RT}")
echo "${RETRY_RESPONSE}" | jq .
```

### 3. 期待結果

| 操作 | 結果 | 理由 |
|------|------|------|
| 初回リフレッシュ | 成功 + 新しい RT 発行 | ローテーション有効 |
| 古い RT で再リフレッシュ | エラー | ローテーションにより旧 RT は無効化済み |

---

## Experiment 3: RT ローテーションを無効にする

> **やりたいこと**: RT ローテーションを無効にして、同じ RT を何度も使えることを確認したい
>
> **変わる設定**: `authorization_server.extension.rotate_refresh_token`

### 1. 設定変更：ローテーションを無効に

```bash
update_auth_server '.extension.rotate_refresh_token = false' \
  | jq '.result.extension.rotate_refresh_token // .'
```

### 2. 挙動確認

```bash
start_auth_flow
register_user "rt-nrot-$(date +%s)@example.com" "TestPass123" "RT NoRotation User"
complete_auth_flow

ORIGINAL_RT="${REFRESH_TOKEN}"
echo "RT: ${ORIGINAL_RT:0:20}..."

echo "--- 1回目のリフレッシュ ---"
refresh
echo "RT 変わった?: $([ "${REFRESH_TOKEN}" = "${ORIGINAL_RT}" ] && echo "同じ（期待通り）" || echo "変わった")"

echo "--- 2回目のリフレッシュ（同じRTで） ---"
refresh "${ORIGINAL_RT}"
echo "RT 変わった?: $([ "${REFRESH_TOKEN}" = "${ORIGINAL_RT}" ] && echo "同じ（期待通り）" || echo "変わった")"
```

### 3. 期待結果

| 操作 | 結果 | 理由 |
|------|------|------|
| 1回目リフレッシュ | 成功 + RT は同じ | ローテーション無効 |
| 2回目リフレッシュ | 成功 + RT は同じ | 同じ RT を繰り返し使える |

### 4. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 4: RT 戦略を SLIDING に変更する

> **やりたいこと**: RT 戦略を SLIDING にして、リフレッシュごとに有効期限が延長されることを確認したい
>
> **変わる設定**: `authorization_server.extension.refresh_token_strategy`
>
> **FIXED vs SLIDING**:
> - FIXED: RT の有効期限は最初の発行時点から固定（例: 7日間）
> - SLIDING: リフレッシュするたびに有効期限が延長される

### 1. 設定変更：RT を短くして SLIDING に

```bash
update_auth_server '
  .extension.refresh_token_duration = 30 |
  .extension.refresh_token_strategy = "SLIDING"
' | jq '.result.extension | {refresh_token_duration, refresh_token_strategy} // .'
```

### 2. 挙動確認

```bash
start_auth_flow
register_user "rt-slide-$(date +%s)@example.com" "TestPass123" "RT Sliding User"
complete_auth_flow

echo "--- 20秒待機（30秒の期限に近づく） ---"
sleep 20

echo "--- リフレッシュ（SLIDING なので期限が延長される） ---"
refresh

echo ""
echo "--- さらに20秒待機（FIXED なら合計40秒で期限切れだが、SLIDING なら延長済み） ---"
sleep 20

echo "--- もう一度リフレッシュ（SLIDING なのでまだ有効のはず） ---"
refresh
echo "成功すれば SLIDING が効いている"
```

### 3. 期待結果

| タイミング | FIXED の場合 | SLIDING の場合 |
|-----------|------------|---------------|
| 20秒後にリフレッシュ | 成功 | 成功 |
| さらに20秒後（合計40秒） | 失敗（30秒で期限切れ） | 成功（リフレッシュ時に期限延長） |

### 4. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 5: M2M クライアントのスコープを制限する

> **やりたいこと**: M2M クライアントで要求可能なスコープを確認し、許可範囲外のスコープが拒否されることを見たい
>
> **変わる設定**: なし（クライアント登録時のスコープ制限の確認）

### 1. M2M で全スコープ取得

```bash
echo "--- api:read api:write の両方を要求 ---"
m2m_token "api:read api:write"
echo ""
introspect_token "${M2M_ACCESS_TOKEN}" "${M2M_CLIENT_ID}" "${M2M_CLIENT_SECRET}" | jq '{active, scope, client_id}'
```

### 2. M2M で一部スコープのみ要求

```bash
echo "--- api:read のみ要求 ---"
m2m_token "api:read"
echo ""
introspect_token "${M2M_ACCESS_TOKEN}" "${M2M_CLIENT_ID}" "${M2M_CLIENT_SECRET}" | jq '{active, scope, client_id}'
```

### 3. 期待結果

| 要求スコープ | 結果 | Introspection の scope |
|-------------|------|----------------------|
| `api:read api:write` | 成功 | `api:read api:write` |
| `api:read` | 成功 | `api:read` |

---

## Experiment 6: AT を JWT に変更してリソースサーバーでローカル検証可能にする

> **やりたいこと**: AT を opaque から JWT に変更し、リソースサーバーが JWKS で署名検証できることを確認したい
>
> **変わる設定**: `authorization_server.extension.access_token_type`
>
> **opaque vs JWT**:
> - opaque: トークンの中身が見えない。リソースサーバーは毎回 Introspection エンドポイントを呼ぶ必要がある
> - JWT: トークン自体にクレームが含まれ、JWKS で署名検証すればローカルで有効性を判定できる

### 1. ベースライン：opaque トークン

```bash
start_auth_flow
register_user "opaque-$(date +%s)@example.com" "TestPass123" "Opaque User"
complete_auth_flow

echo "--- opaque AT（デコード不可） ---"
echo "${ACCESS_TOKEN}" | head -c 60
echo "..."
echo ""

# ピリオド区切りではないのでJWTとして解釈できない
echo "ピリオド数: $(echo "${ACCESS_TOKEN}" | tr -cd '.' | wc -c | tr -d ' ')"
```

### 2. 設定変更：AT を JWT に

```bash
update_auth_server '.extension.access_token_type = "JWT"' \
  | jq '.result.extension.access_token_type // .'
```

### 3. JWT トークンを取得してデコード

```bash
start_auth_flow
register_user "jwt-$(date +%s)@example.com" "TestPass123" "JWT User"
complete_auth_flow

echo "--- JWT AT（3パートに分かれる） ---"
echo "ピリオド数: $(echo "${ACCESS_TOKEN}" | tr -cd '.' | wc -c | tr -d ' ')"

echo ""
echo "--- JWT ヘッダー ---"
echo "${ACCESS_TOKEN}" | cut -d'.' -f1 | base64 -d 2>/dev/null | jq .

echo ""
echo "--- JWT ペイロード ---"
echo "${ACCESS_TOKEN}" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .

echo ""
echo "--- JWKS エンドポイント（署名検証用の公開鍵） ---"
curl -s "${TENANT_BASE}/v1/jwks" | jq '.keys[] | {kid, kty, alg}'

echo ""
echo "--- Introspection も引き続き動作する ---"
introspect_token | jq '{active, scope, client_id}'
```

### 4. M2M でも JWT を確認

```bash
echo "--- M2M client_credentials で JWT トークン ---"
m2m_token
echo "${M2M_ACCESS_TOKEN}" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq '{iss, scope, client_id, exp}'
```

### 5. 期待結果

| 項目 | opaque | JWT |
|------|--------|-----|
| トークン形式 | ランダム文字列 | `header.payload.signature` |
| ピリオド数 | 0 | 2 |
| クライアント側でデコード | 不可 | 可能（`base64 -d`） |
| JWKS で署名検証 | 不要 | 可能 |
| Introspection | 必須 | 任意（ローカル検証可能） |
| ペイロードに `scope` | - | 含まれる |

> **使い分けの指針**:
> - **opaque**: トークン情報を隠蔽したい場合、Revocation を即時反映したい場合
> - **JWT**: リソースサーバーが IdP への問い合わせなしにトークンを検証したい場合（パフォーマンス優先）

### 6. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 7: Token Revocation → Introspection で無効化を確認する

> **やりたいこと**: トークンを Revoke したあと、Introspection で `active: false` になることを確認したい
>
> **変わる設定**: なし（Revocation エンドポイントの動作確認）

### 1. Web Client でトークン取得 → Revoke

```bash
start_auth_flow
register_user "revoke-$(date +%s)@example.com" "TestPass123" "Revoke User"
complete_auth_flow

echo "--- Revoke 前の Introspection ---"
introspect_token | jq '{active, scope}'

echo ""
echo "--- AT を Revoke ---"
revoke_token "${ACCESS_TOKEN}"

echo ""
echo "--- Revoke 後の Introspection ---"
introspect_token | jq '{active}'
```

### 2. M2M トークンも同様に

```bash
echo "--- M2M トークン取得 ---"
m2m_token

echo "--- M2M Revoke 前 ---"
introspect_token "${M2M_ACCESS_TOKEN}" "${M2M_CLIENT_ID}" "${M2M_CLIENT_SECRET}" | jq '{active, scope}'

echo ""
revoke_token "${M2M_ACCESS_TOKEN}" "${M2M_CLIENT_ID}" "${M2M_CLIENT_SECRET}"

echo ""
echo "--- M2M Revoke 後 ---"
introspect_token "${M2M_ACCESS_TOKEN}" "${M2M_CLIENT_ID}" "${M2M_CLIENT_SECRET}" | jq '{active}'
```

### 3. 期待結果

| トークン | Revoke 前 | Revoke 後 |
|---------|----------|----------|
| Web Client AT | `active: true` | `active: false` |
| M2M AT | `active: true` | `active: false` |

---

## Experiment 8: カスタムスコープと UserInfo の関係

> **やりたいこと**: `api:read` はリソースアクセス用スコープであり、UserInfo のクレームには影響しないことを確認したい
>
> **変わる設定**: 認可リクエストの `scope` パラメータ（設定変更ではなくリクエスト側の違い）

### 1. scope=openid+api:read のみ

```bash
start_auth_flow "openid+api:read"
register_user "scope1-$(date +%s)@example.com" "TestPass123" "Scope User 1" > /dev/null
complete_auth_flow > /dev/null

echo "--- scope=openid+api:read ---"
echo "UserInfo:"
get_userinfo | jq .
echo ""
echo "Introspection scope:"
introspect_token | jq '{scope}'
```

### 2. scope=openid+profile+email+api:read

```bash
start_auth_flow "openid+profile+email+api:read"
register_user "scope2-$(date +%s)@example.com" "TestPass123" "Scope User 2" > /dev/null
complete_auth_flow > /dev/null

echo "--- scope=openid+profile+email+api:read ---"
echo "UserInfo:"
get_userinfo | jq .
echo ""
echo "Introspection scope:"
introspect_token | jq '{scope}'
```

### 3. 期待結果

| scope | UserInfo のクレーム | Introspection の scope |
|-------|-------------------|----------------------|
| `openid api:read` | `sub` のみ | `openid api:read` |
| `openid profile email api:read` | `sub`, `name`, `email` 等 | `openid profile email api:read` |

> `api:read` はリソースサーバー向けのスコープなので UserInfo の返却値には影響しません。
> UserInfo で返るクレームは `profile`, `email` 等の OIDC 標準スコープで制御されます。

---

## Experiment 9: クライアント設定を更新して ViewData への反映を確認する

> **やりたいこと**: クライアントのスコープや規約URLを変更し、認可画面用の ViewData に反映されるか確認したい
>
> **変わる設定**: クライアント設定（`scope`, `client_name`, `tos_uri`, `policy_uri`）
>
> **ViewData とは**: 認可リクエスト時にフロントエンド（同意画面）が取得する表示用データ。
> クライアント名、要求スコープ、利用規約URL 等が含まれる。

### 1. ベースライン：現在の ViewData を確認

```bash
start_auth_flow "openid+profile+email+api:read"

echo "--- 現在の ViewData ---"
get_view_data | jq '{client_id, client_name, scopes, tos_uri, policy_uri}'
```

### 2. クライアント設定を更新

```bash
# スコープに api:write を追加 + 利用規約URL を設定
update_web_client '
  .scope = "openid profile email api:read api:write" |
  .client_name = "Updated Web App" |
  .tos_uri = "https://example.com/terms" |
  .policy_uri = "https://example.com/privacy"
' | jq '{client_id, client_name, scope, tos_uri, policy_uri} // .'
```

### 3. 更新後の ViewData を確認

```bash
# 新しい認可リクエスト（api:write も要求）
start_auth_flow "openid+profile+email+api:read+api:write"

echo "--- 更新後の ViewData ---"
get_view_data | jq '{client_id, client_name, scopes, tos_uri, policy_uri}'
```

### 4. 期待結果

| 項目 | 更新前 | 更新後 |
|------|--------|--------|
| `client_name` | `Web Application` | `Updated Web App` |
| `scopes` | `["openid", "profile", "email", "api:read"]` | `["openid", "profile", "email", "api:read", "api:write"]` |
| `tos_uri` | `null` | `https://example.com/terms` |
| `policy_uri` | `null` | `https://example.com/privacy` |

> サードパーティ連携では、同意画面でユーザーに「どのアプリが」「どの権限を」「どの規約のもとで」要求しているかを正確に表示する必要があります。
> クライアント設定の変更が ViewData に即座に反映されることを確認できます。

### 5. 元に戻す

```bash
restore_web_client
```

---

## Experiment 10: 規約更新後に prompt=none で再同意を要求されることを確認する

> **やりたいこと**: 同意済みのクライアントがスコープや規約を変更した場合、prompt=none で再同意が必要になることを確認したい
>
> **変わる設定**: クライアント設定（`scope`）
>
> **サードパーティ連携での重要性**: ユーザーが一度同意した後でも、アプリがアクセス権限を拡大した場合は
> 再度ユーザーの同意が必要。prompt=none はユーザー操作なしの認可を試みるため、
> 未同意のスコープがあれば `interaction_required` や `consent_required` エラーが返る。

### 1. ログインして同意を完了（scope=openid+api:read）

```bash
TEST_EMAIL="consent-$(date +%s)@example.com"
start_auth_flow "openid+api:read"
register_user "${TEST_EMAIL}" "TestPass123" "Consent User"
complete_auth_flow

echo "--- 同意済みスコープで prompt=none → 成功するはず ---"
try_prompt_none "同意済み（openid+api:read）"
```

### パターンA: スコープを拡大して prompt=none

### 2a. クライアントのスコープを拡大

```bash
update_web_client '.scope = "openid profile email api:read api:write"' \
  | jq '{client_id, scope} // .'
```

### 3a. 新しいスコープで prompt=none → エラー

```bash
echo "--- 未同意のスコープ（api:write）を含めて prompt=none ---"

redirect_url=$(curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+api:read+api:write&state=consent-scope-$(date +%s)&prompt=none")

code=$(echo "${redirect_url}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
error=$(echo "${redirect_url}" | sed -n 's/.*[?&]error=\([^&#]*\).*/\1/p')

echo "--- 結果 ---"
if [ -n "${code}" ]; then
  echo "  Result: code issued（想定外：再同意なしで通った）"
elif [ -n "${error}" ]; then
  echo "  Result: ${error}（期待通り：再同意が必要）"
else
  echo "  Result: redirect to login"
  echo "  Redirect: ${redirect_url}"
fi

# 元に戻す
restore_web_client
```

### パターンB: 規約URL を更新して prompt=none

### 2b. 利用規約URL を変更

```bash
# 同意済みの状態から規約URL だけ変更
update_web_client '
  .tos_uri = "https://example.com/terms/v3" |
  .policy_uri = "https://example.com/privacy/v2"
' | jq '{client_id, tos_uri, policy_uri} // .'
```

### 3b. 同じスコープで prompt=none → 規約変更によりエラー

```bash
echo "--- 規約URL 変更後に prompt=none（同じスコープ） ---"

redirect_url=$(curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+api:read&state=consent-tos-$(date +%s)&prompt=none")

code=$(echo "${redirect_url}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
error=$(echo "${redirect_url}" | sed -n 's/.*[?&]error=\([^&#]*\).*/\1/p')

echo "--- 結果 ---"
if [ -n "${code}" ]; then
  echo "  Result: code issued（規約変更では再同意不要だった）"
elif [ -n "${error}" ]; then
  echo "  Result: ${error}（規約変更により再同意が必要）"
else
  echo "  Result: redirect to login"
  echo "  Redirect: ${redirect_url}"
fi
```

### 4. 期待結果

| パターン | 変更内容 | prompt=none の結果 | 理由 |
|---------|---------|-------------------|------|
| ベースライン | 変更なし | 成功（code 発行） | 既に同意済み |
| A: スコープ拡大 | `api:write` 追加 | `interaction_required` | `api:write` は未同意 |
| B: 規約URL 変更 | `tos_uri`, `policy_uri` 更新 | `interaction_required` | 規約変更により既存の同意が無効化される |

> idp-server はスコープの変更だけでなく、`tos_uri` / `policy_uri` の変更も同意無効化の対象としています。
> サードパーティアプリが利用規約やプライバシーポリシーを改定した場合、
> 既存ユーザーに対して改めて同意画面を表示し、新しい規約への同意を取得する必要があります。

### 5. 元に戻す

```bash
restore_web_client
```

---

## 実験一覧

| # | やりたいこと | 変わる設定 | 確認できること |
|---|------------|-----------|--------------|
| 1 | AT 有効期限を短くしたい | `access_token_duration` | 期限切れで Introspection が `active: false` → RT で復活 |
| 2 | RT ローテーションを体験したい | なし（デフォルト有効） | 古い RT が無効化される |
| 3 | RT ローテーションを無効にしたい | `rotate_refresh_token` | 同じ RT を繰り返し使える |
| 4 | RT 戦略を SLIDING にしたい | `refresh_token_strategy` | リフレッシュで期限延長 |
| 5 | M2M スコープを制限したい | なし（リクエスト側） | 要求スコープが Introspection に反映 |
| 6 | AT を JWT にしたい | `access_token_type` | JWT でローカル検証可能、opaque との違い |
| 7 | トークンを無効化したい | なし（Revocation） | Revoke 後に `active: false` |
| 8 | カスタムスコープと UserInfo | なし（リクエスト側） | `api:read` は UserInfo に影響しない |
| 9 | 規約・スコープ変更を同意画面に反映したい | クライアント設定 | ViewData にクライアント名・スコープ・規約URLが反映される |
| 10 | 規約更新後に再同意を要求したい | クライアント設定（スコープ拡大） | prompt=none で未同意スコープがエラーになる |
