import { jest, describe, expect, it } from "@jest/globals";
import { createFederatedUser } from "../../../user";
import {
  backendUrl,
  clientSecretPostClient,
  federationServerConfig,
  publicClient,
  serverConfig
} from "../../testConfig";
import { sleep } from "../../../lib/util";
import { post } from "../../../lib/http";

describe("token refresh strategy", () => {

  it("fixed & rotate strategy", async () => {
    jest.setTimeout(180_000);

    const { user, accessToken, refreshToken: initialRefreshToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: publicClient,
      adminClient: clientSecretPostClient
    });

    const refreshTokenDuration = 60;
    const startTime = Date.now();
    let expireAt = startTime + refreshTokenDuration * 1000;
    let refreshToken = initialRefreshToken;

    while (Date.now() < expireAt - 2000) {
      console.log(`${(Date.now() - startTime) / 1000 }` + " second");

      await sleep(1000);

      const refreshTokenResponse = await post({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/tokens`,
        body: new URLSearchParams({
          client_id: publicClient.clientId,
          grant_type: "refresh_token",
          refresh_token: refreshToken
        }).toString()
      });

      console.log(refreshTokenResponse.data);
      expect(refreshTokenResponse.status).toBe(200);
      refreshToken = refreshTokenResponse.data.refresh_token;

      const introspectionResponse = await post({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/tokens/introspection-extensions`,
        body: new URLSearchParams({
          token: refreshTokenResponse.data.access_token,
          client_id: clientSecretPostClient.clientId,
          client_secret: clientSecretPostClient.clientSecret
        }).toString()
      });

      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);

    }

    await sleep(3000);

    const refreshTokenResponse = await post({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/tokens`,
      body: new URLSearchParams({
        client_id: publicClient.clientId,
        grant_type: "refresh_token",
        refresh_token: refreshToken
      }).toString()
    });

    expect(refreshTokenResponse.status).toBe(400);
    expect(refreshTokenResponse.data.error).toEqual("invalid_grant");
    expect(refreshTokenResponse.data.error_description).toEqual("refresh token is expired");
  });


});