import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { requestAuthorizations } from "../../../../oauth/request";

describe("organization grant management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";
  let accessToken;
  let grantId;

  beforeAll(async () => {
    // Get OAuth token with org-management scope
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    expect(tokenResponse.status).toBe(200);
    accessToken = tokenResponse.data.access_token;

    // Create a grant by completing an authorization code flow
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: "clientSecretPost001",
      responseType: "code",
      state: "test-grant-management",
      scope: "openid profile email",
      redirectUri: "http://localhost:8080/callback",
    });

    if (authorizationResponse.code) {
      // Exchange code for token to create a grant
      const exchangeResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: "http://localhost:8080/callback",
        clientId: "clientSecretPost001",
        clientSecret: "clientSecretPostValue",
      });
      console.log("Grant creation token response:", exchangeResponse.status);
    }
  });

  describe("success pattern", () => {
    it("list grants with pagination", async () => {
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/grants?limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List grants response:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(Array.isArray(listResponse.data.list)).toBe(true);
      expect(listResponse.data).toHaveProperty("total_count");
      expect(listResponse.data).toHaveProperty("limit", 10);
      expect(listResponse.data).toHaveProperty("offset", 0);

      // Save grant ID for subsequent tests
      if (listResponse.data.list.length > 0) {
        grantId = listResponse.data.list[0].id;
        console.log("Found grant ID:", grantId);
      }
    });

    it("get specific grant details", async () => {
      // Skip if no grants exist
      if (!grantId) {
        console.log("Skipping - no grants available");
        return;
      }

      const detailResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/grants/${grantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Grant detail response:", JSON.stringify(detailResponse.data, null, 2));
      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data).toHaveProperty("id", grantId);
    });

    it("filter grants by user_id", async () => {
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/grants?user_id=12345678-1234-1234-1234-123456789012`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Filter by user_id response:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
    });

    it("filter grants by client_id", async () => {
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/grants?client_id=clientSecretPost001`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Filter by client_id response:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
    });

    it("dry run delete grant", async () => {
      // Skip if no grants exist
      if (!grantId) {
        console.log("Skipping - no grants available");
        return;
      }

      const dryRunDeleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/grants/${grantId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Dry run delete response:", dryRunDeleteResponse.data);
      expect(dryRunDeleteResponse.status).toBe(200);
      expect(dryRunDeleteResponse.data).toHaveProperty("message");
      expect(dryRunDeleteResponse.data).toHaveProperty("grant_id", grantId);
      expect(dryRunDeleteResponse.data).toHaveProperty("dry_run", true);

      // Verify grant was not actually deleted
      const verifyResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/grants/${grantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyResponse.status).toBe(200);
    });
  });

  describe("error cases", () => {
    it("unauthorized access without proper scope", async () => {
      // Get token without org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "account", // Missing org-management scope
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const limitedToken = tokenResponse.data.access_token;

      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/grants`,
        headers: {
          Authorization: `Bearer ${limitedToken}`
        }
      });
      console.log("Unauthorized response:", listResponse.data);
      expect(listResponse.status).toBe(403);
    });

    it("get non-existent grant", async () => {
      const nonExistentResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/grants/non-existent-grant-id`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Non-existent grant response:", nonExistentResponse.data);
      expect([400, 404]).toContain(nonExistentResponse.status);
    });

    it("delete non-existent grant", async () => {
      const deleteNonExistentResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/grants/non-existent-grant-id`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete non-existent grant response:", deleteNonExistentResponse.data);
      expect([400, 404]).toContain(deleteNonExistentResponse.status);
    });

    it("invalid organization id", async () => {
      const invalidOrgResponse = await get({
        url: `${backendUrl}/v1/management/organizations/invalid-org-id-123/tenants/${tenantId}/grants`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid org response:", invalidOrgResponse.data);
      expect([400, 404]).toContain(invalidOrgResponse.status);
    });

    it("invalid tenant path parameter", async () => {
      const invalidTenantResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/invalid-tenant-id/grants`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid tenant response:", invalidTenantResponse.data);
      expect([400, 403, 404]).toContain(invalidTenantResponse.status);
    });
  });
});
