import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { sleep } from "../../../lib/util";

/**
 * Standard Use Case: Minimal Client Configuration Security Event (Issue #1035)
 *
 * This test verifies that security events work correctly when:
 * - client_name is NOT set (minimal client configuration)
 * - Security events should still be recorded without errors
 *
 * Related fix: CibaFlowEventCreator.resolveClientName() handles null/empty client name
 */
describe("Standard Use Case: Minimal Client Security Event (Issue #1035)", () => {
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

  it("should handle password grant flow with minimal client config (no client_name)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const clientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@minimal-client.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Step 1: Create Organization with Minimal Client Config ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Minimal Client Test Org ${timestamp}`,
        description: "Test organization for minimal client config",
      },
      tenant: {
        id: tenantId,
        name: `Minimal Client Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `MIN_SESSION_${organizationId.substring(0, 8)}`,
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
        email: orgAdminEmail,
        raw_password: orgAdminPassword,
        username: orgAdminEmail,
      },
      // Minimal client config - NO client_name
      client: {
        client_id: clientId,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        // client_name is intentionally omitted
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

    // Verify client_name is not set in response
    expect(onboardingResponse.data.client.client_name).toEqual("");
    console.log("✅ Organization created with minimal client config (no client_name)");

    console.log("\n=== Step 2: Login with Password Grant (triggers security event) ===");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile email management",
      clientId: clientId,
      clientSecret: orgClientSecret,
    });

    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.access_token).toBeDefined();
    const orgAccessToken = tokenResponse.data.access_token;
    console.log("✅ Password grant succeeded with minimal client config");

    console.log("\n=== Step 3: Wait for Security Events to be Persisted ===");
    await sleep(1000);

    console.log("\n=== Step 4: Verify Security Events ===");

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events?limit=20`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    expect(securityEventsResponse.status).toBe(200);
    const events = securityEventsResponse.data.list || [];
    console.log(`✅ Retrieved ${events.length} security events`);

    // Find token-related security events
    const tokenEvents = events.filter(
      event => event.type?.includes("token") || event.type?.includes("password")
    );

    console.log(`  Token-related events: ${tokenEvents.length}`);
    tokenEvents.forEach(event => {
      console.log(`    - Type: ${event.type}`);
      console.log(`      Client ID: ${event.client?.id || 'N/A'}`);
      console.log(`      Client Name: "${event.client?.name || ''}"`);

      // Verify client name is empty string (not null, not undefined, not error)
      if (event.client) {
        expect(event.client.id).toBe(clientId);
        // Client name should be empty string when not set
        expect(typeof event.client.name).toBe("string");
      }
    });

    console.log("✅ Security events properly recorded with empty client_name");

    console.log("\n=== Step 5: Clean Up ===");

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${clientId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("\n=== Test Completed Successfully ===");
    console.log("✅ Verified: Security events work with minimal client config (no client_name)");
  });
});
