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

  it("5.3.1. token without openid scope is rejected with insufficient_scope", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
    });
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);
    // OIDC was not requested, so no id_token is issued
    expect(tokenResponse.data).not.toHaveProperty("id_token");

    const headers = createBearerHeader(tokenResponse.data.access_token);
    const userinfoResponse = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: headers,
    });
    console.log("UserInfo without openid scope:", userinfoResponse.status, JSON.stringify(userinfoResponse.data));

    // OIDC Core 5.3.1 / RFC 6750 3.1: UserInfo requires the openid scope
    expect(userinfoResponse.status).toBe(403);
    expect(userinfoResponse.data.error).toBe("insufficient_scope");

    const postUserinfoResponse = await postUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: headers,
    });
    expect(postUserinfoResponse.status).toBe(403);
    expect(postUserinfoResponse.data.error).toBe("insufficient_scope");
  });

  it("5.3.3. UserInfo Error Response - client_credentials token with no subject returns invalid_token", async () => {
    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "client_credentials",
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const userinfoResponse = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: createBearerHeader(tokenResponse.data.access_token),
    });
    console.log("UserInfo with client_credentials token:", userinfoResponse.status, JSON.stringify(userinfoResponse.data));

    // client_credentials token has no subject (no end-user), so UserInfo should reject it
    expect(userinfoResponse.status).toBe(401);
    expect(userinfoResponse.data.error).toBe("invalid_token");
  });

  it("5.3.3. UserInfo Error Response - unknown (non-existent) access token returns invalid_token", async () => {
    // A syntactically valid Bearer token that does not exist in the token store.
    // Exercises UserinfoVerifier#throwExceptionIfNotFoundToken, which is a different
    // code path from the "missing token" case (UserinfoValidator#validate).
    const userinfoResponse = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: createBearerHeader("nonexistent-access-token-0123456789"),
    });
    console.log("UserInfo with unknown token:", userinfoResponse.status, JSON.stringify(userinfoResponse.data));

    expect(userinfoResponse.status).toBe(401);
    expect(userinfoResponse.data.error).toBe("invalid_token");
  });

  it("missing access token ", async () => {
    const userinfoResponse = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: {},
    });
    console.log(userinfoResponse.data);
    console.log(userinfoResponse.headers);
    expect(userinfoResponse.status).toBe(401);
    expect(userinfoResponse.data.error).toEqual("invalid_token");
  });
});
