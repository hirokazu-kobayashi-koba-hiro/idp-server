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
 * Identity Verification User Attribute Update
 *
 * result セクションの拡張機能を検証する:
 * - user_claims_mapping_rules: 承認時に標準クレーム（family_name 等）を更新
 * - custom_properties_mapping_rules: 承認時に custom_properties をキー単位マージ
 * - user_status: 承認時のユーザーステータス遷移を設定で制御
 *   - 省略時: IDENTITY_VERIFIED（後方互換）
 *   - "KEEP": 現状維持（遷移なし）
 */
describe("Identity Verification User Attribute Update", () => {
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

  const buildConfiguration = ({ configId, type, result }) => ({
    "id": configId,
    "type": type,
    "attributes": {
      "enabled": true
    },
    "common": {
      "auth_type": "none"
    },
    "processes": {
      "apply": {
        "request": {
          "schema": {
            "type": "object",
            "required": ["last_name", "first_name"],
            "properties": {
              "last_name": { "type": "string" },
              "first_name": { "type": "string" },
              "kyc_level": { "type": "string" },
              "address": {
                "type": "object",
                "properties": {
                  "street_address": { "type": "string" },
                  "locality": { "type": "string" },
                  "region": { "type": "string" },
                  "postal_code": { "type": "string" },
                  "country": { "type": "string" }
                }
              }
            }
          }
        },
        "execution": {
          "type": "no_action"
        },
        "store": {
          "application_details_mapping_rules": [
            {
              "from": "$.request_body",
              "to": "*"
            }
          ]
        }
      },
      "evaluate-result": {
        "execution": {
          "type": "no_action"
        },
        "transition": {
          "approved": {
            "any_of": [
              [
                {
                  "path": "$.request_body.approved",
                  "type": "boolean",
                  "operation": "eq",
                  "value": true
                }
              ]
            ]
          },
          "rejected": {
            "any_of": [
              [
                {
                  "path": "$.request_body.rejected",
                  "type": "boolean",
                  "operation": "eq",
                  "value": true
                }
              ]
            ]
          }
        }
      }
    },
    "result": result
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

  const applyAndApprove = async ({ type, accessToken, applyBody, evaluation }) => {
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");

    const applyResponse = await postWithJson({
      url: applyUrl,
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`
      },
      body: applyBody
    });
    console.log("Apply response:", applyResponse.status, JSON.stringify(applyResponse.data, null, 2));
    expect(applyResponse.status).toBe(200);

    const applicationId = applyResponse.data.id;

    const evaluateUrl = serverConfig.identityVerificationApplicationsEvaluateResultEndpoint
      .replace("{type}", type)
      .replace("{id}", applicationId);

    const evaluateResponse = await postWithJson({
      url: evaluateUrl,
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`
      },
      body: evaluation || {
        "approved": true,
        "rejected": false
      }
    });
    console.log("Evaluate result response:", evaluateResponse.status, JSON.stringify(evaluateResponse.data, null, 2));
    expect(evaluateResponse.status).toBe(200);

    return applicationId;
  };

  const getUser = async (sub) => {
    const response = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${sub}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` }
    });
    expect(response.status).toBe(200);
    return response.data;
  };

  it("updates standard claims and custom_properties, transits to IDENTITY_VERIFIED by default", async () => {
    const { user, accessToken } = await createTestUser();

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "verified_claims_mapping_rules": [
            {
              "static_value": "eidas",
              "to": "verification.trust_framework"
            },
            {
              "from": "$.application.application_details.last_name",
              "to": "claims.family_name"
            }
          ],
          "user_claims_mapping_rules": [
            {
              "from": "$.application.application_details.last_name",
              "to": "family_name"
            },
            {
              "from": "$.application.application_details.first_name",
              "to": "given_name"
            }
          ],
          "custom_properties_mapping_rules": [
            {
              "from": "$.application.application_details.kyc_level",
              "to": "kyc_level"
            }
          ]
        }
      })
    );

    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "kyc_level": "gold"
      }
    });

    const updatedUser = await getUser(user.sub);
    console.log("Updated user:", JSON.stringify(updatedUser, null, 2));

    // user_status 省略時は従来通り IDENTITY_VERIFIED に遷移する（後方互換）
    expect(updatedUser).toHaveProperty("status", "IDENTITY_VERIFIED");

    // user_claims_mapping_rules による標準クレーム更新
    expect(updatedUser).toHaveProperty("family_name", "Yamada");
    expect(updatedUser).toHaveProperty("given_name", "Hanako");

    // custom_properties_mapping_rules による業務属性更新
    expect(updatedUser).toHaveProperty("custom_properties");
    expect(updatedUser.custom_properties).toHaveProperty("kyc_level", "gold");

    // 既存の verified_claims_mapping_rules も従来通り動作する
    expect(updatedUser).toHaveProperty("verified_claims");
    expect(updatedUser.verified_claims.claims).toHaveProperty("family_name", "Yamada");
    expect(updatedUser.verified_claims.verification).toHaveProperty("trust_framework", "eidas");
  });

  it("keeps current user status when user_status is KEEP", async () => {
    const { user, accessToken } = await createTestUser();

    const beforeUser = await getUser(user.sub);
    const beforeStatus = beforeUser.status;
    console.log("User status before approval:", beforeStatus);

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "custom_properties_mapping_rules": [
            {
              "from": "$.application.application_details.kyc_level",
              "to": "kyc_level"
            }
          ],
          "user_status": "KEEP"
        }
      })
    );

    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Suzuki",
        "first_name": "Taro",
        "kyc_level": "bronze"
      }
    });

    const updatedUser = await getUser(user.sub);
    console.log("Updated user:", JSON.stringify(updatedUser, null, 2));

    // user_status: KEEP はステータス現状維持
    expect(updatedUser).toHaveProperty("status", beforeStatus);

    // custom_properties は更新される
    expect(updatedUser.custom_properties).toHaveProperty("kyc_level", "bronze");

    // user_claims_mapping_rules なしのため標準クレームは変わらない
    expect(updatedUser.family_name).toBe(beforeUser.family_name);
  });

  it("transits to configured user_status", async () => {
    const { user, accessToken } = await createTestUser();

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "custom_properties_mapping_rules": [
            {
              "from": "$.application.application_details.kyc_level",
              "to": "kyc_level"
            }
          ],
          "user_status": "IDENTITY_VERIFICATION_REQUIRED"
        }
      })
    );

    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Sato",
        "first_name": "Jiro",
        "kyc_level": "silver"
      }
    });

    const updatedUser = await getUser(user.sub);
    console.log("Updated user:", JSON.stringify(updatedUser, null, 2));

    expect(updatedUser).toHaveProperty("status", "IDENTITY_VERIFICATION_REQUIRED");
    expect(updatedUser.custom_properties).toHaveProperty("kyc_level", "silver");
  });

  it("re-approval overwrites attributes and keeps IDENTITY_VERIFIED without transition error", async () => {
    const { user, accessToken } = await createTestUser();

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "user_claims_mapping_rules": [
            {
              "from": "$.application.application_details.last_name",
              "to": "family_name"
            }
          ],
          "custom_properties_mapping_rules": [
            {
              "from": "$.application.application_details.kyc_level",
              "to": "kyc_level"
            }
          ]
        }
      })
    );

    // 1回目の承認: REGISTERED/FEDERATED -> IDENTITY_VERIFIED
    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "kyc_level": "gold"
      }
    });

    const firstApproved = await getUser(user.sub);
    expect(firstApproved).toHaveProperty("status", "IDENTITY_VERIFIED");
    expect(firstApproved.custom_properties).toHaveProperty("kyc_level", "gold");

    // 2回目の承認: IDENTITY_VERIFIED -> IDENTITY_VERIFIED（自己遷移でもエラーにならない）
    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada-Sato",
        "first_name": "Hanako",
        "kyc_level": "platinum"
      }
    });

    const secondApproved = await getUser(user.sub);
    console.log("User after re-approval:", JSON.stringify(secondApproved, null, 2));

    expect(secondApproved).toHaveProperty("status", "IDENTITY_VERIFIED");
    // 同一キーは上書き
    expect(secondApproved.custom_properties).toHaveProperty("kyc_level", "platinum");
    expect(secondApproved).toHaveProperty("family_name", "Yamada-Sato");
  });

  it("accumulates attributes across multiple verification types (eKYC then membership rank)", async () => {
    const { user, accessToken } = await createTestUser();

    // type A: eKYC風（標準クレーム + verified_claims + デフォルトステータス遷移）
    const ekycConfigId = uuidv4();
    const ekycType = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId: ekycConfigId,
        type: ekycType,
        result: {
          "verified_claims_mapping_rules": [
            {
              "static_value": "eidas",
              "to": "verification.trust_framework"
            },
            {
              "from": "$.application.application_details.last_name",
              "to": "claims.family_name"
            }
          ],
          "user_claims_mapping_rules": [
            {
              "from": "$.application.application_details.last_name",
              "to": "family_name"
            }
          ],
          "custom_properties_mapping_rules": [
            {
              "from": "$.application.application_details.kyc_level",
              "to": "kyc_level"
            }
          ]
        }
      })
    );

    // type B: 会員ランク認定（custom_properties のみ + ステータス現状維持）
    const rankConfigId = uuidv4();
    const rankType = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId: rankConfigId,
        type: rankType,
        result: {
          "custom_properties_mapping_rules": [
            {
              "from": "$.application.application_details.kyc_level",
              "to": "membership_rank"
            }
          ],
          "user_status": "KEEP"
        }
      })
    );

    await applyAndApprove({
      type: ekycType,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "kyc_level": "gold"
      }
    });

    await applyAndApprove({
      type: rankType,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "kyc_level": "platinum"
      }
    });

    const updatedUser = await getUser(user.sub);
    console.log("User after both approvals:", JSON.stringify(updatedUser, null, 2));

    // type B は KEEP のため type A で遷移した IDENTITY_VERIFIED が維持される
    expect(updatedUser).toHaveProperty("status", "IDENTITY_VERIFIED");

    // custom_properties はキー単位マージで両方残る
    expect(updatedUser.custom_properties).toHaveProperty("kyc_level", "gold");
    expect(updatedUser.custom_properties).toHaveProperty("membership_rank", "platinum");

    // type A の標準クレーム・verified_claims は type B の承認後も保持される
    expect(updatedUser).toHaveProperty("family_name", "Yamada");
    expect(updatedUser.verified_claims.claims).toHaveProperty("family_name", "Yamada");
  });

  it("applies static_value, functions and nested object mapping", async () => {
    const { user, accessToken } = await createTestUser();

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "user_claims_mapping_rules": [
            {
              "from": "$.application.application_details.first_name",
              "to": "nickname",
              "functions": [
                { "name": "case", "args": { "mode": "upper" } }
              ]
            },
            {
              "from": "$.application.application_details.address",
              "to": "address"
            }
          ],
          "custom_properties_mapping_rules": [
            {
              "from": "$.application.application_details.kyc_level",
              "to": "kyc_level",
              "functions": [
                { "name": "case", "args": { "mode": "upper" } }
              ]
            },
            {
              "static_value": true,
              "to": "verified_member"
            },
            {
              "static_value": 3,
              "to": "assurance_level"
            }
          ]
        }
      })
    );

    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "kyc_level": "gold",
        "address": {
          "street_address": "1-2-3 Chiyoda",
          "locality": "Chiyoda-ku",
          "region": "Tokyo",
          "postal_code": "1000001",
          "country": "JP"
        }
      }
    });

    const updatedUser = await getUser(user.sub);
    console.log("Updated user:", JSON.stringify(updatedUser, null, 2));

    // functions チェーン適用（case upper）
    expect(updatedUser).toHaveProperty("nickname", "HANAKO");
    expect(updatedUser.custom_properties).toHaveProperty("kyc_level", "GOLD");

    // static_value の型保持（boolean / number）
    expect(updatedUser.custom_properties).toHaveProperty("verified_member", true);
    expect(updatedUser.custom_properties).toHaveProperty("assurance_level", 3);

    // ネストオブジェクト（address）の更新
    expect(updatedUser).toHaveProperty("address");
    expect(updatedUser.address).toHaveProperty("locality", "Chiyoda-ku");
    expect(updatedUser.address).toHaveProperty("country", "JP");
  });

  it("does not update user attributes when application is rejected", async () => {
    const { user, accessToken } = await createTestUser();

    const beforeUser = await getUser(user.sub);

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "user_claims_mapping_rules": [
            {
              "from": "$.application.application_details.last_name",
              "to": "family_name"
            }
          ],
          "custom_properties_mapping_rules": [
            {
              "from": "$.application.application_details.kyc_level",
              "to": "kyc_level"
            }
          ]
        }
      })
    );

    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "ShouldNotApply",
        "first_name": "Rejected",
        "kyc_level": "gold"
      },
      evaluation: {
        "approved": false,
        "rejected": true
      }
    });

    const updatedUser = await getUser(user.sub);
    console.log("User after rejection:", JSON.stringify(updatedUser, null, 2));

    // 拒否時はステータス・標準クレーム・custom_properties いずれも更新されない
    expect(updatedUser.status).toBe(beforeUser.status);
    expect(updatedUser.family_name).toBe(beforeUser.family_name);
    if (updatedUser.custom_properties) {
      expect(updatedUser.custom_properties).not.toHaveProperty("kyc_level");
    }
  });
});
