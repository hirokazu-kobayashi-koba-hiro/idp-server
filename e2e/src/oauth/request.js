import {
  authorize, createParams,
  deny,
  getAuthorizations,
  postAuthentication
} from "../api/oauthClient";
import { serverConfig } from "../tests/testConfig";
import { convertNextAction, convertToAuthorizationResponse, convertToSnake } from "../lib/util";
import { createHash, X509Certificate } from "node:crypto";
import { encodeBuffer } from "../lib/bas64";
import { getClientCert } from "../api/cert/clientCert";
import { get, post } from "../lib/http";

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
  authorizationDetails,
  customParams,
  action = "authorize",
  authorizeEndpoint,
  denyEndpoint,
  user = {
    "username": "ito.ichiro@gmail.com",
    "password": "successUserCode001",
  },
  interaction = async (id, user) => {
    const passwordResponse = await postAuthentication({
      endpoint: serverConfig.authorizationIdEndpoint + "password-authentication",
      id,
      body: {
        ...user
      }
    });

    if (passwordResponse.status >= 400) {
      console.error(passwordResponse.data);
    }
  }
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
      authorizationDetails,
      customParams,
    });

    console.log(response.headers);
    console.log(response.data);
    const { location } = response.headers;
    const { nextAction, params } = convertNextAction(location);

    if (nextAction !== "goAuthentication") {
      console.debug("redirect");

      const authorizationResponse = convertToAuthorizationResponse(location);
      return {
        status: response.status,
        authorizationResponse,
        error: {
          error: authorizationResponse.error,
          error_description: authorizationResponse.errorDescription,
        }
      };
    }

    if (response.status !== 302) {
      return {
        status: response.status,
        error: response.data,
      };
    }

    const id = params.get("id");

    if (action === "authorize") {

      await interaction(id, user);

      const authorizeResponse = await authorize({
        endpoint: authorizeEndpoint || serverConfig.authorizeEndpoint,
        id,
        body: {}
      });

      // console.log(authorizeResponse.headers);
      console.log(JSON.stringify(authorizeResponse.data, null, 2));

      if (authorizeResponse.status !== 200) {

        return {
          status: authorizeResponse.status,
          authorizationResponse: {
            error: authorizeResponse.data.error,
            error_description: authorizeResponse.data.error_description
          },
          response: authorizeResponse,
        };
      }

      const authorizationResponse = convertToAuthorizationResponse(
        authorizeResponse.data.redirect_uri
      );
      return {
        status: authorizeResponse.status,
        authorizationResponse,
        response: authorizeResponse,
      };
    } else {

      await interaction(id, user);

      const denyResponse = await deny({
        endpoint: denyEndpoint || serverConfig.denyEndpoint,
        id,
      });
      console.log(denyResponse.data);
      const authorizationResponse = convertToAuthorizationResponse(
        denyResponse.data.redirect_uri
      );
      return {
        status: denyResponse.status,
        authorizationResponse,
        response: denyResponse,
      };
    }
};

export const pushAuthorizations = async ({
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
   customParams,
   clientSecret,
   clientAssertion,
   clientAssertionType,
  }) => {
  const params = createParams({
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
    customParams,
    clientSecret,
    clientAssertion,
    clientAssertionType,
  });
  console.log(params);

  return await post({
    url: endpoint,
    headers: {},
    body: params,
  });
};

/**
 * Request RP-Initiated Logout
 *
 * @param {string} endpoint - Logout endpoint URL
 * @param {string} [clientId] - Client ID (optional)
 * @param {string} [idTokenHint] - ID Token hint (optional, recommended)
 * @param {string} [logoutHint] - Logout hint (optional)
 * @param {string} [postLogoutRedirectUri] - Post-logout redirect URI (optional)
 * @param {string} [state] - State parameter (optional)
 * @param {string} [uiLocales] - UI locales (optional)
 * @see https://openid.net/specs/openid-connect-rpinitiated-1_0.html
 */
export const requestLogout = async ({
  endpoint,
  clientId,
  idTokenHint,
  logoutHint,
  postLogoutRedirectUri,
  state,
  uiLocales,
}) => {
  const paramsObj = {};
  if (clientId) paramsObj.clientId = clientId;
  if (idTokenHint) paramsObj.idTokenHint = idTokenHint;
  if (logoutHint) paramsObj.logoutHint = logoutHint;
  if (postLogoutRedirectUri) paramsObj.postLogoutRedirectUri = postLogoutRedirectUri;
  if (state) paramsObj.state = state;
  if (uiLocales) paramsObj.uiLocales = uiLocales;

  const params = new URLSearchParams(convertToSnake(paramsObj));

  return await get({
    url: endpoint + "?" + params.toString(),
    headers: {},
  });
};

export const certThumbprint = (clientCertFile) =>{
  const cert = getClientCert(clientCertFile);
  let digest;
  if (cert instanceof X509Certificate) {
    digest = createHash("sha256").update(cert.raw).digest();
  } else {
    digest = createHash("sha256")
      .update(
        Buffer.from(
          cert.replace(/(?:-----(?:BEGIN|END) CERTIFICATE-----|\s|=)/g, ""),
          "base64",
        ),
      )
      .digest();
  }
  const thumbprint = encodeBuffer(digest);
  console.log(thumbprint);
  return thumbprint;
};
