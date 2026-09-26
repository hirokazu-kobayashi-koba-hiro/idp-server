#!/bin/bash
# oid4vci-haip テナントを Credential Issuer にする（認可サーバーの上書きとウォレット用クライアント）。
#
# setup.sh の後半。テナントを作り直さずに設定だけ流し直せるよう分けてある（何度流しても同じ結果）。
#   - jwks に VC の署名鍵（pki/issuer.jwk.json、x5c 付き）を足す
#   - credential_issuance: 署名鍵と、利用者のどのクレームを VC に載せるか
#   - credential_issuer_metadata: 公開するメタデータ
#
# 使い方: ./configure-issuer.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

ORGANIZATION_ID="fc67e9a8-623f-4a64-9301-1e9044224457"
ORGANIZER_TENANT_ID="41b7e675-29a2-4145-a857-3cfb41f7ac3a"
PUBLIC_TENANT_ID="b01c0787-b7be-4699-9a5a-042f70d41697"
NEW_ADMIN_EMAIL="${NEW_ADMIN_EMAIL:-oid4vci-conformance-admin@example.com}"
NEW_ADMIN_PASSWORD="Oid4vciConformanceSecure123!"
NEW_ADMIN_CLIENT_ID="d70f5b22-e27e-4556-8473-d8711c4b58d5"
NEW_ADMIN_CLIENT_SECRET="oid4vci-conformance-admin-secret-change-in-production-minimum-32-characters"
WALLET_CLIENT_ID="876cfb14-9fef-4436-a3b0-7282879164f4"
WALLET_CLIENT_2_ID="f4840d7f-b58f-4f1c-9f65-ba3ea2ce1f54"
TEST_ALIAS="idp-server-oid4vci-haip"
REDIRECT_URI="https://localhost.emobix.co.uk:8443/test/a/${TEST_ALIAS}/callback"
CREDENTIAL_SCOPE="identity_credential"

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
  --argjson signing_key "$(cat "${SCRIPT_DIR}/pki/issuer.jwk.json")" \
  --arg scope "${CREDENTIAL_SCOPE}" '
  .token_endpoint_auth_methods_supported = ((.token_endpoint_auth_methods_supported // []) + ["attest_jwt_client_auth"] | unique)
  | .client_attestation_signing_alg_values_supported = ["ES256"]
  | .client_attestation_pop_signing_alg_values_supported = ["ES256"]
  | .scopes_supported = ((.scopes_supported // []) + [$scope] | unique)
  | .extension.fapi20_scopes = ((.extension.fapi20_scopes // []) + [$scope] | unique)
  | .jwks = ((.jwks | fromjson | .keys |= (map(select(.kid != $signing_key.kid)) + [$signing_key])) | tojson)
  | .credential_issuance = {
      signing_key_id: $signing_key.kid,
      credentials: {
        ($scope): {
          expires_in: 31536000,
          claims: [
            { name: "given_name", from: "$.given_name" },
            { name: "family_name", from: "$.family_name" },
            { name: "email", from: "$.email" }
          ]
        }
      }
    }
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

# --- 認証ポリシー: credential の scope を標準ポリシーに入れる ---
# テンプレートのポリシーは scope で選ばれる（transfers / write → 高セキュリティ、openid / read /
# account → 標準）。どれにも当たらない scope は優先度の高い高セキュリティ側に倒れ、そちらは
# パスキーの新規登録を許さないため、初めて来た利用者がサインインできない。VC の発行は標準側で扱う。
echo ""
echo "🔧 認証ポリシーに ${CREDENTIAL_SCOPE} を足す"

POLICY_ID=$(curl -sk -H "Authorization: Bearer ${ACCESS_TOKEN}" "${MANAGEMENT}/authentication-policies" \
  | jq -r '.list[] | select(.flow == "oauth") | .id')
POLICY=$(curl -sk -H "Authorization: Bearer ${ACCESS_TOKEN}" "${MANAGEMENT}/authentication-policies/${POLICY_ID}")
UPDATED_POLICY=$(echo "${POLICY}" | jq --arg scope "${CREDENTIAL_SCOPE}" '
  .policies |= map(
    if .description == "financial_standard_policy"
    then .conditions.scopes = ((.conditions.scopes // []) + [$scope] | unique)
    else . end)')
STATUS=$(curl -sk -o /dev/null -w "%{http_code}" -X PUT \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" -H "Content-Type: application/json" \
  "${MANAGEMENT}/authentication-policies/${POLICY_ID}" -d "${UPDATED_POLICY}")
if [ "${STATUS}" != "200" ]; then
  echo "  ❌ 認証ポリシーの更新に失敗 (${STATUS})"
  exit 1
fi
echo "  ✅ financial_standard_policy の scope に ${CREDENTIAL_SCOPE} を追加"

echo ""
echo "✅ 完了"
echo "  credential issuer metadata: ${BASE}/.well-known/openid-credential-issuer/${PUBLIC_TENANT_ID}"
