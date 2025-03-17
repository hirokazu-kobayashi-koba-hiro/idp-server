#!/bin/zsh

#server
curl -X POST "${IDP_SERVER_DOMAIN}api/v1/admin/server/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/server.json

#client
curl -X POST "${IDP_SERVER_DOMAIN}api/v1/admin/client/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretBasic.json

curl -X POST "${IDP_SERVER_DOMAIN}api/v1/admin/client/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretBasic2.json

curl -X POST "${IDP_SERVER_DOMAIN}api/v1/admin/client/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretJwt.json

curl -X POST "${IDP_SERVER_DOMAIN}api/v1/admin/client/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretPost.json


curl -X POST "${IDP_SERVER_DOMAIN}api/v1/admin/client/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretPostWithIdTokenEnc.json

curl -X POST "${IDP_SERVER_DOMAIN}api/v1/admin/client/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/privateKeyJwt.json

curl -X POST "${IDP_SERVER_DOMAIN}api/v1/admin/client/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/selfSignedTlsClientAuth.json

curl -X POST "${IDP_SERVER_DOMAIN}api/v1/admin/client/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/unsupportedClient.json

curl -X POST "${IDP_SERVER_DOMAIN}api/v1/admin/client/registration" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/publicClient.json


#curl -X POST "${IDP_SERVER_DOMAIN}94d8598e-f238-4150-85c2-c4accf515784/api/v1/admin/server/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./app/src/main/resources/config/sample/unsupportedServer.json

#curl -X POST "${IDP_SERVER_DOMAIN}94d8598e-f238-4150-85c2-c4accf515784/api/v1/admin/client/registration" \
#-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
#-H "Content-Type:application/json" \
#--data @./app/src/main/resources/config/sample/clients/unsupportedServerUnsupportedClient.json
