import { describe, expect, it } from "@jest/globals";
import { post } from "./lib/http";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { loginForClientSecretPost } from "./ciba/login";

describe("identity-verification application", () => {

  describe("success pattern", () => {

    it("apply", async () => {

      const tokenResponse = await loginForClientSecretPost({
        serverConfig,
        client: clientSecretPostClient,
        scope: "identity_verification_application identity_verification_delete identity_credentials_update"
      });

      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", "ekyc")
        .replace("{process}", "apply")
      ;
      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tokenResponse.access_token}`
        },
        body: {
          "last_name": "john",
          "first_name": "mac",
          "last_name_kana": "jon",
          "first_name_kana": "mac",
          "birthdate": "1992-02-12",
          "nationality": "JP",
          "email_address": "test@gmail.com",
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

      console.log(applyResponse.data);
      expect(applyResponse.status).toBe(200);

      const processEndpoint = serverConfig.identityVerificationProcessEndpoint
        .replace("{type}", "ekyc")
        .replace("{id}", applyResponse.data.id);
      const requestEkycResponse = await post({
        url: processEndpoint.replace("{process}", "issue-ekyc"),
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tokenResponse.access_token}`
        },
        body: {
          "trust_framework":"eidas",
          "document_type": "driver_license",
        }
      });
      console.log(requestEkycResponse.data);
      expect(requestEkycResponse.status).toBe(200);

    });
  });
});