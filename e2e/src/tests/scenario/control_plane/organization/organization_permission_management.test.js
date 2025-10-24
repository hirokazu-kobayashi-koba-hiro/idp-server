import { describe, expect, it } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { generateRandomString } from "../../../../lib/util";
import { v4 as uuidv4 } from "uuid";

describe("organization permission management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";

  describe("success pattern", () => {
    it("create tenant, manage permissions, and delete tenant", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
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
            "name": `Permission Test Tenant ${timestamp}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for permission management",
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

      // Step 2: Create a new permission within the organization/tenant
      const permissionName = `org-permission-${generateRandomString(10)}`;
      const createPermissionResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": permissionName,
          "description": "Test organization permission"
        }
      });
      console.log("Create permission response:", createPermissionResponse.data);
      expect(createPermissionResponse.status).toBe(201);
      expect(createPermissionResponse.data).toHaveProperty("result");

      const permissionId = createPermissionResponse.data.result.id;
      expect(createPermissionResponse.data.result.name).toBe(permissionName);
      expect(createPermissionResponse.data.result.description).toBe("Test organization permission");
      console.log("✅ Permission created successfully");

      // Step 3: Test dry run for permission creation
      const dryRunCreateResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": `dry-run-permission-${generateRandomString(10)}`,
          "description": "Dry run test permission"
        }
      });
      console.log("Dry run create response:", dryRunCreateResponse.data);
      expect(dryRunCreateResponse.status).toBe(200);
      expect(dryRunCreateResponse.data).toHaveProperty("dry_run", true);
      console.log("✅ Dry run permission creation verified");

      // Step 4: List permissions within the organization/tenant
      const listPermissionsResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List permissions response:", listPermissionsResponse.data);
      expect(listPermissionsResponse.status).toBe(200);
      expect(listPermissionsResponse.data).toHaveProperty("list");
      expect(listPermissionsResponse.data.list.length).toBeGreaterThan(0);

      const foundPermission = listPermissionsResponse.data.list.find(permission => permission.id === permissionId);
      expect(foundPermission).toBeDefined();
      expect(foundPermission.name).toBe(permissionName);
      console.log("✅ Permission found in list");

      // Step 5: Get specific permission details
      const getPermissionResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions/${permissionId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Get permission response:", getPermissionResponse.data);
      expect(getPermissionResponse.status).toBe(200);
      expect(getPermissionResponse.data).toHaveProperty("id", permissionId);
      expect(getPermissionResponse.data).toHaveProperty("name", permissionName);
      expect(getPermissionResponse.data).toHaveProperty("description", "Test organization permission");
      console.log("✅ Permission details retrieved successfully");

      // Step 6: Update permission with dry run
      const updatedDescription = "Updated test organization permission";
      const dryRunUpdateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions/${permissionId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": `${permissionName}-updated`,
          "description": updatedDescription
        }
      });
      console.log("Dry run update response:", dryRunUpdateResponse.data);
      expect(dryRunUpdateResponse.status).toBe(200);
      expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);
      console.log("✅ Dry run permission update verified");

      // Step 7: Actually update the permission
      const updatePermissionResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions/${permissionId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": `${permissionName}-updated`,
          "description": updatedDescription
        }
      });
      console.log("Update permission response:", updatePermissionResponse.data);
      expect(updatePermissionResponse.status).toBe(200);
      expect(updatePermissionResponse.data).toHaveProperty("result");
      expect(updatePermissionResponse.data.result.name).toBe(`${permissionName}-updated`);
      expect(updatePermissionResponse.data.result.description).toBe(updatedDescription);
      console.log("✅ Permission updated successfully");

      // Step 8: Verify the update
      const verifyUpdateResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions/${permissionId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyUpdateResponse.status).toBe(200);
      expect(verifyUpdateResponse.data.name).toBe(`${permissionName}-updated`);
      expect(verifyUpdateResponse.data.description).toBe(updatedDescription);
      console.log("✅ Permission update verified");

      // Step 9: Test dry run for permission deletion
      const dryRunDeleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions/${permissionId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Dry run delete response:", dryRunDeleteResponse.status);
      expect(dryRunDeleteResponse.status).toBe(200);
      console.log("✅ Dry run permission deletion verified");

      // Step 10: Delete the permission
      const deletePermissionResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions/${permissionId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete permission response:", deletePermissionResponse.status);
      expect(deletePermissionResponse.status).toBe(204);
      console.log("✅ Permission deleted successfully");

      // Step 11: Verify permission is deleted
      const verifyDeleteResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/permissions/${permissionId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyDeleteResponse.status).toBe(404);
      console.log("✅ Permission deletion verified");

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

      console.log("🎉 All organization permission management tests passed!");
    });

    it("error scenarios", async () => {
      // Get OAuth token
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/permissions`,
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
        url: `${backendUrl}/v1/management/organizations/${invalidOrgId}/tenants/${existingTenantId}/permissions`,
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${invalidTenantId}/permissions`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid tenant response:", invalidTenantResponse.status);
      expect(invalidTenantResponse.status).toBe(404);
      console.log("✅ Invalid tenant returns 404");

      // Test 4: Try to create permission with invalid data (empty name)
      const invalidCreateResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/permissions`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": "",
          "description": "Invalid permission"
        }
      });
      console.log("Invalid create response:", invalidCreateResponse.status);
      expect(invalidCreateResponse.status).toBe(400);
      console.log("✅ Empty permission name returns 400");

      // Test 5: Try to get non-existent permission
      const nonExistentPermissionId = "00000000-0000-0000-0000-000000000000";
      const notFoundResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/permissions/${nonExistentPermissionId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Not found response:", notFoundResponse.status);
      expect(notFoundResponse.status).toBe(404);
      console.log("✅ Non-existent permission returns 404");

      // Test 6: Try to create permission with duplicate name
      const duplicatePermissionName = `duplicate-permission-${generateRandomString(5)}`;

      // First creation - should succeed
      const firstCreateResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/permissions`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": duplicatePermissionName,
          "description": "First permission"
        }
      });
      expect(firstCreateResponse.status).toBe(201);
      const firstPermissionId = firstCreateResponse.data.result.id;
      console.log("✅ First permission created successfully");

      // Second creation with same name - should fail
      const duplicateCreateResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/permissions`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": duplicatePermissionName,
          "description": "Duplicate permission"
        }
      });
      console.log("Duplicate create response:", duplicateCreateResponse.status);
      expect(duplicateCreateResponse.status).toBe(400);
      console.log("✅ Duplicate permission name returns 400");

      // Clean up - delete the created permission
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/permissions/${firstPermissionId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("✅ Cleanup permission deleted");
    });
  });
});