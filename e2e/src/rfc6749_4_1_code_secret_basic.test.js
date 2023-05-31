import { describe, expect, it } from "@jest/globals";

import { getAuthorizations, requestToken } from "./api/oauthClient";
import { clientSecretBasicClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";
import { createBasicAuthHeader } from "./lib/util";

describe("The OAuth 2.0 Authorization Framework code client_secret_basic", () => {
  it("success pattern", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretBasicClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: clientSecretBasicClient.scope,
      redirectUri: clientSecretBasicClient.redirectUri,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const basicAuth = createBasicAuthHeader({
      username: clientSecretBasicClient.clientId,
      password: clientSecretBasicClient.clientSecret,
    });
    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretBasicClient.redirectUri,
      basicAuth,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
  });

  describe("3.1.  Authorization Endpoint", () => {
    it("The authorization endpoint is used to interact with the resource owner and obtain an authorization grant.  The authorization server MUST first verify the identity of the resource owner.", async () => {});

    it('The authorization server MUST support the use of the HTTP "GET" method [RFC2616] for the authorization endpoint and MAY support the use of the "POST" method as well.', async () => {
      const response = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "code",
        scope: "account",
        state: "aiueo",
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(response.data);
      expect(response.status).toBe(200);
    });
  });

  describe("4.1.  Authorization Code Grant", () => {
    it("4.1.1.  Authorization Request response_type REQUIRED", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        redirectUri: clientSecretBasicClient.redirectUri,
        scope: clientSecretBasicClient.scope,
      });
      console.log(authorizationResponse);

      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual(
        "response type is required in authorization request"
      );
    });

    it("4.1.1.  Authorization Request client_id REQUIRED", async () => {
      const { status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        redirectUri: clientSecretBasicClient.redirectUri,
        scope: clientSecretBasicClient.scope,
      });
      console.log(error);
      expect(status).toBe(400);

      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toEqual(
        "authorization request must contains client_id"
      );
    });

    it("4.1.1.  Authorization Request scope", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        redirectUri: clientSecretBasicClient.redirectUri,
        clientId: clientSecretBasicClient.clientId,
        responseType: "code",
        scope: "test bank",
      });
      console.log(authorizationResponse);

      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual(
        "authorization request does not contains valid scope (test bank)"
      );
    });
  });
});
