import {
  authorize, createParams,
  deny,
  getAuthorizations,
  postAuthentication
} from "../api/oauthClient";
import { serverConfig } from "../testConfig";
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
  user = {
    "username": "ito.ichiro@gmail.com",
    "password": "successUserCode",
  },
  mfa,
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

      const passwordResponse = await postAuthentication({
        endpoint: serverConfig.authenticationEndpoint + "password-authentication",
        id,
        body: {
          ...user
        }
      });

      if (passwordResponse.status >= 400) {
        console.error(passwordResponse.data);
      }

      if (mfa === "email") {
        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authenticationEndpoint + "email-verification-challenge",
          id,
          body: {
            email: "test@gmail.com",
            email_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authenticationEndpoint + "email-verification",
          id,
          body: {
            verification_code: "123",
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
      }

      if (mfa === "webauthn") {
        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authenticationEndpoint + "webauthn-authentication-challenge",
          id,
          body: {
            email_template: "authentication"
          }
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authenticationEndpoint + "webauthn-authentication",
          id,
          body: {
            verification_code: "123",
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
      }

      if (mfa === "legacy-authentication") {
        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authenticationEndpoint + "legacy-authentication",
          id,
          body: {
            username: "dummy",
            password: "password",
          }
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);
      }

      const authorizeResponse = await authorize({
        endpoint: serverConfig.authorizeEndpoint,
        id,
        body: {
          action: "signin"
        }
      });

      console.log(authorizeResponse.headers);
      console.log(authorizeResponse.data);
      const authorizationResponse = convertToAuthorizationResponse(
        authorizeResponse.data.redirect_uri
      );
      return {
        status: authorizeResponse.status,
        authorizationResponse,
      };
    } else {
      const denyResponse = await deny({
        endpoint: serverConfig.denyEndpoint,
        id,
      });
      console.log(denyResponse.data);
      const authorizationResponse = convertToAuthorizationResponse(
        denyResponse.data.redirect_uri
      );
      return {
        status: denyResponse.status,
        authorizationResponse,
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

export const requestLogout = async ({ endpoint, clientId}) => {

  const params = new URLSearchParams(convertToSnake({
    clientId
  }));

  return await get({
    url: endpoint + "?" +params.toString(),
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
