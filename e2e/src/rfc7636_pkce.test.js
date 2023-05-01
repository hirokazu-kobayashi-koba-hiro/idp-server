import { describe, expect, it } from "@jest/globals";

import { requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";
import { calculateCodeChallengeWithS256 } from "./lib/oauth";

describe("The OAuth 2.0 Authorization Framework code", () => {
  it("success pattern plain", async () => {
    const codeVerifier = "aiueo12345678";
    const codeChallenge = codeVerifier;
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      codeChallenge,
      codeChallengeMethod: "plain",
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
      codeVerifier,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);

  });

  it("success pattern s256", async () => {
    const codeVerifier = "aiueo12345678";
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      codeChallenge,
      codeChallengeMethod: "s256",
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
      codeVerifier,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);

  });

});
