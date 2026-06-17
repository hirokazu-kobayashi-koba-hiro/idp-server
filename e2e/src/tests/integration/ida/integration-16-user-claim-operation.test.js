import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig,
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";

/**
 * #1626: pre_hook `user_claim` の `operation` 拡張を実フローで検証する。
 *
 * `user_claim` ルールに `operation` を指定すると、リクエスト値（target）とユーザー属性（expected）を
 * ConditionOperation で比較できる。`operation` 省略時は従来どおり `eq`（完全一致, 後方互換）。
 *
 * テストユーザーには FIDO-UAF 登録で authentication_devices が 1 件付与される。
 * `$.authentication_devices[*].id`（配列）に対し、
 *  - in: リクエストの id が保有デバイスに含まれる → 200
 *  - in: 未保有の id を指定 → 400 pre_hook_validation_failed（サイレント成功させない）
 *  - operation 省略（eq）: 完全一致 → 200（後方互換）
 */
describe("Identity Verification - user_claim operation (#1626)", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  let userAccessToken;
  let deviceId;
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

    const { authenticationDeviceId } = await registerFidoUaf({ accessToken });
    expect(authenticationDeviceId).toBeDefined();
    deviceId = authenticationDeviceId;
  });

  afterAll(async () => {
    for (const configId of createdConfigIds) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
      });
    }
  });

  // apply process whose pre_hook runs a user_claim verification with the given rules.
  const registerConfiguration = async (type, rules) => {
    const configId = uuidv4();
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
                required: ["id"],
                properties: { id: { type: "string" } },
              },
            },
            pre_hook: {
              verifications: [
                { type: "user_claim", details: { verification_parameters: rules } },
              ],
            },
            execution: { type: "no_action" },
            store: {
              application_details_mapping_rules: [{ from: "$.request_body", to: "*" }],
            },
          },
        },
      },
    });
    expect(response.status).toBe(201);
    createdConfigIds.push(configId);
  };

  const apply = async (type, body) => {
    const url = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");
    const response = await postWithJson({
      url,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`,
      },
      body,
    });
    console.log("apply response:", response.status, JSON.stringify(response.data, null, 2));
    return response;
  };

  it("in: accepts a request id that is a member of the user's authentication devices", async () => {
    const type = uuidv4();
    await registerConfiguration(type, [
      {
        operation: "in",
        request_json_path: "$.id",
        user_claim_json_path: "$.authentication_devices[*].id",
      },
    ]);

    const response = await apply(type, { id: deviceId });

    expect(response.status).toBe(200);
  });

  it("in: rejects a request id the user does not hold with 400 pre_hook_validation_failed", async () => {
    const type = uuidv4();
    await registerConfiguration(type, [
      {
        operation: "in",
        request_json_path: "$.id",
        user_claim_json_path: "$.authentication_devices[*].id",
      },
    ]);

    const response = await apply(type, { id: "00000000-0000-0000-0000-000000000000" });

    expect(response.status).toBe(400);
    expect(response.data).toHaveProperty("error", "pre_hook_validation_failed");
  });

  it("eq (operation omitted): exact match is honored (backward compatible)", async () => {
    const type = uuidv4();
    await registerConfiguration(type, [
      {
        // no operation -> defaults to eq -> Objects.equals against the first device id (scalar path)
        request_json_path: "$.id",
        user_claim_json_path: "$.authentication_devices[0].id",
      },
    ]);

    const matched = await apply(type, { id: deviceId });
    expect(matched.status).toBe(200);

    const mismatched = await apply(type, { id: "00000000-0000-0000-0000-000000000000" });
    expect(mismatched.status).toBe(400);
    expect(mismatched.data).toHaveProperty("error", "pre_hook_validation_failed");
  });
});
