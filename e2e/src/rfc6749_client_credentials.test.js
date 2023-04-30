import { describe, expect, it } from "@jest/globals";

import { requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";

describe("The OAuth 2.0 Authorization Framework client credentials", () => {
  it("success pattern", async () => {

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "client_credentials",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);

  });



});
