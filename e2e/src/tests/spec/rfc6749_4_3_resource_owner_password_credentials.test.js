import { describe, expect, it } from "@jest/globals";

import { getJwks, inspectToken, requestToken } from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig, unsupportedServerConfig, unsupportedClient } from "../testConfig";
import { verifyAndDecodeJwt } from "../../lib/jose";

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
      expect(tokenResponse.data.error_description).toEqual("Client authentication failed: method=client_secret_post, client_id=clientSecretPost, reason=client_secret does not match");
    });
  });

  describe("5.2. Error Response", () => {
    it("unsupported_grant_type The authorization grant type is not supported by the authorization server.", async () => {
      const tokenResponse = await requestToken({
        endpoint: unsupportedServerConfig.tokenEndpoint,
        grantType: "password",
        scope: unsupportedClient.scope,
        clientId: unsupportedClient.clientId,
        clientSecret: unsupportedClient.clientSecret,
        username: oauth.username,
        password: oauth.password,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("unsupported_grant_type");
      expect(tokenResponse.data.error_description).toEqual(
        "this request grant_type is password, but authorization server does not support"
      );
    });

    it("unauthorized_client The authenticated client is not authorized to use this authorization grant type.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        scope: unsupportedClient.scope,
        clientId: unsupportedClient.clientId,
        clientSecret: unsupportedClient.clientSecret,
        username: oauth.username,
        password: oauth.password,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("unauthorized_client");
      expect(tokenResponse.data.error_description).toEqual(
        "this request grant_type is password, but client does not support"
      );
    });
  });

  // The password grant authenticates the resource owner with a username/password at the token
  // endpoint. Per RFC 9068 the issued JWT access token MAY carry authentication information claims,
  // and per RFC 8176 the password authentication method is recorded in amr. idp-server records the
  // method as "password" (StandardAuthenticationMethod.PASSWORD) consistently across all flows.
  describe("RFC 9068 2.2.1. Authentication Information Claims / RFC 8176 amr", () => {
    it("amr OPTIONAL. JSON array of strings ... identifiers for authentication methods used. The password grant MUST record the password method, so the issued access token amr MUST contain \"password\" and auth_time MUST be present.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        scope: "openid " + clientSecretPostClient.scope,
        username: oauth.username,
        password: oauth.password,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.access_token).toBeDefined();

      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(JSON.stringify(introspectionResponse.data, null, 2));
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);

      expect(introspectionResponse.data.amr).toBeDefined();
      expect(Array.isArray(introspectionResponse.data.amr)).toBe(true);
      expect(introspectionResponse.data.amr).toContain("password");

      expect(introspectionResponse.data).toHaveProperty("auth_time");
      expect(typeof introspectionResponse.data.auth_time).toBe("number");
      expect(introspectionResponse.data.auth_time).toBeGreaterThan(0);
    });

    it("amr OPTIONAL. When the openid scope is granted, the issued ID Token amr MUST contain \"password\" and auth_time MUST be present.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        scope: "openid " + clientSecretPostClient.scope,
        username: oauth.username,
        password: oauth.password,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.id_token).toBeDefined();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      console.log("ID Token payload:", decodedIdToken.payload);

      expect(decodedIdToken.payload).toHaveProperty("amr");
      expect(Array.isArray(decodedIdToken.payload.amr)).toBe(true);
      expect(decodedIdToken.payload.amr).toContain("password");

      expect(decodedIdToken.payload).toHaveProperty("auth_time");
      expect(typeof decodedIdToken.payload.auth_time).toBe("number");
    });
  });
});
