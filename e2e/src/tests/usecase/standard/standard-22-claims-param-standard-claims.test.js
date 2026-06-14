import { describe, expect, it, beforeAll } from "@jest/globals";
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
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";

/**
 * Standard Use Case: `claims` request parameter for individual standard claims
 *
 * Regression for #1594: GrantUserinfoClaims / GrantIdTokenClaims のクレーム要否判定が
 * コピペ起因で別クレームの has() を参照していた。scope ベース（profile 等）の通常経路では
 * OR 左辺で吸収されて表面化しないため、`claims` リクエストパラメータ / id_token_strict_mode の
 * 経路を直接踏んで検証する。
 *
 * カバー範囲:
 * - GrantUserinfoClaims#shouldAddFamilyName: profile scope 無しで claims に family_name を要求 → UserInfo に出る
 *   （旧バグ: hasMiddleName を参照 → family_name を要求しても出ず、middle_name 要求で誤混入）
 * - GrantIdTokenClaims#shouldAddFamilyName (strict): claims id_token に family_name → ID Token に出る（旧: hasGivenName）
 * - GrantIdTokenClaims#shouldAddWebsite   (strict): claims id_token に website     → ID Token に出る（旧: hasProfile）
 * - GrantIdTokenClaims#shouldAddPhoneNumber(strict): claims id_token に phone_number→ ID Token に出る（旧: hasName）
 */
describe("Standard Use Case: claims request parameter (individual standard claims) #1594", () => {
  let systemAccessToken;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

  // 検証対象クレームに区別できる値を持たせる
  const FAMILY_NAME = "FamilyTarget";
  const MIDDLE_NAME = "MiddleDecoy";
  const GIVEN_NAME = "GivenDecoy";
  const NAME = "Name Decoy";
  const WEBSITE = "https://example.com/website-target";
  const PROFILE = "https://example.com/profile-decoy";
  const PHONE_NUMBER = "+81-90-1111-2222";

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
   * テナント + クライアント + 管理者を onboarding し、password 認証設定を登録、
   * 全標準クレームを持つエンドユーザーを作成して、ログイン可能な状態にする。
   */
  async function setupTenant({ strictMode, claimsSupported, scopesSupported }) {
    const ts = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const jwks = await generateECP256JWKS();
    const adminEmail = `cp-admin-${ts}@claims-param.example.com`;
    const adminPassword = `Admin_${ts}!`;

    const onboardResp = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `Claims Param Test Org ${ts}`,
          description: "#1594 claims request parameter regression",
        },
        tenant: {
          id: tenantId,
          name: `Claims Param Tenant ${ts}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `CP_${tenantId.substring(0, 8)}`,
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
          jwks,
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: scopesSupported,
          claims_supported: claimsSupported,
          claims_parameter_supported: true,
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: {
            access_token_type: "JWT",
            ...(strictMode ? { id_token_strict_mode: true } : {}),
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
          grant_types: ["authorization_code", "password"],
          scope: scopesSupported.join(" "),
          client_name: "Claims Param Client",
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

    // password 認証設定（インタラクティブログイン用）
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
                { from: "$.error_description", to: "error_description" },
              ],
            },
          },
        },
      },
    });

    // 全標準クレームを持つエンドユーザーを作成
    const userEmail = `user-${ts}@claims-param.example.com`;
    const userPassword = "ClaimsParam_1!";
    const createUserResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        sub: uuidv4(),
        provider_id: "idp-server",
        name: NAME,
        given_name: GIVEN_NAME,
        family_name: FAMILY_NAME,
        middle_name: MIDDLE_NAME,
        profile: PROFILE,
        website: WEBSITE,
        email: userEmail,
        email_verified: true,
        phone_number: PHONE_NUMBER,
        phone_number_verified: true,
        raw_password: userPassword,
      },
    });
    if (createUserResp.status !== 201) {
      console.error("create user failed:", JSON.stringify(createUserResp.data, null, 2));
    }
    expect(createUserResp.status).toBe(201);

    return { organizationId, tenantId, clientId, clientSecret, userEmail, userPassword };
  }

  /** インタラクティブ password ログイン → token 取得（claims パラメータ対応） */
  async function loginAndGetTokens({ tenantId, clientId, clientSecret, scope, claims, userEmail, userPassword }) {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `s-${Date.now()}-${Math.floor(Math.random() * 100000)}`,
      scope,
      redirectUri,
      claims,
      prompt: "login",
    });
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");
    await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username: userEmail, password: userPassword },
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
    expect(tokenResp.status).toBe(200);
    return tokenResp.data;
  }

  const decodeIdToken = (idToken) =>
    JSON.parse(Buffer.from(idToken.split(".")[1], "base64url").toString());

  it("UserInfo: family_name requested via claims param (no profile scope) is returned", async () => {
    const ctx = await setupTenant({
      strictMode: false,
      scopesSupported: ["openid", "profile", "email", "management"],
      claimsSupported: ["sub", "iss", "name", "given_name", "family_name", "middle_name", "email"],
    });

    try {
      // family_name のみ claims で要求（profile scope は付けない）
      const tokens = await loginAndGetTokens({
        ...ctx,
        scope: "openid",
        claims: JSON.stringify({ userinfo: { family_name: { essential: true } } }),
      });
      const userinfo = await getUserinfo({
        endpoint: `${backendUrl}/${ctx.tenantId}/v1/userinfo`,
        authorizationHeader: createBearerHeader(tokens.access_token),
      });
      expect(userinfo.status).toBe(200);
      console.log("[userinfo family_name] keys:", Object.keys(userinfo.data));
      // 修正前は family_name が出なかった（shouldAddFamilyName が hasMiddleName を参照）
      expect(userinfo.data.family_name).toBe(FAMILY_NAME);
      // profile scope なし & 未要求の標準クレームは出ない
      expect(userinfo.data.name).toBeUndefined();
      expect(userinfo.data.given_name).toBeUndefined();

      // 逆ケース: middle_name を要求 → middle_name は出るが family_name は誤混入しない
      const tokens2 = await loginAndGetTokens({
        ...ctx,
        scope: "openid",
        claims: JSON.stringify({ userinfo: { middle_name: { essential: true } } }),
      });
      const userinfo2 = await getUserinfo({
        endpoint: `${backendUrl}/${ctx.tenantId}/v1/userinfo`,
        authorizationHeader: createBearerHeader(tokens2.access_token),
      });
      expect(userinfo2.status).toBe(200);
      expect(userinfo2.data.middle_name).toBe(MIDDLE_NAME);
      expect(userinfo2.data.family_name).toBeUndefined();
    } finally {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${ctx.organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("ID Token (strict mode): family_name / website / phone_number requested via claims param are returned", async () => {
    const ctx = await setupTenant({
      strictMode: true,
      scopesSupported: ["openid", "profile", "email", "phone", "management"],
      claimsSupported: [
        "sub", "iss", "name", "given_name", "family_name",
        "profile", "website", "phone_number", "phone_number_verified", "email",
      ],
    });

    try {
      // 修正対象の3クレームを claims id_token で要求
      const tokens = await loginAndGetTokens({
        ...ctx,
        scope: "openid",
        claims: JSON.stringify({
          id_token: {
            family_name: { essential: true },
            website: { essential: true },
            phone_number: { essential: true },
          },
        }),
      });
      const payload = decodeIdToken(tokens.id_token);
      console.log("[id_token positive] keys:", Object.keys(payload));
      expect(payload.family_name).toBe(FAMILY_NAME); // 旧: hasGivenName 参照で欠落
      expect(payload.website).toBe(WEBSITE); // 旧: hasProfile 参照で欠落
      expect(payload.phone_number).toBe(PHONE_NUMBER); // 旧: hasName 参照で欠落

      // 逆ケース: 旧バグが誤参照していたクレーム（given_name/profile/name）を要求
      // → 要求したものだけ出て、family_name/website/phone_number は混入しない
      const tokens2 = await loginAndGetTokens({
        ...ctx,
        scope: "openid",
        claims: JSON.stringify({
          id_token: {
            given_name: { essential: true },
            profile: { essential: true },
            name: { essential: true },
          },
        }),
      });
      const payload2 = decodeIdToken(tokens2.id_token);
      console.log("[id_token negative] keys:", Object.keys(payload2));
      expect(payload2.given_name).toBe(GIVEN_NAME);
      expect(payload2.profile).toBe(PROFILE);
      expect(payload2.name).toBe(NAME);
      expect(payload2.family_name).toBeUndefined(); // 旧: given_name 要求で誤混入
      expect(payload2.website).toBeUndefined(); // 旧: profile 要求で誤混入
      expect(payload2.phone_number).toBeUndefined(); // 旧: name 要求で誤混入
    } finally {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${ctx.organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });
});
