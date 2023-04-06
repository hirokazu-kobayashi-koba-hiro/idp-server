import { describe, expect, it } from "@jest/globals";

import { authorize, getAuthorizations, requestToken } from "../api/oauthClient";
import { convertToAuthorizationResponse } from "../lib/util";

describe("The OAuth 2.0 Authorization Framework", () => {
  const authorizationEndpoint = "http://localhost:8080/123/api/debug/v1/authorizations";
  const authorizeEndpoint = "http://localhost:8080/123/api/debug/v1/authorizations/{id}/authorize";
  const tokenEndpoint = "http://localhost:8080/123/api/v1/tokens";

  describe("3.1.  Authorization Endpoint", () => {
    it("success pattern",async () => {
      const response = await getAuthorizations({
        endpoint: authorizationEndpoint,
        clientId: "s6BhdRkqt3",
        responseType: "code",
        state: "aiueo",
        redirectUri: "https://client.example.org/callback"
      });
      console.log(response.data);
      expect(response.status).toBe(200);

      const authorizeResponse = await authorize({
        endpoint: authorizeEndpoint,
        id: response.data.id,
      });
      console.log(authorizeResponse.data);
      expect(authorizeResponse.status).toBe(200);

      const authorizationResponse = convertToAuthorizationResponse(authorizeResponse.data.redirect_uri);
      console.log(authorizationResponse);

      const tokenResponse = await requestToken({
        endpoint: tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: "https://client.example.org/callback",
        clientId: "s6BhdRkqt3",
        clientSecret: "cf136dc3c1fc93f31185e5885805d",
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
    });

    it("The authorization endpoint is used to interact with the resource owner and obtain an authorization grant.  The authorization server MUST first verify the identity of the resource owner.", async () => {});

    it("The authorization server MUST support the use of the HTTP \"GET\" method [RFC2616] for the authorization endpoint and MAY support the use of the \"POST\" method as well.", async () => {
      const response = await getAuthorizations({
        endpoint: authorizationEndpoint,
        clientId: "s6BhdRkqt3",
        responseType: "code",
        state: "aiueo",
        redirectUri: "https://client.example.org/callback"
      });
      console.log(response.data);
      expect(response.status).toBe(200);
    });
  });


});
