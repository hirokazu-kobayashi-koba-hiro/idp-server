import { describe, expect, it, xit } from "@jest/globals";

import { getAuthorizations, requestToken } from "./api/oauthClient";
import {
  clientSecretPostClient,
  serverConfig,
  unsupportedClient,
  unsupportedServerConfig,
} from "./testConfig";
import { requestAuthorizations } from "./oauth";
import { matchWithUSASCII } from "./lib/util";

describe("The OAuth 2.0 Authorization Framework code", () => {
  it("success pattern", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: clientSecretPostClient.scope,
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
  });

  describe("3.1.  Authorization Endpoint", () => {
    it("The authorization endpoint is used to interact with the resource owner and obtain an authorization grant.  The authorization server MUST first verify the identity of the resource owner.", async () => {});

    it("The authorization server MUST support the use of the HTTP \"GET\" method [RFC2616] for the authorization endpoint and MAY support the use of the \"POST\" method as well.", async () => {
      const response = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "account",
        state: "aiueo",
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(response.data);
      expect(response.status).toBe(200);
    });
  });

  describe("4.1.  Authorization Code Grant", () => {
    it("4.1.1.  Authorization Request response_type REQUIRED", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        scope: clientSecretPostClient.scope,
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual(
        "response type is required in authorization request"
      );
    });

    it("4.1.1.  Authorization Request client_id REQUIRED", async () => {
      const { status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        redirectUri: clientSecretPostClient.redirectUri,
        scope: clientSecretPostClient.scope,
      });
      console.log(error);
      expect(status).toBe(400);

      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toEqual(
        "authorization request must contains client_id"
      );
    });

    it("4.1.1.  Authorization Request scope", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "test bank",
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual(
        "authorization request does not contains valid scope (test bank)"
      );
    });
  });

  describe("4.1.2.  Authorization Response", () => {
    it("code REQUIRED.  The authorization code generated by the authorization server.  The authorization code MUST expire shortly after it is issued to mitigate the risk of leaks.  A maximum authorization code lifetime of 10 minutes is RECOMMENDED.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
    });

    it("state REQUIRED if the \"state\" parameter was present in the client authorization request.  The exact value received from the client.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.state).toEqual("aiueo");

      const { authorizationResponse: reAuthorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(reAuthorizationResponse);
      expect(reAuthorizationResponse.code).not.toBeNull();
      expect(reAuthorizationResponse.state).toBeNull();
    });
  });

  describe("4.1.2.1.  Error Response", () => {
    it("error REQUIRED.  A single ASCII [USASCII] error code from the following:", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
    });

    it("invalid_request The request is missing a required parameter, includes an invalid parameter value, includes a parameter more than once, or is otherwise malformed.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
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
        responseType: "code",
        scope: unsupportedClient.scope,
        redirectUri: unsupportedClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("unauthorized_client");
      expect(authorizationResponse.errorDescription).toEqual("client is unauthorized response_type (code)");
    });

    it("access_denied The resource owner or authorization server denied the request.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        state: "aiueo",
        responseType: "code",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
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
        responseType: "code",
        scope: unsupportedClient.scope,
        redirectUri: unsupportedClient.redirectUri,
        action: "deny",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("unsupported_response_type");
      expect(authorizationResponse.errorDescription).toEqual("authorization server is unsupported response_type (code)");
    });

    it("invalid_scope The requested scope is invalid, unknown, or malformed.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        state: "aiueo",
        responseType: "code",
        scope: "clientSecretPostClient.scope",
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual("authorization request does not contains valid scope (clientSecretPostClient.scope)");
    });

    it("error_description TOPTIONAL.  Human-readable ASCII [USASCII] text providing additional information, used to assist the client developer in understanding the error that occurred. Values for the \"error_description\" parameter MUST NOT include characters outside the set %x20-21 / %x23-5B / %x5D-7E.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        state: "aiueo",
        responseType: "code",
        scope: "clientSecretPostClient.scope",
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual("authorization request does not contains valid scope (clientSecretPostClient.scope)");
      expect(matchWithUSASCII(authorizationResponse.errorDescription)).toBe(true);
    });

    it("state REQUIRED if a \"state\" parameter was present in the client authorization request.  The exact value received from the client.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        state: "aiueo",
        responseType: "code",
        scope: "clientSecretPostClient.scope",
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(authorizationResponse.error)).toBe(true);
      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual("authorization request does not contains valid scope (clientSecretPostClient.scope)");
      expect(matchWithUSASCII(authorizationResponse.errorDescription)).toBe(true);
      expect(authorizationResponse.state).toEqual("aiueo");

      const { authorizationResponse: reAuthorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "clientSecretPostClient.scope",
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(reAuthorizationResponse);
      expect(reAuthorizationResponse.error).not.toBeNull();
      expect(matchWithUSASCII(reAuthorizationResponse.error)).toBe(true);
      expect(reAuthorizationResponse.error).toEqual("invalid_scope");
      expect(reAuthorizationResponse.errorDescription).toEqual("authorization request does not contains valid scope (clientSecretPostClient.scope)");
      expect(matchWithUSASCII(reAuthorizationResponse.errorDescription)).toBe(true);
      expect(reAuthorizationResponse.state).toBeNull();
    });
  });

  describe("4.1.3.  Access Token Request", () => {
    it("grant_type REQUIRED.  Value MUST be set to \"authorization_code\".", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_request");
      expect(tokenResponse.data.error_description).toEqual(
        "token request must contains grant_type, but this request does not contains grant_type"
      );
    });

    it("code REQUIRED.  The authorization code received from the authorization server.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_request");
      expect(tokenResponse.data.error_description).toEqual(
        "token request does not contains code, authorization_code grant must contains code"
      );
    });

    it("require client authentication for confidential clients or for any client that was issued client credentials (or with other authentication requirements),", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code: authorizationResponse.code,
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(401);
      expect(tokenResponse.data.error).toEqual("invalid_client");
      expect(tokenResponse.data.error_description).toEqual(
        "client authentication type is client_secret_post, but request does not contains client_secret_post"
      );
    });

    it("redirect_uri REQUIRED, if the \"redirect_uri\" parameter was included in the authorization request as described in Section 4.1.1, and their values MUST be identical.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code: authorizationResponse.code,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_request");
      expect(tokenResponse.data.error_description).toEqual(
        "token request redirect_uri does not equals to authorization request redirect_uri ()"
      );
    });
  });

  describe("5.1.  Successful Response", () => {
    it("access_token REQUIRED.  The access token issued by the authorization server.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code: authorizationResponse.code,
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
    });

    it("token_type REQUIRED.  The type of the token issued as described in Section 7.1.  Value is case insensitive.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code: authorizationResponse.code,
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data.token_type).toEqual("Bearer");
    });

    it("expires_in REQUIRED.  RECOMMENDED.  The lifetime in seconds of the access token.  For example, the value \"3600\" denotes that the access token will expire in one hour from the time the response was generated. If omitted, the authorization server SHOULD provide the expiration time via other means or document the default value.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code: authorizationResponse.code,
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data.token_type).toEqual("Bearer");
      expect(tokenResponse.data).toHaveProperty("expires_in");
    });

    it("refresh_token OPTIONAL.  The refresh token, which can be used to obtain new access tokens using the same authorization grant as described in Section 6.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code: authorizationResponse.code,
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data.token_type).toEqual("Bearer");
      expect(tokenResponse.data).toHaveProperty("expires_in");
    });

    it("scope OPTIONAL, if identical to the scope requested by the client; otherwise, REQUIRED.  The scope of the access token as described by Section 3.3.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code: authorizationResponse.code,
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data.token_type).toEqual("Bearer");
      expect(tokenResponse.data).toHaveProperty("expires_in");
      expect(tokenResponse.data).toHaveProperty("scope");
    });

    xit("The authorization server MUST include the HTTP \"Cache-Control\" response header field [RFC2616] with a value of \"no-store\" in any response containing tokens, credentials, or other sensitive information, as well as the \"Pragma\" response header field [RFC2616] with a value of \"no-cache\"", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code: authorizationResponse.code,
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      console.log(tokenResponse.headers);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data.token_type).toEqual("Bearer");
      expect(tokenResponse.data).toHaveProperty("expires_in");
      expect(tokenResponse.data).toHaveProperty("scope");
      //FIXME
      expect(tokenResponse.headers["Cache-Control"]).toEqual("no-store");
    });
  });

  describe("5.2.  Error Response", () => {
    it("error REQUIRED.  A single ASCII [USASCII] error code from the following:", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code: authorizationResponse.code,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(matchWithUSASCII(tokenResponse.data.error)).toBe(true);
    });

    it("invalid_request The request is missing a required parameter, includes an unsupported parameter value (other than grant type), repeats a parameter, includes multiple credentials, utilizes more than one mechanism for authenticating the client, or is otherwise malformed.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(matchWithUSASCII(tokenResponse.data.error)).toBe(true);
      expect(tokenResponse.data.error).toEqual("invalid_request");
    });

    it("invalid_client Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method).  The authorization server MAY return an HTTP 401 (Unauthorized) status code to indicate which HTTP authentication schemes are supported.  If the client attempted to authenticate via the \"Authorization\" request header field, the authorization server MUST respond with an HTTP 401 (Unauthorized) status code and include the \"WWW-Authenticate\" response header field matching the authentication scheme used by the client.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: "clientSecretPostClient.clientId",
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(401);
      expect(matchWithUSASCII(tokenResponse.data.error)).toBe(true);
      expect(tokenResponse.data.error).toEqual("invalid_client");
    });

    it("invalid_grant The provided authorization grant (e.g., authorization code, resource owner credentials) or refresh token is invalid, expired, revoked, does not match the redirection URI used in the authorization request, or was issued to another client.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: "authorizationResponse.code",
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(matchWithUSASCII(tokenResponse.data.error)).toBe(true);
      expect(tokenResponse.data.error).toEqual("invalid_grant");
    });

    it("unauthorized_client The authenticated client is not authorized to use this authorization grant type.", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: "authorizationResponse.code",
        grantType: "authorization_code",
        redirectUri: unsupportedClient.redirectUri,
        clientId: unsupportedClient.clientId,
        clientSecret: unsupportedClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(matchWithUSASCII(tokenResponse.data.error)).toBe(true);
      expect(tokenResponse.data.error).toEqual("unauthorized_client");
      expect(tokenResponse.data.error_description).toEqual(
        "this request grant_type is authorization_code, but client does not support"
      );
    });

    it("unsupported_grant_type The authorization grant type is not supported by the authorization server.", async () => {
      const tokenResponse = await requestToken({
        endpoint: unsupportedServerConfig.tokenEndpoint,
        code: "authorizationResponse.code",
        grantType: "authorization_code",
        redirectUri: unsupportedClient.redirectUri,
        clientId: unsupportedClient.clientId,
        clientSecret: unsupportedClient.clientSecret,
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(matchWithUSASCII(tokenResponse.data.error)).toBe(true);
      expect(tokenResponse.data.error).toEqual("unsupported_grant_type");
      expect(tokenResponse.data.error_description).toEqual(
        "this request grant_type is authorization_code, but authorization server does not support"
      );
    });
  });
});