import { describe, expect, it } from "@jest/globals";

import { getAuthorizations, requestToken } from "../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "../oauth";

describe("The OAuth 2.0 Authorization Framework", () => {
  it("success pattern", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: clientSecretPostClient.scope,
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

  describe("3.1.  Authorization Endpoint", () => {
    it("The authorization endpoint is used to interact with the resource owner and obtain an authorization grant.  The authorization server MUST first verify the identity of the resource owner.", async () => {});

    it("The authorization server MUST support the use of the HTTP \"GET\" method [RFC2616] for the authorization endpoint and MAY support the use of the \"POST\" method as well.", async () => {
      const response = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "account",
        state: "aiueo",
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(response.data);
      expect(response.status).toBe(200);
    });
  });

  describe("4.1.  Authorization Code Grant", () => {
    it("4.1.1.  Authorization Request response_type REQUIRED", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        scope: clientSecretPostClient.scope,
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_request");
    });
  });
});
