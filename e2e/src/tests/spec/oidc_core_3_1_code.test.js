import { describe, expect, it, xit } from "@jest/globals";

import { getJwks, requestToken } from "../../api/oauthClient";
import {
  clientSecretBasicClient,
  clientSecretPostClient,
  serverConfig,
} from "../testConfig";
import { requestAuthorizations, requestLogout } from "../../oauth/request";
import { createJwtWithPrivateKey, verifyAndDecodeJwt } from "../../lib/jose";
import { createBasicAuthHeader, toEpocTime } from "../../lib/util";
import { calculateIdTokenClaimHashWithS256 } from "../../lib/oauth";

describe("OpenID Connect Core 1.0 incorporating errata set 1 code", () => {
  it("success pattern", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email" + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      customParams: {
        organizationId: "123",
        organizationName: "test",
      }
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
    expect(tokenResponse.data).toHaveProperty("id_token");
  });

  xit("success pattern prompt none", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      prompt: "none",
      state: "aiueo",
      scope: "openid profile phone email" + clientSecretPostClient.scope,
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
  });

  describe("3.1.2.1.  Authentication Request", () => {
    it("scope REQUIRED. OpenID Connect requests MUST contain the openid scope value. If the openid scope value is not present, the behavior is entirely unspecified. Other scope values MAY be present. Scope values used that are not understood by an implementation SHOULD be ignored. See Sections 5.4 and 11 for additional scope values defined by this specification.", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual(
        "authorization request does not contains valid scope ()"
      );
    });

    it("response_type REQUIRED. OAuth 2.0 Response Type value that determines the authorization processing flow to be used, including what parameters are returned from the endpoints used. When using the Authorization Code Flow, this value is code.", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual(
        "response type is required in authorization request"
      );
    });

    it("client_id REQUIRED. OAuth 2.0 Client Identifier valid at the Authorization Server.", async () => {
      const { status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
      });
      console.log(error);
      // expect(status).toBe(400);

      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toEqual(
        "authorization request must contains client_id"
      );
    });

    it("state RECOMMENDED. Opaque value used to maintain state between the request and the callback. Typically, Cross-Site Request Forgery (CSRF, XSRF) mitigation is done by cryptographically binding the value of this parameter with a browser cookie.", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
      });
      console.log(authorizationResponse);

      expect(authorizationResponse.code).not.toBeNull();
    });

    it("response_mode OPTIONAL. Informs the Authorization Server of the mechanism to be used for returning parameters from the Authorization Endpoint. This use of this parameter is NOT RECOMMENDED when the Response Mode that would be requested is the default mode specified for the Response Type.", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
      });
      console.log(authorizationResponse);

      expect(authorizationResponse.code).not.toBeNull();
    });

    it("nonce OPTIONAL. String value used to associate a Client session with an ID Token, and to mitigate replay attacks. The value is passed through unmodified from the Authentication Request to the ID Token. Sufficient entropy MUST be present in the nonce values used to prevent attackers from guessing values. For implementation notes, see Section 15.5.2.", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
      });
      console.log(authorizationResponse);

      expect(authorizationResponse.code).not.toBeNull();
    });

    it("display OPTIONAL. ASCII string value that specifies how the Authorization Server displays the authentication and consent user interface pages to the End-User. ", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "test",
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual(
        "authorization request display is defined that page, popup, touch, wap, but request display is (test)"
      );
    });

    it("prompt OPTIONAL. Space delimited, case sensitive list of ASCII string values that specifies whether the Authorization Server prompts the End-User for reauthentication and consent. ", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "test",
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual(
        "authorization request prompt is defined that none, login, consent, select_account, but request prompt is (test)"
      );
    });

    xit("prompt none The Authorization Server MUST NOT display any authentication or consent user interface pages. An error is returned if an End-User is not already authenticated or the Client does not have pre-configured consent for the requested Claims or does not fulfill other conditions for processing the request. The error code will typically be login_required, interaction_required, or another code defined in Section 3.1.2.6. This can be used as a method to check for existing authentication and/or consent. ", async () => {
      const logoutResponse = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        clientId: clientSecretPostClient.clientId,
      });
      expect(logoutResponse.status).toBe(200);

      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "none",
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("login_required");
      expect(authorizationResponse.errorDescription).toEqual("invalid session, session is not registered");
    });

    it("prompt login The Authorization Server SHOULD prompt the End-User for reauthentication. If it cannot reauthenticate the End-User, it MUST return an error, typically login_required.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
    });

    it("prompt consent The Authorization Server SHOULD prompt the End-User for consent before returning information to the Client. If it cannot obtain consent, it MUST return an error, typically consent_required.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "consent",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
    });

    it("prompt select_account The Authorization Server SHOULD prompt the End-User to select a user account. This enables an End-User who has multiple accounts at the Authorization Server to select amongst the multiple accounts that they might have current sessions for. If it cannot obtain an account selection choice made by the End-User, it MUST return an error, typically account_selection_required.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "consent",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
    });

    it("If this parameter contains none with any other value, an error is returned.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "none consent",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual(
        "authorization request must not contains none with any other (none consent)"
      );
    });

    it("max_age OPTIONAL. Maximum Authentication Age. Specifies the allowable elapsed time in seconds since the last time the End-User was actively authenticated by the OP. If the elapsed time is greater than this value, the OP MUST attempt to actively re-authenticate the End-User. (The max_age request parameter corresponds to the OpenID 2.0 PAPE [OpenID.PAPE] max_auth_age request parameter.) When max_age is used, the ID Token returned MUST include an auth_time Claim Value.", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        maxAge: "-100",
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual(
        "authorization request max_age is invalid (-100)"
      );
    });
  });

  describe("3.1.2.2.  Authentication Request Validation", () => {
    //The Authorization Server MUST validate the request received as follows:
    it("1. The Authorization Server MUST validate all the OAuth 2.0 parameters according to the OAuth 2.0 specification.", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        maxAge: "-100",
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual(
        "response type is required in authorization request"
      );
    });

    it("2. Verify that a scope parameter is present and contains the openid scope value. (If no openid scope value is present, the request may still be a valid OAuth 2.0 request, but is not an OpenID Connect request.)", async () => {
      const { status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: "https://client.example.org:443/callback",
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(error);
      // expect(status).toBe(400);

      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toEqual(
        "authorization request redirect_uri does not register in client configuration (https://client.example.org:443/callback)"
      );
    });

    it("3. The Authorization Server MUST verify that all the REQUIRED parameters are present and their usage conforms to this specification.", async () => {
      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("invalid_scope");
      expect(authorizationResponse.errorDescription).toEqual(
        "authorization request does not contains valid scope ()"
      );
    });

    it("4. If the sub (subject) Claim is requested with a specific value for the ID Token, the Authorization Server MUST only send a positive response if the End-User identified by that sub value has an active session with the Authorization Server or has been Authenticated as a result of the request. The Authorization Server MUST NOT reply with an ID Token or Access Token for a different user, even if they have an active session with the Authorization Server. Such a request can be made either using an id_token_hint parameter or by requesting a specific Claim Value as described in Section 5.5.1, if the claims parameter is supported by the implementation.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
    });
  });

  describe("3.1.2.3.  Authorization Server Authenticates End-User", () => {
    describe("The Authorization Server MUST attempt to Authenticate the End-User in the following cases:", () => {
      it("The End-User is not already Authenticated.", async () => {
        const { authorizationResponse } = await requestAuthorizations({
          endpoint: serverConfig.authorizationEndpoint,
          clientId: clientSecretPostClient.clientId,
          redirectUri: clientSecretPostClient.redirectUri,
          responseType: "code",
          scope: "openid " + clientSecretPostClient.scope,
          state: "state",
          responseMode: "query",
          nonce: "nonce",
          display: "page",
        });
        console.log(authorizationResponse);
        expect(authorizationResponse.code).not.toBeNull();
      });

      it("The Authentication Request contains the prompt parameter with the value login. In this case, the Authorization Server MUST reauthenticate the End-User even if the End-User is already authenticated.\n", async () => {
        const { authorizationResponse } = await requestAuthorizations({
          endpoint: serverConfig.authorizationEndpoint,
          clientId: clientSecretPostClient.clientId,
          redirectUri: clientSecretPostClient.redirectUri,
          responseType: "code",
          scope: "openid " + clientSecretPostClient.scope,
          state: "state",
          responseMode: "query",
          nonce: "nonce",
          display: "page",
          prompt: "login",
        });
        console.log(authorizationResponse);
        expect(authorizationResponse.code).not.toBeNull();
      });
    });

    describe("The Authorization Server MUST NOT interact with the End-User in the following case:", () => {
      xit("The Authentication Request contains the prompt parameter with the value none. In this case, the Authorization Server MUST return an error if an End-User is not already Authenticated or could not be silently Authenticated.", async () => {
        const logoutResponse = await requestLogout({
          endpoint: serverConfig.logoutEndpoint,
          clientId: clientSecretPostClient.clientId,
        });
        expect(logoutResponse.status).toBe(200);

        const { status, authorizationResponse } = await requestAuthorizations({
          endpoint: serverConfig.authorizationEndpoint,
          clientId: clientSecretPostClient.clientId,
          redirectUri: clientSecretPostClient.redirectUri,
          responseType: "code",
          scope: "openid " + clientSecretPostClient.scope,
          state: "state",
          responseMode: "query",
          nonce: "nonce",
          display: "page",
          prompt: "none",
        });
        console.log(authorizationResponse);
        // expect(status).toBe(302);

        expect(authorizationResponse.error).toEqual("login_required");
        expect(authorizationResponse.errorDescription).toEqual(
          "invalid session, session is not registered"
        );
      });
    });
  });

  describe("3.1.2.5.  Successful Authentication Response", () => {
    it("When using the Authorization Code Flow, the Authorization Response MUST return the parameters defined in Section 4.1.2 of OAuth 2.0 [RFC6749] by adding them as query parameters to the redirect_uri specified in the Authorization Request using the application/x-www-form-urlencoded format, unless a different Response Mode was specified.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
    });
  });

  describe("3.1.2.6.  Authentication Error Response", () => {
    // if on multi thread, this test is failed
    xit("login_required The Authorization Server requires End-User authentication. This error MAY be returned when the prompt parameter value in the Authentication Request is none, but the Authentication Request cannot be completed without displaying a user interface for End-User authentication.", async () => {
      const logoutResponse = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        clientId: clientSecretPostClient.clientId,
      });
      expect(logoutResponse.status).toBe(200);

      const { status, authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "none",
      });
      console.log(authorizationResponse);
      expect(status).toBe(302);

      expect(authorizationResponse.error).toEqual("login_required");
      expect(authorizationResponse.errorDescription).toEqual("invalid session, session is not registered");
    });

    xit("invalid_request_object The request parameter contains an invalid Request Object.", async () => {
      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + clientSecretPostClient.scope,
          redirect_uri: clientSecretPostClient.redirectUri,
          aud: "aud",
          iss: clientSecretPostClient.clientId,
        },
        privateKey: clientSecretPostClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: clientSecretPostClient.clientId,
      });
      console.log(authorizationResponse);
      console.log(authorizationResponse.data);
      expect(authorizationResponse.error).toEqual("invalid_request_object");
      expect(authorizationResponse.errorDescription).toEqual(
        "request object is invalid, aud claim must be issuer"
      );
    });
  });

  describe("3.1.3.1.  Token Request", () => {
    it("A Client makes a Token Request by presenting its Authorization Grant (in the form of an Authorization Code) to the Token Endpoint using the grant_type value authorization_code, as described in Section 4.1.3 of OAuth 2.0 [RFC6749]. If the Client is a Confidential Client, then it MUST authenticate to the Token Endpoint using the authentication method registered for its client_id, as described in Section 9.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
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
      expect(tokenResponse.data).toHaveProperty("id_token");
    });
  });

  describe("3.1.3.2.  Token Request Validation", () => {
    //The Authorization Server MUST validate the Token Request as follows:
    it("Authenticate the Client if it was issued Client Credentials or if it uses another Client Authentication method, per Section 9.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: "clientSecretPostClient.clientSecret",
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(401);
      expect(tokenResponse.data.error).toEqual("invalid_client");
      expect(tokenResponse.data.error_description).toEqual(
        "client authentication type is client_secret_post, but request client_secret does not match client_secret"
      );
    });

    it("Ensure the Authorization Code was issued to the authenticated Client.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const basicAuth = createBasicAuthHeader({
        username: clientSecretBasicClient.clientId,
        password: clientSecretBasicClient.clientSecret,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        basicAuth,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_grant");
      expect(tokenResponse.data.error_description).toContain(
        "not found authorization code"
      );
    });

    it("Verify that the Authorization Code is valid.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
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
      expect(tokenResponse.data.error).toEqual("invalid_grant");
      expect(tokenResponse.data.error_description).toContain(
        "not found authorization code"
      );
    });

    it("If possible, verify that the Authorization Code has not been previously used.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
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

      const reTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(reTokenResponse.data);

      expect(reTokenResponse.status).toBe(400);
      expect(reTokenResponse.data.error).toEqual("invalid_grant");
      expect(reTokenResponse.data.error_description).toContain(
        "not found authorization code"
      );
    });

    it("Ensure that the redirect_uri parameter value is identical to the redirect_uri parameter value that was included in the initial Authorization Request. If the redirect_uri parameter value is not present when there is only one registered redirect_uri value, the Authorization Server MAY return an error (since the Client should have included the parameter) or MAY proceed without an error (since OAuth 2.0 permits the parameter to be omitted in this case).", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: "clientSecretPostClient.redirectUri",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_request");
      expect(tokenResponse.data.error_description).toEqual(
        "token request redirect_uri does not equals to authorization request redirect_uri (clientSecretPostClient.redirectUri)"
      );
    });

    it("Verify that the Authorization Code used was issued in response to an OpenID Connect Authentication Request (so that an ID Token will be returned from the Token Endpoint).", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
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
      expect(tokenResponse.data.error).toEqual("invalid_grant");
      expect(tokenResponse.data.error_description).toEqual(
        "not found authorization code (authorizationResponse.code)"
      );
    });
  });

  describe("3.1.3.3.  Successful Token Response", () => {
    it("In addition to the response parameters specified by OAuth 2.0, the following parameters MUST be included in the response: id_token ID Token value associated with the authenticated session.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
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
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data.token_type).toEqual("Bearer");
      expect(tokenResponse.data).toHaveProperty("expires_in");
      expect(tokenResponse.data).toHaveProperty("scope");
      expect(tokenResponse.data).toHaveProperty("id_token");
    });
  });

  describe("3.1.3.6.  ID Token", () => {
    it("at_hash　OPTIONAL. Access Token hash value. Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation of the access_token value, where the hash algorithm used is the hash algorithm used in the alg Header Parameter of the ID Token's JOSE Header. For instance, if the alg is RS256, hash the access_token value with SHA-256, then take the left-most 128 bits and base64url encode them. The at_hash value is a case sensitive string.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
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
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data.token_type).toEqual("Bearer");
      expect(tokenResponse.data).toHaveProperty("expires_in");
      expect(tokenResponse.data).toHaveProperty("scope");
      expect(tokenResponse.data).toHaveProperty("id_token");

      const jwkResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });
      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwkResponse.data,
      });
      console.log(decodedIdToken);
      if (decodedIdToken.payload.at_hash) {
        expect(decodedIdToken.payload.at_hash).toEqual(
          calculateIdTokenClaimHashWithS256(tokenResponse.data.access_token)
        );
      }
    });
  });

  describe("3.1.3.7.  ID Token Validation", () => {
    //Clients MUST validate the ID Token in the Token Response in the following manner:
    it("1. If the ID Token is encrypted, decrypt it using the keys and algorithms that the Client specified during Registration that the OP was to use to encrypt the ID Token. If encryption was negotiated with the OP at Registration time and the ID Token is not encrypted, the RP SHOULD reject it.", async () => {});

    it("2. The Issuer Identifier for the OpenID Provider (which is typically obtained during Discovery) MUST exactly match the value of the iss (issuer) Claim.", async () => {
      const { payload } = await getIdToken({ client: clientSecretPostClient });
      expect(payload.iss).toEqual(serverConfig.issuer);
    });

    it("3. The Client MUST validate that the aud (audience) Claim contains its client_id value registered at the Issuer identified by the iss (issuer) Claim as an audience. The aud (audience) Claim MAY contain an array with more than one element. The ID Token MUST be rejected if the ID Token does not list the Client as a valid audience, or if it contains additional audiences not trusted by the Client.", async () => {
      const { payload } = await getIdToken({ client: clientSecretPostClient });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
    });

    it("4. If the ID Token contains multiple audiences, the Client SHOULD verify that an azp Claim is present.", async () => {
      const { payload } = await getIdToken({ client: clientSecretPostClient });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      if (payload.azp) {
        expect(payload.azp).toContain(clientSecretPostClient.clientId);
      }
    });

    it("5. If an azp (authorized party) Claim is present, the Client SHOULD verify that its client_id is the Claim Value.", async () => {
      const { payload } = await getIdToken({ client: clientSecretPostClient });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      if (payload.azp) {
        expect(payload.azp).toContain(clientSecretPostClient.clientId);
      }
    });

    it("6. If the ID Token is received via direct communication between the Client and the Token Endpoint (which it is in this flow), the TLS server validation MAY be used to validate the issuer in place of checking the token signature. The Client MUST validate the signature of all other ID Tokens according to JWS [JWS] using the algorithm specified in the JWT alg Header Parameter. The Client MUST use the keys provided by the Issuer.", async () => {
      const { payload, verifyResult } = await getIdToken({
        client: clientSecretPostClient,
      });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      if (payload.azp) {
        expect(payload.azp).toContain(clientSecretPostClient.clientId);
      }
      expect(verifyResult).toBe(true);
    });

    it("7. The alg value SHOULD be the default of RS256 or the algorithm sent by the Client in the id_token_signed_response_alg parameter during Registration.", async () => {
      const { header, payload, verifyResult } = await getIdToken({
        client: clientSecretPostClient,
      });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      if (payload.azp) {
        expect(payload.azp).toContain(clientSecretPostClient.clientId);
      }
      expect(verifyResult).toBe(true);
      expect(header.alg).toEqual(clientSecretPostClient.idTokenAlg);
    });

    it("8. If the JWT alg Header Parameter uses a MAC based algorithm such as HS256, HS384, or HS512, the octets of the UTF-8 representation of the client_secret corresponding to the client_id contained in the aud (audience) Claim are used as the key to validate the signature. For MAC based algorithms, the behavior is unspecified if the aud is multi-valued or if an azp value is present that is different than the aud value.", async () => {
      const { header, payload, verifyResult } = await getIdToken({
        client: clientSecretPostClient,
      });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      if (payload.azp) {
        expect(payload.azp).toContain(clientSecretPostClient.clientId);
      }
      expect(verifyResult).toBe(true);
      expect(header.alg).toEqual(clientSecretPostClient.idTokenAlg);
    });

    it("9. The current time MUST be before the time represented by the exp Claim.", async () => {
      const { header, payload, verifyResult } = await getIdToken({
        client: clientSecretPostClient,
      });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      if (payload.azp) {
        expect(payload.azp).toContain(clientSecretPostClient.clientId);
      }
      expect(verifyResult).toBe(true);
      expect(header.alg).toEqual(clientSecretPostClient.idTokenAlg);
      expect(payload.exp > toEpocTime({})).toBe(true);
    });

    it("10. The iat Claim can be used to reject tokens that were issued too far away from the current time, limiting the amount of time that nonces need to be stored to prevent attacks. The acceptable range is Client specific.", async () => {
      const { header, payload, verifyResult } = await getIdToken({
        client: clientSecretPostClient,
      });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      if (payload.azp) {
        expect(payload.azp).toContain(clientSecretPostClient.clientId);
      }
      expect(verifyResult).toBe(true);
      expect(header.alg).toEqual(clientSecretPostClient.idTokenAlg);
      expect(payload.exp > toEpocTime({})).toBe(true);
      expect(payload.iat > toEpocTime({ adjusted: -10000000 })).toBe(true);
    });

    it("11. If a nonce value was sent in the Authentication Request, a nonce Claim MUST be present and its value checked to verify that it is the same value as the one that was sent in the Authentication Request. The Client SHOULD check the nonce value for replay attacks. The precise method for detecting replay attacks is Client specific.", async () => {
      const nonce = "123";
      const { header, payload, verifyResult } = await getIdToken({
        client: clientSecretPostClient,
        nonce,
      });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      if (payload.azp) {
        expect(payload.azp).toContain(clientSecretPostClient.clientId);
      }
      expect(verifyResult).toBe(true);
      expect(header.alg).toEqual(clientSecretPostClient.idTokenAlg);
      expect(payload.exp > toEpocTime({})).toBe(true);
      expect(payload.iat > toEpocTime({ adjusted: -10000000 })).toBe(true);
      expect(payload.nonce).toEqual(nonce);
    });

    it("12. If the acr Claim was requested, the Client SHOULD check that the asserted Claim Value is appropriate. The meaning and processing of acr Claim Values is out of scope for this specification.", async () => {
      const nonce = "123";
      const acrValues = serverConfig.acr;
      const { header, payload, verifyResult } = await getIdToken({
        client: clientSecretPostClient,
        nonce,
        acrValues,
      });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      if (payload.azp) {
        expect(payload.azp).toContain(clientSecretPostClient.clientId);
      }
      expect(verifyResult).toBe(true);
      expect(header.alg).toEqual(clientSecretPostClient.idTokenAlg);
      expect(payload.exp > toEpocTime({})).toBe(true);
      expect(payload.iat > toEpocTime({ adjusted: -10000000 })).toBe(true);
      expect(payload.nonce).toEqual(nonce);
      expect(payload.acr).toEqual(acrValues);
    });

    it("13. If the auth_time Claim was requested, either through a specific request for this Claim or by using the max_age parameter, the Client SHOULD check the auth_time Claim value and request re-authentication if it determines too much time has elapsed since the last End-User authentication.", async () => {
      const nonce = "123";
      const acrValues = serverConfig.acr;
      const maxAge = 1000;
      const { header, payload, verifyResult } = await getIdToken({
        client: clientSecretPostClient,
        nonce,
        acrValues,
        maxAge,
      });
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      if (payload.azp) {
        expect(payload.azp).toContain(clientSecretPostClient.clientId);
      }
      expect(verifyResult).toBe(true);
      expect(header.alg).toEqual(clientSecretPostClient.idTokenAlg);
      expect(payload.exp > toEpocTime({})).toBe(true);
      expect(payload.iat > toEpocTime({ adjusted: -10000000 })).toBe(true);
      expect(payload.nonce).toEqual(nonce);
      expect(payload.acr).toEqual(acrValues);
      expect(payload).toHaveProperty("auth_time");
    });

    describe("3.1.3.8.  Access Token Validation", () => {
      it("When using the Authorization Code Flow, if the ID Token contains an at_hash Claim, the Client MAY use it to validate the Access Token in the same manner as for the Implicit Flow, as defined in Section 3.2.2.9, but using the ID Token and Access Token returned from the Token Endpoint.", async () => {
        const { authorizationResponse } = await requestAuthorizations({
          endpoint: serverConfig.authorizationEndpoint,
          clientId: clientSecretPostClient.clientId,
          redirectUri: clientSecretPostClient.redirectUri,
          responseType: "code",
          scope: "openid " + clientSecretPostClient.scope,
          state: "state",
          responseMode: "query",
          nonce: "nonce",
          display: "page",
          prompt: "login",
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

        const jwkResponse = await getJwks({
          endpoint: serverConfig.jwksEndpoint,
        });
        const decodedIdToken = verifyAndDecodeJwt({
          jwt: tokenResponse.data.id_token,
          jwks: jwkResponse.data,
        });
        console.log(decodedIdToken);
        if (decodedIdToken.payload.at_hash) {
          expect(decodedIdToken.payload.at_hash).toEqual(
            calculateIdTokenClaimHashWithS256(tokenResponse.data.access_token)
          );
        }
      });
    });
  });
});

const getIdToken = async ({ client, nonce, acrValues, maxAge }) => {
  const { authorizationResponse } = await requestAuthorizations({
    endpoint: serverConfig.authorizationEndpoint,
    clientId: client.clientId,
    redirectUri: client.redirectUri,
    responseType: "code",
    scope: "openid " + client.scope,
    state: "state",
    responseMode: "query",
    nonce,
    display: "page",
    prompt: "login",
    acrValues,
    maxAge,
  });
  console.log(authorizationResponse);
  expect(authorizationResponse.code).not.toBeNull();

  const tokenResponse = await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    code: authorizationResponse.code,
    grantType: "authorization_code",
    redirectUri: client.redirectUri,
    clientId: client.clientId,
    clientSecret: client.clientSecret,
  });
  console.log(tokenResponse.data);
  expect(tokenResponse.status).toBe(200);
  expect(tokenResponse.status).toBe(200);
  expect(tokenResponse.data).toHaveProperty("access_token");
  expect(tokenResponse.data.token_type).toEqual("Bearer");
  expect(tokenResponse.data).toHaveProperty("expires_in");
  expect(tokenResponse.data).toHaveProperty("scope");
  expect(tokenResponse.data).toHaveProperty("id_token");

  const jwkResponse = await getJwks({
    endpoint: serverConfig.jwksEndpoint,
  });
  const decodedIdToken = verifyAndDecodeJwt({
    jwt: tokenResponse.data.id_token,
    jwks: jwkResponse.data,
  });
  console.log(decodedIdToken);
  return decodedIdToken;
};
