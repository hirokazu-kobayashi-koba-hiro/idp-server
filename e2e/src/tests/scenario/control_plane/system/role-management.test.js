import { describe, expect, it, test } from "@jest/globals";
import { deletion, get, patchWithJson, putWithJson, postWithJson } from "../../../../lib/http";
import { backendUrl, adminServerConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { generateRandomString } from "../../../../lib/util";

describe("role management api", () => {

  describe("success pattern", () => {

    it("crud", async () => {

      const tokenResponse = await requestToken({
        endpoint: adminServerConfig.tokenEndpoint,
        grantType: "password",
        username: adminServerConfig.oauth.username,
        password: adminServerConfig.oauth.password,
        scope: adminServerConfig.adminClient.scope,
        clientId: adminServerConfig.adminClient.clientId,
        clientSecret: adminServerConfig.adminClient.clientSecret
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const permissionListResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/permissions?limit=100`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(permissionListResponse.status).toBe(200);

      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": "organization-role:"+  generateRandomString(20),
          "description": "test",
          "permissions": [
            permissionListResponse.data.list[0].id,
            permissionListResponse.data.list[2].id,
          ]
        }
      });
      console.log(JSON.stringify(createResponse.data, null, 2));
      expect(createResponse.status).toBe(201);
      expect(createResponse.data).toHaveProperty("result");

      const roleId = createResponse.data.result.id;

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles?id=${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");

      const detailResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data).toHaveProperty("id");

      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": "role:"+  generateRandomString(20),
          "description": "test-3",
          "permissions": [
            permissionListResponse.data.list[2].id,
            permissionListResponse.data.list[3].id,
          ]
        }
      });
      console.log(updateResponse.data);
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data).toHaveProperty("result");
      expect(updateResponse.data).toHaveProperty("diff");

      // ロール名変更しないPUT（description/permissionsのみ変更）
      const updateNoNameChangeResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": createResponse.data.result.name, // 既存名
          "description": "test-2",
          "permissions": [
            permissionListResponse.data.list[1].id,
            permissionListResponse.data.list[2].id,
          ]
        }
      });
      expect(updateNoNameChangeResponse.status).toBe(200);
      expect(updateNoNameChangeResponse.data).toHaveProperty("result");
      expect(updateNoNameChangeResponse.data.result.name).toBe(createResponse.data.result.name);
      expect(updateNoNameChangeResponse.data.result.description).toBe("test-2");
      expect(updateNoNameChangeResponse.data.result.permissions).toEqual(
        expect.arrayContaining([
          permissionListResponse.data.list[1].id,
          permissionListResponse.data.list[2].id,
        ])
      );

      //TODO implement api
      // const removeResponse = await putWithJson({
      //   url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles/${roleId}/permissions:remove`,
      //   headers: {
      //     Authorization: `Bearer ${accessToken}`,
      //   },
      //   body: {
      //     "permissions": [
      //       permissionListResponse.data.list[2].id,
      //       permissionListResponse.data.list[3].id,
      //     ]
      //   }
      // });
      // console.log(removeResponse.data);
      // expect(removeResponse.status).toBe(200);
      // expect(removeResponse.data).toHaveProperty("result");
      // expect(removeResponse.data).toHaveProperty("diff");

      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log(deleteResponse.data);
      expect(deleteResponse.status).toBe(204);
    });


  });


});