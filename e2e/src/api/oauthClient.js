import { get, post, postWithJson } from ".../../lib/http";
import { convertToSnake } from ".../../lib/util";
import { encodedClientCert } from "./cert/clientCert";

export const createParams = ({
  scope,
  responseType,
  clientId,
  redirectUri,
  state,
  responseMode,
  nonce,
  display,
  prompt,
  maxAge,
  uiLocales,
  idTokenHint,
  loginHint,
  acrValues,
  claims,
  request,
  requestUri,
  codeChallenge,
  codeChallengeMethod,
  authorizationDetails,
  customParams,
  clientSecret,
  clientAssertion,
  clientAssertionType,
}) => {
  const baseParams = {
    scope,
    responseType,
    clientId,
    redirectUri,
    state,
    responseMode,
    nonce,
    display,
    prompt,
    maxAge,
    uiLocales,
    idTokenHint,
    loginHint,
    acrValues,
    claims,
    request,
    requestUri,
    codeChallenge,
    codeChallengeMethod,
    authorizationDetails,
    customParams,
    clientSecret,
    clientAssertion,
    clientAssertionType,
  };

  const filtered = Object.fromEntries(
    Object.entries(baseParams).filter(([, v]) => v !== undefined && v !== null)
  );

  return new URLSearchParams(convertToSnake(filtered)).toString();
};

export const createAuthorizationRequest = ({
 endpoint,
 scope,
 responseType,
 clientId,
 redirectUri,
 state,
 responseMode,
 nonce,
 display,
 prompt,
 maxAge,
 uiLocales,
 idTokenHint,
 loginHint,
 acrValues,
 claims,
 request,
 requestUri,
 codeChallenge,
 codeChallengeMethod,
 authorizationDetails,
 presentationDefinition,
 customParams,
}) => {
  const params = createParams({
    scope,
    responseType,
    clientId,
    redirectUri,
    state,
    responseMode,
    nonce,
    display,
    prompt,
    maxAge,
    uiLocales,
    idTokenHint,
    loginHint,
    acrValues,
    claims,
    request,
    requestUri,
    codeChallenge,
    codeChallengeMethod,
    authorizationDetails,
    presentationDefinition,
    customParams,
  });
  const url = `${endpoint}?${params}`;
  console.log(url);
  return url;
};

export const getAuthorizations = async ({
  endpoint,
  scope,
  responseType,
  clientId,
  redirectUri,
  state,
  responseMode,
  nonce,
  display,
  prompt,
  maxAge,
  uiLocales,
  idTokenHint,
  loginHint,
  acrValues,
  claims,
  request,
  requestUri,
  codeChallenge,
  codeChallengeMethod,
  authorizationDetails,
  presentationDefinition,
  customParams,
}) => {
  const url = createAuthorizationRequest({
    endpoint,
    scope,
    responseType,
    clientId,
    redirectUri,
    state,
    responseMode,
    nonce,
    display,
    prompt,
    maxAge,
    uiLocales,
    idTokenHint,
    loginHint,
    acrValues,
    claims,
    request,
    requestUri,
    codeChallenge,
    codeChallengeMethod,
    authorizationDetails,
    presentationDefinition,
    customParams,
  });
  return await get({
    url: url,
    headers: {},
  });
};

export const authenticateWithPassword = async ({ endpoint, id, body }) => {
  const url = endpoint.replace("{id}", id);
  return await postWithJson({
    url,
    body,
  });
};

export const postAuthentication = async ({ endpoint, id, body }) => {
  const url = endpoint.replace("{id}", id);
  return await postWithJson({
    url,
    body,
  });
};

export const authorize = async ({ endpoint, id, body }) => {
  const url = endpoint.replace("{id}", id);
  return await postWithJson({
    url,
    body,
  });
};

export const deny = async ({ endpoint, id }) => {
  const url = endpoint.replace("{id}", id);
  return await postWithJson({
    url,
  });
};

export const requestToken = async ({
  endpoint,
  code,
  grantType,
  redirectUri,
  refreshToken,
  codeVerifier,
  scope,
  username,
  password,
  authReqId,
  clientId,
  clientSecret,
  clientAssertion,
  clientAssertionType,
  basicAuth,
  clientCertFile,
  clientCertKeyFile,
}) => {
  let params = new URLSearchParams();
  if (code) {
    params.append("code", code);
  }
  if (grantType) {
    params.append("grant_type", grantType);
  }
  if (redirectUri) {
    params.append("redirect_uri", redirectUri);
  }
  if (refreshToken) {
    params.append("refresh_token", refreshToken);
  }
  if (scope) {
    params.append("scope", scope);
  }
  if (username) {
    params.append("username", username);
  }
  if (password) {
    params.append("password", password);
  }
  if (codeVerifier) {
    params.append("code_verifier", codeVerifier);
  }
  if (authReqId) {
    params.append("auth_req_id", authReqId);
  }
  if (clientId) {
    params.append("client_id", clientId);
  }
  if (clientSecret) {
    params.append("client_secret", clientSecret);
  }
  if (clientAssertion) {
    params.append("client_assertion", clientAssertion);
  }
  if (clientAssertionType) {
    params.append("client_assertion_type", clientAssertionType);
  }
  console.log(params.toString());
  let headers = basicAuth ? basicAuth : {};
  if (clientCertFile) {
    const clientCert = encodedClientCert(clientCertFile);
    headers = {
      ...headers,
      "x-ssl-cert": clientCert,
    };
  }
  console.log(headers);
  return await post({
    url: endpoint,
    body: params,
    headers,
  });
};

export const inspectToken = async ({ endpoint, token, tokenHintType }) => {
  let params = new URLSearchParams();
  if (params) {
    params.append("token", token);
  }
  if (tokenHintType) {
    params.append("token_hint_type", tokenHintType);
  }
  return await post({
    url: endpoint,
    body: params,
  });
};

export const revokeToken = async ({
  endpoint,
  token,
  tokenHintType,
  clientId,
  clientSecret,
  clientAssertion,
  clientAssertionType,
  basicAuth,
}) => {
  let params = new URLSearchParams();
  if (params) {
    params.append("token", token);
  }
  if (tokenHintType) {
    params.append("token_hint_type", tokenHintType);
  }
  if (clientId) {
    params.append("client_id", clientId);
  }
  if (clientSecret) {
    params.append("client_secret", clientSecret);
  }
  if (clientAssertion) {
    params.append("client_assertion", clientAssertion);
  }
  if (clientAssertionType) {
    params.append("client_assertion_type", clientAssertionType);
  }
  const headers = basicAuth ? basicAuth : {};
  return await post({
    url: endpoint,
    body: params,
    headers,
  });
};

export const getUserinfo = async ({ endpoint, authorizationHeader }) => {
  return await get({
    url: endpoint,
    headers: authorizationHeader,
  });
};

export const postUserinfo = async ({ endpoint, authorizationHeader }) => {
  return await post({
    url: endpoint,
    headers: authorizationHeader,
  });
};

export const getConfiguration = async ({ endpoint }) => {
  return await get({
    url: endpoint,
  });
};

export const getJwks = async ({ endpoint }) => {
  return await get({
    url: endpoint,
  });
};

export const requestBackchannelAuthentications = async ({
  endpoint,
  scope,
  clientNotificationToken,
  acrValues,
  loginHintToken,
  bindingMessage,
  userCode,
  idTokenHint,
  loginHint,
  requestedExpiry,
  authorizationDetails,
  request,
  clientId,
  clientSecret,
  clientAssertion,
  clientAssertionType,
  basicAuth,
}) => {
  let params = new URLSearchParams();
  if (scope) {
    params.append("scope", scope);
  }
  if (clientNotificationToken) {
    params.append("client_notification_token", clientNotificationToken);
  }
  if (acrValues) {
    params.append("acr_values", acrValues);
  }
  if (loginHintToken) {
    params.append("login_hint_token", loginHintToken);
  }
  if (bindingMessage) {
    params.append("binding_message", bindingMessage);
  }
  if (userCode) {
    params.append("user_code", userCode);
  }
  if (idTokenHint) {
    params.append("id_token_hint", idTokenHint);
  }
  if (loginHint) {
    params.append("login_hint", loginHint);
  }
  if (requestedExpiry) {
    params.append("requested_expiry", requestedExpiry);
  }
  if (authorizationDetails) {
    params.append("authorization_details", authorizationDetails);
  }
  if (request) {
    params.append("request", request);
  }
  if (clientId) {
    params.append("client_id", clientId);
  }
  if (clientSecret) {
    params.append("client_secret", clientSecret);
  }
  if (clientAssertion) {
    params.append("client_assertion", clientAssertion);
  }
  if (clientAssertionType) {
    params.append("client_assertion_type", clientAssertionType);
  }
  console.log(params.toString());
  const headers = basicAuth ? basicAuth : {};
  return await post({
    url: endpoint,
    body: params,
    headers,
  });
};

export const postAuthenticationDeviceInteraction = async ({
 endpoint,
 flowType,
 id,
 interactionType,
 body,
}) => {

  const url = endpoint.replace("{flow-type}", flowType).replace("{id}", id) + interactionType;
  return await postWithJson({
    url: url,
    body,
  });
};

export const completeBackchannelAuthentications = async ({
  endpoint,
  authReqId,
  action,
}) => {
  return await post({
    url: `${endpoint}?auth_req_id=${authReqId}&action=${action}`,
  });
};

export const getAuthenticationDeviceAuthenticationTransaction = async ({
  endpoint,
  deviceId,
  params,
}) => {
  const url = endpoint.replace("{id}", deviceId);
  return await get({
    url: url,
    params,
  });
};

export const requestCredentials = async ({
  endpoint,
  params,
  authorizationHeader,
}) => {

  return await postWithJson({
    url: endpoint,
    body: params,
    headers: authorizationHeader,
  });
};

export const requestBatchCredentials = async ({
 endpoint,
 params,
 authorizationHeader,
}) => {
  return await postWithJson({
    url: endpoint,
    body: params,
    headers: authorizationHeader,
  });
};
