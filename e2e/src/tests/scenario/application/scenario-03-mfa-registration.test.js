import { describe, expect, it } from "@jest/globals";

import {
  getUserinfo,
  postAuthenticationDeviceInteraction,
} from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient, federationServerConfig,
  serverConfig
} from "../../testConfig";
import { get, patchWithJson, postWithJson } from "../../../lib/http";
import { createFederatedUser } from "../../../user";
import {generateRandomUser, generateValidCredentialFromChallenge} from "../../../lib/fido/fido2";

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

      xit("fido2", async () => {
        const tenantId = serverConfig.tenantId;
          const { user, accessToken } = await createFederatedUser({
              serverConfig: serverConfig,
              federationServerConfig: federationServerConfig,
              client: clientSecretPostClient,
              adminClient: clientSecretPostClient
          });

          console.log(user);

          let mfaRegistrationResponse =
              await postWithJson({
                  url: `${backendUrl}/${tenantId}/v1/me/mfa/fido2-registration`,
                  body: {
                      "app_name": "idp-server-app",
                      "platform": "Android",
                      "os": "Android15",
                      "model": "galaxy z fold 6",
                      "locale": "ja",
                      "notification_channel": "fcm",
                      "notification_token": "test token",
                      "priority": 1,
                      action: "reset"
                  },
                  headers: {
                      "Authorization": `Bearer ${accessToken}`
                  }
              });
          console.log(mfaRegistrationResponse.data);
          expect(mfaRegistrationResponse.status).toBe(200);

          const discoverableUser = generateRandomUser();
          const requestBody = {
              username: discoverableUser.username,
              displayName: discoverableUser.displayName,
              authenticatorSelection: {
                  authenticatorAttachment: "platform",
                  requireResidentKey: true,
                  userVerification: "required"
              },
              attestation: "none",
              extensions: {
                  credProps: true
              }
          };

          console.log(JSON.stringify(requestBody, null, 2));

          let authenticationResponse;
          let transactionId = mfaRegistrationResponse.data.id;
          authenticationResponse = await postWithJson({
              url: `${backendUrl}/${tenantId}/v1/authentications/${transactionId}/fido2-registration-challenge`,
              body: requestBody
          });
          console.log(JSON.stringify(authenticationResponse.data, null, 2));
          expect(authenticationResponse.status).toBe(200);

          const validCredential = generateValidCredentialFromChallenge(authenticationResponse.data);
          console.log(JSON.stringify(validCredential, null, 2));

          authenticationResponse = await postWithJson({
              url: `${backendUrl}/${tenantId}/v1/authentications/${transactionId}/fido2-registration`,
              body: validCredential
          });
          console.log(JSON.stringify(authenticationResponse.data, null, 2));
          expect(authenticationResponse.status).toBe(200);


          let userinfoResponse = await get({
              url: `${backendUrl}/${tenantId}/v1/userinfo`,
              headers: {
                  "Authorization": `Bearer ${accessToken}`
              }
          });
          console.log(JSON.stringify(userinfoResponse.data, null, 2));
          expect(userinfoResponse.status).toBe(200);
          expect(userinfoResponse.data.sub).toEqual(user.sub);
          expect(userinfoResponse.data).toHaveProperty("authentication_devices");
          expect(userinfoResponse.data.authentication_devices.length).toBe(1);
          // expect(userinfoResponse.data.authentication_devices[0].id).toEqual(authenticationDeviceId);
          expect(userinfoResponse.data.authentication_devices[0]).toHaveProperty("app_name");
          expect(userinfoResponse.data.authentication_devices[0].platform).toEqual("Android");
          expect(userinfoResponse.data.authentication_devices[0].os).toEqual("Android15");
          expect(userinfoResponse.data.authentication_devices[0].model).toEqual("galaxy z fold 6");
          expect(userinfoResponse.data.authentication_devices[0].locale).toEqual("ja");
          expect(userinfoResponse.data.authentication_devices[0].notification_channel).toEqual("fcm");
          expect(userinfoResponse.data.authentication_devices[0].notification_token).toEqual("test token");
          expect(userinfoResponse.data.authentication_devices[0].available_methods).toContain("fido2");
          expect(userinfoResponse.data.authentication_devices[0].priority).toEqual(1);
      });

  });

  describe("reset pattern ", () => {
    it("fido-uaf - reset removes all existing FIDO-UAF devices", async () => {
      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });

      console.log(user);

      // First device registration
      let mfaRegistrationResponse =
        await postWithJson({
          url: serverConfig.resourceOwnerEndpoint + "/mfa/fido-uaf-registration",
          body: {
            "app_name": "device-1",
            "platform": "Android",
            "os": "Android15",
            "model": "device 1",
            "locale": "ja",
            "notification_channel": "fcm",
            "notification_token": "token1",
            "priority": 1
          },
          headers: {
            "Authorization": `Bearer ${accessToken}`
          }
        });
      console.log(mfaRegistrationResponse.data);
      expect(mfaRegistrationResponse.status).toBe(200);

      let authenticationResponse;
      const transactionId1 = mfaRegistrationResponse.data.id;
      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: transactionId1,
        interactionType: "fido-uaf-registration-challenge",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(200);

      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: transactionId1,
        interactionType: "fido-uaf-registration",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(200);
      const device1Id = authenticationResponse.data.device_id;

      // Second device registration (normal)
      mfaRegistrationResponse =
        await postWithJson({
          url: serverConfig.resourceOwnerEndpoint + "/mfa/fido-uaf-registration",
          body: {
            "app_name": "device-2",
            "platform": "iOS",
            "os": "iOS18",
            "model": "device 2",
            "locale": "en",
            "notification_channel": "apns",
            "notification_token": "token2",
            "priority": 2
          },
          headers: {
            "Authorization": `Bearer ${accessToken}`
          }
        });
      expect(mfaRegistrationResponse.status).toBe(200);

      const transactionId2 = mfaRegistrationResponse.data.id;
      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: transactionId2,
        interactionType: "fido-uaf-registration-challenge",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(200);

      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: transactionId2,
        interactionType: "fido-uaf-registration",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(200);
      const device2Id = authenticationResponse.data.device_id;

      // Verify we have 2 devices
      let userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log("Before reset:", JSON.stringify(userinfoResponse.data.authentication_devices, null, 2));
      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data.authentication_devices.length).toBe(2);

      // Third device registration with reset action
      mfaRegistrationResponse =
        await postWithJson({
          url: serverConfig.resourceOwnerEndpoint + "/mfa/fido-uaf-registration",
          body: {
            "action": "reset",
            "app_name": "device-3-reset",
            "platform": "Android",
            "os": "Android16",
            "model": "device 3 reset",
            "locale": "ja",
            "notification_channel": "fcm",
            "notification_token": "token3",
            "priority": 1
          },
          headers: {
            "Authorization": `Bearer ${accessToken}`
          }
        });
      console.log("Reset registration response:", mfaRegistrationResponse.data);
      expect(mfaRegistrationResponse.status).toBe(200);

      const transactionId3 = mfaRegistrationResponse.data.id;
      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: transactionId3,
        interactionType: "fido-uaf-registration-challenge",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(200);

      authenticationResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: transactionId3,
        interactionType: "fido-uaf-registration",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(authenticationResponse.status).toBe(200);
      const device3Id = authenticationResponse.data.device_id;

      // Verify that only the reset device exists (previous devices removed)
      userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log("After reset:", JSON.stringify(userinfoResponse.data.authentication_devices, null, 2));
      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data).toHaveProperty("authentication_devices");
      expect(userinfoResponse.data.authentication_devices.length).toBe(1);
      expect(userinfoResponse.data.authentication_devices[0].id).toEqual(device3Id);
      expect(userinfoResponse.data.authentication_devices[0].app_name).toEqual("device-3-reset");
      expect(userinfoResponse.data.authentication_devices[0].platform).toEqual("Android");
      expect(userinfoResponse.data.authentication_devices[0].os).toEqual("Android16");
      expect(userinfoResponse.data.authentication_devices[0].model).toEqual("device 3 reset");
      expect(userinfoResponse.data.authentication_devices[0].available_methods).toContain("fido-uaf");

      // Verify that device1Id and device2Id are no longer present
      const deviceIds = userinfoResponse.data.authentication_devices.map(device => device.id);
      expect(deviceIds).not.toContain(device1Id);
      expect(deviceIds).not.toContain(device2Id);
      expect(deviceIds).toContain(device3Id);
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

  describe("error pattern - insufficient scope", () => {
    it("patch authentication device fails without claims:authentication_devices scope", async () => {
      // まず通常のスコープでユーザー作成・デバイス登録
      const { user, accessToken: fullAccessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });

      console.log(user);

      // デバイス登録
      let mfaRegistrationResponse =
        await postWithJson({
          url: serverConfig.resourceOwnerEndpoint + "/mfa/fido-uaf-registration",
          body: {
            "app_name": "test-app",
            "platform": "Android",
            "os": "Android15",
            "model": "test device",
            "locale": "ja",
            "notification_channel": "fcm",
            "notification_token": "test token",
            "priority": 1
          },
          headers: {
            "Authorization": `Bearer ${fullAccessToken}`
          }
        });
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
      expect(authenticationResponse.status).toBe(200);
      const deviceId = authenticationResponse.data.device_id;

      // 制限されたスコープで新しいトークン取得（claims:authentication_devices を除外）
      const { accessToken: limitedAccessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient,
        scope: "openid profile email"  // claims:authentication_devices スコープを除外
      });

      // 制限されたトークンでPATCH実行
      const patchResponse = await patchWithJson({
        url: serverConfig.resourceOwnerEndpoint + `/authentication-devices/${deviceId}`,
        body: {
          "app_name": "updated-app",
          "platform": "iOS",
          "os": "iOS18",
          "model": "updated device",
          "locale": "en",
          "notification_channel": "apns",
          "notification_token": "updated token",
          "priority": 10
        },
        headers: {
          "Authorization": `Bearer ${limitedAccessToken}`
        }
      });

      console.log(patchResponse.data);
      expect(patchResponse.status).toBe(403);
      expect(patchResponse.data.error).toBe("insufficient_scope");
      expect(patchResponse.data.error_description).toBe("The request requires 'claims:authentication_devices' scope");
      expect(patchResponse.data.scope).toBe("claims:authentication_devices");
    });
  });

});
