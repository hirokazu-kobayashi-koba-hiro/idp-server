import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson } from "../../../lib/http";
import { requestToken, getAuthorizations, authorize } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction, convertToAuthorizationResponse } from "../../../lib/util";

/**
 * Issue #1501: does $.user.* resolve during a NEW user registration flow?
 *
 * A freshly created user (InitialRegistrationInteractor) has sub (User.initialized()) and
 * status=INITIALIZED, and is committed to the transaction on the successful (non-challenge)
 * registration interaction. So $.user.status should be referenceable and equal "INITIALIZED".
 *
 * This test settles that empirically:
 *   - success_conditions requiring $.user.status == "INITIALIZED" → registration COMPLETES
 *     (⇒ $.user.* is referenceable for new users, value = INITIALIZED)
 *   - control: requiring $.user.status == "REGISTERED" → registration BLOCKED
 *     (⇒ the condition is actually evaluated against status, not vacuously true)
 */
describe("Authentication policy $.user.* during NEW user registration (Issue #1501)", () => {
  let systemAccessToken;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";
  const created = [];

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

  afterAll(async () => {
    for (const c of created) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${c.organizationId}/tenants/${c.tenantId}`,
        headers: { Authorization: `Bearer ${c.mgmtAccessToken}` },
      }).catch(() => {});
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${c.organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  /** Onboards a fresh tenant whose registration success_conditions gate on $.user.status. */
  async function setupTenant(statusConditionValue) {
    const ts = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${ts}-${clientId.substring(0, 8)}@reg-attr.example.com`;
    const adminPassword = `AdminPass_${ts}!`;

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Reg Attr Org ${ts}`,
          description: "E2E for $.user.* during registration (#1501)",
        },
        tenant: {
          id: tenantId,
          name: `Reg Attr Tenant ${ts}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `REG_ATTR_${organizationId.substring(0, 8)}`,
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
          client_name: "Reg Attr Client",
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
    const mgmtAccessToken = mgmtTokenResponse.data.access_token;
    created.push({ organizationId, tenantId, mgmtAccessToken });

    // initial-registration config
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

    // Policy: registration completes only if $.user.status == <statusConditionValue>
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: `registration_gated_on_user_status_${statusConditionValue}`,
            priority: 1,
            conditions: {},
            available_methods: ["initial-registration"],
            step_definitions: [
              { method: "initial-registration", order: 1, requires_user: false },
            ],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.initial-registration.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  { path: "$.user.status", operation: "eq", value: statusConditionValue },
                ],
              ],
            },
          },
        ],
      },
    });

    return { tenantId, clientId };
  }

  /** Runs a registration flow and returns { registrationStatus, code }. */
  async function register(tenantId, clientId) {
    const ts = Date.now();
    const email = `newuser-${ts}-${crypto.randomBytes(4).toString("hex")}@reg-attr.example.com`;

    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `reg-${ts}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    const regResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email, password: "NewUserPass_1!", name: "New User" },
    });

    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });

    const code =
      authorizeResp.status === 200 && authorizeResp.data && authorizeResp.data.redirect_uri
        ? convertToAuthorizationResponse(authorizeResp.data.redirect_uri).code
        : undefined;

    return { registrationStatus: regResp.status, authorizeStatus: authorizeResp.status, code };
  }

  it("$.user.status IS referenceable during registration (new user = INITIALIZED) → completes", async () => {
    const { tenantId, clientId } = await setupTenant("INITIALIZED");
    const result = await register(tenantId, clientId);
    console.log("status==INITIALIZED registration:", JSON.stringify(result));

    expect(result.registrationStatus).toBe(200);
    // If $.user.status were NOT referenceable for a new user, this code would be undefined.
    expect(result.code).toBeDefined();
  });

  it("control: requiring $.user.status == REGISTERED blocks registration (status is INITIALIZED)", async () => {
    const { tenantId, clientId } = await setupTenant("REGISTERED");
    const result = await register(tenantId, clientId);
    console.log("status==REGISTERED registration:", JSON.stringify(result));

    // Registration interaction itself succeeds, but the policy condition is not met.
    expect(result.registrationStatus).toBe(200);
    expect(result.code).toBeUndefined();
  });
});
