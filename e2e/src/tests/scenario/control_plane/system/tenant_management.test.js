import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, putWithJson } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";

/**
 * System-Level Tenant Management API - E2E Tests
 *
 * Tests for /v1/management/tenants/{id} endpoints
 * - GET /v1/management/tenants/{id}
 * - PUT /v1/management/tenants/{id}
 * - DELETE /v1/management/tenants/{id}
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
    it("should retrieve existing tenant", async () => {
      // Use admin tenant ID that we know exists
      const response = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      console.log("GET existing tenant response:", response.status, response.data.id);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("id", adminServerConfig.tenantId);
      expect(response.data).toHaveProperty("name");
      expect(response.data).toHaveProperty("tenant_domain");
    });

    it("should update existing tenant", async () => {
      const response = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "tenant_domain": "http://localhost:8080",
          "enabled": true
        }
      });

      console.log("PUT existing tenant response:", response.status);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("result");
      expect(response.data.result).toHaveProperty("id", adminServerConfig.tenantId);
      expect(response.data.result).toHaveProperty("enabled", true);
    });

    it("should support dry-run update", async () => {
      const response = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}?dry_run=true`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "tenant_domain": "http://localhost:8080",
          "enabled": false
        }
      });

      console.log("Dry-run update response:", response.status);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("dry_run", true);
      expect(response.data).toHaveProperty("result");

      // Verify tenant was not actually updated
      const verifyResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(verifyResponse.status).toBe(200);
      expect(verifyResponse.data).toHaveProperty("enabled", true); // Should still be true
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
            "tenant_domain": "http://localhost:8080",
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
