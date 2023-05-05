import { describe, expect, it } from "@jest/globals";

import { completeBackchannelAuthentications, requestBackchannelAuthentications, requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";
import { createJwt, createJwtWithPrivateKey } from "./lib/jose";
import { isNumber, isString } from "./lib/util";

describe("OpenID Connect Client-Initiated Backchannel Authentication Flow - Core 1.0", () => {
  const ciba = serverConfig.ciba;


  describe("11. Token Error Response", () => {
    it("authorization_pending The authorization request is still pending as the end-user hasn't yet been authenticated.", async () => {
      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: ciba.loginHint,
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
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("authorization_pending");
    });
  });

});
