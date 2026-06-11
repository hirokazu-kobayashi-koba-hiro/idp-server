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
 * Identity Verification Process Variations
 *
 * プロセス構造は自由に定義できることを前提に、ユーザー属性更新（result セクション拡張）を
 * 定型外のプロセス構成で検証する:
 *
 * 1. 単一プロセス即時承認: 1プロセスで申込みと同時に承認しユーザー属性を更新（会員ランク登録など）
 * 2. 多段プロセス: apply -> upload-document -> final-review。
 *    success_count 条件で全ステップ完了を強制し、承認まではユーザーが更新されないことを確認
 * 3. コールバック承認: 外部サービスからの審査結果通知（Basic認証）で承認しユーザー属性を更新
 */
describe("Identity Verification Process Variations", () => {
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
    console.log("Configuration creation response:", response.status, JSON.stringify(response.data, null, 2));
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

  const getUser = async (sub) => {
    const response = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${sub}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` }
    });
    expect(response.status).toBe(200);
    return response.data;
  };

  it("single process approves immediately and updates custom_properties (membership rank registration)", async () => {
    const { user, accessToken } = await createTestUser();

    const beforeUser = await getUser(user.sub);

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration({
      "id": configId,
      "type": type,
      "attributes": { "enabled": true },
      "common": { "auth_type": "none" },
      "processes": {
        "register-rank": {
          "request": {
            "schema": {
              "type": "object",
              "required": ["rank"],
              "properties": {
                "rank": { "type": "string" },
                "point_balance": { "type": "integer" }
              }
            }
          },
          "execution": { "type": "no_action" },
          "store": {
            "application_details_mapping_rules": [
              { "from": "$.request_body", "to": "*" }
            ]
          },
          "transition": {
            "approved": {
              "any_of": [
                [
                  {
                    "path": "$.request_body.rank",
                    "type": "string",
                    "operation": "exists",
                    "value": true
                  }
                ]
              ]
            }
          }
        }
      },
      "result": {
        // 単一プロセス即時承認のため、申込みリクエストから直接マッピング
        "custom_properties_mapping_rules": [
          { "from": "$.request_body.rank", "to": "membership_rank" },
          { "from": "$.request_body.point_balance", "to": "point_balance" }
        ],
        "user_status": "KEEP"
      }
    });

    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "register-rank");

    const response = await callProcess({
      url: applyUrl,
      accessToken,
      body: { "rank": "gold", "point_balance": 1200 }
    });
    expect(response.status).toBe(200);

    const updatedUser = await getUser(user.sub);
    console.log("Updated user:", JSON.stringify(updatedUser, null, 2));

    // 1回のAPI呼び出しで即時承認・属性反映
    expect(updatedUser.custom_properties).toHaveProperty("membership_rank", "gold");
    expect(updatedUser.custom_properties).toHaveProperty("point_balance", 1200);
    // KEEP のためステータスは変わらない
    expect(updatedUser.status).toBe(beforeUser.status);
  });

  it("multi-step process updates user only after final approval with success_count conditions", async () => {
    const { user, accessToken } = await createTestUser();

    const beforeUser = await getUser(user.sub);

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration({
      "id": configId,
      "type": type,
      "attributes": { "enabled": true },
      "common": { "auth_type": "none" },
      "processes": {
        "apply": {
          "request": {
            "schema": {
              "type": "object",
              "required": ["last_name", "first_name"],
              "properties": {
                "last_name": { "type": "string" },
                "first_name": { "type": "string" }
              }
            }
          },
          "execution": { "type": "no_action" },
          "store": {
            "application_details_mapping_rules": [
              { "from": "$.request_body", "to": "*" }
            ]
          }
        },
        "upload-document": {
          "request": {
            "schema": {
              "type": "object",
              "required": ["document_type"],
              "properties": {
                "document_type": { "type": "string" }
              }
            }
          },
          "execution": { "type": "no_action" },
          "store": {
            "application_details_mapping_rules": [
              { "from": "$.application.application_details", "to": "*" },
              { "from": "$.request_body.document_type", "to": "document_type" }
            ]
          }
        },
        "final-review": {
          "execution": { "type": "no_action" },
          "transition": {
            "approved": {
              "any_of": [
                [
                  {
                    "path": "$.request_body.approved",
                    "type": "boolean",
                    "operation": "eq",
                    "value": true
                  },
                  {
                    "path": "$.application.processes.apply.success_count",
                    "type": "integer",
                    "operation": "gte",
                    "value": 1
                  },
                  {
                    "path": "$.application.processes.upload-document.success_count",
                    "type": "integer",
                    "operation": "gte",
                    "value": 1
                  }
                ]
              ]
            }
          }
        }
      },
      "result": {
        "user_claims_mapping_rules": [
          { "from": "$.application.application_details.last_name", "to": "family_name" }
        ],
        // 複数プロセスで蓄積した application_details からマッピング
        "custom_properties_mapping_rules": [
          { "from": "$.application.application_details.document_type", "to": "verified_document_type" }
        ]
      }
    });

    // Step 1: apply
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");
    const applyResponse = await callProcess({
      url: applyUrl,
      accessToken,
      body: { "last_name": "Tanaka", "first_name": "Ichiro" }
    });
    expect(applyResponse.status).toBe(200);
    const applicationId = applyResponse.data.id;

    // 申込み直後はユーザーは更新されない
    let currentUser = await getUser(user.sub);
    expect(currentUser.status).toBe(beforeUser.status);
    expect(currentUser.family_name).toBe(beforeUser.family_name);

    // Step 2: upload-document
    const uploadUrl = serverConfig.identityVerificationProcessEndpoint
      .replace("{type}", type)
      .replace("{id}", applicationId)
      .replace("{process}", "upload-document");
    const uploadResponse = await callProcess({
      url: uploadUrl,
      accessToken,
      body: { "document_type": "driver_license" }
    });
    expect(uploadResponse.status).toBe(200);

    // 中間プロセス完了時点でもまだ更新されない
    currentUser = await getUser(user.sub);
    expect(currentUser.status).toBe(beforeUser.status);

    // Step 3: final-review（全ステップの success_count 条件を満たして承認）
    const reviewUrl = serverConfig.identityVerificationProcessEndpoint
      .replace("{type}", type)
      .replace("{id}", applicationId)
      .replace("{process}", "final-review");
    const reviewResponse = await callProcess({
      url: reviewUrl,
      accessToken,
      body: { "approved": true }
    });
    expect(reviewResponse.status).toBe(200);

    const updatedUser = await getUser(user.sub);
    console.log("Updated user:", JSON.stringify(updatedUser, null, 2));

    // 最終承認で初めてユーザーが更新される
    expect(updatedUser).toHaveProperty("status", "IDENTITY_VERIFIED");
    expect(updatedUser).toHaveProperty("family_name", "Tanaka");
    // upload-document プロセスで蓄積した application_details からの反映
    expect(updatedUser.custom_properties).toHaveProperty("verified_document_type", "driver_license");
  });

  it("callback from external service approves and updates user attributes", async () => {
    const { user, accessToken } = await createTestUser();

    const configId = uuidv4();
    const type = uuidv4();
    const basicAuth = { username: "callback_user", password: "callback_password001" };

    await registerConfiguration({
      "id": configId,
      "type": type,
      "attributes": { "enabled": true },
      "common": {
        "auth_type": "none",
        "callback_application_id_param": "application_id"
      },
      "processes": {
        "apply": {
          "request": {
            "schema": {
              "type": "object",
              "required": ["last_name", "first_name", "external_ref"],
              "properties": {
                "last_name": { "type": "string" },
                "first_name": { "type": "string" },
                "external_ref": { "type": "string" }
              }
            }
          },
          "execution": { "type": "no_action" },
          "store": {
            "application_details_mapping_rules": [
              { "from": "$.request_body", "to": "*" },
              // コールバックの引き当てキー（application_details ->> 'application_id'）
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
                "result": { "type": "string" },
                "rank": { "type": "string" }
              }
            }
          },
          "transition": {
            "approved": {
              "any_of": [
                [
                  {
                    "path": "$.request_body.result",
                    "type": "string",
                    "operation": "eq",
                    "value": "ok"
                  }
                ]
              ]
            }
          }
        }
      },
      "result": {
        // 申込み時に store した値（標準クレーム）とコールバックボディ（業務属性）の両方からマッピング
        "user_claims_mapping_rules": [
          { "from": "$.application.application_details.last_name", "to": "family_name" }
        ],
        "custom_properties_mapping_rules": [
          { "from": "$.request_body.rank", "to": "membership_rank" }
        ]
      }
    });

    // Step 1: エンドユーザーが申込み
    const externalRef = uuidv4();
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");
    const applyResponse = await callProcess({
      url: applyUrl,
      accessToken,
      body: { "last_name": "Watanabe", "first_name": "Saburo", "external_ref": externalRef }
    });
    expect(applyResponse.status).toBe(200);

    // Step 2: 外部サービスがコールバックで審査結果を通知（Basic認証）
    const callbackUrl = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
      .replace("{type}", type)
      .replace("{callbackName}", "callback-result");
    const callbackResponse = await callProcess({
      url: callbackUrl,
      headers: {
        "Content-Type": "application/json",
        ...createBasicAuthHeader(basicAuth)
      },
      body: {
        "application_id": externalRef,
        "result": "ok",
        "rank": "platinum"
      }
    });
    expect(callbackResponse.status).toBe(200);

    const updatedUser = await getUser(user.sub);
    console.log("Updated user:", JSON.stringify(updatedUser, null, 2));

    // コールバック承認経路でも標準クレーム・custom_properties・ステータスが更新される
    expect(updatedUser).toHaveProperty("status", "IDENTITY_VERIFIED");
    expect(updatedUser).toHaveProperty("family_name", "Watanabe");
    expect(updatedUser.custom_properties).toHaveProperty("membership_rank", "platinum");
  });
});
