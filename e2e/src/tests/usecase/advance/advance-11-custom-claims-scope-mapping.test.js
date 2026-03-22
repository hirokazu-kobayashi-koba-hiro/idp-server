import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
  getUserinfo,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";

/**
 * Advance Use Case: custom_claims_scope_mapping
 *
 * EXPERIMENTS-authorization-server.md Exp10 を E2E Jest に移植。
 *
 * カバー範囲 (GAP-ANALYSIS B-11):
 * - custom_claims_scope_mapping=true + claims:status スコープ → UserInfo に status クレーム
 * - custom_claims_scope_mapping=false → claims:status が効かない
 */
describe("Advance Use Case: Custom Claims Scope Mapping", () => {
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

  /** Helper: create tenant with custom_claims_scope_mapping setting */
  async function createTenantWithCustomClaims({ enabled }) {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@custom-claims.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

    const onboardingResp = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: { id: organizationId, name: `Custom Claims Test ${timestamp}`, description: `custom_claims_scope_mapping=${enabled}` },
        tenant: {
          id: tenantId, name: `Custom Claims Tenant ${timestamp}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `CC_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
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
          scopes_supported: ["openid", "profile", "email", "claims:status", "management", "org-management"],
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email", "email_verified", "status"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: {
            access_token_type: "JWT",
            custom_claims_scope_mapping: enabled,
          },
        },
        user: {
          sub: uuidv4(), provider_id: "idp-server", name: "Admin",
          email: adminEmail, email_verified: true, raw_password: adminPassword,
        },
        client: {
          client_id: clientId, client_secret: clientSecret, redirect_uris: [redirectUri],
          response_types: ["code"], grant_types: ["authorization_code", "password"],
          scope: "openid profile email claims:status management org-management",
          client_name: "Custom Claims Client", token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardingResp.status !== 201) {
      console.error("Onboarding failed:", JSON.stringify(onboardingResp.data, null, 2));
    }
    expect(onboardingResp.status).toBe(201);

    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password", username: adminEmail, password: adminPassword,
      scope: "management org-management", clientId, clientSecret,
    });
    const mgmtToken = mgmtResp.data.access_token;

    // Auth configs
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" },
        interactions: { "password-authentication": { request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } }, pre_hook: {}, execution: { function: "password_verification" }, post_hook: {}, response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }] } } },
      },
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(), type: "initial-registration", attributes: {}, metadata: {},
        interactions: { "initial-registration": { request: { schema: { type: "object", required: ["email", "password", "name"], properties: { name: { type: "string" }, email: { type: "string", format: "email" }, password: { type: "string", minLength: 8 } } } } } },
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

  it("should include status in UserInfo when custom_claims_scope_mapping is enabled", async () => {
    console.log("\n=== custom_claims_scope_mapping=true ===");

    const env = await createTenantWithCustomClaims({ enabled: true });
    try {
      // Register user + login with claims:status scope
      const authResp = await getAuthorizations({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations`,
        clientId: env.clientId, responseType: "code",
        state: `cc-on-${Date.now()}`, scope: "openid profile email claims:status",
        redirectUri: env.redirectUri,
      });
      const { params } = convertNextAction(authResp.headers.location);
      const authId = params.get("id");

      await postWithJson({
        url: `${backendUrl}/${env.tenantId}/v1/authorizations/${authId}/initial-registration`,
        body: { email: `cc-on-${Date.now()}@test.com`, password: "CcOnPass_1!", name: "CC User" },
      });
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
      expect(tokenResp.status).toBe(200);

      const userinfo = await getUserinfo({
        endpoint: `${backendUrl}/${env.tenantId}/v1/userinfo`,
        authorizationHeader: createBearerHeader(tokenResp.data.access_token),
      });
      expect(userinfo.status).toBe(200);
      expect(userinfo.data.sub).toBeDefined();
      // status should be present (e.g. "REGISTERED")
      expect(userinfo.data.status).toBeDefined();
      console.log(`UserInfo status: ${userinfo.data.status} (custom claim present)`);
    } finally {
      await env.cleanup();
    }
  });

  it("should NOT include status in UserInfo when custom_claims_scope_mapping is disabled", async () => {
    console.log("\n=== custom_claims_scope_mapping=false ===");

    const env = await createTenantWithCustomClaims({ enabled: false });
    try {
      const authResp = await getAuthorizations({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations`,
        clientId: env.clientId, responseType: "code",
        state: `cc-off-${Date.now()}`, scope: "openid profile email claims:status",
        redirectUri: env.redirectUri,
      });
      const { params } = convertNextAction(authResp.headers.location);
      const authId = params.get("id");

      await postWithJson({
        url: `${backendUrl}/${env.tenantId}/v1/authorizations/${authId}/initial-registration`,
        body: { email: `cc-off-${Date.now()}@test.com`, password: "CcOffPass_1!", name: "CC User Off" },
      });
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
      expect(tokenResp.status).toBe(200);

      const userinfo = await getUserinfo({
        endpoint: `${backendUrl}/${env.tenantId}/v1/userinfo`,
        authorizationHeader: createBearerHeader(tokenResp.data.access_token),
      });
      expect(userinfo.status).toBe(200);
      expect(userinfo.data.sub).toBeDefined();
      // status should NOT be present
      expect(userinfo.data.status).toBeUndefined();
      console.log("UserInfo status: undefined (custom claim absent)");
    } finally {
      await env.cleanup();
    }
  });
});
