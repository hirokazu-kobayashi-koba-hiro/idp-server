#!/bin/bash

# read .env
set -a; [ -f .env ] && source .env; set +a

echo "env: $ENV"
echo "url: $AUTHORIZATION_SERVER_URL"

#admin

echo "get access token"
echo "-------------------------------------------------"
echo ""

ACCESS_TOKEN=$(./config/scripts/get-access-token.sh -u "$ADMIN_USER_EMAIL" -p "$ADMIN_USER_PASSWORD" -t "$ADMIN_TENANT_ID" -e "$AUTHORIZATION_SERVER_URL" -c "$ADMIN_CLIENT_ID" -s "$ADMIN_CLIENT_SECRET")

echo "$ACCESS_TOKEN"

# test-tenant onboarding

echo "-------------------------------------------------"
echo ""
echo "test-tenant"

./config/scripts/onboarding.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config/examples/e2e/test-tenant/initial.json" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"


# organizer-tenant

echo "-------------------------------------------------"
echo ""
echo "organizer-tenant"

./config/scripts/onboarding.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config/examples/e2e/organizer-tenant/initial.json" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"


## admin-tenant

echo "-------------------------------------------------"
echo ""
echo "test-tenant"

./config/scripts/upsert-tenant.sh \
  -f "./config/examples/e2e/test-tenant/tenants/admin-tenant.json" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

## tenant-a

echo "-------------------------------------------------"
echo ""
echo "tenant-a"

./config/scripts/upsert-tenant.sh \
  -f "./config/examples/e2e/test-tenant/tenants/tenant-a.json" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

## authorization-server

echo "-------------------------------------------------"
echo ""
echo "authorization-server"

./config/scripts/upsert-authorization-server.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config/examples/e2e/test-tenant/authorization-server/idp-server.json" \
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
  -t "${ADMIN_TENANT_ID}" \
  -f "./config/examples/e2e/test-tenant/clients/${client_file}" \
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
  sms/external.json
  webauthn/webauthn4j.json
)

for authentication_config_file in "${authentication_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$authentication_config_file")"

./config/scripts/upsert-authentication-config.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config/examples/e2e/test-tenant/authentication-config/${authentication_config_file}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
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

./config/scripts/upsert-authentication-policy.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config/examples/e2e/test-tenant/authentication-policy/${authentication_policy_file}" \
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
  trust-service.json
  authentication-assurance.json
)

for identity_verification_config_file in "${identity_verification_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$identity_verification_config_file")"

./config/scripts/upsert-identity-verification-config.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config/examples/e2e/test-tenant/identity/${identity_verification_config_file}" \
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
  -t "${ADMIN_TENANT_ID}" \
  -f "./config/examples/e2e/test-tenant/federation/oidc/${federation_config_file}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
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

./config/scripts/upsert-security-event-hook-config.sh \
  -t "${ADMIN_TENANT_ID}" \
  -f "./config/examples/e2e/test-tenant/security-event-hook/${security_event_hook_config_file}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

done
