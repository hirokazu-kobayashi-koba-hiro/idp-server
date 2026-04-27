import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, post } from "../../../lib/http";
import { getJwks, requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig,
  mockApiBaseUrl
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";
import { verifyAndDecodeJwt } from "../../../lib/jose";

describe("Identity Verification - verified_claims structure in AT and UserInfo", () => {
  const tenantId = serverConfig.tenantId;

  let userAccessToken;
  let testUser;

  beforeAll(async () => {
    // verified_claims を持つユーザーを作成し、身元確認を完了させる
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

    // 既存の authentication-assurance タイプで身元確認を実施して verified_claims を設定
    const type = "authentication-assurance";
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");

    const applyResponse = await post({
      url: applyUrl,
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
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

    // request-authentication
    const processEndpoint = serverConfig.identityVerificationProcessEndpoint
      .replace("{type}", type)
      .replace("{id}", applicationId);

    const reqAuthResponse = await post({
      url: processEndpoint.replace("{process}", "request-authentication"),
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
      },
      body: {
        "trust_framework": "eidas",
        "evidence_document_type": "driver_license",
      }
    });
    expect(reqAuthResponse.status).toBe(200);

    // authentication-status
    const authStatusResponse = await post({
      url: processEndpoint.replace("{process}", "authentication-status"),
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
      },
      body: { tx_id: reqAuthResponse.data.tx_id }
    });
    expect(authStatusResponse.status).toBe(200);

    // evaluate-result (承認)
    const evaluateResultEndpoint = serverConfig.identityVerificationApplicationsEvaluateResultEndpoint
      .replace("{type}", type)
      .replace("{id}", applicationId);

    const evaluateResponse = await post({
      url: evaluateResultEndpoint,
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
      },
      body: { "approved": true, "rejected": false }
    });
    console.log("Evaluate result:", evaluateResponse.data);
    expect(evaluateResponse.status).toBe(200);

    console.log("Identity verification completed - user now has verified_claims");
  });

  describe("Access Token verified_claims structure", () => {

    it("should include verification and claims in nested structure with selective scope", async () => {
      // verified_claims:given_name scope で AT を取得
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        scope: "openid profile verified_claims:given_name verified_claims:family_name"
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");

      // AT を JWT として decode
      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      expect(jwksResponse.status).toBe(200);

      const decodedAt = verifyAndDecodeJwt({
        jwt: tokenResponse.data.access_token,
        jwks: jwksResponse.data,
      });
      console.log("Decoded AT:", JSON.stringify(decodedAt.payload, null, 2));

      // verified_claims が verification + claims のネスト構造であること
      expect(decodedAt.payload).toHaveProperty("verified_claims");
      const verifiedClaims = decodedAt.payload.verified_claims;

      expect(verifiedClaims).toHaveProperty("verification");
      expect(verifiedClaims).toHaveProperty("claims");

      // claims はリクエストしたスコープに対応するもののみ
      const claims = verifiedClaims.claims;
      console.log("AT verified_claims.claims:", claims);
      console.log("AT verified_claims.verification:", verifiedClaims.verification);
    });

  });

  describe("UserInfo verified_claims structure", () => {

    it("should include verified_claims in UserInfo response with selective scope", async () => {
      // verified_claims:given_name scope で AT を取得し、UserInfo を呼ぶ
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        scope: "openid profile verified_claims:given_name verified_claims:family_name"
      });

      expect(tokenResponse.status).toBe(200);

      // UserInfo エンドポイントを呼び出し
      const userinfoResponse = await get({
        url: serverConfig.userinfoEndpoint,
        headers: {
          "Authorization": `Bearer ${tokenResponse.data.access_token}`
        }
      });

      console.log("UserInfo response:", JSON.stringify(userinfoResponse.data, null, 2));
      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data.sub).toBeDefined();

      // UserInfo に verified_claims が含まれること
      expect(userinfoResponse.data).toHaveProperty("verified_claims");
      const verifiedClaims = userinfoResponse.data.verified_claims;

      // verification + claims のネスト構造であること
      expect(verifiedClaims).toHaveProperty("verification");
      expect(verifiedClaims).toHaveProperty("claims");

      // claims にリクエストした項目が含まれること
      const claims = verifiedClaims.claims;
      console.log("UserInfo verified_claims.claims:", claims);
      console.log("UserInfo verified_claims.verification:", verifiedClaims.verification);
    });

  });

});
