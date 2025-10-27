import { describe, expect, it } from "@jest/globals";
import { deletion, get, post, postWithJson } from "../../../lib/http";
import { clientSecretPostClient, serverConfig, federationServerConfig } from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import {
  getAuthenticationDeviceAuthenticationTransaction, getJwks, getUserinfo, postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications, requestToken
} from "../../../api/oauthClient";
import { verifyAndDecodeJwt } from "../../../lib/jose";
import { createBasicAuthHeader } from "../../../lib/util";

describe("identity-verification application", () => {

  describe("success pattern", () => {

    it("investment-account-opening", async () => {

      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });
      console.log(user);
      const { authenticationDeviceId } = await registerFidoUaf({ accessToken: accessToken});

      const ciba = serverConfig.ciba;

      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email transfers claims:authentication_devices claims:ex_sub " + clientSecretPostClient.identityVerificationScope,
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
          "mobile_phone_number": user.phone_number,
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
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=applying`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toEqual(applicationId);
      expect(applicationsResponse.data.list[0].application_details.external_application_id).toEqual(externalId);
      expect(applicationsResponse.data.list[0].type).toEqual(type);
      expect(applicationsResponse.data.list[0].tenant_id).toEqual(serverConfig.tenantId);
      expect(applicationsResponse.data.list[0].client_id).toEqual(clientSecretPostClient.clientId);
      expect(applicationsResponse.data.list[0].user_id).toEqual(user.sub);
      expect(applicationsResponse.data.list[0]).toHaveProperty("application_details");
      expect(applicationsResponse.data.list[0].status).toEqual("applying");
      expect(applicationsResponse.data.list[0]).toHaveProperty("requested_at");
      expect(applicationsResponse.data.list[0].attributes.label).toEqual("証券口座開設");


      const callbackEndpoint = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
        .replace("{type}", type)
        .replace("{callbackName}", "callback-examination");

      let callbackExaminationResponse = await post({
        url: callbackEndpoint,
        headers: {
          "Content-Type": "application/json",
          ...createBasicAuthHeader({
            username: serverConfig.identityVerification.basicAuth.username,
            password: serverConfig.identityVerification.basicAuth.password
          })
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
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=examination_processing`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toEqual(applicationId);
      expect(applicationsResponse.data.list[0].application_details.external_application_id).toEqual(externalId);
      expect(applicationsResponse.data.list[0].type).toEqual(type);
      expect(applicationsResponse.data.list[0].tenant_id).toEqual(serverConfig.tenantId);
      expect(applicationsResponse.data.list[0].client_id).toEqual(clientSecretPostClient.clientId);
      expect(applicationsResponse.data.list[0].user_id).toEqual(user.sub);
      expect(applicationsResponse.data.list[0]).toHaveProperty("application_details");
      expect(applicationsResponse.data.list[0]).toHaveProperty("requested_at");
      expect(applicationsResponse.data.list[0].attributes.label).toEqual("証券口座開設");

      const callbackResultEndpoint = serverConfig.identityVerificationApplicationsPublicCallbackEndpoint
        .replace("{type}", type)
        .replace("{callbackName}", "callback-result");

      const callbackResultResponse = await post({
        url: callbackResultEndpoint,
        headers: {
          "Content-Type": "application/json",
          ...createBasicAuthHeader({
            username: serverConfig.identityVerification.basicAuth.username,
            password: serverConfig.identityVerification.basicAuth.password
          })
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
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=approved`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toEqual(applicationId);
      expect(applicationsResponse.data.list[0].application_details.external_application_id).toEqual(externalId);
      expect(applicationsResponse.data.list[0].type).toEqual(type);
      expect(applicationsResponse.data.list[0].tenant_id).toEqual(serverConfig.tenantId);
      expect(applicationsResponse.data.list[0].client_id).toEqual(clientSecretPostClient.clientId);
      expect(applicationsResponse.data.list[0].user_id).toEqual(user.sub);
      expect(applicationsResponse.data.list[0]).toHaveProperty("application_details");
      expect(applicationsResponse.data.list[0].status).toEqual("approved");
      expect(applicationsResponse.data.list[0]).toHaveProperty("requested_at");
      expect(applicationsResponse.data.list[0].attributes.label).toEqual("証券口座開設");

      backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email transfers claims:authentication_devices claims:ex_sub " + clientSecretPostClient.identityVerificationScope,
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
      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

      let authenticationResponse = await postAuthenticationDeviceInteraction({
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
      console.log(JSON.stringify(authenticationResponse.data, null, 2));
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

      let userinfoResponse = await getUserinfo({
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
      expect(resultsResponse.data.list[0]).toHaveProperty("id");
      expect(resultsResponse.data.list[0].type).toEqual(type);
      expect(resultsResponse.data.list[0].tenant_id).toEqual(serverConfig.tenantId);
      expect(resultsResponse.data.list[0].user_id).toEqual(user.sub);
      expect(resultsResponse.data.list[0].source).toEqual("application");
      expect(resultsResponse.data.list[0]).toHaveProperty("source_details");
      expect(resultsResponse.data.list[0].source_details.status).not.toBeNull();
      expect(resultsResponse.data.list[0]).toHaveProperty("verified_at");
      expect(resultsResponse.data.list[0].attributes.label).toEqual("証券口座開設");

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


    });

    it("authentication-assurance", async () => {

      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });
      console.log(user);
      const { authenticationDeviceId } = await registerFidoUaf({ accessToken: accessToken});

      const ciba = serverConfig.ciba;

      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email transfers claims:authentication_devices claims:ex_sub " + clientSecretPostClient.identityVerificationScope,
          bindingMessage: ciba.bindingMessage,
          loginHint: `sub:${user.sub}`,
          acrValues: "urn:mace:incommon:iap:gold",
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(403);
      expect(backchannelAuthenticationResponse.data.error).toEqual("access_denied");

      const type = "authentication-assurance";

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


      const requestAuthenticationResponse = await post({
        url: processEndpoint.replace("{process}", "request-authentication"),
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        },
        body: {
          "trust_framework":"eidas",
          "evidence_document_type": "driver_license",
        }
      });
      console.log(requestAuthenticationResponse.data);
      expect(requestAuthenticationResponse.status).toBe(200);

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
      console.log(authenticationStatusResponse.data);
      expect(authenticationStatusResponse.status).toBe(200);


      let applicationsResponse = await get({
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=applying`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toEqual(applicationId);
      expect(applicationsResponse.data.list[0].application_details.external_application_id).toEqual(externalId);
      expect(applicationsResponse.data.list[0].type).toEqual(type);
      expect(applicationsResponse.data.list[0].tenant_id).toEqual(serverConfig.tenantId);
      expect(applicationsResponse.data.list[0].client_id).toEqual(clientSecretPostClient.clientId);
      expect(applicationsResponse.data.list[0].user_id).toEqual(user.sub);
      expect(applicationsResponse.data.list[0]).toHaveProperty("application_details");
      expect(applicationsResponse.data.list[0].status).toEqual("applying");
      expect(applicationsResponse.data.list[0]).toHaveProperty("requested_at");


      const evaluateResultEndpoint = serverConfig.identityVerificationApplicationsEvaluateResultEndpoint
        .replace("{type}", type)
        .replace("{id}", applicationId)
      ;
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
      console.log(evaluateResultResponse.data);
      expect(evaluateResultResponse.status).toBe(200);

      applicationsResponse = await get({
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=approved`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toEqual(applicationId);

      backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email transfers claims:authentication_devices claims:ex_sub " + clientSecretPostClient.identityVerificationScope,
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
      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

      let authenticationResponse = await postAuthenticationDeviceInteraction({
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

      let userinfoResponse = await getUserinfo({
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
      expect(resultsResponse.data.list[0]).toHaveProperty("id");
      expect(resultsResponse.data.list[0].application_id).toEqual(applicationId);
      expect(resultsResponse.data.list[0].tenant_id).toEqual(serverConfig.tenantId);
      expect(resultsResponse.data.list[0].user_id).toBe(user.sub);
      expect(resultsResponse.data.list[0].type).toEqual(type);
      expect(resultsResponse.data.list[0]).toHaveProperty("verified_at");
      expect(resultsResponse.data.list[0]).toHaveProperty("verified_until");

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

    });
  });

  describe("cancel pattern", () => {
    it("investment-account-opening", async () => {

      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });
      console.log(user);
      const { authenticationDeviceId } = await registerFidoUaf({ accessToken: accessToken});

      const ciba = serverConfig.ciba;

      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email transfers claims:authentication_devices claims:ex_sub " + clientSecretPostClient.identityVerificationScope,
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
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=applying`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toEqual(applicationId);
      expect(applicationsResponse.data.list[0].application_details.external_application_id).toEqual(externalId);
      expect(applicationsResponse.data.list[0].type).toEqual(type);
      expect(applicationsResponse.data.list[0].tenant_id).toEqual(serverConfig.tenantId);
      expect(applicationsResponse.data.list[0].client_id).toEqual(clientSecretPostClient.clientId);
      expect(applicationsResponse.data.list[0].user_id).toEqual(user.sub);
      expect(applicationsResponse.data.list[0]).toHaveProperty("application_details");
      expect(applicationsResponse.data.list[0].status).toEqual("applying");
      expect(applicationsResponse.data.list[0]).toHaveProperty("requested_at");



      let cancelResponse = await post({
        url: processEndpoint.replace("{process}", "cancel"),
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        },
        body: {
          "application_id": externalId,
        }
      });
      console.log(cancelResponse.data);
      expect(cancelResponse.status).toBe(200);

      applicationsResponse = await get({
        url: serverConfig.identityVerificationApplicationsEndpoint + `?id=${applicationId}&type=${type}&status=cancelled`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(applicationsResponse.data, null, 2));
      expect(applicationsResponse.status).toBe(200);
      expect(applicationsResponse.data.list.length).toBe(1);
      expect(applicationsResponse.data.list[0].id).toEqual(applicationId);
      expect(applicationsResponse.data.list[0].application_details.external_application_id).toEqual(externalId);
      expect(applicationsResponse.data.list[0].type).toEqual(type);
      expect(applicationsResponse.data.list[0].tenant_id).toEqual(serverConfig.tenantId);
      expect(applicationsResponse.data.list[0].client_id).toEqual(clientSecretPostClient.clientId);
      expect(applicationsResponse.data.list[0].user_id).toEqual(user.sub);
      expect(applicationsResponse.data.list[0]).toHaveProperty("application_details");
      expect(applicationsResponse.data.list[0]).toHaveProperty("requested_at");
      expect(applicationsResponse.data.list[0].status).toEqual("cancelled");
    });
  });

  describe("error pattern", () => {

    it("required params investment-account-opening", async () => {

      const type = "investment-account-opening";
      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });

      console.log(user);

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
        body: {}
      });

      console.log(applyResponse.data);
      expect(applyResponse.status).toBe(400);
      expect(applyResponse.data.error).toEqual("invalid_request");
      expect(applyResponse.data.error_description).toEqual("The identity verification request is invalid. Please review your input for missing or incorrect fields.");
      expect(applyResponse.data.error_messages).toContain("last_name is missing");
      expect(applyResponse.data.error_messages).toContain("last_name is missing");
      expect(applyResponse.data.error_messages).toContain("first_name is missing");
      expect(applyResponse.data.error_messages).toContain("last_name_kana is missing");
      expect(applyResponse.data.error_messages).toContain("first_name_kana is missing");
      expect(applyResponse.data.error_messages).toContain("birthdate is missing");
      expect(applyResponse.data.error_messages).toContain("nationality is missing");
      expect(applyResponse.data.error_messages).toContain("email_address is missing");
      expect(applyResponse.data.error_messages).toContain("mobile_phone_number is missing");
      expect(applyResponse.data.error_messages).toContain("address is missing");
    });

    it("invalid type params investment-account-opening", async () => {

      const type = "investment-account-opening";
      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });

      console.log(user);

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
          "last_name": {"name": "value"},
          "first_name": {"name": "value"},
          "last_name_kana": {"name": "value"},
          "first_name_kana": {"name": "value"},
          "birthdate": {"name": "value"},
          "nationality": {"name": "value"},
          "email_address": {"name": "value"},
          "mobile_phone_number": {"name": "value"},
          "address": {
            "street_address": {"name": "value"},
            "locality": {"name": "value"},
            "region": {"name": "value"},
            "postal_code": {"name": "value"},
            "country": {"name": "value"}
          }
        }
      });

      console.log(applyResponse.data);
      expect(applyResponse.status).toBe(400);
      expect(applyResponse.data.error).toEqual("invalid_request");
      expect(applyResponse.data.error_description).toEqual("The identity verification request is invalid. Please review your input for missing or incorrect fields.");
      expect(applyResponse.data.error_messages).toContain("last_name is not a string");
      expect(applyResponse.data.error_messages).toContain("last_name is not a string");
      expect(applyResponse.data.error_messages).toContain("first_name is not a string");
      expect(applyResponse.data.error_messages).toContain("last_name_kana is not a string");
      expect(applyResponse.data.error_messages).toContain("first_name_kana is not a string");
      expect(applyResponse.data.error_messages).toContain("birthdate is not a string");
      expect(applyResponse.data.error_messages).toContain("nationality is not a string");
      expect(applyResponse.data.error_messages).toContain("email_address is not a string");
      expect(applyResponse.data.error_messages).toContain("mobile_phone_number is not a string");
      expect(applyResponse.data.error_messages).toContain("address.region is not a string");
      expect(applyResponse.data.error_messages).toContain("address.country is not a string");
      expect(applyResponse.data.error_messages).toContain("address.locality is not a string");
      expect(applyResponse.data.error_messages).toContain("address.postal_code is not a string");
      expect(applyResponse.data.error_messages).toContain("address.street_address is not a string");
    });

    it("email unmatched continuous-customer-due-diligence", async () => {

      const type = "continuous-customer-due-diligence";
      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });

      console.log(user);

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
      expect(applyResponse.status).toBe(400);
      expect(applyResponse.data.error).toEqual("pre_hook_validation_failed");
      expect(applyResponse.data.error_description).toEqual("Pre-hook validation failed for identity verification request");
      expect(applyResponse.data.error_messages[0]).toContain("User claim verification failed. unmatched: $.request_body.email_address, user:email");
    });
  });
});