#!/bin/bash
# FAPI 2.0 適合性テスト用テナントを作成する。
#
# config/templates/use-cases/financial-grade-2.0/setup.sh を、ID を固定した状態で呼び出す。
# テンプレートは既定で ID を uuidgen するが、OIDF のテスト設定 JSON
# (oidc-test/fapi2/*.json) はテナント ID とクライアント ID を直接持っているため、
# ここで固定する必要がある。
#
# なぜ専用テナントを作るのか:
#   FAPI 2.0 のテナントとしては config/examples/e2e/fapi2-tenant/ が既にあるが、そちらは
#   e2e の仕様準拠テスト (e2e/src/tests/spec/fapi2_0_mtls.test.js、909 行) が使っており、
#   認証ポリシーや ui_config を適合性テストの都合で変えると影響が読めない。分離する。
#
# 使い方:
#   ./setup.sh
#   ./setup.sh --dry-run

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
TEMPLATE_DIR="${PROJECT_ROOT}/config/templates/use-cases/financial-grade-2.0"

if [ ! -f "${TEMPLATE_DIR}/setup.sh" ]; then
  echo "❌ テンプレートが見つかりません: ${TEMPLATE_DIR}/setup.sh" >&2
  exit 1
fi

# --- 固定 ID（oidc-test/fapi2/*.json と一致させること）---
export ORGANIZATION_ID="c1f2a3b4-d5e6-4f7a-8b9c-0d1e2f3a4b5c"
export ORGANIZER_TENANT_ID="c2f3a4b5-d6e7-4f8a-9b0c-1d2e3f4a5b6c"
export PUBLIC_TENANT_ID="c3f4a5b6-d7e8-4f9a-0b1c-2d3e4f5a6b7c"

export TLS_CLIENT_ID="c4f5a6b7-d8e9-4f0a-1b2c-3d4e5f6a7b8c"
export PKJ_CLIENT_ID="c5f6a7b8-d9e0-4f1a-2b3c-4d5e6f7a8b9c"

export FINANCIAL_USER_SUB="c6f7a8b9-d0e1-4f2a-3b4c-5d6e7f8a9b0c"
export FINANCIAL_DEVICE_ID="c7f8a9b0-d1e2-4f3a-4b5c-6d7e8f9a0b1c"

export ORGANIZATION_NAME="FAPI 2.0 Conformance Organization"
export NEW_ADMIN_EMAIL="${NEW_ADMIN_EMAIL:-fapi2-conformance-admin@example.com}"
export NEW_ADMIN_PASSWORD="Fapi2ConformanceSecure123!"
export NEW_ADMIN_CLIENT_ID="c8f9a0b1-d2e3-4f4a-5b6c-7d8e9f0a1b2c"
export NEW_ADMIN_CLIENT_SECRET="fapi2-conformance-admin-secret-change-in-production-minimum-32-characters"

export FINANCIAL_USER_EMAIL="fapi2-conformance@example.com"
export FINANCIAL_USER_PASSWORD="Fapi2ConformanceUser123!"

export COOKIE_NAME="FAPI2_CONFORMANCE_SESSION"

# 認証ポリシーが email → fido2 の 2 段なので、ステップを順に描画する /auth/ を使う。
# テンプレート既定の /signin/fido2/ はパスキー直行の旧画面で、複数ステップを扱えない。
export SIGNIN_PAGE="/auth/"

# suite の callback。oidc-test/fapi2/*.json の alias と対応する。
export REDIRECT_URI="https://localhost.emobix.co.uk:8443/test/a/idp-server-fapi2-private_key_jwt/callback"

echo "=========================================="
echo "FAPI 2.0 適合性テスト用テナント"
echo "=========================================="
echo "  organization : ${ORGANIZATION_ID}"
echo "  tenant       : ${PUBLIC_TENANT_ID}"
echo "  private_key_jwt client : ${PKJ_CLIENT_ID}"
echo "  tls_client_auth client : ${TLS_CLIENT_ID}"
echo ""

"${TEMPLATE_DIR}/setup.sh" "$@"

# --- 適合性テスト用クライアント ---
#
# テンプレートが作るクライアント（TLS_CLIENT_ID / PKJ_CLIENT_ID）とは別に、OIDF suite が
# 秘密鍵と証明書を持っているクライアントを登録する。鍵は suite 側のテスト設定 JSON に
# 埋まっているため、サーバ側をそれに合わせる必要がある。
#
# 定義は config/examples/e2e/fapi2-tenant/clients/ と同じ鍵・DN で、ID と alias だけ
# 別にしてある（client_configuration の主キーは id 単独なので client_id はテナントを
# またいで一意。使い回すと 409 になる）。
if [ "${1:-}" = "--dry-run" ]; then
  echo "（--dry-run のため適合性テスト用クライアントの登録はスキップ）"
  exit 0
fi

# AUTHORIZATION_SERVER_URL はテンプレート側と同じ .env から取る（子プロセスの値は届かない）
set -a
source "${PROJECT_ROOT}/.env"
set +a

echo ""
echo "🔧 適合性テスト用クライアントを登録"

ACCESS_TOKEN=$("${PROJECT_ROOT}/config/scripts/get-access-token.sh" \
  -u "${NEW_ADMIN_EMAIL}" -p "${NEW_ADMIN_PASSWORD}" -t "${ORGANIZER_TENANT_ID}" \
  -e "${AUTHORIZATION_SERVER_URL}" \
  -c "${NEW_ADMIN_CLIENT_ID}" -s "${NEW_ADMIN_CLIENT_SECRET}")

for CLIENT_FILE in "${SCRIPT_DIR}"/clients/*.json; do
  "${PROJECT_ROOT}/config/scripts/upsert-client.sh" \
    -t "${PUBLIC_TENANT_ID}" \
    -o "${ORGANIZATION_ID}" \
    -f "${CLIENT_FILE}" \
    -b "${AUTHORIZATION_SERVER_URL}" \
    -a "${ACCESS_TOKEN}" \
    -d false
done
