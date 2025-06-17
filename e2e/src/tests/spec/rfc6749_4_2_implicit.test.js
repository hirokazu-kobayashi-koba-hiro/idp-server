import { describe, expect, it } from "@jest/globals";

import {
  clientSecretBasicClient,
  serverConfig,
  unsupportedClient,
  unsupportedServerConfig,
} from "../testConfig";
import { requestAuthorizations } from "../../oauth/signin";
import { matchWithUSASCII } from "../../lib/util";

describe("The OAuth 2.0 Authorization Framework implicit", () => {
  it("success pattern", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretBasicClient.clientId,
      responseType: "token",
      state: "aiueo",
      scope: clientSecretBasicClient.scope,
      redirectUri: clientSecretBasicClient.redirectUri,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.accessToken).not.toBeNull();
    expect(authorizationResponse.tokenType).toEqual("Bearer");
    expect(authorizationResponse.expiresIn).not.toBeNull();
  });

  describe("4.2.1.  Authorization Request", () => {
    it("response_type REQUIRED.  Value MUST be set to \"token\".", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.expiresIn).not.toBeNull();
    });

    it("client_id REQUIRED.  The client identifier as described in Section 2.2.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.expiresIn).not.toBeNull();
    });

    it("redirect_uri OPTIONAL.  As described in Section 3.1.2.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.expiresIn).not.toBeNull();
    });

    it("scope OPTIONAL.  The scope of the access request as described by Section 3.3.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual("authorization request does not contains valid scope ()");
    });
  });

  describe("4.2.2.  Access Token Response", () => {
    it("access_token REQUIRED.  The access token issued by the authorization server.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.accessToken).not.toBeNull();
    });

    it("token_type REQUIRED.  The type of the token issued as described in Section 7.1.  Value is case insensitive.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
    });

    it("expires_in RECOMMENDED.  The lifetime in seconds of the access token.  For example, the value \"3600\" denotes that the access token will expire in one hour from the time the response was generated. If omitted, the authorization server SHOULD provide the expiration time via other means or document the default value.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.expiresIn).not.toBeNull();
    });

    it("scope OPTIONAL, if identical to the scope requested by the client; otherwise, REQUIRED.  The scope of the access token as described by Section 3.3.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.expiresIn).not.toBeNull();
      expect(authorizationResponse.scope).not.toBeNull();
    });

    it("state REQUIRED if the \"state\" parameter was present in the client authorization request.  The exact value received from the client.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.expiresIn).not.toBeNull();
      // expect(authorizationResponse.scope).not.toBeNull();
      expect(authorizationResponse.state).toEqual("aiueo");

      const { authorizationResponse: reAuthorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(reAuthorizationResponse);
      expect(reAuthorizationResponse.accessToken).not.toBeNull();
      expect(reAuthorizationResponse.tokenType).toEqual("Bearer");
      expect(reAuthorizationResponse.expiresIn).not.toBeNull();
      // expect(reAuthorizationResponse.scope).not.toBeNull();
      expect(reAuthorizationResponse.state).toBeNull();
    });

    it("The authorization server MUST NOT issue a refresh token.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.expiresIn).not.toBeNull();
      // expect(authorizationResponse.scope).not.toBeNull();
      expect(authorizationResponse.state).toEqual("aiueo");
      expect(authorizationResponse.refreshToken).toBeNull();
    });
  });

  describe("4.2.2.1.  Error Response", () => {
    it("error REQUIRED.  A single ASCII [USASCII] error code from the following:", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        state: "aiueo",
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
    });

    it("invalid_request The request is missing a required parameter, includes an invalid parameter value, includes a parameter more than once, or is otherwise malformed.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        state: "aiueo",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual("response type is required in authorization request");
    });

    it("unauthorized_client The client is not authorized to request an authorization code using this method.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: unsupportedClient.clientId,
        state: "aiueo",
        responseType: "token",
        scope: unsupportedClient.scope,
        redirectUri: unsupportedClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("unauthorized_client");
      expect(authorizationResponse.errorDescription).toEqual("client is unauthorized response_type (token)");
    });

    it("access_denied The resource owner or authorization server denied the request.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        state: "aiueo",
        responseType: "token",
        scope: clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
        action: "deny",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("access_denied");
      expect(authorizationResponse.errorDescription).toEqual("The resource owner or authorization server denied the request.");
    });

    it("unsupported_response_type The authorization server does not support obtaining an authorization code using this method.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: unsupportedServerConfig.authorizationEndpoint,
        clientId: unsupportedClient.clientId,
        state: "aiueo",
        responseType: "token",
        scope: unsupportedClient.scope,
        redirectUri: unsupportedClient.redirectUri,
        action: "deny",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("unsupported_response_type");
      expect(authorizationResponse.errorDescription).toEqual("authorization server is unsupported response_type (token)");
    });

    it("invalid_scope The requested scope is invalid, unknown, or malformed.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        state: "aiueo",
        responseType: "token",
        scope: "clientSecretBasicClient.scope",
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual("authorization request does not contains valid scope (clientSecretBasicClient.scope)");
    });

    it("error_description TOPTIONAL.  Human-readable ASCII [USASCII] text providing additional information, used to assist the client developer in understanding the error that occurred. Values for the \"error_description\" parameter MUST NOT include characters outside the set %x20-21 / %x23-5B / %x5D-7E.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        state: "aiueo",
        responseType: "token",
        scope: "clientSecretBasicClient.scope",
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual("authorization request does not contains valid scope (clientSecretBasicClient.scope)");
      expect(matchWithUSASCII(authorizationResponse.errorDescription)).toBe(true);
    });

    it("state REQUIRED if a \"state\" parameter was present in the client authorization request.  The exact value received from the client.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        state: "aiueo",
        responseType: "token",
        scope: "clientSecretBasicClient.scope",
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual("authorization request does not contains valid scope (clientSecretBasicClient.scope)");
      expect(matchWithUSASCII(authorizationResponse.errorDescription)).toBe(true);
      expect(authorizationResponse.state).toEqual("aiueo");

      const { authorizationResponse: reAuthorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "token",
        scope: "clientSecretBasicClient.scope",
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(reAuthorizationResponse);
      expect(reAuthorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(reAuthorizationResponse.error)).toBe(true);
      expect(reAuthorizationResponse.error).toEqual("invalid_scope");
      expect(reAuthorizationResponse.errorDescription).toEqual("authorization request does not contains valid scope (clientSecretBasicClient.scope)");
      expect(matchWithUSASCII(reAuthorizationResponse.errorDescription)).toBe(true);
      expect(reAuthorizationResponse.state).toBeNull();
    });
  });
});
