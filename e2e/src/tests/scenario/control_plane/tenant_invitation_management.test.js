import { describe, expect, it } from "@jest/globals";
import { get, post } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { clientSecretPostClient, serverConfig, backendUrl } from "../../testConfig";

describe("tenant invitation management api", () => {

  describe("success pattern", () => {
    it("invitation", async () => {
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

      const response = await post({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/invitations`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json;charset=UTF-8",
        },
        body: {
          "email": "test@gmail.com",
          "role_id": "c6df7aaa-ab0f-4c31-839d-49b6874de144",
          "role_name": "administrator",
        }
      });

      console.log(response.data);
      expect(response.status).toBe(200);
      const { id } = response.data.result;

      const invitationMetaDataResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/invitations/${id}`,
        headers: {
          "Content-Type": "application/json;charset=UTF-8",
        },
      });

      console.log(invitationMetaDataResponse.data);
      expect(invitationMetaDataResponse.status).toBe(200);
    });
  });
});