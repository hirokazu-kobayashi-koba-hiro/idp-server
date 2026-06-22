import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson, patchWithJson, get } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { requestAuthorizations } from "../../../oauth/request";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Issue #1501: authentication policy `success_conditions` can gate on user attributes using the
 * multi-value operators `in` (on `$.user.status`) and `contains` (on `$.user.roles`). This is the
 * replacement for the dropped `registered` boolean: instead of a derived flag, the policy author
 * lists exactly the statuses/roles they mean.
 *
 * One policy combines both operators as two OR groups (`any_of`):
 *   [ password≥1, $.user.status in ["REGISTERED","IDENTITY_VERIFIED"] ]   // group 1 — `in`
 *   [ password≥1, $.user.roles contains "<role>"                     ]   // group 2 — `contains`
 *
 * All users pass the password step (the interaction returns 200), so the only thing that differs is
 * the user attribute each group gates on:
 *   - REGISTERED, no role                       → group 1 (in)       → code issued
 *   - IDENTITY_VERIFIED, no role                → group 1 (in)       → code issued (in matches EITHER)
 *   - IDENTITY_VERIFICATION_REQUIRED, WITH role → group 2 (contains) → code issued
 *       (status is NOT in the list, so this is rescued purely by `roles contains`)
 *   - IDENTITY_VERIFICATION_REQUIRED, no role   → neither group      → no code (control)
 *
 * The last two users share the same (active) status and differ only by role membership, so the
 * `contains` effect is isolated. Full-HTTP-flow counterpart of MfaConditionEvaluatorTest
 * (#statusInList / #rolesContains).
 */
describe("Authentication policy multi-value conditions: $.user.status `in` / $.user.roles `contains` (Issue #1501)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  let roleName;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

  const timestamp = Date.now();
  const registeredEmail = `registered-${timestamp}@user-attr.example.com`;
  const verifiedEmail = `verified-${timestamp}@user-attr.example.com`;
  const roleRescueEmail = `role-rescue-${timestamp}@user-attr.example.com`;
  const noRoleEmail = `no-role-${timestamp}@user-attr.example.com`;
  const password = "UserAttr_1!";

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
          name: `User Attr Multi Op Org ${timestamp}`,
          description: "E2E for auth policy in/contains multi-value conditions (#1501)",
        },
        tenant: {
          id: tenantId,
          name: `User Attr Multi Op Tenant ${timestamp}`,
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
          client_name: "User Attr Multi Op Client",
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

    // An existing tenant role to drive `$.user.roles contains`. PolicyEvaluationUserContextCreator
    // projects roles as role NAMES, so the condition value is this role's name (taken dynamically to
    // avoid depending on a specific seeded role name, and to avoid creating a role+permission chain).
    const rolesResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(rolesResponse.status).toBe(200);
    expect(rolesResponse.data.list.length).toBeGreaterThan(0);
    const role = rolesResponse.data.list[0];
    roleName = role.name;

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

    // Policy: password (1st factor) AND ( status in [...] OR roles contains <role> )
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_with_status_in_or_role_contains",
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
                    path: "$.user.status",
                    operation: "in",
                    value: ["REGISTERED", "IDENTITY_VERIFIED"],
                  },
                ],
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: "$.user.roles",
                    operation: "contains",
                    value: roleName,
                  },
                ],
              ],
            },
          },
        ],
      },
    });

    await createUser(registeredEmail, password, "REGISTERED", null);
    await createUser(verifiedEmail, password, "IDENTITY_VERIFIED", null);
    await createUser(roleRescueEmail, password, "IDENTITY_VERIFICATION_REQUIRED", role);
    await createUser(noRoleEmail, password, "IDENTITY_VERIFICATION_REQUIRED", null);
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

  /**
   * Creates a user (always born REGISTERED), optionally assigns a role, and—when a different target
   * status is requested—moves it there with the management PATCH (the mechanism exercised by
   * security/invalid_user_status_authorization.test.js).
   */
  async function createUser(email, password, targetStatus, role) {
    const sub = uuidv4();
    const body = {
      sub,
      provider_id: "idp-server",
      name: email,
      preferred_username: email,
      email,
      email_verified: true,
      raw_password: password,
      status: "REGISTERED",
    };
    if (role) {
      body.roles = [{ role_id: role.id, role_name: role.name }];
    }
    const resp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body,
    });
    expect(resp.status).toBe(201);

    if (targetStatus !== "REGISTERED") {
      const patchResp = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${sub}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
        body: { status: targetStatus },
      });
      expect(patchResp.status).toBe(200);
      expect(patchResp.data.diff).toHaveProperty("status");
    }
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
        // The password verification itself succeeds for all users; only the policy gate differs.
        expect(resp.status).toBe(200);
      },
    });
  }

  it("ALLOWS when $.user.status is REGISTERED (matched by `in`)", async () => {
    const { status, authorizationResponse } = await passwordLogin(registeredEmail, password);
    console.log("REGISTERED login:", status, JSON.stringify(authorizationResponse));

    expect(status).toBe(200);
    expect(authorizationResponse.code).toBeDefined();
  });

  it("ALLOWS when $.user.status is IDENTITY_VERIFIED (the other listed status, `in`)", async () => {
    const { status, authorizationResponse } = await passwordLogin(verifiedEmail, password);
    console.log("IDENTITY_VERIFIED login:", status, JSON.stringify(authorizationResponse));

    // `in` matches EITHER status: one condition covers multiple statuses (no `registered` flag).
    expect(status).toBe(200);
    expect(authorizationResponse.code).toBeDefined();
  });

  it("ALLOWS when status is NOT listed but $.user.roles contains the role (`contains` rescue)", async () => {
    const { status, authorizationResponse } = await passwordLogin(roleRescueEmail, password);
    console.log("role-rescue login:", status, JSON.stringify(authorizationResponse));

    // status (IDENTITY_VERIFICATION_REQUIRED) fails the `in` group; only `roles contains` lets it pass.
    expect(status).toBe(200);
    expect(authorizationResponse.code).toBeDefined();
  });

  it("REJECTS when neither $.user.status is listed nor $.user.roles contains the role", async () => {
    const { authorizationResponse } = await passwordLogin(noRoleEmail, password);
    console.log("no-role login:", JSON.stringify(authorizationResponse));

    // Same active status as the rescued user, differing only by role membership: the missing code is
    // due to both multi-value conditions failing, not a password failure nor an inactive-user reject.
    expect(authorizationResponse.code).toBeUndefined();
  });
});
