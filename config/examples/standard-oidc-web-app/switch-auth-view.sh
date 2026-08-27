#!/bin/bash
set -e

# Switches the sample application's public tenant between auth view topologies, so the
# difference each one makes can be observed in a browser without rebuilding anything.
#
#   same-site           auth.local.test    SameSite=Lax   同一登録可能ドメイン (local.test)
#   cross-site          auth.idp.local     SameSite=None  別ドメイン。Safari の ITP が効く
#   cross-site-cp       auth-cp.idp.local  SameSite=None  別ドメイン + context path (/idp-admin)
#   strict              auth.local.test    SameSite=Strict 連携が必ず落ちる構成の再現
#
# 見どころ:
#   cross-site を Safari で開くと、auth view から API への fetch が third-party になり、
#   ITP が SameSite に関係なく Cookie を落とすため、ログインそのものが成立しない。
#   Chrome でサードパーティ Cookie を許可していれば通る。
#
#   strict は同一サイトなのでログインは通るが、/linking/start への遷移が
#   cross-site の top-level navigation になるため OP セッション Cookie が送られず、
#   アカウント連携だけが 401 になる。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
PUBLIC_TENANT_ID="a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d"

case "${1:-}" in
  same-site)     TENANT_FILE="${SCRIPT_DIR}/public-tenant-same-site.json" ;;
  cross-site)    TENANT_FILE="${SCRIPT_DIR}/public-tenant-cross-site.json" ;;
  cross-site-cp) TENANT_FILE="${SCRIPT_DIR}/public-tenant-cross-site-context-path.json" ;;
  strict)        TENANT_FILE="${SCRIPT_DIR}/public-tenant-strict.json" ;;
  *)
    echo "Usage: $0 {same-site|cross-site|cross-site-cp|strict}"
    echo ""
    echo "  same-site      auth.local.test    SameSite=Lax    どのブラウザでも通る"
    echo "  cross-site     auth.idp.local     SameSite=None   Safari はログイン不可"
    echo "  cross-site-cp  auth-cp.idp.local  SameSite=None   + context path"
    echo "  strict         auth.local.test    SameSite=Strict ログインは通るが連携が 401"
    exit 1
    ;;
esac

set -a; source "${PROJECT_ROOT}/.env"; set +a

TOKEN=$(curl -sk -X POST "${AUTHORIZATION_SERVER_URL}/${ADMIN_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ADMIN_USER_EMAIL}" \
  --data-urlencode "password=${ADMIN_USER_PASSWORD}" \
  --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
  --data-urlencode "scope=account management" | jq -r '.access_token')

BODY=$(jq '.tenant' "${TENANT_FILE}")

CODE=$(curl -sk -o /tmp/switch-auth-view.out -w "%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${BODY}")

if [ "${CODE}" != "200" ]; then
  echo "❌ failed (HTTP ${CODE})"
  cat /tmp/switch-auth-view.out
  exit 1
fi

echo "✅ switched to ${1}"
curl -sk -o /dev/null -w "   authorize -> %{redirect_url}\n" \
  "${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/authorizations?response_type=code&client_id=8a9f5e2c-1b3d-4c6a-9f8e-7d5c3a2b1e4f&redirect_uri=https%3A%2F%2Fsample.local.test%2Fapi%2Fauth%2Fcallback%2Fidp-server&scope=openid+profile+email&state=probe"
