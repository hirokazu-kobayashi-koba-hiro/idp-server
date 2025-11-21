import { describe, expect, it } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  getJwks, inspectToken, postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";
import {
  createJwtWithPrivateKey,
  generateJti,
  verifyAndDecodeJwt,
} from "../../lib/jose";
import { toEpocTime } from "../../lib/util";
import { generateCodeVerifier } from "../../lib/oauth";

describe("OpenID Connect Core 1.0 incorporating errata set 1 request object", () => {
  describe("authorization code flow", () => {
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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
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

      const decodedAccessToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.access_token,
        jwks: jwksResponse.data
      });
      console.log(JSON.stringify(decodedAccessToken, null, 2));

      expect(decodedAccessToken.payload).toHaveProperty("authorization_details");

    });
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

    const decodedAccessToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.access_token,
      jwks: jwksResponse.data
    });
    console.log(JSON.stringify(decodedAccessToken, null, 2));

    expect(decodedAccessToken.payload).toHaveProperty("authorization_details");
  });

  describe("ciba flow", () => {
    const ciba = serverConfig.ciba;

    it("normal pattern", async () => {
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

      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          authorizationDetails: JSON.stringify(authorizationDetails),
          loginHint: ciba.loginHintSub,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });

      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

      const failureResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: "serverConfig.ciba.userCode",
        }
      });
      console.log(failureResponse.data);
      console.log(failureResponse.status);

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
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
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);

      backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        idTokenHint: tokenResponse.data.id_token,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);

      const decodedAccessToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.access_token,
        jwks: jwksResponse.data
      });
      console.log(JSON.stringify(decodedAccessToken, null, 2));

      expect(decodedAccessToken.payload).toHaveProperty("authorization_details");
    });

    it("request object pattern", async () => {
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

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          scope: "openid profile phone email ",
          binding_message: ciba.bindingMessage,
          userCode: ciba.userCode,
          authorization_details: authorizationDetails,
          login_hint: ciba.loginHintSub,
          client_secret: clientSecretPostClient.clientSecret,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          request
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      const authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });

      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];

      const failureResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransactionResponse.data.flow,
        id: authenticationTransactionResponse.data.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: "serverConfig.ciba.userCode",
        }
      });
      console.log(failureResponse.data);
      console.log(failureResponse.status);

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
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
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);

      backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        idTokenHint: tokenResponse.data.id_token,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);

      const decodedAccessToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.access_token,
        jwks: jwksResponse.data
      });
      console.log(JSON.stringify(decodedAccessToken, null, 2));

      expect(decodedAccessToken.payload).toHaveProperty("authorization_details");
    });

    it("oneshot with request object pattern", async () => {
      const authorizationDetails = [{
        "type": "account_information",
        "actions": [
          "list_accounts",
          "read_balances",
          "read_transactions"
        ],
        "locations": [
          "https://example.com/accounts"
        ],
        "oneshot_token": true,
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

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          scope: "openid profile phone email ",
          binding_message: ciba.bindingMessage,
          userCode: ciba.userCode,
          authorization_details: authorizationDetails,
          login_hint: ciba.loginHintSub,
          client_secret: clientSecretPostClient.clientSecret,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          request
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      const authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });

      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);
      const authenticationTransaction = authenticationTransactionResponse.data.list[0]

      const failureResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: "serverConfig.ciba.userCode",
        }
      });
      console.log(failureResponse.data);
      console.log(failureResponse.status);

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
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
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);

      backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        idTokenHint: tokenResponse.data.id_token,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);

      const decodedAccessToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.access_token,
        jwks: jwksResponse.data
      });
      console.log(JSON.stringify(decodedAccessToken, null, 2));

      expect(decodedAccessToken.payload).toHaveProperty("authorization_details");

      let introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);

      introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(false);

    });
  });

  describe("token response validation - RFC 9396 Section 7", () => {
    it("AS MUST return the authorization_details as granted by the resource owner", async () => {
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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: JSON.stringify(authorizationDetails),
      });
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

      console.log("Token response:", JSON.stringify(tokenResponse.data, null, 2));
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("authorization_details");
      expect(Array.isArray(tokenResponse.data.authorization_details)).toBe(true);
      expect(tokenResponse.data.authorization_details).toHaveLength(1);
      expect(tokenResponse.data.authorization_details[0].type).toBe("account_information");
    });

    it("AS MUST return multiple authorization_details as granted and assigned to the access token", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;
      const authorizationDetails = [
        {
          "type": "account_information",
          "actions": [
            "list_accounts",
            "read_balances"
          ],
          "locations": [
            "https://example.com/accounts"
          ]
        },
        {
          "type": "payment_initiation",
          "actions": [
            "initiate"
          ],
          "locations": [
            "https://example.com/payments"
          ],
          "instructedAmount": {
            "currency": "EUR",
            "amount": "100.00"
          }
        }
      ];

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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: JSON.stringify(authorizationDetails),
      });
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

      console.log("Token response with multiple details:", JSON.stringify(tokenResponse.data, null, 2));
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("authorization_details");
      expect(Array.isArray(tokenResponse.data.authorization_details)).toBe(true);
      expect(tokenResponse.data.authorization_details).toHaveLength(2);

      const types = tokenResponse.data.authorization_details.map(d => d.type);
      expect(types).toContain("account_information");
      expect(types).toContain("payment_initiation");
    });
  });

  describe("multiple authorization details processing - RFC 9396 Section 2", () => {
    it("authorization_details array MAY contain multiple entries of the same type", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;
      const authorizationDetails = [
        {
          "type": "account_information",
          "actions": [
            "list_accounts"
          ],
          "locations": [
            "https://example.com/accounts/personal"
          ]
        },
        {
          "type": "account_information",
          "actions": [
            "read_balances"
          ],
          "locations": [
            "https://example.com/accounts/business"
          ]
        }
      ];

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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: JSON.stringify(authorizationDetails),
      });
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

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.authorization_details).toHaveLength(2);
      expect(tokenResponse.data.authorization_details.every(d => d.type === "account_information")).toBe(true);
    });
  });

  describe("parameter structure errors - RFC 9396 Section 4", () => {
    it("AS MUST refuse to process authorization_details not conforming to array structure", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;

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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: '{"type":"account_information"}', // Object instead of array
      });

      console.log("Authorization response for non-array:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
    });

    it("AS MUST refuse to process empty authorization_details array", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;

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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: '[aaa]',
      });

      console.log("Authorization response for empty array:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
    });

    it("AS MUST refuse to process authorization_details with invalid JSON syntax", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;

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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: '{invalid json}',
      });

      console.log("Authorization response for invalid JSON:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
    });
  });

  describe("error cases - type validation", () => {
    it("AS MUST refuse authorization details without required type field", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;
      const authorizationDetails = [{
        "actions": [
          "list_accounts",
          "read_balances"
        ],
        "locations": [
          "https://example.com/accounts"
        ]
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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: JSON.stringify(authorizationDetails),
      });
      
      console.log("Authorization response for missing type:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("type");
    });

    it("AS MUST refuse authorization details with empty type field", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;
      const authorizationDetails = [{
        "type": "",
        "actions": [
          "list_accounts",
          "read_balances"
        ],
        "locations": [
          "https://example.com/accounts"
        ]
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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: JSON.stringify(authorizationDetails),
      });
      
      console.log("Authorization response for empty type:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("authorization details does not contains type");
    });

    it("AS MUST refuse to process any unknown authorization details type", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;
      const authorizationDetails = [{
        "type": "unsupported_type",
        "actions": [
          "list_accounts",
          "read_balances"
        ],
        "locations": [
          "https://example.com/accounts"
        ]
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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: JSON.stringify(authorizationDetails),
      });
      
      console.log("Authorization response for unsupported type:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("unauthorized authorization details type (unsupported_type)");
    });

    it("AS MUST refuse authorization details with null type field", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;
      const authorizationDetails = [{
        "type": null,
        "actions": [
          "list_accounts",
          "read_balances"
        ],
        "locations": [
          "https://example.com/accounts"
        ]
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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: JSON.stringify(authorizationDetails),
      });
      
      console.log("Authorization response for null type:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("authorization details does not contains type");
    });

    it("AS MUST refuse to process mixed valid and invalid authorization details", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;
      const authorizationDetails = [
        {
          "type": "account_information",
          "actions": [
            "list_accounts",
            "read_balances"
          ],
          "locations": [
            "https://example.com/accounts"
          ]
        },
        {
          "type": "invalid_type",
          "actions": [
            "invalid_action"
          ],
          "locations": [
            "https://example.com/invalid"
          ]
        }
      ];

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
        responseMode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        codeChallenge,
        codeChallengeMethod: "plain",
        authorizationDetails: JSON.stringify(authorizationDetails),
      });
      
      console.log("Authorization response for mixed valid/invalid types:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("unauthorized authorization details type (invalid_type)");
    });
  });

  describe("request object error handling - RFC 9396 Section 3", () => {
    it("AS MUST refuse request object with invalid authorization_details type", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;
      const authorizationDetails = [{
        "type": "invalid_type",
        "actions": ["some_action"],
        "locations": ["https://example.com/invalid"]
      }];

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          scope: "openid profile phone email",
          redirect_uri: clientSecretPostClient.redirectUri,
          state: "aiueo",
          nonce: "nonce",
          code_challenge: codeChallenge,
          code_challenge_method: "plain",
          authorization_details: authorizationDetails,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const { authorizationResponse } = await requestAuthorizations({
        client_id: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        request
      });

      console.log("Authorization response for request object with invalid type:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("invalid_type");
    });

    it("AS MUST refuse request object with missing type field in authorization_details", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;
      const authorizationDetails = [{
        "actions": ["list_accounts"],
        "locations": ["https://example.com/accounts"]
      }];

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          scope: "openid profile phone email",
          redirect_uri: clientSecretPostClient.redirectUri,
          state: "aiueo",
          nonce: "nonce",
          code_challenge: codeChallenge,
          code_challenge_method: "plain",
          authorization_details: authorizationDetails,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const { authorizationResponse } = await requestAuthorizations({
        client_id: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        request
      });

      console.log("Authorization response for request object with missing type:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("type");
    });

    it("AS MUST refuse request object with unsupported authorization_details type", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;
      const authorizationDetails = [{
        "type": "unsupported_custom_type",
        "actions": ["custom_action"]
      }];

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          scope: "openid profile phone email",
          redirect_uri: clientSecretPostClient.redirectUri,
          state: "aiueo",
          nonce: "nonce",
          code_challenge: codeChallenge,
          code_challenge_method: "plain",
          authorization_details: authorizationDetails,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const { authorizationResponse } = await requestAuthorizations({
        client_id: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        request
      });

      console.log("Authorization response for request object with unsupported type:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("unsupported_custom_type");
    });

    it("AS MUST refuse request object with non-array authorization_details (string)", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          scope: "openid profile phone email",
          redirect_uri: clientSecretPostClient.redirectUri,
          state: "aiueo",
          nonce: "nonce",
          code_challenge: codeChallenge,
          code_challenge_method: "plain",
          authorization_details: "invalid_string",  // Not an array!
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const { authorizationResponse } = await requestAuthorizations({
        client_id: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        request
      });

      console.log("Authorization response for request object with string authorization_details:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("not array");
    });

    it("AS MUST refuse request object with non-array authorization_details (object)", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          scope: "openid profile phone email",
          redirect_uri: clientSecretPostClient.redirectUri,
          state: "aiueo",
          nonce: "nonce",
          code_challenge: codeChallenge,
          code_challenge_method: "plain",
          authorization_details: { "type": "direct_object" },  // Object, not array!
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const { authorizationResponse } = await requestAuthorizations({
        client_id: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        request
      });

      console.log("Authorization response for request object with object authorization_details:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("not array");
    });

    it("AS MUST refuse request object with empty authorization_details array", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = codeVerifier;

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          scope: "openid profile phone email",
          redirect_uri: clientSecretPostClient.redirectUri,
          state: "aiueo",
          nonce: "nonce",
          code_challenge: codeChallenge,
          code_challenge_method: "plain",
          authorization_details: [],  // Empty array!
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const { authorizationResponse } = await requestAuthorizations({
        client_id: clientSecretPostClient.clientId,
        responseType: "code",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        request
      });

      console.log("Authorization response for request object with empty authorization_details:", authorizationResponse);
      expect(authorizationResponse.error).toBe("invalid_authorization_details");
      expect(authorizationResponse.errorDescription).toContain("unspecified");
    });
  });
});
