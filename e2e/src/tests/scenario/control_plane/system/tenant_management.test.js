import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";

/**
 * System-Level Tenant Management API - E2E Tests
 *
 * Tests for /v1/management/tenants endpoints
 * - POST /v1/management/tenants (create)
 * - GET /v1/management/tenants (list)
 * - GET /v1/management/tenants/{id} (get)
 * - PUT /v1/management/tenants/{id} (update)
 * - DELETE /v1/management/tenants/{id} (delete)
 */
describe("System-Level Tenant Management API", () => {
  let accessToken;

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

  describe("Success Pattern", () => {
    it("crud operations", async () => {
      const timestamp = Date.now();

      // Create a new tenant
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": uuidv4(),
            "name": `System Tenant ${timestamp}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for system management",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": `http://localhost:8080/${adminServerConfig.tenantId}`,
            "authorization_endpoint": `http://localhost:8080/${adminServerConfig.tenantId}/v1/authorizations`,
            "token_endpoint": `http://localhost:8080/${adminServerConfig.tenantId}/v1/tokens`,
            "userinfo_endpoint": `http://localhost:8080/${adminServerConfig.tenantId}/v1/userinfo`,
            "jwks_uri": `http://localhost:8080/${adminServerConfig.tenantId}/v1/jwks`,
            "scopes_supported": [
              "openid",
              "profile",
              "email",
              "account",
              "management"
            ],
            "response_types_supported": ["code"],
            "response_modes_supported": ["query"],
            "subject_types_supported": ["public"],
            "grant_types_supported": [
              "authorization_code",
              "refresh_token",
              "password",
              "client_credentials"
            ],
            "token_endpoint_auth_methods_supported": [
              "client_secret_post",
              "client_secret_basic"
            ],
            "extension": {
              "access_token_type": "JWT",
              "access_token_duration": 3600,
              "id_token_duration": 3600
            }
          }
        }
      });
      console.log("Create tenant response:", createResponse.status, createResponse.data);
      expect(createResponse.status).toBe(201);
      expect(createResponse.data).toHaveProperty("result");

      const tenantId = createResponse.data.result.id;

      // List tenants
      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List tenants response:", listResponse.status, `(${listResponse.data.list.length} tenants)`);
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(Array.isArray(listResponse.data.list)).toBe(true);

      // Get specific tenant details
      const detailResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Tenant detail response:", detailResponse.status, detailResponse.data.id);
      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data).toHaveProperty("id", tenantId);
      expect(detailResponse.data).toHaveProperty("name");

      // Update tenant
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
            "name": `Updated Organization Tenant ${timestamp}`,
            "description": "Updated description for organization tenant",
            "domain": "BUSINESS"
        }
      });
      console.log("Update tenant response:", updateResponse.status);
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data).toHaveProperty("result");

      // Delete tenant
      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete tenant response:", deleteResponse.status);
      expect(deleteResponse.status).toBe(204);
    });
  });

  describe("Resource Not Found Tests", () => {
    describe("GET /v1/management/tenants/{id}", () => {
      it("should return 404 for non-existent tenant ID", async () => {
        const nonExistentId = uuidv4();
        console.log("Testing GET with non-existent tenant ID:", nonExistentId);

        const response = await get({
          url: `${backendUrl}/v1/management/tenants/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        console.log("GET Response:", response.status, response.data);
        expect(response.status).toBe(404);
        expect(response.data).toHaveProperty("error");
        expect(response.data.error).toBe("not_found");
      });
    });

    describe("PUT /v1/management/tenants/{id}", () => {
      it("should return 404 for non-existent tenant ID", async () => {
        const nonExistentId = uuidv4();
        console.log("Testing PUT with non-existent tenant ID:", nonExistentId);

        const response = await putWithJson({
          url: `${backendUrl}/v1/management/tenants/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "domain": "http://localhost:8080",
            "enabled": true
          }
        });

        console.log("PUT Response:", response.status, response.data);
        expect(response.status).toBe(404);
        expect(response.data).toHaveProperty("error");
        expect(response.data.error).toBe("not_found");
      });
    });

    describe("DELETE /v1/management/tenants/{id}", () => {
      it("should return 404 for non-existent tenant ID", async () => {
        const nonExistentId = uuidv4();
        console.log("Testing DELETE with non-existent tenant ID:", nonExistentId);

        const response = await deletion({
          url: `${backendUrl}/v1/management/tenants/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        console.log("DELETE Response:", response.status, response.data);
        expect(response.status).toBe(404);
        expect(response.data).toHaveProperty("error");
        expect(response.data.error).toBe("not_found");
      });
    });
  });

  describe("Error Response Format Validation", () => {
    it("should return consistent error format for 404 responses", async () => {
      const nonExistentId = uuidv4();

      const getResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${nonExistentId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      expect(getResponse.status).toBe(404);
      expect(getResponse.data).toHaveProperty("error");
      expect(getResponse.data).toHaveProperty("error_description");
      expect(getResponse.data.error).toBe("not_found");

      console.log("404 Error format validated:", getResponse.data);
    });
  });
});
