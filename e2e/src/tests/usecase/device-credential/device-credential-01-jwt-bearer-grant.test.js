import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, postWithJson } from "../../../lib/http";
import { requestToken, getUserinfo, getJwks } from "../../../api/oauthClient";
import { generateECP256JWKS, createJwt, generateJti, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { toEpocTime } from "../../../lib/util";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Device Credential Use Case: JWT Bearer Grant with Device Credentials
 *
 * This test verifies that device credentials registered during user creation
 * can be used for JWT Bearer Grant authentication (RFC 7523).
 *
 * Test scenario:
 * 1. Create user with device including device_credentials (symmetric key)
 * 2. Generate JWT signed with device secret (HS256)
 * 3. Request token using JWT Bearer Grant
 * 4. Verify access token is issued
 */
describe("Device Credential Use Case: JWT Bearer Grant with Device Credentials", () => {
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

  it("should issue token using JWT Bearer Grant with device credential (symmetric key)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@device-cred.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    // Device and credential IDs
    const deviceId = uuidv4();
    const credentialId = uuidv4();
    // Generate a secure device secret for HMAC signing (minimum 256 bits for HS256)
    const deviceSecret = crypto.randomBytes(32).toString("base64");

    console.log("\n=== Step 1: Create Organization and Tenant with Device Credential User ===");
    console.log(`  Device ID: ${deviceId}`);
    console.log(`  Credential ID: ${credentialId}`);

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Device Credential Test Org ${timestamp}`,
        description: "Device credential JWT Bearer Grant test organization",
      },
      tenant: {
        id: tenantId,
        name: `Device Credential Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `DEV_CRED_SESSION_${organizationId.substring(0, 8)}`,
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
      user: {
        sub: userId,
        email: orgAdminEmail,
        raw_password: orgAdminPassword,
        username: orgAdminEmail,
        // Register device with device credentials
        authentication_devices: [
          {
            id: deviceId,
            app_name: "Device Credential Test App",
            priority: 1,
            device_credentials: [
              {
                id: credentialId,
                type: "symmetric",
                secret_value: deviceSecret,
                algorithm: "HS256"
              }
            ]
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-device-cred-client-${timestamp}`,
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
        client_name: `Device Credential Test Client ${timestamp}`,
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
    console.log("Onboarding response:", JSON.stringify(onboardingResponse.data, null, 2));

    console.log("\n=== Step 2: Get Admin Token (verify user was created correctly) ===");

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
    console.log("Admin token obtained successfully");

    console.log("\n=== Step 3: Create JWT Bearer Assertion (Device-signed JWT) ===");

    // Create JWT assertion signed with device secret
    // JWT format for device authentication:
    // - iss: "device:{deviceId}" (identifies the device)
    // - sub: "{userId}" (identifies the user)
    // - aud: token endpoint URL (authorization server)
    const tokenEndpoint = `${backendUrl}/${tenantId}/v1/tokens`;
    const issuer = `${backendUrl}/${tenantId}`;

    const assertionPayload = {
      iss: `device:${deviceId}`,
      sub: userId,
      aud: issuer,
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 })
    };

    console.log("JWT Assertion Payload:", JSON.stringify(assertionPayload, null, 2));

    // Sign JWT with device secret (HS256)
    const assertion = createJwt({
      payload: assertionPayload,
      secret: deviceSecret,
      options: {
        algorithm: "HS256"
      }
    });

    console.log("JWT Assertion created (truncated):", assertion.substring(0, 50) + "...");

    console.log("\n=== Step 4: Request Token using JWT Bearer Grant ===");

    const jwtBearerTokenResponse = await requestToken({
      endpoint: tokenEndpoint,
      grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: assertion,
      scope: "openid profile email",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    console.log("JWT Bearer Grant response status:", jwtBearerTokenResponse.status);
    if (jwtBearerTokenResponse.status !== 200) {
      console.log("JWT Bearer Grant error:", JSON.stringify(jwtBearerTokenResponse.data, null, 2));
    }

    expect(jwtBearerTokenResponse.status).toBe(200);
    expect(jwtBearerTokenResponse.data).toHaveProperty("access_token");
    const accessToken = jwtBearerTokenResponse.data.access_token;
    console.log("Access token obtained via JWT Bearer Grant");

    console.log("\n=== Step 5: Verify Access Token JWT Signature ===");

    // Get JWKS from the authorization server
    const jwksEndpoint = `${backendUrl}/${tenantId}/v1/jwks`;
    const jwksResponse = await getJwks({ endpoint: jwksEndpoint });
    expect(jwksResponse.status).toBe(200);
    console.log("JWKS obtained from server");

    // Verify and decode the access token
    const { header, payload, verifyResult } = verifyAndDecodeJwt({
      jwt: accessToken,
      jwks: jwksResponse.data,
    });

    console.log("Access Token Header:", JSON.stringify(header, null, 2));
    console.log("Access Token Payload:", JSON.stringify(payload, null, 2));
    console.log("Signature verification result:", verifyResult);

    expect(verifyResult).toBe(true);
    expect(payload.sub).toBe(userId);
    expect(payload.iss).toBe(`${backendUrl}/${tenantId}`);
    console.log("JWT signature verified successfully");

    console.log("\n=== Step 6: Request Userinfo with Access Token ===");

    const userinfoEndpoint = `${backendUrl}/${tenantId}/v1/userinfo`;
    const userinfoResponse = await getUserinfo({
      endpoint: userinfoEndpoint,
      authorizationHeader: { Authorization: `Bearer ${accessToken}` },
    });

    console.log("Userinfo response status:", userinfoResponse.status);
    if (userinfoResponse.status !== 200) {
      console.log("Userinfo error:", JSON.stringify(userinfoResponse.data, null, 2));
    }

    expect(userinfoResponse.status).toBe(200);
    expect(userinfoResponse.data).toHaveProperty("sub");
    expect(userinfoResponse.data.sub).toBe(userId);
    console.log("Userinfo response:", JSON.stringify(userinfoResponse.data, null, 2));
    console.log("Userinfo request successful - access token is valid");

    console.log("\n=== Step 7: Clean Up Resources ===");

    // Delete client
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${orgClientId}`,
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
    console.log("Verified:");
    console.log("  1. Device credentials registered during user creation");
    console.log("  2. JWT Bearer Grant authentication works with device credentials");
    console.log("  3. Access token JWT signature is valid");
    console.log("  4. Access token can be used to access Userinfo endpoint");
  });

  it("should issue token using JWT Bearer Grant with device_id mapping (sub contains deviceId)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@device-id-mapping.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    // Device and credential IDs
    const deviceId = uuidv4();
    const credentialId = uuidv4();
    const deviceSecret = crypto.randomBytes(32).toString("base64");

    console.log("\n=== [device_id mapping] Step 1: Create Organization and Tenant ===");
    console.log(`  User ID: ${userId}`);
    console.log(`  Device ID: ${deviceId}`);
    console.log(`  subject_claim_mapping: device_id`);

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Device ID Mapping Test Org ${timestamp}`,
        description: "device_id mapping JWT Bearer Grant test",
      },
      tenant: {
        id: tenantId,
        name: `Device ID Mapping Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `DEV_ID_SESSION_${organizationId.substring(0, 8)}`,
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
      user: {
        sub: userId,
        email: orgAdminEmail,
        raw_password: orgAdminPassword,
        username: orgAdminEmail,
        authentication_devices: [
          {
            id: deviceId,
            app_name: "Device ID Mapping Test App",
            priority: 1,
            device_credentials: [
              {
                id: credentialId,
                type: "symmetric",
                secret_value: deviceSecret,
                algorithm: "HS256"
              }
            ]
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-device-id-mapping-client-${timestamp}`,
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
        client_name: `Device ID Mapping Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          available_federations: [
            {
              issuer: "device",
              type: "device",
              subject_claim_mapping: "device_id",  // Use device_id mapping
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

    expect(onboardingResponse.status).toBe(201);
    console.log("Onboarding completed successfully");

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

    console.log("\n=== [device_id mapping] Step 2: Create JWT with deviceId in sub claim ===");

    const tokenEndpoint = `${backendUrl}/${tenantId}/v1/tokens`;
    const issuer = `${backendUrl}/${tenantId}`;

    // Key difference: sub contains deviceId, not userId
    const assertionPayload = {
      iss: `device:${deviceId}`,
      sub: deviceId,  // deviceId in sub claim (not userId)
      aud: issuer,
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 })
    };

    console.log("JWT Assertion Payload (sub=deviceId):", JSON.stringify(assertionPayload, null, 2));

    const assertion = createJwt({
      payload: assertionPayload,
      secret: deviceSecret,
      options: {
        algorithm: "HS256"
      }
    });

    console.log("\n=== [device_id mapping] Step 3: Request Token using JWT Bearer Grant ===");

    const jwtBearerTokenResponse = await requestToken({
      endpoint: tokenEndpoint,
      grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: assertion,
      scope: "openid profile email",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    console.log("JWT Bearer Grant response status:", jwtBearerTokenResponse.status);
    if (jwtBearerTokenResponse.status !== 200) {
      console.log("JWT Bearer Grant error:", JSON.stringify(jwtBearerTokenResponse.data, null, 2));
    }

    expect(jwtBearerTokenResponse.status).toBe(200);
    expect(jwtBearerTokenResponse.data).toHaveProperty("access_token");
    const accessToken = jwtBearerTokenResponse.data.access_token;
    console.log("Access token obtained via JWT Bearer Grant with device_id mapping");

    console.log("\n=== [device_id mapping] Step 4: Verify Access Token ===");

    const jwksEndpoint = `${backendUrl}/${tenantId}/v1/jwks`;
    const jwksResponse = await getJwks({ endpoint: jwksEndpoint });
    expect(jwksResponse.status).toBe(200);

    const { payload, verifyResult } = verifyAndDecodeJwt({
      jwt: accessToken,
      jwks: jwksResponse.data,
    });

    console.log("Access Token Payload:", JSON.stringify(payload, null, 2));
    console.log("Signature verification result:", verifyResult);

    expect(verifyResult).toBe(true);
    // Key verification: The token should be issued for the USER (not the device)
    expect(payload.sub).toBe(userId);
    console.log(`Verified: Token issued for user ${userId} (resolved from device ${deviceId})`);

    console.log("\n=== [device_id mapping] Step 5: Verify Userinfo ===");

    const userinfoEndpoint = `${backendUrl}/${tenantId}/v1/userinfo`;
    const userinfoResponse = await getUserinfo({
      endpoint: userinfoEndpoint,
      authorizationHeader: { Authorization: `Bearer ${accessToken}` },
    });

    expect(userinfoResponse.status).toBe(200);
    expect(userinfoResponse.data.sub).toBe(userId);
    console.log("Userinfo confirms user identity:", userinfoResponse.data.sub);

    console.log("\n=== [device_id mapping] Step 6: Clean Up ===");

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

    console.log("\n=== [device_id mapping] Test Completed Successfully ===");
    console.log("Verified:");
    console.log("  1. JWT sub claim contains deviceId (not userId)");
    console.log("  2. Server resolves user from deviceId via device_id mapping");
    console.log("  3. Access token is issued for the correct user");
    console.log("  4. Userinfo confirms user identity");
  });
});
