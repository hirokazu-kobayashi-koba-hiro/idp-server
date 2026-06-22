import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
} from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
} from "../../../lib/util";

/**
 * Issue #1394: HttpRequestSmsSender (SMS delivery delegated to an external API)
 *
 * Pattern #2 of the SMS authentication matrix:
 *   function    = sms_authentication_challenge  (OTP generated/verified INSIDE idp-server)
 *   sender_type = http_request                  (SMS delivery delegated to an external API)
 *
 * Because the OTP stays internal, it is NOT returned in the challenge response
 * (SmsChallengeAuthenticationExecutor returns success(Map.of())). The test reads the
 * internally-generated OTP from the stored interaction via the Management API
 * (authentication-transactions -> authentication-interactions -> payload.verification_code),
 * which is the same mechanism scenario-01 uses for the internal email OTP.
 *
 * Verifies that HttpRequestSmsSender (new) delegates delivery to the mock SMS API and that
 * the full Password (1st) + SMS OTP (2nd) MFA login completes with amr = [password, sms].
 */
describe("MFA Use Case: SMS OTP via HttpRequestSmsSender (Issue #1394)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  let testEmail;
  let testPassword;
  const testPhone = "+819012345678";
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
    const adminEmail = `admin-${timestamp}@mfa-sms-http.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `MFA SMS HTTP Test Org ${timestamp}`,
          description: "E2E test for HttpRequestSmsSender (#1394)",
        },
        tenant: {
          id: tenantId,
          name: `MFA SMS HTTP Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
          },
          session_config: {
            cookie_name: `MFA_SMS_HTTP_${organizationId.substring(0, 8)}`,
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
          client_name: "MFA SMS HTTP Test Client",
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
                  phone_number: { type: "string", maxLength: 255 },
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

    // Register SMS authentication config (Pattern #2: internal OTP + http_request sender)
    // function "sms_authentication_challenge" generates/verifies the OTP internally;
    // sender_type "http_request" delegates ONLY the SMS delivery to the mock external API
    // through the new HttpRequestSmsSender.
    const smsAuthResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "sms",
        attributes: {},
        metadata: {
          type: "internal",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
        },
        interactions: {
          "sms-authentication-challenge": {
            request: {
              schema: {
                type: "object",
                properties: {
                  phone_number: { type: "string" },
                  template: { type: "string" },
                },
              },
            },
            pre_hook: {},
            execution: {
              function: "sms_authentication_challenge",
              details: {
                sender_type: "http_request",
                settings: {
                  http_request: {
                    url: `${mockApiBaseUrl}/sms-authentication-challenge`,
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
                    body_mapping_rules: [
                      { from: "$.request_body.to", to: "phone_number" },
                      { from: "$.request_body.body", to: "message" },
                    ],
                  },
                },
                templates: {
                  registration: {
                    subject: "[ID Verification] Your signup sms confirmation code",
                    body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\n– IDP Support",
                  },
                  authentication: {
                    subject: "[ID Verification] Your login sms confirmation code",
                    body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\n– IDP Support",
                  },
                },
                retry_count_limitation: 5,
                expire_seconds: 300,
              },
            },
            post_hook: {},
            response: {
              body_mapping_rules: [{ from: "$.response_body", to: "*" }],
            },
          },
          "sms-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  verification_code: { type: "string" },
                },
              },
            },
            execution: {
              function: "sms_authentication",
              details: {
                retry_count_limitation: 5,
                expire_seconds: 300,
              },
            },
          },
        },
      },
    });
    expect(smsAuthResp.status).toBe(201);

    // Create MFA authentication policy: Password (1st) + SMS (2nd)
    const authPolicyResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "mfa_password_sms_http_request",
            priority: 1,
            conditions: {},
            available_methods: ["password", "sms", "initial-registration"],
            step_definitions: [
              {
                method: "password",
                order: 1,
                requires_user: false,
                user_identity_source: "username",
              },
              {
                method: "sms",
                order: 2,
                requires_user: true,
              },
            ],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: "$.sms-authentication.success_count",
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
    testEmail = `sms-http-user-${timestamp}@mfa-sms-http.example.com`;
    testPassword = "SmsHttpTestPass_1!";

    const regAuthResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `reg-${timestamp}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(regAuthResp.status).toBe(302);
    const { params: regParams } = convertNextAction(regAuthResp.headers.location);
    const regAuthId = regParams.get("id");

    const regResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/initial-registration`,
      body: {
        email: testEmail,
        password: testPassword,
        name: "MFA SMS HTTP Test User",
        phone_number: testPhone,
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

  it("delegates SMS delivery via HttpRequestSmsSender and completes Password + SMS OTP MFA (amr = password, sms)", async () => {
    // Step 1: Start new authorization flow
    const state = `mfa-sms-http-${Date.now()}`;
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

    // Step 2: Password Authentication (1st factor)
    const passwordResp = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: {
        username: testEmail,
        password: testPassword,
      },
    });
    expect(passwordResp.status).toBe(200);

    // Step 3: SMS OTP Challenge (2nd factor)
    // HttpRequestSmsSender posts {to, body} to the mock SMS API; a 2xx => challenge success.
    const challengeResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/sms-authentication-challenge`,
      body: {
        phone_number: testPhone,
        template: "authentication",
      },
    });
    expect(challengeResp.status).toBe(200);

    // Step 4: Retrieve the internally-generated OTP via the Management API.
    // The OTP is NOT in the challenge response (HttpRequestSmsSender only delegates delivery);
    // it is persisted on the stored sms-authentication-challenge interaction.
    const txnListResp = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-transactions?authorization_id=${authId}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(txnListResp.status).toBe(200);
    const transactionId = txnListResp.data.list[0].id;

    const interactionResp = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(interactionResp.status).toBe(200);
    const verificationCode = interactionResp.data.payload.verification_code;
    expect(verificationCode).toBeDefined();

    // Step 5: SMS OTP Verification (internal)
    const smsAuthResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/sms-authentication`,
      body: {
        verification_code: verificationCode,
      },
    });
    expect(smsAuthResp.status).toBe(200);

    // Step 6: Authorize (consent grant)
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

    // Step 7: Token Exchange
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

    // Step 8: Verify amr in ID Token contains both password and sms
    const jwksResp = await get({
      url: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    expect(jwksResp.status).toBe(200);

    const { payload } = verifyAndDecodeJwt({
      jwt: tokenResp.data.id_token,
      jwks: jwksResp.data,
    });
    expect(payload.sub).toBeDefined();
    expect(payload.iss).toBe(`${backendUrl}/${tenantId}`);
    expect(payload.amr).toBeDefined();
    expect(payload.amr).toContain("sms");
    expect(payload.amr).toContain("password");
  });
});
