import { describe, expect, it } from "@jest/globals";

import { requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth/signin";
import { matchWithUSASCII } from "./lib/util";

describe("The OAuth 2.0 Authorization Framework refresh token", () => {

  it("success pattern", async () => {
    const { refreshToken } = await getRefreshToken({
      server: serverConfig,
      client: clientSecretPostClient,
    });

    const refreshTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      refreshToken: refreshToken,
      grantType: "refresh_token",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    console.log(refreshTokenResponse.data);
    expect(refreshTokenResponse.status).toBe(200);
  });

  describe("6.  Refreshing an Access Token", () => {
    it("grant_type REQUIRED.  Value MUST be set to \"refresh_token\".", async () => {
      const { refreshToken } = await getRefreshToken({
        server: serverConfig,
        client: clientSecretPostClient,
      });

      const refreshTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(refreshTokenResponse.data);
      expect(refreshTokenResponse.status).toBe(200);
    });

    it("refresh_token REQUIRED.  The refresh token issued to the client.", async () => {

      const refreshTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(refreshTokenResponse.data);
      expect(refreshTokenResponse.status).toBe(400);
      expect(refreshTokenResponse.data.error).toEqual("invalid_request");
      expect(refreshTokenResponse.data.error_description).toEqual("token request does not contains refresh_token, refresh_token grant must contains refresh_token");
    });

    it("refresh_token REQUIRED.  The refresh token issued to the client.", async () => {

      const refreshTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(refreshTokenResponse.data);
      expect(refreshTokenResponse.status).toBe(400);
      expect(refreshTokenResponse.data.error).toEqual("invalid_request");
      expect(refreshTokenResponse.data.error_description).toEqual("token request does not contains refresh_token, refresh_token grant must contains refresh_token");
    });

    it("scope OPTIONAL.  The scope of the access request as described by Section 3.3.  The requested scope MUST NOT include any scope not originally granted by the resource owner, and if omitted is treated as equal to the scope originally granted by the resource owner.", async () => {
      const { refreshToken } = await getRefreshToken({
        server: serverConfig,
        client: clientSecretPostClient,
      });

      const refreshTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(refreshTokenResponse.data);
      expect(refreshTokenResponse.status).toBe(200);
    });

    it("The authorization server MUST: require client authentication for confidential clients or for any client that was issued client credentials (or with other authentication requirements),", async () => {
      const { refreshToken } = await getRefreshToken({
        server: serverConfig,
        client: clientSecretPostClient,
      });

      const refreshTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
      });

      console.log(refreshTokenResponse.data);
      expect(refreshTokenResponse.status).toBe(401);
      expect(refreshTokenResponse.data.error).toEqual("invalid_client");
    });

    it("The authorization server MUST: validate the refresh token.", async () => {
      const { refreshToken } = await getRefreshToken({
        server: serverConfig,
        client: clientSecretPostClient,
      });

      const refreshTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(refreshTokenResponse.data);
      expect(refreshTokenResponse.status).toBe(200);

      const reRefreshTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(reRefreshTokenResponse.data);
      expect(reRefreshTokenResponse.status).toBe(400);
      expect(reRefreshTokenResponse.data.error).toEqual("invalid_grant");
      expect(reRefreshTokenResponse.data.error_description).toContain("refresh token does not exists");
    });

    it("If valid and authorized, the authorization server issues an access token as described in Section 5.1.  If the request failed", async () => {
      const { refreshToken } = await getRefreshToken({
        server: serverConfig,
        client: clientSecretPostClient,
      });

      const refreshTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(refreshTokenResponse.data);
      expect(refreshTokenResponse.status).toBe(200);
      expect(refreshTokenResponse.data).toHaveProperty("access_token");
      expect(refreshTokenResponse.data.token_type).toEqual("Bearer");
      expect(refreshTokenResponse.data).toHaveProperty("expires_in");
      expect(refreshTokenResponse.data).toHaveProperty("scope");
      expect(refreshTokenResponse.data).toHaveProperty("refresh_token");
    });

    it("If valid and authorized, the authorization server issues an access token as described in Section 5.1.  If the request failed", async () => {
      const refreshTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken: "refreshToken",
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(refreshTokenResponse.data);
      expect(refreshTokenResponse.status).toBe(400);
      expect(refreshTokenResponse.data.error).toEqual("invalid_grant");
      expect(matchWithUSASCII(refreshTokenResponse.data.error)).toBe(true);
      expect(refreshTokenResponse.data.error_description).toContain("refresh token does not exists");
      expect(matchWithUSASCII(refreshTokenResponse.data.error_description)).toBe(true);
    });

    it("If valid and authorized, the authorization server issues an access token as described in Section 5.1.  If the request failed", async () => {
      const { refreshToken, scope } = await getRefreshToken({
        server: serverConfig,
        client: clientSecretPostClient,
      });

      const refreshTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(refreshTokenResponse.data);
      expect(refreshTokenResponse.status).toBe(200);
      expect(refreshTokenResponse.data).toHaveProperty("access_token");
      expect(refreshTokenResponse.data.token_type).toEqual("Bearer");
      expect(refreshTokenResponse.data).toHaveProperty("expires_in");
      expect(refreshTokenResponse.data).toHaveProperty("scope");
      expect(refreshTokenResponse.data).toHaveProperty("refresh_token");
      expect(refreshTokenResponse.data.scope).toBe(scope);
    });
  });
});

const getRefreshToken = async ({ server, client, }) => {
  const { authorizationResponse } = await requestAuthorizations({
    endpoint: server.authorizationEndpoint,
    clientId: client.clientId,
    responseType: "code",
    state: "aiueo",
    scope: client.scope,
    redirectUri: client.redirectUri,
  });
  console.log(authorizationResponse);
  expect(authorizationResponse.code).not.toBeNull();

  const tokenResponse = await requestToken({
    endpoint: server.tokenEndpoint,
    code: authorizationResponse.code,
    grantType: "authorization_code",
    redirectUri: client.redirectUri,
    clientId: client.clientId,
    clientSecret: client.clientSecret,
  });
  console.log(tokenResponse.data);
  expect(tokenResponse.status).toBe(200);
  return {
    refreshToken: tokenResponse.data.refresh_token,
    scope: tokenResponse.data.scope,
  };
};
