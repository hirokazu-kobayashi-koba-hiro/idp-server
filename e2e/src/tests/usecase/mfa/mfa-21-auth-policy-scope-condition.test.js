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
 * MFA Use Case: Scope-Conditioned Authentication Policy
 *
 * VERIFY-CONFIG-CHANGES (mfa-email) パターン6-2, (mfa-sms) パターン3-2 を E2E Jest に移植。
 *
 * カバー範囲 (GAP-ANALYSIS C-12, C-16):
 * - transfers スコープなし → パスワードのみでログイン成功 (amr=["password"])
 * - transfers スコープあり + パスワードのみ → 認可コード発行失敗 (MFA未達)
 * - transfers スコープあり + MFA (password + email) → 成功 (amr=["email","password"])
 */
describe("MFA Use Case: Scope-Conditioned Authentication Policy", () => {
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
    const adminEmail = `admin-${timestamp}@scope-mfa.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;
    testEmail = `user-${timestamp}@scope-mfa.example.com`;
    testPassword = "ScopeMfaPass_1!";

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `Scope MFA Test Org ${timestamp}`,
          description: "E2E test for scope-conditioned MFA policy",
        },
        tenant: {
          id: tenantId,
          name: `Scope MFA Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `SCOPE_MFA_${organizationId.substring(0, 8)}`,
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
          scopes_supported: ["openid", "profile", "email", "transfers", "management", "org-management"],
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
          scope: "openid profile email transfers management org-management",
          client_name: "Scope MFA Test Client",
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
      clientId, clientSecret,
    });
    expect(mgmtTokenResp.status).toBe(200);
    mgmtAccessToken = mgmtTokenResp.data.access_token;

    // Auth configs: initial-registration
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(), type: "initial-registration", attributes: {}, metadata: {},
        interactions: { "initial-registration": { request: { schema: { type: "object", required: ["email", "password", "name"], properties: { name: { type: "string" }, email: { type: "string", format: "email" }, password: { type: "string", minLength: 8 } } } } } },
      },
    });

    // Auth configs: password
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" },
        interactions: { "password-authentication": { request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } }, pre_hook: {}, execution: { function: "password_verification" }, post_hook: {}, response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }, { from: "$.error_description", to: "error_description" }] } } },
      },
    });

    // Auth configs: email (internal OTP + http_request send to mockoon)
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(), type: "email", attributes: {},
        metadata: { type: "no-action", description: "Email OTP", transaction_id_param: "transaction_id", verification_code_param: "verification_code" },
        interactions: {
          "email-authentication-challenge": {
            request: { schema: { type: "object", properties: { email: { type: "string" }, template: { type: "string" } } } },
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
                    header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                    body_mapping_rules: [{ from: "$.request_body", to: "*" }],
                  },
                },
                templates: {
                  authentication: { subject: "Code", body: "Code: {VERIFICATION_CODE}" },
                  registration: { subject: "Code", body: "Code: {VERIFICATION_CODE}" },
                },
                retry_count_limitation: 5,
                expire_seconds: 300,
              },
            },
            post_hook: {},
            response: { body_mapping_rules: [{ static_value: "sent", to: "status", condition: { operation: "missing", path: "$.error" } }, { from: "$.error", to: "error", condition: { operation: "exists", path: "$.error" } }] },
          },
          "email-authentication": {
            request: { schema: { type: "object", properties: { verification_code: { type: "string" } } } },
            pre_hook: {},
            execution: { function: "email_authentication" },
            post_hook: {},
            response: { body_mapping_rules: [{ static_value: "verified", to: "status", condition: { operation: "missing", path: "$.error" } }, { from: "$.error", to: "error", condition: { operation: "exists", path: "$.error" } }] },
          },
        },
      },
    });

    // Auth policy: 2 policies with different priorities
    // - transfers scope → MFA required (password + email), priority 10
    // - default → password only, priority 1
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "mfa_for_transfers",
            priority: 10,
            conditions: { scopes: ["transfers"] },
            available_methods: ["password", "email", "initial-registration"],
            step_definitions: [
              { method: "password", order: 1, requires_user: false, user_identity_source: "username" },
              { method: "email", order: 2, requires_user: true },
            ],
            success_conditions: {
              any_of: [
                [
                  { path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 },
                  { path: "$.email-authentication.success_count", type: "integer", operation: "gte", value: 1 },
                ],
                [{ path: "$.initial-registration.success_count", type: "integer", operation: "gte", value: 1 }],
              ],
            },
          },
          {
            description: "password_only",
            priority: 1,
            conditions: {},
            available_methods: ["password", "initial-registration"],
            step_definitions: [
              { method: "password", order: 1, requires_user: false, user_identity_source: "username" },
            ],
            success_conditions: {
              any_of: [
                [{ path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
                [{ path: "$.initial-registration.success_count", type: "integer", operation: "gte", value: 1 }],
              ],
            },
          },
        ],
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
      body: { email: testEmail, password: testPassword, name: "Scope MFA User" },
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

  it("should require only password when transfers scope is NOT requested", async () => {
    console.log("\n=== No transfers scope → password only ===");

    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `no-transfers-${Date.now()}`,
      scope: "openid profile email", redirectUri, prompt: "login",
    });
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId, body: { username: testEmail, password: testPassword },
    });

    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId, body: {},
    });
    expect(authorizeResp.status).toBe(200);
    const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);
    expect(result.code).toBeDefined();

    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code", code: result.code, redirectUri, clientId, clientSecret,
    });
    expect(tokenResp.status).toBe(200);

    const jwksResp = await get({ url: `${backendUrl}/${tenantId}/v1/jwks` });
    const { payload } = verifyAndDecodeJwt({ jwt: tokenResp.data.id_token, jwks: jwksResp.data });
    expect(payload.amr).toContain("password");
    expect(payload.amr).not.toContain("email");
    console.log(`amr: ${JSON.stringify(payload.amr)} (password only)`);
  });

  it("should fail authorization with only password when transfers scope IS requested", async () => {
    console.log("\n=== transfers scope + password only → authorize fails ===");

    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `transfers-pwd-${Date.now()}`,
      scope: "openid profile email transfers", redirectUri, prompt: "login",
    });
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId, body: { username: testEmail, password: testPassword },
    });

    // Authorize should fail — MFA not satisfied (email-authentication missing)
    // success_conditions が未達の場合、authorize は 400 を返す
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId, body: {},
    });
    expect(authorizeResp.status).toBe(400);
    console.log(`Authorize with password only: status=${authorizeResp.status} (MFA not satisfied)`);
  });

  it("should succeed with MFA (password + email) when transfers scope IS requested", async () => {
    console.log("\n=== transfers scope + MFA → success ===");

    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `transfers-mfa-${Date.now()}`,
      scope: "openid profile email transfers", redirectUri, prompt: "login",
    });
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    // Step 1: Password
    await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId, body: { username: testEmail, password: testPassword },
    });

    // Step 2: Email OTP challenge
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: { email: testEmail, template: "authentication" },
    });

    // Get verification code via Management API
    const txResp = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-transactions?authorization_id=${authId}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    const txId = txResp.data.list[0].id;
    const intResp = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-interactions/${txId}/email-authentication-challenge`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    const code = intResp.data.payload.verification_code;

    // Step 3: Email OTP verify
    const emailResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication`,
      body: { verification_code: code },
    });
    expect(emailResp.status).toBe(200);

    // Step 4: Authorize → success
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId, body: {},
    });
    expect(authorizeResp.status).toBe(200);
    const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);

    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code", code: result.code, redirectUri, clientId, clientSecret,
    });
    expect(tokenResp.status).toBe(200);

    const jwksResp = await get({ url: `${backendUrl}/${tenantId}/v1/jwks` });
    const { payload } = verifyAndDecodeJwt({ jwt: tokenResp.data.id_token, jwks: jwksResp.data });
    expect(payload.amr).toContain("password");
    expect(payload.amr).toContain("email");
    console.log(`amr: ${JSON.stringify(payload.amr)} (password + email MFA)`);
  });
});
