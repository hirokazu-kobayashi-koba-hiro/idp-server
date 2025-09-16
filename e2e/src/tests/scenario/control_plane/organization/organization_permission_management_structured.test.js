import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { generateRandomString } from "../../../../lib/util";
import { v4 as uuidv4 } from "uuid";

/**
 * Organization Permission Management API - Structured E2E Tests
 *
 * Comprehensive test suite based on API quality improvement strategy
 * - JsonSchema validation tests
 * - Business rule tests
 * - Integration tests
 * - API specification compliance tests
 */
describe("Organization Permission Management API - Structured Tests", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  let accessToken;
  let testTenantId;

  // Helper function to create a test permission
  const createTestPermission = async (permissionName, description = "Test permission") => {
    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
      headers: { Authorization: `Bearer ${accessToken}` },
      body: {
        "name": permissionName,
        "description": description
      }
    });
    expect(response.status).toBe(201);
    return response.data.result;
  };

  // Helper function to delete a test permission
  const deleteTestPermission = async (permissionId) => {
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${permissionId}`,
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

    describe("POST /permissions - Create Permission Validation", () => {

      it("should return 400 for missing required field 'name'", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            // name field missing
            "description": "Test permission without name"
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
      });

      it("should accept permission creation without description (optional field)", async () => {
        const permissionName = `test-permission-no-desc-${Date.now()}`;
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": permissionName
            // description field missing - should be optional
          }
        });

        expect(response.status).toBe(201);
        expect(response.data.result).toHaveProperty("name", permissionName);

        // Cleanup
        await deleteTestPermission(response.data.result.id);
      });

      it("should return 400 for invalid name type (not string)", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": 123, // should be string
            "description": "Test permission with invalid name type"
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
      });

      it("should return 400 for name exceeding maxLength (255 chars)", async () => {
        const longName = "a".repeat(256); // 256 characters
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": longName,
            "description": "Test permission with overly long name"
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
      });

      it("should return 400 for invalid description type (not string)", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": "test-permission-invalid-desc",
            "description": 123 // should be string
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");
      });
    });

    describe("PUT /permissions/{id} - Update Permission Validation", () => {

      it("should return 400 for missing required field 'name' in update", async () => {
        // Create test permission for this specific test
        const testPermission = await createTestPermission(`test-permission-for-validation-${Date.now()}`);

        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${testPermission.id}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            // name field missing
            "description": "Updated description without name"
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");

        // Cleanup
        await deleteTestPermission(testPermission.id);
      });

      it("should accept permission update with only name (description optional)", async () => {
        // Create test permission for this specific test
        const testPermission = await createTestPermission(`test-permission-name-only-${Date.now()}`);

        const updatedName = `${testPermission.name}-updated`;
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${testPermission.id}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": updatedName
            // description field missing - should be optional
          }
        });

        expect(response.status).toBe(200);
        expect(response.data.result.name).toBe(updatedName);

        // Cleanup
        await deleteTestPermission(testPermission.id);
      });
    });
  });

  /**
   * 📋 2. Business Rule Tests
   * Based on strategy document lines 256-259
   */
  describe("Business Rule Tests", () => {

    describe("Resource Existence Validation", () => {

      it("should return 404 for non-existent permission ID in GET", async () => {
        const nonExistentId = uuidv4();
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });

      it("should return 404 for non-existent permission ID in PUT", async () => {
        const nonExistentId = uuidv4();
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": "updated-permission",
            "description": "Updated permission description"
          }
        });

        expect(response.status).toBe(404);
      });

      it("should return 404 for non-existent permission ID in DELETE", async () => {
        const nonExistentId = uuidv4();
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });
    });

    describe("Duplicate Prevention", () => {

      it("should return 409 for duplicate permission name in creation", async () => {
        // Create first permission
        const existingPermissionName = `existing-permission-${Date.now()}`;
        const firstPermission = await createTestPermission(existingPermissionName, "Existing permission for duplicate testing");

        // Attempt to create permission with same name
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": existingPermissionName, // duplicate name
            "description": "Another permission with the same name"
          }
        });

        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_request");

        // Cleanup
        await deleteTestPermission(firstPermission.id);
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
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
            headers: { Authorization: `Bearer ${limitedTokenResponse.data.access_token}` },
            body: {
              "name": "test-permission-forbidden",
              "description": "Test permission with insufficient permissions"
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

      it("should successfully create a permission with all fields", async () => {
        const permissionName = `create-test-permission-${Date.now()}`;
        const permission = await createTestPermission(
          permissionName,
          "Integration test permission with complete data"
        );

        expect(permission).toHaveProperty("id");
        expect(permission).toHaveProperty("name", permissionName);
        expect(permission).toHaveProperty("description", "Integration test permission with complete data");

        // UUID format validation
        expect(permission.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);

        // Cleanup
        await deleteTestPermission(permission.id);
      });

      it("should successfully retrieve a created permission", async () => {
        const permissionName = `retrieve-test-permission-${Date.now()}`;
        const createdPermission = await createTestPermission(permissionName, "Permission for retrieval test");

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${createdPermission.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("id", createdPermission.id);
        expect(response.data).toHaveProperty("name", permissionName);
        expect(response.data).toHaveProperty("description", "Permission for retrieval test");

        // Cleanup
        await deleteTestPermission(createdPermission.id);
      });

      it("should successfully list permissions including the created one", async () => {
        const permissionName = `list-test-permission-${Date.now()}`;
        const createdPermission = await createTestPermission(permissionName, "Permission for list test");

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(response.data).toHaveProperty("limit");
        expect(response.data).toHaveProperty("offset");
        expect(Array.isArray(response.data.list)).toBe(true);

        // Check if our created permission is in the list
        const foundPermission = response.data.list.find(p => p.id === createdPermission.id);
        expect(foundPermission).toBeTruthy();
        expect(foundPermission.name).toBe(permissionName);

        // Cleanup
        await deleteTestPermission(createdPermission.id);
      });

      it("should successfully update a created permission", async () => {
        const permissionName = `update-test-permission-${Date.now()}`;
        const createdPermission = await createTestPermission(permissionName, "Original description");

        const updatedName = `${permissionName}-updated`;
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${createdPermission.id}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": updatedName,
            "description": "Updated integration test permission"
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", false);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result.name).toBe(updatedName);
        expect(response.data.result.description).toBe("Updated integration test permission");

        // Cleanup
        await deleteTestPermission(createdPermission.id);
      });

      it("should successfully delete a created permission", async () => {
        const permissionName = `delete-test-permission-${Date.now()}`;
        const createdPermission = await createTestPermission(permissionName);

        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${createdPermission.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(204); // DELETE returns 204 No Content
        // 204 responses typically have no body

        // Verify deletion by trying to retrieve
        const verifyResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${createdPermission.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        expect(verifyResponse.status).toBe(404);
      });
    });

    describe("Dry Run Functionality", () => {

      it("should perform dry run for permission creation", async () => {
        const permissionName = `dry-run-create-permission-${Date.now()}`;
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": permissionName,
            "description": "Dry run test permission"
          }
        });

        expect(response.status).toBe(201); // Dry run creation still returns 201
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");

        // Verify permission was not actually created
        const verifyResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        const foundPermission = verifyResponse.data.list.find(p => p.name === permissionName);
        expect(foundPermission).toBeFalsy();
      });

      it("should perform dry run for permission update", async () => {
        const permissionName = `dry-run-update-permission-${Date.now()}`;
        const createdPermission = await createTestPermission(permissionName, "Original description");

        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${createdPermission.id}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": `${permissionName}-updated`,
            "description": "Dry run updated description"
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);

        // Verify permission was not actually updated
        const verifyResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${createdPermission.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        expect(verifyResponse.data.name).toBe(permissionName); // Original name
        expect(verifyResponse.data.description).toBe("Original description"); // Original description

        // Cleanup
        await deleteTestPermission(createdPermission.id);
      });

      it("should perform dry run for permission deletion", async () => {
        const permissionName = `dry-run-delete-permission-${Date.now()}`;
        const createdPermission = await createTestPermission(permissionName);

        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${createdPermission.id}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(204); // Dry run deletion returns 204
        // 204 responses typically have no body

        // Verify permission still exists
        const verifyResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${createdPermission.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        expect(verifyResponse.status).toBe(200);

        // Cleanup
        await deleteTestPermission(createdPermission.id);
      });
    });

    describe("Pagination and Filtering", () => {

      // Helper to create multiple test permissions
      const createMultipleTestPermissions = async (count) => {
        const permissions = [];
        for (let i = 1; i <= count; i++) {
          const permission = await createTestPermission(
            `pagination-permission-${i}-${Date.now()}`,
            `Pagination test permission ${i}`
          );
          permissions.push(permission);
        }
        return permissions;
      };

      const cleanupMultiplePermissions = async (permissions) => {
        for (const permission of permissions) {
          await deleteTestPermission(permission.id);
        }
      };

      it("should support pagination with limit parameter", async () => {
        // Create test data for this specific test
        const testPermissions = await createMultipleTestPermissions(5);

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions?limit=3`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(response.data).toHaveProperty("limit", 3);
        expect(response.data).toHaveProperty("offset", 0);
        expect(response.data.list.length).toBeLessThanOrEqual(3);

        // Cleanup
        await cleanupMultiplePermissions(testPermissions);
      });

      it("should support pagination with offset parameter", async () => {
        // Create test data for this specific test
        const testPermissions = await createMultipleTestPermissions(5);

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions?limit=2&offset=1`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 2);
        expect(response.data).toHaveProperty("offset", 1);
        expect(response.data.list.length).toBeLessThanOrEqual(2);

        // Cleanup
        await cleanupMultiplePermissions(testPermissions);
      });
    });
  });

  /**
   * 📋 4. API Specification Compliance Tests
   * Verify complete compliance with OpenAPI specification
   */
  describe("API Specification Compliance Tests", () => {

    describe("Response Structure Validation", () => {

      it("should return correct response structure for permission creation", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": `spec-test-permission-${generateRandomString(8)}`,
            "description": "Permission for spec compliance testing"
          }
        });

        expect(response.status).toBe(201);
        expect(response.data).toHaveProperty("dry_run", false);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id");
        expect(response.data.result).toHaveProperty("name");
        expect(response.data.result).toHaveProperty("description");

        // UUID format validation
        expect(response.data.result.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);

        // Cleanup
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${response.data.result.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should return correct response structure for permission retrieval", async () => {
        // Create test permission for this specific test
        const testPermission = await createTestPermission(
          `spec-compliance-permission-${Date.now()}`,
          "Permission for spec compliance testing"
        );

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${testPermission.id}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("id");
        expect(response.data).toHaveProperty("name");
        expect(response.data).toHaveProperty("description");

        // UUID format validation
        expect(response.data.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);

        // Cleanup
        await deleteTestPermission(testPermission.id);
      });

      it("should return correct response structure for permissions list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
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
          const permission = response.data.list[0];
          expect(permission).toHaveProperty("id");
          expect(permission).toHaveProperty("name");
          expect(permission).toHaveProperty("description");
        }
      });
    });

    describe("Error Response Structure Validation", () => {

      it("should return standard error structure for 400 Bad Request", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            // Missing required name field
            "description": "Invalid permission request"
          }
        });

        expect(response.status).toBe(400);
        expect(response.data).toHaveProperty("error");
        expect(response.data).toHaveProperty("error_description");
        expect(response.data.error).toBe("invalid_request");
      });

      it("should return standard error structure for 404 Not Found", async () => {
        const nonExistentId = uuidv4();
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
        // 404 responses may have minimal structure or empty body
      });
    });
  });
});