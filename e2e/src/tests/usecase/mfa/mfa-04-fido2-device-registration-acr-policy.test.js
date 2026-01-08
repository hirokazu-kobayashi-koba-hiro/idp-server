import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";
import { sleep } from "../../../lib/util";

/**
 * Use Case: FIDO2 (WebAuthn) Device Registration with ACR Policy Enforcement
 *
 * Issue: Penetration Test Issue #8 - ACR-based access control for device registration
 * Commit: 7accd3b3e - ACR/MFA policy enforcement for FIDO device registration
 *
 * Security Requirement:
 * - FIDO2 device registration should require proper authentication level
 * - Password-only authentication should NOT be sufficient when policy requires MFA
 * - Email authentication (as second factor) should satisfy the policy
 *
 * Flow:
 * 1. Setup tenant with device_registration_conditions policy requiring email-authentication
 * 2. User logs in with password only
 * 3. Attempt FIDO2 registration → Should fail (ACR not met)
 * 4. User completes email authentication
 * 5. Attempt FIDO2 registration → Should succeed (ACR met)
 */
describe("Use Case: FIDO2 Device Registration with ACR Policy", () => {
  let systemAccessToken;

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
  });

  it("should reject FIDO2 registration when ACR policy is not satisfied (password only)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = `https://app.example.com/callback`;

    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const userPassword = `Password${timestamp}!`;

    console.log("\n=== FIDO2 ACR Policy Test: Setup ===\n");

    const { jwks } = await generateRS256KeyPair();

    // Step 1: Create Organization and Tenant
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `FIDO2 ACR Test Org ${timestamp}`,
        description: "Test organization for FIDO2 ACR policy",
      },
      tenant: {
        id: tenantId,
        name: `FIDO2 ACR Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
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

    console.log("Onboarding response:", onboardingResponse.status);
    expect(onboardingResponse.status).toBe(201);
    console.log("✓ Organization and tenant created\n");

    // Get admin token
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
    const adminAccessToken = adminTokenResponse.data.access_token;
    console.log("✓ Admin access token obtained\n");

    // Step 2: Create Password Authentication Configuration
    console.log("=== Step 2: Create Password Authentication Configuration ===");

    const passwordAuthConfigId = uuidv4();
    const passwordAuthConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: passwordAuthConfigId,
        type: "password",
        attributes: {},
        metadata: {
          type: "internal",
          description: "Password authentication"
        },
        interactions: {}
      }
    });

    expect(passwordAuthConfigResponse.status).toBe(201);
    console.log("✓ Password authentication configuration created\n");

    // Step 3: Create Email Authentication Configuration
    console.log("=== Step 3: Create Email Authentication Configuration ===");

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
          description: "Email authentication for MFA",
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
              previous_interaction: {
                key: "email-authentication-challenge"
              },
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
    console.log("✓ Email authentication configuration created\n");

    // Step 4: Create FIDO2 Authentication Configuration
    console.log("=== Step 4: Create FIDO2 Authentication Configuration ===");

    const fido2AuthConfigId = uuidv4();
    const fido2AuthConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: fido2AuthConfigId,
        type: "fido2",
        attributes: {},
        metadata: {
          type: "external",
          description: "FIDO2 WebAuthn authentication",
        },
        interactions: {
          "fido2-registration-challenge": {
            request: {
              schema: {
                type: "object",
                properties: {}
              }
            },
            pre_hook: {},
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/fido2/registration-challenge",
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" }
                ],
                body_mapping_rules: [
                  { from: "$.request_body", to: "*" }
                ]
              },
              http_request_store: {
                key: "fido2-registration-challenge",
                interaction_mapping_rules: [
                  { from: "$.response_body.challenge", to: "challenge" }
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
          "fido2-registration": {
            request: {
              schema: {
                type: "object",
                properties: {}
              }
            },
            pre_hook: {},
            execution: {
              function: "http_request",
              previous_interaction: {
                key: "fido2-registration-challenge"
              },
              http_request: {
                url: "http://host.docker.internal:4000/fido2/registration",
                method: "POST",
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

    expect(fido2AuthConfigResponse.status).toBe(201);
    console.log("✓ FIDO2 authentication configuration created\n");

    // Step 5: Create Authentication Policy with device_registration_conditions
    console.log("=== Step 5: Create Authentication Policy with device_registration_conditions ===");

    const authPolicyConfigId = uuidv4();

    // Policy requires email-authentication success for device registration
    // This prevents password-only users from registering new FIDO2 devices
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
            description: "mfa_required_for_device_registration",
            priority: 10,
            conditions: {
              scopes: ["openid"]
            },
            available_methods: ["password", "email", "fido2"],
            success_conditions: {
              any_of: [
                // Password authentication is sufficient for login
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1
                  }
                ]
              ]
            },
            // Device registration requires email authentication (MFA)
            device_registration_conditions: {
              any_of: [
                // Option 1: Email authentication completed (MFA satisfied)
                [
                  {
                    path: "$.email-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1
                  }
                ],
                // Option 2: Already has FIDO2 device authenticated
                [
                  {
                    path: "$.fido2-authentication.success_count",
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

    console.log("Auth policy response:", authPolicyResponse.status, JSON.stringify(authPolicyResponse.data, null, 2));
    expect(authPolicyResponse.status).toBe(201);
    console.log("✓ Authentication policy with device_registration_conditions created\n");

    // Step 6: Create Client
    console.log("=== Step 6: Create Test Client ===");

    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "FIDO2 ACR Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
      },
    });

    expect(createClientResponse.status).toBe(201);
    console.log("✓ Client created\n");

    // Step 7: Register User
    console.log("=== Step 7: Register User ===");

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
        password: userPassword,
        name: userName,
      },
    });

    expect(registrationResponse.status).toBe(200);
    console.log("✓ User registered\n");

    // Step 8: Attempt FIDO2 Registration Challenge (Should FAIL - ACR not met)
    console.log("=== Step 8: Attempt FIDO2 Registration (Password Only - Should FAIL) ===");

    // Use the same user that was registered (not a random user)
    // This tests ACR policy, not user mismatch protection
    const fido2ChallengeRequestBody = {
      username: userEmail,
      displayName: userName,
      authenticatorSelection: {
        authenticatorAttachment: "platform",
        requireResidentKey: true,
        userVerification: "required",
      },
      attestation: "none",
    };

    const fido2ChallengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido2-registration-challenge`,
      body: fido2ChallengeRequestBody,
    });

    console.log("FIDO2 challenge response (password only):", fido2ChallengeResponse.status, fido2ChallengeResponse.data);

    // Should be rejected due to device_registration_conditions not met
    expect(fido2ChallengeResponse.status).toBe(400);
    expect(fido2ChallengeResponse.data.error).toBe("forbidden");
    expect(fido2ChallengeResponse.data.error_description).toContain("device registration requirements");

    console.log("✓ FIDO2 registration correctly rejected (ACR policy not satisfied)\n");

    // Step 9: Verify Security Event was logged
    console.log("=== Step 9: Verify Security Event Logging ===");

    await sleep(1000); // Wait for security event to be persisted

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        event_type: "fido2_registration_challenge_failure",
        limit: 10,
      },
    });

    console.log("Security events response:", securityEventsResponse.status);
    expect(securityEventsResponse.status).toBe(200);

    // Verify security event was logged for ACR policy violation
    const securityEvents = securityEventsResponse.data.list || [];
    console.log(`Found ${securityEvents.length} fido2_registration_challenge_failure event(s)`);

    expect(securityEvents.length).toBeGreaterThanOrEqual(1);

    const latestEvent = securityEvents[0];
    console.log("Latest security event:", JSON.stringify(latestEvent, null, 2));

    // Verify event contains expected information
    expect(latestEvent.type).toBe("fido2_registration_challenge_failure");
    console.log("✓ Security event correctly logged for ACR policy violation\n");

    console.log("\n=== Test Completed: ACR Policy Enforcement Working ===\n");
    console.log("Summary:");
    console.log("  - Password-only authentication cannot register FIDO2 devices");
    console.log("  - device_registration_conditions policy is enforced");
    console.log("  - Security event (fido2_registration_challenge_failure) is logged\n");
  });

  it("should allow FIDO2 registration after email authentication (MFA satisfied)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = `https://app.example.com/callback`;

    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const userPassword = `Password${timestamp}!`;

    console.log("\n=== FIDO2 ACR Policy Test: Email Auth Then FIDO2 Registration ===\n");

    const { jwks } = await generateRS256KeyPair();

    // Setup tenant (same as previous test)
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `FIDO2 MFA Test Org ${timestamp}`,
        description: "Test organization for FIDO2 MFA flow",
      },
      tenant: {
        id: tenantId,
        name: `FIDO2 MFA Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
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
        client_id_alias: `admin-client-mfa-${timestamp}`,
        client_secret: `admin-secret-mfa-${timestamp}`,
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
    console.log("✓ Organization and tenant created\n");

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
    const adminAccessToken = adminTokenResponse.data.access_token;

    // Create Password Authentication Configuration
    const passwordAuthConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: passwordAuthConfigId,
        type: "password",
        attributes: {},
        metadata: { type: "internal", description: "Password authentication" },
        interactions: {}
      }
    });
    console.log("✓ Password auth config created");

    // Create Email Authentication Configuration
    const emailAuthConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: emailAuthConfigId,
        type: "email",
        attributes: {},
        metadata: {
          type: "external",
          description: "Email authentication for MFA",
          sender: "test@gmail.com",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
          templates: {
            registration: {
              subject: "[ID Verification] Your signup email confirmation code",
              body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\n– IDP Support"
            },
            authentication: {
              subject: "[ID Verification] Your login email confirmation code",
              body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\n– IDP Support"
            }
          },
          settings: {
            smtp: {
              host: "smtp.gmail.com",
              port: 587,
              username: "test@gmail.com",
              password: "test",
              auth: true,
              starttls: { enable: true }
            }
          },
          retry_count_limitation: 5,
          expire_seconds: 300
        },
        interactions: {
          "email-authentication-challenge": {
            request: { schema: { type: "object", properties: { email: { type: "string" } } } },
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
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }]
              },
              http_request_store: {
                key: "email-authentication-challenge",
                interaction_mapping_rules: [{ from: "$.response_body.transaction_id", to: "transaction_id" }]
              }
            },
            post_hook: {},
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] }
          },
          "email-authentication": {
            request: { schema: { type: "object", properties: { verification_code: { type: "string" } } } },
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
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }]
              }
            },
            post_hook: {},
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] }
          }
        }
      }
    });
    console.log("✓ Email auth config created");

    // Create FIDO2 Authentication Configuration
    const fido2AuthConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: fido2AuthConfigId,
        type: "fido2",
        attributes: {},
        metadata: {
          type: "external",
          description: "FIDO2 WebAuthn authentication",
        },
        interactions: {
          "fido2-registration-challenge": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/fido2/registration-challenge",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }]
              },
              http_request_store: {
                key: "fido2-registration-challenge",
                interaction_mapping_rules: [{ from: "$.response_body.challenge", to: "challenge" }]
              }
            },
            post_hook: {},
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] }
          },
          "fido2-registration": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              previous_interaction: { key: "fido2-registration-challenge" },
              http_request: {
                url: "http://host.docker.internal:4000/fido2/registration",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }]
              }
            },
            post_hook: {},
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] }
          }
        }
      }
    });
    console.log("✓ FIDO2 auth config created");

    // Create Authentication Policy with device_registration_conditions
    const authPolicyConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: authPolicyConfigId,
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "mfa_required_for_device_registration",
            priority: 10,
            conditions: { scopes: ["openid"] },
            available_methods: ["password", "email", "fido2"],
            success_conditions: {
              any_of: [
                [{ path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }]
              ]
            },
            device_registration_conditions: {
              any_of: [
                [{ path: "$.email-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
                [{ path: "$.fido2-authentication.success_count", type: "integer", operation: "gte", value: 1 }]
              ]
            }
          }
        ]
      }
    });
    console.log("✓ Authentication policy created");

    // Create Client
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "FIDO2 MFA Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    console.log("✓ Client created\n");

    // Step: Start authorization and register user
    console.log("=== Step: User Registration ===");

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
    console.log("Authorization started:", authId);

    // Register user with password
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: userEmail,
        password: userPassword,
        name: userName,
      },
    });
    expect(registrationResponse.status).toBe(200);
    console.log("✓ User registered with password\n");

    // Step: Verify FIDO2 registration fails with password only
    console.log("=== Step: Verify FIDO2 Registration Fails (Password Only) ===");

    // Use the same user that was registered (not a random user)
    // This tests ACR policy, not user mismatch protection
    const fido2ChallengeRequestBody = {
      username: userEmail,
      displayName: userName,
      authenticatorSelection: {
        authenticatorAttachment: "platform",
        requireResidentKey: true,
        userVerification: "required",
      },
      attestation: "none",
    };

    const failedChallengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido2-registration-challenge`,
      body: fido2ChallengeRequestBody,
    });

    console.log("FIDO2 challenge (password only):", failedChallengeResponse.status, failedChallengeResponse.data);
    expect(failedChallengeResponse.status).toBe(400);
    expect(failedChallengeResponse.data.error).toBe("forbidden");
    console.log("✓ FIDO2 registration correctly rejected (password only)\n");

    // Step: Complete Email Authentication (MFA)
    console.log("=== Step: Complete Email Authentication (MFA) ===");

    const emailChallengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: {
        email: userEmail,
        provider_id: "idp-server",
      },
    });

    console.log("Email challenge response:", emailChallengeResponse.status, emailChallengeResponse.data);
    expect(emailChallengeResponse.status).toBe(200);
    console.log("✓ Email verification code sent\n");

    // Get verification code from mock response
    const verificationCode = emailChallengeResponse.data.verification_code || "123456";
    console.log(`Verification code: ${verificationCode}`);

    const emailAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication`,
      body: {
        code: verificationCode,
      },
    });

    console.log("Email auth response:", emailAuthResponse.status, emailAuthResponse.data);
    expect(emailAuthResponse.status).toBe(200);
    console.log("✓ Email authentication completed (MFA satisfied)\n");

    // Step: Verify FIDO2 registration NOW succeeds
    console.log("=== Step: Verify FIDO2 Registration NOW Succeeds (MFA Satisfied) ===");

    const successChallengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido2-registration-challenge`,
      body: fido2ChallengeRequestBody,
    });

    console.log("FIDO2 challenge (after MFA):", successChallengeResponse.status, successChallengeResponse.data);

    // Should now succeed because email-authentication.success_count >= 1
    expect(successChallengeResponse.status).toBe(200);
    console.log("✓ FIDO2 registration challenge succeeded (MFA satisfied)\n");

    // Verify the challenge response has expected fields (WebAuthn format)
    expect(successChallengeResponse.data).toHaveProperty("challenge");
    expect(successChallengeResponse.data).toHaveProperty("rp");
    expect(successChallengeResponse.data).toHaveProperty("user");

    // Step: Verify Security Events were logged
    console.log("=== Step: Verify Security Event Logging ===");

    await sleep(1000); // Wait for security events to be persisted

    // Verify failure event was logged (password-only attempt)
    const failureEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        event_type: "fido2_registration_challenge_failure",
        limit: 10,
      },
    });

    console.log("Failure events response:", failureEventsResponse.status);
    expect(failureEventsResponse.status).toBe(200);

    const failureEvents = failureEventsResponse.data.list || [];
    console.log(`Found ${failureEvents.length} fido2_registration_challenge_failure event(s)`);
    expect(failureEvents.length).toBeGreaterThanOrEqual(1);
    console.log("✓ Failure security event correctly logged\n");

    // Verify success event was logged (after MFA)
    const successEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        event_type: "fido2_registration_challenge_success",
        limit: 10,
      },
    });

    console.log("Success events response:", successEventsResponse.status);
    expect(successEventsResponse.status).toBe(200);

    const successEvents = successEventsResponse.data.list || [];
    console.log(`Found ${successEvents.length} fido2_registration_challenge_success event(s)`);
    expect(successEvents.length).toBeGreaterThanOrEqual(1);
    console.log("✓ Success security event correctly logged\n");

    console.log("\n=== Test Completed: MFA → FIDO2 Registration Flow Working ===\n");
    console.log("Summary:");
    console.log("  1. Password-only authentication → FIDO2 registration REJECTED");
    console.log("  2. After email authentication (MFA) → FIDO2 registration ALLOWED");
    console.log("  3. device_registration_conditions policy is correctly enforced");
    console.log("  4. Security events (failure & success) are properly logged\n");
  });

  it("should reject FIDO2 registration for unauthenticated user even without device_registration_conditions policy", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = `https://app.example.com/callback`;

    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const userPassword = `Password${timestamp}!`;

    console.log("\n=== FIDO2 Test: Unauthenticated User Cannot Register Device ===\n");
    console.log("This test verifies that FIDO2 device registration requires authentication");
    console.log("even when device_registration_conditions is NOT configured in the policy.\n");

    const { jwks } = await generateRS256KeyPair();

    // Setup tenant WITHOUT device_registration_conditions
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `FIDO2 Unauth Test Org ${timestamp}`,
        description: "Test organization for unauthenticated FIDO2 registration",
      },
      tenant: {
        id: tenantId,
        name: `FIDO2 Unauth Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
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
        client_id_alias: `admin-client-unauth-${timestamp}`,
        client_secret: `admin-secret-unauth-${timestamp}`,
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
    console.log("✓ Organization and tenant created\n");

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
    const adminAccessToken = adminTokenResponse.data.access_token;

    // Create Password Authentication Configuration
    const passwordAuthConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: passwordAuthConfigId,
        type: "password",
        attributes: {},
        metadata: { type: "internal", description: "Password authentication" },
        interactions: {}
      }
    });
    console.log("✓ Password auth config created");

    // Create FIDO2 Authentication Configuration
    const fido2AuthConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: fido2AuthConfigId,
        type: "fido2",
        attributes: {},
        metadata: {
          type: "external",
          description: "FIDO2 WebAuthn authentication",
        },
        interactions: {
          "fido2-registration-challenge": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/fido2/registration-challenge",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }]
              },
              http_request_store: {
                key: "fido2-registration-challenge",
                interaction_mapping_rules: [{ from: "$.response_body.challenge", to: "challenge" }]
              }
            },
            post_hook: {},
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] }
          },
          "fido2-registration": {
            request: { schema: { type: "object", properties: {} } },
            pre_hook: {},
            execution: {
              function: "http_request",
              previous_interaction: { key: "fido2-registration-challenge" },
              http_request: {
                url: "http://host.docker.internal:4000/fido2/registration",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }]
              }
            },
            post_hook: {},
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] }
          }
        }
      }
    });
    console.log("✓ FIDO2 auth config created");

    // Create Authentication Policy WITHOUT device_registration_conditions
    const authPolicyConfigId = uuidv4();
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: authPolicyConfigId,
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "simple_password_policy_no_device_registration_conditions",
            priority: 10,
            conditions: { scopes: ["openid"] },
            available_methods: ["password", "fido2"],
            success_conditions: {
              any_of: [
                [{ path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }]
              ]
            }
            // NOTE: device_registration_conditions is NOT set
          }
        ]
      }
    });
    console.log("✓ Authentication policy created (WITHOUT device_registration_conditions)");

    // Create Client
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "FIDO2 Unauth Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    console.log("✓ Client created\n");

    // Step: Start authorization (but do NOT authenticate)
    console.log("=== Step: Start Authorization (No Authentication) ===");

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
    console.log("Authorization started:", authId);
    console.log("NOTE: User has NOT authenticated yet\n");

    // Step: Attempt FIDO2 Registration WITHOUT any authentication
    console.log("=== Step: Attempt FIDO2 Registration (No Authentication) ===");

    const fido2ChallengeRequestBody = {
      username: userEmail,
      displayName: userName,
      authenticatorSelection: {
        authenticatorAttachment: "platform",
        requireResidentKey: true,
        userVerification: "required",
      },
      attestation: "none",
    };

    const fido2ChallengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido2-registration-challenge`,
      body: fido2ChallengeRequestBody,
    });

    console.log("FIDO2 challenge response (unauthenticated):", fido2ChallengeResponse.status, fido2ChallengeResponse.data);

    // Should be rejected because user is not authenticated at all
    // Note: "unauthorized" is returned for unauthenticated users (vs "forbidden" for ACR policy violations)
    expect(fido2ChallengeResponse.status).toBe(400);
    expect(fido2ChallengeResponse.data.error).toBe("unauthorized");
    expect(fido2ChallengeResponse.data.error_description).toContain("requires authenticated session");

    console.log("✓ FIDO2 registration correctly rejected (user not authenticated)\n");

    // Verify security event was logged
    console.log("=== Step: Verify Security Event Logging ===");

    await sleep(1000);

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        event_type: "fido2_registration_challenge_failure",
        limit: 10,
      },
    });

    expect(securityEventsResponse.status).toBe(200);
    const securityEvents = securityEventsResponse.data.list || [];
    console.log(`Found ${securityEvents.length} fido2_registration_challenge_failure event(s)`);
    expect(securityEvents.length).toBeGreaterThanOrEqual(1);
    console.log("✓ Security event correctly logged\n");

    console.log("\n=== Test Completed: Unauthenticated FIDO2 Registration Blocked ===\n");
    console.log("Summary:");
    console.log("  - Unauthenticated user cannot register FIDO2 device");
    console.log("  - This protection works even WITHOUT device_registration_conditions policy");
    console.log("  - Security event is logged for the rejection\n");
  });
});
