import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";

/**
 * Standard Use Case: Password Management (Change & Admin Reset)
 *
 * verify.sh (login-password-only) の Step 8-11 を E2E Jest に移植。
 *
 * カバー範囲 (GAP-ANALYSIS A-02, A-03):
 * - パスワード変更 (POST /v1/me/password/change)
 * - 変更後のパスワードでログイン確認
 * - 管理者パスワードリセット (PUT /v1/management/.../users/{sub}/password)
 * - リセット後のパスワードでログイン確認
 */
describe("Standard Use Case: Password Management", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  let testUserSub;
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
    const adminEmail = `admin-${timestamp}@password-mgmt.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: {
          id: organizationId,
          name: `Password Mgmt Test Org ${timestamp}`,
          description: "E2E test for password change and admin reset",
        },
        tenant: {
          id: tenantId,
          name: `Password Mgmt Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
          },
          session_config: {
            cookie_name: `PWD_SESSION_${organizationId.substring(0, 8)}`,
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
          client_name: "Password Mgmt Test Client",
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
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    // Register password authentication config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { type: "password", description: "Password authentication" },
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

    // Register initial-registration config
    await postWithJson({
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

    // Register test user via initial-registration flow
    testEmail = `pwd-user-${timestamp}@password-mgmt.example.com`;
    testPassword = "InitialPass_1!";

    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `setup-${timestamp}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    const regResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: testEmail, password: testPassword, name: "Password Test User" },
    });
    expect(regResp.status).toBe(200);
    testUserSub = regResp.data.user.sub;

    // Complete authorization to persist user
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);
    await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: result.code,
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

  /** Helper: login and get access token */
  async function loginAndGetToken(email, password) {
    const state = `login-${Date.now()}`;
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

    const loginResult = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username: email, password },
    });
    if (loginResult.status !== 200) {
      return { loginStatus: loginResult.status, loginData: loginResult.data };
    }

    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
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
    return {
      loginStatus: 200,
      accessToken: tokenResp.data.access_token,
      idToken: tokenResp.data.id_token,
    };
  }

  it("should change password and login with new password", async () => {
    console.log("\n=== Password Change Test ===");

    // Step 1: Login with initial password
    console.log("Step 1: Login with initial password");
    const { loginStatus, accessToken } = await loginAndGetToken(
      testEmail,
      testPassword
    );
    expect(loginStatus).toBe(200);
    expect(accessToken).toBeDefined();
    console.log("Login successful");

    // Step 2: Change password
    console.log("\nStep 2: Change password");
    const newPassword = "NewChangedPass_2!";

    const changeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/me/password/change`,
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: {
        current_password: testPassword,
        new_password: newPassword,
      },
    });
    expect(changeResponse.status).toBe(200);
    console.log("Password change successful");

    // Step 3: Login with old password should fail
    console.log("\nStep 3: Verify old password is rejected");
    const oldPwdResult = await loginAndGetToken(testEmail, testPassword);
    expect(oldPwdResult.loginStatus).toBe(400);
    console.log("Old password correctly rejected");

    // Step 4: Login with new password should succeed
    console.log("\nStep 4: Verify new password works");
    const newPwdResult = await loginAndGetToken(testEmail, newPassword);
    expect(newPwdResult.loginStatus).toBe(200);
    expect(newPwdResult.accessToken).toBeDefined();
    console.log("New password login successful");

    // Update testPassword for next test
    testPassword = newPassword;

    console.log("\n=== Password Change Test Completed ===");
  });

  it("should reset password via admin Management API", async () => {
    console.log("\n=== Admin Password Reset Test ===");

    // Step 1: Admin resets the user's password
    console.log("Step 1: Admin resets user password via Management API");
    const adminResetPassword = "AdminReset_3!";

    const resetResponse = await putWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserSub}/password`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        raw_password: adminResetPassword,
      },
    });
    expect(resetResponse.status).toBe(200);
    console.log("Admin password reset successful");

    // Step 2: Login with pre-reset password should fail
    console.log("\nStep 2: Verify pre-reset password is rejected");
    const oldPwdResult = await loginAndGetToken(testEmail, testPassword);
    expect(oldPwdResult.loginStatus).toBe(400);
    console.log("Pre-reset password correctly rejected");

    // Step 3: Login with admin-reset password should succeed
    console.log("\nStep 3: Verify admin-reset password works");
    const resetPwdResult = await loginAndGetToken(
      testEmail,
      adminResetPassword
    );
    expect(resetPwdResult.loginStatus).toBe(200);
    expect(resetPwdResult.accessToken).toBeDefined();
    console.log("Admin-reset password login successful");

    console.log("\n=== Admin Password Reset Test Completed ===");
  });
});
