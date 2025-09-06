import { describe, expect, it, test } from "@jest/globals";
import { deletion, get, patchWithJson, putWithJson, postWithJson } from "../../../lib/http";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../testConfig";
import { requestToken } from "../../../api/oauthClient";
import { generateRandomString } from "../../../lib/util";

describe("role management api", () => {

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
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": "organization-role:"+  generateRandomString(20),
          "description": "test",
          "permissions": [
            "organization:create",
            "organization:read",
            "organization:update",
            "organization:delete"
          ]
        }
      });
      console.log(JSON.stringify(createResponse.data, null, 2));
      expect(createResponse.status).toBe(201);
      expect(createResponse.data).toHaveProperty("result");

      const roleId = createResponse.data.result.id;

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/roles?id=${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");

      const detailResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data).toHaveProperty("id");

      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": "role:"+  generateRandomString(20),
          "description": "test-3",
          "permissions": [
            "organization:create",
            "organization:read",
            "organization:update",
            "organization:delete",
            "tenant:create",
            "tenant:read",
            "tenant:update",
            "tenant:delete"
          ]
        }
      });
      console.log(updateResponse.data);
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data).toHaveProperty("result");
      expect(updateResponse.data).toHaveProperty("diff");

      const removeResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/roles/${roleId}/permissions:remove`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "permissions": [
            "tenant:create",
            "tenant:read",
            "tenant:update",
            "tenant:delete"
          ]
        }
      });
      console.log(removeResponse.data);
      expect(removeResponse.status).toBe(200);
      expect(removeResponse.data).toHaveProperty("result");
      expect(removeResponse.data).toHaveProperty("diff");

      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log(deleteResponse.data);
      expect(deleteResponse.status).toBe(204);
    });


  });


});