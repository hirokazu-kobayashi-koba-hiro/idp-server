import { describe, expect, it, test } from "@jest/globals";
import { deletion, get, patchWithJson, putWithJson, postWithJson } from "../../../../lib/http";
import { backendUrl, adminServerConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { generateRandomNumber } from "../../../../lib/util";

describe("user management api", () => {

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

      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "provider_id": "idp-server",
          "name": "test",
          "email": generateRandomNumber(10) +"t@mail.com",
          "raw_password": "test@01234"
        }
      });
      console.log(createResponse.data);
      expect(createResponse.status).toBe(201);
      expect(createResponse.data).toHaveProperty("result");

      const userId = createResponse.data.result.sub;

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users?user_id=${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");

      const detailResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data).toHaveProperty("sub");

      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
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
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
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
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}/password`,
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
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log(deleteResponse.data);
      expect(deleteResponse.status).toBe(204);
    });

    it("role and permission updates", async () => {
      const tokenResponse = await requestToken({
        endpoint: adminServerConfig.tokenEndpoint,
        grantType: "password",
        username: adminServerConfig.oauth.username,
        password: adminServerConfig.oauth.password,
        scope: adminServerConfig.adminClient.scope,
        clientId: adminServerConfig.adminClient.clientId,
        clientSecret: adminServerConfig.adminClient.clientSecret
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Create a test user with unique email
      const timestamp = Date.now();
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "provider_id": "idp-server",
          "name": `role-test-user-${timestamp}`,
          "email": `roletest-${timestamp}@example.com`,
          "raw_password": "test@01234"
        }
      });
      console.log(createResponse.data);
      expect(createResponse.status).toBe(201);
      const userId = createResponse.data.result.sub;

      // Get existing roles from role management API
      const rolesListResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Available roles:", rolesListResponse.data);
      expect(rolesListResponse.status).toBe(200);
      
      // Use existing roles if available, otherwise create test roles
      let rolesToAssign = [];
      if (rolesListResponse.data.list && rolesListResponse.data.list.length > 0) {
        // Take first available role
        const firstRole = rolesListResponse.data.list[0];
        rolesToAssign = [{
          "role_id": firstRole.id,
          "role_name": firstRole.name
        }];
      } else {
        // Fallback to test role structure (this might need adjustment)
        rolesToAssign = [{
          "role_id": "550e8400-e29b-41d4-a716-446655440000",
          "role_name": "Test Role"
        }];
      }

      console.log(rolesToAssign);

      // Test role updates
      const updateRolesResponse = await patchWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "roles": rolesToAssign
        }
      });
      console.log("updateRoles response:", updateRolesResponse.data);
      expect(updateRolesResponse.status).toBe(200);
      expect(updateRolesResponse.data.result).toHaveProperty("roles");

      // Verify roles were added
      const verifyRolesAdded = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyRolesAdded.status).toBe(200);
      expect(verifyRolesAdded.data.roles.length).toBeGreaterThan(0);

      // Test removing all roles (DELETE INSERT with empty roles)
      const removeAllRolesResponse = await patchWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "roles": []
        }
      });
      console.log("removeAllRoles response:", removeAllRolesResponse.data);
      expect(removeAllRolesResponse.status).toBe(200);
      // Empty roles are omitted from response (User.toMap() behavior)
      const resultRoles = removeAllRolesResponse.data.result.roles || [];
      expect(resultRoles).toHaveLength(0);
      // Verify diff shows roles and permissions were removed
      expect(removeAllRolesResponse.data).toHaveProperty("diff");
      expect(removeAllRolesResponse.data.diff).toHaveProperty("roles");
      expect(removeAllRolesResponse.data.diff).toHaveProperty("permissions");
      console.log("Diff:", JSON.stringify(removeAllRolesResponse.data.diff, null, 2));

      // Verify roles are actually removed from database
      const verifyRolesRemoved = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyRolesRemoved.status).toBe(200);
      // Empty roles are omitted from response
      const dbRoles = verifyRolesRemoved.data.roles || [];
      expect(dbRoles).toHaveLength(0);
      // Permissions should also be empty when roles are removed
      const dbPermissions = verifyRolesRemoved.data.permissions || [];
      expect(dbPermissions).toHaveLength(0);
      console.log("✅ Roles and permissions successfully removed from user (system level)");

      // Re-add roles for subsequent tests
      await patchWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "roles": rolesToAssign
        }
      });

      // Test tenant assignments update
      const updateTenantAssignmentsResponse = await patchWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}/tenant-assignments`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "current_tenant_id": adminServerConfig.tenantId,
          "assigned_tenants": [adminServerConfig.tenantId]
        }
      });
      console.log("updateTenantAssignments response:", updateTenantAssignmentsResponse.data);
      expect(updateTenantAssignmentsResponse.status).toBe(200);
      expect(updateTenantAssignmentsResponse.data.result).toHaveProperty("current_tenant_id");

      // Test organization assignments update with valid UUIDs
      const validOrgId = "9eb8eb8c-2615-4604-809f-5cae1c00a462";
      const validSubOrgId = "9eb8eb8c-2615-4604-809f-5cae1c00a462";
      const updateOrgAssignmentsResponse = await patchWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}/organization-assignments`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "current_organization_id": validOrgId,
          "assigned_organizations": [validOrgId, validSubOrgId]
        }
      });
      console.log("updateOrgAssignments response:", updateOrgAssignmentsResponse.data);
      expect(updateOrgAssignmentsResponse.status).toBe(200);
      expect(updateOrgAssignmentsResponse.data.result).toHaveProperty("current_organization_id");

      // Verify all updates by getting the user
      const verifyResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Final user state:", JSON.stringify(verifyResponse.data, null, 2));
      expect(verifyResponse.status).toBe(200);
      expect(verifyResponse.data).toHaveProperty("roles");
      expect(verifyResponse.data).toHaveProperty("permissions");
      expect(verifyResponse.data).toHaveProperty("current_tenant_id");
      expect(verifyResponse.data).toHaveProperty("assigned_tenants");

      // Clean up - delete test user
      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      expect(deleteResponse.status).toBe(204);
    });


    describe("find list query pattern", () => {
      const validCases = [
        ["valid user_id", "user_id", "c9cfa3c3-8534-45a6-8905-7a1f9cd3fd18"],
        ["valid from", "from", "2025-06-20T19:51:39.901577"],
        ["valid to", "to", "2025-06-21T19:51:39.901577"],
        ["valid external_user_id", "external_user_id", "external-user-001"],
        ["valid provider_id", "provider_id", "google"],
        ["valid external_user_id", "external_user_id", "google-user-123"],
        ["valid email", "email", "user@example.com"],
        ["valid status", "status", "REGISTERED"],
        ["valid name", "name", "ito.ichiro"],
        ["valid given_name", "given_name", "ichiro"],
        ["valid family_name", "family_name", "ito"],
        ["valid middle_name", "middle_name", "shinji"],
        ["valid nickname", "nickname", "itchy"],
        ["valid preferred_username", "preferred_username", "ichiro-ito"],
        ["valid phone_number", "phone_number", "+819012345678"],
        ["valid role", "role", "admin"],
        ["valid permission", "permission", "user.read"],
        ["user_id", "user_id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
        ["external_user_id", "external_user_id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
        ["provider_id", "provider_id", "idp-server"],
        ["external_user_id", "external_user_id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
        ["email", "email", "ito.ichiro@gmail.com"],
        ["name", "name", "ito ichiro"],
        ["given_name", "given_name", "ichiro"],
        ["family_name", "family_name", "ito"],
        ["middle_name", "middle_name", "mac"],
        ["nickname", "nickname", "ito"],
        ["preferred_username", "preferred_username", "ichiro"],
        ["phone_number", "phone_number", "09012345678"],
        ["role", "role", "Administrator"],
      ];
      test.each(validCases)("success case:%s param: %s, value: %s", async (description, param, value) => {
        console.log(description, param, value);

        const tokenResponse = await requestToken({
          endpoint: adminServerConfig.tokenEndpoint,
          grantType: "password",
          username: adminServerConfig.oauth.username,
          password: adminServerConfig.oauth.password,
          scope: adminServerConfig.adminClient.scope,
          clientId: adminServerConfig.adminClient.clientId,
          clientSecret: adminServerConfig.adminClient.clientSecret
        });

        expect(tokenResponse.status).toBe(200);
        const accessToken = tokenResponse.data.access_token;

        const listResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users?${param}=${encodeURIComponent(value)}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });

        console.log(JSON.stringify(listResponse.data));
        expect(listResponse.status).toBe(200);
        expect(Array.isArray(listResponse.data.list)).toBe(true);
      });
    });


  });

  describe("error case", () => {

    const errorCases = [
      ["user_id", "user_id", "123"],
      ["from", "from", "2025-06-20C19:51:39.901577"],
      ["to", "to", "2025-06-20-19:51:39.901577"],
    ];
    test.each(errorCases)("error case:%s param: %s, value: %s", async (description, param, value) => {
      console.log(description, param, value);

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

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users?${param}=${value}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(400);

    });

  });

});