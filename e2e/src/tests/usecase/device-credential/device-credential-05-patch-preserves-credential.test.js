import { describe, expect, it, beforeAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, get, postWithJson, patchWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateRS256KeyPair, createJwt, generateJti } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { toEpocTime } from "../../../lib/util";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";

/**
 * Regression test for Issue #1412
 *
 * Bug: PATCH /v1/me/authentication-devices/{device-id} drops the device credential.
 *
 * When a device's notification_channel etc. is updated via PATCH,
 * AuthenticationDevice.patchWith() used the short constructor and overwrote
 * credential_type / credential_id / credential_payload / credential_metadata with null.
 * As a result, the device_secret (stored in credential_payload) was lost and
 * device_secret_jwt authentication / JWT Bearer Grant stopped working.
 *
 * This test reproduces the scenario end-to-end:
 *   1. Register a FIDO-UAF device with device_secret issuance (credential attached)
 *   2. Confirm credential fields are exposed in Userinfo (type / id / metadata)
 *   3. Confirm JWT Bearer Grant works with the issued secret
 *   4. PATCH the device notification_channel via /v1/me
 *   5. Confirm credential fields SURVIVE the PATCH (the actual bug)
 *   6. Confirm JWT Bearer Grant STILL works after PATCH (credential_payload survived)
 *
 * Before the fix, steps 5 and 6 fail (credential vanishes, JWT Bearer Grant returns error).
 *
 * Note: Requires mock FIDO-UAF server at http://host.docker.internal:4000
 */
describe("Device Credential: PATCH must preserve device credential (Issue #1412)", () => {
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

  it("should preserve credential fields when device is patched via /v1/me", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = "https://app.example.com/callback";

    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const userPassword = `Password${timestamp}!`;

    console.log("\n=== Issue #1412: PATCH must preserve device credential ===\n");

    const { jwks } = await generateRS256KeyPair();

    // Step 1: Create tenant with device secret issuance enabled
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Patch Credential Test Org ${timestamp}`,
        description: "Test organization for Issue #1412",
      },
      tenant: {
        id: tenantId,
        name: `Patch Credential Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          persistence_enabled: true,
        },
        identity_policy_config: {
          identity_unique_key_type: "EMAIL",
          authentication_device_rule: {
            max_devices: 10,
            required_identity_verification: false,
            authentication_type: "device_secret_jwt",
            issue_device_secret: true,
            device_secret_algorithm: "HS256",
            device_secret_expires_in_seconds: 31536000,
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
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management", "claims:authentication_devices"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        claims_supported: ["sub", "name", "email", "email_verified", "authentication_devices"],
        extension: {
          access_token_type: "JWT",
          access_token_duration: 3600,
          id_token_duration: 3600,
          refresh_token_duration: 86400,
          id_token_strict_mode: false,
          custom_claims_scope_mapping: true,
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
        client_id_alias: `admin-client-patch-credential-${timestamp}`,
        client_secret: `admin-secret-${timestamp}`,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management claims:authentication_devices",
        client_name: "Admin Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingRequest,
    });
    if (onboardingResponse.status !== 201) {
      console.log("Onboarding error:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);

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
    console.log("✅ Tenant created with device secret issuance enabled");

    // Step 2: Password authentication config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { type: "internal", description: "Password authentication" },
        interactions: {
          "password-authentication": {
            request: {
              schema: {
                type: "object",
                properties: { username: { type: "string" }, password: { type: "string" } },
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

    // Step 3: FIDO-UAF authentication config (mock server)
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "fido-uaf",
        attributes: {},
        metadata: { type: "external", description: "FIDO-UAF authentication" },
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
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] },
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
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] },
          },
        },
      },
    });

    // Step 4: OAuth authentication policy
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_then_fido_uaf",
            priority: 10,
            conditions: { scopes: ["openid"] },
            available_methods: ["password", "fido-uaf"],
            success_conditions: {
              any_of: [[{ path: "$.initial-registration.success_count", type: "integer", operation: "gte", value: 1 }]],
            },
          },
        ],
      },
    });

    // Step 5: Test client with JWT Bearer Grant
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "Patch Credential Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password", "urn:ietf:params:oauth:grant-type:jwt-bearer"],
        response_types: ["code"],
        scope: "openid profile email claims:authentication_devices",
        token_endpoint_auth_method: "client_secret_post",
        extension: {
          available_federations: [
            {
              issuer: "device",
              type: "device",
              subject_claim_mapping: "sub",
              jwt_bearer_grant_enabled: true,
            },
          ],
        },
      },
    });
    console.log("✅ Auth configs, policy and client created");

    // Step 6: Authorization flow + user registration
    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email claims:authentication_devices",
      state: `state_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });
    expect(authorizeResponse.status).toBe(302);
    const authId = new URL(authorizeResponse.headers.location, backendUrl).searchParams.get("id");

    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: userEmail, password: userPassword, name: userName },
    });
    expect(registrationResponse.status).toBe(200);
    const userId = registrationResponse.data.user?.sub;

    // Step 7: FIDO-UAF registration → device secret issued
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration-challenge`,
      body: { app_name: "Patch Test App", platform: "iOS", os: "iOS 17", model: "iPhone 15" },
    });
    expect(challengeResponse.status).toBe(200);

    const registrationCompleteResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration`,
      body: {
        uafResponse: challengeResponse.data.uafRequest
          ? [{ assertionScheme: "UAFV1TLV", assertion: "mock_assertion_data" }]
          : [],
      },
    });
    expect(registrationCompleteResponse.status).toBe(200);

    const deviceId = registrationCompleteResponse.data.device_id;
    const deviceSecret = registrationCompleteResponse.data.device_secret;
    const deviceSecretJwtIssuer = registrationCompleteResponse.data.device_secret_jwt_issuer;
    expect(deviceId).toBeDefined();
    expect(deviceSecret).toBeDefined();
    console.log(`✅ Device registered with secret: device_id=${deviceId}`);

    // Step 8: Complete authorization → exchange code → user access token (has claims:authentication_devices)
    const authorizeCompleteResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });
    expect(authorizeCompleteResponse.status).toBe(200);
    const authCode = new URL(authorizeCompleteResponse.data.redirect_uri).searchParams.get("code");

    const tokenExchangeResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: authCode,
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(tokenExchangeResponse.status).toBe(200);
    const userAccessToken = tokenExchangeResponse.data.access_token;
    console.log("✅ User access token obtained (scope includes claims:authentication_devices)");

    // Helper: read the single registered device from Userinfo
    const getDevice = async () => {
      const userinfoResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: { Authorization: `Bearer ${userAccessToken}` },
      });
      expect(userinfoResponse.status).toBe(200);
      expect(Array.isArray(userinfoResponse.data.authentication_devices)).toBe(true);
      const device = userinfoResponse.data.authentication_devices.find((d) => d.id === deviceId);
      expect(device).toBeDefined();
      return device;
    };

    // Helper: JWT Bearer Grant using the issued device secret
    const jwtBearerGrant = async () => {
      const assertion = createJwt({
        payload: {
          iss: deviceSecretJwtIssuer,
          sub: userId,
          aud: `${backendUrl}/${tenantId}`,
          jti: generateJti(),
          exp: toEpocTime({ adjusted: 3600 }),
          iat: toEpocTime({ adjusted: 0 }),
        },
        secret: deviceSecret,
        options: { algorithm: "HS256" },
      });
      return await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion: assertion,
        scope: "openid profile email claims:authentication_devices",
        clientId: clientId,
        clientSecret: clientSecret,
      });
    };

    // Step 9: BEFORE PATCH - credential fields are present
    const deviceBefore = await getDevice();
    console.log("Device before patch:", JSON.stringify(deviceBefore, null, 2));
    expect(deviceBefore.credential_type).toBe("jwt_bearer_symmetric");
    expect(deviceBefore.credential_id).toBeTruthy();
    expect(deviceBefore.credential_metadata).toBeTruthy();
    // credential_payload must never be exposed (security)
    expect(deviceBefore).not.toHaveProperty("credential_payload");
    console.log("✅ BEFORE patch: credential_type / credential_id / credential_metadata present");

    // Step 10: BEFORE PATCH - JWT Bearer Grant works (sanity)
    const jwtBearerBefore = await jwtBearerGrant();
    expect(jwtBearerBefore.status).toBe(200);
    console.log("✅ BEFORE patch: JWT Bearer Grant succeeded");

    // Step 11: PATCH device notification_channel via /v1/me
    const newNotificationToken = `fcm-token-${timestamp}`;
    const patchResponse = await patchWithJson({
      url: `${backendUrl}/${tenantId}/v1/me/authentication-devices/${deviceId}`,
      headers: { Authorization: `Bearer ${userAccessToken}` },
      body: { notification_channel: "fcm", notification_token: newNotificationToken },
    });
    console.log("PATCH response:", patchResponse.status);
    if (patchResponse.status !== 200) {
      console.log("PATCH error:", JSON.stringify(patchResponse.data, null, 2));
    }
    expect(patchResponse.status).toBe(200);
    console.log("✅ Device patched (notification_channel → fcm)");

    // Step 12: AFTER PATCH - patch applied AND credential fields survived (Issue #1412)
    const deviceAfter = await getDevice();
    console.log("Device after patch:", JSON.stringify(deviceAfter, null, 2));

    // The PATCH itself must have taken effect
    expect(deviceAfter.notification_channel).toBe("fcm");
    expect(deviceAfter.notification_token).toBe(newNotificationToken);

    // The actual bug: credential fields must NOT be wiped by the PATCH
    expect(deviceAfter.credential_type).toBe("jwt_bearer_symmetric");
    expect(deviceAfter.credential_id).toBe(deviceBefore.credential_id);
    expect(deviceAfter.credential_metadata).toEqual(deviceBefore.credential_metadata);
    console.log("✅ AFTER patch: credential fields preserved");

    // Step 13: AFTER PATCH - JWT Bearer Grant still works (credential_payload / device_secret survived)
    const jwtBearerAfter = await jwtBearerGrant();
    if (jwtBearerAfter.status !== 200) {
      console.log("JWT Bearer Grant after patch error:", JSON.stringify(jwtBearerAfter.data, null, 2));
    }
    expect(jwtBearerAfter.status).toBe(200);
    expect(jwtBearerAfter.data).toHaveProperty("access_token");
    console.log("✅ AFTER patch: JWT Bearer Grant still succeeded (device_secret preserved)");

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});

    console.log("\n=== Test Summary ===");
    console.log("✅ Device credential survives PATCH /v1/me/authentication-devices/{id} (Issue #1412)");
  });
});
