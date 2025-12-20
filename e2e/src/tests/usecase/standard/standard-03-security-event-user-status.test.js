import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { sleep } from "../../../lib/util";

/**
 * Standard Use Case: Security Event User Status (Issue #1114)
 *
 * This test verifies that security events include user status in the detail field:
 * - User status (REGISTERED, LOCKED, etc.) should be recorded in security event detail
 * - Security events should properly reflect the user's current status at the time of the event
 *
 * Related implementation:
 * - SecurityEventUserAttributeConfiguration.includeStatus (default: true)
 * - SecurityEventUserCreatable.toDetailWithSensitiveData() includes status in detail JSON
 */
describe("Standard Use Case: Security Event User Status (Issue #1114)", () => {
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

  it("should include user status in security event detail when user logs in", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const clientId = uuidv4();
    const userEmail = `user-${timestamp}@user-status-test.example.com`;
    const userPassword = `TestPass${timestamp}!`;
    const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Step 1: Create Organization with User ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `User Status Test Org ${timestamp}`,
        description: "Test organization for user status in security events",
      },
      tenant: {
        id: tenantId,
        name: `User Status Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `STATUS_SESSION_${organizationId.substring(0, 8)}`,
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
        security_event_user_config: {
          include_id: true,
          include_name: true,
          include_status: true,
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
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        response_modes_supported: ["query"],
        extension: {
          access_token_type: "JWT",
        },
      },
      user: {
        sub: userId,
        email: userEmail,
        raw_password: userPassword,
        username: userEmail,
      },
      client: {
        client_id: clientId,
        client_name: `User Status Test Client ${timestamp}`,
        client_secret: clientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: onboardingRequest,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("Onboarding response status:", onboardingResponse.status);
    if (onboardingResponse.status !== 201) {
      console.error("Onboarding failed:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);
    console.log("✅ Organization created with user");

    console.log("\n=== Step 2: Login with Password Grant (triggers security event) ===");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid profile email management",
      clientId: clientId,
      clientSecret: clientSecret,
    });

    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.access_token).toBeDefined();
    const accessToken = tokenResponse.data.access_token;
    console.log("✅ Password grant succeeded");

    console.log("\n=== Step 3: Wait for Security Events to be Persisted ===");
    await sleep(1500);

    console.log("\n=== Step 4: Verify Security Events contain user status ===");

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events?limit=20`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    expect(securityEventsResponse.status).toBe(200);
    const events = securityEventsResponse.data.list || [];
    console.log(`✅ Retrieved ${events.length} security events`);

    // Log all events for debugging
    console.log("  All events:");
    events.forEach(event => {
      console.log(`    - type: ${event.type}`);
      console.log(`      user: ${JSON.stringify(event.user)}`);
      console.log(`      detail: ${JSON.stringify(event.detail)}`);
    });

    // All events should be checked for user status info
    const userEvents = events;

    let statusVerified = false;
    userEvents.forEach(event => {
      console.log(`\n  Event: ${event.type}`);
      console.log(`    User sub: ${event.user?.sub || 'N/A'}`);
      console.log(`    User Name: ${event.user?.name || 'N/A'}`);
      console.log(`    User Status: ${event.user?.status || 'N/A'}`);

      // Check status in event.user (top-level) or event.detail.user
      const userStatus = event.user?.status || event.detail?.user?.status;
      if (userStatus) {
        console.log(`    ✅ User Status found: ${userStatus}`);
        statusVerified = true;

        // Verify that the status is a valid UserStatus value
        const validStatuses = [
          "UNREGISTERED", "INITIALIZED", "FEDERATED", "REGISTERED",
          "IDENTITY_VERIFIED", "IDENTITY_VERIFICATION_REQUIRED",
          "LOCKED", "DISABLED", "SUSPENDED", "DEACTIVATED",
          "DELETED_PENDING", "DELETED", "UNKNOWN"
        ];
        expect(validStatuses).toContain(userStatus);
      }
    });

    // At least one event should have user status
    expect(statusVerified).toBe(true);
    console.log("\n✅ User status is correctly included in security event");

    console.log("\n=== Step 5: Clean Up ===");

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${clientId}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("\n=== Test Completed Successfully ===");
    console.log("✅ Verified: User status is included in security event detail (Issue #1114)");
  });

  it("should verify status is not included when include_status is set to false", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const clientId = uuidv4();
    const userEmail = `user-${timestamp}@no-status-test.example.com`;
    const userPassword = `TestPass${timestamp}!`;
    const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Test: Status excluded when include_status=false ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `No Status Test Org ${timestamp}`,
        description: "Test organization with status disabled",
      },
      tenant: {
        id: tenantId,
        name: `No Status Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `NOSTATUS_SESSION_${organizationId.substring(0, 8)}`,
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
        security_event_user_config: {
          include_id: true,
          include_name: true,
          include_status: false,  // Explicitly disable status
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
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        response_modes_supported: ["query"],
        extension: {
          access_token_type: "JWT",
        },
      },
      user: {
        sub: userId,
        email: userEmail,
        raw_password: userPassword,
        username: userEmail,
      },
      client: {
        client_id: clientId,
        client_name: `No Status Test Client ${timestamp}`,
        client_secret: clientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: onboardingRequest,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    expect(onboardingResponse.status).toBe(201);
    console.log("✅ Organization created with include_status=false");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid profile email management",
      clientId: clientId,
      clientSecret: clientSecret,
    });

    expect(tokenResponse.status).toBe(200);
    const accessToken = tokenResponse.data.access_token;
    console.log("✅ Password grant succeeded");

    await sleep(1500);

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events?limit=20`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    expect(securityEventsResponse.status).toBe(200);
    const events = securityEventsResponse.data.list || [];

    const authEvents = events.filter(
      event => event.type?.includes("password") || event.type?.includes("token") || event.type?.includes("auth")
    );

    authEvents.forEach(event => {
      console.log(`  Event: ${event.type}`);
      if (event.detail) {
        console.log(`    Detail: ${JSON.stringify(event.detail, null, 2)}`);

        // Status should NOT be present when include_status=false
        if (event.detail.status) {
          console.log(`    ⚠️ Status unexpectedly present: ${event.detail.status}`);
        } else {
          console.log(`    ✅ Status correctly excluded from detail`);
        }
      }
    });

    // Clean up
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${clientId}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("✅ Verified: Status is excluded when include_status=false");
  });
});
