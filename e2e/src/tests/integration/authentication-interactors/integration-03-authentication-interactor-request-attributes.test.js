import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";

/**
 * Issue #1773: $.request_attributes.* in authentication interaction mapping.
 *
 * The two HTTP authentication executors put the RequestAttributes object itself into the mapping
 * context instead of its toMap(), so it serialized as {"json_node_wrapper":{"json_node":{...}}} and
 * every documented path ($.request_attributes.ip_address, .user_agent) resolved to null. The
 * identity-verification side already called toMap(), so the same path worked there and not here.
 *
 * The mock endpoint echoes the body it received, which is what makes this a check of the value that
 * actually reached the external API rather than of the mapper in isolation. Source IP and
 * User-Agent are the point of the issue: a risk-decision API cannot use client-supplied values.
 */
describe("Authentication Interactor: $.request_attributes in mapping rules", () => {
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
          name: `Request Attributes Test Org ${timestamp}`,
          description: "Test organization for $.request_attributes mapping",
        },
        tenant: {
          id: tenantId,
          name: `Request Attributes Tenant ${timestamp}`,
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

    // Both HTTP executors are affected, so cover http_request and http_requests in one config:
    // the challenge interaction uses the single-request form, the registration one the list form.
    const bodyMappingRules = [
      { from: "$.request_attributes.ip_address", to: "ip" },
      { from: "$.request_attributes.user_agent", to: "ua" },
      { from: "$.request_attributes.action", to: "action" },
      { from: "$.request_attributes.resource", to: "resource" },
      { from: "$.request_attributes.headers['User-Agent']", to: "header_ua" },
    ];
    const httpRequest = {
      url: `${mockApiBaseUrl}/e2e/echo-request-context`,
      method: "POST",
      header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
      body_mapping_rules: bodyMappingRules,
    };

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "fido-uaf",
        attributes: {},
        metadata: { type: "external", description: "request_attributes echo" },
        interactions: {
          "fido-uaf-registration-challenge": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: { function: "http_request", http_request: httpRequest },
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
            execution: { function: "http_requests", http_requests: [httpRequest] },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_requests[0].response_body", to: "*" },
              ],
            },
          },
        },
      },
    });

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "Request Attributes Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
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

  async function startAuthorizationAndRegister() {
    const timestamp = Date.now();
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
    const authId = new URL(authorizeResponse.headers.location, backendUrl).searchParams.get("id");

    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: faker.internet.email(),
        password: `Password${timestamp}!`,
        name: faker.person.fullName(),
      },
    });
    expect(registrationResponse.status).toBe(200);

    return authId;
  }

  /**
   * The mock echoes what it received, so an unresolved mapping shows up as an empty string rather
   * than as a missing assertion.
   */
  function expectRequestContextReached(data) {
    console.log("Echoed request context:", JSON.stringify(data, null, 2));

    // Docker-internal address of the e2e client; asserting the shape keeps this independent of the
    // subnet the compose network happens to allocate.
    expect(data.received_ip).toMatch(/^\d+\.\d+\.\d+\.\d+$/);
    expect(data.received_user_agent).toMatch(/^axios\//);
    expect(data.received_action).toBe("POST");
    expect(data.received_resource).toContain("/v1/authorizations/");

    // Parity with identity verification, which already exposes the full attribute map.
    expect(data.received_header_user_agent).toBe(data.received_user_agent);

    // The pre-#1773 failure mode: every path resolved to null, which the mock echoed as the
    // literal string "null". Asserted explicitly so a regression is unmistakable in the output.
    expect(data.received_ip).not.toBe("null");
    expect(data.received_user_agent).not.toBe("null");
  }

  it("should resolve $.request_attributes.* for the http_request executor", async () => {
    const authId = await startAuthorizationAndRegister();

    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration-challenge`,
      body: {},
    });

    expect(response.status).toBe(200);
    expectRequestContextReached(response.data);
  });

  it("should resolve $.request_attributes.* for the http_requests executor", async () => {
    const authId = await startAuthorizationAndRegister();

    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration-challenge`,
      body: {},
    });
    expect(challengeResponse.status).toBe(200);

    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido-uaf-registration`,
      body: {},
    });

    expect(response.status).toBe(200);
    expectRequestContextReached(response.data);
  });
});
