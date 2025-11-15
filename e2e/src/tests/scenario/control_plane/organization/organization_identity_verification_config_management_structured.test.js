/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Organization Identity Verification Config Management API - Structured E2E Tests
 *
 * This test suite provides comprehensive testing for organization-level identity verification configuration management,
 * covering all aspects of the identity verification configuration management API including:
 *
 * - Identity Verification Config Creation (POST)
 * - Identity Verification Config List Retrieval (GET)
 * - Identity Verification Config Detail Retrieval (GET)
 * - Identity Verification Config Update (PUT)
 * - Identity Verification Config Deletion (DELETE)
 * - Organization-level Access Control
 * - Error Cases and Edge Cases
 * - API Specification Compliance
 */

import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";

describe("Organization Identity Verification Config Management API - Structured Tests", () => {
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
      it("should return correct response structure for identity verification config list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations?limit=10&offset=0`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(response.data).toHaveProperty("limit", 10);
        expect(response.data).toHaveProperty("offset", 0);
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(typeof response.data.total_count).toBe("number");
      });

      it("should return correct response structure for identity verification config creation", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: uuidv4(),
            attributes: {
              enabled: true
            },
            common: {
              callback_application_id_param: "app_id",
              auth_type: "bearer"
            },
            processes: {
              email_verification: {
                request: {
                  basic_auth: {
                    username: "test_user",
                    password: "test_pass"
                  },
                  schema: {
                    type: "object",
                    properties: {
                      email: { type: "string" }
                    }
                  }
                },
                execution: {
                  mock: {
                    enabled: true,
                    response: { status: "success" }
                  }
                }
              }
            },
            registration: {
              enabled: true,
              auto_registration: false,
              required_attributes: ["email"]
            },
            result: {
              verified_claims_mapping_rules: [
                {
                  from: "$.email",
                  to: "$.verified_claims.email",
                  convert_type: "string"
                }
              ]
            }
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");

        // Log the actual response structure for verification
        console.log("Create Response Result:", JSON.stringify(response.data.result, null, 2));

        // Verify detailed structure elements
        expect(response.data.result).toHaveProperty("id");
        expect(response.data.result).toHaveProperty("type");
        expect(response.data.result).toHaveProperty("attributes");
        expect(response.data.result).toHaveProperty("common");
        expect(response.data.result).toHaveProperty("processes");
        expect(response.data.result).toHaveProperty("registration");
        expect(response.data.result).toHaveProperty("result");
        expect(response.data.result.result).toHaveProperty("verified_claims_mapping_rules");
        expect(response.data.result.result).toHaveProperty("source_details_mapping_rules");

        // Verify nested attributes structure
        expect(response.data.result.attributes).toHaveProperty("enabled");
        expect(typeof response.data.result.attributes.enabled).toBe("boolean");

        // Verify common structure
        expect(response.data.result.common).toHaveProperty("auth_type");
        expect(response.data.result.common).toHaveProperty("callback_application_id_param");

        // Verify processes structure
        expect(typeof response.data.result.processes).toBe("object");

        // Verify mapping rules are arrays in result.result
        expect(Array.isArray(response.data.result.result.verified_claims_mapping_rules)).toBe(true);
        expect(Array.isArray(response.data.result.result.source_details_mapping_rules)).toBe(true);
      });
    });

    describe("Error Response Structure Validation", () => {
      it("should return 404 for non-existent identity verification config", async () => {
        const nonExistentConfigId = "00000000-0000-0000-0000-000000000000";
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${nonExistentConfigId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });

      it("should return standard error structure for invalid request", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            invalid: "data"
          }
        });

        expect(response.status).toBe(400);
      });
    });
  });

  /**
   * Layer 2: Functional API Tests
   * Tests core functionality and business logic in RESTful order
   */
  describe("Functional API Tests", () => {
    describe("Identity Verification Config Creation (POST)", () => {
      it("should successfully create identity verification configuration", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: uuidv4(),
            attributes: {
              enabled: true,
              verification_timeout: 300
            },
            common: {
              callback_application_id_param: "app_id",
              auth_type: "bearer"
            },
            processes: {
              document_check: {
                request: {
                  schema: {
                    type: "object",
                    properties: {
                      document_type: { type: "string" },
                      document_image: { type: "string" }
                    },
                    required: ["document_type", "document_image"]
                  }
                },
                execution: {
                  http_request: {
                    url: "https://api.example.com/verify",
                    method: "POST",
                    headers: {
                      "Content-Type": "application/json"
                    }
                  }
                },
                transition: {
                  approved: {
                    conditions: [
                      {
                        path: "$.verification_result.status",
                        operator: "eq",
                        value: "approved",
                        type: "string"
                      }
                    ]
                  },
                  rejected: {
                    conditions: [
                      {
                        path: "$.verification_result.status",
                        operator: "eq",
                        value: "rejected",
                        type: "string"
                      }
                    ]
                  }
                }
              }
            },
            registration: {
              enabled: true,
              auto_registration: false,
              required_attributes: ["document_type"]
            },
            result: {
              verified_claims_mapping_rules: [
                {
                  from: "$.verification_result.document_data.name",
                  to: "$.verified_claims.name",
                  convert_type: "string"
                }
              ],
              source_details_mapping_rules: [
                {
                  from: "$.verification_result.document_data.issuer",
                  to: "$.source_details.issuer",
                  convert_type: "string"
                }
              ]
            }
          }
        });

        expect(response.status).toBe(201);
        expect(response.data).toHaveProperty("dry_run", false);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id");
        expect(response.data.result).toHaveProperty("type");

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should support dry run mode for creation", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: uuidv4(),
            attributes: {
              enabled: false
            },
            processes: {
              simple_check: {
                execution: {
                  mock: {
                    enabled: true,
                    response: { status: "pending" }
                  }
                }
              }
            }
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");
      });
    });

    describe("Identity Verification Config List Retrieval (GET)", () => {
      it("should successfully retrieve identity verification config list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(typeof response.data.total_count).toBe("number");
      });

      it("should support pagination with limit parameter", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations?limit=5`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 5);
        expect(response.data.list.length).toBeLessThanOrEqual(5);
      });

      it("should support pagination with offset parameter", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations?limit=5&offset=2`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 5);
        expect(response.data).toHaveProperty("offset", 2);
      });
    });

    describe("Identity Verification Config Detail Retrieval (GET)", () => {
      it("should successfully retrieve specific identity verification config when it exists", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: uuidv4(),
            attributes: {
              enabled: true
            },
            processes: {
              test_process: {
                execution: {
                  mock: {
                    enabled: true,
                    response: { status: "test" }
                  }
                }
              }
            }
          }
        });

        expect(createResponse.status).toBe(201);

        // Now retrieve it
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);

        // Log the actual GET response structure for verification
        console.log("GET Response Data:", JSON.stringify(response.data, null, 2));

        // Verify detailed GET response structure elements
        expect(response.data).toHaveProperty("id", configId);
        expect(response.data).toHaveProperty("type");
        expect(response.data).toHaveProperty("attributes");
        expect(response.data).toHaveProperty("processes");

        // Verify attributes structure
        expect(response.data.attributes).toHaveProperty("enabled");
        expect(typeof response.data.attributes.enabled).toBe("boolean");

        // Verify processes structure
        expect(typeof response.data.processes).toBe("object");
        expect(response.data.processes).toHaveProperty("test_process");

        // Verify UUID format for id and type
        expect(response.data.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
        expect(response.data.type).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should return 404 for non-existent identity verification config", async () => {
        const nonExistentId = "00000000-0000-0000-0000-000000000000";
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });
    });

    describe("Identity Verification Config Update (PUT)", () => {
      it("should successfully update identity verification configuration", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: uuidv4(),
            attributes: {
              enabled: true
            }
          }
        });

        expect(createResponse.status).toBe(201);

        // Now update it
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            type: uuidv4(),
            attributes: {
              enabled: false,
              updated_field: "new_value"
            },
            common: {
              auth_type: "hmac"
            },
            processes: {
              updated_process: {
                execution: {
                  mock: {
                    enabled: true,
                    response: { status: "updated" }
                  }
                }
              }
            }
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", false);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id");

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should support dry run mode for update", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: uuidv4(),
            attributes: {
              enabled: true
            }
          }
        });

        expect(createResponse.status).toBe(201);

        // Now test dry run update
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            type: uuidv4(),
            attributes: {
              enabled: false
            }
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });
    });

    describe("Identity Verification Config Deletion (DELETE)", () => {
      it("should support dry run mode for deletion", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: uuidv4(),
            attributes: {
              enabled: true
            }
          }
        });

        expect(createResponse.status).toBe(201);

        // Test dry run deletion
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);

        // Clean up (actual deletion)
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should successfully delete identity verification configuration", async () => {
        // Create a config specifically for this test
        const configId = uuidv4();
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: uuidv4(),
            attributes: {
              enabled: true
            }
          }
        });

        expect(createResponse.status).toBe(201);

        // Now delete it
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
        headers: {}
      });

      expect(response.status).toBe(401);
    });

    it("should return 400/404 for invalid organization ID", async () => {
      const invalidOrgId = "invalid-org-id";
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${invalidOrgId}/tenants/${tenantId}/identity-verification-configurations`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      expect([400, 404]).toContain(response.status);
    });

    it("should return 400/404 for invalid tenant ID", async () => {
      const invalidTenantId = "invalid-tenant-id";
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${invalidTenantId}/identity-verification-configurations`,
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
    describe("Complete Identity Verification Config Management Workflow", () => {
      it("should successfully manage identity verification config lifecycle", async () => {
        const configId = uuidv4();

        // 1. Create identity verification config (POST)
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: uuidv4(),
            attributes: {
              enabled: true,
              test_mode: true
            },
            common: {
              callback_application_id_param: "lifecycle_app",
              auth_type: "bearer"
            },
            processes: {
              lifecycle_process: {
                request: {
                  schema: {
                    type: "object",
                    properties: {
                      user_id: { type: "string" }
                    }
                  }
                },
                execution: {
                  mock: {
                    enabled: true,
                    response: { status: "lifecycle_success" }
                  }
                }
              }
            },
            registration: {
              enabled: true,
              auto_registration: true
            },
            result: {
              verified_claims_mapping_rules: [
                {
                  from: "$.user_data.id",
                  to: "$.verified_claims.user_id",
                  convert_type: "string"
                }
              ]
            }
          }
        });

        expect(createResponse.status).toBe(201);
        expect(createResponse.data.result).toHaveProperty("id", configId);

        // 2. Retrieve the created config (GET detail)
        const getResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getResponse.status).toBe(200);
        expect(getResponse.data).toHaveProperty("id", configId);

        // 3. Update the config (PUT)
        const updateResponse = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            type: uuidv4(),
            attributes: {
              enabled: false,
              test_mode: false,
              updated_at: new Date().toISOString()
            },
            common: {
              auth_type: "hmac"
            }
          }
        });

        expect(updateResponse.status).toBe(200);
        expect(updateResponse.data.result).toHaveProperty("id");

        // 4. Verify the update (GET detail)
        const getUpdatedResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getUpdatedResponse.status).toBe(200);
        expect(getUpdatedResponse.data).toHaveProperty("id", configId);

        // 5. Delete the config (DELETE)
        const deleteResponse = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(deleteResponse.status).toBe(204);

        // 6. Verify deletion (GET detail should return 404)
        const getDeletedResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getDeletedResponse.status).toBe(404);
      });

      it("should successfully retrieve and paginate through identity verification configs", async () => {
        let offset = 0;
        const limit = 5;
        let hasMoreConfigs = true;
        let allConfigs = [];

        while (hasMoreConfigs && allConfigs.length < 20) { // Limit to prevent infinite loops
          const response = await get({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations?limit=${limit}&offset=${offset}`,
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