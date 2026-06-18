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
import { mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";

/**
 * #1522: callback API 経路でも pre_hook (process_sequence) が honor されることを検証する。
 *
 * 修正前は callback 経路が pre_hook 検証を実行せず、callback プロセスに設定した
 * dependencies / allow_retry が事実上無視されてバイパス可能だった。
 *
 * 1. 依存プロセス未完で callback-result を送ると 400 pre_hook_validation_failed
 * 2. allow_retry: false の callback-result を二重送信すると 2 回目が 400
 */
describe("Identity Verification - callback pre_hook verification (#1522)", () => {
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

  // apply (store application_id) + a callback "callback-result" guarded by process_sequence.
  const buildConfig = ({ configId, type, basicAuth, dependencies }) => ({
    "id": configId,
    "type": type,
    "attributes": { "enabled": true },
    "common": { "auth_type": "none", "callback_application_id_param": "application_id" },
    "processes": {
      "apply": {
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
        "request": {
          "basic_auth": basicAuth,
          "schema": {
            "type": "object",
            "required": ["application_id", "result"],
            "properties": {
              "application_id": { "type": "string" },
              "result": { "type": "string" }
            }
          }
        },
        "dependencies": dependencies,
        "pre_hook": { "verifications": [{ "type": "process_sequence" }] },
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

  const apply = async ({ type, accessToken, externalRef }) => {
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");
    const res = await callProcess({ url: applyUrl, accessToken, body: { external_ref: externalRef } });
    expect(res.status).toBe(200);
    return res.data.id;
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

  const sendCallbackResult = async ({ type, basicAuth, externalRef }) => {
    const callbackUrl = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
      .replace("{type}", type)
      .replace("{callbackName}", "callback-result");
    return callProcess({
      url: callbackUrl,
      headers: { "Content-Type": "application/json", ...createBasicAuthHeader(basicAuth) },
      body: { application_id: externalRef, result: "ok" }
    });
  };

  it("rejects callback when a required process is not completed (process_sequence honored on callback)", async () => {
    const { accessToken } = await createTestUser();
    const type = uuidv4();
    const basicAuth = { username: "cb_dep_user", password: "cb_dep_password001" };
    // callback-result requires "callback-examination", which is never executed.
    await registerConfiguration(
      buildConfig({
        configId: uuidv4(),
        type,
        basicAuth,
        dependencies: { required_processes: ["callback-examination"], allow_retry: false }
      })
    );

    const externalRef = uuidv4();
    await apply({ type, accessToken, externalRef });

    const response = await sendCallbackResult({ type, basicAuth, externalRef });

    expect(response.status).toBe(400);
    expect(response.data).toHaveProperty("error", "pre_hook_validation_failed");
    expect(JSON.stringify(response.data)).toContain("callback-examination");
  });

  it("rejects a duplicate callback when allow_retry is false (retry control honored on callback)", async () => {
    const { accessToken } = await createTestUser();
    const type = uuidv4();
    const basicAuth = { username: "cb_retry_user", password: "cb_retry_password001" };
    await registerConfiguration(
      buildConfig({
        configId: uuidv4(),
        type,
        basicAuth,
        dependencies: { required_processes: [], allow_retry: false }
      })
    );

    const externalRef = uuidv4();
    await apply({ type, accessToken, externalRef });

    const first = await sendCallbackResult({ type, basicAuth, externalRef });
    expect(first.status).toBe(200);

    const second = await sendCallbackResult({ type, basicAuth, externalRef });
    expect(second.status).toBe(400);
    expect(second.data).toHaveProperty("error", "pre_hook_validation_failed");
    expect(JSON.stringify(second.data)).toContain("does not allow retry");
  });

  // New capability unlocked by the unification: a callback process can now run an `execution`
  // (e.g. http_request) on receipt AND drive its transition from the executed `$.response_body`.
  // Before the unification the callback never executed, so `$.response_body` did not exist and the
  // transition could only see the inbound `$.request_body`. (#1522)
  it("drives the callback transition from the executed $.response_body (→ approved)", async () => {
    const { accessToken } = await createTestUser();
    const type = uuidv4();
    const basicAuth = { username: "cb_exec_user", password: "cb_exec_password001" };
    await registerConfiguration({
      "id": uuidv4(),
      "type": type,
      "attributes": { "enabled": true },
      "common": { "auth_type": "none", "callback_application_id_param": "application_id" },
      "processes": {
        "apply": {
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
          "request": {
            "basic_auth": basicAuth,
            "schema": {
              "type": "object",
              "required": ["application_id"],
              "properties": { "application_id": { "type": "string" } }
            }
          },
          // execution runs against the mock on callback receipt; the mock returns {status:"ok"}
          "execution": {
            "type": "http_request",
            "http_request": {
              "url": `${mockApiBaseUrl}/crm-registration`,
              "method": "POST",
              "auth_type": "none",
              "header_mapping_rules": [{ "static_value": "application/json", "to": "Content-Type" }],
              "body_mapping_rules": [{ "from": "$.request_body", "to": "*" }]
            }
          },
          // transition reads the EXECUTED response, not the inbound request body
          "transition": {
            "approved": {
              "any_of": [
                [{ "path": "$.response_body.status", "type": "string", "operation": "eq", "value": "ok" }]
              ]
            }
          },
          "response": { "body_mapping_rules": [{ "from": "$.response_body", "to": "*" }] }
        }
      }
    });

    const externalRef = uuidv4();
    const applicationId = await apply({ type, accessToken, externalRef });

    const callbackUrl = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
      .replace("{type}", type)
      .replace("{callbackName}", "callback-result");
    const response = await callProcess({
      url: callbackUrl,
      headers: { "Content-Type": "application/json", ...createBasicAuthHeader(basicAuth) },
      body: { application_id: externalRef }
    });

    // execution ran on the callback path → the executed response was echoed back
    expect(response.status).toBe(200);
    expect(response.data).toHaveProperty("status", "ok");

    // and the transition was driven by that executed $.response_body → application is approved
    const status = await getApplicationStatus({ type, accessToken, applicationId });
    expect(status).toBe("approved");
  });

  // #1613: when a callback `execution` (http_request) hits a downstream failure, the callback must
  // report that real status (e.g. 502) instead of collapsing every error to 400. Before the fix the
  // status code was discarded and CLIENT_ERROR (400) was returned unconditionally, masking IdP-side
  // downstream outages as "your request is invalid" to the calling eKYC service.
  it("returns the downstream status (502) when the callback execution fails, not a blanket 400", async () => {
    const { accessToken } = await createTestUser();
    const type = uuidv4();
    const basicAuth = { username: "cb_5xx_user", password: "cb_5xx_password001" };
    await registerConfiguration({
      "id": uuidv4(),
      "type": type,
      "attributes": { "enabled": true },
      "common": { "auth_type": "none", "callback_application_id_param": "application_id" },
      "processes": {
        "apply": {
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
          "request": {
            "basic_auth": basicAuth,
            "schema": {
              "type": "object",
              "required": ["application_id"],
              "properties": { "application_id": { "type": "string" } }
            }
          },
          // execution targets the mock's error endpoint, which returns 502 for body.status="502"
          "execution": {
            "type": "http_request",
            "http_request": {
              "url": `${mockApiBaseUrl}/e2e/error-responses`,
              "method": "POST",
              "auth_type": "none",
              "header_mapping_rules": [{ "static_value": "application/json", "to": "Content-Type" }],
              "body_mapping_rules": [{ "static_value": "502", "to": "status" }]
            }
          }
        }
      }
    });

    const externalRef = uuidv4();
    await apply({ type, accessToken, externalRef });

    const callbackUrl = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
      .replace("{type}", type)
      .replace("{callbackName}", "callback-result");
    const response = await callProcess({
      url: callbackUrl,
      headers: { "Content-Type": "application/json", ...createBasicAuthHeader(basicAuth) },
      body: { application_id: externalRef }
    });

    // the callback surfaces the downstream 502 instead of a flattened 400 (#1613)
    expect(response.status).toBe(502);
  });

  // The callback endpoint is public; its only gate is the configured Basic auth. A callback with
  // wrong credentials must be rejected (not silently processed). (#1186)
  it("rejects a callback presenting invalid Basic credentials", async () => {
    const { accessToken } = await createTestUser();
    const type = uuidv4();
    const basicAuth = { username: "cb_authz_user", password: "cb_authz_password001" };
    await registerConfiguration(
      buildConfig({
        configId: uuidv4(),
        type,
        basicAuth,
        dependencies: { required_processes: [], allow_retry: true }
      })
    );

    const externalRef = uuidv4();
    await apply({ type, accessToken, externalRef });

    const callbackUrl = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
      .replace("{type}", type)
      .replace("{callbackName}", "callback-result");
    const response = await callProcess({
      url: callbackUrl,
      headers: {
        "Content-Type": "application/json",
        ...createBasicAuthHeader({ username: basicAuth.username, password: "wrong-password" })
      },
      body: { application_id: externalRef, result: "ok" }
    });

    // The server enforces the configured Basic auth and rejects the callback as an invalid request
    // (400 invalid_request) rather than processing it. (Note: rejection is modeled as request
    // validation, not a 401 challenge.)
    expect(response.status).toBe(400);
    expect(response.data).toHaveProperty("error", "invalid_request");
    expect(JSON.stringify(response.data)).toContain("basic authentication");
  });
});
