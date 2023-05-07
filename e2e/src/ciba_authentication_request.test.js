import { describe, expect, it, xit } from "@jest/globals";

import {
  completeBackchannelAuthentications,
  requestBackchannelAuthentications,
  requestToken,
} from "./api/oauthClient";
import {
  clientSecretPostClient,
  privateKeyJwtClient,
  serverConfig,
} from "./testConfig";
import { createJwt, createJwtWithPrivateKey, generateJti } from "./lib/jose";
import { isNumber, toEpocTime } from "./lib/util";

describe("OpenID Connect Client-Initiated Backchannel Authentication Flow - Core 1.0", () => {
  const ciba = serverConfig.ciba;

  it("success pattern", async () => {
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

    const completeResponse = await completeBackchannelAuthentications({
      endpoint: serverConfig.backchannelAuthenticationAutomatedCompleteEndpoint,
      authReqId: backchannelAuthenticationResponse.data.auth_req_id,
      action: "allow",
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
  });

  describe("7. Backchannel Authentication Endpoint", () => {
    describe("7.1. Authentication Request", () => {
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

      it('Because in the CIBA flow, the OP does not have an interaction with the end-user through the consumption device, it is REQUIRED that the Client provides one (and only one) of the hints specified above in the authentication request, that is "login_hint_token", "id_token_hint" or "login_hint".', async () => {
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
            exp: toEpocTime({ plus: -10 }),
            iat: toEpocTime({}),
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
            exp: toEpocTime({ plus: 3000 }),
            iat: toEpocTime({ plus: 1000 }),
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
      it("", async () => {
        //TODO
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
          "client authentication type is client_secret_post, but request does not contains client_secret_post"
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

      it('3. Validate all the authentication request parameters. In the event the request contains more than one of the hints specified in Authentication Request, the OpenID Provider MUST return an "invalid_request" error response as per Section 13.', async () => {
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

    it('expires_in REQUIRED. A JSON number with a positive integer value indicating the expiration time of the "auth_req_id" in seconds since the authentication request was received. A Client calling the token endpoint with an expired auth_req_id will receive an error, see Token Error Response.', async () => {
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

    it('error_description OPTIONAL. Human-readable ASCII [USASCII] text providing additional information, used to assist the client developer in understanding the error that occurred. Values for the "error_description" parameter MUST NOT include characters outside the set %x20-21 / %x23-5B / %x5D-7E.', async () => {
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
        "client is unsupported ciba grant"
      );
    });
  });
});
