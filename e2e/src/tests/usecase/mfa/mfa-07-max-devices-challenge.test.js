import { describe, expect, it, beforeAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";
import {
  convertNextAction,
  convertToAuthorizationResponse,
} from "../../../lib/util";
import { generateRandomUser, generateValidCredentialFromChallenge } from "../../../lib/fido/fido2";

/**
 * FIDO2 max_devices check at challenge stage
 *
 * Issue #1372: Verify that max_devices limit is checked at the
 * fido2-registration-challenge stage (before browser key generation),
 * not only at the fido2-registration stage (after key generation).
 *
 * This prevents orphaned keys in the browser/authenticator when the
 * server rejects the registration at completion time.
 */
describe("Use Case: FIDO2 max_devices check at registration-challenge", () => {
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

  it("should reject fido2-registration-challenge when max_devices is reached", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";
    const userEmail = `fido2-limit-${timestamp}@example.com`;
    const userPassword = `FidoLimit${timestamp}!`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Setup: Create tenant with max_devices=1 ===");

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `FIDO2 MaxDevices Test ${timestamp}`,
          description: "Test max_devices check at challenge stage",
        },
        tenant: {
          id: tenantId,
          name: `FIDO2 MaxDevices Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          tenant_type: "ORGANIZER",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
            authentication_device_rule: {
              max_devices: 1,
              required_identity_verification: false,
              authentication_type: "none",
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
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: userEmail,
          email_verified: true,
          raw_password: userPassword,
          authentication_devices: [
            {
              id: uuidv4(),
              app_name: "Existing Device",
            },
          ],
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management",
          client_name: "FIDO2 MaxDevices Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);
    console.log("Tenant created with max_devices=1, user has 1 device");

    // Get admin token and setup auth configs
    const adminToken = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "management",
      clientId,
      clientSecret,
    });
    expect(adminToken.status).toBe(200);
    const mgmtToken = adminToken.data.access_token;

    // Create password auth config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { type: "password" },
        interactions: {
          "password-authentication": {
            request: {
              schema: {
                type: "object",
                properties: { username: { type: "string" }, password: { type: "string" } },
                required: ["username", "password"],
              },
            },
            pre_hook: {},
            execution: { function: "password_verification" },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.user_id", to: "user_id" },
                { from: "$.error", to: "error" },
              ],
            },
          },
        },
      },
    });

    // Create FIDO2 auth config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(),
        type: "fido2",
        attributes: {},
        metadata: { type: "fido2" },
        interactions: {
          "fido2-registration-challenge": {
            execution: { function: "webauthn_registration_challenge" },
            response: {
              body_mapping_rules: [{ from: "$.result", to: "*" }],
            },
          },
          "fido2-registration": {
            execution: { function: "webauthn_registration" },
            response: {
              body_mapping_rules: [
                { from: "$.result.device_id", to: "device_id" },
                { from: "$.result.credential_id", to: "credential_id" },
              ],
            },
          },
        },
      },
    });

    // Create auth policy
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(),
        priority: 100,
        description: "default",
        conditions: { scopes: ["openid"] },
        available_methods: ["password", "fido2"],
        success_conditions: {
          any_of: [
            [{ path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
            [{ path: "$.fido2-registration.success_count", type: "integer", operation: "gte", value: 1 }],
          ],
        },
      },
    });

    console.log("\n=== Step 1: Login with password ===");

    const cookieJar = {};
    const authResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations?response_type=code&client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&scope=openid+profile+email&state=test-${timestamp}&prompt=login`,
      body: {},
    });

    // Use direct flow: get auth, password auth, then try fido2-registration-challenge
    const { get: httpGet } = await import("../../../lib/http/index.js");
    const authResp = await httpGet({
      url: `${backendUrl}/${tenantId}/v1/authorizations?response_type=code&client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&scope=openid+profile+email&state=test-${timestamp}&prompt=login`,
    });

    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");
    console.log("Authorization ID:", authId);

    // Password authentication
    const pwAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
      body: { username: userEmail, password: userPassword },
    });
    expect(pwAuthResponse.status).toBe(200);
    console.log("Password authentication: OK");

    console.log("\n=== Step 2: Try fido2-registration-challenge (should fail - max_devices reached) ===");

    const discoverableUser = generateRandomUser();
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido2-registration-challenge`,
      body: {
        username: discoverableUser.username,
        displayName: discoverableUser.displayName,
        authenticatorSelection: {
          authenticatorAttachment: "platform",
          requireResidentKey: true,
          userVerification: "required",
        },
        attestation: "none",
      },
    });

    console.log("Challenge response status:", challengeResponse.status);
    console.log("Challenge response:", JSON.stringify(challengeResponse.data, null, 2));

    // Before fix: challenge succeeds, registration fails later (orphaned key)
    // After fix: challenge fails with max_devices error
    expect(challengeResponse.status).toBe(400);
    expect(challengeResponse.data.error).toBe("device_limit_exceeded");
    expect(challengeResponse.data.error_description).toContain("Maximum number of devices reached");
    expect(challengeResponse.data.max_devices).toBeDefined();
    expect(challengeResponse.data.current_devices).toBeDefined();

    console.log("✅ Challenge correctly rejected at max_devices limit");
    console.log("   No orphaned key created in browser/authenticator");
  });

  it("should allow fido2-registration-challenge with action:reset even when max_devices is reached", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";
    const userEmail = `fido2-reset-${timestamp}@example.com`;
    const userPassword = `FidoReset${timestamp}!`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Setup: Create tenant with max_devices=1 (reset test) ===");

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `FIDO2 Reset Test ${timestamp}`,
          description: "Test reset action bypasses max_devices check",
        },
        tenant: {
          id: tenantId,
          name: `FIDO2 Reset Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          tenant_type: "ORGANIZER",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
            authentication_device_rule: {
              max_devices: 1,
              required_identity_verification: false,
              authentication_type: "none",
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
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "account"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: userEmail,
          email_verified: true,
          raw_password: userPassword,
          authentication_devices: [
            { id: uuidv4(), app_name: "Existing Device" },
          ],
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management account",
          client_name: "FIDO2 Reset Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);
    console.log("Tenant created with max_devices=1, user has 1 device");

    // Get access token with account scope for MFA endpoint
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid profile email account",
      clientId,
      clientSecret,
    });
    expect(tokenResponse.status).toBe(200);
    const accessToken = tokenResponse.data.access_token;

    // Create auth configs
    const mgmtTokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "management",
      clientId,
      clientSecret,
    });
    const mgmtToken = mgmtTokenResp.data.access_token;

    const fido2Details = {
      rp_id: "local.test",
      allowed_origins: [backendUrl],
      rp_name: "Test Service",
      require_resident_key: true,
      attestation_preference: "none",
      user_presence_required: true,
      authenticator_attachment: "platform",
      user_verification_required: true,
    };

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(), type: "fido2", attributes: {}, metadata: {},
        interactions: {
          "fido2-registration-challenge": {
            execution: { function: "webauthn4j_registration_challenge", details: fido2Details },
            response: { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] },
          },
          "fido2-registration": {
            execution: { function: "webauthn4j_registration", details: fido2Details },
            response: { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] },
          },
        },
      },
    });

    // Create auth policy for oauth flow
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [{
          priority: 100,
          description: "default",
          conditions: { scopes: ["openid"] },
          available_methods: ["password", "fido2"],
          success_conditions: {
            any_of: [
              [{ path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
            ],
          },
        }],
      },
    });

    // Create auth policy for fido2-registration flow (MFA endpoint)
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(),
        flow: "fido2-registration",
        enabled: true,
        policies: [{
          priority: 100,
          description: "fido2 mfa registration",
          conditions: { scopes: ["openid"] },
          available_methods: ["fido2"],
          success_conditions: {
            any_of: [
              [{ path: "$.fido2-registration.success_count", type: "integer", operation: "gte", value: 1 }],
            ],
          },
        }],
      },
    });

    // Create password auth config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" },
        interactions: {
          "password-authentication": {
            request: {
              schema: {
                type: "object",
                properties: { username: { type: "string" }, password: { type: "string" } },
                required: ["username", "password"],
              },
            },
            execution: { function: "password_verification" },
            response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }] },
          },
        },
      },
    });

    console.log("\n=== Step 1: MFA registration with action:reset ===");

    const mfaResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/me/mfa/fido2-registration`,
      body: {
        app_name: "Reset Device",
        platform: "Android",
        os: "Android16",
        model: "Test Device",
        locale: "ja",
        notification_channel: "fcm",
        notification_token: "test-token",
        priority: 1,
        action: "reset",
      },
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    console.log("MFA registration status:", mfaResponse.status);
    console.log("MFA registration:", JSON.stringify(mfaResponse.data, null, 2));
    expect(mfaResponse.status).toBe(200);
    const transactionId = mfaResponse.data.id;

    console.log("\n=== Step 2: fido2-registration-challenge (should succeed despite max_devices) ===");

    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authentications/${transactionId}/fido2-registration-challenge`,
      body: {
        username: userEmail,
        displayName: "Reset User",
        authenticatorSelection: {
          authenticatorAttachment: "platform",
          requireResidentKey: true,
          userVerification: "required",
        },
        attestation: "none",
      },
    });

    console.log("Challenge response status:", challengeResponse.status);
    console.log("Challenge response:", JSON.stringify(challengeResponse.data, null, 2));

    // With action:reset, challenge should succeed even though max_devices is reached
    expect(challengeResponse.status).toBe(200);
    console.log("✅ Challenge succeeded with action:reset (max_devices check skipped)");
  });

  it("should reject fido-uaf-registration-challenge when max_devices is reached", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";
    const userEmail = `uaf-limit-${timestamp}@example.com`;
    const userPassword = `UafLimit${timestamp}!`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Setup: Create tenant with max_devices=1 (FIDO-UAF) ===");

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `FIDO-UAF MaxDevices Test ${timestamp}`,
          description: "Test max_devices check at FIDO-UAF challenge stage",
        },
        tenant: {
          id: tenantId,
          name: `FIDO-UAF MaxDevices Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          tenant_type: "ORGANIZER",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
            authentication_device_rule: {
              max_devices: 1,
              required_identity_verification: false,
              authentication_type: "none",
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
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: userEmail,
          email_verified: true,
          raw_password: userPassword,
          authentication_devices: [
            {
              id: uuidv4(),
              app_name: "Existing UAF Device",
            },
          ],
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management",
          client_name: "FIDO-UAF MaxDevices Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);
    console.log("Tenant created with max_devices=1, user has 1 device");

    const adminToken = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "management",
      clientId,
      clientSecret,
    });
    expect(adminToken.status).toBe(200);
    const mgmtToken = adminToken.data.access_token;

    // Create password auth config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { type: "password" },
        interactions: {
          "password-authentication": {
            request: {
              schema: {
                type: "object",
                properties: { username: { type: "string" }, password: { type: "string" } },
                required: ["username", "password"],
              },
            },
            execution: { function: "password_verification" },
            response: {
              body_mapping_rules: [
                { from: "$.user_id", to: "user_id" },
                { from: "$.error", to: "error" },
              ],
            },
          },
        },
      },
    });

    // Create FIDO-UAF auth config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(),
        type: "fido-uaf",
        attributes: {},
        metadata: { type: "fido-uaf" },
        interactions: {
          "fido-uaf-registration-challenge": {
            execution: { function: "fido_uaf_registration_challenge" },
            response: {
              body_mapping_rules: [{ from: "$.result", to: "*" }],
            },
          },
          "fido-uaf-registration": {
            execution: { function: "fido_uaf_registration" },
            response: {
              body_mapping_rules: [{ from: "$.result.device_id", to: "device_id" }],
            },
          },
        },
      },
    });

    // Create auth policy
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(),
        priority: 100,
        description: "default",
        conditions: { scopes: ["openid"] },
        available_methods: ["password", "fido-uaf"],
        success_conditions: {
          any_of: [
            [{ path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
          ],
        },
      },
    });

    console.log("\n=== Step 1: Login with password ===");

    const { get: httpGet } = await import("../../../lib/http/index.js");
    const authResp = await httpGet({
      url: `${backendUrl}/${tenantId}/v1/authorizations?response_type=code&client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&scope=openid+profile+email&state=uaf-${timestamp}&prompt=login`,
    });
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    const pwAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
      body: { username: userEmail, password: userPassword },
    });
    expect(pwAuthResponse.status).toBe(200);
    console.log("Password authentication: OK");

    console.log("\n=== Step 2: Try fido-uaf-registration-challenge (should fail) ===");

    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration-challenge`,
      body: {},
    });

    console.log("Challenge response status:", challengeResponse.status);
    console.log("Challenge response:", JSON.stringify(challengeResponse.data, null, 2));

    expect(challengeResponse.status).toBe(400);
    expect(challengeResponse.data.error).toBe("device_limit_exceeded");
    expect(challengeResponse.data.error_description).toContain("Maximum number of devices reached");
    expect(challengeResponse.data.max_devices).toBeDefined();
    expect(challengeResponse.data.current_devices).toBeDefined();

    console.log("✅ FIDO-UAF challenge correctly rejected at max_devices limit");
  });

  it("should allow fido-uaf-registration-challenge with action:reset even when max_devices is reached", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";
    const userEmail = `uaf-reset-${timestamp}@example.com`;
    const userPassword = `UafReset${timestamp}!`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Setup: Create tenant with max_devices=1 (FIDO-UAF reset) ===");

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `FIDO-UAF Reset Test ${timestamp}`,
          description: "Test reset action bypasses max_devices for FIDO-UAF",
        },
        tenant: {
          id: tenantId,
          name: `FIDO-UAF Reset Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          tenant_type: "ORGANIZER",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
            authentication_device_rule: {
              max_devices: 1,
              required_identity_verification: false,
              authentication_type: "none",
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
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "account"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: userEmail,
          email_verified: true,
          raw_password: userPassword,
          authentication_devices: [
            { id: uuidv4(), app_name: "Existing UAF Device" },
          ],
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management account",
          client_name: "FIDO-UAF Reset Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid profile email account",
      clientId,
      clientSecret,
    });
    expect(tokenResponse.status).toBe(200);
    const accessToken = tokenResponse.data.access_token;

    const mgmtTokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "management",
      clientId,
      clientSecret,
    });
    const mgmtToken = mgmtTokenResp.data.access_token;

    // Create FIDO-UAF auth config (external type using mockoon)
    const mockApiBaseUrl = process.env.MOCK_API_BASE_URL || "http://host.docker.internal:4000";
    const oauthConfig = {
      type: "password",
      token_endpoint: `${mockApiBaseUrl}/token`,
      client_id: "your-client-id",
      username: "username",
      password: "password",
      scope: "application",
      cache_enabled: true,
      cache_ttl_seconds: 1800,
      cache_buffer_seconds: 10,
    };
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(), type: "fido-uaf", attributes: { type: "external" }, metadata: {},
        interactions: {
          "fido-uaf-registration-challenge": {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/fido-uaf/registration-challenge`,
                method: "POST",
                auth_type: "oauth2",
                oauth_authorization: oauthConfig,
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
            },
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] },
          },
          "fido-uaf-registration": {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/fido-uaf/registration`,
                method: "POST",
                auth_type: "oauth2",
                oauth_authorization: oauthConfig,
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
            },
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] },
          },
        },
      },
    });

    // Create auth policies
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(), flow: "fido-uaf-registration", enabled: true,
        policies: [{
          priority: 100, description: "fido-uaf mfa registration",
          conditions: { scopes: ["openid"] },
          available_methods: ["fido-uaf"],
          success_conditions: { any_of: [
            [{ path: "$.fido-uaf-registration.success_count", type: "integer", operation: "gte", value: 1 }],
          ]},
        }],
      },
    });

    console.log("\n=== Step 1: MFA registration with action:reset ===");

    const mfaResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/me/mfa/fido-uaf-registration`,
      body: {
        app_name: "Reset UAF Device",
        platform: "Android",
        os: "Android16",
        model: "Test Device",
        locale: "ja",
        notification_channel: "fcm",
        notification_token: "test-token",
        priority: 1,
        action: "reset",
      },
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    console.log("MFA registration status:", mfaResponse.status);
    expect(mfaResponse.status).toBe(200);
    const transactionId = mfaResponse.data.id;

    console.log("\n=== Step 2: fido-uaf-registration-challenge (should succeed despite max_devices) ===");

    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authentications/${transactionId}/fido-uaf-registration-challenge`,
      body: {},
    });

    console.log("Challenge response status:", challengeResponse.status);
    console.log("Challenge response:", JSON.stringify(challengeResponse.data, null, 2));

    // With action:reset, challenge should succeed even though max_devices is reached
    expect(challengeResponse.status).toBe(200);
    console.log("✅ FIDO-UAF challenge succeeded with action:reset (max_devices check skipped)");
  });
});
