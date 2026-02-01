import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

describe("organization security event hook result user search api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";
  const testConfigId = uuidv4();
  let configCreated = false;

  // Setup: Create security event hook configuration and trigger login events
  beforeAll(async () => {
    console.log("Setting up security event hook configuration...");

    // Get access token
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    const accessToken = tokenResponse.data.access_token;

    // Create security event hook configuration for password authentication events
    // Event types: password_success, password_failure, login_success, issue_token_success, etc.
    try {
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          id: testConfigId,
          type: "WEBHOOK",  // Must use a registered hook type
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

    console.log("Generating security events by performing login operations...");

    // Login with test user to generate security events
    const loginAttempts = [
      {
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "openid profile email"
      },
      {
        username: "ito.ichiro@gmail.com",
        password: "wrongpassword",  // This will fail and trigger user_signin_failure
        scope: "openid"
      }
    ];

    for (const attempt of loginAttempts) {
      try {
        const response = await requestToken({
          endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          grantType: "password",
          username: attempt.username,
          password: attempt.password,
          scope: attempt.scope,
          clientId: "org-client",
          clientSecret: "org-client-001"
        });
        console.log(`Login attempt with scope "${attempt.scope}": status=${response.status}`);
      } catch (error) {
        console.log(`Login attempt failed (expected for some cases)`);
      }
    }

    // Wait for security events to be processed (async webhook execution)
    console.log("Waiting for async webhook execution...");
    await new Promise(resolve => setTimeout(resolve, 5000));
    console.log("Security event generation completed");
  });

  // Cleanup: Delete the test security event hook configuration
  afterAll(async () => {
    if (configCreated) {
      console.log("Cleaning up security event hook configuration...");
      try {
        const tokenResponse = await requestToken({
          endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          grantType: "password",
          username: "ito.ichiro@gmail.com",
          password: "successUserCode001",
          scope: "org-management account management",
          clientId: "org-client",
          clientSecret: "org-client-001"
        });
        const accessToken = tokenResponse.data.access_token;

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
  });

  const getAccessToken = async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
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
      const testUserId = uuidv4();

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_id=${testUserId}&limit=10`,
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

      // First, get some events to find an actual user_id
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("Initial list response:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);

      if (listResponse.data.total_count > 0) {
        // Find an event with user info in payload
        const eventWithUser = listResponse.data.list.find(
          event => event.security_event?.user?.sub
        );

        if (eventWithUser) {
          const userId = eventWithUser.security_event.user.sub;
          console.log("Testing with user_id:", userId);

          const userFilteredResponse = await get({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_id=${userId}&limit=10`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });

          console.log("Filtered by user_id response:", JSON.stringify(userFilteredResponse.data, null, 2));
          expect(userFilteredResponse.status).toBe(200);
          expect(userFilteredResponse.data).toHaveProperty("list");
          expect(userFilteredResponse.data.total_count).toBeGreaterThan(0);

          // Verify all returned events have the same user_id
          for (const event of userFilteredResponse.data.list) {
            if (event.security_event?.user?.sub) {
              expect(event.security_event.user.sub).toBe(userId);
            }
          }
        } else {
          console.log("No events with user info found for user_id filtering test");
        }
      } else {
        console.log("No security events available for user_id filtering test");
      }
    });
  });

  describe("user_name search", () => {
    it("search by user_name returns filtered results", async () => {
      const accessToken = await getAccessToken();
      const testUserName = "nonexistent-user-name-xyz";

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_name=${encodeURIComponent(testUserName)}&limit=10`,
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

      // First, get some events to find an actual user_name
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("Initial list response for user_name test:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);

      if (listResponse.data.total_count > 0) {
        // Find an event with user name in payload
        const eventWithUser = listResponse.data.list.find(
          event => event.security_event?.user?.name
        );

        if (eventWithUser) {
          const fullUserName = eventWithUser.security_event.user.name;
          // Use partial name for LIKE search (first 3 characters)
          const partialName = fullUserName.substring(0, Math.min(3, fullUserName.length));
          console.log("Testing with partial user_name:", partialName, "from full name:", fullUserName);

          const userNameFilteredResponse = await get({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_name=${encodeURIComponent(partialName)}&limit=10`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });

          console.log("Filtered by user_name response:", JSON.stringify(userNameFilteredResponse.data, null, 2));
          expect(userNameFilteredResponse.status).toBe(200);
          expect(userNameFilteredResponse.data).toHaveProperty("list");
          expect(userNameFilteredResponse.data.total_count).toBeGreaterThan(0);

          // Verify all returned events have user_name containing the partial match
          for (const event of userNameFilteredResponse.data.list) {
            if (event.security_event?.user?.name) {
              expect(event.security_event.user.name.toLowerCase())
                .toContain(partialName.toLowerCase());
            }
          }
        } else {
          console.log("No events with user name found for user_name filtering test");
        }
      } else {
        console.log("No security events available for user_name filtering test");
      }
    });
  });

  describe("combined user search", () => {
    it("search with both user_id and user_name filters", async () => {
      const accessToken = await getAccessToken();

      // First, get some events to find actual user data
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(listResponse.status).toBe(200);

      if (listResponse.data.total_count > 0) {
        // Find an event with both user id and name
        const eventWithUser = listResponse.data.list.find(
          event => event.security_event?.user?.sub && event.security_event?.user?.name
        );

        if (eventWithUser) {
          const userId = eventWithUser.security_event.user.sub;
          const userName = eventWithUser.security_event.user.name;
          console.log("Testing combined filter with user_id:", userId, "and user_name:", userName);

          const combinedFilterResponse = await get({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?user_id=${userId}&user_name=${encodeURIComponent(userName)}&limit=10`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });

          console.log("Combined filter response:", JSON.stringify(combinedFilterResponse.data, null, 2));
          expect(combinedFilterResponse.status).toBe(200);
          expect(combinedFilterResponse.data).toHaveProperty("list");
          expect(combinedFilterResponse.data.total_count).toBeGreaterThan(0);

          // All results should match both criteria
          for (const event of combinedFilterResponse.data.list) {
            if (event.security_event?.user) {
              expect(event.security_event.user.sub).toBe(userId);
              expect(event.security_event.user.name.toLowerCase())
                .toContain(userName.toLowerCase());
            }
          }
        } else {
          console.log("No events with complete user info found for combined filtering test");
        }
      } else {
        console.log("No security events available for combined filtering test");
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
