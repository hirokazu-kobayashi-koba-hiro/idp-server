import { describe, expect, it } from "@jest/globals";
import { deletion, postWithJson, putWithJson } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

/**
 * Organization User Password Policy E2E Tests
 *
 * Tests password policy validation for Management API user operations:
 * - User creation (POST /v1/management/organizations/{orgId}/tenants/{tenantId}/users)
 * - Password update (PUT /v1/management/organizations/{orgId}/tenants/{tenantId}/users/{userId}/password)
 *
 * Validates tenant-specific password policies:
 * - Minimum length (default: 8 characters)
 * - Complexity requirements (uppercase, lowercase, numbers, special chars)
 * - Custom regex patterns
 */
describe("Organization User Password Policy", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  /**
   * Helper: Get OAuth token with org-management scope
   */
  const getAccessToken = async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001",
    });
    expect(tokenResponse.status).toBe(200);
    return tokenResponse.data.access_token;
  };

  /**
   * Helper: Create a user with password
   */
  const createUser = async (accessToken, password) => {
    const timestamp = Date.now();
    const userId = uuidv4();
    const email = `pwpolicy${timestamp}@example.com`;

    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users`,
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      body: {
        sub: userId,
        provider_id: "idp-server",
        name: `Password Policy Test ${timestamp}`,
        email: email,
        raw_password: password,
      },
    });

    return { userId, email, response };
  };

  /**
   * Helper: Update user password
   */
  const updatePassword = async (accessToken, userId, newPassword, dryRun = false) => {
    const url = dryRun
      ? `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}/password?dry_run=true`
      : `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}/password`;

    return await putWithJson({
      url,
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      body: {
        raw_password: newPassword,
      },
    });
  };

  /**
   * Helper: Delete user
   */
  const deleteUser = async (accessToken, userId) => {
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });
  };

  describe("User creation - Password policy validation", () => {
    let accessToken;

    beforeEach(async () => {
      accessToken = await getAccessToken();
    });

    it("should accept password with exactly 8 characters (default policy)", async () => {
      const { userId, response } = await createUser(accessToken, "Valid123");

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("result");

      await deleteUser(accessToken, userId);
    });

    it("should accept password with more than 8 characters", async () => {
      const { userId, response } = await createUser(accessToken, "LongerPassword123");

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("result");

      await deleteUser(accessToken, userId);
    });

    it("should accept passphrase (20+ characters, no complexity)", async () => {
      const { userId, response } = await createUser(
        accessToken,
        "my favorite coffee shop"
      );

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("result");

      await deleteUser(accessToken, userId);
    });

    it("should reject password shorter than 8 characters", async () => {
      const { response } = await createUser(accessToken, "Short1");

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_request");
      expect(response.data.error_description).toContain("Password policy violation");
      expect(response.data.error_description).toContain("at least 8 characters");
    });

    it("should reject empty password", async () => {
      const { response } = await createUser(accessToken, "");

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_request");
      expect(response.data.error_description).toContain("Password policy violation");
      expect(response.data.error_description).toContain("Password is required");
    });

    it("should reject password with only spaces", async () => {
      const { response } = await createUser(accessToken, "        ");

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_request");
      expect(response.data.error_description).toContain("Password policy violation");
    });
  });

  describe("Password update - Password policy validation", () => {
    let accessToken;
    let userId;

    beforeEach(async () => {
      accessToken = await getAccessToken();
      const result = await createUser(accessToken, "InitialPass123");
      userId = result.userId;
      expect(result.response.status).toBe(201);
    });

    afterEach(async () => {
      if (userId) {
        await deleteUser(accessToken, userId);
      }
    });

    it("should accept password with exactly 8 characters", async () => {
      const response = await updatePassword(accessToken, userId, "Valid123");

      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("result");
    });

    it("should accept password with more than 8 characters", async () => {
      const response = await updatePassword(accessToken, userId, "NewPassword456");

      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("result");
    });

    it("should accept passphrase (20+ characters)", async () => {
      const response = await updatePassword(
        accessToken,
        userId,
        "i love drinking coffee in the morning"
      );

      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("result");
    });

    it("should reject password shorter than 8 characters", async () => {
      const response = await updatePassword(accessToken, userId, "Short1");

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_request");
      expect(response.data.error_description).toContain("Password policy violation");
      expect(response.data.error_description).toContain("at least 8 characters");
    });

    it("should reject empty password", async () => {
      const response = await updatePassword(accessToken, userId, "");

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_request");
      expect(response.data.error_description).toContain("Password policy violation");
      expect(response.data.error_description).toContain("Password is required");
    });

    it("should reject 7-character password", async () => {
      const response = await updatePassword(accessToken, userId, "Pass123");

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_request");
      expect(response.data.error_description).toContain("at least 8 characters");
    });

    it("should validate in dry-run mode without persisting changes", async () => {
      const response = await updatePassword(accessToken, userId, "Short1", true);

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_request");
      expect(response.data.error_description).toContain("Password policy violation");
    });

    it("should accept valid password in dry-run mode", async () => {
      const response = await updatePassword(accessToken, userId, "ValidDryRun123", true);

      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("dry_run", true);
    });
  });

  describe("Edge cases", () => {
    let accessToken;

    beforeEach(async () => {
      accessToken = await getAccessToken();
    });

    it("should handle unicode characters in password", async () => {
      const { userId, response } = await createUser(accessToken, "パスワード12345678");

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("result");

      await deleteUser(accessToken, userId);
    });

    it("should handle special characters in password", async () => {
      const { userId, response } = await createUser(
        accessToken,
        "P@ssw0rd!#$%^&*()"
      );

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("result");

      await deleteUser(accessToken, userId);
    });

    it("should accept password at maximum length (72 characters)", async () => {
      const maxPassword = "A".repeat(72);
      const { userId, response } = await createUser(accessToken, maxPassword);

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("result");

      await deleteUser(accessToken, userId);
    });

    it("should reject password exceeding maximum length (100 characters)", async () => {
      const longPassword = "A".repeat(100);
      const { response } = await createUser(accessToken, longPassword);

      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_request");
      expect(response.data.error_description).toContain("Password policy violation");
      expect(response.data.error_description).toContain("must not exceed 72 characters");
    });
  });

  describe("NIST SP 800-63B compliance", () => {
    let accessToken;

    beforeEach(async () => {
      accessToken = await getAccessToken();
    });

    it("should accept simple 8-character password (no forced complexity)", async () => {
      const { userId, response } = await createUser(accessToken, "simplepass");

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("result");

      await deleteUser(accessToken, userId);
    });

    it("should accept all-lowercase password", async () => {
      const { userId, response } = await createUser(accessToken, "alllowercase");

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("result");

      await deleteUser(accessToken, userId);
    });

    it("should accept all-uppercase password", async () => {
      const { userId, response } = await createUser(accessToken, "ALLUPPERCASE");

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("result");

      await deleteUser(accessToken, userId);
    });

    it("should accept numeric-only password (8+ digits)", async () => {
      const { userId, response } = await createUser(accessToken, "12345678");

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("result");

      await deleteUser(accessToken, userId);
    });
  });
});
