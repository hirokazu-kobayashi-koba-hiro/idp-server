import { describe, expect, it, xit } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "../../api/oauthClient";
import {
  clientSecretPostClient,
  privateKeyJwtClient,
  publicClient,
  serverConfig,
} from "../testConfig";
import { createJwt, createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { isNumber, sleep, toEpocTime } from "../../lib/util";
import { postWithJson } from "../../lib/http";

describe("OpenID Connect Client-Initiated Backchannel Authentication Flow - Core 1.0", () => {
  const ciba = serverConfig.ciba;

  it("success pattern", async () => {
    let backchannelAuthenticationResponse =
      await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: ciba.loginHintDevice,
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
    console.log(authenticationTransactionResponse.data);
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
  });

  describe("7. Backchannel Authentication Endpoint", () => {
    describe("7.1. Authentication Request", () => {
      it ("The Client MUST authenticate to the Backchannel Authentication Endpoint using the authentication method registered for its client_id, such as the authentication methods from Section 9 of [OpenID.Core] or authentication methods defined by extension in other specifications.", async () => {
        // Public clients use 'none' authentication type and have no client authentication
        // This test attempts CIBA request without any client authentication
        const requestObject = createJwtWithPrivateKey({
          payload: {
            scope: "openid profile phone email ",
            binding_message: ciba.bindingMessage,
            user_code: ciba.userCode,
            login_hint: ciba.loginHintDevice,
            client_id: publicClient.clientId,
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

      it("scope REQUIRED. The scope of the access request as described by Section 3.3 of [RFC6749].", async () => {
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            bindingMessage: ciba.bindingMessage,
            userCode: ciba.userCode,
            loginHint: ciba.loginHint,
            clientSecret: clientSecretPostClient.clientSecret,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "invalid_scope"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual(
          "backchannel request does not contains openid scope. OpenID Connect implements authentication as an extension to OAuth 2.0 by including the openid scope value in the authorization requests."
        );
      });

      it("Because in the CIBA flow, the OP does not have an interaction with the end-user through the consumption device, it is REQUIRED that the Client provides one (and only one) of the hints specified above in the authentication request, that is \"login_hint_token\", \"id_token_hint\" or \"login_hint\".", async () => {
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            bindingMessage: ciba.bindingMessage,
            userCode: ciba.userCode,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            clientSecret: clientSecretPostClient.clientSecret,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "invalid_request"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual(
          "backchannel request does not have any hint, must contains login_hint or login_hint_token or id_token_hint"
        );
      });
    });

    describe("7.1.1. Signed Authentication Request", () => {
      it("The JWT MUST be secured with an asymmetric signature and follow the guidance from Section 10.1 of [OpenID.Core] regarding asymmetric signatures. ", async () => {
        const request = createJwt({
          payload: {
            client_id: clientSecretPostClient.clientId,
            binding_message: ciba.bindingMessage,
            user_code: ciba.userCode,
            login_hint: ciba.loginHint,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            client_secret: clientSecretPostClient.clientSecret,
            aud: "aud",
            iss: clientSecretPostClient.clientId,
          },
          secret: clientSecretPostClient.clientSecret,
        });
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            request,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "invalid_request_object"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual(
          "request object is invalid, request object must signed with asymmetric key"
        );
      });

      it("aud The Audience claim MUST contain the value of the Issuer Identifier for the OP, which identifies the Authorization Server as an intended audience.", async () => {
        const request = createJwtWithPrivateKey({
          payload: {
            client_id: clientSecretPostClient.clientId,
            binding_message: ciba.bindingMessage,
            user_code: ciba.userCode,
            login_hint: ciba.loginHint,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            client_secret: clientSecretPostClient.clientSecret,
            aud: "aud",
            iss: clientSecretPostClient.clientId,
          },
          privateKey: clientSecretPostClient.requestKey,
        });
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            request,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "invalid_request_object"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual("request object is invalid, aud claim must be issuer");
      });

      it("iss The Issuer claim MUST be the client_id of the OAuth Client.", async () => {
        const request = createJwtWithPrivateKey({
          payload: {
            client_id: clientSecretPostClient.clientId,
            binding_message: ciba.bindingMessage,
            user_code: ciba.userCode,
            login_hint: ciba.loginHint,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            client_secret: clientSecretPostClient.clientSecret,
            aud: serverConfig.issuer,
            iss: "clientId",
          },
          privateKey: clientSecretPostClient.requestKey,
        });
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            request,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "invalid_request_object"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual("request object is invalid, iss claim must be client_id");
      });

      it("exp An expiration time that limits the validity lifetime of the signed authentication request.", async () => {
        const request = createJwtWithPrivateKey({
          payload: {
            client_id: clientSecretPostClient.clientId,
            binding_message: ciba.bindingMessage,
            user_code: ciba.userCode,
            login_hint: ciba.loginHint,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            client_secret: clientSecretPostClient.clientSecret,
            aud: serverConfig.issuer,
            iss: clientSecretPostClient.clientId,
            exp: toEpocTime({ adjusted: -10 }),
            iat: toEpocTime({}),
            nbf: toEpocTime({}),
            jti: generateJti(),
          },
          privateKey: clientSecretPostClient.requestKey,
        });

        console.log(request);

        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            request,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "invalid_request_object"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual("request object is invalid, jwt is expired");
      });

      xit("iat The time at which the signed authentication request was created.", async () => {
        const request = createJwtWithPrivateKey({
          payload: {
            client_id: clientSecretPostClient.clientId,
            binding_message: ciba.bindingMessage,
            user_code: ciba.userCode,
            login_hint: ciba.loginHint,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            client_secret: clientSecretPostClient.clientSecret,
            aud: serverConfig.issuer,
            iss: clientSecretPostClient.clientId,
            exp: toEpocTime({ adjusted: 3000 }),
            iat: toEpocTime({ adjusted: 1000 }),
            nbf: toEpocTime({}),
            jti: generateJti(),
          },
          privateKey: clientSecretPostClient.requestKey,
        });
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            request,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "invalid_request_object"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual("");
      });
    });

    describe("7.1.2. User Code", () => {
      it ("invalid_user_code", async () => {
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            bindingMessage: ciba.bindingMessage,
            userCode: "ciba.userCode",
            loginHint: ciba.loginHintSub,
            clientSecret: clientSecretPostClient.clientSecret,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual("invalid_user_code");
        expect(backchannelAuthenticationResponse.data.error_description).toEqual("The user code was invalid. Unmatch to user password.");
      });

      xit("missing_user_code", async () => {
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            bindingMessage: ciba.bindingMessage,
            loginHint: ciba.loginHintSub,
            clientSecret: clientSecretPostClient.clientSecret,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual("missing_user_code");
        expect(backchannelAuthenticationResponse.data.error_description).toEqual("user_code is required for this id-provider.");
      });
    });

    describe("7.2. Authentication Request Validation", () => {
      //The OpenID Provider MUST validate the request received as follows:
      it("1. Authenticate the Client per the authentication method registered or configured for its client_id. It is RECOMMENDED that Clients not send shared secrets in the Authentication Request but rather that public-key cryptography be used.", async () => {
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            bindingMessage: ciba.bindingMessage,
            userCode: ciba.userCode,
            loginHint: ciba.loginHint,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(401);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "invalid_client"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual(
          "Client authentication failed: method=client_secret_post, client_id=clientSecretPost, reason=request does not contain client_secret"
        );
      });

      it("2. If the authentication request is signed, validate the JWT sent with the request parameter, which includes verifying the signature and ensuring that the JWT is valid in all other respects per [RFC7519].", async () => {
        const request = createJwt({
          payload: {
            client_id: clientSecretPostClient.clientId,
            bindingMessage: ciba.bindingMessage,
            userCode: ciba.userCode,
            loginHint: ciba.loginHint,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            client_secret: clientSecretPostClient.clientSecret,
          },
          secret: "clientSecretPostClient.requestKey",
        });
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            request,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "invalid_request_object"
        );
      });

      it("3. Validate all the authentication request parameters. In the event the request contains more than one of the hints specified in Authentication Request, the OpenID Provider MUST return an \"invalid_request\" error response as per Section 13.", async () => {
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            bindingMessage: ciba.bindingMessage,
            userCode: ciba.userCode,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            clientSecret: clientSecretPostClient.clientSecret,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "invalid_request"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual(
          "backchannel request does not have any hint, must contains login_hint or login_hint_token or id_token_hint"
        );
      });

      it("4. The OpenID Provider MUST process the hint provided to determine if the hint is valid and if it corresponds to a valid user. The type, issuer (where applicable) and maximum age (where applicable) of a hint that an OP accepts should be communicated to Clients. How the OP validates hints and informs Clients of its hint requirements is out-of-scope of this specification.", async () => {
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            bindingMessage: ciba.bindingMessage,
            userCode: ciba.userCode,
            loginHint: ciba.invalidLoginHint,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            clientSecret: clientSecretPostClient.clientSecret,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "unknown_user_id"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual(
          "The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint)."
        );
      });

      it("5. If the hint is not valid or if the OP is not able to determine the user then an error should be returned to the Client as per Section Authentication Error Response.", async () => {
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            bindingMessage: ciba.bindingMessage,
            userCode: ciba.userCode,
            loginHint: ciba.invalidLoginHint,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            clientSecret: clientSecretPostClient.clientSecret,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "unknown_user_id"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual(
          "The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint)."
        );
      });

      it("6. The OpenID Provider MUST verify that all the REQUIRED parameters are present and their usage conforms to this specification.", async () => {
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            bindingMessage: ciba.bindingMessage,
            userCode: ciba.userCode,
            loginHint: ciba.invalidLoginHint,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            clientSecret: clientSecretPostClient.clientSecret,
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(400);
        expect(backchannelAuthenticationResponse.data.error).toEqual(
          "unknown_user_id"
        );
        expect(
          backchannelAuthenticationResponse.data.error_description
        ).toEqual(
          "The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint)."
        );
      });
    });
  });

  describe("7.3. Successful Authentication Request Acknowledgement", () => {
    it("auth_req_id REQUIRED. This is a unique identifier to identify the authentication request made by the Client. It MUST contain sufficient entropy (a minimum of 128 bits while 160 bits is recommended) to make brute force guessing or forgery of a valid auth_req_id computationally infeasible - the means of achieving this are implementation-specific, with possible approaches including secure pseudorandom number generation or cryptographically secured self-contained tokens. The OpenID Provider MUST restrict the characters used to 'A'-'Z', 'a'-'z', '0'-'9', '.', '-' and '_', to reduce the chance of the client incorrectly decoding or re-encoding the auth_req_id; this character set was chosen to allow the server to use unpadded base64url if it wishes. The identifier MUST be treated as opaque by the client.", async () => {
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
      expect(backchannelAuthenticationResponse.data.auth_req_id).not.toBeNull();

      const regExp = new RegExp("^[A-Za-z0-9.\\-_]+$");
      expect(
        regExp.test(backchannelAuthenticationResponse.data.auth_req_id)
      ).toBe(true);
    });

    it("expires_in REQUIRED. A JSON number with a positive integer value indicating the expiration time of the \"auth_req_id\" in seconds since the authentication request was received. A Client calling the token endpoint with an expired auth_req_id will receive an error, see Token Error Response.", async () => {
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
      expect(backchannelAuthenticationResponse.data.auth_req_id).not.toBeNull();
      const regExp = new RegExp("^[A-Za-z0-9.\\-_]+$");
      expect(
        regExp.test(backchannelAuthenticationResponse.data.auth_req_id)
      ).toBe(true);

      expect(backchannelAuthenticationResponse.data.expires_in).not.toBeNull();
      expect(isNumber(backchannelAuthenticationResponse.data.expires_in)).toBe(
        true
      );
    });

    it("interval OPTIONAL. A JSON number with a positive integer value indicating the minimum amount of time in seconds that the Client MUST wait between polling requests to the token endpoint. This parameter will only be present if the Client is registered to use the Poll or Ping modes. If no value is provided, clients MUST use 5 as the default value.", async () => {
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);
      expect(backchannelAuthenticationResponse.data.auth_req_id).not.toBeNull();
      const regExp = new RegExp("^[A-Za-z0-9.\\-_]+$");
      expect(
        regExp.test(backchannelAuthenticationResponse.data.auth_req_id)
      ).toBe(true);

      expect(backchannelAuthenticationResponse.data.expires_in).not.toBeNull();
      expect(isNumber(backchannelAuthenticationResponse.data.expires_in)).toBe(
        true
      );

      if (backchannelAuthenticationResponse.data.interval) {
        expect(isNumber(backchannelAuthenticationResponse.data.interval)).toBe(
          true
        );
      }
    });
  });

  describe("11. Token Error Response", () => {

    it("authorization_pending", async () => {
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHintSub,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("authorization_pending");
      expect(tokenResponse.data.error_description).toEqual("The authorization request is still pending as the end-user hasn't yet been authenticated.");

    });

    it("expired_token", async () => {
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHintSub,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          requestedExpiry: 1,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(200);

      await sleep(5000);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("expired_token");
      expect(tokenResponse.data.error_description).toEqual("The auth_req_id has expired. The Client will need to make a new Authentication Request.");

    });

    it("access_denied", async () => {
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHintSub,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          requestedExpiry: 1,
          clientSecret: clientSecretPostClient.clientSecret,
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

      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

      const intaractionResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: authenticationTransaction.flow,
        id: authenticationTransaction.id,
        interactionType: "authentication-device-deny",
        body: {},
      });
      console.log(intaractionResponse.data);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("access_denied");
      expect(tokenResponse.data.error_description).toEqual("The end-user denied the authorization request.");

    });

    it("invalid_grant", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
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
      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);

      const authenticationTransaction = authenticationTransactionResponse.data.list[0];
      console.log(authenticationTransaction);

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

      let tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);

      tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: backchannelAuthenticationResponse.data.auth_req_id,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toEqual("invalid_grant");
      expect(tokenResponse.data.error_description).toContain("auth_req_id is invalid");
    });
  });

  describe("13. Authentication Error Response", () => {
    it("error REQUIRED. A single ASCII error code from one present in the list below.", async () => {
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.invalidLoginHint,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(400);

      expect(backchannelAuthenticationResponse.data).toHaveProperty("error");
    });

    it("error_description OPTIONAL. Human-readable ASCII [USASCII] text providing additional information, used to assist the client developer in understanding the error that occurred. Values for the \"error_description\" parameter MUST NOT include characters outside the set %x20-21 / %x23-5B / %x5D-7E.", async () => {
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.invalidLoginHint,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(400);

      expect(backchannelAuthenticationResponse.data).toHaveProperty("error");
      expect(
        /^[\x20-\x21\x23-\x5B\x5D-\x7E]*$/.test(
          backchannelAuthenticationResponse.data.error_description
        )
      ).toBe(true);
    });

    it("unauthorized_client The Client is not authorized to use this authentication flow.", async () => {
      const backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: privateKeyJwtClient.clientId,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          scope: "openid profile phone email" + privateKeyJwtClient.scope,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(400);

      expect(backchannelAuthenticationResponse.data).toHaveProperty("error");
      expect(backchannelAuthenticationResponse.data.error).toEqual(
        "unauthorized_client"
      );
      expect(
        /^[\x20-\x21\x23-\x5B\x5D-\x7E]*$/.test(
          backchannelAuthenticationResponse.data.error_description
        )
      ).toBe(true);
      expect(backchannelAuthenticationResponse.data.error_description).toEqual(
        "client is unauthorized ciba grant"
      );
    });
  });

  describe("error request", () => {

    //TODO register IDENTITY_VERIFICATION_REQUIRED user
    xit("identity verification id is required", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email " + clientSecretPostClient.identityVerificationScope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(403);
      expect(backchannelAuthenticationResponse.data.error).toEqual("access_denied");
      expect(backchannelAuthenticationResponse.data.error_description).toEqual("This request contains required identity verification scope. But the user is not identity verified. scopes: transfers");
    });

    it("tenantId id is invalid", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationInvalidTenantIdEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect([400, 404]).toContain(backchannelAuthenticationResponse.status);
    });

    it("clientId id is not specified", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: "",
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(400);
      expect(backchannelAuthenticationResponse.data.error).toEqual("invalid_request");
      expect(backchannelAuthenticationResponse.data.error_description).toEqual("client_id is in neither body or header. client_id is required");
    });

    it("loginHint is invalid format", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: "email--aa",
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(400);
      expect(backchannelAuthenticationResponse.data.error).toEqual("unknown_user_id");
      expect(backchannelAuthenticationResponse.data.error_description).toEqual("The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint).");
    });

    xit("bindingMessage is invalid format", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage + "a@@@?<>aaggahhgguuaugklagba#########&777a----------------------------------------------------------------------------------------",
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientSecret: clientSecretPostClient.clientSecret,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(400);
      expect(backchannelAuthenticationResponse.data.error).toEqual("unknown_user_id");
      expect(backchannelAuthenticationResponse.data.error_description).toEqual("The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint).");
    });

    it("clientSecret is not specified", async () => {
      let backchannelAuthenticationResponse =
        await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
        });
      console.log(backchannelAuthenticationResponse.data);
      expect(backchannelAuthenticationResponse.status).toBe(401);
      expect(backchannelAuthenticationResponse.data.error).toEqual("invalid_client");
      expect(backchannelAuthenticationResponse.data.error_description).toEqual("Client authentication failed: method=client_secret_post, client_id=clientSecretPost, reason=request does not contain client_secret");
    });

  });

  describe("7.1. Authentication Request", () => {
    it("The client constructs the request by including the following parameters using the \"application/x-www-form-urlencoded\" format - OpenID CIBA Section 7.1", async () => {
      // OpenID CIBA Section 7.1: Content-Type MUST be application/x-www-form-urlencoded
      // Sending application/json should return HTTP 415 Unsupported Media Type
      const basicAuth = Buffer.from(
        `${clientSecretPostClient.clientId}:${clientSecretPostClient.clientSecret}`
      ).toString("base64");

      const response = await postWithJson({
        url: serverConfig.backchannelAuthenticationEndpoint,
        headers: {
          "Content-Type": "application/json",
          Authorization: `Basic ${basicAuth}`,
        },
        body: {
          scope: "openid profile phone email" + clientSecretPostClient.scope,
          binding_message: ciba.bindingMessage,
          user_code: ciba.userCode,
          login_hint: ciba.loginHint,
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
