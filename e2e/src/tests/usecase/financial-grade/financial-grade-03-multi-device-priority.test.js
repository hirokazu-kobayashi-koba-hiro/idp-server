import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { mtlsPost, mtlsPostWithJson } from "../../../lib/http/mtls";
import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestToken,
  getJwks
} from "../../../api/oauthClient";
import { createJwtWithPrivateKey, generateJti, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mtlBackendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import fs from "fs";
import path from "path";
import { toEpocTime } from "../../../lib/util";
import { calculateCodeChallengeWithS256, generateCodeVerifier } from "../../../lib/oauth";

/**
 * Financial Grade: Multi-Device Priority - Later Registration Wins (Issue #1027)
 *
 * This test verifies that when a user registers multiple authentication devices
 * sequentially WITHOUT explicit priority values, the LAST registered device is selected.
 *
 * Uses FIDO-UAF registration flow via mTLS (FAPI compliant) to register
 * devices one by one, then sends CIBA request to verify which device is selected.
 *
 * Expected behavior (後勝ち = Later Registration Wins):
 * - First device registered: auto priority = 1
 * - Second device registered: auto priority = 2
 * - Third device registered: auto priority = 3
 *
 * With max() selection, the last registered device (highest auto-priority) wins.
 * This is consistent with AuthenticationPolicy priority logic.
 */
describe("Financial Grade: Multi-Device Priority - Later Registration Wins (Issue #1027)", () => {
  let systemAccessToken;
  let organizationId;
  let organizerTenantId;
  let testTenantId;
  let testClientId;
  let userId;
  let userAccessToken;
  let orgAdminToken;

  // Load configuration files as templates
  const configDir = path.join(process.cwd(), "../config/examples/financial-grade");
  const onboardingTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "onboarding-request.json"), "utf8")
  );
  const financialTenantTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "financial-tenant.json"), "utf8")
  );
  const financialClientTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "financial-client.json"), "utf8")
  );
  const initialRegConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/initial-registration/standard.json"), "utf8")
  );
  const smsConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/sms/external.json"), "utf8")
  );
  const fidoUafConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/fido-uaf/external.json"), "utf8")
  );
  const authPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/oauth.json"), "utf8")
  );
  const fidoUafRegistrationPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/fido-uaf-registration.json"), "utf8")
  );

  let onboardingConfig;
  let testTenantConfig;
  let testClientConfig;
  let initialRegConfig;
  let smsConfig;
  let fidoUafConfig;
  let authPolicyConfig;
  let fidoUafRegPolicyConfig;
  let clientCertPath;
  let clientKeyPath;
  let userEmail;
  let userPassword;

  // Device IDs for verification
  let firstDeviceId;
  let secondDeviceId;
  let thirdDeviceId;

  const timestamp = Date.now();
  const initialRegConfigId = uuidv4();
  const smsConfigId = uuidv4();
  const fidoUafConfigId = uuidv4();
  const authPolicyId = uuidv4();
  const fidoUafRegPolicyId = uuidv4();

  beforeAll(async () => {
    // Get system admin token
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

    // Generate unique IDs
    organizationId = uuidv4();
    organizerTenantId = uuidv4();
    testTenantId = uuidv4();
    testClientId = uuidv4();
    userEmail = `no-priority-test-${timestamp}@example.com`;
    userPassword = `NoPriorityTest${timestamp}!`;

    // Client certificate paths for mTLS
    clientCertPath = path.join(configDir, "certs/client-cert.pem");
    clientKeyPath = path.join(configDir, "certs/client-key.pem");

    // Create onboarding config
    onboardingConfig = JSON.parse(JSON.stringify(onboardingTemplate));
    onboardingConfig.organization.id = organizationId;
    onboardingConfig.tenant.id = organizerTenantId;
    onboardingConfig.user.sub = uuidv4();
    onboardingConfig.client.client_id = uuidv4();
    onboardingConfig.authorization_server.issuer = `${backendUrl}/${organizerTenantId}`;
    onboardingConfig.authorization_server.authorization_endpoint = `${backendUrl}/${organizerTenantId}/v1/authorizations`;
    onboardingConfig.authorization_server.token_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens`;
    onboardingConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${organizerTenantId}/v1/userinfo`;
    onboardingConfig.authorization_server.jwks_uri = `${backendUrl}/${organizerTenantId}/v1/jwks`;
    onboardingConfig.authorization_server.introspection_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/introspection`;
    onboardingConfig.authorization_server.revocation_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/revocation`;
    onboardingConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${organizerTenantId}/v1/backchannel/authentications`;

    // Create tenant config with CIBA support and max_devices = 5
    testTenantConfig = JSON.parse(JSON.stringify(financialTenantTemplate));
    testTenantConfig.tenant.id = testTenantId;
    testTenantConfig.tenant.name = "No-Priority Multi-Device Test Tenant";
    testTenantConfig.tenant.identity_policy_config = {
      identity_unique_key_type: "EMAIL",
      authentication_device_rule: {
        max_devices: 5,
        required_identity_verification: false,
      },
    };
    testTenantConfig.authorization_server.issuer = `${backendUrl}/${testTenantId}`;
    testTenantConfig.authorization_server.authorization_endpoint = `${backendUrl}/${testTenantId}/v1/authorizations`;
    testTenantConfig.authorization_server.token_endpoint = `${backendUrl}/${testTenantId}/v1/tokens`;
    testTenantConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${testTenantId}/v1/userinfo`;
    testTenantConfig.authorization_server.jwks_uri = `${backendUrl}/${testTenantId}/v1/jwks`;
    testTenantConfig.authorization_server.introspection_endpoint = `${backendUrl}/${testTenantId}/v1/tokens/introspection`;
    testTenantConfig.authorization_server.revocation_endpoint = `${backendUrl}/${testTenantId}/v1/tokens/revocation`;
    testTenantConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${testTenantId}/v1/backchannel/authentications`;
    // Add CIBA grant type
    testTenantConfig.authorization_server.grant_types_supported = [
      "authorization_code", "refresh_token", "urn:openid:params:grant-type:ciba"
    ];
    testTenantConfig.authorization_server.backchannel_token_delivery_modes_supported = ["poll", "ping"];
    testTenantConfig.authorization_server.backchannel_authentication_request_signing_alg_values_supported = ["RS256", "ES256"];
    testTenantConfig.authorization_server.backchannel_user_code_parameter_supported = true;
    testTenantConfig.authorization_server.extension = {
      ...testTenantConfig.authorization_server.extension,
      backchannel_authentication_polling_interval: 5,
      backchannel_authentication_request_expires_in: 300,
      required_backchannel_auth_user_code: true,
      backchannel_auth_user_code_type: "numeric",
    };

    // Client config - add CIBA grant type to mTLS client
    testClientConfig = JSON.parse(JSON.stringify(financialClientTemplate));
    testClientConfig.client_id = testClientId;
    // Add CIBA grant type and settings
    testClientConfig.grant_types = [
      ...testClientConfig.grant_types,
      "urn:openid:params:grant-type:ciba"
    ];
    testClientConfig.backchannel_token_delivery_mode = "poll";
    testClientConfig.backchannel_user_code_parameter = true;
    testClientConfig.extension = {
      ...testClientConfig.extension,
      default_ciba_authentication_interaction_type: "authentication-device-notification-no-action",
    };

    // Authentication configs
    initialRegConfig = JSON.parse(JSON.stringify(initialRegConfigTemplate));
    initialRegConfig.id = initialRegConfigId;

    smsConfig = JSON.parse(JSON.stringify(smsConfigTemplate));
    smsConfig.id = smsConfigId;

    fidoUafConfig = JSON.parse(JSON.stringify(fidoUafConfigTemplate));
    fidoUafConfig.id = fidoUafConfigId;

    // Authentication policies
    authPolicyConfig = JSON.parse(JSON.stringify(authPolicyTemplate));
    authPolicyConfig.id = authPolicyId;

    fidoUafRegPolicyConfig = JSON.parse(JSON.stringify(fidoUafRegistrationPolicyTemplate));
    fidoUafRegPolicyConfig.id = fidoUafRegPolicyId;
  });

  afterAll(async () => {
    // Cleanup
    console.log("\n=== Cleanup ===");

    if (testClientId && testTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${testTenantId}/clients/${testClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (userId && testTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${testTenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (testTenantId && authPolicyConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${testTenantId}/authentication-policies/${authPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (testTenantId && fidoUafRegPolicyConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${testTenantId}/authentication-policies/${fidoUafRegPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (testTenantId && initialRegConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${testTenantId}/authentication-configurations/${initialRegConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (testTenantId && smsConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${testTenantId}/authentication-configurations/${smsConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (testTenantId && fidoUafConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${testTenantId}/authentication-configurations/${fidoUafConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (testTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${testTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    const adminClientId = onboardingConfig?.client?.client_id;
    if (adminClientId && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/clients/${adminClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    const adminUserId = onboardingConfig?.user?.sub;
    if (adminUserId && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/users/${adminUserId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (organizationId) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("should select the last registered device when priority is not specified (後勝ち)", async () => {
    // Skip if client certificate not found
    if (!fs.existsSync(clientCertPath) || !fs.existsSync(clientKeyPath)) {
      console.log("⚠️  Client certificate not found, skipping test");
      return;
    }

    // Step 1: Create organization
    console.log("\n=== Step 1: Creating Organization ===");
    const createOrgResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingConfig,
    });
    expect(createOrgResponse.status).toBe(201);
    console.log(`✅ Organization created: ${organizationId}`);

    // Step 2: Login as organization admin
    console.log("\n=== Step 2: Login as Organization Admin ===");
    const orgTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${organizerTenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingConfig.user.email,
      password: onboardingConfig.user.raw_password,
      scope: "openid profile email management",
      clientId: onboardingConfig.client.client_id,
      clientSecret: onboardingConfig.client.client_secret,
    });
    expect(orgTokenResponse.status).toBe(200);
    orgAdminToken = orgTokenResponse.data.access_token;
    console.log("✅ Organization admin logged in");

    // Step 3: Create tenant with CIBA support
    console.log("\n=== Step 3: Creating Tenant with CIBA Support ===");
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: testTenantConfig,
    });
    expect(createTenantResponse.status).toBe(201);
    console.log(`✅ Tenant created: ${testTenantId}`);

    // Step 4: Create mTLS client for user registration
    console.log("\n=== Step 4: Creating mTLS Client ===");
    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${testTenantId}/clients`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: testClientConfig,
    });
    expect(createClientResponse.status).toBe(201);
    console.log(`✅ Client created: ${testClientId}`);

    // Step 5: Create authentication configs
    console.log("\n=== Step 5: Creating Authentication Configs ===");
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${testTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: initialRegConfig,
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${testTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: smsConfig,
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${testTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: fidoUafConfig,
    });
    console.log("✅ Authentication configurations created");

    // Step 6: Create authentication policies
    console.log("\n=== Step 6: Creating Authentication Policies ===");
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${testTenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: authPolicyConfig,
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${testTenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: fidoUafRegPolicyConfig,
    });
    console.log("✅ Authentication policies created");

    // Step 7: Register user via Authorization Code Flow + PKCE + JAR
    console.log("\n=== Step 7: Registering User ===");

    const codeVerifier = generateCodeVerifier(64);
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
    const nonce = crypto.randomBytes(16).toString("base64url");
    const state = crypto.randomBytes(16).toString("hex");

    const clientJwks = JSON.parse(testClientConfig.jwks);
    const clientPrivateKey = clientJwks.keys[0];

    const issuer = `${backendUrl}/${testTenantId}`;
    const requestObject = createJwtWithPrivateKey({
      payload: {
        response_type: "code",
        client_id: testClientId,
        redirect_uri: testClientConfig.redirect_uris[0],
        scope: "openid profile transfers",
        state: state,
        nonce: nonce,
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
        response_mode: "jwt",
        aud: issuer,
        iss: testClientId,
        exp: toEpocTime({ adjusted: 300 }),
        iat: toEpocTime({}),
        nbf: toEpocTime({}),
        jti: generateJti(),
      },
      privateKey: clientPrivateKey,
    });

    const authzParams = new URLSearchParams({
      request: requestObject,
      client_id: testClientId,
    });

    const authzResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${authzParams.toString()}`,
    });

    expect(authzResponse.status).toBe(302);
    const location = authzResponse.headers.location;
    const authTxId = new URL(location).searchParams.get("id");

    // Register new user
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${authTxId}/initial-registration`,
      body: {
        email: userEmail,
        name: "No Priority Test User",
        phone_number: "+81-90-1234-5678",
        password: userPassword,
        birthdate: "1990-01-01",
        address: {
          country: "JP",
          postal_code: "100-0001",
          region: "Tokyo",
          locality: "Chiyoda",
        },
      },
    });

    expect(registrationResponse.status).toBe(200);
    userId = registrationResponse.data.user.sub;
    console.log(`✅ User registered: ${userId}`);

    // SMS authentication
    await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${authTxId}/sms-authentication-challenge`,
      body: { phone_number: "+81-90-1234-5678", template: "authentication" },
    });
    await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${authTxId}/sms-authentication`,
      body: { verification_code: "123456" },
    });

    // Authorize
    const authorizeResponse = await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${authTxId}/authorize`,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);

    const authRedirectUri = new URL(authorizeResponse.data.redirect_uri);
    const jarmResponse = authRedirectUri.searchParams.get("response");

    const jwksResponse = await getJwks({
      endpoint: `${backendUrl}/${testTenantId}/v1/jwks`,
    });
    const decodedJarm = verifyAndDecodeJwt({
      jwt: jarmResponse,
      jwks: jwksResponse.data,
    });
    const code = decodedJarm.payload.code;

    // Exchange code for token via mTLS
    const tokenParams = new URLSearchParams({
      grant_type: "authorization_code",
      code: code,
      redirect_uri: testClientConfig.redirect_uris[0],
      client_id: testClientId,
      code_verifier: codeVerifier,
    });

    const tokenResponse = await mtlsPost({
      url: `${mtlBackendUrl}/${testTenantId}/v1/tokens`,
      body: tokenParams.toString(),
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });

    expect(tokenResponse.status).toBe(200);
    userAccessToken = tokenResponse.data.access_token;
    console.log("✅ User access token obtained");

    // Step 8: Register THREE devices sequentially WITHOUT specifying priority
    console.log("\n=== Step 8: Registering 3 Devices WITHOUT Priority ===");
    const authenticationDeviceEndpoint = `${backendUrl}/${testTenantId}/v1/authentications/{id}/`;

    // Device 1 (First registered - should get auto priority 1)
    console.log("  Registering first device...");
    let mfaResponse = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${testTenantId}/v1/me/mfa/fido-uaf-registration`,
      body: {
        app_name: "First App",
        platform: "Android",
        os: "Android15",
        model: "Device 1",
        locale: "ja",
        notification_channel: "fcm",
        notification_token: "token1",
        // NO priority specified
      },
      headers: { Authorization: `Bearer ${userAccessToken}` },
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });
    expect(mfaResponse.status).toBe(200);

    let transactionId = mfaResponse.data.id;
    await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration-challenge",
      body: {},
    });
    let interactionResponse = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration",
      body: {},
    });
    expect(interactionResponse.status).toBe(200);
    firstDeviceId = interactionResponse.data.device_id;
    console.log(`  ✅ First device registered: ${firstDeviceId} (expected auto priority: 1)`);

    // Send authentication device log for first device
    await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authentication-devices/logs`,
      body: {
        device_id: firstDeviceId,
        event: "fido_uaf_registration",
        status: "success",
        timestamp: new Date().toISOString(),
        details: {
          app_name: "First App",
          platform: "Android",
          model: "Device 1",
        },
      },
    });
    console.log(`  📝 Authentication device log sent for first device`);

    // Device 2 (Second registered - should get auto priority 2)
    console.log("  Registering second device...");
    mfaResponse = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${testTenantId}/v1/me/mfa/fido-uaf-registration`,
      body: {
        app_name: "Second App",
        platform: "iOS",
        os: "iOS18",
        model: "Device 2",
        locale: "en",
        notification_channel: "apns",
        notification_token: "token2",
        // NO priority specified
      },
      headers: { Authorization: `Bearer ${userAccessToken}` },
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });
    expect(mfaResponse.status).toBe(200);

    transactionId = mfaResponse.data.id;
    await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration-challenge",
      body: {},
    });
    interactionResponse = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration",
      body: {},
    });
    expect(interactionResponse.status).toBe(200);
    secondDeviceId = interactionResponse.data.device_id;
    console.log(`  ✅ Second device registered: ${secondDeviceId} (expected auto priority: 2)`);

    // Send authentication device log for second device
    await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authentication-devices/logs`,
      body: {
        device_id: secondDeviceId,
        event: "fido_uaf_registration",
        status: "success",
        timestamp: new Date().toISOString(),
        details: {
          app_name: "Second App",
          platform: "iOS",
          model: "Device 2",
        },
      },
    });
    console.log(`  📝 Authentication device log sent for second device`);

    // Device 3 (Third registered - should get auto priority 3)
    console.log("  Registering third device...");
    mfaResponse = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${testTenantId}/v1/me/mfa/fido-uaf-registration`,
      body: {
        app_name: "Third App",
        platform: "Android",
        os: "Android16",
        model: "Device 3",
        locale: "ja",
        notification_channel: "fcm",
        notification_token: "token3",
        // NO priority specified
      },
      headers: { Authorization: `Bearer ${userAccessToken}` },
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });
    expect(mfaResponse.status).toBe(200);

    transactionId = mfaResponse.data.id;
    await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration-challenge",
      body: {},
    });
    interactionResponse = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration",
      body: {},
    });
    expect(interactionResponse.status).toBe(200);
    thirdDeviceId = interactionResponse.data.device_id;
    console.log(`  ✅ Third device registered: ${thirdDeviceId} (expected auto priority: 3)`);

    // Send authentication device log for third device
    await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authentication-devices/logs`,
      body: {
        device_id: thirdDeviceId,
        event: "fido_uaf_registration",
        status: "success",
        timestamp: new Date().toISOString(),
        details: {
          app_name: "Third App",
          platform: "Android",
          model: "Device 3",
        },
      },
    });
    console.log(`  📝 Authentication device log sent for third device`);

    // Step 9: Send CIBA request via mTLS (using same client)
    console.log("\n=== Step 9: Sending CIBA Request via mTLS ===");

    const cibaParams = new URLSearchParams({
      scope: "openid profile",
      binding_message: "No-priority test",
      user_code: "123456",
      login_hint: `sub:${userId},idp:idp-server`,
      client_id: testClientId,
    });

    const cibaRequest = await mtlsPost({
      url: `${mtlBackendUrl}/${testTenantId}/v1/backchannel/authentications`,
      body: cibaParams.toString(),
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });

    console.log("CIBA response status:", cibaRequest.status);
    console.log("CIBA response data:", JSON.stringify(cibaRequest.data, null, 2));
    expect(cibaRequest.status).toBe(200);
    expect(cibaRequest.data).toHaveProperty("auth_req_id");
    const authReqId = cibaRequest.data.auth_req_id;
    console.log(`✅ CIBA request accepted: ${authReqId}`);

    // Step 10: Verify which device received the request
    console.log("\n=== Step 10: Verifying Which Device Received the Request ===");

    const firstDeviceTransactions = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: `${backendUrl}/${testTenantId}/v1/authentication-devices/{id}/authentications`,
      deviceId: firstDeviceId,
      params: { "attributes.auth_req_id": authReqId },
    });

    const secondDeviceTransactions = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: `${backendUrl}/${testTenantId}/v1/authentication-devices/{id}/authentications`,
      deviceId: secondDeviceId,
      params: { "attributes.auth_req_id": authReqId },
    });

    const thirdDeviceTransactions = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: `${backendUrl}/${testTenantId}/v1/authentication-devices/{id}/authentications`,
      deviceId: thirdDeviceId,
      params: { "attributes.auth_req_id": authReqId },
    });

    const firstCount = firstDeviceTransactions.data.list?.length || 0;
    const secondCount = secondDeviceTransactions.data.list?.length || 0;
    const thirdCount = thirdDeviceTransactions.data.list?.length || 0;

    console.log(`  First device (1st registered): ${firstCount} transaction(s)`);
    console.log(`  Second device (2nd registered): ${secondCount} transaction(s)`);
    console.log(`  Third device (3rd registered): ${thirdCount} transaction(s)`);

    // Determine which device received the transaction
    let selectedDevice;
    let selectedPriority;

    if (thirdCount > 0) {
      selectedDevice = "Third device (3rd registered)";
      selectedPriority = 3;
    } else if (secondCount > 0) {
      selectedDevice = "Second device (2nd registered)";
      selectedPriority = 2;
    } else if (firstCount > 0) {
      selectedDevice = "First device (1st registered)";
      selectedPriority = 1;
    } else {
      throw new Error("No device received the authentication transaction!");
    }

    console.log(`\n  → Selected device: ${selectedDevice}`);

    // 後勝ち: Third device (last registered) should be selected
    expect(selectedPriority).toBe(3);
    console.log("✅ Third device (last registered) correctly selected");

    console.log("\n=== Test Completed Successfully ===");
    console.log("✅ Verified: When priority is not specified, LAST registered device is selected (後勝ち)");
    console.log("   Registration order determines priority:");
    console.log("   - 1st registered → auto priority 1");
    console.log("   - 2nd registered → auto priority 2");
    console.log("   - 3rd registered → auto priority 3 (selected by max())");
  });
});
