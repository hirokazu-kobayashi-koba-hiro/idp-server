import { describe, expect, it } from "@jest/globals";
import { requestToken } from "../../../api/oauthClient";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../testConfig";
import { post, postWithJson } from "../../../lib/http";
import { faker } from "@faker-js/faker";

/**
 * Password Change API E2E Test
 *
 * API: POST /{tenant-id}/v1/me/password/change
 * Requires: Resource owner access token
 */
describe("Password Change API", () => {
  const passwordChangeUrl = `${backendUrl}/${serverConfig.tenantId}/v1/me/password/change`;

  /**
   * Helper function to create a new user with password via Management API
   */
  const createUserWithPassword = async (initialPassword) => {
   const email =  faker.internet.email();
    const testUser = {
      email: email,
      name: email,
    };

    // Get admin access token
    const adminTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret
    });
    expect(adminTokenResponse.status).toBe(200);
    const adminAccessToken = adminTokenResponse.data.access_token;

    // Create user with password via Management API
    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/users`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        "provider_id": "idp-server",
        "name": testUser.name,
        "email": testUser.email,
        "raw_password": initialPassword
      }
    });
    expect(createResponse.status).toBe(201);
    console.log("Created user:", createResponse.data);

    // Get access token for the created user using Resource Owner Password Credentials
    const userTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: testUser.email,
      password: initialPassword,
      scope: "openid profile email",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(JSON.stringify(userTokenResponse.data, null, 2));
    expect(userTokenResponse.status).toBe(200);

    return {
      user: testUser,
      accessToken: userTokenResponse.data.access_token,
    };
  };

  /**
   * Helper function to call password change API
   */
  const changePassword = async (accessToken, currentPassword, newPassword) => {
    return await post({
      url: passwordChangeUrl,
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`,
      },
      body: {
        current_password: currentPassword,
        new_password: newPassword,
      },
    });
  };

  describe("Success scenarios", () => {
    it("should successfully change password and login with new password", async () => {
      const initialPassword = "InitialPass123";
      const newPassword = "NewPassword456";

      // 1. Create user with initial password
      const { user, accessToken } = await createUserWithPassword(initialPassword);
      console.log("Created user:", user.email);

      // 2. Change password
      const response = await changePassword(accessToken, initialPassword, newPassword);
      console.log("Password change response:", response.data);

      expect(response.status).toBe(200);
      expect(response.data.message).toEqual("Password changed successfully.");

      // 3. Verify: Can login with new password
      const newTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: user.email,
        password: newPassword,
        scope: "openid profile email",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log("Login with new password:", newTokenResponse.status);
      expect(newTokenResponse.status).toBe(200);
      expect(newTokenResponse.data).toHaveProperty("access_token");

      // 4. Verify: Cannot login with old password (Issue #896)
      const oldPasswordResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: user.email,
        password: initialPassword,
        scope: "openid profile email",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(JSON.stringify(oldPasswordResponse.data, null, 2));
      console.log("Login with old password:", oldPasswordResponse.status);
      expect(oldPasswordResponse.status).toBe(400);
      expect(oldPasswordResponse.data.error).toEqual("invalid_grant");
      expect(oldPasswordResponse.data.error_description).toContain("does not found user by token request, or invalid password");
    });
  });

  describe("Validation errors", () => {
    it("should reject when current_password is missing", async () => {
      const { accessToken } = await createUserWithPassword("TestPass123");

      const response = await changePassword(accessToken, "", "NewPassword123");
      console.log("Missing current_password response:", response.data);

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_request");
      expect(response.data.error_description).toContain("Current password is required");
    });

    it("should reject when new_password is missing", async () => {
      const { accessToken } = await createUserWithPassword("TestPass123");

      const response = await changePassword(accessToken, "TestPass123", "");
      console.log("Missing new_password response:", response.data);

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_request");
      expect(response.data.error_description).toContain("New password is required");
    });

    it("should reject when current_password is incorrect", async () => {
      const { accessToken } = await createUserWithPassword("TestPass123");

      const response = await changePassword(accessToken, "WrongPassword123", "NewPassword123");
      console.log("Incorrect current_password response:", response.data);

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_current_password");
      expect(response.data.error_description).toEqual("Current password is incorrect.");
    });
  });

  describe("Password policy validation", () => {
    it("should reject password shorter than 8 characters (default policy)", async () => {
      const initialPassword = "TestPass123";
      const { accessToken } = await createUserWithPassword(initialPassword);

      const response = await changePassword(accessToken, initialPassword, "Short1");
      console.log("Short password response:", response.data);

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_new_password");
      expect(response.data.error_description).toContain("at least 8 characters");
    });

    it("should accept password with exactly 8 characters", async () => {
      const initialPassword = "TestPass123";
      const { user, accessToken } = await createUserWithPassword(initialPassword);

      const response = await changePassword(accessToken, initialPassword, "Valid123");
      console.log("8-character password response:", response.data);

      expect(response.status).toBe(200);

      // Verify: Can login with new 8-character password
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: user.email,
        password: "Valid123",
        scope: "openid profile email",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
    });
  });

  describe("Authorization", () => {
    it("should reject request without access token", async () => {
      const response = await post({
        url: passwordChangeUrl,
        headers: {
          "Content-Type": "application/json",
        },
        body: {
          current_password: "SomePassword123",
          new_password: "NewPassword123",
        },
      });
      console.log("No access token response:", response.data);

      expect(response.status).toBe(401);
    });

    it("should reject request with invalid access token", async () => {
      const response = await post({
        url: passwordChangeUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer invalid_token_123",
        },
        body: {
          current_password: "SomePassword123",
          new_password: "NewPassword123",
        },
      });
      console.log("Invalid access token response:", response.data);

      expect(response.status).toBe(401);
    });
  });

  describe("Security events", () => {
    it("should generate password_change_success event on successful change", async () => {
      const initialPassword = "TestPass123";
      const { accessToken } = await createUserWithPassword(initialPassword);

      const response = await changePassword(accessToken, initialPassword, "NewPassword123");

      // Note: Security event verification would require checking event logs/hooks
      // This is a placeholder for future event verification tests
      console.log("Password change for security event test:", response.status, response.data);
      expect(response.status).toBe(200);
    });
  });
});
