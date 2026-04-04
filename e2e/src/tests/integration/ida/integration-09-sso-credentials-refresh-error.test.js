import { describe, expect, it, beforeAll } from "@jest/globals";
import { postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { createFederatedUser } from "../../../user";
import {
  backendUrl,
  adminServerConfig,
  serverConfig,
  federationServerConfig,
  clientSecretPostClient,
  mockApiBaseUrl,
} from "../../testConfig";
import { v4 as uuidv4 } from "uuid";

/**
 * Integration Test: SSO Credentials Refresh Error Handling
 *
 * Issue #1456: Verify that SsoCredentialsParameterResolver returns appropriate
 * error responses instead of 500 server_error when refresh token fails.
 *
 * Test Flow:
 * 1. Create federated user (SSO credentials stored via federation login)
 * 2. Create identity verification config with sso_credentials pre_hook
 *    configured to use invalid client_id (mock-server returns 401)
 * 3. Apply for identity verification → SSO token refresh fails
 * 4. Verify error response contains proper error classification
 *
 * Prerequisites:
 * - Federation tenant configured (testConfig.federationServerConfig)
 * - Mock API server running (for OAuth token endpoint)
 */
describe("Integration: SSO Credentials Refresh Error Handling (#1456)", () => {
  let systemAccessToken;
  let userAccessToken;
  let idaConfigType;

  beforeAll(async () => {
    console.log("\n=== Setting up SSO Credentials Error Test ===\n");

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

    // Create federated user → SSO credentials saved automatically
    console.log("Creating federated user with SSO credentials...");
    const { user, accessToken } = await createFederatedUser({
      serverConfig,
      federationServerConfig,
      client: clientSecretPostClient,
    });
    userAccessToken = accessToken;
    console.log(`Federated user created: sub=${user.sub}`);

    // Create identity verification config with sso_credentials pre_hook
    // Using invalid client_id to trigger 401 on token refresh
    const timestamp = Date.now();
    idaConfigType = `sso-refresh-error-${timestamp}`;

    const idaConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        id: uuidv4(),
        type: idaConfigType,
        processes: {
          apply: {
            request: {
              schema: {
                type: "object",
                properties: { name: { type: "string" } },
                required: ["name"],
              },
            },
            pre_hook: {
              additional_parameters: [
                {
                  type: "sso_credentials",
                  details: {
                    token_endpoint: `${mockApiBaseUrl}/e2e/oauth-retry/401-always`,
                    client_id: "invalid",
                    client_secret: "invalid-secret",
                    client_authentication_type: "client_secret_post",
                  },
                },
              ],
            },
            execution: { type: "no_action" },
            store: {
              application_details_mapping_rules: [
                { from: "$.request_body", to: "*" },
              ],
            },
          },
        },
      },
    });
    console.log("IDA config status:", idaConfigResponse.status);
    expect(idaConfigResponse.status).toBe(201);

    console.log("\n=== Setup Complete ===\n");
  });

  it("should return proper error instead of 500 when SSO token refresh fails with 401", async () => {
    console.log("\n=== Test: SSO token refresh failure (401 from token endpoint) ===");

    const response = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/applications/${idaConfigType}/apply`,
      body: { name: "Test User" },
      headers: { Authorization: `Bearer ${userAccessToken}` },
    });

    console.log("Response status:", response.status);
    console.log("Response:", JSON.stringify(response.data, null, 2));

    // Before fix: 500 server_error
    // After fix: 401 with AUTHENTICATION_ERROR classification
    expect(response.status).toBe(401);
    expect(response.data.error).toBe("sso_credentials_error");
    expect(response.data.error_details.error_type).toBe("AUTHENTICATION_ERROR");
    expect(response.data.error_details.retryable).toBe(false);
    expect(response.data.error_details.status_code).toBe(401);

    console.log("=== Test Completed ===\n");
  });

  it("should classify 403 as AUTHENTICATION_ERROR with retryable=false", async () => {
    console.log("\n=== Test: SSO token refresh failure (403 from token endpoint) ===");

    // Create config pointing to 403-always endpoint
    const timestamp403 = Date.now();
    const idaConfigType403 = `sso-refresh-403-${timestamp403}`;
    const idaConfig403Response = await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        id: uuidv4(),
        type: idaConfigType403,
        processes: {
          apply: {
            request: {
              schema: {
                type: "object",
                properties: { name: { type: "string" } },
                required: ["name"],
              },
            },
            pre_hook: {
              additional_parameters: [
                {
                  type: "sso_credentials",
                  details: {
                    token_endpoint: `${mockApiBaseUrl}/e2e/oauth-retry/403-always`,
                    client_id: "test-client",
                    client_secret: "test-secret",
                    client_authentication_type: "client_secret_post",
                  },
                },
              ],
            },
            execution: { type: "no_action" },
            store: { application_details_mapping_rules: [{ from: "$.request_body", to: "*" }] },
          },
        },
      },
    });
    expect(idaConfig403Response.status).toBe(201);

    const response = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/applications/${idaConfigType403}/apply`,
      body: { name: "Test User 403" },
      headers: { Authorization: `Bearer ${userAccessToken}` },
    });

    console.log("Response status:", response.status);
    console.log("Response:", JSON.stringify(response.data, null, 2));

    expect(response.status).toBe(403);
    expect(response.data.error).toBe("sso_credentials_error");
    expect(response.data.error_details.error_type).toBe("AUTHENTICATION_ERROR");
    expect(response.data.error_details.retryable).toBe(false);
    expect(response.data.error_details.status_code).toBe(403);

    console.log("=== Test Completed ===\n");
  });

  it("should classify non-auth errors as SERVER_ERROR with retryable=true", async () => {
    console.log("\n=== Test: SSO token refresh failure (server error) ===");

    // Create config pointing to unreachable endpoint (returns 503 via Docker proxy)
    const timestampErr = Date.now();
    const idaConfigTypeErr = `sso-refresh-conn-err-${timestampErr}`;
    const idaConfigErrResponse = await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        id: uuidv4(),
        type: idaConfigTypeErr,
        processes: {
          apply: {
            request: {
              schema: {
                type: "object",
                properties: { name: { type: "string" } },
                required: ["name"],
              },
            },
            pre_hook: {
              additional_parameters: [
                {
                  type: "sso_credentials",
                  details: {
                    token_endpoint: "http://host.docker.internal:19999/oauth/token",
                    client_id: "test-client",
                    client_secret: "test-secret",
                    client_authentication_type: "client_secret_post",
                  },
                },
              ],
            },
            execution: { type: "no_action" },
            store: { application_details_mapping_rules: [{ from: "$.request_body", to: "*" }] },
          },
        },
      },
    });
    expect(idaConfigErrResponse.status).toBe(201);

    const response = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/applications/${idaConfigTypeErr}/apply`,
      body: { name: "Test User Conn Error" },
      headers: { Authorization: `Bearer ${userAccessToken}` },
    });

    console.log("Response status:", response.status);
    console.log("Response:", JSON.stringify(response.data, null, 2));

    // Non-auth HTTP error → SERVER_ERROR, retryable=true, original status code preserved
    expect(response.status).not.toBe(500);
    expect(response.data.error).toBe("sso_credentials_error");
    expect(response.data.error_details.error_type).not.toBe("AUTHENTICATION_ERROR");
    expect(response.data.error_details.retryable).toBe(true);

    console.log("=== Test Completed ===\n");
  });

  it("should return proper error when user has no SSO credentials", async () => {
    console.log("\n=== Test: No SSO credentials for user ===");

    // Use a non-federated user (admin user from adminServerConfig has no SSO credentials)
    const nonFederatedTokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: "identity_verification_application management",
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(nonFederatedTokenResponse.status).toBe(200);
    const nonFederatedToken = nonFederatedTokenResponse.data.access_token;

    // Create config on admin tenant with sso_credentials pre_hook
    const timestampNoSso = Date.now();
    const idaConfigTypeNoSso = `sso-no-credentials-${timestampNoSso}`;
    const idaConfigNoSsoResponse = await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/identity-verification-configurations`,
      headers: { Authorization: `Bearer ${nonFederatedToken}` },
      body: {
        id: uuidv4(),
        type: idaConfigTypeNoSso,
        processes: {
          apply: {
            request: {
              schema: {
                type: "object",
                properties: { name: { type: "string" } },
                required: ["name"],
              },
            },
            pre_hook: {
              additional_parameters: [
                {
                  type: "sso_credentials",
                  details: {
                    token_endpoint: `${mockApiBaseUrl}/token`,
                    client_id: "test-client",
                    client_secret: "test-secret",
                    client_authentication_type: "client_secret_post",
                  },
                },
              ],
            },
            execution: { type: "no_action" },
            store: { application_details_mapping_rules: [{ from: "$.request_body", to: "*" }] },
          },
        },
      },
    });
    console.log("Config status:", idaConfigNoSsoResponse.status);
    expect(idaConfigNoSsoResponse.status).toBe(201);

    const response = await postWithJson({
      url: `${backendUrl}/${adminServerConfig.tenantId}/v1/me/identity-verification/applications/${idaConfigTypeNoSso}/apply`,
      body: { name: "Non-Federated User" },
      headers: { Authorization: `Bearer ${nonFederatedToken}` },
    });

    console.log("Response status:", response.status);
    console.log("Response:", JSON.stringify(response.data, null, 2));

    // User has no SSO credentials → exception → UNEXPECTED_ERROR
    expect(response.status).toBe(400);
    expect(response.data.error).toBe("sso_credentials_error");
    expect(response.data.error_details.error_type).toBe("UNEXPECTED_ERROR");
    expect(response.data.error_details.retryable).toBe(false);

    console.log("=== Test Completed ===\n");
  });
});
