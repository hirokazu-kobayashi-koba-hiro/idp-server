import { afterAll, beforeAll, beforeEach, describe, expect, it } from "@jest/globals";
import {
  adminServerConfig,
  backendUrl,
  clientSecretBasicClient,
  clientSecretPostClient,
  federationServerConfig,
  publicClient,
  serverConfig
} from "../../testConfig";
import { authorize, deny, postAuthentication, requestToken } from "../../../api/oauthClient";
import { deletion, get, post, postWithJson, putWithJson } from "../../../lib/http";
import { requestAuthorizations } from "../../../oauth/request";
import { generateECP256JWKS } from "../../../lib/jose";
import { convertNextAction } from "../../../lib/util";
import { v4 as uuidv4 } from "uuid";
import axios from "axios";
import {
  createOperatorWithPermission,
  deleteOperators,
  fetchPermissionIds,
} from "../../../lib/adminPermission";

/**
 * 外部IdPアカウント連携 (#1531)
 *
 * 外部IdP役は federation テナント自身。RP は test-tenant の clientSecretPost。
 * 連携は park-and-claim で進む: 未認証の callback は何も確定させず、
 * Bearer を持つ complete だけが linked_external_accounts に書く。
 */
describe("account linking", () => {

  const PROVIDER = "account-linking";
  const RETURN_TO = "https://client.example.org/linking/callback";

  const ownerUser = {
    username: "ito.ichiro@gmail.com",
    password: "successUserCode001",
  };
  const otherUser = {
    username: "ida.verified.user@gmail.com",
    password: "successUserCode001",
  };

  /** RP でログインし、アクセストークンを得る。cookie jar に OP セッションが載る。 */
  const MANAGE_SCOPE = "linked_account:manage";

  const login = async (user, scope = `openid profile email ${MANAGE_SCOPE}`) => {
    const { status, authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      redirectUri: clientSecretPostClient.redirectUri,
      responseType: "code",
      state: "account-linking-e2e",
      scope,
      user,
    });
    expect(status).toBe(200);

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    return tokenResponse.data.access_token;
  };

  const startLink = async (accessToken, provider = PROVIDER, scope = "openid profile email offline_access") =>
    await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/link/${provider}`,
      headers: { Authorization: `Bearer ${accessToken}` },
      body: {
        redirect_uri: RETURN_TO,
        scope,
      },
    });

  /** 組織管理 API を叩くための管理者トークン。 */
  const requestAdminAccessToken = async () => {
    const adminTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(adminTokenResponse.status).toBe(200);
    return adminTokenResponse.data.access_token;
  };

  /**
   * 外部IdP でログインして同意し、連携 callback の URL を得る。
   *
   * 外部IdP役テナントは password 認証設定を持たないため、scenario-02 と同じく
   * email 認証で通す。検証コードは管理APIから取り出す。
   */
  const consentAtExternalIdp = async (externalAuthorizationUri) => {
    const authorizationResponse = await get({ url: externalAuthorizationUri });
    expect(authorizationResponse.status).toBe(302);

    const { params } = convertNextAction(authorizationResponse.headers.location);
    const id = params.get("id");

    const challengeResponse = await postAuthentication({
      endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication-challenge`,
      id,
      body: {
        email: ownerUser.username,
        email_template: "authentication",
      },
    });
    expect(challengeResponse.status).toBe(200);

    const adminAccessToken = await requestAdminAccessToken();

    const transactionResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    expect(transactionResponse.status).toBe(200);
    const transactionId = transactionResponse.data.list[0].id;

    const interactionResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    expect(interactionResponse.status).toBe(200);

    const verificationResponse = await postAuthentication({
      endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication`,
      id,
      body: {
        verification_code: interactionResponse.data.payload.verification_code,
      },
    });
    expect(verificationResponse.status).toBe(200);

    const authorizeResponse = await authorize({
      endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/authorize`,
      id,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);

    return authorizeResponse.data.redirect_uri;
  };

  /** 連携を1回通して alias を返す。 */
  const linkOnce = async (accessToken, provider, scope = "account offline_access") => {
    const linkResponse = await startLink(accessToken, provider, scope);
    expect(linkResponse.status).toBe(201);

    const startResponse = await get({ url: linkResponse.data.start_url });
    expect(startResponse.status).toBe(302);

    const callbackUri = await consentAtExternalIdp(startResponse.headers.location);
    const callbackResponse = await get({ url: callbackUri });
    expect(callbackResponse.status).toBe(302);

    const completeResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/complete`,
      headers: { Authorization: `Bearer ${accessToken}` },
      body: { state: linkResponse.data.state },
    });
    expect(completeResponse.status).toBe(201);

    return completeResponse.data.alias;
  };

  const READ_TOKEN_SCOPE = "linked_account:read_token";

  /** 戻り先へ返せない失敗は、テナントのエラー画面へ送る。 */
  const expectErrorPage = (location, error) => {
    expect(location).toContain("/error/?");
    const params = new URL(location).searchParams;
    expect(params.get("error")).toBe(error);
    expect(params.get("tenant_id")).toBe(serverConfig.tenantId);
  };

  /** 保管された外部トークンを、クライアント認証 + ユーザーのアクセストークンで取り出す。 */
  const retrieveToken = async ({ alias, token, clientId, clientSecret, headers = {} }) => {
    const params = new URLSearchParams();
    if (token !== undefined) params.append("token", token);
    if (clientId) params.append("client_id", clientId);
    if (clientSecret) params.append("client_secret", clientSecret);
    return await post({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/linked-external-accounts/${alias}/token`,
      headers,
      body: params.toString(),
    });
  };

  /** Cookie を持たないブラウザ。OP セッションも束縛 Cookie も無い。 */
  const getWithoutCookies = async (url) =>
    await axios.get(url, { maxRedirects: 0, validateStatus: () => true });

  /** 連携の失敗として RP の戻り先へ返されたことを確かめる。state は渡したときだけ載る。 */
  const expectReturnedFailure = (location, error, state) => {
    expect(location.startsWith(`${RETURN_TO}?`)).toBe(true);
    const params = new URL(location).searchParams;
    expect(params.get("linking")).toBe("failed");
    expect(params.get("error")).toBe(error);
    expect(params.get("error_description")).toBeNull();
    if (state) {
      expect(params.get("state")).toBe(state);
    } else {
      expect(params.get("state")).toBeNull();
    }
  };

  /** 外部IdP 側でトークンがまだ有効か。外部IdP役テナントにも同じ client が居る。 */
  const isActiveAtExternalIdp = async (token) => {
    const params = new URLSearchParams();
    params.append("token", token);
    params.append("client_id", clientSecretPostClient.clientId);
    params.append("client_secret", clientSecretPostClient.clientSecret);
    const response = await post({
      url: `${backendUrl}/${federationServerConfig.tenantId}/v1/tokens/introspection`,
      body: params.toString(),
    });
    expect(response.status).toBe(200);
    return response.data.active;
  };

  const listAliases = async (accessToken) => {
    const response = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(response.status).toBe(200);
    return response.data.list.map((it) => it.alias);
  };

  const unlink = async (accessToken, alias) =>
    await deletion({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/${alias}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });

  describe("success pattern", () => {

    it("link -> start -> callback -> complete", async () => {
      const accessToken = await login(ownerUser);

      const linkResponse = await startLink(accessToken);
      console.log(linkResponse.data);
      expect(linkResponse.status).toBe(201);
      expect(linkResponse.data.start_url).toContain("/v1/linking/start?state=");
      expect(linkResponse.data.state).not.toBeUndefined();

      const state = linkResponse.data.state;

      // start は idp-server 自身の URL。ここで操作者を検証してから外部IdPへ送る。
      const startResponse = await get({ url: linkResponse.data.start_url });
      expect(startResponse.status).toBe(302);

      // コールバックは Bearer を運べないので、start が発行するこの Cookie だけが
      // 「戻ってきたブラウザは start を通ったブラウザか」を判定できる。
      // SameSite=Lax でないと外部IdPからのクロスサイト遷移で送られず、連携が成立しない。
      const setCookie = (startResponse.headers["set-cookie"] || []).join("; ");
      expect(setCookie).toContain("IDP_LINK_BINDING=");
      expect(setCookie).toContain("HttpOnly");
      expect(setCookie).toContain("SameSite=Lax");

      const externalAuthorizationUri = startResponse.headers.location;
      console.log(externalAuthorizationUri);
      expect(externalAuthorizationUri).toContain(federationServerConfig.tenantId);
      expect(externalAuthorizationUri).toContain("code_challenge=");
      expect(externalAuthorizationUri).toContain("code_challenge_method=S256");

      const callbackUri = await consentAtExternalIdp(externalAuthorizationUri);
      console.log(callbackUri);
      expect(callbackUri).toContain(`/${serverConfig.tenantId}/v1/linking/callback/${PROVIDER}`);

      // callback は park するだけ。RP の戻り先へ 302 する。
      const callbackResponse = await get({ url: callbackUri });
      expect(callbackResponse.status).toBe(302);
      expect(callbackResponse.headers.location).toContain(RETURN_TO);
      expect(callbackResponse.headers.location).toContain("linking=done");

      // まだ確定していないので一覧には出ない。
      const beforeCompleteResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(beforeCompleteResponse.status).toBe(200);
      const aliasesBefore = beforeCompleteResponse.data.list.map((it) => it.alias);

      const completeResponse = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/complete`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { state },
      });
      console.log(completeResponse.data);
      expect(completeResponse.status).toBe(201);
      expect(completeResponse.data.provider).toBe(PROVIDER);
      expect(completeResponse.data.alias).toContain(`${PROVIDER}-`);
      expect(completeResponse.data.access_token_expires_at).not.toBeNull();

      const alias = completeResponse.data.alias;

      const listResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(listResponse.status).toBe(200);
      const aliasesAfter = listResponse.data.list.map((it) => it.alias);
      expect(aliasesAfter).toContain(alias);

      // 同じ外部アカウントをもう一度連携しても行は増えない。
      // UNIQUE (tenant_id, provider, federated_user_id) があるので、再連携は
      // 既存行の更新でなければ成立しない。alias は URL に出るため保たれる。
      expect(aliasesAfter.filter((it) => it === alias)).toHaveLength(1);
      if (aliasesBefore.includes(alias)) {
        expect(aliasesAfter).toHaveLength(aliasesBefore.length);
      }

      // state は単回。二度目の complete は通らない。
      const replayResponse = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/complete`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { state },
      });
      expect(replayResponse.status).toBeGreaterThanOrEqual(400);
    });
  });

  describe("plain OAuth 2.0 delegation", () => {

    const OAUTH2_PROVIDER = "account-linking-oauth2";

    /**
     * 連携先が OAuth 2.0 の認可委譲だけを行う場合。
     *
     * openid スコープを要求しないので id_token は返らず、userinfo も設定されていない。
     * つまりプロバイダーは「誰の grant か」を一切名乗らない。連携はそれでも成立する必要がある
     * ——リンクのキーは (tenant, user, provider, alias) であって、外部の識別子ではないため。
     */
    it("links without any identifier from the provider", async () => {
      const accessToken = await login(ownerUser);

      const linkResponse = await startLink(accessToken, OAUTH2_PROVIDER, "account offline_access");
      console.log(linkResponse.data);
      expect(linkResponse.status).toBe(201);

      const state = linkResponse.data.state;

      const startResponse = await get({ url: linkResponse.data.start_url });
      expect(startResponse.status).toBe(302);

      const externalAuthorizationUri = startResponse.headers.location;
      // openid が無いことが前提。あると id_token が返り、別の経路になってしまう。
      expect(externalAuthorizationUri).not.toContain("openid");

      const callbackUri = await consentAtExternalIdp(externalAuthorizationUri);
      expect(callbackUri).toContain(`/v1/linking/callback/${OAUTH2_PROVIDER}`);

      const callbackResponse = await get({ url: callbackUri });
      expect(callbackResponse.status).toBe(302);

      const completeResponse = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/complete`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { state },
      });
      console.log(completeResponse.data);
      expect(completeResponse.status).toBe(201);
      expect(completeResponse.data.provider).toBe(OAUTH2_PROVIDER);
      expect(completeResponse.data.alias).toContain(`${OAUTH2_PROVIDER}-`);

      // 識別子が無いので表示名も無い。連携そのものは成立している。
      expect(completeResponse.data.federated_username).toBeNull();

      const listResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(listResponse.status).toBe(200);
      expect(listResponse.data.list.map((it) => it.alias)).toContain(
        completeResponse.data.alias
      );
    });

    /**
     * 識別子が無いと「同じ外部アカウントか」を判定できないので、再連携の検出は成立しない。
     * もう一度連携すれば別の grant として増える。OAuth 2.0 の委譲としてはそれが自然。
     */
    it("adds a new link each time, because there is nothing to compare", async () => {
      const accessToken = await login(ownerUser);

      const firstAlias = await linkOnce(accessToken, OAUTH2_PROVIDER);
      const secondAlias = await linkOnce(accessToken, OAUTH2_PROVIDER);

      expect(firstAlias).not.toBe(secondAlias);

      const listResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const aliases = listResponse.data.list.map((it) => it.alias);
      expect(aliases).toContain(firstAlias);
      expect(aliases).toContain(secondAlias);
    });
  });

  describe("stored token retrieval", () => {

    it("hands out only the external access token, which works at the external IdP", async () => {
      const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);
      const alias = await linkOnce(accessToken, PROVIDER, "openid profile email offline_access");

      const response = await retrieveToken({
        alias,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(200);
      expect(response.headers["cache-control"]).toContain("no-store");
      expect(response.data.access_token).toBeTruthy();
      expect(response.data.token_type).toBe("Bearer");
      expect(response.data.issued_token_type).toBe("urn:ietf:params:oauth:token-type:access_token");
      expect(response.data.expires_in).toBeGreaterThan(0);
      // 外部のリフレッシュトークンはここに残して、ここで使う。アプリが使うとローテーションで
      // 保管側が無効になるため、外には出さない。
      expect(response.data.refresh_token).toBeUndefined();
      expect(response.data.id_token).toBeUndefined();

      // 返ったのは外部IdPが発行した本物のトークン。外部IdPの userinfo で使える。
      const userinfoResponse = await get({
        url: `${backendUrl}/${federationServerConfig.tenantId}/v1/userinfo`,
        headers: { Authorization: `Bearer ${response.data.access_token}` },
      });
      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data.sub).toBeTruthy();

      // idp-server のトークンではないので、idp-server のイントロスペクションでは有効にならない。
      const introspectionParams = new URLSearchParams();
      introspectionParams.append("token", response.data.access_token);
      introspectionParams.append("client_id", clientSecretPostClient.clientId);
      introspectionParams.append("client_secret", clientSecretPostClient.clientSecret);
      const introspectionResponse = await post({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/tokens/introspection`,
        body: introspectionParams.toString(),
      });
      expect(introspectionResponse.data.active).toBe(false);
    });

    /**
     * 取り出しのイベントには、連携先（account_linking.provider）と断った理由（account_linking.error）
     * が付く。連携先ごとに数えて、連携先の不調とクライアントの探りを分けるため。alias は付けない。
     */
    const countEvents = async (eventType, query) => {
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/security-events?event_type=${eventType}${query}&limit=1`,
        headers: { Authorization: `Bearer ${await requestAdminAccessToken()}` },
      });
      expect(response.status).toBe(200);
      return response.data;
    };

    /** イベントは非同期に記録されるので、件数が増えるまで待って最新の 1 件を返す。 */
    const waitForNewEvent = async (eventType, query, before) => {
      for (let i = 0; i < 20; i++) {
        const events = await countEvents(eventType, query);
        if (events.total_count > before) {
          return events.list[0];
        }
        await new Promise((resolve) => setTimeout(resolve, 500));
      }
      return null;
    };

    it("records the provider on the retrieval's events, so failures can be counted per provider", async () => {
      const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);
      const alias = await linkOnce(accessToken, PROVIDER, "openid profile email offline_access");
      const query = `&details.account_linking.provider=${PROVIDER}`;
      const before = (await countEvents("external_account_token_retrieved", query)).total_count;

      const retrieved = await retrieveToken({
        alias,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(retrieved.status).toBe(200);

      const event = await waitForNewEvent("external_account_token_retrieved", query, before);
      expect(event).not.toBeNull();
      // alias は account_linking に入れない（リクエストの URL としては既存の details に残る）。
      expect(event.detail.account_linking).toEqual({ provider: PROVIDER });
    });

    it("records the error, and no provider, when it refuses before finding the account", async () => {
      const query = "&details.account_linking.error=invalid_token";
      const before = (await countEvents("external_account_token_retrieval_failed", query)).total_count;

      const refused = await retrieveToken({
        alias: `${PROVIDER}-1`,
        token: "not-a-token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(refused.status).toBe(401);

      const event = await waitForNewEvent("external_account_token_retrieval_failed", query, before);
      expect(event).not.toBeNull();
      expect(event.detail.account_linking).toEqual({ error: "invalid_token" });
    });

    it("refuses a user token without linked_account:read_token", async () => {
      const accessToken = await login(ownerUser);

      const response = await retrieveToken({
        alias: `${PROVIDER}-1`,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(403);
      expect(response.data.error).toBe("insufficient_scope");
    });

    it("refuses a public client", async () => {
      const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);

      const response = await retrieveToken({
        alias: `${PROVIDER}-1`,
        token: accessToken,
        clientId: publicClient.clientId,
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(401);
      expect(response.data.error).toBe("invalid_client");
    });

    it("refuses a client that fails authentication", async () => {
      const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);

      const response = await retrieveToken({
        alias: `${PROVIDER}-1`,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: "wrong-secret",
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(401);
      expect(response.data.error).toBe("invalid_client");
    });

    it("refuses a request without the user's token", async () => {
      const response = await retrieveToken({
        alias: `${PROVIDER}-1`,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
    });

    // エラーの優先順位: クライアント認証を先に見る。順番が入れ替わると、認証できないクライアントに
    // ユーザートークンの状態（無い・別のクライアント向け）を教えることになる。
    it("answers invalid_client before looking at a missing user token", async () => {
      const response = await retrieveToken({
        alias: `${PROVIDER}-1`,
        clientId: clientSecretPostClient.clientId,
        clientSecret: "wrong-secret",
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(401);
      expect(response.data.error).toBe("invalid_client");
    });

    it("answers invalid_client before looking at whom the user token was issued to", async () => {
      const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);

      const basic = Buffer.from(`${clientSecretBasicClient.clientId}:wrong-secret`).toString("base64");
      const response = await retrieveToken({
        alias: `${PROVIDER}-1`,
        token: accessToken,
        headers: { Authorization: `Basic ${basic}` },
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(401);
      expect(response.data.error).toBe("invalid_client");
    });

    it("refuses a user token issued to another client", async () => {
      // 漏れたユーザーのトークンを別の confidential client が持ち込んでも、外部トークンは出さない。
      const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);

      const basic = Buffer.from(
        `${clientSecretBasicClient.clientId}:${clientSecretBasicClient.clientSecret}`
      ).toString("base64");
      const response = await retrieveToken({
        alias: `${PROVIDER}-1`,
        token: accessToken,
        headers: { Authorization: `Basic ${basic}` },
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(403);
      expect(response.data.error).toBe("unauthorized_client");
    });

    it("refuses an unknown user token", async () => {
      const response = await retrieveToken({
        alias: `${PROVIDER}-1`,
        token: "not-a-token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(401);
      expect(response.data.error).toBe("invalid_token");
    });

    it("answers 404 for an alias the user has not linked", async () => {
      const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);

      const response = await retrieveToken({
        alias: `${PROVIDER}-9999`,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(404);
    });
  });

  describe("unlink", () => {

    it("removes the link and revokes its tokens at the external IdP", async () => {
      const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);
      const alias = await linkOnce(accessToken, PROVIDER, "openid profile email offline_access");

      const tokenResponse = await retrieveToken({
        alias,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const externalAccessToken = tokenResponse.data.access_token;

      const unlinkResponse = await unlink(accessToken, alias);
      expect(unlinkResponse.status).toBe(204);

      expect(await listAliases(accessToken)).not.toContain(alias);

      const afterResponse = await retrieveToken({
        alias,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(afterResponse.status).toBe(404);

      // 行を消すだけでは、写し取られたトークンは外部IdP側で生き続ける。解除は外部でも失効させる。
      const userinfoResponse = await get({
        url: `${backendUrl}/${federationServerConfig.tenantId}/v1/userinfo`,
        headers: { Authorization: `Bearer ${externalAccessToken}` },
      });
      expect(userinfoResponse.status).toBe(401);

      const againResponse = await unlink(accessToken, alias);
      expect(againResponse.status).toBe(404);
      expect(againResponse.data.error).toBe("not_found");
    });

    it("never hands a removed link's alias to a new link", async () => {
      const accessToken = await login(ownerUser);
      const removed = await linkOnce(accessToken, "account-linking-oauth2");
      expect(removed).toMatch(/^account-linking-oauth2-[a-z0-9]{8}$/);
      expect((await unlink(accessToken, removed)).status).toBe(204);

      // A sequence would hand the freed number to the next link, and an RP that kept the old
      // alias would then read another account's tokens under it.
      const next = await linkOnce(accessToken, "account-linking-oauth2");
      expect(next).not.toBe(removed);
      expect((await listAliases(accessToken))).not.toContain(removed);
    });

    it("answers 404 for another user's alias and leaves it in place", async () => {
      const ownerAccessToken = await login(ownerUser);
      const alias = await linkOnce(ownerAccessToken, PROVIDER, "openid profile email offline_access");

      const otherAccessToken = await login(otherUser);
      const response = await unlink(otherAccessToken, alias);
      expect(response.status).toBe(404);

      expect(await listAliases(ownerAccessToken)).toContain(alias);
    });
  });

  describe("user deletion", () => {

    /** 消してよい使い捨てのユーザーを作る。既存のテストユーザーは他のテストが使う。 */
    const createDisposableUser = async () => {
      const email = `account-linking-${Date.now()}@example.com`;
      const password = "successUserCode001";
      const response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${await requestAdminAccessToken()}` },
        body: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: email,
          email,
          raw_password: password,
        },
      });
      expect(response.status).toBe(201);
      return { username: email, password };
    };

    /** 後始末は非同期のライフサイクル処理なので、外部IdP で失効するまで少し待つ。 */
    const waitUntilInactiveAtExternalIdp = async (token) => {
      let active = await isActiveAtExternalIdp(token);
      for (let i = 0; i < 20 && active; i++) {
        await new Promise((resolve) => setTimeout(resolve, 500));
        active = await isActiveAtExternalIdp(token);
      }
      return active;
    };

    // 識別子を持たない OAuth 2.0 の連携先を使う。OIDC 側は外部アカウントが既存ユーザーに連携済みで、
    // duplicate_link_policy=reject により別ユーザーへは連携できない。
    it("revokes the linked accounts' tokens at the external IdP when the user is deleted", async () => {
      const user = await createDisposableUser();
      const accessToken = await login(user, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);
      const alias = await linkOnce(accessToken, "account-linking-oauth2");

      const tokenResponse = await retrieveToken({
        alias,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const externalAccessToken = tokenResponse.data.access_token;
      expect(await isActiveAtExternalIdp(externalAccessToken)).toBe(true);

      const deleteResponse = await deletion({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(deleteResponse.status).toBe(204);

      // linked_external_accounts は user に FK を張っていないので、後始末はライフサイクルの
      // executor の仕事。
      expect(await waitUntilInactiveAtExternalIdp(externalAccessToken)).toBe(false);
    });

    it("cleans up the same way when an administrator deletes the user", async () => {
      const user = await createDisposableUser();
      const accessToken = await login(user, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);
      const alias = await linkOnce(accessToken, "account-linking-oauth2");

      const tokenResponse = await retrieveToken({
        alias,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const externalAccessToken = tokenResponse.data.access_token;

      // コールバックまで進めて complete しない連携も残しておく。ユーザーが消えると誰も確定できない。
      const parkedLink = await startLink(accessToken, "account-linking-oauth2", "account offline_access");
      expect(parkedLink.status).toBe(201);
      const parkedStart = await get({ url: parkedLink.data.start_url });
      expect(parkedStart.status).toBe(302);
      const parkedCallback = await get({ url: await consentAtExternalIdp(parkedStart.headers.location) });
      expect(parkedCallback.status).toBe(302);

      const userinfoResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/userinfo`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(userinfoResponse.status).toBe(200);

      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/users/${userinfoResponse.data.sub}`,
        headers: { Authorization: `Bearer ${await requestAdminAccessToken()}` },
      });
      expect(deleteResponse.status).toBe(204);

      expect(await waitUntilInactiveAtExternalIdp(externalAccessToken)).toBe(false);
    });
  });

  describe("user scopes", () => {

    const listWith = async (accessToken) =>
      await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

    it("refuses a user token carrying neither linked_account:read nor linked_account:manage", async () => {
      const accessToken = await login(ownerUser, "openid profile email");

      const list = await listWith(accessToken);
      expect(list.status).toBe(403);
      expect(list.data.error).toBe("insufficient_scope");
      expect(list.data.scope).toBe("linked_account:read");

      const start = await startLink(accessToken);
      expect(start.status).toBe(403);
      expect(start.data.scope).toBe(MANAGE_SCOPE);

      const removal = await unlink(accessToken, `${PROVIDER}-1`);
      expect(removal.status).toBe(403);
      expect(removal.data.scope).toBe(MANAGE_SCOPE);
    });

    it("lets linked_account:read list, and nothing more", async () => {
      const accessToken = await login(ownerUser, "openid profile email linked_account:read");

      expect((await listWith(accessToken)).status).toBe(200);
      expect((await startLink(accessToken)).status).toBe(403);
      expect((await unlink(accessToken, `${PROVIDER}-1`)).status).toBe(403);
    });

    it("refuses to start a link to a provider the client is not configured to link", async () => {
      const accessToken = await login(ownerUser);
      // Not in the client's linking_providers. Refused before the provider is looked up, so the
      // answer is the same whether or not the tenant configures it.
      const response = await startLink(accessToken, "no-such-provider");
      expect(response.status).toBe(403);
    });

    it("refuses a requested scope beyond what the provider is configured for", async () => {
      const accessToken = await login(ownerUser);
      // account-linking-oauth2 is configured for "account offline_access" only.
      const response = await startLink(accessToken, "account-linking-oauth2", "account offline_access admin");
      expect(response.status).toBe(400);
      expect(response.data.error_description).toContain("admin");

      const narrower = await startLink(accessToken, "account-linking-oauth2", "account");
      expect(narrower.status).toBe(201);
    });

    it("lets linked_account:manage list too, since managing needs the list", async () => {
      const accessToken = await login(ownerUser);
      expect((await listWith(accessToken)).status).toBe(200);
    });

    it("refuses to complete a link from a client other than the one that started it", async () => {
      const accessToken = await login(ownerUser);
      const linkResponse = await startLink(accessToken, "account-linking-oauth2", "account offline_access");
      expect(linkResponse.status).toBe(201);
      const startResponse = await get({ url: linkResponse.data.start_url });
      const callbackResponse = await get({
        url: await consentAtExternalIdp(startResponse.headers.location),
      });
      expect(callbackResponse.status).toBe(302);

      // 同じユーザーが別のクライアントで得た、manage 付きのトークン。
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        redirectUri: clientSecretBasicClient.redirectUri,
        responseType: "code",
        state: "account-linking-other-client",
        scope: `openid ${MANAGE_SCOPE}`,
        user: ownerUser,
      });
      const basic = Buffer.from(
        `${clientSecretBasicClient.clientId}:${clientSecretBasicClient.clientSecret}`
      ).toString("base64");
      const otherClientToken = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretBasicClient.redirectUri,
        basicAuth: { Authorization: `Basic ${basic}` },
      });
      expect(otherClientToken.status).toBe(200);
      expect(otherClientToken.data.scope).toContain(MANAGE_SCOPE);

      const completeResponse = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/complete`,
        headers: { Authorization: `Bearer ${otherClientToken.data.access_token}` },
        body: { state: linkResponse.data.state },
      });
      expect(completeResponse.status).toBe(403);

      // 始めたクライアントからは、まだ確定できる。
      const ownComplete = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/complete`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { state: linkResponse.data.state },
      });
      expect(ownComplete.status).toBe(201);
    });
  });

  describe("management API", () => {

    const orgUserBase = (sub) =>
      `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/users/${sub}/linked-external-accounts`;

    const isActiveAtExternalIdp = async (token) => {
      const params = new URLSearchParams();
      params.append("token", token);
      params.append("client_id", clientSecretPostClient.clientId);
      params.append("client_secret", clientSecretPostClient.clientSecret);
      const response = await post({
        url: `${backendUrl}/${federationServerConfig.tenantId}/v1/tokens/introspection`,
        body: params.toString(),
      });
      return response.data.active;
    };

    let adminToken;
    let sub;
    let alias;
    let externalAccessToken;
    const operators = [];

    beforeAll(async () => {
      adminToken = await requestAdminAccessToken();

      const email = `account-linking-admin-${Date.now()}@example.com`;
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${adminToken}` },
        body: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: email,
          email,
          raw_password: "successUserCode001",
        },
      });
      expect(createResponse.status).toBe(201);
      sub = createResponse.data.result.sub;

      const accessToken = await login(
        { username: email, password: "successUserCode001" },
        `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`
      );
      alias = await linkOnce(accessToken, "account-linking-oauth2");

      const tokenResponse = await retrieveToken({
        alias,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      externalAccessToken = tokenResponse.data.access_token;
    });

    afterAll(async () => {
      await deleteOperators(
        adminToken,
        serverConfig.organizationId,
        serverConfig.tenantId,
        operators
      );
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/users/${sub}`,
        headers: { Authorization: `Bearer ${adminToken}` },
      });
    });

    it("lists the user's links with the external identifier, and never the tokens", async () => {
      const response = await get({
        url: orgUserBase(sub),
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      expect(response.status).toBe(200);
      const account = response.data.list.find((it) => it.alias === alias);
      expect(account).toBeDefined();
      expect(account.provider).toBe("account-linking-oauth2");
      expect(account).toHaveProperty("federated_user_id");
      expect(JSON.stringify(response.data)).not.toContain(externalAccessToken);
      expect(account.access_token).toBeUndefined();
      expect(account.encrypted_access_token).toBeUndefined();
    });

    it("reads one link, and answers 404 for an unknown alias or user", async () => {
      const response = await get({
        url: `${orgUserBase(sub)}/${alias}`,
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      expect(response.status).toBe(200);
      expect(response.data.alias).toBe(alias);

      const unknownAlias = await get({
        url: `${orgUserBase(sub)}/account-linking-oauth2-9999`,
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      expect(unknownAlias.status).toBe(404);

      const unknownUser = await get({
        url: orgUserBase(uuidv4()),
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      expect(unknownUser.status).toBe(404);
    });

    it("refuses an operator holding an unrelated permission", async () => {
      const permissionIds = await fetchPermissionIds(
        adminToken,
        serverConfig.organizationId,
        serverConfig.tenantId
      );
      const operator = await createOperatorWithPermission({
        managementToken: adminToken,
        organizationId: serverConfig.organizationId,
        tenantId: serverConfig.tenantId,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        permissionName: "idp:session:read",
        permissionId: permissionIds.get("idp:session:read"),
        label: "linked-account-decoy",
      });
      operators.push(operator);

      const list = await get({
        url: orgUserBase(sub),
        headers: { Authorization: `Bearer ${operator.accessToken}` },
      });
      expect(list.status).toBe(403);

      const removal = await deletion({
        url: `${orgUserBase(sub)}/${alias}`,
        headers: { Authorization: `Bearer ${operator.accessToken}` },
      });
      expect(removal.status).toBe(403);
    });

    /**
     * 新しい権限は、権限を列挙しているロールに付ける前に権限レコードが要る。
     * テナントのオンボーディング後に足された権限なので、無ければ permissions API で作る。
     */
    const operatorHolding = async (permissionName) => {
      let permissionIds = await fetchPermissionIds(
        adminToken,
        serverConfig.organizationId,
        serverConfig.tenantId
      );
      if (!permissionIds.has(permissionName)) {
        const created = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/permissions`,
          headers: { Authorization: `Bearer ${adminToken}` },
          body: { name: permissionName, description: permissionName },
        });
        expect(created.status).toBe(201);
        permissionIds = await fetchPermissionIds(
          adminToken,
          serverConfig.organizationId,
          serverConfig.tenantId
        );
      }
      const operator = await createOperatorWithPermission({
        managementToken: adminToken,
        organizationId: serverConfig.organizationId,
        tenantId: serverConfig.tenantId,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        permissionName,
        permissionId: permissionIds.get(permissionName),
        label: permissionName.replace(/[^a-z]/g, "-"),
      });
      operators.push(operator);
      return operator.accessToken;
    };

    it("lets an operator holding only idp:linked-account:read read, and not remove", async () => {
      const token = await operatorHolding("idp:linked-account:read");

      const list = await get({ url: orgUserBase(sub), headers: { Authorization: `Bearer ${token}` } });
      expect(list.status).toBe(200);
      expect(list.data.list.map((it) => it.alias)).toContain(alias);

      const removal = await deletion({
        url: `${orgUserBase(sub)}/${alias}`,
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(removal.status).toBe(403);
    });

    it("lets an operator holding only idp:linked-account:delete remove, and not read", async () => {
      const token = await operatorHolding("idp:linked-account:delete");

      const list = await get({ url: orgUserBase(sub), headers: { Authorization: `Bearer ${token}` } });
      expect(list.status).toBe(403);

      // dry run で権限だけを確かめる。本当に外すのは後のケース。
      const removal = await deletion({
        url: `${orgUserBase(sub)}/${alias}?dry_run=true`,
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(removal.status).toBe(200);
      expect(removal.data.dry_run).toBe(true);
    });

    it("is reachable at system level too", async () => {
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

      const response = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/users/${sub}/linked-external-accounts`,
        headers: { Authorization: `Bearer ${systemTokenResponse.data.access_token}` },
      });
      expect(response.status).toBe(200);
      expect(response.data.list.map((it) => it.alias)).toContain(alias);
    });

    it("removes a link on an administrator's behalf, revoking its tokens at the external IdP", async () => {
      const dryRun = await deletion({
        url: `${orgUserBase(sub)}/${alias}?dry_run=true`,
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      expect(dryRun.status).toBe(200);
      expect(dryRun.data.dry_run).toBe(true);
      expect(await isActiveAtExternalIdp(externalAccessToken)).toBe(true);

      const removal = await deletion({
        url: `${orgUserBase(sub)}/${alias}`,
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      expect(removal.status).toBe(204);

      const list = await get({
        url: orgUserBase(sub),
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      expect(list.data.list.map((it) => it.alias)).not.toContain(alias);
      expect(await isActiveAtExternalIdp(externalAccessToken)).toBe(false);

      const again = await deletion({
        url: `${orgUserBase(sub)}/${alias}`,
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      expect(again.status).toBe(404);
    });
  });

  /**
   * テナント全体の連携の管理 API。ユーザーを指定せずに連携先や期間で絞り込み、件数（total_count）
   * を返す。連携先を消す前や障害時に、その連携先の連携がいくつ残っているかを確かめるためのもの。
   */
  describe("tenant-wide management API", () => {

    const PROVIDER_FILTER = "account-linking-oauth2";
    const orgBase = `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/linked-external-accounts`;

    const isActiveAtExternalIdp = async (token) => {
      const params = new URLSearchParams();
      params.append("token", token);
      params.append("client_id", clientSecretPostClient.clientId);
      params.append("client_secret", clientSecretPostClient.clientSecret);
      const response = await post({
        url: `${backendUrl}/${federationServerConfig.tenantId}/v1/tokens/introspection`,
        body: params.toString(),
      });
      return response.data.active;
    };

    let adminToken;
    let sub;
    let aliases;
    let externalAccessToken;
    const operators = [];

    beforeAll(async () => {
      adminToken = await requestAdminAccessToken();

      const email = `account-linking-tenant-admin-${Date.now()}@example.com`;
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/users`,
        headers: { Authorization: `Bearer ${adminToken}` },
        body: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: email,
          email,
          raw_password: "successUserCode001",
        },
      });
      expect(createResponse.status).toBe(201);
      sub = createResponse.data.result.sub;

      const accessToken = await login(
        { username: email, password: "successUserCode001" },
        `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`
      );
      aliases = [
        await linkOnce(accessToken, PROVIDER_FILTER),
        await linkOnce(accessToken, PROVIDER_FILTER),
      ];
      const tokenResponse = await retrieveToken({
        alias: aliases[0],
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      externalAccessToken = tokenResponse.data.access_token;
    });

    afterAll(async () => {
      await deleteOperators(adminToken, serverConfig.organizationId, serverConfig.tenantId, operators);
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/users/${sub}`,
        headers: { Authorization: `Bearer ${adminToken}` },
      });
    });

    const listFor = (query, token = adminToken) =>
      get({ url: `${orgBase}?${new URLSearchParams(query)}`, headers: { Authorization: `Bearer ${token}` } });

    it("lists links across the tenant by provider and user, with whose they are, and never the tokens", async () => {
      const response = await listFor({ provider: PROVIDER_FILTER, user_id: sub });
      expect(response.status).toBe(200);
      expect(response.data.total_count).toBe(2);
      expect(response.data.list.map((it) => it.alias).sort()).toEqual([...aliases].sort());
      for (const account of response.data.list) {
        expect(account.id).toBeTruthy();
        expect(account.user_id).toBe(sub);
        expect(account.provider).toBe(PROVIDER_FILTER);
      }
      expect(JSON.stringify(response.data)).not.toContain(externalAccessToken);
    });

    it("counts a provider's links without returning them all", async () => {
      const response = await listFor({ provider: PROVIDER_FILTER, limit: "1" });
      expect(response.status).toBe(200);
      expect(response.data.list).toHaveLength(1);
      expect(response.data.total_count).toBeGreaterThanOrEqual(2);

      const none = await listFor({ provider: "no-such-provider" });
      expect(none.data.total_count).toBe(0);
      expect(none.data.list).toEqual([]);
    });

    it("answers 400 for a user_id that is not a UUID, and 404 for a link that does not exist", async () => {
      expect((await listFor({ user_id: "not-a-uuid" })).status).toBe(400);
      for (const id of [uuidv4(), "not-a-uuid"]) {
        const response = await get({ url: `${orgBase}/${id}`, headers: { Authorization: `Bearer ${adminToken}` } });
        expect(response.status).toBe(404);
      }
    });

    it("reads one link by its identifier", async () => {
      const id = (await listFor({ user_id: sub })).data.list.find((it) => it.alias === aliases[0]).id;
      const response = await get({ url: `${orgBase}/${id}`, headers: { Authorization: `Bearer ${adminToken}` } });
      expect(response.status).toBe(200);
      expect(response.data.alias).toBe(aliases[0]);
      expect(response.data.user_id).toBe(sub);
    });

    it("keeps reading and removing to idp:linked-account:read and idp:linked-account:delete", async () => {
      const permissionIds = await fetchPermissionIds(adminToken, serverConfig.organizationId, serverConfig.tenantId);
      const holding = async (permissionName) => {
        const operator = await createOperatorWithPermission({
          managementToken: adminToken,
          organizationId: serverConfig.organizationId,
          tenantId: serverConfig.tenantId,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
          permissionName,
          permissionId: permissionIds.get(permissionName),
          label: `tenant-${permissionName.replace(/[^a-z]/g, "-")}`,
        });
        operators.push(operator);
        return operator.accessToken;
      };
      const id = (await listFor({ user_id: sub })).data.list[0].id;

      const reader = await holding("idp:linked-account:read");
      expect((await listFor({ user_id: sub }, reader)).status).toBe(200);
      expect((await deletion({ url: `${orgBase}/${id}`, headers: { Authorization: `Bearer ${reader}` } })).status).toBe(403);

      const remover = await holding("idp:linked-account:delete");
      expect((await listFor({ user_id: sub }, remover)).status).toBe(403);
      const dryRun = await deletion({ url: `${orgBase}/${id}?dry_run=true`, headers: { Authorization: `Bearer ${remover}` } });
      expect(dryRun.status).toBe(200);
      expect(dryRun.data.dry_run).toBe(true);
    });

    it("is reachable at system level too", async () => {
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

      const response = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/linked-external-accounts?${new URLSearchParams({ user_id: sub })}`,
        headers: { Authorization: `Bearer ${systemTokenResponse.data.access_token}` },
      });
      expect(response.status).toBe(200);
      expect(response.data.total_count).toBe(2);
    });

    it("refuses to remove a provider that links still use, and never renames one", async () => {
      const configUrl = `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/federation-configurations/9e3b7d52-1c48-4a06-b7f9-2d5e8c0a4f13`;
      const current = await get({ url: configUrl, headers: { Authorization: `Bearer ${adminToken}` } });
      expect(current.status).toBe(200);
      expect(current.data.sso_provider).toBe(PROVIDER_FILTER);

      // dry_run でも同じ判定をする。実際に消すと他のテストが連携先を失う。
      const removal = await deletion({ url: `${configUrl}?dry_run=true`, headers: { Authorization: `Bearer ${adminToken}` } });
      expect(removal.status).toBe(409);
      expect(removal.data.linked_external_accounts).toBeGreaterThanOrEqual(2);

      const rename = await putWithJson({
        url: `${configUrl}?dry_run=true`,
        headers: { Authorization: `Bearer ${adminToken}` },
        body: { ...current.data, sso_provider: `${PROVIDER_FILTER}-renamed` },
      });
      // 連携は連携先を名前で引くので、連携の有無に関係なく名前と種類は変えられない（400）。
      expect(rename.status).toBe(400);

      const retyped = await putWithJson({
        url: `${configUrl}?dry_run=true`,
        headers: { Authorization: `Bearer ${adminToken}` },
        body: { ...current.data, type: "oidc" },
      });
      expect(retyped.status).toBe(400);

      // 使えない設定（別の連携先のコールバック）は、更新の時点で断る。
      const broken = await putWithJson({
        url: `${configUrl}?dry_run=true`,
        headers: { Authorization: `Bearer ${adminToken}` },
        body: {
          ...current.data,
          payload: { ...current.data.payload, redirect_uri: `${backendUrl}/${serverConfig.tenantId}/v1/linking/callback/other` },
        },
      });
      expect(broken.status).toBe(400);
      expect(JSON.stringify(broken.data.error_messages)).toContain("redirect_uri");

      // 名前と種類を変えない更新は通る。
      const unchanged = await putWithJson({
        url: `${configUrl}?dry_run=true`,
        headers: { Authorization: `Bearer ${adminToken}` },
        body: current.data,
      });
      expect(unchanged.status).toBe(200);
    });

    it("removes a link by its identifier, revoking its tokens at the external IdP", async () => {
      const id = (await listFor({ user_id: sub })).data.list.find((it) => it.alias === aliases[0]).id;
      expect(await isActiveAtExternalIdp(externalAccessToken)).toBe(true);

      const removal = await deletion({ url: `${orgBase}/${id}`, headers: { Authorization: `Bearer ${adminToken}` } });
      expect(removal.status).toBe(204);
      expect(await isActiveAtExternalIdp(externalAccessToken)).toBe(false);

      const after = await listFor({ user_id: sub });
      expect(after.data.total_count).toBe(1);
      expect((await deletion({ url: `${orgBase}/${id}`, headers: { Authorization: `Bearer ${adminToken}` } })).status).toBe(404);
    });
  });

  describe("linking CSRF", () => {

    it("/linking/start rejects an operator other than the user bound at link", async () => {
      // 攻撃者が自分の Bearer で連携を開始し、その start_url を被害者に踏ませる想定。
      // ここを素通しすると、被害者の外部アカウントが攻撃者のレコードに入る。
      const attackerAccessToken = await login(ownerUser);
      const linkResponse = await startLink(attackerAccessToken);
      expect(linkResponse.status).toBe(201);

      // 被害者のブラウザ（同じ cookie jar の OP セッションを別ユーザで上書きする）
      await login(otherUser);

      const startResponse = await get({ url: linkResponse.data.start_url });
      // 被害者を RP へ戻すと、攻撃者の連携を被害者側のセッションに渡すことになる。エラー画面へ送る。
      expect(startResponse.status).toBe(302);
      expectErrorPage(startResponse.headers.location, "access_denied");
    });

    it("does not redeem a code that comes back to another provider's callback", async () => {
      // 連携先ごとにコールバックは別。始めた連携先と違うコールバックに届いた応答は取り違えなので、
      // code を交換せずにエラー画面へ送る。
      const accessToken = await login(ownerUser);
      const linkResponse = await startLink(accessToken);
      expect(linkResponse.status).toBe(201);
      const startResponse = await get({ url: linkResponse.data.start_url });
      expect(startResponse.status).toBe(302);
      const callbackUri = await consentAtExternalIdp(startResponse.headers.location);
      expect(callbackUri).toContain(`/v1/linking/callback/${PROVIDER}?`);

      const mixedUp = await get({
        url: callbackUri.replace(`/v1/linking/callback/${PROVIDER}?`, "/v1/linking/callback/account-linking-oauth2?"),
      });
      expect(mixedUp.status).toBe(302);
      expectErrorPage(mixedUp.headers.location, "invalid_request");
    });
  });

  /**
   * ブラウザに OP セッションが無いとき（ネイティブアプリの ephemeral なブラウザなど）、/linking/start は
   * RP へ戻さず、連携を始めた本人としてのログインへ送る。ログインは普段の認可フロー（始めたクライアントの
   * oauth のポリシー）を通り、最後にコードを出さずに /linking/start へ戻る。
   */
  describe("sign-in again at /linking/start", () => {
    const authorizationsBase = `${backendUrl}/${serverConfig.tenantId}/v1/authorizations`;

    const ownerSub = async (accessToken) => {
      const userinfo = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/userinfo`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(userinfo.status).toBe(200);
      return userinfo.data.sub;
    };

    /** セッションの無いブラウザで start_url を開き、送られた認可リクエストを返す。 */
    const signInRequestFor = async (startUrl) => {
      const response = await axios.get(startUrl, { maxRedirects: 0, validateStatus: () => true });
      expect(response.status).toBe(302);
      const location = new URL(response.headers.location);
      expect(`${location.origin}${location.pathname}`).toBe(authorizationsBase);
      return location;
    };

    /** 送られた認可リクエストを開いて、認証画面の認可リクエスト ID を得る。 */
    const openSignIn = async (location) => {
      const response = await get({ url: location.toString() });
      expect(response.status).toBe(302);
      return convertNextAction(response.headers.location).params.get("id");
    };

    it("sends a browser without a session to sign in as the user who started the link", async () => {
      const accessToken = await login(ownerUser);
      const linkResponse = await startLink(accessToken);
      expect(linkResponse.status).toBe(201);

      const location = await signInRequestFor(linkResponse.data.start_url);
      const params = location.searchParams;
      expect(params.get("client_id")).toBe(clientSecretPostClient.clientId);
      expect(params.get("prompt")).toBe("login");
      expect(params.get("login_hint")).toBe(`sub:${await ownerSub(accessToken)}`);
      expect(params.get("idp_linking_state")).toBe(linkResponse.data.state);
    });

    it("issues no code: the sign-in goes back to /linking/start, and the link goes on to the external IdP", async () => {
      const accessToken = await login(ownerUser);
      const linkResponse = await startLink(accessToken);
      const id = await openSignIn(await signInRequestFor(linkResponse.data.start_url));

      const signedIn = await postAuthentication({
        endpoint: `${authorizationsBase}/{id}/password-authentication`,
        id,
        body: ownerUser,
      });
      expect(signedIn.status).toBe(200);

      const authorized = await authorize({ endpoint: `${authorizationsBase}/{id}/authorize`, id, body: {} });
      expect(authorized.status).toBe(200);
      const resume = new URL(authorized.data.redirect_uri);
      expect(resume.pathname).toBe(`/${serverConfig.tenantId}/v1/linking/start`);
      expect(resume.searchParams.get("state")).toBe(linkResponse.data.state);
      expect(resume.searchParams.get("code")).toBeNull();

      const startResponse = await get({ url: resume.toString() });
      expect(startResponse.status).toBe(302);
      expect(startResponse.headers.location).toContain(federationServerConfig.tenantId);
    });

    // 連携に普段より強いログインを求める設定（linking_acr_values）。ログインが新しくても acr が当たらなければ
    // ログインし直しへ送り、その認可リクエストに acr_values を載せて、acr に当てた認証ポリシーを効かせる。
    it("sends a recent sign-in that did not reach the client's linking_acr_values to sign in again with acr_values", async () => {
      const clientUrl = `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/clients/${clientSecretPostClient.clientId}`;
      const adminHeaders = { Authorization: `Bearer ${await requestAdminAccessToken()}` };
      const current = await get({ url: clientUrl, headers: adminHeaders });
      expect(current.status).toBe(200);
      const { created_at, updated_at, ...clientBody } = current.data;
      const strongAcr = "urn:e2e:linking:strong";

      const updated = await putWithJson({
        url: clientUrl,
        headers: adminHeaders,
        body: { ...clientBody, extension: { ...clientBody.extension, linking_acr_values: [strongAcr] } },
      });
      expect(updated.status).toBe(200);
      try {
        const accessToken = await login(ownerUser);
        const linkResponse = await startLink(accessToken);
        expect(linkResponse.status).toBe(201);

        // ログインは今したばかりでも、acr が linking_acr_values に当たらないので外部IdP へは進まない。
        const startResponse = await get({ url: linkResponse.data.start_url });
        expect(startResponse.status).toBe(302);
        const location = new URL(startResponse.headers.location);
        expect(`${location.origin}${location.pathname}`).toBe(authorizationsBase);
        expect(location.searchParams.get("acr_values")).toBe(strongAcr);
        expect(location.searchParams.get("prompt")).toBe("login");
        expect(location.searchParams.get("idp_linking_state")).toBe(linkResponse.data.state);
      } finally {
        const restored = await putWithJson({ url: clientUrl, headers: adminHeaders, body: clientBody });
        expect(restored.status).toBe(200);
      }
    });

    it("does not let anyone else sign in for the link", async () => {
      const accessToken = await login(ownerUser);
      const linkResponse = await startLink(accessToken);
      const id = await openSignIn(await signInRequestFor(linkResponse.data.start_url));

      const someoneElse = await postAuthentication({
        endpoint: `${authorizationsBase}/{id}/password-authentication`,
        id,
        body: otherUser,
      });
      expect(someoneElse.status).toBe(400);
    });

    it("hands a cancelled sign-in back to the link rather than to the client", async () => {
      const accessToken = await login(ownerUser);
      const linkResponse = await startLink(accessToken);
      const id = await openSignIn(await signInRequestFor(linkResponse.data.start_url));

      const denied = await deny({ endpoint: `${authorizationsBase}/{id}/deny`, id });
      expect(denied.status).toBe(200);
      const resume = new URL(denied.data.redirect_uri);
      expect(resume.pathname).toBe(`/${serverConfig.tenantId}/v1/linking/start`);
      expect(resume.searchParams.get("returned_from_sign_in")).toBe("true");
      expect(resume.searchParams.get("error")).toBeNull();

      // 戻ったブラウザはもう一度ログインへは送られず、RP に login_required が返る。
      const back = await getWithoutCookies(resume.toString());
      expect(back.status).toBe(302);
      expectReturnedFailure(back.headers.location, "login_required", null);
    });
  });

  describe("provider scope", () => {
    it("requires openid of a provider configured for it, so the external account is identified", async () => {
      const accessToken = await login(ownerUser);
      const withoutOpenid = await startLink(accessToken, PROVIDER, "profile email offline_access");
      expect(withoutOpenid.status).toBe(400);
      expect(withoutOpenid.data.error).toBe("invalid_request");

      const withOpenid = await startLink(accessToken, PROVIDER, "openid profile");
      expect(withOpenid.status).toBe(201);
    });

    it("asks the provider for its configured scope when the scope is blank", async () => {
      // 空の scope は省略と同じ。そのまま外部IdP に送ると openid が付かず、外部アカウントを識別できない。
      const accessToken = await login(ownerUser);
      const blank = await startLink(accessToken, PROVIDER, "   ");
      expect(blank.status).toBe(201);
      const startResponse = await get({ url: blank.data.start_url });
      expect(startResponse.status).toBe(302);
      const scope = new URL(startResponse.headers.location).searchParams.get("scope");
      expect(scope.split(" ")).toContain("openid");
    });
  });

  describe("failure returns", () => {

    const callbackUrl = (params) =>
      `${backendUrl}/${serverConfig.tenantId}/v1/linking/callback/${PROVIDER}?${new URLSearchParams(params).toString()}`;

    /** 連携を開始して /linking/start を通し、束縛 Cookie を cookie jar に載せる。 */
    const startedLink = async () => {
      const accessToken = await login(ownerUser);
      const linkResponse = await startLink(accessToken);
      expect(linkResponse.status).toBe(201);
      const startResponse = await get({ url: linkResponse.data.start_url });
      expect(startResponse.status).toBe(302);
      return { accessToken, state: linkResponse.data.state };
    };

    it("returns a denial at the external IdP to the client, with the state", async () => {
      const { accessToken, state } = await startedLink();

      const response = await get({
        url: callbackUrl({ error: "access_denied", error_description: "user declined", state }),
      });
      expect(response.status).toBe(302);
      expectReturnedFailure(response.headers.location, "access_denied", state);

      // 外部IdPはこの認可リクエストに二度と答えないので、連携セッションは消える。
      const completeResponse = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/complete`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { state },
      });
      expect(completeResponse.status).toBe(404);
    });

    it("does not pass the external IdP's error code through", async () => {
      const { state } = await startedLink();

      const response = await get({
        url: callbackUrl({ error: "invalid_scope<script>", state }),
      });
      expect(response.status).toBe(302);
      expectReturnedFailure(response.headers.location, "server_error", state);
    });

    it("returns a failed token exchange to the client, with the state", async () => {
      const { state } = await startedLink();

      const response = await get({ url: callbackUrl({ code: "not-a-code", state }) });
      expect(response.status).toBe(302);
      expectReturnedFailure(response.headers.location, "server_error", state);
    });

    it("sends a browser without the binding to the error page, and leaves the link to its owner", async () => {
      const { state } = await startedLink();

      // 束縛 Cookie を持たないブラウザ。他人の state で拒否を送っても、その人の連携は終わらない。
      const foreign = await getWithoutCookies(callbackUrl({ error: "access_denied", state }));
      expect(foreign.status).toBe(302);
      expectErrorPage(foreign.headers.location, "access_denied");
      expect(foreign.headers.location.startsWith(RETURN_TO)).toBe(false);

      // 連携を始めたブラウザからは、まだ続きが通る。
      const owner = await get({ url: callbackUrl({ error: "access_denied", state }) });
      expect(owner.status).toBe(302);
      expectReturnedFailure(owner.headers.location, "access_denied", state);
    });

    it("returns a callback carrying neither a code nor an error to the client", async () => {
      const { state } = await startedLink();

      const response = await get({ url: callbackUrl({ state }) });
      expect(response.status).toBe(302);
      expectReturnedFailure(response.headers.location, "server_error", state);
    });

    it("tells a browser reopening a finished callback that the link has moved on", async () => {
      const accessToken = await login(ownerUser);
      const linkResponse = await startLink(accessToken, "account-linking-oauth2", "account offline_access");
      const startResponse = await get({ url: linkResponse.data.start_url });
      const callbackUri = await consentAtExternalIdp(startResponse.headers.location);

      const first = await get({ url: callbackUri });
      expect(first.status).toBe(302);
      expect(first.headers.location).toContain("linking=done");

      // 再読み込みや戻るボタン。束縛 Cookie は使い終わって消えているが、
      // 「別のブラウザで始めた連携」ではないので、そうは言わない。
      const again = await get({ url: callbackUri });
      expect(again.status).toBe(302);
      expectErrorPage(again.headers.location, "invalid_request");
    });

    it("sends an unknown state to the error page", async () => {
      const response = await get({
        url: callbackUrl({ error: "access_denied", state: "unknown-state" }),
      });
      expect(response.status).toBe(302);
      expectErrorPage(response.headers.location, "invalid_request");
    });

    it("returns login_required, without the state, when the browser comes back from the sign-in still without a session", async () => {
      const accessToken = await login(ownerUser);
      const linkResponse = await startLink(accessToken);
      expect(linkResponse.status).toBe(201);

      // 戻ってきてもセッションが無いなら、もう一度ログインへは送らない（セッションを書けない環境でループしない）。
      const response = await getWithoutCookies(`${linkResponse.data.start_url}&returned_from_sign_in=true`);
      expect(response.status).toBe(302);
      expectReturnedFailure(response.headers.location, "login_required", null);

      // 操作者を確かめられないので連携は触らない。サインイン済みのブラウザからは続けられる。
      const startResponse = await get({ url: linkResponse.data.start_url });
      expect(startResponse.status).toBe(302);
      expect(startResponse.headers.location).toContain(federationServerConfig.tenantId);
    });
  });

  /**
   * 外部トークンはテナントごとのデータ鍵（DEK）で暗号化し、DEK は KEK で包んで保存する。
   * KEK の交換はサーバーの再起動を伴うのでここでは扱わず、運用 API の振る舞いと、
   * DEK の新しい世代を作っても既存の連携が読めることを確かめる。
   */
  describe("data keys", () => {

    const adminHeaders = {
      Authorization: `Basic ${Buffer.from(
        `${process.env.IDP_SERVER_API_KEY}:${process.env.IDP_SERVER_API_SECRET}`
      ).toString("base64")}`,
    };

    it("reports which key encryption keys wrap the data keys", async () => {
      const response = await get({
        url: `${backendUrl}/v1/admin/operations/data-keys`,
        headers: adminHeaders,
      });
      expect(response.status).toBe(200);
      expect(response.data.active_key_id).toBeTruthy();
      expect(response.data.configured_key_ids).toContain(response.data.active_key_id);
      expect(response.data.unopenable).toBe(0);
      expect(JSON.stringify(response.data)).not.toContain("wrapped_key");
    });

    it("refuses the operations without the administrator credentials", async () => {
      const response = await get({ url: `${backendUrl}/v1/admin/operations/data-keys` });
      expect(response.status).toBe(401);
    });

    it("keeps an existing link readable across a new data key generation", async () => {
      const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);
      const alias = await linkOnce(accessToken, "account-linking-oauth2");
      const retrieve = () =>
        retrieveToken({
          alias,
          token: accessToken,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
        });

      const before = await retrieve();
      expect(before.status).toBe(200);

      const rotateResponse = await postWithJson({
        url: `${backendUrl}/v1/admin/operations/data-keys/rotate`,
        headers: adminHeaders,
        body: { tenant_id: serverConfig.tenantId },
      });
      expect(rotateResponse.status).toBe(200);
      expect(rotateResponse.data.purpose).toBe("account-linking-token");
      expect(rotateResponse.data.data_key_id).toBeTruthy();

      // 古い世代の DEK は開くためだけに残る。取り出しは同じトークンを返し、行は新しい世代へ移る。
      const after = await retrieve();
      expect(after.status).toBe(200);
      expect(after.data.access_token).toBe(before.data.access_token);
      const again = await retrieve();
      expect(again.status).toBe(200);
      expect(again.data.access_token).toBe(before.data.access_token);
    });

    it("refuses a rotation without a tenant or for an unknown purpose", async () => {
      const withoutTenant = await postWithJson({
        url: `${backendUrl}/v1/admin/operations/data-keys/rotate`,
        headers: adminHeaders,
        body: {},
      });
      expect(withoutTenant.status).toBe(400);

      const unknownPurpose = await postWithJson({
        url: `${backendUrl}/v1/admin/operations/data-keys/rotate`,
        headers: adminHeaders,
        body: { tenant_id: serverConfig.tenantId, purpose: "oauth-token" },
      });
      expect(unknownPurpose.status).toBe(400);
    });

    const activeKeyId = async () =>
      (await get({ url: `${backendUrl}/v1/admin/operations/data-keys`, headers: adminHeaders })).data
        .active_key_id;
    const rewrap = (body) =>
      postWithJson({
        url: `${backendUrl}/v1/admin/operations/data-keys/rewrap`,
        headers: adminHeaders,
        body,
      });

    it("has nothing to re-wrap while every data key is under the active KEK", async () => {
      const response = await rewrap({ max_number: 100, expected_active_key_id: await activeKeyId() });
      expect(response.status).toBe(200);
      expect(response.data.rewrapped).toBe(0);
      expect(response.data.pending_rewrap).toBe(0);
    });

    it("refuses to re-wrap unless the caller names the KEK this server encrypts with", async () => {
      const withoutExpectation = await rewrap({ max_number: 100 });
      expect(withoutExpectation.status).toBe(400);

      // A server still on another configuration during a rolling update must not re-wrap.
      const mismatch = await rewrap({ expected_active_key_id: "some-other-kek" });
      expect(mismatch.status).toBe(409);
      expect(mismatch.data.active_key_id).toBe(await activeKeyId());
      expect(mismatch.data.expected_active_key_id).toBe("some-other-kek");
    });

    it("answers 400, not 500, for an out-of-range or non-numeric batch size", async () => {
      const expected_active_key_id = await activeKeyId();
      for (const max_number of [0, -1, 10001, "abc", 1.5]) {
        const response = await rewrap({ max_number, expected_active_key_id });
        expect(response.status).toBe(400);
      }
      for (const max_deletion_number of [0, -1, 100001, "abc"]) {
        const response = await postWithJson({
          url: `${backendUrl}/v1/admin/operations/delete-expired-data`,
          headers: adminHeaders,
          body: { max_deletion_number },
        });
        expect(response.status).toBe(400);
      }
    });

    it("answers 404 for a tenant that does not exist and 400 for one that is not a UUID", async () => {
      const unknown = await postWithJson({
        url: `${backendUrl}/v1/admin/operations/data-keys/rotate`,
        headers: adminHeaders,
        body: { tenant_id: uuidv4() },
      });
      expect(unknown.status).toBe(404);

      const malformed = await postWithJson({
        url: `${backendUrl}/v1/admin/operations/data-keys/rotate`,
        headers: adminHeaders,
        body: { tenant_id: "not-a-uuid" },
      });
      expect(malformed.status).toBe(400);
    });
  });

  /**
   * テナントのデータ鍵（DEK）の管理 API。組織・システムの管理 API で、テナントの DEK を一覧し、
   * 新しい世代を作る。運用 API の一覧はテナントをまたいで引き、KEK で絞り込める。
   * 鍵そのもの（wrapped_key）はどこからも返さない。
   */
  describe("data key management API", () => {
    const orgDataKeys = `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/data-keys`;
    const opsHeaders = {
      Authorization: `Basic ${Buffer.from(
        `${process.env.IDP_SERVER_API_KEY}:${process.env.IDP_SERVER_API_SECRET}`
      ).toString("base64")}`,
    };
    let adminToken;
    const operators = [];

    beforeAll(async () => {
      adminToken = await requestAdminAccessToken();
      // 連携が 1 件はある状態にする。DEK は最初の連携で作られる。
      const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE}`);
      await linkOnce(accessToken, "account-linking-oauth2");
    });

    // 管理者トークンの有効期間は 120 秒しかない。
    beforeEach(async () => {
      adminToken = await requestAdminAccessToken();
    });

    afterAll(async () => {
      await deleteOperators(
        await requestAdminAccessToken(),
        serverConfig.organizationId,
        serverConfig.tenantId,
        operators
      );
    });

    const listKeys = async (query = "") =>
      await get({ url: `${orgDataKeys}${query}`, headers: { Authorization: `Bearer ${adminToken}` } });

    const linkingKeys = (list) => list.filter((it) => it.purpose === "account-linking-token");

    /** 権限レコードが無ければ作ってから、その権限だけを持つ管理者を作る。 */
    const operatorHolding = async (permissionName) => {
      let permissionIds = await fetchPermissionIds(adminToken, serverConfig.organizationId, serverConfig.tenantId);
      if (!permissionIds.has(permissionName)) {
        const created = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/permissions`,
          headers: { Authorization: `Bearer ${adminToken}` },
          body: { name: permissionName, description: permissionName },
        });
        expect(created.status).toBe(201);
        permissionIds = await fetchPermissionIds(adminToken, serverConfig.organizationId, serverConfig.tenantId);
      }
      const operator = await createOperatorWithPermission({
        managementToken: adminToken,
        organizationId: serverConfig.organizationId,
        tenantId: serverConfig.tenantId,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        permissionName,
        permissionId: permissionIds.get(permissionName),
        label: permissionName.replace(/[^a-z]/g, "-"),
      });
      operators.push(operator);
      return operator.accessToken;
    };

    it("lists the tenant's data keys with how many links each still encrypts, never the key", async () => {
      const response = await listKeys();
      expect(response.status).toBe(200);
      const keys = linkingKeys(response.data.list);
      expect(keys.length).toBeGreaterThanOrEqual(1);
      expect(keys.filter((it) => it.status === "active")).toHaveLength(1);
      expect(response.data.total_count).toBe(response.data.list.length);
      for (const key of keys) {
        expect(key.tenant_id).toBe(serverConfig.tenantId);
        expect(key.kek_id).toBeTruthy();
        expect(typeof key.linked_external_accounts).toBe("number");
      }
      expect(keys.reduce((sum, it) => sum + it.linked_external_accounts, 0)).toBeGreaterThanOrEqual(1);
      expect(JSON.stringify(response.data)).not.toContain("wrapped_key");
    });

    it("narrows the list by status and refuses an unknown one", async () => {
      const active = await listKeys("?status=active&purpose=account-linking-token");
      expect(active.status).toBe(200);
      expect(active.data.list).toHaveLength(1);
      expect(active.data.list[0].status).toBe("active");

      const unknown = await listKeys("?status=retired");
      expect(unknown.status).toBe(400);
    });

    it("starts a new generation: the new key is active and the previous one only opens", async () => {
      const before = linkingKeys((await listKeys()).data.list);
      const previous = before.find((it) => it.status === "active");

      const dryRun = await postWithJson({
        url: `${orgDataKeys}/rotate?dry_run=true`,
        headers: { Authorization: `Bearer ${adminToken}` },
        body: { purpose: "account-linking-token" },
      });
      expect(dryRun.status).toBe(200);
      expect(dryRun.data.dry_run).toBe(true);
      expect(linkingKeys((await listKeys()).data.list)).toHaveLength(before.length);

      const rotated = await postWithJson({
        url: `${orgDataKeys}/rotate`,
        headers: { Authorization: `Bearer ${adminToken}` },
        body: { purpose: "account-linking-token" },
      });
      expect(rotated.status).toBe(200);
      expect(rotated.data.data_key_id).toBeTruthy();

      const after = linkingKeys((await listKeys()).data.list);
      expect(after).toHaveLength(before.length + 1);
      const current = after.find((it) => it.status === "active");
      expect(current.id).toBe(rotated.data.data_key_id);
      expect(current.version).toBe(Math.max(...before.map((it) => it.version)) + 1);
      expect(after.find((it) => it.id === previous.id).status).toBe("passive");
      // 一括で暗号化し直さないので、連携の数の合計は変わらない。
      const total = (keys) => keys.reduce((sum, it) => sum + it.linked_external_accounts, 0);
      expect(total(after)).toBe(total(before));
    });

    it("refuses to rotate for an unknown purpose", async () => {
      const response = await postWithJson({
        url: `${orgDataKeys}/rotate`,
        headers: { Authorization: `Bearer ${adminToken}` },
        body: { purpose: "oauth-token" },
      });
      expect(response.status).toBe(400);
    });

    it("lets an operator holding only idp:data-key:read list, and not rotate", async () => {
      const token = await operatorHolding("idp:data-key:read");
      const list = await get({ url: orgDataKeys, headers: { Authorization: `Bearer ${token}` } });
      expect(list.status).toBe(200);

      const rotation = await postWithJson({
        url: `${orgDataKeys}/rotate`,
        headers: { Authorization: `Bearer ${token}` },
        body: {},
      });
      expect(rotation.status).toBe(403);
    });

    it("refuses the list to an operator without idp:data-key:read", async () => {
      const token = await operatorHolding("idp:linked-account:read");
      const list = await get({ url: orgDataKeys, headers: { Authorization: `Bearer ${token}` } });
      expect(list.status).toBe(403);
    });

    it("answers the same list through the system-level management API", async () => {
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
      const response = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/data-keys`,
        headers: { Authorization: `Bearer ${systemToken.data.access_token}` },
      });
      expect(response.status).toBe(200);
      expect(response.data.list.map((it) => it.id).sort()).toEqual(
        (await listKeys()).data.list.map((it) => it.id).sort()
      );
    });

    it("lists data keys across tenants for the operator, narrowed by tenant or KEK", async () => {
      const ops = (query) =>
        get({ url: `${backendUrl}/v1/admin/operations/data-keys/list${query}`, headers: opsHeaders });

      const byTenant = await ops(`?tenant_id=${serverConfig.tenantId}&limit=100`);
      expect(byTenant.status).toBe(200);
      expect(byTenant.data.total_count).toBe((await listKeys()).data.list.length);
      for (const key of byTenant.data.list) {
        expect(key.tenant_id).toBe(serverConfig.tenantId);
        expect(key.kek_configured).toBe(true);
      }
      expect(JSON.stringify(byTenant.data)).not.toContain("wrapped_key");

      const byKek = await ops(`?kek_id=${byTenant.data.list[0].kek_id}&limit=1`);
      expect(byKek.status).toBe(200);
      expect(byKek.data.list).toHaveLength(1);
      expect(byKek.data.total_count).toBeGreaterThanOrEqual(byTenant.data.total_count);

      const unknownKek = await ops("?kek_id=no-such-kek");
      expect(unknownKek.status).toBe(200);
      expect(unknownKek.data.total_count).toBe(0);

      expect((await ops("?tenant_id=not-a-uuid")).status).toBe(400);
      expect((await ops("?status=retired")).status).toBe(400);
      expect(
        (await get({ url: `${backendUrl}/v1/admin/operations/data-keys/list` })).status
      ).toBe(401);
    });
  });

  /**
   * テナントの削除。連携の行は tenant への FK の CASCADE で消えるので、その前に外部IdP で失効させる。
   * 消してよいテナントを onboarding で作り、そこで連携してから消す。
   */
  describe("tenant deletion", () => {
    const LINKING_PROVIDER = "account-linking-oauth2";
    // 外部IdP役テナントの clientSecretPost。連携の callback をこの client の redirect_uris に足す。
    const EXTERNAL_CLIENT_ID = "5b5fa267-9b15-4efd-8762-60f42398ab8b";

    let systemAccessToken;
    let organizationId;
    let tenantId;
    let externalClientUrl;
    let externalClientBefore;

    const requestSystemAccessToken = async () => {
      const response = await requestToken({
        endpoint: adminServerConfig.tokenEndpoint,
        grantType: "password",
        username: adminServerConfig.oauth.username,
        password: adminServerConfig.oauth.password,
        scope: adminServerConfig.adminClient.scope,
        clientId: adminServerConfig.adminClient.clientId,
        clientSecret: adminServerConfig.adminClient.clientSecret,
      });
      expect(response.status).toBe(200);
      return response.data.access_token;
    };

    /** 連携できる最小のテナントを作る。管理者ユーザーが、そのまま連携するユーザーも兼ねる。 */
    const createLinkingTenant = async () => {
      const timestamp = Date.now();
      organizationId = uuidv4();
      tenantId = uuidv4();
      const clientId = uuidv4();
      const clientSecret = `client-secret-${uuidv4()}`;
      const user = { username: `linking-${timestamp}@example.com`, password: `LinkingPass_${timestamp}!` };
      const callbackUri = `${backendUrl}/${tenantId}/v1/linking/callback/${LINKING_PROVIDER}`;
      const scope = `openid profile email management org-management ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`;

      const onboardingResponse = await postWithJson({
        url: `${backendUrl}/v1/management/onboarding`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
        body: {
          organization: { id: organizationId, name: `Linking tenant deletion ${timestamp}`, description: "E2E for #1531" },
          tenant: {
            id: tenantId,
            name: `Linking tenant deletion ${timestamp}`,
            domain: backendUrl,
            authorization_provider: "idp-server",
          },
          authorization_server: {
            issuer: `${backendUrl}/${tenantId}`,
            authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
            token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
            userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
            jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
            jwks: await generateECP256JWKS(),
            scopes_supported: scope.split(" "),
            response_types_supported: ["code"],
            response_modes_supported: ["query"],
            subject_types_supported: ["public"],
            grant_types_supported: ["authorization_code", "password"],
            id_token_signing_alg_values_supported: ["ES256"],
            token_endpoint_auth_methods_supported: ["client_secret_post"],
            claims_supported: ["sub", "name", "email", "email_verified"],
          },
          user: {
            sub: uuidv4(),
            provider_id: "idp-server",
            name: user.username,
            email: user.username,
            email_verified: true,
            raw_password: user.password,
          },
          client: {
            client_id: clientId,
            client_secret: clientSecret,
            redirect_uris: [clientSecretPostClient.redirectUri],
            response_types: ["code"],
            grant_types: ["authorization_code", "password"],
            scope,
            client_name: "Linking tenant deletion client",
            token_endpoint_auth_method: "client_secret_post",
            application_type: "web",
            extension: {
              linking_return_uris: [RETURN_TO],
              linking_providers: [LINKING_PROVIDER],
              linking_token_providers: [LINKING_PROVIDER],
            },
          },
        },
      });
      expect(onboardingResponse.status).toBe(201);

      const mgmtTokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: user.username,
        password: user.password,
        scope: "management org-management",
        clientId,
        clientSecret,
      });
      expect(mgmtTokenResponse.status).toBe(200);
      const mgmtAccessToken = mgmtTokenResponse.data.access_token;
      const tenantApi = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;
      const headers = { Authorization: `Bearer ${mgmtAccessToken}` };

      const authConfigResponse = await postWithJson({
        url: `${tenantApi}/authentication-configurations`,
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
      expect(authConfigResponse.status).toBe(201);

      const policyResponse = await postWithJson({
        url: `${tenantApi}/authentication-policies`,
        headers,
        body: {
          flow: "oauth",
          enabled: true,
          policies: [
            {
              description: "password",
              priority: 1,
              conditions: {},
              available_methods: ["password"],
              success_conditions: {
                any_of: [
                  [{ path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
                ],
              },
            },
          ],
        },
      });
      expect(policyResponse.status).toBe(201);

      // test-tenant の account-linking-oauth2 と同じ連携先を、このテナントの callback で登録する。
      const federationResponse = await postWithJson({
        url: `${tenantApi}/federation-configurations`,
        headers,
        body: {
          id: uuidv4(),
          type: "account-linking",
          sso_provider: LINKING_PROVIDER,
          enabled: true,
          payload: {
            type: "standard",
            provider: "standard",
            issuer: federationServerConfig.issuer,
            issuer_name: LINKING_PROVIDER,
            authorization_endpoint: `${federationServerConfig.issuer}/v1/authorizations`,
            token_endpoint: `${federationServerConfig.issuer}/v1/tokens`,
            revocation_endpoint: `${federationServerConfig.issuer}/v1/tokens/revocation`,
            client_id: clientSecretPostClient.clientId,
            client_secret: clientSecretPostClient.clientSecret,
            client_authentication_type: "client_secret_post",
            redirect_uri: callbackUri,
            scopes_supported: ["account", "offline_access"],
            response_types_supported: ["code"],
            code_challenge_methods_supported: ["S256"],
            grant_types_supported: ["authorization_code", "refresh_token"],
            duplicate_link_policy: "reject",
            refresh_token_expires_in: 86400,
          },
        },
      });
      expect(federationResponse.status).toBe(201);

      return { user, clientId, clientSecret, scope, callbackUri, tenantApi, headers };
    };

    beforeAll(async () => {
      systemAccessToken = await requestSystemAccessToken();
    });

    afterAll(async () => {
      if (externalClientBefore) {
        await putWithJson({
          url: externalClientUrl,
          headers: { Authorization: `Bearer ${await requestAdminAccessToken()}` },
          body: externalClientBefore,
        });
      }
      if (organizationId) {
        await deletion({
          url: `${backendUrl}/v1/management/orgs/${organizationId}`,
          headers: { Authorization: `Bearer ${await requestSystemAccessToken()}` },
        }).catch(() => {});
      }
    });

    it("revokes the linked accounts' tokens at the external IdP before the tenant goes", async () => {
      const tenant = await createLinkingTenant();

      // 外部IdP役の client に、このテナントの callback を一時的に足す。afterAll で元に戻す。
      externalClientUrl = `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${federationServerConfig.tenantId}/clients/${EXTERNAL_CLIENT_ID}`;
      const externalHeaders = { Authorization: `Bearer ${await requestAdminAccessToken()}` };
      const externalClient = await get({ url: externalClientUrl, headers: externalHeaders });
      expect(externalClient.status).toBe(200);
      const { created_at, updated_at, ...externalClientBody } = externalClient.data;
      externalClientBefore = externalClientBody;
      const addCallback = await putWithJson({
        url: externalClientUrl,
        headers: externalHeaders,
        body: { ...externalClientBody, redirect_uris: [...externalClientBody.redirect_uris, tenant.callbackUri] },
      });
      expect(addCallback.status).toBe(200);

      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        clientId: tenant.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        state: "tenant-deletion",
        scope: `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`,
        user: tenant.user,
        interaction: async (id, user) => {
          const response = await postAuthentication({
            endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
            id,
            body: user,
          });
          expect(response.status).toBe(200);
        },
      });
      expect(status).toBe(200);
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: tenant.clientId,
        clientSecret: tenant.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;
      const bearer = { Authorization: `Bearer ${accessToken}` };

      const linkResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/me/linked-external-accounts/link/${LINKING_PROVIDER}`,
        headers: bearer,
        body: { redirect_uri: RETURN_TO, scope: "account offline_access" },
      });
      expect(linkResponse.status).toBe(201);
      const startResponse = await get({ url: linkResponse.data.start_url });
      expect(startResponse.status).toBe(302);
      const callbackResponse = await get({ url: await consentAtExternalIdp(startResponse.headers.location) });
      expect(callbackResponse.status).toBe(302);
      const completeResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/me/linked-external-accounts/complete`,
        headers: bearer,
        body: { state: linkResponse.data.state },
      });
      expect(completeResponse.status).toBe(201);

      const params = new URLSearchParams();
      params.append("token", accessToken);
      params.append("client_id", tenant.clientId);
      params.append("client_secret", tenant.clientSecret);
      const storedToken = await post({
        url: `${backendUrl}/${tenantId}/v1/linked-external-accounts/${completeResponse.data.alias}/token`,
        body: params.toString(),
      });
      expect(storedToken.status).toBe(200);
      const externalAccessToken = storedToken.data.access_token;
      expect(await isActiveAtExternalIdp(externalAccessToken)).toBe(true);

      const deleteResponse = await deletion({ url: tenant.tenantApi, headers: tenant.headers });
      expect(deleteResponse.status).toBe(204);

      // ユーザー削除と違い、失効はテナントの削除の中で同期的に終わっている。
      expect(await isActiveAtExternalIdp(externalAccessToken)).toBe(false);
    });
  });
});
