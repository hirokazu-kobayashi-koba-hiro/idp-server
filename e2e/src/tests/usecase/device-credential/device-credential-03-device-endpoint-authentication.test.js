import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestBackchannelAuthentications,
  requestToken
} from "../../../api/oauthClient";
import { generateECP256JWKS, createJwt, generateJti } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { toEpocTime } from "../../../lib/util";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Device Credential Use Case: Device Endpoint Authentication
 *
 * This test verifies that the authentication device endpoint can be protected
 * using device credential JWT authentication based on tenant policy.
 *
 * Test scenarios:
 * 1. Device authentication with device_secret_jwt
 * 2. Device authentication with access_token
 * 3. No authentication required when policy is set to "none"
 * 4. Unauthorized access when authentication is required but not provided
 */
describe("Device Credential Use Case: Device Endpoint Authentication", () => {
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

  it("should require device_secret_jwt authentication when policy is set", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@device-auth.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const cibaClientId = uuidv4();
    const cibaClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    const deviceId = uuidv4();
    const credentialId = uuidv4();
    const deviceSecret = crypto.randomBytes(32).toString("base64");

    console.log("\n=== Step 1: Create Tenant with device_secret_jwt Authentication Policy ===");
    console.log(`  Device ID: ${deviceId}`);

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Device Auth Test Org ${timestamp}`,
        description: "Device endpoint authentication test",
      },
      tenant: {
        id: tenantId,
        name: `Device Auth Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `DEV_AUTH_SESSION_${organizationId.substring(0, 8)}`,
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
        // Set device authentication policy to device_secret_jwt
        identity_policy_config: {
          authentication_device_rule: {
            authentication_type: "device_secret_jwt",
            max_devices: 5
          }
        }
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:openid:params:grant-type:ciba"
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        backchannel_token_delivery_modes_supported: ["poll"],
        backchannel_user_code_parameter_supported: true,
        response_modes_supported: ["query"],
        extension: {
          access_token_type: "JWT",
          backchannel_authentication_polling_interval: 5,
          backchannel_authentication_request_expires_in: 300,
          required_backchannel_auth_user_code: false,
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
            app_name: "Device Auth Test App",
            priority: 1,
            device_credentials: [
              {
                id: credentialId,
                type: "jwt_bearer_symmetric",
                type_specific_data: {
                  secret_value: deviceSecret,
                  algorithm: "HS256"
                }
              }
            ]
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-device-auth-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: `Device Auth Test Client ${timestamp}`,
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
      console.log("Onboarding error:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);

    // Get admin token for cleanup
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

    console.log("\n=== Step 2: Create CIBA Client ===");

    const cibaClientRequest = {
      client_id: cibaClientId,
      client_id_alias: `ciba-device-auth-client-${timestamp}`,
      client_secret: cibaClientSecret,
      redirect_uris: [`${backendUrl}/callback`],
      response_types: [],
      grant_types: ["urn:openid:params:grant-type:ciba"],
      scope: "openid profile",
      client_name: `CIBA Device Auth Client ${timestamp}`,
      token_endpoint_auth_method: "client_secret_post",
      application_type: "web",
      backchannel_token_delivery_mode: "poll",
      backchannel_user_code_parameter: true,
      extension: {
        default_ciba_authentication_interaction_type: "authentication-device-notification-no-action",
      },
    };

    const cibaClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      body: cibaClientRequest,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });
    expect(cibaClientResponse.status).toBe(201);
    console.log("CIBA client created");

    console.log("\n=== Step 3: Create CIBA Authentication Request (Transaction) ===");

    const cibaResponse = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      scope: "openid profile",
      loginHint: `device:${deviceId},idp:idp-server`,
      bindingMessage: "Test",
      clientId: cibaClientId,
      clientSecret: cibaClientSecret,
    });

    console.log("CIBA response status:", cibaResponse.status);
    if (cibaResponse.status !== 200) {
      console.log("CIBA error:", JSON.stringify(cibaResponse.data, null, 2));
    }
    expect(cibaResponse.status).toBe(200);
    expect(cibaResponse.data).toHaveProperty("auth_req_id");
    console.log("CIBA transaction created with auth_req_id:", cibaResponse.data.auth_req_id);

    console.log("\n=== Step 4: Access Device Endpoint WITHOUT Authentication (Should Fail) ===");

    const unauthorizedResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?limit=10&offset=0`,
    });

    console.log("Unauthorized response status:", unauthorizedResponse.status);
    expect(unauthorizedResponse.status).toBe(401);
    console.log("Access denied without authentication as expected");

    console.log("\n=== Step 5: Create Device Credential JWT Assertion ===");

    const assertionPayload = {
      iss: `device:${deviceId}`,
      sub: deviceId,
      aud: `${backendUrl}/${tenantId}`,
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 })
    };

    const deviceAssertion = createJwt({
      payload: assertionPayload,
      secret: deviceSecret,
      options: { algorithm: "HS256" }
    });

    console.log("Device JWT assertion created");

    console.log("\n=== Step 6: Access Device Endpoint WITH Device JWT Authentication ===");

    const authenticatedResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?limit=10&offset=0`,
      headers: {
        Authorization: `Bearer ${deviceAssertion}`
      }
    });

    console.log("Authenticated response status:", authenticatedResponse.status);
    if (authenticatedResponse.status !== 200) {
      console.log("Authenticated error:", JSON.stringify(authenticatedResponse.data, null, 2));
    }
    expect(authenticatedResponse.status).toBe(200);
    expect(authenticatedResponse.data).toHaveProperty("list");
    expect(authenticatedResponse.data).toHaveProperty("total_count");
    console.log("Transaction list retrieved successfully");
    console.log("Total transactions:", authenticatedResponse.data.total_count);

    // Verify CIBA transaction is in the list
    if (authenticatedResponse.data.total_count > 0) {
      console.log("Transactions found:", authenticatedResponse.data.list.length);
    }

    console.log("\n=== Step 7: Clean Up Resources ===");

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${cibaClientId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${orgClientId}`,
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
    console.log("Verified:");
    console.log("  1. Device endpoint requires authentication when policy is device_secret_jwt");
    console.log("  2. Access without authentication is denied (401)");
    console.log("  3. Access with device JWT authentication succeeds");
    console.log("  4. Transaction list can be retrieved with proper authentication");
  });

  it("should allow access without authentication when policy is 'none'", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@no-auth.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const cibaClientId = uuidv4();
    const cibaClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    const deviceId = uuidv4();

    console.log("\n=== [no-auth] Step 1: Create Tenant with 'none' Authentication Policy ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `No Auth Test Org ${timestamp}`,
        description: "No device authentication test",
      },
      tenant: {
        id: tenantId,
        name: `No Auth Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `NO_AUTH_SESSION_${organizationId.substring(0, 8)}`,
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
        // Set device authentication policy to none (default)
        identity_policy_config: {
          authentication_device_rule: {
            authentication_type: "none",
            max_devices: 5
          }
        }
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:openid:params:grant-type:ciba"
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        backchannel_token_delivery_modes_supported: ["poll"],
        backchannel_user_code_parameter_supported: true,
        response_modes_supported: ["query"],
        extension: {
          access_token_type: "JWT",
          backchannel_authentication_polling_interval: 5,
          backchannel_authentication_request_expires_in: 300,
          required_backchannel_auth_user_code: false,
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
            app_name: "No Auth Test App",
            priority: 1
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-no-auth-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: `No Auth Test Client ${timestamp}`,
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

    // Get admin token for cleanup
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

    console.log("\n=== [no-auth] Step 2: Create CIBA Client ===");

    const cibaClientRequest = {
      client_id: cibaClientId,
      client_id_alias: `ciba-no-auth-client-${timestamp}`,
      client_secret: cibaClientSecret,
      redirect_uris: [`${backendUrl}/callback`],
      response_types: [],
      grant_types: ["urn:openid:params:grant-type:ciba"],
      scope: "openid profile",
      client_name: `CIBA No Auth Client ${timestamp}`,
      token_endpoint_auth_method: "client_secret_post",
      application_type: "web",
      backchannel_token_delivery_mode: "poll",
      backchannel_user_code_parameter: true,
      extension: {
        default_ciba_authentication_interaction_type: "authentication-device-notification-no-action",
      },
    };

    const cibaClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      body: cibaClientRequest,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });
    expect(cibaClientResponse.status).toBe(201);

    console.log("\n=== [no-auth] Step 3: Create CIBA Authentication Request ===");

    const cibaResponse = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      scope: "openid profile",
      loginHint: `device:${deviceId},idp:idp-server`,
      bindingMessage: "Test",
      clientId: cibaClientId,
      clientSecret: cibaClientSecret,
    });

    console.log("CIBA response status:", cibaResponse.status);
    if (cibaResponse.status !== 200) {
      console.log("CIBA error:", JSON.stringify(cibaResponse.data, null, 2));
    }
    expect(cibaResponse.status).toBe(200);
    console.log("CIBA transaction created");

    console.log("\n=== [no-auth] Step 4: Access Device Endpoint WITHOUT Authentication (Should Succeed) ===");

    const noAuthResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?limit=10&offset=0`,
    });

    console.log("No-auth response status:", noAuthResponse.status);
    if (noAuthResponse.status !== 200) {
      console.log("No-auth error:", JSON.stringify(noAuthResponse.data, null, 2));
    }
    expect(noAuthResponse.status).toBe(200);
    expect(noAuthResponse.data).toHaveProperty("list");
    expect(noAuthResponse.data).toHaveProperty("total_count");
    console.log("Access without authentication succeeded as expected");
    console.log("Total transactions:", noAuthResponse.data.total_count);

    console.log("\n=== [no-auth] Step 5: Clean Up ===");

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${cibaClientId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${orgClientId}`,
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

    console.log("\n=== [no-auth] Test Completed Successfully ===");
    console.log("Verified:");
    console.log("  1. Device endpoint allows access without authentication when policy is 'none'");
    console.log("  2. Transaction list can be retrieved without Authorization header");
  });

  it("should fail authentication when device has no credentials registered", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@no-cred.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    const deviceId = uuidv4();
    // Generate a secret that is NOT registered on the server
    const unregisteredSecret = crypto.randomBytes(32).toString("base64");

    console.log("\n=== [no-cred] Step 1: Create Tenant with device_secret_jwt Policy (Device WITHOUT Credentials) ===");
    console.log(`  Device ID: ${deviceId}`);

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `No Cred Test Org ${timestamp}`,
        description: "Device without credentials test",
      },
      tenant: {
        id: tenantId,
        name: `No Cred Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `NO_CRED_SESSION_${organizationId.substring(0, 8)}`,
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
        // Set device authentication policy to device_secret_jwt
        identity_policy_config: {
          authentication_device_rule: {
            authentication_type: "device_secret_jwt",
            max_devices: 5
          }
        }
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:openid:params:grant-type:ciba"
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        backchannel_token_delivery_modes_supported: ["poll"],
        backchannel_user_code_parameter_supported: true,
        response_modes_supported: ["query"],
        extension: {
          access_token_type: "JWT",
          backchannel_authentication_polling_interval: 5,
          backchannel_authentication_request_expires_in: 300,
          required_backchannel_auth_user_code: false,
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
            app_name: "No Cred Test App",
            priority: 1,
            // NO device_credentials - this is the key difference
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-no-cred-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: `No Cred Test Client ${timestamp}`,
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
      console.log("Onboarding error:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);

    // Get admin token for cleanup
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

    console.log("\n=== [no-cred] Step 2: Access WITHOUT Authentication (Should Fail - 401) ===");

    const unauthorizedResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?limit=10&offset=0`,
    });

    console.log("Unauthorized response status:", unauthorizedResponse.status);
    expect(unauthorizedResponse.status).toBe(401);
    console.log("Access denied without authentication as expected");

    console.log("\n=== [no-cred] Step 3: Create JWT with Unregistered Secret ===");

    const assertionPayload = {
      iss: `device:${deviceId}`,
      sub: deviceId,
      aud: `${backendUrl}/${tenantId}`,
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 })
    };

    const fakeDeviceAssertion = createJwt({
      payload: assertionPayload,
      secret: unregisteredSecret,
      options: { algorithm: "HS256" }
    });

    console.log("JWT created with unregistered secret");

    console.log("\n=== [no-cred] Step 4: Access WITH JWT (Unregistered Secret - Should Fail - 401) ===");

    const failedAuthResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?limit=10&offset=0`,
      headers: {
        Authorization: `Bearer ${fakeDeviceAssertion}`
      }
    });

    console.log("Failed auth response status:", failedAuthResponse.status);
    if (failedAuthResponse.status !== 401) {
      console.log("Unexpected success response:", JSON.stringify(failedAuthResponse.data, null, 2));
    }
    expect(failedAuthResponse.status).toBe(401);
    console.log("Authentication failed as expected - no credential registered for device");

    console.log("\n=== [no-cred] Step 5: Clean Up ===");

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${orgClientId}`,
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

    console.log("\n=== [no-cred] Test Completed Successfully ===");
    console.log("Verified:");
    console.log("  1. Device endpoint denies access without authentication (401)");
    console.log("  2. Device endpoint denies access with JWT when no credential is registered (401)");
    console.log("  3. Server properly rejects authentication attempts for devices without credentials");
  });

  it("should require access_token authentication when policy is set", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@access-token.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const cibaClientId = uuidv4();
    const cibaClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    const deviceId = uuidv4();
    const credentialId = uuidv4();
    // Generate a secure device secret for HMAC signing
    const deviceSecret = crypto.randomBytes(32).toString("base64");

    console.log("\n=== [access-token] Step 1: Create Tenant with access_token Authentication Policy ===");
    console.log(`  Device ID: ${deviceId}`);
    console.log(`  Credential ID: ${credentialId}`);

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Access Token Auth Test Org ${timestamp}`,
        description: "Access token device authentication test",
      },
      tenant: {
        id: tenantId,
        name: `Access Token Auth Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `ACCESS_TOKEN_SESSION_${organizationId.substring(0, 8)}`,
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
        // Set device authentication policy to access_token
        identity_policy_config: {
          authentication_device_rule: {
            authentication_type: "access_token",
            max_devices: 5
          }
        }
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:openid:params:grant-type:ciba",
          "urn:ietf:params:oauth:grant-type:jwt-bearer"
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        backchannel_token_delivery_modes_supported: ["poll"],
        backchannel_user_code_parameter_supported: true,
        response_modes_supported: ["query"],
        extension: {
          access_token_type: "JWT",
          backchannel_authentication_polling_interval: 5,
          backchannel_authentication_request_expires_in: 300,
          required_backchannel_auth_user_code: false,
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
            app_name: "Access Token Auth Test App",
            priority: 1,
            // Device credentials for JWT Bearer Grant
            device_credentials: [
              {
                id: credentialId,
                type: "jwt_bearer_symmetric",
                type_specific_data: {
                  secret_value: deviceSecret,
                  algorithm: "HS256"
                }
              }
            ]
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-access-token-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer"
        ],
        scope: "openid profile email management",
        client_name: `Access Token Auth Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          available_federations: [
            {
              issuer: "device",
              type: "device",
              subject_claim_mapping: "sub",
              jwt_bearer_grant_enabled: true
            }
          ]
        }
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: onboardingRequest,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("Onboarding response status:", onboardingResponse.status);
    if (onboardingResponse.status !== 201) {
      console.log("Onboarding error:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);

    // Get admin token for cleanup and CIBA client creation
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
    const adminAccessToken = adminTokenResponse.data.access_token;

    console.log("\n=== [access-token] Step 2: Create JWT Bearer Assertion and Get Device Access Token ===");

    const issuer = `${backendUrl}/${tenantId}`;
    const assertionPayload = {
      iss: `device:${deviceId}`,
      sub: userId,
      aud: issuer,
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 })
    };

    const assertion = createJwt({
      payload: assertionPayload,
      secret: deviceSecret,
      options: { algorithm: "HS256" }
    });

    console.log("JWT Bearer Assertion created");

    const jwtBearerTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: assertion,
      scope: "openid profile",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    console.log("JWT Bearer Grant response status:", jwtBearerTokenResponse.status);
    if (jwtBearerTokenResponse.status !== 200) {
      console.log("JWT Bearer Grant error:", JSON.stringify(jwtBearerTokenResponse.data, null, 2));
    }
    expect(jwtBearerTokenResponse.status).toBe(200);
    expect(jwtBearerTokenResponse.data).toHaveProperty("access_token");
    const deviceAccessToken = jwtBearerTokenResponse.data.access_token;
    console.log("Device access token obtained via JWT Bearer Grant");

    console.log("\n=== [access-token] Step 3: Create CIBA Client ===");

    const cibaClientRequest = {
      client_id: cibaClientId,
      client_id_alias: `ciba-access-token-client-${timestamp}`,
      client_secret: cibaClientSecret,
      redirect_uris: [`${backendUrl}/callback`],
      response_types: [],
      grant_types: ["urn:openid:params:grant-type:ciba"],
      scope: "openid profile",
      client_name: `CIBA Access Token Client ${timestamp}`,
      token_endpoint_auth_method: "client_secret_post",
      application_type: "web",
      backchannel_token_delivery_mode: "poll",
      backchannel_user_code_parameter: true,
      extension: {
        default_ciba_authentication_interaction_type: "authentication-device-notification-no-action",
      },
    };

    const cibaClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      body: cibaClientRequest,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    expect(cibaClientResponse.status).toBe(201);
    console.log("CIBA client created");

    console.log("\n=== [access-token] Step 4: Create CIBA Authentication Request (Transaction) ===");

    const cibaResponse = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      scope: "openid profile",
      loginHint: `device:${deviceId},idp:idp-server`,
      bindingMessage: "Test",
      clientId: cibaClientId,
      clientSecret: cibaClientSecret,
    });

    console.log("CIBA response status:", cibaResponse.status);
    if (cibaResponse.status !== 200) {
      console.log("CIBA error:", JSON.stringify(cibaResponse.data, null, 2));
    }
    expect(cibaResponse.status).toBe(200);
    expect(cibaResponse.data).toHaveProperty("auth_req_id");
    console.log("CIBA transaction created");

    console.log("\n=== [access-token] Step 5: Access Device Endpoint WITHOUT Authentication (Should Fail) ===");

    const unauthorizedResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?limit=10&offset=0`,
    });

    console.log("Unauthorized response status:", unauthorizedResponse.status);
    expect(unauthorizedResponse.status).toBe(401);
    console.log("Access denied without authentication as expected");

    console.log("\n=== [access-token] Step 6: Access Device Endpoint WITH Device Access Token (JWT Bearer Grant) ===");

    const authenticatedResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?limit=10&offset=0`,
      headers: {
        Authorization: `Bearer ${deviceAccessToken}`
      }
    });

    console.log("Authenticated response status:", authenticatedResponse.status);
    if (authenticatedResponse.status !== 200) {
      console.log("Authenticated error:", JSON.stringify(authenticatedResponse.data, null, 2));
    }
    expect(authenticatedResponse.status).toBe(200);
    expect(authenticatedResponse.data).toHaveProperty("list");
    expect(authenticatedResponse.data).toHaveProperty("total_count");
    console.log("Transaction list retrieved successfully with device access token");
    console.log("Total transactions:", authenticatedResponse.data.total_count);

    console.log("\n=== [access-token] Step 7: Clean Up Resources ===");

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${cibaClientId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${orgClientId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("\n=== [access-token] Test Completed Successfully ===");
    console.log("Verified:");
    console.log("  1. Device can obtain access token via JWT Bearer Grant");
    console.log("  2. Device endpoint requires authentication when policy is access_token");
    console.log("  3. Access without authentication is denied (401)");
    console.log("  4. Access with device's access token (from JWT Bearer Grant) succeeds");
  });

  it("should deny access when using another user's access token", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userAId = uuidv4();
    const userBId = uuidv4();
    const orgClientId = uuidv4();
    const userAEmail = `userA-${timestamp}@other-user.example.com`;
    const userAPassword = `TestPassA${timestamp}!`;
    const userBEmail = `userB-${timestamp}@other-user.example.com`;
    const userBPassword = `TestPassB${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    const deviceId = uuidv4();
    const credentialId = uuidv4();
    const deviceSecret = crypto.randomBytes(32).toString("base64");

    console.log("\n=== [other-user] Step 1: Create Tenant with Two Users (Only User A owns the device) ===");
    console.log(`  Device ID: ${deviceId}`);
    console.log(`  User A ID: ${userAId} (owns device)`);
    console.log(`  User B ID: ${userBId} (does NOT own device)`);

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Other User Token Test Org ${timestamp}`,
        description: "Test access denial with another user's token",
      },
      tenant: {
        id: tenantId,
        name: `Other User Token Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `OTHER_USER_SESSION_${organizationId.substring(0, 8)}`,
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
        identity_policy_config: {
          authentication_device_rule: {
            authentication_type: "access_token",
            max_devices: 5
          }
        }
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer"
        ],
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
      // User A: owns the device
      user: {
        sub: userAId,
        email: userAEmail,
        raw_password: userAPassword,
        username: userAEmail,
        authentication_devices: [
          {
            id: deviceId,
            app_name: "User A Device",
            priority: 1,
            device_credentials: [
              {
                id: credentialId,
                type: "jwt_bearer_symmetric",
                type_specific_data: {
                  secret_value: deviceSecret,
                  algorithm: "HS256"
                }
              }
            ]
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-other-user-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer"
        ],
        scope: "openid profile email management",
        client_name: `Other User Token Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          available_federations: [
            {
              issuer: "device",
              type: "device",
              subject_claim_mapping: "sub",
              jwt_bearer_grant_enabled: true
            }
          ]
        }
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: onboardingRequest,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("Onboarding response status:", onboardingResponse.status);
    if (onboardingResponse.status !== 201) {
      console.log("Onboarding error:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);

    // Get User A's token for admin operations
    const userATokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userAEmail,
      password: userAPassword,
      scope: "openid profile email management",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });
    expect(userATokenResponse.status).toBe(200);
    const userAAdminToken = userATokenResponse.data.access_token;

    console.log("\n=== [other-user] Step 2: Create User B (without device) ===");

    const createUserBRequest = {
      sub: userBId,
      email: userBEmail,
      raw_password: userBPassword,
      username: userBEmail,
      provider_id: "idp-server",
      // User B has NO authentication_devices
    };

    const createUserBResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      body: createUserBRequest,
      headers: { Authorization: `Bearer ${userAAdminToken}` },
    });

    console.log("Create User B response status:", createUserBResponse.status);
    if (createUserBResponse.status !== 201) {
      console.log("Create User B error:", JSON.stringify(createUserBResponse.data, null, 2));
    }
    expect(createUserBResponse.status).toBe(201);
    console.log("User B created successfully");

    console.log("\n=== [other-user] Step 3: Get User A's Device Access Token (via JWT Bearer Grant) ===");

    const issuer = `${backendUrl}/${tenantId}`;
    const assertionPayload = {
      iss: `device:${deviceId}`,
      sub: userAId,
      aud: issuer,
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 })
    };

    const assertion = createJwt({
      payload: assertionPayload,
      secret: deviceSecret,
      options: { algorithm: "HS256" }
    });

    const userADeviceTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: assertion,
      scope: "openid profile",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    expect(userADeviceTokenResponse.status).toBe(200);
    const userADeviceToken = userADeviceTokenResponse.data.access_token;
    console.log("User A's device access token obtained");

    console.log("\n=== [other-user] Step 4: Get User B's Access Token (via password grant) ===");

    const userBTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userBEmail,
      password: userBPassword,
      scope: "openid profile",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    expect(userBTokenResponse.status).toBe(200);
    const userBToken = userBTokenResponse.data.access_token;
    console.log("User B's access token obtained");

    console.log("\n=== [other-user] Step 5: User A accesses their own device (Should Succeed) ===");

    const userAAccessResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?limit=10&offset=0`,
      headers: {
        Authorization: `Bearer ${userADeviceToken}`
      }
    });

    console.log("User A access response status:", userAAccessResponse.status);
    expect(userAAccessResponse.status).toBe(200);
    console.log("User A can access their own device - OK");

    console.log("\n=== [other-user] Step 6: User B tries to access User A's device (Should Fail - 401) ===");

    const userBAccessResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?limit=10&offset=0`,
      headers: {
        Authorization: `Bearer ${userBToken}`
      }
    });

    console.log("User B access response status:", userBAccessResponse.status);
    if (userBAccessResponse.status !== 401) {
      console.log("Unexpected response:", JSON.stringify(userBAccessResponse.data, null, 2));
    }
    expect(userBAccessResponse.status).toBe(401);
    console.log("User B denied access to User A's device - Security check passed!");

    console.log("\n=== [other-user] Step 7: Clean Up Resources ===");

    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${orgClientId}`,
      headers: { Authorization: `Bearer ${userAAdminToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${userAAdminToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("\n=== [other-user] Test Completed Successfully ===");
    console.log("Verified:");
    console.log("  1. User A (device owner) can access their device with their token");
    console.log("  2. User B (non-owner) is denied access with their token (401)");
    console.log("  3. Access token ownership is properly validated");
  });
});
