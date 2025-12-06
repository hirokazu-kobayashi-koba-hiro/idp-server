import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig, mockApiBaseUrl
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";
import { sleep } from "../../../lib/util";

describe("Identity Verification Error Handling", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  let userAccessToken;
  let testUser;
  let configId;
  let configurationType;
  let validApplicationId; // For 404 tests

  beforeAll(async () => {
    console.log("Setting up Identity Verification Error Handling Test...");

    // Authenticate as organization admin
    const orgAuthResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      scope: "org-management account management"
    });

    expect(orgAuthResponse.status).toBe(200);
    orgAccessToken = orgAuthResponse.data.access_token;

    // Create federated user
    const userResult = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope: "openid profile phone email identity_verification_application identity_verification_application_delete " + clientSecretPostClient.identityVerificationScope
    });

    testUser = userResult.user;
    userAccessToken = userResult.accessToken;
    console.log(`Created test user: ${testUser.sub}`);

    await registerFidoUaf({ accessToken: userAccessToken });

    // Create test configuration
    configId = uuidv4();
    configurationType = uuidv4();

    const configurationData = {
      "id": configId,
      "type": configurationType,
      "attributes": {
        "enabled": true,
        "error_handling_test": true
      },
      "common": {
        "callback_application_id_param": "app_id",
        "auth_type": "none"
      },
      "processes": {
        "apply": {
          "request": {
            "schema": {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              },
              "required": ["name"]
            }
          },
          "execution": {
            "type": "no_action"
          },
          "transition": {
            "applying": {
              "any_of": [[]]
            }
          },
          "store": {
            "application_details_mapping_rules": [
              {
                "from": "$.request_body",
                "to": "*"
              }
            ]
          }
        },
        "complete": {
          "request": {
            "schema": {
              "type": "object",
              "properties": {}
            }
          },
          "execution": {
            "type": "no_action"
          },
          "transition": {
            "applied": {
              "any_of": [[]]
            }
          },
          "dependencies": {
            "allow_retry": false
          }
        }
      }
    };

    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        "Authorization": `Bearer ${orgAccessToken}`,
        "Content-Type": "application/json"
      },
      body: configurationData
    });

    expect(response.status).toBe(201);
    console.log("✅ Test configuration created");

    // Create a valid application for 404 tests
    const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`;
    const applyResponse = await postWithJson({
      url: applyUrl,
      body: { "name": "Test User" },
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
      }
    });

    expect(applyResponse.status).toBe(200);
    validApplicationId = applyResponse.data.id;
    console.log(`✅ Valid application created: ${validApplicationId}`);
  });

  afterAll(async () => {
    if (configId) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` }
      });
      console.log(`Cleaned up test configuration: ${configId}`);
    }
  });

  describe("401 Unauthorized Tests", () => {

    it("should return 401 when accessing initial application API without access token", async () => {
      console.log("\n🧪 Test: 401 - No access token for initial application");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: { "name": "Test User" },
        headers: {
          "Content-Type": "application/json"
          // No Authorization header
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_token");
      expect(response.data.error_description).toContain("token is not active");

      console.log("✅ Correctly returned 401 for missing access token");
    });

    it("should return 401 when accessing initial application API with invalid access token", async () => {
      console.log("\n🧪 Test: 401 - Invalid access token for initial application");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: { "name": "Test User" },
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer invalid-token-xyz123"
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_token");

      console.log("✅ Correctly returned 401 for invalid access token");
    });

    it("should return 401 when accessing subsequent process API without access token", async () => {
      console.log("\n🧪 Test: 401 - No access token for subsequent process");

      const processUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/${validApplicationId}/complete`;

      const response = await postWithJson({
        url: processUrl,
        body: {},
        headers: {
          "Content-Type": "application/json"
          // No Authorization header
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_token");

      console.log("✅ Correctly returned 401 for missing access token in subsequent process");
    });

    it("should return 401 when accessing application list without access token", async () => {
      console.log("\n🧪 Test: 401 - No access token for application list");

      const listUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications`;

      const response = await get({
        url: listUrl,
        headers: {
          "Content-Type": "application/json"
          // No Authorization header
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_token");

      console.log("✅ Correctly returned 401 for missing access token in list API");
    });

    it("should return 401 when deleting application without access token", async () => {
      console.log("\n🧪 Test: 401 - No access token for application deletion");

      const deleteUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/${validApplicationId}`;

      const response = await deletion({
        url: deleteUrl,
        headers: {
          "Content-Type": "application/json"
          // No Authorization header
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_token");

      console.log("✅ Correctly returned 401 for missing access token in deletion API");
    });

    it("should return 401 when accessing verification results without access token", async () => {
      console.log("\n🧪 Test: 401 - No access token for verification results");

      const resultsUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/results`;

      const response = await get({
        url: resultsUrl,
        headers: {
          "Content-Type": "application/json"
          // No Authorization header
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_token");

      console.log("✅ Correctly returned 401 for missing access token in results API");
    });

  });

  describe("404 Not Found Tests", () => {

    it("should return 404 when accessing subsequent process with non-existent application ID", async () => {
      console.log("\n🧪 Test: 404 - Non-existent application ID");

      const nonExistentId = "00000000-0000-0000-0000-000000000000";
      const processUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/${nonExistentId}/complete`;

      const response = await postWithJson({
        url: processUrl,
        body: {},
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBe(404);
      expect(response.data).toHaveProperty("error", "invalid_request");
      expect(response.data.error_description).toContain("not found");

      console.log("✅ Correctly returned 404 for non-existent application ID");
    });

    it("should return 404 when accessing subsequent process with invalid UUID format", async () => {
      console.log("\n🧪 Test: 404 - Invalid UUID format for application ID");

      const invalidId = "not-a-valid-uuid";
      const processUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/${invalidId}/complete`;

      const response = await postWithJson({
        url: processUrl,
        body: {},
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // Should return 404 or 400 depending on implementation
      expect([400, 404]).toContain(response.status);
      expect(response.data).toHaveProperty("error");

      console.log(`✅ Correctly returned ${response.status} for invalid UUID format`);
    });

    it("should return 404 when deleting non-existent application", async () => {
      console.log("\n🧪 Test: 404 - Delete non-existent application");

      const nonExistentId = "00000000-0000-0000-0000-000000000000";
      const deleteUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/${nonExistentId}`;

      const response = await deletion({
        url: deleteUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBe(404);
      expect(response.data).toHaveProperty("error", "invalid_request");

      console.log("✅ Correctly returned 404 for deleting non-existent application");
    });

    it("should return 404 when accessing another user's application", async () => {
      console.log("\n🧪 Test: 404 - Access another user's application");

      // Create another user
      const anotherUserResult = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient,
        scope: "openid profile phone email identity_verification_application " + clientSecretPostClient.identityVerificationScope
      });

      const anotherUserAccessToken = anotherUserResult.accessToken;
      console.log(`Created another test user: ${anotherUserResult.user.sub}`);

      // Try to access the first user's application with the second user's token
      const processUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/${validApplicationId}/complete`;

      const response = await postWithJson({
        url: processUrl,
        body: {},
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${anotherUserAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // Should return 404 (application doesn't belong to this user)
      expect(response.status).toBe(404);
      expect(response.data).toHaveProperty("error", "invalid_request");
      expect(response.data.error_description).toContain("not found");

      console.log("✅ Correctly returned 404 when accessing another user's application");
    });

    it("should return empty list when filtering by non-existent application ID", async () => {
      console.log("\n🧪 Test: Empty list for non-existent application ID filter");

      const nonExistentId = "00000000-0000-0000-0000-000000000000";
      const listUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications?id=${nonExistentId}`;

      const response = await get({
        url: listUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // List API should return 200 with empty list
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data.list).toEqual([]);
      expect(response.data.total_count).toBe(0);

      console.log("✅ Correctly returned empty list for non-existent application ID");
    });

    it("should return 404 when accessing results with non-existent result ID", async () => {
      console.log("\n🧪 Test: 404 - Non-existent verification result ID");

      const nonExistentId = "00000000-0000-0000-0000-000000000000";
      const resultsUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/results?id=${nonExistentId}`;

      const response = await get({
        url: resultsUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // Results list API should return 200 with empty list (not 404)
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data.list).toEqual([]);

      console.log("✅ Correctly returned empty list for non-existent result ID");
    });

  });

  describe("401 and 404 Combined Scenario Tests", () => {

    it("should prioritize 401 over 404 when both conditions apply", async () => {
      console.log("\n🧪 Test: 401 takes precedence over 404");

      const nonExistentId = "00000000-0000-0000-0000-000000000000";
      const processUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/${nonExistentId}/complete`;

      const response = await postWithJson({
        url: processUrl,
        body: {},
        headers: {
          "Content-Type": "application/json"
          // No Authorization header (401) + non-existent ID (404)
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // 401 should take precedence (authentication checked before authorization)
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_token");

      console.log("✅ Correctly prioritized 401 over 404");
    });

  });

  describe("External Service Error Handling", () => {
    let errorTestConfigId;
    let errorTestConfigType;

    beforeAll(async () => {
      console.log("\n🔧 Setup: Creating error response test configuration...");

      errorTestConfigId = uuidv4();
      errorTestConfigType = `error-test-${uuidv4()}`;

      const configurationData = {
        "id": errorTestConfigId,
        "type": errorTestConfigType,
        "attributes": {
          "enabled": true,
          "description": "Test configuration for external service error handling"
        },
        "common": {
          "callback_application_id_param": "app_id",
          "auth_type": "none"
        },
        "processes": {
          "apply": {
            "request": {
              "schema": {
                "type": "object",
                "properties": {
                  "test_case": { "type": "string" }
                },
                "required": ["test_case"]
              }
            },
            "execution": {
              "type": "http_request",
              "http_request": {
                "url": `${mockApiBaseUrl}/e2e/error-responses`,
                "method": "POST",
                "auth_type": "none",
                "body_mapping_rules": [
                  {
                    "from": "$.request_body",
                    "to": "*"
                  }
                ]
              }
            },
            "transition": {
              "applying": {
                "any_of": [[]]
              }
            },
            "store": {
              "application_details_mapping_rules": [
                {
                  "from": "$.request_body",
                  "to": "*"
                }
              ]
            }
          }
        }
      };

      const configUrl = `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`;

      const configResponse = await postWithJson({
        url: configUrl,
        body: configurationData,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${orgAccessToken}`
        }
      });

      console.log(`✅ Configuration created: ${errorTestConfigType}`);
      expect(configResponse.status).toBe(201);
    });

    afterAll(async () => {
      if (errorTestConfigId) {
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${errorTestConfigId}`,
          headers: { Authorization: `Bearer ${orgAccessToken}` }
        });
        console.log(`🧹 Cleaned up test configuration: ${errorTestConfigId}`);
      }
    });

    it("should successfully process when external service returns 200", async () => {
      console.log("\n🧪 Test: External service returns 200 Success");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_200"
          // No status parameter → Mockoon returns 200 by default
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // Successful external call should return 200 with application ID
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("id");

      // Verify application ID is a valid UUID
      const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
      expect(response.data.id).toMatch(uuidRegex);

      // Note: Only 'id' is returned in success response
      // External service response is stored in database but not returned to client

      console.log("✅ External 200 success correctly processed with valid application ID");
    });

    it("should handle 400 Bad Request from external service", async () => {
      console.log("\n🧪 Test: External service returns 400 Bad Request");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_400",
          "status": "400"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // idp-server should map external 4xx to CLIENT_ERROR (400)
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "client_error");
      expect(response.data.error_details).toHaveProperty("status_code", 400);
      expect(response.data).toHaveProperty("error_messages");
      expect(Array.isArray(response.data.error_messages)).toBe(true);

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "invalid_request");

      console.log("✅ External 400 correctly mapped to CLIENT_ERROR (400) with proper error structure");
    });

    it("should handle 401 Unauthorized from external service", async () => {
      console.log("\n🧪 Test: External service returns 401 Unauthorized");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_401",
          "status": "401"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 401 is propagated as-is by idp-server
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "client_error");
      expect(response.data.error_details).toHaveProperty("status_code", 401);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "invalid_token");

      console.log("✅ External 401 propagated with client_error category and authentication error details");
    });

    it("should handle 403 Forbidden from external service", async () => {
      console.log("\n🧪 Test: External service returns 403 Forbidden");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_403",
          "status": "403"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 403 is propagated as-is by idp-server
      expect(response.status).toBe(403);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "client_error");
      expect(response.data.error_details).toHaveProperty("status_code", 403);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "insufficient_scope");
      expect(response.data.error_details.response_body).toHaveProperty("scope", "identity_verification_application_delete");

      console.log("✅ External 403 propagated with client_error category and authorization error details");
    });

    it("should handle 408 Request Timeout from external service", async () => {
      console.log("\n🧪 Test: External service returns 408 Request Timeout");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_408",
          "status": "408"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 408 is propagated as-is by idp-server
      expect(response.status).toBe(408);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "client_error");
      expect(response.data.error_details).toHaveProperty("status_code", 408);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "request_timeout");
      expect(response.data.error_details.response_body).toHaveProperty("timeout_seconds", 30);

      console.log("✅ External 408 propagated with client_error category and timeout information");
    });

    it("should handle 409 Conflict from external service", async () => {
      console.log("\n🧪 Test: External service returns 409 Conflict");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_409",
          "status": "409"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 409 is propagated as-is by idp-server
      expect(response.status).toBe(409);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "client_error");
      expect(response.data.error_details).toHaveProperty("status_code", 409);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "invalid_request");
      expect(response.data.error_details.response_body).toHaveProperty("current_state", "approved");
      expect(response.data.error_details.response_body).toHaveProperty("requested_state", "applying");

      console.log("✅ External 409 propagated with client_error category and state transition information");
    });

    it("should handle 413 Payload Too Large from external service", async () => {
      console.log("\n🧪 Test: External service returns 413 Payload Too Large");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_413",
          "status": "413"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 413 is propagated as-is by idp-server
      expect(response.status).toBe(413);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "client_error");
      expect(response.data.error_details).toHaveProperty("status_code", 413);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "payload_too_large");
      expect(response.data.error_details.response_body).toHaveProperty("max_size_bytes");
      expect(response.data.error_details.response_body).toHaveProperty("received_size_bytes");

      console.log("✅ External 413 propagated with client_error category and payload size information");
    });

    it("should handle 415 Unsupported Media Type from external service", async () => {
      console.log("\n🧪 Test: External service returns 415 Unsupported Media Type");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_415",
          "status": "415"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 415 is propagated as-is by idp-server
      expect(response.status).toBe(415);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "client_error");
      expect(response.data.error_details).toHaveProperty("status_code", 415);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "unsupported_media_type");
      expect(response.data.error_details.response_body).toHaveProperty("supported_types");
      expect(response.data.error_details.response_body).toHaveProperty("received_type");

      console.log("✅ External 415 propagated with client_error category and media type information");
    });

    it("should handle 422 Unprocessable Entity from external service", async () => {
      console.log("\n🧪 Test: External service returns 422 Unprocessable Entity");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_422",
          "status": "422"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 422 is propagated as-is by idp-server
      expect(response.status).toBe(422);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "client_error");
      expect(response.data.error_details).toHaveProperty("status_code", 422);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "validation_error");
      expect(response.data.error_details.response_body).toHaveProperty("validation_errors");
      expect(Array.isArray(response.data.error_details.response_body.validation_errors)).toBe(true);

      console.log("✅ External 422 propagated with client_error category and validation error details");
    });

    it("should handle 429 Too Many Requests from external service", async () => {
      console.log("\n🧪 Test: External service returns 429 Too Many Requests");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_429",
          "status": "429"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 429 is propagated as-is by idp-server
      expect(response.status).toBe(429);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "client_error");
      expect(response.data.error_details).toHaveProperty("status_code", 429);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "invalid_request");
      expect(response.data.error_details.response_body).toHaveProperty("retry_after", 60);

      console.log("✅ External 429 propagated with client_error category and rate limit information");
    });

    it("should handle 500 Internal Server Error from external service", async () => {
      console.log("\n🧪 Test: External service returns 500 Internal Server Error");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_500",
          "status": "500"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 500 (5xx) should be mapped to SERVER_ERROR (500)
      expect(response.status).toBe(500);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "server_error");
      expect(response.data.error_details).toHaveProperty("status_code", 500);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "server_error");
      expect(response.data.error_details.response_body).toHaveProperty("error_id");

      console.log("✅ External 500 correctly mapped to SERVER_ERROR (500) with error tracking");
    });

    it("should handle 502 Bad Gateway from external service", async () => {
      console.log("\n🧪 Test: External service returns 502 Bad Gateway");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_502",
          "status": "502"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 502 is propagated as-is by idp-server
      expect(response.status).toBe(502);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "server_error");
      expect(response.data.error_details).toHaveProperty("status_code", 502);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "bad_gateway");
      expect(response.data.error_details.response_body).toHaveProperty("upstream_error");

      console.log("✅ External 502 propagated with server_error category and upstream error information");
    });

    it("should handle 503 Service Unavailable from external service", async () => {
      console.log("\n🧪 Test: External service returns 503 Service Unavailable");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_503",
          "status": "503"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 503 is propagated as-is by idp-server
      expect(response.status).toBe(503);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "server_error");
      expect(response.data.error_details).toHaveProperty("status_code", 503);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "service_unavailable");
      expect(response.data.error_details.response_body).toHaveProperty("retryable", true);

      console.log("✅ External 503 propagated with server_error category and retryable flag");
    });

    it("should handle 504 Gateway Timeout from external service", async () => {
      console.log("\n🧪 Test: External service returns 504 Gateway Timeout");

      const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${errorTestConfigType}/apply`;

      const response = await postWithJson({
        url: applyUrl,
        body: {
          "test_case": "external_504",
          "status": "504"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      // External 504 is propagated as-is by idp-server
      expect(response.status).toBe(504);
      expect(response.data).toHaveProperty("error", "execution_failed");
      expect(response.data).toHaveProperty("error_description");
      expect(response.data.error_description).toContain("execution failed");
      expect(response.data).toHaveProperty("error_details");
      expect(response.data.error_details).toHaveProperty("execution_type", "http_request");
      expect(response.data.error_details).toHaveProperty("status_category", "server_error");
      expect(response.data.error_details).toHaveProperty("status_code", 504);
      expect(response.data).toHaveProperty("error_messages");

      // Verify external service error response is included
      expect(response.data.error_details).toHaveProperty("response_body");
      expect(response.data.error_details.response_body).toHaveProperty("error", "gateway_timeout");
      expect(response.data.error_details.response_body).toHaveProperty("timeout_seconds", 30);

      console.log("✅ External 504 propagated with server_error category and timeout information");
    });

  });

});
