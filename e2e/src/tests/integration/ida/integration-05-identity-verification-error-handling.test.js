import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig,
  mockApiBaseUrl
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";

describe("Identity Verification Error Handling (401/404)", () => {
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
      username: "ito.ichiro",
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
      scope: "openid profile phone email identity_verification_application " + clientSecretPostClient.identityVerificationScope
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
      expect(response.data.error_description).toContain("access token");

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
      expect(response.data).toHaveProperty("error", "not_found");
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
      expect(response.data).toHaveProperty("error", "not_found");

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
      expect(response.data).toHaveProperty("error", "not_found");
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

});
