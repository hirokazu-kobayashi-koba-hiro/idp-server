import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { sleep } from "../../../lib/util";

/**
 * Standard Use Case: Minimal Client Configuration with CIBA (Issue #1035)
 *
 * This test verifies that CIBA security events work correctly when:
 * - client_name is NOT set (minimal client configuration)
 * - Security events should still be recorded without errors
 *
 * Related fix: CibaFlowEventCreator.resolveClientName() handles null/empty client name
 */
describe("Standard Use Case: Minimal Client CIBA Security Event (Issue #1035)", () => {
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

  it("should handle CIBA flow with minimal client config (no client_name)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const cibaClientId = uuidv4();
    const deviceId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@minimal-client.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const cibaClientSecret = `ciba-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Step 1: Create Organization with Minimal Client Config ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Minimal Client Test Org ${timestamp}`,
        description: "Test organization for minimal client CIBA flow",
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
        grant_types_supported: ["authorization_code", "refresh_token", "password", "urn:openid:params:grant-type:ciba"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        backchannel_token_delivery_modes_supported: ["poll", "ping"],
        backchannel_authentication_request_signing_alg_values_supported: ["RS256", "ES256"],
        backchannel_user_code_parameter_supported: true,
        response_modes_supported: ["query"],
        extension: {
          access_token_type: "JWT",
          backchannel_authentication_polling_interval: 5,
          backchannel_authentication_request_expires_in: 300,
          required_backchannel_auth_user_code: true,
          backchannel_auth_user_code_type: "numeric",
        },
      },
      user: {
        sub: userId,
        email: orgAdminEmail,
        raw_password: orgAdminPassword,
        username: orgAdminEmail,
        authentication_devices: [
          {
            id: deviceId,
            app_name: "Test Device",
            priority: 1,
          }
        ]
      },
      // Minimal client config - NO client_name
      client: {
        client_id: orgClientId,
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
    console.log("✅ Organization created with minimal client config (no client_name)");

    console.log("\n=== Step 2: Get Admin Token ===");

    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile email management",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    expect(adminTokenResponse.status).toBe(200);
    const orgAdminAccessToken = adminTokenResponse.data.access_token;
    console.log("✅ Admin token obtained");

    console.log("\n=== Step 3: Create CIBA Client WITHOUT client_name ===");

    // Create CIBA client with minimal config (no client_name)
    const cibaClientRequest = {
      client_id: cibaClientId,
      client_secret: cibaClientSecret,
      // client_name is intentionally omitted
      redirect_uris: [`${backendUrl}/callback`],
      token_endpoint_auth_method: "client_secret_post",
      grant_types: ["urn:openid:params:grant-type:ciba"],
      scope: "openid profile email",
      backchannel_token_delivery_mode: "poll",
      backchannel_user_code_parameter: true,
      extension: {
        default_ciba_authentication_interaction_type: "authentication-device-notification-no-action",
      },
    };

    const clientCreationResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      body: cibaClientRequest,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    expect(clientCreationResponse.status).toBe(201);
    console.log("✅ CIBA Client created WITHOUT client_name");

    // Verify client_name is not set
    const createdClient = clientCreationResponse.data.result;
    console.log("  Client config:", JSON.stringify(createdClient, null, 2));
    expect(createdClient.client_name).toEqual("");

    console.log("\n=== Step 4: Send CIBA Request ===");

    // This is the critical test - CIBA request with a client that has no client_name
    // Before fix: This could cause issues in security event creation
    // After fix: Should work correctly with empty client_name
    const cibaRequest = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      clientId: cibaClientId,
      scope: "openid profile email",
      bindingMessage: "Minimal client test",
      userCode: "123456",
      loginHint: `sub:${userId},idp:idp-server`,
      clientSecret: cibaClientSecret,
    });

    console.log("CIBA request response:", JSON.stringify(cibaRequest.data, null, 2));
    expect(cibaRequest.status).toBe(200);
    expect(cibaRequest.data).toHaveProperty("auth_req_id");

    const authReqId = cibaRequest.data.auth_req_id;
    console.log(`✅ CIBA request accepted, auth_req_id: ${authReqId}`);

    console.log("\n=== Step 5: Complete Authentication ===");

    const deviceTransactions = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentication-devices/{id}/authentications`,
      deviceId: deviceId,
      params: {
        "attributes.auth_req_id": authReqId
      },
    });

    expect(deviceTransactions.status).toBe(200);
    expect(deviceTransactions.data.list.length).toBeGreaterThan(0);

    const authenticationTransaction = deviceTransactions.data.list[0];
    console.log(`  Transaction ID: ${authenticationTransaction.id}`);

    // SECURITY: Verify context is NOT included when device authentication is not performed
    // (authentication_type: "none" is the default, so context should be excluded)
    expect(authenticationTransaction).not.toHaveProperty("context");
    console.log("✅ Context correctly excluded (device authentication not required)");

    const completeResponse = await postAuthenticationDeviceInteraction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentications/{id}/`,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "password-authentication",
      body: {
        username: orgAdminEmail,
        password: orgAdminPassword,
      }
    });
    expect(completeResponse.status).toBe(200);
    console.log("✅ Authentication completed");

    console.log("\n=== Step 6: Request Token ===");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: authReqId,
      clientId: cibaClientId,
      clientSecret: cibaClientSecret,
    });

    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("access_token");
    console.log("✅ Token obtained successfully");

    console.log("\n=== Step 7: Verify Security Events ===");

    await sleep(1000);

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events?limit=20`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    expect(securityEventsResponse.status).toBe(200);
    console.log(`✅ Retrieved ${securityEventsResponse.data.list?.length || 0} security events`);

    // Find CIBA-related security events
    const cibaEvents = securityEventsResponse.data.list?.filter(
      event => event.type?.includes("backchannel")
    ) || [];

    console.log(`  CIBA-related events: ${cibaEvents.length}`);
    cibaEvents.forEach(event => {
      console.log(`    - Type: ${event.type}`);
      console.log(`      Client ID: ${event.client?.id || 'N/A'}`);
      console.log(`      Client Name: ${event.client?.name || '(empty)'}`);

      // Verify client name is empty string (not null, not undefined, not error)
      if (event.client) {
        expect(event.client.id).toBe(cibaClientId);
        // Client name should be empty string when not set
        expect(event.client.name).toBe("");
      }
    });

    // Verify at least one CIBA event was recorded
    expect(cibaEvents.length).toBeGreaterThan(0);
    console.log("✅ Security events properly recorded with empty client_name");

    console.log("\n=== Step 8: Clean Up ===");

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${orgClientId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${cibaClientId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("\n=== Test Completed Successfully ===");
    console.log("✅ Verified: CIBA security events work with minimal client config (no client_name)");
    console.log("   Issue #1035 fix confirmed: resolveClientName() handles null/empty correctly");
  });
});
