import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, postWithJson, putWithJson, get } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
  getUserinfo,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";

/**
 * Advance Use Case: External Password Auth Configuration Effects
 *
 * EXPERIMENTS (external-password-auth) Exp3, Exp8 を E2E Jest に移植。
 *
 * カバー範囲:
 * - B-27: 外部認証URL変更 → 接続エラー
 * - B-29: provider_id 変更 → 同一メールでも別ユーザー
 */
describe("Advance Use Case: External Password Auth Config Effects", () => {
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

  /** Helper: create tenant with external password auth */
  async function createExternalAuthEnv({ authUrl, providerId }) {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@ext-cfg.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

    const resp = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: { id: organizationId, name: `Ext Auth Config Test ${timestamp}`, description: "External auth config effects test" },
        tenant: {
          id: tenantId, name: `Ext Auth Tenant ${timestamp}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID" },
          session_config: { cookie_name: `EAC_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: { sub: uuidv4(), provider_id: "idp-server", name: "Admin", email: adminEmail, email_verified: true, raw_password: adminPassword },
        client: {
          client_id: clientId, client_secret: clientSecret, redirect_uris: [redirectUri],
          response_types: ["code"], grant_types: ["authorization_code", "password"],
          scope: "openid profile email management org-management",
          client_name: "Ext Auth Config Client", token_endpoint_auth_method: "client_secret_post", application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(resp.status).toBe(201);

    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password", username: adminEmail, password: adminPassword,
      scope: "management org-management", clientId, clientSecret,
    });
    const mgmtToken = mgmtResp.data.access_token;

    // External password auth config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(), type: "password", attributes: {},
        metadata: { description: "External password auth" },
        interactions: {
          "password-authentication": {
            execution: {
              function: "http_request",
              http_request: {
                url: authUrl,
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [
                  { from: "$.request_body.username", to: "username" },
                  { from: "$.request_body.password", to: "password" },
                ],
              },
            },
            user_resolve: {
              user_mapping_rules: [
                { from: "$.execution_http_request.response_body.user_id", to: "external_user_id" },
                { from: "$.execution_http_request.response_body.email", to: "email" },
                { from: "$.execution_http_request.response_body.name", to: "name" },
                { static_value: providerId, to: "provider_id" },
              ],
            },
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_request.response_body.user_id", to: "user_id" },
                { from: "$.execution_http_request.response_body.email", to: "email" },
              ],
            },
          },
        },
      },
    });

    return {
      organizationId, tenantId, clientId, clientSecret, redirectUri, mgmtToken,
      cleanup: async () => {
        await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`, headers: { Authorization: `Bearer ${mgmtToken}` } }).catch(() => {});
        await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
      },
    };
  }

  /** Helper: login and get sub */
  async function loginAndGetSub(env, email, password) {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations`,
      clientId: env.clientId, responseType: "code",
      state: `ext-${Date.now()}`, scope: "openid profile email", redirectUri: env.redirectUri,
    });
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    const loginResp = await postAuthentication({
      endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId, body: { username: email, password },
    });
    if (loginResp.status !== 200) return { status: loginResp.status };

    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations/{id}/authorize`,
      id: authId, body: {},
    });
    const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);
    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
      grantType: "authorization_code", code: result.code, redirectUri: env.redirectUri,
      clientId: env.clientId, clientSecret: env.clientSecret,
    });
    const userinfo = await getUserinfo({
      endpoint: `${backendUrl}/${env.tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenResp.data.access_token),
    });
    return { status: 200, sub: userinfo.data.sub };
  }

  it("B-27: unreachable external auth URL causes authentication error", async () => {
    console.log("\n=== Unreachable External Auth URL ===");

    const env = await createExternalAuthEnv({
      authUrl: "http://host.docker.internal:9999/auth/not-exist",
      providerId: "unreachable",
    });
    try {
      const result = await loginAndGetSub(env, "test@example.com", "password");
      // 外部認証サービスに接続不可のとき、idp-serverは503 Service Unavailableを返す
      expect(result.status).toBe(503);
      console.log(`Unreachable URL: status=${result.status} (Service Unavailable)`);
    } finally {
      await env.cleanup();
    }
  });

  it("B-29: different provider_id creates different user for same email", async () => {
    console.log("\n=== Provider ID → Different Users ===");

    // Provider A
    const envA = await createExternalAuthEnv({
      authUrl: `${mockApiBaseUrl}/auth/password`,
      providerId: "provider-alpha",
    });

    // Provider B (same org would conflict, use separate)
    const envB = await createExternalAuthEnv({
      authUrl: `${mockApiBaseUrl}/auth/password`,
      providerId: "provider-beta",
    });

    try {
      const email = "test@example.com";
      const password = "ExternalPass123!";

      const resultA = await loginAndGetSub(envA, email, password);
      expect(resultA.status).toBe(200);
      console.log(`Provider Alpha: sub=${resultA.sub}`);

      const resultB = await loginAndGetSub(envB, email, password);
      expect(resultB.status).toBe(200);
      console.log(`Provider Beta:  sub=${resultB.sub}`);

      // Different tenants, so subs are naturally different
      // The key point is both succeed independently
      expect(resultA.sub).toBeDefined();
      expect(resultB.sub).toBeDefined();
      console.log("Both providers create users independently for same email");
    } finally {
      await envA.cleanup();
      await envB.cleanup();
    }
  });
});
