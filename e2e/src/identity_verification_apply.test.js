import { describe, expect, it, xit } from "@jest/globals";
import { deletion, get, post } from "./lib/http";
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

      const applicationId = applyResponse.data.id;
      const externalId = applyResponse.data.external_workflow_application_id;

      const processEndpoint = serverConfig.identityVerificationProcessEndpoint
        .replace("{type}", type)
        .replace("{id}", applicationId);

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


      let applicationsResponse = await get({
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=applying&trust_framework=eidas&external_workflow_delegation=mocky`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tokenResponse.access_token}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toBe(applicationId);

      const callbackEndpoint = serverConfig.identityVerificationApplicationsStaticCallbackExaminationEndpoint
        .replace("{type}", type);

      let callbackExaminationResponse = await post({
        url: callbackEndpoint,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tokenResponse.access_token}`
        },
        body: {
          "application_id": externalId,
          "step": "first-examination",
          "comment": "test comment",
          "rejected": false
        }
      });
      console.log(callbackExaminationResponse.data);
      expect(callbackExaminationResponse.status).toBe(200);


      applicationsResponse = await get({
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=examination_processing&trust_framework=eidas&external_workflow_delegation=mocky`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tokenResponse.access_token}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toBe(applicationId);

      const callbackResultEndpoint = serverConfig.identityVerificationApplicationsStaticCallbackResultEndpoint
        .replace("{type}", type);
      const callbackResultResponse = await post({
        url: callbackResultEndpoint,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tokenResponse.access_token}`
        },
        body: {
          "application_id": externalId,
          "verification": {
            "trust_framework": "eidas",
            "evidence": [
              {
                "type": "electronic_record",
                "check_details": [
                  {
                    "check_method": "kbv",
                    "organization": "TheCreditBureau",
                    "txn": "kbv1-hf934hn09234ng03jj3"
                  }
                ],
                "time": "2021-04-09T14:12Z",
                "record": {
                  "type": "mortgage_account",
                  "source": {
                    "name": "TheCreditBureau"
                  }
                }
              },
              {
                "type": "electronic_record",
                "check_details": [
                  {
                    "check_method": "kbv",
                    "organization": "OpenBankingTPP",
                    "txn": "kbv2-nm0f23u9459fj38u5j6"
                  }
                ],
                "time": "2021-04-09T14:12Z",
                "record": {
                  "type": "bank_account",
                  "source": {
                    "name": "TheBank"
                  }
                }
              }
            ]
          },
          "claims": {
            "given_name": "Sarah",
            "family_name": "Meredyth",
            "birthdate": "1976-03-11",
            "place_of_birth": {
              "country": "UK"
            },
            "address": {
              "locality": "Edinburgh",
              "postal_code": "EH1 9GP",
              "country": "UK",
              "street_address": "122 Burns Crescent"
            }
          }
        }

      });
      console.log(callbackResultResponse.data);
      expect(callbackResultResponse.status).toBe(200);

      applicationsResponse = await get({
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=approved&trust_framework=eidas&external_workflow_delegation=mocky`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tokenResponse.access_token}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toBe(applicationId);

      const deleteUrl = serverConfig.identityVerificationApplicationsDeletionEndpoint
        .replace("{type}", type)
        .replace("{id}", applicationId);

      const deleteResponse = await deletion({
        url: deleteUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tokenResponse.access_token}`
        }
      });

      expect(deleteResponse.status).toBe(200);

    });
  });

  describe("error pattern", () => {

    xit("continuous-customer-due-diligence", async () => {

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