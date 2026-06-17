import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig,
} from "../../testConfig";
import { createFederatedUser } from "../../../user";
import { v4 as uuidv4 } from "uuid";

/**
 * #1637: store の `application_details_update_policy` を検証する。
 *
 * 複数 process が同じ親キー（`progress`）配下の別サブキーを書くとき、
 *  - 既定（`merge` = トップレベル putAll）: 後続 process が親キーごと置換し、先行サブキーが消える
 *  - `deep_merge`: Map を再帰マージし、サブキーが共存する
 *
 * apply（progress.opening を作成）→ review-investment（progress.investment を追記）の 2 process で、
 * policy 別に application_details.progress の中身がどうなるかを確認する。
 */
describe("Identity Verification - application_details deep_merge (#1637)", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  let userAccessToken;
  const createdConfigIds = [];

  beforeAll(async () => {
    const orgAuthResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      scope: "org-management account management",
    });
    expect(orgAuthResponse.status).toBe(200);
    orgAccessToken = orgAuthResponse.data.access_token;

    const { accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope:
        "openid profile phone email identity_verification_application " +
        clientSecretPostClient.identityVerificationScope,
    });
    userAccessToken = accessToken;
  });

  afterAll(async () => {
    for (const configId of createdConfigIds) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
      });
    }
  });

  // apply writes progress.opening; review-investment writes progress.investment under the given
  // update policy. No carry-forward ($.application.application_details) mapping is used, so the
  // sibling subkey survives only when the policy preserves it.
  const registerConfiguration = async (type, investmentUpdatePolicy) => {
    const configId = uuidv4();
    const investmentStore = {
      application_details_mapping_rules: [
        { from: "$.request_body.investment_status", to: "progress.investment" },
      ],
    };
    if (investmentUpdatePolicy) {
      investmentStore.application_details_update_policy = investmentUpdatePolicy;
    }
    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
        "Content-Type": "application/json",
      },
      body: {
        id: configId,
        type,
        attributes: { enabled: true },
        common: { auth_type: "none" },
        processes: {
          apply: {
            request: {
              schema: {
                type: "object",
                required: ["opening_status"],
                properties: { opening_status: { type: "string" } },
              },
            },
            execution: { type: "no_action" },
            store: {
              application_details_mapping_rules: [
                { from: "$.request_body.opening_status", to: "progress.opening" },
              ],
            },
          },
          "review-investment": {
            request: {
              schema: {
                type: "object",
                required: ["investment_status"],
                properties: { investment_status: { type: "string" } },
              },
            },
            execution: { type: "no_action" },
            store: investmentStore,
          },
        },
      },
    });
    expect(response.status).toBe(201);
    createdConfigIds.push(configId);
    return type;
  };

  const apply = async (type, body) => {
    const url = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");
    const response = await postWithJson({
      url,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${userAccessToken}` },
      body,
    });
    expect(response.status).toBe(200);
    return response.data.id;
  };

  const runProcess = async (type, applicationId, process, body) => {
    const url = serverConfig.identityVerificationProcessEndpoint
      .replace("{type}", type)
      .replace("{id}", applicationId)
      .replace("{process}", process);
    const response = await postWithJson({
      url,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${userAccessToken}` },
      body,
    });
    expect(response.status).toBe(200);
    return response;
  };

  const getApplicationDetails = async (type, applicationId) => {
    const response = await get({
      url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}`,
      headers: { Authorization: `Bearer ${userAccessToken}` },
    });
    expect(response.status).toBe(200);
    expect(response.data.list.length).toBe(1);
    console.log("application_details:", JSON.stringify(response.data.list[0].application_details, null, 2));
    return response.data.list[0].application_details;
  };

  it("deep_merge: sibling subkeys under a shared parent are preserved across processes", async () => {
    const type = await registerConfiguration(uuidv4(), "deep_merge");

    const applicationId = await apply(type, { opening_status: "approved" });
    await runProcess(type, applicationId, "review-investment", { investment_status: "approved" });

    const details = await getApplicationDetails(type, applicationId);
    expect(details.progress.opening).toEqual("approved");
    expect(details.progress.investment).toEqual("approved");
  });

  it("default (merge): the shared parent is replaced, dropping the earlier sibling subkey", async () => {
    // Documents the pre-#1637 behavior and guards the default path: without deep_merge the second
    // process's putAll replaces `progress` wholesale, so progress.opening is lost.
    const type = await registerConfiguration(uuidv4(), undefined);

    const applicationId = await apply(type, { opening_status: "approved" });
    await runProcess(type, applicationId, "review-investment", { investment_status: "approved" });

    const details = await getApplicationDetails(type, applicationId);
    expect(details.progress.investment).toEqual("approved");
    expect(details.progress.opening).toBeUndefined();
  });
});
