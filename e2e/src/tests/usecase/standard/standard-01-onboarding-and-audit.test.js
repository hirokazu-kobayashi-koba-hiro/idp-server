import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";

/**
 * Standard Use Case: Onboarding and Audit Log Tracking
 *
 * This test demonstrates a standard workflow:
 * 1. Create a new tenant (Organization onboarding)
 * 2. Create a client for the tenant
 * 3. Search audit logs to verify operations were recorded
 * 4. Test new audit log search features (Issue #913)
 *    - outcome_result filter
 *    - target_tenant_id filter
 *    - dry_run filter
 *    - Multiple types search
 *    - No from/to default (全期間検索)
 */
describe("Standard Use Case: Onboarding and Audit Log Tracking", () => {
  let accessToken;
  let tenantId;
  let clientId;

  beforeAll(async () => {
    // Get OAuth token with system admin privileges
    const tokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);
    accessToken = tokenResponse.data.access_token;
  });

  describe("Step 1: Tenant Onboarding", () => {
    it("should create a new tenant successfully", async () => {
      const timestamp = Date.now();
      tenantId = uuidv4();

      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            id: tenantId,
            name: `Standard Tenant ${timestamp}`,
            domain: "http://localhost:8080",
            description: "Standard use case tenant for audit log testing",
            authorization_provider: "idp-server",
            tenant_type: "BUSINESS"
          },
          authorization_server: {
            issuer: `http://localhost:8080/${tenantId}`,
            authorization_endpoint: `http://localhost:8080/${tenantId}/v1/authorizations`,
            token_endpoint: `http://localhost:8080/${tenantId}/v1/tokens`,
            userinfo_endpoint: `http://localhost:8080/${tenantId}/v1/userinfo`,
            jwks_uri: `http://localhost:8080/${tenantId}/v1/jwks`,
            scopes_supported: ["openid", "profile", "email"],
            response_types_supported: ["code"],
            response_modes_supported: ["query"],
            subject_types_supported: ["public"],
            grant_types_supported: ["authorization_code", "refresh_token"],
            token_endpoint_auth_methods_supported: ["client_secret_post"],
            extension: {
              access_token_type: "JWT",
              access_token_duration: 3600,
              id_token_duration: 3600
            }
          }
        }
      });

      console.log("✅ Tenant created:", createResponse.status, createResponse.data.result.id);
      expect(createResponse.status).toBe(201);
      expect(createResponse.data.result.id).toBe(tenantId);
    });
  });

  describe("Step 2: Client Registration", () => {
    it("should create a client for the new tenant", async () => {
      clientId = uuidv4();

      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          client_id: clientId,
          client_name: "Standard Use Case Test Client",
          client_secret: "test-secret-123",
          grant_types: ["authorization_code", "refresh_token"],
          redirect_uris: ["http://localhost:3000/callback"],
          enabled: true
        }
      });

      console.log("✅ Client created:", createResponse.status, createResponse.data.result.client_id);
      expect(createResponse.status).toBe(201);
      expect(createResponse.data.result.client_id).toBe(clientId);
    });
  });

  describe("Step 3: Audit Log Search - New Features (Issue #913)", () => {

    it("should search audit logs without from/to (全期間検索)", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?limit=50`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("✅ Audit logs retrieved (全期間):", response.status, `total=${response.data.total_count}`);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data).toHaveProperty("total_count");
      expect(response.data.list.length).toBeGreaterThan(0);
    });

    it("should filter by outcome_result=success", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?outcome_result=success&limit=20`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("✅ Success logs retrieved:", response.status, `count=${response.data.list.length}`);
      expect(response.status).toBe(200);
      expect(response.data.list.length).toBeGreaterThan(0);

      // Verify all results have outcome_result=success
      response.data.list.forEach(log => {
        expect(log.outcome_result).toBe("success");
      });
    });

    it("should filter by target_tenant_id", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?target_tenant_id=${tenantId}&limit=20`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("✅ Target tenant logs retrieved:", response.status, `count=${response.data.list.length}`);
      expect(response.status).toBe(200);

      if (response.data.list.length > 0) {
        // Verify all results have the correct target_tenant_id
        response.data.list.forEach(log => {
          expect(log.target_tenant_id).toBe(tenantId);
        });
      }
    });

    it("should filter by dry_run=false (本番実行のみ)", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?dry_run=false&limit=20`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("✅ Dry run=false logs retrieved:", response.status, `count=${response.data.list.length}`);
      expect(response.status).toBe(200);

      // Verify all results have dry_run=false
      response.data.list.forEach(log => {
        expect(log.dry_run).toBe(false);
      });
    });

    it("should search multiple types (tenant_create,client_create)", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?type=tenant_create,client_create&limit=50`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("✅ Multiple types retrieved:", response.status, `count=${response.data.list.length}`);
      expect(response.status).toBe(200);
      expect(response.data.list.length).toBeGreaterThan(0);

      // Verify all results match one of the specified types
      response.data.list.forEach(log => {
        expect(["tenant_create", "client_create"]).toContain(log.type);
      });
    });

    it("should validate limit range (1-1000)", async () => {
      // Test limit=0 (should fail)
      const response1 = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?limit=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("❌ limit=0 validation:", response1.status);
      expect(response1.status).toBe(400);

      // Test limit=1001 (should fail)
      const response2 = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?limit=1001`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("❌ limit=1001 validation:", response2.status);
      expect(response2.status).toBe(400);

      // Test limit=1000 (should succeed)
      const response3 = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?limit=1000`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("✅ limit=1000 validation:", response3.status);
      expect(response3.status).toBe(200);
    });

    it("should combine multiple filters", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?type=tenant_create,client_create&outcome_result=success&dry_run=false&limit=30`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("✅ Combined filters:", response.status, `count=${response.data.list.length}`);
      expect(response.status).toBe(200);

      // Verify all filters are applied
      response.data.list.forEach(log => {
        expect(["tenant_create", "client_create"]).toContain(log.type);
        expect(log.outcome_result).toBe("success");
        expect(log.dry_run).toBe(false);
      });
    });
  });

  describe("Cleanup", () => {
    it("should delete the test client", async () => {
      if (!clientId) {
        console.log("⏭️ Skipping client deletion (not created)");
        return;
      }

      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients/${clientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });

      console.log("🧹 Client deleted:", deleteResponse.status);
      expect(deleteResponse.status).toBe(204);
    });

    it("should delete the test tenant", async () => {
      if (!tenantId) {
        console.log("⏭️ Skipping tenant deletion (not created)");
        return;
      }

      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });

      console.log("🧹 Tenant deleted:", deleteResponse.status);
      expect(deleteResponse.status).toBe(204);
    });
  });
});
