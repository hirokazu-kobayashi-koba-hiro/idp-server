import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, post, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig,
  mockApiBaseUrl
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";

describe("Identity Verification - Array Functions in verified_claims", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  let userAccessToken;
  let testUser;

  const configIds = [];

  beforeAll(async () => {
    console.log("Setting up Array Functions in verified_claims Test...");

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

    const userResult = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope: "openid profile phone email identity_verification_application identity_verification_application_delete identity_verification_result claims:authentication_devices claims:ex_sub " + clientSecretPostClient.identityVerificationScope
    });

    testUser = userResult.user;
    userAccessToken = userResult.accessToken;
    console.log(`Created test user: ${testUser.sub}`);

    await registerFidoUaf({ accessToken: userAccessToken });
    console.log("Registered FIDO UAF for test user");
  });

  afterAll(async () => {
    for (const configId of configIds) {
      try {
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${orgAccessToken}` }
        });
        console.log(`Cleaned up configuration: ${configId}`);
      } catch (e) {
        console.log(`Failed to clean up configuration: ${configId}`, e.message);
      }
    }
  });

  /**
   * IDA設定を作成するヘルパー
   */
  async function createIdaConfig(configId, configurationType, processes, verifiedClaimsMappingRules) {
    // evaluate-resultプロセスを自動追加(承認用)
    const allProcesses = {
      ...processes,
      "evaluate-result": {
        "execution": { "type": "no_action" },
        "transition": {
          "approved": {
            "any_of": [[
              { "path": "$.request_body.approved", "type": "boolean", "operation": "eq", "value": true },
              { "path": "$.request_body.rejected", "type": "boolean", "operation": "eq", "value": false }
            ]]
          },
          "rejected": {
            "any_of": [[
              { "path": "$.request_body.approved", "type": "boolean", "operation": "eq", "value": false },
              { "path": "$.request_body.rejected", "type": "boolean", "operation": "eq", "value": true }
            ]]
          }
        }
      }
    };

    const configurationData = {
      "id": configId,
      "type": configurationType,
      "attributes": { "enabled": true },
      "common": { "auth_type": "none" },
      "processes": allProcesses,
      "result": {
        "verified_claims_mapping_rules": verifiedClaimsMappingRules,
        "source_details_mapping_rules": [
          { "from": "$.application.application_details", "to": "*" }
        ]
      }
    };

    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        "Authorization": `Bearer ${orgAccessToken}`,
        "Content-Type": "application/json"
      },
      body: configurationData
    });
    expect(response.status).toBe(201);
    return response;
  }

  /**
   * 申込み(ID発行) → 後続プロセス(外部API呼出し) → evaluate-result(承認) の一連フロー
   */
  async function applyAndApprove(configurationType, applyProcess, verifyProcess, applyBody, verifyBody) {
    // Step 1: 申込み(ID発行)
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", configurationType)
      .replace("{process}", applyProcess);

    const applyResponse = await postWithJson({
      url: applyUrl,
      body: applyBody,
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
      }
    });
    console.log("Apply response:", JSON.stringify(applyResponse.data, null, 2));
    expect(applyResponse.status).toBe(200);

    const applicationId = applyResponse.data.id;
    expect(applicationId).toBeDefined();

    // Step 2: 後続プロセス(外部API呼出し)
    const processEndpoint = serverConfig.identityVerificationProcessEndpoint
      .replace("{type}", configurationType)
      .replace("{id}", applicationId);

    const verifyResponse = await postWithJson({
      url: processEndpoint.replace("{process}", verifyProcess),
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
      },
      body: verifyBody
    });
    console.log("Verify response:", JSON.stringify(verifyResponse.data, null, 2));
    expect(verifyResponse.status).toBe(200);

    // Step 3: evaluate-result(承認)
    const evaluateResultEndpoint = serverConfig.identityVerificationApplicationsEvaluateResultEndpoint
      .replace("{type}", configurationType)
      .replace("{id}", applicationId);

    const evaluateResponse = await post({
      url: evaluateResultEndpoint,
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
      },
      body: { "approved": true, "rejected": false }
    });
    console.log("Evaluate result response:", JSON.stringify(evaluateResponse.data, null, 2));
    expect(evaluateResponse.status).toBe(200);

    return applicationId;
  }

  describe("pluck and append functions in verified_claims_mapping_rules", () => {

    it("should set array elements in verified_claims using pluck and append functions", async () => {
      const configId = uuidv4();
      const configurationType = uuidv4();
      configIds.push(configId);

      // 2プロセス構成: apply(申込み受付) + verify(検証)
      // 全プロセスに execution が必須
      const processes = {
        "apply": {
          "request": {
            "schema": {
              "type": "object",
              "properties": {
                "accounts": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "account_no": { "type": "string" },
                      "type": { "type": "string" }
                    }
                  }
                },
                "tags": {
                  "type": "array",
                  "items": { "type": "string" }
                },
                "given_name": { "type": "string" },
                "family_name": { "type": "string" },
                "birthdate": { "type": "string" }
              },
              "required": ["accounts", "given_name", "family_name", "birthdate"]
            }
          },
          "execution": {
            "type": "http_request",
            "http_request": {
              "url": `${mockApiBaseUrl}/crm-registration`,
              "method": "POST",
              "auth_type": "none",
              "header_mapping_rules": [
                { "static_value": "application/json", "to": "Content-Type" }
              ],
              "body_mapping_rules": [
                { "from": "$.request_body", "to": "*" },
                { "from": "$.user.sub", "to": "user_id" }
              ]
            }
          },
          "store": {
            "application_details_mapping_rules": [
              { "from": "$.request_body", "to": "*" }
            ]
          },
          "response": {
            "body_mapping_rules": [
              { "from": "$.response_body", "to": "*" }
            ]
          }
        },
        "verify": {
          "request": {
            "schema": {
              "type": "object",
              "properties": {
                "trust_framework": { "type": "string" }
              },
              "required": ["trust_framework"]
            }
          },
          "execution": {
            "type": "http_request",
            "http_request": {
              "url": `${mockApiBaseUrl}/crm-registration`,
              "method": "POST",
              "auth_type": "none",
              "header_mapping_rules": [
                { "static_value": "application/json", "to": "Content-Type" }
              ],
              "body_mapping_rules": [
                { "from": "$.application.application_details", "to": "*" },
                { "from": "$.user.sub", "to": "user_id" }
              ]
            }
          },
          "store": {
            "application_details_mapping_rules": [
              { "from": "$.response_body", "to": "external_response" }
            ]
          },
          "response": {
            "body_mapping_rules": [
              { "from": "$.response_body", "to": "*" }
            ]
          }
        }
      };

      const verifiedClaimsMappingRules = [
        // 直接配列マッピング: accounts配列をそのままverified_claimsに設定
        { "from": "$.application.application_details.accounts", "to": "claims.accounts" },
        // pluck: accountsオブジェクト配列からaccount_noフィールドを抽出
        {
          "from": "$.application.application_details.accounts",
          "to": "claims.account_numbers",
          "functions": [
            { "name": "pluck", "args": { "field": "account_no" } }
          ]
        },
        // append: tags配列に"verified"要素を追加
        {
          "from": "$.application.application_details.tags",
          "to": "claims.tags",
          "functions": [
            { "name": "append", "args": { "value": "verified" } }
          ]
        },
        // 基本的な文字列マッピング
        { "from": "$.application.application_details.given_name", "to": "claims.given_name" },
        { "from": "$.application.application_details.family_name", "to": "claims.family_name" }
      ];

      await createIdaConfig(configId, configurationType, processes, verifiedClaimsMappingRules);

      // 申込み → 検証 → 承認
      const applicationId = await applyAndApprove(
        configurationType,
        "apply",
        "verify",
        {
          "accounts": [
            { "account_no": "123", "type": "savings" },
            { "account_no": "456", "type": "checking" },
            { "account_no": "789", "type": "investment" }
          ],
          "tags": ["finance", "personal"],
          "given_name": "ArrayTest",
          "family_name": "User",
          "birthdate": "1990-01-01"
        },
        { "trust_framework": "eidas" }
      );

      // 身元確認結果を取得してverified_claimsの配列データを検証
      const resultsResponse = await get({
        url: serverConfig.identityVerificationResultResourceOwnerEndpoint + `?application_id=${applicationId}&type=${configurationType}`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log("Results response:", JSON.stringify(resultsResponse.data, null, 2));
      expect(resultsResponse.status).toBe(200);
      expect(resultsResponse.data.list.length).toBe(1);

      const result = resultsResponse.data.list[0];
      expect(result).toHaveProperty("verified_claims");
      expect(result).toHaveProperty("verified_at");

      const verifiedClaims = result.verified_claims;

      // 直接配列マッピングの検証: accounts配列がそのまま保持されている
      expect(verifiedClaims.claims).toHaveProperty("accounts");
      expect(verifiedClaims.claims.accounts).toHaveLength(3);
      expect(verifiedClaims.claims.accounts[0].account_no).toBe("123");
      expect(verifiedClaims.claims.accounts[1].account_no).toBe("456");
      expect(verifiedClaims.claims.accounts[2].account_no).toBe("789");

      // pluck関数の検証: account_noフィールドのみ抽出されたフラット配列
      expect(verifiedClaims.claims).toHaveProperty("account_numbers");
      expect(verifiedClaims.claims.account_numbers).toEqual(["123", "456", "789"]);

      // append関数の検証: 元の配列に"verified"が追加されている
      expect(verifiedClaims.claims).toHaveProperty("tags");
      expect(verifiedClaims.claims.tags).toEqual(["finance", "personal", "verified"]);

      // 基本文字列マッピングの検証
      expect(verifiedClaims.claims.given_name).toBe("ArrayTest");
      expect(verifiedClaims.claims.family_name).toBe("User");

      console.log("pluck, append functions work correctly in verified_claims_mapping_rules");
    });

  });

  describe("merge existing verified_claims with new request data using dynamic args", () => {

    it("should merge new accounts from request_body into existing verified_claims accounts", async () => {
      // Step 1: 初回設定 - accountsオブジェクト配列をverified_claimsに設定
      const config1Id = uuidv4();
      const config1Type = uuidv4();
      configIds.push(config1Id);

      const commonProcesses = (schemaProps, requiredFields) => ({
        "apply": {
          "request": {
            "schema": {
              "type": "object",
              "properties": schemaProps,
              "required": requiredFields
            }
          },
          "execution": {
            "type": "http_request",
            "http_request": {
              "url": `${mockApiBaseUrl}/crm-registration`,
              "method": "POST",
              "auth_type": "none",
              "header_mapping_rules": [
                { "static_value": "application/json", "to": "Content-Type" }
              ],
              "body_mapping_rules": [
                { "from": "$.request_body", "to": "*" },
                { "from": "$.user.sub", "to": "user_id" }
              ]
            }
          },
          "store": {
            "application_details_mapping_rules": [
              { "from": "$.request_body", "to": "*" }
            ]
          },
          "response": {
            "body_mapping_rules": [{ "from": "$.response_body", "to": "*" }]
          }
        },
        "verify": {
          "request": {
            "schema": {
              "type": "object",
              "properties": { "trust_framework": { "type": "string" } },
              "required": ["trust_framework"]
            }
          },
          "execution": {
            "type": "http_request",
            "http_request": {
              "url": `${mockApiBaseUrl}/crm-registration`,
              "method": "POST",
              "auth_type": "none",
              "header_mapping_rules": [
                { "static_value": "application/json", "to": "Content-Type" }
              ],
              "body_mapping_rules": [
                { "from": "$.application.application_details", "to": "*" },
                { "from": "$.user.sub", "to": "user_id" }
              ]
            }
          },
          "store": {
            "application_details_mapping_rules": [
              { "from": "$.response_body", "to": "external_response" }
            ]
          },
          "response": {
            "body_mapping_rules": [{ "from": "$.response_body", "to": "*" }]
          }
        }
      });

      const accountSchema = {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "account_no": { "type": "string" },
            "type": { "type": "string" }
          }
        }
      };

      await createIdaConfig(config1Id, config1Type,
        commonProcesses(
          {
            "accounts": accountSchema,
            "given_name": { "type": "string" },
            "family_name": { "type": "string" },
            "birthdate": { "type": "string" }
          },
          ["accounts", "given_name", "family_name", "birthdate"]
        ),
        [
          { "from": "$.application.application_details.accounts", "to": "claims.accounts" },
          {
            "from": "$.application.application_details.accounts",
            "to": "claims.account_numbers",
            "functions": [{ "name": "pluck", "args": { "field": "account_no" } }]
          },
          { "from": "$.application.application_details.given_name", "to": "claims.given_name" },
          { "from": "$.application.application_details.family_name", "to": "claims.family_name" }
        ]
      );

      // 初回: accounts = [{account_no: "111"}, {account_no: "222"}] を設定
      await applyAndApprove(
        config1Type, "apply", "verify",
        {
          "accounts": [
            { "account_no": "111", "type": "savings" },
            { "account_no": "222", "type": "checking" }
          ],
          "given_name": "MergeTest",
          "family_name": "User",
          "birthdate": "1990-01-01"
        },
        { "trust_framework": "eidas" }
      );

      // Step 2: 2回目の設定 - 動的argsで既存accountsに新規accountsをマージ
      const config2Id = uuidv4();
      const config2Type = uuidv4();
      configIds.push(config2Id);

      await createIdaConfig(config2Id, config2Type,
        commonProcesses(
          {
            "new_accounts": accountSchema,
            "given_name": { "type": "string" },
            "family_name": { "type": "string" },
            "birthdate": { "type": "string" }
          },
          ["new_accounts", "given_name", "family_name", "birthdate"]
        ),
        [
          // 既存accountsにリクエストの新規accountsをaccount_noキーでマージ(動的args)
          {
            "from": "$.user.verified_claims.claims.accounts",
            "to": "claims.accounts",
            "functions": [
              { "name": "merge", "args": { "source": "$.application.application_details.new_accounts", "key": "account_no" } }
            ]
          },
          // マージ後のaccountsからaccount_numbersを再抽出
          {
            "from": "$.user.verified_claims.claims.accounts",
            "to": "claims.account_numbers",
            "functions": [
              { "name": "merge", "args": { "source": "$.application.application_details.new_accounts", "key": "account_no" } },
              { "name": "pluck", "args": { "field": "account_no" } }
            ]
          },
          { "from": "$.application.application_details.given_name", "to": "claims.given_name" },
          { "from": "$.application.application_details.family_name", "to": "claims.family_name" }
        ]
      );

      // 2回目: 新規口座(333)を追加 + 既存口座(111)を残高更新
      const application2Id = await applyAndApprove(
        config2Type, "apply", "verify",
        {
          "new_accounts": [
            { "account_no": "333", "type": "investment" },
            { "account_no": "111", "type": "savings_premium" }
          ],
          "given_name": "MergeTest",
          "family_name": "User",
          "birthdate": "1990-01-01"
        },
        { "trust_framework": "eidas" }
      );

      // 結果検証
      const results2Response = await get({
        url: serverConfig.identityVerificationResultResourceOwnerEndpoint + `?application_id=${application2Id}&type=${config2Type}`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`
        }
      });

      console.log("Merge results:", JSON.stringify(results2Response.data, null, 2));
      expect(results2Response.status).toBe(200);
      expect(results2Response.data.list.length).toBe(1);

      const result2 = results2Response.data.list[0];
      expect(result2).toHaveProperty("verified_claims");

      const verifiedClaims2 = result2.verified_claims;

      // merge検証: 既存2件 + 新規1件 = 3件 (account_no "111" は後勝ちで更新)
      expect(verifiedClaims2.claims).toHaveProperty("accounts");
      expect(verifiedClaims2.claims.accounts).toHaveLength(3);

      const accountNos = verifiedClaims2.claims.accounts.map(a => a.account_no);
      expect(accountNos).toContain("111");
      expect(accountNos).toContain("222");
      expect(accountNos).toContain("333");

      // account_no "111" はmergeの後勝ちでtypeが更新されている
      const account111 = verifiedClaims2.claims.accounts.find(a => a.account_no === "111");
      expect(account111.type).toBe("savings_premium");

      // pluck検証: merge後のaccountsからaccount_noが抽出されている
      expect(verifiedClaims2.claims).toHaveProperty("account_numbers");
      expect(verifiedClaims2.claims.account_numbers).toHaveLength(3);
      expect(verifiedClaims2.claims.account_numbers).toContain("111");
      expect(verifiedClaims2.claims.account_numbers).toContain("222");
      expect(verifiedClaims2.claims.account_numbers).toContain("333");

      // 基本claims引き継ぎ
      expect(verifiedClaims2.claims.given_name).toBe("MergeTest");
      expect(verifiedClaims2.claims.family_name).toBe("User");

      console.log("merge with dynamic args works correctly across applications");
    });

  });

});
