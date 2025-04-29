import { describe, expect, it } from "@jest/globals";
import { post } from "./lib/http";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { loginForClientSecretPost } from "./ciba/login";

describe("identity-verification application", () => {

  describe("success pattern", () => {

    it("investment-account-opening", async () => {

      const type = "investment-account-opening";
      const tokenResponse = await loginForClientSecretPost({
        serverConfig,
        client: clientSecretPostClient,
        scope: "identity_verification_application identity_verification_delete identity_credentials_update"
      });

      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
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

      console.log(applyResponse.data);
      expect(applyResponse.status).toBe(200);

      const processEndpoint = serverConfig.identityVerificationProcessEndpoint
        .replace("{type}", type)
        .replace("{id}", applyResponse.data.id);

      const requestEkycResponse = await post({
        url: processEndpoint.replace("{process}", "request-ekyc"),
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

      const completeEkycResponse = await post({
        url: processEndpoint.replace("{process}", "complete-ekyc"),
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tokenResponse.access_token}`
        },
        body: {}
      });
      console.log(completeEkycResponse.data);
      expect(completeEkycResponse.status).toBe(200);

    });
  });

  describe("error pattern", () => {

    it("continuous-customer-due-diligence", async () => {

      const type = "continuous-customer-due-diligence";
      const tokenResponse = await loginForClientSecretPost({
        serverConfig,
        client: clientSecretPostClient,
        scope: "identity_verification_application identity_verification_delete identity_credentials_update"
      });

      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
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

      console.log(applyResponse.data);
      expect(applyResponse.status).toBe(400);
      expect(applyResponse.data.error).toEqual("invalid_request");
      expect(applyResponse.data.error_description).toEqual("identity verification application is invalid.");
      expect(applyResponse.data.error_details[0]).toContain("user does not have approved application required any type");
    });
  });
});