import { describe, expect, it } from "@jest/globals";
import { get } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

describe("organization security event management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  describe("success pattern", () => {
    it("list security events", async () => {
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

      // List security events within the organization
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List security events response:", JSON.stringify(listResponse.data, null, 2));
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=5&offset=0`,
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

    it("filter by event type", async () => {
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

      // Test event type filtering
      const filteredResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?event_type=user_login&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Filtered response:", JSON.stringify(filteredResponse.data, null, 2));
      expect(filteredResponse.status).toBe(200);
      expect(filteredResponse.data).toHaveProperty("list");
      expect(Array.isArray(filteredResponse.data.list)).toBe(true);
    });

    it("filter by multiple event types (comma-separated)", async () => {
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

      const multiTypeResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?event_type=user_signin,user_signin_failure,user_signout&limit=100`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Multi event type response:", multiTypeResponse.status, "count:", multiTypeResponse.data.list?.length);
      expect(multiTypeResponse.status).toBe(200);
      expect(multiTypeResponse.data).toHaveProperty("list");
      expect(Array.isArray(multiTypeResponse.data.list)).toBe(true);

      if (multiTypeResponse.data.list.length > 0) {
        const types = multiTypeResponse.data.list.map(e => e.type);
        console.log("Event types found:", [...new Set(types)]);
      }
    });
  });

  describe("query parameter validation", () => {
    it("limit validation - maximum boundary (1000)", async () => {
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

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=1000`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Limit=1000 response:", response.status);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("limit", 1000);
    });

    it("limit validation - exceeds maximum (1001)", async () => {
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

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=1001`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Limit=1001 response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("limit validation - minimum boundary (1)", async () => {
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

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Limit=1 response:", response.status);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("limit", 1);
    });

    it("limit validation - below minimum (0)", async () => {
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

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Limit=0 response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("limit validation - non-numeric value", async () => {
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

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=abc`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Limit=abc response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("offset validation - negative value", async () => {
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

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?offset=-1`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Offset=-1 response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("from/to parameter - without time range retrieves all events", async () => {
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

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=100`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Without from/to response:", response.status, "total_count:", response.data.total_count);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data).toHaveProperty("total_count");
    });

    it("from/to parameter - with time range filters events", async () => {
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

      const from = "2025-01-01T00:00:00Z";
      const to = "2025-12-31T23:59:59Z";
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&limit=100`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("With from/to response:", response.status, "total_count:", response.data.total_count);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
    });
  });

  describe("error cases", () => {
    it("unauthorized access without proper scope", async () => {
      // Get token without org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "account", // Missing org-management scope
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access organization security events API
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Unauthorized response:", listResponse.data);
      expect(listResponse.status).toBe(403);
    });

    it("invalid organization id", async () => {
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

      // Try to access with invalid organization ID
      const invalidOrgResponse = await get({
        url: `${backendUrl}/v1/management/organizations/invalid-org-id-123/tenants/${tenantId}/security-events`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid org response:", invalidOrgResponse.data);
      expect([400, 404]).toContain(invalidOrgResponse.status);
    });

    it("invalid tenant path parameter", async () => {
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

      // Try to access with invalid tenant ID
      const invalidTenantResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/invalid-tenant-id/security-events`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid tenant response:", invalidTenantResponse.data);
      expect([400, 403, 404]).toContain(invalidTenantResponse.status);
    });
  });

  describe("specific security event retrieval", () => {
    it("get specific security event", async () => {
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

      // First, get the list of security events to find a valid event ID
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List response for event lookup:", listResponse.data);
      expect(listResponse.status).toBe(200);

      if (listResponse.data.total_count > 0) {
        const firstEvent = listResponse.data.list[0];
        const eventId = firstEvent.id;

        // Get specific security event details
        const detailResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events/${eventId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Security event detail response:", detailResponse.data);
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data).toHaveProperty("id", eventId);
        expect(detailResponse.data).toHaveProperty("type");
      } else {
        console.log("No security events available for detail testing");
      }
    });

    it("get non-existent security event", async () => {
      const notExistsId = uuidv4();
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

      // Try to get non-existent security event
      const nonExistentResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events/${notExistsId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Non-existent security event response:", nonExistentResponse.data);
      expect(nonExistentResponse.status).toBe(404);
    });
  });
});