import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  authorize,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  sleep,
} from "../../../lib/util";

/**
 * MFA Use Case: Email OTP Configuration Effects
 *
 * VERIFY-CONFIG-CHANGES (mfa-email) パターン1-3 を E2E Jest に移植。
 *
 * OTP管理はidp-server内部（execution.function = "email_authentication_challenge"）
 * メール送信はno_action（テスト用：送信しない）
 * 検証コードはManagement APIで取得
 *
 * カバー範囲:
 * - C-08: OTP 有効期間 (expire_seconds=5) → 期限切れで検証失敗
 * - C-09: OTP リトライ制限 (retry_count_limitation=2) → 上限超過で拒否
 * - C-10: OTP 再送信 → 旧コード無効化
 */
describe("MFA Use Case: Email OTP Configuration Effects", () => {
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
    clientId = uuidv4();
    clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@otp-config.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;
    testEmail = `user-${timestamp}@otp-config.example.com`;
    testPassword = "OtpConfigPass_1!";

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `OTP Config Test Org ${timestamp}`,
          description: "E2E test for Email OTP configuration effects",
        },
        tenant: {
          id: tenantId,
          name: `OTP Config Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `OTP_CFG_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email", "email_verified"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: "Admin",
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
          client_name: "OTP Config Test Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardingResponse.status !== 201) {
      console.error("Onboarding failed:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);

    const mgmtTokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management org-management",
      clientId,
      clientSecret,
    });
    expect(mgmtTokenResp.status).toBe(200);
    mgmtAccessToken = mgmtTokenResp.data.access_token;

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
                  name: { type: "string" },
                  email: { type: "string", format: "email" },
                  password: { type: "string", minLength: 8 },
                },
              },
            },
          },
        },
      },
    });

    // Register password config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { type: "password" },
        interactions: {
          "password-authentication": {
            request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } },
            pre_hook: {},
            execution: { function: "password_verification" },
            post_hook: {},
            response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }, { from: "$.error_description", to: "error_description" }] },
          },
        },
      },
    });

    // Register email config: internal OTP management + no_action (no email sent)
    // expire_seconds=5, retry_count_limitation=2 for fast testing
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "email",
        attributes: {},
        metadata: {
          type: "no-action",
          description: "Email OTP with short expiry for testing",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
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
              function: "email_authentication_challenge",
              details: {
                function: "http_request",
                sender: "noreply@test.example.com",
                sender_config: {
                  http_request: {
                    url: `${mockApiBaseUrl}/email/send`,
                    method: "POST",
                    header_mapping_rules: [
                      { static_value: "application/json", to: "Content-Type" },
                    ],
                    body_mapping_rules: [
                      { from: "$.request_body", to: "*" },
                    ],
                  },
                },
                templates: {
                  authentication: { subject: "Code", body: "Code: {VERIFICATION_CODE}" },
                  registration: { subject: "Code", body: "Code: {VERIFICATION_CODE}" },
                },
                retry_count_limitation: 2,
                expire_seconds: 5,
              },
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { static_value: "sent", to: "status", condition: { operation: "missing", path: "$.error" } },
                { from: "$.error", to: "error", condition: { operation: "exists", path: "$.error" } },
                { from: "$.error_description", to: "error_description", condition: { operation: "exists", path: "$.error_description" } },
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
              function: "email_authentication",
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { static_value: "verified", to: "status", condition: { operation: "missing", path: "$.error" } },
                { from: "$.error", to: "error", condition: { operation: "exists", path: "$.error" } },
                { from: "$.error_description", to: "error_description", condition: { operation: "exists", path: "$.error_description" } },
              ],
            },
          },
        },
      },
    });

    // Auth policy: email only
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [{
          description: "email_only_for_otp_test",
          priority: 1,
          conditions: {},
          available_methods: ["email", "initial-registration"],
          success_conditions: {
            any_of: [
              [{ path: "$.email-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
              [{ path: "$.initial-registration.success_count", type: "integer", operation: "gte", value: 1 }],
            ],
          },
        }],
      },
    });

    // Register test user
    const regAuth = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `reg-${timestamp}`, scope: "openid profile email", redirectUri,
    });
    const { params: rp } = convertNextAction(regAuth.headers.location);
    const regId = rp.get("id");
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${regId}/initial-registration`,
      body: { email: testEmail, password: testPassword, name: "OTP Config Test User" },
    });
    const regAuthorize = await authorize({ endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`, id: regId, body: {} });
    const regResult = convertToAuthorizationResponse(regAuthorize.data.redirect_uri);
    await requestToken({ endpoint: `${backendUrl}/${tenantId}/v1/tokens`, grantType: "authorization_code", code: regResult.code, redirectUri, clientId, clientSecret });
  });

  afterAll(async () => {
    if (mgmtAccessToken) {
      await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`, headers: { Authorization: `Bearer ${mgmtAccessToken}` } }).catch(() => {});
    }
    if (systemAccessToken) {
      await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
    }
  });

  /** Helper: start auth flow and send email challenge, get verification code via Management API */
  async function startAndChallenge() {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `otp-${Date.now()}`, scope: "openid profile email", redirectUri, prompt: "login",
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    // Send challenge
    const challengeResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: { email: testEmail, template: "authentication" },
    });
    if (challengeResp.status !== 200) {
      console.error("Challenge failed:", JSON.stringify(challengeResp.data, null, 2));
    }
    expect(challengeResp.status).toBe(200);

    // Get verification code via Management API
    const txResp = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-transactions?authorization_id=${authId}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(txResp.status).toBe(200);
    const transactionId = txResp.data.list[0].id;

    const interactionResp = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(interactionResp.status).toBe(200);
    const verificationCode = interactionResp.data.payload.verification_code;

    return { authId, verificationCode };
  }

  /** Helper: verify OTP */
  async function verifyOtp(authId, code) {
    return await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication`,
      body: { verification_code: code },
    });
  }

  it("C-08: OTP expires after expire_seconds (5s)", async () => {
    console.log("\n=== OTP Expiration Test (expire_seconds=5) ===");

    // Immediately → success
    const { authId: authId1, verificationCode: code1 } = await startAndChallenge();
    const immediateResp = await verifyOtp(authId1, code1);
    expect(immediateResp.status).toBe(200);
    console.log("Immediately: OTP verification succeeded");

    // Wait 7s → expired
    const { authId: authId2, verificationCode: code2 } = await startAndChallenge();
    console.log("Waiting 7 seconds for OTP to expire...");
    await sleep(7000);
    const expiredResp = await verifyOtp(authId2, code2);
    expect(expiredResp.status).toBe(400);
    console.log(`After 7s: OTP expired (status=${expiredResp.status})`);

    console.log("=== OTP Expiration Test Completed ===");
  }, 30000);

  it("C-09: OTP rejected after retry_count_limitation (2) exceeded", async () => {
    console.log("\n=== OTP Retry Limit Test (retry_count_limitation=2) ===");

    const { authId, verificationCode } = await startAndChallenge();

    // 2 wrong attempts
    const wrong1 = await verifyOtp(authId, "000000");
    expect(wrong1.status).toBe(400);
    console.log("Attempt 1 (wrong code): rejected");

    const wrong2 = await verifyOtp(authId, "111111");
    expect(wrong2.status).toBe(400);
    console.log("Attempt 2 (wrong code): rejected");

    // 3rd attempt with correct code → should be rejected (limit exceeded)
    const correct = await verifyOtp(authId, verificationCode);
    expect(correct.status).toBe(400);
    console.log("Attempt 3 (correct code, limit exceeded): rejected");

    console.log("=== OTP Retry Limit Test Completed ===");
  });

  it("C-10: re-challenge invalidates old verification code", async () => {
    console.log("\n=== OTP Re-send Invalidation Test ===");

    // Start auth flow
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `resend-${Date.now()}`, scope: "openid profile email", redirectUri, prompt: "login",
    });
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    // 1st challenge → get code via Management API
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: { email: testEmail, template: "authentication" },
    });
    const txResp1 = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-transactions?authorization_id=${authId}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    const txId1 = txResp1.data.list[0].id;
    const int1 = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-interactions/${txId1}/email-authentication-challenge`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    const oldCode = int1.data.payload.verification_code;
    console.log(`1st challenge code: ${oldCode}`);

    // 2nd challenge (re-send) → get new code
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: { email: testEmail, template: "authentication" },
    });
    const int2 = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-interactions/${txId1}/email-authentication-challenge`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    const newCode = int2.data.payload.verification_code;
    console.log(`2nd challenge code: ${newCode}`);

    // Old code → rejected
    const oldResult = await verifyOtp(authId, oldCode);
    expect(oldResult.status).toBe(400);
    console.log("Old code after re-send: rejected");

    // New code → success
    const newResult = await verifyOtp(authId, newCode);
    expect(newResult.status).toBe(200);
    console.log("New code: accepted");

    console.log("=== OTP Re-send Invalidation Test Completed ===");
  });
});
