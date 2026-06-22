import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { requestAuthorizations } from "../../../oauth/request";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Issue #1501: authentication policy `success_conditions` can reference the transaction user as
 * `$.user.*`.
 *
 * Policy: password (1st factor) AND `$.user.custom_properties.tier == "premium"`. Both users pass
 * the password step (the interaction returns 200), so the only difference is the user attribute the
 * policy gates on:
 *   - premium user  → success_conditions satisfied → authorization completes (code issued)
 *   - basic user    → success_conditions NOT satisfied → no code (gated by $.user.*, not by password)
 *
 * This exercises the full HTTP flow through MfaConditionEvaluator + PolicyEvaluationUserContextCreator.
 */
describe("Authentication policy condition on user attribute $.user.* (Issue #1501)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

  const timestamp = Date.now();
  const premiumEmail = `premium-${timestamp}@user-attr.example.com`;
  const premiumPassword = "PremiumPass_1!";
  const basicEmail = `basic-${timestamp}@user-attr.example.com`;
  const basicPassword = "BasicPass_2!";

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

    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@user-attr.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `User Attr Cond Org ${timestamp}`,
          description: "E2E for auth policy user-attribute condition (#1501)",
        },
        tenant: {
          id: tenantId,
          name: `User Attr Cond Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `USER_ATTR_${organizationId.substring(0, 8)}`,
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
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email", "email_verified"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: "Admin User",
          email: adminEmail,
          email_verified: true,
          raw_password: adminPassword,
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management",
          client_name: "User Attr Cond Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management",
      clientId,
      clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    // password authentication config
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
                properties: { username: { type: "string" }, password: { type: "string" } },
                required: ["username", "password"],
              },
            },
            execution: { function: "password_verification" },
            response: { body_mapping_rules: [] },
          },
        },
      },
    });

    // Policy: password (1st factor) AND $.user.custom_properties.tier == "premium"
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_with_premium_tier",
            priority: 1,
            conditions: {},
            available_methods: ["password"],
            step_definitions: [{ method: "password", order: 1, requires_user: false }],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: "$.user.custom_properties.tier",
                    operation: "eq",
                    value: "premium",
                  },
                ],
              ],
            },
          },
        ],
      },
    });

    await createUser(premiumEmail, premiumPassword, "premium");
    await createUser(basicEmail, basicPassword, "basic");
  });

  afterAll(async () => {
    if (mgmtAccessToken) {
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

  async function createUser(email, password, tier) {
    const sub = uuidv4();
    const resp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        sub,
        provider_id: "idp-server",
        name: email,
        preferred_username: email,
        email,
        email_verified: true,
        raw_password: password,
        custom_properties: { tier },
        status: "REGISTERED",
      },
    });
    expect(resp.status).toBe(201);
    return sub;
  }

  /** Runs a password-only authorization flow; the password step always returns 200. */
  function passwordLogin(email, password) {
    return requestAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
      clientId,
      responseType: "code",
      state: `login-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
      prompt: "login",
      user: { username: email, password },
      interaction: async (id, user) => {
        const resp = await postWithJson({
          url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/password-authentication`,
          body: { username: user.username, password: user.password },
        });
        // The password verification itself succeeds for both users; only the policy gate differs.
        expect(resp.status).toBe(200);
      },
    });
  }

  it("ALLOWS authorization when $.user.custom_properties.tier satisfies success_conditions (premium)", async () => {
    const { status, authorizationResponse } = await passwordLogin(premiumEmail, premiumPassword);
    console.log("premium login:", status, JSON.stringify(authorizationResponse));

    expect(status).toBe(200);
    expect(authorizationResponse.code).toBeDefined();
  });

  it("REJECTS authorization when the user attribute does not satisfy success_conditions (basic)", async () => {
    const { authorizationResponse } = await passwordLogin(basicEmail, basicPassword);
    console.log("basic login:", JSON.stringify(authorizationResponse));

    // Password succeeded (asserted in the interaction), so the absence of a code is due to the
    // $.user.custom_properties.tier condition, not a password failure.
    expect(authorizationResponse.code).toBeUndefined();
  });
});
