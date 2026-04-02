import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  authorize,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
} from "../../../lib/util";

/**
 * Standard Use Case: Client Auth Method & Response Type Validation
 *
 * EXPERIMENTS-client.md Exp4, Exp8 を E2E Jest に移植。
 *
 * カバー範囲:
 * - B-17: token_endpoint_auth_method=none — サーバー未対応→エラー / 対応追加→成功
 * - B-18: response_types 制限 — 未登録の response_type → エラー
 */
describe("Standard Use Case: Client Auth Method & Response Type Validation", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let mgmtAccessToken;
  const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

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
    const clientId = uuidv4();
    const clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@client-val.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    // Create tenant with client_secret_post and none in auth methods
    const resp = await onboarding({
      body: {
        organization: { id: organizationId, name: `Client Validation Test ${timestamp}`, description: "Client auth method and response_type test" },
        tenant: {
          id: tenantId, name: `Client Val Tenant ${timestamp}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `CV_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          // Support both client_secret_post and none
          token_endpoint_auth_methods_supported: ["client_secret_post", "none"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: { sub: uuidv4(), provider_id: "idp-server", name: "Admin", email: adminEmail, email_verified: true, raw_password: adminPassword },
        client: {
          client_id: clientId, client_secret: clientSecret, redirect_uris: [redirectUri],
          response_types: ["code"], grant_types: ["authorization_code", "password"],
          scope: "openid profile email management org-management",
          client_name: "Mgmt Client", token_endpoint_auth_method: "client_secret_post", application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(resp.status).toBe(201);

    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password", username: adminEmail, password: adminPassword,
      scope: "management org-management", clientId, clientSecret,
    });
    mgmtAccessToken = mgmtResp.data.access_token;

    // Auth configs
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: { id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" }, interactions: { "password-authentication": { request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } }, pre_hook: {}, execution: { function: "password_verification" }, post_hook: {}, response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }] } } } },
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: { id: uuidv4(), type: "initial-registration", attributes: {}, metadata: {}, interactions: { "initial-registration": { request: { schema: { type: "object", required: ["email", "password", "name"], properties: { name: { type: "string" }, email: { type: "string", format: "email" }, password: { type: "string", minLength: 8 } } } } } } },
    });
  });

  afterAll(async () => {
    if (mgmtAccessToken) {
      await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`, headers: { Authorization: `Bearer ${mgmtAccessToken}` } }).catch(() => {});
    }
    if (systemAccessToken) {
      await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
    }
  });

  it("B-17: client with token_endpoint_auth_method=none can exchange code without client_secret", async () => {
    console.log("\n=== Public Client (auth_method=none) ===");

    // Create client with auth_method=none
    const pubClientId = uuidv4();
    const pubResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        client_id: pubClientId,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code"],
        scope: "openid profile email",
        client_name: "Public Client (none)",
        token_endpoint_auth_method: "none",
        application_type: "web",
      },
    });
    expect(pubResp.status).toBe(201);
    console.log("Public client created (auth_method=none)");

    // Register user and get code
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: pubClientId, responseType: "code",
      state: `pub-${Date.now()}`, scope: "openid profile email", redirectUri,
    });
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: `pub-${Date.now()}@test.com`, password: "PubTestPass_1!", name: "Public User" },
    });
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId, body: {},
    });
    const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);

    // Token exchange WITHOUT client_secret → should succeed (auth_method=none)
    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: result.code,
      redirectUri,
      clientId: pubClientId,
      // No clientSecret!
    });
    expect(tokenResp.status).toBe(200);
    expect(tokenResp.data.access_token).toBeDefined();
    console.log("Token exchange without client_secret: success (auth_method=none)");

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${pubClientId}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    }).catch(() => {});
  });

  it("B-18: authorization request with unregistered response_type should fail", async () => {
    console.log("\n=== response_types Restriction ===");

    // Create client with response_types=["token"] only (no "code")
    const tokenOnlyClientId = uuidv4();
    const tokenOnlyResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        client_id: tokenOnlyClientId,
        client_secret: `tok-${crypto.randomBytes(16).toString("hex")}`,
        redirect_uris: [redirectUri],
        response_types: ["token"],  // Only token, not code
        grant_types: ["implicit"],
        scope: "openid profile email",
        client_name: "Token Only Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    });
    expect(tokenOnlyResp.status).toBe(201);
    console.log("Client created with response_types=[token]");

    // Try response_type=code → should be rejected
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: tokenOnlyClientId, responseType: "code",
      state: `rt-${Date.now()}`, scope: "openid profile email", redirectUri,
    });

    // 未登録の response_type → 302 でエラーページにリダイレクト（unauthorized_client）
    expect(authResp.status).toBe(302);
    const location = authResp.headers.location;
    const errorUrl = new URL(location, backendUrl);
    expect(errorUrl.searchParams.get("error")).toBe("unauthorized_client");
    console.log(`response_type=code rejected: ${errorUrl.searchParams.get("error")}`);

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${tokenOnlyClientId}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    }).catch(() => {});

    console.log("=== response_types Restriction Test Completed ===");
  });
});
