import { describe, expect, it } from "@jest/globals";

import { requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";

describe("The OAuth 2.0 Authorization Framework resource owner password credentials", () => {
  const oauth = serverConfig.oauth;

  it("success pattern", async () => {
    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      scope: clientSecretPostClient.scope,
      username: oauth.username,
      password: oauth.password,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
  });
});
