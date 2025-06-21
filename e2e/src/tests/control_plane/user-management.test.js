import { describe, expect, it, test } from "@jest/globals";
import { deletion, get, patchWithJson, putWithJson, postWithJson } from "../../lib/http";
import { backendUrl, clientSecretPostClient, serverConfig } from "../testConfig";
import { requestToken } from "../../api/oauthClient";

describe("audit log management api", () => {

  describe("success pattern", () => {

    it("crud", async () => {

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "provider_id": "idp-server",
          "name": "test",
          "email": "test",
          "raw_password": "test@01234"
        }
      });
      console.log(createResponse.data);
      expect(createResponse.status).toBe(201);
      expect(createResponse.data).toHaveProperty("result");

      const userId = createResponse.data.result.sub;

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users?user_id=${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");

      const detailResponse = await get({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data).toHaveProperty("sub");

      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "provider_id": "idp-server",
          "name": "test-3",
          "email": "test-3"
        }
      });
      console.log(updateResponse.data);
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data).toHaveProperty("result");

      const patchResponse = await patchWithJson({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": "test-2",
        }
      });
      console.log(patchResponse.data);
      expect(patchResponse.status).toBe(200);
      expect(patchResponse.data).toHaveProperty("result");


      const updatePasswordResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users/${userId}/password`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "provider_id": "idp-server",
          "name": "test",
          "email": "test",
          "raw_password": "test@012355"
        }
      });
      console.log(updatePasswordResponse.data);
      expect(updatePasswordResponse.status).toBe(200);
      expect(updatePasswordResponse.data).toHaveProperty("result");

      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log(deleteResponse.data);
      expect(deleteResponse.status).toBe(204);
    });

  });

});