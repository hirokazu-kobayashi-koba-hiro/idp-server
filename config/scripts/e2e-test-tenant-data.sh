#!/bin/bash

# read .env
set -a; [ -f .env ] && source .env; set +a

echo "url: $AUTHORIZATION_SERVER_URL"
ORGANIZATION_ID="9eb8eb8c-2615-4604-809f-5cae1c00a462"

#admin

echo "get access token"
echo "-------------------------------------------------"
echo ""

while getopts ":t:" opt; do
  case $opt in
    t) TENANT_ID="$OPTARG" ;;
    *) echo "Usage: $0 -t <tenant_id> "&& exit 1 ;;
  esac
done

## org access-token

ACCESS_TOKEN=$(./config/scripts/get-access-token.sh -u "ito.ichiro@gmail.com" -p "successUserCode001" -t "67e7eae6-62b0-4500-9eff-87459f63fc66" -e "$AUTHORIZATION_SERVER_URL" -c "clientSecretPost" -s "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890")

echo "org access-token"
echo "$ACCESS_TOKEN"



# tenant-${TENANT_ID}

echo "-------------------------------------------------"
echo ""
echo "tenant-${TENANT_ID}"

./config/scripts/upsert-tenant.sh \
  -f "./config/examples/e2e/tenant-${TENANT_ID}/tenant.json" \
  -o "${ORGANIZATION_ID}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"


# client

echo "-------------------------------------------------"
echo ""
echo "client"

./config/scripts/upsert-client.sh \
  -t "${TENANT_ID}" \
  -o "${ORGANIZATION_ID}" \
  -f "./config/examples/e2e/tenant-${TENANT_ID}/clients/clientSecretPost.json" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"


# user
echo "-------------------------------------------------"
echo ""
echo "user"

./config/scripts/upsert-user.sh \
  -t "${TENANT_ID}" \
  -o "${ORGANIZATION_ID}" \
  -f "./config/examples/e2e/tenant-${TENANT_ID}/user/admin.json" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"


## authorization-server

echo "-------------------------------------------------"
echo ""
echo "authorization-server"

./config/scripts/upsert-authorization-server.sh \
  -t "${TENANT_ID}" \
  -o "${ORGANIZATION_ID}" \
  -f "./config/examples/e2e/tenant-${TENANT_ID}/authorization-server/idp-server.json" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

##client

echo "client"

client_files=(
  clientSecretBasic.json
  clientSecretBasic2.json
  clientSecretJwt.json
  clientSecretPost.json
  clientSecretPostWithIdTokenEnc.json
  privateKeyJwt.json
  publicClient.json
  selfSignedTlsClientAuth.json
  selfSignedTlsClientAuth2.json
  unsupportedClient.json
)

for client_file in "${client_files[@]}"; do
  echo "🔧 Registering: $(basename "$client_file")"

./config/scripts/upsert-client.sh \
  -t "${TENANT_ID}" \
  -o "${ORGANIZATION_ID}" \
  -f "./config/examples/e2e/tenant-${TENANT_ID}/clients/${client_file}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done

##authentication-config
echo "-------------------------------------------------"
echo ""
echo "authentication-config"

authentication_config_files=(
  authentication-device/multi-push-notification.json
  email/no-action.json
  external-token/mocky.json
  fido-uaf/external.json
  initial-registration/standard.json
  sms/no-action.json
  webauthn/webauthn4j.json
)

for authentication_config_file in "${authentication_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$authentication_config_file")"

./config/scripts/upsert-authentication-config.sh \
  -t "${TENANT_ID}" \
  -o "${ORGANIZATION_ID}" \
  -f "./config/examples/e2e/tenant-${TENANT_ID}/authentication-config/${authentication_config_file}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done

#identity-verification-config
echo "-------------------------------------------------"
echo ""
echo "identity-verification-config"

identity_verification_config_files=(
  investment-account-opening.json
  continuous-customer-due-diligence.json
)

for identity_verification_config_file in "${identity_verification_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$identity_verification_config_file")"

./config/scripts/upsert-identity-verification-config.sh \
  -t "${TENANT_ID}" \
  -o "${ORGANIZATION_ID}" \
  -f "./config/examples/e2e/tenant-${TENANT_ID}/identity/${identity_verification_config_file}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done

#federation-oidc
echo "-------------------------------------------------"
echo ""
echo "federation-config oidc"

federation_config_files=(
  facebook.json
  google.json
  oauth-extenstion.json
)

for federation_config_file in "${federation_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$federation_config_file")"

./config/scripts/upsert-federation-config.sh \
  -t "${TENANT_ID}" \
  -o "${ORGANIZATION_ID}" \
  -f "./config/examples/e2e/tenant-${TENANT_ID}/federation/oidc/${federation_config_file}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done

#federation-oidc
echo "-------------------------------------------------"
echo ""
echo "federation-config oidc"

security_event_hook_config_files=(
  slack.json
  ssf.json
)

for security_event_hook_config_file in "${security_event_hook_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$security_event_hook_config_file")"

./config/scripts/upsert-security-event-hook-config.sh \
  -t "${TENANT_ID}" \
  -o "${ORGANIZATION_ID}" \
  -f "./config/examples/e2e/tenant-${TENANT_ID}/security-event-hook/${security_event_hook_config_file}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done

##authentication-policy
echo "-------------------------------------------------"
echo ""
echo "authentication-policy"

authentication_policy_files=(
  oauth.json
)

for authentication_policy_file in "${authentication_policy_files[@]}"; do
  echo "🔧 Registering: $(basename "$authentication_policy_file")"

./config/scripts/upsert-authentication-policy.sh \
  -t "${TENANT_ID}" \
  -o "${ORGANIZATION_ID}" \
  -f "./config/examples/e2e/tenant-${TENANT_ID}/authentication-policy/${authentication_policy_file}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done