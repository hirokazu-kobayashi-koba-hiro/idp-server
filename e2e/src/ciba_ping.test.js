import { describe, expect, it } from "@jest/globals";

import {
  completeBackchannelAuthentications,
  requestBackchannelAuthentications,
  requestToken,
} from "./api/oauthClient";
import {
  clientSecretBasicClient,
  serverConfig,
} from "./testConfig";
import { createBasicAuthHeader } from "./lib/util";


describe("OpenID Connect Client-Initiated Backchannel Authentication Flow - Core 1.0", () => {
  const ciba = serverConfig.ciba;

  describe("10.2. Ping Callback", () => {
    it("success pattern", async () => {
      const basicAuth = createBasicAuthHeader({
        username: clientSecretBasicClient.clientId,
        password: clientSecretBasicClient.clientSecret,
      });
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          scope: "openid profile phone email" + clientSecretBasicClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          basicAuth,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      const completeResponse = await completeBackchannelAuthentications({
        endpoint:
          serverConfig.backchannelAuthenticationAutomatedCompleteEndpoint,
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        action: "allow",
      });
      expect(completeResponse.status).toBe(200);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        basicAuth,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
    });
  });
});
