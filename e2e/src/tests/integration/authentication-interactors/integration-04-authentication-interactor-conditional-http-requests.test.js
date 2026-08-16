import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";

/**
 * Authentication Interactor: conditional http_requests (Issue #1789)
 *
 * `http_requests` ran every configured request unconditionally, so "call the second API only when
 * the first one said X" could not be expressed. The same `ConditionSpec` gate already existed for
 * identity-verification pre_hook parameters and for mapping rules — only the authentication
 * executor lacked the口.
 *
 * Two things are pinned here:
 *
 *  1. A request whose `condition` is false is **not sent**. Observed by pointing it at an endpoint
 *     that answers 503: if it were still called, the chain would abort and the interaction would
 *     return 503 instead of 200.
 *  2. The skipped request keeps its slot in `execution_http_requests`. `[N]` means "the Nth
 *     configured request", not "the Nth request that ran" — otherwise a mapping rule pointing at a
 *     later request would read a different one depending on whether the condition happened to
 *     hold, and a JSONPath landing on the wrong request resolves to null rather than failing
 *     (#1646), so nothing would report it.
 */
describe("Authentication Interactor: conditional http_requests (#1789)", () => {
  let systemAccessToken;
  let adminAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  const clientSecret = `client-secret-${Date.now()}`;
  const redirectUri = "https://app.example.com/callback";

  // The /auth/password mock echoes the submitted username, which gives the chain a deterministic
  // value for later requests to branch on.
  const PROBE = "conditional-probe";

  const probeRequest = {
    url: `${mockApiBaseUrl}/auth/password`,
    method: "POST",
    header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
    body_mapping_rules: [{ static_value: PROBE, to: "username" }],
  };

  // Condition is false, and the endpoint answers 503. Being skipped is therefore observable as
  // "the interaction succeeded at all".
  const skippedRequest = {
    url: `${mockApiBaseUrl}/e2e/error-responses`,
    method: "POST",
    condition: {
      operation: "eq",
      path: "$.execution_http_requests[0].response_body.user_id",
      value: "never-matches",
    },
    header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
    body_mapping_rules: [{ static_value: "503", to: "status" }],
  };

  // Condition is true, so this one runs — and must still be reachable at index [2].
  const conditionalRequest = {
    url: `${mockApiBaseUrl}/user/details`,
    method: "POST",
    condition: {
      operation: "eq",
      path: "$.execution_http_requests[0].response_body.user_id",
      value: PROBE,
    },
    header_mapping_rules: [{ static_value: "test-client-id", to: "x-client-id" }],
    body_mapping_rules: [{ static_value: PROBE, to: "user_id" }],
  };

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();

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

    const { jwks } = await generateRS256KeyPair();
    const adminEmail = `admin-${timestamp}@conditional.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Conditional http_requests Org ${timestamp}`,
          description: "E2E for #1789",
        },
        tenant: {
          id: tenantId,
          name: `Conditional http_requests Tenant ${timestamp}`,
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
          jwks,
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          id_token_signing_alg_values_supported: ["RS256"],
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
          client_name: "Conditional http_requests Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management org-management",
      clientId,
      clientSecret,
    });
    expect(adminTokenResponse.status).toBe(200);
    adminAccessToken = adminTokenResponse.data.access_token;

    // Password config so the flow can establish a user before the fido-uaf interaction runs.
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { type: "internal", description: "Password authentication" },
        interactions: {
          "password-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  username: { type: "string" },
                  password: { type: "string" },
                },
              },
            },
            execution: { function: "password_verification" },
          },
        },
      },
    });

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "fido-uaf",
        attributes: {},
        metadata: { type: "external", description: "conditional http_requests" },
        interactions: {
          "fido-uaf-registration-challenge": {
            request: { schema: { type: "object", properties: {} } },
            execution: {
              function: "http_requests",
              http_requests: [probeRequest, skippedRequest, conditionalRequest],
            },
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_requests[0].response_body.user_id", to: "probe_user_id" },
                { from: "$.execution_http_requests[1]", to: "second_slot" },
                {
                  from: "$.execution_http_requests[2].response_body.birthdate",
                  to: "third_birthdate",
                },
              ],
            },
          },
        },
      },
    });

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_then_fido_uaf",
            priority: 10,
            conditions: { scopes: ["openid"] },
            available_methods: ["password", "fido-uaf"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.initial-registration.success_count",
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
  });

  afterAll(async () => {
    if (tenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${tenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
    if (organizationId) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  /** Helper: start authorization flow and register a fresh user, return authId. */
  async function startAuthorizationAndRegister() {
    const timestamp = Date.now();
    const userEmail = faker.internet.email();
    const userPassword = `Password${timestamp}!`;

    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state-${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });
    expect(authorizeResponse.status).toBe(302);
    const authId = new URL(authorizeResponse.headers.location, backendUrl).searchParams.get("id");
    expect(authId).toBeTruthy();

    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: userEmail,
        password: userPassword,
        name: faker.person.fullName(),
      },
    });
    expect(registrationResponse.status).toBe(200);

    return authId;
  }

  it("does not send a request whose condition is false, and keeps its slot in execution_http_requests", async () => {
    const authId = await startAuthorizationAndRegister();

    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration-challenge`,
      body: {},
    });

    console.log("Conditional chain response:", response.status);
    console.log("Conditional chain data:", JSON.stringify(response.data, null, 2));

    // The skipped request points at a 503. Reaching 200 at all is the proof it was not sent —
    // before #1789 the condition was ignored, the chain hit the 503 and aborted.
    expect(response.status).toBe(200);

    // The first request ran and gave the later ones something to branch on.
    expect(response.data.probe_user_id).toBe(PROBE);

    // The skipped request kept its slot rather than being dropped.
    expect(response.data.second_slot).toEqual({ skipped: true });

    // ...so the third request is still reachable at [2]. If the skip had compacted the list this
    // would be undefined, because [2] would no longer exist.
    expect(response.data.third_birthdate).toBe("2000-02-02");
  });
});
