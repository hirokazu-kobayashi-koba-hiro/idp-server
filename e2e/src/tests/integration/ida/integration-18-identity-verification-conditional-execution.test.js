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
 * #1186: pre_hook verification の Conditional Execution（条件付き実行）を実フローで検証する。
 *
 * verification config に `condition`（platform の {@code ConditionSpec}）を付けると、condition が
 * 真のときだけ検証が実行され、偽のときはスキップされてフローが素通りする
 * （IdentityVerificationApplicationRequestVerifiers が `condition.evaluate()` false で continue）。
 *
 * 各テストは「実行されると必ず失敗する user_claim 検証」（保有しない device id を `in` で要求）を
 * condition でゲートし、condition の真偽で 400(実行→失敗) ↔ 200(スキップ→素通り) が反転することを
 * 確認する。condition は apply リクエストボディ（`$.request_body.*`）で駆動する。
 */
describe("Identity Verification - conditional execution of pre_hook verification (#1186)", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  // applying with this id makes the gated user_claim verification FAIL whenever it actually runs
  // (the id is never a member of the user's authentication_devices).
  const BOGUS_ID = "00000000-0000-0000-0000-000000000000";

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

    // gives the user >=1 authentication device, so the bogus id reliably fails the `in` check.
    await registerFidoUaf({ accessToken });
  });

  afterAll(async () => {
    for (const configId of createdConfigIds) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
      });
    }
  });

  // registers a process whose pre_hook runs an always-failing user_claim verification gated by
  // the given condition.
  const registerConfiguration = async (type, condition) => {
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
                properties: {
                  id: { type: "string" },
                  amount: { type: "integer" },
                  role: { type: "string" },
                  country: { type: "string" },
                },
              },
            },
            pre_hook: {
              verifications: [
                {
                  type: "user_claim",
                  details: {
                    verification_parameters: [
                      {
                        operation: "in",
                        request_json_path: "$.id",
                        user_claim_json_path: "$.authentication_devices[*].id",
                      },
                    ],
                  },
                  condition,
                },
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
    return type;
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

  const expectVerificationRan = (response) => {
    expect(response.status).toBe(400);
    expect(response.data).toHaveProperty("error", "pre_hook_validation_failed");
  };

  it("gte: condition true → verification runs (rejects with pre_hook_validation_failed)", async () => {
    const type = await registerConfiguration(uuidv4(), {
      operation: "gte",
      path: "$.request_body.amount",
      value: 100000,
    });

    expectVerificationRan(await apply(type, { id: BOGUS_ID, amount: 200000 }));
  });

  it("gte: condition false → verification skipped → 200 (flow proceeds)", async () => {
    // The core of conditional execution: the same failing verification is skipped because the
    // condition does not hold, so the application is accepted.
    const type = await registerConfiguration(uuidv4(), {
      operation: "gte",
      path: "$.request_body.amount",
      value: 100000,
    });

    const response = await apply(type, { id: BOGUS_ID, amount: 50000 });
    expect(response.status).toBe(200);
  });

  it("eq: condition true → verification runs", async () => {
    const type = await registerConfiguration(uuidv4(), {
      operation: "eq",
      path: "$.request_body.role",
      value: "admin",
    });

    expectVerificationRan(await apply(type, { id: BOGUS_ID, role: "admin" }));
  });

  it("in: condition true → verification runs", async () => {
    const type = await registerConfiguration(uuidv4(), {
      operation: "in",
      path: "$.request_body.country",
      value: ["US", "EU", "JP"],
    });

    expectVerificationRan(await apply(type, { id: BOGUS_ID, country: "JP" }));
  });

  it("allOf: all nested conditions true → runs; one false → skipped", async () => {
    const condition = {
      operation: "allOf",
      value: [
        { operation: "gte", path: "$.request_body.amount", value: 100000 },
        { operation: "eq", path: "$.request_body.role", value: "admin" },
      ],
    };
    const type = await registerConfiguration(uuidv4(), condition);

    expectVerificationRan(await apply(type, { id: BOGUS_ID, amount: 200000, role: "admin" }));

    const oneFalse = await apply(type, { id: BOGUS_ID, amount: 200000, role: "editor" });
    expect(oneFalse.status).toBe(200);
  });

  it("anyOf: one nested condition true → runs; none true → skipped", async () => {
    const condition = {
      operation: "anyOf",
      value: [
        { operation: "eq", path: "$.request_body.role", value: "admin" },
        { operation: "eq", path: "$.request_body.role", value: "editor" },
      ],
    };
    const type = await registerConfiguration(uuidv4(), condition);

    expectVerificationRan(await apply(type, { id: BOGUS_ID, role: "editor" }));

    const noneTrue = await apply(type, { id: BOGUS_ID, role: "viewer" });
    expect(noneTrue.status).toBe(200);
  });
});
