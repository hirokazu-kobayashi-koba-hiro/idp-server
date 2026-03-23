import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
  getUserinfo,
} from "../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../lib/util";

/**
 * Security Test: External API Authentication 2nd Factor Bypass Prevention
 *
 * external-api-authentication を MFA の 2段階目として使用した場合に、
 * 外部APIのレスポンスで1段階目の認証済みユーザーが入れ替わらないことを検証する。
 *
 * 攻撃シナリオ:
 * 1. ユーザーA でパスワード認証（1段階目）
 * 2. 2段階目の external-api-authentication で、外部APIがユーザーBの情報を返す
 * 3. 結果: sub はユーザーA のまま（ユーザーBに入れ替わらない）
 *
 * Prerequisites:
 * - Mock server (Mockoon) running at host.docker.internal:4000
 */
describe("Security: External API Authentication 2nd Factor Bypass Prevention", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  let testUserASub;
  let testUserAEmail;
  let testUserAPassword;
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
    const adminEmail = `admin-${timestamp}@sec-ext-api.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: {
          id: organizationId,
          name: `Security ExtAPI 2FA Test Org ${timestamp}`,
          description:
            "Security test: external-api-authentication 2nd factor bypass",
        },
        tenant: {
          id: tenantId,
          name: `Security ExtAPI 2FA Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID",
          },
          session_config: {
            cookie_name: `SEC_EXT_${organizationId.substring(0, 8)}`,
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
          client_name: "Security ExtAPI 2FA Test Client",
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
        metadata: {},
        interactions: {
          "password-authentication": {
            request: {
              schema: {
                type: "object",
                required: ["username", "password"],
                properties: {
                  username: { type: "string" },
                  password: { type: "string" },
                },
              },
            },
            execution: { function: "password_verification" },
            response: { body_mapping_rules: [] },
          },
        },
      },
    });
    expect(passwordAuthResp.status).toBe(201);

    // Register external-api-authentication config
    // The mock /auth/password endpoint returns user_id based on the username sent,
    // so when we send a different username in the 2nd factor, the external API
    // returns a DIFFERENT user's info (simulating an attack).
    const extApiAuthResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "external-api-authentication",
        attributes: {},
        metadata: {
          description:
            "External API auth for 2nd factor bypass testing",
        },
        interactions: {
          verify_identity: {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/auth/password`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.request_body.username", to: "username" },
                  { from: "$.request_body.password", to: "password" },
                ],
              },
            },
            user_resolve: {
              user_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body.user_id",
                  to: "external_user_id",
                },
                {
                  from: "$.execution_http_request.response_body.email",
                  to: "email",
                },
                {
                  from: "$.execution_http_request.response_body.name",
                  to: "name",
                },
                { static_value: "mock-external-api", to: "provider_id" },
              ],
            },
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body.email",
                  to: "external_email",
                },
              ],
            },
          },
        },
      },
    });
    expect(extApiAuthResp.status).toBe(201);

    // Create MFA authentication policy: Password (1st) + External API (2nd)
    const authPolicyResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "mfa_password_external_api",
            priority: 1,
            conditions: {},
            available_methods: [
              "password",
              "external-api",
              "initial-registration",
            ],
            step_definitions: [
              {
                method: "password",
                order: 1,
                requires_user: false,
                user_identity_source: "username",
              },
              {
                method: "external-api",
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
                    path: "$.external-api-authentication.success_count",
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

    // Register test user A via initial-registration
    testUserAEmail = `user-a-${timestamp}@sec-ext-api.example.com`;
    testUserAPassword = "UserAPass_1!";

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
        email: testUserAEmail,
        password: testUserAPassword,
        name: "User A",
      },
    });
    expect(regResp.status).toBe(200);
    testUserASub = regResp.data.user.sub;

    const regAuthorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: regAuthId,
      body: {},
    });
    expect(regAuthorizeResp.status).toBe(200);
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

  it("should complete MFA flow: password (1st) + external-api (2nd) and verify amr", async () => {
    // Step 1: Start authorization
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `mfa-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
      prompt: "login",
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    // Step 2: Password authentication (1st factor) - user A
    const passwordResp = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username: testUserAEmail, password: testUserAPassword },
    });
    expect(passwordResp.status).toBe(200);

    // Step 3: External API authentication (2nd factor) - send user A's credentials
    const extApiResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/external-api-authentication`,
      body: {
        interaction: "verify_identity",
        username: testUserAEmail,
        password: "ExternalPass123!",
      },
    });
    expect(extApiResp.status).toBe(200);

    // Step 4: Authorize
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResp.status).toBe(200);
    const result = convertToAuthorizationResponse(
      authorizeResp.data.redirect_uri
    );

    // Step 5: Token exchange
    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: result.code,
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(tokenResp.status).toBe(200);

    // Verify ID Token amr contains both methods
    const jwksResp = await get({
      url: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    const { payload } = verifyAndDecodeJwt({
      jwt: tokenResp.data.id_token,
      jwks: jwksResp.data,
    });
    expect(payload.sub).toBe(testUserASub);
    expect(payload.amr).toContain("password");
    expect(payload.amr).toContain("external-api");

    // Verify UserInfo
    const userinfoResp = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenResp.data.access_token),
    });
    expect(userinfoResp.status).toBe(200);
    expect(userinfoResp.data.sub).toBe(testUserASub);
  });

  it("should NOT swap user when 2nd factor external API returns different user info (bypass attempt)", async () => {
    // Step 1: Start authorization
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `bypass-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
      prompt: "login",
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    // Step 2: Password authentication (1st factor) - authenticate as user A
    const passwordResp = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username: testUserAEmail, password: testUserAPassword },
    });
    expect(passwordResp.status).toBe(200);

    // Step 3: ATTACK - External API authentication (2nd factor)
    // Send DIFFERENT user's email (attacker@evil.com) to the external API.
    // The mock /auth/password returns user_id based on the username sent,
    // so the external API response contains attacker's info, not user A's.
    const extApiResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/external-api-authentication`,
      body: {
        interaction: "verify_identity",
        username: "attacker@evil.com",
        password: "ExternalPass123!",
      },
    });
    // SECURITY ASSERTION: 2nd factor must fail because external API returned
    // a different user (attacker@evil.com) than the authenticated user (user A)
    expect(extApiResp.status).toBe(400);
    expect(extApiResp.data.error).toBe("user_identity_mismatch");
  });

  it("should fail when 2nd factor is called without completing 1st factor (skip attack)", async () => {
    // Step 1: Start authorization
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `skip-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
      prompt: "login",
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    // ATTACK: Skip 1st factor (password), directly call 2nd factor
    const extApiResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/external-api-authentication`,
      body: {
        interaction: "verify_identity",
        username: "attacker@evil.com",
        password: "ExternalPass123!",
      },
    });
    // 2nd factor requires authenticated user but transaction has no user
    // → User.notFound() → user_not_found error
    expect(extApiResp.status).toBe(400);
    expect(extApiResp.data.error).toBe("user_not_found");
  });
});
