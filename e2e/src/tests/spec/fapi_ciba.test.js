import { describe, expect, it } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  inspectToken,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken,
} from "../../api/oauthClient";
import { selfSignedTlsAuthClient, serverConfig } from "../testConfig";
import { createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { toEpocTime } from "../../lib/util";
import { certThumbprint } from "../../oauth/request";

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
});
