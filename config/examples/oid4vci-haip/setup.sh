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

set -a
source "${PROJECT_ROOT}/.env"
set +a
BASE="${AUTHORIZATION_SERVER_URL}"
ISSUER="${BASE}/${PUBLIC_TENANT_ID}"
MANAGEMENT="${BASE}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}"

ACCESS_TOKEN=$("${PROJECT_ROOT}/config/scripts/get-access-token.sh" \
  -u "${NEW_ADMIN_EMAIL}" -p "${NEW_ADMIN_PASSWORD}" -t "${ORGANIZER_TENANT_ID}" \
  -e "${BASE}" \
  -c "${NEW_ADMIN_CLIENT_ID}" -s "${NEW_ADMIN_CLIENT_SECRET}")

# --- 認可サーバー: Credential Issuer にする ---
echo ""
echo "🔧 認可サーバーを Credential Issuer にする"

CURRENT=$(curl -sk -H "Authorization: Bearer ${ACCESS_TOKEN}" "${MANAGEMENT}/authorization-server")

UPDATED=$(echo "${CURRENT}" | jq \
  --arg issuer "${ISSUER}" \
  --arg scope "${CREDENTIAL_SCOPE}" '
  .token_endpoint_auth_methods_supported = ((.token_endpoint_auth_methods_supported // []) + ["attest_jwt_client_auth"] | unique)
  | .client_attestation_signing_alg_values_supported = ["ES256"]
  | .client_attestation_pop_signing_alg_values_supported = ["ES256"]
  | .scopes_supported = ((.scopes_supported // []) + [$scope] | unique)
  | .extension.fapi20_scopes = ((.extension.fapi20_scopes // []) + [$scope] | unique)
  | .credential_issuer_metadata = {
      credential_endpoint: ($issuer + "/v1/credentials"),
      nonce_endpoint: ($issuer + "/v1/credentials/nonce"),
      display: [{ name: "idp-server OID4VCI conformance", locale: "en-US" }],
      credential_configurations_supported: {
        ($scope): {
          format: "dc+sd-jwt",
          scope: $scope,
          vct: "urn:idp-server:identity_credential",
          cryptographic_binding_methods_supported: ["jwk"],
          credential_signing_alg_values_supported: ["ES256"],
          proof_types_supported: { jwt: { proof_signing_alg_values_supported: ["ES256"] } },
          credential_metadata: {
            display: [{ name: "Identity Credential", locale: "en-US" }],
            claims: [
              { path: ["given_name"], display: [{ name: "Given Name", locale: "en-US" }] },
              { path: ["family_name"], display: [{ name: "Family Name", locale: "en-US" }] },
              { path: ["email"], display: [{ name: "Email", locale: "en-US" }] }
            ]
          }
        }
      }
    }')

RESPONSE=$(curl -sk -w "\n%{http_code}" -X PUT \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" -H "Content-Type: application/json" \
  "${MANAGEMENT}/authorization-server" -d "${UPDATED}")
STATUS=$(echo "${RESPONSE}" | tail -n1)
if [ "${STATUS}" != "200" ]; then
  echo "  ❌ 認可サーバーの更新に失敗 (${STATUS})"
  echo "${RESPONSE}" | sed '$d' | jq '.' 2>/dev/null || echo "${RESPONSE}" | sed '$d'
  exit 1
fi
echo "  ✅ credential_issuer_metadata を設定"

# --- ウォレット用クライアント ---
echo ""
echo "🔧 ウォレット用クライアントを登録"

ATTESTER_JWKS=$(jq -c '{keys: [{kty, crv, x, y, kid, use, alg}]}' "${SCRIPT_DIR}/pki/attester.jwk.json")
WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

for ENTRY in "${WALLET_CLIENT_ID}:wallet" "${WALLET_CLIENT_2_ID}:wallet2"; do
  CLIENT_ID="${ENTRY%%:*}"
  NAME="${ENTRY##*:}"
  jq -n \
    --arg client_id "${CLIENT_ID}" \
    --arg name "OID4VCI conformance ${NAME}" \
    --arg redirect "${REDIRECT_URI}" \
    --arg scope "${CREDENTIAL_SCOPE}" \
    --arg attester_jwks "${ATTESTER_JWKS}" '{
      client_id: $client_id,
      client_name: $name,
      redirect_uris: [$redirect, ($redirect + "?dummy1=lorem&dummy2=ipsum")],
      response_types: ["code"],
      grant_types: ["authorization_code", "refresh_token"],
      scope: $scope,
      token_endpoint_auth_method: "attest_jwt_client_auth",
      application_type: "native",
      extension: {
        client_attestation_trust_source: "attester_jwks",
        client_attestation_attester_jwks: $attester_jwks
      }
    }' > "${WORK_DIR}/${NAME}.json"

  "${PROJECT_ROOT}/config/scripts/upsert-client.sh" \
    -t "${PUBLIC_TENANT_ID}" \
    -o "${ORGANIZATION_ID}" \
    -f "${WORK_DIR}/${NAME}.json" \
    -b "${BASE}" \
    -a "${ACCESS_TOKEN}" \
    -d false
done

echo ""
echo "✅ 完了"
echo "  credential issuer metadata: ${BASE}/.well-known/openid-credential-issuer/${PUBLIC_TENANT_ID}"
