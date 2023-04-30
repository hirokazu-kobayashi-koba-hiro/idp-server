import { describe, expect, it } from "@jest/globals";

import { getJwks, requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";
import { createJwtWithPrivateKey, verifyAndDecodeIdToken, verifySignature } from "./lib/jose";

describe("OpenID Connect Core 1.0 incorporating errata set 1 request object", () => {
  it("success pattern", async () => {
    const requestObject = createJwtWithPrivateKey({
      payload: {
        client_id: clientSecretPostClient.clientId,
        response_type: "code",
        state: "aiueo",
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        redirect_uri: clientSecretPostClient.redirectUri,
      },
      privateKey: clientSecretPostClient.requestKey,
    });

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      request: requestObject,
      clientId: clientSecretPostClient.clientId,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");

    const jwksResponse = await getJwks({endpoint: serverConfig.jwksEndpoint});
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeIdToken({
      idToken: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);
  });
});
