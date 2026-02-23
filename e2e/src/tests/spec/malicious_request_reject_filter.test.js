import { describe, expect, it } from "@jest/globals";

import { requestBackchannelAuthentications, requestToken } from "../../api/oauthClient";
import { postWithJson } from "../../lib/http";
import { clientSecretPostClient, serverConfig } from "../testConfig";

describe("MaliciousRequestRejectFilter", () => {
  const ciba = serverConfig.ciba;

  describe("CIBA backchannel authentication request", () => {
    it("should reject binding_message containing null byte with 400", async () => {
      const response = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        bindingMessage: "hello\x00world",
        userCode: ciba.userCode,
        loginHint: ciba.loginHint,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(response.data);
      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
      expect(response.data.error_description).toBe("Invalid parameter value");
    });

    it("should reject scope containing null byte with 400", async () => {
      const response = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid\x00profile",
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: ciba.loginHint,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(response.data);
      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
      expect(response.data.error_description).toBe("Invalid parameter value");
    });

    it("should reject login_hint containing null byte with 400", async () => {
      const response = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: "user\x00@example.com",
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(response.data);
      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
      expect(response.data.error_description).toBe("Invalid parameter value");
    });
  });

  describe("Token request", () => {
    it("should reject grant_type containing null byte with 400", async () => {
      const response = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code\x00",
        code: "test-code",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        redirectUri: "https://example.com/callback",
      });
      console.log(response.data);
      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
      expect(response.data.error_description).toBe("Invalid parameter value");
    });
  });

  describe("Oversized request body", () => {
    it("should reject JSON body exceeding 10MB with 413", async () => {
      const oversizedBody = { data: "x".repeat(11 * 1024 * 1024) };
      const response = await postWithJson({
        url: serverConfig.tokenEndpoint,
        body: oversizedBody,
      });
      console.log(response.data);
      expect(response.status).toBe(413);
      expect(response.data.error).toBe("invalid_request");
      expect(response.data.error_description).toBe("Request body too large");
    });
  });
});
