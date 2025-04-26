import { expect, } from "@jest/globals";
import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "../api/oauthClient";

export const loginForClientSecretPost = async ({ serverConfig, client, scope }) => {
  const cibaConfig = serverConfig.ciba;

  let backchannelAuthenticationResponse =
    await requestBackchannelAuthentications({
      endpoint: serverConfig.backchannelAuthenticationEndpoint,
      clientId: client.clientId,
      scope: "openid profile phone email" + client.scope + " " + scope,
      bindingMessage: cibaConfig.bindingMessage,
      userCode: cibaConfig.userCode,
      loginHint: cibaConfig.loginHint,
      clientSecret: client.clientSecret,
    });

  const authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
    endpoint: serverConfig.authenticationDeviceEndpoint,
    deviceId: cibaConfig.authenticationDeviceId,
    params: {},
  });


  const completeResponse = await postAuthenticationDeviceInteraction({
    endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
    flowType: authenticationTransactionResponse.data.authorization_flow,
    id: authenticationTransactionResponse.data.id,
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
    clientId: client.clientId,
    clientSecret: client.clientSecret,
  });
  console.log(tokenResponse.data);

  return tokenResponse.data;
};
