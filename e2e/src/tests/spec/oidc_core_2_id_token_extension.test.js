import { describe, expect, it } from "@jest/globals";

import { getJwks } from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";
import { verifyAndDecodeJwt } from "../../lib/jose";

describe("OpenID Connect Core 1.0 incorporating errata set 1 id_token", () => {

  //The primary extension that OpenID Connect makes to OAuth 2.0 to enable End-Users to be Authenticated is the ID Token data structure. The ID Token is a security token that contains Claims about the Authentication of an End-User by an Authorization Server when using a Client, and potentially other requested Claims. The ID Token is represented as a JSON Web Token (JWT) [JWT].
  //
  // The following Claims are used within the ID Token for all OAuth 2.0 flows used by OpenID Connect:
  describe("2.  ID Token", () => {
    it("iss REQUIRED. Issuer Identifier for the Issuer of the response. The iss value is a case sensitive URL using the https scheme that contains scheme, host, and optionally, port number and path components and no query or fragment components.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "id_token",
        state: "aiueo",
        scope: "openid profile phone email claims:roles claims:permissions claims:assigned_tenants claims:assigned_organizations claims:authentication_devices " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        nonce: "nonce",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.idToken).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload.iss).not.toBeNull();
      expect(payload.iss).toEqual(serverConfig.issuer);
    });
  });

});
