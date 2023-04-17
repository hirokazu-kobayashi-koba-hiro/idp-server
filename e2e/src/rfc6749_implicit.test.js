import { describe, expect, it } from "@jest/globals";

import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";

describe("The OAuth 2.0 Authorization Framework implicit", () => {
  it("success pattern", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "token",
      state: "aiueo",
      scope: clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.accessToken).not.toBeNull();
    expect(authorizationResponse.tokenType).toEqual("Bearer");
    expect(authorizationResponse.expiresIn).not.toBeNull();
  });

});
