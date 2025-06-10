import { describe, expect, it, xit } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction, getJwks, getUserinfo,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "./api/oauthClient";
import {
  clientSecretPostClient,
  privateKeyJwtClient,
  serverConfig,
} from "./testConfig";
import { createJwt, createJwtWithPrivateKey, generateJti, verifyAndDecodeJwt } from "./lib/jose";
import { isNumber, toEpocTime } from "./lib/util";
import { get, post, postWithJson } from "./lib/http";
import { loginForClientSecretPost } from "./ciba/login";

describe("user - mfa registration", () => {

  xit("fido-uaf", async () => {
    const tokenResponse = await loginForClientSecretPost({
      serverConfig,
      client: clientSecretPostClient,
      scope: "claims:authentication_devices"
    });

    let mfaRegistrationResponse =
      await postWithJson({
        url: serverConfig.usersEndpoint + "/mfa-registration",
        body: {
          "flow": "fido-uaf-registration",
          "platform": "Android",
          "os": "Android15",
          "model": "galaxy z fold 6",
          "notification_channel": "fcm",
          "notification_token": "test token",
          "preferred_for_notification": true
        },
        headers: {
          "Authorization": `Bearer ${tokenResponse.access_token}`
        }
      });
    console.log(mfaRegistrationResponse.data);
    expect(mfaRegistrationResponse.status).toBe(200);

    let authenticationResponse;
    const transactionId = mfaRegistrationResponse.data.id;
    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration-challenge",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      }
    });
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

    let userinfoResponse = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: {
        "Authorization": `Bearer ${tokenResponse.access_token}`
      }
    });
    console.log(JSON.stringify(userinfoResponse.data, null, 2));
    expect(userinfoResponse.status).toBe(200);


    mfaRegistrationResponse =
      await postWithJson({
        url: serverConfig.usersEndpoint + "/mfa-registration",
        body: {
          "flow": "fido-uaf-deregistration",
          "authentication_device_id": "53f0943a-f9de-4e5a-a58a-e5585d745570"
        },
        headers: {
          "Authorization": `Bearer ${tokenResponse.access_token}`
        }
      });
    console.log(mfaRegistrationResponse.data);
    expect(mfaRegistrationResponse.status).toBe(200);

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-deregistration",
      body: {
        authentication_device_id: "53f0943a-f9de-4e5a-a58a-e5585d745570",
      }
    });
    expect(authenticationResponse.status).toBe(200);

    userinfoResponse = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: {
        "Authorization": `Bearer ${tokenResponse.access_token}`
      }
    });
    console.log(JSON.stringify(userinfoResponse.data, null, 2));
    expect(userinfoResponse.status).toBe(200);

  });


});
