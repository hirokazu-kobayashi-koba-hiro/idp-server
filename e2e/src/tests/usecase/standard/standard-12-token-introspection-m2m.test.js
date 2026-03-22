import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
  inspectToken,
  revokeToken,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBasicAuthHeader,
} from "../../../lib/util";

/**
 * Standard Use Case: M2M (client_credentials) + Token Introspection + Revocation
 *
 * verify.sh (third-party) の Phase 1/2 を E2E Jest に移植。
 *
 * カバー範囲 (GAP-ANALYSIS A-15, A-16):
 * - M2M client_credentials Grant
 * - Token Introspection (active: true/false)
 * - Web Client (client_secret_basic) 認可コードフロー
 * - Token Revocation → Introspection で active: false 確認
 * - Refresh Token
 */
describe("Standard Use Case: M2M + Token Introspection", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let webClientId;
  let webClientSecret;
  let m2mClientId;
  let m2mClientSecret;
  let mgmtAccessToken;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

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

    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    const adminUserId = uuidv4();
    webClientId = uuidv4();
    webClientSecret = `web-secret-${crypto.randomBytes(16).toString("hex")}`;
    m2mClientId = uuidv4();
    m2mClientSecret = `m2m-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@m2m-test.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    // Create org with web client
    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: {
          id: organizationId,
          name: `M2M Test Org ${timestamp}`,
          description: "E2E test for M2M and Token Introspection",
        },
        tenant: {
          id: tenantId,
          name: `M2M Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
          },
          session_config: {
            cookie_name: `M2M_SESSION_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: {
            allow_origins: [backendUrl],
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: [
            "client_secret_post",
            "client_secret_basic",
          ],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: [
            "authorization_code",
            "refresh_token",
            "client_credentials",
            "password",
          ],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: [
            "openid",
            "profile",
            "email",
            "api:read",
            "api:write",
            "management",
            "org-management",
          ],
          claims_supported: [
            "sub",
            "iss",
            "auth_time",
            "acr",
            "name",
            "email",
            "email_verified",
          ],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: {
            access_token_type: "JWT",
          },
        },
        user: {
          sub: adminUserId,
          provider_id: "idp-server",
          name: "Admin User",
          email: adminEmail,
          email_verified: true,
          raw_password: adminPassword,
        },
        client: {
          client_id: webClientId,
          client_secret: webClientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email api:read management org-management",
          client_name: "Web Application",
          token_endpoint_auth_method: "client_secret_basic",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardingResponse.status !== 201) {
      console.error(
        "Onboarding failed:",
        JSON.stringify(onboardingResponse.data, null, 2)
      );
    }
    expect(onboardingResponse.status).toBe(201);

    // Get management token
    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management org-management",
      basicAuth: createBasicAuthHeader({
        username: webClientId,
        password: webClientSecret,
      }),
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    // Register M2M client
    const m2mClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        client_id: m2mClientId,
        client_secret: m2mClientSecret,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["client_credentials"],
        scope: "api:read api:write",
        client_name: "M2M Service Client",
        token_endpoint_auth_method: "client_secret_basic",
        application_type: "web",
      },
    });
    if (m2mClientResponse.status !== 201) {
      console.error("M2M client creation failed:", JSON.stringify(m2mClientResponse.data, null, 2));
    }
    expect(m2mClientResponse.status).toBe(201);

    // Register authentication configs
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
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
                properties: {
                  username: { type: "string" },
                  password: { type: "string" },
                },
                required: ["username", "password"],
              },
            },
            pre_hook: {},
            execution: { function: "password_verification" },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.user_id", to: "user_id" },
                { from: "$.username", to: "username" },
                { from: "$.error", to: "error" },
                { from: "$.error_description", to: "error_description" },
              ],
            },
          },
        },
      },
    });

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "initial-registration",
        attributes: {},
        metadata: {},
        interactions: {
          "initial-registration": {
            request: {
              schema: {
                type: "object",
                required: ["email", "password", "name"],
                properties: {
                  name: { type: "string", maxLength: 255 },
                  email: { type: "string", format: "email", maxLength: 255 },
                  password: { type: "string", maxLength: 64, minLength: 8 },
                },
              },
            },
          },
        },
      },
    });
  });

  afterAll(async () => {
    if (mgmtAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${m2mClientId}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      }).catch(() => {});
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      }).catch(() => {});
    }
    if (systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("should obtain M2M token via client_credentials and verify with Introspection", async () => {
    console.log("\n=== M2M client_credentials Grant ===");

    // Step 1: Get M2M token
    console.log("Step 1: M2M client_credentials grant");
    const m2mTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "client_credentials",
      scope: "api:read api:write",
      basicAuth: createBasicAuthHeader({
        username: m2mClientId,
        password: m2mClientSecret,
      }),
    });
    expect(m2mTokenResponse.status).toBe(200);
    expect(m2mTokenResponse.data.access_token).toBeDefined();
    expect(m2mTokenResponse.data.token_type).toBe("Bearer");
    expect(m2mTokenResponse.data.expires_in).toBeGreaterThan(0);
    // client_credentials should not return refresh_token
    const m2mAccessToken = m2mTokenResponse.data.access_token;
    console.log("M2M token obtained");

    // Step 2: Introspect M2M token → active: true
    console.log("\nStep 2: Introspect M2M token (active: true)");
    const introspectResponse = await inspectToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
      token: m2mAccessToken,
      basicAuth: createBasicAuthHeader({
        username: m2mClientId,
        password: m2mClientSecret,
      }),
    });
    expect(introspectResponse.status).toBe(200);
    expect(introspectResponse.data.active).toBe(true);
    expect(introspectResponse.data.client_id).toBe(m2mClientId);
    expect(introspectResponse.data.scope).toContain("api:read");
    expect(introspectResponse.data.scope).toContain("api:write");
    console.log(
      `Introspection: active=${introspectResponse.data.active}, scope=${introspectResponse.data.scope}, client_id=${introspectResponse.data.client_id}`
    );

    // Step 3: Request partial scope
    console.log("\nStep 3: M2M with partial scope (api:read only)");
    const partialTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "client_credentials",
      scope: "api:read",
      basicAuth: createBasicAuthHeader({
        username: m2mClientId,
        password: m2mClientSecret,
      }),
    });
    expect(partialTokenResponse.status).toBe(200);

    const partialIntrospect = await inspectToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
      token: partialTokenResponse.data.access_token,
      basicAuth: createBasicAuthHeader({
        username: m2mClientId,
        password: m2mClientSecret,
      }),
    });
    expect(partialIntrospect.status).toBe(200);
    expect(partialIntrospect.data.active).toBe(true);
    expect(partialIntrospect.data.scope).toContain("api:read");
    console.log(`Partial scope introspection: scope=${partialIntrospect.data.scope}`);

    console.log("\n=== M2M client_credentials Test Completed ===");
  });

  it("should complete Web Client auth code flow with client_secret_basic and Introspection", async () => {
    console.log("\n=== Web Client (client_secret_basic) Flow ===");

    // Step 1: Register user + auth code flow
    console.log("Step 1: Authorization Code Flow with client_secret_basic");
    const testEmail = `web-${Date.now()}@m2m-test.example.com`;
    const testPassword = "WebTestPass_1!";

    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: webClientId,
      responseType: "code",
      state: `web-flow-${Date.now()}`,
      scope: "openid profile email api:read",
      redirectUri,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: testEmail, password: testPassword, name: "Web Test User" },
    });

    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    const result = convertToAuthorizationResponse(
      authorizeResp.data.redirect_uri
    );
    expect(result.code).toBeDefined();

    // Token exchange with client_secret_basic (HTTP Basic Auth)
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: result.code,
      redirectUri,
      basicAuth: createBasicAuthHeader({
        username: webClientId,
        password: webClientSecret,
      }),
    });
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.access_token).toBeDefined();
    expect(tokenResponse.data.refresh_token).toBeDefined();
    const accessToken = tokenResponse.data.access_token;
    const refreshToken = tokenResponse.data.refresh_token;
    console.log("Token obtained via client_secret_basic");

    // Step 2: Introspect Web Client token
    console.log("\nStep 2: Introspect Web Client token");
    const introspectResponse = await inspectToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
      token: accessToken,
      basicAuth: createBasicAuthHeader({
        username: webClientId,
        password: webClientSecret,
      }),
    });
    expect(introspectResponse.status).toBe(200);
    expect(introspectResponse.data.active).toBe(true);
    expect(introspectResponse.data.client_id).toBe(webClientId);
    console.log(
      `Introspection: active=${introspectResponse.data.active}, client_id=${introspectResponse.data.client_id}`
    );

    // Step 3: Refresh token
    console.log("\nStep 3: Refresh token");
    const refreshResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken,
      basicAuth: createBasicAuthHeader({
        username: webClientId,
        password: webClientSecret,
      }),
    });
    expect(refreshResponse.status).toBe(200);
    expect(refreshResponse.data.access_token).toBeDefined();
    expect(refreshResponse.data.access_token).not.toBe(accessToken);
    console.log("Refresh token successful");

    console.log("\n=== Web Client Flow Completed ===");
  });

  it("should mark token as inactive after revocation", async () => {
    console.log("\n=== Token Revocation Test ===");

    // Step 1: Get M2M token
    console.log("Step 1: Get M2M token");
    const m2mTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "client_credentials",
      scope: "api:read",
      basicAuth: createBasicAuthHeader({
        username: m2mClientId,
        password: m2mClientSecret,
      }),
    });
    expect(m2mTokenResponse.status).toBe(200);
    const m2mAccessToken = m2mTokenResponse.data.access_token;

    // Step 2: Introspect before revocation → active: true
    console.log("Step 2: Introspect before revocation");
    const beforeRevoke = await inspectToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
      token: m2mAccessToken,
      basicAuth: createBasicAuthHeader({
        username: m2mClientId,
        password: m2mClientSecret,
      }),
    });
    expect(beforeRevoke.status).toBe(200);
    expect(beforeRevoke.data.active).toBe(true);
    console.log(`Before revocation: active=${beforeRevoke.data.active}`);

    // Step 3: Revoke token
    console.log("\nStep 3: Revoke token");
    const revokeResponse = await revokeToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
      token: m2mAccessToken,
      basicAuth: createBasicAuthHeader({
        username: m2mClientId,
        password: m2mClientSecret,
      }),
    });
    expect(revokeResponse.status).toBe(200);
    console.log("Token revoked");

    // Step 4: Introspect after revocation → active: false
    console.log("\nStep 4: Introspect after revocation");
    const afterRevoke = await inspectToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
      token: m2mAccessToken,
      basicAuth: createBasicAuthHeader({
        username: m2mClientId,
        password: m2mClientSecret,
      }),
    });
    expect(afterRevoke.status).toBe(200);
    expect(afterRevoke.data.active).toBe(false);
    console.log(`After revocation: active=${afterRevoke.data.active}`);

    console.log("\n=== Token Revocation Test Completed ===");
  });
});
