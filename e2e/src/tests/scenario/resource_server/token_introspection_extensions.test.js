import { describe, expect, it } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  getJwks,
  inspectTokenWithVerification, postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "../../../api/oauthClient";
import {
  clientSecretBasicClient,
  clientSecretJwtClient,
  clientSecretPostClient, federationServerConfig,
  privateKeyJwtClient, publicClient, selfSignedTlsAuthClient,
  serverConfig
} from "../../testConfig";
import { certThumbprint, requestAuthorizations } from "../../../oauth/request";
import { createBasicAuthHeader, sleep, toEpocTime } from "../../../lib/util";
import { calculateCodeChallengeWithS256, createClientAssertion, generateCodeVerifier } from "../../../lib/oauth";
import { createJwtWithPrivateKey, generateJti, verifyAndDecodeJwt } from "../../../lib/jose";
import { createFederatedUser, registerFidoUaf } from "../../../user";

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

  describe("standard custom claims", () => {

    it("ex_sub", async () => {
      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient,
        scope: "openid claims:ex_sub"
      });

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: accessToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.scope).toContain("claims:ex_sub");
      expect(introspectionResponse.data.sub).toEqual(user.sub);
      expect(introspectionResponse.data).toHaveProperty("ex_sub");
    });

    it("authentication_devices", async () => {
      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient,
        scope: "openid claims:ex_sub claims:authentication_devices"
      });

      const { authenticationDeviceId } = await registerFidoUaf({ accessToken });
      console.log(authenticationDeviceId);

      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email claims:ex_sub claims:authentication_devices",
          bindingMessage: "999",
          loginHint: `device:${authenticationDeviceId},idp:${federationServerConfig.providerName}`,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "authentication-device-binding-message",
        body: {
          binding_message: "999",
        }
      });
      expect(completeResponse.status).toBe(200);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.scope).toContain("claims:authentication_devices");
      expect(introspectionResponse.data.sub).toEqual(user.sub);
      expect(introspectionResponse.data).toHaveProperty("authentication_devices");
      expect(introspectionResponse.data.authentication_devices[0].id).toEqual(authenticationDeviceId);
    });

    it("roles", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email claims:ex_sub claims:authentication_devices claims:roles",
          bindingMessage: serverConfig.ciba.bindingMessage,
          userCode: serverConfig.ciba.userCode,
          loginHint: serverConfig.ciba.loginHintDevice,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

      const failureResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: "serverConfig.ciba.userCode",
        }
      });
      console.log(failureResponse.data);
      console.log(failureResponse.status);

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(completeResponse.status).toBe(200);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.scope).toContain("claims:roles");
      expect(introspectionResponse.data).toHaveProperty("roles");
    });

    it("permissions", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email claims:ex_sub claims:authentication_devices claims:roles claims:permissions",
          bindingMessage: serverConfig.ciba.bindingMessage,
          userCode: serverConfig.ciba.userCode,
          loginHint: serverConfig.ciba.loginHintDevice,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

      const failureResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: "serverConfig.ciba.userCode",
        }
      });
      console.log(failureResponse.data);
      console.log(failureResponse.status);

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(completeResponse.status).toBe(200);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(JSON.stringify(introspectionResponse.data, null, 2));
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.scope).toContain("claims:permissions");
      expect(introspectionResponse.data).toHaveProperty("permissions");
    });

    it("assigned_tenants", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email claims:ex_sub claims:authentication_devices claims:roles claims:permissions claims:assigned_tenants",
          bindingMessage: serverConfig.ciba.bindingMessage,
          userCode: serverConfig.ciba.userCode,
          loginHint: serverConfig.ciba.loginHintDevice,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

      const failureResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: "serverConfig.ciba.userCode",
        }
      });
      console.log(failureResponse.data);
      console.log(failureResponse.status);

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(completeResponse.status).toBe(200);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(JSON.stringify(introspectionResponse.data, null, 2));
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.scope).toContain("claims:assigned_tenants");
      expect(introspectionResponse.data).toHaveProperty("assigned_tenants");
    });

    it("assigned_organizations", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email claims:ex_sub claims:authentication_devices claims:roles claims:permissions claims:assigned_tenants claims:assigned_organizations",
          bindingMessage: serverConfig.ciba.bindingMessage,
          userCode: serverConfig.ciba.userCode,
          loginHint: serverConfig.ciba.loginHintDevice,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

      const failureResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: "serverConfig.ciba.userCode",
        }
      });
      console.log(failureResponse.data);
      console.log(failureResponse.status);

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        }
      });
      expect(completeResponse.status).toBe(200);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(JSON.stringify(introspectionResponse.data, null, 2));
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.scope).toContain("claims:assigned_organizations");
      expect(introspectionResponse.data).toHaveProperty("assigned_organizations");
    });

  });

  describe("401 error", () => {

    it("invalid_token. access token is not found.", async () => {

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: "tokenResponse.data.access_token",
        clientId: selfSignedTlsAuthClient.clientId,
        clientCertFile: selfSignedTlsAuthClient.clientCertFile,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toEqual(false);
      expect(introspectionResponse.data.error).toEqual("invalid_token");
      expect(introspectionResponse.data.error_description).toEqual("Token not found or invalid.");
      expect(introspectionResponse.data.status_code).toEqual(401);
    });

    it("invalid_token. access token is expired", async () => {
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

      await sleep(1000);

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: selfSignedTlsAuthClient.clientId,
        clientCert: selfSignedTlsAuthClient.clientCertFile,
        clientCertFile: selfSignedTlsAuthClient.clientCertFile,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.status_code).toBe(401);
      expect(introspectionResponse.data.error).toEqual("invalid_token");
      expect(introspectionResponse.data.error_description).toEqual("Access token has expired, but the refresh token is still valid.");

    });

    it("invalid_token. Both access token and refresh token are expired", async () => {
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

      await sleep(2000);

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: selfSignedTlsAuthClient.clientId,
        clientCert: selfSignedTlsAuthClient.clientCertFile,
        clientCertFile: selfSignedTlsAuthClient.clientCertFile,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.status_code).toBe(401);
      expect(introspectionResponse.data.error).toEqual("invalid_token");
      expect(introspectionResponse.data.error_description).toEqual("Token has expired (access and refresh tokens).");

    });

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

const getAccessTokenForPrivateKeyJwt = async ({ scope }) => {
  const { authorizationResponse } = await requestAuthorizations({
    endpoint: serverConfig.authorizationEndpoint,
    responseType: "code",
    state: "aiueo",
    scope: scope,
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
  return tokenResponse.data.access_token;
};
