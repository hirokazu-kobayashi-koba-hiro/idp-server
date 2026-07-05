import { describe, expect, it } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken,
} from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";

/**
 * CIBA auth_req_id single-use under concurrent polling (TOCTOU).
 *
 * 脆弱性パターン:
 * CibaGrantService.create() は find(auth_req_id) -> verify -> register(token) -> delete
 * の順で、認可コード交換と同型の TOCTOU を持つ。同一 auth_req_id で並行に poll すると、
 * 双方が「認可済み」の grant を読み取り、双方がトークンを発行し得る
 * (1 auth_req_id からトークン2セット = single-use 違反)。
 *
 * 修正:
 * CibaGrantService は CibaGrantRepository.findForUpdate(tenant, authReqId) で grant 行を
 * SELECT ... FOR UPDATE ロックする。並行 poll は先行トランザクションの commit(grant 削除)
 * までブロックし、その後は行なし -> invalid_grant。
 *
 * 期待: 並行 poll で成功は最大1つ。残りは invalid_grant。
 *
 * 重大度: High
 * CWE: CWE-362 (Race Condition / Concurrent Execution using Shared Resource)
 * 関連ファイル:
 * - CibaGrantService.java (find -> findForUpdate)
 * - ciba/grant/{Postgresql,Mysql}Executor.java (selectOneForUpdate + tenant_id)
 */
describe("CIBA auth_req_id single-use under concurrent polling", () => {
  const ciba = serverConfig.ciba;

  it("Concurrent token requests with the same auth_req_id issue at most one token set (single-use enforced under a race).", async () => {
    const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
      endpoint: serverConfig.backchannelAuthenticationEndpoint,
      clientId: clientSecretPostClient.clientId,
      scope:
        "openid profile email" +
        (clientSecretPostClient.scope ? " " + clientSecretPostClient.scope : ""),
      bindingMessage: ciba.bindingMessage,
      userCode: ciba.userCode,
      loginHint: ciba.loginHint,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(backchannelAuthenticationResponse.status).toBe(200);
    const authReqId = backchannelAuthenticationResponse.data.auth_req_id;

    const authenticationTransactionResponse =
      await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: { "attributes.auth_req_id": authReqId },
      });
    expect(authenticationTransactionResponse.status).toBe(200);
    const authenticationTransaction = authenticationTransactionResponse.data.list[0];

    const completeResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "password-authentication",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      },
    });
    expect(completeResponse.status).toBe(200);

    // The grant is now authorized. Poll the token endpoint with the same auth_req_id
    // several times in parallel; findForUpdate (SELECT ... FOR UPDATE) must let at
    // most one exchange succeed.
    const poll = () =>
      requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

    const responses = await Promise.all([poll(), poll(), poll()]);
    const succeeded = responses.filter((response) => response.status === 200);
    const denied = responses.filter((response) => response.status !== 200);

    expect(succeeded).toHaveLength(1);
    denied.forEach((response) => {
      expect(response.status).toBe(400);
      expect(response.data.error).toEqual("invalid_grant");
    });
  });
});
