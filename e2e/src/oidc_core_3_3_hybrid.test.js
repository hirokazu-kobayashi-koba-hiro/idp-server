import { describe, expect, it } from "@jest/globals";

import { getJwks, requestToken } from "./api/oauthClient";
import {
  clientSecretBasicClient,
  clientSecretPostClient,
  serverConfig,
} from "./testConfig";
import { requestAuthorizations } from "./oauth";
import { createJwtWithPrivateKey, verifyAndDecodeJwt } from "./lib/jose";
import { createBasicAuthHeader, toEpocTime } from "./lib/util";
import { calculateIdTokenClaimHashWithS256 } from "./lib/oauth";

describe("OpenID Connect Core 1.0 incorporating errata set 1 code hybrid", () => {
  describe("success pattern", () => {
    it("code token", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code token",
        state: "aiueo",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        nonce: "nonce",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.expiresIn).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        nonce: "nonce",
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("id_token");
    });

    it("code token id_token", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code token id_token",
        state: "aiueo",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        nonce: "nonce",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.expiresIn).not.toBeNull();
      expect(authorizationResponse.idToken).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        nonce: "nonce",
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("id_token");
    });

    it("code id_token", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code id_token",
        state: "aiueo",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        nonce: "nonce",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.idToken).not.toBeNull();

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

    it("none", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "none",
        state: "aiueo",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).toBeNull();
      expect(authorizationResponse.accessToken).toBeNull();
      expect(authorizationResponse.idToken).toBeNull();
    });
  });

  describe("3.2.2.1.  Authentication Request", () => {
    //Authentication Requests are made as defined in Section 3.1.2.1, except that these Authentication Request parameters are used as follows:

    it("response_type REQUIRED. OAuth 2.0 Response Type value that determines the authorization processing flow to be used, including what parameters are returned from the endpoints used. When using the Implicit Flow, this value is id_token token or id_token. The meanings of both of these values are defined in OAuth 2.0 Multiple Response Type Encoding Practices [OAuth.Responses]. No Access Token is returned when the value is id_token.", async () => {
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

    it("redirect_uri REQUIRED. Redirection URI to which the response will be sent. ", async () => {
      const { status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid " + clientSecretPostClient.scope,
        responseType: "code token id_token",
      });
      console.log(error);
      // expect(status).toBe(400);

      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toEqual(
        "oidc profile authorization request must contains redirect_uri param"
      );
    });

    it("redirect_uri This URI MUST exactly match one of the Redirection URI values for the Client pre-registered at the OpenID Provider, with the matching performed as described in Section 6.2.1 of [RFC3986] (Simple String Comparison).", async () => {
      const { status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        redirectUri: clientSecretPostClient.redirectUri + "/aiueo",
        scope: "openid " + clientSecretPostClient.scope,
        responseType: "code token id_token",
        clientId: clientSecretPostClient.clientId,
      });
      console.log(error);
      // expect(status).toBe(400);

      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toContain(
        "authorization request redirect_uri does not register in client configuration"
      );
    });

    it("nonce REQUIRED. String value used to associate a Client session with an ID Token, and to mitigate replay attacks. The value is passed through unmodified from the Authentication Request to the ID Token. Sufficient entropy MUST be present in the nonce values used to prevent attackers from guessing values. For implementation notes, see Section 15.5.2.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code token id_token",
        redirectUri: clientSecretPostClient.redirectUri,
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
      });

      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toContain(
        "When using implicit flow or hybrid flow, authorization request must contains nonce."
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
        responseType: "code token id_token",
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
        responseType: "code token id_token",
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
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.idToken).not.toBeNull();
    });
  });

  describe("3.3.2.5.  Successful Authentication Response", () => {
    //When using the Hybrid Flow, Authentication Responses are made in the same manner as for the Implicit Flow, as defined in Section 3.2.2.5, with the exception of the differences specified in this section.
    it("code Authorization Code. This is always returned when using the Hybrid Flow.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.idToken).not.toBeNull();
      expect(authorizationResponse.state).toEqual("state");
      expect(authorizationResponse.expiresIn).not.toBeNull();
    });

    it("When using the Implicit Flow, all response parameters are added to the fragment component of the Redirection URI, as specified in OAuth 2.0 Multiple Response Type Encoding Practices [OAuth.Responses], unless a different Response Mode was specified.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.code).not.toBeNull();
    });

    it("access_token OAuth 2.0 Access Token.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.accessToken).not.toBeNull();
    });

    it("access_token This is returned unless the response_type value used is id_token.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.accessToken).toBeNull();
    });

    it("token_type OAuth 2.0 Token Type value. The value MUST be Bearer or another token_type value that the Client has negotiated with the Authorization Server. Clients implementing this profile MUST support the OAuth 2.0 Bearer Token Usage [RFC6750] specification. This profile only describes the use of bearer tokens. This is returned in the same cases as access_token is.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
    });

    it("id_token REQUIRED. ID Token.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.idToken).not.toBeNull();
    });

    it("state OAuth 2.0 state value. REQUIRED if the state parameter is present in the Authorization Request. Clients MUST verify that the state value is equal to the value of state parameter in the Authorization Request.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.idToken).not.toBeNull();
      expect(authorizationResponse.state).toEqual("state");
    });

    it("expires_in OPTIONAL. Expiration time of the Access Token in seconds since the response was generated.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.idToken).not.toBeNull();
      expect(authorizationResponse.state).toEqual("state");
      expect(authorizationResponse.expiresIn).not.toBeNull();
    });

  });

  describe("3.2.2.9.  Access Token Validation", () => {
    //To validate an Access Token issued from the Authorization Endpoint with an ID Token, the Client SHOULD do the following:
    //Hash the octets of the ASCII representation of the access_token with the hash algorithm specified in JWA [JWA] for the alg Header Parameter of the ID Token's JOSE Header. For instance, if the alg is RS256, the hash algorithm used is SHA-256.
    // Take the left-most half of the hash and base64url encode it.
    it("The value of at_hash in the ID Token MUST match the value produced in the previous step.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.idToken).not.toBeNull();
      expect(authorizationResponse.state).toEqual("state");
      expect(authorizationResponse.expiresIn).not.toBeNull();

      const jwksResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });

      expect(decodedIdToken.payload.at_hash).toEqual(
        calculateIdTokenClaimHashWithS256(authorizationResponse.accessToken)
      );
    });
  });

  describe("3.3.2.11.  ID Token", () => {
    //The contents of the ID Token are as described in Section 2. When using the Implicit Flow, these additional requirements for the following ID Token Claims apply:
    it("nonce Use of the nonce Claim is REQUIRED for this flow.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.idToken).not.toBeNull();
      expect(authorizationResponse.state).toEqual("state");
      expect(authorizationResponse.expiresIn).not.toBeNull();

      const jwksResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });

      expect(decodedIdToken.payload).toHaveProperty("nonce");
    });

    it("at_hash Access Token hash value. Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation of the access_token value, where the hash algorithm used is the hash algorithm used in the alg Header Parameter of the ID Token's JOSE Header. For instance, if the alg is RS256, hash the access_token value with SHA-256, then take the left-most 128 bits and base64url encode them. The at_hash value is a case sensitive string.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.accessToken).not.toBeNull();
      expect(authorizationResponse.tokenType).toEqual("Bearer");
      expect(authorizationResponse.idToken).not.toBeNull();
      expect(authorizationResponse.state).toEqual("state");
      expect(authorizationResponse.expiresIn).not.toBeNull();

      const jwksResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });

      expect(decodedIdToken.payload).toHaveProperty("nonce");
      expect(decodedIdToken.payload.at_hash).toEqual(
        calculateIdTokenClaimHashWithS256(authorizationResponse.accessToken)
      );
    });

    it("at_hash If the ID Token is issued from the Authorization Endpoint with an access_token value, which is the case for the response_type value id_token token, this is REQUIRED; it MAY NOT be used when no Access Token is issued, which is the case for the response_type value id_token.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.idToken).not.toBeNull();
      expect(authorizationResponse.state).toEqual("state");

      const jwksResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });

      expect(decodedIdToken.payload).toHaveProperty("nonce");
      expect(decodedIdToken.payload).not.toHaveProperty("at_hash");
    });

    it("c_hash Code hash value. Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation of the code value, where the hash algorithm used is the hash algorithm used in the alg Header Parameter of the ID Token's JOSE Header. For instance, if the alg is HS512, hash the code value with SHA-512, then take the left-most 256 bits and base64url encode them. The c_hash value is a case sensitive string.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.idToken).not.toBeNull();
      expect(authorizationResponse.state).toEqual("state");

      const jwksResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);

      expect(decodedIdToken.payload).toHaveProperty("nonce");
      expect(decodedIdToken.payload).not.toHaveProperty("at_hash");
      expect(decodedIdToken.payload).toHaveProperty("c_hash");
      expect(decodedIdToken.payload.c_hash).toEqual(calculateIdTokenClaimHashWithS256(authorizationResponse.code));
    });
  });

  describe("3.3.2.12.  ID Token Validation", () => {
    //When using the Implicit Flow, the contents of the ID Token MUST be validated in the same manner as for the Authorization Code Flow, as defined in Section 3.1.3.7, with the exception of the differences specified in this section.
    it("1. The Client MUST validate the signature of the ID Token according to JWS [JWS] using the algorithm specified in the alg Header Parameter of the JOSE Header.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce: "nonce",
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.idToken).not.toBeNull();
      expect(authorizationResponse.state).toEqual("state");

      const jwksResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });

      expect(decodedIdToken.verifyResult).toEqual(true);
    });

    it("2. The value of the nonce Claim MUST be checked to verify that it is the same value as the one that was sent in the Authentication Request. The Client SHOULD check the nonce value for replay attacks. The precise method for detecting replay attacks is Client specific.", async () => {
      const nonce = "nonceValue";
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        nonce,
        display: "page",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.responseMode).toEqual("#");
      expect(authorizationResponse.idToken).not.toBeNull();
      expect(authorizationResponse.state).toEqual("state");

      const jwksResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });

      expect(decodedIdToken.verifyResult).toEqual(true);
      expect(decodedIdToken.payload.nonce).toEqual(nonce);
    });
  });

  describe("3.3.3.6.  ID Token", () => {
    //When using the Hybrid Flow, the contents of an ID Token returned from the Token Endpoint are the same as for an ID Token returned from the Authorization Endpoint, as defined in Section 3.3.2.11, with the exception of the differences specified in this section.
    it("If an ID Token is returned from both the Authorization Endpoint and from the Token Endpoint, which is the case for the response_type values code id_token and code id_token token, the iss and sub Claim Values MUST be identical in both ID Tokens. ", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        clientSecretPostClient,
        display: "page",
        prompt: "login",
        nonce: "nonce",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.idToken).not.toBeNull();

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

      const jwkResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwkResponse.data,
      });

      const decodedIdTokenTokenResponse = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwkResponse.data,
      });

      expect(decodedIdToken.payload.iss).toEqual(decodedIdTokenTokenResponse.payload.iss);
      expect(decodedIdToken.payload.sub).toEqual(decodedIdTokenTokenResponse.payload.sub);
    });
  });

  describe("3.3.3.7.  ID Token Validation", () => {
    it("When using the Hybrid Flow, the contents of an ID Token returned from the Token Endpoint MUST be validated in the same manner as for the Authorization Code Flow, as defined in Section 3.1.3.7.", async () => {
      const nonce = "1234";
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        clientSecretPostClient,
        display: "page",
        prompt: "login",
        nonce,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.idToken).not.toBeNull();

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

      const jwkResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwkResponse.data,
      });

      const decodedIdTokenTokenResponse = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwkResponse.data,
      });

      expect(decodedIdToken.payload.iss).toEqual(decodedIdTokenTokenResponse.payload.iss);
      expect(decodedIdToken.payload.sub).toEqual(decodedIdTokenTokenResponse.payload.sub);
      expect(decodedIdToken.verifyResult).toEqual(true);
      expect(decodedIdToken.payload.nonce).toEqual(nonce);
    });
  });

  describe("3.3.3.9.  Access Token Validation", () => {
    it("When using the Hybrid Flow, the Access Token returned from the Token Endpoint is validated in the same manner as for the Authorization Code Flow, as defined in Section 3.1.3.8.", async () => {
      const nonce = "1234";
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        responseType: "code token id_token",
        scope: "openid " + clientSecretPostClient.scope,
        state: "state",
        responseMode: "query",
        clientSecretPostClient,
        display: "page",
        prompt: "login",
        nonce,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
      expect(authorizationResponse.idToken).not.toBeNull();

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

      const jwkResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwkResponse.data,
      });

      const decodedIdTokenTokenResponse = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwkResponse.data,
      });

      expect(decodedIdToken.payload.iss).toEqual(decodedIdTokenTokenResponse.payload.iss);
      expect(decodedIdToken.payload.sub).toEqual(decodedIdTokenTokenResponse.payload.sub);
      expect(decodedIdToken.verifyResult).toEqual(true);
      expect(decodedIdTokenTokenResponse.payload.nonce).toEqual(nonce);
      expect(decodedIdTokenTokenResponse.payload.at_hash).toEqual(
        calculateIdTokenClaimHashWithS256(tokenResponse.data.access_token)
      );
    });
  });
});

