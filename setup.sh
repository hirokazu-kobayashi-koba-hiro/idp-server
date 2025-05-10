#!/bin/zsh

#admin
curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./sample-config/admin.json | jq

##client
#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/clients/clientSecretBasic.json
#
#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/clients/clientSecretBasic2.json
#
#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/clients/clientSecretJwt.json
#
#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/clients/clientSecretPost.json
#
#
#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/clients/clientSecretPostWithIdTokenEnc.json
#
#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/clients/privateKeyJwt.json
#
#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/clients/selfSignedTlsClientAuth.json
#
#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/clients/unsupportedClient.json
#
#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/clients/publicClient.json


#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/server/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/unsupportedServer.json
#
#curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/tenants/d2849b5f-4e95-41c1-a82d-0197224085ec/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./sample-config/clients/unsupportedServerUnsupportedClient.json
