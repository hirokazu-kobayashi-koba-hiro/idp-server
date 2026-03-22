import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
  getUserinfo,
} from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";

/**
 * MFA Use Case: Email OTP + Password MFA Flow
 *
 * verify.sh (mfa-email) の Phase 1-2 を E2E Jest に移植。
 *
 * カバー範囲 (GAP-ANALYSIS A-04):
 * - Phase 1: ユーザー登録 (initial-registration → authorize → token)
 * - Phase 2: MFA ログイン (Email OTP challenge → verification → Password → token)
 * - amr (Authentication Methods References) に email + password が含まれること
 * - UserInfo / Refresh Token 動作確認
 */
describe("MFA Use Case: Email OTP + Password MFA Flow", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  let testEmail;
  let testPassword;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

  beforeAll(async () => {
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

    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    const adminUserId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@mfa-email.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    // Create organization + tenant
    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: {
          id: organizationId,
          name: `MFA Email Test Org ${timestamp}`,
          description: "E2E test for Email OTP MFA flow",
        },
        tenant: {
          id: tenantId,
          name: `MFA Email Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
          },
          session_config: {
            cookie_name: `MFA_EMAIL_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: {
            allow_origins: [backendUrl],
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: [
            "authorization_code",
            "refresh_token",
            "password",
          ],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: [
            "openid",
            "profile",
            "email",
            "management",
            "org-management",
          ],
          claims_supported: [
            "sub",
            "iss",
            "auth_time",
            "acr",
            "name",
            "email",
            "email_verified",
          ],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: {
            access_token_type: "JWT",
          },
        },
        user: {
          sub: adminUserId,
          provider_id: "idp-server",
          name: "Admin User",
          email: adminEmail,
          email_verified: true,
          raw_password: adminPassword,
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management org-management",
          client_name: "MFA Email Test Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardingResponse.status !== 201) {
      console.error(
        "Onboarding failed:",
        JSON.stringify(onboardingResponse.data, null, 2)
      );
    }
    expect(onboardingResponse.status).toBe(201);

    // Get management token
    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management org-management",
      clientId,
      clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    // Register initial-registration config
    const initialRegResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "initial-registration",
        attributes: {},
        metadata: {},
        interactions: {
          "initial-registration": {
            request: {
              schema: {
                type: "object",
                required: ["email", "password", "name"],
                properties: {
                  name: { type: "string", maxLength: 255 },
                  email: { type: "string", format: "email", maxLength: 255 },
                  password: { type: "string", maxLength: 64, minLength: 8 },
                },
              },
            },
          },
        },
      },
    });
    expect(initialRegResp.status).toBe(201);

    // Register password authentication config
    const passwordAuthResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { type: "password" },
        interactions: {
          "password-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  username: { type: "string" },
                  password: { type: "string" },
                },
                required: ["username", "password"],
              },
            },
            pre_hook: {},
            execution: { function: "password_verification" },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.user_id", to: "user_id" },
                { from: "$.username", to: "username" },
                { from: "$.error", to: "error" },
                { from: "$.error_description", to: "error_description" },
              ],
            },
          },
        },
      },
    });
    expect(passwordAuthResp.status).toBe(201);

    // Register email authentication config (http_request to mock server)
    const emailAuthResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "email",
        attributes: {},
        metadata: {
          type: "external",
          description: "Email authentication via mock",
          sender: "test@example.com",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
          templates: {
            registration: {
              subject: "Verification Code",
              body: "Code: {VERIFICATION_CODE}",
            },
            authentication: {
              subject: "Verification Code",
              body: "Code: {VERIFICATION_CODE}",
            },
          },
          settings: {
            smtp: {
              host: "smtp.example.com",
              port: 587,
              username: "test",
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
                url: "http://host.docker.internal:4000/email-authentication-challenge",
                method: "POST",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: "http://host.docker.internal:4000/token",
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
              previous_interaction: {
                key: "email-authentication-challenge",
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
    expect(emailAuthResp.status).toBe(201);

    // Create MFA authentication policy: Email (1st) + Password (2nd)
    const authPolicyResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "mfa_email_password",
            priority: 1,
            conditions: {},
            available_methods: ["password", "email", "initial-registration"],
            step_definitions: [
              {
                method: "email",
                order: 1,
                requires_user: false,
                user_identity_source: "email",
              },
              {
                method: "password",
                order: 2,
                requires_user: true,
              },
            ],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.email-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                ],
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
    expect(authPolicyResp.status).toBe(201);

    // Phase 1: Register test user
    testEmail = `mfa-user-${timestamp}@mfa-email.example.com`;
    testPassword = "MfaTestPass_1!";

    const regAuthResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `reg-${timestamp}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(regAuthResp.status).toBe(302);
    const { params: regParams } = convertNextAction(
      regAuthResp.headers.location
    );
    const regAuthId = regParams.get("id");

    const regResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/initial-registration`,
      body: {
        email: testEmail,
        password: testPassword,
        name: "MFA Email Test User",
      },
    });
    expect(regResp.status).toBe(200);

    const regAuthorize = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: regAuthId,
      body: {},
    });
    const regResult = convertToAuthorizationResponse(
      regAuthorize.data.redirect_uri
    );
    await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: regResult.code,
      redirectUri,
      clientId,
      clientSecret,
    });
  });

  afterAll(async () => {
    if (mgmtAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      }).catch(() => {});
    }
    if (systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("should complete MFA login with Email OTP (1st factor) + Password (2nd factor) and verify amr", async () => {
    console.log("\n=== Phase 2: MFA Login (Email OTP + Password) ===");

    // Step 1: Start new authorization flow
    console.log("Step 1: Start authorization flow for MFA login");
    const state = `mfa-login-${Date.now()}`;
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state,
      scope: "openid profile email",
      redirectUri,
      prompt: "login",
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");
    expect(authId).toBeDefined();
    console.log(`Authorization ID: ${authId}`);

    // Step 2: Email OTP Challenge (1st factor)
    console.log("\nStep 2: Email OTP Challenge");
    const challengeResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: {
        email: testEmail,
        template: "authentication",
      },
    });
    expect(challengeResp.status).toBe(200);
    const verificationCode =
      challengeResp.data.verification_code || "123456";
    console.log(`Verification code: ${verificationCode}`);

    // Step 3: Email OTP Verification
    console.log("\nStep 3: Email OTP Verification");
    const emailAuthResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication`,
      body: {
        verification_code: verificationCode,
      },
    });
    expect(emailAuthResp.status).toBe(200);
    console.log("Email OTP verified (1st factor complete)");

    // Step 4: Password Authentication (2nd factor)
    console.log("\nStep 4: Password Authentication (2nd factor)");
    const passwordResp = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: {
        username: testEmail,
        password: testPassword,
      },
    });
    expect(passwordResp.status).toBe(200);
    console.log("Password authentication successful (2nd factor complete)");

    // Step 5: Authorize (consent grant)
    console.log("\nStep 5: Authorize");
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResp.status).toBe(200);
    const authResult = convertToAuthorizationResponse(
      authorizeResp.data.redirect_uri
    );
    expect(authResult.code).toBeDefined();
    expect(authResult.state).toBe(state);
    console.log("Authorization code obtained");

    // Step 6: Token Exchange
    console.log("\nStep 6: Token Exchange");
    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: authResult.code,
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(tokenResp.status).toBe(200);
    expect(tokenResp.data.access_token).toBeDefined();
    expect(tokenResp.data.id_token).toBeDefined();
    expect(tokenResp.data.refresh_token).toBeDefined();
    const accessToken = tokenResp.data.access_token;
    const idToken = tokenResp.data.id_token;
    const refreshToken = tokenResp.data.refresh_token;
    console.log("Token exchange successful");

    // Step 7: Verify amr in ID Token
    console.log("\nStep 7: Verify amr in ID Token");
    const jwksResp = await get({
      url: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    expect(jwksResp.status).toBe(200);

    const { payload } = verifyAndDecodeJwt({
      jwt: idToken,
      jwks: jwksResp.data,
    });
    expect(payload.sub).toBeDefined();
    expect(payload.iss).toBe(`${backendUrl}/${tenantId}`);

    // amr should contain both "email" and "password"
    expect(payload.amr).toBeDefined();
    expect(Array.isArray(payload.amr)).toBe(true);
    expect(payload.amr).toContain("email");
    expect(payload.amr).toContain("password");
    console.log(`amr: ${JSON.stringify(payload.amr)}`);

    // Step 8: UserInfo
    console.log("\nStep 8: UserInfo");
    const userinfoResp = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(accessToken),
    });
    expect(userinfoResp.status).toBe(200);
    expect(userinfoResp.data.sub).toBeDefined();
    expect(userinfoResp.data.email).toBe(testEmail);
    expect(userinfoResp.data.name).toBe("MFA Email Test User");
    console.log(
      `UserInfo: sub=${userinfoResp.data.sub}, email=${userinfoResp.data.email}`
    );

    // Step 9: Refresh Token
    console.log("\nStep 9: Refresh Token");
    const refreshResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken,
      clientId,
      clientSecret,
    });
    expect(refreshResp.status).toBe(200);
    expect(refreshResp.data.access_token).toBeDefined();
    expect(refreshResp.data.access_token).not.toBe(accessToken);
    console.log("Refresh token successful");

    console.log("\n=== MFA Email OTP Flow Test Completed ===");
  });
});
