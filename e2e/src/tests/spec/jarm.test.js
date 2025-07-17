import { describe, expect, it } from "@jest/globals";

import { getJwks, inspectToken, requestToken } from "../../api/oauthClient";
import {
  selfSignedTlsAuthClient,
  serverConfig,
} from "../testConfig";
import { requestAuthorizations, certThumbprint } from "../../oauth/request";
import { verifyAndDecodeJwt } from "../../lib/jose";

describe("Financial-grade API: JWT Secured Authorization Response Mode for OAuth 2.0 (JARM)", () => {
  it("success", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email " + selfSignedTlsAuthClient.scope,
      redirectUri: selfSignedTlsAuthClient.redirectUri,
      clientId: selfSignedTlsAuthClient.clientId,
      responseMode: "jwt"
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.response).not.toBeNull();

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedResponse = verifyAndDecodeJwt({
      jwt: authorizationResponse.response,
      jwks: jwksResponse.data,
    });

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: decodedResponse.payload.code,
      grantType: "authorization_code",
      redirectUri: selfSignedTlsAuthClient.redirectUri,
      clientId: selfSignedTlsAuthClient.clientId,
      clientCertFile: selfSignedTlsAuthClient.clientCertFile,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");


    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);

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


});
