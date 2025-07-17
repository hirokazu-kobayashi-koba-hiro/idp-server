import { describe, expect, it } from "@jest/globals";

import { getJwks, inspectTokenWithVerification, requestToken } from "../../../api/oauthClient";
import {
  clientSecretBasicClient,
  clientSecretJwtClient,
  clientSecretPostClient,
  privateKeyJwtClient, selfSignedTlsAuthClient,
  serverConfig
} from "../../testConfig";
import { certThumbprint, requestAuthorizations } from "../../../oauth/request";
import { createBasicAuthHeader, toEpocTime } from "../../../lib/util";
import { calculateCodeChallengeWithS256, createClientAssertion, generateCodeVerifier } from "../../../lib/oauth";
import { createJwtWithPrivateKey, generateJti, verifyAndDecodeJwt } from "../../../lib/jose";

describe("OAuth 2.0 Token Introspection Extensions", () => {
  describe("success pattern", () => {

    it("client_secret_post ", async () => {

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: "openid " + clientSecretPostClient.scope,
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

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
    });

    it("client_secret_basic ", async () => {

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: "openid " + clientSecretBasicClient.scope,
        redirectUri: clientSecretBasicClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretBasicClient.redirectUri,
        basicAuth: createBasicAuthHeader({
          username: clientSecretBasicClient.clientId,
          password: clientSecretBasicClient.clientSecret,
        })
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("id_token");

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        basicAuth: createBasicAuthHeader({
          username: clientSecretBasicClient.clientId,
          password: clientSecretBasicClient.clientSecret,
        })
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
    });

    it("client_secret_jwt", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email " + clientSecretJwtClient.scope,
        redirectUri: clientSecretJwtClient.redirectUri,
        clientId: clientSecretJwtClient.clientId,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const clientAssertion = createClientAssertion({
        client: clientSecretJwtClient,
        issuer: serverConfig.issuer,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretJwtClient.redirectUri,
        clientId: clientSecretJwtClient.clientId,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("id_token");

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretJwtClient.clientId,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
    });

    it("private_key_jwt", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email " + privateKeyJwtClient.scope,
        redirectUri: privateKeyJwtClient.redirectUri,
        clientId: privateKeyJwtClient.clientId,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const clientAssertion = createClientAssertion({
        client: privateKeyJwtClient,
        issuer: serverConfig.issuer,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: privateKeyJwtClient.redirectUri,
        clientId: privateKeyJwtClient.clientId,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("id_token");

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: privateKeyJwtClient.clientId,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
    });

    it("self_signed_tls_client_auth", async () => {
      const codeVerifier = generateCodeVerifier(64);
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

      console.log(decodedResponse);

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

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: selfSignedTlsAuthClient.clientId,
        clientCert: selfSignedTlsAuthClient.clientCertFile,
        clientCertFile: selfSignedTlsAuthClient.clientCertFile,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data).toHaveProperty("cnf");
      const thumbprint = certThumbprint(selfSignedTlsAuthClient.clientCertFile);
      expect(introspectionResponse.data.cnf["x5t#S256"]).toEqual(thumbprint);
    });
  });

  describe("401 error", () => {

    it("requires mTLS client certificate self_signed_tls_client_auth", async () => {
      const codeVerifier = generateCodeVerifier(64);
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

      console.log(decodedResponse);

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

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: selfSignedTlsAuthClient.clientId,
        clientCertFile: selfSignedTlsAuthClient.clientCertFile,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toEqual(false);
      expect(introspectionResponse.data.error).toEqual("invalid_token");
      expect(introspectionResponse.data.error_description).toEqual("Sender-constrained access token requires mTLS client certificate, but none was provided.");
      expect(introspectionResponse.data.status_code).toEqual(401);
    });

    it("unmatch mTLS client certificate self_signed_tls_client_auth", async () => {
      const codeVerifier = generateCodeVerifier(64);
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

      console.log(decodedResponse);

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

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: selfSignedTlsAuthClient.clientId,
        clientCert: "exampleCertificate.pem",
        clientCertFile: selfSignedTlsAuthClient.clientCertFile,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toEqual(false);
      expect(introspectionResponse.data.error).toEqual("invalid_token");
      expect(introspectionResponse.data.error_description).toEqual("mTLS client certificate thumbprint does not match the sender-constrained access token.");
      expect(introspectionResponse.data.status_code).toEqual(401);
    });
  });

  describe("403 error", () => {

    it("insufficient scope self_signed_tls_client_auth", async () => {
      const codeVerifier = generateCodeVerifier(64);
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

      console.log(decodedResponse);

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

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        scope: "management",
        clientId: selfSignedTlsAuthClient.clientId,
        clientCert: selfSignedTlsAuthClient.clientCertFile,
        clientCertFile: selfSignedTlsAuthClient.clientCertFile,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toEqual(false);
      expect(introspectionResponse.data.error).toEqual("insufficient_scope");
      expect(introspectionResponse.data.error_description).toEqual("Requested scopes are not granted. Requested: management, Granted: phone openid profile write email");
      expect(introspectionResponse.data.status_code).toEqual(403);
    });

  });

});
