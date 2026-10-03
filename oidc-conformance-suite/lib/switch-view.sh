#!/bin/bash
# テナントの認可画面を、別サイト構成（cross-site）と元の構成のあいだで切り替える。
#
#   ./switch-view.sh <tenant-id> cross-site [<認可画面の base_url>]
#   ./switch-view.sh <tenant-id> same-site
#
# cross-site にすると、テナントの設定を次のように書き換える（書き換える前の設定は保存しておく）。
#
#   ui_config.cross_site            true（別サイト認可画面モード: view_binding / auth_proof / /complete）
#   ui_config.base_url              認可画面を別サイトに置く。指定が無ければ、今の base_url が
#                                   idp-server と別サイトならそのまま、同じサイトなら https://auth.idp.local
#   cors_config.allow_origins       認可画面のオリジンを足す
#   session_config.cookie_same_site Lax
#
# Lax にするのは、別サイト認可画面モードで通ったことの証明にするため。Lax の Cookie は、どのブラウザでも
# 別サイトからの XHR には付かない（Safari がサードパーティ Cookie を落とすのと同じ状態になる）。None のままだと、
# Chromium はサードパーティ Cookie を送るので、モードを使わなくても通ってしまう。/complete はトップレベルの
# GET なので、Lax でも Cookie が届く。
#
# same-site にすると、保存しておいた設定に戻す。
#
# テナント設定の更新は全置換なので、GET したものを書き換えて PUT する。システム管理者のトークンは
# リポジトリ直下の .env から取る（config/examples/*/update.sh と同じ）。

set -euo pipefail

TENANT_ID="${1:-}"
MODE="${2:-}"
VIEW_BASE_URL="${3:-}"

if [ -z "$TENANT_ID" ] || { [ "$MODE" != "cross-site" ] && [ "$MODE" != "same-site" ]; }; then
  echo "usage: $0 <tenant-id> cross-site [<認可画面の base_url>]" >&2
  echo "       $0 <tenant-id> same-site" >&2
  exit 1
fi

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${LIB_DIR}/../.." && pwd)"
BACKUP_DIR="${LIB_DIR}/.view-backup"
BACKUP_FILE="${BACKUP_DIR}/${TENANT_ID}.json"

if [ ! -f "${REPO_ROOT}/.env" ]; then
  echo "❌ ${REPO_ROOT}/.env がありません" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
source "${REPO_ROOT}/.env"
set +a

TOKEN=$(curl -sk -X POST "${AUTHORIZATION_SERVER_URL}/${ADMIN_TENANT_ID}/v1/tokens" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ADMIN_USER_EMAIL}" \
  --data-urlencode "password=${ADMIN_USER_PASSWORD}" \
  --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
  --data-urlencode "scope=account management" | jq -r '.access_token // empty')
if [ -z "$TOKEN" ]; then
  echo "❌ システム管理者のトークンを取れませんでした" >&2
  exit 1
fi

TENANT_URL="${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}"

put_tenant() {
  local body="$1"
  local response code
  response=$(curl -sk -w "\n%{http_code}" -X PUT "$TENANT_URL" \
    -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$body")
  code=$(echo "$response" | tail -n1)
  if [ "$code" != "200" ]; then
    echo "❌ テナントの更新に失敗しました (HTTP ${code})" >&2
    echo "$response" | sed '$d' >&2
    exit 1
  fi
}

show() {
  curl -sk "$TENANT_URL" -H "Authorization: Bearer ${TOKEN}" | jq '{
    cross_site: .ui_config.cross_site,
    base_url: .ui_config.base_url,
    cookie_same_site: .session_config.cookie_same_site,
    allow_origins: .cors_config.allow_origins
  }'
}

if [ "$MODE" = "same-site" ]; then
  if [ ! -f "$BACKUP_FILE" ]; then
    echo "❌ 戻す設定がありません（${BACKUP_FILE}）。cross-site に切り替えたことが無いか、すでに戻しています" >&2
    exit 1
  fi
  put_tenant "$(cat "$BACKUP_FILE")"
  rm -f "$BACKUP_FILE"
  echo "✅ ${TENANT_ID} を元の構成に戻しました"
  show
  exit 0
fi

CURRENT=$(curl -sk "$TENANT_URL" -H "Authorization: Bearer ${TOKEN}")
if [ "$(echo "$CURRENT" | jq -r '.id // empty')" != "$TENANT_ID" ]; then
  echo "❌ テナントを取れませんでした: $(echo "$CURRENT" | head -c 300)" >&2
  exit 1
fi

# 二度目の cross-site で、書き換え済みの設定を「元」として上書きしないようにする。
mkdir -p "$BACKUP_DIR"
if [ ! -f "$BACKUP_FILE" ]; then
  echo "$CURRENT" > "$BACKUP_FILE"
fi

if [ -z "$VIEW_BASE_URL" ]; then
  current_base=$(echo "$CURRENT" | jq -r '.ui_config.base_url // empty')
  current_host=$(echo "$current_base" | sed -E 's#^[a-z]+://([^/:]+).*#\1#')
  api_host=$(echo "$AUTHORIZATION_SERVER_URL" | sed -E 's#^[a-z]+://([^/:]+).*#\1#')
  # 登録可能ドメイン（ここでは末尾の 2 ラベル）が違えば、すでに別サイト
  if [ -n "$current_host" ] && [ "$(echo "$current_host" | awk -F. '{print $(NF-1)"."$NF}')" != "$(echo "$api_host" | awk -F. '{print $(NF-1)"."$NF}')" ]; then
    VIEW_BASE_URL="$current_base"
  else
    VIEW_BASE_URL="https://auth.idp.local"
  fi
fi
VIEW_ORIGIN=$(echo "$VIEW_BASE_URL" | sed -E 's#^([a-z]+://[^/]+).*#\1#')

UPDATED=$(echo "$CURRENT" | jq --arg base "$VIEW_BASE_URL" --arg origin "$VIEW_ORIGIN" '
  .ui_config.cross_site = true
  | .ui_config.base_url = $base
  | .session_config.cookie_same_site = "Lax"
  | .cors_config.allow_origins = (((.cors_config.allow_origins // []) + [$origin]) | unique)')
put_tenant "$UPDATED"
echo "✅ ${TENANT_ID} を別サイト構成にしました（戻すときは: $0 ${TENANT_ID} same-site）"
show
