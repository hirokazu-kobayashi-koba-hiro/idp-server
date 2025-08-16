import { describe, expect, it } from "@jest/globals";

import {
  getUserinfo,
  postAuthenticationDeviceInteraction,
} from "../../../api/oauthClient";
import {
  clientSecretPostClient, federationServerConfig,
  serverConfig
} from "../../testConfig";
import { get, patchWithJson, postWithJson } from "../../../lib/http";
import { createFederatedUser } from "../../../user";

describe("user - mfa registration", () => {

  describe("success pattern ", () => {
    it("fido-uaf", async () => {
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
            "app_name": "idp-server-app",
            "platform": "Android",
            "os": "Android15",
            "model": "galaxy z fold 6",
            "locale": "ja",
            "notification_channel": "fcm",
            "notification_token": "test token",
            "priority": 1
          },
          headers: {
            "Authorization": `Bearer ${accessToken}`
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
      console.log(JSON.stringify(authenticationResponse.data, null, 2));
      expect(authenticationResponse.status).toBe(200);
      expect(authenticationResponse.data).toHaveProperty("uafRequest");
      expect(authenticationResponse.data).toHaveProperty("uafRequest[0].header.op");
      expect(authenticationResponse.data).toHaveProperty("uafRequest[0].header.appID");
      expect(authenticationResponse.data).toHaveProperty("uafRequest[0].header.upv.major");
      expect(authenticationResponse.data).toHaveProperty("uafRequest[0].header.upv.minor");
      expect(authenticationResponse.data).toHaveProperty("uafRequest[0].challenge");
      expect(authenticationResponse.data).toHaveProperty("uafRequest[0].transaction");

      const fidoUafFacetsResponse = await get({
        url: serverConfig.fidoUafFacetsEndpoint,
        headers: {
          "Content-Type": "application/json",
        }
      });
      console.log(JSON.stringify(fidoUafFacetsResponse.data, null, 2));
      expect(fidoUafFacetsResponse.status).toBe(200);
      expect(fidoUafFacetsResponse.data).toHaveProperty("trustedFacets");
      expect(fidoUafFacetsResponse.data).toHaveProperty("trustedFacets[0].ids");
      expect(fidoUafFacetsResponse.data).toHaveProperty("trustedFacets[0].version");

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
      expect(userinfoResponse.data.authentication_devices[0].id).toEqual(authenticationDeviceId);
      expect(userinfoResponse.data.authentication_devices[0].app_name).toEqual("idp-server-app");
      expect(userinfoResponse.data.authentication_devices[0].platform).toEqual("Android");
      expect(userinfoResponse.data.authentication_devices[0].os).toEqual("Android15");
      expect(userinfoResponse.data.authentication_devices[0].model).toEqual("galaxy z fold 6");
      expect(userinfoResponse.data.authentication_devices[0].locale).toEqual("ja");
      expect(userinfoResponse.data.authentication_devices[0].notification_channel).toEqual("fcm");
      expect(userinfoResponse.data.authentication_devices[0].notification_token).toEqual("test token");
      expect(userinfoResponse.data.authentication_devices[0].available_methods).toContain("fido-uaf");
      expect(userinfoResponse.data.authentication_devices[0].priority).toEqual(1);

      const device = userinfoResponse.data.authentication_devices[0];

      const patchResponse = await patchWithJson({
        url: serverConfig.resourceOwnerEndpoint + `/authentication-devices/${device.id}`,
        body: {
          "app_name": "app_name",
          "platform": "iOS",
          "os": "iOS18",
          "model": "iphone15",
          "locale": "en",
          "notification_channel": "fcm",
          "notification_token": "notification_token",
          "priority": 10
        },
        headers: {
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(patchResponse.data);
      expect(patchResponse.status).toBe(200);

      userinfoResponse = await getUserinfo({
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
      expect(userinfoResponse.data.authentication_devices[0].id).toEqual(authenticationDeviceId);
      expect(userinfoResponse.data.authentication_devices[0].app_name).toEqual("app_name");
      expect(userinfoResponse.data.authentication_devices[0].platform).toEqual("iOS");
      expect(userinfoResponse.data.authentication_devices[0].os).toEqual("iOS18");
      expect(userinfoResponse.data.authentication_devices[0].model).toEqual("iphone15");
      expect(userinfoResponse.data.authentication_devices[0].locale).toEqual("en");
      expect(userinfoResponse.data.authentication_devices[0].notification_channel).toEqual("fcm");
      expect(userinfoResponse.data.authentication_devices[0].notification_token).toEqual("notification_token");
      expect(userinfoResponse.data.authentication_devices[0].available_methods).toContain("fido-uaf");
      expect(userinfoResponse.data.authentication_devices[0].priority).toEqual(10);

      mfaRegistrationResponse =
        await postWithJson({
          url: serverConfig.resourceOwnerEndpoint + "/mfa/fido-uaf-deregistration",
          body: {
            "device_id": authenticationDeviceId
          },
          headers: {
            "Authorization": `Bearer ${accessToken}`
          }
        });
      console.log(mfaRegistrationResponse.data);
      expect(mfaRegistrationResponse.status).toBe(200);

      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: mfaRegistrationResponse.data.id,
        interactionType: "fido-uaf-deregistration",
        body: {
          device_id: authenticationDeviceId,
        }
      });
      expect(authenticationResponse.status).toBe(200);

      userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(userinfoResponse.data, null, 2));
      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data.sub).toEqual(user.sub);
      expect(userinfoResponse.data).not.toHaveProperty("authentication_devices");
      expect(userinfoResponse.data).not.toHaveProperty("mfa");
    });

  });

  describe("cancel pattern ", () => {
    it("fido-uaf", async () => {
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
            "app_name": "idp-server-app",
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
        interactionType: "fido-uaf-cancel",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });

      expect(authenticationResponse.status).toBe(200);

      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: transactionId,
        interactionType: "fido-uaf-registration",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(404);

    });

  });

});
