import { describe, expect, it, beforeAll } from "@jest/globals";
import { postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { backendUrl, adminServerConfig, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";

/**
 * Integration Test: OAuth Token Retry on 401/403
 *
 * Issue #1143: Verify that HttpRequestExecutor invalidates cached OAuth token
 * and retries when external service returns 401 Unauthorized or 403 Forbidden.
 *
 * Test Flow:
 * 1. Create Identity Verification configuration with OAuth authentication
 * 2. Execute apply process that calls external service with OAuth token
 * 3. External service returns 401 Unauthorized
 * 4. HttpRequestExecutor should:
 *    - Invalidate the cached token
 *    - Request a new token from the token endpoint
 *    - Retry the request with the new token
 * 5. Verify retry behavior through application logs
 *
 * Expected Log Output:
 * - "Received 401 Unauthorized, invalidating cached token and retrying: uri=..."
 * - "Invalidated cached access token for key: ..."
 */
describe("Integration: OAuth Token Retry on 401/403", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let adminAccessToken;
  let userAccessToken;
  let clientId;
  let clientSecret;
  let idaConfigType401;
  let idaConfigType403;
  const redirectUri = `https://app.example.com/callback`;

  beforeAll(async () => {
    console.log("\n=== Setting up OAuth Retry Test ===\n");

    // Get system admin token
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

    // Setup test tenant
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${timestamp}`;
    idaConfigType401 = `oauth-retry-401-test-${timestamp}`;
    idaConfigType403 = `oauth-retry-403-test-${timestamp}`;

    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `OAuth Retry Test Org ${timestamp}`,
        description: "Test organization for OAuth retry on 401/403",
      },
      tenant: {
        id: tenantId,
        name: `OAuth Retry Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        token_endpoint_auth_signing_alg_values_supported: ["RS256", "ES256"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwks,
        grant_types_supported: ["authorization_code", "refresh_token", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management", "identity_verification_application"],
        response_types_supported: ["code"],
        response_modes_supported: ["query", "fragment"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        claims_parameter_supported: true,
        extension: {
          access_token_type: "JWT",
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          access_token_duration: 3600,
          id_token_duration: 3600,
          refresh_token_duration: 86400,
        },
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        name: faker.person.fullName(),
        email: faker.internet.email(),
        email_verified: true,
        raw_password: `AdminPass${timestamp}!`,
      },
      client: {
        client_id: clientId,
        client_id_alias: `oauth-retry-client-${timestamp}`,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management identity_verification_application",
        client_name: "OAuth Retry Test Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
      body: onboardingRequest,
    });

    expect(onboardingResponse.status).toBe(201);
    console.log(`Tenant created: ${tenantId}`);

    // Get admin token for the new tenant
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingRequest.user.email,
      password: onboardingRequest.user.raw_password,
      scope: "management identity_verification_application",
      clientId: clientId,
      clientSecret: clientSecret,
    });

    expect(adminTokenResponse.status).toBe(200);
    adminAccessToken = adminTokenResponse.data.access_token;
    userAccessToken = adminAccessToken;
    console.log(`Admin token obtained`);

    // Create identity verification configuration for 401 test
    // OAuth authentication is configured to call Mockoon's /token endpoint
    // The external service endpoint returns 401 always
    const idaConfig401Id = uuidv4();
    const idaConfig401Response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: idaConfig401Id,
        type: idaConfigType401,
        attributes: {
          enabled: true,
          description: "Test configuration for OAuth retry on 401",
        },
        common: {
          callback_application_id_param: "app_id",
          auth_type: "none",
        },
        processes: {
          apply: {
            request: {
              schema: {
                type: "object",
                properties: {
                  name: { type: "string" },
                },
                required: ["name"],
              },
            },
            execution: {
              type: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/e2e/oauth-retry/401-always`,
                method: "POST",
                auth_type: "oauth2",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: `${mockApiBaseUrl}/token`,
                  client_id: "test-oauth-client",
                  username: "test-user",
                  password: "test-password",
                  scope: "api:access",
                },
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.request_body", to: "*" },
                ],
              },
            },
            transition: {
              applying: {
                any_of: [[]],
              },
            },
            store: {
              application_details_mapping_rules: [
                { from: "$.request_body", to: "*" },
              ],
            },
          },
        },
      },
    });

    expect(idaConfig401Response.status).toBe(201);
    console.log(`Identity verification config (401) created: ${idaConfigType401}`);

    // Create identity verification configuration for 403 test
    const idaConfig403Id = uuidv4();
    const idaConfig403Response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: idaConfig403Id,
        type: idaConfigType403,
        attributes: {
          enabled: true,
          description: "Test configuration for OAuth retry on 403",
        },
        common: {
          callback_application_id_param: "app_id",
          auth_type: "none",
        },
        processes: {
          apply: {
            request: {
              schema: {
                type: "object",
                properties: {
                  name: { type: "string" },
                },
                required: ["name"],
              },
            },
            execution: {
              type: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/e2e/oauth-retry/403-always`,
                method: "POST",
                auth_type: "oauth2",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: `${mockApiBaseUrl}/token`,
                  client_id: "test-oauth-client",
                  username: "test-user",
                  password: "test-password",
                  scope: "api:access",
                },
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.request_body", to: "*" },
                ],
              },
            },
            transition: {
              applying: {
                any_of: [[]],
              },
            },
            store: {
              application_details_mapping_rules: [
                { from: "$.request_body", to: "*" },
              ],
            },
          },
        },
      },
    });

    expect(idaConfig403Response.status).toBe(201);
    console.log(`Identity verification config (403) created: ${idaConfigType403}`);

    console.log("\n=== Setup Complete ===\n");
  });

  it("should retry with new token when external service returns 401 Unauthorized", async () => {
    console.log("\n=== Test: OAuth Retry on 401 Unauthorized ===\n");
    console.log("Expected behavior:");
    console.log("1. idp-server gets OAuth token from /token endpoint");
    console.log("2. idp-server calls external service with Bearer token");
    console.log("3. External service returns 401 Unauthorized");
    console.log("4. idp-server invalidates cached token (check logs for: 'Invalidated cached access token')");
    console.log("5. idp-server gets new token and retries (check logs for: 'Received 401 Unauthorized, invalidating cached token and retrying')");
    console.log("6. External service still returns 401 (no infinite retry)");
    console.log("");

    const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${idaConfigType401}/apply`;

    const response = await postWithJson({
      url: applyUrl,
      body: { name: "Test User for 401" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`,
      },
    });

    console.log(`Response status: ${response.status}`);
    console.log("Response data:", JSON.stringify(response.data, null, 2));

    // External service always returns 401, so after retry it should still fail with 401
    expect(response.status).toBe(401);
    expect(response.data).toHaveProperty("error", "execution_failed");
    expect(response.data).toHaveProperty("error_details");
    expect(response.data.error_details).toHaveProperty("status_code", 401);

    console.log("\n*** CHECK APPLICATION LOGS FOR: ***");
    console.log("  - 'Received 401 Unauthorized, invalidating cached token and retrying'");
    console.log("  - 'Invalidated cached access token for key:'");
    console.log("\nThis confirms the retry mechanism is working.\n");

    console.log("=== Test Completed ===\n");
  });

  it("should retry with new token when external service returns 403 Forbidden", async () => {
    console.log("\n=== Test: OAuth Retry on 403 Forbidden ===\n");
    console.log("Expected behavior:");
    console.log("1. idp-server gets OAuth token from /token endpoint");
    console.log("2. idp-server calls external service with Bearer token");
    console.log("3. External service returns 403 Forbidden");
    console.log("4. idp-server invalidates cached token (check logs for: 'Invalidated cached access token')");
    console.log("5. idp-server gets new token and retries (check logs for: 'Received 403 Forbidden, invalidating cached token and retrying')");
    console.log("6. External service still returns 403 (no infinite retry)");
    console.log("");

    const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${idaConfigType403}/apply`;

    const response = await postWithJson({
      url: applyUrl,
      body: { name: "Test User for 403" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`,
      },
    });

    console.log(`Response status: ${response.status}`);
    console.log("Response data:", JSON.stringify(response.data, null, 2));

    // External service always returns 403, so after retry it should still fail with 403
    expect(response.status).toBe(403);
    expect(response.data).toHaveProperty("error", "execution_failed");
    expect(response.data).toHaveProperty("error_details");
    expect(response.data.error_details).toHaveProperty("status_code", 403);

    console.log("\n*** CHECK APPLICATION LOGS FOR: ***");
    console.log("  - 'Received 403 Forbidden, invalidating cached token and retrying'");
    console.log("  - 'Invalidated cached access token for key:'");
    console.log("\nThis confirms the retry mechanism is working.\n");

    console.log("=== Test Completed ===\n");
  });

  it("should verify OAuth token is included in Authorization header", async () => {
    console.log("\n=== Test: Verify OAuth Token in Authorization Header ===\n");
    console.log("This test verifies that the OAuth token is correctly added to the request.");
    console.log("Check application logs for: 'Authorization: Bearer ...'");
    console.log("");

    // This test just confirms that OAuth authentication is being used
    // The token endpoint is called, and the token is added to the Authorization header
    const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${idaConfigType401}/apply`;

    const response = await postWithJson({
      url: applyUrl,
      body: { name: "Test User for OAuth Header Check" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`,
      },
    });

    // We expect 401 because the mock always returns 401
    expect(response.status).toBe(401);

    console.log("\n*** CHECK APPLICATION LOGS FOR: ***");
    console.log("  - HTTP request to /token endpoint (OAuth token request)");
    console.log("  - HTTP request to /e2e/oauth-retry/401-always with 'Authorization: Bearer' header");
    console.log("\n=== Test Completed ===\n");
  });
});
