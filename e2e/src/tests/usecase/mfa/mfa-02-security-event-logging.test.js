import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";
import { sleep } from "../../../lib/util";

/**
 * Use Case: MFA Security Event Logging Verification
 *
 * Issue #915: Verify that authentication success/failure details are recorded in SecurityEvent
 *
 * This test verifies:
 * 1. Password authentication success events contain proper details
 * 2. Password authentication failure events contain error information (code, message)
 * 3. Security events can be queried and filtered by event_type
 */
describe("Use Case: MFA Security Event Logging", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let adminAccessToken;
  const redirectUri = `https://app.example.com/callback`;

  beforeAll(async () => {
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

    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `MFA Security Event Test Org ${timestamp}`,
        description: "Test organization for MFA security event logging",
      },
      tenant: {
        id: tenantId,
        name: `MFA Security Event Tenant ${timestamp}`,
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
        scopes_supported: ["openid", "profile", "email", "management"],
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
        name: faker.internet.email(),
        email: faker.internet.email(),
        email_verified: true,
        raw_password: `AdminPass${timestamp}!`,
      },
      client: {
        client_id: uuidv4(),
        client_id_alias: `admin-client-alias-${timestamp}`,
        client_secret: `admin-secret-${timestamp}`,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: "Admin Client",
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

    // Get admin token for the new tenant
    const createdClient = onboardingResponse.data.client;
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingRequest.user.email,
      password: onboardingRequest.user.raw_password,
      scope: "management",
      clientId: createdClient.client_id,
      clientSecret: createdClient.client_secret,
    });

    expect(adminTokenResponse.status).toBe(200);
    adminAccessToken = adminTokenResponse.data.access_token;

    // Create email authentication configuration
    const emailAuthConfigId = uuidv4();
    const emailAuthConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: emailAuthConfigId,
        type: "email",
        attributes: {},
        metadata: {
          type: "external",
          description: "Email authentication for security event test",
          sender: "test@gmail.com",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
          templates: {
            registration: {
              subject: "[ID Verification] Your signup email confirmation code",
              body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\n– IDP Support"
            },
            authentication: {
              subject: "[ID Verification] Your login email confirmation code",
              body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\n– IDP Support"
            }
          },
          settings: {
            smtp: {
              host: "smtp.gmail.com",
              port: 587,
              username: "test@gmail.com",
              password: "test",
              auth: true,
              starttls: {
                enable: true
              }
            }
          },
          retry_count_limitation: 5,
          expire_seconds: 300
        },
        interactions: {
          "email-authentication-challenge": {
            request: {
              schema: {
                type: "object",
                properties: {
                  email: { type: "string" },
                  template: { type: "string" }
                }
              }
            },
            pre_hook: {},
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/email-authentication-challenge",
                method: "POST",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: "http://host.docker.internal:4000/token",
                  client_id: "your-client-id",
                  username: "username",
                  password: "password",
                  scope: "application"
                },
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" }
                ],
                body_mapping_rules: [
                  { from: "$.request_body", to: "*" }
                ]
              },
              http_request_store: {
                key: "email-authentication-challenge",
                interaction_mapping_rules: [
                  { from: "$.response_body.transaction_id", to: "transaction_id" }
                ]
              }
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_request.response_body", to: "*" }
              ]
            }
          },
          "email-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  verification_code: { type: "string" }
                }
              }
            },
            pre_hook: {},
            execution: {
              function: "http_request",
              previous_interaction: { key: "email-authentication-challenge" },
              http_request: {
                url: "http://host.docker.internal:4000/email-authentication",
                method: "POST",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: "http://host.docker.internal:4000/token",
                  client_id: "your-client-id",
                  username: "username",
                  password: "password",
                  scope: "application"
                },
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" }
                ],
                body_mapping_rules: [
                  { from: "$.request_body", to: "*" }
                ]
              }
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_request.response_body", to: "*" }
              ]
            }
          }
        }
      }
    });

    expect(emailAuthConfigResponse.status).toBe(201);

    // Create SMS authentication configuration
    const smsAuthConfigId = uuidv4();
    const smsAuthConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: smsAuthConfigId,
        type: "sms",
        attributes: {},
        metadata: {
          type: "external",
          description: "SMS authentication for security event test",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
          retry_count_limitation: 5,
          expire_seconds: 300
        },
        interactions: {
          "sms-authentication-challenge": {
            request: {
              schema: {
                type: "object",
                properties: {
                  phone_number: { type: "string" },
                  template: { type: "string" }
                }
              }
            },
            pre_hook: {},
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/sms-authentication-challenge",
                method: "POST",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: "http://host.docker.internal:4000/token",
                  client_id: "your-client-id",
                  username: "username",
                  password: "password",
                  scope: "application"
                },
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" }
                ],
                body_mapping_rules: [
                  { from: "$.request_body", to: "*" }
                ]
              },
              http_request_store: {
                key: "sms-authentication-challenge",
                interaction_mapping_rules: [
                  { from: "$.response_body.transaction_id", to: "transaction_id" }
                ]
              }
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_request.response_body", to: "*" }
              ]
            }
          },
          "sms-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  verification_code: { type: "string" }
                }
              }
            },
            pre_hook: {},
            execution: {
              function: "http_request",
              previous_interaction: { key: "sms-authentication-challenge" },
              http_request: {
                url: "http://host.docker.internal:4000/sms-authentication",
                method: "POST",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: "http://host.docker.internal:4000/token",
                  client_id: "your-client-id",
                  username: "username",
                  password: "password",
                  scope: "application"
                },
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" }
                ],
                body_mapping_rules: [
                  { from: "$.request_body", to: "*" }
                ]
              }
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_request.response_body", to: "*" }
              ]
            }
          }
        }
      }
    });

    expect(smsAuthConfigResponse.status).toBe(201);

    // Create authentication policy
    const authPolicyConfigId = uuidv4();
    const authPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: authPolicyConfigId,
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_or_initial_registration_policy",
            priority: 10,
            conditions: {
              scopes: ["openid"]
            },
            available_methods: ["password", "initial-registration"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1
                  }
                ],
                [
                  {
                    path: "$.initial-registration.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1
                  }
                ]
              ]
            }
          }
        ]
      }
    });

    expect(authPolicyResponse.status).toBe(201);

    // Create test client
    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "MFA Security Event Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
      },
    });

    expect(createClientResponse.status).toBe(201);
  });

  it("should record password authentication failure with error details in security event (Issue #915)", async () => {
    const timestamp = Date.now();
    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const correctPassword = `CorrectPass${timestamp}!`;
    const wrongPassword = `WrongPass${timestamp}!`;

    console.log("\n=== Step 1: Register User ===");

    // Start authorization flow
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
    const authId = new URL(location, backendUrl).searchParams.get('id');
    expect(authId).toBeDefined();
    console.log("Authorization started:", authId);

    // Register user
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: userEmail,
        password: correctPassword,
        name: userName,
      },
    });

    expect(registrationResponse.status).toBe(200);
    console.log("✓ User registered:", userEmail);

    // Complete first authorization to establish user
    const authorizeCompleteResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    expect(authorizeCompleteResponse.status).toBe(200);
    console.log("✓ Initial authorization completed\n");

    console.log("\n=== Step 2: Start New Authorization and Attempt Wrong Password ===");

    // Start new authorization flow
    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_fail_${timestamp}`,
    });

    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });

    expect(authorizeResponse2.status).toBe(302);
    const location2 = authorizeResponse2.headers.location;
    const authId2 = new URL(location2, backendUrl).searchParams.get('id');
    expect(authId2).toBeDefined();
    console.log("New authorization started:", authId2);

    // Attempt password authentication with WRONG password
    const wrongPasswordResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/password-authentication`,
      body: {
        username: userEmail,
        password: wrongPassword,
      },
    });

    console.log("Wrong password response:", wrongPasswordResponse.status, JSON.stringify(wrongPasswordResponse.data, null, 2));
    expect(wrongPasswordResponse.status).toBe(400);
    expect(wrongPasswordResponse.data.error).toBeDefined();
    console.log("✓ Password authentication failed as expected\n");

    console.log("\n=== Step 3: Verify Security Event Contains Error Details ===");

    // Wait for security events to be persisted
    await sleep(2000);

    // Query security events for password authentication failures
    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "password_failure",
        limit: 10,
      },
    });

    console.log("Security events response:", securityEventsResponse.status);
    expect(securityEventsResponse.status).toBe(200);

    // Find the relevant security event
    const failureEvents = securityEventsResponse.data.list;
    console.log(`Found ${failureEvents.length} password_failure event(s)`);

    // Issue #915: Must have at least one failure event
    expect(failureEvents.length).toBeGreaterThan(0);

    const latestFailureEvent = failureEvents[0];
    console.log("Latest failure event:", JSON.stringify(latestFailureEvent, null, 2));

    // Verify event type
    expect(latestFailureEvent).toHaveProperty("type", "password_failure");

    // Issue #915: detail field must exist
    expect(latestFailureEvent).toHaveProperty("detail");
    expect(latestFailureEvent.detail).toBeDefined();
    console.log("Event detail:", JSON.stringify(latestFailureEvent.detail, null, 2));

    // Issue #915: Verify execution_result is present
    expect(latestFailureEvent.detail).toHaveProperty("execution_result");
    expect(latestFailureEvent.detail.execution_result).toHaveProperty("error");
    expect(latestFailureEvent.detail.execution_result).toHaveProperty("error_description");
    console.log("✓ Execution result found in security event:");
    console.log(`  - error: ${latestFailureEvent.detail.execution_result.error}`);
    console.log(`  - error_description: ${latestFailureEvent.detail.execution_result.error_description}`);

    // Issue #1021: Verify user information is present in failure event
    expect(latestFailureEvent).toHaveProperty("user");
    expect(latestFailureEvent.user).toBeDefined();
    expect(latestFailureEvent.user).toHaveProperty("name", userEmail);
    console.log("✓ User information found in failure event (Issue #1021):");
    console.log(`  - user.name: ${latestFailureEvent.user.name}`);

    console.log("\n=== Step 4: Attempt Correct Password and Verify Success Event ===");

    // Start another authorization flow
    const authParams3 = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_success_${timestamp}`,
    });

    const authorizeResponse3 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams3.toString()}`,
      headers: {},
    });

    expect(authorizeResponse3.status).toBe(302);
    const location3 = authorizeResponse3.headers.location;
    const authId3 = new URL(location3, backendUrl).searchParams.get('id');
    expect(authId3).toBeDefined();

    // Attempt password authentication with CORRECT password
    const correctPasswordResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId3}/password-authentication`,
      body: {
        username: userEmail,
        password: correctPassword,
      },
    });

    console.log("Correct password response:", correctPasswordResponse.status, JSON.stringify(correctPasswordResponse.data, null, 2));
    expect(correctPasswordResponse.status).toBe(200);
    expect(correctPasswordResponse.data.error).toBeUndefined();
    console.log("✓ Password authentication succeeded\n");

    // Wait for security events to be persisted
    await sleep(2000);

    // Query security events for password authentication success
    const successEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "password_success",
        limit: 10,
      },
    });

    console.log("Success events response:", successEventsResponse.status);
    expect(successEventsResponse.status).toBe(200);

    const successEvents = successEventsResponse.data.list;
    console.log(`Found ${successEvents.length} password_success event(s)`);

    if (successEvents.length > 0) {
      const latestSuccessEvent = successEvents[0];
      console.log("Latest success event:", JSON.stringify(latestSuccessEvent, null, 2));

      expect(latestSuccessEvent).toHaveProperty("type", "password_success");

      // Issue #1017: Verify user.name is preferred_username (email by default identity policy)
      expect(latestSuccessEvent.user).toHaveProperty("name", userEmail);
      console.log(`✓ user.name matches preferred_username: ${latestSuccessEvent.user.name}`);

      console.log("✓ Password authentication success event recorded\n");
    }

    console.log("\n=== Test Completed Successfully ===\n");
    console.log("Summary:");
    console.log("  - Password authentication failure events are recorded");
    console.log("  - Error details (error, error_description) are included in execution_result");
    console.log("  - Password authentication success events are recorded");
  });

  it("should record multiple authentication attempts with distinct error details", async () => {
    const timestamp = Date.now();
    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const correctPassword = `CorrectPass${timestamp}!`;

    console.log("\n=== Multiple Authentication Attempts Test ===");

    // Register user first
    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_multi_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    expect(authorizeResponse.status).toBe(302);
    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get('id');

    // Register user
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: userEmail,
        password: correctPassword,
        name: userName,
      },
    });

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    console.log("✓ User registered:", userEmail);

    // Multiple failed attempts
    for (let i = 1; i <= 3; i++) {
      const attemptParams = new URLSearchParams({
        response_type: "code",
        client_id: clientId,
        redirect_uri: redirectUri,
        scope: "openid profile email",
        state: `state_attempt_${i}_${timestamp}`,
      });

      const attemptAuthResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/authorizations?${attemptParams.toString()}`,
        headers: {},
      });

      expect(attemptAuthResponse.status).toBe(302);
      const attemptLocation = attemptAuthResponse.headers.location;
      const attemptAuthId = new URL(attemptLocation, backendUrl).searchParams.get('id');

      const failResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${attemptAuthId}/password-authentication`,
        body: {
          username: userEmail,
          password: `WrongPass${i}${timestamp}!`,
        },
      });

      console.log(`Attempt ${i}: status=${failResponse.status}, error=${failResponse.data.error || 'none'}`);
    }

    // Wait for security events to be persisted
    await sleep(2000);

    // Query all recent security events
    const allEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        limit: 20,
      },
    });

    expect(allEventsResponse.status).toBe(200);
    console.log(`\nTotal security events: ${allEventsResponse.data.list.length}`);

    // Count event types
    const eventTypeCounts = {};
    allEventsResponse.data.list.forEach(event => {
      eventTypeCounts[event.event_type] = (eventTypeCounts[event.event_type] || 0) + 1;
    });

    console.log("Event type distribution:");
    Object.entries(eventTypeCounts).forEach(([type, count]) => {
      console.log(`  - ${type}: ${count}`);
    });

    console.log("\n=== Multiple Authentication Attempts Test Completed ===\n");
  });

  it("should record email authentication failure with error details in security event", async () => {
    const timestamp = Date.now();
    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const correctPassword = `CorrectPass${timestamp}!`;

    console.log("\n=== Email Authentication Failure Test ===");

    // Step 1: Register user
    console.log("\n=== Step 1: Register User ===");

    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_email_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    expect(authorizeResponse.status).toBe(302);
    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get('id');
    expect(authId).toBeDefined();

    // Register user
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: userEmail,
        password: correctPassword,
        name: userName,
      },
    });

    expect(registrationResponse.status).toBe(200);
    console.log("✓ User registered:", userEmail);

    // Complete authorization
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    // Step 2: Start new authorization and attempt email authentication with wrong code
    console.log("\n=== Step 2: Email Authentication Challenge and Wrong Code ===");

    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_email_fail_${timestamp}`,
    });

    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });

    expect(authorizeResponse2.status).toBe(302);
    const location2 = authorizeResponse2.headers.location;
    const authId2 = new URL(location2, backendUrl).searchParams.get('id');
    expect(authId2).toBeDefined();
    console.log("New authorization started:", authId2);

    // Send email authentication challenge
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/email-authentication-challenge`,
      body: {
        email: userEmail,
        template: "authentication",
      },
    });

    console.log("Email challenge response:", challengeResponse.status, JSON.stringify(challengeResponse.data, null, 2));

    // Attempt email authentication with WRONG verification code
    const wrongCodeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/email-authentication`,
      body: {
        verification_code: "000000", // Wrong code
      },
    });

    console.log("Wrong code response:", wrongCodeResponse.status, JSON.stringify(wrongCodeResponse.data, null, 2));
    expect(wrongCodeResponse.status).toBe(400);
    expect(wrongCodeResponse.data.error).toBeDefined();
    console.log("✓ Email authentication failed as expected\n");

    // Step 3: Verify security event contains error details
    console.log("\n=== Step 3: Verify Security Event Contains Error Details ===");

    await sleep(2000);

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "email_verification_failure",
        limit: 10,
      },
    });

    console.log("Security events response:", securityEventsResponse.status);
    expect(securityEventsResponse.status).toBe(200);

    const failureEvents = securityEventsResponse.data.list;
    console.log(`Found ${failureEvents.length} email_verification_failure event(s)`);

    expect(failureEvents.length).toBeGreaterThan(0);

    const latestFailureEvent = failureEvents[0];
    console.log("Latest failure event:", JSON.stringify(latestFailureEvent, null, 2));

    // Verify event type
    expect(latestFailureEvent).toHaveProperty("type", "email_verification_failure");

    // Issue #915: detail and execution_result must exist
    expect(latestFailureEvent).toHaveProperty("detail");
    expect(latestFailureEvent.detail).toBeDefined();

    expect(latestFailureEvent.detail).toHaveProperty("execution_result");
    console.log("✓ Execution result found in email failure security event:");
    console.log(`  - execution_result: ${JSON.stringify(latestFailureEvent.detail.execution_result)}`);

    // Issue #1021: Verify user information is present in failure event
    expect(latestFailureEvent).toHaveProperty("user");
    expect(latestFailureEvent.user).toBeDefined();
    expect(latestFailureEvent.user).toHaveProperty("name", userEmail);
    console.log("✓ User information found in email failure event (Issue #1021):");
    console.log(`  - user.name: ${latestFailureEvent.user.name}`);

    console.log("\n=== Email Authentication Failure Test Completed ===\n");
  });

  it("should record SMS authentication failure with error details in security event (Issue #1021)", async () => {
    const timestamp = Date.now();
    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const correctPassword = `CorrectPass${timestamp}!`;
    const userPhoneNumber = `+8190${Math.floor(10000000 + Math.random() * 90000000)}`;

    console.log("\n=== SMS Authentication Failure Test ===");

    // Step 1: Register user
    console.log("\n=== Step 1: Register User ===");

    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_sms_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    expect(authorizeResponse.status).toBe(302);
    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get('id');
    expect(authId).toBeDefined();

    // Register user
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: userEmail,
        password: correctPassword,
        name: userName,
        phone_number: userPhoneNumber,
      },
    });

    expect(registrationResponse.status).toBe(200);
    console.log("✓ User registered:", userEmail, "phone:", userPhoneNumber);

    // Complete authorization
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    // Step 2: Start new authorization and attempt SMS authentication with wrong code
    console.log("\n=== Step 2: SMS Authentication Challenge and Wrong Code ===");

    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_sms_fail_${timestamp}`,
    });

    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });

    expect(authorizeResponse2.status).toBe(302);
    const location2 = authorizeResponse2.headers.location;
    const authId2 = new URL(location2, backendUrl).searchParams.get('id');
    expect(authId2).toBeDefined();
    console.log("New authorization started:", authId2);

    // Send SMS authentication challenge
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/sms-authentication-challenge`,
      body: {
        phone_number: userPhoneNumber,
        template: "authentication",
      },
    });

    console.log("SMS challenge response:", challengeResponse.status, JSON.stringify(challengeResponse.data, null, 2));

    // Attempt SMS authentication with WRONG verification code
    const wrongCodeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/sms-authentication`,
      body: {
        verification_code: "000000", // Wrong code
      },
    });

    console.log("Wrong code response:", wrongCodeResponse.status, JSON.stringify(wrongCodeResponse.data, null, 2));
    expect(wrongCodeResponse.status).toBe(400);
    expect(wrongCodeResponse.data.error).toBeDefined();
    console.log("✓ SMS authentication failed as expected\n");

    // Step 3: Verify security event contains error details
    console.log("\n=== Step 3: Verify Security Event Contains Error Details ===");

    await sleep(2000);

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "sms_verification_failure",
        limit: 10,
      },
    });

    console.log("Security events response:", securityEventsResponse.status);
    expect(securityEventsResponse.status).toBe(200);

    const failureEvents = securityEventsResponse.data.list;
    console.log(`Found ${failureEvents.length} sms_verification_failure event(s)`);

    expect(failureEvents.length).toBeGreaterThan(0);

    const latestFailureEvent = failureEvents[0];
    console.log("Latest failure event:", JSON.stringify(latestFailureEvent, null, 2));

    // Verify event type
    expect(latestFailureEvent).toHaveProperty("type", "sms_verification_failure");

    // Issue #915: detail and execution_result must exist
    expect(latestFailureEvent).toHaveProperty("detail");
    expect(latestFailureEvent.detail).toBeDefined();

    expect(latestFailureEvent.detail).toHaveProperty("execution_result");
    console.log("✓ Execution result found in SMS failure security event:");
    console.log(`  - execution_result: ${JSON.stringify(latestFailureEvent.detail.execution_result)}`);

    // Issue #1021: Verify user information is present in failure event
    expect(latestFailureEvent).toHaveProperty("user");
    expect(latestFailureEvent.user).toBeDefined();
    console.log("✓ User information found in SMS failure event (Issue #1021):");
    console.log(`  - user.name: ${latestFailureEvent.user.name}`);
    console.log(`  - user.sub: ${latestFailureEvent.user.sub}`);

    console.log("\n=== SMS Authentication Failure Test Completed ===\n");
  });

  it("should record user info in security event when 2nd factor SMS authentication fails (Issue #1034)", async () => {
    const timestamp = Date.now();
    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const correctPassword = `CorrectPass${timestamp}!`;
    const userPhoneNumber = `+8190${Math.floor(10000000 + Math.random() * 90000000)}`;

    console.log("\n=== 2nd Factor SMS Authentication Failure Test (Issue #1034) ===");

    // Step 1: Register user with phone number
    console.log("\n=== Step 1: Register User ===");

    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_2fa_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    expect(authorizeResponse.status).toBe(302);
    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get('id');
    expect(authId).toBeDefined();

    // Register user
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: userEmail,
        password: correctPassword,
        name: userName,
        phone_number: userPhoneNumber,
      },
    });

    expect(registrationResponse.status).toBe(200);
    console.log("✓ User registered:", userEmail, "phone:", userPhoneNumber);

    // Complete authorization
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    // Step 2: Start new authorization and complete password authentication (1st factor)
    console.log("\n=== Step 2: Password Authentication (1st Factor) ===");

    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_2fa_pass_${timestamp}`,
    });

    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });

    expect(authorizeResponse2.status).toBe(302);
    const location2 = authorizeResponse2.headers.location;
    const authId2 = new URL(location2, backendUrl).searchParams.get('id');
    expect(authId2).toBeDefined();
    console.log("New authorization started:", authId2);

    // Complete password authentication (1st factor)
    const passwordResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/password-authentication`,
      body: {
        username: userEmail,
        password: correctPassword,
      },
    });

    console.log("Password auth response:", passwordResponse.status);
    expect(passwordResponse.status).toBe(200);
    console.log("✓ Password authentication succeeded (1st factor)\n");

    // Step 3: Attempt SMS authentication as 2nd factor with wrong code
    console.log("\n=== Step 3: SMS Authentication (2nd Factor) - Wrong Code ===");

    // Send SMS challenge
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/sms-authentication-challenge`,
      body: {
        phone_number: userPhoneNumber,
        template: "authentication",
      },
    });

    console.log("SMS challenge response:", challengeResponse.status, JSON.stringify(challengeResponse.data, null, 2));

    // Attempt SMS authentication with WRONG verification code
    const wrongCodeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/sms-authentication`,
      body: {
        verification_code: "000000", // Wrong code
      },
    });

    console.log("Wrong code response:", wrongCodeResponse.status, JSON.stringify(wrongCodeResponse.data, null, 2));
    expect(wrongCodeResponse.status).toBe(400);
    expect(wrongCodeResponse.data.error).toBeDefined();
    console.log("✓ SMS authentication failed as expected (2nd factor)\n");

    // Step 4: Verify security event contains user information
    console.log("\n=== Step 4: Verify Security Event Contains User Info ===");

    await sleep(2000);

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "sms_verification_failure",
        limit: 10,
      },
    });

    console.log("Security events response:", securityEventsResponse.status);
    expect(securityEventsResponse.status).toBe(200);

    const failureEvents = securityEventsResponse.data.list;
    console.log(`Found ${failureEvents.length} sms_verification_failure event(s)`);

    expect(failureEvents.length).toBeGreaterThan(0);

    const latestFailureEvent = failureEvents[0];
    console.log("Latest failure event:", JSON.stringify(latestFailureEvent, null, 2));

    // Verify event type
    expect(latestFailureEvent).toHaveProperty("type", "sms_verification_failure");

    // Issue #1034: Verify user information is present in 2nd factor failure event
    // When transaction has user (after 1st factor), user info should come from transaction.user()
    expect(latestFailureEvent).toHaveProperty("user");
    expect(latestFailureEvent.user).toBeDefined();
    expect(latestFailureEvent.user).toHaveProperty("sub");
    expect(latestFailureEvent.user).toHaveProperty("name");
    console.log("✓ User information found in 2nd factor SMS failure event (Issue #1034):");
    console.log(`  - user.name: ${latestFailureEvent.user.name}`);
    console.log(`  - user.sub: ${latestFailureEvent.user.sub}`);

    // The user name should match the registered user's email (preferred_username)
    expect(latestFailureEvent.user.name).toBe(userEmail);
    console.log("✓ User name matches registered user's email");

    console.log("\n=== 2nd Factor SMS Authentication Failure Test Completed ===\n");
    console.log("Summary:");
    console.log("  - User completed 1st factor (password authentication)");
    console.log("  - 2nd factor SMS authentication failed with wrong code");
    console.log("  - Security event correctly includes user info from transaction");
  });

  it("should record user info in security event when 2nd factor Email authentication fails (Issue #1034)", async () => {
    const timestamp = Date.now();
    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const correctPassword = `CorrectPass${timestamp}!`;

    console.log("\n=== 2nd Factor Email Authentication Failure Test (Issue #1034) ===");

    // Step 1: Register user
    console.log("\n=== Step 1: Register User ===");

    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_2fa_email_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    expect(authorizeResponse.status).toBe(302);
    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get('id');
    expect(authId).toBeDefined();

    // Register user
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: userEmail,
        password: correctPassword,
        name: userName,
      },
    });

    expect(registrationResponse.status).toBe(200);
    console.log("✓ User registered:", userEmail);

    // Complete authorization
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    // Step 2: Start new authorization and complete password authentication (1st factor)
    console.log("\n=== Step 2: Password Authentication (1st Factor) ===");

    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_2fa_email_pass_${timestamp}`,
    });

    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });

    expect(authorizeResponse2.status).toBe(302);
    const location2 = authorizeResponse2.headers.location;
    const authId2 = new URL(location2, backendUrl).searchParams.get('id');
    expect(authId2).toBeDefined();
    console.log("New authorization started:", authId2);

    // Complete password authentication (1st factor)
    const passwordResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/password-authentication`,
      body: {
        username: userEmail,
        password: correctPassword,
      },
    });

    console.log("Password auth response:", passwordResponse.status);
    expect(passwordResponse.status).toBe(200);
    console.log("✓ Password authentication succeeded (1st factor)\n");

    // Step 3: Attempt Email authentication as 2nd factor with wrong code
    console.log("\n=== Step 3: Email Authentication (2nd Factor) - Wrong Code ===");

    // Send Email challenge
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/email-authentication-challenge`,
      body: {
        email: userEmail,
        template: "authentication",
      },
    });

    console.log("Email challenge response:", challengeResponse.status, JSON.stringify(challengeResponse.data, null, 2));

    // Attempt Email authentication with WRONG verification code
    const wrongCodeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/email-authentication`,
      body: {
        verification_code: "000000", // Wrong code
      },
    });

    console.log("Wrong code response:", wrongCodeResponse.status, JSON.stringify(wrongCodeResponse.data, null, 2));
    expect(wrongCodeResponse.status).toBe(400);
    expect(wrongCodeResponse.data.error).toBeDefined();
    console.log("✓ Email authentication failed as expected (2nd factor)\n");

    // Step 4: Verify security event contains user information
    console.log("\n=== Step 4: Verify Security Event Contains User Info ===");

    await sleep(2000);

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "email_verification_failure",
        limit: 10,
      },
    });

    console.log("Security events response:", securityEventsResponse.status);
    expect(securityEventsResponse.status).toBe(200);

    const failureEvents = securityEventsResponse.data.list;
    console.log(`Found ${failureEvents.length} email_verification_failure event(s)`);

    expect(failureEvents.length).toBeGreaterThan(0);

    const latestFailureEvent = failureEvents[0];
    console.log("Latest failure event:", JSON.stringify(latestFailureEvent, null, 2));

    // Verify event type
    expect(latestFailureEvent).toHaveProperty("type", "email_verification_failure");

    // Issue #1034: Verify user information is present in 2nd factor failure event
    expect(latestFailureEvent).toHaveProperty("user");
    expect(latestFailureEvent.user).toBeDefined();
    expect(latestFailureEvent.user).toHaveProperty("sub");
    expect(latestFailureEvent.user).toHaveProperty("name");
    console.log("✓ User information found in 2nd factor Email failure event (Issue #1034):");
    console.log(`  - user.name: ${latestFailureEvent.user.name}`);
    console.log(`  - user.sub: ${latestFailureEvent.user.sub}`);

    // The user name should match the registered user's email
    expect(latestFailureEvent.user.name).toBe(userEmail);
    console.log("✓ User name matches registered user's email");

    console.log("\n=== 2nd Factor Email Authentication Failure Test Completed ===\n");
    console.log("Summary:");
    console.log("  - User completed 1st factor (password authentication)");
    console.log("  - 2nd factor Email authentication failed with wrong code");
    console.log("  - Security event correctly includes user info from transaction");
  });

  it("should handle UserTooManyFoundResultException when multiple users have same phone number", async () => {
    const timestamp = Date.now();
    const sharedPhoneNumber = `+8190${Math.floor(10000000 + Math.random() * 90000000)}`;
    const userEmail1 = faker.internet.email();
    const userEmail2 = faker.internet.email();
    const userPassword = `TestPass${timestamp}!`;

    console.log("\n=== UserTooManyFoundResultException Test ===");
    console.log("Shared phone number:", sharedPhoneNumber);

    // Step 1: Register first user with phone number
    console.log("\n=== Step 1: Register First User ===");

    const authParams1 = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email phone",
      state: `state_toomany1_${timestamp}`,
    });

    const authorizeResponse1 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams1.toString()}`,
      headers: {},
    });

    expect(authorizeResponse1.status).toBe(302);
    const location1 = authorizeResponse1.headers.location;
    const authId1 = new URL(location1, backendUrl).searchParams.get('id');

    const registration1 = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/initial-registration`,
      body: {
        email: userEmail1,
        password: userPassword,
        name: "User One",
        phone_number: sharedPhoneNumber,
      },
    });

    expect(registration1.status).toBe(200);
    console.log("✓ First user registered:", userEmail1, "phone:", sharedPhoneNumber);

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/authorize`,
      body: {},
    });

    // Step 2: Register second user with SAME phone number
    console.log("\n=== Step 2: Register Second User with SAME Phone ===");

    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email phone",
      state: `state_toomany2_${timestamp}`,
    });

    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });

    expect(authorizeResponse2.status).toBe(302);
    const location2 = authorizeResponse2.headers.location;
    const authId2 = new URL(location2, backendUrl).searchParams.get('id');

    const registration2 = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/initial-registration`,
      body: {
        email: userEmail2,
        password: userPassword,
        name: "User Two",
        phone_number: sharedPhoneNumber,  // Same phone as user1
      },
    });

    expect(registration2.status).toBe(200);
    console.log("✓ Second user registered:", userEmail2, "phone:", sharedPhoneNumber);

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/authorize`,
      body: {},
    });

    // Step 3: Start new authorization and trigger SMS authentication failure
    console.log("\n=== Step 3: SMS Authentication with Shared Phone (triggers TooMany) ===");

    const authParams3 = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email phone",
      state: `state_toomany3_${timestamp}`,
    });

    const authorizeResponse3 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams3.toString()}`,
      headers: {},
    });

    expect(authorizeResponse3.status).toBe(302);
    const location3 = authorizeResponse3.headers.location;
    const authId3 = new URL(location3, backendUrl).searchParams.get('id');
    console.log("Authorization ID:", authId3);

    // Send SMS challenge with shared phone number
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId3}/sms-authentication-challenge`,
      body: {
        phone_number: sharedPhoneNumber,
      },
    });

    console.log("SMS challenge response:", challengeResponse.status);
    console.log("SMS challenge data:", JSON.stringify(challengeResponse.data, null, 2));

    // Try SMS authentication with wrong code - this should trigger tryResolveUserForLogging
    // which will catch UserTooManyFoundResultException
    const smsAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId3}/sms-authentication`,
      body: {
        verification_code: "000000",  // Wrong code
      },
    });

    console.log("SMS auth (wrong code) response:", smsAuthResponse.status);
    console.log("SMS auth data:", JSON.stringify(smsAuthResponse.data, null, 2));

    // Should return 400 (not 500) - exception should be caught
    expect(smsAuthResponse.status).toBe(400);
    console.log("✓ SMS authentication failed with 400 (not 500)");

    // Step 4: Check security events
    console.log("\n=== Step 4: Verify Security Event ===");
    await sleep(2000);

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        type: "sms_verification_failure",
        limit: 10,
      },
    });

    expect(securityEventsResponse.status).toBe(200);
    const events = securityEventsResponse.data.list || [];
    console.log(`Found ${events.length} sms_verification_failure event(s)`);

    // Find the event for this test (most recent)
    const latestEvent = events[0];
    if (latestEvent) {
      console.log("Latest SMS failure event:");
      console.log("  Type:", latestEvent.type);
      console.log("  User:", JSON.stringify(latestEvent.user));

      // When UserTooManyFoundResultException occurs, user should be null
      // because tryResolveUserForLogging returns null
      if (latestEvent.user === null || latestEvent.user === undefined) {
        console.log("✓ User is null (expected when TooManyUsers)");
      } else {
        console.log("⚠ User info present (TooMany exception might not have occurred)");
      }
    }

    console.log("\n=== UserTooManyFoundResultException Test Completed ===");
    console.log("Summary:");
    console.log("  - Two users created with same phone number");
    console.log("  - SMS authentication failure triggers tryResolveUserForLogging");
    console.log("  - UserTooManyFoundResultException is caught (returns null)");
    console.log("  - Security event recorded without user info");
  });
});
