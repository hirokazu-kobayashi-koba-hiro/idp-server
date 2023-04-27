import { describe, expect, it } from "@jest/globals";

import { requestToken } from "./api/oauthClient";
import { clientSecretJwtClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";
import { createJwt, createJwtWithPrivateKey, decodeJwt } from "./lib/jose";
import { createClientAssertion } from "./lib/oauth";

describe("OpenID Connect Core 1.0 incorporating errata set 1 request object", () => {
  it("success pattern", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email " + clientSecretJwtClient.scope,
      redirectUri: clientSecretJwtClient.redirectUri,
      clientId: clientSecretJwtClient.clientId,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const clientAssertion = createClientAssertion({
      client: clientSecretJwtClient,
      issuer: serverConfig.issuer,
    });

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretJwtClient.redirectUri,
      clientId: clientSecretJwtClient.clientId,
      clientAssertion,
      clientAssertionType:
        "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");
    const decodedIdToken = decodeJwt(tokenResponse.data.id_token);
    console.log(decodedIdToken);
  });
});
