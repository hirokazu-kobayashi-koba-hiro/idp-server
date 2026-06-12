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
              "risk_flag": { "type": "string" },
              "email_address": { "type": "string" },
              "extra": { "type": "object" },
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

  const applyAndApprove = async ({ type, accessToken, applyBody, evaluation, expectedEvaluateStatus = 200 }) => {
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
    expect(evaluateResponse.status).toBe(expectedEvaluateStatus);

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

  it("deep_merge preserves verified_claims from previous verification types", async () => {
    const { user, accessToken } = await createTestUser();

    // type A: eKYC（氏名を検証、デフォルトポリシー）
    const ekycConfigId = uuidv4();
    const ekycType = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId: ekycConfigId,
        type: ekycType,
        result: {
          "verified_claims_mapping_rules": [
            { "static_value": "eidas", "to": "verification.trust_framework" },
            { "from": "$.application.application_details.last_name", "to": "claims.family_name" }
          ]
        }
      })
    );
    await applyAndApprove({
      type: ekycType,
      accessToken,
      applyBody: { "last_name": "Yamada", "first_name": "Hanako", "kyc_level": "gold" }
    });

    // type B: 収入確認（claims を deep_merge で追加）
    const incomeConfigId = uuidv4();
    const incomeType = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId: incomeConfigId,
        type: incomeType,
        result: {
          "verified_claims_mapping_rules": [
            { "from": "$.application.application_details.kyc_level", "to": "claims.income_class" }
          ],
          "verified_claims_update_policy": "deep_merge",
          "user_status": "KEEP"
        }
      })
    );
    await applyAndApprove({
      type: incomeType,
      accessToken,
      applyBody: { "last_name": "Yamada", "first_name": "Hanako", "kyc_level": "tier3" }
    });

    const updatedUser = await getUser(user.sub);
    console.log("User after deep_merge approval:", JSON.stringify(updatedUser, null, 2));

    // deep_merge: type A の検証済みクレームを保持したまま type B のクレームが追加される
    expect(updatedUser.verified_claims.claims).toHaveProperty("family_name", "Yamada");
    expect(updatedUser.verified_claims.claims).toHaveProperty("income_class", "tier3");
    // type B が出力していない verification も保持される
    expect(updatedUser.verified_claims.verification).toHaveProperty("trust_framework", "eidas");
  });

  it("replace_managed synchronizes declared custom properties on re-approval", async () => {
    const { user, accessToken } = await createTestUser();

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "custom_properties_mapping_rules": [
            { "from": "$.application.application_details.kyc_level", "to": "kyc_level" },
            { "from": "$.application.application_details.risk_flag", "to": "risk_flag" }
          ],
          "custom_properties_update_policy": "replace_managed",
          "user_status": "KEEP"
        }
      })
    );

    // 1回目: リスクフラグ付きで承認
    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "kyc_level": "gold",
        "risk_flag": "high"
      }
    });

    const firstUser = await getUser(user.sub);
    expect(firstUser.custom_properties).toHaveProperty("kyc_level", "gold");
    expect(firstUser.custom_properties).toHaveProperty("risk_flag", "high");

    // 2回目: 今回の審査では risk_flag に該当なし
    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "kyc_level": "platinum"
      }
    });

    const secondUser = await getUser(user.sub);
    console.log("User after replace_managed re-approval:", JSON.stringify(secondUser, null, 2));

    // 宣言キーは審査結果と同期: 値が出たキーは更新、出なかったキーは削除
    expect(secondUser.custom_properties).toHaveProperty("kyc_level", "platinum");
    expect(secondUser.custom_properties).not.toHaveProperty("risk_flag");
    // 宣言外のキー（Federation 由来の role 等）は影響を受けない
    expect(secondUser.custom_properties).toHaveProperty("role");
  });

  // --- エッジケース1: email 更新時の email_verified 陳腐化（既知の注意点を仕様として固定） ---
  it("email_verified remains unchanged when only email is updated (stale flag caveat)", async () => {
    const { user, accessToken } = await createTestUser();

    // type A: 推奨パターン（email と email_verified を両方明示的にマッピング）
    const verifiedConfigId = uuidv4();
    const verifiedType = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId: verifiedConfigId,
        type: verifiedType,
        result: {
          "user_claims_mapping_rules": [
            { "from": "$.application.application_details.email_address", "to": "email" },
            { "static_value": true, "to": "email_verified" }
          ],
          "user_status": "KEEP"
        }
      })
    );

    const firstEmail = `verified-${uuidv4()}@example.com`;
    await applyAndApprove({
      type: verifiedType,
      accessToken,
      applyBody: { "last_name": "Yamada", "first_name": "Hanako", "email_address": firstEmail }
    });

    const afterFirst = await getUser(user.sub);
    expect(afterFirst).toHaveProperty("email", firstEmail);
    expect(afterFirst).toHaveProperty("email_verified", true);

    // type B: email のみマッピング（email_verified を更新しない設定）
    const staleConfigId = uuidv4();
    const staleType = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId: staleConfigId,
        type: staleType,
        result: {
          "user_claims_mapping_rules": [
            { "from": "$.application.application_details.email_address", "to": "email" }
          ],
          "user_status": "KEEP"
        }
      })
    );

    const secondEmail = `unverified-${uuidv4()}@example.com`;
    await applyAndApprove({
      type: staleType,
      accessToken,
      applyBody: { "last_name": "Yamada", "first_name": "Hanako", "email_address": secondEmail }
    });

    const afterSecond = await getUser(user.sub);
    console.log("Stale flag check:", afterSecond.email, afterSecond.email_verified);

    // email は更新されるが、email_verified は旧メールの検証結果のまま残る（陳腐化）
    // → email を更新する設定では email_verified も明示的にマッピングすること（推奨パターン）
    expect(afterSecond).toHaveProperty("email", secondEmail);
    expect(afterSecond).toHaveProperty("email_verified", true);
  });

  // --- エッジケース2: address はオブジェクト単位の置換（部分マッピングで他フィールドが消える） ---
  it("address is replaced wholesale when mapped partially", async () => {
    const { user, accessToken } = await createTestUser();

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "user_claims_mapping_rules": [
            { "from": "$.application.application_details.address", "to": "address" }
          ],
          "user_status": "KEEP"
        }
      })
    );

    // 1回目: フル住所を設定
    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "address": {
          "street_address": "1-2-3 Chiyoda",
          "locality": "Chiyoda-ku",
          "region": "Tokyo",
          "postal_code": "1000001",
          "country": "JP"
        }
      }
    });

    const afterFull = await getUser(user.sub);
    expect(afterFull.address).toHaveProperty("locality", "Chiyoda-ku");
    expect(afterFull.address).toHaveProperty("country", "JP");

    // 2回目: locality だけの部分オブジェクト → address は丸ごと置換され他フィールドは消える
    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "address": { "locality": "Osaka-shi" }
      }
    });

    const afterPartial = await getUser(user.sub);
    console.log("Address after partial mapping:", JSON.stringify(afterPartial.address));

    // 部分更新ではなく全体置換（完全なオブジェクトをマッピングすべき、という仕様の固定）
    expect(afterPartial.address).toHaveProperty("locality", "Osaka-shi");
    expect(afterPartial.address).not.toHaveProperty("country");
    expect(afterPartial.address).not.toHaveProperty("region");
  });

  // --- エッジケース3: 型不一致のマッピングは fail-closed（承認エラー・ユーザー未更新） ---
  it("fails closed when mapped value type does not match the claim type", async () => {
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
            // boolean フィールドに文字列をマッピング（型不一致）
            { "from": "$.application.application_details.kyc_level", "to": "email_verified" }
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
        "kyc_level": "definitely-not-boolean"
      },
      expectedEvaluateStatus: 500
    });

    const afterUser = await getUser(user.sub);
    expect(afterUser.status).toBe(beforeUser.status);
    expect(afterUser.email_verified).toBe(beforeUser.email_verified);
  });

  // --- エッジケース4: replace_managed と "*" の組み合わせは同期削除が効かない ---
  it("replace_managed with wildcard rule cannot delete previously expanded keys", async () => {
    const { user, accessToken } = await createTestUser();

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "custom_properties_mapping_rules": [
            { "from": "$.application.application_details.extra", "to": "*" }
          ],
          "custom_properties_update_policy": "replace_managed",
          "user_status": "KEEP"
        }
      })
    );

    // 1回目: a, b を展開して設定
    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "extra": { "loyalty_tier": "gold", "campaign_code": "SPRING" }
      }
    });

    const afterFirst = await getUser(user.sub);
    expect(afterFirst.custom_properties).toHaveProperty("loyalty_tier", "gold");
    expect(afterFirst.custom_properties).toHaveProperty("campaign_code", "SPRING");

    // 2回目: loyalty_tier のみ → "*" は管理対象キーを宣言できないため campaign_code は残る
    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "extra": { "loyalty_tier": "platinum" }
      }
    });

    const afterSecond = await getUser(user.sub);
    console.log("Wildcard replace_managed:", JSON.stringify(afterSecond.custom_properties));

    expect(afterSecond.custom_properties).toHaveProperty("loyalty_tier", "platinum");
    // 同期削除されない（"*" の制限。削除したいキーは to を明示宣言する）
    expect(afterSecond.custom_properties).toHaveProperty("campaign_code", "SPRING");
    expect(afterSecond.custom_properties).toHaveProperty("role");
  });

  // --- エッジケース5: デフォルト merge はトップレベル丸ごと置換（差分キーは失われる） ---
  it("default merge replaces verified_claims top-level objects wholesale across types", async () => {
    const { user, accessToken } = await createTestUser();

    // type A: 氏名を検証
    const ekycConfigId = uuidv4();
    const ekycType = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId: ekycConfigId,
        type: ekycType,
        result: {
          "verified_claims_mapping_rules": [
            { "static_value": "eidas", "to": "verification.trust_framework" },
            { "from": "$.application.application_details.last_name", "to": "claims.family_name" }
          ]
        }
      })
    );
    await applyAndApprove({
      type: ekycType,
      accessToken,
      applyBody: { "last_name": "Yamada", "first_name": "Hanako" }
    });

    // type B: デフォルト merge のまま claims を出力（income のみ）
    const incomeConfigId = uuidv4();
    const incomeType = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId: incomeConfigId,
        type: incomeType,
        result: {
          "verified_claims_mapping_rules": [
            { "from": "$.application.application_details.kyc_level", "to": "claims.income_class" }
          ],
          "user_status": "KEEP"
        }
      })
    );
    await applyAndApprove({
      type: incomeType,
      accessToken,
      applyBody: { "last_name": "Yamada", "first_name": "Hanako", "kyc_level": "tier2" }
    });

    const updatedUser = await getUser(user.sub);
    console.log("Verified claims after default merge:", JSON.stringify(updatedUser.verified_claims));

    // デフォルト merge は claims オブジェクトを丸ごと差し替える（type A の氏名は失われる）
    // → 段階的KYCで共存させたい場合は deep_merge を使う
    expect(updatedUser.verified_claims.claims).toHaveProperty("income_class", "tier2");
    expect(updatedUser.verified_claims.claims).not.toHaveProperty("family_name");
    // 出力していないトップレベルキー（verification）は保持される
    expect(updatedUser.verified_claims.verification).toHaveProperty("trust_framework", "eidas");
  });

  it("recalculates preferred_username by identity policy when email is updated", async () => {
    // テストテナントのIDポリシーは EMAIL_OR_EXTERNAL_USER_ID（email から preferred_username を導出）
    const { user, accessToken } = await createTestUser();

    const beforeUser = await getUser(user.sub);
    console.log("Before:", beforeUser.email, beforeUser.preferred_username);

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "user_claims_mapping_rules": [
            { "from": "$.application.application_details.email_address", "to": "email" }
          ],
          "user_status": "KEEP"
        }
      })
    );

    const newEmail = `verified-${uuidv4()}@example.com`;
    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "Yamada",
        "first_name": "Hanako",
        "email_address": newEmail
      }
    });

    const updatedUser = await getUser(user.sub);
    console.log("After:", updatedUser.email, updatedUser.preferred_username);

    // email 更新に追従して preferred_username がIDポリシーで再計算される
    expect(updatedUser).toHaveProperty("email", newEmail);
    expect(updatedUser).toHaveProperty("preferred_username", newEmail);
  });

  it("fails closed when recalculated preferred_username collides with another user", async () => {
    // ユーザーA・Bを作成（同一テナント・同一プロバイダー）
    const { user: userA } = await createTestUser();
    const { user: userB, accessToken: accessTokenB } = await createTestUser();

    const userABefore = await getUser(userA.sub);
    const userBBefore = await getUser(userB.sub);

    const configId = uuidv4();
    const type = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId,
        type,
        result: {
          "user_claims_mapping_rules": [
            { "from": "$.application.application_details.email_address", "to": "email" }
          ],
          "user_status": "KEEP"
        }
      })
    );

    // ユーザーB が ユーザーA のメールアドレスへ更新を試みる
    // → preferred_username 再計算で一意制約（uk_preferred_username）に衝突し 409 Conflict
    await applyAndApprove({
      type,
      accessToken: accessTokenB,
      applyBody: {
        "last_name": "Suzuki",
        "first_name": "Taro",
        "email_address": userABefore.email
      },
      expectedEvaluateStatus: 409
    });

    // 全ロールバック: ユーザーB の email / preferred_username は変わらない
    const userBAfter = await getUser(userB.sub);
    console.log("User B after collision attempt:", userBAfter.email, userBAfter.preferred_username);
    expect(userBAfter.email).toBe(userBBefore.email);
    expect(userBAfter.preferred_username).toBe(userBBefore.preferred_username);

    // ユーザーA も無傷
    const userAAfter = await getUser(userA.sub);
    expect(userAAfter.email).toBe(userABefore.email);
    expect(userAAfter.preferred_username).toBe(userABefore.preferred_username);
  });

  it("fails closed and does not update user when user_status is invalid", async () => {
    const { user, accessToken } = await createTestUser();

    const beforeUser = await getUser(user.sub);

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
          "user_status": "SUPER_VERIFIED"
        }
      })
    );

    // 不正な user_status は承認時に fail-closed（500）となりユーザーは更新されない
    await applyAndApprove({
      type,
      accessToken,
      applyBody: {
        "last_name": "ShouldNotApply",
        "first_name": "InvalidStatus",
        "kyc_level": "gold"
      },
      expectedEvaluateStatus: 500
    });

    const afterUser = await getUser(user.sub);
    console.log("User after invalid user_status approval:", JSON.stringify(afterUser, null, 2));

    expect(afterUser.status).toBe(beforeUser.status);
    if (afterUser.custom_properties) {
      expect(afterUser.custom_properties).not.toHaveProperty("kyc_level");
    }
  });

  it("fails closed when configured user_status transition is not allowed by lifecycle", async () => {
    const { user, accessToken } = await createTestUser();

    // まずデフォルト設定で IDENTITY_VERIFIED へ遷移させる
    const firstConfigId = uuidv4();
    const firstType = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId: firstConfigId,
        type: firstType,
        result: {
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
      type: firstType,
      accessToken,
      applyBody: { "last_name": "Yamada", "first_name": "Hanako", "kyc_level": "gold" }
    });

    const verifiedUser = await getUser(user.sub);
    expect(verifiedUser).toHaveProperty("status", "IDENTITY_VERIFIED");

    // IDENTITY_VERIFIED -> REGISTERED は UserLifecycleManager で許可されない遷移
    const secondConfigId = uuidv4();
    const secondType = uuidv4();
    await registerConfiguration(
      buildConfiguration({
        configId: secondConfigId,
        type: secondType,
        result: {
          "custom_properties_mapping_rules": [
            {
              "from": "$.application.application_details.kyc_level",
              "to": "denied_marker"
            }
          ],
          "user_status": "REGISTERED"
        }
      })
    );

    await applyAndApprove({
      type: secondType,
      accessToken,
      applyBody: { "last_name": "Yamada", "first_name": "Hanako", "kyc_level": "should-not-apply" },
      expectedEvaluateStatus: 500
    });

    const afterUser = await getUser(user.sub);
    console.log("User after disallowed transition approval:", JSON.stringify(afterUser, null, 2));

    // ステータスも属性も変わらない
    expect(afterUser).toHaveProperty("status", "IDENTITY_VERIFIED");
    expect(afterUser.custom_properties).not.toHaveProperty("denied_marker");
    expect(afterUser.custom_properties).toHaveProperty("kyc_level", "gold");
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
