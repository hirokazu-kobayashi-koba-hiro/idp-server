import { describe, expect, it } from "@jest/globals";
import { get, postWithJson } from "../../../../lib/http";
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
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // List security events within the organization
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log(
        "List security events response:",
        JSON.stringify(listResponse.data, null, 2)
      );
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
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test pagination parameters
      const paginatedResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log(
        "Paginated response:",
        JSON.stringify(paginatedResponse.data, null, 2)
      );
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
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test event type filtering
      const filteredResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?event_type=user_login&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log(
        "Filtered response:",
        JSON.stringify(filteredResponse.data, null, 2)
      );
      expect(filteredResponse.status).toBe(200);
      expect(filteredResponse.data).toHaveProperty("list");
      expect(Array.isArray(filteredResponse.data.list)).toBe(true);
    });

    it("filter by multiple event types (comma-separated)", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const multiTypeResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?event_type=issue_token_success,user_delete&limit=100`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log(
        "Multi event type response:",
        multiTypeResponse.status,
        "count:",
        multiTypeResponse.data.list?.length
      );
      expect(multiTypeResponse.status).toBe(200);
      expect(multiTypeResponse.data).toHaveProperty("list");
      expect(Array.isArray(multiTypeResponse.data.list)).toBe(true);

      if (multiTypeResponse.data.list.length > 0) {
        const types = multiTypeResponse.data.list.map((e) => e.type);
        console.log("Event types found:", [...new Set(types)]);
      }
    });

    it("filter by details flat key (details.action=POST)", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?details.action=POST&limit=20`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data.total_count).toBeGreaterThan(0);
      // 返ってきた全 event の detail.action は POST であるはず
      for (const event of response.data.list) {
        expect(event.detail?.action).toBe("POST");
      }
    });

    it("filter by details nested key (details.user.status=REGISTERED)", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?details.user.status=REGISTERED&limit=20`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data.total_count).toBeGreaterThan(0);
      // 返ってきた全 event の detail.user.status は REGISTERED であるはず
      for (const event of response.data.list) {
        expect(event.detail?.user?.status).toBe("REGISTERED");
      }
    });

    it("filter by details nested key returns 0 for non-existent value", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?details.user.status=__NON_EXISTENT__&limit=20`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(200);
      expect(response.data.total_count).toBe(0);
      expect(response.data.list).toHaveLength(0);
    });

    it("loose filter (details_any.action=POST) matches the same as strict for string values", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;
      const headers = { Authorization: `Bearer ${accessToken}` };

      const strict = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?details.action=POST&limit=20`,
        headers,
      });
      const loose = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?details_any.action=POST&limit=20`,
        headers,
      });
      expect(strict.status).toBe(200);
      expect(loose.status).toBe(200);
      // "POST" は数値/真偽値ではないので typed leaf は付かず、loose は strict と同一結果
      expect(loose.data.total_count).toBe(strict.data.total_count);
      expect(loose.data.total_count).toBeGreaterThan(0);
      for (const event of loose.data.list) {
        expect(event.detail?.action).toBe("POST");
      }
    });

    it("loose filter (details_any.user.status=REGISTERED) works on nested string value", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?details_any.user.status=REGISTERED&limit=20`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(200);
      expect(response.data.total_count).toBeGreaterThan(0);
      for (const event of response.data.list) {
        expect(event.detail?.user?.status).toBe("REGISTERED");
      }
    });
  });

  describe("query parameter validation", () => {
    it("limit validation - maximum boundary (1000)", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=1000`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("Limit=1000 response:", response.status);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("limit", 1000);
    });

    it("limit validation - exceeds maximum (1001)", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=1001`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("Limit=1001 response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("limit validation - minimum boundary (1)", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("Limit=1 response:", response.status);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("limit", 1);
    });

    it("limit validation - below minimum (0)", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("Limit=0 response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("limit validation - non-numeric value", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=abc`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("Limit=abc response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("offset validation - negative value", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?offset=-1`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("Offset=-1 response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("details key validation - empty segment (details..) returns 400", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?details..=POST&limit=20`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("details.. response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("details key validation - empty key (details.) returns 400", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?details.=POST&limit=20`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("details. response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("details key validation - empty segment loose key (details_any..) returns 400", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?details_any..=POST&limit=20`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("details_any.. response:", response.status, response.data);
      expect(response.status).toBe(400);
    });

    it("from/to parameter - without time range retrieves all events", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=100`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log(
        "Without from/to response:",
        response.status,
        "total_count:",
        response.data.total_count
      );
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data).toHaveProperty("total_count");
    });

    it("from/to parameter - with time range filters events", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const from = "2025-01-01T00:00:00Z";
      const to = "2025-12-31T23:59:59Z";
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&limit=100`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log(
        "With from/to response:",
        response.status,
        "total_count:",
        response.data.total_count
      );
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
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "account", // Missing org-management scope
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access organization security events API
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("Unauthorized response:", listResponse.data);
      expect(listResponse.status).toBe(403);
    });

    it("invalid organization id", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access with invalid organization ID
      const invalidOrgResponse = await get({
        url: `${backendUrl}/v1/management/organizations/invalid-org-id-123/tenants/${tenantId}/security-events`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("Invalid org response:", invalidOrgResponse.data);
      expect([400, 404]).toContain(invalidOrgResponse.status);
    });

    it("invalid tenant path parameter", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access with invalid tenant ID
      const invalidTenantResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/invalid-tenant-id/security-events`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
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
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // First, get the list of security events to find a valid event ID
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
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
            Authorization: `Bearer ${accessToken}`,
          },
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
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to get non-existent security event
      const nonExistentResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events/${notExistsId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log(
        "Non-existent security event response:",
        nonExistentResponse.data
      );
      expect(nonExistentResponse.status).toBe(404);
    });
  });

  // 型ゆるい detail フィルタ (details_any.*) の数値マッチを end-to-end で実証する。
  // authentication-device の /logs はリクエストボディをそのまま detail.execution_result に
  // 格納するため、数値値を持つ security_event を実 API 経由で生成できる
  // (user_id を渡せば FIDO-UAF 登録は不要)。
  describe("loose detail numeric match (end-to-end via authentication-device log)", () => {
    it("details_any.* matches a numerically-stored detail while details.* does not", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001",
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;
      const headers = { Authorization: `Bearer ${accessToken}` };

      // access token の sub を user_id として使う (findUser が解決 → event 発行)
      const sub = JSON.parse(
        Buffer.from(accessToken.split(".")[1], "base64").toString("utf8")
      ).sub;

      // marker(文字列・自分のイベント特定用) と attempts(数値) を持つログを投入。
      // detail.execution_result.marker="<uuid>", detail.execution_result.attempts=3(数値) になる。
      const marker = uuidv4();
      const logResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authentication-devices/logs`,
        body: { user_id: sub, marker, attempts: 3 },
      });
      expect([200, 204]).toContain(logResponse.status);

      const base = `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events`;
      const markerFilter = `details.execution_result.marker=${marker}`;

      // event 発行は非同期 + control plane の READ は replica の可能性があるため、
      // marker(文字列・strict)でヒットするまでポーリングする。
      let exists = 0;
      for (let i = 0; i < 30; i++) {
        const r = await get({
          url: `${base}?${markerFilter}&limit=20`,
          headers,
        });
        expect(r.status).toBe(200);
        if (r.data.total_count > 0) {
          exists = r.data.total_count;
          break;
        }
        await new Promise((resolve) => setTimeout(resolve, 500));
      }
      // (sanity) イベントが生成され、文字列 marker は strict containment でヒットする
      expect(exists).toBeGreaterThan(0);

      // strict (details.*) は数値 3 にマッチしない: {"attempts":"3"} != 保存値 3(数値)
      const strict = await get({
        url: `${base}?${markerFilter}&details.execution_result.attempts=3&limit=20`,
        headers,
      });
      expect(strict.status).toBe(200);
      expect(strict.data.total_count).toBe(0);

      // loose (details_any.*) は typed leaf {"attempts":3} で数値 3 にマッチする
      const loose = await get({
        url: `${base}?${markerFilter}&details_any.execution_result.attempts=3&limit=20`,
        headers,
      });
      expect(loose.status).toBe(200);
      expect(loose.data.total_count).toBeGreaterThan(0);
      for (const event of loose.data.list) {
        expect(event.detail?.execution_result?.attempts).toBe(3);
        expect(event.detail?.execution_result?.marker).toBe(marker);
      }
    });
  });
});
