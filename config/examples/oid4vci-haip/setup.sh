#!/bin/bash
# OID4VCI 1.0 / HAIP 適合性テスト用テナントを作成する（oid4vci-1_0-issuer-haip-test-plan）。
#
# FAPI 2.0 のテンプレート（config/templates/use-cases/financial-grade-2.0/setup.sh）で土台を作り、
# その上に Credential Issuer としての設定とウォレット用クライアントを足す。HAIP の認可フローは
# FAPI 2.0（PAR / PKCE / DPoP）そのものなので、土台は同じでよい。
#
# 足すもの:
#   - 認可サーバー: attest_jwt_client_auth、Client Attestation の alg、credential_issuer_metadata、
#     credential の scope（FAPI 2.0 のプロファイルを効かせるため fapi20_scopes にも入れる）
#   - ウォレット用クライアント 2 つ（multiple-clients モジュールが client2 を使う）。
#     認証は attest_jwt_client_auth、信頼元は attester_jwks（suite が Wallet Provider 役）
#
# PKI（pki/）は pki/generate.sh で作ったものをコミットしてある。
#
# 使い方:
#   ./setup.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
TEMPLATE_DIR="${PROJECT_ROOT}/config/templates/use-cases/financial-grade-2.0"

# --- 固定 ID（oidc-test/*.json と driver/flow.mjs の TENANTS と一致させること）---
export ORGANIZATION_ID="fc67e9a8-623f-4a64-9301-1e9044224457"
export ORGANIZER_TENANT_ID="41b7e675-29a2-4145-a857-3cfb41f7ac3a"
export PUBLIC_TENANT_ID="b01c0787-b7be-4699-9a5a-042f70d41697"

export TLS_CLIENT_ID="8f3c0548-4250-40ef-8dfe-000f745bf643"
export PKJ_CLIENT_ID="d12abaf8-f995-4ed9-b658-12167f9b6427"

export FINANCIAL_USER_SUB="8af355d7-2832-4515-9042-17600acb7d00"
export FINANCIAL_DEVICE_ID="dd25c102-c2f9-4fcd-99db-3f54dfbc7695"

export ORGANIZATION_NAME="OID4VCI HAIP Conformance Organization"
export NEW_ADMIN_EMAIL="${NEW_ADMIN_EMAIL:-oid4vci-conformance-admin@example.com}"
export NEW_ADMIN_PASSWORD="Oid4vciConformanceSecure123!"
export NEW_ADMIN_CLIENT_ID="d70f5b22-e27e-4556-8473-d8711c4b58d5"
export NEW_ADMIN_CLIENT_SECRET="oid4vci-conformance-admin-secret-change-in-production-minimum-32-characters"

export FINANCIAL_USER_EMAIL="oid4vci-conformance@example.com"
export FINANCIAL_USER_PASSWORD="Oid4vciConformanceUser123!"

export COOKIE_NAME="OID4VCI_CONFORMANCE_SESSION"
export SIGNIN_PAGE="/auth/"

WALLET_CLIENT_ID="876cfb14-9fef-4436-a3b0-7282879164f4"
WALLET_CLIENT_2_ID="f4840d7f-b58f-4f1c-9f65-ba3ea2ce1f54"
TEST_ALIAS="idp-server-oid4vci-haip"
export REDIRECT_URI="https://localhost.emobix.co.uk:8443/test/a/${TEST_ALIAS}/callback"

CREDENTIAL_SCOPE="identity_credential"

echo "=========================================="
echo "OID4VCI HAIP 適合性テスト用テナント"
echo "=========================================="
echo "  organization : ${ORGANIZATION_ID}"
echo "  tenant       : ${PUBLIC_TENANT_ID}"
echo "  wallet       : ${WALLET_CLIENT_ID} / ${WALLET_CLIENT_2_ID}"
echo ""

"${TEMPLATE_DIR}/setup.sh"

"${SCRIPT_DIR}/configure-issuer.sh"
