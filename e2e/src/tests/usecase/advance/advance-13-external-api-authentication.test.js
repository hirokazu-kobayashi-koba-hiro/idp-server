import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  authorize,
  getUserinfo,
} from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";

/**
 * Advance Use Case: External API Authentication (interaction-based routing)
 *
 * カバー範囲:
 * - external-api-authentication type の新規 Interactor
 * - リクエストボディの "interaction" フィールドで interaction 設定をルーティング
 * - interaction ごとに異なる外部APIを呼び出し
 * - user_resolve 付き interaction: ユーザー解決 + 同一ユーザー再認証で sub 一致
 * - user_resolve なし interaction: 外部APIの結果だけ返却
 * - 未定義 interaction → 400 エラー
 * - interaction 未指定 → 400 エラー
 * - 外部APIエラーのステータスコード透過
 *
 * Prerequisites:
 * - Mock server (Mockoon) running at host.docker.internal:4000
 *   with POST /auth/password and POST /e2e/error-responses endpoints
 */
describe("Advance Use Case: External API Authentication", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
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
    const adminEmail = `admin-${timestamp}@ext-api.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: {
          id: organizationId,
          name: `External API Auth Test Org ${timestamp}`,
          description: "E2E test for external API authentication interactor",
        },
        tenant: {
          id: tenantId,
          name: `External API Auth Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID",
          },
          session_config: {
            cookie_name: `EXT_API_${organizationId.substring(0, 8)}`,
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
          client_name: "External API Auth Test Client",
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

    // Register external-api-authentication config with multiple interactions
    const authConfigResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "external-api-authentication",
        attributes: {},
        metadata: {
          description:
            "External API authentication with interaction-based routing",
        },
        interactions: {
          // Operation 1: password_verify (with user_resolve)
          password_verify: {
            request: {
              schema: {
                type: "object",
                required: ["interaction", "username", "password"],
                properties: {
                  interaction: { type: "string" },
                  username: { type: "string", minLength: 1 },
                  password: { type: "string", minLength: 1 },
                },
              },
            },
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
                  from: "$.execution_http_request.response_body.user_id",
                  to: "user_id",
                },
                {
                  from: "$.execution_http_request.response_body.email",
                  to: "email",
                },
              ],
            },
          },
          // Interaction 2: challenge → verify (previous_interaction pattern)
          challenge: {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/e2e/error-responses`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.request_body", to: "*" },
                ],
              },
              http_request_store: {
                key: "challenge",
                interaction_mapping_rules: [
                  {
                    from: "$.response_body.application_id",
                    to: "transaction_id",
                  },
                ],
              },
            },
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body.application_id",
                  to: "transaction_id",
                },
              ],
            },
          },
          verify: {
            execution: {
              function: "http_request",
              previous_interaction: { key: "challenge" },
              http_request: {
                url: `${mockApiBaseUrl}/e2e/error-responses`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  {
                    from: "$.interaction.transaction_id",
                    to: "transaction_id",
                  },
                  { from: "$.request_body.code", to: "verification_code" },
                ],
              },
            },
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body",
                  to: "*",
                },
              ],
            },
          },
          // Interaction 3: risk_check (no user_resolve - returns API result only)
          risk_check: {
            request: {
              schema: {
                type: "object",
                required: ["interaction"],
                properties: {
                  interaction: { type: "string" },
                  status: { type: "string" },
                },
              },
            },
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/e2e/error-responses`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.request_body", to: "*" },
                ],
              },
            },
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body",
                  to: "*",
                },
              ],
            },
          },
        },
      },
    });
    if (authConfigResp.status !== 201) {
      console.error(
        "Auth config failed:",
        JSON.stringify(authConfigResp.data, null, 2)
      );
    }
    expect(authConfigResp.status).toBe(201);
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

  /** Helper: start auth flow and get authId */
  async function startAuthFlow() {
    const state = `ext-api-${Date.now()}`;
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    return params.get("id");
  }

  /** Helper: start auth flow with password_verify authentication, returns authId */
  async function startAuthenticatedFlow(
    email = "test@example.com",
    password = "ExternalPass123!"
  ) {
    const authId = await startAuthFlow();
    const loginResp = await callExternalApiAuth(authId, {
      interaction: "password_verify",
      username: email,
      password,
    });
    expect(loginResp.status).toBe(200);
    return authId;
  }

  /** Helper: call external-api-authentication with given body */
  async function callExternalApiAuth(authId, body) {
    return await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/external-api-authentication`,
      body,
    });
  }

  /** Helper: complete auth flow (authorize + token) */
  async function completeFlow(authId) {
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResp.status).toBe(200);
    const result = convertToAuthorizationResponse(
      authorizeResp.data.redirect_uri
    );

    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: result.code,
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(tokenResp.status).toBe(200);
    return tokenResp.data;
  }

  // === password_verify interaction (with user_resolve) ===

  it("should authenticate via password_verify interaction, issue tokens, and verify ID Token amr", async () => {
    const authId = await startAuthenticatedFlow();

    // Complete flow
    const tokenData = await completeFlow(authId);
    expect(tokenData.access_token).toBeDefined();
    expect(tokenData.id_token).toBeDefined();
    expect(tokenData.refresh_token).toBeDefined();
    expect(tokenData.token_type).toBe("Bearer");
    expect(tokenData.expires_in).toBeGreaterThan(0);

    // Verify ID Token
    const jwksResp = await get({
      url: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    expect(jwksResp.status).toBe(200);

    const { header, payload } = verifyAndDecodeJwt({
      jwt: tokenData.id_token,
      jwks: jwksResp.data,
    });
    expect(header.alg).toBe("ES256");
    expect(payload.iss).toBe(`${backendUrl}/${tenantId}`);
    expect(payload.sub).toBeDefined();
    expect(payload.exp).toBeGreaterThan(Math.floor(Date.now() / 1000));
    expect(payload.iat).toBeDefined();
    expect(payload.amr).toBeDefined();
    expect(payload.amr).toContain("external-api");

    // Verify UserInfo
    const userinfoResp = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenData.access_token),
    });
    expect(userinfoResp.status).toBe(200);
    expect(userinfoResp.data.sub).toBe(payload.sub);
    expect(userinfoResp.data.email).toBeDefined();

    // Verify Refresh Token
    const refreshResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken: tokenData.refresh_token,
      clientId,
      clientSecret,
    });
    expect(refreshResp.status).toBe(200);
    expect(refreshResp.data.access_token).toBeDefined();
  });

  it("should resolve same user on 2nd login via password_verify", async () => {
    const testEmail = "repeat-user@example.com";
    const testPassword = "ExternalPass123!";

    // 1st login
    const authId1 = await startAuthenticatedFlow(testEmail, testPassword);
    const tokenData1 = await completeFlow(authId1);

    const userinfo1 = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenData1.access_token),
    });
    const firstSub = userinfo1.data.sub;

    // 2nd login
    const authId2 = await startAuthenticatedFlow(testEmail, testPassword);
    const tokenData2 = await completeFlow(authId2);

    const userinfo2 = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenData2.access_token),
    });
    expect(userinfo2.data.sub).toBe(firstSub);
  });

  it("should reject invalid credentials via password_verify interaction", async () => {
    const authId = await startAuthFlow();

    const loginResp = await callExternalApiAuth(authId, {
      interaction: "password_verify",
      username: "test@example.com",
      password: "invalid",
    });
    // Mock server returns 401 for password="invalid"
    expect(loginResp.status).toBe(401);
  });

  // === risk_check interaction (no user_resolve) ===

  it("should return external API result for risk_check interaction (no user_resolve)", async () => {
    const authId = await startAuthenticatedFlow();

    // Mock /e2e/error-responses returns 200 with success body by default
    const resp = await callExternalApiAuth(authId, {
      interaction: "risk_check",
    });
    expect(resp.status).toBe(200);
    expect(resp.data.status).toBe("success");
  });

  // === previous_interaction (challenge → verify) ===

  it("should pass data from challenge to verify via previous_interaction", async () => {
    const authId = await startAuthenticatedFlow();

    // Step 1: Challenge - external API returns application_id, stored as transaction_id
    const challengeResp = await callExternalApiAuth(authId, {
      interaction: "challenge",
    });
    expect(challengeResp.status).toBe(200);
    expect(challengeResp.data.transaction_id).toBeDefined();
    const transactionId = challengeResp.data.transaction_id;

    // Step 2: Verify - previous_interaction loads stored transaction_id
    const verifyResp = await callExternalApiAuth(authId, {
      interaction: "verify",
      code: "123456",
    });
    expect(verifyResp.status).toBe(200);
    // Mock /e2e/error-responses receives { transaction_id: "...", verification_code: "123456" }
    // and returns 200 success
    expect(verifyResp.data.status).toBe("success");
  });

  it("should not crash when verify is called without prior challenge", async () => {
    const authId = await startAuthenticatedFlow();

    // Skip challenge, directly call verify which has previous_interaction: { key: "challenge" }
    // previous_interaction data doesn't exist → transaction_id is not resolved in body_mapping_rules
    // The external API receives the request without transaction_id
    // Whether this succeeds or fails depends on the external API's validation
    const verifyResp = await callExternalApiAuth(authId, {
      interaction: "verify",
      code: "123456",
    });
    // idp-server must not return 500 (internal server error)
    expect(verifyResp.status).not.toBe(500);
  });

  // === Error propagation ===

  it.each([
    { statusCode: 429, description: "Too Many Requests" },
    { statusCode: 500, description: "Internal Server Error" },
    { statusCode: 502, description: "Bad Gateway" },
    { statusCode: 503, description: "Service Unavailable" },
  ])(
    "should propagate $statusCode ($description) from external API",
    async ({ statusCode }) => {
      const authId = await startAuthenticatedFlow();

      const resp = await callExternalApiAuth(authId, {
        interaction: "risk_check",
        status: String(statusCode),
      });
      expect(resp.status).toBe(statusCode);
    }
  );

  // === Operation routing errors ===

  it("should return 400 when interaction is missing", async () => {
    const authId = await startAuthenticatedFlow();

    const resp = await callExternalApiAuth(authId, {
      username: "test@example.com",
      password: "test",
    });
    expect(resp.status).toBe(400);
    expect(resp.data.error).toBe("invalid_request");
  });

  it("should return 400 when interaction is not configured", async () => {
    const authId = await startAuthenticatedFlow();

    const resp = await callExternalApiAuth(authId, {
      interaction: "nonexistent_interaction",
    });
    expect(resp.status).toBe(400);
    expect(resp.data.error).toBe("invalid_request");
  });

  it("should return 400 when request fails JSON Schema validation", async () => {
    const authId = await startAuthenticatedFlow();

    // password_verify requires username and password, sending without them
    const resp = await callExternalApiAuth(authId, {
      interaction: "password_verify",
    });
    expect(resp.status).toBe(400);
    expect(resp.data.error).toBe("invalid_request");
    expect(resp.data.error_messages).toBeDefined();
  });
});
