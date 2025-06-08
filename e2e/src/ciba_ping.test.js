import { describe, expect, it } from "@jest/globals";

import {
  completeBackchannelAuthentications,
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "./api/oauthClient";
import {
  clientSecretBasicClient,
  serverConfig,
} from "./testConfig";
import { createBasicAuthHeader } from "./lib/util";
import { get } from "./lib/http";


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

      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {},
      });
      console.log(authenticationTransactionResponse.data);

      authenticationTransactionResponse = await get({
        url: serverConfig.authenticationEndpoint + `?attributes.auth_req_id=${backchannelAuthenticationResponse.data.auth_req_id}`,
      });

      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
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
