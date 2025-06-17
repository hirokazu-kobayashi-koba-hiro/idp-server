import { describe, expect, it } from "@jest/globals";

import { getJwks, inspectToken, requestToken } from "../../api/oauthClient";
import {
  clientSecretBasicClient, clientSecretPostClient, privateKeyJwtClient,
  selfSignedTlsAuthClient,
  serverConfig,
} from "../testConfig";
import { requestAuthorizations, certThumbprint } from "../../oauth/signin";
import { createJwtWithPrivateKey, generateJti, verifyAndDecodeJwt } from "../../lib/jose";
import {
  calculateCodeChallengeWithS256,
  createInvalidClientAssertionWithPrivateKey,
} from "../../lib/oauth";
import { toEpocTime } from "../../lib/util";

describe("Financial-grade API Security Profile 1.0 - Part 1: Baseline", () => {
  it("success", async () => {
    const codeVerifier = "aiueo12345678";
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email " + selfSignedTlsAuthClient.fapiBaselineScope,
      redirectUri: selfSignedTlsAuthClient.redirectUri,
      clientId: selfSignedTlsAuthClient.clientId,
      nonce: "nonce",
      codeChallenge,
      codeChallengeMethod: "S256",
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

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


  describe("5.2.2.  Authorization server", () => {
    it("1. shall support confidential clients;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email " + selfSignedTlsAuthClient.fapiBaselineScope,
        redirectUri: selfSignedTlsAuthClient.redirectUri,
        clientId: selfSignedTlsAuthClient.clientId,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
    });

    it("2. should support public clients;", async () => {

    });

    it("3. shall provide a client secret that adheres to the requirements in Section 16.19 of OIDC if a symmetric key is used;", async () => {

    });

    it("4. shall authenticate the confidential client using one of the following methods: unauthorized client_secret_basic", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email " + clientSecretBasicClient.fapiBaselineScope,
        redirectUri: clientSecretBasicClient.redirectUri,
        clientId: clientSecretBasicClient.clientId,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).toEqual("unauthorized_client");
      expect(authorizationResponse.errorDescription).toEqual("When FAPI Baseline profile, client_secret_basic MUST not used");
    });

    it("4. shall authenticate the confidential client using one of the following methods: unauthorized client_secret_post", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email " + clientSecretPostClient.fapiBaselineScope,
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).toEqual("unauthorized_client");
      expect(authorizationResponse.errorDescription).toEqual("When FAPI Baseline profile, client_secret_post MUST not used");
    });

    it("5. shall require and use a key of size 2048 bits or larger for RSA algorithms;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email " + privateKeyJwtClient.fapiBaselineScope,
        redirectUri: privateKeyJwtClient.redirectUri,
        clientId: privateKeyJwtClient.clientId,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const clientAssertion = createInvalidClientAssertionWithPrivateKey({
        client: privateKeyJwtClient,
        issuer: serverConfig.issuer,
        invalidPrivateKey: privateKeyJwtClient.clientSecretKeyWith2040,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: privateKeyJwtClient.redirectUri,
        clientId: privateKeyJwtClient.clientId,
        nonce: "nonce",
        codeVerifier,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("unauthorized_client");
      expect(tokenResponse.data.error_description).toEqual("When FAPI Baseline profile, shall require and use a key of size 2048 bits or larger for RSA algorithms");
    });

    it("6. shall require and use a key of size 160 bits or larger for elliptic curve algorithms;", async () => {

    });

    it("7. shall require RFC7636 with S256 as the code challenge method;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email " + privateKeyJwtClient.fapiBaselineScope,
        redirectUri: privateKeyJwtClient.redirectUri,
        clientId: privateKeyJwtClient.clientId,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "plain",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual("When FAPI Baseline profile, shall require RFC7636 with S256 as the code challenge method.");
    });

    it("8. shall require redirect URIs to be pre-registered;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "profile phone email " + privateKeyJwtClient.fapiBaselineScope,
        redirectUri: privateKeyJwtClient.redirectUriWithPort,
        clientId: privateKeyJwtClient.clientId,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(error);
      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toContain("When FAPI Baseline profile, shall require the value of redirect_uri to exactly match one of the pre-registered redirect URIs");
    });

    it("9. shall require the redirect_uri in the authorization request;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "profile phone email " + privateKeyJwtClient.fapiBaselineScope,
        clientId: privateKeyJwtClient.clientId,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(error);
      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toEqual("When FAPI Baseline profile, shall require the redirect_uri in the authorization request");
    });

    it("10. shall require the value of redirect_uri to exactly match one of the pre-registered redirect URIs;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "profile phone email " + privateKeyJwtClient.fapiBaselineScope,
        clientId: privateKeyJwtClient.clientId,
        redirectUri: privateKeyJwtClient.redirectUriWithPort,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(error);
      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toContain("When FAPI Baseline profile, shall require the value of redirect_uri to exactly match one of the pre-registered redirect URIs");
    });

    it("19. shall return an invalid_client error as defined in 5.2 of RFC6749 when mis-matched client identifiers were provided through the client authentication methods that permits sending the client identifier in more than one way;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email " + privateKeyJwtClient.fapiBaselineScope,
        redirectUri: privateKeyJwtClient.redirectUri,
        clientId: privateKeyJwtClient.clientId,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const clientAssertion = createJwtWithPrivateKey({
        payload: {
          iss: "clientId",
          sub: "clientId",
          aud: serverConfig.issuer,
          jti: generateJti(),
          exp: toEpocTime({ adjusted: 3600 }),
          iat: toEpocTime({ adjusted: 0 }),
        },
        privateKey: privateKeyJwtClient.clientSecretKey,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: privateKeyJwtClient.redirectUri,
        clientId: privateKeyJwtClient.clientId,
        codeVerifier,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_client");
      expect(tokenResponse.data.error_description).toContain("When FAPI Baseline profile, client_id must matched client_assertion sub claim");
    });

    it("20. shall require redirect URIs to use the https scheme;", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "profile phone email " + privateKeyJwtClient.fapiBaselineScope,
        clientId: privateKeyJwtClient.clientId,
        redirectUri: privateKeyJwtClient.redirectUriWithHttp,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(error);
      expect(error.error).toEqual("invalid_request");
      expect(error.error_description).toContain("When FAPI Baseline profile, shall shall require redirect URIs to use the https scheme");
    });
  });

  describe("5.2.2.2.  Client requesting openid scope", () => {
    it("If the client requests the openid scope, the authorization server shall require the nonce parameter defined in Section 3.1.2.1 of OIDC in the authentication request.", async () => {
      const codeVerifier = "aiueo12345678";
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email " + privateKeyJwtClient.fapiBaselineScope,
        clientId: privateKeyJwtClient.clientId,
        redirectUri: privateKeyJwtClient.redirectUri,
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).toEqual("invalid_request");
      expect(authorizationResponse.errorDescription).toEqual("When FAPI Baseline profile, shall require the nonce parameter defined in Section 3.1.2.1 of OIDC in the authentication request.");
    });
  });

});
