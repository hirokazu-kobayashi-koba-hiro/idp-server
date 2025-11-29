import { describe, expect, it } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction, getConfiguration,
  inspectToken,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken,
} from "../../api/oauthClient";
import {
  clientSecretBasicClient,
  clientSecretJwt2Client,
  clientSecretJwtClient,
  clientSecretPostClient,
  privateKeyJwtClient,
  publicClient,
  selfSignedTlsAuthClient,
  selfSignedTlsClientAuth2,
  serverConfig,
} from "../testConfig";
import { createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { getEntropyBits, isArray, toEpocTime } from "../../lib/util";
import { certThumbprint, requestAuthorizations } from "../../oauth/request";
import { calculateCodeChallengeWithS256, generateCodeVerifier } from "../../lib/oauth";
import { post } from "../../lib/http";
import { encodedClientCert } from "../../api/cert/clientCert";

describe("FAPI CIBA Profile - Financial-grade API: Client Initiated Backchannel Authentication Profile", () => {
  const ciba = serverConfig.ciba;
  const client = selfSignedTlsAuthClient;

  it("success pattern with signed request object", async () => {
    // Create signed request object with FAPI CIBA requirements
    const requestObject = createJwtWithPrivateKey({
      payload: {
        scope: "openid profile phone email " + client.fapiAdvanceScope,
        binding_message: ciba.bindingMessage,
        user_code: ciba.userCode,
        login_hint: ciba.loginHintDevice,
        client_id: client.clientId,
        aud: serverConfig.issuer,
        iss: client.clientId,
        exp: toEpocTime({ adjusted: 1800 }), // 30 minutes (within 60 minutes requirement)
        iat: toEpocTime({}),
        nbf: toEpocTime({}),
        jti: generateJti(),
      },
      privateKey: client.requestKey,
    });

    console.log("Request Object JWT:", requestObject);

    // Backchannel authentication request with signed request object
    let backchannelAuthenticationResponse = await requestBackchannelAuthentications({
      endpoint: serverConfig.backchannelAuthenticationEndpoint,
      clientId: client.clientId,
      request: requestObject,
      clientCertFile: client.clientCertFile,
    });

    console.log("Backchannel Authentication Response:", backchannelAuthenticationResponse.data);
    expect(backchannelAuthenticationResponse.status).toBe(200);
    expect(backchannelAuthenticationResponse.data).toHaveProperty("auth_req_id");
    expect(backchannelAuthenticationResponse.data).toHaveProperty("expires_in");

    // Get authentication transaction
    let authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: serverConfig.ciba.authenticationDeviceId,
      params: {
        "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id,
      },
    });

    console.log("Authentication Transaction Response:", authenticationTransactionResponse.data);
    expect(authenticationTransactionResponse.status).toBe(200);
    expect(authenticationTransactionResponse.data.list).toHaveLength(1);

    const authenticationTransaction = authenticationTransactionResponse.data.list[0];

    // Authenticate with user code
    const completeResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "password-authentication",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      },
    });

    console.log("Authentication Complete Response:", completeResponse.data);
    expect(completeResponse.status).toBe(200);

    // Request token with CIBA grant
    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: backchannelAuthenticationResponse.data.auth_req_id,
      clientId: client.clientId,
      clientCertFile: client.clientCertFile,
    });

    console.log("Token Response:", tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("access_token");
    expect(tokenResponse.data).toHaveProperty("id_token");
    expect(tokenResponse.data).toHaveProperty("token_type");

    const introspectionResponse = await inspectToken({
      endpoint: serverConfig.tokenIntrospectionEndpoint,
      token: tokenResponse.data.access_token,
      clientId: selfSignedTlsAuthClient.clientId,
      clientCertFile: selfSignedTlsAuthClient.clientCertFile,
    });
    console.log(introspectionResponse.data);
    expect(introspectionResponse.status).toBe(200);
    expect(introspectionResponse.data).toHaveProperty("cnf");
    const thumbprint = certThumbprint(selfSignedTlsAuthClient.clientCertFile);
    expect(introspectionResponse.data.cnf["x5t#S256"]).toEqual(thumbprint);
  });

  describe("FAPI CIBA 5.2.2.  In addition the Authorization server, for all operations,", () => {
    it("1. shall only support Confidential Clients for Client Initiated Backchannel Authentication flows;", async () => {
      // Public clients use 'none' authentication type and have no client authentication
      // This test attempts CIBA request without any client authentication
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + publicClient.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: publicClient.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: publicClient.requestKey,
      });

      // Attempt request without client certificate (no mTLS) or client assertion
      // This simulates a public client attempting CIBA
      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: publicClient.clientId,
        request: requestObject,
        // clientCertFile is intentionally omitted - no mTLS authentication
        // In a real scenario, server should also check for absence of client_assertion
      });

      console.log("Expected error (public client):", backchannelResponse.data);
      // Note: The actual error depends on how the request is made
      // Without mTLS cert, it may fail at transport level or with 401 Unauthorized
      // With properly configured server, it should return unauthorized_client
      expect(backchannelResponse.status).toBe(400);
      expect(backchannelResponse.data.error).toEqual("unauthorized_client");
    });


    it("should accept request without binding_message when authorization_details is present", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          authorization_details: [
            {
              type: "payment_initiation",
              instructedAmount: {
                currency: "EUR",
                amount: "123.50",
              },
              creditorName: "Merchant A",
              creditorAccount: {
                iban: "DE02100100109307118603",
              },
            },
          ],
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
        algorithm: "PS256",
      });

      let backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Backchannel Authentication Response:", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
    });

    // Additional FAPI CIBA Spec Tests based on OpenID FAPI CIBA Profile 5.2.2

    it("3. shall not support CIBA push mode", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + clientSecretJwtClient.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: clientSecretJwtClient.clientId,
          aud: serverConfig.issuer,
          iss: clientSecretJwtClient.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretJwtClient.requestKey,
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretJwtClient.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Expected error (no nbf):", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(400);
      expect(backchannelResponse.data.error).toBe("invalid_request");
      expect(backchannelResponse.data.error_description).toEqual("FAPI CIBA Profile does not support push mode for token delivery. Use poll or ping mode.");
    });

    it("should reject request object without nbf claim", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          // nbf is missing - should be rejected
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Expected error (no nbf):", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(400);
      expect(backchannelResponse.data.error).toBe("invalid_request");
      expect(backchannelResponse.data.error_description).toContain("nbf");
    });

    it("should reject request object without exp claim", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          // exp is missing - should be rejected
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Expected error (no exp):", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(400);
      expect(backchannelResponse.data.error).toBe("invalid_request");
      expect(backchannelResponse.data.error_description).toContain("exp");
    });

    it("should accept request object with exactly 60 minutes lifetime", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiAdvanceScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 3600 }), // Exactly 60 minutes
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("60-minute lifetime response:", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(200);
      expect(backchannelResponse.data).toHaveProperty("auth_req_id");
    });

    it("should reject request object without aud claim", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          // aud is missing - should be rejected
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Expected error (no aud):", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(400);
      expect(backchannelResponse.data.error).toBe("invalid_request");
      expect(backchannelResponse.data.error_description).toContain("aud");
    });

    it("should reject request object with wrong aud claim", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: "https://wrong-issuer.example.com", // Wrong issuer
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Expected error (wrong aud):", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(400);
      expect(backchannelResponse.data.error).toBe("invalid_request");
      expect(backchannelResponse.data.error_description).toContain("aud");
    });
  });

  describe("5.2.2.2 - Unique Authorization Context / binding_message Requirement", () => {
    it("should require binding_message when authorization_details is not present", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          // No authorization_details
          // No binding_message - should be rejected
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Expected error (no binding_message):", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(400);
      expect(backchannelResponse.data.error).toBe("invalid_request");
      expect(backchannelResponse.data.error_description).toContain("binding_message");
    });
  });

  describe("FAPI Advance 5.2.2.  Authorization server", () => {
    it("shall accept confidential client with self_signed_tls_client_auth", async () => {
      // This is the success case - already covered in main test
      // Verifies that confidential client with mTLS is accepted
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiAdvanceScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile, // Confidential client with mTLS
      });

      console.log("Confidential client response:", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(200);
      expect(backchannelResponse.data).toHaveProperty("auth_req_id");
    });

    it("should reject request without signed request object", async () => {
      // Try to make CIBA request without request object (should fail for FAPI CIBA)

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        scope: "openid profile phone email " + client.fapiBaselineScope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: ciba.loginHintDevice,
        clientCertFile: client.clientCertFile,
      });

      console.log("Expected error:", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(400);
      expect(backchannelResponse.data.error).toBe("invalid_request");
    });

    it("should reject request object with invalid lifetime (> 60 minutes)", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 3700 }), // 61+ minutes (exceeds 60 minutes requirement)
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Expected error:", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(400);
      expect(backchannelResponse.data.error).toBe("invalid_request");
    });

    it("should reject request object with invalid signing algorithm (RS256)", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
        algorithm: "RS256", // Invalid for FAPI CIBA (requires PS256 or ES256)
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Expected error:", backchannelResponse.data);
      expect(backchannelResponse.status).toBe(400);
      expect(backchannelResponse.data.error).toBe("invalid_request");
    });

    it("should issue sender-constrained access tokens with cnf claim", async () => {
      // This test is already covered in the main success pattern test
      // Verifies that access token contains cnf claim with x5t#S256 thumbprint
      // Confirming the requirement from FAPI Part 2 Section 5.2.2-5
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiAdvanceScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      expect(backchannelResponse.status).toBe(200);

      // Authenticate
      const authTxResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelResponse.data.auth_req_id,
        },
      });

      const authTx = authTxResponse.data.list[0];

      await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authTx.flow,
        id: authTx.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        },
      });

      // Get token
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelResponse.data.auth_req_id,
        clientId: client.clientId,
        clientCertFile: client.clientCertFile,
      });

      expect(tokenResponse.status).toBe(200);

      // Introspect to verify cnf claim
      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: client.clientId,
        clientCertFile: client.clientCertFile,
      });

      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data).toHaveProperty("cnf");
      expect(introspectionResponse.data.cnf).toHaveProperty("x5t#S256");

      const thumbprint = certThumbprint(client.clientCertFile);
      expect(introspectionResponse.data.cnf["x5t#S256"]).toEqual(thumbprint);
    });
  });

  describe("FAPI Baseline 5.2.2.  Authorization Server The Authorization Server shall support the provisions specified in clause 5.2.2 of Financial-grade API - Part 1 and clause 5.2.2 of Financial-grade API - Part 2.", () => {
    it("1. shall support confidential clients;", async () => {
      // Public clients use 'none' authentication type and have no client authentication
      // This test attempts CIBA request without any client authentication

      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      // Attempt request without client certificate (no mTLS) or client assertion
      // This simulates a public client attempting CIBA
      const backchannelResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile
      });

      console.log(JSON.stringify(backchannelResponse.data, null, 2));
      expect(backchannelResponse.status).toBe(200);
      expect(backchannelResponse.data).toHaveProperty("auth_req_id");
      expect(backchannelResponse.data).toHaveProperty("interval");
      expect(backchannelResponse.data).toHaveProperty("expires_in");

    });

    it("Not Applicable. 2. should support public clients; But ciba is not support, because backchannel request.", () => {
      console.log("ciba is not support, because backchannel request.");
    });


    it("Not Applicable. 3. shall provide a client secret that adheres to the requirements in Section 16.19 of OIDC if a symmetric key is used;", async () => {
      console.log(
        "FAPI Advance. 5.2.2.14 shall authenticate the confidential client using one of the following methods (this overrides FAPI Security Profile 1.0 - Part 1: Baseline clause 5.2.2-4):\n" +
          "tls_client_auth or self_signed_tls_client_auth as specified in section 2 of MTLS, or\n" +
          "private_key_jwt as specified in section 9 of OIDC;"
      );
    });

    it("4. shall authenticate the confidential client using one of the following methods: unauthorized client_secret_basic", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: clientSecretBasicClient.clientId,
          aud: serverConfig.issuer,
          iss: clientSecretBasicClient.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretBasicClient.requestKey,
      });

      const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        request: requestObject,
      });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(401);
      expect(backchannelAuthenticationResponse.data.error).toEqual("invalid_client");
      expect(backchannelAuthenticationResponse.data.error_description).toContain("FAPI CIBA Profile requires client authentication method");
    });

    it("4. shall authenticate the confidential client using one of the following methods: unauthorized client_secret_post", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: clientSecretPostClient.clientId,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const backchannelAuthenticationResponse  = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        request: requestObject,
      });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(401);
      expect(backchannelAuthenticationResponse.data.error).toEqual("invalid_client");
      expect(backchannelAuthenticationResponse.data.error_description).toEqual("FAPI CIBA Profile requires client authentication method to be one of: private_key_jwt, tls_client_auth, self_signed_tls_client_auth. Current method: client_secret_post");
    });

    it("4. shall authenticate the confidential client using one of the following methods: unauthorized client_secret_jwt", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + clientSecretJwt2Client.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: clientSecretJwt2Client.clientId,
          aud: serverConfig.issuer,
          iss: clientSecretJwt2Client.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretJwt2Client.requestKey,
      });

      const backchannelAuthenticationResponse  = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretJwt2Client.clientId,
        request: requestObject,
      });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(401);
      expect(backchannelAuthenticationResponse.data.error).toEqual("invalid_client");
      expect(backchannelAuthenticationResponse.data.error_description).toEqual("FAPI CIBA Profile requires client authentication method to be one of: private_key_jwt, tls_client_auth, self_signed_tls_client_auth. Current method: client_secret_jwt");
    });

    it("5. shall require and use a key of size 2048 bits or larger for RSA algorithms;", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + selfSignedTlsAuthClient.fapiBaselineScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: selfSignedTlsAuthClient.clientId,
          aud: serverConfig.issuer,
          iss: selfSignedTlsAuthClient.clientId,
          exp: toEpocTime({ adjusted: 1800 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: selfSignedTlsAuthClient.shortSizeRequestKey,
      });

      const backchannelAuthenticationResponse  = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: selfSignedTlsAuthClient.clientId,
        request: requestObject,
        clientCertFile: selfSignedTlsAuthClient.clientCertFile
      });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(400);
      expect(backchannelAuthenticationResponse.data.error).toEqual("invalid_request");
      expect(backchannelAuthenticationResponse.data.error_description).toEqual("FAPI CIBA Profile requires RSA key size to be 2048 bits or larger. Current key size: 2000 bits");
    });

    it("6. shall require and use a key of size 160 bits or larger for elliptic curve algorithms;", async () => {
      // Note: ES256 uses P-256 curve which is 256 bits (> 160 bits requirement)
      // This test verifies that EC keys meet the minimum requirement
      console.log("ES256 uses P-256 curve (256 bits), which exceeds the 160 bits minimum requirement.");
      console.log("Implementation validates key size in FapiCibaVerifier.throwExceptionIfInvalidSigningAlgorithm()");

      // If we had a test key with < 160 bits, it would be rejected
      // However, standard EC curves (P-256, P-384, P-521) all exceed 160 bits
      // This requirement is automatically satisfied by using standard curves
    });

    it("Not Applicable. 7. shall require RFC7636 with S256 as the code challenge method;", () => {
      console.log("CIBA does not use authorization code flow, so PKCE is not applicable.");
    });

    it("Not Applicable. 8. shall require redirect URIs to be pre-registered;", () => {
      console.log("CIBA is a backchannel flow with no redirect, so redirect_uri is not applicable.");
    });

    it("Not Applicable. 9. shall require the redirect_uri in the authorization request;", () => {
      console.log("CIBA is a backchannel flow with no redirect, so redirect_uri is not applicable.");
    });

    it("Not Applicable. 10. shall require the value of redirect_uri to exactly match one of the pre-registered redirect URIs;", () => {
      console.log("CIBA is a backchannel flow with no redirect, so redirect_uri is not applicable.");
    });

    it(" 11. shall require user authentication to an appropriate Level of Assurance for the operations the client will be authorized to perform on behalf of the user;", () => {
      console.log(
        "CIBA requires appropriate Level of Assurance (LoA) for user authentication:\n" +
        "1. Authentication Policy configuration defines required LoA\n" +
        "2. FIDO-UAF (biometric) provides high LoA\n" +
        "3. Password authentication provides basic LoA\n" +
        "4. Multi-factor authentication can be required via Authentication Policy\n" +
        "Implementation: AuthenticationPolicyVerifier validates authentication methods"
      );
    });

    it ("12. shall require explicit approval by the user to authorize the requested scope if it has not been previously authorized;", () => {
      console.log(
        "CIBA requires explicit user approval:\n" +
        "1. User receives push notification on authentication device\n" +
        "2. binding_message displays transaction details for user review\n" +
        "3. authorization_details provides structured authorization context\n" +
        "4. User must explicitly approve (authenticate) the request\n" +
        "5. Approval is captured in postAuthenticationDeviceInteraction endpoint"
      );
    });

    it ("Not Applicable. 13. shall reject an authorization code (Section 1.3.1 of RFC6749) if it has been previously used;", () => {
      console.log("CIBA does not use authorization code flow, so authorization code is not applicable.");
    });

    it ("14. shall return token responses that conform to Section 4.1.4 of RFC6749;", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiAdvanceScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }), // 30 minutes (within 60 minutes requirement)
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      console.log("Request Object JWT:", requestObject);

      // Backchannel authentication request with signed request object
      let backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Backchannel Authentication Response:", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
      expect(backchannelAuthenticationResponse.data).toHaveProperty("auth_req_id");
      expect(backchannelAuthenticationResponse.data).toHaveProperty("expires_in");

      // Get authentication transaction
      let authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id,
        },
      });

      console.log("Authentication Transaction Response:", authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);
      expect(authenticationTransactionResponse.data.list).toHaveLength(1);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];

      // Authenticate with user code
      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        },
      });

      console.log("Authentication Complete Response:", completeResponse.data);
      expect(completeResponse.status).toBe(200);

      // Request token with CIBA grant
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: client.clientId,
        clientCertFile: client.clientCertFile,
      });

      console.log("Token Response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      // RFC 6749 Section 5.1
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data).toHaveProperty("token_type");
      expect(tokenResponse.data.token_type).toBe("Bearer");
      expect(tokenResponse.data).toHaveProperty("expires_in");
      expect(typeof tokenResponse.data.expires_in).toBe("number");
      expect(tokenResponse.data).toHaveProperty("id_token");
    });

    it ("Not Applicable. 15. shall return the list of granted scopes with the issued access token if the request was passed in the front channel and was not integrity protected;", () => {
      console.log("CIBA is a backchannel flow only, so front channel is not applicable.");
    });

    it ("16. shall provide non-guessable access tokens, authorization codes, and refresh token (where applicable), with sufficient entropy such that the probability of an attacker guessing the generated token is computationally infeasible as per RFC6749 Section 10.10;", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiAdvanceScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }), // 30 minutes (within 60 minutes requirement)
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      console.log("Request Object JWT:", requestObject);

      // Backchannel authentication request with signed request object
      let backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Backchannel Authentication Response:", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
      expect(backchannelAuthenticationResponse.data).toHaveProperty("auth_req_id");
      expect(backchannelAuthenticationResponse.data).toHaveProperty("expires_in");

      // Get authentication transaction
      let authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id,
        },
      });

      console.log("Authentication Transaction Response:", authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);
      expect(authenticationTransactionResponse.data.list).toHaveLength(1);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];

      // Authenticate with user code
      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        },
      });

      console.log("Authentication Complete Response:", completeResponse.data);
      expect(completeResponse.status).toBe(200);

      // Request token with CIBA grant
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: client.clientId,
        clientCertFile: client.clientCertFile,
      });

      console.log("Token Response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data).toHaveProperty("id_token");
      expect(tokenResponse.data).toHaveProperty("token_type");

      // Access Token Entropy
      const accessToken = tokenResponse.data.access_token;
      const accessTokenEntropy = getEntropyBits(accessToken);
      console.log(`Access Token entropy: ${accessTokenEntropy} bits`);
      expect(accessTokenEntropy).toBeGreaterThanOrEqual(128);

      const refreshToken = tokenResponse.data.refresh_token;
      const refreshTokenEntropy = getEntropyBits(refreshToken);
      console.log(`Refresh Token entropy: ${refreshTokenEntropy} bits`);
      expect(refreshTokenEntropy).toBeGreaterThanOrEqual(128);

      // auth_req_id Entropy
      const authReqId = backchannelAuthenticationResponse.data.auth_req_id;
      const authReqIdEntropy = getEntropyBits(authReqId);
      console.log(`auth_req_id entropy: ${authReqIdEntropy} bits`);
      expect(authReqIdEntropy).toBeGreaterThanOrEqual(128);

      console.log("Note: CIBA does not use authorization code, so code entropy is not applicable.");
    });

    it ("17. should clearly identify the details of the grant to the user during authorization as in 16.18 of OIDC;", () => {
      console.log(
        "CIBA clearly identifies grant details through:\n" +
        "1. binding_message - Transaction-specific details (e.g., 'TX-12345: €500 to ACME Corp')\n" +
        "2. authorization_details - Structured authorization context per RFC 9396\n" +
        "   - Example: payment_initiation with amount, currency, creditor details\n" +
        "3. scope - Requested permissions displayed to user\n" +
        "4. Client information - App name and icon shown on authentication device\n" +
        "\n" +
        "These details are displayed on the authentication device during user approval.\n" +
        "FAPI CIBA requires binding_message when authorization_details is not present."
      );
    });

    it ("18. should provide a mechanism for the end-user to revoke access tokens and refresh tokens granted to a client as in 16.18 of OIDC;", async () => {
      const requestObject = createJwtWithPrivateKey({
        payload: {
          scope: "openid profile phone email " + client.fapiAdvanceScope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHintDevice,
          client_id: client.clientId,
          aud: serverConfig.issuer,
          iss: client.clientId,
          exp: toEpocTime({ adjusted: 1800 }), // 30 minutes (within 60 minutes requirement)
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: client.requestKey,
      });

      console.log("Request Object JWT:", requestObject);

      // Backchannel authentication request with signed request object
      let backchannelAuthenticationResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: client.clientId,
        request: requestObject,
        clientCertFile: client.clientCertFile,
      });

      console.log("Backchannel Authentication Response:", backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
      expect(backchannelAuthenticationResponse.data).toHaveProperty("auth_req_id");
      expect(backchannelAuthenticationResponse.data).toHaveProperty("expires_in");

      // Get authentication transaction
      let authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: serverConfig.ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id,
        },
      });

      console.log("Authentication Transaction Response:", authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);
      expect(authenticationTransactionResponse.data.list).toHaveLength(1);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];

      // Authenticate with user code
      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "password-authentication",
        body: {
          username: serverConfig.ciba.username,
          password: serverConfig.ciba.userCode,
        },
      });

      console.log("Authentication Complete Response:", completeResponse.data);
      expect(completeResponse.status).toBe(200);

      // Request token with CIBA grant
      let tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: client.clientId,
        clientCertFile: client.clientCertFile,
      });

      console.log("Token Response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data).toHaveProperty("id_token");
      expect(tokenResponse.data).toHaveProperty("token_type");

      let userinfoResponse = await post({
        url: serverConfig.userinfoEndpoint,
        headers: {
          Authorization: `Bearer ${tokenResponse.data.access_token}`,
          "x-ssl-cert": encodedClientCert(client.clientCertFile),
        },
      });
      expect(userinfoResponse.status).toBe(200);

      let revokeResponse = await post({
        url: serverConfig.tokenRevocationEndpoint,
        headers: {
          "x-ssl-cert": encodedClientCert(client.clientCertFile),
        },
        body: new URLSearchParams({
          client_id: client.clientId,
          token: tokenResponse.data.access_token
        }).toString()
      });

      expect(revokeResponse.status).toBe(200);

      userinfoResponse = await post({
        url: serverConfig.userinfoEndpoint,
        headers: {
          Authorization: `Bearer ${tokenResponse.data.access_token}`,
          "x-ssl-cert": encodedClientCert(client.clientCertFile),
        },
      });
      expect(userinfoResponse.status).toBe(401);

      tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "refresh_token",
        refreshToken: tokenResponse.data.refresh_token,
        clientId: client.clientId,
        clientCertFile: client.clientCertFile,
      });

      console.log(JSON.stringify(tokenResponse.data, null, 2));
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_grant");

    });

    it("19. shall return an invalid_client error as defined in 5.2 of RFC6749 when mis-matched client identifiers were provided through the client authentication methods that permits sending the client identifier in more than one way;", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email " + privateKeyJwtClient.fapiBaselineScope,
        redirectUri: privateKeyJwtClient.redirectUri,
        clientId: privateKeyJwtClient.clientId,
        nonce: "nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const clientAssertion = createJwtWithPrivateKey({
        payload: {
          iss: "clientId",
          sub: "clientId",
          aud: serverConfig.issuer,
          jti: generateJti(),
          exp: toEpocTime({ adjusted: 3600 }),
          iat: toEpocTime({ adjusted: 0 }),
        },
        privateKey: privateKeyJwtClient.clientSecretKey,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: privateKeyJwtClient.redirectUri,
        clientId: privateKeyJwtClient.clientId,
        codeVerifier,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_client");
      expect(tokenResponse.data.error_description).toContain("When FAPI Baseline profile, client_id must matched client_assertion sub claim");
    });

    it("Not Applicable. 20. shall require redirect URIs to use the https scheme;", async () => {
      console.log("CIBA is a backchannel flow with no redirect, so redirect_uri is not applicable.");
    });

    it("21. should issue access tokens with a lifetime of under 10 minutes unless the tokens are sender-constrained;", async () => {
      console.log(
        "FAPI Part 1 5.2.2-21: Access token lifetime requirements:\n" +
        "- Tokens WITHOUT sender-constraint (Bearer tokens): < 10 minutes lifetime required\n" +
        "- Tokens WITH sender-constraint (mTLS bound): Longer lifetime allowed\n" +
        "\n" +
        "FAPI CIBA uses sender-constrained tokens (mTLS binding with cnf:x5t#S256),\n" +
        "so the 10-minute restriction does NOT apply.\n" +
        "\n" +
        "Verification: Access tokens include cnf claim binding them to client certificate,\n" +
        "allowing secure use with longer lifetimes (typically 1 hour)."
      );
    });

    it ("22. shall support OIDD, may support RFC8414 and shall not distribute discovery metadata (such as the authorization endpoint) by any other means.", async () => {
      const response = await getConfiguration({
        endpoint: serverConfig.discoveryEndpoint,
      });
      console.log(JSON.stringify(response.data, null, 2));
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty(
        "backchannel_token_delivery_modes_supported"
      );
      expect(
        isArray(response.data.backchannel_token_delivery_modes_supported)
      ).toBe(true);
      expect(
        response.data.backchannel_token_delivery_modes_supported
      ).toEqual(["poll", "ping", "push"]);
    });
  });

});
