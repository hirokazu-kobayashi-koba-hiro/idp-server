import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";

/**
 * Authentication Interactor Error Propagation Tests
 *
 * Verifies that HTTP status codes from external authentication services
 * (e.g., FIDO-UAF server) are propagated accurately to the client,
 * rather than being collapsed to 200/400/500.
 *
 * Uses the /e2e/error-responses mock endpoint with FIDO-UAF registration
 * interaction configured to point to it. The mock returns the exact HTTP status
 * specified in the request body's "status" field.
 *
 * Flow:
 * 1. fido-uaf-registration-challenge → sends device_id (no "status" field) → mock returns 200
 * 2. fido-uaf-registration → sends original request body (with "status" field) → mock returns specified status
 *
 * Related Implementation:
 * - AuthenticationExecutionStatus.fromStatusCode()
 * - AuthenticationInteractionStatus.fromStatusCode()
 * - AuthenticationInteractionRequestResult.error()
 * - HttpRequestAuthenticationExecutor.createExecutionResult()
 */
describe("Authentication Interactor: External Service Error Code Propagation", () => {
  let systemAccessToken;
  let adminAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  const clientSecret = `client-secret-${Date.now()}`;
  const redirectUri = "https://app.example.com/callback";

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();

    // Get system access token
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

    // Onboard tenant
    const { jwks } = await generateRS256KeyPair();

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Error Propagation Test Org ${timestamp}`,
          description: "Test organization for authentication error propagation",
        },
        tenant: {
          id: tenantId,
          name: `Error Propagation Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          tenant_type: "ORGANIZER",
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

    // Create password authentication config
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

    // Create FIDO-UAF config
    // - fido-uaf-registration-challenge: points to /e2e/error-responses
    //   (sends device_id only, no "status" field → mock returns 200)
    // - fido-uaf-registration: points to /e2e/error-responses
    //   (sends original request body with "status" field → mock returns specified status)
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "fido-uaf",
        attributes: {},
        metadata: { type: "external", description: "FIDO-UAF with error testing" },
        interactions: {
          "fido-uaf-registration-challenge": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/e2e/error-responses`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_request.response_body", to: "*" },
              ],
            },
          },
          "fido-uaf-registration": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              previous_interaction: { key: "fido-uaf-registration-challenge" },
              http_request: {
                url: `${mockApiBaseUrl}/e2e/error-responses`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
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

    // Create authentication policy
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

    // Create test client
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "Error Propagation Test Client",
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
   * Helper: Start authorization flow and register user, return authId
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

  /**
   * Helper: Run challenge (succeeds), then send registration with specific mock status code.
   *
   * Challenge sends device_id only (no "status" field) → mock returns 200.
   * Registration sends original request body (with "status" field) → mock returns specified status.
   */
  async function sendFidoUafRegistrationWithStatus(authId, statusCode) {
    // Step 1: Challenge (succeeds - sends device_id, no "status" field)
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration-challenge`,
      body: {},
    });
    expect(challengeResponse.status).toBe(200);

    // Step 2: Registration (sends original request body with "status" field)
    return await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration`,
      body: {
        status: String(statusCode),
      },
    });
  }

  it.each([
    { statusCode: 429, description: "Too Many Requests" },
    { statusCode: 504, description: "Gateway Timeout" },
    { statusCode: 401, description: "Unauthorized" },
    { statusCode: 403, description: "Forbidden" },
    { statusCode: 502, description: "Bad Gateway" },
    { statusCode: 503, description: "Service Unavailable" },
  ])(
    "should propagate $statusCode $description from external FIDO-UAF service",
    async ({ statusCode }) => {
      const authId = await startAuthorizationAndRegister();

      const response = await sendFidoUafRegistrationWithStatus(authId, statusCode);

      console.log(`Response status: ${response.status} (expected: ${statusCode})`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBe(statusCode);
    }
  );

  it.each([
    {
      statusCode: "401_text",
      expectedStatus: 401,
      description: "plain text response",
    },
    {
      statusCode: "503_html",
      expectedStatus: 503,
      description: "HTML response",
    },
  ])(
    "should propagate $expectedStatus from external service returning $description without 500 error",
    async ({ statusCode, expectedStatus }) => {
      const authId = await startAuthorizationAndRegister();

      const response = await sendFidoUafRegistrationWithStatus(
        authId,
        statusCode
      );

      console.log(
        `Response status: ${response.status} (expected: ${expectedStatus})`
      );
      console.log("Response data:", JSON.stringify(response.data, null, 2));

      expect(response.status).toBe(expectedStatus);
      expect(response.status).not.toBe(500);
    }
  );

  it("should return 200 when external FIDO-UAF service succeeds", async () => {
    const authId = await startAuthorizationAndRegister();

    // Challenge
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration-challenge`,
      body: {},
    });
    expect(challengeResponse.status).toBe(200);

    // Registration - no "status" field → mock returns 200 by default
    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration`,
      body: {},
    });

    console.log(`Response status: ${response.status}`);
    console.log("Response data:", JSON.stringify(response.data, null, 2));

    expect(response.status).toBe(200);
  });
});
