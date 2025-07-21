import { describe, expect, it } from "@jest/globals";
import { deletion, get, post, postWithJson } from "../../../lib/http";
import { clientSecretPostClient, serverConfig, federationServerConfig, publicClient } from "../../testConfig";
import { createFederatedUser } from "../../../user";
import {
  getAuthenticationDeviceAuthenticationTransaction, getJwks, getUserinfo, postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications, requestToken
} from "../../../api/oauthClient";
import { verifyAndDecodeJwt } from "../../../lib/jose";

describe("multi client", () => {

  describe("success pattern", () => {

    it("multi-app fido", async () => {

      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: publicClient,
        adminClient: clientSecretPostClient
      });

      console.log(user);

      let mfaRegistrationResponse =
        await postWithJson({
          url: serverConfig.resourceOwnerEndpoint + "/mfa/fido-uaf-registration",
          body: {
            "platform": "Android",
            "os": "Android15",
            "model": "galaxy z fold 6",
            "notification_channel": "fcm",
            "notification_token": "test token",
            "preferred_for_notification": true
          },
          headers: {
            "Authorization": `Bearer ${accessToken}`
          }
        });
      console.log(mfaRegistrationResponse.data);
      expect(mfaRegistrationResponse.status).toBe(200);

      const transactionId = mfaRegistrationResponse.data.id;

      let authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: transactionId,
        interactionType: "fido-uaf-registration-challenge",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      console.log(authenticationResponse.data);
      expect(authenticationResponse.status).toBe(200);

      const fidoUafFacetsResponse = await get({
        url: serverConfig.fidoUafFacetsEndpoint,
        headers: {
          "Content-Type": "application/json",
        }
      });
      console.log(fidoUafFacetsResponse.data);
      expect(fidoUafFacetsResponse.status).toBe(200);

      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: transactionId,
        interactionType: "fido-uaf-registration",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(200);
      expect(authenticationResponse.data).toHaveProperty("device_id");
      const authenticationDeviceId = authenticationResponse.data.device_id;

      let userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(userinfoResponse.data, null, 2));
      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data.sub).toEqual(user.sub);
      expect(userinfoResponse.data).toHaveProperty("authentication_devices");
      expect(userinfoResponse.data.authentication_devices.length).toBe(1);
      expect(userinfoResponse.data).toHaveProperty("mfa");
      expect(userinfoResponse.data.authentication_devices[0].id).toEqual(authenticationDeviceId);

      const ciba = serverConfig.ciba;

      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email transfers " + clientSecretPostClient.identityVerificationScope,
          bindingMessage: ciba.bindingMessage,
          loginHint: `sub:${user.sub}`,
          acrValues: "urn:mace:incommon:iap:gold",
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(403);
      expect(backchannelAuthenticationResponse.data.error).toEqual("access_denied");

      const type = "investment-account-opening";

      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", "apply")
      ;
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

      console.log(applyResponse.data);
      expect(applyResponse.status).toBe(200);

      const applicationId = applyResponse.data.id;
      const externalId = applyResponse.data.external_application_id;

      const processEndpoint = serverConfig.identityVerificationProcessEndpoint
        .replace("{type}", type)
        .replace("{id}", applicationId);

      const registrationResponse = await post({
        url: processEndpoint.replace("{process}", "crm-registration"),
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        },
        body: {
          "trust_framework":"eidas",
          "evidence_document_type": "driver_license",
        }
      });
      console.log(registrationResponse.data);
      expect(registrationResponse.status).toBe(200);


      const requestEkycResponse = await post({
        url: processEndpoint.replace("{process}", "request-ekyc"),
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        },
        body: {
          "trust_framework":"eidas",
          "evidence_document_type": "driver_license",
        }
      });
      console.log(requestEkycResponse.data);
      expect(requestEkycResponse.status).toBe(200);

      const completeEkycResponse = await post({
        url: processEndpoint.replace("{process}", "complete-ekyc"),
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        },
        body: {}
      });
      console.log(completeEkycResponse.data);
      expect(completeEkycResponse.status).toBe(200);


      let applicationsResponse = await get({
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=applying&trust_framework=eidas&external_workflow_delegation=mocky`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toBe(applicationId);

      const callbackEndpoint = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
        .replace("{type}", type)
        .replace("{callbackName}", "callback-examination");

      let callbackExaminationResponse = await post({
        url: callbackEndpoint,
        headers: {
          "Content-Type": "application/json"
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
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toBe(applicationId);

      const callbackResultEndpoint = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
        .replace("{type}", type)
        .replace("{callbackName}", "callback-result");
      const callbackResultResponse = await post({
        url: callbackResultEndpoint,
        headers: {
          "Content-Type": "application/json"
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
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toBe(applicationId);

      // const deleteUrl = serverConfig.identityVerificationApplicationsDeletionEndpoint
      //   .replace("{type}", type)
      //   .replace("{id}", applicationId);
      //
      // const deleteResponse = await deletion({
      //   url: deleteUrl,
      //   headers: {
      //     "Content-Type": "application/json",
      //     "Authorization": `Bearer ${accessToken}`
      //   }
      // });
      //
      // expect(deleteResponse.status).toBe(200);

      backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email transfers " + clientSecretPostClient.identityVerificationScope,
          bindingMessage: ciba.bindingMessage,
          loginHint: `device:${authenticationDeviceId},idp:${federationServerConfig.providerName}`,
          acrValues: "urn:mace:incommon:iap:gold",
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);


      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log(JSON.stringify(authenticationTransactionResponse.data, null, 2));
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];

      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "fido-uaf-registration-challenge",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(200);

      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "fido-uaf-authentication-challenge",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(200);

      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "fido-uaf-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(200);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.scope).toContain("transfers");

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      expect(decodedIdToken.payload).toHaveProperty("amr");

      const decodedAccessToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.access_token,
        jwks: jwksResponse.data
      });
      console.log(JSON.stringify(decodedAccessToken, null, 2));

      userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          "Authorization": `Bearer ${tokenResponse.data.access_token}`
        }
      });
      console.log(JSON.stringify(userinfoResponse.data, null, 2));
      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data.sub).toEqual(user.sub);
      expect(userinfoResponse.data).toHaveProperty("authentication_devices");
      expect(userinfoResponse.data.authentication_devices.length).toBe(1);
      expect(userinfoResponse.data).toHaveProperty("mfa");

      let resultsResponse = await get({
        url: serverConfig.identityVerificationResultResourceOwnerEndpoint + `?application_id=${applicationId}&type=${type}`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(resultsResponse.data, null, 2));
      expect(resultsResponse.status).toBe(200);
      expect(resultsResponse.data.list.length).toBe(1);
      expect(resultsResponse.data.list[0].application_id).toBe(applicationId);

    });


  });

});