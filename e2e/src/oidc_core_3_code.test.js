import { describe, expect, it, xit } from "@jest/globals";

import { requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";

describe("OpenID Connect Core 1.0 incorporating errata set 1 code", () => {
  it("success pattern", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
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
      expect(status).toBe(400);

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
      expect(status).toBe(200);

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
      expect(status).toBe(200);

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
      expect(status).toBe(200);

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

    it("prompt none The Authorization Server MUST NOT display any authentication or consent user interface pages. An error is returned if an End-User is not already authenticated or the Client does not have pre-configured consent for the requested Claims or does not fulfill other conditions for processing the request. The error code will typically be login_required, interaction_required, or another code defined in Section 3.1.2.6. This can be used as a method to check for existing authentication and/or consent. ", async () => {
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
      expect(authorizationResponse.errorDescription).toEqual("invalid session");
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
});
