import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

describe("organization security event hook result user search api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";
  const testConfigId = uuidv4();
  const testUserId = uuidv4();
  const testUserEmail = `test-hook-search-${Date.now()}@example.com`;
  const testUserName = `HookSearchTestUser ${Date.now()}`;
  const testUserPassword = "TestPassword123!";
  let configCreated = false;
  let testUserCreated = false;

  const getAdminAccessToken = async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    return tokenResponse.data.access_token;
  };

  // Setup: Create test user, security event hook configuration, and trigger login events
  beforeAll(async () => {
    const accessToken = await getAdminAccessToken();

    // Step 1: Create a dedicated test user
    console.log("Creating dedicated test user...");
    try {
      const createUserResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          sub: testUserId,
          provider_id: "idp-server",
          name: testUserName,
          email: testUserEmail,
          raw_password: testUserPassword
        }
      });
      console.log("Test user created:", createUserResponse.status);
      if (createUserResponse.status === 201) {
        testUserCreated = true;
      }
    } catch (error) {
      console.log("Failed to create test user:", error.message);
    }

    // Step 2: Create security event hook configuration
    console.log("Setting up security event hook configuration...");
    try {
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          id: testConfigId,
          type: "WEBHOOK",
          triggers: ["password_success", "password_failure", "login_success", "issue_token_success"],
          events: {
            password_success: {
              execution: {
                function: "http_request",
                http_request: {
                  url: "https://webhook.example.com/test",
                  method: "POST",
                  auth_type: "NONE"
                }
              }
            },
            password_failure: {
              execution: {
                function: "http_request",
                http_request: {
                  url: "https://webhook.example.com/test",
                  method: "POST",
                  auth_type: "NONE"
                }
              }
            },
            login_success: {
              execution: {
                function: "http_request",
                http_request: {
                  url: "https://webhook.example.com/test",
                  method: "POST",
                  auth_type: "NONE"
                }
              }
            },
            issue_token_success: {
              execution: {
                function: "http_request",
                http_request: {
                  url: "https://webhook.example.com/test",
                  method: "POST",
                  auth_type: "NONE"
                }
              }
            }
          },
          enabled: true
        }
      });
      console.log("Security event hook config created:", createResponse.status);
      if (createResponse.status === 201) {
        configCreated = true;
      }
    } catch (error) {
      console.log("Failed to create security event hook config:", error.message);
    }

    // Wait for config to be active
    await new Promise(resolve => setTimeout(resolve, 500));

    // Step 3: Generate security events using the dedicated test user
    console.log("Generating security events by performing login operations...");

    // Successful login
    try {
      const response = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: testUserEmail,
        password: testUserPassword,
        scope: "openid profile email",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log(`Login success attempt: status=${response.status}`);
    } catch (error) {
      console.log(`Login success attempt failed: ${error.message}`);
    }

    // Failed login (wrong password) - safe because this is a dedicated test user
    try {
      const response = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: testUserEmail,
        password: "wrongpassword",
        scope: "openid",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log(`Login failure attempt: status=${response.status}`);
    } catch (error) {
      console.log(`Login failure attempt failed (expected)`);
    }

    // Wait for security events to be processed (async webhook execution)
    console.log("Waiting for async webhook execution...");
    await new Promise(resolve => setTimeout(resolve, 5000));
    console.log("Security event generation completed");
  });

  // Cleanup: Delete the test security event hook configuration and test user
  afterAll(async () => {
    try {
      const accessToken = await getAdminAccessToken();

      if (configCreated) {
        console.log("Cleaning up security event hook configuration...");
        try {
          await deletion({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations/${testConfigId}`,
            headers: {
              Authorization: `Bearer ${accessToken}`,
            }
          });
          console.log("Security event hook config deleted");
        } catch (error) {
          console.log("Failed to delete security event hook config:", error.message);
        }
      }

      if (testUserCreated) {
        console.log("Cleaning up test user...");
        try {
          await deletion({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${testUserId}`,
            headers: {
              Authorization: `Bearer ${accessToken}`,
            }
          });
          console.log("Test user deleted");
        } catch (error) {
          console.log("Failed to delete test user:", error.message);
        }
      }
    } catch (error) {
      console.log("Failed to get access token for cleanup:", error.message);
    }
  });

  const getAccessToken = async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    expect(tokenResponse.status).toBe(200);
    return tokenResponse.data.access_token;
  };

  describe("user_id search", () => {
    it("search by user_id returns filtered results", async () => {
      const accessToken = await getAccessToken();
      const nonExistentUserId = uuidv4();

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_id=${nonExistentUserId}&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("User ID search response:", JSON.stringify(response.data, null, 2));
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(Array.isArray(response.data.list)).toBe(true);
      expect(response.data).toHaveProperty("total_count");
      // With a random UUID, we expect 0 results
      expect(response.data.total_count).toBe(0);
    });

    it("search by user_id with existing user returns results", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_id=${testUserId}&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("Filtered by user_id response:", JSON.stringify(response.data, null, 2));
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data.total_count).toBeGreaterThan(0);

      // Verify all returned events have the same user_id
      for (const event of response.data.list) {
        if (event.security_event?.user?.sub) {
          expect(event.security_event.user.sub).toBe(testUserId);
        }
      }
    });
  });

  describe("user_name search", () => {
    it("search by user_name returns filtered results", async () => {
      const accessToken = await getAccessToken();
      const testNonExistentUserName = "nonexistent-user-name-xyz";

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_name=${encodeURIComponent(testNonExistentUserName)}&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("User name search response:", JSON.stringify(response.data, null, 2));
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(Array.isArray(response.data.list)).toBe(true);
      expect(response.data).toHaveProperty("total_count");
      // With a non-existent name, we expect 0 results
      expect(response.data.total_count).toBe(0);
    });

    it("search by user_name with partial match", async () => {
      const accessToken = await getAccessToken();

      // Use "test-hook-search" as partial match - the security event records email as user name
      const partialName = "test-hook-search";

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_name=${encodeURIComponent(partialName)}&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("Filtered by user_name response:", JSON.stringify(response.data, null, 2));
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data.total_count).toBeGreaterThan(0);

      // Verify all returned events have user_name containing the partial match
      for (const event of response.data.list) {
        if (event.security_event?.user?.name) {
          expect(event.security_event.user.name.toLowerCase())
            .toContain(partialName.toLowerCase());
        }
      }
    });
  });

  describe("combined user search", () => {
    it("search with both user_id and user_name filters", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_id=${testUserId}&user_name=${encodeURIComponent("test-hook-search")}&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("Combined filter response:", JSON.stringify(response.data, null, 2));
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data.total_count).toBeGreaterThan(0);

      // All results should match both criteria
      for (const event of response.data.list) {
        if (event.security_event?.user) {
          expect(event.security_event.user.sub).toBe(testUserId);
          expect(event.security_event.user.name.toLowerCase())
            .toContain("test-hook-search");
        }
      }
    });
  });

  describe("edge cases", () => {
    it("empty user_id returns all results", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_id=&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("Empty user_id response:", JSON.stringify(response.data, null, 2));
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(Array.isArray(response.data.list)).toBe(true);
    });

    it("empty user_name returns all results", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_name=&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("Empty user_name response:", JSON.stringify(response.data, null, 2));
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(Array.isArray(response.data.list)).toBe(true);
    });
  });
});
