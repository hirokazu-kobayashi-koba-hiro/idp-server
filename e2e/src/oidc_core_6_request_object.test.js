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
  it("success pattern", async () => {
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
    console.log(tokenResponse.data);
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

  it("success pattern none", async () => {
    const requestObject = createJwtWithNoneSignature({
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
    console.log(tokenResponse.data);
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

  it("success pattern jwe", async () => {
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
      },
      privateKey: clientSecretPostClient.requestKey,
    });

    const jwe = await createJwe({
      text: requestObject,
      key: clientSecretPostClient.requestEncKey,
      enc: clientSecretPostClient.requestEnc,
    });

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      request: jwe,
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
    console.log(tokenResponse.data);
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

  describe("6.  Passing Request Parameters as JWTs", () => {
    xit("The JWT MUST be secured with an asymmetric signature and follow the guidance from Section 10.1 of [OpenID.Core] regarding asymmetric signatures. ", async () => {
      const request = createJwt({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + clientSecretPostClient.scope,
          redirect_uri: clientSecretPostClient.redirectUri,
          aud: "aud",
          iss: clientSecretPostClient.clientId,
        },
        secret: clientSecretPostClient.clientSecret,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: clientSecretPostClient.clientId,
      });
      console.log(authorizationResponse);
      console.log(authorizationResponse.data);
      expect(authorizationResponse.error).toEqual(
        "invalid_request_object"
      );
      expect(
        authorizationResponse.errorDescription
      ).toEqual(
        "request object is invalid, request object must signed with asymmetric key"
      );
    });

    xit("aud The Audience claim MUST contain the value of the Issuer Identifier for the OP, which identifies the Authorization Server as an intended audience.", async () => {
      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + clientSecretPostClient.scope,
          redirect_uri: clientSecretPostClient.redirectUri,
          aud: "aud",
          iss: clientSecretPostClient.clientId,
        },
        privateKey: clientSecretPostClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: clientSecretPostClient.clientId,
      });
      console.log(authorizationResponse);
      console.log(authorizationResponse.data);
      expect(authorizationResponse.error).toEqual(
        "invalid_request_object"
      );
      expect(
        authorizationResponse.errorDescription
      ).toEqual("request object is invalid, aud claim must be issuer");
    });

    xit("iss The Issuer claim MUST be the client_id of the OAuth Client.", async () => {
      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + clientSecretPostClient.scope,
          redirect_uri: clientSecretPostClient.redirectUri,
          aud: serverConfig.issuer,
          iss: "clientId",
        },
        privateKey: clientSecretPostClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: clientSecretPostClient.clientId,
      });
      console.log(authorizationResponse);
      console.log(authorizationResponse.data);
      expect(authorizationResponse.error).toEqual(
        "invalid_request_object"
      );
      expect(
        authorizationResponse.errorDescription
      ).toEqual("request object is invalid, iss claim must be client_id");
    });

    xit("exp An expiration time that limits the validity lifetime of the signed authentication request.", async () => {
      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + clientSecretPostClient.scope,
          redirect_uri: clientSecretPostClient.redirectUri,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: -10 }),
          iat: toEpocTime({}),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: clientSecretPostClient.clientId,
      });
      console.log(authorizationResponse);
      console.log(authorizationResponse.data);
      expect(authorizationResponse.error).toEqual(
        "invalid_request_object"
      );
      expect(
        authorizationResponse.errorDescription
      ).toEqual("request object is invalid, jwt is expired");
    });

    xit("iat The time at which the signed authentication request was created.", async () => {
      const request = createJwtWithPrivateKey({
        payload: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          state: "aiueo",
          scope: "openid profile phone email " + clientSecretPostClient.scope,
          redirect_uri: clientSecretPostClient.redirectUri,
          aud: serverConfig.issuer,
          iss: clientSecretPostClient.clientId,
          exp: toEpocTime({ adjusted: 3000 }),
          iat: toEpocTime({ adjusted: 1000 }),
          nbf: toEpocTime({}),
          jti: generateJti(),
        },
        privateKey: clientSecretPostClient.requestKey,
      });
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        request,
        clientId: clientSecretPostClient.clientId,
      });
      console.log(authorizationResponse);
      console.log(authorizationResponse.data);
      expect(authorizationResponse.error).toEqual(
        "invalid_request_object"
      );
    });
  });

  describe("", () => {
    it("", async () => {

    });
  });
});
