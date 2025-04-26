import { describe, expect, it } from "@jest/globals";
import { post } from "./lib/http";
import { serverConfig } from "./testConfig";

describe("", () => {

  describe("success pattern", () => {

    it("apply", async () => {

      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", "ekyc")
        .replace("{process}", "apply")
      ;
      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json"
        },
        body: {
          "last_name": "john",
          "first_name": "mac",
          "last_name_kana": "jon",
          "first_name_kana": "mac",
          "birthdate": "1992-02-12",
          "nationality": "JP",
          "address": {
            "street_address": "test",
            "locality": "test",
            "region": "test",
            "postal_code": "1000001",
            "country": "JP"
          },
          "document_type": "driver_license",
          "document_number": "A123456789",
          "document_expiry_date": "2030-12-31",
          "selfie_image": "iVBORw0KGgoAAAANSUhEUgAAAAUA...",
          "document_front_image": "iVBORw0KGgoAAAANSUhEUgAAAAUA...",
          "document_back_image": "iVBORw0KGgoAAAANSUhEUgAAAAUA..."
        }

      });

      console.log(applyResponse.data);
      expect(applyResponse.status).toBe(200);
    });
  });
});