import { describe, expect, it } from "@jest/globals";
import { deletion, get, post } from "../../../lib/http";
import { clientSecretPostClient, serverConfig, federationServerConfig } from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";

describe("Identity Verification Result Search", () => {

  describe("verified_until search condition", () => {

    it("should not return 500 error when searching with verified_until_from and verified_until_to", async () => {
      // Issue #1181: verified_until検索条件でSQLエラー（500）が発生しないことを確認
      // 注: verified_untilの算出ロジックは未実装のため、値の検証は行わない (Issue #1182)

      // Step 1: ユーザー作成とFIDO登録
      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });
      console.log("Created user:", user.sub);
      await registerFidoUaf({ accessToken: accessToken });

      const type = "authentication-assurance";

      // Step 2: 身元確認の申込み
      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", "apply");

      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        },
        body: {
          "last_name": "john",
          "first_name": "mac",
          "last_name_kana": "jon",
          "first_name_kana": "mac",
          "birthdate": "1992-02-12",
          "nationality": "JP",
          "email_address": "ito.ichiro@gmail.com",
          "mobile_phone_number": "09012345678",
          "address": {
            "street_address": "test",
            "locality": "test",
            "region": "test",
            "postal_code": "1000001",
            "country": "JP"
          }
        }
      });
      console.log("Apply response:", applyResponse.data);
      expect(applyResponse.status).toBe(200);

      const applicationId = applyResponse.data.id;

      // Step 3: 認証リクエスト
      const processEndpoint = serverConfig.identityVerificationProcessEndpoint
        .replace("{type}", type)
        .replace("{id}", applicationId);

      const requestAuthenticationResponse = await post({
        url: processEndpoint.replace("{process}", "request-authentication"),
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        },
        body: {
          "trust_framework": "eidas",
          "evidence_document_type": "driver_license",
        }
      });
      console.log("Request authentication response:", requestAuthenticationResponse.data);
      expect(requestAuthenticationResponse.status).toBe(200);

      // Step 4: 認証ステータス確認
      const authenticationStatusResponse = await post({
        url: processEndpoint.replace("{process}", "authentication-status"),
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        },
        body: {
          tx_id: requestAuthenticationResponse.data.tx_id
        }
      });
      console.log("Authentication status response:", authenticationStatusResponse.data);
      expect(authenticationStatusResponse.status).toBe(200);

      // Step 5: 承認（身元確認結果が作成される）
      const evaluateResultEndpoint = serverConfig.identityVerificationApplicationsEvaluateResultEndpoint
        .replace("{type}", type)
        .replace("{id}", applicationId);

      const evaluateResultResponse = await post({
        url: evaluateResultEndpoint,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        },
        body: {
          "approved": true,
          "rejected": false
        }
      });
      console.log("Evaluate result response:", evaluateResultResponse.data);
      expect(evaluateResultResponse.status).toBe(200);

      // Step 6: 身元確認結果が作成されたことを確認
      let resultsResponse = await get({
        url: serverConfig.identityVerificationResultResourceOwnerEndpoint + `?application_id=${applicationId}&type=${type}`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log("Results response:", JSON.stringify(resultsResponse.data, null, 2));
      expect(resultsResponse.status).toBe(200);
      expect(resultsResponse.data.list.length).toBe(1);
      expect(resultsResponse.data.list[0]).toHaveProperty("verified_until");

      // Step 7: verified_until_from で検索 - 500エラーにならないことを確認
      const pastDate = "2020-01-01T00:00:00";
      resultsResponse = await get({
        url: serverConfig.identityVerificationResultResourceOwnerEndpoint + `?type=${type}&verified_until_from=${pastDate}`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log("Search with verified_until_from:", resultsResponse.status, JSON.stringify(resultsResponse.data, null, 2));
      expect(resultsResponse.status).toBe(200);

      // Step 8: verified_until_to で検索 - 500エラーにならないことを確認
      const futureDate = "2099-12-31T23:59:59";
      resultsResponse = await get({
        url: serverConfig.identityVerificationResultResourceOwnerEndpoint + `?type=${type}&verified_until_to=${futureDate}`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log("Search with verified_until_to:", resultsResponse.status, JSON.stringify(resultsResponse.data, null, 2));
      expect(resultsResponse.status).toBe(200);

      // Step 9: verified_until_from と verified_until_to の両方で検索 - 500エラーにならないことを確認
      resultsResponse = await get({
        url: serverConfig.identityVerificationResultResourceOwnerEndpoint + `?type=${type}&verified_until_from=${pastDate}&verified_until_to=${futureDate}`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log("Search with verified_until range:", resultsResponse.status, JSON.stringify(resultsResponse.data, null, 2));
      expect(resultsResponse.status).toBe(200);

      // Step 10: クリーンアップ
      const deleteUrl = serverConfig.identityVerificationApplicationsDeletionEndpoint
        .replace("{type}", type)
        .replace("{id}", applicationId);

      const deleteResponse = await deletion({
        url: deleteUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      expect(deleteResponse.status).toBe(200);

      console.log("Test completed - verified_until search conditions do not cause SQL errors");
    });

  });

});
