import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import {
  requestToken,
  getJwks,
  requestBackchannelAuthentications,
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction
} from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { verifyAndDecodeJwt } from "../../../lib/jose";
import { v4 as uuidv4 } from "uuid";

/**
 * Identity Verification Token Reflection
 *
 * 承認で更新したユーザー属性が、実際にRPから見える出口（UserInfo / アクセストークン）に
 * 反映されることを end-to-end で検証する。
 *
 * - UserInfo: 承認前に発行済みのアクセストークンのまま、更新後の標準クレームが返る
 * - アクセストークン: 承認後にリフレッシュで再発行したJWTに
 *   custom_properties（claims:ex_sub スコープ）と verified_claims
 *   （verified_claims:family_name スコープ）が載る
 */
describe("Identity Verification Token Reflection", () => {
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

  // 単一プロセス即時承認の設定（結果マッピングは request_body を参照）
  const buildImmediateApprovalConfiguration = ({ configId, type, result }) => ({
    "id": configId,
    "type": type,
    "attributes": { "enabled": true },
    "common": { "auth_type": "none" },
    "processes": {
      "register": {
        "request": {
          "schema": {
            "type": "object",
            "required": ["last_name"],
            "properties": {
              "last_name": { "type": "string" },
              "address": { "type": "object" },
              "external_customer_id": { "type": "string" }
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
    "result": result
  });

  const applyAndApprove = async ({ type, accessToken, body }) => {
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "register");
    const response = await postWithJson({
      url: applyUrl,
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`
      },
      body
    });
    console.log("Apply response:", response.status, JSON.stringify(response.data, null, 2));
    expect(response.status).toBe(200);
  };

  it("userinfo reflects updated claims with the access token issued before approval", async () => {
    const { user, accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope: "openid profile email address identity_verification_application " + clientSecretPostClient.identityVerificationScope
    });
    await registerFidoUaf({ accessToken });

    // 承認前の userinfo（family_name / address は未設定）
    const beforeResponse = await get({
      url: serverConfig.userinfoEndpoint,
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    expect(beforeResponse.status).toBe(200);
    console.log("Userinfo before:", JSON.stringify(beforeResponse.data, null, 2));
    expect(beforeResponse.data).not.toHaveProperty("family_name");

    const configId = uuidv4();
    const type = `profile-registration-${uuidv4()}`;
    await registerConfiguration(
      buildImmediateApprovalConfiguration({
        configId,
        type,
        result: {
          "user_claims_mapping_rules": [
            { "from": "$.request_body.last_name", "to": "family_name" },
            { "from": "$.request_body.address", "to": "address" }
          ],
          "user_status": "KEEP"
        }
      })
    );

    await applyAndApprove({
      type,
      accessToken,
      body: {
        "last_name": "Tanaka",
        "address": {
          "locality": "Shibuya-ku",
          "region": "Tokyo",
          "postal_code": "1500001",
          "country": "JP"
        }
      }
    });

    // 承認前に発行されたアクセストークンのままでも、userinfo は最新のユーザー属性を返す
    const afterResponse = await get({
      url: serverConfig.userinfoEndpoint,
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    expect(afterResponse.status).toBe(200);
    console.log("Userinfo after:", JSON.stringify(afterResponse.data, null, 2));

    expect(afterResponse.data).toHaveProperty("sub", user.sub);
    expect(afterResponse.data).toHaveProperty("family_name", "Tanaka");
    expect(afterResponse.data).toHaveProperty("address");
    expect(afterResponse.data.address).toHaveProperty("locality", "Shibuya-ku");
  });

  it("refresh keeps grant-time claims, new authorization carries updated verified_claims", async () => {
    const tokenScope = "openid profile email claims:ex_sub verified_claims:family_name";
    const { user, accessToken, refreshToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope: tokenScope + " identity_verification_application " + clientSecretPostClient.identityVerificationScope
    });
    const { authenticationDeviceId } = await registerFidoUaf({ accessToken });

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });

    // ログイン時点のアクセストークンが持つ ex_sub（フェデレーション由来）を控えておく
    const originalPayload = verifyAndDecodeJwt({ jwt: accessToken, jwks: jwksResponse.data }).payload;
    const originalExSub = originalPayload.ex_sub;

    const externalCustomerId = `CIF-${uuidv4()}`;
    const configId = uuidv4();
    const type = `customer-registration-${uuidv4()}`;
    await registerConfiguration(
      buildImmediateApprovalConfiguration({
        configId,
        type,
        result: {
          // claims:ex_sub スコープでアクセストークンに載る業務属性
          "custom_properties_mapping_rules": [
            { "from": "$.request_body.external_customer_id", "to": "ex_sub" }
          ],
          // verified_claims:family_name スコープでアクセストークンに載る検証済みクレーム
          "verified_claims_mapping_rules": [
            { "static_value": "eidas", "to": "verification.trust_framework" },
            { "from": "$.request_body.last_name", "to": "claims.family_name" }
          ]
        }
      })
    );

    await applyAndApprove({
      type,
      accessToken,
      body: { "last_name": "Tanaka", "external_customer_id": externalCustomerId }
    });

    // DBには反映されていることを先に確認（トークン経路との切り分け）
    const dbUserResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${user.sub}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` }
    });
    expect(dbUserResponse.status).toBe(200);
    console.log("DB custom_properties:", JSON.stringify(dbUserResponse.data.custom_properties));
    expect(dbUserResponse.data.custom_properties).toHaveProperty("ex_sub", externalCustomerId);

    // --- 観点1: リフレッシュ再発行はグラント保存時（ログイン時）のユーザースナップショットを引き継ぐ ---
    const refreshResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "refresh_token",
      refreshToken: refreshToken,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret
    });
    expect(refreshResponse.status).toBe(200);

    const refreshedPayload = verifyAndDecodeJwt({
      jwt: refreshResponse.data.access_token,
      jwks: jwksResponse.data
    }).payload;
    console.log("Refreshed access token payload:", JSON.stringify(refreshedPayload, null, 2));

    // 承認後の更新はリフレッシュ再発行には反映されない（現仕様の固定）
    expect(refreshedPayload.ex_sub).toBe(originalExSub);
    expect(refreshedPayload.ex_sub).not.toBe(externalCustomerId);
    expect(refreshedPayload).not.toHaveProperty("verified_claims");

    // --- 観点2: 新規認可（CIBA）で発行したアクセストークンには最新の属性が反映される ---
    const ciba = serverConfig.ciba;
    const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
      endpoint: serverConfig.backchannelAuthenticationEndpoint,
      clientId: clientSecretPostClient.clientId,
      scope: tokenScope,
      bindingMessage: ciba.bindingMessage,
      loginHint: `device:${authenticationDeviceId},idp:${federationServerConfig.providerName}`,
      acrValues: "urn:mace:incommon:iap:gold",
      clientSecret: clientSecretPostClient.clientSecret
    });
    console.log("CIBA response:", backchannelAuthenticationResponse.status, JSON.stringify(backchannelAuthenticationResponse.data));
    expect(backchannelAuthenticationResponse.status).toBe(200);

    const authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: authenticationDeviceId,
      params: {
        "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
      }
    });
    expect(authenticationTransactionResponse.status).toBe(200);
    const authenticationTransaction = authenticationTransactionResponse.data.list[0];

    for (const interactionType of [
      "fido-uaf-authentication-challenge",
      "fido-uaf-authentication"
    ]) {
      const authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType,
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode
        }
      });
      expect(authenticationResponse.status).toBe(200);
    }

    const cibaTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: backchannelAuthenticationResponse.data.auth_req_id,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret
    });
    console.log("CIBA token response:", cibaTokenResponse.status);
    expect(cibaTokenResponse.status).toBe(200);

    const newPayload = verifyAndDecodeJwt({
      jwt: cibaTokenResponse.data.access_token,
      jwks: jwksResponse.data
    }).payload;
    console.log("New authorization access token payload:", JSON.stringify(newPayload, null, 2));

    // verified_claims:family_name スコープにより、承認由来の検証済みクレームが新規認可のトークンに載る。
    // #1435/#1514 で AT/UserInfo の verified_claims は OIDC4IDA 準拠のネスト構造（verification + claims）。
    // trust_framework は verification の必須要素なので、scope 未要求でも常に含まれる。
    expect(newPayload).toHaveProperty("verified_claims");
    expect(newPayload.verified_claims.claims).toHaveProperty("family_name", "Tanaka");
    expect(newPayload.verified_claims.verification).toHaveProperty("trust_framework", "eidas");

    // ex_sub は予約クレーム（ScopeMappingCustomClaimsCreator が User.externalUserId を優先）のため、
    // custom_properties に ex_sub を書いてもトークンには反映されない（DBには反映済み = 上で確認済み）
    // 予約クレーム名: status / ex_sub / roles / permissions / assigned_tenants /
    //               assigned_organizations / authentication_devices
    expect(newPayload).toHaveProperty("ex_sub", originalExSub);
    expect(newPayload.ex_sub).not.toBe(externalCustomerId);
  });
});
