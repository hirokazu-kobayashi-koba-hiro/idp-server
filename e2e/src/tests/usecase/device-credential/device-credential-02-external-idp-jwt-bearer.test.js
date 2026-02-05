import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, postWithJson } from "../../../lib/http";
import { requestToken, getUserinfo, getJwks } from "../../../api/oauthClient";
import { generateECP256JWKS, generateECP256KeyPair, createJwtWithJwk, createJwtWithNoneSignature, generateJti, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { toEpocTime } from "../../../lib/util";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * External IdP JWT Bearer Grant Test
 *
 * This test verifies JWT Bearer Grant with external IdP federation.
 * The external IdP's JWKS is configured inline in the federation settings.
 *
 * Test scenario:
 * 1. Create tenant with user
 * 2. Configure client with external IdP federation (inline JWKS)
 * 3. Generate JWT signed with external IdP's private key
 * 4. Request token using JWT Bearer Grant
 * 5. Verify access token is issued for the correct user
 */
describe("External IdP JWT Bearer Grant", () => {
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

  it("should issue token using JWT Bearer Grant with external IdP (inline JWKS)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@external-idp.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;

    // Generate JWKS for the authorization server
    const serverJwksContent = await generateECP256JWKS();

    // Generate separate keypair for the external IdP
    const externalIdpJwks = await generateECP256KeyPair();
    const externalIdpIssuer = "https://external-idp.example.com";

    console.log("\n=== Step 1: Create Organization and Tenant ===");
    console.log(`  User ID: ${userId}`);
    console.log(`  External IdP Issuer: ${externalIdpIssuer}`);

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `External IdP Test Org ${timestamp}`,
        description: "External IdP JWT Bearer Grant test organization",
      },
      tenant: {
        id: tenantId,
        name: `External IdP Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `EXT_IDP_SESSION_${organizationId.substring(0, 8)}`,
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
        jwks: serverJwksContent,
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
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-external-idp-client-${timestamp}`,
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
        client_name: `External IdP Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          available_federations: [
            {
              id: "external-idp-federation",
              issuer: externalIdpIssuer,
              type: "oidc",
              sso_provider: "ExternalTestIdP",
              subject_claim_mapping: "sub",
              jwt_bearer_grant_enabled: true,
              // Inline JWKS for external IdP signature verification
              jwks: JSON.stringify(externalIdpJwks.publicJwks)
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
    console.log("Onboarding completed successfully");

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
    console.log("Admin token obtained successfully");

    console.log("\n=== Step 3: Create JWT signed by External IdP ===");

    const tokenEndpoint = `${backendUrl}/${tenantId}/v1/tokens`;
    const audience = `${backendUrl}/${tenantId}`;

    // JWT assertion from external IdP
    const assertionPayload = {
      iss: externalIdpIssuer,  // External IdP issuer
      sub: userId,             // User ID in this system
      aud: audience,           // This authorization server
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 })
    };

    console.log("JWT Assertion Payload:", JSON.stringify(assertionPayload, null, 2));

    // Sign with external IdP's private key (ES256)
    const assertion = createJwtWithJwk({
      payload: assertionPayload,
      privateJwk: externalIdpJwks.privateJwk,
      options: {
        algorithm: "ES256",
        keyId: "signing_key_1"
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
    console.log("Access token obtained via JWT Bearer Grant with external IdP");

    console.log("\n=== Step 5: Verify Access Token ===");

    const jwksEndpoint = `${backendUrl}/${tenantId}/v1/jwks`;
    const jwksResponse = await getJwks({ endpoint: jwksEndpoint });
    expect(jwksResponse.status).toBe(200);

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

    console.log("\n=== Step 6: Request Userinfo ===");

    const userinfoEndpoint = `${backendUrl}/${tenantId}/v1/userinfo`;
    const userinfoResponse = await getUserinfo({
      endpoint: userinfoEndpoint,
      authorizationHeader: { Authorization: `Bearer ${accessToken}` },
    });

    console.log("Userinfo response status:", userinfoResponse.status);
    expect(userinfoResponse.status).toBe(200);
    expect(userinfoResponse.data.sub).toBe(userId);
    console.log("Userinfo response:", JSON.stringify(userinfoResponse.data, null, 2));

    console.log("\n=== Step 7: Clean Up ===");

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
    console.log("  1. External IdP JWKS configured inline in federation");
    console.log("  2. JWT signed by external IdP verified successfully");
    console.log("  3. Access token issued for the correct user");
    console.log("  4. Userinfo confirms user identity");
  });

  it("should reject JWT from untrusted issuer", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@untrusted.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;

    const serverJwksContent = await generateECP256JWKS();
    const trustedIdpJwks = await generateECP256KeyPair();
    const untrustedIdpJwks = await generateECP256KeyPair();

    const trustedIssuer = "https://trusted-idp.example.com";
    const untrustedIssuer = "https://untrusted-idp.example.com";

    console.log("\n=== [Untrusted Issuer] Step 1: Create Organization and Tenant ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Untrusted Issuer Test Org ${timestamp}`,
        description: "Test rejection of untrusted issuer",
      },
      tenant: {
        id: tenantId,
        name: `Untrusted Issuer Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `UNTRUST_SESSION_${organizationId.substring(0, 8)}`,
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
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: serverJwksContent,
        grant_types_supported: [
          "authorization_code",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer"
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
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
      client: {
        client_id: orgClientId,
        client_id_alias: `test-untrusted-issuer-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: [
          "authorization_code",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer"
        ],
        scope: "openid profile email management",
        client_name: `Untrusted Issuer Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          available_federations: [
            {
              id: "trusted-idp",
              issuer: trustedIssuer,  // Only trust this issuer
              type: "oidc",
              subject_claim_mapping: "sub",
              jwt_bearer_grant_enabled: true,
              jwks: JSON.stringify(trustedIdpJwks.publicJwks)
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

    console.log("\n=== [Untrusted Issuer] Step 2: Create JWT from untrusted issuer ===");

    const tokenEndpoint = `${backendUrl}/${tenantId}/v1/tokens`;
    const audience = `${backendUrl}/${tenantId}`;

    const assertionPayload = {
      iss: untrustedIssuer,  // Untrusted issuer!
      sub: userId,
      aud: audience,
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 })
    };

    console.log("JWT Assertion with untrusted issuer:", assertionPayload.iss);

    const assertion = createJwtWithJwk({
      payload: assertionPayload,
      privateJwk: untrustedIdpJwks.privateJwk,
      options: {
        algorithm: "ES256",
        keyId: "signing_key_1"
      }
    });

    console.log("\n=== [Untrusted Issuer] Step 3: Request Token (should fail) ===");

    const jwtBearerTokenResponse = await requestToken({
      endpoint: tokenEndpoint,
      grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: assertion,
      scope: "openid profile email",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    console.log("JWT Bearer Grant response status:", jwtBearerTokenResponse.status);
    console.log("Error response:", JSON.stringify(jwtBearerTokenResponse.data, null, 2));

    expect(jwtBearerTokenResponse.status).toBe(400);
    expect(jwtBearerTokenResponse.data.error).toBe("invalid_grant");
    expect(jwtBearerTokenResponse.data.error_description).toContain("not trusted");
    console.log("Correctly rejected JWT from untrusted issuer");

    console.log("\n=== [Untrusted Issuer] Step 4: Clean Up ===");

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

    console.log("\n=== [Untrusted Issuer] Test Completed ===");
    console.log("Verified: JWT from untrusted issuer is correctly rejected");
  });

  it("should reject JWT with invalid signature (wrong key)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@wrong-key.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;

    const serverJwksContent = await generateECP256JWKS();
    const trustedIdpJwks = await generateECP256KeyPair();
    const wrongKeyJwks = await generateECP256KeyPair();  // Different key pair

    const trustedIssuer = "https://trusted-idp.example.com";

    console.log("\n=== [Wrong Key] Step 1: Create Organization and Tenant ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Wrong Key Test Org ${timestamp}`,
        description: "Test rejection of JWT signed with wrong key",
      },
      tenant: {
        id: tenantId,
        name: `Wrong Key Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `WRONG_KEY_SESSION_${organizationId.substring(0, 8)}`,
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
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: serverJwksContent,
        grant_types_supported: [
          "authorization_code",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer"
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
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
      client: {
        client_id: orgClientId,
        client_id_alias: `test-wrong-key-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: [
          "authorization_code",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer"
        ],
        scope: "openid profile email management",
        client_name: `Wrong Key Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          available_federations: [
            {
              id: "trusted-idp",
              issuer: trustedIssuer,
              type: "oidc",
              subject_claim_mapping: "sub",
              jwt_bearer_grant_enabled: true,
              jwks: JSON.stringify(trustedIdpJwks.publicJwks)  // Trusted public key
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

    console.log("\n=== [Wrong Key] Step 2: Create JWT signed with wrong key ===");

    const tokenEndpoint = `${backendUrl}/${tenantId}/v1/tokens`;
    const audience = `${backendUrl}/${tenantId}`;

    const assertionPayload = {
      iss: trustedIssuer,  // Correct issuer
      sub: userId,
      aud: audience,
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 })
    };

    console.log("JWT signed with WRONG private key (not matching registered JWKS)");

    // Sign with wrong key (not matching the registered public key)
    const assertion = createJwtWithJwk({
      payload: assertionPayload,
      privateJwk: wrongKeyJwks.privateJwk,  // Wrong key!
      options: {
        algorithm: "ES256",
        keyId: "signing_key_1"
      }
    });

    console.log("\n=== [Wrong Key] Step 3: Request Token (should fail) ===");

    const jwtBearerTokenResponse = await requestToken({
      endpoint: tokenEndpoint,
      grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: assertion,
      scope: "openid profile email",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    console.log("JWT Bearer Grant response status:", jwtBearerTokenResponse.status);
    console.log("Error response:", JSON.stringify(jwtBearerTokenResponse.data, null, 2));

    expect(jwtBearerTokenResponse.status).toBe(400);
    expect(jwtBearerTokenResponse.data.error).toBe("invalid_grant");
    console.log("Correctly rejected JWT with invalid signature");

    console.log("\n=== [Wrong Key] Step 4: Clean Up ===");

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

    console.log("\n=== [Wrong Key] Test Completed ===");
    console.log("Verified: JWT signed with wrong key is correctly rejected");
  });

  it("should reject JWT with alg: none (no signature)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@alg-none.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;

    const serverJwksContent = await generateECP256JWKS();
    const trustedIdpJwks = await generateECP256KeyPair();

    const trustedIssuer = "https://trusted-idp.example.com";

    console.log("\n=== [Alg None] Step 1: Create Organization and Tenant ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Alg None Test Org ${timestamp}`,
        description: "Test rejection of JWT with alg: none",
      },
      tenant: {
        id: tenantId,
        name: `Alg None Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `ALG_NONE_SESSION_${organizationId.substring(0, 8)}`,
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
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: serverJwksContent,
        grant_types_supported: [
          "authorization_code",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer"
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
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
      client: {
        client_id: orgClientId,
        client_id_alias: `test-alg-none-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: [
          "authorization_code",
          "password",
          "urn:ietf:params:oauth:grant-type:jwt-bearer"
        ],
        scope: "openid profile email management",
        client_name: `Alg None Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          available_federations: [
            {
              id: "trusted-idp",
              issuer: trustedIssuer,
              type: "oidc",
              subject_claim_mapping: "sub",
              jwt_bearer_grant_enabled: true,
              jwks: JSON.stringify(trustedIdpJwks.publicJwks)
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

    console.log("\n=== [Alg None] Step 2: Create JWT with alg: none ===");

    const tokenEndpoint = `${backendUrl}/${tenantId}/v1/tokens`;
    const audience = `${backendUrl}/${tenantId}`;

    const assertionPayload = {
      iss: trustedIssuer,
      sub: userId,
      aud: audience,
      jti: generateJti(),
      exp: toEpocTime({ adjusted: 3600 }),
      iat: toEpocTime({ adjusted: 0 })
    };

    console.log("Creating JWT with alg: none (unsigned)");

    // Create JWT with alg: none (no signature)
    const assertion = createJwtWithNoneSignature({
      payload: assertionPayload,
      additionalOptions: {}
    });

    console.log("JWT (truncated):", assertion.substring(0, 50) + "...");

    console.log("\n=== [Alg None] Step 3: Request Token (should fail) ===");

    const jwtBearerTokenResponse = await requestToken({
      endpoint: tokenEndpoint,
      grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: assertion,
      scope: "openid profile email",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    console.log("JWT Bearer Grant response status:", jwtBearerTokenResponse.status);
    console.log("Error response:", JSON.stringify(jwtBearerTokenResponse.data, null, 2));

    expect(jwtBearerTokenResponse.status).toBe(400);
    expect(jwtBearerTokenResponse.data.error).toBe("invalid_grant");
    console.log("Correctly rejected JWT with alg: none");

    console.log("\n=== [Alg None] Step 4: Clean Up ===");

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

    console.log("\n=== [Alg None] Test Completed ===");
    console.log("Verified: JWT with alg: none is correctly rejected (prevents algorithm confusion attack)");
  });
});
