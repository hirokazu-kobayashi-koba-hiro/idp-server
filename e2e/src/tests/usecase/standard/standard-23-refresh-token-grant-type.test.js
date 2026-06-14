import { describe, expect, it, beforeAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson } from "../../../lib/http";
import { requestToken, getAuthorizations, authorize } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction, convertToAuthorizationResponse } from "../../../lib/util";

/**
 * Standard Use Case: refresh_token issuance gated by grant_types (#1355)
 *
 * authorization_code grant のトークンレスポンスに refresh_token を含めるのは、
 * 認可サーバー(grant_types_supported) と クライアント(grant_types) の両方が refresh_token を
 * 許可している場合のみ。どちらかが許可していなければ、使えない refresh_token を発行しない。
 *
 * （RefreshTokenGrantValidator は使用時に server/client 両方を弾くため、発行ゲートはそれと対称）
 */
describe("Standard Use Case: refresh_token issuance gated by grant_types (#1355)", () => {
  let systemAccessToken;
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
  });

  /**
   * 指定の grant_types でテナント/クライアントを onboarding し、authorization_code フローを実行して
   * トークンレスポンスを返す。クリーンアップ用に organizationId も返す。
   */
  async function authorizationCodeTokenResponse({ serverGrantTypes, clientGrantTypes }) {
    const ts = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const jwks = await generateECP256JWKS();
    const adminEmail = `rt-admin-${ts}@example.com`;
    const adminPassword = `Admin_${ts}!`;

    const onboardResp = await onboarding({
      body: {
        organization: { id: organizationId, name: `RT Gate Org ${ts}`, description: "#1355" },
        tenant: {
          id: tenantId,
          name: `RT Gate Tenant ${ts}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `RT_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks,
          grant_types_supported: serverGrantTypes,
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          claims_supported: ["sub", "iss", "name", "email"],
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
          grant_types: clientGrantTypes,
          scope: "openid profile email management",
          client_name: "RT Gate Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardResp.status !== 201) {
      console.error("onboarding failed:", JSON.stringify(onboardResp.data, null, 2));
    }
    expect(onboardResp.status).toBe(201);

    // password grant で管理トークンを取得（認証設定登録用）
    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management",
      clientId,
      clientSecret,
    });
    expect(mgmtResp.status).toBe(200);
    const mgmtToken = mgmtResp.data.access_token;

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
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
                properties: { username: { type: "string" }, password: { type: "string" } },
                required: ["username", "password"],
              },
            },
            pre_hook: {},
            execution: { function: "password_verification" },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.user_id", to: "user_id" },
                { from: "$.error", to: "error" },
              ],
            },
          },
        },
      },
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
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

    // authorization_code フロー（ユーザー登録 → authorize → token）
    const userEmail = `rt-user-${ts}@example.com`;
    const auth = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `rt-${ts}`,
      scope: "openid profile email",
      redirectUri,
    });
    const { params } = convertNextAction(auth.headers.location);
    const authId = params.get("id");
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: userEmail, password: "RtUser_1!", name: "RT User" },
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
      clientId,
      clientSecret,
    });
    return { tokenResp, organizationId };
  }

  const cleanup = async (organizationId) => {
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});
  };

  it("issues refresh_token when both server and client allow refresh_token", async () => {
    const { tokenResp, organizationId } = await authorizationCodeTokenResponse({
      serverGrantTypes: ["authorization_code", "password", "refresh_token"],
      clientGrantTypes: ["authorization_code", "password", "refresh_token"],
    });
    try {
      expect(tokenResp.status).toBe(200);
      expect(tokenResp.data.access_token).toBeDefined();
      expect(tokenResp.data.refresh_token).toBeDefined();
    } finally {
      await cleanup(organizationId);
    }
  });

  it("does NOT issue refresh_token when client lacks refresh_token grant (#1355)", async () => {
    const { tokenResp, organizationId } = await authorizationCodeTokenResponse({
      serverGrantTypes: ["authorization_code", "password", "refresh_token"],
      clientGrantTypes: ["authorization_code", "password"],
    });
    try {
      expect(tokenResp.status).toBe(200);
      expect(tokenResp.data.access_token).toBeDefined();
      // 修正前は使えない refresh_token がレスポンスに含まれていた
      expect(tokenResp.data.refresh_token).toBeUndefined();
    } finally {
      await cleanup(organizationId);
    }
  });

  it("does NOT issue refresh_token when authorization server lacks refresh_token grant", async () => {
    const { tokenResp, organizationId } = await authorizationCodeTokenResponse({
      serverGrantTypes: ["authorization_code", "password"],
      clientGrantTypes: ["authorization_code", "password", "refresh_token"],
    });
    try {
      expect(tokenResp.status).toBe(200);
      expect(tokenResp.data.access_token).toBeDefined();
      expect(tokenResp.data.refresh_token).toBeUndefined();
    } finally {
      await cleanup(organizationId);
    }
  });
});
