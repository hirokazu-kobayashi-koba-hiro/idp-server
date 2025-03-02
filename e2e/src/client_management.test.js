import { describe, expect, it } from "@jest/globals";
import { get } from "./lib/http";
import { requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig, backendUrl } from "./testConfig";

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

      ///api/v1/management/tenants/{tenant-id}/clients
      const response = await get({
        url: `${backendUrl}/api/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(response.data);
      expect(response.status).toBe(200);
    });
  });
});