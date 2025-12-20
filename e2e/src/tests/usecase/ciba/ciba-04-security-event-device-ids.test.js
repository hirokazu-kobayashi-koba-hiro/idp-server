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
 * CIBA Use Case: Security Event Authentication Device IDs (Issue #1124)
 *
 * This test verifies that authentication device IDs are included in security events:
 * - include_authentication_device_ids=true enables device ID logging
 * - CIBA flow security events contain the user's authentication device IDs
 * - Device IDs match the devices registered during onboarding
 */
describe("CIBA Use Case: Security Event Authentication Device IDs (Issue #1124)", () => {
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

  it("should include authentication device IDs in CIBA security events when enabled", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const cibaClientId = uuidv4();
    const deviceId = uuidv4();
    const userEmail = `user-${timestamp}@device-ids-ciba.example.com`;
    const userPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const cibaClientSecret = `ciba-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Step 1: Create Organization with CIBA and Device IDs Config ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Device IDs CIBA Test Org ${timestamp}`,
        description: "Test organization for device IDs in CIBA security events",
      },
      tenant: {
        id: tenantId,
        name: `Device IDs CIBA Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `DEVIDS_CIBA_${organizationId.substring(0, 8)}`,
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
          include_authentication_device_ids: true,  // Enable device IDs
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
        email: userEmail,
        raw_password: userPassword,
        username: userEmail,
        authentication_devices: [
          {
            id: deviceId,
            app_name: "Test Device",
            priority: 1,
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_name: `Device IDs Admin Client ${timestamp}`,
        client_secret: orgClientSecret,
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
    console.log("✅ Organization created with include_authentication_device_ids=true");
    console.log(`   Device ID: ${deviceId}`);

    console.log("\n=== Step 2: Get Admin Token ===");

    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid profile email management",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    expect(adminTokenResponse.status).toBe(200);
    const orgAdminAccessToken = adminTokenResponse.data.access_token;
    console.log("✅ Admin token obtained");

    console.log("\n=== Step 3: Create CIBA Client ===");

    const cibaClientRequest = {
      client_id: cibaClientId,
      client_secret: cibaClientSecret,
      client_name: `Device IDs CIBA Client ${timestamp}`,
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
    console.log("✅ CIBA Client created");

    console.log("\n=== Step 4: Send CIBA Request ===");

    const cibaRequest = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      clientId: cibaClientId,
      scope: "openid profile email",
      bindingMessage: "Device IDs test",
      userCode: "123456",
      loginHint: `sub:${userId},idp:idp-server`,
      clientSecret: cibaClientSecret,
    });

    expect(cibaRequest.status).toBe(200);
    expect(cibaRequest.data).toHaveProperty("auth_req_id");
    const authReqId = cibaRequest.data.auth_req_id;
    console.log(`✅ CIBA request accepted, auth_req_id: ${authReqId}`);

    console.log("\n=== Step 5: Complete Authentication via Device ===");

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

    const completeResponse = await postAuthenticationDeviceInteraction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentications/{id}/`,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "password-authentication",
      body: {
        username: userEmail,
        password: userPassword,
      }
    });
    expect(completeResponse.status).toBe(200);
    console.log("✅ Device authentication completed");

    console.log("\n=== Step 6: Request Token ===");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: authReqId,
      clientId: cibaClientId,
      clientSecret: cibaClientSecret,
    });

    expect(tokenResponse.status).toBe(200);
    console.log("✅ Token obtained");

    console.log("\n=== Step 7: Verify Security Events ===");

    await sleep(1500);

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events?limit=30`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    expect(securityEventsResponse.status).toBe(200);
    const events = securityEventsResponse.data.list || [];
    console.log(`✅ Retrieved ${events.length} security events`);

    // Verify authentication_device_ids is included in security events
    const eventsWithDeviceIds = events.filter(event => {
      const deviceIds = event.user?.authentication_device_ids || event.detail?.user?.authentication_device_ids;
      return deviceIds && deviceIds.length > 0;
    });

    expect(eventsWithDeviceIds.length).toBeGreaterThan(0);
    console.log(`  Events with device IDs: ${eventsWithDeviceIds.length}`);

    // Verify the device ID matches what was registered
    eventsWithDeviceIds.forEach(event => {
      const deviceIds = event.user?.authentication_device_ids || event.detail?.user?.authentication_device_ids;
      console.log(`    - ${event.type}: ${JSON.stringify(deviceIds)}`);
      expect(deviceIds).toContain(deviceId);
    });

    console.log("✅ Authentication device IDs correctly included in security events");

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
    console.log("✅ Verified: Authentication device IDs are included in CIBA security events (Issue #1124)");
  });
});
