import { describe, expect, it } from "@jest/globals";

import { requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";

describe("The OAuth 2.0 Authorization Framework refresh token", () => {
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

    const refreshTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      refreshToken: tokenResponse.data.refresh_token,
      grantType: "refresh_token",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    console.log(refreshTokenResponse.data);
    expect(refreshTokenResponse.status).toBe(200);
  });
});
