import { describe, expect, it } from "@jest/globals";

import { getJwks, requestToken } from "./api/oauthClient";
import {
  clientSecretJwtClient,
  privateKeyJwtClient,
  serverConfig,
} from "./testConfig";
import { requestAuthorizations } from "./oauth";
import { verifyAndDecodeIdToken } from "./lib/jose";
import { createClientAssertion } from "./lib/oauth";

describe("OpenID Connect Core 1.0 incorporating errata set 1 request object", () => {
  it("client secret jwt", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email " + clientSecretJwtClient.scope,
      redirectUri: clientSecretJwtClient.redirectUri,
      clientId: clientSecretJwtClient.clientId,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const clientAssertion = createClientAssertion({
      client: clientSecretJwtClient,
      issuer: serverConfig.issuer,
    });

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretJwtClient.redirectUri,
      clientId: clientSecretJwtClient.clientId,
      clientAssertion,
      clientAssertionType:
        "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
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

  it("private key jwt", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email " + privateKeyJwtClient.scope,
      redirectUri: privateKeyJwtClient.redirectUri,
      clientId: privateKeyJwtClient.clientId,
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const clientAssertion = createClientAssertion({
      client: privateKeyJwtClient,
      issuer: serverConfig.issuer,
    });

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: privateKeyJwtClient.redirectUri,
      clientId: privateKeyJwtClient.clientId,
      clientAssertion,
      clientAssertionType:
        "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
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
});
