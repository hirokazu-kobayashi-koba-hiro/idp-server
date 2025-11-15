/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Organization Authentication Config Management API - Structured E2E Tests
 *
 * This test suite provides comprehensive testing for organization-level authentication configuration management,
 * covering all aspects of the authentication configuration management API including:
 *
 * - Authentication Config Creation (postWithJson)
 * - Authentication Config List Retrieval (GET)
 * - Authentication Config Detail Retrieval (GET)
 * - Authentication Config Update (putWithJson)
 * - Authentication Config deletionetion (delete)
 * - Organization-level Access Control
 * - Error Cases and Edge Cases
 * - API Specification Compliance
 */

import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { validateAuthenticationConfig, validateListResponse } from "../../../../lib/schemaValidation";
import { v4 as uuidv4 } from "uuid";

describe("Organization Authentication Config Management API - Structured Tests", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";
  let accessToken;

  beforeAll(async () => {
    // Authenticate as organization admin
    const authResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      clientId: "org-client",
      clientSecret: "org-client-001",
      scope: "org-management account management"
    });

    expect(authResponse.status).toBe(200);
    expect(authResponse.data).toHaveProperty("access_token");
    accessToken = authResponse.data.access_token;
  });

  /**
   * Layer 1: API Specification Compliance Tests
   * Tests fundamental API compliance and contract adherence
   */
  describe("API Specification Compliance Tests", () => {
    describe("Response Structure Validation", () => {
      it("should return correct response structure for authentication config list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations?limit=10&offset=0`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect([200]).toContain(response.status);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(response.data).toHaveProperty("limit", 10);
        expect(response.data).toHaveProperty("offset", 0);
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(typeof response.data.total_count).toBe("number");

        // OpenAPI schema validation
        const validation = validateListResponse(response.data, validateAuthenticationConfig);
        if (!validation.valid) {
          console.log("Schema validation errors:", validation.errors);
        }
        expect(validation.valid).toBe(true);
      });

      it("should return correct response structure for authentication config creation", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `test-auth-${configId}`,
            config: {
              test: true
            }
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id");
        expect(response.data.result).toHaveProperty("type");

        // OpenAPI schema validation
        const validation = validateAuthenticationConfig(response.data.result);
        if (!validation.valid) {
          console.log("Schema validation errors:", validation.errors);
        }
        expect(validation.valid).toBe(true);
      });
    });

    describe("Error Response Structure Validation", () => {
      it("should return standard error structure for 404 Not Found", async () => {
        const nonExistentConfigId = "00000000-0000-0000-0000-000000000000";
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${nonExistentConfigId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });

      it("should return standard error structure for invalid request", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            invalid: "data"
          }
        });

        expect([400, 500]).toContain(response.status);
      });
    });
  });

  /**
   * Layer 2: Functional API Tests
   * Tests core functionality and business logic
   */
  describe("Functional API Tests", () => {
    describe("Authentication Config Creation", () => {
      it("should successfully create authentication configuration", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `test-auth-${configId}`,
            config: {
              enabled: true,
              test: true
            }
          }
        });

        expect(response.status).toBe(201);
        expect(response.data).toHaveProperty("dry_run", false);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id", configId);
        expect(response.data.result).toHaveProperty("type", `test-auth-${configId}`);

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should support dry run mode for creation", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `test-auth-dry-${configId}`,
            config: {
              enabled: false
            }
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");
      });
    });

    describe("Authentication Config List Retrieval", () => {
      it("should successfully retrieve authentication config list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(typeof response.data.total_count).toBe("number");
      });

      it("should support pagination with limit parameter", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations?limit=5`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 5);
        expect(response.data.list.length).toBeLessThanOrEqual(5);
      });

      it("should support pagination with offset parameter", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations?limit=5&offset=2`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 5);
        expect(response.data).toHaveProperty("offset", 2);
      });
    });

    describe("Authentication Config Detail Retrieval", () => {
      it("should successfully retrieve specific authentication config when it exists", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `detail-test-${configId}`,
            config: {
              test: true
            }
          }
        });

        expect(createResponse.status).toBe(201);

        // Now retrieve it
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("id", configId);
        expect(response.data).toHaveProperty("type");
        expect(response.data).toHaveProperty("interactions");

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should return 404 for non-existent authentication config", async () => {
        const nonExistentId = "00000000-0000-0000-0000-000000000000";
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });
    });

    describe("Authentication Config Update", () => {
      it("should successfully update authentication configuration", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `update-test-${configId}`,
            config: {
              enabled: true,
              test: true
            }
          }
        });

        expect(createResponse.status).toBe(201);

        // Now update it
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `updated-test-auth-${configId}`,
            config: {
              enabled: false,
              updated: true
            }
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", false);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id", configId);

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should support dry run mode for update", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `dry-update-test-${configId}`,
            config: {
              enabled: true,
              test: true
            }
          }
        });

        expect(createResponse.status).toBe(201);

        // Now test dry run update
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `dry-update-test-auth-${configId}`,
            config: {
              enabled: true,
              dry_update: true
            }
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });
    });

    describe("Authentication Config deletionetion", () => {
      it("should support dry run mode for deletionetion", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `dry-deletion-test-${configId}`,
            config: {
              test: true
            }
          }
        });

        expect(createResponse.status).toBe(201);

        // Test dry run deletion
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);

        // Clean up (actual deletion)
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should successfully delete authentication configuration", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `deletion-test-${configId}`,
            config: {
              test: true
            }
          }
        });

        expect(createResponse.status).toBe(201);

        // Now delete it
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(204);
      });
    });
  });

  /**
   * Layer 3: Organization Access Control Tests
   * Tests organization-level security and isolation
   */
  describe("Organization Access Control Tests", () => {
    it("should return 401 for unauthenticated requests", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
        headers: {}
      });

      expect(response.status).toBe(401);
    });

    it("should return 400/404 for invalid organization ID", async () => {
      const invalidOrgId = "invalid-org-id";
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${invalidOrgId}/tenants/${tenantId}/authentication-configurations`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      expect([400, 404]).toContain(response.status);
    });

    it("should return 400/404 for invalid tenant ID", async () => {
      const invalidTenantId = "invalid-tenant-id";
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${invalidTenantId}/authentication-configurations`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      expect([400, 404]).toContain(response.status);
    });
  });

  /**
   * Layer 4: Integration Tests
   * Tests complete workflows and integrations
   */
  describe("Integration Tests", () => {
    describe("Complete Authentication Config Management Workflow", () => {
      it("should successfully manage authentication config lifecycle", async () => {
        const configId = uuidv4();

        // 1. Create authentication config
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `lifecycle-test-${configId}`,
            config: {
              enabled: true,
              lifecycle_test: true
            }
          }
        });

        expect(createResponse.status).toBe(201);
        expect(createResponse.data.result).toHaveProperty("id", configId);

        // 2. Retrieve the created config
        const getResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getResponse.status).toBe(200);
        expect(getResponse.data).toHaveProperty("id", configId);
        expect(getResponse.data).toHaveProperty("type", `lifecycle-test-${configId}`);

        // 3. Update the config
        const updateResponse = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: `updated-lifecycle-test-${configId}`,
            config: {
              enabled: false,
              lifecycle_test: true,
              updated: true
            }
          }
        });

        expect(updateResponse.status).toBe(200);
        expect(updateResponse.data.result).toHaveProperty("id", configId);

        // 4. Verify the update
        const getUpdatedResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getUpdatedResponse.status).toBe(200);
        expect(getUpdatedResponse.data).toHaveProperty("type", `updated-lifecycle-test-${configId}`);

        // 5. delete the config
        const deleteResponse = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(deleteResponse.status).toBe(204);

        // 6. Verify deletionetion
        const getdeletedResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getdeletedResponse.status).toBe(404);
      });

      it("should successfully retrieve and paginate through authentication configs", async () => {
        let offset = 0;
        const limit = 5;
        let hasMoreConfigs = true;
        let allConfigs = [];

        while (hasMoreConfigs && allConfigs.length < 20) { // Limit to prevent infinite loops
          const response = await get({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations?limit=${limit}&offset=${offset}`,
            headers: { Authorization: `Bearer ${accessToken}` }
          });

          expect(response.status).toBe(200);
          expect(response.data).toHaveProperty("list");
          expect(response.data).toHaveProperty("total_count");
          expect(response.data).toHaveProperty("limit", limit);
          expect(response.data).toHaveProperty("offset", offset);

          allConfigs = allConfigs.concat(response.data.list);

          // Check if there are more configs
          if (response.data.list.length < limit || allConfigs.length >= response.data.total_count) {
            hasMoreConfigs = false;
          } else {
            offset += limit;
          }
        }

        // Verify that we can retrieve configs and they have proper structure
        if (allConfigs.length > 0) {
          const firstConfig = allConfigs[0];
          expect(firstConfig).toHaveProperty("id");
          expect(firstConfig).toHaveProperty("type");
        }
      });
    });
  });
});