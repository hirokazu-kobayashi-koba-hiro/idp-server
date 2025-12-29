import { describe, expect, it } from "@jest/globals";

import { requestToken } from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig, unsupportedServerConfig, unsupportedClient } from "../testConfig";

describe("The OAuth 2.0 Authorization Framework client credentials", () => {
  it("success pattern", async () => {
    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "client_credentials",
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
  });

  describe("4.4.2.  Access Token Request", () => {
    it("grant_type REQUIRED.  Value MUST be set to \"client_credentials\".", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
    });

    it("scope OPTIONAL.  The scope of the access request as described by Section 3.3.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_scope");
    });

    it("The client MUST authenticate with the authorization server as described in Section 3.2.1.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(401);
      expect(tokenResponse.data.error).toEqual("invalid_client");
    });
  });

  describe("4.4.3.  Access Token Response", () => {
    it("If the access token request is valid and authorized, the authorization server issues an access token as described in Section 5.1. ", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data.token_type).toEqual("Bearer");
      expect(tokenResponse.data).toHaveProperty("expires_in");
      expect(tokenResponse.data).toHaveProperty("scope");
    });

    it("A refresh token SHOULD NOT be included.  If the request failed client authentication or is invalid, the authorization server returns an error response as described in Section 5.2.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data.token_type).toEqual("Bearer");
      expect(tokenResponse.data).toHaveProperty("expires_in");
      expect(tokenResponse.data).toHaveProperty("scope");
      expect(tokenResponse.data).not.toHaveProperty("refresh_token");
    });
  });

  describe("5.2. Error Response", () => {
    it("unsupported_grant_type The authorization grant type is not supported by the authorization server.", async () => {
      const tokenResponse = await requestToken({
        endpoint: unsupportedServerConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: unsupportedClient.scope,
        clientId: unsupportedClient.clientId,
        clientSecret: unsupportedClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("unsupported_grant_type");
      expect(tokenResponse.data.error_description).toEqual(
        "this request grant_type is client_credentials, but authorization server does not support"
      );
    });

    it("unauthorized_client The authenticated client is not authorized to use this authorization grant type.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: unsupportedClient.scope,
        clientId: unsupportedClient.clientId,
        clientSecret: unsupportedClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("unauthorized_client");
      expect(tokenResponse.data.error_description).toEqual(
        "this request grant_type is client_credentials, but client does not support"
      );
    });
  });
});
