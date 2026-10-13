import { describe, expect, it } from "@jest/globals";

import {
  getUserinfo,
  postAuthenticationDeviceInteraction,
} from "../../../api/oauthClient";
import {
  clientSecretPostClient,
  federationServerConfig,
  serverConfig,
} from "../../testConfig";
import { postWithJson } from "../../../lib/http";
import { createFederatedUser } from "../../../user";

describe("user - mfa fido-uaf deregistration device owner", () => {

  /** Creates a user and registers a FIDO-UAF device through /me/mfa. */
  const createUserWithFidoUafDevice = async () => {
    const { user, accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
    });

    const registration = await postWithJson({
      url: serverConfig.resourceOwnerEndpoint + "/mfa/fido-uaf-registration",
      body: {
        app_name: "idp-server-app",
        platform: "Android",
        os: "Android15",
        model: "galaxy z fold 6",
        locale: "ja",
        notification_channel: "fcm",
        notification_token: "test token",
        priority: 1,
      },
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(registration.status).toBe(200);

    for (const interactionType of ["fido-uaf-registration-challenge", "fido-uaf-registration"]) {
      const response = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        id: registration.data.id,
        interactionType,
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        },
      });
      expect(response.status).toBe(200);
      if (interactionType === "fido-uaf-registration") {
        return { user, accessToken, deviceId: response.data.device_id };
      }
    }
  };

  /** Starts a deregistration through /me/mfa for the user's own device. */
  const startDeregistration = async ({ accessToken, deviceId }) => {
    const response = await postWithJson({
      url: serverConfig.resourceOwnerEndpoint + "/mfa/fido-uaf-deregistration",
      body: { device_id: deviceId },
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(response.status).toBe(200);
    return response.data.id;
  };

  const deviceIdsOf = async ({ accessToken }) => {
    const userinfo = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: { Authorization: `Bearer ${accessToken}` },
    });
    expect(userinfo.status).toBe(200);
    return (userinfo.data.authentication_devices || []).map((device) => device.id);
  };

  it("rejects a device_id that belongs to another user", async () => {
    const owner = await createUserWithFidoUafDevice();
    const other = await createUserWithFidoUafDevice();

    const transactionId = await startDeregistration(other);
    const response = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-deregistration",
      body: { device_id: owner.deviceId },
    });
    console.log("another user's device:", response.status, response.data);

    expect(response.status).toBe(400);
    expect(response.data.error).toBe("invalid_request");

    // Neither user's device was removed
    expect(await deviceIdsOf(owner)).toContain(owner.deviceId);
    expect(await deviceIdsOf(other)).toContain(other.deviceId);
  });

  it("deletes the user's own device", async () => {
    const owner = await createUserWithFidoUafDevice();

    const transactionId = await startDeregistration(owner);
    const response = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-deregistration",
      body: { device_id: owner.deviceId },
    });

    expect(response.status).toBe(200);
    expect(await deviceIdsOf(owner)).not.toContain(owner.deviceId);
  });
});
