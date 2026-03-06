# クライアント設定 実験ガイド

クライアント（`client`）の設定を1つ変えて → 挙動がどう変わるかを手元で確認するガイドです。
EXPERIMENTS-basics.md（テナント設定）、EXPERIMENTS-authorization-server.md（認可サーバー設定）の続編として、
**個別クライアント単位**での動作制御に焦点を当てています。

> **前提**: `setup.sh` が正常に完了していること。

> **認可サーバー設定との違い**: 認可サーバー設定はテナント内の全クライアントに適用されるが、
> クライアント設定は個別クライアント単位で動作を制御できる。
> トークン有効期限やリフレッシュトークン戦略など、クライアント設定でオーバーライドできる項目もある。

---

## 共通準備

```bash
cd config/templates/use-cases/login-password-only
source helpers.sh
get_admin_token
```

### helpers.sh で使える関数（クライアント関連）

| 関数 | 用途 | 使用例 |
|------|------|--------|
| `update_client` | クライアント設定を部分変更 | `update_client '.scope = "openid profile"'` |
| `restore_client` | クライアント設定を元に戻す | `restore_client` |

> **重要**: クライアント更新 API（PUT）も**フル置換**です。
> `update_client` を使えば、ベースの JSON から変えたいフィールドだけ上書きして送れます。

---

## Experiment 1: スコープを制限する

> **やりたいこと**: クライアントが利用できるスコープを制限したい
>
> **変わる設定**: `scope`
>
> **実装の仕組み**: `ClientConfiguration.filteredScope()` が認可リクエストのスコープを
> クライアントの登録済み `scope` でフィルタリングする。
> クライアントに登録されていないスコープはサイレントに除外される。
>
> **認可サーバーの `scopes_supported` との違い**:
> EXPERIMENTS-authorization-server.md Exp 4 で確認した通り、`scopes_supported` は Discovery 表示専用。
> **実際のスコープフィルタリングはクライアント設定の `scope` で行われる。**

### 1. ベースライン確認

```bash
start_auth_flow "openid+profile+email"
register_user "scope-base-$(date +%s)@example.com" "TestPass123" "Scope User"
complete_auth_flow

echo "--- ベースライン ---"
echo "Token scope: $(echo ${TOKEN_RESPONSE} | jq -r '.scope')"
echo ""
echo "--- UserInfo ---"
get_userinfo | jq '{sub, name, email}'
```

> `scope` に `email` が含まれ、UserInfo に `email` が返るはずです。

### 2. 設定変更：email スコープを除外

```bash
update_client '.scope = "openid profile"' \
  | jq '{scope: .result.scope, diff: .diff}'
```

### 3. 挙動確認

```bash
# email をリクエストしてもクライアント設定でフィルタされる
start_auth_flow "openid+profile+email"
register_user "scope-test-$(date +%s)@example.com" "TestPass123" "Scope User 2"
complete_auth_flow

echo "--- email を外した後 ---"
echo "Token scope: $(echo ${TOKEN_RESPONSE} | jq -r '.scope')"
echo ""
echo "--- UserInfo ---"
get_userinfo | jq '{sub, name, email}'
```

### 4. 期待結果

| 条件 | Token scope | UserInfo `email` |
|------|------------|-----------------|
| `scope = "openid profile email"` | `openid profile email` | あり |
| `scope = "openid profile"` | `openid profile` | `null`（消える） |

> **ポイント**: 認可リクエストで `email` をリクエストしても、クライアントの `scope` に含まれていなければ除外される。
> これにより、マルチクライアント環境で「このクライアントには email を渡さない」といった制御が可能。

### 5. 元に戻す

```bash
restore_client
```

---

## Experiment 2: grant_types を制限する

> **やりたいこと**: リフレッシュトークンの使用を禁止したい
>
> **変わる設定**: `grant_types`
>
> **実装の仕組み**: `grant_types` はトークンエンドポイントで**許可される grant type** を制御する。
> `refresh_token` を外しても、認可コードフローのトークンレスポンスには RT が含まれる（発行はされる）。
> ただし、その RT を使って `grant_type=refresh_token` でトークンを再取得しようとするとエラーになる。

### 1. ベースライン確認：RT でのトークン再取得が成功する

```bash
start_auth_flow
register_user "gt-base-$(date +%s)@example.com" "TestPass123" "GT User"
complete_auth_flow

echo "--- ベースライン: RT あり ---"
echo "${TOKEN_RESPONSE}" | jq '{scope, has_refresh_token: (.refresh_token != null)}'

echo ""
echo "--- RT でトークン再取得（成功するはず） ---"
curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" | jq '{token_type, expires_in, error}'
```

### 2. 設定変更：grant_types から refresh_token を外す

```bash
update_client '.grant_types = ["authorization_code"]' \
  | jq '{grant_types: .result.grant_types, diff: .diff}'
```

### 3. 挙動確認：RT は発行されるが、使用するとエラー

```bash
start_auth_flow
register_user "gt-test-$(date +%s)@example.com" "TestPass123" "GT User 2"
complete_auth_flow

echo "--- grant_types から refresh_token を外した後 ---"
echo "RT は発行される: $(echo ${TOKEN_RESPONSE} | jq -r '.refresh_token // "null"' | head -c 30)..."

echo ""
echo "--- RT でトークン再取得（エラーになるはず） ---"
curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=$(echo ${TOKEN_RESPONSE} | jq -r '.refresh_token')" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" | jq '{error, error_description}'
```

### 4. 期待結果

| 条件 | RT 発行 | `grant_type=refresh_token` |
|------|---------|---------------------------|
| `grant_types` に `refresh_token` あり | される | 成功（新しい AT 取得） |
| `grant_types` に `refresh_token` なし | される | **エラー**（`unauthorized_client` 等） |

> **ポイント**: `grant_types` は「トークンエンドポイントで使える grant type」を制御する。
> RT の**発行**を止めるのではなく、RT の**使用**を禁止する設定。
>
> **既知の課題（[#1355](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1355)）**:
> `grant_types` に `refresh_token` がないクライアントでも RT がレスポンスに含まれる。
> 使えない RT を発行するのは混乱を招くため、将来的にはレスポンスから除外する予定。
>
> **ユースケース**: SPA 等で RT のリフレッシュフローを禁止し、
> AT 期限切れ時に再認証を要求したい場合に有効。

### 5. 元に戻す

```bash
restore_client
```

---

## Experiment 3: redirect_uri の検証

> **やりたいこと**: 登録されていない redirect_uri での認可リクエストが拒否されることを確認したい
>
> **変わる設定**: `redirect_uris`
>
> **実装の仕組み**: 認可エンドポイントは、リクエストの `redirect_uri` がクライアントの
> `redirect_uris` に完全一致するかを検証する。不一致の場合、RFC 6749 に従い
> ユーザーエージェントをリダイレクトせずにエラーを返す。

### 1. ベースライン確認

```bash
start_auth_flow
echo "--- ベースライン: 認可リクエスト成功 ---"
echo "Authorization ID: ${AUTHORIZATION_ID}"
echo "Redirect URI: ${REDIRECT_URI}"
```

### 2. 設定変更：redirect_uris を別の URI に

```bash
update_client '.redirect_uris = ["https://different.example.com/callback"]' \
  | jq '{redirect_uris: .result.redirect_uris, diff: .diff}'
```

### 3. 挙動確認

```bash
ENCODED_REDIRECT=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")

echo "--- 登録外の redirect_uri で認可リクエスト ---"
REDIRECT_URL=$(curl -s -o /dev/null -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${ENCODED_REDIRECT}&scope=openid+profile+email&state=test-$(date +%s)")

echo "Redirect URL: ${REDIRECT_URL}"

# リダイレクト先にエラー情報が含まれているか確認
ERROR=$(echo "${REDIRECT_URL}" | sed -n 's/.*[?&]error=\([^&#]*\).*/\1/p')
ERROR_DESC=$(echo "${REDIRECT_URL}" | sed -n 's/.*[?&]error_description=\([^&#]*\).*/\1/p')
echo "Error: ${ERROR}"
echo "Error Description: $(python3 -c "import urllib.parse; print(urllib.parse.unquote('${ERROR_DESC}'))" 2>/dev/null)"
```

### 4. 期待結果

| 条件 | 結果 |
|------|------|
| `redirect_uri` が登録済み | 認可フロー開始（認証画面にリダイレクト） |
| `redirect_uri` が未登録 | エラー（`invalid_request: redirect_uri does not register in client configuration`） |

> **RFC 6749 Section 3.1.2.4**: redirect_uri が不正な場合、認可サーバーは
> リソースオーナーにエラーを通知し、不正な redirect_uri へのリダイレクトは行わない。

### 5. 元に戻す

```bash
restore_client
```

---

## Experiment 4: クライアント認証方式を変える

> **やりたいこと**: トークンエンドポイントでのクライアント認証方式を変更したい
>
> **変わる設定**: `token_endpoint_auth_method`（クライアント）、`token_endpoint_auth_methods_supported`（認可サーバー）
>
> **実装の仕組み**: クライアント認証は**2層で制御**される。
>
> 1. **認可サーバー**: `token_endpoint_auth_methods_supported` でサポートする方式を宣言
> 2. **クライアント**: `token_endpoint_auth_method` で使用する方式を指定
>
> クライアントが指定した方式を認可サーバーがサポートしていない場合、エラーになる。
>
> | 方式 | 認証情報の送り方 |
> |------|----------------|
> | `client_secret_post` | POST ボディに `client_id` + `client_secret` |
> | `client_secret_basic` | `Authorization: Basic base64(client_id:client_secret)` ヘッダー |
> | `none` | クライアント認証なし（パブリッククライアント） |

### 1. エラーパターン：サーバーが未対応の認証方式を設定

デフォルトの認可サーバーは `client_secret_post` と `client_secret_basic` のみサポート。
クライアントを `none` に変更すると、サーバーが未対応のためエラーになる。

```bash
echo "--- 認可サーバーのサポート方式を確認 ---"
curl -s "${TENANT_BASE}/.well-known/openid-configuration" \
  | jq '.token_endpoint_auth_methods_supported'

# クライアントを none に変更
update_client '.token_endpoint_auth_method = "none"' \
  | jq '{token_endpoint_auth_method: .result.token_endpoint_auth_method}'

start_auth_flow
register_user "auth-none1-$(date +%s)@example.com" "TestPass123" "Auth User"

AUTHORIZE_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
```

```shell
echo "--- none で認証（サーバーが未対応 → エラー） ---"
curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" | jq '{error, error_description}'
```

### 2. 成功パターン：認可サーバーの設定も変更

認可サーバーの `token_endpoint_auth_methods_supported` に `none` を追加すれば成功する。

```bash
# 認可サーバーに none を追加
update_auth_server '.token_endpoint_auth_methods_supported = ["client_secret_post", "client_secret_basic", "none"]' \
  | jq '.result.token_endpoint_auth_methods_supported'

start_auth_flow
register_user "auth-none2-$(date +%s)@example.com" "TestPass123" "Auth User 2"

AUTHORIZE_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
```

```shell
echo "--- none で認証（サーバーも対応済み → 成功） ---"
curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" | jq '{token_type, expires_in, error}'
```

### 3. 期待結果

| 認可サーバー | クライアント | 結果 |
|------------|------------|------|
| `["client_secret_post", "client_secret_basic"]` | `none` | **エラー**（`server does not supported client authentication type (none)`） |
| `["client_secret_post", "client_secret_basic", "none"]` | `none` | **成功**（認証なしでトークン取得） |

> **ポイント**: クライアント認証方式は認可サーバーとクライアントの**両方で整合**が必要。
> クライアント設定だけ変えてもサーバーが未対応ならエラーになる。
>
> **`none` のユースケース**: SPA やネイティブアプリなど、client_secret を安全に保持できない
> パブリッククライアント向け。PKCE との併用が推奨される。

### 4. 元に戻す

```bash
restore_client
restore_auth_server
```

---

## Experiment 5: クライアント別トークン有効期限

> **やりたいこと**: 特定のクライアントだけトークン有効期限を変えたい（認可サーバー設定をオーバーライド）
>
> **変わる設定**: `extension.access_token_duration` / `extension.id_token_duration`
>
> **実装の仕組み**: トークン生成時、クライアントの `extension` にトークン有効期限が設定されていれば、
> 認可サーバーの設定よりも優先される。
> これにより、同じテナント内でもクライアントごとに異なる有効期限を設定できる。

### 1. ベースライン確認（認可サーバーのデフォルト）

```bash
echo "--- 認可サーバーのデフォルト設定 ---"
echo "${AUTH_SERVER_JSON}" | jq '{
  access_token_duration: .extension.access_token_duration,
  id_token_duration: .extension.id_token_duration
}'

start_auth_flow
register_user "dur-base-$(date +%s)@example.com" "TestPass123" "Duration User"
complete_auth_flow

echo "--- ベースライン ---"
echo "AT expires_in: $(echo ${TOKEN_RESPONSE} | jq '.expires_in')"
decode_jwt_payload "$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')" \
  | jq '{exp_minus_iat: (.exp - .iat)}'
```

### 2. 設定変更：クライアント独自の有効期限

```bash
update_client '.extension.access_token_duration = 15 | .extension.id_token_duration = 20' \
  | jq '{
    access_token_duration: .result.extension.access_token_duration,
    id_token_duration: .result.extension.id_token_duration,
    diff: .diff
  }'
```

### 3. 挙動確認

```bash
start_auth_flow
register_user "dur-test-$(date +%s)@example.com" "TestPass123" "Duration User 2"
complete_auth_flow

echo "--- クライアント設定で上書き後 ---"
echo "AT expires_in: $(echo ${TOKEN_RESPONSE} | jq '.expires_in')"
decode_jwt_payload "$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')" \
  | jq '{exp_minus_iat: (.exp - .iat)}'
```

### 4. 期待結果

| 設定元 | AT `expires_in` | IDT `exp - iat` |
|--------|----------------|-----------------|
| 認可サーバー（デフォルト） | `3600` | `3600` |
| クライアント `extension` | **`15`** | **`20`** |

> **ユースケース**: 管理画面クライアントは短い AT（セキュリティ重視）、
> モバイルアプリクライアントは長い AT（利便性重視）など、用途に応じた設定ができる。

### 5. 元に戻す

```bash
restore_client
```

---

## Experiment 6: クライアント別リフレッシュトークン戦略

> **やりたいこと**: 特定のクライアントだけリフレッシュトークンの動作を変えたい
>
> **変わる設定**: `extension.refresh_token_strategy` / `extension.rotate_refresh_token`
>
> **実装の仕組み**: 認可サーバー設定と同様に、クライアントの `extension` で
> リフレッシュトークン戦略をオーバーライドできる。
>
> | strategy | rotate | トークン値 | 有効期限 |
> |----------|--------|-----------|---------|
> | EXTENDS | true | **新しい** | **延長**（now + duration） |
> | FIXED | true | **新しい** | 同じ（初回発行時のまま） |
> | FIXED | false | 同じ | 同じ（初回発行時のまま） |

### 1. 認可サーバーの設定を確認

```bash
echo "--- 認可サーバーのデフォルト RT 設定 ---"
echo "${AUTH_SERVER_JSON}" | jq '{
  refresh_token_duration: .extension.refresh_token_duration,
  refresh_token_strategy: .extension.refresh_token_strategy,
  rotate_refresh_token: .extension.rotate_refresh_token
}'
```

### 2. クライアントを FIXED + rotate=false に設定

```bash
update_client '
  .extension.refresh_token_duration = 120 |
  .extension.refresh_token_strategy = "FIXED" |
  .extension.rotate_refresh_token = false
' | jq '.result.extension | {refresh_token_duration, refresh_token_strategy, rotate_refresh_token}'
```

### 3. 挙動確認：トークン値が変わらない

```bash
start_auth_flow
register_user "rt-fixed-$(date +%s)@example.com" "TestPass123" "RT User"
complete_auth_flow

ORIGINAL_RT="${REFRESH_TOKEN}"
echo "--- 初回 RT: ${ORIGINAL_RT:0:30}... ---"

sleep 3

echo "--- リフレッシュ実行 ---"
REFRESH_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

NEW_RT=$(echo "${REFRESH_RESPONSE}" | jq -r '.refresh_token')
echo "リフレッシュ後 RT: ${NEW_RT:0:30}..."
echo "トークン値が変わった: $([ "${ORIGINAL_RT}" != "${NEW_RT}" ] && echo 'YES' || echo 'NO')"
```

### 4. クライアントを EXTENDS + rotate=true に変更

```bash
update_client '
  .extension.refresh_token_duration = 120 |
  .extension.refresh_token_strategy = "EXTENDS" |
  .extension.rotate_refresh_token = true
' | jq '.result.extension | {refresh_token_duration, refresh_token_strategy, rotate_refresh_token}'

start_auth_flow
register_user "rt-extends-$(date +%s)@example.com" "TestPass123" "RT User 2"
complete_auth_flow

ORIGINAL_RT="${REFRESH_TOKEN}"
echo "--- 初回 RT: ${ORIGINAL_RT:0:30}... ---"

sleep 3

echo "--- リフレッシュ実行 ---"
REFRESH_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

NEW_RT=$(echo "${REFRESH_RESPONSE}" | jq -r '.refresh_token')
echo "リフレッシュ後 RT: ${NEW_RT:0:30}..."
echo "トークン値が変わった: $([ "${ORIGINAL_RT}" != "${NEW_RT}" ] && echo 'YES' || echo 'NO')"
```

### 5. 期待結果

| 設定 | strategy | rotate | トークン値 | 有効期限延長 |
|------|----------|--------|-----------|------------|
| パターン1 | FIXED | false | 変わらない | しない |
| パターン2 | EXTENDS | true | **変わる** | **する** |

> **認可サーバー設定との優先順位**:
> - クライアントの `extension` に `refresh_token_strategy` / `rotate_refresh_token` が設定されていれば、それが優先
> - 未設定の場合は認可サーバーのデフォルトが適用される
>
> **EXPERIMENTS-authorization-server.md Exp 7 との違い**:
> そちらは認可サーバー全体の設定変更（全クライアントに影響）。
> こちらは特定クライアントだけの設定変更。

### 6. 元に戻す

```bash
restore_client
```

---

## Experiment 7: tos_uri / policy_uri で再同意を要求する

> **やりたいこと**: 利用規約やプライバシーポリシーの URI を変更して、ユーザーに再同意を求めたい
>
> **変わる設定**: `tos_uri`, `policy_uri`
>
> **実装の仕組み**: `OAuthRequestContext.createConsentClaims()` がクライアントの `tos_uri` / `policy_uri` から
> `ConsentClaims` を生成する。既存の認可グラントに保存された ConsentClaims と比較し、
> 値が変わっていると `interaction_required` エラーを返す。
>
> ```
> ConsentClaim.equals(): name と value の両方を比較
> → URI の値が変わった → equals() が false → 再同意が必要
> ```
>
> **注意**: `prompt=none` を使うとユーザー操作なしで確認できる。
> ConsentClaims が一致しない → `interaction_required`。

### 1. テストユーザーを作成して認可グラントを確立

```bash
TOS_EMAIL="tos-$(date +%s)@example.com"
start_auth_flow
register_user "${TOS_EMAIL}" "TestPass123" "TOS User"
complete_auth_flow > /dev/null
echo "テストユーザー: ${TOS_EMAIL}"
```

### 2. ベースライン：prompt=none が成功することを確認

```bash
try_prompt_none "tos_uri 設定前"
```

> 既存のセッション + 認可グラントがあるため、`prompt=none` で code が発行されるはずです。

### 3. 設定変更：tos_uri を追加

```bash
update_client '.tos_uri = "https://example.com/terms-v2"' \
  | jq '{tos_uri: .result.tos_uri, diff: .diff}'
```

### 4. 挙動確認：prompt=none → interaction_required

```bash
try_prompt_none "tos_uri 設定後"
```

### 5. さらに policy_uri も追加

```bash
update_client '.tos_uri = "https://example.com/terms-v2" | .policy_uri = "https://example.com/privacy-v2"' \
  | jq '{tos_uri: .result.tos_uri, policy_uri: .result.policy_uri}'
```

```bash
try_prompt_none "tos_uri + policy_uri 設定後"
```

### 6. 期待結果

| 条件 | `prompt=none` の結果 | 理由 |
|------|---------------------|------|
| tos_uri / policy_uri なし | code 発行 | ConsentClaims が一致（両方とも空） |
| tos_uri を追加 | `interaction_required` | 既存グラントに tos_uri の同意がない |
| tos_uri + policy_uri を追加 | `interaction_required` | 同上（両方の同意が必要） |

> **実運用での活用**:
> - 利用規約を改定した場合、`tos_uri` の値を変更するだけで全ユーザーに再同意を要求できる
> - URL のバージョニング（`/terms-v2`）で管理すると、いつの時点の規約に同意したかを追跡できる
> - `policy_uri` も同様にプライバシーポリシー改定時に使える

### 7. 元に戻す

```bash
restore_client
```

---

## Experiment 8: response_types の制限

> **やりたいこと**: クライアントが使える response_type を制限したい
>
> **変わる設定**: `response_types`
>
> **実装の仕組み**: 認可エンドポイントは、リクエストの `response_type` がクライアントの
> `response_types` に含まれているかを検証する。
> 未登録の response_type でリクエストすると、エラーが返される。

### 1. ベースライン確認

```bash
start_auth_flow
echo "--- ベースライン: response_type=code 成功 ---"
echo "Authorization ID: ${AUTHORIZATION_ID}"
```

### 2. 設定変更：response_types を変更

```bash
update_client '.response_types = ["token"]' \
  | jq '{response_types: .result.response_types, diff: .diff}'
```

### 3. 挙動確認：response_type=code → エラー

```bash
ENCODED_REDIRECT=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")

echo "--- response_type=code（未登録） ---"
REDIRECT_URL=$(curl -s -o /dev/null -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${ENCODED_REDIRECT}&scope=openid+profile+email&state=test-$(date +%s)")

echo "Redirect URL: ${REDIRECT_URL}"
```

```bash
ERROR=$(echo "${REDIRECT_URL}" | sed -n 's/.*[?&]error=\([^&#]*\).*/\1/p')
ERROR_DESC=$(echo "${REDIRECT_URL}" | sed -n 's/.*[?&]error_description=\([^&#]*\).*/\1/p')
echo "Error: ${ERROR}"
echo "Error Description: $(python3 -c "import urllib.parse; print(urllib.parse.unquote('${ERROR_DESC}'))" 2>/dev/null)"
```

### 4. 期待結果

| response_type | `response_types` 登録状況 | 結果 |
|--------------|-------------------------|------|
| `code` | `["code"]`（デフォルト） | 成功 |
| `code` | `["token"]`（code 未登録） | エラー（`unauthorized_client: client is unauthorized response_type (code)`） |

> **補足**: `response_types` と `grant_types` は関連している。
> `response_type=code` には `grant_type=authorization_code`、
> `response_type=token` には `grant_type=implicit` が対応する。

### 5. 元に戻す

```bash
restore_client
```

---

## 実験一覧

| # | やりたいこと | 変わる設定 | 確認できること |
|---|------------|-----------|--------------|
| 1 | スコープを制限したい | `scope` | email を外す → UserInfo から消える |
| 2 | RT 使用を禁止したい | `grant_types` | refresh_token を外す → RT 使用時エラー |
| 3 | redirect_uri を検証したい | `redirect_uris` | 登録外 URI → エラー |
| 4 | 認証方式を変えたい | `token_endpoint_auth_method` | サーバー未対応 → エラー、対応追加 → 成功 |
| 5 | トークン有効期限を個別設定したい | `extension.*_duration` | 認可サーバー設定をオーバーライド |
| 6 | RT 戦略を個別設定したい | `extension.refresh_token_strategy` | FIXED/EXTENDS をクライアント単位で設定 |
| 7 | 規約改定で再同意を求めたい | `tos_uri` / `policy_uri` | ConsentClaims 変更 → `interaction_required` |
| 8 | response_type を制限したい | `response_types` | 未登録の response_type → エラー |

### 3つの実験ガイドの全体像

| ガイド | 設定レベル | 影響範囲 |
|--------|----------|---------|
| EXPERIMENTS-basics.md | テナント設定 | テナント全体（パスワードポリシー、セッション等） |
| EXPERIMENTS-authorization-server.md | 認可サーバー設定 | テナント内の全クライアント（トークン設定、PKCE等） |
| **EXPERIMENTS-client.md**（本ガイド） | クライアント設定 | **個別クライアント**（スコープ、認証方式、オーバーライド等） |
