import { describe, expect, it, xit } from "@jest/globals";

import { requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";

describe("OpenID Connect Core 1.0 incorporating errata set 1", () => {
  xit("success pattern", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: "openid " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
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
  });

  describe("3.1.2.1.  Authentication Request", () => {
    it("response_type REQUIRED. OAuth 2.0 Response Type value that determines the authorization processing flow to be used, including what parameters are returned from the endpoints used. When using the Authorization Code Flow, this value is code.", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual("response type is required on authorization request");
    });

    it ("client_id REQUIRED. OAuth 2.0 Client Identifier valid at the Authorization Server.", async () => {
      const { status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
      });
      console.log(error);
      expect(status).toBe(400);

      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toEqual("authorization request must contains client_id");
    });


  });

});
