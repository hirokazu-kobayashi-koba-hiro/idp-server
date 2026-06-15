import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson, get } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  authorize,
  getJwks,
  getUserinfo,
} from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * eKYC Use Case: verified_claims structure in Access Token and UserInfo (OIDC4IDA)
 *
 * #1514 / #1435。verified_claims の Access Token / UserInfo 出力が OIDC4IDA 準拠の
 * ネスト構造（verification + claims）で、scope による選択的返却・データ最小化が効くことを検証する。
 *
 * データ登録は 0 ベース（onboarding でテナント／クライアント／認証設定／身元確認設定を新規作成）。
 * 同一ユーザーで完結させるため、フェデレーションではなく test テナントへ initial-registration で
 * 直接登録し、verified_claims 登録「後」に認可コードフロー（password 認証）でトークンを取り直す。
 */
describe("eKYC Use Case: verified_claims structure in AT and UserInfo", () => {

  // verified_claims に登録する値（assert の期待値）
  const GIVEN_NAME = "Taro";
  const FAMILY_NAME = "Yamada";
  const BIRTHDATE = "1990-01-01";
  const TRUST_FRAMEWORK = "eidas";
  const EVIDENCE_TYPE = "driver_license";

  // claims(given_name/family_name) と verification(trust_framework) のみ要求。
  // birthdate・evidence は「ユーザーは保持するが要求しない」→ 返らないこと（データ最小化）を検証する。
  const positiveScope =
    "openid verified_claims:given_name verified_claims:family_name verified_claims:verification:trust_framework";
  // ユーザーが保持しない claim のみ要求 → OIDC4IDA §5.7.4: verified_claims 自体を返さない。
  const nonMatchingScope = "openid verified_claims:nonexistent_claim";
  // 身元確認フロー実行用（apply に必要なスコープ）
  const verificationScope =
    "openid identity_verification_application identity_verification_application_delete identity_verification_result";

  const ts = Date.now();
  const organizationId = uuidv4();
  const tenantId = uuidv4();
  const clientId = uuidv4();
  const clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
  const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";
  const ivType = uuidv4(); // identity verification configuration type

  const endUserEmail = `vc-user-${ts}@example.com`;
  const endUserPassword = `VcUserPass_${ts}!`;

  const tenantBase = `${backendUrl}/${tenantId}`;
  const mgmtBase = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;

  let systemAccessToken;
  let jwks;
  let positiveAccessToken;
  let negativeAccessToken;

  /** GET authorizations → interaction(authId) → authorize → code → token */
  async function authorizationCodeFlow({ scope, interaction }) {
    const authResp = await getAuthorizations({
      endpoint: `${tenantBase}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `vc-${uuidv4()}`,
      scope,
      redirectUri,
    });
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");
    expect(authId).toBeTruthy();

    await interaction(authId);

    const authorizeResp = await authorize({
      endpoint: `${tenantBase}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    const { code } = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);
    expect(code).toBeTruthy();

    const tokenResp = await requestToken({
      endpoint: `${tenantBase}/v1/tokens`,
      code,
      grantType: "authorization_code",
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(tokenResp.status).toBe(200);
    return tokenResp.data.access_token;
  }

  /** initial-registration で新規ユーザー登録しつつトークン取得 */
  async function registerEndUser(scope) {
    return authorizationCodeFlow({
      scope,
      interaction: async (authId) => {
        const reg = await postWithJson({
          url: `${tenantBase}/v1/authorizations/${authId}/initial-registration`,
          body: { email: endUserEmail, password: endUserPassword, name: "VC User" },
        });
        if (reg.status >= 400) console.error("initial-registration failed:", reg.data);
        expect(reg.status).toBe(200);
      },
    });
  }

  /** password 認証で既存ユーザーとしてログインしトークン取得 */
  async function loginEndUser(scope) {
    return authorizationCodeFlow({
      scope,
      interaction: async (authId) => {
        const login = await postWithJson({
          url: `${tenantBase}/v1/authorizations/${authId}/password-authentication`,
          body: { username: endUserEmail, password: endUserPassword },
        });
        if (login.status >= 400) console.error("password-authentication failed:", login.data);
        expect(login.status).toBe(200);
      },
    });
  }

  /** apply → evaluate-result(approve)。承認時に result マッピングで verified_claims が本人へ登録される */
  async function applyAndApprove(token) {
    const applyResp = await postWithJson({
      url: `${tenantBase}/v1/me/identity-verification/applications/${ivType}/apply`,
      headers: { Authorization: `Bearer ${token}` },
      body: {
        given_name: GIVEN_NAME,
        family_name: FAMILY_NAME,
        birthdate: BIRTHDATE,
        trust_framework: TRUST_FRAMEWORK,
        evidence_type: EVIDENCE_TYPE,
      },
    });
    if (applyResp.status >= 400) console.error("apply failed:", applyResp.data);
    expect(applyResp.status).toBe(200);
    const applicationId = applyResp.data.id;

    const evalResp = await postWithJson({
      url: `${tenantBase}/v1/me/identity-verification/applications/${ivType}/${applicationId}/evaluate-result`,
      headers: { Authorization: `Bearer ${token}` },
      body: { approved: true, rejected: false },
    });
    if (evalResp.status >= 400) console.error("evaluate-result failed:", evalResp.data);
    expect(evalResp.status).toBe(200);
  }

  beforeAll(async () => {
    // system token (onboarding 用)
    const sysResp = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(sysResp.status).toBe(200);
    systemAccessToken = sysResp.data.access_token;

    // 0 ベース: org + tenant + admin user + client を作成
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${ts}@ekyc-vc.example.com`;
    const adminPassword = `AdminPass_${ts}!`;
    const clientScope =
      "openid profile email management org-management " +
      "identity_verification_application identity_verification_application_delete identity_verification_result " +
      "verified_claims:given_name verified_claims:family_name verified_claims:nonexistent_claim verified_claims:verification:trust_framework verified_claims:verification:evidence";

    const onboardingResp = await onboarding({
      body: {
        organization: { id: organizationId, name: `eKYC VC Test ${ts}`, description: "verified_claims AT/UserInfo structure" },
        tenant: {
          id: tenantId,
          name: `eKYC VC Tenant ${ts}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `VC_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
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
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: [
            "openid", "profile", "email", "management", "org-management",
            "identity_verification_application", "identity_verification_application_delete", "identity_verification_result",
            "verified_claims:given_name", "verified_claims:family_name", "verified_claims:nonexistent_claim",
            "verified_claims:verification:trust_framework", "verified_claims:verification:evidence",
          ],
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email", "email_verified", "given_name", "family_name", "birthdate", "address"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          claims_parameter_supported: true,
          verified_claims_supported: true,
          extension: {
            access_token_type: "JWT",
            custom_claims_scope_mapping: true,
            access_token_selective_verified_claims: true,
          },
        },
        user: { sub: uuidv4(), provider_id: "idp-server", name: "Admin", email: adminEmail, email_verified: true, raw_password: adminPassword },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "password"],
          scope: clientScope,
          client_name: "eKYC VC Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardingResp.status !== 201) console.error("Onboarding failed:", JSON.stringify(onboardingResp.data, null, 2));
    expect(onboardingResp.status).toBe(201);

    // tenant 管理トークン（admin user / password grant）
    const mgmtResp = await requestToken({
      endpoint: `${tenantBase}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management org-management",
      clientId,
      clientSecret,
    });
    expect(mgmtResp.status).toBe(200);
    const mgmtToken = mgmtResp.data.access_token;
    const mgmtHeaders = { Authorization: `Bearer ${mgmtToken}`, "Content-Type": "application/json" };

    // 認証設定: password / initial-registration
    await postWithJson({
      url: `${mgmtBase}/authentication-configurations`,
      headers: mgmtHeaders,
      body: {
        id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" },
        interactions: { "password-authentication": { request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } }, pre_hook: {}, execution: { function: "password_verification" }, post_hook: {}, response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }] } } },
      },
    });
    await postWithJson({
      url: `${mgmtBase}/authentication-configurations`,
      headers: mgmtHeaders,
      body: {
        id: uuidv4(), type: "initial-registration", attributes: {}, metadata: {},
        interactions: { "initial-registration": { request: { schema: { type: "object", required: ["email", "password", "name"], properties: { name: { type: "string" }, email: { type: "string", format: "email" }, password: { type: "string", minLength: 8 } } } } } },
      },
    });

    // 認証ポリシー: password + initial-registration（登録は initial-registration 単体、ログインは password 単体で完了）
    await postWithJson({
      url: `${mgmtBase}/authentication-policies`,
      headers: mgmtHeaders,
      body: {
        id: uuidv4(), flow: "oauth", enabled: true,
        policies: [{
          description: "password_and_registration", priority: 1, conditions: {},
          available_methods: ["password", "initial-registration"],
          success_conditions: { any_of: [
            [{ path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
            [{ path: "$.initial-registration.success_count", type: "integer", operation: "gte", value: 1 }],
          ] },
        }],
      },
    });

    // 身元確認設定（result マッピング付き。execution は no_action でモック不要）
    await postWithJson({
      url: `${mgmtBase}/identity-verification-configurations`,
      headers: mgmtHeaders,
      body: {
        id: uuidv4(), type: ivType, attributes: { enabled: true }, common: { auth_type: "none" },
        processes: {
          apply: {
            request: { schema: { type: "object", required: ["given_name", "family_name", "birthdate"], properties: { given_name: { type: "string" }, family_name: { type: "string" }, birthdate: { type: "string" }, trust_framework: { type: "string" }, evidence_type: { type: "string" } } } },
            execution: { type: "no_action" },
            store: { application_details_mapping_rules: [{ from: "$.request_body", to: "*" }] },
            response: { body_mapping_rules: [{ from: "$.response_body", to: "*" }] },
          },
          "evaluate-result": {
            execution: { type: "no_action" },
            transition: {
              approved: { any_of: [[{ path: "$.request_body.approved", type: "boolean", operation: "eq", value: true }]] },
              rejected: { any_of: [[{ path: "$.request_body.rejected", type: "boolean", operation: "eq", value: true }]] },
            },
          },
        },
        result: {
          verified_claims_mapping_rules: [
            { from: "$.application.application_details.given_name", to: "claims.given_name" },
            { from: "$.application.application_details.family_name", to: "claims.family_name" },
            { from: "$.application.application_details.birthdate", to: "claims.birthdate" },
            { from: "$.application.application_details.trust_framework", to: "verification.trust_framework" },
            { from: "$.application.application_details.evidence_type", to: "verification.evidence.0.type" },
          ],
          source_details_mapping_rules: [{ from: "$.application.application_details", to: "*" }],
        },
      },
    });

    // 0 ベースのユーザー登録 → 身元確認で verified_claims を本人へ登録
    const registerToken = await registerEndUser(verificationScope);
    await applyAndApprove(registerToken);
    console.log("Identity verification completed - user now has verified_claims");

    // 登録「後」に、要求スコープ別のトークンを認可コードフロー(password)で取り直す
    jwks = (await getJwks({ endpoint: `${tenantBase}/v1/jwks` })).data;
    positiveAccessToken = await loginEndUser(positiveScope);
    negativeAccessToken = await loginEndUser(nonMatchingScope);
  });

  afterAll(async () => {
    await deletion({ url: `${mgmtBase}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
    await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
  });

  describe("Access Token verified_claims structure", () => {
    it("should include verification and claims in nested structure with selective scope", async () => {
      const decodedAt = verifyAndDecodeJwt({ jwt: positiveAccessToken, jwks });
      console.log("Decoded AT:", JSON.stringify(decodedAt.payload, null, 2));

      expect(decodedAt.payload).toHaveProperty("verified_claims");
      const verifiedClaims = decodedAt.payload.verified_claims;
      expect(verifiedClaims).toHaveProperty("verification");
      expect(verifiedClaims).toHaveProperty("claims");

      const claims = verifiedClaims.claims;
      expect(claims.given_name).toBe(GIVEN_NAME);
      expect(claims.family_name).toBe(FAMILY_NAME);
      // データ最小化: ユーザーは birthdate を持つが要求していないので返らない
      expect(claims).not.toHaveProperty("birthdate");
      Object.keys(claims).forEach((key) => expect(["given_name", "family_name"]).toContain(key));

      // verified_claims:verification:trust_framework を要求 → trust_framework が返る
      expect(verifiedClaims.verification.trust_framework).toBe(TRUST_FRAMEWORK);
      // evidence は未要求なので返らない（オプトイン: 生PII の漏洩防止）
      expect(verifiedClaims.verification).not.toHaveProperty("evidence");
    });
  });

  describe("UserInfo verified_claims structure", () => {
    it("should include verified_claims in UserInfo response with selective scope", async () => {
      const userinfoResp = await getUserinfo({
        endpoint: `${tenantBase}/v1/userinfo`,
        authorizationHeader: createBearerHeader(positiveAccessToken),
      });
      console.log("UserInfo response:", JSON.stringify(userinfoResp.data, null, 2));
      expect(userinfoResp.status).toBe(200);
      expect(userinfoResp.data.sub).toBeDefined();

      expect(userinfoResp.data).toHaveProperty("verified_claims");
      const verifiedClaims = userinfoResp.data.verified_claims;
      expect(verifiedClaims).toHaveProperty("verification");
      expect(verifiedClaims).toHaveProperty("claims");

      const claims = verifiedClaims.claims;
      expect(claims.given_name).toBe(GIVEN_NAME);
      expect(claims.family_name).toBe(FAMILY_NAME);
      expect(claims).not.toHaveProperty("birthdate");
      Object.keys(claims).forEach((key) => expect(["given_name", "family_name"]).toContain(key));

      expect(verifiedClaims.verification.trust_framework).toBe(TRUST_FRAMEWORK);
      expect(verifiedClaims.verification).not.toHaveProperty("evidence");
    });
  });

  describe("verified_claims omitted when no requested claim matches (#1514)", () => {
    // OIDC4IDA §5.7.4: ユーザーは verified_claims を持つが要求された verified_claims:* が
    // どの claim にもマッチしない場合、claims を空にして verification だけ漏らさず、verified_claims 自体を返さない。
    it("AT must not contain verified_claims when no requested claim matches", async () => {
      const decodedAt = verifyAndDecodeJwt({ jwt: negativeAccessToken, jwks });
      console.log("Decoded AT (no-match):", JSON.stringify(decodedAt.payload, null, 2));
      expect(decodedAt.payload).not.toHaveProperty("verified_claims");
    });

    it("UserInfo must not contain verified_claims when no requested claim matches", async () => {
      const userinfoResp = await getUserinfo({
        endpoint: `${tenantBase}/v1/userinfo`,
        authorizationHeader: createBearerHeader(negativeAccessToken),
      });
      console.log("UserInfo response (no-match):", JSON.stringify(userinfoResp.data, null, 2));
      expect(userinfoResp.status).toBe(200);
      expect(userinfoResp.data.sub).toBeDefined();
      expect(userinfoResp.data).not.toHaveProperty("verified_claims");
    });
  });

});
