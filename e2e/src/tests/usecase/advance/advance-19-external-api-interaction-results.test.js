import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken, getAuthorizations, authorize } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction } from "../../../lib/util";

/**
 * Advance Use Case: per-interaction authentication results (Issue #1771)
 *
 * `external-api-authentication` holds several interactions in one configuration, and they all reach
 * the server through the same endpoint path. Their results used to accumulate under the single key
 * `external-api-authentication`, so a policy could only ask about the total:
 *
 *   $.external-api-authentication.success_count >= 3
 *
 * That is satisfied by calling **one** interaction three times, which means the interactions a
 * multi-step flow actually requires can be skipped and the authentication still completes.
 *
 * The results now carry a per-interaction breakdown, so a policy can require each step by name.
 * Two things are pinned:
 *
 *   1. Running one step three times does NOT satisfy a policy that names three steps.
 *   2. Running each step once does.
 *
 * The type-level total remains the sum, so conditions written before this keep working.
 */
describe("Advance Use Case: External API per-interaction results (Issue #1771)", () => {
  let systemAccessToken;
  let mgmtAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  const redirectUri = "https://app.example.com/callback";

  // Three interactions, each hitting a different mock endpoint. /auth/password echoes the submitted
  // username, which gives each step a distinguishable response.
  const STEP_A = "step-a";
  const STEP_B = "step-b";
  const STEP_C = "step-c";

  // Only the first step resolves the user. The later ones are the "補助判定型" shape (no
  // user_resolve), which is the multi-step flow the issue describes: one step establishes who it is,
  // the rest add checks that must also have run.
  const stepInteraction = (label, resolvesUser = false) => ({
    request: {
      schema: {
        type: "object",
        required: ["interaction"],
        properties: { interaction: { type: "string" } },
      },
    },
    execution: {
      function: "http_request",
      http_request: {
        url: `${mockApiBaseUrl}/auth/password`,
        method: "POST",
        header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
        body_mapping_rules: [{ static_value: label, to: "username" }],
      },
    },
    ...(resolvesUser
      ? {
          user_resolve: {
            user_mapping_rules: [
              {
                from: "$.execution_http_request.response_body.user_id",
                to: "external_user_id",
              },
              { from: "$.execution_http_request.response_body.email", to: "email" },
              { static_value: "mock-external-api", to: "provider_id" },
            ],
          },
        }
      : {}),
    response: {
      body_mapping_rules: [
        { from: "$.execution_http_request.response_body.user_id", to: "reached" },
      ],
    },
  });

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@interaction-results.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const systemTokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(systemTokenResponse.status).toBe(200);
    systemAccessToken = systemTokenResponse.data.access_token;

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Interaction Results Org ${timestamp}`,
          description: "E2E for #1771",
        },
        tenant: {
          id: tenantId,
          name: `Interaction Results Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub", "name", "email", "email_verified"],
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
          grant_types: ["authorization_code", "password"],
          scope: "openid profile email management org-management",
          client_name: "Interaction Results Client",
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
      scope: "management org-management",
      clientId,
      clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    const authConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "external-api-authentication",
        attributes: {},
        metadata: { type: "external", description: "three interactions in one config" },
        interactions: {
          [STEP_A]: stepInteraction(STEP_A, true),
          [STEP_B]: stepInteraction(STEP_B),
          [STEP_C]: stepInteraction(STEP_C),
        },
      },
    });
    expect(authConfigResponse.status).toBe(201);

    // The point of the issue: each step is required by name. Before the breakdown existed the only
    // expressible condition was the total, which one step could satisfy on its own.
    const policyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "all_three_interactions_required",
            priority: 1,
            conditions: {},
            available_methods: ["external-api"],
            step_definitions: [
              {
                method: "external-api",
                order: 1,
                requires_user: false,
                allow_registration: true,
                user_identity_source: "email",
              },
            ],
            success_conditions: {
              any_of: [
                [
                  {
                    path: `$.external-api-authentication.interactions.${STEP_A}.success_count`,
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: `$.external-api-authentication.interactions.${STEP_B}.success_count`,
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: `$.external-api-authentication.interactions.${STEP_C}.success_count`,
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
    expect(policyResponse.status).toBe(201);
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

  async function startAuthorization() {
    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `interaction-results-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResponse.status).toBe(302);
    return convertNextAction(authResponse.headers.location).params.get("id");
  }

  async function runStep(authId, interaction) {
    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/external-api-authentication`,
      body: { interaction },
    });
    expect(response.status).toBe(200);
    expect(response.data.reached).toBe(interaction);
    return response;
  }

  async function authenticationStatusOf(authId) {
    const response = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authentication-status`,
    });
    expect(response.status).toBe(200);
    return response.data;
  }

  it("does not treat three calls to one interaction as three interactions", async () => {
    const authId = await startAuthorization();

    // step-b rather than step-a: step-a resolves the user, and once a user exists the next call to
    // it becomes a 2nd-factor identity check. Repeating a step that only adds a check keeps this
    // test about the counting.
    await runStep(authId, STEP_B);
    await runStep(authId, STEP_B);
    await runStep(authId, STEP_B);

    const status = await authenticationStatusOf(authId);
    console.log("Status after 3x step-b:", JSON.stringify(status, null, 2));

    const typeResult = status.interaction_results["external-api-authentication"];

    // The total is what a pre-#1771 policy could see, and it already reads 3 — which is exactly why
    // "success_count >= 3" was not a usable condition.
    expect(typeResult.success_count).toBe(3);

    // The breakdown tells them apart: only step-b ran.
    expect(typeResult.interactions[STEP_B].success_count).toBe(3);
    expect(typeResult.interactions[STEP_A]).toBeUndefined();
    expect(typeResult.interactions[STEP_C]).toBeUndefined();

    // And the policy naming all three is not satisfied.
    expect(status.status).toBe("in_progress");
    expect(status.status).not.toBe("success");
  }, 90000);

  it("completes once each interaction has run", async () => {
    const authId = await startAuthorization();

    await runStep(authId, STEP_A);
    await runStep(authId, STEP_B);
    await runStep(authId, STEP_C);

    const status = await authenticationStatusOf(authId);
    console.log("Status after a/b/c:", JSON.stringify(status, null, 2));

    const typeResult = status.interaction_results["external-api-authentication"];
    expect(typeResult.success_count).toBe(3);
    expect(typeResult.interactions[STEP_A].success_count).toBe(1);
    expect(typeResult.interactions[STEP_B].success_count).toBe(1);
    expect(typeResult.interactions[STEP_C].success_count).toBe(1);

    expect(status.status).toBe("success");

    // The flow really does complete, not just report a status.
    const authorizeResponse = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);
    expect(authorizeResponse.data.redirect_uri).toContain("code=");
  }, 90000);
});
