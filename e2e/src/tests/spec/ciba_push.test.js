import { describe, expect, it } from "@jest/globals";

import {
  completeBackchannelAuthentications,
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications
} from "../../api/oauthClient";
import {
  clientSecretJwtClient,
  serverConfig,
} from "../testConfig";
import { createClientAssertion } from "../../lib/oauth";
import { get } from "../../lib/http";

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
          clientAssertionType:
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log(authenticationTransactionResponse.data);
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
    });
  });
});
