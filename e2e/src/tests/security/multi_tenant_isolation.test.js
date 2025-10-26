import { describe, expect, it } from "@jest/globals";
import {
  adminServerConfig,
  serverConfig,
  clientSecretPostClient,
} from "../testConfig";
import { inspectToken, requestToken } from "../../api/oauthClient";
import { get } from "../../lib/http";
import { requestAuthorizations } from "../../oauth/request";

/**
 * Multi-Tenant API Isolation Test
 *
 * Validates that API layer properly enforces tenant isolation by verifying
 * that access tokens from one tenant cannot access resources in another tenant.
 *
 * Note: This test validates the API layer (first line of defense).
 * For database-level RLS validation, see multi_tenant_rls.test.js (TODO: Issue #734)
 *
 * Related Issue: #734 - マルチテナント分離のための行レベルセキュリティ(RLS)の完全性を検証
 */
describe("Security: Multi-Tenant API Isolation (Issue #734)", () => {
  /**
   * Test Strategy:
   * 1. Authenticate as a user in Tenant A
   * 2. Attempt to access resources from Tenant B using Tenant A's token
   * 3. Verify that access is properly denied (401/400)
   *
   * This validates that:
   * - API layer enforces tenant context via access token validation
   * - Cross-tenant access attempts are blocked at API layer
   * - Proper HTTP error codes are returned (401 Unauthorized / 400 Bad Request)
   */

  it("should not access other tenant's user information", async () => {
    console.log("\n" + "=".repeat(80));
    console.log("MULTI-TENANT ISOLATION TEST: User Information Access");
    console.log("=".repeat(80) + "\n");

    // =====================================================================
    // Step 1: Authenticate in Tenant A and get access token
    // =====================================================================
    console.log("📋 Step 1: Authenticate in Tenant A");
    console.log("-".repeat(80));

    const authResult = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      scope: clientSecretPostClient.scope,
      responseType: "code",
      clientId: clientSecretPostClient.clientId,
      redirectUri: clientSecretPostClient.redirectUri,
      state: "test-state-" + Date.now(),
      nonce: "test-nonce-" + Date.now(),
    });

    expect(authResult.status).toBe(200);
    expect(authResult.authorizationResponse.code).toBeDefined();
    console.log("✅ Authorization successful in Tenant A");

    // Exchange code for token
    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authResult.authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.access_token).toBeDefined();
    const tenantAAccessToken = tokenResponse.data.access_token;
    console.log("✅ Obtained access token from Tenant A");

    // =====================================================================
    // Step 2: Get user info in Tenant A (should succeed)
    // =====================================================================
    console.log(
      "\n📋 Step 2: Access own user info in Tenant A (should succeed)"
    );
    console.log("-".repeat(80));

    const tenantAUserInfo = await get({
      url: serverConfig.userinfoEndpoint,
      headers: {
        Authorization: `Bearer ${tenantAAccessToken}`,
      },
    });

    expect(tenantAUserInfo.status).toBe(200);
    expect(tenantAUserInfo.data.sub).toBeDefined();
    console.log("✅ Successfully retrieved own user info");
    console.log(`   User sub: ${tenantAUserInfo.data.sub}`);

    // =====================================================================
    // Step 3: Attempt to access Tenant B's userinfo with Tenant A token
    // =====================================================================
    console.log("\n📋 Step 3: Attempt cross-tenant userinfo access");
    console.log("-".repeat(80));

    const tenantBUserInfoEndpoint = adminServerConfig.userinfoEndpoint;
    console.log(`   Request: GET ${tenantBUserInfoEndpoint}`);
    console.log(`   Using Token from: ${serverConfig.tenantId}`);

    const crossTenantResponse = await get({
      url: tenantBUserInfoEndpoint,
      headers: {
        Authorization: `Bearer ${tenantAAccessToken}`,
      },
    });

    console.log(`   Response Status: ${crossTenantResponse.status}`);

    // Must be denied with 401 Unauthorized (token from different tenant)
    expect(crossTenantResponse.status).toBe(401);
    console.log("✅ Cross-tenant userinfo access properly blocked");

    console.log("\n" + "=".repeat(80));
    console.log("✅ User information isolation verified");
    console.log("=".repeat(80) + "\n");
  });

  it("should not introspect tokens from other tenants", async () => {
    console.log("\n" + "=".repeat(80));
    console.log("MULTI-TENANT ISOLATION TEST: Token Introspection");
    console.log("=".repeat(80) + "\n");

    // =====================================================================
    // Step 1: Authenticate in Tenant A and get access token
    // =====================================================================
    console.log("📋 Step 1: Obtain access token from Tenant A");
    console.log("-".repeat(80));

    const authResult = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      scope: clientSecretPostClient.scope,
      responseType: "code",
      clientId: clientSecretPostClient.clientId,
      redirectUri: clientSecretPostClient.redirectUri,
      state: "test-state-" + Date.now(),
      nonce: "test-nonce-" + Date.now(),
    });

    expect(authResult.status).toBe(200);

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authResult.authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    const tenantAAccessToken = tokenResponse.data.access_token;
    console.log("✅ Obtained Tenant A access token");

    // =====================================================================
    // Step 2: Introspect token in same tenant (should succeed)
    // =====================================================================
    console.log(
      "\n📋 Step 2: Introspect token in same tenant (should succeed)"
    );
    console.log("-".repeat(80));

    const sameTenantIntrospection = await inspectToken({
      endpoint: serverConfig.tokenIntrospectionEndpoint,
      token: tenantAAccessToken,
      tokenHintType: "access_token",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    expect(sameTenantIntrospection.status).toBe(200);
    expect(sameTenantIntrospection.data.active).toBe(true);
    console.log("✅ Same-tenant introspection succeeded");
    console.log(`   Token is active: ${sameTenantIntrospection.data.active}`);

    // =====================================================================
    // Step 3: Attempt to introspect Tenant A token from Tenant B endpoint
    // =====================================================================
    console.log("\n📋 Step 3: Attempt cross-tenant token introspection");
    console.log("-".repeat(80));
    console.log("   Introspecting Tenant A token via Tenant B endpoint");

    const crossTenantIntrospection = await inspectToken({
      endpoint: adminServerConfig.tokenIntrospectionEndpoint,
      token: tenantAAccessToken,
      tokenHintType: "access_token",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    console.log(`   Response Status: ${crossTenantIntrospection.status}`);

    // Must be denied with 400 Bad Request (client not found in target tenant)
    expect(crossTenantIntrospection.status).toBe(400);
    console.log("✅ Cross-tenant introspection blocked with error status");

    console.log("\n" + "=".repeat(80));
    console.log("✅ Token introspection isolation verified");
    console.log("=".repeat(80) + "\n");
  });

  it("should not access other tenant's resource owner endpoint", async () => {
    console.log("\n" + "=".repeat(80));
    console.log("MULTI-TENANT ISOLATION TEST: Resource Owner Endpoint Access");
    console.log("=".repeat(80) + "\n");

    // =====================================================================
    // Step 1: Authenticate and get access token
    // =====================================================================
    console.log("📋 Step 1: Obtain access token from Tenant A");
    console.log("-".repeat(80));

    const authResult = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      scope: clientSecretPostClient.scope,
      responseType: "code",
      clientId: clientSecretPostClient.clientId,
      redirectUri: clientSecretPostClient.redirectUri,
      state: "test-state-" + Date.now(),
      nonce: "test-nonce-" + Date.now(),
    });

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authResult.authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    const tenantAAccessToken = tokenResponse.data.access_token;
    console.log("✅ Obtained access token");

    // =====================================================================
    // Step 2: Access own resource owner endpoint (should succeed)
    // =====================================================================
    console.log(
      "\n📋 Step 2: Access own resource owner endpoint (should succeed)"
    );
    console.log("-".repeat(80));

    const ownResourceOwner = await get({
      url:
        serverConfig.resourceOwnerEndpoint +
        "/identity-verification/applications",
      headers: {
        Authorization: `Bearer ${tenantAAccessToken}`,
      },
    });

    expect(ownResourceOwner.status).toBe(200);
    console.log("✅ Successfully accessed own resource owner endpoint");

    // =====================================================================
    // Step 3: Attempt cross-tenant resource owner access
    // =====================================================================
    console.log("\n📋 Step 3: Attempt cross-tenant resource owner access");
    console.log("-".repeat(80));
    console.log(
      `   Request: GET ${adminServerConfig.resourceOwnerEndpoint}` +
        "/identity-verification/applications"
    );

    const crossTenantResourceOwner = await get({
      url: adminServerConfig.resourceOwnerEndpoint,
      headers: {
        Authorization: `Bearer ${tenantAAccessToken}`,
      },
    });

    console.log(`   Response Status: ${crossTenantResourceOwner.status}`);

    // Must be denied with 401 Unauthorized (token from different tenant)
    expect(crossTenantResourceOwner.status).toBe(401);
    console.log("✅ Cross-tenant resource owner access properly blocked");

    console.log("\n" + "=".repeat(80));
    console.log("✅ Resource owner endpoint isolation verified");
    console.log("=".repeat(80) + "\n");
  });
});
