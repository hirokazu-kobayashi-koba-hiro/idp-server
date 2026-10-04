import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, post, postWithJson } from "../../../lib/http";
import {
  requestToken,
  postAuthentication,
  authorize,
} from "../../../api/oauthClient";
import {
  createJwtWithPrivateKey,
  generateECP256JWKS,
  generateJti,
} from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  toEpocTime,
} from "../../../lib/util";

/**
 * Issue #1907: 認可リクエストのカスタムパラメータを、ログイン中の利用者の属性と照合する。
 *
 * RP が渡した会員番号（member_no）と、ログインした利用者の custom_properties.member_no を、
 * 属性照合の条件のステップ（$.request.custom_params.member_no eq value_path
 * $.user.custom_properties.member_no）で突き合わせる。
 * - 機密クライアントの PAR、署名つきリクエストオブジェクトの値は信頼する（既定）
 * - クエリだけで渡した値は信頼しない（利用者が書き換えられる）
 * - 管理 API は value_path の誤用と未知の出どころを 400 にする
 */
describe("Authentication: custom params verification (#1907)", () => {
  let systemAccessToken;
  let mgmtAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let requestKey;
  let user;
  const redirectUri = "https://app.example.com/callback";
  const MEMBER_NO = "A123";
  // Requests with this scope get the policy that also trusts the query (see beforeAll).
  const QUERY_SCOPE = "member:query";

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const serverJwks = await generateECP256JWKS();
    requestKey = JSON.parse(
      await generateECP256JWKS({ kid: "request_key_1" })
    ).keys[0];
    const { d, ...requestPublicKey } = requestKey;
    const adminEmail = `admin-${timestamp}@custom-params.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const systemTokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(systemTokenResponse.status).toBe(200);
    systemAccessToken = systemTokenResponse.data.access_token;

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Custom Params Org ${timestamp}`,
          description: "E2E for #1907",
        },
        tenant: {
          id: tenantId,
          name: `Custom Params Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          pushed_authorization_request_endpoint: `${backendUrl}/${tenantId}/v1/authorizations/push`,
          jwks: serverJwks,
          scopes_supported: [
            "openid",
            "profile",
            "email",
            "management",
            "org-management",
            QUERY_SCOPE,
          ],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          id_token_signing_alg_values_supported: ["ES256"],
          request_object_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub", "iss", "auth_time", "name", "email"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
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
          grant_types: ["authorization_code", "password"],
          scope: `openid profile email management org-management ${QUERY_SCOPE}`,
          client_name: "Custom Params Client",
          token_endpoint_auth_method: "client_secret_post",
          request_object_signing_alg: "ES256",
          jwks: JSON.stringify({ keys: [requestPublicKey] }),
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

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

    const configurationsUrl = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`;

    const passwordConfigResponse = await postWithJson({
      url: configurationsUrl,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: {},
        interactions: {
          "password-authentication": {
            execution: { function: "password_verification" },
            response: { body_mapping_rules: [] },
          },
        },
      },
    });
    expect(passwordConfigResponse.status).toBe(201);

    const attributeConfigResponse = await postWithJson({
      url: configurationsUrl,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "attribute-verification",
        attributes: {},
        metadata: {},
        interactions: {
          "member-match": {
            execution: {
              details: {
                conditions: {
                  any_of: [
                    [
                      {
                        path: "$.request.custom_params.member_no",
                        type: "string",
                        operation: "eq",
                        value_path: "$.user.custom_properties.member_no",
                      },
                    ],
                  ],
                },
                error: "member_mismatch",
              },
            },
          },
        },
      },
    });
    expect(attributeConfigResponse.status).toBe(201);

    const policyResponse = await postWithJson({
      url: policiesUrl(),
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          // Only for requests with QUERY_SCOPE: a value in the query is enough. For a check
          // that guards against a mix-up, not against the end-user (who can change the query).
          {
            ...memberMatchPolicy(),
            description: "password_and_member_match_trusting_query",
            priority: 2,
            conditions: { scopes: [QUERY_SCOPE] },
            custom_params_trusted_sources: ["pushed", "request_object", "query"],
          },
          memberMatchPolicy(),
        ],
      },
    });
    expect(policyResponse.status).toBe(201);

    user = await createUser(MEMBER_NO);
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

  /** Password, then the member-match step. */
  const memberMatchPolicy = () => ({
    description: "password_and_member_match",
    priority: 1,
    conditions: {},
    available_methods: ["password", "attribute-verification"],
    step_definitions: [
      {
        method: "password",
        order: 1,
        requires_user: false,
        user_identity_source: "username",
      },
      {
        method: "attribute-verification",
        interaction: "member-match",
        order: 2,
        requires_user: true,
      },
    ],
    success_conditions: {
      any_of: [
        [
          {
            path: "$.password-authentication.success_count",
            type: "integer",
            operation: "gte",
            value: 1,
          },
          {
            path: "$.attribute-verification.interactions.member-match.success_count",
            type: "integer",
            operation: "gte",
            value: 1,
          },
        ],
      ],
    },
  });

  const policiesUrl = () =>
    `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`;

  const createUser = async (memberNo) => {
    const email = `user-${Date.now()}-${crypto
      .randomBytes(4)
      .toString("hex")}@custom-params.example.com`;
    const password = "UserPass_1!";
    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        sub: uuidv4(),
        provider_id: "idp-server",
        name: "Member User",
        email,
        email_verified: true,
        raw_password: password,
        custom_properties: { member_no: memberNo },
      },
    });
    expect(response.status).toBe(201);
    return { email, password, sub: response.data.result.sub };
  };

  /** prompt=login unless {@code reuseSession}, so each test signs in afresh. */
  const baseParams = (reuseSession = false) => ({
    client_id: clientId,
    response_type: "code",
    scope: "openid profile email",
    redirect_uri: redirectUri,
    state: `cp-${Date.now()}`,
    ...(reuseSession ? {} : { prompt: "login" }),
  });

  /** Opens the authorization endpoint and returns the authorization id. */
  const startAuthorization = async (params) => {
    const response = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations`,
      params,
    });
    expect(response.status).toBe(302);
    const id = convertNextAction(response.headers.location).params.get("id");
    expect(id).toBeTruthy();
    return id;
  };

  const pushedAuthorization = async (extra, reuseSession = false) => {
    const response = await post({
      url: `${backendUrl}/${tenantId}/v1/authorizations/push`,
      body: new URLSearchParams({
        ...baseParams(reuseSession),
        client_secret: clientSecret,
        ...extra,
      }).toString(),
    });
    expect(response.status).toBe(201);
    return startAuthorization({
      client_id: clientId,
      request_uri: response.data.request_uri,
    });
  };

  const requestObject = (claims) =>
    createJwtWithPrivateKey({
      payload: {
        ...baseParams(),
        iss: clientId,
        aud: `${backendUrl}/${tenantId}`,
        exp: toEpocTime({ adjusted: 3000 }),
        iat: toEpocTime({}),
        nbf: toEpocTime({}),
        jti: generateJti(),
        ...claims,
      },
      privateKey: requestKey,
    });

  const signIn = async (authId) => {
    const response = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username: user.email, password: user.password },
    });
    expect(response.status).toBe(200);
  };

  const matchMember = (authId) =>
    postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/attribute-verification`,
      body: { interaction: "member-match" },
    });

  const authorizeFor = (authId) =>
    authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });

  describe("a value the end-user cannot change", () => {
    it("completes the authorization when the member number of a pushed request matches", async () => {
      const authId = await pushedAuthorization({ member_no: MEMBER_NO });
      await signIn(authId);

      const matchResponse = await matchMember(authId);
      expect(matchResponse.status).toBe(200);

      const authorizeResponse = await authorizeFor(authId);
      expect(authorizeResponse.status).toBe(200);
      const { code } = convertToAuthorizationResponse(
        authorizeResponse.data.redirect_uri
      );
      expect(code).toBeTruthy();
    });

    it("refuses a pushed request for another member", async () => {
      const authId = await pushedAuthorization({ member_no: "B456" });
      await signIn(authId);

      const matchResponse = await matchMember(authId);
      expect(matchResponse.status).toBe(400);
      expect(matchResponse.data.error).toBe("member_mismatch");
    });

    it("uses the member number of a signed request object, not the one the query adds", async () => {
      const authId = await startAuthorization({
        client_id: clientId,
        request: requestObject({ member_no: MEMBER_NO }),
        member_no: "Z999",
      });
      await signIn(authId);

      const matchResponse = await matchMember(authId);
      expect(matchResponse.status).toBe(200);
    });
  });

  describe("a reused session", () => {
    const authorizeWithSession = (authId) =>
      postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize-with-session`,
        body: {},
      });

    it("is checked against the member number of each new request", async () => {
      const firstId = await pushedAuthorization({ member_no: MEMBER_NO });
      await signIn(firstId);
      expect((await matchMember(firstId)).status).toBe(200);
      expect((await authorizeFor(firstId)).status).toBe(200);

      const sameMemberId = await pushedAuthorization({ member_no: MEMBER_NO }, true);
      const sameMemberResponse = await authorizeWithSession(sameMemberId);
      expect(sameMemberResponse.status).toBe(200);

      // The member-match step is checked again for the new request. Its success at the first
      // sign-in is not carried over, so the policy needs no copy of the expression.
      const otherMemberId = await pushedAuthorization({ member_no: "B456" }, true);
      const otherMemberResponse = await authorizeWithSession(otherMemberId);
      expect(otherMemberResponse.status).toBe(400);
      expect(otherMemberResponse.data.error).toBe("member_mismatch");
    });
  });

  describe("a value the end-user can change", () => {
    it("is not used by default when it comes only in the query", async () => {
      const authId = await startAuthorization({
        ...baseParams(),
        member_no: MEMBER_NO,
      });
      await signIn(authId);

      const matchResponse = await matchMember(authId);
      expect(matchResponse.status).toBe(400);
      expect(matchResponse.data.error).toBe("member_mismatch");
    });

    describe("under a policy that trusts the query", () => {
      const queryRequest = (memberNo) => ({
        ...baseParams(),
        scope: `openid profile email ${QUERY_SCOPE}`,
        member_no: memberNo,
      });

      it("completes the authorization when the member number in the query matches", async () => {
        const authId = await startAuthorization(queryRequest(MEMBER_NO));
        await signIn(authId);

        const matchResponse = await matchMember(authId);
        expect(matchResponse.status).toBe(200);

        const authorizeResponse = await authorizeFor(authId);
        expect(authorizeResponse.status).toBe(200);
        const { code } = convertToAuthorizationResponse(
          authorizeResponse.data.redirect_uri
        );
        expect(code).toBeTruthy();
      });

      it("refuses a query for another member", async () => {
        const authId = await startAuthorization(queryRequest("B456"));
        await signIn(authId);

        const matchResponse = await matchMember(authId);
        expect(matchResponse.status).toBe(400);
        expect(matchResponse.data.error).toBe("member_mismatch");
      });
    });
  });

  describe("the management API", () => {
    const policyWith = (policy) => ({
      id: uuidv4(),
      flow: "ciba",
      enabled: true,
      policies: [
        {
          description: "invalid",
          priority: 1,
          conditions: {},
          available_methods: ["password"],
          success_conditions: {
            any_of: [
              [
                {
                  path: "$.password-authentication.success_count",
                  type: "integer",
                  operation: "gte",
                  value: 1,
                },
              ],
            ],
          },
          ...policy,
        },
      ],
    });

    it("refuses value_path on an operation other than eq and ne", async () => {
      const response = await postWithJson({
        url: policiesUrl(),
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
        body: policyWith({
          failure_conditions: {
            any_of: [
              [
                {
                  path: "$.request.custom_params.tier",
                  type: "integer",
                  operation: "gte",
                  value_path: "$.user.custom_properties.tier",
                },
              ],
            ],
          },
        }),
      });
      expect(response.status).toBe(400);
      expect(response.data.error_description).toContain("value_path");
    });

    it("refuses an unknown source in custom_params_trusted_sources", async () => {
      const response = await postWithJson({
        url: policiesUrl(),
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
        body: policyWith({ custom_params_trusted_sources: ["header"] }),
      });
      expect(response.status).toBe(400);
      expect(response.data.error_description).toContain(
        "custom_params_trusted_sources"
      );
    });
  });
});
