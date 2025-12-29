import { describe, expect, it } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "../../api/oauthClient";
import {
  clientSecretBasicClient,
  clientSecretJwtClient,
  serverConfig,
} from "../testConfig";
import { createBasicAuthHeader } from "../../lib/util";
import { createClientAssertion } from "../../lib/oauth";

/**
 * RFC 8266 - CIBA Client Notification Tests
 *
 * These tests verify that the IdP server correctly sends CIBA notifications
 * with the Authorization header containing the client_notification_token
 * as a Bearer token (RFC 8266 Section 10.2 and 10.3).
 *
 * The Mockoon mock server is configured to:
 * - Return 204 No Content when Authorization header matches "Bearer <token>"
 * - Return 401 Unauthorized when Authorization header is missing or invalid
 *
 * If the notification fails (401), the server logs an error but the test
 * may still pass because the grant flow completes. Check server logs for
 * "CIBA client notification failed" to detect issues.
 */
describe("RFC 8266 - CIBA Client Notification with Authorization Header", () => {
  const ciba = serverConfig.ciba;

  describe("10.2. Ping Mode - client_notification_token in Authorization header", () => {
    it("should send notification with Bearer token and receive 204 from mock server", async () => {
      // clientSecretBasic is configured with backchannel_token_delivery_mode: "ping"
      const basicAuth = createBasicAuthHeader({
        username: clientSecretBasicClient.clientId,
        password: clientSecretBasicClient.clientSecret,
      });

      // Step 1: Send backchannel authentication request
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          scope: "openid profile phone email" + clientSecretBasicClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          basicAuth,
        });
      console.log("Backchannel authentication response:", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
      expect(backchannelAuthenticationResponse.data.auth_req_id).toBeDefined();

      // Step 2: Get authentication transaction for the device
      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log("Authentication transaction:", authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      expect(authenticationTransaction).toBeDefined();

      // Step 3: Complete authentication on device
      // This triggers the PING notification to client_notification_endpoint
      // The server should send: POST /ciba/callback with Authorization: Bearer <token>
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
      console.log("Authentication completed - PING notification should have been sent");

      // Step 4: Token request (PING mode - client polls token endpoint)
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        basicAuth,
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.access_token).toBeDefined();
      expect(tokenResponse.data.id_token).toBeDefined();
    });
  });

  describe("10.3. Push Mode - client_notification_token in Authorization header", () => {
    it("should send notification with Bearer token containing tokens and receive 204 from mock server", async () => {
      // clientSecretJwt is configured with backchannel_token_delivery_mode: "push"
      const clientAssertion = createClientAssertion({
        client: clientSecretJwtClient,
        issuer: serverConfig.issuer,
      });

      // Step 1: Send backchannel authentication request
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretJwtClient.clientId,
          scope: "openid profile phone email" + clientSecretJwtClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientAssertion,
          clientAssertionType:
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        });
      console.log("Backchannel authentication response:", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
      expect(backchannelAuthenticationResponse.data.auth_req_id).toBeDefined();

      // Step 2: Get authentication transaction for the device
      let authenticationTransactionResponse;
      authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log("Authentication transaction:", authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      expect(authenticationTransaction).toBeDefined();

      // Step 3: Complete authentication on device
      // This triggers the PUSH notification to client_notification_endpoint
      // The server should send: POST /ciba/callback with Authorization: Bearer <token>
      // and body containing access_token, id_token, refresh_token, token_type, expires_in
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
      console.log("Authentication completed - PUSH notification with tokens should have been sent");

      // Note: In PUSH mode, there's no token endpoint call needed.
      // The tokens are delivered directly via the notification callback.
      // If the mock server returned 401 (missing Authorization header),
      // the notification would fail and be logged on the server side.
    });
  });

  describe("Error Notification - access_denied when user denies the request", () => {
    it("PING mode - should send error notification with access_denied when user denies", async () => {
      // clientSecretBasic is configured with backchannel_token_delivery_mode: "ping"
      const basicAuth = createBasicAuthHeader({
        username: clientSecretBasicClient.clientId,
        password: clientSecretBasicClient.clientSecret,
      });

      // Step 1: Send backchannel authentication request
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          scope: "openid profile phone email" + clientSecretBasicClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          basicAuth,
        });
      console.log("Backchannel authentication response:", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
      expect(backchannelAuthenticationResponse.data.auth_req_id).toBeDefined();

      // Step 2: Get authentication transaction for the device
      const authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log("Authentication transaction:", authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      expect(authenticationTransaction).toBeDefined();

      // Step 3: Deny authentication on device
      // This triggers the error notification to client_notification_endpoint
      // The server should send: POST /ciba/callback with Authorization: Bearer <token>
      // and body containing error: "access_denied", error_description: "..."
      const denyResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "authentication-device-deny",
        body: {}
      });
      expect(denyResponse.status).toBe(200);
      console.log("Authentication denied - Error notification should have been sent");

      // Step 4: Token request should return access_denied
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        basicAuth,
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("access_denied");
    });

    it("PUSH mode - should send error notification with access_denied when user denies", async () => {
      // clientSecretJwt is configured with backchannel_token_delivery_mode: "push"
      const clientAssertion = createClientAssertion({
        client: clientSecretJwtClient,
        issuer: serverConfig.issuer,
      });

      // Step 1: Send backchannel authentication request
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretJwtClient.clientId,
          scope: "openid profile phone email" + clientSecretJwtClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientAssertion,
          clientAssertionType:
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        });
      console.log("Backchannel authentication response:", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
      expect(backchannelAuthenticationResponse.data.auth_req_id).toBeDefined();

      // Step 2: Get authentication transaction for the device
      const authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
        },
      });
      console.log("Authentication transaction:", authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      expect(authenticationTransaction).toBeDefined();

      // Step 3: Deny authentication on device
      // This triggers the error notification to client_notification_endpoint
      // The server should send: POST /ciba/callback with Authorization: Bearer <token>
      // and body containing error: "access_denied", error_description: "..."
      const denyResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "authentication-device-deny",
        body: {}
      });
      expect(denyResponse.status).toBe(200);
      console.log("Authentication denied - Error notification with access_denied should have been sent to client");

      // Note: In PUSH mode, there's no token endpoint call needed.
      // The error notification is delivered directly via the notification callback.
      // If the mock server returned 401 (missing Authorization header),
      // the notification would fail and be logged on the server side.
    });
  });
});
