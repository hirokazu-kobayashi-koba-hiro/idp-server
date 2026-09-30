import { beforeAll, describe, expect, it } from "@jest/globals";
import axios from "axios";
import { wrapper } from "axios-cookiejar-support";
import { CookieJar } from "tough-cookie";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";
import { get, postWithJson } from "../../../lib/http";
import {
  postAuthenticationDeviceInteraction,
  requestToken,
} from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import jwtDecode from "jwt-decode";
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
  let deviceUser;
  let deviceId;
  let management;
  let managementHeaders;

  /** デバイス側で完了する方式（パスワード → FIDO-UAF）を選ばせる acr。 */
  const DEVICE_LAST_ACR = "urn:e2e:cross-site:password-then-fido-uaf";
  /** ブラウザでの入力が一切なく、デバイスだけで認証する方式（login_hint ＋ FIDO-UAF）を選ばせる acr。 */
  const DEVICE_ONLY_ACR = "urn:e2e:cross-site:fido-uaf-only";
  /** 認証ポリシーでブラウザの束縛を外している（auth_session_binding_required: false）方式を選ばせる acr。 */
  const NO_BINDING_ACR = "urn:e2e:cross-site:password-without-binding";

  const authorizations = () => `${backendUrl}/${tenantId}/v1/authorizations`;

  /** 認可リクエストを開始し、認可画面に渡される id を取り出す。 */
  const startAuthorization = async (extraParams = {}) => {
    const response = await get({
      url: authorizations(),
      params: {
        client_id: clientId,
        redirect_uri: redirectUri,
        response_type: "code",
        scope: "openid profile email",
        state: uuidv4(),
        nonce: uuidv4(),
        ...extraParams,
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

  /**
   * Cookie を持たないクライアント。
   *
   * `get` / `postWithJson` はすべての呼び出しで 1 つの Cookie jar を共有するので、ブラウザでいえば
   * 「XHR にも Cookie が付く」状態になる。別サイトの認可画面からの XHR（Safari は Cookie を送らない）や、
   * 別のブラウザを表すときはこちらを使う。
   */
  const withoutCookies = axios.create({
    maxRedirects: 0,
    validateStatus: () => true,
  });

  const complete = async (id, authProof) =>
    get({
      url: `${authorizations()}/${id}/complete`,
      params: { auth_proof: authProof },
    });

  /**
   * /complete はトップレベル遷移で開かれるので、照合に失敗したらテナントのエラー画面へ 302 する。
   * RP へは戻さない（code も error も渡さない）。
   */
  const expectErrorPage = (response) => {
    expect(response.status).toBe(302);
    const location = new URL(response.headers.location);
    expect(location.pathname).toMatch(/\/error\/?$/);
    expect(location.searchParams.get("error")).toBe("invalid_request");
    expect(location.searchParams.get("code")).toBeNull();
  };

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
          acr_values_supported: [DEVICE_LAST_ACR, DEVICE_ONLY_ACR, NO_BINDING_ACR],
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
    management = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;
    managementHeaders = headers;

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

    const emailConfig = await postWithJson({
      url: `${management}/authentication-configurations`,
      headers,
      body: {
        id: uuidv4(),
        type: "email",
        attributes: {},
        metadata: { type: "internal", description: "Email authentication" },
        interactions: {
          "email-authentication-challenge": {
            execution: { function: "email_authentication_challenge" },
          },
        },
      },
    });
    expect(emailConfig.status).toBe(201);

    // FIDO-UAF はデバイス側で完了する。サーバー側は mockoon に委譲する。
    const fidoUafInteraction = (path) => ({
      execution: {
        function: "http_request",
        http_request: {
          url: `http://host.docker.internal:4000/fido-uaf/${path}`,
          method: "POST",
          auth_type: "oauth2",
          oauth_authorization: {
            type: "password",
            token_endpoint: "http://host.docker.internal:4000/token",
            client_id: "your-client-id",
            username: "username",
            password: "password",
            scope: "application",
            cache_buffer_seconds: 10,
            cache_ttl_seconds: 1800,
            cache_enabled: true,
          },
          header_mapping_rules: [
            { static_value: "application/json", to: "Content-Type" },
          ],
          body_mapping_rules: [{ from: "$.request_body", to: "*" }],
        },
      },
      response: {
        body_mapping_rules: [
          { from: "$.execution_http_request.response_body", to: "*" },
        ],
      },
    });
    const fidoUafConfig = await postWithJson({
      url: `${management}/authentication-configurations`,
      headers,
      body: {
        id: uuidv4(),
        type: "fido-uaf",
        attributes: {
          type: "external",
          service_name: "mocky",
          device_id_param: "user_id",
        },
        metadata: {},
        interactions: {
          "fido-uaf-authentication-challenge": fidoUafInteraction(
            "authentication-challenge"
          ),
          "fido-uaf-authentication": fidoUafInteraction("authentication"),
        },
      },
    });
    expect(fidoUafConfig.status).toBe(201);

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
            available_methods: ["password", "email"],
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
          {
            description: "fido_uaf_only",
            priority: 30,
            conditions: { acr_values: [DEVICE_ONLY_ACR] },
            available_methods: ["fido-uaf"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.fido-uaf-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                ],
              ],
            },
          },
          {
            description: "password_without_binding",
            priority: 40,
            conditions: { acr_values: [NO_BINDING_ACR] },
            available_methods: ["password"],
            auth_session_binding_required: false,
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
          {
            description: "password_then_fido_uaf",
            priority: 20,
            conditions: { acr_values: [DEVICE_LAST_ACR] },
            available_methods: ["password", "fido-uaf"],
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
                    path: "$.fido-uaf-authentication.success_count",
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

    // 管理 API は body をそのまま User に読み込むので、デバイスはここで持たせる。
    deviceId = uuidv4();
    deviceUser = {
      sub: uuidv4(),
      provider_id: "idp-server",
      name: faker.person.fullName(),
      email: faker.internet.email().toLowerCase(),
      raw_password: `CrossSiteDevice${timestamp}!`,
      authentication_devices: [
        {
          id: deviceId,
          app_name: "cross-site-device",
          platform: "Android",
          os: "15",
          model: "Pixel",
          locale: "ja",
          notification_channel: "fcm",
          notification_token: "e2e-dummy-token",
          available_methods: ["fido-uaf"],
          priority: 1,
        },
      ],
    };
    const createDeviceUser = await postWithJson({
      url: `${management}/users`,
      headers,
      body: deviceUser,
    });
    expect(createDeviceUser.status).toBe(201);
  }, 120000);

  describe("発行", () => {
    it("認証の開始（challenge）では auth_proof は返らない", async () => {
      const id = await startAuthorization();

      // 誰でも呼べる種類のステップ。ここで proof を出すと、ID を知っているだけで手に入る。
      const response = await postWithJson({
        url: `${authorizations()}/${id}/email-authentication-challenge`,
        body: { email: user.email },
      });

      expect(response.data?.auth_proof).toBeUndefined();
    });

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

    it("別のユーザーが得た auth_proof は使えない", async () => {
      const id = await startAuthorization();
      const otherId = await startAuthorization();

      // 他人の proof を、自分のトランザクションに持ち込む。リクエスト ID の一致だけを見ていると通る。
      const otherProof = (await authenticate(otherId)).data.auth_proof;
      await authenticate(id);

      expect((await authorize(id, { auth_proof: otherProof })).status).toBe(
        400
      );
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

    it("redirect_uri を省略したリクエストでも完了できる", async () => {
      // 登録値が 1 つのクライアントは redirect_uri を省略できる。リクエストから読むと null になる。
      // 省略できるのは OAuth 2.0 のリクエストだけで、openid を付けると OIDC として redirect_uri が
      // 必須になり、認可リクエストの時点で弾かれて /complete まで届かない。
      const started = await get({
        url: authorizations(),
        params: {
          client_id: clientId,
          response_type: "code",
          scope: "profile email",
          state: uuidv4(),
        },
      });
      expect(started.status).toBe(302);
      const id = new URL(started.headers.location).searchParams.get("id");

      const authProof = (await authenticate(id)).data.auth_proof;
      const completionProof = (await authorize(id, { auth_proof: authProof }))
        .data.auth_proof;
      const response = await complete(id, completionProof);

      expect(response.status).toBe(302);
      expect(
        new URL(response.headers.location).searchParams.get("code")
      ).toBeTruthy();
    });

    it("ID Token に sid が入る", async () => {
      // cross-site では OP セッションを指す Cookie がブラウザに届かない。proof がその識別子を
      // 運べていないと、ここで sid が欠ける。欠けるとバックチャネルログアウトがこのクライアント
      // を見つけられない。
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;
      const completionProof = (await authorize(id, { auth_proof: authProof }))
        .data.auth_proof;
      const completed = await complete(id, completionProof);
      const code = new URL(completed.headers.location).searchParams.get("code");

      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "authorization_code",
        code,
        redirectUri,
        clientId,
        clientSecret,
      });
      expect(tokenResponse.status).toBe(200);

      const idToken = jwtDecode(tokenResponse.data.id_token);
      expect(idToken.sid).toBeTruthy();
    });

    it("authorize 用の auth_proof は /complete では使えない", async () => {
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;

      const response = await complete(id, authProof);

      expectErrorPage(response);
    });

    it("一度使った auth_proof は二度使えない", async () => {
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;
      const completionProof = (await authorize(id, { auth_proof: authProof }))
        .data.auth_proof;

      expect((await complete(id, completionProof)).status).toBe(302);
      expectErrorPage(await complete(id, completionProof));
    });

    it("auth_proof を付けなければ拒否される", async () => {
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;
      await authorize(id, { auth_proof: authProof });

      const response = await get({ url: `${authorizations()}/${id}/complete` });

      expectErrorPage(response);
    });

    it("認可リクエストを始めたブラウザ以外からは完了できない", async () => {
      // 攻撃者が自分の資格情報でここまで進め、proof ② を付けた URL を被害者に踏ませる形。
      // 通ると、攻撃者の OP セッションが被害者のブラウザに書かれる。
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;
      const completionProof = (await authorize(id, { auth_proof: authProof }))
        .data.auth_proof;

      const response = await withoutCookies.get(`${authorizations()}/${id}/complete`, {
        params: { auth_proof: completionProof },
      });

      expectErrorPage(response);
      expect(response.headers["set-cookie"] ?? []).not.toEqual(
        expect.arrayContaining([expect.stringMatching(/^IDP_IDENTITY=[^;]+/)])
      );
    });

    it("始めたブラウザ以外からの試行で proof ② は失われない", async () => {
      // 照合は proof を消費する前。順番が逆だと、Cookie を持たない第三者が先に使い切れてしまう。
      const id = await startAuthorization();
      const authProof = (await authenticate(id)).data.auth_proof;
      const completionProof = (await authorize(id, { auth_proof: authProof }))
        .data.auth_proof;

      await withoutCookies.get(`${authorizations()}/${id}/complete`, {
        params: { auth_proof: completionProof },
      });

      expect((await complete(id, completionProof)).status).toBe(302);
    });
  });

  describe("認証ポリシーで束縛を外している（auth_session_binding_required: false）", () => {
    // 別サイト構成は、以前はこの opt-out を立てないと動かなかった。/complete は opt-out を尊重する
    // （別の理由で外しているテナントもありうる）ので、別サイト構成に移したら true に戻すのが前提になる。
    const authorizeWithoutBinding = async () => {
      const id = await startAuthorization({ acr_values: NO_BINDING_ACR });
      const authProof = (await authenticate(id)).data.auth_proof;
      expect(typeof authProof).toBe("string");
      const authorized = await authorize(id, { auth_proof: authProof });
      expect(authorized.status).toBe(200);
      return { id, completionProof: authorized.data.auth_proof };
    };

    it("始めたブラウザからは今までどおり完了できる", async () => {
      const { id, completionProof } = await authorizeWithoutBinding();

      const completed = await complete(id, completionProof);

      expect(completed.status).toBe(302);
      expect(new URL(completed.headers.location).searchParams.get("code")).toBeTruthy();
    });

    it("始めたブラウザ以外からも完了できてしまう（opt-out を尊重した結果。戻すのは運用側）", async () => {
      const { id, completionProof } = await authorizeWithoutBinding();

      const completed = await withoutCookies.get(`${authorizations()}/${id}/complete`, {
        params: { auth_proof: completionProof },
      });

      expect(completed.status).toBe(302);
      expect(new URL(completed.headers.location).searchParams.get("code")).toBeTruthy();
    });
  });

  describe("既存の OP セッションで続ける（with-session）", () => {
    /**
     * 他のテストと Cookie を共有しないブラウザ。共有の jar には前のテストの OP セッションが残っていて、
     * 「最初のログイン」が既存セッションの再利用になってしまう。
     *
     * トップレベル遷移（認可リクエストと /complete）はこの jar で、認可画面からの XHR は
     * withoutCookies で呼ぶ。別サイトの認可画面を Safari で開いたときと同じ形。
     */
    const newBrowser = () =>
      wrapper(
        axios.create({
          jar: new CookieJar(),
          maxRedirects: 0,
          validateStatus: () => true,
        })
      );

    const startIn = async (browser) => {
      const response = await browser.get(authorizations(), {
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
      return new URL(response.headers.location).searchParams.get("id");
    };

    const completeIn = (browser, id, authProof) =>
      browser.get(`${authorizations()}/${id}/complete`, { params: { auth_proof: authProof } });

    const tokenFrom = async (completed) => {
      const token = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "authorization_code",
        code: new URL(completed.headers.location).searchParams.get("code"),
        redirectUri,
        clientId,
        clientSecret,
      });
      expect(token.status).toBe(200);
      return jwtDecode(token.data.id_token);
    };

    // 別サイトの認可画面からの XHR には Cookie が付かない。OP セッションが読めるのは認可リクエストの
    // トップレベル遷移だけなので、そこで認可リクエストに紐づけておき、画面の呼び出しはそれを使う。
    it("別サイトの画面からでも SSO で完了でき、auth_time は最初のログインのまま", async () => {
      const browser = newBrowser();

      // 1 回目: 通常のログイン。認証と authorize は Cookie なしの XHR
      const firstId = await startIn(browser);
      const firstProof = (
        await withoutCookies.post(`${authorizations()}/${firstId}/password-authentication`, {
          username: user.email,
          password: user.raw_password,
        })
      ).data.auth_proof;
      const firstCompletion = (
        await withoutCookies.post(`${authorizations()}/${firstId}/authorize`, {
          auth_proof: firstProof,
        })
      ).data.auth_proof;
      const first = await completeIn(browser, firstId, firstCompletion);
      expect(first.status).toBe(302);
      const firstAuthTime = (await tokenFrom(first)).auth_time;

      // 2 回目: 認可リクエストはトップレベル遷移なので、/complete で書かれた OP セッションの Cookie が届く
      const id = await startIn(browser);

      const viewData = await withoutCookies.get(`${authorizations()}/${id}/view-data`);
      expect(viewData.status).toBe(200);
      expect(viewData.data.session_enabled).toBe(true);

      const withSession = await withoutCookies.post(
        `${authorizations()}/${id}/authorize-with-session`,
        {}
      );
      expect(withSession.status).toBe(200);
      expect(withSession.data.redirect_uri).toBeUndefined();
      expect(typeof withSession.data.auth_proof).toBe("string");

      const completed = await completeIn(browser, id, withSession.data.auth_proof);
      expect(completed.status).toBe(302);

      const idToken = await tokenFrom(completed);
      expect(idToken.auth_time).toBe(firstAuthTime);
      expect(idToken.sid).toBeTruthy();
    });

    it("SSO でも、始めたブラウザ以外からは完了できない", async () => {
      // 認可リクエスト ID が漏れても、別のブラウザは被害者のセッションで code を受け取れない
      const id = await startAuthorization();
      const withSession = await withoutCookies.post(
        `${authorizations()}/${id}/authorize-with-session`,
        {}
      );
      expect(withSession.status).toBe(200);

      const response = await withoutCookies.get(`${authorizations()}/${id}/complete`, {
        params: { auth_proof: withSession.data.auth_proof },
      });

      expectErrorPage(response);
    });
  });

  describe("最後の段がデバイスで完了する（パスワード → FIDO-UAF）", () => {
    // デバイスの呼び出しに返したものは、authorize を呼ぶブラウザには届かない。ブラウザが頼れるのは
    // 1 段目で受け取った proof だけで、OP セッションはデバイスの呼び出しの中で作られる。
    const startDeviceLastAuthorization = async () => {
      const response = await get({
        url: authorizations(),
        params: {
          client_id: clientId,
          redirect_uri: redirectUri,
          response_type: "code",
          scope: "openid profile email",
          acr_values: DEVICE_LAST_ACR,
          state: uuidv4(),
          nonce: uuidv4(),
        },
      });
      expect(response.status).toBe(302);
      const id = new URL(response.headers.location).searchParams.get("id");
      expect(id).toBeTruthy();
      return id;
    };

    const authenticateOnDevice = async (id) => {
      const transactions = await get({
        url: `${management}/authentication-transactions?authorization_id=${id}`,
        headers: managementHeaders,
      });
      expect(transactions.status).toBe(200);
      const transactionId = transactions.data.list[0].id;

      const interact = (interactionType, body) =>
        postAuthenticationDeviceInteraction({
          endpoint: `${backendUrl}/${tenantId}/v1/authentications/{id}/`,
          id: transactionId,
          interactionType,
          body,
        });

      const challenge = await interact("fido-uaf-authentication-challenge", {
        device_id: deviceId,
      });
      expect(challenge.status).toBe(200);
      return interact("fido-uaf-authentication", {});
    };

    it("1 段目の auth_proof で authorize と /complete を通り、ID Token に sid が入る", async () => {
      const id = await startDeviceLastAuthorization();

      const password = await postWithJson({
        url: `${authorizations()}/${id}/password-authentication`,
        body: { username: deviceUser.email, password: deviceUser.raw_password },
      });
      expect(password.status).toBe(200);
      const authProof = password.data.auth_proof;
      expect(typeof authProof).toBe("string");

      const device = await authenticateOnDevice(id);
      expect(device.status).toBe(200);
      // デバイスに返した値はブラウザに届かないので、ここで出しても使い道が無い。
      expect(device.data?.auth_proof).toBeUndefined();

      const status = await get({
        url: `${authorizations()}/${id}/authentication-status`,
      });
      expect(status.data.status).toBe("success");

      const authorized = await authorize(id, { auth_proof: authProof });
      expect(authorized.status).toBe(200);
      const completed = await complete(id, authorized.data.auth_proof);
      expect(completed.status).toBe(302);
      const code = new URL(completed.headers.location).searchParams.get("code");

      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "authorization_code",
        code,
        redirectUri,
        clientId,
        clientSecret,
      });
      expect(tokenResponse.status).toBe(200);

      const idToken = jwtDecode(tokenResponse.data.id_token);
      expect(idToken.sub).toBe(deviceUser.sub);
      expect(idToken.amr).toEqual(
        expect.arrayContaining(["password", "fido-uaf"])
      );
      // OP セッションはデバイスの呼び出しで作られている。認可リクエストに紐づけて引けていないと、
      // ここで sid が欠ける。
      expect(idToken.sid).toBeTruthy();
    });
  });

  describe("デバイスだけで認証する（login_hint ＋ FIDO-UAF）", () => {
    // ブラウザは承認を待ってポーリングするだけで、本人しか通せないステップを通らない。proof は一度も
    // 発行されないので authorize は proof を求めず、始めたブラウザには /complete で束縛する。
    // 同一サイト構成でこのフローが持つのと同じ強さ。
    const startDeviceOnlyAuthorization = async () => {
      const response = await get({
        url: authorizations(),
        params: {
          client_id: clientId,
          redirect_uri: redirectUri,
          response_type: "code",
          scope: "openid profile email",
          acr_values: DEVICE_ONLY_ACR,
          login_hint: `sub:${deviceUser.sub}`,
          state: uuidv4(),
          nonce: uuidv4(),
        },
      });
      expect(response.status).toBe(302);
      return new URL(response.headers.location).searchParams.get("id");
    };

    const approveOnDevice = async (id) => {
      const transactions = await get({
        url: `${management}/authentication-transactions?authorization_id=${id}`,
        headers: managementHeaders,
      });
      expect(transactions.status).toBe(200);
      const transactionId = transactions.data.list[0].id;
      const interact = (interactionType, body) =>
        postAuthenticationDeviceInteraction({
          endpoint: `${backendUrl}/${tenantId}/v1/authentications/{id}/`,
          id: transactionId,
          interactionType,
          body,
        });
      expect(
        (await interact("fido-uaf-authentication-challenge", { device_id: deviceId })).status
      ).toBe(200);
      const approved = await interact("fido-uaf-authentication", {});
      expect(approved.status).toBe(200);
    };

    it("proof なしで authorize を通り、/complete で始めたブラウザに code が渡る", async () => {
      const id = await startDeviceOnlyAuthorization();
      await approveOnDevice(id);

      const authorized = await withoutCookies.post(`${authorizations()}/${id}/authorize`, {});
      expect(authorized.status).toBe(200);
      // code は応答に出ず、/complete 用の proof の中にある
      expect(authorized.data.redirect_uri).toBeUndefined();
      expect(typeof authorized.data.auth_proof).toBe("string");

      const completed = await complete(id, authorized.data.auth_proof);
      expect(completed.status).toBe(302);

      const token = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "authorization_code",
        code: new URL(completed.headers.location).searchParams.get("code"),
        redirectUri,
        clientId,
        clientSecret,
      });
      expect(token.status).toBe(200);
      const idToken = jwtDecode(token.data.id_token);
      expect(idToken.sub).toBe(deviceUser.sub);
      expect(idToken.amr).toEqual(expect.arrayContaining(["fido-uaf"]));
      expect(idToken.sid).toBeTruthy();
    });

    it("始めたブラウザ以外は、authorize の結果を /complete に持ち込めない", async () => {
      const id = await startDeviceOnlyAuthorization();
      await approveOnDevice(id);
      const authorized = await withoutCookies.post(`${authorizations()}/${id}/authorize`, {});
      expect(authorized.status).toBe(200);

      const response = await withoutCookies.get(`${authorizations()}/${id}/complete`, {
        params: { auth_proof: authorized.data.auth_proof },
      });

      expectErrorPage(response);
    });

    it("authorize をもう一度呼んでも、最初の /complete 用 proof は失われない", async () => {
      // proof を求めない認可では、リクエスト ID を知っていれば authorize を呼べる。呼ぶたびに
      // /complete 用の proof を差し替えられると、正規のブラウザが締め出される。
      const id = await startDeviceOnlyAuthorization();
      await approveOnDevice(id);
      const first = await withoutCookies.post(`${authorizations()}/${id}/authorize`, {});
      expect(first.status).toBe(200);

      const second = await withoutCookies.post(`${authorizations()}/${id}/authorize`, {});
      expect(second.status).toBe(400);
      expect(second.data.error).toBe("invalid_request");

      const completed = await complete(id, first.data.auth_proof);
      expect(completed.status).toBe(302);
      expect(new URL(completed.headers.location).searchParams.get("code")).toBeTruthy();
    });

    it("ブラウザ側の認証を経た認可では、proof なしの authorize は今までどおり拒否される", async () => {
      // proof が一度発行された認可は、proof を求め続ける。デバイスだけの扱いには落ちない。
      const id = await startAuthorization();
      await authenticate(id);

      const response = await withoutCookies.post(`${authorizations()}/${id}/authorize`, {});

      expect(response.status).toBe(400);
    });
  });
});
