import { describe, expect, it } from "@jest/globals";

import { requestToken } from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";

describe("The OAuth 2.0 Authorization Framework resource owner password credentials", () => {
  const oauth = serverConfig.oauth;

  it("success pattern", async () => {
    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      scope: clientSecretPostClient.scope,
      username: oauth.username,
      password: oauth.password,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
  });

  describe("4.3.2.  Access Token Request", () => {
    it("grant_type REQUIRED.  Value MUST be set to \"password\".", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        scope: clientSecretPostClient.scope,
        username: oauth.username,
        password: oauth.password,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
    });

    it("username REQUIRED.  The resource owner username.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        scope: clientSecretPostClient.scope,
        password: oauth.password,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_request");
      expect(tokenResponse.data.error_description).toEqual("token request does not contains username, password grant must contains username");
    });

    it("password REQUIRED.  The resource owner password.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        scope: clientSecretPostClient.scope,
        username: oauth.username,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_request");
      expect(tokenResponse.data.error_description).toEqual("token request does not contains password, password grant must contains password");
    });

    it("scope OPTIONAL.  The scope of the access request as described by Section 3.3.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        scope: "clientSecretPostClient.scope",
        username: oauth.username,
        password: oauth.password,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_scope");
      expect(tokenResponse.data.error_description).toEqual("token request does not contains valid scope");
    });

    it("If the client type is confidential or the client was issued client credentials (or assigned other authentication requirements), the client MUST authenticate with the authorization server as described in Section 3.2.1.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        clientId: clientSecretPostClient.clientId,
        clientSecret: "clientSecretPostClient.clientSecret",
        scope: clientSecretPostClient.scope,
        username: oauth.username,
        password: oauth.password,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(401);
      expect(tokenResponse.data.error).toEqual("invalid_client");
      expect(tokenResponse.data.error_description).toEqual("client authentication type is client_secret_post, but request client_secret does not match client_secret");
    });
  });
});
