import { describe, expect, it, beforeAll } from "@jest/globals";
import { postWithJson } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";

/**
 * Organization Role Management API - 実際の動作調査
 * 仕様と実装の差異を特定するためのデバッグテスト
 */
describe("Organization Role Management API - Debug Behavior", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  let accessToken;
  let testTenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9"; // 既存テナントを使用

  beforeAll(async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    accessToken = tokenResponse.data.access_token;
  });

  describe("Debug API Responses", () => {

    it("should debug missing name field response", async () => {
      console.log("🧪 Testing missing 'name' field...");

      const response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          // name field missing intentionally
          "permissions": []
        }
      });

      console.log("📨 Status:", response.status);
      console.log("📨 Response:", JSON.stringify(response.data, null, 2));

      // Log details for analysis
      expect(response.status).toBeGreaterThanOrEqual(400);
    });

    it("should debug missing permissions field response", async () => {
      console.log("🧪 Testing missing 'permissions' field...");

      const response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "name": "test-role-no-permissions"
          // permissions field missing intentionally
        }
      });

      console.log("📨 Status:", response.status);
      console.log("📨 Response:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBeGreaterThanOrEqual(400);
    });

    it("should debug invalid data type response", async () => {
      console.log("🧪 Testing invalid data types...");

      const response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "name": 12345, // invalid type
          "permissions": "not-an-array" // invalid type
        }
      });

      console.log("📨 Status:", response.status);
      console.log("📨 Response:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBeGreaterThanOrEqual(400);
    });

    it("should debug successful role creation response structure", async () => {
      console.log("🧪 Testing successful role creation...");

      // First create a permission
      const permissionResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "name": `debug-permission-${Date.now()}`,
          "description": "Debug permission"
        }
      });

      if (permissionResponse.status === 201) {
        const permissionId = permissionResponse.data.result.id;
        console.log("✅ Permission created:", permissionId);

        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": `debug-role-${Date.now()}`,
            "description": "Debug role for testing",
            "permissions": [permissionId]
          }
        });

        console.log("📨 Status:", response.status);
        console.log("📨 Response structure:");
        console.log(JSON.stringify(response.data, null, 2));

        if (response.status === 201) {
          console.log("✅ Role created successfully");
          console.log("🔍 Response analysis:");
          console.log("- Has 'dry_run' field:", 'dry_run' in response.data);
          console.log("- Has 'result' field:", 'result' in response.data);
          if (response.data.result) {
            console.log("- Result has 'id':", 'id' in response.data.result);
            console.log("- Result has 'name':", 'name' in response.data.result);
            console.log("- Result has 'description':", 'description' in response.data.result);
            console.log("- Result has 'permissions':", 'permissions' in response.data.result);
            console.log("- Permissions is array:", Array.isArray(response.data.result.permissions));
          }
        } else {
          console.log("❌ Role creation failed");
        }

        expect(response.status).toBeGreaterThanOrEqual(200);
      } else {
        console.log("❌ Permission creation failed, skipping role test");
        console.log("Permission response:", permissionResponse.status, permissionResponse.data);
      }
    });

    it("should debug dry_run functionality", async () => {
      console.log("🧪 Testing dry_run functionality...");

      // Create a permission first
      const permissionResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/permissions`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "name": `dry-run-permission-${Date.now()}`,
          "description": "Dry run permission"
        }
      });

      if (permissionResponse.status === 201) {
        const permissionId = permissionResponse.data.result.id;

        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${testTenantId}/roles?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            "name": `dry-run-role-${Date.now()}`,
            "description": "Dry run role",
            "permissions": [permissionId]
          }
        });

        console.log("📨 Dry Run Status:", response.status);
        console.log("📨 Dry Run Response:");
        console.log(JSON.stringify(response.data, null, 2));

        if (response.data && 'dry_run' in response.data) {
          console.log("✅ dry_run field present:", response.data.dry_run);
        } else {
          console.log("❌ dry_run field missing");
        }

        expect(response.status).toBeGreaterThanOrEqual(200);
      }
    });
  });
});