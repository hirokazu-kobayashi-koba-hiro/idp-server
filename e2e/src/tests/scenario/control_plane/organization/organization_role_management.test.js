import { describe, expect, it } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { generateRandomString } from "../../../../lib/util";
import { v4 as uuidv4 } from "uuid";

describe("organization role management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";

  describe("success pattern", () => {
    it("create tenant, manage roles, and delete tenant", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Step 1: Create a new tenant within the organization
      const timestamp = Date.now();
      const newTenantId = uuidv4();
      const createTenantResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": newTenantId,
            "name": `Role Test Tenant ${timestamp}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for role management",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": `http://localhost:8080/${newTenantId}`,
            "authorization_endpoint": `http://localhost:8080/${newTenantId}/v1/authorizations`,
            "token_endpoint": `http://localhost:8080/${newTenantId}/v1/tokens`,
            "userinfo_endpoint": `http://localhost:8080/${newTenantId}/v1/userinfo`,
            "jwks_uri": `http://localhost:8080/${newTenantId}/v1/jwks`,
            "scopes_supported": ["openid", "profile", "email"],
            "response_types_supported": ["code"],
            "response_modes_supported": ["query", "fragment"],
            "subject_types_supported": ["public"],
            "grant_types_supported": ["authorization_code", "refresh_token"],
            "token_endpoint_auth_methods_supported": ["client_secret_post", "client_secret_basic"]
          }
        }
      });
      console.log("Create tenant response:", createTenantResponse.data);
      expect(createTenantResponse.status).toBe(201);
      expect(createTenantResponse.data).toHaveProperty("result");
      console.log("✅ New tenant created successfully");

      // Step 2: Create permissions first (required for role creation)
      const permission1Name = `org-permission-create-${generateRandomString(8)}`;
      const permission2Name = `org-permission-read-${generateRandomString(8)}`;
      const permission3Name = `org-permission-update-${generateRandomString(8)}`;

      const createPermission1Response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": permission1Name,
          "description": "Test organization create permission"
        }
      });
      expect(createPermission1Response.status).toBe(201);
      const permission1Id = createPermission1Response.data.result.id;

      const createPermission2Response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": permission2Name,
          "description": "Test organization read permission"
        }
      });
      expect(createPermission2Response.status).toBe(201);
      const permission2Id = createPermission2Response.data.result.id;

      const createPermission3Response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": permission3Name,
          "description": "Test organization update permission"
        }
      });
      expect(createPermission3Response.status).toBe(201);
      const permission3Id = createPermission3Response.data.result.id;
      console.log("✅ Test permissions created successfully");

      // Step 3: Create a new role within the organization/tenant
      const roleName = `org-role-${generateRandomString(10)}`;
      const createRoleResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": roleName,
          "description": "Test organization role",
          "permissions": [
            permission1Id,
            permission2Id,
            permission3Id
          ]
        }
      });
      console.log("Create role response:", createRoleResponse.data);
      expect(createRoleResponse.status).toBe(201);
      expect(createRoleResponse.data).toHaveProperty("result");

      const roleId = createRoleResponse.data.result.id;
      expect(createRoleResponse.data.result.name).toBe(roleName);
      const permissionNames = createRoleResponse.data.result.permissions.map(p => p.name);
      expect(permissionNames).toContain(permission1Name);
      console.log("✅ Role created successfully");

      // Step 3: Test dry run for role creation
      const dryRunCreateResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": `dry-run-role-${generateRandomString(10)}`,
          "description": "Dry run test role",
          "permissions": [permission2Id]
        }
      });
      console.log("Dry run create response:", dryRunCreateResponse.data);
      expect(dryRunCreateResponse.status).toBe(200);
      expect(dryRunCreateResponse.data).toHaveProperty("dry_run", true);
      console.log("✅ Dry run role creation verified");

      // Step 4: List roles within the organization/tenant
      const listRolesResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List roles response:", listRolesResponse.data);
      expect(listRolesResponse.status).toBe(200);
      expect(listRolesResponse.data).toHaveProperty("list");
      expect(listRolesResponse.data.list.length).toBeGreaterThan(0);

      const foundRole = listRolesResponse.data.list.find(role => role.id === roleId);
      expect(foundRole).toBeDefined();
      expect(foundRole.name).toBe(roleName);
      console.log("✅ Role found in list");

      // Step 5: Get specific role details
      const getRoleResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Get role response:", getRoleResponse.data);
      expect(getRoleResponse.status).toBe(200);
      expect(getRoleResponse.data).toHaveProperty("id", roleId);
      expect(getRoleResponse.data).toHaveProperty("name", roleName);
      const getRolePermissionNames = getRoleResponse.data.permissions.map(p => p.name);
      expect(getRolePermissionNames).toContain(permission1Name);
      console.log("✅ Role details retrieved successfully");

      // Step 6: Update role with dry run
      const dryRunUpdateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": `${roleName}-updated`,
          "description": "Updated test organization role",
          "permissions": [
            permission1Id,
            permission2Id,
            permission3Id
          ]
        }
      });
      console.log("Dry run update response:", dryRunUpdateResponse.data);
      expect(dryRunUpdateResponse.status).toBe(200);
      expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);
      console.log("✅ Dry run role update verified");

      // Step 7: Actually update the role (replace permission1 and permission3 with only permission2)
      const updateRoleResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": `${roleName}-updated`,
          "description": "Updated test organization role",
          "permissions": [
            permission2Id
          ]
        }
      });
      console.log("Update role response:", updateRoleResponse.data);
      expect(updateRoleResponse.status).toBe(200);
      expect(updateRoleResponse.data).toHaveProperty("result");
      expect(updateRoleResponse.data.result.name).toBe(`${roleName}-updated`);
      const updatePermissionNames = updateRoleResponse.data.result.permissions.map(p => p.name);
      expect(updatePermissionNames).toContain(permission2Name);
      console.log("✅ Role updated successfully");

      // Step 8: Verify the update - permissions should be completely replaced (not accumulated)
      const verifyUpdateResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyUpdateResponse.status).toBe(200);
      expect(verifyUpdateResponse.data.name).toBe(`${roleName}-updated`);
      const verifyPermissionNames = verifyUpdateResponse.data.permissions.map(p => p.name);

      // Issue #944: Verify permissions are completely replaced, not accumulated
      expect(verifyUpdateResponse.data.permissions).toHaveLength(1);
      expect(verifyPermissionNames).toContain(permission2Name);
      // Original permissions should be removed
      expect(verifyPermissionNames).not.toContain(permission1Name);
      expect(verifyPermissionNames).not.toContain(permission3Name);
      console.log("✅ Role update verified - permissions completely replaced (Issue #944)");

      // Step 8-2: Update with multiple permissions
      const updateMultipleResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": `${roleName}-updated`,
          "description": "Updated test organization role",
          "permissions": [
            permission1Id,
            permission3Id
          ]
        }
      });
      expect(updateMultipleResponse.status).toBe(200);

      // Verify permission2 is removed and permission1, permission3 are added
      const verifyMultipleUpdateResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyMultipleUpdateResponse.status).toBe(200);
      expect(verifyMultipleUpdateResponse.data.permissions).toHaveLength(2);
      const verifyMultiplePermissionNames = verifyMultipleUpdateResponse.data.permissions.map(p => p.name);
      expect(verifyMultiplePermissionNames).toContain(permission1Name);
      expect(verifyMultiplePermissionNames).toContain(permission3Name);
      expect(verifyMultiplePermissionNames).not.toContain(permission2Name);
      console.log("✅ Multiple permissions update verified - complete replacement (Issue #944)");

      // Step 8-3: Update with empty permissions array (should remove all permissions)
      const updateEmptyResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": `${roleName}-updated`,
          "description": "Role with no permissions",
          "permissions": []
        }
      });
      expect(updateEmptyResponse.status).toBe(200);

      // Verify all permissions are removed
      const verifyEmptyUpdateResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyEmptyUpdateResponse.status).toBe(200);
      expect(verifyEmptyUpdateResponse.data.permissions).toHaveLength(0);
      console.log("✅ Empty permissions update verified - all permissions removed (Issue #944)");

      // Step 9: Test dry run for role deletion
      const dryRunDeleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Dry run delete response:", dryRunDeleteResponse.status);
      expect(dryRunDeleteResponse.status).toBe(200);
      console.log("✅ Dry run role deletion verified");

      // Step 10: Delete the role
      const deleteRoleResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete role response:", deleteRoleResponse.status);
      expect(deleteRoleResponse.status).toBe(204);
      console.log("✅ Role deleted successfully");

      // Step 11: Verify role is deleted
      const verifyDeleteResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyDeleteResponse.status).toBe(404);
      console.log("✅ Role deletion verified");

      // Step 12: Clean up - Delete the test tenant
      const deleteTenantResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete tenant response:", deleteTenantResponse.status);
      expect(deleteTenantResponse.status).toBe(204);
      console.log("✅ Test tenant deleted successfully");

      console.log("🎉 All organization role management tests passed!");
    });

    it("GET response can be used directly as UPDATE request body (roundtrip)", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Step 1: Create a new tenant for this test
      const newTenantId = uuidv4();
      const createTenantResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": newTenantId,
            "name": `Role Roundtrip Test Tenant ${Date.now()}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for role roundtrip test",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": `http://localhost:8080/${newTenantId}`,
            "authorization_endpoint": `http://localhost:8080/${newTenantId}/v1/authorizations`,
            "token_endpoint": `http://localhost:8080/${newTenantId}/v1/tokens`,
            "userinfo_endpoint": `http://localhost:8080/${newTenantId}/v1/userinfo`,
            "jwks_uri": `http://localhost:8080/${newTenantId}/v1/jwks`,
            "scopes_supported": ["openid", "profile", "email"],
            "response_types_supported": ["code"],
            "response_modes_supported": ["query", "fragment"],
            "subject_types_supported": ["public"],
            "grant_types_supported": ["authorization_code", "refresh_token"],
            "token_endpoint_auth_methods_supported": ["client_secret_post", "client_secret_basic"]
          }
        }
      });
      expect(createTenantResponse.status).toBe(201);

      // Step 2: Create a permission (required for role creation)
      const permissionName = `roundtrip-permission-${generateRandomString(8)}`;
      const createPermissionResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": permissionName,
          "description": "Permission for role roundtrip test"
        }
      });
      expect(createPermissionResponse.status).toBe(201);
      const permissionId = createPermissionResponse.data.result.id;

      // Step 3: Create a role
      const roleName = `roundtrip-role-${generateRandomString(10)}`;
      const createRoleResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": roleName,
          "description": "Test role for roundtrip verification",
          "permissions": [permissionId]
        }
      });
      expect(createRoleResponse.status).toBe(201);
      const roleId = createRoleResponse.data.result.id;

      // Step 4: GET the role
      const getBeforeResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(getBeforeResponse.status).toBe(200);
      const originalRole = getBeforeResponse.data;

      // Step 5: PUT the GET response body, filtering out null, empty string, and server-managed fields
      const serverManagedFields = ["created_at", "updated_at"];
      const filterNullAndEmpty = (obj) => {
        if (obj === null || obj === undefined) return undefined;
        if (typeof obj !== "object" || Array.isArray(obj)) return obj;
        const filtered = {};
        for (const [key, value] of Object.entries(obj)) {
          if (value === null || value === "" || serverManagedFields.includes(key)) continue;
          const filteredValue = filterNullAndEmpty(value);
          if (filteredValue !== undefined) filtered[key] = filteredValue;
        }
        return Object.keys(filtered).length > 0 ? filtered : undefined;
      };
      const filteredRole = filterNullAndEmpty(originalRole);
      // Workaround: GET returns permissions as object array [{name, description, id}],
      // but PUT expects string array of permission IDs ["id1", "id2"]
      if (Array.isArray(filteredRole.permissions) && filteredRole.permissions.length > 0 && typeof filteredRole.permissions[0] === "object") {
        filteredRole.permissions = filteredRole.permissions.map(p => p.id);
      }
      console.log("GET role response:", JSON.stringify(originalRole, null, 2));
      console.log("Filtered role body:", JSON.stringify(filteredRole, null, 2));

      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: filteredRole
      });
      console.log("PUT response status:", updateResponse.status);
      console.log("PUT response body:", JSON.stringify(updateResponse.data, null, 2));
      expect(updateResponse.status).toBe(200);

      // Step 6: GET again and verify nothing changed
      const getAfterResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(getAfterResponse.status).toBe(200);
      const updatedRole = getAfterResponse.data;

      // Compare original and updated configurations (excluding updated_at which changes on every PUT)
      const { updated_at: _origUpdatedAt, ...originalComparable } = originalRole;
      const { updated_at: _updatedUpdatedAt, ...updatedComparable } = updatedRole;
      expect(JSON.stringify(updatedComparable)).toBe(JSON.stringify(originalComparable));

      // Cleanup: delete role, then tenant (permissions are deleted with tenant)
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/roles/${roleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
    });

    it("error scenarios", async () => {
      // Get OAuth token
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const existingTenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

      // Test 1: Try with invalid authorization
      const unauthorizedResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/roles`,
        headers: {
          Authorization: "Bearer invalid-token"
        }
      });
      console.log("Unauthorized response:", unauthorizedResponse.status);
      expect(unauthorizedResponse.status).toBe(401);
      console.log("✅ Invalid token returns 401");

      // Test 2: Try with invalid organization ID
      const invalidOrgId = "00000000-0000-0000-0000-000000000000";
      const invalidOrgResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${invalidOrgId}/tenants/${existingTenantId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid organization response:", invalidOrgResponse.status);
      expect(invalidOrgResponse.status).toBe(404);
      console.log("✅ Invalid organization returns 404");

      // Test 3: Try with invalid tenant ID
      const invalidTenantId = "00000000-0000-0000-0000-000000000000";
      const invalidTenantResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${invalidTenantId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid tenant response:", invalidTenantResponse.status);
      expect(invalidTenantResponse.status).toBe(404);
      console.log("✅ Invalid tenant returns 404");

      // Test 4: Try to create role with invalid data (empty name)
      const invalidCreateResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/roles`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": "",
          "description": "Invalid role",
          "permissions": []
        }
      });
      console.log("Invalid create response:", invalidCreateResponse.data);
      expect(invalidCreateResponse.status).toBe(400);
      console.log("✅ Empty role name returns 400");

      // Test 5: Try to get non-existent role
      const nonExistentRoleId = "00000000-0000-0000-0000-000000000000";
      const notFoundResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/roles/${nonExistentRoleId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Not found response:", notFoundResponse.status);
      expect(notFoundResponse.status).toBe(404);
      console.log("✅ Non-existent role returns 404");
    });
  });
});