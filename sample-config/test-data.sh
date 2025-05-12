#!/bin/zsh

#admin
curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/public-tenant/initialization" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./sample-config/tenant1.json | jq

#client

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

./sample-config/register-client.sh \
  -u ito.ichiro@gmail.com \
  -p successUserCode \
  -t 67e7eae6-62b0-4500-9eff-87459f63fc66 \
  -f ./sample-config/clients/${client_file}

sleep 1

done

#authentication-config
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

./sample-config/register-authentication-config.sh \
  -u ito.ichiro@gmail.com \
  -p successUserCode \
  -t 67e7eae6-62b0-4500-9eff-87459f63fc66 \
  -f ./sample-config/authentication-config/${authentication_config_file}

sleep 1
done

#identity-verification-config
echo "identity-verification-config"

identity_verification_config_files=(
  identity-verification-investment.json
  identity-verification-continuous-customer-due-diligence.json
)

for identity_verification_config_file in "${identity_verification_config_files[@]}"; do
  echo "🔧 Registering: $(basename "$identity_verification_config_file")"

./sample-config/register-identity-verification-config.sh \
  -u ito.ichiro@gmail.com \
  -p successUserCode \
  -t 67e7eae6-62b0-4500-9eff-87459f63fc66 \
  -f ./sample-config/identity/${identity_verification_config_file}

sleep 1

done