import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, post, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";
import { sleep } from "../../../lib/util";

/**
 * Use Case: Password Reset with Email Authentication Flow
 *
 * Issue #1002: Self-service password reset using email authentication
 *
 * Flow:
 * 1. User initiates password reset (scope=password:reset)
 * 2. Email authentication challenge (6-digit code sent)
 * 3. Email authentication verification
 * 4. Authorization code → Access token with password:reset scope
 * 5. Password reset API call
 * 6. Verify new password works
 */
describe("Use Case: Password Reset with Email Authentication", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;

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

  it("should complete password reset flow via email authentication", async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${timestamp}`;
    const redirectUri = `https://app.example.com/callback`;

    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const initialPassword = `InitialPass${timestamp}!`;
    const newPassword = `NewPassword${timestamp}!`;

    console.log("\n=== Step 1: Create Test Organization and Tenant ===");

    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Password Reset Test Org ${timestamp}`,
        description: "Test organization for password reset use case",
      },
      tenant: {
        id: tenantId,
        name: `Password Reset Tenant ${timestamp}`,
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
        scopes_supported: ["openid", "profile", "email", "password:reset", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query", "fragment"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        claims_parameter_supported: true,
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
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

    // Get fresh admin token for the new tenant using the created client
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

    console.log("\n=== Step 1.5: Create Email Authentication Configuration ===");

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
          description: "Email authentication for password reset",
          sender: "test@gmail.com",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
          templates: {
            registration: {
              subject: "[ID Verification] Your signup email confirmation code",
              body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\nIf you did not request this, please contact your administrator.\n\n– IDP Support"
            },
            authentication: {
              subject: "[ID Verification] Your login email confirmation code",
              body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\nIf you did not request this, please contact your administrator.\n\n– IDP Support"
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
                  phone_number: {
                    type: "string"
                  },
                  template: {
                    type: "string"
                  }
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
                  {
                    static_value: "application/json",
                    to: "Content-Type"
                  }
                ],
                body_mapping_rules: [
                  {
                    from: "$.request_body",
                    to: "*"
                  }
                ]
              },
              http_request_store: {
                key: "email-authentication-challenge",
                interaction_mapping_rules: [
                  {
                    from: "$.response_body.transaction_id",
                    to: "transaction_id"
                  }
                ]
              }
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body",
                  to: "*"
                }
              ]
            }
          },
          "email-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  verification_code: {
                    type: "string"
                  }
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
                  {
                    static_value: "application/json",
                    to: "Content-Type"
                  }
                ],
                body_mapping_rules: [
                  {
                    from: "$.request_body",
                    to: "*"
                  }
                ]
              }
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body",
                  to: "*"
                }
              ]
            }
          }
        }
      }
    });

    console.log("Email auth config response:", emailAuthConfigResponse.status);
    expect(emailAuthConfigResponse.status).toBe(201);
    console.log("✓ Email authentication configuration created\n");

    console.log("\n=== Step 2: Create Client for Password Reset ===");

    const createClientRequest = {
      client_id: clientId,
      client_secret: clientSecret,
      client_name: "Password Reset Test Client",
      redirect_uris: [redirectUri],
      grant_types: ["authorization_code", "password"],
      response_types: ["code"],
      scope: "openid profile email password:reset",
      token_endpoint_auth_method: "client_secret_post",
    };

    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: createClientRequest,
    });

    console.log("Create client response:", createClientResponse.status, createClientResponse.data);
    expect(createClientResponse.status).toBe(201);
    console.log("✓ Client created\n");

    console.log("\n=== Step 3: Register User with Password Grant ===");

    // Initial user registration using authorization flow
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

    // Server should redirect (302) to authentication page with authorization ID
    expect(authorizeResponse.status).toBe(302);
    const location = authorizeResponse.headers.location;
    expect(location).toBeDefined();

    // Extract authorization ID from redirect URL
    const authId = new URL(location, backendUrl).searchParams.get('id');
    expect(authId).toBeDefined();
    console.log("Authorization started:", authId);

    // Initial registration
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: {
        email: userEmail,
        password: initialPassword,
        name: userName,
      },
    });

    expect(registrationResponse.status).toBe(200);
    console.log("✓ User registered\n");

    // Authorize
    const authorizeCompleteResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    console.log("Authorize complete response:", authorizeCompleteResponse.status, authorizeCompleteResponse.data);
    expect(authorizeCompleteResponse.status).toBe(200);

    // Extract code from redirect_uri
    const redirectUrl = new URL(authorizeCompleteResponse.data.redirect_uri);
    const authCode = redirectUrl.searchParams.get('code');
    expect(authCode).toBeDefined();
    console.log("Authorization code:", authCode);
    console.log("✓ Authorization code obtained\n");

    // Exchange for token
    const initialTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      code: authCode,
      grantType: "authorization_code",
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    console.log("Initial token response:", initialTokenResponse.status, initialTokenResponse.data);
    expect(initialTokenResponse.status).toBe(200);
    console.log("✓ Initial access token obtained\n");

    console.log("\n=== Step 4: Initiate Password Reset Flow (scope=password:reset) ===");

    const resetAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "password:reset",
      state: `reset_${timestamp}`,
    });

    const resetAuthorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${resetAuthParams.toString()}`,
      headers: {},
    });

    // Server should redirect (302) to authentication page with authorization ID
    expect(resetAuthorizeResponse.status).toBe(302);
    const resetLocation = resetAuthorizeResponse.headers.location;
    expect(resetLocation).toBeDefined();

    // Extract authorization ID from redirect URL
    const resetAuthId = new URL(resetLocation, backendUrl).searchParams.get('id');
    expect(resetAuthId).toBeDefined();
    console.log("Password reset authorization started:", resetAuthId);
    console.log("✓ Authorization for password reset initiated\n");

    console.log("\n=== Step 5: Email Authentication Challenge ===");

    const emailChallengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${resetAuthId}/email-authentication-challenge`,
      body: {
        email: userEmail,
        provider_id: "idp-server",
      },
    });

    console.log("Email challenge response:", emailChallengeResponse.status, emailChallengeResponse.data);
    expect(emailChallengeResponse.status).toBe(200);
    console.log("✓ Email verification code sent (check logs for code)\n");

    // Extract verification code from response (mock email sender returns code)
    const verificationCode = emailChallengeResponse.data.verification_code || "123456";
    console.log(`Verification code: ${verificationCode}`);

    console.log("\n=== Step 6: Email Authentication Verification ===");

    const emailAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${resetAuthId}/email-authentication`,
      body: {
        code: verificationCode,
      },
    });

    console.log("Email auth response:", emailAuthResponse.status, emailAuthResponse.data);
    expect(emailAuthResponse.status).toBe(200);
    console.log("✓ Email verified - user authenticated\n");

    console.log("\n=== Step 7: Complete Authorization (authorize) ===");

    const finalAuthorizeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${resetAuthId}/authorize`,
      body: {},
    });

    console.log("Final authorize response:", finalAuthorizeResponse.status, finalAuthorizeResponse.data);
    expect(finalAuthorizeResponse.status).toBe(200);
    expect(finalAuthorizeResponse.data).toHaveProperty("redirect_uri");

    // Extract code from redirect_uri
    const resetRedirectUrl = new URL(finalAuthorizeResponse.data.redirect_uri);
    const resetAuthCode = resetRedirectUrl.searchParams.get('code');
    expect(resetAuthCode).toBeDefined();
    console.log("✓ Authorization code for password reset obtained\n");

    console.log("\n=== Step 8: Exchange Code for password:reset Token ===");

    const resetTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      code: resetAuthCode,
      grantType: "authorization_code",
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    console.log("Reset token response:", resetTokenResponse.status, resetTokenResponse.data);
    expect(resetTokenResponse.status).toBe(200);
    expect(resetTokenResponse.data).toHaveProperty("access_token");
    expect(resetTokenResponse.data.scope).toContain("password:reset");
    const resetAccessToken = resetTokenResponse.data.access_token;
    console.log("✓ Access token with password:reset scope obtained\n");

    console.log("\n=== Step 9: Reset Password ===");

    const resetPasswordResponse = await post({
      url: `${backendUrl}/${tenantId}/v1/me/password/reset`,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${resetAccessToken}`,
      },
      body: {
        new_password: newPassword,
      },
    });

    console.log("Reset password response:", resetPasswordResponse.status, resetPasswordResponse.data);
    expect(resetPasswordResponse.status).toBe(200);
    expect(resetPasswordResponse.data.message).toEqual("Password changed successfully.");
    console.log("✓ Password reset successful\n");

    console.log("\n=== Step 10: Verify Login with New Password ===");

    const newPasswordLoginResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: newPassword,
      scope: "openid profile email",
      clientId: clientId,
      clientSecret: clientSecret,
    });

    console.log("New password login response:", newPasswordLoginResponse.status);
    expect(newPasswordLoginResponse.status).toBe(200);
    expect(newPasswordLoginResponse.data).toHaveProperty("access_token");
    console.log("✓ Login with new password successful\n");

    console.log("\n=== Step 11: Verify Old Password No Longer Works ===");

    const oldPasswordLoginResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: initialPassword,
      scope: "openid profile email",
      clientId: clientId,
      clientSecret: clientSecret,
    });

    console.log("Old password login response:", oldPasswordLoginResponse.status);
    expect([400, 401]).toContain(oldPasswordLoginResponse.status);
    console.log("✓ Old password correctly rejected\n");

    console.log("\n=== Step 12: Verify Security Events ===");

    await sleep(1000); // Wait for security events to be persisted

    const securityEventsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      params: {
        event_type: "password_reset_success",
        limit: 10,
      },
    });

    console.log("Security events response:", securityEventsResponse.status);
    expect(securityEventsResponse.status).toBe(200);
    // Note: Security events may not be immediately available
    console.log(`✓ Security events checked: ${securityEventsResponse.data.list.length} password_reset_success event(s)\n`);

    console.log("\n=== Test Completed Successfully ===\n");
  });

  it("should reject password reset without password:reset scope", async () => {
    console.log("\n=== Testing Scope Validation ===\n");

    const timestamp = Date.now();
    const testTenantId = uuidv4();
    const testClientId = uuidv4();
    const testClientSecret = `secret-${timestamp}`;
    const redirectUri = "https://app.example.com/callback";
    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const initialPassword = `InitialPass${timestamp}!`;

    console.log("Step 1: Create test tenant and client...");

    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: uuidv4(),
        name: `Scope Test Org ${timestamp}`,
        description: "Test organization for scope validation",
      },
      tenant: {
        id: testTenantId,
        name: `Scope Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "BUSINESS",
      },
      authorization_server: {
        issuer: `${backendUrl}/${testTenantId}`,
        authorization_endpoint: `${backendUrl}/${testTenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${testTenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${testTenantId}/v1/jwks`,
        jwks: jwks,
        grant_types_supported: ["authorization_code", "password"],
        scopes_supported: ["openid", "profile", "email"],
        response_types_supported: ["code"],
        response_modes_supported: ["query", "fragment"],
        subject_types_supported: ["public"],
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
        client_id: testClientId,
        client_id_alias: `test-client-alias-${timestamp}`,
        client_secret: testClientSecret,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email",
        client_name: `Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingRequest,
    });

    console.log("Onboarding response:", onboardingResponse.status, onboardingResponse.data);
    expect(onboardingResponse.status).toBe(201);
    console.log("✓ Tenant created\n");

    console.log("Step 2: Register user...");

    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });

    expect(authorizeResponse.status).toBe(302);
    const authLocation = authorizeResponse.headers.location;
    const authId = new URL(authLocation, backendUrl).searchParams.get('id');

    await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: userEmail, password: initialPassword, name: userName },
    });

    const authCompleteResponse = await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    expect(authCompleteResponse.status).toBe(200);
    const authCompleteUrl = new URL(authCompleteResponse.data.redirect_uri);
    const authCode = authCompleteUrl.searchParams.get('code');

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
      code: authCode,
      grantType: "authorization_code",
      redirectUri: redirectUri,
      clientId: testClientId,
      clientSecret: testClientSecret,
    });

    console.log("Token response:", tokenResponse.status, tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
    const normalAccessToken = tokenResponse.data.access_token;
    console.log("✓ User registered and access token obtained (openid scope only)\n");

    console.log("Step 3: Attempt password reset with insufficient scope...");

    const resetAttemptResponse = await post({
      url: `${backendUrl}/${testTenantId}/v1/me/password/reset`,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${normalAccessToken}`,
      },
      body: {
        new_password: `NewPassword${timestamp}!`,
      },
    });

    console.log("Reset attempt response:", resetAttemptResponse.status, resetAttemptResponse.data);
    expect(resetAttemptResponse.status).toBe(403);
    expect(resetAttemptResponse.data.error).toBe("insufficient_scope");
    expect(resetAttemptResponse.data.scope).toBe("password:reset");
    console.log("✓ Password reset correctly rejected due to insufficient scope\n");

    console.log("\n=== Scope Validation Test Completed ===\n");
  });

  it("should validate password policy during reset", async () => {
    console.log("\n=== Testing Password Policy Validation ===\n");

    const timestamp = Date.now();
    const testTenantId = uuidv4();
    const testClientId = uuidv4();
    const testClientSecret = `secret-${timestamp}`;
    const redirectUri = "https://app.example.com/callback";
    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const initialPassword = `InitialPass${timestamp}!`;
    const weakPassword = "weak"; // Too short - violates policy

    console.log("Step 1: Setup tenant with password policy...");

    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: uuidv4(),
        name: `Policy Test Org ${timestamp}`,
        description: "Test organization for password policy validation",
      },
      tenant: {
        id: testTenantId,
        name: `Policy Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "BUSINESS",
        identity_policy_config: {
          identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID",
          password_policy: {
            min_length: 10,
            require_uppercase: true,
            require_lowercase: true,
            require_number: true,
            require_special_char: true,
            max_history: 0,
            max_length: 72
          }
        }
      },
      authorization_server: {
        issuer: `${backendUrl}/${testTenantId}`,
        authorization_endpoint: `${backendUrl}/${testTenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${testTenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${testTenantId}/v1/jwks`,
        jwks: jwks,
        grant_types_supported: ["authorization_code", "password"],
        scopes_supported: ["openid", "password:reset", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query", "fragment"],
        subject_types_supported: ["public"],
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
        client_id: testClientId,
        client_id_alias: `test-client-alias-policy-${timestamp}`,
        client_secret: testClientSecret,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "password"],
        scope: "openid password:reset management",
        client_name: `Policy Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingRequest,
    });

    console.log("Onboarding response (policy test):", onboardingResponse.status, onboardingResponse.data);
    expect(onboardingResponse.status).toBe(201);
    console.log("✓ Tenant with password policy created\n");

    // Get admin token for Email auth config creation
    const createdClient3 = onboardingResponse.data.client;
    const adminToken3Response = await requestToken({
      endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingRequest.user.email,
      password: onboardingRequest.user.raw_password,
      scope: "management",
      clientId: createdClient3.client_id,
      clientSecret: createdClient3.client_secret,
    });
    expect(adminToken3Response.status).toBe(200);
    const adminToken3 = adminToken3Response.data.access_token;

    // Create email authentication configuration
    const emailAuthConfig3Id = uuidv4();
    const emailAuthConfig3Response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${onboardingRequest.organization.id}/tenants/${testTenantId}/authentication-configurations`,
      headers: {
        Authorization: `Bearer ${adminToken3}`,
      },
      body: {
        id: emailAuthConfig3Id,
        type: "email",
        attributes: {},
        metadata: {
          type: "external",
          description: "Email authentication for password reset",
          sender: "test@gmail.com",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
          templates: {
            registration: {
              subject: "[ID Verification] Your signup email confirmation code",
              body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\nIf you did not request this, please contact your administrator.\n\n– IDP Support"
            },
            authentication: {
              subject: "[ID Verification] Your login email confirmation code",
              body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\nIf you did not request this, please contact your administrator.\n\n– IDP Support"
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
                  phone_number: {
                    type: "string"
                  },
                  template: {
                    type: "string"
                  }
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
                  {
                    static_value: "application/json",
                    to: "Content-Type"
                  }
                ],
                body_mapping_rules: [
                  {
                    from: "$.request_body",
                    to: "*"
                  }
                ]
              },
              http_request_store: {
                key: "email-authentication-challenge",
                interaction_mapping_rules: [
                  {
                    from: "$.response_body.transaction_id",
                    to: "transaction_id"
                  }
                ]
              }
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body",
                  to: "*"
                }
              ]
            }
          },
          "email-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  verification_code: {
                    type: "string"
                  }
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
                  {
                    static_value: "application/json",
                    to: "Content-Type"
                  }
                ],
                body_mapping_rules: [
                  {
                    from: "$.request_body",
                    to: "*"
                  }
                ]
              }
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body",
                  to: "*"
                }
              ]
            }
          }
        }
      }
    });
    expect(emailAuthConfig3Response.status).toBe(201);
    console.log("✓ Email authentication configuration created for policy test\n");

    console.log("Step 2: Register user and get password:reset token...");

    // Register user (abbreviated - same as previous test)
    const authParams3 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid",
      state: `state_${timestamp}`,
    });

    const authResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${authParams3.toString()}`,
      headers: {},
    });

    expect(authResponse.status).toBe(302);
    const authLoc = authResponse.headers.location;
    const authId = new URL(authLoc, backendUrl).searchParams.get('id');

    await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: userEmail, password: initialPassword, name: userName },
    });

    const authCompleteResponse = await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    expect(authCompleteResponse.status).toBe(200);
    const firstRedirectUrl = new URL(authCompleteResponse.data.redirect_uri);
    const firstAuthCode = firstRedirectUrl.searchParams.get('code');

    const firstTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
      code: firstAuthCode,
      grantType: "authorization_code",
      redirectUri: redirectUri,
      clientId: testClientId,
      clientSecret: testClientSecret,
    });

    expect(firstTokenResponse.status).toBe(200);

    // Now get password:reset token
    const resetAuthParams3 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "password:reset",
      state: `reset_${timestamp}`,
    });

    const resetAuthResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${resetAuthParams3.toString()}`,
      headers: {},
    });

    expect(resetAuthResponse.status).toBe(302);
    const resetAuthLoc = resetAuthResponse.headers.location;
    const resetAuthId = new URL(resetAuthLoc, backendUrl).searchParams.get('id');

    const emailChallengeResponse = await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${resetAuthId}/email-authentication-challenge`,
      body: { email: userEmail, provider_id: "idp-server" },
    });

    const verificationCode = emailChallengeResponse.data.verification_code || "123456";

    await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${resetAuthId}/email-authentication`,
      body: { code: verificationCode },
    });

    const resetAuthCompleteResponse = await postWithJson({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${resetAuthId}/authorize`,
      body: {},
    });

    expect(resetAuthCompleteResponse.status).toBe(200);
    const resetCompleteUrl = new URL(resetAuthCompleteResponse.data.redirect_uri);
    const resetCode = resetCompleteUrl.searchParams.get('code');

    const resetTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
      code: resetCode,
      grantType: "authorization_code",
      redirectUri: redirectUri,
      clientId: testClientId,
      clientSecret: testClientSecret,
    });

    expect(resetTokenResponse.status).toBe(200);
    const resetAccessToken = resetTokenResponse.data.access_token;
    console.log("✓ password:reset token obtained\n");

    console.log("Step 3: Attempt reset with weak password (policy violation)...");

    const weakPasswordResetResponse = await post({
      url: `${backendUrl}/${testTenantId}/v1/me/password/reset`,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${resetAccessToken}`,
      },
      body: {
        new_password: weakPassword,
      },
    });

    console.log("Weak password reset response:", weakPasswordResetResponse.status, weakPasswordResetResponse.data);
    expect(weakPasswordResetResponse.status).toBe(400);
    expect(weakPasswordResetResponse.data.error).toBe("invalid_new_password");
    console.log("✓ Weak password correctly rejected by policy\n");

    console.log("\n=== Password Policy Validation Test Completed ===\n");
  });

  /**
   * Issue #1011: Verify authentication policy priority and description are correctly applied
   *
   * This test verifies that:
   * 1. Authentication policy with priority and description can be created
   * 2. The policy is correctly applied during authorization flow
   * 3. view-data API returns the correct priority and description
   */
  it("should apply authentication policy with priority and description", async () => {
    const timestamp = Date.now();
    const testOrgId = uuidv4();
    const testTenantId = uuidv4();
    const testClientId = uuidv4();
    const testClientSecret = `client-secret-policy-${timestamp}`;
    const redirectUri = `https://app.example.com/callback`;

    console.log("\n=== Authentication Policy Test: Setup ===");

    const { jwks } = await generateRS256KeyPair();

    // Step 1: Create Organization and Tenant
    const onboardingRequest = {
      organization: {
        id: testOrgId,
        name: `Auth Policy Test Org ${timestamp}`,
        description: "Test organization for authentication policy",
      },
      tenant: {
        id: testTenantId,
        name: `Auth Policy Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
      },
      authorization_server: {
        issuer: `${backendUrl}/${testTenantId}`,
        authorization_endpoint: `${backendUrl}/${testTenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        token_endpoint_auth_signing_alg_values_supported: ["RS256"],
        userinfo_endpoint: `${backendUrl}/${testTenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${testTenantId}/v1/jwks`,
        jwks: jwks,
        grant_types_supported: ["authorization_code", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256"],
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
        client_id_alias: `admin-client-policy-${timestamp}`,
        client_secret: `admin-secret-policy-${timestamp}`,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "password"],
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

    // Get admin token
    const createdClient = onboardingResponse.data.client;
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
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
    console.log("\n=== Step 2: Create Password Authentication Configuration ===");

    const passwordAuthConfigId = uuidv4();
    const passwordAuthConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/authentication-configurations`,
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

    // Step 3: Create Authentication Policy with priority and description
    console.log("\n=== Step 3: Create Authentication Policy (Issue #1011) ===");

    const authPolicyConfigId = uuidv4();
    const policyDescription = "default_password_policy";
    const policyPriority = 10;

    const authPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/authentication-policies`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: authPolicyConfigId,
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: policyDescription,
            priority: policyPriority,
            conditions: {
              scopes: ["openid"]
            },
            available_methods: ["password"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
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

    // Issue #1011: Verify priority and description in response
    expect(authPolicyResponse.data.result.policies[0]).toHaveProperty("priority", policyPriority);
    expect(authPolicyResponse.data.result.policies[0]).toHaveProperty("description", policyDescription);
    console.log("✓ Authentication policy created with priority and description\n");

    // Step 4: Create Client
    console.log("\n=== Step 4: Create Test Client ===");

    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/clients`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        client_id: testClientId,
        client_secret: testClientSecret,
        client_name: "Auth Policy Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: "openid profile",
        token_endpoint_auth_method: "client_secret_post",
      },
    });

    expect(createClientResponse.status).toBe(201);
    console.log("✓ Client created\n");

    // Step 5: Start authorization and check view-data for policy
    console.log("\n=== Step 5: Start Authorization and Verify Policy in view-data ===");

    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile",
      state: `state_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    expect(authorizeResponse.status).toBe(302);
    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get('id');
    expect(authId).toBeDefined();
    console.log("Authorization started:", authId);

    // Get view-data to verify authentication policy is applied
    const viewDataResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${authId}/view-data`,
      headers: {},
    });

    console.log("View data response:", viewDataResponse.status, JSON.stringify(viewDataResponse.data, null, 2));
    expect(viewDataResponse.status).toBe(200);

    // Issue #1011: Verify authentication_policy contains priority and description
    expect(viewDataResponse.data).toHaveProperty("authentication_policy");
    const authPolicy = viewDataResponse.data.authentication_policy;
    expect(authPolicy).toHaveProperty("priority", policyPriority);
    expect(authPolicy).toHaveProperty("description", policyDescription);
    expect(authPolicy).toHaveProperty("available_methods");
    expect(authPolicy.available_methods).toContain("password");

    console.log("✓ Authentication policy correctly applied with priority and description\n");

    console.log("\n=== Authentication Policy Test Completed ===\n");
  });

  it("should apply the highest priority policy when multiple policies exist (Issue #1011)", async () => {
    const timestamp = Date.now();
    const testOrgId = uuidv4();
    const testTenantId = uuidv4();
    const testClientId = uuidv4();
    const testClientSecret = `client-secret-${timestamp}`;
    const redirectUri = `https://app.example.com/callback`;

    console.log("\n=== Multiple Policies Priority Test (Issue #1011) ===\n");

    // Step 1: Create organization and tenant
    console.log("=== Step 1: Create Test Organization and Tenant ===");

    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: testOrgId,
        name: `Multi-Policy Test Org ${timestamp}`,
        description: "Test organization for multiple policies priority test",
      },
      tenant: {
        id: testTenantId,
        name: `Multi-Policy Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
      },
      authorization_server: {
        issuer: `${backendUrl}/${testTenantId}`,
        authorization_endpoint: `${backendUrl}/${testTenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        token_endpoint_auth_signing_alg_values_supported: ["RS256", "ES256"],
        userinfo_endpoint: `${backendUrl}/${testTenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${testTenantId}/v1/jwks`,
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
        token_introspection_endpoint: `${backendUrl}/${testTenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${testTenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${testTenantId}/v1/backchannel/authentications`,
        extension: {
          access_token_type: "JWT",
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          access_token_duration: 3600,
          refresh_token_duration: 86400,
          session_duration: 3600,
        },
      },
      client: {
        client_id: uuidv4(),
        client_secret: `admin-secret-${timestamp}`,
        client_name: "Multi-Policy Admin Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile management",
        token_endpoint_auth_method: "client_secret_post",
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: faker.internet.email(),
        name: faker.person.fullName(),
        raw_password: `AdminPass${timestamp}!`,
        roles: [{ name: "admin" }],
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
      body: onboardingRequest,
    });

    console.log("Multi-Policy Onboarding response:", onboardingResponse.status);
    expect(onboardingResponse.status).toBe(201);
    console.log("✓ Organization and tenant created\n");

    // Get admin token
    const createdClient = onboardingResponse.data.client;
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
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
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/authentication-configurations`,
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

    // Step 3: Create Authentication Policy with MULTIPLE policies with different priorities
    console.log("=== Step 3: Create Authentication Policy with Multiple Policies ===");

    const authPolicyConfigId = uuidv4();

    // Create policies with different priorities
    // Note: Higher priority number = higher priority (max() is used for selection)
    // Policy with priority 100 (highest priority - should be selected for openid scope)
    // Policy with priority 1 (lowest priority - should not be selected when priority 100 matches)
    // Policy with priority 50 (medium priority - matches profile scope specifically)
    const authPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/authentication-policies`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: authPolicyConfigId,
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "low_priority_fallback_policy",
            priority: 1,
            conditions: {
              scopes: ["openid"]
            },
            available_methods: ["password"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1
                  }
                ]
              ]
            }
          },
          {
            description: "high_priority_main_policy",
            priority: 100,
            conditions: {
              scopes: ["openid"]
            },
            available_methods: ["password"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1
                  }
                ]
              ]
            }
          },
          {
            description: "medium_priority_profile_policy",
            priority: 50,
            conditions: {
              scopes: ["profile"]
            },
            available_methods: ["password"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
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

    // Verify all policies have priority and description
    const policies = authPolicyResponse.data.result.policies;
    expect(policies.length).toBe(3);

    policies.forEach(policy => {
      expect(policy).toHaveProperty("priority");
      expect(policy).toHaveProperty("description");
    });
    console.log("✓ Multiple authentication policies created with priority and description\n");

    // Step 4: Create Client
    console.log("=== Step 4: Create Test Client ===");

    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/clients`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        client_id: testClientId,
        client_secret: testClientSecret,
        client_name: "Multi-Policy Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: "openid profile",
        token_endpoint_auth_method: "client_secret_post",
      },
    });

    expect(createClientResponse.status).toBe(201);
    console.log("✓ Client created\n");

    // Step 5: Start authorization with scope=openid and verify HIGH PRIORITY policy is applied
    console.log("=== Step 5: Verify Highest Priority Policy is Applied ===");

    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile",
      state: `state_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    expect(authorizeResponse.status).toBe(302);
    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get('id');
    expect(authId).toBeDefined();
    console.log("Authorization started:", authId);

    // Get view-data to verify highest priority policy is applied
    const viewDataResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${authId}/view-data`,
      headers: {},
    });

    console.log("View data response:", viewDataResponse.status, JSON.stringify(viewDataResponse.data, null, 2));
    expect(viewDataResponse.status).toBe(200);

    // Verify the HIGHEST PRIORITY policy (priority=100, description="high_priority_main_policy") is applied
    expect(viewDataResponse.data).toHaveProperty("authentication_policy");
    const appliedPolicy = viewDataResponse.data.authentication_policy;

    // The policy with priority=100 should be selected (highest number = highest priority)
    expect(appliedPolicy).toHaveProperty("priority", 100);
    expect(appliedPolicy).toHaveProperty("description", "high_priority_main_policy");
    expect(appliedPolicy).toHaveProperty("available_methods");
    expect(appliedPolicy.available_methods).toContain("password");

    console.log("✓ Highest priority policy (priority=100) correctly applied\n");
    console.log("  - Applied policy description:", appliedPolicy.description);
    console.log("  - Applied policy priority:", appliedPolicy.priority);

    console.log("\n=== Multiple Policies Priority Test Completed ===\n");
  });

  it("should apply correct policy based on client_ids condition (Documentation verification)", async () => {
    const timestamp = Date.now();
    const testOrgId = uuidv4();
    const testTenantId = uuidv4();
    const adminClientId = uuidv4();
    const userClientId = uuidv4();
    const otherClientId = uuidv4();
    const redirectUri = `https://app.example.com/callback`;

    console.log("\n=== Client-based Policy Selection Test (Documentation Verification) ===\n");

    // Step 1: Create organization and tenant
    console.log("=== Step 1: Create Test Organization and Tenant ===");

    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: testOrgId,
        name: `Client Policy Test Org ${timestamp}`,
        description: "Test organization for client-based policy selection",
      },
      tenant: {
        id: testTenantId,
        name: `Client Policy Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
      },
      authorization_server: {
        issuer: `${backendUrl}/${testTenantId}`,
        authorization_endpoint: `${backendUrl}/${testTenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        token_endpoint_auth_signing_alg_values_supported: ["RS256", "ES256"],
        userinfo_endpoint: `${backendUrl}/${testTenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${testTenantId}/v1/jwks`,
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
        token_introspection_endpoint: `${backendUrl}/${testTenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${testTenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${testTenantId}/v1/backchannel/authentications`,
        extension: {
          access_token_type: "JWT",
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          access_token_duration: 3600,
          refresh_token_duration: 86400,
          session_duration: 3600,
        },
      },
      client: {
        client_id: uuidv4(),
        client_secret: `admin-secret-${timestamp}`,
        client_name: "Client Policy Admin Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile management",
        token_endpoint_auth_method: "client_secret_post",
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: faker.internet.email(),
        name: faker.person.fullName(),
        raw_password: `AdminPass${timestamp}!`,
        roles: [{ name: "admin" }],
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
      body: onboardingRequest,
    });

    console.log("Client Policy Onboarding response:", onboardingResponse.status);
    expect(onboardingResponse.status).toBe(201);
    console.log("✓ Organization and tenant created\n");

    // Get admin token
    const createdClient = onboardingResponse.data.client;
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
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
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/authentication-configurations`,
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

    // Step 3: Create Authentication Policy with client_ids conditions (matching documentation)
    console.log("=== Step 3: Create Authentication Policy (Matching Documentation) ===");

    const authPolicyConfigId = uuidv4();

    // Documentation example:
    // - admin-app → priority 100 (highest - password + webauthn)
    // - user-app → priority 50 (password only)
    // - default (no client_ids) → priority 1 (lowest - password only)
    const authPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/authentication-policies`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: authPolicyConfigId,
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "admin app - high security",
            priority: 100,
            conditions: {
              client_ids: [adminClientId]
            },
            available_methods: ["password"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1
                  }
                ]
              ]
            }
          },
          {
            description: "normal app - standard security",
            priority: 50,
            conditions: {
              client_ids: [userClientId]
            },
            available_methods: ["password"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1
                  }
                ]
              ]
            }
          },
          {
            description: "default - password only",
            priority: 1,
            conditions: {},
            available_methods: ["password"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
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
    console.log("✓ Authentication policies created with client_ids conditions\n");

    // Step 4: Create three clients (admin-app, user-app, other-app)
    console.log("=== Step 4: Create Test Clients ===");

    // admin-app client
    const createAdminClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/clients`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        client_id: adminClientId,
        client_secret: `admin-client-secret-${timestamp}`,
        client_name: "admin-app",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: "openid profile",
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    expect(createAdminClientResponse.status).toBe(201);
    console.log("✓ admin-app client created");

    // user-app client
    const createUserClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/clients`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        client_id: userClientId,
        client_secret: `user-client-secret-${timestamp}`,
        client_name: "user-app",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: "openid profile",
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    expect(createUserClientResponse.status).toBe(201);
    console.log("✓ user-app client created");

    // other-app client
    const createOtherClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/clients`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        client_id: otherClientId,
        client_secret: `other-client-secret-${timestamp}`,
        client_name: "other-app",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: "openid profile",
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    expect(createOtherClientResponse.status).toBe(201);
    console.log("✓ other-app client created\n");

    // Step 5: Test with admin-app → should get priority 100
    console.log("=== Step 5a: Test admin-app (Expected: priority 100) ===");

    const adminAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: adminClientId,
      redirect_uri: redirectUri,
      scope: "openid profile",
      state: `state_admin_${timestamp}`,
    });

    const adminAuthorizeResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${adminAuthParams.toString()}`,
      headers: {},
    });

    expect(adminAuthorizeResponse.status).toBe(302);
    const adminLocation = adminAuthorizeResponse.headers.location;
    const adminAuthId = new URL(adminLocation, backendUrl).searchParams.get('id');
    expect(adminAuthId).toBeDefined();

    const adminViewDataResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${adminAuthId}/view-data`,
      headers: {},
    });

    console.log("admin-app view data:", JSON.stringify(adminViewDataResponse.data.authentication_policy, null, 2));
    expect(adminViewDataResponse.status).toBe(200);
    expect(adminViewDataResponse.data.authentication_policy).toHaveProperty("priority", 100);
    expect(adminViewDataResponse.data.authentication_policy).toHaveProperty("description", "admin app - high security");
    console.log("✓ admin-app correctly got priority 100 policy\n");

    // Step 5b: Test with user-app → should get priority 50
    console.log("=== Step 5b: Test user-app (Expected: priority 50) ===");

    const userAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: userClientId,
      redirect_uri: redirectUri,
      scope: "openid profile",
      state: `state_user_${timestamp}`,
    });

    const userAuthorizeResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${userAuthParams.toString()}`,
      headers: {},
    });

    expect(userAuthorizeResponse.status).toBe(302);
    const userLocation = userAuthorizeResponse.headers.location;
    const userAuthId = new URL(userLocation, backendUrl).searchParams.get('id');
    expect(userAuthId).toBeDefined();

    const userViewDataResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${userAuthId}/view-data`,
      headers: {},
    });

    console.log("user-app view data:", JSON.stringify(userViewDataResponse.data.authentication_policy, null, 2));
    expect(userViewDataResponse.status).toBe(200);
    expect(userViewDataResponse.data.authentication_policy).toHaveProperty("priority", 50);
    expect(userViewDataResponse.data.authentication_policy).toHaveProperty("description", "normal app - standard security");
    console.log("✓ user-app correctly got priority 50 policy\n");

    // Step 5c: Test with other-app → should get priority 1 (default)
    console.log("=== Step 5c: Test other-app (Expected: priority 1 - default) ===");

    const otherAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: otherClientId,
      redirect_uri: redirectUri,
      scope: "openid profile",
      state: `state_other_${timestamp}`,
    });

    const otherAuthorizeResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${otherAuthParams.toString()}`,
      headers: {},
    });

    expect(otherAuthorizeResponse.status).toBe(302);
    const otherLocation = otherAuthorizeResponse.headers.location;
    const otherAuthId = new URL(otherLocation, backendUrl).searchParams.get('id');
    expect(otherAuthId).toBeDefined();

    const otherViewDataResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${otherAuthId}/view-data`,
      headers: {},
    });

    console.log("other-app view data:", JSON.stringify(otherViewDataResponse.data.authentication_policy, null, 2));
    expect(otherViewDataResponse.status).toBe(200);
    expect(otherViewDataResponse.data.authentication_policy).toHaveProperty("priority", 1);
    expect(otherViewDataResponse.data.authentication_policy).toHaveProperty("description", "default - password only");
    console.log("✓ other-app correctly got priority 1 (default) policy\n");

    console.log("\n=== Client-based Policy Selection Test Completed ===\n");
    console.log("Summary:");
    console.log("  - admin-app → priority 100 (highest) ✓");
    console.log("  - user-app  → priority 50  ✓");
    console.log("  - other-app → priority 1 (default/lowest) ✓");
    console.log("\nDocumentation is CORRECT: Higher priority number = Higher priority\n");
  });

  it("should apply correct policy based on acr_values condition", async () => {
    const timestamp = Date.now();
    const testOrgId = uuidv4();
    const testTenantId = uuidv4();
    const testClientId = uuidv4();
    const testClientSecret = `client-secret-${timestamp}`;
    const redirectUri = `https://app.example.com/callback`;

    console.log("\n=== ACR Values Policy Selection Test ===\n");

    // Step 1: Create organization and tenant
    console.log("=== Step 1: Create Test Organization and Tenant ===");

    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: testOrgId,
        name: `ACR Policy Test Org ${timestamp}`,
        description: "Test organization for acr_values policy selection",
      },
      tenant: {
        id: testTenantId,
        name: `ACR Policy Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
      },
      authorization_server: {
        issuer: `${backendUrl}/${testTenantId}`,
        authorization_endpoint: `${backendUrl}/${testTenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        token_endpoint_auth_signing_alg_values_supported: ["RS256", "ES256"],
        userinfo_endpoint: `${backendUrl}/${testTenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${testTenantId}/v1/jwks`,
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
        acr_values_supported: [
          "urn:mace:incommon:iap:gold",
          "urn:mace:incommon:iap:silver",
          "urn:mace:incommon:iap:bronze"
        ],
        token_introspection_endpoint: `${backendUrl}/${testTenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${testTenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${testTenantId}/v1/backchannel/authentications`,
        extension: {
          access_token_type: "JWT",
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          access_token_duration: 3600,
          refresh_token_duration: 86400,
          session_duration: 3600,
        },
      },
      client: {
        client_id: uuidv4(),
        client_secret: `admin-secret-${timestamp}`,
        client_name: "ACR Policy Admin Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile management",
        token_endpoint_auth_method: "client_secret_post",
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: faker.internet.email(),
        name: faker.person.fullName(),
        raw_password: `AdminPass${timestamp}!`,
        roles: [{ name: "admin" }],
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
      body: onboardingRequest,
    });

    console.log("ACR Policy Onboarding response:", onboardingResponse.status);
    expect(onboardingResponse.status).toBe(201);
    console.log("✓ Organization and tenant created\n");

    // Get admin token
    const createdClient = onboardingResponse.data.client;
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${testTenantId}/v1/tokens`,
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
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/authentication-configurations`,
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

    // Step 3: Create Authentication Policy with acr_values conditions
    console.log("=== Step 3: Create Authentication Policy with ACR Values Conditions ===");

    const authPolicyConfigId = uuidv4();

    // ACR-based policy selection:
    // - gold ACR → priority 100 (high security, requires MFA-level)
    // - silver ACR → priority 50 (medium security)
    // - default (no ACR) → priority 1 (basic password)
    const authPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/authentication-policies`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        id: authPolicyConfigId,
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "gold_acr_high_security_policy",
            priority: 100,
            conditions: {
              acr_values: ["urn:mace:incommon:iap:gold"]
            },
            available_methods: ["password"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1
                  }
                ]
              ]
            }
          },
          {
            description: "silver_acr_medium_security_policy",
            priority: 50,
            conditions: {
              acr_values: ["urn:mace:incommon:iap:silver"]
            },
            available_methods: ["password"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1
                  }
                ]
              ]
            }
          },
          {
            description: "default_basic_policy",
            priority: 1,
            conditions: {},
            available_methods: ["password"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
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
    console.log("✓ Authentication policies created with acr_values conditions\n");

    // Step 4: Create Test Client
    console.log("=== Step 4: Create Test Client ===");

    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${testOrgId}/tenants/${testTenantId}/clients`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        client_id: testClientId,
        client_secret: testClientSecret,
        client_name: "ACR Policy Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: "openid profile",
        token_endpoint_auth_method: "client_secret_post",
      },
    });

    expect(createClientResponse.status).toBe(201);
    console.log("✓ Client created\n");

    // Step 5a: Test with acr_values=gold → should get priority 100
    console.log("=== Step 5a: Test with acr_values=gold (Expected: priority 100) ===");

    const goldAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile",
      state: `state_gold_${timestamp}`,
      acr_values: "urn:mace:incommon:iap:gold",
    });

    const goldAuthorizeResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${goldAuthParams.toString()}`,
      headers: {},
    });

    expect(goldAuthorizeResponse.status).toBe(302);
    const goldLocation = goldAuthorizeResponse.headers.location;
    const goldAuthId = new URL(goldLocation, backendUrl).searchParams.get('id');
    expect(goldAuthId).toBeDefined();

    const goldViewDataResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${goldAuthId}/view-data`,
      headers: {},
    });

    console.log("gold ACR view data:", JSON.stringify(goldViewDataResponse.data.authentication_policy, null, 2));
    expect(goldViewDataResponse.status).toBe(200);
    expect(goldViewDataResponse.data.authentication_policy).toHaveProperty("priority", 100);
    expect(goldViewDataResponse.data.authentication_policy).toHaveProperty("description", "gold_acr_high_security_policy");
    console.log("✓ gold ACR correctly got priority 100 policy\n");

    // Step 5b: Test with acr_values=silver → should get priority 50
    console.log("=== Step 5b: Test with acr_values=silver (Expected: priority 50) ===");

    const silverAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile",
      state: `state_silver_${timestamp}`,
      acr_values: "urn:mace:incommon:iap:silver",
    });

    const silverAuthorizeResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${silverAuthParams.toString()}`,
      headers: {},
    });

    expect(silverAuthorizeResponse.status).toBe(302);
    const silverLocation = silverAuthorizeResponse.headers.location;
    const silverAuthId = new URL(silverLocation, backendUrl).searchParams.get('id');
    expect(silverAuthId).toBeDefined();

    const silverViewDataResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${silverAuthId}/view-data`,
      headers: {},
    });

    console.log("silver ACR view data:", JSON.stringify(silverViewDataResponse.data.authentication_policy, null, 2));
    expect(silverViewDataResponse.status).toBe(200);
    expect(silverViewDataResponse.data.authentication_policy).toHaveProperty("priority", 50);
    expect(silverViewDataResponse.data.authentication_policy).toHaveProperty("description", "silver_acr_medium_security_policy");
    console.log("✓ silver ACR correctly got priority 50 policy\n");

    // Step 5c: Test without acr_values → should get priority 1 (default)
    console.log("=== Step 5c: Test without acr_values (Expected: priority 1 - default) ===");

    const defaultAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile",
      state: `state_default_${timestamp}`,
      // No acr_values specified
    });

    const defaultAuthorizeResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations?${defaultAuthParams.toString()}`,
      headers: {},
    });

    expect(defaultAuthorizeResponse.status).toBe(302);
    const defaultLocation = defaultAuthorizeResponse.headers.location;
    const defaultAuthId = new URL(defaultLocation, backendUrl).searchParams.get('id');
    expect(defaultAuthId).toBeDefined();

    const defaultViewDataResponse = await get({
      url: `${backendUrl}/${testTenantId}/v1/authorizations/${defaultAuthId}/view-data`,
      headers: {},
    });

    console.log("default (no ACR) view data:", JSON.stringify(defaultViewDataResponse.data.authentication_policy, null, 2));
    expect(defaultViewDataResponse.status).toBe(200);
    expect(defaultViewDataResponse.data.authentication_policy).toHaveProperty("priority", 1);
    expect(defaultViewDataResponse.data.authentication_policy).toHaveProperty("description", "default_basic_policy");
    console.log("✓ no acr_values correctly got priority 1 (default) policy\n");

    console.log("\n=== ACR Values Policy Selection Test Completed ===\n");
    console.log("Summary:");
    console.log("  - acr_values=gold   → priority 100 (gold_acr_high_security_policy) ✓");
    console.log("  - acr_values=silver → priority 50 (silver_acr_medium_security_policy) ✓");
    console.log("  - no acr_values     → priority 1 (default_basic_policy) ✓");
    console.log("\nacr_values condition routing is working correctly!\n");
  });
});
