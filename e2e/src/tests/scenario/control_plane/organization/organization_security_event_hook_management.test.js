import { describe, expect, it } from "@jest/globals";
import { get } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

describe("organization security event hook management api", () => {
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?limit=10&offset=0`,
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?limit=5&offset=0`,
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?event_type=user_login&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Filtered response:", JSON.stringify(filteredResponse.data, null, 2));
      expect(filteredResponse.status).toBe(200);
      expect(filteredResponse.data).toHaveProperty("list");
      expect(Array.isArray(filteredResponse.data.list)).toBe(true);
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks`,
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
        url: `${backendUrl}/v1/management/organizations/invalid-org-id-123/tenants/${tenantId}/security-event-hooks`,
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/invalid-tenant-id/security-event-hooks`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid tenant response:", invalidTenantResponse.data);
      expect(invalidTenantResponse.status).toBe(400);
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks?limit=1`,
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
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks/${eventId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Security event detail response:", detailResponse.data);
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data).toHaveProperty("id", eventId);
        expect(detailResponse.data).toHaveProperty("event_type");
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hooks/${notExistsId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Non-existent security event response:", nonExistentResponse.data);
      expect(nonExistentResponse.status).toBe(404);
    });
  });
});