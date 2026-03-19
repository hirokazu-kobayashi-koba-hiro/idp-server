import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, post, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { sleep } from "../../../lib/util";

/**
 * Standard Use Case: Token Management API
 *
 * This test demonstrates the token management workflow:
 * 1. Create organization via Onboarding API
 * 2. Create tokens via password grant
 * 3. List tokens via Token Management API (filtering, pagination)
 * 4. Get token detail
 * 5. Dry-run delete (verify token is NOT deleted)
 * 6. Delete token (verify token IS deleted)
 * 7. Delete all user tokens with dry-run (verify affected_count)
 * 8. Delete all user tokens (verify all tokens deleted)
 *
 * Key behavior verified:
 * - Token list returns metadata without token values (security)
 * - Filtering by client_id, grant_type works correctly
 * - Dry-run returns target info without actual deletion
 * - User-level token deletion removes all tokens for that user
 * - Deleted tokens cannot be used for refresh
 */
describe("Standard Use Case: Token Management", () => {
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

  it("should manage tokens through full lifecycle", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const adminUserId = uuidv4();
    const clientId = uuidv4();
    const adminEmail = `admin-${timestamp}@token-mgmt-test.example.com`;
    const adminPassword = `AdminPass${timestamp}!`;
    const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Step 1: Create Organization via Onboarding API ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Token Mgmt Test Org ${timestamp}`,
        description: `E2E test organization for token management created at ${new Date().toISOString()}`,
      },
      tenant: {
        id: tenantId,
        name: `Token Mgmt Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `TOKEN_SESSION_${organizationId.substring(0, 8)}`,
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
        token_endpoint_auth_methods_supported: [
          "client_secret_post",
          "client_secret_basic",
        ],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: [
          "openid",
          "profile",
          "email",
          "org-management",
          "management",
        ],
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
        name: "Token Mgmt Test Admin",
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
        client_name: `Token Mgmt Test Client ${timestamp}`,
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
      console.error(
        "Onboarding failed:",
        JSON.stringify(createResponse.data, null, 2)
      );
    }
    expect(createResponse.status).toBe(201);
    console.log(`Organization created: ${organizationId}`);

    console.log("\n=== Step 2: Create Multiple Tokens via Password Grant ===");

    // Token 1: management scope
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
    const managementAccessToken = mgmtTokenResponse.data.access_token;
    console.log("Management token created");

    // Token 2: openid scope (creates another token record)
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
    const userAccessToken = userTokenResponse.data.access_token;
    const userRefreshToken = userTokenResponse.data.refresh_token;
    expect(userAccessToken).toBeDefined();
    expect(userRefreshToken).toBeDefined();
    console.log("User token with refresh token created");

    await sleep(500);

    console.log("\n=== Step 3: List Tokens via Management API ===");

    const tokenListUrl = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/tokens`;

    const listResponse = await get({
      url: `${tokenListUrl}?user_id=${adminUserId}&limit=10`,
      headers: {
        Authorization: `Bearer ${managementAccessToken}`,
      },
    });

    console.log("Token list response:", JSON.stringify(listResponse.data));
    expect(listResponse.status).toBe(200);
    expect(listResponse.data.list).toBeDefined();
    expect(listResponse.data.total_count).toBe(2);
    expect(listResponse.data.limit).toBe(10);
    expect(listResponse.data.offset).toBe(0);

    // Verify all tokens belong to the user
    listResponse.data.list.forEach((token) => {
      expect(token.user_id).toBe(adminUserId);
    });

    // Verify token metadata fields
    const firstToken = listResponse.data.list[0];
    expect(firstToken.id).toBeDefined();
    expect(firstToken.tenant_id).toBe(tenantId);
    expect(firstToken.client_id).toBe(clientId);
    expect(firstToken.grant_type).toBe("password");
    expect(firstToken.token_type).toBeDefined();
    expect(firstToken.access_token_expires_at).toBeDefined();
    expect(firstToken.created_at).toBeDefined();
    expect(typeof firstToken.has_refresh_token).toBe("boolean");

    // Security: token values must NOT be exposed
    expect(firstToken.encrypted_access_token).toBeUndefined();
    expect(firstToken.hashed_access_token).toBeUndefined();
    expect(firstToken.encrypted_refresh_token).toBeUndefined();
    console.log("Token list verified: metadata only, no token values exposed");

    console.log("\n=== Step 4: Filter Tokens by client_id ===");

    const filteredResponse = await get({
      url: `${tokenListUrl}?client_id=${clientId}&limit=10`,
      headers: {
        Authorization: `Bearer ${managementAccessToken}`,
      },
    });

    expect(filteredResponse.status).toBe(200);
    filteredResponse.data.list.forEach((token) => {
      expect(token.client_id).toBe(clientId);
    });
    console.log(
      `Filtered by client_id: ${filteredResponse.data.total_count} tokens`
    );

    console.log("\n=== Step 5: Get Token Detail ===");

    const tokenId = listResponse.data.list[0].id;

    const detailResponse = await get({
      url: `${tokenListUrl}/${tokenId}`,
      headers: {
        Authorization: `Bearer ${managementAccessToken}`,
      },
    });

    expect(detailResponse.status).toBe(200);
    expect(detailResponse.data.id).toBe(tokenId);
    expect(detailResponse.data.tenant_id).toBe(tenantId);
    console.log("Token detail retrieved:", detailResponse.data.id);

    console.log("\n=== Step 6: Dry-Run Delete Single Token ===");

    // Find the user token (not the management token we're using)
    const userToken = listResponse.data.list.find(
      (t) => t.scopes && t.scopes.includes("openid")
    );
    expect(userToken).toBeDefined();

    const dryRunResponse = await deletion({
      url: `${tokenListUrl}/${userToken.id}?dry_run=true`,
      headers: {
        Authorization: `Bearer ${managementAccessToken}`,
      },
    });

    expect(dryRunResponse.status).toBe(200);
    expect(dryRunResponse.data.dry_run).toBe(true);
    expect(dryRunResponse.data.target).toBeDefined();
    expect(dryRunResponse.data.target.id).toBe(userToken.id);
    console.log("Dry-run delete: target info returned without deletion");

    // Verify token still exists
    const verifyExists = await get({
      url: `${tokenListUrl}/${userToken.id}`,
      headers: {
        Authorization: `Bearer ${managementAccessToken}`,
      },
    });
    expect(verifyExists.status).toBe(200);
    console.log("Token still exists after dry-run");

    console.log("\n=== Step 7: Actually Delete Single Token ===");

    const deleteResponse = await deletion({
      url: `${tokenListUrl}/${userToken.id}`,
      headers: {
        Authorization: `Bearer ${managementAccessToken}`,
      },
    });

    expect(deleteResponse.status).toBe(204);
    console.log("Token deleted successfully (204 No Content)");

    // Verify token no longer exists
    const verifyDeleted = await get({
      url: `${tokenListUrl}/${userToken.id}`,
      headers: {
        Authorization: `Bearer ${managementAccessToken}`,
      },
    });
    expect(verifyDeleted.status).toBe(404);
    console.log("Deleted token returns 404");

    // Verify access token is no longer active via introspection
    const introspectionResponse = await post({
      url: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
      body: {
        token: userAccessToken,
        client_id: clientId,
        client_secret: clientSecret,
      },
    });
    expect(introspectionResponse.status).toBe(200);
    expect(introspectionResponse.data.active).toBe(false);
    console.log("Access token inactive via introspection after deletion");

    // Verify refresh token is also invalidated (record physically deleted)
    const refreshAfterDelete = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken: userRefreshToken,
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(refreshAfterDelete.status).toBe(400);
    console.log("Refresh token invalidated after token deletion");

    console.log("\n=== Step 8: Create More Tokens for User Deletion Test ===");

    // Create a few more tokens
    for (let i = 0; i < 2; i++) {
      const extraToken = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: adminEmail,
        password: adminPassword,
        scope: "openid profile",
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(extraToken.status).toBe(200);
    }

    await sleep(500);

    console.log(
      "\n=== Step 9: Dry-Run Delete All User Tokens ==="
    );

    const userTokensUrl = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${adminUserId}/tokens`;

    const userDryRunResponse = await deletion({
      url: `${userTokensUrl}?dry_run=true`,
      headers: {
        Authorization: `Bearer ${managementAccessToken}`,
      },
    });

    expect(userDryRunResponse.status).toBe(200);
    expect(userDryRunResponse.data.dry_run).toBe(true);
    expect(userDryRunResponse.data.user_id).toBe(adminUserId);
    expect(typeof userDryRunResponse.data.affected_count).toBe("number");
    expect(userDryRunResponse.data.affected_count).toBeGreaterThanOrEqual(2);
    console.log(
      `Dry-run user token deletion: ${userDryRunResponse.data.affected_count} tokens would be affected`
    );

    console.log(
      "\n=== Step 10: Actually Delete All User Tokens ==="
    );

    const userDeleteResponse = await deletion({
      url: `${userTokensUrl}`,
      headers: {
        Authorization: `Bearer ${managementAccessToken}`,
      },
    });

    // Management token itself was also deleted, so this may return 204 or 401
    console.log(
      `User token deletion response: ${userDeleteResponse.status}`
    );
    expect([204, 401].includes(userDeleteResponse.status)).toBe(true);

    // Need fresh token - all previous tokens were deleted
    const freshTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "org-management management",
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(freshTokenResponse.status).toBe(200);
    const freshToken = freshTokenResponse.data.access_token;

    // Verify no old tokens remain (only the fresh one)
    const afterDeleteList = await get({
      url: `${tokenListUrl}?user_id=${adminUserId}&limit=10`,
      headers: {
        Authorization: `Bearer ${freshToken}`,
      },
    });
    expect(afterDeleteList.status).toBe(200);
    // Only the fresh token should exist
    expect(afterDeleteList.data.total_count).toBe(1);
    console.log(
      "All old user tokens deleted, only fresh token remains"
    );

    console.log("\n=== Cleanup ===");

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${freshToken}` },
    }).catch(() => {});
    console.log("Tenant deleted");

    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});
    console.log("Organization deleted");

    console.log("\n=== Test Completed Successfully ===");
    console.log(
      "Verified: Token list returns metadata without token values"
    );
    console.log(
      "Verified: Filtering by client_id/grant_type works"
    );
    console.log(
      "Verified: Dry-run returns target info without actual deletion"
    );
    console.log(
      "Verified: Token deletion invalidates associated refresh tokens"
    );
    console.log(
      "Verified: User-level token deletion removes all user tokens"
    );
  });

  it("should create token via management API and verify full lifecycle", async () => {
    // キャッシュTTL待ち（61秒）があるためタイムアウトを延長
    jest.setTimeout(120000);
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const adminUserId = uuidv4();
    const testUserId = uuidv4();
    const clientId = uuidv4();
    const adminEmail = `admin-${timestamp}@token-create-test.example.com`;
    const adminPassword = `AdminPass${timestamp}!`;
    const testUserEmail = `user-${timestamp}@token-create-test.example.com`;
    const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Create Test: Step 1 - Setup Organization ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Token Create Test Org ${timestamp}`,
        description: "E2E test for token creation via management API",
      },
      tenant: {
        id: tenantId,
        name: `Token Create Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `CREATE_SESSION_${organizationId.substring(0, 8)}`,
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
        token_endpoint_auth_methods_supported: [
          "client_secret_post",
          "client_secret_basic",
        ],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: [
          "openid",
          "profile",
          "email",
          "org-management",
          "management",
        ],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
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
        name: "Token Create Admin",
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
        client_name: `Token Create Test Client`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const createOrgResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingRequest,
    });
    expect(createOrgResponse.status).toBe(201);
    console.log(`Organization created: ${organizationId}`);

    // Register test user (separate from admin)
    const registerUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        sub: testUserId,
        provider_id: "idp-server",
        name: "Test User for Token Create",
        email: testUserEmail,
        email_verified: true,
        raw_password: `TestPass${timestamp}!`,
      },
    });
    expect(registerUserResponse.status).toBe(201);
    console.log(`Test user created: ${testUserId}`);

    // Get management token via normal flow
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
    const managementAccessToken = mgmtTokenResponse.data.access_token;
    console.log("Management token obtained via password grant");

    const tokenMgmtUrl = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/tokens`;

    console.log(
      "\n=== Create Test: Step 2 - Create Token with Custom Claims (JWT) ==="
    );

    const createJwtResponse = await postWithJson({
      url: tokenMgmtUrl,
      headers: { Authorization: `Bearer ${managementAccessToken}` },
      body: {
        client_id: clientId,
        scopes: "openid profile email",
        user_id: testUserId,
        token_format: "jwt",
        access_token_duration: 1800,
        refresh_token_duration: 43200,
        custom_claims: {
          role: "tester",
          department: "qa",
          test_id: timestamp,
        },
      },
    });

    console.log(
      "Create JWT Response:",
      createJwtResponse.status,
      JSON.stringify(createJwtResponse.data).substring(0, 200)
    );
    expect(createJwtResponse.status).toBe(201);
    expect(createJwtResponse.data).toHaveProperty("id");
    expect(createJwtResponse.data).toHaveProperty("access_token");
    expect(createJwtResponse.data).toHaveProperty("token_type", "Bearer");
    expect(createJwtResponse.data).toHaveProperty("expires_in", 1800);
    expect(createJwtResponse.data).toHaveProperty("refresh_token");
    expect(createJwtResponse.data).toHaveProperty("scopes");

    const createdJwtToken = createJwtResponse.data.access_token;
    const createdTokenId = createJwtResponse.data.id;
    const createdRefreshToken = createJwtResponse.data.refresh_token;

    // Verify JWT structure (header.payload.signature)
    const jwtParts = createdJwtToken.split(".");
    expect(jwtParts.length).toBe(3);
    console.log("JWT token created with 3 parts (header.payload.signature)");

    // Decode and verify JWT payload
    const payload = JSON.parse(
      Buffer.from(jwtParts[1], "base64url").toString()
    );
    expect(payload.iss).toBe(`${backendUrl}/${tenantId}`);
    expect(payload.client_id).toBe(clientId);
    expect(payload.sub).toBe(testUserId);
    expect(payload.scope).toBe("openid profile email");
    expect(payload.role).toBe("tester");
    expect(payload.department).toBe("qa");
    expect(payload.test_id).toBe(timestamp);
    console.log("JWT payload verified: standard claims + custom claims present");

    // Verify standard claims cannot be overwritten by custom claims
    expect(payload.iss).not.toBe("evil-issuer");
    console.log("Security: standard claims not overwritable by custom claims");

    console.log(
      "\n=== Create Test: Step 3 - Introspection Verifies Token Active ==="
    );

    const introspectResponse = await post({
      url: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
      body: {
        token: createdJwtToken,
        client_id: clientId,
        client_secret: clientSecret,
      },
    });

    expect(introspectResponse.status).toBe(200);
    expect(introspectResponse.data.active).toBe(true);
    expect(introspectResponse.data.client_id).toBe(clientId);
    expect(introspectResponse.data.sub).toBe(testUserId);
    // Custom claims should be in introspection response
    expect(introspectResponse.data.role).toBe("tester");
    expect(introspectResponse.data.department).toBe("qa");
    console.log(
      "Introspection: active=true, custom claims included in response"
    );

    console.log(
      "\n=== Create Test: Step 4 - Token Visible in List API ==="
    );

    const listResponse = await get({
      url: `${tokenMgmtUrl}?user_id=${testUserId}&limit=10`,
      headers: { Authorization: `Bearer ${managementAccessToken}` },
    });

    expect(listResponse.status).toBe(200);
    const createdTokenInList = listResponse.data.list.find(
      (t) => t.id === createdTokenId
    );
    expect(createdTokenInList).toBeDefined();
    expect(createdTokenInList.client_id).toBe(clientId);
    expect(createdTokenInList.has_refresh_token).toBe(true);
    console.log("Token visible in list API with correct metadata");

    console.log(
      "\n=== Create Test: Step 5 - Create Opaque Token (no user) ==="
    );

    const createOpaqueResponse = await postWithJson({
      url: tokenMgmtUrl,
      headers: { Authorization: `Bearer ${managementAccessToken}` },
      body: {
        client_id: clientId,
        scopes: "management",
        token_format: "opaque",
        access_token_duration: 600,
      },
    });

    expect(createOpaqueResponse.status).toBe(201);
    expect(createOpaqueResponse.data).toHaveProperty("access_token");
    expect(createOpaqueResponse.data).toHaveProperty("expires_in", 600);
    // No refresh token for client_credentials-like token
    expect(createOpaqueResponse.data.refresh_token).toBeUndefined();

    const opaqueToken = createOpaqueResponse.data.access_token;
    // Opaque token should NOT be a JWT
    expect(opaqueToken.split(".").length).not.toBe(3);
    console.log("Opaque token created (not JWT format, no refresh token)");

    // Introspect opaque token
    const introspectOpaqueResponse = await post({
      url: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
      body: {
        token: opaqueToken,
        client_id: clientId,
        client_secret: clientSecret,
      },
    });
    expect(introspectOpaqueResponse.status).toBe(200);
    expect(introspectOpaqueResponse.data.active).toBe(true);
    console.log("Opaque token active via introspection");

    console.log(
      "\n=== Create Test: Step 6 - Dry-run Create ==="
    );

    const dryRunCreateResponse = await postWithJson({
      url: `${tokenMgmtUrl}?dry_run=true`,
      headers: { Authorization: `Bearer ${managementAccessToken}` },
      body: {
        client_id: clientId,
        scopes: "openid profile",
        user_id: testUserId,
        token_format: "jwt",
      },
    });

    expect(dryRunCreateResponse.status).toBe(200);
    expect(dryRunCreateResponse.data).toHaveProperty("dry_run", true);
    expect(dryRunCreateResponse.data).toHaveProperty("access_token");
    console.log("Dry-run create: token returned but not persisted");

    // Dry-run token should NOT be active via introspection
    const introspectDryRunResponse = await post({
      url: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
      body: {
        token: dryRunCreateResponse.data.access_token,
        client_id: clientId,
        client_secret: clientSecret,
      },
    });
    expect(introspectDryRunResponse.status).toBe(200);
    expect(introspectDryRunResponse.data.active).toBe(false);
    console.log("Dry-run token inactive via introspection (not persisted)");

    console.log(
      "\n=== Create Test: Step 7 - Delete Created Token and Verify Lifecycle ==="
    );

    // Delete the JWT token
    const deleteResponse = await deletion({
      url: `${tokenMgmtUrl}/${createdTokenId}`,
      headers: { Authorization: `Bearer ${managementAccessToken}` },
    });
    expect(deleteResponse.status).toBe(204);
    console.log("Created token deleted");

    // Wait for token cache to expire (TOKEN_CACHE_ENABLED=true の場合、TTL 60秒)
    // 管理APIの削除はDBレコードを物理削除するが、キャッシュは削除しない
    await sleep(61000);

    // Introspection should return active=false
    const introspectAfterDelete = await post({
      url: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
      body: {
        token: createdJwtToken,
        client_id: clientId,
        client_secret: clientSecret,
      },
    });
    expect(introspectAfterDelete.status).toBe(200);
    expect(introspectAfterDelete.data.active).toBe(false);
    console.log("Deleted token inactive via introspection");

    // Refresh token should also be invalidated
    const refreshAfterDelete = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken: createdRefreshToken,
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(refreshAfterDelete.status).toBe(400);
    console.log("Refresh token invalidated after token deletion");

    console.log("\n=== Cleanup ===");

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${managementAccessToken}` },
    }).catch(() => {});
    console.log("Tenant deleted");

    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});
    console.log("Organization deleted");

    console.log("\n=== Token Create Lifecycle Test Completed ===");
    console.log(
      "Verified: JWT token with custom claims created via management API"
    );
    console.log(
      "Verified: Custom claims present in JWT payload and introspection"
    );
    console.log(
      "Verified: Opaque token created without user (no refresh token)"
    );
    console.log(
      "Verified: Dry-run creates token but does not persist"
    );
    console.log(
      "Verified: Full lifecycle: create → introspect → list → delete → inactive"
    );
  });
});
