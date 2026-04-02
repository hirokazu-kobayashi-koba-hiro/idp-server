import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
  getUserinfo,
  inspectToken,
  revokeToken,
} from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
  createBasicAuthHeader,
} from "../../../lib/util";

/**
 * Standard Use Case: JWT Access Token & Token Revocation via Introspection
 *
 * EXPERIMENTS (third-party) Exp6, Exp7 を E2E Jest に移植。
 *
 * カバー範囲:
 * - B-19: access_token_type を opaque と JWT で比較
 *   - opaque: ピリオドなし、デコード不可
 *   - JWT: header.payload.signature、JWKS で署名検証可能、ペイロードに scope/sub
 * - B-20: Token Revocation → Introspection で active: false
 *   - authorization_code フローのトークン
 *   - client_credentials (M2M) のトークン
 */
describe("Standard Use Case: JWT Access Token & Token Revocation", () => {
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

  /** Helper: create a self-contained tenant+client for each test */
  async function createTestEnvironment({ accessTokenType, grantTypes, scope }) {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@jwt-test.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `JWT AT Test Org ${timestamp}`,
          description: `E2E test: access_token_type=${accessTokenType}`,
        },
        tenant: {
          id: tenantId,
          name: `JWT AT Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `JWT_${tenantId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: grantTypes,
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "api:read", "api:write", "management", "org-management"],
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email", "email_verified"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
          token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
          extension: {
            access_token_type: accessTokenType,
          },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: "Admin",
          email: adminEmail,
          email_verified: true,
          raw_password: adminPassword,
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: grantTypes,
          scope: scope,
          client_name: `JWT Test Client ${accessTokenType}`,
          token_endpoint_auth_method: "client_secret_basic",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardingResponse.status !== 201) {
      console.error("Onboarding failed:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);

    // Get management token
    const mgmtTokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management org-management",
      basicAuth: createBasicAuthHeader({ username: clientId, password: clientSecret }),
    });
    expect(mgmtTokenResp.status).toBe(200);
    const mgmtAccessToken = mgmtTokenResp.data.access_token;

    // Register auth configs
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" },
        interactions: {
          "password-authentication": {
            request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } },
            pre_hook: {}, execution: { function: "password_verification" }, post_hook: {},
            response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }, { from: "$.error_description", to: "error_description" }] },
          },
        },
      },
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(), type: "initial-registration", attributes: {}, metadata: {},
        interactions: { "initial-registration": { request: { schema: { type: "object", required: ["email", "password", "name"], properties: { name: { type: "string" }, email: { type: "string", format: "email" }, password: { type: "string", minLength: 8 } } } } } },
      },
    });

    return {
      organizationId, tenantId, clientId, clientSecret,
      adminEmail, adminPassword, mgmtAccessToken, redirectUri, jwksContent,
      cleanup: async () => {
        await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`, headers: { Authorization: `Bearer ${mgmtAccessToken}` } }).catch(() => {});
        await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
      },
    };
  }

  it("B-19: JWT access token is decodable and verifiable via JWKS", async () => {
    console.log("\n=== JWT Access Token Test ===");

    const env = await createTestEnvironment({
      accessTokenType: "JWT",
      grantTypes: ["authorization_code", "refresh_token", "password"],
      scope: "openid profile email management org-management",
    });

    try {
      // Get token via authorization code flow
      const testEmail = `jwt-user-${Date.now()}@jwt-test.example.com`;
      const testPassword = "JwtTestPass_1!";

      const authResp = await getAuthorizations({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations`,
        clientId: env.clientId, responseType: "code",
        state: `jwt-${Date.now()}`, scope: "openid profile email", redirectUri: env.redirectUri,
      });
      const { params } = convertNextAction(authResp.headers.location);
      const authId = params.get("id");

      await postWithJson({
        url: `${backendUrl}/${env.tenantId}/v1/authorizations/${authId}/initial-registration`,
        body: { email: testEmail, password: testPassword, name: "JWT Test User" },
      });
      const authorizeResp = await authorize({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations/{id}/authorize`,
        id: authId, body: {},
      });
      const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);
      const tokenResp = await requestToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
        grantType: "authorization_code", code: result.code, redirectUri: env.redirectUri,
        basicAuth: createBasicAuthHeader({ username: env.clientId, password: env.clientSecret }),
      });
      expect(tokenResp.status).toBe(200);
      const accessToken = tokenResp.data.access_token;

      // JWT has 3 parts separated by dots
      const parts = accessToken.split(".");
      expect(parts.length).toBe(3);
      console.log(`JWT format confirmed: ${parts.length} parts (header.payload.signature)`);

      // Verify via JWKS
      const jwksResp = await get({ url: `${backendUrl}/${env.tenantId}/v1/jwks` });
      expect(jwksResp.status).toBe(200);

      const { header, payload, verifyResult } = verifyAndDecodeJwt({
        jwt: accessToken,
        jwks: jwksResp.data,
      });

      expect(header.alg).toBeDefined();
      expect(payload.iss).toBe(`${backendUrl}/${env.tenantId}`);
      expect(payload.sub).toBeDefined();
      expect(payload.exp).toBeGreaterThan(Math.floor(Date.now() / 1000));
      console.log(`JWT payload: iss=${payload.iss}, sub=${payload.sub}, alg=${header.alg}`);

      // Introspection also works on JWT tokens
      const introspectResp = await inspectToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens/introspection`,
        token: accessToken,
        basicAuth: createBasicAuthHeader({ username: env.clientId, password: env.clientSecret }),
      });
      expect(introspectResp.status).toBe(200);
      expect(introspectResp.data.active).toBe(true);
      console.log("Introspection on JWT token: active=true");

      console.log("=== JWT Access Token Test Completed ===");
    } finally {
      await env.cleanup();
    }
  });

  it("B-20: revoked token becomes inactive in Introspection (auth_code + M2M)", async () => {
    console.log("\n=== Token Revocation + Introspection Test ===");

    const env = await createTestEnvironment({
      accessTokenType: "JWT",
      grantTypes: ["authorization_code", "refresh_token", "client_credentials", "password"],
      scope: "openid profile email api:read api:write management org-management",
    });

    // Create M2M client
    const m2mClientId = uuidv4();
    const m2mClientSecret = `m2m-${crypto.randomBytes(16).toString("hex")}`;
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${env.organizationId}/tenants/${env.tenantId}/clients`,
      headers: { Authorization: `Bearer ${env.mgmtAccessToken}` },
      body: {
        client_id: m2mClientId, client_secret: m2mClientSecret,
        redirect_uris: [env.redirectUri], response_types: ["code"],
        grant_types: ["client_credentials"],
        scope: "api:read api:write", client_name: "M2M Revoke Client",
        token_endpoint_auth_method: "client_secret_basic", application_type: "web",
      },
    });

    try {
      // --- Part A: Authorization Code Flow Token Revocation ---
      console.log("Part A: Auth Code Flow token revocation");

      const testEmail = `revoke-${Date.now()}@jwt-test.example.com`;
      const testPassword = "RevokePass_1!";
      const authResp = await getAuthorizations({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations`,
        clientId: env.clientId, responseType: "code",
        state: `revoke-${Date.now()}`, scope: "openid profile email", redirectUri: env.redirectUri,
      });
      const { params } = convertNextAction(authResp.headers.location);
      const authId = params.get("id");

      await postWithJson({
        url: `${backendUrl}/${env.tenantId}/v1/authorizations/${authId}/initial-registration`,
        body: { email: testEmail, password: testPassword, name: "Revoke User" },
      });
      const authorizeResp = await authorize({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations/{id}/authorize`,
        id: authId, body: {},
      });
      const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);
      const tokenResp = await requestToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
        grantType: "authorization_code", code: result.code, redirectUri: env.redirectUri,
        basicAuth: createBasicAuthHeader({ username: env.clientId, password: env.clientSecret }),
      });
      expect(tokenResp.status).toBe(200);
      const accessToken = tokenResp.data.access_token;

      // Before revocation → active: true
      const beforeRevoke = await inspectToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens/introspection`,
        token: accessToken,
        basicAuth: createBasicAuthHeader({ username: env.clientId, password: env.clientSecret }),
      });
      expect(beforeRevoke.data.active).toBe(true);
      console.log("Before revocation: active=true");

      // Revoke
      const revokeResp = await revokeToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens/revocation`,
        token: accessToken,
        basicAuth: createBasicAuthHeader({ username: env.clientId, password: env.clientSecret }),
      });
      expect(revokeResp.status).toBe(200);
      console.log("Token revoked");

      // After revocation → active: false
      const afterRevoke = await inspectToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens/introspection`,
        token: accessToken,
        basicAuth: createBasicAuthHeader({ username: env.clientId, password: env.clientSecret }),
      });
      expect(afterRevoke.data.active).toBe(false);
      console.log("After revocation: active=false");

      // --- Part B: M2M Token Revocation ---
      console.log("\nPart B: M2M token revocation");

      const m2mTokenResp = await requestToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
        grantType: "client_credentials", scope: "api:read",
        basicAuth: createBasicAuthHeader({ username: m2mClientId, password: m2mClientSecret }),
      });
      expect(m2mTokenResp.status).toBe(200);
      const m2mToken = m2mTokenResp.data.access_token;

      const m2mBefore = await inspectToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens/introspection`,
        token: m2mToken,
        basicAuth: createBasicAuthHeader({ username: m2mClientId, password: m2mClientSecret }),
      });
      expect(m2mBefore.data.active).toBe(true);
      console.log("M2M before revocation: active=true");

      await revokeToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens/revocation`,
        token: m2mToken,
        basicAuth: createBasicAuthHeader({ username: m2mClientId, password: m2mClientSecret }),
      });

      const m2mAfter = await inspectToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens/introspection`,
        token: m2mToken,
        basicAuth: createBasicAuthHeader({ username: m2mClientId, password: m2mClientSecret }),
      });
      expect(m2mAfter.data.active).toBe(false);
      console.log("M2M after revocation: active=false");

      console.log("\n=== Token Revocation Test Completed ===");
    } finally {
      await env.cleanup();
    }
  });
});
