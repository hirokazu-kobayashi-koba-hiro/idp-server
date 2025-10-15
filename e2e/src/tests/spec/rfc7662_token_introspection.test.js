import { describe, expect, it } from "@jest/globals";

import { getJwks, inspectToken, requestToken } from "../../api/oauthClient";
import {
  clientSecretBasicClient,
  clientSecretJwtClient,
  clientSecretPostClient,
  privateKeyJwtClient,
  serverConfig
} from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";
import { createBasicAuthHeader } from "../../lib/util";
import { createClientAssertion } from "../../lib/oauth";
import { verifyAndDecodeJwt } from "../../lib/jose";

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

});
