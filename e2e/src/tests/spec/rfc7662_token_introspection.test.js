import { describe, expect, it } from "@jest/globals";

import { getJwks, inspectToken, requestToken } from "../../api/oauthClient";
import {
  clientSecretBasicClient,
  clientSecretJwtClient,
  clientSecretPostClient,
  privateKeyJwtClient,
  publicClient,
  serverConfig
} from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";
import { createBasicAuthHeader } from "../../lib/util";
import { createClientAssertion } from "../../lib/oauth";
import { verifyAndDecodeJwt } from "../../lib/jose";
import { postWithJson } from "../../lib/http";

describe("OAuth 2.0 Token Introspection", () => {
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

      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(JSON.stringify(introspectionResponse.data, null, 2));
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);

      // RFC 9068: Verify authentication information claims in JWT access token
      expect(introspectionResponse.data).toHaveProperty("auth_time");
      expect(typeof introspectionResponse.data.auth_time).toBe("number");
      expect(introspectionResponse.data.auth_time).toBeGreaterThan(0);

      // AMR (Authentication Methods References) should be an array
      if (introspectionResponse.data.amr) {
        expect(Array.isArray(introspectionResponse.data.amr)).toBe(true);
      }

      // ACR (Authentication Context Class Reference) should be a string
      if (introspectionResponse.data.acr) {
        expect(typeof introspectionResponse.data.acr).toBe("string");
      }
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

      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
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

      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
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

      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
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
  });

  describe("2.1. Introspection Request", () => {
    it("The client makes a request to the introspection endpoint by sending the following parameters using the \"application/x-www-form-urlencoded\" format - RFC 7662 Section 2.1", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);

      // RFC 7662 Section 2.1: Content-Type MUST be application/x-www-form-urlencoded
      // Sending application/json should return HTTP 400 Bad Request
      const basicAuth = Buffer.from(
        `${clientSecretPostClient.clientId}:${clientSecretPostClient.clientSecret}`
      ).toString("base64");

      const response = await postWithJson({
        url: serverConfig.tokenIntrospectionEndpoint,
        headers: {
          "Content-Type": "application/json",
          Authorization: `Basic ${basicAuth}`,
        },
        body: {
          token: tokenResponse.data.access_token,
        },
      });

      console.log(response.data);
      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
      expect(response.data.error_description).toBe(
        "Bad request. Content-Type header does not match supported values"
      );
    });

    it("To prevent token scanning attacks, the endpoint MUST require authorization — a public client (token_endpoint_auth_method=none) is rejected with invalid_client - RFC 7662 Section 2.1 (#1707)", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);

      // A public client (no secret) is rejected at client authentication, before any token lookup.
      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: publicClient.clientId,
      });
      console.log(JSON.stringify(introspectionResponse.data, null, 2));
      expect(introspectionResponse.status).toBe(400);
      expect(introspectionResponse.data).toHaveProperty("error", "invalid_client");
    });

    it("does not fall back to a refresh-token lookup — a refresh token introspects as active:false, avoiding token type confusion (#1707)", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.refresh_token).toBeDefined();

      // The refresh token value must NOT be returned as an active access token.
      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.refresh_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(JSON.stringify(introspectionResponse.data, null, 2));
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(false);
    });
  });

  describe("2.2. Introspection Response", () => {
    const introspectActiveToken = async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      return introspectionResponse;
    };

    it("token_type OPTIONAL. Type of the token as defined in Section 5.1 of OAuth 2.0 [RFC6749] - RFC 7662 Section 2.2 (#1707)", async () => {
      const introspectionResponse = await introspectActiveToken();
      expect(introspectionResponse.data.token_type).toBe("Bearer");
    });

    it("username OPTIONAL. Human-readable identifier for the resource owner who authorized this token - RFC 7662 Section 2.2 (#1707)", async () => {
      const introspectionResponse = await introspectActiveToken();
      // The resource owner (ito.ichiro) has a preferred_username, so username is asserted
      // unconditionally: a regression that drops username must fail this test.
      expect(introspectionResponse.data).toHaveProperty("username");
      expect(typeof introspectionResponse.data.username).toBe("string");
      expect(introspectionResponse.data.username.length).toBeGreaterThan(0);
    });
  });

});
