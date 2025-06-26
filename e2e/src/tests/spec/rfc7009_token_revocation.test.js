import { describe, expect, it } from "@jest/globals";

import { inspectToken, requestToken, revokeToken } from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";

describe("OAuth 2.0 Token Revocation", () => {
  it("success pattern", async () => {
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
    });
    console.log(reIntrospectionResponse.data);
    expect(reIntrospectionResponse.status).toBe(200);
    expect(reIntrospectionResponse.data.active).toBe(false);
  });
});
