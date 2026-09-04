import { describe, expect, it } from "@jest/globals";

import { requestToken } from "../../api/oauthClient";
import { clientSecretPostClient, publicClientCredentialsClient, serverConfig, unsupportedServerConfig, unsupportedClient } from "../testConfig";

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

  describe("4.4.  Client Credentials Grant", () => {
    it("The client credentials grant type MUST only be used by confidential clients.", async () => {
      // publicClientCredentials is registered with token_endpoint_auth_method=none AND
      // grant_types=[client_credentials], so the unauthorized_client check passes and the
      // confidential-client guard is what has to reject the request. A public client presents no
      // credential, so accepting it would issue an access token to an unauthenticated caller.
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: publicClientCredentialsClient.scope,
        clientId: publicClientCredentialsClient.clientId,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(401);
      expect(tokenResponse.data.error).toEqual("invalid_client");
      // TokenRequestErrorHandler maps both ClientUnAuthorizedException (this guard) and
      // ClientConfigurationNotFoundException (client not registered) to 401 invalid_client, so the
      // status and error code alone cannot tell them apart -- an unregistered fixture would make
      // this test pass even with the guard removed. Assert on the description to pin the cause.
      expect(tokenResponse.data.error_description).toContain("confidential client");
    });
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
