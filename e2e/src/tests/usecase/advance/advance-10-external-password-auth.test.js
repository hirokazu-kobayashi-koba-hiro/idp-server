import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
  getUserinfo,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";

/**
 * Advance Use Case: External Password Authentication (http_request executor)
 *
 * verify.sh (external-password-auth) を E2E Jest に移植。
 *
 * カバー範囲 (GAP-ANALYSIS A-10, A-11):
 * - 外部サービス経由パスワード認証 (http_request executor + user_mapping_rules)
 * - 認証成功 → UserInfo に外部サービスからマッピングされた値が反映
 * - 認証失敗 (不正パスワード → 400)
 * - 2回目ログインで同一ユーザー解決 (sub一致)
 *
 * Prerequisites:
 * - Mock server (Mockoon) running at host.docker.internal:4000
 *   with POST /auth/password endpoint
 */
describe("Advance Use Case: External Password Authentication", () => {
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
    const adminEmail = `admin-${timestamp}@ext-auth.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `External Auth Test Org ${timestamp}`,
          description: "E2E test for external password authentication",
        },
        tenant: {
          id: tenantId,
          name: `External Auth Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID",
          },
          session_config: {
            cookie_name: `EXT_AUTH_${organizationId.substring(0, 8)}`,
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
          client_name: "External Auth Test Client",
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

    // Register external password authentication config (http_request to mock server)
    const authConfigResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: {
          description: "External password authentication via mock server",
        },
        interactions: {
          "password-authentication": {
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
                { static_value: "mock-external-auth", to: "provider_id" },
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

  /** Helper: start auth flow and attempt password login */
  async function startAuthAndLogin(email, password) {
    const state = `ext-auth-${Date.now()}`;
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
    const authId = params.get("id");

    const loginResp = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username: email, password },
    });

    return { authId, loginStatus: loginResp.status, loginData: loginResp.data, state };
  }

  /** Helper: complete auth flow and get tokens */
  async function completeFlow(authId) {
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResp.status).toBe(200);
    const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);

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

  it("should authenticate via external service, map user info, and verify UserInfo", async () => {
    console.log("\n=== External Password Authentication: Success Flow ===");

    // Step 1: Login via external service
    console.log("Step 1: Login via external mock service");
    const testEmail = "test@example.com";
    const testPassword = "ExternalPass123!";
    const { authId, loginStatus } = await startAuthAndLogin(testEmail, testPassword);
    expect(loginStatus).toBe(200);
    console.log("External authentication successful");

    // Step 2: Complete flow → Token
    console.log("\nStep 2: Complete authorization flow");
    const tokenData = await completeFlow(authId);
    expect(tokenData.access_token).toBeDefined();
    expect(tokenData.refresh_token).toBeDefined();
    console.log("Token obtained");

    // Step 3: UserInfo → verify mapped values from external service
    console.log("\nStep 3: Verify UserInfo (mapped from external service)");
    const userinfoResp = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenData.access_token),
    });
    expect(userinfoResp.status).toBe(200);
    expect(userinfoResp.data.sub).toBeDefined();
    expect(userinfoResp.data.email).toBeDefined();
    const firstSub = userinfoResp.data.sub;
    console.log(
      `UserInfo: sub=${userinfoResp.data.sub}, email=${userinfoResp.data.email}, name=${userinfoResp.data.name}`
    );

    // Step 4: 2nd login → same user (sub matches)
    console.log("\nStep 4: 2nd login → verify same user resolved");
    const { authId: authId2, loginStatus: loginStatus2 } = await startAuthAndLogin(
      testEmail,
      testPassword
    );
    expect(loginStatus2).toBe(200);
    const tokenData2 = await completeFlow(authId2);

    const userinfoResp2 = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenData2.access_token),
    });
    expect(userinfoResp2.status).toBe(200);
    expect(userinfoResp2.data.sub).toBe(firstSub);
    console.log(
      `2nd login: sub=${userinfoResp2.data.sub} (matches 1st: ${firstSub})`
    );

    // Step 5: Refresh Token
    console.log("\nStep 5: Refresh Token");
    const refreshResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken: tokenData2.refresh_token,
      clientId,
      clientSecret,
    });
    expect(refreshResp.status).toBe(200);
    expect(refreshResp.data.access_token).toBeDefined();
    console.log("Refresh token successful");

    console.log("\n=== External Password Auth Success Flow Completed ===");
  });

  it("should reject invalid credentials from external service", async () => {
    console.log("\n=== External Password Authentication: Failure Flow ===");

    // Mock server returns 401 for password "invalid"
    const { loginStatus, loginData } = await startAuthAndLogin(
      "test@example.com",
      "invalid"
    );
    // http_request executor は外部サービスのHTTPステータスコードをそのまま透過する
    // MockoonのPOST /auth/password はpassword="invalid"に対して401を返す
    expect(loginStatus).toBe(401);
    console.log(`Authentication rejected: status=${loginStatus} (外部サービスの401が透過)`);

    console.log("\n=== External Password Auth Failure Flow Completed ===");
  });
});
