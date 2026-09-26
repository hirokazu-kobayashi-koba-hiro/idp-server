import { beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";
import { get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";

/**
 * 認可画面が別サイトにある構成（ui_config.cross_site）での auth_proof の境界 (#1904)
 *
 * ブラウザは使わない。確かめたいのは「どの呼び出しが受け付けられ、どれが拒否されるか」で、
 * それは HTTP だけで決まる。Safari 実機の一周は e2e/src/tests/browser/ にあるが、
 * あちらは既定でスキップされるので、CI で守るのはここ。
 *
 * このテストは自前のテナントを立てる。既存テナントを cross_site にすると他のテストが巻き添えになる。
 */
describe("cross-site authorization view: auth_proof", () => {
  let tenantId;
  let clientId;
  let clientSecret;
  let redirectUri;
  let user;

  const authorizations = () => `${backendUrl}/${tenantId}/v1/authorizations`;

  /** 認可リクエストを開始し、認可画面に渡される id を取り出す。 */
  const startAuthorization = async () => {
    const response = await get({
      url: authorizations(),
      params: {
        client_id: clientId,
        redirect_uri: redirectUri,
        response_type: "code",
        scope: "openid profile email",
        state: uuidv4(),
        nonce: uuidv4(),
      },
    });
    expect(response.status).toBe(302);
    const id = new URL(response.headers.location).searchParams.get("id");
    expect(id).toBeTruthy();
    return id;
  };

  const authenticate = async (id) =>
    postWithJson({
      url: `${authorizations()}/${id}/password-authentication`,
      body: { username: user.email, password: user.raw_password },
    });

  const authorize = async (id, body) =>
    postWithJson({
      url: `${authorizations()}/${id}/authorize`,
      body: body ?? {},
    });

  const complete = async (id, authProof) =>
    get({
      url: `${authorizations()}/${id}/complete`,
      params: { auth_proof: authProof },
    });

  beforeAll(async () => {
    const systemToken = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(systemToken.status).toBe(200);

    const timestamp = Date.now();
    const organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `cross-site-secret-${timestamp}`;
    redirectUri = "https://rp.example.com/callback";
    user = {
      sub: uuidv4(),
      provider_id: "idp-server",
      name: faker.person.fullName(),
      email: faker.internet.email(),
      email_verified: true,
      raw_password: `CrossSite${timestamp}!`,
    };

    const { jwks } = await generateRS256KeyPair();

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemToken.data.access_token}` },
      body: {
        organization: {
          id: organizationId,
          name: `Cross-site Auth Proof Org ${timestamp}`,
          description: "auth_proof boundary test",
        },
        tenant: {
          id: tenantId,
          name: `Cross-site Auth Proof Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          // ここが本題。宣言したテナントだけが auth_proof を要求する。
          ui_config: {
            base_url: "https://view.example.com",
            cross_site: true,
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: [
            "client_secret_post",
            "client_secret_basic",
          ],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwks,
          grant_types_supported: [
            "authorization_code",
            "refresh_token",
            "password",
          ],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query", "fragment"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["RS256", "ES256"],
          claims_supported: ["sub", "name", "email", "email_verified"],
          extension: {
            access_token_type: "JWT",
            token_signed_key_id: "signing_key_1",
            id_token_signed_key_id: "signing_key_1",
            access_token_duration: 3600,
            id_token_duration: 3600,
            refresh_token_duration: 86400,
          },
        },
        user,
        client: {
          client_id: clientId,
          client_id_alias: `cross-site-client-${timestamp}`,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management",
          client_name: "Cross-site RP",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const adminToken = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: user.email,
      password: user.raw_password,
      scope: "openid profile email management",
      clientId,
      clientSecret,
    });
    expect(adminToken.status).toBe(200);
    const headers = { Authorization: `Bearer ${adminToken.data.access_token}` };
    const management = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;

    const passwordConfig = await postWithJson({
      url: `${management}/authentication-configurations`,
      headers,
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { type: "password", description: "Password authentication" },
        interactions: {
          "password-authentication": {
            request: {
              schema: {
                type: "object",
                required: ["username", "password"],
                properties: {
                  username: { type: "string" },
                  password: { type: "string" },
                },
              },
            },
            execution: { function: "password_verification" },
            response: {
              body_mapping_rules: [
                { from: "$.user_id", to: "user_id" },
                { from: "$.username", to: "username" },
              ],
            },
          },
        },
      },
    });
    expect(passwordConfig.status).toBe(201);

    const policy = await postWithJson({
      url: `${management}/authentication-policies`,
      headers,
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_only",
            priority: 10,
            conditions: { scopes: ["openid"] },
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
          },
        ],
      },
    });
    expect(policy.status).toBe(201);
  }, 120000);

  describe("発行", () => {
    it("ブラウザ側の認証が成功すると auth_proof が返る", async () => {
      const id = await startAuthorization();
      const response = await authenticate(id);

      expect(response.status).toBe(200);
      expect(typeof response.data.auth_proof).toBe("string");
      expect(response.data.auth_proof.length).toBeGreaterThan(0);
    });
  });

  describe("authorize", () => {
    it("auth_proof を付けなければ拒否される", async () => {
      const id = await startAuthorization();
      await authenticate(id);

      const response = await authorize(id);

      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
    });

    it("別のリクエスト向けの auth_proof は拒否される", async () => {
      const mine = await startAuthorization();
      const other = await startAuthorization();
      const otherProof = (await authenticate(other)).data.auth_proof;
      await authenticate(mine);

      const response = await authorize(mine, { auth_proof: otherProof });

      expect(response.status).toBe(400);
    });

    it("でたらめな auth_proof は拒否される", async () => {
      const id = await startAuthorization();
      await authenticate(id);

      const response = await authorize(id, { auth_proof: "a".repeat(32) });

      expect(response.status).toBe(400);
    });

    it("一度使った auth_proof は二度使えない", async () => {
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;

      expect((await authorize(id, { auth_proof: authProof })).status).toBe(200);
      expect((await authorize(id, { auth_proof: authProof })).status).toBe(400);
    });

    it("成功しても code は応答に含まれず、auth_proof だけが返る", async () => {
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;

      const response = await authorize(id, { auth_proof: authProof });

      expect(response.status).toBe(200);
      expect(response.data.redirect_uri).toBeUndefined();
      expect(typeof response.data.auth_proof).toBe("string");
    });
  });

  describe("/complete", () => {
    it("正規の手順なら RP へリダイレクトされ、code が渡る", async () => {
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;
      const completionProof = (await authorize(id, { auth_proof: authProof }))
        .data.auth_proof;

      const response = await complete(id, completionProof);

      expect(response.status).toBe(302);
      const location = new URL(response.headers.location);
      expect(`${location.origin}${location.pathname}`).toBe(redirectUri);
      expect(location.searchParams.get("code")).toBeTruthy();
    });

    it("authorize 用の auth_proof は /complete では使えない", async () => {
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;

      const response = await complete(id, authProof);

      expect(response.status).toBe(400);
    });

    it("一度使った auth_proof は二度使えない", async () => {
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;
      const completionProof = (await authorize(id, { auth_proof: authProof }))
        .data.auth_proof;

      expect((await complete(id, completionProof)).status).toBe(302);
      expect((await complete(id, completionProof)).status).toBe(400);
    });

    it("auth_proof を付けなければ拒否される", async () => {
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;
      await authorize(id, { auth_proof: authProof });

      const response = await get({ url: `${authorizations()}/${id}/complete` });

      expect(response.status).toBe(400);
    });
  });
});
