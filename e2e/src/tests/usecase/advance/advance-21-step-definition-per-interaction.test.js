import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken, getAuthorizations } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction } from "../../../lib/util";

/**
 * Advance Use Case: step_definitions resolved per interaction (Issue #1813)
 *
 * `external-api-authentication` holds several interactions in one configuration and reports the
 * same `method()` — "external-api" — whichever one ran. A step definition keyed on the method alone
 * therefore applied to all of them, so `requires_user` could not be false for the interaction that
 * identifies the user and true for the one that only adds a check. Whichever value you picked, one
 * of the two interactions behaved wrongly.
 *
 * This configuration mixes both factors in one place, which is what was impossible:
 *
 *   identify  user_resolve あり  requires_user: false   1st factor
 *   verify    user_resolve なし  requires_user: true    2nd factor
 *
 * Pinned here:
 *
 *   1. `verify` before anything identified the user is rejected — the 2nd-factor constraint is
 *      enforced for that interaction alone.
 *   2. `identify` then `verify` both succeed — the 1st-factor interaction is not subject to it.
 */
describe("Advance Use Case: step_definitions per interaction (Issue #1813)", () => {
  let systemAccessToken;
  let mgmtAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  const redirectUri = "https://app.example.com/callback";

  const IDENTIFY = "identify";
  const VERIFY = "verify";

  /**
   * One interaction against the mock. /auth/password echoes the submitted username, so each
   * interaction has a distinguishable response. Only `identify` carries user_resolve.
   */
  const interaction = (label, resolvesUser) => ({
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
    const adminEmail = `admin-${timestamp}@step-definition.example.com`;
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
          name: `Step Definition Org ${timestamp}`,
          description: "E2E for #1813",
        },
        tenant: {
          id: tenantId,
          name: `Step Definition Tenant ${timestamp}`,
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
          client_name: "Step Definition Client",
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
        metadata: { type: "external", description: "1st and 2nd factor in one configuration" },
        interactions: {
          [IDENTIFY]: interaction(IDENTIFY, true),
          [VERIFY]: interaction(VERIFY, false),
        },
      },
    });
    expect(authConfigResponse.status).toBe(201);

    // The definitions this issue adds. Keyed on the method alone these two lines could not
    // coexist — the configuration would have had to pick one requires_user for both interactions.
    const policyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "identify_then_verify",
            priority: 1,
            conditions: {},
            available_methods: ["external-api"],
            step_definitions: [
              {
                method: "external-api",
                interaction: IDENTIFY,
                order: 1,
                requires_user: false,
                allow_registration: true,
                user_identity_source: "email",
              },
              {
                method: "external-api",
                interaction: VERIFY,
                order: 2,
                requires_user: true,
              },
            ],
            success_conditions: {
              any_of: [
                [
                  {
                    path: `$.external-api-authentication.interactions.${IDENTIFY}.success_count`,
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: `$.external-api-authentication.interactions.${VERIFY}.success_count`,
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

    // The stored representation has to carry the new key, or a management GET -> PUT round trip
    // would silently drop it and the policy would go back to method-level resolution.
    const storedPolicy = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(storedPolicy.status).toBe(200);
    const storedSteps = storedPolicy.data.list[0].policies[0].step_definitions;
    expect(storedSteps.map((step) => step.interaction)).toEqual([IDENTIFY, VERIFY]);
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
      state: `step-definition-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResponse.status).toBe(302);
    return convertNextAction(authResponse.headers.location).params.get("id");
  }

  function runStep(authId, name) {
    return postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/external-api-authentication`,
      body: { interaction: name },
    });
  }

  it("rejects the 2nd-factor interaction before a user has been identified", async () => {
    const authId = await startAuthorization();

    const response = await runStep(authId, VERIFY);
    console.log("verify without a user:", response.status, JSON.stringify(response.data));

    expect(response.status).toBe(400);
    expect(response.data.error).toBe("user_not_found");
  });

  it("accepts the 1st-factor interaction, then the 2nd-factor one", async () => {
    const authId = await startAuthorization();

    // requires_user: false applies to this interaction only, so it runs with no user yet and
    // resolves one from the external API response.
    const identifyResponse = await runStep(authId, IDENTIFY);
    console.log("identify:", identifyResponse.status, JSON.stringify(identifyResponse.data));
    expect(identifyResponse.status).toBe(200);
    expect(identifyResponse.data.reached).toBe(IDENTIFY);

    // requires_user: true is now satisfied by the user the previous interaction resolved.
    const verifyResponse = await runStep(authId, VERIFY);
    console.log("verify after identify:", verifyResponse.status, JSON.stringify(verifyResponse.data));
    expect(verifyResponse.status).toBe(200);
    expect(verifyResponse.data.reached).toBe(VERIFY);

    const status = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authentication-status`,
    });
    expect(status.status).toBe(200);
    expect(status.data.status).toBe("success");
  }, 90000);
});
