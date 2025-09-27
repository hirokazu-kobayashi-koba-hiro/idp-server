import { describe, expect, it, test, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

describe("Organization Authentication Policy Config Management API Test", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  let authHeader;

  beforeAll(async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    authHeader = `Bearer ${tokenResponse.data.access_token}`;
  });

  // E2E Test for Authentication Policy Config Management
  test("should successfully manage authentication policy configurations for organization", async () => {
    const configId = uuidv4();

    // Test CREATE
    const createRequest = {
      id: configId,
      flow: uuidv4(), // Use UUID for unique flow
      enabled: true,
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
      ]
    };

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: createRequest,
    });

    console.log(createResponse.data);
    expect(createResponse.status).toBe(201);
    expect(createResponse.data).toHaveProperty("dry_run", false);
    expect(createResponse.data).toHaveProperty("result");
    expect(createResponse.data.result).toHaveProperty("id", configId);

    // Test GET LIST
    const listResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(listResponse.status).toBe(200);
    expect(Array.isArray(listResponse.data.list)).toBe(true);
    const configIds = listResponse.data.list.map((config) => config.id);
    expect(configIds).toContain(configId);

    // Test GET by ID
    const getResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(getResponse.status).toBe(200);
    expect(getResponse.data).toHaveProperty("id", configId);
    expect(getResponse.data).toHaveProperty("flow");
    expect(getResponse.data).toHaveProperty("enabled", true);

    // Test UPDATE
    const updateRequest = {
      id: configId,
      flow: uuidv4(),
      enabled: true,
      policies: [
        {
          description: "updated_password_policy",
          priority: 1,
          conditions: {
            scopes: ["openid"]
          },
          available_methods: ["password", "email"],
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
      ]
    };

    const updateResponse = await putWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: updateRequest,
    });

    expect(updateResponse.status).toBe(200);
    expect(updateResponse.data).toHaveProperty("dry_run", false);
    expect(updateResponse.data).toHaveProperty("result");
    expect(updateResponse.data.result).toHaveProperty("policies");
    expect(Array.isArray(updateResponse.data.result.policies)).toBe(true);

    // Test DELETE
    const deleteResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(deleteResponse.status).toBe(204);

    // Verify deletion by attempting to get the deleted config
    const verifyDeleteResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(verifyDeleteResponse.status).toBe(404);
  });

  test("should support dry-run for create operation", async () => {
    const configId = uuidv4();

    const createRequest = {
      id: configId,
      flow: uuidv4(),
      enabled: false,
      policies: [
        {
          description: "mfa_requirement_policy",
          priority: 1,
          conditions: {
            scopes: ["openid"]
          },
          available_methods: ["password", "webauthn"],
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
      ]
    };

    const dryRunResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies?dry_run=true`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: createRequest,
    });

    expect(dryRunResponse.status).toBe(201);
    expect(dryRunResponse.data).toHaveProperty("dry_run", true);
    expect(dryRunResponse.data).toHaveProperty("result");
    expect(dryRunResponse.data.result).toHaveProperty("id", configId);

    // Verify that dry-run didn't actually create the config
    const verifyResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(verifyResponse.status).toBe(404);
  });

  test("should support dry-run for update operation", async () => {
    const configId = uuidv4();

    // First create a config
    const createRequest = {
      id: configId,
      flow: uuidv4(),
      enabled: true,
      policies: [
        {
          description: "session_management_policy",
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
      ]
    };

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: createRequest,
    });

    // Test dry-run update
    const updateRequest = {
      id: configId,
      flow: uuidv4(),
      enabled: true,
      policies: [
        {
          description: "extended_session_management_policy",
          priority: 1,
          conditions: {
            scopes: ["openid"]
          },
          available_methods: ["password", "email"],
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
      ]
    };

    const dryRunUpdateResponse = await putWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}?dry_run=true`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: updateRequest,
    });

    expect(dryRunUpdateResponse.status).toBe(200);
    expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);

    // Verify original config is unchanged
    const verifyResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(verifyResponse.status).toBe(200);
    expect(verifyResponse.data).toHaveProperty("policies");
    expect(Array.isArray(verifyResponse.data.policies)).toBe(true);
    expect(verifyResponse.data.policies.length).toBeGreaterThan(0);

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });
  });

  test("should support dry-run for delete operation", async () => {
    const configId = uuidv4();

    // First create a config
    const createRequest = {
      id: configId,
      flow: uuidv4(),
      enabled: true,
      policies: [
        {
          description: "account_lockout_policy",
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
          },
          failure_conditions: {
            any_of: [
              [
                {
                  path: "$.password-authentication.failure_count",
                  type: "integer",
                  operation: "gte",
                  value: 5
                }
              ]
            ]
          }
        }
      ]
    };

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: createRequest,
    });

    // Test dry-run delete
    const dryRunDeleteResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}?dry_run=true`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(dryRunDeleteResponse.status).toBe(200);
    expect(dryRunDeleteResponse.data).toHaveProperty("message");
    expect(dryRunDeleteResponse.data.message).toMatch(/simulated successfully/);

    // Verify config still exists after dry-run delete
    const verifyResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(verifyResponse.status).toBe(200);
    expect(verifyResponse.data).toHaveProperty("id", configId);

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });
  });

  test("should handle non-existent authentication policy config", async () => {
    const nonExistentId = uuidv4();

    const getResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${nonExistentId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(getResponse.status).toBe(404);

    const updateResponse = await putWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${nonExistentId}`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: {
        id: nonExistentId,
        type: "non-existent",
        policy_type: "test",
        rules: {},
      },
    });

    expect(updateResponse.status).toBe(404);

    const deleteResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${nonExistentId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(deleteResponse.status).toBe(404);
  });

  test("should support pagination for authentication policy config list", async () => {
    const configIds = [];

    // Create multiple configs
    for (let i = 0; i < 3; i++) {
      const configId = uuidv4();
      configIds.push(configId);

      const createRequest = {
        id: configId,
        type: configId,
        policy_type: `test_policy_${i}`,
        rules: { test_value: i },
        description: `Test policy ${i}`,
        enabled: true,
      };

      await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
        headers: {
          Authorization: authHeader,
          "Content-Type": "application/json",
        },
        body: createRequest,
      });
    }

    // Test pagination
    const paginatedResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies?limit=2&offset=0`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(paginatedResponse.status).toBe(200);
    expect(paginatedResponse.data).toHaveProperty("limit", 2);
    expect(paginatedResponse.data).toHaveProperty("offset", 0);
    expect(paginatedResponse.data).toHaveProperty("total_count");
    expect(Array.isArray(paginatedResponse.data.list)).toBe(true);

    // Cleanup
    for (const configId of configIds) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies/${configId}`,
        headers: {
          Authorization: authHeader,
        },
      });
    }
  });
});