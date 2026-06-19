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
import { createBasicAuthHeader, sleep } from "../../../lib/util";
import { v4 as uuidv4 } from "uuid";

/**
 * #1617 Phase 1 (PR #1647): terminal application statuses are absorbing.
 *
 * A successful process / callback re-run on an application that is *already* terminal keeps the
 * same status (the reconcile absorbs it). The c604f0fa fix guards the terminal side-effects so that
 * such a successful re-run does NOT:
 *   - re-publish the lifecycle security events ({type}_approved / identity_verification_application_approved)
 *   - re-register the IdentityVerificationResult
 *   - re-patch the user's verified_claims
 *
 * Before the fix, re-submitting a successful callback-result on an approved application re-fired the
 * approved events and re-registered the result. This test reproduces that path and asserts the
 * counts stay flat across the re-run.
 *
 * The re-run is made to *succeed* (allow_retry: true, no status restriction) on purpose: the whole
 * point is that pre_hook passes and the callback executes again — so a flat event/result count
 * proves the EntryService terminal guard, not a rejected re-submission (which #1522 already covers).
 */
describe("Identity Verification - terminal absorbing: no duplicate lifecycle events on successful re-run (#1617)", () => {
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
      scope: "openid profile phone email identity_verification_application identity_verification_result " + clientSecretPostClient.identityVerificationScope
    });
    await registerFidoUaf({ accessToken: accessToken });
    return { user, accessToken };
  };

  // apply (no_action) + a callback-result that transitions → approved on result=ok.
  // allow_retry: true and no status restriction → a successful re-run reaches the terminal guard.
  const buildConfig = ({ configId, type, basicAuth }) => ({
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
        "dependencies": { "required_processes": [], "allow_retry": true },
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
    const res = await postWithJson({
      url: applyUrl,
      headers: { "Content-Type": "application/json", "Authorization": `Bearer ${accessToken}` },
      body: { external_ref: externalRef }
    });
    expect(res.status).toBe(200);
    return res.data.id;
  };

  const sendCallbackResult = async ({ type, basicAuth, externalRef }) => {
    const callbackUrl = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
      .replace("{type}", type)
      .replace("{callbackName}", "callback-result");
    return postWithJson({
      url: callbackUrl,
      headers: { "Content-Type": "application/json", ...createBasicAuthHeader(basicAuth) },
      body: { application_id: externalRef, result: "ok" }
    });
  };

  const getApplication = async ({ type, accessToken, applicationId }) => {
    const res = await get({
      url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}`,
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    expect(res.status).toBe(200);
    expect(res.data.list.length).toBe(1);
    return res.data.list[0];
  };

  const countResults = async ({ type, accessToken, applicationId }) => {
    const res = await get({
      url: serverConfig.identityVerificationResultResourceOwnerEndpoint + `?application_id=${applicationId}&type=${type}`,
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    expect(res.status).toBe(200);
    return res.data.list.length;
  };

  // {type} is a uuid, so {type}_approved is unique to this test and cannot collide with the approved
  // events of other applications living in the same shared tenant.
  const countApprovedEvents = async ({ type }) => {
    const res = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      params: { event_type: `${type}_approved`, limit: 100 }
    });
    expect(res.status).toBe(200);
    return res.data.total_count;
  };

  it("keeps lifecycle events and verification result flat when a successful callback-result is re-run on an already-approved application", async () => {
    const { accessToken } = await createTestUser();
    const type = uuidv4();
    const basicAuth = { username: "cb_terminal_dup_user", password: "cb_terminal_dup_password001" };
    await registerConfiguration(buildConfig({ configId: uuidv4(), type, basicAuth }));

    const externalRef = uuidv4();
    const applicationId = await apply({ type, accessToken, externalRef });

    // 1st callback-result → newly terminal (approved): fires the lifecycle event + registers result.
    const first = await sendCallbackResult({ type, basicAuth, externalRef });
    expect(first.status).toBe(200);

    const afterFirst = await getApplication({ type, accessToken, applicationId });
    expect(afterFirst.status).toBe("approved");

    await sleep(2000);
    expect(await countApprovedEvents({ type })).toBe(1);
    expect(await countResults({ type, accessToken, applicationId })).toBe(1);

    // 2nd callback-result (identical body) → pre_hook passes (allow_retry: true) and the callback
    // executes again, but the application is already terminal (#1617 absorbing). The EntryService
    // guard must skip the terminal side-effects.
    const second = await sendCallbackResult({ type, basicAuth, externalRef });
    expect(second.status).toBe(200);

    const afterSecond = await getApplication({ type, accessToken, applicationId });
    // status unchanged (absorbed)...
    expect(afterSecond.status).toBe("approved");
    // ...yet the re-run really did execute — proving the flat counts below are the terminal guard,
    // not a rejected re-submission.
    expect(afterSecond.processes["callback-result"].success_count).toBe(2);

    await sleep(2000);
    // the lifecycle event was NOT re-published and the result was NOT re-registered.
    expect(await countApprovedEvents({ type })).toBe(1);
    expect(await countResults({ type, accessToken, applicationId })).toBe(1);
  });
});
