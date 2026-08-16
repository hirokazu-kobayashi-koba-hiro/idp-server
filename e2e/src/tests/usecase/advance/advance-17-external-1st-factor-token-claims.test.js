import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  authorize,
  getUserinfo,
  getJwks,
} from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction } from "../../../lib/util";

/**
 * Issue #1792: 外部ソースで解決した1要素目のユーザーが痩せていて、そのセッションのトークンに
 * 既存クレームが載らない。
 *
 * 外部API認証の1要素目は、マッピングの出力に sub / status を足しただけのユーザーを
 * トランザクションに置く。認可グラントはそれをスナップショットするため、既存ユーザーが
 * 持っていた属性はトークンに載らない。一方 UserInfo はDBを読み直すので正しく返る。
 *
 * この非対称そのものを1本で撮る。どの mapping rule も出さないキーをユーザーに seed し、
 * 同一リクエストで発行された ID Token / アクセストークンと UserInfo を突き合わせる。
 *
 * - ID Token / アクセストークン ... AuthorizationCodeGrantService:208 /
 *   ScopeMappingCustomClaimsCreator:54 がいずれも authorizationGrant.user() を読む
 * - UserInfo ................... UserinfoHandler:82 が delegate.findUser() でDBを読み直す
 */
describe("Advance Use Case: Tokens carry the existing user's claims after an external 1st factor (Issue #1792)", () => {
  let systemAccessToken;
  let mgmtAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  const redirectUri = "https://app.example.com/callback";

  const PROVIDER_ID = "mock-external-api";
  // No mapping rule below produces this key, so it can only reach a token via the existing user.
  const SEEDED_KEY = "seeded_rank";
  const SEEDED_VALUE = "gold";
  const SEEDED_SCOPE = `claims:${SEEDED_KEY}`;

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
    const adminEmail = `admin-${timestamp}@token-claims.example.com`;
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
          name: `Token Claims Org ${timestamp}`,
          description: "E2E for #1792",
        },
        tenant: {
          id: tenantId,
          name: `Token Claims Tenant ${timestamp}`,
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
          scopes_supported: [
            "openid",
            "profile",
            "email",
            "management",
            "org-management",
            SEEDED_SCOPE,
          ],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub", "name", "email", "email_verified"],
          // custom_claims_scope_mapping: without it ScopeMappingCustomClaimsCreator never runs and
          // the claims: scope is silently inert.
          extension: {
            access_token_type: "JWT",
            custom_claims_scope_mapping: true,
          },
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
          scope: `openid profile email management org-management ${SEEDED_SCOPE}`,
          client_name: "Token Claims Client",
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

    // Deliberately minimal: identity only. Nothing here produces SEEDED_KEY.
    const authConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "external-api-authentication",
        attributes: {},
        metadata: { type: "external", description: "identity-only user_resolve" },
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

    // Seed the user the 1st factor will find, holding an attribute the login never declares.
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

  it("issues tokens carrying an attribute the login never produced, matching what UserInfo returns", async () => {
    const scope = `openid profile email ${SEEDED_SCOPE}`;

    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `token-claims-${Date.now()}`,
      scope,
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

    const authorizeResponse = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);
    expect(authorizeResponse.data.redirect_uri).toContain("code=");
    const code = new URL(authorizeResponse.data.redirect_uri).searchParams.get("code");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code,
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.id_token).toBeDefined();

    const jwksResponse = await getJwks({ endpoint: `${backendUrl}/${tenantId}/v1/jwks` });
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    const decodedAccessToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.access_token,
      jwks: jwksResponse.data,
    });
    console.log("id_token payload:", JSON.stringify(decodedIdToken.payload, null, 2));
    console.log("access_token payload:", JSON.stringify(decodedAccessToken.payload, null, 2));

    // UserInfo reads the user back from the database, so it is right even today. Asserting it
    // first pins the reference the tokens are compared against — the point of #1792 is that the
    // same authorization answers this question two different ways.
    const userinfoResponse = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
    });
    expect(userinfoResponse.status).toBe(200);
    expect(userinfoResponse.data[SEEDED_KEY]).toBe(SEEDED_VALUE);

    // The grant snapshots whatever the interactor put on the transaction. Before #1792 that was
    // the mapping output plus sub / status, so the seeded attribute never reached a token.
    expect(decodedIdToken.payload[SEEDED_KEY]).toBe(SEEDED_VALUE);
    expect(decodedAccessToken.payload[SEEDED_KEY]).toBe(SEEDED_VALUE);

    // The attribute is untouched in the database either way; only the token was thin.
    const getUserResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${seededUserSub}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(getUserResponse.status).toBe(200);
    expect(getUserResponse.data.custom_properties[SEEDED_KEY]).toBe(SEEDED_VALUE);
  }, 90000);
});
