import { describe, expect, it } from "@jest/globals";
import { postWithJson } from "../../../lib/http";
import {
  serverConfig,
  clientSecretPostClient,
  backendUrl,
} from "../../testConfig";
import {
  getAuthorizations,
  postAuthentication,
  authorize,
} from "../../../api/oauthClient";
import { convertNextAction } from "../../../lib/util";

/**
 * OpenID Connect Core 3.1.2.1 — prompt=login と authorize-with-session
 *
 * `POST /{tenant}/v1/authorizations/{id}/authorize-with-session` は、既存の OP セッションを使って
 * **認証を行わずに**認可を完了させる。prompt=login はまさにその再認証を要求するパラメータなので、
 * このエンドポイントは prompt=login のリクエストを受け付けてはいけない。
 *
 * 画面は view-data の `session_enabled`（`OAuthViewDataCreator.isSessionEnabled()` が
 * prompt=login を弾く）を見てから呼ぶが、このエンドポイントは HTTP で直接叩けるため、
 * サーバー側でも判定する（`OIDCSessionVerifier.verifyForAuthorization()`）。
 */
describe("3.1.2.1.  Authentication Request", () => {
  const user = {
    username: serverConfig.oauth.username,
    password: serverConfig.oauth.password,
  };

  /** 認可リクエストを 1 本作って、サインイン画面に渡される authorization id を返す。 */
  const startAuthorization = async (extra = {}) => {
    const response = await getAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      scope: "openid profile",
      redirectUri: clientSecretPostClient.redirectUri,
      state: `state-${Date.now()}`,
      nonce: `nonce-${Date.now()}`,
      ...extra,
    });
    expect(response.status).toBe(302);

    const { nextAction, params } = convertNextAction(response.headers.location);
    expect(nextAction).toBe("goAuthentication");
    return params.get("id");
  };

  /** パスワード認証して認可まで完了させる。ここで OP セッションができる。 */
  const signIn = async (authorizationId) => {
    const passwordResponse = await postAuthentication({
      endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authorizationId,
      body: user,
    });
    expect(passwordResponse.status).toBe(200);

    const authorizeResponse = await authorize({
      endpoint: serverConfig.authorizeEndpoint,
      id: authorizationId,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);
  };

  const authorizeWithSession = async (authorizationId) =>
    await postWithJson({
      url:
        serverConfig.authorizationIdEndpoint.replace("{id}", authorizationId) +
        "authorize-with-session",
      body: {},
    });

  describe("prompt", () => {
    it("login The Authorization Server SHOULD prompt the End-User for reauthentication. If it cannot reauthenticate the End-User, it MUST return an error, typically login_required.", async () => {
      // 1 回目: 通常どおりログインしてセッションを作る
      const firstId = await startAuthorization();
      await signIn(firstId);

      // 2 回目: prompt=login。セッションはあるが、再認証を省略してはいけない
      const promptLoginId = await startAuthorization({ prompt: "login" });
      const rejected = await authorizeWithSession(promptLoginId);

      expect(rejected.status).toBe(400);
      expect(rejected.data.error).toBe("invalid_request");
      expect(rejected.data.error_description).toBe(
        "prompt=login requires re-authentication",
      );
    });

    it("同じセッションでも prompt が無ければ authorize-with-session で認可が完了する（拒否が prompt=login 固有であることの確認）", async () => {
      const firstId = await startAuthorization();
      await signIn(firstId);

      const secondId = await startAuthorization();
      const accepted = await authorizeWithSession(secondId);

      expect(accepted.status).toBe(200);
      expect(accepted.data.redirect_uri).toBeDefined();
    });
  });
});
