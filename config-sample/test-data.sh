#!/bin/bash

# read .env
set -a; [ -f .env ] && source .env; set +a

echo "env: $ENV"
echo "url: $BASE_URL"

#admin

echo "get access token"
echo "-------------------------------------------------"
echo ""

ACCESS_TOKEN=$(./config-sample/get-access-token.sh -u "$ADMIN_USERNAME" -p "$ADMIN_PASSWORD" -t "$ADMIN_TENANT_ID" -e "$BASE_URL" -c "$ADMIN_CLIENT_ID" -s "$ADMIN_CLIENT_SECRET")

echo "$ACCESS_TOKEN"

# tenant-1 onboarding

echo "-------------------------------------------------"
echo ""
echo "tenant-1"

./config-sample/onboarding.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config-sample/${ENV}/tenant-1/initial.json" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"


## admin-tenant

echo "-------------------------------------------------"
echo ""
echo "admin-tenant"

./config-sample/upsert-tenant.sh \
  -f "./config-sample/${ENV}/admin-tenant/tenants/admin-tenant.json" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

## tenant-a

echo "-------------------------------------------------"
echo ""
echo "tenant-a"

./config-sample/upsert-tenant.sh \
  -f "./config-sample/${ENV}/admin-tenant/tenants/tenant-a.json" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

## authorization-server

echo "-------------------------------------------------"
echo ""
echo "authorization-server"

./config-sample/upsert-authorization-server.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config-sample/${ENV}/admin-tenant/authorization-server/idp-server.json" \
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
  -t "${ADMIN_TENANT_ID}" \
  -f "./config-sample/${ENV}/admin-tenant/clients/${client_file}" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done

##authentication-config
echo "-------------------------------------------------"
echo ""
echo "authentication-config"

authentication_config_files=(
  authentication-device/fcm.json
  email/no-action.json
  external-token/mocky.json
  fido-uaf/external.json
  initial-registration/standard.json
  sms/external.json
  webauthn/webauthn4j.json
)

for authentication_config_file in "${authentication_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$authentication_config_file")"

./config-sample/upsert-authentication-config.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config-sample/${ENV}/admin-tenant/authentication-config/${authentication_config_file}" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done

##authentication-policy
echo "-------------------------------------------------"
echo ""
echo "authentication-policy"

authentication_policy_files=(
  ciba.json
  fido-uaf-deregistration.json
  fido-uaf-registration.json
  oauth.json
)

for authentication_policy_file in "${authentication_policy_files[@]}"; do
  echo "🔧 Registering: $(basename "$authentication_policy_file")"

./config-sample/upsert-authentication-policy.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config-sample/${ENV}/admin-tenant/authentication-policy/${authentication_policy_file}" \
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
  trust-service.json
  authentication-assurance.json
)

for identity_verification_config_file in "${identity_verification_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$identity_verification_config_file")"

./config-sample/upsert-identity-verification-config.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config-sample/${ENV}/admin-tenant/identity/${identity_verification_config_file}" \
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
  -t "${ADMIN_TENANT_ID}" \
  -f "./config-sample/${ENV}/admin-tenant/federation/oidc/${federation_config_file}" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done

#security-event-hook
echo "-------------------------------------------------"
echo ""
echo "security-event-hook"

security_event_hook_config_files=(
  email.json
  slack.json
  ssf.json
)

for security_event_hook_config_file in "${security_event_hook_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$security_event_hook_config_file")"

./config-sample/upsert-security-event-hook-config.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config-sample/${ENV}/admin-tenant/security-event-hook/${security_event_hook_config_file}" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done
