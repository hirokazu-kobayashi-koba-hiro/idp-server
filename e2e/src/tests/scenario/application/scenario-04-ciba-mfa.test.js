import { describe, expect, it, xit } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction, getJwks,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient, federationServerConfig,
  serverConfig
} from "../../testConfig";
import { verifyAndDecodeJwt } from "../../../lib/jose";
import { get } from "../../../lib/http";
import { createFederatedUser, registerFido2 } from "../../../user";
import { generateValidAssertionFromChallenge } from "../../../lib/fido/fido2";

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
        scope: "openid profile phone email",
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: ciba.loginHint,
        acrValues: "urn:mace:incommon:iap:gold",
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

    let authenticationResponse;

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
    console.log(authenticationResponse.data);
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
    console.log(authenticationResponse.data);
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
    expect(tokenResponse.data.scope).toContain("phone");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);
    expect(decodedIdToken.payload).toHaveProperty("amr");

    const decodedAccessToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.access_token,
      jwks: jwksResponse.data
    });
    console.log(JSON.stringify(decodedAccessToken, null, 2));

  });

  it("fido2", async () => {
    const { user, accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient
    });

    console.log(user);
    const { authenticationDeviceId, userId } = await registerFido2({ accessToken });

    let backchannelAuthenticationResponse =
      await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email",
        bindingMessage: ciba.bindingMessage,
        loginHint: `sub:${user.sub}`,
        acrValues: "urn:mace:incommon:iap:gold",
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

    let authenticationResponse;

    const challengeRequest = {
      userVerification: "required",
      timeout: 6000
    };

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "fido2-authentication-challenge",
      body: challengeRequest
    });
    console.log(JSON.stringify(authenticationResponse.data, null, 2));
    expect(authenticationResponse.status).toBe(200);

    const authenticationRequest = generateValidAssertionFromChallenge(authenticationResponse.data, userId);
    console.log(JSON.stringify(authenticationRequest, null, 2));

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "fido2-authentication",
      body: authenticationRequest
    });
    console.log(JSON.stringify(authenticationResponse.data, null, 2));
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
    expect(tokenResponse.data.scope).toContain("phone");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);
    expect(decodedIdToken.payload).toHaveProperty("amr");

    const decodedAccessToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.access_token,
      jwks: jwksResponse.data
    });
    console.log(JSON.stringify(decodedAccessToken, null, 2));

  });

  it("fido-uaf cancel", async () => {
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
        scope: "openid profile phone email",
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: ciba.loginHint,
        acrValues: "urn:mace:incommon:iap:gold",
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

    let authenticationResponse;

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
      interactionType: "fido-uaf-cancel",
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
    expect(tokenResponse.status).toBe(400);

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
    console.log(authenticationResponse.data);
    expect(authenticationResponse.status).toBe(404);

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
      params: {
        "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
      },
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
    console.log(JSON.stringify(authenticationResponse.data, null, 2));
    expect(authenticationResponse.status).toBe(200);

    const transactionId = authenticationTransaction.id;

    const adminTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret
    });
    console.log(adminTokenResponse.data);
    expect(adminTokenResponse.status).toBe(200);
    const accessToken = adminTokenResponse.data.access_token;

    const interactionResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
      headers: {
        Authorization: `Bearer ${accessToken}`
      }
    });
    console.log(interactionResponse.data);
    const verificationCode = interactionResponse.data.payload.verification_code;

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "sms-authentication",
      body: {
        "verification_code": verificationCode,
      }
    });
    console.log(JSON.stringify(authenticationResponse.data, null, 2));
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
