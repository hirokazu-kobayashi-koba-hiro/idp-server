import {
  authenticateWithPassword,
  authorize,
  createAuthorizationRequest,
  deny,
  getAuthorizations
} from "../api/oauthClient";
import { serverConfig } from "../testConfig";
import { convertToAuthorizationResponse } from "../lib/util";
import puppeteer from "puppeteer-core";
import { createHash, X509Certificate } from "node:crypto";
import { encodeBuffer } from "../lib/bas64";
import { getClientCert } from "../api/cert/clientCert";

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
  presentationDefinition,
  customParams,
  action = "authorize",
  user = {
    "username": "ito.ichiro@gmail.com",
    "password": "successUserCode",
  },
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
    console.log(response.data);
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

    if (action === "authorize") {
      const passwordResponse = await authenticateWithPassword({
        endpoint: serverConfig.passwordAuthenticationEndpoint,
        id: response.data.id,
        body: {
          ...user
        }
      });

      if (passwordResponse.status >= 400) {
        console.error(passwordResponse.data);
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
    } else {
      const denyResponse = await deny({
        endpoint: serverConfig.denyEndpoint,
        id: response.data.id,
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
