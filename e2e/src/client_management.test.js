import { describe, expect, it } from "@jest/globals";
import { get } from "./lib/http";
import { requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";

describe("client management api", () => {

  describe("success pattern", () => {
    it("get", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${serverConfig.issuer}/api/v1/management/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(response.data);
      expect(response.status).toBe(200);
    });
  });
});