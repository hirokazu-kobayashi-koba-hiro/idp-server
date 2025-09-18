import { describe, expect, it } from "@jest/globals";
import { get } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";

describe("organization authentication interaction management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  describe("success pattern", () => {
    it("list authentication interactions", async () => {
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

      // List authentication interactions within the organization
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions?limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List authentication interactions response:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);

      // Validate response structure according to OpenAPI specification
      expect(listResponse.data).toHaveProperty("list");
      expect(Array.isArray(listResponse.data.list)).toBe(true);
      expect(listResponse.data).toHaveProperty("total_count");
      expect(typeof listResponse.data.total_count).toBe("number");
      expect(listResponse.data).toHaveProperty("limit");
      expect(listResponse.data.limit).toBe(10);
      expect(listResponse.data).toHaveProperty("offset");
      expect(listResponse.data.offset).toBe(0);

      // Validate individual interaction structure if list exists
      if (listResponse.data.list.length > 0) {
        const interaction = listResponse.data.list[0];

        // Validate required fields according to implementation
        expect(interaction).toHaveProperty("transaction_id");
        expect(typeof interaction.transaction_id).toBe("string");

        expect(interaction).toHaveProperty("type");
        expect(typeof interaction.type).toBe("string");

        expect(interaction).toHaveProperty("payload");
        expect(typeof interaction.payload).toBe("object");
      }
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions?limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Paginated response:", JSON.stringify(paginatedResponse.data, null, 2));
      expect(paginatedResponse.status).toBe(200);
      expect(paginatedResponse.data).toHaveProperty("list");
      expect(Array.isArray(paginatedResponse.data.list)).toBe(true);
      expect(paginatedResponse.data.limit).toBe(5);
      expect(paginatedResponse.data.offset).toBe(0);
    });

    it("filter by interaction type", async () => {
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

      // Filter by interaction type
      const filterResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions?type=password&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Filter by type response:", JSON.stringify(filterResponse.data, null, 2));
      expect(filterResponse.status).toBe(200);
      expect(filterResponse.data).toHaveProperty("results");
      expect(Array.isArray(filterResponse.data.results)).toBe(true);
    });

    it("filter by transaction_id", async () => {
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

      // First, get a list to find a transaction ID
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(listResponse.status).toBe(200);

      if (listResponse.data.list && listResponse.data.list.length > 0) {
        const transactionId = listResponse.data.list[0].transaction_id;

        // Filter by specific transaction ID
        const filterResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions?transaction_id=${transactionId}&limit=10`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });

        console.log("Filter by transaction_id response:", JSON.stringify(filterResponse.data, null, 2));
        expect(filterResponse.status).toBe(200);
        expect(filterResponse.data).toHaveProperty("list");
        expect(Array.isArray(filterResponse.data.list)).toBe(true);

        // All returned interactions should have the same transaction ID
        filterResponse.data.list.forEach(interaction => {
          expect(interaction.transaction_id).toBe(transactionId);
        });
      } else {
        console.log("No interactions available for transaction_id filter test");
      }
    });

    it("filter by date range", async () => {
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

      // Test date range filtering
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);

      const dateRangeResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions?created_at_from=${yesterday.toISOString()}&created_at_to=${tomorrow.toISOString()}&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("Date range filter response:", JSON.stringify(dateRangeResponse.data, null, 2));
      expect(dateRangeResponse.status).toBe(200);
      expect(dateRangeResponse.data).toHaveProperty("results");
      expect(Array.isArray(dateRangeResponse.data.results)).toBe(true);

      // Validate that all returned interactions fall within the date range
      dateRangeResponse.data.list.forEach(interaction => {
        const createdAt = new Date(interaction.created_at);
        expect(createdAt >= yesterday).toBe(true);
        expect(createdAt <= tomorrow).toBe(true);
      });
    });

    it("boundary value testing for pagination", async () => {
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

      // Test minimum limit
      const minLimitResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions?limit=1&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(minLimitResponse.status).toBe(200);
      expect(minLimitResponse.data.limit).toBe(1);
      expect(minLimitResponse.data.offset).toBe(0);
      expect(minLimitResponse.data.list.length).toBeLessThanOrEqual(1);

      // Test maximum reasonable limit
      const maxLimitResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions?limit=100&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(maxLimitResponse.status).toBe(200);
      expect(maxLimitResponse.data.limit).toBe(100);
      expect(maxLimitResponse.data.offset).toBe(0);
    });

    it("filter by status", async () => {
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

      // Filter by status
      const statusFilterResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions?status=completed&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Filter by status response:", JSON.stringify(statusFilterResponse.data, null, 2));
      expect(statusFilterResponse.status).toBe(200);
      expect(statusFilterResponse.data).toHaveProperty("results");
      expect(Array.isArray(statusFilterResponse.data.results)).toBe(true);
    });
  });

  describe("error patterns", () => {
    it("unauthorized access", async () => {
      // Try to access without proper token
      try {
        const unauthorizedResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions`,
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
        url: `${backendUrl}/v1/management/organizations/${wrongOrgId}/tenants/${tenantId}/authentication-interactions`,
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${nonExistentTenantId}/authentication-interactions`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(notFoundResponse.status).toBe(403);
    });
  });

  describe("get specific interaction", () => {
    it("get specific authentication interaction", async () => {
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

      // First, list interactions to get a valid transaction ID
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("List response for finding interaction:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);

      // If there are interactions available, test getting a specific one
      if (listResponse.data.list && listResponse.data.list.length > 0) {
        const firstInteraction = listResponse.data.list[0];
        const transactionId = firstInteraction.transaction_id;
        const interactionType = firstInteraction.type;

        console.log("Testing with transaction ID:", transactionId, "type:", interactionType);

        // Get the specific interaction
        const getResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions/${transactionId}/types/${interactionType}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });

        console.log("Get specific interaction response:", JSON.stringify(getResponse.data, null, 2));
        expect(getResponse.status).toBe(200);
        expect(getResponse.data).toHaveProperty("result");

        // Validate detailed interaction structure according to implementation
        const detailResult = getResponse.data.result;
        expect(detailResult).toHaveProperty("transaction_id", transactionId);
        expect(detailResult).toHaveProperty("type", interactionType);
        expect(detailResult).toHaveProperty("payload");
        expect(typeof detailResult.payload).toBe("object");
      } else {
        console.log("No authentication interactions available for testing get operation");
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

      // Try to get non-existent interaction
      const nonExistentTransactionId = "99999999-9999-9999-9999-999999999999";
      const notFoundResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions/${nonExistentTransactionId}/types/password`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(notFoundResponse.status).toBe(404);
    });
  });
});