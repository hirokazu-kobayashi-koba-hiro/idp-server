import { get, post } from "../lib/http";
import { convertToSnake } from "../lib/util";

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
}) => {
  let params = {};
  if (scope) {
    params = {
      ...params,
      scope,
    };
  }
  if (responseType) {
    params = {
      ...params,
      responseType,
    };
  }
  if (clientId) {
    params = {
      ...params,
      clientId,
    };
  }
  if (redirectUri) {
    params = {
      ...params,
      redirectUri,
    };
  }
  if (state) {
    params = {
      ...params,
      state,
    };
  }
  if (responseMode) {
    params = {
      ...params,
      responseMode,
    };
  }
  if (nonce) {
    params = {
      ...params,
      nonce,
    };
  }
  if (display) {
    params = {
      ...params,
      display,
    };
  }
  if (prompt) {
    params = {
      ...params,
      prompt,
    };
  }
  if (maxAge) {
    params = {
      ...params,
      maxAge,
    };
  }
  if (uiLocales) {
    params = {
      ...params,
      uiLocales,
    };
  }
  if (idTokenHint) {
    params = {
      ...params,
      idTokenHint,
    };
  }
  if (loginHint) {
    params = {
      ...params,
      loginHint,
    };
  }
  if (acrValues) {
    params = {
      ...params,
      acrValues,
    };
  }
  if (claims) {
    params = {
      ...params,
      claims,
    };
  }
  if (request) {
    params = {
      ...params,
      request,
    };
  }
  if (requestUri) {
    params = {
      ...params,
      requestUri,
    };
  }
  const query = new URLSearchParams(convertToSnake(params)).toString();
  const url = `${endpoint}?${query}`;
  console.log(url);
  return await get({
    url: url,
    headers: {},
  });
};

export const authorize = async ({ endpoint, id }) => {
  const url = endpoint.replace("{id}", id);
  return await post({
    url,
  });
};

export const requestToken = async ({
  endpoint,
  code,
  grantType,
  redirectUri,
  refreshToken,
  clientId,
  clientSecret,
  clientAssertion,
  clientAssertionType,
  basicAuth,
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
