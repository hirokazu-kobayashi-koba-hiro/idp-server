import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";

/**
 * #1969: 申込みを ID で取得する入口は、パスの {verification-type} が申込みの種別と一致するときだけ
 * 処理する。一致しなければ、存在しない申込みと同じ 404 にする。
 *
 * 対象: /me/ の process 実行・削除、コールバック（申込みID指定・ボディで特定）
 */
describe("Identity Verification - application type must match the path (#1969)", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  const createdConfigIds = [];

  beforeAll(async () => {
    const orgAuthResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      scope: "org-management account management"
    });
    expect(orgAuthResponse.status).toBe(200);
    orgAccessToken = orgAuthResponse.data.access_token;
  });

  afterAll(async () => {
    for (const configId of createdConfigIds) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` }
      });
    }
  });

  const registerConfiguration = async (configurationData) => {
    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        "Authorization": `Bearer ${orgAccessToken}`,
        "Content-Type": "application/json"
      },
      body: configurationData
    });
    expect(response.status).toBe(201);
    createdConfigIds.push(configurationData.id);
  };

  const createTestUser = async () => {
    const { user, accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope: "openid profile phone email identity_verification_application identity_verification_application_delete " + clientSecretPostClient.identityVerificationScope
    });
    await registerFidoUaf({ accessToken: accessToken });
    return { user, accessToken };
  };

  const callProcess = async ({ url, accessToken, body, headers }) => {
    const response = await postWithJson({
      url,
      headers: headers || {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`
      },
      body
    });
    console.log("Process response:", url, response.status, JSON.stringify(response.data, null, 2));
    return response;
  };

  const buildConfig = ({ configId, type }) => ({
    "id": configId,
    "type": type,
    "attributes": { "enabled": true },
    "common": { "callback_application_id_param": "application_id" },
    "processes": {
      "apply": {
        "caller": "end_user",
        "request": {
          "schema": {
            "type": "object",
            "required": ["external_ref"],
            "properties": { "external_ref": { "type": "string" } }
          }
        },
        "execution": { "type": "no_action" },
        "store": {
          "application_details_mapping_rules": [
            { "from": "$.request_body", "to": "*" },
            { "from": "$.request_body.external_ref", "to": "application_id" }
          ]
        }
      },
      "update": {
        "caller": "end_user",
        "execution": { "type": "no_action" }
      },
      "callback-result": {
        "caller": "external_service",
        "execution": { "type": "no_action" },
        "transition": {
          "approved": {
            "any_of": [
              [{ "path": "$.request_body.result", "type": "string", "operation": "eq", "value": "ok" }]
            ]
          }
        }
      }
    }
  });

  const getApplicationStatus = async ({ type, accessToken, applicationId }) => {
    const res = await get({
      url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}`,
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    expect(res.status).toBe(200);
    return res.data.list.length === 0 ? null : res.data.list[0].status;
  };

  // 種別 A で申込み、種別 B も同じ構成で登録しておく
  const setUp = async () => {
    const typeA = `type-a-${uuidv4().substring(0, 8)}`;
    const typeB = `type-b-${uuidv4().substring(0, 8)}`;
    await registerConfiguration(buildConfig({ configId: uuidv4(), type: typeA }));
    await registerConfiguration(buildConfig({ configId: uuidv4(), type: typeB }));
    const { accessToken } = await createTestUser();
    const externalRef = `ext-${uuidv4()}`;
    const applied = await callProcess({
      url: serverConfig.identityVerificationApplyEndpoint.replace("{type}", typeA).replace("{process}", "apply"),
      accessToken,
      body: { external_ref: externalRef }
    });
    expect(applied.status).toBe(200);
    return { typeA, typeB, accessToken, externalRef, applicationId: applied.data.id };
  };

  const callbackWithId = ({ type, applicationId, body }) =>
    callProcess({
      url: `${backendUrl}/${tenantId}/internal/v1/identity-verification/callback/${type}/${applicationId}/callback-result`,
      headers: { "Content-Type": "application/json" },
      body
    });

  const callbackByBody = ({ type, body }) =>
    callProcess({
      url: serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
        .replace("{type}", type)
        .replace("{callbackName}", "callback-result"),
      headers: { "Content-Type": "application/json" },
      body
    });

  // 存在しない申込みと同じ応答か（メッセージ中の ID だけ差し替えて比べる）
  const expectSameAsUnknown = (actual, unknown, actualId, unknownId) => {
    expect(actual.status).toBe(404);
    expect(unknown.status).toBe(404);
    expect(actual.data).toEqual({
      ...unknown.data,
      error_description: unknown.data.error_description.replace(unknownId, actualId)
    });
  };

  it("/me/ の process 実行は、別の種別のパスからは 404（存在しない申込みと同じ）", async () => {
    const { typeA, typeB, accessToken, applicationId } = await setUp();
    const unknownId = uuidv4();

    const processUrl = (type, id) =>
      serverConfig.identityVerificationProcessEndpoint
        .replace("{type}", type)
        .replace("{id}", id)
        .replace("{process}", "update");

    const mismatched = await callProcess({ url: processUrl(typeB, applicationId), accessToken, body: {} });
    const unknown = await callProcess({ url: processUrl(typeB, unknownId), accessToken, body: {} });
    expectSameAsUnknown(mismatched, unknown, applicationId, unknownId);

    const matched = await callProcess({ url: processUrl(typeA, applicationId), accessToken, body: {} });
    expect(matched.status).toBe(200);
  });

  it("コールバック（申込みID指定）は、別の種別のパスからは 404 で、申込みは変わらない", async () => {
    const { typeA, typeB, accessToken, applicationId } = await setUp();
    const unknownId = uuidv4();

    const mismatched = await callbackWithId({ type: typeB, applicationId, body: { result: "ok" } });
    const unknown = await callbackWithId({ type: typeB, applicationId: unknownId, body: { result: "ok" } });
    expectSameAsUnknown(mismatched, unknown, applicationId, unknownId);
    expect(await getApplicationStatus({ type: typeA, accessToken, applicationId })).toBe("requested");

    const matched = await callbackWithId({ type: typeA, applicationId, body: { result: "ok" } });
    expect(matched.status).toBe(200);
    expect(await getApplicationStatus({ type: typeA, accessToken, applicationId })).toBe("approved");
  });

  it("コールバック（ボディで特定）は、別の種別のパスからは 404 で、申込みは変わらない", async () => {
    const { typeA, typeB, accessToken, externalRef, applicationId } = await setUp();
    const unknownRef = `ext-${uuidv4()}`;

    const mismatched = await callbackByBody({ type: typeB, body: { application_id: externalRef, result: "ok" } });
    const unknown = await callbackByBody({ type: typeB, body: { application_id: unknownRef, result: "ok" } });
    expectSameAsUnknown(mismatched, unknown, externalRef, unknownRef);
    expect(await getApplicationStatus({ type: typeA, accessToken, applicationId })).toBe("requested");

    const matched = await callbackByBody({ type: typeA, body: { application_id: externalRef, result: "ok" } });
    expect(matched.status).toBe(200);
    expect(await getApplicationStatus({ type: typeA, accessToken, applicationId })).toBe("approved");
  });

  it("/me/ の削除は、別の種別のパスからは 404 で、申込みは残る", async () => {
    const { typeA, typeB, accessToken, applicationId } = await setUp();
    const unknownId = uuidv4();

    const deleteUrl = (type, id) =>
      serverConfig.identityVerificationApplicationsDeletionEndpoint.replace("{type}", type).replace("{id}", id);

    const mismatched = await deletion({ url: deleteUrl(typeB, applicationId), headers: { Authorization: `Bearer ${accessToken}` } });
    const unknown = await deletion({ url: deleteUrl(typeB, unknownId), headers: { Authorization: `Bearer ${accessToken}` } });
    expectSameAsUnknown(mismatched, unknown, applicationId, unknownId);
    expect(await getApplicationStatus({ type: typeA, accessToken, applicationId })).toBe("requested");

    const matched = await deletion({ url: deleteUrl(typeA, applicationId), headers: { Authorization: `Bearer ${accessToken}` } });
    expect(matched.status).toBe(200);
    expect(await getApplicationStatus({ type: typeA, accessToken, applicationId })).toBeNull();
  });
});
