import {
  authorize,
  createAuthorizationRequest,
  deny,
  getAuthorizations,
  postAuthentication
} from "../api/oauthClient";
import { serverConfig } from "../tests/testConfig";
import { convertNextAction, convertToAuthorizationResponse, convertToSnake } from "../lib/util";
import puppeteer from "puppeteer-core";
import { createHash, X509Certificate } from "node:crypto";
import { encodeBuffer } from "../lib/bas64";
import { getClientCert } from "../api/cert/clientCert";
import { get } from "../lib/http";
import { generateFakeWebAuthnCredential } from "../lib/webauthn";

export const requestAuthorizationsForSignup = async ({
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
  action = "authorize",
  user,
  mfa,
}) => {
  if (serverConfig.enabledSsr) {
    const requestUrl = createAuthorizationRequest({
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
    const browser = await puppeteer.launch({ headless: false });
    const page = await browser.newPage();

    await page.goto(requestUrl);

    // Wait and click on first result
    try {

      const buttonSelector = action === "authorize" ? "#authorize-button" : "#deny-button";
      const button = await page.waitForSelector(buttonSelector, {
        timeout: 100
      });
      await button.click();
      console.log("click");
      await page.waitForNavigation({
        timeout: 1000,
      });
      const url = page.url();
      console.log(url);

      const authorizationResponse = convertToAuthorizationResponse(
        url,
      );
      return {
        status: 302,
        authorizationResponse,
      };
    } catch (e) {
      try {
        console.log("error");
        const errorElement = await page.waitForSelector("#error", {
          timeout: 100
        });
        console.log("errorDescription");
        const errorDescriptionElement = await page.waitForSelector("#errorDescription", {
          timeout: 100
        });
        console.log("jsonValue");
        const error = await (await errorElement.getProperty("textContent")).jsonValue();
        const errorDescription = await ((await errorDescriptionElement.getProperty("textContent")).jsonValue());
        return {
          status: 400,
          error: {
            error,
            error_description: errorDescription,
          }
        };
      } catch (e) {
        const url = page.url();
        console.log(url);
        const authorizationResponse = convertToAuthorizationResponse(
          url,
        );
        return {
          status: 302,
          authorizationResponse,
        };
      }
    } finally {
      console.log("finally");
      await browser.close();
    }
  } else {
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
      presentationDefinition,
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
        endpoint: serverConfig.authorizationIdEndpoint + "password-registration",
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
          endpoint: serverConfig.authorizationIdEndpoint + "email-verification-challenge",
          id,
          body: {
            email_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-verification",
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
          endpoint: serverConfig.authorizationIdEndpoint + "webauthn-registration-challenge",
          id,
          body: {
            email_template: "authentication"
          }
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        const credential = generateFakeWebAuthnCredential(challengeResponse.data.challenge);

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "webauthn-registration",
          id,
          body: credential
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
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
  }
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
