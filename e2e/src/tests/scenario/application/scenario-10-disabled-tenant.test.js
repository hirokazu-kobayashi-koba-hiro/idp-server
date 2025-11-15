import { describe, expect, it } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations
} from "../../../api/oauthClient";
import {
  backendUrl,
  serverConfig
} from "../../testConfig";
import { v4 as uuidv4 } from "uuid";

describe("Disabled Tenant Scenario", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";

  it("should work when enabled, then fail when disabled", async () => {
    // Step 1: Get admin access token with org-management scope
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    console.log("Admin token response:", adminTokenResponse.status);
    expect(adminTokenResponse.status).toBe(200);
    const adminAccessToken = adminTokenResponse.data.access_token;

    // Step 2: Create new tenant
    const timestamp = Date.now();
    const tenantId = uuidv4();
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`
      },
      body: {
        tenant: {
          "id": tenantId,
          "name": `Disable Test Tenant ${timestamp}`,
          "domain": "http://localhost:8080",
          "description": "Test tenant for disable scenario",
          "authorization_provider": "idp-server",
          "tenant_type": "BUSINESS"
        },
        authorization_server: {
          "issuer": `http://localhost:8080/${tenantId}`,
          "authorization_endpoint": `http://localhost:8080/${tenantId}/v1/authorizations`,
          "token_endpoint": `http://localhost:8080/${tenantId}/v1/tokens`,
          "token_endpoint_auth_methods_supported": ["client_secret_post"],
          "token_endpoint_auth_signing_alg_values_supported": ["RS256"],
          "userinfo_endpoint": `http://localhost:8080/${tenantId}/v1/userinfo`,
          "jwks_uri": `http://localhost:8080/${tenantId}/v1/jwks`,
          "jwks": "{ \"keys\": [ { \"kty\": \"EC\", \"d\": \"yIWDrlhnCy3yL9xLuqZGOBFFq4PWGsCeM7Sc_lfeaQQ\", \"use\": \"sig\", \"crv\": \"P-256\", \"kid\": \"access_token\", \"x\": \"iWJINqt0ySv3kVEvlHbvNkPKY2pPSf1cG1PSx3tRfw0\", \"y\": \"rW1FdfXK5AQcv-Go6Xho0CR5AbLai7Gp9IdLTIXTSIQ\", \"alg\": \"ES256\" }] }",
          "grant_types_supported": ["authorization_code", "password", "refresh_token"],
          "token_signed_key_id": "access_token",
          "id_token_signed_key_id": "access_token",
          "scopes_supported": ["openid", "profile", "email", "account"],
          "response_types_supported": ["code"],
          "response_modes_supported": ["query"],
          "subject_types_supported": ["public"],
          "id_token_signing_alg_values_supported": ["ES256"],
          "extension": {
            "access_token_type": "JWT",
            "token_signed_key_id": "access_token",
            "id_token_signed_key_id": "access_token",
            "access_token_duration": 3600,
            "id_token_duration": 3600
          }
        }
      }
    });
    console.log("Create tenant response:", createTenantResponse.status);
    expect(createTenantResponse.status).toBe(201);

    try {
      // Step 3: Create client configuration
      const clientId = uuidv4();
      const clientSecret = "test-client-secret-12345678901234567890";
      const createClientResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`
        },
        body: {
          "client_id": clientId,
          "client_name": "Test Client for Disable Scenario",
          "client_secret": clientSecret,
          "redirect_uris": ["https://example.com/callback"],
          "grant_types": ["authorization_code", "password"],
          "response_types": ["code"],
          "token_endpoint_auth_method": "client_secret_post",
          "scope": "openid profile email account"
        }
      });
      console.log("Create client response:", createClientResponse.data);
      expect(createClientResponse.status).toBe(201);

      // Step 4: Create test user
      const userId = uuidv4();
      const username = `testuser${timestamp}`;
      const email = `testuser${timestamp}@example.com`;
      const preUsername = email;
      const password = "TestPassword123!";
      const createUserResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`
        },
        body: {
          "sub": userId,
          "provider_id": "idp-server",
          "name": username,
          "email": email,
          "email_verified": true,
          "raw_password": password
        }
      });
      console.log("Create user response:", createUserResponse.status);
      expect(createUserResponse.status).toBe(201);

      // Step 5: Test authorization request (should succeed)
      const authEndpoint = `${backendUrl}/${tenantId}/v1/authorizations`;
      const authResponseEnabled = await getAuthorizations({
        endpoint: authEndpoint,
        clientId: clientId,
        responseType: "code",
        state: "test-state",
        scope: "openid profile",
        redirectUri: "https://example.com/callback"
      });
      console.log("Authorization response (enabled):", authResponseEnabled.status);
      expect(authResponseEnabled.status).toBe(302);

      // Step 6: Test password grant token request (should succeed)
      const tokenEndpoint = `${backendUrl}/${tenantId}/v1/tokens`;
      const tokenResponseEnabled = await requestToken({
        endpoint: tokenEndpoint,
        grantType: "password",
        username: preUsername,
        password: password,
        scope: "account",
        clientId: clientId,
        clientSecret: clientSecret
      });
      console.log("Token response (enabled):", tokenResponseEnabled.data);
      expect(tokenResponseEnabled.status).toBe(200);
      expect(tokenResponseEnabled.data).toHaveProperty("access_token");

      // Step 7: Disable the tenant
      const tenantConfigResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`
        }
      });
      expect(tenantConfigResponse.status).toBe(200);
      const tenantConfig = tenantConfigResponse.data;

      const disableResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`
        },
        body: {
          ...tenantConfig,
          enabled: false
        }
      });
      console.log("Disable tenant response:", disableResponse.status);
      expect(disableResponse.status).toBe(200);

      // Step 8: Test authorization request (should fail)
        const authResponseDisabled = await getAuthorizations({
          endpoint: authEndpoint,
          clientId: clientId,
          responseType: "code",
          state: "test-state-2",
          scope: "openid profile",
          redirectUri: "https://example.com/callback"
        });
        console.log("Authorization response (disabled):", authResponseDisabled.status);
        expect(authResponseDisabled.status).toBeGreaterThanOrEqual(400);

      // Step 9: Test password grant token request (should fail)
        const tokenResponseDisabled = await requestToken({
          endpoint: tokenEndpoint,
          grantType: "password",
          username: `testuser${timestamp}@example.com`,
          password: password,
          scope: "account",
          clientId: clientId,
          clientSecret: clientSecret
        });
        console.log("Token response (disabled):", tokenResponseDisabled.status);
        expect(tokenResponseDisabled.status).toBeGreaterThanOrEqual(400);


    } finally {
      // Cleanup: Delete the tenant
      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`
        }
      });
      console.log("Delete tenant response:", deleteResponse.status);
      expect(deleteResponse.status).toBe(204);
    }
  });
});
