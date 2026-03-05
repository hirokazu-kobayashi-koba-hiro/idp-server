# 認可サーバー設定 実験ガイド

認可サーバー（`authorization_server`）の設定を1つ変えて → 挙動がどう変わるかを手元で確認するガイドです。
EXPERIMENTS-basics.md の続編として、認可サーバー固有の設定に焦点を当てています。

> **前提**: `setup.sh` が正常に完了していること。
> EXPERIMENTS-basics.md の Experiment 3（claims_supported）と 4（access_token_duration）は既存のガイドを参照。

---

## 共通準備

```bash
cd config/templates/use-cases/login-password-only
source helpers.sh
get_admin_token
```

---

## Experiment 1: ID Token の有効期限を短くする

> **やりたいこと**: ID Token の有効期限を短くしたい → `exp` クレームの変化を確認
>
> **変わる設定**: `authorization_server.extension.id_token_duration`
>
> **実装の仕組み**: `IdTokenCreator` が `now + id_token_duration` で `exp` を計算。
> クライアント設定にオーバーライドがあればそちらが優先される。

### 1. ベースライン確認

```bash
start_auth_flow
register_user "idt-$(date +%s)@example.com" "TestPass123" "IDT User"
complete_auth_flow

echo "--- ベースライン: ID Token の exp ---"
decode_jwt_payload "$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')" | jq '{exp, iat, exp_minus_iat: (.exp - .iat)}'
```

> `exp - iat` がデフォルト `3600`（1時間）になるはずです。

### 2. 設定変更：ID Token を 30秒に

```bash
update_auth_server '.extension.id_token_duration = 30' \
  | jq '.result.extension.id_token_duration // .'
```

### 3. 挙動確認

```bash
start_auth_flow
register_user "idt2-$(date +%s)@example.com" "TestPass123" "IDT User 2"
complete_auth_flow

echo "--- 変更後: ID Token の exp ---"
decode_jwt_payload "$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')" | jq '{exp, iat, exp_minus_iat: (.exp - .iat)}'
```

### 4. 期待結果

| タイミング | `exp - iat` | 意味 |
|-----------|-------------|------|
| 変更前 | `3600` | デフォルト 1時間 |
| 変更後 | `30` | 30秒に短縮 |

> **補足**: ID Token はフロントエンドで検証されるため、短すぎるとクライアント側で即座に期限切れ扱いになる。
> Access Token とは独立した有効期限なので、AT は有効でも IDT は期限切れになりうる。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 2: 認可コードの有効期限を短くして期限切れを体験する

> **やりたいこと**: 認可コードの有効期限を極端に短くして、コード交換が失敗する様子を見たい
>
> **変わる設定**: `authorization_server.extension.authorization_code_valid_duration`
>
> **実装の仕組み**: `OAuthAuthorizeContext` がコード生成時に `now + authorizationCodeValidDuration` で有効期限を設定。
> トークンエンドポイントでコードの期限切れをチェックする。

### 1. 設定変更：認可コードを 3秒に

```bash
update_auth_server '.extension.authorization_code_valid_duration = 3' \
  | jq '.result.extension.authorization_code_valid_duration // .'
```

### 2. 挙動確認：即座にコード交換 → 成功

```bash
start_auth_flow
register_user "code1-$(date +%s)@example.com" "TestPass123" "Code User"

# 認可してコードを取得
AUTHORIZE_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
echo "Code: ${AUTHORIZATION_CODE}"

echo "--- 即座にトークン交換（成功するはず） ---"
curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" | jq '{token_type, expires_in, error, error_description}'
```

### 3. 挙動確認：5秒待ってからコード交換 → 失敗

```bash
start_auth_flow
register_user "code2-$(date +%s)@example.com" "TestPass123" "Code User 2"

AUTHORIZE_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')

echo "--- 5秒待機... ---"
sleep 5

echo "--- 期限切れ後にトークン交換（失敗するはず） ---"
curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" | jq '{error, error_description}'
```

### 4. 期待結果

| タイミング | 結果 | 理由 |
|-----------|------|------|
| 即座に交換 | 200 OK（トークン取得成功） | コード有効期間内（3秒以内） |
| 5秒後に交換 | 400 エラー（`invalid_grant` 等） | コード期限切れ |

> **RFC 6749 推奨**: 認可コードの有効期限は最大10分。デフォルトは 600秒（10分）。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 3: default_max_age で再認証を強制する

> **やりたいこと**: 認証の鮮度要件を設定して、一定時間後に再認証を要求したい
>
> **変わる設定**: `authorization_server.extension.default_max_age`
>
> **実装の仕組み**: クライアントの認可リクエストに `max_age` が含まれない場合、
> サーバーの `default_max_age` が適用される。セッションの `auth_time` からの経過秒数が
> `max_age` を超えると、再認証が必要になる。
>
> **注意**: `prompt=none` を使うとユーザー操作なしで確認できる。
> セッション有効＋認証が新しい → code 発行、認証が古い → `login_required`。

### 1. まずテストユーザーを作成

```bash
MAX_AGE_EMAIL="maxage-$(date +%s)@example.com"
start_auth_flow
register_user "${MAX_AGE_EMAIL}" "TestPass123" "MaxAge User"
complete_auth_flow > /dev/null
echo "テストユーザー: ${MAX_AGE_EMAIL}"
```

### 2. 設定変更：default_max_age を 10秒に

```bash
update_auth_server '.extension.default_max_age = 10' \
  | jq '.result.extension.default_max_age // .'
```

### 3. 挙動確認

```bash
echo "--- 即座に prompt=none（認証が新しい → 成功するはず） ---"
try_prompt_none "即座"

echo ""
echo "--- 15秒待機... ---"
sleep 15

echo "--- 15秒後に prompt=none（認証が古い → login_required） ---"
try_prompt_none "15秒後"
```

### 4. 期待結果

| タイミング | 結果 | 理由 |
|-----------|------|------|
| 即座に | code 発行 | `auth_time` からの経過 < `default_max_age`（10秒） |
| 15秒後 | `login_required` | `auth_time` からの経過 > 10秒 → 再認証が必要 |

> **セッション有効期限（Experiment 5 in EXPERIMENTS.md）との違い**:
> - `session_config.timeout_seconds`: セッション自体の寿命（ブラウザの cookie が消える）
> - `default_max_age`: セッションはあるが「認証が古すぎる」場合に再認証を要求
>
> **クライアントが `max_age=0` を送ると**: 常に再認証を要求（`prompt=login` と似た効果）

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 4: scopes_supported は Discovery 表示専用であることを確認する

> **やりたいこと**: `scopes_supported` を変更して、実際のスコープ処理に影響するか確認したい
>
> **変わる設定**: `authorization_server.scopes_supported`
>
> **実装の仕組み**: `scopes_supported` は **Discovery（`.well-known/openid-configuration`）の表示専用**。
> 実際のスコープフィルタリングは**クライアント設定の `scope`** で行われる（`ClientConfiguration.filteredScope()`）。
> `AuthorizationServerConfiguration.filteredScope()` メソッドは存在するが、認可リクエスト処理フローから呼ばれていない可能性がある（#1353 で調査中）。
>
> つまり、サーバー側で `scopes_supported` を変えても、クライアントが許可されたスコープを
> リクエストする限り、実際の動作には影響しない。

### 1. 設定変更：email スコープを除外

```bash
update_auth_server '.scopes_supported = ["openid", "profile"]' \
  | jq '.result.scopes_supported // .'
```

### 2. Discovery の変化を確認

```bash
echo "--- Discovery の scopes_supported ---"
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq '.scopes_supported'
```

### 3. 挙動確認：email を含むリクエストを送る

```bash
# email スコープをリクエスト（クライアントの scope に含まれているため通る）
start_auth_flow "openid+profile+email"
register_user "scope-filter-$(date +%s)@example.com" "TestPass123" "Scope User"
complete_auth_flow

echo "--- scopes_supported から email を除外しても ---"
echo "Token scope: $(echo ${TOKEN_RESPONSE} | jq -r '.scope')"
echo ""
echo "--- UserInfo にはまだ email が返る ---"
get_userinfo | jq '{sub, name, email}'
```

### 4. 期待結果

| 変更内容 | Discovery | 実際の scope | UserInfo |
|---------|-----------|-------------|----------|
| `scopes_supported` から email 除外 | `["openid", "profile"]` | `openid profile email`（変わらない） | `sub`, `name`, `email`（変わらない） |

> **ポイント**: `scopes_supported` は Discovery のみ。スコープの実際のフィルタリングは
> クライアント設定の `scope` フィールドで制御される。
>
> **スコープを実際に制限するには**: クライアント設定の `scope` を変更する必要がある。
> ```bash
> # 例: クライアントの scope から email を外す（クライアント更新 API）
> # PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/clients/{client-id}
> # { "scope": "openid profile" }
> ```

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 5: 認可リクエストの有効期限を短くする

> **やりたいこと**: 認可リクエスト（認証中のコンテキスト）の有効期限を短くして、
> ログイン画面で放置した場合の挙動を確認したい
>
> **変わる設定**: `authorization_server.extension.oauth_authorization_request_expires_in`
>
> **実装の仕組み**: `OAuthAuthenticationTransactionCreator` が認可リクエスト受信時に
> `now + oauthAuthorizationRequestExpiresIn` で有効期限を設定。
> 認証操作（パスワード入力等）時にこの期限を超えていると、リクエストが無効になる。
>
> **違い**:
> - `authorization_code_valid_duration`: コード発行**後**の有効期限（Experiment 2）
> - `oauth_authorization_request_expires_in`: コード発行**前**（認証中）の有効期限（本実験）

### 1. 設定変更：認可リクエストを 5秒に

```bash
update_auth_server '.extension.oauth_authorization_request_expires_in = 5' \
  | jq '.result.extension.oauth_authorization_request_expires_in // .'
```

### 2. 挙動確認：即座に認証 → 成功

```bash
start_auth_flow
echo "--- 即座に認証（成功するはず） ---"
register_user "req1-$(date +%s)@example.com" "TestPass123" "Req User" | jq .
```

### 3. 挙動確認：10秒待ってから認証 → 失敗

```bash
start_auth_flow
echo "--- 10秒待機... ---"
sleep 10

echo "--- 期限切れ後に認証（失敗するはず） ---"
register_user "req2-$(date +%s)@example.com" "TestPass123" "Req User 2" | jq .
```

### 4. 期待結果

| タイミング | 結果 | エラー |
|-----------|------|--------|
| 即座に認証 | 成功 | — |
| 10秒後に認証 | 失敗 | `auth_session_mismatch: Missing AUTH_SESSION cookie` |

> **エラーメッセージについて**: 期限切れ後のエラーが「認可リクエスト期限切れ」ではなく
> `auth_session_mismatch` になるのは、**AUTH_SESSION cookie の TTL が認可リクエストと同じ値で設定される**ため。
>
> ```
> 認可リクエスト作成時（OAuthFlowEntryService.java:187-188）:
>   authSessionCookieDelegate.setAuthSessionCookie(
>       tenant, authSessionId.value(), requestResponse.oauthAuthorizationRequestExpiresIn());
>                                      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
>                                      cookie の maxAge = 認可リクエストの有効期限（同じ値）
> ```
>
> 期限切れ → cookie がブラウザ/curl から消える → `AuthSessionValidator` が cookie なしを検出
> → `auth_session_mismatch` エラー。認可リクエスト自体の期限切れチェック（DB の `expires_at > now()`）
> には到達しない。
>
> **実運用での意味**: ログイン画面を開いたまま放置した場合の挙動を制御する。
> デフォルト 1800秒（30分）は一般的だが、金融系では短く設定する場合がある。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 6: id_token_strict_mode で ID Token のクレームを制限する

> **やりたいこと**: ID Token に含まれるクレームを厳格に制限したい
>
> **変わる設定**: `authorization_server.extension.id_token_strict_mode`
>
> **実装の仕組み**:
> - `false`（デフォルト）: scope に `profile` があれば `name` 等が ID Token に含まれる
> - `true`: `claims` パラメータで明示的に要求されたクレームのみ ID Token に含まれる
>
> **UserInfo への影響**: なし。strict mode は ID Token のみに影響する。

### 1. ベースライン確認（strict mode OFF）

```bash
start_auth_flow "openid+profile+email"
register_user "strict1-$(date +%s)@example.com" "TestPass123" "Strict User"
complete_auth_flow

echo "--- strict_mode=false: ID Token のクレーム ---"
decode_jwt_payload "$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')" | jq '{sub, name, email, given_name, family_name}'

echo ""
echo "--- strict_mode=false: UserInfo ---"
get_userinfo | jq .
```

### 2. 設定変更：strict mode ON

```bash
update_auth_server '.extension.id_token_strict_mode = true' \
  | jq '.result.extension.id_token_strict_mode // .'
```

### 3. 挙動確認：scope のみ（claims パラメータなし）

```bash
start_auth_flow "openid+profile+email"
register_user "strict2-$(date +%s)@example.com" "TestPass123" "Strict User 2"
complete_auth_flow

echo "--- strict_mode=true, claims未指定: ID Token のクレーム ---"
decode_jwt_payload "$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')" | jq '{sub, name, email, given_name, family_name}'

echo ""
echo "--- strict_mode=true: UserInfo（影響なし） ---"
get_userinfo | jq .
```

### 4. 挙動確認：claims パラメータで ID Token のクレームを明示要求

strict mode ON でも、認可リクエストの `claims` パラメータで `essential: true` を指定すれば
ID Token にクレームを含められる。

```bash
# claims パラメータ: email は essential、name は voluntary
CLAIMS_JSON='{"id_token":{"email":{"essential":true},"name":null}}'
CLAIMS_PARAM=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${CLAIMS_JSON}'))")

[ -n "${COOKIE_JAR:-}" ] && [ -f "${COOKIE_JAR}" ] && rm -f "${COOKIE_JAR}"
COOKIE_JAR=$(mktemp)
STATE="exp-state-$(date +%s)"
ENCODED_REDIRECT=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")

AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${ENCODED_REDIRECT}&scope=openid+profile+email&state=${STATE}&claims=${CLAIMS_PARAM}")

AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTHORIZATION_ID}"

register_user "strict-claims1-$(date +%s)@example.com" "TestPass123" "Claims User"
complete_auth_flow

echo "--- strict_mode=true, essential=email: ID Token ---"
decode_jwt_payload "$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')" | jq '{sub, name, email}'
```

```bash
# claims パラメータ: email, name ともに essential
CLAIMS_JSON='{"id_token":{"email":{"essential":true},"name":{"essential":true}}}'
CLAIMS_PARAM=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${CLAIMS_JSON}'))")

[ -n "${COOKIE_JAR:-}" ] && [ -f "${COOKIE_JAR}" ] && rm -f "${COOKIE_JAR}"
COOKIE_JAR=$(mktemp)
STATE="exp-state-$(date +%s)"

AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${ENCODED_REDIRECT}&scope=openid+profile+email&state=${STATE}&claims=${CLAIMS_PARAM}")

AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTHORIZATION_ID}"

register_user "strict-claims2-$(date +%s)@example.com" "TestPass123" "Claims User 2"
complete_auth_flow

echo "--- strict_mode=true, essential=email+name: ID Token ---"
decode_jwt_payload "$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')" | jq '{sub, name, email}'
```

### 5. 期待結果

| 条件 | ID Token `email` | ID Token `name` | UserInfo |
|------|-------------------|------------------|----------|
| strict=false（デフォルト） | あり | あり | あり |
| strict=true, claims 未指定 | `null` | `null` | あり |
| strict=true, `email: essential`, `name: voluntary` | **あり** | `null` | あり |
| strict=true, `email: essential`, `name: essential` | **あり** | **あり** | あり |

> **OIDC仕様との関係**: strict mode は OIDC Core 仕様に厳格な実装。
> ID Token に scope ベースのクレームを含めるかは OIDC Core では OPTIONAL だが、
> `claims` パラメータで `essential: true` を指定したクレームは仕様上含めるべきとされる。
>
> - `essential: true` → strict mode でも ID Token に含まれる
> - voluntary（`null` / `essential` なし）→ strict mode では含まれない
> - フロントエンドで ID Token からクレームを読む場合は strict=`false` が便利
> - セキュリティ重視で「ID Token は認証用途のみ」にしたい場合は strict=`true` + 必要なクレームだけ `essential: true` で要求
>
> **`claims:*` カスタムスコープへの影響**: strict mode が `true` の場合、
> `claims:*` カスタムスコープのクレームは **UserInfo からも除外される**。
> 標準 OIDC クレーム（`profile`, `email` スコープ）には影響しない。

### 6. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 7: リフレッシュトークン戦略を変えて挙動を比較する

> **やりたいこと**: リフレッシュトークンのローテーションと期限延長の挙動を比較したい
>
> **変わる設定**: `extension.refresh_token_strategy` + `extension.rotate_refresh_token`
>
> **実装の仕組み**: `RefreshTokenCreatable` が以下の4パターンで動作する。
>
> | strategy | rotate | トークン値 | 有効期限 |
> |----------|--------|-----------|---------|
> | EXTENDS | true | **新しい** | **延長**（now + duration） |
> | EXTENDS | false | 同じ | **延長**（now + duration） |
> | FIXED | true | **新しい** | 同じ（初回発行時のまま） |
> | FIXED | false | 同じ | 同じ（初回発行時のまま） |

> **注意**: パターンB は sleep 50 + sleep 30 で合計80秒以上の待機が発生します。
> 時間がない場合はパターンA と C だけでも十分に挙動差を確認できます。

### 1. パターンA: FIXED + rotate=true（デフォルト相当）

```bash
# refresh_token_duration を短く（60秒）して観察しやすくする
update_auth_server '
  .extension.refresh_token_duration = 60 |
  .extension.refresh_token_strategy = "FIXED" |
  .extension.rotate_refresh_token = true
' | jq '.result.extension | {refresh_token_duration, refresh_token_strategy, rotate_refresh_token}'

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
echo "新 RT: ${NEW_RT:0:30}..."
echo "トークン値が変わった: $([ "${ORIGINAL_RT}" != "${NEW_RT}" ] && echo 'YES' || echo 'NO')"
```

### 2. パターンB: EXTENDS + rotate=true（スライディングウィンドウ）

```bash
update_auth_server '
  .extension.refresh_token_duration = 60 |
  .extension.refresh_token_strategy = "EXTENDS" |
  .extension.rotate_refresh_token = true
' | jq '.result.extension | {refresh_token_duration, refresh_token_strategy, rotate_refresh_token}'

start_auth_flow
register_user "rt-extends-$(date +%s)@example.com" "TestPass123" "RT User 2"
complete_auth_flow

ORIGINAL_RT="${REFRESH_TOKEN}"
echo "--- 初回 RT: ${ORIGINAL_RT:0:30}... ---"

# 50秒待って、初回の60秒期限に近づいた状態でリフレッシュ
echo "--- 50秒待機（FIXED なら残り10秒しかない）... ---"
sleep 50

echo "--- リフレッシュ実行（EXTENDS なので期限が延長される） ---"
REFRESH_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

NEW_RT=$(echo "${REFRESH_RESPONSE}" | jq -r '.refresh_token')
echo "新 RT: ${NEW_RT:0:30}..."
echo "トークン値が変わった: $([ "${ORIGINAL_RT}" != "${NEW_RT}" ] && echo 'YES' || echo 'NO')"

# さらに30秒待ってリフレッシュ（FIXED だったら期限切れだが EXTENDS なら有効）
echo "--- さらに30秒待機... ---"
sleep 30

echo "--- 2回目のリフレッシュ（EXTENDS なのでまだ有効） ---"
REFRESH_TOKEN="${NEW_RT}"
REFRESH_RESPONSE2=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

echo "${REFRESH_RESPONSE2}" | jq '{token_type, expires_in, error, error_description}'
```

### 3. パターンC: FIXED + rotate=false（トークン再利用）

```bash
update_auth_server '
  .extension.refresh_token_duration = 60 |
  .extension.refresh_token_strategy = "FIXED" |
  .extension.rotate_refresh_token = false
' | jq '.result.extension | {refresh_token_duration, refresh_token_strategy, rotate_refresh_token}'

start_auth_flow
register_user "rt-noro-$(date +%s)@example.com" "TestPass123" "RT User 3"
complete_auth_flow

ORIGINAL_RT="${REFRESH_TOKEN}"
echo "--- 初回 RT: ${ORIGINAL_RT:0:30}... ---"

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

### 4. 期待結果まとめ

| パターン | strategy | rotate | トークン値変化 | 有効期限延長 | ユースケース |
|---------|----------|--------|-------------|------------|------------|
| A | FIXED | true | **変わる** | しない | 一般的（トークン固定攻撃防止） |
| B | EXTENDS | true | **変わる** | **する** | アクティブユーザーに無期限セッション |
| C | FIXED | false | 変わらない | しない | シンプル（トークン再利用） |

> **セキュリティ推奨**:
> - `rotate_refresh_token = true`: トークン漏洩時の被害を限定（古いトークンが無効化される）
> - `FIXED` 戦略: リフレッシュチェーンに上限を設ける（初回発行から一定時間で必ず期限切れ）
> - `EXTENDS` 戦略: ユーザーがアクティブなら無期限に延長（利便性重視だがリスクもある）

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 8: code_challenge_methods_supported と PKCE の関係を確認する

> **やりたいこと**: PKCE（Proof Key for Code Exchange）の動作を確認したい
>
> **変わる設定**: `authorization_server.code_challenge_methods_supported`
>
> **実装の仕組み**:
> - `code_challenge_methods_supported` は **Discovery 表示専用**。この設定で PKCE を強制/無効化することはできない。
> - PKCE 検証は `idp-server-core-extension-pkce` モジュールの `AuthorizationCodeGrantPkceVerifier` が担当。
>   SPI 経由で自動登録され、認可リクエストに `code_challenge` があれば検証が動作する。
> - つまり: `code_challenge` を送れば検証される、送らなければスキップされる。

### 1. Discovery 表示の確認

```bash
echo "--- 変更前: Discovery ---"
curl -s "${TENANT_BASE}/.well-known/openid-configuration" \
  | jq '.code_challenge_methods_supported'
```

### 2. 設定変更：S256 を追加

```bash
update_auth_server '.code_challenge_methods_supported = ["S256"]' \
  | jq '.result.code_challenge_methods_supported // .'
```

```bash
echo "--- 変更後: Discovery ---"
curl -s "${TENANT_BASE}/.well-known/openid-configuration" \
  | jq '.code_challenge_methods_supported'
```

### 3. PKCE ありで認可フロー

#### 3a. code_verifier / code_challenge を生成

```bash
CODE_VERIFIER=$(python3 -c "import secrets, base64; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('='))")
CODE_CHALLENGE=$(echo -n "${CODE_VERIFIER}" | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '=')
echo "code_verifier:  ${CODE_VERIFIER}"
echo "code_challenge: ${CODE_CHALLENGE}"
```

#### 3b. PKCE パラメータ付きで認可リクエスト → 登録 → 認可

```bash
[ -n "${COOKIE_JAR:-}" ] && [ -f "${COOKIE_JAR}" ] && rm -f "${COOKIE_JAR}"
COOKIE_JAR=$(mktemp)
STATE="pkce-$(date +%s)"
ENCODED_REDIRECT=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")

AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${ENCODED_REDIRECT}&scope=openid+profile+email&state=${STATE}&code_challenge=${CODE_CHALLENGE}&code_challenge_method=S256")

AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
echo "Authorization ID: ${AUTHORIZATION_ID}"

register_user "pkce-$(date +%s)@example.com" "TestPass123" "PKCE User"
```

#### 3c. 認可コード取得 → code_verifier ありでトークン交換（成功するはず）

```bash
AUTHORIZE_RESPONSE=$(curl -s \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')

echo "--- code_verifier ありでトークン交換 ---"
curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" \
  --data-urlencode "code_verifier=${CODE_VERIFIER}" | jq '{token_type, expires_in, error}'
```

#### 3d. PKCE なしのフロー（code_challenge 未送信 → verifier 不要）

```bash
# start_auth_flow は code_challenge を送らないので PKCE 検証は発生しない
start_auth_flow
register_user "pkce2-$(date +%s)@example.com" "TestPass123" "PKCE User 2"
complete_auth_flow
echo "PKCE なしのフローも成功する（code_challenge を送らなかったため）"
```

### 4. 期待結果

| ケース | 結果 | 理由 |
|--------|------|------|
| Discovery 変更前 | `null` or 空 | `code_challenge_methods_supported` 未設定 |
| Discovery 変更後 | `["S256"]` | 設定が反映される |
| PKCE あり + 正しい verifier | 成功 | `code_challenge` と `code_verifier` が一致 |
| PKCE なし（code_challenge 未送信） | 成功 | PKCE は任意のため |

> **重要**: `code_challenge_methods_supported` は Discovery に表示するだけで、PKCE を**強制しない**。
> PKCE を必須にしたい場合はクライアント設定側で制御する必要がある。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 9: claims_parameter_supported の影響を確認する

> **やりたいこと**: `claims` パラメータで個別クレームを要求する機能を確認したい
>
> **変わる設定**: `authorization_server.claims_parameter_supported`
>
> **実装の仕組み**: この設定は **Discovery 表示専用**。
> `false` にしても `claims` パラメータの処理は無効化されない。
> クライアントが Discovery を参照して `claims` パラメータの使用可否を判断する想定。

### 1. Discovery の確認

```bash
echo "--- 変更前: claims_parameter_supported ---"
curl -s "${TENANT_BASE}/.well-known/openid-configuration" \
  | jq '.claims_parameter_supported'
```

### 2. 設定変更：false に

```bash
update_auth_server '.claims_parameter_supported = false' \
  | jq '.result.claims_parameter_supported // .'
```

### 3. Discovery 確認

```bash
echo "--- 変更後: claims_parameter_supported ---"
curl -s "${TENANT_BASE}/.well-known/openid-configuration" \
  | jq '.claims_parameter_supported'
```

### 4. 期待結果

| 設定 | Discovery 表示 | 実際の動作 |
|------|---------------|-----------|
| `true`（デフォルト） | `true` | `claims` パラメータ使用可 |
| `false` | `false` | Discovery には `false` と表示されるが、サーバー側で拒否はしない |

> **ポイント**: 現在の実装では `claims_parameter_supported` は Discovery のみ。
> サーバー側で `claims` パラメータを実際に拒否するロジックはない。
> クライアントが行儀よく Discovery を見てくれることが前提。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 10: custom_claims_scope_mapping で claims:* スコープを有効にする

> **やりたいこと**: `claims:` プレフィックス付きスコープでカスタムクレームを取得したい
>
> **変わる設定**: `authorization_server.extension.custom_claims_scope_mapping`
>
> **実装の仕組み**: `ScopeMappingCustomClaimsCreator` が `claims:` プレフィックス付きスコープを
> ユーザーのカスタムプロパティにマッピングする。
>
> **対応するクレーム**: `status`, `ex_sub`, `roles`, `permissions`,
> `assigned_tenants`, `assigned_organizations`, `authentication_devices`
>
> **前提**: ユーザーに対応するカスタムプロパティが設定されている必要がある。
> 未設定の場合はサイレントに省略される。
>
> **id_token_strict_mode との関係**: strict mode が `true` だと、
> ID Token への `claims:*` クレーム追加が無効化される。Access Token / UserInfo は影響なし。

### 1. 認可サーバー設定 + クライアント設定を変更

`custom_claims_scope_mapping` を有効化するだけでなく、**クライアントの `scope` にも `claims:status` を追加**する必要がある。
Experiment 4 で確認した通り、`scopes_supported` は Discovery 表示専用で、
実際のスコープフィルタリングはクライアント設定の `scope` で行われるため。

```bash
# 認可サーバー: claims:status を scopes_supported に追加 + custom_claims_scope_mapping 有効化
update_auth_server '
  .scopes_supported = ["openid", "profile", "email", "claims:status"] |
  .extension.custom_claims_scope_mapping = true
' | jq '.result | {scopes_supported, extension_custom_claims: .extension.custom_claims_scope_mapping}'

# クライアント: scope に claims:status を追加
CLIENT_JSON_RAW=$(curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")
ORIGINAL_CLIENT_SCOPE=$(echo "${CLIENT_JSON_RAW}" | jq -r '.scope')
echo "変更前 scope: ${ORIGINAL_CLIENT_SCOPE}"

UPDATED_CLIENT=$(echo "${CLIENT_JSON_RAW}" | jq '.scope = "openid profile email claims:status"')
curl -s -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${UPDATED_CLIENT}" | jq '{scope: .result.scope, diff: .diff.scope}'
```

### 2. 挙動確認

```bash
start_auth_flow "openid+profile+email+claims:status"
register_user "custom-$(date +%s)@example.com" "TestPass123" "Custom Claims User"
complete_auth_flow

echo "--- Token scope ---"
echo "${TOKEN_RESPONSE}" | jq '.scope'

echo "--- UserInfo ---"
get_userinfo | jq .
```

### 3. 期待結果

| scope | UserInfo / AT に含まれるクレーム |
|-------|-------------------------------|
| `claims:status` | ユーザーの `status`（例: `REGISTERED`） |

> **注意**:
> - ユーザーにカスタムプロパティが設定されていない場合、クレームはサイレントに省略される
> - `status` はほぼ全ユーザーに存在する（`REGISTERED` 等）
> - `roles` は管理者に設定する場合が多い
> - **`scopes_supported` だけでなくクライアントの `scope` にも追加が必要**（Experiment 4 参照）

### 4. 元に戻す

```bash
# クライアントの scope を元に戻す
RESTORED_CLIENT=$(echo "${CLIENT_JSON_RAW}" | jq ".scope = \"${ORIGINAL_CLIENT_SCOPE}\"")
curl -s -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${RESTORED_CLIENT}" | jq '{scope: .result.scope}'

restore_auth_server
```

---

## 実験一覧

| # | やりたいこと | 変わる設定 | 確認できること |
|---|------------|-----------|--------------|
| 1 | ID Token を短くしたい | `id_token_duration` | `exp` クレームの変化 |
| 2 | 認可コードを短くしたい | `authorization_code_valid_duration` | 期限切れコードの拒否 |
| 3 | 認証の鮮度を要求したい | `default_max_age` | 古い認証で `login_required` |
| 4 | scopes_supported の影響範囲 | `scopes_supported` | Discovery のみ（実動作に影響なし） |
| 5 | 認証画面の放置を制限したい | `oauth_authorization_request_expires_in` | 認可コンテキストの期限切れ |
| 6 | ID Token を厳格にしたい | `id_token_strict_mode` | scope ベースのクレーム除外 |
| 7 | RT のローテーション/延長 | `refresh_token_strategy` + `rotate_refresh_token` | 4パターンの挙動差 |
| 8 | PKCE を確認したい | `code_challenge_methods_supported` | Discovery 表示 + PKCE 動作 |
| 9 | claims パラメータの表示 | `claims_parameter_supported` | Discovery 表示のみ変化 |
| 10 | カスタムクレームを取得したい | `custom_claims_scope_mapping` | `claims:*` スコープの動作 |

### EXPERIMENTS-basics.md との対応

| EXPERIMENTS-basics.md | EXPERIMENTS-authorization-server.md |
|----------------------|-------------------------------------|
| Exp 3: claims_supported | → 本ガイドでは扱わない（既存参照） |
| Exp 4: access_token_duration | → 本ガイドでは扱わない（既存参照） |
| Exp 5: session timeout | → Exp 3（default_max_age）と比較して理解 |
| Exp 6: scope による出し分け | → Exp 4（scopes_supported）で制限側を確認 |
