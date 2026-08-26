import { describe, expect, it } from "@jest/globals";
import {
  backendUrl,
  clientSecretPostClient,
  federationServerConfig,
  serverConfig
} from "../../testConfig";
import { authorize, postAuthentication, requestToken } from "../../../api/oauthClient";
import { get, postWithJson } from "../../../lib/http";
import { requestAuthorizations } from "../../../oauth/request";
import { convertNextAction } from "../../../lib/util";

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
  const login = async (user) => {
    const { status, authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      redirectUri: clientSecretPostClient.redirectUri,
      responseType: "code",
      state: "account-linking-e2e",
      scope: "openid profile email",
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

  const startLink = async (accessToken) =>
    await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/link/${PROVIDER}`,
      headers: { Authorization: `Bearer ${accessToken}` },
      body: {
        redirect_uri: RETURN_TO,
        scope: "openid profile email offline_access",
      },
    });

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
    const adminAccessToken = adminTokenResponse.data.access_token;

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
      console.log(startResponse.status, startResponse.data);
      expect(startResponse.status).toBe(403);
    });
  });
});
