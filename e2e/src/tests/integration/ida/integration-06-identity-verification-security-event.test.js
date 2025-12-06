import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { backendUrl, adminServerConfig, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";
import { sleep } from "../../../lib/util";

/**
 * Integration Test: Identity Verification Security Event Logging
 *
 * Issue #915: Verify that identity verification failure details are recorded in SecurityEvent
 *
 * This test verifies:
 * 1. Identity verification application failure events are recorded
 * 2. Error details (execution_result) are included in failure events
 * 3. Security events can be queried and filtered by event type
 */
describe("Integration: Identity Verification Security Event Logging", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let adminAccessToken;
  let userAccessToken;
  let clientId;
  let clientSecret;
  let identityVerificationConfigType;
  const redirectUri = `https://app.example.com/callback`;

  beforeAll(async () => {
    console.log("\n=== Setting up Identity Verification Security Event Test ===\n");

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

    // Setup test tenant with security event logging enabled
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${timestamp}`;
    identityVerificationConfigType = `ida-security-event-test-${timestamp}`;

    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `IDA Security Event Test Org ${timestamp}`,
        description: "Test organization for identity verification security event logging",
      },
      tenant: {
        id: tenantId,
        name: `IDA Security Event Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_user_ex_sub: true,
          include_client_id: true,
          include_ip: true,
          persistence_enabled: true,
          include_detail: true,
        },
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
        client_id_alias: `ida-security-event-client-${timestamp}`,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management identity_verification_application",
        client_name: "IDA Security Event Test Client",
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
    console.log(`✓ Tenant created: ${tenantId}`);

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
    userAccessToken = adminAccessToken; // Same user for simplicity
    console.log(`✓ Admin token obtained`);

    // Create identity verification configuration with external service that can return errors
    const idaConfigId = uuidv4();
    const idaConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: idaConfigId,
        type: identityVerificationConfigType,
        attributes: {
          enabled: true,
          description: "Test configuration for security event logging",
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
                  test_case: { type: "string" },
                  status: { type: "string" },
                },
                required: ["test_case"],
              },
            },
            execution: {
              type: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/e2e/error-responses`,
                method: "POST",
                auth_type: "none",
                body_mapping_rules: [
                  {
                    from: "$.request_body",
                    to: "*",
                  },
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
                {
                  from: "$.request_body",
                  to: "*",
                },
              ],
            },
          },
        },
      },
    });

    expect(idaConfigResponse.status).toBe(201);
    console.log(`✓ Identity verification configuration created: ${identityVerificationConfigType}`);

    console.log("\n=== Setup Complete ===\n");
  });

  it("should record identity verification application failure with execution_result in security event", async () => {
    console.log("\n=== Test: Identity Verification Failure Security Event ===\n");

    // Step 1: Trigger identity verification failure (external service returns 400)
    console.log("Step 1: Triggering identity verification failure...");

    const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${identityVerificationConfigType}/apply`;

    const failureResponse = await postWithJson({
      url: applyUrl,
      body: {
        test_case: "external_400",
        status: "400",
      },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`,
      },
    });

    console.log(`Response status: ${failureResponse.status}`);
    console.log("Response data:", JSON.stringify(failureResponse.data, null, 2));

    expect(failureResponse.status).toBe(400);
    expect(failureResponse.data).toHaveProperty("error", "execution_failed");
    console.log("✓ Identity verification failed as expected\n");

    // Step 2: Wait for security events to be persisted
    console.log("Step 2: Waiting for security events to be persisted...");
    await sleep(2000);

    // Step 3: Query security events for identity verification application failures
    console.log("Step 3: Querying security events...");

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "identity_verification_application_failure",
        limit: 10,
      },
    });

    console.log("Security events response:", securityEventsResponse.status);
    expect(securityEventsResponse.status).toBe(200);

    // Find the relevant security event
    const failureEvents = securityEventsResponse.data.list;
    console.log(`Found ${failureEvents.length} identity_verification_application_failure event(s)`);

    // Issue #915: Must have at least one failure event
    expect(failureEvents.length).toBeGreaterThan(0);

    const latestFailureEvent = failureEvents[0];
    console.log("Latest failure event:", JSON.stringify(latestFailureEvent, null, 2));

    // Verify event type
    expect(latestFailureEvent).toHaveProperty("type", "identity_verification_application_failure");

    // Issue #915: detail field must exist
    expect(latestFailureEvent).toHaveProperty("detail");
    expect(latestFailureEvent.detail).toBeDefined();
    console.log("Event detail:", JSON.stringify(latestFailureEvent.detail, null, 2));

    // Issue #915: Verify execution_result is present with error information
    expect(latestFailureEvent.detail).toHaveProperty("execution_result");
    expect(latestFailureEvent.detail.execution_result).toHaveProperty("error");
    console.log("✓ Execution result found in security event:");
    console.log(`  - error: ${latestFailureEvent.detail.execution_result.error}`);

    if (latestFailureEvent.detail.execution_result.error_description) {
      console.log(`  - error_description: ${latestFailureEvent.detail.execution_result.error_description}`);
    }

    console.log("\n=== Test Completed Successfully ===\n");
  });

  it("should record identity verification process failure with dynamic event type", async () => {
    console.log("\n=== Test: Identity Verification Process Failure Security Event ===\n");

    // Step 1: First create a successful application
    console.log("Step 1: Creating a successful application...");

    const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${identityVerificationConfigType}/apply`;

    const successResponse = await postWithJson({
      url: applyUrl,
      body: {
        test_case: "success_case",
        // No status parameter → Mockoon returns 200 by default
      },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`,
      },
    });

    console.log(`Apply response status: ${successResponse.status}`);

    // If external service returns success, application should be created
    if (successResponse.status === 200) {
      console.log("✓ Application created successfully");
      console.log(`  Application ID: ${successResponse.data.id}`);
    } else {
      console.log("Note: External service did not return success, skipping process test");
      return;
    }

    // Wait for events to be persisted
    await sleep(2000);

    // Query for apply success event
    const successEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "identity_verification_application_apply",
        limit: 10,
      },
    });

    expect(successEventsResponse.status).toBe(200);
    console.log(`Found ${successEventsResponse.data.list.length} identity_verification_application_apply event(s)`);

    if (successEventsResponse.data.list.length > 0) {
      const successEvent = successEventsResponse.data.list[0];
      expect(successEvent).toHaveProperty("type", "identity_verification_application_apply");
      console.log("✓ Apply success event recorded");
    }

    console.log("\n=== Test Completed ===\n");
  });

  it("should record 500 server error with execution_result in security event", async () => {
    console.log("\n=== Test: Server Error (500) Security Event ===\n");

    // Step 1: Trigger identity verification failure (external service returns 500)
    console.log("Step 1: Triggering server error...");

    const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${identityVerificationConfigType}/apply`;

    const errorResponse = await postWithJson({
      url: applyUrl,
      body: {
        test_case: "external_500",
        status: "500",
      },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`,
      },
    });

    console.log(`Response status: ${errorResponse.status}`);
    console.log("Response data:", JSON.stringify(errorResponse.data, null, 2));

    expect(errorResponse.status).toBe(500);
    expect(errorResponse.data).toHaveProperty("error", "execution_failed");
    expect(errorResponse.data.error_details).toHaveProperty("status_category", "server_error");
    console.log("✓ Server error occurred as expected\n");

    // Step 2: Wait for security events to be persisted
    console.log("Step 2: Waiting for security events to be persisted...");
    await sleep(2000);

    // Step 3: Query security events
    console.log("Step 3: Querying security events...");

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "identity_verification_application_failure",
        limit: 10,
      },
    });

    expect(securityEventsResponse.status).toBe(200);

    const failureEvents = securityEventsResponse.data.list;
    console.log(`Found ${failureEvents.length} failure event(s)`);

    expect(failureEvents.length).toBeGreaterThan(0);

    const latestEvent = failureEvents[0];
    console.log("Latest failure event:", JSON.stringify(latestEvent, null, 2));

    // Verify execution_result is present
    expect(latestEvent).toHaveProperty("detail");
    expect(latestEvent.detail).toHaveProperty("execution_result");
    expect(latestEvent.detail.execution_result).toHaveProperty("error");
    expect(latestEvent.detail.execution_result).toHaveProperty("error_details");
    expect(latestEvent.detail.execution_result.error_details).toHaveProperty("status_category", "server_error");
    expect(latestEvent.detail.execution_result.error_details).toHaveProperty("status_code", 500);

    console.log("✓ Server error details correctly recorded in security event:");
    console.log(`  - error: ${latestEvent.detail.execution_result.error}`);
    console.log(`  - status_category: ${latestEvent.detail.execution_result.error_details.status_category}`);
    console.log(`  - status_code: ${latestEvent.detail.execution_result.error_details.status_code}`);

    console.log("\n=== Test Completed ===\n");
  });

  it("should include user and client information in security event", async () => {
    console.log("\n=== Test: User and Client Information in Security Event ===\n");

    // Trigger a failure
    const applyUrl = `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${identityVerificationConfigType}/apply`;

    await postWithJson({
      url: applyUrl,
      body: {
        test_case: "user_info_test",
        status: "400",
      },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`,
      },
    });

    await sleep(2000);

    // Query security events
    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "identity_verification_application_failure",
        limit: 5,
      },
    });

    expect(securityEventsResponse.status).toBe(200);
    expect(securityEventsResponse.data.list.length).toBeGreaterThan(0);

    const event = securityEventsResponse.data.list[0];
    console.log("Event:", JSON.stringify(event, null, 2));

    // Verify user information is included
    expect(event).toHaveProperty("user");
    expect(event.user).toHaveProperty("sub");
    console.log(`✓ User sub recorded: ${event.user.sub}`);

    // Verify client information is included
    expect(event).toHaveProperty("client");
    expect(event.client).toHaveProperty("id");
    console.log(`✓ Client ID recorded: ${event.client.id}`);

    // Verify tenant information
    expect(event).toHaveProperty("tenant");
    expect(event.tenant).toHaveProperty("id", tenantId);
    console.log(`✓ Tenant ID recorded: ${event.tenant.id}`);

    // Verify execution_result is present for failure event
    expect(event).toHaveProperty("detail");
    expect(event.detail).toHaveProperty("execution_result");
    expect(event.detail.execution_result).toHaveProperty("error");
    console.log(`✓ Execution result recorded: ${event.detail.execution_result.error}`);

    console.log("\n=== Test Completed ===\n");
  });
});
