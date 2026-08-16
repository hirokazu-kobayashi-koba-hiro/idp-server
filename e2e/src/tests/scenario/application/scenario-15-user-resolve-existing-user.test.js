import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken, getAuthorizations, authorize } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction } from "../../../lib/util";

/**
 * Issue #1767: user_resolve の user_mapping_rules から既存ユーザーの属性を参照できること。
 *
 * 1要素目は「ユーザーを特定するキー自体がマッピングの産物」という順序の制約があり、$.user を
 * 材料に入れるにはマッピングを二度に分ける必要がある。ここではその二段が実際に効いているかを、
 * 外部API認証（1要素目）で確認する。
 *
 * どの mapping rule も出していないキーをユーザーに seed しておき、$.user 経由でそれを読んで
 * 別のキーへ書く。読めていなければ書き込み先のキーは現れない。
 *
 * 検証は管理API GET（＝永続化後の値）で行う。トークンや UserInfo を見ると #1792（1要素目の
 * トランザクションユーザーが痩せている件）の影響を測ってしまい、本件の判定にならない。
 */
describe("Authentication: user_resolve reads the existing user via $.user (#1767)", () => {
  let systemAccessToken;
  let mgmtAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  const redirectUri = "https://app.example.com/callback";

  const PROVIDER_ID = "mock-external-api";
  const SEEDED_KEY = "seeded_rank";
  const CARRIED_KEY = "carried_forward_rank";
  const SEEDED_VALUE = "gold";

  // The mock echoes the submitted username as user_id / email, so this doubles as external_user_id.
  const externalUserId = `ext-user-${Date.now()}@example.com`;
  let seededUserSub;

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@user-resolve.example.com`;
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
          name: `user_resolve $.user Org ${timestamp}`,
          description: "E2E for #1767",
        },
        tenant: {
          id: tenantId,
          name: `user_resolve $.user Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID",
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub", "name", "email", "email_verified"],
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
          scope: "openid profile email management org-management",
          client_name: "user_resolve $.user Client",
          token_endpoint_auth_method: "client_secret_post",
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

    // The rule below is the point of #1767: it reads a key this configuration never produces.
    const authConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "external-api-authentication",
        attributes: {},
        metadata: { type: "external", description: "reads $.user on the 1st factor" },
        interactions: {
          password_verify: {
            request: {
              schema: {
                type: "object",
                required: ["interaction", "username", "password"],
                properties: {
                  interaction: { type: "string" },
                  username: { type: "string", minLength: 1 },
                  password: { type: "string", minLength: 1 },
                },
              },
            },
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/auth/password`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.request_body.username", to: "username" },
                  { from: "$.request_body.password", to: "password" },
                ],
              },
            },
            user_resolve: {
              user_mapping_rules: [
                { from: "$.execution_http_request.response_body.user_id", to: "external_user_id" },
                { from: "$.execution_http_request.response_body.email", to: "email" },
                { static_value: PROVIDER_ID, to: "provider_id" },
                // #1767: readable only if the existing user was looked up before this mapping ran.
                {
                  from: `$.user.custom_properties.${SEEDED_KEY}`,
                  to: `custom_properties.${CARRIED_KEY}`,
                },
              ],
            },
            response: {
              body_mapping_rules: [
                { from: "$.execution_http_request.response_body", to: "*" },
              ],
            },
          },
        },
      },
    });
    expect(authConfigResponse.status).toBe(201);

    // Seed the user so the 1st factor finds it, with a key no mapping rule produces.
    const createUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        sub: uuidv4(),
        provider_id: PROVIDER_ID,
        external_user_id: externalUserId,
        name: "Seeded External User",
        email: externalUserId,
        email_verified: true,
        raw_password: `SeededPass_${Date.now()}!`,
        custom_properties: { [SEEDED_KEY]: SEEDED_VALUE },
      },
    });
    expect(createUserResponse.status).toBe(201);
    seededUserSub = createUserResponse.data.result.sub;
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

  it("resolves $.user on the 1st factor so a rule can derive from the stored attributes", async () => {
    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `user-resolve-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResponse.status).toBe(302);
    const authId = convertNextAction(authResponse.headers.location).params.get("id");

    const loginResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/external-api-authentication`,
      body: {
        interaction: "password_verify",
        username: externalUserId,
        password: "ExternalPass123!",
      },
    });
    expect(loginResponse.status).toBe(200);

    // Attributes are persisted only once the authorization succeeds (#1792), so the flow has to be
    // completed before reading the user back.
    const authorizeResponse = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);

    const getUserResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${seededUserSub}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(getUserResponse.status).toBe(200);
    console.log(
      "custom_properties after 1st factor:",
      JSON.stringify(getUserResponse.data.custom_properties, null, 2)
    );

    // The seeded key is untouched...
    expect(getUserResponse.data.custom_properties[SEEDED_KEY]).toBe(SEEDED_VALUE);
    // ...and the rule that reads it through $.user produced a value. Before #1767 the path did
    // not resolve on a 1st factor, so this key never appeared.
    expect(getUserResponse.data.custom_properties[CARRIED_KEY]).toBe(SEEDED_VALUE);
  });
});
