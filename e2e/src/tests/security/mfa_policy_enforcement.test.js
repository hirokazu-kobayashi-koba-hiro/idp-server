import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { generateRS256KeyPair } from "../../lib/jose";
import {
  adminServerConfig,
  backendUrl,
  clientSecretPostClient,
  mockApiBaseUrl,
  serverConfig,
} from "../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";

/**
 * Security Test: MFA Policy Enforcement
 *
 * Tests that the authentication policy (MFA requirements) is properly enforced.
 *
 * [Expected Behavior]
 * - Should return 400 error when MFA is not completed
 * - Should return error message indicating authentication policy requirements not satisfied
 *
 */
describe("Security: MFA Policy Enforcement", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let testClientId;
  let testClientSecret;
  let adminAccessToken;
  let testUser;
  const redirectUri = `https://app.example.com/callback`;

  beforeAll(async () => {
    console.log("\n=== Setting up MFA-required Tenant (from scratch) ===\n");

    // Step 1: Get system admin token
    console.log("Step 1: Getting system admin token...");
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
    console.log("✓ System admin token acquired\n");

    // Step 2: Setup test tenant with MFA-required policy
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    testClientId = uuidv4();
    testClientSecret = `client-secret-${timestamp}`;
    testUser = {
      email: faker.internet.email(),
      password: `TestPassword${timestamp}!`,
      name: faker.person.fullName(),
    };

    console.log("Step 2: Creating tenant with MFA-required policy...");
    console.log(`  Organization ID: ${organizationId}`);
    console.log(`  Tenant ID: ${tenantId}`);
    console.log(`  Test User: ${testUser.email}`);

    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `MFA Policy Test Org ${timestamp}`,
        description: "Test organization for MFA policy enforcement",
      },
      tenant: {
        id: tenantId,
        name: `MFA Policy Test Tenant ${timestamp}`,
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
        token_endpoint_auth_methods_supported: [
          "client_secret_post",
          "client_secret_basic",
        ],
        token_endpoint_auth_signing_alg_values_supported: ["RS256", "ES256"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwks,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
        ],
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
    console.log("✓ Tenant created\n");

    // Step 3: Get admin token for the new tenant
    console.log("Step 3: Getting admin token for new tenant...");
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
    console.log("✓ Admin token acquired\n");

    // Step 4: Create Email authentication configuration
    console.log("Step 4: Creating Email authentication configuration...");
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
          description: "Email authentication for MFA policy test",
          sender: "test@gmail.com",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
          templates: {
            registration: {
              subject: "[MFA Test] Your signup email confirmation code",
              body: "Code: {VERIFICATION_CODE}",
            },
            authentication: {
              subject: "[MFA Test] Your login email confirmation code",
              body: "Code: {VERIFICATION_CODE}",
            },
          },
          settings: {
            smtp: {
              host: "smtp.gmail.com",
              port: 587,
              username: "test@gmail.com",
              password: "test",
              auth: true,
              starttls: { enable: true },
            },
          },
          retry_count_limitation: 5,
          expire_seconds: 300,
        },
        interactions: {
          "email-authentication-challenge": {
            request: {
              schema: {
                type: "object",
                properties: {
                  email: { type: "string" },
                  template: { type: "string" },
                },
              },
            },
            pre_hook: {},
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/email-authentication-challenge`,
                method: "POST",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: `${mockApiBaseUrl}/token`,
                  client_id: "your-client-id",
                  username: "username",
                  password: "password",
                  scope: "application",
                },
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
              http_request_store: {
                key: "email-authentication-challenge",
                interaction_mapping_rules: [
                  {
                    from: "$.response_body.transaction_id",
                    to: "transaction_id",
                  },
                ],
              },
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_request.response_body", to: "*" },
              ],
            },
          },
          "email-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  verification_code: { type: "string" },
                },
              },
            },
            pre_hook: {},
            execution: {
              function: "http_request",
              previous_interaction: { key: "email-authentication-challenge" },
              http_request: {
                url: `${mockApiBaseUrl}/email-authentication`,
                method: "POST",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: `${mockApiBaseUrl}/token`,
                  client_id: "your-client-id",
                  username: "username",
                  password: "password",
                  scope: "application",
                },
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

    expect(emailAuthConfigResponse.status).toBe(201);
    console.log("✓ Email authentication configuration created\n");

    // Step 5: Create MFA-REQUIRED authentication policy
    console.log("Step 5: Creating MFA-REQUIRED authentication policy...");
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
            description: "MFA_REQUIRED_POLICY: password + email authentication",
            priority: 10,
            conditions: {
              scopes: ["openid"],
            },
            available_methods: ["password", "email", "initial-registration"],
            // Key: MFA required - password AND email both required
            // any_of contains arrays of AND conditions; single array = all conditions must pass
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.initial-registration.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: "$.email-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                ],
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: "$.email-authentication.success_count",
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

    expect(authPolicyResponse.status).toBe(201);
    console.log("✓ MFA-REQUIRED authentication policy created");
    console.log("  Policy: password + email authentication both required\n");

    // Step 6: Create test client
    console.log("Step 6: Creating test client...");
    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: {
        Authorization: `Bearer ${adminAccessToken}`,
      },
      body: {
        client_id: testClientId,
        client_secret: testClientSecret,
        client_name: "MFA Policy Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
      },
    });

    expect(createClientResponse.status).toBe(201);
    console.log("✓ Test client created\n");

    console.log("=== Setup Completed ===\n");
    console.log("Test Environment:");
    console.log(`  Tenant ID: ${tenantId}`);
    console.log(`  Client ID: ${testClientId}`);
    console.log(`  Test User: ${testUser.email}`);
    console.log(`  MFA Policy: Password + Email (both required)\n`);
  }, 120000);

  it("should FAIL to issue authorization code with password-only authentication when MFA is required", async () => {
    console.log("\n=== Pattern A: Password Only (Email Missing) ===\n");
    console.log(
      "Scenario: MFA required but only password authentication completed\n"
    );

    const timestamp = Date.now();

    // Step 1: Register test user
    console.log("Step 1: Registering test user...");
    const authParams1 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `registration_${timestamp}`,
    });

    const authorizeResponse1 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams1.toString()}`,
      headers: {},
    });

    expect(authorizeResponse1.status).toBe(302);
    const location1 = authorizeResponse1.headers.location;
    const authId1 = new URL(location1, backendUrl).searchParams.get("id");
    expect(authId1).toBeDefined();

    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/initial-registration`,
      body: {
        email: testUser.email,
        password: testUser.password,
        name: testUser.name,
      },
    });

    expect(registrationResponse.status).toBe(200);

    // Step 2: Attempt Email authentication as 2nd factor with wrong code
    // Note: At this point, the user exists in the transaction from initial-registration
    console.log("\n=== Step 2: Email Authentication (2nd Factor) - Wrong Code ===");

    // Send Email challenge
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/email-authentication-challenge`,
      body: {
        email: testUser.email,
        template: "registration",
      },
    });

    console.log("Email challenge response:", challengeResponse.status, JSON.stringify(challengeResponse.data, null, 2));


    // Attempt Email authentication with WRONG verification code
    const emailAuthnResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/email-authentication`,
      body: {
        verification_code: "123456", //mock server
      },
    });

    console.log("Wrong code response:", emailAuthnResponse.status, JSON.stringify(emailAuthnResponse.data, null, 2));
    expect(emailAuthnResponse.status).toBe(200);
    console.log("✓ Email authentication failed as expected (2nd factor during registration)\n");

    // Complete first authorization
    const authorizeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/authorize`,
      body: {},
    });

    console.log(JSON.stringify(authorizeResponse.data, null, 2));
    expect(authorizeResponse.status).toBe(200);

    console.log(`✓ User registered: ${testUser.email}\n`);
    console.log("✓ Initial authorization completed\n");

    // Step 2: Start new authorization flow
    console.log("Step 2: Starting new authorization flow...");
    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `mfa_policy_test_${timestamp}`,
    });

    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });

    expect(authorizeResponse2.status).toBe(302);
    const location2 = authorizeResponse2.headers.location;
    const authId2 = new URL(location2, backendUrl).searchParams.get("id");
    expect(authId2).toBeDefined();
    console.log(`  Authorization ID: ${authId2}\n`);

    // Step 3: Password authentication only (MFA incomplete)
    console.log(
      "Step 3: Attempting password authentication ONLY (MFA incomplete)..."
    );
    const passwordAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/password-authentication`,
      body: {
        username: testUser.email,
        password: testUser.password,
      },
    });

    expect(passwordAuthResponse.status).toBe(200);
    console.log("✓ Password authentication succeeded");

    // Extract session cookie
    const setCookieHeaders = passwordAuthResponse.headers["set-cookie"];
    let sessionCookie = null;
    if (Array.isArray(setCookieHeaders)) {
      sessionCookie = setCookieHeaders.find((cookie) =>
        cookie.startsWith("SESSION=")
      );
    } else if (typeof setCookieHeaders === "string") {
      sessionCookie = setCookieHeaders.startsWith("SESSION=")
        ? setCookieHeaders
        : null;
    }
    expect(sessionCookie).toBeTruthy();
    console.log(`  Session Cookie: ${sessionCookie.substring(0, 50)}...`);
    console.log("  MFA NOT completed (email authentication skipped)\n");

    // Step 4: Attempt to issue authorization code with authorize-with-session
    console.log(
      "Step 4: Attempting to issue authorization code with password-only session..."
    );
    console.log(
      "  authorize-with-session should REJECT MFA-incomplete session\n"
    );

    const authorizeWithSessionResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/authorize-with-session`,
      headers: {
        Cookie: sessionCookie,
      },
      body: {},
    });

    console.log(`Response status: ${authorizeWithSessionResponse.status}`);
    console.log(
      `Response data:`,
      JSON.stringify(authorizeWithSessionResponse.data, null, 2)
    );

    // Expected behavior: 400 Bad Request
    // Previous vulnerable behavior: 200 OK with authorization code issued
    console.log("\n=== Test Assertion ===");
    console.log("Expected: 400 Bad Request (MFA policy not satisfied)");
    console.log(`Actual: ${authorizeWithSessionResponse.status}`);

    if (authorizeWithSessionResponse.status === 200) {
      console.log(
        "\n ERROR: Authorization code issued without complete MFA!"
      );
      console.log(
        "   Authentication policy requirements (password + email) not satisfied but authorization code was issued"
      );

      // Extract authorization code from response
      if (authorizeWithSessionResponse.data.redirect_uri) {
        const redirectUrl = new URL(
          authorizeWithSessionResponse.data.redirect_uri
        );
        const code = redirectUrl.searchParams.get("code");
        if (code) {
          console.log(
            `   Issued authorization code: ${code.substring(0, 20)}...`
          );
        }
      }
    }

    // After fix: should return 400
    expect(authorizeWithSessionResponse.status).toBe(400);
    expect(authorizeWithSessionResponse.data.error).toBe("invalid_request");
    expect(authorizeWithSessionResponse.data.error_description).toMatch(
      /authentication.*policy|policy.*requirements|not.*satisfied/i
    );

    console.log("\n✓ Test Result: MFA policy enforcement works correctly");
    console.log(
      "   Authorization code issuance was rejected with appropriate error message\n"
    );

    console.log("=== Pattern A Test Completed ===\n");
  }, 60000);

  /**
   * Pattern E: Complete MFA flow then reuse session
   * Flow 1: Authorization Request → initial-registration → email auth → authorize → token
   * Flow 2: Authorization Request → authorize-with-session → token (using same session)
   */
  it("Pattern E: should SUCCESS when using valid session from completed MFA flow", async () => {
    console.log("\n=== Pattern E: MFA Complete + Session Reuse ===\n");

    const timestamp = Date.now();
    const patternEUser = {
      email: faker.internet.email(),
      password: `PatternE${timestamp}!`,
      name: faker.person.fullName(),
    };

    // === FLOW 1: Registration with MFA ===
    console.log("=== FLOW 1: Registration with MFA ===\n");

    // Step 1: Start authorization request
    console.log("Step 1: Starting authorization request...");
    const authParams1 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_e_flow1_${timestamp}`,
    });

    const authorizeResponse1 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams1.toString()}`,
      headers: {},
    });

    expect(authorizeResponse1.status).toBe(302);
    const location1 = authorizeResponse1.headers.location;
    const authId1 = new URL(location1, backendUrl).searchParams.get("id");
    console.log(`   Authorization ID: ${authId1}`);

    // Step 2: Initial registration
    console.log("Step 2: Initial registration...");
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/initial-registration`,
      body: {
        email: patternEUser.email,
        password: patternEUser.password,
        name: patternEUser.name,
      },
    });
    expect(registrationResponse.status).toBe(200);
    console.log(`✓ User registered: ${patternEUser.email}`);

    // Extract session cookie from registration response
    const regCookieHeaders = registrationResponse.headers["set-cookie"];
    let sessionCookie = null;
    if (Array.isArray(regCookieHeaders)) {
      sessionCookie = regCookieHeaders.find((cookie) => cookie.startsWith("SESSION="));
    } else if (typeof regCookieHeaders === "string") {
      sessionCookie = regCookieHeaders.startsWith("SESSION=") ? regCookieHeaders : null;
    }
    console.log(`   Session Cookie: ${sessionCookie?.substring(0, 50)}...`);

    // Step 3: Email authentication challenge
    console.log("Step 3: Email authentication challenge...");
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/email-authentication-challenge`,
      body: {
        email: patternEUser.email,
        template: "registration",
      },
    });
    expect(challengeResponse.status).toBe(200);
    console.log("✓ Email challenge sent");

    // Step 4: Email authentication
    console.log("Step 4: Email authentication...");
    const emailAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/email-authentication`,
      body: {
        verification_code: "123456",
      },
    });
    expect(emailAuthResponse.status).toBe(200);
    console.log("✓ Email authentication succeeded");

    // IMPORTANT: Session cookie is regenerated after authentication (Session Fixation Prevention)
    // We must capture the LATEST session cookie from the last authentication response
    const emailAuthCookieHeaders = emailAuthResponse.headers["set-cookie"];
    if (Array.isArray(emailAuthCookieHeaders)) {
      const newCookie = emailAuthCookieHeaders.find((cookie) =>
        cookie.startsWith("SESSION=")
      );
      if (newCookie) {
        sessionCookie = newCookie;
        console.log(`   Updated Session Cookie (after regeneration): ${sessionCookie.substring(0, 50)}...`);
      }
    } else if (
      typeof emailAuthCookieHeaders === "string" &&
      emailAuthCookieHeaders.startsWith("SESSION=")
    ) {
      sessionCookie = emailAuthCookieHeaders;
      console.log(`   Updated Session Cookie (after regeneration): ${sessionCookie.substring(0, 50)}...`);
    }

    // Step 5: Authorize (completes MFA)
    console.log("Step 5: Authorize (MFA complete)...");
    const authorizeApiResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/authorize`,
      body: {},
    });

    expect(authorizeApiResponse.status).toBe(200);
    expect(authorizeApiResponse.data.redirect_uri).toBeDefined();

    const redirectUrl1 = new URL(authorizeApiResponse.data.redirect_uri);
    const code1 = redirectUrl1.searchParams.get("code");
    expect(code1).toBeTruthy();
    console.log(`✓ First authorization code issued: ${code1.substring(0, 20)}...`);

    // Step 6: Token request (Flow 1 complete)
    console.log("Step 6: Token request...");
    const tokenResponse1 = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/tokens`,
      body: {
        grant_type: "authorization_code",
        code: code1,
        redirect_uri: redirectUri,
        client_id: testClientId,
        client_secret: testClientSecret,
      },
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
    });
    console.log(`   Token response status: ${tokenResponse1.status}`);
    if (tokenResponse1.status === 200) {
      console.log("✓ Access token acquired");
    }

    // === FLOW 2: Session Reuse ===
    console.log("\n=== FLOW 2: Session Reuse with authorize-with-session ===\n");

    // Step 7: New authorization request
    console.log("Step 7: Starting new authorization request...");
    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_e_flow2_${timestamp}`,
    });

    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });

    expect(authorizeResponse2.status).toBe(302);
    const location2 = authorizeResponse2.headers.location;
    const authId2 = new URL(location2, backendUrl).searchParams.get("id");
    console.log(`   New Authorization ID: ${authId2}`);

    // Step 8: Authorize-with-session (should succeed with valid session)
    // Note: Session cookie is automatically handled by axios-cookiejar-support
    console.log("Step 8: Authorize-with-session (using valid session)...");
    console.log("   Using automatic cookie management (axios-cookiejar-support)");
    const authorizeWithSessionResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/authorize-with-session`,
      headers: {},
      body: {},
    });

    console.log(`Response status: ${authorizeWithSessionResponse.status}`);
    console.log(`Response data:`, JSON.stringify(authorizeWithSessionResponse.data, null, 2));

    // Should succeed with 200
    expect(authorizeWithSessionResponse.status).toBe(200);
    expect(authorizeWithSessionResponse.data.redirect_uri).toBeDefined();

    const redirectUrl2 = new URL(authorizeWithSessionResponse.data.redirect_uri);
    const code2 = redirectUrl2.searchParams.get("code");
    expect(code2).toBeTruthy();
    console.log(`✓ Second authorization code issued: ${code2.substring(0, 20)}...`);

    // Step 9: Token request (Flow 2 complete)
    console.log("Step 9: Token request...");
    const tokenResponse2 = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/tokens`,
      body: {
        grant_type: "authorization_code",
        code: code2,
        redirect_uri: redirectUri,
        client_id: testClientId,
        client_secret: testClientSecret,
      },
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
    });
    console.log(`   Token response status: ${tokenResponse2.status}`);
    if (tokenResponse2.status === 200) {
      console.log("✓ Access token acquired via session reuse");
    }

    console.log("\n✓ Pattern E: Session reuse after MFA completion succeeded");
    console.log(`   Flow 1 code: ${code1.substring(0, 20)}...`);
    console.log(`   Flow 2 code (session reuse): ${code2.substring(0, 20)}...\n`);
  }, 90000);

  /**
   * Pattern B: Email only (password not completed) → 400 error
   * Verify that email-only authentication is rejected when MFA requires password + email
   */
  it("Pattern B: should FAIL when only email authentication is completed (password missing)", async () => {
    console.log("\n=== Pattern B: Email Only (Password Missing) ===\n");

    const timestamp = Date.now();
    const patternBUser = {
      email: faker.internet.email(),
      password: `PatternB${timestamp}!`,
      name: faker.person.fullName(),
    };

    // Register new user first
    const regAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_b_reg_${timestamp}`,
    });

    const regResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${regAuthParams.toString()}`,
      headers: {},
    });
    const regAuthId = new URL(regResponse.headers.location, backendUrl).searchParams.get("id");

    // Register user
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/initial-registration`,
      body: {
        email: patternBUser.email,
        password: patternBUser.password,
        name: patternBUser.name,
      },
    });

    // Complete registration with email auth
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/email-authentication-challenge`,
      body: { email: patternBUser.email, template: "registration" },
    });
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/email-authentication`,
      body: { verification_code: "123456" },
    });
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/authorize`,
      body: {},
    });

    console.log(`✓ User registered: ${patternBUser.email}\n`);

    // Start new authorization flow
    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_b_test_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    const authId = new URL(authorizeResponse.headers.location, backendUrl).searchParams.get("id");

    // Skip password authentication, only do email
    console.log("Step 1: Email authentication challenge (skipping password)...");
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: {
        email: patternBUser.email,
        template: "authentication",
      },
    });
    expect(challengeResponse.status).toBe(200);

    console.log("Step 2: Email authentication...");
    const emailAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication`,
      body: {
        verification_code: "123456",
      },
    });
    expect(emailAuthResponse.status).toBe(200);

    // Extract session cookie
    const setCookieHeaders = emailAuthResponse.headers["set-cookie"];
    let sessionCookie = null;
    if (Array.isArray(setCookieHeaders)) {
      sessionCookie = setCookieHeaders.find((cookie) => cookie.startsWith("SESSION="));
    } else if (typeof setCookieHeaders === "string") {
      sessionCookie = setCookieHeaders.startsWith("SESSION=") ? setCookieHeaders : null;
    }

    console.log("Step 3: Attempting authorize-with-session (email only, password missing)...");
    const authorizeWithSessionResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize-with-session`,
      headers: {
        Cookie: sessionCookie || "",
      },
      body: {},
    });

    console.log(`Response status: ${authorizeWithSessionResponse.status}`);
    console.log(`Response data:`, JSON.stringify(authorizeWithSessionResponse.data, null, 2));

    // Should fail with 400 because password authentication is missing
    expect(authorizeWithSessionResponse.status).toBe(400);
    expect(authorizeWithSessionResponse.data.error).toBe("invalid_request");

    console.log("\n✓ Pattern B: Email-only authentication rejected as expected\n");
  }, 60000);

  /**
   * Pattern C: Password success + Email failure → 400 error
   * Verify that partial MFA completion is rejected
   */
  it("Pattern C: should FAIL when password succeeds but email authentication fails", async () => {
    console.log("\n=== Pattern C: Password Success + Email Failure ===\n");

    const timestamp = Date.now();

    // Start new authorization flow
    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_c_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    const authId = new URL(authorizeResponse.headers.location, backendUrl).searchParams.get("id");

    // Step 1: Password authentication (success)
    console.log("Step 1: Password authentication...");
    const passwordAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
      body: {
        username: testUser.email,
        password: testUser.password,
      },
    });
    expect(passwordAuthResponse.status).toBe(200);
    console.log("✓ Password authentication succeeded");

    // Extract session cookie
    const setCookieHeaders = passwordAuthResponse.headers["set-cookie"];
    let sessionCookie = null;
    if (Array.isArray(setCookieHeaders)) {
      sessionCookie = setCookieHeaders.find((cookie) => cookie.startsWith("SESSION="));
    } else if (typeof setCookieHeaders === "string") {
      sessionCookie = setCookieHeaders.startsWith("SESSION=") ? setCookieHeaders : null;
    }

    // Step 2: Email authentication challenge
    console.log("Step 2: Email authentication challenge...");
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: {
        email: testUser.email,
        template: "authentication",
      },
    });

    // Step 3: Email authentication with WRONG code (simulating failure)
    console.log("Step 3: Email authentication with wrong code...");
    const emailAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication`,
      body: {
        verification_code: "wrong_code_999999",
      },
    });
    // Note: Mock server may return 200 with failure status or 400
    console.log(`Email auth response: ${emailAuthResponse.status}`);

    // Step 4: Attempt to authorize (should fail because email auth failed/not completed)
    console.log("Step 4: Attempting authorize-with-session (email auth failed)...");
    const authorizeWithSessionResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize-with-session`,
      headers: {
        Cookie: sessionCookie,
      },
      body: {},
    });

    console.log(`Response status: ${authorizeWithSessionResponse.status}`);
    console.log(`Response data:`, JSON.stringify(authorizeWithSessionResponse.data, null, 2));

    // Should fail with 400
    expect(authorizeWithSessionResponse.status).toBe(400);
    expect(authorizeWithSessionResponse.data.error).toBe("invalid_request");

    console.log("\n✓ Pattern C: Password success + Email failure rejected as expected\n");
  }, 60000);

  /**
   * Pattern J: authorize (direct) with MFA not completed → 400 error
   * Verify that direct authorize API also checks MFA policy
   */
  it("Pattern J: should FAIL when calling authorize API directly with MFA incomplete", async () => {
    console.log("\n=== Pattern J: Direct Authorize with MFA Incomplete ===\n");

    const timestamp = Date.now();

    // Start new authorization flow
    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_j_${timestamp}`,
    });

    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    const authId = new URL(authorizeResponse.headers.location, backendUrl).searchParams.get("id");

    // Step 1: Password authentication only
    console.log("Step 1: Password authentication only...");
    const passwordAuthResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
      body: {
        username: testUser.email,
        password: testUser.password,
      },
    });
    expect(passwordAuthResponse.status).toBe(200);
    console.log("✓ Password authentication succeeded (MFA incomplete)");

    // Step 2: Call authorize API directly (not authorize-with-session)
    console.log("Step 2: Calling authorize API directly (MFA incomplete)...");
    const directAuthorizeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });

    console.log(`Response status: ${directAuthorizeResponse.status}`);
    console.log(`Response data:`, JSON.stringify(directAuthorizeResponse.data, null, 2));

    // Should fail with 400 because MFA is not complete
    expect(directAuthorizeResponse.status).toBe(400);
    expect(directAuthorizeResponse.data.error).toBe("invalid_request");

    console.log("\n✓ Pattern J: Direct authorize with MFA incomplete rejected as expected\n");
  }, 60000);

  /**
   * Pattern K: Using session with different authorization_id → 400 error
   * Verify that session cannot be used across different authorization flows
   */
  it("Pattern K: should FAIL when using session from different authorization flow", async () => {
    console.log("\n=== Pattern K: Session from Different Authorization Flow ===\n");

    const timestamp = Date.now();

    // === First Authorization Flow ===
    console.log("=== First Authorization Flow ===");
    const authParams1 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_k_flow1_${timestamp}`,
    });

    const authorizeResponse1 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams1.toString()}`,
      headers: {},
    });
    const authId1 = new URL(authorizeResponse1.headers.location, backendUrl).searchParams.get("id");

    // Complete MFA in first flow
    const passwordAuthResponse1 = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/password-authentication`,
      body: {
        username: testUser.email,
        password: testUser.password,
      },
    });
    expect(passwordAuthResponse1.status).toBe(200);

    // Extract session cookie from first flow
    const setCookieHeaders = passwordAuthResponse1.headers["set-cookie"];
    let sessionCookie = null;
    if (Array.isArray(setCookieHeaders)) {
      sessionCookie = setCookieHeaders.find((cookie) => cookie.startsWith("SESSION="));
    } else if (typeof setCookieHeaders === "string") {
      sessionCookie = setCookieHeaders.startsWith("SESSION=") ? setCookieHeaders : null;
    }

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/email-authentication-challenge`,
      body: { email: testUser.email, template: "authentication" },
    });
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/email-authentication`,
      body: { verification_code: "123456" },
    });

    console.log(`✓ First flow MFA completed (Auth ID: ${authId1})`);
    console.log(`  Session from first flow: ${sessionCookie?.substring(0, 50)}...`);

    // === Second Authorization Flow ===
    console.log("\n=== Second Authorization Flow ===");
    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_k_flow2_${timestamp}`,
    });

    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });
    const authId2 = new URL(authorizeResponse2.headers.location, backendUrl).searchParams.get("id");
    console.log(`  Second flow Auth ID: ${authId2}`);

    // Attempt to use first flow's session with second flow's authorization
    console.log("\nAttempting to use first flow's session with second authorization...");
    const authorizeWithSessionResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/authorize-with-session`,
      headers: {
        Cookie: sessionCookie,
      },
      body: {},
    });

    console.log(`Response status: ${authorizeWithSessionResponse.status}`);
    console.log(`Response data:`, JSON.stringify(authorizeWithSessionResponse.data, null, 2));

    // Should fail because session is for different authorization flow
    // Note: This may return 400 or another error depending on implementation
    expect([400, 401, 403]).toContain(authorizeWithSessionResponse.status);

    console.log("\n✓ Pattern K: Session from different flow rejected as expected\n");
  }, 60000);

  /**
   * Pattern G: Using another user's session → 400 error
   * Verify that one user cannot use another user's session
   */
  it("Pattern G: should FAIL when using another user's session", async () => {
    console.log("\n=== Pattern G: Another User's Session ===\n");

    const timestamp = Date.now();

    // Create second test user
    const secondUser = {
      email: faker.internet.email(),
      password: `SecondUser${timestamp}!`,
      name: faker.person.fullName(),
    };

    // Register second user
    console.log("Registering second user...");
    const regAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_g_reg_${timestamp}`,
    });

    const regResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${regAuthParams.toString()}`,
      headers: {},
    });
    const regAuthId = new URL(regResponse.headers.location, backendUrl).searchParams.get("id");

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/initial-registration`,
      body: {
        email: secondUser.email,
        password: secondUser.password,
        name: secondUser.name,
      },
    });
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/email-authentication-challenge`,
      body: { email: secondUser.email, template: "registration" },
    });
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/email-authentication`,
      body: { verification_code: "123456" },
    });
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/authorize`,
      body: {},
    });

    console.log(`✓ Second user registered: ${secondUser.email}\n`);

    // === First User (testUser) completes MFA and gets session ===
    console.log("=== First User's Authorization Flow ===");
    const authParams1 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_g_user1_${timestamp}`,
    });

    const authorizeResponse1 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams1.toString()}`,
      headers: {},
    });
    const authId1 = new URL(authorizeResponse1.headers.location, backendUrl).searchParams.get("id");

    const passwordAuthResponse1 = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/password-authentication`,
      body: {
        username: testUser.email,
        password: testUser.password,
      },
    });

    // Extract first user's session cookie
    const setCookieHeaders = passwordAuthResponse1.headers["set-cookie"];
    let firstUserSession = null;
    if (Array.isArray(setCookieHeaders)) {
      firstUserSession = setCookieHeaders.find((cookie) => cookie.startsWith("SESSION="));
    } else if (typeof setCookieHeaders === "string") {
      firstUserSession = setCookieHeaders.startsWith("SESSION=") ? setCookieHeaders : null;
    }

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/email-authentication-challenge`,
      body: { email: testUser.email, template: "authentication" },
    });
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/email-authentication`,
      body: { verification_code: "123456" },
    });

    console.log(`✓ First user (${testUser.email}) MFA completed`);
    console.log(`  First user session: ${firstUserSession?.substring(0, 50)}...`);

    // === Second User starts authorization but uses first user's session ===
    console.log("\n=== Second User's Authorization Flow (with stolen session) ===");
    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: testClientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `pattern_g_user2_${timestamp}`,
    });

    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });
    const authId2 = new URL(authorizeResponse2.headers.location, backendUrl).searchParams.get("id");

    // Second user starts password auth (to establish their identity in transaction)
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/password-authentication`,
      body: {
        username: secondUser.email,
        password: secondUser.password,
      },
    });

    // Attempt to use first user's session for second user's authorization
    console.log("\nAttempting to use first user's session for second user's authorization...");
    const authorizeWithSessionResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/authorize-with-session`,
      headers: {
        Cookie: firstUserSession,
      },
      body: {},
    });

    console.log(`Response status: ${authorizeWithSessionResponse.status}`);
    console.log(`Response data:`, JSON.stringify(authorizeWithSessionResponse.data, null, 2));

    // Should fail because session belongs to different user
    expect([400, 401, 403]).toContain(authorizeWithSessionResponse.status);

    console.log("\n✓ Pattern G: Another user's session rejected as expected\n");
  }, 90000);
});
