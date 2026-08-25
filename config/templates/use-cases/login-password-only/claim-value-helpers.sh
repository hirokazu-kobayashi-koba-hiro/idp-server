#!/bin/bash
#
# Claim Value Selection (#1816) - Experiment Helpers
#
# EXPERIMENTS-claim-value-selection.md で使う関数を定義する。
# helpers.sh を読み込んだあとに source すること（変数を共有するため）。
#
# 使い方:
#   source helpers.sh --org claim-value-check
#   source claim-value-helpers.sh
#   get_admin_token
#
#   setup_claim_value_tenant          # スコープ + 同意画面 + カスタムクレーム有効化
#   register_claim_value_user         # テストユーザー登録 + custom_properties 付与
#   show_claim_values                 # view-data の候補を表示
#   consent_url                       # ブラウザで開く認可リクエストを表示
#   try_selection "ラベル" '<json>'   # 同意ボディを渡してトークンの中身を見る

# 選択対象を含むスコープ（URL エンコード済み: start_auth_flow にそのまま渡す）
CLAIM_SCOPES="openid+profile+email+claims%3Aaccounts+claims%3Acards+claims%3Abranch"

# ============================================================
# 1. テナント設定
#
# scopes_supported は Discovery 表示専用なので、クライアントの scope にも足す。
# signin_page は同意画面を持つ /auth/ に向ける（/signin/ には ConsentStep がない）。
# ============================================================
setup_claim_value_tenant() {
  update_auth_server '
    .scopes_supported = ["openid", "profile", "email", "claims:accounts", "claims:cards", "claims:branch"] |
    .extension.custom_claims_scope_mapping = true
  ' | jq -c '.result.scopes_supported // .'

  update_client '.scope = "openid profile email claims:accounts claims:cards claims:branch"' \
    | jq -c '.result.scope // .'

  update_tenant '.ui_config.signin_page = "/auth/"' | jq -c '.result.ui_config // .'
}

# ============================================================
# 2. テストユーザー登録
#
# 登録スキーマに custom_properties の口がないので、サインアップ後に管理APIで付与する。
# 登録したメールアドレスは CLAIM_VALUE_EMAIL に入る。
# ============================================================
register_claim_value_user() {
  CLAIM_VALUE_EMAIL="${1:-claim-values-$(date +%s)@example.com}"
  CLAIM_VALUE_PASSWORD="${2:-TestPass123}"

  start_auth_flow "${CLAIM_SCOPES}" > /dev/null
  register_user "${CLAIM_VALUE_EMAIL}" "${CLAIM_VALUE_PASSWORD}" "Claim Values User" > /dev/null
  complete_auth_flow > /dev/null

  local sub
  sub=$(decode_jwt_payload "$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')" | jq -r '.sub')

  curl -s -X PATCH "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/users/${sub}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
      "custom_properties": {
        "accounts": ["acc-1", "acc-2", "acc-3"],
        "branch": "tokyo",
        "cards": [
          {"id": "card-1", "brand": "visa", "limit": 100000},
          {"id": "card-2", "brand": "master", "limit": 50000}
        ]
      }
    }' | jq -c '.result.custom_properties'

  echo "user:     ${CLAIM_VALUE_EMAIL}"
  echo "password: ${CLAIM_VALUE_PASSWORD}"
  echo "sub:      ${sub}"
}

# 登録済みユーザーが必要な関数の前提チェック。
# 未設定のまま進むと password_login が弾かれ、認可も token も失敗して
# 最後の jq が壊れた JSON を読むことになるため、ここで止める。
require_claim_value_user() {
  if [ -z "${CLAIM_VALUE_EMAIL:-}" ] || [ -z "${CLAIM_VALUE_PASSWORD:-}" ]; then
    echo "Error: テストユーザーが未設定です。" >&2
    echo "  register_claim_value_user を実行するか、既存ユーザーを使う場合は次を設定してください:" >&2
    echo '    CLAIM_VALUE_EMAIL="claim-values-xxxxxxxxxx@example.com"' >&2
    echo '    CLAIM_VALUE_PASSWORD="TestPass123"' >&2
    return 1
  fi
}

# ============================================================
# 3. 候補の確認（認証前 → 認証後）
# ============================================================
show_claim_values() {
  require_claim_value_user || return 1

  start_auth_flow "${CLAIM_SCOPES}" > /dev/null

  echo -n "認証前: "
  curl -s -b "${COOKIE_JAR}" \
    "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/view-data" | jq -c '.claim_values'

  password_login "${CLAIM_VALUE_EMAIL}" "${CLAIM_VALUE_PASSWORD}" > /dev/null

  echo -n "認証後: "
  curl -s -b "${COOKIE_JAR}" \
    "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/view-data" | jq -c '.claim_values'
}

# ============================================================
# 4. ブラウザで開く認可リクエスト
# ============================================================
consent_url() {
  local redirect_enc
  redirect_enc=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")
  echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${redirect_enc}&scope=${CLAIM_SCOPES}&state=claim-value-check"
}

# ============================================================
# 5. 同意ボディを渡してアクセストークンの中身を見る
#
# complete_auth_flow は空ボディを送るので、選択を渡す場合はこちらを使う。
#
# 使用例:
#   try_selection "acc-2 だけ" '{"granted_claim_values": {"accounts": ["acc-2"]}}'
# ============================================================
try_selection() {
  local label="$1"
  local body="$2"

  require_claim_value_user || return 1

  echo "--- ${label} ---"

  start_auth_flow "${CLAIM_SCOPES}" > /dev/null

  local login_response
  login_response=$(password_login "${CLAIM_VALUE_EMAIL}" "${CLAIM_VALUE_PASSWORD}")
  if echo "${login_response}" | jq -e '.error' > /dev/null 2>&1; then
    echo "認証に失敗しました: $(echo "${login_response}" | jq -c '.')" >&2
    return 1
  fi

  local authorize_response code token_response access_token
  authorize_response=$(curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
    -H "Content-Type: application/json" -d "${body}")
  code=$(echo "${authorize_response}" | jq -r '.redirect_uri // ""' | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
  if [ -z "${code}" ]; then
    echo "認可コードが取得できませんでした: $(echo "${authorize_response}" | jq -c '.')" >&2
    return 1
  fi

  token_response=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=authorization_code" \
    --data-urlencode "code=${code}" \
    --data-urlencode "redirect_uri=${REDIRECT_URI}" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}")
  access_token=$(echo "${token_response}" | jq -r '.access_token // ""')
  if [ -z "${access_token}" ]; then
    echo "トークンが取得できませんでした: $(echo "${token_response}" | jq -c '.')" >&2
    return 1
  fi

  # 出ていないクレームは表示にも出さない。{accounts, cards, branch} と書くと、
  # jq が存在しないキーを null として作ってしまい「省略された」のか
  # 「null が入っている」のか区別できなくなるため。
  decode_jwt_payload "${access_token}" \
    | jq -c 'with_entries(select(.key == "accounts" or .key == "cards" or .key == "branch"))'
}

echo "Claim value selection helpers loaded (setup_claim_value_tenant / register_claim_value_user / show_claim_values / consent_url / try_selection)"
