import { describe, expect, it } from "@jest/globals";

import { get } from "../../../lib/http";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  requestBackchannelAuthentications,
  requestToken,
} from "../../../api/oauthClient";
import {
  adminServerConfig,
  backendUrl,
  clientSecretPostClient,
  serverConfig,
} from "../../testConfig";

/**
 * 認証トランザクションの id 絞り込み。
 *
 * 「一覧から探す」代わりに「id 指定で 1 件引く」設計のクライアントが依存する経路。
 * 同じ AuthenticationTransactionQueries が 3 つのエンドポイントから使われるため、
 * デバイス単位 / テナント単位 / 組織単位の全てを対象にする。
 */
describe("authentication transaction id filter", () => {
  /**
   * CIBA を 1 件流し、フィルタ対象になる認証トランザクションを用意する。
   * auth_req_id での絞り込みは既に動くため、そこから id を取得する。
   */
  const prepareTransaction = async () => {
    const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
      endpoint: serverConfig.backchannelAuthenticationEndpoint,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      scope: "openid profile phone email" + clientSecretPostClient.scope,
      bindingMessage: serverConfig.ciba.bindingMessage,
      userCode: serverConfig.ciba.userCode,
      loginHint: serverConfig.ciba.loginHint,
    });
    expect(backchannelAuthenticationResponse.status).toBe(200);

    const listedByAuthReqId = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: serverConfig.ciba.authenticationDeviceId,
      params: {
        "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id,
      },
    });
    expect(listedByAuthReqId.status).toBe(200);

    const transaction = listedByAuthReqId.data.list[0];
    expect(transaction).toBeDefined();
    expect(transaction.id).toBeDefined();

    return transaction;
  };

  /** 組織単位の管理 API 用。組織に属するテナントのクライアントで足りる。 */
  const requestOrganizationManagementToken = async () => {
    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    return tokenResponse.data.access_token;
  };

  /** テナント単位の管理 API はシステムレベルのため admin テナントのトークンが要る。 */
  const requestAdminToken = async () => {
    const tokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    return tokenResponse.data.access_token;
  };

  it("device transaction list returns the single transaction specified by id", async () => {
    const transaction = await prepareTransaction();

    const response = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: serverConfig.ciba.authenticationDeviceId,
      params: { id: transaction.id },
    });
    console.log("device list by id:", response.status, JSON.stringify(response.data));

    expect(response.status).toBe(200);
    expect(response.data.list).toHaveLength(1);
    expect(response.data.list[0].id).toBe(transaction.id);
    expect(response.data.total_count).toBe(1);
  });

  it("tenant transaction management list returns the single transaction specified by id", async () => {
    const transaction = await prepareTransaction();
    const accessToken = await requestAdminToken();

    const response = await get({
      url: `${serverConfig.authenticationEndpoint}?id=${transaction.id}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    console.log("tenant list by id:", response.status, JSON.stringify(response.data));

    expect(response.status).toBe(200);
    expect(response.data.list).toHaveLength(1);
    expect(response.data.list[0].id).toBe(transaction.id);
    expect(response.data.total_count).toBe(1);
  });

  it("organization transaction management list returns the single transaction specified by id", async () => {
    const transaction = await prepareTransaction();
    const accessToken = await requestOrganizationManagementToken();

    const response = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?id=${transaction.id}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    console.log("organization list by id:", response.status, JSON.stringify(response.data));

    expect(response.status).toBe(200);
    expect(response.data.list).toHaveLength(1);
    expect(response.data.list[0].id).toBe(transaction.id);
    expect(response.data.total_count).toBe(1);
  });

  it("tenant transaction management list filters by device_id", async () => {
    const transaction = await prepareTransaction();
    const accessToken = await requestAdminToken();

    // device_id だけだと同一デバイスの過去分に埋もれて既定の limit から押し出されるため、
    // id を併用して 1 件に固定したうえで device_id が絞り込みとして働くことを見る。
    const matched = await get({
      url: `${serverConfig.authenticationEndpoint}?id=${transaction.id}&device_id=${serverConfig.ciba.authenticationDeviceId}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    console.log("tenant list by id+device_id:", matched.status, JSON.stringify(matched.data));

    expect(matched.status).toBe(200);
    expect(matched.data.list).toHaveLength(1);
    expect(matched.data.list[0].id).toBe(transaction.id);

    const unmatched = await get({
      url: `${serverConfig.authenticationEndpoint}?id=${transaction.id}&device_id=00000000-0000-4000-8000-000000000000`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    console.log("tenant list by id+other device_id:", unmatched.status, JSON.stringify(unmatched.data));

    expect(unmatched.status).toBe(200);
    expect(unmatched.data.list).toHaveLength(0);
    expect(unmatched.data.total_count).toBe(0);
  });

  it("device transaction list keeps list and total_count consistent with device_id", async () => {
    const transaction = await prepareTransaction();

    // パスのデバイスに対する device_id クエリ。selectListByDeviceId は device_id を
    // 見ないため、件数側だけが絞られて list と食い違わないことを確認する。
    const sameDevice = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: serverConfig.ciba.authenticationDeviceId,
      params: { id: transaction.id, device_id: serverConfig.ciba.authenticationDeviceId },
    });
    console.log("device list by id+same device_id:", sameDevice.status, JSON.stringify(sameDevice.data));

    expect(sameDevice.status).toBe(200);
    expect(sameDevice.data.list).toHaveLength(sameDevice.data.total_count);
    expect(sameDevice.data.list).toHaveLength(1);

    const otherDevice = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: serverConfig.ciba.authenticationDeviceId,
      params: { id: transaction.id, device_id: "00000000-0000-4000-8000-000000000000" },
    });
    console.log("device list by id+other device_id:", otherDevice.status, JSON.stringify(otherDevice.data));

    // パスのデバイスが勝ち、クエリの device_id は見られない。
    expect(otherDevice.status).toBe(200);
    expect(otherDevice.data.list).toHaveLength(otherDevice.data.total_count);
    expect(otherDevice.data.list).toHaveLength(1);
    expect(otherDevice.data.list[0].id).toBe(transaction.id);
  });

  it("device transaction list accepts exclude_expired", async () => {
    const transaction = await prepareTransaction();

    const response = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: serverConfig.ciba.authenticationDeviceId,
      params: { id: transaction.id, exclude_expired: "false" },
    });
    console.log("device list with exclude_expired=false:", response.status, JSON.stringify(response.data));

    expect(response.status).toBe(200);
    expect(response.data.list).toHaveLength(1);
    expect(response.data.list[0].id).toBe(transaction.id);
  });

  it("device transaction list returns empty for an unknown id", async () => {
    await prepareTransaction();

    const response = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: serverConfig.ciba.authenticationDeviceId,
      params: { id: "00000000-0000-4000-8000-000000000000" },
    });
    console.log("device list by unknown id:", response.status, JSON.stringify(response.data));

    expect(response.status).toBe(200);
    expect(response.data.list).toHaveLength(0);
    expect(response.data.total_count).toBe(0);
  });
});
