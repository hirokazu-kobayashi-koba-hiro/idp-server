#!/bin/zsh

#server
curl -X POST "http://localhost:8080/api/v1/server/configurations" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/server.json

curl -X POST "http://localhost:8080/api/v1/server/configurations" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/unsupportedServer.json

#client
curl -X POST "http://localhost:8080/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretBasic.json

curl -X POST "http://localhost:8080/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretBasic2.json

curl -X POST "http://localhost:8080/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretJwt.json

curl -X POST "http://localhost:8080/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretPost.json


curl -X POST "http://localhost:8080/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretPostWithIdTokenEnc.json

curl -X POST "http://localhost:8080/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/privateKeyJwt.json

curl -X POST "http://localhost:8080/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/selfSignedTlsClientAuth.json

curl -X POST "http://localhost:8080/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/unsupportedClient.json

curl -X POST "http://localhost:8080/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/unsupportedServerUnsupportedClient.json

curl -X POST "http://localhost:8080/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/publicClient.json
