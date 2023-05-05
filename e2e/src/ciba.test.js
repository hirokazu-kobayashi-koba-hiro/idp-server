import { describe, expect, it } from "@jest/globals";

import { requestBackchannelAuthentications, requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";

describe("OpenID Connect Client-Initiated Backchannel Authentication Flow - Core 1.0", () => {
  it("success pattern", async () => {
    const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
      endpoint: serverConfig.backchannelAuthenticationEndpoint,
      clientId: clientSecretPostClient.clientId,
      scope: "openid profile phone email" + clientSecretPostClient.scope,
      bindingMessage: "999",
      userCode: "successUserCode",
      loginHint: "123",
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(backchannelAuthenticationResponse.data);
    expect(backchannelAuthenticationResponse.status).toBe(200);

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: backchannelAuthenticationResponse.data.auth_req_id,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
  });
});
