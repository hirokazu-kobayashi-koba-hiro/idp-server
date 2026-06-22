import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";

/**
 * Authentication Interactor: response_resolve_configs schema symmetry (issue #1500)
 *
 * Before the fix, authentication-configurations declared
 * HttpRequestExecutionConfig.responseResolveConfigs as the wrapper type
 * HttpResponseResolveConfigs, so the same `response_resolve_configs` key required
 * an object wrapper ({"configs": [...]}) on the authentication side while
 * identity-verification accepted a bare array ([...]). Posting the array form to
 * authentication-configurations returned 500. In addition:
 *
 *  - the field was never consumed (no responseResolveConfigs()/hasResponseConfigs()
 *    override), so even a successfully stored config did nothing at runtime, and
 *  - toMap() omitted response_resolve_configs, so GET never echoed it back.
 *
 * This test asserts the authentication side now behaves identically to
 * identity-verification:
 *  1. POST with the bare array form returns 201 (not 500).
 *  2. GET round-trips response_resolve_configs as an array.
 *  3. mapped_status_code is actually applied at runtime.
 *
 * See e2e/src/tests/integration/ida/integration-01-identity_verification-condition.test.js
 * for the identity-verification counterpart this mirrors.
 */
describe("Authentication Interactor: response_resolve_configs schema symmetry (#1500)", () => {
  let systemAccessToken;
  let adminAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let createConfigResponse;
  const configId = uuidv4();
  const clientSecret = `client-secret-${Date.now()}`;
  const redirectUri = "https://app.example.com/callback";

  // The mock e2e/error-responses endpoint, when no "status" field matches a rule,
  // falls through to a 200 response whose body contains verification_status: "pending".
  // response_resolve_configs remaps that body to 401, exercising body-based resolution.
  const httpRequestUnderTest = {
    url: `${mockApiBaseUrl}/e2e/error-responses`,
    method: "POST",
    response_resolve_configs: [
      {
        conditions: [
          {
            path: "$.response_body.verification_status",
            operation: "eq",
            value: "pending",
          },
        ],
        match_mode: "ALL",
        mapped_status_code: 401,
      },
    ],
    header_mapping_rules: [
      { static_value: "application/json", to: "Content-Type" },
    ],
    body_mapping_rules: [{ from: "$.request_body", to: "*" }],
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

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Response Resolve Configs Org ${timestamp}`,
          description: "Test organization for response_resolve_configs symmetry",
        },
        tenant: {
          id: tenantId,
          name: `Response Resolve Configs Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwks,
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["RS256"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: faker.internet.email(),
          email_verified: true,
          raw_password: `AdminPass${timestamp}!`,
        },
        client: {
          client_id: uuidv4(),
          client_secret: `admin-secret-${timestamp}`,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "password"],
          scope: "openid profile email management",
          client_name: "Admin Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const createdClient = onboardingResponse.data.client;
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingResponse.data.user.email,
      password: `AdminPass${timestamp}!`,
      scope: "management",
      clientId: createdClient.client_id,
      clientSecret: createdClient.client_secret,
    });
    expect(adminTokenResponse.status).toBe(200);
    adminAccessToken = adminTokenResponse.data.access_token;

    // Password authentication config (used by initial-registration)
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
            pre_hook: {},
            execution: { function: "password_verification" },
            post_hook: {},
            response: {},
          },
        },
      },
    });

    // Config under test: fido-uaf with response_resolve_configs in the BARE ARRAY form.
    // Before the fix this POST returned 500 (Jackson could not deserialize an array
    // into the HttpResponseResolveConfigs wrapper).
    createConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: configId,
        type: "fido-uaf",
        attributes: {},
        metadata: {
          type: "external",
          description: "response_resolve_configs symmetry test",
        },
        interactions: {
          "fido-uaf-registration-challenge": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              http_request: httpRequestUnderTest,
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_request.response_body", to: "*" },
              ],
            },
          },
        },
      },
    });

    // Authentication policy allowing password + fido-uaf
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

    // Test client
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "Response Resolve Configs Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    });

    console.log("Setup complete: tenant, configs, policy, and client created");
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

  /**
   * Helper: start authorization flow and register a fresh user, return authId.
   */
  async function startAuthorizationAndRegister() {
    const timestamp = Date.now();
    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const userPassword = `Password${timestamp}!`;

    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });
    expect(authorizeResponse.status).toBe(302);

    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get("id");

    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: userEmail, password: userPassword, name: userName },
    });
    expect(registrationResponse.status).toBe(200);

    return authId;
  }

  it("accepts response_resolve_configs in the bare array form on POST (not 500)", () => {
    console.log("Create config response:", createConfigResponse.status);
    expect(createConfigResponse.status).toBe(201);
  });

  it("round-trips response_resolve_configs as an array on GET", async () => {
    const getResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations/${configId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });

    expect(getResponse.status).toBe(200);
    const httpRequest =
      getResponse.data.interactions["fido-uaf-registration-challenge"].execution
        .http_request;

    expect(httpRequest).toHaveProperty("response_resolve_configs");
    expect(Array.isArray(httpRequest.response_resolve_configs)).toBe(true);
    expect(httpRequest.response_resolve_configs.length).toBe(1);
    expect(httpRequest.response_resolve_configs[0].mapped_status_code).toBe(401);
    // Must NOT be re-wrapped as {"configs": [...]}
    expect(httpRequest.response_resolve_configs).not.toHaveProperty("configs");
  });

  it("applies mapped_status_code from response_resolve_configs at runtime", async () => {
    const authId = await startAuthorizationAndRegister();

    // Mock returns HTTP 200 with verification_status: "pending"; response_resolve_configs
    // remaps that body to 401. Before the fix the field was inert and this stayed 200.
    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration-challenge`,
      body: {},
    });

    console.log("Challenge response status:", response.status);
    console.log("Challenge response data:", JSON.stringify(response.data, null, 2));

    expect(response.status).toBe(401);
    expect(response.status).not.toBe(200);
    expect(response.status).not.toBe(500);
  });
});
