import { describe, expect, it } from "@jest/globals";

import { getJwks, inspectToken, requestToken } from "./api/oauthClient";
import {
  selfSignedTlsAuthClient,
  serverConfig,
} from "./testConfig";
import { requestAuthorizations, certThumbprint } from "./oauth";
import { verifyAndDecodeIdToken } from "./lib/jose";

describe("OpenID Connect Core 1.0 incorporating errata set 1 request object", () => {
  it("self_signed_tls_client_auth", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email " + selfSignedTlsAuthClient.scope,
      redirectUri: selfSignedTlsAuthClient.redirectUri,
      clientId: selfSignedTlsAuthClient.clientId,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: selfSignedTlsAuthClient.redirectUri,
      clientId: selfSignedTlsAuthClient.clientId,
      clientCertFile: selfSignedTlsAuthClient.clientCertFile,
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

    const introspectionResponse = await inspectToken({
      endpoint: serverConfig.tokenIntrospectionEndpoint,
      token: tokenResponse.data.access_token,
    });
    console.log(introspectionResponse.data);
    expect(introspectionResponse.status).toBe(200);
    expect(introspectionResponse.data).toHaveProperty("cnf");
    const thumbprint = certThumbprint(selfSignedTlsAuthClient.clientCertFile);
    expect(introspectionResponse.data.cnf["x5t#S256"]).toEqual(thumbprint);
  });


});
