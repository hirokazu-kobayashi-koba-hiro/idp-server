import { describe, expect, it, beforeAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction } from "../../../lib/util";

/**
 * Advance Use Case: External Password Auth — allow_registration enforcement
 *
 * Issue #1538 回帰テスト。
 *
 * 単独 password ステップ (requires_user:false) で外部認証 API が成功 (200) を返したとき、
 * step_definitions[].allow_registration の値によって JIT 作成可否が制御されることを検証する。
 *
 * - allow_registration:false → 対応 user 未登録なら認証失敗 (400 user_not_found)。
 *   孤立した INITIALIZED user を JIT 作成しない。
 * - allow_registration:true  → 従来どおり JIT 作成して成功 (200)。
 *
 * いずれも fresh tenant を使うため、mock が返す external_user_id は事前未登録となる。
 *
 * Prerequisites:
 * - Mock server (Mockoon) running at host.docker.internal:4000 with POST /auth/password
 */
describe("Advance Use Case: External Password Auth allow_registration (Issue #1538)", () => {
  let systemAccessToken;
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
  });

  /**
   * Create a tenant with external password authentication and an oauth-flow policy whose
   * single password step has the given allow_registration value.
   */
  async function createTenantWithPolicy(allowRegistration) {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}-${allowRegistration}@ext-reg.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Ext Auth allow_registration ${allowRegistration} ${timestamp}`,
          description: "Issue #1538 allow_registration enforcement test",
        },
        tenant: {
          id: tenantId,
          name: `Ext Auth Reg Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID",
          },
          session_config: {
            cookie_name: `EXT_REG_${tenantId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
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
          scopes_supported: ["openid", "profile", "email", "management"],
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
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
          grant_types: ["authorization_code", "password"],
          scope: "openid profile email management",
          client_name: "Ext Auth Reg Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management",
      clientId,
      clientSecret,
    });
    expect(mgmtResp.status).toBe(200);
    const mgmtToken = mgmtResp.data.access_token;

    // External password authentication config (http_request to mock server)
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { description: "External password auth (Issue #1538)" },
        interactions: {
          "password-authentication": {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/auth/password`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.request_body.username", to: "username" },
                  { from: "$.request_body.password", to: "password" },
                ],
              },
            },
            user_resolve: {
              user_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body.user_id",
                  to: "external_user_id",
                },
                {
                  from: "$.execution_http_request.response_body.email",
                  to: "email",
                },
                {
                  from: "$.execution_http_request.response_body.name",
                  to: "name",
                },
                { static_value: "mock-external-auth", to: "provider_id" },
              ],
            },
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body.user_id",
                  to: "user_id",
                },
                {
                  from: "$.execution_http_request.response_body.email",
                  to: "email",
                },
              ],
            },
          },
        },
      },
    });

    // Authentication policy: single password step, 1st factor, allow_registration controlled
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: `external_password_allow_registration_${allowRegistration}`,
            priority: 1,
            conditions: {},
            available_methods: ["password"],
            step_definitions: [
              {
                method: "password",
                order: 1,
                requires_user: false,
                allow_registration: allowRegistration,
                user_identity_source: "email",
              },
            ],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                ],
              ],
            },
          },
        ],
      },
    });

    return {
      organizationId,
      tenantId,
      clientId,
      clientSecret,
      cleanup: async () => {
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
          headers: { Authorization: `Bearer ${mgmtToken}` },
        }).catch(() => {});
        await deletion({
          url: `${backendUrl}/v1/management/orgs/${organizationId}`,
          headers: { Authorization: `Bearer ${systemAccessToken}` },
        }).catch(() => {});
      },
    };
  }

  /** Start an authorization-code flow and attempt the external password login. */
  async function attemptExternalLogin(env, username, password) {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations`,
      clientId: env.clientId,
      responseType: "code",
      state: `ext-reg-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    return postAuthentication({
      endpoint: `${backendUrl}/${env.tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username, password },
    });
  }

  it("should reject external password auth when allow_registration=false and the user is not pre-registered", async () => {
    const env = await createTenantWithPolicy(false);
    try {
      // Mock returns 200 for these credentials, but the resolved external_user_id is not
      // pre-registered in this fresh tenant. allow_registration:false must reject instead of
      // JIT-creating an orphan user.
      const loginResp = await attemptExternalLogin(
        env,
        "test@example.com",
        "ExternalPass123!"
      );
      console.log(
        "allow_registration=false login:",
        loginResp.status,
        JSON.stringify(loginResp.data)
      );

      expect(loginResp.status).not.toBe(200);
      expect(loginResp.status).toBe(400);
      expect(loginResp.data.error).toBe("user_not_found");
    } finally {
      await env.cleanup();
    }
  });

  it("should allow JIT creation when allow_registration=true", async () => {
    const env = await createTenantWithPolicy(true);
    try {
      const loginResp = await attemptExternalLogin(
        env,
        "test@example.com",
        "ExternalPass123!"
      );
      console.log(
        "allow_registration=true login:",
        loginResp.status,
        JSON.stringify(loginResp.data)
      );

      expect(loginResp.status).toBe(200);
      expect(loginResp.data.user).toBeDefined();
    } finally {
      await env.cleanup();
    }
  });
});
