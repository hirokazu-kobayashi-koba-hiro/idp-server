import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, postWithJson } from "../../../lib/http";
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

/**
 * CIBA Use Case: Multi-Device Priority Selection (Issue #1027)
 *
 * This test verifies that when a user has multiple authentication devices,
 * CIBA selects the device with the HIGHEST priority value.
 *
 * Priority logic (after Issue #1027 fix):
 * - Higher priority value = higher precedence (e.g., 100 > 50 > 1)
 * - Consistent with AuthenticationPolicy priority logic
 *
 * Test scenario:
 * 1. Create user with 3 devices: priority 1, priority 50, priority 100
 * 2. Send CIBA request
 * 3. Verify device with priority 100 receives the authentication request
 */
describe("CIBA Use Case: Multi-Device Priority Selection (Issue #1027)", () => {
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

  it("should select device with highest priority value for CIBA notification", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@multi-device.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const cibaClientId = uuidv4();
    const cibaClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    // Create 3 devices with different priorities
    const lowPriorityDeviceId = uuidv4();    // priority: 1 (lowest)
    const mediumPriorityDeviceId = uuidv4(); // priority: 50 (medium)
    const highPriorityDeviceId = uuidv4();   // priority: 100 (highest - should be selected)

    console.log("\n=== Step 1: Create Organization and Tenant with Multi-Device User ===");
    console.log(`  Low priority device (1): ${lowPriorityDeviceId}`);
    console.log(`  Medium priority device (50): ${mediumPriorityDeviceId}`);
    console.log(`  High priority device (100): ${highPriorityDeviceId}`);

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `CIBA Multi-Device Test Org ${timestamp}`,
        description: "CIBA multi-device priority test organization",
      },
      tenant: {
        id: tenantId,
        name: `CIBA Multi-Device Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `CIBA_MD_SESSION_${organizationId.substring(0, 8)}`,
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
        // Register 3 devices with different priorities
        authentication_devices: [
          {
            id: lowPriorityDeviceId,
            app_name: "Low Priority App",
            priority: 1,  // Lowest priority
          },
          {
            id: mediumPriorityDeviceId,
            app_name: "Medium Priority App",
            priority: 50, // Medium priority
          },
          {
            id: highPriorityDeviceId,
            app_name: "High Priority App",
            priority: 100, // Highest priority - should be selected
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-org-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: `Test Organization Client ${timestamp}`,
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
    expect(onboardingResponse.status).toBe(201);

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

    console.log("\n=== Step 3: Create CIBA Client ===");

    const clientRequest = {
      client_id: cibaClientId,
      client_secret: cibaClientSecret,
      client_name: `CIBA Multi-Device Client ${timestamp}`,
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
      body: clientRequest,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    expect(clientCreationResponse.status).toBe(201);
    console.log("✅ CIBA Client created");

    console.log("\n=== Step 4: Send CIBA Request (using sub to identify user) ===");

    // Send CIBA request using login_hint with user's sub
    // The system should automatically select the highest priority device
    // Note: Using sub:userId format for user identification
    const cibaRequest = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      clientId: cibaClientId,
      scope: "openid profile email",
      bindingMessage: "Multi-device test",
      userCode: "123456",
      loginHint: `sub:${userId},idp:idp-server`,
      clientSecret: cibaClientSecret,
    });

    console.log("CIBA request response:", JSON.stringify(cibaRequest.data, null, 2));
    expect(cibaRequest.status).toBe(200);
    expect(cibaRequest.data).toHaveProperty("auth_req_id");

    const authReqId = cibaRequest.data.auth_req_id;
    console.log(`✅ CIBA request accepted, auth_req_id: ${authReqId}`);

    console.log("\n=== Step 5: Verify Which Device Received the Request ===");

    // Check all devices to see which one received the authentication transaction
    // After Issue #1027 fix: The device with priority 100 (highest value) should receive it
    // Before fix: The device with priority 1 (lowest value) would receive it

    const highPriorityDeviceTransactions = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentication-devices/{id}/authentications`,
      deviceId: highPriorityDeviceId,
      params: {
        "attributes.auth_req_id": authReqId
      },
    });

    const mediumPriorityDeviceTransactions = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentication-devices/{id}/authentications`,
      deviceId: mediumPriorityDeviceId,
      params: {
        "attributes.auth_req_id": authReqId
      },
    });

    const lowPriorityDeviceTransactions = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentication-devices/{id}/authentications`,
      deviceId: lowPriorityDeviceId,
      params: {
        "attributes.auth_req_id": authReqId
      },
    });

    const highCount = highPriorityDeviceTransactions.data.list?.length || 0;
    const mediumCount = mediumPriorityDeviceTransactions.data.list?.length || 0;
    const lowCount = lowPriorityDeviceTransactions.data.list?.length || 0;

    console.log(`  High priority device (100): ${highCount} transaction(s)`);
    console.log(`  Medium priority device (50): ${mediumCount} transaction(s)`);
    console.log(`  Low priority device (1): ${lowCount} transaction(s)`);

    // Determine which device received the transaction
    let selectedDevice;
    let selectedDeviceTransactions;
    let selectedPriority;

    if (highCount > 0) {
      selectedDevice = "High priority (100)";
      selectedDeviceTransactions = highPriorityDeviceTransactions;
      selectedPriority = 100;
    } else if (mediumCount > 0) {
      selectedDevice = "Medium priority (50)";
      selectedDeviceTransactions = mediumPriorityDeviceTransactions;
      selectedPriority = 50;
    } else if (lowCount > 0) {
      selectedDevice = "Low priority (1)";
      selectedDeviceTransactions = lowPriorityDeviceTransactions;
      selectedPriority = 1;
    } else {
      throw new Error("No device received the authentication transaction!");
    }

    console.log(`\n  → Selected device: ${selectedDevice}`);

    // After Issue #1027 fix, high priority device (100) should be selected
    // If low priority (1) is selected, the fix hasn't been applied
    expect(selectedPriority).toBe(100);
    console.log("✅ High priority device (100) correctly selected (Issue #1027 fix verified)");

    console.log("\n=== Step 6: Complete Authentication on Selected Device ===");

    const authenticationTransaction = selectedDeviceTransactions.data.list[0];

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
    console.log("✅ Authentication completed on high priority device");

    console.log("\n=== Step 7: Request Token ===");

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

    console.log("\n=== Step 8: Clean Up Resources ===");

    // Delete clients
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${orgClientId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${cibaClientId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    // Delete tenant
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    // Delete organization
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("\n=== Test Completed Successfully ===");
    console.log("✅ Verified: CIBA selects device with HIGHEST priority value");
    console.log("   Priority 100 > Priority 50 > Priority 1");
    console.log("   (Consistent with AuthenticationPolicy priority logic - Issue #1027)");
  });
});
