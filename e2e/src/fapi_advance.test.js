import { describe, expect, it, xit } from "@jest/globals";

import { getJwks, inspectToken, requestToken } from "./api/oauthClient";
import {
  clientSecretBasicClient, clientSecretPostClient, privateKeyJwtClient,
  selfSignedTlsAuthClient,
  serverConfig,
} from "./testConfig";
import { requestAuthorizations, certThumbprint } from "./oauth";
import { createJwtWithPrivateKey, generateJti, verifyAndDecodeJwt } from "./lib/jose";
import {
  calculateCodeChallengeWithS256,
  createClientAssertion,
  createInvalidClientAssertionWithPrivateKey,
} from "./lib/oauth";
import { toEpocTime } from "./lib/util";

describe("Financial-grade API Security Profile 1.0 - Part 2: Advanced", () => {
  it("success", async () => {
    const codeVerifier = "aiueo12345678";
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
    const request = createJwtWithPrivateKey({
      payload: {
        response_type: "code",
        state: "aiueo",
        scope: "openid profile phone email " + selfSignedTlsAuthClient.fapiAdvanceScope,
        redirect_uri: selfSignedTlsAuthClient.redirectUri,
        client_id: selfSignedTlsAuthClient.clientId,
        nonce: "nonce",
        aud: serverConfig.issuer,
        iss: selfSignedTlsAuthClient.clientId,
        sub: selfSignedTlsAuthClient.clientId,
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
        response_mode: "jwt",
        exp: toEpocTime({ adjusted: 3000 }),
        iat: toEpocTime({}),
        nbf: toEpocTime({}),
        jti: generateJti(),
      },
      privateKey: selfSignedTlsAuthClient.requestKey,
    });
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      request,
      clientId: selfSignedTlsAuthClient.clientId,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.response).not.toBeNull();

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedResponse = verifyAndDecodeJwt({
      jwt: authorizationResponse.response,
      jwks: jwksResponse.data,
    });

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: decodedResponse.payload.code,
      grantType: "authorization_code",
      redirectUri: selfSignedTlsAuthClient.redirectUri,
      clientId: selfSignedTlsAuthClient.clientId,
      clientCertFile: selfSignedTlsAuthClient.clientCertFile,
      codeVerifier,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");


    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);

    const introspectionResponse = await inspectToken({
      endpoint: serverConfig.tokenIntrospectionEndpoint,
      token: tokenResponse.data.access_token,
    });
    console.log(introspectionResponse.data);
    expect(introspectionResponse.status).toBe(200);
    expect(introspectionResponse.data).toHaveProperty("cnf");
    const thumbprint = certThumbprint(selfSignedTlsAuthClient.clientCertFile);
    expect(introspectionResponse.data.cnf["x5t#S256"]).toEqual(thumbprint);
  });

  describe("5.1.1.  ID Token as Detached Signature", () => {
    it("While the name ID Token (as used in the OpenID Connect Hybrid Flow) suggests that it is something that provides the identity of the resource owner (subject), it is not necessarily so. While it does identify the authorization server by including the issuer identifier, it is perfectly fine to have an ephemeral subject identifier. In this case, the ID Token acts as a detached signature of the issuer to the authorization response and it was an explicit design decision of OpenID Connect Core to make the ID Token act as a detached signature.", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const request = createJwtWithPrivateKey({
        payload: {
          response_type: "code id_token",
          state: "aiueo",
          scope: "openid profile phone email " + selfSignedTlsAuthClient.fapiAdvanceScope,
          redirect_uri: selfSignedTlsAuthClient.redirectUri,
          client_id: selfSignedTlsAuthClient.clientId,
          nonce: "nonce",
          aud: serverConfig.issuer,
          iss: selfSignedTlsAuthClient.clientId,
          sub: selfSignedTlsAuthClient.clientId,
          code_challenge: codeChallenge,
          code_challenge_method: "S256",
          response_mode: "code",
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: selfSignedTlsAuthClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: selfSignedTlsAuthClient.clientId,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedResponseIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });

      expect(decodedResponseIdToken.payload).toHaveProperty("s_hash");

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: selfSignedTlsAuthClient.redirectUri,
        clientId: selfSignedTlsAuthClient.clientId,
        clientCertFile: selfSignedTlsAuthClient.clientCertFile,
        codeVerifier,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("id_token");


      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);

      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data).toHaveProperty("cnf");
      const thumbprint = certThumbprint(selfSignedTlsAuthClient.clientCertFile);
      expect(introspectionResponse.data.cnf["x5t#S256"]).toEqual(thumbprint);
    });
  });

  describe("5.2.2.  Authorization server", () => {
    it ("1. shall require a JWS signed JWT request object passed by value with the request parameter or by reference with the request_uri parameter;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "profile phone email " + selfSignedTlsAuthClient.fapiAdvanceScope,
        clientId: selfSignedTlsAuthClient.clientId,
        redirectUri: selfSignedTlsAuthClient.redirectUri,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(authorizationResponse);

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedResponse = verifyAndDecodeJwt({
        jwt: authorizationResponse.response,
        jwks: jwksResponse.data,
      });

      expect(decodedResponse.payload.error).toEqual("invalid_request");
      expect(decodedResponse.payload.error_description).toContain("When FAPI Advance profile, shall require a JWS signed JWT request object passed by value with the request parameter or by reference with the request_uri parameter");
    });

    it ("2. shall require the response_type value code id_token, the response_type value code in conjunction with the response_mode value jwt;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const request = createJwtWithPrivateKey({
        payload: {
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + selfSignedTlsAuthClient.fapiAdvanceScope,
          redirect_uri: selfSignedTlsAuthClient.redirectUri,
          client_id: selfSignedTlsAuthClient.clientId,
          nonce: "nonce",
          aud: serverConfig.issuer,
          iss: selfSignedTlsAuthClient.clientId,
          sub: selfSignedTlsAuthClient.clientId,
          code_challenge: codeChallenge,
          code_challenge_method: "S256",
          response_mode: "query",
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: selfSignedTlsAuthClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: selfSignedTlsAuthClient.clientId,
      });
      console.log(authorizationResponse);

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedResponse = verifyAndDecodeJwt({
        jwt: authorizationResponse.response,
        jwks: jwksResponse.data,
      });

      expect(decodedResponse.payload.error).toEqual("invalid_request");
      expect(decodedResponse.payload.error_description).toContain("When FAPI Advance profile, shall require the response_type value code id_token, or the response_type value code in conjunction with the response_mode value jwt");
    });

    it ("5. shall only issue sender-constrained access tokens;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const request = createJwtWithPrivateKey({
        payload: {
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + selfSignedTlsAuthClient.fapiAdvanceScope,
          redirect_uri: selfSignedTlsAuthClient.redirectUri,
          client_id: selfSignedTlsAuthClient.clientId,
          nonce: "nonce",
          aud: serverConfig.issuer,
          iss: selfSignedTlsAuthClient.clientId,
          sub: selfSignedTlsAuthClient.clientId,
          code_challenge: codeChallenge,
          code_challenge_method: "S256",
          response_mode: "jwt",
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: selfSignedTlsAuthClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: selfSignedTlsAuthClient.clientId,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.response).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedResponse = verifyAndDecodeJwt({
        jwt: authorizationResponse.response,
        jwks: jwksResponse.data,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: decodedResponse.payload.code,
        grantType: "authorization_code",
        redirectUri: selfSignedTlsAuthClient.redirectUri,
        clientId: selfSignedTlsAuthClient.clientId,
        clientCertFile: selfSignedTlsAuthClient.clientCertFile,
        codeVerifier,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("id_token");


      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);

      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data).toHaveProperty("cnf");
      const thumbprint = certThumbprint(selfSignedTlsAuthClient.clientCertFile);
      expect(introspectionResponse.data.cnf["x5t#S256"]).toEqual(thumbprint);
    });

    it("13. shall require the request object to contain an exp claim that has a lifetime of no longer than 60 minutes after the nbf claim;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const request = createJwtWithPrivateKey({
        payload: {
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + selfSignedTlsAuthClient.fapiAdvanceScope,
          redirect_uri: selfSignedTlsAuthClient.redirectUri,
          client_id: selfSignedTlsAuthClient.clientId,
          nonce: "nonce",
          aud: serverConfig.issuer,
          iss: selfSignedTlsAuthClient.clientId,
          sub: selfSignedTlsAuthClient.clientId,
          code_challenge: codeChallenge,
          code_challenge_method: "S256",
          response_mode: "jwt",
          exp: toEpocTime({ adjusted: 3601 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: selfSignedTlsAuthClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: selfSignedTlsAuthClient.clientId,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.response).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedResponse = verifyAndDecodeJwt({
        jwt: authorizationResponse.response,
        jwks: jwksResponse.data,
      });

      expect(decodedResponse.payload.error).toEqual("invalid_request_object");
      expect(decodedResponse.payload.error_description).toEqual("When FAPI Advance profile, shall require the request object to contain an exp claim that has a lifetime of no longer than 60 minutes after the nbf claim");
    });

    xit("14. shall authenticate the confidential client using one of the following methods (this overrides FAPI Security Profile 1.0 - Part 1: Baseline clause 5.2.2-4): tls_client_auth or self_signed_tls_client_auth as specified in section 2 of MTLS, or private_key_jwt as specified in section 9 of OIDC;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const request = createJwtWithPrivateKey({
        payload: {
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + clientSecretPostClient.fapiAdvanceScope,
          redirect_uri: clientSecretPostClient.redirectUri,
          client_id: clientSecretPostClient.clientId,
          nonce: "nonce",
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          sub: clientSecretPostClient.clientId,
          code_challenge: codeChallenge,
          code_challenge_method: "S256",
          response_mode: "jwt",
          exp: toEpocTime({ adjusted: 3601 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: clientSecretPostClient.clientId,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.response).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedResponse = verifyAndDecodeJwt({
        jwt: authorizationResponse.response,
        jwks: jwksResponse.data,
      });

      expect(decodedResponse.payload.error).toEqual("invalid_request_object");
      expect(decodedResponse.payload.error_description).toEqual("When FAPI Advance profile, shall require the request object to contain an exp claim that has a lifetime of no longer than 60 minutes after the nbf claim");
    });

    it("15. shall require the aud claim in the request object to be, or to be an array containing, the OP's Issuer Identifier URL;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const request = createJwtWithPrivateKey({
        payload: {
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + selfSignedTlsAuthClient.fapiAdvanceScope,
          redirect_uri: selfSignedTlsAuthClient.redirectUri,
          client_id: selfSignedTlsAuthClient.clientId,
          nonce: "nonce",
          iss: selfSignedTlsAuthClient.clientId,
          sub: selfSignedTlsAuthClient.clientId,
          code_challenge: codeChallenge,
          code_challenge_method: "S256",
          response_mode: "jwt",
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: selfSignedTlsAuthClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: selfSignedTlsAuthClient.clientId,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.response).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedResponse = verifyAndDecodeJwt({
        jwt: authorizationResponse.response,
        jwks: jwksResponse.data,
      });

      expect(decodedResponse.payload.error).toEqual("invalid_request_object");
      expect(decodedResponse.payload.error_description).toEqual("When FAPI Advance profile, shall require the request object to contain an aud claim");
    });

    it("17. shall require the request object to contain an nbf claim that is no longer than 60 minutes in the past; and", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const request = createJwtWithPrivateKey({
        payload: {
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + selfSignedTlsAuthClient.fapiAdvanceScope,
          redirect_uri: selfSignedTlsAuthClient.redirectUri,
          client_id: selfSignedTlsAuthClient.clientId,
          nonce: "nonce",
          aud: serverConfig.issuer,
          iss: selfSignedTlsAuthClient.clientId,
          sub: selfSignedTlsAuthClient.clientId,
          code_challenge: codeChallenge,
          code_challenge_method: "S256",
          response_mode: "jwt",
          exp: toEpocTime({ adjusted: -200 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({ adjusted: -3601}),
          jti: generateJti(),
        },
        privateKey: selfSignedTlsAuthClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: selfSignedTlsAuthClient.clientId,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.response).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedResponse = verifyAndDecodeJwt({
        jwt: authorizationResponse.response,
        jwks: jwksResponse.data,
      });

      expect(decodedResponse.payload.error).toEqual("invalid_request_object");
      expect(decodedResponse.payload.error_description).toEqual("When FAPI Advance profile, shall require the request object to contain an nbf claim that is no longer than 60 minutes in the past");
    });
  });

});
