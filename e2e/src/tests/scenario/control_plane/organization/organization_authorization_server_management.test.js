import { describe, expect, it } from "@jest/globals";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";

describe("organization authorization server management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";

  describe("success pattern", () => {
    it("create tenant, update authorization server, and delete tenant", async () => {
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

      // Step 1: Create a new tenant within the organization
      const timestamp = Date.now();
      const newTenantId = uuidv4();
      const createTenantResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": newTenantId,
            "name": `Test Tenant ${timestamp}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for authorization server management",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": `http://localhost:8080/${newTenantId}`,
            "authorization_endpoint": `http://localhost:8080/${newTenantId}/v1/authorizations`,
            "token_endpoint": `http://localhost:8080/${newTenantId}/v1/tokens`,
            "userinfo_endpoint": `http://localhost:8080/${newTenantId}/v1/userinfo`,
            "jwks_uri": `http://localhost:8080/${newTenantId}/v1/jwks`,
            "scopes_supported": [
              "openid",
              "profile",
              "email"
            ],
            "response_types_supported": [
              "code"
            ],
            "response_modes_supported": [
              "query",
              "fragment"
            ],
            "subject_types_supported": [
              "public"
            ],
            "grant_types_supported": [
              "authorization_code",
              "refresh_token"
            ],
            "token_endpoint_auth_methods_supported": [
              "client_secret_post",
              "client_secret_basic"
            ]
          }
        }
      });
      console.log("Create tenant response:", createTenantResponse.data);
      expect(createTenantResponse.status).toBe(201);
      expect(createTenantResponse.data).toHaveProperty("result");
      expect(createTenantResponse.data.result.id).toBe(newTenantId);
      console.log("✅ New tenant created successfully");

      // Step 2: Get current authorization server configuration
      const getAuthServerResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Get auth server response:", getAuthServerResponse.data);
      expect(getAuthServerResponse.status).toBe(200);
      expect(getAuthServerResponse.data).toHaveProperty("issuer");
      expect(getAuthServerResponse.data.issuer).toBe(`http://localhost:8080/${newTenantId}`);
      console.log("✅ Authorization server configuration retrieved successfully");

      // Step 3: Test dry run for authorization server update
      const dryRunUpdateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "issuer": `https://updated-test-issuer.example.com/${newTenantId}`,
          "authorization_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/authorize`,
          "token_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/token`,
          "userinfo_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/userinfo`,
          "jwks_uri": `https://updated-test-issuer.example.com/${newTenantId}/.well-known/jwks.json`,
          "scopes_supported": ["openid", "profile", "email", "phone"],
          "response_types_supported": ["code", "id_token"],
          "response_modes_supported": ["query", "fragment"],
          "subject_types_supported": ["public", "pairwise"],
          "grant_types_supported": ["authorization_code", "refresh_token", "client_credentials"]
        }
      });
      console.log("Dry run update response:", dryRunUpdateResponse.data);
      expect(dryRunUpdateResponse.status).toBe(200);
      expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);
      console.log("✅ Dry run update functionality verified");

      // Step 4: Update authorization server configuration
      const updateAuthServerResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "issuer": `https://updated-test-issuer.example.com/${newTenantId}`,
          "authorization_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/authorize`,
          "token_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/token`,
          "userinfo_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/userinfo`,
          "jwks_uri": `https://updated-test-issuer.example.com/${newTenantId}/.well-known/jwks.json`,
          "scopes_supported": ["openid", "profile", "email", "phone"],
          "response_types_supported": ["code", "id_token"],
          "response_modes_supported": ["query", "fragment"],
          "subject_types_supported": ["public", "pairwise"],
          "grant_types_supported": ["authorization_code", "refresh_token", "client_credentials"]
        }
      });
      console.log("Update auth server response:", updateAuthServerResponse.data);
      expect(updateAuthServerResponse.status).toBe(200);
      expect(updateAuthServerResponse.data).toBeDefined();
      console.log("✅ Authorization server configuration updated successfully");

      // Step 5: Verify the configuration was updated
      const verifyAuthServerResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Verify auth server response:", verifyAuthServerResponse.data);
      expect(verifyAuthServerResponse.status).toBe(200);
      expect(verifyAuthServerResponse.data).toHaveProperty("issuer", `https://updated-test-issuer.example.com/${newTenantId}`);
      expect(verifyAuthServerResponse.data).toHaveProperty("jwks_uri", `https://updated-test-issuer.example.com/${newTenantId}/.well-known/jwks.json`);
      expect(verifyAuthServerResponse.data.scopes_supported).toContain("phone");
      expect(verifyAuthServerResponse.data.response_types_supported).toContain("id_token");
      expect(verifyAuthServerResponse.data.subject_types_supported).toContain("pairwise");
      console.log("✅ Authorization server configuration update verified");

      // Step 6: Clean up - Delete the test tenant
      const deleteTenantResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete tenant response:", deleteTenantResponse.status);
      expect(deleteTenantResponse.status).toBe(204);
      console.log("✅ Test tenant deleted successfully");

      console.log("🎉 All authorization server management tests passed!");
    });

    it("error scenarios", async () => {
      // Get OAuth token
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

      const existingTenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

      // Test 1: Try with invalid authorization
      const unauthorizedResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/authorization-server`,
        headers: {
          Authorization: "Bearer invalid-token"
        }
      });
      console.log("Unauthorized response:", unauthorizedResponse.status);
      expect(unauthorizedResponse.status).toBe(401);
      console.log("✅ Invalid token returns 401");

      // Test 2: Try with invalid organization ID
      const invalidOrgId = "00000000-0000-0000-0000-000000000000";
      const invalidOrgResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${invalidOrgId}/tenants/${existingTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid organization response:", invalidOrgResponse.status);
      expect(invalidOrgResponse.status).toBe(404);
      console.log("✅ Invalid organization returns 404");

      // Test 3: Try with invalid tenant ID
      const invalidTenantId = "00000000-0000-0000-0000-000000000000";
      const invalidTenantResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${invalidTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid tenant response:", invalidTenantResponse.status);
      expect(invalidTenantResponse.status).toBe(403);
      console.log("✅ Invalid tenant returns 403");

      // Test 4: Try with missing required field (missing jwks_uri)
      const invalidUpdateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "issuer": "https://example.com",
          "authorization_endpoint": "https://example.com/authorize",
          "token_endpoint": "https://example.com/token"
          // Missing required fields: jwks_uri, scopes_supported, response_types_supported, response_modes_supported, subject_types_supported
        }
      });
      console.log("Invalid update response:", invalidUpdateResponse.status);
      expect(invalidUpdateResponse.status).toBe(400);
      console.log("✅ Missing required fields returns 400");
    });
  });
});