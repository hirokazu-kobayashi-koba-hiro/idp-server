import { describe, expect, it } from "@jest/globals";

import { getJwks, requestToken } from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import { pushAuthorizations, requestAuthorizations } from "../../oauth/request";
import {
  createJwtWithPrivateKey,
  generateJti,
  verifyAndDecodeJwt,
} from "../../lib/jose";
import { toEpocTime } from "../../lib/util";
import { generateCodeVerifier } from "../../lib/oauth";
import { postWithJson } from "../../lib/http";

describe("rfc9126 OAuth 2.0 Pushed Authorization Requests", () => {
  it("success pattern normal", async () => {
    const codeVerifier = generateCodeVerifier(64);
    const codeChallenge = codeVerifier;
    const authorizationDetails = [{
      "type": "account_information",
      "actions": [
        "list_accounts",
        "read_balances",
        "read_transactions"
      ],
      "locations": [
        "https://example.com/accounts"
      ]
    },
      {
        "type": "payment_initiation",
        "actions": [
          "initiate",
          "status",
          "cancel"
        ],
        "locations": [
          "https://example.com/payments"
        ],
        "instructedAmount": {
          "currency": "EUR",
          "amount": "123.50"
        },
        "creditorName": "Merchant A",
        "creditorAccount": {
          "iban": "DE02100100109307118603"
        },
        "remittanceInformationUnstructured": "Ref Number Merchant"
      }];
    const parResponse = await pushAuthorizations({
      endpoint: serverConfig.authorizationEndpoint + "/push",
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      aud: serverConfig.issuer,
      iss: clientSecretPostClient.clientId,
      clientId: clientSecretPostClient.clientId,
      responseMode: "query",
      nonce: "nonce",
      display: "page",
      prompt: "login",
      codeChallenge,
      codeChallengeMethod: "plain",
      authorizationDetails: JSON.stringify(authorizationDetails),
      clientSecret: clientSecretPostClient.clientSecret,
    });

    console.log(parResponse.data);
    expect(parResponse.status).toBe(201);

    const { request_uri } = parResponse.data;

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      requestUri: request_uri
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      codeVerifier,
    });
    console.log(JSON.stringify(tokenResponse.data));
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);

    const { authorizationResponse: reusedResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      requestUri: request_uri
    });

    console.log(reusedResponse);
    expect(reusedResponse.code).toBeNull();

  });

  it("success pattern request object", async () => {
    const codeVerifier = generateCodeVerifier(64);
    const codeChallenge = codeVerifier;
    const requestObject = createJwtWithPrivateKey({
      payload: {
        client_id: clientSecretPostClient.clientId,
        response_type: "code",
        state: "aiueo",
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        redirect_uri: clientSecretPostClient.redirectUri,
        aud: serverConfig.issuer,
        iss: clientSecretPostClient.clientId,
        exp: toEpocTime({ adjusted: 3000 }),
        iat: toEpocTime({}),
        nbf: toEpocTime({}),
        jti: generateJti(),
        response_mode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        code_challenge: codeChallenge,
        code_challenge_method: "plain",
        login_hint: serverConfig.oauth.username,
        acr_values: serverConfig.acr,
        authorization_details: [{
          "type": "account_information",
          "actions": [
            "list_accounts",
            "read_balances",
            "read_transactions"
          ],
          "locations": [
            "https://example.com/accounts"
          ]
        },
          {
            "type": "payment_initiation",
            "actions": [
              "initiate",
              "status",
              "cancel"
            ],
            "locations": [
              "https://example.com/payments"
            ],
            "instructedAmount": {
              "currency": "EUR",
              "amount": "123.50"
            },
            "creditorName": "Merchant A",
            "creditorAccount": {
              "iban": "DE02100100109307118603"
            },
            "remittanceInformationUnstructured": "Ref Number Merchant"
          }]
      },
      privateKey: clientSecretPostClient.requestKey,
    });

    const parResponse = await pushAuthorizations({
      endpoint: serverConfig.authorizationEndpoint + "/push",
      request: requestObject,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    console.log(parResponse.data);
    expect(parResponse.status).toBe(201);

    const { request_uri } = parResponse.data;

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      requestUri: request_uri,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      codeVerifier,
    });
    console.log(JSON.stringify(tokenResponse.data));
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);
  });

  describe("3. Pushed Authorization Request Endpoint", () => {
    it("The client makes a request to the PAR endpoint by sending the following parameters using the \"application/x-www-form-urlencoded\" format - RFC 9126 Section 3", async () => {
      // RFC 9126 Section 3: Content-Type MUST be application/x-www-form-urlencoded
      // Sending application/json should return HTTP 400 Bad Request
      const basicAuth = Buffer.from(
        `${clientSecretPostClient.clientId}:${clientSecretPostClient.clientSecret}`
      ).toString("base64");

      const response = await postWithJson({
        url: serverConfig.pushedAuthorizationEndpoint,
        headers: {
          "Content-Type": "application/json",
          Authorization: `Basic ${basicAuth}`,
        },
        body: {
          response_type: "code",
          client_id: clientSecretPostClient.clientId,
          redirect_uri: clientSecretPostClient.redirectUri,
          scope: clientSecretPostClient.scope,
        },
      });

      console.log(response.data);
      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
      expect(response.data.error_description).toBe(
        "Bad request. Content-Type header does not match supported values"
      );
    });
  });

});
