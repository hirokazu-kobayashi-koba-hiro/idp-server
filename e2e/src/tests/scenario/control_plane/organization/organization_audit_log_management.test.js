import { describe, expect, it } from "@jest/globals";
import { get } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

describe("organization audit log management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  describe("success pattern", () => {
    it("list audit logs", async () => {
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

      // List audit logs within the organization
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/audit-logs?limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List audit logs response:", JSON.stringify(listResponse.data, null, 2));
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/audit-logs?limit=5&offset=0`,
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

    it("filter by action", async () => {
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

      // Test action filtering
      const filteredResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/audit-logs?action=create&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Filtered response:", JSON.stringify(filteredResponse.data, null, 2));
      expect(filteredResponse.status).toBe(200);
      expect(filteredResponse.data).toHaveProperty("list");
      expect(Array.isArray(filteredResponse.data.list)).toBe(true);
    });

    it("filter by resource", async () => {
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

      // Test resource filtering
      const filteredResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/audit-logs?resource=user&limit=10&offset=0`,
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

      // Try to access organization audit logs API
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/audit-logs`,
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
        url: `${backendUrl}/v1/management/organizations/invalid-org-id-123/tenants/${tenantId}/audit-logs`,
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
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/invalid-tenant-id/audit-logs`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid tenant response:", invalidTenantResponse.data);
      expect([400, 404]).toContain(invalidTenantResponse.status);
    });
  });

  describe("specific audit log retrieval", () => {
    it("get specific audit log", async () => {
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

      // First, get the list of audit logs to find a valid log ID
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/audit-logs?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List response for log lookup:", listResponse.data);
      expect(listResponse.status).toBe(200);

      if (listResponse.data.total_count > 0) {
        const firstLog = listResponse.data.list[0];
        const logId = firstLog.id;

        // Get specific audit log details
        const detailResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/audit-logs/${logId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Audit log detail response:", detailResponse.data);
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data).toHaveProperty("id", logId);
        expect(detailResponse.data).toHaveProperty("target_resource_action");
      } else {
        console.log("No audit logs available for detail testing");
      }
    });

    it("get non-existent audit log", async () => {
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

      // Try to get non-existent audit log
      const nonExistentResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/audit-logs/${notExistsId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Non-existent audit log response:", nonExistentResponse.data);
      expect(nonExistentResponse.status).toBe(404);
    });
  });
});