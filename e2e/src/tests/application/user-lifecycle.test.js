import { describe, xit, expect } from "@jest/globals";
import { loginForClientSecretPost } from "../../ciba/login";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import { deletion } from "../../lib/http";

describe("User lifecycle", () => {

  describe("success pattern", () => {

    xit("delete user", async () => {

      const tokenResponse = await loginForClientSecretPost({
        serverConfig,
        client: clientSecretPostClient,
        scope: "identity_verification_application identity_verification_delete identity_credentials_update"
      });

      const deleteResponse = await deletion({
        url: serverConfig.usersEndpoint,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tokenResponse.access_token}`
        }
      });

      expect(deleteResponse.status).toBe(204);

    });
  });
});