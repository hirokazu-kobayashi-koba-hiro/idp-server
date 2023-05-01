import { authorize, getAuthorizations } from "../api/oauthClient";
import { serverConfig } from "../testConfig";
import { convertToAuthorizationResponse } from "../lib/util";

export const requestAuthorizations = async ({
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
  enabledSsr = false,
}) => {
  const response = await getAuthorizations({
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
  });
  console.log(response.data);
  if (enabledSsr) {
    return {};
  } else {
    if (response.status === 302) {
      console.debug("redirect");
      console.log(response.headers);
      const { location } = response.headers;
      const authorizationResponse = convertToAuthorizationResponse(location);
      return {
        status: response.status,
        authorizationResponse,
      };
    }

    if (response.status !== 200) {
      return {
        status: response.status,
        error: response.data,
      };
    }

    const authorizeResponse = await authorize({
      endpoint: serverConfig.authorizeEndpoint,
      id: response.data.id,
    });
    console.log(authorizeResponse.data);
    const authorizationResponse = convertToAuthorizationResponse(
      authorizeResponse.data.redirect_uri
    );
    return {
      status: authorizeResponse.status,
      authorizationResponse,
    };
  }
};
