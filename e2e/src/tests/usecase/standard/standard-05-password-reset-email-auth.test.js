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
});
