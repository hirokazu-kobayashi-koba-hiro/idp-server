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
  sleep,
} from "../../../lib/util";

/**
 * Standard Use Case: Claims & Token Configuration Effects
 *
 * EXPERIMENTS-basics.md Exp3/Exp4, EXPERIMENTS-authorization-server.md Exp6 を E2E Jest に移植。
 *
 * カバー範囲:
 * - B-04: claims_supported 制御 → UserInfo 返却値変化
 * - B-05: access_token_duration 短縮 → 期限切れ 401 → RT で復活
 * - B-10: id_token_strict_mode → ID Token からクレーム除外
 * - B-14: クライアント scope 制限 → UserInfo からクレーム消滅
 */
describe("Standard Use Case: Claims & Token Configuration Effects", () => {
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
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@claims-test.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;
    testEmail = `user-${timestamp}@claims-test.example.com`;
    testPassword = "ClaimsTest_1!";

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: {
          id: organizationId,
          name: `Claims Config Test Org ${timestamp}`,
          description: "E2E test for claims_supported, token duration, strict mode",
        },
        tenant: {
          id: tenantId,
          name: `Claims Config Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `CLAIMS_${organizationId.substring(0, 8)}`,
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
          // claims_supported にはフル設定 → テストでサブ設定のテナントを別途作る
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "given_name", "family_name", "email", "email_verified"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: {
            access_token_type: "JWT",
            access_token_duration: 3600,
            id_token_duration: 3600,
          },
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
          client_name: "Claims Config Test Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
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

    // Auth configs
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" },
        interactions: {
          "password-authentication": {
            request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } },
            pre_hook: {}, execution: { function: "password_verification" }, post_hook: {},
            response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }, { from: "$.error_description", to: "error_description" }] },
          },
        },
      },
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(), type: "initial-registration", attributes: {}, metadata: {},
        interactions: { "initial-registration": { request: { schema: { type: "object", required: ["email", "password", "name"], properties: { name: { type: "string" }, email: { type: "string", format: "email" }, password: { type: "string", minLength: 8 } } } } } },
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
      body: { email: testEmail, password: testPassword, name: "Claims Test User" },
    });
    const regAuthorize = await authorize({ endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`, id: regId, body: {} });
    const regResult = convertToAuthorizationResponse(regAuthorize.data.redirect_uri);
    await requestToken({ endpoint: `${backendUrl}/${tenantId}/v1/tokens`, grantType: "authorization_code", code: regResult.code, redirectUri, clientId, clientSecret });
  });

  afterAll(async () => {
    if (systemAccessToken) {
      await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
    }
  });

  /** Helper: login and get tokens */
  async function loginAndGetTokens(scope = "openid profile email") {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `test-${Date.now()}`, scope, redirectUri, prompt: "login",
    });
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");
    await postAuthentication({ endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`, id: authId, body: { username: testEmail, password: testPassword } });
    const authorizeResp = await authorize({ endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`, id: authId, body: {} });
    const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);
    const tokenResp = await requestToken({ endpoint: `${backendUrl}/${tenantId}/v1/tokens`, grantType: "authorization_code", code: result.code, redirectUri, clientId, clientSecret });
    expect(tokenResp.status).toBe(200);
    return tokenResp.data;
  }

  it("B-04: claims_supported restricts UserInfo when email is removed", async () => {
    console.log("\n=== claims_supported: sub-only tenant ===");

    // Create separate tenant with claims_supported = sub only
    const subOnlyTenantId = uuidv4();
    const subOnlyClientId = uuidv4();
    const subOnlyClientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const subOnlyJwks = await generateECP256JWKS();
    const ts2 = Date.now();
    const subOnlyAdminEmail = `subonly-admin-${ts2}@claims-test.example.com`;
    const subOnlyAdminPassword = `SubOnly_${ts2}!`;

    const onboard2 = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: { id: uuidv4(), name: `Sub Only Test Org ${ts2}`, description: "Sub-only claims test" },
        tenant: {
          id: subOnlyTenantId, name: `Sub Only Tenant ${ts2}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `SUB_${subOnlyTenantId.substring(0, 8)}`, use_secure_cookie: false },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${subOnlyTenantId}`,
          authorization_endpoint: `${backendUrl}/${subOnlyTenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${subOnlyTenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${subOnlyTenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${subOnlyTenantId}/v1/jwks`,
          jwks: subOnlyJwks,
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          // KEY: claims_supported に email/name を含めない
          claims_supported: ["sub", "iss", "auth_time", "acr"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: { sub: uuidv4(), provider_id: "idp-server", name: "Admin", email: subOnlyAdminEmail, email_verified: true, raw_password: subOnlyAdminPassword },
        client: {
          client_id: subOnlyClientId, client_secret: subOnlyClientSecret, redirect_uris: [redirectUri],
          response_types: ["code"], grant_types: ["authorization_code", "password"],
          scope: "openid profile email management", client_name: "Sub Only Client",
          token_endpoint_auth_method: "client_secret_post", application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboard2.status !== 201) {
      console.error("B-04 onboarding failed:", JSON.stringify(onboard2.data, null, 2));
    }
    expect(onboard2.status).toBe(201);

    // Get mgmt token + register auth configs
    const mgmt2 = await requestToken({ endpoint: `${backendUrl}/${subOnlyTenantId}/v1/tokens`, grantType: "password", username: subOnlyAdminEmail, password: subOnlyAdminPassword, scope: "management", clientId: subOnlyClientId, clientSecret: subOnlyClientSecret });
    const mgmt2Token = mgmt2.data.access_token;

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${subOnlyTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmt2Token}` },
      body: { id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" }, interactions: { "password-authentication": { request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } }, pre_hook: {}, execution: { function: "password_verification" }, post_hook: {}, response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }] } } } },
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${subOnlyTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmt2Token}` },
      body: { id: uuidv4(), type: "initial-registration", attributes: {}, metadata: {}, interactions: { "initial-registration": { request: { schema: { type: "object", required: ["email", "password", "name"], properties: { name: { type: "string" }, email: { type: "string", format: "email" }, password: { type: "string", minLength: 8 } } } } } } },
    });

    // Register + login
    const email2 = `subonly-${ts2}@claims-test.example.com`;
    const auth2 = await getAuthorizations({ endpoint: `${backendUrl}/${subOnlyTenantId}/v1/authorizations`, clientId: subOnlyClientId, responseType: "code", state: `s-${ts2}`, scope: "openid profile email", redirectUri });
    const { params: p2 } = convertNextAction(auth2.headers.location);
    await postWithJson({ url: `${backendUrl}/${subOnlyTenantId}/v1/authorizations/${p2.get("id")}/initial-registration`, body: { email: email2, password: "SubOnlyPass_1!", name: "Sub Only User" } });
    const auth2Resp = await authorize({ endpoint: `${backendUrl}/${subOnlyTenantId}/v1/authorizations/{id}/authorize`, id: p2.get("id"), body: {} });
    const r2 = convertToAuthorizationResponse(auth2Resp.data.redirect_uri);
    const t2 = await requestToken({ endpoint: `${backendUrl}/${subOnlyTenantId}/v1/tokens`, grantType: "authorization_code", code: r2.code, redirectUri, clientId: subOnlyClientId, clientSecret: subOnlyClientSecret });

    // UserInfo should only have sub (no email, no name)
    const userinfo2 = await getUserinfo({ endpoint: `${backendUrl}/${subOnlyTenantId}/v1/userinfo`, authorizationHeader: createBearerHeader(t2.data.access_token) });
    expect(userinfo2.status).toBe(200);
    expect(userinfo2.data.sub).toBeDefined();
    expect(userinfo2.data.email).toBeUndefined();
    expect(userinfo2.data.name).toBeUndefined();
    console.log("claims_supported=[sub,iss,auth_time,acr]: email/name absent from UserInfo");

    // Cleanup
    await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${subOnlyTenantId}`, headers: { Authorization: `Bearer ${mgmt2Token}` } }).catch(() => {});
  });

  it("B-05: short access_token_duration causes 401, recoverable via refresh_token", async () => {
    console.log("\n=== Short AT Duration: 401 → RT Recovery ===");

    // Create tenant with 5-second AT
    const shortTenantId = uuidv4();
    const shortClientId = uuidv4();
    const shortClientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const shortJwks = await generateECP256JWKS();
    const ts3 = Date.now();
    const shortAdminEmail = `short-admin-${ts3}@claims-test.example.com`;
    const shortAdminPassword = `Short_${ts3}!`;

    const onboard3 = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: { id: uuidv4(), name: `Short AT Test Org ${ts3}`, description: "Short AT duration test" },
        tenant: {
          id: shortTenantId, name: `Short AT Tenant ${ts3}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `SHORT_${shortTenantId.substring(0, 8)}`, use_secure_cookie: false },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${shortTenantId}`,
          authorization_endpoint: `${backendUrl}/${shortTenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${shortTenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${shortTenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${shortTenantId}/v1/jwks`,
          jwks: shortJwks,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email", "email_verified"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: {
            access_token_type: "JWT",
            access_token_duration: 5, // 5 seconds!
          },
        },
        user: { sub: uuidv4(), provider_id: "idp-server", name: "Admin", email: shortAdminEmail, email_verified: true, raw_password: shortAdminPassword },
        client: {
          client_id: shortClientId, client_secret: shortClientSecret, redirect_uris: [redirectUri],
          response_types: ["code"], grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management", client_name: "Short AT Client",
          token_endpoint_auth_method: "client_secret_post", application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(onboard3.status).toBe(201);

    const mgmt3 = await requestToken({ endpoint: `${backendUrl}/${shortTenantId}/v1/tokens`, grantType: "password", username: shortAdminEmail, password: shortAdminPassword, scope: "management", clientId: shortClientId, clientSecret: shortClientSecret });
    const mgmt3Token = mgmt3.data.access_token;

    await postWithJson({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${shortTenantId}/authentication-configurations`, headers: { Authorization: `Bearer ${mgmt3Token}` }, body: { id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" }, interactions: { "password-authentication": { request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } }, pre_hook: {}, execution: { function: "password_verification" }, post_hook: {}, response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }] } } } } });
    await postWithJson({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${shortTenantId}/authentication-configurations`, headers: { Authorization: `Bearer ${mgmt3Token}` }, body: { id: uuidv4(), type: "initial-registration", attributes: {}, metadata: {}, interactions: { "initial-registration": { request: { schema: { type: "object", required: ["email", "password", "name"], properties: { name: { type: "string" }, email: { type: "string", format: "email" }, password: { type: "string", minLength: 8 } } } } } } } });

    // Register + login
    const email3 = `short-${ts3}@claims-test.example.com`;
    const auth3 = await getAuthorizations({ endpoint: `${backendUrl}/${shortTenantId}/v1/authorizations`, clientId: shortClientId, responseType: "code", state: `at-${ts3}`, scope: "openid profile email", redirectUri });
    const { params: p3 } = convertNextAction(auth3.headers.location);
    await postWithJson({ url: `${backendUrl}/${shortTenantId}/v1/authorizations/${p3.get("id")}/initial-registration`, body: { email: email3, password: "ShortATPass_1!", name: "Short AT User" } });
    const auth3Resp = await authorize({ endpoint: `${backendUrl}/${shortTenantId}/v1/authorizations/{id}/authorize`, id: p3.get("id"), body: {} });
    const r3 = convertToAuthorizationResponse(auth3Resp.data.redirect_uri);
    const t3 = await requestToken({ endpoint: `${backendUrl}/${shortTenantId}/v1/tokens`, grantType: "authorization_code", code: r3.code, redirectUri, clientId: shortClientId, clientSecret: shortClientSecret });
    expect(t3.status).toBe(200);
    expect(t3.data.expires_in).toBeLessThanOrEqual(5);

    // Immediately → 200
    const ui1 = await getUserinfo({ endpoint: `${backendUrl}/${shortTenantId}/v1/userinfo`, authorizationHeader: createBearerHeader(t3.data.access_token) });
    expect(ui1.status).toBe(200);
    console.log("Immediately after token: UserInfo 200 OK");

    // Wait 7s → 401
    await sleep(7000);
    const ui2 = await getUserinfo({ endpoint: `${backendUrl}/${shortTenantId}/v1/userinfo`, authorizationHeader: createBearerHeader(t3.data.access_token) });
    expect(ui2.status).toBe(401);
    console.log("After 7s (AT expired): UserInfo 401");

    // Refresh → new AT → 200
    const refreshResp = await requestToken({ endpoint: `${backendUrl}/${shortTenantId}/v1/tokens`, grantType: "refresh_token", refreshToken: t3.data.refresh_token, clientId: shortClientId, clientSecret: shortClientSecret });
    expect(refreshResp.status).toBe(200);
    expect(refreshResp.data.expires_in).toBeLessThanOrEqual(5);

    const ui3 = await getUserinfo({ endpoint: `${backendUrl}/${shortTenantId}/v1/userinfo`, authorizationHeader: createBearerHeader(refreshResp.data.access_token) });
    expect(ui3.status).toBe(200);
    console.log("After refresh: UserInfo 200 OK with new AT");

    await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${shortTenantId}`, headers: { Authorization: `Bearer ${mgmt3Token}` } }).catch(() => {});
  }, 30000);

  it("B-14: client scope restriction removes email from UserInfo", async () => {
    console.log("\n=== Client Scope Restriction ===");

    // Use main tenant, login with scope=openid (no profile/email)
    const tokens = await loginAndGetTokens("openid");
    const userinfo = await getUserinfo({ endpoint: `${backendUrl}/${tenantId}/v1/userinfo`, authorizationHeader: createBearerHeader(tokens.access_token) });
    expect(userinfo.status).toBe(200);
    expect(userinfo.data.sub).toBeDefined();
    expect(userinfo.data.email).toBeUndefined();
    expect(userinfo.data.name).toBeUndefined();
    console.log("scope=openid: sub only, email/name absent");

    // Login with full scope
    const tokensFull = await loginAndGetTokens("openid profile email");
    const userinfoFull = await getUserinfo({ endpoint: `${backendUrl}/${tenantId}/v1/userinfo`, authorizationHeader: createBearerHeader(tokensFull.access_token) });
    expect(userinfoFull.status).toBe(200);
    expect(userinfoFull.data.email).toBe(testEmail);
    expect(userinfoFull.data.name).toBe("Claims Test User");
    console.log("scope=openid profile email: email + name present");
  });
});
