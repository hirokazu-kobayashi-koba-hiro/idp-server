import { describe, expect, it } from "@jest/globals";

import { getUserinfo, postUserinfo, requestToken } from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";
import { createBearerHeader } from "../../lib/util";

describe("OpenID Connect Core 1.0 incorporating errata set 1 userinfo", () => {
  it("success pattern", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email address " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");
    const headers = createBearerHeader(tokenResponse.data.access_token);
    const userinfoResponse = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: headers,
    });
    console.log(userinfoResponse.data);
    expect(userinfoResponse.status).toBe(200);
    expect(userinfoResponse.data).toHaveProperty("sub");

    const postUserinfoResponse = await postUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: headers,
    });
    console.log(postUserinfoResponse.data);
    expect(postUserinfoResponse.status).toBe(200);
    expect(postUserinfoResponse.data).toHaveProperty("sub");
  });
});
