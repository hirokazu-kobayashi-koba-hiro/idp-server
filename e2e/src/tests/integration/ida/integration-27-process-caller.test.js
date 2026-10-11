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
import { createBasicAuthHeader } from "../../../lib/util";
import { v4 as uuidv4 } from "uuid";

/**
 * #1966: process の caller で、実行できる入口を分ける。
 *
 * エンドユーザー向けの入口（/me/...）とコールバック（/internal/...）は、同じ processes から
 * process を引く。caller で入口を限定し、限定された入口以外から呼ぶと、登録されていない
 * process と同じく 404 にする。caller が無く request.basic_auth がある process は、
 * コールバック用とみなす。
 */
describe("Identity Verification - process caller (#1966)", () => {
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
      scope: "openid profile phone email identity_verification_application " + clientSecretPostClient.identityVerificationScope
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

  // apply (end user) + "callback-result", whose caller and basic_auth vary by test.
  const buildConfig = ({ configId, type, callbackResult }) => ({
    "id": configId,
    "type": type,
    "attributes": { "enabled": true },
    "common": { "auth_type": "none", "callback_application_id_param": "application_id" },
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
      "callback-result": {
        ...callbackResult,
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

  const resultSchema = {
    "type": "object",
    "required": ["application_id", "result"],
    "properties": {
      "application_id": { "type": "string" },
      "result": { "type": "string" }
    }
  };

  const setUp = async (callbackResult) => {
    const type = `caller-${uuidv4().substring(0, 8)}`;
    await registerConfiguration(buildConfig({ configId: uuidv4(), type, callbackResult }));
    const { accessToken } = await createTestUser();
    const externalRef = `ext-${uuidv4()}`;
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");
    const applied = await callProcess({ url: applyUrl, accessToken, body: { external_ref: externalRef } });
    expect(applied.status).toBe(200);
    return { type, accessToken, externalRef, applicationId: applied.data.id };
  };

  const getApplicationStatus = async ({ type, accessToken, applicationId }) => {
    const res = await get({
      url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}`,
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    expect(res.status).toBe(200);
    expect(res.data.list.length).toBe(1);
    return res.data.list[0].status;
  };

  const callFromEndUser = async ({ type, accessToken, applicationId, process, body }) => {
    const url = serverConfig.identityVerificationProcessEndpoint
      .replace("{type}", type)
      .replace("{id}", applicationId)
      .replace("{process}", process);
    return callProcess({ url, accessToken, body });
  };

  const callFromExternalService = async ({ type, process, basicAuth, body }) => {
    const url = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
      .replace("{type}", type)
      .replace("{callbackName}", process);
    return callProcess({
      url,
      headers: { "Content-Type": "application/json", ...(basicAuth ? createBasicAuthHeader(basicAuth) : {}) },
      body
    });
  };

  const callFromExternalServiceWithId = async ({ type, applicationId, process, body }) => {
    const url = `${backendUrl}/${tenantId}/internal/v1/identity-verification/callback/${type}/${applicationId}/${process}`;
    return callProcess({ url, headers: { "Content-Type": "application/json" }, body });
  };

  it("caller が無く basic_auth がある process は、エンドユーザー向けの入口から実行できない", async () => {
    const basicAuth = { username: "caller_cb_user", password: "caller_cb_password001" };
    const { type, accessToken, externalRef, applicationId } = await setUp({
      "request": { "basic_auth": basicAuth, "schema": resultSchema }
    });

    const fromEndUser = await callFromEndUser({
      type, accessToken, applicationId,
      process: "callback-result",
      body: { application_id: externalRef, result: "ok" }
    });
    expect(fromEndUser.status).toBe(404);
    expect(await getApplicationStatus({ type, accessToken, applicationId })).toBe("requested");

    const fromExternalService = await callFromExternalService({
      type, process: "callback-result", basicAuth,
      body: { application_id: externalRef, result: "ok" }
    });
    expect(fromExternalService.status).toBe(200);
    expect(await getApplicationStatus({ type, accessToken, applicationId })).toBe("approved");
  });

  it("caller が external_service の process は、エンドユーザー向けの入口から実行できない", async () => {
    const { type, accessToken, externalRef, applicationId } = await setUp({
      "caller": "external_service",
      "request": { "schema": resultSchema }
    });

    const fromEndUser = await callFromEndUser({
      type, accessToken, applicationId,
      process: "callback-result",
      body: { application_id: externalRef, result: "ok" }
    });
    expect(fromEndUser.status).toBe(404);

    const applyStyleUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "callback-result");
    const fromEndUserWithoutId = await callProcess({
      url: applyStyleUrl, accessToken,
      body: { application_id: externalRef, result: "ok" }
    });
    expect(fromEndUserWithoutId.status).toBe(404);

    // 登録されていない process と同じ応答にして、もう一方の入口用の process があるかを読ませない。
    const unregisteredUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "no-such-process");
    const unregistered = await callProcess({
      url: unregisteredUrl, accessToken,
      body: { application_id: externalRef, result: "ok" }
    });
    expect(unregistered.status).toBe(404);
    expect(fromEndUserWithoutId.data).toEqual({
      ...unregistered.data,
      error_description: unregistered.data.error_description.replace("no-such-process", "callback-result")
    });
    expect(await getApplicationStatus({ type, accessToken, applicationId })).toBe("requested");

    const fromExternalService = await callFromExternalService({
      type, process: "callback-result",
      body: { application_id: externalRef, result: "ok" }
    });
    expect(fromExternalService.status).toBe(200);
    expect(await getApplicationStatus({ type, accessToken, applicationId })).toBe("approved");
  });

  it("caller が end_user の process は、コールバックから実行できない", async () => {
    const { type, accessToken, externalRef, applicationId } = await setUp({
      "caller": "end_user",
      "request": { "schema": resultSchema }
    });

    const fromExternalService = await callFromExternalService({
      type, process: "callback-result",
      body: { application_id: externalRef, result: "ok" }
    });
    expect(fromExternalService.status).toBe(404);

    const fromExternalServiceWithId = await callFromExternalServiceWithId({
      type, applicationId,
      process: "callback-result",
      body: { application_id: externalRef, result: "ok" }
    });
    expect(fromExternalServiceWithId.status).toBe(404);
    expect(await getApplicationStatus({ type, accessToken, applicationId })).toBe("requested");

    const fromEndUser = await callFromEndUser({
      type, accessToken, applicationId,
      process: "callback-result",
      body: { application_id: externalRef, result: "ok" }
    });
    expect(fromEndUser.status).toBe(200);
    expect(await getApplicationStatus({ type, accessToken, applicationId })).toBe("approved");
  });
});
