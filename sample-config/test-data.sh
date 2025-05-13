#!/bin/bash

#admin

echo "get access token"
echo "-------------------------------------------------"
echo ""

while getopts ":u:p:t:e:c:s:d:" opt; do
  case $opt in
    u) USERNAME="$OPTARG" ;;
    p) PASSWORD="$OPTARG" ;;
    t) TENANT_ID="$OPTARG" ;;
    e) BASE_URL="$OPTARG" ;;
    c) CLIENT_ID="$OPTARG" ;;
    s) CLIENT_SECRET="$OPTARG" ;;
    d) DRY_RUN="$OPTARG" ;;
    *) echo "Usage: $0 -u <username> -p <password> -t <tenant_id> -e <base_url> -c <client_id> -s <client_secret> -d <dry_run> "&& exit 1 ;;
  esac
done

ACCESS_TOKEN=$(./sample-config/get-access-token.sh -u "$USERNAME" -p "$PASSWORD" -t "$TENANT_ID" -e "$BASE_URL" -c "$CLIENT_ID" -s "$CLIENT_SECRET")

echo "$ACCESS_TOKEN"

##client

echo "client"

client_files=(
  clientSecretBasic.json
  clientSecretBasic2.json
  clientSecretJwt.json
  clientSecretPostWithIdTokenEnc.json
  privateKeyJwt.json
  publicClient.json
  selfSignedTlsClientAuth.json
  selfSignedTlsClientAuth2.json
  unsupportedClient.json
)

for client_file in "${client_files[@]}"; do
  echo "🔧 Registering: $(basename "$client_file")"

./sample-config/upsert-client.sh \
  -t "${TENANT_ID}" \
  -f "./sample-config/admin-tenant/clients/${client_file}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

sleep 1

done

##authentication-config
echo "-------------------------------------------------"
echo ""
echo "authentication-config"

authentication_config_files=(
  authentication-device/fcm.json
  email/smtp.json
  fido-uaf/external.json
  legacy/mocky.json
  password/standard.json
  sms/external.json
  webauthn/webauthn4j.json
)

for authentication_config_file in "${authentication_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$authentication_config_file")"

./sample-config/upsert-authentication-config.sh \
  -t "${TENANT_ID}" \
  -f "./sample-config/admin-tenant/authentication-config/${authentication_config_file}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

sleep 1
done

#identity-verification-config
echo "-------------------------------------------------"
echo ""
echo "identity-verification-config"

identity_verification_config_files=(
  identity-verification-investment.json
  identity-verification-continuous-customer-due-diligence.json
)

for identity_verification_config_file in "${identity_verification_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$identity_verification_config_file")"

./sample-config/upsert-identity-verification-config.sh \
  -t "${TENANT_ID}" \
  -f "./sample-config/admin-tenant/identity/${identity_verification_config_file}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

sleep 1

done

# tenant-1

echo "-------------------------------------------------"
echo ""

echo "tenant-1"
curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/public-tenant/initialization" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./sample-config/tenant-1/initial.json | jq