import { describe, expect, it, xit } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "./api/oauthClient";
import {
  clientSecretPostClient,
  privateKeyJwtClient,
  serverConfig,
} from "./testConfig";
import { createJwt, createJwtWithPrivateKey, generateJti } from "./lib/jose";
import { isNumber, toEpocTime } from "./lib/util";
import { get } from "./lib/http";

describe("ciba - mfa", () => {
  const ciba = serverConfig.ciba;

  it("fido-uaf", async () => {
    const fidoUafFacetsResponse = await get({
      url: serverConfig.fidoUafFacetsEndpoint,
      headers: {
        "Content-Type": "application/json",
      }
    });
    console.log(fidoUafFacetsResponse.data);
    expect(fidoUafFacetsResponse.status).toBe(200);

    let backchannelAuthenticationResponse =
      await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: ciba.loginHint,
        clientSecret: clientSecretPostClient.clientSecret,
      });
    console.log(backchannelAuthenticationResponse.data);
    expect(backchannelAuthenticationResponse.status).toBe(200);

    let authenticationTransactionResponse;
    authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: serverConfig.ciba.authenticationDeviceId,
      params: {},
    });
    console.log(authenticationTransactionResponse);

    authenticationTransactionResponse = await get({
      url: serverConfig.authenticationEndpoint + `?attributes.auth_req_id=${backchannelAuthenticationResponse.data.auth_req_id}`,
    });

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

    let authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "password-authentication",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      }
    });
    expect(authenticationResponse.status).toBe(200);

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "fido-uaf-registration-challenge",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      }
    });
    expect(authenticationResponse.status).toBe(200);

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "password-authentication",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      }
    });
    expect(authenticationResponse.status).toBe(200);

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "fido-uaf-authentication-challenge",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      }
    });
    expect(authenticationResponse.status).toBe(200);

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "fido-uaf-authentication",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      }
    });
    expect(authenticationResponse.status).toBe(200);

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: backchannelAuthenticationResponse.data.auth_req_id,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);

  });

  it("sms authentication", async () => {

    let backchannelAuthenticationResponse =
      await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: ciba.loginHint,
        clientSecret: clientSecretPostClient.clientSecret,
      });
    console.log(backchannelAuthenticationResponse.data);
    expect(backchannelAuthenticationResponse.status).toBe(200);

    let authenticationTransactionResponse;
    authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: serverConfig.ciba.authenticationDeviceId,
      params: {},
    });
    console.log(authenticationTransactionResponse);

    authenticationTransactionResponse = await get({
      url: serverConfig.authenticationEndpoint + `?attributes.auth_req_id=${backchannelAuthenticationResponse.data.auth_req_id}`,
    });

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

    let authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "sms-authentication-challenge",
      body: {
        phone_number: "09012345678",
        provider_id: "idp-server"
      }
    });
    expect(authenticationResponse.status).toBe(200);

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "sms-authentication",
      body: {
        "verification_code": "123456",
      }
    });
    expect(authenticationResponse.status).toBe(200);

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "password-authentication",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      }
    });
    console.log(authenticationResponse.data);
    console.log(authenticationResponse.status);


    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: backchannelAuthenticationResponse.data.auth_req_id,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);

  });

});
