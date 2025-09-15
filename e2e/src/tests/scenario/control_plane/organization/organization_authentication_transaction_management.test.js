import { describe, expect, it } from "@jest/globals";
import { get } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";

describe("organization authentication transaction management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  describe("success pattern", () => {
    it("list authentication transactions", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // List authentication transactions within the organization
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-transactions?limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List authentication transactions response:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(Array.isArray(listResponse.data.list)).toBe(true);
      expect(listResponse.data).toHaveProperty("total_count");
      expect(listResponse.data).toHaveProperty("limit", 10);
      expect(listResponse.data).toHaveProperty("offset", 0);
    });

    it("pagination support", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test pagination parameters
      const paginatedResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-transactions?limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Paginated response:", JSON.stringify(paginatedResponse.data, null, 2));
      expect(paginatedResponse.status).toBe(200);
      expect(paginatedResponse.data).toHaveProperty("list");
      expect(paginatedResponse.data).toHaveProperty("total_count");
      expect(paginatedResponse.data).toHaveProperty("limit", 5);
      expect(paginatedResponse.data).toHaveProperty("offset", 0);
      expect(Array.isArray(paginatedResponse.data.list)).toBe(true);
    });

    it("filter by authentication status", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Filter by authentication status
      const filterResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-transactions?status=completed&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Filter by status response:", JSON.stringify(filterResponse.data, null, 2));
      expect(filterResponse.status).toBe(200);
      expect(filterResponse.data).toHaveProperty("list");
      expect(Array.isArray(filterResponse.data.list)).toBe(true);
      expect(filterResponse.data).toHaveProperty("total_count");
      expect(filterResponse.data).toHaveProperty("limit", 10);
      expect(filterResponse.data).toHaveProperty("offset", 0);
    });

    it("filter by user identifier", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Filter by user identifier
      const userFilterResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-transactions?user_id=test-user&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Filter by user response:", JSON.stringify(userFilterResponse.data, null, 2));
      expect(userFilterResponse.status).toBe(200);
      expect(userFilterResponse.data).toHaveProperty("list");
      expect(Array.isArray(userFilterResponse.data.list)).toBe(true);
    });
  });

  describe("error patterns", () => {
    it("unauthorized access", async () => {
      // Try to access without proper token
      try {
        const unauthorizedResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-transactions`,
          headers: {
            Authorization: "Bearer invalid-token"
          }
        });
        expect(unauthorizedResponse.status).toBe(401);
      } catch (error) {
        expect(error.response.status).toBe(401);
      }
    });

    it("unauthorized error for wrong organization", async () => {
      // Get token for a different organization
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access a different organization (should fail)
      const wrongOrgId = "8df08cfe-e9d6-4ed3-a6a1-b3eb77479420";
      const forbiddenResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${wrongOrgId}/tenants/${tenantId}/authentication-transactions`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(forbiddenResponse.status).toBe(401);
    });

    it("forbidden no assigned tenant", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access non-existent tenant
      const nonExistentTenantId = "99999999-9999-9999-9999-999999999999";
      const notFoundResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${nonExistentTenantId}/authentication-transactions`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(notFoundResponse.status).toBe(403);
    });
  });

  describe("get specific transaction", () => {
    it("get specific authentication transaction", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // First, list transactions to get a valid transaction ID
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-transactions?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("List response for finding transaction:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);

      // If there are transactions available, test getting a specific one
      if (listResponse.data.list && listResponse.data.list.length > 0) {
        const firstTransaction = listResponse.data.list[0];
        const transactionId = firstTransaction.transaction_id;

        console.log("Testing with transaction ID:", transactionId);

        // Get the specific transaction
        const getResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-transactions/${transactionId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });

        console.log("Get specific transaction response:", JSON.stringify(getResponse.data, null, 2));
        expect(getResponse.status).toBe(200);
        expect(getResponse.data).toHaveProperty("transaction_id", transactionId);
      } else {
        console.log("No authentication transactions available for testing get operation");
      }
    });

    it("no transaction", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to get non-existent transaction
      const nonExistentTransactionId = "99999999-9999-9999-9999-999999999999";
      const notFoundResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-transactions/${nonExistentTransactionId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(notFoundResponse.status).toBe(404);
    });
  });
});