import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { sleep } from "../../../lib/util";

/**
 * Standard Use Case: Grant Revocation with Token Deletion
 *
 * This test demonstrates the grant revocation workflow:
 * 1. Create organization via Onboarding API
 * 2. Create tokens via password grant (creates a grant)
 * 3. Verify grant exists via Management API
 * 4. Revoke grant via Management API
 * 5. Verify all associated tokens are also deleted
 *
 * Key behavior verified:
 * - Password Grant creates AuthorizationGranted records
 * - Grant revocation deletes both the grant and ALL tokens for that user+client
 * - Dry-run mode simulates revocation without actual deletion
 */
describe("Standard Use Case: Grant Revocation with Token Deletion", () => {
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

  it("should revoke grant and delete all associated tokens", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const adminUserId = uuidv4();
    const clientId = uuidv4();
    const adminEmail = `admin-${timestamp}@grant-test.example.com`;
    const adminPassword = `AdminPass${timestamp}!`;
    const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Step 1: Create Organization via Onboarding API ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Grant Revocation Test Org ${timestamp}`,
        description: `E2E test organization for grant revocation created at ${new Date().toISOString()}`,
      },
      tenant: {
        id: tenantId,
        name: `Grant Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `GRANT_SESSION_${organizationId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: {
          allow_origins: [backendUrl],
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
        scopes_supported: ["openid", "profile", "email", "org-management", "management"],
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
        name: "Grant Test Admin",
        email: adminEmail,
        email_verified: true,
        raw_password: adminPassword,
      },
      client: {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email org-management management",
        client_name: `Grant Test Client ${timestamp}`,
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

    console.log("\n=== Step 2: Get Management Token ===");

    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "org-management management",
      clientId: clientId,
      clientSecret: clientSecret,
    });

    expect(mgmtTokenResponse.status).toBe(200);
    const accessToken = mgmtTokenResponse.data.access_token;
    console.log(`✅ Management token obtained`);

    console.log("\n=== Step 3: Create Additional Grant via Password Grant ===");

    // Create a token with different scopes (grant will be merged)
    const userTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "openid profile email",
      clientId: clientId,
      clientSecret: clientSecret,
    });

    expect(userTokenResponse.status).toBe(200);
    expect(userTokenResponse.data.access_token).toBeDefined();
    expect(userTokenResponse.data.refresh_token).toBeDefined();
    const refreshToken = userTokenResponse.data.refresh_token;
    console.log(`✅ Access token and refresh token obtained`);

    await sleep(500);

    console.log("\n=== Step 4: Verify Grant Exists ===");

    const grantsListResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/grants?client_id=${clientId}`,
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    console.log(`Grants list response:`, JSON.stringify(grantsListResponse.data, null, 2));
    expect(grantsListResponse.status).toBe(200);
    expect(grantsListResponse.data.list).toBeDefined();
    expect(grantsListResponse.data.list.length).toBeGreaterThan(0);

    const grantId = grantsListResponse.data.list[0].id;
    console.log(`✅ Grant found: ${grantId}`);

    // Verify grant contains merged scopes
    const grantScopes = grantsListResponse.data.list[0].scopes;
    expect(grantScopes).toContain("openid");
    expect(grantScopes).toContain("org-management");
    console.log(`✅ Grant scopes verified: ${grantScopes.join(", ")}`);

    console.log("\n=== Step 5: Verify Refresh Token Works Before Revocation ===");

    const refreshBeforeRevoke = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken: refreshToken,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    expect(refreshBeforeRevoke.status).toBe(200);
    expect(refreshBeforeRevoke.data.access_token).toBeDefined();
    const newRefreshToken = refreshBeforeRevoke.data.refresh_token || refreshToken;
    console.log(`✅ Refresh token works before revocation`);

    console.log("\n=== Step 6: Revoke Grant via Management API ===");

    const revokeResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/grants/${grantId}`,
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    console.log(`Revoke response status: ${revokeResponse.status}`);
    console.log(`Revoke response data: ${JSON.stringify(revokeResponse.data)}`);
    expect(revokeResponse.status).toBe(204);
    // 204 No Content should have no body (empty string, undefined, or null)
    expect(revokeResponse.data === "" || revokeResponse.data === undefined || revokeResponse.data === null).toBe(true);
    console.log(`✅ Grant revoked successfully (204 No Content)`);

    await sleep(500);

    console.log("\n=== Step 7: Verify Refresh Token No Longer Works ===");

    const refreshAfterRevoke = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken: newRefreshToken,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    console.log(`Refresh after revoke status: ${refreshAfterRevoke.status}`);
    console.log(`Refresh after revoke data:`, JSON.stringify(refreshAfterRevoke.data, null, 2));
    expect(refreshAfterRevoke.status).toBe(400);
    expect(refreshAfterRevoke.data.error).toBe("invalid_grant");
    console.log(`✅ Refresh token invalidated (invalid_grant error)`);

    console.log("\n=== Step 8: Verify Management Token Also Invalidated ===");

    // The management token should also be invalidated because all tokens for user+client were deleted
    const grantAfterRevoke = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/grants/${grantId}`,
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    console.log(`Grant access after revoke status: ${grantAfterRevoke.status}`);
    // Expect 401 because the management token was also deleted with the grant
    expect(grantAfterRevoke.status).toBe(401);
    console.log(`✅ Management token also invalidated (401 Unauthorized)`);

    console.log("\n=== Cleanup ===");

    // Need fresh token for cleanup since our token was invalidated
    const cleanupTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "org-management management",
      clientId: clientId,
      clientSecret: clientSecret,
    });
    const cleanupToken = cleanupTokenResponse.data.access_token;

    // Delete tenant
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${cleanupToken}` },
    }).catch(() => {});
    console.log("🧹 Tenant deleted");

    // Delete organization
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});
    console.log("🧹 Organization deleted");

    console.log("\n=== Test Completed Successfully ===");
    console.log("✅ Verified: Password Grant creates AuthorizationGranted records");
    console.log("✅ Verified: Grant revocation deletes both grant and ALL associated tokens");
    console.log("✅ Verified: Scopes are merged across multiple token requests");
  });

  it("should verify dry-run does not delete grant or tokens", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const adminUserId = uuidv4();
    const clientId = uuidv4();
    const adminEmail = `admin-dryrun-${timestamp}@grant-test.example.com`;
    const adminPassword = `AdminPass${timestamp}!`;
    const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Dry-Run Test: Create Organization ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Dry-Run Grant Test Org ${timestamp}`,
        description: "E2E test organization for grant dry-run test",
      },
      tenant: {
        id: tenantId,
        name: `Dry-Run Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `DRYRUN_SESSION_${organizationId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: {
          allow_origins: [backendUrl],
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
        grant_types_supported: ["password", "refresh_token"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "org-management", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        extension: {
          access_token_type: "JWT",
        },
      },
      user: {
        sub: adminUserId,
        provider_id: "idp-server",
        name: "Dry-Run Test Admin",
        email: adminEmail,
        email_verified: true,
        raw_password: adminPassword,
      },
      client: {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["password", "refresh_token"],
        scope: "openid profile email org-management management",
        client_name: `Dry-Run Test Client`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingRequest,
    });

    if (createResponse.status !== 201) {
      console.error("Onboarding failed:", JSON.stringify(createResponse.data, null, 2));
    }
    expect(createResponse.status).toBe(201);
    console.log(`✅ Organization created: ${organizationId}`);

    console.log("\n=== Dry-Run Test: Get Token (Creates Grant) ===");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "org-management management openid profile email",
      clientId: clientId,
      clientSecret: clientSecret,
    });

    expect(tokenResponse.status).toBe(200);
    const accessToken = tokenResponse.data.access_token;
    const refreshToken = tokenResponse.data.refresh_token;
    console.log(`✅ Token obtained (grant created)`);

    await sleep(500);

    console.log("\n=== Dry-Run Test: Get Grant ID ===");

    const grantsListResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/grants?client_id=${clientId}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    expect(grantsListResponse.status).toBe(200);
    expect(grantsListResponse.data.list.length).toBeGreaterThan(0);
    const grantId = grantsListResponse.data.list[0].id;
    console.log(`✅ Grant found: ${grantId}`);

    console.log("\n=== Dry-Run Test: Execute Dry-Run Revocation ===");

    const dryRunResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/grants/${grantId}?dry_run=true`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    console.log(`Dry-run response:`, JSON.stringify(dryRunResponse.data, null, 2));
    expect(dryRunResponse.status).toBe(200);
    expect(dryRunResponse.data.dry_run).toBe(true);
    expect(dryRunResponse.data.grant_id).toBe(grantId);
    console.log(`✅ Dry-run completed successfully`);

    console.log("\n=== Dry-Run Test: Verify Grant Still Exists ===");

    const grantAfterDryRun = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/grants/${grantId}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    expect(grantAfterDryRun.status).toBe(200);
    expect(grantAfterDryRun.data.id).toBe(grantId);
    console.log(`✅ Grant still exists after dry-run`);

    console.log("\n=== Dry-Run Test: Verify Refresh Token Still Works ===");

    const refreshAfterDryRun = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken: refreshToken,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    expect(refreshAfterDryRun.status).toBe(200);
    expect(refreshAfterDryRun.data.access_token).toBeDefined();
    console.log(`✅ Refresh token still works after dry-run`);

    console.log("\n=== Cleanup ===");

    // Delete grant (actual deletion, not dry-run)
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/grants/${grantId}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    }).catch(() => {});

    // Get new token for cleanup
    const cleanupTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "org-management management",
      clientId: clientId,
      clientSecret: clientSecret,
    });
    const cleanupToken = cleanupTokenResponse.data.access_token;

    // Delete tenant
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${cleanupToken}` },
    }).catch(() => {});
    console.log("🧹 Tenant deleted");

    // Delete organization
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});
    console.log("🧹 Organization deleted");

    console.log("\n=== Dry-Run Test Completed Successfully ===");
    console.log("✅ Verified: Dry-run simulates revocation without actual deletion");
    console.log("✅ Verified: Grant and tokens remain after dry-run");
  });
});
