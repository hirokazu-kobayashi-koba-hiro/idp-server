import { describe, expect, it } from "@jest/globals";

import {
  completeBackchannelAuthentications,
  requestBackchannelAuthentications,
  requestToken,
} from "./api/oauthClient";
import {
  clientSecretBasicClient,
  clientSecretJwtClient,
  clientSecretPostClient,
  serverConfig,
} from "./testConfig";
import { createBasicAuthHeader, sleep } from "./lib/util";
import { createClientAssertion } from "./lib/oauth";

describe("OpenID Connect Client-Initiated Backchannel Authentication Flow - Core 1.0", () => {
  const ciba = serverConfig.ciba;

  describe("10.3. Push Callback", () => {

    it("10.3.1. Successful Token Delivery", async () => {
      const clientAssertion = createClientAssertion({
        client: clientSecretJwtClient,
        issuer: serverConfig.issuer,
      });
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretJwtClient.clientId,
          scope: "openid profile phone email" + clientSecretJwtClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientAssertion,
          clientAssertionType: "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
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


    });

  });
});
