#!/bin/zsh

#server
curl -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/management/tenants" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/server.json

curl -X POST "http://localhost:8080/94d8598e-f238-4150-85c2-c4accf515784/api/v1/management/tenants" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/unsupportedServer.json

#client
curl -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretBasic.json

curl -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretBasic2.json

curl -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretJwt.json

curl -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretPost.json


curl -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/clientSecretPostWithIdTokenEnc.json

curl -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/privateKeyJwt.json

curl -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/selfSignedTlsClientAuth.json

curl -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/unsupportedClient.json

curl -X POST "http://localhost:8080/94d8598e-f238-4150-85c2-c4accf515784/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/unsupportedServerUnsupportedClient.json

curl -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/client/registration" \
-H "Content-Type:application/json" \
--data @./app/src/main/resources/config/sample/clients/publicClient.json
