import { describe, expect, it, xit } from "@jest/globals";

import { getJwks, requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";
import {
  createJwe,
  createJwt,
  createJwtWithNoneSignature,
  createJwtWithPrivateKey,
  generateJti,
  verifyAndDecodeIdToken,
} from "./lib/jose";
import { toEpocTime } from "./lib/util";

describe("OpenID Connect Core 1.0 incorporating errata set 1 request object", () => {
  it("success pattern normal", async () => {
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
    const { authorizationResponse } = await requestAuthorizations({
      client_id: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      aud: serverConfig.issuer,
      iss: clientSecretPostClient.clientId,
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      authorizationDetails: JSON.stringify(authorizationDetails),
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
    });
    console.log(JSON.stringify(tokenResponse.data));
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeIdToken({
      idToken: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);
  });

  it("success pattern request object", async () => {
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

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      request: requestObject,
      clientId: clientSecretPostClient.clientId,
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
    });
    console.log(JSON.stringify(tokenResponse.data));
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeIdToken({
      idToken: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);
  });

});
