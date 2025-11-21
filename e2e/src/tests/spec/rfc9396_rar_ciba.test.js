import { describe, expect, it } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  getJwks,
  inspectToken,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import {
  createJwtWithPrivateKey,
  generateJti,
  verifyAndDecodeJwt,
} from "../../lib/jose";
import { toEpocTime } from "../../lib/util";

/**
 * RFC 9396 Rich Authorization Requests - CIBA Flow Tests
 *
 * This test suite validates RFC 9396 compliance specifically for CIBA (Client-Initiated Backchannel Authentication) flows.
 *
 * Test Coverage:
 * - Token response validation (Section 7)
 * - Request object integration (Section 3)
 * - Oneshot token pattern
 * - Multiple authorization_details processing
 * - Error handling
 */
describe("RFC 9396 Rich Authorization Requests - CIBA Flow", () => {
  const ciba = serverConfig.ciba;

  /**
   * Helper function to complete CIBA authentication flow
   */
  const completeCibaAuthentication = async (authReqId) => {
    const authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: serverConfig.ciba.authenticationDeviceId,
      params: {
        "attributes.auth_req_id": authReqId
      },
    });

    expect(authenticationTransactionResponse.status).toBe(200);
    const authenticationTransaction = authenticationTransactionResponse.data.list[0];

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
  };

  describe("token response validation - RFC 9396 Section 7", () => {
    it("AS MUST return authorization_details in CIBA token response", async () => {
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

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        authorizationDetails: JSON.stringify(authorizationDetails),
        loginHint: ciba.loginHintSub,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(backchannelAuthenticationResponse.status).toBe(200);

      await completeCibaAuthentication(backchannelAuthenticationResponse.data.auth_req_id);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log("CIBA Token response:", JSON.stringify(tokenResponse.data, null, 2));
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("authorization_details");
      expect(Array.isArray(tokenResponse.data.authorization_details)).toBe(true);
      expect(tokenResponse.data.authorization_details).toHaveLength(1);
      expect(tokenResponse.data.authorization_details[0].type).toBe("account_information");

      // Verify access token contains authorization_details
      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      const decodedAccessToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.access_token,
        jwks: jwksResponse.data
      });
      expect(decodedAccessToken.payload).toHaveProperty("authorization_details");
      expect(decodedAccessToken.payload.authorization_details).toHaveLength(1);
    });

    it("AS MUST return multiple authorization_details in CIBA token response", async () => {
      const authorizationDetails = [
        {
          "type": "account_information",
          "actions": ["list_accounts", "read_balances"],
          "locations": ["https://example.com/accounts"]
        },
        {
          "type": "payment_initiation",
          "actions": ["initiate"],
          "locations": ["https://example.com/payments"],
          "instructedAmount": {
            "currency": "EUR",
            "amount": "100.00"
          }
        }
      ];

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        authorizationDetails: JSON.stringify(authorizationDetails),
        loginHint: ciba.loginHintSub,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(backchannelAuthenticationResponse.status).toBe(200);

      await completeCibaAuthentication(backchannelAuthenticationResponse.data.auth_req_id);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.authorization_details).toHaveLength(2);

      const types = tokenResponse.data.authorization_details.map(d => d.type);
      expect(types).toContain("account_information");
      expect(types).toContain("payment_initiation");
    });
  });

  describe("request object integration - RFC 9396 Section 3", () => {
    it("AS MUST process authorization_details within signed request object", async () => {
      const authorizationDetails = [{
        "type": "account_information",
        "actions": ["list_accounts", "read_balances"],
        "locations": ["https://example.com/accounts"]
      }];

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          scope: "openid profile phone email",
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          authorization_details: authorizationDetails,
          login_hint: ciba.loginHintSub,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        request
      });
      expect(backchannelAuthenticationResponse.status).toBe(200);

      await completeCibaAuthentication(backchannelAuthenticationResponse.data.auth_req_id);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("authorization_details");
      expect(tokenResponse.data.authorization_details[0].type).toBe("account_information");
    });
  });

  describe("oneshot token pattern", () => {
    it("AS MUST honor oneshot_token flag in authorization_details", async () => {
      const authorizationDetails = [{
        "type": "account_information",
        "actions": ["list_accounts"],
        "locations": ["https://example.com/accounts"],
        "oneshot_token": true,
      }];

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          scope: "openid profile phone email",
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          authorization_details: authorizationDetails,
          login_hint: ciba.loginHintSub,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        request
      });
      expect(backchannelAuthenticationResponse.status).toBe(200);

      await completeCibaAuthentication(backchannelAuthenticationResponse.data.auth_req_id);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);

      // First introspection should succeed
      let introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);

      // Second introspection should fail (oneshot consumed)
      introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(false);
    });
  });

  describe("multiple authorization details processing", () => {
    it("authorization_details array MAY contain multiple entries of same type in CIBA", async () => {
      const authorizationDetails = [
        {
          "type": "account_information",
          "actions": ["list_accounts"],
          "locations": ["https://example.com/accounts/personal"]
        },
        {
          "type": "account_information",
          "actions": ["read_balances"],
          "locations": ["https://example.com/accounts/business"]
        }
      ];

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        authorizationDetails: JSON.stringify(authorizationDetails),
        loginHint: ciba.loginHintSub,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(backchannelAuthenticationResponse.status).toBe(200);

      await completeCibaAuthentication(backchannelAuthenticationResponse.data.auth_req_id);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.authorization_details).toHaveLength(2);
      expect(tokenResponse.data.authorization_details.every(d => d.type === "account_information")).toBe(true);
    });
  });

  describe("error handling", () => {
    it("AS MUST refuse CIBA request with invalid authorization_details type", async () => {
      const authorizationDetails = [{
        "type": "invalid_type",
        "actions": ["some_action"],
        "locations": ["https://example.com/invalid"]
      }];

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        authorizationDetails: JSON.stringify(authorizationDetails),
        loginHint: ciba.loginHintSub,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log("CIBA error response:", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.data.error).toBe("invalid_authorization_details");
      expect(backchannelAuthenticationResponse.data.error_description).toContain("invalid_type");
    });

    it("AS MUST refuse CIBA request with missing type field", async () => {
      const authorizationDetails = [{
        "actions": ["list_accounts"],
        "locations": ["https://example.com/accounts"]
      }];

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        authorizationDetails: JSON.stringify(authorizationDetails),
        loginHint: ciba.loginHintSub,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log("CIBA error response (missing type):", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.data.error).toBe("invalid_authorization_details");
      expect(backchannelAuthenticationResponse.data.error_description).toContain("type");
    });

    it("AS MUST refuse CIBA request with non-array authorization_details", async () => {
      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        authorizationDetails: '{"type":"account_information"}', // Object instead of array
        loginHint: ciba.loginHintSub,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log("CIBA error response (non-array):", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.data.error).toBe("invalid_authorization_details");
      expect(backchannelAuthenticationResponse.data.error_description).toContain("array");
    });

    it("AS MUST refuse CIBA request with empty authorization_details array", async () => {
      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        authorizationDetails: '[]',
        loginHint: ciba.loginHintSub,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log("CIBA error response (empty array):", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.data.error).toBe("invalid_authorization_details");
      expect(backchannelAuthenticationResponse.data.error_description).toContain("unspecified");
    });
  });

  describe("request object error handling - RFC 9396 Section 3", () => {
    it("AS MUST refuse request object with invalid authorization_details type", async () => {
      const authorizationDetails = [{
        "type": "invalid_type",
        "actions": ["some_action"],
        "locations": ["https://example.com/invalid"]
      }];

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          scope: "openid profile phone email",
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          authorization_details: authorizationDetails,
          login_hint: ciba.loginHintSub,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        request
      });

      console.log("CIBA request object error response (invalid type):", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.data.error).toBe("invalid_authorization_details");
      expect(backchannelAuthenticationResponse.data.error_description).toContain("invalid_type");
    });

    it("AS MUST refuse request object with missing type field in authorization_details", async () => {
      const authorizationDetails = [{
        "actions": ["list_accounts"],
        "locations": ["https://example.com/accounts"]
      }];

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          scope: "openid profile phone email",
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          authorization_details: authorizationDetails,
          login_hint: ciba.loginHintSub,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        request
      });

      console.log("CIBA request object error response (missing type):", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.data.error).toBe("invalid_authorization_details");
      expect(backchannelAuthenticationResponse.data.error_description).toContain("type");
    });

    it("AS MUST refuse request object with unsupported authorization_details type", async () => {
      const authorizationDetails = [{
        "type": "unsupported_custom_type",
        "actions": ["custom_action"]
      }];

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          scope: "openid profile phone email",
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          authorization_details: authorizationDetails,
          login_hint: ciba.loginHintSub,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        request
      });

      console.log("CIBA request object error response (unsupported type):", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.data.error).toBe("invalid_authorization_details");
      expect(backchannelAuthenticationResponse.data.error_description).toContain("unsupported_custom_type");
    });

    it("AS MUST refuse request object with multiple authorization_details containing invalid type", async () => {
      const authorizationDetails = [
        {
          "type": "account_information",
          "actions": ["list_accounts"],
          "locations": ["https://example.com/accounts"]
        },
        {
          "type": "invalid_type",
          "actions": ["invalid_action"]
        }
      ];

      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          scope: "openid profile phone email",
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          authorization_details: authorizationDetails,
          login_hint: ciba.loginHintSub,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        request
      });

      console.log("CIBA request object error response (mixed types):", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.data.error).toBe("invalid_authorization_details");
      expect(backchannelAuthenticationResponse.data.error_description).toContain("invalid_type");
    });
  });
});
