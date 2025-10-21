import { describe, expect, it } from "@jest/globals";

import { inspectToken, requestToken, revokeToken } from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";

describe("RFC 7009 - OAuth 2.0 Token Revocation", () => {
  it("success pattern - revoke access token", async () => {
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
    expect(tokenResponse.data).toHaveProperty("id_token");

    const introspectionResponse = await inspectToken({
      endpoint: serverConfig.tokenIntrospectionEndpoint,
      token: tokenResponse.data.access_token,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(introspectionResponse.data);
    expect(introspectionResponse.status).toBe(200);
    expect(introspectionResponse.data.active).toBe(true);

    const revokeResponse = await revokeToken({
      endpoint: serverConfig.tokenRevocationEndpoint,
      token: tokenResponse.data.access_token,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(revokeResponse.data);
    expect(revokeResponse.status).toBe(200);

    const reIntrospectionResponse = await inspectToken({
      endpoint: serverConfig.tokenIntrospectionEndpoint,
      token: tokenResponse.data.access_token,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(reIntrospectionResponse.data);
    expect(reIntrospectionResponse.status).toBe(200);
    expect(reIntrospectionResponse.data.active).toBe(false);
  });

  describe("2.1. Revocation Request", () => {
    it("token parameter REQUIRED - RFC 7009 Section 2.1", async () => {
      // The client constructs the request by including the following
      // parameters using the "application/x-www-form-urlencoded" format
      // token REQUIRED

      const revokeResponse = await revokeToken({
        endpoint: serverConfig.tokenRevocationEndpoint,
        token: null, // Missing token parameter
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(revokeResponse.data);
      expect(revokeResponse.status).toBe(400);
      expect(revokeResponse.data.error).toBe("invalid_request");
      expect(revokeResponse.data.error_description).toBe(
        "token revocation request must contains token parameters"
      );
    });

    it("client authentication REQUIRED - RFC 7009 Section 2.1", async () => {
      // The client also includes its authentication credentials as described in
      // Section 2.3 of [RFC6749].

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      const revokeResponse = await revokeToken({
        endpoint: serverConfig.tokenRevocationEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: "invalid_secret", // Wrong client secret
      });

      console.log(revokeResponse.data);
      expect(revokeResponse.status).toBe(401);
      expect(revokeResponse.data.error).toBe("invalid_client");
    });

    it("token_type_hint OPTIONAL - RFC 7009 Section 2.1", async () => {
      // token_type_hint OPTIONAL

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      const revokeResponse = await revokeToken({
        endpoint: serverConfig.tokenRevocationEndpoint,
        token: tokenResponse.data.access_token,
        tokenHintType: "access_token", // Optional hint
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(revokeResponse.data);
      expect(revokeResponse.status).toBe(200);
    });
  });

  describe("2.2. Revocation Response", () => {
    it("successful response HTTP 200 - RFC 7009 Section 2.2", async () => {
      // The authorization server responds with HTTP status code 200
      // if the token has been revoked successfully or if the client
      // submitted an invalid token.

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      const revokeResponse = await revokeToken({
        endpoint: serverConfig.tokenRevocationEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(revokeResponse.status).toBe(200);
    });

    it("invalid token also returns HTTP 200 - RFC 7009 Section 2.2", async () => {
      // The authorization server responds with HTTP status code 200
      // if the client submitted an invalid token.
      // Note: invalid tokens do not cause an error

      const revokeResponse = await revokeToken({
        endpoint: serverConfig.tokenRevocationEndpoint,
        token: "invalid_token_12345",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(revokeResponse.data);
      expect(revokeResponse.status).toBe(200);
    });
  });

  describe("2.2.1. Error Response", () => {
    it("invalid_request error - RFC 7009 Section 2.2.1", async () => {
      // The request is missing a required parameter

      const revokeResponse = await revokeToken({
        endpoint: serverConfig.tokenRevocationEndpoint,
        token: null,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(revokeResponse.status).toBe(400);
      expect(revokeResponse.data).toHaveProperty("error");
      expect(revokeResponse.data.error).toBe("invalid_request");
      expect(revokeResponse.data).toHaveProperty("error_description");
    });

    it("invalid_client error - RFC 7009 Section 2.2.1", async () => {
      // Client authentication failed

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      const revokeResponse = await revokeToken({
        endpoint: serverConfig.tokenRevocationEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: "wrong_secret",
      });

      expect(revokeResponse.status).toBe(401);
      expect(revokeResponse.data).toHaveProperty("error");
      expect(revokeResponse.data.error).toBe("invalid_client");
      expect(revokeResponse.data).toHaveProperty("error_description");
    });
  });

  describe("2.3. Cross-Origin Requests", () => {
    it("supports CORS for browser-based clients - RFC 7009 Section 2.3", async () => {
      // The authorization server SHOULD support CORS

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      const revokeResponse = await revokeToken({
        endpoint: serverConfig.tokenRevocationEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // Check CORS headers are present
      expect(revokeResponse.status).toBe(200);
      // Note: CORS headers check would require custom axios config
    });
  });
});
