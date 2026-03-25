import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../lib/http";
import { requestToken, getUserinfo, getJwks, postAuthentication } from "../../api/oauthClient";
import { requestAuthorizations } from "../../oauth/request";
import { generateECP256JWKS, generateECP256KeyPair, createJwtWithJwk, generateJti, verifyAndDecodeJwt } from "../../lib/jose";
import { adminServerConfig, backendUrl } from "../testConfig";
import { toEpocTime } from "../../lib/util";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * RFC 8693 - OAuth 2.0 Token Exchange
 *
 * @see https://datatracker.ietf.org/doc/html/rfc8693
 */
describe("RFC 8693 - OAuth 2.0 Token Exchange", () => {
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

  /**
   * Helper: create a tenant with external IdP federation configured for token exchange
   */
  async function createTestTenant({ tokenExchangeGrantEnabled = true } = {}) {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@token-exchange.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;

    const serverJwksContent = await generateECP256JWKS();
    const externalIdpJwks = await generateECP256KeyPair();
    const externalIdpIssuer = "https://external-idp.example.com";

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Token Exchange Test Org ${timestamp}`,
        description: "RFC 8693 Token Exchange test organization",
      },
      tenant: {
        id: tenantId,
        name: `Token Exchange Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `TE_SESSION_${organizationId.substring(0, 8)}`,
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
          "urn:ietf:params:oauth:grant-type:token-exchange"
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
        client_id_alias: `test-token-exchange-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:ietf:params:oauth:grant-type:token-exchange"
        ],
        scope: "openid profile email management",
        client_name: `Token Exchange Test Client ${timestamp}`,
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
              jwt_bearer_grant_enabled: false,
              token_exchange_grant_enabled: tokenExchangeGrantEnabled,
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

    return {
      organizationId,
      tenantId,
      userId,
      orgClientId,
      orgClientSecret,
      orgAdminAccessToken: adminTokenResponse.data.access_token,
      externalIdpJwks,
      externalIdpIssuer,
      tokenEndpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      audience: `${backendUrl}/${tenantId}`,
      jwksEndpoint: `${backendUrl}/${tenantId}/v1/jwks`,
      userinfoEndpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
    };
  }

  async function cleanup(ctx) {
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}/clients/${ctx.orgClientId}`,
      headers: { Authorization: `Bearer ${ctx.orgAdminAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}`,
      headers: { Authorization: `Bearer ${ctx.orgAdminAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${ctx.organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
  }

  describe("2.1. Request", () => {

  it("grant_type REQUIRED. The value urn:ietf:params:oauth:grant-type:token-exchange indicates that a token exchange is being performed.", async () => {
    const ctx = await createTestTenant();

    try {
      const assertionPayload = {
        iss: ctx.externalIdpIssuer,
        sub: ctx.userId,
        aud: ctx.audience,
        jti: generateJti(),
        exp: toEpocTime({ adjusted: 3600 }),
        iat: toEpocTime({ adjusted: 0 }),
      };

      const subjectTokenJwt = createJwtWithJwk({
        payload: assertionPayload,
        privateJwk: ctx.externalIdpJwks.privateJwk,
        options: { algorithm: "ES256", keyId: "signing_key_1" },
      });

      const tokenResponse = await requestToken({
        endpoint: ctx.tokenEndpoint,
        grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
        subjectToken: subjectTokenJwt,
        subjectTokenType: "urn:ietf:params:oauth:token-type:access_token",
        scope: "openid profile email",
        clientId: ctx.orgClientId,
        clientSecret: ctx.orgClientSecret,
      });

      console.log("Token Exchange response status:", tokenResponse.status);
      if (tokenResponse.status !== 200) {
        console.log("Token Exchange error:", JSON.stringify(tokenResponse.data, null, 2));
      }

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data).toHaveProperty("issued_token_type");
      expect(tokenResponse.data.issued_token_type).toBe("urn:ietf:params:oauth:token-type:access_token");
      expect(tokenResponse.data.token_type).toBe("Bearer");

      // Verify access token
      const jwksResponse = await getJwks({ endpoint: ctx.jwksEndpoint });
      expect(jwksResponse.status).toBe(200);

      const { payload, verifyResult } = verifyAndDecodeJwt({
        jwt: tokenResponse.data.access_token,
        jwks: jwksResponse.data,
      });

      expect(verifyResult).toBe(true);
      expect(payload.sub).toBe(ctx.userId);
      expect(payload.iss).toBe(ctx.audience);

      // Verify userinfo
      const userinfoResponse = await getUserinfo({
        endpoint: ctx.userinfoEndpoint,
        authorizationHeader: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
      });

      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data.sub).toBe(ctx.userId);
    } finally {
      await cleanup(ctx);
    }
  });

  it("subject_token REQUIRED. A security token that represents the identity of the party on behalf of whom the request is being made.", async () => {
    const ctx = await createTestTenant();

    try {
      const tokenResponse = await requestToken({
        endpoint: ctx.tokenEndpoint,
        grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
        subjectTokenType: "urn:ietf:params:oauth:token-type:access_token",
        scope: "openid profile email",
        clientId: ctx.orgClientId,
        clientSecret: ctx.orgClientSecret,
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_request");
    } finally {
      await cleanup(ctx);
    }
  });

  it("subject_token_type REQUIRED. An identifier, as described in Section 3, that indicates the type of the security token in the subject_token parameter.", async () => {
    const ctx = await createTestTenant();

    try {
      const assertionPayload = {
        iss: ctx.externalIdpIssuer,
        sub: ctx.userId,
        aud: ctx.audience,
        jti: generateJti(),
        exp: toEpocTime({ adjusted: 3600 }),
        iat: toEpocTime({ adjusted: 0 }),
      };

      const subjectTokenJwt = createJwtWithJwk({
        payload: assertionPayload,
        privateJwk: ctx.externalIdpJwks.privateJwk,
        options: { algorithm: "ES256", keyId: "signing_key_1" },
      });

      const tokenResponse = await requestToken({
        endpoint: ctx.tokenEndpoint,
        grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
        subjectToken: subjectTokenJwt,
        scope: "openid profile email",
        clientId: ctx.orgClientId,
        clientSecret: ctx.orgClientSecret,
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_request");
    } finally {
      await cleanup(ctx);
    }
  });

  it("The authorization server MUST perform the appropriate validation procedures for the indicated token type - untrusted issuer MUST be rejected.", async () => {
    const ctx = await createTestTenant();

    try {
      const untrustedJwks = await generateECP256KeyPair();
      const untrustedIssuer = "https://untrusted-idp.example.com";

      const assertionPayload = {
        iss: untrustedIssuer,
        sub: ctx.userId,
        aud: ctx.audience,
        jti: generateJti(),
        exp: toEpocTime({ adjusted: 3600 }),
        iat: toEpocTime({ adjusted: 0 }),
      };

      const subjectTokenJwt = createJwtWithJwk({
        payload: assertionPayload,
        privateJwk: untrustedJwks.privateJwk,
        options: { algorithm: "ES256", keyId: "signing_key_1" },
      });

      const tokenResponse = await requestToken({
        endpoint: ctx.tokenEndpoint,
        grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
        subjectToken: subjectTokenJwt,
        subjectTokenType: "urn:ietf:params:oauth:token-type:access_token",
        scope: "openid profile email",
        clientId: ctx.orgClientId,
        clientSecret: ctx.orgClientSecret,
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_grant");
    } finally {
      await cleanup(ctx);
    }
  });

  it("The authorization server MUST perform the appropriate validation procedures for the indicated token type - invalid signature MUST be rejected.", async () => {
    const ctx = await createTestTenant();

    try {
      const wrongKeyJwks = await generateECP256KeyPair();

      const assertionPayload = {
        iss: ctx.externalIdpIssuer,
        sub: ctx.userId,
        aud: ctx.audience,
        jti: generateJti(),
        exp: toEpocTime({ adjusted: 3600 }),
        iat: toEpocTime({ adjusted: 0 }),
      };

      // Sign with wrong key
      const subjectTokenJwt = createJwtWithJwk({
        payload: assertionPayload,
        privateJwk: wrongKeyJwks.privateJwk,
        options: { algorithm: "ES256", keyId: "signing_key_1" },
      });

      const tokenResponse = await requestToken({
        endpoint: ctx.tokenEndpoint,
        grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
        subjectToken: subjectTokenJwt,
        subjectTokenType: "urn:ietf:params:oauth:token-type:access_token",
        scope: "openid profile email",
        clientId: ctx.orgClientId,
        clientSecret: ctx.orgClientSecret,
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_grant");
    } finally {
      await cleanup(ctx);
    }
  });

  it("The authorization server MUST perform the appropriate validation procedures for the indicated token type - expired subject_token MUST be rejected.", async () => {
    const ctx = await createTestTenant();

    try {
      const assertionPayload = {
        iss: ctx.externalIdpIssuer,
        sub: ctx.userId,
        aud: ctx.audience,
        jti: generateJti(),
        exp: toEpocTime({ adjusted: -3600 }), // expired 1 hour ago
        iat: toEpocTime({ adjusted: -7200 }),
      };

      const subjectTokenJwt = createJwtWithJwk({
        payload: assertionPayload,
        privateJwk: ctx.externalIdpJwks.privateJwk,
        options: { algorithm: "ES256", keyId: "signing_key_1" },
      });

      const tokenResponse = await requestToken({
        endpoint: ctx.tokenEndpoint,
        grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
        subjectToken: subjectTokenJwt,
        subjectTokenType: "urn:ietf:params:oauth:token-type:access_token",
        scope: "openid profile email",
        clientId: ctx.orgClientId,
        clientSecret: ctx.orgClientSecret,
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_grant");
    } finally {
      await cleanup(ctx);
    }
  });

  }); // describe("2.1. Request")

  describe("2.2.1. Successful Response", () => {

  it("issued_token_type REQUIRED. An identifier for the representation of the issued security token.", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const adminUserId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@jit-test.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;

    const serverJwksContent = await generateECP256JWKS();
    const externalIdpJwks = await generateECP256KeyPair();
    const externalIdpIssuer = "https://external-idp-jit.example.com";

    // User that exists ONLY on external IdP (not in idp-server)
    const externalOnlyUserId = uuidv4();
    const externalUserEmail = `external-user-${timestamp}@jit-test.example.com`;

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `JIT Provisioning Test Org ${timestamp}`,
        description: "JIT Provisioning test organization",
      },
      tenant: {
        id: tenantId,
        name: `JIT Provisioning Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `JIT_SESSION_${organizationId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: { allow_origins: [backendUrl] },
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
          "urn:ietf:params:oauth:grant-type:token-exchange"
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
        extension: { access_token_type: "JWT" },
      },
      user: {
        sub: adminUserId,
        email: orgAdminEmail,
        raw_password: orgAdminPassword,
        username: orgAdminEmail,
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-jit-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:ietf:params:oauth:grant-type:token-exchange"
        ],
        scope: "openid profile email management",
        client_name: `JIT Provisioning Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          available_federations: [
            {
              id: "external-idp-jit",
              issuer: externalIdpIssuer,
              type: "oidc",
              sso_provider: "ExternalJitIdP",
              token_exchange_grant_enabled: true,
              jit_provisioning_enabled: true,
              jwks: JSON.stringify(externalIdpJwks.publicJwks),
              userinfo_mapping_rules: [
                { from: "$.sub", to: "external_user_id" },
                { from: "$.email", to: "email" },
                { from: "$.preferred_username", to: "preferred_username" },
                { from: "$.name", to: "name" }
              ]
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
    console.log("JIT onboarding status:", onboardingResponse.status);
    if (onboardingResponse.status !== 201) {
      console.log("JIT onboarding error:", JSON.stringify(onboardingResponse.data, null, 2));
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

    try {
      // Create JWT for a user that does NOT exist in idp-server
      const assertionPayload = {
        iss: externalIdpIssuer,
        sub: externalOnlyUserId,
        aud: `${backendUrl}/${tenantId}`,
        jti: generateJti(),
        exp: toEpocTime({ adjusted: 3600 }),
        iat: toEpocTime({ adjusted: 0 }),
        email: externalUserEmail,
        name: "JIT Test User",
        preferred_username: externalUserEmail,
      };

      const subjectTokenJwt = createJwtWithJwk({
        payload: assertionPayload,
        privateJwk: externalIdpJwks.privateJwk,
        options: { algorithm: "ES256", keyId: "signing_key_1" },
      });

      console.log("\n=== JIT Provisioning: Token Exchange with non-existing user ===");

      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
        subjectToken: subjectTokenJwt,
        subjectTokenType: "urn:ietf:params:oauth:token-type:access_token",
        scope: "openid profile email",
        clientId: orgClientId,
        clientSecret: orgClientSecret,
      });

      console.log("JIT Token Exchange response status:", tokenResponse.status);
      if (tokenResponse.status !== 200) {
        console.log("JIT Token Exchange error:", JSON.stringify(tokenResponse.data, null, 2));
      }

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data).toHaveProperty("issued_token_type");
      expect(tokenResponse.data.issued_token_type).toBe("urn:ietf:params:oauth:token-type:access_token");

      // Verify userinfo returns the JIT-provisioned user's info
      const userinfoResponse = await getUserinfo({
        endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        authorizationHeader: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
      });

      expect(userinfoResponse.status).toBe(200);
      // sub should be a new UUID (not the external IdP's sub)
      expect(userinfoResponse.data.sub).toBeDefined();
      expect(userinfoResponse.data.sub).not.toBe(externalOnlyUserId);
      console.log("JIT Provisioning succeeded, new user sub:", userinfoResponse.data.sub);

    } finally {
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
    }
  });

  }); // describe("2.2.1. Successful Response")

  describe("JIT Provisioning (Extension)", () => {

  it("When jit_provisioning_enabled is false and user does not exist, the authorization server MUST reject the request with invalid_grant.", async () => {
    const ctx = await createTestTenant(); // default: jit disabled

    try {
      // Use a sub that doesn't exist in idp-server
      const nonExistentUserId = uuidv4();

      const assertionPayload = {
        iss: ctx.externalIdpIssuer,
        sub: nonExistentUserId,
        aud: ctx.audience,
        jti: generateJti(),
        exp: toEpocTime({ adjusted: 3600 }),
        iat: toEpocTime({ adjusted: 0 }),
      };

      const subjectTokenJwt = createJwtWithJwk({
        payload: assertionPayload,
        privateJwk: ctx.externalIdpJwks.privateJwk,
        options: { algorithm: "ES256", keyId: "signing_key_1" },
      });

      const tokenResponse = await requestToken({
        endpoint: ctx.tokenEndpoint,
        grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
        subjectToken: subjectTokenJwt,
        subjectTokenType: "urn:ietf:params:oauth:token-type:access_token",
        scope: "openid profile email",
        clientId: ctx.orgClientId,
        clientSecret: ctx.orgClientSecret,
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_grant");
    } finally {
      await cleanup(ctx);
    }
  });

  }); // describe("JIT Provisioning (Extension)")

  describe("Introspection Verification (Extension)", () => {

  it("When token_exchange_token_verification_method is introspection, the authorization server MUST introspect the subject_token at the external IdP and fetch additional userinfo via userinfoHttpRequests for JIT Provisioning.", async () => {
    const timestamp = Date.now();
    const tenantAUserId = uuidv4();

    // === Tenant A: the "external IdP" that issues the original access token ===
    const tenantAOrgId = uuidv4();
    const tenantAId = uuidv4();
    const tenantAClientId = uuidv4();
    const tenantAEmail = `admin-a-${timestamp}@userinfo-fetch.example.com`;
    const tenantAPassword = `TestPassA${timestamp}!`;
    const tenantAClientSecret = `client-secret-a-${crypto.randomBytes(16).toString("hex")}`;
    const tenantAJwks = await generateECP256JWKS();

    const tenantAOnboarding = {
      organization: {
        id: tenantAOrgId,
        name: `Userinfo Fetch External IdP Org ${timestamp}`,
        description: "Acts as external IdP for userinfo_http_requests test",
      },
      tenant: {
        id: tenantAId,
        name: `Userinfo Fetch External IdP Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `UF_A_SESSION_${tenantAOrgId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: { allow_origins: [backendUrl] },
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_client_id: true,
          persistence_enabled: true,
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantAId}`,
        authorization_endpoint: `${backendUrl}/${tenantAId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantAId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantAId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantAId}/v1/jwks`,
        jwks: tenantAJwks,
        grant_types_supported: ["authorization_code", "refresh_token", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        claims_supported: ["sub", "email", "email_verified", "name", "preferred_username"],
        claims_parameter_supported: true,
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantAId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantAId}/v1/tokens/revocation`,
        response_modes_supported: ["query"],
        extension: { access_token_type: "JWT" },
      },
      user: {
        sub: tenantAUserId,
        email: tenantAEmail,
        raw_password: tenantAPassword,
        username: tenantAEmail,
        name: "Userinfo Fetch Test User",
      },
      client: {
        client_id: tenantAClientId,
        client_id_alias: `test-userinfo-fetch-ext-idp-client-${timestamp}`,
        client_secret: tenantAClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: `Userinfo Fetch External IdP Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const tenantAResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: tenantAOnboarding,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(tenantAResponse.status).toBe(201);

    // Get access token from Tenant A via Authorization Code Flow
    // (Password Grant doesn't populate GrantUserinfoClaims - see Issue #1440)
    const tenantAAuthEndpoint = `${backendUrl}/${tenantAId}/v1/authorizations`;
    const tenantAAuthorizeEndpoint = `${backendUrl}/${tenantAId}/v1/authorizations/{id}/authorize`;
    const tenantAAuthIdEndpoint = `${backendUrl}/${tenantAId}/v1/authorizations/{id}/`;

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: tenantAAuthEndpoint,
      clientId: tenantAClientId,
      responseType: "code",
      state: "token-exchange-test",
      scope: "openid profile email",
      redirectUri: "http://localhost:3000/callback",
      authorizeEndpoint: tenantAAuthorizeEndpoint,
      user: {
        username: tenantAEmail,
        password: tenantAPassword,
      },
      interaction: async (id, user) => {
        await postAuthentication({
          endpoint: tenantAAuthIdEndpoint + "password-authentication",
          id,
          body: user,
        });
      },
    });
    expect(authorizationResponse.code).toBeDefined();

    const tenantATokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantAId}/v1/tokens`,
      grantType: "authorization_code",
      code: authorizationResponse.code,
      redirectUri: "http://localhost:3000/callback",
      clientId: tenantAClientId,
      clientSecret: tenantAClientSecret,
    });
    expect(tenantATokenResponse.status).toBe(200);
    const externalAccessToken = tenantATokenResponse.data.access_token;

    // Verify Tenant A's UserInfo returns email via Authorization Code Flow
    const tenantAUserinfo = await getUserinfo({
      endpoint: `${backendUrl}/${tenantAId}/v1/userinfo`,
      authorizationHeader: { Authorization: `Bearer ${externalAccessToken}` },
    });
    expect(tenantAUserinfo.status).toBe(200);
    expect(tenantAUserinfo.data.email).toBe(tenantAEmail);

    // Get admin token for Tenant A (for cleanup)
    const tenantAAdminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantAId}/v1/tokens`,
      grantType: "password",
      username: tenantAEmail,
      password: tenantAPassword,
      scope: "openid profile email management",
      clientId: tenantAClientId,
      clientSecret: tenantAClientSecret,
    });
    expect(tenantAAdminTokenResponse.status).toBe(200);
    const tenantAAdminToken = tenantAAdminTokenResponse.data.access_token;

    // === Tenant B: token exchange server with userinfo_http_requests ===
    const tenantBOrgId = uuidv4();
    const tenantBId = uuidv4();
    const tenantBClientId = uuidv4();
    const tenantBAdminUserId = uuidv4();
    const tenantBEmail = `admin-b-${timestamp}@userinfo-fetch.example.com`;
    const tenantBPassword = `TestPassB${timestamp}!`;
    const tenantBClientSecret = `client-secret-b-${crypto.randomBytes(16).toString("hex")}`;
    const tenantBJwks = await generateECP256JWKS();

    const tenantBOnboarding = {
      organization: {
        id: tenantBOrgId,
        name: `Userinfo Fetch Token Exchange Org ${timestamp}`,
        description: "Token exchange server with userinfo_http_requests + JIT",
      },
      tenant: {
        id: tenantBId,
        name: `Userinfo Fetch Token Exchange Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `UF_B_SESSION_${tenantBOrgId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: { allow_origins: [backendUrl] },
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_client_id: true,
          persistence_enabled: true,
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantBId}`,
        authorization_endpoint: `${backendUrl}/${tenantBId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantBId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantBId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantBId}/v1/jwks`,
        jwks: tenantBJwks,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:ietf:params:oauth:grant-type:token-exchange"
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        claims_supported: ["sub", "email", "name", "preferred_username"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantBId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantBId}/v1/tokens/revocation`,
        response_modes_supported: ["query"],
        extension: { access_token_type: "JWT" },
      },
      user: {
        sub: tenantBAdminUserId,
        email: tenantBEmail,
        raw_password: tenantBPassword,
        username: tenantBEmail,
      },
      client: {
        client_id: tenantBClientId,
        client_id_alias: `test-userinfo-fetch-te-client-${timestamp}`,
        client_secret: tenantBClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:ietf:params:oauth:grant-type:token-exchange"
        ],
        scope: "openid profile email management",
        client_name: `Userinfo Fetch Token Exchange Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          available_federations: [
            {
              id: "external-idp-userinfo-fetch",
              issuer: `${backendUrl}/${tenantAId}`,
              type: "oidc",
              sso_provider: "TenantA",
              token_exchange_grant_enabled: true,
              token_exchange_token_verification_method: "introspection",
              jit_provisioning_enabled: true,
              introspection_endpoint: `${backendUrl}/${tenantAId}/v1/tokens/introspection`,
              introspection_auth_method: "client_secret_post",
              introspection_client_id: tenantAClientId,
              introspection_client_secret: tenantAClientSecret,
              userinfo_http_requests: [
                {
                  url: `${backendUrl}/${tenantAId}/v1/userinfo`,
                  method: "GET",
                  header_mapping_rules: [
                    {
                      from: "$.request_body.access_token",
                      to: "Authorization",
                      functions: [{ name: "format", args: { template: "Bearer {{value}}" } }]
                    }
                  ]
                }
              ],
              userinfo_mapping_rules: [
                { from: "$.userinfo_execution_http_requests[0].response_body.sub", to: "external_user_id" },
                { from: "$.userinfo_execution_http_requests[0].response_body.email", to: "email" },
                { from: "$.userinfo_execution_http_requests[0].response_body.email", to: "preferred_username" }
              ]
            }
          ]
        }
      },
    };

    const tenantBResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: tenantBOnboarding,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(tenantBResponse.status).toBe(201);

    const tenantBAdminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantBId}/v1/tokens`,
      grantType: "password",
      username: tenantBEmail,
      password: tenantBPassword,
      scope: "openid profile email management",
      clientId: tenantBClientId,
      clientSecret: tenantBClientSecret,
    });
    expect(tenantBAdminTokenResponse.status).toBe(200);
    const tenantBAdminToken = tenantBAdminTokenResponse.data.access_token;

    try {
      console.log("\n=== Token Exchange with Introspection + userinfoHttpRequests + JIT ===");

      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantBId}/v1/tokens`,
        grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
        subjectToken: externalAccessToken,
        subjectTokenType: "urn:ietf:params:oauth:token-type:access_token",
        scope: "openid profile email",
        clientId: tenantBClientId,
        clientSecret: tenantBClientSecret,
      });

      console.log("Token Exchange (userinfo fetch) response status:", tokenResponse.status);
      if (tokenResponse.status !== 200) {
        console.log("Token Exchange error:", JSON.stringify(tokenResponse.data, null, 2));
      }

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data.issued_token_type).toBe("urn:ietf:params:oauth:token-type:access_token");

      // Verify the exchanged token returns userinfo with email from external IdP
      const userinfoResponse = await getUserinfo({
        endpoint: `${backendUrl}/${tenantBId}/v1/userinfo`,
        authorizationHeader: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
      });

      console.log("Userinfo fetch result:", JSON.stringify(userinfoResponse.data, null, 2));

      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data.sub).toBeDefined();
      expect(userinfoResponse.data.sub).not.toBe(tenantAUserId);
      // The email should have been fetched from Tenant A's userinfo endpoint via userinfoHttpRequests
      expect(userinfoResponse.data.email).toBe(tenantAEmail);
      console.log("Introspection + userinfoHttpRequests + JIT succeeded, email:", userinfoResponse.data.email);

    } finally {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${tenantBOrgId}/tenants/${tenantBId}/clients/${tenantBClientId}`,
        headers: { Authorization: `Bearer ${tenantBAdminToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${tenantBOrgId}/tenants/${tenantBId}`,
        headers: { Authorization: `Bearer ${tenantBAdminToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${tenantBOrgId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${tenantAOrgId}/tenants/${tenantAId}/clients/${tenantAClientId}`,
        headers: { Authorization: `Bearer ${tenantAAdminToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${tenantAOrgId}/tenants/${tenantAId}`,
        headers: { Authorization: `Bearer ${tenantAAdminToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${tenantAOrgId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });
    }
  });

  it("When token_exchange_token_verification_method is introspection, the authorization server MUST introspect the subject_token and perform JIT Provisioning when the user does not exist.", async () => {
    const timestamp = Date.now();
    const tenantAUserId = uuidv4();

    // === Tenant A: the "external IdP" that issues the original access token ===
    const tenantAOrgId = uuidv4();
    const tenantAId = uuidv4();
    const tenantAClientId = uuidv4();
    const tenantAEmail = `admin-a-${timestamp}@token-exchange-introspect.example.com`;
    const tenantAPassword = `TestPassA${timestamp}!`;
    const tenantAClientSecret = `client-secret-a-${crypto.randomBytes(16).toString("hex")}`;
    const tenantAJwks = await generateECP256JWKS();

    const tenantAOnboarding = {
      organization: {
        id: tenantAOrgId,
        name: `Introspect External IdP Org ${timestamp}`,
        description: "Acts as external IdP for token exchange introspection test",
      },
      tenant: {
        id: tenantAId,
        name: `Introspect External IdP Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `TE_A_SESSION_${tenantAOrgId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: { allow_origins: [backendUrl] },
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_client_id: true,
          persistence_enabled: true,
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantAId}`,
        authorization_endpoint: `${backendUrl}/${tenantAId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantAId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantAId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantAId}/v1/jwks`,
        jwks: tenantAJwks,
        grant_types_supported: ["authorization_code", "refresh_token", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantAId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantAId}/v1/tokens/revocation`,
        response_modes_supported: ["query"],
        extension: { access_token_type: "JWT" },
      },
      user: {
        sub: tenantAUserId,
        email: tenantAEmail,
        raw_password: tenantAPassword,
        username: tenantAEmail,
      },
      client: {
        client_id: tenantAClientId,
        client_id_alias: `test-introspect-ext-idp-client-${timestamp}`,
        client_secret: tenantAClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: `Introspect External IdP Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const tenantAResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: tenantAOnboarding,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    console.log("Tenant A onboarding status:", tenantAResponse.status);
    if (tenantAResponse.status !== 201) {
      console.log("Tenant A onboarding error:", JSON.stringify(tenantAResponse.data, null, 2));
    }
    expect(tenantAResponse.status).toBe(201);

    // Get access token from Tenant A
    const tenantATokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantAId}/v1/tokens`,
      grantType: "password",
      username: tenantAEmail,
      password: tenantAPassword,
      scope: "openid profile email",
      clientId: tenantAClientId,
      clientSecret: tenantAClientSecret,
    });
    expect(tenantATokenResponse.status).toBe(200);
    const externalAccessToken = tenantATokenResponse.data.access_token;
    console.log("Got access token from Tenant A (external IdP)");

    // Get admin token for Tenant A (for cleanup)
    const tenantAAdminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantAId}/v1/tokens`,
      grantType: "password",
      username: tenantAEmail,
      password: tenantAPassword,
      scope: "openid profile email management",
      clientId: tenantAClientId,
      clientSecret: tenantAClientSecret,
    });
    expect(tenantAAdminTokenResponse.status).toBe(200);
    const tenantAAdminToken = tenantAAdminTokenResponse.data.access_token;

    // === Tenant B: token exchange server — NO shared user, JIT will create one ===
    const tenantBOrgId = uuidv4();
    const tenantBId = uuidv4();
    const tenantBClientId = uuidv4();
    const tenantBAdminUserId = uuidv4(); // Different user for admin only
    const tenantBEmail = `admin-b-${timestamp}@token-exchange-introspect.example.com`;
    const tenantBPassword = `TestPassB${timestamp}!`;
    const tenantBClientSecret = `client-secret-b-${crypto.randomBytes(16).toString("hex")}`;
    const tenantBJwks = await generateECP256JWKS();

    const tenantBOnboarding = {
      organization: {
        id: tenantBOrgId,
        name: `Introspect Token Exchange Org ${timestamp}`,
        description: "Token exchange server with introspection + JIT",
      },
      tenant: {
        id: tenantBId,
        name: `Introspect Token Exchange Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `TE_B_SESSION_${tenantBOrgId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: { allow_origins: [backendUrl] },
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_client_id: true,
          persistence_enabled: true,
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantBId}`,
        authorization_endpoint: `${backendUrl}/${tenantBId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantBId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantBId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantBId}/v1/jwks`,
        jwks: tenantBJwks,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:ietf:params:oauth:grant-type:token-exchange"
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantBId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantBId}/v1/tokens/revocation`,
        response_modes_supported: ["query"],
        extension: { access_token_type: "JWT" },
      },
      user: {
        sub: tenantBAdminUserId, // Admin user only — NOT the external user
        email: tenantBEmail,
        raw_password: tenantBPassword,
        username: tenantBEmail,
      },
      client: {
        client_id: tenantBClientId,
        client_id_alias: `test-introspect-te-client-${timestamp}`,
        client_secret: tenantBClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: [
          "authorization_code",
          "refresh_token",
          "password",
          "urn:ietf:params:oauth:grant-type:token-exchange"
        ],
        scope: "openid profile email management",
        client_name: `Introspect Token Exchange Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          available_federations: [
            {
              id: "external-idp-introspection",
              issuer: `${backendUrl}/${tenantAId}`,
              type: "oidc",
              sso_provider: "TenantA",
              token_exchange_grant_enabled: true,
              token_exchange_token_verification_method: "introspection",
              jit_provisioning_enabled: true,
              introspection_endpoint: `${backendUrl}/${tenantAId}/v1/tokens/introspection`,
              introspection_auth_method: "client_secret_post",
              introspection_client_id: tenantAClientId,
              introspection_client_secret: tenantAClientSecret,
              userinfo_mapping_rules: [
                { from: "$.sub", to: "external_user_id" },
                { from: "$.sub", to: "preferred_username" }
              ]
            }
          ]
        }
      },
    };

    const tenantBResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: tenantBOnboarding,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    console.log("Tenant B onboarding status:", tenantBResponse.status);
    if (tenantBResponse.status !== 201) {
      console.log("Tenant B onboarding error:", JSON.stringify(tenantBResponse.data, null, 2));
    }
    expect(tenantBResponse.status).toBe(201);

    // Get admin token for Tenant B (for cleanup)
    const tenantBAdminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantBId}/v1/tokens`,
      grantType: "password",
      username: tenantBEmail,
      password: tenantBPassword,
      scope: "openid profile email management",
      clientId: tenantBClientId,
      clientSecret: tenantBClientSecret,
    });
    expect(tenantBAdminTokenResponse.status).toBe(200);
    const tenantBAdminToken = tenantBAdminTokenResponse.data.access_token;

    try {
      console.log("\n=== Token Exchange with Introspection + JIT Provisioning ===");

      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantBId}/v1/tokens`,
        grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
        subjectToken: externalAccessToken,
        subjectTokenType: "urn:ietf:params:oauth:token-type:access_token",
        scope: "openid profile email",
        clientId: tenantBClientId,
        clientSecret: tenantBClientSecret,
      });

      console.log("Token Exchange (introspection+JIT) response status:", tokenResponse.status);
      if (tokenResponse.status !== 200) {
        console.log("Token Exchange error:", JSON.stringify(tokenResponse.data, null, 2));
      }

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data).toHaveProperty("issued_token_type");
      expect(tokenResponse.data.issued_token_type).toBe("urn:ietf:params:oauth:token-type:access_token");

      // Verify the exchanged token works on Tenant B
      const userinfoResponse = await getUserinfo({
        endpoint: `${backendUrl}/${tenantBId}/v1/userinfo`,
        authorizationHeader: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
      });

      expect(userinfoResponse.status).toBe(200);
      // sub should be a new UUID (JIT-provisioned), not the Tenant A user's sub
      expect(userinfoResponse.data.sub).toBeDefined();
      expect(userinfoResponse.data.sub).not.toBe(tenantAUserId);
      console.log("Introspection + JIT succeeded, new user sub:", userinfoResponse.data.sub);

    } finally {
      // Cleanup Tenant B
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${tenantBOrgId}/tenants/${tenantBId}/clients/${tenantBClientId}`,
        headers: { Authorization: `Bearer ${tenantBAdminToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${tenantBOrgId}/tenants/${tenantBId}`,
        headers: { Authorization: `Bearer ${tenantBAdminToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${tenantBOrgId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });

      // Cleanup Tenant A
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${tenantAOrgId}/tenants/${tenantAId}/clients/${tenantAClientId}`,
        headers: { Authorization: `Bearer ${tenantAAdminToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${tenantAOrgId}/tenants/${tenantAId}`,
        headers: { Authorization: `Bearer ${tenantAAdminToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${tenantAOrgId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });
    }
  });

  }); // describe("Introspection Verification (Extension)")
});
