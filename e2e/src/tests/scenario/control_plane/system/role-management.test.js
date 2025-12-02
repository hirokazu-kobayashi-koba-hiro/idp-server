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
      console.log(JSON.stringify(updateResponse.data, null, 2));
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data).toHaveProperty("result");
      expect(updateResponse.data).toHaveProperty("diff");

      // Verify permissions are completely replaced (not accumulated)
      const afterUpdateDetail = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(afterUpdateDetail.status).toBe(200);
      expect(afterUpdateDetail.data.permissions).toHaveLength(2);
      expect(afterUpdateDetail.data.permissions.map(p => p.id)).toContain(permissionListResponse.data.list[2].id);
      expect(afterUpdateDetail.data.permissions.map(p => p.id)).toContain(permissionListResponse.data.list[3].id);

      // Original permissions should be removed
      expect(afterUpdateDetail.data.permissions.map(p => p.id)).not.toContain(
        permissionListResponse.data.list[0].id
      );

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
      expect(updateNoNameChangeResponse.data.result.permissions[0].id).toEqual(permissionListResponse.data.list[1].id);
      expect(updateNoNameChangeResponse.data.result.permissions[1].id).toEqual(permissionListResponse.data.list[2].id);

      // Test: Update with empty permissions array (should remove all permissions)
      const updateEmptyPermissionsResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": updateNoNameChangeResponse.data.result.name,
          "description": "test-no-permissions",
          "permissions": []
        }
      });
      expect(updateEmptyPermissionsResponse.status).toBe(200);
      expect(updateEmptyPermissionsResponse.data).toHaveProperty("result");

      // Verify all permissions are removed
      const afterEmptyUpdateDetail = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(afterEmptyUpdateDetail.status).toBe(200);
      expect(afterEmptyUpdateDetail.data.permissions).toHaveLength(0);

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