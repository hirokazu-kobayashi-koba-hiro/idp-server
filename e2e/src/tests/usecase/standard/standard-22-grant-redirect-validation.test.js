import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
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
 * Standard Use Case: Grant Type Restriction & Redirect URI Validation
 *
 * EXPERIMENTS-client.md Exp2, Exp3 を E2E Jest に移植。
 *
 * カバー範囲:
 * - B-15: grant_types から refresh_token を除外 → RT 使用時エラー
 * - B-16: 未登録の redirect_uri → エラー
 */
describe("Standard Use Case: Grant Type & Redirect URI Validation", () => {
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
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@grant-test.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: {
          id: organizationId,
          name: `Grant Validation Test Org ${timestamp}`,
          description: "E2E test for grant_types and redirect_uri validation",
        },
        tenant: {
          id: tenantId,
          name: `Grant Validation Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `GRANT_${organizationId.substring(0, 8)}`,
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
          client_name: "Grant Validation Test Client",
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

  it("B-15: should reject refresh_token grant when client grant_types excludes it", async () => {
    console.log("\n=== Grant Type Restriction: refresh_token excluded ===");

    // Create a client WITHOUT refresh_token in grant_types
    const noRtClientId = uuidv4();
    const noRtClientSecret = `no-rt-${crypto.randomBytes(16).toString("hex")}`;

    const clientResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        client_id: noRtClientId,
        client_secret: noRtClientSecret,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code"],  // NO refresh_token
        scope: "openid profile email",
        client_name: "No RT Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    });
    expect(clientResp.status).toBe(201);
    console.log("Client created without refresh_token grant type");

    // Register user + get tokens via authorization_code
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: noRtClientId,
      responseType: "code",
      state: `no-rt-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: `no-rt-${Date.now()}@test.com`, password: "NoRtPass_1!", name: "No RT User" },
    });
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
      clientId: noRtClientId,
      clientSecret: noRtClientSecret,
    });
    expect(tokenResp.status).toBe(200);
    console.log("Authorization code flow: success (token obtained)");

    // Try refresh_token → should fail
    const refreshToken = tokenResp.data.refresh_token;
    if (refreshToken) {
      console.log("RT was issued (known behavior), attempting to use it...");
      const refreshResp = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "refresh_token",
        refreshToken,
        clientId: noRtClientId,
        clientSecret: noRtClientSecret,
      });
      // grant_types に refresh_token がないクライアントでRT使用 → unauthorized_client
      expect(refreshResp.status).toBe(400);
      expect(refreshResp.data.error).toBe("unauthorized_client");
      console.log(`Refresh token usage rejected: status=${refreshResp.status}, error=${refreshResp.data?.error}`);
    } else {
      console.log("RT not issued (ideal behavior)");
    }

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${noRtClientId}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    }).catch(() => {});

    console.log("=== Grant Type Restriction Test Completed ===");
  });

  it("B-16: should reject authorization request with unregistered redirect_uri", async () => {
    console.log("\n=== Redirect URI Validation ===");

    // Step 1: Valid redirect_uri → 302
    console.log("Step 1: Valid redirect_uri → should succeed");
    const validResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `valid-uri-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(validResp.status).toBe(302);
    console.log(`Valid redirect_uri: status=${validResp.status} (302 redirect)`);

    // Step 2: Invalid redirect_uri → error
    console.log("\nStep 2: Unregistered redirect_uri → should fail");
    const invalidResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `invalid-uri-${Date.now()}`,
      scope: "openid profile email",
      redirectUri: "https://evil.example.com/callback",
    });

    // 未登録 redirect_uri の場合、idp-server はエラーページにリダイレクトする（evil URI にはリダイレクトしない）
    expect(invalidResp.status).toBe(302);
    const location = invalidResp.headers.location;
    const redirectHost = new URL(location).hostname;
    expect(redirectHost).not.toBe("evil.example.com");

    const errorUrl = new URL(location, backendUrl);
    expect(errorUrl.searchParams.get("error")).toBe("invalid_request");
    console.log(`Redirected to error page: error=${errorUrl.searchParams.get("error")}`);

    console.log("\n=== Redirect URI Validation Test Completed ===");
  });
});
