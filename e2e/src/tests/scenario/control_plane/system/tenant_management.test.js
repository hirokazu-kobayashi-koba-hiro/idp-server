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
