import { describe, expect, it, beforeAll } from "@jest/globals";
import { postWithJson, putWithJson, get } from "../../../../lib/http";
import { backendUrl, adminServerConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { generateRandomNumber } from "../../../../lib/util";

/**
 * Issue #729: Multiple IdP Same Email Support E2E Tests
 *
 * Tests the following scenarios:
 * 1. Multiple IdPs can use the same email address
 * 2. EMAIL_OR_EXTERNAL_USER_ID policy fallback mechanism
 * 3. preferred_username mutability (updates when email changes)
 * 4. Duplicate prevention within same IdP
 */
describe("Issue #729: Multiple IdP Same Email Support", () => {

  let accessToken;
  const testEmail = `test-${generateRandomNumber(10)}@example.com`;

  beforeAll(async () => {
    const tokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret
    });
    expect(tokenResponse.status).toBe(200);
    accessToken = tokenResponse.data.access_token;
  });

  describe("Scenario 1: Multiple IdPs with same email", () => {

    it("should allow same email for different providers", async () => {
      // 1. Create user with external-idp-1 provider
      const externalIdp1UserResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "external-idp-1",
          external_user_id: `external-idp-1-${generateRandomNumber(10)}`,
          name: "External IdP 1 User",
          email: testEmail,
          raw_password: "Password@123"
        }
      });
      console.log("External IdP 1 User:", externalIdp1UserResponse.data);
      expect(externalIdp1UserResponse.status).toBe(201);
      expect(externalIdp1UserResponse.data.result.email).toBe(testEmail);
      expect(externalIdp1UserResponse.data.result.provider_id).toBe("external-idp-1");
      expect(externalIdp1UserResponse.data.result.preferred_username).toBe(testEmail);

      const externalIdp1UserId = externalIdp1UserResponse.data.result.sub;

      // 2. Create user with test-idp provider (same email)
      const testIdpUserResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "test-idp",
          external_user_id: `test-idp-${generateRandomNumber(10)}`,
          name: "Test IdP User",
          email: testEmail,  // Same email as external-idp-1 user
          raw_password: "Password@123"
        }
      });
      console.log("Test IdP User:", testIdpUserResponse.data);
      expect(testIdpUserResponse.status).toBe(201);
      expect(testIdpUserResponse.data.result.email).toBe(testEmail);
      expect(testIdpUserResponse.data.result.provider_id).toBe("test-idp");
      expect(testIdpUserResponse.data.result.preferred_username).toBe(testEmail);

      const testIdpUserId = testIdpUserResponse.data.result.sub;

      // 3. Create user with local provider (same email)
      const localUserResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "idp-server",
          name: "Local User",
          email: testEmail,  // Same email as external-idp-1 and test-idp users
          raw_password: "Password@123"
        }
      });
      console.log("Local User:", localUserResponse.data);
      expect(localUserResponse.status).toBe(201);
      expect(localUserResponse.data.result.email).toBe(testEmail);
      expect(localUserResponse.data.result.provider_id).toBe("idp-server");
      expect(localUserResponse.data.result.preferred_username).toBe(testEmail);

      // 4. Verify all three users exist independently
      const externalIdp1Detail = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${externalIdp1UserId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(externalIdp1Detail.status).toBe(200);
      expect(externalIdp1Detail.data.email).toBe(testEmail);
      expect(externalIdp1Detail.data.provider_id).toBe("external-idp-1");

      const testIdpDetail = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${testIdpUserId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(testIdpDetail.status).toBe(200);
      expect(testIdpDetail.data.email).toBe(testEmail);
      expect(testIdpDetail.data.provider_id).toBe("test-idp");

      // All three users should have different sub values
      expect(externalIdp1UserId).not.toBe(testIdpUserId);
    });

    it("should prevent duplicate email within same provider", async () => {
      const uniqueEmail = `unique-${generateRandomNumber(10)}@example.com`;

      // 1. Create first user with external-idp-2 provider
      const firstUserResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "external-idp-2",
          external_user_id: `external-idp-2-${generateRandomNumber(10)}`,
          name: "First User",
          email: uniqueEmail,
          raw_password: "Password@123"
        }
      });
      expect(firstUserResponse.status).toBe(201);

      // 2. Try to create second user with same provider and email (should fail)
      const duplicateUserResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "external-idp-2",  // Same provider
          external_user_id: `external-idp-2-${generateRandomNumber(10)}`,
          name: "Duplicate User",
          email: uniqueEmail,  // Same email
          raw_password: "Password@123"
        }
      });
      console.log("Duplicate attempt:", duplicateUserResponse.data);
      expect(duplicateUserResponse.status).toBe(400);
      // Expected error message from UserVerifier.throwExceptionIfUserEmailAlreadyExists()
      expect(duplicateUserResponse.data.error_description).toContain("User email is already exists");
    });
  });

  describe("Scenario 2: EMAIL_OR_EXTERNAL_USER_ID fallback policy", () => {

    it("should use email as preferred_username when email is provided", async () => {
      const emailUser = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "test-idp",
          external_user_id: `test-idp-${generateRandomNumber(10)}`,
          name: "User with Email",
          email: `with-email-${generateRandomNumber(10)}@example.com`,
          raw_password: "Password@123"
        }
      });

      expect(emailUser.status).toBe(201);
      expect(emailUser.data.result.preferred_username).toBe(emailUser.data.result.email);
    });

    it("should fallback to provider.external_user_id when email is missing", async () => {
      const externalUserId = `test-idp-${generateRandomNumber(10)}`;

      const noEmailUser = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "test-idp",
          external_user_id: externalUserId,
          name: "User without Email",
          // email is not provided
          raw_password: "Password@123"
        }
      });

      console.log("User without email:", noEmailUser.data);
      expect(noEmailUser.status).toBe(201);
      expect(noEmailUser.data.result.email).toBeUndefined();
      // Should fallback to "provider.external_user_id" format
      expect(noEmailUser.data.result.preferred_username).toBe(`test-idp.${externalUserId}`);
    });

    it("should use simple external_user_id for idp-server provider", async () => {
      const externalUserId = `local-${generateRandomNumber(10)}`;

      const localUser = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "idp-server",
          external_user_id: externalUserId,
          name: `local-user-${generateRandomNumber(10)}`,
          // email is not provided, will fallback to external_user_id
          raw_password: "Password@123"
        }
      });

      console.log("Local user without email:", localUser.data);
      expect(localUser.status).toBe(201);
      expect(localUser.data.result.email).toBeUndefined();
      // For idp-server, should use external_user_id directly (not provider.external_user_id format)
      expect(localUser.data.result.preferred_username).toBe(externalUserId);
    });

    it("should reject when no identifier can be determined", async () => {
      const noIdentifierUser = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "test-idp",
          // No external_user_id, no email, no name
          raw_password: "Password@123"
        }
      });

      console.log("No identifier user:", noIdentifierUser.data);
      expect(noIdentifierUser.status).toBe(400);
      // Expected error message from UserVerifier.throwExceptionIfPreferredUsernameNotSet()
      expect(noIdentifierUser.data.error_description).toContain("User preferred_username could not be determined from tenant identity policy");
      expect(noIdentifierUser.data.error_description).toContain("Ensure required fields");
    });
  });

  describe("Scenario 3: preferred_username mutability", () => {

    it("should update preferred_username when email changes", async () => {
      const initialEmail = `initial-${generateRandomNumber(10)}@example.com`;
      const updatedEmail = `updated-${generateRandomNumber(10)}@example.com`;

      // 1. Create user with initial email
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "external-idp-3",
          external_user_id: `external-idp-3-${generateRandomNumber(10)}`,
          name: "Mutable User",
          email: initialEmail,
          raw_password: "Password@123"
        }
      });

      expect(createResponse.status).toBe(201);
      expect(createResponse.data.result.email).toBe(initialEmail);
      expect(createResponse.data.result.preferred_username).toBe(initialEmail);

      const userId = createResponse.data.result.sub;

      // 2. Update email
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "external-idp-3",
          external_user_id: createResponse.data.result.external_user_id,
          name: "Mutable User",
          email: updatedEmail  // Changed email
        }
      });

      console.log("Updated user:", updateResponse.data);
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data.result.email).toBe(updatedEmail);
      // preferred_username should automatically update to new email
      expect(updateResponse.data.result.preferred_username).toBe(updatedEmail);

      // 3. Verify persistence
      const detailResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data.email).toBe(updatedEmail);
      expect(detailResponse.data.preferred_username).toBe(updatedEmail);
    });

    it("should recalculate preferred_username when email is removed", async () => {
      const initialEmail = `with-email-${generateRandomNumber(10)}@example.com`;
      const externalUserId = `test-idp-${generateRandomNumber(10)}`;

      // 1. Create user with email
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "test-idp",
          external_user_id: externalUserId,
          name: "User",
          email: initialEmail,
          raw_password: "Password@123"
        }
      });

      expect(createResponse.status).toBe(201);
      expect(createResponse.data.result.preferred_username).toBe(initialEmail);

      const userId = createResponse.data.result.sub;

      // 2. Update without email (simulate external IdP stopped providing email)
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: "test-idp",
          external_user_id: externalUserId,
          name: "User"
          // email removed
        }
      });

      console.log("Updated user without email:", updateResponse.data);
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data.result.email).toBeUndefined();
      // Should fallback to provider.external_user_id
      expect(updateResponse.data.result.preferred_username).toBe(`test-idp.${externalUserId}`);
    });
  });

  describe("Scenario 4: Cross-scenario validation", () => {

    it("should maintain provider isolation across all operations", async () => {
      const sharedEmail = `shared-${generateRandomNumber(10)}@example.com`;

      // Create users with same email across multiple providers
      const providers = ["external-idp-4", "test-idp", "external-idp-5"];
      const userIds = [];

      for (const provider of providers) {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            provider_id: provider,
            external_user_id: `${provider}-${generateRandomNumber(10)}`,
            name: `${provider} User`,
            email: sharedEmail,
            raw_password: "Password@123"
          }
        });

        expect(response.status).toBe(201);
        expect(response.data.result.provider_id).toBe(provider);
        expect(response.data.result.email).toBe(sharedEmail);
        expect(response.data.result.preferred_username).toBe(sharedEmail);
        userIds.push(response.data.result.sub);
      }

      // Verify all users are isolated
      expect(new Set(userIds).size).toBe(providers.length);

      // Update one user's email should not affect others
      const newEmail = `new-${generateRandomNumber(10)}@example.com`;
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userIds[0]}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          provider_id: providers[0],
          name: `${providers[0]} User`,
          email: newEmail
        }
      });

      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data.result.email).toBe(newEmail);

      // Other users should still have original email
      for (let i = 1; i < userIds.length; i++) {
        const detailResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userIds[i]}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data.email).toBe(sharedEmail);
      }
    });
  });
});
