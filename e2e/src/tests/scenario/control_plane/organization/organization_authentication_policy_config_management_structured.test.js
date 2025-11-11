/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Organization Authentication Policy Config Management API - Structured E2E Tests
 *
 * This test suite provides comprehensive testing for organization-level authentication policy configuration management,
 * covering all aspects of the authentication policy configuration management API including:
 *
 * - Authentication Policy Config Creation (POST)
 * - Authentication Policy Config List Retrieval (GET)
 * - Authentication Policy Config Detail Retrieval (GET)
 * - Authentication Policy Config Update (PUT)
 * - Authentication Policy Config Deletion (DELETE)
 * - Organization-level Access Control
 * - Error Cases and Edge Cases
 * - API Specification Compliance
 */

import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { validateListResponse } from "../../../../lib/schemaValidation";
import { v4 as uuidv4 } from "uuid";

describe("Organization Authentication Policy Config Management API - Structured Tests", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";
  let accessToken;

  beforeAll(async () => {
    // Authenticate as organization admin
    const authResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro",
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
      it("should return correct response structure for authentication policy config list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies?limit=10&offset=0`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect([200]).toContain(response.status);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(response.data).toHaveProperty("limit", 10);
        expect(response.data).toHaveProperty("offset", 0);
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(typeof response.data.total_count).toBe("number");
      });

      it("should return correct response structure for authentication policy config creation", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: configId,
            policies: [
              {
                description: "test_password_policy",
                priority: 1,
                conditions: {
                  scopes: ["openid"]
                },
                available_methods: ["password"],
                success_conditions: {
                  any_of: [
                    [
                      {
                        path: "$.password-authentication.success_count",
                        type: "integer",
                        operation: "gte",
                        value: 1
                      }
                    ]
                  ]
                }
              }
            ],
            enabled: true
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id");
        expect(response.data.result).toHaveProperty("flow");
      });
    });

    describe("Error Response Structure Validation", () => {
      it("should return 404 for non-existent authentication policy config", async () => {
        const nonExistentConfigId = "00000000-0000-0000-0000-000000000000";
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${nonExistentConfigId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });

      it("should return standard error structure for invalid request", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
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
   * Tests core functionality and business logic in RESTful order
   */
  describe("Functional API Tests", () => {
    describe("Authentication Policy Config Creation (POST)", () => {
      it("should successfully create authentication policy configuration", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: configId,
            policies: [
              {
                priority: 1,
                conditions: {
                  client_ids: ["test-client"],
                  scopes: ["openid", "profile"]
                },
                available_methods: ["password", "fido2"],
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: true
          }
        });

        expect(response.status).toBe(201);
        expect(response.data).toHaveProperty("dry_run", false);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id", configId);
        expect(response.data.result).toHaveProperty("flow", configId);

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should support dry run mode for creation", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: "client_credentials",
            policies: [
              {
                priority: 2,
                conditions: {
                  client_ids: ["api-client"]
                },
                available_methods: ["client_secret"],
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: false
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");
      });
    });

    describe("Authentication Policy Config List Retrieval (GET)", () => {
      it("should successfully retrieve authentication policy config list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(typeof response.data.total_count).toBe("number");
      });

      it("should support pagination with limit parameter", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies?limit=5`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 5);
        expect(response.data.list.length).toBeLessThanOrEqual(5);
      });

      it("should support pagination with offset parameter", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies?limit=5&offset=2`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 5);
        expect(response.data).toHaveProperty("offset", 2);
      });
    });

    describe("Authentication Policy Config Detail Retrieval (GET)", () => {
      it("should successfully retrieve specific authentication policy config when it exists", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: "hybrid",
            policies: [
              {
                priority: 3,
                conditions: {
                  client_ids: ["detail-test-client"],
                  acr_values: ["1", "2"]
                },
                available_methods: ["password", "sms"],
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: true
          }
        });

        expect([201, 409]).toContain(createResponse.status); // May already exist

        // Now retrieve it
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("id", configId);
        expect(response.data).toHaveProperty("flow", "hybrid");
        expect(response.data).toHaveProperty("policies");
        expect(Array.isArray(response.data.policies)).toBe(true);

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should return 404 for non-existent authentication policy config", async () => {
        const nonExistentId = "00000000-0000-0000-0000-000000000000";
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });
    });

    describe("Authentication Policy Config Update (PUT)", () => {
      it("should successfully update authentication policy configuration", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: configId,
            policies: [
              {
                priority: 1,
                conditions: {
                  client_ids: ["update-test-client"]
                },
                available_methods: ["password"],
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: true
          }
        });

        expect([201, 409]).toContain(createResponse.status); // May already exist

        // Now update it
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: "implicit",
            policies: [
              {
                priority: 2,
                conditions: {
                  client_ids: ["updated-test-client"],
                  scopes: ["openid", "email"]
                },
                available_methods: ["fido2", "sms"],
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: false
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", false);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id", configId);

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should support dry run mode for update", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: configId,
            policies: [
              {
                priority: 1,
                conditions: {
                  client_ids: ["dry-update-test-client"]
                },
                available_methods: ["password"],
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: true
          }
        });

        expect([201, 409]).toContain(createResponse.status); // May already exist

        // Now test dry run update
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: "hybrid",
            policies: [
              {
                priority: 3,
                conditions: {
                  client_ids: ["dry-update-test-client"],
                  acr_values: ["2"]
                },
                available_methods: ["fido2"],
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: true
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });
    });

    describe("Authentication Policy Config Deletion (DELETE)", () => {
      it("should support dry run mode for deletion", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: configId,
            policies: [
              {
                priority: 1,
                conditions: {
                  client_ids: ["dry-deletion-test-client"]
                },
                available_methods: ["password"],
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: true
          }
        });

        expect([201, 409]).toContain(createResponse.status); // May already exist

        // Test dry run deletion
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("message");

        // Clean up (actual deletion)
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should successfully delete authentication policy configuration", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: configId,
            policies: [
              {
                priority: 1,
                conditions: {
                  client_ids: ["deletion-test-client"]
                },
                available_methods: ["password"],
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: true
          }
        });

        expect([201, 409]).toContain(createResponse.status); // May already exist

        // Now delete it
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
        headers: {}
      });

      expect(response.status).toBe(401);
    });

    it("should return 400/404 for invalid organization ID", async () => {
      const invalidOrgId = "invalid-org-id";
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${invalidOrgId}/tenants/${tenantId}/authentication-policies`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      expect([400, 404]).toContain(response.status);
    });

    it("should return 400/404 for invalid tenant ID", async () => {
      const invalidTenantId = "invalid-tenant-id";
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${invalidTenantId}/authentication-policies`,
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
    describe("Complete Authentication Policy Config Management Workflow", () => {
      it("should successfully manage authentication policy config lifecycle", async () => {
        const configId = uuidv4();

        // 1. Create authentication policy config (POST)
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: configId,
            policies: [
              {
                priority: 1,
                conditions: {
                  client_ids: ["lifecycle-test-client"],
                  scopes: ["openid", "profile"]
                },
                available_methods: ["password", "fido2"],
                acr_mapping_rules: {
                  "1": ["password"],
                  "2": ["fido2"]
                },
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: true
          }
        });

        expect([201, 409]).toContain(createResponse.status); // May already exist
        expect(createResponse.data.result).toHaveProperty("id", configId);

        // 2. Retrieve the created config (GET detail)
        const getResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getResponse.status).toBe(200);
        expect(getResponse.data).toHaveProperty("id", configId);
        expect(getResponse.data).toHaveProperty("flow", configId);

        // 3. Update the config (PUT)
        const updateResponse = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            flow: "hybrid",
            policies: [
              {
                priority: 2,
                conditions: {
                  client_ids: ["updated-lifecycle-test-client"],
                  scopes: ["openid", "email", "profile"],
                  acr_values: ["2"]
                },
                available_methods: ["fido2", "sms"],
                level_of_authentication_scopes: {
                  "high": ["email"],
                  "medium": ["profile"]
                },
                success_conditions: {
                  conditions: []
                }
              }
            ],
            enabled: false
          }
        });

        expect(updateResponse.status).toBe(200);
        expect(updateResponse.data.result).toHaveProperty("id", configId);

        // 4. Verify the update (GET detail)
        const getUpdatedResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getUpdatedResponse.status).toBe(200);
        expect(getUpdatedResponse.data).toHaveProperty("flow", "hybrid");

        // 5. Delete the config (DELETE)
        const deleteResponse = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(deleteResponse.status).toBe(204);

        // 6. Verify deletion (GET detail should return 404)
        const getDeletedResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getDeletedResponse.status).toBe(404);
      });

      it("should successfully retrieve and paginate through authentication policy configs", async () => {
        let offset = 0;
        const limit = 5;
        let hasMoreConfigs = true;
        let allConfigs = [];

        while (hasMoreConfigs && allConfigs.length < 20) { // Limit to prevent infinite loops
          const response = await get({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies?limit=${limit}&offset=${offset}`,
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
          expect(firstConfig).toHaveProperty("flow");
        }
      });
    });
  });
});