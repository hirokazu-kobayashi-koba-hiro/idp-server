#!/bin/bash

# read .env
set -a; [ -f .env ] && source .env; set +a

echo "env: $ENV"
echo "url: $BASE_URL"

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

ACCESS_TOKEN=$(./config-sample/get-access-token.sh -u "$ADMIN_USERNAME" -p "$ADMIN_PASSWORD" -t "$ADMIN_TENANT_ID" -e "$BASE_URL" -c "$ADMIN_CLIENT_ID" -s "$ADMIN_CLIENT_SECRET")

echo "$ACCESS_TOKEN"



# tenant-${TENANT_ID}

echo "-------------------------------------------------"
echo ""
echo "tenant-${TENANT_ID}"

./config-sample/upsert-tenant.sh \
  -f "./config-sample/${ENV}/tenant-${TENANT_ID}/tenant.json" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"


# client

echo "-------------------------------------------------"
echo ""
echo "client"

./config-sample/upsert-client.sh \
  -t "${TENANT_ID}" \
  -f "./config-sample/${ENV}/tenant-${TENANT_ID}/clients/clientSecretPost.json" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"


# user
echo "-------------------------------------------------"
echo ""
echo "user"

./config-templates/upsert-user.sh \
  -t "${TENANT_ID}" \
  -f "./config-sample/${ENV}/tenant-${TENANT_ID}/user/admin.json" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"


## authorization-server

echo "-------------------------------------------------"
echo ""
echo "authorization-server"

./config-sample/upsert-authorization-server.sh \
  -t "${TENANT_ID}" \
  -f "./config-sample/${ENV}/tenant-${TENANT_ID}/authorization-server/idp-server.json" \
  -b "${BASE_URL}" \
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

./config-sample/upsert-client.sh \
  -t "${TENANT_ID}" \
  -f "./config-sample/${ENV}/tenant-${TENANT_ID}/clients/${client_file}" \
  -b "${BASE_URL}" \
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

./config-sample/upsert-authentication-config.sh \
  -t "${TENANT_ID}" \
  -f "./config-sample/${ENV}/tenant-${TENANT_ID}/authentication-config/${authentication_config_file}" \
  -b "${BASE_URL}" \
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

./config-sample/upsert-identity-verification-config.sh \
  -t "${TENANT_ID}" \
  -f "./config-sample/${ENV}/tenant-${TENANT_ID}/identity/${identity_verification_config_file}" \
  -b "${BASE_URL}" \
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

./config-sample/upsert-federation-config.sh \
  -t "${TENANT_ID}" \
  -f "./config-sample/${ENV}/tenant-${TENANT_ID}/federation/oidc/${federation_config_file}" \
  -b "${BASE_URL}" \
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

./config-sample/upsert-security-event-hook-config.sh \
  -t "${TENANT_ID}" \
  -f "./config-sample/${ENV}/tenant-${TENANT_ID}/security-event-hook/${security_event_hook_config_file}" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done
