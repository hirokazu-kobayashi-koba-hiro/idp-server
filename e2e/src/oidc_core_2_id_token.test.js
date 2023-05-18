import { describe, expect, it } from "@jest/globals";

import { getJwks, getUserinfo, postUserinfo, requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";
import { createBearerHeader, isNumber, matchWithUSASCII } from "./lib/util";
import { verifyAndDecodeIdToken } from "./lib/jose";

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
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.idToken).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeIdToken({
        idToken: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload.iss).not.toBeNull();
      expect(payload.iss).toEqual(serverConfig.issuer);
    });

    it("sub REQUIRED. REQUIRED. Subject Identifier. A locally unique and never reassigned identifier within the Issuer for the End-User, which is intended to be consumed by the Client, e.g., 24400320 or AItOawmwtWwcT0k51BayewNvutrJUqsvl6qs7A4. It MUST NOT exceed 255 ASCII characters in length. The sub value is a case sensitive string.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "id_token",
        state: "aiueo",
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.idToken).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeIdToken({
        idToken: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload.iss).not.toBeNull();
      expect(payload.iss).toEqual(serverConfig.issuer);

      expect(payload.sub).not.toBeNull();
      expect(payload.sub.length <= 255).toBe(true);
      expect(matchWithUSASCII(payload.sub)).toEqual(true);
    });

    it("aud REQUIRED. Audience(s) that this ID Token is intended for. It MUST contain the OAuth 2.0 client_id of the Relying Party as an audience value. It MAY also contain identifiers for other audiences. In the general case, the aud value is an array of case sensitive strings. In the common special case when there is one audience, the aud value MAY be a single case sensitive string.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "id_token",
        state: "aiueo",
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.idToken).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeIdToken({
        idToken: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload.iss).not.toBeNull();
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.sub).not.toBeNull();
      expect(payload.sub.length <= 255).toBe(true);
      expect(matchWithUSASCII(payload.sub)).toEqual(true);

      expect(payload.aud).not.toBeNull();
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
    });

    it("exp REQUIRED. Expiration time on or after which the ID Token MUST NOT be accepted for processing. The processing of this parameter requires that the current date/time MUST be before the expiration date/time listed in the value. Implementers MAY provide for some small leeway, usually no more than a few minutes, to account for clock skew. Its value is a JSON number representing the number of seconds from 1970-01-01T0:0:0Z as measured in UTC until the date/time. See RFC 3339 [RFC3339] for details regarding date/times in general and UTC in particular.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "id_token",
        state: "aiueo",
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.idToken).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeIdToken({
        idToken: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload.iss).not.toBeNull();
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.sub).not.toBeNull();
      expect(payload.sub.length <= 255).toBe(true);
      expect(matchWithUSASCII(payload.sub)).toEqual(true);
      expect(payload.aud).not.toBeNull();
      expect(payload.aud).toContain(clientSecretPostClient.clientId);

      expect(payload.exp).not.toBeNull();
      expect(isNumber(payload.exp)).toBe(true);
    });

    it("iat REQUIRED. Time at which the JWT was issued. Its value is a JSON number representing the number of seconds from 1970-01-01T0:0:0Z as measured in UTC until the date/time.", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "id_token",
        state: "aiueo",
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.idToken).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeIdToken({
        idToken: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload.iss).not.toBeNull();
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.sub).not.toBeNull();
      expect(payload.sub.length <= 255).toBe(true);
      expect(matchWithUSASCII(payload.sub)).toEqual(true);
      expect(payload.aud).not.toBeNull();
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      expect(payload.exp).not.toBeNull();
      expect(isNumber(payload.exp)).toBe(true);

      expect(payload.iat).not.toBeNull();
      expect(isNumber(payload.iat)).toBe(true);
    });

    it("auth_time Time when the End-User authentication occurred. Its value is a JSON number representing the number of seconds from 1970-01-01T0:0:0Z as measured in UTC until the date/time. When a max_age request is made or when auth_time is requested as an Essential Claim, then this Claim is REQUIRED; otherwise, its inclusion is OPTIONAL. (The auth_time Claim semantically corresponds to the OpenID 2.0 PAPE [OpenID.PAPE] auth_time response parameter.)", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "id_token",
        state: "aiueo",
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.idToken).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeIdToken({
        idToken: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload.iss).not.toBeNull();
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.sub).not.toBeNull();
      expect(payload.sub.length <= 255).toBe(true);
      expect(matchWithUSASCII(payload.sub)).toEqual(true);
      expect(payload.aud).not.toBeNull();
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      expect(payload.exp).not.toBeNull();
      expect(isNumber(payload.exp)).toBe(true);
      expect(payload.iat).not.toBeNull();
      expect(isNumber(payload.iat)).toBe(true);

      if (payload.auth_time) {
        expect(isNumber(payload.auth_time)).toBe(true);
      }
    });

    it("nonce String value used to associate a Client session with an ID Token, and to mitigate replay attacks. The value is passed through unmodified from the Authentication Request to the ID Token. If present in the ID Token, Clients MUST verify that the nonce Claim Value is equal to the value of the nonce parameter sent in the Authentication Request. If present in the Authentication Request, Authorization Servers MUST include a nonce Claim in the ID Token with the Claim Value being the nonce value sent in the Authentication Request. Authorization Servers SHOULD perform no other processing on nonce values used. The nonce value is a case sensitive string.", async () => {
      const nonce = "nonce_value";
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "id_token",
        state: "aiueo",
        nonce,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.idToken).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeIdToken({
        idToken: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload.iss).not.toBeNull();
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.sub).not.toBeNull();
      expect(payload.sub.length <= 255).toBe(true);
      expect(matchWithUSASCII(payload.sub)).toEqual(true);
      expect(payload.aud).not.toBeNull();
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      expect(payload.exp).not.toBeNull();
      expect(isNumber(payload.exp)).toBe(true);
      expect(payload.iat).not.toBeNull();
      expect(isNumber(payload.iat)).toBe(true);
      if (payload.auth_time) {
        expect(isNumber(payload.auth_time)).toBe(true);
      }

      expect(payload.nonce).not.toBeNull();
      expect(payload.nonce).toEqual(nonce);
    });


    it("acr OPTIONAL. Authentication Context Class Reference. String specifying an Authentication Context Class Reference value that identifies the Authentication Context Class that the authentication performed satisfied. The value \"0\" indicates the End-User authentication did not meet the requirements of ISO/IEC 29115 [ISO29115] level 1. Authentication using a long-lived browser cookie, for instance, is one example where the use of \"level 0\" is appropriate. Authentications with level 0 SHOULD NOT be used to authorize access to any resource of any monetary value. (This corresponds to the OpenID 2.0 PAPE [OpenID.PAPE] nist_auth_level 0.) An absolute URI or an RFC 6711 [RFC6711] registered name SHOULD be used as the acr value; registered names MUST NOT be used with a different meaning than that which is registered. Parties using this claim will need to agree upon the meanings of the values used, which may be context-specific. The acr value is a case sensitive string.", async () => {
      const nonce = "nonce_value";
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "id_token",
        state: "aiueo",
        nonce,
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.idToken).not.toBeNull();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeIdToken({
        idToken: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload.iss).not.toBeNull();
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.sub).not.toBeNull();
      expect(payload.sub.length <= 255).toBe(true);
      expect(matchWithUSASCII(payload.sub)).toEqual(true);
      expect(payload.aud).not.toBeNull();
      expect(payload.aud).toContain(clientSecretPostClient.clientId);
      expect(payload.exp).not.toBeNull();
      expect(isNumber(payload.exp)).toBe(true);
      expect(payload.iat).not.toBeNull();
      expect(isNumber(payload.iat)).toBe(true);
      if (payload.auth_time) {
        expect(isNumber(payload.auth_time)).toBe(true);
      }

      expect(payload.nonce).not.toBeNull();
      expect(payload.nonce).toEqual(nonce);

      //TODO acr
    });
  });
});
