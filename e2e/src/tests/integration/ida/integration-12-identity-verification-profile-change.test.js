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
 * Identity Verification Profile Change (審査つき変更申込みユースケース)
 *
 * 住所変更・連絡先変更を「申込み → 審査 → 外部サービスのコールバック承認 → 反映」の
 * 業務フローとして検証する。
 *
 * - 住所変更: 標準クレーム address（全置換）+ verified_claims の claims.address（deep_merge で
 *   既存の検証済みクレームを保持）を同時更新。ステータスは KEEP
 * - 連絡先変更: email / phone_number と検証フラグ（email_verified / phone_number_verified）を
 *   明示マッピングする推奨パターン。preferred_username はIDポリシーで新メールに追従
 */
describe("Identity Verification Profile Change", () => {
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
    console.log("Configuration creation response:", response.status);
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

  const getUser = async (sub) => {
    const response = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${sub}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` }
    });
    expect(response.status).toBe(200);
    return response.data;
  };

  // 審査つき変更申込みの共通プロセス構成（apply → 外部サービスの callback-result で承認）
  const buildReviewFlowConfiguration = ({ configId, type, applySchema, basicAuth, result }) => ({
    "id": configId,
    "type": type,
    "attributes": { "enabled": true },
    "common": {
      "auth_type": "none",
      "callback_application_id_param": "application_id"
    },
    "processes": {
      "apply": {
        "request": { "schema": applySchema },
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
          },
          "rejected": {
            "any_of": [
              [
                {
                  "path": "$.request_body.result",
                  "type": "string",
                  "operation": "eq",
                  "value": "ng"
                }
              ]
            ]
          }
        }
      }
    },
    "result": result
  });

  const applyAndCallbackApprove = async ({ type, accessToken, applyBody, basicAuth }) => {
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

    // 外部サービス（審査側）がコールバックで結果を通知
    const callbackUrl = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
      .replace("{type}", type)
      .replace("{callbackName}", "callback-result");
    const callbackResponse = await postWithJson({
      url: callbackUrl,
      headers: {
        "Content-Type": "application/json",
        ...createBasicAuthHeader(basicAuth)
      },
      body: {
        "application_id": applyBody.external_ref,
        "result": "ok"
      }
    });
    console.log("Callback response:", callbackResponse.status, JSON.stringify(callbackResponse.data, null, 2));
    expect(callbackResponse.status).toBe(200);
  };

  it("address change application updates address and verified claims via callback approval", async () => {
    const { user, accessToken } = await createTestUser();

    // 前提: eKYC 済みユーザー（IDENTITY_VERIFIED + 検証済み氏名）を作る
    const ekycConfigId = uuidv4();
    const ekycType = `ekyc-${uuidv4()}`;
    await registerConfiguration({
      "id": ekycConfigId,
      "type": ekycType,
      "attributes": { "enabled": true },
      "common": { "auth_type": "none" },
      "processes": {
        "register": {
          "request": {
            "schema": {
              "type": "object",
              "required": ["last_name"],
              "properties": { "last_name": { "type": "string" } }
            }
          },
          "execution": { "type": "no_action" },
          "store": {
            "application_details_mapping_rules": [{ "from": "$.request_body", "to": "*" }]
          },
          "transition": {
            "approved": {
              "any_of": [
                [
                  {
                    "path": "$.request_body.last_name",
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
        // 単一プロセス即時承認では $.application.* が未生成のため request_body を参照する
        "verified_claims_mapping_rules": [
          { "static_value": "eidas", "to": "verification.trust_framework" },
          { "from": "$.request_body.last_name", "to": "claims.family_name" }
        ]
      }
    });

    const ekycApplyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", ekycType)
      .replace("{process}", "register");
    const ekycResponse = await postWithJson({
      url: ekycApplyUrl,
      headers: { "Content-Type": "application/json", "Authorization": `Bearer ${accessToken}` },
      body: { "last_name": "Yamada" }
    });
    expect(ekycResponse.status).toBe(200);

    const verifiedUser = await getUser(user.sub);
    expect(verifiedUser).toHaveProperty("status", "IDENTITY_VERIFIED");
    expect(verifiedUser.verified_claims.claims).toHaveProperty("family_name", "Yamada");

    // 住所変更申込み（審査つき・コールバック承認）
    const basicAuth = { username: "address_review_svc", password: "address_review_password001" };
    const configId = uuidv4();
    const type = `address-change-${uuidv4()}`; // 可読性 + 一意性の両立
    await registerConfiguration(
      buildReviewFlowConfiguration({
        configId,
        type,
        basicAuth,
        applySchema: {
          "type": "object",
          "required": ["external_ref", "document_type", "new_address"],
          "properties": {
            "external_ref": { "type": "string" },
            "document_type": { "type": "string" },
            "new_address": {
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
        },
        result: {
          // 標準クレーム address は完全オブジェクトで全置換
          "user_claims_mapping_rules": [
            { "from": "$.application.application_details.new_address", "to": "address" }
          ],
          // 検証済み住所も更新。deep_merge で既存の検証済み氏名は保持
          "verified_claims_mapping_rules": [
            { "from": "$.application.application_details.new_address", "to": "claims.address" }
          ],
          "verified_claims_update_policy": "deep_merge",
          "user_status": "KEEP"
        }
      })
    );

    const newAddress = {
      "street_address": "4-5-6 Umeda",
      "locality": "Osaka-shi",
      "region": "Osaka",
      "postal_code": "5300001",
      "country": "JP"
    };
    await applyAndCallbackApprove({
      type,
      accessToken,
      basicAuth,
      applyBody: {
        "external_ref": uuidv4(),
        "document_type": "residence_certificate",
        "new_address": newAddress
      }
    });

    const updatedUser = await getUser(user.sub);
    console.log("User after address change:", JSON.stringify(updatedUser, null, 2));

    // 標準クレームの住所が新住所に置き換わる
    expect(updatedUser.address).toHaveProperty("locality", "Osaka-shi");
    expect(updatedUser.address).toHaveProperty("postal_code", "5300001");

    // 検証済みクレーム: 住所は更新、eKYC の氏名・trust_framework は deep_merge で保持
    expect(updatedUser.verified_claims.claims.address).toHaveProperty("locality", "Osaka-shi");
    expect(updatedUser.verified_claims.claims).toHaveProperty("family_name", "Yamada");
    expect(updatedUser.verified_claims.verification).toHaveProperty("trust_framework", "eidas");

    // ステータスは現状維持（IDENTITY_VERIFIED のまま）
    expect(updatedUser).toHaveProperty("status", "IDENTITY_VERIFIED");
  });

  it("name change application updates family_name and verified claims via callback approval", async () => {
    const { user, accessToken } = await createTestUser();

    // 前提: eKYC 済みユーザー（検証済みの氏名 + 住所を保持）
    const ekycConfigId = uuidv4();
    const ekycType = `ekyc-${uuidv4()}`;
    await registerConfiguration({
      "id": ekycConfigId,
      "type": ekycType,
      "attributes": { "enabled": true },
      "common": { "auth_type": "none" },
      "processes": {
        "register": {
          "request": {
            "schema": {
              "type": "object",
              "required": ["last_name", "address"],
              "properties": {
                "last_name": { "type": "string" },
                "address": { "type": "object" }
              }
            }
          },
          "execution": { "type": "no_action" },
          "store": {
            "application_details_mapping_rules": [{ "from": "$.request_body", "to": "*" }]
          },
          "transition": {
            "approved": {
              "any_of": [
                [
                  {
                    "path": "$.request_body.last_name",
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
        // 単一プロセス即時承認では $.application.* が未生成のため request_body を参照する
        "verified_claims_mapping_rules": [
          { "static_value": "eidas", "to": "verification.trust_framework" },
          { "from": "$.request_body.last_name", "to": "claims.family_name" },
          { "from": "$.request_body.address", "to": "claims.address" }
        ],
        "user_claims_mapping_rules": [
          { "from": "$.request_body.last_name", "to": "family_name" }
        ]
      }
    });

    const ekycApplyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", ekycType)
      .replace("{process}", "register");
    const ekycResponse = await postWithJson({
      url: ekycApplyUrl,
      headers: { "Content-Type": "application/json", "Authorization": `Bearer ${accessToken}` },
      body: {
        "last_name": "Yamada",
        "address": { "locality": "Chiyoda-ku", "country": "JP" }
      }
    });
    expect(ekycResponse.status).toBe(200);

    const verifiedUser = await getUser(user.sub);
    expect(verifiedUser).toHaveProperty("status", "IDENTITY_VERIFIED");
    expect(verifiedUser).toHaveProperty("family_name", "Yamada");
    expect(verifiedUser.verified_claims.claims).toHaveProperty("family_name", "Yamada");

    // 氏名変更申込み（審査つき・コールバック承認）
    const basicAuth = { username: "name_review_svc", password: "name_review_password001" };
    const configId = uuidv4();
    const type = `name-change-${uuidv4()}`;
    await registerConfiguration(
      buildReviewFlowConfiguration({
        configId,
        type,
        basicAuth,
        applySchema: {
          "type": "object",
          "required": ["external_ref", "document_type", "new_last_name"],
          "properties": {
            "external_ref": { "type": "string" },
            "document_type": { "type": "string" },
            "new_last_name": { "type": "string" }
          }
        },
        result: {
          "user_claims_mapping_rules": [
            { "from": "$.application.application_details.new_last_name", "to": "family_name" }
          ],
          // 検証済みの姓も更新。deep_merge で検証済み住所・trust_framework は保持
          "verified_claims_mapping_rules": [
            { "from": "$.application.application_details.new_last_name", "to": "claims.family_name" }
          ],
          "verified_claims_update_policy": "deep_merge",
          "user_status": "KEEP"
        }
      })
    );

    await applyAndCallbackApprove({
      type,
      accessToken,
      basicAuth,
      applyBody: {
        "external_ref": uuidv4(),
        "document_type": "family_register",
        "new_last_name": "Sato"
      }
    });

    const updatedUser = await getUser(user.sub);
    console.log("User after name change:", updatedUser.family_name, JSON.stringify(updatedUser.verified_claims));

    // 標準クレームの姓が更新される
    expect(updatedUser).toHaveProperty("family_name", "Sato");

    // 検証済みクレーム: 姓は更新、住所・trust_framework は deep_merge で保持
    expect(updatedUser.verified_claims.claims).toHaveProperty("family_name", "Sato");
    expect(updatedUser.verified_claims.claims.address).toHaveProperty("locality", "Chiyoda-ku");
    expect(updatedUser.verified_claims.verification).toHaveProperty("trust_framework", "eidas");

    // ステータスは現状維持、preferred_username（EMAILポリシー）も氏名変更の影響を受けない
    expect(updatedUser).toHaveProperty("status", "IDENTITY_VERIFIED");
    expect(updatedUser.preferred_username).toBe(verifiedUser.preferred_username);
  });

  it("contact change application updates email/phone with verification flags via callback approval", async () => {
    const { user, accessToken } = await createTestUser();

    const beforeUser = await getUser(user.sub);

    const basicAuth = { username: "contact_review_svc", password: "contact_review_password001" };
    const configId = uuidv4();
    const type = `contact-change-${uuidv4()}`;
    await registerConfiguration(
      buildReviewFlowConfiguration({
        configId,
        type,
        basicAuth,
        applySchema: {
          "type": "object",
          "required": ["external_ref", "new_email", "new_phone"],
          "properties": {
            "external_ref": { "type": "string" },
            "new_email": { "type": "string" },
            "new_phone": { "type": "string" }
          }
        },
        result: {
          // 推奨パターン: 連絡先の更新は検証フラグも明示的にマッピングする
          "user_claims_mapping_rules": [
            { "from": "$.application.application_details.new_email", "to": "email" },
            { "static_value": true, "to": "email_verified" },
            { "from": "$.application.application_details.new_phone", "to": "phone_number" },
            { "static_value": true, "to": "phone_number_verified" }
          ],
          "user_status": "KEEP"
        }
      })
    );

    const newEmail = `contact-change-${uuidv4()}@example.com`;
    const newPhone = "09099998888";
    await applyAndCallbackApprove({
      type,
      accessToken,
      basicAuth,
      applyBody: {
        "external_ref": uuidv4(),
        "new_email": newEmail,
        "new_phone": newPhone
      }
    });

    const updatedUser = await getUser(user.sub);
    console.log("User after contact change:", updatedUser.email, updatedUser.phone_number, updatedUser.preferred_username);

    // 連絡先と検証フラグが同時に更新される
    expect(updatedUser).toHaveProperty("email", newEmail);
    expect(updatedUser).toHaveProperty("email_verified", true);
    expect(updatedUser).toHaveProperty("phone_number", newPhone);
    expect(updatedUser).toHaveProperty("phone_number_verified", true);

    // preferred_username はIDポリシー（EMAIL_OR_EXTERNAL_USER_ID）で新メールに追従
    expect(updatedUser).toHaveProperty("preferred_username", newEmail);

    // ステータスは現状維持
    expect(updatedUser.status).toBe(beforeUser.status);
  });
});
