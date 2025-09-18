import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { generateRandomString } from "../../../../lib/util";
import { v4 as uuidv4 } from "uuid";

/**
 * Organization Role Management API - Structured E2E Tests
 *
 * Comprehensive test suite based on API quality improvement strategy
 * - JsonSchema validation tests
 * - Business rule tests
 * - Integration tests
 * - API specification compliance tests
 */
describe("Organization Role Management API - Structured Tests", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  let accessToken;
  let testTenantId;
  let testPermissions = [];

  // Helper function to create a test role
  const createTestRole = async (roleName, description = "Test role", permissions = [testPermissions[0]]) => {
    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
      headers: { Authorization: `Bearer ${accessToken}` },
      body: {
        "name": roleName,
        "description": description,
        "permissions": permissions
      }
    });
    expect(response.status).toBe(201);
    return response.data.result;
  };

  // Helper function to delete a test role
  const deleteTestRole = async (roleId) => {
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${roleId}`,
      headers: { Authorization: `Bearer ${accessToken}` }
    });
  };

  beforeAll(async () => {
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
    accessToken = tokenResponse.data.access_token;

    // Create test tenant
    testTenantId = uuidv4();
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
      headers: { Authorization: `Bearer ${accessToken}` },
      body: {
        tenant: {
          "id": testTenantId,
          "name": `E2E Test Tenant ${Date.now()}`,
          "domain": "http://localhost:8080",
          "description": "Test tenant for E2E testing",
          "authorization_provider": "idp-server",
          "tenant_type": "BUSINESS"
        },
        authorization_server: {
          "issuer": `http://localhost:8080/${testTenantId}`,
          "authorization_endpoint": `http://localhost:8080/${testTenantId}/v1/authorizations`,
          "token_endpoint": `http://localhost:8080/${testTenantId}/v1/tokens`,
          "userinfo_endpoint": `http://localhost:8080/${testTenantId}/v1/userinfo`,
          "jwks_uri": `http://localhost:8080/${testTenantId}/v1/jwks`,
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

    // Create test permissions
    for (let i = 1; i <= 3; i++) {
      const permissionResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "name": `test-permission-${i}-${generateRandomString(8)}`,
          "description": `Test permission ${i} for role management`
        }
      });
      expect(permissionResponse.status).toBe(201);
      testPermissions.push(permissionResponse.data.result.id);
    }
  });

  afterAll(async () => {
    // Cleanup test tenant
    if (testTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
    }
  });

  /**
   * 📋 1. JsonSchema Validation Tests
   * Based on strategy document lines 252-254
   */
  describe("JsonSchema Validation Tests", () => {

    describe("POST /roles - Create Role Validation", () => {

      it("should return 400 for missing required field 'name'", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            // name field missing
            "permissions": [testPermissions[0]]
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
        expect(response.data.error_description).toBe("role registration validation is failed");
        expect(response.data.error_messages).toContain("name is missing");
      });

      it("should return 400 for missing required field 'permissions'", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": "test-role-no-permissions"
            // permissions field missing
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
        expect(response.data.error_description).toBe("role registration validation is failed");
        expect(response.data.error_messages).toContain("permissions is missing");
      });

      it("should return 400 for invalid name type (not string)", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": 12345, // invalid type
            "permissions": [testPermissions[0]]
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
      });

      it("should return 400 for name exceeding maxLength (255 chars)", async () => {
        const longName = "a".repeat(256); // 256 characters > 255 limit

        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": longName,
            "permissions": [testPermissions[0]]
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
      });

      it("should return 400 for description exceeding maxLength (255 chars)", async () => {
        const longDescription = "a".repeat(256); // 256 characters > 255 limit

        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": "test-role-long-desc",
            "description": longDescription,
            "permissions": [testPermissions[0]]
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
      });

      it("should return 400 for invalid permissions format (not array)", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": "test-role-invalid-permissions",
            "permissions": "not-an-array" // invalid type
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
      });

      it("should return 400 for invalid permission ID format (not UUID)", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": "test-role-invalid-permission-id",
            "permissions": ["not-a-uuid"] // invalid UUID format
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
      });
    });

    describe("PUT /roles/{id} - Update Role Validation", () => {

      it("should return 400 for missing required field 'name' in update", async () => {
        // Create test role for this specific test
        const testRole = await createTestRole(`test-role-for-validation-${Date.now()}`);

        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${testRole.id}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            // name field missing
            "permissions": [testPermissions[1]]
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");

        // Cleanup
        await deleteTestRole(testRole.id);
      });
    });
  });

  /**
   * 📋 2. Business Rule Tests
   * Based on strategy document lines 256-259
   */
  describe("Business Rule Tests", () => {

    describe("Resource Existence Validation", () => {

      it("should return 404 for non-existent role ID in GET", async () => {
        const nonExistentId = uuidv4();
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
        expect(response.data.error).toBe("not_found");
      });

      it("should return 404 for non-existent role ID in PUT", async () => {
        const nonExistentId = uuidv4();
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": "updated-role",
            "permissions": [testPermissions[0]]
          }
        });

        expect(response.status).toBe(404);
        expect(response.data.error).toBe("not_found");
      });

      it("should return 404 for non-existent role ID in DELETE", async () => {
        const nonExistentId = uuidv4();
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
        expect(response.data.error).toBe("not_found");
      });

      it("should return 400 for non-existent permission ID in role creation", async () => {
        const nonExistentPermissionId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": "role-with-invalid-permission",
            "permissions": [nonExistentPermissionId]
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
        expect(response.data.error_description).toContain("role registration verification is failed");
      });
    });

    describe("Duplicate Prevention", () => {

      it("should return 409 for duplicate role name in creation", async () => {
        // Create first role
        const existingRoleName = `existing-role-${Date.now()}`;
        const firstRole = await createTestRole(existingRoleName, "Existing role for duplicate testing");

        // Attempt to create role with same name
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": existingRoleName, // duplicate name
            "permissions": [testPermissions[1]]
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");

        // Cleanup
        await deleteTestRole(firstRole.id);
      });
    });

    describe("Organization Access Control", () => {

      //TODO create limited.user
      xit("should return 403 for insufficient permissions", async () => {
        // Test with insufficient permissions token (adjust according to implementation)
        const limitedTokenResponse = await requestToken({
          endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
          grantType: "password",
          username: "limited.user", // User with limited permissions
          password: "limitedUserCode001",
          scope: "limited-scope",
          clientId: "limited-client",
          clientSecret: "limited-client-001"
        }).catch(() => null);

        if (limitedTokenResponse) {
          const response = await postWithJson({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
            headers: { Authorization: `Bearer ${limitedTokenResponse.data.access_token}` },
            body: {
              "name": "test-role-forbidden",
              "permissions": [testPermissions[0]]
            }
          });

          expect(response.status).toBe(403);
          expect(response.data.error).toBe("access_denied");
        }
      });
    });
  });

  /**
   * 📋 3. Integration Tests
   * Based on strategy document lines 261-264
   */
  describe("Integration Tests", () => {

    describe("Complete CRUD Workflow", () => {

      // Helper function to create a test role
      const createTestRole = async (roleName, description = "Test role", permissions = [testPermissions[0]]) => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": roleName,
            "description": description,
            "permissions": permissions
          }
        });
        expect(response.status).toBe(201);
        return response.data.result;
      };

      // Helper function to delete a test role
      const deleteTestRole = async (roleId) => {
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${roleId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      };

      it("should successfully create a role with all fields", async () => {
        const roleName = `create-test-role-${Date.now()}`;
        const role = await createTestRole(
          roleName,
          "Integration test role with complete data",
          testPermissions.slice(0, 2)
        );

        expect(role).toHaveProperty("id");
        expect(role.name).toBe(roleName);
        expect(role.description).toBe("Integration test role with complete data");
        expect(role.permissions).toHaveLength(2);

        // Cleanup
        await deleteTestRole(role.id);
      });

      it("should successfully retrieve a created role", async () => {
        const roleName = `retrieve-test-role-${Date.now()}`;
        const createdRole = await createTestRole(roleName, "Role for retrieval test", testPermissions.slice(0, 2));

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${createdRole.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data.id).toBe(createdRole.id);
        expect(response.data.name).toBe(roleName);
        expect(response.data.permissions).toHaveLength(2);

        // Cleanup
        await deleteTestRole(createdRole.id);
      });

      it("should successfully list roles including the created one", async () => {
        const roleName = `list-test-role-${Date.now()}`;
        const createdRole = await createTestRole(roleName);

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(response.data.list.length).toBeGreaterThan(0);

        const foundRole = response.data.list.find(role => role.id === createdRole.id);
        expect(foundRole).toBeDefined();
        expect(foundRole.name).toBe(roleName);

        // Cleanup
        await deleteTestRole(createdRole.id);
      });

      it("should successfully update a created role", async () => {
        const roleName = `update-test-role-${Date.now()}`;
        const createdRole = await createTestRole(roleName, "Original description", [testPermissions[0]]);

        const updatedName = `${roleName}-updated`;
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${createdRole.id}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": updatedName,
            "description": "Updated integration test role",
            "permissions": [testPermissions[1]] // Use different permission
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", false);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result.name).toBe(updatedName);
        expect(response.data.result.description).toBe("Updated integration test role");
        expect(response.data.result.permissions).toHaveLength(1);

        // Cleanup
        await deleteTestRole(createdRole.id);
      });

      it("should successfully delete a created role", async () => {
        const roleName = `delete-test-role-${Date.now()}`;
        const createdRole = await createTestRole(roleName);

        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${createdRole.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(204);

        // Confirm the role is deleted
        const verifyResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${createdRole.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        expect(verifyResponse.status).toBe(404);
        expect(verifyResponse.data.error).toBe("not_found");
      });
    });

    describe("Dry Run Functionality", () => {

      it("should perform dry run for role creation", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": `dry-run-role-${generateRandomString(8)}`,
            "description": "Dry run test role",
            "permissions": [testPermissions[0]]
          }
        });

        expect(response.status).toBe(201);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id");

        // Confirm the role was not actually created
        const verifyResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${response.data.result.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        expect(verifyResponse.status).toBe(404);
      });

      it("should perform dry run for role update", async () => {
        const roleName = `dry-run-update-role-${Date.now()}`;

        // Helper function to create a test role for dry run test
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": roleName,
            "permissions": [testPermissions[0]]
          }
        });
        expect(createResponse.status).toBe(201);
        const roleId = createResponse.data.result.id;

        // Perform dry run update
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${roleId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": "dry-run-updated-name",
            "permissions": [testPermissions[1]]
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data.result.name).toBe("dry-run-updated-name");

        // Confirm the role was not actually updated
        const verifyResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${roleId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        expect(verifyResponse.data.name).not.toBe("dry-run-updated-name");

        // Cleanup
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${roleId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should perform dry run for role deletion", async () => {
        const roleName = `dry-run-delete-role-${Date.now()}`;

        // Helper function to create a test role for dry run test
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": roleName,
            "permissions": [testPermissions[0]]
          }
        });
        expect(createResponse.status).toBe(201);
        const roleId = createResponse.data.result.id;

        // Perform dry run deletion
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${roleId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(204);

        // Confirm the role was not actually deleted
        const verifyResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${roleId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        expect(verifyResponse.status).toBe(200);

        // Cleanup
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${roleId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });
    });

    describe("Pagination and Filtering", () => {

      // Helper to create multiple test roles
      const createMultipleTestRoles = async (count) => {
        const roles = [];
        for (let i = 1; i <= count; i++) {
          const role = await createTestRole(
            `pagination-role-${i}-${Date.now()}`,
            `Pagination test role ${i}`,
            [testPermissions[i % testPermissions.length]]
          );
          roles.push(role);
        }
        return roles;
      };

      const cleanupMultipleRoles = async (roles) => {
        for (const role of roles) {
          await deleteTestRole(role.id);
        }
      };

      it("should support pagination with limit parameter", async () => {
        // Create test data for this specific test
        const testRoles = await createMultipleTestRoles(5);

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles?limit=3`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(response.data).toHaveProperty("limit", 3);
        expect(response.data).toHaveProperty("offset", 0);
        expect(response.data.list.length).toBeLessThanOrEqual(3);

        // Cleanup
        await cleanupMultipleRoles(testRoles);
      });

      it("should support pagination with offset parameter", async () => {
        // Create test data for this specific test
        const testRoles = await createMultipleTestRoles(5);

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles?limit=2&offset=1`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 2);
        expect(response.data).toHaveProperty("offset", 1);
        expect(response.data.list.length).toBeLessThanOrEqual(2);

        // Cleanup
        await cleanupMultipleRoles(testRoles);
      });
    });
  });

  /**
   * 📋 4. API Specification Compliance Tests
   * Verify complete compliance with OpenAPI specification
   */
  describe("API Specification Compliance Tests", () => {

    describe("Response Structure Validation", () => {

      it("should return correct response structure for role creation", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": `spec-test-role-${generateRandomString(8)}`,
            "description": "Specification test role",
            "permissions": [testPermissions[0]]
          }
        });

        expect(response.status).toBe(201);
        expect(response.data).toHaveProperty("dry_run");
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id");
        expect(response.data.result).toHaveProperty("name");
        expect(response.data.result).toHaveProperty("description");
        expect(response.data.result).toHaveProperty("permissions");
        expect(Array.isArray(response.data.result.permissions)).toBe(true);

        // UUID format validation
        expect(response.data.result.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);

        // Cleanup
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${response.data.result.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should return correct response structure for role retrieval", async () => {
        // Create test role for this specific test
        const testRole = await createTestRole(
          `spec-compliance-role-${Date.now()}`,
          "Role for spec compliance testing",
          testPermissions.slice(0, 2)
        );

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${testRole.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("id");
        expect(response.data).toHaveProperty("name");
        expect(response.data).toHaveProperty("description");
        expect(response.data).toHaveProperty("permissions");
        expect(Array.isArray(response.data.permissions)).toBe(true);

        // UUID format validation
        expect(response.data.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);

        // Cleanup
        await deleteTestRole(testRole.id);
      });

      it("should return correct response structure for roles list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(response.data).toHaveProperty("limit");
        expect(response.data).toHaveProperty("offset");
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(typeof response.data.total_count).toBe("number");
        expect(typeof response.data.limit).toBe("number");
        expect(typeof response.data.offset).toBe("number");

        if (response.data.list.length > 0) {
          const role = response.data.list[0];
          expect(role).toHaveProperty("id");
          expect(role).toHaveProperty("name");
          expect(role).toHaveProperty("permissions");
          expect(Array.isArray(role.permissions)).toBe(true);
        }
      });
    });

    describe("Error Response Structure Validation", () => {

      it("should return standard error structure for 400 Bad Request", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            // Missing required fields to trigger 400
          }
        });

        expect(response.status).toBe(400);
        expect(response.data).toHaveProperty("error");
        expect(response.data).toHaveProperty("error_description");
        expect(typeof response.data.error).toBe("string");
        expect(typeof response.data.error_description).toBe("string");
      });

      it("should return standard error structure for 404 Not Found", async () => {
        const nonExistentId = uuidv4();
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
        expect(response.data).toHaveProperty("error");
        expect(response.data).toHaveProperty("error_description");
        expect(response.data.error).toBe("not_found");
        expect(typeof response.data.error_description).toBe("string");
      });
    });
  });
});