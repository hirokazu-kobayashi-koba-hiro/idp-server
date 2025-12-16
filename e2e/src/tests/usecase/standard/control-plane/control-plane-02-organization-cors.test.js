import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson, options } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { generateECP256JWKS } from "../../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Standard Use Case: Organization-level CORS Configuration
 *
 * This test verifies that organization management endpoints use the
 * organization's ORGANIZER tenant CORS configuration instead of the
 * Admin tenant CORS configuration.
 *
 * Related Issue: #1068 - DynamicCorsFilter does not use organization-specific
 * CORS settings for /management/organizations/{org_id}/ endpoints
 *
 * Test Scenario:
 * 1. Create organization with a tenant that has specific CORS settings
 * 2. Send request to organization endpoint with Origin header
 * 3. Verify Access-Control-Allow-Origin matches organization's CORS config
 */
describe("Standard Use Case: Organization-level CORS Configuration", () => {
  let systemAccessToken;

  beforeAll(async () => {
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
    systemAccessToken = tokenResponse.data.access_token;
  });

  it("should return organization's CORS settings for organization management API", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const adminUserId = uuidv4();
    const clientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@cors-test.example.com`;
    const orgAdminPassword = `AdminPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    // Organization-specific CORS origin (different from admin tenant)
    const orgCorsOrigin = "https://org-frontend.example.com";

    console.log("\n=== Step 1: Create Organization with specific CORS settings ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `CORS Test Org ${timestamp}`,
        description: `E2E test organization for CORS created at ${new Date().toISOString()}`,
      },
      tenant: {
        id: tenantId,
        name: `CORS Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `CORS_SESSION_${organizationId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: {
          allow_origins: [orgCorsOrigin, backendUrl],
          allow_credentials: true,
          allow_headers: "Content-Type, Authorization, X-Requested-With",
          allow_methods: "GET, POST, PUT, DELETE, PATCH, OPTIONS",
        },
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_client_id: true,
          persistence_enabled: true,
          include_detail: true,
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: ["authorization_code", "refresh_token", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management", "org-management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        extension: {
          access_token_type: "JWT",
          access_token_duration: 3600,
          id_token_duration: 3600,
          refresh_token_duration: 86400,
        },
      },
      user: {
        sub: adminUserId,
        provider_id: "idp-server",
        name: "CORS Test Admin User",
        email: orgAdminEmail,
        email_verified: true,
        raw_password: orgAdminPassword,
      },
      client: {
        client_id: clientId,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management org-management",
        client_name: `CORS Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
      body: onboardingRequest,
    });

    if (createResponse.status !== 201) {
      console.error("Onboarding failed:", JSON.stringify(createResponse.data, null, 2));
    }
    expect(createResponse.status).toBe(201);
    console.log(`✅ Organization created: ${organizationId}`);
    console.log(`   Tenant ID: ${tenantId}`);
    console.log(`   CORS Origin: ${orgCorsOrigin}`);

    console.log("\n=== Step 2: Get org-management token ===");

    const orgAdminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile email management",
      clientId: clientId,
      clientSecret: orgClientSecret,
    });

    expect(orgAdminTokenResponse.status).toBe(200);
    const orgAccessToken = orgAdminTokenResponse.data.access_token;
    console.log("✅ Got management access token");

    console.log("\n=== Step 3: Send OPTIONS preflight request to organization endpoint ===");

    // Send OPTIONS preflight request with organization-specific Origin
    const preflightResponse = await options({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: {
        "Origin": orgCorsOrigin,
        "Access-Control-Request-Method": "GET",
        "Access-Control-Request-Headers": "Authorization, Content-Type",
      },
    });

    console.log(`   Preflight status: ${preflightResponse.status}`);
    console.log(`   Access-Control-Allow-Origin: ${preflightResponse.headers["access-control-allow-origin"]}`);
    console.log(`   Access-Control-Allow-Methods: ${preflightResponse.headers["access-control-allow-methods"]}`);
    console.log(`   Access-Control-Allow-Headers: ${preflightResponse.headers["access-control-allow-headers"]}`);

    // Verify CORS headers
    expect(preflightResponse.status).toBe(200);

    // Key assertion: The CORS origin should match organization's CORS config
    // If bug #1068 is NOT fixed, this will return admin tenant's domain instead
    const allowOrigin = preflightResponse.headers["access-control-allow-origin"];
    console.log(`\n   Expected Origin: ${orgCorsOrigin}`);
    console.log(`   Actual Origin: ${allowOrigin}`);

    expect(allowOrigin).toBe(orgCorsOrigin);
    console.log("✅ CORS preflight returned correct organization-specific origin");

    console.log("\n=== Step 4: Send actual request with Origin header ===");

    const actualResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: {
        "Authorization": `Bearer ${orgAccessToken}`,
        "Origin": orgCorsOrigin,
      },
    });

    console.log(`   Response status: ${actualResponse.status}`);
    console.log(`   Access-Control-Allow-Origin: ${actualResponse.headers["access-control-allow-origin"]}`);

    // Verify CORS header in actual response
    const actualAllowOrigin = actualResponse.headers["access-control-allow-origin"];
    expect(actualAllowOrigin).toBe(orgCorsOrigin);
    console.log("✅ Actual request returned correct organization-specific CORS origin");

    console.log("\n=== Step 5: Verify system management API uses Admin tenant CORS ===");

    // Send request to system-level management API (not organization-specific)
    // This should use Admin tenant's CORS settings
    const systemResponse = await options({
      url: `${backendUrl}/v1/management/tenants`,
      headers: {
        "Origin": adminServerConfig.frontendUrl || backendUrl,
        "Access-Control-Request-Method": "GET",
        "Access-Control-Request-Headers": "Authorization, Content-Type",
      },
    });

    console.log(`   System API preflight status: ${systemResponse.status}`);
    console.log(`   Access-Control-Allow-Origin: ${systemResponse.headers["access-control-allow-origin"]}`);

    expect(systemResponse.status).toBe(200);
    console.log("✅ System management API returned CORS headers");

    console.log("\n=== Step 6: Cleanup ===");

    // Delete tenant first (foreign key constraint)
    const deleteTenantResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });
    expect(deleteTenantResponse.status).toBe(204);
    console.log("🧹 Tenant deleted");

    // Delete organization
    const deleteOrgResponse = await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
    });
    expect(deleteOrgResponse.status).toBe(204);
    console.log("🧹 Organization deleted");

    console.log("\n=== Test completed successfully ===");
  });

  it("should verify CORS header difference between organization and admin tenant", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const adminUserId = uuidv4();
    const clientId = uuidv4();
    const orgAdminEmail = `admin2-${timestamp}@cors-test.example.com`;
    const orgAdminPassword = `AdminPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    // Unique organization CORS origin to distinguish from admin
    const uniqueOrgOrigin = `https://unique-org-${timestamp}.example.com`;

    console.log("\n=== Test: Verify CORS difference between org and admin ===");
    console.log(`   Organization unique origin: ${uniqueOrgOrigin}`);

    // Create organization with unique CORS origin
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `CORS Diff Test ${timestamp}`,
        description: `Testing CORS configuration difference`,
      },
      tenant: {
        id: tenantId,
        name: `CORS Diff Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `CORS_DIFF_${organizationId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: {
          allow_origins: [uniqueOrgOrigin],
          allow_credentials: true,
          allow_headers: "Content-Type, Authorization",
          allow_methods: "GET, POST, PUT, DELETE, PATCH, OPTIONS",
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: ["password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        extension: {
          access_token_type: "JWT",
          access_token_duration: 3600,
        },
      },
      user: {
        sub: adminUserId,
        provider_id: "idp-server",
        name: "CORS Diff Test User",
        email: orgAdminEmail,
        email_verified: true,
        raw_password: orgAdminPassword,
      },
      client: {
        client_id: clientId,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["password"],
        scope: "openid profile management",
        client_name: `CORS Diff Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
      body: onboardingRequest,
    });

    expect(createResponse.status).toBe(201);
    console.log(`✅ Created organization with unique CORS origin`);

    // Test organization endpoint with unique origin
    const orgPreflightResponse = await options({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: {
        "Origin": uniqueOrgOrigin,
        "Access-Control-Request-Method": "GET",
      },
    });

    console.log(`\n   Organization endpoint:`);
    console.log(`   - Request Origin: ${uniqueOrgOrigin}`);
    console.log(`   - Response Allow-Origin: ${orgPreflightResponse.headers["access-control-allow-origin"]}`);

    // This is the key assertion - if #1068 is not fixed, this will fail
    // because it will return admin tenant's domain instead of uniqueOrgOrigin
    const orgAllowOrigin = orgPreflightResponse.headers["access-control-allow-origin"];

    // Bug behavior: returns admin tenant domain (e.g., backendUrl)
    // Fixed behavior: returns organization's CORS origin (uniqueOrgOrigin)
    if (orgAllowOrigin === uniqueOrgOrigin) {
      console.log("✅ Bug #1068 is FIXED: Organization CORS origin correctly returned");
    } else {
      console.log("❌ Bug #1068 NOT fixed: Got admin tenant origin instead of organization origin");
      console.log(`   Expected: ${uniqueOrgOrigin}`);
      console.log(`   Actual: ${orgAllowOrigin}`);
    }

    expect(orgAllowOrigin).toBe(uniqueOrgOrigin);

    // Cleanup
    console.log("\n=== Cleanup ===");

    // Get org-management token for cleanup
    const cleanupTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile management org-management",
      clientId: clientId,
      clientSecret: orgClientSecret,
    });
    expect(cleanupTokenResponse.status).toBe(200);
    const cleanupToken = cleanupTokenResponse.data.access_token;

    // Delete tenant first (foreign key constraint)
    const deleteTenantResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: {
        Authorization: `Bearer ${cleanupToken}`,
      },
    });
    expect(deleteTenantResponse.status).toBe(204);
    console.log("🧹 Tenant deleted");

    // Delete organization
    const deleteOrgResponse = await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
    });
    expect(deleteOrgResponse.status).toBe(204);
    console.log("🧹 Organization deleted");

    console.log("✅ Test completed");
  });
});
