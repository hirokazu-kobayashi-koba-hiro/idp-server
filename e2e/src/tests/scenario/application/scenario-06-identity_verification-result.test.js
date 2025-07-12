import { describe, expect, it } from "@jest/globals";
import { deletion, get, post, postWithJson } from "../../../lib/http";
import { clientSecretPostClient, serverConfig, federationServerConfig } from "../../testConfig";
import { createFederatedUser } from "../../../user";
import {
  getAuthenticationDeviceAuthenticationTransaction, getJwks, getUserinfo, postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications, requestToken
} from "../../../api/oauthClient";
import { verifyAndDecodeJwt } from "../../../lib/jose";
import { createBasicAuthHeader } from "../../../lib/util";

describe("identity-verification result", () => {

  describe("success pattern", () => {

    it("trust-service", async () => {

      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
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

      const type = "trust-service";

      const callbackResultEndpoint = serverConfig.identityVerificationResultEndpoint
        .replace("{type}", type);

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
          "user_id": user.sub,
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
          ],
          "last_name": "last_name",
          "first_name": "first_name",
          "last_name_kana": "last_name_kana",
          "first_name_kana": "first_name_kana",
          "nationality": "nationality",
          "email_address": "user.email_address@gmail.com",
          "mobile_phone_number": "09000000000",
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

      });
      console.log(callbackResultResponse.data);
      expect(callbackResultResponse.status).toBe(200);

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
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

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

    });
  });

});