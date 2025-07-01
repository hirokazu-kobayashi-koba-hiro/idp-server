import { describe, it, expect } from "@jest/globals";
import { backendUrl, clientSecretPostClient, federationServerConfig, serverConfig } from "../../testConfig";
import { deletion, post } from "../../../lib/http";
import { createFederatedUser } from "../../../user";
import { sleep } from "../../../lib/util";

describe("User lifecycle", () => {

  describe("success pattern", () => {

    it("delete user", async () => {

      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        clientSecretPostClient: clientSecretPostClient
      });

      console.log(user);

      const deleteResponse = await deletion({
        url: serverConfig.usersEndpoint,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });

      expect(deleteResponse.status).toBe(204);

      const userinfoResponse = await post({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/userinfo`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });

      console.log(userinfoResponse.data);
      expect(userinfoResponse.status).toBe(401);

      await sleep(200);

      const introspectionResponse = await post({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/tokens/introspection`,
        body: new URLSearchParams({
          token: accessToken
        }).toString()
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(false);

    });
  });
});

