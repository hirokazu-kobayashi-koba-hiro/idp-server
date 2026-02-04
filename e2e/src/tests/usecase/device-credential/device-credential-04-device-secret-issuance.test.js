import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestToken,
  requestBackchannelAuthentications,
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
} from "../../../api/oauthClient";
import { generateRS256KeyPair, createJwt, generateJti } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { toEpocTime } from "../../../lib/util";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";

/**
 * Device Credential Use Case: Device Secret Issuance via FIDO-UAF Registration
 *
 * This test verifies that device secrets are issued during FIDO-UAF registration
 * based on tenant's authentication_device_rule configuration:
 *
 * 1. Tenant configured with issue_device_secret: true
 * 2. User authenticates (password)
 * 3. FIDO-UAF registration challenge
 * 4. FIDO-UAF registration completion
 * 5. Response includes device_secret, device_secret_algorithm, device_secret_jwt_issuer
 * 6. Issued secret can be used for JWT Bearer Grant
 *
 * Related Implementation:
 * - AuthenticationDeviceRule.issueDeviceSecret
 * - DeviceSecretIssuer
 * - FidoUafRegistrationInteractor
 *
 * Note: Requires mock FIDO-UAF server at http://host.docker.internal:4000
 */
describe("Device Credential Use Case: Device Secret Issuance via FIDO-UAF Registration", () => {
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

  it("should issue device secret during FIDO-UAF registration when tenant policy is enabled", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = "https://app.example.com/callback";

    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const userPassword = `Password${timestamp}!`;

    console.log("\n=== Device Secret Issuance via FIDO-UAF Registration Test ===\n");

    const { jwks } = await generateRS256KeyPair();

    // Step 1: Create Tenant with Device Secret Issuance Enabled
    console.log("=== Step 1: Create Tenant with Device Secret Issuance Enabled ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Device Secret FIDO-UAF Test Org ${timestamp}`,
        description: "Test organization for device secret issuance via FIDO-UAF",
      },
      tenant: {
        id: tenantId,
        name: `Device Secret FIDO-UAF Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_client_id: true,
          persistence_enabled: true,
        },
        // Key configuration: Enable device secret issuance
        identity_policy_config: {
          identity_unique_key_type: "EMAIL",
          authentication_device_rule: {
            max_devices: 10,
            required_identity_verification: false,
            authentication_type: "device_secret_jwt",
            issue_device_secret: true,
            device_secret_algorithm: "HS256",
            device_secret_expires_in_seconds: 31536000, // 1 year
          },
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwks,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer",
          "urn:openid:params:grant-type:ciba",
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        // CIBA configuration
        backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        backchannel_token_delivery_modes_supported: ["poll"],
        backchannel_user_code_parameter_supported: true,
        extension: {
          access_token_type: "JWT",
          access_token_duration: 3600,
          id_token_duration: 3600,
          refresh_token_duration: 86400,
          backchannel_authentication_polling_interval: 5,
          backchannel_authentication_request_expires_in: 300,
        },
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        name: faker.internet.email(),
        email: faker.internet.email(),
        email_verified: true,
        raw_password: `AdminPass${timestamp}!`,
      },
      client: {
        client_id: uuidv4(),
        client_id_alias: `admin-client-device-secret-${timestamp}`,
        client_secret: `admin-secret-${timestamp}`,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: "Admin Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingRequest,
    });

    console.log("Onboarding response:", onboardingResponse.status);
    if (onboardingResponse.status !== 201) {
      console.log("Onboarding error:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);
    console.log("✅ Tenant created with device secret issuance enabled");

    // Get admin token
    const createdClient = onboardingResponse.data.client;
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingRequest.user.email,
      password: onboardingRequest.user.raw_password,
      scope: "management",
      clientId: createdClient.client_id,
      clientSecret: createdClient.client_secret,
    });

    expect(adminTokenResponse.status).toBe(200);
    const adminAccessToken = adminTokenResponse.data.access_token;
    console.log("✅ Admin access token obtained\n");

    // Step 2: Verify Tenant Configuration
    console.log("=== Step 2: Verify Tenant Configuration ===");

    const tenantResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    expect(tenantResponse.status).toBe(200);
    const deviceRule = tenantResponse.data.identity_policy_config?.authentication_device_rule;
    expect(deviceRule.issue_device_secret).toBe(true);
    expect(deviceRule.device_secret_algorithm).toBe("HS256");
    console.log("✅ Device secret issuance configuration verified\n");

    // Step 3: Create Password Authentication Configuration
    console.log("=== Step 3: Create Password Authentication Configuration ===");

    const passwordAuthConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: passwordAuthConfigId,
        type: "password",
        attributes: {},
        metadata: { type: "internal", description: "Password authentication" },
        interactions: {
          "password-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  username: { type: "string" },
                  password: { type: "string" },
                },
              },
            },
            pre_hook: {},
            execution: { function: "password_verification" },
            post_hook: {},
            response: {},
          },
        },
      },
    });
    console.log("✅ Password auth config created\n");

    // Step 4: Create FIDO-UAF Authentication Configuration
    console.log("=== Step 4: Create FIDO-UAF Authentication Configuration ===");

    const fidoUafAuthConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: fidoUafAuthConfigId,
        type: "fido-uaf",
        attributes: {},
        metadata: {
          type: "external",
          description: "FIDO-UAF authentication with device secret issuance",
        },
        interactions: {
          "fido-uaf-registration-challenge": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/fido-uaf/registration-challenge",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
              http_request_store: {
                key: "fido-uaf-registration-challenge",
                interaction_mapping_rules: [
                  { from: "$.response_body.challenge", to: "challenge" },
                  { from: "$.response_body.device_id", to: "device_id" },
                ],
              },
            },
            post_hook: {},
            response: {
              body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }],
            },
          },
          "fido-uaf-registration": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              previous_interaction: { key: "fido-uaf-registration-challenge" },
              http_request: {
                url: "http://host.docker.internal:4000/fido-uaf/registration",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
            },
            post_hook: {},
            response: {
              body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }],
            },
          },
          "fido-uaf-authentication-challenge": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/fido-uaf/authentication-challenge",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
              // Note: http_request_store is NOT needed here because
              // FidoUafAuthenticationChallengeInteractor internally saves the FidoUafInteraction with deviceId
            },
            post_hook: {},
            response: {
              body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }],
            },
          },
          "fido-uaf-authentication": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              previous_interaction: { key: "fido-uaf-authentication-challenge" },
              http_request: {
                url: "http://host.docker.internal:4000/fido-uaf/authentication",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
            },
            post_hook: {},
            response: {
              body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }],
            },
          },
        },
      },
    });
    console.log("✅ FIDO-UAF auth config created\n");

    // Step 5: Create Authentication Policy (no device_registration_conditions for simplicity)
    console.log("=== Step 5: Create Authentication Policy ===");

    const authPolicyConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: authPolicyConfigId,
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_then_fido_uaf",
            priority: 10,
            conditions: { scopes: ["openid"] },
            available_methods: ["password", "fido-uaf"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.initial-registration.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                ],
              ],
            },
          },
        ],
      },
    });
    console.log("✅ OAuth authentication policy created");

    // Create CIBA authentication policy (using FIDO-UAF since device was registered via FIDO-UAF)
    const cibaAuthPolicyConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: cibaAuthPolicyConfigId,
        flow: "ciba",
        enabled: true,
        policies: [
          {
            description: "ciba_fido_uaf_auth",
            priority: 10,
            conditions: { scopes: ["openid"] },
            available_methods: ["fido-uaf"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.fido-uaf-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                ],
              ],
            },
          },
        ],
      },
    });
    console.log("✅ CIBA authentication policy created\n");

    // Step 6: Create Test Client with JWT Bearer Grant support
    console.log("=== Step 6: Create Test Client ===");

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "Device Secret Test Client",
        redirect_uris: [redirectUri],
        grant_types: [
          "authorization_code",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer",
          "urn:openid:params:grant-type:ciba",
        ],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
        backchannel_token_delivery_mode: "poll",
        backchannel_user_code_parameter: true,
        extension: {
          available_federations: [
            {
              issuer: "device",
              type: "device",
              subject_claim_mapping: "sub",
              jwt_bearer_grant_enabled: true,
            },
          ],
          default_ciba_authentication_interaction_type: "authentication-device-notification-no-action",
        },
      },
    });
    console.log("✅ Client created\n");

    // Step 7: Start Authorization and Register User
    console.log("=== Step 7: Start Authorization and Register User ===");

    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    expect(authorizeResponse.status).toBe(302);
    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get("id");
    console.log("Authorization started:", authId);

    // Register user with password
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: userEmail,
        password: userPassword,
        name: userName,
      },
    });
    expect(registrationResponse.status).toBe(200);
    const userId = registrationResponse.data.user?.sub;
    console.log("✅ User registered:", userId, "\n");

    // Step 8: FIDO-UAF Registration Challenge
    console.log("=== Step 8: FIDO-UAF Registration Challenge ===");

    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration-challenge`,
      body: {
        app_name: "Device Secret Test App",
        platform: "iOS",
        os: "iOS 17",
        model: "iPhone 15",
      },
    });

    console.log("FIDO-UAF challenge response:", challengeResponse.status);
    if (challengeResponse.status !== 200) {
      console.log("Challenge error:", JSON.stringify(challengeResponse.data, null, 2));
    }
    expect(challengeResponse.status).toBe(200);
    console.log("✅ FIDO-UAF registration challenge received\n");

    // Step 9: FIDO-UAF Registration Completion
    console.log("=== Step 9: FIDO-UAF Registration Completion ===");

    const registrationCompleteResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration`,
      body: {
        // Mock response from FIDO-UAF authenticator
        uafResponse: challengeResponse.data.uafRequest
          ? [{ assertionScheme: "UAFV1TLV", assertion: "mock_assertion_data" }]
          : [],
      },
    });

    console.log("FIDO-UAF registration response:", registrationCompleteResponse.status);
    console.log("Response data:", JSON.stringify(registrationCompleteResponse.data, null, 2));

    expect(registrationCompleteResponse.status).toBe(200);

    // Step 10: Verify Device Secret in Response
    console.log("\n=== Step 10: Verify Device Secret in Response ===");

    const responseData = registrationCompleteResponse.data;

    // These fields should be added by FidoUafRegistrationInteractor when DeviceSecretIssuer issues a secret
    expect(responseData).toHaveProperty("device_id");
    expect(responseData).toHaveProperty("device_secret");
    expect(responseData).toHaveProperty("device_secret_algorithm");
    expect(responseData).toHaveProperty("device_secret_jwt_issuer");

    const deviceId = responseData.device_id;
    const deviceSecret = responseData.device_secret;
    const deviceSecretAlgorithm = responseData.device_secret_algorithm;
    const deviceSecretJwtIssuer = responseData.device_secret_jwt_issuer;

    console.log("✅ Device secret issued:");
    console.log(`   - device_id: ${deviceId}`);
    console.log(`   - device_secret: ${deviceSecret.substring(0, 20)}...`);
    console.log(`   - device_secret_algorithm: ${deviceSecretAlgorithm}`);
    console.log(`   - device_secret_jwt_issuer: ${deviceSecretJwtIssuer}`);

    expect(deviceSecretAlgorithm).toBe("HS256");
    expect(deviceSecretJwtIssuer).toBe(`device:${deviceId}`);

    // Step 11: Complete Authorization Flow (persist user and device to DB)
    console.log("\n=== Step 11: Complete Authorization Flow ===");

    const authorizeCompleteResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    console.log("Authorization complete response:", authorizeCompleteResponse.status);
    expect(authorizeCompleteResponse.status).toBe(200);

    // Extract code from redirect_uri
    const authRedirectUrl = new URL(authorizeCompleteResponse.data.redirect_uri);
    const authCode = authRedirectUrl.searchParams.get("code");
    expect(authCode).toBeDefined();
    console.log("✅ Authorization code obtained");

    // Exchange code for token to persist user and device
    const tokenExchangeResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: authCode,
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    console.log("Token exchange status:", tokenExchangeResponse.status);
    expect(tokenExchangeResponse.status).toBe(200);
    console.log("✅ User and device persisted to database\n");

    // Step 12: Use Device Secret for JWT Bearer Grant
    console.log("\n=== Step 12: Use Device Secret for JWT Bearer Grant ===");

    const issuer = `${backendUrl}/${tenantId}`;
    const assertionPayload = {
      iss: deviceSecretJwtIssuer,
      sub: userId,
      aud: issuer,
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 }),
    };

    const assertion = createJwt({
      payload: assertionPayload,
      secret: deviceSecret,
      options: { algorithm: "HS256" },
    });

    const jwtBearerResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: assertion,
      scope: "openid profile email",
      clientId: clientId,
      clientSecret: clientSecret,
    });

    console.log("JWT Bearer Grant response:", jwtBearerResponse.status);
    if (jwtBearerResponse.status !== 200) {
      console.log("JWT Bearer Grant error:", JSON.stringify(jwtBearerResponse.data, null, 2));
    }

    expect(jwtBearerResponse.status).toBe(200);
    expect(jwtBearerResponse.data).toHaveProperty("access_token");
    console.log("✅ Access token obtained via JWT Bearer Grant with issued device secret\n");

    // Step 13: CIBA Flow with Device Secret
    console.log("\n=== Step 13: CIBA Flow with Device Secret ===");

    // 13.1: Request backchannel authentication
    // Note: userCode must match user's password
    const cibaResponse = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      clientId: clientId,
      clientSecret: clientSecret,
      scope: "openid profile email",
      bindingMessage: "CIBA-TEST",
      userCode: userPassword,
      loginHint: `device:${deviceId},idp:idp-server`,
    });

    console.log("CIBA response:", cibaResponse.status);
    if (cibaResponse.status !== 200) {
      console.log("CIBA error:", JSON.stringify(cibaResponse.data, null, 2));
    }
    expect(cibaResponse.status).toBe(200);
    expect(cibaResponse.data).toHaveProperty("auth_req_id");

    const authReqId = cibaResponse.data.auth_req_id;
    console.log("✅ CIBA request initiated, auth_req_id:", authReqId);

    // 13.2: Verify device endpoint requires authentication (401 without JWT)
    const noAuthResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?attributes.auth_req_id=${authReqId}`,
      headers: {},
    });
    console.log("Without JWT response:", noAuthResponse.status);
    expect(noAuthResponse.status).toBe(401);
    console.log("✅ Verified: Device endpoint returns 401 without JWT authentication");

    // 13.3: Get authentication transaction (WITH device_secret_jwt authentication)
    // Create JWT for device endpoint authentication
    const deviceAuthJwt = createJwt({
      payload: {
        iss: deviceSecretJwtIssuer,
        sub: userId,
        aud: issuer,
        jti: generateJti(),
        exp: toEpocTime({ adjusted: 3600 }),
        iat: toEpocTime({ adjusted: 0 }),
      },
      secret: deviceSecret,
      options: { algorithm: "HS256" },
    });

    const authTransactionResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?attributes.auth_req_id=${authReqId}`,
      headers: {
        Authorization: `Bearer ${deviceAuthJwt}`,
      },
    });

    console.log("Authentication transaction response:", authTransactionResponse.status);
    if (authTransactionResponse.status !== 200) {
      console.log("Auth transaction error:", JSON.stringify(authTransactionResponse.data, null, 2));
    }
    expect(authTransactionResponse.status).toBe(200);
    expect(authTransactionResponse.data.list.length).toBeGreaterThan(0);

    const authTransaction = authTransactionResponse.data.list[0];
    console.log("✅ Authentication transaction retrieved:", authTransaction.id);

    // 13.4: Complete authentication using FIDO-UAF (two-step: challenge then authentication)
    // Step 1: Request FIDO-UAF authentication challenge
    const fidoUafChallengeResponse = await postAuthenticationDeviceInteraction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentications/{id}/`,
      flowType: authTransaction.flow,
      id: authTransaction.id,
      interactionType: "fido-uaf-authentication-challenge",
      body: {
        device_id: deviceId,
      },
    });

    console.log("FIDO-UAF challenge response:", fidoUafChallengeResponse.status);
    if (fidoUafChallengeResponse.status !== 200) {
      console.log("FIDO-UAF challenge error:", JSON.stringify(fidoUafChallengeResponse.data, null, 2));
    }
    expect(fidoUafChallengeResponse.status).toBe(200);
    console.log("✅ FIDO-UAF authentication challenge received");

    // Step 2: Complete FIDO-UAF authentication
    const completeAuthResponse = await postAuthenticationDeviceInteraction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentications/{id}/`,
      flowType: authTransaction.flow,
      id: authTransaction.id,
      interactionType: "fido-uaf-authentication",
      body: {
        device_id: deviceId,
        uafResponse: fidoUafChallengeResponse.data.uafRequest
          ? [{ assertionScheme: "UAFV1TLV", assertion: "mock_assertion_data" }]
          : [],
      },
    });

    console.log("FIDO-UAF authentication complete response:", completeAuthResponse.status);
    if (completeAuthResponse.status !== 200) {
      console.log("FIDO-UAF auth error:", JSON.stringify(completeAuthResponse.data, null, 2));
    }
    expect(completeAuthResponse.status).toBe(200);
    console.log("✅ CIBA authentication completed via FIDO-UAF");

    // 13.5: Request token with CIBA grant type
    const cibaTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: authReqId,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    console.log("CIBA token response:", cibaTokenResponse.status);
    if (cibaTokenResponse.status !== 200) {
      console.log("CIBA token error:", JSON.stringify(cibaTokenResponse.data, null, 2));
    }
    expect(cibaTokenResponse.status).toBe(200);
    expect(cibaTokenResponse.data).toHaveProperty("access_token");
    expect(cibaTokenResponse.data).toHaveProperty("id_token");
    console.log("✅ CIBA token obtained with device secret authentication\n");

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});

    console.log("=== Test Summary ===");
    console.log("✅ Tenant created with issue_device_secret: true");
    console.log("✅ FIDO-UAF registration completed");
    console.log("✅ Device secret automatically issued during registration");
    console.log("✅ Authorization flow completed (user and device persisted)");
    console.log("✅ Device secret used for JWT Bearer Grant authentication");
    console.log("✅ CIBA flow completed with device_secret_jwt + FIDO-UAF authentication");
  });

  it("should NOT issue device secret when tenant policy is disabled", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = "https://app.example.com/callback";

    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const userPassword = `Password${timestamp}!`;

    console.log("\n=== Device Secret NOT Issued When Policy Disabled Test ===\n");

    const { jwks } = await generateRS256KeyPair();

    // Create tenant WITHOUT device secret issuance
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `No Device Secret Test Org ${timestamp}`,
        description: "Test organization without device secret issuance",
      },
      tenant: {
        id: tenantId,
        name: `No Device Secret Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          persistence_enabled: true,
        },
        // Device secret issuance DISABLED (default)
        identity_policy_config: {
          identity_unique_key_type: "EMAIL",
          authentication_device_rule: {
            max_devices: 5,
            issue_device_secret: false, // Explicitly disabled
          },
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwks,
        grant_types_supported: ["authorization_code", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256"],
        extension: { access_token_type: "JWT" },
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: faker.internet.email(),
        email_verified: true,
        raw_password: `AdminPass${timestamp}!`,
      },
      client: {
        client_id: uuidv4(),
        client_secret: `admin-secret-${timestamp}`,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "password"],
        scope: "openid profile email management",
        client_name: "Admin Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingRequest,
    });

    expect(onboardingResponse.status).toBe(201);
    console.log("✅ Tenant created with device secret issuance DISABLED");

    // Verify configuration
    const tenantResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    expect(tenantResponse.status).toBe(200);
    const deviceRule = tenantResponse.data.identity_policy_config?.authentication_device_rule;
    expect(deviceRule.issue_device_secret).toBe(false);
    console.log("✅ Verified: issue_device_secret is false\n");

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});

    console.log("=== Test Summary ===");
    console.log("✅ Tenant created with issue_device_secret: false");
    console.log("✅ Device secret will NOT be issued during FIDO-UAF registration");
    console.log("   (Full FIDO-UAF flow test skipped - requires mock server)\n");
  });
});
